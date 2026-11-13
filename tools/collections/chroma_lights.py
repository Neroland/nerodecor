"""
Chroma Lights collection -- twelve hub-coloured light fittings.

House rules for this collection
  * Housings are the near-black navy `body` ramp with `starsteel` caps and are NEVER tinted: only the
    emissive layer (and nothing else) carries tintindex 0, so the hub colour lands on a near-white
    diffuser and the metal stays metal. Every block is shape="custom" for that reason (the kit's
    cube/strip emitters tint the base faces too); `connects` still states the connection family.
  * Glow is always a second element with the SAME box as its base element (identical quads, never
    a partial overlay), shade false, light_emission 15, cutout, cleared under baked caps/bezels.
  * Connection art is baked into texture variants (caps, bezels, seam rings); geometry that appears
    or disappears with a neighbour is a multipart part, never an overlapping quad.

Blockstate properties each block expects from Java (all booleans are "true"/"false")
  light_strip      face, axis, pos_link, neg_link                 (kit strip: 2x1 bar on a face)
  edge_trim        facing (wall it hugs), half (bottom|top), shape (straight|inner|outer),
                   pos_link, neg_link (straight only: same trim continues at +axis / -axis end).
                   inner/outer = the corner between `facing` and the wall counter-clockwise of it
                   (north -> west -> south -> east), for both halves.
  floor_tile       north, east, south, west, up, down  (true = same tile, same colour next door)
  floor_tile_slab  type (bottom|top|double), north, east, south, west
  step_light       facing (the way the lit nosing looks)
  ceiling_coffer   north, east, south, west
  light_pillar     axis, pos_link, neg_link
  neon_tube        face, axis, shape (straight|bend), pos_link, neg_link.
                   straight: as light_strip. bend: one leg leaves along `axis` toward + when
                   pos_link else -, the other leaves along the cross axis (the other in-plane
                   axis of `face`) toward + when neg_link else -.
  light_pane       north, east, south, west (pane arms), up, down (same pane above / below)
  sconce_bar       facing (the way it looks; it hangs on the block behind), vertical
  halo_ring        (none)
  cage_lamp        face (the side it is mounted on)
"""
import math

from ._kit import (FACE_EDGES, FACES, Art, Block, _apply, _AXES, _band, _clear, _DIRS, _rot,
                   _variant, animate, bevel, bezel_layer, cap_layer, expand_mask16, expand_strip,
                   lerp, new, put, ramp, ramp5, rect, strip_json, transpose, wear)

COLLECTION = "chroma_lights"

HOUSING = ramp("body")              # house black+blue metal
TRIM = ramp("starsteel")            # cap metal
STATION = ramp("station")           # mid steel for seam rails
DIFFUSER = ramp5((222, 232, 242))   # unlit diffuser under the glow (cool, never pure grey)
# Emissive ramp: near-white and almost neutral so a runtime tint multiplies cleanly.
GLOW = [(190, 204, 222), (208, 220, 234), (224, 234, 244), (238, 245, 250), (250, 252, 254)]
CUTOUT = "minecraft:cutout"

TEXTURES = {}


# --- local helpers (the kit has no untinted-housing emitters; see the report) -----------------
def breath(t):
    return 0.5 + 0.5 * math.cos(2 * math.pi * t)


def glo(k, hot=False):
    """Glow colour at breathing level k: hot = the bright core rows."""
    return lerp(GLOW[2], GLOW[4], k) if hot else lerp(GLOW[0], GLOW[3], k)


def tex(tid):
    def deco(fn):
        TEXTURES[tid] = fn
        return fn
    return deco


def family(prefix, suffixes, make):
    """Register TEXTURES[prefix + suffix] for a lazily built {suffix: Art|Image} set."""
    cache = {}

    def get(s):
        if not cache:
            cache.update(make())
        return cache[s]
    for s in suffixes:
        TEXTURES[prefix + s] = (lambda s=s: get(s))
    return [prefix + s for s in suffixes]


def T(name, uv=None, cull=None):
    d = {"texture": "#" + name}
    if uv:
        d["uv"] = list(uv)
    if cull:
        d["cullface"] = cull
    return d


def solid(c, frm, to, faces):
    """Housing / unlit element: never tinted."""
    return c.el(frm, to, faces, cull=False, tint=False)


def lit(c, frm, to, faces):
    """Glow layer: same box as its base element, chroma tinted, shade false, emission 15."""
    return c.el(frm, to, faces, cull=False, emit=15)


def mdl(c, textures, els):
    return {"textures": {k: c.tex(v) for k, v in textures.items()}, "elements": els,
            "render_type": CUTOUT}


def texmap(*tids):
    out = {}
    for t in tids:
        for x in (t if isinstance(t, (list, tuple)) else [t]):
            out[x] = x
    return out


def face_rotation(face):
    """(x, y) blockstate rotation taking a model mounted on DOWN to `face`."""
    for rx in (0, 90, 180, 270):
        for ry in (0, 90, 180, 270):
            if _rot(_DIRS["down"], rx, ry) == _DIRS[face]:
                return rx, ry
    raise ValueError(face)


def var(c, model, rx=0, ry=0):
    v = {"model": c.model(model)}
    if rx % 360:
        v["x"] = rx % 360
    if ry % 360:
        v["y"] = ry % 360
    return v


def b2s(b):
    return "true" if b else "false"


STRIP_SUF = [s % (g, p, n, t) for g in ("", "_glow") for p in (0, 1) for n in (0, 1)
             for t in ("", "_t") for s in ("%s_c%d%d%s",)]


def untint_base(models):
    for m in models.values():
        for e in m["elements"]:
            if not e.get("light_emission"):
                for f in e["faces"].values():
                    f.pop("tintindex", None)
    return models


# =========================================================================================
# light_strip -- 2x1 bar on any face (kit strip emitter, housing untinted)
# =========================================================================================
def light_strip():
    """Bar top samples rows 7..8, its sides rows 6 and 9, its ends the outer columns of 7..8."""
    img = new(16, 16, HOUSING[1])
    bevel(img, 0, 0, 16, 6, HOUSING, fill=1)
    bevel(img, 0, 10, 16, 16, HOUSING, fill=1)
    for x in range(16):
        put(img, x, 6, HOUSING[3])   # lit side (key light from the top-left)
        put(img, x, 9, HOUSING[0])   # occluded side
    rect(img, 0, 7, 16, 8, DIFFUSER[2])
    rect(img, 0, 8, 16, 9, DIFFUSER[1])
    return wear(img, "light_strip", HOUSING, rate=0.04)


def light_strip_cap():
    img = cap_layer(TRIM)
    for x in range(16):
        put(img, x, 6, TRIM[3])
        put(img, x, 9, TRIM[0])
    return img


def light_strip_glow():
    def frame(t):
        k = breath(t)
        img = new()
        for x in range(16):
            put(img, x, 7, glo(k, True))
            put(img, x, 8, glo(k))
        return img
    return animate("light_strip_glow", "breathing", frame)


TEXTURES["light_strip"] = light_strip
LIGHT_STRIP_T = family("light_strip", STRIP_SUF,
                       lambda: expand_strip(Art.of(light_strip()), light_strip_cap(), light_strip_glow()))


def light_strip_model(c):
    return untint_base(strip_json(c, c.block)[0])


def light_strip_state(c):
    return strip_json(c, c.block)[1]


