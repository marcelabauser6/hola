package com.athensmc.athenscoins.network;

import com.athensmc.athenscoins.block.StatsHologramBlockEntity;
import com.athensmc.athenscoins.stats.HologramConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A hologram's whole configuration, submitted in one go.
 *
 * <p>The config travels as NBT rather than as a field-by-field buffer, and that is the same decision
 * the block entity makes for its own storage - {@link HologramConfig#save()} is the single definition
 * of what a hologram is. A hand-written buffer pair here would be a second one, and a reader that
 * drifted from its writer does not throw: it produces a hologram whose colours have swapped places.</p>
 *
 * <p>Everything the client sends is treated as a suggestion. The position must be a real projector
 * within reach, the sender must be an operator, and every value goes through
 * {@link HologramConfig#load} whose setters clamp - so a crafted packet cannot install a hologram
 * scaled to ten thousand percent or with two hundred lines.</p>
 */
public class C2SHologramConfigPacket {

    private static final double MAX_REACH_SQR = 64.0D;

    private final BlockPos pos;
    private final CompoundTag config;

    public C2SHologramConfigPacket(BlockPos pos, HologramConfig config) {
        this.pos = pos;
        this.config = config.save();
    }

    public C2SHologramConfigPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.config = buffer.readNbt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeNbt(config);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                    > MAX_REACH_SQR) {
                return;
            }
            BlockEntity entity = player.level().getBlockEntity(pos);
            if (!(entity instanceof StatsHologramBlockEntity projector)) {
                return;
            }
            // The same verdict the block reaches when it decides whether to open the editor. Asking the
            // block rather than repeating the rule is what stops the two from disagreeing - and a client
            // that is refused the screen but accepted by the save packet can edit anything.
            if (!com.athensmc.athenscoins.block.StatsHologramBlock.canEdit(player, player.level(), pos)) {
                player.sendSystemMessage(Component
                        .translatable("message.athens_coins.hologram_op_only")
                        .withStyle(ChatFormatting.RED));
                return;
            }
            projector.applyConfig(HologramConfig.load(config));
            player.sendSystemMessage(Component
                    .translatable("message.athens_coins.hologram_saved")
                    .withStyle(ChatFormatting.GREEN));
        });
        ctx.setPacketHandled(true);
    }
}
