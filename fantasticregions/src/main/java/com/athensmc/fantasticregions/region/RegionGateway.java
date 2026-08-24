package com.athensmc.fantasticregions.region;

import com.athensmc.fantasticregions.FantasticRegions;
import com.athensmc.fantasticregions.selection.Shape;

import de.z0rdak.yawp.api.core.ILevelRegionApi;
import de.z0rdak.yawp.api.core.RegionManager;
import de.z0rdak.yawp.core.area.CuboidArea;
import de.z0rdak.yawp.core.area.RegionAnchors;
import de.z0rdak.yawp.core.area.SphereArea;
import de.z0rdak.yawp.core.area.VerticalCylinderArea;
import de.z0rdak.yawp.core.flag.FlagState;
import de.z0rdak.yawp.core.flag.IFlag;
import de.z0rdak.yawp.core.group.PlayerContainer;
import de.z0rdak.yawp.core.region.CuboidRegion;
import de.z0rdak.yawp.core.region.CylinderRegion;
import de.z0rdak.yawp.core.region.IMarkableRegion;
import de.z0rdak.yawp.core.region.SphereRegion;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The one place this mod talks to Yet Another World Protector.
 *
 * <p>Everything else - commands, packets, screens - goes through here. YAWP's own types stop at this
 * boundary and {@link RegionSnapshot} goes out the other side. That is not ceremony: YAWP is a separate
 * jar on a separate release cycle, and when its API shifts the compiler should point at this file
 * rather than at forty call sites across the client.</p>
 *
 * <p>Server side only. Every method here assumes it is running where the region store lives, because
 * the store is authoritative and the client only ever holds a copy.</p>
 */
public final class RegionGateway {

    /** The group YAWP puts trusted players in. Its own commands use these two names. */
    public static final String GROUP_MEMBER = "members";
    public static final String GROUP_OWNER = "owners";

    private RegionGateway() {
    }

    private static Optional<ILevelRegionApi> api(ResourceKey<Level> dimension) {
        return RegionManager.get().getDimRegionApi(dimension);
    }

