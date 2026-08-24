/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.EditBox
 *  net.minecraft.client.gui.components.Tooltip
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.network.chat.Component
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.world.item.CreativeModeTab
 *  net.minecraft.world.item.CreativeModeTabs
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.Items
 *  net.minecraft.world.level.ItemLike
 */
package com.fshop.client.screen;

import net.minecraft.resources.ResourceLocation;

import net.minecraft.core.registries.Registries;

import com.fshop.client.RegistryLists;
import com.fshop.client.Sfx;
import com.fshop.client.screen.NbtEditorScreen;
import com.fshop.client.widget.ScrollSelector;
import com.fshop.economy.CoinEconomy;
import com.fshop.network.CollectMainShopPacket;
import com.fshop.network.PacketHandler;
import com.fshop.network.SaveMainShopPacket;
import com.fshop.shop.PlayerShop;
import com.fshop.shop.ShopOffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

public final class MainShopCreatorScreen
extends Screen {
    private final PlayerShop shop;
    private final List<ShopOffer> offers;
    private final long[] pending;
    private String name;
    private ItemStack icon;
    private Tab activeTab = Tab.ITEMS;
    private ShopOffer selected;
    private boolean fromInventory = false;
    private final List<Label> labels = new ArrayList<Label>();
    private String help = "";
    private int leftPos;
    private int topPos;
    private int panelW;
    private int panelH;

    public MainShopCreatorScreen(PlayerShop shop) {
        super((Component)Component.m_237113_((String)"Creador de tienda"));
        this.shop = shop;
        this.offers = new ArrayList<ShopOffer>(shop.getOffers());
        // Every currency, so cash earnings are visible and collectable here too.
        this.pending = new long[CoinEconomy.TYPES];
        for (int c = 0; c < CoinEconomy.TYPES; ++c) {
            this.pending[c] = shop.getPendingEarnings(c);
        }
        this.name = shop.getName() == null || shop.getName().isBlank() ? "La Moneda de Oro" : shop.getName();
        this.icon = shop.getIcon().m_41619_() ? new ItemStack((ItemLike)Items.f_42417_) : shop.getIcon().m_41777_();
    }

    protected void m_7856_() {
        this.panelW = Math.min(this.f_96543_ - 16, 460);
        this.panelH = Math.min(this.f_96544_ - 16, 300);
        this.leftPos = (this.f_96543_ - this.panelW) / 2;
        this.topPos = (this.f_96544_ - this.panelH) / 2;
        this.labels.clear();
        this.initHeader();
        this.initFooter();
        if (this.activeTab == Tab.ITEMS) {
            this.initItems();
        } else {
            this.initSettings();
        }
    }

    private int bodyX() {
        return this.leftPos + 8;
    }

    private int bodyY() {
        return this.topPos + 58;
    }

    private int bodyW() {
        return this.panelW - 16;
    }

    private int bodyH() {
        return this.panelH - 58 - 28;
    }

    private void initHeader() {
        Tab[] tabs = Tab.values();
        int gap = 2;
        int tabW = (this.panelW - 16 - gap * (tabs.length - 1)) / tabs.length;
        int x = this.leftPos + 8;
        int y = this.topPos + 22;
        for (Tab tab : tabs) {
            boolean active = tab == this.activeTab;
            String text = (active ? "\u00a7f\u00a7l" : "\u00a77") + tab.label;
            this.m_142416_(Button.m_253074_((Component)Component.m_237113_((String)text), b -> {
                this.activeTab = tab;
                Sfx.click();
                this.m_232761_();
            }).m_252987_(x, y, tabW, 16).m_253136_());
            x += tabW + gap;
        }
    }

    private void initFooter() {
        int w = 150;
        this.m_142416_(Button.m_253074_((Component)Component.m_237113_((String)"\u00a7aGuardar y publicar"), b -> {
            this.save();
            Sfx.success();
            this.m_7379_();
        }).m_252987_(this.leftPos + this.panelW - w - 8, this.topPos + this.panelH - 24, w, 18).m_253136_());
        this.m_142416_(Button.m_253074_((Component)Component.m_237113_((String)"Cerrar"), b -> this.m_7379_()).m_252987_(this.leftPos + 8, this.topPos + this.panelH - 24, 80, 18).m_253136_());
        long sum = 0L;
        for (long amount : this.pending) {
            sum += amount;
        }
        String cobrar = sum > 0L
                ? "\u00a7aCobrar: " + this.pending[2] + "o " + this.pending[1] + "p " + this.pending[0] + "b"
                        + (this.pending.length > CoinEconomy.CASH && this.pending[CoinEconomy.CASH] > 0L
                                ? " " + CoinEconomy.formatAmount(CoinEconomy.CASH, this.pending[CoinEconomy.CASH])
                                : "")
                : "\u00a77Sin ganancias";
        this.m_142416_(Button.m_253074_((Component)Component.m_237113_((String)cobrar), b -> {
            long total = 0L;
            for (long amount : this.pending) {
                total += amount;
            }
            if (total > 0L) {
                PacketHandler.sendToServer(new CollectMainShopPacket(false));
                java.util.Arrays.fill(this.pending, 0L);
                Sfx.success();
                this.m_232761_();
            }
        }).m_257505_(Tooltip.m_257550_((Component)Component.m_237113_((String)"Cobra las ganancias que la tienda del servidor ha recaudado (se depositan en tus monedas)."))).m_252987_(this.leftPos + 92, this.topPos + this.panelH - 24, 128, 18).m_253136_());
    }

    private void initItems() {
        this.help = "Clic en un item para a\u00f1adirlo. Pasa el rat\u00f3n por cada control para ver qu\u00e9 hace.";
        int x = this.bodyX();
        int y = this.bodyY();
        int colW = (this.bodyW() - 8) / 2;
        int rightX = x + colW + 8;
        int catRows = 76;
        int listBottom = y + this.bodyH() - catRows - 2;
        this.m_142416_(Button.m_253074_((Component)Component.m_237113_((String)(this.fromInventory ? "Fuente: \u00a7bInventario" : "Fuente: \u00a7eRegistro")), b -> {
            this.fromInventory = !this.fromInventory;
            Sfx.click();
            this.m_232761_();
        }).m_257505_(Tooltip.m_257550_((Component)Component.m_237113_((String)"Registro = todos los items del juego. Inventario = tus items REALES con su NBT (llaves de crates, cabezas, items custom con nombre/lore/encantamientos)."))).m_252987_(x, y, colW, 16).m_253136_());
        EditBox search = new EditBox(this.f_96547_, x, y + 18, colW, 16, (Component)Component.m_237119_());
        search.m_257771_((Component)Component.m_237113_((String)"Buscar item..."));
        this.m_142416_(search);
        int listY = y + 38;
        int listH = listBottom - listY;
        if (this.fromInventory) {
            ArrayList<ItemStack> invItems = new ArrayList<ItemStack>();
            LocalPlayer p = Minecraft.m_91087_().f_91074_;
            if (p != null) {
                for (ItemStack st2 : p.m_150109_().f_35974_) {
                    if (st2 == null || st2.m_41619_()) continue;
                    invItems.add(st2.m_41777_());
                }
            }
            ScrollSelector<ItemStack> invList = new ScrollSelector<ItemStack>(x, listY, colW, listH, 18, st -> st.m_41786_().getString(), st -> st.m_41786_().getString() + " " + RegistryLists.itemId(st.m_41720_()), st -> st);
            invList.setItems(invItems);
            invList.onSelect(st -> {
                this.addOfferStack((ItemStack)st);
                Sfx.select();
                this.m_232761_();
            });
            search.m_94151_(invList::setQuery);
            this.m_142416_(invList);
            if (invItems.isEmpty()) {
                this.addLabel("\u00a77Tu inventario esta vacio.", x, listY + 4);
            }
        } else {
            ScrollSelector<Item> items = new ScrollSelector<Item>(x, listY, colW, listH, 18, RegistryLists::itemName, it -> RegistryLists.itemName(it) + " " + RegistryLists.itemId(it), ItemStack::new);
            items.setItems(RegistryLists.items());
            items.onSelect(it -> {
                this.addOffer((Item)it);
                Sfx.select();
                this.m_232761_();
            });
            search.m_94151_(items::setQuery);
            this.m_142416_(items);
        }
        int bw = colW / 3 - 2;
        int r1 = y + this.bodyH() - 72;
        int r2 = y + this.bodyH() - 54;
        int r3 = y + this.bodyH() - 36;
        int r4 = y + this.bodyH() - 18;
        this.m_142416_(this.catButton("Bloques", x, r1, bw, (ResourceKey<CreativeModeTab>)ResourceKey.m_135785_(Registries.f_279569_, new ResourceLocation("building_blocks"))));
        this.m_142416_(this.catButton("Naturales", x + bw + 2, r1, bw, (ResourceKey<CreativeModeTab>)ResourceKey.m_135785_(Registries.f_279569_, new ResourceLocation("natural_blocks"))));
        this.m_142416_(this.catButton("Colores", x + 2 * (bw + 2), r1, bw, (ResourceKey<CreativeModeTab>)ResourceKey.m_135785_(Registries.f_279569_, new ResourceLocation("colored_blocks"))));
        this.m_142416_(this.catButton("Funcional", x, r2, bw, (ResourceKey<CreativeModeTab>)ResourceKey.m_135785_(Registries.f_279569_, new ResourceLocation("functional_blocks"))));
        this.m_142416_(this.catButton("Combate", x + bw + 2, r2, bw, (ResourceKey<CreativeModeTab>)ResourceKey.m_135785_(Registries.f_279569_, new ResourceLocation("combat"))));
        this.m_142416_(this.catButton("Herram.", x + 2 * (bw + 2), r2, bw, (ResourceKey<CreativeModeTab>)ResourceKey.m_135785_(Registries.f_279569_, new ResourceLocation("tools_and_utilities"))));
        this.m_142416_(this.catButton("Comida", x, r3, bw, (ResourceKey<CreativeModeTab>)ResourceKey.m_135785_(Registries.f_279569_, new ResourceLocation("food_and_drinks"))));
        this.m_142416_(this.catButton("Redstone", x + bw + 2, r3, bw, (ResourceKey<CreativeModeTab>)ResourceKey.m_135785_(Registries.f_279569_, new ResourceLocation("redstone_blocks"))));
        this.m_142416_(this.catButton("Ingred.", x + 2 * (bw + 2), r3, bw, (ResourceKey<CreativeModeTab>)ResourceKey.m_135785_(Registries.f_279569_, new ResourceLocation("ingredients"))));
        this.m_142416_(this.catButton("Huevos", x, r4, bw, (ResourceKey<CreativeModeTab>)ResourceKey.m_135785_(Registries.f_279569_, new ResourceLocation("spawn_eggs"))));
        this.m_142416_(Button.m_253074_((Component)Component.m_237113_((String)"\u00a7e+ TODO"), b -> {
            for (Item it : RegistryLists.items()) {
                this.addOfferIfNew(it);
            }
            Sfx.select();
            this.m_232761_();
        }).m_257505_(Tooltip.m_257550_((Component)Component.m_237113_((String)"Agrega TODOS los items del juego (\u00a1son muchos!). \u00dasalo con cuidado."))).m_252987_(x + bw + 2, r4, bw, 16).m_253136_());
        this.m_142416_(Button.m_253074_((Component)Component.m_237113_((String)"\u00a7cLimpiar"), b -> {
            this.offers.clear();
            this.selected = null;
            Sfx.click();
            this.m_232761_();
        }).m_257505_(Tooltip.m_257550_((Component)Component.m_237113_((String)"Quita TODAS las ofertas de la tienda."))).m_252987_(x + 2 * (bw + 2), r4, bw, 16).m_253136_());
        int editorH = 116;
        ScrollSelector<ShopOffer> list = new ScrollSelector<ShopOffer>(rightX, y, colW, this.bodyH() - editorH, 16, o -> (o == this.selected ? "\u00a7e\u25b6 " : "\u00a7f") + o.getItem().m_41786_().getString() + (String)(o.getBundle() > 1 ? " \u00a78x" + o.getBundle() : "") + " " + CoinEconomy.coinColorCode(o.getCoin()) + CoinEconomy.formatAmount(o.getCoin(), o.getUnitPrice()) + MainShopCreatorScreen.coinShort(o.getCoin()), o -> o.getItem().m_41786_().getString(), ShopOffer::getItem);
        list.setItems(new ArrayList<ShopOffer>(this.offers));
        list.onSelect(o -> {
            this.selected = o;
            this.m_232761_();
        });
        this.m_142416_(list);
        if (this.selected != null && this.offers.contains(this.selected)) {
            ShopOffer o2 = this.selected;
            int ey = y + this.bodyH() - 110;
            this.addLongField(rightX + 46, ey, 70, o2.getUnitPrice(), v -> o2.setUnitPrice(Math.max(1L, v)), "Precio:", rightX, ey + 4, "Precio por CADA venta (por el 'Vender de a'). Ej: si vendes de a 64 y el precio es 10, el jugador paga 10 por 64 items.");
            this.m_142416_(Button.m_253074_((Component)Component.m_237113_((String)("Moneda: " + CoinEconomy.coinColorCode(o2.getCoin()) + MainShopCreatorScreen.coinName(o2.getCoin()))), b -> {
                // Cycle over the currencies that are actually selectable. Stepping modulo TYPES and
                // then sanitizing meant gold -> cash -> sanitize -> gold without the currency mod, so
                // the button could never get back to bronze.
                int currencies = CoinEconomy.cashAvailable() ? CoinEconomy.TYPES : CoinEconomy.GOLD + 1;
                o2.setCoin(CoinEconomy.sanitize((o2.getCoin() + 1) % currencies));
                Sfx.click();
                this.m_232761_();
            }).m_257505_(Tooltip.m_257550_((Component)Component.m_237113_((String)"Moneda del precio: bronce, plata, oro o cash digital. Clic para cambiar."))).m_252987_(rightX + 122, ey, colW - 122, 16).m_253136_());
            int sy = ey + 22;
            this.m_142416_(Button.m_253074_((Component)Component.m_237113_((String)(o2.isInfinite() ? "\u00a7bStock: \u221e" : "\u00a7fStock: limitado")), b -> {
                o2.setInfinite(!o2.isInfinite());
                Sfx.click();
                this.m_232761_();
            }).m_257505_(Tooltip.m_257550_((Component)Component.m_237113_((String)"\u221e = nunca se agota (ideal para la tienda del servidor). 'Limitado' = defines una cantidad exacta."))).m_252987_(rightX, sy, colW / 2 - 2, 16).m_253136_());
            if (!o2.isInfinite()) {
                this.addIntField(rightX + colW / 2 + 30, sy, colW / 2 - 32, o2.getStock(), v -> o2.setStock(Math.max(0, v)), "Cant.", rightX + colW / 2, sy + 4, "Cantidad total de items disponibles para vender.");
            }
            int by = ey + 44;
            this.addIntField(rightX + 74, by, 40, o2.getBundle(), v -> o2.setBundle(Math.max(1, v)), "Vender de a:", rightX, by + 4, "Cuantos items entrega CADA compra y a los que aplica el precio. 1 = de a uno. 64 = vende de a un stack. El stock no cambia esto.");
            this.m_142416_(Button.m_253074_((Component)Component.m_237113_((String)"\u00a7b\u270e Editar NBT"), b -> this.f_96541_.m_91152_((Screen)new NbtEditorScreen(this, o2.getItem()))).m_257505_(Tooltip.m_257550_((Component)Component.m_237113_((String)"Personaliza el item: nombre y lore con color, encantamientos, atributos... (items custom)."))).m_252987_(rightX + 120, by, colW - 120, 16).m_253136_());
            int ry = ey + 66;
            this.m_142416_(Button.m_253074_((Component)Component.m_237113_((String)"Precio a TODAS"), b -> {
                for (ShopOffer x2 : this.offers) {
                    x2.setUnitPrice(o2.getUnitPrice());
                    x2.setCoin(o2.getCoin());
                }
                Sfx.success();
                this.m_232761_();
            }).m_257505_(Tooltip.m_257550_((Component)Component.m_237113_((String)"Aplica ESTE precio y moneda a TODAS las ofertas de golpe (precios por bultos)."))).m_252987_(rightX, ry, colW / 2 - 2, 16).m_253136_());
            this.m_142416_(Button.m_253074_((Component)Component.m_237113_((String)"Stack a TODAS"), b -> {
                for (ShopOffer x2 : this.offers) {
                    x2.setBundle(o2.getBundle());
                }
                Sfx.success();
                this.m_232761_();
            }).m_257505_(Tooltip.m_257550_((Component)Component.m_237113_((String)"Aplica ESTE 'Vender de a' (tama\u00f1o de venta por stack) a TODAS las ofertas."))).m_252987_(rightX + colW / 2, ry, colW / 2, 16).m_253136_());
            int ry2 = ey + 88;
            this.m_142416_(Button.m_253074_((Component)Component.m_237113_((String)"\u00a7cQuitar este item"), b -> {
                this.offers.remove(o2);
                this.selected = null;
                Sfx.click();
                this.m_232761_();
            }).m_257505_(Tooltip.m_257550_((Component)Component.m_237113_((String)"Quita este item de la tienda."))).m_252987_(rightX, ry2, colW, 16).m_253136_());
        } else {
            this.addLabel("\u00a77Selecciona un item de la lista de la derecha", rightX, y + this.bodyH() - 82);
            this.addLabel("\u00a77para editar su precio, moneda, stock y NBT.", rightX, y + this.bodyH() - 70);
        }
    }

    private Button catButton(String label, int x, int y, int w, ResourceKey<CreativeModeTab> key) {
        return Button.m_253074_((Component)Component.m_237113_((String)label), b -> {
            int before = this.offers.size();
            for (Item it : RegistryLists.itemsOfTab(key)) {
                this.addOfferIfNew(it);
            }
            if (this.offers.size() > before) {
                Sfx.select();
            } else {
                Sfx.click();
            }
            this.m_232761_();
        }).m_257505_(Tooltip.m_257550_((Component)Component.m_237113_((String)"Agrega DE GOLPE todos los items de esta categor\u00eda (precio 1 bronce, stock \u221e). Luego ajusta los que quieras."))).m_252987_(x, y, w, 16).m_253136_();
    }

    private void initSettings() {
        this.help = "Nombre de la tienda e icono que se muestra en el primer slot del mercado.";
        int x = this.bodyX();
        int y = this.bodyY();
        int colW = (this.bodyW() - 8) / 2;
        int rightX = x + colW + 8;
        EditBox nameBox = new EditBox(this.f_96547_, x + 70, y, colW - 70, 16, (Component)Component.m_237119_());
        nameBox.m_94199_(48);
        nameBox.m_94144_(this.name);
        nameBox.m_94151_(s -> {
            this.name = s;
        });
        this.m_142416_(nameBox);
        this.addLabel("Nombre:", x, y + 4);
        this.addLabel("\u00a76Icono actual:", x, y + 34);
        this.addLabel("\u00a7eElige el icono \u2192", rightX, y - 2);
        EditBox iconSearch = new EditBox(this.f_96547_, rightX, y + 12, colW, 16, (Component)Component.m_237119_());
        iconSearch.m_257771_((Component)Component.m_237113_((String)"Buscar icono..."));
        this.m_142416_(iconSearch);
        ScrollSelector<Item> iconList = new ScrollSelector<Item>(rightX, y + 32, colW, this.bodyH() - 34, 18, RegistryLists::itemName, it -> RegistryLists.itemName(it) + " " + RegistryLists.itemId(it), ItemStack::new);
        iconList.setItems(RegistryLists.items());
        iconList.onSelect(it -> {
            this.icon = new ItemStack((ItemLike)it);
            Sfx.select();
            this.m_232761_();
        });
        iconSearch.m_94151_(iconList::setQuery);
        this.m_142416_(iconList);
    }

    private void addOffer(Item item) {
        ShopOffer offer = new ShopOffer(new ItemStack((ItemLike)item), 1L, 0, 0);
        offer.setInfinite(true);
        this.offers.add(offer);
        this.selected = offer;
    }

    private void addOfferStack(ItemStack stack) {
        ItemStack copy = stack.m_41777_();
        copy.m_41764_(1);
        ShopOffer offer = new ShopOffer(copy, 1L, 0, 0);
        offer.setInfinite(true);
        this.offers.add(offer);
        this.selected = offer;
    }

    private void addOfferIfNew(Item item) {
        for (ShopOffer o : this.offers) {
            if (o.getItem().m_41720_() != item) continue;
            return;
        }
        ShopOffer offer = new ShopOffer(new ItemStack((ItemLike)item), 1L, 0, 0);
        offer.setInfinite(true);
        this.offers.add(offer);
    }

    private void save() {
        PlayerShop out = new PlayerShop(this.shop.getId(), this.shop.getOwner(), this.shop.getOwnerName(), this.name == null || this.name.isBlank() ? "La Moneda de Oro" : this.name);
        out.setMain(true);
        out.setIcon(this.icon);
        out.getOffers().addAll(this.offers);
        PacketHandler.sendToServer(new SaveMainShopPacket(out));
    }

    private static String coinName(int coin) {
        return switch (coin) {
            case 3 -> CoinEconomy.cashName();
            case 2 -> "Oro";
            case 1 -> "Plata";
            default -> "Bronce";
        };
    }

    private static String coinShort(int coin) {
        return switch (coin) {
            case 3 -> CoinEconomy.cashSymbol();
            case 2 -> "o";
            case 1 -> "p";
            default -> "b";
        };
    }

    private void addLabel(String text, int x, int y) {
        this.labels.add(new Label(text, x, y));
    }

    private void addIntField(int x, int y, int w, int value, IntConsumer setter, String label, int lx, int ly, String tip) {
        EditBox box = new EditBox(this.f_96547_, x, y, w, 16, (Component)Component.m_237119_());
        box.m_94199_(10);
        box.m_94144_(Integer.toString(value));
        box.m_94151_(s -> {
            try {
                setter.accept(Integer.parseInt(s.trim()));
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
        });
        if (tip != null) {
            box.m_257544_(Tooltip.m_257550_((Component)Component.m_237113_((String)tip)));
        }
        this.m_142416_(box);
        if (label != null) {
            this.addLabel(label, lx, ly);
        }
    }

    private void addLongField(int x, int y, int w, long value, LongConsumer setter, String label, int lx, int ly, String tip) {
        EditBox box = new EditBox(this.f_96547_, x, y, w, 16, (Component)Component.m_237119_());
        box.m_94199_(12);
        box.m_94144_(Long.toString(value));
        box.m_94151_(s -> {
            try {
                setter.accept(Long.parseLong(s.trim()));
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
        });
        if (tip != null) {
            box.m_257544_(Tooltip.m_257550_((Component)Component.m_237113_((String)tip)));
        }
        this.m_142416_(box);
        if (label != null) {
            this.addLabel(label, lx, ly);
        }
    }

    public void m_88315_(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.m_280273_(g);
        g.m_280509_(this.leftPos, this.topPos, this.leftPos + this.panelW, this.topPos + this.panelH, -535160294);
        g.m_280509_(this.leftPos, this.topPos, this.leftPos + this.panelW, this.topPos + 18, -14013910);
        g.m_280509_(this.leftPos, this.topPos + this.panelH - 1, this.leftPos + this.panelW, this.topPos + this.panelH, -12961222);
        g.m_280509_(this.leftPos + 6, this.topPos + 40, this.leftPos + this.panelW - 6, this.topPos + 41, -12961222);
        g.m_280056_(this.f_96547_, "\u00a76\u2726 La Moneda de Oro \u00a77- \u00a7f" + this.offers.size() + " items", this.leftPos + 8, this.topPos + 5, 0xFFFFFF, false);
        g.m_280203_(this.icon, this.leftPos + this.panelW - 24, this.topPos + 2);
        if (!this.help.isEmpty()) {
            String trimmed = this.f_96547_.m_92834_("\u00a77" + this.help, this.panelW - 16);
            g.m_280056_(this.f_96547_, trimmed, this.leftPos + 8, this.topPos + 45, 10141936, false);
        }
        if (this.activeTab == Tab.SETTINGS) {
            g.m_280203_(this.icon, this.bodyX() + 70, this.topPos + 88);
        }
        super.m_88315_(g, mouseX, mouseY, partial);
        for (Label l : this.labels) {
            g.m_280056_(this.f_96547_, l.text(), l.x(), l.y(), 0xE0E0E0, false);
        }
    }

    public boolean m_7043_() {
        return false;
    }

    private static enum Tab {
        ITEMS("Items"),
        SETTINGS("Ajustes");

        final String label;

        private Tab(String label) {
            this.label = label;
        }
    }

    private record Label(String text, int x, int y) {
    }
}

