package fr.blueprint.core.graph.screen;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Un écran d'un blueprint (story 10.1).
 *
 * <p>Immuable et <b>ordonné</b> : l'ordre des éléments est l'ordre de dessin, donc la
 * superposition. Un {@code LinkedHashMap} plutôt qu'une liste parce que le nom est
 * l'identité (AC2) et que tout le produit désignera un élément par son nom — le graphe
 * (FR47), les diagnostics, les modificateurs.
 *
 * <p><b>Modal ou permanent.</b> Un écran modal capte la souris et fige le jeu ; un HUD
 * ne capte rien et se dessine pendant qu'on joue (story 10.9). Le drapeau vit ici dès
 * maintenant : le rajouter plus tard obligerait à migrer les écrans déjà créés.
 */
public final class Screen {

    /**
     * La plus petite fenêtre réellement rencontrée, en unités d'interface : 1280×720
     * avec un <i>GUI scale</i> de 4. Ce n'est pas un cas tordu, c'est un réglage
     * courant — et c'est pourquoi un menu dessiné « 400 de large » ne rentre chez
     * personne.
     */
    public static final int SAFE_WIDTH = 320;
    public static final int SAFE_HEIGHT = 180;

    private final String name;
    private final boolean hud;
    private final Map<String, ScreenElement> elements;

    public Screen(String name, boolean hud, List<ScreenElement> elements) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("un écran doit avoir un nom");
        }
        this.name = name;
        this.hud = hud;
        Map<String, ScreenElement> byName = new LinkedHashMap<>();
        for (ScreenElement element : elements) {
            byName.put(element.name(), element);
        }
        this.elements = Collections.unmodifiableMap(byName);
    }

    public static Screen empty(String name) {
        return new Screen(name, false, List.of());
    }

    public String name() {
        return name;
    }

    /** Vrai si l'écran s'affiche par-dessus le jeu sans le figer (story 10.9). */
    public boolean hud() {
        return hud;
    }

    /** Les éléments dans l'ordre de dessin ; le dernier est au-dessus. */
    public Map<String, ScreenElement> elements() {
        return elements;
    }

    public @Nullable ScreenElement element(String elementName) {
        return elements.get(elementName);
    }

    public int size() {
        return elements.size();
    }

    /** Les enfants directs d'un conteneur, dans l'ordre de dessin. */
    public List<ScreenElement> childrenOf(@Nullable String parent) {
        List<ScreenElement> out = new ArrayList<>();
        for (ScreenElement element : elements.values()) {
            if (java.util.Objects.equals(element.parent(), parent)) {
                out.add(element);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Les packs de ressources dont cet écran a besoin (AC1c), <b>déduits</b> des
     * textures utilisées : l'espace de nom d'une texture EST le pack qui la fournit.
     *
     * <p>Déduits et non déclarés, à dessein. Une liste écrite à la main dérive : on
     * ajoute une image sans compléter la liste, ou on retire la dernière image d'un
     * pack sans l'en retirer. L'éditeur avertirait alors pour un pack devenu inutile et
     * se tairait sur celui qui manque — exactement l'inverse du service rendu.
     *
     * <p>{@code minecraft} en est exclu : il est toujours là.
     */
    public java.util.Set<String> requiredPacks() {
        java.util.Set<String> packs = new java.util.LinkedHashSet<>();
        for (ScreenElement element : elements.values()) {
            if (element.texture() != null
                    && !"minecraft".equals(element.texture().getNamespace())) {
                packs.add(element.texture().getNamespace());
            }
        }
        return java.util.Collections.unmodifiableSet(packs);
    }

    // ------------------------------------------------------------- modifications
    // Toutes rendent un NOUVEL écran : c'est ce qui permet aux EditOperation de
    // porter leur inverse sans copier le monde à la main.

    public Screen with(ScreenElement element) {
        List<ScreenElement> out = new ArrayList<>(elements.values());
        out.removeIf(existing -> existing.name().equals(element.name()));
        out.add(element);
        return new Screen(name, hud, out);
    }

    /** Remplace un élément EN PLACE : l'ordre de dessin ne bouge pas. */
    public Screen replacing(String elementName, ScreenElement replacement) {
        List<ScreenElement> out = new ArrayList<>();
        for (ScreenElement existing : elements.values()) {
            out.add(existing.name().equals(elementName) ? replacement : existing);
        }
        return new Screen(name, hud, out);
    }

    /**
     * Retire un élément <b>et ses descendants</b>. Les laisser orphelins produirait
     * des éléments dont le parent n'existe plus : invisibles, indélogeables, et
     * comptés dans le plafond.
     */
    public Screen without(String elementName) {
        java.util.Set<String> doomed = new java.util.LinkedHashSet<>();
        collectSubtree(elementName, doomed);
        List<ScreenElement> out = new ArrayList<>();
        for (ScreenElement existing : elements.values()) {
            if (!doomed.contains(existing.name())) {
                out.add(existing);
            }
        }
        return new Screen(name, hud, out);
    }

    private void collectSubtree(String root, java.util.Set<String> into) {
        if (!into.add(root)) {
            return; // déjà vu : coupe un cycle de parenté malformé
        }
        for (ScreenElement element : elements.values()) {
            if (root.equals(element.parent())) {
                collectSubtree(element.name(), into);
            }
        }
    }

    public Screen renamed(String newName) {
        return new Screen(newName, hud, List.copyOf(elements.values()));
    }

    public Screen withHud(boolean newHud) {
        return new Screen(name, newHud, List.copyOf(elements.values()));
    }

    /** Réordonne : {@code delta} positif remonte l'élément vers le premier plan. */
    public Screen reordered(String elementName, int delta) {
        List<ScreenElement> out = new ArrayList<>(elements.values());
        int from = -1;
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i).name().equals(elementName)) {
                from = i;
                break;
            }
        }
        if (from < 0) {
            return this;
        }
        int to = Math.clamp(from + delta, 0, out.size() - 1);
        out.add(to, out.remove(from));
        return new Screen(name, hud, out);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Screen screen
                && name.equals(screen.name)
                && hud == screen.hud
                && List.copyOf(elements.values()).equals(List.copyOf(screen.elements.values()));
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(name, hud, List.copyOf(elements.values()));
    }

    @Override
    public String toString() {
        return "Screen[" + name + (hud ? ", HUD" : "") + ", " + elements.size() + " éléments]";
    }
}
