"""
Seven Worlds collection -- 24 blocks, 3-4 per Nerospace world (by name only; no dependency).

Every block has its own texture idea rather than a recolour: Station is manufactured (plates,
tread, quilted foil), Luna is dust and basalt, Mars is rust and sediment, Cindara is volcanic,
Glacira is ice and rime, Greenxertz is organic (weave, pores, comb) and Nyxara is cut crystal.

Blockstate contracts the Java side has to match:
  station_plating, glacira_bubble_ice   mask16 (six booleans north..down)
  luna_mare_column, glacira_cryo_fin    DecorPillarBlock: axis + pos_link + neg_link
  mars_dust_drift_tile                  horizontal `facing` (drift sits in the facing-left corner)
  luna_bootprint_tile and the flat tiles (station_tread_plate, mars_rust_plate, cindara_ash_tile,
  glacira_frost_plate) pick a random weighted model; no property needed.
"""
import math

from ._kit import (Block, FACES, animate, bevel, det, facing_variants, lerp, new, put, ramp,
                   ramp5, rect, wear)

COLLECTION = "seven_worlds"

# --- ramps (index 0 occlusion .. 4 glint) ------------------------------------------------------
ST = ramp("station")
LU = ramp("luna")
MA = ramp("mars")
CI = ramp("cindara")
GL = ramp("glacira")
GX = ramp("greenxertz")
NY = ramp("nyxara")
VOID = ramp("void_crystal")

HULL = [ST[1], ST[2], lerp(ST[3], ST[4], 0.35), lerp(ST[3], ST[4], 0.75), (228, 236, 243)]
FOIL = [(84, 46, 34), (146, 92, 38), (204, 150, 52), (238, 198, 98), (255, 241, 184)]
BASALT = [(20, 18, 31), (36, 34, 49), (58, 56, 71), (88, 85, 98), (132, 128, 135)]
BEAD_O = [(104, 36, 40), (176, 72, 30), (232, 124, 38), (250, 176, 84), (255, 232, 170)]
BEAD_G = [(16, 60, 62), (28, 112, 80), (62, 170, 92), (132, 214, 120), (214, 248, 190)]
STEEL = [(38, 34, 47), (66, 60, 73), (98, 92, 103), (134, 128, 135), (180, 173, 170)]
SAND = [(112, 62, 46), (166, 106, 72), (208, 152, 106), (231, 186, 138), (249, 224, 182)]
HEMA = [(22, 20, 37), (40, 38, 61), (64, 63, 90), (100, 101, 131), (164, 170, 199)]
CBAS = [(17, 9, 20), (30, 14, 24), (46, 24, 33), (68, 40, 45), (102, 66, 63)]
SLAG = [(21, 14, 25), (41, 28, 39), (65, 47, 55), (96, 74, 76), (143, 115, 106)]
HEAT = [(88, 66, 152), (128, 70, 134), (172, 104, 92), (216, 178, 100)]   # violet .. straw
ASH = [(78, 60, 64), (124, 106, 103), (168, 152, 144), (200, 186, 172), (233, 223, 205)]
SCORCH = [(34, 16, 22), (72, 38, 32), (112, 74, 60)]
COLD = [(14, 28, 47), (30, 58, 85), (58, 97, 125), (104, 147, 171), (172, 207, 221)]
RIME = [GL[2], GL[3], lerp(GL[3], GL[4], 0.5), GL[4], (242, 251, 252)]
SNOW = [(92, 136, 184), (148, 186, 220), (202, 225, 240), (231, 243, 248), (251, 254, 249)]
TEAL = [(10, 30, 40), (20, 70, 66), (38, 122, 104), (92, 178, 140), (190, 236, 196)]
WAX = [GX[0], GX[1], (120, 146, 52), (186, 200, 88), (238, 238, 156)]
AMBER = [(92, 34, 28), (160, 76, 22), (224, 142, 36), (248, 196, 84), (255, 238, 176)]
RIND = [(34, 26, 41), (58, 48, 63), (84, 72, 85), (110, 98, 107), (148, 136, 137)]
AGATE_W = (236, 226, 245)


# --- helpers ----------------------------------------------------------------------------------
def mid(r, i, t=0.5):
    return lerp(r[i], r[min(4, i + 1)], t)


def paint(fn):
    img = new()
    for y in range(16):
        for x in range(16):
            put(img, x, y, fn(x, y))
    return img


def n(seed, x, y):
    """Tile-periodic hash noise."""
    return det(seed, x % 16, y % 16)


def done(img):
    """No pure grey: nudge any accidental r == g == b pixel toward blue."""
    px = img.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r, g, b, a = px[x, y]
            if a and r == g == b:
                px[x, y] = (r, g, min(255, b + 3) if b < 253 else b - 3, a)
    return img


def voronoi(seeds, x, y):
    """(nearest seed index, d1, d2) on the 16px torus."""
    ds = []
    for i, (sx, sy) in enumerate(seeds):
        dx = ((x + 0.5 - sx + 8) % 16) - 8
        dy = ((y + 0.5 - sy + 8) % 16) - 8
        ds.append((math.hypot(dx, dy), i))
    ds.sort()
    return ds[0][1], ds[0][0], ds[1][0]


def bead(img, x, y, r, shadow=None, size=2):
    """A small sphere lit from the top-left, with an optional cast shadow."""
    if size == 1:
        put(img, x, y, r[2])
        if shadow:
            put(img, x + 1, y + 1, shadow)
        return
    if size == 2:
        put(img, x, y, r[4]); put(img, x + 1, y, r[2])
        put(img, x, y + 1, r[2]); put(img, x + 1, y + 1, r[1])
        if shadow:
            put(img, x + 2, y + 1, shadow); put(img, x + 1, y + 2, shadow); put(img, x + 2, y + 2, shadow)
        return
    # 3: a plus with a glint
    for dx, dy, i in ((1, 0, 3), (0, 1, 3), (1, 1, 2), (2, 1, 1), (1, 2, 1), (0, 0, 3), (2, 0, 2),
                      (0, 2, 2), (2, 2, 0)):
        put(img, x + dx, y + dy, r[i])
    put(img, x + 1, y + 1, r[3]); put(img, x, y, r[4])
    if shadow:
        put(img, x + 3, y + 1, shadow); put(img, x + 3, y + 2, shadow)
        put(img, x + 1, y + 3, shadow); put(img, x + 2, y + 3, shadow)


