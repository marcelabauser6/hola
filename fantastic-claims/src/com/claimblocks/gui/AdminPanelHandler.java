/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
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
 *  net.minecraft.world.level.block.Block
 *  net.minecraftforge.network.NetworkHooks
 */
package com.claimblocks.gui;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.data.ClaimTier;
import com.claimblocks.gui.AdminClaimSubMenuHandler;
import com.claimblocks.gui.AdminGlobalFlagsHandler;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkHooks;

public class AdminPanelHandler
extends ChestMenu {
    private static final int CLAIMS_PER_PAGE = 45;
    private final SimpleContainer inv;
    private final ServerPlayer viewer;
    private final int page;
    private final List<Claim> claims;

    public AdminPanelHandler(int syncId, Inventory pInv, int page) {
        this(syncId, pInv, new SimpleContainer(54), page);
    }

    private AdminPanelHandler(int syncId, Inventory pInv, SimpleContainer inv, int page) {
        super(MenuType.f_39962_, syncId, pInv, (Container)inv, 6);
        this.inv = inv;
        this.viewer = (ServerPlayer)pInv.f_35978_;
        this.page = page;
        this.claims = ClaimManager.getInstance().getAllClaims();
        this.rebuild();
    }

    public boolean m_6875_(Player player) {
        return true;
    }

    public ItemStack m_7648_(Player player, int index) {
        return ItemStack.f_41583_;
    }

    private void rebuild() {
        ItemStack bg = AdminPanelHandler.withName(new ItemStack((ItemLike)Items.f_42183_), (Component)Component.m_237113_((String)" "));
        for (int i = 0; i < 54; ++i) {
            this.inv.m_6836_(i, bg.m_41777_());
        }
        int start = this.page * 45;
        int end = Math.min(start + 45, this.claims.size());
        for (int i = start; i < end; ++i) {
            this.inv.m_6836_(i - start, AdminPanelHandler.claimItem(this.claims.get(i)));
        }
        if (this.page > 0) {
            this.inv.m_6836_(45, AdminPanelHandler.withName(new ItemStack((ItemLike)Items.f_42412_), (Component)Component.m_237113_((String)"<< P\u00e1gina anterior").m_130940_(ChatFormatting.AQUA)));
        }
        this.inv.m_6836_(46, AdminPanelHandler.withLore(AdminPanelHandler.withName(new ItemStack((ItemLike)Items.f_42517_), (Component)Component.m_237113_((String)"Estad\u00edsticas").m_130944_(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD})), List.of(Component.m_237113_((String)"Resumen del servidor").m_130940_(ChatFormatting.GRAY))));
        this.inv.m_6836_(47, AdminPanelHandler.withLore(AdminPanelHandler.withName(new ItemStack((ItemLike)Items.f_42351_), (Component)Component.m_237113_((String)"Flags Globales").m_130944_(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD})), List.of(Component.m_237113_((String)"PVP / Mob griefing / Fire").m_130940_(ChatFormatting.GRAY))));
        boolean bypassing = ClaimManager.getInstance().isBypassing(this.viewer.m_20148_());
        this.inv.m_6836_(48, AdminPanelHandler.withLore(AdminPanelHandler.withName(new ItemStack((ItemLike)Items.f_42545_), (Component)Component.m_237113_((String)("Modo Bypass: " + (bypassing ? "ON" : "OFF"))).m_130944_(new ChatFormatting[]{bypassing ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD})), List.of(Component.m_237113_((String)"Ignorar protecciones de zonas").m_130940_(ChatFormatting.GRAY))));
        this.inv.m_6836_(49, AdminPanelHandler.withName(new ItemStack((ItemLike)Items.f_42127_), (Component)Component.m_237113_((String)"Cerrar panel").m_130940_(ChatFormatting.WHITE)));
        if (end < this.claims.size()) {
            this.inv.m_6836_(53, AdminPanelHandler.withName(new ItemStack((ItemLike)Items.f_42412_), (Component)Component.m_237113_((String)"P\u00e1gina siguiente >>").m_130940_(ChatFormatting.AQUA)));
        }
        this.m_38946_();
    }

    private static ItemStack claimItem(Claim c) {
        ClaimTier tier = c.getTier();
        Block block = tier != null ? ClaimBlocks.blockForTier(tier) : null;
        ItemStack stack = block != null ? new ItemStack((ItemLike)block.m_5456_()) : new ItemStack((ItemLike)Items.f_42516_);
        MutableComponent name = Component.m_237113_((String)(c.getOwnerName() + " - " + c.sizeLabel())).m_130944_(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD});
        return AdminPanelHandler.withLore(AdminPanelHandler.withName(stack, (Component)name), List.of(Component.m_237113_((String)("Posici\u00f3n: X:" + c.getX() + " Z:" + c.getZ())).m_130940_(ChatFormatting.GRAY), Component.m_237113_((String)("Dimensi\u00f3n: " + c.getWorld())).m_130940_(ChatFormatting.DARK_AQUA), Component.m_237113_((String)"Clic para gestionar este claim").m_130940_(ChatFormatting.YELLOW)));
    }

    static ItemStack withName(ItemStack s, Component t) {
        s.m_41714_(t);
        return s;
    }

    static ItemStack withLore(ItemStack s, List<Component> lore) {
        ClaimBlocks.setLore(s, lore);
        return s;
    }

    public void m_150399_(int slot, int button, ClickType clickType, Player player) {
        if (slot >= 0 && slot < 54) {
            if (slot == 45 && this.page > 0) {
                AdminPanelHandler.open(this.viewer, this.page - 1);
            } else if (slot == 53) {
                int max = (this.claims.size() - 1) / 45;
                if (this.page < max) {
                    AdminPanelHandler.open(this.viewer, this.page + 1);
                }
            } else if (slot == 49) {
                this.viewer.m_6915_();
            } else if (slot == 46) {
                this.viewer.m_6915_();
                this.viewer.f_8924_.m_129892_().m_230957_(this.viewer.m_20203_(), "fsclaimadmin stats");
            } else if (slot == 47) {
                AdminGlobalFlagsHandler.open(this.viewer);
            } else if (slot == 48) {
                ClaimManager.getInstance().toggleBypass(this.viewer.m_20148_());
                this.rebuild();
            } else {
                int idx = this.page * 45 + slot;
                if (idx < this.claims.size()) {
                    AdminClaimSubMenuHandler.open(this.viewer, this.claims.get(idx).getClaimId());
                }
            }
        }
    }

    public static void open(ServerPlayer player, int page) {
        final int p = Math.max(0, page);
        NetworkHooks.openScreen((ServerPlayer)player, (MenuProvider)new MenuProvider(){

            public Component m_5446_() {
                return Component.m_237113_((String)"Panel de Administraci\u00f3n").m_130944_(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD});
            }

            public AbstractContainerMenu m_7208_(int id, Inventory inv, Player pl) {
                return new AdminPanelHandler(id, inv, p);
            }
        });
    }

    public static Claim findClaim(UUID id) {
        for (Claim c : ClaimManager.getInstance().getAllClaims()) {
            if (!c.getClaimId().equals(id)) continue;
            return c;
        }
        return null;
    }
}

