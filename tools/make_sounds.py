"""
Makes every one of Solace's own sounds from code: sine waves for tones, shaped noise for breath, paper and static.
Nothing is recorded or downloaded, so there are no licenses to worry about.
Run:  python3 tools/make_sounds.py        (needs numpy and soundfile: pip3 install soundfile)

Writes mono .ogg files into src/main/resources/assets/solace/sounds/ (mono, because Minecraft only fades a sound
with distance when it is mono). It also writes .wav copies and reel.wav (every sound in a row) into
build/sound-preview/, for listening on a computer that can't play .ogg.

Each sound is one function below. The numbers worth tweaking are at the top of each one. The random seed comes
from the file name, so running the script again gives exactly the same files unless you change something.
"""
import math
import zlib
from pathlib import Path

import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/solace/sounds"
PREVIEW = ROOT / "build/sound-preview"
SR = 44100  # samples per second

# How loud each sound is on average (RMS, in dB below full scale). Bigger negative = quieter.
LOUDNESS = {
    "loop": -24,     # hum and drone: always there, so quiet
    "oneshot": -20,  # the negative surge and fade, the Watcher vanishing, paper
    "soft": -24,     # flickers: often heard, so gentler
    "ui": -30,       # bond flame, Watcher ringing: barely there
}
PEAK = 10 ** (-1 / 20)  # never louder than 1 dB below the maximum


# ---------- Building blocks ----------

def seconds(n):
    return np.arange(n) / SR


def freqs(n):
    return np.fft.rfftfreq(n, 1 / SR)


def band(f, centre, width_oct):
    """How much of frequency f passes a soft band around centre (width in octaves)."""
    f = np.maximum(f, 1.0)
    return np.exp(-0.5 * (np.log2(f / centre) / width_oct) ** 2)


def lowpass(f, cutoff, steep=2):
    return 1 / np.sqrt(1 + (np.maximum(f, 0) / cutoff) ** (2 * steep))


def highpass(f, cutoff, steep=2):
    f = np.maximum(f, 1.0)
    return 1 / np.sqrt(1 + (cutoff / f) ** (2 * steep))


def noise(n, rng, shape=None):
    """Noise with a chosen tone colour (shape is a function of frequency). It repeats perfectly after n samples."""
    spec = rng.normal(size=n // 2 + 1) + 1j * rng.normal(size=n // 2 + 1)
    if shape is not None:
        spec *= shape(freqs(n))
    spec[0] = 0
    x = np.fft.irfft(spec, n)
    return x / (np.std(x) + 1e-12)


def filt(x, shape, wrap=False):
    """Filters a sound. wrap=True keeps it seamless for loops; otherwise silence is padded so nothing wraps round."""
    pad = 0 if wrap else SR // 2
    y = np.concatenate([x, np.zeros(pad)])
    y = np.fft.irfft(np.fft.rfft(y) * shape(freqs(len(y))), len(y))
    return y[:len(x)]


def bank(n, rng, low=120, high=11000, count=48):
    """A row of narrow noise bands. Mixing them with changing amounts makes a filter that moves over time."""
    centres = np.geomspace(low, high, count)
    width = math.log2(high / low) / (count - 1) * 0.7
    bands = np.stack([noise(n, rng, lambda f, c=c: band(f, c, width)) for c in centres])
    return centres, bands


def sweep(centres, bands, centre_curve, width_oct):
    """Plays a bank through a band whose centre follows centre_curve (one value per sample)."""
    gains = band(centres[:, None], centre_curve[None, :], width_oct)
    return (bands * gains).sum(axis=0)


def control(points, n):
    """A smooth curve through (time in seconds, value) points, one value per sample."""
    times, values = zip(*points)
    return np.interp(seconds(n), times, values)


def smooth(x, ms):
    k = max(1, int(SR * ms / 1000))
    return np.convolve(x, np.ones(k) / k, mode="same")


def envelope(n, attack, release, curve=2.0):
    """Fades in over `attack` seconds and dies away (curved) over the rest, ending at zero."""
    t = seconds(n)
    up = np.clip(t / max(attack, 1e-4), 0, 1)
    down = np.clip((t[-1] - t) / max(release, 1e-4), 0, 1) ** curve
    return np.minimum(up, 1) * down


def clicks(n, rng, rate_curve, length_ms=(0.5, 3.0)):
    """Sparse sharp ticks (the grain of static). rate_curve is ticks per second, per sample."""
    out = np.zeros(n)
    chance = rate_curve / SR
    where = np.nonzero(rng.random(n) < chance)[0]
    for i in where:
        m = int(SR * rng.uniform(*length_ms) / 1000)
        burst = rng.normal(size=m) * np.exp(-np.arange(m) / (m / 4 + 1)) * rng.uniform(0.2, 1.0)
        end = min(n, i + m)
        out[i:end] += burst[:end - i]
    return out


def buzz(n, f0_curve, harmonics=12):
    """An electric buzz (a stack of harmonics) whose pitch follows f0_curve."""
    phase = 2 * np.pi * np.cumsum(f0_curve) / SR
    return sum(np.sin(k * phase) / k ** 1.2 for k in range(1, harmonics + 1))


def reverb(x, rng, length=0.8, wet=0.25, dark=3000):
    """A small dark room: blends in a softly decaying copy of the sound. The echo carries on past the end."""
    m = int(SR * length)
    tail = rng.normal(size=m) * np.exp(-6.9 * np.arange(m) / m)
    tail = filt(tail, lambda f: lowpass(f, dark))
    tail /= np.sqrt(np.sum(tail ** 2)) + 1e-12
    size = len(x) + m
    wet_part = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(tail, size), size)
    dry = np.concatenate([x, np.zeros(m)])
    return dry * (1 - wet) + wet_part * wet * (np.std(x) / (np.std(wet_part[:len(x)]) + 1e-12))


