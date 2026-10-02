# likka_sprites

Herramientas y material de revisión de la hoja final de Likka (`likkapet_design_system.md` §1.7).

La hoja final **no está aquí**: vive en `sprites/` (`likka.png`, `likka.json`, `likka_poses.json`), que es donde la esperan la documentación y la app.

## Carpetas

| Carpeta | Contenido |
| :--- | :--- |
| `tools/build.py` | Construye `sprites/likka.png`, `sprites/likka.json` (formato JSON de Aseprite: *Array* + `frameTags`) y `sprites/likka_poses.json` a partir de `sprites/drafts/2d-sprites-sheets/`. Resultado determinista. |
| `tools/validate.py` | Valida la hoja con los criterios de la prueba unitaria de §1.7 y los del formato del arte (alfa binaria, colores, pies, alto, cuadros y duración por tag). |
| `tools/contact.py` | Regenera todo lo que hay en `review/`. |
| `review/` | `contact_<tag>.png` (×4 sobre cacao y sobre crema), `anim_<tag>.gif` (ciclo ×4 sobre cacao), `overview.png` (hoja completa ×2) y `peek_options.png` (peek visible al 55 / 60 / 65 %; se usa 60 %). |

## Regenerar

Desde la raíz del proyecto (Python 3 con Pillow, numpy y scipy):

```bash
python likka_sprites/tools/build.py
python likka_sprites/tools/validate.py
python likka_sprites/tools/contact.py
```

## Construcción

- Escalado solo por factor entero con vecino más cercano: ÷2 en las hojas de 1280×1280 y ÷3 en la diagonal; `idle`, `walk_down` y `walk_up` van sin escalar. Las hojas de IA no son escalados limpios: al reducirlas y volver a ampliarlas difiere entre un 22 % y un 32 % de los píxeles visibles, por el suavizado del remuestreo. El vecino más cercano y la paleta común absorben ese ruido.
- Alfa binaria (umbral 128), motas sueltas de menos de 6 px eliminadas y paleta común de 16 colores para toda la hoja (k-means en Lab, con el contorno y el ámbar de los ojos fijos). La paleta se calcula siempre con los mismos cuadros (`PALETTE_FRAMES` en `build.py`, la selección de la primera versión), así que cambiar los cuadros o el ritmo de un tag no recolorea la hoja. Contorno de 1 px en todo píxel opaco que toca transparencia.
- Pies en y = 92 en cada cuadro. Un solo desplazamiento en X por tag, que centra la caja de todos sus cuadros y así conserva el movimiento de la animación. `peek` deja visible el 60 % del ancho y queda cortado por el lado derecho.
- Duración: cada tag tiene un fps uniforme o una duración por cuadro en ms (`TAGS` en `build.py`). `validate.py` comprueba la duración exacta de cada cuadro e informa del salto máximo de cada tag (píxeles que cambian entre cuadros seguidos, incluido el paso del último al primero).

## Tags

Los índices de cuadro son los de la hoja de origen (desde 0, fila por fila).

| Tag | Origen (`sprites/drafts/2d-sprites-sheets/`) | Cuadros elegidos | fps / duración | Alto de Likka (px) |
| :--- | :--- | :--- | :---: | :---: |
| `idle` | `left-diagonal-down-idle.png` (sin escalar) | 0, 1, 6, 7 (reposo; sin el aleteo 2–5) | 750 / 250 / 250 / 250 ms (vuelta de 1,5 s) | 86 |
| `walk_down` | `front_front_walk_walk_down.png` (sin escalar) | 0–7 | 10 | 81–90 |
| `walk_up` | `walk_up.png` (sin escalar) | 0, 1, 2, 5, 6, 7, 8, 11 (de 12) | 10 | 76–78 |
| `walk_diag_down_right` | `likka-walk-rigth-diagonal.png` (÷3) | 0–4 | 10 | 81–83 |
| `annoyed` | `Likka-a-angry.png` (÷2) | 0, 6, 17, 16 | 8 | 89–90 |
| `fury` | `Likka-a-angry.png` (÷2) | 7–12 (los más intensos) | 8 | 85–88 |
| `peek` | cuadros de `idle` | 0, 1, 6, 7 (60 % visible, cortado a la derecha) | 750 / 250 / 250 / 250 ms, igual que `idle` | 86 |
| `sit` | `Likka-sit-sit-down.png` (÷2) | 0, 4, 16, 23 | 6 | 63 |
| `sleep` | `Likka-sleepy.png` (÷2) | 15, 16, 23, 17 | 4 | 88 |
| `look_around` | `Likka-c-curiosity.png` (÷2) | 0, 6, 11, 19 | 6 | 89–90 |

## Ritmo de idle

En la hoja de origen, los cuadros 2–5 de idle levantan el élitro (un aleteo). Cualquier paso entre reposo {0, 1, 6, 7} y aleteo cambia al menos 1456 px, y ningún orden lo evita. Por eso `idle` y `peek` usan solo los cuadros de reposo, con el 0 sostenido: el salto máximo baja de 1813 a 463 px y la vuelta pasa de 1 s a 1,5 s. En el resto de tags los saltos son parejos (máximo/mediana entre 1,0 y 1,26) y no se tocaron: en `look_around` los ~2000 px son los giros de cabeza del propio original.

## Excepciones aceptadas al rango de 84–88 px

Aceptadas por la dueña; no hay factor entero que las acerque al rango sin agrandar el arte.

- `walk_up`: 76–78 px, porque la hoja va sin escalar (ya documentado en §1.7).
- `sit`: 63 px. Tiene la misma escala (÷2) que el resto, pero Likka sentado es más bajo.
- `walk_diag_down_right`: 81–83 px. Con ÷3 queda un poco por debajo; con ÷2 mediría unos 123 px y no cabría en el lienzo.

## Paleta (16 colores)

| # | Color | Uso |
| :---: | :--- | :--- |
| 1 | `#140A0B` | Contorno (fijo) |
| 2 | `#ECA142` | Ojos ámbar (fijo) |
| 3 | `#1A0D18` | |
| 4 | `#271222` | |
| 5 | `#2F1318` | |
| 6 | `#381B2B` | |
| 7 | `#482527` | |
| 8 | `#4F1F33` | |
| 9 | `#5F272E` | |
| 10 | `#782535` | Frambuesa (bufanda, capa de `sit`) |
| 11 | `#633C39` | |
| 12 | `#7A4437` | |
| 13 | `#955B41` | |
| 14 | `#B57546` | |
| 15 | `#C8916F` | |
| 16 | `#E7B27E` | Luz de cuernos y élitros |
