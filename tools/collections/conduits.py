"""
Conduits collection -- pipes, ducts, cable runs and an emitter head (9 blocks).

Blockstate contract (the Java side must expose exactly these properties):

  decor_pipe, coolant_tube   north/south/east/west/up/down (booleans, "connected on that side").
                             Multipart: one ARM model per connected side, plus per unconnected
                             side one CORE face chosen from that side's four in-plane neighbours
                             (pipe band continuing along the run, or a joint plate where two runs
                             meet), a flanged END stub on the far side of a dead end, and one
                             flanged LONE segment when nothing connects (also the item model).
                             Both share one 6px bore (5..11), so they join each other.
  pipe_valve, pipe_gauge     axis (x/y/z). Multipart: a body plus the two decor_pipe arms on that
                             axis, so they join a pipe run at either end.
  cable_tray                 face, axis, pos_link, neg_link -- same meaning as the kit strip
                             (face = mounting side, axis = run, link = same tray continues).
  hanging_cables             pos_link (same block above), neg_link (same block below).
  duct                       axis, pos_link, neg_link (seam flange baked at unlinked ends only).
  duct_fan                   facing (six-way; the fan face).
  beam_emitter_prop          facing (six-way; the lens).

UV rule used throughout: along the run a texture coordinate equals the world coordinate (fuv with
ident=axis), so bands, seams and the coolant flow line up across arms, cores and blocks and the
flow travels towards +axis on every face. Textures whose run is vertical on a face use a transposed
twin (`_v` / `_t`). Nothing is rotated per face and no partial quad overlays another (z-fight rule).
"""
import math

from ._kit import (Art, Block, animate, bevel, cap_layer, expand_strip, facing_variants, lerp, new,
                   put, ramp, ramp5, rect, strip_rotation, transpose, wear)

COLLECTION = "conduits"

# --- ramps -------------------------------------------------------------------------------------
BODY = ramp("body")                                   # house near-black navy
STATION = ramp("station")
MET = [BODY[1], BODY[2], BODY[3], BODY[4], STATION[3]]        # pipe / sheet metal
FIT = [BODY[2], BODY[3], BODY[4], STATION[3], STATION[4]]     # fittings: flanges, joints, bezels
ALLOY = ramp("nero_alloy")                            # teal marking (non-emissive accent)
PLASMA = ramp("plasma")                               # gauge needle glow
MARS = ramp("mars")                                   # valve handwheel
GLASS = ramp5((84, 92, 106))                          # chroma glass: neutral, the hub tints it
NEUTRAL = ramp5((222, 232, 242))                      # chroma glow: near-white

# --- geometry / uv helpers ---------------------------------------------------------------------
AX = {"x": 0, "y": 1, "z": 2}
DIRS = {"north": ("z", -1), "south": ("z", 1), "west": ("x", -1), "east": ("x", 1),
        "down": ("y", -1), "up": ("y", 1)}
OPP = {"north": "south", "south": "north", "west": "east", "east": "west", "up": "down", "down": "up"}
# face -> (u axis, u sign, v axis, v sign) under vanilla auto-UV (left->right, top->bottom)
_FA = {"north": ("x", -1, "y", -1), "south": ("x", 1, "y", -1), "west": ("z", 1, "y", -1),
       "east": ("z", -1, "y", -1), "up": ("x", 1, "z", 1), "down": ("x", 1, "z", -1)}


def dirs_of(axis):
    neg = [d for d, (a, s) in DIRS.items() if a == axis and s < 0][0]
    return neg, OPP[neg]


def fuv(face, frm, to, ident=()):
    """UV for one face. Axes named in `ident` map texture coordinate = world coordinate (so a
    pattern runs the same way on every face); the others keep the vanilla auto-UV."""
    ua, us, va, vs = _FA[face]

    def edge(axis, sign, first):
        lo, hi = frm[AX[axis]], to[AX[axis]]
        w = (lo if first else hi) if sign > 0 else (hi if first else lo)
        return w if (axis in ident or sign > 0) else 16 - w
    return [edge(ua, us, True), edge(va, vs, True), edge(ua, us, False), edge(va, vs, False)]


def run_faces(axis, frm, to, th, tv):
    """The four faces around a run along `axis`: `th` where the run lies along texture u, `tv`
    (the transposed twin) where it lies along v."""
    out = {}
    for f, (fax, _s) in DIRS.items():
        if fax != axis:
            out[f] = {"texture": th if _FA[f][0] == axis else tv, "uv": fuv(f, frm, to, (axis,))}
    return out


def box(axis, a0, a1, lo=5, hi=11):
    frm, to = [lo] * 3, [hi] * 3
    frm[AX[axis]], to[AX[axis]] = a0, a1
    return frm, to


def R(x, y, w, h):
    return [x, y, x + w, y + h]


def mdl(c, elements, tex, rt=None, particle=None):
    """Model json listing only the textures its elements use. tex = {key: texture id}."""
    used = {f["texture"][1:] for e in elements for f in e["faces"].values()}
    tx = {"particle": c.tex(particle or tex[sorted(used)[0]])}
    for k in sorted(used):
        tx[k] = c.tex(tex[k])
    m = {"textures": tx, "elements": elements}
    if rt:
        m["render_type"] = rt
    return m


# =============================================================================================
# Six-way pipe family (decor_pipe, coolant_tube)
# =============================================================================================
def _plate(c, axis, a0, a1, outward):
    """8x8 flange plate, 1px thick: both big faces show the flange/mouth, the rim a fitting strip."""
    frm, to = box(axis, a0, a1, 4, 12)
    faces = {}
    for f, (fax, _s) in DIRS.items():
        if fax == axis:
            faces[f] = {"texture": "#end", "uv": fuv(f, frm, to)}
        else:
            faces[f] = {"texture": "#joint", "uv": R(0, 4, 1, 8) if _FA[f][0] == axis else R(4, 0, 8, 1)}
    return c.el(frm, to, faces, cull=False, tint=False)


def _run(c, axis, a0, a1, glow, tint, end=None):
    frm, to = box(axis, a0, a1)
    faces = run_faces(axis, frm, to, "#h", "#v")
    if end:
        faces[end] = {"texture": "#end", "uv": fuv(end, frm, to), "cullface": end}
    els = [c.el(frm, to, faces, cull=False, tint=False if end and not tint else tint)]
    if end and tint:  # keep the metal mouth untinted: split it off as its own (non-overlapping) face
        mouth = faces.pop(end)
        els = [c.el(frm, to, faces, cull=False, tint=True),
               c.el(frm, to, {end: mouth}, cull=False, tint=False)]
    if glow:
        els.append(c.el(frm, to, run_faces(axis, frm, to, "#gh", "#gv"), cull=False, emit=15))
    return els


