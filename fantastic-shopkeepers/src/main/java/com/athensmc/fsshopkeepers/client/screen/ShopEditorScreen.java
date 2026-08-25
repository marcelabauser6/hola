package com.athensmc.fsshopkeepers.client.screen;

import com.athensmc.fsshopkeepers.client.MobVariants;
import com.athensmc.fsshopkeepers.client.layout.EditorGeometry;
import com.athensmc.fsshopkeepers.client.layout.Rect;
import com.athensmc.fsshopkeepers.client.theme.FSGui;
import com.athensmc.fsshopkeepers.money.Cash;
import com.athensmc.fsshopkeepers.money.Money;
import com.athensmc.fsshopkeepers.net.DeleteShopPacket;
import com.athensmc.fsshopkeepers.net.Net;
import com.athensmc.fsshopkeepers.net.SaveShopPacket;
import com.athensmc.fsshopkeepers.shop.ShopObjectKind;
import com.athensmc.fsshopkeepers.shop.Shopkeeper;
import com.athensmc.fsshopkeepers.shop.TradeOffer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
 * <p>Built to match the Crates editor, because that is the Fantastic family's editor and this should be the same tool
 * with different fields. Same centred panel capped at 540x320, same title strip, same row of tabs, same rule with a line
 * of help under it, same footer. The controls are plain vanilla {@link Button}s and {@link EditBox}es with the active tab
 * marked by bold white text and the inactive ones grey, exactly as Crates does it - not custom-drawn buttons, which was
 * the mistake that made the first attempt look like a different mod.</p>
 *
 * <p>Every position, for widgets and for the item icons alike, comes from {@link EditorGeometry}. The first version
 * placed buttons with one formula and icons with another, and they drifted apart: that is what put a coin on top of a
 * button and a tooltip across a price field.</p>
 */
public final class ShopEditorScreen extends Screen {

    private static final int GUTTER = EditorGeometry.GUTTER;
    private static final int TITLE_HEIGHT = EditorGeometry.TITLE_HEIGHT;
    private static final int ROW_PITCH = EditorGeometry.ROW_PITCH;

    /** Which detail block holds what, in the trades tab. */
    private static final int BLOCK_ITEM = 0;
    private static final int BLOCK_AMOUNT = 1;
    private static final int BLOCK_PRICE = 2;
    private static final int BLOCK_PAY_1 = 3;
    private static final int BLOCK_PAY_2 = 4;
    private static final int BLOCK_COUNT = 5;

    private enum Tab {
        TRADES("Tratos", "Elige una fila a la izquierda y ponle articulo, cantidad y precio a la derecha."),
        APPEARANCE("Aspecto", "El cuerpo y el nombre del tendero. Busca el mob y ajusta sus variantes."),
        SETTINGS("Ajustes", "Cuenta bancaria donde cobrar, traspaso y permisos.");

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

    /**
     * A label to draw after the widgets.
     *
     * <p>{@code centreWidth} greater than zero centres the text in a box that wide starting at {@code x}, which is what
     * the stepper's number needs; everything else is drawn from its left edge.</p>
     */
    private record Label(String text, int x, int y, int centreWidth) {
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
    private String helpLine = "";

    private EditorGeometry geo;

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

    @Override
    protected void init() {
        geo = new EditorGeometry(width, height);
        labels.clear();
        helpLine = activeTab.help;

        initTabs();
        initFooter();
        switch (activeTab) {
            case TRADES -> initTrades();
            case APPEARANCE -> initAppearance();
            case SETTINGS -> initSettings();
        }
    }

    private void addLabel(String text, Rect where) {
        if (!where.isEmpty()) {
            labels.add(new Label(text, where.x(), where.y(), 0));
        }
    }

    /** A label centred in its rectangle, and vertically centred if the rectangle is a control. */
    private void addCentredLabel(String text, Rect where) {
        if (!where.isEmpty()) {
            int y = where.y() + (where.height() - font.lineHeight) / 2 + 1;
            labels.add(new Label(text, where.x(), y, where.width()));
        }
    }

    /** A plain vanilla button, which is what the Crates editor uses for everything. */
    private void button(Rect where, String text, Runnable action) {
        if (where.isEmpty()) {
            return;
        }
        addRenderableWidget(Button.builder(Component.literal(text), pressed -> action.run())
                .bounds(where.x(), where.y(), where.width(), where.height()).build());
    }

