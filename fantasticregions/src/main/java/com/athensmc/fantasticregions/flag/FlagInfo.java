package com.athensmc.fantasticregions.flag;

/**
 * One protection flag, as the interface needs to present it.
 *
 * @param id      YAWP's own flag identifier, e.g. {@code break-blocks}. This is the string that goes
 *                to {@code IProtectedRegion.getFlag}/{@code containsFlag}, so it must match YAWP
 *                exactly - a typo here is a control that silently edits nothing.
 * @param label   Short Spanish name for the row. Kept inside a character budget so it is never
 *                clipped or ellipsised, which the layout guard enforces.
 * @param help    What the flag actually does, in one or two plain sentences, shown next to the row.
 *                Written to answer "what happens if I turn this on", because the flag names alone are
 *                ambiguous in both languages - {@code no-pvp} and {@code melee-players} sound like the
 *                same setting and are not.
 * @param group   Which tab the row belongs to.
 */
public record FlagInfo(String id, String label, String help, FlagGroup group) {

    /** Longest a label may be. Anything over this is a build failure, not a truncated row. */
    public static final int MAX_LABEL_CHARS = 26;

    /** Longest an explanation may be, so it wraps into the help box instead of overflowing it. */
    public static final int MAX_HELP_CHARS = 160;

    public FlagInfo {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("flag id must not be blank");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("flag " + id + " has no label");
        }
        if (help == null || help.isBlank()) {
            throw new IllegalArgumentException("flag " + id + " has no explanation");
        }
        if (group == null) {
            throw new IllegalArgumentException("flag " + id + " has no group");
        }
    }
}
