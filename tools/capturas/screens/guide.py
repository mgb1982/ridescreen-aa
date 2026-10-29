"""Install guide, steps g2–g7, in every language.

Where each text comes from:
- RideScreen AA's own screens (g4, labels in the notes): the app's strings.xml.
- Android itself (g2: unknown sources): AOSP strings in lib/android_aosp.json (stock wording; Samsung,
  Xiaomi… may differ).
- Play Protect (g3) and Android Auto (g5, g6) are not open source. ES/CA/EN keep the texts of the published
  2.2 guide; IT/EL/RU are translated from English and NOT verified on a phone (see UNVERIFIED).
- The notes under each picture are ours: ES/CA/EN as published, IT/EL/RU translated.
"""
import json
import os

from lib.base import phone_page
from lib.res import Strings, esc

AOSP = json.load(open(os.path.join(os.path.dirname(__file__), "..", "lib", "android_aosp.json"), encoding="utf-8"))
UNVERIFIED = {"it", "el", "ru"}   # Play Protect / Android Auto wording in these languages


def aosp(lang, key):
    return AOSP[lang][key]


PLAY_PROTECT = {  # title, body, more details, install anyway, OK
    "es": ("App no segura bloqueada", "Play Protect no reconoce al desarrollador de esta app. Las apps de "
           "desarrolladores desconocidos a veces pueden ser poco seguras.", "Más detalles", "Instalar de todas formas",
           "Aceptar"),
    "ca": ("App no segura bloquejada", "Play Protect no reconeix el desenvolupador d'aquesta app. Les apps de "
           "desenvolupadors desconeguts poden ser poc segures.", "Més detalls", "Instal·la igualment", "D'acord"),
    "en": ("Unsafe app blocked", "Play Protect doesn't recognise this app's developer. Apps from unknown developers "
           "can sometimes be unsafe.", "More details", "Install anyway", "OK"),
    "it": ("App non sicura bloccata", "Play Protect non riconosce lo sviluppatore di questa app. Le app di "
           "sviluppatori sconosciuti a volte possono non essere sicure.", "Altri dettagli", "Installa comunque", "OK"),
    "el": ("Η μη ασφαλής εφαρμογή αποκλείστηκε", "Το Play Protect δεν αναγνωρίζει τον προγραμματιστή αυτής της "
           "εφαρμογής. Οι εφαρμογές από άγνωστους προγραμματιστές μερικές φορές δεν είναι ασφαλείς.",
           "Περισσότερες λεπτομέρειες", "Εγκατάσταση ούτως ή άλλως", "OK"),
    "ru": ("Небезопасное приложение заблокировано", "Play Защита не может определить разработчика этого "
           "приложения. Приложения от неизвестных разработчиков иногда бывают небезопасными.", "Подробнее",
           "Всё равно установить", "ОК"),
}

ANDROID_AUTO = {  # privacy, help, about, version, toast, dev settings, start HU server, quit dev mode
    "es": ("Ajustes de privacidad", "Ayuda y comentarios", "Acerca de", "Versión",
           "¿Activar ajustes de desarrollador? → <b>Aceptar</b>", "Ajustes para desarrolladores",
           "Iniciar servidor de unidad principal", "Salir del modo de desarrollador"),
    "ca": ("Configuració de privadesa", "Ajuda i suggeriments", "Quant a", "Versió",
           "Vols activar la configuració de desenvolupador? → <b>D'acord</b>", "Configuració per a desenvolupadors",
           "Inicia el servidor d'unitat principal", "Surt del mode de desenvolupador"),
    "en": ("Privacy settings", "Help & feedback", "About", "Version", "Enable developer settings? → <b>OK</b>",
           "Developer settings", "Start head unit server", "Quit developer mode"),
    "it": ("Impostazioni privacy", "Guida e feedback", "Informazioni", "Versione",
           "Attivare le impostazioni sviluppatore? → <b>OK</b>", "Impostazioni sviluppatore",
           "Avvia server unità principale", "Esci dalla modalità sviluppatore"),
    "el": ("Ρυθμίσεις απορρήτου", "Βοήθεια και σχόλια", "Πληροφορίες", "Έκδοση",
           "Ενεργοποίηση ρυθμίσεων προγραμματιστή; → <b>OK</b>", "Ρυθμίσεις προγραμματιστή",
           "Έναρξη διακομιστή κύριας μονάδας", "Έξοδος από τη λειτουργία προγραμματιστή"),
    "ru": ("Настройки конфиденциальности", "Справка и отзывы", "О приложении", "Версия",
           "Включить настройки для разработчиков? → <b>ОК</b>", "Настройки для разработчиков",
           "Запустить сервер головного устройства", "Выйти из режима разработчика"),
}
AA_VERSION = "17.7.663654-release"

