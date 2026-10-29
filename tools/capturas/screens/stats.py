"""Phone screens 16–23 (v2.3): heat map, red lights, same-trip routes, trip video, trophies, maintenance and
the year on the bike. Built from the activities' own code (sizes in dp/sp = CSS px here), texts from strings.xml,
numbers from lib/demo.py.

Android's Typeface.DEFAULT_BOLD (card titles in TrafficLightsActivity / TrophiesActivity) is Roboto Bold, not
Archivo: that is reproduced on purpose.
"""
import datetime as dt
import math

from lib import demo as D
from lib.base import phone_page
from lib.maps import ROUTE, svg as map_svg
from lib.res import Strings, esc

ROBOTO_BOLD = "font-family:'Roboto';font-weight:700"

COMMON = """
.pg{position:absolute;left:0;right:0;top:0}
.h24{font-family:'ArchivoBlack';font-size:24px;line-height:1.25}
.b13{font-size:13px;line-height:1.38;color:var(--text2);padding-top:6px}
.hdr{font-size:16px;font-weight:700;color:var(--text);padding:18px 0 4px}
.btn{display:flex;align-items:center;justify-content:center;min-height:40px;border:1px solid var(--outline);
  border-radius:999px;font-size:14px;font-weight:500;color:var(--text);padding:0 12px;text-align:center}
.btn.f{background:var(--gold);border-color:var(--gold);color:var(--on-brand)}
.row{display:flex;gap:8px}
.row>*{flex:1}
"""


def _scrolled(body, offset, css, bg="var(--bg)"):
    """A ScrollView scrolled down by [offset] px under the status bar."""
    # The ScrollView sits below the status bar (insets as padding): nothing shows through it.
    return phone_page(f'<div class="pg" style="top:{30 - offset}px">{body}</div>'
                      f'<div style="position:absolute;left:0;right:0;top:0;height:30px;background:{bg};z-index:40"></div>',
                      extra_css=COMMON + css, bg=bg)


# ---------------------------------------------------------------- 16 · all rides, heat mode (TripMapActivity.showAll)

# Extra paths (1000-box) for the rest of the history; weights ~ how often each is ridden.
OTHER_PATHS = [
    ([(270, 250), (300, 262), (338, 276), (372, 296), (396, 318), (410, 342)], 0.55),   # around Sants
    ([(270, 250), (232, 236), (198, 212), (176, 180), (170, 140)], 0.35),               # up to the park
    ([(270, 250), (322, 238), (380, 222), (446, 206), (512, 190), (560, 178)], 0.45),   # towards the Eixample
    ([(760, 680), (790, 720), (812, 770), (830, 820)], 0.25),                           # Zona Franca, further
    ([(410, 342), (452, 360), (500, 372), (548, 380)], 0.2),
]


def _heat_cells():
    cells = {}

    def add(path, w):
        for (x0, y0), (x1, y1) in zip(path, path[1:]):
            n = max(1, int(math.hypot(x1 - x0, y1 - y0) / 6))
            for i in range(n + 1):
                x, y = x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n
                k = (round(x / 6), round(y / 6))
                cells[k] = max(cells.get(k, 0), w)
    add([(x, y) for x, y, _ in ROUTE], 1.0)
    for p, w in OTHER_PATHS:
        add(p, w)
    return [(kx * 6, ky * 6, w) for (kx, ky), w in cells.items()]


