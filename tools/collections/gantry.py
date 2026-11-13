"""
Gantry collection -- industrial walkways and structure. Eight blocks, no emissives, no block
entities: `catwalk`, `catwalk_stairs`, `railing`, `gantry_ladder`, `floor_grate`,
`floor_grate_slab`, `strut`, `truss_beam`.

House look: near-black navy frames (`body`), slate-blue walking surfaces and rails (`station`), and
one painted (not glowing) Nero-alloy teal marker per block. Everything you can see through --
catwalk mesh, grates, stair treads, railing panels, the ladder and the truss lattice -- is a
cutout texture, never modelled bars.

Blockstate properties the Java blocks must expose (all booleans default false):
  catwalk            north / east / south / west   (same block on that side). The deck is the TOP
                     3px of the block (y 13..16) so it runs flush with floor tops, grates and the
                     top tread of catwalk_stairs.
  catwalk_stairs     facing (horizontal; the high end, as vanilla stairs)
  railing            north / east / south / west   (fence-style arms; post only when not a
                     straight run)
  gantry_ladder      facing (horizontal, as the vanilla ladder), pos_link (ladder above),
                     neg_link (ladder below)
  floor_grate        north / east / south / west / up / down   (kit mask16)
  floor_grate_slab   type (bottom / top / double), north / east / south / west
  strut, truss_beam  axis (x / y / z), pos_link, neg_link   (same block at the +axis / -axis end)

Connected looks are baked into face-texture variants (2px frame per unconnected edge); end caps on
the strut, truss and ladder are extra elements that never share a plane with the run (the run is
shortened to make room), so nothing here can z-fight.
"""
from functools import lru_cache

from ._kit import (Art, Block, FACE_EDGES, _AXES, _rot, bevel, bezel_layer, expand_mask16, new, put,
                   ramp, rect, wear)

COLLECTION = "gantry"

BODY = ramp("body")          # near-black navy structure
STEEL = ramp("station")      # slate-blue walking surfaces, rails, lattice
NERO = ramp("nero_alloy")    # the one accent hue: painted teal markers (not emissive)

HORIZ = ("north", "east", "south", "west")
CUTOUT = "minecraft:cutout"


# =========================================================================================
# Painters
# =========================================================================================
# --- catwalk: expanded-metal mesh over a cross joist --------------------------------------
def _catwalk_base(seed):
    img = new()
    for y in range(16):
        for x in range(16):
            a = (x + y) % 4 == 3      # "/" strands, in shadow
            b = (x - y) % 4 == 0      # "\" strands, catching the key light
            if a and b:
                put(img, x, y, STEEL[4])
            elif b:
                put(img, x, y, STEEL[3])
            elif a:
                put(img, x, y, STEEL[1])
    for x in range(16):               # joist under the mesh, across the walking direction
        put(img, x, 7, BODY[3])
        put(img, x, 8, BODY[1])
    for x in (3, 11):                 # mesh clips on the joist
        put(img, x, 7, STEEL[4])
        put(img, x, 8, STEEL[2])
    return wear(img, seed, STEEL, rate=0.05)


def _catwalk_frame():
    img = bezel_layer(BODY)
    for i in (5, 10):                 # teal lane ticks on the lip
        put(img, i, 1, NERO[2])
        put(img, i, 14, NERO[1])
        put(img, 1, i, NERO[2])
        put(img, 14, i, NERO[1])
    return img


@lru_cache(maxsize=None)
def _catwalk_set():
    return expand_mask16(Art.of(_catwalk_base("catwalk")), _catwalk_frame())


def catwalk_side():
    """Lip edge: rows 0..2 are the 3px deck side; the rest repeats it so the tile stays opaque."""
    img = new(16, 16, BODY[1])
    for y0 in range(0, 15, 3):
        for x in range(16):
            put(img, x, y0, BODY[3])
            put(img, x, y0 + 1, BODY[2])
            put(img, x, y0 + 2, BODY[0])
        put(img, 0, y0, BODY[4])
        for x in (2, 13):
            put(img, x, y0 + 1, STEEL[3])
        for x in (7, 8):
            put(img, x, y0 + 1, NERO[2])
    return wear(img, "catwalk_side", BODY, rate=0.04)


