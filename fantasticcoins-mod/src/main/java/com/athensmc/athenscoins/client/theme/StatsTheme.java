package com.athensmc.athenscoins.client.theme;

import com.athensmc.athenscoins.AthensCoinsMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Appearance of the economy stats table, edited in game and saved per client.
 *
 * <p>Colours are stored as plain {@code 0xRRGGBB} with opacity kept separate, so the editor can
 * offer a single alpha slider without the user having to think in ARGB.</p>
 */
@OnlyIn(Dist.CLIENT)
public class StatsTheme {

    public static final String FILE_NAME = "fantasticcurrency-stats-theme.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static StatsTheme active;

    // ---- panel
    public int backgroundColor = 0x14101A;
    public int backgroundAlpha = 235;
    public int borderColor = 0xC9A227;
    public int titleBarColor = 0x2A1F33;
    public int titleTextColor = 0xFFD98F;

    // ---- table
    public int sectionColor = 0x9EC5D8;
    public int labelColor = 0xBFB3C4;
    public int valueColor = 0xFFFFFF;
    public int accentColor = 0x4CD964;
    public int rowColor = 0xFFFFFF;
    public int rowAlpha = 18;

    // ---- text format
    public boolean textShadow = true;
    public boolean boldSections = true;
    public boolean italicLabels = false;

    // ------------------------------------------------------------------ access

    public static StatsTheme get() {
        if (active == null) {
            active = load();
        }
        return active;
    }

