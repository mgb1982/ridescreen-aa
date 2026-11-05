"""Textos del material de lanzamiento de la 2.4 (carrusel e imagen para los grupos).

[[clave]] es un texto de la app (strings.xml del idioma), como en las píldoras. Solo funciones que están
en la rama de la 2.4 (prerelease/trip-obd-telemetry, 2cbc824). Ver claude/novedades-2.4.md en el proyecto.
"""

T = {
"es": dict(
    brand_tag="Novedades",
    cover_k="Ya disponible", cover_t="RideScreen AA 2.4",
    cover_s="Abre tu moto desde el móvil, lleva tu propio cuadro con el OBD y úsala en 10 idiomas.",
    cover_chips=["🔓 Keyless", "🔌 OBD", "📟 Dashboard", "🌍 10 idiomas"],

    pke_k="Keyless", pke_t="Tu moto, desde el móvil",
    pke_s="Desbloquea, bloquea, abre el asiento o el depósito por Bluetooth, sin la app oficial. "
          "Tras cada orden, la app comprueba cómo ha quedado la moto.",
    pke_b=["**Desbloquear al conectar** (opcional): un toque y listo.",
           "**Bloquear termina el viaje**: guarda dónde has aparcado.",
           "El **McuID** se lee con la cámara, del QR del cuadro."],

    wid_k="Pantalla de inicio", wid_t="Widget [[widget_control_label]]",
    wid_s="Las acciones de tu moto sin abrir la app, con la foto de tu Garaje. Si lo haces pequeño, se queda "
          "en una fila de iconos.",

    obd_k="OBD", obd_t="Tu propio cuadro",
    obd_s="Con un adaptador OBD Bluetooth (ELM327), el cuadro enseña RPM, velocidad, refrigerante, acelerador y "
          "batería en vez de Android Auto. Tres estilos, de día y de noche.",
    obd_note="Estilos del selector de la app",
    obd_b=["**Solo lectura**: nunca escribe en la centralita.", "Sin OBD, la velocidad sale del GPS del móvil.",
           "Tus viajes guardan también RPM y acelerador."],

    dash_k="En el cuadro", dash_t="Te saluda y se despide",
    dash_s="Una tarjeta de bienvenida al empezar y el resumen del día al terminar. Y si va a llover donde has "
           "aparcado, te avisa.",
    hello="[[dc_good_afternoon]], Álex", weather="21° · [[dc_wx_cloudy]]", streak="5 [[dc_streak_days]]",

    lang_k="Idiomas", lang_t="Ahora en 10 idiomas",
    lang_s="La app y el reloj, completos. Hecha en catalán y traducida a todos los demás.",
    lang_new="Nuevo",

    end_k="Descárgala", end_t="Gratis y sin anuncios",
    end_s="Si ya la tienes, la propia app te ofrece la actualización. Si no, guía paso a paso en la web.",
    end_more="Y más: pulsaciones del reloj, aviso de lluvia al aparcar, iconos nuevos y Zontes 350D/350E en pruebas.",
    end_thanks="Gracias a Alexandru (OpenCfMoto), a Luis Gil por el OBD, a Melo, a todos los testers y a quienes "
               "apoyáis en Ko-fi.",
),
"ca": dict(
    brand_tag="Novetats",
    cover_k="Ja disponible", cover_t="RideScreen AA 2.4",
    cover_s="Obre la moto des del mòbil, porta el teu propi quadre amb l’OBD i fes-la servir en 10 idiomes.",
    cover_chips=["🔓 Keyless", "🔌 OBD", "📟 Dashboard", "🌍 10 idiomes"],

    pke_k="Keyless", pke_t="La moto, des del mòbil",
    pke_s="Desbloqueja, bloqueja, obre el seient o el dipòsit per Bluetooth, sense l’app oficial. "
          "Després de cada ordre, l’app comprova com ha quedat la moto.",
    pke_b=["**Desbloquejar en connectar** (opcional): un toc i ja està.",
           "**Bloquejar acaba el viatge**: desa on has aparcat.",
           "El **McuID** es llegeix amb la càmera, del QR del quadre."],

    wid_k="Pantalla d’inici", wid_t="Widget [[widget_control_label]]",
    wid_s="Les accions de la moto sense obrir l’app, amb la foto del teu Garatge. Si el fas petit, es queda "
          "en una fila d’icones.",

    obd_k="OBD", obd_t="El teu propi quadre",
    obd_s="Amb un adaptador OBD Bluetooth (ELM327), el quadre mostra RPM, velocitat, refrigerant, accelerador i "
          "bateria en lloc d’Android Auto. Tres estils, de dia i de nit.",
    obd_note="Estils del selector de l’app",
    obd_b=["**Només lectura**: mai escriu a la centraleta.", "Sense OBD, la velocitat surt del GPS del mòbil.",
           "Els viatges també desen RPM i accelerador."],

    dash_k="Al quadre", dash_t="Et saluda i s’acomiada",
    dash_s="Una targeta de benvinguda en començar i el resum del dia en acabar. I si plourà on has aparcat, "
           "t’avisa.",
    hello="[[dc_good_afternoon]], Àlex", weather="21° · [[dc_wx_cloudy]]", streak="5 [[dc_streak_days]]",

    lang_k="Idiomes", lang_t="Ara en 10 idiomes",
    lang_s="L’app i el rellotge, complets. Feta en català i traduïda a tots els altres.",
    lang_new="Nou",

    end_k="Descarrega-la", end_t="Gratis i sense anuncis",
    end_s="Si ja la tens, la mateixa app t’ofereix l’actualització. Si no, guia pas a pas al web.",
    end_more="I més: pulsacions del rellotge, avís de pluja en aparcar, icones noves i Zontes 350D/350E en proves.",
    end_thanks="Gràcies a Alexandru (OpenCfMoto), a Luis Gil per l’OBD, a Melo, a tots els testers i als qui "
               "doneu suport a Ko-fi.",
),
"en": dict(
    brand_tag="What’s new",
    cover_k="Out now", cover_t="RideScreen AA 2.4",
    cover_s="Unlock your bike from your phone, bring your own dash with OBD, and use it in 10 languages.",
    cover_chips=["🔓 Keyless", "🔌 OBD", "📟 Dashboard", "🌍 10 languages"],

    pke_k="Keyless", pke_t="Your bike, from your phone",
    pke_s="Unlock, lock, open the seat or the fuel cap over Bluetooth, without the official app. "
          "After each command, the app checks how the bike was left.",
    pke_b=["**Unlock on Connect** (optional): one tap and you’re off.",
           "**Locking ends the ride**: it saves where you parked.",
           "The **McuID** is read with the camera, from the dash QR."],

    wid_k="Home screen", wid_t="[[widget_control_label]] widget",
    wid_s="Your bike’s actions without opening the app, with your Garage photo. Make it small and it becomes "
          "a row of icons.",

    obd_k="OBD", obd_t="Your own dash",
    obd_s="With a Bluetooth OBD adapter (ELM327), the dash shows RPM, speed, coolant, throttle and battery "
          "instead of Android Auto. Three styles, day and night.",
    obd_note="Styles from the app’s picker",
    obd_b=["**Read-only**: it never writes to the ECU.", "No OBD? Speed comes from the phone’s GPS.",
           "Your rides also keep RPM and throttle."],

    dash_k="On the dash", dash_t="It says hello and goodbye",
    dash_s="A welcome card when you start and the day’s summary when you finish. And if rain is coming where "
           "you parked, it lets you know.",
    hello="[[dc_good_afternoon]], Alex", weather="21° · [[dc_wx_cloudy]]", streak="5 [[dc_streak_days]]",

    lang_k="Languages", lang_t="Now in 10 languages",
    lang_s="The app and the watch, fully translated. Made in Catalan and translated into all the others.",
    lang_new="New",

    end_k="Get it", end_t="Free, no ads",
    end_s="Already have it? The app offers you the update. New here? Step-by-step guide on the website.",
    end_more="Plus: heart rate from the watch, rain alert when you park, new icons, and Zontes 350D/350E in testing.",
    end_thanks="Thanks to Alexandru (OpenCfMoto), to Luis Gil for the OBD work, to Melo, to every tester and to "
               "everyone supporting on Ko-fi.",
),
}

# Language names as each language writes itself. «new» marks the full translations that arrive in 2.4.
LANGS = [("Català", False), ("Español", False), ("English", False), ("Italiano", False),
         ("Français", True), ("Deutsch", True), ("Português", True), ("Ελληνικά", True), ("Русский", True),
         ("ไทย", True)]
