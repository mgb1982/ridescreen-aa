**RideScreen AA** es Android Auto inalámbrico en el cuadro de tu moto (cuadros EasyConn/Carbit), con arreglos y extras pensados para las **Zontes 125X / ZT125T-X**. Es la continuación de la versión no oficial de OpenCfMoto que se ha ido compartiendo en el grupo, ahora con nombre, diseño e icono propios.

Basada en [OpenCfMoto](https://github.com/zanderp/open-cfmoto) de Alexandru (zanderp). No es una app oficial de Zontes ni de CFMoto. Código fuente en este repositorio, licencia AGPL-3.0.

> ⚠️ **¿La probaste alguna vez y no te funcionó? Dale otra oportunidad.** Desde la v2.2 es mucho más intuitiva, y desde la v2.3 amplía la gama de motos compatibles.
>
> 📱 [Guía del móvil](https://ridescreen.app/guia/) · ⌚ [Guía del reloj](https://ridescreen.app/reloj/)

### Novedades de la v2.5

**📊 Dashboard: tu cuadro, a tu manera**
- **9 diseños nuevos**: LED, Analógico, VFD, Mínimo, Cabina, Clean, Pit, Classic y Focus (además de Sport, Racing y Touring), de día y de noche.
- **Color de acento**, para dejar el cuadro con tu tono (la zona roja sigue siendo roja).
- **Tu foto de fondo**, con ajuste «Rellenar» o «Encajar» y un velo para que las cifras se lean a pleno sol y de noche.
- **Pantalla partida «Android Auto + datos»**: Android Auto en una ventana a la izquierda y tus datos en una columna a la derecha. En Ajustes → Dashboard puedes elegir cuánto se corta por abajo (la barra de accesos de Android Auto) con un deslizador del 0 al 40 %. **(en pruebas)**
- **También sin adaptador OBD**: tarjetas con datos del móvil (hora, viaje, altitud, batería…) y reloj analógico en Classic y Sport.
- **Cronómetro 0–60 y 0–100 km/h** con cuenta atrás 3-2-1 en el cuadro.
- Hoja de **Estilo** y tarjeta **Dashboard** en Inicio, con vista previa de cómo queda y botón «Opciones del dashboard».
- **Cuadro sin conexión**: con la moto apagada ves la vista previa del cuadro (vertical y horizontal) y puedes conectar desde ahí.

**🏍️ Viajes y vídeos**
- **El viaje se graba** aunque la moto conecte con el móvil en el bolsillo. Pide el permiso de ubicación «**todo el tiempo**» una sola vez (puedes negarte). Si Android no deja usar el GPS, la notificación dice «Toca para grabar el viaje».
- Un **corte de enlace** (túnel, cuadro apagado un momento) ya no parte el viaje en dos (hasta 10 minutos) **(en pruebas)**, y el registro explica por qué se descarta un tramo.
- **Inclinación y fuerzas G** en los dashboards y en el vídeo del viaje, con calibración (Viajes → Calibrar inclinación: móvil en el soporte, moto recta y parada).
- **Tu pulso**, desde el reloj, en dashboards y vídeos, y **resumen final** del vídeo con tu inclinación y pulso máximos.
- **Nuevo estilo de vídeo «HUD»** (en inglés): velocidad con barra de RPM, arco de inclinación con pico y destello al batir récord, bola de G, corazón que late a tu pulso, rótulos TOP SPEED / MAX LEAN / MAX G y diseño vertical 9:16. **(en pruebas)**
- **Elige qué datos salen en el vídeo** (velocidad, RPM, refrigerante, acelerador y carga, voltaje, inclinación, G, pulso, minimapa y rótulos de récord) y un atajo «Vídeo corto». Lo que no se grabó en ese viaje sale desactivado. **(en pruebas)**
- **Gasolineras**: si tienes Waze y Google Maps, eliges con cuál navegar hasta cada una.
- **Modo espejo**: al pararlo (o si ya no hay proyección) libera la orientación del móvil.

**⌚ Reloj**
- **Bloquea la moto desde el reloj**: botón «Bloquear» con doble toque, solo bloquear, y rechazado si la moto se mueve.
- **Pulso continuo** durante el viaje, incluso con la pantalla del reloj apagada (servicio en segundo plano).
- **Actualiza la app del reloj desde el móvil**: Ajustes → Ayuda → Actualizar la app del reloj («Esta versión»).

**🔌 OBD y mantenimiento**
- **Kilometraje de la moto** leído del OBD (PID 31): Mantenimiento te pregunta una vez si quieres usarlo, y desde entonces lo aplica y te avisa si el valor baja o da un salto raro.
- La app lee **todo lo que la centralita ofrece** (carga, presión del colector, avance, sondas, ajustes de combustible…) y guarda un resumen de rangos al terminar el viaje en el registro.
- **Acelerador**: la ECU de la 125X solo manda hasta el 65 % con el gas a fondo; ahora el «throttle» se reescala para que a fondo marque 100 %. Hay un botón para restablecer la calibración (Moto → Restablecer calibración del acelerador).
- Lectura del OBD más robusta: espera creciente con la moto apagada, se para al aparcar y no relanza Android Auto al aparcar.

**🏠 Menús y uso diario**
- **Menús rediseñados**: pestañas Inicio, Viajes, Moto y Ajustes. La **Ayuda** está ahora en Ajustes; tocar la foto de tu moto abre el Garaje; el escáner de QR para añadir una moto está en el Garaje.
- Pantalla **Cuadro** con selector Android Auto / Dashboard / Mapa / Espejo.
- **Widget de aparcamiento** sin actualizaciones periódicas (ya no despierta la app cada 30 minutos): muestra la distancia y la hora a la que aparcaste, y se actualiza al aparcar, al guardar un viaje y al abrir la app.
- **Botones del manillar por Wi‑Fi** (beta, desactivado por defecto; Ajustes → Manillar y mandos): para cuadros que mandan los botones por la Wi‑Fi en vez de por Bluetooth (creemos que la gama 368). Música y volumen siempre; flechas, OK, atrás y asistente si los botones controlan Android Auto. **(en pruebas)**
- **Registros que duran**: se guardan en el móvil 7 días (hasta 20 MB), sobreviven a las actualizaciones y se comparten completos desde Ajustes → Ayuda → Compartir registros. Siguen ocultando números de serie, MAC y contraseñas.
- **Accesos directos del icono** (mantén pulsado el icono de la app): Bloquear, Desbloquear, Asiento y Depósito de la moto. De momento **no se pueden pedir por voz con Gemini**.

**🔑 Llave y seguridad**
- Bloquear desde la app ya no reconecta enseguida (retiene las conexiones automáticas hasta que desbloquees). **(en pruebas)**
- Una respuesta de rechazo de la caja se trata como rechazo: la app no guarda el aparcamiento ni termina el viaje hasta que la caja confirma «bloqueada». **(en pruebas)**
- **Detector de caídas** (experimental): tras un bache, si sigues circulando a 15 km/h o más durante 5 segundos, deja de vigilar al momento. Los umbrales y la alarma de 60 segundos antes de cualquier SMS no cambian.

**🔗 Conexión con el cuadro**
- **Reloj del cuadro a las 00:00**: la app contesta antes a la petición de vídeo del cuadro, para que pida la hora a tiempo (los registros muestran que se quedaba a 00:00 cuando la pedía pasados ~2 segundos). Si la primera petición llega tarde, la app vuelve a intentarlo sola hasta tres veces (antes se rendía a la segunda). **(en pruebas)** Si aun así se queda a 00:00, el botón de la app lo pone en hora.
- Android Auto elige el Bluetooth de la moto, no el del adaptador OBD.
- Pensado para gastar menos batería: menos redibujados, OBD que se detiene al aparcar y reintentos del Wi‑Fi más espaciados.

**🌍 Idiomas**: 18 idiomas. Búlgaro, checo, húngaro, coreano, neerlandés, polaco, rumano y turco pasan de estar a medias a **completos**. Traducidos con IA y sin revisión nativa: si hablas uno, ayúdanos a revisarlo.

**Motos**: lista ampliada en [ridescreen.app](https://ridescreen.app/) (150X, 150V, 125D, 125E, GPX DZ2 y las ya conocidas). La Zontes 350D y 350E siguen **en pruebas**.

❤️ Gracias a Melo por prestar el iPhone para las pruebas de iOS, a Luis Gil por sus pruebas a diario (y sus ideas para los menús), a quienes mandáis registros de motos nuevas, a todos los testers y a quienes apoyáis el proyecto en Ko-fi.

**Y todo lo de la v2.4**: la llave en el móvil, el lector OBD, el dashboard en el cuadro, los vídeos con datos del OBD, la copia de seguridad completa y mucho más.

### Instalación en el móvil
1. Descarga **`RideScreen-AA-v2.5.apk`** (abajo, en *Assets*).
2. Si vienes de la **v2.4.1** o de cualquier versión anterior (v1, v1.1, v2, v2.2, v2.3, v2.4, zontes-1/2), se instala **encima** sin perder nada. Si tienes la OpenCfMoto **oficial**, desinstálala primero: están firmadas con claves distintas.
3. Si sale el aviso de **Play Protect** ("app no segura" o "bloqueada"), es porque Google aún no conoce la app: **Más detalles → Instalar de todas formas**.
4. Las próximas versiones te las ofrecerá la propia app.

### Instalación en el reloj (opcional, Wear OS)
El reloj se actualiza **desde el móvil**: **Ajustes → Ayuda → ⌚ Actualizar la app del reloj** descarga **`RideScreen-AA-v2.5-WearOS-reloj.apk`** y se la envía por Bluetooth (tarda un par de minutos). Si es tu primera vez, la instalación inicial se hace por depuración inalámbrica: paso a paso en la [guía del reloj](https://ridescreen.app/reloj/). Para el pulso continuo, da el permiso del sensor en el reloj.

### Si algo falla
Mira la [guía del móvil](https://ridescreen.app/guia/) (tiene los mensajes de error más habituales y qué hacer). Si sigue, en la app **Ajustes → Ayuda → Compartir registros** y pásalo por el grupo contando qué ha pasado y a qué hora. Los registros ocultan números de serie y contraseñas.

☕ RideScreen AA es gratis, sin anuncios y de código abierto, y lo hace una sola persona con vuestra ayuda. Si te acompaña en cada trayecto y quieres que siga creciendo (más motos, más funciones, más idiomas), puedes [invitarme a un café en Ko-fi](https://ko-fi.com/mgb1982).
