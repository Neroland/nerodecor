"""
Chroma Core collection -- owned by one art agent.

Blocks
  chroma_hub   the hero piece: a navy pedestal console (foot, plinth, finned column, deck with four
               pylons) carrying a faceted plasma prism on an emitter socket. One accent hue
               (house plasma). Every glow is a separate, identical-vertex cutout twin element
               (light_emission 15, shade false) sampling a `_glow` texture; the three glow textures
               breathe (8 frames) and are desynced by animate()'s per-texture phase.
               Side faces use vanilla auto-UV against one elevation texture (`chroma_hub_side`:
               texture row = 16 - y), top faces against plan textures, so there are no UV tables.
  chroma_cube  replaces the 12 neon cubes. mask16 connected cube; the WHOLE face is tinted
               (chroma + paintable), so it is authored near-white: a frosted light-guide cell with a
               bright core and an X lattice. The glow layer covers everything except the lattice
               and the baked rim and breathes from the core outwards.

Items (item-only art, declared through the runner's ITEMS / ITEM_TEXTURES hook, see _kit.Item;
textures land in textures/item/chroma_core/<tid>.png):
  chroma_linker  handheld wand: ring antenna cradling a plasma node
  scene_chip     data chip; layer1 = the scene-colour stripe (near-white, tint index 1)
  paint_gun      spray tool; layer1 = the paint canister (near-white, tint index 1)
"""
import math

from ._kit import (NS, Block, Item, animate, bevel, bezel_layer, lerp, new, put, ramp, rect,
                   wear)

COLLECTION = "chroma_core"

BODY = ramp("body")          # house near-black navy
STEEL = ramp("starsteel")    # trim
PLASMA = ramp("plasma")      # the one accent hue
# Near-white tint targets (hand-tuned 5-step ramps: shadows lean blue-violet, glint leans warm).
LUX = [(150, 158, 196), (188, 196, 224), (222, 230, 244), (240, 244, 250), (255, 253, 244)]
RIM = [(92, 100, 148), (128, 137, 184), (166, 175, 212), (206, 214, 236), (238, 242, 250)]


def _wave(t, phase=0.0):
    return 0.5 + 0.5 * math.cos(2 * math.pi * (t + phase))


# =========================================================================================
# chroma_cube
# =========================================================================================
def _lattice(x, y):
    """The light-guide lattice: a cell ring plus the X struts running out to the corners (they
    meet the neighbour's struts when cubes connect)."""
    ring = (x in (4, 11) and 4 <= y <= 11) or (y in (4, 11) and 4 <= x <= 11)
    strut = (x == y or x + y == 15) and (x < 4 or x > 11)
    return ring or strut


def chroma_cube():
    img = new(16, 16, LUX[2])
    for y in range(16):
        for x in range(16):
            if _lattice(x, y):
                put(img, x, y, LUX[1])
    for i in range(5, 11):          # key light inside the cell: lit top/left, occluded bottom/right
        put(img, i, 5, LUX[3])
        put(img, 5, i, LUX[3])
        put(img, i, 10, LUX[1])
        put(img, 10, i, LUX[1])
    put(img, 10, 5, LUX[2])
    put(img, 5, 10, LUX[2])
    rect(img, 6, 6, 10, 10, LUX[3])
    rect(img, 7, 7, 9, 9, LUX[4])
    put(img, 6, 6, LUX[4])
    put(img, 9, 9, LUX[2])
    for (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11)):   # lattice nodes
        put(img, x, y, LUX[0])
    return wear(img, "chroma_cube", LUX, rate=0.03)


def chroma_cube_frame():
    return bezel_layer(RIM)


def chroma_cube_glow():
    def frame(t):
        k = _wave(t)
        img = new()
        for y in range(16):
            for x in range(16):
                if _lattice(x, y):
                    continue
                d = max(abs(x - 7.5), abs(y - 7.5))
                if d < 1:
                    c = LUX[4]
                elif d < 2:
                    c = lerp(LUX[3], LUX[4], k)
                elif d < 3:
                    c = lerp(LUX[2], LUX[3], k)
                else:                                   # outer field lags the core by a quarter
                    c = lerp(LUX[2], LUX[3], 0.6 * _wave(t, -0.25))
                put(img, x, y, c)
        return img
    return animate("chroma_cube_glow", "breathing", frame)