# =========================================================================================
# edge_trim -- L-profile bar in the crook of floor and wall; stairs-style corners
# =========================================================================================
# sheet rows: 0..2 floor flange top (0 under the wall flange, 1 light channel, 2 lip),
# 5..6 wall flange face, 8 floor flange front, 10 wall flange top. Strip runs along texture x.
def edge_trim():
    img = new(16, 16, HOUSING[1])
    for x in range(16):
        put(img, x, 0, HOUSING[0])
        put(img, x, 1, DIFFUSER[1])
        put(img, x, 2, HOUSING[3])
        put(img, x, 5, HOUSING[3])
        put(img, x, 6, HOUSING[1])
        put(img, x, 8, HOUSING[2])
        put(img, x, 10, HOUSING[2])
    for x in (3, 12):                      # fixing screws on the wall flange
        put(img, x, 6, HOUSING[0])
    return wear(img, "edge_trim", HOUSING, rate=0.03)


def edge_trim_cap():
    return cap_layer(TRIM)


def _trim_glow(pixels, w=16):
    def frame(t):
        img = new()
        for (x, y) in pixels:
            put(img, x, y, glo(breath(t), True))
        return img
    return animate("edge_trim_glow", "breathing", frame)   # one phase for the whole family


def edge_trim_glow():
    return _trim_glow([(x, 1) for x in range(16)])


def _trim_corner(inner):
    """Plan view of a corner (position-mapped up faces). Contour distance d: 0 wall flange,
    1 light channel, 2 lip. inner: d = min(x, y) (two runs meeting); outer: d = max(x, y)."""
    def d(x, y):
        return min(x, y) if inner else max(x, y)
    img = new(16, 16, HOUSING[1])
    glow = []
    for y in range(16):
        for x in range(16):
            k = d(x, y)
            if k == 0:
                put(img, x, y, HOUSING[0])
            elif k == 1:
                put(img, x, y, DIFFUSER[1])
                glow.append((x, y))
            elif k == 2:
                put(img, x, y, HOUSING[3])
    return img, glow


TEXTURES["edge_trim"] = edge_trim
EDGE_SUF = [s for s in STRIP_SUF if not s.endswith("_t")]
EDGE_T = family("edge_trim", EDGE_SUF,
                lambda: expand_strip(Art.of(edge_trim()), edge_trim_cap(), edge_trim_glow(), width=2))
TEXTURES["edge_trim_inner"] = lambda: _trim_corner(True)[0]
TEXTURES["edge_trim_inner_glow"] = lambda: _trim_glow(_trim_corner(True)[1])
TEXTURES["edge_trim_outer"] = lambda: _trim_corner(False)[0]
TEXTURES["edge_trim_outer_glow"] = lambda: _trim_glow(
    [p for p in _trim_corner(False)[1] if p[0] < 3 and p[1] < 3])


def edge_trim_model(c):
    out = {}
    for p in (0, 1):
        for n in (0, 1):
            tx = {"particle": "edge_trim", "t": "edge_trim_c%d%d" % (p, n),
                  "g": "edge_trim_glow_c%d%d" % (p, n)}
            floor = ([0, 0, 0], [16, 1, 3])
            els = [
                solid(c, *floor, {"up": T("t", [0, 0, 16, 3]), "south": T("t", [0, 8, 16, 9]),
                                  "west": T("t", [0, 8, 1, 9], "west"), "east": T("t", [15, 8, 16, 9], "east"),
                                  "down": T("t", [0, 0, 16, 3], "down"), "north": T("t", [16, 8, 0, 9], "north")}),
                solid(c, [0, 1, 0], [16, 3, 1],
                      {"south": T("t", [0, 5, 16, 7]), "up": T("t", [0, 10, 16, 11]),
                       "west": T("t", [0, 5, 1, 7], "west"), "east": T("t", [15, 5, 16, 7], "east"),
                       "north": T("t", [16, 5, 0, 7], "north")}),
                lit(c, *floor, {"up": T("g", [0, 0, 16, 3])}),
            ]
            out["edge_trim_c%d%d" % (p, n)] = mdl(c, tx, els)
    # inner corner: runs along the north and the west wall, mitred in the plan texture
    tx = {"particle": "edge_trim", "t": "edge_trim_c00", "p": "edge_trim_inner", "g": "edge_trim_inner_glow"}
    fn, fw = ([0, 0, 0], [16, 1, 3]), ([0, 0, 3], [3, 1, 16])
    out["edge_trim_inner"] = mdl(c, tx, [
        solid(c, *fn, {"up": T("p", [0, 0, 16, 3]), "south": T("t", [0, 8, 16, 9]),
                       "east": T("t", [15, 8, 16, 9], "east"), "down": T("t", [0, 0, 16, 3], "down"),
                       "north": T("t", [16, 8, 0, 9], "north"), "west": T("t", [0, 8, 3, 9], "west")}),
        solid(c, *fw, {"up": T("p", [0, 3, 3, 16]), "east": T("t", [3, 8, 16, 9]),
                       "south": T("t", [0, 8, 3, 9], "south"), "down": T("p", [0, 3, 3, 16], "down"),
                       "west": T("t", [3, 8, 16, 9], "west")}),
        solid(c, [1, 1, 0], [16, 3, 1],
              {"south": T("t", [1, 5, 16, 7]), "up": T("t", [1, 10, 16, 11]),
               "east": T("t", [15, 5, 16, 7], "east"), "north": T("t", [16, 5, 1, 7], "north")}),
        solid(c, [0, 1, 0], [1, 3, 16],
              {"east": T("t", [0, 5, 16, 7]), "up": T("t", [4, 10, 5, 11]),
               "south": T("t", [4, 5, 5, 7], "south"), "west": T("t", [0, 5, 16, 7], "west"),
               "north": T("t", [4, 5, 5, 7], "north")}),
        lit(c, *fn, {"up": T("g", [0, 0, 16, 3])}),
        lit(c, *fw, {"up": T("g", [0, 3, 3, 16])}),
    ])
    # outer corner: the nub that carries the trim round an outside corner
    tx = {"particle": "edge_trim", "t": "edge_trim_c00", "p": "edge_trim_outer", "g": "edge_trim_outer_glow"}
    fl = ([0, 0, 0], [3, 1, 3])
    out["edge_trim_outer"] = mdl(c, tx, [
        solid(c, *fl, {"up": T("p", [0, 0, 3, 3]), "south": T("t", [4, 8, 7, 9]), "east": T("t", [4, 8, 7, 9]),
                       "down": T("p", [0, 0, 3, 3], "down"), "north": T("t", [4, 8, 7, 9], "north"),
                       "west": T("t", [4, 8, 7, 9], "west")}),
        solid(c, [0, 1, 0], [1, 3, 1],
              {"south": T("t", [4, 5, 5, 7]), "east": T("t", [4, 5, 5, 7]), "up": T("t", [4, 10, 5, 11]),
               "north": T("t", [4, 5, 5, 7], "north"), "west": T("t", [4, 5, 5, 7], "west")}),
        lit(c, *fl, {"up": T("g", [0, 0, 3, 3])}),
    ])
    return out


def edge_trim_state(c):
    ry0 = {"north": 0, "east": 90, "south": 180, "west": 270}
    out = {}
    for facing, ry in ry0.items():
        for half in ("bottom", "top"):
            rx = 180 if half == "top" else 0
            for shape in ("straight", "inner", "outer"):
                y = ry if half == "bottom" else ry + (180 if shape == "straight" else 90)
                run = _rot((1, 0, 0), rx, y % 360)
                flip = sum(run) < 0
                for pos in (False, True):
                    for neg in (False, True):
                        if shape == "straight":
                            cp, cn = int(not pos), int(not neg)
                            if flip:
                                cp, cn = cn, cp
                            model = "edge_trim_c%d%d" % (cp, cn)
                        else:
                            model = "edge_trim_" + shape
                        key = "facing=%s,half=%s,neg_link=%s,pos_link=%s,shape=%s" % (
                            facing, half, b2s(neg), b2s(pos), shape)
                        out[key] = var(c, model, rx, y)
    return {"variants": out}


