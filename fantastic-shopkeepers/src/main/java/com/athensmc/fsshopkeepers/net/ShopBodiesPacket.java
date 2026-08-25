package com.athensmc.fsshopkeepers.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server to client: these entities are shopkeepers.
 *
 * <p>The client cannot work this out for itself. A shopkeeper is an ordinary villager, sheep or golem with a tag in its
 * server-side data, and entity NBT is never sent to clients - so to the client it is just a villager. That gap is what let
 * Easy Villagers pick a shopkeeper up: its handler runs on the client, decides a sneaking right-click means "pocket this
 * villager", and sends its own packet. This mod's refusal ran on the server, long after.</p>
 *
 * <p>The whole set is sent rather than changes to it. It is a few dozen uuids on a busy server, it is only sent when the set
 * actually changes, and a full replacement cannot drift out of step with the server the way a stream of additions and
 * removals can.</p>
 */
public final class ShopBodiesPacket {

    /** A sane ceiling, so a malformed packet cannot make the client allocate without bound. */
    private static final int MAX_BODIES = 20_000;

    private final List<UUID> bodies;

    public ShopBodiesPacket(List<UUID> bodies) {
        this.bodies = bodies;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(bodies.size());
        for (UUID id : bodies) {
            buf.writeUUID(id);
        }
    }

    public static ShopBodiesPacket decode(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_BODIES);
        List<UUID> bodies = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            bodies.add(buf.readUUID());
        }
        return new ShopBodiesPacket(bodies);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.athensmc.fsshopkeepers.client.ClientShopBodies.replace(bodies)));
        ctx.setPacketHandled(true);
    }
}
