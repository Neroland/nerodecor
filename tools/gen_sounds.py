#!/usr/bin/env python3
"""
NeroDecor ambient sound synthesiser — the subtle block noises of the Luminous collection.

Every sound is synthesised from first principles (sines, filtered noise, envelopes) with a
deterministic hash-based noise source, written as 16-bit mono WAV and encoded to Ogg Vorbis
with ffmpeg into common/src/main/resources/assets/nerodecor/sounds/block/. Nothing is sampled
from anywhere, so there is no licensing question.

Design rule: these are *ambience*, not alerts. Soft attacks, no harsh transients, peaks held at
-6 dBFS, and the code plays them rarely and quietly (see content/fx/AmbientFx.java).

Additive by default: an existing .ogg is kept (Vorbis encoders are not byte-stable across ffmpeg
builds, so regenerating would churn the repo). Use --force to rebuild everything.

Usage: python tools/gen_sounds.py [--multiloader] [--force]
Needs ffmpeg with libvorbis on PATH; skips gracefully without it.
"""
import argparse
import hashlib
import math
import os
import shutil
import struct
import subprocess
import sys
import tempfile
import wave

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
OUT = os.path.join(REPO, "common", "src", "main", "resources", "assets", "nerodecor", "sounds", "block")
SR = 44100
TAU = 2 * math.pi


def det_noise(seed, n):
    """n deterministic samples in [-1, 1): a hash-seeded xorshift stream (no RNG state)."""
    x = int.from_bytes(hashlib.sha256(seed.encode()).digest()[:8], "big") or 1
    out = []
    for _ in range(n):
        x ^= (x << 13) & 0xFFFFFFFFFFFFFFFF
        x ^= x >> 7
        x ^= (x << 17) & 0xFFFFFFFFFFFFFFFF
        out.append((x & 0xFFFFFF) / 0x800000 - 1.0)
    return out


def silence(sec):
    return [0.0] * int(sec * SR)


def mix(dst, src, at=0.0, gain=1.0):
    o = int(at * SR)
    need = o + len(src)
    if len(dst) < need:
        dst.extend([0.0] * (need - len(dst)))
    for i, v in enumerate(src):
        dst[o + i] += v * gain
    return dst


def sine(freq, sec, amp=1.0, glide=0.0, vib=0.0, vib_rate=5.0):
    n = int(sec * SR)
    out, ph = [], 0.0
    for i in range(n):
        t = i / SR
        f = freq * (1 + glide * t / sec) * (1 + vib * math.sin(TAU * vib_rate * t))
        ph += TAU * f / SR
        out.append(amp * math.sin(ph))
    return out


def sweep(f0, f1, sec, amp=1.0):
    """Exponential frequency sweep f0 -> f1."""
    n = int(sec * SR)
    out, ph = [], 0.0
    for i in range(n):
        f = f0 * (f1 / f0) ** (i / n)
        ph += TAU * f / SR
        out.append(amp * math.sin(ph))
    return out


def lowpass(sig, cutoff):
    a = 1 - math.exp(-TAU * cutoff / SR)
    y, out = 0.0, []
    for v in sig:
        y += a * (v - y)
        out.append(y)
    return out


def highpass(sig, cutoff):
    lp = lowpass(sig, cutoff)
    return [v - l for v, l in zip(sig, lp)]


def env(sig, attack, release, shape="lin"):
    n = len(sig)
    a, r = max(1, int(attack * SR)), max(1, int(release * SR))
    out = []
    for i, v in enumerate(sig):
        g = 1.0
        if i < a:
            g = i / a
        if i > n - r:
            g = min(g, (n - i) / r)
        if shape == "smooth":
            g = g * g * (3 - 2 * g)
        out.append(v * g)
    return out


def swell(sig, peak_at=0.4):
    """Raised-sine swell: silent -> peak -> silent (for hums)."""
    n = len(sig)
    p = max(1, int(n * peak_at))
    out = []
    for i, v in enumerate(sig):
        g = math.sin(0.5 * math.pi * i / p) if i < p else math.cos(0.5 * math.pi * (i - p) / (n - p))
        out.append(v * g * g)
    return out


def decay(sig, tau):
    return [v * math.exp(-(i / SR) / tau) for i, v in enumerate(sig)]


def normalise(sig, peak_db=-6.0):
    m = max(1e-9, max(abs(v) for v in sig))
    g = (10 ** (peak_db / 20)) / m
    return [v * g for v in sig]


# --- designs ------------------------------------------------------------------------------
def circuit_chirp(pattern, seed):
    out = silence(0.02)
    t = 0.02
    for k, f in enumerate(pattern):
        blip = decay(env(sine(f, 0.06, glide=-0.03), 0.003, 0.02), 0.022)
        tick = decay(highpass(det_noise(seed + str(k), int(0.004 * SR)), 3000), 0.001)
        mix(out, blip, t)
        mix(out, tick, t, 0.15)
        t += 0.052 + 0.012 * (k % 2)
    mix(out, silence(0.08), t)
    return normalise(lowpass(out, 7000), -9.0)


def void_hum(root, seed):
    d = 3.2
    s = sine(root, d, 0.5, vib=0.004, vib_rate=0.4)
    mix(s, sine(root * 1.5, d, 0.26, vib=0.003, vib_rate=0.3))
    mix(s, sine(root * 2.01, d, 0.14))
    mix(s, sine(root * 4.0, d, 0.08, vib=0.01, vib_rate=0.7))
    breath = det_noise(seed, int(d * SR))
    for _ in range(4):
        breath = lowpass(breath, 260)
    mix(s, [v * 6.0 for v in breath], 0, 0.5)
    mix(s, sine(root * 16, d, 0.02, vib=0.006, vib_rate=3.1))  # faint glassy shimmer
    return normalise(swell(s, 0.45), -7.0)


