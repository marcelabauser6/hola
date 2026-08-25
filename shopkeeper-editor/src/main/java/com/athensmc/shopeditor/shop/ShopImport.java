package com.athensmc.shopeditor.shop;

import com.athensmc.shopeditor.ShopEditor;
import com.athensmc.shopeditor.trade.TradeLine;

import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Brings the shops that already exist on the server into this mod's own store.
 *
 * <p>Read through Shopkeepers' live API, not by parsing its {@code save.yml}. The plugin is running on the same
 * server, so asking it what its shops contain gets data it has already interpreted - stack sizes, item metadata,
 * currency matching, the lot - instead of a YAML reader of mine having to agree with its serialiser on every
 * detail. Parsing the file was the obvious route and it is the one that quietly gets an edge case wrong.</p>
 *
 * <p>Run once. Afterwards the shops belong to this mod, and the plugin can be removed without them going with
 * it - which is the whole point of importing rather than reading it live for ever.</p>
 *
 * <p>Nothing is written to Shopkeepers and nothing of its data is altered. If the import goes wrong, the plugin's
 * shops are untouched and the server is exactly where it was.</p>
 */
public final class ShopImport {

    /** What an import run found, for reporting back to the admin who asked for it. */
    public record Report(int shopsFound, int shopsImported, int tradesImported, int skipped,
                         List<String> notes) {

        public boolean isEmpty() {
            return shopsFound == 0;
        }
    }

    private ShopImport() {
    }

    /** True when Shopkeepers can be reached at all. */
    public static boolean isAvailable() {
        try {
            Class.forName("com.nisovin.shopkeepers.api.ShopkeepersAPI");
            return true;
        } catch (ClassNotFoundException | RuntimeException | LinkageError absent) {
            return false;
        }
    }