# Notes under each picture. {open_aa}, {scan}, {more} are the app's own labels in that language.
NOTES = {
    "g3": {
        "es": "Sale porque la app aún no está en Play Store. Toca <b>Más detalles</b> y luego <b>Instalar de todas "
              "formas</b>.<br><br>No pulses \"Aceptar\": cancela la instalación.",
        "ca": "Surt perquè l'app encara no és a Play Store. Toca <b>Més detalls</b> i després <b>Instal·la "
              "igualment</b>.<br><br>No premis \"D'acord\": cancel·la la instal·lació.",
        "en": "It shows up because the app isn't on the Play Store yet. Tap <b>More details</b> and then <b>Install "
              "anyway</b>.<br><br>Don't tap \"OK\": it cancels the install.",
        "it": "Compare perché l'app non è ancora sul Play Store. Tocca <b>Altri dettagli</b> e poi <b>Installa "
              "comunque</b>.<br><br>Non toccare \"OK\": annulla l'installazione.",
        "el": "Εμφανίζεται επειδή η εφαρμογή δεν είναι ακόμα στο Play Store. Πάτησε <b>Περισσότερες λεπτομέρειες</b> "
              "και μετά <b>Εγκατάσταση ούτως ή άλλως</b>.<br><br>Μην πατήσεις \"OK\": ακυρώνει την εγκατάσταση.",
        "ru": "Появляется, потому что приложения ещё нет в Play Маркете. Нажми <b>Подробнее</b>, а потом <b>Всё равно "
              "установить</b>.<br><br>Не нажимай \"ОК\": установка отменится.",
    },
    "g4": {
        "es": "Acepta todos los permisos que pida. La <b>ubicación</b> la exige Android para conectar al Wi‑Fi de la "
              "moto: la app no te rastrea.",
        "ca": "Accepta tots els permisos que demani. La <b>ubicació</b> l'exigeix Android per connectar a la Wi‑Fi de "
              "la moto: l'app no et segueix.",
        "en": "Accept every permission it asks for. Android requires <b>location</b> to join the bike's Wi‑Fi: the "
              "app doesn't track you.",
        "it": "Accetta tutti i permessi che chiede. La <b>posizione</b> la richiede Android per collegarsi al Wi‑Fi "
              "della moto: l'app non ti traccia.",
        "el": "Αποδέξου όλες τις άδειες που ζητά. Την <b>τοποθεσία</b> τη ζητά το Android για να συνδεθεί στο Wi‑Fi "
              "της μηχανής: η εφαρμογή δεν σε παρακολουθεί.",
        "ru": "Разреши всё, что попросит приложение. <b>Геолокацию</b> требует Android, чтобы подключиться к Wi‑Fi "
              "мотоцикла: приложение тебя не отслеживает.",
    },
    "g5": {
        "es": "Abre <b>Android Auto</b> (Ajustes del móvil → Android Auto, o desde RideScreen: <i>{open_aa}</i>), baja "
              "del todo y toca <b>Versión</b> 10 veces seguidas.",
        "ca": "Obre <b>Android Auto</b> (Configuració del mòbil → Android Auto, o des de RideScreen: <i>{open_aa}</i>), "
              "baixa del tot i toca <b>Versió</b> 10 vegades seguides.",
        "en": "Open <b>Android Auto</b> (phone Settings → Android Auto, or from RideScreen: <i>{open_aa}</i>), scroll "
              "to the bottom and tap <b>Version</b> 10 times in a row.",
        "it": "Apri <b>Android Auto</b> (Impostazioni del telefono → Android Auto, oppure da RideScreen: "
              "<i>{open_aa}</i>), scorri fino in fondo e tocca <b>Versione</b> 10 volte di fila.",
        "el": "Άνοιξε το <b>Android Auto</b> (Ρυθμίσεις κινητού → Android Auto, ή από το RideScreen: <i>{open_aa}</i>), "
              "κατέβα μέχρι κάτω και πάτησε <b>Έκδοση</b> 10 φορές στη σειρά.",
        "ru": "Открой <b>Android Auto</b> (Настройки телефона → Android Auto или из RideScreen: <i>{open_aa}</i>), "
              "пролистай в самый низ и нажми <b>Версия</b> 10 раз подряд.",
    },
    "g6": {
        "es": "Toca ⋮ (arriba a la derecha) → <b>Iniciar servidor de unidad principal</b>.<br><br>⚠️ Se apaga cada vez "
              "que <b>reinicias el móvil</b> o se actualiza Android Auto: repite solo este paso.",
        "ca": "Toca ⋮ (a dalt a la dreta) → <b>Inicia el servidor d'unitat principal</b>.<br><br>⚠️ S'apaga cada cop "
              "que <b>reinicies el mòbil</b> o s'actualitza Android Auto: repeteix només aquest pas.",
        "en": "Tap ⋮ (top right) → <b>Start head unit server</b>.<br><br>⚠️ It turns off every time you <b>restart "
              "the phone</b> or Android Auto updates: just repeat this step.",
        "it": "Tocca ⋮ (in alto a destra) → <b>Avvia server unità principale</b>.<br><br>⚠️ Si spegne ogni volta che "
              "<b>riavvii il telefono</b> o Android Auto si aggiorna: ripeti solo questo passo.",
        "el": "Πάτησε ⋮ (πάνω δεξιά) → <b>Έναρξη διακομιστή κύριας μονάδας</b> (Start head unit server).<br><br>⚠️ "
              "Σβήνει κάθε φορά που <b>κάνεις επανεκκίνηση στο κινητό</b> ή ενημερώνεται το Android Auto: επανάλαβε "
              "μόνο αυτό το βήμα.",
        "ru": "Нажми ⋮ (вверху справа) → <b>Запустить сервер головного устройства</b> (Start head unit server).<br><br>"
              "⚠️ Он выключается при каждой <b>перезагрузке телефона</b> или обновлении Android Auto: повтори только "
              "этот шаг.",
    },
    "g7": {
        "es": "1. Moto encendida y parada.<br>2. En el cuadro, cambia a la <b>interfaz 3</b> (proyección del móvil), "
              "la que muestra el <b>QR</b>.<br>3. En RideScreen toca <b>{scan}</b> (abajo, en <b>{more}</b>) y "
              "apunta.<br><br>⚠️ El QR de <b>Información</b> no es el bueno.",
        "ca": "1. Moto engegada i parada.<br>2. Al quadre, canvia a la <b>interfície 3</b> (projecció del mòbil), la "
              "que mostra el <b>QR</b>.<br>3. A RideScreen toca <b>{scan}</b> (a baix, a <b>{more}</b>) i apunta."
              "<br><br>⚠️ El QR d'<b>Informació</b> no és el bo.",
        "en": "1. Bike on and parked.<br>2. On the dash, switch to <b>screen 3</b> (phone projection), the one showing "
              "the <b>QR</b>.<br>3. In RideScreen tap <b>{scan}</b> (at the bottom, under <b>{more}</b>) and point."
              "<br><br>⚠️ The QR under <b>Information</b> is not the right one.",
        "it": "1. Moto accesa e ferma.<br>2. Sul cruscotto passa all'<b>interfaccia 3</b> (proiezione del telefono), "
              "quella con il <b>QR</b>.<br>3. In RideScreen tocca <b>{scan}</b> (in basso, in <b>{more}</b>) e "
              "inquadra.<br><br>⚠️ Il QR di <b>Informazioni</b> non è quello giusto.",
        "el": "1. Μηχανή αναμμένη και σταματημένη.<br>2. Στο ταμπλό, πήγαινε στη <b>διεπαφή 3</b> (προβολή κινητού), "
              "αυτή που δείχνει το <b>QR</b>.<br>3. Στο RideScreen πάτησε <b>{scan}</b> (κάτω, στο <b>{more}</b>) και "
              "σημάδεψε.<br><br>⚠️ Το QR των <b>Πληροφοριών</b> δεν είναι το σωστό.",
        "ru": "1. Мотоцикл включён и стоит.<br>2. На панели переключись на <b>интерфейс 3</b> (проекция телефона), "
              "где показан <b>QR</b>.<br>3. В RideScreen нажми <b>{scan}</b> (внизу, в разделе <b>{more}</b>) и "
              "наведи камеру.<br><br>⚠️ QR из раздела <b>Информация</b> не подходит.",
    },
}
POINT_AT_QR = {"es": "Apunta la cámara al QR del cuadro", "ca": "Apunta la càmera al QR del quadre",
               "en": "Point the camera at the dash QR", "it": "Inquadra il QR del cruscotto",
               "el": "Σημάδεψε με την κάμερα το QR του ταμπλό", "ru": "Наведи камеру на QR на панели"}

