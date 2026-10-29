"""Phone screens (412×915 CSS px). Texts come from the app's strings.xml; data from lib/demo.py."""
import os

from lib import demo as D
from lib.base import ASSETS, PHONE_H, phone_page
from lib.maps import svg as map_svg
from lib.res import Strings, esc, icon
from lib.system import sys_text

HERO = "file://" + os.path.join(ASSETS, "hero.png")

CSS = """
.hero{position:relative;height:300px;background:var(--surface-high) url(HERO) center/cover no-repeat}
.hero .brand{position:absolute;left:22px;top:44px;font-size:19px;color:#fff}
.hero .brand b{color:var(--gold);font-weight:400}
.hero .menu{position:absolute;right:16px;top:34px;width:44px;height:44px;border-radius:50%;
  background:rgba(26,35,29,.55);display:flex;align-items:center;justify-content:center;color:#fff}
.hero .name{position:absolute;left:22px;bottom:30px;font-size:40px;color:var(--gold);line-height:1}
.hero .sub{position:absolute;left:22px;bottom:12px;font-size:12.5px;color:var(--text)}
.wrap{padding:6px 16px 0}
.card{background:var(--surface);border-radius:20px}
.status{padding:16px}
.status .row{display:flex;align-items:center;gap:12px}
.dot{width:12px;height:12px;border-radius:50%;flex:none}
.status .t{font-weight:600;font-size:17px}
.status .s{margin:2px 0 0 24px;font-size:13px;color:var(--text2)}
.status hr{border:0;height:1px;background:var(--surface-high);margin:14px 0 12px}
.stats{display:flex}
.stats>div{flex:1}
.stats .v{font-family:'ArchivoBlack';font-size:24px;line-height:1.15}
.stats .l{font-size:12.5px;color:var(--text2)}
.stats .v small{font-family:'Archivo';font-weight:600;font-size:12px;color:var(--text2);margin-left:3px}
.parked{display:flex;gap:12px;padding:12px;margin-top:10px;align-items:center}
.parked .ph{width:64px;height:72px;border-radius:16px;background:url(HERO) 30% 40%/240px auto;flex:none}
.parked .t{font-weight:600;font-size:15.5px}
.parked .s{font-size:13px;color:var(--text2);margin-top:2px}
.btn-o{display:inline-flex;align-items:center;gap:4px;border:1px solid var(--outline);border-radius:999px;
  padding:5px 12px;font-size:13px;color:var(--text);margin-top:8px;margin-right:6px;white-space:nowrap}
.tiles{display:flex;gap:10px;margin-top:10px}
.tile{flex:1;min-height:108px;background:var(--surface);border-radius:20px;padding:14px 6px 12px;
  display:flex;flex-direction:column;align-items:center;text-align:center}
.tile .b{width:46px;height:46px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:22px}
.tile .t{font-weight:500;font-size:13px;margin-top:8px;line-height:1.2}
.tile .s{font-size:11px;color:var(--text2);line-height:1.25;margin-top:1px}
.tile.big{min-height:112px;align-items:flex-start;text-align:left;padding:14px 10px 12px 16px;justify-content:center}
.tile.big .b{width:42px;height:42px;font-size:20px}
.tile.big .t{font-size:15px}
.tile.sel{outline:1.5px solid var(--gold);outline-offset:-1.5px}
.more-t{font-size:11.5px;letter-spacing:1.2px;color:var(--text2);margin:16px 4px 4px;text-transform:uppercase}
.more{display:flex}
.more div{flex:1;display:flex;flex-direction:column;align-items:center;gap:4px;font-size:12px;color:var(--text2);
  padding:6px 0}
.search{display:flex;gap:10px;align-items:center;margin-top:12px}
.search .in{flex:1;border-bottom:1px solid var(--outline);padding:10px 2px;font-size:15px;color:var(--text2)}
.search .go{border:1px solid var(--outline);border-radius:999px;padding:8px 14px;font-size:14px}
.chips{display:flex;gap:8px;margin-top:10px;white-space:nowrap;overflow:hidden}
.chips span{border:1px solid var(--outline);border-radius:999px;padding:7px 12px;font-size:13px}
.scroll{position:absolute;left:0;right:0;top:0;height:835px;overflow:hidden}
.cta{position:absolute;left:20px;right:20px;bottom:14px;height:58px;border-radius:999px;background:var(--gold);
  color:var(--on-brand);display:flex;align-items:center;justify-content:center;gap:10px;
  font-family:'Archivo';font-weight:800;font-size:16.5px;letter-spacing:1.6px;text-transform:uppercase}
.scrim{position:absolute;inset:0;background:rgba(0,0,0,.55);z-index:20}
.sheet{position:absolute;left:24px;right:24px;top:330px;background:#2A362D;border-radius:28px;padding:22px 22px 30px;z-index:30}
.sheet h3{font-weight:500;font-size:21px;margin-bottom:10px}
.sheet div{font-size:16.5px;padding:13px 2px}
""".replace("HERO", HERO)

TILE_COL = {"trips": "#5B8DEF", "fuel": "#E5774E", "maint": "#4FB6A6", "tl": "#E05A5A", "live": "#5FD07A",
            "help": "#A98BE0", "park": "#D1A955", "controls": "#5B8DEF", "hud": "#4FB6A6", "trip": "#5FD07A"}


def _bubble(col):
    r, g, b = int(col[1:3], 16), int(col[3:5], 16), int(col[5:7], 16)
    return f"background:rgba({r},{g},{b},.2)"


def _tile(emoji, key, title, sub="", big=False, sel=False):
    cls = "tile big" if big else "tile"
    if sel:
        cls += " sel"
    s = f'<div class="s">{esc(sub)}</div>' if sub else ""
    return (f'<div class="{cls}"><div class="b" style="{_bubble(TILE_COL[key])}">{emoji}</div>'
            f'<div class="t">{esc(title)}</div>{s}</div>')


def _hero(s, h=280):
    # MainActivity: hero is 280 dp, 190 dp while connected.
    return (f'<div class="hero" style="height:{h}px"><div class="brand display">RideScreen <b>AA</b></div>'
            f'<div class="menu">{icon("ic_settings", "#fff", 22)}</div>'
            f'<div class="name display">{esc(D.BIKE)}</div><div class="sub">{D.DASH_SSID}</div></div>')


