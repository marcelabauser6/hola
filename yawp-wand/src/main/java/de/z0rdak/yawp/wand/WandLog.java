package de.z0rdak.yawp.wand;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * The wand's logger.
 *
 * <p>Its own rather than YAWP's {@code Constants.LOGGER}, so a line from the wand is attributable to the
 * wand. The code lives inside YAWP's jar and namespace, but a message about a command failing to register
 * is more useful when it does not look like it came from the region store.</p>
 */
public final class WandLog {

    public static final Logger LOGGER = LogUtils.getLogger();

    private WandLog() {
    }
}
