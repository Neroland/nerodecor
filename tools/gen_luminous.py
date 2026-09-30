#!/usr/bin/env python3
"""
NeroDecor "Luminous" collection — procedural texture generator.

Companion to tools/gen_textures.py for the signature set added in 0.4.0: circuit plating,
holo-grid floor, starfield panel, void rift, plasma conduit, aurora glass, data stream panel,
fusion lamp, lumen panel, void crystal lattice, starsteel pillar, capacitor bank, xenobloom
and ion vent. Same guarantees as gen_textures.py:

  * Deterministic — no RNG, no timestamps; every bit of "noise" is a hash of its inputs, so a
    rerun produces byte-identical PNGs.
  * Palette-driven — the house colours (black+blue body, nero-alloy teal, starsteel, void
    crystal, plasma) are the same constants gen_textures.py and tools/palette.json use.
  * Guarded — tools/gen_luminous.manifest.json records each output's sha256; --check reports
    drift and writes nothing.

Per block it emits into common/src/main/resources/assets/nerodecor/textures/block:

  <name>.png (+ .mcmeta when animated)   the lit-by-world base texture
  <name>_glow.png (+ .mcmeta)             cutout emissive overlay (alpha is 0 or 255 only), drawn
                                          by the model with "light_emission": 15 so it glows at night
  <name>_frame.png                        cutout bezel for connected textures: the model shows the
                                          strip of this frame along every edge whose neighbour is
                                          NOT the same block, so a wall of them reads as one panel

Pillars emit _side_v/_side_h (flow along the axis), _end and _collar_v/_collar_h instead.

Usage:  python tools/gen_luminous.py [--multiloader] [--check] [--force] [--preview OUT.png]
"""
import argparse
import hashlib
import io
import json
import math
import os
import sys

try:
    from PIL import Image
except ModuleNotFoundError:
    print("gen_luminous: Pillow not installed; skipping (pip install pillow).")
    sys.exit(0)

S = 16
HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
RES = os.path.join(REPO, "common", "src", "main", "resources", "assets", "nerodecor", "textures")
MANIFEST = os.path.join(HERE, "gen_luminous.manifest.json")

# --- house palette (matches gen_textures.py + palette.json) -------------------------------
BODY = (15, 19, 27)
BODY_HI = (30, 37, 50)
BODY_LO = (9, 11, 17)
SEAM = (7, 9, 14)
TEAL = (46, 139, 158)        # nero_alloy
STARSTEEL = (159, 196, 224)  # starsteel
VOID = (122, 63, 176)        # void_crystal
PLASMA = (63, 208, 224)      # plasma_glass


# --- tiny maths helpers -----------------------------------------------------------------
def det(*parts):
    """Deterministic float in [0,1) — the only source of 'noise'."""
    h = hashlib.sha256("|".join(str(p) for p in parts).encode()).digest()
    return int.from_bytes(h[:4], "big") / 0xFFFFFFFF


def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


def lerp(a, b, t):
    t = clamp(t)
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def scale(c, f):
    return tuple(max(0, min(255, round(v * f))) for v in c)


def add(c, d):
    return tuple(max(0, min(255, c[i] + d)) for i in range(3))


def ramp(stops, t):
    """Piecewise-linear colour ramp; stops = [(t, rgb), ...] ascending."""
    t = clamp(t)
    for i in range(1, len(stops)):
        t0, c0 = stops[i - 1]
        t1, c1 = stops[i]
        if t <= t1:
            return lerp(c0, c1, (t - t0) / (t1 - t0) if t1 > t0 else 1.0)
    return stops[-1][1]


def smooth(t):
    t = clamp(t)
    return t * t * (3 - 2 * t)


def tile_noise(seed, x, y, period):
    """Seamless value noise: lattice of `period` cells wrapping over the 16px tile."""
    cell = S / period
    gx, gy = x / cell, y / cell
    x0, y0 = int(math.floor(gx)), int(math.floor(gy))
    fx, fy = smooth(gx - x0), smooth(gy - y0)

    def v(ix, iy):
        return det(seed, ix % period, iy % period)
    a = v(x0, y0) + (v(x0 + 1, y0) - v(x0, y0)) * fx
    b = v(x0, y0 + 1) + (v(x0 + 1, y0 + 1) - v(x0, y0 + 1)) * fx
    return a + (b - a) * fy


def new(fill=(0, 0, 0, 0)):
    return Image.new("RGBA", (S, S), fill)


def put(img, x, y, rgb, a=255):
    img.load()[x % S, y % S] = tuple(rgb) + (a,)


# --- shared pieces --------------------------------------------------------------------
def body(seed, base=BODY, hi=BODY_HI, rate=0.92):
    img = new(base + (255,))
    for y in range(S):
        for x in range(S):
            if det(seed, x, y) > rate:
                put(img, x, y, hi)
    return img


def bezel(outer, light, mid, dark, accent=None):
    """2px connected-texture frame: dark seam outside, bevelled metal inside (lit top-left)."""
    img = new()
    for i in range(S):
        for (x, y) in ((i, 0), (i, S - 1), (0, i), (S - 1, i)):
            put(img, x, y, outer)
    for i in range(1, S - 1):
        put(img, i, 1, light)
        put(img, 1, i, light)
        put(img, i, S - 2, dark)
        put(img, S - 2, i, dark)
    put(img, S - 2, 1, mid)
    put(img, 1, S - 2, mid)
    if accent:
        # a single accent pixel pair at each corner: reads as a lit fastener where edges meet
        for (x, y) in ((1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)):
            put(img, x, y, accent)
    return img


