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
    figure("solace.png", skin=(217, 220, 227), hair=(58, 62, 78), cloak=(159, 179, 200), pants=(91, 107, 125), eyes=(127, 232, 255), seed=1)
    figure("solace_uneasy.png", skin=(196, 199, 206), hair=(38, 40, 48), cloak=(74, 85, 102), pants=(52, 60, 72), eyes=(235, 240, 245),
           tears=(110, 112, 120), cracks=(12, 12, 16), seed=2)
    figure("solace_hollow.png", skin=(28, 22, 30), hair=(10, 8, 12), cloak=(20, 16, 22), pants=(14, 12, 16), eyes=(255, 40, 40),
           tears=(120, 0, 0), cracks=(140, 0, 0), seed=3)
    figure("watcher.png", skin=(200, 204, 210), hair=(150, 140, 130), cloak=(185, 190, 198), pants=(165, 170, 178), eyes=(0, 0, 0),
           slim=True, alpha=150, seed=4)
    eyes_only("solace_eyes.png", (127, 232, 255))
    eyes_only("solace_hollow_eyes.png", (255, 50, 50))
    logo()
    print("Textures written to", OUT)
