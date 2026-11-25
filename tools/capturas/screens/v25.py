"""Pantallas nuevas de la 2.5 (RC11, prerelease/v25-rc11-slim), recreadas desde los layouts y los textos del RC11:

- settings_hub    SettingsHubActivity + activity_settings_hub.xml + row_settings_section.xml (Ayuda primero, 8 secciones)
- cuadro          HudViewActivity + activity_hud_view.xml: selector Android Auto / Dashboard / Mapa / Espejo y la vista previa
- dash_window     ControlsActivity, sección «Dashboard», diseño «Android Auto + datos»: fila y diálogo «ocultar la barra inferior»
- home_dash       MainActivity: la tarjeta «Dashboard» del Inicio (vista previa, nombre del estilo y un solo botón)

Lo que no se puede recrear fielmente lleva «Maqueta» en la imagen (ver README, sección 2.5).
"""
from lib import demo as D
from lib import demo_v25 as V
from lib.base import phone_page
from lib.res import Strings, esc, icon
from screens.phone import CSS as HOME_CSS
from screens.phone import dash_preview, home

OK = {"es": "Aceptar", "ca": "D'acord", "en": "OK", "it": "OK", "el": "OK", "ru": "ОК", "de": "OK", "fr": "OK",
      "pt": "OK", "th": "ตกลง"}  # android.R.string.ok

# Material card of the settings screens: Card.OpenCfMoto (22 dp, 1 dp outline, 20 dp padding)
CARD = """
.mc{background:var(--surface);border:1px solid var(--outline);border-radius:22px;padding:20px}
.h1{font-size:26px;font-weight:700;line-height:1.2}
.seg{display:flex;align-items:center;justify-content:center;background:var(--surface-high);color:var(--text);
  border-radius:12px;min-height:44px;padding:0 12px;font-size:12px;font-weight:500;text-align:center;line-height:1.25}
.seg.on{background:var(--gold);color:var(--on-brand)}
.fil{display:flex;align-items:center;justify-content:center;background:var(--gold);color:var(--on-brand);border-radius:999px;
  min-height:40px;padding:0 24px;font-size:14px;font-weight:500;text-align:center}
"""


# ---------------------------------------------------------------- Settings hub

HUB_CSS = CARD + """
.hub{position:absolute;inset:0;padding:30px 16px 24px;overflow:hidden}
.hub .h1{margin:8px 0 8px}
.row{margin-top:10px;display:flex;align-items:center;min-height:104px;padding:20px;box-sizing:border-box}
.row .ic{width:44px;text-align:center;font-size:28px;line-height:1.2;flex:none}
.row .tx{flex:1;margin-left:12px;min-width:0}
.row .t{font-size:17px;font-weight:700;line-height:1.25}
.row .s{font-size:13px;color:var(--text2);margin-top:2px;line-height:1.3}
.row .ch{font-size:24px;color:var(--text2);margin-left:8px}
"""

# SettingsSection.icon (SettingsHubActivity.kt) and the strings behind titleRes / subtitleRes
SECTIONS = [("🔗", "connection"), ("🎯", "display"), ("📊", "dashboard"), ("🎮", "handlebar"), ("🛣️", "route"),
            ("⌚", "watch"), ("🔒", "privacy"), ("🛠️", "advanced")]


def settings_hub(lang):
    s = Strings(lang)
    rows = [("❓", s("settings_help"), s("settings_help_sub"))]
    rows += [(ic, s(f"settings_sec_{k}"), s(f"settings_sec_{k}_sub")) for ic, k in SECTIONS]
    cards = "".join(f'<div class="mc row"><div class="ic">{ic}</div><div class="tx"><div class="t">{esc(t)}</div>'
                    f'<div class="s">{esc(sub)}</div></div><div class="ch">›</div></div>' for ic, t, sub in rows)
    body = f'<div class="hub"><div class="h1">{esc(s("settings_hub_title"))}</div>{cards}</div>'
    return phone_page(body, extra_css=HUB_CSS)


# ---------------------------------------------------------------- Cuadro (Dash view)

