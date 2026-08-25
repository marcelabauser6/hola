package com.athensmc.fsshopkeepers.client.screen;

import com.athensmc.fsshopkeepers.client.MobVariants;
import com.athensmc.fsshopkeepers.client.layout.EditorLayout;
import com.athensmc.fsshopkeepers.client.layout.Rect;
import com.athensmc.fsshopkeepers.client.theme.FantasticTheme;
import com.athensmc.fsshopkeepers.money.Cash;
import com.athensmc.fsshopkeepers.money.Money;
import com.athensmc.fsshopkeepers.net.DeleteShopPacket;
import com.athensmc.fsshopkeepers.net.Net;
import com.athensmc.fsshopkeepers.net.SaveShopPacket;
import com.athensmc.fsshopkeepers.shop.ShopObjectKind;
import com.athensmc.fsshopkeepers.shop.Shopkeeper;
import com.athensmc.fsshopkeepers.shop.TradeOffer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The screen an administrator sets a shop up on.
 *
 * <p>Full screen and organised as bands and tabs rather than as a chest of icons. Shopkeepers put its editor in a
 * double chest, which meant every control was an item you had to know the meaning of and the number of trades was
 * capped by the number of slots. Here a trade is a row that says what it is, prices are typed as numbers, and the list
 * scrolls.</p>
 *
 * <p>All the geometry comes from {@link EditorLayout}, which is checked by a test for overlaps at fourteen window
 * sizes, and every label is drawn through {@link FantasticTheme}'s truncating helpers. Those two together are what
 * keeps text off other text: the layout stops controls sharing pixels, and the truncation stops a long item name
 * running out of its cell into the control beside it.</p>
 *
 * <p>Text fields are drawn and edited by this screen rather than by {@code EditBox} widgets. A widget carries its own
 * position, so a scrolling list of them has to be rebuilt every time the list moves, and a stale widget left behind is
 * a field floating over the wrong row. Editing a plain string against the row the layout points at cannot drift.</p>
 */
public final class ShopEditorScreen extends Screen {

    /** Which tab is showing. */
    private enum Tab {
        TRADES("Tratos", "Que vende la tienda y a que precio."),
        APPEARANCE("Apariencia", "El cuerpo y el nombre del tendero."),
        SETTINGS("Ajustes", "Cuenta bancaria, traspaso y permisos.");

        private final String title;
        private final String help;

        Tab(String title, String help) {
            this.title = title;
            this.help = help;
        }
    }

    /** What the keyboard is currently typing into. */
    private enum FieldTarget {
        NONE, PRICE, SHOP_NAME, HIRE_COST, ACCOUNT, PERMISSION, MOB_SEARCH
    }

    /**
     * One editable trade row.
     *
     * <p>The price is held as the text the admin typed, not as a number. Parsing on every keystroke and storing the
     * result would make {@code "25."} unrepresentable half way through being typed, so the text is kept and only
     * turned into cents when the shop is saved.</p>
     */
    private static final class Row {
        ItemStack result;
        String priceText;
        ItemStack cost1;
        ItemStack cost2;

        Row(ItemStack result, String priceText, ItemStack cost1, ItemStack cost2) {
            this.result = result;
            this.priceText = priceText;
            this.cost1 = cost1;
            this.cost2 = cost2;
        }

        static Row from(TradeOffer offer) {
            return new Row(offer.result().copy(),
                    offer.hasCashPrice() ? offer.priceText() : "",
                    offer.cost1().copy(), offer.cost2().copy());
        }

        boolean priceValid() {
            return priceText.isBlank() || Money.parseOrInvalid(priceText) >= 0L;
        }

        long priceCents() {
            if (priceText.isBlank()) {
                return 0L;
            }
            return Math.max(0L, Money.parseOrInvalid(priceText));
        }

        TradeOffer toOffer() {
            return new TradeOffer(result, priceCents(), cost1, cost2);
        }
    }

    private final Shopkeeper.EditorView source;

    private final List<Row> rows = new ArrayList<>();
    private String shopName;
    private ShopObjectKind kind;
    private ResourceLocation entityType;
    private CompoundTag entityData;
    private String accountText;
    private boolean forHire;
    private String hireCostText;
    private String permission;

    private Tab tab = Tab.TRADES;
    private int scroll;
    private FieldTarget focus = FieldTarget.NONE;
    private int focusedRow = -1;
    private String mobSearch = "";

    private EditorLayout layout;
    private String hint = "";

    /** Mob types offered in the appearance tab, filtered by the search box. */
    private final List<ResourceLocation> mobChoices = new ArrayList<>();

