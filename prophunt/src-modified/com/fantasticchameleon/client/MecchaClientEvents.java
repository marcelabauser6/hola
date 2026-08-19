package com.fantasticchameleon.client;

import com.fantasticchameleon.item.ArenaWandItem;
import com.fantasticchameleon.item.FantasticItems;
import com.fantasticchameleon.item.ShotgunItem;
import com.fantasticchameleon.network.MecchaAttachPayload;
import com.fantasticchameleon.platform.ClientNet;
import com.fantasticchameleon.prophunt.PropHuntClient;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Intercepta el clic de acople sólo cuando el cliente sabe que es un hider Meccha activo. */
@Mod.EventBusSubscriber(modid = "fantastic_chameleon", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MecchaClientEvents {
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

   private static boolean isTool(ItemStack stack) {
      return stack != null && !stack.m_41619_()
         && (ArenaWandItem.is(stack) || stack.m_41720_() instanceof ShotgunItem || stack.m_150930_(FantasticItems.PAINT_BRUSH.get()));
   }
}
