package fr.blueprint.core.graph;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Un blueprint : nœuds, liens, variables, commentaires, métadonnées.
 * Mutable <b>uniquement</b> via les {@link EditOperation} (même paquet) — c'est ce qui
 * donne, avec la même mécanique, l'annuler/rétablir de l'éditeur et les patchs réseau.
 * {@code revision} s'incrémente à chaque opération appliquée (verrouillage optimiste).
 */
public final class Blueprint {

    private final Identifier id;
    private BlueprintMeta meta;
    private boolean enabled = true;
    private int revision;
    private final Map<UUID, Node> nodes = new LinkedHashMap<>();
    private final Set<Link> links = new LinkedHashSet<>();
    /**
     * Index des liens par nœud <b>touché</b>, dans les deux sens (épic 14).
     *
     * <p>Sans lui, {@link #linksFrom}, {@link #linksInto} et {@link #linksTouching}
     * répondaient chacune par un {@code links.stream().filter(...)} — un balayage de
     * <b>tous</b> les liens du graphe, à chaque question. Le validateur en pose une par
     * lien <i>et</i> une par nœud : la validation était donc en O(N·L + L²), et c'est elle
     * que {@code Compiler.compile} appelle en entier avant d'émettre quoi que ce soit.
     * Mesuré : un graphe de mille nœuds et quatre mille liens compilait en <b>112 ms</b>,
     * contre un budget NFR2 de 50 ms — que le banc d'alors ne pouvait pas voir, ses nœuds
     * n'ayant aucun pin de données.
     *
     * <p>Un seul index plutôt que deux (« sortants » et « entrants ») : les trois questions
     * s'y répondent en O(degré), et surtout <b>l'ordre est exactement préservé</b>. Chaque
     * ensemble reçoit ses liens dans l'ordre où le graphe les a reçus, donc filtrer l'index
     * rend la même séquence que filtrer {@code links} — ce que deux index fusionnés ne
     * garantiraient pas. L'ordre de {@link #linksTouching} décide quel diagnostic sort le
     * premier d'une résolution de joker : ce n'est pas un détail cosmétique.
     *
     * <p>Un lien réflexif (même nœud des deux côtés) n'entre qu'une fois, l'ensemble
     * s'en charge — c'est le comportement du filtre {@code ||} qu'il remplace.
     */
    private final Map<UUID, Set<Link>> linksByNode = new LinkedHashMap<>();
    private final Map<String, Variable> variables = new LinkedHashMap<>();
    private final Map<UUID, CommentBox> comments = new LinkedHashMap<>();
    /** Écrans du blueprint (épic 10) — même sérialisation, même révision, même verrou. */
    private final Map<String, fr.blueprint.core.graph.screen.Screen> screens = new LinkedHashMap<>();
    /**
     * Fonctions du blueprint (story 20.1) — même sérialisation, même révision, même verrou.
     *
     * <p>Leurs nœuds ne sont <b>pas</b> dans {@link #nodes} : une fonction possède son
     * corps. Tout ce qui parcourt un graphe doit donc parcourir les deux, et l'oubli le
     * plus coûteux serait celui du validateur — un nœud {@code ADMIN} caché dans un corps
     * échapperait au plafond de permission du blueprint.
     */
    private final Map<String, BlueprintFunction> functions = new LinkedHashMap<>();

    public Blueprint(Identifier id) {
        this(id, BlueprintMeta.DEFAULT);
    }

    public Blueprint(Identifier id, BlueprintMeta meta) {
        this.id = id;
        this.meta = meta;
    }

    public Identifier id() {
        return id;
    }

    public BlueprintMeta meta() {
        return meta;
    }

    public boolean enabled() {
        return enabled;
    }

    /**
     * État de cycle de vie serveur (FR20), piloté par {@code BlueprintManager} et les
     * commandes — volontairement PAS une {@code EditOperation} : l'annuler/rétablir de
     * l'éditeur ne doit jamais réactiver un blueprint désactivé par un admin (MODEL-001).
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int revision() {
        return revision;
    }

    public Map<UUID, Node> nodes() {
        return Collections.unmodifiableMap(nodes);
    }

    public @Nullable Node node(UUID uuid) {
        return nodes.get(uuid);
    }

    public Set<Link> links() {
        return Collections.unmodifiableSet(links);
    }

    public Map<String, Variable> variables() {
        return Collections.unmodifiableMap(variables);
    }

    public Collection<CommentBox> comments() {
        return Collections.unmodifiableCollection(comments.values());
    }

    /** Les écrans, par nom. Vide pour l'immense majorité des blueprints. */
    public Map<String, fr.blueprint.core.graph.screen.Screen> screens() {
        return Collections.unmodifiableMap(screens);
    }

