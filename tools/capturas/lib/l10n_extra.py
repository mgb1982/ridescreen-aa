"""Guide and screen texts for the four languages added to the website on 6 Nov 2026 (de, fr, pt-PT, th).

Android's own texts come from AOSP (lib/android_aosp.json). Play Protect, Android Auto, the GitHub release
line and Android's Wi‑Fi request are not open source: translated from English, **unverified on a phone**.
German uses «du», French «tu», Portuguese is European (as in the app).
"""

AVG_MAX = {"de": ("Ø", "max."), "fr": ("moy.", "max."), "pt": ("média", "máx.")}   # from share_avg/share_max
OK = {"de": "OK", "fr": "OK", "pt": "OK", "th": "ตกลง"}

SMS_EXAMPLE = {"de": "(Beispiel: was deine Kontakte bekommen)", "fr": "(Exemple : ce que reçoivent tes contacts)",
               "pt": "(Exemplo: o que os teus contactos recebem)", "th": "(ตัวอย่าง: ข้อความที่ผู้ติดต่อของคุณจะได้รับ)"}

G1 = {
    "de": ("RideScreen AA is wireless Android Auto on your bike's dash…",
           "✅ Das ist die fürs <b>Handy</b>.<br>❌ Die mit <b>WearOS-reloj</b> ist für die Uhr: aufs Handy lässt sie sich nicht installieren (ungültiges Paket)."),
    "fr": ("RideScreen AA is wireless Android Auto on your bike's dash…",
           "✅ C’est celle du <b>téléphone</b>.<br>❌ Celle avec <b>WearOS-reloj</b> est pour la montre : elle ne s’installe pas sur un téléphone (paquet non valide)."),
    "pt": ("RideScreen AA is wireless Android Auto on your bike's dash…",
           "✅ Esta é a do <b>telemóvel</b>.<br>❌ A que diz <b>WearOS-reloj</b> é para o relógio: no telemóvel não se instala (pacote inválido)."),
    "th": ("RideScreen AA is wireless Android Auto on your bike's dash…",
           "✅ ไฟล์นี้สำหรับ<b>มือถือ</b><br>❌ ไฟล์ที่มี <b>WearOS-reloj</b> สำหรับนาฬิกา ติดตั้งบนมือถือไม่ได้ (แพ็กเกจไม่ถูกต้อง)"),
}

G8 = {
    "de": ("RideScreen AA möchte eine Verbindung herstellen mit", "Abbrechen", "Verbinden",
           "Tippe auf <b>Verbinden</b>: Das ist das WLAN deines Motorrads. Falls du es verpasst hast, tippe in der App noch einmal auf die Verbindungstaste."
           "<br><br>Lass dieses Fenster nicht offen: Nach anderthalb Minuten läuft es ab. Das <b>WLAN des Handys muss eingeschaltet sein</b>."),
    "fr": ("RideScreen AA souhaite se connecter à", "Annuler", "Connecter",
           "Appuie sur <b>Connecter</b> : c’est le Wi‑Fi de ta moto. Si tu l’as raté, appuie à nouveau sur le bouton de connexion dans l’app."
           "<br><br>Ne laisse pas cette fenêtre ouverte : elle expire au bout d’une minute et demie. Le <b>Wi‑Fi du téléphone doit être activé</b>."),
    "pt": ("RideScreen AA pretende ligar-se a", "Cancelar", "Ligar",
           "Toca em <b>Ligar</b>: é o Wi‑Fi da tua mota. Se te escapou, volta a tocar no botão de ligação na app."
           "<br><br>Não deixes esta janela aberta: expira ao fim de minuto e meio. O <b>Wi‑Fi do telemóvel tem de estar ligado</b>."),
    "th": ("RideScreen AA ต้องการเชื่อมต่อกับ", "ยกเลิก", "เชื่อมต่อ",
           "แตะ <b>เชื่อมต่อ</b> นี่คือ Wi‑Fi ของรถคุณ ถ้าพลาดไป ให้แตะปุ่มเชื่อมต่อในแอปอีกครั้ง"
           "<br><br>อย่าเปิดหน้าต่างนี้ทิ้งไว้ หน้าต่างจะหมดเวลาภายในหนึ่งนาทีครึ่ง <b>ต้องเปิด Wi‑Fi ของมือถือไว้</b>"),
}

