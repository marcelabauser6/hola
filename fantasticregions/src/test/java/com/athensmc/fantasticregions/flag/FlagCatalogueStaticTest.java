package com.athensmc.fantasticregions.flag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks the flag catalogue against the set of flags YAWP actually has.
 *
 * <p>The expected list is not a transcription of YAWP's documentation. It was read out of the
 * {@code RegionFlag} enum in the shipped jar with {@code tools/extract_flags.sh}, which is how a flag
 * like {@code melee-wtrader} - abbreviated in the code and spelled out everywhere else - ends up
 * correct. A flag missing from the catalogue is a row the interface cannot show; a flag in the
 * catalogue that YAWP does not have is a control that appears to work and edits nothing.</p>
 *
 * <p>Runs on a plain JVM. It cannot load {@code RegionFlag} itself, because that enum builds
 * {@code ResourceLocation} tags in its static initialiser and would drag the whole client in, so the
 * comparison is against the extracted list. When YAWP is updated, re-run the extractor.</p>
 */
public final class FlagCatalogueStaticTest {

    /** The 93 flag identifiers in yawp-1.20.1mohistfix.jar (YAWP 0.6.3-beta1). */
    private static final String[] YAWP_FLAG_IDS = {
            "access-container", "access-enderchest", "animal-breeding", "animal-mounting",
            "animal-taming", "animal-unmounting", "break-blocks", "creeper-explosion-blocks",
            "creeper-explosion-entities", "dragon-destruction", "drop-loot", "drop-loot-player",
            "drop-xp", "enderman-griefing", "enderman-tp-from", "enderpearl-from", "enderpearl-to",
            "enter-dim", "exec-command", "explosions-blocks", "explosions-entities", "fall-damage",
            "fall-damage-animals", "fall-damage-monsters", "fall-damage-players",
            "fall-damage-villagers", "fire-bow", "fire-tick", "fluid-flow", "ignite-explosives",
            "invincible", "item-drop", "item-pickup", "keep-inv", "keep-xp", "lava-flow",
            "leaf-decay", "level-freeze", "lightning", "melee-animals", "melee-monsters",
            "melee-players", "melee-villagers", "melee-wtrader", "mob-griefing", "no-flight",
            "no-hunger", "no-item-despawn", "no-knockback", "no-pvp", "no-sign-edit",
            "place-blocks", "place-fluids", "scoop-fluids", "send-chat", "set-spawn",
            "shovel-path", "shulker-tp-from", "sleep", "snow-fall", "snow-melting",
            "spawn-portal", "spawning-all", "spawning-animal", "spawning-golem",
            "spawning-monster", "spawning-slime", "spawning-trader", "spawning-villager",
            "spawning-xp", "strip-wood", "till-farmland", "tools-secondary",
            "trample-farmland", "trample-farmland-player", "use-blocks", "use-bonemeal",
            "use-elytra", "use-entities", "use-items", "use-portal", "use-portal-animals",
            "use-portal-items", "use-portal-minecarts", "use-portal-monsters",
            "use-portal-players", "use-portal-villagers", "walker-freeze", "water-flow",
            "wither-destruction", "xp-freeze", "xp-pickup", "zombie-destruction",
    };

    private static final List<String> FAILURES = new ArrayList<>();

    private FlagCatalogueStaticTest() {
    }

    public static void main(String[] args) {
        checkEveryYawpFlagIsPresent();
        checkNoInventedFlags();
        checkLabelsFit();
        checkExplanationsFit();
        checkLabelsAreUnique();
        checkGroupsArePartition();

        if (!FAILURES.isEmpty()) {
            System.err.println("Flag catalogue check failed:");
            FAILURES.forEach(failure -> System.err.println("  - " + failure));
            System.exit(1);
        }
        System.out.println("Flag catalogue OK: " + FlagCatalogue.size()
                + " flags, all named, explained and filed.");
    }

    private static void checkEveryYawpFlagIsPresent() {
        for (String id : YAWP_FLAG_IDS) {
            if (!FlagCatalogue.contains(id)) {
                FAILURES.add("YAWP flag '" + id + "' is missing from the catalogue");
            }
        }
    }

    private static void checkNoInventedFlags() {
        Set<String> known = new LinkedHashSet<>(List.of(YAWP_FLAG_IDS));
        for (FlagInfo flag : FlagCatalogue.all()) {
            if (!known.contains(flag.id())) {
                FAILURES.add("catalogue has '" + flag.id() + "', which YAWP does not define");
            }
        }
        if (FlagCatalogue.size() != YAWP_FLAG_IDS.length) {
            FAILURES.add("catalogue has " + FlagCatalogue.size() + " flags, YAWP has "
                    + YAWP_FLAG_IDS.length);
        }
    }

    private static void checkLabelsFit() {
        for (FlagInfo flag : FlagCatalogue.all()) {
            int length = flag.label().length();
            if (length > FlagInfo.MAX_LABEL_CHARS) {
                FAILURES.add("label for '" + flag.id() + "' is " + length + " chars, over the "
                        + FlagInfo.MAX_LABEL_CHARS + " budget: " + flag.label());
            }
            if (flag.label().contains("...") || flag.label().contains("…")) {
                FAILURES.add("label for '" + flag.id() + "' is elided: " + flag.label());
            }
        }
    }

    private static void checkExplanationsFit() {
        for (FlagInfo flag : FlagCatalogue.all()) {
            int length = flag.help().length();
            if (length > FlagInfo.MAX_HELP_CHARS) {
                FAILURES.add("explanation for '" + flag.id() + "' is " + length + " chars, over the "
                        + FlagInfo.MAX_HELP_CHARS + " budget");
            }
            if (flag.help().contains("...") || flag.help().contains("…")) {
                FAILURES.add("explanation for '" + flag.id() + "' is elided");
            }
            if (!flag.help().endsWith(".")) {
                FAILURES.add("explanation for '" + flag.id() + "' is not a finished sentence");
            }
        }
    }

    /**
     * Two rows reading the same thing is a bug even when the identifiers differ.
     *
     * <p>The families of near-identical flags are exactly where it would happen: {@code fall-damage}
     * has five variants and {@code use-portal} seven, and giving two of them the same short label
     * makes the pair impossible to tell apart in the list.</p>
     */
    private static void checkLabelsAreUnique() {
        Set<String> seen = new LinkedHashSet<>();
        for (FlagInfo flag : FlagCatalogue.all()) {
            if (!seen.add(flag.label())) {
                FAILURES.add("two flags share the label '" + flag.label() + "'");
            }
        }
    }

    private static void checkGroupsArePartition() {
        int total = 0;
        for (FlagGroup group : FlagGroup.values()) {
            int size = FlagCatalogue.group(group).size();
            if (size == 0) {
                FAILURES.add("tab '" + group.label() + "' has no flags");
            }
            total += size;
        }
        if (total != FlagCatalogue.size()) {
            FAILURES.add("groups hold " + total + " flags but the catalogue has "
                    + FlagCatalogue.size());
        }
    }
}
