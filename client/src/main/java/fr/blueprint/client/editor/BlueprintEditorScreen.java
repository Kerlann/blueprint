package fr.blueprint.client.editor;

import fr.blueprint.client.registry.ClientNodeRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * L'écran de l'éditeur : héberge le canevas plein écran. Édite toujours une copie
 * ({@link EditorSession}) ; {@code Ctrl+S} enregistre, la fermeture avec des
 * modifications non enregistrées demande confirmation (U2). Les panneaux latéraux
 * (variables, détails, diagnostics) arrivent avec les stories 5.5/5.6b/5.10.
 */
public final class BlueprintEditorScreen extends Screen {

    private final EditorSession session;
    private final CanvasWidget canvas;
    private final fr.blueprint.client.editor.screen.ScreenDesignerWidget designer;
    /**
     * Le mode courant (story 10.2, AC1). Un seul écran, deux modes : on passe du graphe
     * aux écrans et retour sans fermer ni réenregistrer, et les deux partagent la même
     * session, la même pile d'annulation et le même {@code Ctrl+S}.
     */
    private fr.blueprint.client.editor.screen.ModeTabs.Mode mode =
            fr.blueprint.client.editor.screen.ModeTabs.Mode.GRAPH;
    private boolean framed;

    public BlueprintEditorScreen(EditorSession session,
                                 fr.blueprint.core.registry.PluginLoader.LoadedRegistries registries,
                                 ClientNodeRegistry descriptors) {
        this(session, registries, descriptors, registries.nodes());
    }