    public ShopEditorScreen(Shopkeeper.EditorView view) {
        super(Component.literal("Editor de tienda"));
        this.source = view;
        this.shopName = view.name();
        this.kind = view.objectKind();
        this.entityType = view.entityType();
        this.entityData = view.entityData().copy();
        this.accountText = view.linkedAccount() > 0 ? String.valueOf(view.linkedAccount()) : "";
        this.forHire = view.forHire();
        this.hireCostText = view.hireCost() > 0L ? Money.plain(view.hireCost()) : "";
        this.permission = view.tradePermission();
        for (TradeOffer offer : view.offers()) {
            rows.add(Row.from(offer));
        }
        // An empty shop opens with one blank row, so the first thing an admin sees is somewhere to start rather
        // than an empty list and a button to find.
        if (rows.isEmpty()) {
            rows.add(new Row(ItemStack.EMPTY, "", ItemStack.EMPTY, ItemStack.EMPTY));
        }
        rebuildMobChoices();
    }

    @Override
    protected void init() {
        super.init();
        layout = new EditorLayout(width, height);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void rebuildMobChoices() {
        mobChoices.clear();
        String needle = mobSearch.toLowerCase();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            // Only things that can stand somewhere and be clicked. Projectiles and falling blocks are in the
            // registry too and would be nonsense as shopkeepers.
            boolean usable = type.getCategory() != MobCategory.MISC || type == EntityType.ARMOR_STAND;
            if (!usable) {
                continue;
            }
            ResourceLocation id = EntityType.getKey(type);
            String label = type.getDescription().getString().toLowerCase();
            if (needle.isEmpty() || label.contains(needle) || id.getPath().contains(needle)) {
                mobChoices.add(id);
            }
        }
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hint = tab.help;
        graphics.fill(0, 0, width, height, FantasticTheme.BACKDROP);

        drawHeader(graphics);
        drawTabs(graphics, mouseX, mouseY);
        switch (tab) {
            case TRADES -> drawTradesTab(graphics, mouseX, mouseY);
            case APPEARANCE -> drawAppearanceTab(graphics, mouseX, mouseY);
            case SETTINGS -> drawSettingsTab(graphics, mouseX, mouseY);
        }
        drawFooter(graphics, mouseX, mouseY);
        drawHelp(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawHeader(GuiGraphics graphics) {
        Rect header = layout.header();
        FantasticTheme.panel(graphics, header, FantasticTheme.PANEL);
        Rect inner = header.shrink(EditorLayout.GAP);

        String title = shopName.isBlank() ? source.type().title() : shopName;
        Rect titleRect = new Rect(inner.x(), inner.y(), inner.width() * 2 / 3, 10);
        FantasticTheme.leftText(graphics, font, titleRect, title, FantasticTheme.ACCENT);

        String subtitle = source.type().title()
                + (source.ownerName().isBlank() ? "" : "  ·  dueño: " + source.ownerName())
                + "  ·  " + kind.title();
        Rect subtitleRect = new Rect(inner.x(), inner.y() + 12, inner.width() * 2 / 3, 10);
        FantasticTheme.leftText(graphics, font, subtitleRect, subtitle, FantasticTheme.TEXT_MUTED);

        Rect countRect = new Rect(inner.x() + inner.width() * 2 / 3, inner.y() + 6,
                inner.width() / 3, 10);
        long usable = rows.stream().filter(row -> row.toOffer().isComplete()).count();
        FantasticTheme.rightText(graphics, font, countRect, usable + " / " + rows.size() + " tratos listos",
                FantasticTheme.TEXT_MUTED);
    }

    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Rect rect = layout.tab(i, tabs.length);
            boolean selected = tabs[i] == tab;
            boolean hovered = rect.contains(mouseX, mouseY);
            FantasticTheme.Style style = selected ? FantasticTheme.Style.SELECTED : FantasticTheme.Style.PLAIN;
            FantasticTheme.button(graphics, rect, style, hovered, true);
            FantasticTheme.centeredText(graphics, font, rect, tabs[i].title,
                    FantasticTheme.textOn(style, true));
            if (hovered) {
                hint = tabs[i].help;
            }
        }
    }

    private void drawTradesTab(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect addButton = layout.addTradeButton();
        boolean canAdd = rows.size() < 45;
        boolean addHovered = addButton.contains(mouseX, mouseY);
        FantasticTheme.button(graphics, addButton, FantasticTheme.Style.PLAIN, addHovered, canAdd);
        FantasticTheme.centeredText(graphics, font, addButton, "+ Anadir trato",
                FantasticTheme.textOn(FantasticTheme.Style.PLAIN, canAdd));
        if (addHovered) {
            hint = canAdd ? "Anade una fila vacia al final de la lista."
                    : "Has alcanzado el maximo de 45 tratos.";
        }

        Rect countLabel = layout.tradeCountLabel();
        String currency = Cash.available() ? Cash.symbol() : "sin economia";
        FantasticTheme.rightText(graphics, font, countLabel, "Precios en " + currency,
                FantasticTheme.TEXT_MUTED);

        int visible = layout.visibleRows();
        clampScroll(rows.size(), visible);
        for (int slot = 0; slot < visible; slot++) {
            int index = scroll + slot;
            if (index >= rows.size()) {
                break;
            }
            drawTradeRow(graphics, layout.row(slot), rows.get(index), index, mouseX, mouseY);
        }
        drawScrollbar(graphics, rows.size(), visible);
    }