    public static void replace(StatsTheme theme) {
        active = theme;
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    public static StatsTheme load() {
        Path file = path();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                StatsTheme loaded = GSON.fromJson(reader, StatsTheme.class);
                if (loaded != null) {
                    return loaded.clamped();
                }
            } catch (IOException | RuntimeException exception) {
                AthensCoinsMod.LOGGER.warn("Could not read {}, using defaults", FILE_NAME, exception);
            }
        }
        return new StatsTheme();
    }

    public void save() {
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            AthensCoinsMod.LOGGER.error("Could not write {}", FILE_NAME, exception);
        }
    }

    public StatsTheme copy() {
        StatsTheme copy = new StatsTheme();
        copy.backgroundColor = backgroundColor;
        copy.backgroundAlpha = backgroundAlpha;
        copy.borderColor = borderColor;
        copy.titleBarColor = titleBarColor;
        copy.titleTextColor = titleTextColor;
        copy.sectionColor = sectionColor;
        copy.labelColor = labelColor;
        copy.valueColor = valueColor;
        copy.accentColor = accentColor;
        copy.rowColor = rowColor;
        copy.rowAlpha = rowAlpha;
        copy.textShadow = textShadow;
        copy.boldSections = boldSections;
        copy.italicLabels = italicLabels;
        return copy;
    }

    private StatsTheme clamped() {
        backgroundAlpha = Math.max(0, Math.min(255, backgroundAlpha));
        rowAlpha = Math.max(0, Math.min(255, rowAlpha));
        backgroundColor &= 0xFFFFFF;
        borderColor &= 0xFFFFFF;
        titleBarColor &= 0xFFFFFF;
        titleTextColor &= 0xFFFFFF;
        sectionColor &= 0xFFFFFF;
        labelColor &= 0xFFFFFF;
        valueColor &= 0xFFFFFF;
        accentColor &= 0xFFFFFF;
        rowColor &= 0xFFFFFF;
        return this;
    }

    // ------------------------------------------------------------------ derived ARGB

    public int background() {
        return (backgroundAlpha << 24) | backgroundColor;
    }

    public int titleBar() {
        return 0xFF000000 | titleBarColor;
    }

    public int border() {
        return 0xFF000000 | borderColor;
    }

    public int row() {
        return (rowAlpha << 24) | rowColor;
    }

    // ------------------------------------------------------------------ editor model

    /** One editable colour, exposed to the editor without reflection. */
    public record Slot(String nameKey, IntSupplier getter, IntConsumer setter,
                       boolean hasAlpha, IntSupplier alphaGetter, IntConsumer alphaSetter) {
    }

    /** The list the editor walks; order is what the user sees. */
    public List<Slot> slots() {
        List<Slot> slots = new ArrayList<>();
        slots.add(new Slot("theme.athens_coins.background",
                () -> backgroundColor, value -> backgroundColor = value,
                true, () -> backgroundAlpha, value -> backgroundAlpha = value));
        slots.add(new Slot("theme.athens_coins.border",
                () -> borderColor, value -> borderColor = value, false, null, null));
        slots.add(new Slot("theme.athens_coins.title_bar",
                () -> titleBarColor, value -> titleBarColor = value, false, null, null));
        slots.add(new Slot("theme.athens_coins.title_text",
                () -> titleTextColor, value -> titleTextColor = value, false, null, null));
        slots.add(new Slot("theme.athens_coins.section",
                () -> sectionColor, value -> sectionColor = value, false, null, null));
        slots.add(new Slot("theme.athens_coins.label",
                () -> labelColor, value -> labelColor = value, false, null, null));
        slots.add(new Slot("theme.athens_coins.value",
                () -> valueColor, value -> valueColor = value, false, null, null));
        slots.add(new Slot("theme.athens_coins.accent",
                () -> accentColor, value -> accentColor = value, false, null, null));
        slots.add(new Slot("theme.athens_coins.rows",
                () -> rowColor, value -> rowColor = value,
                true, () -> rowAlpha, value -> rowAlpha = value));
        return slots;
    }

    // ------------------------------------------------------------------ presets

    /** Ready-made looks, so an admin does not have to build one from scratch. */
    public static StatsTheme preset(int index) {
        StatsTheme theme = new StatsTheme();
        switch (index) {
            case 1 -> {   // midnight blue
                theme.backgroundColor = 0x0E1524;
                theme.borderColor = 0x4A7BB5;
                theme.titleBarColor = 0x1B2A44;
                theme.titleTextColor = 0xBFD8FF;
                theme.sectionColor = 0x7FB2E5;
                theme.labelColor = 0xA8B8CC;
                theme.accentColor = 0x5BE0C0;
            }
            case 2 -> {   // burgundy, matching the wallet
                theme.backgroundColor = 0x1E0E11;
                theme.borderColor = 0xC9A227;
                theme.titleBarColor = 0x4A2A2E;
                theme.titleTextColor = 0xFFD98F;
                theme.sectionColor = 0xD8A0A0;
                theme.labelColor = 0xC9AFAF;
                theme.accentColor = 0x4CD964;
            }
            case 3 -> {   // high contrast
                theme.backgroundColor = 0x000000;
                theme.backgroundAlpha = 255;
                theme.borderColor = 0xFFFFFF;
                theme.titleBarColor = 0x202020;
                theme.titleTextColor = 0xFFFFFF;
                theme.sectionColor = 0xFFFF55;
                theme.labelColor = 0xDDDDDD;
                theme.valueColor = 0xFFFFFF;
                theme.accentColor = 0x55FF55;
                theme.rowAlpha = 32;
                theme.textShadow = false;
            }
            case 4 -> {   // light parchment
                theme.backgroundColor = 0xE8DCC0;
                theme.backgroundAlpha = 250;
                theme.borderColor = 0x7A5A2A;
                theme.titleBarColor = 0xC9AE7E;
                theme.titleTextColor = 0x3A2A10;
                theme.sectionColor = 0x5A3A14;
                theme.labelColor = 0x4A3A22;
                theme.valueColor = 0x1A1208;
                theme.accentColor = 0x1E7A32;
                theme.rowColor = 0x000000;
                theme.rowAlpha = 16;
                theme.textShadow = false;
            }
            default -> {  // the shipped default
            }
        }
        return theme;
    }

    public static int presetCount() {
        return 5;
    }
}
