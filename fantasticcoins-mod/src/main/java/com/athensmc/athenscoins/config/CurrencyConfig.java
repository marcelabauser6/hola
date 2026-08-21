package com.athensmc.athenscoins.config;

import com.athensmc.athenscoins.AthensCoinsMod;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fantastic Currency settings, stored as JSON at {@code config/fantasticcurrency.json}.
 *
 * <p>A hand-rolled config rather than {@code ForgeConfigSpec} for one reason: it can be
 * re-read from disk on demand, which is what makes {@code /fscurrency reload} actually
 * reload something.</p>
 */
public final class CurrencyConfig {

    public static final String FILE_NAME = "fantasticcurrency.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile Settings settings = new Settings();
    private static Path path;

    private CurrencyConfig() {
    }

    public static Settings get() {
        return settings;
    }

    public static Path path() {
        if (path == null) {
            path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        }
        return path;
    }

    /** Outcome of a load attempt, so the reload command can report something useful. */
    public record LoadResult(boolean ok, String detail) {
    }

    /** Loads the file, writing a default one first if it does not exist yet. */
    public static LoadResult load() {
        Path file = path();
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Settings defaults = new Settings();
                write(file, defaults);
                defaults.bake();
                settings = defaults;
                return new LoadResult(true, "created default " + FILE_NAME);
            }
            Settings loaded;
            try (Reader reader = Files.newBufferedReader(file)) {
                loaded = GSON.fromJson(reader, Settings.class);
            }
            if (loaded == null) {
                loaded = new Settings();
            }
            String warnings = loaded.validate();
            loaded.bake();
            settings = loaded;
            return new LoadResult(true, warnings.isEmpty() ? "loaded " + FILE_NAME : warnings);
        } catch (IOException | JsonSyntaxException exception) {
            AthensCoinsMod.LOGGER.error("Could not read {} - keeping the previous settings",
                    FILE_NAME, exception);
            return new LoadResult(false, exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage());
        }
    }

    private static void write(Path file, Settings value) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(value, writer);
        }
    }

    /** Writes the currently active settings back out, e.g. to add newly introduced keys. */
    public static void save() {
        try {
            write(path(), settings);
        } catch (IOException exception) {
            AthensCoinsMod.LOGGER.error("Could not write {}", FILE_NAME, exception);
        }
    }

    // ==================================================================== settings

    /**
     * The editable settings. Field names are exactly the JSON keys.
     */
    public static class Settings {

        // ---- currency identity
        public String currencyName = "Fantastic Cash";
        public String currencySymbol = "$";
        public String cashColor = "#4CD964";

        // ---- exchange rates: how much Fantastic Cash one physical coin is worth
        public double bronzeCoinValue = 0.15D;
        public double silverCoinValue = 1.35D;
        public double goldCoinValue = 12.15D;

        // ---- coin colours in GUIs and chat
        public String bronzeColor = "#C87137";
        public String silverColor = "#DCDCDC";
        public String goldColor = "#FFC93C";

        // ---- banking
        public double startingBalance = 0.0D;
        public int exchangeToCashFeePercent = 0;
        public int exchangeToCoinsFeePercent = 0;
        public int maxCoinsPerExchange = 2304;
        public int atmDetectionRange = 6;

        // ---- transfers
        public boolean transfersEnabled = true;
        public double minTransfer = 0.01D;
        public double maxTransfer = 1_000_000.0D;
        public int transferRequestTimeoutSeconds = 60;

        // ---- banking
        /** How far a bank may set its rates from the official one, in percent. */
        public int rateMarginPercent = 15;
        /** Most commission periods collected at once after the server was offline. */
        public int commissionMaxCatchUp = 7;

        // ---- wallet GUI
        public int walletTheme = 1;

        // ---- baked (not serialised, derived from the above)
        private transient long[] coinValueCents;
        private transient int[] coinColors;
        private transient int cashColorRgb;
        private transient long startingCents;
        private transient long minTransferCents;
        private transient long maxTransferCents;

        /** Clamps nonsense values and returns a human-readable list of anything corrected. */
        String validate() {
            StringBuilder warnings = new StringBuilder();

            if (currencyName == null || currencyName.isBlank()) {
                currencyName = "Fantastic Cash";
                append(warnings, "currencyName was blank");
            }
            if (currencySymbol == null || currencySymbol.isBlank()) {
                currencySymbol = "$";
                append(warnings, "currencySymbol was blank");
            }
            if (currencySymbol.length() > 3) {
                currencySymbol = currencySymbol.substring(0, 3);
                append(warnings, "currencySymbol trimmed to 3 characters");
            }

            bronzeCoinValue = clampValue(bronzeCoinValue, warnings, "bronzeCoinValue");
            silverCoinValue = clampValue(silverCoinValue, warnings, "silverCoinValue");
            goldCoinValue = clampValue(goldCoinValue, warnings, "goldCoinValue");

            exchangeToCashFeePercent = clampInt(exchangeToCashFeePercent, 0, 100, warnings,
                    "exchangeToCashFeePercent");
            exchangeToCoinsFeePercent = clampInt(exchangeToCoinsFeePercent, 0, 100, warnings,
                    "exchangeToCoinsFeePercent");
            maxCoinsPerExchange = clampInt(maxCoinsPerExchange, 1, 46_080, warnings,
                    "maxCoinsPerExchange");
            atmDetectionRange = clampInt(atmDetectionRange, 1, 32, warnings, "atmDetectionRange");
            transferRequestTimeoutSeconds = clampInt(transferRequestTimeoutSeconds, 5, 3600,
                    warnings, "transferRequestTimeoutSeconds");
            walletTheme = clampInt(walletTheme, 1, 3, warnings, "walletTheme");
            rateMarginPercent = clampInt(rateMarginPercent, 0, 100, warnings, "rateMarginPercent");
            commissionMaxCatchUp = clampInt(commissionMaxCatchUp, 1, 60, warnings,
                    "commissionMaxCatchUp");

            if (startingBalance < 0.0D) {
                startingBalance = 0.0D;
                append(warnings, "startingBalance cannot be negative");
            }
            if (minTransfer < 0.01D) {
                minTransfer = 0.01D;
                append(warnings, "minTransfer raised to 0.01");
            }
            if (maxTransfer < minTransfer) {
                maxTransfer = minTransfer;
                append(warnings, "maxTransfer was below minTransfer");
            }
            return warnings.toString();
        }

        private double clampValue(double value, StringBuilder warnings, String name) {
            if (value < 0.01D) {
                append(warnings, name + " raised to 0.01");
                return 0.01D;
            }
            if (value > 1_000_000.0D) {
                append(warnings, name + " capped at 1000000");
                return 1_000_000.0D;
            }
            return value;
        }

        private int clampInt(int value, int min, int max, StringBuilder warnings, String name) {
            if (value < min || value > max) {
                append(warnings, name + " clamped to [" + min + ", " + max + "]");
                return Math.max(min, Math.min(max, value));
            }
            return value;
        }

        private static void append(StringBuilder builder, String message) {
            if (!builder.isEmpty()) {
                builder.append("; ");
            }
            builder.append(message);
        }

        /** Pre-computes the cents and RGB values the rest of the mod reads every tick. */
        void bake() {
            coinValueCents = new long[] {
                    Money.fromConfig(bronzeCoinValue),
                    Money.fromConfig(silverCoinValue),
                    Money.fromConfig(goldCoinValue),
            };
            coinColors = new int[] {
                    parseColor(bronzeColor, 0xC87137),
                    parseColor(silverColor, 0xDCDCDC),
                    parseColor(goldColor, 0xFFC93C),
            };
            cashColorRgb = parseColor(cashColor, 0x4CD964);
            startingCents = Money.fromConfig(startingBalance);
            minTransferCents = Math.max(1L, Money.fromConfig(minTransfer));
            maxTransferCents = Math.max(minTransferCents, Money.fromConfig(maxTransfer));
        }

        private static int parseColor(String raw, int fallback) {
            if (raw == null) {
                return fallback;
            }
            String hex = raw.trim();
            if (hex.startsWith("#")) {
                hex = hex.substring(1);
            } else if (hex.startsWith("0x") || hex.startsWith("0X")) {
                hex = hex.substring(2);
            }
            try {
                return (int) (Long.parseLong(hex, 16) & 0xFFFFFF);
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }

        // ---- accessors used by the rest of the mod

        public long coinValueCents(CoinType type) {
            return coinValueCents[type.ordinal()];
        }

        public int coinColor(CoinType type) {
            return coinColors[type.ordinal()];
        }

        public int cashColorRgb() {
            return cashColorRgb;
        }

        public long startingCents() {
            return startingCents;
        }

        public long minTransferCents() {
            return minTransferCents;
        }

        public long maxTransferCents() {
            return maxTransferCents;
        }
    }

    static {
        // Make sure the defaults are usable even before load() runs.
        settings.bake();
    }
}
