"""Píldoras y collage para los testers de la 2.4. Nunca se publica: escribe en out/dry/testers24/.

    python3 tools/capturas/testers24/render.py [es]
"""
import importlib
import os
import sys

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

from lib.base import root_css  # noqa: E402
from lib.render import OUT, Job, render  # noqa: E402
from lib.res import Strings, esc  # noqa: E402
from screens.pills import CSS, markup  # noqa: E402

FOOT = {"es": "2.4 de pruebas"}


def html(lang, p, n, total):
    s = Strings(lang)
    steps = "".join(f'<div class="st"><div class="n">{i}</div><div class="tx">{markup(s, t)}</div></div>'
                    for i, t in enumerate(p["steps"], 1))
    note = f'<div class="note">{markup(s, p["note"])}</div>' if p.get("note") else ""
    foot = (markup(s, p["foot"], chips=False) + " · " if p.get("foot") else "") + f"{FOOT[lang]} · {n}/{total}"
    return f"""<!doctype html><html lang="{lang}"><head><meta charset="utf-8"><style>{root_css()}{CSS}</style></head><body>
<div class="pl"><div class="tag"><i>{p["icon"]}</i>RideScreen AA · {esc(p["tag"])}</div>
<h1>{markup(s, p["title"], chips=False)}</h1><div class="sub">{markup(s, p["sub"])}</div>
<div class="steps">{steps}</div>{note}<div class="ft">{foot}</div></div></body></html>"""


def collage(paths, dst, cols=2, gap=36, bg=(14, 20, 16)):
    ims = [Image.open(p).convert("RGB") for p in paths]
    w = ims[0].width
    rows = [ims[i:i + cols] for i in range(0, len(ims), cols)]   # row by row, so 1, 2, 3… read in order
    H = gap + sum(max(i.height for i in r) + gap for r in rows)
    sheet = Image.new("RGB", (cols * w + (cols + 1) * gap, H), bg)
    y = gap
    for r in rows:
        for c, im in enumerate(r):
            sheet.paste(im, (gap + c * (w + gap), y))
        y += max(i.height for i in r) + gap
    sheet.save(dst, quality=88, optimize=True)
    return sheet.size


def main(langs):
    for lang in langs:
        ps = importlib.import_module(f"testers24.{lang}").PILLS
        rel = lambda p: f"testers24/{lang}/{p['slug']}.png"   # noqa: E731
        jobs = [Job(f"t24_{p['slug']}_{lang}", html(lang, p, i, len(ps)), 1080, None, 1, [(rel(p), None)])
                for i, p in enumerate(ps, 1)]
        for r, size in render(jobs, dry=True):
            print(f"{size[0]}×{size[1]}  {r}")
        d = os.path.join(OUT, "dry", "testers24", lang)
        print(collage([os.path.join(OUT, "dry", rel(p)) for p in ps], os.path.join(d, "collage.jpg")), "collage")


if __name__ == "__main__":
    main(sys.argv[1:] or ["es"])
    for lang in sys.argv[1:] or ["es"]:
        compact(lang)


COMPACT = """
body{background:var(--bg)}
.sheet{width:1800px;padding:34px 34px 20px;background:var(--bg)}
.head{display:flex;align-items:baseline;gap:18px;margin:0 6px 22px}
.head b{font-family:'ArchivoBlack';font-weight:400;font-size:44px;color:var(--text)}
.head span{font-size:22px;color:var(--text2)}
.cols{column-count:3;column-gap:22px}
.pl{width:auto;padding:24px 24px 18px;border-radius:24px;break-inside:avoid;margin-bottom:22px;
  border:2px solid #2E3B32}
.tag{font-size:16px;letter-spacing:1.6px;gap:8px}.tag i{font-size:18px}
h1{font-size:33px;margin-top:8px}
.sub{font-size:18px;margin-top:8px;line-height:1.38}
.steps{margin-top:16px;gap:8px}
.st{gap:14px;border-radius:16px;padding:11px 14px}
.n{width:32px;height:32px;font-size:16px;margin-top:1px}
.tx{font-size:18.5px;line-height:1.4;padding-top:2px}
.chip{border-width:1.5px;border-radius:8px;padding:0 7px 1px}
.note{margin-top:14px;border-left-width:5px;padding:2px 0 2px 14px;font-size:17.5px;line-height:1.4}
.ft{margin-top:12px;font-size:15px}
"""


def compact(lang):
    """One dense sheet with every pill (3 columns), for sending as a WhatsApp/Telegram attachment."""
    s = Strings(lang)
    ps = importlib.import_module(f"testers24.{lang}").PILLS
    cards = []
    for i, p in enumerate(ps, 1):
        steps = "".join(f'<div class="st"><div class="n">{k}</div><div class="tx">{markup(s, t)}</div></div>'
                        for k, t in enumerate(p["steps"], 1))
        note = f'<div class="note">{markup(s, p["note"])}</div>' if p.get("note") else ""
        foot = markup(s, p["foot"], chips=False) if p.get("foot") else ""
        cards.append(f'<div class="pl"><div class="tag"><i>{p["icon"]}</i>{i} · {esc(p["tag"])}</div>'
                     f'<h1>{markup(s, p["title"], chips=False)}</h1><div class="sub">{markup(s, p["sub"])}</div>'
                     f'<div class="steps">{steps}</div>{note}<div class="ft">{foot}</div></div>')
    doc = (f'<!doctype html><html lang="{lang}"><head><meta charset="utf-8"><style>{root_css()}{CSS}{COMPACT}'
           f'</style></head><body><div class="sheet"><div class="head"><b>RideScreen AA 2.4</b>'
           f'<span>{esc(HEAD[lang])}</span></div><div class="cols">{"".join(cards)}</div></div></body></html>')
    rel = f"testers24/{lang}/novedades-2.4-compacto.png"
    for r, size in render([Job(f"t24_compact_{lang}", doc, 1800, None, 1.5, [(rel, None)])], dry=True):
        print(f"{size[0]}×{size[1]}  {r}")
    src = os.path.join(OUT, "dry", rel)
    Image.open(src).convert("RGB").save(src[:-4] + ".jpg", quality=86, optimize=True, progressive=True)


HEAD = {"es": "versión de pruebas · novedades frente a la 2.3 y cómo poner el keyless"}
