#!/usr/bin/env python3
"""RideScreen AA capture generator.

    python3 tools/capturas/capturas.py              # everything, ES/CA/EN/IT/EL/RU
    python3 tools/capturas/capturas.py --lang=it,el # only some languages
    python3 tools/capturas/capturas.py home trips   # only some screens
    python3 tools/capturas/capturas.py --list       # what is generated and where it goes
    python3 tools/capturas/capturas.py --dry        # write into tools/capturas/out/dry instead of the repo

See tools/capturas/README.md for the rules (canvas, data, privacy, naming).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from lib.base import PHONE_DPR, PHONE_H, PHONE_W, WATCH_DPR, WATCH_W  # noqa: E402
from lib.render import Job, render  # noqa: E402
from screens import guide, phone, pills, stats, watch  # noqa: E402

DOCS_M = "docs/screenshots/ridescreen/movil/"
DOCS_W = "docs/screenshots/ridescreen/reloj/"
DOCS_I = "docs/screenshots/ridescreen/imagenes/"
# Web folders per language.
# it/el/ru follow the pages in PR #3 (site/<lang>/guide/, site/<lang>/watch/).
WEB_IMG = {"es": "site/img/", "ca": "site/img/ca/", "en": "site/img/en/", "it": "site/img/it/",
           "el": "site/img/el/", "ru": "site/img/ru/"}
WEB_GUIDE = {"es": "site/guia/", "ca": "site/ca/guia/", "en": "site/en/guide/", "it": "site/it/guide/",
             "el": "site/el/guide/", "ru": "site/ru/guide/"}
WEB_WATCH = {"es": "site/reloj/", "ca": "site/ca/rellotge/", "en": "site/en/watch/", "it": "site/it/watch/",
             "el": "site/el/watch/", "ru": "site/ru/watch/"}
LANGS = ("es", "ca", "en", "it", "el", "ru")

PHONE_DOCS, PHONE_WEB, GUIDE = 540, 540, 824
WATCH_DOCS, WATCH_WEB = 360, 300


def _docs(lang, rel):
    return [(rel, None)] if lang == "es" else []


# name → (builder(lang), kind, outputs(lang) → [(path, width)])
SCREENS = {
    "home_stop": (lambda l: phone.home(l), "phone",
                  lambda l: [(DOCS_M + "01_inicio_parada.png", PHONE_DOCS)] if l == "es" else []),
    "home_conn": (lambda l: phone.home(l, connected=True), "phone",
                  lambda l: ([(DOCS_M + "02_inicio_conectado.png", PHONE_DOCS)] if l == "es" else [])
                  + [(WEB_IMG[l] + "inicio.webp", PHONE_WEB), (WEB_GUIDE[l] + "g9_listo.png", GUIDE)]),
    "home_sheet": (lambda l: phone.home(l, sheet=True), "phone",
                   lambda l: ([(DOCS_M + "09_aparcamiento_opciones.png", PHONE_DOCS)] if l == "es" else [])
                   + [(WEB_IMG[l] + "aparcamiento.webp", PHONE_WEB)]),
    "wifi_off": (lambda l: phone.wifi_off(l), "phone",
                 lambda l: [(DOCS_M + "05_primera_conexion_wifi_apagada.png", PHONE_DOCS)] if l == "es" else []),
    "mic": (lambda l: phone.mic(l), "phone",
            lambda l: [(DOCS_M + "06_microfono_para_la_voz.png", PHONE_DOCS)] if l == "es" else []),
    "whats_new": (lambda l: phone.whats_new(l), "whats_new",
                  lambda l: [(DOCS_M + "04_novedades_v2.3.png", PHONE_DOCS)] if l == "es" else []),
    "guide_wifi": (lambda l: phone.guide_wifi(l), "phone",
                   lambda l: [(WEB_GUIDE[l] + "g8_wifi.png", GUIDE)]),
    "help": (lambda l: phone.help_menu(l), "phone",
             lambda l: [(DOCS_M + "03_ayuda.png", PHONE_DOCS)] if l == "es" else []),
    "waiting": (lambda l: phone.waiting(l), "phone",
                lambda l: [(DOCS_M + "08_esperando_a_la_moto.png", PHONE_DOCS)] if l == "es" else []),
    "live": (lambda l: phone.live_page(l), "phone",
             lambda l: [(DOCS_M + "27_ruta_en_directo.png", PHONE_DOCS)] if l == "es" else []),
    "notifications": (lambda l: phone.notifications(l), "phone",
                      lambda l: [(DOCS_M + "28_notificaciones.png", PHONE_DOCS)] if l == "es" else []),
    "trips": (lambda l: phone.trips(l), "phone",
              lambda l: [(DOCS_M + "13_viajes.png", PHONE_DOCS)] if l == "es" else []),
    "trips_off": (lambda l: phone.trips(l, logging_off=True), "phone",
                  lambda l: [(DOCS_M + "14_viajes_registro_desactivado.png", PHONE_DOCS)] if l == "es" else []),
    "trip_map": (lambda l: phone.trip_map(l), "phone",
                 lambda l: ([(DOCS_M + "15_viaje_por_velocidad.png", PHONE_DOCS)] if l == "es" else [])
                 + [(WEB_IMG[l] + "viaje.webp", PHONE_WEB)]),
    "crash": (lambda l: phone.crash(l), "phone",
              lambda l: ([(DOCS_M + "24_detector_de_caidas.png", PHONE_DOCS)] if l == "es" else [])
              + [(WEB_IMG[l] + "caidas.webp", PHONE_WEB)]),
    "sms": (lambda l: phone.sms(l), "phone",
            lambda l: [(DOCS_M + "26_sms_al_contacto.png", PHONE_DOCS)] if l == "es" else []),
    "widget": (lambda l: phone.widget(l), "widget",
               lambda l: [(DOCS_M + "29_widget.png", PHONE_DOCS)] if l == "es" else []),
    "guide_download": (lambda l: phone.guide_download(l), "phone",
                       lambda l: [(WEB_GUIDE[l] + "g1_descarga.png", GUIDE)]),
    "guide_g2": (lambda l: guide.unknown_sources(l), "phone", lambda l: [(WEB_GUIDE[l] + "g2_fuentes.png", GUIDE)]),
    "guide_g3": (lambda l: guide.play_protect(l), "phone", lambda l: [(WEB_GUIDE[l] + "g3_playprotect.png", GUIDE)]),
    "guide_g4": (lambda l: guide.setup_permissions(l), "phone", lambda l: [(WEB_GUIDE[l] + "g4_permisos.png", GUIDE)]),
    "guide_g5": (lambda l: guide.aa_version(l), "phone", lambda l: [(WEB_GUIDE[l] + "g5_aa_version.png", GUIDE)]),
    "guide_g6": (lambda l: guide.aa_server(l), "phone", lambda l: [(WEB_GUIDE[l] + "g6_aa_servidor.png", GUIDE)]),
    "guide_g7": (lambda l: guide.scan_qr(l), "phone", lambda l: [(WEB_GUIDE[l] + "g7_qr.png", GUIDE)]),
    "fuel": (lambda l: stats.fuel(l), "phone",
             lambda l: ([(DOCS_M + "12_gasolineras.png", PHONE_DOCS)] if l == "es" else [])
             + [(WEB_IMG[l] + "gasolineras.webp", PHONE_WEB)]),
    # 2.3 screens 16–23 (screens/stats.py)
    "heat": (lambda l: stats.heat_map(l), "phone",
             lambda l: [(DOCS_M + "16_mapa_de_calor.png", PHONE_DOCS)] if l == "es" else []),
    "lights": (lambda l: stats.lights(l), "phone",
               lambda l: ([(DOCS_M + "17_semaforos.png", PHONE_DOCS)] if l == "es" else [])
               + [(WEB_IMG[l] + "semaforos.webp", PHONE_WEB)]),
    "routes": (lambda l: stats.routes(l), "phone",
               lambda l: [(DOCS_M + "18_trayectos.png", PHONE_DOCS)] if l == "es" else []),
    "video": (lambda l: stats.video(l), "phone",
              lambda l: [(DOCS_M + "19_video_del_viaje.png", PHONE_DOCS)] if l == "es" else []),
    "trophies": (lambda l: stats.trophies(l), "phone",
                 lambda l: ([(DOCS_M + "20_trofeos.png", PHONE_DOCS)] if l == "es" else [])
                 + [(WEB_IMG[l] + "trofeos.webp", PHONE_WEB)]),
    "trophies_next": (lambda l: stats.trophies_next(l), "phone",
                      lambda l: [(DOCS_M + "21_trofeos_proximos.png", PHONE_DOCS)] if l == "es" else []),
    "maint": (lambda l: stats.maintenance(l), "phone",
              lambda l: ([(DOCS_M + "22_mantenimiento.png", PHONE_DOCS)] if l == "es" else [])
              + [(WEB_IMG[l] + "mantenimiento.webp", PHONE_WEB)]),
    "year": (lambda l: stats.year(l), "phone",
             lambda l: [(DOCS_M + "23_tu_ano_en_moto.png", PHONE_DOCS)] if l == "es" else []),
    "share": (lambda l: phone.share_card(l), "share",
              lambda l: [(DOCS_I + "01_compartir_viaje.png", 720)] if l == "es" else []),
    # Watch
    "w_find": (lambda l: watch.find_bike(l), "watch",
               lambda l: ([(DOCS_W + "01_brujula_hacia_tu_moto.png", WATCH_DOCS)] if l == "es" else [])
               + [(WEB_WATCH[l] + "r_brujula.png", WATCH_WEB), (WEB_IMG[l] + "reloj-brujula.webp", WATCH_WEB)]),
    "w_last": (lambda l: watch.last_ride(l), "watch",
               lambda l: ([(DOCS_W + "02_tu_moto_en_reposo.png", WATCH_DOCS)] if l == "es" else [])
               + [(WEB_WATCH[l] + "r_reposo.png", WATCH_WEB)]),
    "w_turn": (lambda l: watch.turn(l), "watch",
               lambda l: ([(DOCS_W + "03_aviso_de_giro.png", WATCH_DOCS)] if l == "es" else [])
               + [(WEB_WATCH[l] + "r_giro.png", WATCH_WEB), (WEB_IMG[l] + "reloj-giro.webp", WATCH_WEB)]),
    "w_tile": (lambda l: watch.tile(l), "watch",
               lambda l: ([(DOCS_W + "04_tile.png", WATCH_DOCS)] if l == "es" else [])
               + [(WEB_WATCH[l] + "r_tile.png", WATCH_WEB)]),
    "w_trophy": (lambda l: watch.trophy(l), "watch",
                 lambda l: [(DOCS_W + "05_trofeo.png", WATCH_DOCS)] if l == "es" else []),
    "w_pad": (lambda l: watch.pad(l), "watch",
              lambda l: ([(DOCS_W + "06_cruceta_sin_bisel.png", WATCH_DOCS)] if l == "es" else [])
              + [(WEB_WATCH[l] + "r_cruceta.png", WATCH_WEB)]),
    "w_crash": (lambda l: watch.crash(l), "watch",
                lambda l: ([(DOCS_W + "07_alarma_de_caida.png", WATCH_DOCS)] if l == "es" else [])
                + [(WEB_WATCH[l] + "r_caida.png", WATCH_WEB)]),
    "w_ride": (lambda l: watch.ride(l), "watch",
               lambda l: ([(DOCS_W + "08_viaje_en_curso.png", WATCH_DOCS)] if l == "es" else [])
               + [(WEB_WATCH[l] + "r_viaje.png", WATCH_WEB)]),
    "w_info": (lambda l: watch.info(l), "watch",
               lambda l: ([(DOCS_W + "09_informacion.png", WATCH_DOCS)] if l == "es" else [])
               + [(WEB_WATCH[l] + "r_info.png", WATCH_WEB)]),
    "w_trips": (lambda l: watch.trips(l), "watch",
                lambda l: ([(DOCS_W + "10_viajes.png", WATCH_DOCS)] if l == "es" else [])
                + [(WEB_WATCH[l] + "r_viajes.png", WATCH_WEB)]),
    "w_map": (lambda l: watch.trip_map(l), "watch",
              lambda l: [(DOCS_W + "11_mapa.png", WATCH_DOCS)] if l == "es" else []),
}

# Pills: 1080 px wide, height from the content; one folder per language, same file names.
PILLS_DIR = "docs/pildoras/"


def _has_pills(lang):
    try:
        pills.texts(lang)
        return True
    except ModuleNotFoundError:
        return False


for _n, _slug in enumerate(pills.slugs(), 1):
    SCREENS[f"pill{_n:02d}"] = ((lambda n: lambda l: pills.pill(l, n))(_n), "pill",
                                (lambda slug: lambda l: [(f"{PILLS_DIR}{l}/{slug}.png", None)] if _has_pills(l) else [])(_slug))

CANVAS = {
    "phone": (PHONE_W, PHONE_H, PHONE_DPR),
    "watch": (WATCH_W, WATCH_W, WATCH_DPR),
    "share": (phone.SHARE_W, phone.SHARE_H, 1),
    "widget": (phone.WIDGET_W, phone.WIDGET_H, PHONE_DPR),
    "whats_new": (PHONE_W, phone.WHATS_NEW_H, PHONE_DPR),
    "pill": (pills.W, None, 1),
}


def jobs(names=None, langs=LANGS):
    out = []
    for name, (build, kind, outputs) in SCREENS.items():
        if names and not any(name.startswith(n) for n in names):
            continue
        w, h, dpr = CANVAS[kind]
        for lang in langs:
            o = outputs(lang)
            if o:   # build only what has an output (pills without texts in a language are skipped)
                out.append(Job(f"{name}_{lang}", build(lang), w, h, dpr, o))
    return out


def main(argv):
    dry = "--dry" in argv
    names = [a for a in argv if not a.startswith("--")]
    langs = LANGS
    for a in argv:
        if a.startswith("--lang="):
            langs = tuple(a.split("=", 1)[1].split(","))
    js = jobs(names, langs)
    if "--list" in argv:
        for j in js:
            for rel, wpx in j.outputs:
                print(f"{j.name:22s} → {rel} ({wpx or 'master'} px)")
        return
    for rel, size in render(js, dry=dry):
        print(f"{size[0]}×{size[1]}  {rel}")


if __name__ == "__main__":
    main(sys.argv[1:])