def heat_map(lang):
    from lib.maps import ramp
    s = Strings(lang)
    cells = sorted(_heat_cells(), key=lambda c: c[2])         # hottest on top
    r = 7.0
    overlay = "".join(f'<circle cx="{x}" cy="{y}" r="{r * 1.9}" fill="{ramp(w)}" fill-opacity="{70 / 255:.3f}"/>'
                      f'<circle cx="{x}" cy="{y}" r="{r}" fill="{ramp(w)}" fill-opacity="{210 / 255:.3f}"/>'
                      for x, y, w in cells)
    from lib.maps import fit_bbox
    bbox = fit_bbox([(x, y) for x, y, _ in cells], 412 / 750, pad=0.08)
    stats = s("all_rides_stats", D.CAREER["trips"], D.num(lang, D.CAREER["km"], 0))
    seg = [("map_mode_routes", False), ("map_mode_heat", True), None,
           ("map_period_month", False), ("map_period_year", False), ("map_period_all", True)]
    segs = "".join('<i class="sep"></i>' if x is None else
                   f'<span class="sg" style="opacity:{1 if x[1] else .5}">{esc(s(x[0]))}</span>' for x in seg)
    css = """
.top{padding:12px 16px 0;display:flex;align-items:center;gap:10px}
.top .t{flex:1}
.top h1{font-weight:800;font-size:20px}
.top p{font-size:13px;color:var(--text2);margin-top:2px}
.pill{background:var(--surface-high);border-radius:999px;padding:12px 18px;font-size:14px;font-weight:500}
.segs{display:flex;align-items:center;gap:4px;padding:10px 16px 12px}
.sg{flex:1;min-height:44px;border-radius:12px;background:var(--surface-high);display:flex;align-items:center;
  justify-content:center;font-size:13px;font-weight:500;text-align:center}
.sep{width:1px;align-self:stretch;background:var(--text2);opacity:.3;margin:0 6px}
.mp{position:absolute;left:0;right:0;top:108px;bottom:0}
"""
    body = (f'<div class="top"><div class="t"><h1>{esc(s("all_rides_title"))}</h1><p>{esc(stats)}</p></div>'
            f'<span class="pill">{esc(s("share_button"))}</span></div><div class="segs">{segs}</div>'
            f'<div class="mp">{map_svg(412, 760, "light", None, bbox=bbox, markers=False, overlay=overlay)}</div>')
    return phone_page(f'<div class="pg" style="top:30px;bottom:0">{body}</div>', extra_css=COMMON + css)


# ---------------------------------------------------------------- 17/18 · your red lights (TrafficLightsActivity)

# Markers in the 1000-box: (x, y, stop rate, passes). The named ones are the "worst" list.
TL_MARKERS = [
    (403, 411, .81, 21), (618, 554, .63, 19), (318, 326, .40, 20), (692, 605, .28, 18), (297, 297, .55, 12),
    (343, 354, .22, 15), (465, 466, .45, 9), (525, 512, .18, 14), (740, 658, .33, 8), (338, 276, .12, 11),
    (396, 318, .64, 7), (232, 236, .10, 6), (380, 222, .35, 9), (446, 206, .5, 5), (512, 190, .2, 6),
    (452, 360, .15, 4), (790, 720, .4, 3), (176, 180, .6, 3), (560, 178, .25, 4), (548, 380, .1, 3),
]


def _rate_col(rate):
    return "rgb(229,57,53)" if rate >= .6 else "rgb(251,192,45)" if rate >= .3 else "rgb(67,160,71)"


def _tl_page(lang):
    s = Strings(lang)
    dirs = s.a["tl_dirs"]
    bbox = (130, 150, 860, 760)
    scale = (bbox[2] - bbox[0]) / 380
    overlay = "".join(
        f'<circle cx="{x}" cy="{y}" r="{(5 + 9 * min(n, 20) / 20) * scale:.1f}" fill="{_rate_col(r)}" '
        f'stroke="#fff" stroke-width="{1.15 * scale:.2f}"/>' for x, y, r, n in TL_MARKERS)

    def card(title, text, accent):
        return (f'<div class="cd" style="border-color:{accent}"><div class="ct">{esc(title)}</div>'
                f'<div class="cx">{esc(text)}</div></div>')

    worst = ""
    for name, stops, passes, wait, d, ds, dp, cyc, fit in D.TL["worst"]:
        t = s("tl_line", stops, passes, round(stops * 100 / passes), wait) + "\n" + s("tl_main_dir", dirs[d], ds, dp)
        if cyc:
            t += "\n" + s("tl_cycle", cyc, fit)
        worst += card(name, t, _rate_col(stops / passes))
    wave = ""
    for name, d, ok, pred, unsure, soon, soon_n, cyc in D.TL["wave"]:
        t = (s("gw_hits", ok, pred, round(ok * 100 / pred), unsure) + "\n" + s("gw_soon", soon, soon_n) + "\n"
             + s("gw_cycle", cyc) + "\n" + s("gw_learning", 5, 80))
        wave += card(f"{name} · {dirs[d]}", t, "rgb(251,192,45)")
    routes = ""
    for name, variants in D.TL["routes"]:
        best = min(range(len(variants)), key=lambda i: variants[i][1])
        lines = [("🏆 " if i == best else "") + s("tl_variant", i + 1, c, m, st, D.num(lang, km))
                 for i, (c, m, st, km) in enumerate(variants)]
        routes += card(name, "\n".join(lines) + "\n" + s("tl_variant_hint"), "var(--gold)")
    css = f"""
.tl{{padding:12px 16px 28px}}
.mapc{{height:300px;border-radius:18px;overflow:hidden;margin-top:10px}}
.cd{{margin-top:8px;background:var(--surface);border:1px solid;border-radius:16px;padding:10px 14px}}
.ct{{{ROBOTO_BOLD};font-size:15px}}
.cx{{font-size:13px;line-height:1.38;color:var(--text2);white-space:pre-line}}
.tl .hdr{{{ROBOTO_BOLD}}}
"""
    body = (f'<div class="tl"><div class="h24">{esc(s("tl_title"))}</div>'
            f'<div class="b13">{esc(s("tl_summary", D.TL["trips"], D.TL["junctions"]))}</div>'
            f'<div class="mapc">{map_svg(380, 300, "light", None, bbox=bbox, markers=False, dim=False, overlay=overlay)}</div>'
            f'<div class="b13">{esc(s("tl_tap_hint"))}</div>'
            f'<div class="hdr">{esc(s("tl_worst_title"))}</div>{worst}'
            f'<div class="hdr">{esc(s("gw_title"))}</div><div class="b13">{esc(s("gw_intro"))}</div>{wave}'
            f'<div class="hdr" id="routes">{esc(s("tl_routes_title"))}</div>{routes}'
            f'<div class="b13">{esc(s("tl_footer"))}</div></div>')
    return body, css


