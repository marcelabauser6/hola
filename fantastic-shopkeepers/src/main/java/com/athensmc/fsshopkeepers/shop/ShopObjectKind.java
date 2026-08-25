package com.athensmc.fsshopkeepers.shop;

/**
 * What a shop physically is in the world.
 *
 * <p>Three answers, because there are three genuinely different ways a shop can exist. A {@link #LIVING} shop is
 * a mob standing somewhere. A {@link #SIGN} shop is a block with text on it. A {@link #VIRTUAL} shop is nowhere
 * at all and can only be reached by command, which is what makes it useful for a menu-driven server shop.</p>
 *
 * <p>The mob's species is not part of this enum. It is a registry id stored on the shop, so any entity the server
 * knows about - including one added by another mod - can be a shopkeeper without this enum growing a case.</p>
 */
public enum ShopObjectKind {

    /** A mob standing in the world, frozen and invulnerable. */
    LIVING("mob", "Mob", "Un NPC vivo que se queda quieto en el mundo."),

    /** A sign, for a shop that should not be a creature. */
    SIGN("cartel", "Cartel", "Un cartel con el nombre de la tienda escrito."),

    /** No presence in the world; reached by command only. */
    VIRTUAL("virtual", "Virtual", "Sin cuerpo. Solo se abre por comando.");

    private final String id;
    private final String title;
    private final String description;

    ShopObjectKind(String id, String title, String description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    /** True when the shop has an entity that needs spawning and keeping alive. */
    public boolean hasEntity() {
        return this == LIVING;
    }

    /** True when the shop occupies a block position in a loaded world. */
    public boolean isPlaced() {
        return this != VIRTUAL;
    }

    /** Looks up a kind by id, defaulting to a mob because that is what almost every shop is. */
    public static ShopObjectKind byId(String id) {
        if (id != null) {
            for (ShopObjectKind kind : values()) {
                if (kind.id.equalsIgnoreCase(id)) {
                    return kind;
                }
            }
        }
        return LIVING;
    }
}
