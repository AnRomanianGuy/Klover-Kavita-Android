"""Original, procedurally drawn demo content for fake_kavita.py's --showcase mode.

Everything here is generated from code (no copyrighted artwork, no downloaded images), so recordings and screenshots made with it
can be published freely. Needs Pillow and numpy; uses fonts from C:/Windows/Fonts when present and falls back to Pillow's own.

The server swaps these in for its plain test images: covers, comic / manga pages, webtoon strips, PDF pages, the EPUB's text and
figures, and series summaries.
"""
import io
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

FONT_DIR = "C:/Windows/Fonts"
_font_cache = {}
SERIES_BY_ID = {}  # set by fake_kavita.install_showcase


def font(name, size):
    key = (name, size)
    if key not in _font_cache:
        path = os.path.join(FONT_DIR, name)
        try:
            _font_cache[key] = ImageFont.truetype(path, size)
        except OSError:
            try:
                _font_cache[key] = ImageFont.load_default(size=size)
            except TypeError:
                _font_cache[key] = ImageFont.load_default()
    return _font_cache[key]


SERIF_B, SERIF, SERIF_I = "georgiab.ttf", "georgia.ttf", "georgiai.ttf"
SANS, SANS_B, HAND, COMIC, COMIC_B, DISPLAY = "segoeui.ttf", "segoeuib.ttf", "segoepr.ttf", "comic.ttf", "comicbd.ttf", "bahnschrift.ttf"


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient(w, h, top, bottom):
    t = np.linspace(0, 1, h)[:, None, None]
    arr = np.array(top, dtype=np.float32) * (1 - t) + np.array(bottom, dtype=np.float32) * t
    arr = np.repeat(arr, w, axis=1)
    return Image.fromarray(arr.astype(np.uint8), "RGB")


def glow(size, centre, radius, colour, strength=1.0):
    """A soft round light on its own RGBA layer."""
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse([centre[0] - radius, centre[1] - radius, centre[0] + radius, centre[1] + radius], fill=colour + (int(255 * strength),))
    return layer.filter(ImageFilter.GaussianBlur(radius * 0.45))


def to_png(img, quality=None):
    out = io.BytesIO()
    img.convert("RGB").save(out, "PNG", optimize=False)
    return out.getvalue()


def wrap(draw, text, fnt, width):
    lines, cur = [], ""
    for word in text.split():
        trial = (cur + " " + word).strip()
        if draw.textlength(trial, font=fnt) <= width or not cur:
            cur = trial
        else:
            lines.append(cur)
            cur = word
    if cur:
        lines.append(cur)
    return lines


# ------------------------------------------------------------------------------------------------------------------ covers

# sky top, sky bottom, light, far land, near land, ink
PALETTES = [
    ("#1d2b53", "#ff8f70", "#ffe29a", "#46557f", "#1b2447", "#ffffff"),   # dusk
    ("#0f2a3f", "#2f8f9d", "#f6f1c8", "#1d5a6a", "#0b2f3d", "#ffffff"),   # sea
    ("#2a1a45", "#c4527a", "#ffd6a5", "#6b2f6a", "#2b1442", "#ffffff"),   # plum
    ("#14323a", "#5fbf8f", "#fff3b0", "#2d7f6a", "#103a34", "#ffffff"),   # clover
    ("#1b1d3a", "#7a6ff0", "#e9e4ff", "#3a3a80", "#171835", "#ffffff"),   # night violet
    ("#3a1c1c", "#e07a3f", "#ffe3b0", "#8a3a2a", "#2e1414", "#ffffff"),   # ember
    ("#102235", "#4d8fd6", "#e8f4ff", "#2a5a96", "#0d2036", "#ffffff"),   # blue hour
]

STYLE_FOR = {
    "Clover Fields": 0, "Wildflower Protocol": 0, "Saffron Skies": 0, "Kitchen Gods": 0,
    "The Last Lighthouse": 1, "Tidewater": 1, "Harbor Lights": 1, "Salt & Thread": 1,
    "Paper Lanterns": 2, "Paper Moons": 2, "Violet Hours": 2, "Ink & Ember": 2,
    "Moonlit Archive": 3, "Blue Hour": 3, "The Cartographer's Dog": 3, "Lost Property": 3,
    "Fox Mountain": 4, "Northern Rails": 4, "The Long Thaw": 4, "Winter Telegraph": 4, "Hollow Crown": 4,
    "Neon Parish": 5, "Velvet Static": 5, "Orbit Cafe": 5, "Bright Machinery": 5, "Quiet Machines": 5,
    "Glass Orchard": 6, "Cinder Atlas": 6, "Dust Choir": 6, "Iron Garden": 6,
}
PALETTE_FOR = {
    "Clover Fields": 3, "Wildflower Protocol": 3, "Saffron Skies": 5, "Kitchen Gods": 0,
    "The Last Lighthouse": 1, "Tidewater": 6, "Harbor Lights": 1, "Salt & Thread": 6,
    "Paper Lanterns": 0, "Paper Moons": 4, "Violet Hours": 2, "Ink & Ember": 5,
    "Moonlit Archive": 4, "Blue Hour": 6, "The Cartographer's Dog": 3, "Lost Property": 2,
    "Fox Mountain": 5, "Northern Rails": 1, "The Long Thaw": 6, "Winter Telegraph": 1, "Hollow Crown": 2,
    "Neon Parish": 2, "Velvet Static": 4, "Orbit Cafe": 6, "Bright Machinery": 0, "Quiet Machines": 1,
    "Glass Orchard": 3, "Cinder Atlas": 5, "Dust Choir": 0, "Iron Garden": 3,
}


