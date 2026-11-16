#!/usr/bin/env python3
"""Genera los SVG animados que usa el README (carpeta assets/).

Solo usa la biblioteca estándar de Python. Las animaciones son SMIL (<animate>,
<animateTransform>, <animateMotion>), que GitHub reproduce cuando el SVG se incluye
con <img>. No usan JavaScript ni fuentes externas, porque GitHub los bloquea.

Uso (desde la raíz del repositorio):
    python3 tools/generar_svg_readme.py
"""
import math
import random
from pathlib import Path
from xml.sax.saxutils import escape

OUT = Path(__file__).resolve().parent.parent / "assets"

FONT = "'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif"
MONO = "ui-monospace, SFMono-Regular, Menlo, Consolas, 'Liberation Mono', monospace"

GREEN_D = "#005828"
GREEN_M = "#0B7A3E"
ACCENT = "#00A85A"
SOFT = "#E1F2E7"
YELLOW = "#FEDC7A"
INK = "#2A1A22"
MUTED = "#5E7566"
AMBER = "#C58A00"
RAMP = ["#E8F5E9", "#C8E6C9", "#81C784", "#43A047", "#2E7D32", "#1B5E20"]
NO_DATA = "#E0E0E0"


def svg(width, height, body, defs="", title=""):
    t = f"<title>{escape(title)}</title>" if title else ""
    d = f"<defs>{defs}</defs>" if defs else ""
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}" role="img">{t}{d}{body}</svg>\n'
    )


def save(name, content):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / name).write_text(content, encoding="utf-8")
    print(f"{name:<18} {len(content) / 1024:6.1f} KB")


