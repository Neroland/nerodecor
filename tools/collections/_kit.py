"""
NeroDecor collection kit -- the shared house painting toolkit and JSON emitters every collection
module under tools/collections/ uses. Deterministic: no RNG, no clocks; every bit of "noise" is a
hash of its inputs (det()), so a rerun is byte-identical.

Painting rules this kit enforces (see MODELS.md "Collection layer"):
  * colours come from 5-step hue-shifted ramps (ramp()/ramp5()); index 0 = occlusion ... 4 = glint;
    no pure grey and no pure black
  * key light is top-left: 1px highlight on the top/left edge, 1px occlusion on the bottom/right
  * wear touches at most 8% of a texture's pixels, chosen by hash(id, x, y)
  * dithering is for large gradients only (>= 64 px area)
  * glow layers are alpha 0/255, one accent hue, and <= 12% of pixels unless the block is a light
  * animations are fixed kinds (breathing 8, data/flow 16, flicker 4 frames) with a per-texture
    frame offset so neighbouring textures do not pulse in lockstep
"""
import colorsys
import hashlib
import io
import json
import math
import os
from dataclasses import dataclass, field
from typing import Callable, Optional

try:  # painting needs Pillow; JSON emission (used by gen_resources.py) does not
    from PIL import Image
except ModuleNotFoundError:  # pragma: no cover
    Image = None

NS = "nerodecor"
S = 16
HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)

# =========================================================================================
# Declarative block spec
# =========================================================================================
SHAPES = ("cube", "strip", "custom")
CONNECTS = ("none", "mask16", "strip")


@dataclass
class Block:
    """One block a collection declares. JSON + textures are derived from it by gen_collections.

    id          registry path (nerodecor:<id>); must match the Java registration
    shape       cube | strip | custom
    connects    none | mask16 (6 booleans north..down, baked 2px bezel per face) |
                strip (axis + pos_link/neg_link, baked caps; shape must be "strip")
    chroma      hub-driven colour: every base face carries tintindex 0
    paintable   has the COLOR property: also tinted, and its item gets the nerodecor:decor_color tint
    emissive    textures[...] has a <tex>_glow overlay drawn as a second full-size cutout layer
    light       block light level (Java side). light > 0 lifts the 12% glow-pixel cap
    block_entity  has a block entity (BER / Java-driven state); JSON is still static
    textures    role -> texture id painted by the module's TEXTURES. cube/mask16/strip use "all"
    model       custom only: fn(ctx) -> {model_name: model_json}; ctx = Ctx (refs + element helpers)
    blockstate  custom only: fn(ctx) -> blockstate json (default: single variant of item_model)
    item_model  custom only: model name used for the item (default: first model returned)
    name        en_us name (default: title-cased id)
    family      neroland:decor/<family> tag (default: the collection name)
    recipe      optional recipe json written to data/nerodecor/recipe/<id>.json
    loot        optional loot table json (default: drops itself, survives_explosion). Use it for
                blocks whose drop must carry data, e.g. copy_components from the block entity.
                Written in the pre-26.3 format; the runner also writes the 26.3-format copy to
                common/src/main/resources-26.3 (loot_26_3)
    render_type optional model render_type (emissive blocks default to minecraft:cutout)

    Every emitted block model gets "parent": "minecraft:block/block" unless it sets its own parent,
    so items that use it (models/item/<id>.json parents the item model) inherit vanilla display
    transforms. A model's own "display" still overrides the parent's per slot.
    """
    id: str
    shape: str = "cube"
    connects: str = "none"
    chroma: bool = False
    paintable: bool = False
    emissive: bool = False
    light: int = 0
    block_entity: bool = False
    textures: dict = field(default_factory=dict)
    model: Optional[Callable] = None
    blockstate: Optional[Callable] = None
    item_model: Optional[str] = None
    name: Optional[str] = None
    family: Optional[str] = None
    recipe: Optional[dict] = None
    loot: Optional[dict] = None
    render_type: Optional[str] = None

    @property
    def tinted(self):
        return self.chroma or self.paintable


@dataclass
class Item:
    """Item-only art (an item with no block) a collection declares in ITEMS; its painters live in
    the module's ITEM_TEXTURES ({texture id: painter}). gen_collections writes
    textures/item/<collection>/<tid>.png, models/item/<id>.json and items/<id>.json; the en_us
    `item.nerodecor.<id>` entry is merged by gen_resources.py like block names.

    id      registry path (nerodecor:<id>); must match the Java item registration
    layers  texture ids, one per model layer (layer0, layer1, ...); each 16x16 (32x32 allowed),
            alpha 0/255 only, no pure grey/black
    parent  item model parent (minecraft:item/generated, or minecraft:item/handheld for tools)
    tints   optional client-item tint sources, one per layer (e.g. {"type": "minecraft:constant",
            "value": -1} for an untinted layer, {"type": "nerodecor:decor_color"}, a minecraft:dye)
    name    en_us name (default: title-cased id)
    """
    id: str
    layers: list
    parent: str = "minecraft:item/generated"
    tints: Optional[list] = None
    name: Optional[str] = None