def _hills(img, layers, base_y, amp, colours, seed, scale=1.0):
    w, h = img.size
    rnd = random.Random(seed)
    d = ImageDraw.Draw(img)
    for i in range(layers):
        t = i / max(1, layers - 1)
        y0 = base_y + i * amp * 0.9
        f1, f2 = rnd.uniform(0.8, 1.6) / w * 6.28, rnd.uniform(2.0, 3.4) / w * 6.28
        p1, p2 = rnd.uniform(0, 6.28), rnd.uniform(0, 6.28)
        pts = [(x, y0 + math.sin(x * f1 + p1) * amp * 0.55 + math.sin(x * f2 + p2) * amp * 0.25) for x in range(0, w + 8, 8)]
        d.polygon(pts + [(w, h), (0, h)], fill=mix(colours[0], colours[1], t))


def _stars(img, count, seed, region):
    rnd = random.Random(seed)
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = region
    for _ in range(count):
        x, y, r = rnd.uniform(x0, x1), rnd.uniform(y0, y1), rnd.uniform(1.2, 3.6)
        a = rnd.uniform(0.5, 1.0)
        d.ellipse([x - r, y - r, x + r, y + r], fill=mix((20, 20, 40), (255, 255, 240), a))


def scene(style, palette, w, h, seed, variant=0):
    """A full-bleed illustrated scene (no text), drawn at the size asked."""
    top, bottom, light, far, near, ink = [hexc(c) for c in palette]
    rnd = random.Random(seed * 7 + variant)
    img = gradient(w, h, top, bottom)
    horizon = h * (0.56 + 0.04 * ((variant % 3) - 1))
    if style in (0, 1, 3, 4):  # a sun or moon with a halo
        cx, cy, r = w * (0.5 + 0.12 * math.sin(seed + variant)), h * 0.30, w * 0.13
        g = glow(img.size, (cx, cy), r * 3.0, light, 0.45)
        img.paste(g, (0, 0), g)
        d = ImageDraw.Draw(img)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=light)
        if style == 3:  # craters
            for dx, dy, rr in ((-0.35, -0.2, 0.22), (0.25, 0.1, 0.16), (-0.1, 0.35, 0.12)):
                d.ellipse([cx + dx * r - rr * r, cy + dy * r - rr * r, cx + dx * r + rr * r, cy + dy * r + rr * r], fill=mix(light, bottom, 0.25))
    if style in (1, 3, 4, 5):
        _stars(img, 90, seed, (0, 0, w, horizon * 0.8))

    d = ImageDraw.Draw(img)
    if style == 0:  # rolling hills with little clovers
        _hills(img, 5, horizon, h * 0.07, (far, near), seed)
        d = ImageDraw.Draw(img)
        for _ in range(26):
            x, y = rnd.uniform(0, w), rnd.uniform(horizon + h * 0.08, h * 0.95)
            s = rnd.uniform(10, 26) * w / 800
            for ang in (0, 90, 180, 270):
                ax, ay = x + math.cos(math.radians(ang + 45)) * s * 0.7, y + math.sin(math.radians(ang + 45)) * s * 0.7
                d.ellipse([ax - s * 0.6, ay - s * 0.6, ax + s * 0.6, ay + s * 0.6], fill=mix(light, near, 0.55))
    elif style == 1:  # sea with a lighthouse
        d.rectangle([0, horizon, w, h], fill=far)
        for i in range(9):
            y = horizon + i * (h - horizon) / 9
            d.rectangle([0, y, w, h], fill=mix(far, near, i / 9))
            for x in range(-40, w + 80, 70):
                d.arc([x + (i % 2) * 35, y - 8, x + 70 + (i % 2) * 35, y + 14], 200, 340, fill=mix(light, far, 0.6), width=3)
        tx, tw, ty = w * 0.68, w * 0.085, horizon - h * 0.30
        d.polygon([(tx - tw, horizon + h * 0.04), (tx + tw, horizon + h * 0.04), (tx + tw * 0.62, ty + h * 0.07), (tx - tw * 0.62, ty + h * 0.07)], fill=ink)
        for k in range(1, 4):
            yy = ty + h * 0.07 + k * h * 0.055
            d.polygon([(tx - tw * (0.62 + k * 0.1), yy), (tx + tw * (0.62 + k * 0.1), yy), (tx + tw * (0.62 + k * 0.1), yy + h * 0.022), (tx - tw * (0.62 + k * 0.1), yy + h * 0.022)], fill=(200, 60, 60))
        d.rectangle([tx - tw * 0.7, ty, tx + tw * 0.7, ty + h * 0.07], fill=light)
        d.polygon([(tx - tw * 0.9, ty), (tx + tw * 0.9, ty), (tx, ty - h * 0.045)], fill=ink)
        beam = Image.new("RGBA", img.size, (0, 0, 0, 0))
        ImageDraw.Draw(beam).polygon([(tx, ty + h * 0.035), (0, ty - h * 0.08), (0, ty + h * 0.16)], fill=light + (70,))
        beam = beam.filter(ImageFilter.GaussianBlur(10))
        img.paste(beam, (0, 0), beam)
        d = ImageDraw.Draw(img)
        d.polygon([(0, horizon + h * 0.26), (w * 0.4, horizon + h * 0.13), (w * 0.9, horizon + h * 0.2), (w, horizon + h * 0.3), (w, h), (0, h)], fill=near)
    elif style == 2:  # floating lanterns
        for i in range(34):
            x, y = rnd.uniform(0.03, 0.97) * w, rnd.uniform(0.04, 0.78) * h
            r = rnd.uniform(0.018, 0.06) * w * (0.6 + y / h)
            c = mix(light, (255, 130, 70), rnd.uniform(0, 0.6))
            g = glow(img.size, (x, y), r * 2.4, c, 0.5)
            img.paste(g, (0, 0), g)
            d = ImageDraw.Draw(img)
            d.rounded_rectangle([x - r * 0.8, y - r, x + r * 0.8, y + r], radius=r * 0.35, fill=c)
            d.line([(x, y - r), (x, y - r * 1.8)], fill=mix(c, top, 0.5), width=2)
        _hills(img, 3, h * 0.82, h * 0.05, (far, near), seed)
    elif style == 3:  # a moonlit archive: stacks of books and shelves
        d.rectangle([0, horizon, w, h], fill=near)
        for sx in range(0, w, int(w * 0.12)):
            top_y = horizon - rnd.uniform(0.04, 0.22) * h
            d.rectangle([sx, top_y, sx + w * 0.11, h], fill=mix(far, near, rnd.uniform(0.2, 0.9)))
            by = top_y + 10
            while by < h - 30:
                bh = rnd.uniform(18, 40) * h / 1200
                d.rectangle([sx + 6, by, sx + w * 0.11 - 6, by + bh], fill=mix(light, far, rnd.uniform(0.55, 0.9)))
                by += bh + 5
    elif style == 4:  # mountains with an aurora
        for i in range(3):
            band = Image.new("RGBA", img.size, (0, 0, 0, 0))
            pts = [(x, h * (0.18 + 0.07 * i) + math.sin(x / w * 7 + i + seed) * h * 0.04) for x in range(0, w + 10, 10)]
            ImageDraw.Draw(band).line(pts, fill=mix((90, 255, 190), light, i / 3) + (95,), width=int(h * 0.05))
            band = band.filter(ImageFilter.GaussianBlur(h * 0.02))
            img.paste(band, (0, 0), band)
        d = ImageDraw.Draw(img)
        for i, (c0, hh, offs) in enumerate(((far, 0.30, 0.0), (mix(far, near, 0.5), 0.24, 0.25), (near, 0.16, 0.55))):
            peaks = [(0, h)]
            x = -w * 0.1
            while x < w * 1.1:
                px = x + rnd.uniform(0.12, 0.26) * w
                peaks += [(x, horizon + h * (0.05 + offs * 0.2)), (px, horizon - h * hh * rnd.uniform(0.7, 1.1) + h * offs * 0.1)]
                x = px + rnd.uniform(0.05, 0.14) * w
            peaks += [(w, h)]
            d.polygon(peaks, fill=c0)
    elif style == 5:  # neon skyline
        d.rectangle([0, horizon, w, h], fill=near)
        for k in range(7):
            ring = glow(img.size, (w * 0.5, horizon - h * 0.04), w * (0.35 - k * 0.03), mix(light, (255, 60, 160), k / 7), 0.09)
            img.paste(ring, (0, 0), ring)
        d = ImageDraw.Draw(img)
        d.ellipse([w * 0.5 - w * 0.2, horizon - h * 0.2, w * 0.5 + w * 0.2, horizon + h * 0.12], fill=mix(light, (255, 90, 170), 0.5))
        for sy in range(int(horizon - h * 0.16), int(horizon + h * 0.12), int(h * 0.025)):
            d.rectangle([w * 0.28, sy, w * 0.72, sy + h * 0.006 + (sy - horizon) * 0.02], fill=top)
        x = 0
        while x < w:
            bw = rnd.uniform(0.07, 0.15) * w
            bh = rnd.uniform(0.1, 0.34) * h
            d.rectangle([x, horizon + h * 0.02 - bh, x + bw, h], fill=far if rnd.random() < 0.5 else near)
            for wy in range(int(horizon - bh + h * 0.04), int(horizon), int(h * 0.028)):
                for wx in range(int(x + 8), int(x + bw - 10), int(w * 0.03)):
                    if rnd.random() < 0.45:
                        d.rectangle([wx, wy, wx + w * 0.012, wy + h * 0.012], fill=mix(light, (255, 90, 170), rnd.random()))
            x += bw + rnd.uniform(0, 0.015) * w
    else:  # geometry: a crisp sun, bold rings, layered hills
        cx, cy = w * 0.5, h * 0.36
        for k in range(6, 0, -1):
            r = w * (0.07 + k * 0.075)
            ring = Image.new("RGBA", img.size, (0, 0, 0, 0))
            ImageDraw.Draw(ring).ellipse([cx - r, cy - r, cx + r, cy + r], fill=mix(light, top, k / 7) + (int(70 + 18 * (6 - k)),))
            img.paste(ring, (0, 0), ring)
        d = ImageDraw.Draw(img)
        d.ellipse([cx - w * 0.1, cy - w * 0.1, cx + w * 0.1, cy + w * 0.1], fill=light)
        for i in range(7):
            x, y, r = rnd.uniform(0.05, 0.95) * w, rnd.uniform(0.08, 0.6) * h, rnd.uniform(0.012, 0.04) * w
            d.ellipse([x - r, y - r, x + r, y + r], fill=mix(light, bottom, rnd.random() * 0.5))
        _hills(img, 3, h * 0.7, h * 0.07, (far, near), seed)
    return img