    /**
     * Variante synchro réseau (6.2) : la vue validateur vient des descripteurs reçus du
     * serveur quand ils diffèrent des registres locaux — descripteurs et {@code lookup}
     * proviennent alors de la MÊME source, jamais d'un mélange.
     */
    public BlueprintEditorScreen(EditorSession session,
                                 fr.blueprint.core.registry.PluginLoader.LoadedRegistries registries,
                                 ClientNodeRegistry descriptors,
                                 fr.blueprint.core.graph.NodeTypeLookup lookup) {
        super(Component.translatable("blueprint.editor.title",
                session.blueprint().id().toString()));
        this.session = session;
        this.canvas = new CanvasWidget(session, lookup, descriptors, registries, this::onClose);
        // La MÊME pile d'annulation que le canevas de nœuds (AC6) : un Ctrl+Z défait le
        // dernier geste, qu'il ait eu lieu dans les nœuds ou dans un écran.
        this.designer = new fr.blueprint.client.editor.screen.ScreenDesignerWidget(
                session, lookup, canvas.controller().history());
        // Le canevas ne décide pas du mode, mais il peut le demander : un clic sur un
        // diagnostic qui vise un corps de fonction ouvre le corps ET l'onglet, sinon la
        // barre annoncerait « Graphe » au-dessus d'un corps.
        this.canvas.setModeRequest(wanted -> this.mode = wanted);
    }



    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        canvas.mouseMoved(mouseX, mouseY);
    }

    public EditorSession session() {
        return session;
    }

    /** État du débogueur affiché (9.1b) — alimenté par les instantanés du serveur. */
    public DebugView debug() {
        return canvas.debug();
    }

    /**
     * QA DBG-002 : fermer l'éditeur DOIT arrêter le débogage. Sinon le serveur continue
     * de pousser des instantanés à un joueur qui n'a plus d'éditeur, et surtout une
     * exécution en pause reste figée parce que plus personne ne peut la relancer.
     */
    public void stopDebugging() {
        if (canvas.debug().debugging()) {
            fr.blueprint.client.net.DebugClient.stop(session.blueprint().id());
            canvas.debug().clear();
        }
    }

    @Override
    protected void init() {
        canvas.setSize(width, height);
        // Recadrer à la première ouverture seulement : un redimensionnement de la
        // fenêtre ne doit pas faire perdre la position de travail.
        if (!framed) {
            canvas.frameAll();
            framed = true;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // La barre d'outils (5.6b) porte le titre et l'indicateur ● non-enregistré.
        if (mode == fr.blueprint.client.editor.screen.ModeTabs.Mode.GRAPH) {
            // Le canevas dessine sa propre barre d'outils (il y met « Tester » selon
            // l'état de compilation) : il reçoit le mode pour y placer les onglets.
            canvas.setMode(mode);
            canvas.render(graphics, font, mouseX, mouseY);
        } else {
            designer.setBounds(ToolbarWidget.HEIGHT, width, height);
            designer.render(graphics, font, mouseX, mouseY);
            ToolbarWidget.render(graphics, font, session.blueprint().id().toString(),
                    session.dirty(), session.savable(), false, width, mode);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Le canevas peint tout : pas de flou ni de fond vanilla.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        // Les onglets d'abord : ils flottent au-dessus des deux modes, et un clic dessus
        // ne doit jamais poser un élément ni désélectionner un nœud au passage.
        var clicked = fr.blueprint.client.editor.screen.ModeTabs.modeAt(
                font, event.x(), event.y(), ToolbarWidget.tabsX(width), ToolbarWidget.HEIGHT);
        if (clicked != null) {
            mode = clicked;
            return true;
        }
        if (mode == fr.blueprint.client.editor.screen.ModeTabs.Mode.SCREENS) {
            return designer.mouseClicked(event, doubled) || super.mouseClicked(event, doubled);
        }
        return canvas.mouseClicked(event, doubled) || super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (mode == fr.blueprint.client.editor.screen.ModeTabs.Mode.SCREENS) {
            return designer.mouseReleased(event) || super.mouseReleased(event);
        }
        return canvas.mouseReleased(event) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (mode == fr.blueprint.client.editor.screen.ModeTabs.Mode.SCREENS) {
            return designer.mouseDragged(event, dx, dy) || super.mouseDragged(event, dx, dy);
        }
        return canvas.mouseDragged(event, dx, dy) || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        // La molette n'allait qu'au canevas de nœuds : dans l'onglet Écrans elle ne
        // faisait donc rien du tout, y compris depuis que le concepteur sait zoomer.
        if (mode == fr.blueprint.client.editor.screen.ModeTabs.Mode.SCREENS) {
            return designer.mouseScrolled(mouseX, mouseY, vAmount)
                    || super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
        }
        return canvas.mouseScrolled(mouseX, mouseY, vAmount)
                || super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_S && event.hasControlDown()) {
            save();
            return true;
        }
        if (mode == fr.blueprint.client.editor.screen.ModeTabs.Mode.SCREENS) {
            // Ctrl+Z / Ctrl+Y restent au canevas de nœuds : il détient la pile, et le
            // concepteur y a déposé ses inverses. Les dupliquer ici donnerait deux
            // chemins d'annulation pour une seule pile.
            if (event.hasControlDown()
                    && (event.key() == GLFW.GLFW_KEY_Z || event.key() == GLFW.GLFW_KEY_Y)) {
                return canvas.keyPressed(event) || super.keyPressed(event);
            }
            return designer.keyPressed(event) || super.keyPressed(event);
        }
        return canvas.keyPressed(event) || super.keyPressed(event);
    }

    private void save() {
        if (session.save() && minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(
                    "blueprint.editor.saved", session.blueprint().id().toString()), true);
        }
    }

    @Override
    public void onClose() {
        if (session.dirty()) {
            minecraft.setScreen(new UnsavedChangesScreen(this, session));
        } else {
            // Plus de session à recaler : les verdicts tardifs du serveur (6.3)
            // ne doivent pas s'appliquer à un éditeur fermé.
            fr.blueprint.client.net.BlueprintNet.closed(session);
            stopDebugging();
            super.onClose();
        }
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        // Le concepteur en a besoin depuis qu'Espace y sert au déplacement de vue : sans
        // le relâchement, la touche resterait enfoncée pour lui à jamais.
        if (mode == fr.blueprint.client.editor.screen.ModeTabs.Mode.SCREENS) {
            return designer.keyReleased(event) || super.keyReleased(event);
        }
        return canvas.keyReleased(event) || super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (mode == fr.blueprint.client.editor.screen.ModeTabs.Mode.SCREENS) {
            return designer.charTyped(event) || super.charTyped(event);
        }
        return canvas.charTyped(event) || super.charTyped(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