@dataclass
class Art:
    """A painted texture: one frame (still) or an animation strip."""
    frames: list
    anim: Optional[str] = None          # breathing | data | flow | flicker | None
    interpolate: Optional[bool] = None  # None = kind default (glow textures never interpolate)

    @staticmethod
    def of(x):
        return x if isinstance(x, Art) else Art([x])

    @property
    def size(self):
        return self.frames[0].size


# =========================================================================================
# Deterministic maths
# =========================================================================================
def det(*parts):
    """Deterministic float in [0,1) -- the only source of 'noise'."""
    h = hashlib.sha256("|".join(str(p) for p in parts).encode()).digest()
    return int.from_bytes(h[:4], "big") / 0x100000000


def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else hi if v > hi else v


def lerp(a, b, t):
    t = clamp(t)
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# =========================================================================================
# Palette + ramps
# =========================================================================================
_PALETTE = None


def palette():
    global _PALETTE
    if _PALETTE is None:
        with open(os.path.join(TOOLS, "palette.json"), encoding="utf-8") as fh:
            _PALETTE = json.load(fh)
    return _PALETTE


def ramp(name):
    """A named 5-step ramp from tools/palette.json: ramps.house.<name> or ramps.worlds.<name>
    (station, luna, mars, cindara, glacira, greenxertz, nyxara). Index 0 darkest .. 4 brightest."""
    r = palette()["ramps"]
    for group in ("house", "worlds"):
        if name in r[group]:
            return [hexrgb(h) for h in r[group][name]]
    raise KeyError("no ramp %r in palette.json (house: %s; worlds: %s)"
                   % (name, sorted(r["house"]), sorted(r["worlds"])))


def accent(name):
    """A single palette colour: materials.* or accents.* (the DecorColor dye set)."""
    p = palette()
    for group in ("materials", "accents"):
        if name in p[group]:
            return hexrgb(p[group][name]["rgb"])
    raise KeyError(name)


def _hue_toward(h, target, amt):
    d = ((target - h + 0.5) % 1.0) - 0.5
    return (h + d * amt) % 1.0


def ramp5(base, shift=1.0):
    """5-step hue-shifted ramp around `base` (index 2). Shadows drift toward blue-violet and gain
    saturation, highlights drift toward warm yellow and lose a little; value never hits 0 and
    saturation never drops below 0.10, so there is no pure black or pure grey."""
    h, s, v = colorsys.rgb_to_hsv(*(c / 255.0 for c in base))
    s = max(s, 0.10)
    out = []
    for step, vf, sf, target in ((0, 0.42, 1.18, 0.70), (1, 0.70, 1.08, 0.70),
                                 (2, 1.00, 1.00, h), (3, 1.25, 0.86, 0.14), (4, 1.48, 0.66, 0.14)):
        amt = {0: 0.16, 1: 0.08, 2: 0.0, 3: 0.07, 4: 0.13}[step] * shift
        hh = _hue_toward(h, target, amt)
        ss = clamp(s * sf, 0.10, 1.0)
        vv = clamp(v * vf, 0.07, 0.98)
        out.append(tuple(round(c * 255) for c in colorsys.hsv_to_rgb(hh, ss, vv)))
    return out


def nearest(r, rgb):
    """Index of the ramp step closest to rgb, and the squared distance."""
    best = min(range(len(r)), key=lambda i: sum((r[i][k] - rgb[k]) ** 2 for k in range(3)))
    return best, sum((r[best][k] - rgb[k]) ** 2 for k in range(3))


# =========================================================================================
# Canvas helpers
# =========================================================================================
def new(w=S, h=None, fill=None):
    """New RGBA canvas; fill = rgb (opaque) or None (transparent)."""
    return Image.new("RGBA", (w, h or w), (tuple(fill) + (255,)) if fill else (0, 0, 0, 0))


def put(img, x, y, rgb, a=255):
    w, h = img.size
    img.load()[x % w, y % h] = tuple(rgb[:3]) + (a,)