def tone(t, f, amp=1.0, phase=0.0):
    return amp * np.sin(2 * np.pi * f * t + phase)


def tidy_end(x):
    """Cuts off the silent end of a sound, then fades the last bit so it never ends in a click."""
    loud = np.nonzero(np.abs(x) > np.max(np.abs(x)) * 0.001)[0]
    x = x[:loud[-1] + 1] if len(loud) else x
    k = min(len(x), int(SR * 0.02))
    x[-k:] *= np.linspace(1, 0, k)
    return x


def level(x, group):
    """Sets the average loudness for the sound's group, then gently rounds off any peak that is too loud."""
    rms = np.sqrt(np.mean(x ** 2)) + 1e-12
    x = x * (10 ** (LOUDNESS[group] / 20) / rms)
    over = np.abs(x) > PEAK * 0.7
    if over.any():
        knee = PEAK * 0.7
        x = np.where(over, np.sign(x) * (knee + (PEAK - knee) * np.tanh((np.abs(x) - knee) / (PEAK - knee))), x)
    return x


# ---------- The sounds ----------
# Loops: every tone's frequency fits a whole number of times into the loop length, so the end flows into the start.

def hum(rng, uneasy=False):
    """A low, soft hum near Solace. The uneasy version adds a sour, slowly beating note for the later chapters."""
    length = 6.0
    base = 55.0  # Hz, a low A. Laptop speakers can't play this low, so the overtones above it carry the hum.
    n = int(SR * length)
    t = seconds(n)
    x = (tone(t, base, 0.7) + tone(t, base + 1 / length, 0.45, 1.0) + tone(t, base * 2, 0.9, 2.0)
         + tone(t, base * 3, 0.6, 0.5) + tone(t, base * 4, 0.4, 2.5) + tone(t, base * 6, 0.18, 1.7)
         + tone(t, base * 8, 0.07, 0.9))
    x += 0.35 * noise(n, rng, lambda f: band(f, 180, 0.8) * lowpass(f, 400))  # breath
    swell = 0.85 + 0.15 * np.sin(2 * np.pi * t * 2 / length)  # breathes twice per loop
    if uneasy:
        x += tone(t, 467 / length, 0.3, 0.3)            # a tritone above, slightly sharp
        x += tone(t, 675 / length, 0.4, 1.1)            # beats against the 110 Hz overtone, 2.5 times a second
        swell *= 0.85 + 0.15 * np.sin(2 * np.pi * t * 5 / length) * np.sin(2 * np.pi * t * 2 / length)
    return x * swell


def lantern_drone(rng):
    """The Lantern's drone: a deep chord with a cold shimmer on top that slowly swells up and down."""
    length = 8.0
    n = int(SR * length)
    t = seconds(n)
    x = (tone(t, 41.25, 0.7) + tone(t, 41.375, 0.5, 0.9) + tone(t, 61.875, 0.5, 2.1) + tone(t, 82.5, 0.6, 0.4)
         + tone(t, 123.75, 0.6, 1.3) + tone(t, 165.0, 0.45, 2.2) + tone(t, 247.5, 0.35, 0.7)
         + tone(t, 330.0, 0.2, 2.9) + tone(t, 495.0, 0.08, 1.4))
    shimmer = tone(t, 1320.0, 1.0) + tone(t, 1321.25, 0.8, 1.0) + tone(t, 1980.0, 0.5, 2.0)
    x += 0.06 * shimmer * (0.5 + 0.5 * np.sin(2 * np.pi * t * 3 / length))
    return x


