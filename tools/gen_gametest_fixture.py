#!/usr/bin/env python3
"""Writes the NeroDecor 0.7.1 fixture structure used by the 26.3 NeoForge GameTests.

Every block registered in 0.7.1 (the frozen IDS_071 list) is placed on a grid, and three chests hold one of each block item. Stage 3 asserts the neon cubes in it come back as
same-colour chroma cubes, placed and in the chests. Deterministic: same input, byte-identical output.

    python tools/gen_gametest_fixture.py [--check]
"""
import gzip
import json
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "neoforge/src/gametest/resources/data/nerodecor_gametest/structure/fixture_071.nbt"
MANIFEST = ROOT / "neoforge/src/gametest/resources/data/nerodecor_gametest/fixture_071_ids.json"
DATA_VERSION = 5023  # Minecraft 26.3
COLS = 10            # blocks per row (x)
SIZE = (12, 3, 10)   # x, y, z


def s(x):
    b = x.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def named(t, name, payload):
    return bytes([t]) + s(name) + payload


def i32(v):
    return struct.pack(">i", v)


def lst(t, items):
    return bytes([t]) + i32(len(items)) + b"".join(items)


def compound(*fields):
    return b"".join(fields) + b"\x00"


# The 59 ids registered in 0.7.1-beta.2, frozen: this is a save from that release and must not grow.
IDS_071 = (
    "aurora_glass", "capacitor_bank", "circuit_plating", "circuit_plating_slab", "crystal_lattice",
    "data_stream_panel", "fusion_lamp", "glass_cyan", "glass_cyan_pane", "glass_cyan_slab",
    "glass_light_blue", "glass_light_blue_pane", "glass_light_blue_slab", "glass_plasma_glass",
    "glass_plasma_glass_pane", "glass_plasma_glass_slab", "holo_grid_floor", "holo_grid_floor_slab",
    "hull_nero_alloy", "hull_nero_alloy_slab", "hull_nero_alloy_stairs", "hull_nero_alloy_wall",
    "hull_starsteel", "hull_starsteel_slab", "hull_starsteel_stairs", "hull_starsteel_wall",
    "hull_void_crystal", "hull_void_crystal_slab", "hull_void_crystal_stairs", "hull_void_crystal_wall",
    "ion_vent", "lumen_panel", "lumen_panel_slab", "neon_blue", "neon_cyan", "neon_green", "neon_light_blue",
    "neon_lime", "neon_magenta", "neon_orange", "neon_pink", "neon_purple", "neon_red", "neon_white",
    "neon_yellow", "panel_nero_alloy", "panel_nero_alloy_slab", "panel_nero_alloy_stairs", "panel_starsteel",
    "panel_starsteel_slab", "panel_starsteel_stairs", "panel_void_crystal", "panel_void_crystal_slab",
    "panel_void_crystal_stairs", "plasma_conduit", "starfield_panel", "starsteel_pillar", "void_rift",
    "xeno_bloom",
)


def block_ids():
    assert len(IDS_071) == 59
    return list(IDS_071)


def build():
    ids = block_ids()
    palette = ["minecraft:air", "minecraft:chest"] + ["nerodecor:" + i for i in ids]
    blocks = []
    for n, _ in enumerate(ids):
        x, z = 1 + n % COLS, 1 + n // COLS
        blocks.append(compound(named(9, "pos", lst(3, [i32(x), i32(1), i32(z)])), named(3, "state", i32(n + 2))))
    # Three chests at z = 8, one stack of each block item.
    for c in range(3):
        items = []
        for slot, bid in enumerate(ids[c * 27:(c + 1) * 27]):
            items.append(compound(named(1, "Slot", bytes([slot])), named(8, "id", s("nerodecor:" + bid)),
                                  named(3, "count", i32(1))))
        nbt = compound(named(8, "id", s("minecraft:chest")), named(9, "Items", lst(10, items) if items else lst(0, [])))
        blocks.append(compound(named(9, "pos", lst(3, [i32(1 + c * 2), i32(1), i32(8)])),
                               named(3, "state", i32(1)), named(10, "nbt", nbt)))
    root = compound(
        named(3, "DataVersion", i32(DATA_VERSION)),
        named(9, "size", lst(3, [i32(v) for v in SIZE])),
        # 26.3 reads palette entries as {id, properties}; older versions used {Name, Properties}.
        named(9, "palette", lst(10, [compound(named(8, "id", s(p))) for p in palette])),
        named(9, "blocks", lst(10, blocks)),
        named(9, "entities", lst(0, [])),
    )
    data = gzip.compress(named(10, "", root), mtime=0)
    manifest = json.dumps({"_comment": "0.7.1 fixture: block ids in placement order (x = 1 + n % 10, z = 1 + n // 10, y = 1); chests at (1,1,8), (3,1,8), (5,1,8)",
                           "ids": ids}, indent=2) + "\n"
    return data, manifest


def main():
    data, manifest = build()
    if "--check" in sys.argv:
        ok = OUT.exists() and OUT.read_bytes() == data and MANIFEST.read_text(encoding="utf-8") == manifest
        print("fixture_071: " + ("clean" if ok else "DRIFT"))
        return 0 if ok else 1
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(data)
    MANIFEST.write_text(manifest, encoding="utf-8", newline="\n")
    print("fixture_071: wrote " + str(len(json.loads(manifest)["ids"])) + " blocks")
    return 0


if __name__ == "__main__":
    sys.exit(main())
