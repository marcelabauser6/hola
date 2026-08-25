package com.athensmc.athenscoins.bank;

import net.minecraft.server.level.ServerPlayer;

/**
 * Who is allowed to do what, in one place.
 *
 * <p>The mod had exactly one answer to every question of authority: {@code hasPermissions(2)}. That
 * means a server whose bank staff are not operators cannot delegate anything - to let somebody run a
 * bank you had to hand them the whole server. It also meant the checks were scattered across three
 * blocks and six packet handlers, each spelling out its own version, which is how the central bank
 * ended up strictly operator-only while the bank terminal quietly created a bank for anyone who
 * right-clicked it before checking whether they were allowed to be there.</p>
 *
 * <p>Three tiers, and nothing else:</p>
 * <ul>
 *   <li><b>Operators</b> — everything, including licensing founders.</li>
 *   <li><b>Founders</b> — licensed by the central bank. May place and open bank terminals, and open the
 *       central bank. This is the tier that makes staff possible without giving out op.</li>
 *   <li><b>Bankers</b> — appointed by a bank, for that bank only. May open <em>their</em> terminal and
 *       serve customers; may not change the bank's terms and cannot touch another bank.</li>
 * </ul>
 *
 * <p>Everyone else is a customer: the ATM, their wallet, and the loan application they file at it.</p>
 *
 * <p>Every method takes the player, not a UUID, because the operator check needs the player object and
 * splitting the two tiers across two call styles is how one of them gets forgotten.</p>
 */
public final class BankAccess {

    private BankAccess() {
    }

    /** Operators only. Licensing a founder is the one thing a founder cannot do. */
    public static boolean isOperator(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    /**
     * May create banks: place a bank terminal, and open the central bank.
     *
     * <p>Deliberately the same test for both. The central bank is where founders are licensed and rates
     * are set, so anyone who can found a bank can see the board they are competing on.</p>
     */
    public static boolean canFoundBanks(ServerPlayer player) {
        return isOperator(player) || BankData.get(player.server).isFounder(player.getUUID());
    }

    /**
     * May open this bank's terminal: staff of this bank, or anyone who could have founded it.
     *
     * <p>A banker of one bank gets nothing at another bank's terminal - the appointment is per bank, and
     * a shared customer register is not a shared staff list.</p>
     */
    public static boolean canOpenTerminal(ServerPlayer player, Bank bank) {
        return canFoundBanks(player) || bank.isBanker(player.getUUID());
    }

    /**
     * May change a bank's terms: its name, colour, fees, rates and lending policy.
     *
     * <p>Not bankers. A banker serves customers; the terms of the accounts they open are the owner's
     * decision, and letting a teller move the fee would let them change a contract customers have
     * already signed up to.</p>
     */
    public static boolean canConfigure(ServerPlayer player) {
        return canFoundBanks(player);
    }
}
