"""Píldoras en castellano. Orden = número de la píldora (1–16 como las publicadas; 17–21, nuevas de la 2.3).
Sintaxis en screens/pills.py. Los botones de la app van como [[clave]] para que salgan tal cual en cada idioma."""

PILLS = [
    dict(slug="01-primera-conexion", icon="🔌", tag="Empezar",
         title="Primera conexión con la moto",
         sub="Solo se hace una vez por moto: la app aprende el Wi‑Fi del cuadro leyendo su QR.",
         steps=[
             "Abre la app, entra en [[home_settings_short|⚙️]] → [[setup_get_started]] y concede los permisos que pide "
             "(cámara, ubicación, notificaciones, dispositivos cercanos).",
             "En **Android Auto**: ajustes → toca **Versión** unas 10 veces → menú [[=⋮]] → "
             "**Iniciar servidor de unidad principal**.",
             "Enciende la moto y pon el cuadro en la **interfaz 3** (la del QR de MotoPlay).",
             "En la app pulsa [[main_scan]] y apunta al QR. Si la cámara no lo lee, usa [[qr_from_photo]].",
             "Pulsa [[main_connect]] y acepta el Wi‑Fi de la moto cuando Android te lo pregunte.",
         ],
         note="Si Android pregunta «¿Conectar a ZT…?», pulsa **Conectar**: es el Wi‑Fi de tu moto. "
              "Si dejas el aviso abierto, la conexión no avanza.",
         foot="Las siguientes veces basta con pulsar Conectar"),

    dict(slug="02-conexion-automatica", icon="🔵", tag="Sin tocar el móvil",
         title="Que conecte sola al arrancar la moto",
         sub="La app puede ponerse en marcha sola cuando el móvil se conecta al Bluetooth de la moto.",
         steps=[
             "Empareja antes el móvil con el Bluetooth de la moto en los ajustes de Android "
             "(en Zontes empieza por **ZT**).",
             "En la app: [[home_settings_short|⚙️]] → **[[setup_bt_trigger]]** → elige el de la moto.",
             "Concede **Mostrar sobre otras apps** cuando te lo pida: sin ese permiso, Android no deja que la app "
             "arranque Android Auto en segundo plano.",
             "Deja activado **[[watch_setting]]**: si tardas en volver, la app sigue pendiente del Wi‑Fi de la moto "
             "y conecta sola.",
         ],
         note="Si alguna vez no arranca sola, te saldrá una notificación «**ZT… encendida · Toca para conectar**». "
              "Un toque y listo. En Xiaomi, Redmi y POCO activa también el **Inicio automático** (la app te avisa).",
         foot="Funciona con la moto guardada en el Garaje"),

    dict(slug="03-android-auto-no-responde", icon="⚠️", tag="Si algo se atasca",
         title="Android Auto no arranca en el cuadro",
         sub="Casi siempre es el servidor de unidad principal de Android Auto, que se queda parado o colgado "
             "(pasa mucho tras reiniciar el móvil).",
         steps=[
             "Abre **Android Auto** → menú [[=⋮]] → **Detener servidor de unidad principal**.",
             "Vuelve al menú [[=⋮]] → **Iniciar servidor de unidad principal**.",
             "Vuelve a RideScreen AA y pulsa [[main_connect]].",
             "¿No ves esas opciones en el menú? En los ajustes de Android Auto toca **Versión** unas 10 veces para "
             "desbloquearlas.",
         ],
         note="En Android 15 y 16, concede también **Mostrar sobre otras apps** a RideScreen AA: es lo que le deja "
              "abrir Android Auto sin que tengas el móvil en la mano.",
         foot="Si sigue fallando: [[help_pill]] → [[help_share_logs]]"),

    dict(slug="04-ajustar-pantalla", icon="🎯", tag="Pantalla",
         title="Ajustar la imagen al cuadro",
         sub="Mueve los bordes con flechas en el móvil y mira el resultado en directo en la moto.",
         steps=[
             "Conecta con la moto y ve a [[home_settings_short|⚙️]] → [[fit_open]].",
             "En el cuadro aparece un **marco amarillo**: es donde se dibuja Android Auto.",
             "Mueve cada borde con sus flechas hasta que la línea amarilla quede justo en el borde visible de la "
             "pantalla. Mantén pulsada una flecha para ir más rápido.",
             "Elige cómo llenar el marco: **[[fit_mode_fill]]** (sin franjas, recorta un poco), "
             "**[[fit_mode_fit]]** o **[[fit_mode_stretch]]**.",
             "¿El mapa se ve recortado o aplastado? Pulsa [[fit_shape_from_frame]] para que Android Auto se dibuje "
             "justo a esa medida.",
         ],
         note="Si algo queda mal, [[fit_undo]] o [[fit_reset]] lo devuelven a como estaba.",
         foot="Cada moto del Garaje guarda su propio ajuste"),

    dict(slug="05-voz", icon="🎙️", tag="Voz",
         title="«Ok Google» y el botón de voz",
         sub="Para hablar con el Asistente desde el intercomunicador, el reloj o «Ok Google», la app necesita el "
             "micrófono.",
         steps=[
             "Al pulsar [[main_connect]] la app te pedirá el micrófono: elige **Mientras se usa la app**.",
             "Con «Solo esta vez» o «Preguntar siempre», la voz puede dejar de funcionar a mitad del viaje.",
             "Si lo denegaste: Ajustes del móvil → Aplicaciones → **RideScreen AA** → Permisos → **Micrófono** → "
             "Permitir mientras se usa la app.",
             "El micrófono que se usa es el del dispositivo Bluetooth conectado (intercomunicador, gafas…).",
         ],
         note="Prueba en parado: «Ok Google, llévame a casa» y mira que la ruta salga en el cuadro.",
         foot="Sin micrófono, Android Auto funciona igual, pero sin voz"),

    dict(slug="06-detector-caidas", icon="🆘", tag="Seguridad · Experimental",
         title="Detector de caídas (experimental)",
         sub="Si vas en moto y hay un golpe fuerte seguido de quietud, suena una alarma de 60 s en el móvil y en el "
             "reloj.",
         steps=[
             "[[home_settings_short|⚙️]] → [[crash_entry]] → actívalo.",
             "Escribe tu nombre y añade hasta **3 contactos** de emergencia.",
             "Si nadie pulsa **[[crash_im_ok]]** en 60 s, se envía un SMS con tu ubicación a esos contactos, y otro a "
             "los 5 minutos.",
             "Pruébalo con [[crash_simulate]] (no envía nada) y [[crash_test_sms]].",
         ],
         note="**Está en fase experimental:** puede dar falsas alarmas o no detectar una caída. No es un dispositivo "
              "de seguridad certificado y nunca llama a emergencias. Avisa a tus contactos para que un mensaje no "
              "les asuste. Para los SMS, mira la píldora 15.",
         foot="Activo siempre que estés conectado a la moto"),

    dict(slug="07-aparcamiento", icon="🅿️", tag="Tu moto",
         title="Dónde aparqué (con foto)",
         sub="La app guarda dónde dejas la moto al desconectarte del cuadro, sin tener que pulsar nada.",
         steps=[
             "Mientras estás conectado, pulsa la casilla [[park_photo_button]]: haces una foto del sitio "
             "(planta, número de plaza…) y se desconecta.",
             "El aparcamiento aparece en la pantalla principal, en el **widget** y en el **reloj**.",
             "Con la moto aparcada, la tarjeta [[home_parked_title]] de la pantalla principal tiene [[parked_walk]] y "
             "[[home_view_photo]]. Tócala para repetir la foto u olvidar el sitio.",
             "En el reloj, la **brújula** apunta a tu moto y te dice a qué distancia está.",
         ],
         note="Añade el widget «**[[widget_label]]**» a la pantalla de inicio: verás dónde está la moto y "
              "tu último viaje sin abrir la app.",
         foot="La foto se borra al olvidar el sitio"),

    dict(slug="08-gasolineras", icon="⛽", tag="Ruta",
         title="Gasolineras cerca",
         sub="Precio de la Gasolina 95 en las gasolineras cercanas, con datos oficiales del Ministerio.",
         steps=[
             "En la pantalla principal pulsa la casilla [[home_tile_fuel|⛽]].",
             "Ordénalas por [[fuel_sort_price]] o [[fuel_sort_distance]].",
             "Toca una y Google Maps te lleva hasta ella, en el cuadro si estás conectado.",
         ],
         note="Al repostar, apúntalo en [[home_tile_maint|🔧]] → [[maint_refuel]] con el depósito lleno: así la app "
              "calcula tu consumo real.",
         foot="De momento, solo gasolineras de España"),

    dict(slug="09-viajes-records", icon="🏆", tag="Viajes",
         title="Viajes, récords y trofeos",
         sub="Cada salida con la app se guarda sola: ruta coloreada por velocidad, km, tiempo y un nombre tipo "
             "«Sants → Zona Franca».",
         steps=[
             "Comprueba que [[setup_log_trips_automatically]] está activado en [[home_settings_short|⚙️]].",
             "Pulsa la casilla [[home_tile_trips|🏍️]] y toca cualquiera para verlo en el mapa.",
             "Ahí mismo, [[all_rides_button]] muestra todas tus rutas o el mapa de **[[map_mode_heat]]** "
             "(mes, año o siempre), y [[week_button]] el resumen de la semana.",
             "La tarjeta de 🏆 **Récords** te lleva a los **Trofeos**: km totales, velocidad punta, días seguidos, "
             "madrugador…",
         ],
         note="Los túneles no cortan el viaje: el tramo sin GPS se une en línea recta al salir.",
         foot="Todo se calcula en tu móvil, sin cuentas ni nubes"),

    dict(slug="10-video-viaje", icon="🎬", tag="Viajes",
         title="Vídeo del viaje",
         sub="Junta los clips que grabaste durante un viaje (Ray-Ban Meta, cámara de acción, móvil…) con la ruta, "
             "la velocidad y la hora encima.",
         steps=[
             "Pasa antes los clips a la galería del móvil. Los de las gafas, impórtalos en la app **Meta AI**.",
             "[[home_tile_trips|🏍️]]: en la ficha del viaje, si hay clips de esa hora verás [[tv_button_clips(N)]].",
             "Marca los clips que quieras, elige [[tv_speed_normal]] o [[tv_speed_fast]] y pulsa [[tv_make]]. "
             "Deja la pantalla abierta mientras se crea.",
             "Se guarda en **Películas/RideScreen AA**, listo para compartir.",
         ],
         note="Si la imagen va adelantada o retrasada respecto a la velocidad, ajústalo en "
              "**[[tv_sync_title]]** (±1 s / ±5 s).",
         foot="Grabar en marcha: la vista, siempre en la carretera"),

    dict(slug="11-semaforos", icon="🚦", tag="Viajes",
         title="Tus semáforos y trayectos",
         sub="Con tus viajes guardados, la app descubre en qué semáforos paras más y cuál de tus caminos es de "
             "verdad más rápido.",
         steps=[
             "Pulsa la casilla [[home_tile_tl|🚦]] de la pantalla principal.",
             "En el mapa: **rojo** = paras casi siempre, **amarillo** = a veces, **verde** = pocas veces. "
             "Toca uno para ver la espera media.",
             "En **[[tl_routes_title]]** verás tus rutas alternativas A → B comparadas. 🏆 = la más rápida "
             "normalmente.",
         ],
         note="Cuantos más viajes hagas por la misma zona, más fiable es. Los semáforos salen de OpenStreetMap.",
         foot="Calculado en tu móvil a partir de tus viajes"),

    dict(slug="12-mantenimiento", icon="🔧", tag="Tu moto",
         title="Libreta de mantenimiento",
         sub="Aceite, correa, frenos, ITV, seguro… con aviso cuando toca, contando los km que haces con la app.",
         steps=[
             "Pulsa la casilla [[home_tile_maint|🔧]] de la pantalla principal (te muestra la tarea más urgente).",
             "Pon los km que marca ahora el cuadro con [[maint_fix_km]].",
             "En cada tarea, pulsa **[[maint_edit]]** e indica cuándo se hizo por última vez. Con una 125X, "
             "[[maint_plan_125]] carga la tabla del manual.",
             "Al repostar con el depósito lleno, pulsa [[maint_refuel]]: con dos repostajes verás tu consumo real "
             "(L/100 km).",
         ],
         note="Los trayectos que hagas sin la app no suman: corrige los km de vez en cuando.",
         foot="Te avisa al conectar con la moto"),

    dict(slug="13-reloj", icon="⌚", tag="Wear OS",
         title="La app del reloj",
         sub="Próxima maniobra, aparcamiento, brújula hacia tu moto y la alarma del detector de caídas, en la muñeca.",
         steps=[
             "La primera vez, instálala por depuración inalámbrica: lo explica la [[help_watch_guide]], en "
             "[[help_pill]].",
             "Después se actualiza desde el móvil: [[help_pill]] → [[help_update_watch]].",
             "Mientras vas conectado: próxima maniobra y vibraciones de giro (experimental).",
             "Con la moto aparcada, la página **Tu moto** te dice hace cuánto aparcaste y la **brújula** te lleva "
             "hasta ella.",
             "¿Reloj sin bisel? ▲ ▼ recorren los menús; **mantén pulsado** para moverte por Android Auto.",
         ],
         note="Si salta el detector de caídas, puedes pulsar «Estoy bien» directamente en el reloj.",
         foot="Necesita el móvil cerca, conectado por Bluetooth"),

    dict(slug="14-si-algo-falla", icon="❓", tag="Ayuda",
         title="Si algo falla",
         sub="Con el registro de lo que ha pasado se encuentra la causa casi siempre. Y no contiene tus contraseñas.",
         steps=[
             "Justo después del fallo, pulsa [[help_pill]] → [[help_share_logs]].",
             "Envíalo por el grupo de Telegram contando qué hacías y qué ha pasado (y a qué hora, más o menos).",
             "En [[help_pill]] también tienes [[help_view_logs]], la [[help_guide]] y [[help_whats_new]].",
         ],
         note="Los registros ocultan números de serie y contraseñas. Los informes anónimos de errores ayudan a ver "
              "qué falla más a menudo.",
         foot="Cuanto antes lo compartas, más completo está"),

    dict(slug="15-permisos-sms", icon="🆘", tag="Seguridad",
         title="Activar los SMS del detector de caídas",
         sub="Android bloquea el permiso de SMS a las apps instaladas desde un APK («ajustes restringidos»). "
             "Se desbloquea una sola vez:",
         steps=[
             "En la app: [[home_settings_short|⚙️]] → [[crash_entry]] y actívalo. Android dirá que el permiso está "
             "**restringido**: es normal.",
             "Pulsa [[crash_restricted_open]] (o Ajustes del móvil → Aplicaciones → **RideScreen AA**).",
             "Toca [[=⋮]] arriba a la derecha → **Permitir ajustes restringidos** y confirma con tu PIN o huella.",
             "[[=Permisos]] → [[=SMS]] → **Permitir**.",
             "Vuelve a la app, añade tus contactos y pulsa [[crash_test_sms]] para comprobarlo.",
         ],
         note="¿No aparece ⋮ → **Permitir ajustes restringidos**? Intenta antes activar el SMS en Permisos una vez "
              "(saldrá bloqueado) y vuelve a mirar: la opción aparece después de ese primer intento.",
         foot="Solo hay que hacerlo una vez por instalación"),

    dict(slug="16-no-conecta", icon="🛠️", tag="Si no conecta",
         title="La app no conecta con la moto",
         sub="Revisa estas cosas en orden. Casi siempre es una de ellas.",
         steps=[
             "Desliza desde arriba en el móvil y comprueba que están encendidos el **Wi‑Fi** y la **Ubicación**. "
             "Sin la ubicación, el móvil no es capaz de encontrar el Wi‑Fi de la moto.",
             "Enciende la moto y pon el cuadro en la **pantalla del QR** (interfaz 3). La moto solo enciende su Wi‑Fi "
             "en esa pantalla.",
             "Comprueba que el móvil «ve» la moto: en **Ajustes → Wi‑Fi** del móvil tiene que aparecer una red que "
             "empieza por **ZT**. No hace falta tocarla. Si no sale, apaga y enciende la moto.",
             "Abre **Android Auto** → menú [[=⋮]] → **Iniciar servidor de unidad principal**. Repítelo cada vez que "
             "reinicies el móvil. ¿No sale esa opción? Toca **Versión** 10 veces en los ajustes de Android Auto.",
             "Abre RideScreen AA con el móvil desbloqueado y pulsa [[main_connect]]. Si Android pregunta "
             "«¿Conectar a ZT…?», pulsa **Conectar**.",
         ],
         note="Los pasos 1 y 4 son los que más se olvidan: el servidor de Android Auto se apaga solo al reiniciar el "
              "móvil.",
         foot="¿Sigue sin conectar? Comparte los registros de la app en el grupo"),

    # ---------------------------------------------------------------- nuevas de la 2.3
    dict(slug="17-que-moto-es", icon="🏍️", tag="Empezar",
         title="[[bike_picker_title]]",
         sub="Al escanear el QR de una moto nueva, la app te pregunta el modelo y aplica la configuración probada "
             "para su cuadro.",
         steps=[
             "Tras escanear el QR, elige tu moto: **Zontes 125X, 125V, 368G, 368K o 368E**.",
             "¿No está la tuya? Elige [[bike_picker_other]]: la app ajusta la imagen sola.",
             "Para cambiarlo después: [[garage_garage]] (en la fila **[[main_more]]**) → toca la moto → "
             "[[bike_picker_menu]].",
         ],
         note="¿Tienes otra moto y te funciona? Comparte los registros en el grupo: así se puede añadir a la lista.",
         foot="Se guarda para cada moto del Garaje"),

    dict(slug="18-casa-trabajo", icon="🏠", tag="Ruta",
         title="Casa, Trabajo y tus citas",
         sub="Con la moto conectada, tus destinos de siempre están a un toque, debajo del buscador.",
         steps=[
             "Toca [[dest_home|🏠]] o [[dest_work|💼]] y escribe la dirección, como la buscarías en Maps.",
             "Desde entonces, un toque en el botón y la ruta sale en el cuadro.",
             "Mantén pulsado el botón para cambiar la dirección y elegir a quién avisar: al salir le llega "
             "«llegaré sobre las…» por SMS o WhatsApp.",
             "[[dest_calendar|📅]]: con permiso de calendario, aparecen tus próximas citas con lugar y vas con un toque.",
         ],
         note="Sin permiso de SMS se abre la app de mensajes con el texto listo para enviar.",
         foot="El botón 🧭 elige si la ruta se abre en Google Maps o en Waze"),

    dict(slug="19-caja-negra", icon="🎥", tag="Seguridad · Experimental",
         title="Caja negra",
         sub="Con el detector de caídas activo, el móvil recuerda el último minuto de conducción: velocidad, "
             "frenada, inclinación y posición.",
         steps=[
             "Solo si detecta un impacto guarda ese minuto y los 30 s siguientes. Se guardan los 10 últimos.",
             "Ábrela en [[home_settings_short|⚙️]] → [[crash_entry]] → [[bb_open(2)]].",
             "Cada impacto trae un gráfico de −60 s a +30 s y **[[bb_figures]]**: velocidad, frenada más fuerte, "
             "fuerza máxima, inclinación y lugar.",
             "[[bb_share]] crea un informe con el recorrido en GPX.",
         ],
         note="**Experimental:** datos aproximados del GPS y los sensores del móvil, no certificados. Nada sale del "
              "móvil salvo que tú lo compartas.",
         foot="Necesita el detector de caídas activado"),

    dict(slug="20-bateria", icon="🔋", tag="Tu moto",
         title="Batería de la moto",
         sub="Si tu moto tiene keyless (PKE), la app lee el voltaje de la batería por Bluetooth.",
         steps=[
             "[[help_pill]] → [[pke_entry]].",
             "Escribe el código PKE de tu moto: está en la app oficial Zontes Smart → gestión de motos, debajo de la "
             "moto.",
             "Con la moto cerca verás el voltaje y si está bloqueada. Tras una lectura, la tarjeta "
             "**[[pke_card_title]]** sale también en la pantalla principal.",
             "Si el voltaje es bajo te avisa: si la moto lleva días parada, quizá necesite un paseo o una carga.",
         ],
         note="Si la app oficial de Zontes está conectada a la caja, RideScreen AA no puede leerla: ciérrala antes.",
         foot="En la 2.3, solo lectura"),

    dict(slug="21-modo-pasajero", icon="👥", tag="Viajes",
         title="Modo pasajero",
         sub="Tu pasajero escribe el destino, se mueve por Android Auto y cambia la música desde su propio móvil.",
         steps=[
             "Los dos necesitáis RideScreen AA. En los dos móviles: [[help_pill]] → [[pax_entry]].",
             "El conductor, conectado a la moto: en **[[pax_as_rider]]**, toca [[pax_pair_new]].",
             "El pasajero: en **[[pax_as_passenger]]**, toca [[pax_search]] y elige la moto.",
             "Comprobad que las dos pantallas muestran el mismo código y tocad [[pax_code_yes]]. "
             "Solo hace falta la primera vez.",
         ],
         note="Nada pasa por internet: los móviles hablan por Bluetooth, y los vinculados solo pueden enviar "
              "destinos, teclas de Android Auto y controles de música.",
         foot="Se vincula una sola vez"),
]