def pipe_family(c, P, glow=False, tint=False):
    tex = {"h": P, "v": P + "_v", "end": "decor_pipe_end", "joint": P + "_joint",
           "gh": P + "_glow", "gv": P + "_glow_v", "gj": P + "_joint_glow"}
    rt = "minecraft:cutout" if glow else None
    M = {}
    # lone segment (x run, flanged both ends) -- first, so it is the item model
    M[P] = mdl(c, [_plate(c, "x", 2, 3, "west")] + _run(c, "x", 3, 13, glow, tint)
               + [_plate(c, "x", 13, 14, "east")], tex, rt, P)
    core = ([5, 5, 5], [11, 11, 11])
    for d, (axis, sign) in DIRS.items():
        a0, a1 = (11, 16) if sign > 0 else (0, 5)
        M["%s_arm_%s" % (P, d)] = mdl(c, _run(c, axis, a0, a1, glow, tint, end=d), tex, rt, P)
        # dead-end stub: 2px neck + flange plate on the side opposite the only connection
        n0, n1, p0, p1 = (11, 13, 13, 14) if sign > 0 else (3, 5, 2, 3)
        M["%s_end_%s" % (P, d)] = mdl(c, _run(c, axis, n0, n1, glow, tint) + [_plate(c, axis, p0, p1, d)],
                                      tex, rt, P)
        # core faces: joint plate, or the band continuing along either in-plane axis
        els = [c.el(core[0], core[1], {d: {"texture": "#joint", "uv": fuv(d, *core)}}, cull=False, tint=False)]
        if glow:
            els.append(c.el(core[0], core[1], {d: {"texture": "#gj", "uv": fuv(d, *core)}}, cull=False, emit=15))
        M["%s_joint_%s" % (P, d)] = mdl(c, els, tex, rt, P)
        for a in AX:
            if a == axis:
                continue
            face = {d: run_faces(a, core[0], core[1], "#h", "#v")[d]}
            els = [c.el(core[0], core[1], face, cull=False, tint=tint)]
            if glow:
                els.append(c.el(core[0], core[1], {d: run_faces(a, core[0], core[1], "#gh", "#gv")[d]},
                                cull=False, emit=15))
            M["%s_core_%s_%s" % (P, d, a)] = mdl(c, els, tex, rt, P)
    return M


def pipe_blockstate(c, P):
    parts = [{"when": {d: "false" for d in DIRS}, "apply": {"model": c.model(P)}}]
    for d, (axis, _s) in DIRS.items():
        parts.append({"when": {d: "true"}, "apply": {"model": c.model("%s_arm_%s" % (P, d))}})
        a, b = [x for x in AX if x != axis]
        for run, other in ((a, b), (b, a)):
            o1, o2 = dirs_of(other)
            parts.append({"when": {"OR": [{d: "false", o1: "false", o2: "false", r: "true"}
                                          for r in dirs_of(run)]},
                          "apply": {"model": c.model("%s_core_%s_%s" % (P, d, run))}})
        parts.append({"when": {"OR": [{d: "false", r: "true", o: "true"}
                                      for r in dirs_of(a) for o in dirs_of(b)]},
                      "apply": {"model": c.model("%s_joint_%s" % (P, d))}})
        when = {x: "false" for x in DIRS}
        when[OPP[d]] = "true"
        parts.append({"when": when, "apply": {"model": c.model("%s_end_%s" % (P, d))}})
    return {"multipart": parts}


# --- pipe textures --------------------------------------------------------------------------
BAND = (3, 2, 2, 2, 1, 0)     # ramp index per band row (rows 5..10): 1px highlight .. 1px occlusion


def _tile():
    """Dark hull backing for the unused part of a tile (also what the particle picks up)."""
    img = new(16, 16, BODY[1])
    bevel(img, 0, 0, 16, 16, BODY, fill=1)
    return img


def decor_pipe():
    img = _tile()
    for i, k in enumerate(BAND):
        y = 5 + i
        for x in range(16):
            put(img, x, y, MET[k])
        put(img, 0, y, MET[min(4, k + 1)])            # coupling seam on the block boundary
        put(img, 15, y, MET[max(0, k - 1)])
        put(img, 3, y, ALLOY[(3, 2, 2, 2, 1, 0)[i]])  # teal service band
        put(img, 4, y, MET[max(0, k - 1)])            # its cast shadow
    put(img, 6, 5, MET[4])
    put(img, 7, 5, MET[4])
    put(img, 12, 5, MET[4])
    return wear(img, "decor_pipe", MET, rate=0.05, region=(0, 5, 16, 11))


def decor_pipe_end():
    """Concentric: 10x10 valve flange (3..13), 8x8 pipe flange (4..12), 6x6 mouth (5..11)."""
    img = _tile()
    bevel(img, 3, 3, 13, 13, FIT, fill=1)
    bevel(img, 4, 4, 12, 12, FIT, fill=2)
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
        put(img, x, y, FIT[4] if (x, y) == (4, 4) else FIT[0])     # bolts, lit from the top-left
    bevel(img, 5, 5, 11, 11, MET, fill=2)
    rect(img, 6, 6, 10, 10, BODY[0])                               # bore
    for i in range(6, 10):
        put(img, i, 9, BODY[1])
        put(img, 9, i, BODY[1])
    put(img, 9, 9, BODY[2])
    return img


def decor_pipe_joint():
    """6x6 fitting plate (5..11) + the flange rim strips (row 0 / column 0)."""
    img = _tile()
    bevel(img, 5, 5, 11, 11, FIT, fill=2)
    put(img, 7, 7, FIT[3])
    put(img, 8, 7, FIT[1])
    put(img, 7, 8, FIT[1])
    put(img, 8, 8, FIT[0])
    for i in range(4, 12):
        put(img, i, 0, FIT[3] if i < 11 else FIT[1])
        put(img, 0, i, FIT[3] if i < 11 else FIT[1])
    put(img, 4, 0, FIT[4])
    put(img, 0, 4, FIT[4])
    return wear(img, "decor_pipe_joint", FIT, rate=0.04, region=(5, 5, 11, 11))


GBAND = (3, 1, 2, 2, 1, 0)    # glass: highlight, wall, fluid channel x2, wall, occlusion


