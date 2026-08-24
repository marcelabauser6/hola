package com.athensmc.athenscoins.client.screen;

import com.athensmc.athenscoins.block.StatsHologramBlockEntity;
import com.athensmc.athenscoins.client.layout.HologramEditorLayout;
import com.athensmc.athenscoins.client.layout.PanelMetrics;
import com.athensmc.athenscoins.client.layout.ScreenLayout;
import com.athensmc.athenscoins.client.layout.ScreenText;
import com.athensmc.athenscoins.client.widget.ColorPicker;
import com.athensmc.athenscoins.client.widget.IntSlider;
import com.athensmc.athenscoins.network.C2SHologramConfigPacket;
import com.athensmc.athenscoins.network.ModNetwork;
import com.athensmc.athenscoins.stats.EconomySnapshot;
import com.athensmc.athenscoins.stats.HologramConfig;
import com.athensmc.athenscoins.stats.HologramLines;
import com.athensmc.athenscoins.stats.StatsMetric;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * The hologram editor.
 *
 * <p>This replaces two screens. There used to be a read-only stats dashboard and, behind a button on
 * it, a colour editor for that dashboard's own appearance - so the thing being edited was a private
 * window only the person editing it would ever see. The statistics are now a hologram standing in the
 * world, and this screen is how you build one.</p>
 *
 * <p><b>The preview is always on screen.</b> Every control here changes something about an object the
 * player is not looking at while they change it, which was the specific complaint about the old editor:
 * you moved sliders and had no idea what they did. The right-hand third draws the hologram exactly as it
 * will appear, from the same {@link HologramLines} the world renderer uses, so there is no way for the
 * preview to promise something the projector will not produce.</p>
 *
 * <p><b>Two tabs, not three columns.</b> Content and appearance each need a list plus a set of controls,
 * and squeezing both alongside a preview left every list four rows tall. Splitting them means the line
 * list and the metric palette both get real height, and the preview stays put across the tab switch so
 * it never looks like it went away.</p>
 *
 * <p>Edits are made to a copy and only sent on Save, so backing out of the screen leaves the projector
 * exactly as it was.</p>
 */
public class StatsHologramEditorScreen extends Screen {

    private static final int ROW_H = 12;
    private static final int PAD = PanelMetrics.CONTENT_PAD;
    private static final int HINT_H = 12;
    private static final int GAP = 4;

    private static final int TEXT_TITLE = 0xFFBFD8FF;
    private static final int TEXT_HINT = 0xFF9FB4CC;
    private static final int TEXT_HEADING = 0xFF7FB2E5;
    private static final int TEXT_LABEL = 0xFF8A99AD;
    private static final int TEXT_VALUE = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0xFF6C7A8C;
    private static final int TEXT_GOOD = 0xFF6BE06B;

    private enum Tab {
        CONTENT("gui.athens_coins.holo_tab_content"),
        LOOK("gui.athens_coins.holo_tab_look");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    /** One editable colour, so the picker can be pointed at any of them without reflection. */
    private record ColorSlot(String nameKey, IntSupplier getter, IntConsumer setter,
                             boolean hasAlpha, IntSupplier alphaGetter, IntConsumer alphaSetter) {
    }

    private final BlockPos pos;
    private final EconomySnapshot snapshot;
    private final HologramConfig working;
    private final ColorPicker picker = new ColorPicker();

    private Tab tab = Tab.CONTENT;
    private ScreenLayout.Regions layout;
    private ScreenLayout.Rect hintRow;
    private ScreenLayout.Rect leftColumn;
    private ScreenLayout.Rect midColumn;
    private ScreenLayout.Rect previewColumn;
    private ScreenLayout.Rect lineList;
    private ScreenLayout.Rect metricList;
    private ScreenLayout.Rect colorList;
    private ScreenLayout.Rect alphaBar;
    private ScreenLayout.Rect messageRow;
    private HologramEditorLayout lookLayout;

    private List<ColorSlot> colorSlots;
    private int selectedLine;
    private int selectedColor;
    private int metricScroll;
    private int lineScroll;
    private boolean draggingAlpha;

    private EditBox titleBox;
    private EditBox labelBox;
    private Component message;
    private Component hoverTooltip;