    private EditBox field(Rect where, String value, String hint, int maxLength) {
        EditBox box = new EditBox(font, where.x() + 1, where.y() + 1, where.width() - 2,
                where.height() - 2, Component.literal(hint));
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setHint(Component.literal(hint));
        addRenderableWidget(box);
        return box;
    }

    private void initTabs() {
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            boolean current = tab == activeTab;
            button(geo.tab(i, tabs.length), (current ? "\u00a7f\u00a7l" : "\u00a77") + tab.label, () -> {
                if (activeTab != tab) {
                    activeTab = tab;
                    tradeScroll = 0;
                    mobScroll = 0;
                    rebuildWidgets();
                }
            });
        }
    }

    private void initFooter() {
        button(geo.closeButton(), "Cerrar", this::onClose);
        button(geo.deleteButton(), "\u00a7cBorrar", () ->
                minecraft.setScreen(new ConfirmDeleteScreen(this, source.id())));
        button(geo.saveButton(), "\u00a7aGuardar cambios", this::save);
    }

    // ------------------------------------------------------------ trades tab

    private void initTrades() {
        selectedRow = Math.max(0, Math.min(selectedRow, rows.size() - 1));
        int visible = geo.tradeVisibleRows();
        tradeScroll = Math.max(0, Math.min(tradeScroll, Math.max(0, rows.size() - visible)));

        for (int slot = 0; slot < visible; slot++) {
            int index = tradeScroll + slot;
            if (index >= rows.size()) {
                break;
            }
            Row row = rows.get(index);
            boolean current = index == selectedRow;
            String price = row.priceText.isBlank() ? "" : "  \u00a7a" + row.priceText;
            button(geo.tradeRow(slot), (current ? "\u00a7e\u25b6 " : "") + row.describe() + price, () -> {
                selectedRow = index;
                rebuildWidgets();
            });
        }

        button(geo.addTradeButton(), "\u00a7a+ Anadir", () -> {
            if (rows.size() < 45) {
                rows.add(new Row(ItemStack.EMPTY, "", ItemStack.EMPTY, ItemStack.EMPTY));
                selectedRow = rows.size() - 1;
                tradeScroll = Math.max(0, rows.size() - geo.tradeVisibleRows());
                rebuildWidgets();
            }
        });
        button(geo.removeTradeButton(), "\u00a7cQuitar", () -> {
            rows.remove(Math.max(0, Math.min(selectedRow, rows.size() - 1)));
            if (rows.isEmpty()) {
                rows.add(new Row(ItemStack.EMPTY, "", ItemStack.EMPTY, ItemStack.EMPTY));
            }
            selectedRow = Math.max(0, selectedRow - 1);
            rebuildWidgets();
        });

        initTradeDetail(rows.get(Math.max(0, Math.min(selectedRow, rows.size() - 1))));
    }

    private void initTradeDetail(Row row) {
        addLabel("\u00a77Articulo que vende", geo.detailLabel(BLOCK_ITEM));
        button(geo.detailControlAfterIcon(BLOCK_ITEM),
                row.result.isEmpty() ? "\u00a78Elegir articulo..." : row.result.getHoverName().getString(),
                () -> pickItem(chosen -> {
                    // A new item keeps the count the admin already chose, so picking again is not a reset.
                    int keep = row.result.isEmpty() ? 1 : row.result.getCount();
                    chosen.setCount(Math.max(1, Math.min(keep, chosen.getMaxStackSize())));
                    row.result = chosen;
                }));

        addLabel("\u00a77Cantidad que entrega", geo.detailLabel(BLOCK_AMOUNT));
        stepper(BLOCK_AMOUNT, row.result, () -> row.result);

        addLabel("\u00a77Precio en " + Cash.currencyName() + " \u00a78(opcional)",
                geo.detailLabel(BLOCK_PRICE));
        Rect priceRect = geo.detailControl(BLOCK_PRICE);
        if (!priceRect.isEmpty()) {
            EditBox price = field(priceRect, row.priceText, "0.00", 16);
            price.setFilter(text -> text.isEmpty() || text.matches("[0-9]{0,10}([.,][0-9]{0,2})?"));
            price.setResponder(text -> row.priceText = text);
        }

        payBlock(BLOCK_PAY_1, "Pago 1", row.cost1, chosen -> row.cost1 = chosen,
                () -> row.cost1, cleared -> row.cost1 = cleared);
        payBlock(BLOCK_PAY_2, "Pago 2", row.cost2, chosen -> row.cost2 = chosen,
                () -> row.cost2, cleared -> row.cost2 = cleared);

        String reason = row.toOffer().incompleteReason();
        addLabel(reason == null ? "\u00a7aEsta fila esta lista." : "\u00a7e" + reason,
                geo.detailStatus(BLOCK_COUNT));
    }

