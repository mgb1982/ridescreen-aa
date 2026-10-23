"""Wear OS screens (225×225 CSS px, round). Texts from wear/…/strings.xml, data from lib/demo.py."""
import math
import os

from lib import demo as D
from lib.base import ASSETS, dots, watch_page
from lib.maps import svg as map_svg
from lib.res import WEAR_RES, Strings, esc

PHOTO = "file://" + os.path.join(ASSETS, "hero.png")
GREY, GOLD, GREEN = "#9E9E9E", "#D1A955", "#5BD47A"
COND = "display:inline-block;transform:scaleX(.86);transform-origin:center"   # ~ sans-serif-condensed

CSS = f"""
.photo{{position:absolute;inset:0;background:#000 url({PHOTO}) 58% 40%/auto 125% no-repeat;opacity:.62}}
.lay{{position:relative;display:flex;flex-direction:column;align-items:center;z-index:2}}
.st{{font-size:12px}}
.big{{font-size:60px;font-weight:500;line-height:1;margin-top:-2px}}
.unit{{font-size:11px;color:{GREY};margin-top:1px}}
.prow{{display:flex;gap:6px;margin-top:6px}}
.pill{{color:#F1F4EC}}
"""

# Pager: 0 ride, 1 pad, 2 info, 3 trips.
PAGES = 4


def _ring(speed=None, top=0, summary=False):
    """SpeedRingView: 270° arc from bottom-left, 140 km/h full scale, ticks at 50/90/120, dot at top speed."""
    size, stroke = 225, 5
    r = size / 2 - 4 - stroke / 2
    cx = cy = size / 2

    def pt(deg, rad=r):
        a = math.radians(deg)
        return cx + rad * math.cos(a), cy + rad * math.sin(a)

    def arc(frac, col):
        if frac <= 0:
            return ""
        a0, a1 = 135, 135 + 270 * min(frac, 1)
        (x0, y0), (x1, y1) = pt(a0), pt(a1)
        large = 1 if a1 - a0 > 180 else 0
        return (f'<path d="M{x0:.2f},{y0:.2f} A{r:.2f},{r:.2f} 0 {large} 1 {x1:.2f},{y1:.2f}" fill="none" '
                f'stroke="{col}" stroke-width="{stroke}" stroke-linecap="round"/>')
    parts = [arc(1, "rgba(255,255,255,.15)")]
    if summary:
        parts.append(arc(1, "rgba(209,169,85,.5)"))
    elif speed is not None:
        frac = lambda k: min(k / 140, 1)
        parts.append(arc(frac(speed), "#E5484D" if speed >= 120 else GOLD))
        for t in (50, 90, 120):
            a = 135 + 270 * frac(t)
            (x1, y1), (x2, y2) = pt(a, r - stroke * .9), pt(a, r + stroke * .9)
            parts.append(f'<line x1="{x1:.2f}" y1="{y1:.2f}" x2="{x2:.2f}" y2="{y2:.2f}" stroke="rgba(255,255,255,.5)" stroke-width="1.5"/>')
        if top:
            x, y = pt(135 + 270 * frac(top))
            parts.append(f'<circle cx="{x:.2f}" cy="{y:.2f}" r="{stroke * .6}" fill="#fff"/>')
    return (f'<svg width="{size}" height="{size}" style="position:absolute;inset:0;z-index:1">'
            + "".join(parts) + "</svg>")


def _ride_page(s, lang, status, status_col, speed, unit, pills, ring, photo=False, page=0):
    p1 = "".join(f'<span class="pill">{esc(x)}</span>' for x in pills[:2])
    p2 = "".join(f'<span class="pill">{esc(x)}</span>' for x in pills[2:])
    body = ('<div class="photo"></div>' if photo else "") + ring + (
        f'<div class="lay"><div class="st" style="color:{status_col}">{esc(status)}</div>'
        f'<div class="big"><span style="{COND}">{esc(speed)}</span></div><div class="unit">{esc(unit)}</div>'
        f'<div class="prow">{p1}</div>' + (f'<div class="prow">{p2}</div>' if p2 else "") + "</div>"
        + dots(PAGES, page))
    return watch_page(body, extra_css=CSS)


