"""
Lab & Habitat collection (8 blocks) -- lab furniture and crew quarters.

Every block is `custom`: the kit's built-in shapes are cubes and a 2px bar, so models, blockstates
and connected variants are built here (see "kit gaps" at the bottom of this docstring).

Blockstate contract (the Java block must expose exactly these properties):

  lab_bench       axis=x|z, pos_link, neg_link   horizontal run; legs only on unlinked ends
  fume_hood       facing=n|e|s|w, open           open = sash raised
  specimen_tank   pos_link (same tank above), neg_link (below)   vertical run; chroma fluid
  sample_fridge   facing, open                   twin doors, each swings 90 deg (6px out of the block)
  lab_stool       (none)                         sittable -- see SEAT_Y
  crew_bunk       facing (foot -> head, as vanilla beds), part=head|foot
  locker          facing, open, pos_link (above), neg_link (below)   twin doors (7px out when open)
  planter         axis=x|z, pos_link, neg_link, plant=0..3   multipart: trough + plant
                  plant 0 fern, 1 violet bloom, 2 ember succulent, 3 frost reed

pos_link / neg_link mean "the same block continues at the +axis / -axis end", as in
DecorPillarBlock. Model suffix `_c<p><n>`: p=1 draws the end piece at the +axis end (pos_link
false), n=1 at the -axis end. Models are drawn facing NORTH; `facing` is the side the front faces.

Render layers: fume_hood, specimen_tank, sample_fridge and planter are cutout (their models carry
render_type minecraft:cutout; Fabric needs the same layer set in code). The rest are solid.

Emissive: fume_hood (amber: status lamp, chamber light, flask), sample_fridge (cyan: readout,
vials, cabinet light), specimen_tank (near-neutral, tintindex 0: bubbles, meniscus, base ring --
the hub colour does the rest). Glow is always an identical-box duplicate element (emit 15, no
shade). No block here is a light source (light=0), so every glow texture stays under 12%.

Kit gaps worked around locally (candidates for _kit):
  * custom models get no parent, so items would have no display transforms; models here set
    "parent": "minecraft:block/block".
  * Ctx.el(cull=True) puts a cullface on every face, wrong for inset furniture; `_M.box` below only
    culls faces that lie on the block boundary.
  * no helper for cap/mid/cap on custom shapes, for open/facing variant tables, or for mirrored UVs.
"""
import math

from ._kit import (Block, half_loot, accent, animate, bevel, det, lerp, new, put, ramp, ramp5, rect,  # noqa: F401
                   wear)

COLLECTION = "lab_habitat"

# Seat height for the nerodecor:seat entity on lab_stool: the cushion top is at y = 11px.
# Mount the seat entity at block y + SEAT_Y (x/z centred) and subtract the rider's own offset.
SEAT_Y = 11 / 16.0

# --- ramps (index 0 occlusion .. 4 glint) ------------------------------------------------------
BODY = ramp("body")              # house near-black navy
STEEL = ramp("starsteel")        # trim, chrome
ALLOY = ramp("nero_alloy")       # teal fabric / accents (painted, not emissive)
PLASMA = ramp("plasma")          # cyan emissive (sample_fridge)
STATION = ramp("station")        # mid blue-grey panels (locker doors, ducts)
GLAC = ramp("glacira")           # cold glass / water
GREEN = ramp("greenxertz")
NYX = ramp("nyxara")
MARS = ramp("mars")
CIND = ramp("cindara")           # amber emissive (fume_hood)
CER = ramp5((204, 216, 230))     # lab ceramic white
LINER = ramp5((184, 210, 226))   # cold cabinet liner
SHEET = ramp5((198, 208, 226))   # bed linen
PAD = ramp5((52, 76, 104))       # padded bulkhead
SOIL = ramp5((78, 54, 62))       # regolith compost
FLUID = ramp5((214, 226, 240))   # near-neutral tint target (chroma)
AMBER = (CIND[3], CIND[4], lerp(CIND[4], (255, 244, 226), 0.55))


class _C:
    """Tiny chainable canvas wrapper over the kit primitives."""

    def __init__(self, fill=None):
        self.i = new(16, 16, fill)

    def r(self, x0, y0, x1, y1, c):
        rect(self.i, x0, y0, x1, y1, c)
        return self

    def h(self, x0, x1, y, c):
        rect(self.i, x0, y, x1, y + 1, c)
        return self

    def v(self, x, y0, y1, c):
        rect(self.i, x, y0, x + 1, y1, c)
        return self

    def p(self, x, y, c):
        put(self.i, x, y, c)
        return self

    def ps(self, pts, c):
        for x, y in pts:
            put(self.i, x, y, c)
        return self

    def b(self, x0, y0, x1, y1, r, fill=2):
        bevel(self.i, x0, y0, x1, y1, r, fill)
        return self

    def w(self, seed, r, rate=0.05):
        wear(self.i, seed, r, rate=rate)
        return self.i


# =================================================================================================
# Model builder
# =================================================================================================
def _duv(face, f, t):
    """Vanilla auto-UV for a face of the box f..t (texture as the viewer of that face sees it)."""
    return {"up": [f[0], f[2], t[0], t[2]], "down": [f[0], 16 - t[2], t[0], 16 - f[2]],
            "north": [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]],
            "south": [f[0], 16 - t[1], t[0], 16 - f[1]],
            "west": [f[2], 16 - t[1], t[2], 16 - f[1]],
            "east": [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]]}[face]


_EDGE = {"down": (1, 0, 0), "up": (1, 1, 16), "north": (2, 0, 0), "south": (2, 1, 16),
         "west": (0, 0, 0), "east": (0, 1, 16)}


class _M:
    """Collects elements + the textures they use. Face spec: "tid" (auto-UV by position, so a
    texture painted as an elevation/plan lines up on its own), "tid~" (same, mirrored in u: lets
    the east face reuse a west elevation) or ("tid", [u0, v0, u1, v1])."""

    def __init__(self, ctx, particle, rt=None):
        self.c, self.rt, self.tx, self.els = ctx, rt, {"particle": ctx.tex(particle)}, []

    def box(self, f, t, faces, tint=False, emit=0, rot=None):
        fs = {}
        for name, spec in faces.items():
            uv = None
            if isinstance(spec, tuple):
                spec, uv = spec
            mirror = spec.endswith("~")
            tid = spec.rstrip("~")
            if uv is None:
                uv = _duv(name, f, t)
                if mirror:
                    uv = [uv[2], uv[1], uv[0], uv[3]]
            self.tx[tid] = self.c.tex(tid)
            face = {"texture": "#" + tid, "uv": list(uv)}
            a, end, lim = _EDGE[name]
            if rot is None and (t if end else f)[a] == lim:
                face["cullface"] = name
            if tint:
                face["tintindex"] = 0
            fs[name] = face
        e = {"from": list(f), "to": list(t)}
        if rot is not None:
            e["rotation"] = {"origin": [8, 8, 8], "axis": "y", "angle": rot}
        if emit:
            e["shade"] = False
            e["light_emission"] = emit
        e["faces"] = fs
        self.els.append(e)
        return self

    def glow(self, f, t, faces, tint=False, rot=None):
        return self.box(f, t, faces, tint=tint, emit=15, rot=rot)

    def json(self):
        m = {"parent": "minecraft:block/block", "textures": self.tx, "elements": self.els}
        if self.rt:
            m["render_type"] = self.rt
        return m


CUTOUT = "minecraft:cutout"
_ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def _variant(ctx, model, y=0):
    v = {"model": ctx.model(model)}
    if y:
        v["y"] = y
    return v


def _tf(b):
    return "true" if b else "false"


def _sides(tid, *names):
    return {n: tid for n in names}


def _ends(faces, p, n, tid, lo="west", hi="east"):
    """Add the end faces of a run only where the run actually ends."""
    if n:
        faces[lo] = tid
    if p:
        faces[hi] = tid
    return faces


ALL4 = ("north", "south", "west", "east")
PN = ((1, 1), (1, 0), (0, 1), (0, 0))

TEXTURES = {}
BLOCKS = []


def _tex(tid):
    def deco(fn):
        TEXTURES[tid] = fn
        return fn
    return deco


def _texv(tid, **kw):
    """Register painter(**kw) under tid (cap/mid/cap and similar variants)."""
    def deco(fn):
        TEXTURES[tid] = lambda: fn(**kw)
        return fn
    return deco


# =================================================================================================
# lab_bench -- horizontal cap/mid/cap. Resin worktop on a dark drawer rail, chrome legs on the ends.
# =================================================================================================
def _bench_top(p, n):
    c = _C(BODY[2])
    c.h(0, 16, 0, CER[3]).h(0, 16, 1, CER[2]).h(0, 16, 2, CER[1])
    c.h(0, 16, 13, CER[3]).h(0, 16, 14, CER[2]).h(0, 16, 15, CER[0])
    c.h(0, 16, 3, BODY[1]).h(0, 16, 4, BODY[3]).h(0, 16, 12, BODY[0])
    for x in range(1, 16, 4):                       # engraved rule along the front edge
        c.p(x, 14, CER[1])
    c.ps([(3, 6), (11, 6), (7, 10), (15, 10)], BODY[1])   # anti-slip studs
    c.ps([(3, 7), (11, 7), (7, 11), (15, 11)], BODY[3])
    c.v(15, 5, 12, BODY[1])                         # slab joint
    if n:
        c.v(0, 0, 16, CER[3]).v(1, 1, 15, CER[2]).v(2, 3, 13, CER[1]).p(0, 0, CER[4]).p(0, 15, CER[1])
    if p:
        c.v(15, 0, 16, CER[0]).v(14, 1, 15, CER[2]).v(13, 3, 13, CER[3]).p(15, 0, CER[2])
    return c.w("lab_bench_top%d%d" % (p, n), BODY, 0.05)


for _p, _n in PN:
    TEXTURES["lab_bench_top_c%d%d" % (_p, _n)] = (lambda p=_p, n=_n: _bench_top(p, n))


