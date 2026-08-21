package com.athensmc.athenscoins.client.screen;

import com.athensmc.athenscoins.client.theme.StatsTheme;
import com.athensmc.athenscoins.client.widget.ColorPicker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Full appearance editor for the stats table.
 *
 * <p>Pick a slot on the left, then set its colour with the hue/saturation picker, a rainbow row,
 * a set of neutral swatches or by nudging the hex value. Slots that support it also get an
 * opacity slider. Presets cover the common looks, and everything is saved per client.</p>
 */
public class StatsThemeEditorScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 232;

    private static final int SLOT_H = 15;
    private static final int SWATCH = 12;

    /** Fixed rainbow row, so a specific hue is one click away. */
    private static final int[] RAINBOW = new int[12];
    /** Neutral ramp for backgrounds and text. */
    private static final int[] NEUTRALS = {
            0x000000, 0x1A1A1A, 0x333333, 0x4D4D4D, 0x808080,
            0xB3B3B3, 0xD9D9D9, 0xFFFFFF, 0x14101A, 0x0E1524, 0x1E0E11, 0xE8DCC0,
    };

    static {
        for (int i = 0; i < RAINBOW.length; i++) {
            RAINBOW[i] = ColorPicker.hsvToRgb(i / (float) RAINBOW.length, 0.85F, 0.95F);
        }
    }

    private final Screen parent;
    private final ColorPicker picker = new ColorPicker();

    private StatsTheme working;
    private List<StatsTheme.Slot> slots;
    private int selected;

    private int leftPos;
    private int topPos;
    private int alphaBarX;
    private int alphaBarY;
    private int alphaBarW;
    private boolean draggingAlpha;

    public StatsThemeEditorScreen(Screen parent) {
        super(Component.translatable("gui.athens_coins.theme_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        leftPos = (width - PANEL_W) / 2;
        topPos = (height - PANEL_H) / 2;

        if (working == null) {
            working = StatsTheme.get().copy();
        }
        slots = working.slots();
        picker.setBounds(leftPos + 176, topPos + 34, 110, 74, 10);
        picker.setRgb(slots.get(selected).getter().getAsInt());

        alphaBarX = leftPos + 176;
        alphaBarY = topPos + 34 + 74 + 4 + 10 + 8;
        alphaBarW = 110;

        clearWidgets();

        // format toggles
        addRenderableWidget(Button.builder(formatLabel("gui.athens_coins.theme_shadow", working.textShadow),
                        button -> {
                            working.textShadow = !working.textShadow;
                            button.setMessage(formatLabel("gui.athens_coins.theme_shadow", working.textShadow));
                        })
                .bounds(leftPos + 176, topPos + 150, 110, 15).build());
        addRenderableWidget(Button.builder(formatLabel("gui.athens_coins.theme_bold", working.boldSections),
                        button -> {
                            working.boldSections = !working.boldSections;
                            button.setMessage(formatLabel("gui.athens_coins.theme_bold", working.boldSections));
                        })
                .bounds(leftPos + 176, topPos + 167, 110, 15).build());
        addRenderableWidget(Button.builder(formatLabel("gui.athens_coins.theme_italic", working.italicLabels),
                        button -> {
                            working.italicLabels = !working.italicLabels;
                            button.setMessage(formatLabel("gui.athens_coins.theme_italic", working.italicLabels));
                        })
                .bounds(leftPos + 176, topPos + 184, 110, 15).build());

        // presets
        for (int i = 0; i < StatsTheme.presetCount(); i++) {
            final int index = i;
            addRenderableWidget(Button.builder(Component.literal(String.valueOf(i + 1)),
                            button -> {
                                working = StatsTheme.preset(index);
                                rebuild();
                            })
                    .bounds(leftPos + 8 + i * 24, topPos + 184, 22, 15).build());
        }

        // actions
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.theme_save"),
                        button -> {
                            StatsTheme.replace(working);
                            working.save();
                            minecraft.setScreen(parent);
                        })
                .bounds(leftPos + 8, topPos + PANEL_H - 26, 100, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.theme_reset"),
                        button -> {
                            working = new StatsTheme();
                            rebuild();
                        })
                .bounds(leftPos + 112, topPos + PANEL_H - 26, 100, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.back"),
                        button -> minecraft.setScreen(parent))
                .bounds(leftPos + PANEL_W - 90, topPos + PANEL_H - 26, 82, 18).build());
    }

    private void rebuild() {
        slots = working.slots();
        picker.setRgb(slots.get(selected).getter().getAsInt());
        init();
    }

    private Component formatLabel(String key, boolean on) {
        return Component.translatable(key).append(": ")
                .append(Component.translatable(on ? "gui.athens_coins.on" : "gui.athens_coins.off"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        // Preview the theme being edited, so changes are visible immediately.
        graphics.fill(leftPos, topPos, leftPos + PANEL_W, topPos + PANEL_H, working.background());
        StatsScreen.outline(graphics, leftPos, topPos, PANEL_W, PANEL_H, working.border());
        graphics.fill(leftPos + 1, topPos + 1, leftPos + PANEL_W - 1, topPos + 18, working.titleBar());
        graphics.drawString(font, title, leftPos + PANEL_W / 2 - font.width(title) / 2, topPos + 6,
                0xFF000000 | working.titleTextColor, working.textShadow);

        // ---- slot list
        for (int i = 0; i < slots.size(); i++) {
            StatsTheme.Slot slot = slots.get(i);
            int y = topPos + 24 + i * SLOT_H;
            boolean active = i == selected;
            if (active) {
                graphics.fill(leftPos + 6, y - 1, leftPos + 168, y + SLOT_H - 2, 0x40FFFFFF);
            }
            // colour chip
            graphics.fill(leftPos + 8, y, leftPos + 8 + SWATCH, y + SWATCH - 1,
                    0xFF000000 | slot.getter().getAsInt());
            StatsScreen.outline(graphics, leftPos + 8, y, SWATCH, SWATCH - 1, 0xFF000000);
            graphics.drawString(font, Component.translatable(slot.nameKey()),
                    leftPos + 26, y + 1, 0xFF000000 | working.labelColor, working.textShadow);
            if (slot.hasAlpha()) {
                String alpha = slot.alphaGetter().getAsInt() * 100 / 255 + "%";
                graphics.drawString(font, alpha, leftPos + 164 - font.width(alpha), y + 1,
                        0xFF000000 | working.labelColor, working.textShadow);
            }
        }

        // ---- picker
        picker.render(graphics);

        StatsTheme.Slot slot = slots.get(selected);
        String hex = String.format(Locale.ROOT, "#%06X", picker.rgb());
        graphics.drawString(font, hex, leftPos + 176, topPos + 34 + 74 + 4 + 10 + 20 - 44,
                0xFF000000 | working.valueColor, working.textShadow);

        // rainbow row
        int rainbowY = topPos + 122;
        for (int i = 0; i < RAINBOW.length; i++) {
            int x = leftPos + 176 + i * 9;
            graphics.fill(x, rainbowY, x + 8, rainbowY + 8, 0xFF000000 | RAINBOW[i]);
        }
        // neutral row
        int neutralY = rainbowY + 10;
        for (int i = 0; i < NEUTRALS.length; i++) {
            int x = leftPos + 176 + i * 9;
            graphics.fill(x, neutralY, x + 8, neutralY + 8, 0xFF000000 | NEUTRALS[i]);
            StatsScreen.outline(graphics, x, neutralY, 8, 8, 0x60000000);
        }

        // ---- alpha slider, only for slots that have one
        if (slot.hasAlpha()) {
            int alpha = slot.alphaGetter().getAsInt();
            graphics.drawString(font, Component.translatable("gui.athens_coins.theme_opacity"),
                    alphaBarX, alphaBarY - 10, 0xFF000000 | working.labelColor, working.textShadow);
            // checkerboard so transparency is visible
            for (int i = 0; i < alphaBarW / 4; i++) {
                int x0 = alphaBarX + i * 4;
                graphics.fill(x0, alphaBarY, Math.min(x0 + 4, alphaBarX + alphaBarW), alphaBarY + 8,
                        i % 2 == 0 ? 0xFF6E6E6E : 0xFF3A3A3A);
            }
            graphics.fillGradient(alphaBarX, alphaBarY, alphaBarX + alphaBarW, alphaBarY + 8,
                    0xFF000000 | slot.getter().getAsInt(), 0xFF000000 | slot.getter().getAsInt());
            graphics.fill(alphaBarX, alphaBarY, alphaBarX + alphaBarW * alpha / 255, alphaBarY + 8,
                    0xFF000000 | slot.getter().getAsInt());
            StatsScreen.outline(graphics, alphaBarX, alphaBarY, alphaBarW, 8, 0xFF000000);
            int knob = alphaBarX + alphaBarW * alpha / 255;
            graphics.fill(knob - 1, alphaBarY - 2, knob + 2, alphaBarY + 10, 0xFFFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // slot list
        for (int i = 0; i < slots.size(); i++) {
            int y = topPos + 24 + i * SLOT_H;
            if (mouseX >= leftPos + 6 && mouseX < leftPos + 168 && mouseY >= y - 1 && mouseY < y + SLOT_H - 2) {
                selected = i;
                picker.setRgb(slots.get(i).getter().getAsInt());
                return true;
            }
        }

        if (picker.mouseClicked(mouseX, mouseY)) {
            slots.get(selected).setter().accept(picker.rgb());
            return true;
        }

        // swatch rows
        int rainbowY = topPos + 122;
        if (mouseY >= rainbowY && mouseY < rainbowY + 8) {
            int index = (int) ((mouseX - (leftPos + 176)) / 9);
            if (index >= 0 && index < RAINBOW.length) {
                apply(RAINBOW[index]);
                return true;
            }
        }
        int neutralY = rainbowY + 10;
        if (mouseY >= neutralY && mouseY < neutralY + 8) {
            int index = (int) ((mouseX - (leftPos + 176)) / 9);
            if (index >= 0 && index < NEUTRALS.length) {
                apply(NEUTRALS[index]);
                return true;
            }
        }

        // alpha slider
        StatsTheme.Slot slot = slots.get(selected);
        if (slot.hasAlpha() && mouseY >= alphaBarY - 2 && mouseY < alphaBarY + 10
                && mouseX >= alphaBarX && mouseX < alphaBarX + alphaBarW) {
            draggingAlpha = true;
            applyAlpha(mouseX);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void apply(int rgb) {
        slots.get(selected).setter().accept(rgb);
        picker.setRgb(rgb);
    }

    private void applyAlpha(double mouseX) {
        StatsTheme.Slot slot = slots.get(selected);
        int value = (int) Math.round((mouseX - alphaBarX) / (double) alphaBarW * 255.0D);
        slot.alphaSetter().accept(Math.max(0, Math.min(255, value)));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (picker.mouseDragged(mouseX, mouseY)) {
            slots.get(selected).setter().accept(picker.rgb());
            return true;
        }
        if (draggingAlpha) {
            applyAlpha(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        picker.mouseReleased();
        draggingAlpha = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Scrolling over the picker nudges brightness, handy for fine tuning. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        StatsTheme.Slot slot = slots.get(selected);
        if (slot.hasAlpha()) {
            int step = (int) Math.signum(delta) * 8;
            slot.alphaSetter().accept(Math.max(0, Math.min(255, slot.alphaGetter().getAsInt() + step)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
