"""
Greebles collection -- small surface-detail pieces that make a base feel lived-in.

Eight blocks, all `custom`. Blockstate properties the Java side must declare (this module is the
contract for them):

  louvre_vent        face
  maintenance_panel  face, open (bool)
  hazard_stripe      face, axis, pos_link, neg_link (bool), edge (bool)   -- kit strip convention
  deck_sign          face, number (0..9)
  warning_placard    face, symbol (caution|voltage|radiation|flammable|cryo|suit|no_entry|oxygen)
  rivet_plate        face, north/east/south/west/up/down (bool: same block, same face, that side)
  pipe_bracket       face, axis                                           -- kit strip convention
  floor_arrow        facing (north|north_east|east|south_east|south|south_west|west|north_west)

`face` is the side of its own block space the piece is MOUNTED on (down = lying on the floor), the
same meaning the kit's strips use. Plates are drawn once, mounted on north with the front facing
south, and rotated; lying on the floor their top edge points north.

Z-fight rule: every detail is baked into the texture. The only stacked quads are the `_glow`
layers, which reuse the exact element they light. Side faces of the thin plates extrude the edge
texels of the front picture (see _box), so no face needs its own texture.

Accent hues (one per block): maintenance_panel amber, deck_sign plasma, floor_arrow hazard
yellow, pipe_bracket nero alloy; each warning_placard symbol carries its own signal colour.
"""
import math

from ._kit import (Block, animate, bevel, expand_mask16, expand_strip, new, put, ramp, ramp5,
                   rect, strip_rotation, wear)

COLLECTION = "greebles"

STEEL = ramp("station")             # pale hull steel
BODY = ramp("body")                 # house blue-black
STAR = ramp("starsteel")            # bright strap metal
ALLOY = ramp("nero_alloy")
PLASMA = ramp("plasma")
HAZ = ramp5((226, 172, 34))         # hazard yellow
AMBER = ramp5((224, 132, 30))
RED = ramp5((178, 50, 40))
BLUE = ramp5((52, 112, 196))
GREEN = ramp5((58, 146, 84))
ENAMEL = ramp5((192, 204, 212))     # painted sign plate
COPPER = ramp5((186, 108, 62))

CUTOUT = "minecraft:cutout"
SYMBOLS = ("caution", "voltage", "radiation", "flammable", "cryo", "suit", "no_entry", "oxygen")
ARROW_DIRS = ("north", "north_east", "east", "south_east", "south", "south_west", "west",
              "north_west")

_CACHE = {}


def _memo(key, fn):
    if key not in _CACHE:
        _CACHE[key] = fn()
    return _CACHE[key]


# =========================================================================================
# Small painting helpers
# =========================================================================================
def _rivet(img, x, y, r):
    """2x2 domed rivet lit top-left."""
    put(img, x, y, r[4])
    put(img, x + 1, y, r[3])
    put(img, x, y + 1, r[2])
    put(img, x + 1, y + 1, r[0])


def _screw(img, x, y, r):
    """1px screw head with its cast shadow."""
    put(img, x, y, r[4])
    put(img, x + 1, y + 1, r[0])


def _bitmap(img, x0, y0, rows, rgb, scale=1):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "#":
                rect(img, x0 + i * scale, y0 + j * scale, x0 + (i + 1) * scale, y0 + (j + 1) * scale, rgb)


def _pixels(rows, x0=0, y0=0):
    return {(x0 + i, y0 + j) for j, row in enumerate(rows) for i, ch in enumerate(row) if ch == "#"}


FONT = {
    "D": ("##.", "#.#", "#.#", "#.#", "##."),
    "E": ("###", "#..", "##.", "#..", "###"),
    "C": (".##", "#..", "#..", "#..", ".##"),
    "K": ("#.#", "#.#", "##.", "#.#", "#.#"),
    "2": ("##.", "..#", ".#.", "#..", "###"),
}


def _text(img, x, y, s, rgb):
    for ch in s:
        _bitmap(img, x, y, FONT[ch], rgb)
        x += 4


# =========================================================================================
# Model helpers
# =========================================================================================
FACE_ROT = {"north": (0, 0), "east": (0, 90), "south": (0, 180), "west": (0, 270),
            "down": (90, 0), "up": (270, 0)}