def _scroll_to(html, anchor_id, margin):
    """Scroll so [anchor_id] sits [margin] px below the top, but never past the end (ScrollView clamps)."""
    js = ("<script>document.fonts.ready.then(()=>{const p=document.querySelector('.pg');"
          f"const h=document.getElementById('{anchor_id}');"
          f"const y=Math.max(0,Math.min(h.offsetTop-{margin},p.offsetHeight-885));"
          "p.style.top=(30-y)+'px';});</script>")
    return html.replace("</body>", js + "</body>")


def lights(lang):
    body, css = _tl_page(lang)
    return _scrolled(body, 0, css)


def routes(lang):
    """Same page, scrolled so "same trip, different routes" sits at the top (measured in the browser)."""
    body, css = _tl_page(lang)
    html = _scrolled(body, 0, css)
    return _scroll_to(html, "routes", 4)   # offset depends on how the texts wrap in each language


# ---------------------------------------------------------------- 19 · trip video (TripVideoActivity), scrolled down

def video(lang):
    s = Strings(lang)
    sync = "".join(f'<span class="btn">{l}</span>' for l in ("−5 s", "−1 s", "+1 s", "+5 s"))
    css = """
.tv{padding:12px 20px 28px}
.sw{display:flex;align-items:center;justify-content:space-between;gap:12px;min-height:48px;font-size:14px}
.tg{width:52px;height:32px;border-radius:999px;background:var(--gold);position:relative;flex:none}
.tg:after{content:"";position:absolute;right:6px;top:6px;width:20px;height:20px;border-radius:50%;background:var(--on-brand)}
.ol{font-size:15px;text-align:center;padding:8px 0 2px}
.sync{display:flex;gap:6px;margin-top:4px}.sync .btn{flex:1}
.pb{height:4px;background:var(--gold);border-radius:2px;margin-top:12px}
.chips{display:flex;gap:6px;overflow:hidden;white-space:nowrap;margin-top:4px}
.chips .btn{flex:none}
"""
    clip_n, total = len(D.CLIPS), sum(c[1] for c in D.CLIPS)
    # VideoStyle / VideoMap chips; defaults CLASSIC and LINE, the rest at alpha .45 (markLook)
    styles = "".join(f'<span class="btn" style="opacity:{1 if i == 0 else .45}">{esc(s(k))}</span>' for i, k in enumerate(
        ["tvs_classic", "tvs_sport", "tvs_minimal", "tvs_cinema", "tvs_retro", "tvs_telemetry"]))
    maps = "".join(f'<span class="btn" style="opacity:{1 if i == 0 else .45}">{esc(s(k))}</span>'
                   for i, k in enumerate(["tvm_line", "tvm_speed", "tvm_streets"]))
    body = (f'<div class="tv" id="tv">'
            f'<div class="h24">{esc(s("tv_title"))}</div>'
            f'<div class="b13">{esc(D.TRIP["name"][lang])} · {D.TRIP["start"]}–{D.TRIP["end"]}</div>'
            f'<div class="b13">{esc(s("tv_how"))}</div>'
            f'<div class="b13" style="color:var(--text);font-size:15px">{esc(s("tv_found", clip_n, total // 60, total % 60))}</div>'
            f'<div style="height:520px"></div>'   # clips, your videos and the live preview (not part of this capture)
            f'<div class="b13" id="cut">{esc(s("tv_style"))}</div><div class="chips">{styles}</div>'
            f'<div class="b13">{esc(s("tv_map"))}</div><div class="chips">{maps}</div>'
            f'<div class="sw"><span>{esc(s("tv_cards"))}</span><span class="tg"></span></div>'
            f'<div class="sw"><span>{esc(s("tv_watermark"))}</span><span class="tg"></span></div>'
            f'<div class="hdr">{esc(s("tv_speed_title"))}</div>'
            f'<div class="row" style="gap:6px;padding-top:4px"><span class="btn">{esc(s("tv_speed_normal"))}</span>'
            f'<span class="btn" style="opacity:.5">{esc(s("tv_speed_fast"))}</span></div>'
            f'<div class="hdr">{esc(s("tv_sync_title"))}</div><div class="b13">{esc(s("tv_sync_help"))}</div>'
            f'<div class="ol">{esc(s("tv_sync_zero"))}</div><div class="sync">{sync}</div>'
            f'<div class="hdr">{esc(s("tv_music_title"))}</div><div class="b13">{esc(s("tv_music_none"))}</div>'
            f'<div class="row" style="margin-top:4px"><span class="btn">{esc(s("tv_music_pick"))}</span></div>'
            f'<span class="btn" style="margin-top:8px">{esc(s("tv_make"))}</span>'
            f'<span class="btn" style="margin-top:4px">{esc(s("tv_highlights"))}</span>'
            f'<div class="b13">{esc(s("tv_highlights_help"))}</div><div class="pb"></div>'
            f'<div class="row" style="gap:6px;margin-top:6px"><span class="btn">{esc(s("tv_open"))}</span>'
            f'<span class="btn">{esc(s("share_button"))}</span></div>'
            f'<div class="b13">{esc(s("tv_meta_tip"))}</div></div>')
    html = _scrolled(body, 0, css)
    return _scroll_to(html, "cut", 4)


