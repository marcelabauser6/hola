package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankAccount;
import com.athensmc.athenscoins.bank.BankManager;
import com.athensmc.athenscoins.bank.BankRules;
import com.athensmc.athenscoins.bank.Loan;
import com.athensmc.athenscoins.bank.LoanNotices;
import com.athensmc.athenscoins.block.AtmBlockEntity;
import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.menu.AtmMenu;
import com.athensmc.athenscoins.menu.AtmState;
import com.athensmc.athenscoins.transfer.TransferRequests;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import com.athensmc.athenscoins.wallet.WalletManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Everything a customer does at an ATM with an amount they typed themselves.
 *
 * <p>The exchange presets ride vanilla's container-button channel, which carries nothing but an
 * {@code int} button id. That was fine while every ATM action was "one of twelve fixed coin counts"
 * or "move the whole balance", and it is why the machine had no way to accept a figure: there was no
 * field in the message to put one in. Withdrawing a chosen amount, paying a chosen amount off a
 * loan, or sending a chosen amount to another player all need a number on the wire, so they travel
 * here instead.</p>
 *
 * <p>Every amount is revalidated on arrival. The screen filters keystrokes and parses with
 * {@link Money#parse} before sending, but a hostile client can put any long in this packet, so the
 * server repeats the range check and then lets the bank layer clamp against the real balance,
 * ceiling, reserve and debt.</p>
 */
public class C2SAtmActionPacket {

    /** Interacting through a block: the same 8-block leash the bank terminal uses. */
    private static final double MAX_REACH_SQR = 64.0D;

    public enum Action {
        /** Account to wallet card, up to the bank's ceiling. */
        WITHDRAW,
        /** Wallet card back to the account. */
        DEPOSIT,
        /** Ask another player to accept a payment. */
        TRANSFER,
        /** Borrow from the bank's reserve, within its policy. */
        LOAN_REQUEST,
        /** Pay down an outstanding loan. */
        LOAN_REPAY,
        /** Sell a chosen number of coins for cash. */
        EXCHANGE_TO_CASH,
        /** Buy a chosen number of coins with cash. */
        EXCHANGE_TO_COINS;

        private static final Action[] VALUES = values();

        static Action byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : WITHDRAW;
        }

        boolean isExchange() {
            return this == EXCHANGE_TO_CASH || this == EXCHANGE_TO_COINS;
        }
    }

    private static final UUID NO_TARGET = new UUID(0L, 0L);

    private final BlockPos pos;
    private final Action action;
    /** Cents for money actions, a coin count for the two exchange actions. */
    private final long value;
    private final UUID target;
    private final int coin;

    public C2SAtmActionPacket(BlockPos pos, Action action, long value) {
        this(pos, action, value, null, -1);
    }

    public C2SAtmActionPacket(BlockPos pos, Action action, long value, UUID target, int coin) {
        this.pos = pos;
        this.action = action;
        this.value = value;
        this.target = target == null ? NO_TARGET : target;
        this.coin = coin;
    }

    public C2SAtmActionPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.action = Action.byOrdinal(buffer.readByte());
        this.value = buffer.readVarLong();
        this.target = buffer.readUUID();
        this.coin = buffer.readByte();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeByte(action.ordinal());
        buffer.writeVarLong(value);
        buffer.writeUUID(target);
        buffer.writeByte(coin);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            // Must be standing at a real ATM that a real bank issued.
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > MAX_REACH_SQR) {
                return;
            }
            if (!(player.level().getBlockEntity(pos) instanceof AtmBlockEntity machine)) {
                return;
            }
            Bank bank = machine.bank(player.server);
            if (bank == null) {
                deny(player, machine.unbranded()
                        ? "message.athens_coins.atm_unbranded" : "message.athens_coins.atm_bank_gone");
                return;
            }
            // The machine only serves its own bank's customers, same as the preset buttons.
            BankAccount account = BankManager.accountOf(player);
            if (account == null || !account.bankId().equals(bank.id())) {
                deny(player, "message.athens_coins.atm_needs_account");
                return;
            }
            // A client can put any long here; refuse nonsense before the bank layer sees it.
            if (value <= 0L || value > Money.MAX_CENTS) {
                deny(player, "message.athens_coins.amount_positive");
                return;
            }
            if (action.isExchange() && (coin < 0 || coin >= CoinType.ORDERED.length
                    || value > Integer.MAX_VALUE)) {
                return;
            }

            apply(player, bank, account);
            resync(player, bank);
        });
        ctx.setPacketHandled(true);
    }

    private void apply(ServerPlayer player, Bank bank, BankAccount account) {
        String symbol = CurrencyConfig.get().currencySymbol;
        switch (action) {
            case WITHDRAW -> {
                long moved = BankManager.toWallet(player, value);
                if (moved <= 0L) {
                    // Distinguish "card is at its ceiling" from "the account cannot cover it".
                    deny(player, BankRules.walletFull(account.walletBalance(), bank.walletLimit())
                            ? "message.athens_coins.atm_wallet_full"
                            : "message.athens_coins.atm_not_enough_account");
                    return;
                }
                ok(player, "message.athens_coins.atm_to_wallet", Money.format(moved, symbol));
                chime(player, 1.2F);
            }
            case DEPOSIT -> {
                long moved = BankManager.toAccount(player, value);
                if (moved <= 0L) {
                    deny(player, "message.athens_coins.atm_not_enough_card");
                    return;
                }
                ok(player, "message.athens_coins.atm_to_account", Money.format(moved, symbol));
                chime(player, 0.9F);
            }
            case TRANSFER -> {
                ServerPlayer recipient = NO_TARGET.equals(target)
                        ? null : player.server.getPlayerList().getPlayer(target);
                TransferRequests.Outcome outcome = TransferRequests.request(player, recipient, value);
                player.displayClientMessage(outcome.message().copy().withStyle(
                        outcome.ok() ? ChatFormatting.GREEN : ChatFormatting.RED), true);
                if (outcome.ok()) {
                    chime(player, 1.4F);
                }
            }
            case LOAN_REQUEST -> {
                BankManager.LoanResult result = BankManager.grantLoan(player.server, account, value);
                if (!result.ok()) {
                    deny(player, result.messageKey());
                    return;
                }
                // Say the amount: the bank clamps the request to its reserve and its policy, so what
                // was asked for and what was lent are often different numbers.
                ok(player, "message.athens_coins.atm_loan_taken", Money.format(result.amount(), symbol));
                if (result.partial()) {
                    player.sendSystemMessage(Component.translatable(
                                    "message.athens_coins.bank_loan_partial",
                                    Money.format(result.amount(), symbol))
                            .withStyle(ChatFormatting.YELLOW));
                }
                Loan loan = account.loan();
                if (loan != null) {
                    LoanNotices.granted(player.server, account, result.amount(), loan.dueAt());
                }
                chime(player, 1.0F);
            }
            case LOAN_REPAY -> {
                long paid = BankManager.repayLoan(player.server, account, value);
                if (paid <= 0L) {
                    deny(player, account.loan() == null || account.loan().settled()
                            ? "message.athens_coins.atm_no_loan"
                            : "message.athens_coins.atm_not_enough_repay");
                    return;
                }
                boolean cleared = account.loan() == null || account.loan().settled();
                ok(player, cleared ? "message.athens_coins.atm_loan_cleared"
                        : "message.athens_coins.atm_loan_repaid", Money.format(paid, symbol));
                chime(player, cleared ? 1.5F : 1.1F);
            }
            case EXCHANGE_TO_CASH, EXCHANGE_TO_COINS -> exchange(player, bank, symbol);
        }
    }

    private void exchange(ServerPlayer player, Bank bank, String symbol) {
        CoinType type = CoinType.ORDERED[coin];
        long rate = bank.rate(type);
        int count = (int) value;
        boolean toCash = action == Action.EXCHANGE_TO_CASH;
        WalletManager.Exchange result = toCash
                ? WalletManager.exchangeToCash(player, type, count, rate)
                : WalletManager.exchangeToCoins(player, type, count, rate);
        if (result.isEmpty()) {
            player.displayClientMessage(Component.translatable(toCash
                            ? "message.athens_coins.no_coins" : "message.athens_coins.no_cash",
                    type.shortName()).withStyle(ChatFormatting.RED), true);
            return;
        }
        player.displayClientMessage(Component.translatable(toCash
                                ? "message.athens_coins.exchanged_to_cash"
                                : "message.athens_coins.exchanged_to_coins",
                        result.coins(), type.shortName(), Money.format(result.cents(), symbol))
                .withStyle(ChatFormatting.GREEN), true);
        chime(player, toCash ? 1.4F : 1.0F);
    }

    /** Pushes the post-action state so the screen redraws from the server, not from a guess. */
    private void resync(ServerPlayer player, Bank bank) {
        if (player.containerMenu instanceof AtmMenu menu) {
            menu.refreshFrom(player, bank);
        }
        ModNetwork.toPlayer(player, new S2CAtmSyncPacket(AtmState.capture(player, bank, pos)));
    }

    private void ok(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args)
                .withStyle(ChatFormatting.GREEN), true);
    }

    private void deny(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    private void chime(ServerPlayer player, float pitch) {
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.4F, pitch);
    }
}
