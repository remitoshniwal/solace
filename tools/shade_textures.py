"""
Paints the textures for Solace's Blockbench model (tools/models/solace.bbmodel) and the Lantern orb.
Run:  python3 tools/shade_textures.py

One 256x256 sheet holds every piece of the model; the negative form uses the same layout with different colours,
so the model just swaps sheets. "_glow" sheets are drawn on top, glowing (they make edges and flames shine in the dark).

Sheet layout (x, y, width, height), shared with tools/models/build_solace.js:
  skin      (0, 0, 64, 64)      the body, wrapped round the torso and limbs
  strands   (64, 0, 192, 64)    6 wisps, 32x64 each, rising from the base
  cloak     (0, 64, 128, 128)   one tattered sheet: the normal form's cloak, the negative's long robe
  spike     (128, 64, 32, 64)   shoulder spikes, base at the top, tip at the bottom
  bone      (160, 64, 32, 64)   claws, the same way round
  band      (192, 64, 64, 32)   wrapping band (black in the negative)
  robe      (192, 96, 64, 32)   short tattered over-skirt (white in the negative)
  curls     (128, 128, 128, 64) 2 curling tendrils, 64x64 each
  flames    (0, 192, 128, 64)   4 flames, 32x64 each (negative only)
  blades    (128, 192, 128, 64) 2 curved blades, 64x64 each (negative only)
"""
import math
import random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

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


def wisp(w, h, rng, body, light, rim=None):
    """One wisp: a bundle of tapering strands rising from the bottom, drifting sideways at the top."""
    img = canvas(w, h)
    d = ImageDraw.Draw(img)
    S = SS
    lean = rng.uniform(-0.35, 0.35) * w
    for k in range(rng.randint(5, 8)):
        bx = w * 0.5 + rng.uniform(-0.18, 0.18) * w
        top = (w * 0.5 + lean + rng.uniform(-0.3, 0.3) * w, rng.uniform(0.0, 0.25) * h)
        mid = (bx + rng.uniform(-0.25, 0.25) * w, h * rng.uniform(0.45, 0.65))
        width = rng.uniform(0.12, 0.3) * w
        stroke(d, (bx * S, h * S), (mid[0] * S, mid[1] * S), (top[0] * S, top[1] * S), width * S, 0.5 * S, body)
        if light:
            stroke(d, ((bx + 0.5) * S, h * S), ((mid[0] + 1) * S, mid[1] * S), (top[0] * S, top[1] * S), width * 0.18 * S, 0.3 * S, light)
    if rim:
        edge = img.filter(ImageFilter.FIND_EDGES).split()[3]
        glow = Image.new("RGBA", img.size, rim[:3] + (0,))
        glow.putalpha(edge.point(lambda a: min(255, a * 2) if a > 30 else 0).filter(ImageFilter.GaussianBlur(S * 0.6)).point(lambda a: int(a * rim[3] / 255)))
        return shrink(img), shrink(glow)
    return shrink(img), None


def ragged_strip(w, h, rng, color, edge_color=None):
    """A long cloth strip with a torn, uneven bottom and slightly frayed sides."""
    img = canvas(w, h)
    d = ImageDraw.Draw(img)
    S = SS
    left, right = [], []
    for i in range(12):
        y = h * i / 11
        left.append((w * (0.12 + rng.uniform(-0.06, 0.06) + 0.05 * i / 11) * S, y * S))
        right.append((w * (0.88 + rng.uniform(-0.06, 0.06) - 0.05 * i / 11) * S, y * S))
    bottom = []
    n = rng.randint(3, 5)
    for i in range(n * 2 + 1):
        x = w * (0.2 + 0.6 * i / (n * 2))
        y = h * (0.78 + (0.2 if i % 2 == 0 else 0.02) * rng.uniform(0.6, 1.0))
        bottom.append((x * S, y * S))
    d.polygon(left + bottom[::-1][::-1] + right[::-1], fill=color)
    if edge_color:
        for x0 in (0.14, 0.86):
            stroke(d, (w * x0 * S, 0), (w * x0 * S, h * 0.5 * S), (w * (x0 + rng.uniform(-0.04, 0.04)) * S, h * 0.8 * S), 1.2 * S, 0.4 * S, edge_color)
    # a few darker folds
    for _ in range(3):
        x = rng.uniform(0.3, 0.7) * w
        stroke(d, (x * S, 0), ((x + rng.uniform(-2, 2)) * S, h * 0.4 * S), ((x + rng.uniform(-3, 3)) * S, h * 0.75 * S), 1.0 * S, 0.3 * S,
               tuple(max(0, c - 18) for c in color[:3]) + (255,))
    return shrink(img)