def series_style(name, sid):
    return STYLE_FOR.get(name, sid % 7), PALETTES[PALETTE_FOR.get(name, sid % len(PALETTES))]


def cover(name, sid, w=800, h=1200, badge=None, variant=0):
    style, pal = series_style(name, sid)
    img = scene(style, pal, w, h, sid, variant)
    d = ImageDraw.Draw(img)
    ink = hexc(pal[5])
    # darken the foot so the title reads on any scene
    shade = Image.new("RGBA", img.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shade)
    for i in range(int(h * 0.5)):
        sd.line([(0, h - 1 - i), (w, h - 1 - i)], fill=(8, 10, 22, int(190 * (1 - i / (h * 0.5)) ** 1.6)))
    img.paste(shade, (0, 0), shade)
    d = ImageDraw.Draw(img)
    # title
    fnt = font(SERIF_B, int(w * 0.105))
    lines = wrap(d, name.upper() if len(name) < 14 else name, fnt, w * 0.82)
    while len(lines) > 3:
        fnt = font(SERIF_B, int(fnt.size * 0.9))
        lines = wrap(d, name, fnt, w * 0.82)
    lh = int(fnt.size * 1.12)
    y = h - h * 0.08 - lh * len(lines)
    for ln in lines:
        tw = d.textlength(ln, font=fnt)
        d.text(((w - tw) / 2 + 3, y + 3), ln, fill=(0, 0, 0), font=fnt)
        d.text(((w - tw) / 2, y), ln, fill=ink, font=fnt)
        y += lh
    d.line([(w * 0.34, h - h * 0.062), (w * 0.66, h - h * 0.062)], fill=ink, width=3)
    d.rectangle([w * 0.03, h * 0.02, w * 0.97, h * 0.98], outline=mix(ink, (0, 0, 0), 0.35), width=3)
    if badge:
        bf = font(SANS_B, int(w * 0.06))
        tw = d.textlength(badge, font=bf)
        bx = w * 0.07
        d.rounded_rectangle([bx, h * 0.05, bx + tw + w * 0.07, h * 0.05 + bf.size * 1.7], radius=bf.size * 0.8, fill=(10, 12, 26))
        d.text((bx + w * 0.035, h * 0.05 + bf.size * 0.32), badge, fill=ink, font=bf)
    return img


