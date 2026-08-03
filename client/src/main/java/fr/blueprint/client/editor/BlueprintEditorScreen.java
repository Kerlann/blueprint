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
        canvas.render(graphics, font, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Le canevas peint tout : pas de flou ni de fond vanilla.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        return canvas.mouseClicked(event, doubled) || super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return canvas.mouseReleased(event) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return canvas.mouseDragged(event, dx, dy) || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        return canvas.mouseScrolled(mouseX, mouseY, vAmount)
                || super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_S && event.hasControlDown()) {
            save();
            return true;
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
            super.onClose();
        }
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        return canvas.keyReleased(event) || super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return canvas.charTyped(event) || super.charTyped(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
