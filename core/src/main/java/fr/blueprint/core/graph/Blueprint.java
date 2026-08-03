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
    private final Map<String, Variable> variables = new LinkedHashMap<>();
    private final Map<UUID, CommentBox> comments = new LinkedHashMap<>();

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

    public @Nullable CommentBox comment(UUID uuid) {
        return comments.get(uuid);
    }

    // --- requêtes utilisées par le validateur et les opérations ---

    /** Liens sortant du pin donné. */
    public List<Link> linksFrom(UUID node, String pin) {
        return links.stream().filter(l -> l.fromNode().equals(node) && l.fromPin().equals(pin)).toList();
    }

    /** Liens entrant dans le pin donné. */
    public List<Link> linksInto(UUID node, String pin) {
        return links.stream().filter(l -> l.toNode().equals(node) && l.toPin().equals(pin)).toList();
    }

    /** Tous les liens touchant un nœud, dans les deux sens. */
    public List<Link> linksTouching(UUID node) {
        return links.stream()
                .filter(l -> l.fromNode().equals(node) || l.toNode().equals(node))
                .toList();
    }

    // --- mutations réservées aux EditOperation (même paquet) ---

    void putNode(Node node) {
        nodes.put(node.uuid(), node);
    }

    void dropNode(UUID uuid) {
        nodes.remove(uuid);
    }

    void putLink(Link link) {
        links.add(link);
    }

    void dropLink(Link link) {
        links.remove(link);
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
        c.links.addAll(links);
        c.variables.putAll(variables);
        c.comments.putAll(comments);
        c.preservedVariables = preservedVariables.copy();
        return c;
    }

    /** Égalité de contenu, révision exclue (elle compte les opérations, pas l'état). */
    public boolean contentEquals(Blueprint other) {
        if (!id.equals(other.id) || !meta.equals(other.meta) || enabled != other.enabled
                || !links.equals(other.links) || !variables.equals(other.variables)
                || !comments.equals(other.comments)
                || !preservedVariables.equals(other.preservedVariables)
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
}
