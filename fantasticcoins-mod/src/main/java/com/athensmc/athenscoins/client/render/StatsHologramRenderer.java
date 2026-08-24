package com.athensmc.athenscoins.client.render;

import com.athensmc.athenscoins.block.StatsHologramBlockEntity;
import com.athensmc.athenscoins.stats.HologramConfig;
import com.athensmc.athenscoins.stats.HologramLines;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws a stats hologram: floating text above its projector.
 *
 * <p>The mod's first piece of in-world rendering, so a few of the choices here are worth stating
 * rather than leaving as apparently arbitrary constants.</p>
 *
 * <p><b>{@code NORMAL}, not {@code SEE_THROUGH}.</b> Vanilla name tags switch to see-through so you can
 * find a player behind a wall. A hologram is furniture: a board in a bank lobby that glowed through the
 * lobby wall would be visible from outside the building, and there would be no way to hide one you did
 * not want to see. Depth-tested text means a wall hides it, which is what a sign on a wall does.</p>
 *
 * <p><b>No drop shadow, and not as a setting either.</b> {@code Font} draws a shadow by offsetting a
 * second copy of the glyph by a hair in z - about a thousandth of a block once this text has been scaled
 * down to nameplate size. At that separation the depth buffer cannot tell the two copies apart, so the
 * shadow and the glyph fight for every pixel and the whole board strobes as the camera moves. It is not a
 * look worth offering a toggle for; a hologram is a projection and projections do not cast shadows.</p>
 *
 * <p><b>Full brightness.</b> The text is lit at {@link LightTexture#FULL_BRIGHT} regardless of the light
 * reaching the block, because the thing being simulated is a projection, not a painted surface. Passing
 * the block's own light would make a board in an unlit square unreadable at night - which is exactly
 * when someone is most likely to be standing in front of it.</p>
 *
 * <p><b>The panel is one quad, not one per line.</b> {@code Font.drawInBatch} can draw its own
 * background, but it sizes it to that string, so a table of six rows of different widths came out as a
 * ragged stack of separate boxes. One quad measured from the widest row reads as a board.</p>
 *
 * <p><b>Bottom-anchored.</b> Rows are laid out upward from the projector, so raising the height offset
 * lifts the whole board and does not change where the first line sits relative to the last. Anchoring
 * the top instead made the board grow downward into the projector as lines were added.</p>
 */
@OnlyIn(Dist.CLIENT)
public class StatsHologramRenderer implements BlockEntityRenderer<StatsHologramBlockEntity> {

    /** Vanilla's name-tag scale, which is what makes 100% look like a normal floating label. */
    private static final float BASE_SCALE = 0.025F;
    /** Padding around the text inside the panel, in text pixels. */
    private static final int PAD_X = 4;
    private static final int PAD_Y = 2;

    private final Font font;

    public StatsHologramRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(StatsHologramBlockEntity projector, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        HologramConfig config = projector.config();
        List<HologramLines.Row> rows = HologramLines.build(config, projector.snapshot());
        MutableComponent title = titleOf(config);
        if (rows.isEmpty() && title == null) {
            return;
        }

        int spacing = config.lineSpacing();
        int titleHeight = title == null ? 0 : spacing + 2;
        int textWidth = HologramLines.panelWidth(rows, title, font::width, font::width);
        int totalHeight = titleHeight + rows.size() * spacing;

        pose.pushPose();
        pose.translate(0.5D, config.heightOffset() + 0.5D, 0.5D);
        if (config.billboard()) {
            pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        } else {
            Direction facing = projector.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
            pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        }
        // Negative X and Y flip the text into world space: after this, +y is down on screen, which is
        // why the rows below are laid out from a negative top edge towards zero.
        float scale = BASE_SCALE * config.scale();
        pose.scale(-scale, -scale, scale);

        Matrix4f matrix = pose.last().pose();
        float half = textWidth / 2.0F;
        float top = -totalHeight;

        if (config.showBackground()) {
            drawPanel(matrix, buffers, -half - PAD_X, top - PAD_Y, half + PAD_X, PAD_Y,
                    config.background());
        }

        float y = top;
        if (title != null) {
            float titleX = -font.width(title) / 2.0F;
            font.drawInBatch(title, titleX, y, 0xFF000000 | config.titleColor(), false,
                    matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            y += titleHeight;
        }
        for (HologramLines.Row row : rows) {
            drawRow(row, config, matrix, buffers, half, y);
            y += spacing;
        }
        pose.popPose();
    }

    private void drawRow(HologramLines.Row row, HologramConfig config, Matrix4f matrix,
                         MultiBufferSource buffers, float half, float y) {
        if (row.isSpacer()) {
            return;
        }
        if (row.valueOnly()) {
            float x = -font.width(row.value()) / 2.0F;
            font.drawInBatch(row.value(), x, y, 0xFF000000 | row.rgb(), false,
                    matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            return;
        }
        font.drawInBatch(row.label(), -half, y, 0xFF000000 | config.labelColor(),
                false, matrix, buffers, Font.DisplayMode.NORMAL, 0,
                LightTexture.FULL_BRIGHT);
        if (!row.value().isEmpty()) {
            font.drawInBatch(row.value(), half - font.width(row.value()), y,
                    0xFF000000 | row.rgb(), false, matrix, buffers,
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }
    }

    /**
     * The translucent board behind the text.
     *
     * <p>{@code RenderType.textBackground()} takes position, colour and a lightmap coordinate - the same
     * format vanilla uses for the box behind a name tag - so the quad is emitted by hand rather than
     * through any of the {@code GuiGraphics} helpers, which do not exist out here in the world.</p>
     */
    private static void drawPanel(Matrix4f matrix, MultiBufferSource buffers,
                                  float x0, float y0, float x1, float y1, int argb) {
        int alpha = argb >>> 24;
        if (alpha == 0) {
            return;
        }
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        VertexConsumer consumer = buffers.getBuffer(RenderType.textBackground());
        consumer.vertex(matrix, x0, y1, 0.03F).color(red, green, blue, alpha)
                .uv2(LightTexture.FULL_BRIGHT).endVertex();
        consumer.vertex(matrix, x1, y1, 0.03F).color(red, green, blue, alpha)
                .uv2(LightTexture.FULL_BRIGHT).endVertex();
        consumer.vertex(matrix, x1, y0, 0.03F).color(red, green, blue, alpha)
                .uv2(LightTexture.FULL_BRIGHT).endVertex();
        consumer.vertex(matrix, x0, y0, 0.03F).color(red, green, blue, alpha)
                .uv2(LightTexture.FULL_BRIGHT).endVertex();
    }

    private static MutableComponent titleOf(HologramConfig config) {
        if (!config.hasTitle()) {
            return null;
        }
        MutableComponent title = Component.literal(config.title());
        return config.boldTitle() ? title.withStyle(ChatFormatting.BOLD) : title;
    }

    /**
     * Keeps drawing when the projector itself is off screen.
     *
     * <p>Without this the board vanishes as soon as you tilt up far enough that the plinth leaves the
     * view - which is precisely what someone reading a board mounted above eye level does.</p>
     */
    @Override
    public boolean shouldRenderOffScreen(StatsHologramBlockEntity projector) {
        return true;
    }

    /** Readable from across a market square, not just from the block next to it. */
    @Override
    public int getViewDistance() {
        return 96;
    }
}
