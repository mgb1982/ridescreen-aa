"""Texts that Android (or another system app) shows, not RideScreen: they follow the phone's language even when
the app itself has no translation and falls back to English. Keep them to the platform's own wording."""

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


def sys_text(key, lang):
    return SYS[key][lang]
