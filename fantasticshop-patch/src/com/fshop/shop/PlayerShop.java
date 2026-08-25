/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.nbt.ListTag
 *  net.minecraft.nbt.Tag
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraft.world.item.ItemStack
 */
package com.fshop.shop;

import com.fshop.shop.ShopOffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public final class PlayerShop {
    private final UUID id;
    private UUID owner;
    private String ownerName;
    private String name;
    private final List<ShopOffer> offers = new ArrayList<ShopOffer>();
    private final long[] pendingEarnings = new long[4];
    /** Bank account this shop settles into. 0 means the shop cannot sell. */
    private int accountNumber;
    private boolean main;
    private ItemStack icon = ItemStack.f_41583_;

    public PlayerShop(UUID id, UUID owner, String ownerName, String name) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName == null ? "" : ownerName;
        this.name = name == null ? "" : name;
    }

    public UUID getId() {
        return this.id;
    }

    public UUID getOwner() {
        return this.owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public String getOwnerName() {
        return this.ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName == null ? "" : ownerName;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public List<ShopOffer> getOffers() {
        return this.offers;
    }

    public boolean isMain() {
        return this.main;
    }

    public void setMain(boolean main) {
        this.main = main;
    }

    public ItemStack getIcon() {
        return this.icon;
    }

    public void setIcon(ItemStack icon) {
        ItemStack itemStack = this.icon = icon == null ? ItemStack.f_41583_ : icon.m_41777_();
        if (!this.icon.m_41619_()) {
            this.icon.m_41764_(1);
        }
    }

    public int getAccountNumber() {
        return this.accountNumber;
    }

    public void setAccountNumber(int number) {
        this.accountNumber = Math.max(0, number);
    }

    /** A shop with no account is frozen: it can be browsed but nothing can be bought or listed. */
    public boolean canSell() {
        return this.accountNumber > 0;
    }

    public long getPendingEarnings(int coin) {
        return this.pendingEarnings[PlayerShop.slot(coin)];
    }

    public long totalPendingEarnings() {
        long total = 0L;
        for (long amount : this.pendingEarnings) {
            total += amount;
        }
        return total;
    }

    public void addEarnings(int coin, long amount) {
        int n = PlayerShop.slot(coin);
        this.pendingEarnings[n] = this.pendingEarnings[n] + Math.max(0L, amount);
    }

    /**
     * Removes and returns one currency's pending earnings.
     *
     * <p>Payout used to be "deposit everything, then {@code clearEarnings()}", which threw away any
     * amount the deposit had not actually delivered - and since the cash deposit can fail (the
     * seller's account may have been closed since the sale) that silently destroyed money. Taking one
     * currency at a time lets a caller clear only what it managed to pay out.</p>
     */
    public long takeEarnings(int coin) {
        int n = PlayerShop.slot(coin);
        long amount = this.pendingEarnings[n];
        this.pendingEarnings[n] = 0L;
        return amount;
    }

    /** Puts an amount back after a failed payout. */
    public void restoreEarnings(int coin, long amount) {
        int n = PlayerShop.slot(coin);
        this.pendingEarnings[n] = this.pendingEarnings[n] + Math.max(0L, amount);
    }

    public void clearEarnings() {
        java.util.Arrays.fill(this.pendingEarnings, 0L);
    }

    /** Clamps a currency id onto the earnings array, whose length is the number of currencies. */
    private static int slot(int coin) {
        return Math.max(0, Math.min(4 - 1, coin));
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.m_128362_("id", this.id);
        tag.m_128362_("owner", this.owner);
        tag.m_128359_("ownerName", this.ownerName);
        tag.m_128359_("name", this.name);
        tag.m_128388_("earnings3", new long[]{this.pendingEarnings[0], this.pendingEarnings[1], this.pendingEarnings[2]});
        tag.m_128356_("earningsCash", this.pendingEarnings[3]);
        tag.m_128405_("account", this.accountNumber);
        tag.m_128379_("main", this.main);
        if (!this.icon.m_41619_()) {
            tag.m_128365_("icon", (Tag)this.icon.m_41739_(new CompoundTag()));
        }
        ListTag list = new ListTag();
        for (ShopOffer offer : this.offers) {
            list.add(offer.toNbt());
        }
        tag.m_128365_("offers", (Tag)list);
        return tag;
    }

    public static PlayerShop fromNbt(CompoundTag tag) {
        PlayerShop shop = new PlayerShop(tag.m_128342_("id"), tag.m_128342_("owner"), tag.m_128461_("ownerName"), tag.m_128461_("name"));
        shop.pendingEarnings[3] = tag.m_128454_("earningsCash");
        shop.accountNumber = tag.m_128451_("account");
        long[] e = tag.m_128467_("earnings3");
        for (int i = 0; i < 3 && i < e.length; ++i) {
            shop.pendingEarnings[i] = e[i];
        }
        shop.main = tag.m_128471_("main");
        if (tag.m_128441_("icon")) {
            shop.icon = ItemStack.m_41712_((CompoundTag)tag.m_128469_("icon"));
        }
        ListTag list = tag.m_128437_("offers", 10);
        for (int i = 0; i < list.size(); ++i) {
            shop.offers.add(ShopOffer.fromNbt(list.m_128728_(i)));
        }
        return shop;
    }

    public void toBuf(FriendlyByteBuf buf) {
        buf.m_130077_(this.id);
        buf.m_130077_(this.owner);
        buf.m_130070_(this.ownerName);
        buf.m_130070_(this.name);
        buf.m_130103_(this.pendingEarnings[0]);
        buf.m_130103_(this.pendingEarnings[1]);
        buf.m_130103_(this.pendingEarnings[2]);
        buf.m_130103_(this.pendingEarnings[3]);
        buf.m_130130_(this.accountNumber);
        buf.writeBoolean(this.main);
        buf.m_130055_(this.icon);
        buf.m_130130_(this.offers.size());
        for (ShopOffer offer : this.offers) {
            offer.toBuf(buf);
        }
    }

    public static PlayerShop fromBuf(FriendlyByteBuf buf) {
        PlayerShop shop = new PlayerShop(buf.m_130259_(), buf.m_130259_(), buf.m_130277_(), buf.m_130277_());
        shop.pendingEarnings[0] = buf.m_130258_();
        shop.pendingEarnings[1] = buf.m_130258_();
        shop.pendingEarnings[2] = buf.m_130258_();
        shop.pendingEarnings[3] = buf.m_130258_();
        shop.accountNumber = buf.m_130242_();
        shop.main = buf.readBoolean();
        shop.icon = buf.m_130267_();
        int n = buf.m_130242_();
        for (int i = 0; i < n; ++i) {
            shop.offers.add(ShopOffer.fromBuf(buf));
        }
        return shop;
    }
}