def glow_from(layer):
    """Keep only the emissive pixels of a (partly transparent) layer; force alpha to 0/255."""
    out = new()
    src, dst = layer.load(), out.load()
    for y in range(S):
        for x in range(S):
            r, g, b, a = src[x, y]
            if a >= 128:
                dst[x, y] = (r, g, b, 255)
    return out


def over(base, top):
    out = base.copy()
    out.alpha_composite(top)
    return out


def dim_into(base, glow, f):
    """Base texture = body with the glow pixels drawn in at factor f (daylight look)."""
    out = base.copy()
    s, d = glow.load(), out.load()
    for y in range(S):
        for x in range(S):
            r, g, b, a = s[x, y]
            if a:
                d[x, y] = scale((r, g, b), f) + (255,)
    return out


def transpose(img):
    return img.transpose(Image.Transpose.TRANSPOSE)


# =========================================================================================
# Designs. Each returns {texture_suffix: (frames:list[Image], frametime:int)}; suffix "" is
# the base texture. Frame counts are kept small; every loop is seamless in time.
# =========================================================================================

# --- 1. Circuit plating: teal traces with light packets racing along them ---------------
_CIRCUIT_PATHS = [
    # waypoints; paths leaving one edge re-enter on the opposite edge so walls tile seamlessly
    [(0, 4), (4, 4), (4, 7), (11, 7), (11, 4), (15, 4)],
    [(15, 12), (13, 12), (13, 10), (6, 10), (6, 12), (0, 12)],
    [(9, 0), (9, 2), (14, 2)],
    [(9, 15), (9, 13), (2, 13)],
    [(2, 13), (2, 15)],
    [(14, 2), (14, 0)],
]
_CIRCUIT_VIAS = [(4, 4), (11, 7), (13, 10), (6, 12), (14, 2), (2, 13), (9, 7), (9, 10)]


def _trace_pixels(waypoints):
    px = []
    for (x0, y0), (x1, y1) in zip(waypoints, waypoints[1:]):
        dx = (x1 > x0) - (x1 < x0)
        dy = (y1 > y0) - (y1 < y0)
        x, y = x0, y0
        while (x, y) != (x1, y1):
            if not px or px[-1] != (x, y):
                px.append((x, y))
            x += dx
            y += dy
        px.append((x1, y1))
    out = []
    for p in px:
        if not out or out[-1] != p:
            out.append(p)
    return out


def circuit_plating():
    F = 16
    trace_lit = (30, 112, 128)
    head = (176, 248, 255)
    paths = [_trace_pixels(w) for w in _CIRCUIT_PATHS]
    # a short vertical trace through the junctions so the board reads as a network
    paths.append(_trace_pixels([(9, 3), (9, 12)]))
    base_frames, glow_frames = [], []
    for f in range(F):
        glow = new()
        for i, p in enumerate(paths):
            for (x, y) in p:
                put(glow, x, y, trace_lit)
            # one packet per path; long paths run faster so every loop closes in F frames
            n = len(p)
            pos = ((f / F) + det("cp-phase", i)) * n
            for k in range(5):
                j = int(pos) - k
                if 0 <= j < n:
                    x, y = p[j]
                    put(glow, x, y, lerp(head, trace_lit, k / 4.0))
        for (x, y) in _CIRCUIT_VIAS:
            put(glow, x, y, (92, 192, 206))
        base = body("circuit")
        # faint substrate etching between the traces
        for y in range(0, S, 4):
            for x in range(2, S, 4):
                put(base, x, y, (19, 25, 35))
        base = dim_into(base, glow, 0.62)
        for (x, y) in _CIRCUIT_VIAS:
            put(base, x, y, (70, 150, 165))
        base_frames.append(base)
        glow_frames.append(glow)
    frame = bezel((8, 10, 15), (60, 78, 96), (46, 60, 74), (30, 40, 52), accent=TEAL)
    return {"": (base_frames, 2), "_glow": (glow_frames, 2), "_frame": ([frame], 0)}


