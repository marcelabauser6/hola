package com.athensmc.fsshopkeepers.client.screen;

import com.athensmc.fsshopkeepers.client.MobVariants;
import com.athensmc.fsshopkeepers.client.layout.EditorGeometry;
import com.athensmc.fsshopkeepers.client.layout.Rect;
import com.athensmc.fsshopkeepers.client.theme.FSGui;
import com.athensmc.fsshopkeepers.client.widget.FSButton;
import com.athensmc.fsshopkeepers.money.Cash;
import com.athensmc.fsshopkeepers.money.Money;
import com.athensmc.fsshopkeepers.net.DeleteShopPacket;
import com.athensmc.fsshopkeepers.net.Net;
import com.athensmc.fsshopkeepers.net.SaveShopPacket;
import com.athensmc.fsshopkeepers.shop.ShopObjectKind;
import com.athensmc.fsshopkeepers.shop.Shopkeeper;
import com.athensmc.fsshopkeepers.shop.TradeOffer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The screen an administrator sets a shop up on.
 *
 * <p>Built to the Fantastic family's pattern: a centred panel no wider than 540 pixels, a title strip, a row of tabs, a
 * rule, one line of help text about whatever is under the cursor, and a footer of actions. The geometry is the family's
 * - the same 8-pixel gutter, 18-pixel controls, 22-pixel row pitch and 62/28 body inset - so this screen sits beside
 * the other Fantastic editors rather than looking like a different mod.</p>
 *
 * <p>The trades tab is two columns: the shop's rows on the left, and the selected row's details on the right. Cramming
 * an item, a name, a quantity stepper, a price field, two payment slots and a delete button into one 500-pixel row is
 * what made the first attempt unreadable; splitting it means every control has room for its label.</p>
 *
 * <p>Text is edited with real {@link EditBox} widgets rather than hand-rolled fields. A widget brings a caret,
 * selection, clipboard and click-to-position for free, all of which a hand-rolled field silently lacks.</p>
 */
public final class ShopEditorScreen extends Screen {

    /**
     * The family's gutters and control sizes, re-exported from the shared geometry.
     *
     * <p>Aliases rather than copies, so the numbers the screen draws with are provably the numbers the overlap test
     * checks. A second set of constants here is how a screen and its test come to disagree.</p>
     */
    private static final int GUTTER = EditorGeometry.GUTTER;
    private static final int TITLE_HEIGHT = EditorGeometry.TITLE_HEIGHT;
    private static final int TAB_HEIGHT = EditorGeometry.TAB_HEIGHT;
    private static final int CONTROL_HEIGHT = EditorGeometry.CONTROL_HEIGHT;
    private static final int ROW_PITCH = EditorGeometry.ROW_PITCH;

    private enum Tab {
        TRADES("Tratos", "Que vende la tienda y a que precio."),
        APPEARANCE("Aspecto", "El cuerpo y el nombre del tendero."),
        SETTINGS("Ajustes", "Cuenta bancaria, traspaso y permisos.");

        final String label;
        final String help;

        Tab(String label, String help) {
            this.label = label;
            this.help = help;
        }
    }

