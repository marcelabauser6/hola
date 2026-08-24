package com.athensmc.fantasticregions;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Fantastic Regions: the Yet Another World Protector command tree, replaced by an interface.
 *
 * <p>YAWP already stores regions, evaluates flags and draws region outlines with display entities.
 * None of that is reimplemented here. What this mod does is remove the ~40 subcommands players had
 * to memorise and put the same operations behind screens, so marking out a region is done by
 * clicking two corners with a rod and watching the outline follow the selection.</p>
 *
 * <p><strong>Licence.</strong> This mod calls into YAWP, which is AGPL v3, so this mod is AGPL v3
 * as well. Anyone running it on a public server is on the hook for AGPL section 13: players who
 * interact with it over the network can ask for the source, and they have to be able to get it.</p>
 *
 * <p><strong>Sides.</strong> YAWP describes itself as server-side, and it is - it never needed a
 * client. An interface does, so this mod is installed on both sides and the two halves talk over a
 * private channel. The server stays the only authority on what a region actually is; the client is
 * shown a copy and asks for edits.</p>
 */
@Mod(FantasticRegions.MOD_ID)
public final class FantasticRegions {
    public static final String MOD_ID = "fantasticregions";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FantasticRegions() {
    }
}