# --- floor grate: bearing bars with cross rods ---------------------------------------------
def floor_grate():
    """Square bar grating, symmetric under the 2px frame: bars on columns/rows 1..2 mod 4 (lit top
    + shadowed inner wall, which gives the grate its depth), 2x2 openings between."""
    img = new()
    for y in range(16):
        for x in range(16):
            cx, cy = x % 4, y % 4
            bx, by = cx in (1, 2), cy in (1, 2)
            if not (bx or by):
                continue
            if cx == 1 and cy == 1:
                c = STEEL[4]              # crossing, catching the key light
            elif cx == 2 and cy == 2:
                c = STEEL[0]
            elif (cx == 1 and not by) or (cy == 1 and not bx):
                c = STEEL[3]              # bar tops
            elif cx == 1 or cy == 1:
                c = STEEL[2]
            else:
                c = STEEL[1]              # inner walls
            put(img, x, y, c)
    return wear(img, "floor_grate", STEEL, rate=0.05)


def floor_grate_frame():
    img = bezel_layer(BODY)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        put(img, x, y, STEEL[3])      # corner bolts
    return img


def _slab_side(m):
    """Side of the half-height grate: rows 0..7 (and repeated in 8..15). m bits: 2 = frame on the
    left edge, 1 = frame on the right; top and bottom always carry it."""
    def paint():
        g = floor_grate()
        half = new(16, 8)
        half.paste(g.crop((0, 0, 16, 8)), (0, 0))
        fr = bezel_layer(BODY, 16, 8)
        for (x, y) in ((1, 1), (14, 1), (1, 6), (14, 6)):
            put(fr, x, y, STEEL[3])
        hp, fp = half.load(), fr.load()
        for y in range(8):
            for x in range(16):
                if y < 2 or y >= 6 or (m & 2 and x < 2) or (m & 1 and x >= 14):
                    hp[x, y] = fp[x, y]
        img = new()
        img.paste(half, (0, 0))
        img.paste(half, (0, 8))
        return img
    return paint


# --- catwalk stairs ------------------------------------------------------------------------
def catwalk_stairs_tread():
    """rows 0..3 tread top (nosing = row 3), rows 4..5 tread edge, rows 6..9 underside."""
    img = new(16, 16, BODY[1])
    for x in range(16):
        put(img, x, 0, STEEL[1])
        slot1, slot2 = x % 4 in (1, 2), x % 4 in (3, 0)
        put(img, x, 1, STEEL[3], 0 if slot1 else 255)
        put(img, x, 2, STEEL[2], 0 if slot2 else 255)
        put(img, x, 3, NERO[2] if x % 8 in (2, 3, 4, 5) else STEEL[4])     # nosing, teal dashes
        put(img, x, 4, BODY[3])
        put(img, x, 5, BODY[0])
        put(img, x, 6, STEEL[0])
        put(img, x, 7, STEEL[1], 0 if slot2 else 255)
        put(img, x, 8, STEEL[1], 0 if slot1 else 255)
        put(img, x, 9, STEEL[0])
    put(img, 1, 4, BODY[4])
    return wear(img, "catwalk_stairs_tread", STEEL, rate=0.04, region=(0, 0, 16, 3))


def catwalk_stairs_side():
    """Stringer elevation as seen from the east (low step on the left): four stepped plates."""
    img = new(16, 16, BODY[1])
    for k in range(4):
        x0, y0, y1 = 4 * k, 12 - 4 * k, min(16, 20 - 4 * k)
        bevel(img, x0, y0, x0 + 4, y1, BODY, fill=2)
        put(img, x0 + 2, y0 + 2, STEEL[3])                 # tread bolt
        if y1 - y0 >= 8:
            rect(img, x0 + 1, y0 + 4, x0 + 3, y0 + 6, BODY[0])   # lightening slot
            put(img, x0 + 1, y0 + 6, BODY[3])
            put(img, x0 + 2, y0 + 6, BODY[3])
    return wear(img, "catwalk_stairs_side", BODY, rate=0.05)


# --- railing ---------------------------------------------------------------------------------
def railing():
    """Panel elevation, mirrored about the middle so it reads as a tile; an arm samples columns
    0..7 (column 7 = the baluster at the block edge). Rows 1..2 handrail side, 3..15 infill."""
    img = new()
    for x in range(16):
        m = x if x < 8 else 15 - x
        put(img, x, 1, STEEL[4] if m == 0 else STEEL[3])   # handrail side
        put(img, x, 2, STEEL[1])
        put(img, x, 6, STEEL[3])                           # tension cables
        put(img, x, 10, STEEL[2])
        put(img, x, 13, BODY[3])                           # toe board
        put(img, x, 14, BODY[0] if m % 4 in (1, 2) else BODY[2])
        put(img, x, 15, BODY[0])
        if m == 7:                                         # baluster
            for y in range(3, 13):
                put(img, x, y, BODY[3] if x == 7 else BODY[1])
            put(img, x, 6, STEEL[4])
            put(img, x, 10, STEEL[3])
        if m == 5:
            put(img, x, 14, NERO[2])                       # teal marker on the toe board
    return img


