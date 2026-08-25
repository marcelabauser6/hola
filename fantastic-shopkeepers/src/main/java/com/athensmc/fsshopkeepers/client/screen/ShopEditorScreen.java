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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The screen an administrator sets a shop up on.
 *
 * <p>Built to match the Crates editor, because that is the Fantastic family's editor: the same centred panel capped at
 * 540x320, title strip, row of tabs, rule with a line of help under it, and footer. The controls are plain vanilla
 * {@link Button}s and {@link EditBox}es with the active tab marked by bold white text, exactly as Crates does it.</p>
 *
 * <p>Every position, for widgets and for the item icons alike, comes from {@link EditorGeometry}, and every button label
 * is cut to its button's width before it is drawn. Those two together are what keeps controls and text off each other:
 * one formula for where things go, and no label that can spill past its own edge.</p>
 *
 * <p>Typing survives a rebuild. Changing the mob search re-creates the list of buttons, which destroys the text field
 * along with them, so the field that had focus is remembered and re-focused with its caret at the end. Without that, a
 * search box accepts exactly one letter and then goes dead - which is precisely what it did.</p>
 */
public final class ShopEditorScreen extends Screen {

    private static final int GUTTER = EditorGeometry.GUTTER;
    private static final int TITLE_HEIGHT = EditorGeometry.TITLE_HEIGHT;

    /** Which detail block holds what, in the trades tab. */
    private static final int BLOCK_ITEM = 0;
    private static final int BLOCK_AMOUNT = 1;
    private static final int BLOCK_PRICE = 2;
    private static final int BLOCK_PAY_1 = 3;
    private static final int BLOCK_PAY_2 = 4;
    private static final int BLOCK_COUNT = 5;

    /** The most trades one shop may hold, matching the server's cap. */
    private static final int MAX_ROWS = 45;

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

    /** Which text field the keyboard belongs to, remembered across rebuilds. */
    private enum Focus {
        NONE, MOB_SEARCH, PRICE, NAME, ACCOUNT, HIRE, PERMISSION
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

        boolean hasCashPrice() {
            return priceCents() > 0L;
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
    private final Map<Focus, EditBox> fields = new LinkedHashMap<>();
    private Focus focus = Focus.NONE;
    private String helpLine = "";

    private boolean draggingTradeBar;
    private boolean draggingMobBar;

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
        fields.clear();
        helpLine = activeTab.help;

        initTabs();
        initFooter();
        switch (activeTab) {
            case TRADES -> initTrades();
            case APPEARANCE -> initAppearance();
            case SETTINGS -> initSettings();
        }
    }

    // ------------------------------------------------------------ widget helpers

    private void addLabel(String text, Rect where) {
        if (!where.isEmpty()) {
            labels.add(new Label(fit(text, where.width()), where.x(), where.y(), 0));
        }
    }

    private void addCentredLabel(String text, Rect where) {
        if (!where.isEmpty()) {
            int y = where.y() + (where.height() - font.lineHeight) / 2 + 1;
            labels.add(new Label(fit(text, where.width()), where.x(), y, where.width()));
        }
    }

    /**
     * Cuts text to a width.
     *
     * <p>Applied to every label and every button caption. Vanilla's {@link Button} draws whatever message it is given and
     * lets it run past its own edge, which is how "Pechera de Diamante y Netherita" came out clipped mid-word and
     * overlapping what was beside it.</p>
     */
    private String fit(String text, int maxWidth) {
        if (maxWidth <= 4) {
            return "";
        }
        return font.width(text) <= maxWidth ? text : font.plainSubstrByWidth(text, maxWidth);
    }

    private void button(Rect where, String text, boolean enabled, Runnable action) {
        if (where.isEmpty()) {
            return;
        }
        Button widget = Button.builder(Component.literal(fit(text, where.width() - 6)),
                        pressed -> action.run())
                .bounds(where.x(), where.y(), where.width(), where.height()).build();
        // A control that cannot do anything is greyed rather than left looking clickable, which is what made the
        // quantity buttons seem broken when the item on sale was a chestplate and could never stack past one.
        widget.active = enabled;
        addRenderableWidget(widget);
    }

