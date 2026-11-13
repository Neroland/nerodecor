"""
Ancients collection -- weathered alien-precursor architecture with dormant glyph tech.

Seven blocks (ids match the registry list):

  glyph_block            cube, 32px, connects mask16, emissive. Properties: north..down (bool, as
                         every mask16 block) + `glyph` (0..7). The stone face and its rim are the
                         96 mask16 parts; the glyph is ONE extra full-cube part per `glyph` value
                         (a carved cutout layer + the emissive strokes), identical in size to the
                         face under it. Joined blocks lose the rim between them, so a wall of them
                         reads as one cartouche.
  ancient_brick          cube, 4 weighted variants. Interlocking trapezoid coursing.
  cracked_ancient_brick  cube, 3 weighted variants.
  ancient_pillar         cube pillar. Properties `axis`, `pos_link`, `neg_link` (DecorPillarBlock):
                         capital baked on the + end, plinth on the - end, when not linked.
  dormant_core           cube, 32px, chroma. `powered`: false = dark lens + dim glyphs (tinted,
                         world-lit), true = emissive breathing glow (tinted). The stone is NOT
                         tinted; only the glyph layers take the hub colour.
  glyph_strip            4px-wide, 1px-thick band; 32px; chroma. Properties `face`, `axis`,
                         `pos_link`, `neg_link` exactly as the kit's strip shape. Stone untinted.
  arch_lintel            corbelled half-arch. `facing` (horizontal) + `shape` (left|mid|right).
                         `left` = the corbel steps down to the support on the viewer's LEFT when
                         looking at the face the block is `facing` (for facing=north that is +x).

One invented alphabet (GLYPHS, strokes on a 3x3 node lattice) is used everywhere: large (6px node
pitch, 2px strokes) on glyph_block, small (2px pitch, 1px strokes) on glyph_strip, dormant_core
and the mason's mark on ancient_brick.
"""
import math

from ._kit import (Block, animate, bevel, expand_mask16, expand_strip, lerp, new, put, ramp, ramp5,
                   rect, strip_rotation, wear, det, FACES, FACE_EDGES)

COLLECTION = "ancients"

# --- ramps (index 0 occlusion .. 4 glint; shadows lean blue-violet, highlights lean warm) --------
STONE = [(27, 28, 44), (50, 56, 72), (82, 93, 100), (126, 136, 128), (176, 178, 150)]
BRONZE = [(38, 26, 36), (78, 52, 46), (124, 90, 58), (172, 136, 80), (218, 194, 128)]
LENS = ramp("body")                 # dormant lens glass
PLASMA = ramp("plasma")             # glyph_block's own accent (not chroma)
CHROMA = ramp5((222, 232, 242))     # near-neutral: the hub tint supplies the colour

# --- the alphabet -------------------------------------------------------------------------------
# name -> (polylines over lattice nodes (col,row) in 0..2, dots)
GLYPHS = {
    "gate":   ([[(0, 2), (0, 0), (2, 0), (2, 2)]], [(1, 2)]),
    "fork":   ([[(0, 0), (0, 1), (2, 1), (2, 0)], [(1, 0), (1, 2)]], []),
    "peak":   ([[(0, 2), (1, 0), (2, 2)]], [(1, 2)]),
    "eye":    ([[(1, 0), (2, 1), (1, 2), (0, 1), (1, 0)]], [(1, 1)]),
    "step":   ([[(0, 0), (1, 0), (1, 1), (2, 1), (2, 2)]], [(0, 2)]),
    "spiral": ([[(1, 1), (2, 1), (2, 0), (0, 0), (0, 2), (2, 2)]], []),
    "ascend": ([[(0, 1), (1, 0), (2, 1)], [(1, 1), (1, 2)]], [(0, 2), (2, 2)]),
    "twin":   ([[(1, 0), (0, 0), (0, 2)], [(1, 2), (2, 2), (2, 0)]], [(1, 1)]),
    "cross":  ([[(1, 0), (1, 2)], [(0, 1), (2, 1)]], [(0, 0), (2, 2)]),
    "cup":    ([[(0, 0), (0, 2), (2, 2), (2, 0)], [(1, 0), (1, 1)]], []),
}
BLOCK_GLYPHS = ["gate", "fork", "peak", "eye", "step", "spiral", "ascend", "twin"]   # glyph=0..7
STRIP_GLYPHS = ["step", "cup", "twin", "peak"]
CORE_GLYPHS = ["eye", "spiral", "gate", "cross"]