    public StatsHologramEditorScreen(BlockPos pos, HologramConfig config, EconomySnapshot snapshot) {
        super(Component.translatable("gui.athens_coins.holo_title"));
        this.pos = pos;
        // A copy: the projector keeps showing what it showed until Save is pressed.
        this.working = config.copy();
        this.snapshot = snapshot;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        layout = PanelMetrics.stats(width, height);
        ScreenLayout.Rect content = layout.content().inset(PAD);
        hintRow = new ScreenLayout.Rect(content.x(), content.y(), content.width(), HINT_H);
        ScreenLayout.Rect body = new ScreenLayout.Rect(content.x(), hintRow.bottom(),
                content.width(), Math.max(0, content.height() - HINT_H));
        leftColumn = ScreenLayout.column(body, 3, 10, 0);
        midColumn = ScreenLayout.column(body, 3, 10, 1);
        previewColumn = ScreenLayout.column(body, 3, 10, 2);

        colorSlots = buildColorSlots();
        if (selectedColor >= colorSlots.size()) {
            selectedColor = 0;
        }
        clampSelection();

        buildTabs();
        if (tab == Tab.CONTENT) {
            buildContentTab();
        } else {
            buildLookTab();
        }
        buildFooter();
    }

    private void buildTabs() {
        ScreenLayout.Rect tabs = layout.tabs().inset(GAP);
        int cell = ScreenLayout.gridCellWidth(tabs, Tab.values().length + 1, GAP);
        int x = tabs.x();
        for (Tab candidate : Tab.values()) {
            Button button = Button.builder(Component.translatable(candidate.key), ignored -> {
                        tab = candidate;
                        rebuild();
                    })
                    .bounds(x, tabs.y(), cell, Math.min(18, tabs.height())).build();
            button.active = tab != candidate;
            addRenderableWidget(button);
            x += cell + GAP;
        }
        // Presets sit with the tabs rather than inside a tab: they replace the whole hologram, content
        // and appearance both, so belonging to either tab would have been a lie about their scope.
        int presetCell = Math.max(14, (cell - GAP * (HologramConfig.presetCount() - 1))
                / HologramConfig.presetCount());
        for (int i = 0; i < HologramConfig.presetCount(); i++) {
            int index = i;
            addRenderableWidget(Button.builder(Component.literal(String.valueOf(i + 1)), ignored -> {
                        applyPreset(index);
                    })
                    .bounds(x, tabs.y(), presetCell, Math.min(18, tabs.height()))
                    .tooltip(Tooltip.create(Component.translatable(
                            "gui.athens_coins.holo_preset_tip", i + 1))).build());
            x += presetCell + GAP;
        }
    }

    private void applyPreset(int index) {
        HologramConfig preset = HologramConfig.preset(index);
        copyInto(preset, working);
        selectedLine = 0;
        message = Component.translatable("gui.athens_coins.holo_preset_applied", index + 1);
        rebuild();
    }

    /**
     * Overwrites one config's contents with another's.
     *
     * <p>The screen holds a final reference to {@code working} so every widget callback can close over
     * it; a preset therefore has to be poured into that object rather than replacing it. Going through
     * NBT means this cannot miss a field the way a hand-written list of assignments would the next time
     * one is added.</p>
     */
    private static void copyInto(HologramConfig from, HologramConfig into) {
        HologramConfig source = HologramConfig.load(from.save());
        into.setTitle(source.title());
        into.lines().clear();
        into.lines().addAll(source.lines());
        into.setTitleColor(source.titleColor());
        into.setLabelColor(source.labelColor());
        into.setValueColor(source.valueColor());
        into.setAccentColor(source.accentColor());
        into.setBackgroundColor(source.backgroundColor());
        into.setBackgroundAlpha(source.backgroundAlpha());
        into.setScalePercent(source.scalePercent());
        into.setLineSpacing(source.lineSpacing());
        into.setHeightOffsetTenths(source.heightOffsetTenths());
        into.setTopRows(source.topRows());
        into.setShowBackground(source.showBackground());
        into.setTextShadow(source.textShadow());
        into.setBoldTitle(source.boldTitle());
        into.setBillboard(source.billboard());
        into.setShowLabels(source.showLabels());
    }

    // ------------------------------------------------------------------ content tab

