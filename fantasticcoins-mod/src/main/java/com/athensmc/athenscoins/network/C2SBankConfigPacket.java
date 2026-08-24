package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankData;
import com.athensmc.athenscoins.bank.BankManager;
import com.athensmc.athenscoins.bank.BankRules;
import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The bank's whole settings form, submitted in one go.
 *
 * <p>Settings used to be eight buttons sharing two entry boxes: you typed a number, worked out which
 * of the eight buttons it belonged to, pressed it, and repeated. Each press was its own packet and
 * its own half-applied state, and nothing told you what the current values were. This carries the
 * entire form, so the screen can be a list of labelled boxes pre-filled with what the bank actually
 * has, and one Save applies the lot.</p>
 *
 * <p>Submitting the form is also what marks a bank {@linkplain Bank#configured() configured}, which
 * is what unlocks the terminal's other tabs.</p>
 */
public class C2SBankConfigPacket {

    private static final double MAX_REACH_SQR = 64.0D;

    private final BlockPos pos;
    private final String name;
    private final int themeColor;
    private final long walletLimit;
    private final long commissionFee;
    private final int commissionPeriodDays;
    private final long[] rates;
    private final boolean loansEnabled;
    private final long loanMaxAmount;
    private final int loanDays;
    private final int loanInterestBasisPoints;

    public C2SBankConfigPacket(BlockPos pos, String name, int themeColor, long walletLimit,
                               long commissionFee, int commissionPeriodDays, long[] rates,
                               boolean loansEnabled, long loanMaxAmount, int loanDays,
                               int loanInterestBasisPoints) {
        this.pos = pos;
        this.name = name;
        this.themeColor = themeColor;
        this.walletLimit = walletLimit;
        this.commissionFee = commissionFee;
        this.commissionPeriodDays = commissionPeriodDays;
        this.rates = rates.clone();
        this.loansEnabled = loansEnabled;
        this.loanMaxAmount = loanMaxAmount;
        this.loanDays = loanDays;
        this.loanInterestBasisPoints = loanInterestBasisPoints;
    }

    public C2SBankConfigPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.name = buffer.readUtf(32);
        this.themeColor = buffer.readInt();
        this.walletLimit = buffer.readVarLong();
        this.commissionFee = buffer.readVarLong();
        this.commissionPeriodDays = buffer.readVarInt();
        this.rates = new long[CoinType.ORDERED.length];
        for (int i = 0; i < rates.length; i++) {
            rates[i] = buffer.readVarLong();
        }
        this.loansEnabled = buffer.readBoolean();
        this.loanMaxAmount = buffer.readVarLong();
        this.loanDays = buffer.readVarInt();
        this.loanInterestBasisPoints = buffer.readVarInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeUtf(name, 32);
        buffer.writeInt(themeColor);
        buffer.writeVarLong(walletLimit);
        buffer.writeVarLong(commissionFee);
        buffer.writeVarInt(commissionPeriodDays);
        for (long rate : rates) {
            buffer.writeVarLong(rate);
        }
        buffer.writeBoolean(loansEnabled);
        buffer.writeVarLong(loanMaxAmount);
        buffer.writeVarInt(loanDays);
        buffer.writeVarInt(loanInterestBasisPoints);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !player.hasPermissions(2)) {
                if (player != null) {
                    player.sendSystemMessage(Component
                            .translatable("message.athens_coins.bank_op_only")
                            .withStyle(ChatFormatting.RED));
                }
                return;
            }
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > MAX_REACH_SQR) {
                return;
            }
            BankData data = BankData.get(player.server);
            Bank bank = data.bankAt(pos);
            if (bank == null) {
                return;
            }
            apply(player, data, bank);
            ModNetwork.toPlayer(player, S2COpenTerminalPacket.of(player, bank, pos, true));
        });
        ctx.setPacketHandled(true);
    }

    /**
     * Applies every field, then reports whatever the server refused to take literally.
     *
     * <p>The clamps live in {@link Bank}'s setters and in {@link BankRules}, so a hostile or simply
     * mistaken client cannot install an out-of-policy bank. What matters for usability is telling the
     * operator when that happened: a rate silently pulled back inside the official band looks like
     * the form did not save.</p>
     */
    private void apply(ServerPlayer player, BankData data, Bank bank) {
        List<Component> adjusted = new ArrayList<>();
        String symbol = CurrencyConfig.get().currencySymbol;

        bank.setName(name);
        bank.setThemeColor(themeColor);

        if (!BankManager.setWalletLimit(player.server, bank, walletLimit, player.getUUID())) {
            adjusted.add(Component.translatable("gui.athens_coins.cfg_wallet_limit"));
        }
        bank.setCommissionFee(commissionFee);

        int daysBefore = bank.commissionPeriodDays();
        bank.setCommissionPeriodDays(commissionPeriodDays);
        if (bank.commissionPeriodDays() != daysBefore) {
            // The fee clock counts periods from a fixed anchor, so changing the period without
            // restarting it would bill the difference retroactively.
            BankManager.resetCommissionPolicyClock(player.server, bank, System.currentTimeMillis());
        }
        if (bank.commissionPeriodDays() != commissionPeriodDays) {
            adjusted.add(Component.translatable("gui.athens_coins.cfg_fee_days"));
        }

        int margin = CurrencyConfig.get().rateMarginPercent;
        for (CoinType type : CoinType.ORDERED) {
            long requested = rates[type.ordinal()];
            long official = CurrencyConfig.get().coinValueCents(type);
            long allowed = BankRules.clampRate(requested, official, margin);
            bank.setRate(type, requested);
            if (allowed != requested) {
                adjusted.add(Component.translatable("gui.athens_coins.cfg_rate_of",
                        type.shortName(), Money.format(allowed, symbol)));
            }
        }

        bank.setLoansEnabled(loansEnabled);
        bank.setLoanMaxAmount(loanMaxAmount);
        bank.setLoanDays(loanDays);
        if (bank.loanDays() != loanDays) {
            adjusted.add(Component.translatable("gui.athens_coins.cfg_loan_days"));
        }
        bank.setLoanInterestBasisPoints(loanInterestBasisPoints);
        if (bank.loanInterestBasisPoints() != loanInterestBasisPoints) {
            adjusted.add(Component.translatable("gui.athens_coins.cfg_loan_interest"));
        }

        boolean first = !bank.configured();
        bank.markConfigured();
        data.setDirty();

        player.sendSystemMessage(Component.translatable(first
                        ? "message.athens_coins.bank_configured_first"
                        : "message.athens_coins.bank_configured")
                .withStyle(ChatFormatting.GREEN));
        for (Component note : adjusted) {
            player.sendSystemMessage(Component.translatable("message.athens_coins.bank_cfg_adjusted", note)
                    .withStyle(ChatFormatting.YELLOW));
        }
    }
}
