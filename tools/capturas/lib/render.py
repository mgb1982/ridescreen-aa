"""Render HTML jobs with Chromium (Playwright) and export every published size from one master."""
import os
import shutil
from PIL import Image

from .res import ROOT

OUT = os.path.join(os.path.dirname(__file__), "..", "out")


def chromium_path():
    env = os.environ.get("CAPTURAS_CHROMIUM")
    if env:
        return env
    base = "/opt/pw-browsers"
    if os.path.isdir(base):
        for d in sorted(os.listdir(base), reverse=True):
            p = os.path.join(base, d, "chrome-linux", "chrome")
            if d.startswith("chromium-") and os.path.exists(p):
                return p
    return None  # let Playwright use its own download


class Job:
    """One screen in one language → one master PNG → N published files.

    outputs: list of (relative path from repo root, width or None for master size).
    Height scales with width. .png = truecolour PNG with alpha (never palette), .webp = WebP q90,
    .jpg = JPEG q90 (photos only).
    """

    def __init__(self, name, html, w, h, dpr, outputs):
        self.name, self.html, self.w, self.h, self.dpr, self.outputs = name, html, w, h, dpr, outputs


def render(jobs, dry=False):
    from playwright.sync_api import sync_playwright
    os.makedirs(os.path.join(OUT, "html"), exist_ok=True)
    os.makedirs(os.path.join(OUT, "masters"), exist_ok=True)
    written = []
    with sync_playwright() as p:
        kw = {}
        cp = chromium_path()
        if cp:
            kw["executable_path"] = cp
        br = p.chromium.launch(**kw)
        for j in jobs:
            hp = os.path.abspath(os.path.join(OUT, "html", j.name + ".html"))
            with open(hp, "w", encoding="utf-8") as f:
                f.write(j.html)
            pg = br.new_page(viewport={"width": j.w, "height": j.h}, device_scale_factor=j.dpr)
            pg.goto("file://" + hp)
            pg.evaluate("document.fonts.ready")
            pg.wait_for_timeout(250)
            mp = os.path.join(OUT, "masters", j.name + ".png")
            pg.screenshot(path=mp, omit_background=True, clip={"x": 0, "y": 0, "width": j.w, "height": j.h})
            pg.close()
            master = Image.open(mp).convert("RGBA")
            for rel, width in j.outputs:
                dst = os.path.join(ROOT, rel)
                im = master if not width else master.resize(
                    (width, round(master.height * width / master.width)), Image.LANCZOS)
                if dry:
                    dst = os.path.join(OUT, "dry", rel)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                ext = os.path.splitext(dst)[1].lower()
                if im.getextrema()[3][0] == 255:   # fully opaque (phone screens): no alpha channel needed
                    im = im.convert("RGB")
                if ext == ".png":
                    im.save(dst, optimize=True)
                elif ext == ".webp":
                    im.save(dst, quality=90, method=6)
                elif ext in (".jpg", ".jpeg"):
                    im.convert("RGB").save(dst, quality=90, optimize=True, progressive=True)
                written.append((rel, im.size))
        br.close()
    return written


def clean():
    shutil.rmtree(OUT, ignore_errors=True)