def soft_chord(n, notes, pitch, brightness):
    """A dark chord made of buzzing tones. brightness (0 to 1, per sample) fades between dull and edgy."""
    bright = sum(buzz(n, f * pitch, 10) for f in notes)
    dull = sum(buzz(n, f * pitch, 2) for f in notes)
    return bright * brightness + dull * (1 - brightness)


def squash(x, amount=2.0):
    """Soft distortion: makes a sound heavier and fuller without making it harsh."""
    x = x / (np.max(np.abs(x)) + 1e-12)
    return np.tanh(amount * x) / np.tanh(amount)


def flicker(rng):
    """The negative flickering: a soft, muffled tone cutting in and out, like a bad signal."""
    length = rng.uniform(0.25, 0.35)
    n = int(SR * length)
    t = seconds(n)
    root = rng.choice([174.6, 196.0, 220.0])  # F, G or A
    pitch = np.ones(n)
    gate = np.zeros(n)
    pos = 0.0
    while pos < length * 0.85:  # a few short bursts with gaps between
        on, off = rng.uniform(0.02, 0.05), rng.uniform(0.01, 0.035)
        a, b = int(SR * pos), int(SR * min(length, pos + on))
        pitch[a:b] = 2 ** (rng.choice([-1, 0, 0, 1, 3]) / 12)  # each burst jumps a little in pitch
        gate[a:b] = rng.uniform(0.5, 1.0) * (1 - 0.6 * pos / length)
        pos += on + off
    x = buzz(n, root * pitch, 4) + 0.6 * buzz(n, root * 1.189 * pitch, 4)  # root and a minor third
    x = filt(x, lambda f: lowpass(f, 1200))
    x *= smooth(gate, 4) * envelope(n, 0.005, length * 0.3, 1.0)
    return reverb(x, rng, 0.5, 0.2, 2000)


# A, A, B flat, E, B flat: a dark chord with a clash in it, shared by the surge and the fade.
NEGATIVE_CHORD = [55.0, 110.0, 116.54, 164.81, 233.08]


def negative_surge(rng):
    """The full negative turn: a short swell sucking in, then a deep, heavy hit with the dark chord ringing out."""
    length = 1.8
    hit = 0.35  # seconds: when it lands
    n = int(SR * length)
    t = seconds(n)
    before = t < hit
    after = np.clip(t - hit, 0, None)
    bend = np.where(before, 0.94 + 0.06 * t / hit, 1.0)  # the swell slides up into the hit
    rise = np.where(before, (t / hit) ** 3, 0.0)
    ring = np.where(before, 0.0, np.exp(-after / 0.55))
    shade = np.where(before, 0.3, np.exp(-after / 0.25))  # bright at the hit, darkening as it rings
    x = squash(soft_chord(n, NEGATIVE_CHORD, bend, shade) * (0.35 * rise + ring), 2.0)
    sub_hz = 30 + 50 * np.exp(-after / 0.08)  # a deep boom that drops in pitch
    x += 0.9 * np.sin(2 * np.pi * np.cumsum(sub_hz) / SR) * np.exp(-after / 0.45) * ~before
    x = filt(x, lambda f: lowpass(f, 5000))
    x *= envelope(n, 0.01, 0.4, 1.0)
    return reverb(x, rng, 1.2, 0.3, 2500)


def negative_fade(rng):
    """The negative wearing off: the same dark chord sliding down and slowing to a stop, like a tape running out."""
    length = 1.1
    n = int(SR * length)
    t = seconds(n)
    slow = (1 - t / length) ** 1.6  # 1 at the start, 0 at the end
    pitch = 0.25 + 0.75 * slow      # ends two octaves down
    x = squash(soft_chord(n, NEGATIVE_CHORD[1:], pitch, 0.6 * slow) * slow ** 0.8, 1.5)
    x = filt(x, lambda f: lowpass(f, 4000))
    x *= envelope(n, 0.02, 0.3, 1.0)
    return reverb(x, rng, 0.9, 0.25, 2500)


def watcher_near(rng):
    """A very faint high ringing, like your ears ringing, as a Watcher appears somewhere far off."""
    length = 3.0
    n = int(SR * length)
    t = seconds(n)
    x = tone(t, 3400) + tone(t, 3406, 0.8, 1.2) + tone(t, 6800, 0.1)
    x += 0.15 * noise(n, rng, lambda f: band(f, 3400, 0.3))
    fade = np.sin(np.pi * np.clip(t / length, 0, 1)) ** 2  # slowly in, slowly out
    return x * fade


