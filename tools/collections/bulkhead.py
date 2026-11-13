"""
Bulkhead collection -- doors, hatches, frames and glazing (12 blocks).

Everything here is built from _kit primitives. Local helpers the kit does not have yet:
  box()/mirror_x()   elements with explicit per-face UVs, and an x-mirror (hinge=right doors)
  mount_variants()   blockstate rotations for a part authored on the DOWN face with its top to north
  _mask16_state()    the kit's 16-mask cube with an extra blockstate condition (smart_glass `opaque`)
  multiblock tiles   blast_door paints one 48x48 design and slices it into nine 16px tiles

Blockstate properties each block expects from Java:
  sliding_door       facing (n/e/s/w), half (lower/upper), hinge (left/right), open
  iris_hatch         face (the side it is mounted on, six-way), open
  blast_door         facing, part 0-8 (row * 3 + col; row 0 = top, col 0 = viewer's left when looking
                     at the front), open
  pressure_trapdoor  facing, half (top/bottom), open          (vanilla trapdoor layout)
  airlock_frame      north..down booleans (kit mask16)
  door_status_light  face
  access_pad         face
  viewport_frame     north..down booleans (kit mask16)
  porthole           axis
  canopy_glass       facing (low edge), half, pos_link / neg_link (same block at the +/- ridge end)
  shutter            axis (x/z = the way the curtain runs), open, pos_link (shutter above),
                     neg_link (shutter below)
  smart_glass        north..down booleans, opaque

Block-entity parts (drawn by the Java renderer; the JSON here is the static/closed state):
  sliding_door_leaf_lower / _upper (+ _right)   the sliding leaf, authored at its closed position
  iris_hatch_blade                              one flat blade quad (cutout), rotated round the centre
  blast_door_panel_left / _right                the two 21x42 px panels, relative to the centre part
  pressure_trapdoor_leaf                        the hatch plate, hinge on the south edge
  shutter_slat                                  one 4 px slat of the roller curtain
"""
import math
from functools import lru_cache

from ._kit import (FACE_EDGES, FACES, Art, Block, animate, bevel, bezel_layer, det, el, expand_mask16,
                   expand_strip, lerp, new, put, ramp, ramp5, rect, wear)

COLLECTION = "bulkhead"

# --- ramps ----------------------------------------------------------------------------------------
BODY = ramp("body")                    # near-black navy plate
HULL = ramp("station")                 # lighter blue-grey fittings
STEEL = ramp("starsteel")              # bright trim, used sparingly
PLASMA = ramp("plasma")                # cyan accent (doors, hatch, pad)
AMBER = ramp5((238, 150, 34))          # hazard accent (blast door, airlock frame)
FROST = ramp5((222, 230, 240))         # near-neutral: chroma tint target
RIB = [BODY[0], BODY[1], BODY[3], BODY[4], HULL[3]]      # raised navy trim
NEUTRAL = ramp5((74, 82, 96), shift=0.6)                 # near-neutral frame for tinted glass
HAZ = lerp(AMBER[2], BODY[1], 0.22)    # painted (non-emissive) hazard yellow
HAZ_D = lerp(AMBER[1], BODY[0], 0.30)
GLASS = PLASMA[2]


# =================================================================================================
# Local painting helpers
# =================================================================================================
def _hl(img, x0, x1, y, c, a=255):
    for x in range(x0, x1):
        put(img, x, y, c, a)


def _vl(img, x, y0, y1, c, a=255):
    for y in range(y0, y1):
        put(img, x, y, c, a)


def _inset(img, x0, y0, x1, y1, r, fill=None):
    """Recess: occlusion on the top/left inner edge, highlight on the bottom/right."""
    if fill is not None:
        rect(img, x0, y0, x1, y1, fill)
    _hl(img, x0, x1, y1 - 1, r[3])
    _vl(img, x1 - 1, y0, y1, r[3])
    _hl(img, x0, x1, y0, r[0])
    _vl(img, x0, y0, y1, r[0])