# --- 2. Holo-grid floor: a Tron-style grid that breathes, with a slow scan line ---------
def holo_grid_floor():
    F = 16
    frames, glows = [], []
    for f in range(F):
        breath = 0.8 + 0.2 * math.sin(2 * math.pi * f / F)
        scan = (f * S // F)  # one row per frame
        glow = new()
        for i in range(S):
            for (x, y) in ((i, 0), (0, i)):
                put(glow, x, y, scale((52, 196, 214), breath))
            if i % 2 == 0:
                for (x, y) in ((i, 8), (8, i)):
                    put(glow, x, y, scale((34, 132, 150), breath))
        put(glow, 0, 0, (200, 248, 252))
        for (x, y) in ((8, 8), (7, 8), (9, 8), (8, 7), (8, 9)):
            put(glow, x, y, scale((120, 228, 238), breath))
        # scan line brightens wherever it crosses a grid line
        for x in range(S):
            a = glow.load()[x, scan]
            if a[3]:
                put(glow, x, scan, lerp(a[:3], (210, 250, 255), 0.7))
        put(glow, 0, scan, (230, 252, 255))
        put(glow, 8, scan, (190, 245, 250))
        base = new((10, 14, 22, 255))
        for y in range(S):
            for x in range(S):
                n = tile_noise("hg", x, y, 4)
                put(base, x, y, lerp((9, 12, 20), (16, 22, 33), n))
        base = dim_into(base, glow, 0.7)
        frames.append(base)
        glows.append(glow)
    frame = bezel((6, 8, 12), (58, 74, 92), (40, 52, 66), (24, 32, 42), accent=PLASMA)
    return {"": (frames, 3), "_glow": (glows, 3), "_frame": ([frame], 0)}


# --- 3. Starfield panel: nebula haze + twinkling stars; a wall becomes a window to space ---
_STARS = [  # x, y, kind, phase
    (3, 2, "big", 0.00), (11, 5, "s", 0.31), (6, 9, "s", 0.62), (13, 12, "big", 0.47),
    (1, 13, "s", 0.83), (8, 14, "t", 0.15), (14, 1, "t", 0.71), (9, 3, "t", 0.55),
    (4, 6, "t", 0.9), (15, 8, "s", 0.05),
]


def starfield_panel():
    F = 16
    base0 = new()
    for y in range(S):
        for x in range(S):
            n1 = tile_noise("sf-a", x, y, 4)
            n2 = tile_noise("sf-b", x, y, 8)
            n3 = tile_noise("sf-c", x, y, 2)
            c = (5, 7, 16)
            c = lerp(c, (46, 24, 74), (n1 * 0.7 + n2 * 0.3) ** 2.2 * 0.9)
            c = lerp(c, (12, 50, 66), (n3 ** 3) * 0.6)
            put(base0, x, y, c)
    frames, glows = [], []
    for f in range(F):
        glow = new()
        for (x, y, kind, ph) in _STARS:
            tw = 0.5 + 0.5 * math.sin(2 * math.pi * (f / F + ph))
            if kind == "big":
                b = 0.6 + 0.4 * tw
                put(glow, x, y, scale((240, 244, 255), b))
                for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    put(glow, x + dx, y + dy, scale((120, 150, 220), 0.45 + 0.55 * tw))
            elif kind == "s":
                put(glow, x, y, scale((200, 220, 255), 0.5 + 0.5 * tw))
            else:
                if tw > 0.35:
                    put(glow, x, y, scale((170, 180, 230), 0.35 + 0.5 * tw))
        frames.append(dim_into(base0, glow, 0.85))
        glows.append(glow)
    frame = bezel((8, 10, 16), (122, 148, 172), (96, 118, 138), (64, 80, 98))
    return {"": (frames, 5), "_glow": (glows, 5), "_frame": ([frame], 0)}


# --- 4. Void rift: a slow three-armed vortex of void crystal -----------------------------
_RIFT_RAMP = [(0.0, (8, 4, 16)), (0.35, (44, 18, 80)), (0.6, (112, 58, 170)),
              (0.82, (182, 132, 236)), (1.0, (240, 226, 255))]


def void_rift():
    F = 16
    frames, glows = [], []
    for f in range(F):
        img, glow = new(), new()
        for y in range(S):
            for x in range(S):
                dx, dy = x - 7.5, y - 7.5
                r = math.hypot(dx, dy) / 7.5
                th = math.atan2(dy, dx)
                swirl = 0.5 + 0.5 * math.sin(3 * th + 7.0 * r - 2 * math.pi * f / F)
                fall = 1 - smooth((r - 0.15) / 0.95)
                core = math.exp(-(r * 3.2) ** 2)
                v = clamp(swirl * fall * 0.85 + core)
                c = ramp(_RIFT_RAMP, v)
                # crystalline rim so the block edge reads
                edge = min(x, y, S - 1 - x, S - 1 - y)
                if edge == 0:
                    c = lerp((16, 9, 28), c, 0.25)
                elif edge == 1:
                    c = lerp((34, 18, 58), c, 0.55)
                put(img, x, y, c)
                if v > 0.52 and edge > 0:
                    put(glow, x, y, c)
        frames.append(img)
        glows.append(glow)
    return {"": (frames, 3), "_glow": (glows, 3)}


# --- 5. Plasma conduit (pillar): plasma flowing through a starsteel-cased glass tube -----
_PLASMA_RAMP = [(0.0, (10, 36, 52)), (0.45, (26, 138, 178)), (0.75, (63, 208, 224)),
                (1.0, (214, 252, 255))]
_HOUSING = {0: (28, 34, 44), 1: (122, 148, 172), 2: (74, 90, 110),
            13: (70, 86, 104), 14: (104, 128, 150), 15: (24, 30, 40)}


def _conduit_side(f, F):
    img, glow = new(), new()
    prof = {5: 0.35, 6: 0.72, 7: 1.0, 8: 1.0, 9: 0.72, 10: 0.35}
    for y in range(S):
        for x in range(S):
            if x in _HOUSING:
                c = _HOUSING[x]
                if x in (1, 14) and y in (3, 11):
                    c = (196, 216, 232)
                elif x in (1, 14) and y in (4, 12):
                    c = (54, 66, 82)
                put(img, x, y, c)
            elif x in (3, 12):
                put(img, x, y, (40, 70, 84))
            elif x == 4:
                put(img, x, y, (70, 122, 138) if y % 8 in (1, 2, 3) else (26, 50, 60))
            elif x == 11:
                put(img, x, y, (20, 40, 50))
            else:
                w = 0.55 + 0.45 * math.sin(2 * math.pi * (2 * y / S + f / F))
                blob = 0.25 * math.sin(2 * math.pi * (y / S - 2 * f / F) + x)
                v = clamp(prof[x] * (0.55 + 0.45 * w) + blob * prof[x])
                c = ramp(_PLASMA_RAMP, v)
                put(img, x, y, c)
                if v > 0.38:
                    put(glow, x, y, c)
    return img, glow


def plasma_conduit():
    F = 16
    sides, sglows = zip(*[_conduit_side(f, F) for f in range(F)])
    end, eglow = new(), new()
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - 7.5, y - 7.5)
            edge = min(x, y, S - 1 - x, S - 1 - y)
            if edge == 0:
                c = (24, 30, 40)
            elif edge == 1:
                c = (128, 154, 178) if (x + y) < S else (66, 80, 98)
            elif d < 2.2:
                c = (200, 250, 255)
            elif d < 3.3:
                c = PLASMA
            elif d < 4.4:
                c = (18, 40, 52)
            elif d < 5.5:
                c = (40, 150, 172)
            else:
                c = (84, 102, 124)
            put(end, x, y, c)
            if d < 3.3 or 4.4 <= d < 5.5:
                put(eglow, x, y, c)
    collar = _collar_v((24, 30, 40), (146, 174, 198), (64, 78, 96), (200, 222, 236))
    return {"_side_v": (list(sides), 2), "_side_v_glow": (list(sglows), 2),
            "_side_h": ([transpose(i) for i in sides], 2), "_side_h_glow": ([transpose(i) for i in sglows], 2),
            "_end": ([end], 0), "_end_glow": ([eglow], 0),
            "_collar_v": ([collar], 0), "_collar_h": ([transpose(collar)], 0)}