    /** One editable trade row. The price stays as typed text until the shop is saved. */
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
            return new Row(offer.result().copy(), offer.hasCashPrice() ? offer.priceText() : "",
                    offer.cost1().copy(), offer.cost2().copy());
        }

        boolean priceValid() {
            return priceText.isBlank() || Money.parseOrInvalid(priceText) >= 0L;
        }

        long priceCents() {
            return priceText.isBlank() ? 0L : Math.max(0L, Money.parseOrInvalid(priceText));
        }

        TradeOffer toOffer() {
            return new TradeOffer(result, priceCents(), cost1, cost2);
        }

        String describe() {
            if (result.isEmpty()) {
                return "\u00a78(sin articulo)";
            }
            return "\u00a7f" + result.getHoverName().getString()
                    + (result.getCount() > 1 ? " \u00a77x" + result.getCount() : "");
        }
    }

    /** A label drawn after the widgets, with an optional tooltip zone. */
    private record Label(String text, int x, int y, int colour) {
    }

    private record TooltipZone(int x, int y, int width, int height, List<Component> lines) {
    }

    private final Shopkeeper.EditorView source;

    private final List<Row> rows = new ArrayList<>();
    private String shopName;
    private ShopObjectKind kind;
    private ResourceLocation entityType;
    private CompoundTag entityData;
    private int linkedAccount;
    private boolean forHire;
    private String hireCostText;
    private String permission;

    private Tab activeTab = Tab.TRADES;
    private int selectedRow;
    private int tradeScroll;
    private int mobScroll;
    private String mobSearch = "";

    private final List<Label> labels = new ArrayList<>();
    private final List<TooltipZone> tooltipZones = new ArrayList<>();
    private String helpLine = "";

    private EditorGeometry geo;
    private int leftPos;
    private int topPos;
    private int panelWidth;
    private int panelHeight;

    private EditBox priceBox;
    private EditBox nameBox;
    private EditBox mobSearchBox;
    private EditBox accountBox;
    private EditBox hireBox;
    private EditBox permissionBox;

    public ShopEditorScreen(Shopkeeper.EditorView view) {
        super(Component.literal("Editor de tienda"));
        this.source = view;
        this.shopName = view.name();
        this.kind = view.objectKind();
        this.entityType = view.entityType();
        this.entityData = view.entityData().copy();
        this.linkedAccount = view.linkedAccount();
        this.forHire = view.forHire();
        this.hireCostText = view.hireCost() > 0L ? Money.plain(view.hireCost()) : "";
        this.permission = view.tradePermission();
        for (TradeOffer offer : view.offers()) {
            rows.add(Row.from(offer));
        }
        if (rows.isEmpty()) {
            rows.add(new Row(ItemStack.EMPTY, "", ItemStack.EMPTY, ItemStack.EMPTY));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- geometry

    private int bodyX() {
        return geo.body().x();
    }

    private int bodyY() {
        return geo.body().y();
    }

    private int bodyW() {
        return geo.body().width();
    }

    private int bodyH() {
        return geo.body().height();
    }

    private int separatorY() {
        return geo.separatorY();
    }

    @Override
    protected void init() {
        geo = new EditorGeometry(width, height);
        panelWidth = geo.panelWidth();
        panelHeight = geo.panelHeight();
        leftPos = geo.leftPos();
        topPos = geo.topPos();
        labels.clear();
        tooltipZones.clear();
        priceBox = null;
        nameBox = null;
        mobSearchBox = null;
        accountBox = null;
        hireBox = null;
        permissionBox = null;

        initTabs();
        initFooter();
        switch (activeTab) {
            case TRADES -> initTrades();
            case APPEARANCE -> initAppearance();
            case SETTINGS -> initSettings();
        }
    }

    private void addLabel(String text, int x, int y, int colour) {
        labels.add(new Label(text, x, y, colour));
    }

    private void addTooltip(int x, int y, int w, int h, String... lines) {
        List<Component> components = new ArrayList<>(lines.length);
        for (String line : lines) {
            components.add(Component.literal(line));
        }
        tooltipZones.add(new TooltipZone(x, y, w, h, components));
    }

    private void initTabs() {
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            boolean current = tab == activeTab;
            Rect slot = geo.tab(i, tabs.length);
            int x = slot.x();
            int w = slot.width();
            addRenderableWidget(new FSButton(x, slot.y(), w, TAB_HEIGHT,
                    Component.literal((current ? "\u00a7f\u00a7l" : "\u00a77") + tab.label),
                    current ? FSGui.ACCENT_BLUE : FSGui.PANEL_BORDER, () -> {
                        if (activeTab != tab) {
                            activeTab = tab;
                            tradeScroll = 0;
                            mobScroll = 0;
                            rebuildWidgets();
                        }
                    }));
        }
    }

    private void initFooter() {
        Rect save = geo.saveButton();
        addRenderableWidget(new FSButton(save.x(), save.y(), save.width(), save.height(),
                Component.literal("\u00a7aGuardar cambios"), FSGui.ACCENT_GREEN, this::save));
        Rect close = geo.closeButton();
        addRenderableWidget(new FSButton(close.x(), close.y(), close.width(), close.height(),
                Component.literal("Cerrar"), FSGui.PANEL_BORDER, this::onClose));
        Rect delete = geo.deleteButton();
        addRenderableWidget(new FSButton(delete.x(), delete.y(), delete.width(), delete.height(),
                Component.literal("\u00a7cBorrar"), FSGui.ACCENT_RED,
                () -> minecraft.setScreen(new ConfirmDeleteScreen(this, source.id()))));
    }

    // ---------------------------------------------------------------- trades tab

    private int tradeListWidth() {
        return geo.tradeListWidth();
    }

    private int tradeVisibleRows() {
        return geo.tradeVisibleRows();
    }

    private void initTrades() {
        helpLine = "Elige una fila a la izquierda y ponle articulo y precio a la derecha.";
        int x = bodyX();
        int y = bodyY();
        int listW = tradeListWidth();

        selectedRow = Math.max(0, Math.min(selectedRow, rows.size() - 1));
        int visible = tradeVisibleRows();
        int maxScroll = Math.max(0, rows.size() - visible);
        tradeScroll = Math.max(0, Math.min(tradeScroll, maxScroll));

        for (int slot = 0; slot < visible; slot++) {
            int index = tradeScroll + slot;
            if (index >= rows.size()) {
                break;
            }
            Row row = rows.get(index);
            boolean current = index == selectedRow;
            String price = row.priceText.isBlank() ? "\u00a78-" : "\u00a7a" + row.priceText;
            String label = (current ? "\u00a7e\u25b6 " : "  ") + row.describe() + "  " + price;
            int rowIndex = index;
            // Room is left to the left of the row for the item icon, drawn after the widgets.
            Rect rowRect = geo.tradeRow(slot);
            addRenderableWidget(new FSButton(rowRect.x(), rowRect.y(), rowRect.width(), rowRect.height(),
                    Component.literal(label), current ? FSGui.ACCENT_BLUE : FSGui.PANEL_BORDER, () -> {
                        selectedRow = rowIndex;
                        rebuildWidgets();
                    }).leftAligned());
        }

        Rect addRect = geo.addTradeButton();
        addRenderableWidget(new FSButton(addRect.x(), addRect.y(), addRect.width(), addRect.height(),
                Component.literal("\u00a7a+ Anadir"), FSGui.ACCENT_GREEN, () -> {
                    if (rows.size() < 45) {
                        rows.add(new Row(ItemStack.EMPTY, "", ItemStack.EMPTY, ItemStack.EMPTY));
                        selectedRow = rows.size() - 1;
                        tradeScroll = Math.max(0, rows.size() - tradeVisibleRows());
                        rebuildWidgets();
                    }
                }));
        Rect removeRect = geo.removeTradeButton();
        addRenderableWidget(new FSButton(removeRect.x(), removeRect.y(), removeRect.width(),
                removeRect.height(), Component.literal("\u00a7cQuitar"), FSGui.ACCENT_RED, () -> {
                    if (!rows.isEmpty()) {
                        rows.remove(Math.max(0, Math.min(selectedRow, rows.size() - 1)));
                        if (rows.isEmpty()) {
                            rows.add(new Row(ItemStack.EMPTY, "", ItemStack.EMPTY, ItemStack.EMPTY));
                        }
                        selectedRow = Math.max(0, selectedRow - 1);
                        rebuildWidgets();
                    }
                }));

        initTradeDetail(x + listW + GUTTER, y, bodyW() - listW - GUTTER);
    }

    /** The right-hand column: everything about the row currently selected. */
    private void initTradeDetail(int x, int y, int w) {
        if (rows.isEmpty()) {
            return;
        }
        Row row = rows.get(Math.max(0, Math.min(selectedRow, rows.size() - 1)));
        int line = y;

        addLabel("\u00a77Articulo que vende", x, line, FSGui.TEXT_DIM);
        line += 10;
        addRenderableWidget(new FSButton(x + 20, line, w - 20, CONTROL_HEIGHT,
                Component.literal(row.result.isEmpty() ? "\u00a78Elegir articulo..."
                        : "\u00a7f" + row.result.getHoverName().getString()),
                FSGui.ACCENT_BLUE, () -> minecraft.setScreen(new ItemPickerScreen(this, chosen -> {
                    row.result = chosen;
                    rebuildWidgets();
                }))));
        addTooltip(x + 20, line, w - 20, CONTROL_HEIGHT,
                "\u00a7fArticulo que el cliente recibe.",
                "\u00a77Puedes elegirlo de todo el juego o de tu inventario.");
        line += ROW_PITCH;

        addLabel("\u00a77Cantidad por compra", x, line + 5, FSGui.TEXT_DIM);
        int stepX = x + w - 62;
        addRenderableWidget(new FSButton(stepX, line, 18, CONTROL_HEIGHT, Component.literal("-"),
                FSGui.PANEL_BORDER, () -> {
                    if (!row.result.isEmpty()) {
                        row.result.setCount(Math.max(1, row.result.getCount() - 1));
                        rebuildWidgets();
                    }
                }));
        addLabel("\u00a7f" + (row.result.isEmpty() ? "-" : String.valueOf(row.result.getCount())),
                stepX + 26, line + 5, FSGui.TEXT);
        addRenderableWidget(new FSButton(stepX + 44, line, 18, CONTROL_HEIGHT, Component.literal("+"),
                FSGui.PANEL_BORDER, () -> {
                    if (!row.result.isEmpty()) {
                        row.result.setCount(Math.min(row.result.getMaxStackSize(), row.result.getCount() + 1));
                        rebuildWidgets();
                    }
                }));
        line += ROW_PITCH;

        addLabel("\u00a77Precio en " + Cash.currencyName(), x, line, FSGui.TEXT_DIM);
        line += 10;
        priceBox = new EditBox(font, x + 1, line + 1, w - 2, CONTROL_HEIGHT - 2,
                Component.literal("Precio"));
        priceBox.setMaxLength(16);
        priceBox.setValue(row.priceText);
        priceBox.setHint(Component.literal("0.00"));
        // Filtered as typed, so a price field can never hold a letter and the row can never be unsaveable.
        priceBox.setFilter(text -> text.isEmpty() || text.matches("[0-9]{0,10}([.,][0-9]{0,2})?"));
        priceBox.setResponder(text -> row.priceText = text);
        addRenderableWidget(priceBox);
        addTooltip(x, line, w, CONTROL_HEIGHT,
                "\u00a7fLo que paga el cliente.",
                "\u00a77Admite coma o punto y dos decimales.",
                "\u00a78Dejalo vacio si solo cobras con articulos.");
        line += ROW_PITCH;

        addLabel("\u00a77Pago con articulos \u00a78(opcional)", x, line, FSGui.TEXT_DIM);
        line += 10;
        int halfW = (w - 4) / 2;
        addRenderableWidget(new FSButton(x + 20, line, halfW - 20, CONTROL_HEIGHT,
                Component.literal(row.cost1.isEmpty() ? "\u00a78vacio"
                        : "\u00a7f" + row.cost1.getHoverName().getString()),
                FSGui.PANEL_BORDER, () -> minecraft.setScreen(new ItemPickerScreen(this, chosen -> {
                    row.cost1 = chosen;
                    rebuildWidgets();
                }))));
        addRenderableWidget(new FSButton(x + halfW + 4 + 20, line, halfW - 20 - 4, CONTROL_HEIGHT,
                Component.literal(row.cost2.isEmpty() ? "\u00a78vacio"
                        : "\u00a7f" + row.cost2.getHoverName().getString()),
                FSGui.PANEL_BORDER, () -> minecraft.setScreen(new ItemPickerScreen(this, chosen -> {
                    row.cost2 = chosen;
                    rebuildWidgets();
                }))));
        addTooltip(x, line, w, CONTROL_HEIGHT,
                "\u00a7fArticulos que el cliente entrega ademas del precio.",
                "\u00a77Clic derecho en el boton para vaciarlo.");
        line += ROW_PITCH;

        String reason = row.toOffer().incompleteReason();
        addLabel(reason == null ? "\u00a7aEsta fila esta lista." : "\u00a7e" + reason, x, line + 4,
                FSGui.TEXT_DIM);
    }

    // ---------------------------------------------------------------- appearance tab

    private List<ResourceLocation> mobChoices() {
        List<ResourceLocation> out = new ArrayList<>();
        String needle = mobSearch.toLowerCase();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            boolean usable = type.getCategory() != MobCategory.MISC || type == EntityType.ARMOR_STAND;
            if (!usable) {
                continue;
            }
            ResourceLocation id = EntityType.getKey(type);
            String label = type.getDescription().getString().toLowerCase();
            if (needle.isEmpty() || label.contains(needle) || id.getPath().contains(needle)) {
                out.add(id);
            }
        }
        return out;
    }

    private void initAppearance() {
        helpLine = "Cuerpo del tendero. Busca el mob por nombre y ajusta sus variantes.";
        int x = bodyX();
        int y = bodyY();
        int w = bodyW();

        addLabel("\u00a77Nombre de la tienda", x, y, FSGui.TEXT_DIM);
        nameBox = new EditBox(font, x + 1, y + 11, w - 2, CONTROL_HEIGHT - 2, Component.literal("Nombre"));
        nameBox.setMaxLength(SaveShopPacket.MAX_NAME);
        nameBox.setValue(shopName);
        nameBox.setHint(Component.literal("sin nombre"));
        nameBox.setResponder(text -> shopName = text);
        addRenderableWidget(nameBox);
        y += 32;

        ShopObjectKind[] kinds = ShopObjectKind.values();
        int each = (w - 2 * (kinds.length - 1)) / kinds.length;
        for (int i = 0; i < kinds.length; i++) {
            ShopObjectKind option = kinds[i];
            boolean current = option == kind;
            int bx = x + i * (each + 2);
            addRenderableWidget(new FSButton(bx, y, i == kinds.length - 1 ? x + w - bx : each, CONTROL_HEIGHT,
                    Component.literal((current ? "\u00a7f\u00a7l" : "\u00a77") + option.title()),
                    current ? FSGui.ACCENT_BLUE : FSGui.PANEL_BORDER, () -> {
                        kind = option;
                        rebuildWidgets();
                    }));
            addTooltip(bx, y, each, CONTROL_HEIGHT, "\u00a7f" + option.title(), "\u00a77" + option.description());
        }
        y += ROW_PITCH;

        if (kind != ShopObjectKind.LIVING) {
            addLabel(kind == ShopObjectKind.SIGN
                    ? "\u00a77Una tienda de cartel no tiene mob que configurar."
                    : "\u00a77Una tienda virtual no tiene cuerpo. Se abre por comando.", x, y + 4,
                    FSGui.TEXT_DIM);
            return;
        }

        // Variants sit on the right, the searchable mob list on the left.
        int listW = Math.max(130, w * 52 / 100);
        mobSearchBox = new EditBox(font, x + 1, y + 1, listW - 2, CONTROL_HEIGHT - 2,
                Component.literal("Buscar"));
        mobSearchBox.setValue(mobSearch);
        mobSearchBox.setHint(Component.literal("buscar mob..."));
        mobSearchBox.setResponder(text -> {
            if (!text.equals(mobSearch)) {
                mobSearch = text;
                mobScroll = 0;
                rebuildWidgets();
            }
        });
        addRenderableWidget(mobSearchBox);

        List<ResourceLocation> choices = mobChoices();
        int listTop = y + ROW_PITCH;
        int listBottom = bodyY() + bodyH();
        int visible = Math.max(1, (listBottom - listTop) / ROW_PITCH);
        int maxScroll = Math.max(0, choices.size() - visible);
        mobScroll = Math.max(0, Math.min(mobScroll, maxScroll));
        for (int slot = 0; slot < visible; slot++) {
            int index = mobScroll + slot;
            if (index >= choices.size()) {
                break;
            }
            ResourceLocation id = choices.get(index);
            boolean current = id.equals(entityType);
            String label = EntityType.byString(id.toString())
                    .map(type -> type.getDescription().getString()).orElse(id.getPath());
            addRenderableWidget(new FSButton(x, listTop + slot * ROW_PITCH, listW - 8, CONTROL_HEIGHT,
                    Component.literal((current ? "\u00a7a\u2714 " : "\u00a7f") + label),
                    current ? FSGui.ACCENT_GREEN : FSGui.PANEL_BORDER, () -> {
                        if (!id.equals(entityType)) {
                            entityType = id;
                            // Variant data belonged to the old species and means nothing on the new one.
                            entityData = new CompoundTag();
                            rebuildWidgets();
                        }
                    }).leftAligned());
        }

        int rightX = x + listW + GUTTER;
        int rightW = w - listW - GUTTER;
        int line = y;
        addLabel("\u00a77Variantes", rightX, line, FSGui.TEXT_DIM);
        line += 12;
        List<MobVariants.Option> options = MobVariants.optionsFor(entityType);
        if (options.isEmpty()) {
            addLabel("\u00a78Este mob no tiene variantes.", rightX, line + 4, FSGui.TEXT_DIM);
            return;
        }
        for (MobVariants.Option option : options) {
            if (line + CONTROL_HEIGHT > bodyY() + bodyH()) {
                break;
            }
            addRenderableWidget(new FSButton(rightX, line, rightW, CONTROL_HEIGHT,
                    Component.literal("\u00a77" + option.label() + ": \u00a7f"
                            + option.currentLabel(entityData)),
                    FSGui.PANEL_BORDER, () -> {
                        option.cycle(entityData);
                        rebuildWidgets();
                    }));
            line += ROW_PITCH;
        }
    }

    // ---------------------------------------------------------------- settings tab

    private void initSettings() {
        helpLine = "Ajustes de la tienda. Los permisos solo los ve el staff.";
        int x = bodyX();
        int y = bodyY();
        int w = bodyW();

        addLabel("\u00a77Cuenta bancaria donde cobrar", x, y, FSGui.TEXT_DIM);
        accountBox = new EditBox(font, x + 1, y + 11, w - 2, CONTROL_HEIGHT - 2, Component.literal("Cuenta"));
        accountBox.setMaxLength(10);
        accountBox.setValue(linkedAccount > 0 ? String.valueOf(linkedAccount) : "");
        accountBox.setHint(Component.literal("tu cuenta por defecto"));
        accountBox.setFilter(text -> text.matches("[0-9]{0,10}"));
        accountBox.setResponder(text -> {
            try {
                linkedAccount = text.isBlank() ? 0 : Math.max(0, Integer.parseInt(text));
            } catch (NumberFormatException tooBig) {
                linkedAccount = 0;
            }
        });
        addRenderableWidget(accountBox);
        addTooltip(x, y + 11, w, CONTROL_HEIGHT, "\u00a7fNumero de cuenta donde entran las ventas.",
                "\u00a77Vacio usa la que tengas por defecto.");
        y += 32;

        addRenderableWidget(new FSButton(x, y, w, CONTROL_HEIGHT,
                Component.literal(forHire ? "\u00a7aEn traspaso: se vende" : "\u00a77En traspaso: no"),
                forHire ? FSGui.ACCENT_GREEN : FSGui.PANEL_BORDER, () -> {
                    forHire = !forHire;
                    rebuildWidgets();
                }));
        addTooltip(x, y, w, CONTROL_HEIGHT, "\u00a7fSi esta activo, otro jugador puede comprar la tienda.");
        y += ROW_PITCH;

        addLabel(forHire ? "\u00a77Precio de traspaso" : "\u00a78Precio de traspaso", x, y, FSGui.TEXT_DIM);
        hireBox = new EditBox(font, x + 1, y + 11, w - 2, CONTROL_HEIGHT - 2, Component.literal("Traspaso"));
        hireBox.setMaxLength(16);
        hireBox.setValue(hireCostText);
        hireBox.setHint(Component.literal(forHire ? "0.00" : "activa el traspaso primero"));
        hireBox.setEditable(forHire);
        hireBox.setFilter(text -> text.isEmpty() || text.matches("[0-9]{0,10}([.,][0-9]{0,2})?"));
        hireBox.setResponder(text -> hireCostText = text);
        addRenderableWidget(hireBox);
        y += 32;

        addLabel("\u00a77Permiso para comerciar \u00a78(solo staff)", x, y, FSGui.TEXT_DIM);
        permissionBox = new EditBox(font, x + 1, y + 11, w - 2, CONTROL_HEIGHT - 2,
                Component.literal("Permiso"));
        permissionBox.setMaxLength(128);
        permissionBox.setValue(permission);
        permissionBox.setHint(Component.literal("cualquiera puede comprar"));
        permissionBox.setResponder(text -> permission = text);
        addRenderableWidget(permissionBox);
        y += 32;

        addLabel(Cash.available()
                ? "\u00a7aFantastic Currency detectado: se cobra en " + Cash.currencyName() + "."
                : "\u00a7cFantastic Currency no esta instalado: solo pagos con articulos.",
                x, y, FSGui.TEXT_DIM);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(leftPos, topPos, leftPos + panelWidth, topPos + panelHeight, FSGui.WINDOW_BG);
        graphics.fill(leftPos, topPos, leftPos + panelWidth, topPos + TITLE_HEIGHT, FSGui.TITLE_BAR);
        graphics.fill(leftPos, topPos + panelHeight - 1, leftPos + panelWidth, topPos + panelHeight,
                FSGui.RULE);
        int separatorY = separatorY();
        graphics.fill(leftPos + 6, separatorY, leftPos + panelWidth - 6, separatorY + 1, FSGui.RULE);

        String owner = source.ownerName().isBlank() ? "" : " \u00a77- \u00a7f" + source.ownerName();
        graphics.drawString(font, "\u00a7d\u2726 \u00a7fFantastic Shopkeepers \u00a7d\u2726 \u00a77- "
                + source.type().title() + owner, leftPos + GUTTER, topPos + 6, 0xFFFFFF, false);

        if (!helpLine.isEmpty()) {
            String trimmed = font.plainSubstrByWidth("\u00a77" + helpLine, panelWidth - GUTTER * 2);
            graphics.drawString(font, trimmed, leftPos + GUTTER, separatorY + 4, FSGui.HELP_TEXT, false);
        }

        if (activeTab == Tab.TRADES) {
            drawTradeIcons(graphics);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        for (Label label : labels) {
            graphics.drawString(font, label.text(), label.x(), label.y(), label.colour(), false);
        }
        drawScrollbars(graphics, mouseX, mouseY);

        for (TooltipZone zone : tooltipZones) {
            if (mouseX >= zone.x() && mouseX < zone.x() + zone.width()
                    && mouseY >= zone.y() && mouseY < zone.y() + zone.height()) {
                graphics.renderComponentTooltip(font, zone.lines(), mouseX, mouseY);
                break;
            }
        }
    }

    /**
     * The item icons beside the trade rows, and the one in the detail column.
     *
     * <p>Drawn by the screen rather than by the row buttons: a button renders its own label and nothing else, and the
     * space for the icon was reserved to the left of each button precisely so this pass has somewhere to put it.</p>
     */
    private void drawTradeIcons(GuiGraphics graphics) {
        int x = bodyX();
        int y = bodyY();
        int visible = tradeVisibleRows();
        for (int slot = 0; slot < visible; slot++) {
            int index = tradeScroll + slot;
            if (index >= rows.size()) {
                break;
            }
            Rect icon = geo.tradeRowIcon(slot);
            ItemStack stack = rows.get(index).result;
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, icon.x(), icon.y());
                graphics.renderItemDecorations(font, stack, icon.x(), icon.y());
            } else {
                FSGui.inset(graphics, icon.x(), icon.y(), icon.width(), icon.height());
            }
        }

        if (rows.isEmpty()) {
            return;
        }
        Row selected = rows.get(Math.max(0, Math.min(selectedRow, rows.size() - 1)));
        int detailX = bodyX() + tradeListWidth() + GUTTER;
        drawSlotIcon(graphics, detailX, bodyY() + 10, selected.result);
        int payLine = bodyY() + 10 + ROW_PITCH * 3 + 10;
        int halfW = (bodyW() - tradeListWidth() - GUTTER - 4) / 2;
        drawSlotIcon(graphics, detailX, payLine, selected.cost1);
        drawSlotIcon(graphics, detailX + halfW + 4, payLine, selected.cost2);
    }

    private void drawSlotIcon(GuiGraphics graphics, int x, int y, ItemStack stack) {
        if (stack.isEmpty()) {
            FSGui.inset(graphics, x, y, 18, 18);
            return;
        }
        graphics.renderItem(stack, x + 1, y + 1);
        graphics.renderItemDecorations(font, stack, x + 1, y + 1);
    }

    private void drawScrollbars(GuiGraphics graphics, int mouseX, int mouseY) {
        if (activeTab == Tab.TRADES) {
            int visible = tradeVisibleRows();
            if (rows.size() > visible) {
                int trackX = bodyX() + tradeListWidth() - 6;
                int trackTop = bodyY();
                int trackHeight = visible * ROW_PITCH;
                int maxScroll = rows.size() - visible;
                int thumbHeight = FSGui.thumbHeight(trackHeight, visible, rows.size());
                boolean hot = mouseX >= trackX && mouseX < trackX + 5
                        && mouseY >= trackTop && mouseY < trackTop + trackHeight;
                FSGui.scrollbar(graphics, trackX, trackTop, 5, trackHeight,
                        FSGui.thumbTop(trackTop, trackHeight, thumbHeight, tradeScroll, maxScroll),
                        thumbHeight, hot);
            }
            return;
        }
        if (activeTab == Tab.APPEARANCE && kind == ShopObjectKind.LIVING) {
            List<ResourceLocation> choices = mobChoices();
            int listW = Math.max(130, bodyW() * 52 / 100);
            int listTop = bodyY() + 32 + ROW_PITCH * 2;
            int listBottom = bodyY() + bodyH();
            int visible = Math.max(1, (listBottom - listTop) / ROW_PITCH);
            if (choices.size() > visible) {
                int trackX = bodyX() + listW - 6;
                int trackHeight = visible * ROW_PITCH;
                int maxScroll = choices.size() - visible;
                int thumbHeight = FSGui.thumbHeight(trackHeight, visible, choices.size());
                boolean hot = mouseX >= trackX && mouseX < trackX + 5
                        && mouseY >= listTop && mouseY < listTop + trackHeight;
                FSGui.scrollbar(graphics, trackX, listTop, 5, trackHeight,
                        FSGui.thumbTop(listTop, trackHeight, thumbHeight, mobScroll, maxScroll),
                        thumbHeight, hot);
            }
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int step = (int) -Math.signum(delta);
        if (activeTab == Tab.TRADES) {
            int maxScroll = Math.max(0, rows.size() - tradeVisibleRows());
            int next = Math.max(0, Math.min(tradeScroll + step, maxScroll));
            if (next != tradeScroll) {
                tradeScroll = next;
                rebuildWidgets();
            }
            return true;
        }
        if (activeTab == Tab.APPEARANCE && kind == ShopObjectKind.LIVING) {
            int next = Math.max(0, mobScroll + step);
            if (next != mobScroll) {
                mobScroll = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /**
     * Right-clicking a payment button clears it.
     *
     * <p>Handled before the widgets get the click, because a button only reports being pressed and cannot tell the
     * screen which mouse button did it.</p>
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && activeTab == Tab.TRADES && !rows.isEmpty()) {
            Row row = rows.get(Math.max(0, Math.min(selectedRow, rows.size() - 1)));
            int detailX = bodyX() + tradeListWidth() + GUTTER;
            int detailW = bodyW() - tradeListWidth() - GUTTER;
            int payLine = bodyY() + 10 + ROW_PITCH * 3 + 10;
            int halfW = (detailW - 4) / 2;
            if (mouseY >= payLine && mouseY < payLine + CONTROL_HEIGHT) {
                if (mouseX >= detailX && mouseX < detailX + halfW) {
                    row.cost1 = ItemStack.EMPTY;
                    rebuildWidgets();
                    return true;
                }
                if (mouseX >= detailX + halfW + 4 && mouseX < detailX + detailW) {
                    row.cost2 = ItemStack.EMPTY;
                    rebuildWidgets();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ---------------------------------------------------------------- saving

    private void save() {
        List<TradeOffer> offers = new ArrayList<>(rows.size());
        for (Row row : rows) {
            offers.add(row.toOffer());
        }
        long hireCost = hireCostText.isBlank() ? 0L : Math.max(0L, Money.parseOrInvalid(hireCostText));
        Net.saveShop(new SaveShopPacket(source.id(), shopName, kind, entityType, entityData, linkedAccount,
                forHire, hireCost, permission, offers));
        minecraft.setScreen(null);
    }

    /** Asks the server to delete the shop. Called by the confirmation screen. */
    void confirmDelete() {
        Net.deleteShop(new DeleteShopPacket(source.id()));
        minecraft.setScreen(null);
    }
}
