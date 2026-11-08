"""Imágenes de lanzamiento de la 2.4 para mensajes (WhatsApp/Telegram/redes), 1080×1350:
collage1 (llave y OBD), collage2 (cuadro, viajes y mandos) y pildora (cómo activar los comandos de la llave).

Textos: notas de la release v2.4 (published body) y la web capturas/v2.4. Nombres de botones y menús:
cadenas de la app en la release (RS_APP_RES = res de d06d51e), con [[clave]]. Si un idioma no tiene una
cadena, se ve la inglesa y queda anotada en lib.res.FALLBACKS (se imprime al final).

    RS_APP_RES=<res de v2.4> python3 tools/capturas/lanzamiento24/mensajes.py [es ca en it th]

Escribe en out/dry/lanzamiento24/mensajes/. Nada de McuID, PKE, MAC ni SSID: los valores salen como ••••.
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
from lib.render import Job, render  # noqa: E402
from lib.res import FALLBACKS, Strings, esc, icon  # noqa: E402
from lanzamiento24.render import ASSETS, CSS, _b64, thai_css  # noqa: E402
from lib.base import root_css  # noqa: E402
from lanzamiento24.widget import widget  # noqa: E402
from screens.phone import DIALOG_CSS  # noqa: E402
from screens.pills import markup  # noqa: E402

SITE_IMG = os.environ.get("RS_SITE_IMG", "/home/claude/web24/site/img")
MASK = "••••…••••"

M = {
"es": dict(
    tag="Ya disponible",
    c1_k="Novedades 2.4", c1_t="La llave, en el móvil",
    c1_s="Siempre con confirmación. Nunca con la moto en marcha.",
    wid="Widget «[[widget_control_label]]»", opt="Opcional, apagado por defecto", qr="McuID con la cámara",
    obd_ro="Solo lectura · adaptador ELM327 Bluetooth", obd_codes="Códigos de avería",
    obd_live=["RPM", "km/h", "°C", "Acelerador"], obd_live_t="En directo",
    c2_k="Novedades 2.4", c2_t="Tu cuadro, tus viajes",
    dash_t="Dashboard en el cuadro", dash_s="Escala de las esferas a tu moto",
    trips_t="Viajes", trips=["Vídeo estilo «Gauges»", "Reproducción con datos del OBD", "Fusionar recorridos"],
    pads_t="Mandos", pads_s="▲/▼ para el volumen, ideal con intercomunicador",
    watch_t="Reloj", watch_s="cada 5 s",
    lang_t="10 idiomas completos", bikes_t="Motos nuevas", bikes=["Zontes 350D", "Zontes 350E"],
    p_k="Paso a paso", p_t="Los comandos de la llave",
    p1="[[help_pill]] → 🔒 [[pke_unlock_title]]",
    p2="Escanea el QR de Información del cuadro: la app guarda la clave de tu moto",
    p3="Confirma la clave, que se muestra enmascarada",
    p4="Prueba a desbloquear con la moto parada",
    p4b="Siempre con confirmación, nunca en marcha",
    p_ft="Opcional: widget «[[widget_control_label]]» y «{UOC}» (apagado por defecto)",
),
"ca": dict(
    tag="Ja disponible",
    c1_k="Novetats 2.4", c1_t="La clau, al mòbil",
    c1_s="Sempre amb confirmació. Mai amb la moto en marxa.",
    wid="Widget «[[widget_control_label]]»", opt="Opcional, desactivat per defecte", qr="McuID amb la càmera",
    obd_ro="Només lectura · adaptador ELM327 Bluetooth", obd_codes="Codis d’avaria",
    obd_live=["RPM", "km/h", "°C", "Accelerador"], obd_live_t="En directe",
    c2_k="Novetats 2.4", c2_t="El teu quadre, els teus viatges",
    dash_t="Dashboard al quadre", dash_s="Escala de les esferes a la teva moto",
    trips_t="Viatges", trips=["Vídeo estil «Gauges»", "Reproducció amb dades de l’OBD", "Fusionar recorreguts"],
    pads_t="Comandaments", pads_s="▲/▼ per al volum, ideal amb intercomunicador",
    watch_t="Rellotge", watch_s="cada 5 s",
    lang_t="10 idiomes complets", bikes_t="Motos noves", bikes=["Zontes 350D", "Zontes 350E"],
    p_k="Pas a pas", p_t="Les ordres de la clau",
    p1="[[help_pill]] → 🔒 [[pke_unlock_title]]",
    p2="Escaneja el QR d’Informació del quadre: l’app desa la clau de la teva moto",
    p3="Confirma la clau, que es mostra emmascarada",
    p4="Prova de desbloquejar amb la moto aturada",
    p4b="Sempre amb confirmació, mai en marxa",
    p_ft="Opcional: widget «[[widget_control_label]]» i «{UOC}» (desactivat per defecte)",
),
"en": dict(
    tag="Out now",
    c1_k="New in 2.4", c1_t="Your key, on your phone",
    c1_s="Always with confirmation. Never while the bike is moving.",
    wid="«[[widget_control_label]]» widget", opt="Optional, off by default", qr="McuID with the camera",
    obd_ro="Read only · ELM327 Bluetooth adapter", obd_codes="Fault codes",
    obd_live=["RPM", "km/h", "°C", "Throttle"], obd_live_t="Live",
    c2_k="New in 2.4", c2_t="Your dash, your rides",
    dash_t="Dashboard on the dash", dash_s="Gauge scale to suit your bike",
    trips_t="Rides", trips=["«Gauges» video style", "Replay with OBD data", "Merge rides"],
    pads_t="Controls", pads_s="▲/▼ for volume, great with an intercom",
    watch_t="Watch", watch_s="every 5 s",
    lang_t="10 full languages", bikes_t="New bikes", bikes=["Zontes 350D", "Zontes 350E"],
    p_k="Step by step", p_t="Set up the key commands",
    p1="[[help_pill]] → 🔒 [[pke_unlock_title]]",
    p2="Scan the dash’s Information QR: the app saves your bike’s key",
    p3="Confirm the key, shown masked",
    p4="Try unlocking with the bike stopped",
    p4b="Always confirmed, never while moving",
    p_ft="Optional: «[[widget_control_label]]» widget and «{UOC}» (off by default)",
),
"it": dict(
    tag="Disponibile ora",
    c1_k="Novità 2.4", c1_t="La chiave, nel telefono",
    c1_s="Sempre con conferma. Mai con la moto in movimento.",
    wid="Widget «[[widget_control_label]]»", opt="Opzionale, disattivato di default", qr="McuID con la fotocamera",
    obd_ro="Sola lettura · adattatore ELM327 Bluetooth", obd_codes="Codici di errore",
    obd_live=["Giri", "km/h", "°C", "Acceleratore"], obd_live_t="In diretta",
    c2_k="Novità 2.4", c2_t="Il tuo cruscotto, i tuoi viaggi",
    dash_t="Dashboard sul cruscotto", dash_s="Scala dei quadranti adatta alla tua moto",
    trips_t="Viaggi", trips=["Video in stile «Gauges»", "Riproduzione con i dati OBD", "Unire i percorsi"],
    pads_t="Comandi", pads_s="▲/▼ per il volume, ideale con l’interfono",
    watch_t="Orologio", watch_s="ogni 5 s",
    lang_t="10 lingue complete", bikes_t="Nuove moto", bikes=["Zontes 350D", "Zontes 350E"],
    p_k="Passo dopo passo", p_t="I comandi della chiave",
    p1="[[help_pill]] → 🔒 [[pke_unlock_title]]",
    p2="Scansiona il QR di Informazioni del cruscotto: l’app salva la chiave della tua moto",
    p3="Conferma la chiave, mostrata mascherata",
    p4="Prova a sbloccare con la moto ferma",
    p4b="Sempre con conferma, mai in movimento",
    p_ft="Opzionale: widget «[[widget_control_label]]» e «{UOC}» (disattivato di default)",
),
"th": dict(
    tag="พร้อมให้ใช้แล้ว",
    c1_k="ใหม่ใน 2.4", c1_t="กุญแจอยู่ในมือถือ",
    c1_s="ยืนยันทุกครั้ง ไม่ทำงานขณะรถวิ่ง",
    wid="วิดเจ็ต «[[widget_control_label]]»", opt="เลือกได้ ปิดไว้ตั้งต้น", qr="อ่าน McuID ด้วยกล้อง",
    obd_ro="อ่านอย่างเดียว · อะแดปเตอร์ ELM327 Bluetooth", obd_codes="รหัสข้อผิดพลาด",
    obd_live=["รอบ", "กม./ชม.", "°C", "คันเร่ง"], obd_live_t="แบบสด",
    c2_k="ใหม่ใน 2.4", c2_t="หน้าปัดของคุณ ทริปของคุณ",
    dash_t="แดชบอร์ดบนหน้าปัด", dash_s="ปรับสเกลหน้าปัดตามรถของคุณ",
    trips_t="ทริป", trips=["วิดีโอสไตล์ «Gauges»", "เล่นซ้ำพร้อมข้อมูล OBD", "รวมเส้นทาง"],
    pads_t="ปุ่มควบคุม", pads_s="▲/▼ ปรับระดับเสียง เหมาะกับอินเตอร์คอม",
    watch_t="นาฬิกา", watch_s="ทุก 5 วินาที",
    lang_t="ครบ 10 ภาษา", bikes_t="รถรุ่นใหม่", bikes=["Zontes 350D", "Zontes 350E"],
    p_k="ทีละขั้นตอน", p_t="ตั้งค่าคำสั่งกุญแจ",
    p1="[[help_pill]] → 🔒 [[pke_unlock_title]]",
    p2="สแกน QR ในหน้า Information ของหน้าปัด แอปจะบันทึกคีย์ของรถคุณ",
    p3="ยืนยันคีย์ ซึ่งแสดงแบบปิดบังไว้",
    p4="ลองปลดล็อกขณะรถจอดอยู่",
    p4b="ยืนยันทุกครั้ง ไม่ทำงานขณะรถวิ่ง",
    p_ft="เลือกได้: วิดเจ็ต «[[widget_control_label]]» และ «{UOC}» (ปิดไว้ตั้งต้น)",
),
}
LANG_CHIPS = ["Español", "Català", "English", "Italiano", "Français", "Deutsch", "Português", "Ελληνικά", "Русский", "ไทย"]

X = DIALOG_CSS + """
.sl{padding:72px 72px 0}
.k{margin-top:40px}
h1{font-size:72px;line-height:1.05}
html[lang=th] h1{line-height:1.32;font-size:64px}
html[lang=th] .s,html[lang=th] .lb,html[lang=th] .st{line-height:1.5}
.s{font-size:30px;margin-top:16px}
.tile{background:var(--surface);border:2px solid var(--outline);border-radius:30px;overflow:hidden}
.lb{font-size:30px;font-weight:700;line-height:1.25}
.lb small{display:block;font-size:28px;font-weight:600;color:var(--text2);margin-top:4px;line-height:1.3}
.pad{padding:22px 26px}
.pke{margin-top:26px;padding:22px 28px}
.pke .btns{margin-top:4px}.pke .btn{font-size:28px;padding:18px 4px 14px}
.row2{display:grid;grid-template-columns:1fr 1fr;gap:18px;margin-top:18px}
.chipx{display:inline-block;background:var(--surface-high);border-radius:14px;padding:8px 14px;font-size:28px;
  font-weight:600;color:var(--text);margin-top:12px;line-height:1.3}
