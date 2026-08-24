#!/usr/bin/env python3
"""Adds the bank-redesign translation keys to every language file.

Separate from `add_atm_lang.py` only to keep each file readable; the key namespaces are disjoint
(`cfg_*`, `color_*`, `click_*`, `risk_*`, `req_*`), so the two cannot fight over a key.

Wording rule for this set: labels are short because the form gives them about 42% of the row, and
hints are one short clause because they sit under the label in the same space. Anything that needs a
sentence goes in a tooltip, which wraps, instead of a label, which clips.

Idempotent: re-running rewrites the same values and reports zero new keys.
"""
import collections
import json
import os

FILES = ["en_us.json", "es_ar.json", "es_es.json", "es_mx.json"]
BASE = os.path.join(os.path.dirname(__file__), os.pardir,
                    "src", "main", "resources", "assets", "athens_coins", "lang")

ADD = {
    # --- tab gating ----------------------------------------------------------
    "gui.athens_coins.tab_locked": "Configura el banco primero",
    "gui.athens_coins.cfg_required": "Completa esta ficha para abrir las demás pestañas",
    "gui.athens_coins.cfg_hint": "Cambia lo que necesites y pulsa Guardar",

    # --- form chrome ---------------------------------------------------------
    "gui.athens_coins.cfg_save": "Guardar",
    "gui.athens_coins.cfg_save_tip": "Aplica toda la ficha de una vez",
    "gui.athens_coins.cfg_sent": "Configuración enviada",
    "gui.athens_coins.cfg_bad_field": "%s: %s",

    # --- groups --------------------------------------------------------------
    "gui.athens_coins.cfg_group_identity": "Identidad",
    "gui.athens_coins.cfg_group_wallet": "Tarjeta y comisión",
    "gui.athens_coins.cfg_group_rates": "Tasas de cambio",
    "gui.athens_coins.cfg_group_loans": "Préstamos",

    # --- fields --------------------------------------------------------------
    "gui.athens_coins.cfg_name": "Nombre",
    "gui.athens_coins.cfg_name_hint": "Cómo te ven los clientes",
    "gui.athens_coins.cfg_color": "Color",
    "gui.athens_coins.cfg_color_hint": "Marca del banco y sus cajeros",
    "gui.athens_coins.cfg_color_tip": "Clic para cambiar de color. Tiñe la terminal y los cajeros que emitas.",
    "gui.athens_coins.cfg_wallet_limit": "Techo de tarjeta",
    "gui.athens_coins.cfg_wallet_limit_hint": "Máximo que se lleva encima. 0 = sin techo",
    "gui.athens_coins.cfg_fee": "Comisión",
    "gui.athens_coins.cfg_fee_hint": "Importe fijo por periodo",
    "gui.athens_coins.cfg_fee_days": "Cada cuántos días",
    "gui.athens_coins.cfg_fee_days_hint": "Días reales entre cobros (1 a 30)",
    "gui.athens_coins.cfg_rate_hint": "Precio de la moneda, dentro de la banda oficial",
    "gui.athens_coins.cfg_loans": "Conceder préstamos",
    "gui.athens_coins.cfg_loans_hint": "Si no, no se aceptan solicitudes",
    "gui.athens_coins.cfg_loans_tip": "Clic para activar o desactivar los préstamos de este banco",
    "gui.athens_coins.cfg_loan_max": "Préstamo máximo",
    "gui.athens_coins.cfg_loan_max_hint": "Tope por cliente",
    "gui.athens_coins.cfg_loan_days": "Plazo",
    "gui.athens_coins.cfg_loan_days_hint": "Días hábiles para pagar (1 a 60)",
    "gui.athens_coins.cfg_loan_interest": "Interés de mora",
    "gui.athens_coins.cfg_loan_interest_hint": "Porcentaje por día hábil vencido",
    "gui.athens_coins.cfg_rate_of": "tasa de %s ajustada a %s",

    # --- palette -------------------------------------------------------------
    "gui.athens_coins.color_slate": "Pizarra",
    "gui.athens_coins.color_navy": "Marino",
    "gui.athens_coins.color_teal": "Turquesa",
    "gui.athens_coins.color_forest": "Bosque",
    "gui.athens_coins.color_olive": "Oliva",
    "gui.athens_coins.color_gold": "Oro",
    "gui.athens_coins.color_amber": "Ámbar",
    "gui.athens_coins.color_rust": "Óxido",
    "gui.athens_coins.color_crimson": "Carmesí",
    "gui.athens_coins.color_plum": "Ciruela",
    "gui.athens_coins.color_violet": "Violeta",
    "gui.athens_coins.color_graphite": "Grafito",

    # --- what a click will do ------------------------------------------------
    # Clicking a row used to be an unlabelled action with nothing saying the row was even clickable.
    "gui.athens_coins.click_grant_banker": "Clic: dar acceso de banquero a %s",
    "gui.athens_coins.click_revoke_banker": "Clic: quitar el acceso de banquero a %s",
    "gui.athens_coins.click_open_account": "Clic: aperturar una cuenta a %s",
    "gui.athens_coins.click_already_has": "%s ya tiene cuenta",
    "gui.athens_coins.click_account_detail": "Clic izq.: ficha de %s (riesgo %s). Der.: cerrar.",
    "gui.athens_coins.atms_rates": "Tasas de este banco",

    # --- shortened, because they did not fit -----------------------------------
    # These four were written before the screens had column headings to lean on, so each one carried the
    # whole explanation. They are now the longest strings in a band that also has to hold a value.
    "gui.athens_coins.accounts_hint2": "Izquierdo: ficha. Derecho: cerrar.",
    "gui.athens_coins.users_hint": "Clic: dar o quitar acceso de banquero",
    "gui.athens_coins.atms_note": "Cada cajero usa las tasas y el color de este banco",
    "gui.athens_coins.central_official": "Tasas oficiales",

    # --- account file, and the bank's own risk desk --------------------------
    # The account screen was two unlabelled columns of coloured numbers with nothing saying what could
    # be done with them. Every column is titled now, and the standing is shown with a word next to it.
    "gui.athens_coins.acct_hint": "Escribe un monto y pulsa Prestar o Cobrar",
    "gui.athens_coins.acct_section_data": "Cuenta",
    "gui.athens_coins.acct_section_risk": "Central de riesgo",
    "gui.athens_coins.acct_wallet": "Tarjeta",
    "gui.athens_coins.risk_score": "Calificación",
    "gui.athens_coins.risk_loans": "Préstamos",
    "gui.athens_coins.risk_settled": "Pagados",
    "gui.athens_coins.risk_overdue": "Con mora",
    "gui.athens_coins.risk_borrowed": "Total prestado",
    "gui.athens_coins.risk_penalty": "Mora cobrada",
    "gui.athens_coins.risk_last": "Último",
    "gui.athens_coins.risk_never": "Nunca",
    "gui.athens_coins.risk_hint": "Historial en este banco",
    "gui.athens_coins.risk_good": "Bueno",
    "gui.athens_coins.risk_fair": "Regular",
    "gui.athens_coins.risk_bad": "Malo",

    # --- central bank --------------------------------------------------------
    # The figures used to sit loose at the top with nothing lining up. Two titled tables now, each with
    # its own header row, so every number has a column name above it.
    "gui.athens_coins.central_hint": "Elige un banco y usa los botones de abajo",
    "gui.athens_coins.central_col_coin": "Moneda",
    "gui.athens_coins.central_col_official": "Oficial",
    "gui.athens_coins.central_col_min": "Mín",
    "gui.athens_coins.central_col_max": "Máx",
    "gui.athens_coins.central_system": "Sistema",
    "gui.athens_coins.central_banks_count": "Bancos",
    "gui.athens_coins.central_accounts_count": "Cuentas",
    "gui.athens_coins.central_reserve_total": "Reservas",
    "gui.athens_coins.central_deposits_total": "Depósitos",
    "gui.athens_coins.central_loans_total": "Préstamos vivos",
    "gui.athens_coins.central_margin": "Banda",
    "gui.athens_coins.central_col_bank": "Banco",
    "gui.athens_coins.central_col_reserve": "Reserva",
    "gui.athens_coins.central_col_deposits": "Depósitos",
    "gui.athens_coins.central_col_loans": "Prestado",
    "gui.athens_coins.central_col_accounts": "Ctas",
    "gui.athens_coins.central_selected": "Seleccionado: %s",
    "gui.athens_coins.central_click_bank": "Clic: operar sobre %s",

    # --- loan applications ---------------------------------------------------
    # Borrowing used to be self-service at the ATM; a loan is an agreement, so the customer applies
    # and the bank decides. The wording has to make that obvious at both ends.
    "gui.athens_coins.tab_requests": "Solicitudes",
    "gui.athens_coins.requests_hint": "Izquierdo: aprobar. Derecho: rechazar.",
    "gui.athens_coins.requests_none": "No hay solicitudes pendientes",
    "gui.athens_coins.click_request": "%s pide %s. Izquierdo aprueba, derecho rechaza.",
    "gui.athens_coins.atm_apply": "Solicitar",
    "gui.athens_coins.atm_apply_tip": "Envía la solicitud al banco. No se cobra hasta que la aprueben.",
    "gui.athens_coins.atm_apply_hint": "El banco tiene que aprobarla antes de recibir el dinero",
    "gui.athens_coins.atm_request_pending": "Solicitado",
    "gui.athens_coins.atm_request_wait": "Esperando que el banco la revise",
    "message.athens_coins.loan_requested": "Solicitud de %s enviada al banco",
    "message.athens_coins.loan_over_max": "El máximo de este banco es %s",
    "message.athens_coins.loan_request_filed": "%s solicita un préstamo de %s en %s",
    "message.athens_coins.loan_request_approved": "Préstamo de %s aprobado a %s",
    "message.athens_coins.loan_request_refused": "Solicitud de %s rechazada",
    "message.athens_coins.loan_request_rejected": "%s rechazó tu solicitud de préstamo",
    "message.athens_coins.loan_request_gone": "Esa solicitud ya no existe",

    # --- confirmations -------------------------------------------------------
    # Opening an account, handing over banker access and closing an account were one click on a row,
    # executed immediately. They ask first now, naming the target so the actor can see what they hit.
    "message.athens_coins.ask_open_account": "¿Aperturar una cuenta a %s?",
    "message.athens_coins.ask_grant_banker": "¿Dar acceso de banquero a %s? Podrá aperturar y cerrar cuentas.",
    "message.athens_coins.ask_revoke_banker": "¿Quitar el acceso de banquero a %s?",
    "message.athens_coins.ask_close_account": "¿Cerrar la cuenta %s y entregar su dinero en tarjeta?",
    "message.athens_coins.ask_approve_loan": "¿Aprobar %s de préstamo a %s?",
    "message.athens_coins.ask_reject_loan": "¿Rechazar la solicitud de %s?",
    "message.athens_coins.ask_expired": "Esa confirmación ya caducó",
    "message.athens_coins.ask_cancelled": "Cancelado",
    "message.athens_coins.button_confirm": "[Confirmar]",
    "message.athens_coins.button_cancel": "[Cancelar]",

    # --- server replies ------------------------------------------------------
    "message.athens_coins.bank_configured": "Configuración guardada",
    "message.athens_coins.bank_configured_first":
        "Banco configurado. Ya puedes usar el resto de las pestañas.",
    "message.athens_coins.bank_cfg_adjusted": "Ajustado por política: %s",
}

