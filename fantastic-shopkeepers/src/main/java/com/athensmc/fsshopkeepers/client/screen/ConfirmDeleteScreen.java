package com.athensmc.fsshopkeepers.client.screen;

import com.athensmc.fsshopkeepers.client.theme.FSGui;
import com.athensmc.fsshopkeepers.client.widget.FSButton;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/**
 * The confirmation before a shop is deleted.
 *
 * <p>A screen of its own rather than a second click on the same button. Deleting takes the shop, its trades and its
 * position with it and cannot be undone, and a button that deletes on the click after the one that armed it is a button
 * that deletes when someone clicks twice out of habit.</p>
 *
 * <p>Cancel is the default: Escape and Enter both cancel, so a keypress made without reading cannot destroy
 * anything.</p>
 */
public final class ConfirmDeleteScreen extends Screen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 104;
    private static final int BUTTON_WIDTH = 132;
    private static final int BUTTON_HEIGHT = 18;
    private static final int GUTTER = 8;

    private final ShopEditorScreen parent;
    private final UUID shopId;

    private int leftPos;
    private int topPos;

    public ConfirmDeleteScreen(ShopEditorScreen parent, UUID shopId) {
        super(Component.literal("Borrar tienda"));
        this.parent = parent;
        this.shopId = shopId;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        leftPos = (width - PANEL_WIDTH) / 2;
        topPos = (height - PANEL_HEIGHT) / 2;
        int buttonY = topPos + PANEL_HEIGHT - 26;

        addRenderableWidget(new FSButton(leftPos + GUTTER, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.literal("Cancelar"), FSGui.PANEL_BORDER, this::onClose));
        addRenderableWidget(new FSButton(leftPos + PANEL_WIDTH - GUTTER - BUTTON_WIDTH, buttonY, BUTTON_WIDTH,
                BUTTON_HEIGHT, Component.literal("\u00a7cSi, borrarla"), FSGui.ACCENT_RED,
                parent::confirmDelete));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT, FSGui.WINDOW_BG);
        graphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + 20, FSGui.TITLE_BAR);
        graphics.fill(leftPos, topPos + PANEL_HEIGHT - 1, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT,
                FSGui.RULE);

        graphics.drawString(font, "\u00a7c\u2726 \u00a7fBorrar esta tienda?", leftPos + GUTTER, topPos + 6,
                0xFFFFFF, false);
        graphics.drawString(font, "\u00a7fSe perderan sus tratos y su tendero.", leftPos + GUTTER,
                topPos + 32, FSGui.TEXT, false);
        graphics.drawString(font, "\u00a77El cofre y su contenido no se tocan.", leftPos + GUTTER,
                topPos + 46, FSGui.TEXT_DIM, false);
        graphics.drawString(font, "\u00a78Esto no se puede deshacer.", leftPos + GUTTER, topPos + 60,
                FSGui.TEXT_DIM, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Escape and Enter both cancel: the safe answer is the one a reflex reaches.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
