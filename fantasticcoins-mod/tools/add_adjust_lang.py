#!/usr/bin/env python3
"""Translation keys for the licensing tiers, the in-screen confirmations and the per-bank stats boards.

Three groups of change, all driven by the same round of feedback:

*Confirmations moved out of chat.* The `ask_*` questions survive - they were the right wording - but the
two clickable words and the expiry notice do not, because the dialog is inside the screen now and has real
buttons. Each question gains a `*_detail` line saying what it costs, which is the part somebody about to
press Confirm actually needs and which there was no room for on a chat line.

*Account opening became an offer to the customer.* An account is an agreement, so the question goes to the
person who will be paying the fee, with the terms in it. That is a new vocabulary (`offer_*`) rather than a
reworded confirmation, because the reader changed.

*Boards and terminals gained an owner.* `founder`/`licence` wording throughout, and boards are named after
the bank that issued them.

Wording rule for this set: `*_detail` and `*_note` lines get a full band and may be a clause; everything
that shares a row with a value stays inside the character budget verify.py enforces.

Idempotent: re-running rewrites the same values and reports zero new keys.
"""
import collections
import json
import os

FILES = ["en_us.json", "es_ar.json", "es_es.json", "es_mx.json"]
BASE = os.path.join(os.path.dirname(__file__), os.pardir,
                    "src", "main", "resources", "assets", "athens_coins", "lang")

