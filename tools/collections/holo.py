"""
Holo collection -- holograms, signs and display stands (8 blocks).

Blocks and the blockstate properties the Java side must register (same names):

  holo_projector  face = down|up|north|south|east|west (the side the puck is MOUNTED on; it emits
                  the other way).                                         chroma, BE
  holo_sign       facing = north|east|south|west (the side the text front looks at). chroma, BE
  arrow_sign      facing (as holo_sign) + direction = up|up_right|right|down_right|down|
                  down_left|left|up_left (as read from the front).        chroma, light 7
  world_globe     world = station|luna|mars|cindara|glacira|greenxertz|nyxara. The model is the
                  base only; the BE draws the sphere above the cradle (centre y=11.5, r=4.5). BE
  holo_table      no properties.                                          chroma, BE
  holo_curtain    axis = x|z (the horizontal axis the sheet runs along) + up/down/north/south/
                  east/west booleans (true = same curtain, same axis, on that side; only the four
                  in-plane ones are read).                                chroma, light 8, no collision
  holo_plinth     no properties.
  ticker_strip    face + axis + pos_link + neg_link, exactly as the kit's strip blocks. chroma

Chroma: housings are never tinted (tint=False on every base element); only the glow layers carry
tintindex 0, and those are painted near-neutral so the hub colour does the work. The two
non-chroma blocks glow in one fixed hue: plasma for the plinth, the world's own ramp for the globe.

Block-entity renderer textures are shipped beside the block textures (same folder) and are listed
under `be_*` roles of the owning block; no model references them:

  holo_projector_beam      16 flow     translucent light cone, scanlines rising (tile vertically)
  holo_projector_scanline  16 flow     translucent overlay for any projected content
  holo_projector_shimmer   16 flicker  sparse sparkle overlay
  holo_projector_idle      32 still    wireframe gem shown when nothing is projected
  holo_sign_glyphs_0..2    32 still    3x5 font in 4x6 cells, 8 x 5 cells per page; glyph index
                                       i = codepoint - 32 (ASCII 32..126), then extras from 95
                                       (GLYPH_EXTRAS); page = i // 40, col = i % 40 % 8,
                                       row = i % 40 // 8
  holo_sign_icons          32 still    4 x 4 atlas of 8x8 icons, order = SIGN_ICONS
  holo_sign_backing        32 flow     translucent panel fill behind the text
  world_globe_<world>      GLOBE still equirectangular surface (x = longitude, wraps)
  world_globe_<world>_glow GLOBE still emissive lights for the same map (alpha 0/255)
  world_globe_scan         16 flow     translucent latitude sweep, drawn on a slightly larger shell
  world_globe_halo         16 still    translucent rim billboard
  holo_table_grid          32 data     translucent terrain wireframe for the well (tile)
  holo_table_beam          16 flow     translucent column above the well
  holo_table_blips         16 flicker  marker blips over the grid

All of them are authored near-neutral so the renderer can tint them, except the globe maps.
"""
import functools
import math

from ._kit import (Art, Block, FACES, _AXES, _DIRS, _rot, animate, bevel, cap_layer, clamp, det,
                   lerp, new, put, ramp, ramp5, rect, strip_rotation, transpose, wear)

COLLECTION = "holo"

BODY = ramp("body")                 # house near-black navy
STEEL = ramp("starsteel")           # sparse bright trim
PLASMA = ramp("plasma")             # fixed accent of the non-chroma plinth
N = ramp5((222, 232, 242))          # near-neutral glow (tint target)
WORLDS = ("station", "luna", "mars", "cindara", "glacira", "greenxertz", "nyxara")
DIRECTIONS = {"up": (0, -1), "up_right": (1, -1), "right": (1, 0), "down_right": (1, 1),
              "down": (0, 1), "down_left": (-1, 1), "left": (-1, 0), "up_left": (-1, -1)}
GLOBE = 32                          # globe map size; the maps are procedural, 16 also works
BLOCK_PARENT = "minecraft:block/block"


# =========================================================================================
# local painting helpers
# =========================================================================================
def hl(img, x0, x1, y, rgb, a=255):
    for x in range(x0, x1):
        put(img, x, y, rgb, a)


def vl(img, x, y0, y1, rgb, a=255):
    for y in range(y0, y1):
        put(img, x, y, rgb, a)


def band(img, x0, x1, y_hi, y_lo, r, fill=2):
    """Elevation band for block rows y_lo..y_hi (block space, y up) between columns x0..x1."""
    r0, r1 = 16 - y_hi, 16 - y_lo
    if r1 - r0 == 1:
        hl(img, x0, x1, r0, r[fill])
        put(img, x0, r0, r[3])
        put(img, x1 - 1, r0, r[0])
    else:
        bevel(img, x0, r0, x1, r1, r, fill)


def rings(spec, r=BODY, bg=1):
    """Plan view: concentric bevelled squares, [(inset, fill), ...] outermost first."""
    img = new(16, 16, r[bg])
    for inset, fill in spec:
        bevel(img, inset, inset, 16 - inset, 16 - inset, r, fill)
    return img


def square(img, i, rgb, a=255, skip=()):
    """1px square outline at inset i (corners included unless listed in skip)."""
    j = 15 - i
    for k in range(i, j + 1):
        for (x, y) in ((k, i), (k, j), (i, k), (j, k)):
            if (x, y) not in skip:
                put(img, x, y, rgb, a)


def line(img, x0, y0, x1, y1, rgb, a=255):
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        put(img, x0, y0, rgb, a)
        if x0 == x1 and y0 == y1:
            return
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def pulse(t, lo=0.0, hi=1.0):
    return lo + (hi - lo) * (0.5 + 0.5 * math.cos(2 * math.pi * t))


def bitmap(img, x0, y0, rows, rgb, a=255):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "#":
                put(img, x0 + i, y0 + j, rgb, a)


