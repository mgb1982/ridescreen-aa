"""Pillole in italiano. Stesso ordine e sintassi di pills/es.py. «Il Wi‑Fi», come nell'app."""

PILLS = [
    dict(icon="🔌", tag="Per iniziare",
         title="Prima connessione con la moto",
         sub="Si fa una sola volta per moto: l'app impara il Wi‑Fi del cruscotto leggendo il suo QR.",
         steps=[
             "Apri l'app, vai in [[home_settings_short|⚙️]] → [[setup_get_started]] e concedi i permessi che chiede "
             "(fotocamera, posizione, notifiche, dispositivi nelle vicinanze).",
             "In **Android Auto**: impostazioni → tocca **Versione** circa 10 volte → menu [[=⋮]] → "
             "**Avvia server unità principale**.",
             "Accendi la moto e metti il cruscotto sull'**interfaccia 3** (quella del QR di MotoPlay).",
             "Nell'app tocca [[main_scan]] e inquadra il QR. Se la fotocamera non lo legge, usa [[qr_from_photo]].",
             "Tocca [[main_connect]] e accetta il Wi‑Fi della moto quando Android te lo chiede.",
         ],
         note="Se Android chiede «Connettersi a ZT…?», tocca **Connetti**: è il Wi‑Fi della tua moto. "
              "Se lasci l'avviso aperto, la connessione non va avanti.",
         foot="Le volte successive basta toccare Connetti"),

    dict(icon="🔵", tag="Senza toccare il telefono",
         title="Che si colleghi da sola quando accendi la moto",
         sub="L'app può avviarsi da sola quando il telefono si collega al Bluetooth della moto.",
         steps=[
             "Prima abbina il telefono al Bluetooth della moto nelle impostazioni di Android "
             "(sulle Zontes inizia con **ZT**).",
             "Nell'app: [[home_settings_short|⚙️]] → **[[setup_bt_trigger]]** → scegli quello della moto.",
             "Concedi **Mostra sopra altre app** quando te lo chiede: senza quel permesso Android non lascia che "
             "l'app avvii Android Auto in background.",
             "Lascia attivo **[[watch_setting]]**: se ci metti un po' a tornare, l'app resta in attesa del Wi‑Fi "
             "della moto e si collega da sola.",
         ],
         note="Se qualche volta non parte da sola, vedrai una notifica «**[[bt_trigger_title(ZT…)]] · "
              "[[bt_trigger_text]]**». Un tocco e via. Su Xiaomi, Redmi e POCO attiva anche l'**Avvio automatico** "
              "(l'app te lo segnala).",
         foot="Funziona con la moto salvata nel Garage"),

    dict(icon="⚠️", tag="Se qualcosa si blocca",
         title="Android Auto non parte sul cruscotto",
         sub="Quasi sempre è il server unità principale di Android Auto, fermo o bloccato (succede spesso dopo aver "
             "riavviato il telefono).",
         steps=[
             "Apri **Android Auto** → menu [[=⋮]] → **Arresta server unità principale**.",
             "Torna al menu [[=⋮]] → **Avvia server unità principale**.",
             "Torna su RideScreen AA e tocca [[main_connect]].",
             "Non vedi queste opzioni nel menu? Nelle impostazioni di Android Auto tocca **Versione** circa 10 volte "
             "per sbloccarle.",
         ],
         note="Su Android 15 e 16 concedi anche **Mostra sopra altre app** a RideScreen AA: è ciò che le permette "
              "di aprire Android Auto senza che tu abbia il telefono in mano.",
         foot="Se continua a non funzionare: [[help_pill]] → [[help_share_logs]]"),

    dict(icon="🎯", tag="Schermo",
         title="Adattare l'immagine al cruscotto",
         sub="Sposta i bordi con le frecce sul telefono e guarda il risultato in diretta sulla moto.",
         steps=[
             "Collegati alla moto e vai in [[home_settings_short|⚙️]] → [[fit_open]].",
             "Sul cruscotto compare una **cornice gialla**: è dove viene disegnato Android Auto.",
             "Sposta ogni bordo con le sue frecce finché la linea gialla non coincide con il bordo visibile dello "
             "schermo. Tieni premuta una freccia per andare più veloce.",
             "Scegli come riempire la cornice: **[[fit_mode_fill]]** (senza bande, taglia un po'), "
             "**[[fit_mode_fit]]** o **[[fit_mode_stretch]]**.",
             "La mappa appare tagliata o schiacciata? Tocca [[fit_shape_from_frame]] perché Android Auto venga "
             "disegnato esattamente a quella misura.",
         ],
         note="Se qualcosa non va, [[fit_undo]] o [[fit_reset]] riportano tutto com'era.",
         foot="Ogni moto del Garage salva la sua regolazione"),

    dict(icon="🎙️", tag="Voce",
         title="«Ok Google» e il tasto della voce",
         sub="Per parlare con l'Assistente dall'interfono, dall'orologio o con «Ok Google», l'app ha bisogno del "
             "microfono.",
         steps=[
             "Quando tocchi [[main_connect]] l'app ti chiede il microfono: scegli **Mentre usi l'app**.",
             "Con «Solo questa volta» o «Chiedi ogni volta», la voce può smettere di funzionare a metà viaggio.",
             "Se l'hai negato: Impostazioni del telefono → App → **RideScreen AA** → Autorizzazioni → **Microfono** → "
             "Consenti solo mentre l'app è in uso.",
             "Il microfono usato è quello del dispositivo Bluetooth collegato (interfono, occhiali…).",
         ],
         note="Prova da fermo: «Ok Google, portami a casa» e controlla che il percorso compaia sul cruscotto.",
         foot="Senza microfono Android Auto funziona lo stesso, ma senza voce"),

    dict(icon="🆘", tag="Sicurezza · Sperimentale",
         title="Rilevamento cadute (sperimentale)",
         sub="Se sei in moto e c'è un colpo forte seguito da immobilità, suona un allarme di 60 s sul telefono e "
             "sull'orologio.",
         steps=[
             "[[home_settings_short|⚙️]] → [[crash_entry]] → attivalo.",
             "Scrivi il tuo nome e aggiungi fino a **3 contatti** di emergenza.",
             "Se nessuno tocca **[[crash_im_ok]]** entro 60 s, parte un SMS con la tua posizione a quei contatti, e "
             "un altro dopo 5 minuti.",
             "Provalo con [[crash_simulate]] (non invia nulla) e [[crash_test_sms]].",
         ],
         note="**È sperimentale:** può dare falsi allarmi o non rilevare una caduta. Non è un dispositivo di "
              "sicurezza certificato e non chiama mai i soccorsi. Avvisa i tuoi contatti perché un messaggio non li "
              "spaventi. Per gli SMS, guarda la pillola 15.",
         foot="Attivo ogni volta che sei collegato alla moto"),

    dict(icon="🅿️", tag="La tua moto",
         title="Dove ho parcheggiato (con foto)",
         sub="L'app salva dove lasci la moto quando ti scolleghi dal cruscotto, senza toccare nulla.",
         steps=[
             "Mentre sei collegato, tocca la casella [[park_photo_button]]: scatti una foto del posto (piano, numero "
             "del posto…) e si scollega.",
             "Il parcheggio compare nella schermata principale, nel **widget** e sull'**orologio**.",
             "Con la moto parcheggiata, la scheda [[home_parked_title]] della schermata principale ha [[parked_walk]] "
             "e [[home_view_photo]]. Toccala per rifare la foto o dimenticare il posto.",
             "Sull'orologio la **bussola** punta alla tua moto e ti dice a che distanza è.",
         ],
         note="Aggiungi il widget «**[[widget_label]]**» alla schermata Home: vedrai dov'è la moto e il tuo ultimo "
              "viaggio senza aprire l'app.",
         foot="La foto si cancella quando dimentichi il posto"),

    dict(icon="⛽", tag="In viaggio",
         title="Distributori vicini",
         sub="Prezzo della benzina 95 nei distributori vicini, con i dati ufficiali del Ministero spagnolo.",
         steps=[
             "Nella schermata principale tocca la casella [[home_tile_fuel|⛽]].",
             "Ordinali per [[fuel_sort_price]] o [[fuel_sort_distance]].",
             "Toccane uno e Google Maps ti ci porta, sul cruscotto se sei collegato.",
         ],
         note="Quando fai benzina, segnalo in [[home_tile_maint|🔧]] → [[maint_refuel]] con il pieno: così l'app "
              "calcola il tuo consumo reale.",
         foot="Per ora solo distributori in Spagna"),

    dict(icon="🏆", tag="Viaggi",
         title="Viaggi, record e trofei",
         sub="Ogni uscita con l'app si salva da sola: percorso colorato per velocità, km, tempo e un nome tipo "
             "«Sants → Zona Franca».",
         steps=[
             "Controlla che [[setup_log_trips_automatically]] sia attivo in [[home_settings_short|⚙️]].",
             "Tocca la casella [[home_tile_trips|🏍️]] e poi un viaggio qualsiasi per vederlo sulla mappa.",
             "Lì stesso, [[all_rides_button]] mostra tutti i tuoi percorsi o la mappa **[[map_mode_heat]]** (mese, "
             "anno o sempre), e [[week_button]] il riepilogo della settimana.",
             "La scheda 🏆 **Record** ti porta ai **Trofei**: km totali, velocità massima, giorni di fila, "
             "mattiniero…",
         ],
         note="Le gallerie non spezzano il viaggio: il tratto senza GPS si unisce in linea retta all'uscita.",
         foot="Tutto calcolato sul tuo telefono, senza account né cloud"),

    dict(icon="🎬", tag="Viaggi",
         title="Video del viaggio",
         sub="Unisce le clip che hai girato durante un viaggio (Ray-Ban Meta, action cam, telefono…) con percorso, "
             "velocità e ora in sovrimpressione.",
         steps=[
             "Prima copia le clip nella galleria del telefono. Quelle degli occhiali, importale nell'app **Meta AI**.",
             "[[home_tile_trips|🏍️]]: nella scheda del viaggio, se ci sono clip di quell'ora vedrai "
             "[[tv_button_clips(N)]].",
             "Seleziona le clip che vuoi, scegli [[tv_speed_normal]] o [[tv_speed_fast]] e tocca [[tv_make]]. "
             "Lascia lo schermo aperto mentre viene creato.",
             "Si salva in **Movies/RideScreen AA**, pronto da condividere.",
         ],
         note="Se l'immagine è in anticipo o in ritardo rispetto alla velocità, regolala in "
              "**[[tv_sync_title]]** (±1 s / ±5 s).",
         foot="Riprendere in marcia: lo sguardo sempre sulla strada"),

    dict(icon="🚦", tag="Viaggi",
         title="I tuoi semafori e tragitti",
         sub="Con i tuoi viaggi salvati, l'app scopre a quali semafori ti fermi di più e quale dei tuoi percorsi è "
             "davvero il più veloce.",
         steps=[
             "Tocca la casella [[home_tile_tl|🚦]] della schermata principale.",
             "Sulla mappa: **rosso** = ti fermi quasi sempre, **giallo** = a volte, **verde** = di rado. "
             "Toccane uno per vedere l'attesa media.",
             "In **[[tl_routes_title]]** vedrai i tuoi percorsi alternativi A → B a confronto. 🏆 = di solito il più "
             "veloce.",
         ],
         note="Più viaggi fai nella stessa zona, più è affidabile. I semafori vengono da OpenStreetMap.",
         foot="Calcolato sul tuo telefono a partire dai tuoi viaggi"),

    dict(icon="🔧", tag="La tua moto",
         title="Libretto di manutenzione",
         sub="Olio, cinghia, freni, revisione, assicurazione… con un avviso quando tocca, contando i km che fai con "
             "l'app.",
         steps=[
             "Tocca la casella [[home_tile_maint|🔧]] della schermata principale (mostra l'attività più urgente).",
             "Inserisci i km che segna ora il cruscotto con [[maint_fix_km]].",
             "Per ogni attività tocca **[[maint_edit]]** e indica quando è stata fatta l'ultima volta. Con una 125X, "
             "[[maint_plan_125]] carica la tabella del manuale.",
             "Quando fai il pieno, tocca [[maint_refuel]]: dopo due rifornimenti vedrai il tuo consumo reale "
             "(L/100 km).",
         ],
         note="I tragitti fatti senza l'app non contano: correggi i km ogni tanto.",
         foot="Ti avvisa quando ti colleghi alla moto"),

    dict(icon="⌚", tag="Wear OS",
         title="L'app dell'orologio",
         sub="Prossima manovra, parcheggio, bussola verso la tua moto e l'allarme del rilevamento cadute, al polso.",
         steps=[
             "La prima volta, installala con il debug wireless: lo spiega la [[help_watch_guide]], in [[help_pill]].",
             "Poi si aggiorna dal telefono: [[help_pill]] → [[help_update_watch]].",
             "Mentre sei collegato: prossima manovra e vibrazioni di svolta (sperimentale).",
             "Con la moto parcheggiata, la pagina **La tua moto** ti dice da quanto hai parcheggiato e la "
             "**bussola** ti ci porta.",
             "Orologio senza ghiera? ▲ ▼ scorrono i menu; **tieni premuto** per muoverti in Android Auto.",
         ],
         note="Se scatta il rilevamento cadute, puoi toccare [[w:crash_im_ok]] direttamente sull'orologio.",
         foot="Serve il telefono vicino, collegato via Bluetooth"),

    dict(icon="❓", tag="Aiuto",
         title="Se qualcosa non va",
         sub="Con il registro di quello che è successo si trova quasi sempre la causa. E non contiene le tue "
             "password.",
         steps=[
             "Subito dopo il problema, tocca [[help_pill]] → [[help_share_logs]].",
             "Invialo nel gruppo Telegram raccontando cosa stavi facendo e cosa è successo (e più o meno a che ora).",
             "In [[help_pill]] trovi anche [[help_view_logs]], la [[help_guide]] e [[help_whats_new]].",
         ],
         note="I registri nascondono numeri di serie e password. Le segnalazioni anonime di errori aiutano a capire "
              "cosa si rompe più spesso.",
         foot="Prima lo condividi, più è completo"),

    dict(icon="🆘", tag="Sicurezza",
         title="Attivare gli SMS del rilevamento cadute",
         sub="Android blocca il permesso SMS alle app installate da un APK («impostazioni con limitazioni»). "
             "Si sblocca una sola volta:",
         steps=[
             "Nell'app: [[home_settings_short|⚙️]] → [[crash_entry]] e attivalo. Android dirà che il permesso è "
             "**limitato**: è normale.",
             "Tocca [[crash_restricted_open]] (oppure Impostazioni del telefono → App → **RideScreen AA**).",
             "Tocca [[=⋮]] in alto a destra → **Consenti impostazioni con limitazioni** e conferma con PIN o "
             "impronta.",
             "[[=Autorizzazioni]] → [[=SMS]] → **Consenti**.",
             "Torna nell'app, aggiungi i tuoi contatti e tocca [[crash_test_sms]] per verificarlo.",
         ],
         note="Non compare ⋮ → **Consenti impostazioni con limitazioni**? Prima prova ad attivare gli SMS in "
              "Autorizzazioni una volta (risulterà bloccato) e guarda di nuovo: l'opzione compare dopo quel primo "
              "tentativo.",
         foot="Va fatto una sola volta per installazione"),

    dict(icon="🛠️", tag="Se non si collega",
         title="L'app non si collega alla moto",
         sub="Controlla queste cose in ordine. Quasi sempre è una di queste.",
         steps=[
             "Scorri dall'alto sul telefono e controlla che siano attivi il **Wi‑Fi** e la **Posizione**. "
             "Senza la posizione il telefono non riesce a trovare il Wi‑Fi della moto.",
             "Accendi la moto e metti il cruscotto sulla **schermata del QR** (interfaccia 3). La moto accende il "
             "suo Wi‑Fi solo in quella schermata.",
             "Controlla che il telefono «veda» la moto: in **Impostazioni → Wi‑Fi** del telefono deve comparire una "
             "rete che inizia con **ZT**. Non serve toccarla. Se non c'è, spegni e riaccendi la moto.",
             "Apri **Android Auto** → menu [[=⋮]] → **Avvia server unità principale**. Rifallo ogni volta che "
             "riavvii il telefono. L'opzione non c'è? Tocca **Versione** 10 volte nelle impostazioni di Android Auto.",
             "Apri RideScreen AA con il telefono sbloccato e tocca [[main_connect]]. Se Android chiede "
             "«Connettersi a ZT…?», tocca **Connetti**.",
         ],
         note="I passi 1 e 4 sono quelli che si dimenticano di più: il server di Android Auto si spegne da solo "
              "quando riavvii il telefono.",
         foot="Ancora niente? Condividi i registri dell'app nel gruppo"),

    # ---------------------------------------------------------------- novità della 2.3
    dict(icon="🏍️", tag="Per iniziare",
         title="[[bike_picker_title]]",
         sub="Quando scansioni il QR di una moto nuova, l'app ti chiede il modello e applica la configurazione "
             "provata per il suo cruscotto.",
         steps=[
             "Dopo aver scansionato il QR, scegli la tua moto: **Zontes 125X, 125V, 368G, 368K o 368E**.",
             "La tua non c'è? Scegli [[bike_picker_other]]: l'app regola l'immagine da sola.",
             "Per cambiarlo dopo: [[garage_garage]] (nella riga **[[main_more]]**) → tocca la moto → "
             "[[bike_picker_menu]].",
         ],
         note="Hai un'altra moto e funziona? Condividi i registri nel gruppo: così si può aggiungere all'elenco.",
         foot="Si salva per ogni moto del Garage"),

    dict(icon="🏠", tag="In viaggio",
         title="Casa, Lavoro e i tuoi appuntamenti",
         sub="Con la moto collegata, le tue destinazioni di sempre sono a un tocco, sotto la ricerca.",
         steps=[
             "Tocca [[dest_home|🏠]] o [[dest_work|💼]] e scrivi l'indirizzo, come lo cercheresti su Maps.",
             "Da lì in poi, un tocco sul pulsante e il percorso compare sul cruscotto.",
             "Tieni premuto il pulsante per cambiare l'indirizzo e scegliere chi avvisare: quando parti riceve "
             "«arrivo verso le…» via SMS o WhatsApp.",
             "[[dest_calendar|📅]]: con il permesso del calendario compaiono i tuoi prossimi appuntamenti con un "
             "luogo e ci vai con un tocco.",
         ],
         note="Senza il permesso SMS si apre l'app dei messaggi con il testo pronto da inviare.",
         foot="Il pulsante 🧭 sceglie se il percorso si apre in Google Maps o in Waze"),

    dict(icon="🎥", tag="Sicurezza · Sperimentale",
         title="Scatola nera",
         sub="Con il rilevamento cadute attivo, il telefono ricorda l'ultimo minuto di guida: velocità, frenata, "
             "inclinazione e posizione.",
         steps=[
             "Solo se rileva un impatto salva quel minuto e i 30 s successivi. Si tengono gli ultimi 10.",
             "Aprila in [[home_settings_short|⚙️]] → [[crash_entry]] → [[bb_open(2)]].",
             "Ogni impatto ha un grafico da −60 s a +30 s e **[[bb_figures]]**: velocità, frenata più forte, forza "
             "massima, inclinazione e luogo.",
             "[[bb_share]] crea un rapporto con il percorso in GPX.",
         ],
         note="**Sperimentale:** dati approssimativi del GPS e dei sensori del telefono, non certificati. Niente "
              "esce dal telefono se non lo condividi tu.",
         foot="Serve il rilevamento cadute attivo"),

    dict(icon="🔋", tag="La tua moto",
         title="Batteria della moto",
         sub="Se la tua moto ha il keyless (PKE), l'app legge la tensione della batteria via Bluetooth.",
         steps=[
             "[[help_pill]] → [[pke_entry]].",
             "Scrivi il codice PKE della tua moto: lo trovi nell'app ufficiale Zontes Smart → gestione moto, sotto "
             "la moto.",
             "Con la moto vicina vedrai la tensione e se è bloccata. Dopo una lettura, la scheda "
             "**[[pke_card_title]]** compare anche nella schermata principale.",
             "Se la tensione è bassa ti avvisa: se la moto è ferma da giorni, forse le serve un giro o una ricarica.",
         ],
         note="Se l'app ufficiale Zontes è collegata alla centralina, RideScreen AA non può leggerla: chiudila "
              "prima.",
         foot="Nella 2.3, sola lettura"),

    dict(icon="👥", tag="Viaggi",
         title="Modalità passeggero",
         sub="Il tuo passeggero scrive la destinazione, si muove in Android Auto e cambia la musica dal suo "
             "telefono.",
         steps=[
             "Vi serve RideScreen AA a tutti e due. Su entrambi i telefoni: [[help_pill]] → [[pax_entry]].",
             "Chi guida, collegato alla moto: in **[[pax_as_rider]]**, tocca [[pax_pair_new]].",
             "Il passeggero: in **[[pax_as_passenger]]**, tocca [[pax_search]] e scegli la moto.",
             "Controllate che i due schermi mostrino lo stesso codice e toccate [[pax_code_yes]]. Serve solo la "
             "prima volta.",
         ],
         note="Niente passa da internet: i telefoni parlano via Bluetooth, e quelli abbinati possono inviare solo "
              "destinazioni, tasti di Android Auto e comandi della musica.",
         foot="Si abbinano una sola volta"),
]
