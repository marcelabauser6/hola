/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.core.BlockPos
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.server.level.ServerLevel
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
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.levelgen.Heightmap$Types
 *  net.minecraftforge.network.NetworkHooks
 */
package com.claimblocks.gui;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.gui.AdminPanelHandler;
import com.claimblocks.gui.ClaimMenuHandler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.network.NetworkHooks;

public class AdminClaimSubMenuHandler
extends ChestMenu {
    private static final Map<UUID, UUID> pendingTransfers = new ConcurrentHashMap<UUID, UUID>();
    private final SimpleContainer inv;
    private final ServerPlayer viewer;
    private final UUID claimId;
    private boolean awaitingDeleteConfirm = false;

    public AdminClaimSubMenuHandler(int syncId, Inventory pInv, UUID claimId) {
        this(syncId, pInv, new SimpleContainer(54), claimId);
    }

    private AdminClaimSubMenuHandler(int syncId, Inventory pInv, SimpleContainer inv, UUID claimId) {
        super(MenuType.f_39962_, syncId, pInv, (Container)inv, 6);
        this.inv = inv;
        this.viewer = (ServerPlayer)pInv.f_35978_;
        this.claimId = claimId;
        this.rebuild();
    }

    public boolean m_6875_(Player player) {
        return true;
    }

    public ItemStack m_7648_(Player player, int index) {
        return ItemStack.f_41583_;
    }

    private Claim claim() {
        return AdminPanelHandler.findClaim(this.claimId);
    }

    private void rebuild() {
        ItemStack bg = AdminClaimSubMenuHandler.withName(new ItemStack((ItemLike)Items.f_42191_), (Component)Component.m_237113_((String)" "));
        for (int i = 0; i < 54; ++i) {
            this.inv.m_6836_(i, bg.m_41777_());
        }
        Claim c = this.claim();
        if (c != null) {
            String owner = c.getOwnerName();
            this.inv.m_6836_(11, AdminClaimSubMenuHandler.withLore(AdminClaimSubMenuHandler.withName(new ItemStack((ItemLike)Items.f_42584_), (Component)Component.m_237113_((String)"Teleportar al claim").m_130944_(new ChatFormatting[]{ChatFormatting.AQUA, ChatFormatting.BOLD})), List.of(Component.m_237113_((String)("Te lleva al centro del claim de " + owner)).m_130940_(ChatFormatting.GRAY))));
            this.inv.m_6836_(12, AdminClaimSubMenuHandler.withLore(AdminClaimSubMenuHandler.withName(new ItemStack((ItemLike)Items.f_42351_), (Component)Component.m_237113_((String)"Ver y editar flags").m_130944_(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD})), List.of(Component.m_237113_((String)"Abre el men\u00fa de flags de este claim").m_130940_(ChatFormatting.GRAY))));
            if (this.awaitingDeleteConfirm) {
                this.inv.m_6836_(13, AdminClaimSubMenuHandler.withLore(AdminClaimSubMenuHandler.withName(new ItemStack((ItemLike)Items.f_41996_), (Component)Component.m_237113_((String)"\u00bfConfirmar eliminaci\u00f3n?").m_130944_(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})), List.of(Component.m_237113_((String)("Esto eliminar\u00e1 la zona de " + owner)).m_130940_(ChatFormatting.YELLOW), Component.m_237113_((String)"El bloque NO se devuelve al due\u00f1o").m_130940_(ChatFormatting.RED), Component.m_237113_((String)"Clic de nuevo para confirmar").m_130940_(ChatFormatting.GRAY))));
            } else {
                this.inv.m_6836_(13, AdminClaimSubMenuHandler.withLore(AdminClaimSubMenuHandler.withName(new ItemStack((ItemLike)Items.f_42127_), (Component)Component.m_237113_((String)"Eliminar este claim").m_130944_(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})), List.of(Component.m_237113_((String)("Elimina la zona de " + owner)).m_130940_(ChatFormatting.YELLOW), Component.m_237113_((String)"Clic para pedir confirmaci\u00f3n").m_130940_(ChatFormatting.GRAY))));
            }
            this.inv.m_6836_(15, AdminClaimSubMenuHandler.withLore(AdminClaimSubMenuHandler.withName(new ItemStack((ItemLike)Items.f_42516_), (Component)Component.m_237113_((String)"Transferir claim").m_130944_(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD})), List.of(Component.m_237113_((String)"Cambia el due\u00f1o de esta zona").m_130940_(ChatFormatting.GRAY))));
            this.inv.m_6836_(22, AdminClaimSubMenuHandler.withName(new ItemStack((ItemLike)Items.f_42412_), (Component)Component.m_237113_((String)"Volver al panel").m_130940_(ChatFormatting.AQUA)));
            this.m_38946_();
        }
    }

    public void m_150399_(int slot, int button, ClickType clickType, Player player) {
        if (slot >= 0 && slot < 54) {
            Claim c = this.claim();
            if (c == null) {
                this.viewer.m_6915_();
            } else {
                if (slot != 13 && this.awaitingDeleteConfirm) {
                    this.awaitingDeleteConfirm = false;
                }
                if (slot == 22) {
                    AdminPanelHandler.open(this.viewer, 0);
                } else if (slot == 11) {
                    this.teleportToClaim(c);
                } else if (slot == 12) {
                    String title = "[Admin] Flags de " + c.getOwnerName() + " - " + c.sizeLabel();
                    ClaimMenuHandler.open(this.viewer, c, 0, title);
                } else if (slot == 13) {
                    if (!this.awaitingDeleteConfirm) {
                        this.awaitingDeleteConfirm = true;
                        this.rebuild();
                    } else {
                        this.adminDelete(c);
                    }
                } else if (slot == 15) {
                    this.startTransfer(c);
                }
            }
        }
    }

    private void teleportToClaim(Claim c) {
        ServerLevel world = null;
        for (ServerLevel w : this.viewer.f_8924_.m_129785_()) {
            if (!w.m_46472_().m_135782_().toString().equals(c.getWorld())) continue;
            world = w;
            break;
        }
        if (world == null) {
            this.viewer.m_5661_((Component)Component.m_237113_((String)"[x] No se pudo encontrar la dimensi\u00f3n.").m_130940_(ChatFormatting.RED), false);
            this.viewer.m_6915_();
        } else {
            int topY = world.m_6924_(Heightmap.Types.MOTION_BLOCKING, c.getX(), c.getZ());
            this.viewer.m_8999_(world, (double)c.getX() + 0.5, (double)topY, (double)c.getZ() + 0.5, this.viewer.m_146908_(), this.viewer.m_146909_());
            this.viewer.m_5661_((Component)Component.m_237113_((String)("\u2714 Teletransportado a la zona de " + c.getOwnerName() + ".")).m_130940_(ChatFormatting.GREEN), false);
            this.viewer.m_6915_();
        }
    }

    private void adminDelete(Claim c) {
        String ownerName = c.getOwnerName();
        UUID ownerId = c.getOwnerUUID();
        ServerLevel world = null;
        for (ServerLevel w : this.viewer.f_8924_.m_129785_()) {
            if (!w.m_46472_().m_135782_().toString().equals(c.getWorld())) continue;
            world = w;
            break;
        }
        BlockPos pos = c.getCenter();
        if (world != null && ClaimBlocks.isClaimConcreteForTier(world.m_8055_(pos).m_60734_(), c.getTier())) {
            world.m_7731_(pos, Blocks.f_50016_.m_49966_(), 3);
        }
        ClaimManager.getInstance().removeClaim((Level)world, c.getCenter());
        this.viewer.m_5661_((Component)Component.m_237113_((String)("\u2714 Zona de " + ownerName + " eliminada por admin.")).m_130944_(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD}), false);
        ServerPlayer owner = this.viewer.f_8924_.m_6846_().m_11259_(ownerId);
        MutableComponent msg = Component.m_237113_((String)"[!] Un administrador elimin\u00f3 tu zona ").m_130940_(ChatFormatting.YELLOW).m_7220_((Component)Component.m_237113_((String)c.sizeLabel()).m_130944_(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD})).m_7220_((Component)Component.m_237113_((String)(" en X:" + c.getX() + " Z:" + c.getZ())).m_130940_(ChatFormatting.YELLOW));
        if (owner != null) {
            owner.m_5661_((Component)msg, false);
        } else {
            ClaimManager.getInstance().queueMessage(ownerId, (Component)msg);
        }
        this.viewer.m_6915_();
    }

    private void startTransfer(Claim c) {
        pendingTransfers.put(this.viewer.m_20148_(), c.getClaimId());
        this.viewer.m_5661_((Component)Component.m_237113_((String)"[i] Escribe el nombre del nuevo due\u00f1o en el chat.").m_130940_(ChatFormatting.AQUA), false);
        this.viewer.m_5661_((Component)Component.m_237113_((String)"    Escribe 'cancelar' para abortar.").m_130940_(ChatFormatting.GRAY), false);
        this.viewer.m_6915_();
    }

    public static UUID popPendingTransfer(UUID opId) {
        return pendingTransfers.remove(opId);
    }

    public static boolean hasPendingTransfer(UUID opId) {
        return pendingTransfers.containsKey(opId);
    }

    public static void clearPendingTransfer(UUID opId) {
        if (opId != null) {
            pendingTransfers.remove(opId);
        }
    }

    private static ItemStack withName(ItemStack s, Component t) {
        s.m_41714_(t);
        return s;
    }

    private static ItemStack withLore(ItemStack s, List<Component> lore) {
        ClaimBlocks.setLore(s, lore);
        return s;
    }

    public static void open(ServerPlayer player, final UUID claimId) {
        Claim c = AdminPanelHandler.findClaim(claimId);
        if (c == null) {
            player.m_5661_((Component)Component.m_237113_((String)"[x] La zona ya no existe.").m_130940_(ChatFormatting.RED), false);
        } else {
            String title = "Admin - " + c.getOwnerName() + " " + c.sizeLabel();
            if (title.length() > 40) {
                title = title.substring(0, 37) + "...";
            }
            final String t = title;
            NetworkHooks.openScreen((ServerPlayer)player, (MenuProvider)new MenuProvider(){

                public Component m_5446_() {
                    return Component.m_237113_((String)t).m_130944_(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD});
                }

                public AbstractContainerMenu m_7208_(int id, Inventory inv, Player pl) {
                    return new AdminClaimSubMenuHandler(id, inv, claimId);
                }
            });
        }
    }
}

