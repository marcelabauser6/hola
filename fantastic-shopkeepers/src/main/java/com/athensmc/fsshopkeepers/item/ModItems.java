package com.athensmc.fsshopkeepers.item;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;
import com.athensmc.fsshopkeepers.money.Cash;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's items.
 *
 * <p>Exactly one, and it exists only to be looked at. Fantastic Cash is a bank balance, not an object, so the vanilla
 * trading window has nothing to put in its payment slot where a villager would show emeralds. Registering a note gives
 * that slot something to draw, which is what makes the window read the way the original plugin's did.</p>
 *
 * <p>Deliberately not in any creative tab and not craftable. A player who obtains one by command has a picture of money,
 * not money: nothing in this mod accepts it as payment, because payment is taken from the balance.</p>
 */
public final class ModItems {

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FantasticShopkeepers.MOD_ID);

    /** The banknote shown in a trade's payment slot to stand for a Cash price. */
    public static final RegistryObject<Item> CASH_NOTE = ITEMS.register("cash_note",
            () -> new Item(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    /**
     * A note stack standing for a price.
     *
     * <p>The formatted amount becomes the stack's name, so hovering it reads "23,00 $" in full. The count is left at one
     * and the visible number is drawn separately by the screen, because a price of 1250.50 is not a stack size and
     * writing it as one would render as a four-digit smear across a 16-pixel slot.</p>
     */
    public static ItemStack noteFor(long priceCents) {
        ItemStack note = new ItemStack(CASH_NOTE.get());
        note.setHoverName(Component.literal(Cash.format(priceCents))
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        return note;
    }

    /**
     * The price as a short string that fits in the corner of a slot.
     *
     * <p>Whole amounts lose their ".00", because "23" is what a player expects to see on a price tag and the decimals
     * only matter when they are not zero. Past four digits it switches to thousands, since five digits do not fit.</p>
     */
    public static String shortAmount(long priceCents) {
        long units = priceCents / 100L;
        long cents = priceCents % 100L;
        if (units >= 1_000_000L) {
            return units / 1_000_000L + "M";
        }
        if (units >= 10_000L) {
            return units / 1_000L + "k";
        }
        if (cents == 0L) {
            return String.valueOf(units);
        }
        if (units >= 100L) {
            // No room for decimals as well as three digits, so the decimals go.
            return String.valueOf(units);
        }
        return units + "." + String.format("%02d", cents);
    }
}
