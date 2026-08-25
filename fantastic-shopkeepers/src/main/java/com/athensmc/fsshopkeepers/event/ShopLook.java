package com.athensmc.fsshopkeepers.event;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;
import com.athensmc.fsshopkeepers.shop.ShopRegistry;
import com.athensmc.fsshopkeepers.shop.ShopSpawner;
import com.athensmc.fsshopkeepers.shop.Shopkeeper;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Turns shopkeepers to face whoever is nearest, and keeps them where they were put.
 *
 * <p>The mob's own AI stays switched off. That is deliberate: turning it back on to get head tracking would also turn on
 * everything else, and "everything else" differs per creature - a villager does not steer with the goals a sheep uses, it
 * runs on a brain that would send it off to sleep, to work and to wander. Driving the rotation from here works the same way
 * for every creature in the game, including ones added by other mods, and cannot make any of them walk away.</p>
 *
 * <p>Rotation is eased a few degrees per tick rather than snapped, so a shopkeeper turns its head to follow a customer
 * instead of flicking to face them. Idle animation is untouched and keeps playing.</p>
 */
@Mod.EventBusSubscriber(modid = FantasticShopkeepers.MOD_ID)
public final class ShopLook {

    /** How far away a customer is noticed. Beyond this the shopkeeper is left as it was. */
    private static final double LOOK_RANGE = 10.0D;

    /** Degrees a shopkeeper may turn in one tick. Enough to keep up, slow enough to read as a turn. */
    private static final float MAX_TURN_PER_TICK = 12.0F;

    /** How far a shopkeeper may drift before it is put back. */
    private static final double DRIFT_TOLERANCE = 1.25D;

    /** Ticks between drift checks. Drift is slow, so this need not run every tick. */
    private static final int DRIFT_INTERVAL_TICKS = 20;

    private static int ticks;

    private ShopLook() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null || server.getPlayerList().getPlayerCount() == 0) {
            return;
        }
        boolean checkDrift = ++ticks >= DRIFT_INTERVAL_TICKS;
        if (checkDrift) {
            ticks = 0;
        }

        ShopRegistry registry = ShopRegistry.get(server);
        for (Shopkeeper shop : registry.all()) {
            if (!shop.objectKind().hasEntity()) {
                continue;
            }
            ServerLevel level = server.getLevel(shop.level());
            if (level == null || !level.isLoaded(shop.pos())) {
                continue;
            }
            Entity body = ShopSpawner.findEntity(level, shop);
            if (body == null) {
                continue;
            }
            if (checkDrift) {
                returnIfDrifted(shop, body);
            }
            faceNearestPlayer(level, body);
        }
    }

    /**
     * Puts a shopkeeper back if something has shoved it.
     *
     * <p>A frozen mob can still be pushed by water, a piston or a crowd. Left alone it creeps away from the shop it belongs
     * to, and eventually out of reach of the block the shop is recorded at.</p>
     */
    private static void returnIfDrifted(Shopkeeper shop, Entity body) {
        BlockPos home = shop.pos();
        double homeX = home.getX() + 0.5D;
        double homeZ = home.getZ() + 0.5D;
        double dx = body.getX() - homeX;
        double dy = body.getY() - home.getY();
        double dz = body.getZ() - homeZ;
        if (dx * dx + dy * dy + dz * dz <= DRIFT_TOLERANCE * DRIFT_TOLERANCE) {
            return;
        }
        body.teleportTo(homeX, home.getY(), homeZ);
        body.setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    /** Eases the shopkeeper's head and body round towards the nearest customer. */
    private static void faceNearestPlayer(ServerLevel level, Entity body) {
        Player customer = level.getNearestPlayer(body, LOOK_RANGE);
        if (customer == null) {
            return;
        }
        double dx = customer.getX() - body.getX();
        double dz = customer.getZ() - body.getZ();
        double dy = customer.getEyeY() - body.getEyeY();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 0.05D) {
            // Standing on top of the shopkeeper: any yaw would be arbitrary, so only the pitch is worth setting.
            return;
        }

        float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0F / Math.PI)) - 90.0F;
        float targetPitch = (float) (-(Mth.atan2(dy, flat) * (180.0F / Math.PI)));

        float yaw = approach(body.getYRot(), targetYaw, MAX_TURN_PER_TICK);
        float pitch = approach(body.getXRot(), targetPitch, MAX_TURN_PER_TICK);

        body.setYRot(yaw);
        body.setXRot(pitch);
        body.setYHeadRot(yaw);
        if (body instanceof Mob mob) {
            // Body and head together, so a shopkeeper does not end up looking over its own shoulder.
            mob.setYBodyRot(yaw);
        }
    }

    /**
     * Moves an angle towards another by at most {@code maxStep} degrees.
     *
     * <p>Through {@link Mth#wrapDegrees} so turning from 179 to -179 is two degrees the short way, not 358 the long way
     * round.</p>
     */
    private static float approach(float current, float target, float maxStep) {
        float difference = Mth.wrapDegrees(target - current);
        float step = Mth.clamp(difference, -maxStep, maxStep);
        return Mth.wrapDegrees(current + step);
    }
}