.chipx.g{background:var(--gold);color:var(--on-brand)}
.obd{margin-top:18px;padding:22px 28px}
.obd .live{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-top:14px}
.obd .live div{background:var(--surface-high);border-radius:18px;padding:12px 8px 10px;text-align:center;font-size:28px;
  color:var(--text2);font-weight:600}
.obd .live b{display:block;font-family:'ArchivoBlack','NotoThai';font-weight:400;font-size:46px;color:var(--live);line-height:1.15}
.wcap{font-size:28px;font-weight:700;color:var(--text2);margin:20px 0 10px}
.ph2{display:grid;grid-template-columns:1fr 1fr;gap:6px}
.ph2 div{position:relative}
.ph2 img{display:block;width:100%;height:250px;object-fit:cover}
.ph2 span{position:absolute;left:12px;bottom:10px;background:rgba(15,21,17,.85);border-radius:10px;padding:4px 12px;
  font-size:28px;font-weight:700}
.g2{display:grid;grid-template-columns:1fr 1fr;gap:18px;margin-top:18px}
.g2 .tile{padding:22px 24px;min-height:0}
.ic{font-size:44px;line-height:1;margin-bottom:10px}
ul.t{list-style:none;margin-top:6px}
ul.t li{font-size:28px;line-height:1.3;color:var(--text2);padding-left:22px;position:relative;margin-top:6px}
ul.t li:before{content:"";position:absolute;left:2px;top:14px;width:10px;height:10px;border-radius:50%;background:var(--gold)}
.lchips{display:flex;flex-wrap:wrap;gap:6px;margin-top:10px}
.lchips span{border:2px solid var(--outline);border-radius:999px;padding:2px 10px;font-size:20px;font-weight:600}
/* pill */
.steps{margin-top:26px;display:flex;flex-direction:column;gap:16px}
.step{display:grid;grid-template-columns:72px 1fr 400px;gap:20px;align-items:center;background:var(--surface);
  border:2px solid var(--outline);border-radius:28px;padding:16px 18px 16px 18px;height:212px;overflow:hidden}
