"""Canonical demo data. EVERY capture takes its names, numbers and places from here.

Rules (see README): Barcelona as the example city, but nothing personal: invented rider, invented dash
code, public coordinates, believable numbers for a 125 cc scooter in town.
"""
import datetime as dt

BIKE = "Zontes 125X"
DASH_SSID = "ZT000000"            # invented; real dash codes look like ZT + 6 hex
RIDER = {"es": "Álex", "ca": "Àlex", "en": "Alex", "it": "Alex", "el": "Άλεξ", "ru": "Алекс"}
CONTACTS = [("Laura", "+34 6•• ••• 412"), ("Jordi", "+34 6•• ••• 087")]
# Plaça de Catalunya: public, central, nobody's home.
CRASH_POS = (41.387015, 2.170047)
CRASH_POS_2 = (41.387021, 2.170052)

# The headline trip, used on phone, watch, share card and web.
TRIP = dict(
    name={l: "Sants → Zona Franca" for l in ("es", "ca", "en", "it", "el", "ru")},
    date=dt.date(2026, 10, 6), start="08:12", end="08:31",
    km=6.8, secs=18 * 60 + 40, avg=22, max=71, clips=3,
)
TRIP_2 = dict(
    name={"es": "Por Sants", "ca": "Per Sants", "en": "Around Sants", "it": "Giro per Sants", "el": "Βόλτα στα Sants",
          "ru": "По Сантсу"},
    date=dt.date(2026, 10, 6), start="13:31", end="13:42",
    km=2.4, secs=5 * 60 + 29, avg=25, max=53, clips=0,
)
TRIP_WEEKEND = dict(km=48.6)       # longest ride (record): a Sunday out to Garraf
RECORDS = dict(longest=48.6, top=98, best_day=63.2, streak=5)
TROPHIES = (11, 31)
STREAK_NOW = 5
TODAY = dict(km=9.2, max=71, trips=2)
PARKED = dict(dist="350 m", minutes=12)
MAINT = dict(task_key="maint_oil", left_km=420)   # task name is the app's own default (strings: maint_oil)

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

# Java/Android number formatting per locale (CLDR). Russian groups thousands with a no-break space.
DEC = {"es": ",", "ca": ",", "en": ".", "it": ",", "el": ",", "ru": ","}
THOUS = {"es": ".", "ca": ".", "en": ",", "it": ".", "el": ".", "ru": "\u00a0"}


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
    "it": ["lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato", "domenica"],
    "el": ["Δευτέρα", "Τρίτη", "Τετάρτη", "Πέμπτη", "Παρασκευή", "Σάββατο", "Κυριακή"],
    "ru": ["понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье"],
}
WD_SHORT = {"es": ["lun", "mar", "mié", "jue", "vie", "sáb", "dom"],
            "ca": ["dl.", "dt.", "dc.", "dj.", "dv.", "ds.", "dg."],
            "en": ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"],
            "it": ["lun", "mar", "mer", "gio", "ven", "sab", "dom"],
            "el": ["Δευ", "Τρί", "Τετ", "Πέμ", "Παρ", "Σάβ", "Κυρ"],
            "ru": ["пн", "вт", "ср", "чт", "пт", "сб", "вс"]}
MONTHS = {
    "es": ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"],
    "ca": ["gener", "febrer", "març", "abril", "maig", "juny", "juliol", "agost", "setembre", "octubre", "novembre", "desembre"],
    "en": ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"],
    "it": ["gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre"],
    # Greek and Russian: genitive, as Java prints "d MMMM" (CLDR format context).
    "el": ["Ιανουαρίου", "Φεβρουαρίου", "Μαρτίου", "Απριλίου", "Μαΐου", "Ιουνίου", "Ιουλίου", "Αυγούστου", "Σεπτεμβρίου",
           "Οκτωβρίου", "Νοεμβρίου", "Δεκεμβρίου"],
    "ru": ["января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"],
}
MON_SHORT = {"es": ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic"],
             "ca": ["gen.", "febr.", "març", "abr.", "maig", "juny", "jul.", "ag.", "set.", "oct.", "nov.", "des."],
             "en": ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"],
             "it": ["gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic"],
             "el": ["Ιαν", "Φεβ", "Μαρ", "Απρ", "Μαΐ", "Ιουν", "Ιουλ", "Αυγ", "Σεπ", "Οκτ", "Νοε", "Δεκ"],
             "ru": ["янв.", "февр.", "мар.", "апр.", "мая", "июн.", "июл.", "авг.", "сент.", "окт.", "нояб.", "дек."]}


