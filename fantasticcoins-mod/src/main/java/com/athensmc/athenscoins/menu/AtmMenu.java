package com.athensmc.athenscoins.menu;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankAccount;
import com.athensmc.athenscoins.bank.BankManager;
import com.athensmc.athenscoins.block.ModBlocks;
import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.network.ModNetwork;
import com.athensmc.athenscoins.network.S2CWalletSyncPacket;
import com.athensmc.athenscoins.wallet.CoinType;
import com.athensmc.athenscoins.wallet.Money;
import com.athensmc.athenscoins.wallet.WalletManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The ATM: coins in one direction, cash the other, plus moving cash between the wallet card and
 * the account behind it.
 *
 * <p>Prices are the issuing bank's, not the server's, so competing banks quote differently. Actions
 * ride vanilla's container-button channel, so no custom serverbound packet is needed.</p>
 */
public class AtmMenu extends AbstractContainerMenu implements WalletStateHolder {

    /** Coins out of the inventory, cash into the wallet. */
    public static final int MODE_TO_CASH = 0;
    /** Cash out of the wallet, coins into the inventory. */
    public static final int MODE_TO_COINS = 1;

    /** Amount presets; {@code -1} means "as many as possible". */
    public static final int[] AMOUNTS = { 1, 10, 64, -1 };

    private static final int BUTTONS_PER_MODE = CoinType.ORDERED.length * 4;
    /** Ids above the exchange grid, for moving money between the card and the account. */
    public static final int BUTTON_TO_WALLET = BUTTONS_PER_MODE * 2;
    public static final int BUTTON_TO_ACCOUNT = BUTTON_TO_WALLET + 1;

    private final ContainerLevelAccess access;
    @Nullable
    private final UUID bankId;

    private long cashCents;
    private long accountCents;
    private long walletLimit;
    private final int[] coinCounts = new int[CoinType.ORDERED.length];
    private final long[] rates = new long[CoinType.ORDERED.length];
    private String bankName = "";
    private int themeColor = 0x2E4756;

    /** Server-side. */
    public AtmMenu(int containerId, Inventory inventory, ContainerLevelAccess access, Bank bank) {
        super(ModMenus.ATM.get(), containerId);
        this.access = access;
        this.bankId = bank.id();
        if (inventory.player instanceof ServerPlayer serverPlayer) {
            refreshFrom(serverPlayer, bank);
        }
    }