    /**
     * A payment block: the item, a button to choose it, and a stepper for how many.
     *
     * <p>The stepper is the thing that was missing. A shop that barters wants "five silver coins for a log", and without
     * a quantity control the only expressible price was one of something.</p>
     */
    private void payBlock(int block, String title, ItemStack current,
            java.util.function.Consumer<ItemStack> setter,
            java.util.function.Supplier<ItemStack> getter,
            java.util.function.Consumer<ItemStack> clearer) {
        addLabel("\u00a77" + title + " \u00a78(articulo, opcional)", geo.detailLabel(block));
        Rect buttonRect = geo.detailControlAfterIconBeforeStepper(block);
        button(buttonRect, current.isEmpty() ? "\u00a78vacio" : current.getHoverName().getString(), () -> {
            if (getter.get().isEmpty()) {
                pickItem(chosen -> setter.accept(chosen));
            } else {
                // Clicking a filled slot empties it, which is the obvious way to undo a wrong pick.
                clearer.accept(ItemStack.EMPTY);
                rebuildWidgets();
            }
        });
        stepper(block, current, getter);
    }

    /** A minus/count/plus stepper acting on whatever stack the supplier returns. */
    private void stepper(int block, ItemStack shown, java.util.function.Supplier<ItemStack> target) {
        button(geo.stepperMinus(block), "-", () -> {
            ItemStack stack = target.get();
            if (!stack.isEmpty()) {
                stack.setCount(Math.max(1, stack.getCount() - 1));
                rebuildWidgets();
            }
        });
        addCentredLabel(shown.isEmpty() ? "\u00a78-" : "\u00a7f" + shown.getCount(),
                geo.stepperCount(block));
        button(geo.stepperPlus(block), "+", () -> {
            ItemStack stack = target.get();
            if (!stack.isEmpty()) {
                stack.setCount(Math.min(stack.getMaxStackSize(), stack.getCount() + 1));
                rebuildWidgets();
            }
        });
    }

    private void pickItem(java.util.function.Consumer<ItemStack> onChosen) {
        minecraft.setScreen(new ItemPickerScreen(this, chosen -> {
            onChosen.accept(chosen);
            rebuildWidgets();
        }));
    }

    // ------------------------------------------------------------ appearance tab

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
        addLabel("\u00a77Nombre de la tienda", geo.nameLabel());
        EditBox name = field(geo.nameField(), shopName, "sin nombre", SaveShopPacket.MAX_NAME);
        name.setResponder(text -> shopName = text);

        ShopObjectKind[] kinds = ShopObjectKind.values();
        for (int i = 0; i < kinds.length; i++) {
            ShopObjectKind option = kinds[i];
            boolean current = option == kind;
            button(geo.kindButton(i, kinds.length),
                    (current ? "\u00a7f\u00a7l" : "\u00a77") + option.title(), () -> {
                        kind = option;
                        rebuildWidgets();
                    });
        }

        if (kind != ShopObjectKind.LIVING) {
            addLabel(kind == ShopObjectKind.SIGN
                    ? "\u00a77Una tienda de cartel no tiene mob que configurar."
                    : "\u00a77Una tienda virtual no tiene cuerpo. Se abre por comando.",
                    geo.mobSearchField());
            return;
        }

        EditBox search = field(geo.mobSearchField(), mobSearch, "buscar mob...", 32);
        search.setResponder(text -> {
            if (!text.equals(mobSearch)) {
                mobSearch = text;
                mobScroll = 0;
                rebuildWidgets();
            }
        });

