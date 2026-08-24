package de.z0rdak.yawp.commands;

import com.mojang.brigadier.context.CommandContext;

import de.z0rdak.yawp.core.region.IProtectedRegion;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;

/**
 * Lets the wand call YAWP's region creation.
 *
 * <p>{@code DimensionCommands} is package-private, so its public creation methods cannot be reached from
 * {@code de.z0rdak.yawp.wand}. This sits in the same package and forwards to them.</p>
 *
 * <p>A bridge rather than a reimplementation, and that is the point. Building the region in the wand's own
 * code would mean repeating the name validation, the parent linkage, the default flags from config, the
 * feedback message and the save - and repeating them is how a region created by the rod ends up subtly
 * different from one created by hand. The wand works out two coordinates; everything after that is
 * YAWP's.</p>
 *
 * <p>A new file in YAWP's package, not an edit to one of its classes. Nothing existing is touched.</p>
 */
public final class WandRegionCreator {

    private WandRegionCreator() {
    }

    /** Creates a cuboid region between two corners, exactly as YAWP's own create would. */
    public static int cuboid(CommandContext<CommandSourceStack> context, String name,
                             BlockPos first, BlockPos second, IProtectedRegion parent) {
        return DimensionCommands.createCuboidRegion(context, name, first, second, parent);
    }

    /** Creates a sphere region from a centre and a radius, exactly as YAWP's own create would. */
    public static int sphere(CommandContext<CommandSourceStack> context, String name,
                            BlockPos centre, int radius, IProtectedRegion parent) {
        return DimensionCommands.createSphereRegion(context, name, centre, radius, parent);
    }
}
