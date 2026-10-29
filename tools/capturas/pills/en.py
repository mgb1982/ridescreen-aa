"""Tips in English. Same order and syntax as pills/es.py."""

PILLS = [
    dict(icon="🔌", tag="Getting started",
         title="First connection to your bike",
         sub="You only do this once per bike: the app learns the dash's Wi‑Fi by reading its QR code.",
         steps=[
             "Open the app, go to [[home_settings_short|⚙️]] → [[setup_get_started]] and grant the permissions it "
             "asks for (camera, location, notifications, nearby devices).",
             "In **Android Auto**: settings → tap **Version** about 10 times → menu [[=⋮]] → "
             "**Start head unit server**.",
             "Switch the bike on and put the dash on **interface 3** (the MotoPlay QR screen).",
             "In the app tap [[main_scan]] and point at the QR. If the camera won't read it, use [[qr_from_photo]].",
             "Tap [[main_connect]] and accept the bike's Wi‑Fi when Android asks.",
         ],
         note="If Android asks «Connect to ZT…?», tap **Connect**: it's your bike's Wi‑Fi. "
              "If you leave the prompt open, the connection won't go ahead.",
         foot="Next time, just tap Connect"),

    dict(icon="🔵", tag="Hands off the phone",
         title="Connect by itself when you start the bike",
         sub="The app can start on its own when the phone connects to the bike's Bluetooth.",
         steps=[
             "First pair the phone with the bike's Bluetooth in Android settings (on Zontes it starts with **ZT**).",
             "In the app: [[home_settings_short|⚙️]] → **[[setup_bt_trigger]]** → pick the bike's.",
             "Grant **Display over other apps** when asked: without it, Android won't let the app start Android Auto "
             "in the background.",
             "Leave **[[watch_setting]]** on: if you take a while to come back, the app keeps watching for the "
             "bike's Wi‑Fi and connects by itself.",
         ],
         note="If it ever doesn't start by itself, you'll get a «**[[bt_trigger_title(ZT…)]] · "
              "[[bt_trigger_text]]**» notification. One tap and you're done. On Xiaomi, Redmi and POCO also turn on "
              "**Autostart** (the app tells you).",
         foot="Works with the bike saved in the Garage"),

    dict(icon="⚠️", tag="If something gets stuck",
         title="Android Auto won't start on the dash",
         sub="It's almost always Android Auto's head unit server, stopped or hung (very common after restarting "
             "the phone).",
         steps=[
             "Open **Android Auto** → menu [[=⋮]] → **Stop head unit server**.",
             "Back in the menu [[=⋮]] → **Start head unit server**.",
             "Go back to RideScreen AA and tap [[main_connect]].",
             "Don't see those options? In Android Auto settings tap **Version** about 10 times to unlock them.",
         ],
         note="On Android 15 and 16, also grant RideScreen AA **Display over other apps**: that's what lets it "
              "open Android Auto without the phone in your hand.",
         foot="Still failing? [[help_pill]] → [[help_share_logs]]"),

    dict(icon="🎯", tag="Screen",
         title="Fit the picture to the dash",
         sub="Move the edges with arrows on the phone and watch the result live on the bike.",
         steps=[
             "Connect to the bike and go to [[home_settings_short|⚙️]] → [[fit_open]].",
             "A **yellow frame** appears on the dash: that's where Android Auto is drawn.",
             "Move each edge with its arrows until the yellow line sits right on the visible edge of the screen. "
             "Hold an arrow to go faster.",
             "Choose how to fill the frame: **[[fit_mode_fill]]** (no bars, crops a little), **[[fit_mode_fit]]** "
             "or **[[fit_mode_stretch]]**.",
             "Map looks cropped or squashed? Tap [[fit_shape_from_frame]] so Android Auto draws at exactly that size.",
         ],
         note="If something ends up wrong, [[fit_undo]] or [[fit_reset]] put it back.",
         foot="Each bike in the Garage keeps its own setting"),

    dict(icon="🎙️", tag="Voice",
         title="«Ok Google» and the voice button",
         sub="To talk to the Assistant from your intercom, your watch or «Ok Google», the app needs the microphone.",
         steps=[
             "When you tap [[main_connect]] the app asks for the microphone: choose **While using the app**.",
             "With «Only this time» or «Ask every time», voice can stop working halfway through a ride.",
             "If you denied it: phone Settings → Apps → **RideScreen AA** → Permissions → **Microphone** → "
             "Allow only while using the app.",
             "The microphone used is the connected Bluetooth device's (intercom, glasses…).",
         ],
         note="Try it standing still: «Ok Google, take me home» and check the route shows up on the dash.",
         foot="Without the microphone Android Auto still works, just without voice"),

    dict(icon="🆘", tag="Safety · Experimental",
         title="Crash detection (experimental)",
         sub="If you're riding and there's a hard hit followed by stillness, a 60 s alarm goes off on the phone "
             "and the watch.",
         steps=[
             "[[home_settings_short|⚙️]] → [[crash_entry]] → turn it on.",
             "Type your name and add up to **3 emergency contacts**.",
             "If nobody taps **[[crash_im_ok]]** within 60 s, a text with your location goes to those contacts, "
             "and another after 5 minutes.",
             "Test it with [[crash_simulate]] (sends nothing) and [[crash_test_sms]].",
         ],
         note="**It's experimental:** it can raise false alarms or miss a crash. It isn't a certified safety device "
              "and never calls emergency services. Tell your contacts so a message doesn't scare them. For the "
              "texts, see tip 15.",
         foot="Active whenever you're connected to the bike"),

    dict(icon="🅿️", tag="Your bike",
         title="Where I parked (with a photo)",
         sub="The app saves where you leave the bike when you disconnect from the dash, without tapping anything.",
         steps=[
             "While connected, tap the [[park_photo_button]] tile: you take a photo of the spot (floor, bay "
             "number…) and it disconnects.",
             "The parking spot shows on the main screen, in the **widget** and on the **watch**.",
             "With the bike parked, the [[home_parked_title]] card on the main screen has [[parked_walk]] and "
             "[[home_view_photo]]. Tap the card to retake the photo or forget the spot.",
             "On the watch, the **compass** points to your bike and tells you how far it is.",
         ],
         note="Add the «**[[widget_label]]**» widget to your home screen: you'll see where the bike is and your "
              "last ride without opening the app.",
         foot="The photo is deleted when you forget the spot"),

    dict(icon="⛽", tag="On the road",
         title="Petrol stations nearby",
         sub="Unleaded 95 prices at nearby stations, from the Spanish Ministry's official data.",
         steps=[
             "On the main screen tap the [[home_tile_fuel|⛽]] tile.",
             "Sort them by [[fuel_sort_price]] or [[fuel_sort_distance]].",
             "Tap one and Google Maps takes you there, on the dash if you're connected.",
         ],
         note="When you fill up, log it in [[home_tile_maint|🔧]] → [[maint_refuel]] with a full tank: that's how "
              "the app works out your real consumption.",
         foot="For now, only stations in Spain"),

    dict(icon="🏆", tag="Rides",
         title="Rides, records and trophies",
         sub="Every ride with the app is saved by itself: route coloured by speed, km, time and a name like "
             "«Sants → Zona Franca».",
         steps=[
             "Check that [[setup_log_trips_automatically]] is on in [[home_settings_short|⚙️]].",
             "Tap the [[home_tile_trips|🏍️]] tile and tap any ride to see it on the map.",
             "Right there, [[all_rides_button]] shows all your routes or the **[[map_mode_heat]]** map (month, year "
             "or all time), and [[week_button]] the week's summary.",
             "The 🏆 **Records** card takes you to **Trophies**: total km, top speed, days in a row, early bird…",
         ],
         note="Tunnels don't split the ride: the stretch without GPS is joined with a straight line when you come "
              "out.",
         foot="All worked out on your phone, no accounts or cloud"),

    dict(icon="🎬", tag="Rides",
         title="Ride video",
         sub="Puts together the clips you shot during a ride (Ray-Ban Meta, action cam, phone…) with the route, "
             "speed and time on top.",
         steps=[
             "First move the clips to the phone's gallery. For the glasses, import them in the **Meta AI** app.",
             "[[home_tile_trips|🏍️]]: on the ride's page, if there are clips from that time you'll see "
             "[[tv_button_clips(N)]].",
             "Tick the clips you want, choose [[tv_speed_normal]] or [[tv_speed_fast]] and tap [[tv_make]]. "
             "Keep the screen open while it's made.",
             "It's saved in **Movies/RideScreen AA**, ready to share.",
         ],
         note="If the picture runs ahead of or behind the speed, adjust it in **[[tv_sync_title]]** (±1 s / ±5 s).",
         foot="Filming on the move: eyes always on the road"),

    dict(icon="🚦", tag="Rides",
         title="Your red lights and routes",
         sub="From your saved rides, the app finds which lights you stop at most and which of your routes is "
             "really the fastest.",
         steps=[
             "Tap the [[home_tile_tl|🚦]] tile on the main screen.",
             "On the map: **red** = you almost always stop, **yellow** = sometimes, **green** = rarely. "
             "Tap one to see the average wait.",
             "In **[[tl_routes_title]]** you'll see your alternative A → B routes compared. 🏆 = usually the fastest.",
         ],
         note="The more rides you do in the same area, the more reliable it gets. Traffic lights come from "
              "OpenStreetMap.",
         foot="Worked out on your phone from your rides"),

    dict(icon="🔧", tag="Your bike",
         title="Maintenance log",
         sub="Oil, belt, brakes, MOT, insurance… with a reminder when it's due, counting the km you ride with "
             "the app.",
         steps=[
             "Tap the [[home_tile_maint|🔧]] tile on the main screen (it shows the most urgent task).",
             "Enter what the dash reads now with [[maint_fix_km]].",
             "On each task, tap **[[maint_edit]]** and say when it was last done. On a 125X, [[maint_plan_125]] "
             "loads the manual's table.",
             "When you fill up a full tank, tap [[maint_refuel]]: after two fill-ups you'll see your real "
             "consumption (L/100 km).",
         ],
         note="Rides without the app don't count: correct the km now and then.",
         foot="Reminds you when you connect to the bike"),

    dict(icon="⌚", tag="Wear OS",
         title="The watch app",
         sub="Next turn, parking spot, compass to your bike and the crash alarm, on your wrist.",
         steps=[
             "The first time, install it over wireless debugging: the [[help_watch_guide]] in [[help_pill]] "
             "explains how.",
             "After that it updates from the phone: [[help_pill]] → [[help_update_watch]].",
             "While connected: next turn and turn vibrations (experimental).",
             "With the bike parked, the **Your bike** page tells you how long ago you parked and the **compass** "
             "takes you there.",
             "Watch without a bezel? ▲ ▼ move through the menus; **press and hold** to move around Android Auto.",
         ],
         note="If the crash alarm goes off, you can tap [[w:crash_im_ok]] right on the watch.",
         foot="Needs the phone nearby, connected over Bluetooth"),

    dict(icon="❓", tag="Help",
         title="If something goes wrong",
         sub="With the log of what happened, the cause is almost always found. And it doesn't contain your "
             "passwords.",
         steps=[
             "Right after the problem, tap [[help_pill]] → [[help_share_logs]].",
             "Send it to the Telegram group saying what you were doing and what happened (and roughly when).",
             "In [[help_pill]] you also have [[help_view_logs]], the [[help_guide]] and [[help_whats_new]].",
         ],
         note="Logs hide serial numbers and passwords. Anonymous error reports help show what fails most often.",
         foot="The sooner you share it, the more complete it is"),

    dict(icon="🆘", tag="Safety",
         title="Turn on texts for crash detection",
         sub="Android blocks the SMS permission for apps installed from an APK («restricted settings»). "
             "You unlock it once:",
         steps=[
             "In the app: [[home_settings_short|⚙️]] → [[crash_entry]] and turn it on. Android will say the "
             "permission is **restricted**: that's normal.",
             "Tap [[crash_restricted_open]] (or phone Settings → Apps → **RideScreen AA**).",
             "Tap [[=⋮]] at the top right → **Allow restricted settings** and confirm with your PIN or fingerprint.",
             "[[=Permissions]] → [[=SMS]] → **Allow**.",
             "Back in the app, add your contacts and tap [[crash_test_sms]] to check it.",
         ],
         note="No ⋮ → **Allow restricted settings**? First try turning SMS on in Permissions once (it'll show as "
              "blocked) and look again: the option appears after that first attempt.",
         foot="You only do this once per install"),

    dict(icon="🛠️", tag="If it won't connect",
         title="The app won't connect to the bike",
         sub="Check these in order. It's almost always one of them.",
         steps=[
             "Swipe down from the top of the phone and check that **Wi‑Fi** and **Location** are on. "
             "Without location, the phone can't find the bike's Wi‑Fi.",
             "Switch the bike on and put the dash on the **QR screen** (interface 3). The bike only turns its Wi‑Fi "
             "on in that screen.",
             "Check the phone «sees» the bike: in the phone's **Settings → Wi‑Fi** there should be a network "
             "starting with **ZT**. No need to tap it. If it isn't there, switch the bike off and on.",
             "Open **Android Auto** → menu [[=⋮]] → **Start head unit server**. Do it again every time you restart "
             "the phone. Option not there? Tap **Version** 10 times in Android Auto settings.",
             "Open RideScreen AA with the phone unlocked and tap [[main_connect]]. If Android asks "
             "«Connect to ZT…?», tap **Connect**.",
         ],
         note="Steps 1 and 4 are the ones people forget: Android Auto's server switches off whenever you restart "
              "the phone.",
         foot="Still not connecting? Share the app's logs in the group"),

    # ---------------------------------------------------------------- new in 2.3
    dict(icon="🏍️", tag="Getting started",
         title="[[bike_picker_title]]",
         sub="When you scan a new bike's QR, the app asks which model it is and applies the tested setup for its "
             "dash.",
         steps=[
             "After scanning the QR, pick your bike: **Zontes 125X, 125V, 368G, 368K or 368E**.",
             "Yours isn't there? Pick [[bike_picker_other]]: the app fits the picture by itself.",
             "To change it later: [[garage_garage]] (in the **[[main_more]]** row) → tap the bike → "
             "[[bike_picker_menu]].",
         ],
         note="Got another bike and it works? Share the logs in the group so it can be added to the list.",
         foot="Saved for each bike in the Garage"),

    dict(icon="🏠", tag="On the road",
         title="Home, Work and your appointments",
         sub="With the bike connected, your usual destinations are one tap away, under the search box.",
         steps=[
             "Tap [[dest_home|🏠]] or [[dest_work|💼]] and type the address, as you'd search for it in Maps.",
             "From then on, one tap on the button and the route shows on the dash.",
             "Press and hold the button to change the address and choose who to let know: when you leave, they get "
             "«I'll be there around…» by SMS or WhatsApp.",
             "[[dest_calendar|📅]]: with calendar permission, your next appointments with a place show up and you go "
             "with one tap.",
         ],
         note="Without SMS permission, your messages app opens with the text ready to send.",
         foot="The 🧭 button picks whether the route opens in Google Maps or Waze"),

    dict(icon="🎥", tag="Safety · Experimental",
         title="Black box",
         sub="With crash detection on, the phone remembers the last minute of riding: speed, braking, lean and "
             "position.",
         steps=[
             "Only if it detects an impact does it save that minute and the next 30 s. The last 10 are kept.",
             "Open it in [[home_settings_short|⚙️]] → [[crash_entry]] → [[bb_open(2)]].",
             "Each impact comes with a chart from −60 s to +30 s and **[[bb_figures]]**: speed, hardest braking, "
             "peak force, lean and place.",
             "[[bb_share]] makes a report with the track as GPX.",
         ],
         note="**Experimental:** approximate data from the phone's GPS and sensors, not certified. Nothing leaves "
              "the phone unless you share it.",
         foot="Needs crash detection turned on"),

    dict(icon="🔋", tag="Your bike",
         title="Bike battery",
         sub="If your bike has keyless (PKE), the app reads the battery voltage over Bluetooth.",
         steps=[
             "[[help_pill]] → [[pke_entry]].",
             "Type your bike's PKE code: it's in the official Zontes Smart app → bike management, under the bike.",
             "With the bike nearby you'll see the voltage and whether it's locked. After a reading, the "
             "**[[pke_card_title]]** card also shows on the main screen.",
             "If the voltage is low it warns you: if the bike has been parked for days, it may need a ride or a "
             "charge.",
         ],
         note="If the official Zontes app is connected to the box, RideScreen AA can't read it: close it first.",
         foot="In 2.3, read-only"),

    dict(icon="👥", tag="Rides",
         title="Passenger mode",
         sub="Your passenger types the destination, moves around Android Auto and changes the music from their own "
             "phone.",
         steps=[
             "You both need RideScreen AA. On both phones: [[help_pill]] → [[pax_entry]].",
             "The rider, connected to the bike: under **[[pax_as_rider]]**, tap [[pax_pair_new]].",
             "The passenger: under **[[pax_as_passenger]]**, tap [[pax_search]] and pick the bike.",
             "Check both screens show the same code and tap [[pax_code_yes]]. Only the first time.",
         ],
         note="Nothing goes over the internet: the phones talk over Bluetooth, and paired phones can only send "
              "destinations, Android Auto keys and music controls.",
         foot="Pair once"),
]