def glyph_px(name, pitch, thick, ox=0, oy=0):
    """Pixel set of a glyph: lattice pitch `pitch`, square brush `thick`, offset (ox, oy)."""
    paths, dots = GLYPHS[name]
    out = set()

    def stamp(x, y):
        for dy in range(thick):
            for dx in range(thick):
                out.add((ox + x + dx, oy + y + dy))
    for path in paths:
        for a, b in zip(path, path[1:]):
            x0, y0, x1, y1 = a[0] * pitch, a[1] * pitch, b[0] * pitch, b[1] * pitch
            n = max(abs(x1 - x0), abs(y1 - y0))
            for i in range(n + 1):
                stamp(int(x0 + (x1 - x0) * i / n + 0.5), int(y0 + (y1 - y0) * i / n + 0.5))
    for d in dots:
        stamp(d[0] * pitch, d[1] * pitch)
    return out


def lips(pts):
    """Pixels on the lower/right rim of a carved shape that catch the top-left key light (never a
    pixel squeezed between two strokes)."""
    out = set()
    for (x, y) in pts:
        for q in ((x + 1, y), (x, y + 1)):
            if q not in pts and (q[0] + 1, q[1]) not in pts and (q[0], q[1] + 1) not in pts:
                out.add(q)
    return out


def carve(img, pts, r=STONE, floor=0, lip=3):
    for (x, y) in lips(pts):
        put(img, x, y, r[lip])
    for (x, y) in pts:
        put(img, x, y, r[floor])


def _cache(fn):
    memo = {}

    def get(*a):
        if a not in memo:
            memo[a] = fn(*a)
        return memo[a]
    return get


# =================================================================================================
# Brick
# =================================================================================================
def _joints(r):
    s = (r + 1) // 3          # 0,0,1,1,1,2,2,2 down the course: a 2px lean
    return 2 + s, 11 - s


def _coursing(img):
    """Two 8px courses of interlocking trapezoids (alternately tapering and flaring), the lower
    course shifted half a block so every joint lands mid-stone."""
    for y in range(16):
        c, r = divmod(y, 8)
        ja, jb = _joints(r)
        for x in range(16):
            xs = (x - 8 * c) % 16
            if r == 0 or xs in (ja, jb):
                col = STONE[0]
            elif r == 1:
                col = STONE[4] if xs - 1 in (ja, jb) else STONE[3]
            elif xs - 1 in (ja, jb):
                col = STONE[3]
            elif r == 7 or xs + 1 in (ja, jb):
                col = STONE[1]
            else:
                col = STONE[2]
            put(img, x, y, col)
    return img


def _brick(variant):
    img = _coursing(new(16, 16, STONE[2]))
    if variant == 1:      # a chipped arris on the wide stone
        for (x, y, i) in ((6, 1, 2), (7, 1, 2), (6, 2, 1), (5, 1, 2)):
            put(img, x, y, STONE[i])
    elif variant == 2:    # mason's mark from the alphabet, cut shallow
        for (x, y) in glyph_px("peak", 2, 1, 13, 10):
            put(img, x, y, STONE[1])
    elif variant == 3:    # a bedding seam through the lower course
        for x in range(3, 8):
            put(img, x, 12, STONE[1])
            put(img, x, 13, STONE[3])
    return wear(img, "ancient_brick_%d" % variant, STONE, rate=0.05)


