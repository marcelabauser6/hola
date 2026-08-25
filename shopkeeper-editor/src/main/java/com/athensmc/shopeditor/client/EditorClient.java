package com.athensmc.shopeditor.client;

import com.athensmc.shopeditor.net.OpenEditorMessage;

/**
 * The client half's entry point.
 *
 * <p>Holds the state the server sent and opens the editor on it. Kept separate from the packet class so nothing
 * that touches a screen is reachable from code the dedicated server loads - a screen class on a server is a
 * crash at class-load time, not a caught error.</p>
 */
public final class EditorClient {

    private static OpenEditorMessage current;

    private EditorClient() {
    }

    /** The shop currently being edited, for the screens to read. */
    public static OpenEditorMessage current() {
        return current;
    }

    /**
     * Opens the editor for a shop the server just sent.
     *
     * <p>The screens are not wired in yet; this records the state so that the moment they are, the whole path
     * from command to screen is already exercised.</p>
     */
    public static void open(OpenEditorMessage message) {
        current = message;
    }
}
