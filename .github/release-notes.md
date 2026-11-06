**RideScreen AA** es Android Auto inalámbrico en el cuadro de tu moto (cuadros EasyConn/Carbit), con arreglos y extras pensados para las **Zontes 125X / ZT125T-X**. Es la continuación de la versión no oficial de OpenCfMoto que se ha ido compartiendo en el grupo, ahora con nombre, diseño e icono propios.

Basada en [OpenCfMoto](https://github.com/zanderp/open-cfmoto) de Alexandru (zanderp). No es una app oficial de Zontes ni de CFMoto. Código fuente en este repositorio, licencia AGPL-3.0.

> ⚠️ **¿La probaste alguna vez y no te funcionó? Dale otra oportunidad.** Desde la v2.2 es mucho más intuitiva, y la v2.3 y la v2.4 amplían la gama de motos compatibles.
>
> 📱 [Guía del móvil](https://ridescreen.app/guia/) · ⌚ [Guía del reloj](https://ridescreen.app/reloj/)

### Novedades de la v2.4

**🔑 La llave, en el móvil**
- **Desbloquear, bloquear, abrir el asiento y el depósito** desde la app (❓ Ayuda → 🔒 Bloquear / desbloquear), siempre con confirmación. Después de bloquear o desbloquear, la app vuelve a leer el estado y te avisa si no coincide.
- **Nunca con la moto en marcha**: bloquear, asiento y depósito se rechazan si la moto se mueve (velocidad del OBD o del GPS).
- **Widget «Controles de la moto»** (4×2, o 5×1 solo con iconos): Conectar, Desbloquear, Bloquear, Asiento y Depósito, con la foto de tu Garaje.
- **Desbloquear al conectar** (opcional, apagado por defecto). Solo cuando pulsas Conectar tú: nunca en la conexión automática.
- **Bloquear termina el viaje** (opcional): guarda dónde has aparcado y deja de proyectar. Nunca en marcha ni con el motor encendido.
- **El McuID se lee con la cámara** desde el QR de Información del cuadro. No va a las copias de seguridad.

**🔌 OBD**
- **Lector OBD** (❓ Ayuda → 🔌 Lector OBD) con un adaptador ELM327 Bluetooth, **solo lectura**: datos disponibles, códigos de avería y RPM, velocidad, temperatura y acelerador en directo.
- **Lectura continua mientras proyectas**: «motor en marcha» sale de las RPM reales.

**📊 Dashboard en el cuadro**
- En lugar de Android Auto, el cuadro puede mostrar un **dashboard con tus datos**: estilos **Sport, Racing y Touring**, de día y de noche, con los campos que elijas. Sin OBD, la velocidad sale del GPS.
- **Ajustado a la pantalla de la 125X**: nada importante queda debajo de la franja del cuadro, y la vista previa es exactamente lo que verás en la moto.
- **Escala de las esferas** a tu moto: velocímetro de 120 a 240 km/h y cuentarrevoluciones de 10.000 a 14.000 rpm (en una 125X viene en 140 km/h y 10.000 rpm). Zona roja desde 9.000 rpm.
- Se cambia desde Controles o el atajo del icono de la app, y puede **empezar directamente** al conectar.

**🏍️ Viajes**
- **Reproducción y vídeo del viaje con datos del OBD** (acelerador y RPM) y **fusionar recorridos** (se puede deshacer).

**🎬 En el cuadro y al aparcar**
- **Bienvenida** al empezar a proyectar y **despedida** con el resumen del día.
- **Aviso de lluvia al aparcar** para las próximas horas donde has dejado la moto.

**🎮 Mandos**
- Con «Los botones del manillar controlan Android Auto», ahora puedes dejar **▲/▼ para el volumen** (ideal si llevas intercomunicador): el volumen ya no se queda fijo.

**⌚ Reloj**
- **Pulso** del reloj en la app (❓ Ayuda), cada 5 segundos.
- La app del reloj, traducida a todos los idiomas de la app.

**Motos**
- **Zontes 350D y 350E** (Wi‑Fi Direct): **en pruebas** con la comunidad. Conectan, pero la conexión todavía tarda y la imagen va a pocas imágenes por segundo.
- Mejoras de Wi‑Fi Direct para la T350X.

**Idiomas**: francés, alemán, portugués, griego, ruso y tailandés **completos** (app y reloj), además de castellano, catalán, inglés e italiano.

**Y además**: iconos nuevos en inicio, en la tarjeta de la llave y en el widget; y arreglados los cierres que nos habéis reportado en la v2.3.

❤️ Gracias a Melo por prestar el iPhone para las pruebas de iOS, a Luis Gil por sus pruebas con el OBD, a **Inky en Moto** por los registros que hicieron funcionar la T350X y por su [vídeo sobre la app](https://youtube.com/@inkyenmoto), a todos los testers y a quienes apoyáis el proyecto en Ko-fi: Francisco Javier S., José Antonio R., Raul, Luis Gil, INKY, Juanma M., Alfonsomicra, Isra, Dionisios S., José Manuel, Karsten, Francesc Xavier M., Jon, Alberto y los donantes anónimos.

**Y todo lo de la v2.3**: widget Conectar, batería de la moto, Casa y Trabajo, más motos (toda la gama 368), viajes a salvo, importar GPX, vídeo del viaje, modo pasajero y mucho más.

### Instalación en el móvil
1. Descarga **`RideScreen-AA-v2.4.apk`** (abajo, en *Assets*).
2. Si vienes de la **v1/v1.1/v2/v2.2/v2.3** o de la versión del grupo (zontes-1/2), se instala **encima** sin perder nada. Si tienes la OpenCfMoto **oficial**, desinstálala primero: están firmadas con claves distintas.
3. Si sale el aviso de **Play Protect** ("app no segura" o "bloqueada"), es porque Google aún no conoce la app: **Más detalles → Instalar de todas formas**.
4. Las próximas versiones te las ofrecerá la propia app.

### Instalación en el reloj (opcional, Wear OS)
Desde la v2.2 el reloj se actualiza **desde el móvil**: **❓ Ayuda → ⌚ Actualizar la app del reloj** descarga **`RideScreen-AA-v2.4-WearOS-reloj.apk`** y se la envía por Bluetooth (tarda un par de minutos). Si es tu primera vez, la instalación inicial se hace por depuración inalámbrica: paso a paso en la [guía del reloj](https://ridescreen.app/reloj/).

### Si algo falla
Mira la [guía del móvil](https://ridescreen.app/guia/) (tiene los mensajes de error más habituales y qué hacer). Si sigue, en la app **❓ Ayuda → Compartir registros** y pásalo por el grupo contando qué ha pasado. Los registros ocultan números de serie y contraseñas.

☕ RideScreen AA es gratis y sin anuncios. Si te resulta útil y quieres que siga creciendo (Play Store, más motos, más funciones), puedes [invitarme a un café en Ko-fi](https://ko-fi.com/mgb1982).
