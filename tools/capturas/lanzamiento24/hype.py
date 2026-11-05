"""Dos collages para los grupos (1080×1350): A = «la 2.4 ya está aquí», con el keyless
delante; B = «y además», el resto. El dashboard sale pequeño y siempre con «necesita OBD». Escribe en out/dry/lanzamiento24/<idioma>/hype-a.png y hype-b.png."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
from lib.render import Job, render  # noqa: E402
from lib.res import Strings, esc, icon  # noqa: E402
from lanzamiento24.render import ASSETS, CSS, _b64, page  # noqa: E402
from lanzamiento24.textos import LANGS, T
from screens.pills import markup
from lanzamiento24.widget import widget  # noqa: E402

H = {
 "es": dict(a_k="Keyless · Widget · 10 idiomas", a_h="La 2.4 ya está aquí",
            a_s="Tu moto se abre desde el móvil. Gratis y sin anuncios.",
            b_k="Y además", b_t="Pensada para cada viaje",
            hello_t="Bienvenida y despedida en el cuadro", rain_t="Aviso de lluvia al aparcar",
            lock_t="Bloquear termina el viaje y guarda dónde aparcas", dash_t="Dashboard en el cuadro · con adaptador OBD"),
 "ca": dict(a_k="Keyless · Widget · 10 idiomes", a_h="La 2.4 ja és aquí",
            a_s="La moto s’obre des del mòbil. Gratis i sense anuncis.",
            b_k="I a més", b_t="Pensada per a cada viatge",
            hello_t="Benvinguda i comiat al quadre", rain_t="Avís de pluja en aparcar",
            lock_t="Bloquejar acaba el viatge i desa on aparques", dash_t="Dashboard al quadre · amb adaptador OBD"),
 "en": dict(a_k="Keyless · Widget · 10 languages", a_h="2.4 is here",
            a_s="Unlock your bike from your phone. Free, no ads.",
            b_k="And there’s more", b_t="Made for every ride",
            hello_t="Hello and goodbye on the dash", rain_t="Rain alert when you park",
            lock_t="Locking ends the ride and saves where you parked", dash_t="Dash view · needs an OBD adapter"),
 "it": dict(a_k="Keyless · Widget · 10 lingue", a_h="La 2.4 è arrivata",
            a_s="Apri la moto dal telefono. Gratis e senza pubblicità.",
            b_k="E inoltre", b_t="Pensata per ogni viaggio",
            hello_t="Benvenuto e saluto sul cruscotto", rain_t="Avviso pioggia al parcheggio",
            lock_t="Bloccare termina il viaggio e salva dove parcheggi", dash_t="Dashboard sul cruscotto · con adattatore OBD"),
 "th": dict(a_k="Keyless · Widget · 10 ภาษา", a_h="2.4 มาแล้ว",
            a_s="ปลดล็อกรถจากมือถือ ฟรี ไม่มีโฆษณา",
            b_k="และยังมีอีก", b_t="ออกแบบมาเพื่อทุกการเดินทาง",
            hello_t="การ์ดต้อนรับและบอกลาบนหน้าปัด", rain_t="แจ้งเตือนฝนเมื่อจอดรถ",
            lock_t="ล็อกรถแล้วจบการเดินทาง และบันทึกจุดจอด", dash_t="Dashboard บนหน้าปัด · ใช้กับอะแดปเตอร์ OBD"),
}
X = """
.wcap{margin-top:18px;margin-bottom:10px;font-size:22px;font-weight:700;color:var(--text2);letter-spacing:.3px}
.big{margin-top:28px;padding:24px 30px}
.big .v b{font-size:76px}.big .v span{font-size:30px}
.big .btns{gap:14px;margin-top:22px}.big .btn{font-size:25px;padding:20px 6px 16px}
.wrow{margin-top:20px;border-radius:30px;padding:20px 22px;display:flex;align-items:center;gap:22px}
.wrow .nm{font-size:24px;font-weight:700;color:var(--text2);width:200px;line-height:1.2}
.wrow .ic{flex:1;display:flex;justify-content:space-between}
.wrow .ic i{width:76px;height:76px;border-radius:20px;background:var(--surface-high);display:flex;align-items:center;justify-content:center}
.wrow .ic i.c{background:var(--gold)}
.bot{margin-top:20px;display:grid;grid-template-columns:1fr 1fr;gap:20px}
.tile{background:var(--surface);border:2px solid var(--outline);border-radius:28px;overflow:hidden;display:flex;flex-direction:column}
.tile .vis{flex:1;position:relative;display:flex;align-items:center;justify-content:center;min-height:0}
.tile .vis img{width:100%;height:100%;object-fit:cover;display:block}
.tile .lb{padding:14px 20px 16px;font-size:22px;font-weight:700;line-height:1.25}
.tile .lb small{display:block;font-size:18px;font-weight:600;color:var(--text2);margin-top:2px}
.bot .tile{height:236px}
.lchips{display:flex;flex-wrap:wrap;gap:7px;padding:18px 18px 0;font-family:'Archivo','NotoThai',sans-serif;align-content:flex-start}
.lchips span{border:2px solid var(--outline);border-radius:999px;padding:4px 11px;font-size:18px;font-weight:600}
.lchips span.nw{border-color:var(--gold);color:var(--gold)}
.g4{margin-top:36px;display:grid;grid-template-columns:1fr 1fr;gap:20px}
.g4 .tile{height:400px}
.g4 .vis img{object-fit:contain;padding:0 16px}
.mini{width:88%;height:78%;border-radius:20px;border:7px solid #0B0F0C;background:#121A15;position:relative;overflow:hidden;
  padding:22px 24px}
