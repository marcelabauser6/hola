package com.athensmc.athenscoins.menu;

/** Implemented by menus that display live wallet/cash figures and can be refreshed. */
public interface WalletStateHolder {
    void applyState(long[] digital, int[] cash, boolean atmNearby);
}
