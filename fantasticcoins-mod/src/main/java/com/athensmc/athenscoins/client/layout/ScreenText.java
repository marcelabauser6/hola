package com.athensmc.athenscoins.client.layout;

import net.minecraft.client.gui.Font;

/** Shared text fitting helpers for dynamic names, translations and monetary values. */
public final class ScreenText {
    private static final String ELLIPSIS = "…";

    private ScreenText() {
    }

    public static String fit(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        int bodyWidth = Math.max(0, maxWidth - font.width(ELLIPSIS));
        return font.plainSubstrByWidth(text, bodyWidth) + ELLIPSIS;
    }

    public static boolean wasTruncated(Font font, String text, int maxWidth) {
        return font.width(text) > Math.max(0, maxWidth);
    }
}