    public fr.blueprint.core.graph.screen.@Nullable Screen screen(String name) {
        return screens.get(name);
    }

    /** Les fonctions, par nom. Vide pour l'immense majorité des blueprints. */
    public Map<String, BlueprintFunction> functions() {
        return Collections.unmodifiableMap(functions);
    }

    public @Nullable BlueprintFunction function(String name) {
        return functions.get(name);
    }

    public @Nullable CommentBox comment(UUID uuid) {
        return comments.get(uuid);
    }

    // --- requêtes utilisées par le validateur et les opérations ---

    /** Liens sortant du pin donné. O(degré du nœud) grâce à {@link #linksByNode}. */
    public List<Link> linksFrom(UUID node, String pin) {
        return filterTouching(node, l -> l.fromNode().equals(node) && l.fromPin().equals(pin));
    }

    /** Liens entrant dans le pin donné. O(degré du nœud). */
    public List<Link> linksInto(UUID node, String pin) {
        return filterTouching(node, l -> l.toNode().equals(node) && l.toPin().equals(pin));
    }

    /** Tous les liens touchant un nœud, dans les deux sens, en ordre d'insertion. */
    public List<Link> linksTouching(UUID node) {
        Set<Link> touching = linksByNode.get(node);
        return touching == null || touching.isEmpty() ? List.of() : List.copyOf(touching);
    }

    /**
     * Le filtre commun aux deux questions par pin. Rend {@link List#of()} — donc sans
     * allocation — pour un nœud isolé, ce qui est le cas le plus fréquent lorsqu'on
     * interroge tous les pins d'un nœud dont la plupart ne sont pas câblés.
     */
    private List<Link> filterTouching(UUID node, java.util.function.Predicate<Link> keep) {
        Set<Link> touching = linksByNode.get(node);
        if (touching == null || touching.isEmpty()) {
            return List.of();
        }
        List<Link> found = null;
        for (Link link : touching) {
            if (keep.test(link)) {
                if (found == null) {
                    found = new java.util.ArrayList<>(2);
                }
                found.add(link);
            }
        }
        return found == null ? List.of() : Collections.unmodifiableList(found);
    }

    // --- mutations réservées aux EditOperation (même paquet) ---

    void putNode(Node node) {
        nodes.put(node.uuid(), node);
    }

    void dropNode(UUID uuid) {
        nodes.remove(uuid);
    }

    void putLink(Link link) {
        if (links.add(link)) {
            index(link);
        }
    }

    void dropLink(Link link) {
        if (links.remove(link)) {
            unindex(link, link.fromNode());
            unindex(link, link.toNode());
        }
    }

    /**
     * Entre un lien dans {@link #linksByNode}, des deux côtés.
     *
     * <p>Conditionné à {@code links.add} : un lien déjà présent ne doit pas être compté
     * deux fois, sans quoi un {@code dropLink} le laisserait dans l'index.
     */
    private void index(Link link) {
        linksByNode.computeIfAbsent(link.fromNode(), k -> new LinkedHashSet<>()).add(link);
        linksByNode.computeIfAbsent(link.toNode(), k -> new LinkedHashSet<>()).add(link);
    }

    /** Retire un lien du côté donné, et l'entrée elle-même si le nœud n'a plus de lien. */
    private void unindex(Link link, UUID node) {
        Set<Link> touching = linksByNode.get(node);
        if (touching != null && touching.remove(link) && touching.isEmpty()) {
            // Sans cela, un graphe longuement édité garderait une entrée vide par nœud
            // ayant un jour porté un lien — et linksTouching allouerait pour rien.
            linksByNode.remove(node);
        }
    }

    void putVariable(Variable variable) {
        variables.put(variable.name(), variable);
    }

    void dropVariable(String name) {
        variables.remove(name);
    }

    void putComment(CommentBox comment) {
        comments.put(comment.uuid(), comment);
    }

    void dropComment(UUID uuid) {
        comments.remove(uuid);
    }

    void putScreen(fr.blueprint.core.graph.screen.Screen screen) {
        screens.put(screen.name(), screen);
    }

    void dropScreen(String name) {
        screens.remove(name);
    }

    void putFunction(BlueprintFunction function) {
        functions.put(function.name(), function);
    }

    void dropFunction(String name) {
        functions.remove(name);
    }

    void setMeta(BlueprintMeta meta) {
        this.meta = meta;
    }

    void bumpRevision() {
        revision++;
    }

    void setRevision(int revision) {
        this.revision = revision;
    }

    /**
     * Révision imposée à l'adoption d'un instantané venu du réseau (6.3) : le serveur
     * reste seul maître du compteur — un client ne choisit jamais sa propre révision.
     */
    public void adoptRevision(int revision) {
        this.revision = revision;
    }

