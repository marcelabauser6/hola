package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankAccount;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Full detail of one account, for the banker's account view. */
public class S2COpenAccountPacket {

    /** How much history travels with the account. */
    public static final int LEDGER_ROWS = 60;

    private final BlockPos pos;
    private final BankAccount account;
    private final String bankName;
    private final long commissionFee;
    private final int commissionDays;
    private final long walletLimit;
    private final long loanMax;
    private final int loanDays;
    private final int loanInterest;
    private final long reserve;

    public S2COpenAccountPacket(BlockPos pos, BankAccount account, Bank bank) {
        this.pos = pos;
        this.account = account;
        this.bankName = bank.name();
        this.commissionFee = bank.commissionFee();
        this.commissionDays = bank.commissionPeriodDays();
        this.walletLimit = bank.walletLimit();
        this.loanMax = bank.loanMaxAmount();
        this.loanDays = bank.loanDays();
        this.loanInterest = bank.loanInterestBasisPoints();
        this.reserve = bank.reserve();
    }

    public S2COpenAccountPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.account = BankAccount.read(buffer);
        this.bankName = buffer.readUtf(32);
        this.commissionFee = buffer.readVarLong();
        this.commissionDays = buffer.readVarInt();
        this.walletLimit = buffer.readVarLong();
        this.loanMax = buffer.readVarLong();
        this.loanDays = buffer.readVarInt();
        this.loanInterest = buffer.readVarInt();
        this.reserve = buffer.readVarLong();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        account.write(buffer, LEDGER_ROWS);
        buffer.writeUtf(bankName, 32);
        buffer.writeVarLong(commissionFee);
        buffer.writeVarInt(commissionDays);
        buffer.writeVarLong(walletLimit);
        buffer.writeVarLong(loanMax);
        buffer.writeVarInt(loanDays);
        buffer.writeVarInt(loanInterest);
        buffer.writeVarLong(reserve);
    }

    public BlockPos pos() {
        return pos;
    }

    public BankAccount account() {
        return account;
    }

    public String bankName() {
        return bankName;
    }

    public long commissionFee() {
        return commissionFee;
    }

    public int commissionDays() {
        return commissionDays;
    }

    public long walletLimit() {
        return walletLimit;
    }

    public long loanMax() {
        return loanMax;
    }

    public int loanDays() {
        return loanDays;
    }

    public int loanInterest() {
        return loanInterest;
    }

    public long reserve() {
        return reserve;
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientWalletSync.openAccount(this);
            }
        });
        ctx.setPacketHandled(true);
    }
}
