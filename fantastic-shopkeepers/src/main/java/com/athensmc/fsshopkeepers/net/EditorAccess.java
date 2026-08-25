package com.athensmc.fsshopkeepers.net;

import com.athensmc.fsshopkeepers.FantasticShopkeepers;
import com.athensmc.fsshopkeepers.shop.Shopkeeper;

import net.minecraft.server.level.ServerPlayer;

/**
 * Who may change a shop.
 *
 * <p>One place, asked by every path that writes to a shop. The editor screen is on the client and a client can send
 * whatever it likes, so the question has to be answered again on arrival - the fact that the server opened the editor
 * for someone earlier is not proof that the packet coming back is from them or about the same shop.</p>
 */
public final class EditorAccess {

    private EditorAccess() {
    }

    /**
     * Whether a player may edit or delete a shop.
     *
     * <p>An owner may always edit their own shop. Anyone else needs staff permission, including for admin shops,
     * which have no owner and so would otherwise be editable by nobody.</p>
     */
    public static boolean mayEdit(ServerPlayer player, Shopkeeper shop) {
        if (player == null || shop == null) {
            return false;
        }
        if (shop.isOwner(player.getUUID())
                && FantasticShopkeepers.hasPermission(player, FantasticShopkeepers.Perms.EDIT_OWN)) {
            return true;
        }
        return FantasticShopkeepers.hasPermission(player, FantasticShopkeepers.Perms.EDIT_OTHERS);
    }

    /**
     * Whether a player may change the parts of a shop only staff should touch.
     *
     * <p>The owner of a shop may set its prices and its stock, but not its trade permission or whether it is an admin
     * shop. Letting an owner edit those would let any player who can make a shop grant themselves the effects of
     * one an administrator built.</p>
     */
    public static boolean mayEditStaffFields(ServerPlayer player) {
        return FantasticShopkeepers.hasPermission(player, FantasticShopkeepers.Perms.EDIT_OTHERS);
    }
}