def coolant_tube():
    img = _tile()
    for i, k in enumerate(GBAND):
        y = 5 + i
        for x in range(16):
            put(img, x, y, GLASS[k])
        put(img, 0, y, GLASS[min(4, k + 1)])
        put(img, 15, y, GLASS[max(0, k - 1)] if k else GLASS[1])
    for x in (2, 3, 9):
        put(img, x, 5, GLASS[4])                      # glass glints
    for x in (5, 6, 12):
        put(img, x, 8, GLASS[1])                      # slow eddies in the fluid
    return img


def _flow():
    """Coolant flow: a 7px comet and a 3px blip, 1px per frame towards +u, 16-frame loop."""
    prof = {0: 2, 1: 2, 2: 3, 3: 3, 4: 3, 5: 4, 6: 4, 9: 2, 10: 3, 11: 4}

    def frame(t):
        off = int(round(t * 16)) % 16
        img = new()
        for x in range(16):
            for row, lag in ((7, 0), (8, 1)):
                ph = (x - off + lag) % 16
                if ph in prof and not (row == 8 and ph in (0, 9)):
                    put(img, x, row, NEUTRAL[prof[ph]])
        return img
    return animate("coolant_tube_glow", "flow", frame)


def coolant_tube_glow():
    return _flow()


def coolant_tube_glow_v():
    a = _flow()
    return Art([transpose(f) for f in a.frames], a.anim, a.interpolate)


def coolant_tube_joint():
    """Metal manifold plate with a 2x2 sight window (the glow layer lights it)."""
    img = _tile()
    bevel(img, 5, 5, 11, 11, FIT, fill=2)
    rect(img, 6, 6, 10, 10, FIT[1])
    for i in range(6, 10):
        put(img, i, 6, FIT[0])
        put(img, 6, i, FIT[0])
    rect(img, 7, 7, 9, 9, BODY[0])
    for i in range(4, 12):
        put(img, i, 0, FIT[3] if i < 11 else FIT[1])
        put(img, 0, i, FIT[3] if i < 11 else FIT[1])
    put(img, 4, 0, FIT[4])
    put(img, 0, 4, FIT[4])
    return img


def coolant_tube_joint_glow():
    def frame(t):
        k = 0.5 + 0.5 * math.cos(2 * math.pi * t)
        img = new()
        put(img, 7, 7, lerp(NEUTRAL[3], NEUTRAL[4], k))
        put(img, 8, 7, lerp(NEUTRAL[2], NEUTRAL[4], k))
        put(img, 7, 8, lerp(NEUTRAL[2], NEUTRAL[3], k))
        put(img, 8, 8, lerp(NEUTRAL[1], NEUTRAL[3], k))
        return img
    return animate("coolant_tube_joint_glow", "breathing", frame)


# =============================================================================================
# pipe_valve / pipe_gauge (axis; body + the decor_pipe arms)
# =============================================================================================
def _stubs(c):
    """The two x arms as plain elements (item models only)."""
    return _run(c, "x", 0, 5, False, False, end="west") + _run(c, "x", 11, 16, False, False, end="east")


def _axis_multipart(c, body):
    """body = {axis: variant dict}. Adds the decor_pipe arm models for that axis."""
    parts = []
    for axis in ("x", "y", "z"):
        parts.append({"when": {"axis": axis}, "apply": body[axis]})
        for d in dirs_of(axis):
            parts.append({"when": {"axis": axis}, "apply": {"model": c.model("decor_pipe_arm_" + d)}})
    return {"multipart": parts}


def _valve_body(c):
    els = []
    for x0 in (3, 11):                                         # 10x10 flanges
        frm, to = [x0, 3, 3], [x0 + 2, 13, 13]
        faces = {f: {"texture": "#end", "uv": fuv(f, frm, to)} for f in ("west", "east")}
        faces.update({f: {"texture": "#valve", "uv": fuv(f, frm, to)} for f in ("up", "down", "north", "south")})
        els.append(c.el(frm, to, faces, cull=False))
    frm, to = [5, 4, 4], [11, 12, 12]                          # body
    els.append(c.el(frm, to, {f: {"texture": "#valve", "uv": fuv(f, frm, to)}
                              for f in ("up", "down", "north", "south")}, cull=False))
    frm, to = [7, 12, 7], [9, 14, 9]                           # bonnet
    els.append(c.el(frm, to, {f: {"texture": "#valve", "uv": R(7, 2, 2, 2)}
                              for f in ("north", "south", "east", "west")}, cull=False))
    frm, to = [4, 14, 4], [12, 15, 12]                         # handwheel (cutout disc)
    faces = {f: {"texture": "#wheel", "uv": fuv(f, frm, to)} for f in ("up", "down")}
    faces.update({f: {"texture": "#wheel", "uv": R(4, 0, 8, 1)} for f in ("north", "south", "east", "west")})
    els.append(c.el(frm, to, faces, cull=False))
    frm, to = [7, 15, 7], [9, 16, 9]                           # hub nut
    faces = {f: {"texture": "#valve", "uv": R(7, 1, 2, 1)} for f in ("north", "south", "east", "west")}
    faces["up"] = {"texture": "#valve", "uv": R(7, 0, 2, 2)}
    els.append(c.el(frm, to, faces, cull=False))
    return els


_VT = {"valve": "pipe_valve", "wheel": "pipe_valve_wheel", "end": "decor_pipe_end",
       "h": "decor_pipe", "v": "decor_pipe_v"}


def valve_models(c):
    body = _valve_body(c)
    return {"pipe_valve": mdl(c, _stubs(c) + body, _VT, "minecraft:cutout", "pipe_valve"),
            "pipe_valve_body": mdl(c, body, _VT, "minecraft:cutout", "pipe_valve")}


def valve_blockstate(c):
    m = c.model("pipe_valve_body")
    return _axis_multipart(c, {"x": {"model": m}, "z": {"model": m, "y": 90}, "y": {"model": m, "x": 270}})


def pipe_valve():
    img = _tile()
    bevel(img, 5, 4, 11, 12, MET, fill=2)                      # body elevation
    for x in range(6, 10):
        put(img, x, 7, MET[1])                                 # pressed groove
        put(img, x, 8, MET[3])
    put(img, 6, 10, MET[4])                                    # rivets
    put(img, 9, 10, MET[1])
    for x0 in (3, 11):                                         # flange rims
        for y in range(3, 13):
            put(img, x0, y, FIT[3])
            put(img, x0 + 1, y, FIT[1])
        put(img, x0, 3, FIT[4])
        put(img, x0 + 1, 12, FIT[0])
        for y in (5, 8, 11):
            put(img, x0, y, FIT[2])                            # bolt notches
            put(img, x0 + 1, y, FIT[0])
    put(img, 7, 2, FIT[3])                                     # bonnet stem
    put(img, 8, 2, FIT[1])
    put(img, 7, 3, FIT[2])
    put(img, 8, 3, FIT[0])
    put(img, 7, 0, MARS[4])                                    # hub nut
    put(img, 8, 0, MARS[2])
    put(img, 7, 1, MARS[2])
    put(img, 8, 1, MARS[0])
    return wear(img, "pipe_valve", MET, rate=0.05, region=(5, 4, 11, 12))