        List<ResourceLocation> choices = mobChoices();
        int visible = geo.mobVisibleRows();
        mobScroll = Math.max(0, Math.min(mobScroll, Math.max(0, choices.size() - visible)));
        for (int slot = 0; slot < visible; slot++) {
            int index = mobScroll + slot;
            if (index >= choices.size()) {
                break;
            }
            ResourceLocation id = choices.get(index);
            boolean current = id.equals(entityType);
            String label = EntityType.byString(id.toString())
                    .map(type -> type.getDescription().getString()).orElse(id.getPath());
            button(geo.mobRow(slot), (current ? "\u00a7a\u2714 " : "\u00a7f") + label, () -> {
                if (!id.equals(entityType)) {
                    entityType = id;
                    // Variant data belonged to the old species and means nothing on the new one.
                    entityData = new CompoundTag();
                    rebuildWidgets();
                }
            });
        }

        addLabel("\u00a77Variantes", geo.variantsHeader());
        List<MobVariants.Option> options = MobVariants.optionsFor(entityType);
        if (options.isEmpty()) {
            addLabel("\u00a78Este mob no tiene variantes.", geo.variantRow(0));
            return;
        }
        int rowsAvailable = geo.variantVisibleRows();
        for (int i = 0; i < options.size() && i < rowsAvailable; i++) {
            MobVariants.Option option = options.get(i);
            button(geo.variantRow(i), "\u00a77" + option.label() + ": \u00a7f"
                    + option.currentLabel(entityData), () -> {
                        option.cycle(entityData);
                        rebuildWidgets();
                    });
        }
    }

    // ------------------------------------------------------------ settings tab

    private void initSettings() {
        addLabel("\u00a77Cuenta bancaria donde cobrar", geo.settingLabel(0));
        EditBox account = field(geo.settingControl(0),
                linkedAccount > 0 ? String.valueOf(linkedAccount) : "", "tu cuenta por defecto", 10);
        account.setFilter(text -> text.matches("[0-9]{0,10}"));
        account.setResponder(text -> {
            try {
                linkedAccount = text.isBlank() ? 0 : Math.max(0, Integer.parseInt(text));
            } catch (NumberFormatException tooBig) {
                linkedAccount = 0;
            }
        });

        addLabel("\u00a77Traspaso de la tienda", geo.settingLabel(1));
        button(geo.settingControl(1), forHire ? "\u00a7aSe vende a otro jugador" : "\u00a77No se vende", () -> {
            forHire = !forHire;
            rebuildWidgets();
        });

        addLabel(forHire ? "\u00a77Precio de traspaso" : "\u00a78Precio de traspaso", geo.settingLabel(2));
        EditBox hire = field(geo.settingControl(2), hireCostText,
                forHire ? "0.00" : "activa el traspaso primero", 16);
        hire.setEditable(forHire);
        hire.setFilter(text -> text.isEmpty() || text.matches("[0-9]{0,10}([.,][0-9]{0,2})?"));
        hire.setResponder(text -> hireCostText = text);

        addLabel("\u00a77Permiso para comerciar \u00a78(solo staff)", geo.settingLabel(3));
        EditBox perms = field(geo.settingControl(3), permission, "cualquiera puede comprar", 128);
        perms.setResponder(text -> permission = text);

        addLabel(Cash.available()
                ? "\u00a7aFantastic Currency detectado: se cobra en " + Cash.currencyName() + "."
                : "\u00a7cFantastic Currency no esta instalado: solo pagos con articulos.",
                geo.settingLabel(4));
    }

    // ------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int leftPos = geo.leftPos();
        int topPos = geo.topPos();
        int panelWidth = geo.panelWidth();
        int panelHeight = geo.panelHeight();

        graphics.fill(leftPos, topPos, leftPos + panelWidth, topPos + panelHeight, FSGui.WINDOW_BG);
        graphics.fill(leftPos, topPos, leftPos + panelWidth, topPos + TITLE_HEIGHT, FSGui.TITLE_BAR);
        graphics.fill(leftPos, topPos + panelHeight - 1, leftPos + panelWidth, topPos + panelHeight,
                FSGui.RULE);
        int separatorY = geo.separatorY();
        graphics.fill(leftPos + 6, separatorY, leftPos + panelWidth - 6, separatorY + 1, FSGui.RULE);

        String owner = source.ownerName().isBlank() ? "" : " \u00a77- \u00a7f" + source.ownerName();
        graphics.drawString(font, "\u00a7d\u2726 \u00a7fFantastic Shopkeepers \u00a7d\u2726 \u00a77- "
                + source.type().title() + owner, leftPos + GUTTER, topPos + 6, 0xFFFFFF, false);

