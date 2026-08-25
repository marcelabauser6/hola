#!/usr/bin/env python3
"""Adds the stats-hologram translation keys, and removes the ones the old stats screens used.

The statistics stopped being a private admin window and became a hologram standing in the world, with
the screen turned into its editor. That moved every string: the two dashboard tabs and the colour
editor's own labels are gone, and in their place are the metric names (which are now a list the admin
picks from rather than rows hard-coded into a render method) and the editor's controls.

Wording rules for this set:
- `metric.*` are picked from a list next to their live value and drawn on a hologram beside their
  figure, so they are short noun phrases with no punctuation.
- `holo_*` labels share a row with a slider readout or a swatch; hints get a full band; `*_tip`
  wraps in a tooltip and can be a sentence.

Idempotent: re-running rewrites the same values and reports zero new keys.
"""
import collections
import json
import os

FILES = ["en_us.json", "es_ar.json", "es_es.json", "es_mx.json"]
BASE = os.path.join(os.path.dirname(__file__), os.pardir,
                    "src", "main", "resources", "assets", "athens_coins", "lang")

ADD = {
    # --- the block ------------------------------------------------------------
    "block.athens_coins.stats_hologram": "Holograma de estadísticas",
    "message.athens_coins.hologram_op_only": "Solo un operador puede usar el proyector",
    "message.athens_coins.hologram_saved": "Holograma guardado",
    "message.athens_coins.hologram_gone": "Ese proyector ya no está",
    "message.athens_coins.hologram_none_near": "No hay ningún proyector a %s bloques. Coloca uno.",

    # --- what a line can show -------------------------------------------------
    # These are the figures an admin picks from. Each one is also drawn on the hologram itself, next to
    # its value, so they have to be short enough to leave room for the number.
    "metric.athens_coins.blank": "— línea vacía —",
    "metric.athens_coins.text": "Texto libre",
    "metric.athens_coins.accounts": "Cuentas",
    "metric.athens_coins.online_players": "Conectados",
    "metric.athens_coins.total_cash": "Efectivo",
    "metric.athens_coins.coin_value": "Valor en monedas",
    "metric.athens_coins.total_supply": "Masa monetaria",
    "metric.athens_coins.average": "Saldo medio",
    "metric.athens_coins.median": "Saldo mediano",
    "metric.athens_coins.richest": "Mayor saldo",
    "metric.athens_coins.top_ten_share": "Concentración",
    "metric.athens_coins.rate_bronze": "Tasa bronce",
    "metric.athens_coins.rate_silver": "Tasa plata",
    "metric.athens_coins.rate_gold": "Tasa oro",
    "metric.athens_coins.coins_bronze": "Monedas bronce",
    "metric.athens_coins.coins_silver": "Monedas plata",
    "metric.athens_coins.coins_gold": "Monedas oro",
    "metric.athens_coins.top_holders": "Ranking de saldos",

    # --- the editor -----------------------------------------------------------
    "gui.athens_coins.holo_title": "Editor de holograma",
    "gui.athens_coins.holo_tab_content": "Contenido",
    "gui.athens_coins.holo_tab_look": "Aspecto",
    "gui.athens_coins.holo_hint_content":
        "Elige una línea y luego la cifra que debe mostrar",
    "gui.athens_coins.holo_hint_look": "Todo lo que cambies se ve al instante en la vista previa",

    "gui.athens_coins.holo_lines": "Líneas",
    "gui.athens_coins.holo_no_lines": "Sin líneas. Pulsa + para añadir una.",
    "gui.athens_coins.holo_line_tip": "Clic para seleccionar esta línea",
    "gui.athens_coins.holo_metrics": "Cifras",
    "gui.athens_coins.holo_metric_tip": "Clic para asignarla a la línea seleccionada",
    "gui.athens_coins.holo_label": "Nombre propio",
    "gui.athens_coins.holo_label_tip":
        "Cambia el nombre de esta línea. Déjalo vacío para usar el de la lista.",
    "gui.athens_coins.holo_add_tip": "Añade una línea al final",
    "gui.athens_coins.holo_remove_tip": "Quita la línea seleccionada",
    "gui.athens_coins.holo_up_tip": "Sube la línea seleccionada",
    "gui.athens_coins.holo_down_tip": "Baja la línea seleccionada",
    "gui.athens_coins.holo_full": "El máximo es %s líneas",

    "gui.athens_coins.holo_colors": "Colores",
    "gui.athens_coins.holo_color_title": "Título",
    "gui.athens_coins.holo_color_label": "Etiquetas",
    "gui.athens_coins.holo_color_value": "Valores",
    "gui.athens_coins.holo_color_accent": "Destacado",
    "gui.athens_coins.holo_color_bg": "Fondo",
    "gui.athens_coins.holo_opacity": "Opacidad",
    "gui.athens_coins.holo_layout": "Tamaño y posición",
    "gui.athens_coins.holo_heading": "Título",
    "gui.athens_coins.holo_heading_tip": "Se dibuja centrado arriba. Vacío = sin título.",
    "gui.athens_coins.holo_scale": "Escala",
    "gui.athens_coins.holo_spacing": "Interlineado",
    "gui.athens_coins.holo_height": "Altura",
    "gui.athens_coins.holo_top_rows": "Puestos del ranking",
    "gui.athens_coins.holo_background": "Fondo",
    "gui.athens_coins.holo_shadow": "Sombra",
    "gui.athens_coins.holo_bold": "Título en negrita",
    "gui.athens_coins.holo_billboard": "Mirar al jugador",
    "gui.athens_coins.holo_billboard_tip":
        "Activado, el holograma gira siempre hacia quien lo mira. Desactivado, se queda fijo mirando hacia donde colocaste el proyector.",
    "gui.athens_coins.holo_labels": "Mostrar etiquetas",

    "gui.athens_coins.holo_preview": "Vista previa",
    "gui.athens_coins.holo_preview_empty": "Nada que mostrar todavía",
    "gui.athens_coins.holo_preview_note": "Escala %s%% · %s líneas",
    "gui.athens_coins.holo_preset_tip": "Aplica el diseño %s. Reemplaza líneas y colores.",
    "gui.athens_coins.holo_preset_applied": "Diseño %s aplicado",

    "gui.athens_coins.holo_save": "Guardar",
    "gui.athens_coins.holo_save_tip": "Envía el holograma al proyector. Hasta entonces no cambia nada.",
    "gui.athens_coins.holo_reset": "Restablecer",
    "gui.athens_coins.holo_reset_tip": "Vuelve al holograma de fábrica, sin guardar",
    "gui.athens_coins.holo_reset_done": "Restablecido. Pulsa Guardar para aplicarlo.",
    "gui.athens_coins.holo_close_tip": "Cierra sin guardar los cambios",
    "gui.athens_coins.holo_sent": "Enviado al proyector",

    "gui.athens_coins.on": "sí",
    "gui.athens_coins.off": "no",

    # --- the wallet's new frame ----------------------------------------------
    "gui.athens_coins.wallet_hint": "Pasa el ratón por cada casilla para ver el detalle",
}