def pipe_valve_wheel():
    img = new()
    rim = ["..XXXX..", ".X....X.", "X......X", "X......X", "X......X", "X......X", ".X....X.", "..XXXX.."]
    for y, row in enumerate(rim):
        for x, ch in enumerate(row):
            if ch == "X":
                k = 3 if (x + y) < 6 else (1 if (x + y) > 8 else 2)
                put(img, 4 + x, 4 + y, MARS[k])
    put(img, 6, 4, MARS[4])
    for x, y in ((2, 2), (5, 2), (2, 5), (5, 5)):              # spokes
        put(img, 4 + x, 4 + y, MARS[2] if (x, y) != (5, 5) else MARS[1])
    put(img, 7, 7, MARS[4])                                    # hub
    put(img, 8, 7, MARS[2])
    put(img, 7, 8, MARS[2])
    put(img, 8, 8, MARS[0])
    for x, k in ((6, 3), (7, 2), (8, 2), (9, 1)):              # rim edge strip (row 0)
        put(img, x, 0, MARS[k])
    return img


_GT = {"gauge": "pipe_gauge", "gauge_v": "pipe_gauge_v", "dial": "pipe_gauge_dial",
       "glow": "pipe_gauge_dial_glow", "end": "decor_pipe_end", "h": "decor_pipe", "v": "decor_pipe_v"}


def _gauge_body(c, axis):
    t = "#gauge" if axis == "x" else "#gauge_v"
    frm, to = ([5, 4, 4], [11, 12, 12]) if axis == "x" else ([4, 5, 4], [12, 11, 12])
    ends = dirs_of(axis)
    faces = {f: {"texture": "#end", "uv": fuv(f, frm, to)} for f in ends}
    faces.update({f: {"texture": t, "uv": fuv(f, frm, to)}
                  for f in DIRS if f not in ends and f != "south"})
    els = [c.el(frm, to, faces, cull=False)]                   # collar
    frm, to = [4, 4, 12], [12, 12, 14]                         # dial housing, always faces south
    faces = {f: {"texture": "#gauge", "uv": fuv(f, frm, to)} for f in ("up", "down", "east", "west")}
    faces["south"] = {"texture": "#dial", "uv": fuv("south", frm, to)}
    els.append(c.el(frm, to, faces, cull=False))
    els.append(c.el(frm, to, {"south": {"texture": "#glow", "uv": fuv("south", frm, to)}}, cull=False, emit=15))
    return els


def gauge_models(c):
    bx, by = _gauge_body(c, "x"), _gauge_body(c, "y")
    return {"pipe_gauge": mdl(c, _stubs(c) + bx, _GT, "minecraft:cutout", "pipe_gauge"),
            "pipe_gauge_body_x": mdl(c, bx, _GT, "minecraft:cutout", "pipe_gauge"),
            "pipe_gauge_body_y": mdl(c, by, _GT, "minecraft:cutout", "pipe_gauge")}


def gauge_blockstate(c):
    x, y = c.model("pipe_gauge_body_x"), c.model("pipe_gauge_body_y")
    return _axis_multipart(c, {"x": {"model": x}, "z": {"model": x, "y": 90}, "y": {"model": y}})


def pipe_gauge():
    img = _tile()
    bevel(img, 5, 4, 11, 12, MET, fill=2)                      # collar elevation
    for y in (6, 9):
        for x in range(6, 10):
            put(img, x, y, MET[1])                             # clamp grooves
            put(img, x, y + 1, MET[3])
    put(img, 6, 5, MET[4])
    for i in range(4, 12):                                     # dial-housing rim strips (4 sides)
        lit = FIT[3] if i < 11 else FIT[1]
        put(img, i, 12, lit)
        put(img, i, 13, FIT[1])
        put(img, i, 2, FIT[2])
        put(img, i, 3, FIT[0])
        put(img, 12, i, lit)
        put(img, 13, i, FIT[1])
        put(img, 2, i, FIT[2])
        put(img, 3, i, FIT[0])
    return wear(img, "pipe_gauge", MET, rate=0.05, region=(5, 4, 11, 12))


def pipe_gauge_v():
    return transpose(pipe_gauge())


def pipe_gauge_dial():
    img = _tile()
    bevel(img, 4, 4, 12, 12, FIT, fill=2)                      # bezel
    rect(img, 5, 5, 11, 11, BODY[0])                           # dial face
    for x, y in ((5, 5), (10, 5), (5, 10), (10, 10)):
        put(img, x, y, FIT[1])                                 # rounded corners
    for x in range(6, 10):
        put(img, x, 10, BODY[1])                               # lower scale plate
    put(img, 7, 9, FIT[2])                                     # pivot
    put(img, 8, 9, FIT[0])
    return img


def pipe_gauge_dial_glow():
    needles = (((7, 8), (6, 7), (6, 6)), ((7, 8), (7, 7), (7, 6)),
               ((8, 8), (8, 7), (9, 6)), ((7, 8), (7, 7), (7, 6)))

    def frame(t):
        img = new()
        for x, y in ((5, 7), (6, 5), (9, 5), (10, 7)):         # scale ticks
            put(img, x, y, PLASMA[1])
        n = needles[int(round(t * 4)) % 4]
        for (x, y), k in zip(n, (2, 3, 3)):
            put(img, x, y, PLASMA[k])
        return img
    return animate("pipe_gauge_dial_glow", "flicker", frame)