.mini .ph{position:absolute;right:0;top:0;bottom:0;width:55%;background-size:cover;background-position:center}
.mini .ph:before{content:"";position:absolute;inset:0;background:linear-gradient(90deg,#121A15,rgba(18,26,21,.2))}
.mini .g{position:relative;font-family:'ArchivoBlack','NotoThai';font-size:30px;line-height:1.08}
.mini .w{position:relative;font-size:18px;color:var(--text2);margin-top:8px}
.mini .st{position:relative;display:inline-block;margin-top:14px;background:var(--gold);color:var(--on-brand);font-weight:700;
  font-size:17px;border-radius:999px;padding:6px 14px}
.rain{width:86%;display:flex;gap:16px;align-items:center;background:var(--surface-high);border-radius:22px;padding:22px;
  font-size:23px;font-weight:700;line-height:1.25}
.rain i{font-style:normal;font-size:48px}
.lk{display:flex;align-items:center;gap:22px;font-size:60px;color:var(--text2)}
.lk .p{width:110px;height:110px;border-radius:50%;border:5px solid var(--gold);color:var(--gold);display:flex;
  align-items:center;justify-content:center;font-family:'ArchivoBlack';font-size:64px}
.lk .l{width:110px;height:110px;border-radius:30px;background:var(--surface-high);display:flex;align-items:center;justify-content:center}
"""


def collages(lang):
    s, t, h = Strings(lang), T[lang], H[lang]
    d = lambda n, m: _b64(os.path.join(ASSETS, "dash24", f"{n}-{m}-800x416.png"), "image/png")  # noqa: E731
    hero = _b64(os.path.join(ASSETS, "hero.png"), "image/png")
    g = "var(--gold)"
    lab = lambda k: esc(s(k).rstrip("."))  # noqa: E731
    btn = lambda i, k: f'<div class="btn">{icon(i, g, 46)}{lab(k)}</div>'  # noqa: E731
    volts = "12.6 V" if lang in ("en", "th") else "12,6 V"
    pke = (f'<div class="card pke big"><div class="h">{icon("ic_line_power", "var(--text2)", 26)}{lab("pke_card_title")}</div>'
           f'<div class="v"><b>{volts}</b><span>{lab("pke_locked")}</span></div><div class="btns">'
           f'{btn("ic_line_unlock", "pke_short_unlock")}{btn("ic_line_lock", "pke_short_lock")}'
           f'{btn("ic_line_seat", "pke_short_seat")}{btn("ic_line_fuel", "pke_short_fuel")}</div></div>')
    wid = (f'<div class="wcap">{markup(s, t["wid_t"], chips=False)}</div>'
           f'{widget(s, hero, lang, 330, 58, 920 / 330, compact=True)}')
    dash_l, dash_s = h["dash_t"].split(" · ")
    dash = (f'<div class="tile"><div class="vis"><img src="{d("sport", "night")}"></div>'
            f'<div class="lb">{esc(dash_l)}<small>{esc(dash_s)}</small></div></div>')
    langs = (f'<div class="tile"><div class="vis lchips">'
             + "".join(f'<span class="{"nw" if new else ""}">{esc(n)}</span>' for n, new in LANGS)
             + f'</div><div class="lb">{esc(t["lang_t"])}</div></div>')
    a = (f'<div class="k">{esc(h["a_k"])}</div><h1>{esc(h["a_h"])}</h1><div class="s">{esc(h["a_s"])}</div>'
         f'{pke}{wid}<div class="bot">{dash}{langs}</div>')

    hello = (f'<div class="mini"><div class="ph" style="background-image:url({hero})"></div>'
             f'<div class="g">{markup(s, t["hello"], chips=False)}</div><div class="w">{markup(s, t["weather"], chips=False)}</div>'
             f'<div class="st">🔥 {markup(s, t["streak"], chips=False)}</div></div>')
    rain = f'<div class="rain"><i>🌧️</i>{lab("rain_parked_title")}</div>'
    lock = f'<div class="lk"><div class="l">{icon("ic_line_lock", g, 64)}</div>→<div class="p">P</div></div>'
    tiles = [(hello, h["hello_t"]), (rain, h["rain_t"]), (lock, h["lock_t"]), (f'<img src="{d("racing", "night")}">', h["dash_t"])]
    b = (f'<div class="k">{esc(h["b_k"])}</div><h1>{esc(h["b_t"])}</h1><div class="g4">'
         + "".join(f'<div class="tile"><div class="vis">{v}</div><div class="lb">{esc(c)}</div></div>' for v, c in tiles)
         + "</div>")
    out = []
    for name, body in (("hype-a", a), ("hype-b", b)):
        html = page(lang, body, "", "", t).replace("</style>", X + "</style>").replace('<span class="n">/</span>', "")
        out.append(Job(f"l24_{name}_{lang}", html, 1080, 1350, 1, [(f"lanzamiento24/{lang}/{name}.png", None)]))
    return out


if __name__ == "__main__":
    for r, size in render([j for L in (sys.argv[1:] or list(H)) for j in collages(L)], dry=True):
        print(size, r)
