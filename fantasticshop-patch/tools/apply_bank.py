#!/usr/bin/env python3
"""
Ties a player shop to a bank account.

Design note: the account number is taken from the owner's actual account rather than typed in.
Letting a player type any five digit number would let them point their shop at somebody else's
account, and the shop has no way to prove the number is theirs. Reading it from the bank gives the
same result, cannot be spoofed, and picks up the new number automatically after the owner moves
banks with a card.
"""
import pathlib
import sys

API = "com.athensmc.athenscoins.api.FantasticCurrencyAPI"


def patch(path, pairs):
    file = pathlib.Path(path)
    text = file.read_text(encoding="utf-8")
    for old, new in pairs:
        if old not in text:
            print(f"  !! pattern not found in {path}:\n     {old[:110]}")
            sys.exit(1)
        text = text.replace(old, new, 1)
    file.write_text(text, encoding="utf-8")
    print(f"  patched {path}")


# ------------------------------------------------------------------ PlayerShop: account field
patch("patchsrc/com/fshop/shop/PlayerShop.java", [
    ("private final long[] pendingEarnings = new long[4];",
     "private final long[] pendingEarnings = new long[4];\n"
     "    /** Bank account this shop settles into. 0 means the shop cannot sell. */\n"
     "    private int accountNumber;"),
    ("    public long getPendingEarnings(int coin) {",
     """    public int getAccountNumber() {
        return this.accountNumber;
    }

    public void setAccountNumber(int number) {
        this.accountNumber = Math.max(0, number);
    }

    /** A shop with no account is frozen: it can be browsed but nothing can be bought or listed. */
    public boolean canSell() {
        return this.accountNumber > 0;
    }

    public long getPendingEarnings(int coin) {"""),
    ('tag.m_128356_("earningsCash", this.pendingEarnings[3]);',
     'tag.m_128356_("earningsCash", this.pendingEarnings[3]);\n'
     '        tag.m_128405_("account", this.accountNumber);'),
    ('        shop.pendingEarnings[3] = tag.m_128454_("earningsCash");',
     '        shop.pendingEarnings[3] = tag.m_128454_("earningsCash");\n'
     '        shop.accountNumber = tag.m_128451_("account");'),
    ("        buf.m_130103_(this.pendingEarnings[3]);",
     "        buf.m_130103_(this.pendingEarnings[3]);\n"
     "        buf.m_130130_(this.accountNumber);"),
    ("        shop.pendingEarnings[3] = buf.m_130258_();",
     "        shop.pendingEarnings[3] = buf.m_130258_();\n"
     "        shop.accountNumber = buf.m_130242_();"),
])

# ------------------------------------------------------------------ ShopService: the gate
patch("patchsrc/com/fshop/shop/ShopService.java", [
    ("        OUT_OF_STOCK,", "        NO_BANK_ACCOUNT,\n        OUT_OF_STOCK,"),
    # buying from a frozen shop is refused: there is nowhere for the money to land
    ("""        ShopOffer offer = shop.getOffers().get(offerIndex);
        int items = offer.getBundle() * amount;""",
     """        if (!ShopService.sellerBanked(buyer, shop)) {
            return Result.NO_BANK_ACCOUNT;
        }
        ShopOffer offer = shop.getOffers().get(offerIndex);
        int items = offer.getBundle() * amount;"""),
    # listing stock requires the owner to be banked
    ("""        if (!shop.getOwner().equals(owner.m_20148_())) {
            return Result.NOT_OWNER;
        }
        Inventory inv = owner.m_150109_();""",
     """        if (!shop.getOwner().equals(owner.m_20148_())) {
            return Result.NOT_OWNER;
        }
        if (!ShopService.refreshAccount(owner, shop)) {
            return Result.NO_BANK_ACCOUNT;
        }
        Inventory inv = owner.m_150109_();"""),
    ("    private static boolean isCoin(ItemStack s) {",
     """    /**
     * Re-reads the owner's account number onto the shop.
     *
     * <p>Called before listing so a shop picks up a new number by itself after its owner moves
     * banks, and drops to frozen if they no longer have an account at all.</p>
     */
    private static boolean refreshAccount(ServerPlayer owner, PlayerShop shop) {
        if (shop.isMain()) {
            return true;        // the server shop is not anybody's account
        }
        int number = """ + API + """.accountNumber(owner.f_8924_, owner.m_20148_());
        shop.setAccountNumber(number);
        return number > 0;
    }

    /** True when the shop can accept money: the server shop always can. */
    private static boolean sellerBanked(ServerPlayer buyer, PlayerShop shop) {
        if (shop.isMain()) {
            return true;
        }
        return shop.canSell() && """ + API + """.ownsAccount(
                buyer.f_8924_, shop.getOwner(), shop.getAccountNumber());
    }

    private static boolean isCoin(ItemStack s) {"""),
])

# ------------------------------------------------------------------ creation asks for the account
patch("patchsrc/com/fshop/command/FShopCommands.java", [
    ("""        PlayerShop shop = new PlayerShop(UUID.randomUUID(), player.m_20148_(), player.m_36316_().getName(), name);
        data.putShop(shop);""",
     """        int account = """ + API + """.accountNumber(player.f_8924_, player.m_20148_());
        if (account <= 0) {
            FShopCommands.msg(player, "Necesitas una cuenta bancaria para vender. "
                    + "Pide a un banquero que te aperture una y vuelve a crear la tienda.",
                    ChatFormatting.RED);
            return 0;
        }
        PlayerShop shop = new PlayerShop(UUID.randomUUID(), player.m_20148_(), player.m_36316_().getName(), name);
        shop.setAccountNumber(account);
        data.putShop(shop);
        FShopCommands.msg(player, "Tienda vinculada a la cuenta #" + account + " de "
                + """ + API + """.bankNameOf(player.f_8924_, account) + ".",
                ChatFormatting.GRAY);"""),
])

# ------------------------------------------------------------------ message for the new result
patch("patchsrc/com/fshop/shop/ResultMessages.java", [
    ("""            case OUT_OF_STOCK: {""",
     """            case NO_BANK_ACCOUNT: {
                key = "fshop.msg.no_bank_account";
                break;
            }
            case OUT_OF_STOCK: {"""),
])

print("\nshop tied to bank accounts")
