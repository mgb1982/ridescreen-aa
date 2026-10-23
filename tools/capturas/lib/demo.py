"""Canonical demo data. EVERY capture takes its names, numbers and places from here.

Rules (see README): Barcelona as the example city, but nothing personal: invented rider, invented dash
code, public coordinates, believable numbers for a 125 cc scooter in town.
"""
import datetime as dt

BIKE = "Zontes 125X"
DASH_SSID = "ZT000000"            # invented; real dash codes look like ZT + 6 hex
RIDER = {"es": "Álex", "ca": "Àlex", "en": "Alex"}
CONTACTS = [("Laura", "+34 6•• ••• 412"), ("Jordi", "+34 6•• ••• 087")]
# Plaça de Catalunya: public, central, nobody's home.
CRASH_POS = (41.387015, 2.170047)
CRASH_POS_2 = (41.387021, 2.170052)

# The headline trip, used on phone, watch, share card and web.
TRIP = dict(
    name={"es": "Sants → Zona Franca", "ca": "Sants → Zona Franca", "en": "Sants → Zona Franca"},
    date=dt.date(2026, 10, 6), start="08:12", end="08:31",
    km=6.8, secs=18 * 60 + 40, avg=22, max=71, clips=3,
)
TRIP_2 = dict(
    name={"es": "Por Sants", "ca": "Per Sants", "en": "Around Sants"},
    date=dt.date(2026, 10, 6), start="13:31", end="13:42",
    km=2.4, secs=5 * 60 + 29, avg=25, max=53, clips=0,
)
TRIP_WEEKEND = dict(km=48.6)       # longest ride (record): a Sunday out to Garraf
RECORDS = dict(longest=48.6, top=98, best_day=63.2, streak=5)
TROPHIES = (11, 31)
STREAK_NOW = 5
TODAY = dict(km=9.2, max=71, trips=2)
PARKED = dict(dist="350 m", minutes=12)
MAINT = dict(task={"es": "Aceite y filtro", "ca": "Oli i filtre", "en": "Oil & filter"}, left_km=420)

# Ride in progress (watch, live screens): consistent across turn alert, tile and ride page.
LIVE = dict(speed=47, km=4.2, mins=12, secs=12 * 60 + 40, avg=23, max=64, clock="18:32",
            next_turn_m=120, alt=48, gps=4, heading="NE", phone_batt=64, temp=38)

# Older rides in the watch list.
WATCH_TRIPS = [
    dict(date=dt.date(2026, 10, 6), start="08:12", end="08:31", km=6.8, secs=TRIP["secs"], avg=22, max=71),
    dict(date=dt.date(2026, 10, 5), start="22:38", end="22:51", km=1.72, secs=210, avg=29, max=57),
    dict(date=dt.date(2026, 10, 5), start="13:31", end="13:42", km=2.38, secs=329, avg=25, max=53),
]

VERSION = "2.3"
REPO = "mgb1982/ridescreen-aa"

# ---------------------------------------------------------------- locale formatting

DEC = {"es": ",", "ca": ",", "en": "."}
THOUS = {"es": ".", "ca": ".", "en": ","}


def num(lang, x, decimals=1):
    s = f"{x:,.{decimals}f}"
    return s.replace(",", "§").replace(".", DEC[lang]).replace("§", THOUS[lang])


def km(lang, x, decimals=1):
    return f"{num(lang, x, decimals)} km"


def dur(secs, hours=False):
    h, r = divmod(int(secs), 3600)
    m, s = divmod(r, 60)
    return f"{h:02d}:{m:02d}:{s:02d}" if hours or h else f"{m}:{s:02d}"


WEEKDAYS = {
    "es": ["lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"],
    "ca": ["dilluns", "dimarts", "dimecres", "dijous", "divendres", "dissabte", "diumenge"],
    "en": ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"],
}
WD_SHORT = {"es": ["lun", "mar", "mié", "jue", "vie", "sáb", "dom"],
            "ca": ["dl.", "dt.", "dc.", "dj.", "dv.", "ds.", "dg."],
            "en": ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]}
MONTHS = {
    "es": ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"],
    "ca": ["gener", "febrer", "març", "abril", "maig", "juny", "juliol", "agost", "setembre", "octubre", "novembre", "desembre"],
    "en": ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"],
}
MON_SHORT = {"es": ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic"],
             "ca": ["gen.", "febr.", "març", "abr.", "maig", "juny", "jul.", "ag.", "set.", "oct.", "nov.", "des."],
             "en": ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]}


def long_date(lang, d):
    wd, mo = WEEKDAYS[lang][d.weekday()], MONTHS[lang][d.month - 1]
    if lang == "es":
        return f"{wd}, {d.day} de {mo}"
    if lang == "ca":
        return f"{wd}, {d.day} " + (f"d'{mo}" if mo[0] in "aeiouà" else f"de {mo}")
    return f"{wd}, {mo} {d.day}"


def short_date(lang, d):
    wd, mo = WD_SHORT[lang][d.weekday()], MON_SHORT[lang][d.month - 1]
    if lang == "en":
        return f"{wd} {d.day} {mo}"
    if lang == "ca":
        return f"{wd} {d.day} " + (f"d'{mo}" if mo[0] in "aeiou" else f"de {mo}")
    return f"{wd} {d.day} {mo}"


def ago_minutes(lang, n):
    return {"es": f"hace {n} minutos", "ca": f"fa {n} minuts", "en": f"{n} minutes ago"}[lang]
