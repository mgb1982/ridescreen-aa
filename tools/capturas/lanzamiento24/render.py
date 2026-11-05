"""Material de lanzamiento de la 2.4: carrusel de Instagram (1080×1350) e imagen para los grupos.
Nunca se publica: escribe en out/dry/lanzamiento24/<idioma>/.

    python3 tools/capturas/lanzamiento24/render.py [es ca en]
"""
import base64
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
from PIL import Image  # noqa: E402

from lib.base import EXTRA_FONTS, root_css  # noqa: E402
from lib.render import OUT, Job, render  # noqa: E402
from lib.res import Strings, esc, icon  # noqa: E402
from lanzamiento24.textos import LANGS, T  # noqa: E402
from screens.pills import markup  # noqa: E402

ASSETS = os.path.join(HERE, "..", "assets")
DEMO = dict(volts="12,6 V", volts_en="12.6 V", km="6,8 km", km_en="6.8 km", mins="18 min", avg="22", vmax="71")


def _b64(path, mime):
    return f"data:{mime};base64," + base64.b64encode(open(path, "rb").read()).decode()


def thai_css():
    """Noto Sans Thai (OFL) grafted onto Archivo for U+0E00–0E7F, like Roboto for Greek/Cyrillic."""
    out = []
    for w in (400, 700):
        src = "file://" + os.path.join(EXTRA_FONTS, f"noto-sans-thai-thai-{w}-normal.woff2")
        out.append(f"@font-face{{font-family:'NotoThai';font-weight:{w};src:url('{src}') format('woff2')}}")
    return "".join(out) + (".lg{font-family:'Archivo','NotoThai',sans-serif}"
                           "html[lang=th] body{font-family:'Archivo','NotoThai',sans-serif}"
                           "html[lang=th] h1,html[lang=th] .g{font-family:'ArchivoBlack','NotoThai',sans-serif;font-weight:700}")