# =========================================================================================
# chroma_hub
# =========================================================================================
# Elevation rows (v = 16 - y): prism = own texture; pylons y10..14 -> rows 2..5 (cap, band, post); socket y10..11 ->
# row 5; deck y8..10 -> rows 6..7; column + fins y3..8 -> rows 8..12; plinth y2..3 -> row 13;
# foot y0..2 -> rows 14..15.
_SLOTS = [(x, y) for x in (5, 10) for y in (9, 10, 11)]            # column light slots
_DASH = [(x, 7) for x in (3, 4, 6, 7, 8, 9, 11, 12)]              # deck status line
_TIPS = [(x, 3) for x in (1, 2, 13, 14)]                          # pylon tips


def chroma_hub_side():
    img = new(16, 16, BODY[1])
    # foot
    for x in range(16):
        put(img, x, 14, BODY[3])
        put(img, x, 15, BODY[1])
    put(img, 0, 14, BODY[4])
    put(img, 15, 14, BODY[2])
    put(img, 0, 15, BODY[2])
    put(img, 15, 15, BODY[0])
    for x in (2, 13):
        put(img, x, 15, STEEL[0])
    # plinth
    for x in range(2, 14):
        put(img, x, 13, BODY[2])
    put(img, 2, 13, BODY[4])
    put(img, 13, 13, BODY[0])
    for x in (4, 6, 9, 11):
        put(img, x, 13, BODY[0])       # vent slots
    # column
    rect(img, 4, 8, 12, 13, BODY[2])
    for y in range(8, 13):
        put(img, 4, y, BODY[3])
        put(img, 11, y, BODY[0])
        put(img, 6, y, BODY[1])        # shadow beside the fin
        put(img, 9, y, BODY[1])
    for x in range(4, 12):
        put(img, x, 8, BODY[0])        # deck overhang shadow
    put(img, 4, 12, BODY[2])
    for (x, y) in _SLOTS:
        put(img, x, y, PLASMA[1])
    # fin front (centre columns) and fin sides (columns 2..3 / 12..13)
    for y in range(8, 13):
        put(img, 7, y, STEEL[1])
        put(img, 8, y, STEEL[0])
        for x0 in (2, 12):
            put(img, x0, y, BODY[3])
            put(img, x0 + 1, y, BODY[1])
    for x0 in (2, 7, 12):
        put(img, x0, 8, BODY[1])
        put(img, x0 + 1, 8, BODY[0])
        put(img, x0, 12, STEEL[1] if x0 != 7 else STEEL[2])
        put(img, x0 + 1, 12, STEEL[0] if x0 != 7 else STEEL[1])
    put(img, 7, 9, STEEL[2])
    # deck
    for x in range(1, 15):
        put(img, x, 6, BODY[3])
        put(img, x, 7, BODY[1])
    put(img, 1, 6, BODY[4])
    put(img, 14, 6, BODY[2])
    put(img, 1, 7, BODY[2])
    put(img, 14, 7, BODY[0])
    for (x, y) in _DASH:
        put(img, x, y, PLASMA[1])
    # socket
    for x in range(5, 11):
        put(img, x, 5, STEEL[1])
    put(img, 5, 5, STEEL[3])
    put(img, 6, 5, STEEL[2])
    put(img, 10, 5, STEEL[0])
    # pylons
    for x0 in (1, 13):
        for y in (4, 5):
            put(img, x0, y, BODY[3])
            put(img, x0 + 1, y, BODY[1])
        put(img, x0, 4, BODY[4])
        put(img, x0 + 1, 5, BODY[0])
    for (x, y) in _TIPS:
        put(img, x, y, PLASMA[1])
    for x0 in (1, 13):                 # pylon caps
        put(img, x0, 2, STEEL[1])
        put(img, x0 + 1, 2, STEEL[0])
    return wear(img, "chroma_hub_side", BODY, rate=0.04)