    /**
     * Écrans illisibles — type d'élément inconnu, format d'une version postérieure :
     * NBT brut, ré-émis tel quel. Même promesse que les nœuds fantômes (FR40), et
     * même raison : ouvrir un monde avec une version antérieure du mod ne doit pas
     * effacer silencieusement la moitié d'un menu.
     */
    private net.minecraft.nbt.ListTag preservedScreens = new net.minecraft.nbt.ListTag();

    net.minecraft.nbt.ListTag preservedScreens() {
        return preservedScreens;
    }

    /** Vrai si des écrans sont préservés en brut — l'export texte doit le signaler. */
    public boolean hasPreservedScreens() {
        return !preservedScreens.isEmpty();
    }

    void setPreservedScreens(net.minecraft.nbt.ListTag preserved) {
        this.preservedScreens = preserved;
    }

    /**
     * Fonctions au type de paramètre irrésoluble (mod retiré) : NBT brut, ré-émis tel quel.
     *
     * <p>Même promesse que les écrans, et l'enjeu est plus grand : une fonction porte un
     * corps entier. La jeter parce qu'un de ses paramètres cite un type disparu effacerait
     * des dizaines de nœuds pour un mod qu'on réinstallera peut-être demain.
     */
    private net.minecraft.nbt.ListTag preservedFunctions = new net.minecraft.nbt.ListTag();

    net.minecraft.nbt.ListTag preservedFunctions() {
        return preservedFunctions;
    }

    public boolean hasPreservedFunctions() {
        return !preservedFunctions.isEmpty();
    }

    void setPreservedFunctions(net.minecraft.nbt.ListTag preserved) {
        this.preservedFunctions = preserved;
    }

    // Variables au type irrésoluble (mod retiré) : NBT brut, ré-émis tel quel (P4).
    private net.minecraft.nbt.ListTag preservedVariables = new net.minecraft.nbt.ListTag();

    net.minecraft.nbt.ListTag preservedVariables() {
        return preservedVariables;
    }

    /** Vrai si des variables sont préservées en brut (P4) — l'export texte doit le signaler. */
    public boolean hasPreservedVariables() {
        return !preservedVariables.isEmpty();
    }

    void setPreservedVariables(net.minecraft.nbt.ListTag preserved) {
        this.preservedVariables = preserved;
    }

    /** Copie profonde, pour les instantanés de test et la resynchronisation. */
    public Blueprint copy() {
        Blueprint c = new Blueprint(id, meta);
        c.enabled = enabled;
        c.revision = revision;
        nodes.values().forEach(n -> c.nodes.put(n.uuid(), n.copy()));
        // Par putLink et non par links.addAll : la copie doit repartir avec son index,
        // sinon elle répondrait « aucun lien » à toute question posée par pin.
        links.forEach(c::putLink);
        c.variables.putAll(variables);
        c.comments.putAll(comments);
        // Les écrans sont immuables : les partager suffit, et les oublier ici ferait
        // perdre ses menus à tout blueprint copié — instantané réseau compris.
        c.screens.putAll(screens);
        c.functions.putAll(functions);
        c.preservedVariables = preservedVariables.copy();
        c.preservedScreens = preservedScreens.copy();
        c.preservedFunctions = preservedFunctions.copy();
        return c;
    }

    /** Égalité de contenu, révision exclue (elle compte les opérations, pas l'état). */
    public boolean contentEquals(Blueprint other) {
        if (!id.equals(other.id) || !meta.equals(other.meta) || enabled != other.enabled
                || !links.equals(other.links) || !variables.equals(other.variables)
                || !comments.equals(other.comments)
                || !screens.equals(other.screens)
                || !preservedVariables.equals(other.preservedVariables)
                || !preservedScreens.equals(other.preservedScreens)
                || !preservedFunctions.equals(other.preservedFunctions)
                // Par contentEquals et non par equals : un Node n'a pas d'égalité de
                // contenu générée, donc comparer deux corps par leurs tables comparerait
                // des IDENTITÉS — toujours faux après un aller-retour NBT.
                || !functionsContentEqual(other)
                || !nodes.keySet().equals(other.nodes.keySet())) {
            return false;
        }
        for (Node n : nodes.values()) {
            if (!n.contentEquals(other.nodes.get(n.uuid()))) {
                return false;
            }
        }
        return true;
    }

    private boolean functionsContentEqual(Blueprint other) {
        if (!functions.keySet().equals(other.functions.keySet())) {
            return false;
        }
        for (BlueprintFunction f : functions.values()) {
            if (!f.contentEquals(other.functions.get(f.name()))) {
                return false;
            }
        }
        return true;
    }
}
