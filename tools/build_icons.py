#!/usr/bin/env python3
"""Build every app icon from one description of the mark.

The mark is a candlestick chart: seven candles, three red and four green,
climbing left to right on a black card with a green border. The geometry below
was measured from the reference artwork (a 2000px PNG) and is expressed on the
108-unit canvas Android uses for adaptive icons, so the same numbers drive the
SVG favicon, the raster PNGs and the vector drawables.

Outputs
  web/public/icons/icon.svg                 favicon; the full card
  web/public/icons/icon-192.png, icon-512.png   PWA icons; the full card,
                                            transparent outside the corners
  web/public/icons/icon-180.png             apple-touch-icon; iOS rounds the
                                            corners itself, so this one is
                                            opaque to the edge
  web/public/icons/icon-maskable-512.png    PWA maskable icon; no border and
                                            the candles kept inside the safe
                                            zone, because the launcher crops
  app/src/main/res/drawable/ic_launcher_foreground.xml
  app/src/main/res/drawable/ic_launcher_monochrome.xml

Needs Pillow. Run from the repository root.
"""
from PIL import Image, ImageDraw

CANVAS = 108
RADIUS = 24          # corner radius of the card, about 22% like iOS
BORDER = 0.9         # width of the green frame
RED, GREEN, GREEN_LAST = "#DF2947", "#33E16A", "#4BE676"
FRAME = "#42E074"
BG_TOP, BG_MID, BG_BOTTOM = "#151716", "#070A0A", "#000000"
BG_FLAT = "#0B0F0F"  # the Android launcher background, see colors.xml

# The reference is 2000 x 2065 px with the candles centred; 0.054 units per px.
_S = CANVAS / 2000
def _x(px): return round((px - 1000) * _S + 54, 2)
def _y(px): return round((px - 1032.5) * _S + 54, 2)

BODY_W = round(141 * _S, 2)   # 7.61
WICK_W = round(21 * _S, 2)    # 1.13
BODY_R = 1.0

# centre x, wick top, wick bottom, body top, body bottom, colour
CANDLES = [
    (_x(372),  _y(1240), _y(1552), _y(1326), _y(1468), RED),
    (_x(582),  _y(590),  _y(1685), _y(828),  _y(1466), GREEN),
    (_x(791),  _y(785),  _y(1406), _y(1014), _y(1313), RED),
    (_x(1000), _y(1081), _y(1619), _y(1210), _y(1494), RED),
    (_x(1207), _y(722),  _y(1495), _y(885),  _y(1350), GREEN),
    (_x(1415), _y(376),  _y(1311), _y(555),  _y(1148), GREEN),
    (_x(1626), _y(590),  _y(1124), _y(719),  _y(996),  GREEN_LAST),
]


def scaled(scale):
    """The candles shrunk towards the centre of the canvas."""
    c = CANVAS / 2
    out = []
    for cx, wt, wb, bt, bb, col in CANDLES:
        f = lambda v: round((v - c) * scale + c, 2)
        out.append((f(cx), f(wt), f(wb), f(bt), f(bb), col))
    return out, BODY_W * scale, WICK_W * scale, BODY_R * scale


def safe_scale(diameter):
    """Largest scale at which every candle stays inside the central circle."""
    c = CANVAS / 2
    far = 0.0
    for cx, wt, wb, bt, bb, _ in CANDLES:
        for x, y in ((cx - BODY_W / 2, bt), (cx + BODY_W / 2, bt),
                     (cx - BODY_W / 2, bb), (cx + BODY_W / 2, bb),
                     (cx - WICK_W / 2, wt), (cx + WICK_W / 2, wt),
                     (cx - WICK_W / 2, wb), (cx + WICK_W / 2, wb)):
            far = max(far, ((x - c) ** 2 + (y - c) ** 2) ** 0.5)
    return round(diameter / 2 / far, 3)


def rounded_rect_path(x, y, w, h, r):
    """SVG path data for a rectangle with rounded corners (arcs only, so it
    also parses as a vector drawable)."""
    r = min(r, w / 2, h / 2)
    f = lambda v: f"{v:.2f}".rstrip("0").rstrip(".")
    return (f"M{f(x + r)},{f(y)} H{f(x + w - r)} A{f(r)},{f(r)} 0 0 1 {f(x + w)},{f(y + r)}"
            f" V{f(y + h - r)} A{f(r)},{f(r)} 0 0 1 {f(x + w - r)},{f(y + h)}"
            f" H{f(x + r)} A{f(r)},{f(r)} 0 0 1 {f(x)},{f(y + h - r)}"
            f" V{f(y + r)} A{f(r)},{f(r)} 0 0 1 {f(x + r)},{f(y)} Z")


def candle_paths(scale=1.0):
    """(path data, colour) for every wick and body."""
    candles, bw, ww, br = scaled(scale)
    paths = []
    for cx, wt, wb, bt, bb, col in candles:
        paths.append((rounded_rect_path(cx - ww / 2, wt, ww, wb - wt, ww / 2), col))
        paths.append((rounded_rect_path(cx - bw / 2, bt, bw, bb - bt, br), col))
    return paths


# ---------------------------------------------------------------- SVG