def chroma_hub_side_glow():
    def frame(t):
        img = new()
        k = _wave(t)
        for (x, y) in _SLOTS:           # light climbs the slot as the hub breathes in
            lit = (11 - y) / 2.0
            put(img, x, y, lerp(PLASMA[2], PLASMA[4] if y == 10 else PLASMA[3], clamp01(k * 1.4 - 0.2 * lit)))
        kd = _wave(t, 0.5)
        for (x, y) in _DASH:
            put(img, x, y, lerp(PLASMA[1], PLASMA[3] if x in (7, 8) else PLASMA[2], kd))
        kt = _wave(t, 0.25)
        for (x, y) in _TIPS:
            put(img, x, y, lerp(PLASMA[2], PLASMA[4] if x in (1, 13) else PLASMA[3], kt))
        return img
    return animate("chroma_hub_side_glow", "breathing", frame)


def clamp01(v):
    return 0.0 if v < 0 else 1.0 if v > 1 else v


def chroma_hub_plate():
    """Plan view of the foot and plinth tops (and the undersides)."""
    img = new(16, 16, BODY[1])
    bevel(img, 0, 0, 16, 16, BODY, fill=1)
    bevel(img, 2, 2, 14, 14, BODY, fill=2)
    bevel(img, 4, 4, 12, 12, BODY, fill=1)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        put(img, x, y, STEEL[1])
    put(img, 1, 1, STEEL[2])
    for i in (7, 8):                    # fin footprints on the plinth ring
        put(img, i, 3, STEEL[0])
        put(img, i, 12, STEEL[0])
        put(img, 3, i, STEEL[0])
        put(img, 12, i, STEEL[0])
    return wear(img, "chroma_hub_plate", BODY, rate=0.05)


_CHAN = [(3, 3), (4, 4), (12, 3), (11, 4), (3, 12), (4, 11), (12, 12), (11, 11)]
_PYL = [(x0, y0) for x0 in (1, 13) for y0 in (1, 13)]


def chroma_hub_top():
    """Plan view of the deck (1..14), the pylon tops (corners) and the socket top (5..10)."""
    img = new(16, 16, BODY[1])
    bevel(img, 1, 1, 15, 15, BODY, fill=2)
    for i in range(3, 13):              # inlaid inner panel seam
        put(img, i, 2, BODY[1])
        put(img, i, 13, BODY[3])
        put(img, 2, i, BODY[1])
        put(img, 13, i, BODY[3])
    for (x, y) in _CHAN:
        put(img, x, y, PLASMA[1])
    for (x, y) in ((7, 3), (8, 3), (3, 7), (3, 8)):      # key nubs, lit side
        put(img, x, y, BODY[4])
    for (x, y) in ((7, 12), (8, 12), (12, 7), (12, 8)):  # key nubs, shaded side
        put(img, x, y, BODY[3])
    for (x0, y0) in _PYL:              # pylon caps: steel, key-lit
        put(img, x0, y0, STEEL[2])
        put(img, x0 + 1, y0, STEEL[1])
        put(img, x0, y0 + 1, STEEL[1])
        put(img, x0 + 1, y0 + 1, STEEL[0])
    bevel(img, 5, 5, 11, 11, STEEL, fill=1)
    put(img, 5, 5, STEEL[3])
    rect(img, 6, 6, 10, 10, PLASMA[0])
    rect(img, 7, 7, 9, 9, PLASMA[1])
    return wear(img, "chroma_hub_top", BODY, rate=0.04)


def chroma_hub_top_glow():
    def frame(t):
        img = new()
        k = _wave(t)
        kc = _wave(t, 0.125)
        for i, (x, y) in enumerate(_CHAN):   # inner channel pixel leads, outer follows
            inner = i % 2 == 1
            put(img, x, y, lerp(PLASMA[1], PLASMA[3] if inner else PLASMA[2], kc if inner else _wave(t, 0.25)))
        for y in range(6, 10):
            for x in range(6, 10):
                core = 7 <= x <= 8 and 7 <= y <= 8
                put(img, x, y, lerp(PLASMA[2], PLASMA[4], k) if core else lerp(PLASMA[1], PLASMA[2], k))
        return img
    return animate("chroma_hub_top_glow", "breathing", frame)


