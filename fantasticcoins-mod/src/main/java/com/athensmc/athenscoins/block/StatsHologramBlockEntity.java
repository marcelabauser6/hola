package com.athensmc.athenscoins.block;

import com.athensmc.athenscoins.stats.EconomySnapshot;
import com.athensmc.athenscoins.stats.HologramConfig;
import com.athensmc.athenscoins.stats.StatsCache;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/**
 * The projector behind a stats hologram: what to show, and the figures to show.
 *
 * <p>Two pieces of state with deliberately different lifetimes. The {@link HologramConfig} is the
 * admin's work and is written to disk. The {@link EconomySnapshot} is derived from the bank data and is
 * <em>not</em>: saving it would put a stale copy of numbers that already live in {@code BankData} on
 * disk, and then something would have to decide which of the two to believe after a restart. It is
 * recomputed by the server sweep and only ever travels in the sync tag.</p>
 */
public class StatsHologramBlockEntity extends BlockEntity {

    /** Five seconds. Economy figures do not move fast enough to justify anything tighter. */
    private static final int REFRESH_TICKS = 100;

    private HologramConfig config = HologramConfig.defaults();
    private EconomySnapshot snapshot = EconomySnapshot.empty();
    /** Starts due, so a projector shows real numbers on the first tick after being placed. */
    private int ticksUntilRefresh;

    public StatsHologramBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STATS_HOLOGRAM.get(), pos, state);
    }

    /**
     * Refreshes the figures on a timer, and only sends them when they have actually changed.
     *
     * <p>The snapshot comes from {@link StatsCache}, so six projectors in a market square cost one
     * sweep of the account list between them rather than six.</p>
     */
    public void serverTick(Level level) {
        if (--ticksUntilRefresh > 0) {
            return;
        }
        ticksUntilRefresh = REFRESH_TICKS;
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        EconomySnapshot fresh = StatsCache.snapshot(server);
        if (!fresh.sameValues(snapshot)) {
            pushSnapshot(fresh);
        }
    }

    public HologramConfig config() {
        return config;
    }

    public EconomySnapshot snapshot() {
        return snapshot;
    }

    /** Applies an edited config and tells everyone watching. */
    public void applyConfig(HologramConfig incoming) {
        config = incoming;
        setChanged();
        sync();
    }

    /**
     * Takes new figures from the server sweep.
     *
     * <p>No {@code setChanged()}: that marks the chunk dirty for saving, and these numbers are not
     * saved. Calling it every few seconds for every projector in the world would rewrite chunks for
     * data that is thrown away on load.</p>
     */
    public void pushSnapshot(EconomySnapshot incoming) {
        snapshot = incoming;
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("hologram", config.save());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("hologram")) {
            config = HologramConfig.load(tag.getCompound("hologram"));
        }
        // Only present in the sync tag, never on disk. Forge routes handleUpdateTag through load, so
        // this one method covers both the chunk read and the client update.
        if (tag.contains("stats")) {
            snapshot = EconomySnapshot.load(tag.getCompound("stats"));
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        tag.put("stats", snapshot.save());
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * The box the renderer is allowed to draw in.
     *
     * <p>Block entity renderers are culled against this, and the default is the one block the projector
     * occupies. A hologram floats above it and can be pushed up to eight blocks higher, so with the
     * default box the text would vanish the moment the projector itself left the view - the classic
     * symptom being floating text that disappears when you look up at it.</p>
     */
    @Override
    public AABB getRenderBoundingBox() {
        float top = Math.max(1.0F, config.heightOffset() + 4.0F);
        return new AABB(worldPosition).inflate(2.0D, 0.0D, 2.0D)
                .expandTowards(0.0D, top, 0.0D);
    }
}
