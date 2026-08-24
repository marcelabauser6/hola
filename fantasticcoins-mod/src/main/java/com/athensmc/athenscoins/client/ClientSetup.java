package com.athensmc.athenscoins.client;

import com.athensmc.athenscoins.AthensCoinsMod;
import com.athensmc.athenscoins.block.AtmBlockEntity;
import com.athensmc.athenscoins.block.ModBlockEntities;
import com.athensmc.athenscoins.block.ModBlocks;
import com.athensmc.athenscoins.client.render.StatsHologramRenderer;
import com.athensmc.athenscoins.client.screen.AtmScreen;
import com.athensmc.athenscoins.item.ModItems;
import com.athensmc.athenscoins.menu.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = AthensCoinsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    /** What an unbranded machine's band is tinted with: the same steel blue the cabinet already is. */
    private static final int DEFAULT_TINT = 0x2E4756;

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // The wallet is a plain Screen opened by packet, so only the ATM needs a menu screen.
        event.enqueueWork(() -> MenuScreens.register(ModMenus.ATM.get(), AtmScreen::new));
    }

    /**
     * Paints each ATM in its issuing bank's colour.
     *
     * <p>A tint, not a texture per colour. Twelve palette entries would otherwise mean twelve copies of
     * every face of the machine, and adding a thirteenth colour would mean redrawing the set; the band
     * is drawn once in greys and multiplied here. Tint index 0 is the only one the model uses, and only
     * on the band, so the rest of the cabinet stays brushed steel whatever colour a bank picks.</p>
     *
     * <p>The lower half holds the block entity, so the upper half - which is the half the band is
     * actually on - has to look down for its colour. Reading its own position would give the default
     * every time and the band would never change.</p>
     */
    /**
     * The stats hologram's world renderer.
     *
     * <p>The mod's only block entity renderer. Everything else it draws is either a GUI or a block model,
     * which is why this is the one place {@code EntityRenderersEvent} appears.</p>
     */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.STATS_HOLOGRAM.get(),
                StatsHologramRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (level == null || pos == null) {
                return DEFAULT_TINT;
            }
            if (level.getBlockEntity(pos) instanceof AtmBlockEntity atm) {
                return atm.themeColor();
            }
            if (level.getBlockEntity(pos.below()) instanceof AtmBlockEntity below) {
                return below.themeColor();
            }
            return DEFAULT_TINT;
        }, ModBlocks.ATM.get());
    }

    /**
     * The same colour on the item, read from the branding the terminal stamped on it.
     *
     * <p>Without this an issued machine would be the bank's colour on the ground and plain steel in the
     * inventory, so a banker holding four ATMs for four banks could not tell them apart.</p>
     */
    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            CompoundTag tag = stack.getTag();
            return tag != null && tag.contains(AtmBlockEntity.TAG_BANK_COLOR)
                    ? tag.getInt(AtmBlockEntity.TAG_BANK_COLOR) : DEFAULT_TINT;
        }, ModItems.ATM_ITEM.get());
    }
}
