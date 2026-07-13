#!/usr/bin/env python3
"""
NeroDecor procedural texture pipeline — reproducible, finely-editable, idempotent.

Reads the palette manifest (tools/palette.json, mirroring Neroland Core's exported
PaletteRegistry) and composites every block texture from per-family layer templates. For
each (family x finish) it emits, into common/src/main/resources/assets/nerodecor/textures:

  block/<family>_<finish>.png            base (interior FILL) tile
  block/<family>_<finish>_ctm.png        2x2 CTM atlas [FILL, EDGE, OUTER_CORNER, INNER_CORNER]
  block/<family>_<finish>_emissive.png   separate fullbright glow mask (emissive families only)
  item/<family>_<finish>.png             item icon

Design guarantees (Stage D2):
  * Deterministic — no RNG, no timestamps; any detail comes from a hash of (family,finish,x,y),
    so re-running produces BYTE-IDENTICAL PNGs (verify: run twice, diff sha256).
  * Palette-driven — colours come only from palette.json (kept in lockstep with CoreFinishes).
  * Guarded — a checksum manifest (tools/gen_textures.manifest.json) records every output's
    sha256; --check reports drift and writes nothing; orphan outputs (on disk but not in the
    current spec) fail loudly unless --prune.

Usage:
  python tools/gen_textures.py [--multiloader] [--check] [--prune] [--force]

The CTM atlas layout matches za...client.ctm.CtmPiece; see nerodecor/MODELS.md to add a
family or finish.
"""
import argparse
import hashlib
import io
import json
import os
import sys

try:
    from PIL import Image
except ModuleNotFoundError:
    print("gen_textures: Pillow not installed; skipping (pip install pillow).")
    sys.exit(0)

S = 16  # tile size
HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
RES = os.path.join(REPO, "common", "src", "main", "resources", "assets", "nerodecor", "textures")
BLOCK_DIR = os.path.join(RES, "block")
ITEM_DIR = os.path.join(RES, "item")
MANIFEST = os.path.join(HERE, "gen_textures.manifest.json")

# --- Spec: which families use which finishes. Grow this as blocks are added (Stages E-H). ---
# kind: "material" finishes are opaque; "glass" is translucent; emissive families also emit a mask.
SPEC = {
    "hull":  {"finishes": ["nero_alloy", "starsteel"], "emissive": False, "translucent": False},
    "panel": {"finishes": ["nero_alloy"],              "emissive": False, "translucent": False},
    "neon":  {"finishes": ["red", "cyan"],             "emissive": True,  "translucent": False},
    "glass": {"finishes": ["plasma_glass"],            "emissive": True,  "translucent": True},
}


def det(*parts):
    """Deterministic float in [0,1) from the given parts — the only source of 'noise'."""
    h = hashlib.sha256("|".join(str(p) for p in parts).encode()).digest()
    return int.from_bytes(h[:4], "big") / 0xFFFFFFFF


def hex_rgb(s):
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16))


def shade(rgb, f):
    return tuple(max(0, min(255, round(c * f))) for c in rgb)


def load_palette():
    with open(os.path.join(HERE, "palette.json"), encoding="utf-8") as fh:
        data = json.load(fh)
    finishes = {}
    for name, spec in data["materials"].items():
        finishes[name] = {"rgb": hex_rgb(spec["rgb"]), "emissive": spec["emissive"], "light": spec["light"]}
    for name, spec in data["accents"].items():
        finishes[name] = {"rgb": hex_rgb(spec["rgb"]), "emissive": False, "light": 0}
    return finishes