PLAY_PROTECT = {
    "de": ("Unsichere App blockiert", "Play Protect erkennt den Entwickler dieser App nicht. Apps von unbekannten "
           "Entwicklern können manchmal unsicher sein.", "Weitere Details", "Trotzdem installieren", "OK"),
    "fr": ("Application dangereuse bloquée", "Play Protect ne reconnaît pas le développeur de cette application. Les "
           "applications de développeurs inconnus peuvent parfois être dangereuses.", "Plus d'infos",
           "Installer quand même", "OK"),
    "pt": ("App não segura bloqueada", "O Play Protect não reconhece o programador desta app. As apps de programadores "
           "desconhecidos podem, por vezes, não ser seguras.", "Mais detalhes", "Instalar mesmo assim", "OK"),
    "th": ("บล็อกแอปที่ไม่ปลอดภัยแล้ว", "Play Protect ไม่รู้จักนักพัฒนาแอปนี้ แอปจากนักพัฒนาที่ไม่รู้จักอาจไม่ปลอดภัย",
           "รายละเอียดเพิ่มเติม", "ติดตั้งต่อไป", "ตกลง"),
}

ANDROID_AUTO = {
    "de": ("Datenschutzeinstellungen", "Hilfe & Feedback", "Über", "Version",
           "Entwicklereinstellungen aktivieren? → <b>OK</b>", "Entwicklereinstellungen",
           "Server für Infotainmentsystem starten", "Entwicklermodus beenden"),
    "fr": ("Paramètres de confidentialité", "Aide et commentaires", "À propos", "Version",
           "Activer les paramètres pour les développeurs ? → <b>OK</b>", "Paramètres pour les développeurs",
           "Démarrer le serveur de l'unité principale", "Quitter le mode développeur"),
    "pt": ("Definições de privacidade", "Ajuda e comentários", "Acerca de", "Versão",
           "Ativar as definições de programador? → <b>OK</b>", "Definições de programador",
           "Iniciar servidor da unidade principal", "Sair do modo de programador"),
    "th": ("การตั้งค่าความเป็นส่วนตัว", "ความช่วยเหลือและความคิดเห็น", "เกี่ยวกับ", "เวอร์ชัน",
           "เปิดใช้การตั้งค่าสำหรับนักพัฒนาแอปไหม → <b>ตกลง</b>", "การตั้งค่าสำหรับนักพัฒนาแอป",
           "เริ่มเซิร์ฟเวอร์ชุดเครื่องเสียง", "ออกจากโหมดนักพัฒนาแอป"),
}

