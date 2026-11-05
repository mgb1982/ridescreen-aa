"""Dos collages para los grupos (1080×1350): A = «llega la 2.4» con 4 novedades; B = los 3 estilos reales del
dashboard, de noche y de día. Escribe en out/dry/lanzamiento24/<idioma>/hype-a.png y hype-b.png."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
from lib.render import Job, render  # noqa: E402
from lib.res import Strings, esc, icon  # noqa: E402
from lanzamiento24.render import ASSETS, CSS, _b64, page  # noqa: E402
from lanzamiento24.textos import T  # noqa: E402

H = {
 "es": dict(a_k="La 2.4 llega ya", a_t="Tu moto, a otro nivel", a_s="Gratis, como siempre. Descárgala en ridescreen.app",
            tiles=["Abre la moto desde el móvil", "Tu propio cuadro con OBD", "Widget de controles", "10 idiomas"],
            b_k="Nuevo en la 2.4", b_t="Elige tu cuadro", b_s="Sport, Racing o Touring. De día y de noche. Con un adaptador OBD Bluetooth.",
            real="Imagen real de la app"),
 "ca": dict(a_k="La 2.4 arriba ja", a_t="La teva moto, a un altre nivell", a_s="Gratis, com sempre. Descarrega-la a ridescreen.app",
            tiles=["Obre la moto des del mòbil", "El teu propi quadre amb OBD", "Widget de controls", "10 idiomes"],
            b_k="Nou a la 2.4", b_t="Tria el teu quadre", b_s="Sport, Racing o Touring. De dia i de nit. Amb un adaptador OBD Bluetooth.",
            real="Imatge real de l’app"),
 "en": dict(a_k="2.4 is here", a_t="Your bike, levelled up", a_s="Free, as always. Get it at ridescreen.app",
            tiles=["Unlock your bike from your phone", "Your own dash with OBD", "Controls widget", "10 languages"],
            b_k="New in 2.4", b_t="Pick your dash", b_s="Sport, Racing or Touring. Day and night. With a Bluetooth OBD adapter.",
            real="Real app render"),
 "it": dict(a_k="Arriva la 2.4", a_t="La tua moto, a un altro livello", a_s="Gratis, come sempre. Scaricala su ridescreen.app",
            tiles=["Apri la moto dal telefono", "Il tuo cruscotto con l’OBD", "Widget dei comandi", "10 lingue"],
            b_k="Novità della 2.4", b_t="Scegli il tuo cruscotto", b_s="Sport, Racing o Touring. Di giorno e di notte. Con un adattatore OBD Bluetooth.",
            real="Immagine reale dell’app"),
 "th": dict(a_k="2.4 มาแล้ว", a_t="ยกระดับรถของคุณ", a_s="ฟรีเหมือนเดิม ดาวน์โหลดที่ ridescreen.app",
            tiles=["ปลดล็อกรถจากมือถือ", "หน้าปัดของคุณเองด้วย OBD", "วิดเจ็ตควบคุมรถ", "10 ภาษา"],
            b_k="ใหม่ในเวอร์ชัน 2.4", b_t="เลือกหน้าปัดของคุณ", b_s="Sport, Racing หรือ Touring ทั้งกลางวันและกลางคืน ใช้กับอะแดปเตอร์ OBD Bluetooth",
            real="ภาพจริงจากแอป"),
}
X = """
.grid{margin-top:40px;display:grid;grid-template-columns:1fr 1fr;gap:20px}
.tl{background:var(--surface);border:2px solid var(--outline);border-radius:30px;overflow:hidden;height:370px;position:relative}
.tl .v{position:absolute;inset:0 0 86px 0;display:flex;align-items:center;justify-content:center;gap:14px}
.tl img{width:100%;height:100%;object-fit:cover}
.tl .c{position:absolute;left:0;right:0;bottom:0;height:86px;display:flex;align-items:center;padding:0 24px;
  font-size:27px;font-weight:700;line-height:1.15;background:var(--surface)}
.tl .big{font-family:'ArchivoBlack','NotoThai';font-size:120px;color:var(--gold)}
.dg{margin-top:30px;display:grid;grid-template-columns:1fr 1fr;gap:14px}
.dg div{position:relative}.dg img{width:100%;display:block;border-radius:16px;border:2px solid var(--outline)}
.dg span{position:absolute;left:12px;top:10px;background:rgba(10,14,11,.8);color:var(--text);font-size:18px;
  font-weight:700;letter-spacing:1.5px;padding:4px 10px;border-radius:8px}
.real{margin-top:14px;font-size:20px;color:var(--text2)}
.cta{display:inline-block;margin-top:30px;background:var(--gold);color:var(--on-brand);border-radius:999px;
  padding:16px 30px;font-size:30px;font-weight:700}
"""


def collages(lang):
    s, t, h = Strings(lang), T[lang], H[lang]
    d = lambda n, m: _b64(os.path.join(ASSETS, "dash24", f"{n}-{m}-800x416.png"), "image/png")  # noqa: E731
    hero = _b64(os.path.join(ASSETS, "hero.png"), "image/png")
    g = "var(--gold)"
    keys = "".join(icon(i, g, 64) for i in ("ic_line_unlock", "ic_line_lock", "ic_line_seat", "ic_line_fuel"))
    wid = "".join(icon(i, g, 52) for i in ("ic_line_power", "ic_line_unlock", "ic_line_lock", "ic_line_seat", "ic_line_fuel"))
    tiles = [f'<div class="v">{keys}</div>', f'<img src="{d("sport", "night")}">',
             f'<div class="v" style="background:linear-gradient(0deg,rgba(26,35,29,.9),rgba(26,35,29,.3)),url({hero}) center/cover">{wid}</div>',
             '<div class="v"><span class="big">10</span></div>']
    a = (f'<div class="k">{esc(h["a_k"])}</div><h1>{esc(h["a_t"])}</h1><div class="s">{esc(h["a_s"])}</div><div class="grid">'
         + "".join(f'<div class="tl">{v}<div class="c">{esc(c)}</div></div>' for v, c in zip(tiles, h["tiles"]))
         + "</div>")
    b = (f'<div class="k">{esc(h["b_k"])}</div><h1>{esc(h["b_t"])}</h1><div class="s">{esc(h["b_s"])}</div><div class="dg">'
         + "".join(f'<div><img src="{d(n, m)}"><span>{n.upper()}</span></div>'
                   for n in ("sport", "racing", "touring") for m in ("night", "day"))
         + f'</div><div class="real">{esc(h["real"])}</div>')
    out = []
    for name, body in (("hype-a", a), ("hype-b", b)):
        html = page(lang, body, "", "", t).replace("</style>", X + "</style>").replace('<span class="n">/</span>', "")
        out.append(Job(f"l24_{name}_{lang}", html, 1080, 1350, 1, [(f"lanzamiento24/{lang}/{name}.png", None)]))
    return out


if __name__ == "__main__":
    for r, size in render([j for L in (sys.argv[1:] or list(H)) for j in collages(L)], dry=True):
        print(size, r)