def figure(sid, w=900, h=520):
    """An illustration for the EPUB: a landscape scene, no title."""
    style = [0, 1, 4, 0, 3, 2][sid % 6]
    return scene(style, PALETTES[[3, 0, 6, 5, 4, 2][sid % 6]], w, h, sid + 11)


def cover_png(seed, label, w=400, h=600):
    """Drop-in for fake_kavita.cover_png: the label says what is wanted (see the routes in fake_kavita.py)."""
    if label == "Figure":
        return to_png(figure(seed, w, h).resize((w, h), Image.LANCZOS))
    if label.startswith("Vol "):
        sid, v = seed // 100, int(label[4:])
        base = SERIES_BY_ID.get(sid, {"name": "Untitled"})
        return to_png(cover(base["name"], sid, 800, 1200, badge=f"VOL {v}", variant=v))
    if label.startswith("Ch "):
        sid = (seed - 10000) // 100
        base = SERIES_BY_ID.get(sid, {"name": "Untitled"})
        return to_png(cover(base["name"], sid, 800, 1200, badge=label.upper(), variant=seed % 5))
    sid = seed
    return to_png(cover(label, sid, 800, 1200))


# ------------------------------------------------------------------------------------------------------------- comic pages

LINES = [
    "You came all this way?", "I left the light on.", "It was never about the harvest.", "Listen. Do you hear that?",
    "We should go before the tide.", "Tell them I said hello.", "That's not on any map.", "Five more minutes.",
    "The rain stopped an hour ago.", "I kept your seat.", "Is that smoke?", "Don't look down.",
    "Everything grows if you let it.", "We were wrong about the winter.", "Help me carry this?", "It's quieter than I remember.",
    "Wait for me!", "The train is late again.", "Home is a verb.", "Who left this here?",
]
SFX = ["WHOOSH", "CLANG", "RUSTLE", "DONG", "SHHH", "CRACK", "HUM", "THUD"]

TEMPLATES = [
    [(0, 0, 1, .30), (0, .30, .5, .62), (.5, .30, 1, .62), (0, .62, 1, 1)],
    [(0, 0, 1, .22), (0, .22, 1, .52), (0, .52, .38, 1), (.38, .52, 1, 1)],
    [(0, 0, .55, .45), (.55, 0, 1, .45), (0, .45, 1, .72), (0, .72, .5, 1), (.5, .72, 1, 1)],
    [(0, 0, 1, .40), (0, .40, .33, .70), (.33, .40, .66, .70), (.66, .40, 1, .70), (0, .70, 1, 1)],
    [(0, 0, 1, .55), (0, .55, .5, 1), (.5, .55, 1, 1)],
    [(0, 0, .5, .3), (.5, 0, 1, .3), (0, .3, 1, .64), (0, .64, 1, 1)],
]


