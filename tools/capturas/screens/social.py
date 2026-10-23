"""Instagram carousel slides (1080×1350, 4:5) in the style of the pinned carousels."""
import os

from lib import demo as D
from lib.base import root_css
from lib.res import ROOT, Strings, esc

W, H = 1080, 1350
HUGO = "file://" + os.path.join(ROOT, "docs", "media", "motos", "zontes-703t-cuadro.jpg")

CSS = """
.sl{position:relative;width:1080px;height:1350px;background:linear-gradient(170deg,#1E2A22 0%,#151C17 100%);overflow:hidden;
  font-family:'Archivo','Noto Color Emoji',sans-serif;color:var(--text)}
.tag{position:absolute;left:72px;top:64px;font-weight:700;font-size:26px;letter-spacing:3px;color:var(--gold)}
.h{position:absolute;left:72px;right:72px;top:112px;font-family:'ArchivoBlack';font-size:84px;line-height:1.02}
.h b{color:var(--gold);font-weight:400}
.sub{position:absolute;left:72px;right:90px;font-size:34px;line-height:1.35;color:var(--text2)}
.pills{position:absolute;left:72px;display:flex;gap:12px}
.pills span{background:var(--gold);color:var(--on-brand);font-weight:800;font-size:22px;letter-spacing:1.5px;border-radius:999px;padding:8px 18px}
.foot{position:absolute;right:72px;bottom:46px;color:var(--gold);font-size:26px;font-weight:600}
.phone{position:absolute;border-radius:58px;background:#0B0F0C;padding:14px;box-shadow:0 30px 80px rgba(0,0,0,.55)}
.phone .in{border-radius:46px;overflow:hidden;position:relative}
.phone img{display:block;width:100%}
.card{background:var(--surface);border-radius:44px;display:flex;align-items:center;gap:26px;padding:34px 40px}
.card .t{font-size:26px;letter-spacing:2.5px;color:var(--text2);font-weight:600}
.card .v{font-family:'ArchivoBlack';font-size:84px;line-height:1.05}
.card .v small{font-family:'Archivo';font-size:36px;color:var(--text2);margin-left:6px}
.card .s{font-size:28px;color:var(--text2)}
.card .l{margin-left:auto;text-align:center;font-weight:700;font-size:34px;line-height:1.25;white-space:pre-line}
.note{position:absolute;left:72px;right:72px;border-left:6px solid var(--gold);padding:6px 0 6px 26px;font-size:28px;line-height:1.4;color:var(--text2)}
.rows{position:absolute;left:72px;right:72px;display:flex;flex-direction:column;gap:18px}
.rows div{background:var(--surface);border-radius:28px;padding:26px 30px;font-size:36px;display:flex;gap:22px;align-items:center}
.chips{position:absolute;left:72px;right:72px;display:flex;flex-wrap:wrap;gap:14px}
.chips span{border:2px solid var(--outline);border-radius:999px;padding:12px 24px;font-size:30px;font-weight:600}
.chips span.new{border-color:var(--gold);color:var(--gold)}
.cta{position:absolute;left:72px;right:72px;background:var(--gold);color:var(--on-brand);border-radius:36px;padding:30px 36px;
  font-size:34px;font-weight:700;line-height:1.3}
.cta small{display:block;font-size:28px;font-weight:600;opacity:.85;margin-top:6px}
"""

TAG = "RIDESCREEN AA · NOVEDADES 2.3"
SWIPE = "Desliza →"


def _page(body):
    return (f'<!doctype html><html><head><meta charset="utf-8"><style>{root_css()}{CSS}</style></head>'
            f'<body><div class="sl">{body}</div></body></html>')


def _phone(img, left, top, width, height, pos="top"):
    return (f'<div class="phone" style="left:{left}px;top:{top}px;width:{width}px">'
            f'<div class="in" style="height:{height}px"><img src="file://{img}" style="object-fit:cover;'
            f'height:100%;object-position:{pos}"></div></div>')


def _card(lang, volts, running, locked):
    s = Strings(lang)
    sub = s("engine_running" if running else "engine_stopped") + " · " + s("pke_card_read", s("pke_card_just_now"))
    lock = s("pke_card_locked" if locked else "pke_card_unlocked")
    return (f'<div class="card"><div><div class="t">{esc(s("pke_card_title"))}</div>'
            f'<div class="v">{D.num(lang, volts)}<small>V</small></div><div class="s">{esc(sub)}</div></div>'
            f'<div class="l">{esc(lock)}</div></div>')