@_tex("lab_bench_side")
def _bench_side():
    c = _C(BODY[0])
    c.h(0, 16, 0, CER[3]).h(0, 16, 1, CER[1])
    for x0 in (0, 8):
        c.b(x0, 2, x0 + 8, 6, BODY, 2)
        c.p(x0 + 2, 4, STEEL[3]).p(x0 + 3, 4, STEEL[2]).p(x0 + 4, 4, STEEL[2]).p(x0 + 5, 4, STEEL[1])
        c.p(x0 + 6, 3, ALLOY[3])
    c.h(0, 16, 11, BODY[3]).h(0, 16, 12, BODY[1])
    return c.w("lab_bench_side", BODY, 0.04)


@_tex("lab_bench_end")
def _bench_end():
    c = _C(BODY[0])
    c.h(0, 16, 0, CER[3]).h(0, 16, 1, CER[1])
    c.b(1, 2, 15, 6, BODY, 2)
    c.ps([(3, 4), (12, 4)], STEEL[1])
    c.h(3, 13, 11, STEEL[2]).h(3, 13, 12, STEEL[0])
    for x in (1, 13):
        c.v(x, 6, 16, STEEL[2]).v(x + 1, 6, 16, STEEL[1])
        c.p(x, 6, STEEL[0]).p(x + 1, 6, STEEL[0]).p(x, 7, STEEL[3]).p(x, 8, STEEL[3])
        c.p(x, 15, BODY[2]).p(x + 1, 15, BODY[0])
    return c.i


@_tex("lab_bench_shelf")
def _bench_shelf():
    c = _C(BODY[1])
    c.h(0, 16, 2, BODY[3]).h(0, 16, 13, BODY[0])
    for y in (5, 8, 11):
        c.h(0, 16, y, BODY[0]).h(0, 16, y - 1, BODY[2])
    return c.w("lab_bench_shelf", BODY, 0.05)


def _bench_models(ctx):
    out = {}
    for p, n in PN:
        m = _M(ctx, "lab_bench_side")
        top = "lab_bench_top_c%d%d" % (p, n)
        m.box([0, 14, 0], [16, 16, 16], _ends({"up": top, "down": "lab_bench_shelf",
                                               "north": "lab_bench_side", "south": "lab_bench_side"},
                                              p, n, "lab_bench_end"))
        m.box([0, 10, 1], [16, 14, 15], _ends({"north": "lab_bench_side", "south": "lab_bench_side",
                                               "down": "lab_bench_shelf"}, p, n, "lab_bench_end"))
        x0, x1 = (3 if n else 0), (13 if p else 16)
        m.box([x0, 3, 2], [x1, 5, 14], {"up": "lab_bench_shelf", "down": "lab_bench_shelf",
                                        "north": "lab_bench_side", "south": "lab_bench_side"})
        for cap, lx in ((n, 1), (p, 13)):
            if not cap:
                continue
            for lz in (1, 13):
                m.box([lx, 0, lz], [lx + 2, 10, lz + 2], _sides("lab_bench_end", *ALL4, "down"))
            m.box([lx, 3, 3], [lx + 2, 5, 13], {"west": "lab_bench_end", "east": "lab_bench_end",
                                                "up": "lab_bench_shelf", "down": "lab_bench_shelf"})
        out["lab_bench_c%d%d" % (p, n)] = m.json()
    return out


def _axis_link_state(ctx, base):
    v = {}
    for axis, y in (("x", 0), ("z", 90)):
        for p, n in PN:
            v["axis=%s,neg_link=%s,pos_link=%s" % (axis, _tf(not n), _tf(not p))] = \
                _variant(ctx, "%s_c%d%d" % (base, p, n), y)
    return {"variants": v}


BLOCKS.append(Block(
    "lab_bench", shape="custom", connects="strip", name="Lab Bench",
    textures={k: k for k in ["lab_bench_side", "lab_bench_end", "lab_bench_shelf"]
              + ["lab_bench_top_c%d%d" % pn for pn in PN]},
    model=_bench_models, blockstate=lambda ctx: _axis_link_state(ctx, "lab_bench"),
    item_model="lab_bench_c11"))


# =================================================================================================
# fume_hood -- full-block hood: plinth, lit chamber, plenum, glass sash (`open` raises it).
# =================================================================================================
def _base_band(c, lip=True):
    """Shared plinth rows 12..15 for the hood elevations."""
    c.h(0, 16, 12, STEEL[3] if lip else STEEL[2]).h(0, 16, 13, STEEL[1] if lip else STEEL[0])
    c.h(0, 16, 14, BODY[2]).h(0, 16, 15, BODY[0])
    return c


@_tex("fume_hood_front")
def _hood_front():
    c = _C(BODY[0])
    c.b(0, 0, 16, 3, STATION, 2)
    for x in (2, 5, 8):
        c.p(x, 1, STATION[0]).p(x + 1, 1, STATION[0])
    c.p(11, 1, STEEL[3]).p(12, 1, STEEL[2])
    c.p(14, 1, CIND[1])                                  # lamp socket (lit by the glow layer)
    c.v(0, 3, 12, STATION[3]).v(1, 3, 12, STATION[1]).v(14, 3, 12, STATION[2]).v(15, 3, 12, STATION[0])
    _base_band(c)
    c.p(0, 12, STEEL[4])
    c.ps([(2, 14), (4, 14)], STEEL[2]).ps([(2, 15), (4, 15)], STEEL[0])
    for x in range(9, 15):
        c.p(x, 14, CIND[3] if x % 2 else BODY[0])
    return c.w("fume_hood_front", BODY, 0.04)


@_tex("fume_hood_front_glow")
def _hood_front_glow():
    def frame(t):
        c = _C()
        on = t < 0.5
        c.p(14, 1, AMBER[1] if on else AMBER[0])
        return c.i
    return animate("fume_hood_front_glow", "flicker", frame)


@_tex("fume_hood_side")
def _hood_side():
    c = _C(BODY[1])
    c.b(0, 0, 16, 3, STATION, 2)
    c.b(1, 3, 14, 12, BODY, 1)
    c.b(3, 5, 12, 10, BODY, 2)
    c.ps([(2, 4), (12, 4), (2, 10), (12, 10)], STEEL[1])
    c.ps([(9, 6), (8, 7), (10, 7), (7, 8), (8, 8), (9, 8), (10, 8), (11, 8)], CIND[3]).p(9, 7, BODY[0])  # hazard mark
    c.h(4, 6, 6, BODY[0]).h(4, 6, 7, BODY[3]).h(4, 6, 8, BODY[0])
    c.v(0, 3, 12, BODY[0]).v(14, 3, 12, BODY[2]).v(15, 3, 12, BODY[0])
    _base_band(c, lip=False)
    return c.w("fume_hood_side", BODY, 0.05)


@_tex("fume_hood_back")
def _hood_back():
    c = _C(BODY[1])
    c.b(0, 0, 16, 12, BODY, 1)
    c.b(0, 0, 16, 3, STATION, 2)
    c.b(6, 0, 10, 12, STATION, 2)
    for y in (3, 7):
        c.h(6, 10, y, STATION[0]).h(6, 10, y + 1, STATION[3])
    c.ps([(2, 2), (13, 2), (2, 9), (13, 9)], STEEL[1])
    _base_band(c, lip=False)
    return c.w("fume_hood_back", BODY, 0.05)


@_tex("fume_hood_top")
def _hood_top():
    c = _C(STATION[2])
    c.b(0, 0, 16, 16, STATION, 2)
    c.b(2, 2, 14, 14, BODY, 2)
    c.r(5, 6, 11, 12, STEEL[1]).h(5, 11, 6, STEEL[3]).v(5, 6, 12, STEEL[2]).h(6, 11, 11, STEEL[0])
    c.r(6, 7, 10, 11, BODY[0]).h(7, 10, 10, BODY[1])
    c.ps([(5, 6), (10, 6), (5, 11), (10, 11)], BODY[2]).p(6, 7, STEEL[0])
    return c.w("fume_hood_top", BODY, 0.04)


@_tex("fume_hood_inner")
def _hood_inner():
    c = _C(CER[2])
    c.h(0, 16, 3, CER[0]).h(0, 16, 4, CER[1]).h(0, 16, 11, CER[1])
    c.v(2, 4, 12, CER[3])
    for y in (6, 9):
        for x in (3, 7, 11):
            c.h(x, x + 3, y, BODY[1]).h(x, x + 3, y + 1, CER[3])
    return c.i


@_tex("fume_hood_ceil")
def _hood_ceil():
    c = _C(BODY[1])
    c.b(2, 1, 14, 14, CER, 1)
    c.r(3, 6, 13, 8, STEEL[1])
    return c.i


@_tex("fume_hood_ceil_glow")
def _hood_ceil_glow():
    def frame(t):
        k = 0.5 + 0.5 * math.cos(2 * math.pi * t)
        c = _C()
        c.h(3, 13, 6, lerp(AMBER[1], AMBER[2], 0.6 + 0.4 * k)).h(3, 13, 7, lerp(AMBER[0], AMBER[1], 0.5 + 0.5 * k))
        return c.i
    return animate("fume_hood_ceil_glow", "breathing", frame)


@_tex("fume_hood_deck")
def _hood_deck():
    c = _C(BODY[1])
    c.h(0, 16, 0, STEEL[3])
    c.b(2, 1, 14, 14, BODY, 2)
    c.b(3, 3, 13, 13, BODY, 1)
    c.p(8, 11, BODY[0]).p(7, 11, BODY[3])
    return c.w("fume_hood_deck", BODY, 0.05)


@_tex("fume_hood_sash")
def _hood_sash():
    c = _C()
    c.h(2, 14, 3, STEEL[1])
    c.v(2, 3, 12, STEEL[2]).v(13, 3, 12, STEEL[0])
    c.h(2, 14, 10, STEEL[3]).h(2, 14, 11, STEEL[0])
    c.h(6, 10, 10, STEEL[4]).p(2, 10, STEEL[4])
    c.ps([(4, 6), (5, 5), (6, 4)], STEEL[4]).ps([(10, 8), (11, 7)], STEEL[2])
    return c.i