def _tiles_grid(s, lang):
    trips_sub = " · ".join([s("home_streak", D.STREAK_NOW), s.plural("home_tile_trips_today", D.TODAY["trips"])])
    maint_sub = f'{s(D.MAINT["task_key"])}: ' + s("maint_status_km_left", D.km(lang, D.MAINT["left_km"], 0))
    return (f'<div class="tiles">{_tile("🏍️", "trips", s("home_tile_trips"), trips_sub)}'
            f'{_tile("⛽", "fuel", s("home_tile_fuel"), s("home_tile_fuel_sub"))}'
            f'{_tile("🔧", "maint", s("home_tile_maint"), maint_sub)}</div>'
            f'<div class="tiles">{_tile("🚦", "tl", s("home_tile_tl"))}'
            f'{_tile("📍", "live", s("home_tile_live"))}'
            f'{_tile("❓", "help", s("home_tile_help"), s("home_tile_help_sub"))}</div>')


def _more(s):
    items = [("ic_place", "gpx_map"), ("ic_cast", "main_mirror"), ("ic_qr", "main_scan"),
             ("ic_devices", "garage_garage"), ("ic_tune", "controls_controls"), ("ic_settings", "home_settings_short")]
    cells = "".join(f'<div>{icon(i, "var(--text2)", 22)}<span>{esc(s(k))}</span></div>' for i, k in items)
    return f'<div class="more-t">{esc(s("main_more"))}</div><div class="more">{cells}</div>'


def home(lang, connected=False, sheet=False, battery=None, locked=True):
    s = Strings(lang)
    if connected:
        state, dot, sub = s("conn_streaming"), "var(--live)", D.BIKE
        stats = (f'<div><div class="v">{D.LIVE["speed"]}<small>km/h</small></div><div class="l">{esc(s("stat_speed_now"))}</div></div>'
                 f'<div><div class="v">{D.num(lang, D.TODAY["km"])}</div><div class="l">{esc(s("stat_km_today"))}</div></div>'
                 f'<div><div class="v">18:42</div><div class="l">{esc(s("stat_clock_ok"))}</div></div>')
    else:
        state, dot, sub = s("conn_stopped"), "var(--idle)", s("main_paired", D.BIKE)
        stats = (f'<div><div class="v">{D.num(lang, D.TODAY["km"])}</div><div class="l">{esc(s("stat_km_today"))}</div></div>'
                 f'<div><div class="v">{D.TODAY["max"]}</div><div class="l">{esc(s("stat_max_today"))}</div></div>'
                 f'<div><div class="v">18:42</div><div class="l">{esc(s("stat_clock"))}</div></div>')
    status = (f'<div class="card status"><div class="row"><span class="dot" style="background:{dot}"></span>'
              f'<span class="t">{esc(state)}</span></div><div class="s">{esc(sub)}</div><hr>'
              f'<div class="stats">{stats}</div></div>')
    body = '<div class="scroll">' + _hero(s, 190 if connected else 280) + '<div class="wrap">' + status
    if battery:
        body += battery_card(lang, running=(battery == "running"), locked=locked)
    if connected:
        nav = s("dest_home"), s("dest_work")
        body += (f'<div class="tiles">{_tile("📸", "park", s("home_big_park"), s("home_big_park_sub"), big=True, sel=True)}'
                 f'{_tile("🎮", "controls", s("controls_controls"), s("home_big_controls_sub"), big=True)}</div>'
                 f'<div class="tiles">{_tile("🖥️", "hud", s("hud_dash_view"), s("home_big_hud_sub"), big=True)}'
                 f'{_tile("⏱️", "trip", s("home_big_trip"), s("home_big_trip_sub"), big=True)}</div>'
                 f'<div class="search"><div class="in">{esc(s("main_search_a_place_or_address"))}</div>'
                 f'<div class="go">{esc(s("main_go"))}</div></div>'
                 f'<div class="chips"><span>🏠 {esc(nav[0])}</span><span>💼 {esc(nav[1])} 📨</span>'
                 f'<span>🧭 Google Maps</span><span>📅 {esc(s("dest_calendar"))}</span></div>')
        body += _tiles_grid(s, lang) + "</div></div>"
        body += f'<div class="cta">■&nbsp; {esc(s("main_stop"))}</div>'
    else:
        ago = D.ago_minutes(lang, D.PARKED["minutes"])
        body += (f'<div class="card parked"><div class="ph"></div><div><div class="t">'
                 f'{esc(s("home_parked_title_dist", D.PARKED["dist"]))}</div>'
                 f'<div class="s">{esc(s("home_parked_sub", ago))}</div>'
                 f'<span class="btn-o">{esc(s("parked_walk"))}</span><span class="btn-o">{esc(s("home_view_photo"))}</span>'
                 f'</div></div>')
        body += _tiles_grid(s, lang) + _more(s) + "</div></div>"
        body += f'<div class="cta">{icon("ic_power", "var(--on-brand)", 22)}{esc(s("main_connect_to", D.BIKE))}</div>'
        if sheet:
            items = [s("parked_walk"), s("parked_view_photo"), s("parked_retake_photo"), s("parked_delete")]
            body += ('<div class="scrim"></div><div class="sheet">'
                     f'<h3>{esc(s("parked_options_title"))}</h3>' + "".join(f"<div>{esc(i)}</div>" for i in items) + "</div>")
    return phone_page(body, extra_css=CSS)


# ---------------------------------------------------------------- trips list

