package fr.blueprint.client.editor;

import fr.blueprint.core.graph.Blueprint;
import fr.blueprint.core.graph.NodeTypeLookup;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * L'écran de l'éditeur : héberge le canevas plein écran. Les panneaux latéraux
 * (variables, détails, diagnostics) arrivent avec les stories 5.4–5.6.
 */
public final class BlueprintEditorScreen extends Screen {

    private final CanvasWidget canvas;
    private boolean framed;

    public BlueprintEditorScreen(Blueprint blueprint, NodeTypeLookup lookup) {
        super(Component.translatable("blueprint.editor.title", blueprint.id().toString()));
        this.canvas = new CanvasWidget(blueprint, lookup);
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
        canvas.render(graphics, font);
        graphics.drawString(font, title, 6, 6, 0xFF8A8F98, false);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Le canevas peint tout : pas de flou ni de fond vanilla.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        return canvas.mouseClicked(event) || super.mouseClicked(event, doubled);
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
        return canvas.keyPressed(event) || super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        return canvas.keyReleased(event) || super.keyReleased(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