    private void drawTradeRow(GuiGraphics graphics, Rect row, Row data, int index, int mouseX, int mouseY) {
        boolean rowHovered = row.contains(mouseX, mouseY);
        FantasticTheme.panel(graphics, row, rowHovered ? FantasticTheme.PANEL_LIGHT : FantasticTheme.PANEL);
        EditorLayout.TradeRowCells cells = layout.tradeRow(row);

        // The item on sale.
        FantasticTheme.slot(graphics, cells.icon(), cells.icon().contains(mouseX, mouseY));
        if (!data.result.isEmpty()) {
            graphics.renderItem(data.result, cells.icon().x() + 1, cells.icon().y() + 1);
            graphics.renderItemDecorations(font, data.result, cells.icon().x() + 1, cells.icon().y() + 1);
        }
        if (cells.icon().contains(mouseX, mouseY)) {
            hint = "Clic para elegir el articulo que vende esta fila.";
        }

        String label = data.result.isEmpty() ? "(sin articulo)" : data.result.getHoverName().getString();
        FantasticTheme.leftText(graphics, font, cells.name(), label,
                data.result.isEmpty() ? FantasticTheme.TEXT_DISABLED : FantasticTheme.TEXT);

        // Quantity stepper.
        drawStepper(graphics, cells.minus(), "-", mouseX, mouseY, !data.result.isEmpty());
        FantasticTheme.panel(graphics, cells.count(), FantasticTheme.PANEL_DARK);
        FantasticTheme.centeredText(graphics, font, cells.count(),
                data.result.isEmpty() ? "-" : String.valueOf(data.result.getCount()), FantasticTheme.TEXT);
        drawStepper(graphics, cells.plus(), "+", mouseX, mouseY, !data.result.isEmpty());
        if (cells.count().contains(mouseX, mouseY) || cells.minus().contains(mouseX, mouseY)
                || cells.plus().contains(mouseX, mouseY)) {
            hint = "Cuantas unidades entrega la tienda por cada compra.";
        }

        // Price.
        boolean priceFocused = focus == FieldTarget.PRICE && focusedRow == index;
        FantasticTheme.field(graphics, cells.price(), priceFocused, data.priceValid());
        String priceShown = data.priceText.isBlank() && !priceFocused ? "0.00" : data.priceText;
        int priceColour = !data.priceValid() ? FantasticTheme.NEGATIVE
                : data.priceText.isBlank() ? FantasticTheme.TEXT_DISABLED : FantasticTheme.TEXT;
        Rect priceInner = cells.price().shrink(3);
        FantasticTheme.leftText(graphics, font, priceInner,
                priceShown + (priceFocused ? "_" : ""), priceColour);
        if (cells.price().contains(mouseX, mouseY)) {
            hint = "Precio en " + Cash.currencyName() + ". Admite coma o punto y dos decimales.";
        }

        // Item payment slots.
        drawPaySlot(graphics, cells.pay1(), data.cost1, mouseX, mouseY, 1);
        drawPaySlot(graphics, cells.pay2(), data.cost2, mouseX, mouseY, 2);

        // Delete.
        boolean deleteHovered = cells.delete().contains(mouseX, mouseY);
        FantasticTheme.button(graphics, cells.delete(), FantasticTheme.Style.DANGER, deleteHovered, true);
        FantasticTheme.centeredText(graphics, font, cells.delete(), "x", FantasticTheme.TEXT);
        if (deleteHovered) {
            hint = "Borra esta fila.";
        }

        // An incomplete row says why, in the help line, so the reason is never a mystery.
        String reason = data.toOffer().incompleteReason();
        if (rowHovered && reason != null) {
            hint = reason;
        }
    }

    private void drawStepper(GuiGraphics graphics, Rect rect, String glyph, int mouseX, int mouseY,
            boolean enabled) {
        boolean hovered = rect.contains(mouseX, mouseY);
        FantasticTheme.button(graphics, rect, FantasticTheme.Style.PLAIN, hovered, enabled);
        FantasticTheme.centeredText(graphics, font, rect, glyph,
                FantasticTheme.textOn(FantasticTheme.Style.PLAIN, enabled));
    }

