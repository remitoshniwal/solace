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


# ---------- Solace's body: a tall headless figure in a long coat (custom model, see SolaceModels.createBody) ----------
# Each entry: (u, v, width, height, depth) of one box, matching the texOffs in SolaceModels.java.
BOX = dict(leg=(0, 0, 3, 15, 3), shoe=(0, 18, 3, 1, 5), waist=(16, 0, 6, 7, 3), chest=(34, 0, 8, 7, 4), pad=(16, 10, 3, 2, 5),
           collar_front=(16, 17, 5, 3, 1), collar_back=(16, 21, 5, 3, 1), collar_side=(0, 52, 1, 3, 6), void=(40, 11, 5, 1, 4),
           coat_back=(0, 24, 7, 9, 1), coat_front=(16, 25, 4, 9, 1), coat_side=(26, 25, 1, 9, 3), upper_arm=(34, 25, 2, 8, 2),
           forearm=(42, 25, 2, 7, 2), palm=(50, 25, 2, 2, 2), finger=(58, 25, 1, 3, 1), head=(0, 37, 7, 8, 7), eye=(28, 37, 1, 1, 1))

COAT = dict(coat=(30, 31, 40), coat_dark=(20, 21, 28), coat_light=(44, 46, 58), shirt=(232, 232, 228), tie=(18, 16, 22),
            skin=(224, 214, 206), skin_shade=(196, 184, 178), trousers=(26, 27, 35), crease=(18, 19, 25), shoe=(10, 10, 12),
            shoe_shine=(58, 58, 66), eye=(225, 250, 255), void=(2, 2, 3))


def fill(px, rect, color, rng, noise=4, alpha=255):
    x0, y0, w, h = rect
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            n = rng.randint(-noise, noise)
            px[x, y] = tuple(max(0, min(255, c + n)) for c in color) + (alpha,)


def paint_box(px, name, color, rng, noise=3):
    for rect in faces(*BOX[name]).values():
        fill(px, rect, color, rng, noise)


def face(name, key):
    return faces(*BOX[name])[key]


