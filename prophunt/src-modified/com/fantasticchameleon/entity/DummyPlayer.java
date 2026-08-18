package com.fantasticchameleon.entity;

import com.fantasticchameleon.game.GlobalSettings;
import com.fantasticchameleon.game.Rooms;
import com.fantasticchameleon.item.ArmorPaintHandler;
import com.fantasticchameleon.item.ArmorPresets;
import com.fantasticchameleon.item.ChameleonArmor;
import com.fantasticchameleon.item.FantasticItems;
import com.fantasticchameleon.paint.BodyCanvas;
import com.fantasticchameleon.paint.BodyPart;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.Services;
import com.fantasticchameleon.pose.PoseDefs;
import com.fantasticchameleon.prophunt.PropHunt;
import com.fantasticchameleon.prophunt.PropHuntRules;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class DummyPlayer extends ServerPlayer {
   private static final Set<UUID> DUMMIES = new HashSet<>();
   private static final Random RNG = new Random();
   private static Boolean bukkitBridge;

   private DummyPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
      super(server, level, profile);
   }

   public static boolean bukkitBridgePresent() {
      if (bukkitBridge == null) {
         try {
            Class.forName("org.bukkit.Bukkit");
            bukkitBridge = Boolean.TRUE;
         } catch (ClassNotFoundException var1) {
            bukkitBridge = Boolean.FALSE;
         }
      }

      return bukkitBridge;
   }

   public static boolean isDummy(UUID id) {
      return DUMMIES.contains(id);
   }

   public static List<UUID> all() {
      return new ArrayList<>(DUMMIES);
   }

   public static int count() {
      return DUMMIES.size();
   }

   public void m_8119_() {
      if (this.m_9236_().m_7654_().m_129921_() % 10 == 0) {
         this.f_8906_.m_9953_();
         this.m_284548_().m_7726_().m_8385_(this);
         if (this.m_8958_()) {
            this.m_8959_();
         }
      }

      super.m_8119_();
      this.m_9240_();
      if (this.f_19797_ % 20 == 0) {
         this.absorbDroppedArmor();
      }
   }

   private void absorbDroppedArmor() {
      for (ItemEntity drop : this.m_284548_().m_45976_(ItemEntity.class, this.m_20191_().m_82400_(1.5))) {
         ItemStack stack = drop.m_32055_();
         if (ChameleonArmor.isChameleonPiece(stack)) {
            EquipmentSlot slot = m_147233_(stack);
            if (slot.m_254934_()) {
               int pose = Services.PLATFORM.get(this, PaintAttachments.POSE);
               float yaw = Services.PLATFORM.get(this, PaintAttachments.LOCK_YAW);
               this.m_8061_(slot, stack.m_255036_(1));
               stack.m_41774_(1);
               if (stack.m_41619_()) {
                  drop.m_146870_();
               }

               Services.PLATFORM.set(this, PaintAttachments.POSE, pose);
               Services.PLATFORM.set(this, PaintAttachments.POSING, true);
               Services.PLATFORM.set(this, PaintAttachments.LOCK_YAW, yaw);
               Services.PLATFORM.set(this, PaintAttachments.LOCKED, true);
               this.m_6210_();
            }
         }
      }
   }

   public static ServerPlayer spawn(ServerPlayer near, int index) {
      ServerLevel level = near.m_284548_();
      MinecraftServer server = level.m_7654_();
      String name = freeName(server);
      GameProfile profile = new GameProfile(UUIDUtil.m_235879_(name), name);
      DummyPlayer dummy = new DummyPlayer(server, level, profile);
      dummy.m_20331_(true);
      double angle = (double)index * 2.39996;
      double radius = 1.5 + 0.9 * Math.sqrt((double)index);
      Vec3 pos = near.m_20182_().m_82520_(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
      float yaw = RNG.nextFloat() * 360.0F;
      dummy.m_7678_(pos.f_82479_, pos.f_82480_, pos.f_82481_, yaw, 0.0F);
      server.m_6846_().m_11261_(new FakeConnection(), dummy);
      dummy.m_7678_(pos.f_82479_, pos.f_82480_, pos.f_82481_, yaw, 0.0F);
      dummy.m_143403_(GameType.SURVIVAL);
      DUMMIES.add(dummy.m_20148_());
      return dummy;
   }

   private static BodyCanvas equipPaintedArmor(ServerPlayer dummy) {
      BodyCanvas canvas = null;
      String preset = ArmorPresets.randomStarterName(dummy.m_217043_());
      if (preset != null) {
         canvas = ArmorPresets.canvas(preset);
      }

      if (canvas == null) {
         int color = 0xFF000000 | RNG.nextInt(16777216);
         canvas = BodyCanvas.EMPTY;

         for (BodyPart part : BodyPart.VALUES) {
            canvas = canvas.withRegionFilled(part, color);
         }
      }

      equip(dummy, EquipmentSlot.HEAD, FantasticItems.CHAMELEON_HELMET.get(), canvas);
      equip(dummy, EquipmentSlot.CHEST, FantasticItems.CHAMELEON_CHESTPLATE.get(), canvas);
      equip(dummy, EquipmentSlot.LEGS, FantasticItems.CHAMELEON_LEGGINGS.get(), canvas);
      equip(dummy, EquipmentSlot.FEET, FantasticItems.CHAMELEON_BOOTS.get(), canvas);
      ArmorPaintHandler.updateShrink(dummy);
      dummy.m_6210_();
      return canvas;
   }

   private static void reequip(ServerPlayer dummy, BodyCanvas canvas) {
      equip(dummy, EquipmentSlot.HEAD, FantasticItems.CHAMELEON_HELMET.get(), canvas);
      equip(dummy, EquipmentSlot.CHEST, FantasticItems.CHAMELEON_CHESTPLATE.get(), canvas);
      equip(dummy, EquipmentSlot.LEGS, FantasticItems.CHAMELEON_LEGGINGS.get(), canvas);
      equip(dummy, EquipmentSlot.FEET, FantasticItems.CHAMELEON_BOOTS.get(), canvas);
      resync(dummy);
   }

   public static void resyncIfDummy(ServerPlayer p) {
      if (isDummy(p.m_20148_())) {
         resync(p);
      }
   }

   public static void resync(ServerPlayer dummy) {
      ArmorPaintHandler.updateShrink(dummy);
      dummy.m_6210_();
      GlobalSettings.applyNametagTeam(dummy);
      ServerLevel level = dummy.m_284548_();
      List<Pair<EquipmentSlot, ItemStack>> gear = new ArrayList<>();

      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         gear.add(Pair.of(slot, dummy.m_6844_(slot).m_41777_()));
      }

      ClientboundSetEquipmentPacket eq = new ClientboundSetEquipmentPacket(dummy.m_19879_(), gear);
      ClientboundUpdateAttributesPacket at = new ClientboundUpdateAttributesPacket(dummy.m_19879_(), dummy.m_21204_().m_22170_());

      for (ServerPlayer viewer : level.m_6907_()) {
         viewer.f_8906_.m_9829_(eq);
         viewer.f_8906_.m_9829_(at);
      }
   }

   public static void dressAsHider(ServerPlayer dummy) {
      dressAsHider(dummy, PropHunt.MODE_MECCHA);
   }

   /**
    * Viste al bot segun el modo de la sala: posando y pintado en Meccha, o convertido en un prop en
    * Prop Hunt. El modo se recibe por parametro porque quien llama ya lo tiene a mano, y asi no
    * dependemos de que el bot ya figure como miembro de la sala en ese instante.
    */
   public static void dressAsHider(ServerPlayer dummy, int gameMode) {
      BodyCanvas canvas = equipPaintedArmor(dummy);
      if (PropHunt.normalize(gameMode) == PropHunt.MODE_PROP_HUNT) {
         PropHuntRules.dressAsProp(dummy);
      } else {
         applyCanvasAndPose(dummy, dummy.m_146908_(), canvas);
      }

      resync(dummy);
   }

   private static void applyCanvasAndPose(ServerPlayer dummy, float yaw, BodyCanvas canvas) {
      Services.PLATFORM.set(dummy, PaintAttachments.BODY_CANVAS, canvas);
      int[] wheel = PoseDefs.wheelPoses();
      Services.PLATFORM.set(dummy, PaintAttachments.POSE, wheel[RNG.nextInt(wheel.length)]);
      Services.PLATFORM.set(dummy, PaintAttachments.POSING, true);
      Services.PLATFORM.set(dummy, PaintAttachments.LOCK_YAW, yaw);
      Services.PLATFORM.set(dummy, PaintAttachments.LOCKED, true);
      dummy.m_6210_();
   }

   private static void equip(ServerPlayer dummy, EquipmentSlot slot, Item piece, BodyCanvas canvas) {
      ItemStack stack = FantasticItems.roomGear(piece);
      ArmorPaintHandler.storeRegion(stack, canvas);
      dummy.m_8061_(slot, stack);
   }

   public static void remove(MinecraftServer server, ServerPlayer dummy) {
      DUMMIES.remove(dummy.m_20148_());
      Rooms.leave(dummy);
      server.m_6846_().m_11286_(dummy);
   }

   public static int clearAll(MinecraftServer server) {
      int n = 0;

      for (UUID id : new HashSet<>(DUMMIES)) {
         ServerPlayer p = server.m_6846_().m_11259_(id);
         if (p != null) {
            remove(server, p);
            n++;
         } else {
            DUMMIES.remove(id);
         }
      }

      return n;
   }

   private static String freeName(MinecraftServer server) {
      List<ServerPlayer> online = server.m_6846_().m_11314_();
      int n = 1;

      while (true) {
         String name = "Dummy" + n;
         boolean taken = false;

         for (ServerPlayer p : online) {
            if (p.m_36316_().getName().equals(name)) {
               taken = true;
               break;
            }
         }

         if (!taken) {
            return name;
         }

         n++;
      }
   }
}