def _crack(img, seed, x, y, n, lean=1):
    """Seeded fissure: runs of 2-3px that keep their lean, dark, with the lower-right edge lit."""
    pts = []
    for i in range(n):
        pts.append((x % 16, y % 16))
        d = det(seed, "crack", i // 3)
        step = lean if d < 0.55 else (0 if d < 0.85 else -lean)
        if i % 3 == 2:
            x += step
        y += 1
    s = set(pts)
    for (cx, cy) in pts:
        q = ((cx + 1) % 16, cy)
        if q not in s:
            put(img, q[0], q[1], STONE[3])
    for (cx, cy) in pts:
        put(img, cx, cy, STONE[0])


def _cracked(variant):
    img = _coursing(new(16, 16, STONE[2]))
    seed = "cracked_ancient_brick_%d" % variant
    if variant == 0:
        _crack(img, seed, 6, 2, 11, 1)
        spall = ((12, 5), (13, 5), (13, 6), (12, 6))
    elif variant == 1:
        _crack(img, seed, 14, 9, 6, -1)
        _crack(img, seed + "b", 7, 1, 6, -1)
        spall = ((1, 10), (2, 10), (1, 11))
    else:
        _crack(img, seed, 9, 10, 12, -1)
        spall = ((5, 3), (6, 3), (6, 4), (5, 4), (4, 3))
    for i, (x, y) in enumerate(spall):      # a spalled pocket: dark hollow with a lit lower lip
        put(img, x, y, STONE[0] if i < 2 else STONE[1])
    return wear(img, seed, STONE, rate=0.06)


# =================================================================================================
# Pillar
# =================================================================================================
def _band_row(img, y, r):
    for x in range(16):
        put(img, x, y, r[2])
    put(img, 0, y, r[4])
    put(img, 1, y, r[3])
    put(img, 14, y, r[1])
    put(img, 15, y, r[0])


def _pillar_side(p, n):
    """Shaft: lit left arris, two flutes, a bronze inlay down the middle, occluded right arris.
    p = capital on the top rows (+axis end), n = plinth on the bottom rows (-axis end)."""
    cols = [3, 2, 2, 0, 3, 2, 2, None, None, 2, 2, 0, 3, 2, 1, 0]
    img = new(16, 16, STONE[2])
    for y in range(16):
        for x in range(16):
            if cols[x] is not None:
                put(img, x, y, STONE[cols[x]])
        put(img, 7, y, BRONZE[2])
        put(img, 8, y, BRONZE[1])
        if y % 8 in (3, 4):                       # inlay node
            put(img, 7, y, BRONZE[4] if y % 8 == 3 else BRONZE[3])
            put(img, 8, y, BRONZE[3] if y % 8 == 3 else BRONZE[2])
        if y % 8 == 7:                            # inlay break
            put(img, 7, y, STONE[0])
            put(img, 8, y, STONE[0])
    wear(img, "ancient_pillar_side", STONE, rate=0.05)
    if p:
        for x in range(16):
            put(img, x, 0, STONE[3])
            put(img, x, 1, STONE[2])
            put(img, x, 3, STONE[1])
            put(img, x, 4, STONE[0])
            if img.getpixel((x, 5))[:3] in (STONE[2], STONE[3]):   # cast shadow on the shaft
                put(img, x, 5, STONE[1])
        put(img, 0, 0, STONE[4])
        put(img, 0, 1, STONE[3])
        put(img, 15, 0, STONE[1])
        put(img, 15, 1, STONE[0])
        put(img, 15, 3, STONE[0])
        _band_row(img, 2, BRONZE)
    else:
        for x in (0, 1, 2, 4, 5, 6, 9, 10, 12, 13):   # drum seam (linked above)
            put(img, x, 0, STONE[3])
    if n:
        for x in range(16):
            put(img, x, 11, STONE[3])
            put(img, x, 12, STONE[2])
            put(img, x, 14, STONE[1])
            put(img, x, 15, STONE[0])
        put(img, 0, 11, STONE[4])
        put(img, 0, 12, STONE[3])
        put(img, 15, 11, STONE[1])
        put(img, 15, 12, STONE[0])
        put(img, 15, 14, STONE[0])
        _band_row(img, 13, BRONZE)
    else:
        for x in (1, 2, 5, 6, 9, 10, 13, 14):
            put(img, x, 15, STONE[1])
    return img


def ancient_pillar_end():
    img = new(16, 16, STONE[2])
    bevel(img, 0, 0, 16, 16, STONE, fill=2)
    for i in range(3, 13):                          # sunk socket: shadow top/left, lit lip
        put(img, i, 3, STONE[0])
        put(img, 3, i, STONE[0])
    for i in range(4, 13):
        put(img, i, 12, STONE[3])
        put(img, 12, i, STONE[3])
    rect(img, 4, 4, 12, 12, STONE[1])
    for i in range(5, 11):                          # bronze ring
        put(img, i, 5, BRONZE[3])
        put(img, 5, i, BRONZE[3])
        put(img, i, 10, BRONZE[1])
        put(img, 10, i, BRONZE[1])
    put(img, 5, 5, BRONZE[4])
    put(img, 10, 5, BRONZE[2])
    put(img, 5, 10, BRONZE[2])
    rect(img, 7, 7, 9, 9, BRONZE[2])
    put(img, 7, 7, BRONZE[4])
    put(img, 8, 8, BRONZE[1])
    return wear(img, "ancient_pillar_end", STONE, rate=0.04)


# =================================================================================================
# Lintel
# =================================================================================================
def arch_lintel():
    """Side texture. Course lines sit on rows 6 / 9 / 12, exactly where the corbel steps break
    (y = 10 / 7 / 4), so every step gets its own lit top edge and occluded underside."""
    img = new(16, 16, STONE[2])
    courses = ((0, 6, ()), (6, 9, (5, 13)), (9, 12, (1, 9)), (12, 16, (6, 14)))
    for y0, y1, joints in courses:
        for x in range(16):
            put(img, x, y0, STONE[3])
            put(img, x, y1 - 1, STONE[1])
        for j in joints:
            for y in range(y0, y1):
                lean = (y - y0) // 2 if j % 2 else -((y - y0) // 2)
                put(img, j + lean, y, STONE[0])
                if y > y0:
                    put(img, j + lean + 1, y, STONE[3] if y < y1 - 1 else STONE[2])
    for x in range(16):                             # the beam: bronze fillet + deep underside
        put(img, x, 2, BRONZE[3])
        put(img, x, 3, BRONZE[1])
        put(img, x, 5, STONE[0])
    put(img, 0, 0, STONE[4])
    for x in (3, 11):                               # fillet studs
        put(img, x, 2, BRONZE[4])
        put(img, x + 1, 2, BRONZE[2])
        put(img, x, 3, BRONZE[2])
        put(img, x + 1, 3, BRONZE[0])
    return wear(img, "arch_lintel", STONE, rate=0.05)


def arch_lintel_top():
    img = new(16, 16, STONE[2])
    bevel(img, 0, 0, 16, 16, STONE, fill=2)
    for y in range(1, 15):                          # one leaning megalith joint
        x = 9 - (y + 1) // 5
        put(img, x, y, STONE[0])
        put(img, x + 1, y, STONE[3])
    return wear(img, "arch_lintel_top", STONE, rate=0.05)


# =================================================================================================
# 32px rim shared by glyph_block (mask16 frame layer) and dormant_core
# =================================================================================================
def _rim32(img):
    """4px rim: lit stone arris, stone, bronze fillet, then the shadow it throws into the sunk
    field (top/left) or the lit inner lip (bottom/right); bronze bosses on the corners."""
    for i in range(4, 28):
        for k, c in enumerate((STONE[3], STONE[2], BRONZE[2], STONE[0])):
            put(img, i, k, c)            # top band, outside -> inside
            put(img, k, i, c)            # left band
        for k, c in enumerate((STONE[3], BRONZE[1], STONE[1], STONE[0])):
            put(img, i, 28 + k, c)       # bottom band, inside -> outside
            put(img, 28 + k, i, c)       # right band
    for cx in (0, 28):
        for cy in (0, 28):
            bevel(img, cx, cy, cx + 4, cy + 4, BRONZE, fill=2)
    return img


def glyph_block_frame():
    return _rim32(new(32))


def glyph_block():
    """The field: a sunk dark panel that tiles, so joined blocks form one cartouche."""
    img = new(32, 32, STONE[1])
    for (x, y) in ((6, 6), (25, 6), (6, 25), (25, 25)):     # setting-out marks
        put(img, x, y, STONE[0])
    for (x, y) in ((7, 7), (26, 7), (7, 26), (26, 26)):
        put(img, x, y, STONE[2])
    return wear(img, "glyph_block", STONE, rate=0.05)


_PIPS = ((11, 25), (15, 25), (19, 25))


def _block_glyph(i):
    """(stroke pixels, lit pips, unlit pips). Three pips under the glyph count it in binary."""
    pts = glyph_px(BLOCK_GLYPHS[i], 6, 2, 9, 8)
    lit, unlit = set(), set()
    for bit, (x, y) in enumerate(_PIPS):
        (lit if ((i + 1) >> (2 - bit)) & 1 else unlit).update(
            {(x, y), (x + 1, y), (x, y + 1), (x + 1, y + 1)})
    return pts, lit, unlit


def _glyph_carve(i):
    """Cutout, world-lit: the cut the glow sits in (floor + lit lower-right lips) and dead pips."""
    pts, lit, unlit = _block_glyph(i)
    img = new(32)
    carve(img, pts | lit | unlit)
    return img


def _glyph_glow(i):
    pts, lit, _ = _block_glyph(i)
    tid = "glyph_block_g%d_glow" % i

    def frame(t):
        k = 0.5 - 0.5 * math.cos(2 * math.pi * t)
        img = new(32)
        for (x, y) in pts:
            put(img, x, y, lerp(PLASMA[1], PLASMA[3], k))
        for (x, y) in lit:
            put(img, x, y, lerp(PLASMA[1], PLASMA[2], k))
        return img
    return animate(tid, "breathing", frame)


@_cache
def _glyph_faces():
    return expand_mask16(_art(glyph_block()), glyph_block_frame(), None, width=4)


def _art(x):
    from ._kit import Art
    return Art.of(x)


# =================================================================================================
# Dormant core
# =================================================================================================
_CORE_AT = ((5, 5), (22, 5), (5, 22), (22, 22))


def _core_marks():
    glyphs = set()
    for name, (x, y) in zip(CORE_GLYPHS, _CORE_AT):
        glyphs |= glyph_px(name, 2, 1, x, y)
    traces = set()
    for i in range(4, 8):
        for j in (15, 16):
            traces.update({(j, i), (j, 31 - i), (i, j), (31 - i, j)})
    return glyphs, traces


def _lens(x, y):
    return math.hypot(x - 15.5, y - 15.5)


def dormant_core():
    img = new(32, 32, STONE[1])
    wear(img, "dormant_core", STONE, rate=0.05)
    _rim32(img)
    glyphs, traces = _core_marks()
    carve(img, glyphs | traces)
    for y in range(7, 25):
        for x in range(7, 25):
            d = _lens(x, y)
            s = (x - 15.5) + (y - 15.5)
            if d < 3.3:
                put(img, x, y, LENS[2] if s < -1.5 else LENS[1] if s < 2.5 else LENS[0])
            elif d < 4.5:
                put(img, x, y, STONE[0])
            elif d < 6.7:
                put(img, x, y, BRONZE[3] if s < -3.5 else BRONZE[1] if s > 3.5 else BRONZE[2])
            elif d < 7.7:
                put(img, x, y, STONE[0] if s < 0 else STONE[3])
    put(img, 14, 14, LENS[4])
    put(img, 15, 14, LENS[3])
    put(img, 14, 15, LENS[3])
    put(img, 12, 11, BRONZE[4])
    put(img, 11, 12, BRONZE[4])
    return img


def dormant_core_dim():
    """powered=false: the marks barely hold a charge (tinted by the hub colour, world-lit)."""
    glyphs, traces = _core_marks()
    img = new(32)
    for (x, y) in glyphs:
        put(img, x, y, CHROMA[0])
    for (x, y) in traces:
        put(img, x, y, lerp(STONE[0], CHROMA[0], 0.5))
    return img


def dormant_core_glow():
    glyphs, traces = _core_marks()

    def frame(t):
        k = 0.5 - 0.5 * math.cos(2 * math.pi * t)
        kg = 0.5 - 0.5 * math.cos(2 * math.pi * (t - 0.25))    # marks trail the lens
        img = new(32)
        for (x, y) in glyphs:
            put(img, x, y, lerp(CHROMA[1], CHROMA[4], kg))
        for (x, y) in traces:
            inner = 7 in (x, y, 31 - x, 31 - y) or 6 in (x, y, 31 - x, 31 - y)
            put(img, x, y, lerp(CHROMA[1], CHROMA[3], k if inner else kg))
        for y in range(11, 21):
            for x in range(11, 21):
                d = _lens(x, y)
                if d < 3.3:
                    rim = CHROMA[0] if d >= 2.4 else CHROMA[1] if d >= 1.3 else CHROMA[2]
                    put(img, x, y, lerp(rim, CHROMA[4] if d < 2.4 else CHROMA[2], k))
        return img
    return animate("dormant_core_glow", "breathing", frame)


# =================================================================================================
# Glyph strip (band = texture rows 12..19 of 32, run along x; side faces sample rows 10..11, 20..21)
# =================================================================================================
_STRIP_X = (1, 9, 17, 25)
_CAP = 7


def _strip_marks():
    pts = set()
    for name, x in zip(STRIP_GLYPHS, _STRIP_X):
        pts |= glyph_px(name, 2, 1, x, 14)
    pips = {(7, 16), (15, 16), (23, 16), (31, 16)}
    return pts, pips


def glyph_strip():
    img = new(32, 32, STONE[2])
    bevel(img, 0, 0, 32, 10, STONE, fill=2)
    bevel(img, 0, 22, 32, 32, STONE, fill=2)
    wear(img, "glyph_strip", STONE, rate=0.04)
    for x in range(32):
        put(img, x, 10, STONE[3])
        put(img, x, 11, STONE[2])
        put(img, x, 12, STONE[3])     # upper rail, lit
        put(img, x, 13, STONE[0])     # its shadow in the channel
        for y in range(14, 19):
            put(img, x, y, STONE[1])
        put(img, x, 19, STONE[2])     # lower rail
        put(img, x, 20, STONE[1])
        put(img, x, 21, STONE[0])
    pts, pips = _strip_marks()
    for (x, y) in pts | pips:
        put(img, x, y, STONE[0])
    return img


def glyph_strip_cap():
    """Bronze end clasp, 7 texels: expand_strip copies the outer columns over an unlinked end."""
    img = new(32, 32, BRONZE[2])
    for y in range(32):
        for x0 in (0, 32 - _CAP):
            put(img, x0, y, BRONZE[3])
            put(img, x0 + 1, y, BRONZE[2])
            put(img, x0 + _CAP - 2, y, BRONZE[1])
            put(img, x0 + _CAP - 1, y, BRONZE[0])
    for x0 in (0, 32 - _CAP):
        for x in range(x0, x0 + _CAP):
            put(img, x, 10, BRONZE[4])
            put(img, x, 11, BRONZE[3])
            put(img, x, 12, BRONZE[3] if x < x0 + _CAP - 1 else BRONZE[1])
            put(img, x, 19, BRONZE[1] if x > x0 else BRONZE[2])
            put(img, x, 20, BRONZE[1])
            put(img, x, 21, BRONZE[0])
        put(img, x0, 12, BRONZE[4])
        put(img, x0 + _CAP - 1, 19, BRONZE[0])
        rect(img, x0 + 2, 15, x0 + 5, 18, STONE[0])     # socketed stud
        put(img, x0 + 3, 16, BRONZE[4])
        put(img, x0 + 4, 17, BRONZE[1])
        put(img, x0 + 3, 17, BRONZE[3])
        put(img, x0 + 4, 16, BRONZE[3])
    return img


def glyph_strip_glow():
    pts, pips = _strip_marks()

    def frame(t):
        head = t * 32.0
        img = new(32)
        for (x, y) in pts | pips:
            k = max(0.0, 1.0 - ((head - x) % 32.0) / 14.0)
            put(img, x, y, lerp(CHROMA[1], CHROMA[4], k))
        return img
    return animate("glyph_strip_glow", "flow", frame)


@_cache
def _strip_faces():
    return expand_strip(_art(glyph_strip()), glyph_strip_cap(), glyph_strip_glow(), width=_CAP)


# =================================================================================================
# TEXTURES
# =================================================================================================
TEXTURES = {
    "glyph_block": glyph_block,
    "dormant_core": dormant_core,
    "dormant_core_dim": dormant_core_dim,
    "dormant_core_glow": dormant_core_glow,
    "ancient_pillar_end": ancient_pillar_end,
    "arch_lintel": arch_lintel,
    "arch_lintel_top": arch_lintel_top,
    "glyph_strip": glyph_strip,
}
for _m in range(16):
    TEXTURES["glyph_block_f%d" % _m] = (lambda m=_m: _glyph_faces()["_f%d" % m])
for _i in range(8):
    TEXTURES["glyph_block_g%d" % _i] = (lambda i=_i: _glyph_carve(i))
    TEXTURES["glyph_block_g%d_glow" % _i] = (lambda i=_i: _glyph_glow(i))
for _v in range(4):
    TEXTURES["ancient_brick_%d" % _v] = (lambda v=_v: _brick(v))
for _v in range(3):
    TEXTURES["cracked_ancient_brick_%d" % _v] = (lambda v=_v: _cracked(v))
for _p in (0, 1):
    for _n in (0, 1):
        TEXTURES["ancient_pillar_side_c%d%d" % (_p, _n)] = (lambda p=_p, n=_n: _pillar_side(p, n))
        for _s in ("", "_t"):
            TEXTURES["glyph_strip_c%d%d%s" % (_p, _n, _s)] = (
                lambda k="_c%d%d%s" % (_p, _n, _s): _strip_faces()[k])
            TEXTURES["glyph_strip_glow_c%d%d%s" % (_p, _n, _s)] = (
                lambda k="_glow_c%d%d%s" % (_p, _n, _s): _strip_faces()[k])


def _ids(*names):
    return {n: n for n in names}


CUTOUT = "minecraft:cutout"
FULL = ([0, 0, 0], [16, 16, 16])
UV = [0, 0, 16, 16]


def _mdl(textures, elements, cutout=False):
    m = {"textures": textures, "elements": elements}
    if cutout:
        m["render_type"] = CUTOUT
    return m


# =================================================================================================
# Models + blockstates
# =================================================================================================
def _variant_cube(prefix, count):
    def model(c):
        return {"%s_%d" % (prefix, v): _mdl(
            {"particle": c.tex("%s_%d" % (prefix, v)), "all": c.tex("%s_%d" % (prefix, v))},
            [c.el(*FULL, FACES, "#all", uv=UV)]) for v in range(count)}

    def blockstate(c):
        return {"variants": {"": [{"model": c.model("%s_%d" % (prefix, v))} for v in range(count)]}}
    return model, blockstate


def _glyph_block_model(c):
    models = {}
    inv = {"particle": c.tex("glyph_block"), "all": c.tex("glyph_block_f15"),
           "carve": c.tex("glyph_block_g0"), "glow": c.tex("glyph_block_g0_glow")}
    models["glyph_block_inventory"] = _mdl(inv, [
        c.el(*FULL, FACES, "#all", uv=UV), c.el(*FULL, FACES, "#carve", uv=UV),
        c.el(*FULL, FACES, "#glow", uv=UV, emit=15)], True)
    for face in FACES:
        for m in range(16):
            models["glyph_block_%s_f%d" % (face, m)] = _mdl(
                {"particle": c.tex("glyph_block"), "all": c.tex("glyph_block_f%d" % m)},
                [c.el(*FULL, [face], "#all", uv=UV)], True)
    for i in range(8):
        models["glyph_block_g%d" % i] = _mdl(
            {"particle": c.tex("glyph_block"), "carve": c.tex("glyph_block_g%d" % i),
             "glow": c.tex("glyph_block_g%d_glow" % i)},
            [c.el(*FULL, FACES, "#carve", uv=UV), c.el(*FULL, FACES, "#glow", uv=UV, emit=15)], True)
    return models


def _glyph_block_state(c):
    parts = []
    for face in FACES:
        edges = FACE_EDGES[face]
        for m in range(16):
            bits = (m & 8, m & 4, m & 2, m & 1)
            parts.append({"when": {d: ("false" if bit else "true") for d, bit in zip(edges, bits)},
                          "apply": {"model": c.model("glyph_block_%s_f%d" % (face, m))}})
    for i in range(8):
        parts.append({"when": {"glyph": str(i)}, "apply": {"model": c.model("glyph_block_g%d" % i)}})
    return {"multipart": parts}


def _pillar_model(c):
    out = {}
    for p in (0, 1):
        for n in (0, 1):
            tx = {"particle": c.tex("ancient_pillar_side_c00"),
                  "side": c.tex("ancient_pillar_side_c%d%d" % (p, n)),
                  "end": c.tex("ancient_pillar_end")}
            faces = {f: "#side" for f in ("north", "east", "south", "west")}
            faces.update({"up": "#end", "down": "#end"})
            out["ancient_pillar_c%d%d" % (p, n)] = _mdl(tx, [c.el(*FULL, faces, uv=UV)])
    return out


def _pillar_state(c):
    """The model stands on y. x=90 lays its top toward -z (so the ends swap for axis=z); adding
    y=90 turns that to +x."""
    v = {}
    for axis, rot, flip in (("y", {}, False), ("z", {"x": 90}, True), ("x", {"x": 90, "y": 90}, False)):
        for pos in (False, True):
            for neg in (False, True):
                cp, cn = int(not pos), int(not neg)
                if flip:
                    cp, cn = cn, cp
                e = {"model": c.model("ancient_pillar_c%d%d" % (cp, cn))}
                e.update(rot)
                v["axis=%s,neg_link=%s,pos_link=%s" % (axis, str(neg).lower(), str(pos).lower())] = e
    return {"variants": v}


def _core_model(c):
    tx = {"particle": c.tex("dormant_core"), "all": c.tex("dormant_core")}
    base = c.el(*FULL, FACES, "#all", uv=UV, tint=False)
    return {
        "dormant_core": _mdl(dict(tx, dim=c.tex("dormant_core_dim")),
                             [base, c.el(*FULL, FACES, "#dim", uv=UV, tint=True)], True),
        "dormant_core_on": _mdl(dict(tx, glow=c.tex("dormant_core_glow")),
                                [base, c.el(*FULL, FACES, "#glow", uv=UV, tint=True, emit=15)], True),
    }


def _core_state(c):
    return {"variants": {"powered=false": {"model": c.model("dormant_core")},
                         "powered=true": {"model": c.model("dormant_core_on")}}}


def _strip_model(c):
    """The kit's strip, widened to a 4px band (1px proud). Two canonicals on the DOWN face: along
    +x (z 6..10) and along +z (x 6..10, transposed `_t` textures)."""
    out = {}
    for canon in ("", "_z"):
        for p in (0, 1):
            for n in (0, 1):
                v = "glyph_strip_c%d%d" % (p, n)
                g = "glyph_strip_glow_c%d%d" % (p, n)
                tx = {"particle": c.tex("glyph_strip"), "bar": c.tex(v)}
                if canon == "":
                    frm, to = [0, 0, 6], [16, 1, 10]
                    faces = {"up": {"texture": "#bar", "uv": [0, 6, 16, 10]},
                             "north": {"texture": "#bar", "uv": [16, 5, 0, 6]},
                             "south": {"texture": "#bar", "uv": [0, 10, 16, 11]},
                             "west": {"texture": "#bar", "uv": [0, 6, 0.5, 10]},
                             "east": {"texture": "#bar", "uv": [15.5, 6, 16, 10]},
                             "down": {"texture": "#bar", "uv": [0, 6, 16, 10], "cullface": "down"}}
                    gface = {"up": {"texture": "#glow", "uv": [0, 6, 16, 10]}}
                    tx["glow"] = c.tex(g)
                else:
                    tx["bar_t"] = c.tex(v + "_t")
                    frm, to = [6, 0, 0], [10, 1, 16]
                    faces = {"up": {"texture": "#bar_t", "uv": [6, 0, 10, 16]},
                             "west": {"texture": "#bar", "uv": [0, 5, 16, 6]},
                             "east": {"texture": "#bar", "uv": [16, 10, 0, 11]},
                             "north": {"texture": "#bar_t", "uv": [6, 0, 10, 0.5]},
                             "south": {"texture": "#bar_t", "uv": [6, 15.5, 10, 16]},
                             "down": {"texture": "#bar_t", "uv": [6, 0, 10, 16], "cullface": "down"}}
                    gface = {"up": {"texture": "#glow", "uv": [6, 0, 10, 16]}}
                    tx["glow"] = c.tex(g + "_t")
                out["glyph_strip_c%d%d%s" % (p, n, canon)] = _mdl(tx, [
                    c.el(frm, to, faces, cull=False, tint=False),
                    c.el(frm, to, gface, cull=False, tint=True, emit=15)], True)
    return out


def _strip_state(c):
    variants = {}
    for face in ("down", "up", "north", "south", "west", "east"):
        for axis in ("x", "y", "z"):
            if face in {"x": ("west", "east"), "y": ("down", "up"), "z": ("north", "south")}[axis]:
                continue
            canon, rx, ry, flip = strip_rotation(face, axis)
            for pos in (False, True):
                for neg in (False, True):
                    cp, cn = int(not pos), int(not neg)
                    if flip:
                        cp, cn = cn, cp
                    v = {"model": c.model("glyph_strip_c%d%d%s" % (cp, cn, canon))}
                    if rx:
                        v["x"] = rx
                    if ry:
                        v["y"] = ry
                    variants["axis=%s,face=%s,neg_link=%s,pos_link=%s"
                             % (axis, face, str(neg).lower(), str(pos).lower())] = v
    return {"variants": variants}


def _lintel_elements(c, shape):
    """Drawn facing north; the arch runs along x. Steps break at y = 10 / 7 / 4 (texture rows 6 / 9
    / 12). Faces buried under the element above are left out."""
    side = ("north", "south", "east", "west")

    def box(x0, y0, z0, x1, y1, z1, up=True, down=True):
        if shape == "right":
            x0, x1 = 16 - x1, 16 - x0
        faces = {f: "#side" for f in side}
        if up:
            faces["up"] = "#top"
        if down:
            faces["down"] = "#top"
        cull = {}
        if y1 == 16:
            cull["up"] = "up"
        if y0 == 0:
            cull["down"] = "down"
        if z0 == 0:
            cull["north"] = "north"
        if z1 == 16:
            cull["south"] = "south"
        if x0 == 0:
            cull["west"] = "west"
        if x1 == 16:
            cull["east"] = "east"
        return c.el([x0, y0, z0], [x1, y1, z1], faces, cull=cull)
    els = [box(0, 10, 0, 16, 16, 16)]
    if shape == "mid":
        els.append(box(0, 8, 2, 16, 10, 14, up=False))
    else:
        els += [box(0, 8, 2, 4, 10, 14, up=False),
                box(4, 7, 0, 16, 10, 16, up=False),
                box(8, 4, 0, 16, 7, 16, up=False),
                box(12, 0, 0, 16, 4, 16, up=False)]
    return els


def _lintel_model(c):
    tx = {"particle": c.tex("arch_lintel"), "side": c.tex("arch_lintel"), "top": c.tex("arch_lintel_top")}
    return {"arch_lintel_%s" % s: _mdl(tx, _lintel_elements(c, s)) for s in ("left", "mid", "right")}


def _lintel_state(c):
    v = {}
    for f, ry in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for s in ("left", "mid", "right"):
            e = {"model": c.model("arch_lintel_%s" % s)}
            if ry:
                e["y"] = ry
            v["facing=%s,shape=%s" % (f, s)] = e
    return {"variants": v}


_brick_model, _brick_state = _variant_cube("ancient_brick", 4)
_cracked_model, _cracked_state = _variant_cube("cracked_ancient_brick", 3)

BLOCKS = [
    Block("glyph_block", shape="custom", connects="mask16", emissive=True,
          textures=_ids("glyph_block", *["glyph_block_f%d" % m for m in range(16)],
                        *["glyph_block_g%d" % i for i in range(8)],
                        *["glyph_block_g%d_glow" % i for i in range(8)]),
          model=_glyph_block_model, blockstate=_glyph_block_state, item_model="glyph_block_inventory",
          render_type=CUTOUT, name="Glyph Block"),
    Block("ancient_brick", shape="custom",
          textures=_ids(*["ancient_brick_%d" % v for v in range(4)]),
          model=_brick_model, blockstate=_brick_state, item_model="ancient_brick_0",
          name="Ancient Brick"),
    Block("cracked_ancient_brick", shape="custom",
          textures=_ids(*["cracked_ancient_brick_%d" % v for v in range(3)]),
          model=_cracked_model, blockstate=_cracked_state, item_model="cracked_ancient_brick_0",
          name="Cracked Ancient Brick"),
    Block("ancient_pillar", shape="custom", connects="strip",
          textures=_ids("ancient_pillar_end", *["ancient_pillar_side_c%d%d" % (p, n)
                                                for p in (0, 1) for n in (0, 1)]),
          model=_pillar_model, blockstate=_pillar_state, item_model="ancient_pillar_c11",
          name="Ancient Pillar"),
    Block("dormant_core", shape="custom", chroma=True, emissive=True,
          textures=_ids("dormant_core", "dormant_core_dim", "dormant_core_glow"),
          model=_core_model, blockstate=_core_state, item_model="dormant_core",
          render_type=CUTOUT, name="Dormant Core"),
    Block("glyph_strip", shape="custom", connects="strip", chroma=True, emissive=True,
          textures=_ids("glyph_strip", *["glyph_strip%s_c%d%d%s" % (g, p, n, s)
                                         for g in ("", "_glow") for p in (0, 1) for n in (0, 1)
                                         for s in ("", "_t")]),
          model=_strip_model, blockstate=_strip_state, item_model="glyph_strip_c11",
          render_type=CUTOUT, name="Glyph Strip"),
    Block("arch_lintel", shape="custom", connects="strip",
          textures=_ids("arch_lintel", "arch_lintel_top"),
          model=_lintel_model, blockstate=_lintel_state, item_model="arch_lintel_left",
          name="Arch Lintel"),
]
