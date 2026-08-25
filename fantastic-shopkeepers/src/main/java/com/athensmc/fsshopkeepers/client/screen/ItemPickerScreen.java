package com.athensmc.fsshopkeepers.client.screen;

import com.athensmc.fsshopkeepers.client.theme.FSGui;
import com.athensmc.fsshopkeepers.client.widget.FSButton;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Choosing the item a trade hands over.
 *
 * <p>Two sources, switched at the top: everything the server knows about, or just what is in your own inventory. The
 * inventory list is the one that gets used, because the common case is selling something you are holding and finding a
 * specific enchanted book among a thousand registry entries is not a thing anyone should have to do. Picking from the
 * inventory also carries the item's NBT, so an enchanted or renamed item can be sold as itself.</p>
 *
 * <p>Chosen items are copied. A trade holding a live reference to a stack in the player's inventory would change every
 * time they moved it.</p>
 */
public final class ItemPickerScreen extends Screen {

    private static final int MAX_PANEL_WIDTH = 540;
    private static final int MAX_PANEL_HEIGHT = 320;
    private static final int GUTTER = 8;
    private static final int TITLE_HEIGHT = 20;
    private static final int CONTROL_HEIGHT = 18;

    /** Item cells, sized so an 18-pixel icon has a pixel of air around it. */
    private static final int CELL = 20;

    private final Screen parent;
    private final Consumer<ItemStack> onChosen;

    private final List<ItemStack> shown = new ArrayList<>();
    private String search = "";
    private boolean inventoryOnly = true;
    private int scroll;

    private int leftPos;
    private int topPos;
    private int panelWidth;
    private int panelHeight;

    private EditBox searchBox;

    public ItemPickerScreen(Screen parent, Consumer<ItemStack> onChosen) {
        super(Component.literal("Elegir articulo"));
        this.parent = parent;
        this.onChosen = onChosen;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(width - 16, MAX_PANEL_WIDTH);
        panelHeight = Math.min(height - 16, MAX_PANEL_HEIGHT);
        leftPos = (width - panelWidth) / 2;
        topPos = (height - panelHeight) / 2;

        int toggleWidth = 130;
        searchBox = new EditBox(font, leftPos + GUTTER + 1, topPos + 25, panelWidth - GUTTER * 2 - toggleWidth - 6,
                CONTROL_HEIGHT - 2, Component.literal("Buscar"));
        searchBox.setValue(search);
        searchBox.setHint(Component.literal("buscar articulo..."));
        searchBox.setResponder(text -> {
            if (!text.equals(search)) {
                search = text;
                rebuild();
            }
        });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        addRenderableWidget(new FSButton(leftPos + panelWidth - GUTTER - toggleWidth, topPos + 24, toggleWidth,
                CONTROL_HEIGHT, Component.literal(inventoryOnly ? "\u00a7aMi inventario" : "\u00a7fTodo el juego"),
                inventoryOnly ? FSGui.ACCENT_GREEN : FSGui.ACCENT_BLUE, () -> {
                    inventoryOnly = !inventoryOnly;
                    rebuild();
                    rebuildWidgets();
                }));

        addRenderableWidget(new FSButton(leftPos + GUTTER, topPos + panelHeight - 24, 80, CONTROL_HEIGHT,
                Component.literal("Cerrar"), FSGui.PANEL_BORDER, this::onClose));

        rebuild();
    }

    private int gridTop() {
        return topPos + 24 + CONTROL_HEIGHT + 6;
    }

    private int gridBottom() {
        return topPos + panelHeight - 28;
    }

    private int columns() {
        int usable = panelWidth - GUTTER * 2 - 6;
        return Math.max(1, usable / CELL);
    }

    private int visibleRows() {
        return Math.max(1, (gridBottom() - gridTop()) / CELL);
    }

    private int cellX(int slot) {
        return leftPos + GUTTER + slot % columns() * CELL;
    }

    private int cellY(int slot) {
        return gridTop() + slot / columns() * CELL;
    }

