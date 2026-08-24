package com.athensmc.athenscoins.transfer;

import java.util.UUID;

/**
 * A transfer waiting for the recipient to accept or reject it.
 *
 * <p>No money is held in escrow: the sender's balance is checked when the request is made and
 * again when it is accepted. That keeps funds usable while a request is pending and means a
 * crash can never leave cash stranded.</p>
 */
public record PendingTransfer(int id,
                              UUID sender,
                              String senderName,
                              UUID target,
                              String targetName,
                              long cents,
                              long expiresAt) {

    public boolean isExpired(long now) {
        return now >= expiresAt;
    }

    public long secondsLeft(long now) {
        return Math.max(0L, (expiresAt - now) / 1000L);
    }
}
