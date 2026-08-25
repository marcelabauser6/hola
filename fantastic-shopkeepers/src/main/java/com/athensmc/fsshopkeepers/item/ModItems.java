package com.athensmc.fsshopkeepers.item;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;
import com.athensmc.fsshopkeepers.money.Cash;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.TextColor;
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

    /**
     * The wand that opens a shop's editor.
     *
     * <p>A held item rather than a gesture. The editor used to open on a sneaking right-click, which is the same gesture
     * Easy Villagers uses to pick a villager up as an item - and that mod acts on the client, before the server ever hears
     * about the click, so the shopkeeper was carried off instead of configured. A wand cannot collide with anything,
     * because nothing else looks for it.</p>
     */
    public static final RegistryObject<Item> SHOP_WAND = ITEMS.register("shop_wand",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));

    private ModItems() {
    }

    /** True when this stack is the editor wand. */
    public static boolean isWand(ItemStack stack) {
        return !stack.isEmpty() && stack.is(SHOP_WAND.get());
    }

    /** A wand to hand to an administrator, named and with a line saying what it does. */
    public static ItemStack wand() {
        ItemStack wand = new ItemStack(SHOP_WAND.get());
        wand.setHoverName(Component.literal("Varita del editor")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        return wand;
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
        return noteFor(priceCents, "Fantastic Cash", 0x55FF55);
    }

    /**
     * A note stack standing for a price, named and coloured as the server chose.
     *
     * <p>The name is the currency, not the amount, and the amount goes on a line under it. Naming it after the amount made
     * the tooltip read "23,00" with no clue what twenty-three of anything was.</p>
     *
     * <p>Italics are switched off explicitly. Minecraft italicises any renamed item, which made the money look like a
     * placeholder rather than a label.</p>
     */
    public static ItemStack noteFor(long priceCents, String label, int colourRgb) {
        ItemStack note = new ItemStack(CASH_NOTE.get());
        note.setHoverName(Component.literal(label == null || label.isBlank() ? "Fantastic Cash" : label)
                .withStyle(style -> style.withColor(TextColor.fromRgb(colourRgb)).withItalic(false)));
        setLore(note, Component.literal(Cash.format(priceCents))
                .withStyle(style -> style.withColor(TextColor.fromRgb(colourRgb)).withItalic(false)));
        return note;
    }

    /** Writes a single line of lore, the way vanilla stores it. */
    private static void setLore(ItemStack stack, Component line) {
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(line)));
        stack.getOrCreateTagElement("display").put("Lore", lore);
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
