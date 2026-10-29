# Generador de capturas de RideScreen AA

Todas las capturas del README, `docs/CAPTURAS.md` y la web (`site/`) salen de aquí, en **español, catalán, inglés, italiano, griego y ruso a la vez**. Son recreaciones en HTML de las pantallas reales renderizadas con Chromium. No son capturas de un móvil.

Toman de la app todo lo que pueden:
- **Textos:** `strings.xml` de `app/` y `wear/` en cada idioma.
- **Colores:** `colors.xml`.
- **Fuentes:** Archivo, en `res/font`. Para griego y cirílico (que Archivo no tiene) y para el reloj, Roboto, la fuente del sistema de Android (`fonts/`, licencia OFL).
- **Iconos:** los *vector drawables*, convertidos a SVG.

Lo demás sale de un único fichero de datos de ejemplo.

**Idiomas sin traducción en la app:** cada texto que falta sale en inglés, como hace Android. En la 2.3, griego y ruso no tienen traducción (el móvil sale en inglés con números y fechas en su idioma), y el reloj solo existe en ES/CA/EN. Cuando entren las traducciones, basta con regenerar.

## Uso

```bash
pip install playwright pillow babel   # Chromium: `playwright install chromium` o CAPTURAS_CHROMIUM=/ruta/chrome
python3 tools/capturas/capturas.py            # todo (≈1 min)
python3 tools/capturas/capturas.py home w_    # solo las pantallas que empiezan así
python3 tools/capturas/capturas.py pill --lang=es,ca   # solo las píldoras, en esos idiomas
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
| `lib/demo.py` | **Datos de ejemplo canónicos** (un solo historial: viajes, trofeos, año, mantenimiento, semáforos, gasolineras) y formato de números y fechas por idioma |
| `lib/system.py` | Textos de Android, no de la app (Cancelar, ahora, Hoy…), por idioma |
| `lib/maps.py` | Mapa sintético (cuadrícula tipo Eixample) y la ruta del viaje |
| `lib/render.py` | Chromium → máster → cada tamaño publicado |
| `screens/phone.py`, `screens/watch.py` | Las pantallas |
| `screens/stats.py` | Pantallas 12 y 16–23 (gasolineras, mapa de calor, semáforos, trayectos, vídeo, trofeos, mantenimiento, tu año) |
| `screens/pills.py`, `pills/<idioma>.py` | Píldoras: plantilla y textos (ver abajo) |
| `screens/guide.py` | Guía de instalación, pasos g2–g7 |
| `lib/android_aosp.json` | Textos de Android (AOSP) en los 6 idiomas |
| `fonts/` | Roboto (latin, griego, cirílico) |
| `assets/hero.png` | Foto del Garaje de la cabecera (ver abajo) |

## Reglas

**Lienzos (máster → publicado)**
- **Móvil:** 412×915 CSS px a 2,625 (1082×2402). Se publica a 540 px en `docs/` y la web, y a 824 px en la guía.
- **Reloj:** 225×225 a 2 (450×450), redondo y con esquinas transparentes. Se publica a 360 px en `docs/` y a 300 px en la web.
- **Tarjeta para compartir:** 1080×1350, como `RideShare.kt`. Se publica a 720 px.
- **Píldoras:** 1080 px de ancho; el alto sale del contenido.
- **Formatos:**
  - PNG de color real, nunca con paleta;
  - WebP calidad 90 en la web;
  - las pantallas opacas se guardan sin canal alfa.

**Aspecto**
- Colores, fuentes, iconos y textos siempre de la app, nunca a ojo.
- Barra de estado fija: **18:42 · 78 %**.
- Tema por defecto (125X verde). Los temas por color de la 2.4 solo en su propia sección.
- Un idioma por imagen. Decimales con coma en ES, CA, IT, EL y RU y con punto en EN, con los mismos decimales que la app (el reloj usa 2 en la lista de viajes porque la app lo hace así).

**Datos de ejemplo (`lib/demo.py`)**
- La ciudad de ejemplo es Barcelona, sin nada personal:
  - piloto inventado (Álex / Àlex / Alex / Άλεξ / Алекс);
  - cuadro `ZT000000`;
  - contactos Laura y Jordi;
  - la ubicación del SMS en plaça de Catalunya.
- Cifras creíbles para una 125 por ciudad: el viaje principal es «Sants → Zona Franca · 6,8 km · 18:40 · media 22 · máx 71 km/h». Es el mismo en el móvil, el reloj, el widget y la tarjeta.
- Si cambias un dato, cámbialo aquí y regenera todo: así no se desincroniza nada.

**Honestidad**
- Lo que existe en la app es una «captura».
- Lo que aún no existe (por ejemplo, la bienvenida en el cuadro) lleva «Maqueta» o «Idea» visible en la imagen.
- El detector de caídas siempre con «(experimental)».

**Nombres:** `NN_tema.png`, iguales en todos los idiomas, cada idioma en su carpeta (ver `--list`).

## Píldoras
- Textos en `pills/<idioma>.py`, una lista por idioma en el mismo orden; el número N/total sale solo.
- Los botones de la app se escriben `[[clave]]` y salen de `strings.xml` en cada idioma (en griego y ruso, en inglés, como en el móvil). `[[w:clave]]` es un texto del reloj y `[[=texto]]` uno que no es nuestro (Android, Android Auto, ⋮). Detalle en `screens/pills.py`.
- 1–16 conservan su número; 17–21 son de la 2.3. Se publican en `docs/pildoras/<idioma>/`.
- Textos de Android y Android Auto en IT/EL/RU (permisos, menú de desarrollador): **sin verificar** en un móvil en esos idiomas. En EL/RU el menú de desarrollador de Android Auto lleva también el nombre en inglés.

## Diferencias conocidas con la app (a 23 oct 2026)
- **Cabecera del mapa del viaje:** la app muestra `6.80 km · 00:18:40 · avg 22 · max 71 km/h` en todos los idiomas (`TripMapActivity` y `TripModel.distanceText` usan `Locale.US` y «avg/max» fijos). Las capturas muestran la versión localizada. Hay que arreglarlo en la app.
- **Fuente del reloj:** Roboto, estrechada en las cifras grandes para imitar la condensada de Wear OS.
- **`GarageActivity`:** el menú de una moto («Use this bike», «Rename», «Change photo»…) está en inglés fijo en todos los idiomas (solo «Modelo de moto…» se traduce). Hay que arreglarlo en la app.
- **Semáforos y gasolineras:** nombres de avenidas públicas y precios inventados; la app los saca de OpenStreetMap y del Ministerio.
- **Foto de la cabecera (`assets/hero.png`):** está sacada de la captura anterior, con el texto borrado, porque el original no está en el repo. Si se pone la foto original del Garaje con el mismo nombre, todo mejora de nitidez al regenerar.

## Aún no generadas (siguen siendo de la 2.2)
- **Móvil:** 07 ajustar pantalla, 10–11 cámara y foto del aparcamiento y 25 alarma.
- **Vídeo del viaje (19):** se muestra la parte de abajo; la vista previa con el vídeo no se recrea.
- **Otras:** el marco amarillo del cuadro (`cuadro/01`), la historia del año (`imagenes/02`) y el fotograma real del vídeo (`imagenes/04`, una foto real).


## Textos que no son de la app (guía g2–g7 y píldoras)
- **Android** (fuentes desconocidas, permisos, «Mostrar sobre otras aplicaciones», ajustes restringidos): textos de AOSP en `lib/android_aosp.json`, en los 6 idiomas. Son los de Android puro; Samsung o Xiaomi pueden decirlo distinto.
- **Play Protect y Android Auto** (g3, g5, g6) no son de código abierto: ES/CA/EN mantienen los textos de la guía publicada; IT/EL/RU están traducidos del inglés y **sin verificar** (`screens/guide.py`).
- **g4** es nuestra pantalla de configuración (`activity_setup.xml`), con los textos de la app.
- **g7:** el QR del cuadro es inventado (nunca uno real) y el cuadro se dibuja sin sus rótulos.

Para añadir una pantalla: una función en `screens/` que devuelva HTML, y una línea en `SCREENS` de `capturas.py`.
