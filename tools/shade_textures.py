"""
Paints the textures for Solace's Blockbench model (src/main/resources/assets/solace/models/entity/solace.bbmodel).
The Lantern's body is built from coloured voxels in code (client/VoxelOrb.java); only its soft plasma glow
(orb_plasma_*.png) is painted here.
Run:  python3 tools/shade_textures.py

One 256x256 sheet holds every piece of the model; the negative form uses the same layout with different colours,
so the model just swaps sheets. The negative also has a "_glow" sheet drawn on top, glowing (its flames and body).

Sheet layout (x, y, width, height), shared with tools/models/build_solace.js:
  skin      (0, 0, 64, 64)      the body, wrapped round the torso and limbs
  armour    (64, 0, 64, 64)     rows of laced plates (8 texels a row)
  katana    (128, 0, 64, 32)    scabbard (top half) and hilt (bottom half)
  cloak     (0, 64, 128, 128)   one tattered sheet: the robes
  spike     (128, 64, 32, 64)   shoulder spikes, base at the top, tip at the bottom
  bone      (160, 64, 32, 64)   claws and blades, the same way round
  band      (192, 64, 64, 32)   the sash
  robe      (192, 96, 64, 32)   short tattered strip
  flames    (0, 192, 128, 64)   4 flames, 32x64 each (negative only)
"""
import math
import numpy as np
import random
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/solace/textures/entity"
SS = 4  # draw 4x bigger, then shrink: soft, painterly edges


def canvas(w, h):
    return Image.new("RGBA", (w * SS, h * SS), (0, 0, 0, 0))


