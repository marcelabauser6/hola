package com.athensmc.fantasticregions.wand;

import de.z0rdak.yawp.core.area.AreaType;
import de.z0rdak.yawp.core.stick.MarkerStick;
import de.z0rdak.yawp.util.StickUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * The blaze rod that preselects a region.
 *
 * <p>It is not a registered item, and that is the whole trick. It is a plain blaze rod carrying the
 * marker data YAWP already understands, written with YAWP's own {@code StickUtil}. Two things fall out
 * of that:</p>
 *
 * <ul>
 *   <li>YAWP's existing create command accepts it. That command asks {@code StickUtil.isMarker} and
 *       reads the marked blocks out of the item's tag; it never asks what item is holding them. So the
 *       preselection made with this rod is picked up by the create command unchanged.</li>
 *   <li>Clicking blocks already works. YAWP's own interaction mixin also gates on {@code isMarker}
 *       rather than on the item, so it registers the corners itself. This mod does not intercept the
 *       clicks at all - doing so would have marked every corner twice.</li>
 * </ul>
 *
 * <p>Because there is no new item and no new packet, nothing here needs to be installed on the client.
 * That was not the case for the interface that was planned before, and it is worth the constraint: a
 * wand that works for anyone who can already run the command is a very different thing to deploy.</p>
 */
public final class RegionWand {

    /**
     * The key YAWP nests its marker data under, inside the item's own tag.
     *
     * <p>Read out of {@code StickUtil.initStickTag} in the shipped jar rather than guessed. If a YAWP
     * update moves it, the wand stops being recognised as a marker, which is a visible failure rather
     * than a silent one.</p>
     */
    private static final String STICK_TAG = "stick";

    /** Vanilla's own item display keys, where the name and description live. */
    private static final String DISPLAY_TAG = "display";
    private static final String LORE_TAG = "Lore";

    private RegionWand() {
    }

    /**
     * Builds a wand for one shape, ready to mark.
     *
     * <p>The area type is set now rather than cycled later, which is what makes {@code /yawp wand
     * cuboide} mean something: the rod remembers the shape, so the corners it collects are already the
     * right kind of corners for the region that will be created from them.</p>
     */
    public static ItemStack create(WandShape shape, ResourceKey<Level> dimension) {
        ItemStack stack = new ItemStack(Items.BLAZE_ROD);
        StickUtil.initMarkerNbt(stack, dimension);

        MarkerStick marker = markerOf(stack);
        if (marker != null) {
            marker.setAreaType(AreaType.valueOf(shape.areaTypeName()));
            writeBack(stack, marker);
        }

        // Set after initMarkerNbt, which applies YAWP's own English name, tooltip and glint. The glint
        // is left as it put it; the name and the description are replaced with Spanish ones.
        stack.setHoverName(Component.literal("Vara de zona: " + shape.label())
                .withStyle(ChatFormatting.GOLD)
                .withStyle(style -> style.withItalic(false)));
        describeOnItem(stack, shape, marker);
        return stack;
    }

    /**
     * Replaces the item's description with one in Spanish, explaining the shape and what comes next.
     *
     * <p>On the item rather than only in the chat message, because the rod outlives the message. A rod
     * found in a chest a week later should still say what shape it marks and how the marking is
     * finished, and the point count comes from YAWP's own area type so it cannot drift.</p>
     */
    private static void describeOnItem(ItemStack stack, WandShape shape, MarkerStick marker) {
        int needed = marker == null ? 0 : neededBlocks(marker);
        int max = marker == null ? 0 : maxBlocks(marker);

        List<Component> lines = new ArrayList<>(4);
        lines.add(line(shape.help(), ChatFormatting.GRAY));
        lines.add(line(pointsLine(needed, max), ChatFormatting.YELLOW));
        lines.add(line("El contorno se dibuja mientras la llevas en la mano.", ChatFormatting.GRAY));
        lines.add(line("Al terminar, crea la zona con el comando de crear de YAWP.",
                ChatFormatting.GRAY));

        ListTag lore = new ListTag();
        for (Component component : lines) {
            lore.add(StringTag.valueOf(Component.Serializer.toJson(component)));
        }
        stack.getOrCreateTagElement(DISPLAY_TAG).put(LORE_TAG, lore);
    }

    private static Component line(String text, ChatFormatting colour) {
        return Component.literal(text)
                .withStyle(colour)
                .withStyle(style -> style.withItalic(false));
    }

    /** How many blocks to click, phrased for a fixed count or a range. */
    private static String pointsLine(int needed, int max) {
        if (needed <= 0) {
            return "Marca los bloques con clic derecho.";
        }
        if (max > needed) {
            return "Marca entre " + needed + " y " + max + " bloques con clic derecho.";
        }
        if (needed == 1) {
            return "Marca 1 bloque con clic derecho.";
        }
        return "Marca " + needed + " bloques con clic derecho.";
    }

    /** True for a blaze rod carrying YAWP marker data. */
    public static boolean isWand(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.BLAZE_ROD) && StickUtil.isMarker(stack);
    }

    /** The marker data on a wand, or null when the item is not one or its tag is damaged. */
    public static MarkerStick markerOf(ItemStack stack) {
        CompoundTag tag = StickUtil.getStickNBT(stack);
        if (tag == null) {
            return null;
        }
        try {
            return new MarkerStick(tag);
        } catch (RuntimeException damagedTag) {
            // A hand-edited or half-written tag should leave the rod inert, not crash the tick that
            // was only trying to draw an outline for it.
            return null;
        }
    }

    /** The shape a wand is set to, or null when its area type is one this mod has no name for. */
    public static WandShape shapeOf(ItemStack stack) {
        MarkerStick marker = markerOf(stack);
        if (marker == null) {
            return null;
        }
        AreaType type = marker.getAreaType();
        return type == null ? null : WandShape.fromId(type.name());
    }

    /** The corners marked so far, in the order they were clicked. */
    public static List<BlockPos> markedBlocks(ItemStack stack) {
        MarkerStick marker = markerOf(stack);
        return marker == null ? List.of() : marker.getMarkedBlocks();
    }

    /** How many corners this shape needs before YAWP will accept it. Read off YAWP's own enum. */
    public static int neededBlocks(MarkerStick marker) {
        AreaType type = marker.getAreaType();
        return type == null ? 0 : type.neededBlocks;
    }

    /** The most corners this shape accepts. Equal to the minimum for everything but polygon and prism. */
    public static int maxBlocks(MarkerStick marker) {
        AreaType type = marker.getAreaType();
        return type == null ? 0 : type.maxBlocks;
    }

    /** Writes modified marker data back onto the item. */
    public static void writeBack(ItemStack stack, MarkerStick marker) {
        stack.getOrCreateTag().put(STICK_TAG, marker.serializeNBT());
    }
}
