package com.athensmc.athenscoins.menu;

/** Implemented by menus that show live balances and can be refreshed from the server. */
public interface WalletStateHolder {
    void applyState(long cashCents, int[] coinCounts, boolean atmNearby);
}
