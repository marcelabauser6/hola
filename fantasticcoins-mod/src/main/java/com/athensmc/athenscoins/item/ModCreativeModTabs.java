package com.athensmc.athenscoins.item;

import com.athensmc.athenscoins.AthensCoinsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeModTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AthensCoinsMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> ATHENS_COINS_TAB = CREATIVE_MODE_TABS.register(
            "athens_coins_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.BRONZE_COIN.get()))
                    .title(Component.translatable("creative_tab.athens_coins_tab"))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.BRONZE_COIN.get());
                        output.accept(ModItems.SILVER_COIN.get());
                        output.accept(ModItems.GOLD_COIN.get());
                        output.accept(ModItems.ATM_ITEM.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
