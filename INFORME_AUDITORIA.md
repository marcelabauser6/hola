# Informe de auditoría del estudio y del modelo financiero

## Alcance y criterio de intervención

Se conservaron sin cambios los dos archivos originales. Las correcciones se aplican únicamente a copias identificadas como **VERSIÓN AUDITADA**. Un cambio se considera demostrable cuando está respaldado por una operación aritmética, una fórmula del libro, una contradicción interna observable o la configuración explícita de @RISK. No se inventaron cotizaciones, fuentes, categorías de gasto ni resultados de simulación.

## Matriz de correcciones demostrables

| ID | Archivo / ubicación | Hallazgo y evidencia directa | Acción auditada | Estado |
|---|---|---|---|---|
| C-01 | DOCX, Tabla 7 | Varios puntajes ponderados no equivalían a `peso × puntaje`. Los totales correctos son Comayagua `2.50` y Villanueva `1.60`, no `2.30` y `1.70`. | Recalcular cada puntaje ponderado y los dos totales. | Aplicado |
| C-02 | DOCX, Tabla 11; Tablas 12, 15 y XLSM `Estudio Financiero Det!B33` | El desglose de Tabla 11 sumaba L 51,000, mientras las demás ubicaciones usan L 76,000. Diferencia comprobable: L 25,000; amortización anual a cinco años: L 5,000. | Incorporar una fila explícita “Provisión no desagregada del modelo financiero” por L 25,000 y marcar que requiere respaldo documental. Total reconciliado: L 76,000 y L 15,200 anuales. | Aplicado con salvedad documental |
| C-03 | DOCX, Tabla 23; XLSM `Estudio Financiero Det!H93` | En Año 6 la tabla mostraba cuota L 259,833 aunque saldo, intereses y capital eran cero. La fórmula evaluaba un residuo numérico positivo. | Mostrar cuota cero y usar `ROUND(H90,2)>0` como umbral en la fórmula. | Aplicado |
| C-04 | DOCX, Tablas 25 y 26; XLSM filas 104, 112 y 131 | El ICS se resta en las fórmulas de utilidad (`...-B104`) pero no aparecía como fila en los flujos publicados. | Añadir la fila “(−) Impuesto municipal (ICS)” con los valores de Tabla 24, sin cambiar los flujos netos. | Aplicado |
| C-05 | DOCX, Tablas 28 y 30 y párrafos interpretativos; XLSM `B153` y `B166` | Las fórmulas PRI contaban Año 0 como un año transcurrido. El cruce real ocurre entre Años 3 y 4: `3 + 1,317,196 / 1,962,343 ≈ 3.67` y `3 + 1,166,420 / 1,769,181 ≈ 3.66`. | Excluir Año 0 de `COUNTIF`/`INDEX`, actualizar valores y redacción a “durante el cuarto año”. | Aplicado |
| C-06 | DOCX, Tablas 32 y 33; XLSM `Simulacion @RISK!B11:D21` y `H11:H21` | Las tablas omitían cuatro entradas simuladas y redondeaban rangos de forma inconsistente. El libro configura 11 variables: inflación Normal; las demás PERT, incluidas precio del Año 1 y clientes de Años 1, 5 y 10. | Sincronizar variables, rangos, distribuciones y unidades con el libro; retirar rutas de celda y textos internos como “ejemplo dado por el asesor”. | Aplicado |
| C-07 | XLSM, `Modelo Riesgo Operativo!N17:W27`; DOCX, capítulo de riesgos | La matriz anual usaba `RiskPoisson(probabilidad)`, aunque las entradas se definen como probabilidades anuales, `D3:D13` usa `RiskBinomial(1,p)` y las probabilidades de cero publicadas coinciden con el producto Bernoulli `∏(1-p_t)`, no con `exp(-Σp_t)`. | Homogeneizar la matriz anual a `RiskBinomial(1,p)` para que cada riesgo ocurra como máximo una vez por año y el modelo coincida con la tesis y sus gráficas. | Aplicado; requiere nueva corrida @RISK para refrescar resultados |
| C-08 | DOCX, Tablas 7 y 15–33 | Las tablas conservaban rellenos verdes/azules/naranja, títulos internos repetidos y columnas vacías procedentes de Excel. | Reconstruir las tablas con datos efectivos, sin rellenos ni columnas fantasma; aplicar líneas horizontales APA, encabezado destacado tipográficamente, alineación y tamaños legibles. | Aplicado |