@_tex("fume_hood_props")
def _hood_props():
    c = _C(BODY[1])
    # flask (2x3) at 0,0: neck, then amber reagent
    c.p(0, 0, CER[4]).p(1, 0, CER[2]).p(0, 1, CIND[3]).p(1, 1, CIND[2]).p(0, 2, CIND[2]).p(1, 2, CIND[1])
    c.r(0, 4, 2, 6, CER[3])                                          # flask top
    # hot plate side (4x1) + top (4x4)
    c.h(4, 8, 0, STEEL[1]).r(4, 4, 8, 8, BODY[2]).r(5, 5, 7, 7, STEEL[1]).p(5, 5, STEEL[3]).p(4, 4, BODY[3])
    # beaker (2x2 side, 2x2 top)
    c.p(10, 0, CER[4]).p(11, 0, CER[2]).p(10, 1, ALLOY[3]).p(11, 1, ALLOY[2])
    c.r(10, 4, 12, 6, ALLOY[3]).p(10, 4, CER[4])
    return c.i


@_tex("fume_hood_props_glow")
def _hood_props_glow():
    def frame(t):
        k = 0.5 + 0.5 * math.cos(2 * math.pi * t)
        c = _C()
        c.p(0, 1, lerp(AMBER[0], AMBER[1], k)).p(1, 1, lerp(CIND[2], AMBER[0], k))
        return c.i
    return animate("fume_hood_props_glow", "breathing", frame)


def _hood_models(ctx):
    out = {}
    for is_open in (False, True):
        m = _M(ctx, "fume_hood_side", CUTOUT)
        ext = {"north": "fume_hood_front", "south": "fume_hood_back", "west": "fume_hood_side",
               "east": "fume_hood_side~"}
        m.box([0, 0, 0], [16, 4, 16], dict(ext, up="fume_hood_deck", down="fume_hood_top"))
        m.box([0, 4, 14], [16, 13, 16], {"south": "fume_hood_back", "west": "fume_hood_side",
                                         "east": "fume_hood_side~", "north": "fume_hood_inner"})
        m.box([0, 4, 1], [2, 13, 14], {"west": "fume_hood_side", "east": "fume_hood_inner",
                                       "north": "fume_hood_front"})
        m.box([14, 4, 1], [16, 13, 14], {"east": "fume_hood_side~", "west": "fume_hood_inner",
                                         "north": "fume_hood_front"})
        plenum = ([0, 13, 0], [16, 16, 16])
        m.box(*plenum, dict(ext, up="fume_hood_top", down="fume_hood_ceil"))
        # chamber props
        m.box([9, 4, 6], [11, 7, 8], dict({f: ("fume_hood_props", [0, 0, 2, 3]) for f in ALL4},
                                           up=("fume_hood_props", [0, 4, 2, 6])))
        m.box([4, 4, 8], [8, 5, 12], dict({f: ("fume_hood_props", [4, 0, 8, 1]) for f in ALL4},
                                           up=("fume_hood_props", [4, 4, 8, 8])))
        m.box([5, 5, 9], [7, 7, 11], dict({f: ("fume_hood_props", [10, 0, 12, 2]) for f in ALL4},
                                           up=("fume_hood_props", [10, 4, 12, 6])))
        if is_open:
            m.box([2, 10, 1], [14, 13, 2], {"north": ("fume_hood_sash", [2, 9, 14, 12]),
                                            "south": ("fume_hood_sash", [14, 9, 2, 12]),
                                            "down": ("fume_hood_sash", [2, 11, 14, 12])})
        else:
            m.box([2, 4, 1], [14, 13, 2], {"north": "fume_hood_sash", "south": "fume_hood_sash",
                                           "down": ("fume_hood_sash", [2, 11, 14, 12])})
        m.glow(*plenum, {"north": "fume_hood_front_glow", "down": "fume_hood_ceil_glow"})
        m.glow([9, 4, 6], [11, 7, 8], {f: ("fume_hood_props_glow", [0, 0, 2, 3]) for f in ALL4})
        out["fume_hood_open" if is_open else "fume_hood"] = m.json()
    return out


def _facing_open_state(ctx, base):
    v = {}
    for f, y in _ROT.items():
        for o in (False, True):
            v["facing=%s,open=%s" % (f, _tf(o))] = _variant(ctx, base + ("_open" if o else ""), y)
    return {"variants": v}


BLOCKS.append(Block(
    "fume_hood", shape="custom", emissive=True, name="Fume Hood", render_type=CUTOUT,
    textures={k: k for k in ["fume_hood_front", "fume_hood_front_glow", "fume_hood_side",
                             "fume_hood_back", "fume_hood_top", "fume_hood_inner", "fume_hood_ceil",
                             "fume_hood_ceil_glow", "fume_hood_deck", "fume_hood_sash",
                             "fume_hood_props", "fume_hood_props_glow"]},
    model=_hood_models, blockstate=lambda ctx: _facing_open_state(ctx, "fume_hood"),
    item_model="fume_hood"))


# =================================================================================================
# specimen_tank -- vertical cap/mid/cap. Octagonal glass (4 strips, two of them turned 45 deg) around
# a chroma-tinted fluid column; bubbles, meniscus and base ring are the glow layer. A specimen is
# painted on the cardinal fluid faces and grows with the stack (tail / spine / head).
# =================================================================================================
_INK = (BODY[3], FLUID[0])      # shaded core, murky silhouette (both darken under the tint)

# fluid sheet columns: 0-3 cardinal N/S (specimen front), 4-7 cardinal E/W (specimen side),
# 8-11 and 12-15 the diagonals
_HEAD = [(1, 6), (2, 6), (1, 7), (2, 7), (1, 8), (2, 8)]


def _both(pts):
    """The same silhouette on the N/S columns (0-3) and the E/W columns (4-7): seen from any corner
    the two visible faces agree, so it reads as one body hanging in the middle of the column."""
    return pts + [(x + 4, y) for x, y in pts]


_SPEC = {   # (p, n) -> body pixels, shaded pixels, glowing eye
    (1, 1): (_both(_HEAD + [(2, 9), (2, 10), (1, 11)]), _both([(2, 8), (2, 10)]), _both([(1, 7)])),
    (1, 0): (_both(_HEAD + [(2, y) for y in range(9, 16)]), _both([(2, 8), (2, 12)]),
             _both([(1, 7)])),
    (0, 0): (_both([(2, y) for y in range(16)]), _both([(2, 4), (2, 12)]), []),
    (0, 1): (_both([(2, y) for y in range(0, 9)] + [(1, 9), (1, 10), (2, 11)]), _both([(2, 4), (1, 10)]), []),
}


def _tank_fluid(p, n):
    c = _C(FLUID[2])
    for x0 in (0, 4, 8, 12):
        c.v(x0, 0, 16, FLUID[3]).v(x0 + 3, 0, 16, FLUID[1])
    if p:
        c.h(0, 16, 4, FLUID[4])
    s = _SPEC[(p, n)]
    c.ps(s[0], _INK[1]).ps(s[1], _INK[0])
    return c.i


_BUBBLES = ((0, 0.05, 1), (3, 0.50, 2), (4, 0.30, 1), (7, 0.80, 1), (9, 0.15, 2), (10, 0.65, 1),
            (13, 0.40, 1), (14, 0.90, 2), (12, 0.02, 1))


def _tank_fluid_glow(p, n):
    s = _SPEC[(p, n)]
    solid = set(s[0])

    def frame(t):
        c = _C()
        for x, ph, ln in _BUBBLES:
            y = int(round((ph - t) * 16)) % 16
            for k in range(ln):
                yy = (y + k) % 16
                if (x, yy) not in solid and not (p and yy <= 4):
                    c.p(x, yy, FLUID[4] if k == 0 else FLUID[3])
        if p:
            c.h(0, 16, 4, FLUID[4])
        c.ps(s[2], FLUID[4])
        return c.i
    return animate("specimen_tank_fluid_glow_c%d%d" % (p, n), "flow", frame)


for _p, _n in PN:
    TEXTURES["specimen_tank_fluid_c%d%d" % (_p, _n)] = (lambda p=_p, n=_n: _tank_fluid(p, n))
    TEXTURES["specimen_tank_fluid_glow_c%d%d" % (_p, _n)] = (lambda p=_p, n=_n: _tank_fluid_glow(p, n))


@_tex("specimen_tank_glass")
def _tank_glass():
    c = _C()
    c.ps([(1, 2), (1, 3), (1, 4)], STEEL[4]).p(1, 6, STEEL[3])
    c.p(8, 11, STEEL[2])
    return c.i


@_tex("specimen_tank_cap")
def _tank_cap():
    c = _C(BODY[1])
    c.h(1, 15, 0, BODY[3]).h(1, 15, 1, BODY[1]).p(1, 0, BODY[4]).p(14, 0, BODY[2]).p(14, 1, BODY[0])
    c.ps([(3, 1), (12, 1)], STEEL[1])
    c.h(2, 14, 2, STEEL[1]).p(2, 2, STEEL[3]).p(13, 2, STEEL[0])
    c.h(2, 14, 13, STEEL[1]).p(2, 13, STEEL[2]).p(13, 13, STEEL[0])
    c.h(1, 15, 14, BODY[3]).h(1, 15, 15, BODY[0]).p(1, 14, BODY[4]).p(14, 14, BODY[1])
    c.ps([(5, 15), (7, 15), (9, 15)], BODY[2]).p(12, 14, ALLOY[3])
    return c.i


@_tex("specimen_tank_cap_glow")
def _tank_cap_glow():
    def frame(t):
        k = 0.5 + 0.5 * math.cos(2 * math.pi * t)
        c = _C()
        for x in range(3, 13, 2):
            c.p(x, 13, lerp(FLUID[3], FLUID[4], k))
        return c.i
    return animate("specimen_tank_cap_glow", "breathing", frame)


@_tex("specimen_tank_lid")
def _tank_lid():
    c = _C(BODY[1])
    c.b(1, 1, 15, 15, BODY, 2)
    c.b(4, 4, 12, 12, BODY, 1)
    c.ps([(5, 5), (10, 5), (5, 10), (10, 10)], STEEL[2])
    c.p(7, 7, STEEL[3]).p(8, 7, STEEL[2]).p(7, 8, STEEL[1]).p(8, 8, STEEL[0])
    c.p(12, 3, ALLOY[3]).p(13, 3, ALLOY[1])
    c.ps([(2, 2), (13, 13)], STEEL[1])
    return c.w("specimen_tank_lid", BODY, 0.05)


