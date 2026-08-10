package fr.blueprint.client.screen;

import fr.blueprint.core.graph.screen.Screen;
import fr.blueprint.core.graph.screen.ScreenUpdate;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les HUD affichés chez ce client (story 10.9).
 *
 * <p><b>Un HUD n'est pas un écran.</b> Un {@code Screen} de Minecraft capte la souris,
 * fige le jeu et se ferme par {@code Échap} ; c'est ce qu'il faut pour une boutique et
 * exactement ce qu'il ne faut pas pour une barre de mana. Confondre les deux — ce que
 * faisait la 10.4 faute d'avoir cette classe — donnait un joueur figé sur place dès
 * qu'on voulait lui montrer son or.
 *
 * <p>Plusieurs HUD coexistent : ils ne captent rien, donc deux HUD sont deux dessins au
 * même endroit, comme la barre de vie et la barre d'expérience du jeu.
 *
 * <p>État pur, testable sans client : le dessin vit dans {@code BlueprintHud}.
 */
public final class HudView {

    /** Les HUD affichés, dans l'ordre d'apparition — c'est l'ordre de dessin. */
    private final Map<String, Screen> shown = new LinkedHashMap<>();
    /** Le remplissage des barres, par écran puis par élément (valeur d'exécution). */
    private final Map<String, Map<String, Double>> progress = new LinkedHashMap<>();
    /** Ce que les liaisons client ont produit la dernière fois — pour ne pas le refaire. */
    private final Map<String, ScreenUpdate> lastClient = new LinkedHashMap<>();
    private static final String SEP = " ";
    private boolean hidden;

    /**
     * De quel blueprint vient chaque HUD affiché.
     *
     * <p>Nécessaire depuis la réplication (épic 21) et pas avant : une liaison de source
     * {@code VARIABLE} se résout par {@code (blueprint, nom)}, parce que le nom seul ne dit pas
     * de quelle variable il s'agit quand deux graphes en déclarent une du même nom. Le HUD
     * n'avait jusqu'ici aucune raison de savoir d'où il venait.
     */
    private final Map<String, net.minecraft.resources.Identifier> owners = new LinkedHashMap<>();

    /** Affiche un HUD, ou remplace sa description si elle a changé. */
    public void show(net.minecraft.resources.Identifier blueprint, Screen screen) {
        shown.put(screen.name(), screen);
        owners.put(screen.name(), blueprint);
    }

    public void hide(String screen) {
        shown.remove(screen);
        owners.remove(screen);
        progress.remove(screen);
        lastClient.keySet().removeIf(k -> k.startsWith(screen + SEP));
    }

    public void hideAll() {
        shown.clear();
        owners.clear();
        progress.clear();
        lastClient.clear();
    }

    /**
     * La bascule du joueur — la <b>garde de sécurité</b> de l'AC5.
     *
     * <p>Un écran modal a toujours {@code Échap}. Un HUD n'a rien : un graphe fautif
     * affichant un panneau opaque plein écran laisserait le joueur sans recours, voyant
     * son monde caché sans que rien de ce qu'il tape ne le retire. La touche existe
     * donc dès cette story, pas « plus tard si besoin ».
     */
    public void toggleHidden() {
        hidden = !hidden;
    }

    public boolean hidden() {
        return hidden;
    }

    /** Ce qu'il faut dessiner : rien si le joueur a tout masqué. */
    public List<Screen> visible() {
        return hidden ? List.of() : List.copyOf(shown.values());
    }

    public @Nullable Screen get(String screen) {
        return shown.get(screen);
    }

    public int size() {
        return shown.size();
    }

    /**
     * Recalcule les liaisons de <b>source client</b>, sans réseau ni serveur.
     *
     * <p>Appelée au tick client, pas à l'image : vingt fois par seconde suffit largement
     * pour une barre de vie, et soixante allouerait trois fois plus de listes pour un
     * résultat que l'œil ne distingue pas.
     *
     * <p>Les modifications identiques à celles du tour précédent sont <b>écartées</b>. Le
     * chemin d'application recrée un {@code Screen} à chaque texte changé ; l'appeler pour
     * une valeur qui n'a pas bougé allouerait un écran complet par tick et par HUD, pour
     * repeindre exactement les mêmes pixels. C'est la même discipline que
     * {@code ScreenSessions#queue} côté serveur, appliquée au seul endroit où le serveur
     * n'est plus là pour la tenir.
     *
     * @param values ce que vaut une valeur client, par son nom
     * @return le nombre de modifications réellement appliquées
     */
    /** La clé d'une modification : écran, élément, nature. Le séparateur est explicite. */
    private static String key(ScreenUpdate update) {
        return update.screen() + SEP + update.element() + SEP + update.kind();
    }

    public int refreshClientBindings(java.util.function.Function<String, Object> values) {
        return refresh(screen -> values,
                fr.blueprint.core.graph.screen.ElementBinding.Source.CLIENT);
    }