    /**
     * Copies every Shopkeepers shop into this mod's store.
     *
     * @param centsPerCurrencyUnit how much Cash one of the plugin's currency units is worth, so its
     *                             item-counted prices become money.
     * @param overwrite            whether to replace a shop this mod already has. False by default so a second
     *                             run cannot undo edits made since the first.
     */
    public static Report run(ServerLevel level, long centsPerCurrencyUnit, boolean overwrite) {
        List<String> notes = new ArrayList<>();
        if (!isAvailable()) {
            notes.add("Shopkeepers no está instalado, así que no hay nada que importar.");
            return new Report(0, 0, 0, 0, notes);
        }
        if (centsPerCurrencyUnit <= 0L) {
            notes.add("El valor de la moneda tiene que ser mayor que cero.");
            return new Report(0, 0, 0, 0, notes);
        }

        ShopData store = ShopData.get(level);
        int found = 0;
        int imported = 0;
        int trades = 0;
        int skipped = 0;

        try {
            for (Object shopkeeper : allShopkeepers()) {
                found++;
                UUID villager = entityUuidOf(shopkeeper);
                if (villager == null) {
                    skipped++;
                    notes.add("Una tienda sin NPC activo se omite; entra a su mundo y repite la importación.");
                    continue;
                }
                if (store.isShop(villager) && !overwrite) {
                    skipped++;
                    continue;
                }

                List<TradeLine> lines = tradesOf(shopkeeper, centsPerCurrencyUnit, notes);
                if (lines.isEmpty()) {
                    skipped++;
                    continue;
                }
                store.put(villager, new ShopData.Shop(nameOf(shopkeeper), lines));
                imported++;
                trades += lines.size();
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            notes.add("La importación se detuvo: " + failure);
            ShopEditor.LOGGER.warn("Importación interrumpida", failure);
        }

        ShopEditor.LOGGER.info("Importadas {} tiendas de {} encontradas, con {} tratos.",
                imported, found, trades);
        return new Report(found, imported, trades, skipped, notes);
    }

    @SuppressWarnings("unchecked")
    private static Iterable<Object> allShopkeepers() throws ReflectiveOperationException {
        Class<?> api = Class.forName("com.nisovin.shopkeepers.api.ShopkeepersAPI");
        Object registry = api.getMethod("getShopkeeperRegistry").invoke(null);
        Object all = registry.getClass().getMethod("getAllShopkeepers").invoke(registry);
        return (Iterable<Object>) all;
    }

    private static String nameOf(Object shopkeeper) {
        try {
            Object name = shopkeeper.getClass().getMethod("getName").invoke(shopkeeper);
            return name == null ? "" : name.toString();
        } catch (ReflectiveOperationException | RuntimeException unnamed) {
            return "";
        }
    }

    /**
     * The UUID of the villager a shop is attached to.
     *
     * <p>Only available while the NPC is loaded, which is why an unloaded shop is reported rather than guessed
     * at - inventing a UUID would attach the trades to nothing.</p>
     */
    private static UUID entityUuidOf(Object shopkeeper) {
        try {
            Object shopObject = shopkeeper.getClass().getMethod("getShopObject").invoke(shopkeeper);
            if (shopObject == null) {
                return null;
            }
            for (Method method : shopObject.getClass().getMethods()) {
                if (method.getParameterCount() == 0 && "getEntity".equals(method.getName())) {
                    Object entity = method.invoke(shopObject);
                    if (entity == null) {
                        return null;
                    }
                    Object uuid = entity.getClass().getMethod("getUniqueId").invoke(entity);
                    return uuid instanceof UUID id ? id : null;
                }
            }
            return null;
        } catch (ReflectiveOperationException | RuntimeException noEntity) {
            return null;
        }
    }

    /**
     * Turns a shop's recipes into priced trades.
     *
     * <p>The price comes from the cost items: how many currency items the recipe asks for, times what each is
     * worth, times the Cash value of a unit. That is the same arithmetic the plugin does when a player pays, so
     * an imported price matches what the shop was charging rather than approximating it.</p>
     */
    private static List<TradeLine> tradesOf(Object shopkeeper, long centsPerUnit, List<String> notes)
            throws ReflectiveOperationException {
        List<TradeLine> lines = new ArrayList<>();
        Method getRecipes = shopkeeper.getClass().getMethod("getTradingRecipes",
                Class.forName("org.bukkit.entity.Player"));
        Object recipes = getRecipes.invoke(shopkeeper, (Object) null);
        if (!(recipes instanceof Iterable<?> iterable)) {
            return lines;
        }

        for (Object recipe : iterable) {
            try {
                Object result = recipe.getClass().getMethod("getResultItem").invoke(recipe);
                String itemId = itemIdOf(result);
                int amount = amountOf(result);
                if (itemId == null || amount <= 0) {
                    continue;
                }

                long units = currencyUnits(recipe, "getItem1") + currencyUnits(recipe, "getItem2");
                if (units <= 0L) {
                    notes.add("Un trato de '" + nameOf(shopkeeper)
                            + "' no cobraba con la moneda configurada y se omite.");
                    continue;
                }
                long price = units * centsPerUnit;
                if (price / centsPerUnit != units) {
                    notes.add("Un trato tenía un precio demasiado grande y se omite.");
                    continue;
                }
                lines.add(new TradeLine(itemId, Math.min(amount, TradeLine.MAX_AMOUNT), price));
            } catch (ReflectiveOperationException | RuntimeException bad) {
                notes.add("Un trato no se pudo leer y se omite.");
            }
        }
        return lines;
    }

    private static long currencyUnits(Object recipe, String getter) {
        try {
            Object stack = recipe.getClass().getMethod(getter).invoke(recipe);
            if (stack == null) {
                return 0L;
            }
            Class<?> currencies = Class.forName("com.nisovin.shopkeepers.currency.Currencies");
            Method match = null;
            for (Method method : currencies.getMethods()) {
                if ("match".equals(method.getName()) && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isAssignableFrom(stack.getClass())) {
                    match = method;
                    break;
                }
            }
            if (match == null) {
                return 0L;
            }
            Object currency = match.invoke(null, stack);
            if (currency == null) {
                return 0L;
            }
            int value = (int) currency.getClass().getMethod("getValue").invoke(currency);
            return (long) value * amountOf(stack);
        } catch (ReflectiveOperationException | RuntimeException notCurrency) {
            return 0L;
        }
    }

    /** The registry id of a Bukkit item stack, e.g. {@code minecraft:diamond}. */
    private static String itemIdOf(Object stack) {
        try {
            Object type = stack.getClass().getMethod("getType").invoke(stack);
            Object key = type.getClass().getMethod("getKey").invoke(type);
            return key.toString();
        } catch (ReflectiveOperationException | RuntimeException unknown) {
            return null;
        }
    }

    private static int amountOf(Object stack) {
        try {
            return (int) stack.getClass().getMethod("getAmount").invoke(stack);
        } catch (ReflectiveOperationException | RuntimeException unknown) {
            return 0;
        }
    }
}