def _noise(seed, x, y, cell, period, wrap_y=False):
    gx, gy = x / float(cell), y / float(cell)
    ix, iy = int(math.floor(gx)), int(math.floor(gy))
    fx, fy = gx - ix, gy - iy
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    n = max(1, period // cell)

    def v(a, b):
        return det(seed, cell, a % n, b % n if wrap_y else b)
    top = v(ix, iy) + (v(ix + 1, iy) - v(ix, iy)) * fx
    bot = v(ix, iy + 1) + (v(ix + 1, iy + 1) - v(ix, iy + 1)) * fx
    return top + (bot - top) * fy


def fbm(seed, x, y, size):
    """Deterministic value noise in [0,1], seamless in x (longitude)."""
    c = size // 4
    return (0.55 * _noise(seed, x, y, c, size) + 0.30 * _noise(seed, x, y, max(1, c // 2), size)
            + 0.15 * _noise(seed, x, y, max(1, c // 4), size))


def top_k(cands, size, share=0.115):
    """Keep the strongest candidates [(score, x, y, rgb)] within the 12% emissive budget."""
    keep = sorted(cands, key=lambda c: (-c[0], c[2], c[1]))[:int(size * size * share)]
    img = new(size, size)
    for _s, x, y, rgb in keep:
        put(img, x, y, rgb)
    return img


# =========================================================================================
# holo_projector -- emitter puck
# =========================================================================================
def holo_projector_top():
    img = rings([(2, 1), (3, 2), (5, 0)])
    rect(img, 6, 6, 10, 10, BODY[1])                 # lens glass
    put(img, 6, 6, BODY[2])
    rect(img, 7, 7, 9, 9, BODY[2])
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        put(img, x, y, STEEL[1])                     # bolts
    for (x, y) in ((7, 4), (8, 4), (7, 11), (8, 11), (4, 7), (4, 8), (11, 7), (11, 8)):
        put(img, x, y, BODY[0])                      # cooling slots
    return wear(img, "holo_projector_top", BODY, rate=0.04)


def holo_projector_side():
    img = new(16, 16, BODY[0])
    band(img, 2, 14, 2, 0, BODY, 1)                  # base plate
    band(img, 3, 13, 5, 2, BODY, 2)                  # ring
    band(img, 5, 11, 6, 5, STEEL, 1)                 # lens collar
    hl(img, 4, 12, 12, BODY[0])                      # light channel
    return wear(img, "holo_projector_side", BODY, rate=0.04)


def holo_projector_top_glow():
    def frame(t):
        k = pulse(t)
        img = new()
        for i in range(5, 11):
            for (x, y) in ((i, 5), (i, 10), (5, i), (10, i)):
                put(img, x, y, lerp(N[2], N[3], k))
        rect(img, 7, 7, 9, 9, lerp(N[3], N[4], 1 - k))
        return img
    return animate("holo_projector_top_glow", "breathing", frame)


def holo_projector_side_glow():
    def frame(t):
        img = new()
        s = int(round(t * 16))
        for x in range(4, 12):
            d = (x - s) % 8
            if d < 2:
                put(img, x, 12, N[4] if d == 1 else N[3])
        return img
    return animate("holo_projector_side_glow", "flow", frame)


def holo_projector_beam():
    def frame(t):
        img = new()
        s = int(round(t * 16))
        for y in range(16):
            rise = (y + s) % 16
            for x in range(16):
                edge = abs(x - 7.5) / 7.5
                a = 26 + 18 * (1 - edge)
                if rise % 4 == 0:
                    a += 44
                if rise == 0:
                    a += 70
                if det("beam", x, (y + s) % 16) < 0.05:
                    a += 60
                put(img, x, y, N[4] if rise == 0 else N[3], int(clamp(a / 255.0) * 255))
        return img
    return animate("holo_projector_beam", "flow", frame)


def holo_projector_scanline():
    def frame(t):
        img = new()
        s = int(round(t * 16))
        for y in range(16):
            d = (y - s) % 16
            a = (96 if y % 2 == 0 else 0) + (120, 70, 34)[d] if d < 3 else (72 if y % 2 == 0 else 0)
            if a:
                hl(img, 0, 16, y, N[4] if d == 0 else N[3], min(a, 230))
        return img
    return animate("holo_projector_scanline", "flow", frame)


def holo_projector_shimmer():
    def frame(t):
        f = int(round(t * 4))
        img = new()
        for y in range(16):
            for x in range(16):
                d = det("shimmer", x, y)
                if d < 0.14 and int(d * 1000) % 4 == f:
                    put(img, x, y, N[4], 210)
                elif d < 0.14 and int(d * 1000) % 4 == (f + 1) % 4:
                    put(img, x, y, N[3], 90)
        return img
    return animate("holo_projector_shimmer", "flicker", frame)


def holo_projector_idle():
    """Wireframe gem: back edges faint, front edges solid."""
    img = new(32, 32)
    top, bot = (15, 2), (15, 29)
    back = ((9, 12), (22, 12))
    front = ((3, 16), (15, 20), (28, 16))
    for p in back:
        line(img, top[0], top[1], p[0], p[1], N[2], 96)
        line(img, bot[0], bot[1], p[0], p[1], N[2], 96)
    line(img, front[0][0], front[0][1], back[0][0], back[0][1], N[2], 96)
    line(img, back[0][0], back[0][1], back[1][0], back[1][1], N[2], 96)
    line(img, back[1][0], back[1][1], front[2][0], front[2][1], N[2], 96)
    for a, b in ((front[0], front[1]), (front[1], front[2])):
        line(img, a[0], a[1], b[0], b[1], N[3])
    for p in front:
        line(img, top[0], top[1], p[0], p[1], N[3])
        line(img, bot[0], bot[1], p[0], p[1], N[3])
    for p in (top, bot) + front:
        put(img, p[0], p[1], N[4])
    return img


def projector_model(c):
    tx = {"particle": c.tex("holo_projector_top"), "top": c.tex("holo_projector_top"),
          "side": c.tex("holo_projector_side"), "glow_top": c.tex("holo_projector_top_glow"),
          "glow_side": c.tex("holo_projector_side_glow")}
    els = [box(c, (2, 0, 2), (14, 2, 14)), box(c, (3, 2, 3), (13, 5, 13), skip=("down",)),
           box(c, (5, 5, 5), (11, 6, 11), skip=("down",)),
           box(c, (5, 5, 5), (11, 6, 11), tex={"up": "#glow_top"}, emit=15),
           box(c, (3, 2, 3), (13, 5, 13), tex={"side": "#glow_side"}, emit=15)]
    return {"holo_projector": mdl(tx, els)}


def projector_state(c):
    return {"variants": {"face=%s" % f: rotated(c, "holo_projector", _mount(f)) for f in _DIRS}}


# =========================================================================================
# holo_sign -- emitter rail + floating panel (text is BE-drawn)
# =========================================================================================
def holo_sign_base():
    img = new(16, 16, BODY[1])
    bevel(img, 2, 6, 14, 10, BODY, 2)                # plan: rail
    rect(img, 4, 7, 12, 9, BODY[0])                  # emitter slit
    put(img, 3, 7, STEEL[1])
    put(img, 12, 8, STEEL[1])
    band(img, 2, 14, 2, 0, BODY, 2)                  # elevation
    hl(img, 5, 11, 15, BODY[1])
    return wear(img, "holo_sign_base", BODY, rate=0.04)


def holo_sign_base_glow():
    def frame(t):
        k = pulse(t)
        img = new()
        hl(img, 4, 12, 7, lerp(N[3], N[4], k))
        hl(img, 4, 12, 8, lerp(N[2], N[3], k))
        return img
    return animate("holo_sign_base_glow", "breathing", frame)


def holo_sign_panel_glow():
    """32x32, the panel uses rows 0..21 (16 x 11 block pixels at 2 texels each)."""
    drop = {1: ((20, 0), (21, 0)), 3: ((31, 8), (31, 9), (5, 21), (6, 21), (7, 21))}

    def frame(t):
        f = int(round(t * 4))
        img = new(32, 32)
        hl(img, 0, 19, 0, N[4])
        hl(img, 23, 32, 0, N[3])
        hl(img, 0, 10, 1, N[3])                      # header tab
        vl(img, 0, 0, 22, N[4])
        for y in range(0, 22):
            if y % 4 < 2:
                put(img, 31, y, N[3])                # dashed right edge
        hl(img, 0, 28, 21, N[3])
        put(img, 29, 20, N[3])
        put(img, 30, 19, N[3])                       # chamfer
        for x in (24, 26, 28):
            put(img, x, 2, N[2])                     # status ticks
        for (x, y) in drop.get(f, ()):
            put(img, x, y, (0, 0, 0), 0)
        return img
    return animate("holo_sign_panel_glow", "flicker", frame)


def holo_sign_backing():
    def frame(t):
        img = new(32, 32)
        s = int(round(t * 16)) * 2
        for y in range(32):
            d = (y - s) % 32
            a = 34 + (30 if y % 2 == 0 else 0) + ((60, 40, 22, 10)[d] if d < 4 else 0)
            hl(img, 0, 32, y, N[3] if d else N[4], a)
        return img
    return animate("holo_sign_backing", "flow", frame)


_F = {  # 3x5 glyphs, five octal rows each (4 = left pixel)
    " ": "00000", "!": "22202", '"': "55000", "#": "57575", "$": "36236", "%": "51245",
    "&": "25253", "'": "22000", "(": "12221", ")": "42224", "*": "52500", "+": "02720",
    ",": "00024", "-": "00700", ".": "00002", "/": "11244",
    "0": "75557", "1": "26227", "2": "61247", "3": "61216", "4": "55711", "5": "74616",
    "6": "34757", "7": "71222", "8": "75757", "9": "75716",
    ":": "02020", ";": "02024", "<": "12421", "=": "07070", ">": "42124", "?": "61202",
    "@": "25743",
    "A": "25755", "B": "65656", "C": "34443", "D": "65556", "E": "74647", "F": "74644",
    "G": "34553", "H": "55755", "I": "72227", "J": "11152", "K": "55655", "L": "44447",
    "M": "57755", "N": "65555", "O": "25552", "P": "65644", "Q": "25573", "R": "65655",
    "S": "34216", "T": "72222", "U": "55557", "V": "55552", "W": "55775", "X": "55255",
    "Y": "55222", "Z": "71247",
    "[": "32223", "\\": "44211", "]": "62226", "^": "25000", "_": "00007", "`": "42000",
    "a": "03553", "b": "46556", "c": "03443", "d": "13553", "e": "03563", "f": "12722",
    "g": "03536", "h": "46555", "i": "20222", "j": "10152", "k": "45655", "l": "62227",
    "m": "07755", "n": "06555", "o": "02552", "p": "06564", "q": "03531", "r": "03444",
    "s": "03636", "t": "27221", "u": "05553", "v": "05552", "w": "05577", "x": "05225",
    "y": "05316", "z": "07367",
    "{": "32623", "|": "22222", "}": "62326", "~": "03600",
}
# glyph indices 95.. : extra symbols for display payloads
GLYPH_EXTRAS = (("left", "13731"), ("right", "46764"), ("up", "02277"), ("down", "77220"),
                ("degree", "25200"), ("square", "07770"), ("block", "77777"), ("check", "01152"),
                ("shade", "52525"), ("ellipsis", "00005"))
GLYPHS = [_F[chr(cp)] for cp in range(32, 127)] + [g for _n, g in GLYPH_EXTRAS]


def _glyph_page(page):
    def paint():
        img = new(32, 32)
        for slot in range(40):
            i = page * 40 + slot
            if i >= len(GLYPHS):
                break
            cx, cy = (slot % 8) * 4, (slot // 8) * 6
            for j, d in enumerate(GLYPHS[i]):
                for b in range(3):
                    if int(d) & (4 >> b):
                        put(img, cx + b, cy + j, N[4])
        return img
    return paint


SIGN_ICONS = ("warning", "arrow", "power", "lock", "home", "rocket", "energy", "fluid",
              "check", "cross", "info", "gear", "star", "life", "air", "hazard")
_ICONS = {
    "warning": ("...##...", "..####..", "..#..#..", ".##..##.", ".##..##.", "########", "###..###", "########"),
    "arrow": ("....#...", "....##..", "#######.", "########", "#######.", "....##..", "....#...", "........"),
    "power": ("...##...", ".#.##.#.", "##.##.##", "#..##..#", "#......#", "##....##", ".######.", "..####.."),
    "lock": ("..####..", ".#....#.", ".#....#.", "########", "###..###", "###..###", "########", "........"),
    "home": ("...##...", "..####..", ".######.", "########", ".#....#.", ".#.##.#.", ".#.##.#.", ".######."),
    "rocket": ("...##...", "..####..", "..#..#..", "..####..", "..####..", ".######.", ".#.##.#.", "...##..."),
    "energy": ("....##..", "...##...", "..##....", ".######.", "....##..", "...##...", "..##....", ".#......"),
    "fluid": ("...#....", "...##...", "..###...", ".#####..", ".######.", ".######.", ".######.", "..####.."),
    "check": ("........", ".......#", "......##", "#....##.", "##..##..", ".####...", "..##....", "........"),
    "cross": ("##....##", ".##..##.", "..####..", "...##...", "..####..", ".##..##.", "##....##", "........"),
    "info": ("..####..", ".#.##.#.", "#......#", "#..##..#", "#..##..#", "#..##..#", ".#....#.", "..####.."),
    "gear": ("..#..#..", ".######.", "###..###", ".#....#.", ".#....#.", "###..###", ".######.", "..#..#.."),
    "star": ("...##...", "...##...", "########", ".######.", "..####..", ".######.", ".##..##.", "#......#"),
    "life": (".##..##.", "########", "########", "########", ".######.", "..####..", "...##...", "........"),
    "air": ("..###...", ".#...#..", "#.....#.", "#.....#.", "#.....#.", ".#...#.#", "..###.#.", ".....###"),
    "hazard": ("..####..", ".##..##.", "###..###", "...##...", "...##...", "###..###", ".##..##.", "..####.."),
}


def holo_sign_icons():
    img = new(32, 32)
    for i, name in enumerate(SIGN_ICONS):
        bitmap(img, (i % 4) * 8, (i // 4) * 8, _ICONS[name], N[4])
    return img


def sign_model(c):
    tx = {"particle": c.tex("holo_sign_base"), "top": c.tex("holo_sign_base"),
          "side": c.tex("holo_sign_base"), "glow_top": c.tex("holo_sign_base_glow"),
          "panel": c.tex("holo_sign_panel_glow")}
    els = [box(c, (2, 0, 6), (14, 2, 10)),
           box(c, (2, 0, 6), (14, 2, 10), tex={"up": "#glow_top"}, emit=15),
           c.el((0, 5, 8), (16, 16, 8), {"south": {"texture": "#panel", "uv": [0, 0, 16, 11]},
                                         "north": {"texture": "#panel", "uv": [16, 0, 0, 11]}},
                cull=False, emit=15)]
    return {"holo_sign": mdl(tx, els)}


def sign_state(c):
    return {"variants": {"facing=%s" % f: rotated(c, "holo_sign", (0, _FRONT[f])) for f in _FRONT}}


# =========================================================================================
# arrow_sign -- floating chevrons, 8 directions
# =========================================================================================
def arrow_sign_base():
    img = new(16, 16, BODY[1])
    bevel(img, 6, 6, 10, 10, BODY, 2)                # plan: nub
    rect(img, 7, 7, 9, 9, BODY[0])
    hl(img, 6, 10, 15, BODY[2])                      # elevation: 1px
    put(img, 6, 15, BODY[3])
    put(img, 9, 15, BODY[0])
    return img


def arrow_sign_base_glow():
    def frame(t):
        img = new()
        rect(img, 7, 7, 9, 9, lerp(N[3], N[4], pulse(t)))
        return img
    return animate("arrow_sign_base_glow", "breathing", frame)


def _arrow(name):
    dx, dy = DIRECTIONS[name]
    diag = bool(dx and dy)
    tid = "arrow_sign_%s_glow" % name

    def paint():
        def frame(t):
            img = new()
            for y in range(1, 15 if diag else 14):
                for x in range(1, 15):
                    if diag:                                     # three equal corner brackets
                        a, b = dx * (x - 7.5), dy * (y - 7.5)
                        if max(a, b) - min(a, b) > 5.5:
                            continue
                        k, on = int(math.floor((max(a, b) + 1.5) / 3.0)), (max(a, b) + 1.5) % 3.0 < 2.0
                    else:
                        px, py = x - 7.5, y - 7.0
                        along, across = dx * px + dy * py, -dy * px + dx * py
                        if abs(across) > 5:
                            continue
                        s = along + abs(across)
                        k, on = int(math.floor((s + 5.5) / 4.0)), (s + 5.5) % 4.0 < 2.0
                    if 0 <= k <= 2 and on:
                        lit = (int(round(t * 16)) // 4 + 3 - k) % 4
                        put(img, x, y, (N[4], N[3], N[2], N[1])[lit] if lit < 3 else N[2])
            return img
        return animate(tid, "flow", frame)
    return paint


def arrow_models(c):
    out = {}
    for d in DIRECTIONS:
        tx = {"particle": c.tex("arrow_sign_base"), "top": c.tex("arrow_sign_base"),
              "side": c.tex("arrow_sign_base"), "glow_top": c.tex("arrow_sign_base_glow"),
              "arrow": c.tex("arrow_sign_%s_glow" % d)}
        els = [box(c, (6, 0, 6), (10, 1, 10)),
               box(c, (6, 0, 6), (10, 1, 10), tex={"up": "#glow_top"}, emit=15),
               c.el((0, 0, 8), (16, 16, 8), {"south": {"texture": "#arrow", "uv": [0, 0, 16, 16]},
                                             "north": {"texture": "#arrow", "uv": [16, 0, 0, 16]}},
                    cull=False, emit=15)]
        out["arrow_sign_%s" % d] = mdl(tx, els)
    return out


def arrow_state(c):
    return {"variants": {"direction=%s,facing=%s" % (d, f): rotated(c, "arrow_sign_%s" % d, (0, _FRONT[f]))
                         for d in DIRECTIONS for f in _FRONT}}


# =========================================================================================
# world_globe -- base + the 7-world map set the BE wraps on its sphere
# =========================================================================================
def world_globe_base_top():
    img = rings([(3, 1), (4, 2), (5, 0)])
    rect(img, 6, 6, 10, 10, BODY[1])
    for (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11)):
        put(img, x, y, STEEL[2])                     # prong tips
    put(img, 4, 4, STEEL[3])
    return wear(img, "world_globe_base_top", BODY, rate=0.04)


def world_globe_base_side():
    img = new(16, 16, BODY[0])
    band(img, 3, 13, 2, 0, BODY, 1)                  # foot
    band(img, 6, 10, 4, 2, BODY, 2)                  # stem
    band(img, 4, 12, 6, 4, BODY, 2)                  # cradle
    for x in (4, 11):
        put(img, x, 8, STEEL[2])
        put(img, x, 9, STEEL[1])                     # prongs
    put(img, 4, 8, STEEL[3])
    hl(img, 6, 10, 14, BODY[0])                      # indicator recess
    return wear(img, "world_globe_base_side", BODY, rate=0.04)


def _globe_base_glow(world):
    r = ramp(world)
    tid = "world_globe_base_%s_glow" % world

    def paint():
        def frame(t):
            k = pulse(t)
            img = new()
            for i in range(6, 10):
                for (x, y) in ((i, 5), (i, 10), (5, i), (10, i)):
                    put(img, x, y, lerp(r[3], r[4], k))
            rect(img, 7, 7, 9, 9, lerp(r[3], r[4], 1 - k))
            hl(img, 6, 10, 14, lerp(r[3], r[4], 1 - k))          # foot indicator (elevation)
            return img
        return animate(tid, "breathing", frame)
    return paint


def _wrapd(a, b, size):
    d = abs(a - b) % size
    return min(d, size - d)


def _globe(world):
    """-> (surface, glow candidates) for one world, both GLOBE x GLOBE."""
    g, r = GLOBE, ramp(world)
    u = g / 32.0
    img, glow = new(g, g, r[2]), []
    for y in range(g):
        lat = abs((y + 0.5) / g - 0.5) * 2               # 0 equator .. 1 pole
        for x in range(g):
            v = fbm(world, x, y, g)
            w = fbm(world + "2", x, y, g)
            if world == "station":
                col = r[1] if v < 0.55 else r[2]
                if x % (g // 4) == 0 or y % (g // 4) == 0:
                    col = r[0]
                elif x % (g // 4) == 1 or y % (g // 4) == 1:
                    col = r[2] if v < 0.55 else r[3]
                if abs(y - g / 2 + 0.5) < u:
                    col = r[3]
                elif abs(y - g / 2 + 0.5) < 2 * u:
                    col = r[0]
                if abs(abs(y - g / 2 + 0.5) - 5 * u) < u * 0.6 and x % 2 == 0 and det("stw", x, y) < 0.7:
                    glow.append((det("stg", x, y), x, y, r[4]))
                    col = r[3]
            elif world == "luna":
                col = r[1] if v < 0.40 else r[2] if v < 0.62 else r[3]
            elif world == "mars":
                col = r[1] if v < 0.36 else r[2] if v < 0.60 else r[3]
                if lat > 0.86 - 0.10 * w:
                    col = r[4]
                elif abs(y - g * 0.56 - 2 * u * math.sin(x / float(g) * 2 * math.pi)) < 0.8 * u \
                        and _wrapd(x, g * 0.4, g) < g * 0.22:
                    col = r[0]
            elif world == "cindara":
                col = r[0] if v < 0.45 else r[1] if v < 0.68 else r[2]
                crack = abs(w - 0.5)
                if crack < 0.035:
                    col = r[3] if crack > 0.012 else r[4]
                    glow.append((1 - crack, x, y, col))
            elif world == "glacira":
                col = r[1] if v < 0.30 else r[2] if v < 0.46 else r[3] if v < 0.70 else r[4]
                if abs(w - 0.5) < 0.02 and v >= 0.46:
                    col = r[2]
                if lat > 0.80:
                    col = r[4] if v > 0.35 else r[3]
                if 0.60 < lat < 0.74 and w > 0.52:
                    glow.append((w, x, y, r[3]))
            elif world == "greenxertz":
                if v < 0.47:
                    col = r[0] if v < 0.40 else r[1]
                    if 0.44 < v and w > 0.5:
                        glow.append((w, x, y, r[4]))
                else:
                    col = r[2] if v < 0.60 else r[3] if v < 0.72 else r[4]
                    if lat > 0.85:
                        col = r[4]
            else:  # nyxara: banded, storm eye
                b = (y / float(g)) * 7 + (v - 0.5) * 2.2
                col = (r[1], r[2], r[1], r[3], r[2], r[0], r[2])[int(b) % 7]
                ex, ey = _wrapd(x, g * 0.3, g) / (3.2 * u), (y - g * 0.62) / (1.7 * u)
                e = ex * ex + ey * ey
                if e < 1.0:
                    col = r[4] if e < 0.3 else r[3]
                    glow.append((2 - e, x, y, col))
                elif w > 0.74:
                    glow.append((w, x, y, r[3]))
            put(img, x, y, col)
    if world == "luna":
        px = img.load()
        for i in range(9):
            cx, cy = int(det("lc", i, 0) * g), int(2 * u + det("lc", i, 1) * (g - 4 * u))
            rad = (1.2 + det("lc", i, 2) * 2.2) * u
            for y in range(g):
                for x in range(g):
                    dxw = ((x - cx + g / 2) % g) - g / 2
                    d = math.hypot(dxw, y - cy)
                    if d < rad - 0.6:
                        px[x, y] = r[1] + (255,)
                    elif d < rad + 0.5:
                        px[x, y] = (r[4] if (dxw + (y - cy)) < 0 else r[0]) + (255,)
            if i < 3:
                glow.append((1.0, cx, cy, r[4]))
        for i in range(10):
            glow.append((det("lg", i), int(det("lgx", i) * g), int(det("lgy", i) * g), r[4]))
    if world == "mars":                                  # outpost lights, clustered near the canyon
        for i in range(14):
            x = int(g * 0.4 + (det("mx", i) - 0.5) * g * 0.5) % g
            y = int(g * 0.56 + (det("my", i) - 0.5) * g * 0.45)
            glow.append((det("mg", i), x, y, r[4]))
    return img, glow


@functools.lru_cache(maxsize=None)
def _globe_cached(world):
    return _globe(world)


def _globe_surface(world):
    return lambda: _globe_cached(world)[0].copy()


def _globe_glow(world):
    return lambda: top_k(_globe_cached(world)[1], GLOBE)


def world_globe_scan():
    def frame(t):
        img = new()
        s = int(round(t * 16))
        for y in range(16):
            d = (y - s) % 16
            for x in range(16):
                a = (150, 80, 36)[d] if d < 3 else 0
                if x % 4 == 0 or y % 4 == 0:
                    a = max(a, 30)
                if a:
                    put(img, x, y, N[4] if d == 0 else N[3], a)
        return img
    return animate("world_globe_scan", "flow", frame)


def world_globe_halo():
    img = new()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 5.2 <= d <= 8.0:
                a = int(150 * (1 - abs(d - 7.0) / 1.9)) if d > 6.0 else int(40 * (d - 5.2))
                if a > 6:
                    put(img, x, y, N[4] if d > 6.6 else N[3], min(a, 170))
    return img


def globe_models(c):
    out = {}
    for w in WORLDS:
        tx = {"particle": c.tex("world_globe_base_top"), "top": c.tex("world_globe_base_top"),
              "side": c.tex("world_globe_base_side"), "glow": c.tex("world_globe_base_%s_glow" % w)}
        els = [box(c, (3, 0, 3), (13, 2, 13)), box(c, (6, 2, 6), (10, 4, 10), skip=("up", "down")),
               box(c, (4, 4, 4), (12, 6, 12))]
        for (x, z) in ((4, 4), (11, 4), (4, 11), (11, 11)):
            els.append(box(c, (x, 6, z), (x + 1, 8, z + 1), skip=("down",)))
        els.append(box(c, (4, 4, 4), (12, 6, 12), tex={"up": "#glow"}, emit=15))
        els.append(box(c, (3, 0, 3), (13, 2, 13), tex={"side": "#glow"}, emit=15))
        out["world_globe_%s" % w] = mdl(tx, els)
    return out


def globe_state(c):
    return {"variants": {"world=%s" % w: {"model": c.model("world_globe_%s" % w)} for w in WORLDS}}


# =========================================================================================
# holo_table -- table with emitter well
# =========================================================================================
def holo_table_top():
    img = new(16, 16, BODY[1])
    bevel(img, 0, 0, 16, 16, BODY, 2)                # rim
    hl(img, 1, 15, 1, BODY[2])
    vl(img, 1, 1, 15, BODY[2])
    rect(img, 2, 2, 14, 14, BODY[0])                 # well floor
    for i in range(2, 14):
        put(img, i, 2, BODY[0])
        put(img, 2, i, BODY[0])
        put(img, i, 13, BODY[2])
        put(img, 13, i, BODY[2])                     # inner lip catches the light bottom-right
    for k in (5, 10):
        for i in range(3, 13):
            if (i, k) != (k, k):
                put(img, i, k, BODY[1])
                put(img, k, i, BODY[1])              # etched grid
    rect(img, 7, 7, 9, 9, BODY[2])
    for (x, y) in ((0, 0), (15, 0), (0, 15), (15, 15)):
        put(img, x, y, STEEL[1])
    put(img, 0, 0, STEEL[2])
    return wear(img, "holo_table_top", BODY, rate=0.03, region=(0, 0, 16, 2))


def holo_table_side():
    img = new(16, 16, BODY[0])
    band(img, 2, 14, 2, 0, BODY, 1)                  # foot
    band(img, 5, 11, 10, 2, BODY, 2)                 # column
    band(img, 0, 16, 13, 10, BODY, 1)                # slab
    band(img, 0, 16, 15, 13, BODY, 2)                # rim
    vl(img, 8, 7, 13, BODY[0])                       # column slit
    hl(img, 2, 14, 4, BODY[0])                       # slab light channel
    put(img, 0, 1, STEEL[2])
    put(img, 15, 2, STEEL[0])
    return wear(img, "holo_table_side", BODY, rate=0.04)


def holo_table_under():
    img = new(16, 16, BODY[1])
    bevel(img, 0, 0, 16, 16, BODY, 1)
    bevel(img, 3, 3, 13, 13, BODY, 0)
    return wear(img, "holo_table_under", BODY, rate=0.05)


def holo_table_top_glow():
    ring = ([(x, 5) for x in range(5, 11)] + [(10, y) for y in range(6, 11)]
            + [(x, 10) for x in range(9, 4, -1)] + [(5, y) for y in range(9, 5, -1)])

    def frame(t):
        img = new()
        s = int(round(t * 16))
        for i, (x, y) in enumerate(ring):
            d = (i - s * len(ring) // 16) % len(ring)
            put(img, x, y, N[4] if d < 2 else N[3] if d < 5 else N[2])
        rect(img, 7, 7, 9, 9, N[4])
        for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
            put(img, x, y, N[2])
        return img
    return animate("holo_table_top_glow", "data", frame)


def holo_table_side_glow():
    def frame(t):
        img = new()
        s = int(round(t * 16))
        for x in range(2, 14):
            if (x + s // 2) % 4 < 2:
                put(img, x, 4, N[3])
        for y in range(7, 13):
            put(img, 8, y, N[4] if (y + s) % 8 == 0 else N[2])
        return img
    return animate("holo_table_side_glow", "data", frame)


def holo_table_grid():
    """Translucent terrain wireframe (tileable); contour lines drift."""
    def frame(t):
        img = new(32, 32)
        for y in range(32):
            for x in range(32):
                h = 0.75 * _noise("table", x, y, 16, 32, True) + 0.25 * _noise("table", x, y, 8, 32, True)
                c = (h * 5 + t) % 1.0
                a = 0
                if x % 8 == 0 or y % 8 == 0:
                    a = 40
                if x % 8 == 0 and y % 8 == 0:
                    a = 110
                if c < 0.12:
                    a = 190 if h > 0.5 else 120
                if a:
                    put(img, x, y, N[4] if (c < 0.12 and h > 0.5) else N[3], a)
        return img
    return animate("holo_table_grid", "data", frame)


def holo_table_beam():
    def frame(t):
        img = new()
        s = int(round(t * 16))
        for y in range(16):
            for x in range(16):
                a = 18 + int(30 * (y / 15.0))
                if (x + 2 * ((y + s) % 16)) % 8 == 0:
                    a += 54
                put(img, x, y, N[3], a)
        return img
    return animate("holo_table_beam", "flow", frame)


def holo_table_blips():
    """Four 8x8 markers (pin, ring, cross, diamond); they blink out of step."""
    marks = (("...##...", "..####..", "..####..", "...##...", "...##...", "........", "..####..", "........"),
             ("..####..", ".#....#.", "#......#", "#..##..#", "#..##..#", "#......#", ".#....#.", "..####.."),
             ("...##...", "...##...", "........", "##.##.##", "##.##.##", "........", "...##...", "...##..."),
             ("...##...", "..#..#..", ".#....#.", "#..##..#", "#..##..#", ".#....#.", "..#..#..", "...##..."))

    def frame(t):
        f = int(round(t * 4))
        img = new()
        for i, m in enumerate(marks):
            on = (f + i) % 4
            bitmap(img, (i % 2) * 8, (i // 2) * 8, m, N[4] if on < 2 else N[2], 255 if on < 3 else 90)
        return img
    return animate("holo_table_blips", "flicker", frame)


def table_model(c):
    tx = {"particle": c.tex("holo_table_side"), "top": c.tex("holo_table_top"),
          "side": c.tex("holo_table_side"), "under": c.tex("holo_table_under"),
          "glow_top": c.tex("holo_table_top_glow"), "glow_side": c.tex("holo_table_side_glow")}
    base = {"up": "#top", "down": "#under", "side": "#side"}
    els = [box(c, (2, 0, 2), (14, 2, 14), tex={"up": "#under", "down": "#under", "side": "#side"}),
           box(c, (5, 2, 5), (11, 10, 11), skip=("up", "down")),
           box(c, (0, 10, 0), (16, 13, 16), tex=base),
           box(c, (0, 13, 0), (16, 15, 2), skip=("down",)),
           box(c, (0, 13, 14), (16, 15, 16), skip=("down",)),
           box(c, (0, 13, 2), (2, 15, 14), skip=("down", "north", "south")),
           box(c, (14, 13, 2), (16, 15, 14), skip=("down", "north", "south")),
           box(c, (0, 10, 0), (16, 13, 16), tex={"up": "#glow_top", "side": "#glow_side"}, emit=15),
           box(c, (5, 2, 5), (11, 10, 11), tex={"side": "#glow_side"}, emit=15)]
    return {"holo_table": mdl(tx, els)}


# =========================================================================================
# holo_curtain -- intangible light sheet, 16-mask edge on the four in-plane sides
# =========================================================================================
def _curtain_frame(m, t):
    img = new()
    s = int(round(t * 16))
    for y in range(16):
        d = (y - s) % 16
        for x in range(16):
            a = 40 + (34 if y % 2 == 0 else 0) + ((120, 74, 40, 18)[d] if d < 4 else 0)
            col = N[4] if d == 0 else N[3]
            if det("curtain col", x) < 0.3:
                a += 16
            sp = det("curtain", x, y)
            if sp < 0.05 and (int(sp * 4000) + s) % 16 < 3:
                a, col = a + 90, N[4]
            edge = min((y if m & 8 else 9), (15 - y if m & 4 else 9),
                       (x if m & 2 else 9), (15 - x if m & 1 else 9))
            if edge == 0:
                a, col = 236, N[4]
            elif edge == 1:
                a = max(a, 112)
            put(img, x, y, col, min(a, 236))
    return img


def _curtain(m):
    return lambda: Art([_curtain_frame(m, i / 16.0) for i in range(16)], "flow", False)


def curtain_models(c):
    out = {}
    for m in range(16):
        back = (m & 12) | ((m & 2) >> 1) | ((m & 1) << 1)     # seen from behind, left/right swap
        tx = {"particle": c.tex("holo_curtain_f0"), "front": c.tex("holo_curtain_f%d" % m),
              "back": c.tex("holo_curtain_f%d" % back)}
        els = [c.el((0, 0, 8), (16, 16, 8), {"south": {"texture": "#front", "uv": [0, 0, 16, 16]},
                                             "north": {"texture": "#back", "uv": [0, 0, 16, 16]}},
                    cull=False, emit=15)]
        out["holo_curtain_f%d" % m] = mdl(tx, els, "minecraft:translucent")
    return out


def curtain_state(c):
    """axis=x: sheet in the x/y plane (model as drawn); axis=z: the same model turned y=90, so the
    model's west edge meets the north neighbour and its east edge the south one."""
    parts = []
    for axis, ry, left, right in (("x", 0, "west", "east"), ("z", 90, "north", "south")):
        for m in range(16):
            when = {"axis": axis}
            for bit, side in ((8, "up"), (4, "down"), (2, left), (1, right)):
                when[side] = "false" if m & bit else "true"
            parts.append({"when": when, "apply": rotated(c, "holo_curtain_f%d" % m, (0, ry))})
    return {"multipart": parts}


# =========================================================================================
# holo_plinth -- lit pedestal
# =========================================================================================
def holo_plinth_top():
    img = rings([(2, 1), (3, 2), (4, 0)])
    rect(img, 5, 5, 11, 11, BODY[1])
    bevel(img, 6, 6, 10, 10, BODY, 2)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(img, x, y, STEEL[1])
    put(img, 2, 2, STEEL[2])
    return wear(img, "holo_plinth_top", BODY, rate=0.04)


def holo_plinth_side():
    img = new(16, 16, BODY[0])
    band(img, 2, 14, 2, 0, BODY, 1)                  # foot
    band(img, 4, 12, 12, 2, BODY, 2)                 # column
    band(img, 2, 14, 15, 12, BODY, 1)                # cap
    band(img, 3, 13, 16, 15, STEEL, 1)               # pad edge
    vl(img, 7, 5, 13, BODY[0])
    vl(img, 8, 5, 13, BODY[0])                       # light slot
    vl(img, 5, 5, 13, BODY[1])
    vl(img, 10, 5, 13, BODY[3])                      # fluting
    hl(img, 3, 13, 2, BODY[0])                       # cap light channel
    return wear(img, "holo_plinth_side", BODY, rate=0.04)


def holo_plinth_top_glow():
    def frame(t):
        k = pulse(t)
        img = new()
        for i in range(4, 12):
            for (x, y) in ((i, 4), (i, 11), (4, i), (11, i)):
                put(img, x, y, lerp(PLASMA[1], PLASMA[2], k))
        return img
    return animate("holo_plinth_top_glow", "breathing", frame)


def holo_plinth_side_glow():
    def frame(t):
        img = new()
        s = int(round(t * 16))
        for y in range(5, 13):
            d = (y + s) % 8
            put(img, 7, y, PLASMA[3] if d == 0 else PLASMA[2] if d < 3 else PLASMA[1])
            put(img, 8, y, PLASMA[2] if d == 0 else PLASMA[1])
        hl(img, 3, 13, 2, PLASMA[2])
        return img
    return animate("holo_plinth_side_glow", "flow", frame)


def plinth_model(c):
    tx = {"particle": c.tex("holo_plinth_side"), "top": c.tex("holo_plinth_top"),
          "side": c.tex("holo_plinth_side"), "glow_top": c.tex("holo_plinth_top_glow"),
          "glow_side": c.tex("holo_plinth_side_glow")}
    els = [box(c, (2, 0, 2), (14, 2, 14)), box(c, (4, 2, 4), (12, 12, 12), skip=("up", "down")),
           box(c, (2, 12, 2), (14, 15, 14)), box(c, (3, 15, 3), (13, 16, 13), skip=("down",)),
           box(c, (3, 15, 3), (13, 16, 13), tex={"up": "#glow_top"}, emit=15),
           box(c, (4, 2, 4), (12, 12, 12), tex={"side": "#glow_side"}, emit=15),
           box(c, (2, 12, 2), (14, 15, 14), tex={"side": "#glow_side"}, emit=15)]
    return {"holo_plinth": mdl(tx, els)}


# =========================================================================================
# ticker_strip -- 4px face-attached band, abstract glyphs scrolling along it
# =========================================================================================
_TICK = (("###", "#..", "#.#", "..#"), ("#.#", "#.#", ".#.", "###"),
         (".#.", "###", "...", "#.#"), ("#..", "#.#", "###", "..#"))


def _ticker_base():
    img = new(16, 16, BODY[1])
    bevel(img, 0, 0, 16, 5, BODY, 1)
    bevel(img, 0, 11, 16, 16, BODY, 1)
    hl(img, 0, 16, 5, BODY[3])                       # lit lip (side face)
    hl(img, 0, 16, 10, BODY[0])                      # occluded lip
    rect(img, 0, 6, 16, 10, BODY[0])                 # screen glass
    hl(img, 0, 16, 6, BODY[1])
    return wear(img, "ticker_strip", BODY, rate=0.04, region=(0, 0, 16, 5))


def _ticker_cap():
    img = cap_layer(BODY)
    for x in (1, 14):
        vl(img, x, 6, 10, STEEL[1])
        put(img, x, 6, STEEL[2])
    return img


def _ticker_glow(t):
    img = new()
    s = int(round(t * 16))
    for g, rows in enumerate(_TICK):
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == "#":
                    put(img, (g * 4 + i - s) % 16, 6 + j, N[4] if g == 0 else N[3])
    return img


def _ends(img, p, n, layer=None):
    """Bake (layer given) or clear (None) the 3 end columns: p = +axis end (right), n = -axis."""
    out = img.copy()
    for y in range(16):
        for x in ([13, 14, 15] if p else []) + ([0, 1, 2] if n else []):
            out.putpixel((x, y), layer.getpixel((x, y)) if layer else (0, 0, 0, 0))
    return out


def _ticker(p, n, glow, t_):
    def paint():
        if glow:
            frames = [_ends(_ticker_glow(i / 16.0), p, n) for i in range(16)]
        else:
            frames = [_ends(_ticker_base(), p, n, _ticker_cap())]
        if t_:
            frames = [transpose(f) for f in frames]
        return Art(frames, "flow", False) if glow else frames[0]
    return paint


def _ticker_tid(p, n, glow=False, t_=False):
    return "ticker_strip%s_c%d%d%s" % ("_glow" if glow else "", p, n, "_t" if t_ else "")


def ticker_models(c):
    out = {}
    for p in (0, 1):
        for n in (0, 1):
            tx = {"particle": c.tex(_ticker_tid(1, 1)), "bar": c.tex(_ticker_tid(p, n)),
                  "glow": c.tex(_ticker_tid(p, n, True))}
            faces = {"up": {"texture": "#bar", "uv": [0, 6, 16, 10]},
                     "north": {"texture": "#bar", "uv": [16, 5, 0, 6]},
                     "south": {"texture": "#bar", "uv": [0, 10, 16, 11]},
                     "west": {"texture": "#bar", "uv": [0, 5, 4, 6]},
                     "east": {"texture": "#bar", "uv": [12, 5, 16, 6]},
                     "down": {"texture": "#bar", "uv": [0, 6, 16, 10], "cullface": "down"}}
            out["ticker_strip_c%d%d" % (p, n)] = mdl(tx, [
                c.el((0, 0, 6), (16, 1, 10), faces, cull=False, tint=False),
                c.el((0, 0, 6), (16, 1, 10), {"up": {"texture": "#glow", "uv": [0, 6, 16, 10]}},
                     cull=False, emit=15)])
            tx = {"particle": c.tex(_ticker_tid(1, 1)), "bar": c.tex(_ticker_tid(p, n)),
                  "bar_t": c.tex(_ticker_tid(p, n, False, True)),
                  "glow": c.tex(_ticker_tid(p, n, True, True))}
            faces = {"up": {"texture": "#bar_t", "uv": [6, 0, 10, 16]},
                     "west": {"texture": "#bar", "uv": [0, 5, 16, 6]},
                     "east": {"texture": "#bar", "uv": [16, 10, 0, 11]},
                     "north": {"texture": "#bar", "uv": [0, 5, 4, 6]},
                     "south": {"texture": "#bar", "uv": [12, 5, 16, 6]},
                     "down": {"texture": "#bar_t", "uv": [6, 0, 10, 16], "cullface": "down"}}
            out["ticker_strip_c%d%d_z" % (p, n)] = mdl(tx, [
                c.el((6, 0, 0), (10, 1, 16), faces, cull=False, tint=False),
                c.el((6, 0, 0), (10, 1, 16), {"up": {"texture": "#glow", "uv": [6, 0, 10, 16]}},
                     cull=False, emit=15)])
    return out


def ticker_state(c):
    variants = {}
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
                    key = "axis=%s,face=%s,neg_link=%s,pos_link=%s" % (axis, face, str(neg).lower(), str(pos).lower())
                    variants[key] = rotated(c, "ticker_strip_c%d%d%s" % (cp, cn, canon), (rx, ry))
    return {"variants": variants}


# =========================================================================================
# model helpers
# =========================================================================================
_FRONT = {"south": 0, "west": 90, "north": 180, "east": 270}   # y turn taking a south-facing front there


def mdl(textures, elements, rt="minecraft:cutout"):
    return {"parent": BLOCK_PARENT, "render_type": rt, "textures": textures, "elements": elements}


def box(c, frm, to, tex=None, skip=(), emit=0):
    """Element with position-derived (vanilla default) UVs: `up`/`down` read the plan texture,
    the four sides the elevation texture. tex maps up/down/side -> ref; a glow box lists only the
    faces it lights. Faces on the block boundary are culled. Housings are never tinted."""
    base = {"up": "#top", "down": "#top", "side": "#side"}
    tex = tex if tex is not None else base
    lo = {"west": frm[0], "down": frm[1], "north": frm[2]}
    hi = {"east": to[0], "up": to[1], "south": to[2]}
    faces, cull = {}, {}
    for f in FACES:
        ref = tex.get(f if f in ("up", "down") else "side")
        if ref is None or f in skip:
            continue
        faces[f] = ref
        if lo.get(f) == 0 or hi.get(f) == 16:
            cull[f] = f
    return c.el(frm, to, faces, cull=cull, tint=None if emit else False, emit=emit)


def rotated(c, model, rot):
    v = {"model": c.model(model)}
    if rot[0]:
        v["x"] = rot[0]
    if rot[1]:
        v["y"] = rot[1]
    return v


def _mount(face):
    """Blockstate rotation taking a floor-mounted model to `face`."""
    for rx in (0, 90, 180, 270):
        for ry in (0, 90, 180, 270):
            if _rot(_DIRS["down"], rx, ry) == _DIRS[face]:
                return rx, ry
    raise ValueError(face)


# =========================================================================================
# declarations
# =========================================================================================
TEXTURES = {
    "holo_projector_top": holo_projector_top, "holo_projector_side": holo_projector_side,
    "holo_projector_top_glow": holo_projector_top_glow,
    "holo_projector_side_glow": holo_projector_side_glow,
    "holo_projector_beam": holo_projector_beam, "holo_projector_scanline": holo_projector_scanline,
    "holo_projector_shimmer": holo_projector_shimmer, "holo_projector_idle": holo_projector_idle,
    "holo_sign_base": holo_sign_base, "holo_sign_base_glow": holo_sign_base_glow,
    "holo_sign_panel_glow": holo_sign_panel_glow, "holo_sign_backing": holo_sign_backing,
    "holo_sign_icons": holo_sign_icons,
    "arrow_sign_base": arrow_sign_base, "arrow_sign_base_glow": arrow_sign_base_glow,
    "world_globe_base_top": world_globe_base_top, "world_globe_base_side": world_globe_base_side,
    "world_globe_scan": world_globe_scan, "world_globe_halo": world_globe_halo,
    "holo_table_top": holo_table_top, "holo_table_side": holo_table_side,
    "holo_table_under": holo_table_under, "holo_table_top_glow": holo_table_top_glow,
    "holo_table_side_glow": holo_table_side_glow, "holo_table_grid": holo_table_grid,
    "holo_table_beam": holo_table_beam, "holo_table_blips": holo_table_blips,
    "holo_plinth_top": holo_plinth_top, "holo_plinth_side": holo_plinth_side,
    "holo_plinth_top_glow": holo_plinth_top_glow, "holo_plinth_side_glow": holo_plinth_side_glow,
}
_SIGN_TEX = {"base": "holo_sign_base", "base_glow": "holo_sign_base_glow",
             "panel": "holo_sign_panel_glow", "be_backing": "holo_sign_backing",
             "be_icons": "holo_sign_icons"}
for _p in range((len(GLYPHS) + 39) // 40):
    TEXTURES["holo_sign_glyphs_%d" % _p] = _glyph_page(_p)
    _SIGN_TEX["be_glyphs_%d" % _p] = "holo_sign_glyphs_%d" % _p
_ARROW_TEX = {"base": "arrow_sign_base", "base_glow": "arrow_sign_base_glow"}
for _d in DIRECTIONS:
    TEXTURES["arrow_sign_%s_glow" % _d] = _arrow(_d)
    _ARROW_TEX[_d] = "arrow_sign_%s_glow" % _d
_GLOBE_TEX = {"top": "world_globe_base_top", "side": "world_globe_base_side",
              "be_scan": "world_globe_scan", "be_halo": "world_globe_halo"}
for _w in WORLDS:
    TEXTURES["world_globe_base_%s_glow" % _w] = _globe_base_glow(_w)
    TEXTURES["world_globe_%s" % _w] = _globe_surface(_w)
    TEXTURES["world_globe_%s_glow" % _w] = _globe_glow(_w)
    _GLOBE_TEX["base_glow_" + _w] = "world_globe_base_%s_glow" % _w
    _GLOBE_TEX["be_map_" + _w] = "world_globe_%s" % _w
    _GLOBE_TEX["be_lights_" + _w] = "world_globe_%s_glow" % _w
_CURTAIN_TEX = {}
for _m in range(16):
    TEXTURES["holo_curtain_f%d" % _m] = _curtain(_m)
    _CURTAIN_TEX["f%d" % _m] = "holo_curtain_f%d" % _m
_TICKER_TEX = {}
for _p in (0, 1):
    for _n in (0, 1):
        for _g in (False, True):
            for _t in (False, True):
                _id = _ticker_tid(_p, _n, _g, _t)
                TEXTURES[_id] = _ticker(_p, _n, _g, _t)
                _TICKER_TEX[_id] = _id

BLOCKS = [
    Block("holo_projector", shape="custom", chroma=True, emissive=True, block_entity=True,
          textures={"top": "holo_projector_top", "side": "holo_projector_side",
                    "glow_top": "holo_projector_top_glow", "glow_side": "holo_projector_side_glow",
                    "be_beam": "holo_projector_beam", "be_scanline": "holo_projector_scanline",
                    "be_shimmer": "holo_projector_shimmer", "be_idle": "holo_projector_idle"},
          model=projector_model, blockstate=projector_state),
    Block("holo_sign", shape="custom", chroma=True, emissive=True, block_entity=True,
          textures=_SIGN_TEX, model=sign_model, blockstate=sign_state),
    Block("arrow_sign", shape="custom", chroma=True, emissive=True, light=7,
          textures=_ARROW_TEX, model=arrow_models, blockstate=arrow_state,
          item_model="arrow_sign_right"),
    Block("world_globe", shape="custom", emissive=True, block_entity=True,
          textures=_GLOBE_TEX, model=globe_models, blockstate=globe_state,
          item_model="world_globe_station"),
    Block("holo_table", shape="custom", chroma=True, emissive=True, block_entity=True,
          textures={"top": "holo_table_top", "side": "holo_table_side", "under": "holo_table_under",
                    "glow_top": "holo_table_top_glow", "glow_side": "holo_table_side_glow",
                    "be_grid": "holo_table_grid", "be_beam": "holo_table_beam",
                    "be_blips": "holo_table_blips"},
          model=table_model),
    Block("holo_curtain", shape="custom", connects="mask16", chroma=True, emissive=True, light=8,
          textures=_CURTAIN_TEX, model=curtain_models, blockstate=curtain_state,
          item_model="holo_curtain_f15", render_type="minecraft:translucent"),
    Block("holo_plinth", shape="custom", emissive=True,
          textures={"top": "holo_plinth_top", "side": "holo_plinth_side",
                    "glow_top": "holo_plinth_top_glow", "glow_side": "holo_plinth_side_glow"},
          model=plinth_model),
    Block("ticker_strip", shape="custom", connects="strip", chroma=True, emissive=True,
          textures=_TICKER_TEX, model=ticker_models, blockstate=ticker_state,
          item_model="ticker_strip_c11"),
]
