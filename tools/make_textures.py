"""
Draws Solace's textures (64x64 Minecraft skin layout) with simple pixel art.
Run:  python3 tools/make_textures.py
Want a different look? Change the colors below, or paint over the PNGs in any image editor
(they use the normal Minecraft player-skin layout, so skin editors like Blockbench or NovaSkin work too).
"""
import random
from pathlib import Path
from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/solace/textures/entity"
LOGO = Path(__file__).resolve().parent.parent / "src/main/resources/solace_logo.png"


def faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
    }


def fill_box(img, u, v, w, h, d, color, noise=8, alpha=255, rng=None):
    rng = rng or random.Random(1)
    px = img.load()
    for (x0, y0, fw, fh) in faces(u, v, w, h, d).values():
        for x in range(x0, x0 + fw):
            for y in range(y0, y0 + fh):
                n = rng.randint(-noise, noise)
                px[x, y] = tuple(max(0, min(255, c + n)) for c in color) + (alpha,)


def paint(img, x, y, color, alpha=255):
    img.load()[x, y] = tuple(color) + (alpha,)


def figure(name, skin, hair, cloak, pants, eyes, eye_alpha=255, slim=False, alpha=255, cracks=None, tears=None, seed=1):
    rng = random.Random(seed)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    arm_w = 3 if slim else 4
    # Base layer
    fill_box(img, 0, 0, 8, 8, 8, skin, 6, alpha, rng)            # head
    fill_box(img, 16, 16, 8, 12, 4, cloak, 10, alpha, rng)       # body
    fill_box(img, 40, 16, arm_w, 12, 4, cloak, 10, alpha, rng)   # right arm
    fill_box(img, 32, 48, arm_w, 12, 4, cloak, 10, alpha, rng)   # left arm
    fill_box(img, 0, 16, 4, 12, 4, pants, 8, alpha, rng)         # right leg
    fill_box(img, 16, 48, 4, 12, 4, pants, 8, alpha, rng)        # left leg
    px = img.load()
    # Hands are skin coloured (bottom 3 rows of the arm fronts)
    for (u, v) in ((40, 16), (32, 48)):
        f = faces(u, v, arm_w, 12, 4)
        for key in ("front", "right", "left", "back"):
            x0, y0, fw, fh = f[key]
            for x in range(x0, x0 + fw):
                for y in range(y0 + fh - 3, y0 + fh):
                    px[x, y] = tuple(skin) + (alpha,)
    # Hair: top of head, back, sides, and a fringe on the face
    f = faces(0, 0, 8, 8, 8)
    for key in ("top", "back"):
        x0, y0, fw, fh = f[key]
        for x in range(x0, x0 + fw):
            for y in range(y0, y0 + fh):
                px[x, y] = tuple(c + rng.randint(-6, 6) for c in hair) + (alpha,)
    for key in ("right", "left"):
        x0, y0, fw, fh = f[key]
        for x in range(x0, x0 + fw):
            for y in range(y0, y0 + 5):
                px[x, y] = tuple(c + rng.randint(-6, 6) for c in hair) + (alpha,)
    fx, fy = 8, 8  # face front top-left
    for x in range(fx, fx + 8):
        for y in range(fy, fy + 2):
            px[x, y] = tuple(hair) + (alpha,)
    px[fx, fy + 2] = tuple(hair) + (alpha,)
    px[fx + 7, fy + 2] = tuple(hair) + (alpha,)
    # Eyes (row 4 of the face)
    for ex in (fx + 1, fx + 2, fx + 5, fx + 6):
        paint(img, ex, fy + 4, eyes, eye_alpha)
    if tears:
        for ex in (fx + 2, fx + 5):
            for ty in range(fy + 5, fy + 8):
                paint(img, ex, ty, tears, alpha)
    # Cloak trim down the middle of the body
    for y in range(20, 32):
        px[20, y] = tuple(max(0, c - 25) for c in cloak) + (alpha,)
        px[23, y] = tuple(max(0, c - 25) for c in cloak) + (alpha,)
    # Hood (hat overlay layer): frames the face
    hood = tuple(max(0, c - 15) for c in cloak)
    hf = faces(32, 0, 8, 8, 8)
    for key in ("top", "back", "right", "left"):
        x0, y0, fw, fh = hf[key]
        for x in range(x0, x0 + fw):
            for y in range(y0, y0 + fh):
                if key in ("right", "left") and y > y0 + 6:
                    continue
                px[x, y] = tuple(c + rng.randint(-6, 6) for c in hood) + (alpha,)
    x0, y0, fw, fh = hf["front"]
    for x in range(x0, x0 + fw):
        px[x, y0] = hood + (alpha,)
    for y in range(y0, y0 + 7):
        px[x0, y] = hood + (alpha,)
        px[x0 + 7, y] = hood + (alpha,)
    # Jacket overlay: cloak hem
    jf = faces(16, 32, 8, 12, 4)
    x0, y0, fw, fh = jf["front"]
    for x in range(x0, x0 + fw):
        for y in range(y0 + 9, y0 + fh):
            px[x, y] = hood + (alpha,)
    if cracks:
        for _ in range(70):
            x, y = rng.randint(0, 63), rng.randint(0, 63)
            if px[x, y][3] > 0:
                px[x, y] = tuple(cracks) + (alpha,)
    img.save(OUT / name)
    return img


