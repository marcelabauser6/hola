package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankAccount;
import com.athensmc.athenscoins.bank.BankData;
import com.athensmc.athenscoins.bank.LoanRequest;
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

/** Opens the bank terminal, carrying the bank, its accounts and the online player list. */
public class S2COpenTerminalPacket {

    /**
     * One row of the accounts tab.
     *
     * @param score the 0-100 standing from the bank's risk desk, so a banker can see who is a problem
     *              without opening every account one by one
     */
    public record AccountRow(int number, String owner, long balance, boolean hasLoan, int score) {
    }

    /** One row of the player pickers. */
    public record PlayerRow(UUID id, String name, boolean banker, boolean hasAccount) {
    }

    private final Bank bank;
    private final BlockPos pos;
    private final boolean operator;
    private final List<AccountRow> accounts;
    private final List<PlayerRow> players;
    /** Loan applications waiting on a banker; the requests tab is built from these. */
    private final List<LoanRequest> requests;

    public S2COpenTerminalPacket(Bank bank, BlockPos pos, boolean operator,
                                List<AccountRow> accounts, List<PlayerRow> players,
                                List<LoanRequest> requests) {
        this.bank = bank;
        this.pos = pos;
        this.operator = operator;
        this.accounts = accounts;
        this.players = players;
        this.requests = requests;
    }

    public static S2COpenTerminalPacket of(ServerPlayer viewer, Bank bank, BlockPos pos, boolean operator) {
        BankData data = BankData.get(viewer.server);
        List<AccountRow> accounts = new ArrayList<>();
        for (BankAccount account : data.accountsOf(bank.id())) {
            accounts.add(new AccountRow(account.number(),
                    account.ownerName() == null ? "?" : account.ownerName(),
                    account.balance(),
                    account.loan() != null && !account.loan().settled(),
                    account.creditScore()));
        }
        List<PlayerRow> players = new ArrayList<>();
        for (ServerPlayer online : viewer.server.getPlayerList().getPlayers()) {
            players.add(new PlayerRow(online.getUUID(), online.getGameProfile().getName(),
                    bank.isBanker(online.getUUID()), data.hasAccount(online.getUUID())));
        }
        return new S2COpenTerminalPacket(bank, pos, operator, accounts, players,
                data.loanRequestsOf(bank.id()));
    }

    public S2COpenTerminalPacket(FriendlyByteBuf buffer) {
        this.bank = Bank.read(buffer);
        this.pos = buffer.readBlockPos();
        this.operator = buffer.readBoolean();
        int accountCount = buffer.readVarInt();
        this.accounts = new ArrayList<>(accountCount);
        for (int i = 0; i < accountCount; i++) {
            accounts.add(new AccountRow(buffer.readVarInt(), buffer.readUtf(32),
                    buffer.readVarLong(), buffer.readBoolean(), buffer.readVarInt()));
        }
        int playerCount = buffer.readVarInt();
        this.players = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++) {
            players.add(new PlayerRow(buffer.readUUID(), buffer.readUtf(32),
                    buffer.readBoolean(), buffer.readBoolean()));
        }
        int requestCount = buffer.readVarInt();
        this.requests = new ArrayList<>(requestCount);
        for (int i = 0; i < requestCount; i++) {
            requests.add(LoanRequest.read(buffer, bank.id()));
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        bank.write(buffer);
        buffer.writeBlockPos(pos);
        buffer.writeBoolean(operator);
        buffer.writeVarInt(accounts.size());
        for (AccountRow row : accounts) {
            buffer.writeVarInt(row.number());
            buffer.writeUtf(row.owner(), 32);
            buffer.writeVarLong(row.balance());
            buffer.writeBoolean(row.hasLoan());
            buffer.writeVarInt(row.score());
        }
        buffer.writeVarInt(players.size());
        for (PlayerRow row : players) {
            buffer.writeUUID(row.id());
            buffer.writeUtf(row.name(), 32);
            buffer.writeBoolean(row.banker());
            buffer.writeBoolean(row.hasAccount());
        }
        buffer.writeVarInt(requests.size());
        for (LoanRequest request : requests) {
            request.write(buffer);
        }
    }

    public Bank bank() {
        return bank;
    }

    public BlockPos pos() {
        return pos;
    }

    public boolean operator() {
        return operator;
    }

    public List<AccountRow> accounts() {
        return accounts;
    }

    public List<PlayerRow> players() {
        return players;
    }

    public List<LoanRequest> requests() {
        return requests;
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientWalletSync.openTerminal(this);
            }
        });
        ctx.setPacketHandled(true);
    }
}