@_tex("specimen_tank_surface")
def _tank_surface():
    c = _C(FLUID[2])
    c.r(5, 5, 11, 11, FLUID[3]).ps([(6, 6), (7, 6), (6, 7)], FLUID[4]).ps([(9, 9), (10, 9), (9, 10)], FLUID[1])
    return c.i


def _tank_models(ctx):
    out = {}
    for p, n in PN:
        m = _M(ctx, "specimen_tank_cap", CUTOUT)
        y0, y1 = (3 if n else 0), (13 if p else 16)
        fy1 = 12 if p else 16
        fl, gl = "specimen_tank_fluid_c%d%d" % (p, n), "specimen_tank_fluid_glow_c%d%d" % (p, n)
        cap = _sides("specimen_tank_cap", *ALL4)
        if n:
            m.box([1, 0, 1], [15, 2, 15], dict(cap, up="specimen_tank_lid", down="specimen_tank_lid"))
            m.box([2, 2, 2], [14, 3, 14], dict(cap, up="specimen_tank_lid"))
        if p:
            m.box([2, 13, 2], [14, 14, 14], dict(cap, down="specimen_tank_lid"))
            m.box([1, 14, 1], [15, 16, 15], dict(cap, up="specimen_tank_lid", down="specimen_tank_lid"))

        def col(u, a, b):
            return [u, 16 - b, u + 4, 16 - a]

        def gcol(u, a, b):
            return [u, 16 - b, u + 5, 16 - a]
        # fluid column (tinted), then its glow twin
        for emit in (0, 15):
            t = gl if emit else fl
            m.box([6, y0, 3], [10, fy1, 13], {"north": (t, col(0, y0, fy1)), "south": (t, col(0, y0, fy1))},
                  tint=True, emit=emit)
            m.box([3, y0, 6], [13, fy1, 10], {"west": (t, col(4, y0, fy1)), "east": (t, col(4, y0, fy1))},
                  tint=True, emit=emit)
            m.box([3, y0, 6], [13, fy1, 10], {"west": (t, col(8, y0, fy1)), "east": (t, col(12, y0, fy1))},
                  tint=True, emit=emit, rot=45)
            m.box([3, y0, 6], [13, fy1, 10], {"west": (t, col(12, y0, fy1)), "east": (t, col(8, y0, fy1))},
                  tint=True, emit=emit, rot=-45)
        if p:
            m.box([4.5, 11, 4.5], [11.5, 12, 11.5], {"up": "specimen_tank_surface"}, tint=True)
        if n:
            base = ([2, 2, 2], [14, 3, 14])
            m.glow(*base, _sides("specimen_tank_cap_glow", *ALL4), tint=True)
        # glass
        g = "specimen_tank_glass"
        m.box([5.5, y0, 2], [10.5, y1, 14], {"north": (g, gcol(0, y0, y1)), "south": (g, gcol(5, y0, y1))})
        m.box([2, y0, 5.5], [14, y1, 10.5], {"west": (g, gcol(0, y0, y1)), "east": (g, gcol(5, y0, y1))})
        m.box([2, y0, 5.5], [14, y1, 10.5], {"west": (g, gcol(0, y0, y1)), "east": (g, gcol(10, y0, y1))}, rot=45)
        m.box([2, y0, 5.5], [14, y1, 10.5], {"west": (g, gcol(10, y0, y1)), "east": (g, gcol(5, y0, y1))}, rot=-45)
        out["specimen_tank_c%d%d" % (p, n)] = m.json()
    return out


def _tank_state(ctx):
    return {"variants": {"neg_link=%s,pos_link=%s" % (_tf(not n), _tf(not p)):
                         _variant(ctx, "specimen_tank_c%d%d" % (p, n)) for p, n in PN}}


BLOCKS.append(Block(
    "specimen_tank", shape="custom", connects="strip", chroma=True, emissive=True,
    name="Specimen Tank", render_type=CUTOUT,
    textures={k: k for k in ["specimen_tank_glass", "specimen_tank_cap", "specimen_tank_cap_glow",
                             "specimen_tank_lid", "specimen_tank_surface"]
              + ["specimen_tank_fluid_c%d%d" % pn for pn in PN]
              + ["specimen_tank_fluid_glow_c%d%d" % pn for pn in PN]},
    model=_tank_models, blockstate=_tank_state, item_model="specimen_tank_c11"))


# =================================================================================================
# sample_fridge -- ceramic cabinet, twin doors with frosted windows; `open` swings both doors 90 deg
# and shows the lit shelves and vial racks.
# =================================================================================================
@_tex("sample_fridge_front")
def _fridge_front():
    c = _C(BODY[0])
    for x0 in (0, 8):
        c.b(x0, 0, x0 + 8, 14, CER, 2)
        c.r(x0 + 2, 4, x0 + 6, 11, GLAC[0])
        c.h(x0 + 2, x0 + 6, 4, BODY[0]).h(x0 + 2, x0 + 6, 7, GLAC[1]).h(x0 + 2, x0 + 6, 10, GLAC[1])
        c.p(x0 + 5, 5, GLAC[2]).p(x0 + 4, 6, GLAC[1])
        c.h(x0 + 2, x0 + 6, 11, CER[3]).v(x0 + 1, 4, 11, CER[1])
    c.r(1, 1, 7, 3, BODY[1]).h(1, 7, 1, BODY[0])               # readout window (left leaf)
    c.r(10, 1, 14, 2, CER[1])                                  # badge plate (right leaf)
    c.p(12, 1, ALLOY[2])
    c.v(6, 5, 10, STEEL[1]).p(6, 5, STEEL[3]).v(9, 5, 10, STEEL[2]).p(9, 5, STEEL[4])
    c.h(0, 16, 14, BODY[2]).h(0, 16, 15, BODY[0])
    for x in range(2, 14, 2):
        c.p(x, 14, BODY[0])
    return c.w("sample_fridge_front", CER, 0.025)


@_tex("sample_fridge_front_glow")
def _fridge_front_glow():
    digits = ([(2, 1), (2, 2), (4, 1), (4, 2), (5, 2)], [(2, 1), (2, 2), (4, 1), (5, 1), (5, 2)],
              [(2, 1), (2, 2), (4, 2), (5, 1), (5, 2)], [(2, 1), (2, 2), (4, 1), (4, 2), (5, 1)])

    def frame(t):
        c = _C()
        c.ps(digits[int(t * 4) % 4], PLASMA[3])
        for x0 in (0, 8):
            c.ps([(x0 + 2, 6), (x0 + 4, 6), (x0 + 3, 9), (x0 + 5, 9)], PLASMA[2])
        return c.i
    return animate("sample_fridge_front_glow", "data", frame)


@_tex("sample_fridge_side")
def _fridge_side():
    c = _C(BODY[0])
    c.b(2, 0, 16, 14, CER, 2)
    c.v(0, 0, 14, CER[3]).v(1, 0, 14, CER[0]).p(0, 0, CER[4])
    for y in (9, 11):
        c.h(9, 14, y, CER[0]).h(9, 14, y + 1, CER[3])
    c.r(4, 3, 7, 6, ALLOY[2]).p(4, 3, ALLOY[4]).h(4, 7, 5, ALLOY[1]).p(5, 4, CER[4])
    c.h(2, 16, 14, BODY[2]).h(2, 16, 15, BODY[0])
    return c.w("sample_fridge_side", CER, 0.025)


@_tex("sample_fridge_back")
def _fridge_back():
    c = _C(BODY[0])
    c.b(0, 0, 16, 14, CER, 1)
    c.b(3, 5, 13, 12, BODY, 1)
    for y in (7, 9):
        c.h(4, 12, y, BODY[0]).h(4, 12, y + 1, BODY[3])
    c.ps([(2, 2), (13, 2)], STEEL[1]).h(5, 11, 2, CER[0])
    c.h(0, 16, 14, BODY[2]).h(0, 16, 15, BODY[0])
    return c.w("sample_fridge_back", CER, 0.025)


@_tex("sample_fridge_top")
def _fridge_top():
    c = _C(CER[2])
    c.b(0, 2, 16, 16, CER, 2)
    c.h(0, 16, 0, CER[3]).h(0, 16, 1, CER[0]).p(0, 0, CER[4])
    c.r(4, 11, 12, 13, BODY[1]).h(4, 12, 11, BODY[0]).h(4, 12, 13, CER[3])
    return c.w("sample_fridge_top", CER, 0.025)


@_tex("sample_fridge_inner")
def _fridge_inner():
    c = _C(LINER[2])
    c.v(1, 0, 16, LINER[1]).v(14, 0, 16, LINER[3])
    c.h(0, 16, 0, LINER[0])
    c.h(3, 13, 1, STEEL[2])
    for y in (5, 9):                                            # shelf shadows
        c.h(0, 16, y, LINER[1])
    c.ps([(2, 3), (13, 12), (3, 12)], LINER[4])
    return c.i


@_tex("sample_fridge_inner_glow")
def _fridge_inner_glow():
    def frame(t):
        k = 0.5 + 0.5 * math.cos(2 * math.pi * t)
        c = _C()
        c.h(3, 13, 1, lerp(PLASMA[3], PLASMA[4], k))
        return c.i
    return animate("sample_fridge_inner_glow", "breathing", frame)


@_tex("sample_fridge_rack")
def _fridge_rack():
    c = _C(BODY[1])
    for x in range(0, 10):
        c.p(x, 0, STEEL[2] if x % 2 == 0 else LINER[1])
        c.p(x, 1, PLASMA[1] if x % 2 == 0 else LINER[1])
    c.h(0, 10, 2, BODY[2]).p(0, 2, BODY[3]).p(9, 2, BODY[0])
    c.r(10, 0, 14, 3, BODY[2]).v(13, 0, 3, BODY[0]).v(10, 0, 3, BODY[3])
    c.r(0, 4, 10, 8, BODY[1])
    for x in range(0, 10, 2):
        c.p(x, 5, STEEL[3]).p(x, 6, STEEL[1])
    return c.i


@_tex("sample_fridge_rack_glow")
def _fridge_rack_glow():
    def frame(t):
        c = _C()
        for i, x in enumerate(range(0, 10, 2)):
            k = 0.5 + 0.5 * math.cos(2 * math.pi * (t + i / 5.0))
            c.p(x, 1, lerp(PLASMA[2], PLASMA[3], k))
        return c.i
    return animate("sample_fridge_rack_glow", "breathing", frame)