# =========================================================================================
# floor_tile / floor_tile_slab -- navy deck tile; only the inset seam round a group glows
# =========================================================================================
TILE_VARS = 3


def _edge(x, y, m, w, h):
    """(distance to the nearest unconnected edge, that edge is top/left)."""
    best = (99, False)
    for bit, dist, lit_side in ((8, y, True), (2, x, True), (4, h - 1 - y, False), (1, w - 1 - x, False)):
        if m & bit and dist < best[0]:
            best = (dist, lit_side)
    return best


def _tile(m, v, w=16, h=16):
    img = new(w, h, HOUSING[1])
    for x in range(w):                      # hairline joints keep a merged floor reading as tiles
        put(img, x, 0, HOUSING[2])
        put(img, x, h - 1, HOUSING[0])
    for y in range(h):
        put(img, 0, y, HOUSING[2])
        put(img, w - 1, y, HOUSING[0])
    put(img, 0, 0, HOUSING[3])
    if h == 16:                             # four deck studs, lit top-left
        for (sx, sy) in ((4, 4), (11, 4), (4, 11), (11, 11)):
            put(img, sx, sy, HOUSING[3])
            put(img, sx + 1, sy, HOUSING[2])
            put(img, sx, sy + 1, HOUSING[0])
    wear(img, "floor_tile_v%d" % v, HOUSING, rate=0.05)
    for y in range(h):
        for x in range(w):
            d, hi = _edge(x, y, m, w, h)
            if d == 0:
                put(img, x, y, HOUSING[0])
            elif d == 1:
                put(img, x, y, STATION[2] if hi else STATION[1])
            elif d == 2:
                put(img, x, y, DIFFUSER[1])
            elif d == 3:
                put(img, x, y, HOUSING[0] if hi else HOUSING[2])
    return img


def _tile_glow(m, w=16, h=16, stack=False):
    def frame(t):
        k = breath(t)
        part = new(w, h)
        for y in range(h):
            for x in range(w):
                if _edge(x, y, m, w, h)[0] == 2:
                    put(part, x, y, glo(k, True))
        if not stack:
            return part
        img = new(16, 16)
        img.paste(part, (0, 0))
        img.paste(part, (0, 8))
        return img
    return animate("floor_tile_glow", "breathing", frame)   # shared phase: one seam, one pulse


def _slab_side(lr):
    half = _tile(12 | lr, 0, 16, 8)
    img = new(16, 16)
    img.paste(half, (0, 0))
    img.paste(half, (0, 8))
    return img


TILE_T = []
for _m in range(16):
    for _v in range(TILE_VARS):
        TEXTURES["floor_tile_f%d_v%d" % (_m, _v)] = (lambda m=_m, v=_v: _tile(m, v))
        TILE_T.append("floor_tile_f%d_v%d" % (_m, _v))
    if _m:
        TEXTURES["floor_tile_glow_f%d" % _m] = (lambda m=_m: _tile_glow(m))
        TILE_T.append("floor_tile_glow_f%d" % _m)
SLAB_T = []
for _lr in range(4):
    TEXTURES["floor_tile_slab_side_f%d" % _lr] = (lambda lr=_lr: _slab_side(lr))
    TEXTURES["floor_tile_slab_side_glow_f%d" % _lr] = (lambda lr=_lr: _tile_glow(12 | lr, 16, 8, True))
    SLAB_T += ["floor_tile_slab_side_f%d" % _lr, "floor_tile_slab_side_glow_f%d" % _lr]


def _tile_face(c, face, box, base, glow, uv=(0, 0, 16, 16), cull=True):
    tx = {"particle": "floor_tile_f15_v0", "t": base}
    cf = face if cull else None
    els = [solid(c, box[0], box[1], {face: T("t", uv, cf)})]
    if glow:
        tx["g"] = glow
        els.append(lit(c, box[0], box[1], {face: T("g", uv, cf)}))
    return mdl(c, tx, els)


def _when(face, m, extra=None):
    w = {d: ("false" if bit else "true") for d, bit in zip(FACE_EDGES[face], (m & 8, m & 4, m & 2, m & 1))}
    w.update(extra or {})
    return w


def _weighted(c, names):
    return [{"model": c.model(n)} for n in names] if len(names) > 1 else {"model": c.model(names[0])}


FULL = ([0, 0, 0], [16, 16, 16])


def _tile_all(c, name, box, uv_side, side_base, side_glow):
    tx = {"particle": "floor_tile_f15_v0", "t": "floor_tile_f15_v0", "g": "floor_tile_glow_f15",
          "s": side_base, "sg": side_glow}
    faces = {f: T("t" if f in ("up", "down") else "s", None if f in ("up", "down") else uv_side,
                  f if (f != "up" or box[1][1] == 16) else None) for f in FACES}
    gf = {f: T("g" if f in ("up", "down") else "sg", None if f in ("up", "down") else uv_side,
               f if (f != "up" or box[1][1] == 16) else None) for f in FACES}
    return mdl(c, tx, [solid(c, box[0], box[1], faces), lit(c, box[0], box[1], gf)])


def floor_tile_model(c):
    out = {}
    for face in FACES:
        for m in range(16):
            for v in range(TILE_VARS if face in ("up", "down") else 1):
                out["floor_tile_%s_f%d_v%d" % (face, m, v)] = _tile_face(
                    c, face, FULL, "floor_tile_f%d_v%d" % (m, v), "floor_tile_glow_f%d" % m if m else None)
    out["floor_tile_inventory"] = _tile_all(c, "floor_tile", FULL, None, "floor_tile_f15_v0",
                                            "floor_tile_glow_f15")
    return out


def floor_tile_state(c):
    parts = []
    for face in FACES:
        for m in range(16):
            names = ["floor_tile_%s_f%d_v%d" % (face, m, v)
                     for v in range(TILE_VARS if face in ("up", "down") else 1)]
            parts.append({"when": _when(face, m), "apply": _weighted(c, names)})
    return {"multipart": parts}


def floor_tile_slab_model(c):
    out = {}
    lo, hi = ([0, 0, 0], [16, 8, 16]), ([0, 8, 0], [16, 16, 16])
    for m in range(16):
        g = "floor_tile_glow_f%d" % m if m else None
        for v in range(TILE_VARS):
            b = "floor_tile_f%d_v%d" % (m, v)
            out["floor_tile_slab_up8_f%d_v%d" % (m, v)] = _tile_face(c, "up", lo, b, g, cull=False)
            out["floor_tile_slab_up16_f%d_v%d" % (m, v)] = _tile_face(c, "up", hi, b, g)
            out["floor_tile_slab_down0_f%d_v%d" % (m, v)] = _tile_face(c, "down", lo, b, g)
            out["floor_tile_slab_down8_f%d_v%d" % (m, v)] = _tile_face(c, "down", hi, b, g, cull=False)
    for face in FACES[:4]:
        for lr in range(4):
            s, sg = "floor_tile_slab_side_f%d" % lr, "floor_tile_slab_side_glow_f%d" % lr
            out["floor_tile_slab_%s_bottom_f%d" % (face, lr)] = _tile_face(c, face, lo, s, sg, (0, 8, 16, 16))
            out["floor_tile_slab_%s_top_f%d" % (face, lr)] = _tile_face(c, face, hi, s, sg, (0, 0, 16, 8))
            out["floor_tile_slab_%s_double_f%d" % (face, lr)] = _tile_face(
                c, face, FULL, "floor_tile_f%d_v0" % (12 | lr), "floor_tile_glow_f%d" % (12 | lr))
    out["floor_tile_slab_inventory"] = _tile_all(c, "floor_tile_slab", lo, [0, 8, 16, 16],
                                                 "floor_tile_slab_side_f3", "floor_tile_slab_side_glow_f3")
    return out


