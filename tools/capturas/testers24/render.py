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