# Keys whose screens no longer exist. Left in place they are dead weight a translator has to work
# through and, worse, a reader looking for the live wording finds two candidates. Every one of these
# was checked against the Java source first: none is referenced.
#
# `bank_*`, `hint_*`: the old settings grid of eight buttons over two shared entry boxes, replaced by
# the labelled form. `atm_borrow*`, `atm_loan_taken`: self-service borrowing at the ATM, replaced by
# applying for a loan the bank approves. `central_banks`, `central_columns`: the loose figures and the
# footer legend of the old central-bank header, replaced by two tables with real column headings.
REMOVE = [
    "gui.athens_coins.atm_borrow",
    "gui.athens_coins.atm_borrow_hint",
    "gui.athens_coins.atm_borrow_tip",
    "gui.athens_coins.bank_fee",
    "gui.athens_coins.bank_fee_days",
    "gui.athens_coins.bank_loan_days",
    "gui.athens_coins.bank_loan_max",
    "gui.athens_coins.bank_name",
    "gui.athens_coins.bank_rate_bronze",
    "gui.athens_coins.bank_rate_silver",
    "gui.athens_coins.bank_rate_gold",
    "gui.athens_coins.bank_wallet_limit",
    "gui.athens_coins.hint_days",
    "gui.athens_coins.hint_money",
    "gui.athens_coins.hint_rate",
    "gui.athens_coins.central_banks",
    "gui.athens_coins.central_columns",
    "message.athens_coins.atm_loan_taken",
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
