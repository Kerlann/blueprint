package fr.blueprint.client.screen;

import fr.blueprint.core.graph.screen.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * L'écran d'un blueprint, ouvert en jeu (story 10.3).
 *
 * <p><b>Générique</b> : il ne connaît aucun menu en particulier. Il reçoit la
 * description venue du serveur et la compose — pas une ligne de code par écran, et le
 * <b>même</b> {@link ScreenPainter} que le concepteur (10.2). Deux moteurs de dessin
 * donneraient deux résultats, et l'auteur découvrirait l'écart une fois en partie.
 *
 * <p>L'adaptation ne se contente pas de recentrer : les éléments sont résolus contre la
 * taille RÉELLE de la fenêtre en unités d'interface, ancres et pourcentages compris.
 * Un menu conçu une fois tient donc de 320×180 (1280×720 en <i>GUI scale</i> 4) à
 * 960×540 (3840×2160 en <i>scale</i> 4) — les deux extrêmes réels.
 */
public class BlueprintScreen extends net.minecraft.client.gui.screens.Screen {

    private final Identifier blueprint;
    private final int instance;
    private final TextureCache textures = new TextureCache();
    private final Runnable onClosed;
    private final java.util.function.Consumer<String> onClick;

    /**
     * Le modèle courant. <b>Non final</b> : les modificateurs du graphe (10.4) le
     * remplacent par une version modifiée. Un écran immuable qu'on reconstruit à chaque
     * mise à jour, plutôt qu'un état mutable à côté — ainsi le rendu, le hit-test et le
     * serveur parlent tous du même objet.
     */
    private Screen model;
    private @Nullable String pressed;
    /** La navigation au clavier (10.6, AC3) : Tab parcourt, Entrée active. */
    private final ScreenFocus focus = new ScreenFocus();

    public BlueprintScreen(Identifier blueprint, Screen model, int instance,
                           Runnable onClosed, java.util.function.Consumer<String> onClick) {
        // Le titre du lecteur d'écran : sans lui, l'accessibilité annonce « écran ».
        super(Component.translatable("blueprint.screen.title", model.name()));
        this.blueprint = blueprint;
        this.model = model;
        this.instance = instance;
        this.onClosed = onClosed;
        this.onClick = onClick;
    }

    public Identifier blueprintId() {
        return blueprint;
    }

    public Screen model() {
        return model;
    }

    public int instance() {
        return instance;
    }

    /**
     * Applique les modifications reçues du serveur.
     *
     * <p>Celles qui portent un autre numéro d'instance sont <b>jetées</b> : entre
     * l'envoi et l'arrivée, le joueur a pu refermer l'écran et en rouvrir un autre, et
     * la mise à jour s'appliquerait alors au mauvais bouton, sans la moindre erreur.
     */
    public void apply(int fromInstance,
                      java.util.List<fr.blueprint.core.graph.screen.ScreenUpdate> updates) {
        if (fromInstance != instance) {
            return;
        }
        for (var update : updates) {
            if (!update.screen().isEmpty() && !update.screen().equals(model.name())) {
                continue;   // elle vise un HUD, ou un écran qui n'est plus le nôtre
            }
            var element = model.element(update.element());
            if (element == null) {
                continue; // l'écran a changé sous nos pieds : on ignore, on ne lève pas
            }
            model = model.replacing(update.element(), switch (update.kind()) {
                case TEXT -> element.withText(update.screenText());
                case TEXTURE -> element.withTexture(update.textureId());
                case VISIBLE -> element.withVisible(update.flag());
                case ENABLED -> element.withEnabled(update.flag());
                case PROGRESS -> element;   // la valeur vit à part : voir progress()
            });
            if (update.kind() == fr.blueprint.core.graph.screen.ScreenUpdate.Kind.PROGRESS) {
                progress.put(update.element(), update.number());
            }
        }
        // Le modèle a changé : la mise en page gardée ne vaut plus. C'est le SEUL autre
        // événement que le redimensionnement qui puisse la rendre fausse.
        layout = null;
    }

    /**
     * Le remplissage des barres, par élément. Hors du modèle à dessein : c'est une
     * valeur d'exécution, propre à ce joueur et à cette ouverture, pas une propriété du
     * blueprint. La mettre dans {@code ScreenElement} la ferait voyager dans la
     * sauvegarde et l'export texte, où elle n'a rien à faire.
     */
    private final java.util.Map<String, Double> progress = new java.util.HashMap<>();

    /** Ce que le peintre a besoin de savoir : textures absentes, survol, pression. */
    protected ScreenPainter.Visuals visuals(int mouseX, int mouseY) {
        String hovered = elementAt(mouseX, mouseY);
        return new ScreenPainter.Visuals() {
            @Override
            public boolean textureMissing(Identifier texture) {
                return textures.missing(texture);
            }

            @Override
            public String missingPack(Identifier texture) {
                return textures.missingPack(texture);
            }

            @Override
            public boolean hovered(String element) {
                // Le focus clavier se montre COMME un survol : le joueur qui tabule doit
                // voir où il est, et inventer un second état visuel pour dire la même
                // chose aurait demandé à l'auteur de le styler séparément.
                return element.equals(hovered) || element.equals(focus.focused());
            }

            @Override
            public boolean pressed(String element) {
                return element.equals(pressed);
            }

            @Override
            public double progress(String element) {
                return progress.getOrDefault(element, 0.0);
            }
        };
    }