# ---------------------------------------------------------------- 20/21 · trophies (TrophiesActivity)

def _trophies():
    """[(family row, tier index, target, progress, earned datetime or None)] in Trophies.compute order."""
    out = []
    for fam in D.TROPHY_FAMILIES:
        name, emoji, tk, dk, tiers, unit, value = fam
        for i, t in enumerate(tiers):
            out.append((fam, i, t, min(value, t), D.TROPHY_EARNED.get((name, t))))
    earned = [x for x in out if x[4]]
    assert len(earned) == D.TROPHIES[0] and len(out) == D.TROPHIES[1], "demo trophies out of step with TROPHIES"
    return out


def _medal(fam, tier):
    tiers = fam[4]
    if len(tiers) == 1:
        return fam[1]
    if tier >= len(tiers) - 1:
        return "🏆"
    return ["🥉", "🥈", "🥇"][min(tier, 2)]


def _tr_card(s, lang, t):
    fam, tier, target, prog, at = t
    desc = s(fam[3]) if len(fam[4]) == 1 else s(fam[3], D.num(lang, target, 0))
    extra = ""
    if at:
        extra = f'<div class="eo">{esc(s("tr_earned_on", D.medium_date(lang, at.date())))}</div>'
    elif len(fam[4]) > 1:
        unit = f" {fam[5]}" if fam[5] else ""
        extra = (f'<div class="pr"><i style="width:{prog / target * 100:.1f}%"></i></div>'
                 f'<div class="pt">{D.num(lang, int(prog), 0)} / {D.num(lang, target, 0)}{unit}</div>')
    cls = "tc on" if at else "tc"
    return (f'<div class="{cls}"><div class="md">{_medal(fam, tier) if at else "🔒"}</div>'
            f'<div class="col"><div class="tt">{esc(s(fam[2]))}</div><div class="td">{esc(desc)}</div>{extra}</div></div>')