def long_date(lang, d):
    wd, mo = WEEKDAYS[lang][d.weekday()], MONTHS[lang][d.month - 1]
    if lang == "es":
        return f"{wd}, {d.day} de {mo}"
    if lang == "ca":
        return f"{wd}, {d.day} " + (f"d'{mo}" if mo[0] in "aeiouà" else f"de {mo}")
    if lang in ("it", "el", "ru"):
        return f"{wd} {d.day} {mo}"
    return f"{wd}, {mo} {d.day}"


def short_date(lang, d):
    wd, mo = WD_SHORT[lang][d.weekday()], MON_SHORT[lang][d.month - 1]
    if lang == "en":
        return f"{wd} {d.day} {mo}"
    if lang == "ca":
        return f"{wd} {d.day} " + (f"d'{mo}" if mo[0] in "aeiou" else f"de {mo}")
    return f"{wd} {d.day} {mo}"


def ago_minutes(lang, n):
    ru = "минут" if n % 10 in (0, 5, 6, 7, 8, 9) or 11 <= n % 100 <= 14 else ("минуту" if n % 10 == 1 else "минуты")
    return {"es": f"hace {n} minutos", "ca": f"fa {n} minuts", "en": f"{n} minutes ago", "it": f"{n} minuti fa",
            "el": f"πριν από {n} λεπτά", "ru": f"{n} {ru} назад"}[lang]


def medium_date(lang, d):
    """java.text.DateFormat MEDIUM in that locale (CLDR, same data as Android)."""
    from babel.dates import format_date
    return format_date(d, format="medium", locale={"en": "en_US"}.get(lang, lang))


# ---------------------------------------------------------------- everything recorded with the app (screens 16–23)
# One history, so trophies, year, heat map and records agree. The app has been in use since mid-August 2026;
# "today" is 6 October 2026, the day of the headline trip.
NOW = dt.datetime(2026, 10, 6, 18, 42)
CAREER = dict(first=dt.date(2026, 8, 17), trips=63, km=1248.0, hours=47.2, days=38, longest=48.6, top=98,
              best_streak=5, months=3, early=True, night=False, weekend=False)
LONGEST_NAME = {l: "Sants → Garraf" for l in ("es", "ca", "en", "it", "el", "ru")}
BEST_MONTH = dict(month=9, km=612)          # September
TIME_OF_DAY = 0                              # morning commuter
# Trophies.Family order, tiers and when each one was earned (None = locked). 11 of 31, see TROPHIES.
TROPHY_FAMILIES = [
    # (family, emoji, title key, desc key, tiers, unit, current value)
    ("DISTANCE", "🛣️", "tr_distance", "tr_distance_d", [100, 500, 1000, 2500, 5000, 10000], "km", CAREER["km"]),
    ("LONGEST", "🧭", "tr_longest", "tr_longest_d", [25, 50, 100, 200], "km", CAREER["longest"]),
    ("SPEED", "⚡", "tr_speed", "tr_speed_d", [70, 90, 110], "km/h", CAREER["top"]),
    ("STREAK", "🔥", "tr_streak", "tr_streak_d", [3, 7, 14, 30], "", CAREER["best_streak"]),
    ("RIDES", "🏍️", "tr_rides", "tr_rides_d", [10, 50, 100, 250, 500], "", CAREER["trips"]),
    ("HOURS", "⏱️", "tr_hours", "tr_hours_d", [10, 50, 100, 250], "h", CAREER["hours"]),
    ("MONTHS", "📅", "tr_months", "tr_months_d", [6, 12], "", CAREER["months"]),
    ("EARLY", "🌅", "tr_early", "tr_early_d", [1], "", 1),
    ("NIGHT", "🌙", "tr_night", "tr_night_d", [1], "", 0),
    ("WEEKEND", "🎉", "tr_weekend", "tr_weekend_d", [1], "", 0),
]
TROPHY_EARNED = {
    ("SPEED", 70): dt.datetime(2026, 8, 17, 8, 30), ("DISTANCE", 100): dt.datetime(2026, 8, 21, 19, 5),
    ("RIDES", 10): dt.datetime(2026, 8, 24, 8, 31), ("STREAK", 3): dt.datetime(2026, 8, 26, 8, 29),
    ("HOURS", 10): dt.datetime(2026, 9, 1, 19, 12), ("EARLY", 1): dt.datetime(2026, 9, 9, 7, 14),
    ("DISTANCE", 500): dt.datetime(2026, 9, 12, 13, 40), ("LONGEST", 25): dt.datetime(2026, 9, 27, 12, 55),
    ("SPEED", 90): dt.datetime(2026, 9, 27, 12, 55), ("RIDES", 50): dt.datetime(2026, 9, 29, 19, 2),
    ("DISTANCE", 1000): dt.datetime(2026, 10, 5, 8, 33),
}

