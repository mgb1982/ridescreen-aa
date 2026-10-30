"""Píldoras para los testers de la 2.4 (novedades frente a la 2.3 y puesta en marcha del keyless).
Misma sintaxis que pills/es.py. Textos de la app desde strings*.xml (build 378, 591a843)."""

PILLS = [
    dict(slug="01-novedades", icon="✨", tag="2.4 de pruebas",
         title="Qué trae la 2.4",
         sub="Gracias por probarla antes que nadie. Esto es lo nuevo respecto a la 2.3:",
         steps=[
             "**Mandos del keyless desde el móvil:** desbloquear, bloquear, abrir el asiento y abrir el depósito, "
             "por Bluetooth, sin la app oficial.",
             "**Widget [[widget_control_label]]** en la pantalla de inicio del móvil, con esas mismas acciones a un toque.",
             "**Desbloquear al conectar** (opcional): al pulsar [[main_connect]], la moto también se desbloquea.",
             "**Lector del McuID:** la clave que necesitan los mandos se lee con la cámara, sin teclear nada.",
             "**Bienvenida y despedida en el cuadro**, y aviso si va a llover donde has aparcado.",
         ],
         note="Para usar los mandos hay que hacer dos cosas una sola vez: poner el **código PKE** y leer el "
              "**McuID**. Las dos píldoras siguientes lo explican paso a paso.",
         foot="Probado en una Zontes 125X"),

    dict(slug="02-codigo-pke", icon="🔢", tag="Paso 1 de 2 · una sola vez",
         title="Pon el código PKE",
         sub="Es un número de tu caja del keyless. Si en la 2.3 ya veías la batería, ya lo tienes puesto: pasa al paso 2.",
         steps=[
             "Busca el código en la app oficial **Zontes Smart** → gestión de motos: sale debajo de tu moto. "
             "Son solo números.",
             "En RideScreen AA pulsa [[help_pill]] → [[pke_entry]].",
             "Escribe el código y pulsa [[dest_save]].",
             "Si Android te pide **Dispositivos cercanos**, pulsa **Permitir**: sin eso la app no llega a la moto.",
             "Con la moto cerca, la app lee la batería. Si ves los voltios y si la moto está bloqueada o desbloqueada, el código está bien.",
         ],
         note="Antes de leer, **cierra la app oficial Zontes Smart**: la caja del keyless solo habla con una app a la vez.",
         foot="Leer la batería nunca abre ni cierra nada"),

    dict(slug="03-mcuid", icon="🔑", tag="Paso 2 de 2 · una sola vez",
         title="Lee el McuID con la cámara",
         sub="El McuID es la clave de tu moto. Está dentro de un QR que enseña el propio cuadro. La app lo lee sola: "
             "no tienes que escribir nada.",
         steps=[
             "Pon el contacto. En el cuadro, desde la interfaz 1, 2 o 3: pulsa **SET** → pulsa **MOD** hasta que "
             "salga **Información** → pulsa **SET** → pulsa **MOD** hasta que salga el **QR**.",
             "En el móvil pulsa [[help_pill]] → [[pke_unlock_entry]].",
             "Sale la ventana [[pke_unlock_mcu_title]]. No escribas nada: pulsa [[pke_unlock_scan]].",
             "Si te pide la cámara, permítelo. Apunta al QR del cuadro, recto y a un palmo, hasta que lo lea "
             "(sin reflejos: tápalo con la mano si da el sol).",
             "La app enseña la clave recortada (4 caracteres … 4 caracteres) y pregunta si la guarda: pulsa [[dest_save]]. "
             "Verás «**[[pke_unlock_scan_saved]]**».",
             "En el cuadro, **mantén pulsado SET** para volver a la interfaz de antes.",
             "Vuelve a entrar en [[pke_unlock_entry]]: ya te salen los mandos.",
         ],
         note="Si sale «No se encontró la clave…», has leído otro QR. El de la **interfaz 3** (el de conectar) "
              "no sirve aquí: tiene que ser el de **Información** (paso 1).",
         foot="No mandes a nadie la foto de ese QR ni la clave, tampoco al grupo: es la llave de tu moto"),

    dict(slug="04-mandos", icon="🔓", tag="Usarlo",
         title="Desbloquear, bloquear, asiento y depósito",
         sub="Con la moto a pocos metros y el Bluetooth del móvil encendido.",
         steps=[
             "[[help_pill]] → [[pke_unlock_entry]] y elige [[pke_unlock_action_unlock]], [[pke_unlock_action_lock]], "
             "[[pke_unlock_action_cushion]] o [[pke_unlock_action_oilbox]].",
             "La app te pide confirmar: pulsa otra vez el nombre de la acción.",
             "Mira la moto. La app comprueba después cómo ha quedado y te avisa si no coincide "
             "(por ejemplo, «La moto ha quedado DESBLOQUEADA — vuelve a bloquear»).",
             "En esa misma lista está [[pke_unlock_on_connect_off]]. Tócalo para activarlo: al pulsar [[main_connect]] "
             "la moto se desbloquea a la vez. Nunca lo hace en la conexión automática.",
         ],
         note="Que la app diga «Orden enviada» no garantiza que la moto la haya hecho: **compruébalo siempre** "
              "en la moto. Si no reacciona, mándanos el registro (última píldora).",
         foot="Cierra la app oficial Zontes Smart antes de usarlo"),

    dict(slug="05-widget", icon="🧩", tag="Pantalla de inicio",
         title="Widget [[widget_control_label]]",
         sub="Las acciones de la moto sin abrir la app.",
         steps=[
             "Mantén pulsado un hueco vacío de la pantalla de inicio del móvil → **Widgets**.",
             "Busca **RideScreen AA** y arrastra [[widget_control_label]] a la pantalla.",
             "Botones: [[main_connect]], [[pke_short_unlock]], [[pke_short_lock]], [[pke_short_seat]] y [[pke_short_fuel]].",
             "[[pke_short_unlock]] y [[pke_short_lock]] van a un toque. [[pke_short_seat]] y [[pke_short_fuel]] "
             "piden confirmación.",
             "Si lo haces más pequeño, se queda en una fila solo con iconos.",
         ],
         note="El widget no enseña si la moto está bloqueada de verdad. Para eso mira la tarjeta "
              "**[[pke_card_title]]** en la app.",
         foot="Necesita el código PKE y el McuID puestos"),

    dict(slug="06-cuadro", icon="👋", tag="En el cuadro",
         title="Bienvenida, despedida y lluvia",
         sub="Pequeñas tarjetas que salen en el cuadro unos segundos. No tienes que activar nada.",
         steps=[
             "**Bienvenida (3 s)** al empezar a proyectar: saludo, nombre y foto de tu moto, el tiempo y tu racha de días.",
             "**Despedida (5 s)** al pulsar [[main_stop]] o aparcar con foto: [[dc_bye_title]] con km, minutos, "
             "media y máxima.",
             "Al aparcar, si va a llover donde has dejado la moto en las próximas horas, te llega un aviso: "
             "«[[rain_parked_title]]».",
         ],
         note="Cuéntanos si las tarjetas se ven bien en tu cuadro o pasan demasiado rápido. Si puedes, "
              "una foto del cuadro nos ayuda mucho.",
         foot="El tiempo sale de Open-Meteo con la ubicación del móvil"),

    dict(slug="07-si-falla", icon="🐞", tag="Si algo falla",
         title="Qué revisar y qué mandarnos",
         sub="Antes de escribir al grupo, prueba esto:",
         steps=[
             "**Cierra la app oficial Zontes Smart** (también en segundo plano): si está conectada a la moto, "
             "RideScreen AA no la encuentra.",
             "Acércate a la moto y comprueba que el Bluetooth del móvil está encendido.",
             "Comprueba que la app tiene **Dispositivos cercanos** permitido (ajustes de Android → Aplicaciones → "
             "RideScreen AA → Permisos).",
             "Si sigue fallando: [[help_pill]] → [[help_share_logs]] y mándanos el archivo por privado, "
             "con la hora aproximada y qué botón pulsaste.",
         ],
         note="El registro no lleva tu McuID ni el código PKE completo. Aun así, mándalo por privado, no al grupo.",
         foot="Gracias por probar la 2.4"),
]