def railing_post():
    """cols 0..2 post side (15 rows) | 4..6 x 0..2 post cap | 8..12 x 0..4 foot plate, row 6 its
    edge | 4..5 x 4..11 handrail top | 6..7 x 4..11 handrail underside."""
    img = new(16, 16, BODY[1])
    for y in range(15):
        put(img, 0, y, BODY[3])
        put(img, 1, y, BODY[2])
        put(img, 2, y, BODY[0])
    for y, r in ((0, (STEEL[4], STEEL[3], STEEL[1])), (1, (STEEL[3], STEEL[2], STEEL[0])),
                 (4, (NERO[3], NERO[2], NERO[1])), (13, (STEEL[3], STEEL[2], STEEL[0]))):
        for x in range(3):
            put(img, x, y, r[x])
    for x in range(3):
        put(img, x, 2, BODY[0])
        put(img, x, 14, BODY[0])
    bevel(img, 4, 0, 7, 3, STEEL, fill=2)
    bevel(img, 8, 0, 13, 5, BODY, fill=2)
    for (x, y) in ((8, 0), (12, 0), (8, 4), (12, 4)):
        put(img, x, y, STEEL[3])
    for x in range(8, 13):
        put(img, x, 6, BODY[3])
    for y in range(4, 12):
        put(img, 4, y, STEEL[4])
        put(img, 5, y, STEEL[2])
        put(img, 6, y, STEEL[1])
        put(img, 7, y, STEEL[0])
    return wear(img, "railing_post", BODY, rate=0.03)


# --- ladder ----------------------------------------------------------------------------------
def _ladder(p, n, seed="gantry_ladder"):
    """Front elevation: rails on columns 2..3 / 12..13, rungs on rows 1, 5, 9, 13. p / n bake the
    collar on the top / bottom rail ends."""
    def paint():
        img = new()
        for y in range(16):
            for x0 in (2, 12):
                put(img, x0, y, STEEL[3])
                put(img, x0 + 1, y, STEEL[1])
        for y in (1, 5, 9, 13):
            for x in range(4, 12):
                put(img, x, y, STEEL[3] if x % 2 else STEEL[2])   # knurled rung
            put(img, 4, y, STEEL[4])
            put(img, 11, y, STEEL[1])
        for x0 in (2, 12):                                 # wall-bracket collars
            put(img, x0, 7, BODY[3])
            put(img, x0 + 1, 7, BODY[2])
            put(img, x0, 8, BODY[2])
            put(img, x0 + 1, 8, BODY[0])
        wear(img, seed, STEEL, rate=0.05)
        for x0 in (2, 12):
            if p:
                put(img, x0, 0, STEEL[4]); put(img, x0 + 1, 0, STEEL[2])
                put(img, x0, 1, STEEL[3]); put(img, x0 + 1, 1, STEEL[1])
                put(img, x0, 2, NERO[3]); put(img, x0 + 1, 2, NERO[1])
            if n:
                put(img, x0, 13, NERO[3]); put(img, x0 + 1, 13, NERO[1])
                put(img, x0, 14, STEEL[3]); put(img, x0 + 1, 14, STEEL[1])
                put(img, x0, 15, STEEL[1]); put(img, x0 + 1, 15, STEEL[0])
        return img
    return paint


# --- strut -------------------------------------------------------------------------------------
def _strut(seed):
    """Columns 6..9 are the 4px post face (repeated across the tile): lit flange, shadowed web,
    web, occluded flange, with a clamp band every 8 rows and one teal dash."""
    def paint():
        img = new(16, 16, BODY[1])
        cols = (BODY[3], BODY[1], BODY[2], BODY[0])
        for y in range(16):
            for x in range(16):
                put(img, x, y, cols[(x - 6) % 4])
        for y in (3, 11):
            for x in range(16):
                put(img, x, y, (STEEL[4], STEEL[3], STEEL[2], STEEL[1])[(x - 6) % 4])
                put(img, x, y + 1, BODY[0])
        for x in range(16):
            if (x - 6) % 4 == 2:
                put(img, x, 7, NERO[3])
                put(img, x, 8, NERO[2])
        return wear(img, seed, BODY, rate=0.05)
    return paint