def _hazard(img, x0, y0, x1, y1, d=1, period=4):
    for y in range(y0, y1):
        for x in range(x0, x1):
            k = (x * d + y) % period
            put(img, x, y, HAZ if k < period // 2 else BODY[0])
            if k == 0:
                put(img, x, y, HAZ_D)


def _glass(img, x0, y0, x1, y1, rgb=GLASS, a=46, glint=PLASMA[4]):
    rect(img, x0, y0, x1, y1, rgb, a)
    for (x, y, ga) in ((3, 5, 150), (4, 4, 170), (5, 3, 150), (4, 6, 90), (6, 4, 90),
                       (11, 11, 110), (12, 10, 110)):
        if x0 <= x < x1 and y0 <= y < y1:
            put(img, x, y, glint, ga)


def _phase(frames, key):
    off = int(det("phase", key) * len(frames))
    return frames[off:] + frames[:off]


def _breath(t):
    return 0.5 + 0.5 * math.cos(2 * math.pi * t)


# =================================================================================================
# Local model helpers
# =================================================================================================
def _auto(face, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {"north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
            "west": [z0, 16 - y1, z1, 16 - y0], "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
            "up": [x0, z0, x1, z1], "down": [x0, 16 - z1, x1, 16 - z0]}[face]


def box(f, t, faces, tint=False, emit=0, cull=None):
    """One element with explicit UVs. faces = {face: "#tex" | ("#tex", [u0, v0, u1, v1])};
    cull = {face: cullface}."""
    spec = {}
    for name, v in faces.items():
        tex, uv = v if isinstance(v, tuple) else (v, None)
        d = {"texture": tex, "uv": list(uv) if uv else _auto(name, f, t)}
        if cull and name in cull:
            d["cullface"] = cull[name]
        spec[name] = d
    return el(list(f), list(t), spec, cull=False, tint=tint, emit=emit)


def mirror_x(els):
    """Mirror elements across x = 8 (textures mirror with them)."""
    out = []
    for e in els:
        f, t = e["from"], e["to"]
        n = dict(e)
        n["from"], n["to"] = [16 - t[0], f[1], f[2]], [16 - f[0], t[1], t[2]]
        faces = {}
        for name, fd in e["faces"].items():
            fd = dict(fd)
            u0, v0, u1, v1 = fd["uv"]
            fd["uv"] = [u1, v0, u0, v1]
            faces[{"east": "west", "west": "east"}.get(name, name)] = fd
        n["faces"] = faces
        out.append(n)
    return out


def mdl(c, tex, els, rt=None, particle=None, display=None):
    tx = {"particle": c.tex(particle or next(iter(tex.values())))}
    tx.update({k: c.tex(v) for k, v in tex.items()})
    m = {"textures": tx, "elements": els}
    if rt:
        m["render_type"] = rt
    if display:
        m["display"] = display
    return m


def _rot(v, rx, ry):
    x, y, z = v
    for _ in range(rx // 90):
        y, z = z, -y
    for _ in range(ry // 90):
        x, z = -z, x
    return (x, y, z)


_DIRS = {"down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1), "south": (0, 0, 1),
         "west": (-1, 0, 0), "east": (1, 0, 0)}
_YROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def _var(c, model, rx=0, ry=0):
    v = {"model": c.model(model)}
    if rx % 360:
        v["x"] = rx % 360
    if ry % 360:
        v["y"] = ry % 360
    return v


def mount_rotation(face):
    """(x, y) taking a part authored on the DOWN face (its top edge to north) onto `face`; on walls
    the part's top edge ends up pointing up."""
    for rx in (0, 90, 180, 270):
        for ry in (0, 90, 180, 270):
            if _rot(_DIRS["down"], rx, ry) != _DIRS[face]:
                continue
            if face in ("up", "down") or _rot(_DIRS["north"], rx, ry) == _DIRS["up"]:
                return rx, ry
    raise ValueError(face)


def _display(scale, ty=0.0):
    s = [scale] * 3
    return {"gui": {"rotation": [30, 225, 0], "translation": [0, ty, 0], "scale": s},
            "ground": {"translation": [0, 3, 0], "scale": [scale * 0.4] * 3},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, ty, 0], "scale": [scale * 0.8] * 3},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                                      "scale": [scale * 0.6] * 3},
            "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [scale * 0.64] * 3}}


# =================================================================================================
# sliding_door -- 1x2, leaf slides into a 2px pocket jamb on the hinge side
# =================================================================================================
@lru_cache(None)
def _leaf():
    """16x32 leaf, LEADING edge at texture left; columns 14..15 sit inside the pocket."""
    img = new(16, 32, BODY[2])
    _vl(img, 0, 0, 32, HULL[2])            # leading edge strip
    _vl(img, 1, 0, 32, BODY[0])            # gasket groove
    _vl(img, 2, 0, 32, BODY[3])            # panel highlight
    for x in (13, 14, 15):
        _vl(img, x, 0, 32, BODY[1])        # shadow cast by the jamb
    _hl(img, 2, 13, 2, BODY[3])
    _hl(img, 2, 14, 30, BODY[0])
    _vl(img, 4, 4, 29, BODY[0])            # light channel (glow sits in it)
    # window
    bevel(img, 6, 4, 13, 13, RIB, fill=2)
    rect(img, 7, 5, 12, 12, PLASMA[0])
    _hl(img, 7, 12, 5, BODY[0])
    _vl(img, 7, 5, 12, BODY[0])
    for (x, y) in ((8, 10), (9, 9), (10, 8), (10, 6)):
        put(img, x, y, PLASMA[1])
    # panel break
    _hl(img, 5, 13, 15, BODY[0])
    _hl(img, 5, 13, 16, BODY[3])
    # lower recess with grip slots
    _inset(img, 6, 19, 13, 26, BODY, fill=BODY[1])
    for y in (21, 23):
        _hl(img, 8, 11, y, BODY[0])
        _hl(img, 8, 11, y + 1, BODY[3])
    _hazard(img, 6, 27, 13, 30)
    wear(img, "sliding_door_leaf", BODY, rate=0.04)
    for y in range(4, 29):                 # unlit emitter as it reads by day
        if y % 4 != 3:
            put(img, 4, y, PLASMA[1])
    return img


def _leaf_glow(t):
    img = new(16, 32)
    k = _breath(t)
    for y in range(4, 29):
        if y % 4 != 3:
            put(img, 4, y, lerp(PLASMA[2], PLASMA[4], k))
    return img


def _leaf_glow_art(half):
    frames = [_leaf_glow(i / 8.0).crop((0, half * 16, 16, half * 16 + 16)) for i in range(8)]
    return Art(_phase(frames, "sliding_door_leaf_glow"), "breathing")   # both halves share a phase


def sliding_door_jamb():
    """Vertical strips: 0-1 jamb front, 2-7 jamb side (pocket slot), 8-10 leaf edge, 11-15 spare."""
    img = new(16, 16, BODY[2])
    for x, c in enumerate((BODY[4], BODY[3], BODY[4], BODY[3], BODY[1], BODY[0], BODY[3], BODY[1],
                           HULL[2], BODY[0], BODY[2])):
        _vl(img, x, 0, 16, c)
    for y in (3, 11):
        put(img, 1, y, HULL[3])
        put(img, 1, y + 1, BODY[0])
        put(img, 6, y, HULL[3])
    return wear(img, "sliding_door_jamb", BODY, rate=0.03)


def sliding_door_header():
    """Horizontal strips: rows 0-1 header front, 2-7 header underside/top, 9-14 sill top, 15 sill
    front."""
    img = new(16, 16, BODY[2])
    for y, c in enumerate((BODY[4], BODY[3], BODY[3], BODY[2], BODY[0], BODY[0], BODY[2], BODY[1],
                           BODY[1], BODY[4], BODY[3], BODY[0], BODY[0], BODY[3], BODY[1], BODY[3])):
        _hl(img, 0, 16, y, c)
    for x in (2, 13):
        put(img, x, 1, HULL[3])
    for x in range(1, 16, 3):              # tread dots on the sill
        put(img, x, 10, BODY[4])
        put(img, x, 13, BODY[4])
    return img


def _door_half(half, opened, glow=True):
    """Elements for one half, hinge=left, facing north (jamb on the west side)."""
    leaf, lg = "#leaf_%s" % half, "#glow_%s" % half
    if half == "lower":
        frame = [box((0, 0, 5), (16, 1, 11), {"north": ("#header", [0, 15, 16, 16]),
                                               "south": ("#header", [0, 15, 16, 16]),
                                               "up": ("#header", [0, 9, 16, 15]),
                                               "down": ("#header", [0, 9, 16, 15]),
                                               "east": ("#header", [0, 15, 6, 16]),
                                               "west": ("#header", [0, 15, 6, 16])},
                     cull={"down": "down"})]
        y0, y1 = 1, 16
    else:
        frame = [box((0, 14, 5), (16, 16, 11), {"north": ("#header", [0, 0, 16, 2]),
                                                 "south": ("#header", [0, 0, 16, 2]),
                                                 "up": ("#header", [0, 2, 16, 8]),
                                                 "down": ("#header", [0, 2, 16, 8]),
                                                 "east": ("#header", [0, 0, 6, 2]),
                                                 "west": ("#header", [0, 0, 6, 2])},
                     cull={"up": "up"})]
        y0, y1 = 0, 14
    v0, v1 = 16 - y1, 16 - y0
    frame.append(box((0, y0, 5), (2, y1, 11), {"north": ("#jamb", [0, v0, 2, v1]),
                                                "south": ("#jamb", [0, v0, 2, v1]),
                                                "west": ("#jamb", [2, v0, 8, v1]),
                                                "east": ("#jamb", [2, v0, 8, v1])},
                     cull={"west": "west"}))
    return frame + _leaf_els(half, 4 if opened else 16, glow)


def _leaf_els(half, x1, glow=True):
    leaf, lg = "#leaf_%s" % half, "#glow_%s" % half
    y0, y1 = (1, 16) if half == "lower" else (0, 14)
    v0, v1 = 16 - y1, 16 - y0
    w = x1 - 2
    f, t = (2, y0, 6.5), (x1, y1, 9.5)
    els = [box(f, t, {"north": (leaf, [0, v0, w, v1]), "south": (leaf, [w, v0, 0, v1]),
                      "east": ("#jamb", [8, v0, 11, v1])})]
    if glow:
        els.append(box(f, t, {"north": (lg, [0, v0, w, v1]), "south": (lg, [w, v0, 0, v1])}, emit=15))
    return els


_DOOR_TEX = {"leaf_lower": "sliding_door_leaf_lower", "leaf_upper": "sliding_door_leaf_upper",
             "glow_lower": "sliding_door_leaf_lower_glow", "glow_upper": "sliding_door_leaf_upper_glow",
             "jamb": "sliding_door_jamb", "header": "sliding_door_header"}


def sliding_door_models(c):
    out = {}

    def tex(*halves):
        keep = {"jamb", "header"} | {"%s_%s" % (p, h) for p in ("leaf", "glow") for h in halves}
        return {k: v for k, v in _DOOR_TEX.items() if k in keep}
    for half in ("lower", "upper"):
        for opened in (False, True):
            els = _door_half(half, opened)
            name = "sliding_door_%s%s" % (half, "_open" if opened else "")
            out[name] = mdl(c, tex(half), els, "minecraft:cutout", particle="sliding_door_jamb")
            out[name + "_right"] = mdl(c, tex(half), mirror_x(els), "minecraft:cutout",
                                       particle="sliding_door_jamb")
        part = _leaf_els(half, 16)
        out["sliding_door_leaf_%s" % half] = mdl(c, tex(half), part, "minecraft:cutout",
                                                 particle="sliding_door_jamb")
        out["sliding_door_leaf_%s_right" % half] = mdl(c, tex(half), mirror_x(part), "minecraft:cutout",
                                                       particle="sliding_door_jamb")
    inv = _door_half("lower", False)
    for e in _door_half("upper", False):
        e = dict(e)
        e["from"] = [e["from"][0], e["from"][1] + 16, e["from"][2]]
        e["to"] = [e["to"][0], e["to"][1] + 16, e["to"][2]]
        inv.append(e)
    for e in inv:
        e["from"][1] -= 8
        e["to"][1] -= 8
    out["sliding_door_inventory"] = mdl(c, tex("lower", "upper"), inv, "minecraft:cutout",
                                        particle="sliding_door_jamb", display=_display(0.5))
    return out


def sliding_door_state(c):
    v = {}
    for facing, ry in _YROT.items():
        for half in ("lower", "upper"):
            for hinge in ("left", "right"):
                for opened in (False, True):
                    name = "sliding_door_%s%s%s" % (half, "_open" if opened else "",
                                                    "_right" if hinge == "right" else "")
                    v["facing=%s,half=%s,hinge=%s,open=%s" % (facing, half, hinge, str(opened).lower())] = \
                        _var(c, name, 0, ry)
    return {"variants": v}


# =================================================================================================
# iris_hatch -- 3px rim plate with an octagonal opening; blades are BE-drawn
# =================================================================================================
def _iris_zone(x, y):
    dx, dy = abs(x - 7.5), abs(y - 7.5)
    d, s = max(dx, dy), dx + dy
    if d <= 4.5 and s <= 7:
        return "hole"
    if d <= 5.5 and s <= 8.5:
        return "ring"
    if d <= 6.5 and s <= 10:
        return "groove"
    return "plate"


_IRIS_TICKS = ((7, 1), (8, 1), (7, 14), (8, 14), (1, 7), (1, 8), (14, 7), (14, 8),
               (3, 3), (12, 3), (3, 12), (12, 12))


def iris_hatch_rim():
    img = new(16, 16, BODY[2])
    bevel(img, 0, 0, 16, 16, BODY, fill=2)
    for y in range(16):
        for x in range(16):
            z = _iris_zone(x, y)
            lit = (x - 7.5) + (y - 7.5) < 0
            if z == "hole":
                put(img, x, y, (0, 0, 0), 0)
            elif z == "ring":
                put(img, x, y, HULL[3] if lit else HULL[1])
            elif z == "groove":
                put(img, x, y, BODY[0])
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        put(img, x, y, HULL[3])
    wear(img, "iris_hatch_rim", BODY, rate=0.03)
    for (x, y) in _IRIS_TICKS:
        put(img, x, y, PLASMA[1])
    return img


def iris_hatch_rim_glow():
    def frame(t):
        img = new()
        for (x, y) in _IRIS_TICKS:
            put(img, x, y, lerp(PLASMA[2], PLASMA[4], _breath(t)))
        return img
    return animate("iris_hatch_rim_glow", "breathing", frame)


def iris_hatch_side():
    img = new(16, 16, BODY[2])
    for y, c in enumerate((BODY[4], BODY[2], BODY[0])):
        _hl(img, 0, 16, y, c)
    for y in range(3, 16):
        _hl(img, 0, 16, y, BODY[1] if y % 2 else BODY[0])
    return img


def _iris_blades(x, y, n=6, twist=1.7):
    dx, dy = x - 7.5, y - 7.5
    r = math.hypot(dx, dy)
    # straight seams tangent to a small centre circle (a real iris), not a spiral
    a = ((math.atan2(dy, dx) + math.asin(min(1.0, twist / max(r, twist)))) / (2 * math.pi)) * n
    return r, a - math.floor(a)


def iris_hatch_closed():
    """The closed iris: six blades spiralling into the centre, each lit along its leading edge."""
    img = new(16, 16, BODY[2])
    for y in range(16):
        for x in range(16):
            r, frac = _iris_blades(x, y)
            c = BODY[0] if frac < 0.2 else BODY[4] if frac < 0.4 else BODY[2]
            put(img, x, y, c)
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        put(img, x, y, HULL[3] if (x, y) == (7, 7) else HULL[1] if (x, y) == (8, 8) else HULL[2])
    return img


def iris_hatch_blade():
    """One blade for the block-entity renderer (cutout): pivot at the outer tip, bottom-left."""
    img = new()
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = math.hypot(dx, dy)
            ang = math.degrees(math.atan2(-dy, dx)) % 360
            if 0.8 <= r <= 8.2 and 28 <= ang <= 92:
                c = BODY[4] if ang > 84 else BODY[0] if ang < 36 else BODY[3] if ang > 62 else BODY[2]
                if r > 7.2:
                    c = BODY[1]
                put(img, x, y, c)
    return img


def iris_hatch_models(c):
    tex = {"rim": "iris_hatch_rim", "rim_glow": "iris_hatch_rim_glow", "side": "iris_hatch_side"}
    side = ("#side", [0, 0, 16, 3])
    wall = ("#side", [3, 0, 13, 3])
    rim = [box((0, 0, 0), (16, 3, 16), {"up": "#rim", "down": "#rim", "north": side, "south": side,
                                         "east": side, "west": side}, cull={"down": "down"}),
           box((0, 0, 0), (16, 3, 16), {"up": "#rim_glow"}, emit=15),
           box((3, 0, 2), (13, 3, 3), {"south": wall}),
           box((3, 0, 13), (13, 3, 14), {"north": wall}),
           box((2, 0, 3), (3, 3, 13), {"east": wall}),
           box((13, 0, 3), (14, 3, 13), {"west": wall})]
    disc = box((3, 1, 3), (13, 2, 13), {"up": ("#closed", [3, 3, 13, 13]),
                                         "down": ("#closed", [3, 13, 13, 3])})
    blade = box((0, 1.5, 0), (16, 1.5, 16), {"up": ("#blade", [0, 0, 16, 16]),
                                              "down": ("#blade", [0, 16, 16, 0])})
    return {"iris_hatch": mdl(c, dict(tex, closed="iris_hatch_closed"), rim + [disc], "minecraft:cutout"),
            "iris_hatch_open": mdl(c, tex, rim, "minecraft:cutout"),
            "iris_hatch_blade": mdl(c, {"blade": "iris_hatch_blade"}, [blade], "minecraft:cutout")}


def iris_hatch_state(c):
    v = {}
    for face in _DIRS:
        rx, ry = mount_rotation(face)
        v["face=%s,open=false" % face] = _var(c, "iris_hatch", rx, ry)
        v["face=%s,open=true" % face] = _var(c, "iris_hatch_open", rx, ry)
    return {"variants": v}


# =================================================================================================
# blast_door -- 3x3 multiblock, one 48x48 design sliced into nine tiles
# =================================================================================================
_ARROWS = [(tip + abs(dy), 23 + dy, i) for i, tip in enumerate((7, 10)) for dy in range(-2, 3)]


def _hub(x, y):
    return math.hypot(x + 0.5 - 24, y + 0.5 - 24)


def _hub_glow(x, y):
    d = _hub(x, y)
    return 2.6 <= d < 3.9 and abs(x + 0.5 - 24) > 1 and abs(y + 0.5 - 24) > 1


@lru_cache(None)
def _blast():
    img = new(48, 48, BODY[2])
    frame = [BODY[0], BODY[1], BODY[3], BODY[4], HULL[3]]
    bevel(img, 0, 0, 48, 48, frame, fill=2)
    for i in range(2, 46):
        put(img, i, 2, BODY[1])
        put(img, 2, i, BODY[1])
    for i in range(2, 46):
        put(img, i, 45, BODY[4])
        put(img, 45, i, BODY[4])
    for p in (4, 11, 19, 28, 36, 43):
        for (x, y) in ((p, 1), (p, 46), (1, p), (46, p)):
            put(img, x, y, HULL[3])
    for (x0, x1, d) in ((3, 24, 1), (24, 45, -1)):
        bevel(img, x0, 3, x1, 45, BODY, fill=2)
        for y0 in (11, 33):
            bevel(img, x0 + 2, y0, x1 - 2, y0 + 4, RIB, fill=2)
        _hl(img, x0 + 2, x1 - 2, 38, BODY[0])
        _hazard(img, x0 + 2, 39, x1 - 2, 43, d)
    for (a, b) in ((6, 15), (33, 42)):                       # louvres
        for y in (5, 7, 9):
            _hl(img, a, b, y, BODY[0])
            _hl(img, a, b, y + 1, BODY[3])
    for a in (18, 26):                                       # id plates
        rect(img, a, 6, a + 4, 9, HULL[1])
        _hl(img, a, a + 4, 6, HULL[2])
        put(img, a + 1, 7, HULL[3])
    for a in (20, 25):                                       # lock bars either side of the seam
        bevel(img, a, 15, a + 3, 33, RIB, fill=2)
    _inset(img, 5, 19, 15, 28, BODY, fill=BODY[0])
    _inset(img, 33, 19, 43, 28, BODY, fill=BODY[0])
    for y in range(16, 32):                                  # hub
        for x in range(16, 32):
            d = _hub(x, y)
            lit = (x + y) < 47
            if d < 5:
                put(img, x, y, BODY[1])
            elif d < 6:
                put(img, x, y, BODY[0])
            elif d < 7.2:
                put(img, x, y, HULL[3] if lit else HULL[1])
            if d < 1.5:
                put(img, x, y, HULL[3] if lit else HULL[1])
    for y in range(17, 31):                                  # seam through the hub
        if 1.5 <= _hub(23, y) < 6:
            put(img, 23, y, BODY[0])
    wear(img, "blast_door", BODY, rate=0.04)
    for y in range(16, 32):                                  # unlit emitters by day
        for x in range(16, 32):
            if _hub_glow(x, y):
                put(img, x, y, AMBER[1])
    for (x, y, _i) in _ARROWS:
        put(img, x, y, AMBER[1])
        put(img, 47 - x, y, AMBER[1])
    return img


def _blast_glow(t, chase):
    img = new(48, 48)
    if chase:
        for (x, y, i) in _ARROWS:
            # the pulse runs outward: inner arrow (i = 1) first, then the outer one
            k = max(0.0, math.cos(2 * math.pi * (t - (1 - i) * 0.22))) ** 2
            c = lerp(AMBER[2], AMBER[4], k)
            put(img, x, y, c)
            put(img, 47 - x, y, c)
    else:
        for y in range(16, 32):
            for x in range(16, 32):
                if _hub_glow(x, y):
                    put(img, x, y, lerp(AMBER[2], AMBER[4], _breath(t)))
    return img


def _tile(img, n):
    r, c = divmod(n, 3)
    return img.crop((c * 16, r * 16, c * 16 + 16, r * 16 + 16))


def _blast_tile(n):
    return lambda: _tile(_blast(), n)


def _blast_tile_glow(n):
    tid = "blast_door_%d_glow" % n
    if n == 4:
        return lambda: animate(tid, "breathing", lambda t: _tile(_blast_glow(t, False), n))
    return lambda: animate(tid, "data", lambda t: _tile(_blast_glow(t, True), n))


def blast_door_edge():
    img = new(16, 16, BODY[2])
    bevel(img, 0, 0, 16, 16, RIB, fill=2)
    _inset(img, 4, 4, 12, 12, BODY, fill=BODY[1])
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(img, x, y, HULL[3])
    return wear(img, "blast_door_edge", BODY, rate=0.03)


def _bd_tex(tiles, glows=()):
    tx = {"edge": "blast_door_edge"}
    tx.update({"t%d" % n: "blast_door_%d" % n for n in sorted(tiles)})
    tx.update({"g%d" % n: "blast_door_%d_glow" % n for n in sorted(glows)})
    return tx


def _bd_part(r, c, opened):
    """Static model for part r*3+c, facing north. Viewer's left is east, so col 0 is at high x."""
    front, back = r * 3 + c, r * 3 + (2 - c)
    els = []
    edge = "#edge"
    y0, y1 = (3 if r == 2 else 0), (13 if r == 0 else 16)
    x0, x1 = (3 if c == 2 else 0), (13 if c == 0 else 16)

    def bar(f, t):
        els.append(box(f, t, {"north": "#t%d" % front, "south": "#t%d" % back, "up": edge, "down": edge,
                              "east": edge, "west": edge}))
    if r == 0:
        bar((0, 13, 4), (16, 16, 12))
    if r == 2:
        bar((0, 0, 4), (16, 3, 12))
    if c == 0:
        bar((13, y0, 4), (16, y1, 12))
    if c == 2:
        bar((0, y0, 4), (3, y1, 12))
    if not opened:
        f, t = (x0, y0, 5), (x1, y1, 11)
        els.append(box(f, t, {"north": "#t%d" % front, "south": "#t%d" % back}))
        if r == 1:
            els.append(box(f, t, {"north": "#g%d" % front, "south": "#g%d" % back}, emit=15))
    glows = {front, back} if (r == 1 and not opened) else ()
    return els, _bd_tex({front, back}, glows)


def _bd_panel(side):
    """BE part: one sliding panel, in the CENTRE part's block space (facing north)."""
    cols = ((3, 16, 0), (16, 24, 1)) if side == "left" else ((24, 32, 1), (32, 45, 2))
    rows = ((3, 16, 0), (16, 32, 1), (32, 45, 2))
    els, tiles, glows = [], set(), set()
    for (d0, d1, c) in cols:
        for (e0, e1, r) in rows:
            f, t = (32 - d1, 32 - e1, 5), (32 - d0, 32 - e0, 11)
            cb = 2 - c
            n_uv = [d0 - 16 * c, e0 - 16 * r, d1 - 16 * c, e1 - 16 * r]
            s_uv = [48 - d1 - 16 * cb, e0 - 16 * r, 48 - d0 - 16 * cb, e1 - 16 * r]
            fr, bk = r * 3 + c, r * 3 + cb
            tiles |= {fr, bk}
            e = ("#edge", [4, 4, 12, 12])
            els.append(box(f, t, {"north": ("#t%d" % fr, n_uv), "south": ("#t%d" % bk, s_uv),
                                  "up": e, "down": e, "east": e, "west": e}))
            if r == 1:
                glows |= {fr, bk}
                els.append(box(f, t, {"north": ("#g%d" % fr, n_uv), "south": ("#g%d" % bk, s_uv)},
                               emit=15))
    return els, _bd_tex(tiles, glows)


def blast_door_models(c):
    out = {}
    inv = []
    for r in range(3):
        for col in range(3):
            n = r * 3 + col
            for opened in (False, True):
                els, tx = _bd_part(r, col, opened)
                out["blast_door_%d%s" % (n, "_open" if opened else "")] = mdl(
                    c, tx, els, "minecraft:cutout", particle="blast_door_edge")
            f, t = (16 - 16 * col, 16 - 16 * r, 4), (32 - 16 * col, 32 - 16 * r, 12)
            full = [0, 0, 16, 16]
            e = ("#edge", full)
            inv.append(box(f, t, {"north": ("#t%d" % n, full), "south": ("#t%d" % (r * 3 + 2 - col), full),
                                  "up": e, "down": e, "east": e, "west": e}))
            if r == 1:
                inv.append(box(f, t, {"north": ("#g%d" % n, full),
                                      "south": ("#g%d" % (r * 3 + 2 - col), full)}, emit=15))
    for side in ("left", "right"):
        els, tx = _bd_panel(side)
        out["blast_door_panel_%s" % side] = mdl(c, tx, els, "minecraft:cutout", particle="blast_door_edge")
    out["blast_door_inventory"] = mdl(c, _bd_tex(range(9), (3, 4, 5)), inv, "minecraft:cutout",
                                      particle="blast_door_edge", display=_display(0.3))
    return out


def blast_door_state(c):
    v = {}
    for facing, ry in _YROT.items():
        for n in range(9):
            for opened in (False, True):
                v["facing=%s,open=%s,part=%d" % (facing, str(opened).lower(), n)] = _var(
                    c, "blast_door_%d%s" % (n, "_open" if opened else ""), 0, ry)
    return {"variants": v}


# =================================================================================================
# pressure_trapdoor
# =================================================================================================
def pressure_trapdoor_top():
    """Hatch top; hinge knuckles on the bottom (south) edge."""
    img = new(16, 16, BODY[2])
    bevel(img, 0, 0, 16, 16, RIB, fill=2)
    bevel(img, 2, 2, 14, 14, BODY, fill=2)
    for (x, y, sx, sy) in ((3, 3, 1, 1), (12, 3, -1, 1), (3, 12, 1, -1), (12, 12, -1, -1)):
        for i in range(3):                                   # hazard corner triangles
            for j in range(3 - i):
                put(img, x + sx * i, y + sy * j, HAZ if (i + j) % 2 == 0 else HAZ_D)
    for y in range(16):                                      # wheel
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            lit = (x + y) < 15
            if 2.9 <= d < 4.2:
                put(img, x, y, HULL[3] if lit else HULL[1])
            elif d < 2.9:
                put(img, x, y, BODY[1])
            if d < 2.9 and (x in (7, 8) or y in (7, 8)):
                put(img, x, y, HULL[2] if lit else HULL[1])
            if d < 1.2:
                put(img, x, y, HULL[4] if (x, y) == (7, 7) else HULL[3])
    for x0 in (2, 10):                                       # hinge knuckles
        rect(img, x0, 14, x0 + 4, 16, HULL[1])
        _hl(img, x0, x0 + 4, 14, HULL[3])
        put(img, x0 + 3, 15, BODY[0])
    return wear(img, "pressure_trapdoor_top", BODY, rate=0.04)


def pressure_trapdoor_bottom():
    """Underside; hinge edge at the top row."""
    img = new(16, 16, BODY[1])
    bevel(img, 0, 0, 16, 16, BODY, fill=1)
    _inset(img, 2, 2, 14, 14, BODY, fill=BODY[1])
    for a in (5, 9):
        bevel(img, a, 3, a + 2, 13, RIB, fill=2)
    bevel(img, 3, 7, 13, 9, RIB, fill=2)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        put(img, x, y, HULL[3])
    rect(img, 7, 7, 9, 9, HULL[1])
    put(img, 7, 7, HULL[3])
    return wear(img, "pressure_trapdoor_bottom", BODY, rate=0.04)


def pressure_trapdoor_side():
    img = new(16, 16, BODY[2])
    for y, c in enumerate((BODY[4], BODY[3], BODY[0])):
        _hl(img, 0, 16, y, c)
    for x in (1, 5, 10, 14):
        put(img, x, 1, HULL[3])
    return img


def pressure_trapdoor_models(c):
    tex = {"top": "pressure_trapdoor_top", "bottom": "pressure_trapdoor_bottom",
           "side": "pressure_trapdoor_side"}
    s = ("#side", [0, 0, 16, 3])

    def plate(y0, cull):
        return [box((0, y0, 0), (16, y0 + 3, 16), {"up": ("#top", [0, 0, 16, 16]),
                                                    "down": ("#bottom", [0, 0, 16, 16]),
                                                    "north": s, "south": s, "east": s, "west": s},
                    cull=cull)]
    v = ("#side", [0, 3, 16, 0])
    opened = [box((0, 0, 13), (16, 16, 16), {"south": ("#top", [0, 0, 16, 16]),
                                              "north": ("#bottom", [0, 16, 16, 0]),
                                              "up": s, "down": v,
                                              "east": ("#side", [0, 0, 16, 3]), "west": ("#side", [0, 0, 16, 3])},
                  cull={"south": "south"})]
    for e in opened:                                         # the 3px strip runs vertically on the ends
        for f in ("east", "west"):
            e["faces"][f]["rotation"] = 90
    return {"pressure_trapdoor_bottom": mdl(c, tex, plate(0, {"down": "down"})),
            "pressure_trapdoor_top": mdl(c, tex, plate(13, {"up": "up"})),
            "pressure_trapdoor_open": mdl(c, tex, opened),
            "pressure_trapdoor_leaf": mdl(c, tex, plate(0, None))}


def pressure_trapdoor_state(c):
    v = {}
    for facing, ry in _YROT.items():
        for half in ("bottom", "top"):
            v["facing=%s,half=%s,open=false" % (facing, half)] = _var(c, "pressure_trapdoor_" + half, 0, ry)
            v["facing=%s,half=%s,open=true" % (facing, half)] = (
                _var(c, "pressure_trapdoor_open", 0, ry) if half == "bottom"
                else _var(c, "pressure_trapdoor_open", 180, ry + 180))
    return {"variants": v}


# =================================================================================================
# airlock_frame -- chevron hazard plate, 16-mask rim
# =================================================================================================
def _chev(x, y):
    return (min(x, 15 - x) + y) % 16


def _chev_glow(x, y):
    return _chev(x, y) == 1


_CHEV = {15: BODY[3], 0: HAZ_D, 1: HAZ, 2: HAZ_D, 3: BODY[0], 8: BODY[0], 9: BODY[3]}


def airlock_frame():
    """One painted chevron and one embossed chevron per tile; mirrored about the tile centre, so
    the zig-zag runs unbroken across a connected frame."""
    img = new(16, 16, BODY[1])
    for y in range(16):
        for x in range(16):
            k = _chev(x, y)
            put(img, x, y, _CHEV.get(k, BODY[2] if 3 < k < 8 else BODY[1]))
    wear(img, "airlock_frame", BODY, rate=0.04)
    for y in range(16):
        for x in range(16):
            if _chev_glow(x, y):
                put(img, x, y, AMBER[1])
    return img


def airlock_frame_frame():
    return bezel_layer([BODY[0], BODY[1], BODY[3], BODY[4], HULL[3]], accent_rgb=HULL[3])


def airlock_frame_glow():
    def frame(t):
        img = new()
        for y in range(16):
            for x in range(16):
                if _chev_glow(x, y):
                    k = max(0.0, math.cos(2 * math.pi * (t - min(x, 15 - x) / 8.0))) ** 2
                    put(img, x, y, lerp(AMBER[2], AMBER[4], k))
        return img
    return animate("airlock_frame_glow", "flow", frame)


# =================================================================================================
# door_status_light -- chroma lozenge
# =================================================================================================
def door_status_light():
    img = new(16, 16, BODY[2])
    bevel(img, 0, 0, 16, 16, BODY, fill=2)
    bevel(img, 4, 6, 12, 10, RIB, fill=2)
    rect(img, 5, 7, 11, 8, FROST[2])
    rect(img, 5, 8, 11, 9, FROST[1])
    put(img, 5, 7, FROST[3])
    _hl(img, 4, 12, 12, BODY[3])
    _hl(img, 4, 12, 14, FROST[1])
    return img


def door_status_light_glow():
    def frame(t):
        k = _breath(t)
        img = new()
        rect(img, 5, 7, 11, 8, lerp(FROST[3], FROST[4], k))
        rect(img, 5, 8, 11, 9, lerp(FROST[2], FROST[3], k))
        _hl(img, 5, 11, 14, lerp(FROST[2], FROST[3], k))
        return img
    return animate("door_status_light_glow", "breathing", frame)


def door_status_light_models(c):
    hs = ("#base", [4, 12, 12, 13])
    ls = ("#base", [5, 14, 11, 15])
    lg = ("#glow", [5, 14, 11, 15])
    els = [box((4, 0, 6), (12, 1, 10), {"up": "#base", "down": "#base", "north": hs, "south": hs,
                                         "east": ("#base", [4, 12, 8, 13]), "west": ("#base", [4, 12, 8, 13])},
               cull={"down": "down"}),
           box((5, 1, 7), (11, 2, 9), {"up": "#base", "north": ls, "south": ls,
                                        "east": ("#base", [5, 14, 7, 15]), "west": ("#base", [5, 14, 7, 15])},
               tint=True),
           box((5, 1, 7), (11, 2, 9), {"up": "#glow", "north": lg, "south": lg,
                                        "east": ("#glow", [5, 14, 7, 15]), "west": ("#glow", [5, 14, 7, 15])},
               tint=True, emit=15)]
    return {"door_status_light": mdl(c, {"base": "door_status_light", "glow": "door_status_light_glow"},
                                     els, "minecraft:cutout")}


def _mount_state(model):
    def state(c):
        return {"variants": {"face=%s" % f: _var(c, model, *mount_rotation(f)) for f in _DIRS}}
    return state


# =================================================================================================
# access_pad -- 32px keypad (screen class), 8x10 plate at double density
# =================================================================================================
_KEYS = [(x, y) for y in (10, 13, 16) for x in (3, 7, 11)]
_GLYPHS = [(3 + 3 * g + i, 3 + j) for g in range(3) for i in range(2) for j in range(3)
           if det("access_pad", g, i, j) < 0.7]


def access_pad():
    img = new(32, 32, BODY[1])
    bevel(img, 0, 0, 16, 20, RIB, fill=2)
    _inset(img, 2, 2, 14, 8, BODY, fill=PLASMA[0])
    for (x, y) in _GLYPHS:
        put(img, x, y, PLASMA[1])
    for (x, y) in _KEYS:
        rect(img, x, y, x + 2, y + 2, BODY[3])
        put(img, x, y, BODY[4])
        _hl(img, x, x + 2, y + 2, BODY[0])
    x, y = _KEYS[6]
    rect(img, x, y, x + 2, y + 2, HAZ_D)                     # cancel key (painted)
    put(img, x, y, HAZ)
    x, y = _KEYS[8]
    rect(img, x, y, x + 2, y + 2, PLASMA[1])                 # enter key (lit by the glow layer)
    _hl(img, 0, 32, 22, BODY[3])                             # side strip
    _hl(img, 0, 32, 23, BODY[1])
    bevel(img, 16, 0, 32, 20, BODY, fill=1)                  # back plate
    return img


def access_pad_glow():
    def frame(t):
        img = new(32, 32)
        step = int(t * 16 + 1e-6)
        shown = min(3, step // 3 + 1) if step < 12 else (3 if step % 2 == 0 else 0)
        for (x, y) in _GLYPHS:
            if (x - 3) // 3 < shown:
                put(img, x, y, PLASMA[3])
        if step < 12 and shown < 3:
            for j in range(3):                               # cursor
                put(img, 3 + 3 * shown, 3 + j, PLASMA[2])
        kx, ky = _KEYS[8]
        rect(img, kx, ky, kx + 2, ky + 2, PLASMA[3])
        put(img, kx, ky, PLASMA[4])
        return img
    return animate("access_pad_glow", "data", frame)


def access_pad_models(c):
    face = [0, 0, 8, 10]
    els = [box((4, 0, 3), (12, 1, 13), {"up": ("#pad", face), "down": ("#pad", [8, 0, 16, 10]),
                                         "north": ("#pad", [0, 11, 8, 11.5]), "south": ("#pad", [0, 11.5, 8, 12]),
                                         "east": ("#pad", [0, 11.5, 10, 12]), "west": ("#pad", [0, 11, 10, 11.5])},
               cull={"down": "down"}),
           box((4, 0, 3), (12, 1, 13), {"up": ("#glow", face)}, emit=15)]
    for e in els[:1]:
        for f in ("east", "west"):
            e["faces"][f]["rotation"] = 90
    return {"access_pad": mdl(c, {"pad": "access_pad", "glow": "access_pad_glow"}, els, "minecraft:cutout")}


# =================================================================================================
# viewport_frame -- glazed centre, 16-mask rim (translucent)
# =================================================================================================
def viewport_frame():
    img = new()
    _glass(img, 0, 0, 16, 16)
    return img


def viewport_frame_frame():
    img = bezel_layer(HULL, accent_rgb=STEEL[3])
    for i in (5, 10):                                        # clamps on the rim
        for (x, y) in ((i, 1), (i, 14), (1, i), (14, i)):
            put(img, x, y, HULL[1])
    return img


# =================================================================================================
# porthole -- bolted ring, tunnel and glass; `axis`
# =================================================================================================
def porthole_rim():
    img = new(16, 16, BODY[2])
    bevel(img, 0, 0, 16, 16, BODY, fill=2)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            lit = (x + y) < 15
            if d < 4.6:
                put(img, x, y, (0, 0, 0), 0)
            elif d < 5.6:
                put(img, x, y, HULL[1] if lit else HULL[4])  # inner lip: lit on the far side
            elif d < 6.9:
                put(img, x, y, HULL[3] if lit else HULL[2])
            elif d < 7.7:
                put(img, x, y, BODY[0])
    for (x, y) in ((7, 1), (8, 14), (1, 8), (14, 7), (3, 3), (12, 12), (12, 3), (3, 12)):
        put(img, x, y, STEEL[3])                             # bolts
    for (x, y) in ((0, 0), (15, 0), (0, 15), (15, 15)):
        put(img, x, y, BODY[1])
    return wear(img, "porthole_rim", BODY, rate=0.03)


def porthole_side():
    img = new(16, 16, BODY[2])
    bevel(img, 0, 0, 16, 16, BODY, fill=2)
    _hl(img, 1, 15, 7, BODY[0])
    _hl(img, 1, 15, 8, BODY[3])
    for (x, y) in ((2, 2), (13, 2), (2, 5), (13, 5), (2, 10), (13, 10), (2, 13), (13, 13)):
        put(img, x, y, HULL[3])
        put(img, x, y + 1, BODY[0])
    return wear(img, "porthole_side", BODY, rate=0.05)


def porthole_inner():
    img = new(16, 16, BODY[1])
    for y in range(16):
        _hl(img, 0, 16, y, BODY[0] if y in (0, 15) else BODY[3] if y in (1, 14) else BODY[1])
    for y in (5, 10):
        _hl(img, 0, 16, y, BODY[2])
    return img


def porthole_glass():
    img = new()
    _glass(img, 3, 3, 13, 13)
    return img


def porthole_models(c):
    tex = {"rim": "porthole_rim", "side": "porthole_side", "inner": "porthole_inner", "glass": "porthole_glass"}
    els = [box((0, 0, 0), (16, 16, 16), {"north": "#rim", "south": "#rim", "east": "#side", "west": "#side",
                                          "up": "#side", "down": "#side"},
               cull={f: f for f in FACES}),
           box((3, 2, 0), (13, 3, 16), {"up": "#inner"}),
           box((3, 13, 0), (13, 14, 16), {"down": "#inner"}),
           box((2, 3, 0), (3, 13, 16), {"east": ("#inner", [0, 3, 16, 13])}),
           box((13, 3, 0), (14, 13, 16), {"west": ("#inner", [0, 3, 16, 13])}),
           box((3, 3, 7), (13, 13, 9), {"north": "#glass", "south": "#glass"})]
    return {"porthole": mdl(c, tex, els, "minecraft:translucent", particle="porthole_side")}


def porthole_state(c):
    return {"variants": {"axis=z": _var(c, "porthole"), "axis=x": _var(c, "porthole", 0, 90),
                         "axis=y": _var(c, "porthole", 90, 0)}}


# =================================================================================================
# canopy_glass -- 45-degree pane; side rails baked per link state
# =================================================================================================
def _canopy_base():
    img = new()
    _glass(img, 0, 0, 16, 16)
    for y, c in ((0, BODY[4]), (1, BODY[3]), (2, BODY[0]), (13, BODY[3]), (14, BODY[2]), (15, BODY[0])):
        _hl(img, 0, 16, y, c)
    for x in (3, 12):
        put(img, x, 1, HULL[3])
        put(img, x, 14, HULL[3])
    return img


def _canopy_cap():
    img = new()
    for x, c in ((0, BODY[4]), (1, BODY[3]), (2, BODY[0]), (13, BODY[3]), (14, BODY[2]), (15, BODY[0])):
        _vl(img, x, 0, 16, c)
    for y in (1, 14):
        put(img, 1, y, HULL[3])
        put(img, 14, y, HULL[3])
    return img


@lru_cache(None)
def _canopy():
    return expand_strip(Art.of(_canopy_base()), _canopy_cap())


def canopy_glass_edge():
    img = new(16, 16, BODY[2])
    for y in range(16):
        _hl(img, 0, 16, y, BODY[3] if y % 2 == 0 else BODY[1])
    return img


_SLOPE = 8 * (math.sqrt(2) - 1)   # how far a 45-degree pane overhangs the block before rotation


def canopy_glass_models(c):
    out = {}
    a, b = round(-_SLOPE, 4), round(16 + _SLOPE, 4)
    edge = ("#edge", [0, 0, 16, 1])
    for p in (0, 1):
        for n in (0, 1):
            e = box((0, 7.5, a), (16, 8.5, b), {"up": ("#pane", [0, 0, 16, 16]),
                                                 "down": ("#pane", [0, 16, 16, 0]),
                                                 "north": edge, "south": edge, "east": edge, "west": edge})
            e["rotation"] = {"origin": [8, 8, 8], "axis": "x", "angle": -45}
            out["canopy_glass_c%d%d" % (p, n)] = mdl(
                c, {"pane": "canopy_glass_c%d%d" % (p, n), "edge": "canopy_glass_edge"}, [e],
                "minecraft:translucent", particle="canopy_glass_edge")
    return out


def canopy_glass_state(c):
    v = {}
    for facing, ry in _YROT.items():
        for half in ("bottom", "top"):
            rx = 180 if half == "top" else 0
            run = _rot((1, 0, 0), rx, ry)
            flip = sum(run) < 0
            for pos in (False, True):
                for neg in (False, True):
                    cp, cn = int(not pos), int(not neg)
                    if flip:
                        cp, cn = cn, cp
                    v["facing=%s,half=%s,neg_link=%s,pos_link=%s"
                      % (facing, half, str(neg).lower(), str(pos).lower())] = _var(
                          c, "canopy_glass_c%d%d" % (cp, cn), rx, ry)
    return {"variants": v}


# =================================================================================================
# shutter -- roller curtain between guide rails; a stack shares one roller housing
# =================================================================================================
_SLAT = (BODY[3], BODY[2], BODY[1], BODY[0])


def _curtain():
    img = new(16, 16, BODY[2])
    for y in range(16):
        _hl(img, 0, 16, y, _SLAT[y % 4])
    for y in range(1, 16, 4):
        for x in (3, 12):
            put(img, x, y, BODY[4])
    return img


def shutter():
    return wear(_curtain(), "shutter", BODY, rate=0.03)


def shutter_foot():
    img = _curtain()
    bevel(img, 0, 12, 16, 16, [BODY[0], BODY[1], HULL[1], HULL[2], HULL[3]], fill=2)
    for x in range(2, 14):
        put(img, x, 14, HAZ if (x // 2) % 2 == 0 else BODY[0])
    return wear(img, "shutter_foot", BODY, rate=0.03)


def shutter_slat():
    """BE part: one slat in rows 0-3; rows 4-5 are its top/bottom edge."""
    img = new(16, 16, BODY[2])
    for y in range(4):
        _hl(img, 0, 16, y, _SLAT[y])
    for x in (3, 12):
        put(img, x, 1, BODY[4])
    _hl(img, 0, 16, 4, BODY[3])
    _hl(img, 0, 16, 5, BODY[0])
    return img


def shutter_housing():
    """Rows 0-3 box front, 4-9 box top/underside, 10-13 end caps (6 wide)."""
    img = new(16, 16, BODY[2])
    bevel(img, 0, 0, 16, 4, RIB, fill=2)
    for x in (2, 13):
        put(img, x, 1, HULL[3])
        put(img, x, 2, BODY[0])
    _hl(img, 5, 11, 2, BODY[1])
    bevel(img, 0, 4, 16, 10, BODY, fill=2)
    bevel(img, 0, 10, 6, 14, RIB, fill=2)
    put(img, 2, 11, HULL[3])
    put(img, 3, 12, HULL[1])
    return img


def shutter_rail():
    """Col 0 rail front, cols 2-5 rail side (channel), row 0 of cols 2-5 doubles as the rail end."""
    img = new(16, 16, BODY[2])
    _vl(img, 0, 0, 16, BODY[4])
    _vl(img, 1, 0, 16, BODY[1])
    for x, c in ((2, BODY[4]), (3, BODY[0]), (4, BODY[0]), (5, BODY[1])):
        _vl(img, x, 0, 16, c)
    return img


def _shutter_els(top_cap, bottom_cap, opened):
    h = 12 if top_cap else 16
    els = []
    for x0 in (0, 15):
        f, t = (x0, 0, 6), (x0 + 1, h, 10)
        els.append(box(f, t, {"north": ("#rail", [0, 16 - h, 1, 16]), "south": ("#rail", [0, 16 - h, 1, 16]),
                              "east": ("#rail", [2, 16 - h, 6, 16]), "west": ("#rail", [2, 16 - h, 6, 16]),
                              "up": ("#rail", [2, 0, 6, 1]), "down": ("#rail", [2, 0, 6, 1])}))
    if top_cap:
        cap = ("#housing", [0, 10, 6, 14])
        els.append(box((0, 12, 5), (16, 16, 11), {"north": ("#housing", [0, 0, 16, 4]),
                                                   "south": ("#housing", [0, 0, 16, 4]),
                                                   "up": ("#housing", [0, 4, 16, 10]),
                                                   "down": ("#housing", [0, 4, 16, 10]),
                                                   "east": cap, "west": cap}))
    if not opened:
        tex = "#foot" if bottom_cap else "#curtain"
        els.append(box((1, 0, 7), (15, h, 9), {"north": tex, "south": tex,
                                                "down": ("#rail", [2, 0, 6, 1])}))
    return els


def shutter_models(c):
    tex = {"curtain": "shutter", "foot": "shutter_foot", "housing": "shutter_housing", "rail": "shutter_rail"}
    out = {}
    for p in (1, 0):
        for n in (1, 0):
            out["shutter_c%d%d" % (p, n)] = mdl(c, tex, _shutter_els(p, n, False), particle="shutter")
    for p in (0, 1):
        out["shutter_open_c%d" % p] = mdl(c, tex, _shutter_els(p, 0, True), particle="shutter")
    edge = ("#slat", [0, 4, 14, 6])
    out["shutter_slat"] = mdl(c, {"slat": "shutter_slat"}, [
        box((1, 0, 7), (15, 4, 9), {"north": ("#slat", [1, 0, 15, 4]), "south": ("#slat", [1, 0, 15, 4]),
                                     "up": edge, "down": edge})])
    return out


def shutter_state(c):
    v = {}
    for axis, ry in (("x", 0), ("z", 90)):
        for opened in (False, True):
            for pos in (False, True):
                for neg in (False, True):
                    name = ("shutter_open_c%d" % int(not pos)) if opened else \
                        "shutter_c%d%d" % (int(not pos), int(not neg))
                    v["axis=%s,neg_link=%s,open=%s,pos_link=%s"
                      % (axis, str(neg).lower(), str(opened).lower(), str(pos).lower())] = _var(c, name, 0, ry)
    return {"variants": v}


# =================================================================================================
# smart_glass -- 16-mask glass with an `opaque` state; chroma tints the frosted state
# =================================================================================================
_SG_NODES = [(x, y) for y in (2, 6, 10, 13) for x in (2, 6, 10, 13) if (x in (2, 13)) or (y in (2, 13))]
_SG_CORNERS = [(2, 2), (13, 2), (2, 13), (13, 13)]


def _sg_clear():
    img = new()
    _glass(img, 0, 0, 16, 16, rgb=FROST[3], a=34, glint=FROST[4])
    return img


def _sg_frost():
    img = new(16, 16, FROST[2])
    for y in range(16):
        for x in range(16):
            k = (x + y) % 16
            if k in (6, 7):
                put(img, x, y, FROST[3])
    return wear(img, "smart_glass_frost", FROST, rate=0.05)


def _sg_frame():
    return bezel_layer(NEUTRAL)


def _sg_glow(nodes, tid):
    def frame(t):
        img = new()
        for (x, y) in nodes:
            put(img, x, y, lerp(FROST[3], FROST[4], _breath(t)))
        return img
    return animate(tid, "breathing", frame)


@lru_cache(None)
def _sg(kind):
    if kind == "clear":
        return expand_mask16(Art.of(_sg_clear()), _sg_frame(), _sg_glow(_SG_CORNERS, "smart_glass_glow"))
    return expand_mask16(Art.of(_sg_frost()), _sg_frame(), _sg_glow(_SG_NODES, "smart_glass_frost_glow"))


def _sg_painter(kind, key):
    return lambda: _sg(kind)[key]


def _mask16_state(c, prefix, tex, when_extra, tint):
    """The kit's mask16 cube (one part per face x 16 neighbour masks) with extra `when` terms."""
    models, parts = {}, []
    for face in FACES:
        edges = FACE_EDGES[face]
        for m in range(16):
            bits = (m & 8, m & 4, m & 2, m & 1)
            els = [el([0, 0, 0], [16, 16, 16], [face], "#all", uv=[0, 0, 16, 16], tint=tint),
                   el([0, 0, 0], [16, 16, 16], [face], "#glow", uv=[0, 0, 16, 16], tint=True, emit=15)]
            name = "%s_%s_f%d" % (prefix, face, m)
            models[name] = {"textures": {"particle": c.tex(tex + "_f15"), "all": c.tex("%s_f%d" % (tex, m)),
                                         "glow": c.tex("%s_glow_f%d" % (tex, m))},
                            "elements": els, "render_type": "minecraft:translucent"}
            when = {d: ("false" if bit else "true") for d, bit in zip(edges, bits)}
            when.update(when_extra)
            parts.append({"when": when, "apply": {"model": c.model(name)}})
    return models, parts


def _sg_build(c):
    m1, p1 = _mask16_state(c, "smart_glass", "smart_glass", {"opaque": "false"}, False)
    m2, p2 = _mask16_state(c, "smart_glass_frost", "smart_glass_frost", {"opaque": "true"}, True)
    inv = {"textures": {"particle": c.tex("smart_glass_frost_f15"), "all": c.tex("smart_glass_frost_f15"),
                        "glow": c.tex("smart_glass_frost_glow_f15")},
           "elements": [el([0, 0, 0], [16, 16, 16], FACES, "#all", uv=[0, 0, 16, 16], tint=True),
                        el([0, 0, 0], [16, 16, 16], FACES, "#glow", uv=[0, 0, 16, 16], tint=True, emit=15)],
           "render_type": "minecraft:translucent"}
    models = {"smart_glass_inventory": inv}
    models.update(m1)
    models.update(m2)
    return models, {"multipart": p1 + p2}


# =================================================================================================
# Declarations
# =================================================================================================
TEXTURES = {
    "sliding_door_leaf_lower": lambda: _leaf().crop((0, 16, 16, 32)),
    "sliding_door_leaf_upper": lambda: _leaf().crop((0, 0, 16, 16)),
    "sliding_door_leaf_lower_glow": lambda: _leaf_glow_art(1),
    "sliding_door_leaf_upper_glow": lambda: _leaf_glow_art(0),
    "sliding_door_jamb": sliding_door_jamb,
    "sliding_door_header": sliding_door_header,
    "iris_hatch_rim": iris_hatch_rim,
    "iris_hatch_rim_glow": iris_hatch_rim_glow,
    "iris_hatch_side": iris_hatch_side,
    "iris_hatch_closed": iris_hatch_closed,
    "iris_hatch_blade": iris_hatch_blade,
    "blast_door_edge": blast_door_edge,
    "pressure_trapdoor_top": pressure_trapdoor_top,
    "pressure_trapdoor_bottom": pressure_trapdoor_bottom,
    "pressure_trapdoor_side": pressure_trapdoor_side,
    "airlock_frame": airlock_frame,
    "airlock_frame_frame": airlock_frame_frame,
    "airlock_frame_glow": airlock_frame_glow,
    "door_status_light": door_status_light,
    "door_status_light_glow": door_status_light_glow,
    "access_pad": access_pad,
    "access_pad_glow": access_pad_glow,
    "viewport_frame": viewport_frame,
    "viewport_frame_frame": viewport_frame_frame,
    "porthole_rim": porthole_rim,
    "porthole_side": porthole_side,
    "porthole_inner": porthole_inner,
    "porthole_glass": porthole_glass,
    "canopy_glass_edge": canopy_glass_edge,
    "shutter": shutter,
    "shutter_foot": shutter_foot,
    "shutter_slat": shutter_slat,
    "shutter_housing": shutter_housing,
    "shutter_rail": shutter_rail,
}
for _n in range(9):
    TEXTURES["blast_door_%d" % _n] = _blast_tile(_n)
for _n in (3, 4, 5):
    TEXTURES["blast_door_%d_glow" % _n] = _blast_tile_glow(_n)
for _k in ("c00", "c01", "c10", "c11"):
    TEXTURES["canopy_glass_" + _k] = (lambda k=_k: _canopy()["_" + k])
for _m in range(16):
    TEXTURES["smart_glass_f%d" % _m] = _sg_painter("clear", "_f%d" % _m)
    TEXTURES["smart_glass_glow_f%d" % _m] = _sg_painter("clear", "_glow_f%d" % _m)
    TEXTURES["smart_glass_frost_f%d" % _m] = _sg_painter("frost", "_f%d" % _m)
    TEXTURES["smart_glass_frost_glow_f%d" % _m] = _sg_painter("frost", "_glow_f%d" % _m)


def _same(*ids):
    return {i: i for i in ids}


def _prefixed(prefix):
    return {k: k for k in TEXTURES if k.startswith(prefix)}


BLOCKS = [
    Block("sliding_door", shape="custom", emissive=True, block_entity=True,
          textures=_prefixed("sliding_door_"), model=sliding_door_models, blockstate=sliding_door_state,
          item_model="sliding_door_inventory", name="Sliding Door"),
    Block("iris_hatch", shape="custom", emissive=True, block_entity=True,
          textures=_prefixed("iris_hatch_"), model=iris_hatch_models, blockstate=iris_hatch_state,
          item_model="iris_hatch", name="Iris Hatch"),
    Block("blast_door", shape="custom", emissive=True, block_entity=True,
          textures=_prefixed("blast_door_"), model=blast_door_models, blockstate=blast_door_state,
          item_model="blast_door_inventory", name="Blast Door"),
    Block("pressure_trapdoor", shape="custom", block_entity=True,
          textures=_prefixed("pressure_trapdoor_"), model=pressure_trapdoor_models,
          blockstate=pressure_trapdoor_state, item_model="pressure_trapdoor_bottom",
          name="Pressure Trapdoor"),
    Block("airlock_frame", shape="cube", connects="mask16", emissive=True,
          textures={"all": "airlock_frame"}, name="Airlock Frame"),
    Block("door_status_light", shape="custom", chroma=True, emissive=True, light=7,
          textures=_same("door_status_light", "door_status_light_glow"),
          model=door_status_light_models, blockstate=_mount_state("door_status_light"),
          name="Door Status Light"),
    Block("access_pad", shape="custom", emissive=True,
          textures=_same("access_pad", "access_pad_glow"), model=access_pad_models,
          blockstate=_mount_state("access_pad"), name="Access Pad"),
    Block("viewport_frame", shape="cube", connects="mask16", textures={"all": "viewport_frame"},
          render_type="minecraft:translucent", name="Viewport Frame"),
    Block("porthole", shape="custom", textures=_prefixed("porthole_"), model=porthole_models,
          blockstate=porthole_state, name="Porthole"),
    Block("canopy_glass", shape="custom", connects="strip", textures=_prefixed("canopy_glass_"),
          model=canopy_glass_models, blockstate=canopy_glass_state, item_model="canopy_glass_c11",
          name="Canopy Glass"),
    Block("shutter", shape="custom", connects="strip", block_entity=True,
          textures=_same("shutter", "shutter_foot", "shutter_slat", "shutter_housing", "shutter_rail"),
          model=shutter_models, blockstate=shutter_state, item_model="shutter_c11", name="Shutter"),
    Block("smart_glass", shape="custom", connects="mask16", chroma=True, emissive=True,
          textures=_prefixed("smart_glass_"), model=lambda c: _sg_build(c)[0],
          blockstate=lambda c: _sg_build(c)[1], item_model="smart_glass_inventory",
          render_type="minecraft:translucent", name="Smart Glass"),
]