def shrink(img):
    return img.resize((img.width // SS, img.height // SS), Image.LANCZOS)


def bezier(p0, p1, p2, t):
    return ((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
            (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1])


def stroke(draw, p0, p1, p2, w0, w1, color, steps=40):
    """A tapering brush stroke along a curve (all coordinates already scaled up)."""
    pts = [bezier(p0, p1, p2, i / steps) for i in range(steps + 1)]
    for i in range(steps):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        w = w0 + (w1 - w0) * (i / steps)
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy) or 1
        nx, ny = -dy / ln * w / 2, dx / ln * w / 2
        draw.polygon([(x0 + nx, y0 + ny), (x1 + nx, y1 + ny), (x1 - nx, y1 - ny), (x0 - nx, y0 - ny)], fill=color)
        draw.ellipse([x1 - w / 2, y1 - w / 2, x1 + w / 2, y1 + w / 2], fill=color)


def flame(w, h, rng):
    """A white flame tongue: soft, licking upward, bright in the middle."""
    img = canvas(w, h)
    d = ImageDraw.Draw(img)
    S = SS
    for layer, (alpha, scale) in enumerate(((110, 1.0), (200, 0.7), (255, 0.4))):
        for k in range(3):
            bx = w * 0.5 + rng.uniform(-0.15, 0.15) * w
            top = (w * 0.5 + rng.uniform(-0.35, 0.35) * w, h * rng.uniform(0.0, 0.3))
            mid = (bx + rng.uniform(-0.3, 0.3) * w, h * 0.55)
            stroke(d, (bx * S, h * S), (mid[0] * S, mid[1] * S), (top[0] * S, top[1] * S), w * 0.45 * scale * S, 0.6 * S,
                   (255, 255, 255, alpha))
    return shrink(img.filter(ImageFilter.GaussianBlur(S * 0.8)))


def body_tile(w, h, rng, base, streak):
    img = Image.new("RGBA", (w, h), base + (255,))
    px = img.load()
    for x in range(w):
        for y in range(h):
            n = rng.randint(-5, 5)
            px[x, y] = tuple(max(0, min(255, c + n)) for c in base) + (255,)
    d = ImageDraw.Draw(img)
    for _ in range(26):  # long sinewy streaks, like strands pulled tight over the body
        x = rng.uniform(0, w)
        d.line([(x, 0), (x + rng.uniform(-6, 6), h * 0.5), (x + rng.uniform(-8, 8), h)], fill=streak + (255,), width=1)
    return img


def cloak_sheet(w, h, rng, color, edge, holes=False):
    """One wide tattered cloth: solid at the top, torn into a few broad, blunt tails towards the hem."""
    img = canvas(w, h)
    d = ImageDraw.Draw(img)
    S = SS
    hem = []
    x = w
    while x > 0:  # walk the hem right to left: each tatter is a broad tongue with a ragged, blunt end
        width = min(x, rng.uniform(0.12, 0.2) * w)
        notch = h * rng.uniform(0.6, 0.68)
        tail = h * (rng.uniform(0.88, 0.99) if rng.random() < 0.5 else rng.uniform(0.74, 0.84))
        hem += [(x, notch), (x - width * 0.12, tail - h * 0.03), (x - width * 0.4, tail), (x - width * 0.62, tail - h * 0.02),
                (x - width * 0.85, tail - h * 0.05), (x - width, notch)]
        x -= width
    outline = [(0, 0), (w, 0)] + hem
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).polygon([(px * S, py * S) for px, py in outline], fill=255)
    cloth = Image.new("RGBA", img.size, color)
    cd = ImageDraw.Draw(cloth)
    # folds: broad darker and lighter bands, kept inside the cloth
    for _ in range(int(w / 8)):
        x0 = rng.uniform(0, w)
        c = tuple(max(0, min(255, v + rng.choice((-12, -6, 8, 12)))) for v in color[:3]) + (255,)
        stroke(cd, (x0 * S, 0), ((x0 + rng.uniform(-2, 2)) * S, h * 0.5 * S), ((x0 + rng.uniform(-3, 3)) * S, h * S), rng.uniform(2, 4) * S, 1.5 * S, c)
    if edge:  # a light frayed edge along the tears
        ImageDraw.Draw(cloth).line([(px * S, py * S) for px, py in hem], fill=edge, width=int(S * 1.2))
    img.paste(cloth, (0, 0), mask)
    return shrink(img)


def gradient_strip(w, h, base, tip, rng):
    """Spikes and claws: base colour at the top fading to the tip colour at the bottom, with faint ridges."""
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        f = (y / (h - 1)) ** 1.6
        for x in range(w):
            n = rng.randint(-4, 4) + (6 if x % 8 in (0, 1) else 0)
            px[x, y] = tuple(max(0, min(255, int(base[k] + (tip[k] - base[k]) * f) + n)) for k in range(3)) + (255,)
    return img


def armor(w, h, base, light, dark, lace, rows=8):
    """Samurai lamellar armour: rows of lacquered plates (lames), each with a lit top edge and a shadowed bottom,
    laced together with cords running down in pairs. Crisp pixel art, no blur."""
    img = Image.new("RGBA", (w, h), base + (255,))
    d = ImageDraw.Draw(img)
    rh = h // rows
    for r in range(rows):
        y0 = r * rh
        d.line([(0, y0), (w - 1, y0)], fill=light + (255,))
        d.line([(0, y0 + rh - 1), (w - 1, y0 + rh - 1)], fill=dark + (255,))
        for x in range(4, w, 8):  # cord pairs crossing each plate
            d.line([(x, y0 + 1), (x, y0 + rh - 2)], fill=lace + (255,))
            d.line([(x + 1, y0 + 1), (x + 1, y0 + rh - 2)], fill=lace + (255,))
            d.point((x - 1, y0 + rh // 2), fill=lace + (255,))
            d.point((x + 2, y0 + rh // 2), fill=lace + (255,))
    return img


def katana(base, wrap, wrap_dark, metal, metal_dark):
    """A 64x32 strip: the scabbard (top half, glossy lacquer) and the hilt (bottom half, diamond-wrapped)."""
    img = Image.new("RGBA", (64, 32), base + (255,))
    d = ImageDraw.Draw(img)
    d.line([(0, 3), (63, 3)], fill=tuple(min(255, c + 30) for c in base) + (255,))  # a gloss line
    for x in range(0, 64, 16):
        d.rectangle([x, 0, x + 1, 15], fill=metal_dark + (255,))                     # bands round the scabbard
    d.rectangle([0, 16, 63, 31], fill=wrap_dark + (255,))
    for x in range(-16, 64, 6):                                                        # the criss-cross hilt wrap
        d.line([(x, 16), (x + 15, 31)], fill=wrap + (255,), width=2)
        d.line([(x + 15, 16), (x, 31)], fill=wrap + (255,), width=2)
    d.rectangle([0, 16, 3, 31], fill=metal + (255,))
    d.rectangle([60, 16, 63, 31], fill=metal + (255,))
    return img


def sheet(negative, seed=7):
    rng = random.Random(seed)
    main = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    glow = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    if negative:
        robe, robe_edge = (14, 14, 18, 255), (230, 230, 236, 255)
        tile = body_tile(64, 64, rng, (232, 234, 238), (205, 207, 214))
        plates = armor(64, 64, (234, 234, 238), (255, 255, 255), (186, 186, 194), (16, 16, 20))
        sword = katana((236, 236, 240), (20, 20, 24), (200, 200, 206), (60, 60, 66), (150, 150, 158))
    else:
        # dark, ashy greys: a weaker shadow of the negative, not a colour of its own
        robe, robe_edge = (24, 24, 28, 255), (78, 78, 88, 255)
        tile = body_tile(64, 64, rng, (22, 22, 26), (38, 38, 44))
        plates = armor(64, 64, (48, 48, 54), (84, 84, 94), (22, 22, 26), (112, 110, 118))
        sword = katana((14, 14, 18), (128, 20, 26), (40, 8, 10), (150, 128, 70), (70, 60, 36))
    main.paste(tile, (0, 0))
    main.paste(plates, (64, 0))
    main.paste(sword, (128, 0))
    cloak = cloak_sheet(128, 128, rng, robe, robe_edge)
    main.alpha_composite(cloak, (0, 64))
    if negative:
        main.paste(gradient_strip(32, 64, (235, 236, 240), (255, 255, 255), rng), (128, 64))   # spikes
        main.paste(gradient_strip(32, 64, (225, 226, 232), (255, 255, 255), rng), (160, 64))   # claws and blades
    else:
        main.paste(gradient_strip(32, 64, (14, 14, 17), (40, 40, 48), rng), (128, 64))         # spikes: black, edges catch a little light
        main.paste(gradient_strip(32, 64, (20, 19, 23), (120, 116, 124), rng), (160, 64))     # claws: pale at the tips
    band = Image.new("RGBA", (64, 32), (12, 12, 16, 255) if negative else (36, 36, 42, 255))
    bd = ImageDraw.Draw(band)
    for y in range(0, 32, 5):
        bd.line([(0, y), (63, y + 3)], fill=(30, 30, 36, 255) if negative else (54, 54, 62, 255))
    main.paste(band, (192, 64))
    # Short over-skirt for the negative's robe: white tatters over the black.
    main.alpha_composite(cloak_sheet(64, 32, rng, (238, 238, 242, 255) if negative else robe, (255, 255, 255, 255) if negative else None), (192, 96))
    for i in range(4):
        f = flame(32, 64, rng)
        if negative:
            main.alpha_composite(f, (i * 32, 192))
            glow.alpha_composite(f, (i * 32, 192))
    if negative:
        # In the negative, the body itself shines softly.
        g = tile.copy()
        g.putalpha(90)
        glow.alpha_composite(g, (0, 0))
    return main, glow


def outline_tex():
    """Plain white: the outline pass colours it white (normal form) or black (negative) per vertex."""
    return Image.new("RGBA", (16, 16), (255, 255, 255, 255))



# ---------- The Lantern's glow: soft blue plasma round its voxel body ----------

def radial(size, stops):
    """A round glow: stops are (radius 0..1, (r, g, b, a)) from the centre out."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    c = (size - 1) / 2
    for x in range(size):
        for y in range(size):
            r = math.hypot(x - c, y - c) / c
            for i in range(len(stops) - 1):
                (r0, c0), (r1, c1) = stops[i], stops[i + 1]
                if r0 <= r <= r1:
                    f = (r - r0) / (r1 - r0) if r1 > r0 else 0
                    px[x, y] = tuple(int(c0[k] + (c1[k] - c0[k]) * f) for k in range(4))
                    break
    return img


def fbm(size, seed, octaves=5, base=4):
    """Smooth random clouds (fractal noise), values 0..1."""
    rng = np.random.default_rng(seed)
    out = np.zeros((size, size))
    amp, total = 1.0, 0.0
    for o in range(octaves):
        n = base * 2 ** o
        grid = rng.random((n + 1, n + 1))
        img = Image.fromarray((grid * 255).astype(np.uint8)).resize((size, size), Image.BICUBIC)
        out += np.asarray(img, dtype=float) / 255 * amp
        total += amp
        amp *= 0.5
    return out / total


def plasma(size, seed, reach=0.9, veins=1.0, glow=1.0):
    """
    A ball of blue plasma (reference: the energy ball picture): a white-hot middle, lightning-like veins branching
    outwards through blue cloud, and a ragged, uneven outline instead of a neat circle.
    """
    c = (size - 1) / 2
    y, x = np.mgrid[0:size, 0:size]
    dx, dy = (x - c) / c, (y - c) / c
    r = np.hypot(dx, dy)
    ang = np.arctan2(dy, dx)
    # warp the space with noise so the veins wander and fork
    wx, wy = fbm(size, seed + 1, base=3), fbm(size, seed + 2, base=3)
    n = fbm(size, seed + 3, octaves=6, base=4)
    warped = np.asarray(Image.fromarray((n * 255).astype(np.uint8)).transform(
        (size, size), Image.AFFINE, (1, 0, 0, 0, 1, 0)), dtype=float) / 255
    ridge = 1 - np.abs(2 * fbm(size, seed + 4, octaves=6, base=5) - 1)          # thin bright lines where the noise crosses the middle
    ridge2 = 1 - np.abs(2 * fbm(size, seed + 5, octaves=5, base=8) - 1)
    lines = np.clip((ridge ** 10) * 1.3 + (ridge2 ** 14) * 0.9, 0, 1) * veins
    # a ragged outline: how far the plasma reaches depends on direction, unevenly
    edge_noise = fbm(size, seed + 6, octaves=4, base=3)
    lobes = 0.78 + 0.12 * np.sin(ang * 3 + seed) + 0.32 * (edge_noise - 0.5) * 2 + 0.2 * (wx - 0.5)
    limit = np.clip(reach * lobes, 0.35, 0.98)
    fall = np.clip(1 - r / limit, 0, 1)
    cloud = (fall ** 1.1) * (0.3 + 0.7 * warped) * glow
    core = np.clip(1 - r / 0.3, 0, 1) ** 1.4
    v = np.clip(cloud * 0.8 + lines * (fall ** 0.6) * 1.1 + core * 1.2, 0, 1.6)
    # colour: deep blue -> bright blue -> pale cyan -> white as it gets brighter
    stops = [(0.0, (0, 0, 0)), (0.18, (10, 30, 150)), (0.45, (35, 110, 255)), (0.8, (120, 200, 255)), (1.1, (225, 245, 255)), (1.6, (255, 255, 255))]
    rgb = np.zeros((size, size, 3))
    for (a0, c0), (a1, c1) in zip(stops, stops[1:]):
        m = (v >= a0) & (v <= a1)
        f = ((v - a0) / (a1 - a0))[m][:, None]
        rgb[m] = np.array(c0) * (1 - f) + np.array(c1) * f
    alpha = np.clip(v * 1.6, 0, 1) * 255
    out = np.dstack([rgb, alpha]).astype(np.uint8)
    return Image.fromarray(out, "RGBA")



def premultiplied(img):
    """Glow textures are added on top of the scene (Minecraft's "eyes" layer adds colour and ignores see-through),
    so a pixel's colour must already be dimmed by how see-through it is, or invisible pixels would glow."""
    px = img.load()
    res = Image.new("RGBA", img.size)
    rp = res.load()
    for x in range(img.width):
        for y in range(img.height):
            cr, cg, cb, ca = px[x, y]
            rp[x, y] = (cr * ca // 255, cg * ca // 255, cb * ca // 255, ca)
    return res


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    m, _ = sheet(False)
    m.save(OUT / "solace_shade.png")
    m, g = sheet(True)
    m.save(OUT / "solace_shade_negative.png")
    premultiplied(g).save(OUT / "solace_shade_negative_glow.png")
    outline_tex().save(OUT / "outline.png")
    for i, (reach, veins, glow) in enumerate(((0.95, 0.9, 0.7), (1.0, 0.6, 0.5))):
        premultiplied(plasma(256, 11 + i * 17, reach, veins, glow)).save(OUT / f"orb_plasma_{i}.png")
    print("Textures written to", OUT)