def strut_flange():
    """End flange: 8x8 plate at 4..11 (the auto-UV of its up/down face); rows 0..1 its edge."""
    img = new(16, 16, BODY[1])
    for x in range(16):
        put(img, x, 0, BODY[3])
        put(img, x, 1, BODY[0])
    for x in (5, 10):
        put(img, x, 0, STEEL[3])
    bevel(img, 4, 4, 12, 12, BODY, fill=2)
    bevel(img, 6, 6, 10, 10, BODY, fill=1)
    for (x, y) in ((5, 5), (10, 5), (5, 10), (10, 10)):
        put(img, x, y, STEEL[4])
        put(img, x + 1 if x == 5 else x - 1, y + 1 if y == 5 else y - 1, BODY[0])
    return img


# --- truss beam ----------------------------------------------------------------------------------
def _truss(seed):
    """Side elevation: chords on columns 2..3 / 12..13, a Warren zig-zag web on 4..11 (cutout),
    gussets where the web meets the chords. Tiles along the beam."""
    def paint():
        img = new()
        for y in range(16):
            put(img, 2, y, BODY[3]); put(img, 3, y, BODY[2])
            put(img, 12, y, BODY[2]); put(img, 13, y, BODY[0])
        for y in range(16):                                # 2px diagonal, lit on its upper edge
            xc = 4 + y if y < 8 else 4 + (15 - y)
            for dx, col in ((-1, STEEL[1]), (0, STEEL[3]), (1, STEEL[2])):
                if 4 <= xc + dx <= 11:
                    put(img, xc + dx, y, STEEL[4] if (dx == 0 and y % 4 == 2) else col)
        for (x, y) in ((4, 15), (4, 0), (5, 15), (5, 0), (4, 14), (4, 1)):      # left gusset
            put(img, x, y, BODY[2])
        for (x, y) in ((11, 7), (11, 8), (10, 7), (10, 8), (11, 6), (11, 9)):   # right gusset
            put(img, x, y, BODY[2])
        put(img, 4, 0, BODY[3]); put(img, 11, 7, BODY[3])
        put(img, 4, 15, STEEL[4]); put(img, 11, 8, STEEL[4])                    # gusset bolts
        for y in (0, 8):                                   # chord splice bands
            put(img, 2, y, STEEL[3]); put(img, 3, y, STEEL[2])
            put(img, 12, y, STEEL[2]); put(img, 13, y, STEEL[1])
        return wear(img, seed, BODY, rate=0.05)
    return paint


def truss_beam_end():
    """End frame: 12x12 at 2..13 (auto-UV of the plate's up/down face), X-braced, open between.
    Row 0 is the plate's 1px edge."""
    img = new()
    for x in range(2, 14):
        put(img, x, 0, BODY[3])
    bevel(img, 2, 2, 14, 14, BODY)
    for i in range(3, 13):
        put(img, i, 3, BODY[2]); put(img, i, 12, BODY[1])
        put(img, 3, i, BODY[2]); put(img, 12, i, BODY[1])
    for i in range(4, 12):
        put(img, i, i, STEEL[3])
        put(img, 15 - i, i, STEEL[1])
    rect(img, 7, 7, 9, 9, NERO[2])
    put(img, 7, 7, NERO[3])
    return img


def _mask_painter(key):
    return lambda: _catwalk_set()[key]


TEXTURES = {
    "catwalk_f0_b": lambda: _catwalk_base("catwalk_b"),
    "catwalk_side": catwalk_side,
    "floor_grate": floor_grate,
    "floor_grate_frame": floor_grate_frame,
    "catwalk_stairs_tread": catwalk_stairs_tread,
    "catwalk_stairs_side": catwalk_stairs_side,
    "railing": railing,
    "railing_post": railing_post,
    "gantry_ladder_c00_b": _ladder(0, 0, "gantry_ladder_b"),
    "strut": _strut("strut"),
    "strut_b": _strut("strut_b"),
    "strut_flange": strut_flange,
    "truss_beam": _truss("truss_beam"),
    "truss_beam_b": _truss("truss_beam_b"),
    "truss_beam_end": truss_beam_end,
}
for _m in range(16):
    TEXTURES["catwalk_f%d" % _m] = _mask_painter("_f%d" % _m)
for _m in range(4):
    TEXTURES["floor_grate_slab_side_%d" % _m] = _slab_side(_m)
for _p in (0, 1):
    for _n in (0, 1):
        TEXTURES["gantry_ladder_c%d%d" % (_p, _n)] = _ladder(_p, _n)


# =========================================================================================
# Models + blockstates
# =========================================================================================
def _tf(b):
    return "true" if b else "false"


def _conns():
    """Every north/east/south/west combination: (model suffix int, {dir: connected})."""
    for i in range(16):
        yield i, {d: bool(i & (8 >> k)) for k, d in enumerate(HORIZ)}