def eyes_only(name, color, slim=False):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for ex in (9, 10, 13, 14):
        paint(img, ex, 12, color, 255)
    img.save(OUT / name)


# ---------- Solace's body: a man in a tailored suit (custom model, see SolaceModels.createBody) ----------
# Layout: head (0,0), hair (32,0), body (16,16), arms (40,16, both arms share it), legs (0,16, both legs share it),
# shoe toes (0,32), jacket hem (16,32). All 64x64.

SUIT = dict(suit=(38, 40, 52), suit_dark=(27, 28, 37), lapel=(31, 32, 43), shirt=(232, 232, 228), tie=(24, 22, 30),
            skin=(226, 196, 166), skin_shade=(206, 174, 144), lips=(176, 128, 116), hair=(22, 22, 27), grey=(112, 112, 118),
            brow=(32, 28, 30), white=(238, 238, 236), pupil=(44, 32, 28), trousers=(33, 35, 46), crease=(25, 26, 34),
            shoe=(14, 14, 16), shoe_shine=(54, 54, 62))


def fill(px, rect, color, rng, noise=4, alpha=255):
    x0, y0, w, h = rect
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            n = rng.randint(-noise, noise)
            px[x, y] = tuple(max(0, min(255, c + n)) for c in color) + (alpha,)


def box(px, u, v, w, h, d, color, rng, noise=4):
    for rect in faces(u, v, w, h, d).values():
        fill(px, rect, color, rng, noise)