def screentone(d, box, base_light, dot, step, density):
    x0, y0, x1, y1 = [int(v) for v in box]
    for yy in range(y0, y1, step):
        row = (yy - y0) // step
        for xx in range(x0 + (step // 2 if row % 2 else 0), x1, step):
            t = density(xx, yy)
            r = step * 0.5 * t
            if r > 0.6:
                d.ellipse([xx - r, yy - r, xx + r, yy + r], fill=dot)


def panel_art(kind, w, h, rnd, colour, pal):
    """The picture inside one panel, in colour (comics) or ink and tone (manga)."""
    top, bottom, light, far, near, ink = [hexc(c) for c in pal]
    if colour:
        img = gradient(w, h, top, bottom)
    else:
        img = Image.new("RGB", (w, h), (250, 250, 250))
    d = ImageDraw.Draw(img)
    black, grey = (18, 18, 20), (120, 120, 126)
    if kind == "sky":
        cx, cy, r = w * rnd.uniform(0.25, 0.75), h * rnd.uniform(0.2, 0.4), min(w, h) * 0.17
        if colour:
            d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=light)
            _hills(img, 3, h * 0.62, h * 0.09, (far, near), rnd.randint(0, 9999))
        else:
            screentone(d, (0, 0, w, h * 0.7), None, grey, 9, lambda x, y: 0.15 + 0.7 * (y / (h * 0.7)))
            d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(252, 252, 252), outline=black, width=4)
            pts = [(x, h * 0.66 + math.sin(x / w * 5 + rnd.random()) * h * 0.06) for x in range(0, w + 6, 6)]
            d.polygon(pts + [(w, h), (0, h)], fill=black)
    elif kind == "city":
        x = 0
        while x < w:
            bw, bh = rnd.uniform(0.08, 0.2) * w, rnd.uniform(0.25, 0.75) * h
            fill = mix(far, near, rnd.random()) if colour else (rnd.choice([30, 52, 70]),) * 3
            d.rectangle([x, h - bh, x + bw, h], fill=fill)
            for wy in range(int(h - bh + 10), int(h - 10), 22):
                for wx in range(int(x + 8), int(x + bw - 12), 18):
                    if rnd.random() < 0.4:
                        d.rectangle([wx, wy, wx + 8, wy + 10], fill=light if colour else (245, 245, 245))
            x += bw + 3
    elif kind == "close":  # a large eye, as in any manga
        cx, cy = w / 2, h / 2
        rx, ry = w * 0.34, h * 0.26
        d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=(250, 250, 250), outline=black, width=5)
        ir = min(rx, ry) * 0.95
        d.ellipse([cx - ir, cy - ir, cx + ir, cy + ir], fill=(near if colour else (60, 60, 66)))
        d.ellipse([cx - ir * 0.55, cy - ir * 0.55, cx + ir * 0.55, cy + ir * 0.55], fill=black)
        d.ellipse([cx - ir * 0.5, cy - ir * 0.62, cx - ir * 0.1, cy - ir * 0.22], fill=(255, 255, 255))
        d.ellipse([cx + ir * 0.15, cy + ir * 0.1, cx + ir * 0.35, cy + ir * 0.3], fill=(255, 255, 255))
        d.arc([cx - rx, cy - ry * 1.45, cx + rx, cy + ry * 0.4], 195, 345, fill=black, width=8)
    elif kind == "speed":
        cx, cy = w * rnd.uniform(0.3, 0.7), h * rnd.uniform(0.3, 0.7)
        for i in range(70):
            ang = i * 360 / 70 + rnd.uniform(-2, 2)
            r0, r1 = min(w, h) * rnd.uniform(0.12, 0.28), max(w, h)
            d.line([(cx + math.cos(math.radians(ang)) * r0, cy + math.sin(math.radians(ang)) * r0),
                    (cx + math.cos(math.radians(ang)) * r1, cy + math.sin(math.radians(ang)) * r1)],
                   fill=(light if colour else black), width=rnd.choice([2, 3, 5]))
    elif kind == "rain":
        for _ in range(110):
            x, y = rnd.uniform(-w * 0.2, w), rnd.uniform(-20, h)
            d.line([(x, y), (x - 16, y + 60)], fill=(light if colour else grey), width=2)
        if not colour:
            screentone(d, (0, h * 0.6, w, h), None, grey, 9, lambda x, y: 0.5)
    else:  # tone: a field of halftone dots
        if colour:
            for i in range(0, h, 14):
                d.rectangle([0, i, w, i + 7], fill=mix(far, near, i / h))
        else:
            screentone(d, (0, 0, w, h), None, black, 8, lambda x, y: 0.15 + 0.8 * (x / w))
    return img


