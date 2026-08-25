package com.athensmc.fsshopkeepers.client.screen;

import com.athensmc.fsshopkeepers.client.layout.EditorLayout;
import com.athensmc.fsshopkeepers.client.layout.Rect;
import com.athensmc.fsshopkeepers.client.theme.FantasticTheme;

import net.minecraft.client.gui.GuiGraphics;
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
 * <p>Two sources, switched between at the top: everything the server knows about, or just what is in your own
 * inventory. The inventory list exists because the common case is selling something you are holding, and finding a
 * specific enchanted book among nine hundred registry entries is not a thing anyone should have to do. Picking from
 * the inventory also carries the item's NBT, so an enchanted or renamed item can be sold as itself.</p>
 *
 * <p>Chosen items are copied, not referenced. A trade holding a live reference to a stack in the player's inventory
 * would change every time they moved it.</p>
 */
public final class ItemPickerScreen extends Screen {

    private static final int CELL = 20;
    private static final int SEARCH_HEIGHT = 18;

    private final Screen parent;
    private final Consumer<ItemStack> onChosen;

    private final List<ItemStack> shown = new ArrayList<>();
    private String search = "";
    private boolean inventoryOnly;
    private int scroll;
    private String hint = "Elige un articulo. Escribe para buscar.";

    public ItemPickerScreen(Screen parent, Consumer<ItemStack> onChosen) {
        super(Component.literal("Elegir articulo"));
        this.parent = parent;
        this.onChosen = onChosen;
    }

    @Override
    protected void init() {
        super.init();
        rebuild();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Rebuilds the visible list.
     *
     * <p>Called on every change to the search text. Filtering a few thousand items on each keystroke is cheap enough
     * to do eagerly, and doing it eagerly keeps the list and the search box from ever disagreeing.</p>
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
                if (stack.isEmpty()) {
                    continue;
                }
                if (matches(stack, needle)) {
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

    private Rect searchBox() {
        return new Rect(EditorLayout.MARGIN, EditorLayout.MARGIN,
                Math.max(0, width - EditorLayout.MARGIN * 2 - 150 - EditorLayout.GAP), SEARCH_HEIGHT);
    }

    private Rect sourceToggle() {
        Rect search = searchBox();
        return new Rect(search.right() + EditorLayout.GAP, EditorLayout.MARGIN, 150, SEARCH_HEIGHT);
    }

    private Rect gridArea() {
        int top = EditorLayout.MARGIN + SEARCH_HEIGHT + EditorLayout.GAP;
        int bottom = height - EditorLayout.MARGIN - EditorLayout.HELP_HEIGHT - EditorLayout.GAP;
        return new Rect(EditorLayout.MARGIN, top, Math.max(0, width - EditorLayout.MARGIN * 2),
                Math.max(0, bottom - top));
    }

    private int columns() {
        Rect grid = gridArea();
        return Math.max(1, (grid.width() - EditorLayout.SCROLLBAR_WIDTH - EditorLayout.GAP + 2) / (CELL + 2));
    }

    private int rows() {
        Rect grid = gridArea();
        return Math.max(0, (grid.height() + 2) / (CELL + 2));
    }

    private Rect cell(int slot) {
        int columns = columns();
        if (columns <= 0) {
            return Rect.EMPTY;
        }
        Rect grid = gridArea();
        int column = slot % columns;
        int row = slot / columns;
        return new Rect(grid.x() + column * (CELL + 2), grid.y() + row * (CELL + 2), CELL, CELL);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, FantasticTheme.BACKDROP);
        hint = "Clic en un articulo para elegirlo. Esc para volver.";

        Rect search = searchBox();
        FantasticTheme.field(graphics, search, true, true);
        String shownText = search.isEmpty() ? "" : (this.search.isEmpty() ? "buscar..." : this.search + "_");
        FantasticTheme.leftText(graphics, font, search.shrink(3), shownText,
                this.search.isEmpty() ? FantasticTheme.TEXT_DISABLED : FantasticTheme.TEXT);

        Rect toggle = sourceToggle();
        boolean toggleHovered = toggle.contains(mouseX, mouseY);
        FantasticTheme.button(graphics, toggle,
                inventoryOnly ? FantasticTheme.Style.SELECTED : FantasticTheme.Style.PLAIN, toggleHovered, true);
        FantasticTheme.centeredText(graphics, font, toggle,
                inventoryOnly ? "Mi inventario" : "Todo el juego",
                FantasticTheme.textOn(inventoryOnly ? FantasticTheme.Style.SELECTED
                        : FantasticTheme.Style.PLAIN, true));
        if (toggleHovered) {
            hint = inventoryOnly
                    ? "Mostrando solo tu inventario. Clic para ver todos los articulos."
                    : "Mostrando todos los articulos. Clic para ver solo tu inventario.";
        }

        Rect grid = gridArea();
        FantasticTheme.panel(graphics, grid, FantasticTheme.PANEL_DARK);

        int perPage = columns() * rows();
        int maxScroll = Math.max(0, (shown.size() - perPage + columns() - 1) / Math.max(1, columns()));
        scroll = Math.min(scroll, maxScroll);
        int first = scroll * columns();

        ItemStack hovered = ItemStack.EMPTY;
        for (int slot = 0; slot < perPage; slot++) {
            int index = first + slot;
            if (index >= shown.size()) {
                break;
            }
            Rect rect = cell(slot);
            if (rect.bottom() > grid.bottom()) {
                break;
            }
            boolean isHovered = rect.contains(mouseX, mouseY);
            FantasticTheme.slot(graphics, rect, isHovered);
            ItemStack stack = shown.get(index);
            graphics.renderItem(stack, rect.x() + 2, rect.y() + 2);
            graphics.renderItemDecorations(font, stack, rect.x() + 2, rect.y() + 2);
            if (isHovered) {
                hovered = stack;
            }
        }

        Rect helpRect = new Rect(EditorLayout.MARGIN, height - EditorLayout.MARGIN - EditorLayout.HELP_HEIGHT,
                Math.max(0, width - EditorLayout.MARGIN * 2), EditorLayout.HELP_HEIGHT);
        FantasticTheme.leftText(graphics, font, helpRect,
                shown.size() + " articulos  ·  " + hint, FantasticTheme.TEXT_MUTED);

        super.render(graphics, mouseX, mouseY, partialTick);
        // The tooltip is drawn last so it sits above the grid rather than under the next row of icons.
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (sourceToggle().contains(mouseX, mouseY)) {
            inventoryOnly = !inventoryOnly;
            rebuild();
            return true;
        }
        int perPage = columns() * rows();
        int first = scroll * columns();
        for (int slot = 0; slot < perPage; slot++) {
            int index = first + slot;
            if (index >= shown.size()) {
                break;
            }
            if (cell(slot).contains(mouseX, mouseY)) {
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
        if (gridArea().contains(mouseX, mouseY)) {
            scroll = Math.max(0, scroll - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (search.length() < 32) {
            search += codePoint;
            rebuild();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            minecraft.setScreen(parent);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                rebuild();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        // Closing without choosing must return to the editor, not to the world, or the admin loses their edits.
        minecraft.setScreen(parent);
    }
}