TRIPS_CSS = """
.top{padding:44px 20px 10px;display:flex;align-items:center;gap:10px}
.top .h{font-family:'ArchivoBlack';font-size:20px;line-height:1.1;flex:1}
.pill{display:inline-flex;align-items:center;justify-content:center;gap:5px;border-radius:999px;
  background:var(--surface);padding:10px 14px;font-size:14.5px;white-space:nowrap}
.pill.o{background:transparent;border:1.5px solid var(--gold);display:flex;margin:0 20px 8px;padding:11px}
.pill.g{background:var(--gold);color:var(--on-brand);font-weight:600}
.off{margin:0 20px 8px;border:1.5px solid var(--gold);border-radius:20px;padding:12px 14px;display:flex;
  gap:10px;align-items:center;font-size:13px;line-height:1.35}
.rec{margin:0 20px 8px;background:var(--surface);border-radius:16px;padding:10px 14px;font-size:13px;line-height:1.45}
.rec .tr{color:var(--gold);font-weight:600}
.day{display:flex;align-items:center;margin:6px 20px 10px}
.day .arr{width:40px;height:40px;border-radius:50%;border:1px solid var(--outline);display:flex;
  align-items:center;justify-content:center;font-size:15px}
.day .c{flex:1;text-align:center}
.day .c b{font-size:16.5px;display:block}
.day .c span{font-size:12.5px;color:var(--text2)}
.trip{margin:0 20px 12px;background:var(--surface);border-radius:20px;padding:12px 12px 12px}
.trip .map{border-radius:14px;overflow:hidden;height:150px}
.trip .nm{color:var(--gold);font-size:14px;margin-top:10px}
.trip .tm{font-family:'ArchivoBlack';font-size:18px}
.trip .st{display:flex;margin-top:6px}
.trip .st div{flex:1;font-size:11.5px;color:var(--text2)}
.trip .st b{display:block;color:var(--text);font-size:14.5px;font-weight:700}
.trip .bt{display:flex;gap:8px;margin-top:10px}
.trip .bt span{flex:1;text-align:center;border:1px solid var(--outline);border-radius:999px;padding:9px;font-size:14px}
.trip .vid{display:block;text-align:center;border:1.5px solid var(--gold);color:var(--gold);border-radius:999px;
  padding:9px;font-size:14px;margin-top:8px}
"""


def _trip_card(s, lang, t, route):
    def stat(label, value):
        return f"<div>{esc(label)}<b>{value}</b></div>"
    vid = (s("tv_button_clips", t["clips"]) if t["clips"] else s("tv_button"))
    return (f'<div class="trip"><div class="map">{map_svg(372, 150, "dark", route)}</div>'
            f'<div class="nm">{esc(t["name"][lang])}</div><div class="tm">{t["start"]} – {t["end"]}</div>'
            f'<div class="st">{stat(s("trip_distance"), D.km(lang, t["km"]))}{stat(s("trips_stat_time"), D.dur(t["secs"]))}'
            f'{stat(s("trips_stat_avg"), str(t["avg"]) + " km/h")}{stat(s("trips_stat_max"), str(t["max"]) + " km/h")}</div>'
            f'<div class="bt"><span>{esc(s("share_button"))}</span><span>{esc(s("trips_export_gpx"))}</span></div>'
            f'<span class="vid">{esc(vid)}</span></div>')


def trips(lang, logging_off=False):
    s = Strings(lang)
    head = (f'<div class="top">{icon("ic_speed", "var(--gold)", 26) if _has("ic_speed") else ""}'
            f'<div class="h">{esc(s("trip_saved_trips"))}</div>'
            f'<span class="pill">{esc(s("yir_button"))}</span><span class="pill">{esc(s("all_rides_button"))}</span></div>')
    body = head + f'<div class="pill o">{esc(s("tl_entry"))}</div>'
    if logging_off:
        body += (f'<div class="off"><div>{esc(s("trips_logging_off"))}</div>'
                 f'<span class="pill g">{esc(s("trips_logging_enable"))}</span></div>')
    else:
        body += f'<div class="pill o">{esc(s("week_button"))}</div>'
        r = D.RECORDS
        rec = s("records_line", D.km(lang, r["longest"]), r["top"], D.km(lang, r["best_day"]), r["streak"])
        body += (f'<div class="rec">{esc(rec)}<div class="tr">{esc(s("tr_card_line", *D.TROPHIES))}</div></div>')
    n = 1 if logging_off else 2
    total = D.TRIP["km"] + (0 if logging_off else D.TRIP_2["km"])
    summary = s.plural("trips_day_summary", n, n, D.num(lang, total))
    body += (f'<div class="day"><span class="arr">‹</span><div class="c"><b>{esc(D.long_date(lang, D.TRIP["date"]))}</b>'
             f'<span>{esc(summary)}</span></div><span class="arr">›</span></div>')
    body += _trip_card(s, lang, D.TRIP, "main")
    if not logging_off:
        body += _trip_card(s, lang, D.TRIP_2, "short")
    return phone_page(body, extra_css=TRIPS_CSS)


def _has(name):
    from lib.res import APP_RES
    return os.path.exists(os.path.join(APP_RES, "drawable", name + ".xml"))


# ---------------------------------------------------------------- trip map

# The app has no strings for these yet (TripMapActivity hard-codes "avg/max", see README). Greek and Russian
# keep the English words because there is no translation to show.
AVG_MAX = {"es": ("media", "máx"), "ca": ("mitjana", "màx"), "en": ("avg", "max"), "it": ("media", "max"),
           "el": ("avg", "max"), "ru": ("avg", "max")}


def trip_map(lang):
    s = Strings(lang)
    t = D.TRIP
    avg, mx = AVG_MAX[lang]
    stats = f'{D.km(lang, t["km"])} · {D.dur(t["secs"], hours=True)} · {avg} {t["avg"]} · {mx} {t["max"]} km/h'
    css = """
.mh{position:absolute;left:0;right:0;top:0;padding:40px 16px 12px 20px;background:var(--bg);z-index:5}
.mh h1{font-family:'ArchivoBlack';font-weight:400;font-size:22px}
.mh p{font-size:13px;color:var(--text2);line-height:1.4;margin-top:4px}
.mh .b{display:flex;gap:8px;margin-top:10px}
.mh .b span{border-radius:999px;background:var(--surface);padding:9px 16px;font-size:14px}
.mp{position:absolute;left:0;right:0;top:170px;bottom:0}
"""
    body = (f'<div class="mh"><h1>{esc(t["name"][lang])}</h1><p>{esc(stats)}<br>{esc(s("map_speed_legend"))}</p>'
            f'<div class="b"><span>{esc(s("tv_button"))}</span><span>{esc(s("share_button"))}</span></div></div>'
            f'<div class="mp">{map_svg(412, 745, "light", "main", speed_colours=True, line_w=6, pad=0.15)}</div>')
    return phone_page(body, extra_css=css)


