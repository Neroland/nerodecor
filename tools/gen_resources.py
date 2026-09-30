#!/usr/bin/env python3
"""
NeroDecor resource harness — emits the committed, loader-agnostic JSON (blockstates, block +
item models, loot tables, recipes, tags, lang) for the registered decor blocks into
common/src/main/resources, matching the Core/nerospace convention (hand-committed JSON, NO
runtime datagen). Deterministic + idempotent: re-running rewrites byte-stable files.

The SPEC below mirrors registry/DecorBlocks.java — keep them in lockstep. Textures come from
tools/gen_textures.py; models here reference nerodecor:block/<texture>.

Usage: python tools/gen_resources.py   (or ./gradlew genAssets)
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
RES = os.path.join(REPO, "common", "src", "main", "resources")
NS = "nerodecor"

# name, kind, texture (base cube texture id path), family, recipe-material (Core ingot / vanilla)
# Finish sets — keep in LOCKSTEP with tools/gen_textures.py + registry/DecorBlocks.java.
STRUCT = ["nero_alloy", "starsteel", "void_crystal"]
GLASS_FIN = ["plasma_glass", "cyan", "light_blue"]
NEON = ["red", "orange", "yellow", "lime", "green", "cyan",
        "light_blue", "blue", "purple", "magenta", "pink", "white"]
STRUCT_MAT = {"nero_alloy": "nerolandcore:nero_alloy_ingot",
              "starsteel": "nerolandcore:starsteel_ingot",
              "void_crystal": "nerolandcore:void_crystal_shard"}
GLASS_MAT = {"plasma_glass": "nerolandcore:plasma_glass_block",
             "cyan": "minecraft:cyan_stained_glass",
             "light_blue": "minecraft:light_blue_stained_glass"}


def _build_spec():
    # (name, kind, cube-texture, family, recipe-material)
    s = []
    for m in STRUCT:
        s.append(("hull_%s" % m, "cube", "hull_%s" % m, "hull", STRUCT_MAT[m]))
        s.append(("hull_%s_slab" % m, "slab", "hull_%s" % m, "hull", None))
        s.append(("hull_%s_stairs" % m, "stairs", "hull_%s" % m, "hull", None))
        s.append(("hull_%s_wall" % m, "wall", "hull_%s" % m, "hull", None))
    for m in STRUCT:
        s.append(("panel_%s" % m, "cube", "panel_%s" % m, "panel", STRUCT_MAT[m]))
        s.append(("panel_%s_slab" % m, "slab", "panel_%s" % m, "panel", None))
        s.append(("panel_%s_stairs" % m, "stairs", "panel_%s" % m, "panel", None))
    for f in GLASS_FIN:
        s.append(("glass_%s" % f, "cube", "glass_%s" % f, "glass", GLASS_MAT[f]))
        s.append(("glass_%s_pane" % f, "pane", "glass_%s" % f, "glass", None))
        s.append(("glass_%s_slab" % f, "slab", "glass_%s" % f, "glass", None))
    for c in NEON:
        s.append(("neon_%s" % c, "cube", "neon_%s" % c, "neon", c))
    return s


SPEC = _build_spec()
CUBE_OF = {t: name for (name, kind, t, fam, mat) in SPEC if kind == "cube"}

files = {}          # relative path -> dict (written as JSON)
lang = {}
mineable = []
decor_tags = {}     # family -> [block ids]
walls = []          # wall block ids — need #minecraft:walls to connect to each other


def tex(t):
    return "%s:block/%s" % (NS, t)


def block_id(name):
    return "%s:%s" % (NS, name)


def title(name):
    return " ".join(w[:1].upper() + w[1:] for w in name.split("_") if w)


def render_type(family):
    return "minecraft:cutout" if family in ("glass", "neon") else None


# --- per-kind emitters --------------------------------------------------------
def emit_cube(name, t, fam):
    files["assets/%s/blockstates/%s.json" % (NS, name)] = {"variants": {"": {"model": "%s:block/%s" % (NS, name)}}}
    # A full cube whose faces carry tintindex 0, so the DecorColorTintSource can paint it
    # (vanilla cube_all has no tintindex). NATURAL colour = white = the base texture unchanged.
    faces = {face: {"uv": [0, 0, 16, 16], "texture": "#all", "tintindex": 0, "cullface": face}
             for face in ("north", "east", "south", "west", "up", "down")}
    model = {"textures": {"particle": tex(t), "all": tex(t)},
             "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]}
    rt = render_type(fam)
    if rt:
        model["render_type"] = rt
    files["assets/%s/models/block/%s.json" % (NS, name)] = model
    files["assets/%s/models/item/%s.json" % (NS, name)] = {"parent": "%s:block/%s" % (NS, name)}
    loot_self(name)
    tag_common(name, fam)


def emit_slab(name, t, fam):
    cube = CUBE_OF[t]
    files["assets/%s/blockstates/%s.json" % (NS, name)] = {"variants": {
        "type=bottom": {"model": "%s:block/%s" % (NS, name)},
        "type=top": {"model": "%s:block/%s_top" % (NS, name)},
        "type=double": {"model": "%s:block/%s" % (NS, cube)},
    }}
    textures = {"bottom": tex(t), "top": tex(t), "side": tex(t)}
    files["assets/%s/models/block/%s.json" % (NS, name)] = {"parent": "minecraft:block/slab", "textures": textures}
    files["assets/%s/models/block/%s_top.json" % (NS, name)] = {"parent": "minecraft:block/slab_top", "textures": textures}
    files["assets/%s/models/item/%s.json" % (NS, name)] = {"parent": "%s:block/%s" % (NS, name)}
    files["data/%s/loot_table/blocks/%s.json" % (NS, name)] = slab_loot(name)
    tag_common(name, fam)


def emit_stairs(name, t, fam):
    files["assets/%s/blockstates/%s.json" % (NS, name)] = stairs_blockstate(name)
    textures = {"bottom": tex(t), "top": tex(t), "side": tex(t)}
    files["assets/%s/models/block/%s.json" % (NS, name)] = {"parent": "minecraft:block/stairs", "textures": textures}
    files["assets/%s/models/block/%s_inner.json" % (NS, name)] = {"parent": "minecraft:block/inner_stairs", "textures": textures}
    files["assets/%s/models/block/%s_outer.json" % (NS, name)] = {"parent": "minecraft:block/outer_stairs", "textures": textures}
    files["assets/%s/models/item/%s.json" % (NS, name)] = {"parent": "%s:block/%s" % (NS, name)}
    loot_self(name)
    tag_common(name, fam)


def emit_wall(name, t, fam):
    files["assets/%s/blockstates/%s.json" % (NS, name)] = wall_blockstate(name)
    textures = {"wall": tex(t)}
    files["assets/%s/models/block/%s_post.json" % (NS, name)] = {"parent": "minecraft:block/template_wall_post", "textures": textures}
    files["assets/%s/models/block/%s_side.json" % (NS, name)] = {"parent": "minecraft:block/template_wall_side", "textures": textures}
    files["assets/%s/models/block/%s_side_tall.json" % (NS, name)] = {"parent": "minecraft:block/template_wall_side_tall", "textures": textures}
    files["assets/%s/models/block/%s_inventory.json" % (NS, name)] = {"parent": "minecraft:block/wall_inventory", "textures": textures}
    files["assets/%s/models/item/%s.json" % (NS, name)] = {"parent": "%s:block/%s_inventory" % (NS, name)}
    loot_self(name)
    walls.append(block_id(name))
    tag_common(name, fam)


def emit_pane(name, t, fam):
    files["assets/%s/blockstates/%s.json" % (NS, name)] = pane_blockstate(name)
    textures = {"pane": tex(t), "edge": tex(t)}
    for suf, parent in (("_post", "post"), ("_side", "side"), ("_side_alt", "side_alt"),
                        ("_noside", "noside"), ("_noside_alt", "noside_alt")):
        files["assets/%s/models/block/%s%s.json" % (NS, name, suf)] = {
            "parent": "minecraft:block/template_glass_pane_%s" % parent, "textures": textures}
    files["assets/%s/models/item/%s.json" % (NS, name)] = {
        "parent": "minecraft:item/generated", "textures": {"layer0": tex(t)}}
    loot_self(name)
    tag_common(name, fam)


# --- shared helpers -----------------------------------------------------------
def loot_self(name):
    files["data/%s/loot_table/blocks/%s.json" % (NS, name)] = {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item", "name": block_id(name)}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    }


def slab_loot(name):
    return {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item",
            "functions": [{"function": "minecraft:set_count", "count": 2,
                "conditions": [{"condition": "minecraft:block_state_property", "block": block_id(name),
                    "properties": {"type": "double"}}]},
                {"function": "minecraft:explosion_decay"}],
            "name": block_id(name)}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    }


def tag_common(name, fam):
    mineable.append(block_id(name))
    decor_tags.setdefault(fam, []).append(block_id(name))
    lang["block.%s.%s" % (NS, name)] = title(name)
    lang["item.%s.%s" % (NS, name)] = title(name)


# --- standard vanilla blockstate templates ------------------------------------
def stairs_blockstate(name):
    m = "%s:block/%s" % (NS, name)
    mi = m + "_inner"
    mo = m + "_outer"
    v = {}

    def add(key, model, x=0, y=0):
        e = {"model": model}
        if x:
            e["x"] = x
        if y:
            e["y"] = y
        e["uvlock"] = True
        v[key] = e
    # bottom/top half x facing x shape — the canonical vanilla stair map
    facings = ["east", "west", "south", "north"]
    yrot = {"east": 0, "west": 180, "south": 90, "north": 270}
    for half, xrot in (("bottom", 0), ("top", 180)):
        for f in facings:
            base = yrot[f]
            add("facing=%s,half=%s,shape=straight" % (f, half), m, xrot, base % 360)
            add("facing=%s,half=%s,shape=outer_right" % (f, half), mo, xrot, base % 360)
            add("facing=%s,half=%s,shape=outer_left" % (f, half), mo, xrot, (base + 270) % 360)
            add("facing=%s,half=%s,shape=inner_right" % (f, half), mi, xrot, base % 360)
            add("facing=%s,half=%s,shape=inner_left" % (f, half), mi, xrot, (base + 270) % 360)
    return {"variants": v}


def wall_blockstate(name):
    post = "%s:block/%s_post" % (NS, name)
    side = "%s:block/%s_side" % (NS, name)
    tall = "%s:block/%s_side_tall" % (NS, name)
    parts = [{"when": {"up": "true"}, "apply": {"model": post}}]
    dirs = {"north": 0, "east": 90, "south": 180, "west": 270}
    for d, y in dirs.items():
        for prop, model in (("low", side), ("tall", tall)):
            a = {"model": model, "uvlock": True}
            if y:
                a["y"] = y
            parts.append({"when": {d: prop}, "apply": a})
    return {"multipart": parts}


def pane_blockstate(name):
    post = "%s:block/%s_post" % (NS, name)
    side = "%s:block/%s_side" % (NS, name)
    side_alt = "%s:block/%s_side_alt" % (NS, name)
    noside = "%s:block/%s_noside" % (NS, name)
    noside_alt = "%s:block/%s_noside_alt" % (NS, name)
    parts = [{"apply": {"model": post}}]
    parts.append({"when": {"north": "true"}, "apply": {"model": side}})
    parts.append({"when": {"east": "true"}, "apply": {"model": side, "y": 90}})
    parts.append({"when": {"south": "true"}, "apply": {"model": side_alt}})
    parts.append({"when": {"west": "true"}, "apply": {"model": side_alt, "y": 90}})
    parts.append({"when": {"north": "false"}, "apply": {"model": noside}})
    parts.append({"when": {"east": "false"}, "apply": {"model": noside, "y": 90}})
    parts.append({"when": {"south": "false"}, "apply": {"model": noside_alt}})
    parts.append({"when": {"west": "false"}, "apply": {"model": noside_alt, "y": 90}})
    return {"multipart": parts}


# --- recipes ------------------------------------------------------------------
STONECUT_COUNT = {"slab": 2, "stairs": 1, "wall": 1}


def emit_recipes():
    for name, kind, t, fam, mat in SPEC:
        if kind == "cube" and mat:
            files["data/%s/recipe/%s.json" % (NS, name)] = cube_recipe(name, fam, mat)
        elif kind in ("slab", "stairs", "wall", "pane"):
            files["data/%s/recipe/%s.json" % (NS, name)] = shape_recipe(name, CUBE_OF[t], kind)
            # Also a stonecutter route from the base cube (the decor-mod convenience).
            if kind in STONECUT_COUNT:
                files["data/%s/recipe/%s_from_stonecutting.json" % (NS, name)] = {
                    "type": "minecraft:stonecutting",
                    "ingredient": block_id(CUBE_OF[t]),
                    "result": {"id": block_id(name), "count": STONECUT_COUNT[kind]}}


def cube_recipe(name, fam, mat):
    # 26.x: an ingredient is the item-id STRING (or #tag), NOT {"item": ...} (removed in 1.21.2).
    if fam == "neon":  # glowstone dust + dye -> neon
        return {"type": "minecraft:crafting_shapeless",
                "ingredients": ["minecraft:glowstone_dust", "minecraft:%s_dye" % mat],
                "result": {"id": block_id(name), "count": 2}}
    if fam == "panel":  # 3-in-a-row -> 3 panels (distinct from hull's 2x2)
        return {"type": "minecraft:crafting_shaped", "pattern": ["MMM"],
                "key": {"M": mat}, "result": {"id": block_id(name), "count": 3}}
    # hull / glass: 2x2 -> 4
    return {"type": "minecraft:crafting_shaped", "pattern": ["MM", "MM"],
            "key": {"M": mat}, "result": {"id": block_id(name), "count": 4}}


def shape_recipe(name, cube, kind):
    src = block_id(cube)  # 26.x: ingredient is the item-id string, not {"item": ...}
    if kind == "slab":
        return {"type": "minecraft:crafting_shaped", "pattern": ["###"],
                "key": {"#": src}, "result": {"id": block_id(name), "count": 6}}
    if kind == "stairs":
        return {"type": "minecraft:crafting_shaped", "pattern": ["#  ", "## ", "###"],
                "key": {"#": src}, "result": {"id": block_id(name), "count": 4}}
    if kind == "wall":
        return {"type": "minecraft:crafting_shaped", "pattern": ["###", "###"],
                "key": {"#": src}, "result": {"id": block_id(name), "count": 6}}
    # pane
    return {"type": "minecraft:crafting_shaped", "pattern": ["###", "###"],
            "key": {"#": src}, "result": {"id": block_id(name), "count": 16}}


# --- Luminous collection (0.4.0) ------------------------------------------------
# Keep in LOCKSTEP with registry/LuminousBlocks.java and tools/gen_luminous.py (textures).
# kind: connected = cube with model-conditioned connected bezel (6 booleans, multipart)
#       plain     = cube with an emissive overlay
#       pillar    = axis pillar that links along its axis (pos_link / neg_link collars)
#       lamp      = fusion lamp (lit=false/true)
#       lslab     = slab of a connected cube (base + glow, no bezel)
# glow: the block has a <name>_glow emissive overlay; emit: light_emission for the whole base
# face (light-field blocks with no separate overlay).
LUMINOUS = [
    # name,                 kind,        glow,  emit
    ("circuit_plating",      "connected", True,  0),
    ("circuit_plating_slab", "lslab",     True,  0),
    ("holo_grid_floor",      "connected", True,  0),
    ("holo_grid_floor_slab", "lslab",     True,  0),
    ("starfield_panel",      "connected", True,  0),
    ("data_stream_panel",    "connected", True,  0),
    ("aurora_glass",         "connected", False, 9),
    ("lumen_panel",          "connected", False, 15),
    ("lumen_panel_slab",     "lslab",     False, 15),
    ("void_rift",            "plain",     True,  0),
    ("crystal_lattice",      "plain",     True,  0),
    ("capacitor_bank",       "plain",     True,  0),
    ("xeno_bloom",           "plain",     True,  0),
    ("ion_vent",             "plain",     True,  0),
    ("plasma_conduit",       "pillar",    True,  0),
    ("starsteel_pillar",     "pillar",    True,  0),
    ("fusion_lamp",          "lamp",      True,  0),
]
LUMINOUS_NAMES = {
    "circuit_plating": "Circuit Plating", "circuit_plating_slab": "Circuit Plating Slab",
    "holo_grid_floor": "Holo-Grid Floor", "holo_grid_floor_slab": "Holo-Grid Floor Slab",
    "starfield_panel": "Starfield Panel", "data_stream_panel": "Data Stream Panel",
    "aurora_glass": "Aurora Glass", "lumen_panel": "Lumen Panel", "lumen_panel_slab": "Lumen Panel Slab",
    "void_rift": "Void Rift", "crystal_lattice": "Void Crystal Lattice", "capacitor_bank": "Capacitor Bank",
    "xeno_bloom": "Xenobloom", "ion_vent": "Ion Vent", "plasma_conduit": "Plasma Conduit",
    "starsteel_pillar": "Starsteel Pillar", "fusion_lamp": "Fusion Lamp",
}
# sound event -> (files, subtitle)
LUMINOUS_SOUNDS = {
    "block.circuit.chirp": (["circuit_chirp1", "circuit_chirp2", "circuit_chirp3"], "Circuitry chirps"),
    "block.void_rift.hum": (["void_hum1", "void_hum2"], "Void rift hums"),
    "block.plasma_conduit.crackle": (["conduit_crackle1", "conduit_crackle2", "conduit_crackle3"], "Plasma crackles"),
    "block.crystal_lattice.chime": (["crystal_chime1", "crystal_chime2", "crystal_chime3"], "Crystal chimes"),
    "block.fusion_lamp.power_on": (["lamp_power_on"], "Fusion lamp ignites"),
    "block.fusion_lamp.power_off": (["lamp_power_off"], "Fusion lamp powers down"),
    "block.fusion_lamp.hum": (["lamp_hum"], "Fusion lamp hums"),
    "block.ion_vent.hiss": (["vent_hiss1", "vent_hiss2"], "Ion vent hisses"),
    "block.capacitor_bank.charge": (["capacitor_charge"], "Capacitor charges"),
}
FACES = ("north", "east", "south", "west", "up", "down")
# For each face, which world direction each TEXTURE edge touches (vanilla auto-UV with
# uv [0,0,16,16]): (top, bottom, left, right).
FACE_EDGES = {
    "north": ("up", "down", "east", "west"),
    "south": ("up", "down", "west", "east"),
    "west": ("up", "down", "north", "south"),
    "east": ("up", "down", "south", "north"),
    "up": ("north", "south", "west", "east"),
    "down": ("south", "north", "west", "east"),
}


def _el(frm, to, faces, texture, emit=0, uv=None, cull=True):
    """One element; `faces` = face names (or {face: texture})."""
    fs = {}
    for f in faces:
        t = faces[f] if isinstance(faces, dict) else texture
        face = {"texture": t}
        if uv:
            face["uv"] = uv
        if cull is True:
            face["cullface"] = f
        elif isinstance(cull, dict) and cull.get(f):
            face["cullface"] = cull[f]
        fs[f] = face
    e = {"from": frm, "to": to}
    if emit:
        e["shade"] = False
        e["light_emission"] = emit
    e["faces"] = fs
    return e


def _core_elements(glow, emit):
    els = [_el([0, 0, 0], [16, 16, 16], FACES, "#all", emit=emit, uv=[0, 0, 16, 16])]
    if glow:
        els.append(_el([0, 0, 0], [16, 16, 16], FACES, "#glow", emit=15, uv=[0, 0, 16, 16]))
    return els


def _model(textures, elements):
    return {"textures": textures, "elements": elements}


def _lum_tex(name, glow, base=None):
    base = base or name
    t = {"particle": tex(base), "all": tex(base)}
    if glow:
        t["glow"] = tex(base + "_glow")
    return t


def lum_connected(name, glow, emit):
    """Connected textures without any coplanar overlay: each face is its own multipart part, and
    the part is chosen by the face's four in-plane neighbours. The matching texture variant
    (<name>_f<mask>, from gen_luminous.py) has the bezel baked in on exactly the unconnected edges,
    and the glow variant is masked there, so nothing is drawn twice on the same plane."""
    bs = []
    for face in FACES:
        edges = FACE_EDGES[face]
        for mask in range(16):
            bits = (mask & 8, mask & 4, mask & 2, mask & 1)
            when = {d: ("false" if bit else "true") for d, bit in zip(edges, bits)}
            textures = {"particle": tex(name), "all": tex("%s_f%d" % (name, mask))}
            els = [_el([0, 0, 0], [16, 16, 16], [face], "#all", emit=emit, uv=[0, 0, 16, 16])]
            if glow:
                textures["glow"] = tex("%s_glow_f%d" % (name, mask))
                els.append(_el([0, 0, 0], [16, 16, 16], [face], "#glow", emit=15, uv=[0, 0, 16, 16]))
            model = "%s_%s_f%d" % (name, face, mask)
            files["assets/%s/models/block/%s.json" % (NS, model)] = _model(textures, els)
            bs.append({"when": when, "apply": {"model": "%s:block/%s" % (NS, model)}})
    files["assets/%s/blockstates/%s.json" % (NS, name)] = {"multipart": bs}
    # inventory / double-slab look: every edge framed
    inv_tex = {"particle": tex(name), "all": tex(name + "_f15")}
    if glow:
        inv_tex["glow"] = tex(name + "_glow_f15")
    files["assets/%s/models/block/%s_inventory.json" % (NS, name)] = _model(inv_tex, _core_elements(glow, emit))
    files["assets/%s/models/item/%s.json" % (NS, name)] = {"parent": "%s:block/%s_inventory" % (NS, name)}


def lum_plain(name, glow, emit):
    files["assets/%s/blockstates/%s.json" % (NS, name)] = {"variants": {"": {"model": "%s:block/%s" % (NS, name)}}}
    files["assets/%s/models/block/%s.json" % (NS, name)] = _model(_lum_tex(name, glow), _core_elements(glow, emit))
    files["assets/%s/models/item/%s.json" % (NS, name)] = {"parent": "%s:block/%s" % (NS, name)}


def lum_slab(name, glow, emit):
    cube = name[:-len("_slab")]
    textures = _lum_tex(cube, glow)
    for suffix, frm, to, open_face in (("", [0, 0, 0], [16, 8, 16], "up"), ("_top", [0, 8, 0], [16, 16, 16], "down")):
        cull = {f: f for f in FACES if f != open_face}
        els = [_el(frm, to, FACES, "#all", emit=emit, cull=cull)]
        if glow:
            els.append(_el(frm, to, FACES, "#glow", emit=15, cull=cull))
        files["assets/%s/models/block/%s%s.json" % (NS, name, suffix)] = _model(textures, els)
    files["assets/%s/blockstates/%s.json" % (NS, name)] = {"variants": {
        "type=bottom": {"model": "%s:block/%s" % (NS, name)},
        "type=top": {"model": "%s:block/%s_top" % (NS, name)},
        "type=double": {"model": "%s:block/%s_inventory" % (NS, cube)},
    }}
    files["assets/%s/models/item/%s.json" % (NS, name)] = {"parent": "%s:block/%s" % (NS, name)}


# Pillars: per axis, the texture for each side face given which ends carry a collar
# (cp = collar at the positive end, cn = at the negative end). Side textures come in
# side_v_c<top><bottom> and side_h_c<left><right>; vanilla auto-UV decides which texture edge
# faces the positive end, so the variant index is swapped per face where needed.
def _pillar_side(axis, face, cp, cn):
    if axis == "y":
        return "side_v", (cp, cn)            # top = +y
    if axis == "x":
        if face == "north":
            return "side_h", (cp, cn)        # u = 16 - x: left = +x
        return "side_h", (cn, cp)            # south/up/down: right = +x
    # axis z
    if face == "east":
        return "side_h", (cp, cn)            # u = 16 - z: left = +z
    if face == "west":
        return "side_h", (cn, cp)            # right = +z
    if face == "up":
        return "side_v", (cn, cp)            # v = z: bottom = +z
    return "side_v", (cp, cn)                # down: v = 16 - z, top = +z


PILLAR_AXES = {
    "y": (("north", "south", "east", "west"), ("up", "down")),
    "x": (("north", "south", "up", "down"), ("east", "west")),
    "z": (("east", "west", "up", "down"), ("north", "south")),
}


def lum_pillar(name, glow, emit):
    """Collars are baked into side-texture variants (no coplanar cap layer); the blockstate picks
    one model per axis x link combination."""
    variants = {}
    for axis, (sides, ends) in PILLAR_AXES.items():
        for pos_link in (False, True):
            for neg_link in (False, True):
                cp, cn = int(not pos_link), int(not neg_link)
                textures = {"particle": tex(name + "_end"), "end": tex(name + "_end")}
                faces, gfaces = {}, {}
                for f in sides:
                    kind, (a, b) = _pillar_side(axis, f, cp, cn)
                    var = "%s_c%d%d" % (kind, a, b)
                    textures[var] = tex("%s_%s" % (name, var))
                    faces[f] = "#" + var
                    if glow:
                        gvar = "%s_glow_c%d%d" % (kind, a, b)
                        textures[gvar] = tex("%s_%s" % (name, gvar))
                        gfaces[f] = "#" + gvar
                for f in ends:
                    faces[f] = "#end"
                    if glow:
                        textures["end_glow"] = tex(name + "_end_glow")
                        gfaces[f] = "#end_glow"
                els = [_el([0, 0, 0], [16, 16, 16], faces, None, uv=[0, 0, 16, 16])]
                if glow:
                    els.append(_el([0, 0, 0], [16, 16, 16], gfaces, None, emit=15, uv=[0, 0, 16, 16]))
                model = "%s_%s_c%d%d" % (name, axis, cp, cn)
                files["assets/%s/models/block/%s.json" % (NS, model)] = _model(textures, els)
                key = "axis=%s,neg_link=%s,pos_link=%s" % (axis, str(neg_link).lower(), str(pos_link).lower())
                variants[key] = {"model": "%s:block/%s" % (NS, model)}
    files["assets/%s/blockstates/%s.json" % (NS, name)] = {"variants": variants}
    files["assets/%s/models/item/%s.json" % (NS, name)] = {"parent": "%s:block/%s_y_c11" % (NS, name)}


def lum_lamp(name, glow, emit):
    files["assets/%s/models/block/%s.json" % (NS, name)] = _model(
        {"particle": tex(name), "all": tex(name)}, _core_elements(False, 0))
    files["assets/%s/models/block/%s_on.json" % (NS, name)] = _model(
        {"particle": tex(name + "_on"), "all": tex(name + "_on"), "glow": tex(name + "_on_glow")},
        _core_elements(True, 0))
    files["assets/%s/blockstates/%s.json" % (NS, name)] = {"variants": {
        "lit=false": {"model": "%s:block/%s" % (NS, name)},
        "lit=true": {"model": "%s:block/%s_on" % (NS, name)}}}
    # the inventory icon shows the lamp lit — what it is for
    files["assets/%s/models/item/%s.json" % (NS, name)] = {"parent": "%s:block/%s_on" % (NS, name)}


def _shaped(pattern, key, result, count):
    return {"type": "minecraft:crafting_shaped", "pattern": pattern, "key": key,
            "result": {"id": block_id(result), "count": count}}


def _shapeless(ingredients, result, count):
    return {"type": "minecraft:crafting_shapeless", "ingredients": ingredients,
            "result": {"id": block_id(result), "count": count}}


def _ring(outer, centre, result, count):
    return _shaped(["OOO", "OCO", "OOO"], {"O": outer, "C": centre}, result, count)


# Cheap on purpose (ADR-001: decoration is expression, not a tax): mostly 8 of an existing
# NeroDecor block around one accent ingredient. Vanilla + Core items only, no gates.
LUMINOUS_RECIPES = {
    "circuit_plating": _ring("nerodecor:hull_nero_alloy", "minecraft:redstone", "circuit_plating", 8),
    "holo_grid_floor": _ring("nerodecor:panel_starsteel", "minecraft:prismarine_crystals", "holo_grid_floor", 8),
    "starfield_panel": _ring("nerodecor:hull_void_crystal", "minecraft:glowstone_dust", "starfield_panel", 8),
    "data_stream_panel": _ring("nerodecor:panel_nero_alloy", "minecraft:lapis_lazuli", "data_stream_panel", 8),
    "aurora_glass": _ring("nerodecor:glass_plasma_glass", "minecraft:amethyst_shard", "aurora_glass", 8),
    "lumen_panel": _ring("minecraft:glass", "minecraft:glowstone", "lumen_panel", 8),
    "void_rift": _shapeless(["nerolandcore:void_crystal_shard", "nerolandcore:void_crystal_shard",
                             "minecraft:ender_pearl", "minecraft:obsidian"], "void_rift", 2),
    "crystal_lattice": _shaped(["VA", "AV"], {"V": "nerolandcore:void_crystal_shard",
                                              "A": "minecraft:amethyst_shard"}, "crystal_lattice", 4),
    "capacitor_bank": _shaped(["SRS", "SGS", "SRS"], {"S": "nerodecor:panel_starsteel", "R": "minecraft:redstone",
                                                      "G": "nerolandcore:plasma_glass_block"}, "capacitor_bank", 2),
    "xeno_bloom": _shapeless(["minecraft:cobbled_deepslate", "minecraft:cobbled_deepslate",
                              "minecraft:cobbled_deepslate", "minecraft:cobbled_deepslate",
                              "minecraft:glow_berries"], "xeno_bloom", 4),
    "ion_vent": _shaped(["B B", "BPB", "B B"], {"B": "minecraft:iron_bars", "P": "nerodecor:panel_nero_alloy"},
                        "ion_vent", 4),
    "plasma_conduit": _shaped(["S", "G", "S"], {"S": "nerolandcore:starsteel_ingot",
                                               "G": "nerolandcore:plasma_glass_block"}, "plasma_conduit", 4),
    "starsteel_pillar": _shaped(["H", "H"], {"H": "nerodecor:hull_starsteel"}, "starsteel_pillar", 2),
    "fusion_lamp": _shaped([" S ", "SLS", " S "], {"S": "nerodecor:panel_starsteel", "L": "minecraft:redstone_lamp"},
                           "fusion_lamp", 1),
}


def emit_luminous():
    emitters = {"connected": lum_connected, "plain": lum_plain, "pillar": lum_pillar,
                "lamp": lum_lamp, "lslab": lum_slab}
    for name, kind, glow, emit in LUMINOUS:
        emitters[kind](name, glow, emit)
        files["assets/%s/items/%s.json" % (NS, name)] = {
            "model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}}
        if kind == "lslab":
            cube = name[:-len("_slab")]
            files["data/%s/loot_table/blocks/%s.json" % (NS, name)] = slab_loot(name)
            files["data/%s/recipe/%s.json" % (NS, name)] = shape_recipe(name, cube, "slab")
            files["data/%s/recipe/%s_from_stonecutting.json" % (NS, name)] = {
                "type": "minecraft:stonecutting", "ingredient": block_id(cube),
                "result": {"id": block_id(name), "count": 2}}
        else:
            loot_self(name)
            files["data/%s/recipe/%s.json" % (NS, name)] = LUMINOUS_RECIPES[name]
        mineable.append(block_id(name))
        decor_tags.setdefault("luminous", []).append(block_id(name))
        lang["block.%s.%s" % (NS, name)] = LUMINOUS_NAMES[name]
        lang["item.%s.%s" % (NS, name)] = LUMINOUS_NAMES[name]
    files["data/%s/recipe/starsteel_pillar_from_stonecutting.json" % NS] = {
        "type": "minecraft:stonecutting", "ingredient": block_id("hull_starsteel"),
        "result": {"id": block_id("starsteel_pillar"), "count": 1}}
    sounds = {}
    for event, (names, subtitle) in sorted(LUMINOUS_SOUNDS.items()):
        key = "subtitles.%s.%s" % (NS, event)
        sounds[event] = {"subtitle": key, "sounds": ["%s:block/%s" % (NS, n) for n in names]}
        lang[key] = subtitle
    files["assets/%s/sounds.json" % NS] = sounds


# --- drive --------------------------------------------------------------------
def main():
    emit = {"cube": emit_cube, "slab": emit_slab, "stairs": emit_stairs, "wall": emit_wall, "pane": emit_pane}
    for name, kind, t, fam, _mat in SPEC:
        emit[kind](name, t, fam)
        # 1.21.4+/26.x: every item needs a client-item file at assets/<ns>/items/<id>.json
        # pointing at its model, or the item icon renders as the missing texture.
        files["assets/%s/items/%s.json" % (NS, name)] = {
            "model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}}
    emit_recipes()
    emit_luminous()

    # aggregate tags
    files["data/minecraft/tags/block/mineable/pickaxe.json"] = {"replace": False, "values": sorted(mineable)}
    # Walls only connect to neighbours in #minecraft:walls (vanilla WallBlock.connectsTo).
    if walls:
        files["data/minecraft/tags/block/walls.json"] = {"replace": False, "values": sorted(walls)}
    for fam, ids in decor_tags.items():
        files["data/neroland/tags/block/decor/%s.json" % fam] = {"replace": False,
            "values": [{"id": i, "required": False} for i in sorted(ids)]}

    # lang
    files["assets/%s/lang/en_us.json" % NS] = dict(sorted(lang.items()))

    n = 0
    for rel, obj in sorted(files.items()):
        path = os.path.join(RES, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(obj, fh, indent=2, sort_keys=False)
            fh.write("\n")
        n += 1
    print("gen_resources: wrote %d JSON files for %d blocks" % (n, len(SPEC) + len(LUMINOUS)))


if __name__ == "__main__":
    main()