CSS = """
.sys{position:absolute;inset:0;background:#0B0D0B}
.nt{position:absolute;left:20px;right:20px;background:#1E2A21;border-radius:22px;padding:16px 18px;
  font-size:15px;line-height:1.5}
.hl{outline:3px solid var(--gold);outline-offset:3px;box-shadow:0 0 0 9px rgba(209,169,85,.25)}
.dlg{position:absolute;left:20px;right:20px;background:#2B2F2C;border-radius:28px;padding:24px 22px 16px;
  font-family:'Roboto','Noto Color Emoji',sans-serif;color:#E2E3DE}
.dlg h3{font-weight:400;font-size:22px;line-height:1.3}
.dlg p{font-size:14px;line-height:1.45;color:#C3C8C0;margin-top:12px}
.bts{display:flex;justify-content:flex-end;gap:8px;margin-top:18px;font-weight:500;font-size:14px;color:#A8D5BA;
  align-items:center}
.bts span{padding:8px 12px;border-radius:999px}
.aa{position:relative;font-family:'Roboto',sans-serif;color:#E2E3DE}
.aa h2{font-weight:400;font-size:22px;padding:42px 20px 14px}
.aa .it{padding:14px 20px;font-size:15px;border-bottom:1px solid #2A2E2B}
.aa .it small{display:block;font-size:12px;color:#9AA096;margin-top:2px}
"""