# Prism atlas (explicit UVs): body side [0,0,4,3], body top [4,0,8,4], cap side [8,0,10,1],
# cap top [8,1,10,3], tip side [10,0,12,1].
def _prism(k):
    """k in [0,1]: 0 = resting crystal, 1 = fully lit."""
    # the glint is pulled back onto the plasma hue so the breathing lerps stay one accent hue
    P = PLASMA[:4] + [(214, 250, 249)]
    img = new(16, 16, P[1])
    side = [[P[4], P[2], P[2], P[1]],
            [P[3], P[3], P[1], P[1]],
            [P[2], P[2], P[2], P[0]]]
    hot = [[P[4], P[3], P[3], P[2]],
           [P[3], P[4], P[2], P[2]],
           [P[3], P[2], P[3], P[1]]]
    for y in range(3):
        for x in range(4):
            put(img, x, y, lerp(side[y][x], hot[y][x], k))
    for y in range(4):                   # body top: lit rim, cap seat in the middle
        for x in range(4, 8):
            edge = x in (4, 7) or y in (0, 3)
            put(img, x, y, lerp(P[3] if edge else P[1], P[4] if edge else P[2], k))
    put(img, 7, 3, lerp(P[1], P[2], k))
    put(img, 4, 0, P[4])
    put(img, 8, 0, lerp(P[3], P[4], k))  # cap side
    put(img, 9, 0, lerp(P[1], P[2], k))
    for (x, y, a, b) in ((8, 1, 4, 4), (9, 1, 3, 4), (8, 2, 3, 4), (9, 2, 2, 3)):
        put(img, x, y, lerp(P[a], P[b], k))
    put(img, 10, 0, lerp(P[2], P[3], k))  # tip side
    put(img, 11, 0, lerp(P[0], P[1], k))
    return img


def chroma_hub_prism():
    return _prism(0.0)


def chroma_hub_prism_glow():
    return animate("chroma_hub_prism_glow", "breathing", lambda t: _prism(_wave(t)))


def _hub_model(c):
    t = {"particle": c.tex("chroma_hub_side"), "side": c.tex("chroma_hub_side"),
         "side_glow": c.tex("chroma_hub_side_glow"), "top": c.tex("chroma_hub_top"),
         "top_glow": c.tex("chroma_hub_top_glow"), "plate": c.tex("chroma_hub_plate"),
         "prism": c.tex("chroma_hub_prism"), "prism_glow": c.tex("chroma_hub_prism_glow")}
    S4 = ("north", "east", "south", "west")
    els = []

    def box(frm, to, up="#top", down=None, sides=S4, cull=False):
        f = {s: "#side" for s in sides}
        if up:
            f["up"] = up
        if down:
            f["down"] = down
        els.append(c.el(frm, to, f, cull=cull, tint=False))

    def glow(frm, to, faces):
        els.append(c.el(frm, to, faces, cull=False, tint=False, emit=15))

    # foot, plinth
    box([0, 0, 0], [16, 2, 16], up="#plate", down="#plate",
        cull={"down": "down", "north": "north", "east": "east", "south": "south", "west": "west"})
    box([2, 2, 2], [14, 3, 14], up="#plate")
    # column + its light slots
    box([4, 3, 4], [12, 8, 12], up=None)
    glow([4, 3, 4], [12, 8, 12], {s: "#side_glow" for s in S4})
    # four fins bracing the deck
    box([7, 3, 2], [9, 8, 4], up=None, sides=("north", "east", "west"))
    box([7, 3, 12], [9, 8, 14], up=None, sides=("south", "east", "west"))
    box([2, 3, 7], [4, 8, 9], up=None, sides=("west", "north", "south"))
    box([12, 3, 7], [14, 8, 9], up=None, sides=("east", "north", "south"))
    # deck + status line and channels
    box([1, 8, 1], [15, 10, 15], down="#plate")
    g = {s: "#side_glow" for s in S4}
    g["up"] = "#top_glow"
    glow([1, 8, 1], [15, 10, 15], g)
    # pylons + tips
    for (x0, z0) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        box([x0, 10, z0], [x0 + 2, 14, z0 + 2])
        glow([x0, 10, z0], [x0 + 2, 14, z0 + 2], {s: "#side_glow" for s in S4})
    # emitter socket + well
    box([5, 10, 5], [11, 11, 11])
    glow([5, 10, 5], [11, 11, 11], {"up": "#top_glow"})
    # prism: tip, body, cap -- each with an identical glow twin; all turned 45 degrees
    rot = {"origin": [8, 8, 8], "axis": "y", "angle": 45}
    parts = (
        ([7, 11, 7], [9, 12, 9], {s: {"uv": [10, 0, 12, 1]} for s in S4}),
        ([6, 12, 6], [10, 15, 10], dict({s: {"uv": [0, 0, 4, 3]} for s in S4},
                                         up={"uv": [4, 0, 8, 4]}, down={"uv": [4, 0, 8, 4]})),
        ([7, 15, 7], [9, 16, 9], dict({s: {"uv": [8, 0, 10, 1]} for s in S4}, up={"uv": [8, 1, 10, 3]})),
    )
    for frm, to, faces in parts:
        for tex, emit in (("#prism", 0), ("#prism_glow", 15)):
            e = c.el(frm, to, {f: dict(spec, texture=tex) for f, spec in faces.items()},
                     cull=False, tint=False, emit=emit)
            e["rotation"] = dict(rot)
            els.append(e)
    return {"chroma_hub": {"textures": t, "elements": els, "render_type": "minecraft:cutout",
                           "display": _HUB_DISPLAY}}