def _key(conn, **extra):
    props = {d: _tf(v) for d, v in conn.items()}
    props.update(extra)
    return ",".join("%s=%s" % (k, props[k]) for k in sorted(props))


def _mask(face, conn, fixed=()):
    """Texture-edge mask for `face`: bit set = that edge is NOT connected (carries the frame).
    Edges pointing at a direction in `fixed` (or not in conn) always carry it."""
    m = 0
    for bit, d in zip((8, 4, 2, 1), FACE_EDGES[face]):
        if d in fixed or not conn.get(d, False):
            m |= bit
    return m


def _mdl(textures, elements, cutout=True):
    m = {"textures": textures, "elements": elements}
    if cutout:
        m["render_type"] = CUTOUT
    return m


# --- catwalk -----------------------------------------------------------------------------------
def _catwalk_model(c, conn, variant=""):
    tx = {"particle": c.tex("catwalk_f15"), "side": c.tex("catwalk_side")}
    faces = {}
    for face in ("up", "down"):
        m = _mask(face, conn)
        tx[face] = c.tex("catwalk_f%d%s" % (m, variant if m == 0 else ""))
        faces[face] = {"texture": "#" + face, "uv": [0, 0, 16, 16]}
    for d in HORIZ:
        if not conn[d]:
            faces[d] = {"texture": "#side", "uv": [0, 0, 16, 3]}
    cull = {"up": "up"}
    cull.update({d: d for d in HORIZ})
    return _mdl(tx, [c.el([0, 13, 0], [16, 16, 16], faces, cull=cull)])


def catwalk_models(c):
    out = {"catwalk_c0": _catwalk_model(c, dict.fromkeys(HORIZ, False))}
    for i, conn in _conns():
        out["catwalk_c%d" % i] = _catwalk_model(c, conn)
    out["catwalk_c15_b"] = _catwalk_model(c, dict.fromkeys(HORIZ, True), "_b")
    return out


def catwalk_state(c):
    v = {}
    for i, conn in _conns():
        v[_key(conn)] = {"model": c.model("catwalk_c%d" % i)}
    v[_key(dict.fromkeys(HORIZ, True))] = [{"model": c.model("catwalk_c15"), "weight": 3},
                                           {"model": c.model("catwalk_c15_b")}]
    return {"variants": v}


# --- floor grate slab ----------------------------------------------------------------------------
_SLAB = {"bottom": (0, 8), "top": (8, 16), "double": (0, 16)}


def _slab_model(c, kind, conn):
    y0, y1 = _SLAB[kind]
    tx = {"particle": c.tex("floor_grate_f15")}
    faces, cull = {}, {}
    for face in ("up", "down"):
        tx[face] = c.tex("floor_grate_f%d" % _mask(face, conn))
        faces[face] = {"texture": "#" + face, "uv": [0, 0, 16, 16]}
    if y1 == 16:
        cull["up"] = "up"
    if y0 == 0:
        cull["down"] = "down"
    for d in HORIZ:
        if conn[d]:
            continue
        lr = _mask(d, conn, fixed=("up", "down")) & 3
        if kind == "double":
            tx[d] = c.tex("floor_grate_f%d" % (12 | lr))
            faces[d] = {"texture": "#" + d, "uv": [0, 0, 16, 16]}
        else:
            tx[d] = c.tex("floor_grate_slab_side_%d" % lr)
            faces[d] = {"texture": "#" + d, "uv": [0, 0, 16, 8]}
        cull[d] = d
    return _mdl(tx, [c.el([0, y0, 0], [16, y1, 16], faces, cull=cull)])


def slab_models(c):
    out = {"floor_grate_slab_bottom_c0": _slab_model(c, "bottom", dict.fromkeys(HORIZ, False))}
    for kind in _SLAB:
        for i, conn in _conns():
            out["floor_grate_slab_%s_c%d" % (kind, i)] = _slab_model(c, kind, conn)
    return out


def slab_state(c):
    return {"variants": {_key(conn, type=kind): {"model": c.model("floor_grate_slab_%s_c%d" % (kind, i))}
                         for kind in _SLAB for i, conn in _conns()}}