def floor_tile_slab_state(c):
    parts = []
    for m in range(16):
        for face, kinds in (("up", (("up8", "bottom"), ("up16", "top|double"))),
                            ("down", (("down0", "bottom|double"), ("down8", "top")))):
            for mname, types in kinds:
                names = ["floor_tile_slab_%s_f%d_v%d" % (mname, m, v) for v in range(TILE_VARS)]
                parts.append({"when": _when(face, m, {"type": types}), "apply": _weighted(c, names)})
    for face in FACES[:4]:
        _t, _b, left, right = FACE_EDGES[face]
        for lr in range(4):
            for typ in ("bottom", "top", "double"):
                parts.append({"when": {"type": typ, left: b2s(not lr & 2), right: b2s(not lr & 1)},
                              "apply": {"model": c.model("floor_tile_slab_%s_%s_f%d" % (face, typ, lr))}})
    return {"multipart": parts}


# =========================================================================================
# step_light -- stair-nosing bar on the leading edge of a tread
# =========================================================================================
# rows 0..2 top (0 = lit leading edge, 1 = grip ridges), 5..6 front (5 lit), 8..9 back / ends,
# 12..14 underside. One-pixel starsteel end caps are part of the fitting.
@tex("step_light")
def step_light():
    img = new(16, 16, HOUSING[1])
    for x in range(16):
        put(img, x, 0, DIFFUSER[1])
        put(img, x, 1, HOUSING[3] if x % 2 == 0 else HOUSING[0])
        put(img, x, 2, HOUSING[2])
        put(img, x, 5, DIFFUSER[1])
        put(img, x, 6, HOUSING[0])
        put(img, x, 8, HOUSING[2])
        put(img, x, 9, HOUSING[0])
    for y in (0, 1, 2, 5, 6):
        put(img, 0, y, TRIM[2] if y != 6 else TRIM[0])
        put(img, 15, y, TRIM[1] if y != 6 else TRIM[0])
    return wear(img, "step_light", HOUSING, rate=0.03)


@tex("step_light_glow")
def step_light_glow():
    def frame(t):
        k = breath(t)
        img = new()
        for x in range(1, 15):
            put(img, x, 0, glo(k, True))
            put(img, x, 5, glo(k))
        return img
    return animate("step_light_glow", "breathing", frame)


def step_light_model(c):
    box = ([0, 0, 0], [16, 2, 3])
    tx = {"particle": "step_light", "t": "step_light", "g": "step_light_glow"}
    return {"step_light": mdl(c, tx, [
        solid(c, *box, {"up": T("t", [0, 0, 16, 3]), "north": T("t", [16, 5, 0, 7], "north"),
                        "south": T("t", [0, 8, 16, 10]), "west": T("t", [0, 8, 3, 10], "west"),
                        "east": T("t", [0, 8, 3, 10], "east"), "down": T("t", [0, 12, 16, 15], "down")}),
        lit(c, *box, {"up": T("g", [0, 0, 16, 3]), "north": T("g", [16, 5, 0, 7], "north")}),
    ])}


def step_light_state(c):
    ry = {"north": 0, "east": 90, "south": 180, "west": 270}
    return {"variants": {"facing=%s" % f: var(c, "step_light", 0, y) for f, y in ry.items()}}


# =========================================================================================
# ceiling_coffer -- recessed 3px tray; rims drop away between neighbours (one long trough)
# =========================================================================================
@tex("ceiling_coffer")
def ceiling_coffer():
    """Diffuser sheet: seamless, so merged coffers read as one trough. Perforation dots."""
    img = new(16, 16, DIFFUSER[2])
    for y in range(1, 16, 4):
        for x in range(1, 16, 4):
            put(img, x, y, DIFFUSER[1])
    return img


@tex("ceiling_coffer_rim")
def ceiling_coffer_rim():
    """Plan of the rim (position-mapped) round a back plate; rows 0..2 double as the rim sides."""
    img = new(16, 16, HOUSING[1])
    bevel(img, 0, 0, 16, 16, HOUSING, fill=1)
    bevel(img, 3, 3, 13, 13, HOUSING, fill=1)
    for i in range(1, 15):
        put(img, i, 1, HOUSING[2])
        put(img, i, 14, HOUSING[2])
        put(img, 1, i, HOUSING[2])
        put(img, 14, i, HOUSING[2])
    for i in range(2, 14):
        put(img, i, 2, HOUSING[0])
        put(img, 2, i, HOUSING[0])
    return wear(img, "ceiling_coffer_rim", HOUSING, rate=0.03, region=(3, 3, 13, 13))


@tex("ceiling_coffer_glow")
def ceiling_coffer_glow():
    def frame(t):
        k = breath(t)
        img = new(16, 16, glo(k, True))
        for y in range(1, 16, 4):
            for x in range(1, 16, 4):
                put(img, x, y, glo(k))
        return img
    return animate("ceiling_coffer_glow", "breathing", frame)


COFFER_TX = {"particle": "ceiling_coffer_rim", "d": "ceiling_coffer", "g": "ceiling_coffer_glow",
             "r": "ceiling_coffer_rim"}


def _coffer_plate(c):
    box = ([0, 15, 0], [16, 16, 16])
    return [solid(c, *box, {"down": T("d"), "up": T("r", None, "up")}), lit(c, *box, {"down": T("g")})]


def _coffer_edge(c, side):
    """Rim along one side (the 12px between the corners)."""
    box = {"north": ([2, 13, 0], [14, 16, 2]), "south": ([2, 13, 14], [14, 16, 16]),
           "west": ([0, 13, 2], [2, 16, 14]), "east": ([14, 13, 2], [16, 16, 14])}[side]
    inner = {"north": "south", "south": "north", "west": "east", "east": "west"}[side]
    return solid(c, *box, {"down": T("r"), side: T("r", [2, 0, 14, 3], side), inner: T("r", [2, 0, 14, 3])})


def _coffer_corner(c, a, b):
    x = 0 if b == "west" else 14
    z = 0 if a == "north" else 14
    ia = "south" if a == "north" else "north"
    ib = "east" if b == "west" else "west"
    return solid(c, [x, 13, z], [x + 2, 16, z + 2],
                 {"down": T("r"), a: T("r", [0, 0, 2, 3], a), b: T("r", [0, 0, 2, 3], b),
                  ia: T("r", [0, 0, 2, 3]), ib: T("r", [0, 0, 2, 3])})


CORNERS = (("north", "west"), ("north", "east"), ("south", "west"), ("south", "east"))


def ceiling_coffer_model(c):
    out = {"ceiling_coffer": mdl(c, COFFER_TX, _coffer_plate(c)
                                 + [_coffer_edge(c, s) for s in FACES[:4]]
                                 + [_coffer_corner(c, a, b) for a, b in CORNERS]),
           "ceiling_coffer_plate": mdl(c, COFFER_TX, _coffer_plate(c))}
    for s in FACES[:4]:
        out["ceiling_coffer_edge_" + s] = mdl(c, COFFER_TX, [_coffer_edge(c, s)])
    for a, b in CORNERS:
        out["ceiling_coffer_corner_%s_%s" % (a, b)] = mdl(c, COFFER_TX, [_coffer_corner(c, a, b)])
    return out


