"""Píndoles en català. Mateix ordre i sintaxi que pills/es.py. «La Wi‑Fi», com a l'app."""

PILLS = [
    dict(icon="🔌", tag="Començar",
         title="Primera connexió amb la moto",
         sub="Només es fa un cop per moto: l'app aprèn la Wi‑Fi del quadre llegint-ne el QR.",
         steps=[
             "Obre l'app, entra a [[home_settings_short|⚙️]] → [[setup_get_started]] i concedeix els permisos que "
             "demana (càmera, ubicació, notificacions, dispositius propers).",
             "A **Android Auto**: configuració → toca **Versió** unes 10 vegades → menú [[=⋮]] → "
             "**Inicia el servidor de la unitat principal**.",
             "Engega la moto i posa el quadre a la **interfície 3** (la del QR de MotoPlay).",
             "A l'app prem [[main_scan]] i apunta al QR. Si la càmera no el llegeix, fes servir [[qr_from_photo]].",
             "Prem [[main_connect]] i accepta la Wi‑Fi de la moto quan Android t'ho pregunti.",
         ],
         note="Si Android pregunta «Vols connectar-te a ZT…?», prem **Connecta**: és la Wi‑Fi de la teva moto. "
              "Si deixes l'avís obert, la connexió no avança.",
         foot="Les vegades següents n'hi ha prou de prémer Connecta"),

    dict(icon="🔵", tag="Sense tocar el mòbil",
         title="Que es connecti sola en engegar la moto",
         sub="L'app es pot posar en marxa sola quan el mòbil es connecta al Bluetooth de la moto.",
         steps=[
             "Abans, vincula el mòbil amb el Bluetooth de la moto a la configuració d'Android "
             "(a Zontes comença per **ZT**).",
             "A l'app: [[home_settings_short|⚙️]] → **[[setup_bt_trigger]]** → tria el de la moto.",
             "Concedeix **Mostra sobre altres aplicacions** quan t'ho demani: sense aquest permís, Android no deixa "
             "que l'app engegui Android Auto en segon pla.",
             "Deixa activat **[[watch_setting]]**: si tardes a tornar, l'app continua pendent de la Wi‑Fi de la moto "
             "i es connecta sola.",
         ],
         note="Si algun cop no s'engega sola, et sortirà una notificació «**[[bt_trigger_title(ZT…)]] · "
              "[[bt_trigger_text]]**». Un toc i llest. A Xiaomi, Redmi i POCO activa també l'**Inici automàtic** "
              "(l'app t'ho avisa).",
         foot="Funciona amb la moto desada al Garatge"),

    dict(icon="⚠️", tag="Si alguna cosa s'encalla",
         title="Android Auto no s'engega al quadre",
         sub="Gairebé sempre és el servidor de la unitat principal d'Android Auto, que es queda aturat o penjat "
             "(passa molt després de reiniciar el mòbil).",
         steps=[
             "Obre **Android Auto** → menú [[=⋮]] → **Atura el servidor de la unitat principal**.",
             "Torna al menú [[=⋮]] → **Inicia el servidor de la unitat principal**.",
             "Torna a RideScreen AA i prem [[main_connect]].",
             "No veus aquestes opcions al menú? A la configuració d'Android Auto toca **Versió** unes 10 vegades per "
             "desbloquejar-les.",
         ],
         note="A Android 15 i 16, concedeix també **Mostra sobre altres aplicacions** a RideScreen AA: és el que li "
              "permet obrir Android Auto sense que tinguis el mòbil a la mà.",
         foot="Si continua fallant: [[help_pill]] → [[help_share_logs]]"),

    dict(icon="🎯", tag="Pantalla",
         title="Ajustar la imatge al quadre",
         sub="Mou les vores amb fletxes al mòbil i mira el resultat en directe a la moto.",
         steps=[
             "Connecta't a la moto i ves a [[home_settings_short|⚙️]] → [[fit_open]].",
             "Al quadre apareix un **marc groc**: és on es dibuixa Android Auto.",
             "Mou cada vora amb les seves fletxes fins que la línia groga quedi just a la vora visible de la "
             "pantalla. Mantén premuda una fletxa per anar més de pressa.",
             "Tria com omplir el marc: **[[fit_mode_fill]]** (sense franges, retalla una mica), "
             "**[[fit_mode_fit]]** o **[[fit_mode_stretch]]**.",
             "El mapa es veu retallat o aixafat? Prem [[fit_shape_from_frame]] perquè Android Auto es dibuixi just "
             "a aquesta mida.",
         ],
         note="Si alguna cosa queda malament, [[fit_undo]] o [[fit_reset]] ho tornen a deixar com estava.",
         foot="Cada moto del Garatge desa el seu propi ajust"),

    dict(icon="🎙️", tag="Veu",
         title="«Ok Google» i el botó de veu",
         sub="Per parlar amb l'Assistent des de l'intercomunicador, el rellotge o «Ok Google», l'app necessita el "
             "micròfon.",
         steps=[
             "En prémer [[main_connect]] l'app et demanarà el micròfon: tria **Mentre s'utilitza l'aplicació**.",
             "Amb «Només aquesta vegada» o «Pregunta sempre», la veu pot deixar de funcionar a mig viatge.",
             "Si l'has denegat: Configuració del mòbil → Aplicacions → **RideScreen AA** → Permisos → **Micròfon** → "
             "Permet només mentre s'utilitza l'aplicació.",
             "El micròfon que es fa servir és el del dispositiu Bluetooth connectat (intercomunicador, ulleres…).",
         ],
         note="Prova-ho aturat: «Ok Google, porta'm a casa» i mira que la ruta surti al quadre.",
         foot="Sense micròfon, Android Auto funciona igual, però sense veu"),

    dict(icon="🆘", tag="Seguretat · Experimental",
         title="Detector de caigudes (experimental)",
         sub="Si vas amb moto i hi ha un cop fort seguit de quietud, sona una alarma de 60 s al mòbil i al rellotge.",
         steps=[
             "[[home_settings_short|⚙️]] → [[crash_entry]] → activa'l.",
             "Escriu el teu nom i afegeix fins a **3 contactes** d'emergència.",
             "Si ningú prem **[[crash_im_ok]]** en 60 s, s'envia un SMS amb la teva ubicació a aquests contactes, i "
             "un altre als 5 minuts.",
             "Prova'l amb [[crash_simulate]] (no envia res) i [[crash_test_sms]].",
         ],
         note="**És en fase experimental:** pot donar falses alarmes o no detectar una caiguda. No és un dispositiu "
              "de seguretat certificat i mai no truca a emergències. Avisa els teus contactes perquè un missatge no "
              "els espanti. Per als SMS, mira la píndola 15.",
         foot="Actiu sempre que estiguis connectat a la moto"),

    dict(icon="🅿️", tag="La teva moto",
         title="On he aparcat (amb foto)",
         sub="L'app desa on deixes la moto quan et desconnectes del quadre, sense haver de prémer res.",
         steps=[
             "Mentre estàs connectat, prem la casella [[park_photo_button]]: fas una foto del lloc "
             "(planta, número de plaça…) i es desconnecta.",
             "L'aparcament apareix a la pantalla principal, al **widget** i al **rellotge**.",
             "Amb la moto aparcada, la targeta [[home_parked_title]] de la pantalla principal té [[parked_walk]] i "
             "[[home_view_photo]]. Toca-la per repetir la foto o oblidar el lloc.",
             "Al rellotge, la **brúixola** apunta a la teva moto i et diu a quina distància és.",
         ],
         note="Afegeix el widget «**[[widget_label]]**» a la pantalla d'inici: veuràs on és la moto i el teu últim "
              "viatge sense obrir l'app.",
         foot="La foto s'esborra quan oblides el lloc"),

    dict(icon="⛽", tag="Ruta",
         title="Benzineres a prop",
         sub="Preu de la Gasolina 95 a les benzineres properes, amb dades oficials del Ministeri.",
         steps=[
             "A la pantalla principal prem la casella [[home_tile_fuel|⛽]].",
             "Ordena-les per [[fuel_sort_price]] o [[fuel_sort_distance]].",
             "Toca'n una i Google Maps t'hi porta, al quadre si estàs connectat.",
         ],
         note="Quan posis benzina, apunta-ho a [[home_tile_maint|🔧]] → [[maint_refuel]] amb el dipòsit ple: així "
              "l'app calcula el teu consum real.",
         foot="De moment, només benzineres d'Espanya"),

    dict(icon="🏆", tag="Viatges",
         title="Viatges, rècords i trofeus",
         sub="Cada sortida amb l'app es desa sola: ruta acolorida per velocitat, km, temps i un nom com "
             "«Sants → Zona Franca».",
         steps=[
             "Comprova que [[setup_log_trips_automatically]] està activat a [[home_settings_short|⚙️]].",
             "Prem la casella [[home_tile_trips|🏍️]] i toca'n qualsevol per veure'l al mapa.",
             "Allà mateix, [[all_rides_button]] mostra totes les teves rutes o el mapa de **[[map_mode_heat]]** "
             "(mes, any o sempre), i [[week_button]] el resum de la setmana.",
             "La targeta de 🏆 **Rècords** et porta als **Trofeus**: km totals, velocitat punta, dies seguits, "
             "matiner…",
         ],
         note="Els túnels no tallen el viatge: el tram sense GPS s'uneix en línia recta en sortir.",
         foot="Tot es calcula al teu mòbil, sense comptes ni núvols"),

    dict(icon="🎬", tag="Viatges",
         title="Vídeo del viatge",
         sub="Ajunta els clips que vas gravar durant un viatge (Ray-Ban Meta, càmera d'acció, mòbil…) amb la ruta, "
             "la velocitat i l'hora a sobre.",
         steps=[
             "Abans, passa els clips a la galeria del mòbil. Els de les ulleres, importa'ls a l'app **Meta AI**.",
             "[[home_tile_trips|🏍️]]: a la fitxa del viatge, si hi ha clips d'aquella hora veuràs "
             "[[tv_button_clips(N)]].",
             "Marca els clips que vulguis, tria [[tv_speed_normal]] o [[tv_speed_fast]] i prem [[tv_make]]. "
             "Deixa la pantalla oberta mentre es crea.",
             "Es desa a **Pel·lícules/RideScreen AA**, a punt per compartir.",
         ],
         note="Si la imatge va avançada o endarrerida respecte a la velocitat, ajusta-ho a "
              "**[[tv_sync_title]]** (±1 s / ±5 s).",
         foot="Gravar en marxa: la vista, sempre a la carretera"),

    dict(icon="🚦", tag="Viatges",
         title="Els teus semàfors i trajectes",
         sub="Amb els teus viatges desats, l'app descobreix en quins semàfors t'atures més i quin dels teus camins "
             "és de debò el més ràpid.",
         steps=[
             "Prem la casella [[home_tile_tl|🚦]] de la pantalla principal.",
             "Al mapa: **vermell** = t'atures gairebé sempre, **groc** = de vegades, **verd** = poques vegades. "
             "Toca'n un per veure l'espera mitjana.",
             "A **[[tl_routes_title]]** veuràs les teves rutes alternatives A → B comparades. 🏆 = la més ràpida "
             "normalment.",
         ],
         note="Com més viatges facis per la mateixa zona, més fiable és. Els semàfors surten d'OpenStreetMap.",
         foot="Calculat al teu mòbil a partir dels teus viatges"),

    dict(icon="🔧", tag="La teva moto",
         title="Llibreta de manteniment",
         sub="Oli, corretja, frens, ITV, assegurança… amb avís quan toca, comptant els km que fas amb l'app.",
         steps=[
             "Prem la casella [[home_tile_maint|🔧]] de la pantalla principal (et mostra la tasca més urgent).",
             "Posa els km que marca ara el quadre amb [[maint_fix_km]].",
             "A cada tasca, prem **[[maint_edit]]** i indica quan es va fer per última vegada. Amb una 125X, "
             "[[maint_plan_125]] carrega la taula del manual.",
             "Quan posis benzina amb el dipòsit ple, prem [[maint_refuel]]: amb dues càrregues veuràs el teu consum "
             "real (L/100 km).",
         ],
         note="Els trajectes que facis sense l'app no compten: corregeix els km de tant en tant.",
         foot="T'avisa quan et connectes a la moto"),

    dict(icon="⌚", tag="Wear OS",
         title="L'app del rellotge",
         sub="Propera maniobra, aparcament, brúixola cap a la teva moto i l'alarma del detector de caigudes, al "
             "canell.",
         steps=[
             "La primera vegada, instal·la-la per depuració sense fils: ho explica la [[help_watch_guide]], a "
             "[[help_pill]].",
             "Després s'actualitza des del mòbil: [[help_pill]] → [[help_update_watch]].",
             "Mentre vas connectat: propera maniobra i vibracions de gir (experimental).",
             "Amb la moto aparcada, la pàgina **La teva moto** et diu fa quant que has aparcat i la **brúixola** "
             "t'hi porta.",
             "Rellotge sense bisell? ▲ ▼ recorren els menús; **mantén premut** per moure't per Android Auto.",
         ],
         note="Si salta el detector de caigudes, pots prémer [[w:crash_im_ok]] directament al rellotge.",
         foot="Necessita el mòbil a prop, connectat per Bluetooth"),

    dict(icon="❓", tag="Ajuda",
         title="Si alguna cosa falla",
         sub="Amb el registre del que ha passat es troba la causa gairebé sempre. I no conté les teves contrasenyes.",
         steps=[
             "Just després de l'error, prem [[help_pill]] → [[help_share_logs]].",
             "Envia'l pel grup de Telegram explicant què feies i què ha passat (i a quina hora, més o menys).",
             "A [[help_pill]] també tens [[help_view_logs]], la [[help_guide]] i [[help_whats_new]].",
         ],
         note="Els registres amaguen números de sèrie i contrasenyes. Els informes anònims d'errors ajuden a veure "
              "què falla més sovint.",
         foot="Com més aviat el comparteixis, més complet és"),

    dict(icon="🆘", tag="Seguretat",
         title="Activar els SMS del detector de caigudes",
         sub="Android bloqueja el permís d'SMS a les apps instal·lades des d'un APK («configuració restringida»). "
             "Es desbloqueja un sol cop:",
         steps=[
             "A l'app: [[home_settings_short|⚙️]] → [[crash_entry]] i activa'l. Android dirà que el permís està "
             "**restringit**: és normal.",
             "Prem [[crash_restricted_open]] (o Configuració del mòbil → Aplicacions → **RideScreen AA**).",
             "Toca [[=⋮]] a dalt a la dreta → **Permet la configuració restringida** i confirma amb el PIN o "
             "l'empremta.",
             "[[=Permisos]] → [[=SMS]] → **Permet**.",
             "Torna a l'app, afegeix els teus contactes i prem [[crash_test_sms]] per comprovar-ho.",
         ],
         note="No apareix ⋮ → **Permet la configuració restringida**? Prova abans d'activar l'SMS a Permisos un cop "
              "(sortirà bloquejat) i torna a mirar: l'opció apareix després d'aquest primer intent.",
         foot="Només cal fer-ho un cop per instal·lació"),

    dict(icon="🛠️", tag="Si no es connecta",
         title="L'app no es connecta a la moto",
         sub="Revisa aquestes coses per ordre. Gairebé sempre és una d'elles.",
         steps=[
             "Llisca des de dalt al mòbil i comprova que estan activades la **Wi‑Fi** i la **Ubicació**. "
             "Sense la ubicació, el mòbil no és capaç de trobar la Wi‑Fi de la moto.",
             "Engega la moto i posa el quadre a la **pantalla del QR** (interfície 3). La moto només encén la seva "
             "Wi‑Fi en aquesta pantalla.",
             "Comprova que el mòbil «veu» la moto: a **Configuració → Wi‑Fi** del mòbil ha d'aparèixer una xarxa que "
             "comença per **ZT**. No cal tocar-la. Si no surt, apaga i engega la moto.",
             "Obre **Android Auto** → menú [[=⋮]] → **Inicia el servidor de la unitat principal**. Repeteix-ho cada "
             "vegada que reiniciïs el mòbil. No surt l'opció? Toca **Versió** 10 vegades a la configuració "
             "d'Android Auto.",
             "Obre RideScreen AA amb el mòbil desbloquejat i prem [[main_connect]]. Si Android pregunta "
             "«Vols connectar-te a ZT…?», prem **Connecta**.",
         ],
         note="Els passos 1 i 4 són els que més s'obliden: el servidor d'Android Auto s'apaga sol quan reinicies "
              "el mòbil.",
         foot="Continua sense connectar-se? Comparteix els registres de l'app al grup"),

    # ---------------------------------------------------------------- noves de la 2.3
    dict(icon="🏍️", tag="Començar",
         title="[[bike_picker_title]]",
         sub="Quan escanegis el QR d'una moto nova, l'app et pregunta el model i aplica la configuració provada per "
             "al seu quadre.",
         steps=[
             "Després d'escanejar el QR, tria la teva moto: **Zontes 125X, 125V, 368G, 368K o 368E**.",
             "No hi és la teva? Tria [[bike_picker_other]]: l'app ajusta la imatge sola.",
             "Per canviar-ho després: [[garage_garage]] (a la fila **[[main_more]]**) → toca la moto → "
             "[[bike_picker_menu]].",
         ],
         note="Tens una altra moto i et funciona? Comparteix els registres al grup: així es pot afegir a la llista.",
         foot="Es desa per a cada moto del Garatge"),

    dict(icon="🏠", tag="Ruta",
         title="Casa, Feina i les teves cites",
         sub="Amb la moto connectada, els teus destins de sempre són a un toc, sota el cercador.",
         steps=[
             "Toca [[dest_home|🏠]] o [[dest_work|💼]] i escriu l'adreça, com la buscaries a Maps.",
             "Des de llavors, un toc al botó i la ruta surt al quadre.",
             "Mantén premut el botó per canviar l'adreça i triar a qui avisar: quan surts, li arriba "
             "«arribaré cap a les…» per SMS o WhatsApp.",
             "[[dest_calendar|📅]]: amb permís de calendari, apareixen les teves properes cites amb lloc i hi vas amb "
             "un toc.",
         ],
         note="Sense permís d'SMS s'obre l'app de missatges amb el text a punt per enviar.",
         foot="El botó 🧭 tria si la ruta s'obre a Google Maps o a Waze"),

    dict(icon="🎥", tag="Seguretat · Experimental",
         title="Caixa negra",
         sub="Amb el detector de caigudes actiu, el mòbil recorda l'últim minut de conducció: velocitat, frenada, "
             "inclinació i posició.",
         steps=[
             "Només si detecta un impacte desa aquell minut i els 30 s següents. Es desen els 10 últims.",
             "Obre-la a [[home_settings_short|⚙️]] → [[crash_entry]] → [[bb_open(2)]].",
             "Cada impacte porta un gràfic de −60 s a +30 s i **[[bb_figures]]**: velocitat, frenada més forta, "
             "força màxima, inclinació i lloc.",
             "[[bb_share]] crea un informe amb el recorregut en GPX.",
         ],
         note="**Experimental:** dades aproximades del GPS i els sensors del mòbil, no certificades. No surt res del "
              "mòbil tret que tu ho comparteixis.",
         foot="Necessita el detector de caigudes activat"),

    dict(icon="🔋", tag="La teva moto",
         title="Bateria de la moto",
         sub="Si la teva moto té keyless (PKE), l'app llegeix el voltatge de la bateria per Bluetooth.",
         steps=[
             "[[help_pill]] → [[pke_entry]].",
             "Escriu el codi PKE de la teva moto: és a l'app oficial Zontes Smart → gestió de motos, sota la moto.",
             "Amb la moto a prop veuràs el voltatge i si està bloquejada. Després d'una lectura, la targeta "
             "**[[pke_card_title]]** surt també a la pantalla principal.",
             "Si el voltatge és baix t'avisa: si la moto fa dies que està aturada, potser li cal una volta o una "
             "càrrega.",
         ],
         note="Si l'app oficial de Zontes està connectada a la caixa, RideScreen AA no la pot llegir: tanca-la abans.",
         foot="A la 2.3, només lectura"),

    dict(icon="👥", tag="Viatges",
         title="Mode passatger",
         sub="El teu passatger escriu la destinació, es mou per Android Auto i canvia la música des del seu propi "
             "mòbil.",
         steps=[
             "Tots dos necessiteu RideScreen AA. Als dos mòbils: [[help_pill]] → [[pax_entry]].",
             "El conductor, connectat a la moto: a **[[pax_as_rider]]**, toca [[pax_pair_new]].",
             "El passatger: a **[[pax_as_passenger]]**, toca [[pax_search]] i tria la moto.",
             "Comproveu que les dues pantalles mostren el mateix codi i toqueu [[pax_code_yes]]. "
             "Només cal la primera vegada.",
         ],
         note="No passa res per internet: els mòbils parlen per Bluetooth, i els vinculats només poden enviar "
              "destinacions, tecles d'Android Auto i controls de música.",
         foot="Es vincula un sol cop"),
]
