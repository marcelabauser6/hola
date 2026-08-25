package com.athensmc.fsshopkeepers;

import com.athensmc.fsshopkeepers.config.ShopConfig;
import com.athensmc.fsshopkeepers.money.Cash;
import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Shops run by NPCs, with prices in Fantastic Cash.
 *
 * <p>A Forge mod on both sides. The server owns the shops, the money and every decision about whether a trade may
 * happen; the client owns the editor screens an administrator uses to set a shop up. Nothing a client sends is
 * trusted - it says which shop it was editing and what it wants the trades to be, and the server checks both.</p>
 *
 * <p>Written for Mohist, which runs Forge mods and Bukkit plugins together. That matters in exactly one place,
 * {@link #hasPermission}, which will use a permissions plugin when one is present and fall back to operator level
 * when it is not, so the same jar behaves sensibly on plain Forge.</p>
 */
@Mod(FantasticShopkeepers.MOD_ID)
public final class FantasticShopkeepers {

    public static final String MOD_ID = "fsshopkeepers";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Operator level that counts as staff when there is no permissions plugin. */
    private static final int STAFF_OP_LEVEL = 2;

    /** Permission nodes, matching the shape Shopkeepers used so existing permission setups read familiarly. */
    public static final class Perms {
        public static final String ADMIN = "shopkeeper.admin";
        public static final String CREATE_ADMIN = "shopkeeper.admin.create";
        public static final String CREATE_PLAYER = "shopkeeper.player.create";
        public static final String EDIT_OWN = "shopkeeper.edit.own";
        public static final String EDIT_OTHERS = "shopkeeper.edit.others";
        public static final String REMOVE_OWN = "shopkeeper.remove.own";
        public static final String REMOVE_OTHERS = "shopkeeper.remove.others";
        public static final String LIST_OWN = "shopkeeper.list.own";
        public static final String LIST_OTHERS = "shopkeeper.list.others";
        public static final String BYPASS_LIMIT = "shopkeeper.bypass.limit";
        public static final String RELOAD = "shopkeeper.reload";

        private Perms() {
        }
    }

    /**
     * Whether a Bukkit-style permission check is reachable.
     *
     * <p>Resolved once. On Mohist the Bukkit classes are present and a permissions plugin answers the question
     * properly; on plain Forge they are absent and every check falls back to operator level. Looking this up per
     * check would put reflection on the path of every shop interaction.</p>
     */
    private static Method bukkitHasPermission;
    private static Method bukkitGetPlayer;
    private static boolean bukkitChecked;

    public FantasticShopkeepers() {
        net.minecraftforge.eventbus.api.IEventBus modEventBus =
                FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::setup);
        com.athensmc.fsshopkeepers.menu.ModMenus.register(modEventBus);
        com.athensmc.fsshopkeepers.item.ModItems.register(modEventBus);
        // Registered in the constructor rather than in setup: the channel must exist before any client can
        // negotiate its version, and setup runs after that negotiation for a client joining a live server.
        com.athensmc.fsshopkeepers.net.Net.register();
    }

    private void setup(FMLCommonSetupEvent event) {
        // Deferred to the work queue: config and the currency bridge are shared state, and setup runs in
        // parallel with other mods' setup by default.
        event.enqueueWork(() -> {
            ShopConfig.load();
            Cash.bind();
            LOGGER.info("Fantastic Shopkeepers listo. Abre el editor con /Fskeepers editar.");
        });
    }

    /**
     * Whether a player holds a permission.
     *
     * <p>Asks a Bukkit permissions plugin first, so a server that already manages permissions keeps managing them.
     * With no plugin, staff nodes fall back to operator level, which is the only answer available on plain Forge and
     * is deliberately strict: an unrecognised node is denied rather than allowed.</p>
     */
    public static boolean hasPermission(ServerPlayer player, String node) {
        if (player == null || node == null || node.isBlank()) {
            return false;
        }
        if (player.hasPermissions(STAFF_OP_LEVEL)) {
            return true;
        }
        Boolean fromPlugin = askBukkit(player, node);
        return fromPlugin != null && fromPlugin;
    }

    /**
     * Asks Bukkit, returning null when Bukkit is not there.
     *
     * <p>All by reflection, because this mod must not have a compile-time dependency on Bukkit: importing it would
     * make the jar refuse to load on the plain Forge servers it is also meant to run on.</p>
     */
    private static Boolean askBukkit(ServerPlayer player, String node) {
        if (!bukkitChecked) {
            bukkitChecked = true;
            try {
                Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
                bukkitGetPlayer = bukkit.getMethod("getPlayer", java.util.UUID.class);
                Class<?> permissible = Class.forName("org.bukkit.permissions.Permissible");
                bukkitHasPermission = permissible.getMethod("hasPermission", String.class);
                LOGGER.info("Servidor hibrido detectado: los permisos se consultaran a Bukkit.");
            } catch (ReflectiveOperationException noBukkit) {
                bukkitGetPlayer = null;
                bukkitHasPermission = null;
            }
        }
        if (bukkitGetPlayer == null || bukkitHasPermission == null) {
            return null;
        }
        try {
            Object bukkitPlayer = bukkitGetPlayer.invoke(null, player.getUUID());
            if (bukkitPlayer == null) {
                return null;
            }
            return (Boolean) bukkitHasPermission.invoke(bukkitPlayer, node);
        } catch (ReflectiveOperationException | RuntimeException failed) {
            return null;
        }
    }

    /** True when the player counts as staff for shop administration. */
    public static boolean isStaff(ServerPlayer player) {
        return hasPermission(player, Perms.ADMIN);
    }
}