@_tex("sample_fridge_door_in")
def _fridge_door_in():
    c = _C(LINER[1])
    c.b(0, 0, 8, 14, LINER, 2)
    for y in (4, 8, 12):
        c.h(1, 7, y, BODY[2]).h(1, 7, y - 1, LINER[3])
    c.ps([(2, 3), (4, 3), (5, 7), (3, 11)], PLASMA[1]).ps([(2, 2), (4, 2), (5, 6), (3, 10)], STEEL[3])
    return c.i


def _fridge_models(ctx):
    out = {}
    F, S, B, T, I = ("sample_fridge_front", "sample_fridge_side", "sample_fridge_back",
                     "sample_fridge_top", "sample_fridge_inner")
    for is_open in (False, True):
        m = _M(ctx, S, CUTOUT)
        m.box([1, 0, 2], [15, 2, 16], {"north": F, "south": B, "west": S, "east": S + "~", "down": T})
        if not is_open:
            m.box([0, 2, 2], [16, 16, 16], {"south": B, "west": S, "east": S + "~", "up": T, "down": T})
            for x0 in (0, 8):
                leaf = ([x0, 2, 0], [x0 + 8, 16, 2])
                faces = {"north": F, "up": T, "down": T}
                faces["west" if x0 == 0 else "east"] = S if x0 == 0 else S + "~"
                m.box(*leaf, faces)
            for x0 in (0, 8):
                m.glow([x0, 2, 0], [x0 + 8, 16, 2], {"north": "sample_fridge_front_glow"})
        else:
            m.box([0, 2, 2], [1, 16, 16], {"west": S, "east": I, "north": S, "south": B, "up": T})
            m.box([15, 2, 2], [16, 16, 16], {"east": S + "~", "west": I, "north": S, "south": B, "up": T})
            back = ([1, 2, 15], [15, 16, 16])
            m.box(*back, {"north": I, "south": B, "up": T})
            m.box([1, 15, 2], [15, 16, 15], {"up": T, "down": I, "north": S})
            m.box([1, 2, 2], [15, 3, 15], {"up": I, "north": S})
            for y in (7, 11):
                m.box([1, y, 3], [15, y + 1, 15], {"up": I, "down": I, "north": ("sample_fridge_rack", [0, 2, 14, 3])})
            racks = [([3, 3, 6], [13, 6, 10]), ([3, 8, 6], [13, 11, 10]), ([3, 12, 6], [13, 14, 10])]
            for f, t in racks:
                hgt = t[1] - f[1]
                m.box(f, t, {"north": ("sample_fridge_rack", [0, 3 - hgt, 10, 3]),
                             "south": ("sample_fridge_rack", [0, 3 - hgt, 10, 3]),
                             "west": ("sample_fridge_rack", [10, 3 - hgt, 14, 3]),
                             "east": ("sample_fridge_rack", [10, 3 - hgt, 14, 3]),
                             "up": ("sample_fridge_rack", [0, 4, 10, 8])})
            # doors swung open: outer skin faces sideways, liner faces the opening
            m.box([0, 2, -6], [2, 16, 2], {"west": (F, [8, 0, 16, 14]), "east": ("sample_fridge_door_in", [0, 0, 8, 14]),
                                           "north": (S, [0, 0, 2, 14]), "up": (T, [0, 0, 2, 8]), "down": (T, [0, 0, 2, 8])})
            m.box([14, 2, -6], [16, 16, 2], {"east": (F, [0, 0, 8, 14]), "west": ("sample_fridge_door_in", [8, 0, 0, 14]),
                                             "north": (S, [0, 0, 2, 14]), "up": (T, [0, 0, 2, 8]), "down": (T, [0, 0, 2, 8])})
            m.glow(*back, {"north": "sample_fridge_inner_glow"})
            for f, t in racks:
                hgt = t[1] - f[1]
                m.glow(f, t, {"north": ("sample_fridge_rack_glow", [0, 3 - hgt, 10, 3])})
        out["sample_fridge_open" if is_open else "sample_fridge"] = m.json()
    return out


BLOCKS.append(Block(
    "sample_fridge", shape="custom", emissive=True, name="Sample Fridge", render_type=CUTOUT,
    textures={k: k for k in ["sample_fridge_front", "sample_fridge_front_glow", "sample_fridge_side",
                             "sample_fridge_back", "sample_fridge_top", "sample_fridge_inner",
                             "sample_fridge_inner_glow", "sample_fridge_rack", "sample_fridge_rack_glow",
                             "sample_fridge_door_in"]},
    model=_fridge_models, blockstate=lambda ctx: _facing_open_state(ctx, "sample_fridge"),
    item_model="sample_fridge"))


# =================================================================================================
# lab_stool -- padded round seat on a chrome column with a foot ring. Seat top at y=11 (SEAT_Y).
# =================================================================================================
@_tex("lab_stool_seat")
def _stool_seat():
    c = _C(BODY[1])
    c.r(3, 3, 13, 13, ALLOY[2])
    c.h(5, 11, 3, ALLOY[3]).v(3, 5, 11, ALLOY[3]).h(5, 11, 12, ALLOY[0]).v(12, 5, 11, ALLOY[0])
    c.h(5, 11, 4, ALLOY[3]).v(5, 5, 11, ALLOY[3]).ps([(5, 4), (6, 4), (5, 5)], ALLOY[4])
    c.h(5, 11, 11, ALLOY[1]).v(11, 5, 11, ALLOY[1])
    for x, y in ((6, 6), (9, 6), (6, 9), (9, 9)):
        c.p(x, y, ALLOY[1])
    c.p(7, 7, ALLOY[1]).p(8, 8, ALLOY[3]).p(8, 7, ALLOY[0]).p(7, 8, ALLOY[0])
    c.v(4, 5, 11, ALLOY[2]).p(4, 5, ALLOY[3])
    return c.i


@_tex("lab_stool_side")
def _stool_side():
    c = _C(BODY[1])
    c.h(3, 13, 5, ALLOY[2]).h(3, 13, 6, ALLOY[0]).p(3, 5, ALLOY[4]).p(4, 5, ALLOY[3]).p(12, 5, ALLOY[1])
    c.ps([(5, 6), (8, 6), (11, 6)], STEEL[1])
    c.h(6, 10, 7, BODY[3]).p(9, 7, BODY[0])
    c.v(7, 8, 14, STEEL[3]).v(8, 8, 14, STEEL[1]).p(7, 8, STEEL[1]).p(8, 8, STEEL[0])
    c.h(5, 11, 12, STEEL[2]).p(5, 12, STEEL[3]).p(10, 12, STEEL[0]).p(7, 12, STEEL[4]).p(8, 12, STEEL[2])
    c.h(6, 10, 14, BODY[3]).p(9, 14, BODY[1])
    c.h(3, 13, 15, BODY[2]).p(3, 15, BODY[4]).p(4, 15, BODY[3]).p(12, 15, BODY[0])
    return c.i


@_tex("lab_stool_metal")
def _stool_metal():
    c = _C(STEEL[1])
    c.b(5, 5, 11, 11, STEEL, 1)
    c.r(6, 6, 10, 10, STEEL[1]).h(6, 10, 7, STEEL[2]).v(7, 6, 10, STEEL[2]).p(7, 7, STEEL[3])
    return c.i


@_tex("lab_stool_base")
def _stool_base():
    c = _C(BODY[1])
    c.r(3, 3, 13, 13, BODY[2])
    c.h(5, 11, 3, BODY[3]).v(3, 5, 11, BODY[3]).h(5, 11, 12, BODY[0]).v(12, 5, 11, BODY[0]).p(5, 3, BODY[4])
    for i in range(4, 12):
        c.p(i, i, BODY[1]).p(15 - i, i, BODY[1])
    c.b(6, 6, 10, 10, BODY, 2)
    return c.i


def _stool_models(ctx):
    m = _M(ctx, "lab_stool_seat")
    S, K, Bt = "lab_stool_side", "lab_stool_metal", "lab_stool_base"
    m.box([5, 9, 3], [11, 11, 13], {"up": "lab_stool_seat", "down": Bt, "north": S, "south": S, "west": S, "east": S})
    m.box([3, 9, 5], [5, 11, 11], {"up": "lab_stool_seat", "down": Bt, "north": S, "south": S, "west": S})
    m.box([11, 9, 5], [13, 11, 11], {"up": "lab_stool_seat", "down": Bt, "north": S, "south": S, "east": S})
    m.box([6, 8, 6], [10, 9, 10], dict(_sides(S, *ALL4), down=K))
    m.box([7, 2, 7], [9, 8, 9], _sides(S, *ALL4))
    ring = {"up": K, "down": K}
    m.box([5, 3, 5], [11, 4, 6], dict(ring, north=S, south=S, west=S, east=S))
    m.box([5, 3, 10], [11, 4, 11], dict(ring, north=S, south=S, west=S, east=S))
    m.box([5, 3, 6], [6, 4, 10], dict(ring, west=S, east=S))
    m.box([10, 3, 6], [11, 4, 10], dict(ring, west=S, east=S))
    sp = (S, [5, 12, 6, 13])
    m.box([6, 3, 7.5], [7, 4, 8.5], dict(ring, north=sp, south=sp))
    m.box([9, 3, 7.5], [10, 4, 8.5], dict(ring, north=sp, south=sp))
    m.box([7.5, 3, 6], [8.5, 4, 7], dict(ring, west=sp, east=sp))
    m.box([7.5, 3, 9], [8.5, 4, 10], dict(ring, west=sp, east=sp))
    m.box([5, 0, 3], [11, 1, 13], {"up": Bt, "down": Bt, "north": S, "south": S, "west": S, "east": S})
    m.box([3, 0, 5], [5, 1, 11], {"up": Bt, "down": Bt, "north": S, "south": S, "west": S})
    m.box([11, 0, 5], [13, 1, 11], {"up": Bt, "down": Bt, "north": S, "south": S, "east": S})
    m.box([6, 1, 6], [10, 2, 10], dict(_sides(S, *ALL4), up=Bt))
    return {"lab_stool": m.json()}