# =============================================================================================
# cable_tray (custom strip: face + axis + pos_link/neg_link; both canonicals, as the kit strip)
# =============================================================================================
def _tray(c, canon, p, n):
    def P(r0, c0, h0, r1, c1, h1):
        return ([r0, h0, c0], [r1, h1, c1]) if canon == "" else ([c0, h0, r0], [c1, h1, r1])
    top = "#top" if canon == "" else "#top_t"
    sides = ("north", "south") if canon == "" else ("west", "east")   # faces along the run
    ends = ("west", "east") if canon == "" else ("north", "south")    # -run end, +run end
    r0, r1 = (1 if n else 0), (15 if p else 16)
    els = []
    frm, to = P(r0, 3, 0, r1, 13, 1)                                  # floor
    faces = {"up": {"texture": top, "uv": fuv("up", frm, to)},
             "down": {"texture": top, "uv": fuv("down", frm, to), "cullface": "down"}}
    faces.update({f: {"texture": "#top", "uv": [r0, 15, r1, 16]} for f in sides})
    faces.update({f: {"texture": "#top", "uv": R(3, 15, 10, 1)} for f in ends})
    els.append(c.el(frm, to, faces, cull=False))
    for c0 in (3, 12):                                                # side walls
        frm, to = P(r0, c0, 1, r1, c0 + 1, 3)
        faces = {"up": {"texture": top, "uv": fuv("up", frm, to)}}
        faces.update({f: {"texture": "#top", "uv": [r0, 13, r1, 15]} for f in sides})
        faces.update({f: {"texture": "#top", "uv": R(0, 13, 1, 2)} for f in ends})
        els.append(c.el(frm, to, faces, cull=False))
    frm, to = P(r0, 5, 1, r1, 11, 2)                                  # cable bundle
    faces = {"up": {"texture": top, "uv": fuv("up", frm, to)},
             sides[0]: {"texture": "#top", "uv": [r0, 5, r1, 6]},
             sides[1]: {"texture": "#top", "uv": [r0, 10, r1, 11]}}
    faces.update({f: {"texture": "#top", "uv": R(5, 0, 6, 1)} for f in ends})
    els.append(c.el(frm, to, faces, cull=False))
    for on, ra in ((n, 0), (p, 15)):                                  # end hoops at unlinked ends
        if not on:
            continue
        frm, to = P(ra, 2, 0, ra + 1, 14, 4)
        faces = {f: {"texture": "#cap", "uv": R(2, 12, 12, 4)} for f in ends}
        faces.update({f: {"texture": "#cap", "uv": R(1, 0, 1, 4)} for f in sides})
        strip = R(0, 0, 1, 12) if canon == "" else R(2, 11, 12, 1)
        faces["up"] = {"texture": "#cap", "uv": strip}
        faces["down"] = {"texture": "#cap", "uv": strip, "cullface": "down"}
        els.append(c.el(frm, to, faces, cull=False))
    return mdl(c, els, {"top": "cable_tray", "top_t": "cable_tray_t", "cap": "cable_tray_cap"},
               None, "cable_tray")


def tray_models(c):
    M = {}
    for canon in ("", "_z"):
        for p in (1, 0):
            for n in (1, 0):
                M["cable_tray_c%d%d%s" % (p, n, canon)] = _tray(c, canon, p, n)
    return M


def _link_variants(c, name, pairs):
    """Strip-style variants: pairs = [(face, axis)] -> key 'axis=..,face=..,neg_link=..,pos_link=..'."""
    out = {}
    for face, axis in pairs:
        canon, rx, ry, flip = strip_rotation(face, axis)
        for pos in (False, True):
            for neg in (False, True):
                cp, cn = int(not pos), int(not neg)
                if flip:
                    cp, cn = cn, cp
                v = {"model": c.model("%s_c%d%d%s" % (name, cp, cn, canon))}
                if rx:
                    v["x"] = rx
                if ry:
                    v["y"] = ry
                out["axis=%s,face=%s,neg_link=%s,pos_link=%s"
                    % (axis, face, str(neg).lower(), str(pos).lower())] = v
    return out


def tray_blockstate(c):
    pairs = [(f, a) for f in DIRS for a in AX if DIRS[f][0] != a]
    return {"variants": _link_variants(c, "cable_tray", pairs)}


def cable_tray():
    """Top-down picture of the tray (run along u): rows 3..12; strips for the sides outside it."""
    img = _tile()
    for x in range(16):
        put(img, x, 3, MET[3])                                # far wall top (lit)
        put(img, x, 12, MET[2])                               # near wall top
        put(img, x, 4, BODY[0])                               # floor in the wall's shadow
        put(img, x, 11, BODY[2] if x % 4 else BODY[0])        # perforated floor
        put(img, x, 5, MET[3])                                # cable 1 (navy jacket)
        put(img, x, 6, MET[1])
        put(img, x, 7, ALLOY[2])                              # cable 2 (teal data line)
        put(img, x, 8, ALLOY[0])
        put(img, x, 9, STATION[2])                            # cable 3 (thin steel braid)
        put(img, x, 10, BODY[0])
        put(img, x, 13, MET[3])                               # wall side strip
        put(img, x, 14, MET[1] if x % 4 != 1 else BODY[0])    # slotted
        put(img, x, 15, MET[0])                               # floor edge
    for x in (5, 13):
        put(img, x, 7, ALLOY[3])                              # glints
        put(img, x - 3, 9, STATION[3])
        put(img, x - 1, 5, MET[4])
    for y, k in ((5, 3), (6, 2), (7, 3), (8, 1), (9, 2), (10, 0)):
        put(img, 8, y, FIT[k])                                # cable tie
        put(img, 9, y, FIT[0])
    for x, col in zip(range(5, 11), (MET[2], MET[1], ALLOY[2], ALLOY[1], STATION[2], BODY[0])):
        put(img, x, 0, col)                                   # cable end strip
    return wear(img, "cable_tray", MET, rate=0.04, region=(0, 3, 16, 13))


def cable_tray_t():
    return transpose(cable_tray())


def cable_tray_cap():
    img = _tile()
    bevel(img, 2, 12, 14, 16, FIT, fill=2)                    # hoop face
    rect(img, 5, 13, 11, 15, BODY[0])                         # grommet with the three cable ends
    for x, col in ((5, MET[2]), (6, MET[2]), (7, ALLOY[2]), (8, ALLOY[2]), (9, STATION[2])):
        put(img, x, 14, col)
    put(img, 3, 13, FIT[4])
    put(img, 12, 14, FIT[0])
    for i in range(12):
        put(img, 0, i, FIT[3])                                # rim strips
        put(img, 2 + i, 11, FIT[3])
    for i in range(4):
        put(img, 1, i, FIT[2])
    return img


