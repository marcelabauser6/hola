#!/usr/bin/env python3
"""Adds the ATM-rework translation keys to every language file, and drops the ones whose
only caller is gone.

All four language files carry the same Spanish text today, so they are updated
identically; keeping them in lockstep is what stops a key from resolving on one client
and showing a raw identifier on another.

Idempotent: re-running it rewrites the same values and reports zero new keys.
"""
import collections
import json
import os

FILES = ["en_us.json", "es_ar.json", "es_es.json", "es_mx.json"]
BASE = os.path.join(os.path.dirname(__file__), os.pardir,
                    "src", "main", "resources", "assets", "athens_coins", "lang")

ADD = {
    # --- manual amount entry -------------------------------------------------
    "gui.athens_coins.amount_hint": "0.00",
    "gui.athens_coins.count_hint": "0",
    "gui.athens_coins.amount_label": "Cantidad",
    "gui.athens_coins.count_label": "Unidades",
    "message.athens_coins.amount_missing": "Escribe una cantidad primero",
    "gui.athens_coins.hint_money": "Escribe el importe arriba. Hasta dos decimales: 1.50",
    "gui.athens_coins.bank_days": "Días",

    # --- ATM tabs ------------------------------------------------------------
    "gui.athens_coins.atm_tab_exchange": "Cambio",
    "gui.athens_coins.atm_tab_cash": "Caja",
    "gui.athens_coins.atm_tab_transfer": "Transferir",
    "gui.athens_coins.atm_tab_loan": "Préstamo",

    # --- cash tab ------------------------------------------------------------
    "gui.athens_coins.atm_in_account": "En el banco",
    "gui.athens_coins.atm_on_card": "En la tarjeta",
    "gui.athens_coins.atm_ceiling": "Techo de la tarjeta",
    "gui.athens_coins.atm_no_ceiling": "sin límite",
    "gui.athens_coins.atm_room": "Espacio libre",
    "gui.athens_coins.atm_cash_hint": "Escribe el importe abajo y elige retirar o depositar",
    "gui.athens_coins.atm_withdraw": "Retirar",
    "gui.athens_coins.atm_withdraw_tip": "Pasa el importe de tu cuenta a la tarjeta, hasta el techo",
    "gui.athens_coins.atm_deposit": "Depositar",
    "gui.athens_coins.atm_deposit_tip": "Devuelve el importe de la tarjeta a tu cuenta",
    "gui.athens_coins.atm_max": "Máx.",
    "gui.athens_coins.atm_max_tip": "Rellena la casilla con el máximo que puedes mover",
    "message.athens_coins.atm_not_enough_account": "No tienes tanto en la cuenta",
    "message.athens_coins.atm_not_enough_card": "No tienes tanto en la tarjeta",

    # --- transfer tab --------------------------------------------------------
    "gui.athens_coins.atm_pick_peer": "Elige a quién pagar y escribe el importe",
    "gui.athens_coins.atm_no_peers": "No hay nadie conectado con cuenta bancaria",
    "gui.athens_coins.atm_send": "Enviar",
    "gui.athens_coins.atm_send_tip": "Pide al jugador elegido que acepte el pago",
    "message.athens_coins.transfer_no_target": "Ese jugador ya no está conectado",

    # --- loan tab ------------------------------------------------------------
    "gui.athens_coins.atm_loan_principal": "Capital",
    "gui.athens_coins.atm_loan_status": "Estado",
    "gui.athens_coins.atm_loan_overdue": "%s día(s) hábiles de mora",
    "gui.athens_coins.atm_loan_on_time": "al día",
    "gui.athens_coins.atm_loan_penalty": "Interés de mora por día hábil",
    "gui.athens_coins.atm_loan_available": "Máximo disponible",
    "gui.athens_coins.atm_loan_term": "Plazo",
    "gui.athens_coins.atm_business_days": "%s día(s) hábiles",
    "gui.athens_coins.atm_repay": "Pagar",
    "gui.athens_coins.atm_repay_tip": "Paga el importe escrito a cuenta de tu préstamo",
    "gui.athens_coins.atm_repay_all": "Todo",
    "gui.athens_coins.atm_repay_all_tip": "Rellena la casilla con todo lo que debes",
    "gui.athens_coins.atm_repay_hint": "Se cobra de tu cuenta y, si no llega, de la tarjeta",
    "gui.athens_coins.atm_borrow": "Pedir",
    "gui.athens_coins.atm_borrow_tip": "Pide prestado el importe escrito, si el banco tiene reserva",
    "gui.athens_coins.atm_borrow_max": "Máximo",
    "gui.athens_coins.atm_borrow_max_tip": "Rellena la casilla con el máximo que permite el banco",
    "gui.athens_coins.atm_borrow_hint": "Pasado el plazo se aplica interés por cada día hábil vencido",
    "message.athens_coins.atm_loan_taken": "Recibiste un préstamo de %s",
    "message.athens_coins.atm_loan_repaid": "Pagaste %s de tu préstamo",
    "message.athens_coins.atm_loan_cleared": "Liquidaste tu préstamo con %s",
    "message.athens_coins.atm_no_loan": "No tienes ningún préstamo vivo",
    "message.athens_coins.atm_not_enough_repay": "No tienes fondos para pagar ese importe",

    # --- exchange tab --------------------------------------------------------
    "gui.athens_coins.atm_do_exchange": "Cambiar",
    "gui.athens_coins.atm_do_exchange_tip": "Cambia las unidades escritas de la moneda elegida",
    "gui.athens_coins.atm_coin_pick_tip": "Clic para elegir otra moneda",
    "gui.athens_coins.atm_coin_bronze": "Bronce",
    "gui.athens_coins.atm_coin_silver": "Plata",
    "gui.athens_coins.atm_coin_gold": "Oro",

    # --- why a machine refused -----------------------------------------------
    # One message used to cover both reasons, and it was a lie in the second: a customer who banks
    # somewhere else does have an account. The wrong-bank line names the bank so the player knows
    # which one to go to.
    "message.athens_coins.atm_no_account":
        "No tienes cuenta bancaria. Pide a un banquero que te aperture una.",
    "message.athens_coins.atm_other_bank":
        "Necesitas una cuenta del banco %s para usar este cajero.",

    # --- borrower notifications ---------------------------------------------
    "message.athens_coins.loan_notice_holder": "Tu banco te concedió %s. Vence el %s.",
    "message.athens_coins.loan_notice_overdue":
        "Tu préstamo está en mora: debes %s. Cada día hábil suma interés.",
    "message.athens_coins.bank_loan_granted_amount": "Préstamo de %s concedido",
    "message.athens_coins.bank_loan_partial": "Solo se pudo prestar %s de lo pedido",

    # The second argument is now a ready-made status ("al día", "3 día(s) hábiles",
    # "2 día(s) hábiles de mora"), so the line must not append its own "día(s)".
    "tooltip.athens_coins.bank_loan_line": "Préstamo: %s (%s)",
}