def bubble(d, centre, text, fnt, maxw):
    lines = wrap(d, text, fnt, maxw)
    lh = int(fnt.size * 1.18)
    tw = max(d.textlength(ln, font=fnt) for ln in lines)
    th = lh * len(lines)
    pad = fnt.size * 0.9
    cx, cy = centre
    box = [cx - tw / 2 - pad, cy - th / 2 - pad * 0.7, cx + tw / 2 + pad, cy + th / 2 + pad * 0.7]
    d.ellipse([box[0] - 3, box[1] - 3, box[2] + 3, box[3] + 3], fill=(20, 20, 24))
    d.ellipse(box, fill=(255, 255, 255))
    tail = [(cx - pad * 0.3, box[3] - 6), (cx + pad * 0.5, box[3] - 6), (cx - pad * 0.1, box[3] + pad * 1.2)]
    d.polygon([(p[0], p[1] + 2) for p in tail], fill=(20, 20, 24))
    d.polygon(tail, fill=(255, 255, 255))
    d.ellipse([box[0] + 2, box[1] + 2, box[2] - 2, box[3] - 2], fill=(255, 255, 255))
    y = cy - th / 2
    for ln in lines:
        d.text((cx - d.textlength(ln, font=fnt) / 2, y), ln, fill=(20, 20, 24), font=fnt)
        y += lh


