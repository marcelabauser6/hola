package de.z0rdak.yawp.commands;

import com.mojang.brigadier.context.CommandContext;

import de.z0rdak.yawp.core.area.IMarkableArea;
import de.z0rdak.yawp.core.region.IMarkableRegion;

import net.minecraft.commands.CommandSourceStack;

import java.lang.reflect.Method;

/**
 * Lets the wand reuse YAWP's own area update.
 *
 * <p>{@code RegionCommands} is package-private and its {@code updateArea} is private on top of that, so it
 * can be reached neither by import nor by same-package access. This sits in the package so the class itself
 * resolves, and reflection covers the rest.</p>
 *
 * <p>Worth the indirection because {@code updateArea} does three things that matter and are easy to miss:
 * it fires the cancellable {@code RegionEvent.UpdateArea} so handlers and other mods see the change, it
 * refuses the move when the parent region would no longer contain the new area, and it raises the region's
 * priority when it has to stay above what it now overlaps. Assigning the area directly skips all three, and
 * a region resized that way can end up in a state YAWP's own commands would have refused.</p>
 */
public final class WandAreaUpdater {

    private static Method updateArea;
    private static boolean resolved;

    private WandAreaUpdater() {
    }

    /** True when YAWP's own update is available, so callers can say which path was taken. */
    public static boolean isAvailable() {
        return resolve() != null;
    }

    /**
     * Runs YAWP's area update.
     *
     * @return the command's own return code.
     * @throws ReflectiveOperationException when the method is missing or refuses the call, so the caller
     *                                      can fall back rather than have a failure look like a success.
     */
    public static int update(CommandContext<CommandSourceStack> context, IMarkableRegion region,
                             IMarkableArea area) throws ReflectiveOperationException {
        Method method = resolve();
        if (method == null) {
            throw new NoSuchMethodException("RegionCommands.updateArea is not available");
        }
        Object code = method.invoke(null, context, region, area);
        return code instanceof Integer value ? value : 1;
    }

    private static Method resolve() {
        if (resolved) {
            return updateArea;
        }
        resolved = true;
        try {
            Method method = RegionCommands.class.getDeclaredMethod("updateArea",
                    CommandContext.class, IMarkableRegion.class, IMarkableArea.class);
            method.setAccessible(true);
            updateArea = method;
        } catch (ReflectiveOperationException | RuntimeException missing) {
            updateArea = null;
        }
        return updateArea;
    }
}
