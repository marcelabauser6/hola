package com.fantasticchameleon.prophunt;

import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Alineacion al grid para el modo Prop Hunt.
 *
 * <p>El modo clasico usa {@code placeAgainstCover}, que pega al jugador contra la pared mas cercana
 * para que su silueta pintada encaje en el escenario. Para un prop eso esta mal: un bloque tiene que
 * quedar en el centro exacto de su celda y con las caras paralelas a los ejes del mundo, igual que
 * cuando colocas un bloque a mano.
 *
 * <p>Por eso aqui centramos al jugador en la celda que ocupa (x/z a .5, y al suelo de la celda) y
 * dejamos el yaw en multiplos de 90 grados.
 */
public final class PropGridSnap {

   private PropGridSnap() {
   }

   /** Redondea un yaw cualquiera al multiplo de 90 grados mas cercano, normalizado a [-180,180). */
   public static float snapYaw(float yaw) {
      return Mth.m_14177_((float)Math.round(yaw / 90.0F) * 90.0F);
   }

   /**
    * Centra al jugador en su celda y fija el yaw alineado con los ejes.
    *
    * <p>Solo teleporta si el hueco destino esta libre; si el centro esta obstruido preferimos dejar
    * al jugador donde estaba antes que empujarlo dentro de un bloque.
    *
    * @return true si se pudo centrar
    */
   public static boolean snapToCell(ServerPlayer player, float yaw) {
      float aligned = snapYaw(yaw);
      Services.PLATFORM.set(player, PaintAttachments.LOCK_YAW, aligned);

      Vec3 target = cellCenter(player);
      if (target == null) {
         return false;
      }

      if (canOccupy(player, target)) {
         player.m_6021_(target.f_82479_, target.f_82480_, target.f_82481_);
         return true;
      }

      return false;
   }

   /** Centro horizontal de la celda apoyado en la superficie de colisión real bajo ese centro. */
   public static Vec3 cellCenter(Player player) {
      int bx = Mth.m_14107_(player.m_20185_());
      int bz = Mth.m_14107_(player.m_20189_());
      return new Vec3((double)bx + 0.5, supportY(player, bx, bz), (double)bz + 0.5);
   }

   /**
    * Resuelve la caja del VoxelShape que cubre exactamente x/z=0.5. Esto mantiene al prop sobre
    * losas, escaleras, vallas y nieve en vez de forzarlo al Y entero de la celda.
    */
   private static double supportY(Player player, int bx, int bz) {
      double current = player.m_20186_();
      double best = Double.NEGATIVE_INFINITY;
      int highest = Mth.m_14107_(current + 0.75);
      int lowest = Mth.m_14107_(current - 1.75);
      for (int by = highest; by >= lowest; by--) {
         BlockPos pos = new BlockPos(bx, by, bz);
         for (AABB box : player.m_9236_().m_8055_(pos).m_60812_(player.m_9236_(), pos).m_83299_()) {
            if (0.5 >= box.f_82288_ && 0.5 <= box.f_82291_ && 0.5 >= box.f_82290_ && 0.5 <= box.f_82293_) {
               double top = (double)by + box.f_82292_;
               if (top <= current + 0.75 && top > best) {
                  best = top;
               }
            }
         }
      }

      return Double.isFinite(best) ? best : (double)Mth.m_14107_(current);
   }

   /** True si el jugador cabe con su hitbox actual en la posicion destino. */
   private static boolean canOccupy(Player player, Vec3 target) {
      Vec3 delta = target.m_82546_(player.m_20182_());
      if (delta.m_82556_() < 1.0E-6) {
         return true;
      }

      return player.m_9236_().m_45756_(player, player.m_20191_().m_82383_(delta));
   }
}
