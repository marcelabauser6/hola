package com.fantasticchameleon.client;

import com.fantasticchameleon.item.ArenaWandItem;
import com.fantasticchameleon.item.FantasticItems;
import com.fantasticchameleon.item.ShotgunItem;
import com.fantasticchameleon.network.MecchaAttachPayload;
import com.fantasticchameleon.paint.PaintAttachments;
import com.fantasticchameleon.platform.ClientNet;
import com.fantasticchameleon.platform.Services;
import com.fantasticchameleon.prophunt.PropHuntClient;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Intercepta el clic de acople sólo cuando el cliente sabe que es un hider Meccha activo. */
@Mod.EventBusSubscriber(modid = "fantastic_chameleon", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MecchaClientEvents {
   private static boolean attachedView;
   private static CameraType previousView;

   private MecchaClientEvents() {
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
      if (event.getHand() != InteractionHand.MAIN_HAND
         || !event.getLevel().m_5776_()
         || isTool(event.getItemStack())
         || !PropHuntClient.canUseMecchaAttach()) {
         return;
      }

      BlockHitResult ray = event.getHitVec();
      Vec3 hit = ray.m_82450_();
      int bx = event.getPos().m_123341_();
      int by = event.getPos().m_123342_();
      int bz = event.getPos().m_123343_();
      ClientNet.sendToServer(new MecchaAttachPayload(
         event.getPos(), ray.m_82434_(),
         (float)(hit.f_82479_ - (double)bx),
         (float)(hit.f_82480_ - (double)by),
         (float)(hit.f_82481_ - (double)bz),
         event.getEntity().m_146908_()
      ));
      event.setCanceled(true);
   }

   /**
    * Al quedarte pegado a un bloque la cámara pasa a tercera persona, y al soltarte vuelve a lo que
    * tenías.
    *
    * <p>Estar adherido significa que la cara del cuerpo toca el bloque, así que en primera persona la
    * cámara queda dentro de la pared y no se ve nada. Lo que interesa ver es justamente tu cuerpo pegado,
    * y eso se ve desde atrás. Se dispara con el atributo sincronizado del propio acople, así que la
    * cámara cambia cuando el servidor confirma la adherencia, no cuando el cliente lo intenta.
    */
   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }

      Minecraft mc = Minecraft.m_91087_();
      if (mc.f_91074_ == null || mc.f_91066_ == null) {
         attachedView = false;
         return;
      }

      boolean attached = Boolean.TRUE.equals(Services.PLATFORM.getOrNull(mc.f_91074_, PaintAttachments.ATTACHED));
      if (attached && !attachedView) {
         // Vista libre al quedar pegado: la cámara sale del cuerpo y puedes rodearte para comprobar cómo
         // te ve el que busca. Con tercera persona fija seguía metiéndose en el bloque.
         previousView = mc.f_91066_.m_92176_();
         if (!FreeCam.active()) {
            FreeCam.enable();
         }

         attachedView = true;
      } else if (!attached && attachedView) {
         if (FreeCam.active()) {
            FreeCam.disable();
         }

         mc.f_91066_.m_92157_(previousView == null ? CameraType.FIRST_PERSON : previousView);
         previousView = null;
         attachedView = false;
      }

      JadeOverlay.updateForRound();
   }

   private static boolean isTool(ItemStack stack) {
      return stack != null && !stack.m_41619_()
         && (ArenaWandItem.is(stack) || stack.m_41720_() instanceof ShotgunItem || stack.m_150930_(FantasticItems.PAINT_BRUSH.get()));
   }
}
