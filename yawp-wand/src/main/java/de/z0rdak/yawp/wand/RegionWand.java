package de.z0rdak.yawp.wand;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * The blaze rod that preselects a region.
 *
 * <p>Not a registered item: a plain blaze rod carrying the marker tag YAWP defines, written by
 * {@link MarkerData}. Nothing here imports YAWP, so the rod can be issued and marked whether or not YAWP
 * is installed - and when it is, its create command accepts the rod, because that command asks for the
 * tag rather than for one of its own objects.</p>
 */
public final class RegionWand {

    private static final String DISPLAY_TAG = "display";
    private static final String LORE_TAG = "Lore";

    private RegionWand() {
    }

    /** Builds a wand for one shape, ready to mark. */
    public static ItemStack create(WandShape shape, ResourceKey<Level> dimension) {
        ItemStack stack = new ItemStack(Items.BLAZE_ROD);
        MarkerData.initialise(stack, shape, dimension);
        stack.setHoverName(name(shape));
        stack.enchant(Enchantments.UNBREAKING, 1);
        describe(stack, shape, List.of());
        return stack;
    }

    public static boolean isWand(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.BLAZE_ROD) && MarkerData.isMarker(stack);
    }

    private static Component name(WandShape shape) {
        return Component.literal("Vara de zona: " + shape.label())
                .withStyle(ChatFormatting.GOLD)
                .withStyle(style -> style.withItalic(false));
    }

    /**
     * Rewrites the item's description to match what is marked.
     *
     * <p>Refreshed after every corner, so the rod itself is the progress readout and not only the line
     * above the hotbar. A rod found in a chest later still says what shape it marks, how many corners it
     * wants and how to undo one.</p>
     */
    public static void describe(ItemStack stack, WandShape shape, List<BlockPos> corners) {
        List<Component> lines = new ArrayList<>(6);
        lines.add(line(shape.help(), ChatFormatting.GRAY));
        lines.add(line(progress(shape, corners.size()), ChatFormatting.YELLOW));
        if (!corners.isEmpty()) {
            lines.add(line(Outlines.measure(shape, asCoordinates(corners)), ChatFormatting.AQUA));
        }
        lines.add(line("Clic derecho para marcar una esquina.", ChatFormatting.GRAY));
        lines.add(line("Agachado y clic derecho para deshacer la última.", ChatFormatting.GRAY));
        lines.add(line("Al completarla, créala con el comando de crear de YAWP.",
                ChatFormatting.DARK_GRAY));

        ListTag lore = new ListTag();
        for (Component component : lines) {
            lore.add(StringTag.valueOf(Component.Serializer.toJson(component)));
        }
        stack.getOrCreateTagElement(DISPLAY_TAG).put(LORE_TAG, lore);
    }

    /** How far along the marking is, phrased for a fixed count or a range. */
    public static String progress(WandShape shape, int marked) {
        int needed = shape.neededBlocks();
        if (marked == 0) {
            return shape.takesRange()
                    ? "Marca entre " + needed + " y " + shape.maxBlocks() + " esquinas."
                    : "Marca " + needed + (needed == 1 ? " esquina." : " esquinas.");
        }
        if (marked < needed) {
            int left = needed - marked;
            return marked + " de " + needed + " marcadas, falta"
                    + (left == 1 ? " 1." : "n " + left + ".");
        }
        if (shape.takesRange() && marked < shape.maxBlocks()) {
            return marked + " marcadas. Ya vale, y admite hasta " + shape.maxBlocks() + ".";
        }
        return marked + " marcadas. Lista para crear.";
    }

    /** Converts positions to the plain coordinate triples the geometry works in. */
    public static List<int[]> asCoordinates(List<BlockPos> corners) {
        List<int[]> coordinates = new ArrayList<>(corners.size());
        for (BlockPos corner : corners) {
            coordinates.add(new int[]{corner.getX(), corner.getY(), corner.getZ()});
        }
        return coordinates;
    }

    private static Component line(String text, ChatFormatting colour) {
        return Component.literal(text)
                .withStyle(colour)
                .withStyle(style -> style.withItalic(false));
    }
}