def conduit_crackle(seed, count):
    d = 0.8
    s = sine(120, d, 0.12)
    mix(s, sine(240, d, 0.05))
    pos = det_noise(seed + "pos", count)
    amp = det_noise(seed + "amp", count)
    for i in range(count):
        at = 0.08 + (pos[i] * 0.5 + 0.5) * (d - 0.2)
        burst = decay(highpass(det_noise(seed + str(i), int(0.008 * SR)), 2200), 0.0018)
        mix(s, burst, at, 0.35 + 0.35 * abs(amp[i]))
    return normalise(env(lowpass(lowpass(s, 7000), 9000), 0.06, 0.2, "smooth"), -10.0)


def crystal_chime(notes):
    out = silence(0.01)
    for k, f in enumerate(notes):
        tone = []
        for ratio, a, tau in ((1.0, 1.0, 0.9), (2.76, 0.32, 0.38), (5.40, 0.1, 0.18)):
            mix(tone, decay(env(sine(f * ratio, 2.0, a), 0.004, 0.3), tau))
        mix(out, tone, 0.01 + k * 0.14, 0.8 if k else 1.0)
    return normalise(out, -8.0)


def lamp_power(on):
    if on:
        s = env(sweep(170, 520, 0.55, 0.6), 0.012, 0.25)
        mix(s, decay(sine(68, 0.12, 0.9), 0.04))
    else:
        s = env(sweep(480, 130, 0.5, 0.6), 0.008, 0.3)
        mix(s, decay(sine(90, 0.1, 0.5), 0.05), 0.3)
    click = decay(lowpass(det_noise("lamp-click-%s" % on, int(0.01 * SR)), 2500), 0.003)
    mix(s, click, 0, 0.5)
    return normalise(decay(s, 0.35 if on else 0.3), -8.0)


def lamp_hum():
    d = 2.6
    s = sine(100, d, 0.5)
    mix(s, sine(200, d, 0.2))
    mix(s, sine(300.4, d, 0.08))
    trem = [1 - 0.15 * (0.5 + 0.5 * math.sin(TAU * 5 * i / SR)) for i in range(len(s))]
    s = [v * g for v, g in zip(s, trem)]
    return normalise(swell(s, 0.5), -12.0)


def vent_hiss(seed):
    d = 1.7
    n = det_noise(seed, int(d * SR))
    s = lowpass(lowpass(lowpass(highpass(n, 900), 3200), 3200), 5000)
    s = [v * 2.5 for v in s]
    mix(s, sine(660, d, 0.02, vib=0.01, vib_rate=0.8))
    return normalise(swell(s, 0.35), -12.0)


def capacitor_charge():
    d = 1.5
    s = [0.6 * v for v in sweep(700, 1400, d, 0.25)]
    mix(s, sine(120, d, 0.18))
    s = swell(s, 0.8)
    ping = decay(env(sine(2093, 0.4, 0.4), 0.003, 0.1), 0.09)
    mix(s, ping, d - 0.25)
    return normalise(s, -10.0)


SOUNDS = {
    "circuit_chirp1": lambda: circuit_chirp([2637, 2093], "cc1"),
    "circuit_chirp2": lambda: circuit_chirp([1760, 2349, 3136], "cc2"),
    "circuit_chirp3": lambda: circuit_chirp([3136, 2637, 2349, 1976], "cc3"),
    "void_hum1": lambda: void_hum(55.0, "vh1"),
    "void_hum2": lambda: void_hum(49.0, "vh2"),
    "conduit_crackle1": lambda: conduit_crackle("cr1", 9),
    "conduit_crackle2": lambda: conduit_crackle("cr2", 13),
    "conduit_crackle3": lambda: conduit_crackle("cr3", 6),
    "crystal_chime1": lambda: crystal_chime([1318.5]),
    "crystal_chime2": lambda: crystal_chime([1568.0, 1046.5]),
    "crystal_chime3": lambda: crystal_chime([1760.0, 1318.5]),
    "lamp_power_on": lambda: lamp_power(True),
    "lamp_power_off": lambda: lamp_power(False),
    "lamp_hum": lamp_hum,
    "vent_hiss1": lambda: vent_hiss("vs1"),
    "vent_hiss2": lambda: vent_hiss("vs2"),
    "capacitor_charge": capacitor_charge,
}


def write_wav(path, sig):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, v)) * 32767)) for v in sig))


def main():
    ap = argparse.ArgumentParser(description="NeroDecor ambient sound synthesiser.")
    ap.add_argument("--multiloader", action="store_true", help="target the flattened common module (default)")
    ap.add_argument("--force", action="store_true", help="re-encode every sound even if it exists")
    ap.add_argument("--wav", metavar="DIR", help="also keep the WAV renders in DIR (for auditioning)")
    args = ap.parse_args()

    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        print("gen_sounds: ffmpeg not on PATH; skipping (existing .ogg files are kept).")
        return
    os.makedirs(OUT, exist_ok=True)
    written = 0
    with tempfile.TemporaryDirectory() as tmp:
        for name, fn in sorted(SOUNDS.items()):
            ogg = os.path.join(OUT, name + ".ogg")
            if os.path.exists(ogg) and not args.force:
                continue
            wav = os.path.join(args.wav or tmp, name + ".wav")
            os.makedirs(os.path.dirname(wav), exist_ok=True)
            write_wav(wav, fn())
            subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", wav, "-map_metadata", "-1",
                            "-c:a", "libvorbis", "-q:a", "4", "-ac", "1", ogg], check=True)
            written += 1
    print("gen_sounds: %d sounds (%d encoded)" % (len(SOUNDS), written))


if __name__ == "__main__":
    sys.exit(main())