def _collar_v(outer, light, dark, rivet):
    """Cap band 3px at the top and bottom (symmetric); transparent elsewhere."""
    img = new()
    for x in range(S):
        put(img, x, 0, outer)
        put(img, x, 1, light)
        put(img, x, 2, dark)
        put(img, x, S - 3, dark)
        put(img, x, S - 2, scale(light, 0.8))
        put(img, x, S - 1, outer)
    for x in (3, 12):
        put(img, x, 1, rivet)
        put(img, x, S - 2, rivet)
    return img


# --- 6. Aurora glass: dark translucent glazing with drifting aurora curtains ---------------
def aurora_glass():
    F = 32
    frames = []
    for f in range(F):
        img = new()
        t = f / F
        for y in range(S):
            for x in range(S):
                # the curtain's lower hem ripples sideways; light streams upward from it in rays
                hem = (11.0 + 1.5 * math.sin(2 * math.pi * (x / S + t))
                       + 0.7 * math.sin(2 * math.pi * (2 * x / S - t) + 1.3))
                rays = 0.5 + 0.5 * (0.5 + 0.5 * math.sin(2 * math.pi * (3 * x / S + 2 * t)))
                if y <= hem:
                    up = hem - y
                    inten = math.exp(-up / 6.0) * rays + 0.55 * math.exp(-(up / 0.9) ** 2)
                    h = clamp(up / 8.0)
                else:
                    inten = 0.55 * math.exp(-(y - hem) / 0.7)
                    h = 0.0
                inten = clamp(inten)
                col = ramp([(0.0, (70, 232, 176)), (0.45, (66, 190, 226)), (1.0, (148, 110, 232))], h)
                c = lerp((9, 14, 26), col, inten * 0.95)
                put(img, x, y, c, round(92 + 136 * inten))
        frames.append(img)
    frame = bezel((10, 12, 18), (130, 156, 180), (100, 122, 144), (66, 82, 100))
    return {"": (frames, 3), "_frame": ([frame], 0)}


# --- 7. Data stream panel: glyph columns cascading down, seamless when stacked ------------
_COLS = [(1, 1, 0.0), (5, 2, 0.4), (9, 1, 0.75), (13, 2, 0.2)]  # x, speed px/frame, phase


def data_stream_panel():
    F = 16
    base0 = body("data", base=BODY_LO, hi=(16, 20, 30), rate=0.95)
    frames, glows = [], []
    for f in range(F):
        glow = new()
        for ci, (cx, speed, ph) in enumerate(_COLS):
            head = int(ph * S + speed * f) % S
            for y in range(S):
                for dx in (0, 1):
                    on = det("glyph", ci, dx, y) > 0.38
                    if not on:
                        continue
                    d = (head - y) % S
                    if d == 0:
                        put(glow, cx + dx, y, (200, 255, 244))
                    elif d < 9:
                        b = 1 - d / 9
                        put(glow, cx + dx, y, lerp((18, 70, 66), (60, 214, 184), b))
                    elif det("glyph-dim", ci, dx, y) > 0.55:
                        put(glow, cx + dx, y, (16, 52, 50))
        frames.append(dim_into(base0, glow, 0.7))
        glows.append(glow)
    frame = bezel((6, 8, 12), (56, 72, 88), (42, 54, 68), (26, 34, 44), accent=TEAL)
    return {"": (frames, 2), "_glow": (glows, 2), "_frame": ([frame], 0)}


