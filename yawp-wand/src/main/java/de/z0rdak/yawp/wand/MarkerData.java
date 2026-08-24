package de.z0rdak.yawp.wand;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reads and writes YAWP's marker tag, without linking against YAWP.
 *
 * <p>This is the heart of the mod being usable on its own. The tag format was read out of
 * {@code MarkerStick.serializeNBT} and {@code AbstractStick.serializeNBT} in the shipped jar, so a rod
 * written here is a rod YAWP's own create command accepts - it asks for this tag, not for a YAWP object.
 * Writing it directly means this mod imports nothing from YAWP and therefore loads, and works, whether
 * or not YAWP does.</p>
 *
 * <p>The layout, for anyone checking it against a future YAWP:</p>
 *
 * <pre>
 * item tag
 *   stick (compound)
 *     stick_type  string   "RegionMarker"       StickType.MARKER.stickName
 *     stick-id    string   a random UUID        regenerated on every write, as YAWP does
 *     valid       boolean  is the area complete
 *     type        string   "Cuboid" ...         AreaType.areaType, not the enum name
 *     dim         string   "minecraft:overworld"
 *     blocks      list     of NbtUtils.writeBlockPos compounds, in click order
 * </pre>
 *
 * <p>Every one of those names is YAWP's, and none of them can be changed here without the rod stopping
 * being a marker as far as YAWP is concerned.</p>
 */
public final class MarkerData {

    /** Where YAWP nests its marker data inside the item's tag. */
    public static final String STICK = "stick";

    /** Marks the tag as a marker. YAWP's own check compares against this exact string. */
    public static final String MARKER_TYPE = "RegionMarker";

    private static final String STICK_TYPE = "stick_type";
    private static final String STICK_ID = "stick-id";
    private static final String VALID = "valid";
    private static final String TYPE = "type";
    private static final String DIM = "dim";
    private static final String BLOCKS = "blocks";

    /** NBT type id for a compound, for the typed list getter. */
    private static final int TAG_COMPOUND = 10;

    private MarkerData() {
    }

    /** True when the item carries a marker tag, by the same test YAWP applies. */
    public static boolean isMarker(ItemStack stack) {
        CompoundTag stick = stickTag(stack);
        return stick != null && MARKER_TYPE.equals(stick.getString(STICK_TYPE));
    }

    public static CompoundTag stickTag(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTag()) {
            return null;
        }
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(STICK) ? tag.getCompound(STICK) : null;
    }

    /** Writes a fresh marker tag for one shape, with nothing marked yet. */
    public static void initialise(ItemStack stack, WandShape shape, ResourceKey<Level> dimension) {
        write(stack, shape, dimension, List.of());
    }

    /**
     * Writes the whole marker tag from a list of corners.
     *
     * <p>Always the whole tag, never an append. That is what makes the marking safe when YAWP's own
     * interaction mixin is alive and appending corners of its own: this mod keeps its own authoritative
     * list and rewrites {@code blocks} from it, so a corner counted twice by two handlers still comes out
     * as one corner. An append here would have made double-marking permanent.</p>
     */
    public static void write(ItemStack stack, WandShape shape, ResourceKey<Level> dimension,
                            List<BlockPos> corners) {
        CompoundTag stick = new CompoundTag();
        stick.putString(STICK_TYPE, MARKER_TYPE);
        stick.putString(STICK_ID, UUID.randomUUID().toString());
        stick.putString(TYPE, shape.yawpTypeName());
        stick.putString(DIM, dimension.location().toString());
        stick.putBoolean(VALID, isComplete(shape, corners.size()));

        ListTag blocks = new ListTag();
        for (BlockPos corner : corners) {
            blocks.add(NbtUtils.writeBlockPos(corner));
        }
        stick.put(BLOCKS, blocks);

        stack.getOrCreateTag().put(STICK, stick);
    }

    /** The shape a rod is set to, or null when the tag is absent or names something unknown. */
    public static WandShape shapeOf(ItemStack stack) {
        CompoundTag stick = stickTag(stack);
        return stick == null ? null : WandShape.fromYawpTypeName(stick.getString(TYPE));
    }

    /** The dimension the rod was issued for, as a plain string. */
    public static String dimensionOf(ItemStack stack) {
        CompoundTag stick = stickTag(stack);
        return stick == null ? "" : stick.getString(DIM);
    }

    /** The corners marked so far, in click order. */
    public static List<BlockPos> corners(ItemStack stack) {
        CompoundTag stick = stickTag(stack);
        if (stick == null || !stick.contains(BLOCKS)) {
            return List.of();
        }
        ListTag blocks = stick.getList(BLOCKS, TAG_COMPOUND);
        List<BlockPos> corners = new ArrayList<>(blocks.size());
        for (Tag entry : blocks) {
            if (entry instanceof CompoundTag compound) {
                corners.add(NbtUtils.readBlockPos(compound));
            }
        }
        return corners;
    }

    /**
     * Adds a corner, and says what the list became.
     *
     * <p>Clicking the same block twice is ignored rather than counted, because a right-click that gets
     * delivered twice is a real possibility and a polygon that gained a duplicate vertex would be
     * rejected by YAWP for reasons the player cannot see.</p>
     *
     * <p>Once the shape is full, a further click replaces the last corner instead of being refused.
     * Overshooting a corner should not mean starting the region again.</p>
     */
    public static List<BlockPos> withCorner(WandShape shape, List<BlockPos> existing, BlockPos added) {
        List<BlockPos> corners = new ArrayList<>(existing);
        if (corners.contains(added)) {
            return corners;
        }
        if (corners.size() >= shape.maxBlocks() && !corners.isEmpty()) {
            corners.set(corners.size() - 1, added);
            return corners;
        }
        corners.add(added);
        return corners;
    }

    /** Whether this many corners is enough for YAWP to build the shape. */
    public static boolean isComplete(WandShape shape, int cornerCount) {
        return cornerCount >= shape.neededBlocks() && cornerCount <= shape.maxBlocks();
    }
}