# ---------------------------------------------------------------- crash detector settings

def crash(lang):
    s = Strings(lang)
    title = s("crash_title")
    main, _, exp = title.partition(" (")
    exp = "(" + exp if exp else ""
    css = """
.cz{padding:44px 20px 0}
.cz h1{font-family:'ArchivoBlack';font-weight:400;font-size:24px;line-height:1.15}
.cz h1 small{font-family:'Archivo';font-size:15px;color:var(--gold);margin-left:4px}
.cz p{font-size:14px;color:var(--text2);line-height:1.42;margin-top:6px}
.sw{display:flex;align-items:center;justify-content:space-between;margin-top:16px;font-size:16px}
.tog{width:52px;height:30px;border-radius:999px;background:var(--gold);position:relative}
.tog:after{content:"";position:absolute;right:4px;top:4px;width:22px;height:22px;border-radius:50%;background:var(--bg)}
.ok{color:var(--live);font-size:13.5px;margin-top:8px}
.cz h2{font-size:16.5px;font-weight:700;margin-top:20px}
.fld{border-bottom:1.5px solid var(--gold);padding:8px 2px;font-size:16px}
.ct{display:flex;align-items:center;justify-content:space-between;margin-top:8px}
.ct small{display:block;font-size:13px;color:var(--text2)}
.x{width:44px;height:44px;border-radius:50%;border:1px solid var(--outline);display:flex;align-items:center;justify-content:center}
.ob{border:1px solid var(--outline);border-radius:999px;padding:11px;text-align:center;font-size:15px;margin-top:10px}
.dis{font-size:12.5px;color:var(--text2);line-height:1.4;margin-top:14px}
"""
    contacts = "".join(f'<div class="ct"><div>{n}<small>{p}</small></div><span class="x">✕</span></div>' for n, p in D.CONTACTS)
    body = (f'<div class="cz"><h1>{esc(main)}<small>{esc(exp)}</small></h1><p>{esc(s("crash_how"))}</p>'
            f'<div class="sw"><span>{esc(s("crash_enable"))}</span><span class="tog"></span></div>'
            f'<div class="ok">{esc(s("crash_status_ready"))}</div>'
            f'<h2>{esc(s("crash_name_title"))}</h2><div class="fld">{D.RIDER[lang]}</div>'
            f'<h2>{esc(s("crash_contacts_title", 3))}</h2>{contacts}'
            f'<div class="ob">{esc(s("crash_add_contact"))}</div>'
            f'<h2>{esc(s("crash_test_title"))}</h2><div class="ob">{esc(s("crash_simulate"))}</div>'
            f'<div class="ob">{esc(s("crash_test_sms"))}</div><div class="dis">{esc(s("crash_disclaimer"))}</div></div>')
    return phone_page(body, extra_css=css)


# ---------------------------------------------------------------- SMS the contact receives

SMS_EXAMPLE = {"es": "(Ejemplo: lo que reciben tus contactos)", "ca": "(Exemple: el que reben els teus contactes)",
               "en": "(Example: what your contacts receive)", "it": "(Esempio: quello che ricevono i tuoi contatti)",
               "el": "(Παράδειγμα: αυτό που λαμβάνουν οι επαφές σου)", "ru": "(Пример: что получат твои контакты)"}


def sms(lang):
    s = Strings(lang)
    rider = D.RIDER[lang]

    def link(p):
        u = f"https://maps.google.com/?q={p[0]:.6f},{p[1]:.6f}"
        return f'<a>{u}</a>'
    b1 = esc(s("crash_sms_body", rider, 60, "§L1§", "±6 m) (18:47")).replace("§L1§", link(D.CRASH_POS))
    b2 = esc(s("crash_sms_followup", rider, 60, "§L2§", "±5 m) (18:52")).replace("§L2§", link(D.CRASH_POS_2))
    css = """
.hd{display:flex;align-items:center;gap:14px;padding:42px 20px 12px}
.av{width:40px;height:40px;border-radius:50%;background:#3F6BD8;color:#fff;display:flex;align-items:center;
  justify-content:center;font-weight:700;font-size:18px}
.hd b{font-size:19px;font-weight:500}
.ts{text-align:center;color:var(--text2);font-size:12.5px;margin:12px 0 8px}
.bub{margin:0 22px 0 18px;background:var(--surface);border-radius:20px;padding:12px 14px;font-size:15px;line-height:1.42}
.bub a{color:#8AB4F8;text-decoration:underline;word-break:break-all}
.ex{text-align:center;color:var(--text2);font-size:13px;margin-top:16px}
"""
    body = (f'<div class="hd"><span class="av">{rider[0]}</span><b>{rider}</b></div>'
            f'<div class="ts">{sys_text("today", lang)} · 18:47</div><div class="bub">{b1}</div>'
            f'<div class="ts">18:52</div><div class="bub">{b2}</div><div class="ex">{SMS_EXAMPLE[lang]}</div>')
    return phone_page(body, extra_css=css)


# ---------------------------------------------------------------- share card (RideShare.kt, 1080×1350)

SHARE_W, SHARE_H = 1080, 1350


