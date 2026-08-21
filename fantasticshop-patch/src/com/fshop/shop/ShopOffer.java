/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.nbt.Tag
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.world.item.ItemStack
 *  net.minecraftforge.registries.ForgeRegistries
 */
package com.fshop.shop;

import com.fshop.economy.CoinEconomy;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class ShopOffer {
    private ItemStack item;
    private long unitPrice;
    private int coin;
    private int stock;
    private boolean infinite;
    private int bundle = 1;
    private static final String[] IDENTITY_KEYS = new String[]{"Enchantments", "StoredEnchantments", "display", "Potion", "CustomPotionEffects", "CustomModelData", "Trim", "BlockEntityTag", "BlockStateTag", "Unbreakable", "AttributeModifiers", "EntityTag", "pages", "author", "title", "generation"};

    public ShopOffer(ItemStack item, long unitPrice, int coin, int stock) {
        this.item = item.m_41777_();
        this.item.m_41764_(1);
        this.unitPrice = Math.max(0L, unitPrice);
        this.coin = CoinEconomy.sanitize(coin);
        this.stock = Math.max(0, stock);
    }

    public boolean isInfinite() {
        return this.infinite;
    }

    public void setInfinite(boolean infinite) {
        this.infinite = infinite;
    }

    public int getBundle() {
        return this.bundle;
    }

    public void setBundle(int bundle) {
        this.bundle = Math.max(1, bundle);
    }

    public boolean hasStock(int amount) {
        return this.infinite || this.stock >= amount;
    }

    public ItemStack getItem() {
        return this.item;
    }

    public ItemStack displayStack(int count) {
        ItemStack s = this.item.m_41777_();
        s.m_41764_(Math.max(1, Math.min(count, this.item.m_41741_())));
        return s;
    }

    public static boolean matchesForMerge(ItemStack a, ItemStack b) {
        if (a.m_41619_() || b.m_41619_() || !ItemStack.m_41656_((ItemStack)a, (ItemStack)b)) {
            return false;
        }
        if (a.m_41773_() != b.m_41773_()) {
            return false;
        }
        return ShopOffer.identityTag(a).equals(ShopOffer.identityTag(b));
    }

    private static CompoundTag identityTag(ItemStack stack) {
        CompoundTag out = new CompoundTag();
        CompoundTag tag = stack.m_41783_();
        if (tag == null) {
            return out;
        }
        for (String key : IDENTITY_KEYS) {
            if (!tag.m_128441_(key)) continue;
            out.m_128365_(key, tag.m_128423_(key).m_6426_());
        }
        return out;
    }

    public static void mergeDuplicates(List<ShopOffer> offers) {
        for (int i = 0; i < offers.size(); ++i) {
            ShopOffer keep = offers.get(i);
            for (int j = offers.size() - 1; j > i; --j) {
                ShopOffer dup = offers.get(j);
                if (!ShopOffer.matchesForMerge(keep.getItem(), dup.getItem())) continue;
                if (!keep.isInfinite()) {
                    keep.addStock(dup.getStock());
                }
                offers.remove(j);
            }
        }
    }

    public static boolean isPristineVanilla(ItemStack stack) {
        if (stack.m_41619_()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.m_41720_());
        if (id == null || !"minecraft".equals(id.m_135827_())) {
            return false;
        }
        if (stack.m_41793_() || stack.m_41768_()) {
            return false;
        }
        CompoundTag tag = stack.m_41783_();
        return tag == null || tag.m_128456_();
    }

    public static int bundleCap(ItemStack stack) {
        if (stack.m_41741_() <= 1 && !ShopOffer.isPristineVanilla(stack)) {
            return 1;
        }
        return 9999;
    }

    public static int fullStack(ItemStack stack) {
        int vanillaMax = stack.m_41741_();
        if (vanillaMax > 1) {
            return vanillaMax;
        }
        return ShopOffer.isPristineVanilla(stack) ? 64 : 1;
    }

    public long getUnitPrice() {
        return this.unitPrice;
    }

    public void setUnitPrice(long unitPrice) {
        this.unitPrice = Math.max(0L, unitPrice);
    }

    public int getCoin() {
        return this.coin;
    }

    public void setCoin(int coin) {
        this.coin = CoinEconomy.sanitize(coin);
    }

    public int getStock() {
        return this.stock;
    }

    public void setStock(int stock) {
        this.stock = Math.max(0, stock);
    }

    public void addStock(int amount) {
        this.stock = Math.max(0, this.stock + amount);
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.m_128365_("item", (Tag)this.item.m_41739_(new CompoundTag()));
        tag.m_128356_("price", this.unitPrice);
        tag.m_128405_("coin", this.coin);
        tag.m_128405_("stock", this.stock);
        tag.m_128379_("inf", this.infinite);
        tag.m_128405_("bundle", this.bundle);
        return tag;
    }

    public static ShopOffer fromNbt(CompoundTag tag) {
        ShopOffer offer = new ShopOffer(ItemStack.m_41712_((CompoundTag)tag.m_128469_("item")), tag.m_128454_("price"), tag.m_128451_("coin"), tag.m_128451_("stock"));
        offer.infinite = tag.m_128471_("inf");
        offer.bundle = tag.m_128441_("bundle") ? Math.max(1, tag.m_128451_("bundle")) : 1;
        return offer;
    }

    public void toBuf(FriendlyByteBuf buf) {
        buf.m_130055_(this.item);
        buf.m_130103_(this.unitPrice);
        buf.m_130130_(this.coin);
        buf.m_130130_(this.stock);
        buf.writeBoolean(this.infinite);
        buf.m_130130_(this.bundle);
    }

    public static ShopOffer fromBuf(FriendlyByteBuf buf) {
        ShopOffer offer = new ShopOffer(buf.m_130267_(), buf.m_130258_(), buf.m_130242_(), buf.m_130242_());
        offer.infinite = buf.readBoolean();
        offer.bundle = Math.max(1, buf.m_130242_());
        return offer;
    }
}

