"""Widget «Controles de la moto» dibujado como ControlWidget.kt (2cbc824): cabecera con foto (61 %), nombre en
Archivo Black dorado, línea de estado, y 5 botones: círculo del color de la función al 26 % con el icono de
línea del mismo color y la etiqueta debajo; tarjeta #243028 con borde dorado y radio 22 dp. `k` = px por dp."""
from lib.res import esc, icon

ACC = {"connect": "#D1A955", "unlock": "#5FD07A", "lock": "#5B8DEF", "seat": "#4FB6A6", "fuel": "#E5774E"}
BTN = [("connect", "ic_line_power", "widget_connect_label"), ("unlock", "ic_line_unlock", "pke_short_unlock"),
       ("lock", "ic_line_lock", "pke_short_lock"), ("seat", "ic_line_seat", "pke_short_seat"),
       ("fuel", "ic_line_fuel", "pke_short_fuel")]


def _rgba(hexc, a):
    h = hexc.lstrip("#")
    return f"rgba({int(h[0:2], 16)},{int(h[2:4], 16)},{int(h[4:6], 16)},{a})"


def widget(s, photo, lang, w_dp=330, h_dp=184, k=2.8, compact=False):
    w, h = w_dp * k, h_dp * k
    band = h if compact else h - int(h * 0.61)
    bw = w / 5
    lab_px = 0 if compact else max(8 * k, min(11 * k, band * 0.20))
    gap = 0 if compact else 3 * k
    d = min(band * 0.80, bw * 0.72) if compact else min(band * 0.58, bw * 0.70)
    top = (band - (d + gap + lab_px * 1.15)) / 2
    btns = ""
    for key, ic, lab in BTN:
        a = ACC[key]
        txt = s(lab)
        # ControlWidget.button: shrinks the label 0.5 dp at a time (min 7 dp) until it fits w − 2 dp.
        fit = lab_px
        while fit > 7 * k and len(txt) * fit * 0.56 > bw - 2 * k:
            fit -= 0.5 * k
        label = "" if compact else (f'<div style="font-family:Archivo;font-weight:500;font-size:{fit:.1f}px;color:#F1F4EC;'
                                    f'margin-top:{gap:.1f}px;white-space:nowrap;line-height:1.15">{esc(txt)}</div>')
        btns += (f'<div style="width:{bw:.1f}px;height:{band:.1f}px;display:flex;flex-direction:column;align-items:center;'
                 f'padding-top:{top:.1f}px"><div style="width:{d:.1f}px;height:{d:.1f}px;border-radius:50%;'
                 f'background:{_rgba(a, .26)};display:flex;align-items:center;justify-content:center">'
                 f'{icon(ic, a, round(d * .58))}</div>{label}</div>')
    volts = ("12.6" if lang in ("en", "th") else "12,6") + " V"
    head = "" if compact else (
        f'<div style="position:relative;height:{int(h * .61)}px;background:url({photo}) center 10%/cover">'
        f'<div style="position:absolute;inset:0;background:linear-gradient(180deg,rgba(10,14,12,0),rgba(10,14,12,.92))"></div>'
        f'<div style="position:absolute;left:{14 * k:.0f}px;bottom:{22 * k:.0f}px;font-family:ArchivoBlack;font-size:{21 * k:.0f}px;'
        f'color:#D1A955;line-height:1">Zontes 125X</div>'
        f'<div style="position:absolute;left:{14 * k:.0f}px;bottom:{5 * k:.0f}px;display:flex;gap:{3 * k:.0f}px;align-items:center;'
        f'font-family:Archivo;font-size:{11 * k:.0f}px;color:#AEBAAA">{icon("ic_line_lock", "#AEBAAA", round(14 * k))}'
        f'{esc(s("widget_state_locked"))} · {volts} · 18:42</div></div>')
    return (f'<div style="width:{w:.0f}px;height:{h:.0f}px;border-radius:{22 * k:.0f}px;border:{k:.1f}px solid #D1A955;'
            f'background:#243028;overflow:hidden;display:flex;flex-direction:column">{head}'
            f'<div style="display:flex">{btns}</div></div>')