def _page(body):
    return phone_page(body, bg="#0B0D0B", extra_css=CSS)


def unknown_sources(lang):
    """g2 · Android (PackageInstaller + Settings): install unknown apps from Chrome."""
    a = lambda k: esc(aosp(lang, k))
    body = (f'<div class="sys"></div>'
            f'<div class="dlg" style="top:230px"><h3>Chrome</h3><p>{a("untrusted_external_source_warning")}</p>'
            f'<div class="bts"><span>{a("cancel")}</span><span class="hl">{a("external_sources_settings")}</span></div></div>'
            f'<div class="aa" style="position:absolute;left:0;right:0;top:540px;background:#1A1D1B;border-radius:24px 24px 0 0;'
            f'bottom:0"><div style="font-size:20px;padding:22px 18px 14px">{a("install_other_apps")}</div>'
            f'<div style="display:flex;align-items:center;padding:4px 18px"><div style="flex:1;font-size:15px">'
            f'{a("external_source_switch_title")}<small style="display:block;font-size:12px;color:#9AA096">Chrome</small></div>'
            f'<span class="hl" style="width:52px;height:32px;border-radius:999px;background:#A8D5BA;position:relative;'
            f'border-radius:999px"><i style="position:absolute;right:5px;top:5px;width:22px;height:22px;border-radius:50%;'
            f'background:#0E3A24"></i></span></div></div>')
    return _page(body)


