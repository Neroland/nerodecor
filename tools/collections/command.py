"""
Command collection -- consoles, wall screens, racks and operator furniture (8 blocks).

Palette: house navy (`body`) with `station` trim; ONE accent hue per block -- console, keyboard and
wall screen plasma cyan, server rack green, drive rack amber, blinkenlight panel chroma (near-white
lamps, the hub colour does the rest). patch_panel and operator_chair are not emissive.

Blockstate contract (the Java registration must match these properties exactly)
------------------------------------------------------------------------------
Every model is drawn "facing north": its front looks north and it sits against the block to its
south. `left` / `right` are the VIEWER's left / right when looking at that front (canonical north:
left = east, right = west). A link boolean is true when the same block continues on that side.

  command_console     facing (4) | shape = straight / inner / outer | left | right
                      straight: cheek plate on each unlinked end (left cap / mid / right cap).
                      inner (concave, operator inside): risers on the back AND on the operator's
                      left (canonical: south + east). outer (convex): corner post only, at the back
                      left (canonical: south-east), stepped hip deck. left / right are ignored for
                      inner / outer. The deck is one element rotated -22.5 deg about x.
  wall_screen         facing (4) | up | down | left | right | idle        block entity, light 6
                      16-mask bezel baked into the 32px face: a bezel edge is drawn on every
                      unlinked side, so an N x M group shows one frame. idle=true adds the animated
                      idle layer (data-flow, 16 frames, tiles seamlessly across the group); the
                      anchor's renderer sets idle=false on the whole group while a `link.display`
                      payload is shown and draws 0.01 px in front of the z=14 face, inset 3 texels
                      (1.5 px) from every bezel edge.
  keyboard_shelf      facing (4)
  operator_chair      facing (8): north, north_east, east, south_east, south, south_west, west,
                      north_west. Diagonals use a second model whose elements are rotated -45 deg
                      about y (18 elements each).
  server_rack         facing (4) | up | down      vertical cap / mid / cap; LEDs flicker (4 frames)
  drive_rack          facing (4) | up | down      vertical cap / mid / cap; pips data-flow (16)
  patch_panel         facing (4) | left | right   horizontal run; rack ears on unlinked ends; two
                      cable layouts picked per position (weighted blockstate variants)
  blinkenlight_panel  none (kit cube, chroma)

Block-entity assets for wall_screen (shipped through its `textures`, not used by any model)
------------------------------------------------------------------------------------------
  wall_screen_font_0 / _1   32x32 glyph pages, near-white on transparent (tint in the renderer).
                            Cells are 4x6 texels, 8 columns x 5 rows = 40 glyphs a page; the glyph
                            is the 3x5 block at the cell's top-left (advance 4, line height 6).
                            Glyph index g -> page g // 40, column (g % 40) % 8, row (g % 40) // 8.
                            g = codepoint - 0x20 for 0x20..0x5F (fold lower case to upper);
                            g = 64..79 are FONT_SYMBOLS, in that order.
  wall_screen_f<mask>       the bezel set (mask bits 8 top, 4 bottom, 2 left, 1 right = bezel there)
  wall_screen_glow_f<mask>  the idle layer, cleared under the bezel
"""
import functools
import math

from ._kit import Block, animate, bevel, det, expand_mask16, new, put, ramp, ramp5, rect, wear

COLLECTION = "command"