def comic_page(series, s, v, c, page, wide=False, colour=False, chapter_no=1):
    """One page, drawn at 800x1200 (1600x1200 for a spread). Deterministic for a given chapter and page."""
    name = SERIES_BY_ID[s]["name"]
    _, pal = series_style(name, s)
    rnd = random.Random(s * 100003 + v * 1009 + c * 31 + page)
    W, H = (1600, 1200) if wide else (800, 1200)
    paper = (252, 250, 245) if colour else (255, 255, 255)
    img = Image.new("RGB", (W, H), paper)
    d = ImageDraw.Draw(img)
    margin, gut = 44, 22
    if wide:
        templates = [[(0, 0, 1, .45), (0, .45, .33, 1), (.33, .45, .66, 1), (.66, .45, 1, 1)]]
        layout = templates[0]
    else:
        layout = TEMPLATES[(s + v * 3 + c + page) % len(TEMPLATES)]
    if page == 0 and not wide:  # a title page for the chapter
        layout = [(0, 0, 1, .46)] + [(0, .46, .5, .74), (.5, .46, 1, .74)] + [(0, .74, 1, 1)]
    kinds = ["sky", "city", "close", "speed", "rain", "tone", "sky"]
    rnd.shuffle(kinds)
    if wide:
        kinds = ["sky", "city", "close", "speed"]
    bubbles = []
    for i, (x0, y0, x1, y1) in enumerate(layout):
        px0, py0 = margin + x0 * (W - 2 * margin) + (gut / 2 if x0 > 0 else 0), margin + y0 * (H - 2 * margin) + (gut / 2 if y0 > 0 else 0)
        px1, py1 = margin + x1 * (W - 2 * margin) - (gut / 2 if x1 < 1 else 0), margin + y1 * (H - 2 * margin) - (gut / 2 if y1 < 1 else 0)
        pw, ph = int(px1 - px0), int(py1 - py0)
        kind = "sky" if (page == 0 and i == 0) else kinds[i % len(kinds)]
        art = panel_art(kind, pw, ph, rnd, colour, pal)
        img.paste(art, (int(px0), int(py0)))
        d.rectangle([px0, py0, px1, py1], outline=(18, 18, 20), width=5)
        if i % 2 == 0 or wide:
            bubbles.append((px0 + pw * rnd.uniform(0.3, 0.7), py0 + ph * rnd.uniform(0.18, 0.4), min(pw * 0.7, 300)))
        if page == 0 and i == 0 and not wide:
            tf = font(SERIF_B, 64)
            for k, (txt, fnt, yy) in enumerate(((name.upper(), tf, ph * 0.62), (f"Chapter {chapter_no}", font(SANS_B, 40), ph * 0.8))):
                for ln in wrap(d, txt, fnt, pw * 0.9):
                    tw = d.textlength(ln, font=fnt)
                    d.text((px0 + (pw - tw) / 2 + 3, py0 + yy + 3), ln, fill=(0, 0, 0), font=fnt)
                    d.text((px0 + (pw - tw) / 2, py0 + yy), ln, fill=(255, 255, 255), font=fnt)
                    yy += fnt.size * 1.1
    bf = font(COMIC, 27)
    used = list(LINES)
    rnd.shuffle(used)
    for (bx, by, bw), line in zip(bubbles[: (3 if wide else 2)], used):
        if page == 0 and not wide and bx < margin + 5:
            continue
        bubble(d, (bx, by), line, bf, bw)
    if rnd.random() < 0.6 and not wide:
        sf, word = font(COMIC_B, 76), rnd.choice(SFX)
        layer = Image.new("RGBA", (420, 150), (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        ld.text((8, 8), word, fill=(255, 255, 255), font=sf, stroke_width=8, stroke_fill=(18, 18, 20))
        layer = layer.rotate(rnd.uniform(-14, 14), expand=True, resample=Image.BICUBIC)
        img.paste(layer, (int(rnd.uniform(60, W - 460)), int(H * rnd.uniform(0.34, 0.6))), layer)
    pf = font(SANS, 24)
    d.text((W - margin - 10, H - 40) if not wide else (W / 2 - 8, H - 40), str(page + 1), fill=(90, 90, 96), font=pf)
    return img


# ------------------------------------------------------------------------------------------------------------ webtoon strips


def webtoon_strip(page, pages=12, w=800, h=2400):
    """A soft vertical comic. The background blends into the next strip's, so there is no seam between pieces."""
    tones = [hexc(x) for x in ("#e8f1ff", "#ffe9e1", "#e9f7ec", "#f1e9ff", "#fff3d6", "#e1f4f7")]

    def tone(t):
        i = int(t)
        a, b = tones[i % len(tones)], tones[(i + 1) % len(tones)]
        return mix(a, b, t - i)
    arr = np.zeros((h, w, 3), dtype=np.uint8)
    for y in range(h):
        arr[y, :, :] = tone(page + y / h)
    img = Image.fromarray(arr, "RGB")
    d = ImageDraw.Draw(img)
    rnd = random.Random(page * 977 + 5)
    y = 130
    for k in range(3):
        ph = rnd.randint(520, 640)
        x0, x1 = rnd.randint(50, 120), w - rnd.randint(50, 120)
        pal = PALETTES[(page * 2 + k) % len(PALETTES)]
        art = scene([0, 1, 2, 4, 5][(page + k) % 5], pal, x1 - x0, ph, page * 13 + k, k)
        mask = Image.new("L", art.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, art.size[0], art.size[1]], radius=34, fill=255)
        img.paste(art, (x0, y), mask)
        d.rounded_rectangle([x0, y, x1, y + ph], radius=34, outline=(255, 255, 255), width=6)
        bubble(d, (x0 + (x1 - x0) * rnd.uniform(0.3, 0.7), y + ph * 0.22), rnd.choice(LINES), font(COMIC, 34), 340)
        y += ph + rnd.randint(110, 150)
    d.text((w - 150, h - 120), f"{page + 1}/{pages}", fill=(120, 120, 130), font=font(SANS, 30))
    return img


# ------------------------------------------------------------------------------------------------------------------ PDF / EPUB

NOTES = [
    ("Soil that remembers", "Every field keeps a diary in its dirt. Dig a hand-width down and you can read the last three seasons: the pale threads of last year's roots, the darker crumb where compost went in, the cool wet seam where the spring rain stopped."),
    ("What clover does", "Clover is a quiet worker. Its roots hold small nodules that take nitrogen out of the air and hand it to the soil, so whatever grows next season starts with a little help it never asked for."),
    ("Reading the weather", "The old almanacs were never really about predictions. They were about paying attention: which way the swallows flew, how the smoke leaned, whether the evening felt thin or thick. Attention is most of farming."),
    ("A short list of tools", "A spade with a worn handle. A pocket knife. Twine. A notebook with a pencil tied to it, because pens freeze and pencils forgive. Everything else is borrowed from a neighbour and returned with something baked."),
    ("On waiting", "Seeds do not hurry and neither should you. Most of the work in a year is waiting well: keeping the ground open, keeping the water moving, keeping out of the way of things that are already going right."),
    ("Winter notes", "In the long dark months the field looks empty and is not. Below the frost line the roots are slow but awake, and the whole garden is rehearsing. Use the time to mend what broke and write down what worked."),
    ("The last page", "If you only keep one habit from this book, keep this one: write the date on everything. A year from now you will not remember the week the first leaves came, and you will want to."),
    ("Margins", "Leave some of the field wild. A strip of long grass along the fence feeds more small lives than any border you could plant, and it asks for nothing but to be left alone."),
]


def pdf_pages(title="Field Notes"):
    pages = []
    W, H = 1240, 1754
    for i in range(8):
        wide = i == 3
        w, h = (H, W) if wide else (W, H)
        img = Image.new("RGB", (w, h), (252, 250, 244))
        d = ImageDraw.Draw(img)
        if i == 0:
            art = scene(0, PALETTES[3], w, int(h * 0.55), 21)
            img.paste(art, (0, 0))
            d.text((90, int(h * 0.6)), "FIELD NOTES", fill=(24, 40, 34), font=font(SERIF_B, 120))
            d.text((94, int(h * 0.6) + 150), "A small book about soil, weather and waiting", fill=(70, 84, 78), font=font(SERIF_I, 44))
            d.line([(94, int(h * 0.6) + 240), (440, int(h * 0.6) + 240)], fill=(46, 125, 80), width=5)
            d.text((94, h - 150), "A. Fieldhouse", fill=(24, 40, 34), font=font(SANS_B, 40))
        elif wide:
            d.text((90, 80), "Rainfall and yield, five seasons", fill=(24, 40, 34), font=font(SERIF_B, 62))
            bars = [34, 52, 41, 66, 58]
            labels = ["Spring", "Summer", "Autumn", "Winter", "Spring"]
            base, ch = h - 190, h - 420
            for k, val in enumerate(bars):
                x0 = 160 + k * 270
                d.rectangle([x0, base - ch * val / 70, x0 + 190, base], fill=mix((46, 125, 80), (190, 120, 40), k / 5))
                d.text((x0 + 10, base + 18), labels[k], fill=(60, 70, 66), font=font(SANS, 34))
                d.text((x0 + 62, base - ch * val / 70 - 52), f"{val}", fill=(24, 40, 34), font=font(SANS_B, 38))
            d.line([(130, base), (w - 130, base)], fill=(80, 80, 84), width=3)
            d.text((90, h - 90), "Fig. 3  Rainfall in millimetres, grouped by season", fill=(90, 96, 92), font=font(SERIF_I, 32))
        else:
            head, body = NOTES[(i * 3) % len(NOTES)]
            d.text((100, 110), f"{i}.  {head}", fill=(24, 40, 34), font=font(SERIF_B, 66))
            y = 250
            for para in (body, NOTES[(i * 3 + 1) % len(NOTES)][1]):
                for ln in wrap(d, para, font(SERIF, 38), w - 200):
                    d.text((100, y), ln, fill=(40, 46, 44), font=font(SERIF, 38))
                    y += 58
                y += 34
            art = figure(i * 5, w - 200, 520)
            img.paste(art, (100, y + 20))
            d.text((100, y + 560), f"Fig. {i}  A field in the last light of the day", fill=(90, 96, 92), font=font(SERIF_I, 32))
        d.text((w / 2 - 12, h - 70), str(i + 1), fill=(130, 130, 134), font=font(SANS, 30))
        pages.append(img)
    return pages


def pdf_bytes():
    pages = pdf_pages()
    out = io.BytesIO()
    pages[0].save(out, "PDF", save_all=True, append_images=pages[1:], resolution=150)
    return out.getvalue()


EPUB_TITLES = ["Thaw", "Sowing", "Long Light", "Harvest Moon", "First Frost", "The Quiet Months"]
EPUB_OPEN = [
    "The first morning the ground gives, you can hear it: a small ticking under the snow, like a house cooling after a fire.",
    "By the second week of April the field has stopped being a place you walk across and become a place you walk into.",
    "In June the days are so long that the evenings take over the afternoons, and nobody quite remembers eating supper.",
    "There is a night in September when the moon comes up the colour of a ripe apricot and the whole valley holds still to watch.",
    "The first frost is never a surprise and always a shock: you wake to a garden you do not recognise, silvered and very quiet.",
    "Winter is not the absence of the year. It is the year folded small, so that it fits in a pocket and can be taken out later.",
]


def book_paragraphs(section):
    rnd = random.Random(section * 41 + 3)
    pool = [p for _, p in NOTES]
    rnd.shuffle(pool)
    out = [EPUB_OPEN[section % len(EPUB_OPEN)]] + pool[: 4 + section % 3]
    extra = [
        "She kept the notebook in the pocket of the green coat, and by the end of the year it smelled faintly of tomato leaves.",
        "He never wrote the weather down. He said the swallows kept better records than he could.",
        "Somewhere beyond the hedge a kettle was going, and the sound of it made the whole afternoon feel borrowed from a kinder century.",
    ]
    rnd.shuffle(extra)
    return out + extra[:2] + pool[4:6]


def summary_for(name):
    return SUMMARIES.get(name)


SUMMARIES = {
    "Clover Fields": "<p>Three generations farm one stubborn field, and the field has opinions.</p><p>A slow, warm story about <b>soil, family</b> and the things that grow when you stop forcing them.</p>",
    "Moonlit Archive": "<p>The night librarian of a flooded city keeps every book dry and every secret safe.</p><p>Until a patron asks for a title that was never written.</p>",
    "Paper Lanterns": "<p>Once a year the whole village sends a lantern up for someone it misses.</p><p>This year, one of them comes back down with a reply.</p>",
    "The Last Lighthouse": "<p>The sea has not moved in a week, and the keeper is the only one who has noticed.</p><p>A quiet mystery about <i>patience and the shapes of fog</i>.</p>",
    "Ink & Ember": "<p>A tattooist whose designs come true and a blacksmith who refuses to believe in them.</p><p>Fire, ink and a very slow burn.</p>",
    "Northern Rails": "<p>The last night train north carries a stranger, a cello and a promise nobody on board remembers making.</p>",
    "Glass Orchard": "<p>Every tree in the orchard is made of glass, and every spring one of them cracks.</p><p>Mira has until the thaw to find out why.</p>",
    "Tidewater": "<p>A harbour town where the tide goes out further each year, and what it leaves behind is getting stranger.</p>",
    "Saffron Skies": "<p>Two rival kite-makers, one festival, and a wind that has started taking sides.</p>",
    "Neon Parish": "<p>The neighbourhood church became a ramen bar, then a radio station, then something nobody has a word for.</p><p>A warm, loud story set under the signs.</p>",
}
DEFAULT_SUMMARY = "<p>A quiet story about <b>patience</b>, soil and the plants nobody notices.</p><p>Spread over six volumes, it follows a family through four seasons and one very long winter.</p>"


def book_html(cid, page, host, api_key):
    base = f"//{host}/api/book/{cid}/book-resources?apiKey={api_key}&file="
    paras = "".join(f"<p>{p}</p>" for p in book_paragraphs(page))
    nxt = f'<a kavita-page="{page + 1}" href="javascript:void(0)">Continue to {EPUB_TITLES[(page + 1) % len(EPUB_TITLES)]}</a>' if page < 5 else "The end."
    half = len(paras) // 2
    first, second = paras[:half], paras[half:]
    # split on a paragraph boundary near the middle
    cut = paras.find("</p>", half) + 4
    first, second = paras[:cut], paras[cut:]
    return (
        "<style>.reading-section p{margin:0 0 1em 0;text-align:justify} .reading-section h1{font-size:1.6em;margin:.2em 0 .6em}"
        " .reading-section .cap{font-style:italic;opacity:.7}</style>"
        f'<div class="chapter"><h1>{page + 1}. {EPUB_TITLES[page % len(EPUB_TITLES)]}</h1>{first}'
        f'<p><img src="{base}fig{page}.png" alt="figure"></p><p class="cap">Figure {page + 1}. The field, late in the day.</p>'
        f"{second}<p>{nxt}</p></div>"
    )