# --- 8. Fusion lamp: a caged plasma core that breathes when powered ------------------------
def _lamp(core_fn):
    img, glow = new(), new()
    for y in range(S):
        for x in range(S):
            edge = min(x, y, S - 1 - x, S - 1 - y)
            if edge == 0:
                c, g = (22, 28, 36), None
            elif edge == 1:
                c, g = ((150, 176, 198) if x + y < S else (70, 86, 104)), None
            elif x in (5, 10) or y in (5, 10):
                c, g = (96, 118, 140), None
                if (x in (5, 10)) and (y in (5, 10)):
                    c = (170, 196, 216)
            else:
                c, g = core_fn(x, y)
            put(img, x, y, c)
            if g:
                put(glow, x, y, g)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(img, x, y, (40, 50, 62))
    return img, glow


def fusion_lamp():
    def off(x, y):
        r = math.hypot(x - 7.5, y - 7.5)
        return lerp((34, 64, 74), (12, 18, 26), r / 5.5), None
    off_img, _ = _lamp(off)
    F = 12
    frames, glows = [], []
    for f in range(F):
        p = 0.88 + 0.12 * math.sin(2 * math.pi * f / F)

        def on(x, y, p=p):
            r = math.hypot(x - 7.5, y - 7.5)
            v = clamp(math.exp(-(r / 4.6) ** 2) * p + 0.12)
            c = ramp([(0.0, (18, 70, 90)), (0.5, (63, 208, 224)), (1.0, (232, 253, 255))], v)
            return c, c
        img, glow = _lamp(on)
        frames.append(img)
        glows.append(glow)
    return {"": ([off_img], 0), "_on": (frames, 3), "_on_glow": (glows, 3)}


# --- 9. Lumen panel: a soft, cool-white light field (easy on the eyes) --------------------
def lumen_panel():
    img = new()
    for y in range(S):
        for x in range(S):
            n = det("lumen", x, y)
            c = add((222, 234, 240), round((n - 0.5) * 5))
            if x % 4 == 2 and y % 4 == 2:
                c = (212, 226, 234)
            put(img, x, y, c)
    frame = bezel((34, 42, 54), (160, 182, 200), (128, 150, 170), (96, 116, 134))
    return {"": ([img], 0), "_frame": ([frame], 0)}


# --- 10. Void crystal lattice: faceted crystal with glowing fractures and a shimmer --------
_SEEDS = [(1.7, 2.2), (7.9, 0.8), (12.6, 4.3), (4.4, 7.1), (10.2, 9.6), (15.1, 11.2), (2.6, 13.4), (7.3, 14.9)]


def _voronoi(x, y):
    ds = []
    for i, (sx, sy) in enumerate(_SEEDS):
        dx = min(abs(x + 0.5 - sx), S - abs(x + 0.5 - sx))
        dy = min(abs(y + 0.5 - sy), S - abs(y + 0.5 - sy))
        ds.append((math.hypot(dx, dy), i))
    ds.sort()
    return ds[0][1], ds[0][0], ds[1][0] - ds[0][0], ds[1][1]


def crystal_lattice():
    F = 12
    frames, glows = [], []
    sparkles = [(4, 5), (12, 9), (8, 13)]
    for f in range(F):
        img, glow = new(), new()
        band = -6 + f * 3.2  # the shimmer sweeps once, then rests (frames 9..11 are calm)
        for y in range(S):
            for x in range(S):
                cell, d1, gap, other = _voronoi(x, y)
                sx, sy = _SEEDS[cell]
                # each facet is a flat plane tilted toward the light (top-left)
                wx = (sx - (x + 0.5) + S / 2) % S - S / 2  # wrapped, so the tile stays seamless
                wy = (sy - (y + 0.5) + S / 2) % S - S / 2
                tilt = (wx + wy) * 0.035
                shade = 0.36 + 0.34 * det("facet", cell) + tilt
                c = scale(VOID, clamp(shade, 0.22, 1.2))
                # only some facet boundaries are glowing fractures; the rest are plain facet edges
                fracture = det("fracture", min(cell, other), max(cell, other)) > 0.45
                if gap < 0.55 and fracture:
                    c = (178, 136, 236)
                    put(glow, x, y, (150, 104, 222))
                elif gap < 0.5:
                    c = scale(c, 0.72)
                if abs((x + y) - band) < 1.2 and f < 9:
                    c = lerp(c, (236, 222, 255), 0.5)
                    put(glow, x, y, c)
                put(img, x, y, c)
        for i, (x, y) in enumerate(sparkles):
            if (f + i * 4) % F < 3:
                put(img, x, y, (246, 238, 255))
                put(glow, x, y, (246, 238, 255))
        frames.append(img)
        glows.append(glow)
    return {"": (frames, 4), "_glow": (glows, 4)}