CSS = """
body{background:var(--bg)}
.sl{width:1080px;height:1350px;position:relative;overflow:hidden;padding:84px 80px 0;
  background:radial-gradient(110% 70% at 15% 0%,#2C3D32 0%,var(--bg) 62%)}
.top{display:flex;align-items:center;gap:16px;font-size:24px;font-weight:700;letter-spacing:3px;color:var(--gold);
  text-transform:uppercase}
.top img{width:52px;height:52px}
.top .n{margin-left:auto;color:var(--text2);letter-spacing:1px;font-weight:600}
.k{font-size:24px;font-weight:700;letter-spacing:3.4px;color:var(--gold);text-transform:uppercase;margin-top:54px}
h1{font-family:'ArchivoBlack';font-weight:400;font-size:84px;line-height:1.02;margin-top:14px;letter-spacing:-.5px}
.s{font-size:31px;line-height:1.38;color:var(--text2);margin-top:26px}
.s b,.bl b{color:var(--text);font-weight:700}
.chip{display:inline-block;border:2px solid #4A5C4E;border-radius:12px;padding:0 12px 3px;color:var(--text);
  font-weight:600;white-space:nowrap}
.bl{margin-top:30px;display:flex;flex-direction:column;gap:12px}
.bl div{font-size:27px;line-height:1.35;color:var(--text2);padding-left:34px;position:relative}
.bl div:before{content:"";position:absolute;left:4px;top:15px;width:12px;height:12px;border-radius:50%;
  background:var(--gold)}
.ft{position:absolute;left:80px;right:80px;bottom:56px;display:flex;justify-content:space-between;font-size:24px;
  color:var(--text2);font-weight:600}
.ft b{color:var(--gold);font-weight:700}
.card{background:var(--surface);border:2px solid var(--outline);border-radius:34px}
/* keyless card */
.pke{margin-top:36px;padding:28px 32px}
.pke .h{display:flex;align-items:center;gap:12px;font-size:20px;letter-spacing:2.4px;font-weight:700;color:var(--text2)}
.pke .v{display:flex;align-items:baseline;gap:22px;margin-top:10px}
.pke .v b{font-family:'ArchivoBlack';font-weight:400;font-size:70px;color:var(--live)}
.pke .v span{font-size:28px;color:var(--text);font-weight:600}
.btns{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-top:26px}
.btn{background:var(--surface-high);border-radius:24px;padding:22px 6px 18px;display:flex;flex-direction:column;
  align-items:center;gap:12px;font-size:24px;font-weight:700;color:var(--text)}
/* widget */
.wid{margin-top:52px;padding:0;overflow:hidden;border-radius:44px}
.wid .ph{height:330px;background-size:cover;background-position:center;position:relative}
.wid .ph:after{content:"";position:absolute;inset:0;background:linear-gradient(180deg,rgba(26,35,29,0) 40%,
  rgba(26,35,29,.95))}
.wid .nm{position:absolute;left:34px;bottom:22px;z-index:2;font-family:'ArchivoBlack';font-size:40px}
.wid .row{display:grid;grid-template-columns:repeat(5,1fr);gap:12px;padding:22px 24px 26px}
.wid .row .btn{font-size:20px;padding:20px 4px 16px}
.wid .row .btn.c{background:var(--gold);color:var(--on-brand)}
.slim{margin-top:28px;border-radius:30px;padding:16px 24px;display:flex;justify-content:space-around}
/* dashboard */
.dash{margin-top:36px;border-radius:30px;overflow:hidden;border:2px solid var(--outline);position:relative}
.dash img{display:block;width:100%}
.dash .lab{position:absolute;right:16px;top:14px;background:rgba(15,21,17,.85);color:var(--text2);
  font-size:19px;font-weight:600;padding:6px 14px;border-radius:10px}
/* dash cards */
.dc{margin-top:34px;display:flex;flex-direction:column;gap:16px}
.scr{width:920px;height:330px;border-radius:30px;border:10px solid #0B0F0C;background:#121A15;position:relative;
  overflow:hidden;padding:34px 44px}
.scr .ph{position:absolute;right:0;top:0;bottom:0;width:58%;background-size:cover;background-position:center}
.scr .ph:before{content:"";position:absolute;inset:0;background:linear-gradient(90deg,#121A15 0%,
  rgba(18,26,21,.55) 45%,rgba(18,26,21,.1))}
.scr .tx{position:relative;z-index:2}
.scr .g{font-family:'ArchivoBlack';font-size:50px;line-height:1.05}
.scr .w{font-size:28px;color:var(--text2);margin-top:16px}
.scr .st{display:inline-flex;gap:12px;align-items:center;margin-top:24px;background:var(--gold);color:var(--on-brand);
  font-weight:700;font-size:26px;border-radius:999px;padding:10px 22px}
.bye .t{font-size:26px;font-weight:700;letter-spacing:2px;color:var(--gold);text-transform:uppercase}
.bye .nums{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-top:22px}
.bye .nums div{font-size:22px;color:var(--text2)}
.bye .nums b{display:block;font-family:'ArchivoBlack';font-weight:400;font-size:54px;color:var(--text);margin-top:6px}
.noti{display:flex;gap:18px;align-items:center;padding:22px 26px;border-radius:26px;font-size:26px}
.noti b{display:block;color:var(--text)}
/* languages */
.langs{margin-top:56px;display:grid;grid-template-columns:1fr 1fr;gap:16px}
.lg{background:var(--surface);border:2px solid var(--outline);border-radius:22px;padding:22px 28px;font-size:38px;
  font-weight:700;display:flex;align-items:center;justify-content:space-between}
.lg i{font-style:normal;font-size:19px;letter-spacing:1.6px;text-transform:uppercase;background:var(--gold);
  color:var(--on-brand);border-radius:999px;padding:5px 12px}
/* cover / end */
.cv h1{font-size:112px;margin-top:18px}
.chips{display:flex;flex-wrap:wrap;gap:16px;margin-top:48px}
.chips span{background:var(--surface);border:2px solid var(--outline);border-radius:999px;padding:16px 28px;
  font-size:31px;font-weight:700}
.hero{position:absolute;left:0;right:0;bottom:0;height:470px;background-size:cover;background-position:center 60%}
.hero:before{content:"";position:absolute;inset:0;background:linear-gradient(180deg,var(--bg) 0%,
  rgba(26,35,29,.2) 45%,rgba(26,35,29,.75))}
.url{display:inline-block;margin-top:56px;background:var(--gold);color:var(--on-brand);border-radius:999px;
  padding:22px 40px;font-family:'ArchivoBlack';font-size:48px}
.more{margin-top:56px;font-size:29px;line-height:1.42;color:var(--text2)}
.thx{margin-top:30px;font-size:25px;line-height:1.45;color:var(--text2);border-left:6px solid var(--gold);padding-left:22px}
"""


