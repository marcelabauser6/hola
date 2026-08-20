package com.athensmc.athenscoins.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Server-side configuration for the wallet / bank system.
 *
 * <p>Generated at {@code <instance>/config/athens_coins-common.toml}.</p>
 */
public final class CoinsConfig {
    public static final ForgeConfigSpec SPEC;

    /** Percentage of a deposit kept by the bank (0 = deposits are 1:1). */
    public static final ForgeConfigSpec.IntValue DEPOSIT_FEE_PERCENT;
    /** Percentage of a withdrawal kept by the bank (0 = withdrawals are 1:1). */
    public static final ForgeConfigSpec.IntValue WITHDRAW_FEE_PERCENT;
    /** How far an ATM may be for /fscoins deposit and /fscoins withdraw to work. */
    public static final ForgeConfigSpec.IntValue ATM_RANGE;
    /** When true, deposits/withdrawals via command work anywhere, no ATM needed. */
    public static final ForgeConfigSpec.BooleanValue BANKING_WITHOUT_ATM;
    /** Allows players to transfer digital funds with /fscoins pay. */
    public static final ForgeConfigSpec.BooleanValue ALLOW_PAY;
    /** Hard cap on coins handed out by a single withdrawal. */
    public static final ForgeConfigSpec.IntValue MAX_WITHDRAW_PER_TRANSACTION;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("FantasticCoins - wallet and bank settings").push("bank");

        DEPOSIT_FEE_PERCENT = builder
                .comment("Fee charged when depositing cash into the digital wallet, in percent.",
                        "0 means a deposit of 100 bronze credits exactly 100 digital bronze.")
                .defineInRange("depositFeePercent", 0, 0, 100);

        WITHDRAW_FEE_PERCENT = builder
                .comment("Fee charged when withdrawing digital funds back into cash, in percent.",
                        "0 means a withdrawal of 100 digital bronze hands out exactly 100 coins.")
                .defineInRange("withdrawFeePercent", 0, 0, 100);

        ATM_RANGE = builder
                .comment("Maximum distance (in blocks) to an ATM for /fscoins deposit and",
                        "/fscoins withdraw to be accepted.")
                .defineInRange("atmRange", 6, 1, 32);

        BANKING_WITHOUT_ATM = builder
                .comment("Set to true to let players deposit and withdraw from anywhere,",
                        "without having to stand next to an ATM block.")
                .define("bankingWithoutAtm", false);

        ALLOW_PAY = builder
                .comment("Allows players to send digital funds to each other with /fscoins pay.")
                .define("allowPay", true);

        MAX_WITHDRAW_PER_TRANSACTION = builder
                .comment("Maximum number of coins a single withdrawal may hand out.",
                        "Keeps the 'M' (maximum) button from spawning tens of thousands of items",
                        "at once. 2304 is exactly a full inventory of one denomination.")
                .defineInRange("maxWithdrawPerTransaction", 2304, 1, 46080);

        builder.pop();

        SPEC = builder.build();
    }

    private CoinsConfig() {
    }
}