def ceiling_coffer_state(c):
    parts = [{"apply": {"model": c.model("ceiling_coffer_plate")}}]
    for s in FACES[:4]:
        parts.append({"when": {s: "false"}, "apply": {"model": c.model("ceiling_coffer_edge_" + s)}})
    for a, b in CORNERS:
        parts.append({"when": {"OR": [{a: "false"}, {b: "false"}]},
                      "apply": {"model": c.model("ceiling_coffer_corner_%s_%s" % (a, b))}})
    return {"multipart": parts}


# =========================================================================================
# light_pillar -- 8px diffuser column along `axis`, collars baked at unlinked ends
# =========================================================================================
@tex("light_pillar")
def light_pillar():
    """Side elevation (position-mapped, cols 4..11): two rails and a six-pixel diffuser."""
    img = new(16, 16, HOUSING[1])
    for y in range(16):
        put(img, 4, y, HOUSING[3])
        put(img, 11, y, HOUSING[0])
        for x in range(5, 11):
            put(img, x, y, DIFFUSER[2] if x < 9 else DIFFUSER[1])
    return img


def light_pillar_cap():
    """Collar: three rows of starsteel, lit from the top-left."""
    img = new(16, 16, TRIM[2])
    for x in range(16):
        for y, i in ((0, 3), (1, 2), (2, 0), (13, 3), (14, 2), (15, 0)):
            put(img, x, y, TRIM[i])
    for y in (0, 1, 2, 13, 14, 15):
        put(img, 4, y, TRIM[4] if y in (0, 13) else TRIM[3])
        put(img, 11, y, TRIM[0] if y in (2, 15) else TRIM[1])
    return img


def light_pillar_glow():
    def frame(t):
        img = new()
        head = t * 16
        for y in range(16):
            d = (y - head) % 16            # a soft band climbing the column, period = one block
            k = max(0.0, 1.0 - d / 6.0)
            for x in range(5, 11):
                edge = x in (5, 10)
                put(img, x, y, lerp(GLOW[1], GLOW[3], k) if edge else lerp(GLOW[2], GLOW[4], k))
        return img
    return animate("light_pillar_glow", "flow", frame)


@tex("light_pillar_end")
def light_pillar_end():
    img = new(16, 16, HOUSING[1])
    bevel(img, 4, 4, 12, 12, TRIM, fill=2)
    bevel(img, 6, 6, 10, 10, HOUSING, fill=1)
    return img


def _pillar_variants():
    base, cap, glow = Art.of(light_pillar()), light_pillar_cap(), light_pillar_glow()
    out = {}
    for p in (0, 1):
        for n in (0, 1):
            band = _band(16, 16, p * 8 + n * 4, 3)   # +axis = texture top
            out["_c%d%d" % (p, n)] = _variant(base, lambda f, b=band: _apply(f, cap, b))
            out["_glow_c%d%d" % (p, n)] = _variant(glow, lambda f, b=band: _clear(f, b))
    return out


PILLAR_T = family("light_pillar", EDGE_SUF, _pillar_variants)


def light_pillar_model(c):
    out = {}
    box = ([4, 0, 4], [12, 16, 12])
    for p in (0, 1):
        for n in (0, 1):
            tx = {"particle": "light_pillar_c11", "t": "light_pillar_c%d%d" % (p, n),
                  "g": "light_pillar_glow_c%d%d" % (p, n), "e": "light_pillar_end"}
            faces = {f: T("t") for f in FACES[:4]}
            if p:
                faces["up"] = T("e", None, "up")
            if n:
                faces["down"] = T("e", None, "down")
            out["light_pillar_c%d%d" % (p, n)] = mdl(c, tx, [
                solid(c, *box, faces), lit(c, *box, {f: T("g") for f in FACES[:4]})])
    return out


def light_pillar_state(c):
    out = {}
    for axis, (rx, ry) in (("y", (0, 0)), ("z", (90, 0)), ("x", (90, 90))):
        flip = sum(_rot((0, 1, 0), rx, ry)) < 0
        for pos in (False, True):
            for neg in (False, True):
                cp, cn = int(not pos), int(not neg)
                if flip:
                    cp, cn = cn, cp
                out["axis=%s,neg_link=%s,pos_link=%s" % (axis, b2s(neg), b2s(pos))] = var(
                    c, "light_pillar_c%d%d" % (cp, cn), rx, ry)
    return {"variants": out}


# =========================================================================================
# neon_tube -- 2px tube on standoffs; straight (capped) or bend
# =========================================================================================
# rows 4..5 tube top, 7..8 tube side, 10..11 tube underside, 13 standoff. Strip along texture x.
def neon_tube():
    img = new(16, 16, HOUSING[1])
    for x in range(16):
        for y, i in ((4, 2), (5, 2), (7, 2), (8, 1), (10, 1), (11, 1)):
            put(img, x, y, DIFFUSER[i])
        put(img, x, 13, HOUSING[2] if x % 2 == 0 else HOUSING[0])
    put(img, 3, 13, HOUSING[3])
    put(img, 11, 13, HOUSING[3])
    return img


def neon_tube_cap():
    """Electrode ferrule: dark collar, bright ring, dark tip."""
    img = new(16, 16, TRIM[1])
    for y in range(16):
        for x, i in ((0, 0), (1, 3), (2, 1), (13, 1), (14, 3), (15, 0)):
            put(img, x, y, TRIM[i])
    return img


def neon_tube_glow():
    levels = (1.0, 0.82, 1.0, 0.55)       # flicker: 4 frames

    def frame(t):
        k = levels[int(round(t * 4)) % 4]
        img = new()
        for x in range(16):
            for y in (4, 5, 7):
                put(img, x, y, glo(k, True))
            for y in (8, 10, 11):
                put(img, x, y, glo(k))
        return img
    return animate("neon_tube_glow", "flicker", frame)


TEXTURES["neon_tube"] = neon_tube
NEON_T = family("neon_tube", STRIP_SUF,
                lambda: expand_strip(Art.of(neon_tube()), neon_tube_cap(), neon_tube_glow()))


def _neon_straight(c, p, n, canon):
    v = "c%d%d" % (p, n)
    tx = {"particle": "neon_tube", "t": "neon_tube_" + v, "g": "neon_tube_glow_" + v,
          "tt": "neon_tube_%s_t" % v, "gt": "neon_tube_glow_%s_t" % v}
    if canon == "":
        box = ([0, 1, 7], [16, 3, 9])
        uv = {"up": ("", [0, 4, 16, 6]), "south": ("", [0, 7, 16, 9]), "north": ("", [16, 7, 0, 9]),
              "down": ("", [0, 10, 16, 12])}
        ends = {"west": T("t", [0, 7, 2, 9], "west"), "east": T("t", [14, 7, 16, 9], "east")}
        posts = [([3, 0, 7], [5, 1, 9]), ([11, 0, 7], [13, 1, 9])]
    else:
        box = ([7, 1, 0], [9, 3, 16])
        uv = {"up": ("t", [4, 0, 6, 16]), "west": ("", [0, 7, 16, 9]), "east": ("", [16, 7, 0, 9]),
              "down": ("t", [10, 16, 12, 0])}
        ends = {"north": T("t", [0, 7, 2, 9], "north"), "south": T("t", [14, 7, 16, 9], "south")}
        posts = [([7, 0, 3], [9, 1, 5]), ([7, 0, 11], [9, 1, 13])]
    base = {f: T("t" + s, u) for f, (s, u) in uv.items()}
    base.update(ends)
    els = [solid(c, *box, base), lit(c, *box, {f: T("g" + s, u) for f, (s, u) in uv.items()})]
    for a, b in posts:
        els.append(solid(c, a, b, {f: T("t", [3, 13, 5, 14]) for f in FACES[:4]}
                         | {"down": T("t", [3, 13, 5, 14], "down")}))
    return mdl(c, tx, els)