B = ramp("body")                    # house navy
ST = ramp("station")                # trim metal
SS = ramp("starsteel")              # labels, latches
PL = ramp("plasma")                 # cyan accent
NA = ramp("nero_alloy")
VC = ramp("void_crystal")
MR = ramp("mars")
GRN = ramp5((64, 226, 120))         # server LEDs
AMB = ramp5((255, 168, 44))         # drive pips
LAMP = ramp5((222, 232, 242))       # chroma lamps (near-white tint target)
PIP_OFF = tuple(int(v * 0.36) for v in AMB[2])
LAMP_OFF = tuple((2 * a + b) // 3 for a, b in zip(LAMP[0], B[1]))
SCR = ramp5((20, 30, 52))           # screen glass
PAD = [B[0], NA[0], tuple((a + b) // 2 for a, b in zip(NA[0], NA[1])), NA[1], NA[2]]  # upholstery
INK = (252, 255, 255)               # font mask (tinted by the renderer)

cache = functools.lru_cache(maxsize=None)


# =========================================================================================
# Shared metal
# =========================================================================================
@cache
def command_metal():
    img = new(16, 16, B[1])
    bevel(img, 0, 0, 16, 16, B, fill=1)
    for i in range(3, 13):
        put(img, i, 3, B[0])
        put(img, 3, i, B[0])
        put(img, i, 12, B[2])
        put(img, 12, i, B[2])
    return wear(img, "command_metal", B, rate=0.05)


@cache
def command_trim():
    img = new(16, 16, ST[1])
    bevel(img, 0, 0, 16, 16, ST, fill=1)
    for y in range(1, 15):
        put(img, 1, y, ST[2])
    for x in range(1, 15):
        put(img, x, 1, ST[2])
    put(img, 3, 3, ST[0])
    put(img, 12, 12, ST[0])
    return wear(img, "command_trim", ST, rate=0.06)


# =========================================================================================
# Racks
# =========================================================================================
def _rack_caps():
    """Top / bottom 3-row cap metal; expand_mask16 copies a band over each unlinked end."""
    img = new(16, 16)
    for x in range(16):
        for y, c in ((0, ST[3]), (1, ST[1]), (2, ST[0]), (13, ST[2]), (14, ST[1]), (15, ST[0])):
            put(img, x, y, c)
    for y in (1, 14):
        for x in (2, 13):
            put(img, x, y, ST[4])
            put(img, x + 1, y, ST[0])
    put(img, 0, 0, ST[4])
    return img


@cache
def rack_top():
    img = new(16, 16, B[1])
    bevel(img, 0, 0, 16, 16, B, fill=1)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 5.2:
                put(img, x, y, B[0] if (d > 3.9 or y % 2 == 0) else B[2])
            if d < 1.5:
                put(img, x, y, ST[1])
    put(img, 7, 7, ST[3])
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(img, x, y, ST[2])
    return wear(img, "rack_top", B, rate=0.04)


@cache
def _rack_side_base():
    img = new(16, 16, B[1])
    bevel(img, 0, 0, 16, 16, B, fill=1)
    for y in range(4, 12):
        for x in range(3, 13):
            put(img, x, y, B[0] if y % 2 == 0 else B[2])
        put(img, 3, y, B[0])
        put(img, 12, y, B[3] if y % 2 else B[0])
    return wear(img, "rack_side", B, rate=0.05, region=(0, 0, 16, 4))


@cache
def _server_base():
    img = new(16, 16, B[1])
    for y in range(16):
        put(img, 0, y, B[3])
        put(img, 1, y, B[2])
        put(img, 14, y, B[1])
        put(img, 15, y, B[0])
    for u in range(4):
        y0 = u * 4
        rect(img, 2, y0, 14, y0 + 1, B[3])
        rect(img, 2, y0 + 1, 14, y0 + 3, B[2])
        rect(img, 2, y0 + 3, 14, y0 + 4, B[0])
        put(img, 2, y0, B[4])
        if u != 2:
            for x in (3, 5, 7):
                rect(img, x, y0 + 1, x + 1, y0 + 3, B[0])
        else:                                   # blade with a label and a pull handle
            rect(img, 3, y0 + 1, 6, y0 + 2, SS[1])
            rect(img, 3, y0 + 2, 8, y0 + 3, B[1])
            put(img, 7, y0 + 1, B[3])
        rect(img, 10, y0 + 1, 14, y0 + 3, B[0])
        for lx in (10, 12):
            for ly in (1, 2):
                put(img, lx, y0 + ly, GRN[0])
        put(img, 1, y0 + 1, B[0])
        put(img, 14, y0 + 1, B[0])
    return img


@cache
def _server_glow():
    def frame(t):
        f = int(round(t * 4))
        img = new()
        k = 0
        for u in range(4):
            for lx in (10, 12):
                for ly in (1, 2):
                    k += 1
                    mode = det("srv", k)
                    on = mode < 0.3 or (mode < 0.9 and det("srv", k, f) < 0.5)
                    if on:
                        put(img, lx, u * 4 + ly, GRN[3] if det("srv", "c", k) < 0.5 else GRN[2])
        return img
    return animate("server_rack_glow", "flicker", frame)


@cache
def _drive_base():
    img = new(16, 16, B[0])
    for y in range(16):
        put(img, 0, y, B[3])
        put(img, 15, y, B[1])
    for row in range(4):
        for col in range(2):
            x0, y0 = 1 + col * 7, row * 4
            rect(img, x0, y0, x0 + 7, y0 + 4, ST[1])
            rect(img, x0, y0, x0 + 7, y0 + 1, ST[2])
            rect(img, x0, y0 + 3, x0 + 7, y0 + 4, ST[0])
            for y in range(y0, y0 + 4):
                put(img, x0 + 6, y, ST[0])
            put(img, x0, y0, ST[3])
            rect(img, x0 + 1, y0 + 1, x0 + 2, y0 + 3, SS[1])      # latch
            put(img, x0 + 1, y0 + 1, SS[2])
            rect(img, x0 + 3, y0 + 2, x0 + 5, y0 + 3, PIP_OFF)     # activity pip (unlit)
    return img


@cache
def _drive_glow():
    def frame(t):
        f = int(round(t * 16))
        img = new()
        for row in range(4):
            for col in range(2):
                k = row * 2 + col
                per = (2, 4, 8, 16)[int(det("drv", k) * 4)]
                ph = int(det("drv", "p", k) * 16)
                duty = 0.35 + 0.4 * det("drv", "d", k)
                if ((f + ph) % per) / float(per) < duty and det("drv", k, (f + ph) // max(1, per // 2)) < 0.8:
                    x0, y0 = 1 + col * 7, row * 4
                    put(img, x0 + 3, y0 + 2, AMB[3])
                    put(img, x0 + 4, y0 + 2, AMB[2])
        return img
    return animate("drive_rack_glow", "data", frame)


@cache
def _rack_set(kind):
    if kind == "side":
        return expand_mask16(_art(_rack_side_base()), _rack_caps(), None, width=3)
    base, glow = (_server_base(), _server_glow()) if kind == "server" else (_drive_base(), _drive_glow())
    return expand_mask16(_art(base), _rack_caps(), glow, width=3)


def _art(img):
    from ._kit import Art
    return Art.of(img)


# =========================================================================================
# Blinkenlight panel (kit cube, chroma: base and glow both take the hub tint)
# =========================================================================================
LAMP_X = (1, 4, 7, 10, 13)
LAMP_Y = (2, 4, 6, 8, 10, 12)


@cache
def blinkenlight_panel():
    img = new(16, 16, B[0])
    for x in range(16):
        put(img, x, 0, B[3])
        put(img, x, 14, B[1])
        put(img, x, 15, B[0])
    for y in range(1, 15):
        put(img, 0, y, B[2])
        put(img, 15, y, B[0])
    put(img, 0, 0, B[4])
    rect(img, 5, 14, 11, 15, B[3])
    for y in LAMP_Y:
        for x in LAMP_X:
            put(img, x, y, LAMP_OFF)
            put(img, x + 1, y, LAMP_OFF)
    return img


def _blink_bits(seed):
    """6 rows x 16 frames of 5 lamps: four binary-counter bits plus one status lamp."""
    rows = []
    for r in range(6):
        mul = (1, 3, 5, 7, 3, 1)[r]
        off = int(det("blink", seed, r) * 16)
        rev = r % 2
        steady = det("blink", "s", seed, r) < 0.5
        fr = []
        for f in range(16):
            v = (f * mul + off) & 15
            bits = [(v >> c) & 1 for c in range(4)]
            if rev:
                bits.reverse()
            fr.append(bits + [((f // 4) % 2 if steady else int(f % 8 == 0)) if r in (0, 3, 5) else 0])
        rows.append(fr)
    return rows


@cache
def _blink_rows():
    for seed in range(400):
        rows = _blink_bits(seed)
        counts = [sum(sum(rows[r][f]) for r in range(6)) for f in range(16)]
        if max(counts) <= 15 and min(counts) >= 8:
            return rows
    raise ValueError("blinkenlight: no pattern under the glow cap")


@cache
def blinkenlight_panel_glow():
    rows = _blink_rows()

    def frame(t):
        f = int(round(t * 16)) % 16
        img = new()
        for r, y in enumerate(LAMP_Y):
            for c, x in enumerate(LAMP_X):
                if rows[r][f][c]:
                    put(img, x, y, LAMP[4])
                    put(img, x + 1, y, LAMP[3])
        return img
    return animate("blinkenlight_panel_glow", "data", frame)


# =========================================================================================
# Patch panel
# =========================================================================================
PORT_X = (2, 5, 8, 11)
CABLES = {
    "a": ((0, 2, PL, 5), (1, 1, VC, 7), (3, 0, MR, 9)),
    "b": ((0, 1, NA, 8), (2, 3, PL, 6), (3, 2, VC, 9)),
}


@cache
def _patch_base(v):
    img = new(16, 16, B[1])
    bevel(img, 0, 0, 16, 16, B, fill=1)
    rect(img, 1, 4, 15, 11, B[0])                      # cable trough
    for x in range(1, 15):
        put(img, x, 11, B[2])
    for x in PORT_X:
        for y0 in (1, 12):
            rect(img, x, y0, x + 2, y0 + 2, B[0])
            put(img, x + 1, y0 + 1, ST[1])
        put(img, x, 3, SS[1] if x != 8 else ST[2])     # label ticks
    put(img, 14, 2, ST[2])
    put(img, 14, 13, ST[2])
    runs = []
    for a, b, r, lane in CABLES[v]:
        xa, xb = PORT_X[a], PORT_X[b]
        pts = [(xa, y) for y in range(3, lane)] + [(x, lane) for x in range(min(xa, xb), max(xa, xb) + 1)]
        pts += [(xb, y) for y in range(lane, 12)]
        runs.append((pts, r, (xa, 2), (xb, 12)))
    for pts, r, pa, pb in runs:                        # occlusion first, then the cables
        for x, y in pts:
            if 4 <= y < 11 and x + 1 < 15:
                put(img, x + 1, y, SCR[0])
    for pts, r, pa, pb in runs:
        for x, y in pts:
            put(img, x, y, r[2])
        for x, y in pts:
            if y == 3 or (y > 3 and (x, y - 1) not in pts and (x - 1, y) not in pts):
                put(img, x, y, r[3])
        put(img, pa[0], pa[1], r[3])
        put(img, pb[0], pb[1], r[1])
    return wear(img, "patch_panel", B, rate=0.03, region=(0, 0, 16, 4))


def _patch_ears():
    img = new(16, 16)
    for y in range(16):
        put(img, 0, y, ST[3])
        put(img, 1, y, ST[1])
        put(img, 14, y, ST[2])
        put(img, 15, y, ST[0])
    for x in (1, 14):
        put(img, x, 3, B[0])
        put(img, x, 12, B[0])
    put(img, 0, 0, ST[4])
    put(img, 0, 15, ST[1])
    put(img, 15, 0, ST[1])
    return img


@cache
def _patch_set(v):
    return expand_mask16(_art(_patch_base(v)), _patch_ears(), None, width=2)


# =========================================================================================
# Keyboard shelf
# =========================================================================================
@cache
def keyboard_shelf():
    """Rows 0..5 (x 2..13): the keyboard top. Rows 6..15: the shelf surface."""
    img = new(16, 16, B[1])
    bevel(img, 2, 0, 14, 6, B, fill=2)
    rect(img, 3, 1, 13, 5, B[0])
    for x in (3, 6, 9):
        rect(img, x, 1, x + 2, 2, ST[1])
        put(img, x, 1, ST[2])
    put(img, 12, 1, ST[0])
    put(img, 3, 3, ST[1])
    rect(img, 5, 3, 11, 4, ST[1])
    put(img, 5, 3, ST[2])
    put(img, 12, 3, ST[1])
    bevel(img, 0, 6, 16, 16, B, fill=1)
    for x in range(3, 13):
        put(img, x, 13, B[0])
        put(img, x, 14, B[2])
    return wear(img, "keyboard_shelf", B, rate=0.05, region=(0, 6, 16, 16))


@cache
def keyboard_shelf_glow():
    def frame(t):
        k = 0.5 + 0.5 * math.cos(2 * math.pi * t)
        img = new()
        c = tuple(round(PL[1][i] + (PL[3][i] - PL[1][i]) * k) for i in range(3))
        put(img, 6, 1, c)
        put(img, 7, 1, c)
        put(img, 12, 1, PL[3])
        put(img, 3, 3, PL[2])
        return img
    return animate("keyboard_shelf_glow", "breathing", frame)


# =========================================================================================
# Command console
# =========================================================================================
@cache
def command_console_deck():
    """Rows 0..11: the control deck (row 0 = far edge, by the riser). Rows 12..15: its front edge."""
    img = new(16, 16, B[1])
    bevel(img, 0, 0, 16, 12, B, fill=1)
    rect(img, 1, 2, 6, 9, B[0])                         # keypad well
    for y in (3, 5, 7):
        for x in (2, 4):
            put(img, x, y, ST[1])
    put(img, 2, 3, ST[0])
    put(img, 4, 5, ST[0])
    for x in (7, 9):                                    # slider slots
        rect(img, x, 2, x + 1, 9, B[0])
    put(img, 7, 4, ST[3])
    put(img, 9, 7, ST[3])
    put(img, 7, 5, ST[0])
    put(img, 9, 8, ST[0])
    rect(img, 11, 2, 15, 7, SCR[0])                     # scope glass
    for x in range(11, 15):
        put(img, x, 7, B[3])
    rect(img, 12, 8, 14, 10, MR[2])                     # guarded abort key
    put(img, 12, 8, MR[3])
    put(img, 13, 9, MR[1])
    for x in range(1, 15):
        put(img, x, 10, B[2])
    put(img, 7, 1, PL[0])
    put(img, 9, 1, PL[0])
    rect(img, 0, 12, 16, 13, B[3])
    rect(img, 0, 13, 16, 14, B[1])
    rect(img, 0, 14, 16, 16, B[0])
    return wear(img, "command_console_deck", B, rate=0.04, region=(0, 9, 16, 12))


@cache
def command_console_deck_glow():
    def frame(t):
        f = int(round(t * 16)) % 16
        img = new()
        for i, x in enumerate(range(11, 15)):
            ph = 2 * math.pi * ((i * 4 + f) % 16) / 16.0
            put(img, x, 4 + int(round(1.6 * math.sin(ph))), PL[3])
        put(img, 2, 3, PL[2])
        if (f // 4) % 2 == 0:
            put(img, 4, 5, PL[2])
        put(img, 7, 1, PL[2] if f < 8 else PL[1])
        put(img, 9, 1, PL[1] if f < 8 else PL[2])
        return img
    return animate("command_console_deck_glow", "data", frame)


@cache
def command_console_riser():
    """Rows 0..3: the status strip above the deck. The rest is hidden behind the deck."""
    img = new(16, 16, B[1])
    bevel(img, 0, 0, 16, 16, B, fill=1)
    rect(img, 1, 1, 15, 4, SCR[0])
    for x in range(1, 15):
        put(img, x, 4, B[2])
    for y, col in ((12, ST[3]), (13, ST[2]), (14, ST[1]), (15, ST[0])):   # riser top cap (tiles along the row)
        rect(img, 0, y, 16, y + 1, col)
    return wear(img, "command_console_riser", B, rate=0.04, region=(0, 5, 16, 12))


BARS = ((2, 5), (6, 8), (9, 13))


@cache
def command_console_riser_glow():
    def frame(t):
        img = new()
        for i, (x0, x1) in enumerate(BARS):
            k = 0.5 + 0.5 * math.cos(2 * math.pi * (t + i / 3.0))
            c = tuple(round(PL[1][j] + (PL[3][j] - PL[1][j]) * k) for j in range(3))
            for x in range(x0, x1):
                put(img, x, 2, c)
        put(img, 14, 2, PL[3])
        return img
    return animate("command_console_riser_glow", "breathing", frame)


@cache
def command_console_body():
    """Rows 0..8 upper panel, 9..13 knee panel with vent slots, 14..15 kick plinth."""
    img = new(16, 16, B[1])
    bevel(img, 0, 0, 16, 9, B, fill=1)
    bevel(img, 0, 9, 16, 14, B, fill=1)
    for x in (5, 7, 9):
        rect(img, x, 10, x + 1, 13, B[0])
    rect(img, 2, 2, 5, 3, ST[1])                         # id plate
    put(img, 2, 2, ST[2])
    for x in range(2, 14):
        put(img, x, 6, B[0])
    rect(img, 0, 14, 16, 15, B[1])
    rect(img, 0, 15, 16, 16, B[0])
    put(img, 0, 14, B[2])
    return wear(img, "command_console_body", B, rate=0.05, region=(0, 0, 16, 14))


# =========================================================================================
# Operator chair
# =========================================================================================
@cache
def operator_chair_frame():
    img = new(16, 16, B[2])
    bevel(img, 0, 0, 16, 16, B, fill=2)
    for y in range(1, 15):
        put(img, 14, y, B[1])
    for x in range(1, 15):
        put(img, x, 14, B[1])
    return wear(img, "operator_chair_frame", B, rate=0.06)


@cache
def operator_chair_pad():
    img = new(16, 16, PAD[2])
    bevel(img, 0, 0, 16, 16, PAD, fill=2)
    for y in (4, 8, 12):
        for x in range(1, 15):
            put(img, x, y, PAD[1])
            put(img, x, y + 1, PAD[3])
    for y in range(1, 15):
        put(img, 14, y, PAD[1])
    return wear(img, "operator_chair_pad", PAD, rate=0.05)


# =========================================================================================
# Wall screen (32px)
# =========================================================================================
SW = 3                              # bezel width in texels
BEZEL = {"t": (ST[3], ST[2], SCR[0]), "l": (ST[3], ST[2], SCR[0]),
         "b": (ST[0], ST[1], ST[2]), "r": (ST[0], ST[1], ST[2])}


@cache
def _screen_glass():
    img = new(32, 32, SCR[1])
    for y in range(3, 32, 4):
        for x in range(3, 32, 4):
            put(img, x, y, SCR[2])
    return img


def _edge(mask, x, y):
    best = None
    for bit, d, e in ((8, y, "t"), (4, 31 - y, "b"), (2, x, "l"), (1, 31 - x, "r")):
        if mask & bit and (best is None or d < best[0]):
            best = (d, e)
    return best


@cache
def _screen_face(mask):
    img = _screen_glass().copy()
    for y in range(32):
        for x in range(32):
            e = _edge(mask, x, y)
            if e and e[0] < SW:
                put(img, x, y, BEZEL[e[1]][e[0]])
    if mask & 4 and mask & 1:
        put(img, 29, 30, PL[1])     # standby pip, baked (not emissive)
    return img


def _idle_rows():
    rows = []
    for i, y in enumerate((1, 5, 9, 25, 29)):
        pat, x, k = [0] * 32, 0, 0
        while x < 30:
            ln = (1 + int(det("idle", i, k, "l") * 5)) if i < 3 else (3 + int(det("idle", i, k, "l") * 7))
            lvl = 3 if det("idle", i, k, "c") < 0.12 else (2 if det("idle", i, k, "c") < 0.55 else 1)
            for j in range(ln):
                if x + j < 31:
                    pat[x + j] = lvl
            x += ln + 1 + int(det("idle", i, k, "g") * 3)
            k += 1
        rows.append((y, (2, -2, 4, -2, 2)[i], pat))
    return rows


def _wave(x, f):
    ph = 2 * math.pi * ((x + 2 * f) % 32) / 32.0
    return 17 + int(round(3.3 * math.sin(ph) + 1.3 * math.sin(3 * ph + 0.6)))


@cache
def _screen_idle():
    rows = _idle_rows()

    def frame(t):
        f = int(round(t * 16)) % 16
        img = new(32, 32)
        for y, speed, pat in rows:
            for x in range(32):
                c = pat[(x + speed * f) % 32]
                if c:
                    put(img, x, y, PL[c])
        for x in range(0, 32, 4):
            put(img, x, 17, PL[1])
        for x in range(32):
            y0, y1 = _wave(x - 1, f), _wave(x, f)
            lo, hi = (y0 + 1, y1) if y1 > y0 else ((y1, y0 - 1) if y1 < y0 else (y1, y1))
            for y in range(lo, hi + 1):
                put(img, x, y, PL[3])
        return img
    return animate("wall_screen_glow", "data", frame)


@cache
def _screen_glow(mask):
    from ._kit import Art
    art = _screen_idle()

    def clear(fr):
        out = fr.copy()
        px = out.load()
        for y in range(32):
            for x in range(32):
                e = _edge(mask, x, y)
                if e and e[0] < SW + 1:
                    px[x, y] = (0, 0, 0, 0)
        return out
    return Art([clear(fr) for fr in art.frames], art.anim, art.interpolate)


# --- 3x5 font -----------------------------------------------------------------------------
_F = {
    " ": "000000000000000", "!": "010010010000010", '"': "101101000000000", "#": "101111101111101",
    "$": "011110010011110", "%": "101001010100101", "&": "010101010101011", "'": "010010000000000",
    "(": "001010010010001", ")": "100010010010100", "*": "000101010101000", "+": "000010111010000",
    ",": "000000000010100", "-": "000000111000000", ".": "000000000000010", "/": "001001010100100",
    "0": "111101101101111", "1": "010110010010111", "2": "111001111100111", "3": "111001111001111",
    "4": "101101111001001", "5": "111100111001111", "6": "111100111101111", "7": "111001001010010",
    "8": "111101111101111", "9": "111101111001111", ":": "000010000010000", ";": "000010000010100",
    "<": "001010100010001", "=": "000111000111000", ">": "100010001010100", "?": "111001011000010",
    "@": "111101111100111", "A": "010101111101101", "B": "110101110101110", "C": "011100100100011",
    "D": "110101101101110", "E": "111100110100111", "F": "111100110100100", "G": "011100101101011",
    "H": "101101111101101", "I": "111010010010111", "J": "001001001101010", "K": "101101110101101",
    "L": "100100100100111", "M": "101111111101101", "N": "110101101101101", "O": "010101101101010",
    "P": "110101110100100", "Q": "010101101111011", "R": "110101110101101", "S": "011100010001110",
    "T": "111010010010010", "U": "101101101101111", "V": "101101101101010", "W": "101101111111101",
    "X": "101101010101101", "Y": "101101010010010", "Z": "111001010100111", "[": "011010010010011",
    "\\": "100100010001001", "]": "110010010010110", "^": "010101000000000", "_": "000000000000111",
}
FONT_SYMBOLS = ("arrow_up", "arrow_down", "arrow_left", "arrow_right", "block", "bar_1", "bar_2",
                "bar_3", "bar_4", "degree", "check", "warning", "dot", "shade", "bar_left", "ellipsis")
_SYM = ("010111010010010", "010010010111010", "010110111110010", "010011111011010", "111111111111111",
        "000000000000111", "000000000111111", "000000111111111", "000111111111111", "010101010000000",
        "000001001101010", "010010101101111", "000000010000000", "101010101010101", "100100100100100",
        "000000000000101")
FONT_CELL = (4, 6)
FONT_PAGE = 40
GLYPHS = [_F[chr(c)] for c in range(0x20, 0x60)] + list(_SYM)


def _font_page(page):
    def paint():
        img = new(32, 32)
        for i, bits in enumerate(GLYPHS[page * FONT_PAGE:(page + 1) * FONT_PAGE]):
            cx, cy = (i % 8) * FONT_CELL[0], (i // 8) * FONT_CELL[1]
            for j, b in enumerate(bits):
                if b == "1":
                    put(img, cx + j % 3, cy + j // 3, INK)
        return img
    return paint


# =========================================================================================
# Texture table
# =========================================================================================
TEXTURES = {
    "command_metal": command_metal, "command_trim": command_trim, "rack_top": rack_top,
    "blinkenlight_panel": blinkenlight_panel, "blinkenlight_panel_glow": blinkenlight_panel_glow,
    "keyboard_shelf": keyboard_shelf, "keyboard_shelf_glow": keyboard_shelf_glow,
    "command_console_deck": command_console_deck, "command_console_deck_glow": command_console_deck_glow,
    "command_console_riser": command_console_riser, "command_console_riser_glow": command_console_riser_glow,
    "command_console_body": command_console_body,
    "operator_chair_frame": operator_chair_frame, "operator_chair_pad": operator_chair_pad,
    "wall_screen_font_0": _font_page(0), "wall_screen_font_1": _font_page(1),
}
RACK_MASKS = (0, 4, 8, 12)          # bit 8 = top cap, bit 4 = bottom cap
for _m in RACK_MASKS:
    TEXTURES["rack_side_f%d" % _m] = lambda m=_m: _rack_set("side")["_f%d" % m]
    for _k in ("server", "drive"):
        TEXTURES["%s_rack_f%d" % (_k, _m)] = lambda k=_k, m=_m: _rack_set(k)["_f%d" % m]
        TEXTURES["%s_rack_glow_f%d" % (_k, _m)] = lambda k=_k, m=_m: _rack_set(k)["_glow_f%d" % m]
for _m in range(4):                 # bit 2 = left ear, bit 1 = right ear
    for _v in "ab":
        TEXTURES["patch_panel_%s_f%d" % (_v, _m)] = lambda v=_v, m=_m: _patch_set(v)["_f%d" % m]
for _m in range(16):
    TEXTURES["wall_screen_f%d" % _m] = lambda m=_m: _screen_face(m)
    TEXTURES["wall_screen_glow_f%d" % _m] = lambda m=_m: _screen_glow(m)


# =========================================================================================
# Model helpers
# =========================================================================================
def _auto_uv(face, f, t):
    (x0, y0, z0), (x1, y1, z1) = f, t
    uv = {"north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
          "west": [z0, 16 - y1, z1, 16 - y0], "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
          "up": [x0, z0, x1, z1], "down": [x0, 16 - z1, x1, 16 - z0]}[face]
    for a in (0, 1):                                    # slide back inside 0..16
        lo, hi = min(uv[a], uv[a + 2]), max(uv[a], uv[a + 2])
        d = -lo if lo < 0 else (16 - hi if hi > 16 else 0)
        uv[a] += d
        uv[a + 2] += d
    return uv


_EDGE = {"north": (2, 0, 0), "south": (2, 1, 16), "west": (0, 0, 0), "east": (0, 1, 16),
         "down": (1, 0, 0), "up": (1, 1, 16)}
ALL = ("north", "east", "south", "west", "up", "down")


def box(frm, to, tex, faces=ALL, over=None, emit=0, rot=None):
    """One element. tex = default texture ref; over = {face: ref | (ref, uv) | (ref, uv, rotation)}.
    Faces on the block boundary get a cullface (unless the element is rotated)."""
    over = over or {}
    fs = {}
    for f in faces:
        spec = over.get(f, tex)
        if spec is None:
            continue
        spec = spec if isinstance(spec, tuple) else (spec,)
        face = {"texture": spec[0], "uv": list(spec[1]) if len(spec) > 1 and spec[1] else _auto_uv(f, frm, to)}
        if len(spec) > 2 and spec[2]:
            face["rotation"] = spec[2]
        axis, end, lim = _EDGE[f]
        if not rot and (frm, to)[end][axis] == lim:
            face["cullface"] = f
        fs[f] = face
    e = {"from": list(frm), "to": list(to)}
    if rot:
        e["rotation"] = rot
    if emit:
        e["shade"] = False
        e["light_emission"] = emit
    e["faces"] = fs
    return e


def mdl(c, textures, elements, particle, cutout=False):
    m = {"parent": "minecraft:block/block",
         "textures": dict({"particle": c.tex(particle)}, **{k: c.tex(v) for k, v in textures.items()}),
         "elements": elements}
    if cutout:
        m["render_type"] = "minecraft:cutout"
    return m


ROT_Y = {"north": 0, "east": 90, "south": 180, "west": 270}


def _var(c, model, facing):
    v = {"model": c.model(model)}
    if ROT_Y[facing]:
        v["y"] = ROT_Y[facing]
    return v


def _tf(b):
    return "true" if b else "false"


# =========================================================================================
# command_console
# =========================================================================================
SLOPE_X = {"angle": -22.5, "axis": "x", "origin": [8, 12, 12]}
SLOPE_Z = {"angle": 22.5, "axis": "z", "origin": [12, 12, 8]}
CONSOLE_TEX = {"deck": "command_console_deck", "deck_glow": "command_console_deck_glow",
               "riser": "command_console_riser", "riser_glow": "command_console_riser_glow",
               "body": "command_console_body", "trim": "command_trim"}


def _console_straight(c, capl, capr):
    """capl: cheek on the operator's left (east, x 15..16); capr: on the right (west, x 0..1)."""
    x0, x1 = (1 if capr else 0), (15 if capl else 16)
    deck = ("#deck", [x0, 0, x1, 12], 180)
    els = [
        box([x0, 0, 3], [x1, 2, 16], "#body", faces=("north", "east", "south", "west", "down")),
        box([x0, 2, 4], [x1, 7, 16], "#body", faces=("north", "east", "south", "west")),
        box([x0, 7, 12], [x1, 16, 16], "#body", faces=("north", "east", "south", "west", "up"),
            over={"north": ("#riser", [16 - x1, 0, 16 - x0, 9]), "up": ("#riser", [x0, 12, x1, 16])}),
        box([x0, 7, 12], [x1, 16, 16], None, faces=("north",), emit=15,
            over={"north": ("#riser_glow", [16 - x1, 0, 16 - x0, 9])}),
        box([x0, 10.5, 0.5], [x1, 12, 12], "#body", rot=SLOPE_X,
            faces=("north", "east", "west", "up", "down"),
            over={"up": deck, "north": ("#deck", [x0, 12, x1, 13.5]), "down": ("#body", [x0, 0, x1, 9]),
                  "east": ("#deck", [0, 12, 11.5, 13.5]), "west": ("#deck", [0, 12, 11.5, 13.5])}),
        box([x0, 10.5, 0.5], [x1, 12, 12], None, rot=SLOPE_X, faces=("up",), emit=15,
            over={"up": ("#deck_glow", [x0, 0, x1, 12], 180)}),
    ]
    for cap, xa in ((capr, 0), (capl, 15)):
        if cap:
            els.append(box([xa, 0, 2], [xa + 1, 10, 16], "#trim"))
            els.append(box([xa, 10, 11], [xa + 1, 16, 16], "#trim", faces=("north", "east", "south", "west", "up")))
    return mdl(c, CONSOLE_TEX, els, "command_console_body", cutout=True)


def _console_inner(c):
    els = [
        box([0, 0, 3], [16, 2, 16], "#body", faces=("north", "east", "south", "west", "down")),
        box([3, 0, 0], [16, 2, 3], "#body", faces=("north", "east", "west", "down")),
        box([0, 2, 4], [16, 7, 16], "#body", faces=("north", "east", "south", "west")),
        box([4, 2, 0], [16, 7, 4], "#body", faces=("north", "east", "west")),
        box([0, 7, 12], [16, 16, 16], "#body", faces=("north", "east", "south", "west", "up"),
            over={"north": ("#riser", [0, 0, 16, 9]), "up": ("#riser", [0, 12, 16, 16])}),
        box([0, 7, 12], [16, 16, 16], None, faces=("north",), emit=15,
            over={"north": ("#riser_glow", [0, 0, 16, 9])}),
        box([12, 7, 0], [16, 16, 12], "#body", faces=("north", "east", "west", "up"),
            over={"west": ("#riser", [0, 0, 12, 9]), "up": ("#riser", [0, 12, 12, 16], 90)}),
        box([12, 7, 0], [16, 16, 12], None, faces=("west",), emit=15,
            over={"west": ("#riser_glow", [0, 0, 12, 9])}),
        box([0, 10.5, 0.5], [16, 12, 12], "#body", rot=SLOPE_X, faces=("north", "west", "up", "down"),
            over={"up": ("#deck", [0, 0, 16, 12], 180), "north": ("#deck", [0, 12, 16, 13.5]),
                  "west": ("#deck", [0, 12, 11.5, 13.5]), "down": ("#body", [0, 0, 16, 9])}),
        box([0, 10.5, 0.5], [16, 12, 12], None, rot=SLOPE_X, faces=("up",), emit=15,
            over={"up": ("#deck_glow", [0, 0, 16, 12], 180)}),
        box([0.5, 10.5, 0], [12, 12, 16], "#body", rot=SLOPE_Z, faces=("north", "west", "up", "down"),
            over={"up": ("#deck", [0, 0, 16, 12], 90), "west": ("#deck", [0, 12, 16, 13.5]),
                  "north": ("#deck", [0, 12, 11.5, 13.5]), "down": ("#body", [0, 0, 16, 9])}),
        box([0.5, 10.5, 0], [12, 12, 16], None, rot=SLOPE_Z, faces=("up",), emit=15,
            over={"up": ("#deck_glow", [0, 0, 16, 12], 90)}),
    ]
    return mdl(c, CONSOLE_TEX, els, "command_console_body", cutout=True)


def _console_outer(c):
    side = ("north", "east", "south", "west")
    els = [
        box([3, 0, 3], [16, 2, 16], "#body", faces=side + ("down",)),
        box([4, 2, 4], [16, 6.5, 16], "#body", faces=side),
        box([1, 6.5, 1], [16, 8, 16], "#body", faces=side + ("up", "down"),
            over={"up": ("#deck", [0, 0, 15, 11]), "north": ("#deck", [0, 12, 15, 13.5]),
                  "west": ("#deck", [0, 12, 15, 13.5])}),
        box([1, 6.5, 1], [16, 8, 16], None, faces=("up",), emit=15,
            over={"up": ("#deck_glow", [0, 0, 15, 11])}),
        box([5, 8, 5], [16, 9.5, 16], "#body", faces=side + ("up",),
            over={"up": ("#body", [0, 0, 11, 9]), "north": ("#deck", [0, 12, 11, 13.5]),
                  "west": ("#deck", [0, 12, 11, 13.5])}),
        box([9, 9.5, 9], [16, 11, 16], "#body", faces=side + ("up",),
            over={"up": ("#deck", [0, 0, 7, 7]), "north": ("#deck", [0, 12, 7, 13.5]),
                  "west": ("#deck", [0, 12, 7, 13.5])}),
        box([12, 11, 12], [16, 16, 16], "#body", faces=side + ("up",),
            over={"north": ("#riser", [9, 0, 13, 5]), "west": ("#riser", [9, 0, 13, 5]),
                  "up": ("#riser", [0, 12, 4, 16])}),
        box([12, 11, 12], [16, 16, 16], None, faces=("north", "west"), emit=15,
            over={"north": ("#riser_glow", [9, 0, 13, 5]), "west": ("#riser_glow", [9, 0, 13, 5])}),
    ]
    return mdl(c, CONSOLE_TEX, els, "command_console_body", cutout=True)


def console_models(c):
    out = {}
    for l in (1, 0):
        for r in (1, 0):
            out["command_console_c%d%d" % (l, r)] = _console_straight(c, l, r)
    out["command_console_inner"] = _console_inner(c)
    out["command_console_outer"] = _console_outer(c)
    return out


def console_state(c):
    v = {}
    for f in ROT_Y:
        for shape in ("straight", "inner", "outer"):
            for l in (False, True):
                for r in (False, True):
                    m = ("command_console_c%d%d" % (not l, not r)) if shape == "straight" \
                        else "command_console_" + shape
                    v["facing=%s,left=%s,right=%s,shape=%s" % (f, _tf(l), _tf(r), shape)] = _var(c, m, f)
    return {"variants": v}


# =========================================================================================
# wall_screen
# =========================================================================================
def screen_models(c):
    out = {}
    for idle in (1, 0):
        for m in (15,) + tuple(range(15)):
            tx = {"face": "wall_screen_f%d" % m, "metal": "command_metal"}
            faces = ["north", "south"] + [f for f, bit in (("up", 8), ("down", 4), ("east", 2), ("west", 1))
                                          if m & bit]
            els = [box([0, 0, 14], [16, 16, 16], "#metal", faces=faces,
                       over={"north": ("#face", [0, 0, 16, 16]), "up": ("#metal", [0, 0, 16, 2]),
                             "down": ("#metal", [0, 14, 16, 16]), "east": ("#metal", [0, 0, 2, 16]),
                             "west": ("#metal", [14, 0, 16, 16])})]
            if idle:
                tx["glow"] = "wall_screen_glow_f%d" % m
                els.append(box([0, 0, 14], [16, 16, 16], None, faces=("north",), emit=15,
                               over={"north": ("#glow", [0, 0, 16, 16])}))
            out["wall_screen_%s_f%d" % ("idle" if idle else "live", m)] = mdl(
                c, tx, els, "wall_screen_f15", cutout=True)
    return out


def screen_state(c):
    v = {}
    for f in ROT_Y:
        for m in range(16):
            up, down, left, right = (not m & 8), (not m & 4), (not m & 2), (not m & 1)
            for idle in (True, False):
                key = "down=%s,facing=%s,idle=%s,left=%s,right=%s,up=%s" % (
                    _tf(down), f, _tf(idle), _tf(left), _tf(right), _tf(up))
                v[key] = _var(c, "wall_screen_%s_f%d" % ("idle" if idle else "live", m), f)
    return {"variants": v}


SCREEN_TEX = {"metal": "command_metal", "font0": "wall_screen_font_0", "font1": "wall_screen_font_1"}
for _m in range(16):
    SCREEN_TEX["f%d" % _m] = "wall_screen_f%d" % _m
    SCREEN_TEX["glow_f%d" % _m] = "wall_screen_glow_f%d" % _m


# =========================================================================================
# keyboard_shelf
# =========================================================================================
def shelf_models(c):
    tx = {"top": "keyboard_shelf", "glow": "keyboard_shelf_glow", "metal": "command_metal",
          "trim": "command_trim"}
    side = ("north", "east", "south", "west")
    els = [
        box([2, 3, 15], [14, 8, 16], "#metal", faces=side + ("down",)),
        box([1, 7, 6], [15, 8, 15], "#metal", faces=("east", "west", "up", "down"),
            over={"up": ("#top", [1, 6, 15, 15], 180)}),
        box([1, 6.5, 5], [15, 8.5, 6], "#trim", over={"north": ("#trim", [1, 0, 15, 2])}),
        box([3, 3, 9], [4, 7, 15], "#trim", faces=("north", "east", "west", "down")),
        box([12, 3, 9], [13, 7, 15], "#trim", faces=("north", "east", "west", "down")),
        box([2, 8, 7.5], [14, 8.75, 13.5], "#trim", faces=side + ("up",),
            over={"up": ("#top", [2, 0, 14, 6], 180), "north": ("#top", [2, 5, 14, 5.75]),
                  "south": ("#top", [2, 0, 14, 0.75]), "east": ("#top", [2, 0, 8, 0.75]),
                  "west": ("#top", [2, 0, 8, 0.75])}),
        box([2, 8, 7.5], [14, 8.75, 13.5], None, faces=("up",), emit=15,
            over={"up": ("#glow", [2, 0, 14, 6], 180)}),
    ]
    return {"keyboard_shelf": mdl(c, tx, els, "command_metal", cutout=True)}


# =========================================================================================
# operator_chair
# =========================================================================================
CHAIR_FACINGS = ("north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west")


def _chair(c, diag):
    F, P = "#frame", "#pad"
    parts = [
        ([3, 0, 7], [13, 1, 9], F), ([7, 0, 2], [9, 1, 7], F), ([7, 0, 9], [9, 1, 14], F),
        ([6, 1, 6], [10, 2, 10], F), ([7, 2, 7], [9, 6, 9], F), ([5, 6, 5], [11, 7, 11], F),
        ([3, 7, 3], [13, 8, 13], F), ([3.5, 8, 3], [12.5, 9.5, 12], P),
        ([7, 7, 13.5], [9, 12, 14.5], F), ([3, 10, 13], [13, 20, 14], F),
        ([3.5, 10.5, 12], [12.5, 19.5, 13], P), ([5, 20, 12.5], [11, 22.5, 14], P),
        ([2, 7, 6], [3, 11.5, 8], F), ([13, 7, 6], [14, 11.5, 8], F),
        ([1.5, 11.5, 4], [3.5, 12.5, 10.5], P), ([12.5, 11.5, 4], [14.5, 12.5, 10.5], P),
        ([3, 11, 11.5], [4.5, 19, 12], P), ([11.5, 11, 11.5], [13, 19, 12], P),
    ]
    rot = {"angle": -45, "axis": "y", "origin": [8, 8, 8]} if diag else None
    els = []
    for frm, to, t in parts:
        e = box(frm, to, t, rot=rot)
        for face in e["faces"].values():
            face.pop("cullface", None)
        els.append(e)
    return mdl(c, {"frame": "operator_chair_frame", "pad": "operator_chair_pad"}, els, "operator_chair_frame")


def chair_models(c):
    return {"operator_chair": _chair(c, False), "operator_chair_diag": _chair(c, True)}


def chair_state(c):
    v = {}
    for i, f in enumerate(CHAIR_FACINGS):
        e = {"model": c.model("operator_chair_diag" if i % 2 else "operator_chair")}
        if i // 2:
            e["y"] = (i // 2) * 90
        v["facing=%s" % f] = e
    return {"variants": v}


# =========================================================================================
# racks
# =========================================================================================
def rack_models(kind):
    def fn(c):
        out = {}
        for m in (12, 8, 4, 0):
            tx = {"front": "%s_rack_f%d" % (kind, m), "glow": "%s_rack_glow_f%d" % (kind, m),
                  "side": "rack_side_f%d" % m, "top": "rack_top"}
            els = [box([0, 0, 0], [16, 16, 16], "#side",
                       over={"north": "#front", "up": "#top", "down": "#top"}),
                   box([0, 0, 0], [16, 16, 16], None, faces=("north",), emit=15, over={"north": "#glow"})]
            out["%s_rack_f%d" % (kind, m)] = mdl(c, tx, els, "rack_side_f12", cutout=True)
        return out
    return fn


def rack_state(kind):
    def fn(c):
        v = {}
        for f in ROT_Y:
            for up in (False, True):
                for down in (False, True):
                    m = (0 if up else 8) | (0 if down else 4)
                    v["down=%s,facing=%s,up=%s" % (_tf(down), f, _tf(up))] = _var(c, "%s_rack_f%d" % (kind, m), f)
        return {"variants": v}
    return fn


def _rack_tex(kind):
    t = {"top": "rack_top"}
    for m in RACK_MASKS:
        t["front_f%d" % m] = "%s_rack_f%d" % (kind, m)
        t["glow_f%d" % m] = "%s_rack_glow_f%d" % (kind, m)
        t["side_f%d" % m] = "rack_side_f%d" % m
    return t


# =========================================================================================
# patch_panel
# =========================================================================================
def patch_models(c):
    out = {}
    for m in (3, 2, 1, 0):
        for v in "ab":
            tx = {"face": "patch_panel_%s_f%d" % (v, m), "metal": "command_metal"}
            faces = ["north", "south", "up", "down"] + [f for f, bit in (("east", 2), ("west", 1)) if m & bit]
            els = [box([0, 0, 13], [16, 16, 16], "#metal", faces=faces,
                       over={"north": ("#face", [0, 0, 16, 16]), "up": ("#metal", [0, 0, 16, 3]),
                             "down": ("#metal", [0, 13, 16, 16]), "east": ("#metal", [0, 0, 3, 16]),
                             "west": ("#metal", [13, 0, 16, 16])})]
            out["patch_panel_%s_f%d" % (v, m)] = mdl(c, tx, els, "command_metal")
    return out


def patch_state(c):
    out = {}
    for f in ROT_Y:
        for left in (False, True):
            for right in (False, True):
                m = (0 if left else 2) | (0 if right else 1)
                out["facing=%s,left=%s,right=%s" % (f, _tf(left), _tf(right))] = [
                    _var(c, "patch_panel_%s_f%d" % (v, m), f) for v in "ab"]
    return {"variants": out}


PATCH_TEX = {"metal": "command_metal"}
for _m in range(4):
    for _v in "ab":
        PATCH_TEX["%s_f%d" % (_v, _m)] = "patch_panel_%s_f%d" % (_v, _m)


# =========================================================================================
BLOCKS = [
    Block("command_console", shape="custom", emissive=True, textures=dict(
        CONSOLE_TEX), model=console_models, blockstate=console_state, item_model="command_console_c11"),
    Block("wall_screen", shape="custom", emissive=True, light=6, block_entity=True, textures=SCREEN_TEX,
          model=screen_models, blockstate=screen_state, item_model="wall_screen_idle_f15"),
    Block("keyboard_shelf", shape="custom", emissive=True,
          textures={"top": "keyboard_shelf", "glow": "keyboard_shelf_glow", "metal": "command_metal",
                    "trim": "command_trim"}, model=shelf_models,
          blockstate=lambda c: {"variants": {"facing=%s" % f: _var(c, "keyboard_shelf", f) for f in ROT_Y}}),
    Block("operator_chair", shape="custom",
          textures={"frame": "operator_chair_frame", "pad": "operator_chair_pad"},
          model=chair_models, blockstate=chair_state, item_model="operator_chair"),
    Block("server_rack", shape="custom", emissive=True, textures=_rack_tex("server"),
          model=rack_models("server"), blockstate=rack_state("server"), item_model="server_rack_f12"),
    Block("drive_rack", shape="custom", emissive=True, textures=_rack_tex("drive"),
          model=rack_models("drive"), blockstate=rack_state("drive"), item_model="drive_rack_f12"),
    Block("patch_panel", shape="custom", textures=PATCH_TEX, model=patch_models, blockstate=patch_state,
          item_model="patch_panel_a_f3"),
    Block("blinkenlight_panel", shape="cube", chroma=True, emissive=True,
          textures={"all": "blinkenlight_panel"}),
]
