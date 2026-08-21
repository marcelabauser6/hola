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
 *  net.minecraft.world.item.Item
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
import com.claimblocks.render.ParticleBorder;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.network.NetworkHooks;

public class ClaimParticleMenuHandler
extends ChestMenu {
    private static final int[] PARTICLE_SLOTS = new int[]{9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35};
    private static final int SLOT_TOGGLE = 4;
    private static final int SLOT_DENSITY_DOWN = 48;
    private static final int SLOT_DENSITY_INFO = 49;
    private static final int SLOT_DENSITY_UP = 50;
    private static final int SLOT_BACK = 45;
    private final SimpleContainer chest;
    private final Claim claim;
    private final ServerPlayer viewer;
    private final int returnPage;

    public ClaimParticleMenuHandler(int syncId, Inventory pInv, Claim claim, int returnPage) {
        this(syncId, pInv, new SimpleContainer(54), claim, returnPage);
    }

    private ClaimParticleMenuHandler(int syncId, Inventory pInv, SimpleContainer chest, Claim claim, int returnPage) {
        super(MenuType.f_39962_, syncId, pInv, (Container)chest, 6);
        this.chest = chest;
        this.claim = claim;
        this.viewer = (ServerPlayer)pInv.f_35978_;
        this.returnPage = returnPage;
        this.rebuild();
    }

    public boolean m_6875_(Player player) {
        return true;
    }

    public ItemStack m_7648_(Player player, int index) {
        return ItemStack.f_41583_;
    }

    private void rebuild() {
        ItemStack bg = ClaimParticleMenuHandler.withName(new ItemStack((ItemLike)Items.f_42183_), (Component)Component.m_237113_((String)" "));
        for (int i = 0; i < 54; ++i) {
            this.chest.m_6836_(i, bg.m_41777_());
        }
        boolean on = this.claim.getFlags().showParticles;
        this.chest.m_6836_(4, ClaimParticleMenuHandler.withLore(ClaimParticleMenuHandler.withName(new ItemStack((ItemLike)(on ? Items.f_42540_ : Items.f_42490_)), (Component)Component.m_237113_((String)(on ? "Part\u00edculas: ACTIVAS" : "Part\u00edculas: INACTIVAS")).m_130944_(new ChatFormatting[]{on ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD})), List.of(Component.m_237113_((String)"Llena tu protecci\u00f3n con part\u00edculas.").m_130940_(ChatFormatting.GRAY), Component.m_237113_((String)("Clic para " + (on ? "desactivar" : "activar"))).m_130940_(ChatFormatting.YELLOW))));
        String current = this.claim.getFlags().borderParticle;
        String[] particles = ParticleBorder.availableParticles();
        for (int i = 0; i < particles.length && i < PARTICLE_SLOTS.length; ++i) {
            String pid = particles[i];
            boolean selected = pid.equals(current);
            this.chest.m_6836_(PARTICLE_SLOTS[i], ClaimParticleMenuHandler.withLore(ClaimParticleMenuHandler.withName(new ItemStack((ItemLike)ClaimParticleMenuHandler.iconFor(pid)), (Component)Component.m_237113_((String)ParticleBorder.particleLabel(pid)).m_130944_(new ChatFormatting[]{selected ? ChatFormatting.GREEN : ChatFormatting.AQUA, ChatFormatting.BOLD})), List.of(Component.m_237113_((String)(selected ? "\u2714 Part\u00edcula seleccionada" : "Clic para usar esta part\u00edcula")).m_130940_(selected ? ChatFormatting.GREEN : ChatFormatting.GRAY), Component.m_237113_((String)"Activa las part\u00edculas autom\u00e1ticamente").m_130940_(ChatFormatting.DARK_GRAY))));
        }
        int density = this.claim.getFlags().particleDensity;
        this.chest.m_6836_(48, ClaimParticleMenuHandler.withName(new ItemStack((ItemLike)Items.f_42451_), (Component)Component.m_237113_((String)"- Menos part\u00edculas (-5)").m_130940_(ChatFormatting.RED)));
        this.chest.m_6836_(49, ClaimParticleMenuHandler.withLore(ClaimParticleMenuHandler.withName(new ItemStack((ItemLike)Items.f_42525_), (Component)Component.m_237113_((String)("Densidad: " + density)).m_130944_(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD})), List.of(Component.m_237113_((String)"Cantidad de part\u00edculas por emisi\u00f3n.").m_130940_(ChatFormatting.GRAY), Component.m_237113_((String)"Rango 1 - 200. Recomendado 5 - 40.").m_130940_(ChatFormatting.DARK_GRAY))));
        this.chest.m_6836_(50, ClaimParticleMenuHandler.withName(new ItemStack((ItemLike)Items.f_42054_), (Component)Component.m_237113_((String)"+ M\u00e1s part\u00edculas (+5)").m_130940_(ChatFormatting.GREEN)));
        this.chest.m_6836_(45, ClaimParticleMenuHandler.withName(new ItemStack((ItemLike)Items.f_42412_), (Component)Component.m_237113_((String)"<< Volver").m_130940_(ChatFormatting.AQUA)));
        this.m_38946_();
    }

    private static Item iconFor(String pid) {
        switch (pid) {
            case "minecraft:heart": {
                return Items.f_41940_;
            }
            case "minecraft:flame": {
                return Items.f_42593_;
            }
            case "minecraft:small_flame": {
                return Items.f_42000_;
            }
            case "minecraft:soul_fire_flame": {
                return Items.f_42053_;
            }
            case "minecraft:soul": {
                return Items.f_42779_;
            }
            case "minecraft:end_rod": {
                return Items.f_42001_;
            }
            case "minecraft:crit": {
                return Items.f_42383_;
            }
            case "minecraft:enchanted_hit": {
                return Items.f_42388_;
            }
            case "minecraft:enchant": {
                return Items.f_42690_;
            }
            case "minecraft:dragon_breath": {
                return Items.f_42735_;
            }
            case "minecraft:portal": {
                return Items.f_42584_;
            }
            case "minecraft:reverse_portal": {
                return Items.f_42545_;
            }
            case "minecraft:cloud": {
                return Items.f_41870_;
            }
            case "minecraft:electric_spark": {
                return Items.f_151049_;
            }
            case "minecraft:wax_on": {
                return Items.f_42784_;
            }
            case "minecraft:glow": {
                return Items.f_151056_;
            }
            case "minecraft:totem_of_undying": {
                return Items.f_42747_;
            }
            case "minecraft:firework": {
                return Items.f_42688_;
            }
            case "minecraft:note": {
                return Items.f_41859_;
            }
            case "minecraft:snowflake": {
                return Items.f_42452_;
            }
            case "minecraft:cherry_leaves": {
                return Items.f_271517_;
            }
            case "minecraft:spore_blossom_air": {
                return Items.f_151014_;
            }
            case "minecraft:sculk_soul": {
                return Items.f_220194_;
            }
            case "minecraft:lava": {
                return Items.f_42448_;
            }
            case "minecraft:splash": {
                return Items.f_42447_;
            }
            case "minecraft:witch": {
                return Items.f_42592_;
            }
        }
        return Items.f_42616_;
    }

    public void m_150399_(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < 54) {
            if (slotId == 4) {
                this.claim.getFlags().showParticles = !this.claim.getFlags().showParticles;
                ClaimManager.getInstance().save();
                this.rebuild();
            } else if (slotId == 45) {
                ClaimMenuHandler.open(this.viewer, this.claim, this.returnPage);
            } else if (slotId != 48 && slotId != 50) {
                String[] particles = ParticleBorder.availableParticles();
                for (int i = 0; i < PARTICLE_SLOTS.length && i < particles.length; ++i) {
                    if (PARTICLE_SLOTS[i] != slotId) continue;
                    this.claim.getFlags().borderParticle = particles[i];
                    this.claim.getFlags().showParticles = true;
                    ClaimManager.getInstance().save();
                    this.viewer.m_5661_((Component)Component.m_237113_((String)("\u2714 Part\u00edcula: " + ParticleBorder.particleLabel(particles[i]))).m_130940_(ChatFormatting.GREEN), true);
                    this.rebuild();
                    return;
                }
            } else {
                int d = this.claim.getFlags().particleDensity + (slotId == 50 ? 5 : -5);
                this.claim.getFlags().particleDensity = Math.max(1, Math.min(200, d));
                ClaimManager.getInstance().save();
                this.rebuild();
            }
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

    public static void open(ServerPlayer player, final Claim claim, final int returnPage) {
        NetworkHooks.openScreen((ServerPlayer)player, (MenuProvider)new MenuProvider(){

            public Component m_5446_() {
                return Component.m_237113_((String)"Part\u00edculas de la protecci\u00f3n").m_130944_(new ChatFormatting[]{ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD});
            }

            public AbstractContainerMenu m_7208_(int id, Inventory inv, Player pl) {
                return new ClaimParticleMenuHandler(id, inv, claim, returnPage);
            }
        });
    }
}