def suit(name, c, uneasy=False, seed=11):
    """Paints the suited man. uneasy = later chapters: paler, tired eyes, tie pulled loose, hair out of place."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    skin = (214, 192, 172) if uneasy else c["skin"]
    # Head: skin, hair on top/back/upper sides, ears.
    box(px, 0, 0, 8, 8, 8, skin, rng, 3)
    f = faces(0, 0, 8, 8, 8)
    fill(px, f["top"], c["hair"], rng, 3)
    for key in ("right", "left"):
        x0, y0, w, h = f[key]
        fill(px, (x0, y0, w, 3), c["hair"], rng, 3)
        fill(px, (x0 + 3, y0 + 4, 2, 2), c["skin_shade"], rng, 2)          # ear
        px[x0 + (1 if key == "right" else 6), y0 + 3] = c["grey"] + (255,)  # grey at the temples
    x0, y0, w, h = f["back"]
    fill(px, (x0, y0, w, 6), c["hair"], rng, 3)
    # Face (front of head)
    fx, fy = 8, 8
    for x in range(8):
        px[fx + x, fy] = c["hair"] + (255,)
        px[fx + x, fy + 1] = c["hair"] + (255,)
    px[fx, fy + 2] = c["hair"] + (255,)
    px[fx + 7, fy + 2] = c["hair"] + (255,)
    if uneasy:
        px[fx + 3, fy + 2] = c["hair"] + (255,)  # a strand fallen out of place
    for x in (1, 2, 5, 6):
        px[fx + x, fy + 3] = c["brow"] + (255,)
    for x, col in ((1, c["white"]), (2, c["pupil"]), (5, c["pupil"]), (6, c["white"])):
        px[fx + x, fy + 4] = col + (255,)
    px[fx + 4, fy + 5] = c["skin_shade"] + (255,)  # a hint of a nose
    if uneasy:
        for x in (1, 2, 5, 6):
            px[fx + x, fy + 5] = (176, 150, 142, 255)  # dark circles
    for x in (3, 4):
        px[fx + x, fy + 6] = c["lips"] + (255,)  # a thin, calm mouth
    px[fx, fy + 7] = c["skin_shade"] + (255,)
    px[fx + 7, fy + 7] = c["skin_shade"] + (255,)

    # Hair layer: neat, swept back, a little volume on top.
    h = faces(32, 0, 8, 8, 8)
    fill(px, h["top"], c["hair"], rng, 4)
    x0, y0, w, hh = h["back"]
    fill(px, (x0, y0, w, 5), c["hair"], rng, 4)
    for key in ("right", "left"):
        x0, y0, w, hh = h[key]
        fill(px, (x0, y0, w, 2), c["hair"], rng, 4)
    x0, y0, w, hh = h["front"]
    fill(px, (x0, y0, w, 1), c["hair"], rng, 4)

    # Body: jacket, white shirt in a V, dark tie, one button, pocket square.
    box(px, 16, 16, 8, 12, 4, c["suit"], rng, 3)
    b = faces(16, 16, 8, 12, 4)
    bx, by, _, _ = b["front"]
    tie_x = 4 if uneasy else 3
    for y in range(0, 6):
        half = max(0, 2 - y // 2)
        for x in range(3 - half, 5 + half):
            px[bx + x, by + y] = c["shirt"] + (255,)
        px[bx + 2 - half, by + y] = c["lapel"] + (255,)
        px[bx + 5 + half, by + y] = c["lapel"] + (255,)
    for y in range(1 if not uneasy else 2, 8):
        px[bx + tie_x, by + y] = c["tie"] + (255,)
        if not uneasy:
            px[bx + tie_x + 1, by + y] = c["tie"] + (255,)
    if uneasy:
        px[bx + 3, by] = skin + (255,)  # top button open
        px[bx + 4, by] = skin + (255,)
    px[bx + 3, by + 8] = (12, 12, 14, 255)  # button
    px[bx + 6, by + 3] = c["shirt"] + (255,)  # pocket square
    for y in range(9, 12):
        px[bx + 3, by + y] = c["suit_dark"] + (255,)
        px[bx + 4, by + y] = c["suit_dark"] + (255,)
    x0, y0, w, hh = b["back"]
    for y in range(y0, y0 + hh):
        px[x0 + 4, y] = c["suit_dark"] + (255,)  # centre seam
    x0, y0, w, hh = b["top"]
    fill(px, (x0 + 2, y0, 4, 2), c["shirt"], rng, 2)  # shirt collar showing at the neck

    # Arms: sleeves, a white cuff, hands.
    box(px, 40, 16, 4, 12, 4, c["suit"], rng, 3)
    a = faces(40, 16, 4, 12, 4)
    for key in ("front", "back", "right", "left"):
        x0, y0, w, hh = a[key]
        fill(px, (x0, y0 + 9, w, 1), c["shirt"], rng, 2)
        fill(px, (x0, y0 + 10, w, 2), skin, rng, 2)
    fill(px, a["bottom"], skin, rng, 2)

    # Legs: trousers with a crease, black shoes.
    box(px, 0, 16, 4, 12, 4, c["trousers"], rng, 3)
    l = faces(0, 16, 4, 12, 4)
    x0, y0, w, hh = l["front"]
    for y in range(y0, y0 + 10):
        px[x0 + 1, y] = c["crease"] + (255,)
    for key in ("front", "back", "right", "left"):
        x0, y0, w, hh = l[key]
        fill(px, (x0, y0 + 10, w, 2), c["shoe"], rng, 2)
        px[x0 + 1, y0 + 10] = c["shoe_shine"] + (255,)
    fill(px, l["bottom"], c["shoe"], rng, 2)
    box(px, 0, 32, 4, 2, 1, c["shoe"], rng, 2)        # toes
    t = faces(0, 32, 4, 2, 1)
    px[t["top"][0] + 1, t["top"][1]] = c["shoe_shine"] + (255,)

    # Jacket hem: hangs over the hips and swings when he walks. A vent at the back.
    box(px, 16, 32, 8, 4, 4, c["suit"], rng, 3)
    j = faces(16, 32, 8, 4, 4)
    x0, y0, w, hh = j["front"]
    for y in range(y0, y0 + hh):
        px[x0 + 3, y] = c["suit_dark"] + (255,)
        px[x0 + 4, y] = c["suit_dark"] + (255,)
    x0, y0, w, hh = j["back"]
    for y in range(y0 + 1, y0 + hh):
        px[x0 + 4, y] = c["suit_dark"] + (255,)
    img.save(OUT / name)
    return img


def negative(src, name):
    """The photo-negative version: black and white with the brightness flipped. Near-black skin, a white suit, white hair."""
    img = src.copy()
    px = img.load()
    for x in range(img.width):
        for y in range(img.height):
            r, g, b, a = px[x, y]
            if a:
                v = 255 - int(0.3 * r + 0.59 * g + 0.11 * b)
                px[x, y] = (max(0, v - 6), max(0, v - 4), min(255, v + 4), a)  # a faint cold tint
    img.save(OUT / name)


def veins(prefix, stages=4, seed=21):
    """Dark veins creeping from his collar and cuffs, a stage further every couple of chapters. Only on skin."""
    rng = random.Random(seed)
    f = faces(0, 0, 8, 8, 8)
    a = faces(40, 16, 4, 12, 4)
    # Where veins start in each stage: (x, y, how far they can wander up)
    starts = [
        [(f["right"][0] + 2, f["right"][1] + 7, 3), (f["left"][0] + 5, f["left"][1] + 7, 3), (a["front"][0] + 1, a["front"][1] + 11, 2)],
        [(f["back"][0] + 2, f["back"][1] + 7, 2), (f["back"][0] + 6, f["back"][1] + 7, 2), (f["front"][0] + 0, f["front"][1] + 7, 2),
         (a["back"][0] + 2, a["back"][1] + 11, 2), (a["right"][0] + 1, a["right"][1] + 11, 2)],
        [(f["front"][0] + 7, f["front"][1] + 7, 3), (f["front"][0] + 1, f["front"][1] + 7, 3), (f["right"][0] + 5, f["right"][1] + 7, 4),
         (a["left"][0] + 2, a["left"][1] + 11, 2)],
        [(f["front"][0] + 2, f["front"][1] + 6, 3), (f["front"][0] + 5, f["front"][1] + 6, 3), (f["left"][0] + 2, f["left"][1] + 7, 5)],
    ]
    # Skin areas veins may cross: the face below the hairline, the sides and back of the neck, the hands.
    allowed = set()
    for key in ("front", "right", "left", "back"):
        x0, y0, w, h = f[key]
        top = 2 if key == "front" else 4 if key == "back" else 3
        for x in range(x0, x0 + w):
            for y in range(y0 + top, y0 + h):
                allowed.add((x, y))
        x0, y0, w, h = a[key]
        for x in range(x0, x0 + w):
            for y in range(y0 + 10, y0 + h):
                allowed.add((x, y))
    eyes = {(f["front"][0] + x, f["front"][1] + 4) for x in (1, 2, 5, 6)}
    drawn = []
    for stage in range(stages):
        for (x, y, reach) in starts[stage]:
            cx, cy = x, y
            for _ in range(reach * 3):
                if (cx, cy) in allowed and (cx, cy) not in eyes:
                    drawn.append((cx, cy))
                cy -= 1 if rng.random() < 0.6 else 0
                cx += rng.choice((-1, 0, 0, 1))
        if stage == stages - 1:
            # The end: the veins reach his eyes.
            for (ex, ey) in eyes:
                drawn.append((ex, ey + 1))
        img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        p = img.load()
        for (vx, vy) in drawn:
            p[vx, vy] = (46, 18, 44, 235)
        img.save(OUT / (prefix + str(stage + 1) + ".png"))


def lantern(name, mass, edge, alpha, seed=6):
    """The Lantern's see-through tendril mass. Everything uses the same noisy colour, so the UVs don't matter."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    for x in range(64):
        for y in range(64):
            t = rng.random()
            c = edge if t < 0.18 else mass
            n = rng.randint(-14, 14)
            a = max(40, min(255, alpha + rng.randint(-40, 30)))
            px[x, y] = tuple(max(0, min(255, v + n)) for v in c) + (a,)
    img.save(OUT / name)


