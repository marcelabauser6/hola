package com.athensmc.athenscoins.block;

import com.athensmc.athenscoins.AthensCoinsMod;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AthensCoinsMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<AtmBlockEntity>> ATM =
            BLOCK_ENTITIES.register("atm", () -> BlockEntityType.Builder
                    .of(AtmBlockEntity::new, ModBlocks.ATM.get())
                    .build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
