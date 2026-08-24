package de.z0rdak.yawp.wand;

import com.mojang.brigadier.context.CommandContext;

import de.z0rdak.yawp.api.core.ILevelRegionApi;
import de.z0rdak.yawp.api.core.RegionManager;
import de.z0rdak.yawp.commands.WandAreaUpdater;
import de.z0rdak.yawp.core.area.IMarkableArea;
import de.z0rdak.yawp.core.region.IMarkableRegion;
import de.z0rdak.yawp.core.stick.MarkerStick;
import de.z0rdak.yawp.util.LocalRegions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Moves an existing region's boundary to whatever the wand has marked, keeping the region itself.
 *
 * <p>The region is not deleted and remade. Its flags, its members and groups, its priority, its parent and
 * children, its teleport anchors - all of it stays; only the shape changes. Remaking it would have been far
 * less code and would have quietly thrown away every setting on it, which for a region that has been in use
 * is the whole value of the thing.</p>
 *
 * <p>The update goes through YAWP's own {@code RegionCommands.updateArea}, which does more than assign a
 * field: it fires the cancellable {@code RegionEvent.UpdateArea} so other mods and its own handlers see the
 * change, refuses the move when the parent region would no longer contain the new area, and raises the
 * region's priority when it needs to stay above what it overlaps. Reaching it needs reflection, since it is
 * private, and that is worth it - reimplementing those three behaviours is how a region resized with the rod
 * ends up in a state its own commands would never have allowed.</p>
 */
public final class WandAreaEdit {

    private WandAreaEdit() {
    }

    /** The result of an attempt, so the command can report it without knowing the details. */
    public sealed interface Result {
        record Applied(int code, boolean usedYawpPath) implements Result {
        }

        record Failed(String reason) implements Result {
        }
    }

    /** The local region names in a dimension, for the command's completion. */
    public static List<String> regionNames(ResourceKey<Level> dimension) {
        Optional<ILevelRegionApi> api = RegionManager.get().getDimRegionApi(dimension);
        if (api.isEmpty()) {
            return List.of();
        }
        return api.get().getAllLocalRegions().stream()
                .map(IMarkableRegion::getName)
                .sorted(String::compareToIgnoreCase)
                .toList();
    }

    /**
     * Applies the rod's marked area to a named region.
     *
     * <p>The shape has to match. A {@code CuboidRegion} holding a sphere's area is not a region YAWP can
     * write back out - its classes are typed to their area - so a mismatch is refused with the reason rather
     * than half-applied. Changing shape means creating a new region, and this command's promise is that the
     * existing one survives.</p>
     */
    public static Result apply(CommandContext<CommandSourceStack> context, ServerPlayer player,
                               ItemStack wand, String regionName) {
        ResourceKey<Level> dimension = player.level().dimension();
        Optional<ILevelRegionApi> api = RegionManager.get().getDimRegionApi(dimension);
        if (api.isEmpty()) {
            return new Result.Failed("YAWP no tiene datos de esta dimensión.");
        }
        Optional<IMarkableRegion> found = api.get().getLocalRegion(regionName);
        if (found.isEmpty()) {
            return new Result.Failed("Aquí no hay ninguna zona llamada '" + regionName + "'.");
        }
        IMarkableRegion region = found.get();

        WandShape shape = MarkerData.shapeOf(wand);
        if (shape == null) {
            return new Result.Failed("Esa vara no tiene una forma que reconozca.");
        }
        int marked = MarkerData.corners(wand).size();
        if (!MarkerData.isComplete(shape, marked)) {
            return new Result.Failed("Falta marcar la zona: " + RegionWand.progress(shape, marked));
        }

        IMarkableArea area = areaFromWand(wand);
        if (area == null) {
            return new Result.Failed("No pude construir el área con lo que hay marcado en la vara.");
        }

        if (region.getAreaType() != area.getAreaType()) {
            return new Result.Failed("La zona '" + regionName + "' es de tipo "
                    + region.getAreaType().name().toLowerCase(java.util.Locale.ROOT)
                    + " y la vara está en " + shape.label().toLowerCase(java.util.Locale.ROOT)
                    + ". Usa una vara de la misma forma para no perder la zona.");
        }

        return update(context, region, area, dimension);
    }

    /**
     * Builds the area YAWP would build from the rod.
     *
     * <p>Through {@code LocalRegions.areaFrom}, by handing it a {@code MarkerStick} made from the rod's own
     * tag. The tag is written in YAWP's format precisely so this works: the geometry, including its rounding
     * for spheres and cylinders, is YAWP's rather than a second implementation that might disagree by a
     * block.</p>
     */
    private static IMarkableArea areaFromWand(ItemStack wand) {
        CompoundTag tag = MarkerData.stickTag(wand);
        if (tag == null) {
            return null;
        }
        try {
            return LocalRegions.areaFrom(new MarkerStick(tag));
        } catch (RuntimeException notAnArea) {
            WandLog.LOGGER.debug("Could not build an area from the wand: {}", notAnArea.toString());
            return null;
        }
    }

    private static Result update(CommandContext<CommandSourceStack> context, IMarkableRegion region,
                                 IMarkableArea area, ResourceKey<Level> dimension) {
        if (WandAreaUpdater.isAvailable()) {
            try {
                int code = WandAreaUpdater.update(context, region, area);
                RegionManager.get().save(dimension);
                return new Result.Applied(code, true);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                WandLog.LOGGER.warn("YAWP's updateArea refused the call, assigning the area directly",
                        failure);
            }
        }

        // Fallback. Assigns the area and saves, which is the part that matters, but skips the parent
        // containment check and the priority adjustment YAWP's own path performs. Reported back so the
        // player is told rather than left to find out.
        try {
            region.setArea(area);
            RegionManager.get().save(dimension);
            return new Result.Applied(1, false);
        } catch (RuntimeException failure) {
            WandLog.LOGGER.error("Could not set the region's area", failure);
            return new Result.Failed("No pude cambiar el área de la zona. Mira el log del servidor.");
        }
    }
}