def share_card(lang):
    s = Strings(lang)
    t = D.TRIP
    wd, mo = D.WEEKDAYS[lang][t["date"].weekday()], D.MONTHS[lang][t["date"].month - 1]
    date = f"{wd} {t['date'].day} {mo} {t['date'].year}"   # RideShare.kt: "EEEE d MMMM yyyy"
    date = date[0].upper() + date[1:]
    title = f'{t["name"][lang]} · {date}'
    stats = [(s("share_km"), D.num(lang, t["km"])), (s("share_time"), D.dur(t["secs"])),
             (s("share_avg"), str(t["avg"])), (s("share_max"), str(t["max"]))]
    cells = "".join(f'<div class="c"><div class="v">{v}</div><div class="l">{esc(l)}</div></div>' for l, v in stats)
    css = f"""
.sc{{position:relative;width:{SHARE_W}px;height:{SHARE_H}px;background:#18211B}}
.wm{{position:absolute;left:60px;top:42px;font-family:'ArchivoBlack';font-size:40px;color:#F1F4EC}}
.wm b{{color:#D1A955;font-weight:400}}
.ti{{position:absolute;left:60px;top:96px;font-size:30px;color:#AEBAAA;white-space:nowrap}}
.mp{{position:absolute;left:0;top:150px;width:{SHARE_W}px;height:760px;overflow:hidden}}
.fd{{position:absolute;left:0;top:690px;width:{SHARE_W}px;height:220px;background:linear-gradient(transparent,#18211B)}}
.bk{{position:absolute;left:60px;top:760px;display:flex;align-items:center;gap:30px}}
.ph{{width:160px;height:160px;border-radius:50%;border:6px solid #D1A955;background:url({HERO}) 28% 45%/360px auto}}
.bk span{{font-family:'ArchivoBlack';font-size:52px;color:#D1A955}}
.st{{position:absolute;left:60px;top:980px;width:960px;display:grid;grid-template-columns:1fr 1fr;row-gap:30px}}
.c .v{{font-family:'ArchivoBlack';font-size:96px;line-height:1;color:#F1F4EC}}
.c .l{{font-size:30px;letter-spacing:.12em;color:#D1A955;text-transform:uppercase;margin:12px 0 0 4px}}
.ft{{position:absolute;left:0;right:0;bottom:22px;text-align:center;font-size:24px;color:#AEBAAA}}
"""
    html = f"""<!doctype html><html><head><meta charset="utf-8"><style>{_root()}{css}</style></head><body>
<div class="sc"><div class="wm">RideScreen <b>AA</b></div><div class="ti">{esc(title)}</div>
<div class="mp">{map_svg(SHARE_W, 760, "dark", "main", line_w=7)}</div><div class="fd"></div>
<div class="bk"><div class="ph"></div><span>{esc(D.BIKE)}</span></div><div class="st">{cells}</div>
<div class="ft">{esc(s("share_footer"))}</div></div></body></html>"""
    return html


def _root():
    from lib.base import root_css
    return root_css()


# ---------------------------------------------------------------- install guide

G1 = {
    "es": ("RideScreen AA es Android Auto inalámbrico en el cuadro de tu moto…",
           "✅ Este es el del <b>móvil</b>.<br>❌ El que pone <b>WearOS-reloj</b> es para el reloj: en el móvil da \"paquete no válido\"."),
    "ca": ("RideScreen AA és Android Auto sense fils al quadre de la teva moto…",
           "✅ Aquest és el del <b>mòbil</b>.<br>❌ El que diu <b>WearOS-reloj</b> és per al rellotge: al mòbil dona \"el paquet no és vàlid\"."),
    "en": ("RideScreen AA is wireless Android Auto on your bike's dash…",
           "✅ This is the <b>phone</b>.<br>❌ The one with <b>WearOS-reloj</b> is for the watch: on a phone it says \"package appears to be invalid\"."),
    # The release text on GitHub is in English for these: only the guide's note is translated.
    "it": ("RideScreen AA is wireless Android Auto on your bike's dash…",
           "✅ Questo è quello del <b>telefono</b>.<br>❌ Quello con <b>WearOS-reloj</b> è per l'orologio: sul telefono non si installa (pacchetto non valido)."),
    "el": ("RideScreen AA is wireless Android Auto on your bike's dash…",
           "✅ Αυτό είναι για το <b>κινητό</b>.<br>❌ Αυτό με το <b>WearOS-reloj</b> είναι για το ρολόι: στο κινητό δεν εγκαθίσταται (μη έγκυρο πακέτο)."),
    "ru": ("RideScreen AA is wireless Android Auto on your bike's dash…",
           "✅ Этот — для <b>телефона</b>.<br>❌ Файл с <b>WearOS-reloj</b> — для часов: на телефон он не установится (недопустимый пакет)."),
}
APK_MB, WATCH_MB = 29.6, 11.5   # v2.3 release assets (31,024,282 and 12,100,956 bytes)


def guide_download(lang):
    desc, note = G1[lang]
    v = D.VERSION
    css = """
.gh{position:absolute;inset:0;background:#0D1117;color:#E6EDF3;font-family:-apple-system,'Inter','Archivo',sans-serif}
.url{margin:34px 0 0;background:#161B22;padding:14px 18px;font-size:14.5px;color:#9198A1;display:flex;gap:8px}
.rl{padding:22px 18px}
.rl h1{font-size:26px;font-weight:600}
.lt{display:inline-block;border:1px solid #238636;color:#3FB950;border-radius:999px;font-size:13px;padding:2px 10px;margin-top:10px}
.rl p{color:#9198A1;font-size:15px;line-height:1.45;margin-top:14px}
.as{font-size:18px;font-weight:600;margin-top:24px}
.as i{font-style:normal;background:#30363D;border-radius:999px;font-size:12px;padding:1px 7px;margin-left:4px;vertical-align:2px}
.box{border:1px solid #30363D;border-radius:8px;margin-top:12px;position:relative}
.row{display:flex;align-items:center;gap:12px;padding:12px 12px;border-top:1px solid #30363D;font-size:14.5px}
.row:first-child{border-top:0}
.row a{color:#4493F8;flex:1;font-weight:600}
.row .sz{color:#9198A1;font-size:12.5px}
.row.dim{opacity:.38}
.row.hl{background:rgba(209,169,85,.10);outline:3px solid #D1A955;outline-offset:-1px;border-radius:8px;
  box-shadow:0 0 0 7px rgba(209,169,85,.22)}
.nt{position:absolute;left:18px;right:18px;bottom:40px;background:#1E2A21;border-radius:22px;padding:16px 18px;
  font-size:15px;line-height:1.5;color:#E6EDF3;font-family:'Archivo','Noto Color Emoji',sans-serif}
"""
    rows = (f'<div class="row hl">📦<a>RideScreen-AA-v{v}.apk</a><span class="sz">{APK_MB} MB</span></div>'
            f'<div class="row dim">📦<a>RideScreen-AA-v{v}-WearOS-reloj.apk</a><span class="sz">{WATCH_MB} MB</span></div>'
            f'<div class="row dim">📦<a style="color:#9198A1;font-weight:400">Source code (zip)</a></div>'
            f'<div class="row dim">📦<a style="color:#9198A1;font-weight:400">Source code (tar.gz)</a></div>')
    body = (f'<div class="gh"><div class="url">🔒 github.com/{D.REPO}/releases</div><div class="rl">'
            f'<h1>RideScreen AA v{v}</h1><span class="lt">Latest</span><p>{esc(desc)}</p>'
            f'<div class="as">▾ Assets <i>4</i></div><div class="box">{rows}</div></div>'
            f'<div class="nt">{note}</div></div>')
    return phone_page(body, bg="#0D1117", extra_css=css)