ADD = {
    # --- licensing tiers -----------------------------------------------------
    "message.athens_coins.central_place_op_only": "Solo un operador puede colocar el banco central",
    "message.athens_coins.central_licence_op_only": "Solo un operador puede dar o quitar licencias",
    "message.athens_coins.central_licensed": "%s ya puede fundar bancos",
    "message.athens_coins.central_unlicensed": "%s ya no puede fundar bancos",
    "message.athens_coins.central_licensed_you":
        "El banco central te autorizó a fundar y dirigir bancos",
    "message.athens_coins.central_unlicensed_you":
        "El banco central te retiró la autorización para fundar bancos",
    "gui.athens_coins.central_tab_rates": "Tasas",
    "gui.athens_coins.central_tab_banks": "Bancos",
    "gui.athens_coins.central_tab_licences": "Licencias",
    "gui.athens_coins.central_hint_rates": "Escribe un precio y pulsa la moneda que quieres mover",
    "gui.athens_coins.central_hint_banks": "Elige un banco y usa los botones de abajo",
    "gui.athens_coins.central_hint_licences": "Clic en un jugador para dar o quitar la licencia",
    "gui.athens_coins.central_licences": "Quién puede fundar bancos",
    "gui.athens_coins.is_founder": "con licencia",
    "gui.athens_coins.not_founder": "sin licencia",
    "gui.athens_coins.click_license": "Clic: autorizar a %s a fundar bancos",
    "gui.athens_coins.click_unlicense": "Clic: quitar la licencia a %s",
    "gui.athens_coins.licence_read_only": "%s · solo un operador puede cambiar esto",
    "gui.athens_coins.licence_read_only_note": "Solo un operador puede dar o quitar licencias",
    "gui.athens_coins.central_amount": "Monto",
    "gui.athens_coins.central_band": "Banda permitida: %s%%",
    "gui.athens_coins.central_issue_board": "Generar tablero del servidor",
    "gui.athens_coins.central_issue_board_tip":
        "Un tablero sin banco: muestra las cifras de todo el servidor. Los de cada banco se generan en su propia terminal.",
    "message.athens_coins.central_board_issued": "Tablero del servidor entregado",

    # --- the in-screen confirmation -----------------------------------------
    "gui.athens_coins.confirm": "Confirmar",
    "gui.athens_coins.cancel": "Cancelar",
    "gui.athens_coins.ask_grant_detail": "Podrá aperturar y cerrar cuentas de este banco.",
    "gui.athens_coins.ask_revoke_detail": "Dejará de poder abrir esta terminal.",
    "gui.athens_coins.ask_close_detail":
        "El saldo sale en tarjeta. Se rechaza si quedan deudas.",
    "gui.athens_coins.ask_approve_detail": "El dinero sale de la reserva ahora mismo.",
    "gui.athens_coins.ask_reject_detail": "La solicitud se borra y se le avisa.",

    # --- opening an account is confirmed in the screen too --------------------
    # It was briefly an offer the customer accepted from a chat button, which needed a command behind it -
    # and a command that only exists while an offer is pending is one that is missing exactly when someone
    # tries to use it. Same dialog as every other action.
    "message.athens_coins.ask_open_account": "¿Aperturar una cuenta a %s?",
    "gui.athens_coins.ask_open_detail": "Se le entrega la cuenta y el comprobante.",

    # --- boards belong to a bank --------------------------------------------
    "item.athens_coins.board_of": "Tablero de %s",
    "message.athens_coins.bank_board_issued": "Tablero del banco entregado",
    "gui.athens_coins.issue_board": "Generar tablero",
    "gui.athens_coins.issue_board_tip":
        "Un tablero con las cifras de este banco. Se coloca donde quieras y lo ve todo el mundo.",
    "gui.athens_coins.holo_scope_server": "Todo el servidor",
    "gui.athens_coins.holo_scope_bank": "Banco: %s",
    "gui.athens_coins.holo_heading_hint": "sin título",
    "metric.athens_coins.reserve": "Reserva",
    "metric.athens_coins.loans_out": "Prestado",

    # --- account file: one magnitude per row, short labels -------------------
    # Every one of these was truncated in the screenshot. They share a row with a figure, and the figure is
    # the reason the row exists, so the label is what gives.
    "gui.athens_coins.acct_wallet": "Tarjeta",
    "gui.athens_coins.acct_wallet_cap": "Techo",
    "gui.athens_coins.acct_fee": "Comisión",
    "gui.athens_coins.acct_fee_every": "Cada",
    "gui.athens_coins.acct_reserve": "Reserva",
    "gui.athens_coins.acct_scroll": "rueda",
    "gui.athens_coins.risk_score": "Nota",
    "gui.athens_coins.risk_borrowed": "Prestado",
    "gui.athens_coins.risk_penalty": "Mora",
    "gui.athens_coins.risk_overdue": "En mora",

    # --- movement names, which share a row with a date and an amount ---------
    # These are the "Présta…", "Pago d…", "A la ca…", "Desde l…" from the screenshot. The row holds three
    # things and the other two are fixed-shape strings that cannot shrink, so the name is what gives.
    # Shortened to the noun and, where the direction matters, an arrow - which reads faster than the words
    # it replaces and costs one character.
    "ledger.athens_coins.central_injection": "Inyección central",
    "ledger.athens_coins.closed": "Cierre",
    "ledger.athens_coins.coin_deposit": "Monedas +",
    "ledger.athens_coins.coin_withdraw": "Monedas -",
    "ledger.athens_coins.loan_granted": "Préstamo",
    "ledger.athens_coins.loan_interest": "Mora",
    "ledger.athens_coins.loan_repaid": "Pago",
    "ledger.athens_coins.shop_purchase": "Compra",
    "ledger.athens_coins.shop_sale": "Venta",
    "ledger.athens_coins.transfer_received": "Transf. recibida",
    "ledger.athens_coins.transfer_sent": "Transf. enviada",
    "ledger.athens_coins.wallet_in": "De tarjeta",
    "ledger.athens_coins.wallet_out": "A tarjeta",
    "gui.athens_coins.acct_fee_debt": "Impagado",
    "gui.athens_coins.acct_overdue": "vencido %sd",

    # --- what just happened, said in the panel -------------------------------
    # The server's own feedback goes to the action bar, and the action bar is not carried across the screen
    # refresh every terminal action triggers - so a button that worked looked like a button that did nothing.
    # These are shown on the tab the click happened on.
    "gui.athens_coins.done_granted": "%s ya es banquero",
    "gui.athens_coins.done_revoked": "%s ya no es banquero",
    "gui.athens_coins.done_opened": "Cuenta aperturada a %s",
    "gui.athens_coins.done_approved": "Préstamo aprobado a %s",
    "gui.athens_coins.done_rejected": "Solicitud de %s rechazada",
    "gui.athens_coins.done_closed": "Cuenta #%s cerrada",
    "gui.athens_coins.done_atms": "%s cajero(s) en tu inventario",
    "gui.athens_coins.done_board": "Tablero en tu inventario",
    "gui.athens_coins.done_licensed": "%s ya puede fundar bancos",
    "gui.athens_coins.done_unlicensed": "%s ya no puede fundar bancos",
    "gui.athens_coins.done_sent": "Hecho",

    # --- charge for sending money to another bank ----------------------------
    "gui.athens_coins.cfg_cross_fee": "A otro banco",
    "gui.athens_coins.cfg_cross_fee_hint": "Cargo fijo al enviar a un cliente de otro banco. 0 = gratis",
    "tooltip.athens_coins.bank_cross_fee": "A otro banco: %s",
    "tooltip.athens_coins.free": "gratis",

    # --- the wallet's third cell: the holder's own record --------------------
    # It showed a picture of an ATM, which told the holder nothing they could not see by looking around.
    "tooltip.athens_coins.record": "Tu historial",
    "tooltip.athens_coins.record_time": "Tiempo jugado: %s",
    "tooltip.athens_coins.record_mobs": "Mobs eliminados: %s",
    "tooltip.athens_coins.record_players": "Jugadores eliminados: %s",
    "tooltip.athens_coins.record_deaths": "Muertes: %s",

    # --- units, so a bare number says what it is ----------------------------
    "gui.athens_coins.unit_money": "($)",
    "gui.athens_coins.unit_days": "(días)",
    "gui.athens_coins.unit_percent": "(%)",
    "gui.athens_coins.unit_days_hint": "días",
}

