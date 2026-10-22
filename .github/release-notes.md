**RideScreen AA** es Android Auto inalámbrico en el cuadro de tu moto (cuadros EasyConn/Carbit), con arreglos y extras pensados para las **Zontes 125X / ZT125T-X**. Es la continuación de la versión no oficial de OpenCfMoto que se ha ido compartiendo en el grupo, ahora con nombre, diseño e icono propios.

Basada en [OpenCfMoto](https://github.com/zanderp/open-cfmoto) de Alexandru (zanderp). No es una app oficial de Zontes ni de CFMoto. Código fuente en este repositorio, licencia AGPL-3.0.

> ⚠️ **¿La probaste alguna vez y no te funcionó? Dale otra oportunidad.** Desde la v2.2 es mucho más intuitiva, y la v2.3 amplía la gama de motos compatibles.
>
> 📱 [Guía del móvil](https://ridescreen.app/guia/) · ⌚ [Guía del reloj](https://ridescreen.app/reloj/)

### Novedades de la v2.3
**🏠 Tu moto, de un vistazo**
- **📲 Widget 1×1 «Conectar»** (idea de Luis Gil): un toque en tu pantalla de inicio conecta con la moto guardada. Lleva la foto de tu Garaje y un anillo de estado: gris (listo), dorado (conectando) y verde con ✓ (conectado).
- **🛡️ Sin cancelaciones por error**: el botón **Conectar/Detener** de la app se bloquea 3 segundos al empezar a conectar y se ve difuminado mientras tanto. El widget nunca cancela.
- **🔋 Tarjeta «Batería de la moto»**: voltaje y estado (bloqueada / desbloqueada) leídos de la caja keyless de la moto, **solo lectura**: nunca envía órdenes ni toca llaves, cerraduras ni firmware. También con la moto apagada y bloqueada. Se pone naranja o roja si la batería anda justa. Aparece al meter tu código PKE en **Ayuda → Batería** (solo lo muestra la app oficial de Zontes, en la gestión del vehículo).
- **🏍️ «Motor en marcha» / «motor parado»**: la app lo deduce del voltaje (sube al arrancar, baja al apagar) y lo muestra junto a «Conectado: proyectando». Con la app abierta, lee el voltaje cada 15 s mientras proyectas.
- **⚡ Velocidad en directo** en la pantalla principal mientras ruedas, y un **sonido al conectar o perder la moto** (6 estilos, pensados para ir con casco).
- **🏠 Casa, Trabajo y tus próximas citas** del calendario, a un toque al estar conectado. «Llego en X min» avisa de tu hora de llegada. Elige **Waze o Google Maps**.
- **📊 Tu racha, el resumen del domingo y el comparador de trayectos** («4 min más que lo habitual»).

**Más motos**
- **🏍️ «¿Qué moto es?»**: elige tu Zontes (125X, 125V, 368 G/K/E) y la app aplica la configuración de pantalla que sabemos que funciona. **Toda la gama 368 es compatible**.
- **En pruebas con la comunidad**: Zontes 125M, 350D y T350X (conexión por punto de acceso del móvil y Wi-Fi Direct).
- **Wi-Fi Direct y Android 17**: la app pide los permisos que hacen falta antes de hablar con el cuadro.

**Conexión**
- **🔌 El Bluetooth del cuadro** despierta la espera larga y puede **arrancar la conexión solo**: en Ajustes, *Conectar cuando se conecte un dispositivo Bluetooth*.
- **🔌 Android Auto se recupera solo** si se cae una vez enlazado el cuadro.
- **Primera conexión más clara**: pide «Dispositivos cercanos» al abrir y te lleva directo al interruptor del Wi-Fi si está apagado. En Xiaomi/Redmi/POCO explica el Autostart una sola vez.
- **🎙️ Micrófono del casco** fijado para la voz de Android Auto.
- **🎮 Controles siempre a mano** (fila «Más»), y se acabó el cierre cuando una parada coincidía con un arranque.

**Tus viajes**
- **🛟 Tus viajes, a salvo**: el viaje en curso se guarda cada 30 segundos. Si la app se cierra a mitad de camino, al volver a abrirla lo recupera y lo guarda en Viajes.
- **📥 Importar viaje (GPX)**: en Viajes puedes traer un recorrido grabado con otra app (o rescatado de la Cronología de Google) y cuenta como un viaje más: mapa, récords y kilómetros de mantenimiento.
- **🎬 Vídeo del viaje, mucho más completo**: 6 estilos, mapa de ruta, de colores por velocidad o de calles reales, tarjetas de apertura y cierre, vista previa en directo, elegir qué clips entran, **«Lo mejor del viaje»** en vertical, música y ×4 sincronizado. Se exporta en segundo plano y los vídeos quedan listados en la app.
- **🚦 Onda verde (observación)**: predice cada semáforo con tus viajes anteriores y puntúa el acierto.
- **🔧 Mantenimiento**: plan manual de la 125X (aceite, casquillos de la polea, dirección, válvulas…) y primer servicio a X km, luego cada Y.

**Seguridad y privacidad**
- **📦 Caja negra (experimental)** con el detector de caídas: guarda el último minuto antes del impacto, con informe y GPX. Incluye «Simular caída».
- **Copias de seguridad privadas**: sin contraseñas ni contactos.
- **Aviso de telemetría** claro y transparente.

**Pasajero**
- **👥 Modo pasajero**: tu acompañante mueve Android Auto, elige el destino y cambia la música desde su móvil. Avisos de «voy de camino» por WhatsApp.

**Reloj**
- **⌚ Anillo de velocidad** en la página principal y tarjeta con el **resumen del último viaje**.

**Control externo (avanzado)**
- Órdenes `app.ridescreen.action.*` por *broadcast* para Key Mapper (modo Experto con Shizuku), Tasker o MacroDroid. **Desactivado por defecto** y solo las obedece mientras proyectas.

**Idiomas**: toda la app en castellano, nueva traducción completa al **italiano**, y catalán.

**Y además**: la foto de tu moto en el Garaje ya no sale girada (vuelve a elegirla si te salía tumbada); el vídeo se recupera mejor si el decodificador del móvil se atasca; la hora del cuadro se puede volver a poner tocando el reloj de la tarjeta de estado; los mapas de viajes y semáforos reutilizan la caché (sin volver a descargar tras actualizar); dirección web nueva (**ridescreen.app**) para precios, guías y enlaces; y muchas correcciones de estabilidad.

❤️ Gracias a los primeros que han apoyado el proyecto en Ko-fi: Francisco Javier S., José Antonio R., Raul y tres donantes anónimos. Gracias a vosotros esto sigue adelante.

**Y todo lo de la v2.2 y la v2**: pantalla principal nueva, Aparcar con foto, gasolineras con precio, semáforos, ajuste de pantalla en directo, detector de caídas, mapa de calor, récords, tu año en moto, libreta de mantenimiento y brújula hacia tu moto en el reloj.

### Instalación en el móvil
1. Descarga **`RideScreen-AA-v2.3.apk`** (abajo, en *Assets*).
2. Si vienes de la **v1/v1.1/v2/v2.2** o de la versión del grupo (zontes-1/2), se instala **encima** sin perder nada. Si tienes la OpenCfMoto **oficial**, desinstálala primero: están firmadas con claves distintas.
3. Si sale el aviso de **Play Protect** ("app no segura" o "bloqueada"), es porque Google aún no conoce la app: **Más detalles → Instalar de todas formas**.
4. Las próximas versiones te las ofrecerá la propia app.

### Instalación en el reloj (opcional, Wear OS)
Desde la v2.2 el reloj se actualiza **desde el móvil**: **❓ Ayuda → ⌚ Actualizar la app del reloj** descarga **`RideScreen-AA-v2.3-WearOS-reloj.apk`** y se la envía por Bluetooth (tarda un par de minutos). Si es tu primera vez, la instalación inicial se hace por depuración inalámbrica: paso a paso en la [guía del reloj](https://ridescreen.app/reloj/).

### Si algo falla
Mira la [guía del móvil](https://ridescreen.app/guia/) (tiene los mensajes de error más habituales y qué hacer). Si sigue, en la app **❓ Ayuda → Compartir registros** y pásalo por el grupo contando qué ha pasado. Los registros ocultan números de serie y contraseñas.

☕ RideScreen AA es gratis y sin anuncios. Si te resulta útil y quieres que siga creciendo (Play Store, más motos, más funciones), puedes [invitarme a un café en Ko-fi](https://ko-fi.com/mgb1982).