def svg():
    inset = BORDER / 2
    body = "\n".join(f'  <path d="{d}" fill="{c}"/>' for d, c in candle_paths())
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {CANVAS} {CANVAS}" width="512" height="512">
  <title>Tickers</title>
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="{BG_TOP}"/>
      <stop offset="0.5" stop-color="{BG_MID}"/>
      <stop offset="1" stop-color="{BG_BOTTOM}"/>
    </linearGradient>
  </defs>
  <rect width="{CANVAS}" height="{CANVAS}" rx="{RADIUS}" fill="url(#bg)"/>
  <rect x="{inset}" y="{inset}" width="{CANVAS - BORDER}" height="{CANVAS - BORDER}" rx="{RADIUS - inset}"
        fill="none" stroke="{FRAME}" stroke-width="{BORDER}"/>
{body}
</svg>
'''


# ---------------------------------------------------------------- Android

VECTOR_HEAD = '''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108"{extra}>
'''


def foreground_xml(scale):
    paths = "\n".join(
        f'    <path android:fillColor="{c}" android:pathData="{d}" />'
        for d, c in candle_paths(scale))
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!--
  Generated by tools/build_icons.py; edit the geometry there, not here.

  Seven candles climbing left to right, the mark of the app. A launcher crops
  the central 72x72 of this 108x108 canvas and may mask it to a circle, so the
  candles are scaled to {scale} of the full card to stay inside the 66dp safe
  zone: the leftmost and rightmost candles are the ones that would be cut.
  The card's green frame cannot survive a mask, so it is left out here; the
  background is the flat @color/ic_launcher_background.
-->
''' + VECTOR_HEAD.format(extra="") + f'''
{paths}
</vector>
'''


def monochrome_xml(scale):
    paths = "\n".join(
        f'    <path android:fillColor="#FFFFFF" android:pathData="{d}" />'
        for d, _ in candle_paths(scale))
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!--
  Generated by tools/build_icons.py; edit the geometry there, not here.

  Themed icons are tinted a single colour. The candles keep their silhouette
  in one tone, so the monochrome variant is the same shapes without the
  red/green split.
-->
''' + VECTOR_HEAD.format(extra='\n    android:tint="#FFFFFF"') + f'''
{paths}
</vector>
'''


# ---------------------------------------------------------------- PNG

def _hex(c):
    return tuple(int(c[i:i + 2], 16) for i in (1, 3, 5))


def _gradient(size):
    top, mid, bottom = _hex(BG_TOP), _hex(BG_MID), _hex(BG_BOTTOM)
    img = Image.new("RGBA", (size, size))
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / (size - 1)
        a, b, u = (top, mid, t * 2) if t < 0.5 else (mid, bottom, (t - 0.5) * 2)
        col = tuple(round(a[i] + (b[i] - a[i]) * u) for i in range(3))
        d.line([(0, y), (size, y)], fill=col + (255,))
    return img


def png(size, *, opaque=False, maskable=False, supersample=8):
    """Render the mark at `size` px. Draws at `supersample` times the size and
    shrinks, because ImageDraw does not antialias."""
    big = size * supersample
    u = big / CANVAS   # px per canvas unit
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    if maskable:
        # Full bleed, flat: the launcher supplies the shape.
        draw.rectangle([0, 0, big, big], fill=_hex(BG_FLAT) + (255,))
        paths = scaled(safe_scale(CANVAS * 0.8))  # 80% is the maskable safe zone
        candles, bw, ww, br = paths
    else:
        if opaque:
            draw.rectangle([0, 0, big, big], fill=_hex(BG_FLAT) + (255,))
        card = Image.new("L", (big, big), 0)
        ImageDraw.Draw(card).rounded_rectangle([0, 0, big - 1, big - 1], radius=RADIUS * u, fill=255)
        img.paste(_gradient(big), (0, 0), card)
        draw = ImageDraw.Draw(img)
        inset = BORDER / 2 * u
        draw.rounded_rectangle([inset, inset, big - 1 - inset, big - 1 - inset],
                               radius=(RADIUS - BORDER / 2) * u,
                               outline=_hex(FRAME) + (255,), width=round(BORDER * u))
        candles, bw, ww, br = scaled(1.0)

    for cx, wt, wb, bt, bb, col in candles:
        c = _hex(col) + (255,)
        draw.rounded_rectangle([(cx - ww / 2) * u, wt * u, (cx + ww / 2) * u, wb * u],
                               radius=ww / 2 * u, fill=c)
        draw.rounded_rectangle([(cx - bw / 2) * u, bt * u, (cx + bw / 2) * u, bb * u],
                               radius=br * u, fill=c)
    return img.resize((size, size), Image.LANCZOS)


def main():
    import os
    web = "web/public/icons"
    res = "app/src/main/res/drawable"
    os.makedirs(web, exist_ok=True)

    with open(f"{web}/icon.svg", "w") as f:
        f.write(svg())
    png(192).save(f"{web}/icon-192.png", optimize=True)
    png(512).save(f"{web}/icon-512.png", optimize=True)
    png(180, opaque=True).save(f"{web}/icon-180.png", optimize=True)
    png(512, maskable=True).save(f"{web}/icon-maskable-512.png", optimize=True)

    launcher = safe_scale(66)  # the adaptive-icon safe zone is a 66dp circle
    with open(f"{res}/ic_launcher_foreground.xml", "w") as f:
        f.write(foreground_xml(launcher))
    with open(f"{res}/ic_launcher_monochrome.xml", "w") as f:
        f.write(monochrome_xml(launcher))
    print(f"launcher scale {launcher}, maskable scale {safe_scale(CANVAS * 0.8)}")


if __name__ == "__main__":
    main()