def ride(lang):
    """Riding, connected to the dash (08_viaje_en_curso)."""
    s, L = Strings(lang, WEAR_RES), D.LIVE
    return _ride_page(s, lang, s("status_streaming"), GREEN, str(L["speed"]), s("unit_kmh"),
                      [D.km(lang, L["km"]), D.dur(L["secs"]), s("pill_max", L["max"]), s("pill_avg", L["avg"])],
                      _ring(L["speed"], L["max"]))


def turn(lang):
    """Android Auto guiding: the status line becomes the next manoeuvre."""
    s, L = Strings(lang, WEAR_RES), D.LIVE
    return _ride_page(s, lang, f'↱ {L["next_turn_m"]} m', GOLD, str(L["speed"]), s("unit_kmh"),
                      [D.km(lang, L["km"]), D.dur(L["secs"]), s("pill_max", L["max"]), s("pill_avg", L["avg"])],
                      _ring(L["speed"], L["max"]))


def last_ride(lang):
    """Idle after a ride: last-ride card over the bike photo (2.3)."""
    s, t = Strings(lang, WEAR_RES), D.TRIP
    ago = D.ago_minutes(lang, D.PARKED["minutes"])
    return _ride_page(s, lang, s("summary_title"), GOLD, D.num(lang, t["km"]), s("summary_unit", ago),
                      [D.dur(t["secs"]), s("pill_avg", t["avg"]), s("pill_max", t["max"])],
                      _ring(summary=True), photo=True)


def find_bike(lang):
    s = Strings(lang, WEAR_RES)
    sub = s("find_parked_ago", D.ago_minutes(lang, D.PARKED["minutes"])) + " · ±5 m"
    arrow = ('<svg width="92" height="92" viewBox="0 0 92 92"><path d="M46 8 L72 80 L46 64 L20 80 Z" '
             'fill="#D1A955" transform="rotate(28 46 46)"/><path d="M46 8 L46 64 L20 80 Z" fill="#B48C3E" '
             'transform="rotate(28 46 46)"/></svg>')
    body = ('<div class="photo"></div><div class="lay">'
            f'<div style="color:{GOLD};font-size:12px;font-weight:500">{esc(s("find_title"))}</div>{arrow}'
            f'<div style="font-size:30px;font-weight:500;color:#F1F4EC;line-height:1.1"><span style="{COND}">{D.PARKED["dist"]}</span></div>'
            f'<div style="font-size:11px;color:#C9C9C9;margin-top:2px">{esc(sub)}</div>'
            f'<span class="pill" style="margin-top:7px;font-size:12px">{esc(s("find_maps"))}</span></div>')
    return watch_page(body, extra_css=CSS)


def tile(lang):
    s, L = Strings(lang, WEAR_RES), D.LIVE
    body = (f'<div style="color:{GOLD};font-size:13px">{esc(s("tile_riding"))}</div>'
            f'<div style="font-size:30px;font-weight:700;color:#F1F4EC">{D.km(lang, L["km"])}</div>'
            f'<div style="color:{GREY};font-size:13px">{L["mins"]} min · {esc(s("pill_avg", L["avg"]))}</div>')
    return watch_page(body, extra_css=CSS)


def trophy(lang):
    app = Strings(lang)
    now = {"es": "ahora", "ca": "ara", "en": "now"}[lang]
    title = app("tr_unlocked_title").replace("🏆 ", "")
    text = f'{app("tr_distance")}: {app("tr_distance_d", D.num(lang, 1000, 0))}'
    body = (f'<div style="color:#BDBDBD;font-size:11px">RideScreen AA · {now}</div>'
            f'<div style="font-size:30px;margin:4px 0 2px">🏆</div>'
            f'<div style="color:{GOLD};font-size:15px;font-weight:700;padding:0 16px">{esc(title)}</div>'
            f'<div style="font-size:12.5px;line-height:1.3;padding:2px 30px 0">{esc(text)}</div>')
    return watch_page(body, extra_css=CSS)


