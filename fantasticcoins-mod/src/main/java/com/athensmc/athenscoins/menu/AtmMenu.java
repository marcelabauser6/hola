package com.athensmc.athenscoins.menu;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankAccount;
import com.athensmc.athenscoins.bank.BankManager;
import com.athensmc.athenscoins.block.ModBlocks;
import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.network.ModNetwork;
import com.athensmc.athenscoins.network.S2CAtmSyncPacket;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The ATM: coins in one direction, cash the other, plus the account behind the wallet card.
 *
 * <p>Prices are the issuing bank's, not the server's, so competing banks quote differently.</p>
 *
 * <p>Two channels carry actions, for one reason. The exchange <em>presets</em> ride vanilla's
 * container-button channel, which transmits a bare {@code int} id and needs no custom packet.
 * Anything with an amount the player typed cannot fit through that channel at all, so it travels as
 * {@link com.athensmc.athenscoins.network.C2SAtmActionPacket}. The menu keeps the presets; the packet
 * owns the typed amounts.</p>
 */
public class AtmMenu extends AbstractContainerMenu implements WalletStateHolder {

    /** Coins out of the inventory, cash into the wallet. */
    public static final int MODE_TO_CASH = 0;
    /** Cash out of the wallet, coins into the inventory. */
    public static final int MODE_TO_COINS = 1;

    /** Amount presets; {@code -1} means "as many as possible". */
    public static final int[] AMOUNTS = { 1, 10, 64, -1 };

    private static final int BUTTONS_PER_MODE = CoinType.ORDERED.length * 4;

    private final ContainerLevelAccess access;
    @Nullable
    private final UUID bankId;
    private final BlockPos atmPos;

    private AtmState state;

    /** Server-side. */
    public AtmMenu(int containerId, Inventory inventory, ContainerLevelAccess access,
                   Bank bank, BlockPos atmPos) {
        super(ModMenus.ATM.get(), containerId);
        this.access = access;
        this.bankId = bank.id();
        this.atmPos = atmPos;
        this.state = inventory.player instanceof ServerPlayer serverPlayer
                ? AtmState.capture(serverPlayer, bank, atmPos)
                : AtmState.empty();
    }

    /** Client-side. */
    public AtmMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        super(ModMenus.ATM.get(), containerId);
        this.access = ContainerLevelAccess.NULL;
        this.bankId = null;
        this.state = AtmState.read(buffer);
        this.atmPos = this.state.atmPos();
    }

    public static void writeState(FriendlyByteBuf buffer, ServerPlayer player, Bank bank,
                                  BlockPos atmPos) {
        AtmState.capture(player, bank, atmPos).write(buffer);
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
        BankManager.Access access = BankManager.accessFor(serverPlayer, bank);
        if (access != BankManager.Access.OK) {
            BankManager.explainRefusal(serverPlayer, bank, access);
            return false;
        }
        BankAccount account = BankManager.accountOf(serverPlayer);
        if (id < 0 || id >= BUTTONS_PER_MODE * 2) {
            return false;
        }
        String symbol = CurrencyConfig.get().currencySymbol;
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

    // ------------------------------------------------------------------ state

    public void refreshFrom(ServerPlayer player, Bank bank) {
        this.state = AtmState.capture(player, bank, atmPos);
    }

    private void refreshAndSync(ServerPlayer player, Bank bank) {
        refreshFrom(player, bank);
        ModNetwork.toPlayer(player, new S2CAtmSyncPacket(state));
    }

    /** Applies the lightweight wallet sync, which knows nothing about the bank-side figures. */
    @Override
    public void applyState(long newCash, int[] newCounts, boolean atmNearby) {
        this.state = state.withCash(newCash, newCounts);
    }

    /** Applies a full push from {@link S2CAtmSyncPacket}. */
    public void applyState(AtmState fresh) {
        this.state = fresh;
    }

    // ------------------------------------------------------------------ accessors

    public AtmState state() {
        return state;
    }

    public long cashCents() {
        return state.cash();
    }

    public long accountCents() {
        return state.accountBalance();
    }

    public long walletLimit() {
        return state.walletLimit();
    }

    public int coinCount(CoinType type) {
        return state.coinCount(type);
    }

    public long rate(CoinType type) {
        return state.rate(type);
    }

    public String bankName() {
        return state.bankName();
    }

    public int themeColor() {
        return state.themeColor();
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