def play_protect(lang):
    """g3 · Play Protect (Google, not open source)."""
    title, text, more, install, ok = PLAY_PROTECT[lang]
    body = (f'<div class="sys"></div><div class="dlg" style="top:210px"><div style="font-size:30px">🛡️</div>'
            f'<h3 style="margin-top:10px">{esc(title)}</h3><p>{esc(text)}</p>'
            f'<div style="color:#A8D5BA;font-size:14px;font-weight:500;margin-top:16px">▾ {esc(more)}</div>'
            f'<div class="hl" style="margin-top:12px;padding:10px 14px;border-radius:14px;font-size:14px">{esc(install)}</div>'
            f'<div class="bts"><span>{esc(ok)}</span></div></div>'
            f'<div class="nt" style="top:600px">{NOTES["g3"][lang]}</div>')
    return _page(body)


def setup_permissions(lang):
    """g4 · RideScreen AA → ⚙️ → Configuration (activity_setup.xml, first card), before granting."""
    s = Strings(lang)
    css = """
.su{position:absolute;inset:30px 0 0;background:var(--bg);padding:0 16px}
.su h1{font-size:26px;font-weight:800;margin-top:8px;line-height:1.2}
.su .ds{font-size:14px;color:var(--text2);margin:4px 0 12px;line-height:1.35}
.cd{background:var(--surface);border:1px solid var(--outline);border-radius:22px;padding:20px}
.lb{font-size:12px;letter-spacing:.96px;color:var(--text2);text-transform:uppercase}
.st{font-size:16px;font-weight:800;margin-top:14px}
.sd{font-size:13px;color:var(--text2);margin-top:2px;line-height:1.35}
.tb{display:inline-flex;align-items:center;min-height:40px;border-radius:14px;background:var(--surface-high);
  padding:0 16px;font-size:14px;font-weight:500;margin-top:6px;white-space:nowrap}
.tx{display:inline-block;line-height:40px;padding:0 12px;font-size:14px;font-weight:500;color:var(--gold);
  white-space:nowrap;overflow:hidden;text-overflow:ellipsis;min-width:0}
"""
    body = (f'<div class="su"><h1>{esc(s("setup_setup_settings"))}</h1>'
            f'<div class="ds">{esc(s("setup_a_one_time_setup_so_android_auto_can_project_to_"))}</div>'
            f'<div class="cd"><div class="lb">{esc(s("setup_get_started"))}</div>'
            f'<div class="st" style="margin-top:12px">✓ {esc(s("setup_1_android_auto"))}</div>'
            f'<div class="sd">{esc(s("setup_android_auto_runs_maps_waze_opencfmoto_relays_it"))}</div>'
            f'<span class="tb">{esc(s("setup_open_android_auto"))}</span>'
            f'<div class="st">• {esc(s("setup_2_permissions"))}</div>'
            f'<div class="sd">{esc(s("setup_camera_scan_the_dash_qr_location_join_the_bike_w"))}</div>'
            f'<div style="display:flex;align-items:center;gap:4px;margin-top:6px">'
            f'<span class="tb hl" style="margin-top:0">{esc(s("setup_grant_permissions"))}</span>'
            f'<span class="tx">{esc(s("setup_app_settings"))}</span></div>'
            f'<div class="st">• {esc(s("setup_3_head_unit_mode_in_android_auto"))}</div>'
            f'<div class="sd">{esc(s("setup_in_android_auto_settings_tap_version_10_times_to"))}</div></div></div>'
            f'<div class="nt" style="top:700px;z-index:5">{NOTES["g4"][lang]}</div>')
    return phone_page(body, extra_css=CSS + css)


def _aa_list(lang):
    privacy, help_, about, version = ANDROID_AUTO[lang][:4]
    rows = f'<div class="it">{esc(privacy)}</div><div class="it">{esc(help_)}</div><div class="it">{esc(about)}</div>'
    return rows, version