    private void button(Rect where, String text, Runnable action) {
        button(where, text, true, action);
    }

    /**
     * A text field that keeps the keyboard when the screen rebuilds around it.
     *
     * <p>Registered under a {@link Focus} so that after a rebuild the field that had focus gets it back, with the caret at
     * the end of what was typed.</p>
     */
    private EditBox field(Rect where, Focus id, String value, String hint, int maxLength, boolean editable) {
        if (where.isEmpty()) {
            return null;
        }
        EditBox box = new EditBox(font, where.x() + 1, where.y() + 1, where.width() - 2,
                where.height() - 2, Component.literal(hint));
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setHint(Component.literal(hint));
        box.setEditable(editable);
        addRenderableWidget(box);
        fields.put(id, box);
        if (focus == id && editable) {
            box.setFocused(true);
            setInitialFocus(box);
            box.moveCursorToEnd();
        }
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
                    focus = Focus.NONE;
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

        // The rows themselves are drawn, not widgets. A list of widgets has to be rebuilt to scroll or filter, and
        // rebuilding while a text field has the keyboard is what killed typing: it clears the widget list that
        // Minecraft is part-way through iterating.
        button(geo.addTradeButton(), "\u00a7a+ Anadir", rows.size() < MAX_ROWS, () -> {
            rows.add(new Row(ItemStack.EMPTY, "", ItemStack.EMPTY, ItemStack.EMPTY));
            selectedRow = rows.size() - 1;
            tradeScroll = Math.max(0, rows.size() - geo.tradeVisibleRows());
            focus = Focus.NONE;
            rebuildWidgets();
        });
        // Removing the only row would leave nothing to edit, so it is disabled rather than silently re-adding one.
        button(geo.removeTradeButton(), "\u00a7cQuitar", rows.size() > 1, () -> {
            rows.remove(Math.max(0, Math.min(selectedRow, rows.size() - 1)));
            selectedRow = Math.max(0, selectedRow - 1);
            focus = Focus.NONE;
            rebuildWidgets();
        });

        initTradeDetail(rows.get(Math.max(0, Math.min(selectedRow, rows.size() - 1))));
    }

    private void initTradeDetail(Row row) {
        addLabel("\u00a77Articulo que vende", geo.detailLabel(BLOCK_ITEM));
        button(geo.detailControlAfterIcon(BLOCK_ITEM),
                row.result.isEmpty() ? "\u00a78Elegir articulo..." : row.result.getHoverName().getString(),
                () -> pickItem(chosen -> {
                    int keep = row.result.isEmpty() ? 1 : row.result.getCount();
                    chosen.setCount(Math.max(1, Math.min(keep, chosen.getMaxStackSize())));
                    row.result = chosen;
                }));

        int maxResult = row.result.isEmpty() ? 1 : row.result.getMaxStackSize();
        addLabel(maxResult > 1 ? "\u00a77Cantidad que entrega"
                : "\u00a77Cantidad que entrega \u00a78(no apilable)", geo.detailLabel(BLOCK_AMOUNT));
        countStepper(BLOCK_AMOUNT, row.result, false, () -> row.result, ignored -> {
        });

        addLabel("\u00a77Precio en " + Cash.currencyName() + " \u00a78(opcional)",
                geo.detailLabel(BLOCK_PRICE));
        EditBox price = field(geo.detailControl(BLOCK_PRICE), Focus.PRICE, row.priceText, "0.00", 16, true);
        if (price != null) {
            price.setFilter(text -> text.isEmpty() || text.matches("[0-9]{0,10}([.,][0-9]{0,2})?"));
            // No rebuild while typing a price: the row's caption catches up on the next rebuild, and rebuilding
            // here would take the keyboard away between digits.
            price.setResponder(text -> row.priceText = text);
        }

        payBlock(BLOCK_PAY_1, "Pago 1", row, true);
        // The trading window has two payment slots. A Cash price already occupies one, so a second payment item
        // would be charged without ever being shown to the buyer.
        payBlock(BLOCK_PAY_2, "Pago 2", row, !row.hasCashPrice());

        String reason = row.toOffer().incompleteReason();
        addLabel(reason == null ? "\u00a7aEsta fila esta lista." : "\u00a7e" + reason,
                geo.detailStatus(BLOCK_COUNT));
    }