    /**
     * L'élément cliquable sous le point, ou {@code null}. Parcourt à l'envers de
     * l'ordre de dessin : c'est celui du dessus qui reçoit le clic, comme partout.
     */
    private @Nullable String elementAt(double mouseX, double mouseY) {
        // La MÊME passe que le dessin : un bouton rangé par son conteneur n'est pas là
        // où sa position écrite le dirait, et un hit-test qui la lirait quand même
        // donnerait un menu dont les boutons se cliquent à côté d'eux-mêmes.
        var placed = layout();
        var elements = java.util.List.copyOf(model.elements().values());
        for (int i = elements.size() - 1; i >= 0; i--) {
            var element = elements.get(i);
            if (!element.kind().interactive() || !element.enabled()
                    || !ScreenPainter.visible(model, element, ScreenPainter.Visuals.NONE)) {
                continue;
            }
            var rect = placed.get(element.name());
            if (rect != null && rect.contains(mouseX, mouseY)) {
                return element.name();
            }
        }
        return null;
    }

    /**
     * {@code Tab} parcourt les éléments cliquables, {@code Entrée} et {@code Espace}
     * activent (10.6, AC3). {@code Échap} ferme, comme tout écran du jeu.
     *
     * <p>Rien n'est consommé quand l'écran n'a aucun élément atteignable : le joueur
     * s'attend alors à ce que la touche fasse ce qu'elle fait partout ailleurs.
     */
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            return focus.move(model, event.hasShiftDown() ? -1 : 1) != null;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) {
            String element = focus.activate(model);
            if (element != null) {
                onClick.accept(element);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
                                boolean doubled) {
        String element = elementAt(event.x(), event.y());
        // Cliquer déplace le focus : passer de la souris au clavier ne doit pas
        // renvoyer au premier bouton du menu.
        focus.focus(model, element);
        if (element == null) {
            return super.mouseClicked(event, doubled);
        }
        pressed = element;
        return true;
    }

    /**
     * Le clic part au <b>relâchement</b>, et seulement si la souris est restée sur le
     * même élément. C'est ce que fait tout bouton du jeu, et ce qui permet d'annuler un
     * clic commencé par erreur en glissant à côté avant de lâcher.
     */
    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        String was = pressed;
        pressed = null;
        if (was != null && was.equals(elementAt(event.x(), event.y()))) {
            onClick.accept(was);
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // width/height sont déjà en unités d'interface : c'est la place réelle, et
        // c'est contre elle que se résolvent les pourcentages et les ancres.
        ScreenPainter.paint(graphics, font, model, layout(), 0, 0, 1,
                visuals(mouseX, mouseY));
    }

    /**
     * La mise en page résolue, <b>gardée entre deux images</b> (story 10.7, AC2b).
     *
     * <p>Redessiner est inévitable à chaque image ; <b>recalculer</b> ne l'est pas. Un
     * menu ouvert et immobile refaisait la passe de disposition soixante fois par seconde
     * pour un résultat identique. Deux choses seulement la rendent fausse : un paquet
     * reçu, qui change le modèle, et un redimensionnement de la fenêtre — les deux
     * invalident ici, et rien d'autre n'a besoin d'y penser.
     *
     * <p>Le même arbitrage que le cache d'infobulle de la 5.12, où recalculer le survol à
     * chaque image coûtait des centaines de milliers de racines carrées par seconde pour
     * un texte qui ne changeait pas.
     */
    private java.util.Map<String, fr.blueprint.core.graph.screen.ScreenLayout.Rect> layout() {
        if (layout == null || layoutWidth != width || layoutHeight != height) {
            layout = fr.blueprint.core.graph.screen.ScreenLayout.solve(model, width, height);
            layoutWidth = width;
            layoutHeight = height;
        }
        return layout;
    }

    private @Nullable java.util.Map<String,
            fr.blueprint.core.graph.screen.ScreenLayout.Rect> layout;
    private int layoutWidth = -1;
    private int layoutHeight = -1;

    /**
     * Le jeu ne se met PAS en pause, comme devant un coffre : le graphe continue de
     * tourner côté serveur pendant que le menu est ouvert, et un solo figé donnerait
     * un affichage qui ne se rafraîchit plus.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Prévenir le serveur depuis {@code removed} et non {@code onClose} : le premier est
     * appelé sur <b>tous</b> les chemins de fermeture — Échap, mais aussi la mort du
     * joueur, le retour au menu principal, ou un autre écran qui prend la place. Le
     * second ne l'est que sur Échap.
     *
     * <p>La différence n'est pas théorique : le serveur croirait l'écran encore ouvert
     * après une mort, et accepterait un clic dessus venu d'un client modifié (FR52).
     */
    @Override
    public void removed() {
        onClosed.run();
        super.removed();
    }
}
