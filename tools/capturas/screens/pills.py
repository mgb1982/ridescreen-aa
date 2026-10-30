"""Pills ("píldoras"): one-feature how-to cards, 1080 px wide, height follows the content.

Texts live in tools/capturas/pills/<lang>.py, one list per language in the same order. Markup inside a text:

    **bold**                 bold (an app label inside it is bold text, not a button)
    [[key]]                  a button/label of the app, taken from strings.xml in that language
    [[key|🚦]]               same, with an emoji in front (when the string itself has none)
    [[key(3)]]               a string with arguments: %1$s/%1$d get 3
    [[=text]]                a label that is not ours (Android, Android Auto, ⋮): literal text
    [[w:key]]                a label of the watch app (wear/…/strings.xml)

Footers render labels as plain text. Every pill is numbered N/total automatically.
"""
import importlib
import re

from lib.base import root_css
from lib.res import WEAR_RES, Strings, esc

W = 1080
WORD = {"es": "píldora", "ca": "píndola", "en": "tip", "it": "pillola", "el": "συμβουλή", "ru": "совет"}

CSS = """
.pl{width:1080px;padding:64px 60px 52px;background:var(--bg);color:var(--text)}
.tag{display:flex;align-items:center;gap:14px;font-weight:700;font-size:29px;letter-spacing:2.6px;color:var(--gold);
  text-transform:uppercase}
.tag i{font-style:normal;letter-spacing:0;font-size:32px}
h1{font-family:'ArchivoBlack';font-weight:400;font-size:70px;line-height:1.04;margin-top:16px;letter-spacing:.2px}
.sub{font-size:32px;line-height:1.45;color:var(--text2);margin-top:20px}
.steps{margin-top:44px;display:flex;flex-direction:column;gap:22px}
.st{display:flex;gap:30px;align-items:flex-start;background:var(--surface);border-radius:30px;padding:30px 34px 30px 34px}
.n{flex:none;width:72px;height:72px;border-radius:50%;background:var(--gold);color:var(--on-brand);display:flex;
  align-items:center;justify-content:center;font-family:'ArchivoBlack';font-size:34px;margin-top:-2px}
.tx{font-size:33px;line-height:1.48;padding-top:2px}
.tx b,.note b{font-weight:600;color:var(--text)}
.chip{display:inline;background:#2E3B32;border:2px solid #4A5C4E;border-radius:14px;padding:3px 14px 5px;
  font-weight:600;white-space:nowrap;box-decoration-break:clone;-webkit-box-decoration-break:clone}
.note{margin-top:40px;border-left:8px solid var(--gold);padding:4px 0 4px 30px;font-size:31px;line-height:1.45;
  color:var(--text2)}
.note .chip{color:var(--text)}
.ft{margin-top:44px;text-align:center;font-size:26px;color:#7F8C7C}
"""

TOKEN = re.compile(r"\[\[(.+?)\]\]|\*\*(.+?)\*\*")
ARGS = re.compile(r"^([a-z0-9_]+)\((.*)\)$")


def label(s, spec):
    """Resolve a [[...]] spec to its visible text."""
    if spec.startswith("="):
        return spec[1:]
    if spec.startswith("w:"):
        s, spec = Strings(s.lang, WEAR_RES), spec[2:]
    emoji = ""
    if "|" in spec:
        spec, emoji = spec.split("|", 1)
    m = ARGS.match(spec)
    text = s(m.group(1), *[a.strip() for a in m.group(2).split(",")]) if m else s(spec)
    if re.search(r"%(\d+\$)?[sdf]", text):
        raise ValueError(f"[[{spec}]] needs arguments: {text!r}")
    return f"{emoji} {text}" if emoji else text


def markup(s, text, chips=True):
    out, pos = [], 0
    for m in TOKEN.finditer(text):
        out.append(esc(text[pos:m.start()]))
        if m.group(1) is not None:
            t = esc(label(s, m.group(1)))
            out.append(f'<span class="chip">{t}</span>' if chips else t)
        else:
            out.append(f"<b>{markup(s, m.group(2), chips=False)}</b>")   # **[[key]]** = app text in bold, no box
        pos = m.end()
    out.append(esc(text[pos:]))
    return "".join(out)


def texts(lang):
    return importlib.import_module(f"pills.{lang}").PILLS


def pill(lang, n):
    """n is 1-based."""
    s = Strings(lang)
    ps = texts(lang)
    if len(ps) != len(texts("es")):
        raise ValueError(f"pills/{lang}.py has {len(ps)} pills, pills/es.py has {len(texts('es'))}")
    p = ps[n - 1]
    steps = "".join(f'<div class="st"><div class="n">{i}</div><div class="tx">{markup(s, t)}</div></div>'
                    for i, t in enumerate(p["steps"], 1))
    note = f'<div class="note">{markup(s, p["note"])}</div>' if p.get("note") else ""
    foot = (markup(s, p["foot"], chips=False) + " · " if p.get("foot") else "") + f"{WORD[lang]} {n}/{len(ps)}"
    # lang= lets Chromium uppercase Greek without accents (ΞΕΚΙΝΗΜΑ, not ΞΕΚΊΝΗΜΑ).
    return f"""<!doctype html><html lang="{lang}"><head><meta charset="utf-8"><style>{root_css()}{CSS}</style></head><body>
<div class="pl"><div class="tag"><i>{p["icon"]}</i>RideScreen AA · {esc(p["tag"])}</div>
<h1>{markup(s, p["title"], chips=False)}</h1><div class="sub">{markup(s, p["sub"])}</div>
<div class="steps">{steps}</div>{note}<div class="ft">{foot}</div></div></body></html>"""


def slugs():
    """File names (same in every language, from the Spanish list)."""
    return [p["slug"] for p in texts("es")]