def _neon_bend(c, sx, sz):
    tx = {"particle": "neon_tube", "t": "neon_tube_c00", "g": "neon_tube_glow_c00",
          "tt": "neon_tube_c00_t", "gt": "neon_tube_glow_c00_t"}
    x0, x1 = (0, 9) if sx < 0 else (7, 16)
    z0, z1 = (0, 7) if sz < 0 else (9, 16)
    bx = ([x0, 1, 7], [x1, 3, 9])
    bz = ([7, 1, z0], [9, 3, z1])
    fx = {"up": ("", [x0, 4, x1, 6]), "south": ("", [x0, 7, x1, 9]), "north": ("", [x1, 7, x0, 9]),
          "down": ("", [x0, 10, x1, 12]), ("east" if sx < 0 else "west"): ("", [7, 7, 9, 9])}
    fz = {"up": ("t", [4, z0, 6, z1]), "west": ("", [z0, 7, z1, 9]), "east": ("", [z1, 7, z0, 9]),
          "down": ("t", [10, z1, 12, z0])}
    els = []
    for box, fs in ((bx, fx), (bz, fz)):
        els.append(solid(c, *box, {f: T("t" + s, u) for f, (s, u) in fs.items()}))
        els.append(lit(c, *box, {f: T("g" + s, u) for f, (s, u) in fs.items()}))
    els.append(solid(c, [7, 0, 7], [9, 1, 9], {f: T("t", [3, 13, 5, 14]) for f in FACES[:4]}
                     | {"down": T("t", [3, 13, 5, 14], "down")}))
    return mdl(c, tx, els)


def _sgn(s):
    return "p" if s > 0 else "n"


def neon_tube_model(c):
    out = {}
    for canon in ("", "_z"):
        for p in (0, 1):
            for n in (0, 1):
                out["neon_tube_c%d%d%s" % (p, n, canon)] = _neon_straight(c, p, n, canon)
    for sx in (-1, 1):
        for sz in (-1, 1):
            out["neon_tube_bend_%s%s" % (_sgn(sx), _sgn(sz))] = _neon_bend(c, sx, sz)
    return out


def _bend_variant(c, face, axis, sa, sb):
    a = _AXES[axis]
    cross = [v for k, v in _AXES.items() if k != axis and not any(v[i] and _DIRS[face][i] for i in range(3))][0]
    want = {tuple(sa * k for k in a), tuple(sb * k for k in cross)}
    for rx in (0, 90, 180, 270):
        for ry in (0, 90, 180, 270):
            if _rot(_DIRS["down"], rx, ry) != _DIRS[face]:
                continue
            for sx in (-1, 1):
                for sz in (-1, 1):
                    if {_rot((sx, 0, 0), rx, ry), _rot((0, 0, sz), rx, ry)} == want:
                        return var(c, "neon_tube_bend_%s%s" % (_sgn(sx), _sgn(sz)), rx, ry)
    raise ValueError("no bend for %s %s" % (face, axis))


def neon_tube_state(c):
    from ._kit import strip_rotation
    out = {}
    for face in _DIRS:
        for axis in _AXES:
            if any(_DIRS[face][i] and _AXES[axis][i] for i in range(3)):
                continue
            canon, rx, ry, flip = strip_rotation(face, axis)
            for pos in (False, True):
                for neg in (False, True):
                    cp, cn = int(not pos), int(not neg)
                    if flip:
                        cp, cn = cn, cp
                    key = "axis=%s,face=%s,neg_link=%s,pos_link=%s,shape=" % (axis, face, b2s(neg), b2s(pos))
                    out[key + "straight"] = var(c, "neon_tube_c%d%d%s" % (cp, cn, canon), rx, ry)
                    out[key + "bend"] = _bend_variant(c, face, axis, 1 if pos else -1, 1 if neg else -1)
    return {"variants": out}


# =========================================================================================
# light_pane -- pane-style light wall; frame only round the outside of a group
# =========================================================================================
def light_pane():
    img = new(16, 16, DIFFUSER[2])
    for y in (5, 10):                      # light-guide ribs, continuous across joined panes
        for x in range(16):
            put(img, x, y, DIFFUSER[1])
    return img


def light_pane_frame():
    return bezel_layer(HOUSING)


def light_pane_glow():
    def frame(t):
        img = new()
        head = t * 16
        for y in range(16):
            d = (y - head) % 16            # a slow scanline, seamless down a stacked wall
            k = max(0.0, 1.0 - d / 5.0)
            for x in range(16):
                rib = y in (5, 10)
                put(img, x, y, lerp(GLOW[0], GLOW[2], k) if rib else lerp(GLOW[2], GLOW[4], k))
        return img
    return animate("light_pane_glow", "data", frame)


@tex("light_pane_edge")
def light_pane_edge():
    """Pane edge metal (2px strips are cut from the middle columns / rows)."""
    img = new(16, 16, HOUSING[1])
    for i in range(16):
        put(img, 7, i, HOUSING[3])
        put(img, 8, i, HOUSING[0])
    return img


PANE_SUF = ["%s_f%d" % (g, m) for g in ("", "_glow") for m in range(16)]
PANE_T = family("light_pane", PANE_SUF,
                lambda: expand_mask16(Art.of(light_pane()), light_pane_frame(), light_pane_glow()))
PANE_BOTH = {"particle": "light_pane_f15", "e": "light_pane_edge"}
POST = ([7, 0, 7], [9, 16, 9])


def _pane_tx(m):
    return dict(PANE_BOTH, t="light_pane_f%d" % m, g="light_pane_glow_f%d" % m)


def light_pane_model(c):
    out = {}
    for u in (0, 1):                       # 1 = framed (no pane above / below)
        for d in (0, 1):
            ud = u * 8 + d * 4
            tag = "u%dd%d" % (u, d)
            arm = ([7, 0, 0], [9, 16, 7])
            out["light_pane_arm_" + tag] = mdl(c, _pane_tx(ud), [
                solid(c, *arm, {"west": T("t"), "east": T("t"), "up": T("e", [7, 0, 9, 7], "up"),
                                "down": T("e", [7, 0, 9, 7], "down")}),
                lit(c, *arm, {"west": T("g"), "east": T("g")})])
            for name, m, uv in (("mid", ud, None), ("end_l", ud | 2, [0, 0, 2, 16]),
                                ("end_r", ud | 1, [14, 0, 16, 16])):
                out["light_pane_post_%s_%s" % (name, tag)] = mdl(c, _pane_tx(m), [
                    solid(c, *POST, {"north": T("t", uv)}), lit(c, *POST, {"north": T("g", uv)})])
    out["light_pane_post_cap"] = mdl(c, PANE_BOTH, [solid(c, *POST, {"north": T("e", [7, 0, 9, 16])})])
    out["light_pane_post_core"] = mdl(c, PANE_BOTH, [
        solid(c, *POST, {"up": T("e", [7, 7, 9, 9], "up"), "down": T("e", [7, 7, 9, 9], "down")})])
    flat = ([0, 0, 7], [16, 16, 9])
    out["light_pane_inventory"] = mdl(c, _pane_tx(15), [
        solid(c, *flat, {"north": T("t"), "south": T("t"), "west": T("e", [7, 0, 9, 16]),
                         "east": T("e", [7, 0, 9, 16]), "up": T("e", [0, 7, 16, 9]), "down": T("e", [0, 7, 16, 9])}),
        lit(c, *flat, {"north": T("g"), "south": T("g")})])
    return out