TR_CSS = f"""
.tr{{padding:12px 20px 24px}}
.hd{{font-size:14px;color:var(--text2);padding:4px 0 8px}}
.tc{{display:flex;align-items:center;margin-top:10px;background:var(--surface);border-radius:18px;padding:12px 14px;opacity:.72}}
.tc.on{{border:1px solid var(--gold);opacity:1}}
.md{{font-size:30px;padding-right:14px;line-height:1}}
.col{{flex:1}}
.tt{{{ROBOTO_BOLD};font-size:16px}}
.td{{font-size:13px;color:var(--text2)}}
.eo{{font-size:12px;color:var(--gold);padding-top:4px}}
.pr{{height:8px;background:rgba(255,255,255,.2);margin-top:6px;border-radius:4px;overflow:hidden}}
.pr i{{display:block;height:100%;background:var(--gold)}}
.pt{{font-size:12px;color:var(--text2)}}
.nx{{font-size:13px;color:var(--text2);padding:18px 0 2px}}
"""


def _tr_page(lang):
    s = Strings(lang)
    all_t = _trophies()
    earned = sorted([t for t in all_t if t[4]], key=lambda t: t[4], reverse=True)
    locked = {}
    for t in all_t:
        if not t[4] and t[0][0] not in locked:
            locked[t[0][0]] = t
    nxt = sorted(locked.values(), key=lambda t: t[3] / t[2], reverse=True)
    body = (f'<div class="tr"><div class="h24">{esc(s("tr_screen_title"))}</div>'
            f'<div class="hd">{esc(s("tr_header", len(earned), len(all_t)))}</div>'
            + "".join(_tr_card(s, lang, t) for t in earned)
            + f'<div class="nx" id="next">{esc(s("tr_next"))}</div>'
            + "".join(_tr_card(s, lang, t) for t in nxt) + "</div>")
    return body


def trophies(lang):
    return _scrolled(_tr_page(lang), 0, TR_CSS)


def trophies_next(lang):
    html = _scrolled(_tr_page(lang), 0, TR_CSS)
    return _scroll_to(html, "next", 2)


# ---------------------------------------------------------------- 22 · maintenance (MaintenanceActivity)

def _maint_status(lang, s, task):
    key, every_km, every_m, last_km, last_at = task
    odo, now = D.MAINT_BOOK["odometer"], D.NOW
    km_left = days_left = None
    frac = 0.0
    if every_km and last_km is not None:
        km_left = last_km + every_km - odo
        frac = max(frac, (odo - last_km) / every_km)
    if every_m and last_at:
        y, m = divmod(last_at.month - 1 + every_m, 12)
        due = dt.datetime(last_at.year + y, m + 1, last_at.day)
        start = dt.datetime.combine(last_at, dt.time())
        days_left = (due - now).days
        frac = max(frac, (now - start) / (due - start))
    if km_left is None and days_left is None:
        return "unknown", s("maint_status_unknown"), 0.0
    if (km_left is not None and km_left <= 0) or (days_left is not None and days_left <= 0):
        level = "due"
    elif (km_left is not None and km_left <= 300) or (days_left is not None and days_left <= 15):
        level = "soon"
    else:
        level = "ok"
    if km_left is not None and (days_left is None or km_left / 30 < days_left):
        text = s("maint_status_km_left", D.km(lang, km_left, 0))
    else:
        text = s("maint_status_days_left", days_left)
    return level, text, min(max(frac, 0), 1)