def rect(img, x0, y0, x1, y1, rgb, a=255):
    """Fill [x0,x1) x [y0,y1)."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(img, x, y, rgb, a)


def bevel(img, x0, y0, x1, y1, r, fill=None):
    """Top-left key light on [x0,x1) x [y0,y1): 1px highlight (r[3]) on the top row + left column,
    1px occlusion (r[0]) on the bottom row + right column, glint r[4] at the top-left pixel.
    fill = ramp index to flood the interior with first (None keeps it)."""
    if fill is not None:
        rect(img, x0, y0, x1, y1, r[fill])
    for x in range(x0, x1):
        put(img, x, y0, r[3])
        put(img, x, y1 - 1, r[0])
    for y in range(y0, y1):
        put(img, x0, y, r[3])
        put(img, x1 - 1, y, r[0])
    put(img, x0, y0, r[4])
    put(img, x1 - 1, y0, r[2])
    put(img, x0, y1 - 1, r[1])


def panel(img, x0, y0, x1, y1, r, fill=2):
    bevel(img, x0, y0, x1, y1, r, fill)


WEAR_MAX = 0.08


def wear(img, seed, r, rate=0.05, region=None):
    """Seeded wear: shift up to `rate` (<= 8%) of the pixels that belong to ramp `r` one step
    darker or lighter. Deterministic from hash(seed, x, y); returns img for chaining."""
    if rate > WEAR_MAX:
        raise ValueError("wear rate %.3f > %.2f" % (rate, WEAR_MAX))
    w, h = img.size
    x0, y0, x1, y1 = region or (0, 0, w, h)
    px = img.load()
    cand = []
    for y in range(y0, y1):
        for x in range(x0, x1):
            d = det(seed, "wear", x, y)
            if d < rate and px[x, y][3] == 255:
                cand.append((d, x, y))
    cap = int(w * h * rate)
    for d, x, y in sorted(cand)[:cap]:
        i, dist = nearest(r, px[x, y][:3])
        if dist > 40 * 40:
            continue
        j = max(0, i - 1) if det(seed, "dir", x, y) < 0.6 else min(len(r) - 1, i + 1)
        px[x, y] = r[j] + (255,)
    return img


_BAYER4 = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def dither(img, x0, y0, x1, y1, c0, c1, axis="y"):
    """Ordered (4x4 Bayer) two-colour gradient from c0 to c1 along `axis` over [x0,x1) x [y0,y1).
    Large gradients only: the area must be at least 64 px."""
    if (x1 - x0) * (y1 - y0) < 64:
        raise ValueError("dither() is for large gradients only (area >= 64 px)")
    span = (y1 - y0) if axis == "y" else (x1 - x0)
    for y in range(y0, y1):
        for x in range(x0, x1):
            t = ((y - y0) if axis == "y" else (x - x0)) / max(1, span - 1)
            put(img, x, y, c1 if t * 16 > _BAYER4[y % 4][x % 4] + 0.5 else c0)


def over(base, top):
    out = base.copy()
    out.alpha_composite(top)
    return out


def transpose(img):
    return img.transpose(Image.Transpose.TRANSPOSE)


# =========================================================================================
# Emissive
# =========================================================================================
GLOW_MAX = 0.12
HUE_TOL = 24 / 360.0


def glow_layer(img):
    """Keep the (mostly) opaque pixels of a layer and force alpha to exactly 0 / 255."""
    out = new(*img.size)
    s, d = img.load(), out.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = s[x, y]
            if a >= 128:
                d[x, y] = (r, g, b, 255)
    return out


def emissive_ratio(img):
    w, h = img.size
    px = img.load()
    return sum(1 for y in range(h) for x in range(w) if px[x, y][3]) / float(w * h)


def check_glow(tid, img, light=False):
    """Raise ValueError unless `img` is a valid glow layer: alpha 0/255 only, one accent hue
    (pixels with saturation < 0.15 count as a neutral hot core), and <= 12% emissive pixels
    unless the block is itself a light."""
    w, h = img.size
    px = img.load()
    hues = []
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a not in (0, 255):
                raise ValueError("%s: glow alpha must be 0 or 255 (found %d at %d,%d)" % (tid, a, x, y))
            if a:
                hh, ss, _ = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
                if ss >= 0.15:
                    hues.append(hh)
    if not light and emissive_ratio(img) > GLOW_MAX + 1e-9:
        raise ValueError("%s: %.1f%% emissive pixels > 12%% (non-light block)" % (tid, emissive_ratio(img) * 100))
    if hues:
        mx = sum(math.cos(2 * math.pi * hh) for hh in hues)
        my = sum(math.sin(2 * math.pi * hh) for hh in hues)
        mean = (math.atan2(my, mx) / (2 * math.pi)) % 1.0
        worst = max(abs(((hh - mean + 0.5) % 1.0) - 0.5) for hh in hues)
        if worst > HUE_TOL:
            raise ValueError("%s: glow uses more than one accent hue (spread %.0f deg)" % (tid, worst * 360))


def daylight(base, glow, f=0.55):
    """Base texture with the glow pixels drawn in at factor f -- how the emitter reads by day."""
    out = base.copy()
    s, d = glow.load(), out.load()
    w, h = base.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = s[x, y]
            if a:
                d[x, y] = (round(r * f), round(g * f), round(b * f), 255)
    return out


# =========================================================================================
# Animation
# =========================================================================================
# kind -> (frames, frametime ticks, interpolate)
ANIM_KINDS = {"breathing": (8, 3, True), "data": (16, 2, False), "flow": (16, 2, False),
              "flicker": (4, 2, False)}


def animate(tid, kind, paint):
    """paint(t) -> Image for t in [0,1) (t = frame / n). Frames are rotated by a per-texture
    offset hash(tid) so neighbouring textures do not pulse in lockstep. Loops must be seamless."""
    n = ANIM_KINDS[kind][0]
    off = int(det("phase", tid) * n)
    frames = [paint(i / float(n)) for i in range(n)]
    return Art(frames[off:] + frames[:off], kind)


def strip_png(frames):
    w, h = frames[0].size
    out = Image.new("RGBA", (w, h * len(frames)), (0, 0, 0, 0))
    for i, fr in enumerate(frames):
        out.paste(fr, (0, i * h))
    return out


def png_bytes(img):
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def mcmeta(art, glow):
    n = len(art.frames)
    ft = ANIM_KINDS[art.anim][1] if art.anim else 2
    interp = art.interpolate if art.interpolate is not None else (ANIM_KINDS.get(art.anim, (0, 0, False))[2])
    if glow:
        interp = False  # blending a cutout layer would create partial alpha
    return (json.dumps({"animation": {"interpolate": interp, "frametime": ft, "frames": list(range(n))}},
                       indent=2) + "\n").encode("utf-8")


# =========================================================================================
# Connected-texture generators (the Luminous technique: bezels/caps BAKED into face variants,
# never a second partial quad -- see MODELS.md, z-fight rule)
# =========================================================================================
FRAME_W = 2
CAP_W = 3


def bezel_layer(r, w=S, h=None, accent_rgb=None):
    """Transparent layer with a 2px frame lit top-left: outer seam r[0], inner ring r[3] on the
    top/left and r[1] on the bottom/right. mask16 blocks paint TEXTURES['<tid>_frame'] with this."""
    h = h or w
    img = new(w, h)
    for i in range(w):
        put(img, i, 0, r[0])
        put(img, i, h - 1, r[0])
    for i in range(h):
        put(img, 0, i, r[0])
        put(img, w - 1, i, r[0])
    for i in range(1, w - 1):
        put(img, i, 1, r[3])
        put(img, i, h - 2, r[1])
    for i in range(1, h - 1):
        put(img, 1, i, r[3])
        put(img, w - 2, i, r[1])
    put(img, w - 2, 1, r[2])
    put(img, 1, h - 2, r[2])
    if accent_rgb:
        for (x, y) in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
            put(img, x, y, accent_rgb)
    return img


def cap_layer(r, w=S, h=None):
    """Opaque end-cap metal for strips (symmetric, so it reads right at either end): seam r[0] on
    the outer columns, a lit ridge r[3] beside it, body r[2], key-lit rows."""
    h = h or w
    img = new(w, h, r[2])
    for y in range(h):
        for x in (0, w - 1):
            put(img, x, y, r[0])
        put(img, 1, y, r[3])
        put(img, w - 2, y, r[3])
        put(img, 2, y, r[1])
        put(img, w - 3, y, r[1])
    return img


def _band(w, h, mask, width):
    """Predicate for texture edges selected by mask (8=top, 4=bottom, 2=left, 1=right)."""
    def inside(x, y):
        return ((mask & 8 and y < width) or (mask & 4 and y >= h - width)
                or (mask & 2 and x < width) or (mask & 1 and x >= w - width))
    return inside


def _apply(img, layer, inside):
    out = img.copy()
    o, d = layer.load(), out.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            if inside(x, y) and o[x, y][3]:
                d[x, y] = o[x, y]
    return out


def _clear(img, inside):
    out = img.copy()
    d = out.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            if inside(x, y):
                d[x, y] = (0, 0, 0, 0)
    return out


def _variant(art, fn):
    return Art([fn(f) for f in art.frames], art.anim, art.interpolate)


def expand_mask16(base, frame, glow=None, width=FRAME_W):
    """16-mask bezel generator: {'_f<mask>': Art, '_glow_f<mask>': Art}. mask bits = texture edges
    carrying the bezel: 8 top, 4 bottom, 2 left, 1 right (0 = fully connected, 15 = isolated).
    The glow is cleared under the bezel so it never paints over it."""
    w, h = base.size
    out = {}
    for m in range(16):
        band = _band(w, h, m, width)
        out["_f%d" % m] = _variant(base, lambda f, b=band: _apply(f, frame, b))
        if glow:
            out["_glow_f%d" % m] = _variant(glow, lambda f, b=band: _clear(f, b))
    return out


def expand_strip(base, cap, glow=None, width=CAP_W):
    """Strip cap/mid/cap generator: {'_c<p><n>': Art, '_glow_c<p><n>': Art}. The strip runs along
    texture x; p=1 bakes a cap on the RIGHT columns (+axis end), n=1 on the LEFT (-axis end).
    c00 = mid, c10/c01 = one end, c11 = a lone strip. Each also gets a transposed `_t` twin (strip
    along texture y, bottom = +axis) for the z-running model the blockstate needs."""
    w, h = base.size
    out = {}
    for p in (0, 1):
        for n in (0, 1):
            band = _band(w, h, p * 1 + n * 2, width)
            v = _variant(base, lambda f, b=band: _apply(f, cap, b))
            out["_c%d%d" % (p, n)] = v
            out["_c%d%d_t" % (p, n)] = _variant(v, transpose)
            if glow:
                g = _variant(glow, lambda f, b=band: _clear(f, b))
                out["_glow_c%d%d" % (p, n)] = g
                out["_glow_c%d%d_t" % (p, n)] = _variant(g, transpose)
    return out


# =========================================================================================
# JSON emitters
# =========================================================================================
FACES = ("north", "east", "south", "west", "up", "down")
# For each face, which world direction each TEXTURE edge touches (vanilla auto-UV): (top, bottom,
# left, right). Same table as gen_resources.FACE_EDGES.
FACE_EDGES = {
    "north": ("up", "down", "east", "west"), "south": ("up", "down", "west", "east"),
    "west": ("up", "down", "north", "south"), "east": ("up", "down", "south", "north"),
    "up": ("north", "south", "west", "east"), "down": ("south", "north", "west", "east"),
}
MAX_ELEMENTS = 40


def title(name):
    return " ".join(w[:1].upper() + w[1:] for w in name.split("_") if w)


class Ctx:
    """What a custom model/blockstate function gets: refs scoped to its collection, plus el()."""

    def __init__(self, coll, block):
        self.coll, self.block = coll, block

    def tex(self, tid):
        return "%s:block/%s/%s" % (NS, self.coll, tid)

    def model(self, name):
        return "%s:block/%s/%s" % (NS, self.coll, name)

    def el(self, frm, to, faces, texture=None, uv=None, cull=True, tint=None, emit=0):
        """One element. faces = list of face names or {face: texture | {face json}}; tint None =
        chroma for glow layers, tinted (chroma or paintable) otherwise; emit > 0 makes it a glow layer (shade false + light_emission)."""
        if tint is None:  # chroma glow takes the hub colour; a paintable block's accent glow does not
            tint = self.block.chroma if emit else self.block.tinted
        return el(frm, to, faces, texture, uv=uv, cull=cull, tint=tint, emit=emit)


def el(frm, to, faces, texture=None, uv=None, cull=True, tint=False, emit=0):
    fs = {}
    for f in faces:
        spec = faces[f] if isinstance(faces, dict) else texture
        face = dict(spec) if isinstance(spec, dict) else {"texture": spec}
        if uv and "uv" not in face:
            face["uv"] = uv
        if cull is True and "cullface" not in face:
            face["cullface"] = f
        elif isinstance(cull, dict) and cull.get(f):
            face["cullface"] = cull[f]
        if tint:
            face["tintindex"] = 0
        fs[f] = face
    e = {"from": list(frm), "to": list(to)}
    if emit:
        e["shade"] = False
        e["light_emission"] = emit
    e["faces"] = fs
    return e


def _mdl(textures, elements, rt=None):
    m = {"textures": textures, "elements": elements}
    if rt:
        m["render_type"] = rt
    return m


def _rt(b):
    return b.render_type or ("minecraft:cutout" if b.emissive else None)


def cube_json(c, b):
    t = b.textures["all"]
    tx = {"particle": c.tex(t), "all": c.tex(t)}
    els = [c.el([0, 0, 0], [16, 16, 16], FACES, "#all", uv=[0, 0, 16, 16])]
    if b.emissive:
        tx["glow"] = c.tex(t + "_glow")
        els.append(c.el([0, 0, 0], [16, 16, 16], FACES, "#glow", uv=[0, 0, 16, 16], emit=15))
    models = {b.id: _mdl(tx, els, _rt(b))}
    return models, {"variants": {"": {"model": c.model(b.id)}}}, b.id


def mask16_json(c, b):
    """Port of the Luminous connected cube: one multipart part per face x 16 neighbour masks."""
    t = b.textures["all"]
    models, parts = {}, []
    for face in FACES:
        edges = FACE_EDGES[face]
        for m in range(16):
            bits = (m & 8, m & 4, m & 2, m & 1)
            tx = {"particle": c.tex(t), "all": c.tex("%s_f%d" % (t, m))}
            els = [c.el([0, 0, 0], [16, 16, 16], [face], "#all", uv=[0, 0, 16, 16])]
            if b.emissive:
                tx["glow"] = c.tex("%s_glow_f%d" % (t, m))
                els.append(c.el([0, 0, 0], [16, 16, 16], [face], "#glow", uv=[0, 0, 16, 16], emit=15))
            name = "%s_%s_f%d" % (b.id, face, m)
            models[name] = _mdl(tx, els, _rt(b))
            parts.append({"when": {d: ("false" if bit else "true") for d, bit in zip(edges, bits)},
                          "apply": {"model": c.model(name)}})
    tx = {"particle": c.tex(t), "all": c.tex(t + "_f15")}
    els = [c.el([0, 0, 0], [16, 16, 16], FACES, "#all", uv=[0, 0, 16, 16])]
    if b.emissive:
        tx["glow"] = c.tex(t + "_glow_f15")
        els.append(c.el([0, 0, 0], [16, 16, 16], FACES, "#glow", uv=[0, 0, 16, 16], emit=15))
    models[b.id + "_inventory"] = _mdl(tx, els, _rt(b))
    return models, {"multipart": parts}, b.id + "_inventory"


# --- strips ---------------------------------------------------------------------------------
# Two canonical strip models, both mounted on the block's DOWN face: a 2px-wide x 1px-thick bar
# running along +x (`<id>_c<p><n>`, top face samples texture rows 7..8, texture right = +x) and the
# same bar running along +z (`<id>_c<p><n>_z`, samples the transposed `_t` texture, bottom = +z).
# Vanilla blockstates rotate x first, then y, which only reaches every (face, axis) pair from both
# canonicals together. Blockstate properties: face (the side it is mounted on), axis (run axis,
# perpendicular to face), pos_link / neg_link (the same strip continues at the +axis / -axis end).
# When a rotation flips the run direction, pos and neg swap.
_DIRS = {"down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1), "south": (0, 0, 1),
         "west": (-1, 0, 0), "east": (1, 0, 0)}
_AXES = {"x": (1, 0, 0), "y": (0, 1, 0), "z": (0, 0, 1)}


def _rot(v, rx, ry):
    """Blockstate rotation (x first, then y), vanilla convention: x=90 maps up -> north,
    y=90 maps north -> east."""
    x, y, z = v
    for _ in range(rx // 90):
        y, z = z, -y
    for _ in range(ry // 90):
        x, z = -z, x
    return (x, y, z)


def strip_rotation(face, axis):
    """(canonical '' or '_z', x, y, flipped) taking a canonical strip to (face, axis)."""
    want_f, want_a = _DIRS[face], _AXES[axis]
    for canon, run0 in (("", (1, 0, 0)), ("_z", (0, 0, 1))):
        for rx in (0, 90, 180, 270):
            for ry in (0, 90, 180, 270):
                if _rot(_DIRS["down"], rx, ry) != want_f:
                    continue
                run = _rot(run0, rx, ry)
                if run == want_a:
                    return canon, rx, ry, False
                if run == tuple(-k for k in want_a):
                    return canon, rx, ry, True
    raise ValueError("no rotation for strip face=%s axis=%s" % (face, axis))


def _strip_models(c, b, t, p, n, canon):
    v = "%s_c%d%d" % (t, p, n)
    tx = {"particle": c.tex(t), "bar": c.tex(v)}
    if canon == "":
        frm, to = (0, 0, 7), (16, 1, 9)
        # u runs with +x on up/south; north runs against it, so its uv is mirrored
        faces = {"up": {"texture": "#bar", "uv": [0, 7, 16, 9]},
                 "north": {"texture": "#bar", "uv": [16, 6, 0, 7]},
                 "south": {"texture": "#bar", "uv": [0, 9, 16, 10]},
                 "west": {"texture": "#bar", "uv": [0, 7, 2, 8]},
                 "east": {"texture": "#bar", "uv": [14, 7, 16, 8]},
                 "down": {"texture": "#bar", "uv": [0, 7, 16, 9], "cullface": "down"}}
        gface = {"up": {"texture": "#glow", "uv": [0, 7, 16, 9]}}
    else:
        tx["bar_t"] = c.tex(v + "_t")
        frm, to = (7, 0, 0), (9, 1, 16)
        # up face: u = x, v = z -> the transposed texture (strip along v, bottom = +z)
        faces = {"up": {"texture": "#bar_t", "uv": [7, 0, 9, 16]},
                 "west": {"texture": "#bar", "uv": [0, 6, 16, 7]},
                 "east": {"texture": "#bar", "uv": [16, 9, 0, 10]},
                 "north": {"texture": "#bar", "uv": [0, 7, 2, 8]},
                 "south": {"texture": "#bar", "uv": [14, 7, 16, 8]},
                 "down": {"texture": "#bar_t", "uv": [7, 0, 9, 16], "cullface": "down"}}
        gface = {"up": {"texture": "#glow", "uv": [7, 0, 9, 16]}}
    els = [c.el(frm, to, faces, cull=False)]
    if b.emissive:
        tx["glow"] = c.tex("%s_glow_c%d%d%s" % (t, p, n, "_t" if canon else ""))
        els.append(c.el(frm, to, gface, cull=False, emit=15))
    return _mdl(tx, els, _rt(b))


def strip_json(c, b):
    t = b.textures["all"]
    models = {}
    for canon in ("", "_z"):
        for p in (0, 1):
            for n in (0, 1):
                models["%s_c%d%d%s" % (b.id, p, n, canon)] = _strip_models(c, b, t, p, n, canon)
    variants = {}
    for face in _DIRS:
        for axis in _AXES:
            if any(_DIRS[face][i] and _AXES[axis][i] for i in range(3)):
                continue  # run axis must lie in the mounting plane
            canon, rx, ry, flip = strip_rotation(face, axis)
            for pos in (False, True):
                for neg in (False, True):
                    cp, cn = int(not pos), int(not neg)
                    if flip:
                        cp, cn = cn, cp
                    v = {"model": c.model("%s_c%d%d%s" % (b.id, cp, cn, canon))}
                    if rx:
                        v["x"] = rx
                    if ry:
                        v["y"] = ry
                    key = "axis=%s,face=%s,neg_link=%s,pos_link=%s" % (axis, face, str(neg).lower(), str(pos).lower())
                    variants[key] = v
    return models, {"variants": variants}, b.id + "_c11"


def custom_json(c, b):
    models = b.model(c)
    item = b.item_model or next(iter(models))
    bs = b.blockstate(c) if b.blockstate else {"variants": {"": {"model": c.model(item)}}}
    return models, bs, item


def facing_variants(c, model, prop="facing", horizontal=True, base="north"):
    """Blockstate variants rotating one model (drawn facing `base`=north) to every facing."""
    rot = {"north": (0, 0), "east": (0, 90), "south": (0, 180), "west": (0, 270),
           "up": (270, 0), "down": (90, 0)}
    out = {}
    for f in (("north", "east", "south", "west") if horizontal else rot):
        rx, ry = rot[f]
        v = {"model": c.model(model)}
        if rx:
            v["x"] = rx
        if ry:
            v["y"] = ry
        out["%s=%s" % (prop, f)] = v
    return {"variants": out}


EMITTERS = {("cube", "none"): cube_json, ("cube", "mask16"): mask16_json,
            ("strip", "strip"): strip_json}


def check_block(coll, b):
    if b.shape not in SHAPES or b.connects not in CONNECTS:
        raise ValueError("%s/%s: bad shape/connects %r/%r" % (coll, b.id, b.shape, b.connects))
    if b.shape == "custom":
        if not b.model:
            raise ValueError("%s/%s: shape 'custom' needs model=fn(ctx)" % (coll, b.id))
    elif (b.shape, b.connects) not in EMITTERS:
        raise ValueError("%s/%s: %s + connects=%s is not supported (use shape='custom')"
                         % (coll, b.id, b.shape, b.connects))
    elif "all" not in b.textures:
        raise ValueError("%s/%s: textures needs an 'all' entry" % (coll, b.id))


def _faces_planes(e):
    f, t = e["from"], e["to"]
    ax = {"down": (1, f[1]), "up": (1, t[1]), "north": (2, f[2]), "south": (2, t[2]),
          "west": (0, f[0]), "east": (0, t[0])}
    for name in e["faces"]:
        a, coord = ax[name]
        o = [i for i in range(3) if i != a]
        yield name, coord, (f[o[0]], f[o[1]], t[o[0]], t[o[1]])


def validate_model(where, model):
    """<= 40 elements; glow elements are shade:false + light_emission; and the z-fight rule: two
    quads facing the same way on the same plane must be identical, or not overlap at all."""
    els = model.get("elements", [])
    if len(els) > MAX_ELEMENTS:
        raise ValueError("%s: %d elements > %d" % (where, len(els), MAX_ELEMENTS))
    quads = []
    for i, e in enumerate(els):
        if e.get("light_emission") and e.get("shade", True):
            raise ValueError("%s: element %d emits light but is shaded (set shade false)" % (where, i))
        if "rotation" in e:
            continue
        for name, coord, r in _faces_planes(e):
            quads.append((name, coord, r, i))
    for a in range(len(quads)):
        for b in range(a + 1, len(quads)):
            n1, c1, r1, i1 = quads[a]
            n2, c2, r2, i2 = quads[b]
            if n1 != n2 or c1 != c2 or r1 == r2:
                continue
            if min(r1[2], r2[2]) > max(r1[0], r2[0]) and min(r1[3], r2[3]) > max(r1[1], r2[1]):
                raise ValueError("%s: elements %d and %d have overlapping, non-identical %s quads on "
                                 "one plane (z-fight) -- bake the detail into the texture instead"
                                 % (where, i1, i2, n1))


BLOCK_PARENT = "minecraft:block/block"
# Output paths starting with this prefix land in common/src/main/resources-26.3 (merged over the shared
# resources for 26.3+ nodes); every other path is relative to common/src/main/resources.
RES_26_3 = "../resources-26.3/"


# --- 26.3 loot format ------------------------------------------------------------------------
def _ordered(d):
    """26.3 loot JSON key order: "type" first, the rest sorted (matches the hand-written overlays)."""
    return dict(([("type", d["type"])] if "type" in d else []) + sorted((k, v) for k, v in d.items() if k != "type"))


def _cond_26_3(c):
    c = dict(c)
    t = c.pop("condition")
    if t == "minecraft:block_state_property":
        c = {"blocks": c.pop("block"), "state": c.pop("properties"), **c}
        t = "minecraft:match_block"
    c["type"] = t
    return _ordered(c)


def _conds_26_3(cs):
    if len(cs) != 1:
        raise ValueError("26.3 loot: only one condition per node is supported, got %d" % len(cs))
    return _cond_26_3(cs[0])


def _fn_26_3(f):
    f = dict(f)
    f["type"] = f.pop("function")
    if "conditions" in f:
        f["condition"] = _conds_26_3(f.pop("conditions"))
    return _ordered(f)


def loot_26_3(table):
    """Pre-26.3 block loot table -> the 26.3 format (pool/entry `conditions` -> one `condition` with
    `type`, `functions` -> `modifier` with `type`, block_state_property -> match_block, bonus_rolls 0
    dropped)."""
    pools = []
    for p in table["pools"]:
        p = dict(p)
        if p.get("bonus_rolls", 0) == 0:
            p.pop("bonus_rolls", None)
        if "conditions" in p:
            p["condition"] = _conds_26_3(p.pop("conditions"))
        ents = []
        for e in p["entries"]:
            e = dict(e)
            if "functions" in e:
                e["modifier"] = [_fn_26_3(f) for f in e.pop("functions")]
            if "conditions" in e:
                e["condition"] = _conds_26_3(e.pop("conditions"))
            ents.append(_ordered(e))
        p["entries"] = ents
        pools.append(_ordered(p))
    out = {k: v for k, v in table.items() if k != "pools"}
    out["pools"] = pools
    return _ordered(out)




def block_json(coll, b, data=True):
    """Every JSON file for one block: {rel_path: obj}, relative to common/src/main/resources.
    data=False leaves out the data files (loot tables, recipe): the block is not registered in Java yet."""
    check_block(coll, b)
    c = Ctx(coll, b)
    fn = custom_json if b.shape == "custom" else EMITTERS[(b.shape, b.connects)]
    models, bs, item_model = fn(c, b)
    out = {}
    for name, m in models.items():
        validate_model("%s/%s" % (coll, name), m)
        if "parent" not in m:  # vanilla display transforms for the item that parents this model
            m = dict([("parent", BLOCK_PARENT)] + list(m.items()))
        out["assets/%s/models/block/%s/%s.json" % (NS, coll, name)] = m
    out["assets/%s/blockstates/%s.json" % (NS, b.id)] = bs
    out["assets/%s/models/item/%s.json" % (NS, b.id)] = {"parent": c.model(item_model)}
    item = {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, b.id)}}
    if b.paintable:
        item["model"]["tints"] = [{"type": "%s:decor_color" % NS}]
    out["assets/%s/items/%s.json" % (NS, b.id)] = item
    if not data:
        return out
    loot = b.loot or {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, b.id)}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]}
    out["data/%s/loot_table/blocks/%s.json" % (NS, b.id)] = loot
    # 26.3 changed the loot format; its nodes read this overlay instead (see loot_26_3)
    out["%sdata/%s/loot_table/blocks/%s.json" % (RES_26_3, NS, b.id)] = loot_26_3(loot)
    if b.recipe:
        out["data/%s/recipe/%s.json" % (NS, b.id)] = b.recipe
    return out


def block_aggregates(coll, b):
    """Shared-file entries gen_resources.py merges: (lang, family)."""
    name = b.name or title(b.id)
    return ({"block.%s.%s" % (NS, b.id): name, "item.%s.%s" % (NS, b.id): name}, b.family or coll)


def block_textures(coll, b, textures):
    """{texture id: Art} the block ships, expanded from the module's TEXTURES painters."""
    def get(tid, required=True):
        if tid not in textures:
            if required:
                raise KeyError("%s/%s: TEXTURES has no painter for %r" % (coll, b.id, tid))
            return None
        return Art.of(textures[tid]())
    out = {}
    if b.shape == "custom":
        for tid in b.textures.values():
            out[tid] = get(tid)
        return out
    t = b.textures["all"]
    base = get(t)
    glow = get(t + "_glow") if b.emissive else None
    out[t] = base
    if b.connects == "none":
        if glow:
            out[t + "_glow"] = glow
    elif b.connects == "mask16":
        out.update({t + k: v for k, v in expand_mask16(base, get(t + "_frame").frames[0], glow).items()})
    elif b.connects == "strip":
        out.update({t + k: v for k, v in expand_strip(base, get(t + "_cap").frames[0], glow).items()})
    return out


