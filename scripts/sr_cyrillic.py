#!/usr/bin/env python3
"""Generate Serbian Cyrillic resources (values-sr/) from the Latin ones (values-b+sr+Latn/)."""
import re, sys, os, glob
# usage: translit.py <res_dir>   (expects values/ and values-b+sr+Latn/ inside, writes values-sr/)
res = sys.argv[1]
src_dir = os.path.join(res, 'values'); lat_dir = os.path.join(res, 'values-b+sr+Latn'); cyr_dir = os.path.join(res, 'values-sr')
os.makedirs(cyr_dir, exist_ok=True)

ELEM = re.compile(r'(<(string|plurals|string-array)\s+name="([^"]+)"[^>]*>)(.*?)(</\2>)', re.S)
ITEM = re.compile(r'(<item[^>]*>)(.*?)(</item>)', re.S)
PROTECT = re.compile(r'(%\d+\$[-+ 0#,]*\.?\d*[sdf]|%[sdf]|%%|\\u[0-9a-fA-F]{4}|\\[nt"\'@?\\]|&[a-zA-Z#0-9]+;|https?://\S+|[\w.-]+\.(?:com|rocks|md|org|app)\S*)')
WORD = re.compile(r'[A-Za-zČĆŽŠĐčćžšđ]+')
KEEP = set("""Android Auto Wi Fi Bluetooth GPS GPX POI QR RideScreen AA CFMoto CFMOTO MotoPlay EasyConn EasyConnect Carbit Zontes Google Waze WhatsApp SMS ID OK PKE APK USB HD TFT DPI JSON VPN SSID AP P2P BLE HID AVRCP Ko fi Discord GitHub Meta AI Ray Ban Shizuku Tasker MacroDroid Key Mapper Xiaomi Redmi POCO Galaxy Wearable Wear OS OpenStreetMap OSRM MapLibre OpenRouteService API Open Meteo Alexandru Manel CarPlay iOS Play Direct SoftAP D pad Select Enter TRK CVT VHS fps lock wake stories reels Expert mode Intent Broadcast receiver Autostart Movies Smart LICENSE NOTICE GNU AGPL Affero General Public License T BOX TV issue Luis Gil Francisco Javier José Antonio Raul zanderp mgb1982 Ok EC BTP ZT QUERY_TIME TIME_SYNC Latest OEM""".split())
FORCE = set('Video video Stop Retro Mapa Alarm alarm audio filter font gamepad hotspot Hotspot internet interval link log panel Parking parking plus problem raster restart server start test TEST Keyless keyless Madrid bit me to a item'.split())
UNITS = {'km':'км','h':'ч','min':'мин','s':'с','m':'м','ms':'мс','MB':'МБ','L':'Л','px':'пкс','mm':'мм'}
DI = [('LJ','Љ'),('Lj','Љ'),('lj','љ'),('NJ','Њ'),('Nj','Њ'),('nj','њ'),('DŽ','Џ'),('Dž','Џ'),('dž','џ')]
MAP = dict(zip('ABVGDĐEŽZIJKLMNOPRSTĆUFHCČŠabvgdđežzijklmnoprstćufhcčš','АБВГДЂЕЖЗИЈКЛМНОПРСТЋУФХЦЧШабвгдђежзијклмнопрстћуфхцчш'))
suspicious = set()
def tr_word(w, eng_words):
    if w in UNITS: return UNITS[w]
    if w in KEEP and w not in FORCE: return w
    if w in eng_words and w not in FORCE: return w
    if w not in FORCE and re.search(r'[qwxyQWXY]', w): suspicious.add(w); return w
    out = w
    for a,b in DI: out = out.replace(a,b)
    return ''.join(MAP.get(c,c) for c in out)
def tr_text(t, eng):
    eng_words = set(WORD.findall(eng))
    parts = PROTECT.split(t)
    res = []
    for i,p in enumerate(parts):
        if i % 2 == 1: res.append(p); continue
        # words glued to digits (e.g. 800NK) stay latin
        res.append(re.sub(r'[0-9]*[A-Za-zČĆŽŠĐčćžšđ]+[0-9A-Za-z]*', lambda m: m.group(0) if re.search(r'\d', m.group(0)) else tr_word(m.group(0), eng_words), p))
    return ''.join(res)

for lat_file in sorted(glob.glob(os.path.join(lat_dir, '*.xml'))):
    fn = os.path.basename(lat_file)
    eng_txt = open(os.path.join(src_dir, fn), encoding='utf-8').read() if os.path.exists(os.path.join(src_dir, fn)) else ''
    eng = {m.group(3): m.group(4) for m in ELEM.finditer(eng_txt)}
    data = open(lat_file, encoding='utf-8').read()
    def repl(m):
        name, body = m.group(3), m.group(4)
        e = eng.get(name, '')
        if m.group(2) == 'string':
            body = tr_text(body, e)
        else:
            body = ITEM.sub(lambda im: im.group(1) + tr_text(im.group(2), e) + im.group(3), body)
        return m.group(1) + body + m.group(5)
    out = ELEM.sub(repl, data)
    out = out.replace('Srpski (latinica)', 'Српски (ћирилица) — generated from values-b+sr+Latn by transliteration')
    open(os.path.join(cyr_dir, fn), 'w', encoding='utf-8').write(out)
if suspicious: print('kept in Latin script (contains q/w/x/y):', sorted(suspicious))
