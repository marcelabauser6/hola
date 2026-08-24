package com.athensmc.athenscoins.stats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * What one stats hologram shows and how it looks.
 *
 * <p>This lives on the server, in the block entity's NBT, and that is the whole reason it exists. The
 * appearance of the stats table used to be {@code StatsTheme}: a JSON file in the client's config
 * folder that the server never saw. That is defensible for a private GUI and impossible for a
 * hologram, because a hologram is something <em>everyone</em> standing in the square looks at. If each
 * client kept its own colours, two players would describe the same object differently and an admin
 * setting it up could not show anyone else the result.</p>
 *
 * <p>One serialisation, not two. The config travels to the client inside the block entity's update tag
 * and comes back inside the save packet, and both use {@link #save()} / {@link #load}. A separate
 * {@code FriendlyByteBuf} pair would have been a second definition of the same twenty fields, free to
 * drift from the first - and the failure mode of a drifted reader is not an exception, it is a
 * hologram that comes back with its colours shuffled.</p>
 *
 * <p>Mutable with plain accessors, because the editor's job is to poke one field at a time from a
 * colour picker or a slider. Every setter clamps, so the editor cannot save something the renderer
 * would have to defend against.</p>
 */
public class HologramConfig {

    /** Enough for a full dashboard; beyond this a hologram is a wall of text nobody reads. */
    public static final int MAX_LINES = 18;
    public static final int TITLE_LIMIT = 48;
    public static final int LABEL_LIMIT = 32;

    public static final int MIN_SCALE = 25;
    public static final int MAX_SCALE = 300;
    public static final int MIN_SPACING = 8;
    public static final int MAX_SPACING = 24;
    /** Vertical offset in tenths of a block, measured from the top of the projector. */
    public static final int MIN_OFFSET = -20;
    public static final int MAX_OFFSET = 80;
    public static final int MIN_TOP_ROWS = 1;
    public static final int MAX_TOP_ROWS = 6;

    /**
     * One line of the hologram.
     *
     * @param label an override for the metric's own name; empty means "use the translated default".
     *              This is what makes the editor complete rather than a fixed menu: a server running
     *              in a language the mod does not ship, or one that calls its money something else,
     *              can write the line the way its players say it.
     */
    public record Line(StatsMetric metric, String label) {
        public Line {
            label = label == null ? "" : label.substring(0, Math.min(label.length(), LABEL_LIMIT));
        }

        public static Line of(StatsMetric metric) {
            return new Line(metric, "");
        }

        public boolean hasCustomLabel() {
            return !label.isEmpty();
        }
    }

    private String title = "";
    private final List<Line> lines = new ArrayList<>();

    private int titleColor = 0xFFD98F;
    private int labelColor = 0xBFB3C4;
    private int valueColor = 0xFFFFFF;
    private int accentColor = 0x4CD964;
    private int backgroundColor = 0x14101A;
    private int backgroundAlpha = 170;

    private int scalePercent = 100;
    private int lineSpacing = 10;
    private int heightOffsetTenths = 12;
    private int topRows = 3;

    private boolean showBackground = true;
    private boolean boldTitle = true;
    /** True to always face the viewer; false to stay flat on the projector's facing. */
    private boolean billboard = true;
    private boolean showLabels = true;

    public HologramConfig() {
    }

    /**
     * What a freshly placed projector shows.
     *
     * <p>A default that already displays something is deliberate: an empty hologram looks broken, and
     * the first question anyone has after placing one is whether it works.</p>
     */
    public static HologramConfig defaults() {
        HologramConfig config = new HologramConfig();
        config.title = "";
        config.lines.add(Line.of(StatsMetric.TOTAL_SUPPLY));
        config.lines.add(Line.of(StatsMetric.TOTAL_CASH));
        config.lines.add(Line.of(StatsMetric.ACCOUNTS));
        config.lines.add(Line.of(StatsMetric.BLANK));
        config.lines.add(Line.of(StatsMetric.TOP_HOLDERS));
        return config;
    }

    // ------------------------------------------------------------------ lines

    public List<Line> lines() {
        return lines;
    }

    public int lineCount() {
        return lines.size();
    }

    public boolean addLine(StatsMetric metric) {
        if (lines.size() >= MAX_LINES) {
            return false;
        }
        lines.add(Line.of(metric));
        return true;
    }

    public void removeLine(int index) {
        if (index >= 0 && index < lines.size()) {
            lines.remove(index);
        }
    }

    public void setLineMetric(int index, StatsMetric metric) {
        if (index >= 0 && index < lines.size()) {
            lines.set(index, new Line(metric, lines.get(index).label()));
        }
    }

    public void setLineLabel(int index, String label) {
        if (index >= 0 && index < lines.size()) {
            lines.set(index, new Line(lines.get(index).metric(), label));
        }
    }

    /**
     * Moves a line and returns where it ended up.
     *
     * <p>Returning the new index rather than a boolean is what lets the editor keep the moved row
     * selected. Without it, pressing "up" twice moves two different lines, because the selection stays
     * behind on the second press.</p>
     */
    public int moveLine(int index, int delta) {
        int target = index + delta;
        if (index < 0 || index >= lines.size() || target < 0 || target >= lines.size()) {
            return index;
        }
        lines.add(target, lines.remove(index));
        return target;
    }

    // ------------------------------------------------------------------ scalars

    public String title() {
        return title;
    }

    public void setTitle(String value) {
        String text = value == null ? "" : value;
        title = text.substring(0, Math.min(text.length(), TITLE_LIMIT));
    }

    public boolean hasTitle() {
        return !title.isEmpty();
    }

    public int titleColor() {
        return titleColor;
    }

    public void setTitleColor(int value) {
        titleColor = value & 0xFFFFFF;
    }

    public int labelColor() {
        return labelColor;
    }

    public void setLabelColor(int value) {
        labelColor = value & 0xFFFFFF;
    }

    public int valueColor() {
        return valueColor;
    }

    public void setValueColor(int value) {
        valueColor = value & 0xFFFFFF;
    }

    public int accentColor() {
        return accentColor;
    }

    public void setAccentColor(int value) {
        accentColor = value & 0xFFFFFF;
    }

    public int backgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(int value) {
        backgroundColor = value & 0xFFFFFF;
    }

    public int backgroundAlpha() {
        return backgroundAlpha;
    }

    public void setBackgroundAlpha(int value) {
        backgroundAlpha = clamp(value, 0, 255);
    }

    /** Background as ARGB, ready to hand to a renderer. */
    public int background() {
        return (backgroundAlpha << 24) | backgroundColor;
    }

    public int scalePercent() {
        return scalePercent;
    }

    public void setScalePercent(int value) {
        scalePercent = clamp(value, MIN_SCALE, MAX_SCALE);
    }

    public float scale() {
        return scalePercent / 100.0F;
    }

    public int lineSpacing() {
        return lineSpacing;
    }

    public void setLineSpacing(int value) {
        lineSpacing = clamp(value, MIN_SPACING, MAX_SPACING);
    }

    public int heightOffsetTenths() {
        return heightOffsetTenths;
    }

    public void setHeightOffsetTenths(int value) {
        heightOffsetTenths = clamp(value, MIN_OFFSET, MAX_OFFSET);
    }

    public float heightOffset() {
        return heightOffsetTenths / 10.0F;
    }

    public int topRows() {
        return topRows;
    }

    public void setTopRows(int value) {
        topRows = clamp(value, MIN_TOP_ROWS, MAX_TOP_ROWS);
    }

    public boolean showBackground() {
        return showBackground;
    }

    public void setShowBackground(boolean value) {
        showBackground = value;
    }

    public boolean boldTitle() {
        return boldTitle;
    }

    public void setBoldTitle(boolean value) {
        boldTitle = value;
    }

    public boolean billboard() {
        return billboard;
    }

    public void setBillboard(boolean value) {
        billboard = value;
    }

    public boolean showLabels() {
        return showLabels;
    }

    public void setShowLabels(boolean value) {
        showLabels = value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ------------------------------------------------------------------ copy

    public HologramConfig copy() {
        return load(save());
    }

    // ------------------------------------------------------------------ persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("title", title);
        ListTag list = new ListTag();
        for (Line line : lines) {
            CompoundTag entry = new CompoundTag();
            entry.putString("metric", line.metric().id());
            if (line.hasCustomLabel()) {
                entry.putString("label", line.label());
            }
            list.add(entry);
        }
        tag.put("lines", list);
        tag.putInt("titleColor", titleColor);
        tag.putInt("labelColor", labelColor);
        tag.putInt("valueColor", valueColor);
        tag.putInt("accentColor", accentColor);
        tag.putInt("bgColor", backgroundColor);
        tag.putInt("bgAlpha", backgroundAlpha);
        tag.putInt("scale", scalePercent);
        tag.putInt("spacing", lineSpacing);
        tag.putInt("offset", heightOffsetTenths);
        tag.putInt("topRows", topRows);
        tag.putBoolean("showBackground", showBackground);
        tag.putBoolean("boldTitle", boldTitle);
        tag.putBoolean("billboard", billboard);
        tag.putBoolean("showLabels", showLabels);
        return tag;
    }

    /**
     * Reads a saved hologram.
     *
     * <p>Every field goes through its own setter, so a hand-edited or corrupt value is clamped on the
     * way in instead of reaching the renderer. A missing field keeps the constructor's default rather
     * than becoming zero: {@code scale} read straight out of an older tag would be 0, and a hologram
     * scaled to nothing is indistinguishable from one that failed to load.</p>
     */
    public static HologramConfig load(CompoundTag tag) {
        HologramConfig config = new HologramConfig();
        if (tag == null) {
            return defaults();
        }
        config.setTitle(tag.getString("title"));
        ListTag list = tag.getList("lines", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && config.lines.size() < MAX_LINES; i++) {
            CompoundTag entry = list.getCompound(i);
            config.lines.add(new Line(StatsMetric.byName(entry.getString("metric")),
                    entry.getString("label")));
        }
        if (tag.contains("titleColor")) {
            config.setTitleColor(tag.getInt("titleColor"));
        }
        if (tag.contains("labelColor")) {
            config.setLabelColor(tag.getInt("labelColor"));
        }
        if (tag.contains("valueColor")) {
            config.setValueColor(tag.getInt("valueColor"));
        }
        if (tag.contains("accentColor")) {
            config.setAccentColor(tag.getInt("accentColor"));
        }
        if (tag.contains("bgColor")) {
            config.setBackgroundColor(tag.getInt("bgColor"));
        }
        if (tag.contains("bgAlpha")) {
            config.setBackgroundAlpha(tag.getInt("bgAlpha"));
        }
        if (tag.contains("scale")) {
            config.setScalePercent(tag.getInt("scale"));
        }
        if (tag.contains("spacing")) {
            config.setLineSpacing(tag.getInt("spacing"));
        }
        if (tag.contains("offset")) {
            config.setHeightOffsetTenths(tag.getInt("offset"));
        }
        if (tag.contains("topRows")) {
            config.setTopRows(tag.getInt("topRows"));
        }
        if (tag.contains("showBackground")) {
            config.setShowBackground(tag.getBoolean("showBackground"));
        }
        if (tag.contains("boldTitle")) {
            config.setBoldTitle(tag.getBoolean("boldTitle"));
        }
        if (tag.contains("billboard")) {
            config.setBillboard(tag.getBoolean("billboard"));
        }
        if (tag.contains("showLabels")) {
            config.setShowLabels(tag.getBoolean("showLabels"));
        }
        return config;
    }

    // ------------------------------------------------------------------ presets

    /** Ready-made looks, so an admin does not have to build one from scratch. */
    public static HologramConfig preset(int index) {
        HologramConfig config = defaults();
        switch (index) {
            case 1 -> {   // market board: rates and coins, no chrome
                config.lines.clear();
                config.lines.add(Line.of(StatsMetric.RATE_BRONZE));
                config.lines.add(Line.of(StatsMetric.RATE_SILVER));
                config.lines.add(Line.of(StatsMetric.RATE_GOLD));
                config.titleColor = 0xBFD8FF;
                config.accentColor = 0x8FE3FF;
                config.backgroundColor = 0x0E1524;
                config.backgroundAlpha = 190;
            }
            case 2 -> {   // leaderboard
                config.lines.clear();
                config.lines.add(Line.of(StatsMetric.TOP_HOLDERS));
                config.topRows = 6;
                config.showLabels = false;
                config.titleColor = 0xFFD98F;
                config.valueColor = 0x6BE06B;
                config.backgroundColor = 0x1E0E11;
                config.backgroundAlpha = 200;
            }
            case 3 -> {   // distribution watch
                config.lines.clear();
                config.lines.add(Line.of(StatsMetric.AVERAGE));
                config.lines.add(Line.of(StatsMetric.MEDIAN));
                config.lines.add(Line.of(StatsMetric.RICHEST));
                config.lines.add(Line.of(StatsMetric.TOP_TEN_SHARE));
                config.accentColor = 0xE0C060;
            }
            case 4 -> {   // no panel at all, just floating text
                config.showBackground = false;
                config.scalePercent = 140;
                config.titleColor = 0xFFFFFF;
                config.labelColor = 0xD0D0D0;
            }
            default -> {  // the shipped default
            }
        }
        return config;
    }

    public static int presetCount() {
        return 5;
    }
}