# =============================================================================================
# hanging_cables (crossed cutout planes; pos_link above, neg_link below)
# =============================================================================================
def _cables(top, bottom):
    def paint():
        img = new()
        # (x, width, ramp, jog rows, end row when the run stops here)
        runs = ((2, 2, MET, None, 13), (6, 1, ALLOY, (5, 11), 9), (9, 2, MET, None, 15),
                (13, 1, STATION, (8, 13), 6))
        for x, w, r, jog, end in runs:
            for y in range(end if bottom else 16):
                xx = x + (1 if jog and jog[0] <= y < jog[1] else 0)
                if w == 2:
                    put(img, xx, y, r[3])
                    put(img, xx + 1, y, r[1])
                else:
                    put(img, xx, y, r[2] if (y + x) % 5 else r[3])
            if bottom:                                       # plug / cut tip
                xx = x + (1 if jog and jog[0] <= end - 1 < jog[1] else 0)
                if w == 2:
                    put(img, xx, end, FIT[3])
                    put(img, xx + 1, end, FIT[1])
                    put(img, xx, end + 1, FIT[2]) if end + 1 < 16 else None
                    put(img, xx + 1, end + 1, FIT[0]) if end + 1 < 16 else None
                else:
                    put(img, xx, end, r[4])
        for x in range(9, 14):                               # tie between the two right-hand runs
            put(img, x, 3, FIT[2] if x < 13 else FIT[0])
        if top:                                              # ceiling rail
            for x in range(1, 15):
                put(img, x, 0, FIT[3])
                put(img, x, 1, FIT[1])
            put(img, 1, 0, FIT[4])
            put(img, 14, 1, FIT[0])
        return img
    return paint


def cables_models(c):
    M = {}
    for p in (1, 0):
        for n in (1, 0):
            tid = "hanging_cables_c%d%d" % (p, n)
            els = []
            for frm, to, faces in (([0.8, 0, 8], [15.2, 16, 8], ("north", "south")),
                                   ([8, 0, 0.8], [8, 16, 15.2], ("west", "east"))):
                e = c.el(frm, to, {f: {"texture": "#cable", "uv": [0, 0, 16, 16]} for f in faces}, cull=False)
                e["shade"] = False
                e["rotation"] = {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}
                els.append(e)
            M[tid] = mdl(c, els, {"cable": tid}, "minecraft:cutout", tid)
    return M


def cables_blockstate(c):
    return {"variants": {"neg_link=%s,pos_link=%s" % (str(neg).lower(), str(pos).lower()):
                         {"model": c.model("hanging_cables_c%d%d" % (int(not pos), int(not neg)))}
                         for pos in (False, True) for neg in (False, True)}}


# =============================================================================================
# duct (full cube, axis + pos_link/neg_link) and duct_fan (facing)
# =============================================================================================
def _duct_side():
    img = new(16, 16, MET[2])
    for x in range(16):
        put(img, x, 0, MET[3])
        put(img, x, 1, MET[2])
        put(img, x, 14, MET[1])
        put(img, x, 15, MET[0])
    for x in range(16):                                       # pressed cross-brace (X crease)
        y = 2 + round(x * 11 / 15)
        y2 = 13 - round(x * 11 / 15)
        put(img, x, y, MET[3])                                # crease facing the key light
        if y2 != y:
            put(img, x, y2, MET[1])                           # crease facing away
    return wear(img, "duct_side", MET, rate=0.025)


def _duct_cap():
    img = cap_layer(FIT)
    for y in (2, 7, 12):                                      # flange bolts
        for x in (1, 14):
            put(img, x, y, FIT[4])
            put(img, x, y + 1, FIT[0])
    return img


def _duct_variant(key):
    return lambda: expand_strip(Art.of(_duct_side()), _duct_cap())[key]


def duct_end():
    img = new(16, 16, FIT[2])
    bevel(img, 0, 0, 16, 16, FIT, fill=2)
    bevel(img, 2, 2, 14, 14, [FIT[4], FIT[3], FIT[2], FIT[0], FIT[1]])   # inset lip (inverted light)
    rect(img, 3, 3, 13, 13, BODY[0])
    for y in range(3, 13):                                    # louvre slats
        k = (y - 3) % 3
        for x in range(3, 13):
            put(img, x, y, (MET[3], MET[1], BODY[0])[k])
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        put(img, x, y, FIT[4] if (x, y) == (1, 1) else FIT[0])
    return img


def duct_models(c):
    M = {}
    for axis in ("x", "y", "z"):
        for p in (1, 0):
            for n in (1, 0):
                frm, to = [0, 0, 0], [16, 16, 16]
                faces = run_faces(axis, frm, to, "#side", "#side_t")
                for d in dirs_of(axis):
                    faces[d] = {"texture": "#end", "uv": [0, 0, 16, 16]}
                v = "duct_side_c%d%d" % (p, n)
                M["duct_%s_c%d%d" % (axis, p, n)] = mdl(
                    c, [c.el(frm, to, faces, cull=True)], {"side": v, "side_t": v + "_t", "end": "duct_end"},
                    None, v)
    return M


def duct_blockstate(c):
    return {"variants": {"axis=%s,neg_link=%s,pos_link=%s" % (a, str(neg).lower(), str(pos).lower()):
                         {"model": c.model("duct_%s_c%d%d" % (a, int(not pos), int(not neg)))}
                         for a in AX for pos in (False, True) for neg in (False, True)}}


def duct_fan():
    """Fan face: bolted frame, round shroud, four blades. 8 frames = a quarter turn (seamless)."""
    def frame(t):
        img = new(16, 16, FIT[2])
        bevel(img, 0, 0, 16, 16, FIT, fill=2)
        for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
            put(img, x, y, FIT[4] if (x, y) == (1, 1) else FIT[0])
        rot = t * math.pi / 2
        for y in range(2, 14):
            for x in range(2, 14):
                dx, dy = x - 7.5, y - 7.5
                r = math.hypot(dx, dy)
                if r > 6.2:
                    continue                                   # frame corner
                if r > 5.3:
                    put(img, x, y, MET[0] if dx + dy < 0 else MET[3])   # shroud ring (inner bevel)
                    continue
                a = (math.atan2(dy, dx) - rot) % (math.pi / 2) / (math.pi / 2)
                if r < 1.2:
                    col = FIT[3] if dx + dy < 0 else FIT[1]    # hub
                elif a < 0.52:
                    col = MET[4] if a < 0.14 else (MET[3] if a < 0.36 else MET[2])   # blade
                else:
                    col = BODY[0]
                put(img, x, y, col)
        return img
    a = animate("duct_fan", "breathing", frame)
    return Art(a.frames, a.anim, False)


def fan_models(c):
    frm, to = [0, 0, 0], [16, 16, 16]
    faces = run_faces("z", frm, to, "#side", "#side_t")
    faces["north"] = {"texture": "#fan", "uv": [0, 0, 16, 16]}
    faces["south"] = {"texture": "#end", "uv": [0, 0, 16, 16]}
    return {"duct_fan": mdl(c, [c.el(frm, to, faces, cull=True)],
                            {"side": "duct_side_c11", "side_t": "duct_side_c11_t", "end": "duct_end",
                             "fan": "duct_fan"}, None, "duct_fan")}