def guide_ready(lang):
    return home(lang, connected=True)


# ---------------------------------------------------------------- parking widget on a home screen

WIDGET_W, WIDGET_H = 412, 440
# The launcher icon is adaptive (mipmap-anydpi); site/img/icon.png is the same artwork as a bitmap.
LAUNCHER = "file://" + os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", "site", "img", "icon.png"))


def widget(lang):
    s = Strings(lang)
    t = D.TRIP
    ago = D.ago_minutes(lang, D.PARKED["minutes"])
    ago = ago[0].upper() + ago[1:]
    ride = (f'{s("widget_last_ride")}: {D.km(lang, t["km"])} · ' + s("widget_last_ride_sub", t["avg"], t["max"]))
    apps = [("#4285F4", "Maps"), ("#25D366", "WhatsApp"), (None, "RideScreen"),
            ("#EA4335", {"es": "Fotos", "ca": "Fotos", "en": "Photos", "it": "Foto"}[lang])]
    icons = "".join(
        f'<div style="display:flex;flex-direction:column;align-items:center;gap:6px;font-size:13px;color:#fff">'
        + (f'<img src="{LAUNCHER}" style="width:52px;height:52px;border-radius:50%">' if c is None
           else f'<div style="width:52px;height:52px;border-radius:50%;background:{c}"></div>')
        + f"<span>{n}</span></div>" for c, n in apps)
    css = """
.hs{position:absolute;inset:0;background:linear-gradient(170deg,#2E3A46,#1C232B 70%)}
.wd{position:absolute;left:20px;right:20px;top:20px;height:172px;border-radius:24px;overflow:hidden;
  border:1.5px solid rgba(209,169,85,.85);background:url(HERO) 50% 45%/cover}
.wd:before{content:"";position:absolute;inset:0;background:linear-gradient(90deg,rgba(26,35,29,.88),rgba(26,35,29,.35))}
.wd>div{position:relative;padding:16px 16px 0}
.wt{color:var(--gold);font-size:11px;letter-spacing:1.2px;font-weight:600;text-transform:uppercase}
.wm{font-family:'Archivo';font-weight:800;font-size:26px;margin-top:2px}
.ws{font-size:12px;color:rgba(241,244,236,.9);margin-top:2px}
.wr{font-size:12px;color:rgba(241,244,236,.9);margin-top:10px}
.ap{position:absolute;left:20px;right:20px;top:220px;display:flex;justify-content:space-around}
""".replace("HERO", HERO)
    body = (f'<div class="hs"></div><div class="wd"><div><div class="wt">{esc(s("widget_parked"))}</div>'
            f'<div class="wm">{D.PARKED["dist"]}</div><div class="ws">{esc(ago)}</div>'
            f'<div class="wr">{esc(ride)}</div></div></div><div class="ap">{icons}</div>')
    from lib.base import root_css
    return (f'<!doctype html><html><head><meta charset="utf-8"><style>{root_css()}{css}'
            f'.scr{{position:relative;width:{WIDGET_W}px;height:{WIDGET_H}px;overflow:hidden}}</style></head>'
            f'<body><div class="scr">{body}</div></body></html>')


# ---------------------------------------------------------------- dialogs over the main screen

DIALOG_CSS = """
.dim{position:absolute;inset:0;background:rgba(0,0,0,.6);z-index:20}
.dlg{position:absolute;left:24px;right:24px;background:#2A362D;border-radius:28px;padding:22px 24px 14px;z-index:30}
.dlg h3{font-family:'Archivo';font-weight:500;font-size:21px;line-height:1.25;margin-bottom:12px}
.dlg .m{font-size:14.5px;line-height:1.45;color:#DCE3D8;white-space:pre-line}
.dlg .bt{display:flex;justify-content:flex-end;gap:22px;margin-top:16px;padding:8px 4px;
  font-weight:700;font-size:14px;letter-spacing:.6px;color:var(--gold);text-transform:uppercase}
"""


def _over_home(lang, inner, height=PHONE_H):
    home_html = home(lang)
    # Reuse the stopped main screen and lay a dimmed dialog over it.
    i = home_html.index('<div class="scr">') + len('<div class="scr">')
    j = home_html.rindex("</div></body>")
    css = home_html.index("</style>")
    out = home_html[:css] + DIALOG_CSS + home_html[css:i] + home_html[i:j] + inner + home_html[j:]
    if height != PHONE_H:
        out = out.replace(f"height:{PHONE_H}px;background:var(--bg)", f"height:{height}px;background:var(--bg)", 1)
    return out


def dialog(lang, title, message, buttons, top=230):
    btns = "".join(f"<span>{esc(b)}</span>" for b in buttons)
    return _over_home(lang, f'<div class="dim"></div><div class="dlg" style="top:{top}px"><h3>{esc(title)}</h3>'
                            f'<div class="m">{esc(message)}</div><div class="bt">{btns}</div></div>')


def wifi_off(lang):
    s = Strings(lang)
    return dialog(lang, s("wifi_off_title"), s("wifi_off_message"), [sys_text("cancel", lang), s("wifi_off_turn_on")], top=260)


def mic(lang):
    s = Strings(lang)
    return dialog(lang, s("mic_perm_title"), s("mic_perm_message"), [s("mic_perm_not_now"), s("mic_perm_allow")], top=210)


WHATS_NEW_H = 1250