# --- catwalk stairs (drawn facing north: low step at the south) ----------------------------------
def stairs_models(c):
    tx = {"particle": c.tex("catwalk_stairs_side"), "tread": c.tex("catwalk_stairs_tread"),
          "side": c.tex("catwalk_stairs_side")}
    els = []
    for k in range(4):
        z0, z1, top = 12 - 4 * k, 16 - 4 * k, 4 * k + 4
        els.append(c.el([1, top - 2, z0], [15, top, z1], {
            "up": {"texture": "#tread", "uv": [1, 0, 15, 4]},
            "down": {"texture": "#tread", "uv": [1, 6, 15, 10]},
            "south": {"texture": "#tread", "uv": [1, 4, 15, 6]},
            "north": {"texture": "#tread", "uv": [1, 4, 15, 6]}}, cull=False))
    for x0 in (0, 15):
        for k in range(4):
            z0, z1, top = 12 - 4 * k, 16 - 4 * k, 4 * k + 4
            bot = max(0, top - 8)
            u0, v0, v1 = 4 * k, 16 - top, 16 - bot
            col = [u0, v0, u0 + 1, v1]
            faces = {"east": {"texture": "#side", "uv": [u0, v0, u0 + 4, v1]},
                     "west": {"texture": "#side", "uv": [u0 + 4, v0, u0, v1]},
                     "up": {"texture": "#side", "uv": [u0, v0, u0 + 1, v0 + 4]},
                     "down": {"texture": "#side", "uv": [u0 + 3, v1 - 4, u0 + 4, v1]},
                     "north": {"texture": "#side", "uv": col},
                     "south": {"texture": "#side", "uv": col}}
            cull = {"west": "west"} if x0 == 0 else {"east": "east"}
            if bot == 0:
                cull["down"] = "down"
            els.append(c.el([x0, bot, z0], [x0 + 1, top, z1], faces, cull=cull))
    return {"catwalk_stairs": _mdl(tx, els)}


def stairs_state(c):
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    return {"variants": {"facing=%s" % f: dict({"model": c.model("catwalk_stairs")}, **({"y": r} if r else {}))
                         for f, r in rot.items()}}


# --- railing ---------------------------------------------------------------------------------------
def _rail_arm(c, south=False):
    """One arm from the block centre to the north (or south) edge: handrail bar + cutout panel."""
    z0, z1 = (8, 16) if south else (0, 8)
    edge_right = not south          # east face: texture right = north
    pe = [0, 3, 8, 16] if edge_right else [8, 3, 0, 16]
    pw = [8, 3, 0, 16] if edge_right else [0, 3, 8, 16]
    he = [0, 1, 8, 3] if edge_right else [8, 1, 0, 3]
    hw = [8, 1, 0, 3] if edge_right else [0, 1, 8, 3]
    return [
        c.el([7, 13, z0], [9, 15, z1], {
            "up": {"texture": "#post", "uv": [4, 4, 6, 12]},
            "down": {"texture": "#post", "uv": [6, 4, 8, 12]},
            "east": {"texture": "#rail", "uv": he},
            "west": {"texture": "#rail", "uv": hw}}, cull=False),
        c.el([7.5, 0, z0], [8.5, 13, z1], {
            "east": {"texture": "#rail", "uv": pe},
            "west": {"texture": "#rail", "uv": pw}}, cull=False),
    ]


def _rail_post(c):
    side = {"texture": "#post", "uv": [0, 0, 3, 15]}
    foot = {"texture": "#post", "uv": [8, 6, 13, 7]}
    return [
        c.el([5.5, 0, 5.5], [10.5, 1, 10.5], {
            "up": {"texture": "#post", "uv": [8, 0, 13, 5]},
            "down": {"texture": "#post", "uv": [8, 0, 13, 5], "cullface": "down"},
            "north": foot, "east": foot, "south": foot, "west": foot}, cull=False),
        c.el([6.5, 1, 6.5], [9.5, 16, 9.5], {
            "up": {"texture": "#post", "uv": [4, 0, 7, 3]},
            "north": side, "east": side, "south": side, "west": side}, cull=False),
    ]


def railing_models(c):
    tx = {"particle": c.tex("railing_post"), "rail": c.tex("railing"), "post": c.tex("railing_post")}
    return {"railing_inventory": _mdl(tx, _rail_post(c) + _rail_arm(c) + _rail_arm(c, south=True)),
            "railing_post": _mdl(tx, _rail_post(c)),
            "railing_side": _mdl(tx, _rail_arm(c))}


def railing_state(c):
    straight = ({"north", "south"}, {"east", "west"})
    post_when = []
    for _i, conn in _conns():
        if {d for d in HORIZ if conn[d]} not in straight:
            post_when.append({d: _tf(conn[d]) for d in HORIZ})
    parts = [{"when": {"OR": post_when}, "apply": {"model": c.model("railing_post")}}]
    for d, r in zip(HORIZ, (0, 90, 180, 270)):
        ap = {"model": c.model("railing_side")}
        if r:
            ap["y"] = r
        parts.append({"when": {d: "true"}, "apply": ap})
    return {"multipart": parts}