    /** Client-side. */
    public AtmMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        super(ModMenus.ATM.get(), containerId);
        this.access = ContainerLevelAccess.NULL;
        this.bankId = null;
        this.cashCents = buffer.readVarLong();
        this.accountCents = buffer.readVarLong();
        this.walletLimit = buffer.readVarLong();
        for (int i = 0; i < coinCounts.length; i++) {
            coinCounts[i] = buffer.readVarInt();
            rates[i] = buffer.readVarLong();
        }
        this.bankName = buffer.readUtf(32);
        this.themeColor = buffer.readInt();
    }

    public static void writeState(FriendlyByteBuf buffer, ServerPlayer player, Bank bank) {
        BankAccount account = BankManager.accountOf(player);
        buffer.writeVarLong(WalletManager.accountOf(player).balance());
        buffer.writeVarLong(account == null ? 0L : account.balance());
        buffer.writeVarLong(bank.walletLimit());
        for (CoinType type : CoinType.ORDERED) {
            buffer.writeVarInt(WalletManager.countCoins(player, type));
            buffer.writeVarLong(bank.rate(type));
        }
        buffer.writeUtf(bank.name(), 32);
        buffer.writeInt(bank.themeColor());
    }

    // ------------------------------------------------------------------ buttons

    public static int buttonId(int mode, CoinType type, int amountIndex) {
        return mode * BUTTONS_PER_MODE + type.ordinal() * AMOUNTS.length + amountIndex;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer) || !stillValid(player)) {
            return false;
        }
        Bank bank = bankId == null ? null : BankManager.data(serverPlayer.server).bank(bankId);
        if (bank == null) {
            return false;
        }
        BankAccount account = BankManager.accountOf(serverPlayer);
        if (account == null) {
            serverPlayer.displayClientMessage(Component
                    .translatable("message.athens_coins.atm_needs_account")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        String symbol = CurrencyConfig.get().currencySymbol;

        // Card <-> account, everything the wallet ceiling allows.
        if (id == BUTTON_TO_WALLET) {
            long moved = BankManager.toWallet(serverPlayer, account.balance());
            report(serverPlayer, moved > 0L, "message.athens_coins.atm_to_wallet",
                    "message.athens_coins.atm_wallet_full", Money.format(moved, symbol));
            refreshAndSync(serverPlayer, bank);
            return true;
        }
        if (id == BUTTON_TO_ACCOUNT) {
            long moved = BankManager.toAccount(serverPlayer, cashCents);
            report(serverPlayer, moved > 0L, "message.athens_coins.atm_to_account",
                    "message.athens_coins.atm_nothing_to_move", Money.format(moved, symbol));
            refreshAndSync(serverPlayer, bank);
            return true;
        }

        if (id < 0 || id >= BUTTONS_PER_MODE * 2) {
            return false;
        }
        int mode = id / BUTTONS_PER_MODE;
        int rest = id % BUTTONS_PER_MODE;
        CoinType type = CoinType.ORDERED[rest / AMOUNTS.length];
        int preset = AMOUNTS[rest % AMOUNTS.length];
        long rate = bank.rate(type);

        WalletManager.Exchange result = mode == MODE_TO_CASH
                ? WalletManager.exchangeToCash(serverPlayer, type, preset, rate)
                : WalletManager.exchangeToCoins(serverPlayer, type, preset, rate);

        if (result.isEmpty()) {
            serverPlayer.displayClientMessage(Component.translatable(
                            mode == MODE_TO_CASH
                                    ? "message.athens_coins.no_coins"
                                    : "message.athens_coins.no_cash",
                            type.shortName()).withStyle(ChatFormatting.RED), true);
        } else {
            serverPlayer.displayClientMessage(Component.translatable(
                            mode == MODE_TO_CASH
                                    ? "message.athens_coins.exchanged_to_cash"
                                    : "message.athens_coins.exchanged_to_coins",
                            result.coins(), type.shortName(),
                            Money.format(result.cents(), symbol))
                    .withStyle(ChatFormatting.GREEN), true);
            serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                    SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS,
                    0.4F, mode == MODE_TO_CASH ? 1.4F : 1.0F);
        }
        refreshAndSync(serverPlayer, bank);
        return true;
    }

    private void report(ServerPlayer player, boolean ok, String okKey, String failKey, String amount) {
        player.displayClientMessage(ok
                ? Component.translatable(okKey, amount).withStyle(ChatFormatting.GREEN)
                : Component.translatable(failKey).withStyle(ChatFormatting.RED), true);
    }

    // ------------------------------------------------------------------ state

    public void refreshFrom(ServerPlayer player, Bank bank) {
        BankAccount account = BankManager.accountOf(player);
        this.cashCents = WalletManager.accountOf(player).balance();
        this.accountCents = account == null ? 0L : account.balance();
        this.walletLimit = bank.walletLimit();
        int[] counts = WalletManager.countAllCoins(player);
        System.arraycopy(counts, 0, coinCounts, 0, counts.length);
        for (CoinType type : CoinType.ORDERED) {
            rates[type.ordinal()] = bank.rate(type);
        }
        this.bankName = bank.name();
        this.themeColor = bank.themeColor();
    }

    private void refreshAndSync(ServerPlayer player, Bank bank) {
        refreshFrom(player, bank);
        ModNetwork.toPlayer(player, new S2CWalletSyncPacket(cashCents, coinCounts, true));
    }

    @Override
    public void applyState(long newCash, int[] newCounts, boolean atmNearby) {
        this.cashCents = newCash;
        System.arraycopy(newCounts, 0, coinCounts, 0, coinCounts.length);
    }

    // ------------------------------------------------------------------ accessors

    public long cashCents() {
        return cashCents;
    }

    public long accountCents() {
        return accountCents;
    }

    public long walletLimit() {
        return walletLimit;
    }

    public int coinCount(CoinType type) {
        return coinCounts[type.ordinal()];
    }

    public long rate(CoinType type) {
        return rates[type.ordinal()];
    }

    public String bankName() {
        return bankName;
    }

    public int themeColor() {
        return themeColor;
    }

    // ------------------------------------------------------------------ menu contract

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.ATM.get());
    }
}