# =============================================================================================
# STATION -- manufactured: plate, tread, foil
# =============================================================================================
def station_plating():
    """Interior of a hull plate: seamless, so a connected group reads as one pale sheet with a
    faint brushed grain and flush fasteners. Seams and bolts come from the _frame layer only."""
    def fn(x, y):
        k = n("plating", x // 3, y)
        if k < 0.10:
            return HULL[3]
        if k > 0.93:
            return mid(HULL, 1, 0.7)
        return HULL[2]
    img = paint(fn)
    for (x, y) in ((4, 4), (12, 12)):          # flush fasteners
        put(img, x, y, HULL[1]); put(img, x - 1, y - 1, HULL[4]); put(img, x + 1, y + 1, HULL[3])
    for x in range(8, 13):                     # stencil tick
        put(img, x, 5, mid(HULL, 1, 0.6))
    for x in range(3, 6):
        put(img, x, 11, mid(HULL, 1, 0.6))
    return done(img)


def station_plating_frame():
    img = new()
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            put(img, x, y, ST[1])
    for i in range(1, 15):
        put(img, i, 1, HULL[4]); put(img, 1, i, HULL[4])
    for i in range(1, 15):
        put(img, i, 14, HULL[1]); put(img, 14, i, HULL[1])
    put(img, 14, 1, HULL[2]); put(img, 1, 14, HULL[2])
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14), (7, 1), (8, 14), (1, 8), (14, 7)):
        put(img, x, y, ST[0])                  # bolts on the perimeter
    return img


def _tread(v):
    """Checker plate: 3px diagonal lugs alternating direction, with a polished walking lane."""
    dark = mid(ST, 1, 0.55)
    def lane(x, y):
        return 5 <= x <= 10
    img = paint(lambda x, y: ST[2] if lane(x, y) else dark)
    for y in range(16):                                   # lane edges: grime pushed aside
        if n("lane%d" % v, 4, y) < 0.6:
            put(img, 4, y, ST[1])
        if n("lane%d" % v, 11, y) < 0.6:
            put(img, 11, y, ST[1])
    for cy in range(4):
        for cx in range(4):
            fwd = (cx + cy) % 2 == 0
            pts = [(0, 0), (1, 1), (2, 2)] if fwd else [(2, 0), (1, 1), (0, 2)]
            worn = n("worn%d" % v, cx, cy) < 0.3
            for i, (dx, dy) in enumerate(pts):
                x, y = cx * 4 + dx, cy * 4 + dy
                ln = lane(x, y)
                c = ST[4] if ln else ST[3]
                if i == 0 and not ln:
                    c = mid(ST, 3, 0.6)
                if ln and worn and i == 2:
                    c = ST[3]
                put(img, x, y, c)
            x, y = cx * 4 + pts[2][0], cy * 4 + pts[2][1] + 1
            put(img, x, y, ST[1] if lane(x, y) else ST[0])   # occlusion under the lug's low end
    return done(wear(img, "tread%d" % v, ST, rate=0.03))


def station_thermal_blanket():
    """Quilted gold foil: diamond stitching on an 8px lattice, each pillow lit from the top-left,
    one glint per quilt."""
    def fn(x, y):
        u, w = (x + y) % 8, (x - y) % 8
        if u == 0 or w == 0:
            return FOIL[0]
        if u == 3 and w == 5:
            return FOIL[4]
        score = (4 - u) + (w - 4) * 0.45
        if u == 7 or w == 1:
            score -= 1.2                                   # pillow rolling into the far stitch
        if score > 1.9:
            return FOIL[3]
        if score > -0.8:
            return FOIL[2]
        if score > -2.4:
            return mid(FOIL, 1)
        return FOIL[1]
    return done(wear(paint(fn), "blanket", FOIL, rate=0.04))


# =============================================================================================
# LUNA -- dust, glass and basalt
# =============================================================================================
def luna_regolith_brick():
    """Two courses of pressed dust blocks. The top and left edge of each is fused to glass
    (cold glint), the body is pocked with pin craters."""
    glass = lerp(LU[4], (196, 222, 240), 0.55)
    def fn(x, y):
        row = y // 8
        ly, lx = y % 8, (x - row * 8) % 16
        if ly == 7 or lx == 15:
            return LU[0]
        if ly == 0:
            return glass if n("sinter", x, y) < 0.7 else LU[3]
        if lx == 0:
            return glass if n("sinter", x, y) < 0.35 else LU[3]
        if ly == 6 or lx == 14:
            return LU[1]
        k = n("dust", x, y)
        return LU[3] if k < 0.12 else mid(LU, 1, 0.6) if k > 0.9 else LU[2]
    img = paint(fn)
    for (x, y) in ((4, 3), (11, 4), (2, 5), (13, 2), (10, 11), (14, 12), (3, 12), (6, 10)):
        put(img, x, y, LU[0]); put(img, x + 1, y + 1, LU[4])      # pin crater: pit + lit rim
    put(img, 7, 2, (88, 110, 140)); put(img, 1, 10, (88, 110, 140))  # fused glass fleck
    return done(img)