def light_pane_state(c):
    ring = ["north", "east", "south", "west"]
    parts = [{"apply": {"model": c.model("light_pane_post_core")}}]
    for i, d in enumerate(ring):
        cw, ccw, ry = ring[(i + 1) % 4], ring[(i + 3) % 4], i * 90
        parts.append({"when": {d: "false", cw: "false", ccw: "false"},
                      "apply": var(c, "light_pane_post_cap", 0, ry)})
        for u in (0, 1):
            for dn in (0, 1):
                tag = "u%dd%d" % (u, dn)
                ud = {"up": b2s(not u), "down": b2s(not dn)}
                parts.append({"when": dict(ud, **{d: "true"}), "apply": var(c, "light_pane_arm_" + tag, 0, ry)})
                # the post face that looks toward d: part of the lit wall when the pane runs past it
                for name, e, w in (("mid", "true", "true"), ("end_l", "false", "true"), ("end_r", "true", "false")):
                    parts.append({"when": dict(ud, **{d: "false", cw: e, ccw: w}),
                                  "apply": var(c, "light_pane_post_%s_%s" % (name, tag), 0, ry)})
    return {"multipart": parts}


# =========================================================================================
# sconce_bar -- wall bar light: back plate + capped diffuser bar; vertical or horizontal
# =========================================================================================
def _sconce_art():
    """Elevation seen from the front (position-mapped): plate cols 5..10 rows 2..13 round the
    bar (cols 7..8, rows 3..12). Cols 12..13 = bar side, col 4 = plate edge, rows 0..1 = caps."""
    img = new(16, 16, HOUSING[1])
    bevel(img, 5, 2, 11, 14, HOUSING, fill=1)
    glow = []
    for (cx, shade) in ((7, 0), (12, 1)):
        for y in range(3, 13):
            capr = y in (3, 4, 11, 12)
            for dx in (0, 1):
                if capr:
                    i = {3: 3, 4: 1, 11: 3, 12: 0}[y]
                    put(img, cx + dx, y, TRIM[max(0, i - dx)])
                else:
                    put(img, cx + dx, y, DIFFUSER[2 - dx])
                    glow.append((cx + dx, y, dx == 0 and not shade))
    for y in range(2, 14):
        put(img, 4, y, HOUSING[2])
    bevel(img, 12, 0, 14, 2, TRIM, fill=2)
    for x in range(5, 11):
        put(img, x, 0, HOUSING[2])
    return img, glow


def _sconce_glow(tid, flip):
    def frame(t):
        k = breath(t)
        img = new()
        for (x, y, hot) in _sconce_art()[1]:
            put(img, x, y, glo(k, hot))
        return transpose(img) if flip else img
    return animate("sconce_bar_glow", "breathing", frame)


TEXTURES["sconce_bar"] = lambda: _sconce_art()[0]
TEXTURES["sconce_bar_glow"] = lambda: _sconce_glow("sconce_bar_glow", False)
TEXTURES["sconce_bar_h"] = lambda: transpose(_sconce_art()[0])
TEXTURES["sconce_bar_h_glow"] = lambda: _sconce_glow("sconce_bar_h_glow", True)


def _sconce(c, vertical):
    """Drawn looking north, hung on the block to the south. Horizontal = x and y swapped, which is
    a transpose of the position-mapped sheet."""
    def bx(x0, y0, z0, x1, y1, z1):
        # north-face auto-UV is u = 16-x, v = 16-y, so swapping x/y transposes the sheet
        return ([x0, y0, z0], [x1, y1, z1]) if vertical else ([16 - y1, 16 - x1, z0], [16 - y0, 16 - x0, z1])

    def uv(a):
        return a if vertical else [a[1], a[0], a[3], a[2]]
    t = "sconce_bar" if vertical else "sconce_bar_h"
    tx = {"particle": "sconce_bar", "t": t, "g": t + "_glow"}
    plate, bar = bx(5, 2, 15, 11, 14, 16), bx(7, 3, 13, 9, 13, 15)
    long_a, long_b = ("west", "east") if vertical else ("up", "down")
    end_a, end_b = ("up", "down") if vertical else ("west", "east")
    side, cap, pedge, pend = uv([12, 3, 14, 13]), uv([12, 0, 14, 2]), uv([4, 2, 5, 14]), uv([5, 0, 11, 1])
    return mdl(c, tx, [
        solid(c, *plate, {"north": T("t"), "south": T("t", None, "south"), long_a: T("t", pedge),
                          long_b: T("t", pedge), end_a: T("t", pend), end_b: T("t", pend)}),
        solid(c, *bar, {"north": T("t"), long_a: T("t", side), long_b: T("t", side),
                        end_a: T("t", cap), end_b: T("t", cap)}),
        lit(c, *bar, {"north": T("g"), long_a: T("g", side), long_b: T("g", side)}),
    ])


def sconce_bar_model(c):
    return {"sconce_bar": _sconce(c, True), "sconce_bar_h": _sconce(c, False)}


def sconce_bar_state(c):
    ry = {"north": 0, "east": 90, "south": 180, "west": 270}
    return {"variants": {"facing=%s,vertical=%s" % (f, b2s(v)): var(c, "sconce_bar" if v else "sconce_bar_h", 0, y)
                         for f, y in ry.items() for v in (True, False)}}


# =========================================================================================
# halo_ring -- octagonal ring light: 8 boxes at 0 / 45 degrees
# =========================================================================================
# rows 0..1 spine (top), 4..5 outer wall, 7..8 inner wall, 10..11 underside. Uniform along the
# segment, so the small overlaps where segments meet show identical texels.
@tex("halo_ring")
def halo_ring():
    img = new(16, 16, HOUSING[1])
    for x in range(16):
        put(img, x, 0, HOUSING[3])
        put(img, x, 1, HOUSING[2])
        put(img, x, 4, HOUSING[2])
        put(img, x, 5, DIFFUSER[1])
        put(img, x, 7, HOUSING[1])
        put(img, x, 8, DIFFUSER[1])
        put(img, x, 10, DIFFUSER[2])
        put(img, x, 11, DIFFUSER[2])
    return img


@tex("halo_ring_glow")
def halo_ring_glow():
    def frame(t):
        k = breath(t)
        img = new()
        for x in range(16):
            put(img, x, 5, glo(k))
            put(img, x, 8, glo(k))
            put(img, x, 10, glo(k, True))
            put(img, x, 11, glo(k, True))
        return img
    return animate("halo_ring_glow", "breathing", frame)