def maintenance(lang):
    s = Strings(lang)
    col = {"due": "var(--error)", "soon": "var(--busy)", "ok": "var(--live)", "unknown": "var(--text2)"}
    rows = []
    for i, t in enumerate(D.MAINT_BOOK["tasks"]):
        level, text, frac = _maint_status(lang, s, t)
        rows.append((-frac, i, t, level, text, frac))
    cards = ""
    for _, _, (key, every_km, every_m, last_km, last_at), level, text, frac in sorted(rows):
        parts = []
        if every_km:
            parts.append(s("maint_every_km", D.km(lang, every_km, 0)))
        if every_m:
            parts.append(s("maint_every_months", every_m))
        last = ([D.km(lang, last_km, 0)] if last_km is not None else []) + \
               ([D.medium_date(lang, last_at)] if last_at else [])
        interval = " · ".join(parts) + ("\n" + s("maint_last", " · ".join(last)) if last else "")
        bar = (f'<div class="lp"><i style="width:{frac * 100:.0f}%;background:{col[level]}"></i></div>'
               if level != "unknown" else "")
        cards += (f'<div class="mc"><div class="mt">{esc(s(key))}</div>'
                  f'<div class="ms" style="color:{col[level]}">{esc(text)}</div>{bar}'
                  f'<div class="mi">{esc(interval)}</div><div class="row" style="margin-top:8px">'
                  f'<span class="btn f">{esc(s("maint_done"))}</span><span class="btn">{esc(s("maint_edit"))}</span></div></div>')
    css = """
.mn{padding:12px 20px 28px}
.mn h1{font-size:22px;font-weight:800}
.bk{font-size:13px;color:var(--gold);margin-top:2px}
.mc{margin-top:10px;background:var(--surface);border-radius:16px;padding:14px 16px}
.lbl{font-size:12px;letter-spacing:.96px;color:var(--text2);text-transform:uppercase}
.odo{font-family:'ArchivoBlack';font-size:30px;line-height:1.2;margin-top:2px}
.hint{font-size:12px;color:var(--text2);margin-top:2px}
.sec{font-size:12px;letter-spacing:.96px;color:var(--text2);text-transform:uppercase;margin-top:22px}
.mt{font-size:16px;font-weight:800}
.ms{font-size:13px;margin-top:2px}
.lp{height:4px;background:var(--surface-high);border-radius:2px;margin-top:6px;overflow:hidden}
.lp i{display:block;height:100%}
.mi{font-size:12px;color:var(--text2);margin-top:2px;white-space:pre-line}
"""
    body = (f'<div class="mn"><h1>{esc(s("maint_title"))}</h1><div class="bk">{esc(D.BIKE)}</div>'
            f'<div class="mc"><div class="lbl">{esc(s("maint_odometer"))}</div>'
            f'<div class="odo">{D.km(lang, D.MAINT_BOOK["odometer"], 0)}</div>'
            f'<div class="hint">{esc(s("maint_odometer_hint"))}</div>'
            f'<span class="btn" style="margin-top:8px">{esc(s("maint_fix_km"))}</span></div>'
            f'<div class="sec">{esc(s("maint_tasks"))}</div>{cards}</div>')
    return _scrolled(body, 0, css)


# ---------------------------------------------------------------- 23 · my year on the bike (YearInReviewActivity), first card

def year(lang):
    s = Strings(lang)
    km = D.CAREER["km"]
    comp = (s("yir_km_times", D.num(lang, km / 620.0)) if km >= 620
            else s("yir_km_percent", int(km / 620 * 100)))
    n_cards = 8      # km, days, streak, longest, top speed, best month, time of day, zone (no fuel card: see demo)
    bars = "".join(f'<i style="background:{"var(--gold)" if i == 0 else "#3A463E"}"></i>' for i in range(n_cards))
    css = """
.yr{position:absolute;inset:30px 0 0;background:#18211B;padding:8px 16px 16px;display:flex;flex-direction:column}
.bars{display:flex;gap:3px;height:4px}.bars i{flex:1;display:block}
.bar{display:flex;align-items:center;gap:8px;margin-top:10px}
.bar .sp{flex:1}
.bar .btn{min-height:40px;padding:0 18px}
.st{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center}
.st .ti{font-size:15px;color:#AEBAAA}
.st .em{font-size:64px;margin-top:24px;line-height:1.2}
.st .bg{font-family:'ArchivoBlack';font-size:54px;color:var(--gold);margin-top:6px;line-height:1.15}
.st .lb{font-size:15px;color:#F1F4EC;letter-spacing:1.5px;margin-top:6px}
.st .sb{font-size:15px;color:#AEBAAA;margin-top:6px}
"""
    body = (f'<div class="yr"><div class="bars">{bars}</div><div class="bar">'
            f'<span class="btn">{esc(s("yir_year"))}</span><span class="btn" style="opacity:.5">{esc(s("yir_month"))}</span>'
            f'<span class="sp"></span><span class="btn">{esc(s("share_button"))}</span></div>'
            f'<div class="st"><div class="ti">{esc(s("yir_story_title", D.NOW.year))}</div><div class="em">🏍️</div>'
            f'<div class="bg">{D.num(lang, km, 0)}</div><div class="lb">{esc(s("yir_km").upper())}</div>'
            f'<div class="sb">{esc(comp)}</div></div></div>')
    return phone_page(body, extra_css=COMMON + css, bg="#18211B")