def _raked(v):
    def fn(x, y):
        yy = (y + (1 if n("rakewave", x // 5, 0) < 0.5 else 0)) % 4
        k = n("rake%d" % v, x, y)
        if yy == 0:
            return LU[4] if k < 0.25 else LU[3]
        if yy == 3:
            return LU[1] if k < 0.2 else LU[2]
        return mid(LU, 2, 0.45) if k < 0.85 else LU[3]
    return paint(fn)


BOOT = [".####.",
        "######",
        "######",
        "######",
        "######",
        ".#####",
        ".####.",
        "......",
        ".####.",
        ".####.",
        ".####."]


def _bootprint(v):
    """v: 0 blank, 1 blank with pebbles, 2 left boot, 3 right boot."""
    img = _raked(v)
    if v == 1:
        bead(img, 10, 5, LU, LU[1], size=2)
        bead(img, 4, 11, LU, LU[1], size=1)
        put(img, 3, 10, LU[4])
    if v >= 2:
        ox, oy = (3, 2) if v == 2 else (7, 3)
        cells = set()
        for j, row in enumerate(BOOT):
            for i, ch in enumerate(row if v == 2 else row[::-1]):
                if ch == "#":
                    cells.add((ox + i, oy + j))
        for (x, y) in cells:
            j = y - oy
            c = LU[1] if j % 2 else mid(LU, 1, 0.6)        # tread bars pressed into the dust
            if (x, y - 1) not in cells or (x - 1, y) not in cells:
                c = LU[0]                                   # near wall of the print, in shadow
            put(img, x, y, c)
        for (x, y) in cells:                                # far lip, lit
            if (x, y + 1) not in cells and (x, y + 2) not in cells:
                put(img, x, y + 1, LU[4])
            if (x + 1, y) not in cells:
                put(img, x + 1, y, LU[3])
    return done(img)


MARE_W = [3, 2, 4, 3, 4]


def _mare_side(p, n_):
    """Columnar basalt: five uneven prisms, each lit on its left arris, snapped by cross joints.
    p / n_ bake a cap course on the top / bottom rows (an unlinked column end)."""
    img = new(16, 16, BASALT[2])
    x0 = 0
    for ci, w in enumerate(MARE_W):
        breaks = sorted({int(n("mare", ci, k) * 16) for k in range(2)})
        seg = 0
        for y in range(16):
            if y in breaks:
                seg += 1
            dark = n("maretone", ci, seg % max(1, len(breaks))) < 0.4
            for i in range(w):
                c = BASALT[1] if dark else BASALT[2]
                if i == 0:
                    c = BASALT[3]
                if i == w - 1:
                    c = BASALT[0]
                elif i == w - 2 and w >= 4:
                    c = BASALT[1]
                if y in breaks:
                    c = BASALT[0] if i < w - 1 else BASALT[0]
                elif (y - 1) % 16 in breaks and 0 < i < w - 1:
                    c = BASALT[3] if not dark else BASALT[2]
                put(img, x0 + i, y, c)
        put(img, x0, (breaks[0] + 1) % 16, BASALT[4])
        x0 += w
    wear(img, "mare", BASALT, rate=0.03)
    if p:
        for x in range(16):
            put(img, x, 0, BASALT[4] if x % 5 else BASALT[3]); put(img, x, 1, BASALT[3]); put(img, x, 2, BASALT[0])
    if n_:
        for x in range(16):
            put(img, x, 13, BASALT[3]); put(img, x, 14, BASALT[2]); put(img, x, 15, BASALT[0])
        put(img, 0, 13, BASALT[4])
    return done(img)


HEX_SEEDS = [(c * 8 + 3.5 + (det("hexx", c, r) - 0.5) * 1.6, r * 8 + (4 if c else 0) + 3.5 + (det("hexy", c, r) - 0.5) * 1.6)
             for c in range(2) for r in range(2)]


def luna_mare_column_end():
    """Hex end grain: four prism heads seen from above, each a slightly different height."""
    cell = {}
    for y in range(16):
        for x in range(16):
            i, d1, d2 = voronoi(HEX_SEEDS, x, y)
            cell[(x, y)] = None if d2 - d1 < 1.1 else i
    tone = [BASALT[2], BASALT[1], mid(BASALT, 2, 0.5), mid(BASALT, 1, 0.5)]
    img = new(16, 16, BASALT[0])
    for y in range(16):
        for x in range(16):
            i = cell[(x, y)]
            if i is None:
                continue
            up, left = cell[(x, (y - 1) % 16)], cell[((x - 1) % 16, y)]
            down, right = cell[(x, (y + 1) % 16)], cell[((x + 1) % 16, y)]
            c = tone[i]
            if up != i and left != i:
                c = BASALT[4]
            elif up != i or left != i:
                c = BASALT[3]
            elif down != i or right != i:
                c = BASALT[1] if c != BASALT[1] else mid(BASALT, 0, 0.6)
            put(img, x, y, c)
    return done(wear(img, "mareend", BASALT, rate=0.04))


def luna_bead_glass():
    """Dust set with orange and green volcanic glass beads."""
    def fn(x, y):
        k = n("beaddust", x, y)
        return LU[3] if k < 0.16 else LU[1] if k > 0.92 else mid(LU, 2, 0.35)
    img = paint(fn)
    sh = mid(LU, 0, 0.6)
    bead(img, 2, 2, BEAD_O, sh, 3)
    bead(img, 10, 9, BEAD_G, sh, 3)
    bead(img, 9, 2, BEAD_G, sh, 2)
    bead(img, 13, 5, BEAD_O, sh, 2)
    bead(img, 4, 8, BEAD_O, sh, 2)
    bead(img, 1, 12, BEAD_G, sh, 2)
    bead(img, 7, 13, BEAD_O, sh, 2)
    bead(img, 14, 13, BEAD_O, sh, 1)
    bead(img, 6, 5, BEAD_G, sh, 1)
    bead(img, 12, 1, BEAD_O, sh, 1)
    bead(img, 0, 7, BEAD_G, sh, 1)
    bead(img, 8, 7, BEAD_O, sh, 1)
    return done(img)


# =============================================================================================
# MARS -- rust and sediment
# =============================================================================================
def _rust_plate(v):
    """Steel plate; rust blooms out of the rivets and runs down the face below them."""
    img = new(16, 16, STEEL[2])
    bevel(img, 0, 0, 16, 16, STEEL, fill=2)
    for x in range(3, 13):                              # rolled grain
        if n("grain%d" % v, x, 0) < 0.5:
            put(img, x, 7 + (x % 2), mid(STEEL, 2, 0.45))
    rivets = ((3, 3), (12, 3), (3, 12), (12, 12))
    for ri, (rx, ry) in enumerate(rivets):
        strength = n("rust%d" % v, ri, 0)
        rad = 1.6 + strength * 2.2
        for y in range(1, 15):
            for x in range(1, 15):
                d = math.hypot(x - rx, (y - ry) * 0.9)
                k = n("bloom%d" % v, x, y)
                if d < rad and k < 1.15 - d / rad:
                    put(img, x, y, MA[1] if d < rad * 0.45 else MA[2] if k < 0.6 else MA[3])
        ln = 2 + int(strength * 7)                      # drip streak
        for i in range(1, ln + 1):
            y = ry + i
            if y > 14:
                break
            t = i / float(ln)
            put(img, rx, y, MA[2] if t < 0.4 else MA[3] if t < 0.75 else lerp(MA[3], STEEL[2], 0.55))
            if i < ln * 0.5 and n("drip%d" % v, ri, i) < 0.6:
                put(img, rx + 1, y, lerp(MA[2], STEEL[2], 0.5))
        put(img, rx, ry, STEEL[4]); put(img, rx + 1, ry, STEEL[3])
        put(img, rx, ry + 1, STEEL[3]); put(img, rx + 1, ry + 1, MA[0])
        put(img, rx + 2, ry + 1, MA[0]); put(img, rx + 1, ry + 2, MA[0])
    return done(wear(img, "rustplate%d" % v, STEEL, rate=0.04))


def mars_wind_strata_brick():
    """Wavy sediment bands; every brick has its own band phase, so the strata step at each joint."""
    bands = [MA[2], MA[3], SAND[3], MA[3], MA[2], MA[1], MA[3], SAND[2]]
    def fn(x, y):
        row = y // 8
        bx = ((x - row * 4) % 16) // 8
        lx, ly = (x - row * 4) % 8, y % 8
        if ly == 7 or lx == 7:
            return MA[0]
        bid = row * 2 + bx
        wave = round(1.3 * math.sin((lx + bid * 3) * math.pi / 4.0 + bid))
        c = bands[(ly + wave + bid * 3) % len(bands)]
        if ly == 0:
            c = lerp(c, SAND[4], 0.35)
        if ly == 6 or lx == 6:
            c = lerp(c, MA[0], 0.35)
        return c
    return done(wear(paint(fn), "strata", MA, rate=0.03))


def mars_spherule_stone():
    """Pale laminated sandstone studded with dark hematite spherules ("blueberries")."""
    def fn(x, y):
        k = n("sandst", x, y)
        lam = (y + (1 if n("lamw", x // 4, 0) < 0.5 else 0)) % 5
        if lam == 0:
            return SAND[3] if k < 0.8 else SAND[4]
        if lam == 3 and k < 0.45:
            return mid(SAND, 1, 0.6)
        return SAND[2] if k < 0.9 else SAND[3]
    img = paint(fn)
    for (x, y) in ((2, 1), (9, 3), (5, 7), (12, 9), (1, 11), (8, 13)):
        bead(img, x, y, [HEMA[0], HEMA[0], HEMA[2], HEMA[3], HEMA[4]], SAND[0], 2)
        put(img, x + 1, y + 1, HEMA[0]); put(img, x + 1, y, HEMA[1]); put(img, x, y + 1, HEMA[1])
        put(img, x, y, HEMA[4])
    for (x, y) in ((6, 1), (13, 2), (14, 6), (1, 6), (9, 8), (4, 12), (14, 13), (11, 14), (6, 10)):
        put(img, x, y, HEMA[1]); put(img, x + 1, y + 1, SAND[1]); put(img, x - 1, y - 1, SAND[4])
    return done(img)


def mars_dust_drift_tile():
    """The clean tile: a cut red stone slab with a chamfer and four dowel holes."""
    img = new(16, 16, MA[2])
    bevel(img, 0, 0, 16, 16, MA, fill=2)
    for i in range(2, 14):
        put(img, i, 2, MA[1]); put(img, 2, i, MA[1])
        put(img, i, 13, MA[3]); put(img, 13, i, MA[3])
    for (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11)):
        put(img, x, y, MA[0]); put(img, x + 1, y + 1, MA[3])
    return done(wear(img, "drifttile", MA, rate=0.04))


def mars_dust_drift_tile_top():
    """The same tile with a drift banked into its top-left corner: three contour bands and a
    scatter of grains ahead of the toe."""
    img = mars_dust_drift_tile()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5, (y + 0.5) * 1.15) + 1.2 * math.sin(x * 0.9) * 0.6
            k = n("drift", x, y)
            if d < 4.2:
                c = SAND[3] if k < 0.8 else SAND[4]
            elif d < 5.2:
                c = SAND[4]                       # crest catching the light
            elif d < 8.2:
                c = SAND[2] if k < 0.85 else SAND[3]
            elif d < 9.4:
                c = SAND[1]                       # slip face in shadow
            elif d < 12.5 and k < 0.42 - (d - 9.4) * 0.11:
                c = SAND[2]
            else:
                continue
            put(img, x, y, c)
    return done(img)


# =============================================================================================
# CINDARA -- volcanic
# =============================================================================================
EMBER_MAIN = [(5, 0), (5, 1), (6, 2), (6, 3), (7, 4), (8, 5), (8, 6), (9, 7), (9, 8), (8, 9), (7, 10),
              (7, 11), (6, 12), (5, 13), (5, 14), (5, 15)]
EMBER_BRANCH = [(9, 5), (10, 5), (11, 4), (12, 4), (13, 4), (14, 3), (15, 3), (0, 3), (1, 4),
                (10, 9), (11, 10), (12, 10), (13, 11)]
EMBER_SEEDS = [(2.5, 2), (11, 1.5), (13.5, 8), (3, 8), (10.5, 13.5), (2, 13.5), (7.5, 6)]


def cindara_ember_basalt():
    """Black basalt broken into blocky plates; the crack gutters are dark red under the glow and
    the rock beside them is heat-stained."""
    cracks = set(EMBER_MAIN) | set(EMBER_BRANCH)
    def fn(x, y):
        i, d1, d2 = voronoi(EMBER_SEEDS, x, y)
        if d2 - d1 < 0.8:
            return CBAS[0]
        c = [CBAS[1], CBAS[2], mid(CBAS, 1)][i % 3]
        return c
    img = paint(fn)
    px = img.load()
    out = img.copy()
    for y in range(16):
        for x in range(16):
            if px[x, y][:3] != CBAS[0] and px[(x - 1) % 16, (y - 1) % 16][:3] == CBAS[0]:
                put(out, x, y, CBAS[3])
    for (x, y) in cracks:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if ((x + dx) % 16, (y + dy) % 16) not in cracks:
                put(out, x + dx, y + dy, lerp(CBAS[2], CI[1], 0.7))
    for (x, y) in cracks:
        put(out, x, y, CI[1])
    return done(out)


def cindara_ember_basalt_glow():
    def frame(t):
        k = 0.5 + 0.5 * math.cos(2 * math.pi * t)
        img = new()
        for i, (x, y) in enumerate(EMBER_MAIN):
            hot = 0.5 + 0.5 * math.cos(2 * math.pi * (t - i / 32.0))
            put(img, x, y, lerp(CI[3], CI[4], hot))
        for (x, y) in EMBER_BRANCH:
            put(img, x, y, lerp(CI[2], CI[3], k))
        return img
    return animate("cindara_ember_basalt_glow", "breathing", frame)


def cindara_slag_brick():
    """Ropey slag: nested pahoehoe folds across each brick, with a temper-colour sheen that runs
    violet to straw along the crests."""
    def tint(t):
        t = max(0.0, min(0.999, t)) * (len(HEAT) - 1)
        i = int(t)
        return lerp(HEAT[i], HEAT[i + 1], t - i)
    def fn(x, y):
        row = y // 8
        lx, ly = (x - row * 8) % 16, y % 8
        if ly == 7 or lx == 15:
            return SLAG[0]
        arch = round(2.4 * math.sin(math.pi * lx / 14.0))
        q = (ly + arch + row) % 3
        t = lx / 14.0 if row == 0 else 1.0 - lx / 14.0
        if q == 0:
            return lerp(SLAG[4], tint(t), 0.72)
        if q == 1:
            return lerp(SLAG[2], tint(t), 0.30)
        return SLAG[1]
    return done(wear(paint(fn), "slag", SLAG, rate=0.03))


def _ash_tile(v):
    """Pale ash tile stamped with concentric scorch rings (pressed in: dark groove, lit far lip)."""
    img = new(16, 16, ASH[2])
    bevel(img, 0, 0, 16, 16, ASH, fill=2)
    wear(img, "ash%d" % v, ASH, rate=0.07)
    ring = {}
    for y in range(1, 15):
        for x in range(1, 15):
            r = math.hypot(x - 7.5, y - 7.5)
            for j, (rr, w) in enumerate(((1.6, 0.75), (4.3, 0.55), (6.5, 0.5))):
                if abs(r - rr) < w and n("ringgap%d" % v, x, y) > 0.07 + 0.05 * j:
                    ring[(x, y)] = j
    for (x, y) in ring:
        if (x - 1, y - 1) not in ring and (x - 1, y) not in ring and 1 <= x - 1 and 1 <= y - 1:
            pass
    for (x, y), j in ring.items():
        if (x + 1, y + 1) not in ring and x + 1 < 15 and y + 1 < 15:
            put(img, x + 1, y + 1, ASH[4])
    for (x, y), j in ring.items():
        put(img, x, y, SCORCH[j] if n("ringtone%d" % v, x, y) < 0.8 else SCORCH[min(2, j + 1)])
    return done(img)


# =============================================================================================
# GLACIRA -- ice and rime
# =============================================================================================
def glacira_bubble_ice():
    """Clear ice (translucent): faint diagonal sheen and three stacked columns of frozen bubbles."""
    img = new()
    body = lerp(GL[3], GL[4], 0.45)
    for y in range(16):
        for x in range(16):
            s = (x + y) % 16
            if s in (4, 5) and n("sheen", x, y) < 0.8:
                put(img, x, y, GL[4], 196)
            elif s in (11,) and n("sheen", x, y) < 0.5:
                put(img, x, y, GL[3], 176)
            else:
                put(img, x, y, body, 150)
    white = (244, 252, 255)
    cols = ((3, ((1, 2), (4, 1), (6, 1), (9, 2), (12, 1), (14, 1))),
            (8, ((0, 1), (2, 1), (5, 2), (8, 1), (10, 1), (13, 2))),
            (12, ((3, 1), (6, 2), (9, 1), (11, 1), (15, 1))))
    for cx, bubbles in cols:
        for by, sz in bubbles:
            x = cx + (1 if n("bubx", cx, by) < 0.3 else 0)
            if sz == 2:
                put(img, x, by, white, 240); put(img, x + 1, by, GL[4], 220)
                put(img, x, by + 1, GL[4], 220); put(img, x + 1, by + 1, GL[2], 215)
            else:
                put(img, x, by, white, 232); put(img, x + 1, by + 1, GL[2], 190)
    return img


def glacira_bubble_ice_frame():
    img = new()
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            put(img, x, y, RIME[1], 235)
    for i in range(1, 15):
        put(img, i, 1, RIME[4], 235); put(img, 1, i, RIME[4], 235)
    for i in range(1, 15):
        put(img, i, 14, RIME[2], 225); put(img, 14, i, RIME[2], 225)
    put(img, 14, 1, RIME[3], 235); put(img, 1, 14, RIME[3], 235)
    return img


def glacira_cryo_fin_plate():
    """Fin face (the model samples 1..15); row 0 is the fin's rimed edge strip."""
    img = new(16, 16, COLD[2])
    bevel(img, 1, 1, 15, 15, COLD, fill=2)
    for i in range(4, 12):                              # shadow where the spine passes through
        put(img, i, 4, COLD[1]); put(img, 4, i, COLD[1]); put(img, i, 11, COLD[3]); put(img, 11, i, COLD[3])
    rect(img, 5, 5, 11, 11, COLD[0])
    for y in range(1, 15):
        for x in range(1, 15):
            edge = min(x - 1, y - 1, 14 - x, 14 - y)
            k = n("finrime", x, y)
            lim = (0.95, 0.5, 0.16)[edge] if edge < 3 else 0.0
            if x + y < 12:
                lim *= 1.25
            if k < lim and not (4 <= x <= 11 and 4 <= y <= 11):
                put(img, x, y, RIME[4] if k < lim * 0.5 else RIME[3])
    for x in range(16):
        put(img, x, 0, RIME[4] if n("finedge", x, 0) < 0.55 else COLD[3])
        put(img, x, 15, COLD[1]); put(img, 0, x, COLD[3]); put(img, 15, x, COLD[1])
    put(img, 0, 0, RIME[4])
    return done(img)


def glacira_cryo_fin_spine():
    """Spine side (columns 5..10), shaded as a tube with a dark gap under each fin."""
    img = new(16, 16, COLD[1])
    shade = [COLD[3], COLD[4], COLD[3], COLD[2], COLD[1], COLD[0]]
    for y in range(16):
        for i in range(6):
            put(img, 5 + i, y, shade[i])
    for k in range(5):
        y = int(round(16 - (1.1 + 3.2 * k)))            # just under a fin (v runs downward)
        for i in range(6):
            put(img, 5 + i, y % 16, COLD[0] if i else COLD[1])
    return done(img)


def glacira_cryo_fin_cap():
    """Spine end / coupling collar (the collar face is 4..12, the bare spine end 5..11)."""
    img = new(16, 16, COLD[1])
    bevel(img, 4, 4, 12, 12, COLD, fill=3)
    bevel(img, 6, 6, 10, 10, [COLD[4], COLD[3], COLD[1], COLD[0], COLD[0]], fill=2)
    put(img, 7, 7, GL[2]); put(img, 8, 8, GL[3]); put(img, 8, 7, GL[2]); put(img, 7, 8, GL[2])
    return done(img)


def _frost_plate(v):
    """Cold steel plate; fern frost grows inward from all four edges."""
    img = new(16, 16, COLD[2])
    bevel(img, 0, 0, 16, 16, COLD, fill=2)
    for x in range(2, 14):
        if n("fgrain%d" % v, x, 3) < 0.3:
            put(img, x, 5 + x % 3 * 3, mid(COLD, 1, 0.6))
    for x in range(6, 10):                               # recessed slot
        put(img, x, 7, COLD[0]); put(img, x, 8, COLD[3])
    frost = {}
    def f(x, y, lvl):
        if 0 <= x < 16 and 0 <= y < 16:
            frost[(x, y)] = max(lvl, frost.get((x, y), 0))
    for i in range(16):                                  # rimed edge, broken
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            k = n("rimb%d" % v, x, y)
            if k < 0.8:
                f(x, y, 2 if k < 0.25 else 1)
    for side in range(4):
        for s in range(4):
            pos = 1 + s * 4 + int(n("fpos%d" % v, side, s) * 2)
            ln = 2 + int(n("flen%d" % v, side, s) * 5)
            lean = -1 if n("flean%d" % v, side, s) < 0.5 else 1
            for i in range(1, ln + 1):
                def at(a, b):                            # a = along the edge, b = inward
                    return {0: (a, b), 1: (a, 15 - b), 2: (b, a), 3: (15 - b, a)}[side]
                a = pos + (lean if i >= 4 else 0)        # the frond kinks once
                f(*at(a, i), 3 if i < ln else 2)
                if i < ln and i >= 2:                    # barbs alternate side to side
                    f(*at(a + (1 if i % 2 else -1), i - 1), 1)
    for (x, y), lvl in frost.items():
        put(img, x, y, (RIME[2], RIME[3], RIME[4])[lvl - 1] if lvl < 3 else RIME[4])
    return done(img)


def glacira_snow_brick():
    """Sawn snow blocks: white faces, blue shadow seams, fine diagonal saw marks."""
    def fn(x, y):
        row = y // 8
        ly = y % 8
        j = (5, 12)[row]
        lx = (x - j) % 16
        if ly == 7:
            return SNOW[0] if n("seam", x, y) < 0.8 else SNOW[1]
        if lx == 0:
            return SNOW[0] if ly > 1 else SNOW[1]
        if ly == 6:
            return SNOW[2]
        if ly == 0:
            return SNOW[4]
        if lx == 1:
            return SNOW[4]
        if lx == 15:
            return SNOW[2]
        saw = (x + (2 * ly if row == 0 else -2 * ly)) % 5
        if saw == 0:
            return SNOW[2]
        return SNOW[3] if n("snow", x, y) < 0.9 else SNOW[4]
    return done(paint(fn))


# =============================================================================================
# GREENXERTZ -- grown, not built
# =============================================================================================
def greenxertz_woven_chitin():
    """Over-under basket weave: green warp bands across, teal weft bands down, each a glossy
    3px strap with a specular pip where it crests."""
    def fn(x, y):
        cx, cy, lx, ly = x // 4, y // 4, x % 4, y % 4
        h_top = (cx + cy) % 2 == 0
        if lx == 3 and ly == 3:
            return GX[0]
        horiz = (ly < 3 and lx == 3) or (ly < 3 and lx < 3 and h_top)
        if horiz:
            if lx == 3:                                   # diving between two crossings
                return (GX[2], GX[1], GX[0])[ly]
            if ly == 0:
                return GX[4] if lx == 1 else GX[3]
            return GX[2] if ly == 1 else GX[1]
        if ly == 3:
            return (TEAL[2], TEAL[1], TEAL[0])[lx]
        if lx == 0:
            return TEAL[4] if ly == 1 else TEAL[3]
        return TEAL[2] if lx == 1 else TEAL[1]
    return done(paint(fn))


SPORE_PORES = [(3, 3), (6, 2), (4, 6), (11, 5), (14, 7), (12, 9), (5, 12), (8, 13), (2, 14), (14, 13), (9, 0)]
SPORE_PUFF = [(4, 6), (12, 9), (8, 13)]
SPORE_LIT = [(3, 3), (11, 5), (5, 12), (14, 13), (9, 0)]


def greenxertz_spore_tile():
    """A fleshy mat with clustered pores (pits lit on their far rim)."""
    def fn(x, y):
        k = (math.sin(2 * math.pi * (x + 2 * y) / 16.0) + math.sin(2 * math.pi * (3 * x - y) / 16.0 + 1.0)
             + (n("mat", x, y) - 0.5) * 0.9)
        return GX[2] if k > 0.9 else GX[1] if k > -0.9 else mid(GX, 0, 0.7)
    img = paint(fn)
    for (x, y) in SPORE_PORES:                            # raised lip, lit on the far side
        put(img, x + 1, y, GX[3]); put(img, x, y + 1, GX[3]); put(img, x + 1, y + 1, GX[2])
        put(img, x - 1, y, GX[2]); put(img, x, y - 1, GX[2])
    for (x, y) in SPORE_PORES:
        put(img, x, y, GX[0])
    return done(img)


def greenxertz_spore_tile_glow():
    lit, hot = GX[4], (232, 248, 176)
    def frame(t):
        img = new()
        k = 0.5 + 0.5 * math.cos(2 * math.pi * t)
        for (x, y) in SPORE_LIT:
            put(img, x, y, lerp(GX[3], lit, k))
        for j, (x, y) in enumerate(SPORE_PUFF):
            s = int(round(((t + j / 3.0) % 1.0) * 8)) % 8
            put(img, x, y, hot if s in (0, 1, 2) else lit)
            path = {1: [(0, -1)], 2: [(0, -2), (1, -1)], 3: [(-1, -3), (1, -2)], 4: [(-1, -4), (2, -3)],
                    5: [(2, -4)]}.get(s, [])
            for dx, dy in path:
                put(img, x + dx, y + dy, lit)
        return img
    return animate("greenxertz_spore_tile_glow", "breathing", frame)


COMB_SEEDS = [(c * 4 + 2 + (det("combx", c, r) - 0.5) * 1.1,
               r * 16 / 3.0 + (2.67 if c % 2 else 0) + 2.4 + (det("comby", c, r) - 0.5) * 1.4)
              for c in range(4) for r in range(3)]
COMB_CAPPED = {1, 5, 6, 10}


def greenxertz_hive_comb():
    """Uneven hex cells with waxy walls; four are capped with domed amber resin."""
    cell = {}
    def fn(x, y):
        i, d1, d2 = voronoi(COMB_SEEDS, x, y)
        cell[(x, y)] = (i, d2 - d1 < 0.95)
        return WAX[3] if d2 - d1 < 0.95 else GX[0]
    img = paint(fn)
    out = img.copy()
    for y in range(16):
        for x in range(16):
            i, wall = cell[(x, y)]
            up, left = cell[(x, (y - 1) % 16)], cell[((x - 1) % 16, y)]
            down, right = cell[(x, (y + 1) % 16)], cell[((x + 1) % 16, y)]
            if wall:
                if not down[1] or not right[1]:
                    put(out, x, y, WAX[2])                # wall edge facing away from the light
                elif not up[1] and not left[1]:
                    put(out, x, y, WAX[4])
                continue
            if i in COMB_CAPPED:
                c = AMBER[2]
                if up[1] or left[1]:
                    c = AMBER[3]
                if down[1] or right[1]:
                    c = AMBER[1]
                if up[1] and left[1]:
                    c = AMBER[4]
                put(out, x, y, c)
            else:
                c = mid(GX, 0, 0.6)
                if up[1] or left[1]:
                    c = (9, 22, 26)                        # shadow under the lit-side wall
                put(out, x, y, c)
    return done(out)


# =============================================================================================
# NYXARA -- cut crystal
# =============================================================================================
_FP = {(0, 0): (1, 1), (1, 0): (9, 3), (0, 1): (4, 10), (1, 1): (12, 8)}


def _fv(i, j):
    bx, by = _FP[(i % 2, j % 2)]
    return (bx + 16 * (i // 2), by + 16 * (j // 2))


def _facets():
    tris = []
    for i in range(-2, 3):
        for j in range(-2, 3):
            a, b, c, d = _fv(i, j), _fv(i + 1, j), _fv(i + 1, j + 1), _fv(i, j + 1)
            if (i + j) % 2 == 0:
                tris.append(((i % 2, j % 2, 0), (a, b, c))); tris.append(((i % 2, j % 2, 1), (a, c, d)))
            else:
                tris.append(((i % 2, j % 2, 0), (a, b, d))); tris.append(((i % 2, j % 2, 1), (b, c, d)))
    return tris


def _in_tri(p, t):
    def s(a, b):
        return (p[0] - b[0]) * (a[1] - b[1]) - (a[0] - b[0]) * (p[1] - b[1])
    d1, d2, d3 = s(t[0], t[1]), s(t[1], t[2]), s(t[2], t[0])
    return not ((d1 < 0 or d2 < 0 or d3 < 0) and (d1 > 0 or d2 > 0 or d3 > 0))


def nyxara_faceted_crystal():
    """A solid cut gem: eight large triangular facets, flat-shaded, with one facet per face
    catching the light and a thin glint along every facet's lit edge."""
    tone = {(0, 0, 0): NY[2], (0, 0, 1): mid(NY, 1, 0.4), (1, 0, 0): NY[4], (1, 0, 1): NY[3],
            (0, 1, 0): NY[1], (0, 1, 1): mid(NY, 2, 0.5), (1, 1, 0): mid(NY, 2, 0.15), (1, 1, 1): mid(NY, 0, 0.7)}
    tris = _facets()
    ids = {}
    for y in range(16):
        for x in range(16):
            p = (x + 0.53, y + 0.47)
            ids[(x, y)] = next((tid for tid, t in tris if _in_tri(p, t)), (0, 0, 0))
    img = new()
    for y in range(16):
        for x in range(16):
            me = ids[(x, y)]
            c = tone[me]
            ul = ids[((x - 1) % 16, (y - 1) % 16)]
            dr = ids[((x + 1) % 16, (y + 1) % 16)]
            if ul != me and me != (1, 0, 0):
                c = lerp(c, NY[4], 0.42)
            elif dr != me and me == (1, 0, 0):
                c = lerp(c, NY[3], 0.5)
            put(img, x, y, c)
    bx, by = 8, 2                                         # star glint on the bright facet
    for (x, y) in ids:
        pass
    cand = [(x, y) for (x, y), v in ids.items() if v == (1, 0, 0)]
    if cand:
        cx = sorted(cand, key=lambda q: (q[0] + q[1], q[1]))[len(cand) // 4]
        put(img, cx[0], cx[1], (248, 236, 255))
    return done(img)


SHARD_SEEDS = [(c * 16 / 3.0 + 2.6 + (det("shx", c, r) - 0.5) * 3.4, r * 16 / 3.0 + 2.6 + (det("shy", c, r) - 0.5) * 3.4)
               for c in range(3) for r in range(3)]


def nyxara_shard_mosaic():
    """Broken crystal shards bedded in dark grout: irregular cells, each bevelled, mixed tones."""
    tones = [ramp5(NY[3]), ramp5((112, 70, 168)), ramp5(VOID[3]), ramp5((150, 92, 196)), ramp5(NY[2]),
             ramp5((96, 110, 206)), ramp5((176, 120, 214)), ramp5(VOID[2]), ramp5((126, 96, 200))]
    grout = (17, 10, 30)
    cell = {}
    for y in range(16):
        for x in range(16):
            i, d1, d2 = voronoi(SHARD_SEEDS, x, y)
            cell[(x, y)] = None if d2 - d1 < 0.95 else i
    img = new(16, 16, grout)
    for y in range(16):
        for x in range(16):
            i = cell[(x, y)]
            if i is None:
                if cell[((x - 1) % 16, (y - 1) % 16)] is not None and n("grout", x, y) < 0.5:
                    put(img, x, y, NY[0])
                continue
            r = tones[i % len(tones)]
            up, left = cell[(x, (y - 1) % 16)], cell[((x - 1) % 16, y)]
            down, right = cell[(x, (y + 1) % 16)], cell[((x + 1) % 16, y)]
            c = r[2]
            if up != i and left != i:
                c = r[4]
            elif up != i or left != i:
                c = r[3]
            elif down != i or right != i:
                c = r[1]
            put(img, x, y, c)
    return done(img)


def nyxara_geode_block():
    """A geode cut flat: dull rind, banded agate rings, a crystal-lined hollow at the centre."""
    def fn(x, y):
        dx, dy = x - 7.5, y - 7.5
        th = math.atan2(dy, dx)
        r = math.hypot(dx, dy) * (1 + 0.07 * math.sin(3 * th + 1.0) + 0.04 * math.sin(5 * th + 2.0))
        rc = math.hypot(dx * 0.62, dy * 1.25)             # the hollow is a slot, not a pupil
        k = n("geode", x, y)
        if rc < 1.25:
            return (13, 7, 26)
        if rc < 2.3:                                      # crystal teeth lining the hollow
            return (246, 236, 255) if (x + y) % 2 == 0 else NY[4] if k < 0.5 else NY[3]
        if r < 4.1:
            return NY[2]
        if r < 4.8:
            return AGATE_W
        if r < 5.6:
            return NY[3]
        if r < 6.2:
            return NY[1]
        if r < 6.9:
            return NY[4] if k < 0.8 else NY[3]
        if r < 7.5:
            return RIND[0]
        return RIND[3] if k < 0.14 else RIND[1] if k > 0.86 else RIND[2]
    return done(paint(fn))


# =============================================================================================
# Models
# =============================================================================================
def _cube_model(c, all_tid, top=None, rt=None):
    tx = {"particle": c.tex(all_tid), "all": c.tex(all_tid)}
    if top:
        tx["top"] = c.tex(top)
        faces = {f: "#all" for f in FACES}
        faces["up"] = "#top"
    else:
        faces = {f: "#all" for f in FACES}
    m = {"textures": tx, "elements": [c.el([0, 0, 0], [16, 16, 16], faces, uv=[0, 0, 16, 16])]}
    if rt:
        m["render_type"] = rt
    return m


def _weighted(tids, weights):
    """Flat tile with seeded-wear variants: one cube model per texture, picked by weight."""
    def model(c):
        return {t: _cube_model(c, t) for t in tids}
    def blockstate(c):
        return {"variants": {"": [dict({"model": c.model(t)}, **({"weight": w} if w != 1 else {}))
                                  for t, w in zip(tids, weights)]}}
    return model, blockstate


def _tile(bid, count=3):
    tids = [bid] + ["%s_v%d" % (bid, i) for i in range(1, count)]
    model, blockstate = _weighted(tids, [1] * count)
    return Block(bid, shape="custom", textures={t: t for t in tids}, model=model, blockstate=blockstate,
                 item_model=bid)


def _bootprint_model(c):
    base = "luna_bootprint_tile"
    return {t: _cube_model(c, base, top=t) for t in (base, base + "_b", base + "_l", base + "_r")}


def _bootprint_state(c):
    base = "luna_bootprint_tile"
    return {"variants": {"": [{"model": c.model(base), "weight": 3}, {"model": c.model(base + "_b"), "weight": 3},
                              {"model": c.model(base + "_l")}, {"model": c.model(base + "_r")}]}}


def _drift_model(c):
    return {"mars_dust_drift_tile": _cube_model(c, "mars_dust_drift_tile", top="mars_dust_drift_tile_top")}


def _pillar_state(c, bid):
    """DecorPillarBlock states. Models are drawn along +y with c<p><n> = cap on the +y / -y end;
    x=90 points +y at north (-z), so the z axis swaps the two ends."""
    out = {}
    for axis, rot, flip in (("y", {}, False), ("z", {"x": 90}, True), ("x", {"x": 90, "y": 90}, False)):
        for pos in (False, True):
            for neg in (False, True):
                cp, cn = int(not pos), int(not neg)
                if flip:
                    cp, cn = cn, cp
                v = {"model": c.model("%s_c%d%d" % (bid, cp, cn))}
                v.update(rot)
                out["axis=%s,neg_link=%s,pos_link=%s" % (axis, str(neg).lower(), str(pos).lower())] = v
    return {"variants": out}


def _mare_model(c):
    out = {}
    for p in (1, 0):
        for q in (1, 0):
            side = "luna_mare_column_c%d%d" % (p, q)
            faces = {f: "#side" for f in ("north", "east", "south", "west")}
            faces["up"] = faces["down"] = "#end"
            out["luna_mare_column_c%d%d" % (p, q)] = {
                "textures": {"particle": c.tex(side), "side": c.tex(side), "end": c.tex("luna_mare_column_end")},
                "elements": [c.el([0, 0, 0], [16, 16, 16], faces, uv=[0, 0, 16, 16])]}
    return out


FIN_Y = [round(1.1 + 3.2 * k, 1) for k in range(5)]


def _fin_model(c):
    out = {}
    for p in (1, 0):
        for q in (1, 0):
            y0, y1 = (0.5 if q else 0), (15.5 if p else 16)
            sides = {f: {"texture": "#spine", "uv": [5, 16 - y1, 11, 16 - y0]}
                     for f in ("north", "east", "south", "west")}
            if not p:
                sides["up"] = {"texture": "#cap", "uv": [5, 5, 11, 11], "cullface": "up"}
            if not q:
                sides["down"] = {"texture": "#cap", "uv": [5, 5, 11, 11], "cullface": "down"}
            els = [c.el([5, y0, 5], [11, y1, 11], sides, cull=False)]
            for a in FIN_Y:
                faces = {f: {"texture": "#plate", "uv": [1, 0, 15, 1]} for f in ("north", "east", "south", "west")}
                faces["up"] = {"texture": "#plate", "uv": [1, 1, 15, 15]}
                faces["down"] = {"texture": "#plate", "uv": [1, 1, 15, 15]}
                els.append(c.el([1, a, 1], [15, round(a + 1, 1), 15], faces, cull=False))
            for on, lo, hi, end in ((q, 0, 0.5, "down"), (p, 15.5, 16, "up")):
                if on:
                    faces = {f: {"texture": "#cap", "uv": [4, 4, 12, 5]} for f in ("north", "east", "south", "west")}
                    faces["up"] = {"texture": "#cap", "uv": [4, 4, 12, 12]}
                    faces["down"] = {"texture": "#cap", "uv": [4, 4, 12, 12]}
                    faces[end]["cullface"] = end
                    els.append(c.el([4, lo, 4], [12, hi, 12], faces, cull=False))
            out["glacira_cryo_fin_c%d%d" % (p, q)] = {
                "textures": {"particle": c.tex("glacira_cryo_fin_plate"), "plate": c.tex("glacira_cryo_fin_plate"),
                             "spine": c.tex("glacira_cryo_fin_spine"), "cap": c.tex("glacira_cryo_fin_cap")},
                "elements": els}
    return out


# =============================================================================================
# Declarations
# =============================================================================================
def _variants(fn, bid, count=3):
    out = {bid: lambda: fn(0)}
    for i in range(1, count):
        out["%s_v%d" % (bid, i)] = (lambda i=i: fn(i))
    return out


TEXTURES = {
    "station_plating": station_plating,
    "station_plating_frame": station_plating_frame,
    "station_thermal_blanket": station_thermal_blanket,
    "luna_regolith_brick": luna_regolith_brick,
    "luna_bootprint_tile": lambda: _bootprint(0),
    "luna_bootprint_tile_b": lambda: _bootprint(1),
    "luna_bootprint_tile_l": lambda: _bootprint(2),
    "luna_bootprint_tile_r": lambda: _bootprint(3),
    "luna_mare_column_c00": lambda: _mare_side(0, 0),
    "luna_mare_column_c10": lambda: _mare_side(1, 0),
    "luna_mare_column_c01": lambda: _mare_side(0, 1),
    "luna_mare_column_c11": lambda: _mare_side(1, 1),
    "luna_mare_column_end": luna_mare_column_end,
    "luna_bead_glass": luna_bead_glass,
    "mars_wind_strata_brick": mars_wind_strata_brick,
    "mars_spherule_stone": mars_spherule_stone,
    "mars_dust_drift_tile": mars_dust_drift_tile,
    "mars_dust_drift_tile_top": mars_dust_drift_tile_top,
    "cindara_ember_basalt": cindara_ember_basalt,
    "cindara_ember_basalt_glow": cindara_ember_basalt_glow,
    "cindara_slag_brick": cindara_slag_brick,
    "glacira_bubble_ice": glacira_bubble_ice,
    "glacira_bubble_ice_frame": glacira_bubble_ice_frame,
    "glacira_cryo_fin_plate": glacira_cryo_fin_plate,
    "glacira_cryo_fin_spine": glacira_cryo_fin_spine,
    "glacira_cryo_fin_cap": glacira_cryo_fin_cap,
    "glacira_snow_brick": glacira_snow_brick,
    "greenxertz_woven_chitin": greenxertz_woven_chitin,
    "greenxertz_spore_tile": greenxertz_spore_tile,
    "greenxertz_spore_tile_glow": greenxertz_spore_tile_glow,
    "greenxertz_hive_comb": greenxertz_hive_comb,
    "nyxara_faceted_crystal": nyxara_faceted_crystal,
    "nyxara_shard_mosaic": nyxara_shard_mosaic,
    "nyxara_geode_block": nyxara_geode_block,
}
TEXTURES.update(_variants(_tread, "station_tread_plate"))
TEXTURES.update(_variants(_rust_plate, "mars_rust_plate"))
TEXTURES.update(_variants(_ash_tile, "cindara_ash_tile"))
TEXTURES.update(_variants(_frost_plate, "glacira_frost_plate"))


def _cube(bid, **kw):
    return Block(bid, textures={"all": bid}, **kw)


_MARE_TEX = ["luna_mare_column_c%d%d" % (p, q) for p in (1, 0) for q in (1, 0)] + ["luna_mare_column_end"]
_BOOT_TEX = ["luna_bootprint_tile" + s for s in ("", "_b", "_l", "_r")]
_FIN_TEX = ["glacira_cryo_fin_plate", "glacira_cryo_fin_spine", "glacira_cryo_fin_cap"]

BLOCKS = [
    # Station
    _cube("station_plating", connects="mask16"),
    _tile("station_tread_plate"),
    _cube("station_thermal_blanket"),
    # Luna
    _cube("luna_regolith_brick"),
    Block("luna_bootprint_tile", shape="custom", textures={t: t for t in _BOOT_TEX},
          model=_bootprint_model, blockstate=_bootprint_state, item_model="luna_bootprint_tile_l"),
    Block("luna_mare_column", shape="custom", connects="strip", textures={t: t for t in _MARE_TEX},
          model=_mare_model, blockstate=lambda c: _pillar_state(c, "luna_mare_column"),
          item_model="luna_mare_column_c11"),
    _cube("luna_bead_glass"),
    # Mars
    _tile("mars_rust_plate"),
    _cube("mars_wind_strata_brick"),
    _cube("mars_spherule_stone"),
    Block("mars_dust_drift_tile", shape="custom",
          textures={"all": "mars_dust_drift_tile", "top": "mars_dust_drift_tile_top"}, model=_drift_model,
          blockstate=lambda c: facing_variants(c, "mars_dust_drift_tile")),
    # Cindara
    _cube("cindara_ember_basalt", emissive=True),
    _cube("cindara_slag_brick"),
    _tile("cindara_ash_tile"),
    # Glacira
    _cube("glacira_bubble_ice", connects="mask16", render_type="minecraft:translucent"),
    Block("glacira_cryo_fin", shape="custom", connects="strip", textures={t: t for t in _FIN_TEX},
          model=_fin_model, blockstate=lambda c: _pillar_state(c, "glacira_cryo_fin"),
          item_model="glacira_cryo_fin_c11"),
    _tile("glacira_frost_plate"),
    _cube("glacira_snow_brick"),
    # Greenxertz
    _cube("greenxertz_woven_chitin"),
    _cube("greenxertz_spore_tile", emissive=True),
    _cube("greenxertz_hive_comb"),
    # Nyxara
    _cube("nyxara_faceted_crystal"),
    _cube("nyxara_shard_mosaic"),
    _cube("nyxara_geode_block"),
]
