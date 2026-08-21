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
 *  net.minecraftforge.network.NetworkHooks
 */
package com.claimblocks.gui;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.data.GlobalFlags;
import com.claimblocks.gui.AdminPanelHandler;
import java.util.List;
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
import net.minecraftforge.network.NetworkHooks;

public class AdminGlobalFlagsHandler
extends ChestMenu {
    private final SimpleContainer inv;
    private final ServerPlayer viewer;

    public AdminGlobalFlagsHandler(int syncId, Inventory pInv) {
        this(syncId, pInv, new SimpleContainer(54));
    }

    private AdminGlobalFlagsHandler(int syncId, Inventory pInv, SimpleContainer inv) {
        super(MenuType.f_39962_, syncId, pInv, (Container)inv, 6);
        this.inv = inv;
        this.viewer = (ServerPlayer)pInv.f_35978_;
        this.rebuild();
    }

    public boolean m_6875_(Player player) {
        return true;
    }

    public ItemStack m_7648_(Player player, int index) {
        return ItemStack.f_41583_;
    }

    private void rebuild() {
        ItemStack bg = AdminGlobalFlagsHandler.withName(new ItemStack((ItemLike)Items.f_42191_), (Component)Component.m_237113_((String)" "));
        for (int i = 0; i < 54; ++i) {
            this.inv.m_6836_(i, bg.m_41777_());
        }
        GlobalFlags g = GlobalFlags.getInstance();
        this.inv.m_6836_(11, AdminGlobalFlagsHandler.flagButton("PVP global", g.globalPVP, "Permite PVP fuera de claims"));
        this.inv.m_6836_(13, AdminGlobalFlagsHandler.flagButton("Mob griefing global", g.globalMobGriefing, "Mobs destruyen bloques fuera de claims"));
        this.inv.m_6836_(15, AdminGlobalFlagsHandler.flagButton("Propagaci\u00f3n de fuego", g.globalFireSpread, "Fire spread global gamerule"));
        this.inv.m_6836_(17, AdminGlobalFlagsHandler.flagButton("Sin spawn de mobs (global)", g.globalNoMobSpawn, "Ning\u00fan mob spawnea en TODO el servidor"));
        this.inv.m_6836_(22, AdminGlobalFlagsHandler.withName(new ItemStack((ItemLike)Items.f_42412_), (Component)Component.m_237113_((String)"Volver al panel").m_130940_(ChatFormatting.AQUA)));
        this.m_38946_();
    }

    private static ItemStack flagButton(String name, boolean on, String desc) {
        ItemStack stack = new ItemStack((ItemLike)(on ? Items.f_42540_ : Items.f_42490_));
        MutableComponent title = Component.m_237113_((String)(name + " " + (on ? "[ON]" : "[OFF]"))).m_130944_(new ChatFormatting[]{on ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD});
        return AdminGlobalFlagsHandler.withLore(AdminGlobalFlagsHandler.withName(stack, (Component)title), List.of(Component.m_237113_((String)desc).m_130940_(ChatFormatting.GRAY), Component.m_237113_((String)("Estado: " + (on ? "ACTIVO" : "INACTIVO") + " - Clic para cambiar")).m_130940_(ChatFormatting.GRAY)));
    }

    public void m_150399_(int slot, int button, ClickType clickType, Player player) {
        if (slot >= 0 && slot < 54) {
            if (slot == 22) {
                AdminPanelHandler.open(this.viewer, 0);
            } else {
                GlobalFlags g = GlobalFlags.getInstance();
                String name = null;
                boolean newVal = false;
                if (slot == 11) {
                    name = "globalPVP";
                    newVal = !g.globalPVP;
                } else if (slot == 13) {
                    name = "globalMobGriefing";
                    newVal = !g.globalMobGriefing;
                } else if (slot == 15) {
                    name = "globalFireSpread";
                    newVal = !g.globalFireSpread;
                } else if (slot == 17) {
                    name = "globalNoMobSpawn";
                    boolean bl = newVal = !g.globalNoMobSpawn;
                }
                if (name != null) {
                    g.set(name, newVal, this.viewer.f_8924_);
                    MutableComponent bcast = Component.m_237113_((String)"[!] Un administrador cambi\u00f3 una configuraci\u00f3n global del servidor.").m_130940_(ChatFormatting.YELLOW);
                    this.viewer.f_8924_.m_6846_().m_11314_().forEach(arg_0 -> AdminGlobalFlagsHandler.lambda$clicked$0((Component)bcast, arg_0));
                    this.rebuild();
                }
            }
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

    public static void open(ServerPlayer player) {
        NetworkHooks.openScreen((ServerPlayer)player, (MenuProvider)new MenuProvider(){

            public Component m_5446_() {
                return Component.m_237113_((String)"Flags Globales").m_130944_(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD});
            }

            public AbstractContainerMenu m_7208_(int id, Inventory inv, Player pl) {
                return new AdminGlobalFlagsHandler(id, inv);
            }
        });
    }

    private static /* synthetic */ void lambda$clicked$0(Component bcast, ServerPlayer p) {
        p.m_5661_(bcast, false);
    }
}