def whats_new(lang):
    s = Strings(lang)
    paras = [p.strip() for p in s("whats_new_body").split("\n\n") if p.strip()]
    items = "".join(f'<div style="margin-bottom:9px">{esc(p)}</div>' for p in paras)
    inner = (f'<div class="dim"></div><div class="dlg" style="top:70px"><h3>{esc(s("whats_new_title"))}</h3>'
             f'<div class="m" style="font-size:13.5px;line-height:1.38;white-space:normal">{items}</div>'
             f'<div class="bt"><span>{esc(s("whats_new_ok"))}</span></div></div>')
    return _over_home(lang, inner, height=WHATS_NEW_H)


# ---------------------------------------------------------------- guide step 8: Android's Wi‑Fi request

G8 = {
    "es": ("RideScreen AA quiere conectarse a", "Cancelar", "Conectar",
           "Pulsa <b>Conectar</b>: es el Wi‑Fi de tu moto. Si se te pasó, vuelve a tocar <b>Conectar</b> en la app."
           "<br><br>No dejes esta ventana abierta: al minuto y medio caduca. El <b>Wi‑Fi del móvil tiene que estar activado</b>."),
    "ca": ("RideScreen AA es vol connectar a", "Cancel·la", "Connecta",
           "Prem <b>Connecta</b>: és el Wi‑Fi de la teva moto. Si se t'ha passat, torna a tocar <b>Connecta</b> a l'app."
           "<br><br>No deixis aquesta finestra oberta: caduca al minut i mig. El <b>Wi‑Fi del mòbil ha d'estar activat</b>."),
    "en": ("RideScreen AA wants to connect to", "Cancel", "Connect",
           "Tap <b>Connect</b>: it's your bike's Wi‑Fi. If you missed it, tap <b>Connect</b> in the app again."
           "<br><br>Don't leave this window open: it expires after a minute and a half. Phone <b>Wi‑Fi has to be on</b>."),
    "it": ("RideScreen AA vuole connettersi a", "Annulla", "Connetti",
           "Tocca <b>Connetti</b>: è il Wi‑Fi della tua moto. Se ti è sfuggito, tocca di nuovo <b>Connetti</b> nell'app."
           "<br><br>Non lasciare aperta questa finestra: scade dopo un minuto e mezzo. Il <b>Wi‑Fi del telefono deve essere attivo</b>."),
    "el": ("Το RideScreen AA θέλει να συνδεθεί στο", "Ακύρωση", "Σύνδεση",
           "Πάτησε <b>Σύνδεση</b>: είναι το Wi‑Fi της μηχανής σου. Αν σου ξέφυγε, πάτησε ξανά το κουμπί σύνδεσης στην εφαρμογή."
           "<br><br>Μην αφήνεις αυτό το παράθυρο ανοιχτό: λήγει σε ενάμισι λεπτό. Το <b>Wi‑Fi του κινητού πρέπει να είναι ενεργό</b>."),
    "ru": ("RideScreen AA хочет подключиться к сети", "Отмена", "Подключить",
           "Нажми <b>Подключить</b>: это Wi‑Fi твоего мотоцикла. Если не успел, снова нажми кнопку подключения в приложении."
           "<br><br>Не оставляй это окно открытым: через полторы минуты оно закроется. <b>Wi‑Fi на телефоне должен быть включён</b>."),
}


def guide_wifi(lang):
    title, cancel, ok, note = G8[lang]
    css = """
.sys{position:absolute;inset:0;background:#0B0D0B}
.wd{position:absolute;left:20px;right:20px;top:140px;background:#2B2F2C;border-radius:28px;padding:22px 20px 14px;
  font-family:'Inter','Noto Color Emoji',sans-serif}
.wd h3{font-weight:400;font-size:22px;line-height:1.3}
.net{background:#373C38;border-radius:16px;padding:12px 14px;margin-top:14px;display:flex;gap:12px;align-items:center;font-size:16px}
.bts{display:flex;justify-content:flex-end;gap:10px;margin-top:14px;font-weight:600;font-size:15px;color:#A8D5BA;align-items:center}
.bts span{padding:9px 14px}
.bts .hl{border:3px solid var(--gold);border-radius:999px;box-shadow:0 0 0 6px rgba(209,169,85,.25)}
.nt{position:absolute;left:20px;right:20px;top:420px;background:#1E2A21;border-radius:22px;padding:16px 18px;
  font-size:15px;line-height:1.5}
"""
    body = (f'<div class="sys"></div><div class="wd"><h3>{esc(title)}</h3>'
            f'<div class="net">📶 <span>{D.DASH_SSID}</span></div>'
            f'<div class="bts"><span>{esc(cancel)}</span><span class="hl">{esc(ok)}</span></div></div>'
            f'<div class="nt">{note}</div>')
    return phone_page(body, bg="#0B0D0B", extra_css=css)


# ---------------------------------------------------------------- help menu (HelpMenu.kt + MainActivity.helpExtras)

def help_menu(lang):
    s = Strings(lang)
    keys = ["help_guide", "help_interfaces", "help_watch_guide", "help_share_logs", "help_whats_new", "help_report",
            "help_view_logs", "help_about", "help_check_update", "help_update_watch", "pax_entry", "pke_entry",
            "help_support"]
    items = "".join(f'<div style="font-size:15.5px;padding:10px 2px">{esc(s(k))}</div>' for k in keys)
    return _over_home(lang, f'<div class="dim"></div><div class="dlg" style="top:70px;padding-bottom:18px">'
                            f'<h3>{esc(s("help_title"))}</h3>{items}</div>')


# ---------------------------------------------------------------- notification shade