def shard(w, h, rng, color, edge=None):
    """A spiky cloak shard: a cluster of sharp points fanning upward."""
    img = canvas(w, h)
    d = ImageDraw.Draw(img)
    S = SS
    for k in range(rng.randint(3, 5)):
        base = w * rng.uniform(0.3, 0.7)
        tip = (w * rng.uniform(0.05, 0.95), h * rng.uniform(0.0, 0.35))
        bw = w * rng.uniform(0.15, 0.3)
        d.polygon([((base - bw) * S, h * S), (tip[0] * S, tip[1] * S), ((base + bw) * S, h * S)], fill=color)
        if edge:
            d.line([((base - bw) * S, h * S), (tip[0] * S, tip[1] * S)], fill=edge, width=int(S * 0.8))
    return shrink(img)


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


def blade(w, h, rng, color, edge):
    """A long curved blade, like a scythe growing from the arm."""
    img = canvas(w, h)
    d = ImageDraw.Draw(img)
    S = SS
    outer = [bezier((w * 0.15, h * 0.05), (w * 1.05, h * 0.4), (w * 0.35, h * 0.98), i / 30) for i in range(31)]
    inner = [bezier((w * 0.15, h * 0.05), (w * 0.75, h * 0.45), (w * 0.35, h * 0.98), i / 30) for i in range(31)]
    d.polygon([(x * S, y * S) for x, y in outer] + [(x * S, y * S) for x, y in inner[::-1]], fill=color)
    d.line([(x * S, y * S) for x, y in outer], fill=edge, width=int(S * 1.2))
    return shrink(img)


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


def cloak_sheet(w, h, rng, color, edge, holes=True):
    """One wide tattered cloth: solid at the top, torn into long ragged tails towards the hem, a few holes."""
    img = canvas(w, h)
    d = ImageDraw.Draw(img)
    S = SS
    pts = [(0, 0), (w * S, 0)]
    x = w
    hem = []
    while x > 0:  # walk the hem right to left: long tails and short tears
        tail = rng.uniform(0.86, 1.0) if rng.random() < 0.45 else rng.uniform(0.6, 0.8)
        width = rng.uniform(3, 9)
        hem.append((x * S, h * rng.uniform(0.5, 0.62) * S))
        hem.append(((x - width / 2) * S, h * tail * S))
        x -= width
    hem.append((0, h * 0.55 * S))
    d.polygon(pts + hem, fill=color)
    # folds: long darker and lighter streaks
    for _ in range(int(w / 3)):
        x0 = rng.uniform(0, w)
        c = tuple(max(0, min(255, v + rng.choice((-14, -8, 10, 16)))) for v in color[:3]) + (255,)
        stroke(d, (x0 * S, 0), ((x0 + rng.uniform(-2, 2)) * S, h * 0.5 * S), ((x0 + rng.uniform(-3, 3)) * S, h * 0.9 * S), rng.uniform(0.5, 1.6) * S, 0.3 * S, c)
    if edge:  # frayed light edges along the tears
        for i in range(1, len(hem) - 1):
            d.line([hem[i - 1], hem[i]], fill=edge, width=int(S * 0.9))
    if holes:
        for _ in range(5):
            cx, cy = rng.uniform(0.1, 0.9) * w, rng.uniform(0.35, 0.6) * h
            r = rng.uniform(1.0, 2.6)
            d.ellipse([(cx - r) * S, (cy - r * 1.8) * S, (cx + r) * S, (cy + r * 1.8) * S], fill=(0, 0, 0, 0))
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