.num{width:64px;height:64px;border-radius:50%;background:var(--gold);color:var(--on-brand);display:flex;
  align-items:center;justify-content:center;font-family:'ArchivoBlack';font-size:38px;align-self:start;margin-top:6px}
.step .tx{font-size:30px;font-weight:700;line-height:1.28}
.step .tx small{display:block;font-size:28px;font-weight:600;color:var(--text2);margin-top:6px;line-height:1.3}
html[lang=th] .step .tx{line-height:1.45}
.shot{height:180px;border-radius:18px;overflow:hidden;position:relative;background:rgba(0,0,0,.55)}
.shot .dlg{position:absolute;left:10px;right:10px;padding:16px 18px 6px;border-radius:20px}
.shot .dlg h3{font-size:19px;margin-bottom:8px}
.shot .dlg .m{font-size:15px}
.shot .r{font-size:17px;padding:8px 2px;color:#DCE3D8}
.shot .r.hl{background:rgba(209,169,85,.22);outline:3px solid var(--gold);border-radius:10px;padding:8px 10px;margin:0 -8px;color:#fff}
.shot .bt{margin-top:8px;gap:16px;font-size:13px}
.shot .bt span.hl{outline:3px solid var(--gold);border-radius:8px;padding:4px 8px;margin:-4px -8px}
.pft{margin-top:20px;font-size:28px;line-height:1.35;color:var(--text2);border-left:6px solid var(--gold);padding-left:18px}
html[lang=th] .pft{line-height:1.5}

.ph2 img{height:210px}
.g2 .tile{padding:18px 22px}
.g2 .lb{font-size:29px}
.lchips span{font-size:19px;padding:1px 9px}
.bk{display:flex;gap:8px;flex-wrap:wrap;margin-top:6px}.bk .chipx{margin-top:0;font-size:28px;padding:4px 12px}
.pl h1{font-size:60px}
html[lang=th] .k,html[lang=th] .pke .h,html[lang=th] .top{letter-spacing:0}
.c1 .k{margin-top:30px}.c1 .pke{margin-top:20px;padding:18px 26px}.c1 .pke .btn{padding:14px 4px 12px}
.c1 .wcap{margin:16px 0 8px}.c1 .row2,.c1 .obd{margin-top:14px}.c1 .pad{padding:18px 24px}
.c1 .obd{padding:18px 26px}.c1 .obd .live{margin-top:10px}.c1 .obd .live div{padding:8px 6px 6px}
html[lang=th] .c1 .s{margin-top:8px}
.col{display:flex;flex-direction:column;gap:18px}.col .tile{flex:1}
.wide{display:grid;grid-template-columns:1.25fr 1fr;gap:20px;margin-top:18px;padding:18px 22px}
.wide .bk{flex-direction:column;align-items:flex-start;gap:8px;margin-top:10px}
html[lang=th] .pl h1{font-size:56px}
.pl .s{font-size:28px;margin-top:10px;line-height:1.35}
.pl .steps{margin-top:18px;gap:14px}
.pl .step{height:186px;grid-template-columns:64px 1fr 360px;gap:16px;padding:12px 14px}
.pl .num{width:58px;height:58px;font-size:34px}
.pl .shot{height:160px}
.pl .pft{margin-top:16px}
"""


def page(lang, body, tag):
    icon_svg = _b64(os.path.join(ASSETS, "icon.svg"), "image/svg+xml")
    top = f'<div class="top"><img src="{icon_svg}">RideScreen AA 2.4 · {esc(tag)}</div>'
    ft = '<div class="ft"><b>ridescreen.app</b><span>@ridescreen.app</span></div>'
    return (f'<!doctype html><html lang="{lang}"><head><meta charset="utf-8"><style>{root_css()}{thai_css()}{CSS}{X}'
            f'</style></head><body><div class="sl">{top}{body}{ft}</div></body></html>')


def _webp(name):
    return _b64(os.path.join(SITE_IMG, name), "image/webp")


def collage1(lang):
    s, m = Strings(lang), M[lang]
    g = "var(--gold)"
    lab = lambda k: esc(s(k).rstrip("."))  # noqa: E731
    btn = lambda i, k: f'<div class="btn">{icon(i, g, 46)}{lab(k)}</div>'  # noqa: E731
    volts = "12.6 V" if lang in ("en", "th") else "12,6 V"
    pke = (f'<div class="card pke"><div class="h">{icon("ic_line_power", "var(--text2)", 26)}{lab("pke_card_title")}'
           f'<span style="margin-left:auto">{volts} · {lab("pke_locked")}</span></div><div class="btns">'
           f'{btn("ic_line_unlock", "pke_short_unlock")}{btn("ic_line_lock", "pke_short_lock")}'
           f'{btn("ic_line_seat", "pke_short_seat")}{btn("ic_line_fuel", "pke_short_fuel")}</div></div>')
    photo = _webp("dashboard-cuadro-2.webp")
    wid = f'<div class="wcap">{markup(s, m["wid"], chips=False)}</div>{widget(s, photo, lang, 330, 48, 936 / 330, compact=True)}'
    opts = (f'<div class="row2"><div class="tile pad"><div class="lb">{markup(s, "[[pke_unlock_on_connect_on]]", chips=False)}'
            f'<small>{esc(m["opt"])}</small></div></div>'
            f'<div class="tile pad"><div class="lb">{esc(m["qr"])}</div>'
            f'<span class="chipx g">{markup(s, "[[pke_unlock_scan]]", chips=False)}</span></div></div>')
    vals = ["4220", "33", "85", "12 %"]
    obd = (f'<div class="tile obd"><div class="lb">{markup(s, "[[obd_entry]]", chips=False)}'
           f'<small>{esc(m["obd_ro"])} · {esc(m["obd_codes"])}</small></div><div class="live">'
           + "".join(f"<div><b>{v}</b>{esc(n)}</div>" for v, n in zip(vals, m["obd_live"])) + "</div></div>")
    body = (f'<div class="c1"><div class="k">{esc(m["c1_k"])}</div><h1>{esc(m["c1_t"])}</h1><div class="s">{esc(m["c1_s"])}</div>'
            f'{pke}{wid}{opts}{obd}</div>')
    return page(lang, body, m["tag"])


def collage2(lang):
    s, m = Strings(lang), M[lang]
    dash = (f'<div class="tile" style="margin-top:24px"><div class="ph2">'
            f'<div><img src="{_webp("dashboard-cuadro-1.webp")}"><span>{lab(s, "dash_design_racing")}</span></div>'
            f'<div><img src="{_webp("dashboard-cuadro-2.webp")}"><span>{lab(s, "dash_design_touring")}</span></div></div>'
            f'<div class="pad"><div class="lb">{esc(m["dash_t"])}<small>'
            f'{lab(s, "dash_design_sport")} · {lab(s, "dash_design_racing")} · {lab(s, "dash_design_touring")} · '
            f'{lab(s, "controls_day")} / {lab(s, "controls_night")} · {esc(m["dash_s"])}</small></div></div></div>')
    trips = (f'<div class="tile"><div class="lb">🏍️ {esc(m["trips_t"])}</div><ul class="t">'
             + "".join(f"<li>{esc(x)}</li>" for x in m["trips"]) + "</ul></div>")
    pads = (f'<div class="tile"><div class="lb">🎮 {esc(m["pads_t"])}'
            f'<small>{esc(m["pads_s"])}</small></div></div>')
    watch = (f'<div class="tile"><div class="lb">⌚ {esc(m["watch_t"])}'
             f'<small>{markup(s, "[[help_heart_rate]]", chips=False)} · {esc(m["watch_s"])}</small></div></div>')
    more = (f'<div class="tile wide"><div><div class="lb">🌍 {esc(m["lang_t"])}</div><div class="lchips">'
            + "".join(f"<span>{esc(x)}</span>" for x in LANG_CHIPS)
            + f'</div></div><div><div class="lb">{esc(m["bikes_t"])}</div><div class="bk">'
            + "".join(f'<span class="chipx">{esc(b)}</span>' for b in m["bikes"]) + "</div></div></div>")
    body = (f'<div class="k">{esc(m["c2_k"])}</div><h1 style="font-size:55px;white-space:nowrap">{esc(m["c2_t"])}</h1>{dash}'
            f'<div class="g2">{trips}<div class="col">{pads}{watch}</div></div>{more}')
    return page(lang, body, m["tag"])


def uoc(s):
    """«Unlock when connecting» as the app names it: pke_unlock_on_connect_off without the box and the state."""
    return re.sub(r"^[☐☑]\s*", "", s("pke_unlock_on_connect_off")).rsplit(":", 1)[0].strip()


def lab(s, k):
    return esc(s(k).rstrip("."))


def _dlg(title, inner, buttons, top=None):
    bt = "".join(f'<span class="{"hl" if hl else ""}">{esc(b)}</span>' for b, hl in buttons)
    pos = f"top:{top}px" if top is not None else "bottom:8px"
    return (f'<div class="shot"><div class="dlg" style="{pos}">'
            + (f"<h3>{esc(title)}</h3>" if title else "") + f'{inner}<div class="bt">{bt}</div></div></div>')


def pildora(lang):
    s, m = Strings(lang), M[lang]
    # 1 · Help list (MainActivity.helpExtras order, tail), Lock / unlock highlighted.
    rows = "".join(f'<div class="r{" hl" if k == "pke_unlock_entry" else ""}">{esc(s(k))}</div>'
                   for k in ("pke_entry", "pke_unlock_entry"))
    shot1 = _dlg(s("help_title"), rows, [], 14)
    # 2 · McuID dialog (PkeUnlockUi.askMcuId) with Scan QR highlighted.
    shot2 = _dlg(s("pke_unlock_mcu_title"),
                 '<div style="border-bottom:2px solid var(--gold);height:30px;margin:4px 0 2px"></div>',
                 [(s("pke_unlock_scan"), True), (s("dash_cancel"), False), (s("dest_save"), False)], 26)
    # 3 · PkeMcuScanActivity confirmation, McuID and PKE code masked.
    msg = s("pke_unlock_scan_confirm", MASK).replace("\n\n", "\n") + "\n" + s("pke_unlock_scan_code_too", "••••")
    shot3 = _dlg("", f'<div class="m">{esc(msg)}</div>', [(s("dest_save"), True)])
    # 4 · PkeUnlockUi.confirm for Unlock.
    un = s("pke_unlock_action_unlock")
    shot4 = _dlg(s("pke_unlock_confirm_title", un), "",
                 [(s("dash_cancel"), False), (s("pke_unlock_confirm_yes", un), True)], 34)
    steps = [(markup(s, m["p1"], chips=False), "", shot1),
             (esc(m["p2"]), "", shot2), (esc(m["p3"]), "", shot3), (esc(m["p4"]), esc(m["p4b"]), shot4)]
    body = (f'<div class="pl"><div class="k">{esc(m["p_k"])}</div><h1>{esc(m["p_t"])}</h1>'
            f'<div class="s">{markup(s, "[[pke_unlock_need_code]]", chips=False)}</div><div class="steps">'
            + "".join(f'<div class="step"><div class="num">{i}</div><div class="tx">{t}'
                      + (f"<small>{sm}</small>" if sm else "") + f"</div>{shot}</div>"
                      for i, (t, sm, shot) in enumerate(steps, 1))
            + f'</div><div class="pft">{markup(s, m["p_ft"], chips=False).replace("{UOC}", esc(uoc(s)))}</div></div>')
    return page(lang, body, m["tag"])


def jobs(lang):
    return [Job(f"msg24_{n}_{lang}", f(lang), 1080, 1350, 1, [(f"lanzamiento24/mensajes/{n}-{lang}.png", None)])
            for n, f in (("collage1", collage1), ("collage2", collage2), ("pildora", pildora))]


if __name__ == "__main__":
    langs = sys.argv[1:] or ["es", "ca", "en", "it", "th"]
    for r, size in render([j for L in langs for j in jobs(L)], dry=True):
        print(size, r)
    print("ENGLISH FALLBACKS:", sorted(FALLBACKS))
