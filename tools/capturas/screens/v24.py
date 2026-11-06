"""Pantallas nuevas de la 2.4 (RC prerelease/v24-rc-slim, 14f054c), recreadas desde el código y los textos:

- key_menu        PkeUnlockUi.pickAction: lista Bloquear / desbloquear sobre el inicio
- widget_home     ControlWidget (4×2 y 5×1) en una pantalla de inicio de Android
- obd_intro       ObdProbeUi.confirm: el Lector OBD antes de empezar (adaptador «OBDII»)
- dash_picker     ControlsActivity: selector de estilo con las 3 vistas previas reales (DashboardMode.preview,
                  renderizadas con Robolectric: assets/dash24rc/)
- dash_scale      ControlsActivity: escala del velocímetro (lista de una opción)
- trip_obd        TripMapActivity con el resumen OBD y la reproducción del viaje
- pulse           HeartRateStore.show: ❤️ Pulso (reloj)
"""
import base64
import os

from lib import demo as D
from lib.base import phone_page
from lib.maps import svg as map_svg
from lib.res import Strings, esc, icon
from lib.system import sys_text
from screens.phone import AVG_MAX, HERO, LAUNCHER, _over_home

ASSETS = os.path.join(os.path.dirname(__file__), "..", "assets")
OK = {"es": "Aceptar", "ca": "D'acord", "en": "OK", "it": "OK", "el": "OK", "ru": "ОК"}  # android.R.string.ok


def _png(path):
    return "data:image/png;base64," + base64.b64encode(open(path, "rb").read()).decode()


def _list_dialog(lang, title, items, buttons, top=150, extra=""):
    rows = "".join(f'<div style="font-size:16px;padding:13px 2px">{x}</div>' for x in items)
    btns = "".join(f"<span>{esc(b)}</span>" for b in buttons)
    return _over_home(lang, f'<div class="dim"></div><div class="dlg" style="top:{top}px"><h3>{esc(title)}</h3>'
                            f'{rows}{extra}<div class="bt">{btns}</div></div>')


def key_menu(lang):
    s = Strings(lang)
    items = [esc(s(k)) for k in ("pke_unlock_action_unlock", "pke_unlock_action_lock", "pke_unlock_action_oilbox",
                                 "pke_unlock_action_cushion", "pke_unlock_on_connect_off")]
    return _list_dialog(lang, s("pke_unlock_title"), items, [s("pke_unlock_change_mcu")], top=190)


def obd_intro(lang):
    s = Strings(lang)
    msg = s("obd_intro", "OBDII")
    return _over_home(lang, f'<div class="dim"></div><div class="dlg" style="top:170px"><h3>{esc(s("obd_title"))}</h3>'
                            f'<div class="m">{esc(msg)}</div><div class="bt"><span>{esc(s("dash_cancel"))}</span>'
                            f'<span>{esc(s("obd_start"))}</span></div></div>')


def dash_picker(lang, theme="night"):
    s = Strings(lang)
    cards = ""
    for i, d in enumerate(("sport", "racing", "touring")):
        sel = i == 0
        img = _png(os.path.join(ASSETS, "dash24rc", f"{d}-{theme}-140-10000.png"))
        cards += (f'<div style="background:#2A362D;border-radius:12px;padding:10px 12px;margin-bottom:10px;'
                  f'border:{2 if sel else 1}px solid {"var(--gold)" if sel else "rgba(241,244,236,.2)"}">'
                  f'<div style="font-weight:700;font-size:16px">{"✓&nbsp; " if sel else ""}{esc(s("dash_design_" + d))}</div>'
                  f'<img src="{img}" style="display:block;width:100%;height:auto;margin-top:6px"></div>')
    return _over_home(lang, f'<div class="dim"></div><div class="dlg" style="top:40px;padding:22px 12px 10px">'
                            f'<h3 style="padding:0 12px">{esc(s("controls_dash_design"))}</h3>{cards}'
                            f'<div class="bt"><span>{esc(s("dash_cancel"))}</span></div></div>')


def dash_scale(lang, chosen=140):
    s = Strings(lang)
    rows = ""
    for v in (120, 140, 160, 200, 240):
        on = v == chosen
        dot = (f'<i style="width:20px;height:20px;border-radius:50%;border:2px solid {"var(--gold)" if on else "#AEBAAA"};'
               f'display:flex;align-items:center;justify-content:center;flex:none">'
               + ('<b style="width:10px;height:10px;border-radius:50%;background:var(--gold)"></b>' if on else "") + "</i>")
        rows += f'<div style="display:flex;align-items:center;gap:20px;font-size:16px;padding:12px 2px">{dot}{v} km/h</div>'
    return _over_home(lang, f'<div class="dim"></div><div class="dlg" style="top:220px"><h3>{esc(s("controls_dash_scale_speed"))}</h3>'
                            f'{rows}<div class="bt"><span>{esc(s("dash_cancel"))}</span></div></div>')