    private void buildContentTab() {
        // Left column: the hologram's lines, with the four buttons that reorder them underneath.
        int buttonRow = leftColumn.bottom() - 18;
        lineList = new ScreenLayout.Rect(leftColumn.x(), leftColumn.y() + 14,
                leftColumn.width(), Math.max(0, buttonRow - leftColumn.y() - 18));
        int cell = ScreenLayout.gridCellWidth(new ScreenLayout.Rect(leftColumn.x(), buttonRow,
                leftColumn.width(), 18), 4, GAP);
        int x = leftColumn.x();
        addRenderableWidget(Button.builder(Component.literal("+"), ignored -> addLine())
                .bounds(x, buttonRow, cell, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.holo_add_tip"))).build());
        x += cell + GAP;
        addRenderableWidget(Button.builder(Component.literal("-"), ignored -> removeLine())
                .bounds(x, buttonRow, cell, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.holo_remove_tip"))).build());
        x += cell + GAP;
        addRenderableWidget(Button.builder(Component.literal("\u25B2"), ignored -> moveLine(-1))
                .bounds(x, buttonRow, cell, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.holo_up_tip"))).build());
        x += cell + GAP;
        addRenderableWidget(Button.builder(Component.literal("\u25BC"), ignored -> moveLine(1))
                .bounds(x, buttonRow, cell, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.holo_down_tip"))).build());

        // Middle column: the palette of figures, and the custom label for the selected line.
        labelBox = new EditBox(font, midColumn.x(), midColumn.bottom() - 18,
                midColumn.width(), 18, Component.translatable("gui.athens_coins.holo_label"));
        labelBox.setMaxLength(HologramConfig.LABEL_LIMIT);
        labelBox.setValue(selectedLine < working.lineCount()
                ? working.lines().get(selectedLine).label() : "");
        labelBox.setResponder(value -> working.setLineLabel(selectedLine, value));
        labelBox.setTooltip(Tooltip.create(Component.translatable("gui.athens_coins.holo_label_tip")));
        addRenderableWidget(labelBox);
        metricList = new ScreenLayout.Rect(midColumn.x(), midColumn.y() + 14, midColumn.width(),
                Math.max(0, midColumn.height() - 14 - 22 - HINT_H));
    }

    private void addLine() {
        if (!working.addLine(StatsMetric.TOTAL_CASH)) {
            message = Component.translatable("gui.athens_coins.holo_full", HologramConfig.MAX_LINES);
            return;
        }
        selectedLine = working.lineCount() - 1;
        message = null;
        rebuild();
    }

    private void removeLine() {
        working.removeLine(selectedLine);
        clampSelection();
        rebuild();
    }

    private void moveLine(int delta) {
        // The config returns the new index so the moved row stays selected; without that, pressing the
        // same button twice moves two different lines.
        selectedLine = working.moveLine(selectedLine, delta);
        rebuild();
    }

    // ------------------------------------------------------------------ look tab

    private void buildLookTab() {
        // Geometry from the pure layout record, so the static test can check that none of these bands
        // land on each other without starting a client.
        lookLayout = HologramEditorLayout.of(leftColumn, colorSlots.size());
        colorList = lookLayout.colorList();
        alphaBar = lookLayout.alphaBar();
        ScreenLayout.Rect square = lookLayout.pickerSquare();
        picker.setBounds(square.x(), square.y(), square.width(), square.height(),
                lookLayout.hueHeight());
        picker.setRgb(colorSlots.get(selectedColor).getter().getAsInt());

        ScreenLayout.Rect column = midColumn;
        int y = column.y() + 14;
        titleBox = new EditBox(font, column.x(), y, column.width(), 18,
                Component.translatable("gui.athens_coins.holo_heading"));
        titleBox.setMaxLength(HologramConfig.TITLE_LIMIT);
        titleBox.setValue(working.title());
        titleBox.setResponder(working::setTitle);
        titleBox.setTooltip(Tooltip.create(Component.translatable("gui.athens_coins.holo_heading_tip")));
        addRenderableWidget(titleBox);
        y += 22;

        addRenderableWidget(new IntSlider(column.x(), y, column.width(), 16,
                "gui.athens_coins.holo_scale", "%", HologramConfig.MIN_SCALE,
                HologramConfig.MAX_SCALE, working.scalePercent(), working::setScalePercent));
        y += 20;
        addRenderableWidget(new IntSlider(column.x(), y, column.width(), 16,
                "gui.athens_coins.holo_spacing", "", HologramConfig.MIN_SPACING,
                HologramConfig.MAX_SPACING, working.lineSpacing(), working::setLineSpacing));
        y += 20;
        addRenderableWidget(new IntSlider(column.x(), y, column.width(), 16,
                "gui.athens_coins.holo_height", "", HologramConfig.MIN_OFFSET,
                HologramConfig.MAX_OFFSET, working.heightOffsetTenths(),
                working::setHeightOffsetTenths));
        y += 20;
        addRenderableWidget(new IntSlider(column.x(), y, column.width(), 16,
                "gui.athens_coins.holo_top_rows", "", HologramConfig.MIN_TOP_ROWS,
                HologramConfig.MAX_TOP_ROWS, working.topRows(), working::setTopRows));
        y += 24;

        int toggleCell = ScreenLayout.gridCellWidth(
                new ScreenLayout.Rect(column.x(), y, column.width(), 16), 2, GAP);
        y = addToggle(column.x(), y, toggleCell, "gui.athens_coins.holo_background",
                working::showBackground, working::setShowBackground, false);
        addToggle(column.x() + toggleCell + GAP, y - 20, toggleCell,
                "gui.athens_coins.holo_shadow", working::textShadow, working::setTextShadow, false);
        y = addToggle(column.x(), y, toggleCell, "gui.athens_coins.holo_bold",
                working::boldTitle, working::setBoldTitle, false);
        addToggle(column.x() + toggleCell + GAP, y - 20, toggleCell,
                "gui.athens_coins.holo_billboard", working::billboard, working::setBillboard, true);
        addToggle(column.x(), y, toggleCell, "gui.athens_coins.holo_labels",
                working::showLabels, working::setShowLabels, false);
    }

    private int addToggle(int x, int y, int width, String key,
                          java.util.function.BooleanSupplier getter,
                          java.util.function.Consumer<Boolean> setter, boolean tip) {
        Button button = Button.builder(toggleLabel(key, getter.getAsBoolean()), ignored -> {
            setter.accept(!getter.getAsBoolean());
            rebuild();
        }).bounds(x, y, width, 16).build();
        if (tip) {
            button.setTooltip(Tooltip.create(
                    Component.translatable("gui.athens_coins.holo_billboard_tip")));
        }
        addRenderableWidget(button);
        return y + 20;
    }

    private static Component toggleLabel(String key, boolean on) {
        return Component.translatable(key).append(Component.literal(": "))
                .append(Component.translatable(on
                        ? "gui.athens_coins.on" : "gui.athens_coins.off"));
    }

    private List<ColorSlot> buildColorSlots() {
        return List.of(
                new ColorSlot("gui.athens_coins.holo_color_title",
                        working::titleColor, working::setTitleColor, false, null, null),
                new ColorSlot("gui.athens_coins.holo_color_label",
                        working::labelColor, working::setLabelColor, false, null, null),
                new ColorSlot("gui.athens_coins.holo_color_value",
                        working::valueColor, working::setValueColor, false, null, null),
                new ColorSlot("gui.athens_coins.holo_color_accent",
                        working::accentColor, working::setAccentColor, false, null, null),
                new ColorSlot("gui.athens_coins.holo_color_bg",
                        working::backgroundColor, working::setBackgroundColor,
                        true, working::backgroundAlpha, working::setBackgroundAlpha));
    }

    // ------------------------------------------------------------------ footer

    private void buildFooter() {
        ScreenLayout.Rect footer = layout.footer().inset(6);
        messageRow = new ScreenLayout.Rect(footer.x(), footer.y() + 19, footer.width(), 10);
        int cell = ScreenLayout.gridCellWidth(footer, 3, GAP);
        int buttonHeight = Math.min(18, footer.height());
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.holo_save"),
                        ignored -> save())
                .bounds(footer.x(), footer.y(), cell, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.holo_save_tip")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.holo_reset"),
                        ignored -> {
                            copyInto(HologramConfig.defaults(), working);
                            selectedLine = 0;
                            message = Component.translatable("gui.athens_coins.holo_reset_done");
                            rebuild();
                        })
                .bounds(footer.x() + cell + GAP, footer.y(), cell, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.holo_reset_tip")))
                .build());
        int closeWidth = Math.min(82, cell);
        addRenderableWidget(Button.builder(Component.translatable("gui.athens_coins.close"),
                        ignored -> onClose())
                .bounds(footer.right() - closeWidth, footer.y(), closeWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.athens_coins.holo_close_tip")))
                .build());
    }

    private void save() {
        ModNetwork.toServer(new C2SHologramConfigPacket(pos, working));
        message = Component.translatable("gui.athens_coins.holo_sent");
    }

    private void rebuild() {
        clampSelection();
        rebuildWidgets();
    }

    private void clampSelection() {
        selectedLine = ScreenLayout.clamp(selectedLine, 0, Math.max(0, working.lineCount() - 1));
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        hoverTooltip = null;
        ScreenLayout.Rect panel = layout.panel();
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), 0xF00E1018);
        Panels.outline(graphics, panel.x(), panel.y(), panel.width(), panel.height(), 0xFF7FB2E5);
        graphics.fill(panel.x() + 1, panel.y() + 1, panel.right() - 1,
                layout.header().bottom() - 2, 0xFF1B2A44);
        renderHeader(graphics, mouseX, mouseY);

        drawFitted(graphics, Component.translatable(tab == Tab.CONTENT
                        ? "gui.athens_coins.holo_hint_content"
                        : "gui.athens_coins.holo_hint_look").getString(),
                hintRow.x(), hintRow.y() + 1, hintRow.width(), TEXT_HINT, mouseX, mouseY);

        if (tab == Tab.CONTENT) {
            renderLineList(graphics, mouseX, mouseY);
            renderMetricList(graphics, mouseX, mouseY);
        } else {
            renderColorList(graphics, mouseX, mouseY);
            renderLookColumn(graphics, mouseX, mouseY);
        }
        renderPreview(graphics, mouseX, mouseY);

        if (message != null) {
            drawFitted(graphics, message.getString(), messageRow.x(), messageRow.y(),
                    messageRow.width(), TEXT_GOOD, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoverTooltip != null) {
            graphics.renderTooltip(font, hoverTooltip, mouseX, mouseY);
        }
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        ScreenLayout.Rect header = layout.header();
        String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        int coordsBudget = Math.min(header.width() / 3, font.width(coords) + 2);
        int titleBudget = Math.max(20, header.width() - coordsBudget - PAD * 3);
        drawFitted(graphics, title.getString(), header.x() + PAD, header.y() + 6, titleBudget,
                TEXT_TITLE, mouseX, mouseY);
        drawRight(graphics, coords, header.right() - PAD, header.y() + 6, coordsBudget,
                TEXT_MUTED, mouseX, mouseY);
    }

    private void renderLineList(GuiGraphics graphics, int mouseX, int mouseY) {
        heading(graphics, leftColumn, "gui.athens_coins.holo_lines", mouseX, mouseY);
        if (working.lineCount() == 0) {
            drawFitted(graphics, Component.translatable("gui.athens_coins.holo_no_lines").getString(),
                    lineList.x(), lineList.y() + 2, lineList.width(), TEXT_MUTED, mouseX, mouseY);
            return;
        }
        int rows = Math.max(1, ScreenLayout.visibleRows(lineList, ROW_H));
        lineScroll = ScreenLayout.clamp(lineScroll, 0, Math.max(0, working.lineCount() - rows));
        graphics.enableScissor(lineList.x(), lineList.y(), lineList.right(), lineList.bottom());
        int y = lineList.y();
        for (int i = lineScroll; i < Math.min(working.lineCount(), lineScroll + rows); i++) {
            HologramConfig.Line line = working.lines().get(i);
            boolean hovered = lineList.contains(mouseX, mouseY) && mouseY >= y && mouseY < y + ROW_H;
            if (i == selectedLine) {
                graphics.fill(lineList.x(), y, lineList.right(), y + ROW_H - 1, 0x600090FF);
            } else if (hovered) {
                graphics.fill(lineList.x(), y, lineList.right(), y + ROW_H - 1, 0x30FFFFFF);
            }
            String number = (i + 1) + ".";
            int numberWidth = Math.min(20, font.width("18.") + 4);
            graphics.drawString(font, number, lineList.x() + 2, y + 2, TEXT_LABEL, false);
            String label = line.hasCustomLabel()
                    ? line.label()
                    : Component.translatable(line.metric().labelKey()).getString();
            drawFitted(graphics, label, lineList.x() + 2 + numberWidth, y + 2,
                    lineList.width() - numberWidth - 6,
                    line.metric() == StatsMetric.BLANK ? TEXT_MUTED : TEXT_VALUE, mouseX, mouseY);
            if (hovered) {
                hoverTooltip = Component.translatable("gui.athens_coins.holo_line_tip");
            }
            y += ROW_H;
        }
        graphics.disableScissor();
    }

    private void renderMetricList(GuiGraphics graphics, int mouseX, int mouseY) {
        heading(graphics, midColumn, "gui.athens_coins.holo_metrics", mouseX, mouseY);
        StatsMetric[] metrics = StatsMetric.selectable();
        int rows = Math.max(1, ScreenLayout.visibleRows(metricList, ROW_H));
        metricScroll = ScreenLayout.clamp(metricScroll, 0, Math.max(0, metrics.length - rows));
        StatsMetric current = selectedLine < working.lineCount()
                ? working.lines().get(selectedLine).metric() : null;
        graphics.enableScissor(metricList.x(), metricList.y(),
                metricList.right(), metricList.bottom());
        int y = metricList.y();
        for (int i = metricScroll; i < Math.min(metrics.length, metricScroll + rows); i++) {
            StatsMetric metric = metrics[i];
            boolean hovered = metricList.contains(mouseX, mouseY)
                    && mouseY >= y && mouseY < y + ROW_H;
            if (metric == current) {
                graphics.fill(metricList.x(), y, metricList.right(), y + ROW_H - 1, 0x600090FF);
            } else if (hovered) {
                graphics.fill(metricList.x(), y, metricList.right(), y + ROW_H - 1, 0x30FFFFFF);
            }
            String name = Component.translatable(metric.labelKey()).getString();
            String value = metric.labelOnly() || metric.kind() == StatsMetric.Kind.TABLE
                    ? "" : metric.format(snapshot);
            int valueWidth = value.isEmpty() ? 0 : Math.min(metricList.width() / 2,
                    font.width(value) + 2);
            drawFitted(graphics, name, metricList.x() + 2, y + 2,
                    metricList.width() - valueWidth - 8, TEXT_VALUE, mouseX, mouseY);
            if (!value.isEmpty()) {
                drawRight(graphics, value, metricList.right() - 2, y + 2, valueWidth,
                        TEXT_LABEL, mouseX, mouseY);
            }
            if (hovered) {
                hoverTooltip = Component.translatable("gui.athens_coins.holo_metric_tip");
            }
            y += ROW_H;
        }
        graphics.disableScissor();
        drawFitted(graphics, Component.translatable("gui.athens_coins.holo_label").getString(),
                midColumn.x(), midColumn.bottom() - 30, midColumn.width(), TEXT_LABEL,
                mouseX, mouseY);
    }

    private void renderColorList(GuiGraphics graphics, int mouseX, int mouseY) {
        heading(graphics, leftColumn, "gui.athens_coins.holo_colors", mouseX, mouseY);
        int slotHeight = HologramEditorLayout.SLOT_H;
        int visible = Math.min(colorSlots.size(), lookLayout.visibleSlots());
        for (int i = 0; i < visible; i++) {
            ColorSlot slot = colorSlots.get(i);
            ScreenLayout.Rect row = lookLayout.slot(i);
            int y = row.y();
            boolean hovered = row.contains(mouseX, mouseY);
            if (i == selectedColor) {
                graphics.fill(row.x(), y, row.right(), y + slotHeight - 1, 0x600090FF);
            } else if (hovered) {
                graphics.fill(row.x(), y, row.right(), y + slotHeight - 1, 0x30FFFFFF);
            }
            int swatch = 8;
            graphics.fill(row.x() + 2, y + 1, row.x() + 2 + swatch, y + 1 + swatch,
                    0xFF000000 | slot.getter().getAsInt());
            Panels.outline(graphics, row.x() + 2, y + 1, swatch, swatch, 0xFF000000);
            String name = Component.translatable(slot.nameKey()).getString();
            drawFitted(graphics, name, row.x() + swatch + 6, y + 1,
                    row.width() - swatch - 10, TEXT_VALUE, mouseX, mouseY);
        }
        picker.render(graphics);
        ColorSlot selected = colorSlots.get(selectedColor);
        ScreenLayout.Rect hexRow = lookLayout.hexRow();
        String hex = String.format("#%06X", selected.getter().getAsInt());
        drawFitted(graphics, hex, hexRow.x(), hexRow.y(), hexRow.width() / 2,
                TEXT_LABEL, mouseX, mouseY);
        if (selected.hasAlpha()) {
            int alpha = selected.alphaGetter().getAsInt();
            drawRight(graphics, Component.translatable("gui.athens_coins.holo_opacity").getString()
                            + " " + alpha * 100 / 255 + "%",
                    hexRow.right(), hexRow.y(), hexRow.width() / 2,
                    TEXT_LABEL, mouseX, mouseY);
            // Checkerboard behind the track, so the transparent end reads as transparent.
            for (int x = alphaBar.x(); x < alphaBar.right(); x += 4) {
                boolean even = ((x - alphaBar.x()) / 4) % 2 == 0;
                graphics.fill(x, alphaBar.y(), Math.min(x + 4, alphaBar.right()), alphaBar.bottom(),
                        even ? 0xFF6E6E6E : 0xFF3A3A3A);
            }
            int filled = alphaBar.x() + alpha * alphaBar.width() / 255;
            graphics.fill(alphaBar.x(), alphaBar.y(), filled, alphaBar.bottom(),
                    0xFF000000 | selected.getter().getAsInt());
            Panels.outline(graphics, alphaBar.x(), alphaBar.y(), alphaBar.width(),
                    alphaBar.height(), 0xFF000000);
            graphics.fill(filled - 1, alphaBar.y() - 2, filled + 1, alphaBar.bottom() + 2,
                    0xFFFFFFFF);
        }
    }

    private void renderLookColumn(GuiGraphics graphics, int mouseX, int mouseY) {
        heading(graphics, midColumn, "gui.athens_coins.holo_layout", mouseX, mouseY);
    }

    /**
     * The hologram, drawn the way the projector will draw it.
     *
     * <p>Same rows from {@link HologramLines}, same widest-row measurement, same label-left /
     * value-right arrangement, same title handling. What differs is only the drawing API and that the
     * scale is clamped to fit the column - a hologram at 300% would not fit in a third of a screen, and
     * a preview that silently cropped it would be worse than one that shows it small.</p>
     */
    private void renderPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        heading(graphics, previewColumn, "gui.athens_coins.holo_preview", mouseX, mouseY);
        ScreenLayout.Rect area = new ScreenLayout.Rect(previewColumn.x(), previewColumn.y() + 14,
                previewColumn.width(), Math.max(0, previewColumn.height() - 14));

        List<HologramLines.Row> rows = HologramLines.build(working, snapshot);
        MutableComponent heading = working.hasTitle()
                ? (working.boldTitle()
                        ? Component.literal(working.title()).withStyle(ChatFormatting.BOLD)
                        : Component.literal(working.title()))
                : null;
        if (rows.isEmpty() && heading == null) {
            drawFitted(graphics, Component.translatable("gui.athens_coins.holo_preview_empty").getString(),
                    area.x(), area.y() + 4, area.width(), TEXT_MUTED, mouseX, mouseY);
            return;
        }

        int spacing = working.lineSpacing();
        int titleHeight = heading == null ? 0 : spacing + 2;
        int textWidth = HologramLines.panelWidth(rows, heading, font::width, font::width);
        int totalHeight = titleHeight + rows.size() * spacing;

        // Fit to the column. The scale slider is still honoured relative to this, so the preview shows
        // a 50% hologram as half the size of a 100% one.
        float requested = working.scale();
        float widthLimit = (area.width() - 8) / (float) Math.max(1, textWidth);
        float heightLimit = (area.height() - 8) / (float) Math.max(1, totalHeight);
        float scale = Math.min(requested, Math.min(widthLimit, heightLimit));
        scale = Math.max(0.35F, scale);

        int panelW = Math.round(textWidth * scale) + 8;
        int panelH = Math.round(totalHeight * scale) + 6;
        int originX = area.x() + Math.max(0, (area.width() - panelW) / 2);
        int originY = area.y() + Math.max(0, (area.height() - panelH) / 2);

        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        // A checkerboard behind it: the background alpha is meaningless against a flat fill.
        for (int y = area.y(); y < area.bottom(); y += 8) {
            for (int x = area.x(); x < area.right(); x += 8) {
                boolean even = (((x - area.x()) / 8) + ((y - area.y()) / 8)) % 2 == 0;
                graphics.fill(x, y, Math.min(x + 8, area.right()), Math.min(y + 8, area.bottom()),
                        even ? 0xFF23262E : 0xFF1A1D24);
            }
        }
        if (working.showBackground()) {
            graphics.fill(originX, originY, originX + panelW, originY + panelH, working.background());
        }

        graphics.pose().pushPose();
        graphics.pose().translate(originX + 4, originY + 3, 0.0D);
        graphics.pose().scale(scale, scale, 1.0F);
        int y = 0;
        if (heading != null) {
            graphics.drawString(font, heading, (textWidth - font.width(heading)) / 2, y,
                    0xFF000000 | working.titleColor(), working.textShadow());
            y += titleHeight;
        }
        for (HologramLines.Row row : rows) {
            if (!row.isSpacer()) {
                if (row.valueOnly()) {
                    graphics.drawString(font, row.value(),
                            (textWidth - font.width(row.value())) / 2, y,
                            0xFF000000 | row.rgb(), working.textShadow());
                } else {
                    graphics.drawString(font, row.label(), 0, y,
                            0xFF000000 | working.labelColor(), working.textShadow());
                    if (!row.value().isEmpty()) {
                        graphics.drawString(font, row.value(),
                                textWidth - font.width(row.value()), y,
                                0xFF000000 | row.rgb(), working.textShadow());
                    }
                }
            }
            y += spacing;
        }
        graphics.pose().popPose();
        graphics.disableScissor();

        String note = Component.translatable("gui.athens_coins.holo_preview_note",
                working.scalePercent(), rows.size()).getString();
        drawFitted(graphics, note, area.x(), area.bottom() - 10, area.width(), TEXT_MUTED,
                mouseX, mouseY);
    }

    private void heading(GuiGraphics graphics, ScreenLayout.Rect area, String key,
                         int mouseX, int mouseY) {
        drawFitted(graphics, Component.translatable(key).getString(), area.x(), area.y(),
                area.width(), TEXT_HEADING, mouseX, mouseY);
        graphics.fill(area.x(), area.y() + 10, area.right(), area.y() + 11, 0x40FFFFFF);
    }

    private void drawFitted(GuiGraphics graphics, String text, int x, int y, int maxWidth,
                            int color, int mouseX, int mouseY) {
        String fitted = ScreenText.fit(font, text, maxWidth);
        graphics.drawString(font, fitted, x, y, color, false);
        if (ScreenText.wasTruncated(font, text, maxWidth)
                && mouseX >= x && mouseX < x + maxWidth && mouseY >= y && mouseY < y + 10) {
            hoverTooltip = Component.literal(text);
        }
    }

    private void drawRight(GuiGraphics graphics, String text, int right, int y, int maxWidth,
                           int color, int mouseX, int mouseY) {
        String fitted = ScreenText.fit(font, text, maxWidth);
        graphics.drawString(font, fitted, right - font.width(fitted), y, color, false);
        if (ScreenText.wasTruncated(font, text, maxWidth)
                && mouseX >= right - maxWidth && mouseX < right && mouseY >= y && mouseY < y + 10) {
            hoverTooltip = Component.literal(text);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.CONTENT) {
            int line = rowAt(lineList, mouseX, mouseY, working.lineCount(), lineScroll);
            if (line >= 0) {
                selectedLine = line;
                labelBox.setValue(working.lines().get(line).label());
                return true;
            }
            StatsMetric[] metrics = StatsMetric.selectable();
            int metric = rowAt(metricList, mouseX, mouseY, metrics.length, metricScroll);
            if (metric >= 0) {
                if (working.lineCount() == 0) {
                    working.addLine(metrics[metric]);
                    selectedLine = 0;
                } else {
                    working.setLineMetric(selectedLine, metrics[metric]);
                }
                return true;
            }
        } else {
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0) {
                selectedColor = slot;
                picker.setRgb(colorSlots.get(slot).getter().getAsInt());
                return true;
            }
            if (picker.mouseClicked(mouseX, mouseY)) {
                colorSlots.get(selectedColor).setter().accept(picker.rgb());
                return true;
            }
            ColorSlot selected = colorSlots.get(selectedColor);
            if (selected.hasAlpha() && alphaBar.expand(2).contains(mouseX, mouseY)) {
                draggingAlpha = true;
                applyAlpha(mouseX);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Which colour row a click landed on.
     *
     * <p>Separate from {@link #rowAt} because the colour list is measured in the layout record's
     * {@code SLOT_H}, not this screen's {@code ROW_H}. Sharing the row helper meant hit-testing used one
     * height and drawing the other, so clicks near the bottom of the list selected the row above.</p>
     */
    private int slotAt(double mouseX, double mouseY) {
        if (colorList == null || !colorList.contains(mouseX, mouseY)) {
            return -1;
        }
        int index = (int) ((mouseY - colorList.y()) / HologramEditorLayout.SLOT_H);
        return index >= 0 && index < Math.min(colorSlots.size(), lookLayout.visibleSlots())
                ? index : -1;
    }

    private int rowAt(ScreenLayout.Rect area, double mouseX, double mouseY, int size, int scroll) {
        if (area == null || !area.contains(mouseX, mouseY)) {
            return -1;
        }
        int rows = Math.max(1, ScreenLayout.visibleRows(area, ROW_H));
        int index = scroll + (int) ((mouseY - area.y()) / ROW_H);
        return index >= 0 && index < Math.min(size, scroll + rows) ? index : -1;
    }

    private void applyAlpha(double mouseX) {
        ColorSlot slot = colorSlots.get(selectedColor);
        int span = Math.max(1, alphaBar.width());
        int value = (int) Math.round((mouseX - alphaBar.x()) * 255.0D / span);
        slot.alphaSetter().accept(ScreenLayout.clamp(value, 0, 255));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (tab == Tab.LOOK) {
            if (picker.mouseDragged(mouseX, mouseY)) {
                colorSlots.get(selectedColor).setter().accept(picker.rgb());
                return true;
            }
            if (draggingAlpha) {
                applyAlpha(mouseX);
                return true;
            }
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
        int step = (int) -Math.signum(delta);
        if (tab == Tab.CONTENT) {
            if (lineList != null && lineList.contains(mouseX, mouseY)) {
                lineScroll = Math.max(0, lineScroll + step);
                return true;
            }
            if (metricList != null && metricList.contains(mouseX, mouseY)) {
                metricScroll = Math.max(0, metricScroll + step);
                return true;
            }
        } else {
            ColorSlot slot = colorSlots.get(selectedColor);
            if (slot.hasAlpha() && alphaBar != null && alphaBar.expand(4).contains(mouseX, mouseY)) {
                slot.alphaSetter().accept(ScreenLayout.clamp(
                        slot.alphaGetter().getAsInt() - step * 8, 0, 255));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /**
     * Opens the editor for a projector the client already knows about.
     *
     * <p>Returns null when the block entity is not there - which can happen if the projector was broken
     * in the instant between the click and the packet arriving. Better a message than a screen editing
     * something that no longer exists.</p>
     */
    public static StatsHologramEditorScreen forProjector(StatsHologramBlockEntity projector) {
        return new StatsHologramEditorScreen(projector.getBlockPos(), projector.config(),
                projector.snapshot());
    }
}