# =============================================================================================
# beam_emitter_prop (facing; drawn facing north, mounted on the south face)
# =============================================================================================
def emitter_models(c):
    A, B, BV, C, D, E, EV = R(0, 0, 10, 10), R(0, 10, 10, 2), R(10, 0, 2, 10), R(10, 10, 6, 6), \
        R(12, 0, 4, 4), R(0, 12, 6, 2), R(12, 4, 2, 6)
    t = "#body"
    els = []
    frm, to = [3, 3, 14], [13, 13, 16]                         # mount plate
    els.append(c.el(frm, to, {"north": {"texture": t, "uv": A}, "south": {"texture": t, "uv": A, "cullface": "south"},
                              "up": {"texture": t, "uv": B}, "down": {"texture": t, "uv": B},
                              "east": {"texture": t, "uv": BV}, "west": {"texture": t, "uv": BV}},
                    cull=False, tint=False))
    frm, to = [5, 5, 8], [11, 11, 14]                          # body
    side = {f: {"texture": t, "uv": C} for f in ("up", "down", "east", "west", "north")}
    els.append(c.el(frm, to, side, cull=False, tint=False))
    els.append(c.el(frm, to, {f: {"texture": "#bglow", "uv": C} for f in ("up", "east", "west")},
                    cull=False, emit=15))
    frm, to = [6, 6, 4], [10, 10, 8]                           # barrel
    els.append(c.el(frm, to, {f: {"texture": t, "uv": D} for f in ("up", "down", "east", "west")},
                    cull=False, tint=False))
    frm, to = [5, 5, 2], [11, 11, 4]                           # lens collar
    lens = {"north": {"texture": "#lens", "uv": fuv("north", frm, to)}}
    els.append(c.el(frm, to, {"north": lens["north"], "south": {"texture": t, "uv": C},
                              "up": {"texture": t, "uv": E}, "down": {"texture": t, "uv": E},
                              "east": {"texture": t, "uv": EV}, "west": {"texture": t, "uv": EV}},
                    cull=False, tint=False))
    els.append(c.el(frm, to, {"north": {"texture": "#lglow", "uv": fuv("north", frm, to)}}, cull=False, emit=15))
    for x in (4, 11):                                          # focusing rails
        for y in (4, 11):
            frm, to = [x, y, 1], [x + 1, y + 1, 14]
            els.append(c.el(frm, to, {f: {"texture": "#rail", "uv": fuv(f, frm, to)}
                                      for f in ("up", "down", "east", "west", "north")}, cull=False, tint=False))
            els.append(c.el(frm, to, {"north": {"texture": "#lglow", "uv": fuv("north", frm, to)}},
                            cull=False, emit=15))
    return {"beam_emitter_prop": mdl(c, els, {"body": "beam_emitter", "bglow": "beam_emitter_glow",
                                              "lens": "beam_emitter_lens", "lglow": "beam_emitter_lens_glow",
                                              "rail": "beam_emitter_rail"}, "minecraft:cutout", "beam_emitter")}


def beam_emitter():
    """Packed atlas: A plate 10x10 @0,0 | B plate rim 10x2 @0,10 and 2x10 @10,0 | C body 6x6 @10,10
    | D barrel 4x4 @12,0 | E collar rim 6x2 @0,12 and 2x6 @12,4."""
    img = new(16, 16, BODY[1])
    bevel(img, 0, 0, 10, 10, FIT, fill=2)                      # A
    bevel(img, 2, 2, 8, 8, MET, fill=1)
    for x, y in ((1, 1), (8, 1), (1, 8), (8, 8)):
        put(img, x, y, FIT[4] if (x, y) == (1, 1) else FIT[0])
    for i in range(10):                                        # B
        put(img, i, 10, FIT[3])
        put(img, i, 11, FIT[1])
        put(img, 10, i, FIT[3])
        put(img, 11, i, FIT[1])
    bevel(img, 10, 10, 16, 16, MET, fill=2)                    # C
    for x in range(11, 15):
        put(img, x, 12, MET[1])                                # slit recess (glow sits on row 13)
        put(img, x, 13, BODY[0])
        put(img, x, 14, MET[3])
    bevel(img, 12, 0, 16, 4, MET, fill=1)                      # D
    put(img, 13, 2, MET[2])
    put(img, 14, 2, MET[2])
    for i in range(6):                                         # E
        put(img, i, 12, FIT[3])
        put(img, i, 13, FIT[1])
        put(img, 12, 4 + i, FIT[3])
        put(img, 13, 4 + i, FIT[1])
    rect(img, 6, 12, 10, 16, BODY[1])
    rect(img, 0, 14, 6, 16, BODY[1])
    rect(img, 14, 4, 16, 10, BODY[1])
    return img


def beam_emitter_glow():
    def frame(t):
        k = 0.5 + 0.5 * math.cos(2 * math.pi * t)
        img = new()
        for x in range(11, 15):
            put(img, x, 13, lerp(NEUTRAL[2], NEUTRAL[4], k))
        return img
    return animate("beam_emitter_glow", "breathing", frame)


def beam_emitter_lens():
    img = _tile()
    bevel(img, 5, 5, 11, 11, FIT, fill=2)                      # collar ring
    rect(img, 6, 6, 10, 10, BODY[0])                           # lens well (the glow layer fills it)
    put(img, 6, 6, BODY[2])
    return img


def beam_emitter_lens_glow():
    def frame(t):
        k = 0.5 + 0.5 * math.cos(2 * math.pi * t)
        img = new()
        for y in range(6, 10):
            for x in range(6, 10):
                core = x in (7, 8) and y in (7, 8)
                corner = x in (6, 9) and y in (6, 9)
                put(img, x, y, NEUTRAL[4] if core else
                    lerp(NEUTRAL[1], NEUTRAL[2], k) if corner else lerp(NEUTRAL[2], NEUTRAL[4], k))
        for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):      # rail tips
            put(img, x, y, lerp(NEUTRAL[2], NEUTRAL[3], 1 - k))
        return img
    return animate("beam_emitter_lens_glow", "breathing", frame)


def beam_emitter_rail():
    img = new(16, 16, FIT[2])
    for y in range(16):
        for x in range(16):
            if (x + y) % 4 == 0:
                put(img, x, y, FIT[1])                         # ribbed in either orientation
            elif (x + y) % 4 == 1:
                put(img, x, y, FIT[3])
    return img