# --- 11. Starsteel pillar: brushed pale metal with a teal-lit inset channel ---------------
def starsteel_pillar():
    F = 8
    sides, sglows = [], []
    for f in range(F):
        p = 0.78 + 0.22 * math.sin(2 * math.pi * f / F)
        img, glow = new(), new()
        for y in range(S):
            for x in range(S):
                brush = round((det("brush", x) - 0.5) * 22 + (det("brush2", x, y) - 0.5) * 6)
                c = add((140, 170, 196), brush)
                if x == 0:
                    c = (58, 72, 88)
                elif x == 1:
                    c = (110, 136, 160)
                elif x == 14:
                    c = (96, 118, 138)
                elif x == 15:
                    c = (46, 58, 72)
                elif x == 6:
                    c = (84, 102, 122)
                elif x in (7, 8):
                    c = scale((52, 198, 206), p)
                    put(glow, x, y, c)
                elif x == 9:
                    c = (184, 208, 228)
                put(img, x, y, c)
        sides.append(img)
        sglows.append(glow)
    end, eglow = new(), new()
    for y in range(S):
        for x in range(S):
            edge = min(x, y, S - 1 - x, S - 1 - y)
            man = abs(x - 7.5) + abs(y - 7.5)
            if edge == 0:
                c = (46, 58, 72)
            elif edge == 1:
                c = (176, 200, 220) if x + y < S else (96, 118, 138)
            elif edge < 4:
                c = (140, 170, 196)
            elif man < 2.5:
                c = (130, 236, 240)
                put(eglow, x, y, c)
            elif man < 3.5:
                c = (46, 170, 184)
                put(eglow, x, y, c)
            else:
                c = (36, 46, 58)
            put(end, x, y, c)
    collar = _collar_v((46, 58, 72), (190, 212, 230), (96, 118, 138), (52, 198, 206))
    return {"_side_v": (sides, 6), "_side_v_glow": (sglows, 6),
            "_side_h": ([transpose(i) for i in sides], 6), "_side_h_glow": ([transpose(i) for i in sglows], 6),
            "_end": ([end], 0), "_end_glow": ([eglow], 0),
            "_collar_v": ([collar], 0), "_collar_h": ([transpose(collar)], 0)}


# --- 12. Capacitor bank: three glass cells charging and discharging out of phase ----------
def capacitor_bank():
    F = 16
    cells = [(3, 0.0), (7, 0.33), (11, 0.66)]
    frames, glows = [], []
    for f in range(F):
        img = body("cap", base=(12, 16, 23))
        glow = new()
        for y in range(S):
            for x in range(S):
                edge = min(x, y, S - 1 - x, S - 1 - y)
                if edge == 0:
                    put(img, x, y, (22, 28, 36))
                elif edge == 1:
                    put(img, x, y, (136, 162, 186) if x + y < S else (66, 80, 98))
        for x in range(2, 14):
            put(img, x, 2, (110, 136, 160))
            put(img, x, 13, (84, 104, 124))
        for (cx, ph) in cells:
            level = 0.5 + 0.44 * math.sin(2 * math.pi * (f / F + ph))
            filled = round(level * 10)
            for y in range(3, 13):
                put(img, cx - 1, y, (40, 62, 76))
                put(img, cx + 2, y, (40, 62, 76))
                rank = 12 - y  # 0 at the bottom
                for dx in (0, 1):
                    if rank < filled:
                        c = lerp((20, 104, 124), PLASMA, rank / 9)
                        if rank == filled - 1:
                            c = (200, 250, 255)
                        put(img, cx + dx, y, c)
                        put(glow, cx + dx, y, c)
                    else:
                        put(img, cx + dx, y, (14, 22, 30) if dx else (22, 34, 44))
            led = PLASMA if level > 0.5 else (26, 90, 104)
            put(img, cx, 14, led)
            put(img, cx + 1, 14, led)
            put(glow, cx, 14, led)
            put(glow, cx + 1, 14, led)
        frames.append(img)
        glows.append(glow)
    return {"": (frames, 4), "_glow": (glows, 4)}


# --- 13. Xenobloom: alien rock threaded with softly pulsing bioluminescent colonies -------
_SPOTS = [(3, 4, 2, 0.0), (11, 2, 1, 0.4), (9, 10, 2, 0.7), (2, 12, 1, 0.55), (14, 13, 1, 0.2)]