    private void drawPaySlot(GuiGraphics graphics, Rect rect, ItemStack stack, int mouseX, int mouseY,
            int which) {
        boolean hovered = rect.contains(mouseX, mouseY);
        FantasticTheme.slot(graphics, rect, hovered);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, rect.x() + 1, rect.y() + 1);
            graphics.renderItemDecorations(font, stack, rect.x() + 1, rect.y() + 1);
        }
        if (hovered) {
            hint = "Articulo de pago " + which + " (opcional). Clic izquierdo elige, derecho lo quita.";
        }
    }

    private void drawAppearanceTab(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect bar = layout.actionBar();
        // The three body kinds share the action bar.
        ShopObjectKind[] kinds = ShopObjectKind.values();
        int each = (bar.width() - EditorLayout.GAP * (kinds.length - 1)) / Math.max(1, kinds.length);
        for (int i = 0; i < kinds.length; i++) {
            Rect rect = new Rect(bar.x() + i * (each + EditorLayout.GAP), bar.y(),
                    i == kinds.length - 1 ? bar.right() - (bar.x() + i * (each + EditorLayout.GAP)) : each,
                    bar.height());
            boolean selected = kinds[i] == kind;
            boolean hovered = rect.contains(mouseX, mouseY);
            FantasticTheme.Style style = selected ? FantasticTheme.Style.SELECTED : FantasticTheme.Style.PLAIN;
            FantasticTheme.button(graphics, rect, style, hovered, true);
            FantasticTheme.centeredText(graphics, font, rect, kinds[i].title(),
                    FantasticTheme.textOn(style, true));
            if (hovered) {
                hint = kinds[i].description();
            }
        }

        int visible = layout.visibleRows();
        if (visible <= 0) {
            return;
        }

        // Row 0: the shop's name.
        EditorLayout.SettingRow nameRow = layout.settingRow(0);
        FantasticTheme.leftText(graphics, font, nameRow.label(), "Nombre de la tienda",
                FantasticTheme.TEXT);
        drawTextField(graphics, nameRow.control(), shopName, focus == FieldTarget.SHOP_NAME,
                "sin nombre", true, mouseX, mouseY, "El nombre que flota sobre el tendero.");

        int usedRows = 1;

        // Variant options, one per row, only when the body is a mob.
        List<MobVariants.Option> options = kind == ShopObjectKind.LIVING
                ? MobVariants.optionsFor(entityType) : List.of();
        for (int i = 0; i < options.size() && usedRows < visible; i++, usedRows++) {
            MobVariants.Option option = options.get(i);
            EditorLayout.SettingRow settingRow = layout.settingRow(usedRows);
            FantasticTheme.leftText(graphics, font, settingRow.label(), option.label(), FantasticTheme.TEXT);
            Rect control = settingRow.control();
            boolean hovered = control.contains(mouseX, mouseY);
            FantasticTheme.button(graphics, control, FantasticTheme.Style.PLAIN, hovered, true);
            FantasticTheme.centeredText(graphics, font, control, option.currentLabel(entityData),
                    FantasticTheme.TEXT);
            if (hovered) {
                hint = "Clic para cambiar: " + option.label().toLowerCase() + ".";
            }
        }

        if (kind != ShopObjectKind.LIVING) {
            if (usedRows < visible) {
                Rect row = layout.row(usedRows);
                FantasticTheme.leftText(graphics, font, row.shrink(EditorLayout.GAP),
                        kind == ShopObjectKind.SIGN
                                ? "Una tienda de cartel no tiene mob que configurar."
                                : "Una tienda virtual no tiene cuerpo. Se abre solo por comando.",
                        FantasticTheme.TEXT_MUTED);
            }
            return;
        }

        // The mob search field and grid take the rest.
        if (usedRows < visible) {
            EditorLayout.SettingRow searchRow = layout.settingRow(usedRows);
            FantasticTheme.leftText(graphics, font, searchRow.label(), "Buscar mob", FantasticTheme.TEXT);
            drawTextField(graphics, searchRow.control(), mobSearch, focus == FieldTarget.MOB_SEARCH,
                    "escribe para filtrar", true, mouseX, mouseY, "Filtra la lista de mobs de abajo.");
            usedRows++;
        }

        drawMobGrid(graphics, mouseX, mouseY, usedRows);
    }

    /**
     * The grid of mob choices, drawn below whatever rows the tab already used.
     *
     * <p>The grid starts at a row boundary rather than at a free pixel, so its cells line up with the rows above and
     * the tab does not look like two screens stitched together.</p>
     */
    private void drawMobGrid(GuiGraphics graphics, int mouseX, int mouseY, int startRow) {
        Rect rowsArea = layout.rowsArea();
        int gridTop = layout.row(Math.min(startRow, Math.max(0, layout.visibleRows() - 1))).y();
        if (gridTop <= 0 || gridTop >= rowsArea.bottom()) {
            return;
        }
        EditorLayout gridLayout = new EditorLayout(width, height);
        int columns = gridLayout.mobColumns();
        int cellHeight = 22;
        int availableHeight = rowsArea.bottom() - gridTop;
        int gridRows = Math.max(0, (availableHeight + EditorLayout.GAP) / (cellHeight + EditorLayout.GAP));
        if (columns <= 0 || gridRows <= 0) {
            return;
        }
        int perPage = columns * gridRows;
        clampScroll(mobChoices.size(), perPage);

        int usable = rowsArea.width() - EditorLayout.SCROLLBAR_WIDTH - EditorLayout.GAP;
        int cellWidth = Math.max(1, (usable - EditorLayout.GAP * (columns - 1)) / columns);

        for (int slot = 0; slot < perPage; slot++) {
            int index = scroll + slot;
            if (index >= mobChoices.size()) {
                break;
            }
            int column = slot % columns;
            int rowIndex = slot / columns;
            Rect cell = new Rect(rowsArea.x() + column * (cellWidth + EditorLayout.GAP),
                    gridTop + rowIndex * (cellHeight + EditorLayout.GAP), cellWidth, cellHeight);
            if (cell.bottom() > rowsArea.bottom()) {
                break;
            }
            ResourceLocation id = mobChoices.get(index);
            boolean selected = id.equals(entityType);
            boolean hovered = cell.contains(mouseX, mouseY);
            FantasticTheme.Style style = selected ? FantasticTheme.Style.SELECTED : FantasticTheme.Style.PLAIN;
            FantasticTheme.button(graphics, cell, style, hovered, true);
            String label = EntityType.byString(id.toString())
                    .map(type -> type.getDescription().getString()).orElse(id.getPath());
            FantasticTheme.centeredText(graphics, font, cell, label, FantasticTheme.textOn(style, true));
            if (hovered) {
                hint = "Clic para que el tendero sea un " + label.toLowerCase() + ".";
            }
        }
    }

    private void drawSettingsTab(GuiGraphics graphics, int mouseX, int mouseY) {
        int visible = layout.visibleRows();
        if (visible <= 0) {
            return;
        }
        int row = 0;

        if (row < visible) {
            EditorLayout.SettingRow accountRow = layout.settingRow(row++);
            FantasticTheme.leftText(graphics, font, accountRow.label(), "Cuenta bancaria",
                    FantasticTheme.TEXT);
            drawTextField(graphics, accountRow.control(), accountText, focus == FieldTarget.ACCOUNT,
                    "cuenta por defecto", true, mouseX, mouseY,
                    "Numero de cuenta donde se abonan las ventas. Vacio usa la tuya por defecto.");
        }

        if (row < visible) {
            EditorLayout.SettingRow hireRow = layout.settingRow(row++);
            FantasticTheme.leftText(graphics, font, hireRow.label(), "En traspaso", FantasticTheme.TEXT);
            Rect control = hireRow.control();
            boolean hovered = control.contains(mouseX, mouseY);
            FantasticTheme.button(graphics, control,
                    forHire ? FantasticTheme.Style.SELECTED : FantasticTheme.Style.PLAIN, hovered, true);
            FantasticTheme.centeredText(graphics, font, control, forHire ? "Si, se vende" : "No",
                    FantasticTheme.textOn(forHire ? FantasticTheme.Style.SELECTED
                            : FantasticTheme.Style.PLAIN, true));
            if (hovered) {
                hint = "Si esta activo, otro jugador puede comprar la tienda por el precio de traspaso.";
            }
        }

        if (row < visible) {
            EditorLayout.SettingRow costRow = layout.settingRow(row++);
            FantasticTheme.leftText(graphics, font, costRow.label(), "Precio de traspaso",
                    forHire ? FantasticTheme.TEXT : FantasticTheme.TEXT_DISABLED);
            drawTextField(graphics, costRow.control(), hireCostText, focus == FieldTarget.HIRE_COST,
                    forHire ? "0.00" : "activa el traspaso", forHire, mouseX, mouseY,
                    "Lo que cuesta quedarse con esta tienda.");
        }

        if (row < visible) {
            EditorLayout.SettingRow permRow = layout.settingRow(row++);
            FantasticTheme.leftText(graphics, font, permRow.label(), "Permiso para comerciar",
                    FantasticTheme.TEXT);
            drawTextField(graphics, permRow.control(), permission, focus == FieldTarget.PERMISSION,
                    "cualquiera puede comprar", true, mouseX, mouseY,
                    "Solo staff. Nodo de permiso que hace falta para comprar aqui.");
        }

        if (row < visible) {
            Rect infoRow = layout.row(row);
            String economy = Cash.available()
                    ? "Fantastic Currency detectado: los precios se cobran en " + Cash.currencyName() + "."
                    : "Fantastic Currency no esta instalado: solo funcionaran los pagos con articulos.";
            FantasticTheme.leftText(graphics, font, infoRow.shrink(EditorLayout.GAP), economy,
                    Cash.available() ? FantasticTheme.POSITIVE : FantasticTheme.NEGATIVE);
        }
    }

    private void drawTextField(GuiGraphics graphics, Rect rect, String value, boolean focused,
            String placeholder, boolean enabled, int mouseX, int mouseY, String help) {
        FantasticTheme.field(graphics, rect, focused, true);
        Rect inner = rect.shrink(3);
        boolean empty = value.isBlank();
        String shown = empty && !focused ? placeholder : value + (focused ? "_" : "");
        int colour = !enabled ? FantasticTheme.TEXT_DISABLED
                : empty && !focused ? FantasticTheme.TEXT_DISABLED : FantasticTheme.TEXT;
        FantasticTheme.leftText(graphics, font, inner, shown, colour);
        if (rect.contains(mouseX, mouseY)) {
            hint = help;
        }
    }

    private void drawScrollbar(GuiGraphics graphics, int total, int visible) {
        Rect track = layout.scrollbar(total);
        if (track.isEmpty()) {
            return;
        }
        FantasticTheme.panel(graphics, track, FantasticTheme.PANEL_DARK);
        FantasticTheme.fill(graphics, layout.scrollHandle(total, scroll), FantasticTheme.ACCENT_DIM);
    }

    private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect footer = layout.footer();
        FantasticTheme.panel(graphics, footer, FantasticTheme.PANEL);
        EditorLayout.FooterButtons buttons = layout.footerButtons();

        boolean canSave = allPricesValid();
        boolean saveHovered = buttons.save().contains(mouseX, mouseY);
        FantasticTheme.button(graphics, buttons.save(), FantasticTheme.Style.PRIMARY, saveHovered, canSave);
        FantasticTheme.centeredText(graphics, font, buttons.save(), "Guardar",
                FantasticTheme.textOn(FantasticTheme.Style.PRIMARY, canSave));
        if (saveHovered) {
            hint = canSave ? "Guarda la tienda y cierra el editor."
                    : "Corrige los precios en rojo antes de guardar.";
        }

        boolean cancelHovered = buttons.cancel().contains(mouseX, mouseY);
        FantasticTheme.button(graphics, buttons.cancel(), FantasticTheme.Style.PLAIN, cancelHovered, true);
        FantasticTheme.centeredText(graphics, font, buttons.cancel(), "Cancelar", FantasticTheme.TEXT);
        if (cancelHovered) {
            hint = "Cierra sin guardar nada.";
        }

        boolean deleteHovered = buttons.delete().contains(mouseX, mouseY);
        FantasticTheme.button(graphics, buttons.delete(), FantasticTheme.Style.DANGER, deleteHovered, true);
        FantasticTheme.centeredText(graphics, font, buttons.delete(), "Borrar tienda", FantasticTheme.TEXT);
        if (deleteHovered) {
            hint = "Borra la tienda entera. No se puede deshacer.";
        }

        if (!buttons.balance().isEmpty()) {
            String balance = Cash.available() && minecraft != null && minecraft.player != null
                    ? "Tu saldo: " + Cash.format(Cash.displayBalance(minecraft.player))
                    : "Sin economia disponible";
            FantasticTheme.leftText(graphics, font, buttons.balance(), balance, FantasticTheme.TEXT_MUTED);
        }
    }

    private void drawHelp(GuiGraphics graphics) {
        FantasticTheme.leftText(graphics, font, layout.help(), hint, FantasticTheme.TEXT_MUTED);
    }

    private boolean allPricesValid() {
        for (Row row : rows) {
            if (!row.priceValid()) {
                return false;
            }
        }
        return true;
    }

    private void clampScroll(int total, int visible) {
        int max = Math.max(0, total - visible);
        scroll = Math.max(0, Math.min(scroll, max));
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            if (layout.tab(i, tabs.length).contains(mouseX, mouseY)) {
                if (tab != tabs[i]) {
                    tab = tabs[i];
                    scroll = 0;
                    focus = FieldTarget.NONE;
                    focusedRow = -1;
                }
                return true;
            }
        }

        EditorLayout.FooterButtons buttons = layout.footerButtons();
        if (buttons.save().contains(mouseX, mouseY)) {
            if (allPricesValid()) {
                save();
            }
            return true;
        }
        if (buttons.cancel().contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (buttons.delete().contains(mouseX, mouseY)) {
            minecraft.setScreen(new ConfirmDeleteScreen(this, source.id()));
            return true;
        }

        boolean handled = switch (tab) {
            case TRADES -> clickTrades(mouseX, mouseY, button);
            case APPEARANCE -> clickAppearance(mouseX, mouseY, button);
            case SETTINGS -> clickSettings(mouseX, mouseY);
        };
        if (handled) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickTrades(double mouseX, double mouseY, int button) {
        if (layout.addTradeButton().contains(mouseX, mouseY)) {
            if (rows.size() < 45) {
                rows.add(new Row(ItemStack.EMPTY, "", ItemStack.EMPTY, ItemStack.EMPTY));
                // Scroll to the row just added, so it is not created off-screen.
                scroll = Math.max(0, rows.size() - layout.visibleRows());
            }
            return true;
        }
        int visible = layout.visibleRows();
        for (int slot = 0; slot < visible; slot++) {
            int index = scroll + slot;
            if (index >= rows.size()) {
                break;
            }
            Rect row = layout.row(slot);
            if (!row.contains(mouseX, mouseY)) {
                continue;
            }
            EditorLayout.TradeRowCells cells = layout.tradeRow(row);
            Row data = rows.get(index);

            if (cells.delete().contains(mouseX, mouseY)) {
                rows.remove(index);
                if (rows.isEmpty()) {
                    rows.add(new Row(ItemStack.EMPTY, "", ItemStack.EMPTY, ItemStack.EMPTY));
                }
                focus = FieldTarget.NONE;
                focusedRow = -1;
                return true;
            }
            if (cells.icon().contains(mouseX, mouseY)) {
                if (button == 1) {
                    data.result = ItemStack.EMPTY;
                } else {
                    openItemPicker(chosen -> data.result = chosen);
                }
                return true;
            }
            if (cells.pay1().contains(mouseX, mouseY)) {
                if (button == 1) {
                    data.cost1 = ItemStack.EMPTY;
                } else {
                    openItemPicker(chosen -> data.cost1 = chosen);
                }
                return true;
            }
            if (cells.pay2().contains(mouseX, mouseY)) {
                if (button == 1) {
                    data.cost2 = ItemStack.EMPTY;
                } else {
                    openItemPicker(chosen -> data.cost2 = chosen);
                }
                return true;
            }
            if (cells.price().contains(mouseX, mouseY)) {
                focus = FieldTarget.PRICE;
                focusedRow = index;
                return true;
            }
            if (cells.minus().contains(mouseX, mouseY) && !data.result.isEmpty()) {
                data.result.setCount(Math.max(1, data.result.getCount() - 1));
                return true;
            }
            if (cells.plus().contains(mouseX, mouseY) && !data.result.isEmpty()) {
                data.result.setCount(Math.min(data.result.getMaxStackSize(), data.result.getCount() + 1));
                return true;
            }
            focus = FieldTarget.NONE;
            focusedRow = -1;
            return true;
        }
        return false;
    }

    private boolean clickAppearance(double mouseX, double mouseY, int button) {
        Rect bar = layout.actionBar();
        ShopObjectKind[] kinds = ShopObjectKind.values();
        int each = (bar.width() - EditorLayout.GAP * (kinds.length - 1)) / Math.max(1, kinds.length);
        for (int i = 0; i < kinds.length; i++) {
            int x = bar.x() + i * (each + EditorLayout.GAP);
            Rect rect = new Rect(x, bar.y(), i == kinds.length - 1 ? bar.right() - x : each, bar.height());
            if (rect.contains(mouseX, mouseY)) {
                kind = kinds[i];
                return true;
            }
        }

        int visible = layout.visibleRows();
        int row = 0;
        if (row < visible && layout.settingRow(row).control().contains(mouseX, mouseY)) {
            focus = FieldTarget.SHOP_NAME;
            focusedRow = -1;
            return true;
        }
        row++;

        List<MobVariants.Option> options = kind == ShopObjectKind.LIVING
                ? MobVariants.optionsFor(entityType) : List.of();
        for (int i = 0; i < options.size() && row < visible; i++, row++) {
            if (layout.settingRow(row).control().contains(mouseX, mouseY)) {
                options.get(i).cycle(entityData);
                return true;
            }
        }
        if (kind != ShopObjectKind.LIVING) {
            return false;
        }
        if (row < visible && layout.settingRow(row).control().contains(mouseX, mouseY)) {
            focus = FieldTarget.MOB_SEARCH;
            focusedRow = -1;
            return true;
        }
        row++;

        return clickMobGrid(mouseX, mouseY, row);
    }

    private boolean clickMobGrid(double mouseX, double mouseY, int startRow) {
        Rect rowsArea = layout.rowsArea();
        int gridTop = layout.row(Math.min(startRow, Math.max(0, layout.visibleRows() - 1))).y();
        if (gridTop <= 0) {
            return false;
        }
        int columns = layout.mobColumns();
        int cellHeight = 22;
        int gridRows = Math.max(0, (rowsArea.bottom() - gridTop + EditorLayout.GAP)
                / (cellHeight + EditorLayout.GAP));
        if (columns <= 0 || gridRows <= 0) {
            return false;
        }
        int usable = rowsArea.width() - EditorLayout.SCROLLBAR_WIDTH - EditorLayout.GAP;
        int cellWidth = Math.max(1, (usable - EditorLayout.GAP * (columns - 1)) / columns);
        for (int slot = 0; slot < columns * gridRows; slot++) {
            int index = scroll + slot;
            if (index >= mobChoices.size()) {
                break;
            }
            int column = slot % columns;
            int rowIndex = slot / columns;
            Rect cell = new Rect(rowsArea.x() + column * (cellWidth + EditorLayout.GAP),
                    gridTop + rowIndex * (cellHeight + EditorLayout.GAP), cellWidth, cellHeight);
            if (cell.contains(mouseX, mouseY)) {
                if (!mobChoices.get(index).equals(entityType)) {
                    entityType = mobChoices.get(index);
                    // Variant data belongs to the old species and would be meaningless on the new one.
                    entityData = new CompoundTag();
                }
                return true;
            }
        }
        return false;
    }

    private boolean clickSettings(double mouseX, double mouseY) {
        int visible = layout.visibleRows();
        int row = 0;
        if (row < visible && layout.settingRow(row).control().contains(mouseX, mouseY)) {
            focus = FieldTarget.ACCOUNT;
            return true;
        }
        row++;
        if (row < visible && layout.settingRow(row).control().contains(mouseX, mouseY)) {
            forHire = !forHire;
            return true;
        }
        row++;
        if (row < visible && layout.settingRow(row).control().contains(mouseX, mouseY)) {
            if (forHire) {
                focus = FieldTarget.HIRE_COST;
            }
            return true;
        }
        row++;
        if (row < visible && layout.settingRow(row).control().contains(mouseX, mouseY)) {
            focus = FieldTarget.PERMISSION;
            return true;
        }
        return false;
    }

    private void openItemPicker(java.util.function.Consumer<ItemStack> onChosen) {
        minecraft.setScreen(new ItemPickerScreen(this, onChosen));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (layout.rowsArea().contains(mouseX, mouseY)) {
            // A wheel notch moves the list one row, in the direction the wheel turned. Clamping is left to the
            // draw pass, which is the only place that knows how many rows the current tab has.
            scroll = Math.max(0, scroll - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (focus == FieldTarget.NONE) {
            return super.charTyped(codePoint, modifiers);
        }
        switch (focus) {
            case PRICE -> {
                if (focusedRow >= 0 && focusedRow < rows.size() && isPriceChar(codePoint)) {
                    Row row = rows.get(focusedRow);
                    if (row.priceText.length() < 16) {
                        row.priceText += codePoint;
                    }
                }
            }
            case HIRE_COST -> {
                if (isPriceChar(codePoint) && hireCostText.length() < 16) {
                    hireCostText += codePoint;
                }
            }
            case ACCOUNT -> {
                if (codePoint >= '0' && codePoint <= '9' && accountText.length() < 10) {
                    accountText += codePoint;
                }
            }
            case SHOP_NAME -> {
                if (shopName.length() < SaveShopPacket.MAX_NAME) {
                    shopName += codePoint;
                }
            }
            case PERMISSION -> {
                if (permission.length() < 128) {
                    permission += codePoint;
                }
            }
            case MOB_SEARCH -> {
                if (mobSearch.length() < 32) {
                    mobSearch += codePoint;
                    scroll = 0;
                    rebuildMobChoices();
                }
            }
            default -> {
            }
        }
        return true;
    }

    /** Digits plus a single separator, so a price field cannot contain letters at all. */
    private boolean isPriceChar(char c) {
        return (c >= '0' && c <= '9') || c == '.' || c == ',';
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (focus != FieldTarget.NONE) {
                focus = FieldTarget.NONE;
                focusedRow = -1;
                return true;
            }
            onClose();
            return true;
        }
        if (focus != FieldTarget.NONE) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                backspace();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == GLFW.GLFW_KEY_TAB) {
                focus = FieldTarget.NONE;
                focusedRow = -1;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void backspace() {
        switch (focus) {
            case PRICE -> {
                if (focusedRow >= 0 && focusedRow < rows.size()) {
                    Row row = rows.get(focusedRow);
                    row.priceText = dropLast(row.priceText);
                }
            }
            case HIRE_COST -> hireCostText = dropLast(hireCostText);
            case ACCOUNT -> accountText = dropLast(accountText);
            case SHOP_NAME -> shopName = dropLast(shopName);
            case PERMISSION -> permission = dropLast(permission);
            case MOB_SEARCH -> {
                mobSearch = dropLast(mobSearch);
                scroll = 0;
                rebuildMobChoices();
            }
            default -> {
            }
        }
    }

    private String dropLast(String value) {
        return value.isEmpty() ? value : value.substring(0, value.length() - 1);
    }

    // ---------------------------------------------------------------- saving

    /**
     * Sends the shop back to the server and closes.
     *
     * <p>The screen closes immediately rather than waiting for confirmation. The server answers with a chat line
     * either way, and holding the editor open on a request that has already been sent invites a second click that
     * would send it twice.</p>
     */
    private void save() {
        List<TradeOffer> offers = new ArrayList<>(rows.size());
        for (Row row : rows) {
            offers.add(row.toOffer());
        }
        int account = accountText.isBlank() ? 0 : parseIntOrZero(accountText);
        long hireCost = hireCostText.isBlank() ? 0L : Math.max(0L, Money.parseOrInvalid(hireCostText));
        Net.saveShop(new SaveShopPacket(source.id(), shopName, kind, entityType, entityData, account,
                forHire, hireCost, permission, offers));
        minecraft.setScreen(null);
    }

    private int parseIntOrZero(String text) {
        try {
            return Math.max(0, Integer.parseInt(text.trim()));
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    /** Asks the server to delete the shop. Called by the confirmation screen. */
    void confirmDelete() {
        Net.deleteShop(new DeleteShopPacket(source.id()));
        minecraft.setScreen(null);
    }
}