# =============================================================================================
TEXTURES = {
    "decor_pipe": decor_pipe,
    "decor_pipe_v": lambda: transpose(decor_pipe()),
    "decor_pipe_end": decor_pipe_end,
    "decor_pipe_joint": decor_pipe_joint,
    "coolant_tube": coolant_tube,
    "coolant_tube_v": lambda: transpose(coolant_tube()),
    "coolant_tube_glow": coolant_tube_glow,
    "coolant_tube_glow_v": coolant_tube_glow_v,
    "coolant_tube_joint": coolant_tube_joint,
    "coolant_tube_joint_glow": coolant_tube_joint_glow,
    "pipe_valve": pipe_valve,
    "pipe_valve_wheel": pipe_valve_wheel,
    "pipe_gauge": pipe_gauge,
    "pipe_gauge_v": pipe_gauge_v,
    "pipe_gauge_dial": pipe_gauge_dial,
    "pipe_gauge_dial_glow": pipe_gauge_dial_glow,
    "cable_tray": cable_tray,
    "cable_tray_t": cable_tray_t,
    "cable_tray_cap": cable_tray_cap,
    "duct_end": duct_end,
    "duct_fan": duct_fan,
    "beam_emitter": beam_emitter,
    "beam_emitter_glow": beam_emitter_glow,
    "beam_emitter_lens": beam_emitter_lens,
    "beam_emitter_lens_glow": beam_emitter_lens_glow,
    "beam_emitter_rail": beam_emitter_rail,
}
for _p in (0, 1):
    for _n in (0, 1):
        TEXTURES["hanging_cables_c%d%d" % (_p, _n)] = _cables(bool(_p), bool(_n))
        for _s in ("", "_t"):
            TEXTURES["duct_side_c%d%d%s" % (_p, _n, _s)] = _duct_variant("_c%d%d%s" % (_p, _n, _s))

_PIPE = ["decor_pipe", "decor_pipe_v", "decor_pipe_end", "decor_pipe_joint"]
_DUCT = ["duct_side_c%d%d%s" % (p, n, s) for p in (0, 1) for n in (0, 1) for s in ("", "_t")]


def _t(ids):
    return {i: i for i in ids}


BLOCKS = [
    Block("decor_pipe", shape="custom", textures=_t(_PIPE),
          model=lambda c: pipe_family(c, "decor_pipe"),
          blockstate=lambda c: pipe_blockstate(c, "decor_pipe"), item_model="decor_pipe"),
    Block("pipe_valve", shape="custom", textures=_t(_PIPE + ["pipe_valve", "pipe_valve_wheel"]),
          model=valve_models, blockstate=valve_blockstate, item_model="pipe_valve",
          render_type="minecraft:cutout"),
    Block("pipe_gauge", shape="custom", emissive=True,
          textures=_t(_PIPE + ["pipe_gauge", "pipe_gauge_v", "pipe_gauge_dial", "pipe_gauge_dial_glow"]),
          model=gauge_models, blockstate=gauge_blockstate, item_model="pipe_gauge"),
    Block("cable_tray", shape="custom", connects="strip",
          textures=_t(["cable_tray", "cable_tray_t", "cable_tray_cap"]),
          model=tray_models, blockstate=tray_blockstate, item_model="cable_tray_c11"),
    Block("hanging_cables", shape="custom", connects="strip",
          textures=_t(["hanging_cables_c%d%d" % (p, n) for p in (0, 1) for n in (0, 1)]),
          model=cables_models, blockstate=cables_blockstate, item_model="hanging_cables_c11",
          render_type="minecraft:cutout"),
    Block("duct", shape="custom", connects="strip", textures=_t(_DUCT + ["duct_end"]),
          model=duct_models, blockstate=duct_blockstate, item_model="duct_x_c11"),
    Block("duct_fan", shape="custom", textures=_t(["duct_side_c11", "duct_side_c11_t", "duct_end", "duct_fan"]),
          model=fan_models, blockstate=lambda c: facing_variants(c, "duct_fan", horizontal=False)),
    Block("coolant_tube", shape="custom", chroma=True, emissive=True,
          textures=_t(["coolant_tube", "coolant_tube_v", "coolant_tube_glow", "coolant_tube_glow_v",
                       "coolant_tube_joint", "coolant_tube_joint_glow", "decor_pipe_end"]),
          model=lambda c: pipe_family(c, "coolant_tube", glow=True, tint=True),
          blockstate=lambda c: pipe_blockstate(c, "coolant_tube"), item_model="coolant_tube"),
    Block("beam_emitter_prop", shape="custom", chroma=True, emissive=True,
          textures=_t(["beam_emitter", "beam_emitter_glow", "beam_emitter_lens", "beam_emitter_lens_glow",
                       "beam_emitter_rail"]),
          model=emitter_models,
          blockstate=lambda c: facing_variants(c, "beam_emitter_prop", horizontal=False)),
]

# =========================================================================================
# Recipes (0.8.0 Stage 6): vanilla + Neroland Core ingredients only, no gate. See _kit.shaped.
# =========================================================================================
from ._kit import attach_recipes, shaped, shapeless  # noqa: E402

_C, _I, _R = "minecraft:copper_ingot", "minecraft:iron_ingot", "minecraft:redstone"
_PIPE, _DUCT = "nerodecor:decor_pipe", "nerodecor:duct"
attach_recipes(BLOCKS, {
    "decor_pipe": shaped("decor_pipe", 8, ["CCC"], C=_C),
    "pipe_valve": shaped("pipe_valve", 1, ["L", "P"], L="minecraft:lever", P=_PIPE),
    "pipe_gauge": shaped("pipe_gauge", 1, ["G", "R", "P"], G="minecraft:glass_pane", P=_PIPE, R=_R),
    "cable_tray": shaped("cable_tray", 8, ["N N", "III"], I=_I, N="minecraft:iron_nugget"),  # not the minecart grid
    "hanging_cables": shaped("hanging_cables", 4, ["SSS", "RRR"], R=_R, S="minecraft:string"),
    "duct": shaped("duct", 8, ["CCC", "C C", "CCC"], C=_C),
    "duct_fan": shapeless("duct_fan", 1, _DUCT, _I, _R),
    "coolant_tube": shaped("coolant_tube", 4, ["GPG"], G="minecraft:glass_pane", P="minecraft:prismarine_shard"),
    "beam_emitter_prop": shaped("beam_emitter_prop", 1, ["A", "Q", "I"], A="minecraft:amethyst_shard", I=_I,
                                Q="minecraft:quartz"),
})