def wave(y, amp, fill, opacity, total, dur, height):
    """Onda senoidal de periodo 100 px que se desplaza 200 px en bucle sin saltos."""
    segs = "t50,0 " * (total // 50 - 1)
    d = f"M0,{y} q25,{-amp} 50,0 {segs}V{height} H0 Z"
    return (
        f'<path d="{d}" fill="{fill}" opacity="{opacity}">'
        f'<animateTransform attributeName="transform" type="translate" from="0 0" to="-200 0" '
        f'dur="{dur}s" repeatCount="indefinite"/></path>'
    )


# ----------------------------------------------------------------------------------------
def header():
    W, H = 1200, 340
    rng = random.Random(7)
    defs = (
        f'<linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">'
        f'<stop offset="0" stop-color="#002E14"/><stop offset="0.55" stop-color="{GREEN_D}"/>'
        f'<stop offset="1" stop-color="{GREEN_M}"/></linearGradient>'
    )
    body = [f'<rect width="{W}" height="{H}" fill="url(#bg)"/>']

    # Mosaico hexagonal: cada hexágono es un "municipio" que cambia de intensidad.
    r = 24
    dx, dy = 1.5 * r, math.sqrt(3) * r
    col = 0
    x = 700.0
    while x < W + r:
        y = (dy / 2 if col % 2 else 0) - dy / 2
        while y < H + dy:
            pts = " ".join(
                f"{x + (r - 2.5) * math.cos(math.radians(60 * i)):.1f},"
                f"{y + (r - 2.5) * math.sin(math.radians(60 * i)):.1f}"
                for i in range(6)
            )
            v = (x - 700) / 520 + 0.22 * math.sin(y / 55) + rng.uniform(-0.18, 0.18)
            color = RAMP[max(0, min(5, int(v * 6)))]
            fade = max(0.0, min(1.0, (x - 700) / 160))
            lo, hi = 0.30 * fade, 0.95 * fade
            begin = -rng.uniform(0, 5)
            body.append(
                f'<polygon points="{pts}" fill="{color}" opacity="{lo:.2f}">'
                f'<animate attributeName="opacity" values="{lo:.2f};{hi:.2f};{lo:.2f}" '
                f'dur="{rng.uniform(3.5, 6):.1f}s" begin="{begin:.1f}s" repeatCount="indefinite"/></polygon>'
            )
            y += dy
        x += dx
        col += 1

    # Título con entrada suave.
    body.append(
        f'<g><animate attributeName="opacity" from="0" to="1" dur="1.2s" fill="freeze"/>'
        f'<animateTransform attributeName="transform" type="translate" from="-40 0" to="0 0" '
        f'dur="1.2s" fill="freeze"/>'
        f'<text x="64" y="150" font-family="{FONT}" font-size="68" font-weight="800" fill="#FFFFFF">EVA Explorer</text>'
        f'<text x="66" y="196" font-family="{FONT}" font-size="21" fill="#CFE8D7">'
        f'Reto 10 · Datos abiertos de Colombia en un mapa interactivo</text></g>'
    )

    # Etiquetas de tecnología.
    x0 = 66
    for label in ["Kotlin", "Jetpack Compose", "MapLibre", "API SODA"]:
        w = len(label) * 10.5 + 34
        body.append(
            f'<rect x="{x0}" y="226" width="{w:.0f}" height="34" rx="17" fill="#FFFFFF" fill-opacity="0.14" '
            f'stroke="#FFFFFF" stroke-opacity="0.45"/>'
            f'<text x="{x0 + w / 2:.0f}" y="249" text-anchor="middle" font-family="{FONT}" font-size="16" '
            f'font-weight="600" fill="#FFFFFF">{escape(label)}</text>'
        )
        x0 += w + 12

    # Alfiler que flota.
    pin = (
        "M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5"
        "s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"
    )
    body.append(
        f'<g transform="translate(588 70) scale(3.4)"><path d="{pin}" fill="{YELLOW}">'
        f'<animateTransform attributeName="transform" type="translate" values="0 0;0 -4;0 0" dur="2.4s" '
        f'repeatCount="indefinite"/></path></g>'
    )

    body.append(wave(300, 12, "#FFFFFF", 0.10, W + 200, 7, H))
    body.append(wave(312, 9, "#FFFFFF", 0.14, W + 200, 5, H))
    body.append(wave(326, 6, "#FFFFFF", 0.18, W + 200, 9, H))
    save("header.svg", svg(W, H, "".join(body), defs, "EVA Explorer · Reto 10"))


# ----------------------------------------------------------------------------------------
def typing():
    W, H = 900, 72
    lines = [
        "Kotlin + Jetpack Compose + Material 3",
        "API SODA de datos.gov.co · JSON con org.json",
        "Mapa coroplético de 1.105 municipios",
        "Guía de estudio: de cero a un APK en CI",
        "Datos abiertos de Colombia en tu bolsillo",
    ]
    n, slot = len(lines), 3.8
    dur = n * slot
    cw, x0 = 12.4, 58
    defs, body = [], [
        f'<rect width="{W}" height="{H}" rx="14" fill="#0F2A1C"/>',
        f'<text x="26" y="45" font-family="{MONO}" font-size="22" font-weight="700" fill="{ACCENT}">$</text>',
    ]
    for i, text in enumerate(lines):
        width = len(text) * cw + 4
        a, b, c, d = i / n + 0.0001, (i + 0.55) / n, (i + 0.9) / n, (i + 0.97) / n
        kt = f"0;{a:.4f};{b:.4f};{c:.4f};{d:.4f};1"
        defs.append(
            f'<clipPath id="c{i}"><rect x="{x0}" y="14" height="44" width="0">'
            f'<animate attributeName="width" values="0;0;{width:.0f};{width:.0f};0;0" keyTimes="{kt}" '
            f'dur="{dur}s" repeatCount="indefinite"/></rect></clipPath>'
        )
        body.append(
            f'<text x="{x0}" y="45" clip-path="url(#c{i})" font-family="{MONO}" font-size="22" '
            f'fill="#9BE7B8" textLength="{width - 4:.0f}" lengthAdjust="spacing">{escape(text)}</text>'
        )
        body.append(
            f'<rect y="19" width="10" height="28" fill="{YELLOW}" opacity="0">'
            f'<animate attributeName="x" values="{x0};{x0};{x0 + width:.0f};{x0 + width:.0f};{x0};{x0}" '
            f'keyTimes="{kt}" dur="{dur}s" repeatCount="indefinite"/>'
            f'<animate attributeName="opacity" calcMode="discrete" values="0;1;0" '
            f'keyTimes="0;{a:.4f};{d:.4f}" dur="{dur}s" repeatCount="indefinite"/></rect>'
        )
    save("typing.svg", svg(W, H, "".join(body), "".join(defs), "Animación de escritura con las características del proyecto"))


# ----------------------------------------------------------------------------------------
def divider():
    W, H = 1200, 18
    defs = (
        f'<linearGradient id="g" x1="0" x2="1"><stop offset="0" stop-color="{ACCENT}" stop-opacity="0"/>'
        f'<stop offset="0.25" stop-color="{ACCENT}"/><stop offset="0.5" stop-color="{YELLOW}"/>'
        f'<stop offset="0.75" stop-color="{ACCENT}"/><stop offset="1" stop-color="{ACCENT}" stop-opacity="0"/></linearGradient>'
        f'<radialGradient id="glow"><stop offset="0" stop-color="#FFFFFF"/>'
        f'<stop offset="0.35" stop-color="{YELLOW}" stop-opacity="0.9"/><stop offset="1" stop-color="{YELLOW}" stop-opacity="0"/></radialGradient>'
    )
    body = (
        f'<rect x="0" y="7" width="{W}" height="4" rx="2" fill="url(#g)"/>'
        f'<circle cy="9" r="9" fill="url(#glow)"><animate attributeName="cx" values="-10;{W + 10}" dur="5s" repeatCount="indefinite"/></circle>'
    )
    save("divider.svg", svg(W, H, body, defs))


# ----------------------------------------------------------------------------------------
def orbit():
    S = 420
    cx = cy = S / 2
    defs = (
        f'<radialGradient id="core"><stop offset="0" stop-color="{ACCENT}"/><stop offset="1" stop-color="{GREEN_D}"/></radialGradient>'
    )
    body = [f'<rect width="{S}" height="{S}" rx="24" fill="#F7FBF8" stroke="#CFE8D7"/>']
    rings = [
        (100, 26, 1, ["Kotlin", "Compose", "Material 3", "OkHttp", "org.json"]),
        (160, 44, 0, ["MapLibre", "SODA API", "GeoJSON", "Gradle", "Actions"]),
    ]
    for radius, dur, sweep, _ in rings:
        body.append(
            f'<circle cx="{cx}" cy="{cy}" r="{radius}" fill="none" stroke="{ACCENT}" stroke-opacity="0.45" '
            f'stroke-width="1.5" stroke-dasharray="4 6"><animate attributeName="stroke-dashoffset" '
            f'from="0" to="{-20 if sweep else 20}" dur="2s" repeatCount="indefinite"/></circle>'
        )
    body.append(
        f'<circle cx="{cx}" cy="{cy}" r="40" fill="url(#core)"><animate attributeName="r" values="40;44;40" dur="3s" repeatCount="indefinite"/></circle>'
        f'<text x="{cx}" y="{cy - 2}" text-anchor="middle" font-family="{FONT}" font-size="17" font-weight="800" fill="#FFFFFF">EVA</text>'
        f'<text x="{cx}" y="{cy + 16}" text-anchor="middle" font-family="{FONT}" font-size="12" fill="#CFE8D7">Explorer</text>'
    )
    for radius, dur, sweep, labels in rings:
        path = (
            f"M{cx + radius},{cy} A{radius},{radius} 0 1,{sweep} {cx - radius},{cy} "
            f"A{radius},{radius} 0 1,{sweep} {cx + radius},{cy}"
        )
        for k, label in enumerate(labels):
            w = len(label) * 8.2 + 26
            body.append(
                f'<g><animateMotion dur="{dur}s" begin="{-dur * k / len(labels):.1f}s" repeatCount="indefinite" path="{path}"/>'
                f'<rect x="{-w / 2:.0f}" y="-13" width="{w:.0f}" height="26" rx="13" fill="{SOFT}" stroke="{ACCENT}"/>'
                f'<text y="5" text-anchor="middle" font-family="{FONT}" font-size="13" font-weight="700" '
                f'fill="{GREEN_D}">{escape(label)}</text></g>'
            )
    save("orbit.svg", svg(S, S, "".join(body), defs, "Tecnologías del proyecto en órbita"))


# ----------------------------------------------------------------------------------------
def flow():
    W, H = 1200, 450
    defs = (
        f'<marker id="ahg" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7" orient="auto">'
        f'<path d="M0,0 L10,5 L0,10 z" fill="{GREEN_M}"/></marker>'
        f'<marker id="aha" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7" orient="auto">'
        f'<path d="M0,0 L10,5 L0,10 z" fill="{AMBER}"/></marker>'
    )
    body = [f'<rect width="{W}" height="{H}" rx="20" fill="#F7FBF8" stroke="#CFE8D7"/>']

    def box(x, y, title, sub, color):
        body.append(
            f'<rect x="{x}" y="{y}" width="210" height="90" rx="14" fill="#FFFFFF" stroke="{color}" stroke-width="2.5"/>'
            f'<text x="{x + 105}" y="{y + 38}" text-anchor="middle" font-family="{FONT}" font-size="19" '
            f'font-weight="700" fill="{INK}">{escape(title)}</text>'
            f'<text x="{x + 105}" y="{y + 62}" text-anchor="middle" font-family="{FONT}" font-size="13" '
            f'fill="{MUTED}">{escape(sub)}</text>'
        )

    def arrow(x1, x2, y, color, marker, label, label_dy, begin):
        d = f"M{x1},{y} H{x2}"
        body.append(
            f'<path d="{d}" stroke="{color}" stroke-width="2.5" fill="none" stroke-dasharray="7 6" marker-end="url(#{marker})">'
            f'<animate attributeName="stroke-dashoffset" from="0" to="{-26 if x2 > x1 else 26}" dur="1.2s" repeatCount="indefinite"/></path>'
            f'<circle r="4.5" fill="{color}"><animateMotion dur="2.2s" begin="{begin}s" repeatCount="indefinite" path="{d}"/></circle>'
            f'<text x="{(x1 + x2) / 2}" y="{y + label_dy}" text-anchor="middle" font-family="{FONT}" font-size="11.5" '
            f'fill="{color}">{escape(label)}</text>'
        )

    xs = [45, 345, 645, 945]
    body.append(f'<text x="45" y="40" font-family="{FONT}" font-size="17" font-weight="700" fill="{GREEN_D}">A · Consulta de datos en tiempo real</text>')
    for x, (t, s) in zip(xs, [("App Android", "Jetpack Compose"), ("AppViewModel", "StateFlow · UiState"),
                              ("SodaClient", "OkHttp + org.json"), ("datos.gov.co", "API SODA v3 / v2")]):
        box(x, 62, t, s, GREEN_M)
    ida = ["eventos", "consulta", "HTTPS"]
    vuelta = ["estado", "filas", "JSON"]
    for i, x in enumerate(xs[:-1]):
        arrow(x + 214, x + 300 - 5, 94, GREEN_M, "ahg", ida[i], -9, 0.4 * i)
        arrow(x + 300 - 5, x + 214, 124, AMBER, "aha", vuelta[i], 17, 1.1 + 0.4 * i)

    # Valores hacia el mapa (codo).
    elbow = "M450,152 V222 H1050 V292"
    body.append(
        f'<path d="{elbow}" stroke="{ACCENT}" stroke-width="2.5" fill="none" stroke-dasharray="7 6" marker-end="url(#ahg)">'
        f'<animate attributeName="stroke-dashoffset" from="0" to="-26" dur="1.2s" repeatCount="indefinite"/></path>'
        f'<circle r="5" fill="{ACCENT}"><animateMotion dur="4s" repeatCount="indefinite" path="{elbow}"/></circle>'
        f'<text x="750" y="213" text-anchor="middle" font-family="{FONT}" font-size="13" font-weight="600" '
        f'fill="{GREEN_D}">valor por municipio, pintado según su código DANE</text>'
    )

    body.append(f'<text x="45" y="268" font-family="{FONT}" font-size="17" font-weight="700" fill="{GREEN_D}">B · Capa de municipios (se prepara una sola vez)</text>')
    for x, (t, s) in zip(xs, [("Shapefile · 78 MB", "Git LFS · MAGNA-SIRGAS"), ("shp_a_geojson.py", "reproyecta y simplifica"),
                              ("municipios.geojson", "2,4 MB · 1.105 polígonos"), ("MapLibre", "FillLayer + expresión match")]):
        box(x, 296, t, s, ACCENT)
    for i, x in enumerate(xs[:-1]):
        arrow(x + 214, x + 300 - 5, 341, ACCENT, "ahg", ["lee", "escribe", "se carga en"][i], -9, 0.5 * i)

    body.append(
        f'<text x="{W / 2}" y="425" text-anchor="middle" font-family="{FONT}" font-size="13" fill="{MUTED}">'
        f'El APK solo lleva el GeoJSON liviano. El shapefile original se queda en el repositorio.</text>'
    )
    save("flow.svg", svg(W, H, "".join(body), defs, "Arquitectura y flujo de datos de EVA Explorer"))


# ----------------------------------------------------------------------------------------
def ruta():
    W, H = 1200, 290
    steps = [("Reto", "Elegir el dataset"), ("API", "SODA · SoQL · token"), ("Proyecto", "Gradle + Compose"),
             ("Datos", "Modelos + org.json"), ("Mapa", "Shapefile a GeoJSON"), ("Estado", "ViewModel + Flow"),
             ("Interfaz", "Tema y pantallas"), ("CI", "Actions genera el APK")]
    xs = [95 + i * 144.3 for i in range(8)]
    ys = [112 + 20 * math.sin(i * 1.15) for i in range(8)]
    d = f"M{xs[0]:.1f},{ys[0]:.1f}"
    for i in range(1, 8):
        mx = (xs[i - 1] + xs[i]) / 2
        d += f" C{mx:.1f},{ys[i - 1]:.1f} {mx:.1f},{ys[i]:.1f} {xs[i]:.1f},{ys[i]:.1f}"
    dur = 12
    body = [
        f'<rect width="{W}" height="{H}" rx="20" fill="#F7FBF8" stroke="#CFE8D7"/>',
        f'<text x="{W / 2}" y="40" text-anchor="middle" font-family="{FONT}" font-size="19" font-weight="700" fill="{GREEN_D}">Ruta de aprendizaje: 8 pasos, de la idea al APK</text>',
        f'<path d="{d}" stroke="#D5E8DB" stroke-width="7" fill="none" stroke-linecap="round"/>',
        f'<path d="{d}" pathLength="100" stroke="{ACCENT}" stroke-width="7" fill="none" stroke-linecap="round" stroke-dasharray="100">'
        f'<animate attributeName="stroke-dashoffset" values="100;0;0" keyTimes="0;0.8;1" dur="{dur}s" repeatCount="indefinite"/></path>',
    ]
    for i, (x, y, (t, s)) in enumerate(zip(xs, ys, steps)):
        ti = 0.8 * i / 7 + 0.0001
        kt = f"0;{ti:.4f};1"
        body.append(
            f'<circle cx="{x:.1f}" cy="{y:.1f}" r="26" fill="#FFFFFF" stroke="{ACCENT}" stroke-width="3">'
            f'<animate attributeName="fill" calcMode="discrete" values="#FFFFFF;{ACCENT};#FFFFFF" keyTimes="0;{ti:.4f};0.97" dur="{dur}s" repeatCount="indefinite"/></circle>'
            f'<text x="{x:.1f}" y="{y + 7:.1f}" text-anchor="middle" font-family="{FONT}" font-size="21" font-weight="800" fill="{GREEN_D}">{i}'
            f'<animate attributeName="fill" calcMode="discrete" values="{GREEN_D};#FFFFFF;{GREEN_D}" keyTimes="0;{ti:.4f};0.97" dur="{dur}s" repeatCount="indefinite"/></text>'
            f'<text x="{x:.1f}" y="205" text-anchor="middle" font-family="{FONT}" font-size="16" font-weight="700" fill="{INK}">{escape(t)}</text>'
            f'<text x="{x:.1f}" y="226" text-anchor="middle" font-family="{FONT}" font-size="12" fill="{MUTED}">{escape(s)}</text>'
        )
    save("ruta.svg", svg(W, H, "".join(body), "", "Ruta de aprendizaje en 8 pasos"))


# ----------------------------------------------------------------------------------------
def choropleth():
    W, H = 1000, 430
    rng = random.Random(11)
    cols, rows, size, gap = 12, 7, 40, 4
    n = cols * rows
    values = [round(math.exp(rng.gauss(3.2, 1.0))) for _ in range(n)]
    ordered = sorted(values)
    k = len(RAMP)
    thresholds = sorted({ordered[(i * (n - 1)) // k] for i in range(1, k)})
    classes = len(thresholds) + 1
    color_of = lambda c: RAMP[round(c * (k - 1) / (classes - 1))] if classes > 1 else RAMP[-1]
    cls = [sum(1 for t in thresholds if v >= t) for v in values]
    rank = {idx: r for r, idx in enumerate(sorted(range(n), key=lambda i: values[i]))}
    dur = 11

    body = [
        f'<rect width="{W}" height="{H}" rx="20" fill="#F7FBF8" stroke="#CFE8D7"/>',
        f'<text x="40" y="42" font-family="{FONT}" font-size="19" font-weight="700" fill="{GREEN_D}">Cómo se colorea el mapa: clasificación en cuantiles</text>',
        f'<text x="40" y="64" font-family="{FONT}" font-size="13" fill="{MUTED}">Los valores se ordenan y se reparten en clases con casi la misma cantidad de municipios.</text>',
    ]
    for i in range(n):
        r_, c_ = divmod(i, cols)
        x, y = 40 + c_ * (size + gap), 84 + r_ * (size + gap)
        s = 0.08 + 0.55 * rank[i] / (n - 1)
        kt = f"0;{s:.4f};{s + 0.05:.4f};0.93;1"
        col = color_of(cls[i])
        dark = cls[i] >= classes - 2
        body.append(
            f'<rect x="{x}" y="{y}" width="{size}" height="{size}" rx="6" fill="{NO_DATA}">'
            f'<animate attributeName="fill" values="{NO_DATA};{NO_DATA};{col};{col};{NO_DATA}" keyTimes="{kt}" dur="{dur}s" repeatCount="indefinite"/></rect>'
            f'<text x="{x + size / 2}" y="{y + size / 2 + 4}" text-anchor="middle" font-family="{FONT}" font-size="11" font-weight="600" fill="#333333">{values[i]}'
            f'<animate attributeName="fill" calcMode="discrete" values="#333333;{"#FFFFFF" if dark else "#333333"};#333333" '
            f'keyTimes="0;{s + 0.05:.4f};0.93" dur="{dur}s" repeatCount="indefinite"/></text>'
        )
    lx = 640
    body.append(f'<text x="{lx}" y="104" font-family="{FONT}" font-size="16" font-weight="700" fill="{INK}">Clases</text>')
    for c in range(classes):
        y = 122 + c * 36
        count = sum(1 for v in cls if v == c)
        name = "valores más bajos" if c == 0 else "valores más altos" if c == classes - 1 else "valores intermedios"
        body.append(
            f'<rect x="{lx}" y="{y}" width="28" height="26" rx="6" fill="{color_of(c)}" stroke="#B9D4C1"/>'
            f'<text x="{lx + 40}" y="{y + 12}" font-family="{FONT}" font-size="14" font-weight="600" fill="{INK}">Clase {c + 1} · {name}</text>'
            f'<text x="{lx + 40}" y="{y + 26}" font-family="{FONT}" font-size="12" fill="{MUTED}">{count} municipios</text>'
        )
    y = 122 + classes * 36
    body.append(
        f'<rect x="{lx}" y="{y}" width="28" height="26" rx="6" fill="{NO_DATA}" stroke="#B9D4C1"/>'
        f'<text x="{lx + 40}" y="{y + 12}" font-family="{FONT}" font-size="14" font-weight="600" fill="{INK}">Sin dato</text>'
        f'<text x="{lx + 40}" y="{y + 26}" font-family="{FONT}" font-size="12" fill="{MUTED}">el municipio no aparece en la consulta</text>'
    )
    body.append(
        f'<text x="40" y="{H - 22}" font-family="{FONT}" font-size="12" fill="{MUTED}">Ejemplo con 84 valores sintéticos. En la app, cada celda es un municipio y su valor viene de la API.</text>'
    )
    save("choropleth.svg", svg(W, H, "".join(body), "", "Clasificación en cuantiles de los valores por municipio"))


# ----------------------------------------------------------------------------------------
def footer():
    W, H = 1200, 150
    defs = (
        f'<linearGradient id="fb" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#002E14"/>'
        f'<stop offset="1" stop-color="{GREEN_M}"/></linearGradient>'
    )
    body = [
        f'<rect width="{W}" height="{H}" fill="url(#fb)"/>',
        wave(34, 12, "#FFFFFF", 0.10, W + 200, 8, H),
        wave(48, 9, "#FFFFFF", 0.13, W + 200, 6, H),
        wave(62, 7, "#FFFFFF", 0.16, W + 200, 10, H),
        f'<text x="{W / 2}" y="100" text-anchor="middle" font-family="{FONT}" font-size="20" font-weight="700" fill="#FFFFFF">'
        f'Reto 10 · Desarrollo de Aplicaciones para Dispositivos Móviles</text>',
        f'<text x="{W / 2}" y="126" text-anchor="middle" font-family="{FONT}" font-size="14" fill="#CFE8D7">'
        f'Universidad Nacional de Colombia · Datos abiertos de Colombia</text>',
    ]
    save("footer.svg", svg(W, H, "".join(body), defs))


if __name__ == "__main__":
    header()
    typing()
    divider()
    orbit()
    flow()
    ruta()
    choropleth()
    footer()