def sheet(negative, seed=7):
    rng = random.Random(seed)
    main = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    glow = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    if negative:
        body, light, rim = (236, 238, 242, 255), (255, 255, 255, 255), (255, 255, 255, 230)
        robe, robe_edge = (14, 14, 18, 255), (230, 230, 236, 255)
        tile = body_tile(64, 64, rng, (232, 234, 238), (205, 207, 214))
    else:
        body, light, rim = (12, 12, 15, 255), (44, 44, 52, 255), (200, 205, 220, 120)
        robe, robe_edge = (11, 11, 14, 255), (52, 52, 62, 255)
        tile = body_tile(64, 64, rng, (16, 16, 19), (32, 32, 38))
    main.paste(tile, (0, 0))
    for i in range(6):
        w_img, w_glow = wisp(32, 64, rng, body, light, rim)
        main.alpha_composite(w_img, (64 + i * 32, 0))
        if w_glow:
            glow.alpha_composite(w_glow, (64 + i * 32, 0))
    main.alpha_composite(cloak_sheet(128, 128, rng, robe, robe_edge), (0, 64))
    if negative:
        main.paste(gradient_strip(32, 64, (235, 236, 240), (255, 255, 255), rng), (128, 64))   # spikes (unused)
        main.paste(gradient_strip(32, 64, (225, 226, 232), (255, 255, 255), rng), (160, 64))   # claws and bone
    else:
        main.paste(gradient_strip(32, 64, (14, 14, 17), (40, 40, 48), rng), (128, 64))         # spikes: black, edges catch a little light
        main.paste(gradient_strip(32, 64, (20, 19, 23), (120, 116, 124), rng), (160, 64))     # claws: pale at the tips
    band = Image.new("RGBA", (64, 32), (12, 12, 16, 255) if negative else (22, 22, 26, 255))
    bd = ImageDraw.Draw(band)
    for y in range(0, 32, 5):
        bd.line([(0, y), (63, y + 3)], fill=(30, 30, 36, 255))
    main.paste(band, (192, 64))
    # Short over-skirt for the negative's robe: white tatters over the black.
    main.alpha_composite(cloak_sheet(64, 32, rng, (238, 238, 242, 255) if negative else robe, (255, 255, 255, 255) if negative else None, holes=False), (192, 96))
    for i in range(2):
        img = canvas(64, 64)
        d = ImageDraw.Draw(img)
        for k in range(4):
            cx = rng.uniform(0.3, 0.7) * 64
            stroke(d, (cx * SS, 64 * SS), (rng.uniform(0, 64) * SS, 30 * SS), (rng.uniform(10, 54) * SS, rng.uniform(2, 14) * SS), 7 * SS, 0.5 * SS, body)
        main.alpha_composite(shrink(img), (128 + i * 64, 128))
    for i in range(4):
        f = flame(32, 64, rng)
        if negative:
            main.alpha_composite(f, (i * 32, 192))
            glow.alpha_composite(f, (i * 32, 192))
    for i in range(2):
        b = blade(64, 64, rng, (245, 245, 250, 255), (255, 255, 255, 255))
        if negative:
            main.alpha_composite(b, (128 + i * 64, 192))
            glow.alpha_composite(b, (128 + i * 64, 192))
    if negative:
        # In the negative, the body itself shines softly.
        g = tile.copy()
        g.putalpha(90)
        glow.alpha_composite(g, (0, 0))
    return main, glow


# ---------- The Lantern orb (from the energy burst in reference image 4) ----------

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


def corona(size, seed):
    """Light breaking outward in uneven rays, cyan at the edges."""
    rng = random.Random(seed)
    img = radial(size, [(0, (255, 255, 255, 255)), (0.18, (210, 255, 255, 230)), (0.5, (60, 200, 220, 110)), (1.0, (0, 60, 80, 0))])
    rays = Image.new("RGBA", (size * SS, size * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(rays)
    c = size * SS / 2
    for _ in range(26):
        a = rng.uniform(0, 2 * math.pi)
        ln = rng.uniform(0.45, 0.98) * c
        w = rng.uniform(0.01, 0.05) * c
        d.polygon([(c + math.cos(a + 1.57) * w, c + math.sin(a + 1.57) * w), (c + math.cos(a) * ln, c + math.sin(a) * ln),
                   (c + math.cos(a - 1.57) * w, c + math.sin(a - 1.57) * w)], fill=(190, 250, 255, rng.randint(70, 160)))
    rays = shrink(rays.filter(ImageFilter.GaussianBlur(SS * 1.5)))
    img.alpha_composite(rays)
    return img


def streak(w, h):
    """One flying spark: a thin bright dash."""
    img = canvas(w, h)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([w * 0.38 * SS, 0, w * 0.62 * SS, h * SS], radius=w * 0.2 * SS, fill=(255, 255, 255, 255))
    return shrink(img.filter(ImageFilter.GaussianBlur(SS * 0.7)))


def smoke(size, seed):
    """Dark, curling smoke around the orb (drawn normally, not glowing)."""
    rng = random.Random(seed)
    img = canvas(size, size)
    d = ImageDraw.Draw(img)
    for _ in range(9):
        cx, cy = rng.uniform(0.25, 0.75) * size, rng.uniform(0.25, 0.75) * size
        r = rng.uniform(0.12, 0.3) * size
        d.ellipse([(cx - r) * SS, (cy - r) * SS, (cx + r) * SS, (cy + r) * SS], fill=(8, 12, 16, rng.randint(60, 140)))
    return shrink(img.filter(ImageFilter.GaussianBlur(SS * 3)))


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
    m, g = sheet(False)
    m.save(OUT / "solace_shade.png")
    premultiplied(g).save(OUT / "solace_shade_glow.png")
    m, g = sheet(True)
    m.save(OUT / "solace_shade_negative.png")
    premultiplied(g).save(OUT / "solace_shade_negative_glow.png")
    premultiplied(radial(64, [(0, (255, 255, 255, 255)), (0.35, (235, 255, 255, 255)), (0.7, (150, 240, 255, 120)), (1.0, (40, 160, 200, 0))])).save(OUT / "orb_core.png")
    premultiplied(corona(128, 3)).save(OUT / "orb_corona.png")
    premultiplied(radial(64, [(0, (120, 230, 245, 140)), (0.6, (30, 140, 170, 60)), (1.0, (0, 40, 60, 0))])).save(OUT / "orb_haze.png")
    premultiplied(streak(8, 32)).save(OUT / "orb_streak.png")
    smoke(64, 5).save(OUT / "orb_smoke.png")
    print("Textures written to", OUT)