SHADE_CSS = """
.sh{position:absolute;inset:0;background:#141A16}
.clk{padding:46px 22px 0}
.clk b{font-family:'Inter';font-weight:400;font-size:46px;display:block;line-height:1}
.clk span{font-size:14.5px;color:#C9D1C5}
.nt{margin:10px 14px 0;background:#26302A;border-radius:22px;padding:13px 16px}
.nt .a{display:flex;align-items:center;gap:8px;font-size:12.5px;color:#C9D1C5}
.nt .a i{width:18px;height:18px;border-radius:50%;background:var(--gold);color:var(--on-brand);font-style:normal;
  font-size:10px;font-weight:800;display:flex;align-items:center;justify-content:center}
.nt b{display:block;font-size:15px;margin-top:6px}
.nt p{font-size:14px;color:#E3E8DF;line-height:1.38;margin-top:2px}
.nt .act{color:var(--gold);font-weight:600;font-size:14px;margin-top:8px}
.cfg{padding:22px 22px 0}
.cfg h4{font-size:12px;letter-spacing:1.4px;color:#C9D1C5;font-weight:500}
.cfg .r{display:flex;justify-content:space-between;align-items:center;margin-top:12px;font-size:15px}
.tog{width:52px;height:30px;border-radius:999px;background:var(--gold);position:relative;flex:none}
.tog:after{content:"";position:absolute;right:4px;top:4px;width:22px;height:22px;border-radius:50%;background:var(--bg)}
.cfg p{font-size:13px;color:var(--text2);line-height:1.42;margin-top:8px}
"""
def AGO_(lang):
    return tuple(sys_text(k, lang) for k in ("now", "1min", "2h", "yesterday"))


def _notif(lang, when, title, text, action=""):
    act = f'<div class="act">{esc(action)}</div>' if action else ""
    return (f'<div class="nt"><div class="a"><i>R</i>RideScreen AA · {when}</div><b>{esc(title)}</b>'
            f'<p>{text}</p>{act}</div>')


def waiting(lang):
    s = Strings(lang)
    now, m1, _, _ = AGO_(lang)
    d = D.TODAY_DATE if hasattr(D, "TODAY_DATE") else D.TRIP["date"]
    body = (f'<div class="clk"><b>18:42</b><span>{esc(D.long_date(lang, d))}</span></div>'
            + _notif(lang, now, s("watch_title", D.BIKE), esc(s("watch_text")), s("watch_stop"))
            + _notif(lang, m1, s("bt_trigger_title", D.BIKE), esc(s("bt_trigger_text")))
            + f'<div class="cfg"><h4>{sys_text("settings", lang).upper()}</h4><div class="r"><span>{esc(s("watch_setting"))}</span>'
              f'<span class="tog"></span></div><p>{esc(s("watch_setting_desc"))}</p></div>')
    return phone_page(body, bg="#141A16", extra_css=SHADE_CSS)


def notifications(lang):
    s = Strings(lang)
    now, _, h2, yday = AGO_(lang)
    tr = (f'🥇 {esc(s("tr_distance"))}: {esc(s("tr_distance_d", D.num(lang, 1000, 0)))}<br>'
          f'🥇 {esc(s("tr_streak"))}: {esc(s("tr_streak_d", D.STREAK_NOW))}')
    maint = esc(f'{s(D.MAINT["task_key"])}: ' + s("maint_status_km_left", D.km(lang, D.MAINT["left_km"], 0)))
    body = (f'<div class="clk"><b>18:42</b><span>{esc(D.long_date(lang, D.TRIP["date"]))}</span></div>'
            + _notif(lang, now, s("tr_unlocked_title"), tr)
            + _notif(lang, now, s("record_title"), esc(s("record_longest", D.km(lang, D.RECORDS["longest"]))))
            + _notif(lang, h2, s("bike_gone_title"), esc(s("bike_gone_text")))
            + _notif(lang, yday, s("maint_notif_title"), maint))
    return phone_page(body, bg="#141A16", extra_css=SHADE_CSS)


# ---------------------------------------------------------------- live route page (telemetry/src/live.js, Spanish only)

def live_page(lang):
    rider = D.RIDER[lang]
    css = """
.ub{position:absolute;left:12px;right:12px;top:8px;height:38px;border-radius:999px;background:#fff;color:#222;
  display:flex;align-items:center;gap:6px;padding:0 14px;font-family:'Inter';font-size:14px;z-index:5}
.lm{position:absolute;left:0;right:0;top:52px;bottom:0}
.lc{position:absolute;left:12px;right:12px;bottom:16px;background:#1E2A21;border-radius:20px;padding:14px 16px;z-index:5}
.lc .w{font-family:'ArchivoBlack';font-size:15px}.lc .w b{color:var(--gold);font-weight:400}
.lc .n{font-size:20px;font-weight:700;margin-top:4px}
.lc .r{font-size:14px;color:var(--text2);margin-top:2px}.lc .r b{color:var(--gold)}
"""
    body = (f'<div class="ub">🔒 ridescreen-telemetry…/l/aB3dE5fG7hJ9</div>'
            f'<div class="lm">{map_svg(412, 863, "light", "main", watch=True, line_w=4, pad=0.2)}</div>'
            f'<div class="lc"><div class="w">RideScreen <b>AA</b></div><div class="n">{rider} está en ruta</div>'
            f'<div class="r"><b>{D.LIVE["speed"]} km/h</b> · actualizado hace 6 s</div></div>')
    return phone_page(body, bg="#EEF0F2", extra_css=css).replace('<div class="sb"', '<div class="sb" style="display:none"', 1)


# ---------------------------------------------------------------- 2.3 battery card (activity_main.xml #pke_card)

BATTERY_CSS = """
.pke{display:flex;align-items:center;gap:12px;margin-top:10px;background:var(--surface);border-radius:20px;padding:12px 16px}
.pke .t{font-size:11.5px;letter-spacing:1.2px;color:var(--text2);font-weight:600}
.pke .v{font-family:'ArchivoBlack';font-size:30px;line-height:1.1}
.pke .v small{font-family:'Archivo';font-size:15px;color:var(--text2);margin-left:3px}
.pke .s{font-size:12px;color:var(--text2)}
.pke .l{margin-left:auto;text-align:center;font-weight:600;font-size:14px;white-space:pre-line;line-height:1.25}
"""


def battery_card(lang, running=False, locked=True):
    s = Strings(lang)
    volts = D.num(lang, 13.9 if running else 12.6)
    sub = s("engine_running" if running else "engine_stopped") + " · " + s("pke_card_read", s("pke_card_just_now"))
    lock = s("pke_card_locked" if locked else "pke_card_unlocked")
    return (f'<style>{BATTERY_CSS}</style><div class="pke"><div><div class="t">{esc(s("pke_card_title"))}</div>'
            f'<div class="v">{volts}<small>V</small></div><div class="s">{esc(sub)}</div></div>'
            f'<div class="l">{esc(lock)}</div></div>')