    /**
     * Rebuilds the visible list.
     *
     * <p>Filtering a few thousand items on each keystroke is cheap enough to do eagerly, and eagerly is what keeps the
     * grid and the search box from ever disagreeing.</p>
     */
    private void rebuild() {
        shown.clear();
        String needle = search.toLowerCase();
        if (inventoryOnly) {
            if (minecraft != null && minecraft.player != null) {
                for (ItemStack stack : minecraft.player.getInventory().items) {
                    if (!stack.isEmpty() && matches(stack, needle)) {
                        shown.add(stack.copy());
                    }
                }
            }
        } else {
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(item);
                if (!stack.isEmpty() && matches(stack, needle)) {
                    shown.add(stack);
                }
            }
        }
        scroll = 0;
    }

    private boolean matches(ItemStack stack, String needle) {
        if (needle.isEmpty()) {
            return true;
        }
        if (stack.getHoverName().getString().toLowerCase().contains(needle)) {
            return true;
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains(needle);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(leftPos, topPos, leftPos + panelWidth, topPos + panelHeight, FSGui.WINDOW_BG);
        graphics.fill(leftPos, topPos, leftPos + panelWidth, topPos + TITLE_HEIGHT, FSGui.TITLE_BAR);
        graphics.fill(leftPos, topPos + panelHeight - 1, leftPos + panelWidth, topPos + panelHeight,
                FSGui.RULE);
        graphics.drawString(font, "\u00a7d\u2726 \u00a7fElegir articulo \u00a7d\u2726 \u00a77- "
                + shown.size() + " disponibles", leftPos + GUTTER, topPos + 6, 0xFFFFFF, false);

        int gridHeight = visibleRows() * CELL;
        FSGui.inset(graphics, leftPos + GUTTER - 2, gridTop() - 2, panelWidth - GUTTER * 2 + 4, gridHeight + 4);

        int perPage = columns() * visibleRows();
        int maxScroll = Math.max(0, (shown.size() - perPage + columns() - 1) / columns());
        scroll = Math.min(scroll, maxScroll);
        int first = scroll * columns();

        ItemStack hovered = ItemStack.EMPTY;
        for (int slot = 0; slot < perPage; slot++) {
            int index = first + slot;
            if (index >= shown.size()) {
                break;
            }
            int x = cellX(slot);
            int y = cellY(slot);
            boolean isHovered = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
            if (isHovered) {
                graphics.fill(x, y, x + CELL, y + CELL, 0x40FFFFFF);
            }
            ItemStack stack = shown.get(index);
            graphics.renderItem(stack, x + 1, y + 1);
            graphics.renderItemDecorations(font, stack, x + 1, y + 1);
            if (isHovered) {
                hovered = stack;
            }
        }

        if (maxScroll > 0) {
            int trackX = leftPos + panelWidth - GUTTER - 3;
            int thumbHeight = FSGui.thumbHeight(gridHeight, visibleRows(),
                    visibleRows() + maxScroll);
            FSGui.scrollbar(graphics, trackX, gridTop(), 5, gridHeight,
                    FSGui.thumbTop(gridTop(), gridHeight, thumbHeight, scroll, maxScroll), thumbHeight, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawString(font, "\u00a77Clic para elegir. Esc para volver sin cambiar nada.",
                leftPos + GUTTER + 90, topPos + panelHeight - 19, FSGui.HELP_TEXT, false);

        // Last, so the tooltip sits above the grid instead of under the next row of icons.
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int perPage = columns() * visibleRows();
        int first = scroll * columns();
        for (int slot = 0; slot < perPage; slot++) {
            int index = first + slot;
            if (index >= shown.size()) {
                break;
            }
            int x = cellX(slot);
            int y = cellY(slot);
            if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) {
                ItemStack chosen = shown.get(index).copy();
                if (chosen.getCount() < 1) {
                    chosen.setCount(1);
                }
                onChosen.accept(chosen);
                minecraft.setScreen(parent);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY >= gridTop() && mouseY < gridBottom()) {
            scroll = Math.max(0, scroll - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        // Back to the editor, never to the world: closing without choosing must not discard the admin's edits.
        minecraft.setScreen(parent);
    }
}
