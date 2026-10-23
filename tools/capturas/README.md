# Generador de capturas de RideScreen AA

Todas las capturas del README, `docs/CAPTURAS.md` y la web (`site/`) salen de aquí, en **español, catalán e inglés a la vez**. Son recreaciones en HTML de las pantallas reales renderizadas con Chromium. No son capturas de un móvil.

Toman de la app todo lo que pueden:
- **Textos:** `strings.xml` de `app/` y `wear/` en cada idioma.
- **Colores:** `colors.xml`.
- **Fuentes:** Archivo, en `res/font`.
- **Iconos:** los *vector drawables*, convertidos a SVG.

Lo demás sale de un único fichero de datos de ejemplo.

## Uso

```bash
pip install playwright pillow      # Chromium: `playwright install chromium` o CAPTURAS_CHROMIUM=/ruta/chrome
python3 tools/capturas/capturas.py            # todo (≈1 min)
python3 tools/capturas/capturas.py home w_    # solo las pantallas que empiezan así
python3 tools/capturas/capturas.py --list     # qué genera y dónde lo escribe
python3 tools/capturas/capturas.py --dry      # escribe en tools/capturas/out/dry, no toca el repo
```

Los HTML intermedios y los másters quedan en `tools/capturas/out/`, que git ignora. Ábrelos en el navegador para depurar una pantalla.

## Estructura

| Fichero | Qué hace |
|---|---|
| `capturas.py` | Tabla de pantallas → idiomas → ficheros publicados (ruta y ancho) |
| `lib/res.py` | Lee strings, colores e iconos de la app |
| `lib/base.py` | Tokens de color, fuentes, barra de estado, lienzos de móvil y reloj |
| `lib/demo.py` | **Datos de ejemplo canónicos** y formato de números y fechas por idioma |
| `lib/maps.py` | Mapa sintético (cuadrícula tipo Eixample) y la ruta del viaje |
| `lib/render.py` | Chromium → máster → cada tamaño publicado |
| `screens/phone.py`, `screens/watch.py` | Las pantallas |
| `assets/hero.png` | Foto del Garaje de la cabecera (ver abajo) |

## Reglas

**Lienzos (máster → publicado)**
- **Móvil:** 412×915 CSS px a 2,625 (1082×2402). Se publica a 540 px en `docs/` y la web, y a 824 px en la guía.
- **Reloj:** 225×225 a 2 (450×450), redondo y con esquinas transparentes. Se publica a 360 px en `docs/` y a 300 px en la web.
- **Tarjeta para compartir:** 1080×1350, como `RideShare.kt`. Se publica a 720 px.
- **Formatos:**
  - PNG de color real, nunca con paleta;
  - WebP calidad 90 en la web;
  - las pantallas opacas se guardan sin canal alfa.

**Aspecto**
- Colores, fuentes, iconos y textos siempre de la app, nunca a ojo.
- Barra de estado fija: **18:42 · 78 %**.
- Tema por defecto (125X verde). Los temas por color de la 2.4 solo en su propia sección.
- Un idioma por imagen. Decimales con coma en ES y CA y con punto en EN, con los mismos decimales que la app (el reloj usa 2 en la lista de viajes porque la app lo hace así).

**Datos de ejemplo (`lib/demo.py`)**
- La ciudad de ejemplo es Barcelona, sin nada personal:
  - piloto inventado (Álex / Àlex / Alex);
  - cuadro `ZT000000`;
  - contactos Laura y Jordi;
  - la ubicación del SMS en plaça de Catalunya.
- Cifras creíbles para una 125 por ciudad: el viaje principal es «Sants → Zona Franca · 6,8 km · 18:40 · media 22 · máx 71 km/h». Es el mismo en el móvil, el reloj, el widget y la tarjeta.
- Si cambias un dato, cámbialo aquí y regenera todo: así no se desincroniza nada.

**Honestidad**
- Lo que existe en la app es una «captura».
- Lo que aún no existe (por ejemplo, la bienvenida en el cuadro) lleva «Maqueta» o «Idea» visible en la imagen.
- El detector de caídas siempre con «(experimental)».

**Nombres:** `NN_tema.png`, iguales en los 3 idiomas, cada idioma en su carpeta (ver `--list`).

## Diferencias conocidas con la app (a 23 oct 2026)
- **Cabecera del mapa del viaje:** la app muestra `6.80 km · 00:18:40 · avg 22 · max 71 km/h` en todos los idiomas (`TripMapActivity` y `TripModel.distanceText` usan `Locale.US` y «avg/max» fijos). Las capturas muestran la versión localizada. Hay que arreglarlo en la app.
- **Fuente del reloj:** Wear OS usa Roboto (condensada en las cifras grandes). Aquí no está instalada, así que se usa Inter estrechada. Si se ponen `Roboto-*.ttf` en `tools/capturas/fonts/`, se usan solas.
- **Foto de la cabecera (`assets/hero.png`):** está sacada de la captura anterior, con el texto borrado, porque el original no está en el repo. Si se pone la foto original del Garaje con el mismo nombre, todo mejora de nitidez al regenerar.

## Aún no generadas (siguen siendo de la 2.2)
- **Móvil:** 07 ajustar pantalla, 10–11 cámara y foto del aparcamiento, 12 gasolineras, 16–23 (mapa de calor, semáforos, trayectos, vídeo, trofeos, mantenimiento, tu año) y 25 alarma.
- **Otras:** el marco amarillo del cuadro (`cuadro/01`), la historia del año (`imagenes/02`) y el fotograma real del vídeo (`imagenes/04`, una foto real).
- **Guía:** los pasos g2–g7.

Para añadir una pantalla: una función en `screens/` que devuelva HTML, y una línea en `SCREENS` de `capturas.py`.
