package com.athensmc.athenscoins.block;

import com.athensmc.athenscoins.AthensCoinsMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, AthensCoinsMod.MOD_ID);

    public static final RegistryObject<Block> ATM = BLOCKS.register("atm", () -> new AtmBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    /** Bank terminal. No recipe, creative only, and placing it requires an operator. */
    public static final RegistryObject<Block> BANK_TERMINAL = BLOCKS.register("bank_terminal",
            () -> new BankTerminalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(4.0F, 8.0F)
                    .sound(SoundType.WOOD)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    /** Central bank. Operator only at every step. */
    public static final RegistryObject<Block> CENTRAL_BANK_TERMINAL =
            BLOCKS.register("central_bank_terminal", () -> new CentralBankTerminalBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLUE)
                            .strength(5.0F, 12.0F)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
