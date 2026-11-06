"""Texts that Android (or another system app) shows, not RideScreen: they follow the phone's language, not the app's. Keep them to the platform's own wording."""

SYS = {
    # android.R.string.cancel (AlertDialog negative button)
    "cancel": {"es": "Cancelar", "ca": "Cancel·la", "en": "Cancel", "it": "Annulla", "el": "Ακύρωση", "ru": "Отмена"},
    # Notification shade: quick settings header and relative times
    "settings": {"es": "Configuración", "ca": "Configuració", "en": "Settings", "it": "Impostazioni",
                 "el": "Ρυθμίσεις", "ru": "Настройки"},
    "now": {"es": "ahora", "ca": "ara", "en": "now", "it": "ora", "el": "τώρα", "ru": "сейчас"},
    "1min": {"es": "hace 1 min", "ca": "fa 1 min", "en": "1 min ago", "it": "1 min fa", "el": "πριν από 1 λ.",
             "ru": "1 мин. назад"},
    "2h": {"es": "hace 2 h", "ca": "fa 2 h", "en": "2 h ago", "it": "2 h fa", "el": "πριν από 2 ώ.", "ru": "2 ч назад"},
    "yesterday": {"es": "ayer", "ca": "ahir", "en": "yesterday", "it": "ieri", "el": "χθες", "ru": "вчера"},
    # Messages app (SMS thread date separator)
    "today": {"es": "Hoy", "ca": "Avui", "en": "Today", "it": "Oggi", "el": "Σήμερα", "ru": "Сегодня"},
    # Android's Wi‑Fi network request dialog (guide step 8)
    "wifi_request": {"es": "quiere conectarse a", "ca": "es vol connectar a", "en": "wants to connect to",
                     "it": "vuole connettersi a", "el": "θέλει να συνδεθεί στο", "ru": "хочет подключиться к сети"},
    "connect": {"es": "Conectar", "ca": "Connecta", "en": "Connect", "it": "Connetti", "el": "Σύνδεση",
                "ru": "Подключить"},
}


# de/fr/pt/th (6 Nov 2026): «cancel» from AOSP; the rest translated from English, unverified on a phone.
EXTRA = {
 "cancel": {
  "de": "Abbrechen",
  "fr": "Annuler",
  "pt": "Cancelar",
  "th": "ยกเลิก"
 },
 "settings": {
  "de": "Einstellungen",
  "fr": "Paramètres",
  "pt": "Definições",
  "th": "การตั้งค่า"
 },
 "now": {
  "de": "jetzt",
  "fr": "maintenant",
  "pt": "agora",
  "th": "ตอนนี้"
 },
 "1min": {
  "de": "vor 1 Min.",
  "fr": "il y a 1 min",
  "pt": "há 1 min",
  "th": "1 นาทีที่แล้ว"
 },
 "2h": {
  "de": "vor 2 Std.",
  "fr": "il y a 2 h",
  "pt": "há 2 h",
  "th": "2 ชม. ที่แล้ว"
 },
 "yesterday": {
  "de": "gestern",
  "fr": "hier",
  "pt": "ontem",
  "th": "เมื่อวาน"
 },
 "today": {
  "de": "Heute",
  "fr": "Aujourd’hui",
  "pt": "Hoje",
  "th": "วันนี้"
 },
 "wifi_request": {
  "de": "möchte eine Verbindung herstellen mit",
  "fr": "souhaite se connecter à",
  "pt": "pretende ligar-se a",
  "th": "ต้องการเชื่อมต่อกับ"
 },
 "connect": {
  "de": "Verbinden",
  "fr": "Connecter",
  "pt": "Ligar",
  "th": "เชื่อมต่อ"
 }
}
for _k, _v in EXTRA.items():
    SYS[_k].update(_v)


def sys_text(key, lang):
    return SYS[key][lang]
