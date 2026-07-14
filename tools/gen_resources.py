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
SPEC = [
    ("hull_nero_alloy",        "cube",  "hull_nero_alloy",  "hull",  "nerolandcore:nero_alloy_ingot"),
    ("hull_nero_alloy_slab",   "slab",  "hull_nero_alloy",  "hull",  None),
    ("hull_nero_alloy_stairs", "stairs","hull_nero_alloy",  "hull",  None),
    ("hull_nero_alloy_wall",   "wall",  "hull_nero_alloy",  "hull",  None),
    ("hull_starsteel",         "cube",  "hull_starsteel",   "hull",  "nerolandcore:starsteel_ingot"),
    ("hull_starsteel_slab",    "slab",  "hull_starsteel",   "hull",  None),
    ("hull_starsteel_stairs",  "stairs","hull_starsteel",   "hull",  None),
    ("hull_starsteel_wall",    "wall",  "hull_starsteel",   "hull",  None),
    ("panel_nero_alloy",       "cube",  "panel_nero_alloy", "panel", "nerolandcore:nero_alloy_ingot"),
    ("panel_nero_alloy_slab",  "slab",  "panel_nero_alloy", "panel", None),
    ("panel_nero_alloy_stairs","stairs","panel_nero_alloy", "panel", None),
    ("glass_plasma_glass",     "cube",  "glass_plasma_glass","glass", "nerolandcore:plasma_glass_block"),
    ("glass_plasma_glass_pane","pane",  "glass_plasma_glass","glass", None),
    ("glass_plasma_glass_slab","slab",  "glass_plasma_glass","glass", None),
    ("neon_red",               "cube",  "neon_red",         "neon",  "red"),
    ("neon_cyan",              "cube",  "neon_cyan",        "neon",  "cyan"),
]

CUBE_OF = {"hull_nero_alloy": "hull_nero_alloy", "hull_starsteel": "hull_starsteel",
           "panel_nero_alloy": "panel_nero_alloy", "glass_plasma_glass": "glass_plasma_glass",
           "neon_red": "neon_red", "neon_cyan": "neon_cyan"}

files = {}          # relative path -> dict (written as JSON)
lang = {}
mineable = []
decor_tags = {}     # family -> [block ids]


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
    model = {"parent": "minecraft:block/cube_all", "textures": {"all": tex(t)}}
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
def emit_recipes():
    for name, kind, t, fam, mat in SPEC:
        if kind == "cube" and mat:
            files["data/%s/recipe/%s.json" % (NS, name)] = cube_recipe(name, fam, mat)
        elif kind == "slab":
            files["data/%s/recipe/%s.json" % (NS, name)] = shape_recipe(name, CUBE_OF[t], "slab")
        elif kind == "stairs":
            files["data/%s/recipe/%s.json" % (NS, name)] = shape_recipe(name, CUBE_OF[t], "stairs")
        elif kind == "wall":
            files["data/%s/recipe/%s.json" % (NS, name)] = shape_recipe(name, CUBE_OF[t], "wall")
        elif kind == "pane":
            files["data/%s/recipe/%s.json" % (NS, name)] = shape_recipe(name, CUBE_OF[t], "pane")


def cube_recipe(name, fam, mat):
    if fam == "neon":  # glowstone dust + dye -> neon
        return {"type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": "minecraft:glowstone_dust"}, {"item": "minecraft:%s_dye" % mat}],
                "result": {"id": block_id(name), "count": 2}}
    if fam == "panel":  # 3-in-a-row -> 3 panels (distinct from hull's 2x2)
        return {"type": "minecraft:crafting_shaped", "pattern": ["MMM"],
                "key": {"M": {"item": mat}}, "result": {"id": block_id(name), "count": 3}}
    # hull / glass: 2x2 -> 4
    return {"type": "minecraft:crafting_shaped", "pattern": ["MM", "MM"],
            "key": {"M": {"item": mat}}, "result": {"id": block_id(name), "count": 4}}


def shape_recipe(name, cube, kind):
    src = block_id(cube)
    if kind == "slab":
        return {"type": "minecraft:crafting_shaped", "pattern": ["###"],
                "key": {"#": {"item": src}}, "result": {"id": block_id(name), "count": 6}}
    if kind == "stairs":
        return {"type": "minecraft:crafting_shaped", "pattern": ["#  ", "## ", "###"],
                "key": {"#": {"item": src}}, "result": {"id": block_id(name), "count": 4}}
    if kind == "wall":
        return {"type": "minecraft:crafting_shaped", "pattern": ["###", "###"],
                "key": {"#": {"item": src}}, "result": {"id": block_id(name), "count": 6}}
    # pane
    return {"type": "minecraft:crafting_shaped", "pattern": ["###", "###"],
            "key": {"#": {"item": src}}, "result": {"id": block_id(name), "count": 16}}


# --- drive --------------------------------------------------------------------
def main():
    emit = {"cube": emit_cube, "slab": emit_slab, "stairs": emit_stairs, "wall": emit_wall, "pane": emit_pane}
    for name, kind, t, fam, _mat in SPEC:
        emit[kind](name, t, fam)
    emit_recipes()

    # aggregate tags
    files["data/minecraft/tags/block/mineable/pickaxe.json"] = {"replace": False, "values": sorted(mineable)}
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
    print("gen_resources: wrote %d JSON files for %d blocks" % (n, len(SPEC)))


if __name__ == "__main__":
    main()