BLOCKS.append(Block(
    "lab_stool", shape="custom", name="Lab Stool",
    textures={k: k for k in ["lab_stool_seat", "lab_stool_side", "lab_stool_metal", "lab_stool_base"]},
    model=_stool_models))


# =================================================================================================
# crew_bunk -- 1x2. Head: floor-standing bulkhead with a privacy hood, pillow. Foot: blanket, footboard.
# Cosmetic only (no spawn point, no night skip).
# =================================================================================================
def _bunk_rail(c, x0, x1):
    c.h(x0, x1, 9, STEEL[2]).h(x0, x1, 10, STEEL[0])
    c.h(x0, x1, 11, BODY[3]).h(x0, x1, 12, BODY[1]).h(x0, x1, 13, BODY[0])


@_tex("crew_bunk_side_head")
def _bunk_side_head():
    c = _C(BODY[0])
    _bunk_rail(c, 2, 16)
    c.v(0, 2, 16, BODY[3]).v(1, 2, 16, BODY[1]).p(0, 2, BODY[4])
    c.h(0, 8, 1, BODY[3]).p(7, 1, BODY[1]).p(0, 1, BODY[4])
    c.b(2, 2, 8, 11, BODY, 2)
    c.r(4, 4, 6, 8, GLAC[0]).p(4, 4, GLAC[2]).p(4, 5, GLAC[1]).h(4, 6, 8, BODY[3])
    c.v(7, 2, 11, STEEL[1]).p(7, 2, STEEL[3])
    c.h(8, 11, 8, SHEET[1])
    c.h(11, 16, 7, ALLOY[2]).h(11, 16, 8, ALLOY[1]).v(11, 7, 9, SHEET[3])
    c.ps([(5, 12), (6, 12)], ALLOY[3]).h(10, 14, 12, STEEL[1])
    return c.w("crew_bunk_side_head", BODY, 0.04)


@_tex("crew_bunk_side_foot")
def _bunk_side_foot():
    c = _C(BODY[0])
    _bunk_rail(c, 0, 14)
    c.h(0, 14, 7, ALLOY[2]).h(0, 14, 8, ALLOY[1])
    c.v(9, 7, 9, STEEL[2]).v(10, 7, 9, ALLOY[3])
    c.h(3, 7, 12, STEEL[1])
    c.v(14, 6, 16, BODY[3]).v(15, 6, 16, BODY[1]).p(14, 6, BODY[4]).p(15, 15, BODY[0])
    return c.w("crew_bunk_side_foot", BODY, 0.04)


@_tex("crew_bunk_end_head")
def _bunk_end_head():
    c = _C(BODY[0])
    c.h(0, 16, 1, BODY[3]).p(0, 1, BODY[4])
    c.b(0, 2, 16, 16, BODY, 1)
    c.b(2, 4, 14, 13, BODY, 2)
    c.r(3, 5, 8, 7, STEEL[1]).h(3, 8, 5, STEEL[2]).p(3, 5, STEEL[3]).p(6, 6, ALLOY[3])
    for y in (9, 11):
        c.h(4, 12, y, BODY[0]).h(4, 12, y + 1, BODY[3])
    c.ps([(1, 3), (14, 3), (1, 14), (14, 14)], STEEL[1])
    return c.w("crew_bunk_end_head", BODY, 0.05)


@_tex("crew_bunk_head_in")
def _bunk_head_in():
    c = _C(PAD[1])
    for x in range(0, 16, 4):
        c.v(x, 0, 16, PAD[0]).v(x + 1, 0, 16, PAD[2])
    c.h(0, 16, 2, PAD[0]).h(0, 16, 3, PAD[2]).h(0, 16, 6, PAD[0])
    c.ps([(12, 4), (13, 4)], STEEL[3]).p(13, 5, STEEL[1])          # reading lamp (unlit)
    c.r(2, 4, 5, 6, ALLOY[1]).p(2, 4, ALLOY[3]).p(3, 4, ALLOY[2])  # pinned photo
    return c.i


@_tex("crew_bunk_end_foot")
def _bunk_end_foot():
    c = _C(BODY[0])
    c.b(0, 6, 16, 16, BODY, 1)
    c.b(2, 8, 14, 14, BODY, 2)
    c.h(6, 10, 10, STEEL[2]).p(6, 10, STEEL[3]).h(6, 10, 11, STEEL[0])
    c.h(3, 13, 12, ALLOY[1])
    c.ps([(1, 7), (14, 7)], STEEL[1])
    return c.w("crew_bunk_end_foot", BODY, 0.05)


@_tex("crew_bunk_roof")
def _bunk_roof():
    c = _C(BODY[1])
    c.b(0, 0, 16, 8, BODY, 2)
    c.h(0, 16, 7, STEEL[1]).p(0, 7, STEEL[3])
    for x in (4, 8, 12):
        c.v(x, 1, 7, BODY[1]).v(x + 1, 1, 7, BODY[3])
    c.h(0, 16, 14, BODY[3]).h(0, 16, 15, BODY[0])
    return c.w("crew_bunk_roof", BODY, 0.05)


@_tex("crew_bunk_head_bed")
def _bunk_head_bed():
    c = _C(BODY[1])
    c.r(1, 2, 15, 11, SHEET[2])
    c.r(1, 2, 15, 8, SHEET[1])                                   # under the hood: in shade
    c.v(1, 2, 11, SHEET[3]).v(14, 2, 11, SHEET[1]).p(14, 2, SHEET[0])
    c.b(3, 3, 13, 8, SHEET, 3)
    c.h(6, 10, 5, SHEET[2])
    c.h(1, 15, 11, SHEET[4]).h(1, 15, 12, SHEET[3]).h(1, 15, 13, ALLOY[1])
    c.r(1, 14, 15, 16, ALLOY[2]).v(1, 14, 16, ALLOY[3]).v(14, 14, 16, ALLOY[1])
    c.h(2, 14, 15, ALLOY[1])
    c.v(0, 8, 16, STEEL[2]).v(15, 8, 16, STEEL[1])
    return c.i


@_tex("crew_bunk_foot_bed")
def _bunk_foot_bed():
    c = _C(BODY[1])
    c.r(1, 0, 15, 14, ALLOY[2])
    c.v(1, 0, 14, ALLOY[3]).v(14, 0, 14, ALLOY[1])
    c.h(1, 15, 9, STEEL[2]).h(1, 15, 10, ALLOY[3]).h(1, 15, 11, ALLOY[1])
    for y in (3, 6):
        c.h(2, 14, y, ALLOY[1]).h(2, 14, y + 1, ALLOY[3])
    c.h(1, 15, 13, ALLOY[0])
    c.h(0, 16, 14, BODY[3]).h(0, 16, 15, BODY[1]).p(0, 14, BODY[4])
    c.v(0, 0, 14, STEEL[2]).v(15, 0, 14, STEEL[1])
    return c.i


@_tex("crew_bunk_pillow")
def _bunk_pillow():
    c = _C(SHEET[3])
    c.h(0, 16, 6, SHEET[4]).h(0, 16, 7, SHEET[1])
    return c.i


def _bunk_models(ctx):
    SH, SF, EH, EF, IN, RF = ("crew_bunk_side_head", "crew_bunk_side_foot", "crew_bunk_end_head",
                              "crew_bunk_end_foot", "crew_bunk_head_in", "crew_bunk_roof")
    HB, FB = "crew_bunk_head_bed", "crew_bunk_foot_bed"
    h = _M(ctx, EH)
    h.box([0, 0, 0], [16, 14, 2], {"north": EH, "south": IN, "west": SH, "east": SH + "~", "down": RF})
    h.box([0, 14, 0], [16, 15, 8], {"up": RF, "down": IN, "north": EH, "south": (RF, [0, 7, 16, 8]),
                                    "west": SH, "east": SH + "~"})
    h.box([0, 5, 2], [1, 14, 8], {"west": SH, "east": IN, "south": (SH, [7, 2, 8, 11])})
    h.box([15, 5, 2], [16, 14, 8], {"east": SH + "~", "west": IN, "south": (SH, [7, 2, 8, 11])})
    h.box([0, 2, 2], [16, 5, 16], {"west": SH, "east": SH + "~", "down": RF})
    h.box([0, 5, 8], [1, 7, 16], {"west": SH, "east": SH + "~", "up": HB})
    h.box([15, 5, 8], [16, 7, 16], {"east": SH + "~", "west": SH, "up": HB})
    h.box([1, 5, 2], [15, 8, 11], {"up": HB, "west": SH, "east": SH + "~"})
    h.box([1, 5, 11], [15, 9, 16], {"up": HB, "west": SH, "east": SH + "~", "north": (HB, [1, 11, 15, 12])})
    P = "crew_bunk_pillow"
    h.box([3, 8, 3], [13, 10, 8], {"up": HB, "south": (P, [0, 6, 10, 8]), "west": (P, [0, 6, 5, 8]),
                                   "east": (P, [0, 6, 5, 8]), "north": (P, [0, 6, 10, 8])})
    f = _M(ctx, EF)
    f.box([0, 0, 14], [16, 10, 16], {"south": EF, "north": EF, "west": SF, "east": SF + "~", "up": FB, "down": RF})
    f.box([0, 2, 0], [16, 5, 14], {"west": SF, "east": SF + "~", "down": RF})
    f.box([0, 5, 0], [1, 7, 14], {"west": SF, "east": SF + "~", "up": FB})
    f.box([15, 5, 0], [16, 7, 14], {"east": SF + "~", "west": SF, "up": FB})
    f.box([1, 5, 0], [15, 9, 14], {"up": FB, "west": SF, "east": SF + "~"})
    return {"crew_bunk_head": h.json(), "crew_bunk_foot": f.json()}


def _bunk_state(ctx):
    v = {}
    for f, y in _ROT.items():
        for part in ("head", "foot"):
            v["facing=%s,part=%s" % (f, part)] = _variant(ctx, "crew_bunk_" + part, y)
    return {"variants": v}


