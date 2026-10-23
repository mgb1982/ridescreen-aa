"""Read the app's own resources (colours, strings, fonts) so captures never drift from the code."""
import glob
import html
import os
import re
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
APP_RES = os.path.join(ROOT, "app", "src", "main", "res")
WEAR_RES = os.path.join(ROOT, "wear", "src", "main", "res")
FONT_DIR = os.path.join(APP_RES, "font")

# Language code → Android values folder suffix ("" = default, which is English).
LANG_DIR = {"es": "-es", "ca": "-ca", "en": "", "it": "-it"}


def colors():
    out = {}
    for f in glob.glob(os.path.join(APP_RES, "values", "colors*.xml")):
        for c in ET.parse(f).getroot().iter("color"):
            v = c.text.strip()
            if v.startswith("#") and len(v) == 9:  # #AARRGGBB → #RRGGBB (alpha FF in the palette)
                v = "#" + v[3:]
            out[c.get("name")] = v
    return out


def _unescape(s):
    s = s.replace("\\n", "\n").replace("\\'", "'").replace('\\"', '"').replace("\\@", "@")
    s = re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), s)
    if len(s) >= 2 and s[0] == '"' and s[-1] == '"':
        s = s[1:-1]
    return s


def _text(el):
    # Keep inline markup (<b>, <i>) as raw text so callers can render it.
    raw = (el.text or "") + "".join(ET.tostring(ch, encoding="unicode") for ch in el)
    return _unescape(raw.strip())


class Strings:
    """Merged strings for one language, falling back to the default (English) values."""

    def __init__(self, lang, res=APP_RES):
        self.lang = lang
        self.s, self.p = {}, {}
        for folder in ["values", "values" + LANG_DIR[lang]] if LANG_DIR[lang] else ["values"]:
            for f in sorted(glob.glob(os.path.join(res, folder, "strings*.xml"))):
                root = ET.parse(f).getroot()
                for el in root.iter("string"):
                    self.s[el.get("name")] = _text(el)
                for el in root.iter("plurals"):
                    self.p[el.get("name")] = {i.get("quantity"): _text(i) for i in el.iter("item")}

    def __call__(self, key, *args):
        v = self.s[key]
        return fmt(v, *args) if args else v

    def plural(self, key, n, *args):
        forms = self.p[key]
        v = forms.get("one" if n == 1 else "other") or forms["other"]
        return fmt(v, *(args or (n,)))


def fmt(template, *args):
    """Android-style %1$s / %1$d / %s / %d substitution."""
    seq = iter(args)

    def rep(m):
        idx, conv = m.group(1), m.group(2)
        v = args[int(idx) - 1] if idx else next(seq)
        return str(v)

    return re.sub(r"%(?:(\d+)\$)?[-0-9.]*([sdf])", rep, template).replace("%%", "%")


def esc(s):
    """HTML-escape but keep <b>/<i> that come from string resources."""
    s = html.escape(s, quote=False)
    return re.sub(r"&lt;(/?)(b|i)&gt;", r"<\1\2>", s)


ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def icon(name, color="currentColor", size=22, res=APP_RES):
    """Android <vector> drawable → inline <svg>, so captures use the app's own icons."""
    p = os.path.join(res, "drawable", name + ".xml")
    root = ET.parse(p).getroot()
    a = lambda el, k, d=None: el.get(ANDROID_NS + k, d)
    vw, vh = a(root, "viewportWidth"), a(root, "viewportHeight")
    parts = []

    def walk(el):
        for ch in el:
            if ch.tag == "path":
                d = a(ch, "pathData")
                fill = a(ch, "fillColor")
                stroke = a(ch, "strokeColor")
                sw = a(ch, "strokeWidth", "0")
                f = color if fill else "none"
                s = f' stroke="{color}" stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round"' if stroke else ""
                parts.append(f'<path d="{d}" fill="{f}"{s} fill-rule="{"evenodd" if a(ch, "fillType") == "evenOdd" else "nonzero"}"/>')
            elif ch.tag == "group":
                tr = []
                tx, ty = a(ch, "translateX"), a(ch, "translateY")
                if tx or ty:
                    tr.append(f"translate({tx or 0} {ty or 0})")
                inner_start = len(parts)
                walk(ch)
                if tr:
                    parts[inner_start:] = [f'<g transform="{" ".join(tr)}">' + "".join(parts[inner_start:]) + "</g>"]
    walk(root)
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 {vw} {vh}" style="display:block">'
            + "".join(parts) + "</svg>")
