package com.fantasticchameleon.network;

import com.fantasticchameleon.compat.CustomPacketPayload;
import com.fantasticchameleon.compat.StreamCodec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record RoomsPayload(
   List<RoomSummary> rooms,
   String yourRoom,
   int[] config,
   int filters,
   int hiderCount,
   int seekerCount,
   boolean canManage,
   boolean isOp,
   boolean freeArena,
   List<String> arenas,
   List<Boolean> arenaBusy,
   List<Integer> arenaRecMin,
   List<Integer> arenaRecMax,
   String selectedArena
) implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<RoomsPayload> TYPE = new CustomPacketPayload.Type<>(new ResourceLocation("fantastic_chameleon", "rooms"));
   public static final int CFG_LEN = 22;
   public static final StreamCodec<ByteBuf, RoomsPayload> STREAM_CODEC = new StreamCodec<ByteBuf, RoomsPayload>() {
      public RoomsPayload decode(ByteBuf buf) {
         FriendlyByteBuf b = new FriendlyByteBuf(buf);
         int n = b.m_130242_();
         List<RoomSummary> rooms = new ArrayList<>(n);

         for (int i = 0; i < n; i++) {
            String name = b.m_130277_();
            boolean locked = b.readBoolean();
            int size = b.m_130242_();
            int phase = b.m_130242_();
            boolean member = b.readBoolean();
            int role = b.m_130242_() - 1;
            int cap = b.m_130242_();
            int m = b.m_130242_();
            List<RoomMember> members = new ArrayList<>(m);

            for (int j = 0; j < m; j++) {
               members.add(new RoomMember(b.m_130259_(), b.m_130277_(), b.readBoolean()));
            }

            boolean featured = b.readBoolean();
            int secondsLeft = b.m_130242_();
            rooms.add(new RoomSummary(name, locked, size, phase, member, role, cap, members, featured, secondsLeft));
         }

         String yourRoom = b.m_130277_();
         int[] cfg = new int[CFG_LEN];

         for (int i = 0; i < CFG_LEN; i++) {
            cfg[i] = b.m_130242_();
         }

         int filters = b.m_130242_();
         int hiderCount = b.m_130242_();
         int seekerCount = b.m_130242_();
         boolean canManage = b.readBoolean();
         boolean isOp = b.readBoolean();
         boolean freeArena = b.readBoolean();
         int an = b.m_130242_();
         List<String> arenas = new ArrayList<>(an);
         List<Boolean> arenaBusy = new ArrayList<>(an);
         List<Integer> recMin = new ArrayList<>(an);
         List<Integer> recMax = new ArrayList<>(an);

         for (int i = 0; i < an; i++) {
            arenas.add(b.m_130277_());
            arenaBusy.add(b.readBoolean());
            recMin.add(b.m_130242_());
            recMax.add(b.m_130242_());
         }

         String selectedArena = b.m_130277_();
         return new RoomsPayload(
            rooms, yourRoom, cfg, filters, hiderCount, seekerCount, canManage, isOp, freeArena, arenas, arenaBusy, recMin, recMax, selectedArena
         );
      }

      public void encode(ByteBuf buf, RoomsPayload value) {
         FriendlyByteBuf b = new FriendlyByteBuf(buf);
         b.m_130130_(value.rooms.size());

         for (RoomSummary r : value.rooms) {
            b.m_130070_(r.name());
            b.writeBoolean(r.locked());
            b.m_130130_(r.size());
            b.m_130130_(r.phase());
            b.writeBoolean(r.member());
            b.m_130130_(r.role() + 1);
            b.m_130130_(r.cap());
            List<RoomMember> members = r.members();
            b.m_130130_(members.size());

            for (RoomMember rm : members) {
               b.m_130077_(rm.id() == null ? new UUID(0L, 0L) : rm.id());
               b.m_130070_(rm.name());
               b.writeBoolean(rm.leader());
            }

            b.writeBoolean(r.featured());
            b.m_130130_(Math.max(0, r.secondsLeft()));
         }

         b.m_130070_(value.yourRoom);

         for (int i = 0; i < CFG_LEN; i++) {
            b.m_130130_(i < value.config.length ? value.config[i] : 0);
         }

         b.m_130130_(value.filters);
         b.m_130130_(value.hiderCount);
         b.m_130130_(value.seekerCount);
         b.writeBoolean(value.canManage);
         b.writeBoolean(value.isOp);
         b.writeBoolean(value.freeArena);
         b.m_130130_(value.arenas.size());

         for (int i = 0; i < value.arenas.size(); i++) {
            b.m_130070_(value.arenas.get(i));
            b.writeBoolean(i < value.arenaBusy.size() && Boolean.TRUE.equals(value.arenaBusy.get(i)));
            b.m_130130_(i < value.arenaRecMin.size() ? value.arenaRecMin.get(i) : 0);
            b.m_130130_(i < value.arenaRecMax.size() ? value.arenaRecMax.get(i) : 0);
         }

         b.m_130070_(value.selectedArena == null ? "" : value.selectedArena);
      }
   };

   @Override
   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