## Supuestos o resultados no modificados por falta de evidencia

| ID | Asunto | Motivo para no alterarlo |
|---|---|---|
| P-01 | Naturaleza de los L 25,000 no desagregados | La diferencia es comprobable, pero el expediente no identifica el concepto real ni aporta factura, cotización o autorización. Se transparenta como provisión, no se inventa una categoría. |
| P-02 | VAN, TIR, IRVA, B/C, WACC y resultados Monte Carlo | No se dispone de Excel con el complemento Palisade @RISK para recalcular el libro. No se sustituyen resultados probabilísticos por estimaciones inventadas. |
| P-03 | Media probabilística del VPN superior al VAN determinístico | La diferencia requiere una corrida reproducible de @RISK y revisión del `RiskOutput`; se documenta como punto pendiente, sin alterar valores. |
| P-04 | Fuentes de parámetros legales, tributarios, de mercado y cotizaciones | El archivo no contiene respaldo suficiente para certificar vigencia u origen. La auditoría interna no convierte esas afirmaciones en hechos verificados. |
| P-05 | Parámetros de impacto y probabilidades de los riesgos | El propio documento indica que provienen de juicio experto. Se preservan hasta contar con series históricas o dictamen experto documentado. |

## Entregables

- `Tesis jaime fredy horacio avance 16 - VERSION AUDITADA.docx`
- `Estudio Financiero @risk Jaime Horacio Fredy (1) - VERSION AUDITADA.xlsm`
- `INFORME_AUDITORIA.md`
- `herramientas/generar_versiones_auditadas.py` (procedimiento reproducible)
- `herramientas/verificar_versiones_auditadas.py` (verificación de integridad y criterios aplicados)

## Limitación técnica explícita

El DOCX se valida estructuralmente con `python-docx` y el XLSM se edita directamente sobre XML dentro del contenedor ZIP para conservar imágenes EMF y demás medios. El XLSM original no contiene `vbaProject.bin`; por tanto, pese a la extensión `.xlsm`, no existe un proyecto VBA que preservar. No hay LibreOffice/Excel ni Palisade @RISK en el entorno, por lo que no es posible renderizar visualmente el documento ni ejecutar una nueva simulación. Los resultados probabilísticos existentes se preservan y la copia XLSM queda configurada para recálculo completo al abrirse en Excel con @RISK.

## Verificación final

El script `herramientas/verificar_versiones_auditadas.py` superó **563 comprobaciones**:

- Los originales son idénticos a `origin/main`:
  - DOCX: `6f2f23d28dc575bd05649f2240b8fb483fe8e007b3e3f2e9f206dccf5223dae5` (SHA-256).
  - XLSM: `82d9f7a3e726a05714005536baba7997e535c9c8e086f7c5ba420c85a0e31a11` (SHA-256).
- La copia DOCX conserva 37 tablas, 2,050 párrafos, 3 secciones, los 198 componentes ZIP y los 33 medios originales byte por byte.
- Las Tablas 7, 11 y 15–33 no contienen rellenos de color ni columnas totalmente vacías; tienen bordes horizontales y no usan líneas verticales.
- Los puntajes de T7, la conciliación de T11, la cuota de T23, la fila ICS de T25/T26, los PRI y las variables de T32/T33 coinciden con la matriz de correcciones.
- La inclusión visible del ICS no cambió ningún flujo de caja neto y las utilidades antes de impuestos se reproducen con las cifras publicadas, dentro de una tolerancia de L 2 por redondeo.
- La copia XLSM conserva los 59 componentes ZIP y los 25 medios originales byte por byte. Solo cambian `xl/workbook.xml`, `xl/worksheets/sheet1.xml` y `xl/worksheets/sheet5.xml`, exactamente los tres XML previstos.
- Fuera de `H93`, `B153`, `B166` y `N17:W27`, ninguna celda del modelo financiero o de riesgos cambió semánticamente.
- Las 110 fórmulas de frecuencia usan `RiskBinomial(1,p)` y el libro solicita cálculo automático y recálculo completo al abrir.
- Ambos contenedores superan la comprobación CRC; el DOCX abre con `python-docx` y el XLSM abre en modo lectura con `openpyxl`.

Hashes de las copias generadas:

- DOCX auditado: `0c7aef98359488e8848cfcdcd1671a857659e956c97d450f9c944e181e1c7592`.
- XLSM auditado: `943f7e0ce57cd022360f7a9048a96fc3440503a9ee369464e877f04211ae9257`.