# Gone with the chat confirmations and the stats command. The `ask_*` questions themselves are kept: the
# dialog asks the same thing, it just has real buttons now.
REMOVE = [
    "message.athens_coins.button_confirm",
    "message.athens_coins.button_cancel",
    "message.athens_coins.ask_expired",
    "message.athens_coins.ask_cancelled",
    "message.athens_coins.hologram_none_near",
    # The hologram never casts a shadow now: Font draws one by offsetting a copy a thousandth of a block in
    # z, which at nameplate scale strobes instead of reading as a shadow.
    "gui.athens_coins.holo_shadow",
    # The account offer and its command are gone; the banker confirms in the screen.
    "gui.athens_coins.offer_sent_to",
    "message.athens_coins.offer_sent",
    "message.athens_coins.offer_header",
    "message.athens_coins.offer_terms",
    "message.athens_coins.offer_accept",
    "message.athens_coins.offer_decline",
    "message.athens_coins.offer_expired",
    "message.athens_coins.offer_lapsed",
    "message.athens_coins.offer_gone",
    "message.athens_coins.offer_declined",
    "message.athens_coins.offer_refused",
    # The wallet has no frame again, so it has no hint line to put in one.
    "gui.athens_coins.wallet_hint",
    # The wallet's third cell reports the holder's record now, not whether an ATM is nearby.
    "tooltip.athens_coins.bank",
    "tooltip.athens_coins.bank_how",
    "tooltip.athens_coins.bank_near",
    "tooltip.athens_coins.bank_far",
    # The central bank has one hint per tab now, and the band is named in the rate tab.
    "gui.athens_coins.central_hint",
    "gui.athens_coins.central_margin",
]


def main():
    for name in FILES:
        path = os.path.join(BASE, name)
        with open(path, encoding="utf-8") as handle:
            data = json.load(handle, object_pairs_hook=collections.OrderedDict)
        added = sum(1 for key in ADD if key not in data)
        data.update(ADD)
        dropped = sum(1 for key in REMOVE if data.pop(key, None) is not None)
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(data, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
        print(f"{name}: +{added} nuevas, -{dropped} obsoletas, {len(data)} en total")


if __name__ == "__main__":
    main()