def pad(lang):
    def b(label, x, y, d=50, hi=False, fs=18):
        ring = f"box-shadow:0 0 0 2.5px {GOLD};" if hi else ""
        return (f'<div style="position:absolute;left:{x - d / 2}px;top:{y - d / 2}px;width:{d}px;height:{d}px;'
                f'border-radius:50%;background:#2B3A2F;{ring}display:flex;align-items:center;justify-content:center;'
                f'font-size:{fs}px;color:#fff">{label}</div>')
    c = 112.5
    body = (b("OK", c, c, 56, fs=15) + b("▲", c, c - 57, hi=True) + b("▼", c, c + 57, hi=True) +
            b("◀", c - 57, c) + b("▶", c + 57, c) + b("↩", c - 52, c - 52, 40, fs=16) + b("⌂", c + 52, c - 52, 40) +
            b("🎤", c - 52, c + 52, 40, fs=15) + b("📊", c + 52, c + 52, 40, fs=15) + dots(PAGES, 1))
    return watch_page(body, extra_css=CSS)


def crash(lang):
    s = Strings(lang, WEAR_RES)
    body = (f'<div style="font-size:15px">{esc(s("crash_title"))}</div>'
            f'<div style="font-size:44px;font-weight:700;line-height:1.1"><span style="{COND}">47</span></div>'
            f'<div style="background:#fff;color:#B3261E;border-radius:12px;padding:10px 18px;font-weight:700;'
            f'font-size:16px;margin-top:4px">{esc(s("crash_im_ok"))}</div>')
    return watch_page(body, bg="#B3261E", extra_css=CSS)


def info(lang):
    s, L = Strings(lang, WEAR_RES), D.LIVE

    def cell(v, label):
        return (f'<div style="width:80px;margin:2px 0;white-space:nowrap"><div style="color:{GOLD};font-size:16px;font-weight:500">'
                f'<span style="{COND}">{v}</span></div><div style="color:{GREY};font-size:9px">{esc(label)}</div></div>')
    grid = (cell(D.dur(L["secs"]), s("label_total")) + cell(L["alt"], s("label_alt")) +
            cell(L["heading"], s("label_heading")) + cell(L["gps"], s("label_gps")) +
            cell(f'{L["phone_batt"]}% · {L["temp"]}°', s("label_battery")) + cell("12/15 +3", s("label_volume")))
    body = (f'<div style="font-size:40px;font-weight:500;line-height:1"><span style="{COND}">18:42</span></div>'
            f'<div style="display:flex;flex-wrap:wrap;width:164px;justify-content:center;margin-top:6px">{grid}</div>'
            + dots(PAGES, 2))
    return watch_page(body, extra_css=CSS)


def trips(lang):
    s = Strings(lang, WEAR_RES)
    cards = ""
    for t in D.WATCH_TRIPS:
        date = f'{D.short_date(lang, t["date"])} · {t["start"]}–{t["end"]}'
        detail = s("trip_line", D.dur(t["secs"]), t["avg"], t["max"])
        cards += (f'<div style="background:#1E2A21;border-radius:16px;padding:6px 10px 7px;margin:5px 0;width:170px">'
                  f'<div style="color:{GREY};font-size:11px">{esc(date)}</div>'
                  f'<div style="color:{GOLD};font-size:20px;font-weight:500;line-height:1.15">'
                  f'<span style="{COND}">{D.km(lang, t["km"], 2)}</span></div>'
                  f'<div style="color:#E0E0E0;font-size:11px">{esc(detail)}</div></div>')
    body = (f'<div style="position:absolute;top:14px;left:0;right:0;color:{GREY};font-size:12px">{esc(s("trips_title"))}</div>'
            f'<div style="position:absolute;top:34px;left:0;right:0;display:flex;flex-direction:column;align-items:center">'
            f'{cards}</div>' + dots(PAGES, 3))
    return watch_page(body, extra_css=CSS)


def trip_map(lang):
    s, t = Strings(lang, WEAR_RES), D.TRIP
    line1 = f'{t["start"]}–{t["end"]} · {D.km(lang, t["km"], 2)}'
    line2 = s("trip_line", D.dur(t["secs"]), t["avg"], t["max"])
    body = (f'<div style="position:absolute;inset:0">{map_svg(225, 225, "light", "main", line_w=3.2, watch=True)}</div>'
            f'<div style="position:absolute;left:0;right:0;bottom:22px;display:flex;justify-content:center">'
            f'<div style="background:rgba(0,0,0,.86);border-radius:14px;padding:5px 12px;font-size:11px;line-height:1.35">'
            f'{esc(line1)}<br>{esc(line2)}</div></div>')
    return watch_page(body, bg="#EEF0F2", extra_css=CSS)