# Their only callers were removed with the old single-view ATM. Note the surviving
# message.athens_coins.atm_to_wallet / atm_to_account: those are the chat confirmations the server
# still sends, and are not the gui.* button labels removed here.
REMOVE = [
    "gui.athens_coins.hint_cents",
    "gui.athens_coins.atm_hint",
    "gui.athens_coins.flow_to_cash",
    "gui.athens_coins.flow_to_coins",
    "gui.athens_coins.atm_in_bank",
    "gui.athens_coins.atm_issuer",
    "gui.athens_coins.atm_to_wallet",
    "gui.athens_coins.atm_to_wallet_tip",
    "gui.athens_coins.atm_to_account",
    "gui.athens_coins.atm_to_account_tip",
    "gui.athens_coins.column_amount",
    "gui.athens_coins.bank_terms",
    # Reserved for a "due soon" reminder that is not implemented; only the grant notice and the
    # overdue warning exist, so shipping this key would be dead weight.
    "message.athens_coins.loan_notice_due",
]


def main():
    for name in FILES:
        path = os.path.join(BASE, name)
        with open(path, encoding="utf-8") as handle:
            data = json.load(handle, object_pairs_hook=collections.OrderedDict)
        dropped = sum(1 for key in REMOVE if data.pop(key, None) is not None)
        added = sum(1 for key in ADD if key not in data)
        data.update(ADD)
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(data, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
        print(f"{name}: +{added} nuevas, -{dropped} obsoletas, {len(data)} en total")


if __name__ == "__main__":
    main()
