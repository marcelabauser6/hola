package com.athensmc.fantasticregions.flag;

/**
 * The tab a protection flag is filed under in the interface.
 *
 * <p>YAWP has 93 flags and its own tagging is aimed at command completion, not at reading: the tags
 * overlap, so the same flag answers to several of them and a list built from tags shows duplicates.
 * These groups partition the flags exactly once each, which is what a tab strip needs - the count on
 * a tab is then the truth about how many rows are behind it.</p>
 *
 * <p>Deliberately free of any Minecraft import: the catalogue guard runs on a plain JVM.</p>
 */
public enum FlagGroup {
    BLOCKS("bloques", "Bloques"),
    ITEMS("objetos", "Objetos"),
    PLAYERS("jugadores", "Jugadores"),
    CREATURES("criaturas", "Criaturas"),
    ENVIRONMENT("entorno", "Entorno"),
    TRAVEL("viaje", "Viaje");

    private final String id;
    private final String label;

    FlagGroup(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    /** Short Spanish name, sized to sit on a tab without being cut. */
    public String label() {
        return label;
    }
}