def _rot(v, rx, ry):
    x, y, z = v
    for _ in range(rx // 90):
        y, z = z, -y
    for _ in range(ry // 90):
        x, z = -z, x
    return (x, y, z)


_VEC = {(0, -1, 0): "down", (0, 1, 0): "up", (0, 0, -1): "north", (0, 0, 1): "south",
        (-1, 0, 0): "west", (1, 0, 0): "east"}


def _variant(c, model, rx=0, ry=0):
    v = {"model": c.model(model)}
    if rx:
        v["x"] = rx
    if ry:
        v["y"] = ry
    return v


def _face_states(c, pick, extra=((),)):
    """Variants for a plate mounted on `face`. extra = iterable of ((prop, value), ...) tuples;
    pick(extra_dict) -> model name or list of model names (random variants)."""
    out = {}
    for face, (rx, ry) in FACE_ROT.items():
        for ex in extra:
            d = dict(ex)
            key = ",".join("%s=%s" % kv for kv in sorted([("face", face)] + list(ex)))
            names = pick(d)
            if isinstance(names, str):
                out[key] = _variant(c, names, rx, ry)
            else:
                out[key] = [_variant(c, n, rx, ry) for n in names]
    return {"variants": out}


def _box(frm, to, tex, p=1.0, over=None, skip=(), tint=False):
    """A box of a south-facing plate. South/north take the front picture by position; the four
    thin sides extrude the picture's edge texels (p = texel size in model units: 1 at 16px, 0.5 at
    32px). North is the mounting side and is culled against the wall. over = {face: uv | face json}."""
    (x0, y0, z0), (x1, y1, z1) = frm, to
    t, b = 16 - y1, 16 - y0
    uvs = {"south": [x0, t, x1, b], "north": [x1, t, x0, b],
           "up": [x0, t, x1, t + p], "down": [x0, b - p, x1, b],
           "west": [x0, t, x0 + p, b], "east": [x1 - p, t, x1, b]}
    faces = {}
    for f, uv in uvs.items():
        if f in skip:
            continue
        o = (over or {}).get(f)
        face = {"texture": tex, "uv": uv}
        if isinstance(o, dict):
            face.update(o)
        elif o:
            face["uv"] = o
        if f == "north" and z0 == 0:
            face["cullface"] = "north"
        if tint:
            face["tintindex"] = 0
        faces[f] = face
    return {"from": list(frm), "to": list(to), "faces": faces}


def _glow(frm, to, face="south", uv=None):
    """The glow twin of one quad: same element, one face, #glow."""
    (x0, y0, z0), (x1, y1, z1) = frm, to
    if uv is None:
        uv = {"south": [x0, 16 - y1, x1, 16 - y0], "up": [x0, z0, x1, z1]}[face]
    return {"from": list(frm), "to": list(to), "shade": False, "light_emission": 15,
            "faces": {face: {"texture": "#glow", "uv": uv}}}


def _frame_bars(tex, depth):
    """A 2px picture-frame of four bars; inner faces sample the lit / occluded frame rows."""
    return [
        _box((0, 14, 0), (16, 16, depth), tex, over={"down": [2, 15, 14, 16]}),
        _box((0, 0, 0), (16, 2, depth), tex, over={"up": [2, 0, 14, 1]}),
        _box((0, 2, 0), (2, 14, depth), tex, over={"east": [15, 2, 16, 14]}, skip=("up", "down")),
        _box((14, 2, 0), (16, 14, depth), tex, over={"west": [0, 2, 1, 14]}, skip=("up", "down")),
    ]


ITEM_DISPLAY = {
    "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
}


def _mdl(c, textures, elements, cutout=False, item=False):
    m = {}
    if item:  # hand-held icon: the plate face-on, centred in its block space
        m["parent"] = "minecraft:block/block"
        m["gui_light"] = "front"
        m["display"] = ITEM_DISPLAY
    m["textures"] = {k: c.tex(v) for k, v in textures.items()}
    m["elements"] = elements
    if cutout:
        m["render_type"] = CUTOUT
    return m


def _shift(els, dz):
    """Copy of plate elements moved along z (the item icon sits mid-block, so no wall cull)."""
    out = []
    for e in els:
        n = dict(e)
        n["from"] = [e["from"][0], e["from"][1], e["from"][2] + dz]
        n["to"] = [e["to"][0], e["to"][1], e["to"][2] + dz]
        n["faces"] = {f: {k: v for k, v in d.items() if k != "cullface"} for f, d in e["faces"].items()}
        if "rotation" in e:
            r = dict(e["rotation"])
            r["origin"] = [r["origin"][0], r["origin"][1], r["origin"][2] + dz]
            n["rotation"] = r
        out.append(n)
    return out


# =========================================================================================
# louvre_vent -- 2px wall grille: frame, four pitched slats, dark duct behind
# =========================================================================================
def _frame_tex(img, r, seed):
    """2px frame ring lit top-left, corner screws; the middle is left for the caller."""
    bevel(img, 0, 0, 16, 16, r, fill=2)
    for i in range(1, 15):
        put(img, i, 1, r[2])
        put(img, 1, i, r[2])
        put(img, i, 14, r[1])
        put(img, 14, i, r[1])
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        put(img, x, y, r[4] if (x, y) == (1, 1) else r[3])
    return img


def _louvre(seed):
    def paint():
        img = new(16, 16)
        _frame_tex(img, STEEL, seed)
        for i in range(4):
            y = 2 + 3 * i
            rect(img, 2, y, 14, y + 1, STEEL[3])        # lit lip of the slat
            rect(img, 2, y + 1, 14, y + 2, STEEL[2])    # slat face
            rect(img, 2, y + 2, 14, y + 3, BODY[0])     # slot
            put(img, 2, y, STEEL[4])
            put(img, 13, y, STEEL[2])
            put(img, 13, y + 1, STEEL[1])
            put(img, 2, y + 2, BODY[1])
        # frame casts onto the first slat / left end
        for y in range(2, 14):
            if img.getpixel((2, y))[:3] == STEEL[2]:
                put(img, 2, y, STEEL[3])
        if seed.endswith("_b"):                          # variant: grime weeping from two slots
            for (x, y) in ((5, 7), (5, 10), (10, 4), (10, 7), (11, 13)):
                put(img, x, y, STEEL[1])
            put(img, 5, 6, STEEL[2])
        return wear(img, seed, STEEL, rate=0.05)
    return paint


def louvre_vent_duct():
    """What shows through the slots: a dark duct throat with a hint of fan hub."""
    img = new(16, 16, BODY[0])
    for y in range(2, 14):
        for x in range(2, 14):
            d = math.hypot(x - 7.5, y - 7.5)
            put(img, x, y, BODY[2] if d < 1.6 else BODY[1] if (d < 5.2 and (x + y) % 3 == 0) else BODY[0])
    return img


def _louvre_els(tex="#face"):
    els = _frame_bars(tex, 2)
    els.append({"from": [2, 2, 0], "to": [14, 14, 0.25],
                "faces": {"south": {"texture": "#duct", "uv": [2, 2, 14, 14]}}})
    for i in range(4):
        yb = 11.25 - 3 * i
        r = 2 + 3 * i
        els.append({"from": [2, yb, 0.75], "to": [14, yb + 2.5, 1.25],
                    "rotation": {"origin": [8, yb + 1.25, 1], "axis": "x", "angle": -22.5},
                    "faces": {"south": {"texture": tex, "uv": [2, r, 14, r + 2]},
                              "up": {"texture": tex, "uv": [2, r, 14, r + 1]},
                              "down": {"texture": tex, "uv": [2, r + 2, 14, r + 3]},
                              "north": {"texture": tex, "uv": [2, r + 2, 14, r + 3]}}})
    return els


def louvre_model(c):
    out = {}
    for v in ("", "_b"):
        tx = {"particle": "louvre_vent", "face": "louvre_vent" + v, "duct": "louvre_vent_duct"}
        out["louvre_vent" + v] = _mdl(c, tx, _louvre_els())
    out["louvre_vent_item"] = _mdl(c, {"particle": "louvre_vent", "face": "louvre_vent",
                                       "duct": "louvre_vent_duct"}, _shift(_louvre_els(), 7), item=True)
    return out


def louvre_state(c):
    return _face_states(c, lambda d: ["louvre_vent", "louvre_vent", "louvre_vent_b"])


# =========================================================================================
# maintenance_panel -- 3px cover; open=true swings the door out and shows the wiring
# =========================================================================================
PANEL_LEDS = ((11, 4), (12, 4))
OPEN_LEDS = ((4, 11), (6, 11), (11, 4))


def maintenance_panel():
    img = new(16, 16)
    _frame_tex(img, STEEL, "mp")
    rect(img, 1, 1, 15, 15, STEEL[1])                      # recess the cover sits in
    bevel(img, 2, 2, 14, 14, STEEL, fill=2)                # the cover
    for y in (4, 5, 10, 11):                               # hinge knuckles (left)
        put(img, 2, y, STEEL[4] if y in (4, 10) else BODY[1])
        put(img, 3, y, STEEL[1])
    rect(img, 10, 3, 14, 6, STEEL[1])                      # status window bezel
    rect(img, 11, 4, 13, 5, AMBER[1])
    put(img, 10, 3, STEEL[0])
    put(img, 13, 5, STEEL[3])
    rect(img, 5, 7, 10, 9, BODY[1])                        # recessed pull
    rect(img, 5, 7, 10, 8, BODY[0])
    rect(img, 5, 9, 10, 10, STEEL[3])
    for x in range(5, 9):                                  # stencilled id lines
        put(img, x, 4, STEEL[1])
    put(img, 5, 5, STEEL[1])
    put(img, 6, 5, STEEL[1])
    for x in range(4, 12):                                 # hazard band along the foot
        for y in (11, 12):
            put(img, x, y, AMBER[2] if (x + y) % 4 < 2 else BODY[1])
    for x in range(4, 12):
        if (x + 11) % 4 < 2:
            put(img, x, 11, AMBER[3])
    _screw(img, 12, 8, STEEL)                              # quarter-turn latches (right)
    _screw(img, 12, 11, STEEL)
    return wear(img, "maintenance_panel", STEEL, rate=0.05)


def maintenance_panel_glow():
    def frame(t):
        k = 0.5 + 0.5 * math.cos(2 * math.pi * t)
        img = new(16, 16)
        put(img, 11, 4, AMBER[4] if k > 0.5 else AMBER[3])
        put(img, 12, 4, AMBER[3] if k > 0.25 else AMBER[2])
        return img
    return animate("maintenance_panel_glow", "breathing", frame)


def maintenance_panel_open():
    img = new(16, 16)
    _frame_tex(img, STEEL, "mpo")
    rect(img, 2, 2, 14, 14, BODY[1])                       # cavity
    rect(img, 2, 2, 14, 3, BODY[0])                        # frame shadow, top and left
    rect(img, 2, 2, 3, 14, BODY[0])
    rect(img, 3, 13, 14, 14, BODY[2])
    rect(img, 3, 3, 10, 5, STEEL[1])                       # terminal rail
    rect(img, 3, 3, 10, 4, STEEL[3])
    for x in (4, 6, 8):
        put(img, x, 4, COPPER[3])
    put(img, 3, 3, STEEL[4])
    rect(img, 10, 3, 14, 6, STEEL[1])                      # status board
    rect(img, 11, 4, 13, 5, AMBER[1])
    wires = ((4, AMBER[2], AMBER[1]), (6, ENAMEL[2], ENAMEL[1]), (8, AMBER[1], AMBER[0]))
    for x, lit, dark in wires:                             # looms dropping to the breaker
        for y in range(5, 10):
            put(img, x, y, lit)
            put(img, x + 1, y, dark if y % 2 else BODY[0])
    for (x, y) in ((10, 6), (10, 7), (11, 8), (12, 9), (12, 10)):   # one pulled loose
        put(img, x, y, ENAMEL[2])
    put(img, 12, 11, COPPER[3])
    put(img, 13, 11, COPPER[2])
    rect(img, 3, 9, 6, 10, BODY[2])                        # cable tie
    put(img, 3, 9, BODY[3])
    bevel(img, 3, 10, 10, 13, STEEL, fill=1)               # breaker module
    put(img, 4, 11, AMBER[1])
    put(img, 6, 11, AMBER[1])
    put(img, 8, 11, BODY[0])
    put(img, 8, 10, STEEL[4])
    return wear(img, "maintenance_panel_open", BODY, rate=0.04)


def maintenance_panel_open_glow():
    def frame(t):
        i = int(round(t * 4)) % 4
        img = new(16, 16)
        put(img, 4, 11, AMBER[3])
        if i != 2:                                         # the second breaker lamp stutters
            put(img, 6, 11, AMBER[4] if i == 0 else AMBER[3])
        put(img, 11, 4, AMBER[2])
        return img
    return animate("maintenance_panel_open_glow", "flicker", frame)


def maintenance_panel_door():
    """Inside of the cover: pressed stiffening ribs and a wiring diagram sticker."""
    img = new(16, 16, STEEL[1])
    bevel(img, 2, 2, 14, 14, STEEL, fill=1)
    for i in range(3, 13):
        put(img, i, i, STEEL[2])
        put(img, i, 15 - i, STEEL[2])
        if i < 12:
            put(img, i + 1, i, STEEL[0])
    bevel(img, 4, 5, 9, 11, ENAMEL, fill=2)                # wiring diagram sticker
    for (x, y) in ((5, 6), (6, 6), (7, 6), (5, 8), (6, 8), (5, 9), (7, 9)):
        put(img, x, y, BODY[2])
    put(img, 7, 8, AMBER[2])
    return wear(img, "maintenance_panel_door", STEEL, rate=0.04)


def _panel_closed():
    cover = ((2, 2, 2), (14, 14, 3))
    return [_box((0, 0, 0), (16, 16, 2), "#face"),
            _box(cover[0], cover[1], "#face", skip=("north",)),
            _glow(*cover)]


def _panel_open():
    back = ((2, 2, 0), (14, 14, 0.5))
    els = _frame_bars("#open", 2)
    els.append({"from": list(back[0]), "to": list(back[1]),
                "faces": {"south": {"texture": "#open", "uv": [2, 2, 14, 14]}}})
    els.append(_glow(*back))
    els.append({"from": [1, 2, 2], "to": [2, 14, 14], "faces": {       # door swung out on its hinge
        "east": {"texture": "#door", "uv": [2, 2, 14, 14]},
        "west": {"texture": "#face", "uv": [2, 2, 14, 14]},
        "south": {"texture": "#face", "uv": [13, 2, 14, 14]},
        "up": {"texture": "#face", "uv": [2, 2, 3, 14]},
        "down": {"texture": "#face", "uv": [13, 2, 14, 14]}}})
    return els


def panel_model(c):
    ct = {"particle": "maintenance_panel", "face": "maintenance_panel", "glow": "maintenance_panel_glow"}
    ot = {"particle": "maintenance_panel", "face": "maintenance_panel", "open": "maintenance_panel_open",
          "door": "maintenance_panel_door", "glow": "maintenance_panel_open_glow"}
    return {"maintenance_panel": _mdl(c, ct, _panel_closed(), cutout=True),
            "maintenance_panel_open": _mdl(c, ot, _panel_open(), cutout=True),
            "maintenance_panel_item": _mdl(c, ct, _shift(_panel_closed(), 6.5), cutout=True, item=True)}


def panel_state(c):
    return _face_states(c, lambda d: "maintenance_panel_open" if d["open"] == "true" else "maintenance_panel",
                        extra=((("open", "false"),), (("open", "true"),)))


# =========================================================================================
# hazard_stripe -- 1px floor/wall decal, strip-connected; edge=true is a 4px border
# =========================================================================================
def _hazard_base(seed, period, rows):
    half = period // 2
    img = new(16, 16)
    for y in range(rows):
        for x in range(16):
            yel = (x + y) % period < half
            lead = (x + y) % period == 0            # paint ridge on the lit edge of each band
            trail = (x + y) % period == half - 1
            if yel:
                c = HAZ[3] if lead and half > 2 else HAZ[1] if trail and half > 2 else HAZ[2]
                if y == 0:
                    c = HAZ[3]
                if y == rows - 1:
                    c = HAZ[1]
            else:
                c = BODY[2] if y == 0 else BODY[0] if y == rows - 1 else BODY[1]
            put(img, x, y, c)
    wear(img, seed, HAZ, rate=0.06, region=(0, 0, 16, rows))
    return wear(img, seed + "_k", BODY, rate=0.02, region=(0, 0, 16, rows))


def _hazard_cap(rows):
    """End bar: 3 columns of steel, bolted. Painted at both ends; expand_strip copies the outer 3."""
    img = new(16, 16)
    for x0 in (0, 13):
        for y in range(rows):
            put(img, x0, y, STEEL[3])
            put(img, x0 + 1, y, STEEL[2])
            put(img, x0 + 2, y, STEEL[0])
        put(img, x0, 0, STEEL[4])
        put(img, x0 + 1, 0, STEEL[3])
        put(img, x0, rows - 1, STEEL[1])
        put(img, x0 + 1, rows - 1, STEEL[0])
        for y in ((1,) if rows <= 4 else (3, rows - 5)):
            put(img, x0, y, STEEL[4])
            put(img, x0 + 1, y + 1, BODY[1])
    return img


def _hazard_set(kind):
    """{suffix: Art} for 'full' (two wear variants of the mid piece) or 'edge'."""
    def make():
        rows, period = (16, 8) if kind == "full" else (4, 4)
        out = dict(expand_strip(_kit_art(_hazard_base("hazard_" + kind, period, rows)), _hazard_cap(rows)))
        if kind == "full":
            alt = expand_strip(_kit_art(_hazard_base("hazard_full_b", period, rows)), _hazard_cap(rows))
            out["_b_c00"], out["_b_c00_t"] = alt["_c00"], alt["_c00_t"]
        return out
    return _memo(("hazard", kind), make)


def _kit_art(img):
    from ._kit import Art
    return Art([img])


HAZ_IDS = {}
for _kind, _base in (("full", "hazard_stripe"), ("edge", "hazard_stripe_edge")):
    for _p in (0, 1):
        for _n in (0, 1):
            for _t in ("", "_t"):
                HAZ_IDS["%s_c%d%d%s" % (_base, _p, _n, _t)] = (_kind, "_c%d%d%s" % (_p, _n, _t))
HAZ_IDS["hazard_stripe_b_c00"] = ("full", "_b_c00")
HAZ_IDS["hazard_stripe_b_c00_t"] = ("full", "_b_c00_t")


def _hazard_el(tex, canon, w):
    """1px plate on the DOWN face. canon '' runs along x (w px deep in z), '_z' along z."""
    if canon == "":
        frm, to = (0, 0, 0), (16, 1, w)
        faces = {"up": {"texture": tex, "uv": [0, 0, 16, w]},
                 "down": {"texture": tex, "uv": [0, w, 16, 0], "cullface": "down"},
                 "north": {"texture": tex, "uv": [16, 0, 0, 1]},
                 "south": {"texture": tex, "uv": [0, w - 1, 16, w]},
                 "west": {"texture": tex, "uv": [0, 1, 1, 2]},
                 "east": {"texture": tex, "uv": [15, 1, 16, 2]}}
    else:
        frm, to = (0, 0, 0), (w, 1, 16)
        faces = {"up": {"texture": tex, "uv": [0, 0, w, 16]},
                 "down": {"texture": tex, "uv": [0, 16, w, 0], "cullface": "down"},
                 "west": {"texture": tex, "uv": [0, 0, 1, 16], "rotation": 270},
                 "east": {"texture": tex, "uv": [w - 1, 0, w, 16], "rotation": 90},
                 "north": {"texture": tex, "uv": [1, 0, 2, 1]},
                 "south": {"texture": tex, "uv": [1, 15, 2, 16]}}
    return {"from": list(frm), "to": list(to), "faces": faces}


def _hazard_names():
    """model name -> (texture id, canon, width)"""
    out = {}
    for kind, base, w in (("", "hazard_stripe", 16), ("_edge", "hazard_stripe_edge", 4)):
        for canon in ("", "_z"):
            for p in (0, 1):
                for n in (0, 1):
                    out["hazard_stripe%s_c%d%d%s" % (kind, p, n, canon)] = (
                        "%s_c%d%d%s" % (base, p, n, "_t" if canon else ""), canon, w)
    for canon in ("", "_z"):
        out["hazard_stripe_b_c00" + canon] = ("hazard_stripe_b_c00" + ("_t" if canon else ""), canon, 16)
    return out


def hazard_model(c):
    out = {}
    for name, (tid, canon, w) in _hazard_names().items():
        out[name] = _mdl(c, {"particle": "hazard_stripe_c00", "bar": tid}, [_hazard_el("#bar", canon, w)])
    face = {"texture": "#bar", "uv": [0, 0, 16, 16]}
    out["hazard_stripe_item"] = _mdl(c, {"particle": "hazard_stripe_c00", "bar": "hazard_stripe_c11"}, [
        {"from": [0, 0, 7.5], "to": [16, 16, 8.5], "faces": {
            "south": face, "north": {"texture": "#bar", "uv": [16, 0, 0, 16]},
            "up": {"texture": "#bar", "uv": [0, 0, 16, 1]}, "down": {"texture": "#bar", "uv": [0, 15, 16, 16]},
            "west": {"texture": "#bar", "uv": [0, 0, 1, 16]}, "east": {"texture": "#bar", "uv": [15, 0, 16, 16]}}}],
        item=True)
    return out


def hazard_state(c):
    variants = {}
    for face in FACE_ROT:
        for axis in ("x", "y", "z"):
            if {"x": ("west", "east"), "y": ("down", "up"), "z": ("north", "south")}[axis].count(face):
                continue
            canon, rx, ry, flip = strip_rotation(face, axis)
            for edge in (False, True):
                for pos in (False, True):
                    for neg in (False, True):
                        cp, cn = int(not pos), int(not neg)
                        if flip:
                            cp, cn = cn, cp
                        name = "hazard_stripe%s_c%d%d%s" % ("_edge" if edge else "", cp, cn, canon)
                        key = "axis=%s,edge=%s,face=%s,neg_link=%s,pos_link=%s" % (
                            axis, str(edge).lower(), face, str(neg).lower(), str(pos).lower())
                        if not edge and (cp, cn) == (0, 0):
                            variants[key] = [_variant(c, name, rx, ry),
                                             _variant(c, "hazard_stripe_b_c00" + canon, rx, ry)]
                        else:
                            variants[key] = _variant(c, name, rx, ry)
    return {"variants": variants}


# =========================================================================================
# deck_sign -- 32px portrait plate, stencilled DECK header, lit seven-segment numeral
# =========================================================================================
SEGS = {"a": (2, 0, 8, 2), "g": (2, 7, 8, 9), "d": (2, 14, 8, 16),
        "f": (0, 1, 2, 7), "b": (8, 1, 10, 7), "e": (0, 9, 2, 15), "c": (8, 9, 10, 15)}
DIGITS = ("abcdef", "bc", "abged", "abgcd", "fgbc", "afgcd", "afgecd", "abc", "abcdefg", "abcdfg")
SIGN = (4, 2, 28, 30)        # plate rect in 32px texels = model x 2..14, y 1..15
DIG_AT = (11, 12)


def _seg_pixels(seg):
    """Texels of one segment, ends mitred so neighbours meet on a diagonal."""
    x0, y0, x1, y1 = SEGS[seg]
    px = {(x, y) for x in range(x0, x1) for y in range(y0, y1)}
    if seg in "agd":
        px -= {(x0, y0), (x1 - 1, y0)} if seg == "a" else {(x0, y1 - 1), (x1 - 1, y1 - 1)} if seg == "d" else set()
    else:
        outer = x0 if x0 == 0 else x1 - 1
        px -= {(outer, y0), (outer, y1 - 1)}
    return {(DIG_AT[0] + x, DIG_AT[1] + y) for x, y in px}


def _deck_sign(n):
    def paint():
        x0, y0, x1, y1 = SIGN
        img = new(32, 32)
        bevel(img, x0, y0, x1, y1, BODY, fill=1)
        for x in range(x0 + 1, x1 - 1):                       # inner keyline
            put(img, x, y0 + 1, BODY[2])
        rect(img, x0 + 2, y0 + 2, x1 - 2, y0 + 9, STEEL[1])    # header band
        rect(img, x0 + 2, y0 + 2, x1 - 2, y0 + 3, STEEL[2])
        rect(img, x0 + 2, y0 + 8, x1 - 2, y0 + 9, BODY[0])
        _text(img, x0 + 4, y0 + 3, "DECK", STAR[3])
        put(img, x1 - 4, y0 + 4, PLASMA[1])                    # header pip
        put(img, x1 - 4, y0 + 6, STEEL[3])
        rect(img, x0 + 2, y0 + 9, x1 - 2, y1 - 2, BODY[0])     # display well
        for x in range(x0 + 2, x1 - 2):
            put(img, x, y1 - 3, BODY[1])
        for y in range(y0 + 9, y1 - 2):
            put(img, x1 - 3, y, BODY[1])
        on = set()
        for s in DIGITS[n]:
            on |= _seg_pixels(s)
        for s in SEGS:                                        # ghost segments
            for (x, y) in _seg_pixels(s) - on:
                put(img, x, y, BODY[1])
        for (x, y) in on:
            put(img, x, y, PLASMA[1])
        for (x, y) in ((x0 + 1, y1 - 2), (x1 - 2, y1 - 2)):
            put(img, x, y, STEEL[2])
        _screw(img, x0 + 1, y0 + 1, STEEL)
        put(img, x1 - 2, y0 + 1, STEEL[3])
        return wear(img, "deck_sign_%d" % n, STEEL, rate=0.03)
    return paint


def _deck_glow(n):
    def paint():
        img = new(32, 32)
        on = set()
        for s in DIGITS[n]:
            on |= _seg_pixels(s)
        for (x, y) in on:
            hot = (x - 1, y) not in on or (x, y - 1) not in on
            put(img, x, y, PLASMA[3] if hot else PLASMA[2])
        return img
    return paint


def _sign_els(tex="#face"):
    plate = ((2, 1, 0), (14, 15, 1))
    return [_box(plate[0], plate[1], tex, p=0.5), _glow(*plate)]


def deck_model(c):
    out = {}
    for n in range(10):
        tx = {"particle": "deck_sign_0", "face": "deck_sign_%d" % n, "glow": "deck_sign_%d_glow" % n}
        out["deck_sign_%d" % n] = _mdl(c, tx, _sign_els(), cutout=True)
    out["deck_sign_item"] = _mdl(c, {"particle": "deck_sign_0", "face": "deck_sign_1",
                                     "glow": "deck_sign_1_glow"}, _shift(_sign_els(), 7.5),
                                 cutout=True, item=True)
    return out


def deck_state(c):
    return _face_states(c, lambda d: "deck_sign_" + d["number"],
                        extra=tuple(((("number", str(n)),)) for n in range(10)))


# =========================================================================================
# warning_placard -- 32px enamel plate, eight symbols
# =========================================================================================
PLAC = (4, 4, 28, 28)        # model x 2..14, y 2..14
PICTO = {
    "caution": (".##.", "####", "####", ".##.", ".##.", ".##.", "....", ".##.", ".##."),
    "voltage": ("...###", "..###.", ".###..", "######", "..###.", ".###..", ".##...", "##....", "#....."),
    "radiation": (".##.....##.", "####...####", "####...####", ".###...###.", "...........",
                  "....###....", "....###....", "...........", "...#####...", "..#######..", "...#####..."),
    "flammable": ("...#....", "...##...", "..###...", ".####.#.", ".######.", "###.####", "##...###",
                  "##...###", ".##.###."),
    "cryo": ("....#....", ".#.###.#.", "..#.#.#..", ".#.###.#.", "####.####", ".#.###.#.", "..#.#.#..",
             ".#.###.#.", "....#...."),
    "suit": ("..#####..", ".#######.", "##.....##", "#.......#", "#.......#", "##.....##", "#########",
             ".#######.", ".##.#.##."),
    "no_entry": ("##########", "##########", "##########"),
    "oxygen": (".###.....", "#...#....", "#...#....", "#...#.##.", "#...#...#", "#...#..#.", ".###..###"),
}
PLAC_STYLE = {"caution": ("tri", HAZ, BODY[0]), "voltage": ("tri", HAZ, BODY[0]),
              "radiation": ("tri", HAZ, BODY[0]), "flammable": ("dia", RED, ENAMEL[4]),
              "cryo": ("dia", BLUE, ENAMEL[4]), "suit": ("disc", BLUE, ENAMEL[4]),
              "no_entry": ("disc", RED, ENAMEL[4]), "oxygen": ("sq", GREEN, ENAMEL[4])}
CAPTION = {"caution": "##.###.#", "voltage": "###.#.##", "radiation": "#.##.###", "flammable": "##.#.###",
           "cryo": "###.##.#", "suit": "#.###.##", "no_entry": "##.##.##", "oxygen": "#.#.####"}


def _shape(kind):
    """Set of plate-local (24x24) texels inside the symbol field."""
    px = set()
    for y in range(24):
        for x in range(24):
            cx, cy = x + 0.5 - 12, y + 0.5
            if kind == "tri":
                inside = 1.5 <= cy <= 18 and abs(cx) <= (cy - 1.0) * 0.62 + 0.4
            elif kind == "dia":
                inside = abs(cx) + abs(cy - 10) <= 9.2
            elif kind == "disc":
                inside = math.hypot(cx, cy - 10) <= 8.9
            else:
                inside = abs(cx) <= 8.5 and abs(cy - 10) <= 8.5 and abs(cx) + abs(cy - 10) <= 15.5
            if inside:
                px.add((x, y))
    return px


def _erode(px, n=1):
    for _ in range(n):
        px = {(x, y) for (x, y) in px if all((x + dx, y + dy) in px for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    return px


def _placard(sym):
    def paint():
        kind, r, ink = PLAC_STYLE[sym]
        x0, y0, x1, y1 = PLAC
        img = new(32, 32)
        bevel(img, x0, y0, x1, y1, ENAMEL, fill=2)
        for x in range(x0 + 1, x1 - 1):
            put(img, x, y1 - 2, ENAMEL[1])
        outer = _shape(kind)
        fill = _erode(outer, 2 if kind == "tri" else 1)
        edge = BODY[1] if kind == "tri" else r[0]
        for (x, y) in outer:
            put(img, x0 + x, y0 + y, edge)
        for (x, y) in fill:
            c = r[2]
            if (x - 1, y) not in fill or (x, y - 1) not in fill:
                c = r[3]
            elif (x + 1, y) not in fill or (x, y + 1) not in fill:
                c = r[1]
            put(img, x0 + x, y0 + y, c)
        rows = PICTO[sym]
        cy = 11.5 if kind == "tri" else 10
        px0 = x0 + 12 - (len(rows[0]) + 1) // 2
        py0 = y0 + int(round(cy - len(rows) / 2.0))
        if sym == "voltage":
            px0 += 0
        _bitmap(img, px0, py0, rows, ink)
        if sym == "no_entry":                                 # the bar gets its own bevel
            for i in range(len(rows[0])):
                put(img, px0 + i, py0, ENAMEL[4])
                put(img, px0 + i, py0 + 2, ENAMEL[2])
        if sym == "oxygen":
            pass
        cap = CAPTION[sym]                                    # microtext caption
        for i, ch in enumerate(cap):
            if ch == "#":
                put(img, x0 + 4 + i * 2, y0 + 21, BODY[2])
                put(img, x0 + 5 + i * 2, y0 + 21, BODY[2])
        for (x, y) in ((x0 + 1, y0 + 1), (x1 - 3, y0 + 1), (x0 + 1, y1 - 3), (x1 - 3, y1 - 3)):
            put(img, x, y, STEEL[1])
            put(img, x + 1, y + 1, ENAMEL[4])
        return wear(img, "warning_placard_" + sym, ENAMEL, rate=0.05)
    return paint


def _plac_els(tex="#face"):
    return [_box((2, 2, 0), (14, 14, 1), tex, p=0.5)]


def placard_model(c):
    out = {}
    for s in SYMBOLS:
        out["warning_placard_" + s] = _mdl(c, {"particle": "warning_placard_caution",
                                               "face": "warning_placard_" + s}, _plac_els())
    out["warning_placard_item"] = _mdl(c, {"particle": "warning_placard_caution",
                                           "face": "warning_placard_caution"}, _shift(_plac_els(), 7.5), item=True)
    return out


def placard_state(c):
    return _face_states(c, lambda d: "warning_placard_" + d["symbol"],
                        extra=tuple(((("symbol", s),)) for s in SYMBOLS))


# =========================================================================================
# rivet_plate -- 1px plate; a group of plates wears its lip and rivets on the perimeter only
# =========================================================================================
RIVET_BAND = 4


def _rivet_base(seed):
    """Rolled plate. Each tile carries a quarter rivet in every corner, so four plates meeting
    make one field rivet at the junction; the perimeter band replaces them at a group's edge."""
    img = new(16, 16, STEEL[2])
    streaks = ((2, 3, 6), (9, 6, 5), (4, 9, 7), (1, 12, 4), (10, 13, 4))
    if seed.endswith("_b"):
        streaks = ((7, 2, 6), (2, 5, 5), (8, 8, 6), (3, 11, 8), (11, 14, 3))
    for x, y, n in streaks:                                    # brushed streaks
        for i in range(n):
            put(img, x + i, y, STEEL[3])
    put(img, 0, 0, STEEL[0])
    put(img, 15, 0, STEEL[2])
    put(img, 0, 15, STEEL[3])
    put(img, 15, 15, STEEL[4])
    put(img, 1, 0, STEEL[1])
    put(img, 0, 1, STEEL[1])
    if seed.endswith("_b"):                                    # variant: a boot scuff
        for (x, y) in ((5, 7), (6, 7), (6, 8), (7, 8), (8, 9)):
            put(img, x, y, STEEL[1])
    return wear(img, seed, STEEL, rate=0.03)


def _rivet_frame():
    """4px perimeter band: lit lip, rivet row on a slightly sunk land, then a groove."""
    img = new(16, 16)
    rect(img, 0, 0, 16, 16, STEEL[2])
    rect(img, 4, 4, 12, 12, STEEL[2], a=0)
    for i in range(16):
        put(img, i, 0, STEEL[3])
        put(img, 0, i, STEEL[3])
    for i in range(16):
        put(img, i, 15, STEEL[0])
        put(img, 15, i, STEEL[0])
    put(img, 0, 0, STEEL[4])
    put(img, 15, 0, STEEL[2])
    put(img, 0, 15, STEEL[1])
    for i in range(3, 13):                                    # groove inside the rivet row
        put(img, i, 3, STEEL[1])
        put(img, 3, i, STEEL[1])
        put(img, i, 12, STEEL[3])
        put(img, 12, i, STEEL[3])
    for a in (1, 5, 9, 13):
        for (x, y) in ((a, 1), (a, 13), (1, a), (13, a)):
            _rivet(img, x, y, STEEL)
            if x + 2 < 16 and y + 2 < 15:
                put(img, x + 2, y + 1, STEEL[1])
                put(img, x + 1, y + 2, STEEL[1])
    return img


def _rivet_set(v):
    return _memo(("rivet", v), lambda: expand_mask16(_kit_art(_rivet_base("rivet_plate" + v)),
                                                     _rivet_frame(), width=RIVET_BAND))


def rivet_model(c):
    out = {}
    for v in ("", "_b"):
        for m in range(16):
            tid = "rivet_plate%s_f%d" % (v, m)
            out[tid] = _mdl(c, {"particle": "rivet_plate_f15", "face": tid},
                            [_box((0, 0, 0), (16, 16, 1), "#face")])
    out["rivet_plate_item"] = _mdl(c, {"particle": "rivet_plate_f15", "face": "rivet_plate_f15"},
                                   _shift([_box((0, 0, 0), (16, 16, 1), "#face")], 7.5), item=True)
    return out


def rivet_state(c):
    """Multipart: per face, per mask. Mask bits follow the kit (8 top, 4 bottom, 2 left, 1 right
    of the front picture = up, down, west, east when mounted on north) and are rotated with it."""
    edges = ((8, (0, 1, 0)), (4, (0, -1, 0)), (2, (-1, 0, 0)), (1, (1, 0, 0)))
    parts = []
    for face, (rx, ry) in FACE_ROT.items():
        for m in range(16):
            when = {"face": face}
            for bit, vec in edges:
                when[_VEC[_rot(vec, rx, ry)]] = "false" if m & bit else "true"
            parts.append({"when": when, "apply": [_variant(c, "rivet_plate_f%d" % m, rx, ry),
                                                  _variant(c, "rivet_plate_b_f%d" % m, rx, ry)]})
    return {"multipart": parts}


# =========================================================================================
# pipe_bracket -- saddle clamp for the 6px decor_pipe (bore y/z 5..11, pipe along `axis`)
# =========================================================================================
# boxes for a pipe along x, mounted on DOWN: (from, to, style)
BRACKET = (
    ((5, 0, 3), (11, 1, 13), "base"),
    ((6, 1, 6), (10, 4, 10), "post"),
    ((6, 4, 4), (10, 5, 12), "strap"),
    ((6, 11, 4), (10, 12, 12), "top"),
    ((6, 5, 4), (10, 11, 5), "strap"),
    ((6, 5, 11), (10, 11, 12), "strap"),
    ((7, 7, 3), (9, 9, 4), "bolt"),
    ((7, 7, 12), (9, 9, 13), "bolt"),
)


FACES6 = ("up", "down", "north", "south", "west", "east")


def _swap(v):
    return (v[2], v[1], v[0])


def _bracket_boxes(canon):
    return BRACKET if canon == "" else tuple((_swap(a), _swap(b), s) for a, b, s in BRACKET)


def _dims(face, a, b):
    dx, dy, dz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
    return {"up": (dx, dz), "down": (dx, dz), "north": (dx, dy), "south": (dx, dy),
            "west": (dz, dy), "east": (dz, dy)}[face]


STYLES = ("base", "post", "strap", "top", "bolt")
STYLE_RAMP = {"base": (STEEL, 2), "post": (BODY, 2), "strap": (STAR, 1), "top": (STAR, 2), "bolt": (STEEL, 1)}
LIT = ("up", "south", "west", "north")
HIDDEN = {("post", "up"), ("post", "down")}


def _bracket_key(face, a, b, style):
    """Atlas key for one face: a bevelled swatch of its own size, or (for 1px-thin faces) a single
    texel from the style's lit/unlit swatch row."""
    w, h = _dims(face, a, b)
    if min(w, h) == 1:
        return ("px", style, face in LIT)
    return (w, h, style)


def _bracket_atlas(canon):
    """Pack one bevelled swatch per (width, height, style) the model needs into 16x16; row 15
    from x=6 holds the thin-face texels."""
    def make():
        need = []
        for a, b, style in _bracket_boxes(canon):
            for face in FACES6:
                k = _bracket_key(face, a, b, style)
                if (style, face) not in HIDDEN and k[0] != "px" and k not in need:
                    need.append(k)
        need.sort(key=lambda k: (-k[1], -k[0], k[2]))
        slots, x, y, rowh = {}, 0, 0, 0
        for k in need:
            w, h = k[0], k[1]
            if x + w > 16:
                x, y, rowh = 0, y + rowh, 0
            if y + h > (15 if x + w > 6 else 16):
                raise ValueError("pipe_bracket atlas overflow")
            slots[k] = (x, y)
            x += w
            rowh = max(rowh, h)
        for i, style in enumerate(STYLES):
            slots[("px", style, True)] = (6 + 2 * i, 15)
            slots[("px", style, False)] = (7 + 2 * i, 15)
        return slots
    return _memo(("bracket_atlas", canon), make)


def _bracket_tex(canon):
    """The clamp's part sheet. The layout is the model's atlas (see _bracket_atlas), but every
    swatch is painted as the part it wraps, on a dark backing plate so the sheet reads as one
    drawing: a drilled foot plate with two anchor bolts, a ribbed post, rolled starsteel straps
    with a hinge seam, the cap strap with its nero-alloy id band, and a hex bolt head."""
    def paint():
        img = new(16, 16, BODY[1])
        atlas = _bracket_atlas(canon)
        used = set()
        for k, (x, y) in atlas.items():
            w, h = (1, 1) if k[0] == "px" else (k[0], k[1])
            used |= {(x + i, y + j) for i in range(w) for j in range(h)}
        for (x, y) in sorted(used):                        # swatches sit proud of the backing
            for ox, oy in ((1, 0), (0, 1), (1, 1)):
                if (x + ox, y + oy) not in used and x + ox < 16 and y + oy < 16:
                    put(img, x + ox, y + oy, BODY[0])
        for k, (x, y) in atlas.items():
            if k[0] == "px":
                r, fill = STYLE_RAMP[k[1]]
                put(img, x, y, r[fill + 1] if k[2] else r[fill - 1])
                continue
            w, h, style = k
            r, fill = STYLE_RAMP[style]
            rect(img, x, y, x + w, y + h, r[fill])         # soft bevel: one ramp step each way
            rect(img, x, y, x + w, y + 1, r[fill + 1])
            rect(img, x, y, x + 1, y + h, r[fill + 1])
            rect(img, x, y + h - 1, x + w, y + h, r[fill - 1])
            rect(img, x + w - 1, y, x + w, y + h, r[fill - 1])
            put(img, x, y, r[min(4, fill + 2)])
            tall = w < h
            if style in ("strap", "top") and max(w, h) == 8:   # one hinge seam on the long bands
                if tall:
                    rect(img, x, y + h - 3, x + w, y + h - 2, r[fill - 1])
                else:
                    rect(img, x + w - 3, y, x + w - 2, y + h, r[fill - 1])
            if style == "top":                             # nero-alloy id band across the strap
                if tall:
                    rect(img, x, y + h // 2 - 1, x + w, y + h // 2 + 1, ALLOY[2])
                    rect(img, x, y + h // 2 - 1, x + w, y + h // 2, ALLOY[3])
                    put(img, x, y + h // 2 - 1, ALLOY[4])
                    put(img, x + w - 1, y + h // 2, ALLOY[1])
                else:
                    rect(img, x + w // 2 - 1, y, x + w // 2 + 1, y + h, ALLOY[2])
                    rect(img, x + w // 2 - 1, y, x + w // 2, y + h, ALLOY[3])
                    put(img, x + w // 2 - 1, y, ALLOY[4])
                    put(img, x + w // 2, y + h - 1, ALLOY[1])
            if style == "base":                            # foot plate: chamfers, bolts, post weld
                for (cx, cy) in ((x, y), (x + w - 1, y), (x, y + h - 1), (x + w - 1, y + h - 1)):
                    put(img, cx, cy, STEEL[1])
                if tall:
                    rect(img, x + 1, y + 3, x + w - 1, y + h - 3, STEEL[1])
                    for by in (y + 1, y + h - 3):
                        _rivet(img, x + w // 2 - 1, by, STAR)
                else:
                    rect(img, x + 3, y + 1, x + w - 3, y + h - 1, STEEL[1])
                    for bx in (x + 1, x + w - 3):
                        _rivet(img, bx, y + h // 2 - 1, STAR)
            if style == "post":                            # ribbed pedestal under the saddle
                rect(img, x, y, x + w, y + 1, BODY[0])     # occluded by the strap above
                rect(img, x, y + 1, x + 1, y + h, BODY[3])
                rect(img, x + 1, y + 1, x + 2, y + h, BODY[2])
                rect(img, x + 2, y + 1, x + 3, y + h, BODY[1])
                rect(img, x + 3, y + 1, x + 4, y + h, BODY[0])
                put(img, x, y + 1, BODY[4])
            if style == "bolt":
                _rivet(img, x, y, STAR)
        return img
    return paint


def _bracket_els(canon):
    atlas = _bracket_atlas(canon)
    els = []
    for a, b, style in _bracket_boxes(canon):
        faces = {}
        for face in FACES6:
            if (style, face) in HIDDEN:
                continue
            k = _bracket_key(face, a, b, style)
            x, y = atlas[k]
            w, h = (1, 1) if k[0] == "px" else (k[0], k[1])
            faces[face] = {"texture": "#metal", "uv": [x, y, x + w, y + h]}
            if style == "base" and face == "down":
                faces[face]["cullface"] = "down"
        els.append({"from": list(a), "to": list(b), "faces": faces})
    return els


def bracket_model(c):
    out = {}
    for canon in ("", "_z"):
        m = _mdl(c, {"particle": "pipe_bracket", "metal": "pipe_bracket" + canon}, _bracket_els(canon))
        m = dict([("parent", "minecraft:block/block")] + list(m.items()))
        out["pipe_bracket" + canon] = m
    return out


def bracket_state(c):
    variants = {}
    for face in FACE_ROT:
        for axis in ("x", "y", "z"):
            if {"x": ("west", "east"), "y": ("down", "up"), "z": ("north", "south")}[axis].count(face):
                continue
            canon, rx, ry, _flip = strip_rotation(face, axis)
            variants["axis=%s,face=%s" % (axis, face)] = _variant(c, "pipe_bracket" + canon, rx, ry)
    return {"variants": variants}


# =========================================================================================
# floor_arrow -- painted stencil arrow, 8-way; a glint runs tail to tip along its spine
# =========================================================================================
def _arrow_uv(x, y, diag):
    """(u along the arrow 0..14, v across it) for a texel; tip is north / north-east."""
    if diag:
        return (x - y + 10) * 14.0 / 23.0, (x + y - 15) / 2.0
    return 14.0 - y, x - 7.5


def _arrow_mask(diag):
    """Stencil arrow: head, a bridge gap, then the shaft (split once more on the straight one)."""
    def make():
        px = set()
        for y in range(1, 15):
            for x in range(1, 15):
                if diag:
                    u, w = x - y, x + y - 15
                    if u >= 5 or (abs(w) <= 2 and -9 <= u <= 2):
                        px.add((x, y))
                elif (1 <= y <= 7 and abs(x - 7.5) < y) or (y in (9, 10, 12, 13, 14) and 5 <= x <= 10):
                    px.add((x, y))
        return px
    return _memo(("arrow", diag), make)


def _floor_arrow(diag):
    def paint():
        allpx = _arrow_mask(diag)
        img = new(16, 16)
        for (x, y) in allpx:
            c = HAZ[2]
            if (x, y - 1) not in allpx and (x - 1, y) not in allpx:
                c = HAZ[4]
            elif (x - 1, y) not in allpx or (x, y - 1) not in allpx:
                c = HAZ[3]
            elif (x + 1, y) not in allpx or (x, y + 1) not in allpx:
                c = HAZ[1]
            put(img, x, y, c)
        return wear(img, "floor_arrow" + ("_diag" if diag else ""), HAZ, rate=0.06)
    return paint


def _floor_arrow_glow(diag):
    tid = "floor_arrow" + ("_diag" if diag else "") + "_glow"

    def frame(t):
        allpx = _arrow_mask(diag)
        img = new(16, 16)
        front = t * 18.0
        for (x, y) in allpx:
            u, v = _arrow_uv(x, y, diag)
            if abs(v) < 1.0 and front - 2.6 <= u < front and u < 11.5:
                put(img, x, y, HAZ[4] if u >= front - 1.3 else HAZ[3])
            elif u >= 11.9 and abs(v) < (1.0 if diag else 2.0):   # the tip holds a steady pilot glow
                put(img, x, y, HAZ[3])
        return img
    return lambda: animate(tid, "flow", frame)


def _arrow_els():
    quad = ((0, 0.25, 0), (16, 0.25, 16))
    base = {"from": list(quad[0]), "to": list(quad[1]),
            "faces": {"up": {"texture": "#paint", "uv": [0, 0, 16, 16]}}}
    return [base, _glow(quad[0], quad[1], "up")]


def arrow_model(c):
    out = {}
    for d in ("", "_diag"):
        out["floor_arrow" + d] = _mdl(c, {"particle": "floor_arrow", "paint": "floor_arrow" + d,
                                          "glow": "floor_arrow" + d + "_glow"}, _arrow_els(), cutout=True)
    f = {"texture": "#paint", "uv": [0, 0, 16, 16]}
    g = {"texture": "#glow", "uv": [0, 0, 16, 16]}
    quad = {"from": [0, 0, 8], "to": [16, 16, 8]}
    out["floor_arrow_item"] = _mdl(c, {"particle": "floor_arrow", "paint": "floor_arrow",
                                       "glow": "floor_arrow_glow"}, [
        dict(quad, faces={"south": f, "north": {"texture": "#paint", "uv": [16, 0, 0, 16]}}),
        dict(quad, shade=False, light_emission=15, faces={"south": g})], cutout=True, item=True)
    return out


def arrow_state(c):
    variants = {}
    for i, name in enumerate(ARROW_DIRS):
        variants["facing=" + name] = _variant(c, "floor_arrow" + ("_diag" if i % 2 else ""), 0, 90 * (i // 2))
    return {"variants": variants}


# =========================================================================================
# Registry
# =========================================================================================
TEXTURES = {
    "louvre_vent": _louvre("louvre_vent"),
    "louvre_vent_b": _louvre("louvre_vent_b"),
    "louvre_vent_duct": louvre_vent_duct,
    "maintenance_panel": maintenance_panel,
    "maintenance_panel_glow": maintenance_panel_glow,
    "maintenance_panel_open": maintenance_panel_open,
    "maintenance_panel_open_glow": maintenance_panel_open_glow,
    "maintenance_panel_door": maintenance_panel_door,
    "pipe_bracket": _bracket_tex(""),
    "pipe_bracket_z": _bracket_tex("_z"),
    "floor_arrow": _floor_arrow(False),
    "floor_arrow_diag": _floor_arrow(True),
    "floor_arrow_glow": _floor_arrow_glow(False),
    "floor_arrow_diag_glow": _floor_arrow_glow(True),
}
for _tid, (_kind, _suffix) in HAZ_IDS.items():
    TEXTURES[_tid] = (lambda k=_kind, s=_suffix: _hazard_set(k)[s])
for _n in range(10):
    TEXTURES["deck_sign_%d" % _n] = _deck_sign(_n)
    TEXTURES["deck_sign_%d_glow" % _n] = _deck_glow(_n)
for _s in SYMBOLS:
    TEXTURES["warning_placard_" + _s] = _placard(_s)
for _v in ("", "_b"):
    for _m in range(16):
        TEXTURES["rivet_plate%s_f%d" % (_v, _m)] = (lambda v=_v, m=_m: _rivet_set(v)["_f%d" % m])


def _tx(*ids):
    return {i: i for i in ids}


BLOCKS = [
    Block("louvre_vent", shape="custom", model=louvre_model, blockstate=louvre_state,
          item_model="louvre_vent_item", name="Louvre Vent",
          textures=_tx("louvre_vent", "louvre_vent_b", "louvre_vent_duct")),
    Block("maintenance_panel", shape="custom", emissive=True, model=panel_model, blockstate=panel_state,
          item_model="maintenance_panel_item", name="Maintenance Panel",
          textures=_tx("maintenance_panel", "maintenance_panel_glow", "maintenance_panel_open",
                       "maintenance_panel_open_glow", "maintenance_panel_door")),
    Block("hazard_stripe", shape="custom", connects="strip", model=hazard_model, blockstate=hazard_state,
          item_model="hazard_stripe_item", name="Hazard Stripe", textures=_tx(*HAZ_IDS)),
    Block("deck_sign", shape="custom", emissive=True, model=deck_model, blockstate=deck_state,
          item_model="deck_sign_item", name="Deck Sign",
          textures=_tx(*(["deck_sign_%d" % n for n in range(10)] + ["deck_sign_%d_glow" % n for n in range(10)]))),
    Block("warning_placard", shape="custom", model=placard_model, blockstate=placard_state,
          item_model="warning_placard_item", name="Warning Placard",
          textures=_tx(*["warning_placard_" + s for s in SYMBOLS])),
    Block("rivet_plate", shape="custom", connects="mask16", model=rivet_model, blockstate=rivet_state,
          item_model="rivet_plate_item", name="Rivet Plate",
          textures=_tx(*["rivet_plate%s_f%d" % (v, m) for v in ("", "_b") for m in range(16)])),
    Block("pipe_bracket", shape="custom", model=bracket_model, blockstate=bracket_state,
          item_model="pipe_bracket", name="Pipe Bracket", textures=_tx("pipe_bracket", "pipe_bracket_z")),
    Block("floor_arrow", shape="custom", emissive=True, model=arrow_model, blockstate=arrow_state,
          item_model="floor_arrow_item", name="Floor Arrow",
          textures=_tx("floor_arrow", "floor_arrow_diag", "floor_arrow_glow", "floor_arrow_diag_glow")),
]

# =========================================================================================
# Recipes (0.8.0 Stage 6): vanilla + Neroland Core ingredients only, no gate. See _kit.shaped.
# =========================================================================================
from ._kit import attach_recipes, shaped  # noqa: E402

_I, _N, _S = "minecraft:iron_ingot", "minecraft:iron_nugget", "minecraft:smooth_stone"
_Y, _K = "minecraft:yellow_dye", "minecraft:black_dye"
attach_recipes(BLOCKS, {
    "louvre_vent": shaped("louvre_vent", 4, ["BB"], B="minecraft:iron_bars"),
    "maintenance_panel": shaped("maintenance_panel", 4, ["NNN", "NRN", "NNN"], N=_N, R="minecraft:redstone"),
    "hazard_stripe": shaped("hazard_stripe", 8, ["SSS", "YKY", "SSS"], K=_K, S=_S, Y=_Y),
    "deck_sign": shaped("deck_sign", 3, ["NNN", "GLG"], G="minecraft:glass_pane", L="minecraft:glowstone_dust",
                        N=_N),
    "warning_placard": shaped("warning_placard", 4, ["NNN", "YKY"], K=_K, N=_N, Y=_Y),
    "rivet_plate": shaped("rivet_plate", 8, ["IN", "NI"], I=_I, N=_N),
    "pipe_bracket": shaped("pipe_bracket", 8, ["N N", "NNN"], N=_N),
    "floor_arrow": shaped("floor_arrow", 4, [" L ", "LLL", " S "], L="minecraft:glowstone_dust",
                          S="minecraft:smooth_stone_slab"),
})
