package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankAccount;
import com.athensmc.athenscoins.bank.BankData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Asks the server for one account's detail. Authority is checked here, not on the client. */
public class C2SRequestAccountPacket {

    private final BlockPos pos;
    private final int number;

    public C2SRequestAccountPacket(BlockPos pos, int number) {
        this.pos = pos;
        this.number = number;
    }

    public C2SRequestAccountPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.number = buffer.readVarInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeVarInt(number);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
                return;
            }
            BankData data = BankData.get(player.server);
            Bank bank = data.bankAt(pos);
            if (bank == null) {
                return;
            }
            if (!player.hasPermissions(2) && !bank.isBanker(player.getUUID())) {
                return;
            }
            BankAccount account = data.account(number);
            // Staff may only read their own bank's books.
            if (account == null || !account.bankId().equals(bank.id())) {
                return;
            }
            ModNetwork.toPlayer(player, new S2COpenAccountPacket(pos, account, bank));
        });
        ctx.setPacketHandled(true);
    }
}