    /**
     * Recalcule les liaisons de source {@code VARIABLE} depuis les valeurs répliquées
     * (épic 21, story 21.5).
     *
     * <p>Le client résout donc lui-même des liaisons de variables, ce que le javadoc de
     * {@code ScreenBindings} déclarait impossible — « il ne connaît pas les variables, et ne
     * doit pas ». La phrase reste vraie au sens où elle a été écrite : le client ne connaît
     * toujours ni portée, ni type déclaré, ni valeur par défaut. Il connaît des valeurs nommées
     * que le serveur lui a <b>envoyées</b>, et il appelle le <b>même</b> code de rendu pour en
     * tirer les mêmes pixels.
     *
     * <p>Ce que cela supprime : un aller-retour par changement. Une barre de mana se rafraîchit
     * désormais comme une barre de vie.
     */
    public int refreshVariableBindings() {
        return refresh(screen -> {
            var blueprint = owners.get(screen);
            return blueprint == null ? null : ReplicatedVars.lookup(blueprint);
        }, fr.blueprint.core.graph.screen.ElementBinding.Source.VARIABLE);
    }

    /**
     * Le tronc commun des deux sources. La table des modifications déjà appliquées est
     * <b>partagée</b> par les deux, et c'est correct : elle est indexée par
     * {@code écran+élément+nature}, et un élément n'a qu'une liaison, donc qu'une source.
     */
    private int refresh(java.util.function.Function<String,
                                java.util.function.Function<String, Object>> lookups,
                        fr.blueprint.core.graph.screen.ElementBinding.Source source) {
        if (shown.isEmpty()) {
            return 0;
        }
        java.util.List<ScreenUpdate> fresh = new java.util.ArrayList<>();
        for (Screen screen : shown.values()) {
            var values = lookups.apply(screen.name());
            if (values == null) {
                continue;   // un HUD sans propriétaire connu : rien à résoudre
            }
            for (ScreenUpdate update
                    : fr.blueprint.core.net.ScreenBindings.updates(screen, values, source)) {
                String key = key(update);
                if (!update.equals(lastClient.get(key))) {
                    lastClient.put(key, update);
                    fresh.add(update);
                }
            }
        }
        return fresh.isEmpty() ? 0 : apply(fresh);
    }

    /**
     * Applique les modifications qui visent un HUD affiché. Celles qui visent autre
     * chose — l'écran modal, un HUD retiré entre-temps — sont <b>ignorées</b> : le
     * client n'invente pas de destinataire.
     *
     * @return le nombre de modifications réellement appliquées
     */
    public int apply(List<ScreenUpdate> updates) {
        int applied = 0;
        for (ScreenUpdate update : updates) {
            Screen screen = shown.get(update.screen());
            if (screen == null) {
                continue;
            }
            var element = screen.element(update.element());
            if (element == null) {
                continue;
            }
            shown.put(screen.name(), screen.replacing(update.element(),
                    switch (update.kind()) {
                        case TEXT -> element.withText(update.screenText());
                        // L'infobulle d'un HUD ne se verra jamais — il ne capte pas la
                        // souris — mais l'appliquer coûte moins que de s'en souvenir : un
                        // écran passé de modal à HUD garde ainsi ce qu'il portait.
                        case TOOLTIP -> element.withTooltip(update.screenText());
                        case STYLE -> element.withStyleName(update.text());
                        case TEXTURE -> element.withTexture(update.textureId());
                        case VISIBLE -> element.withVisible(update.flag());
                        case ENABLED -> element.withEnabled(update.flag());
                        case PROGRESS -> element;
                        // Les valeurs des éléments riches (10.8) vivent à part, comme
                        // le remplissage des barres : ce sont des données d'exécution,
                        // propres à ce joueur et à cette ouverture, pas des propriétés
                        // du blueprint. Les écrire dans le modèle les ferait voyager
                        // dans la sauvegarde et l'export texte.
                        // SCROLL n'a pas de sens sur un HUD : il ne capte pas la molette,
                        // et rien n'y ferait remonter le panneau. La modification est
                        // simplement sans effet plutôt que refusée — un écran passé de
                        // HUD à modal doit pouvoir en tirer parti.
                        case LINES, ITEM, VALUE, SCROLL, SCROLL_X -> element;
                    }));
            if (update.kind() == ScreenUpdate.Kind.PROGRESS) {
                progress.computeIfAbsent(update.screen(), s -> new LinkedHashMap<>())
                        .put(update.element(), update.number());
            }
            applied++;
        }
        return applied;
    }

    public double progressOf(String screen, String element) {
        return progress.getOrDefault(screen, Map.of()).getOrDefault(element, 0.0);
    }

    /** Une déconnexion ne laisse pas un HUD du serveur précédent à l'écran. */
    public void clear() {
        hideAll();
        hidden = false;
    }
}