def suit(name, c, uneasy=False, seed=11):
    """Paints the headless figure. uneasy = later chapters: the coat more worn, the tie pulled loose, the hands paler."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    skin = (206, 198, 194) if uneasy else c["skin"]

    # Legs: trousers with a crease; long black shoes.
    paint_box(px, "leg", c["trousers"], rng)
    x0, y0, w, h = face("leg", "front")
    for y in range(y0, y0 + h):
        px[x0 + 1, y] = c["crease"] + (255,)
    paint_box(px, "shoe", c["shoe"], rng, 2)
    x0, y0, w, h = face("shoe", "top")
    for y in range(y0, y0 + h):
        px[x0 + 1, y] = c["shoe_shine"] + (255,)

    # Waist and chest: the coat, buttoned, with a white shirt V and a dark tie at the chest.
    paint_box(px, "waist", c["coat"], rng)
    x0, y0, w, h = face("waist", "front")
    for y in range(y0, y0 + h):
        px[x0 + 2, y] = c["coat_dark"] + (255,)  # where the coat closes
    for y in (y0 + 1, y0 + 4):
        px[x0 + 3, y] = (6, 6, 8, 255)  # buttons
    paint_box(px, "chest", c["coat"], rng)
    x0, y0, w, h = face("chest", "front")
    for y in range(h):
        half = max(0, 2 - y // 2)
        for x in range(3 - half, 5 + half):
            px[x0 + x, y0 + y] = c["shirt"] + (255,)
        px[x0 + 2 - half, y0 + y] = c["coat_dark"] + (255,)  # lapels
        px[x0 + 5 + half, y0 + y] = c["coat_dark"] + (255,)
    tie_x = 4 if uneasy else 3
    for y in range(0, h):
        px[x0 + tie_x, y0 + y] = c["tie"] + (255,)
        if not uneasy:
            px[x0 + tie_x + 1, y0 + y] = c["tie"] + (255,)
    x0, y0, w, h = face("chest", "back")
    for y in range(y0, y0 + h):
        px[x0 + 4, y] = c["coat_dark"] + (255,)

    # Shoulder pads: catch the light on top.
    paint_box(px, "pad", c["coat"], rng)
    fill(px, face("pad", "top"), c["coat_light"], rng, 3)

    # The high collar: coat outside, white rim on top, black inside. Nothing in it but the eyes.
    for part in ("collar_front", "collar_back", "collar_side"):
        paint_box(px, part, c["coat_dark"], rng, 2)
        fill(px, face(part, "top"), c["shirt"], rng, 2)
    # Inner sides of the collar walls (front wall's back face, back wall's front face, side walls' inner faces).
    fill(px, face("collar_front", "back"), c["void"], rng, 1)
    fill(px, face("collar_back", "front"), c["void"], rng, 1)
    fill(px, face("collar_side", "left"), c["void"], rng, 1)
    fill(px, face("collar_side", "right"), c["void"], rng, 1)
    paint_box(px, "void", c["void"], rng, 1)

    # The long coat panels, darker on the inside.
    for part in ("coat_back", "coat_front", "coat_side"):
        paint_box(px, part, c["coat"], rng)
    fill(px, face("coat_back", "front"), c["coat_dark"], rng, 2)
    fill(px, face("coat_front", "back"), c["coat_dark"], rng, 2)
    x0, y0, w, h = face("coat_back", "back")
    for y in range(y0 + 4, y0 + h):
        px[x0 + 3, y] = c["coat_dark"] + (255,)  # the vent

    # Arms: sleeves, a white cuff, pale hands with long fingers.
    paint_box(px, "upper_arm", c["coat"], rng)
    paint_box(px, "forearm", c["coat"], rng)
    for key in ("front", "back", "left", "right"):
        x0, y0, w, h = face("forearm", key)
        fill(px, (x0, y0 + h - 1, w, 1), c["shirt"], rng, 2)
    fill(px, face("forearm", "bottom"), skin, rng, 2)
    paint_box(px, "palm", skin, rng, 2)
    paint_box(px, "finger", skin, rng, 2)
    x0, y0, w, h = face("finger", "front")
    px[x0, y0 + h - 1] = c["skin_shade"] + (255,)

    # The head only exists in his negative form; here it's left blank. The eyes glow.
    paint_box(px, "head", (255, 255, 255), rng, 0)
    paint_box(px, "eye", c["eye"], rng, 0)
    img.save(OUT / name)
    return img


def negative(src, name):
    """The photo-negative: black and white with the brightness flipped. A white coat, black shirt, black hands.
    The head stays pure white."""
    img = src.copy()
    px = img.load()
    for x in range(img.width):
        for y in range(img.height):
            r, g, b, a = px[x, y]
            if a:
                v = 255 - int(0.3 * r + 0.59 * g + 0.11 * b)
                px[x, y] = (max(0, v - 6), max(0, v - 4), min(255, v + 4), a)  # a faint cold tint
    for rect in faces(*BOX["head"]).values():
        fill(px, rect, (255, 255, 255), random.Random(1), 0)
    img.save(OUT / name)


def skin_areas():
    """Where veins can go: the hands (and the cuffs' edge) and the white shirt at his chest."""
    spots = []
    for part in ("palm", "finger"):
        for rect in faces(*BOX[part]).values():
            x0, y0, w, h = rect
            spots += [(x, y) for x in range(x0, x0 + w) for y in range(y0, y0 + h)]
    for key in ("front", "back", "left", "right"):
        x0, y0, w, h = face("forearm", key)
        spots += [(x, y0 + h - 1) for x in range(x0, x0 + w)]
    x0, y0, w, h = face("chest", "front")
    for y in range(h):
        half = max(0, 2 - y // 2)
        spots += [(x0 + x, y0 + y) for x in range(3 - half, 5 + half)]
    for part in ("collar_front", "collar_back", "collar_side"):
        x0, y0, w, h = face(part, "top")
        spots += [(x, y) for x in range(x0, x0 + w) for y in range(y0, y0 + h)]
    return spots


def veins(prefix, stages=4, seed=21):
    """Dark veins creeping over his hands and up the white of his shirt and collar, a stage further every couple of chapters."""
    rng = random.Random(seed)
    spots = skin_areas()
    allowed = set(spots)
    drawn = []
    for stage in range(stages):
        for _ in range(4 + stage * 3):
            x, y = rng.choice(spots)
            for _ in range(rng.randint(2, 5)):
                if (x, y) in allowed:
                    drawn.append((x, y))
                x += rng.choice((-1, 0, 1))
                y += rng.choice((-1, 0, 1))
        img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        p = img.load()
        for (vx, vy) in drawn:
            p[vx, vy] = (46, 18, 44, 235)
        img.save(OUT / (prefix + str(stage + 1) + ".png"))


def negative_glow(name, seed=31):
    """What shines in his negative form (drawn glowing, added on top): the head, blazing white; white neon veins on his
    black hands; and faint light along the edges of the coat, like neon tubes."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    for rect in faces(*BOX["head"]).values():
        fill(px, rect, (255, 255, 255), rng, 0)
    hands = [s for s in skin_areas() if s[1] >= 25]  # hands and cuffs only (rows 25+)
    for (x, y) in hands:
        if rng.random() < 0.45:
            b = rng.randint(190, 255)
            px[x, y] = (b, b, min(255, b + 10), 255)

    def rim(part, level):
        for key, (x0, y0, fw, fh) in faces(*BOX[part]).items():
            if key in ("top", "bottom"):
                continue
            for x in range(x0, x0 + fw):
                for y in range(y0, y0 + fh):
                    if x in (x0, x0 + fw - 1) and px[x, y][3] == 0:
                        px[x, y] = (level, level, level + 8, 255)
    for part, level in (("chest", 120), ("waist", 100), ("coat_back", 110), ("coat_front", 110), ("coat_side", 90),
                        ("upper_arm", 100), ("forearm", 100), ("leg", 80), ("pad", 130)):
        rim(part, level)
    img.save(OUT / name)


def aura(name, seed=41):
    """The energy that flows around him in his negative form: soft white streaks on black (black = nothing is drawn).
    Tiles in every direction, because the game scrolls it."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 255))
    px = img.load()
    for _ in range(70):
        x, y = rng.randint(0, 63), rng.randint(0, 63)
        length = rng.randint(6, 20)
        peak = rng.randint(90, 230)
        for i in range(length):
            k = 1 - abs(i - length / 2) / (length / 2)  # brightest in the middle of each streak
            v = int(peak * k)
            xx, yy = (x + (i // 5)) % 64, (y + i) % 64
            old = px[xx, yy][0]
            px[xx, yy] = (min(255, old + v), min(255, old + v), min(255, old + v + 12), 255)
    img.save(OUT / name)


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
    normal = suit("solace.png", COAT)
    suit("solace_uneasy.png", COAT, uneasy=True)
    negative(normal, "solace_negative.png")
    veins("solace_veins_")
    negative_glow("solace_negative_glow.png")
    aura("solace_aura.png")
    figure("watcher.png", skin=(200, 204, 210), hair=(150, 140, 130), cloak=(185, 190, 198), pants=(165, 170, 178), eyes=(0, 0, 0),
           slim=True, alpha=150, seed=4)
    lantern("lantern.png", mass=(46, 196, 190), edge=(150, 255, 245), alpha=85)
    lantern("lantern_hollow.png", mass=(14, 18, 22), edge=(70, 10, 14), alpha=200, seed=8)
    core("lantern_core.png", inner=(235, 255, 250), outer=(120, 255, 235))
    core("lantern_core_hollow.png", inner=(255, 90, 70), outer=(200, 10, 10), seed=9)
    logo()
    print("Textures written to", OUT)