CUADRO_CSS = """
.hv{position:absolute;inset:0;background:#000;display:flex;flex-direction:column}
.tb{background:rgba(0,0,0,.4);padding:36px 8px 6px;display:flex;align-items:center}
.gb{display:flex;align-items:center;justify-content:center;background:var(--gold);color:var(--on-brand);border-radius:999px;
  height:44px;min-width:44px;padding:0 12px;font-size:18px;font-weight:500;flex:none}
.tt{color:#fff;font-size:16px;font-weight:700;margin:0 8px;flex:none}
.tg{flex:1;display:flex;margin:0 0 0 0;min-width:0}
.tg span{flex:1;height:44px;display:flex;align-items:center;justify-content:center;text-align:center;font-size:12px;font-weight:500;
  line-height:1.15;color:var(--gold);border:1px solid var(--outline);overflow-wrap:anywhere;overflow:hidden;margin-left:-1px;padding:0 4px;min-width:0}
.tg span:first-child{border-radius:22px 0 0 22px;margin-left:0}
.tg span:last-child{border-radius:0 22px 22px 0}
.tg span.on{background:var(--surface-high)}
.tg span.dis{opacity:.38}
.fs{margin-left:4px}
.stage{flex:1;position:relative;display:flex;flex-direction:column;justify-content:center}
.stage img{display:block;width:100%;height:auto}
.stage.off{padding:12px;align-items:center}
.stage.off img{width:100%}
.note{color:#fff;font-size:14px;text-align:center;margin-top:8px;padding:0 12px}
.offb{display:flex;width:100%;margin-top:8px;gap:8px}
.offb .gb{flex:1;height:48px;border-radius:999px}
.mq{position:absolute;left:12px;top:10px;border:1px solid var(--gold);color:var(--gold);border-radius:999px;padding:3px 10px;
  font-size:12px;font-weight:600;background:rgba(0,0,0,.55)}
.ctl{background:rgba(0,0,0,.6);padding:0 8px 8px}
.ph{height:44px;display:flex;align-items:center;justify-content:center;color:#fff;font-size:14px;font-weight:500}
"""


def cuadro(lang, connected=True):
    """HudViewActivity. connected=True: all four modes enabled, Dashboard lit, the stage mirrors the dash (the live video is
    approximated with the dashboard preview and marked «Maqueta»). connected=False: the offline state the app really draws."""
    s = Strings(lang)
    modes = [("cuadro_mode_aa", "aa"), ("cuadro_mode_dashboard", "dash"), ("cuadro_mode_map", "map"), ("main_mirror", "mirror")]
    sel = "dash" if connected else "aa"
    seg = "".join(f'<span class="{"on" if k == sel else ""}{" dis" if (not connected and k in ("aa", "dash")) else ""}">{esc(s(key))}</span>'
                  for key, k in modes)
    top = (f'<div class="tb"><span class="gb">{s("cuadro_back_glyph")}</span><span class="tt">{esc(s("cuadro_title"))}</span>'
           f'<div class="tg">{seg}</div><span class="gb fs">{s("hud_str_3")}</span></div>')
    img = f'<img src="{dash_preview(lang, V.HOME_DASH["design"], V.HOME_DASH["theme"])}">'
    if connected:
        stage = f'<div class="stage">{img}<span class="mq">Maqueta</span></div>'
    else:
        stage = (f'<div class="stage off">{img}<div class="note">{esc(s("cuadro_offline_notice"))}</div>'
                 f'<div class="offb"><span class="gb">{esc(s("main_connect"))}</span>'
                 f'<span class="gb">{esc(s("cuadro_map_preview"))}</span></div></div>')
    ctl = f'<div class="ctl"><div class="ph">{esc(s("cuadro_pad_handle_closed"))}</div></div>'
    body = f'<div class="hv">{top}{stage}{ctl}</div>'
    return phone_page(body, extra_css=CUADRO_CSS, bg="#000")


# ---------------------------------------------------------------- Dashboard settings: «hide bottom bar» row + dialog

