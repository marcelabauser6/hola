package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankAccount;
import com.athensmc.athenscoins.bank.BankData;
import com.athensmc.athenscoins.bank.BankRules;
import com.athensmc.athenscoins.config.CurrencyConfig;
import com.athensmc.athenscoins.wallet.CoinType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** The whole banking system as the central bank sees it. */
public class S2COpenCentralPacket {

    /** One commercial bank on the central bank's list. */
    public record BankRow(UUID id, String name, long reserve, int accounts, long deposits,
                          long loansOut, boolean seated, long[] rates, int color) {
    }

    private final BlockPos pos;
    private final List<BankRow> banks;
    private final long[] officialRates;
    private final long[] floors;
    private final long[] ceilings;
    private final int marginPercent;
    private final long totalIssued;

    public S2COpenCentralPacket(BlockPos pos, List<BankRow> banks, long[] officialRates,
                                long[] floors, long[] ceilings, int marginPercent, long totalIssued) {
        this.pos = pos;
        this.banks = banks;
        this.officialRates = officialRates;
        this.floors = floors;
        this.ceilings = ceilings;
        this.marginPercent = marginPercent;
        this.totalIssued = totalIssued;
    }

    public static S2COpenCentralPacket of(ServerPlayer viewer, BlockPos pos) {
        BankData data = BankData.get(viewer.server);
        CurrencyConfig.Settings settings = CurrencyConfig.get();
        int margin = settings.rateMarginPercent;

        List<BankRow> rows = new ArrayList<>();
        for (Bank bank : data.banks()) {
            List<BankAccount> accounts = data.accountsOf(bank.id());
            long deposits = 0L;
            long loansOut = 0L;
            for (BankAccount account : accounts) {
                deposits += account.balance();
                if (account.loan() != null && !account.loan().settled()) {
                    loansOut += account.loan().owed();
                }
            }
            long[] rates = new long[CoinType.ORDERED.length];
            for (CoinType type : CoinType.ORDERED) {
                rates[type.ordinal()] = bank.rate(type);
            }
            rows.add(new BankRow(bank.id(), bank.name(), bank.reserve(), accounts.size(),
                    deposits, loansOut, bank.hasSeat(), rates, bank.themeColor()));
        }
        rows.sort((a, b) -> Long.compare(b.reserve(), a.reserve()));

        long[] official = new long[CoinType.ORDERED.length];
        long[] floors = new long[CoinType.ORDERED.length];
        long[] ceilings = new long[CoinType.ORDERED.length];
        for (CoinType type : CoinType.ORDERED) {
            long value = settings.coinValueCents(type);
            official[type.ordinal()] = value;
            floors[type.ordinal()] = BankRules.rateFloor(value, margin);
            ceilings[type.ordinal()] = BankRules.rateCeiling(value, margin);
        }
        return new S2COpenCentralPacket(pos, rows, official, floors, ceilings, margin,
                data.totalIssued());
    }

    public S2COpenCentralPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        int count = buffer.readVarInt();
        this.banks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = buffer.readUUID();
            String name = buffer.readUtf(32);
            long reserve = buffer.readVarLong();
            int accounts = buffer.readVarInt();
            long deposits = buffer.readVarLong();
            long loansOut = buffer.readVarLong();
            boolean seated = buffer.readBoolean();
            long[] rates = new long[CoinType.ORDERED.length];
            for (int r = 0; r < rates.length; r++) {
                rates[r] = buffer.readVarLong();
            }
            banks.add(new BankRow(id, name, reserve, accounts, deposits, loansOut, seated,
                    rates, buffer.readInt()));
        }
        this.officialRates = readRates(buffer);
        this.floors = readRates(buffer);
        this.ceilings = readRates(buffer);
        this.marginPercent = buffer.readVarInt();
        this.totalIssued = buffer.readVarLong();
    }

    private static long[] readRates(FriendlyByteBuf buffer) {
        long[] out = new long[CoinType.ORDERED.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = buffer.readVarLong();
        }
        return out;
    }

    private static void writeRates(FriendlyByteBuf buffer, long[] values) {
        for (long value : values) {
            buffer.writeVarLong(value);
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeVarInt(banks.size());
        for (BankRow row : banks) {
            buffer.writeUUID(row.id());
            buffer.writeUtf(row.name(), 32);
            buffer.writeVarLong(row.reserve());
            buffer.writeVarInt(row.accounts());
            buffer.writeVarLong(row.deposits());
            buffer.writeVarLong(row.loansOut());
            buffer.writeBoolean(row.seated());
            writeRates(buffer, row.rates());
            buffer.writeInt(row.color());
        }
        writeRates(buffer, officialRates);
        writeRates(buffer, floors);
        writeRates(buffer, ceilings);
        buffer.writeVarInt(marginPercent);
        buffer.writeVarLong(totalIssued);
    }

    public BlockPos pos() {
        return pos;
    }

    public List<BankRow> banks() {
        return banks;
    }

    public long official(CoinType type) {
        return officialRates[type.ordinal()];
    }

    public long floor(CoinType type) {
        return floors[type.ordinal()];
    }

    public long ceiling(CoinType type) {
        return ceilings[type.ordinal()];
    }

    public int marginPercent() {
        return marginPercent;
    }

    public long totalIssued() {
        return totalIssued;
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientWalletSync.openCentral(this);
            }
        });
        ctx.setPacketHandled(true);
    }
}