def aa_version(lang):
    """g5 · Android Auto settings, bottom: tap Version 10 times."""
    s = Strings(lang)
    rows, version = _aa_list(lang)
    toast = ANDROID_AUTO[lang][4]
    body = (f'<div class="sys"></div><div class="aa"><h2>Android Auto</h2>{rows}'
            f'<div class="it hl" style="display:flex;align-items:center;margin:6px 4px;border-radius:12px">'
            f'<div style="flex:1">{esc(version)}<small>{AA_VERSION}</small></div>'
            f'<span style="background:var(--gold);color:var(--on-brand);font-family:\'ArchivoBlack\';border-radius:999px;'
            f'padding:4px 12px;font-size:16px">× 10</span></div></div>'
            f'<div style="position:absolute;left:60px;right:60px;top:500px;background:#2B2F2C;border-radius:999px;'
            f'padding:12px 16px;text-align:center;font-family:Roboto,sans-serif;font-size:13.5px;color:#E2E3DE">{toast}</div>'
            f'<div class="nt" style="top:600px">{NOTES["g5"][lang].replace("{open_aa}", esc(s("setup_open_android_auto")))}</div>')
    return _page(body)


def aa_server(lang):
    """g6 · Android Auto ⋮ menu: Start head unit server."""
    version, _toast, dev, start, quit_ = ANDROID_AUTO[lang][3:]
    rows, _ = _aa_list(lang)
    body = (f'<div class="sys"></div><div class="aa"><h2>Android Auto</h2>{rows}'
            f'<div class="it">{esc(version)}<small>{AA_VERSION}</small></div></div>'
            f'<div class="aa" style="position:absolute;top:36px;right:8px;width:250px;background:#2B2F2C;border-radius:8px;'
            f'padding:6px 0;box-shadow:0 6px 20px rgba(0,0,0,.5)">'
            f'<div style="padding:12px 16px;font-size:14.5px">{esc(dev)}</div>'
            f'<div class="hl" style="padding:12px 16px;font-size:14.5px;font-weight:700;background:#3A403B">{esc(start)}</div>'
            f'<div style="padding:12px 16px;font-size:14.5px">{esc(quit_)}</div></div>'
            f'<div class="nt" style="top:540px">{NOTES["g6"][lang]}</div>')
    return _page(body)


def scan_qr(lang):
    """g7 · Scanning the dash QR (interface 3). The dash is drawn without its own labels."""
    s = Strings(lang)
    import random
    qr = []
    rnd = random.Random(7)   # a made-up pattern: never a real dash QR
    for y in range(21):
        for x in range(21):
            finder = (x < 7 and y < 7) or (x > 13 and y < 7) or (x < 7 and y > 13)
            on = ((x in (0, 6) or y in (0, 6) or (2 <= x <= 4 and 2 <= y <= 4)) if finder and x < 7 and y < 7 else
                  ((x in (14, 20) or y in (0, 6) or (16 <= x <= 18 and 2 <= y <= 4)) if finder and x > 13 else
                   ((x in (0, 6) or y in (14, 20) or (2 <= x <= 4 and 16 <= y <= 18)) if finder else rnd.random() < .45)))
            if on:
                qr.append(f'<rect x="{x}" y="{y}" width="1" height="1"/>')
    svg = f'<svg viewBox="-1 -1 23 23" width="132" height="132" style="background:#fff;display:block"><g fill="#000">{"".join(qr)}</g></svg>'
    note = NOTES["g7"][lang].replace("{scan}", esc(s("main_scan"))).replace("{more}", esc(s("main_more")))
    body = (f'<div class="sys" style="background:linear-gradient(#141615,#0B0D0B)"></div>'
            f'<div style="position:absolute;left:58px;right:58px;top:150px;height:190px;background:#000;border-radius:14px;'
            f'display:flex;align-items:center;justify-content:center">'
            f'<div class="hl" style="padding:6px;background:#fff;border-radius:12px">{svg}</div></div>'
            f'<div style="position:absolute;left:0;right:0;top:370px;text-align:center;font-weight:700;font-size:16px">'
            f'{esc(POINT_AT_QR[lang])}</div>'
            f'<div class="nt" style="top:450px">{note}</div>')
    return _page(body)
