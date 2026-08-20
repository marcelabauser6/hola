/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.core.BlockPos
 *  net.minecraft.network.chat.ClickEvent
 *  net.minecraft.network.chat.ClickEvent$Action
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.network.chat.Style
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.sounds.SoundEvents
 *  net.minecraft.sounds.SoundSource
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
 *  net.minecraftforge.event.ServerChatEvent
 *  net.minecraftforge.network.NetworkHooks
 */
package com.claimblocks.gui;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.chat.ChatPromptRouter;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimFlags;
import com.claimblocks.data.ClaimGroup;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.data.ClaimTier;
import com.claimblocks.gui.ClaimParticleMenuHandler;
import com.claimblocks.gui.MemberSelectMenu;
import com.claimblocks.util.PlayerLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.network.NetworkHooks;

public class ClaimMenuHandler
extends ChestMenu {
    public static final int SIZE = 54;
    private static final int[] FLAG_SLOTS_P0 = new int[]{18, 19, 20, 21, 22, 23, 24, 25, 26, 28, 29, 30, 31};
    private static final int[] FLAG_SLOTS_P1 = new int[]{18, 19, 20, 21, 22, 23, 24, 25, 26, 28, 29, 30, 31, 32, 33, 34, 35, 27};
    private static final ClaimFlags.FlagId[] PAGE_0 = new ClaimFlags.FlagId[]{ClaimFlags.FlagId.BUILDING, ClaimFlags.FlagId.BREAKING, ClaimFlags.FlagId.EXPLOSIONS, ClaimFlags.FlagId.FIRE, ClaimFlags.FlagId.MOB_SPAWN, ClaimFlags.FlagId.PVP, ClaimFlags.FlagId.MOB_DAMAGE, ClaimFlags.FlagId.ALERTS, ClaimFlags.FlagId.PUBLIC_MODE, ClaimFlags.FlagId.ANIMAL_KILLING, ClaimFlags.FlagId.CHEST_ACCESS, ClaimFlags.FlagId.CROP_HARVEST, ClaimFlags.FlagId.BURN_HOSTILES};
    private static final ClaimFlags.FlagId[] PAGE_1 = new ClaimFlags.FlagId[]{ClaimFlags.FlagId.ITEM_USE, ClaimFlags.FlagId.ENTITY_INTERACT, ClaimFlags.FlagId.TRAMPLING, ClaimFlags.FlagId.FLUIDS, ClaimFlags.FlagId.PVP_ALL, ClaimFlags.FlagId.TREE_CHOPPING, ClaimFlags.FlagId.SHOW_WELCOME, ClaimFlags.FlagId.ANVIL_USE, ClaimFlags.FlagId.ENDER_PEARL, ClaimFlags.FlagId.SIGN_EDITING, ClaimFlags.FlagId.DOORS_ACCESS, ClaimFlags.FlagId.EFFECT_REGEN, ClaimFlags.FlagId.EFFECT_RESIST, ClaimFlags.FlagId.EFFECT_SPEED, ClaimFlags.FlagId.ALLOW_FLIGHT, ClaimFlags.FlagId.SHOW_LEAVE, ClaimFlags.FlagId.SHOW_BORDER, ClaimFlags.FlagId.SHOW_PARTICLES};
    private static final int[] FLAG_SLOTS_P2 = new int[]{20, 22, 24};
    private static final ClaimFlags.FlagId[] PAGE_2 = new ClaimFlags.FlagId[]{ClaimFlags.FlagId.ALL_MOB_SPAWN, ClaimFlags.FlagId.PASSIVE_MOB_SPAWN, ClaimFlags.FlagId.BLOCK_ALL_INTERACT};
    private static final ClaimFlags.FlagId[][] PAGES = new ClaimFlags.FlagId[][]{PAGE_0, PAGE_1, PAGE_2};
    private static final int[][] PAGE_SLOTS = new int[][]{FLAG_SLOTS_P0, FLAG_SLOTS_P1, FLAG_SLOTS_P2};
    private static final int LAST_PAGE = PAGES.length - 1;
    private static final long PROMPT_TTL_MS = 90000L;
    private static final Map<UUID, PendingChat> pending = new ConcurrentHashMap<UUID, PendingChat>();
    private static final Map<UUID, String> pendingMergeName = new ConcurrentHashMap<UUID, String>();
    private static final Map<String, MergeInvite> invites = new ConcurrentHashMap<String, MergeInvite>();
    private final SimpleContainer chest;
    private final Claim claim;
    private final ServerPlayer viewer;
    private final int page;
    private boolean awaitingDeleteConfirm = false;

    public ClaimMenuHandler(int syncId, Inventory pInv, Claim claim, int page) {
        this(syncId, pInv, new SimpleContainer(54), claim, page);
    }

    private ClaimMenuHandler(int syncId, Inventory pInv, SimpleContainer chest, Claim claim, int page) {
        super(MenuType.f_39962_, syncId, pInv, (Container)chest, 6);
        this.chest = chest;
        this.claim = claim;
        this.viewer = (ServerPlayer)pInv.f_35978_;
        this.page = page;
        this.rebuild();
    }

    public Claim getClaim() {
        return this.claim;
    }

    public int getPage() {
        return this.page;
    }

    public boolean m_6875_(Player player) {
        return true;
    }

    public ItemStack m_7648_(Player player, int index) {
        return ItemStack.f_41583_;
    }

    private void rebuild() {
        ClaimGroup grp;
        ItemStack bg = ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.f_42183_), (Component)Component.m_237113_((String)" "));
        for (int i = 0; i < 54; ++i) {
            this.chest.m_6836_(i, bg.m_41777_());
        }
        ClaimGroup hdrGrp = ClaimManager.getInstance().getGroupOf(this.claim);
        String header = hdrGrp != null ? "Grupo: " + hdrGrp.getName() : "Zona " + this.claim.sizeLabel() + " - " + this.claim.getOwnerName();
        this.chest.m_6836_(4, ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.f_42516_), (Component)Component.m_237113_((String)ClaimMenuHandler.truncate(header, 30)).m_130944_(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD})));
        this.chest.m_6836_(11, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.f_42522_), (Component)Component.m_237113_((String)"Coordenadas").m_130940_(ChatFormatting.AQUA)), List.of(Component.m_237113_((String)("X=" + this.claim.getX() + " Y=" + this.claim.getY() + " Z=" + this.claim.getZ())).m_130940_(ChatFormatting.WHITE))));
        this.chest.m_6836_(13, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.f_42680_), (Component)Component.m_237113_((String)"Due\u00f1o").m_130940_(ChatFormatting.AQUA)), List.of(Component.m_237113_((String)ClaimMenuHandler.truncate(this.claim.getOwnerName(), 35)).m_130944_(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD}))));
        this.chest.m_6836_(15, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.f_42415_), (Component)Component.m_237113_((String)("Zona " + this.claim.sizeLabel())).m_130940_(ChatFormatting.YELLOW)), List.of(Component.m_237113_((String)("Zona " + this.claim.sizeLabel() + " bloques")).m_130940_(ChatFormatting.GRAY), Component.m_237113_((String)("Altura: +/-" + this.claim.getHeight())).m_130940_(ChatFormatting.GRAY))));
        this.chest.m_6836_(17, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.f_42676_), (Component)Component.m_237113_((String)"Mundo").m_130940_(ChatFormatting.AQUA)), List.of(Component.m_237113_((String)ClaimMenuHandler.truncate(this.claim.getWorld(), 35)).m_130940_(ChatFormatting.GRAY))));
        ClaimFlags f = this.claim.getFlags();
        ClaimFlags.FlagId[] ids = PAGES[this.pageIndex()];
        int[] slots = PAGE_SLOTS[this.pageIndex()];
        int tierLevel = ClaimMenuHandler.paidLevelOf(this.claim.getTier());
        for (int i = 0; i < ids.length; ++i) {
            ClaimFlags.FlagId id = ids[i];
            int reqLevel = ClaimMenuHandler.requiredPaidLevel(id);
            if (reqLevel > 0 && tierLevel < reqLevel) {
                this.chest.m_6836_(slots[i], this.lockedEffectButton(id, reqLevel));
                continue;
            }
            this.chest.m_6836_(slots[i], this.flagButton(id, f.get(id)));
        }
        this.chest.m_6836_(38, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.f_42614_), (Component)Component.m_237113_((String)("Miembros (" + this.claim.getMembers().size() + ")")).m_130940_(ChatFormatting.YELLOW)), this.buildMemberLore()));
        this.chest.m_6836_(40, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.f_42656_), (Component)Component.m_237113_((String)"Quitar miembro").m_130940_(ChatFormatting.RED)), List.of(Component.m_237113_((String)"Pide nombre por chat").m_130940_(ChatFormatting.GRAY), Component.m_237113_((String)"Clic para eliminar a un invitado").m_130940_(ChatFormatting.GRAY))));
        this.chest.m_6836_(42, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.f_42680_), (Component)Component.m_237113_((String)"A\u00f1adir miembro").m_130944_(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD})), List.of(Component.m_237113_((String)"Clic izq: elegir de una lista").m_130940_(ChatFormatting.GRAY), Component.m_237113_((String)"Clic der: escribir el nombre por chat").m_130940_(ChatFormatting.GRAY), Component.m_237113_((String)"Tambi\u00e9n sirve /claim addmember <jugador>").m_130940_(ChatFormatting.DARK_GRAY))));
        this.chest.m_6836_(39, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.f_42025_), (Component)Component.m_237113_((String)"Banear jugador").m_130944_(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})), this.buildBanLore()));
        this.chest.m_6836_(41, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.f_42109_), (Component)Component.m_237113_((String)"Desbanear jugador").m_130940_(ChatFormatting.GREEN)), List.of(Component.m_237113_((String)"Pide nombre por chat").m_130940_(ChatFormatting.GRAY), Component.m_237113_((String)"Clic para quitar del baneo").m_130940_(ChatFormatting.GRAY))));
        if (this.page > 0) {
            this.chest.m_6836_(45, ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.f_42412_), (Component)Component.m_237113_((String)"<< P\u00e1gina anterior").m_130940_(ChatFormatting.AQUA)));
        }
        if (this.awaitingDeleteConfirm) {
            this.chest.m_6836_(46, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.f_41996_), (Component)Component.m_237113_((String)"Confirmar eliminaci\u00f3n").m_130944_(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})), List.of(Component.m_237113_((String)"Haz clic de nuevo para confirmar").m_130940_(ChatFormatting.YELLOW))));
            this.chest.m_6836_(47, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.f_42540_), (Component)Component.m_237113_((String)"Cancelar").m_130944_(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD})), List.of(Component.m_237113_((String)"Cancela la eliminaci\u00f3n").m_130940_(ChatFormatting.GRAY))));
        } else {
            this.chest.m_6836_(46, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.f_42127_), (Component)Component.m_237113_((String)"Eliminar zona").m_130944_(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})), List.of(Component.m_237113_((String)"Clic para iniciar eliminaci\u00f3n").m_130940_(ChatFormatting.YELLOW), Component.m_237113_((String)"Devuelve la protecci\u00f3n al inv.").m_130940_(ChatFormatting.GRAY))));
        }
        this.chest.m_6836_(49, ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.f_42497_), (Component)Component.m_237113_((String)"Cerrar").m_130940_(ChatFormatting.WHITE)));
        this.chest.m_6836_(52, ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.f_42517_), (Component)Component.m_237113_((String)"Ver lista de zonas").m_130940_(ChatFormatting.AQUA)));
        if (this.page < LAST_PAGE) {
            this.chest.m_6836_(53, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.f_42412_), (Component)Component.m_237113_((String)"P\u00e1gina siguiente >>").m_130940_(ChatFormatting.AQUA)), List.of(Component.m_237113_((String)("P\u00e1gina " + (this.page + 1) + " de " + (LAST_PAGE + 1))).m_130940_(ChatFormatting.DARK_GRAY))));
        }
        if ((grp = ClaimManager.getInstance().getGroupOf(this.claim)) == null) {
            this.chest.m_6836_(43, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.f_42518_), (Component)Component.m_237113_((String)"Unir protecci\u00f3n").m_130944_(new ChatFormatting[]{ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD})), List.of(Component.m_237113_((String)"Crea un grupo y une zonas de tu equipo").m_130940_(ChatFormatting.GRAY), Component.m_237113_((String)"Clic: elegir nombre e invitar jugadores").m_130940_(ChatFormatting.GRAY))));
        } else {
            this.chest.m_6836_(43, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.f_42518_), (Component)Component.m_237113_((String)ClaimMenuHandler.truncate("Grupo: " + grp.getName(), 30)).m_130944_(new ChatFormatting[]{ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD})), List.of(Component.m_237113_((String)("Miembros registrados: " + grp.getRegisteredPlayers().size())).m_130940_(ChatFormatting.GRAY), Component.m_237113_((String)"Clic: invitar mas jugadores").m_130940_(ChatFormatting.GRAY))));
            this.chest.m_6836_(44, ClaimMenuHandler.withLore(ClaimMenuHandler.withName(new ItemStack((ItemLike)Items.f_42574_), (Component)Component.m_237113_((String)"Disolver grupo").m_130944_(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})), List.of(Component.m_237113_((String)"Separa todas las piedras del grupo").m_130940_(ChatFormatting.GRAY), Component.m_237113_((String)"Cada zona vuelve a ser independiente").m_130940_(ChatFormatting.GRAY))));
        }
        this.m_38946_();
    }

    private List<Component> buildMemberLore() {
        ArrayList<Component> lore = new ArrayList<Component>();
        if (this.claim.getMembers().isEmpty()) {
            lore.add((Component)Component.m_237113_((String)"(sin miembros)").m_130940_(ChatFormatting.DARK_GRAY));
            return lore;
        }
        int max = Math.min(5, this.claim.getMembers().size());
        for (int i = 0; i < max; ++i) {
            String n = i < this.claim.getMemberNames().size() ? this.claim.getMemberNames().get(i) : this.claim.getMembers().get(i).toString();
            lore.add((Component)Component.m_237113_((String)ClaimMenuHandler.truncate(" - " + n, 35)).m_130940_(ChatFormatting.WHITE));
        }
        if (this.claim.getMembers().size() > max) {
            lore.add((Component)Component.m_237113_((String)(" - ... y " + (this.claim.getMembers().size() - max) + " m\u00e1s")).m_130940_(ChatFormatting.GRAY));
        }
        return lore;
    }

    private List<Component> buildBanLore() {
        ArrayList<Component> lore = new ArrayList<Component>();
        lore.add((Component)Component.m_237113_((String)"Escribe el nombre por chat para banear.").m_130940_(ChatFormatting.GRAY));
        lore.add((Component)Component.m_237113_((String)"Si entran, la barrera los saca de la zona.").m_130940_(ChatFormatting.DARK_GRAY));
        Set<UUID> banned = this.claim.getBannedPlayers();
        lore.add((Component)Component.m_237113_((String)("Baneados: " + banned.size())).m_130944_(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}));
        int i = 0;
        for (UUID id : banned) {
            if (i++ >= 8) {
                lore.add((Component)Component.m_237113_((String)" - ...").m_130940_(ChatFormatting.GRAY));
                break;
            }
            lore.add((Component)Component.m_237113_((String)ClaimMenuHandler.truncate(" - " + PlayerLookup.nameOf(this.viewer.m_20194_(), id), 35)).m_130940_(ChatFormatting.WHITE));
        }
        return lore;
    }

    public static void requestBanPlayer(ServerPlayer player, Claim claim, int returnPage) {
        pending.put(player.m_20148_(), new PendingChat(PendingType.BAN_PLAYER, claim.getClaimId(), returnPage));
        player.m_5661_((Component)Component.m_237113_((String)"[Protecci\u00f3n] Escribe el nombre del jugador a BANEAR (o 'cancelar'):").m_130940_(ChatFormatting.YELLOW), false);
    }

    public static void requestUnbanPlayer(ServerPlayer player, Claim claim, int returnPage) {
        pending.put(player.m_20148_(), new PendingChat(PendingType.UNBAN_PLAYER, claim.getClaimId(), returnPage));
        player.m_5661_((Component)Component.m_237113_((String)"[Protecci\u00f3n] Escribe el nombre del jugador a DESBANEAR (o 'cancelar'):").m_130940_(ChatFormatting.YELLOW), false);
    }

    private static void handleBanPlayer(ServerPlayer sender, Claim claim, String name, int page) {
        String query = ChatPromptRouter.extractPlayerName(name);
        UUID claimId = claim.getClaimId();
        PlayerLookup.resolveAsync(sender.m_20194_(), query, target -> {
            if (sender.m_9232_()) {
                return;
            }
            Claim live = ClaimMenuHandler.findClaimById(claimId);
            if (live == null) {
                sender.m_5661_((Component)Component.m_237113_((String)"[x] La zona ya no existe.").m_130940_(ChatFormatting.RED), false);
                return;
            }
            if (target == null) {
                sender.m_5661_((Component)Component.m_237113_((String)("[x] Jugador no encontrado: " + query)).m_130940_(ChatFormatting.RED), false);
                ClaimMenuHandler.open(sender, live, page);
                return;
            }
            if (live.isOwner(target.id())) {
                sender.m_5661_((Component)Component.m_237113_((String)"[x] No puedes banear al due\u00f1o.").m_130940_(ChatFormatting.RED), false);
                ClaimMenuHandler.open(sender, live, page);
                return;
            }
            // banPlayer ya quita la membresia y escribe en la piedra madre si la zona esta agrupada
            live.banPlayer(target.id());
            ClaimManager.getInstance().save();
            sender.m_5661_((Component)Component.m_237113_((String)("\u2714 " + target.name() + " baneado de la zona.")).m_130940_(ChatFormatting.GREEN), false);
            MutableComponent notice = Component.m_237113_((String)("[!] Has sido baneado de una zona de " + sender.m_7755_().getString())).m_130944_(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD});
            if (target.isOnline()) {
                target.online().m_5661_((Component)notice, false);
            } else {
                ClaimManager.getInstance().queueMessage(target.id(), (Component)notice);
            }
            ClaimMenuHandler.open(sender, live, page);
        });
    }

    private static void handleUnbanPlayer(ServerPlayer sender, Claim claim, String name, int page) {
        String query = ChatPromptRouter.extractPlayerName(name);
        UUID claimId = claim.getClaimId();
        PlayerLookup.resolveAsync(sender.m_20194_(), query, target -> {
            if (sender.m_9232_()) {
                return;
            }
            Claim live = ClaimMenuHandler.findClaimById(claimId);
            if (live == null) {
                sender.m_5661_((Component)Component.m_237113_((String)"[x] La zona ya no existe.").m_130940_(ChatFormatting.RED), false);
                return;
            }
            if (target != null && live.isBanned(target.id())) {
                live.unbanPlayer(target.id());
                ClaimManager.getInstance().save();
                sender.m_5661_((Component)Component.m_237113_((String)("\u2714 " + target.name() + " desbaneado.")).m_130940_(ChatFormatting.GREEN), false);
            } else {
                sender.m_5661_((Component)Component.m_237113_((String)"[x] Ese jugador no est\u00e1 baneado.").m_130940_(ChatFormatting.RED), false);
            }
            ClaimMenuHandler.open(sender, live, page);
        });
    }

    private static int paidLevelOf(ClaimTier t) {
        String var2;
        if (t == null) {
            return 0;
        }
        String var1 = t.id;
        return switch (var2 = t.id) {
            case "claimstone_250x250" -> 1;
            case "claimstone_300x300" -> 2;
            case "claimstone_500x500" -> 3;
            default -> 0;
        };
    }

    private static int requiredPaidLevel(ClaimFlags.FlagId id) {
        return switch (id) {
            case EFFECT_REGEN -> 1;
            case EFFECT_RESIST -> 2;
            case EFFECT_SPEED -> 2;
            case ALLOW_FLIGHT -> 3;
            default -> 0;
        };
    }

    private static String requiredTierLabel(int reqLevel) {
        return switch (reqLevel) {
            case 1 -> "250x250";
            case 2 -> "300x300";
            case 3 -> "500x500";
            default -> "?";
        };
    }

    private ItemStack lockedEffectButton(ClaimFlags.FlagId id, int reqLevel) {
        ItemStack stack = new ItemStack((ItemLike)Items.f_42191_);
        return ClaimMenuHandler.withLore(ClaimMenuHandler.withName(stack, (Component)Component.m_237113_((String)(ClaimMenuHandler.effectName(id) + " [LOCKED]")).m_130940_(ChatFormatting.DARK_GRAY)), List.of(Component.m_237113_((String)("Requiere zona " + ClaimMenuHandler.requiredTierLabel(reqLevel) + " o superior")).m_130940_(ChatFormatting.GRAY), Component.m_237113_((String)ClaimMenuHandler.effectShortDesc(id)).m_130940_(ChatFormatting.DARK_GRAY)));
    }

    private static String effectShortDesc(ClaimFlags.FlagId id) {
        return switch (id) {
            case EFFECT_REGEN -> "Regenera vida a duenio y miembros";
            case EFFECT_RESIST -> "Reduce dano a duenio y miembros";
            case EFFECT_SPEED -> "Da velocidad a duenio y miembros";
            case ALLOW_FLIGHT -> "Solo el duenio puede volar en su zona";
            default -> "Perk pasivo";
        };
    }

    private static String effectName(ClaimFlags.FlagId id) {
        return switch (id) {
            case EFFECT_REGEN -> "Regeneraci\u00f3n pasiva";
            case EFFECT_RESIST -> "Resistencia pasiva";
            case EFFECT_SPEED -> "Velocidad pasiva";
            case ALLOW_FLIGHT -> "Vuelo en zona";
            default -> "Perk pasivo";
        };
    }

    private ItemStack flagButton(ClaimFlags.FlagId id, boolean enabled) {
        ItemStack stack = new ItemStack((ItemLike)(enabled ? Items.f_42540_ : Items.f_42490_));
        MutableComponent name = Component.m_237113_((String)ClaimMenuHandler.flagDisplayName(id, enabled)).m_130944_(new ChatFormatting[]{enabled ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD});
        String[] lore = ClaimMenuHandler.flagLore(id);
        return ClaimMenuHandler.withLore(ClaimMenuHandler.withName(stack, (Component)name), List.of(Component.m_237113_((String)lore[0]).m_130940_(ChatFormatting.GRAY), Component.m_237113_((String)("Estado: " + (enabled ? "ACTIVO" : "INACTIVO") + " - " + lore[1])).m_130940_(ChatFormatting.GRAY)));
    }

    private static String flagDisplayName(ClaimFlags.FlagId id, boolean on) {
        return switch (id) {
            default -> throw new IncompatibleClassChangeError();
            case EFFECT_REGEN -> {
                if (on) {
                    yield "Regeneraci\u00f3n pasiva [ON]";
                }
                yield "Regeneraci\u00f3n pasiva [OFF]";
            }
            case EFFECT_RESIST -> {
                if (on) {
                    yield "Resistencia pasiva [ON]";
                }
                yield "Resistencia pasiva [OFF]";
            }
            case EFFECT_SPEED -> {
                if (on) {
                    yield "Velocidad pasiva [ON]";
                }
                yield "Velocidad pasiva [OFF]";
            }
            case ALLOW_FLIGHT -> {
                if (on) {
                    yield "Vuelo en zona: ACTIVO [ON]";
                }
                yield "Vuelo en zona: inactivo [OFF]";
            }
            case BUILDING -> {
                if (on) {
                    yield "Construir: BLOQUEADO [ON]";
                }
                yield "Construir: permitido [OFF]";
            }
            case BREAKING -> {
                if (on) {
                    yield "Romper: BLOQUEADO [ON]";
                }
                yield "Romper: permitido [OFF]";
            }
            case EXPLOSIONS -> {
                if (on) {
                    yield "Explosiones: BLOQUEADAS [ON]";
                }
                yield "Explosiones: permitidas [OFF]";
            }
            case FIRE -> {
                if (on) {
                    yield "Fuego: BLOQUEADO [ON]";
                }
                yield "Fuego: permitido [OFF]";
            }
            case MOB_SPAWN -> {
                if (on) {
                    yield "Mobs hostiles: BLOQUEADOS [ON]";
                }
                yield "Mobs hostiles: permit. [OFF]";
            }
            case PVP -> {
                if (on) {
                    yield "PVP: BLOQUEADO [ON]";
                }
                yield "PVP: permitido [OFF]";
            }
            case MOB_DAMAGE -> {
                if (on) {
                    yield "Da\u00f1o de mobs: BLOQUEADO [ON]";
                }
                yield "Da\u00f1o de mobs: permit. [OFF]";
            }
            case ALERTS -> {
                if (on) {
                    yield "Alertas intrusos: ON [ON]";
                }
                yield "Alertas intrusos: OFF [OFF]";
            }
            case ITEM_USE -> {
                if (on) {
                    yield "Usar items: BLOQUEADO [ON]";
                }
                yield "Usar items: permitido [OFF]";
            }
            case ENTITY_INTERACT -> {
                if (on) {
                    yield "Entidades: BLOQUEADAS [ON]";
                }
                yield "Entidades: libres [OFF]";
            }
            case TRAMPLING -> {
                if (on) {
                    yield "Cultivos: PROTEGIDOS [ON]";
                }
                yield "Cultivos: sin protec. [OFF]";
            }
            case FLUIDS -> {
                if (on) {
                    yield "Fluidos: BLOQUEADOS [ON]";
                }
                yield "Fluidos: permitidos [OFF]";
            }
            case PVP_ALL -> {
                if (on) {
                    yield "Zona PVP libre: ACTIVA [ON]";
                }
                yield "Zona PVP libre: inact. [OFF]";
            }
            case TREE_CHOPPING -> {
                if (on) {
                    yield "\u00c1rboles: PROTEGIDOS [ON]";
                }
                yield "\u00c1rboles: se talan [OFF]";
            }
            case PUBLIC_MODE -> {
                if (on) {
                    yield "Modo visita: ACTIVO [ON]";
                }
                yield "Modo visita: inactivo [OFF]";
            }
            case SHOW_WELCOME -> {
                if (on) {
                    yield "Bienvenida custom: ON [ON]";
                }
                yield "Bienvenida custom: OFF [OFF]";
            }
            case SHOW_LEAVE -> {
                if (on) {
                    yield "Mensaje de salida: ON [ON]";
                }
                yield "Mensaje de salida: OFF [OFF]";
            }
            case SHOW_BORDER -> {
                if (on) {
                    yield "Ver contorno: ON [ON]";
                }
                yield "Ver contorno: OFF [OFF]";
            }
            case SHOW_PARTICLES -> {
                if (on) {
                    yield "Ver part\u00edculas: ON [ON]";
                }
                yield "Ver part\u00edculas: OFF [OFF]";
            }
            case BURN_HOSTILES -> {
                if (on) {
                    yield "Repeler hostiles: ON [ON]";
                }
                yield "Repeler hostiles: OFF [OFF]";
            }
            case ANIMAL_KILLING -> {
                if (on) {
                    yield "Animales: PROTEGIDOS [ON]";
                }
                yield "Animales: se matan [OFF]";
            }
            case CHEST_ACCESS -> {
                if (on) {
                    yield "Cofres: BLOQUEADOS [ON]";
                }
                yield "Cofres: acceso libre [OFF]";
            }
            case CROP_HARVEST -> {
                if (on) {
                    yield "Cosecha: PROTEGIDA [ON]";
                }
                yield "Cosecha: libre [OFF]";
            }
            case ANVIL_USE -> {
                if (on) {
                    yield "Yunques: BLOQUEADOS [ON]";
                }
                yield "Yunques: uso libre [OFF]";
            }
            case ENDER_PEARL -> {
                if (on) {
                    yield "Ender pearl: BLOQUEADA [ON]";
                }
                yield "Ender pearl: permitida [OFF]";
            }
            case SIGN_EDITING -> {
                if (on) {
                    yield "Letreros: BLOQUEADOS [ON]";
                }
                yield "Letreros: editables [OFF]";
            }
            case DOORS_ACCESS -> {
                if (on) {
                    yield "Puertas/Botones: BLOQ [ON]";
                }
                yield "Puertas/Botones: libres [OFF]";
            }
            case ALL_MOB_SPAWN -> {
                if (on) {
                    yield "Spawn de mobs: BLOQUEADO [ON]";
                }
                yield "Spawn de mobs: permitido [OFF]";
            }
            case PASSIVE_MOB_SPAWN -> {
                if (on) {
                    yield "Animales: NO spawnean [ON]";
                }
                yield "Animales: spawnean [OFF]";
            }
            case BLOCK_ALL_INTERACT -> on ? "Interacci\u00f3n total: BLOQ [ON]" : "Interacci\u00f3n total: libre [OFF]";
        };
    }

    private static String[] flagLore(ClaimFlags.FlagId id) {
        String desc;
        switch (id) {
            case EFFECT_REGEN -> desc = "Regenera vida a due\u00f1o y miembros";
            case EFFECT_RESIST -> desc = "Reduce da\u00f1o a due\u00f1o y miembros";
            case EFFECT_SPEED -> desc = "Da velocidad a due\u00f1o y miembros";
            case ALLOW_FLIGHT -> desc = "Due\u00f1o puede volar";
            case BUILDING -> desc = "Intrusos no pueden colocar bloques";
            case BREAKING -> desc = "Intrusos no pueden romper nada";
            case EXPLOSIONS -> desc = "TNT y creepers no destruyen";
            case FIRE -> desc = "El fuego no se propaga aqu\u00ed";
            case MOB_SPAWN -> desc = "Zombies, skeletons no spawnean";
            case PVP -> desc = "Jugadores no pueden atacarse";
            case MOB_DAMAGE -> desc = "Los mobs no da\u00f1an a jugadores";
            case ALERTS -> desc = "Avisa al due\u00f1o cuando entran";
            case ITEM_USE -> desc = "Intrusos no pueden usar items";
            case ENTITY_INTERACT -> desc = "Intrusos no usan mobs/aldeanos";
            case TRAMPLING -> desc = "Intrusos no destruyen la tierra";
            case FLUIDS -> desc = "Nadie coloca agua ni lava aqu\u00ed";
            case PVP_ALL -> desc = "Todos se pueden atacar aqu\u00ed";
            case TREE_CHOPPING -> desc = "Intrusos no pueden talar \u00e1rboles";
            case PUBLIC_MODE -> desc = "Todos entran pero no modifican";
            case SHOW_WELCOME -> desc = "Mensaje personalizado al entrar";
            case SHOW_LEAVE -> desc = "Mensaje personalizado al salir";
            case SHOW_BORDER -> desc = "Dibuja el contorno de tu protecci\u00f3n (l\u00edneas)";
            case SHOW_PARTICLES -> desc = "Llena tu protecci\u00f3n con part\u00edculas";
            case BURN_HOSTILES -> desc = "Quema a los mobs hostiles que entren (d\u00eda o noche)";
            case ANIMAL_KILLING -> desc = "Intrusos no pueden matar animales";
            case CHEST_ACCESS -> desc = "Intrusos no abren cofres ni barriles";
            case CROP_HARVEST -> desc = "Intrusos no cosechan cultivos";
            case ANVIL_USE -> desc = "Intrusos no pueden usar yunques";
            case ENDER_PEARL -> desc = "Intrusos no se teletransportan";
            case SIGN_EDITING -> desc = "Intrusos no editan letreros";
            case DOORS_ACCESS -> desc = "Intrusos no usan puertas, botones ni placas";
            case ALL_MOB_SPAWN -> desc = "Nada spawnea aqu\u00ed: hostiles, animales y mobs de otros mods";
            case PASSIVE_MOB_SPAWN -> desc = "Animales, peces y murci\u00e9lagos dejan de spawnear (aldeanos no)";
            case BLOCK_ALL_INTERACT -> desc = "Intrusos no pueden interactuar con NADA en la zona";
            default -> desc = "";
        }
        String action = id != ClaimFlags.FlagId.SHOW_WELCOME && id != ClaimFlags.FlagId.SHOW_LEAVE ? (id == ClaimFlags.FlagId.SHOW_PARTICLES ? "Clic para elegir part\u00edcula y densidad" : "Clic para cambiar") : "Clic izq: editar | Clic der: on/off";
        return new String[]{desc, action};
    }

    private static ItemStack withName(ItemStack stack, Component name) {
        stack.m_41714_(name);
        return stack;
    }

    private static ItemStack withLore(ItemStack stack, List<Component> lore) {
        ClaimBlocks.setLore(stack, lore);
        return stack;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 3)) + "...";
    }

    public void m_150399_(int slotId, int button, ClickType clickType, Player player) {
        // si la zona se borro mientras el menu estaba abierto, no seguir editando un objeto huerfano
        if (ClaimManager.getInstance().findClaimById(this.claim.getClaimId()) == null) {
            this.viewer.m_5661_((Component)Component.m_237113_((String)"[x] La zona ya no existe.").m_130940_(ChatFormatting.RED), false);
            this.viewer.m_6915_();
            return;
        }
        if (slotId >= 0 && slotId < 54) {
            if (slotId == 45 && this.page > 0) {
                ClaimMenuHandler.open(this.viewer, this.claim, this.page - 1);
            } else if (slotId == 53 && this.page < LAST_PAGE) {
                ClaimMenuHandler.open(this.viewer, this.claim, this.page + 1);
            } else if (slotId == 46) {
                if (!this.awaitingDeleteConfirm) {
                    this.awaitingDeleteConfirm = true;
                    this.rebuild();
                    this.viewer.m_5661_((Component)Component.m_237113_((String)"[!] Haz clic de nuevo para confirmar.").m_130940_(ChatFormatting.YELLOW), true);
                } else {
                    this.performDelete();
                }
            } else if (slotId == 47 && this.awaitingDeleteConfirm) {
                this.awaitingDeleteConfirm = false;
                this.rebuild();
                this.viewer.m_5661_((Component)Component.m_237113_((String)"[i] Eliminaci\u00f3n cancelada.").m_130940_(ChatFormatting.AQUA), true);
            } else {
                ClaimFlags.FlagId clicked;
                if (this.awaitingDeleteConfirm) {
                    this.awaitingDeleteConfirm = false;
                }
                if ((clicked = this.slotToFlag(slotId)) != null) {
                    int reqLevel = ClaimMenuHandler.requiredPaidLevel(clicked);
                    if (reqLevel > 0 && ClaimMenuHandler.paidLevelOf(this.claim.getTier()) < reqLevel) {
                        this.viewer.m_5661_((Component)Component.m_237113_((String)("[x] Requiere zona " + ClaimMenuHandler.requiredTierLabel(reqLevel) + " o superior.")).m_130940_(ChatFormatting.RED), true);
                        return;
                    }
                    if (clicked == ClaimFlags.FlagId.SHOW_WELCOME) {
                        if (button == 1) {
                            this.claim.getFlags().showWelcome = !this.claim.getFlags().showWelcome;
                            ClaimManager.getInstance().save();
                            this.rebuild();
                        } else {
                            ClaimMenuHandler.requestEditWelcome(this.viewer, this.claim, this.page);
                            this.viewer.m_6915_();
                        }
                    } else if (clicked == ClaimFlags.FlagId.SHOW_LEAVE) {
                        if (button == 1) {
                            this.claim.getFlags().showLeave = !this.claim.getFlags().showLeave;
                            ClaimManager.getInstance().save();
                            this.rebuild();
                        } else {
                            ClaimMenuHandler.requestEditLeave(this.viewer, this.claim, this.page);
                            this.viewer.m_6915_();
                        }
                    } else if (clicked == ClaimFlags.FlagId.SHOW_BORDER) {
                        this.claim.getFlags().showBorder = !this.claim.getFlags().showBorder;
                        ClaimManager.getInstance().save();
                        this.rebuild();
                    } else if (clicked == ClaimFlags.FlagId.SHOW_PARTICLES) {
                        ClaimParticleMenuHandler.open(this.viewer, this.claim, this.page);
                    } else {
                        this.claim.getFlags().toggle(clicked);
                        ClaimManager.getInstance().save();
                        this.rebuild();
                    }
                } else if (slotId == 38) {
                    this.viewer.m_5661_((Component)Component.m_237113_((String)"[Protecci\u00f3n] Miembros de la zona:").m_130940_(ChatFormatting.GRAY), false);
                    if (this.claim.getMembers().isEmpty()) {
                        this.viewer.m_5661_((Component)Component.m_237113_((String)"  (sin miembros)").m_130940_(ChatFormatting.DARK_GRAY), false);
                    } else {
                        for (int i = 0; i < this.claim.getMembers().size(); ++i) {
                            String n = i < this.claim.getMemberNames().size() ? this.claim.getMemberNames().get(i) : this.claim.getMembers().get(i).toString();
                            this.viewer.m_5661_((Component)Component.m_237113_((String)("  - " + n)).m_130940_(ChatFormatting.WHITE), false);
                        }
                    }
                } else if (slotId == 42) {
                    if (button == 1) {
                        ClaimMenuHandler.requestAddMember(this.viewer, this.claim, this.page);
                        this.viewer.m_6915_();
                    } else {
                        MemberSelectMenu.open(this.viewer, this.claim, this.page, 0);
                    }
                } else if (slotId == 40) {
                    if (this.claim.getMembers().isEmpty()) {
                        this.viewer.m_5661_((Component)Component.m_237113_((String)"[i] Esta zona no tiene miembros que quitar.").m_130940_(ChatFormatting.YELLOW), true);
                    } else {
                        ClaimMenuHandler.requestRemoveMember(this.viewer, this.claim, this.page);
                        this.viewer.m_6915_();
                    }
                } else if (slotId == 39) {
                    ClaimMenuHandler.requestBanPlayer(this.viewer, this.claim, this.page);
                    this.viewer.m_6915_();
                } else if (slotId == 41) {
                    if (this.claim.getBannedPlayers().isEmpty()) {
                        this.viewer.m_5661_((Component)Component.m_237113_((String)"[i] No hay jugadores baneados.").m_130940_(ChatFormatting.YELLOW), true);
                    } else {
                        ClaimMenuHandler.requestUnbanPlayer(this.viewer, this.claim, this.page);
                        this.viewer.m_6915_();
                    }
                } else if (slotId == 43) {
                    ClaimGroup g = ClaimManager.getInstance().getGroupOf(this.claim);
                    if (g == null) {
                        ClaimMenuHandler.requestMergeName(this.viewer, this.claim, this.page);
                        this.viewer.m_6915_();
                    } else if (this.claim.isGroupMother()) {
                        ClaimMenuHandler.requestMergeUsers(this.viewer, this.claim, this.page);
                        this.viewer.m_6915_();
                    }
                } else if (slotId == 44) {
                    ClaimGroup g = ClaimManager.getInstance().getGroupOf(this.claim);
                    if (g != null && this.claim.isGroupMother()) {
                        ClaimManager.getInstance().dissolveGroupBreaking(g.getGroupId());
                        this.viewer.m_5661_((Component)Component.m_237113_((String)"\u2714 Grupo disuelto. Las piedras solapadas se devolvieron a sus duenos.").m_130940_(ChatFormatting.GREEN), false);
                        this.rebuild();
                    }
                } else if (slotId == 49) {
                    this.viewer.m_6915_();
                } else if (slotId == 52) {
                    this.viewer.m_6915_();
                    this.viewer.f_8924_.m_129892_().m_230957_(this.viewer.m_20203_(), "claim list");
                }
            }
        }
    }

    private void performDelete() {
        ClaimTier tier = this.claim.getTier();
        Level world = this.viewer.m_9236_();
        BlockPos centre = this.claim.getCenter();
        if (tier != null && ClaimBlocks.isClaimConcreteForTier(world.m_8055_(centre).m_60734_(), tier)) {
            world.m_46961_(centre, false);
        }
        world.m_5594_(null, centre, SoundEvents.f_144243_, SoundSource.BLOCKS, 2.0f, 1.0f);
        ClaimManager.getInstance().removeClaim(world, centre);
        if (tier != null) {
            ItemStack stack = ClaimBlocks.createTierItem(tier, 1);
            if (!this.viewer.m_150109_().m_36054_(stack)) {
                this.viewer.m_36176_(stack, false);
            }
        }
        this.viewer.m_5661_((Component)Component.m_237113_((String)"\u2714 Zona eliminada. Protecci\u00f3n devuelta a tu inventario.").m_130940_(ChatFormatting.GREEN), false);
        this.viewer.m_6915_();
    }

    private int pageIndex() {
        return Math.max(0, Math.min(LAST_PAGE, this.page));
    }

    private ClaimFlags.FlagId slotToFlag(int slotIndex) {
        ClaimFlags.FlagId[] ids = PAGES[this.pageIndex()];
        int[] slots = PAGE_SLOTS[this.pageIndex()];
        for (int i = 0; i < slots.length; ++i) {
            if (slots[i] != slotIndex) continue;
            return ids[i];
        }
        return null;
    }

    public static void open(ServerPlayer player, Claim claim, int page) {
        ClaimMenuHandler.open(player, claim, page, null);
    }

    public static void open(ServerPlayer player, final Claim claim, int page, String customTitle) {
        if (claim.getGroupId() != null && !claim.isGroupMother()) {
            Claim mother = claim.getMother();
            String on = mother != null ? mother.getOwnerName() : "?";
            player.m_5661_((Component)Component.m_237113_((String)("[!] Esta piedra pertenece al grupo de " + on + ". Solo la piedra nodriza gestiona el grupo. Puedes romperla para recuperarla.")).m_130940_(ChatFormatting.YELLOW), false);
        } else {
            final int p = Math.max(0, Math.min(LAST_PAGE, page));
            ClaimGroup titleGrp = ClaimManager.getInstance().getGroupOf(claim);
            final String title = customTitle != null ? ClaimMenuHandler.truncate(customTitle, 40) : (titleGrp != null ? ClaimMenuHandler.truncate("Grupo: " + titleGrp.getName(), 40) : ClaimMenuHandler.truncate("Zona " + claim.sizeLabel() + " - " + claim.getOwnerName(), 40));
            NetworkHooks.openScreen((ServerPlayer)player, (MenuProvider)new MenuProvider(){

                public Component m_5446_() {
                    return Component.m_237113_((String)title).m_130944_(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD});
                }

                public AbstractContainerMenu m_7208_(int id, Inventory inv, Player pl) {
                    return new ClaimMenuHandler(id, inv, claim, p);
                }
            });
        }
    }

    public static void requestAddMember(ServerPlayer player, Claim claim, int returnPage) {
        pending.put(player.m_20148_(), new PendingChat(PendingType.ADD_MEMBER, claim.getClaimId(), returnPage));
        player.m_5661_((Component)Component.m_237113_((String)"[Protecci\u00f3n] Escribe el nombre del jugador a a\u00f1adir (o 'cancelar'):").m_130940_(ChatFormatting.YELLOW), false);
        player.m_5661_((Component)Component.m_237113_((String)"    No hace falta que est\u00e9 conectado. Alternativa: /claim addmember <jugador>").m_130940_(ChatFormatting.DARK_GRAY), false);
    }

    public static void requestRemoveMember(ServerPlayer player, Claim claim, int returnPage) {
        pending.put(player.m_20148_(), new PendingChat(PendingType.REMOVE_MEMBER, claim.getClaimId(), returnPage));
        StringBuilder sb = new StringBuilder();
        List<String> names = claim.getMemberNames();
        for (int i = 0; i < names.size(); ++i) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(names.get(i));
        }
        player.m_5661_((Component)Component.m_237113_((String)"[Protecci\u00f3n] Miembros: ").m_130940_(ChatFormatting.GRAY).m_7220_((Component)Component.m_237113_((String)sb.toString()).m_130940_(ChatFormatting.WHITE)), false);
        player.m_5661_((Component)Component.m_237113_((String)"[Protecci\u00f3n] Escribe el nombre del invitado a quitar (o 'cancelar'):").m_130940_(ChatFormatting.YELLOW), false);
    }

    public static void requestEditWelcome(ServerPlayer player, Claim claim, int returnPage) {
        pending.put(player.m_20148_(), new PendingChat(PendingType.EDIT_WELCOME, claim.getClaimId(), returnPage));
        player.m_5661_((Component)Component.m_237113_((String)"[Protecci\u00f3n] Escribe tu bienvenida (max 60 chars) o 'cancelar':").m_130940_(ChatFormatting.YELLOW), false);
    }

    public static void requestEditLeave(ServerPlayer player, Claim claim, int returnPage) {
        pending.put(player.m_20148_(), new PendingChat(PendingType.EDIT_LEAVE, claim.getClaimId(), returnPage));
        player.m_5661_((Component)Component.m_237113_((String)"[Protecci\u00f3n] Escribe tu mensaje de salida (max 60 chars) o 'cancelar':").m_130940_(ChatFormatting.YELLOW), false);
    }

    public static boolean hasPrompt(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        PendingChat p = pending.get(playerId);
        if (p == null) {
            return false;
        }
        if (p.isExpired()) {
            pending.remove(playerId, p);
            return false;
        }
        return true;
    }

    public static PendingChat popPrompt(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        PendingChat p = pending.remove(playerId);
        return p == null || p.isExpired() ? null : p;
    }

    public static void clearPrompt(UUID playerId) {
        if (playerId != null) {
            pending.remove(playerId);
            pendingMergeName.remove(playerId);
        }
    }

    public static void handleChat(ServerChatEvent event) {
        ServerPlayer sender = event.getPlayer();
        if (sender == null) {
            return;
        }
        String raw = event.getRawText();
        if (ChatPromptRouter.consume(sender, raw)) {
            event.setCanceled(true);
            return;
        }
        if (ChatPromptRouter.shouldSuppress(sender.m_20148_(), raw)) {
            event.setCanceled(true);
        }
    }

    public static void dispatchPrompt(ServerPlayer sender, PendingChat p, String text) {
        if (sender == null || p == null || sender.m_9232_()) {
            return;
        }
        if (ChatPromptRouter.isCancel(text)) {
            sender.m_5661_((Component)Component.m_237113_((String)"[Protecci\u00f3n] Cancelado.").m_130940_(ChatFormatting.GRAY), false);
            return;
        }
        Claim claim = ClaimMenuHandler.findClaimById(p.claimId());
        if (claim == null) {
            sender.m_5661_((Component)Component.m_237113_((String)"[x] La zona ya no existe.").m_130940_(ChatFormatting.RED), false);
            return;
        }
        switch (p.type()) {
            case ADD_MEMBER: {
                ClaimMenuHandler.handleAddMember(sender, claim, text, p.returnPage());
                break;
            }
            case REMOVE_MEMBER: {
                ClaimMenuHandler.handleRemoveMember(sender, claim, text, p.returnPage());
                break;
            }
            case EDIT_WELCOME: {
                ClaimMenuHandler.handleEditWelcome(sender, claim, text, p.returnPage());
                break;
            }
            case EDIT_LEAVE: {
                ClaimMenuHandler.handleEditLeave(sender, claim, text, p.returnPage());
                break;
            }
            case BAN_PLAYER: {
                ClaimMenuHandler.handleBanPlayer(sender, claim, text, p.returnPage());
                break;
            }
            case UNBAN_PLAYER: {
                ClaimMenuHandler.handleUnbanPlayer(sender, claim, text, p.returnPage());
                break;
            }
            case MERGE_NAME: {
                ClaimMenuHandler.handleMergeName(sender, claim, text, p.returnPage());
                break;
            }
            case MERGE_USERS: {
                ClaimMenuHandler.handleMergeUsers(sender, claim, text, p.returnPage());
            }
        }
    }

    public static void dispatchAdminTransfer(ServerPlayer op, UUID claimId, String text) {
        if (op == null || op.m_9232_()) {
            return;
        }
        if (ChatPromptRouter.isCancel(text)) {
            op.m_5661_((Component)Component.m_237113_((String)"[Protecci\u00f3n] Cancelado.").m_130940_(ChatFormatting.GRAY), false);
            return;
        }
        ClaimMenuHandler.handleAdminTransfer(op, claimId, ChatPromptRouter.extractPlayerName(text));
    }

    private static void handleAdminTransfer(ServerPlayer op, UUID claimId, String name) {
        PlayerLookup.resolveAsync(op.m_20194_(), name, target -> {
            if (!op.m_9232_()) {
                ClaimMenuHandler.applyAdminTransfer(op, claimId, name, target);
            }
        });
    }

    private static void applyAdminTransfer(ServerPlayer op, UUID claimId, String name, PlayerLookup.Resolved target) {
        Claim claim = ClaimMenuHandler.findClaimById(claimId);
        if (claim == null) {
            op.m_5661_((Component)Component.m_237113_((String)"[x] La zona ya no existe.").m_130940_(ChatFormatting.RED), false);
            return;
        }
        if (target == null) {
            op.m_5661_((Component)Component.m_237113_((String)("[x] Jugador no encontrado: " + name)).m_130940_(ChatFormatting.RED), false);
            return;
        }
        claim.setOwner(target.id(), target.name());
        claim.getMembers().clear();
        claim.getMemberNames().clear();
        ClaimManager.getInstance().save();
        op.m_5661_((Component)Component.m_237113_((String)("\u2714 Zona transferida a " + target.name() + ".")).m_130940_(ChatFormatting.GREEN), false);
        MutableComponent msg = Component.m_237113_((String)"[!] Un administrador te transfiri\u00f3 una zona ").m_130940_(ChatFormatting.YELLOW).m_7220_((Component)Component.m_237113_((String)claim.sizeLabel()).m_130944_(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD})).m_7220_((Component)Component.m_237113_((String)(" en X:" + claim.getX() + " Z:" + claim.getZ())).m_130940_(ChatFormatting.YELLOW));
        if (target.isOnline()) {
            target.online().m_5661_((Component)msg, false);
        } else {
            ClaimManager.getInstance().queueMessage(target.id(), (Component)msg);
        }
    }

    private static void handleAddMember(ServerPlayer sender, Claim claim, String name, int page) {
        ClaimMenuHandler.addMemberByName(sender, claim, name, page, true);
    }

    public static boolean addMemberByName(ServerPlayer sender, Claim claim, String name, int page, boolean reopenMenu) {
        String query = ChatPromptRouter.extractPlayerName(name);
        UUID claimId = claim.getClaimId();
        PlayerLookup.resolveAsync(sender.m_20194_(), query, target -> {
            if (sender.m_9232_()) {
                return;
            }
            Claim live = ClaimMenuHandler.findClaimById(claimId);
            if (live == null) {
                sender.m_5661_((Component)Component.m_237113_((String)"[x] La zona ya no existe.").m_130940_(ChatFormatting.RED), false);
                return;
            }
            if (target == null) {
                sender.m_5661_((Component)Component.m_237113_((String)("[x] No encuentro al jugador \"" + query + "\". Revisa el nombre; si nunca ha entrado al servidor, no puedo resolverlo.")).m_130940_(ChatFormatting.RED), false);
            } else {
                ClaimMenuHandler.addMemberResolved(sender, live, target);
            }
            if (reopenMenu) {
                ClaimMenuHandler.open(sender, live, page);
            }
        });
        return true;
    }

    public static boolean addMemberResolved(ServerPlayer sender, Claim claim, PlayerLookup.Resolved target) {
        if (claim.isOwner(target.id())) {
            sender.m_5661_((Component)Component.m_237113_((String)"[x] Ese jugador ya es el due\u00f1o.").m_130940_(ChatFormatting.RED), false);
            return false;
        }
        if (claim.isMember(target.id())) {
            sender.m_5661_((Component)Component.m_237113_((String)("[i] " + target.name() + " ya es miembro de esta zona.")).m_130940_(ChatFormatting.YELLOW), false);
            return false;
        }
        if (claim.isBanned(target.id())) {
            claim.unbanPlayer(target.id());
            sender.m_5661_((Component)Component.m_237113_((String)("[i] " + target.name() + " estaba baneado de la zona; se le quit\u00f3 el baneo.")).m_130940_(ChatFormatting.YELLOW), false);
        }
        claim.addMember(target.id(), target.name());
        ClaimManager.getInstance().save();
        sender.m_5661_((Component)Component.m_237113_((String)("\u2714 " + target.name() + " agregado como miembro de la zona.")).m_130940_(ChatFormatting.GREEN), false);
        MutableComponent notice = Component.m_237113_((String)("[Protecci\u00f3n] Eres miembro de la zona de " + sender.m_7755_().getString())).m_130940_(ChatFormatting.AQUA);
        if (target.isOnline()) {
            target.online().m_5661_((Component)notice, false);
        } else {
            ClaimManager.getInstance().queueMessage(target.id(), (Component)notice);
        }
        return true;
    }

    private static void handleRemoveMember(ServerPlayer sender, Claim claim, String name, int page) {
        ClaimMenuHandler.removeMemberByName(sender, claim, name, page, true);
    }

    public static boolean removeMemberByName(ServerPlayer sender, Claim claim, String name, int page, boolean reopenMenu) {
        ServerPlayer removed;
        PlayerLookup.Resolved resolved;
        String query = ChatPromptRouter.extractPlayerName(name);
        UUID targetId = null;
        String resolvedName = query;
        for (int i = 0; i < claim.getMemberNames().size() && i < claim.getMembers().size(); ++i) {
            if (!claim.getMemberNames().get(i).equalsIgnoreCase(query)) continue;
            targetId = claim.getMembers().get(i);
            resolvedName = claim.getMemberNames().get(i);
            break;
        }
        if (targetId == null && (resolved = PlayerLookup.resolve(sender.m_20194_(), query)) != null && claim.isMember(resolved.id())) {
            targetId = resolved.id();
            resolvedName = resolved.name();
        }
        if (targetId == null) {
            sender.m_5661_((Component)Component.m_237113_((String)("[x] " + query + " no es miembro de esta zona.")).m_130940_(ChatFormatting.RED), false);
            if (reopenMenu) {
                ClaimMenuHandler.open(sender, claim, page);
            }
            return false;
        }
        claim.removeMember(targetId);
        ClaimManager.getInstance().save();
        sender.m_5661_((Component)Component.m_237113_((String)("\u2714 " + resolvedName + " fue eliminado de la zona.")).m_130940_(ChatFormatting.GREEN), false);
        MutableComponent notice = Component.m_237113_((String)("[Protecci\u00f3n] Ya no eres miembro de la zona de " + sender.m_7755_().getString())).m_130940_(ChatFormatting.YELLOW);
        ServerPlayer serverPlayer = removed = sender.m_20194_() == null ? null : sender.m_20194_().m_6846_().m_11259_(targetId);
        if (removed != null) {
            removed.m_5661_((Component)notice, false);
        } else {
            ClaimManager.getInstance().queueMessage(targetId, (Component)notice);
        }
        if (reopenMenu) {
            ClaimMenuHandler.open(sender, claim, page);
        }
        return true;
    }

    private static void handleEditWelcome(ServerPlayer sender, Claim claim, String text, int page) {
        if (text.length() > 60) {
            text = text.substring(0, 60);
        }
        claim.getFlags().welcomeMessage = text;
        claim.getFlags().showWelcome = !text.isBlank();
        ClaimManager.getInstance().save();
        sender.m_5661_((Component)Component.m_237113_((String)"\u2714 Bienvenida guardada.").m_130940_(ChatFormatting.GREEN), false);
        ClaimMenuHandler.open(sender, claim, page);
    }

    private static void handleEditLeave(ServerPlayer sender, Claim claim, String text, int page) {
        if (text.length() > 60) {
            text = text.substring(0, 60);
        }
        claim.getFlags().leaveMessage = text;
        claim.getFlags().showLeave = !text.isBlank();
        ClaimManager.getInstance().save();
        sender.m_5661_((Component)Component.m_237113_((String)"\u2714 Mensaje de salida guardado.").m_130940_(ChatFormatting.GREEN), false);
        ClaimMenuHandler.open(sender, claim, page);
    }

    public static void requestMergeName(ServerPlayer player, Claim claim, int returnPage) {
        pending.put(player.m_20148_(), new PendingChat(PendingType.MERGE_NAME, claim.getClaimId(), returnPage));
        player.m_5661_((Component)Component.m_237113_((String)"[Grupo] Escribe el NOMBRE de la zona unida (o 'cancelar'):").m_130940_(ChatFormatting.LIGHT_PURPLE), false);
    }

    public static void requestMergeUsers(ServerPlayer player, Claim claim, int returnPage) {
        pending.put(player.m_20148_(), new PendingChat(PendingType.MERGE_USERS, claim.getClaimId(), returnPage));
        player.m_5661_((Component)Component.m_237113_((String)"[Grupo] Escribe el/los jugadores a invitar (separados por espacio) o 'cancelar':").m_130940_(ChatFormatting.LIGHT_PURPLE), false);
    }

    private static void handleMergeName(ServerPlayer sender, Claim claim, String text, int page) {
        String name = text.length() > 32 ? text.substring(0, 32) : text;
        pendingMergeName.put(sender.m_20148_(), name);
        pending.put(sender.m_20148_(), new PendingChat(PendingType.MERGE_USERS, claim.getClaimId(), page));
        sender.m_5661_((Component)Component.m_237113_((String)("[Grupo] Nombre: \"" + name + "\". Ahora escribe el/los jugadores a invitar (separados por espacio):")).m_130940_(ChatFormatting.LIGHT_PURPLE), false);
    }

    private static void handleMergeUsers(ServerPlayer sender, Claim claim, String text, int page) {
        ClaimManager mgr = ClaimManager.getInstance();
        ClaimGroup g = mgr.getGroupOf(claim);
        if (g == null) {
            String name = pendingMergeName.getOrDefault(sender.m_20148_(), "Grupo");
            g = mgr.createGroup(claim, name);
        }
        pendingMergeName.remove(sender.m_20148_());
        String[] parts = ChatPromptRouter.sanitize(text).split("[ ,]+");
        int sent = 0;
        for (String raw : parts) {
            String pname = raw.trim();
            if (pname.isEmpty()) continue;
            ServerPlayer target = sender.f_8924_.m_6846_().m_11255_(pname);
            if (target == null) {
                sender.m_5661_((Component)Component.m_237113_((String)("[x] " + pname + " no esta en linea (debe estar conectado para invitarlo).")).m_130940_(ChatFormatting.RED), false);
                continue;
            }
            if (target.m_20148_().equals(sender.m_20148_())) continue;
            if (g.isRegistered(target.m_20148_())) {
                sender.m_5661_((Component)Component.m_237113_((String)("[i] " + target.m_7755_().getString() + " ya esta en el grupo.")).m_130940_(ChatFormatting.GRAY), false);
                continue;
            }
            String code = ClaimMenuHandler.genCode();
            invites.put(code, new MergeInvite(code, g.getGroupId(), target.m_20148_(), sender.m_7755_().getString(), g.getName()));
            ClaimMenuHandler.sendInvite(target, sender.m_7755_().getString(), g.getName(), code);
            ++sent;
        }
        if (sent > 0) {
            sender.m_5661_((Component)Component.m_237113_((String)("\u2714 Invitacion enviada a " + sent + " jugador(es). Grupo: \"" + g.getName() + "\".")).m_130940_(ChatFormatting.GREEN), false);
        }
        ClaimMenuHandler.open(sender, claim, page);
    }

    private static void sendInvite(ServerPlayer target, String inviterName, String groupName, String code) {
        target.m_5661_((Component)Component.m_237113_((String)("[Grupo] " + inviterName + " te invita a unir tu proteccion al grupo \"" + groupName + "\".")).m_130940_(ChatFormatting.AQUA), false);
        MutableComponent accept = Component.m_237113_((String)" [\u2714 ACEPTAR] ").m_130948_(Style.f_131099_.m_131140_(ChatFormatting.GREEN).m_131136_(Boolean.valueOf(true)).m_131142_(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claimmerge accept " + code)));
        MutableComponent reject = Component.m_237113_((String)"[\u2718 RECHAZAR]").m_130948_(Style.f_131099_.m_131140_(ChatFormatting.RED).m_131136_(Boolean.valueOf(true)).m_131142_(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claimmerge reject " + code)));
        target.m_5661_((Component)Component.m_237113_((String)"").m_7220_((Component)accept).m_7220_((Component)reject), false);
    }

    public static void acceptMerge(ServerPlayer target, String code) {
        MergeInvite inv = invites.remove(code);
        if (inv != null && target.m_20148_().equals(inv.targetId())) {
            ClaimManager mgr = ClaimManager.getInstance();
            ClaimGroup g = mgr.getGroup(inv.groupId());
            if (g == null) {
                target.m_5661_((Component)Component.m_237113_((String)"[x] El grupo ya no existe.").m_130940_(ChatFormatting.RED), false);
            } else {
                ServerPlayer inviter;
                mgr.registerPlayer(g.getGroupId(), target.m_20148_());
                target.m_5661_((Component)Component.m_237113_((String)("\u2714 Te uniste al grupo \"" + g.getName() + "\". Ahora tus piedras colocadas dentro de esa zona se uniran.")).m_130940_(ChatFormatting.GREEN), false);
                MutableComponent note = Component.m_237113_((String)(target.m_7755_().getString() + " acepto unirse al grupo \"" + g.getName() + "\".")).m_130940_(ChatFormatting.GREEN);
                ServerPlayer serverPlayer = inviter = g.getMotherOwnerId() == null ? null : target.f_8924_.m_6846_().m_11259_(g.getMotherOwnerId());
                if (inviter != null) {
                    inviter.m_5661_((Component)note, false);
                } else if (g.getMotherOwnerId() != null) {
                    mgr.queueMessage(g.getMotherOwnerId(), (Component)note);
                }
            }
        } else {
            target.m_5661_((Component)Component.m_237113_((String)"[x] Invitacion no valida o expirada.").m_130940_(ChatFormatting.RED), false);
        }
    }

    public static void rejectMerge(ServerPlayer target, String code) {
        MergeInvite inv = invites.remove(code);
        if (inv != null && target.m_20148_().equals(inv.targetId())) {
            target.m_5661_((Component)Component.m_237113_((String)"[i] Rechazaste la invitacion de union.").m_130940_(ChatFormatting.GRAY), false);
            ClaimGroup g = ClaimManager.getInstance().getGroup(inv.groupId());
            if (g != null && g.getMotherOwnerId() != null) {
                MutableComponent note = Component.m_237113_((String)(target.m_7755_().getString() + " rechazo unirse al grupo \"" + g.getName() + "\".")).m_130940_(ChatFormatting.YELLOW);
                ServerPlayer inviter = target.f_8924_.m_6846_().m_11259_(g.getMotherOwnerId());
                if (inviter != null) {
                    inviter.m_5661_((Component)note, false);
                } else {
                    ClaimManager.getInstance().queueMessage(g.getMotherOwnerId(), (Component)note);
                }
            }
        } else {
            target.m_5661_((Component)Component.m_237113_((String)"[x] Invitacion no valida o expirada.").m_130940_(ChatFormatting.RED), false);
        }
    }

    public static void leaveMerge(ServerPlayer player) {
        ClaimManager mgr = ClaimManager.getInstance();
        ClaimGroup g = mgr.getGroupByRegistered(player.m_20148_());
        if (g == null) {
            player.m_5661_((Component)Component.m_237113_((String)"[!] No estas en ningun grupo.").m_130940_(ChatFormatting.YELLOW), false);
        } else {
            boolean wasMother = player.m_20148_().equals(g.getMotherOwnerId());
            String name = g.getName();
            mgr.leaveGroupBreaking(g.getGroupId(), player.m_20148_());
            player.m_5661_((Component)Component.m_237113_((String)(wasMother ? "\u2714 Disolviste el grupo \"" + name + "\"." : "\u2714 Saliste del grupo \"" + name + "\". Tus piedras vuelven a ser independientes.")).m_130940_(ChatFormatting.GREEN), false);
        }
    }

    private static String genCode() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static Claim findClaimById(UUID id) {
        for (Claim c : ClaimManager.getInstance().getAllClaims()) {
            if (!c.getClaimId().equals(id)) continue;
            return c;
        }
        return null;
    }

    public record PendingChat(PendingType type, UUID claimId, int returnPage, long createdAtMillis) {
        public PendingChat(PendingType type, UUID claimId, int returnPage) {
            this(type, claimId, returnPage, System.currentTimeMillis());
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - this.createdAtMillis > 90000L;
        }
    }

    public static enum PendingType {
        ADD_MEMBER,
        EDIT_WELCOME,
        EDIT_LEAVE,
        BAN_PLAYER,
        UNBAN_PLAYER,
        REMOVE_MEMBER,
        MERGE_NAME,
        MERGE_USERS;

    }

    public record MergeInvite(String code, UUID groupId, UUID targetId, String inviterName, String groupName) {
    }
}