# =========================================================================================
# Item-only art (ITEMS / ITEM_TEXTURES)
# =========================================================================================
def item_json(coll, it):
    """Every JSON file for one item-only Item: {rel_path: obj}."""
    if not it.layers:
        raise ValueError("%s/%s: an Item needs at least one layer" % (coll, it.id))
    if it.tints is not None and len(it.tints) != len(it.layers):
        raise ValueError("%s/%s: tints needs one entry per layer" % (coll, it.id))
    model = {"parent": it.parent,
             "textures": {"layer%d" % i: "%s:item/%s/%s" % (NS, coll, t) for i, t in enumerate(it.layers)}}
    item = {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, it.id)}}
    if it.tints:
        item["model"]["tints"] = it.tints
    return {"assets/%s/models/item/%s.json" % (NS, it.id): model,
            "assets/%s/items/%s.json" % (NS, it.id): item}


def item_aggregates(coll, it):
    """Shared-file entries for an Item: its en_us lang."""
    return {"item.%s.%s" % (NS, it.id): it.name or title(it.id)}


def item_textures(coll, it, textures):
    """{texture id: Art} for an Item's layers, painted by the module's ITEM_TEXTURES."""
    out = {}
    for tid in it.layers:
        if tid not in textures:
            raise KeyError("%s/%s: ITEM_TEXTURES has no painter for %r" % (coll, it.id, tid))
        out[tid] = Art.of(textures[tid]())
    return out


def check_item_art(tid, img):
    """Item sprites: hard alpha (0/255) and no pure grey/black."""
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a not in (0, 255) or (a and r == g == b):
                raise ValueError("item/%s: bad pixel at %d,%d (alpha must be 0/255, no pure grey)"
                                 % (tid, x, y))