# --- Layer painters -----------------------------------------------------------------
def _plate(base, family, finish, borders):
    """A 16x16 plate of `base`, optional inset border on the given sides {t,b,l,r}, and family detail."""
    dark = shade(base, 0.62)
    mid = shade(base, 0.85)
    hi = shade(base, 1.18)
    img = Image.new("RGBA", (S, S), base + (255,))
    px = img.load()
    # subtle deterministic dither so large surfaces aren't dead-flat
    for y in range(S):
        for x in range(S):
            if det(family, finish, x, y) > 0.90:
                px[x, y] = mid + (255,)
    if family == "hull":
        # corner rivets + a horizontal seam
        for (rx, ry) in [(2, 2), (S - 3, 2), (2, S - 3), (S - 3, S - 3)]:
            px[rx, ry] = dark + (255,)
            px[rx - 1, ry - 1] = hi + (255,)
        for x in range(S):
            px[x, S // 2] = shade(base, 0.72) + (255,)
    elif family == "panel":
        # vent slats
        for sy in (4, 8, 12):
            for x in range(3, S - 3):
                px[x, sy] = dark + (255,)
                px[x, sy - 1] = hi + (255,)
    elif family == "neon":
        # dark housing with a bright core bar (the glow lives in the emissive mask)
        for y in range(S):
            for x in range(S):
                px[x, y] = shade(base, 0.28) + (255,)
        for y in range(6, 10):
            for x in range(S):
                px[x, y] = hi + (255,)
    elif family == "glass":
        for y in range(S):
            for x in range(S):
                px[x, y] = mid + (70,)
    # borders (CTM edge/corner drawing)
    edge = shade(base, 0.5) + (255 if family != "glass" else 190,)
    if "t" in borders:
        for x in range(S):
            px[x, 0] = edge
    if "b" in borders:
        for x in range(S):
            px[x, S - 1] = edge
    if "l" in borders:
        for y in range(S):
            px[0, y] = edge
    if "r" in borders:
        for y in range(S):
            px[S - 1, y] = edge
    return img


def base_tile(base, family, finish):
    return _plate(base, family, finish, set())


def ctm_atlas(base, family, finish):
    """2x2 atlas: [FILL | EDGE(top)] / [OUTER(top+left) | INNER(notch top-left)]."""
    atlas = Image.new("RGBA", (S * 2, S * 2), (0, 0, 0, 0))
    fill = _plate(base, family, finish, set())
    edge = _plate(base, family, finish, {"t"})
    outer = _plate(base, family, finish, {"t", "l"})
    inner = _plate(base, family, finish, set())
    ip = inner.load()
    notch = shade(base, 0.5) + (255 if family != "glass" else 190,)
    ip[0, 0] = notch
    ip[1, 0] = notch
    ip[0, 1] = notch
    atlas.paste(fill, (0, 0))
    atlas.paste(edge, (S, 0))
    atlas.paste(outer, (0, S))
    atlas.paste(inner, (S, S))
    return atlas


def emissive_mask(base, family, finish):
    """Additive glow layer: rgb = finish colour, alpha = glow intensity where it should bloom."""
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    glow = shade(base, 1.4)
    if family == "neon":
        for y in range(6, 10):
            for x in range(S):
                px[x, y] = glow + (255,)
    else:  # glass / crystalline — faint whole-surface bloom
        for y in range(S):
            for x in range(S):
                px[x, y] = glow + (60,)
    return img


def item_icon(base, family, finish):
    """A small centred chip of the base tile as the item icon."""
    icon = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    tile = base_tile(base, family, finish)
    chip = tile.crop((2, 2, S - 2, S - 2))
    icon.paste(chip, (2, 2))
    # outline
    px = icon.load()
    line = shade(base, 0.4) + (255,)
    for i in range(1, S - 1):
        px[i, 1] = line
        px[i, S - 2] = line
        px[1, i] = line
        px[S - 2, i] = line
    return icon


# --- Emit / manifest ----------------------------------------------------------------
def png_bytes(img):
    buf = io.BytesIO()
    # No pnginfo, no optimize pass -> deterministic, metadata-free bytes.
    img.save(buf, format="PNG")
    return buf.getvalue()


def expected_outputs(palette):
    """Ordered map of relative-path -> PNG bytes for the whole current spec."""
    out = {}
    for family in sorted(SPEC):
        spec = SPEC[family]
        for finish in sorted(spec["finishes"]):
            if finish not in palette:
                raise SystemExit("gen_textures: finish '%s' (family %s) not in palette.json" % (finish, family))
            base = palette[finish]["rgb"]
            key = "%s_%s" % (family, finish)
            out["block/%s.png" % key] = png_bytes(base_tile(base, family, finish))
            out["block/%s_ctm.png" % key] = png_bytes(ctm_atlas(base, family, finish))
            out["item/%s.png" % key] = png_bytes(item_icon(base, family, finish))
            if spec["emissive"]:
                out["block/%s_emissive.png" % key] = png_bytes(emissive_mask(base, family, finish))
    return out


def scan_existing():
    found = set()
    for d, prefix in ((BLOCK_DIR, "block"), (ITEM_DIR, "item")):
        if os.path.isdir(d):
            for fn in os.listdir(d):
                if fn.endswith(".png"):
                    found.add("%s/%s" % (prefix, fn))
    return found


def main():
    ap = argparse.ArgumentParser(description="NeroDecor reproducible texture generator.")
    ap.add_argument("--multiloader", action="store_true", help="target the flattened common module (default)")
    ap.add_argument("--check", action="store_true", help="report drift vs. disk/manifest; write nothing; nonzero exit on drift")
    ap.add_argument("--prune", action="store_true", help="delete orphan outputs no longer in the spec")
    ap.add_argument("--force", action="store_true", help="rewrite even unchanged outputs")
    args = ap.parse_args()

    palette = load_palette()
    expected = expected_outputs(palette)
    expected_paths = set(expected)
    on_disk = scan_existing()
    # Only our own generated files are candidates for orphan detection (block/<f>_<x>*.png, item/<f>_<x>.png).
    gen_families = tuple(sorted(SPEC))
    ours = {p for p in on_disk if os.path.basename(p).startswith(gen_families)}
    orphans = sorted(ours - expected_paths)

    manifest = {}
    if os.path.exists(MANIFEST):
        with open(MANIFEST, encoding="utf-8") as fh:
            manifest = json.load(fh).get("outputs", {})

    changed, created = [], []
    for rel in sorted(expected):
        data = expected[rel]
        digest = hashlib.sha256(data).hexdigest()
        abspath = os.path.join(RES, rel)
        disk_ok = os.path.exists(abspath) and hashlib.sha256(open(abspath, "rb").read()).hexdigest() == digest
        if not os.path.exists(abspath):
            created.append(rel)
        elif not disk_ok or manifest.get(rel) != digest:
            changed.append(rel)

    if args.check:
        drift = created + changed + orphans
        if drift:
            print("gen_textures --check: DRIFT")
            for r in created:
                print("  new/missing: %s" % r)
            for r in changed:
                print("  stale (source changed): %s" % r)
            for r in orphans:
                print("  orphan (not in spec): %s" % r)
            sys.exit(1)
        print("gen_textures --check: clean (%d outputs)" % len(expected))
        return

    if orphans and not args.prune:
        raise SystemExit("gen_textures: %d orphan output(s) not in the spec (rerun with --prune):\n  %s"
                         % (len(orphans), "\n  ".join(orphans)))

    os.makedirs(BLOCK_DIR, exist_ok=True)
    os.makedirs(ITEM_DIR, exist_ok=True)
    new_manifest = {}
    for rel in sorted(expected):
        data = expected[rel]
        digest = hashlib.sha256(data).hexdigest()
        new_manifest[rel] = digest
        abspath = os.path.join(RES, rel)
        if args.force or rel in created or rel in changed:
            os.makedirs(os.path.dirname(abspath), exist_ok=True)
            with open(abspath, "wb") as fh:
                fh.write(data)
    for rel in orphans:
        os.remove(os.path.join(RES, rel))

    with open(MANIFEST, "w", encoding="utf-8", newline="\n") as fh:
        json.dump({"schema": 1, "outputs": dict(sorted(new_manifest.items()))}, fh, indent=2)
        fh.write("\n")

    print("gen_textures: %d outputs (%d new, %d changed, %d orphan pruned)"
          % (len(expected), len(created), len(changed), len(orphans) if args.prune else 0))


if __name__ == "__main__":
    main()
