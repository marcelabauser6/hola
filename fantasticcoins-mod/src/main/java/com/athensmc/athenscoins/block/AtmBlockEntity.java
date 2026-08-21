package com.athensmc.athenscoins.block;

import com.athensmc.athenscoins.bank.Bank;
import com.athensmc.athenscoins.bank.BankData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Remembers which bank issued an ATM.
 *
 * <p>ATMs are handed out by a bank's terminal and carry that bank's identity: its name, its colour
 * and its exchange rates. Two banks' machines standing in different squares therefore quote
 * different prices, which is the point of letting them compete.</p>
 */
public class AtmBlockEntity extends BlockEntity {

    public static final String TAG_BANK = "FcBank";
    public static final String TAG_BANK_NAME = "FcBankName";
    public static final String TAG_BANK_COLOR = "FcBankColor";

    @Nullable
    private UUID bankId;
    private String bankName = "";
    private int themeColor = 0x2E4756;

    public AtmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ATM.get(), pos, state);
    }

    // ------------------------------------------------------------------ identity

    @Nullable
    public UUID bankId() {
        return bankId;
    }

    public String bankName() {
        return bankName;
    }

    public int themeColor() {
        return themeColor;
    }

    /** True for a machine that was never issued by a terminal. */
    public boolean unbranded() {
        return bankId == null;
    }

    @Nullable
    public Bank bank(MinecraftServer server) {
        return bankId == null ? null : BankData.get(server).bank(bankId);
    }

    /** Copies the branding off the item that was placed. */
    public void applyFrom(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID(TAG_BANK)) {
            return;
        }
        bankId = tag.getUUID(TAG_BANK);
        bankName = tag.getString(TAG_BANK_NAME);
        themeColor = tag.contains(TAG_BANK_COLOR) ? tag.getInt(TAG_BANK_COLOR) : 0x2E4756;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Stamps an ATM item with a bank's identity, ready to be placed. */
    public static void brand(ItemStack stack, Bank bank) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(TAG_BANK, bank.id());
        tag.putString(TAG_BANK_NAME, bank.name());
        tag.putInt(TAG_BANK_COLOR, bank.themeColor());
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (bankId != null) {
            tag.putUUID(TAG_BANK, bankId);
        }
        tag.putString(TAG_BANK_NAME, bankName);
        tag.putInt(TAG_BANK_COLOR, themeColor);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        bankId = tag.hasUUID(TAG_BANK) ? tag.getUUID(TAG_BANK) : null;
        bankName = tag.getString(TAG_BANK_NAME);
        themeColor = tag.contains(TAG_BANK_COLOR) ? tag.getInt(TAG_BANK_COLOR) : 0x2E4756;
    }

    /** Sent to clients so the machine can show its owner's name without a round trip. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
