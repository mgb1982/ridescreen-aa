"""Synthetic map of the example area (Eixample-style chamfered grid, two avenues, park, sea).

No map tiles are fetched: the same drawing is used on every capture, so phone, watch and share card show
the same streets and the same route. Coordinates are in a 1000×1000 box.
"""
import math

# The headline route (Sants → Zona Franca), roughly NW → S, with speed in km/h per point.
ROUTE = [
    (270, 250, 8), (281, 272, 22), (297, 297, 35), (318, 326, 46), (343, 354, 55), (373, 383, 64), (403, 411, 71), (434, 440, 66), (465, 466, 52), (491, 489, 31), (525, 512, 18), (570, 531, 26), (618, 554, 41), (660, 579, 50), (692, 605, 47), (718, 633, 38), (740, 658, 29), (760, 680, 14),
]
ROUTE_SHORT = [(270, 250, 10), (300, 262, 24), (338, 276, 31), (372, 296, 28), (396, 318, 22), (410, 342, 8)]

# Heat ramp of the real app (HeatGrid.color): blue → green → yellow → red.
RAMP = [(59, 130, 246), (34, 197, 94), (250, 204, 21), (239, 68, 68)]


def ramp(x):
    x = max(0.0, min(1.0, x)) * (len(RAMP) - 1)
    i = min(int(math.floor(x)), len(RAMP) - 2)
    f = x - i
    a, b = RAMP[i], RAMP[i + 1]
    return "rgb(%d,%d,%d)" % tuple(int(a[k] + (b[k] - a[k]) * f) for k in range(3))


def _grid(step, stroke, width, angle=45):
    lines = []
    for k in range(-1600, 2600, step):
        lines.append(f'<line x1="{k}" y1="-800" x2="{k}" y2="1800" stroke="{stroke}" stroke-width="{width}"/>')
        lines.append(f'<line x1="-800" y1="{k}" x2="1800" y2="{k}" stroke="{stroke}" stroke-width="{width}"/>')
    return f'<g transform="rotate({angle} 500 500)">{"".join(lines)}</g>'


def _pts(points, bbox):
    x0, y0, x1, y1 = bbox
    return [((x - x0) * 1000 / (x1 - x0), (y - y0) * 1000 / (y1 - y0)) for x, y, *_ in points]


def fit_bbox(pts, aspect, pad=0.22, min_span=260):
    """Frame the route with some margin, at the target aspect (w/h)."""
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    w = max(max(xs) - min(xs), min_span) * (1 + 2 * pad)
    h = max(max(ys) - min(ys), min_span * 0.6) * (1 + 2 * pad)
    if w / h < aspect:
        w = h * aspect
    else:
        h = w / aspect
    return (cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)


