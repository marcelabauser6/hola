package com.fantasticchameleon.forge;

import com.fantasticchameleon.compat.CustomPacketPayload;
import com.fantasticchameleon.compat.StreamCodec;
import com.fantasticchameleon.network.ArenaCornerPayload;
import com.fantasticchameleon.network.ArenaEditPayload;
import com.fantasticchameleon.network.ArenaListPayload;
import com.fantasticchameleon.network.ArenaPayload;
import com.fantasticchameleon.network.ArenaPreviewPayload;
import com.fantasticchameleon.network.AttachSyncPayload;
import com.fantasticchameleon.network.BrushPaintPayload;
import com.fantasticchameleon.network.CanvasDeltaPayload;
import com.fantasticchameleon.network.ClientHooks;
import com.fantasticchameleon.network.ClimbPayload;
import com.fantasticchameleon.network.CrawlPayload;
import com.fantasticchameleon.network.CreatorRollPayload;
import com.fantasticchameleon.network.CreatorSkinPayload;
import com.fantasticchameleon.network.EditorActionPayload;
import com.fantasticchameleon.network.FantasticNetwork;
import com.fantasticchameleon.network.FantasticVersionPayload;
import com.fantasticchameleon.network.ForceExitPayload;
import com.fantasticchameleon.network.GlobalSettingsPayload;
import com.fantasticchameleon.network.InvitePayload;
import com.fantasticchameleon.network.LockPayload;
import com.fantasticchameleon.network.MapRollPayload;
import com.fantasticchameleon.network.MovePayload;
import com.fantasticchameleon.network.NudgePayload;
import com.fantasticchameleon.network.OpenEditorPayload;
import com.fantasticchameleon.network.OpenMenuPayload;
import com.fantasticchameleon.network.PaintSplatPayload;
import com.fantasticchameleon.network.PickModePayload;
import com.fantasticchameleon.network.PosePayload;
import com.fantasticchameleon.network.PreviewDataPayload;
import com.fantasticchameleon.network.PreviewRequestPayload;
import com.fantasticchameleon.network.PropActPayload;
import com.fantasticchameleon.network.ProvokePayload;
import com.fantasticchameleon.prophunt.PropHuntActs;
import com.fantasticchameleon.network.RequestRoomsPayload;
import com.fantasticchameleon.network.RoomActionPayload;
import com.fantasticchameleon.network.RoomConfigPayload;
import com.fantasticchameleon.network.RoomsPayload;
import com.fantasticchameleon.network.RoundStatePayload;
import com.fantasticchameleon.network.SchematicsPayload;
import com.fantasticchameleon.network.SeekerDraftPayload;
import com.fantasticchameleon.network.SetCanvasPayload;
import com.fantasticchameleon.network.SetOrientPayload;
import com.fantasticchameleon.network.SetPropCanvasPayload;
import com.fantasticchameleon.network.SetPropPayload;
import com.fantasticchameleon.network.SetSizePayload;
import com.fantasticchameleon.network.ShaderStatePayload;
import com.fantasticchameleon.network.WhistlePayload;
import com.fantasticchameleon.platform.ClientNet;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ForgeNetwork {
   private static final String PROTOCOL = "1";
   private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      new ResourceLocation("fantastic_chameleon", "main"), () -> "1", v -> true, v -> true
   );
   private static int nextId;

   private ForgeNetwork() {
   }

   public static void register() {
      c2s(SetCanvasPayload.class, SetCanvasPayload.STREAM_CODEC, FantasticNetwork::handleSetCanvas);
      c2s(SetPropCanvasPayload.class, SetPropCanvasPayload.STREAM_CODEC, FantasticNetwork::handleSetPropCanvas);
      c2s(SetPropPayload.class, SetPropPayload.STREAM_CODEC, FantasticNetwork::handleSetProp);
      c2s(CanvasDeltaPayload.class, CanvasDeltaPayload.STREAM_CODEC, FantasticNetwork::handleCanvasDelta);
      c2s(LockPayload.class, LockPayload.STREAM_CODEC, FantasticNetwork::handleLock);
      c2s(PosePayload.class, PosePayload.STREAM_CODEC, FantasticNetwork::handlePose);
      c2s(ProvokePayload.class, ProvokePayload.STREAM_CODEC, FantasticNetwork::handleProvoke);
      c2s(PropActPayload.class, PropActPayload.STREAM_CODEC, PropHuntActs::handle);
      c2s(RequestRoomsPayload.class, RequestRoomsPayload.STREAM_CODEC, FantasticNetwork::handleRequestRooms);
      c2s(ShaderStatePayload.class, ShaderStatePayload.STREAM_CODEC, FantasticNetwork::handleShaderState);
      c2s(NudgePayload.class, NudgePayload.STREAM_CODEC, FantasticNetwork::handleNudge);
      c2s(SetOrientPayload.class, SetOrientPayload.STREAM_CODEC, FantasticNetwork::handleSetOrient);
      c2s(MovePayload.class, MovePayload.STREAM_CODEC, FantasticNetwork::handleMove);
      c2s(SetSizePayload.class, SetSizePayload.STREAM_CODEC, FantasticNetwork::handleSetSize);
      c2s(ClimbPayload.class, ClimbPayload.STREAM_CODEC, FantasticNetwork::handleClimb);
      c2s(ArenaCornerPayload.class, ArenaCornerPayload.STREAM_CODEC, FantasticNetwork::handleArenaCorner);
      c2s(ArenaEditPayload.class, ArenaEditPayload.STREAM_CODEC, FantasticNetwork::handleArenaEdit);
      c2s(PreviewRequestPayload.class, PreviewRequestPayload.STREAM_CODEC, FantasticNetwork::handlePreviewRequest);
      c2s(ArenaPreviewPayload.class, ArenaPreviewPayload.STREAM_CODEC, FantasticNetwork::handleArenaPreview);
      c2s(BrushPaintPayload.class, BrushPaintPayload.STREAM_CODEC, FantasticNetwork::handleBrushPaint);
      c2s(CrawlPayload.class, CrawlPayload.STREAM_CODEC, FantasticNetwork::handleCrawl);
      c2s(RoomConfigPayload.class, RoomConfigPayload.STREAM_CODEC, FantasticNetwork::handleRoomConfig);
      c2s(RoomActionPayload.class, RoomActionPayload.STREAM_CODEC, FantasticNetwork::handleRoomAction);
      c2s(EditorActionPayload.class, EditorActionPayload.STREAM_CODEC, FantasticNetwork::handleEditorAction);
      s2c(FantasticVersionPayload.class, FantasticVersionPayload.STREAM_CODEC, ForgeNetwork::handleVersion);
      s2c(RoomsPayload.class, RoomsPayload.STREAM_CODEC, p -> ClientHooks.onRooms.accept(p));
      s2c(ArenaPayload.class, ArenaPayload.STREAM_CODEC, p -> ClientHooks.onArena.accept(p));
      s2c(ArenaListPayload.class, ArenaListPayload.STREAM_CODEC, p -> ClientHooks.onArenaList.accept(p));
      s2c(PreviewDataPayload.class, PreviewDataPayload.STREAM_CODEC, p -> ClientHooks.onArenaPreview.accept(p));
      s2c(RoundStatePayload.class, RoundStatePayload.STREAM_CODEC, p -> ClientHooks.onRoundState.accept(p));
      s2c(SeekerDraftPayload.class, SeekerDraftPayload.STREAM_CODEC, p -> ClientHooks.onSeekerDraft.accept(p));
      s2c(MapRollPayload.class, MapRollPayload.STREAM_CODEC, p -> ClientHooks.onMapRoll.accept(p));
      s2c(GlobalSettingsPayload.class, GlobalSettingsPayload.STREAM_CODEC, p -> ClientHooks.onGlobalSettings.accept(p));
      s2c(PaintSplatPayload.class, PaintSplatPayload.STREAM_CODEC, p -> ClientHooks.onPaintSplat.accept(p));
      s2c(WhistlePayload.class, WhistlePayload.STREAM_CODEC, p -> ClientHooks.onWhistle.accept(p));
      s2c(ForceExitPayload.class, ForceExitPayload.STREAM_CODEC, p -> ClientHooks.onForceExit.run());
      s2c(OpenMenuPayload.class, OpenMenuPayload.STREAM_CODEC, p -> ClientHooks.onOpenMenu.run());
      s2c(InvitePayload.class, InvitePayload.STREAM_CODEC, p -> ClientHooks.onInvite.accept(p));
      s2c(CreatorSkinPayload.class, CreatorSkinPayload.STREAM_CODEC, p -> ClientHooks.onCreatorSkin.accept(p.id()));
      s2c(CreatorRollPayload.class, CreatorRollPayload.STREAM_CODEC, p -> ClientHooks.onCreatorRoll.accept(p));
      s2c(AttachSyncPayload.class, AttachSyncPayload.STREAM_CODEC, ForgeNetwork::handleAttachSync);
      s2c(OpenEditorPayload.class, OpenEditorPayload.STREAM_CODEC, p -> ClientHooks.onOpenEditor.accept(p));
      s2c(PickModePayload.class, PickModePayload.STREAM_CODEC, p -> ClientHooks.onPickMode.accept(p));
      s2c(SchematicsPayload.class, SchematicsPayload.STREAM_CODEC, p -> ClientHooks.onSchematics.accept(p));
      ClientNet.sender = ForgeNetwork::sendToServer;
   }

   private static <T extends CustomPacketPayload> void c2s(Class<T> type, StreamCodec<? super ByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
      CHANNEL.registerMessage(nextId++, type, (msg, buf) -> codec.encode(buf, (T)msg), buf -> codec.decode(buf), (msg, ctxSupplier) -> {
         Context ctx = (Context)ctxSupplier.get();
         ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
               handler.accept((T)msg, sender);
            }
         });
         ctx.setPacketHandled(true);
      }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
   }

   private static <T extends CustomPacketPayload> void s2c(Class<T> type, StreamCodec<? super ByteBuf, T> codec, Consumer<T> handler) {
      CHANNEL.registerMessage(nextId++, type, (msg, buf) -> codec.encode(buf, (T)msg), buf -> codec.decode(buf), (msg, ctxSupplier) -> {
         Context ctx = (Context)ctxSupplier.get();
         ctx.enqueueWork(() -> handler.accept((T)msg));
         ctx.setPacketHandled(true);
      }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
   }

   public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
   }

   public static void sendToServer(CustomPacketPayload payload) {
      CHANNEL.sendToServer(payload);
   }

   public static void sendToChunk(ServerLevel level, ChunkPos pos, CustomPacketPayload payload) {
      LevelChunk chunk = level.m_6325_(pos.f_45578_, pos.f_45579_);
      CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), payload);
   }

   public static void syncAttachment(Entity entity, String name, byte[] data) {
      AttachSyncPayload payload = new AttachSyncPayload(entity.m_19879_(), name, data);
      if (entity instanceof ServerPlayer) {
         CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), payload);
      } else {
         CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), payload);
      }
   }

   private static void handleVersion(FantasticVersionPayload payload) {
      if (FMLEnvironment.dist == Dist.CLIENT) {
         FantasticNetwork.versionMismatch(payload.version()).ifPresent(ClientAttachments::disconnect);
      }
   }

   private static void handleAttachSync(AttachSyncPayload payload) {
      if (FMLEnvironment.dist == Dist.CLIENT) {
         ClientAttachments.apply(payload);
      }
   }
}
