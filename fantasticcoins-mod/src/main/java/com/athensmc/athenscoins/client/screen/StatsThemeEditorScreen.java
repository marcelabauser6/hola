package com.athensmc.athenscoins.client.screen;

import com.athensmc.athenscoins.client.layout.ScreenLayout;
import com.athensmc.athenscoins.client.layout.ScreenText;
import com.athensmc.athenscoins.client.theme.StatsTheme;
import com.athensmc.athenscoins.client.widget.ColorPicker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** Responsive stats-theme editor with explicitly separated picker, swatches, opacity and actions. */
public class StatsThemeEditorScreen extends Screen {
    private static final int PANEL_W = 340;
    private static final int PANEL_H = 232;
    private static final int SLOT_H = 14;
    private static final int SWATCH = 11;
    private static final int[] RAINBOW = new int[12];
    private static final int[] NEUTRALS = {
            0x000000, 0x1A1A1A, 0x333333, 0x4D4D4D, 0x808080,
            0xB3B3B3, 0xD9D9D9, 0xFFFFFF, 0x14101A, 0x0E1524, 0x1E0E11, 0xE8DCC0
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

    private ScreenLayout.Regions layout;
    private ScreenLayout.Columns columns;
    private int pickerX;
    private int pickerY;
    private int pickerW;
    private int rainbowY;
    private int neutralY;
    private int alphaBarX;
    private int alphaBarY;
    private int alphaBarW;
    private boolean draggingAlpha;
    private Component hoverTooltip;

    public StatsThemeEditorScreen(Screen parent) {
        super(Component.translatable("gui.athens_coins.theme_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ScreenLayout.Rect panel = ScreenLayout.centeredPanel(width, height, PANEL_W, PANEL_H);
        layout = ScreenLayout.regions(panel, 20, 0, 28);
        columns = ScreenLayout.columns(layout.content().inset(8), 10);
        if (working == null) {
            working = StatsTheme.get().copy();
        }
        slots = working.slots();
        selected = ScreenLayout.clamp(selected, 0, Math.max(0, slots.size() - 1));

        ScreenLayout.Rect right = columns.second();
        pickerX = right.x();
        pickerY = right.y() + 1;
        pickerW = right.width();
        int pickerHeight = Math.max(36, Math.min(48, right.height() - 120));
        picker.setBounds(pickerX, pickerY, pickerW, pickerHeight, 8);
        picker.setRgb(slots.get(selected).getter().getAsInt());
        int pickerBottom = pickerY + picker.totalHeight();
        rainbowY = pickerBottom + 14;
        neutralY = rainbowY + 10;
        alphaBarX = pickerX;
        alphaBarY = neutralY + 20;
        alphaBarW = pickerW;

        clearWidgets();
        int toggleY = right.bottom() - 47;
        addToggle("gui.athens_coins.theme_shadow", toggleY, 0);
        addToggle("gui.athens_coins.theme_bold", toggleY + 16, 1);
        addToggle("gui.athens_coins.theme_italic", toggleY + 32, 2);

        ScreenLayout.Rect left = columns.first();
        int presets = StatsTheme.presetCount();
        int gap = 3;
        int presetWidth = ScreenLayout.gridCellWidth(left, Math.max(1, presets), gap);
        for (int i = 0; i < presets; i++) {
            int index = i;
            addRenderableWidget(Button.builder(Component.literal(String.valueOf(i + 1)), ignored -> {
                        working = StatsTheme.preset(index);
                        rebuild();
                    })
                    .bounds(left.x() + i * (presetWidth + gap), left.bottom() - 16, presetWidth, 15)
                    .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.theme_reset")))
                    .build());
        }

        ScreenLayout.Rect footer = layout.footer().inset(6);
        int footerGap = 4;
        int cell = ScreenLayout.gridCellWidth(footer, 3, footerGap);
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.theme_save"), ignored -> {
                    StatsTheme.replace(working);
                    working.save();
                    minecraft.setScreen(parent);
                }).bounds(footer.x(), footer.y(), cell, Math.min(18, footer.height())).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.theme_reset"), ignored -> {
                    working = new StatsTheme();
                    rebuild();
                }).bounds(footer.x() + cell + footerGap, footer.y(), cell,
                        Math.min(18, footer.height())).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.back"),
                        ignored -> minecraft.setScreen(parent))
                .bounds(footer.x() + (cell + footerGap) * 2, footer.y(), cell,
                        Math.min(18, footer.height())).build());
    }

    private void addToggle(String key, int y, int type) {
        boolean state = switch (type) {
            case 0 -> working.textShadow;
            case 1 -> working.boldSections;
            default -> working.italicLabels;
        };
        Component fullLabel = formatLabel(key, state);
        addRenderableWidget(Button.builder(fullLabel, button -> {
                    boolean current;
                    if (type == 0) {
                        working.textShadow = !working.textShadow;
                        current = working.textShadow;
                    } else if (type == 1) {
                        working.boldSections = !working.boldSections;
                        current = working.boldSections;
                    } else {
                        working.italicLabels = !working.italicLabels;
                        current = working.italicLabels;
                    }
                    Component updated = formatLabel(key, current);
                    button.setMessage(updated);
                    button.setTooltip(Tooltip.create(updated));
                }).bounds(columns.second().x(), y, columns.second().width(), 15)
                .tooltip(Tooltip.create(fullLabel)).build());
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        hoverTooltip = null;
        ScreenLayout.Rect panel = layout.panel();
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), working.background());
        StatsScreen.outline(graphics, panel.x(), panel.y(), panel.width(), panel.height(), working.border());
        graphics.fill(panel.x() + 1, panel.y() + 1, panel.right() - 1, layout.header().bottom() - 2, working.titleBar());
        drawFitted(graphics, title.getString(), panel.x() + 8, panel.y() + 6,
                panel.width() - 16, 0xFF000000 | working.titleTextColor, mouseX, mouseY);
        renderSlots(graphics, mouseX, mouseY);
        renderPicker(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoverTooltip != null) {
            graphics.renderTooltip(font, hoverTooltip, mouseX, mouseY);
        }
    }

    private void renderSlots(GuiGraphics graphics, int mouseX, int mouseY) {
        ScreenLayout.Rect left = columns.first();
        for (int i = 0; i < slots.size(); i++) {
            StatsTheme.Slot slot = slots.get(i);
            int y = left.y() + i * SLOT_H;
            if (i == selected) {
                graphics.fill(left.x() - 2, y - 1, left.right(), y + SLOT_H - 1, 0x40FFFFFF);
            }
            graphics.fill(left.x(), y, left.x() + SWATCH, y + SWATCH,
                    0xFF000000 | slot.getter().getAsInt());
            StatsScreen.outline(graphics, left.x(), y, SWATCH, SWATCH, 0xFF000000);
            String alpha = slot.hasAlpha() ? slot.alphaGetter().getAsInt() * 100 / 255 + "%" : "";
            int alphaWidth = alpha.isEmpty() ? 0 : font.width(alpha) + 4;
            drawFitted(graphics, Component.translatable(slot.nameKey()).getString(),
                    left.x() + SWATCH + 5, y + 1,
                    Math.max(10, left.width() - SWATCH - alphaWidth - 7),
                    0xFF000000 | working.labelColor, mouseX, mouseY);
            if (!alpha.isEmpty()) {
                graphics.drawString(font, alpha, left.right() - font.width(alpha), y + 1,
                        0xFF000000 | working.labelColor, working.textShadow);
            }
        }
    }

    private void renderPicker(GuiGraphics graphics) {
        picker.render(graphics);
        StatsTheme.Slot slot = slots.get(selected);
        String hex = String.format(Locale.ROOT, "#%06X", picker.rgb());
        graphics.drawString(font, hex, pickerX, rainbowY - 11,
                0xFF000000 | working.valueColor, working.textShadow);
        drawSwatches(graphics, RAINBOW, rainbowY);
        drawSwatches(graphics, NEUTRALS, neutralY);
        if (slot.hasAlpha()) {
            int alpha = slot.alphaGetter().getAsInt();
            graphics.drawString(font, Component.translatable("gui.athens_coins.theme_opacity"),
                    alphaBarX, alphaBarY - 10, 0xFF000000 | working.labelColor, working.textShadow);
            for (int i = 0; i < alphaBarW / 4; i++) {
                int x0 = alphaBarX + i * 4;
                graphics.fill(x0, alphaBarY, Math.min(x0 + 4, alphaBarX + alphaBarW), alphaBarY + 8,
                        i % 2 == 0 ? 0xFF6E6E6E : 0xFF3A3A3A);
            }
            graphics.fill(alphaBarX, alphaBarY, alphaBarX + alphaBarW * alpha / 255, alphaBarY + 8,
                    0xFF000000 | slot.getter().getAsInt());
            StatsScreen.outline(graphics, alphaBarX, alphaBarY, alphaBarW, 8, 0xFF000000);
            int knob = alphaBarX + alphaBarW * alpha / 255;
            graphics.fill(knob - 1, alphaBarY - 2, knob + 2, alphaBarY + 10, 0xFFFFFFFF);
        }
    }

    private void drawSwatches(GuiGraphics graphics, int[] colors, int y) {
        ScreenLayout.Rect row = new ScreenLayout.Rect(pickerX, y, pickerW, 8);
        for (int i = 0; i < colors.length; i++) {
            ScreenLayout.Rect swatch = ScreenLayout.partition(row, colors.length, i);
            graphics.fill(swatch.x(), swatch.y(), swatch.right(), swatch.bottom(),
                    0xFF000000 | colors[i]);
            StatsScreen.outline(graphics, swatch.x(), swatch.y(), swatch.width(), swatch.height(),
                    0x60000000);
        }
    }

    private void drawFitted(GuiGraphics graphics, String text, int x, int y, int maxWidth,
                            int color, int mouseX, int mouseY) {
        String fitted = ScreenText.fit(font, text, maxWidth);
        graphics.drawString(font, fitted, x, y, color, working.textShadow);
        if (ScreenText.wasTruncated(font, text, maxWidth)
                && mouseX >= x && mouseX < x + maxWidth && mouseY >= y && mouseY < y + 10) {
            hoverTooltip = Component.literal(text);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ScreenLayout.Rect left = columns.first();
        for (int i = 0; i < slots.size(); i++) {
            int y = left.y() + i * SLOT_H;
            if (mouseX >= left.x() - 2 && mouseX < left.right()
                    && mouseY >= y - 1 && mouseY < y + SLOT_H - 1) {
                selected = i;
                picker.setRgb(slots.get(i).getter().getAsInt());
                return true;
            }
        }
        if (picker.mouseClicked(mouseX, mouseY)) {
            slots.get(selected).setter().accept(picker.rgb());
            return true;
        }
        if (applySwatch(mouseX, mouseY, RAINBOW, rainbowY)
                || applySwatch(mouseX, mouseY, NEUTRALS, neutralY)) {
            return true;
        }
        StatsTheme.Slot slot = slots.get(selected);
        if (slot.hasAlpha() && mouseY >= alphaBarY - 2 && mouseY < alphaBarY + 10
                && mouseX >= alphaBarX && mouseX < alphaBarX + alphaBarW) {
            draggingAlpha = true;
            applyAlpha(mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean applySwatch(double mouseX, double mouseY, int[] colors, int y) {
        ScreenLayout.Rect row = new ScreenLayout.Rect(pickerX, y, pickerW, 8);
        int index = ScreenLayout.partitionIndex(row, colors.length, mouseX, mouseY);
        if (index < 0) {
            return false;
        }
        apply(colors[index]);
        return true;
    }

    private void apply(int rgb) {
        slots.get(selected).setter().accept(rgb);
        picker.setRgb(rgb);
    }

    private void applyAlpha(double mouseX) {
        StatsTheme.Slot slot = slots.get(selected);
        int value = (int) Math.round((mouseX - alphaBarX) / (double) alphaBarW * 255.0D);
        slot.alphaSetter().accept(ScreenLayout.clamp(value, 0, 255));
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        StatsTheme.Slot slot = slots.get(selected);
        if (slot.hasAlpha()) {
            int step = (int) Math.signum(delta) * 8;
            slot.alphaSetter().accept(ScreenLayout.clamp(slot.alphaGetter().getAsInt() + step, 0, 255));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
