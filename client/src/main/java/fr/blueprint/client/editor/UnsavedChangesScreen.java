package fr.blueprint.client.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Confirmation à la fermeture avec des modifications non enregistrées (U2 : jamais
 * de perte silencieuse). Trois issues : enregistrer et quitter, quitter sans
 * enregistrer, continuer l'édition.
 */
public final class UnsavedChangesScreen extends Screen {

    private final BlueprintEditorScreen editor;
    private final EditorSession session;

    public UnsavedChangesScreen(BlueprintEditorScreen editor, EditorSession session) {
        super(Component.translatable("blueprint.editor.unsaved.title"));
        this.editor = editor;
        this.session = session;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height / 2 - 20;
        addRenderableWidget(Button.builder(Component.translatable("blueprint.editor.unsaved.save"),
                b -> {
                    session.save();
                    minecraft.setScreen(null);
                }).bounds(cx - 100, y, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blueprint.editor.unsaved.discard"),
                b -> minecraft.setScreen(null)).bounds(cx - 100, y + 24, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("blueprint.editor.unsaved.cancel"),
                b -> minecraft.setScreen(editor)).bounds(cx - 100, y + 48, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 44, 0xFFE6E6E6);
    }

    @Override
    public void onClose() {
        // Échap = continuer l'édition, pas perdre le travail.
        minecraft.setScreen(editor);
    }
}