def slides(masters):
    """masters: dict name → path of rendered phone masters (home_battery, home_conn)."""
    lang = "es"
    out = {}
    out["01_portada"] = _page(
        f'<div class="tag">{TAG}</div>'
        '<div class="h">Tu moto,<br>también en<br>el móvil: <b>batería<br>y bloqueo</b></div>'
        '<div class="pills" style="top:500px"><span>NUEVO</span><span>GRATIS</span><span>SIN ANUNCIOS</span></div>'
        + _phone(masters["home_battery"], 300, 600, 480, 680, "top")
        + f'<div class="foot">{SWIPE}</div>')
    out["02_bateria"] = _page(
        f'<div class="tag">{TAG}</div>'
        '<div class="h">Cuánta <b>batería</b><br>le queda?</div>'
        '<div class="sub" style="top:340px">El voltaje de la batería de tu moto, en directo en la app. '
        'Lleva días parada? Míralo antes de bajar al parking.</div>'
        f'<div style="position:absolute;left:72px;right:72px;top:600px">{_card(lang, 12.6, False, True)}</div>'
        f'<div style="position:absolute;left:72px;right:72px;top:860px">{_card(lang, 13.9, True, True)}</div>'
        '<div class="note" style="top:1130px">Con el motor en marcha sube por encima de 13 V: así sabes que carga.</div>'
        f'<div class="foot">{SWIPE}</div>')
    out["03_bloqueo"] = _page(
        f'<div class="tag">{TAG}</div>'
        '<div class="h">Se quedó<br><b>bloqueada?</b></div>'
        '<div class="sub" style="top:340px">Compruébalo de un vistazo, sin volver a la moto.</div>'
        f'<div style="position:absolute;left:72px;right:72px;top:520px">{_card(lang, 12.6, False, True)}</div>'
        f'<div style="position:absolute;left:72px;right:72px;top:780px">{_card(lang, 12.6, False, False)}</div>'
        '<div class="note" style="top:1070px">Solo lectura: la app mira el estado, no bloquea ni desbloquea.<br>'
        'Necesita el keyless de Zontes y tu código PKE<br>(❓ Ayuda → 🔋 Batería).</div>'
        f'<div class="foot">{SWIPE}</div>')
    out["04_casa_trabajo"] = _page(
        f'<div class="tag">{TAG}</div>'
        '<div class="h">Casa, Trabajo<br>y tus citas,<br><b>a un toque</b></div>'
        '<div class="sub" style="top:440px">Y con «Llego en X min» avisa por WhatsApp o SMS al salir.</div>'
        + _phone(masters["home_conn"], 250, 600, 580, 680, "50% 66%")
        + f'<div class="foot">{SWIPE}</div>')
    out["05_pasajero"] = _page(
        f'<div class="tag">{TAG}</div>'
        '<div class="h">Modo pasajero:<br><b>tu acompañante<br>manda</b></div>'
        '<div class="sub" style="top:440px">Desde su propio móvil, sin tocar el tuyo.</div>'
        '<div class="rows" style="top:600px"><div>📍 Elige el destino</div><div>🎵 Cambia la música</div>'
        '<div>🎮 Mueve Android Auto en el cuadro</div><div>📶 Sin internet: Bluetooth entre los dos móviles</div></div>'
        f'<div class="foot">{SWIPE}</div>')
    out["06_motos"] = _page(
        f'<div class="tag">{TAG}</div>'
        '<div class="h">Ya funciona en<br><b>más Zontes</b></div>'
        f'<div style="position:absolute;left:72px;right:72px;top:330px;height:470px;border-radius:36px;overflow:hidden;'
        f'background:url({HUGO}) 50% 30%/cover"></div>'
        '<div style="position:absolute;left:72px;top:812px;font-size:22px;color:var(--text2)">Zontes 703T · foto de Hugo</div>'
        '<div class="chips" style="top:870px"><span>125X</span><span>125V</span><span>368 G/K/E</span>'
        '<span class="new">703F</span><span class="new">703T</span></div>'
        '<div class="cta" style="top:1060px">Ya la tienes? Configuración → Buscar actualizaciones'
        '<small>Aún no? Descárgala en ridescreen.app</small></div>')
    return out