_HUB_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}


TEXTURES = {
    "chroma_cube": chroma_cube,
    "chroma_cube_frame": chroma_cube_frame,
    "chroma_cube_glow": chroma_cube_glow,
    "chroma_hub_side": chroma_hub_side,
    "chroma_hub_side_glow": chroma_hub_side_glow,
    "chroma_hub_top": chroma_hub_top,
    "chroma_hub_top_glow": chroma_hub_top_glow,
    "chroma_hub_plate": chroma_hub_plate,
    "chroma_hub_prism": chroma_hub_prism,
    "chroma_hub_prism_glow": chroma_hub_prism_glow,
}

# The hub drops with its network id (nerodecor:chroma_network) so a broken hub relinks when placed.
# 26.3+ uses the hand-written overlay in common/src/main/resources-26.3 (new loot format).
_HUB_LOOT = {
    "type": "minecraft:block",
    "pools": [{"rolls": 1, "bonus_rolls": 0,
               "entries": [{"type": "minecraft:item", "name": "%s:chroma_hub" % NS,
                            "functions": [{"function": "minecraft:copy_components",
                                           "source": "block_entity",
                                           "include": ["%s:chroma_network" % NS]}]}],
               "conditions": [{"condition": "minecraft:survives_explosion"}]}]}

# The cube drops itself with its painted colour (one entry per COLOR value; exactly one matches).
_DYES = ["white", "light_gray", "gray", "black", "red", "orange", "yellow", "lime", "green", "cyan",
         "light_blue", "blue", "purple", "magenta", "pink", "brown"]
_CUBE_LOOT = {"type": "minecraft:block",
              "pools": [{"rolls": 1, "bonus_rolls": 0,
                         "entries": [{"type": "minecraft:item", "name": "%s:chroma_cube" % NS,
                                      "conditions": [{"condition": "minecraft:block_state_property",
                                                      "block": "%s:chroma_cube" % NS,
                                                      "properties": {"color": "natural"}}]}]
                         + [{"type": "minecraft:item", "name": "%s:chroma_cube" % NS,
                             "conditions": [{"condition": "minecraft:block_state_property",
                                             "block": "%s:chroma_cube" % NS, "properties": {"color": c}}],
                             "functions": [{"function": "minecraft:set_components",
                                            "components": {"%s:color" % NS: c}}]} for c in _DYES],
                         "conditions": [{"condition": "minecraft:survives_explosion"}]}]}

BLOCKS = [
    Block("chroma_hub", shape="custom", emissive=True, light=7, block_entity=True,
          textures={"side": "chroma_hub_side", "side_glow": "chroma_hub_side_glow",
                    "top": "chroma_hub_top", "top_glow": "chroma_hub_top_glow",
                    "plate": "chroma_hub_plate", "prism": "chroma_hub_prism",
                    "prism_glow": "chroma_hub_prism_glow"},
          model=_hub_model, name="Chroma Hub", loot=_HUB_LOOT),
    Block("chroma_cube", shape="cube", connects="mask16", chroma=True, paintable=True,
          emissive=True, light=15, textures={"all": "chroma_cube"}, name="Chroma Cube", loot=_CUBE_LOOT),
]