# --- ladder (drawn facing north: mounted on the block to the south, 1px off the wall) --------------
def _ladder_model(c, p, n, variant=""):
    tx = {"particle": c.tex("gantry_ladder_c11"),
          "l": c.tex("gantry_ladder_c%d%d%s" % (p, n, variant))}
    els = []
    for x0 in (2, 12):
        rail = {"texture": "#l", "uv": [x0, 0, x0 + 2, 16]}
        faces = {"north": rail, "south": rail, "east": rail, "west": rail}
        if p:
            faces["up"] = {"texture": "#l", "uv": [x0, 0, x0 + 2, 1], "cullface": "up"}
        if n:
            faces["down"] = {"texture": "#l", "uv": [x0, 15, x0 + 2, 16], "cullface": "down"}
        els.append(c.el([x0, 0, 13], [x0 + 2, 16, 15], faces, cull=False))
    for y in (2, 6, 10, 14):
        rung = {"texture": "#l", "uv": [4, 15 - y, 12, 16 - y]}
        els.append(c.el([4, y, 13.5], [12, y + 1, 14.5],
                        {"north": rung, "south": rung, "up": rung, "down": rung}, cull=False))
    ties = [(7, 9)] + ([(14, 16)] if p else []) + ([(0, 2)] if n else [])
    for (y0, y1) in ties:           # wall brackets; at an end the rail returns into the wall
        for x0 in (2, 12):
            uv = [x0, 16 - y1, x0 + 2, 16 - y0]
            row = [x0, 16 - y1, x0 + 2, 16 - y1 + 1]
            faces = {"east": {"texture": "#l", "uv": uv}, "west": {"texture": "#l", "uv": uv},
                     "up": {"texture": "#l", "uv": row}, "down": {"texture": "#l", "uv": row}}
            cull = {"up": "up"} if y1 == 16 else {"down": "down"} if y0 == 0 else False
            els.append(c.el([x0, y0, 15], [x0 + 2, y1, 16], faces, cull=cull))
    return _mdl(tx, els)


def ladder_models(c):
    out = {"gantry_ladder_c11": _ladder_model(c, 1, 1)}
    for p in (0, 1):
        for n in (0, 1):
            out["gantry_ladder_c%d%d" % (p, n)] = _ladder_model(c, p, n)
    out["gantry_ladder_c00_b"] = _ladder_model(c, 0, 0, "_b")
    return out


def ladder_state(c):
    v = {}
    for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for pos in (False, True):
            for neg in (False, True):
                names = ["gantry_ladder_c%d%d" % (int(not pos), int(not neg))]
                if pos and neg:
                    names.append("gantry_ladder_c00_b")
                alts = [dict({"model": c.model(nm)}, **({"y": r} if r else {})) for nm in names]
                v["facing=%s,neg_link=%s,pos_link=%s" % (f, _tf(neg), _tf(pos))] = alts[0] if len(alts) == 1 else alts
    return {"variants": v}


# --- strut + truss (drawn along +y; `axis` rotates them) ---------------------------------------------
def _axis_rot(axis):
    for rx in (0, 90, 180, 270):
        for ry in (0, 90, 180, 270):
            if _rot((0, 1, 0), rx, ry) == _AXES[axis]:
                return rx, ry
    raise ValueError(axis)


def _axis_state(c, bid):
    v = {}
    for axis in ("x", "y", "z"):
        rx, ry = _axis_rot(axis)
        for pos in (False, True):
            for neg in (False, True):
                names = ["%s_c%d%d" % (bid, int(not pos), int(not neg))]
                if pos and neg:
                    names.append("%s_c00_b" % bid)
                alts = []
                for nm in names:
                    a = {"model": c.model(nm)}
                    if rx:
                        a["x"] = rx
                    if ry:
                        a["y"] = ry
                    alts.append(a)
                v["axis=%s,neg_link=%s,pos_link=%s" % (axis, _tf(neg), _tf(pos))] = alts[0] if len(alts) == 1 else alts
    return {"variants": v}


