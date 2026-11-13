#!/usr/bin/env python3
"""Writes the empty 24x24x24 'chroma_arena' structure the Chroma GameTests run in.

The arena has no blocks: it only reserves a test area big enough for a hub and a 16x16x16 block of linked cubes, so
neighbouring tests in the GameTest grid never overlap it. Deterministic: same output every run.

    python neoforge/src/gametest/gen_chroma_arena.py [--check]
"""
import gzip
import struct
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "resources/data/nerodecor_gametest/structure/chroma_arena.nbt"
DATA_VERSION = 5023  # Minecraft 26.3
SIZE = (24, 24, 24)


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


def build():
    air = compound(named(8, "id", s("minecraft:air")))
    root = named(10, "", compound(
        named(3, "DataVersion", i32(DATA_VERSION)),
        named(9, "size", lst(3, [i32(v) for v in SIZE])),
        named(9, "palette", lst(10, [air])),
        named(9, "blocks", lst(0, [])),
        named(9, "entities", lst(0, [])),
    ))
    return gzip.compress(root, mtime=0)


def main():
    data = build()
    if "--check" in sys.argv:
        ok = OUT.exists() and gzip.decompress(OUT.read_bytes()) == gzip.decompress(data)
        print("gen_chroma_arena --check: " + ("clean" if ok else "DRIFT"))
        sys.exit(0 if ok else 1)
    OUT.write_bytes(data)
    print("wrote " + str(OUT.relative_to(HERE)))


if __name__ == "__main__":
    main()