NOTES = {
    "g3": {
        "de": "Erscheint, weil die App noch nicht im Play Store ist. Tippe auf <b>Weitere Details</b> und dann auf "
              "<b>Trotzdem installieren</b>.<br><br>Tippe nicht auf \"OK\": Das bricht die Installation ab.",
        "fr": "Ça s’affiche parce que l’app n’est pas encore sur le Play Store. Appuie sur <b>Plus d'infos</b> puis "
              "sur <b>Installer quand même</b>.<br><br>N’appuie pas sur \"OK\" : ça annule l’installation.",
        "pt": "Aparece porque a app ainda não está na Play Store. Toca em <b>Mais detalhes</b> e depois em "
              "<b>Instalar mesmo assim</b>.<br><br>Não toques em \"OK\": cancela a instalação.",
        "th": "ข้อความนี้ขึ้นเพราะแอปยังไม่อยู่ใน Play Store แตะ <b>รายละเอียดเพิ่มเติม</b> แล้วแตะ <b>ติดตั้งต่อไป</b>"
              "<br><br>อย่าแตะ \"ตกลง\" เพราะจะยกเลิกการติดตั้ง",
    },
    "g4": {
        "de": "Erlaube alle Berechtigungen, die sie anfragt. Den <b>Standort</b> verlangt Android, um sich mit dem "
              "WLAN des Motorrads zu verbinden: Die App verfolgt dich nicht.",
        "fr": "Accepte toutes les autorisations demandées. La <b>localisation</b> est exigée par Android pour se "
              "connecter au Wi‑Fi de la moto : l’app ne te suit pas.",
        "pt": "Aceita todas as autorizações que pedir. A <b>localização</b> é exigida pelo Android para ligar ao "
              "Wi‑Fi da mota: a app não te segue.",
        "th": "อนุญาตทุกสิทธิ์ที่แอปขอ Android ต้องใช้<b>ตำแหน่ง</b>เพื่อเชื่อมต่อ Wi‑Fi ของรถ แอปไม่ได้ติดตามคุณ",
    },
    "g5": {
        "de": "Öffne <b>Android Auto</b> (Handy-Einstellungen → Android Auto oder in RideScreen: <i>{open_aa}</i>), "
              "scrolle ganz nach unten und tippe 10-mal hintereinander auf <b>Version</b>.",
        "fr": "Ouvre <b>Android Auto</b> (Paramètres du téléphone → Android Auto, ou depuis RideScreen : "
              "<i>{open_aa}</i>), descends tout en bas et appuie 10 fois de suite sur <b>Version</b>.",
        "pt": "Abre o <b>Android Auto</b> (Definições do telemóvel → Android Auto, ou a partir do RideScreen: "
              "<i>{open_aa}</i>), desce até ao fim e toca 10 vezes seguidas em <b>Versão</b>.",
        "th": "เปิด <b>Android Auto</b> (การตั้งค่ามือถือ → Android Auto หรือจาก RideScreen: <i>{open_aa}</i>) "
              "เลื่อนลงไปล่างสุด แล้วแตะ <b>เวอร์ชัน</b> 10 ครั้งติดกัน",
    },
    "g6": {
        "de": "Tippe auf ⋮ (oben rechts) → <b>Server für Infotainmentsystem starten</b> (Start head unit server)."
              "<br><br>⚠️ Er geht jedes Mal aus, wenn du <b>das Handy neu startest</b> oder Android Auto sich "
              "aktualisiert: Wiederhole dann nur diesen Schritt.",
        "fr": "Appuie sur ⋮ (en haut à droite) → <b>Démarrer le serveur de l'unité principale</b> (Start head unit "
              "server).<br><br>⚠️ Il s’arrête chaque fois que tu <b>redémarres le téléphone</b> ou qu’Android Auto se "
              "met à jour : refais seulement cette étape.",
        "pt": "Toca em ⋮ (em cima à direita) → <b>Iniciar servidor da unidade principal</b> (Start head unit server)."
              "<br><br>⚠️ Desliga-se sempre que <b>reinicias o telemóvel</b> ou o Android Auto é atualizado: repete "
              "só este passo.",
        "th": "แตะ ⋮ (มุมขวาบน) → <b>เริ่มเซิร์ฟเวอร์ชุดเครื่องเสียง</b> (Start head unit server)<br><br>⚠️ "
              "จะปิดเองทุกครั้งที่<b>รีสตาร์ทมือถือ</b>หรือ Android Auto อัปเดต ให้ทำแค่ขั้นตอนนี้ซ้ำ",
    },
    "g7": {
        "de": "1. Motorrad an und abgestellt.<br>2. Wechsle im Display zur <b>Ansicht 3</b> (Handy-Projektion), die "
              "den <b>QR</b> zeigt.<br>3. Tippe in RideScreen auf <b>{scan}</b> (unten, unter <b>{more}</b>) und "
              "richte die Kamera darauf.<br><br>⚠️ Der QR unter <b>Information</b> ist nicht der richtige.",
        "fr": "1. Moto allumée et à l’arrêt.<br>2. Sur le tableau de bord, passe à l’<b>interface 3</b> (projection "
              "du téléphone), celle qui affiche le <b>QR</b>.<br>3. Dans RideScreen, appuie sur <b>{scan}</b> (en bas, "
              "dans <b>{more}</b>) et vise.<br><br>⚠️ Le QR d’<b>Information</b> n’est pas le bon.",
        "pt": "1. Mota ligada e parada.<br>2. No painel, muda para a <b>interface 3</b> (projeção do telemóvel), a "
              "que mostra o <b>QR</b>.<br>3. No RideScreen toca em <b>{scan}</b> (em baixo, em <b>{more}</b>) e "
              "aponta.<br><br>⚠️ O QR de <b>Informação</b> não é o certo.",
        "th": "1. เปิดรถและจอดนิ่ง<br>2. ที่หน้าปัด เปลี่ยนไป<b>หน้าจอ 3</b> (การฉายภาพจากมือถือ) ที่แสดง <b>QR</b><br>"
              "3. ใน RideScreen แตะ <b>{scan}</b> (ด้านล่าง ใน <b>{more}</b>) แล้วเล็งกล้อง<br><br>⚠️ QR ในหน้า "
              "<b>ข้อมูล</b> ไม่ใช่อันที่ถูกต้อง",
    },
}

POINT_AT_QR = {"de": "Richte die Kamera auf den QR im Display", "fr": "Vise le QR du tableau de bord avec la caméra",
               "pt": "Aponta a câmara ao QR do painel", "th": "เล็งกล้องไปที่ QR บนหน้าปัด"}