    /** Every region in one dimension, sorted by name so the list does not reshuffle between opens. */
    public static List<IMarkableRegion> regionsIn(ResourceKey<Level> dimension) {
        Optional<ILevelRegionApi> api = api(dimension);
        if (api.isEmpty()) {
            return List.of();
        }
        List<IMarkableRegion> regions = new ArrayList<>(api.get().getAllLocalRegions());
        regions.sort((left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        return regions;
    }

    public static Optional<IMarkableRegion> region(ResourceKey<Level> dimension, String name) {
        return api(dimension).flatMap(level -> level.getLocalRegion(name));
    }

    public static boolean exists(ResourceKey<Level> dimension, String name) {
        return api(dimension).map(level -> level.hasLocal(name)).orElse(false);
    }

    /** The names in a dimension, for the deletion command's completion. */
    public static List<String> regionNames(ResourceKey<Level> dimension) {
        return regionsIn(dimension).stream().map(IMarkableRegion::getName).toList();
    }

    /**
     * Builds a region from a finished selection and files it with YAWP.
     *
     * <p>The creating player is passed to YAWP's constructors, which is what puts them in the owners
     * group - so whoever marks a region out can edit it afterwards without a second step.</p>
     *
     * @return the new region, or empty when the name is taken or the shape could not be built.
     */
    public static Optional<IMarkableRegion> create(ServerPlayer player, String name, Shape shape,
                                                   BlockPos first, BlockPos second) {
        ResourceKey<Level> dimension = player.level().dimension();
        Optional<ILevelRegionApi> api = api(dimension);
        if (api.isEmpty()) {
            FantasticRegions.LOGGER.warn("YAWP has no region store for {}", dimension.location());
            return Optional.empty();
        }
        if (api.get().hasLocal(name)) {
            return Optional.empty();
        }

        IMarkableRegion region = switch (shape) {
            case CUBOID -> new CuboidRegion(name, new CuboidArea(first, second), player, dimension);
            case SPHERE -> new SphereRegion(name, new SphereArea(first, second), player, dimension);
            case CYLINDER -> new CylinderRegion(name,
                    new VerticalCylinderArea(first, second), new RegionAnchors(), player, dimension);
        };

        if (!api.get().addLocalRegion(region)) {
            return Optional.empty();
        }
        save(dimension);
        return Optional.of(region);
    }

    public static boolean delete(ResourceKey<Level> dimension, String name) {
        Optional<ILevelRegionApi> api = api(dimension);
        if (api.isEmpty() || !api.get().hasLocal(name)) {
            return false;
        }
        boolean removed = api.get().removeLocalRegion(name);
        if (removed) {
            save(dimension);
        }
        return removed;
    }

    /**
     * Replaces a region's shape, keeping its name, flags and members.
     *
     * <p>This is what makes the perimeter editable at all. YAWP's own commands can only move the area
     * of a region to another area of the same kind, and so can this: changing a cuboid into a sphere
     * would silently reinterpret the two stored corners as a centre and a radius, which is a different
     * region wearing the same name.</p>
     */
    public static boolean reshape(ResourceKey<Level> dimension, String name,
                                  BlockPos first, BlockPos second) {
        Optional<IMarkableRegion> found = region(dimension, name);
        if (found.isEmpty()) {
            return false;
        }
        IMarkableRegion region = found.get();
        String areaType = region.getAreaType().name();
        switch (areaType.toUpperCase(Locale.ROOT)) {
            case "CUBOID" -> region.setArea(new CuboidArea(first, second));
            case "SPHERE" -> region.setArea(new SphereArea(first, second));
            case "CYLINDER" -> region.setArea(new VerticalCylinderArea(first, second));
            default -> {
                // Polygon and prism regions can exist - YAWP can make them, even if this mod does
                // not offer them - and two corners are not enough to describe either. Refusing is
                // the only honest answer; quietly turning one into a cuboid would destroy it.
                FantasticRegions.LOGGER.info("Refusing to reshape {} region '{}' from two corners",
                        areaType, name);
                return false;
            }
        }
        save(dimension);
        return true;
    }

    /** The three states a flag can be left in, plus removal, all through one call. */
    public static boolean setFlagState(ResourceKey<Level> dimension, String regionName,
                                       String flagId, FlagState state) {
        Optional<IMarkableRegion> found = region(dimension, regionName);
        if (found.isEmpty()) {
            return false;
        }
        IMarkableRegion region = found.get();
        if (state == FlagState.UNDEFINED) {
            // Undefined means "say nothing here and let the parent decide", which in YAWP's store is
            // the absence of the flag rather than a flag holding a third value.
            region.removeFlag(flagId);
            save(dimension);
            return true;
        }
        IFlag flag = region.getFlag(flagId);
        if (flag == null) {
            try {
                flag = new de.z0rdak.yawp.api.core.flag.FlagBuilder(flagId).build();
            } catch (IllegalArgumentException unknownFlag) {
                FantasticRegions.LOGGER.warn("Unknown flag id '{}'", flagId);
                return false;
            }
            flag.setState(state);
            region.addFlag(flag);
        } else {
            flag.setState(state);
        }
        save(dimension);
        return true;
    }

    public static FlagState flagState(IMarkableRegion region, String flagId) {
        IFlag flag = region.getFlag(flagId);
        return flag == null ? FlagState.UNDEFINED : flag.getState();
    }

    public static boolean addPlayer(ResourceKey<Level> dimension, String regionName,
                                    UUID playerId, String playerName, String group) {
        Optional<IMarkableRegion> found = region(dimension, regionName);
        if (found.isEmpty()) {
            return false;
        }
        found.get().addPlayer(playerId, playerName, group);
        save(dimension);
        return true;
    }

    public static boolean removePlayer(ResourceKey<Level> dimension, String regionName,
                                       UUID playerId, String group) {
        Optional<IMarkableRegion> found = region(dimension, regionName);
        if (found.isEmpty()) {
            return false;
        }
        found.get().removePlayer(playerId, group);
        save(dimension);
        return true;
    }

    public static boolean addTeam(ResourceKey<Level> dimension, String regionName,
                                  String teamName, String group) {
        Optional<IMarkableRegion> found = region(dimension, regionName);
        if (found.isEmpty()) {
            return false;
        }
        found.get().addTeam(teamName, group);
        save(dimension);
        return true;
    }

    public static boolean removeTeam(ResourceKey<Level> dimension, String regionName,
                                     String teamName, String group) {
        Optional<IMarkableRegion> found = region(dimension, regionName);
        if (found.isEmpty()) {
            return false;
        }
        found.get().removeTeam(teamName, group);
        save(dimension);
        return true;
    }

    public static Collection<PlayerContainer> groups(IMarkableRegion region) {
        return region.getGroups().values();
    }

    public static boolean setPriority(ResourceKey<Level> dimension, String regionName, int priority) {
        Optional<IMarkableRegion> found = region(dimension, regionName);
        if (found.isEmpty()) {
            return false;
        }
        found.get().setPriority(priority);
        save(dimension);
        return true;
    }

    public static boolean setActive(ResourceKey<Level> dimension, String regionName, boolean active) {
        Optional<IMarkableRegion> found = region(dimension, regionName);
        if (found.isEmpty()) {
            return false;
        }
        found.get().setIsActive(active);
        save(dimension);
        return true;
    }

    public static boolean setMuted(ResourceKey<Level> dimension, String regionName, boolean muted) {
        Optional<IMarkableRegion> found = region(dimension, regionName);
        if (found.isEmpty()) {
            return false;
        }
        found.get().setIsMuted(muted);
        save(dimension);
        return true;
    }

    public static boolean rename(ResourceKey<Level> dimension, String from, String to) {
        Optional<IMarkableRegion> found = region(dimension, from);
        if (found.isEmpty() || exists(dimension, to)) {
            return false;
        }
        found.get().rename(to);
        save(dimension);
        return true;
    }

    /**
     * Writes the dimension's regions to disk.
     *
     * <p>Called after every edit rather than on a timer. Region edits are rare and small, and the
     * alternative is that a crash loses whichever flags were set since the last autosave - which the
     * admin has no way to notice until something they thought was protected is not.</p>
     */
    public static void save(ResourceKey<Level> dimension) {
        RegionManager.get().save(dimension);
    }

    public static void save(ServerLevel level) {
        RegionManager.get().save(level);
    }
}