def xeno_bloom():
    F = 16
    rock = new()
    for y in range(S):
        for x in range(S):
            n = tile_noise("xr", x, y, 4) * 0.65 + tile_noise("xr2", x, y, 8) * 0.35
            c = lerp((18, 24, 30), (46, 58, 62), n)
            ridge = abs(tile_noise("xvein", x, y, 4) - 0.5)
            if ridge < 0.045:
                c = (28, 64, 60)  # faint mycelial veins linking the colonies
            elif det("xspeck", x, y) > 0.95:
                c = (66, 80, 84)
            put(rock, x, y, c)
    frames, glows = [], []
    for f in range(F):
        img, glow = rock.copy(), new()
        for (sx, sy, size, ph) in _SPOTS:
            b = 0.3 + 0.7 * (0.5 + 0.5 * math.sin(2 * math.pi * (f / F + ph)))
            core = [(0, 0)] if size == 1 else [(0, 0), (1, 0), (0, 1), (1, 1)]
            halo = set()
            for (dx, dy) in core:
                for (hx, hy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    halo.add((dx + hx, dy + hy))
            halo -= set(core)
            for (dx, dy) in halo:
                c = lerp((28, 52, 52), (56, 150, 128), b)
                put(img, sx + dx, sy + dy, c)
                if b > 0.7:
                    put(glow, sx + dx, sy + dy, c)
            for (dx, dy) in core:
                c = lerp((60, 130, 112), (196, 255, 228), b)
                put(img, sx + dx, sy + dy, c)
                put(glow, sx + dx, sy + dy, c)
        frames.append(img)
        glows.append(glow)
    return {"": (frames, 5), "_glow": (glows, 5)}


# --- 14. Ion vent: a louvred grille with a deep ion-blue glow between the slats ------------
def ion_vent():
    F = 8
    frames, glows = [], []
    for f in range(F):
        p = 0.72 + 0.28 * math.sin(2 * math.pi * f / F)
        img, glow = new(), new()
        for y in range(S):
            for x in range(S):
                edge = min(x, y, S - 1 - x, S - 1 - y)
                if edge == 0:
                    c = (20, 26, 34)
                elif edge == 1:
                    c = (126, 152, 176) if x + y < S else (62, 76, 94)
                elif y % 2 == 1:  # slat
                    c = (92, 112, 134) if x > 2 else (70, 86, 104)
                    if x == S - 3:
                        c = (58, 72, 88)
                else:  # gap: glow deepest in the centre
                    centre = 1 - abs(x - 7.5) / 6.5
                    v = clamp(p * (0.45 + 0.55 * centre))
                    c = ramp([(0.0, (8, 16, 30)), (0.5, (40, 120, 200)), (1.0, (140, 210, 250))], v)
                    if v > 0.3:
                        put(glow, x, y, c)
                put(img, x, y, c)
        frames.append(img)
        glows.append(glow)
    return {"": (frames, 3), "_glow": (glows, 3)}


DESIGNS = {
    "circuit_plating": circuit_plating,
    "holo_grid_floor": holo_grid_floor,
    "starfield_panel": starfield_panel,
    "void_rift": void_rift,
    "plasma_conduit": plasma_conduit,
    "aurora_glass": aurora_glass,
    "data_stream_panel": data_stream_panel,
    "fusion_lamp": fusion_lamp,
    "lumen_panel": lumen_panel,
    "crystal_lattice": crystal_lattice,
    "starsteel_pillar": starsteel_pillar,
    "capacitor_bank": capacitor_bank,
    "xeno_bloom": xeno_bloom,
    "ion_vent": ion_vent,
}


# --- emit ---------------------------------------------------------------------------------
def png_bytes(img):
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def strip(frames):
    out = Image.new("RGBA", (S, S * len(frames)), (0, 0, 0, 0))
    for i, fr in enumerate(frames):
        out.paste(fr, (0, i * S))
    return out


def mcmeta(n, frametime, interpolate):
    return (json.dumps({"animation": {"interpolate": interpolate, "frametime": frametime,
                                      "frames": list(range(n))}}, indent=2) + "\n").encode("utf-8")


# --- baked connected variants ---------------------------------------------------------------
# Frames and collars are BAKED into the face textures rather than drawn as a second coplanar
# layer: a coplanar cutout quad over a solid quad z-fights (the base renders in the solid pass,
# the overlay in the cutout pass). Each face picks one of these variants from its neighbours.
#
# Connected blocks: <name>_f<mask>.png, mask bits = which TEXTURE edges carry the bezel:
#   8 = top, 4 = bottom, 2 = left, 1 = right   (0 = fully connected, 15 = isolated)
# Pillars: <name>_side_v_c<t><b>.png (collar on top / bottom rows) and
#          <name>_side_h_c<l><r>.png (collar on left / right columns); each with a matching _glow.
FRAME_W = 2
COLLAR_W = 3


def _edge_band(mask, width):
    """Pixel predicate for the texture edges selected by `mask` (8=t, 4=b, 2=l, 1=r)."""
    def inside(x, y):
        return ((mask & 8 and y < width) or (mask & 4 and y >= S - width)
                or (mask & 2 and x < width) or (mask & 1 and x >= S - width))
    return inside


def _apply_band(img, overlay, inside):
    """Copy `overlay` pixels into `img` where `inside`; transparent overlay pixels leave img alone."""
    out = img.copy()
    o, d = overlay.load(), out.load()
    for y in range(S):
        for x in range(S):
            if inside(x, y) and o[x, y][3]:
                d[x, y] = o[x, y]
    return out


def _clear_band(img, inside):
    out = img.copy()
    d = out.load()
    for y in range(S):
        for x in range(S):
            if inside(x, y):
                d[x, y] = (0, 0, 0, 0)
    return out


def expand(name, design):
    """Turn a design's _frame / _collar layers into baked per-face variants."""
    d = dict(design)
    if "_frame" in d:
        frame = d.pop("_frame")[0][0]
        base_frames, ft = d[""]
        glow = d.get("_glow")
        for mask in range(16):
            band = _edge_band(mask, FRAME_W)
            d["_f%d" % mask] = ([_apply_band(f, frame, band) for f in base_frames], ft)
            if glow:
                d["_glow_f%d" % mask] = ([_clear_band(f, band) for f in glow[0]], glow[1])
    if "_collar_v" in d:
        collar = d.pop("_collar_v")[0][0]
        d.pop("_collar_h", None)
        sides, ft = d.pop("_side_v")
        sglow, gft = d.pop("_side_v_glow")
        d.pop("_side_h")
        d.pop("_side_h_glow")
        for t in (0, 1):
            for b in (0, 1):
                band = _edge_band(t * 8 + b * 4, COLLAR_W)
                v = [_apply_band(f, collar, band) for f in sides]
                vg = [_clear_band(f, band) for f in sglow]
                d["_side_v_c%d%d" % (t, b)] = (v, ft)
                d["_side_v_glow_c%d%d" % (t, b)] = (vg, gft)
                # transposing maps top rows -> left columns, bottom rows -> right columns
                d["_side_h_c%d%d" % (t, b)] = ([transpose(i) for i in v], ft)
                d["_side_h_glow_c%d%d" % (t, b)] = ([transpose(i) for i in vg], gft)
    return d


def expected_outputs():
    out = {}
    for name in sorted(DESIGNS):
        for suffix, (frames, ft) in sorted(expand(name, DESIGNS[name]()).items()):
            key = "block/%s%s" % (name, suffix)
            out[key + ".png"] = png_bytes(strip(frames) if len(frames) > 1 else frames[0])
            if len(frames) > 1:
                # Cutout glow overlays must not interpolate (blending would add partial alpha).
                interp = "glow" not in suffix and name != "aurora_glass"
                out[key + ".png.mcmeta"] = mcmeta(len(frames), ft, interp)
    return out


def preview(path):
    """Contact sheet: each design as a 3x3 wall (with frame edges where a CTM frame exists)."""
    Z = 6
    cells = []
    for name in sorted(DESIGNS):
        d = DESIGNS[name]()
        if "" in d:
            tile = d[""][0][0]
            if "_glow" in d:
                tile = over(tile, d["_glow"][0][0])
        else:
            tile = over(d["_side_v"][0][0], d["_side_v_glow"][0][0])
        wall = Image.new("RGBA", (S * 3, S * 3), (0, 0, 0, 255))
        for gy in range(3):
            for gx in range(3):
                t = tile.copy()
                if "_frame" in d:
                    fr = d["_frame"][0][0]
                    mask = new()
                    m, src = mask.load(), fr.load()
                    for y in range(S):
                        for x in range(S):
                            if ((gy == 0 and y < 2) or (gy == 2 and y >= S - 2)
                                    or (gx == 0 and x < 2) or (gx == 2 and x >= S - 2)):
                                m[x, y] = src[x, y]
                    t = over(t, mask)
                elif "_collar_v" in d:
                    if gy == 0 or gy == 2:
                        cv = d["_collar_v"][0][0].copy()
                        cm = new()
                        a, b = cm.load(), cv.load()
                        for y in range(S):
                            for x in range(S):
                                if (gy == 0 and y < 3) or (gy == 2 and y >= S - 3):
                                    a[x, y] = b[x, y]
                        t = over(t, cm)
                wall.alpha_composite(t, (gx * S, gy * S))
        cells.append((name, wall.resize((S * 3 * Z, S * 3 * Z), Image.Resampling.NEAREST)))
    cols = 5
    W = S * 3 * Z
    sheet = Image.new("RGBA", (cols * (W + 12) + 12, ((len(cells) + cols - 1) // cols) * (W + 12) + 12), (40, 44, 52, 255))
    for i, (_, im) in enumerate(cells):
        sheet.alpha_composite(im, (12 + (i % cols) * (W + 12), 12 + (i // cols) * (W + 12)))
    sheet.save(path)
    print("preview:", path, [n for n, _ in cells])


def main():
    ap = argparse.ArgumentParser(description="NeroDecor Luminous-collection texture generator.")
    ap.add_argument("--multiloader", action="store_true", help="target the flattened common module (default)")
    ap.add_argument("--check", action="store_true", help="report drift; write nothing; nonzero exit on drift")
    ap.add_argument("--force", action="store_true", help="rewrite even unchanged outputs")
    ap.add_argument("--preview", metavar="PNG", help="write a contact sheet of every design and exit")
    args = ap.parse_args()

    if args.preview:
        preview(args.preview)
        return

    expected = expected_outputs()
    manifest = {}
    if os.path.exists(MANIFEST):
        with open(MANIFEST, encoding="utf-8") as fh:
            manifest = json.load(fh).get("outputs", {})
    drift = []
    for rel, data in sorted(expected.items()):
        digest = hashlib.sha256(data).hexdigest()
        try:
            disk = hashlib.sha256(open(os.path.join(RES, rel), "rb").read()).hexdigest()
        except OSError:
            disk = None
        if disk != digest or manifest.get(rel) != digest:
            drift.append(rel)
    stale = sorted(set(manifest) - set(expected))

    if args.check:
        if drift or stale:
            print("gen_luminous --check: DRIFT")
            for r in drift:
                print("  new/stale: %s" % r)
            for r in stale:
                print("  orphan (no longer generated): %s" % r)
            sys.exit(1)
        print("gen_luminous --check: clean (%d outputs)" % len(expected))
        return

    for rel, data in sorted(expected.items()):
        if args.force or rel in drift:
            path = os.path.join(RES, rel)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "wb") as fh:
                fh.write(data)
    for rel in stale:
        try:
            os.remove(os.path.join(RES, rel))
        except OSError:
            pass
    with open(MANIFEST, "w", encoding="utf-8", newline="\n") as fh:
        json.dump({"schema": 1, "outputs": {k: hashlib.sha256(v).hexdigest()
                                            for k, v in sorted(expected.items())}}, fh, indent=2)
        fh.write("\n")
    print("gen_luminous: %d outputs (%d written, %d removed)" % (len(expected), len(drift), len(stale)))


if __name__ == "__main__":
    main()