BLOCKS.append(Block(
    "crew_bunk", shape="custom", name="Crew Bunk",
    textures={k: k for k in ["crew_bunk_side_head", "crew_bunk_side_foot", "crew_bunk_end_head",
                             "crew_bunk_end_foot", "crew_bunk_head_in", "crew_bunk_roof",
                             "crew_bunk_head_bed", "crew_bunk_foot_bed", "crew_bunk_pillow"]},
    model=_bunk_models, blockstate=_bunk_state, item_model="crew_bunk_head",
    loot=half_loot("crew_bunk", "part", "head")))


# =================================================================================================
# locker -- vertical cap/mid/cap, twin doors. Stacked lockers read as one tall pair of doors: vents
# and name plates on the top piece, handles and kick vents on the bottom piece.
# =================================================================================================
_LEAVES = ((1, 8), (8, 15))


def _locker_front(p, n):
    c = _C(BODY[1])
    c.v(0, 0, 16, BODY[3]).v(15, 0, 16, BODY[0])
    bot = 14 if n else 16
    for i, (x0, x1) in enumerate(_LEAVES):
        c.r(x0, 0, x1, bot, STATION[2])
        c.v(x0, 0, bot, STATION[3]).v(x1 - 1, 0, bot, STATION[0])
        if p:
            c.h(x0, x1, 0, STATION[3]).p(x0, 0, STATION[4]).p(x1 - 1, 0, STATION[2])
            for y in (2, 4, 6):
                c.h(x0 + 2, x1 - 2, y, STATION[0]).h(x0 + 2, x1 - 2, y + 1, STATION[3])
            c.h(x0 + 2, x0 + 5, 9, STEEL[3]).p(x0 + 4, 9, STEEL[1])
            c.v(x0 + 1, 1, 3, ALLOY[3] if i == 0 else MARS[3])          # colour tag
        if n:
            c.h(x0, x1, bot - 1, STATION[0]).p(x0, bot - 1, STATION[1])
            c.h(x0 + 2, x1 - 2, bot - 3, STATION[0]).h(x0 + 2, x1 - 2, bot - 2, STATION[3])
        hy = 9 if (p and n) else (1 if n else None)
        if hy is not None:
            hx = x1 - 3 if i == 0 else x0 + 2
            c.p(hx, hy, STEEL[4]).p(hx, hy + 1, STEEL[2]).p(hx, hy + 2, STEEL[1]).p(hx + (1 if i == 0 else -1), hy + 1, STATION[1])
        if not p and not n:
            c.p(x0 + 3, 8, STATION[1]).p(x0 + 3, 7, STATION[3])
    if n:
        c.h(0, 16, 14, BODY[2]).h(0, 16, 15, BODY[0])
        c.ps([(4, 14), (7, 14), (10, 14)], BODY[0])
    return c.w("locker_front%d%d" % (p, n), STATION, 0.05)


def _locker_side(p, n):
    c = _C(BODY[1])
    bot = 14 if n else 16
    c.r(1, 0, 16, bot, BODY[2])
    c.v(0, 0, bot, STATION[3]).v(1, 0, bot, BODY[3]).v(15, 0, bot, BODY[0])
    c.v(8, 0, bot, BODY[1]).v(9, 0, bot, BODY[3])
    if p:
        c.h(1, 16, 0, BODY[3]).p(1, 0, BODY[4]).ps([(3, 2), (13, 2)], STEEL[1])
    if n:
        c.h(1, 16, bot - 1, BODY[0]).ps([(3, bot - 3), (13, bot - 3)], STEEL[1])
        c.h(2, 15, 14, BODY[1]).h(2, 15, 15, BODY[0]).r(0, 14, 2, 16, BODY[0]).p(15, 14, BODY[0]).p(15, 15, BODY[0])
    return c.w("locker_side%d%d" % (p, n), BODY, 0.03)


@_tex("locker_top")
def _locker_top():
    c = _C(BODY[2])
    c.b(0, 1, 16, 16, BODY, 2)
    c.h(1, 15, 0, STATION[3]).p(0, 0, BODY[3]).p(15, 0, BODY[0])
    for x in (4, 7, 10):
        c.v(x, 5, 12, BODY[0]).v(x + 1, 5, 12, BODY[3])
    return c.w("locker_top", BODY, 0.05)


def _locker_inner(p, n):
    """Back wall as seen through the open doors: left bay a hanging pressure suit, right bay a
    helmet shelf above and boots below."""
    c = _C(BODY[1])
    c.v(1, 0, 16, BODY[0]).v(14, 0, 16, BODY[2])
    suit = MARS
    if p:
        c.h(0, 16, 0, BODY[0])
        c.h(1, 7, 1, STEEL[1])                                  # hanger rail
        c.p(4, 2, STEEL[2])
        c.h(2, 7, 3, suit[3]).r(2, 4, 7, 16 if not n else 9, suit[2])
        c.v(2, 4, 16 if not n else 9, suit[3]).v(6, 4, 16 if not n else 9, suit[1]).p(4, 4, suit[1]).p(4, 5, STEEL[2])
        c.r(10, 1, 13, 3, CER[3]).p(10, 1, CER[4]).h(11, 13, 2, PLASMA[1]).p(12, 1, CER[2])   # helmet
        c.h(9, 15, 3, BODY[3])
    if not p:
        top = 0
        c.r(2, top, 7, 6 if n else 16, suit[2])
        c.v(2, top, 6 if n else 16, suit[3]).v(6, top, 6 if n else 16, suit[1])
        c.v(4, 3, 6 if n else 16, suit[0])
    if p and n:
        c.v(4, 9, 13, suit[0]).r(2, 9, 4, 13, suit[2]).r(5, 9, 7, 13, suit[2]).v(2, 9, 13, suit[3]).v(6, 9, 13, suit[1])
    if n:
        fy = 13 if p else 6
        c.r(2, fy, 4, min(fy + 1, 14), BODY[3]).r(5, fy, 7, min(fy + 1, 14), BODY[3])
        c.r(10, 11, 12, 14, BODY[3]).r(12, 12, 14, 14, BODY[3]).p(10, 11, BODY[4]).h(10, 14, 13, BODY[2])  # boots
        c.h(0, 16, 14, BODY[0]).h(0, 16, 15, BODY[0])
    if not p:
        c.p(11, 4, STEEL[1]).p(11, 5, ALLOY[2]).p(11, 6, ALLOY[1]).p(12, 5, ALLOY[2])            # hook + towel
    return c.i


@_tex("locker_door_in")
def _locker_door_in():
    c = _C(STATION[1])
    c.v(0, 0, 16, STATION[2]).v(6, 0, 16, STATION[0])
    c.r(2, 3, 5, 7, STEEL[2]).p(2, 3, STEEL[4]).p(3, 4, STEEL[3]).h(2, 5, 7, STEEL[1])     # mirror
    c.r(2, 10, 5, 13, ALLOY[1]).p(2, 10, ALLOY[3]).p(3, 11, ALLOY[2])                      # pinned card
    return c.i


for _p, _n in PN:
    TEXTURES["locker_front_c%d%d" % (_p, _n)] = (lambda p=_p, n=_n: _locker_front(p, n))
    TEXTURES["locker_side_c%d%d" % (_p, _n)] = (lambda p=_p, n=_n: _locker_side(p, n))
    TEXTURES["locker_inner_c%d%d" % (_p, _n)] = (lambda p=_p, n=_n: _locker_inner(p, n))


def _locker_models(ctx):
    out = {}
    T, DI = "locker_top", "locker_door_in"
    for p, n in PN:
        F, S, I = ("locker_front_c%d%d" % (p, n), "locker_side_c%d%d" % (p, n), "locker_inner_c%d%d" % (p, n))
        y0 = 2 if n else 0
        for is_open in (False, True):
            m = _M(ctx, S)
            if n:
                m.box([1, 0, 2], [15, 2, 15], {"north": F, "south": S, "west": S, "east": S + "~"})
            if not is_open:
                faces = {"north": F, "south": S, "west": S, "east": S + "~"}
                if p:
                    faces["up"] = T
                if n:
                    faces["down"] = T
                m.box([0, y0, 1], [16, 16, 16], faces)
                for x0, x1 in _LEAVES:
                    d = {"north": F}
                    d["west" if x0 == 1 else "east"] = S if x0 == 1 else S + "~"
                    if p:
                        d["up"] = T
                    if n:
                        d["down"] = T
                    m.box([x0, y0, 0], [x1, 16, 1], d)
            else:
                for x0, tex in ((0, S), (15, S + "~")):
                    w = {"west" if x0 == 0 else "east": tex, "east" if x0 == 0 else "west": I,
                         "north": F, "south": S}
                    if p:
                        w["up"] = T
                    m.box([x0, y0, 1], [x0 + 1, 16, 16], w)
                b = {"north": I, "south": S}
                if p:
                    b["up"] = T
                m.box([1, y0, 15], [15, 16, 16], b)
                m.box([7, y0, 1], [9, 16, 15], {"north": (F, [0, 16 - 16, 2, 16 - y0]), "west": I, "east": I})
                if p:
                    m.box([1, 15, 1], [7, 16, 15], {"up": T, "down": I, "north": F})
                    m.box([9, 15, 1], [15, 16, 15], {"up": T, "down": I, "north": F})
                    m.box([1, 12, 2], [7, 13, 15], {"up": T, "down": T, "north": (S, [1, 0, 7, 1])})
                if n:
                    m.box([1, y0, 1], [7, y0 + 1, 15], {"up": T, "north": F})
                    m.box([9, y0, 1], [15, y0 + 1, 15], {"up": T, "north": F})
                hh = 16 - y0
                m.box([1, y0, -6], [2, 16, 1], {"west": (F, [8, 0, 15, hh]), "east": (DI, [0, 0, 7, hh]),
                                                "north": (S, [0, 0, 1, hh])})
                m.box([14, y0, -6], [15, 16, 1], {"east": (F, [1, 0, 8, hh]), "west": (DI, [7, 0, 0, hh]),
                                                  "north": (S, [0, 0, 1, hh])})
            out["locker_c%d%d%s" % (p, n, "_open" if is_open else "")] = m.json()
    return out


def _locker_state(ctx):
    v = {}
    for f, y in _ROT.items():
        for o in (False, True):
            for p, n in PN:
                v["facing=%s,neg_link=%s,open=%s,pos_link=%s" % (f, _tf(not n), _tf(o), _tf(not p))] = \
                    _variant(ctx, "locker_c%d%d%s" % (p, n, "_open" if o else ""), y)
    return {"variants": v}