# The old stats dashboard and its colour editor. Both screens are gone: the statistics are a hologram
# now, and its editor has its own vocabulary above. Checked against the Java source first - none of
# these is referenced any more.
REMOVE = [
    "gui.athens_coins.stats",
    "gui.athens_coins.stats_supply",
    "gui.athens_coins.stats_supply_total",
    "gui.athens_coins.stats_top",
    "gui.athens_coins.stats_accounts",
    "gui.athens_coins.stats_cash_total",
    "gui.athens_coins.stats_coin_value",
    "gui.athens_coins.stats_distribution",
    "gui.athens_coins.stats_average",
    "gui.athens_coins.stats_median",
    "gui.athens_coins.stats_richest",
    "gui.athens_coins.stats_concentration",
    "gui.athens_coins.stats_rates",
    "gui.athens_coins.stats_rate_line",
    "gui.athens_coins.stats_note",
    "gui.athens_coins.stats_coins_online",
    "gui.athens_coins.stats_edit",
    "gui.athens_coins.theme_save",
    "gui.athens_coins.theme_reset",
    "gui.athens_coins.theme_title",
    "gui.athens_coins.theme_preset",
    "gui.athens_coins.theme_shadow",
    "gui.athens_coins.theme_bold",
    "gui.athens_coins.theme_italic",
    "gui.athens_coins.theme_opacity",
    "theme.athens_coins.background",
    "theme.athens_coins.border",
    "theme.athens_coins.title_bar",
    "theme.athens_coins.title_text",
    "theme.athens_coins.section",
    "theme.athens_coins.label",
    "theme.athens_coins.value",
    "theme.athens_coins.accent",
    "theme.athens_coins.rows",
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