def core(name, inner, outer, seed=7):
    """The bright core. Drawn glowing (full brightness), with a soft edge."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()
    for x in range(32):
        for y in range(32):
            c = inner if rng.random() < 0.6 else outer
            px[x, y] = tuple(max(0, min(255, v + rng.randint(-10, 10))) for v in c) + (rng.randint(170, 235),)
    img.save(OUT / name)


def logo():
    size = 256
    img = Image.new("RGBA", (size, size), (8, 10, 16, 255))
    d = ImageDraw.Draw(img)
    rng = random.Random(7)
    for _ in range(900):  # static
        x, y = rng.randint(0, size - 1), rng.randint(0, size - 1)
        g = rng.randint(10, 40)
        d.point((x, y), fill=(g, g, g + 6, 255))
    # hooded face
    d.rectangle((64, 52, 192, 196), fill=(70, 84, 102, 255))
    d.rectangle((84, 80, 172, 196), fill=(214, 218, 226, 255))
    d.rectangle((84, 80, 172, 100), fill=(200, 206, 214, 255))
    for ex in (100, 144):
        d.rectangle((ex, 134, ex + 14, 142), fill=(127, 232, 255, 255))
        d.rectangle((ex - 4, 130, ex + 18, 146), outline=(60, 160, 190, 120))
    d.rectangle((0, 214, size, 226), fill=(16, 20, 30, 255))
    img.save(LOGO)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    normal = suit("solace.png", SUIT)
    suit("solace_uneasy.png", SUIT, uneasy=True)
    negative(normal, "solace_negative.png")
    veins("solace_veins_")
    figure("watcher.png", skin=(200, 204, 210), hair=(150, 140, 130), cloak=(185, 190, 198), pants=(165, 170, 178), eyes=(0, 0, 0),
           slim=True, alpha=150, seed=4)
    lantern("lantern.png", mass=(46, 196, 190), edge=(150, 255, 245), alpha=120)
    lantern("lantern_hollow.png", mass=(14, 18, 22), edge=(70, 10, 14), alpha=200, seed=8)
    core("lantern_core.png", inner=(235, 255, 250), outer=(120, 255, 235))
    core("lantern_core_hollow.png", inner=(255, 90, 70), outer=(200, 10, 10), seed=9)
    eyes_only("solace_eyes.png", (127, 232, 255))
    logo()
    print("Textures written to", OUT)