def halo_ring_model(c):
    tx = {"particle": "halo_ring", "t": "halo_ring", "g": "halo_ring_glow"}
    a, b = 5.1, 10.9
    segs = {"north": ([a, 7, 1], [b, 9, 3]), "south": ([a, 7, 13], [b, 9, 15]),
            "west": ([1, 7, a], [3, 9, b]), "east": ([13, 7, a], [15, 9, b])}
    opp = {"north": "south", "south": "north", "west": "east", "east": "west"}
    els = []
    for angle in (0, 45):
        for side, box in segs.items():
            ends = ("west", "east") if side in ("north", "south") else ("north", "south")
            base = {"up": T("t", [5, 0, 11, 2]), side: T("t", [5, 4, 11, 6]), opp[side]: T("t", [5, 7, 11, 9]),
                    "down": T("t", [5, 10, 11, 12]), ends[0]: T("t", [0, 0, 2, 2]), ends[1]: T("t", [0, 0, 2, 2])}
            if side in ("west", "east"):   # run along z: turn the lengthwise faces
                base["up"]["rotation"] = 90
                base["down"]["rotation"] = 90
            glow = {f: dict(v, texture="#g") for f, v in base.items() if f in (side, opp[side], "down")}
            pair = [solid(c, *box, base), lit(c, *box, glow)]
            if angle:
                for e in pair:
                    e["rotation"] = {"origin": [8, 8, 8], "axis": "y", "angle": 45}
            els += pair
    return {"halo_ring": mdl(c, tx, els)}


# =========================================================================================
# cage_lamp -- bulkhead lamp: plate, collar, bulb, 4 cutout cage planes, cage crown (9 elements)
# =========================================================================================
@tex("cage_lamp")
def cage_lamp():
    """Side elevation, position-mapped: plate row 15, collar rows 13..14, bulb rows 8..12,
    crown row 6."""
    img = new(16, 16, HOUSING[1])
    for x in range(4, 12):
        put(img, x, 15, HOUSING[3] if x < 11 else HOUSING[0])
    for x in range(6, 10):
        put(img, x, 13, TRIM[3] if x < 9 else TRIM[1])
        put(img, x, 14, TRIM[1] if x < 9 else TRIM[0])
        for y in range(8, 13):
            put(img, x, y, DIFFUSER[2] if x in (7, 8) else DIFFUSER[1])
    for x in range(5, 11):
        put(img, x, 6, TRIM[3] if x < 10 else TRIM[1])
    return img


@tex("cage_lamp_top")
def cage_lamp_top():
    """Plan, position-mapped: mounting plate 4..11 with the cage crown 5..10 inside it."""
    img = new(16, 16, HOUSING[1])
    bevel(img, 4, 4, 12, 12, HOUSING, fill=2)
    bevel(img, 5, 5, 11, 11, TRIM, fill=2)
    bevel(img, 7, 7, 9, 9, HOUSING, fill=1)
    return img


@tex("cage_lamp_cage")
def cage_lamp_cage():
    """Cutout cage plane (position-mapped, cols 5..10 rows 7..14): corner posts and two hoops."""
    img = new()
    for y in range(7, 15):
        put(img, 5, y, TRIM[2])
        put(img, 10, y, TRIM[1])
    for x in range(5, 11):
        for y in (9, 12):
            put(img, x, y, TRIM[2] if x < 10 else TRIM[1])
    put(img, 5, 7, TRIM[3])
    return img


@tex("cage_lamp_glow")
def cage_lamp_glow():
    def frame(t):
        k = breath(t)
        img = new()
        for x in range(6, 10):
            for y in range(8, 13):
                put(img, x, y, glo(k, x in (7, 8)))
        return img
    return animate("cage_lamp_glow", "breathing", frame)


def cage_lamp_model(c):
    tx = {"particle": "cage_lamp_top", "t": "cage_lamp", "p": "cage_lamp_top", "k": "cage_lamp_cage",
          "g": "cage_lamp_glow"}
    side = FACES[:4]
    bulb = ([6, 3, 6], [10, 8, 10])
    els = [
        solid(c, [4, 0, 4], [12, 1, 12], dict({f: T("t") for f in side}, up=T("p"), down=T("p", None, "down"))),
        solid(c, [6, 1, 6], [10, 3, 10], {f: T("t") for f in side}),
        solid(c, *bulb, dict({f: T("t") for f in side}, up=T("t", [6, 8, 10, 12]))),
        lit(c, *bulb, dict({f: T("g") for f in side}, up=T("g", [6, 8, 10, 12]))),
        solid(c, [5, 1, 5], [11, 9, 5], {"north": T("k"), "south": T("k")}),
        solid(c, [5, 1, 11], [11, 9, 11], {"north": T("k"), "south": T("k")}),
        solid(c, [5, 1, 5], [5, 9, 11], {"west": T("k"), "east": T("k")}),
        solid(c, [11, 1, 5], [11, 9, 11], {"west": T("k"), "east": T("k")}),
        solid(c, [5, 9, 5], [11, 10, 11], dict({f: T("t") for f in side}, up=T("p"), down=T("p"))),
    ]
    return {"cage_lamp": mdl(c, tx, els)}


def cage_lamp_state(c):
    return {"variants": {"face=%s" % f: var(c, "cage_lamp", *face_rotation(f)) for f in _DIRS}}


# =========================================================================================
def _b(bid, name, connects, light, textures, model, state, item):
    return Block(bid, shape="custom", connects=connects, chroma=True, emissive=True, light=light,
                 textures=textures, model=model, blockstate=state, item_model=item, name=name)


BLOCKS = [
    _b("light_strip", "Light Strip", "strip", 12, dict(texmap("light_strip", LIGHT_STRIP_T), all="light_strip"),
       light_strip_model, light_strip_state, "light_strip_c11"),
    _b("edge_trim", "Edge Trim", "strip", 10,
       texmap("edge_trim", EDGE_T, "edge_trim_inner", "edge_trim_inner_glow", "edge_trim_outer",
              "edge_trim_outer_glow"), edge_trim_model, edge_trim_state, "edge_trim_c11"),
    _b("floor_tile", "Floor Tile", "mask16", 8, texmap(TILE_T), floor_tile_model, floor_tile_state,
       "floor_tile_inventory"),
    _b("floor_tile_slab", "Floor Tile Slab", "mask16", 8, texmap(TILE_T, SLAB_T), floor_tile_slab_model,
       floor_tile_slab_state, "floor_tile_slab_inventory"),
    _b("step_light", "Step Light", "none", 9, texmap("step_light", "step_light_glow"),
       step_light_model, step_light_state, "step_light"),
    _b("ceiling_coffer", "Ceiling Coffer", "mask16", 15,
       texmap("ceiling_coffer", "ceiling_coffer_rim", "ceiling_coffer_glow"),
       ceiling_coffer_model, ceiling_coffer_state, "ceiling_coffer"),
    _b("light_pillar", "Light Pillar", "strip", 15, texmap(PILLAR_T, "light_pillar_end"),
       light_pillar_model, light_pillar_state, "light_pillar_c11"),
    _b("neon_tube", "Neon Tube", "strip", 13, texmap("neon_tube", NEON_T),
       neon_tube_model, neon_tube_state, "neon_tube_c11"),
    _b("light_pane", "Light Pane", "mask16", 14, texmap(PANE_T, "light_pane_edge"),
       light_pane_model, light_pane_state, "light_pane_inventory"),
    _b("sconce_bar", "Sconce Bar", "none", 13,
       texmap("sconce_bar", "sconce_bar_glow", "sconce_bar_h", "sconce_bar_h_glow"),
       sconce_bar_model, sconce_bar_state, "sconce_bar"),
    _b("halo_ring", "Halo Ring", "none", 15, texmap("halo_ring", "halo_ring_glow"),
       halo_ring_model, None, "halo_ring"),
    _b("cage_lamp", "Cage Lamp", "none", 14,
       texmap("cage_lamp", "cage_lamp_top", "cage_lamp_cage", "cage_lamp_glow"),
       cage_lamp_model, cage_lamp_state, "cage_lamp"),
]

EXAMPLES = []