# Maintenance book (default tasks, Maintenance.defaultTasks): odometer and what was logged.
MAINT_BOOK = dict(
    odometer=4512.0,
    tasks=[  # (string key, every km, every months, last km, last date)
        ("maint_oil", 3000, 12, 1932.0, dt.date(2026, 5, 12)),
        ("maint_air", 6000, 12, 1000.0, dt.date(2026, 4, 20)),
        ("maint_belt", 12000, None, None, None),
        ("maint_brakes", 6000, None, None, None),
        ("maint_tyres", 5000, None, None, None),
        ("maint_service", 6000, 12, 1000.0, dt.date(2026, 4, 20)),
        ("maint_itv", None, 24, None, None),
        ("maint_insurance", None, 12, None, dt.date(2026, 3, 14)),
    ],
    fills=[(3.1, 5.52), (6.4, 10.11), (6.2, 9.86), (6.6, 10.62)],   # litres, euros; first fill doesn't count
    fill_km=(4096.0, 4512.0),
)

# Your red lights (TrafficLights.Result). Big public avenues on the example route, nothing personal.
TL = dict(
    trips=63, junctions=41,
    worst=[  # name, stops, passes, avg wait s, main dir (0 N,1 E,2 S,3 W), dir stops, dir passes, cycle s, fit %
        ("Gran Via de les Corts Catalanes", 17, 21, 46, 1, 15, 17, 90, 78),
        ("Passeig de la Zona Franca", 12, 19, 38, 2, 10, 14, None, None),
        ("Carrer de Tarragona", 8, 20, 29, 2, 6, 13, None, None),
        ("Carrer de la Foneria", 5, 18, 22, 3, 4, 11, None, None),
    ],
    wave=[  # junction, dir, correct, predicted, unsure, soon ±s, soon n, cycle
        ("Gran Via de les Corts Catalanes", 1, 3, 4, 1, 6, 5, 90),
    ],
    routes=[  # name, variants (count, median min, red lights, km); 🏆 = fastest median
        ("Sants → Zona Franca", [(9, 17, 4, 6.8), (4, 21, 9, 6.1)]),
        ("Zona Franca → Sants", [(8, 18, 5, 6.9), (2, 19, 7, 7.4)]),
        ("Sants → Eixample", [(6, 16, 8, 4.2), (3, 14, 5, 4.9)]),
    ],
)

# Clips found for the headline trip (TripVideoActivity). Names follow the phone camera's pattern.
CLIPS = [
    ("08:13:05", 75, "VID_20261006_081305.mp4"),
    ("08:19:42", 45, "VID_20261006_081942.mp4"),
    ("08:24:10", 37, "VID_20261006_082410.mp4"),
]

# Petrol stations around the example spot (FuelActivity). Brands and avenues are public; prices and
# distances are made up. Hours in the Ministry's own format.
FUEL = dict(radius_km=5, source_time="18:30", stations=[
    # brand, address, town, hours, price €/L, distance m
    ("PETROPRIX", "AVINGUDA DEL PARAL·LEL", "BARCELONA", "L-D: 24H", 1.429, 2140),
    ("BALLENOIL", "CARRER DE LA FONERIA", "BARCELONA", "L-D: 24H", 1.439, 4380),
    ("REPSOL", "GRAN VIA DE LES CORTS CATALANES", "BARCELONA", "L-D: 06:00-23:00", 1.519, 1260),
    ("GALP", "CARRER D'ARAGÓ", "BARCELONA", "L-D: 24H", 1.529, 980),
    ("CEPSA", "RONDA DE SANT PAU", "BARCELONA", "L-S: 07:00-22:00", 1.549, 1650),
    ("BP", "CARRER DE BALMES", "BARCELONA", "L-D: 24H", 1.569, 740),
    ("SHELL", "AVINGUDA DIAGONAL", "BARCELONA", "L-D: 24H", 1.579, 2870),
    ("PETRONOR", "CARRER DE MUNTANER", "BARCELONA", "L-V: 07:00-21:00", 1.589, 1930),
    ("REPSOL", "PASSEIG DE SANT JOAN", "BARCELONA", "L-D: 24H", 1.599, 1720),
])