def _strut_model(c, p, n, variant=""):
    tx = {"particle": c.tex("strut"), "post": c.tex("strut" + variant), "flange": c.tex("strut_flange")}
    a, b = (2 if n else 0), (14 if p else 16)
    side = {"texture": "#post", "uv": [6, 16 - b, 10, 16 - a]}
    els = [c.el([6, a, 6], [10, b, 10], {d: side for d in HORIZ}, cull=False)]
    edge = {"texture": "#flange", "uv": [4, 0, 12, 2]}
    for on, y0, cullf in ((n, 0, "down"), (p, 14, "up")):
        if on:
            faces = {"up": {"texture": "#flange", "uv": [4, 4, 12, 12]},
                     "down": {"texture": "#flange", "uv": [4, 4, 12, 12]}}
            faces.update({d: edge for d in HORIZ})
            els.append(c.el([4, y0, 4], [12, y0 + 2, 12], faces, cull={cullf: cullf}))
    return _mdl(tx, els, cutout=False)


def strut_models(c):
    out = {"strut_c11": _strut_model(c, 1, 1)}
    for p in (0, 1):
        for n in (0, 1):
            out["strut_c%d%d" % (p, n)] = _strut_model(c, p, n)
    out["strut_c00_b"] = _strut_model(c, 0, 0, "_b")
    return out


def _truss_model(c, p, n, variant=""):
    tx = {"particle": c.tex("truss_beam"), "t": c.tex("truss_beam" + variant), "end": c.tex("truss_beam_end")}
    a, b = (1 if n else 0), (15 if p else 16)
    els = []
    for (x0, z0) in ((2, 2), (12, 2), (2, 12), (12, 12)):      # corner chords
        chord = {"texture": "#t", "uv": [2, 16 - b, 4, 16 - a]}
        els.append(c.el([x0, a, z0], [x0 + 2, b, z0 + 2], {d: chord for d in HORIZ}, cull=False))
    web = {"texture": "#t", "uv": [4, 0, 12, 16]}
    bew = {"texture": "#t", "uv": [12, 0, 4, 16]}
    for x in (3, 13):                                          # lattice planes, seen from both sides
        els.append(c.el([x, 0, 4], [x, 16, 12], {"west": web, "east": bew}, cull=False))
    for z in (3, 13):
        els.append(c.el([4, 0, z], [12, 16, z], {"north": bew, "south": web}, cull=False))
    edge = {"texture": "#end", "uv": [2, 0, 14, 1]}
    for on, y0, cullf in ((n, 0, "down"), (p, 15, "up")):
        if on:
            faces = {"up": {"texture": "#end", "uv": [2, 2, 14, 14]},
                     "down": {"texture": "#end", "uv": [2, 2, 14, 14]}}
            faces.update({d: edge for d in HORIZ})
            els.append(c.el([2, y0, 2], [14, y0 + 1, 14], faces, cull={cullf: cullf}))
    return _mdl(tx, els)


def truss_models(c):
    out = {"truss_beam_c11": _truss_model(c, 1, 1)}
    for p in (0, 1):
        for n in (0, 1):
            out["truss_beam_c%d%d" % (p, n)] = _truss_model(c, p, n)
    out["truss_beam_c00_b"] = _truss_model(c, 0, 0, "_b")
    return out


# =========================================================================================
def _roles(*tids):
    return {t: t for t in tids}


BLOCKS = [
    Block("catwalk", shape="custom", render_type=CUTOUT, model=catwalk_models, blockstate=catwalk_state,
          textures=_roles("catwalk_side", "catwalk_f0_b", *["catwalk_f%d" % m for m in range(16)])),
    Block("catwalk_stairs", shape="custom", render_type=CUTOUT, model=stairs_models, blockstate=stairs_state,
          textures=_roles("catwalk_stairs_tread", "catwalk_stairs_side")),
    Block("railing", shape="custom", render_type=CUTOUT, model=railing_models, blockstate=railing_state,
          textures=_roles("railing", "railing_post")),
    Block("gantry_ladder", shape="custom", render_type=CUTOUT, model=ladder_models, blockstate=ladder_state,
          textures=_roles("gantry_ladder_c00_b", *["gantry_ladder_c%d%d" % (p, n) for p in (0, 1) for n in (0, 1)])),
    Block("floor_grate", shape="cube", connects="mask16", render_type=CUTOUT,
          textures={"all": "floor_grate"}),
    Block("floor_grate_slab", shape="custom", render_type=CUTOUT, model=slab_models, blockstate=slab_state,
          textures=_roles(*["floor_grate_slab_side_%d" % m for m in range(4)])),
    Block("strut", shape="custom", model=strut_models, blockstate=lambda c: _axis_state(c, "strut"),
          textures=_roles("strut", "strut_b", "strut_flange")),
    Block("truss_beam", shape="custom", render_type=CUTOUT, model=truss_models,
          blockstate=lambda c: _axis_state(c, "truss_beam"),
          textures=_roles("truss_beam", "truss_beam_b", "truss_beam_end")),
]