# =========================================================================================
# Items that are not blocks (local extension -- see the module docstring)
# =========================================================================================
_INK = {
    ".": None,
    "o": BODY[0], "d": BODY[1], "b": BODY[2], "h": BODY[3], "g": BODY[4],
    "z": STEEL[0], "s": STEEL[1], "S": STEEL[2], "w": STEEL[3], "W": STEEL[4],
    "q": PLASMA[0], "p": PLASMA[1], "P": PLASMA[2], "c": PLASMA[3], "C": PLASMA[4],
    "1": LUX[0], "2": LUX[1], "3": LUX[2], "4": LUX[3], "5": LUX[4],
}


def _sprite(rows):
    if len(rows) != 16 or any(len(r) != 16 for r in rows):
        raise ValueError("sprite must be 16x16: %r" % [len(r) for r in rows])
    img = new()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if _INK[ch]:
                put(img, x, y, _INK[ch])
    return img


def chroma_linker():
    return _sprite([
        "...........oo...",
        "..........oCCo..",
        ".........oCccPo.",
        "........oCcCPPpo",
        "........ocPcPppo",
        ".........oPPpqo.",
        "........oSoqqo..",
        ".......owSsoo...",
        "......ohbo......",
        ".....ohbo.......",
        "....ocPo........",
        "...ohbo.........",
        "..oSso..........",
        ".ohdo...........",
        "ohdo............",
        "ooo.............",
    ])


def scene_chip():
    return _sprite([
        "................",
        "...oooooooo.....",
        "..oghhhhhhbo....",
        "..ohbbbbbbbdo...",
        "..ohdddddddbo...",
        "..ohdddddddbo...",
        "..ohbbbbbbbdo...",
        "..ohbqqqqbcdo...",
        "..ohbqPpqbbdo...",
        "..ohbqqqqbbdo...",
        "..ohbbbbbbbdo...",
        "..ohwbSbSbsdo...",
        "..ohSbsbsbzdo...",
        "..obdddddddoo...",
        "...ooooooooo....",
        "................",
    ])


def scene_chip_stripe():
    """layer1: the scene-colour stripe (tint index 1)."""
    return _sprite([
        "................",
        "................",
        "................",
        "................",
        "....5444443.....",
        "....4333322.....",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ])


def paint_gun():
    return _sprite([
        "................",
        ".....oooooo.....",
        "....oddddddo....",
        "....zddddddz....",
        "....oddddddo....",
        "..ooooosSooooo..",
        ".oghhhhhhhhhwSo.",
        ".ohbbbbbbbbbScCo",
        ".ohbpPPpbbbbszo.",
        "..oooohbdsooo...",
        ".....ohbdoso....",
        "....ohbdooo.....",
        "....ohbdo.......",
        "...ohbdo........",
        "...ohddo........",
        "....ooo.........",
    ])


def paint_gun_paint():
    """layer1: the paint canister (tint index 1)."""
    return _sprite([
        "................",
        "................",
        ".....545443.....",
        ".....433332.....",
        ".....322221.....",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ])


ITEM_TEXTURES = {
    "chroma_linker": chroma_linker,
    "scene_chip": scene_chip,
    "scene_chip_stripe": scene_chip_stripe,
    "paint_gun": paint_gun,
    "paint_gun_paint": paint_gun_paint,
}

_WHITE = {"type": "minecraft:constant", "value": -1}
# Scene Chip: vanilla dye tint -- Java only has to set minecraft:dyed_color to the scene colour;
# the default is house plasma (0xFF3FD0E0 as a signed int).
_SCENE_TINT = {"type": "minecraft:dye", "default": (0xFF000000 | (PLASMA[2][0] << 16) | (PLASMA[2][1] << 8)
                                                   | PLASMA[2][2]) - (1 << 32)}

ITEMS = [
    Item("chroma_linker", ["chroma_linker"], parent="minecraft:item/handheld", name="Chroma Linker"),
    Item("scene_chip", ["scene_chip", "scene_chip_stripe"], tints=[_WHITE, _SCENE_TINT], name="Scene Chip"),
    Item("paint_gun", ["paint_gun", "paint_gun_paint"], parent="minecraft:item/handheld",
         tints=[_WHITE, {"type": "%s:decor_color" % NS}], name="Paint Gun"),
]