BLOCKS.append(Block(
    "locker", shape="custom", connects="strip", name="Locker",
    textures={k: k for k in ["locker_top", "locker_door_in"]
              + ["locker_%s_c%d%d" % ((s,) + pn) for s in ("front", "side", "inner") for pn in PN]},
    model=_locker_models, blockstate=_locker_state, item_model="locker_c11"))


# =================================================================================================
# planter -- horizontal trough run + four plant looks on flat cutout planes (two along the run, two
# across), so a row of planters reads as one hedge.
# =================================================================================================
def _planter_side(p, n):
    c = _C(BODY[0])
    c.h(0, 16, 8, STEEL[3]).h(0, 16, 9, STEEL[1])
    c.r(0, 10, 16, 16, BODY[2])
    c.h(0, 16, 10, BODY[3]).h(0, 16, 15, BODY[0])
    c.h(0, 16, 12, ALLOY[1])
    c.r(6, 13, 10, 15, GLAC[1]).h(6, 10, 13, GLAC[2]).p(6, 13, GLAC[3]).p(9, 14, GLAC[0])
    c.v(15, 10, 16, BODY[0])
    if n:
        c.v(0, 8, 16, BODY[3]).p(0, 8, STEEL[4])
    if p:
        c.v(15, 8, 16, BODY[0]).p(15, 8, STEEL[1])
    return c.w("planter_side%d%d" % (p, n), BODY, 0.05)


for _p, _n in PN:
    TEXTURES["planter_side_c%d%d" % (_p, _n)] = (lambda p=_p, n=_n: _planter_side(p, n))


@_tex("planter_end")
def _planter_end():
    c = _C(BODY[0])
    c.h(2, 14, 8, STEEL[3]).h(2, 14, 9, STEEL[1]).p(2, 8, STEEL[4])
    c.b(2, 10, 14, 16, BODY, 2)
    c.h(3, 13, 12, ALLOY[1])
    c.p(7, 13, STEEL[3]).p(8, 13, STEEL[1]).p(7, 14, STEEL[1]).p(8, 14, STEEL[0])
    return c.w("planter_end", BODY, 0.05)


@_tex("planter_rim")
def _planter_rim():
    c = _C(STEEL[2])
    c.h(0, 16, 2, STEEL[3]).h(0, 16, 13, STEEL[1])
    c.v(0, 3, 13, STEEL[3]).v(15, 3, 13, STEEL[1])
    return c.i


@_tex("planter_soil")
def _planter_soil():
    c = _C(SOIL[1])
    for y in range(3, 13):
        for x in range(16):
            d = det("soil", x // 2, y // 2)
            if d < 0.22:
                c.p(x, y, SOIL[2])
            elif d > 0.86:
                c.p(x, y, SOIL[0])
    c.h(0, 16, 3, SOIL[0])
    for x in range(0, 16, 4):
        c.p(x, 8, STATION[2]).p(x + 1, 8, STATION[1])            # drip line
    c.ps([(5, 5), (12, 10)], STEEL[1]).ps([(2, 10), (9, 5)], SOIL[3])
    return c.i


def _blade(c, x, top, lean, r, base=8):
    """One leaf blade from the soil row up to `top`, leaning by `lean` px over its height."""
    n = base - top
    for i in range(n + 1):
        y = base - i
        xx = x + int(round(lean * (i / max(1, n)) ** 2))
        if 0 <= y < 16:
            c.p(xx % 16, y, r[1] if i == 0 else r[2] if i < n * 0.6 else r[3] if i < n else r[4])


def _plant(kind, v):
    c = _C()
    if kind == 0:      # fern: arching fronds
        for i, x in enumerate(range(0 + v * 2, 16, 3)):
            d = det("fern", v, i)
            _blade(c, x, 1 + int(d * 4), (-3, -1, 2, 3, 1)[i % 5], GREEN)
            c.p((x + 1) % 16, 7, GREEN[1]).p((x - 1) % 16, 6 - int(d * 2), GREEN[2])
    elif kind == 1:    # violet bloom on thin stems
        for i, x in enumerate(range(1 + v * 2, 16, 4)):
            top = 2 + int(det("bloom", v, i) * 3)
            _blade(c, x, top + 1, 0, GREEN)
            c.p((x + 1) % 16, 6, GREEN[2]).p((x - 1) % 16, 7, GREEN[1])
            c.p(x, top, NYX[3]).p((x + 1) % 16, top, NYX[2]).p((x - 1) % 16, top, NYX[3])
            c.p(x, top - 1, NYX[4]).p(x, top + 1, NYX[1])
    elif kind == 2:    # ember succulent: low rosettes
        for i, x in enumerate(range(2 + v, 16, 5)):
            c.r(x - 1, 6, x + 2, 9, MARS[2]).p(x, 5, CIND[3]).p(x - 1, 5, MARS[3]).p(x + 1, 5, MARS[2])
            c.p((x - 2) % 16, 7, MARS[3]).p((x + 2) % 16, 7, MARS[1]).p(x, 4, CIND[4]).p(x, 6, MARS[3])
            c.p(x - 1, 8, MARS[1]).p(x + 1, 8, MARS[0]).p(x, 7, CIND[3])
            if det("emb", v, i) > 0.4:
                c.p((x + 2) % 16, 5, CIND[3]).p((x + 2) % 16, 6, MARS[2])
    else:              # frost reed: tall straight stalks with seed heads
        for i, x in enumerate(range(0 + v, 16, 2)):
            top = int(det("reed", v, i) * 5)
            for y in range(top, 9):
                c.p(x, y, GLAC[1] if y > 6 else GLAC[2] if y > top + 1 else GLAC[3])
            if i % 2 == v % 2:
                c.p(x, top, GLAC[4]).p((x + 1) % 16, top + 1, GLAC[3])
    return c.i


for _k in range(4):
    for _v, _s in enumerate("ab"):
        TEXTURES["planter_plant%d_%s" % (_k, _s)] = (lambda k=_k, v=_v: _plant(k, v))


def _trough(m, p, n):
    S, E, R = "planter_side_c%d%d" % (p, n), "planter_end", "planter_rim"
    x0, x1 = (1 if n else 0), (15 if p else 16)
    m.box([x0, 0, 3], [x1, 7, 13], {"up": "planter_soil", "down": R})
    for z in (2, 13):
        m.box([0, 0, z], [16, 8, z + 1], _ends({"north": S, "south": S + "~", "up": R, "down": R}, p, n, E))
    if n:
        m.box([0, 0, 3], [1, 8, 13], {"west": E, "east": E, "up": R, "down": R})
    if p:
        m.box([15, 0, 3], [16, 8, 13], {"east": E, "west": E, "up": R, "down": R})


def _plants(m, k):
    a, b = "planter_plant%d_a" % k, "planter_plant%d_b" % k
    m.box([0, 7, 6], [16, 16, 6], {"north": a, "south": a})
    m.box([0, 7, 10], [16, 16, 10], {"north": b, "south": b})
    m.box([4, 7, 3], [4, 16, 13], {"west": b, "east": b})
    m.box([12, 7, 3], [12, 16, 13], {"west": a, "east": a})


def _planter_models(ctx):
    out = {}
    inv = _M(ctx, "planter_end", CUTOUT)
    _trough(inv, 1, 1)
    _plants(inv, 0)
    out["planter_inventory"] = inv.json()
    for p, n in PN:
        m = _M(ctx, "planter_end", CUTOUT)
        _trough(m, p, n)
        out["planter_c%d%d" % (p, n)] = m.json()
    for k in range(4):
        m = _M(ctx, "planter_plant%d_a" % k, CUTOUT)
        _plants(m, k)
        out["planter_plant%d" % k] = m.json()
    return out


def _planter_state(ctx):
    parts = []
    for axis, y in (("x", 0), ("z", 90)):
        for p, n in PN:
            parts.append({"when": {"axis": axis, "pos_link": _tf(not p), "neg_link": _tf(not n)},
                          "apply": _variant(ctx, "planter_c%d%d" % (p, n), y)})
        for k in range(4):
            parts.append({"when": {"axis": axis, "plant": str(k)},
                          "apply": _variant(ctx, "planter_plant%d" % k, y)})
    return {"multipart": parts}


BLOCKS.append(Block(
    "planter", shape="custom", connects="strip", name="Planter", render_type=CUTOUT,
    textures={k: k for k in ["planter_end", "planter_rim", "planter_soil"]
              + ["planter_side_c%d%d" % pn for pn in PN]
              + ["planter_plant%d_%s" % (k, s) for k in range(4) for s in "ab"]},
    model=_planter_models, blockstate=_planter_state, item_model="planter_inventory"))

# =========================================================================================
# Recipes (0.8.0 Stage 6): vanilla + Neroland Core ingredients only, no gate. See _kit.shaped.
# =========================================================================================
from ._kit import attach_recipes, shaped  # noqa: E402

_I, _Q = "minecraft:iron_ingot", "minecraft:quartz"
attach_recipes(BLOCKS, {
    "lab_bench": shaped("lab_bench", 1, ["QQQ", "I I", "I I"], I=_I, Q=_Q),
    "fume_hood": shaped("fume_hood", 1, ["PPP", "P P", "III"], I=_I, P="minecraft:glass_pane"),
    "specimen_tank": shaped("specimen_tank", 1, ["GGG", "GPG", "III"], G="minecraft:glass", I=_I,
                            P="minecraft:prismarine_crystals"),
    "sample_fridge": shaped("sample_fridge", 1, ["III", "IPI", "IRI"], I=_I, P="minecraft:packed_ice",
                            R="minecraft:redstone"),
    "lab_stool": shaped("lab_stool", 2, ["sss", " I "], I=_I, s="minecraft:smooth_stone_slab"),
    "crew_bunk": shaped("crew_bunk", 1, ["I", "B", "I"], B="#minecraft:beds", I=_I),
    "locker": shaped("locker", 2, ["III", "ICI", "III"], C="minecraft:chest", I=_I),
    "planter": shaped("planter", 2, ["I I", "IDI", "III"], D="minecraft:dirt", I=_I),
})
