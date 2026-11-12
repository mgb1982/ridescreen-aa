"""Feature collage for Zontes 125X / 125V, rendered by the official capture generator."""
from lib.base import root_css

W, H = 1080, 1350


def card(lang):
    if lang != "es":
        raise ValueError("This launch card is Spanish-only")
    css = """
    *{box-sizing:border-box}
    body{margin:0;background:#101510;color:#F4F5F0;font-family:'Archivo',sans-serif}
    .page{width:1080px;height:1350px;position:relative;overflow:hidden;padding:42px 48px;background:
      radial-gradient(ellipse at 50% 0%,#35452D 0%,#182019 42%,#101510 100%)}
    .brand{display:flex;align-items:center;gap:18px;font-size:58px;font-weight:800;letter-spacing:-2px}
    .slashes{color:#C7F000;font-size:68px;font-weight:800;letter-spacing:-12px;margin-right:12px}
    .models{margin-top:18px;background:#C7F000;color:#101510;border-radius:15px;padding:12px 24px;
      font-size:39px;font-weight:800;letter-spacing:1px;text-align:center}
    .sub{font-size:30px;text-align:center;margin:16px 0 26px;color:#E3E9DD}
    .hero{height:420px;display:grid;grid-template-columns:1fr 1fr;gap:22px}
    .bike{border:2px solid #71805E;border-radius:26px;background:linear-gradient(145deg,#34412E,#182019);
      padding:22px;position:relative;overflow:hidden}
    .bike .tag{font-size:24px;color:#C7F000;font-weight:800;letter-spacing:2px}
    .bike .sil{height:245px;display:flex;align-items:center;justify-content:center}
    .bike svg{width:100%;height:100%}
    .bike .caption{font-size:21px;color:#E0E7D9;text-align:center}
    .features{display:grid;grid-template-columns:1fr 1fr;gap:18px;margin-top:22px}
    .feature{min-height:170px;border:1.5px solid #71805E;border-radius:22px;background:#202A21;padding:18px 20px;
      display:flex;gap:18px;align-items:center}
    .ico{width:70px;height:70px;flex:none;border-radius:18px;background:#C7F000;color:#172014;
      display:flex;align-items:center;justify-content:center;font-size:39px;font-weight:800}
    .feature h3{font-size:25px;margin:0 0 7px;color:#C7F000}
    .feature p{font-size:19px;line-height:1.28;margin:0;color:#E7EDE3}
    .foot{margin-top:24px;background:#C7F000;color:#11180E;border-radius:18px;padding:17px 20px;text-align:center;
      font-size:29px;font-weight:800;letter-spacing:1px}
    .note{text-align:center;font-size:17px;color:#C4CCBE;margin-top:12px}
    .mini{display:flex;gap:10px;margin-top:10px}
    .chip{border:1px solid #708064;border-radius:999px;padding:6px 12px;font-size:16px;color:#DCE5D5}
    """
    bike_svg = """
    <svg viewBox="0 0 440 250" xmlns="http://www.w3.org/2000/svg">
      <ellipse cx="218" cy="218" rx="190" ry="13" fill="#0B100B" opacity=".6"/>
      <circle cx="105" cy="184" r="52" fill="#111713" stroke="#A6B09B" stroke-width="8"/>
      <circle cx="105" cy="184" r="25" fill="#39443A" stroke="#A6B09B" stroke-width="5"/>
      <circle cx="342" cy="184" r="52" fill="#111713" stroke="#A6B09B" stroke-width="8"/>
      <circle cx="342" cy="184" r="25" fill="#39443A" stroke="#A6B09B" stroke-width="5"/>
      <path d="M105 180 L155 111 L267 112 L342 180 L285 180 L241 139 L181 145 L150 180Z" fill="#879B36" stroke="#C7F000" stroke-width="5"/>
      <path d="M154 112 L175 73 L265 72 L292 112 L252 129 L186 129Z" fill="#303B31" stroke="#B6C59B" stroke-width="4"/>
      <path d="M178 72 L190 35 L249 35 L266 73" fill="#172019" stroke="#C8D2C1" stroke-width="5"/>
      <path d="M184 35 L252 35 L243 18 L196 18Z" fill="#BFC8B9"/>
      <path d="M270 110 L308 91 L343 114 L322 128 L284 127Z" fill="#202B22" stroke="#A7B78A" stroke-width="4"/>
      <path d="M151 111 L124 91 L87 94" stroke="#AEB9A5" stroke-width="7" fill="none" stroke-linecap="round"/>
      <path d="M294 94 L326 68 L368 68" stroke="#AEB9A5" stroke-width="7" fill="none" stroke-linecap="round"/>
      <path d="M185 146 L221 146 L245 180 L181 180Z" fill="#202820"/>
      <path d="M159 106 L276 106" stroke="#C7F000" stroke-width="5"/>
    </svg>
    """
    body = f"""
    <div class="page">
      <div class="brand"><span class="slashes">//</span>RideScreen AA</div>
      <div class="models">ZONTES 125X + 125V</div>
      <div class="sub">Android Auto inalámbrico en la pantalla de tu moto</div>
      <div class="hero">
        <section class="bike"><div class="tag">125X</div><div class="sil">{bike_svg}</div>
          <div class="caption">Tu cuadro, conectado</div></section>
        <section class="bike"><div class="tag">125V</div><div class="sil">{bike_svg}</div>
          <div class="caption">Navegación sin cables</div></section>
      </div>
      <div class="features">
        <section class="feature"><div class="ico">⌁</div><div><h3>Android Auto</h3><p>Maps, Waze, música y llamadas en el cuadro.</p></div></section>
        <section class="feature"><div class="ico">↻</div><div><h3>Conexión automática</h3><p>Vuelve a conectar con tu moto sin repetir el proceso.</p></div></section>
        <section class="feature"><div class="ico">⌖</div><div><h3>Rutas y estadísticas</h3><p>Guarda viajes, recorridos, kilómetros y datos de conducción.</p></div></section>
        <section class="feature"><div class="ico">▣</div><div><h3>Aparcamiento y mantenimiento</h3><p>Recuerda dónde aparcaste y controla tareas de mantenimiento.</p></div></section>
        <section class="feature"><div class="ico">◉</div><div><h3>Wear OS</h3><p>Funciones complementarias desde tu reloj compatible.</p></div></section>
        <section class="feature"><div class="ico">＋</div><div><h3>Proyecto vivo</h3><p>Funciones nuevas, mejoras y soporte de la comunidad.</p></div></section>
      </div>
      <div class="foot">GRATIS · SIN PUBLICIDAD · ANDROID</div>
      <div class="note">Proyecto independiente, no oficial de Zontes. Compatibilidad según cuadro y configuración.</div>
    </div>
    """
    return f"<!doctype html><html><head><meta charset='utf-8'><style>{root_css()}{css}</style></head><body>{body}</body></html>"
