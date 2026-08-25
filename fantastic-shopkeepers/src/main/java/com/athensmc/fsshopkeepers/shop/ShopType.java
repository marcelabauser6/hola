package com.athensmc.fsshopkeepers.shop;

/**
 * What kind of shop an NPC runs.
 *
 * <p>The distinction that matters is where the goods come from. An {@link #ADMIN} shop invents them, so it never
 * runs out and never fills up. Every other kind is backed by a container the owning player stocks, so what it can
 * do is limited by what is in that chest at the moment a buyer clicks.</p>
 *
 * <p>Kept as an enum with its rules attached, rather than as a class hierarchy, because the differences between
 * the kinds are a handful of yes/no answers - does it need an owner, does it need a chest, does it take money in
 * or pay money out - and each of those is asked in one place.</p>
 */
public enum ShopType {

    /**
     * An unlimited shop with no owner, run by staff.
     *
     * <p>Sells from nothing and buys into nothing, so the trades an admin sets are exactly what players see for
     * as long as the shop exists.</p>
     */
    ADMIN("admin", "Tienda de administrador",
            "Existencias infinitas. No necesita cofre ni dueño.",
            false, false, Direction.SELLS),

    /**
     * A player shop that sells the contents of its chest for money.
     *
     * <p>The most common kind. Stock is whatever is in the chest; when the chest runs out the trade greys out
     * rather than the shop selling items it does not have.</p>
     */
    PLAYER_SELL("venta", "Tienda de venta",
            "Vende lo que hay en el cofre y te paga en Cash.",
            true, true, Direction.SELLS),

    /**
     * A player shop that buys items from other players, paying out of the owner's balance.
     *
     * <p>Bought items go into the chest, so the chest having room is as much a limit as having stock is for a
     * selling shop, and the owner's balance is a third limit on top.</p>
     */
    PLAYER_BUY("compra", "Tienda de compra",
            "Compra articulos a otros jugadores y los guarda en el cofre.",
            true, true, Direction.BUYS),

    /**
     * A player shop that barters items for items.
     *
     * <p>No money involved on either side: the buyer hands over items, the chest hands back items, and the chest
     * has to have room for what comes in as well as stock for what goes out.</p>
     */
    PLAYER_TRADE("trueque", "Tienda de trueque",
            "Intercambia articulos por articulos usando el cofre.",
            true, true, Direction.BARTERS),

    /**
     * A player shop that sells copies of written books.
     *
     * <p>Its stock is the original book in the chest, which is copied rather than consumed, so one book can be
     * sold many times. That is the whole reason it is its own kind.</p>
     */
    PLAYER_BOOK("libros", "Tienda de libros",
            "Vende copias de los libros escritos que haya en el cofre.",
            true, true, Direction.SELLS);

    /** Which way goods and money move, which decides what has to be checked before a trade. */
    public enum Direction {
        /** Buyer pays, shop hands out goods. */
        SELLS,
        /** Buyer hands in goods, shop pays. */
        BUYS,
        /** Items both ways, no money. */
        BARTERS
    }

    private final String id;
    private final String title;
    private final String description;
    private final boolean needsOwner;
    private final boolean needsContainer;
    private final Direction direction;

    ShopType(String id, String title, String description, boolean needsOwner, boolean needsContainer,
            Direction direction) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.needsOwner = needsOwner;
        this.needsContainer = needsContainer;
        this.direction = direction;
    }

    /** The stable id written to the world and accepted by commands. */
    public String id() {
        return id;
    }

    /** The name shown in the editor. */
    public String title() {
        return title;
    }

    /** One line explaining the kind, shown under its name so the choice needs no wiki. */
    public String description() {
        return description;
    }

    /** True when the shop belongs to a player rather than to staff. */
    public boolean isPlayerShop() {
        return needsOwner;
    }

    /** True when the shop draws its stock from a container. */
    public boolean needsContainer() {
        return needsContainer;
    }

    public Direction direction() {
        return direction;
    }

    /** True when the shop hands goods to the buyer and expects payment. */
    public boolean sellsToBuyer() {
        return direction == Direction.SELLS;
    }

    /** True when the shop pays the buyer for goods handed in. */
    public boolean buysFromBuyer() {
        return direction == Direction.BUYS;
    }

    /**
     * Looks up a kind by its written id.
     *
     * <p>Falls back to {@link #ADMIN} for an unknown id so that a shop saved by a newer version does not
     * disappear from an older one, though it will behave as an admin shop until upgraded.</p>
     */
    public static ShopType byId(String id) {
        if (id != null) {
            for (ShopType type : values()) {
                if (type.id.equalsIgnoreCase(id)) {
                    return type;
                }
            }
        }
        return ADMIN;
    }
}