def svg(width, height, style="light", route="main", bbox="auto", speed_colours=False,
        markers=True, line_w=None, watch=False, pad=0.22):
    """Return an <svg> of the area cropped to bbox (x0,y0,x1,y1 in the 1000 box), scaled to width×height."""
    pts = ROUTE if route == "main" else ROUTE_SHORT
    if bbox == "auto":
        bbox = fit_bbox(pts, width / height, pad)
    x0, y0, x1, y1 = bbox
    vb = f"{x0} {y0} {x1 - x0} {y1 - y0}"
    if style == "light":
        # OSM-like palette, then dimmed like the app does (saturation .25, ×0.82).
        base = (f'<rect x="-500" y="-500" width="2000" height="2000" fill="#F2EFE9"/>'
                f'{_grid(46, "#FFFFFF", 9)}'
                f'<ellipse cx="190" cy="420" rx="85" ry="120" fill="#CDEBC5"/>'
                f'<ellipse cx="620" cy="380" rx="95" ry="130" fill="#CDEBC5"/>'
                f'<circle cx="430" cy="110" r="60" fill="#CDEBC5"/>'
                f'<path d="M640 1000 L1000 560 L1000 1000 Z" fill="#AAD3DF"/>'
                f'{_grid(30, "#C7E3EC", 5).replace("<g ", "<g clip-path=\"url(#sea)\" ", 1)}'
                f'<line x1="-200" y1="520" x2="1200" y2="250" stroke="#F7D78E" stroke-width="16"/>'
                f'<line x1="140" y1="-100" x2="300" y2="1100" stroke="#F7D78E" stroke-width="14"/>'
                f'<line x1="-100" y1="900" x2="520" y2="610" stroke="#F7D78E" stroke-width="12"/>'
                f'<line x1="560" y1="-100" x2="760" y2="1100" stroke="#F7D78E" stroke-width="12"/>')
        defs = '<defs><clipPath id="sea"><path d="M640 1000 L1000 560 L1000 1000 Z"/></clipPath></defs>'
        if watch:  # WearTrips.kt: plain map, white casing, #E8541C route
            base = f'{defs}{base}'
            casing, single = "#FFFFFF", "#E8541C"
        else:      # TripMapActivity.dimBaseMap(): saturation .25, ×0.82
            base = f'{defs}<g style="filter:saturate(.25) brightness(.82)">{base}</g>'
            casing, single = "#14201A", None
    else:  # "dark": thumbnails in the trips list
        base = (f'<rect x="-500" y="-500" width="2000" height="2000" fill="#2B302C"/>'
                f'{_grid(56, "#4A4F49", 7)}'
                f'<ellipse cx="190" cy="420" rx="85" ry="60" fill="#2F5A3A" opacity=".75"/>'
                f'<ellipse cx="620" cy="300" rx="95" ry="45" fill="#2F5A3A" opacity=".75"/>'
                f'<path d="M620 1000 L1000 600 L1000 1000 Z" fill="#1F3550"/>'
                f'<line x1="-200" y1="520" x2="1200" y2="250" stroke="#686D66" stroke-width="16"/>'
                f'<line x1="140" y1="-100" x2="300" y2="1100" stroke="#686D66" stroke-width="14"/>'
                f'<line x1="-100" y1="900" x2="520" y2="610" stroke="#686D66" stroke-width="12"/>')
        casing, single = None, "#D1A955"
    scale = (x1 - x0) / width
    lw = (line_w or (5 if style == "light" else 3)) * scale
    path = " ".join(f"{x},{y}" for x, y, *_ in pts)
    route_svg = ""
    if casing:
        route_svg += (f'<polyline points="{path}" fill="none" stroke="{casing}" stroke-width="{lw + (2.4 if watch else 3.5) * scale}" '
                      f'stroke-linecap="round" stroke-linejoin="round"/>')
    if speed_colours:
        top = max(p[2] for p in pts)
        for a, b in zip(pts, pts[1:]):
            col = ramp(((a[2] + b[2]) / 2) / top)
            route_svg += (f'<line x1="{a[0]}" y1="{a[1]}" x2="{b[0]}" y2="{b[1]}" stroke="{col}" '
                          f'stroke-width="{lw}" stroke-linecap="round"/>')
    else:
        route_svg += (f'<polyline points="{path}" fill="none" stroke="{single or "#D1A955"}" stroke-width="{lw}" '
                      f'stroke-linecap="round" stroke-linejoin="round"/>')
    if markers:
        r = (6 if style == "light" else 5) * scale
        if watch:
            route_svg += (f'<circle cx="{pts[0][0]}" cy="{pts[0][1]}" r="{r}" fill="#16A34A" stroke="#fff" stroke-width="{1.6 * scale}"/>'
                          f'<circle cx="{pts[-1][0]}" cy="{pts[-1][1]}" r="{r}" fill="#E11D48" stroke="#fff" stroke-width="{1.6 * scale}"/>')
        elif style == "light":
            route_svg += (f'<circle cx="{pts[0][0]}" cy="{pts[0][1]}" r="{r}" fill="#22C55E" stroke="#fff" stroke-width="{1.6 * scale}"/>'
                          f'<circle cx="{pts[-1][0]}" cy="{pts[-1][1]}" r="{r}" fill="#EF4444" stroke="#fff" stroke-width="{1.6 * scale}"/>')
        else:
            route_svg += (f'<circle cx="{pts[0][0]}" cy="{pts[0][1]}" r="{r}" fill="#1A231D" stroke="#9FD3C7" stroke-width="{1.8 * scale}"/>'
                          f'<circle cx="{pts[-1][0]}" cy="{pts[-1][1]}" r="{r}" fill="#1A231D" stroke="#D1A955" stroke-width="{1.8 * scale}"/>')
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="{vb}" '
            f'preserveAspectRatio="xMidYMid slice" style="display:block">{base}{route_svg}</svg>')
