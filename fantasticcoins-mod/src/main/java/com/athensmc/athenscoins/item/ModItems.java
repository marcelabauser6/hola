package com.athensmc.athenscoins.item;

import com.athensmc.athenscoins.AthensCoinsMod;
import com.athensmc.athenscoins.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Item registry for FantasticCoins.
 *
 * <p>The three physical ("cash") coins are unchanged from the original mod so existing
 * inventories, shops and quest rewards keep working: {@code athens_coins:bronze_coin},
 * {@code athens_coins:silver_coin} and {@code athens_coins:gold_coin}.</p>
 */
public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, AthensCoinsMod.MOD_ID);

    public static final RegistryObject<Item> BRONZE_COIN =
            ITEMS.register("bronze_coin", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> SILVER_COIN =
            ITEMS.register("silver_coin", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> GOLD_COIN =
            ITEMS.register("gold_coin", () -> new Item(new Item.Properties()));

    /** Block item for the bank ATM. */
    public static final RegistryObject<Item> ATM_ITEM =
            ITEMS.register("atm", () -> new BlockItem(ModBlocks.ATM.get(), new Item.Properties()));

    /** Block item for the bank terminal. Operator-gated in OperatorOnlyPlacement. */
    public static final RegistryObject<Item> BANK_TERMINAL_ITEM = ITEMS.register("bank_terminal",
            () -> new BlockItem(ModBlocks.BANK_TERMINAL.get(), new Item.Properties()));

    /** Bearer card holding a closed account's balance, used to move between banks. */
    public static final RegistryObject<Item> BANK_CARD = ITEMS.register("bank_card",
            () -> new BankCardItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> CENTRAL_BANK_TERMINAL_ITEM =
            ITEMS.register("central_bank_terminal", () -> new BlockItem(
                    ModBlocks.CENTRAL_BANK_TERMINAL.get(), new Item.Properties()));

    /** Stats hologram projector. Operator-gated in OperatorOnlyPlacement. */
    public static final RegistryObject<Item> STATS_HOLOGRAM_ITEM = ITEMS.register("stats_hologram",
            () -> new BlockItem(ModBlocks.STATS_HOLOGRAM.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