        if (!helpLine.isEmpty()) {
            graphics.drawString(font, font.plainSubstrByWidth("\u00a77" + helpLine, panelWidth - GUTTER * 2),
                    leftPos + GUTTER, separatorY + 4, FSGui.HELP_TEXT, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        // Labels and icons go over the widgets, from the same geometry the widgets came from.
        for (Label label : labels) {
            int x = label.centreWidth() <= 0 ? label.x()
                    : label.x() + (label.centreWidth() - font.width(label.text())) / 2;
            graphics.drawString(font, label.text(), x, label.y(), FSGui.TEXT, false);
        }
        if (activeTab == Tab.TRADES) {
            drawTradeIcons(graphics);
            drawTradeScrollbar(graphics, mouseX, mouseY);
        } else if (activeTab == Tab.APPEARANCE && kind == ShopObjectKind.LIVING) {
            drawMobScrollbar(graphics, mouseX, mouseY);
        }
    }

    private void drawTradeIcons(GuiGraphics graphics) {
        int visible = geo.tradeVisibleRows();
        for (int slot = 0; slot < visible; slot++) {
            int index = tradeScroll + slot;
            if (index >= rows.size()) {
                break;
            }
            drawIcon(graphics, geo.tradeRowIcon(slot), rows.get(index).result);
        }
        if (rows.isEmpty()) {
            return;
        }
        Row selected = rows.get(Math.max(0, Math.min(selectedRow, rows.size() - 1)));
        drawIcon(graphics, geo.detailIcon(BLOCK_ITEM), selected.result);
        drawIcon(graphics, geo.detailIcon(BLOCK_PAY_1), selected.cost1);
        drawIcon(graphics, geo.detailIcon(BLOCK_PAY_2), selected.cost2);
    }

    private void drawIcon(GuiGraphics graphics, Rect where, ItemStack stack) {
        if (where.isEmpty()) {
            return;
        }
        if (stack.isEmpty()) {
            FSGui.inset(graphics, where.x(), where.y(), where.width(), where.height());
            return;
        }
        graphics.renderItem(stack, where.x() + 1, where.y() + 1);
        graphics.renderItemDecorations(font, stack, where.x() + 1, where.y() + 1);
    }

    private void drawTradeScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        int visible = geo.tradeVisibleRows();
        if (rows.size() <= visible) {
            return;
        }
        Rect track = geo.tradeScrollbar();
        int maxScroll = rows.size() - visible;
        int thumb = FSGui.thumbHeight(track.height(), visible, rows.size());
        boolean hot = track.contains(mouseX, mouseY);
        FSGui.scrollbar(graphics, track.x(), track.y(), track.width(), track.height(),
                FSGui.thumbTop(track.y(), track.height(), thumb, tradeScroll, maxScroll), thumb, hot);
    }

    private void drawMobScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        int total = mobChoices().size();
        int visible = geo.mobVisibleRows();
        if (total <= visible || visible <= 0) {
            return;
        }
        Rect track = geo.mobScrollbar();
        int thumb = FSGui.thumbHeight(track.height(), visible, total);
        boolean hot = track.contains(mouseX, mouseY);
        FSGui.scrollbar(graphics, track.x(), track.y(), track.width(), track.height(),
                FSGui.thumbTop(track.y(), track.height(), thumb, mobScroll, total - visible), thumb, hot);
    }

    // ------------------------------------------------------------ input

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int step = (int) -Math.signum(delta);
        if (activeTab == Tab.TRADES && geo.tradeListColumn().contains(mouseX, mouseY)) {
            int next = Math.max(0, Math.min(tradeScroll + step,
                    Math.max(0, rows.size() - geo.tradeVisibleRows())));
            if (next != tradeScroll) {
                tradeScroll = next;
                rebuildWidgets();
            }
            return true;
        }
        if (activeTab == Tab.APPEARANCE && kind == ShopObjectKind.LIVING
                && geo.mobListColumn().contains(mouseX, mouseY)) {
            int next = Math.max(0, Math.min(mobScroll + step,
                    Math.max(0, mobChoices().size() - geo.mobVisibleRows())));
            if (next != mobScroll) {
                mobScroll = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ------------------------------------------------------------ saving

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