def watcher_vanish(rng):
    """The Watcher vanishing: a soft scatter, like ash breathed out, from bright to dark."""
    length = 1.0
    n = int(SR * length)
    t = seconds(n)
    centres, bands = bank(n, rng)
    x = 0.6 * sweep(centres, bands, control([(0, 5000), (length, 600)], n), 1.2)
    x += 0.4 * sweep(centres, bands, np.full(n, 1100.0), 0.7) * np.exp(-((t - 0.25) / 0.18) ** 2)  # the breath out
    x += 0.5 * filt(clicks(n, rng, control([(0, 120), (length, 5)], n), (0.3, 1.5)), lambda f: highpass(f, 3000))
    x *= np.minimum(1, t / 0.03) * np.exp(-t / 0.3)
    x *= envelope(n, 0.001, 0.1, 1.0)
    return reverb(x, rng, 1.0, 0.3)


def bond_shift(rng):
    """A tiny flame flutter, for when the bond flame next to the hotbar changes."""
    length = 0.5
    n = int(SR * length)
    t = seconds(n)
    centres, bands = bank(n, rng)
    x = sweep(centres, bands, control([(0, 400), (0.1, 900), (length, 700)], n), 1.3)
    flutter = smooth(rng.random(n), 30)
    flutter = (flutter - flutter.min()) / (np.ptp(flutter) + 1e-12)
    x *= 0.4 + 0.6 * flutter
    x = filt(x, lambda f: lowpass(f, 2000))
    return x * envelope(n, 0.04, 0.4, 2.0)


def page_rustle(rng):
    """Dry paper rustling: a few small crinkles over a soft swish."""
    length = 0.6
    n = int(SR * length)
    t = seconds(n)
    centres, bands = bank(n, rng)
    x = 0.12 * sweep(centres, bands, np.full(n, 3000.0), 1.2) * np.exp(-((t - 0.22) / 0.13) ** 2)
    for _ in range(rng.integers(10, 18)):
        at = int(SR * min(length - 0.06, abs(rng.normal(0.2, 0.12))))
        m = int(SR * rng.uniform(0.01, 0.04))
        grain = noise(m, rng, lambda f, c=rng.uniform(2000, 7000): band(f, c, 0.8)) * np.hanning(m)
        x[at:at + m] += grain * rng.uniform(0.3, 1.0)
    x = filt(x, lambda f: highpass(f, 900))  # paper has no deep sound in it
    x *= envelope(n, 0.005, 0.15, 1.0)
    return reverb(x, rng, 0.3, 0.1, 6000)


# (file name, function, loudness group, loops?)
SOUNDS = [
    ("hum", lambda r: hum(r), "loop", True),
    ("hum_uneasy", lambda r: hum(r, uneasy=True), "loop", True),
    ("lantern_drone", lantern_drone, "loop", True),
    *[(f"flicker_{i}", flicker, "soft", False) for i in range(1, 4)],
    ("negative_surge", negative_surge, "oneshot", False),
    ("negative_fade", negative_fade, "oneshot", False),
    ("watcher_near", watcher_near, "ui", False),
    ("watcher_vanish", watcher_vanish, "oneshot", False),
    ("bond_shift", bond_shift, "ui", False),
    ("page_rustle", page_rustle, "oneshot", False),
]
# Sounds whose random seed comes from an older name, so they stay exactly the version Remi picked.
SEEDS = {"watcher_vanish": "watcher_vanish_1", "page_rustle": "page_rustle_1"}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    reel = []
    gap = np.zeros(int(SR * 0.8))
    for name, make, group, loops in SOUNDS:
        rng = np.random.default_rng(zlib.crc32(SEEDS.get(name, name).encode()))
        x = make(rng)
        x = level(x if loops else tidy_end(x), group)
        sf.write(OUT / f"{name}.ogg", x, SR, format="OGG", subtype="VORBIS", compression_level=0.4)
        sf.write(PREVIEW / f"{name}.wav", x, SR, subtype="PCM_16")
        reel += [x, x, gap] if loops else [x, gap]  # loops play twice so you can hear the join
        back, _ = sf.read(OUT / f"{name}.ogg")
        rms = 20 * math.log10(np.sqrt(np.mean(x ** 2)))
        peak = 20 * math.log10(np.max(np.abs(x)))
        seam = abs(back[0] - back[-1]) if loops else 0
        print(f"{name:18s} {len(x) / SR:4.2f} s  avg {rms:5.1f} dB  peak {peak:5.1f} dB"
              f"  {(OUT / f'{name}.ogg').stat().st_size / 1024:5.1f} KB" + (f"  loop join {seam:.4f}" if loops else ""))
    made = {f"{name}.ogg" for name, *_ in SOUNDS}
    for old in sorted(OUT.glob("*.ogg")):
        if old.name not in made:
            print(f"Note: {old.name} is not made by this script any more. Delete it if it isn't needed.")
    sf.write(PREVIEW / "reel.wav", np.concatenate(reel), SR, subtype="PCM_16")
    print(f"Wrote {len(SOUNDS)} sounds to {OUT.relative_to(ROOT)} and previews to {PREVIEW.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
