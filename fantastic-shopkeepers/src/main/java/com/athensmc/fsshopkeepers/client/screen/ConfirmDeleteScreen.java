package com.athensmc.fsshopkeepers.client.screen;

import com.athensmc.fsshopkeepers.client.layout.Rect;
import com.athensmc.fsshopkeepers.client.theme.FantasticTheme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/**
 * The confirmation before a shop is deleted.
 *
 * <p>A screen of its own rather than a second click on the same button. Deleting takes the shop, its trades and its
 * position with it and cannot be undone, and a button that deletes on the click after the one that armed it is a
 * button that deletes when someone clicks twice out of habit.</p>
 *
 * <p>Cancel is the default: Escape and Enter both cancel, so a keypress made without reading cannot destroy
 * anything.</p>
 */
public final class ConfirmDeleteScreen extends Screen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 110;
    private static final int BUTTON_WIDTH = 130;
    private static final int BUTTON_HEIGHT = 20;

    private final ShopEditorScreen parent;
    private final UUID shopId;

    public ConfirmDeleteScreen(ShopEditorScreen parent, UUID shopId) {
        super(Component.literal("Borrar tienda"));
        this.parent = parent;
        this.shopId = shopId;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Rect panel() {
        return new Rect((width - PANEL_WIDTH) / 2, (height - PANEL_HEIGHT) / 2, PANEL_WIDTH, PANEL_HEIGHT);
    }

    private Rect cancelButton() {
        Rect panel = panel();
        return new Rect(panel.x() + 12, panel.bottom() - 12 - BUTTON_HEIGHT, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private Rect deleteButton() {
        Rect panel = panel();
        return new Rect(panel.right() - 12 - BUTTON_WIDTH, panel.bottom() - 12 - BUTTON_HEIGHT,
                BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, FantasticTheme.BACKDROP);
        Rect panel = panel();
        FantasticTheme.panel(graphics, panel, FantasticTheme.PANEL, FantasticTheme.NEGATIVE);

        Rect title = new Rect(panel.x() + 12, panel.y() + 12, panel.width() - 24, 10);
        FantasticTheme.leftText(graphics, font, title, "Borrar esta tienda?", FantasticTheme.NEGATIVE);

        Rect line1 = new Rect(panel.x() + 12, panel.y() + 32, panel.width() - 24, 10);
        FantasticTheme.leftText(graphics, font, line1, "Se perderan sus tratos y su tendero.",
                FantasticTheme.TEXT);
        Rect line2 = new Rect(panel.x() + 12, panel.y() + 46, panel.width() - 24, 10);
        FantasticTheme.leftText(graphics, font, line2, "El cofre y su contenido no se tocan.",
                FantasticTheme.TEXT_MUTED);

        Rect cancel = cancelButton();
        boolean cancelHovered = cancel.contains(mouseX, mouseY);
        FantasticTheme.button(graphics, cancel, FantasticTheme.Style.PLAIN, cancelHovered, true);
        FantasticTheme.centeredText(graphics, font, cancel, "Cancelar", FantasticTheme.TEXT);

        Rect delete = deleteButton();
        boolean deleteHovered = delete.contains(mouseX, mouseY);
        FantasticTheme.button(graphics, delete, FantasticTheme.Style.DANGER, deleteHovered, true);
        FantasticTheme.centeredText(graphics, font, delete, "Si, borrarla", FantasticTheme.TEXT);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (cancelButton().contains(mouseX, mouseY)) {
            minecraft.setScreen(parent);
            return true;
        }
        if (deleteButton().contains(mouseX, mouseY)) {
            parent.confirmDelete();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Both Escape and Enter cancel: the safe answer is the one a reflex should reach.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
