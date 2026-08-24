package com.athensmc.fantasticregions;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * A wand for preselecting the area of a Yet Another World Protector region.
 *
 * <p>{@code /yawp wand <forma>} hands over a blaze rod set to one of YAWP's five area types. Clicking
 * blocks with it collects the corners, and the perimeter is drawn in the world as they are collected,
 * so the extent is visible before the region exists. The region is then created with YAWP's own create
 * command, which picks the preselection straight off the rod.</p>
 *
 * <p><strong>Nothing of YAWP's is modified, replaced or removed.</strong> The rod carries the marker
 * data YAWP already defines, written with YAWP's own utility class, so its interaction mixin registers
 * the clicks and its create command accepts the result. This mod adds one command to the tree and one
 * outline to the world.</p>
 *
 * <p><strong>Server side only.</strong> There is no custom item, no custom packet and no rendering
 * code - the outline is particles, addressed to the player holding the rod. Nothing needs to be
 * installed on anyone's client.</p>
 *
 * <p><strong>Licence.</strong> This mod compiles against YAWP, which is AGPL v3, so it is AGPL v3 as
 * well. Running it on a public server brings AGPL section 13 with it: players who interact with it over
 * the network can ask for the source and have to be able to get it. YAWP's own jar is not bundled - it
 * stays the untouched file it was downloaded as.</p>
 */
@Mod(FantasticRegions.MOD_ID)
public final class FantasticRegions {
    public static final String MOD_ID = "fantasticregions";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FantasticRegions() {
    }
}
