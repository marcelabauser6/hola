/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.arguments.StringArgumentType
 *  com.mojang.brigadier.context.CommandContext
 *  net.minecraft.ChatFormatting
 *  net.minecraft.commands.CommandSourceStack
 *  net.minecraft.network.chat.Component
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.player.Player
 */
package com.fshop.command;

import com.fshop.Perms;
import com.fshop.config.FShopConfig;
import com.fshop.data.FShopSavedData;
import com.fshop.economy.CoinEconomy;
import com.fshop.shop.PlayerShop;
import com.fshop.shop.ShopNet;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

final class FShopCommands {
    private FShopCommands() {
    }

    static int createShop(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = FShopCommands.playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        if (((Boolean)FShopConfig.REQUIRE_ZONE_FOR_CREATE.get()).booleanValue() && !FShopSavedData.get(player.m_284548_()).isInsideAnyZone((Entity)player)) {
            FShopCommands.msg(player, "Debes estar dentro de una zona de mercado para crear tu tienda.", ChatFormatting.RED);
            return 0;
        }
        String name = StringArgumentType.getString(ctx, (String)"name").trim();
        if (name.isEmpty() || name.length() > 32) {
            FShopCommands.msg(player, "El nombre debe tener entre 1 y 32 caracteres.", ChatFormatting.RED);
            return 0;
        }
        FShopSavedData data = FShopSavedData.get(player.m_284548_());
        List<PlayerShop> owned = data.getShopsByOwner(player.m_20148_());
        if (owned.size() >= (Integer)FShopConfig.MAX_SHOPS_PER_PLAYER.get()) {
            FShopCommands.msg(player, "Ya alcanzaste el m\u00e1ximo de tiendas permitidas (" + FShopConfig.MAX_SHOPS_PER_PLAYER.get() + ").", ChatFormatting.RED);
            return 0;
        }
        int account = com.athensmc.athenscoins.api.FantasticCurrencyAPI.accountNumber(player.f_8924_, player.m_20148_());
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
                + com.athensmc.athenscoins.api.FantasticCurrencyAPI.bankNameOf(player.f_8924_, account) + ".",
                ChatFormatting.GRAY);
        FShopCommands.msg(player, "Tienda \"" + name + "\" creada. A\u00f1ade \u00edtems con el bot\u00f3n de venta.", ChatFormatting.GREEN);
        ShopNet.openManage(player, shop);
        return 1;
    }

    static int buy(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = FShopCommands.playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        if (((Boolean)FShopConfig.REQUIRE_ZONE_FOR_BUY.get()).booleanValue() && !FShopSavedData.get(player.m_284548_()).isInsideAnyZone((Entity)player)) {
            FShopCommands.msg(player, "Debes estar dentro de una zona de mercado para comprar.", ChatFormatting.RED);
            return 0;
        }
        ShopNet.openBrowse(player);
        return 1;
    }

    static int sell(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = FShopCommands.playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        if (((Boolean)FShopConfig.REQUIRE_ZONE_FOR_SELL.get()).booleanValue() && !FShopSavedData.get(player.m_284548_()).isInsideAnyZone((Entity)player)) {
            FShopCommands.msg(player, "Debes estar dentro de una zona de mercado para gestionar tu tienda.", ChatFormatting.RED);
            return 0;
        }
        List<PlayerShop> owned = FShopSavedData.get(player.m_284548_()).getShopsByOwner(player.m_20148_());
        if (owned.isEmpty()) {
            FShopCommands.msg(player, "No tienes ninguna tienda. Usa /fshop create <nombre> primero.", ChatFormatting.YELLOW);
            return 0;
        }
        ShopNet.openManage(player, owned.get(0));
        return 1;
    }

    static int edit(CommandContext<CommandSourceStack> ctx) {
        return FShopCommands.sell(ctx);
    }

    static int collect(CommandContext<CommandSourceStack> ctx) {
        int c;
        ServerPlayer player = FShopCommands.playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        if (!CoinEconomy.available()) {
            FShopCommands.msg(player, "La moneda del servidor (FantasticCoins) no est\u00e1 instalada.", ChatFormatting.RED);
            return 0;
        }
        // This used to sum only the three coin slots but call clearEarnings(), which zeroes all four,
        // so any pending cash was deleted without ever being paid. Take each currency, pay it, and
        // put back whatever the payout refused.
        FShopSavedData data = FShopSavedData.get(player.m_284548_());
        long[] paid = new long[CoinEconomy.TYPES];
        boolean heldBack = false;
        for (PlayerShop shop : data.getShopsByOwner(player.m_20148_())) {
            for (c = 0; c < CoinEconomy.TYPES; ++c) {
                long amount = shop.takeEarnings(c);
                if (amount <= 0L) continue;
                if (CoinEconomy.depositSale((Player)player, c, amount,
                        shop.isMain() ? 0 : shop.getAccountNumber())) {
                    paid[c] = paid[c] + amount;
                } else {
                    shop.restoreEarnings(c, amount);
                    heldBack = true;
                }
            }
        }
        long sum = 0L;
        for (long amount : paid) {
            sum += amount;
        }
        data.m_77762_();
        if (sum <= 0L) {
            FShopCommands.msg(player, heldBack
                            ? "No se pudo abonar tu dinero: necesitas una cuenta bancaria activa."
                            : "No tienes ganancias pendientes por cobrar.",
                    heldBack ? ChatFormatting.RED : ChatFormatting.YELLOW);
            return 0;
        }
        FShopCommands.msg(player, "Cobraste: " + FShopCommands.earningsToString(paid) + ".", ChatFormatting.GREEN);
        if (heldBack) {
            FShopCommands.msg(player, "Parte qued\u00f3 pendiente: necesitas una cuenta bancaria activa.",
                    ChatFormatting.YELLOW);
        }
        return 1;
    }

    static int balance(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = FShopCommands.playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        long[] bal = new long[CoinEconomy.TYPES];
        for (int c = 0; c < CoinEconomy.TYPES; ++c) {
            bal[c] = CoinEconomy.balance((Player)player, c);
        }
        FShopCommands.msg(player, "Tu saldo: " + FShopCommands.earningsToString(bal) + ".", ChatFormatting.GOLD);
        return 1;
    }

    static String coinsToString(long[] amounts) {
        return amounts[2] + " oro, " + amounts[1] + " plata, " + amounts[0] + " bronce";
    }

    /** Coins as plain counts, cash formatted with its symbol and two decimals. */
    static String earningsToString(long[] amounts) {
        String text = FShopCommands.coinsToString(amounts);
        if (amounts.length > CoinEconomy.CASH && CoinEconomy.cashAvailable()) {
            text = text + ", " + CoinEconomy.formatAmount(CoinEconomy.CASH, amounts[CoinEconomy.CASH])
                    + " en " + CoinEconomy.cashName();
        }
        return text;
    }

    static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = (CommandSourceStack)ctx.getSource();
        FShopCommands.line(src, "===== FShop =====", ChatFormatting.GOLD);
        FShopCommands.line(src, "/fshop create <nombre> - crea y abre tu tienda", ChatFormatting.YELLOW);
        FShopCommands.line(src, "/fshop buy - explora las tiendas del servidor", ChatFormatting.YELLOW);
        FShopCommands.line(src, "/fshop edit - gestiona tu tienda, stock y precios", ChatFormatting.YELLOW);
        FShopCommands.line(src, "/fshop collect - cobra tus ganancias", ChatFormatting.YELLOW);
        FShopCommands.line(src, "/fshop balance - muestra tu saldo", ChatFormatting.YELLOW);
        FShopCommands.line(src, "Para vender necesitas una cuenta bancaria: sin ella tu tienda queda "
                + "congelada y nadie puede comprarte.", ChatFormatting.GRAY);
        if (Perms.isAdmin(src)) {
            FShopCommands.line(src, "/fshop admin ... - herramientas de administraci\u00f3n (solo OP)", ChatFormatting.AQUA);
        }
        return 1;
    }

    static ServerPlayer playerOrNull(CommandContext<CommandSourceStack> ctx) {
        try {
            return ((CommandSourceStack)ctx.getSource()).m_81375_();
        }
        catch (Exception e) {
            ((CommandSourceStack)ctx.getSource()).m_81352_((Component)Component.m_237113_((String)"Este comando solo puede usarlo un jugador."));
            return null;
        }
    }

    static void msg(ServerPlayer player, String text, ChatFormatting color) {
        player.m_213846_((Component)Component.m_237113_((String)"[FShop] ").m_130940_(ChatFormatting.GOLD).m_7220_((Component)Component.m_237113_((String)text).m_130940_(color)));
    }

    static void line(CommandSourceStack src, String text, ChatFormatting color) {
        src.m_243053_((Component)Component.m_237113_((String)text).m_130940_(color));
    }
}