DW_CSS = CARD + """
.cs{position:absolute;inset:0;padding:30px 16px 24px;overflow:hidden}
.cs .h1{margin:8px 0 12px}
.mc.dc{margin-top:12px}
.cap{font-size:12px;letter-spacing:.96px;text-transform:uppercase;color:var(--text2)}
.dsc{font-size:13px;color:var(--text2);margin-top:8px;line-height:1.35}
.fl{display:flex;align-items:center;justify-content:center;background:var(--gold);color:var(--on-brand);border-radius:999px;
  min-height:40px;font-size:14px;font-weight:500;margin-top:8px;text-align:center;padding:0 24px}
.sr{display:flex;margin-top:8px}
.sr .seg{flex:1;margin:0 4px}
.sr .seg:first-child{margin-left:0}
.sr .seg:last-child{margin-right:0}
.mc .seg.f{margin-top:4px}
.dim{position:absolute;inset:0;background:rgba(0,0,0,.6);z-index:20}
.dlg{position:absolute;left:24px;right:24px;top:300px;background:var(--surface-high);border-radius:28px;padding:24px 24px 18px;z-index:30}
.dlg h3{font-weight:400;font-size:24px;line-height:1.25;margin-bottom:16px}
.dlg .lb{font-size:16px;color:var(--text)}
.sk{position:relative;height:48px;margin:0 -4px}
.sk i{position:absolute;display:block;top:22px;height:4px;border-radius:2px}
.sk .a{left:12px;background:var(--gold)}
.sk .b{background:rgba(174,186,170,.4);right:12px}
.sk b{position:absolute;top:14px;width:20px;height:20px;border-radius:50%;background:var(--gold);margin-left:-10px}
.dlg .bt{display:flex;justify-content:space-between;margin-top:14px;font-weight:500;font-size:14px;color:var(--gold)}
.dlg .bt span{padding:10px 12px}
"""


def dash_window(lang, dialog=False):
    """ControlsActivity (section «Dashboard»), design «Android Auto + datos»: the new crop row (dialog=False) and its slider dialog."""
    s = Strings(lang)
    d = V.DASH
    pct, mx = d["crop_pct"], d["crop_max"]
    seg = lambda k, on=False: f'<div class="seg f{" on" if on else ""}" style="width:100%">{esc(s(k))}</div>'
    themes = "".join(f'<div class="seg{" on" if i == 0 else ""}">{esc(s(k))}</div>'
                     for i, k in enumerate(("controls_auto", "controls_day", "controls_night")))
    crop_row = s("dash_window_crop_row", s("dash_window_crop_title"), pct)
    card = (f'<div class="mc dc"><div class="cap">{esc(s("controls_dash_title"))}</div>'
            f'<div class="dsc">{esc(s("controls_dash_desc"))}</div>'
            f'<div class="fl">{esc(s("controls_dash_show"))}</div><div class="sr">{themes}</div>'
            + seg("controls_dash_design") + seg("controls_dash_fields") + seg("controls_dash_card_order")
            + seg("controls_dash_accent") + seg("dash_window_crop_title").replace(esc(s("dash_window_crop_title")), esc(crop_row))
            + seg("controls_dash_background") + seg("controls_dash_scale") + '</div>')
    pos = pct / mx * 100
    dlg = (f'<div class="dim"></div><div class="dlg"><h3>{esc(s("dash_window_crop_title"))}</h3>'
           f'<div class="lb">{esc(s("dash_window_crop_value", pct))}</div>'
           f'<div class="sk"><i class="a" style="width:calc({pos}% - 12px * {pos / 100})"></i>'
           f'<i class="b" style="left:calc(12px + (100% - 24px) * {pos / 100})"></i>'
           f'<b style="left:calc(12px + (100% - 24px) * {pos / 100})"></b></div>'
           f'<div class="bt"><span>{esc(s("dash_window_crop_default", d["crop_default"]))}</span>'
           f'<span>{esc(s("dash_window_crop_done"))}</span></div></div>')
    body = f'<div class="cs"><div class="h1">{esc(s("settings_sec_dashboard"))}</div>{card}</div>' + (dlg if dialog else "")
    return phone_page(body, extra_css=DW_CSS)


# ---------------------------------------------------------------- Home: the Dashboard card

def home_dash(lang):
    """Home while the bike projects, scrolled so the «Dashboard» card (preview, style name, one «Dashboard options» button) is on top."""
    return home(lang, connected=True, dash_state="on", scroll=326)