def page(lang, body, n, total, t):
    icon_svg = _b64(os.path.join(ASSETS, "icon.svg"), "image/svg+xml")
    top = (f'<div class="top"><img src="{icon_svg}">RideScreen AA 2.4 · {esc(t["brand_tag"])}'
           f'<span class="n">{n}/{total}</span></div>')
    ft = '<div class="ft"><b>ridescreen.app</b><span>@ridescreen.app</span></div>'
    return (f'<!doctype html><html lang="{lang}"><head><meta charset="utf-8"><style>{root_css()}{thai_css()}{CSS}'
            f'</style></head><body><div class="sl">{top}{body}{ft}</div></body></html>')


def bullets(s, items):
    return '<div class="bl">' + "".join(f"<div>{markup(s, x)}</div>" for x in items) + "</div>"


def slides(lang):
    s, t = Strings(lang), T[lang]
    en = lang in ("en", "th")
    gold = "var(--gold)"
    hero = _b64(os.path.join(ASSETS, "hero.png"), "image/png")
    ico = lambda n, c=gold, z=40: icon(n, c, z)  # noqa: E731
    lab = lambda k: esc(s(k).rstrip("."))  # noqa: E731
    out = []

    out.append(f'<div class="cv"><div class="k">{esc(t["cover_k"])}</div><h1>{esc(t["cover_t"])}</h1>'
               f'<div class="s">{esc(t["cover_s"])}</div><div class="chips">'
               + "".join(f"<span>{esc(c)}</span>" for c in t["cover_chips"])
               + f'</div></div><div class="hero" style="background-image:url({hero})"></div>')

    btn = lambda i, k: f'<div class="btn">{ico(i)}{lab(k)}</div>'  # noqa: E731
    out.append(f'<div class="k">{esc(t["pke_k"])}</div><h1>{markup(s, t["pke_t"], chips=False)}</h1>'
               f'<div class="s">{markup(s, t["pke_s"])}</div>'
               f'<div class="card pke"><div class="h">{ico("ic_line_power", "var(--text2)", 26)}{lab("pke_card_title")}</div>'
               f'<div class="v"><b>{DEMO["volts_en" if en else "volts"]}</b><span>{lab("pke_locked")}</span></div>'
               f'<div class="btns">{btn("ic_line_unlock", "pke_short_unlock")}{btn("ic_line_lock", "pke_short_lock")}'
               f'{btn("ic_line_seat", "pke_short_seat")}{btn("ic_line_fuel", "pke_short_fuel")}</div></div>'
               + bullets(s, t["pke_b"]))

    from lanzamiento24.widget import widget
    out.append(f'<div class="k">{esc(t["wid_k"])}</div><h1>{markup(s, t["wid_t"], chips=False)}</h1>'
               f'<div class="s">{markup(s, t["wid_s"])}</div>'
               f'<div style="margin-top:36px">{widget(s, hero, lang, 330, 170, 920 / 330)}</div>'
               f'<div style="margin-top:20px">{widget(s, hero, lang, 330, 54, 920 / 330, compact=True)}</div>')

    dprev = _b64(os.path.join(OUT, "lanzamiento24", "dash_night.jpg"), "image/jpeg")
    out.append(f'<div class="k">{esc(t["obd_k"])}</div><h1>{markup(s, t["obd_t"], chips=False)}</h1>'
               f'<div class="s">{markup(s, t["obd_s"])}</div>'
               f'<div class="dash"><img src="{dprev}"><div class="lab">{esc(t["obd_note"])}</div></div>'
               + bullets(s, t["obd_b"]))

    from screens.phone import AVG_MAX
    av, mx = AVG_MAX[lang]
    nums = [("km", DEMO["km_en" if en else "km"].split()[0]), ("min", "18"), (av, DEMO["avg"]), (mx, DEMO["vmax"])]
    out.append(f'<div class="k">{esc(t["dash_k"])}</div><h1>{markup(s, t["dash_t"], chips=False)}</h1>'
               f'<div class="s">{markup(s, t["dash_s"])}</div><div class="dc">'
               f'<div class="scr"><div class="ph" style="background-image:url({hero})"></div><div class="tx">'
               f'<div class="g">{markup(s, t["hello"], chips=False)}</div><div class="w">{markup(s, t["weather"], chips=False)}</div>'
               f'<div class="st">🔥 {markup(s, t["streak"], chips=False)}</div></div></div>'
               f'<div class="scr bye" style="height:236px"><div class="t">{lab("dc_bye_title")}</div><div class="nums">'
               + "".join(f'<div>{esc(a)}<b>{esc(b)}</b></div>' for a, b in nums)
               + f'</div></div><div class="card noti">🌧️<div><b>{lab("rain_parked_title")}</b></div></div></div>')

    out.append(f'<div class="k">{esc(t["lang_k"])}</div><h1>{esc(t["lang_t"])}</h1><div class="s">{esc(t["lang_s"])}</div>'
               '<div class="langs">' + "".join(
                   f'<div class="lg">{esc(n)}{"<i>" + esc(t["lang_new"]) + "</i>" if new else ""}</div>'
                   for n, new in LANGS) + "</div>")

    out.append(f'<div class="k">{esc(t["end_k"])}</div><h1>{esc(t["end_t"])}</h1><div class="s">{esc(t["end_s"])}</div>'
               f'<div class="url">ridescreen.app</div><div class="more">{esc(t["end_more"])}</div>'
               f'<div class="thx">{esc(t["end_thanks"])}</div>')
    return out


