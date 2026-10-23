"""Shared look: colour tokens from colors.xml, the app's own fonts, status bar, phone canvas."""
import os
from .res import FONT_DIR, colors

HERE = os.path.dirname(__file__)
ASSETS = os.path.abspath(os.path.join(HERE, "..", "assets"))
EXTRA_FONTS = os.path.abspath(os.path.join(HERE, "..", "fonts"))

C = colors()
TOK = {
    "bg": C["bg"], "surface": C["surface"], "surface_high": C["surface_high"], "outline": C["outline"],
    "text": C["text_primary"], "text2": C["text_secondary"], "gold": C["brand_gold"],
    "on_brand": C["on_brand"], "live": C["status_live"], "busy": C["status_busy"],
    "error": C["status_error"], "idle": C["status_idle"],
}

# Phone canvas: 412×915 CSS px. Master at DPR 2.625 (1082×2402), as on a Pixel-class phone.
PHONE_W, PHONE_H, PHONE_DPR = 412, 915, 2.625
# Watch canvas: 225×225 CSS px, master at DPR 2 (450×450).
WATCH_W, WATCH_DPR = 225, 2

# Status bar: always the same so every capture lines up.
STATUS_TIME = "18:42"
STATUS_BATTERY = "78%"


def _face(family, file, weight):
    return (f"@font-face{{font-family:'{family}';src:url('file://{os.path.join(FONT_DIR, file)}');"
            f"font-weight:{weight};font-style:normal}}")


def _watch_fonts():
    # The watch uses the system font (Roboto on Wear OS). Drop Roboto-*.ttf into tools/capturas/fonts/
    # and it is used; otherwise Inter, the closest installed face.
    out = []
    if os.path.isdir(EXTRA_FONTS):
        for f in sorted(os.listdir(EXTRA_FONTS)):
            if f.lower().startswith("roboto") and f.lower().endswith((".ttf", ".otf", ".woff2")):
                w = 700 if "bold" in f.lower() else 500 if "medium" in f.lower() else 400
                out.append(f"@font-face{{font-family:'WatchSans';src:url('file://{os.path.join(EXTRA_FONTS, f)}');font-weight:{w}}}")
    return "".join(out)


FONTS = "".join([
    _face("Archivo", "archivo_regular.ttf", 400),
    _face("Archivo", "archivo_medium.ttf", 500),
    _face("Archivo", "archivo_semibold.ttf", 600),
    _face("Archivo", "archivo_extrabold.ttf", 800),
    _face("ArchivoBlack", "archivo_black.ttf", 400),
]) + _watch_fonts()

EMOJI = "'Noto Color Emoji'"
SANS = f"'Archivo',{EMOJI},sans-serif"
DISPLAY = f"'ArchivoBlack','Archivo',{EMOJI},sans-serif"
WATCH_SANS = f"'WatchSans','Inter',{EMOJI},sans-serif"


def root_css():
    t = TOK
    return f"""{FONTS}
:root{{--bg:{t['bg']};--surface:{t['surface']};--surface-high:{t['surface_high']};--outline:{t['outline']};
--text:{t['text']};--text2:{t['text2']};--gold:{t['gold']};--on-brand:{t['on_brand']};--live:{t['live']};
--busy:{t['busy']};--error:{t['error']};--idle:{t['idle']}}}
*{{box-sizing:border-box;margin:0;padding:0}}
html,body{{background:transparent}}
body{{font-family:{SANS};color:var(--text);-webkit-font-smoothing:antialiased}}
.display{{font-family:{DISPLAY};font-weight:400;letter-spacing:.2px}}
.muted{{color:var(--text2)}}
.gold{{color:var(--gold)}}
"""


def status_bar(dark_text=False):
    col = "#111" if dark_text else "var(--text)"
    signal = ('<svg width="15" height="13" viewBox="0 0 15 13"><g fill="var(--gold)">'
              '<rect x="0" y="9" width="3" height="4" rx=".6"/><rect x="4" y="6" width="3" height="7" rx=".6"/>'
              '<rect x="8" y="3" width="3" height="10" rx=".6"/><rect x="12" y="0" width="3" height="13" rx=".6"/></g></svg>')
    battery = ('<svg width="9" height="15" viewBox="0 0 9 15"><rect x="2.5" y="0" width="4" height="2" rx=".5" fill="var(--live)"/>'
               '<rect x="0" y="1.5" width="9" height="13.5" rx="1.8" fill="var(--live)"/></svg>')
    return (f'<div class="sb" style="position:absolute;left:0;right:0;top:0;height:30px;display:flex;align-items:center;'
            f'justify-content:space-between;padding:0 18px;font:600 13.5px/1 {SANS};color:{col};z-index:50">'
            f'<span>{STATUS_TIME}</span><span style="display:flex;gap:6px;align-items:center">{signal}{battery}'
            f'<span>{STATUS_BATTERY}</span></span></div>')


def phone_page(body, height=PHONE_H, extra_css="", bg="var(--bg)"):
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>{root_css()}
.scr{{position:relative;width:{PHONE_W}px;height:{height}px;background:{bg};overflow:hidden}}
{extra_css}</style></head><body><div class="scr">{body}{status_bar()}</div></body></html>"""


def watch_page(body, bg="#000", extra_css=""):
    w = WATCH_W
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>{root_css()}
body{{font-family:{WATCH_SANS}}}
.w{{position:relative;width:{w}px;height:{w}px;border-radius:50%;overflow:hidden;background:{bg};
display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;color:#fff}}
.pill{{background:rgba(255,255,255,.14);border-radius:999px;padding:4px 11px;font-size:13.5px;font-weight:500}}
.dots{{position:absolute;bottom:9px;left:0;right:0;display:flex;gap:4px;justify-content:center}}
.dots i{{width:4.5px;height:4.5px;border-radius:50%;border:1px solid #aaa;display:block}}
.dots i.on{{background:#fff;border-color:#fff}}
{extra_css}</style></head><body><div class="w">{body}</div></body></html>"""


def dots(n, on):
    return '<div class="dots">' + "".join(f'<i class="{"on" if i == on else ""}"></i>' for i in range(n)) + "</div>"