    /** A payment block: the item, a button to choose it, and a stepper for how many. */
    private void payBlock(int block, String title, Row row, boolean available) {
        boolean isFirst = block == BLOCK_PAY_1;
        ItemStack current = isFirst ? row.cost1 : row.cost2;

        addLabel(available ? "\u00a77" + title + " \u00a78(articulo, opcional)"
                : "\u00a78" + title + " (ocupado por el precio en Cash)", geo.detailLabel(block));

        button(geo.detailControlAfterIconBeforeStepper(block),
                current.isEmpty() ? "\u00a78Elegir articulo..." : current.getHoverName().getString(),
                available, () -> pickItem(chosen -> {
                    int keep = current.isEmpty() ? 1 : current.getCount();
                    chosen.setCount(Math.max(1, Math.min(keep, chosen.getMaxStackSize())));
                    if (isFirst) {
                        row.cost1 = chosen;
                    } else {
                        row.cost2 = chosen;
                    }
                }));

        countStepper(block, current, available, () -> isFirst ? row.cost1 : row.cost2, cleared -> {
            if (isFirst) {
                row.cost1 = cleared;
            } else {
                row.cost2 = cleared;
            }
        });
    }

    /**
     * A minus/count/plus stepper.
     *
     * <p>Both buttons are disabled when they would do nothing: at one for minus, at the item's maximum stack for plus, and
     * always when there is no item. A greyed button says "not possible"; a live button that ignores the click says
     * "broken", which is how the quantity control read when the item on sale was a chestplate.</p>
     *
     * <p>For a payment slot, minus at one empties it, which is the obvious way to undo a wrong pick.</p>
     */
    private void countStepper(int block, ItemStack shown, boolean clearable,
            java.util.function.Supplier<ItemStack> target,
            java.util.function.Consumer<ItemStack> clearer) {
        boolean present = !shown.isEmpty();
        int count = present ? shown.getCount() : 0;
        int max = present ? shown.getMaxStackSize() : 1;

        boolean canDecrease = present && (count > 1 || clearable);
        button(geo.stepperMinus(block), "-", canDecrease, () -> {
            ItemStack stack = target.get();
            if (stack.isEmpty()) {
                return;
            }
            if (stack.getCount() > 1) {
                stack.setCount(stack.getCount() - 1);
            } else if (clearable) {
                clearer.accept(ItemStack.EMPTY);
            }
            rebuildWidgets();
        });

        addCentredLabel(present ? "\u00a7f" + count : "\u00a78-", geo.stepperCount(block));

        button(geo.stepperPlus(block), "+", present && count < max, () -> {
            ItemStack stack = target.get();
            if (!stack.isEmpty() && stack.getCount() < stack.getMaxStackSize()) {
                stack.setCount(stack.getCount() + 1);
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

    /**
     * The vanilla creatures that live in {@link MobCategory#MISC}.
     *
     * <p>MISC is not "not a mob": vanilla files the villager, the armour stand and both golems under it, alongside arrows
     * and boats. Excluding the whole category is why the villager - the most obvious shopkeeper there is - was missing
     * from the list.</p>
     */
    private static final List<String> LIVING_MISC = List.of(
            "villager", "armor_stand", "iron_golem", "snow_golem");

    /** Whether a type can stand in the world as a shopkeeper. */
    private boolean usableAsBody(EntityType<?> type) {
        if (!type.canSummon()) {
            // Drops the player, lightning and the fishing bobber, none of which can be placed.
            return false;
        }
        if (type.getCategory() != MobCategory.MISC) {
            return true;
        }
        ResourceLocation id = EntityType.getKey(type);
        if (!"minecraft".equals(id.getNamespace())) {
            // Another mod's MISC entity is far more likely to be a creature than a projectile, and the server
            // checks its own allow-list before anything is spawned anyway.
            return true;
        }
        return LIVING_MISC.contains(id.getPath());
    }

    /**
     * The mob list, filtered by the search box and sorted by name.
     *
     * <p>Sorted so it can be browsed as well as searched: registry order puts the villager somewhere in the middle of a
     * hundred entries with no way to predict where.</p>
     */
    private List<ResourceLocation> mobChoices() {
        String needle = mobSearch.toLowerCase();
        List<Map.Entry<String, ResourceLocation>> named = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (!usableAsBody(type)) {
                continue;
            }
            ResourceLocation id = EntityType.getKey(type);
            String label = type.getDescription().getString();
            if (needle.isEmpty() || label.toLowerCase().contains(needle)
                    || id.getPath().contains(needle)) {
                named.add(Map.entry(label, id));
            }
        }
        named.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));
        List<ResourceLocation> out = new ArrayList<>(named.size());
        for (Map.Entry<String, ResourceLocation> entry : named) {
            out.add(entry.getValue());
        }
        return out;
    }

    private String mobLabel(ResourceLocation id) {
        return EntityType.byString(id.toString())
                .map(type -> type.getDescription().getString()).orElse(id.getPath());
    }

    private void initAppearance() {
        addLabel("\u00a77Nombre de la tienda", geo.nameLabel());
        EditBox name = field(geo.nameField(), Focus.NAME, shopName, "sin nombre",
                SaveShopPacket.MAX_NAME, true);
        if (name != null) {
            name.setResponder(text -> shopName = text);
        }

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

        EditBox search = field(geo.mobSearchField(), Focus.MOB_SEARCH, mobSearch, "escribe para filtrar",
                32, true);
        if (search != null) {
            // Only the text is recorded. The list below is drawn from it every frame, so no widget is destroyed
            // between one keystroke and the next.
            search.setResponder(text -> {
                if (!text.equals(mobSearch)) {
                    mobSearch = text;
                    mobScroll = 0;
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

    /**
     * The settings tab.
     *
     * <p>Every row says what it does underneath itself, because none of these are guessable. The two that only make sense
     * for a shop with an owner are hidden on an admin shop rather than shown doing nothing: an admin shop has nobody to
     * pay and nobody to sell itself to.</p>
     */
    private void initSettings() {
        boolean playerShop = source.type().isPlayerShop();
        int block = 0;

        if (playerShop) {
            addLabel("\u00a77Cuenta bancaria donde cobrar", geo.settingLabel(block));
            EditBox account = field(geo.settingControl(block), Focus.ACCOUNT,
                    linkedAccount > 0 ? String.valueOf(linkedAccount) : "", "vacio = tu cuenta principal",
                    10, true);
            if (account != null) {
                account.setFilter(text -> text.matches("[0-9]{0,10}"));
                account.setResponder(text -> {
                    try {
                        linkedAccount = text.isBlank() ? 0 : Math.max(0, Integer.parseInt(text));
                    } catch (NumberFormatException tooBig) {
                        linkedAccount = 0;
                    }
                });
            }
            addNote("Numero de cuenta donde entra el dinero de cada venta.", geo.settingNote(block));
            block++;

            addLabel("\u00a77Poner la tienda en venta", geo.settingLabel(block));
            button(geo.settingControl(block),
                    forHire ? "\u00a7aSi: otro jugador puede comprarla" : "\u00a77No: la tienda no se vende",
                    () -> {
                        forHire = !forHire;
                        rebuildWidgets();
                    });
            addNote("Si esta activo, quien pague el precio de abajo pasa a ser el dueno.",
                    geo.settingNote(block));
            block++;

            addLabel(forHire ? "\u00a77Precio para quedarse la tienda"
                    : "\u00a78Precio para quedarse la tienda", geo.settingLabel(block));
            EditBox hire = field(geo.settingControl(block), Focus.HIRE, hireCostText,
                    forHire ? "0.00" : "activa la venta de arriba primero", 16, forHire);
            if (hire != null) {
                hire.setFilter(text -> text.isEmpty() || text.matches("[0-9]{0,10}([.,][0-9]{0,2})?"));
                hire.setResponder(text -> hireCostText = text);
            }
            addNote("Lo que cuesta comprarte la tienda entera, con sus tratos y su cofre.",
                    geo.settingNote(block));
            block++;
        }

        addLabel("\u00a77Permiso necesario para comprar aqui", geo.settingLabel(block));
        EditBox perms = field(geo.settingControl(block), Focus.PERMISSION, permission,
                "vacio = cualquiera puede comprar", 128, true);
        if (perms != null) {
            perms.setResponder(text -> permission = text);
        }
        addNote("Nodo de permiso, por ejemplo tienda.vip. Vacio deja comprar a todo el mundo.",
                geo.settingNote(block));
        block++;

        if (block < geo.settingBlocks()) {
            addLabel(Cash.available()
                    ? "\u00a7aFantastic Currency detectado: los precios se cobran en " + Cash.currencyName()
                    : "\u00a7cFantastic Currency no esta instalado", geo.settingLabel(block));
            addNote(Cash.available()
                    ? "El dinero sale y entra de las cuentas del banco."
                    : "Sin el, solo funcionan los pagos con articulos.", geo.settingControl(block));
        }
    }

    /** A grey explanation line under a control. */
    private void addNote(String text, Rect where) {
        if (!where.isEmpty()) {
            labels.add(new Label(fit("\u00a78" + text, where.width()), where.x(), where.y(), 0));
        }
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
        graphics.drawString(font, fit("\u00a7d\u2726 \u00a7fFantastic Shopkeepers \u00a7d\u2726 \u00a77- "
                        + source.type().title() + owner, panelWidth - GUTTER * 2),
                leftPos + GUTTER, topPos + 6, 0xFFFFFF, false);

        if (!helpLine.isEmpty()) {
            graphics.drawString(font, fit("\u00a77" + helpLine, panelWidth - GUTTER * 2),
                    leftPos + GUTTER, separatorY + 4, FSGui.HELP_TEXT, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        for (Label label : labels) {
            int x = label.centreWidth() <= 0 ? label.x()
                    : label.x() + (label.centreWidth() - font.width(label.text())) / 2;
            graphics.drawString(font, label.text(), x, label.y(), FSGui.TEXT, false);
        }

        if (activeTab == Tab.TRADES) {
            drawTradeRows(graphics, mouseX, mouseY);
            drawTradeIcons(graphics);
            drawScrollbar(graphics, geo.tradeScrollbar(), rows.size(), geo.tradeVisibleRows(), tradeScroll,
                    mouseX, mouseY, draggingTradeBar);
        } else if (activeTab == Tab.APPEARANCE && kind == ShopObjectKind.LIVING) {
            drawMobRows(graphics, mouseX, mouseY);
            drawScrollbar(graphics, geo.mobScrollbar(), mobChoices().size(), geo.mobVisibleRows(), mobScroll,
                    mouseX, mouseY, draggingMobBar);
        }

        drawItemTooltip(graphics, mouseX, mouseY);
    }

    /**
     * One row of a drawn list, in the Fantastic lists' style.
     *
     * <p>Selected rows take the accent, hovered rows lighten. Drawn rather than made of buttons so that scrolling and
     * filtering never have to rebuild the screen.</p>
     */
    private void drawListRow(GuiGraphics graphics, Rect where, String text, boolean selected, boolean hovered) {
        if (where.isEmpty()) {
            return;
        }
        if (selected) {
            graphics.fill(where.x(), where.y(), where.right(), where.bottom(), 0x66E8A33D);
        } else if (hovered) {
            graphics.fill(where.x(), where.y(), where.right(), where.bottom(), 0x33FFFFFF);
        }
        int textY = where.y() + (where.height() - font.lineHeight) / 2 + 1;
        graphics.drawString(font, fit(text, where.width() - 6), where.x() + 3, textY, FSGui.TEXT, false);
    }

    private void drawTradeRows(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect column = geo.tradeListColumn();
        FSGui.inset(graphics, column.x(), column.y(), column.width() - 2, column.height() - 22);
        int visible = geo.tradeVisibleRows();
        tradeScroll = Math.max(0, Math.min(tradeScroll, Math.max(0, rows.size() - visible)));
        for (int slot = 0; slot < visible; slot++) {
            int index = tradeScroll + slot;
            if (index >= rows.size()) {
                break;
            }
            Row row = rows.get(index);
            Rect where = geo.tradeRow(slot);
            String price = row.priceText.isBlank() ? "" : "  \u00a7a" + row.priceText;
            drawListRow(graphics, where, row.describe() + price, index == selectedRow,
                    where.contains(mouseX, mouseY));
        }
    }

    private void drawMobRows(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect column = geo.mobListColumn();
        FSGui.inset(graphics, column.x(), column.y(), column.width() - 2, column.height());
        List<ResourceLocation> choices = mobChoices();
        int visible = geo.mobVisibleRows();
        mobScroll = Math.max(0, Math.min(mobScroll, Math.max(0, choices.size() - visible)));
        if (choices.isEmpty()) {
            Rect first = geo.mobRow(0);
            if (!first.isEmpty()) {
                graphics.drawString(font, "\u00a78Ningun mob coincide.", first.x() + 3,
                        first.y() + 5, FSGui.TEXT_DIM, false);
            }
            return;
        }
        for (int slot = 0; slot < visible; slot++) {
            int index = mobScroll + slot;
            if (index >= choices.size()) {
                break;
            }
            ResourceLocation id = choices.get(index);
            Rect where = geo.mobRow(slot);
            boolean current = id.equals(entityType);
            drawListRow(graphics, where, (current ? "\u00a7a\u2714 " : "") + mobLabel(id), current,
                    where.contains(mouseX, mouseY));
        }
    }

    /** Selecting a row in either drawn list. */
    private boolean clickList(double mouseX, double mouseY) {
        if (activeTab == Tab.TRADES) {
            int visible = geo.tradeVisibleRows();
            for (int slot = 0; slot < visible; slot++) {
                int index = tradeScroll + slot;
                if (index >= rows.size()) {
                    break;
                }
                if (geo.tradeRow(slot).contains(mouseX, mouseY)) {
                    if (index != selectedRow) {
                        selectedRow = index;
                        focus = Focus.NONE;
                        // The detail column is widgets, so choosing a different row does rebuild - but a click is
                        // not a keystroke, so nothing is being typed into at the time.
                        rebuildWidgets();
                    }
                    return true;
                }
            }
            return false;
        }
        if (activeTab == Tab.APPEARANCE && kind == ShopObjectKind.LIVING) {
            List<ResourceLocation> choices = mobChoices();
            int visible = geo.mobVisibleRows();
            for (int slot = 0; slot < visible; slot++) {
                int index = mobScroll + slot;
                if (index >= choices.size()) {
                    break;
                }
                if (geo.mobRow(slot).contains(mouseX, mouseY)) {
                    ResourceLocation id = choices.get(index);
                    if (!id.equals(entityType)) {
                        entityType = id;
                        // Variant data belonged to the old species and means nothing on the new one.
                        entityData = new CompoundTag();
                        rebuildWidgets();
                    }
                    return true;
                }
            }
        }
        return false;
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

    /**
     * The tooltip for an item icon.
     *
     * <p>Tested against the icon's own square and nothing wider, so a full armour tooltip only appears when the cursor is
     * actually on the armour. Using the whole row put a tooltip over half the screen whenever the mouse crossed it.</p>
     */
    private void drawItemTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (activeTab != Tab.TRADES) {
            return;
        }
        int visible = geo.tradeVisibleRows();
        for (int slot = 0; slot < visible; slot++) {
            int index = tradeScroll + slot;
            if (index >= rows.size()) {
                break;
            }
            if (tooltipFor(graphics, geo.tradeRowIcon(slot), rows.get(index).result, mouseX, mouseY)) {
                return;
            }
        }
        Row selected = rows.get(Math.max(0, Math.min(selectedRow, rows.size() - 1)));
        if (tooltipFor(graphics, geo.detailIcon(BLOCK_ITEM), selected.result, mouseX, mouseY)
                || tooltipFor(graphics, geo.detailIcon(BLOCK_PAY_1), selected.cost1, mouseX, mouseY)) {
            return;
        }
        tooltipFor(graphics, geo.detailIcon(BLOCK_PAY_2), selected.cost2, mouseX, mouseY);
    }

    private boolean tooltipFor(GuiGraphics graphics, Rect where, ItemStack stack, int mouseX, int mouseY) {
        // The icon well is 18 wide but the item inside it is 16, drawn one pixel in.
        Rect item = where.isEmpty() ? Rect.EMPTY
                : new Rect(where.x() + 1, where.y() + 1, 16, 16);
        if (stack.isEmpty() || item.isEmpty() || !item.contains(mouseX, mouseY)) {
            return false;
        }
        graphics.renderTooltip(font, stack, mouseX, mouseY);
        return true;
    }

    private void drawScrollbar(GuiGraphics graphics, Rect track, int total, int visible, int scroll,
            int mouseX, int mouseY, boolean dragging) {
        if (track.isEmpty() || visible <= 0 || total <= visible) {
            return;
        }
        int thumb = FSGui.thumbHeight(track.height(), visible, total);
        boolean hot = dragging || track.contains(mouseX, mouseY);
        FSGui.scrollbar(graphics, track.x(), track.y(), track.width(), track.height(),
                FSGui.thumbTop(track.y(), track.height(), thumb, scroll, total - visible), thumb, hot);
    }

    // ------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activeTab == Tab.TRADES && startBarDrag(geo.tradeScrollbar(), rows.size(),
                geo.tradeVisibleRows(), mouseX, mouseY)) {
            draggingTradeBar = true;
            scrollBarTo(mouseY, geo.tradeScrollbar(), rows.size(), geo.tradeVisibleRows(), true);
            return true;
        }
        if (activeTab == Tab.APPEARANCE && kind == ShopObjectKind.LIVING) {
            List<ResourceLocation> choices = mobChoices();
            if (startBarDrag(geo.mobScrollbar(), choices.size(), geo.mobVisibleRows(), mouseX, mouseY)) {
                draggingMobBar = true;
                scrollBarTo(mouseY, geo.mobScrollbar(), choices.size(), geo.mobVisibleRows(), false);
                return true;
            }
        }
        if (clickList(mouseX, mouseY)) {
            return true;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        // Whichever field the click focused becomes the one to restore after the next rebuild.
        focus = Focus.NONE;
        for (Map.Entry<Focus, EditBox> entry : fields.entrySet()) {
            if (entry.getValue().isFocused()) {
                focus = entry.getKey();
                break;
            }
        }
        return handled;
    }

    private boolean startBarDrag(Rect track, int total, int visible, double mouseX, double mouseY) {
        return !track.isEmpty() && visible > 0 && total > visible && track.contains(mouseX, mouseY);
    }

    /**
     * Moves a list to where the scrollbar was grabbed.
     *
     * <p>The thumb centres on the cursor, so grabbing the middle of the bar shows the middle of the list, which is what
     * dragging a scrollbar is expected to do.</p>
     */
    private void scrollBarTo(double mouseY, Rect track, int total, int visible, boolean trades) {
        int maxScroll = Math.max(0, total - visible);
        if (maxScroll == 0 || track.isEmpty()) {
            return;
        }
        int thumb = FSGui.thumbHeight(track.height(), visible, total);
        int travel = Math.max(1, track.height() - thumb);
        double fraction = (mouseY - track.y() - thumb / 2.0D) / travel;
        int target = (int) Math.round(fraction * maxScroll);
        target = Math.max(0, Math.min(target, maxScroll));
        // No rebuild: both lists are drawn from their scroll offset every frame.
        if (trades) {
            tradeScroll = target;
        } else {
            mobScroll = target;
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingTradeBar) {
            scrollBarTo(mouseY, geo.tradeScrollbar(), rows.size(), geo.tradeVisibleRows(), true);
            return true;
        }
        if (draggingMobBar) {
            scrollBarTo(mouseY, geo.mobScrollbar(), mobChoices().size(), geo.mobVisibleRows(), false);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingTradeBar = false;
        draggingMobBar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int step = (int) -Math.signum(delta);
        if (activeTab == Tab.TRADES && geo.tradeListColumn().contains(mouseX, mouseY)) {
            tradeScroll = Math.max(0, Math.min(tradeScroll + step,
                    Math.max(0, rows.size() - geo.tradeVisibleRows())));
            return true;
        }
        if (activeTab == Tab.APPEARANCE && kind == ShopObjectKind.LIVING
                && geo.mobListColumn().contains(mouseX, mouseY)) {
            mobScroll = Math.max(0, Math.min(mobScroll + step,
                    Math.max(0, mobChoices().size() - geo.mobVisibleRows())));
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