def pulse(lang):
    s = Strings(lang)
    return _over_home(lang, f'<div class="dim"></div><div class="dlg" style="top:300px"><h3>{esc(s("help_heart_rate"))}</h3>'
                            f'<div class="m">78 BPM\n\n{esc(s("heart_rate_live"))}</div>'
                            f'<div class="bt"><span>{OK[lang]}</span></div></div>')


def trip_obd(lang):
    s = Strings(lang)
    t = D.TRIP
    avg, mx = AVG_MAX[lang]
    dec = "." if lang == "en" else ","
    stats = f'{D.km(lang, t["km"])} · {D.dur(t["secs"], hours=True)} · {avg} {t["avg"]} · {mx} {t["max"]} km/h'
    obd = s("trip_obd_summary", 2214, "8920", "97", f"23{dec}4", f"13{dec}8")
    status = s("trip_replay_status", "18:31:12", 46, "6150", "94", "38")
    css = """
.mh{position:absolute;left:0;right:0;top:0;padding:40px 16px 10px 20px;background:var(--bg);z-index:5}
.mh h1{font-family:'ArchivoBlack';font-weight:400;font-size:22px}
.mh p{font-size:13px;color:var(--text2);line-height:1.4;margin-top:4px}
.mh .b{display:flex;gap:8px;margin-top:10px}
.mh .b span{border-radius:999px;background:var(--surface);padding:9px 16px;font-size:14px}
.pb{display:flex;align-items:center;gap:12px;margin-top:10px}
.pb .bt{border-radius:999px;background:var(--gold);color:var(--on-brand);padding:9px 16px;font-size:14px;font-weight:600;flex:none}
.pb .sk{flex:1;height:4px;border-radius:2px;background:rgba(241,244,236,.25);position:relative}
.pb .sk:before{content:"";position:absolute;left:0;top:0;bottom:0;width:58%;background:var(--gold);border-radius:2px}
.pb .sk:after{content:"";position:absolute;left:58%;top:-7px;width:18px;height:18px;margin-left:-9px;border-radius:50%;background:var(--gold)}
.st{font-size:12px;color:var(--text2);margin-top:6px}
.mp{position:absolute;left:0;right:0;top:268px;bottom:0}
"""
    body = (f'<div class="mh"><h1>{esc(t["name"][lang])}</h1><p>{esc(stats)}<br>{esc(s("map_speed_legend"))}<br>{esc(obd)}</p>'
            f'<div class="b"><span>{esc(s("tv_button"))}</span><span>{esc(s("share_button"))}</span></div>'
            f'<div class="pb"><span class="bt">{esc(s("trip_replay_pause"))}</span><div class="sk"></div></div>'
            f'<div class="st">{esc(status)}</div></div>'
            f'<div class="mp">{map_svg(412, 647, "light", "main", speed_colours=True, line_w=6, pad=0.15)}</div>')
    return phone_page(body, extra_css=css)


def widget_home(lang):
    """A phone home screen with the 4×2 widget and the 5×1 strip (drawn as ControlWidget.kt draws them)."""
    from lanzamiento24.widget import widget
    s = Strings(lang)
    photo = HERO
    big = widget(s, photo, lang, 372, 186, 1)
    small = widget(s, photo, lang, 372, 66, 1, compact=True)
    apps = [("#4285F4", "Maps"), ("#25D366", "WhatsApp"), (None, "RideScreen"), ("#E9A23B", "Spotify")]
    icons = "".join(
        f'<div style="display:flex;flex-direction:column;align-items:center;gap:6px;font-size:12.5px;color:#fff">'
        + (f'<img src="{LAUNCHER}" style="width:54px;height:54px;border-radius:50%">' if c is None
           else f'<div style="width:54px;height:54px;border-radius:50%;background:{c}"></div>')
        + f"<span>{n}</span></div>" for c, n in apps)
    css = """
.hs{position:absolute;inset:0;background:linear-gradient(170deg,#2E3A46,#1C232B 70%)}
.clk{position:absolute;left:24px;top:64px;color:#fff;font-size:64px;font-weight:300;line-height:1}
.dt{position:absolute;left:26px;top:138px;color:#E6EAF0;font-size:15px}
.w1{position:absolute;left:20px;top:200px}.w2{position:absolute;left:20px;top:410px}
.ap{position:absolute;left:20px;right:20px;bottom:120px;display:flex;justify-content:space-around}
.dk{position:absolute;left:24px;right:24px;bottom:34px;height:56px;border-radius:999px;background:rgba(255,255,255,.14)}
"""
    body = (f'<div class="hs"></div><div class="clk">18:42</div>'
            f'<div class="w1">{big}</div><div class="w2">{small}</div><div class="ap">{icons}</div><div class="dk"></div>')
    return phone_page(body, bg="#1C232B", extra_css=css)
