/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.network.chat.Component
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.Container
 *  net.minecraft.world.MenuProvider
 *  net.minecraft.world.SimpleContainer
 *  net.minecraft.world.entity.player.Inventory
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.inventory.AbstractContainerMenu
 *  net.minecraft.world.inventory.ChestMenu
 *  net.minecraft.world.inventory.ClickType
 *  net.minecraft.world.inventory.MenuType
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.Items
 *  net.minecraft.world.level.ItemLike
 *  net.minecraftforge.network.NetworkHooks
 */
package com.claimblocks.gui;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.gui.ClaimMenuHandler;
import com.claimblocks.util.PlayerLookup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.network.NetworkHooks;

public class MemberSelectMenu
extends ChestMenu {
    private static final int SIZE = 54;
    private static final int ENTRIES_PER_PAGE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BY_NAME = 48;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_INFO = 50;
    private static final int SLOT_NEXT = 53;
    private final SimpleContainer chest;
    private final Claim claim;
    private final ServerPlayer viewer;
    private final int returnPage;
    private final int page;
    private List<ServerPlayer> candidates;

    public MemberSelectMenu(int syncId, Inventory pInv, Claim claim, int returnPage, int page) {
        this(syncId, pInv, new SimpleContainer(54), claim, returnPage, page);
    }

    private MemberSelectMenu(int syncId, Inventory pInv, SimpleContainer chest, Claim claim, int returnPage, int page) {
        super(MenuType.f_39962_, syncId, pInv, (Container)chest, 6);
        this.chest = chest;
        this.claim = claim;
        this.viewer = (ServerPlayer)pInv.f_35978_;
        this.returnPage = returnPage;
        this.page = page;
        this.rebuild();
    }

    public boolean m_6875_(Player player) {
        return true;
    }

    public ItemStack m_7648_(Player player, int index) {
        return ItemStack.f_41583_;
    }

    private List<ServerPlayer> collectCandidates() {
        ArrayList<ServerPlayer> out = new ArrayList<ServerPlayer>();
        if (this.viewer.m_20194_() == null) {
            return out;
        }
        for (ServerPlayer p2 : this.viewer.m_20194_().m_6846_().m_11314_()) {
            if (this.claim.isOwner(p2.m_20148_()) || this.claim.isMember(p2.m_20148_())) continue;
            out.add(p2);
        }
        out.sort(Comparator.comparing(p -> p.m_7755_().getString().toLowerCase()));
        return out;
    }

    private void rebuild() {
        this.candidates = this.collectCandidates();
        ItemStack bg = MemberSelectMenu.withName(new ItemStack((ItemLike)Items.f_42183_), (Component)Component.m_237113_((String)" "));
        for (int i = 0; i < 54; ++i) {
            this.chest.m_6836_(i, bg.m_41777_());
        }
        int start = this.page * 45;
        int end = Math.min(start + 45, this.candidates.size());
        if (this.candidates.isEmpty()) {
            this.chest.m_6836_(22, MemberSelectMenu.withLore(MemberSelectMenu.withName(new ItemStack((ItemLike)Items.f_42127_), (Component)Component.m_237113_((String)"No hay jugadores disponibles").m_130940_(ChatFormatting.RED)), List.of(Component.m_237113_((String)"Todos los conectados ya son miembros,").m_130940_(ChatFormatting.GRAY), Component.m_237113_((String)"o eres el \u00fanico en el servidor.").m_130940_(ChatFormatting.GRAY), Component.m_237113_((String)"Usa \"Escribir nombre\" para alguien offline.").m_130940_(ChatFormatting.YELLOW))));
        } else {
            for (int i = start; i < end; ++i) {
                this.chest.m_6836_(i - start, MemberSelectMenu.playerHead(this.candidates.get(i)));
            }
        }
        if (this.page > 0) {
            this.chest.m_6836_(45, MemberSelectMenu.withName(new ItemStack((ItemLike)Items.f_42412_), (Component)Component.m_237113_((String)"<< P\u00e1gina anterior").m_130940_(ChatFormatting.AQUA)));
        }
        this.chest.m_6836_(48, MemberSelectMenu.withLore(MemberSelectMenu.withName(new ItemStack((ItemLike)Items.f_42656_), (Component)Component.m_237113_((String)"Escribir nombre").m_130944_(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD})), List.of(Component.m_237113_((String)"Para a\u00f1adir a alguien que NO est\u00e1 conectado.").m_130940_(ChatFormatting.GRAY), Component.m_237113_((String)"Se pide el nombre por chat.").m_130940_(ChatFormatting.GRAY))));
        this.chest.m_6836_(49, MemberSelectMenu.withName(new ItemStack((ItemLike)Items.f_42412_), (Component)Component.m_237113_((String)"Volver al men\u00fa de la zona").m_130940_(ChatFormatting.AQUA)));
        this.chest.m_6836_(50, MemberSelectMenu.withLore(MemberSelectMenu.withName(new ItemStack((ItemLike)Items.f_42516_), (Component)Component.m_237113_((String)("Miembros: " + this.claim.getMembers().size())).m_130944_(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD})), List.of(Component.m_237113_((String)"Clic en una cabeza para a\u00f1adir a ese jugador.").m_130940_(ChatFormatting.GRAY))));
        if (end < this.candidates.size()) {
            this.chest.m_6836_(53, MemberSelectMenu.withName(new ItemStack((ItemLike)Items.f_42412_), (Component)Component.m_237113_((String)"P\u00e1gina siguiente >>").m_130940_(ChatFormatting.AQUA)));
        }
        this.m_38946_();
    }

    private static ItemStack playerHead(ServerPlayer player) {
        String name = player.m_7755_().getString();
        ItemStack head = new ItemStack((ItemLike)Items.f_42680_);
        head.m_41784_().m_128359_("SkullOwner", name);
        return MemberSelectMenu.withLore(MemberSelectMenu.withName(head, (Component)Component.m_237113_((String)name).m_130944_(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD})), List.of(Component.m_237113_((String)"Clic para a\u00f1adirlo como miembro").m_130940_(ChatFormatting.GRAY)));
    }

    public void m_150399_(int slot, int button, ClickType clickType, Player player) {
        if (slot < 0 || slot >= 54) {
            return;
        }
        if (slot == 49) {
            ClaimMenuHandler.open(this.viewer, this.claim, this.returnPage);
            return;
        }
        if (slot == 45 && this.page > 0) {
            MemberSelectMenu.open(this.viewer, this.claim, this.returnPage, this.page - 1);
            return;
        }
        if (slot == 53) {
            int max = Math.max(0, (this.candidates.size() - 1) / 45);
            if (this.page < max) {
                MemberSelectMenu.open(this.viewer, this.claim, this.returnPage, this.page + 1);
            }
            return;
        }
        if (slot == 48) {
            ClaimMenuHandler.requestAddMember(this.viewer, this.claim, this.returnPage);
            this.viewer.m_6915_();
            return;
        }
        if (slot >= 0 && slot < 45) {
            int idx = this.page * 45 + slot;
            if (idx >= this.candidates.size()) {
                return;
            }
            if (ClaimManager.getInstance().findClaimById(this.claim.getClaimId()) == null) {
                this.viewer.m_5661_((Component)Component.m_237113_((String)"[x] La zona ya no existe.").m_130940_(ChatFormatting.RED), false);
                this.viewer.m_6915_();
                return;
            }
            ServerPlayer snapshot = this.candidates.get(idx);
            PlayerLookup.Resolved target = PlayerLookup.resolve(this.viewer.m_20194_(), snapshot.m_7755_().getString());
            if (target == null) {
                target = new PlayerLookup.Resolved(snapshot.m_20148_(), snapshot.m_7755_().getString(), null);
            }
            ClaimMenuHandler.addMemberResolved(this.viewer, this.claim, target);
            this.rebuild();
        }
    }

    private static ItemStack withName(ItemStack stack, Component name) {
        stack.m_41714_(name);
        return stack;
    }

    private static ItemStack withLore(ItemStack stack, List<Component> lore) {
        ClaimBlocks.setLore(stack, lore);
        return stack;
    }

    public static void open(ServerPlayer player, final Claim claim, final int returnPage, int page) {
        final int p = Math.max(0, page);
        NetworkHooks.openScreen((ServerPlayer)player, (MenuProvider)new MenuProvider(){

            public Component m_5446_() {
                return Component.m_237113_((String)"A\u00f1adir miembro").m_130944_(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD});
            }

            public AbstractContainerMenu m_7208_(int id, Inventory inv, Player pl) {
                return new MemberSelectMenu(id, inv, claim, returnPage, p);
            }
        });
    }
}