def prep():
    """Real dashboards: Robolectric renders of DashboardRenderer at 2cbc824 (assets/dash24, from the
    'Dashboard snapshots' workflow on test/dashboard-snapshots-release). Sport big, Racing and Touring below."""
    d = os.path.join(OUT, "lanzamiento24")
    os.makedirs(d, exist_ok=True)
    src = lambda n: Image.open(os.path.join(ASSETS, "dash24", f"{n}-night-800x416.png")).convert("RGB")  # noqa: E731
    gap = 20
    big = src("sport").resize((1060, 551))
    sw, sh = 520, 270
    sheet = Image.new("RGB", (1060 + gap + sw, 551), (11, 15, 12))
    sheet.paste(big, (0, 0))
    sheet.paste(src("racing").resize((sw, sh)), (1060 + gap, 0))
    sheet.paste(src("touring").resize((sw, sh)), (1060 + gap, 551 - sh))
    sheet.save(os.path.join(d, "dash_night.jpg"), quality=92)


def main(langs):
    prep()
    for lang in langs:
        bodies = slides(lang)
        jobs = [Job(f"l24_{i:02d}_{lang}", page(lang, b, i, len(bodies), T[lang]), 1080, 1350, 1,
                    [(f"lanzamiento24/{lang}/carrusel-{i:02d}.png", None)]) for i, b in enumerate(bodies, 1)]
        for r, size in render(jobs, dry=True):
            print(f"{size[0]}×{size[1]}  {r}")


if __name__ == "__main__":
    main(sys.argv[1:] or ["es", "ca", "en", "it", "th"])
