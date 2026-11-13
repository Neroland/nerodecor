#!/usr/bin/env python3
"""
NeroDecor collection runner -- writes everything the plug-in modules under tools/collections/
declare into common/src/main/resources, guarded by one manifest per collection.

  python tools/gen_collections.py                         write every collection, then refresh the
                                                          shared lang/tag files via gen_resources.py
  python tools/gen_collections.py --only holo             one collection
  python tools/gen_collections.py --check [--only holo]   drift report (new / stale / orphan /
                                                          collision / aggregate); writes nothing
  python tools/gen_collections.py --preview holo out.png  contact sheet: every texture at 4x,
                                                          labelled, plus an isometric preview of
                                                          each block's item model (BLOCKS + EXAMPLES)
  python tools/gen_collections.py --only holo --examples --out /tmp/res
                                                          dry run into another folder (EXAMPLES
                                                          included; no manifest, no aggregates)

Per-block outputs (relative to common/src/main/resources):
  assets/nerodecor/blockstates/<id>.json, items/<id>.json, models/item/<id>.json,
  assets/nerodecor/models/block/<collection>/*.json, textures/block/<collection>/*.png(.mcmeta),
  data/nerodecor/loot_table/blocks/<id>.json, data/nerodecor/recipe/<id>.json (if declared)
  ../resources-26.3/data/nerodecor/loot_table/blocks/<id>.json (the same loot in the 26.3 format;
  paths with the _kit.RES_26_3 prefix are relative to common/src/main/resources-26.3)
Item-only art (a module's ITEMS + ITEM_TEXTURES, see _kit.Item):
  assets/nerodecor/models/item/<id>.json, items/<id>.json, textures/item/<collection>/<tid>.png
Shared files (en_us.json, mineable/pickaxe, neroland:decor/<family>) belong to gen_resources.py,
which pulls this layer's entries through collections.aggregate().

Deterministic (no RNG, no timestamps). Refuses to overwrite any file it does not own: an output
path that exists on disk but is not in the collection's manifest is a collision.
"""
import argparse
import hashlib
import importlib
import importlib.util
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
RES = os.path.join(REPO, "common", "src", "main", "resources")
NS = "nerodecor"
TEX_ROOT = "assets/%s/textures/block" % NS
PKG = "nerodecor_collections"


def load_package():
    """Import tools/collections as `nerodecor_collections` (never as `collections`)."""
    if PKG in sys.modules:
        return sys.modules[PKG]
    d = os.path.join(HERE, "collections")
    spec = importlib.util.spec_from_file_location(PKG, os.path.join(d, "__init__.py"),
                                                  submodule_search_locations=[d])
    pkg = importlib.util.module_from_spec(spec)
    sys.modules[PKG] = pkg
    spec.loader.exec_module(pkg)
    return pkg


def kit():
    return importlib.import_module(PKG + "._kit")


def render_json(obj):
    return (json.dumps(obj, indent=2) + "\n").encode("utf-8")


def _lint_base(tid, img):
    w, h = img.size
    px = img.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 255 and r == g == b:
                raise ValueError("%s: pure %s pixel at %d,%d -- use a hue-shifted ramp"
                                 % (tid, "black" if r == 0 else "grey", x, y))


def build(pkg, name, examples=False):
    """-> (outputs {rel: bytes}, arts {tid: Art}, blocks). Raises on any rule violation."""
    k = kit()
    mod = pkg.module(name)
    textures = getattr(mod, "TEXTURES", {})
    outs, arts, blocks = {}, {}, pkg.blocks(name, examples)
    refs = set()
    for b in blocks:
        for rel, obj in k.block_json(name, b, data=pkg.has_data(b)).items():
            if rel in outs:
                raise ValueError("%s: two blocks emit %s" % (name, rel))
            outs[rel] = render_json(obj)
            for v in obj.get("textures", {}).values() if isinstance(obj, dict) else ():
                prefix = "%s:block/%s/" % (NS, name)
                if isinstance(v, str) and v.startswith(prefix):
                    refs.add(v[len(prefix):])
        for tid, art in k.block_textures(name, b, textures).items():
            sizes = {f.size for f in art.frames}
            if len(sizes) != 1 or sizes.pop() not in ((16, 16), (32, 32)):
                raise ValueError("%s/%s: frames must all be 16x16 (or 32x32 for screens/glyphs/signs)"
                                 % (name, tid))
            for f in art.frames:
                if "_glow" in tid:
                    k.check_glow(tid, f, light=b.light > 0)
                else:
                    _lint_base(tid, f)
            if art.anim and len(art.frames) != k.ANIM_KINDS[art.anim][0]:
                raise ValueError("%s/%s: %s needs %d frames" % (name, tid, art.anim, k.ANIM_KINDS[art.anim][0]))
            arts[tid] = art
    item_tex = getattr(mod, "ITEM_TEXTURES", {})
    for it in pkg.items(name):
        for rel, obj in k.item_json(name, it).items():
            if rel in outs:
                raise ValueError("%s: %s is emitted twice (item %s)" % (name, rel, it.id))
            outs[rel] = render_json(obj)
        for tid, art in k.item_textures(name, it, item_tex).items():
            if len(art.frames) != 1 or art.size not in ((16, 16), (32, 32)):
                raise ValueError("%s/item/%s: one 16x16 (or 32x32) frame" % (name, tid))
            k.check_item_art(tid, art.frames[0])
            outs["assets/%s/textures/item/%s/%s.png" % (NS, name, tid)] = k.png_bytes(art.frames[0])
    missing = sorted(refs - set(arts))
    if missing:
        raise ValueError("%s: models reference unpainted textures: %s" % (name, ", ".join(missing)))
    for tid, art in sorted(arts.items()):
        rel = "%s/%s/%s.png" % (TEX_ROOT, name, tid)
        img = k.strip_png(art.frames) if len(art.frames) > 1 else art.frames[0]
        outs[rel] = k.png_bytes(img)
        if len(art.frames) > 1:
            outs[rel + ".mcmeta"] = k.mcmeta(art, "_glow" in tid)
    return outs, arts, blocks


def read_disk(root, rel):
    try:
        with open(os.path.join(root, rel), "rb") as fh:
            data = fh.read()
    except OSError:
        return None
    return data if rel.endswith(".png") else data.replace(b"\r\n", b"\n")


def sha(b):
    return hashlib.sha256(b).hexdigest()


def diff(pkg, name, outs):
    """-> (new, stale, orphan, collision) lists of rel paths for one collection."""
    manifest = pkg.read_manifest(name)
    new, stale, collision = [], [], []
    for rel, data in sorted(outs.items()):
        disk = read_disk(RES, rel)
        if rel not in manifest:
            (new if disk is None or disk == data else collision).append(rel)
        elif disk is None or disk != data or manifest[rel] != sha(data):
            stale.append(rel)
    orphan = sorted(set(manifest) - set(outs))
    return new, stale, orphan, collision


def aggregate_problems(pkg, names):
    k = kit()

    def load(rel):
        d = read_disk(RES, rel)
        return json.loads(d) if d else {}
    lang = load("assets/%s/lang/en_us.json" % NS)
    pick = set(load("data/minecraft/tags/block/mineable/pickaxe.json").get("values", []))
    out = []
    for name in names:
        for b in pkg.blocks(name):
            entries, fam = k.block_aggregates(name, b)
            bid = "%s:%s" % (NS, b.id)
            for key, val in entries.items():
                if lang.get(key) != val:
                    out.append("aggregate: en_us %s" % key)
            if pkg.has_data(b) and bid not in pick:
                out.append("aggregate: mineable/pickaxe %s" % bid)
            fam_ids = {v["id"] for v in load("data/neroland/tags/block/decor/%s.json" % fam).get("values", [])}
            if bid not in fam_ids:
                out.append("aggregate: neroland:decor/%s %s" % (fam, bid))
        for it in pkg.items(name):
            for key, val in k.item_aggregates(name, it).items():
                if lang.get(key) != val:
                    out.append("aggregate: en_us %s" % key)
    return out


def write(root, rel, data, dry=False):
    if dry and rel.startswith("../"):  # dry runs keep the 26.3 overlay inside the output folder
        rel = rel[3:]
    path = os.path.join(root, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:  # truncate in place (the dev mount cannot unlink/replace)
        fh.write(data)


# =========================================================================================
# Preview
# =========================================================================================
def _resolve(model, ref):
    tx = model.get("textures", {})
    seen = 0
    while ref.startswith("#") and seen < 8:
        ref = tx.get(ref[1:], "")
        seen += 1
    return ref


def _tex_image(ref, name, arts, cache):
    from PIL import Image
    if ref in cache:
        return cache[ref]
    img = None
    prefix = "%s:block/%s/" % (NS, name)
    if ref.startswith(prefix) and ref[len(prefix):] in arts:
        img = arts[ref[len(prefix):]].frames[0]
    elif ":" in ref:
        ns, path = ref.split(":", 1)
        p = os.path.join(RES, "assets", ns, "textures", path + ".png")
        if os.path.exists(p):
            img = Image.open(p).convert("RGBA")
            img = img.crop((0, 0, img.width, img.width))
    if img is None:
        img = Image.new("RGBA", (2, 2), (255, 0, 255, 255))
    cache[ref] = img
    return img


def iso_model(model, name, arts, tint, k=6):
    """Simple isometric render of a model's elements (up / south / east faces, painter order)."""
    from PIL import Image
    import math
    c30 = math.cos(math.pi / 6)
    W, H = int(32 * c30 * k) + 4, 32 * k + 4
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    cache = {}

    def scr(x, y, z):
        return (W / 2 + (x - z) * c30 * k, H / 2 + ((x + z) * 0.5 - y) * k)
    els = sorted(model.get("elements", []), key=lambda e: e["from"][0] + e["from"][1] + e["from"][2])
    for e in els:
        (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
        for face, shade in (("up", 1.0), ("south", 0.8), ("east", 0.62)):
            f = e["faces"].get(face)
            if not f:
                continue
            if e.get("light_emission"):
                shade = 1.0
            default = {"up": [x0, z0, x1, z1], "south": [x0, 16 - y1, x1, 16 - y0],
                       "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0]}[face]
            u0, v0, u1, v1 = f.get("uv", default)
            tex = _tex_image(_resolve(model, f["texture"]), name, arts, cache)
            tw, th = tex.size
            tp = tex.load()
            dims = {"up": (x1 - x0, z1 - z0), "south": (x1 - x0, y1 - y0), "east": (z1 - z0, y1 - y0)}[face]
            na, nb = max(2, int(dims[0] * k * 1.6)), max(2, int(dims[1] * k * 1.6))
            for i in range(na):
                a = (i + 0.5) / na
                for j in range(nb):
                    b = (j + 0.5) / nb
                    if face == "up":
                        p = (x0 + a * (x1 - x0), y1, z0 + b * (z1 - z0))
                    elif face == "south":
                        p = (x0 + a * (x1 - x0), y1 - b * (y1 - y0), z1)
                    else:
                        p = (x1, y1 - b * (y1 - y0), z1 - a * (z1 - z0))
                    u, v = u0 + a * (u1 - u0), v0 + b * (v1 - v0)
                    tx_ = min(tw - 1, max(0, int(u / 16.0 * tw)))
                    ty_ = min(th - 1, max(0, int(v / 16.0 * th)))
                    r, g, bl, al = tp[tx_, ty_]
                    if al < 128:
                        continue
                    if "tintindex" in f and tint:
                        r, g, bl = r * tint[0] // 255, g * tint[1] // 255, bl * tint[2] // 255
                    sx, sy = scr(*p)
                    if 0 <= int(sx) < W and 0 <= int(sy) < H:
                        px[int(sx), int(sy)] = (int(r * shade), int(g * shade), int(bl * shade), 255)
    return img


def preview(pkg, name, path):
    from PIL import Image, ImageDraw, ImageFont
    k = kit()
    outs, arts, blocks = build(pkg, name, examples=True)
    font = ImageFont.load_default()
    Z, CELL, LBL, PAD = 4, 140, 26, 10
    bg, ink, dim = (34, 38, 46, 255), (226, 232, 240, 255), (140, 150, 166, 255)
    tint = k.accent("light_blue")
    checker = Image.new("RGBA", (16 * Z, 16 * Z), (22, 25, 31, 255))
    cd = ImageDraw.Draw(checker)
    for y in range(0, 16 * Z, 8):
        for x in range(0, 16 * Z, 8):
            if (x // 8 + y // 8) % 2:
                cd.rectangle([x, y, x + 7, y + 7], fill=(30, 34, 42, 255))
    tex_cells = []
    for tid in sorted(arts):
        art = arts[tid]
        im = art.frames[0].resize((art.size[0] * Z, art.size[1] * Z), Image.Resampling.NEAREST)
        back = checker.resize(im.size) if im.size != checker.size else checker.copy()
        back.alpha_composite(im)
        note = "%dx%d" % art.size + (" %s x%d" % (art.anim, len(art.frames)) if art.anim else "")
        tex_cells.append((tid, note, back))
    model_cells = []
    for b in blocks:
        item = json.loads(outs["assets/%s/models/item/%s.json" % (NS, b.id)])
        mref = item["parent"].split("/", 2)[-1]
        model = json.loads(outs["assets/%s/models/block/%s/%s.json" % (NS, name, mref)])
        flags = [b.shape, b.connects] + [f for f in ("chroma", "paintable", "emissive", "block_entity")
                                         if getattr(b, f)]
        if b in getattr(pkg.module(name), "EXAMPLES", []):
            flags.append("EXAMPLE")
        model_cells.append((b.id, " ".join(flags), iso_model(model, name, arts, tint)))
    cols = 7
    big = max([c[2].size[0] for c in model_cells] + [CELL])
    rows_t = (len(tex_cells) + cols - 1) // cols
    rows_m = (len(model_cells) + 3) // 4
    tex_h = max([c[2].size[1] for c in tex_cells] + [16 * Z]) + LBL
    mod_h = max([c[2].size[1] for c in model_cells] + [64]) + LBL
    W = max(cols * (CELL + PAD), 4 * (big + PAD)) + PAD
    H = 40 + rows_t * (tex_h + PAD) + 30 + rows_m * (mod_h + PAD) + PAD
    sheet = Image.new("RGBA", (W, H), bg)
    d = ImageDraw.Draw(sheet)
    d.text((PAD, 10), "%s -- %d blocks, %d textures (4x; models iso, tint=light_blue)"
           % (name, len(blocks), len(arts)), fill=ink, font=font)
    y = 40
    for i, (tid, note, im) in enumerate(tex_cells):
        x = PAD + (i % cols) * (CELL + PAD)
        yy = y + (i // cols) * (tex_h + PAD)
        sheet.alpha_composite(im, (x, yy))
        d.text((x, yy + im.size[1] + 2), tid[:23], fill=ink, font=font)
        d.text((x, yy + im.size[1] + 13), note, fill=dim, font=font)
    y += rows_t * (tex_h + PAD)
    d.text((PAD, y + 6), "models", fill=ink, font=font)
    y += 30
    for i, (bid, flags, im) in enumerate(model_cells):
        x = PAD + (i % 4) * (big + PAD)
        yy = y + (i // 4) * (mod_h + PAD)
        sheet.alpha_composite(im, (x, yy))
        d.text((x, yy + im.size[1] + 2), bid, fill=ink, font=font)
        d.text((x, yy + im.size[1] + 13), flags, fill=dim, font=font)
    sheet.save(path)
    print("preview: %s (%dx%d, %d textures, %d models)" % (path, W, H, len(tex_cells), len(model_cells)))


# =========================================================================================
def main():
    ap = argparse.ArgumentParser(description="NeroDecor collection runner.")
    ap.add_argument("--only", metavar="COLLECTION")
    ap.add_argument("--check", action="store_true", help="report drift; write nothing; exit 1 on drift")
    ap.add_argument("--preview", nargs=2, metavar=("COLLECTION", "OUT_PNG"))
    ap.add_argument("--out", metavar="DIR", help="dry run: write outputs under DIR instead of resources")
    ap.add_argument("--examples", action="store_true", help="include EXAMPLES (only with --out)")
    ap.add_argument("--no-aggregates", action="store_true", help="do not run gen_resources.py after writing")
    ap.add_argument("--multiloader", action="store_true",
                    help="genAssets mode: skip the gen_resources.py refresh (genAssets runs it itself)")
    args = ap.parse_args()

    try:
        from PIL import Image  # noqa: F401
    except ModuleNotFoundError:
        print("gen_collections: Pillow not installed; skipping (pip install pillow).")
        return 0
    pkg = load_package()
    if args.preview:
        preview(pkg, args.preview[0], args.preview[1])
        return 0
    if args.examples and not args.out:
        ap.error("--examples only with --out (EXAMPLES never go into resources)")
    names = [args.only] if args.only else list(pkg.COLLECTIONS)
    if args.only:
        pkg.module(args.only)

    built = {n: build(pkg, n, args.examples)[0] for n in names}
    # block ids / JSON paths are global: no two collections may emit the same file (textures are
    # namespaced by collection folder). Other collections that fail to load are skipped here.
    seen = {}
    for n in pkg.COLLECTIONS:
        try:
            rels = set(built[n]) if n in built else (
                {r for b in pkg.blocks(n) for r in kit().block_json(n, b)}
                | {r for it in pkg.items(n) for r in kit().item_json(n, it)})
        except Exception as e:  # noqa: BLE001
            print("gen_collections: (dup check) skipping %s: %s" % (n, e))
            continue
        for rel in rels:
            if rel in seen:
                raise SystemExit("gen_collections: %s and %s both emit %s" % (seen[rel], n, rel))
            seen[rel] = n

    if args.out:
        agg = {"lang": {}, "decor": {}}
        for n in names:
            for rel, data in built[n].items():
                write(args.out, rel, data, dry=True)
            for b in pkg.blocks(n, args.examples):
                lang, fam = kit().block_aggregates(n, b)
                agg["lang"].update(lang)
                agg["decor"].setdefault(fam, []).append("%s:%s" % (NS, b.id))
            for it in pkg.items(n):
                agg["lang"].update(kit().item_aggregates(n, it))
        write(args.out, "_aggregates.json", render_json(agg))
        print("gen_collections: dry run, %d files -> %s" % (sum(len(v) for v in built.values()), args.out))
        return 0

    report = {n: diff(pkg, n, built[n]) for n in names}
    collisions = [(n, r) for n, (_a, _b, _c, col) in report.items() for r in col]
    if args.check:
        problems = []
        for n, (new, stale, orphan, col) in report.items():
            problems += ["%s new: %s" % (n, r) for r in new]
            problems += ["%s stale: %s" % (n, r) for r in stale]
            problems += ["%s orphan (no longer generated): %s" % (n, r) for r in orphan]
            problems += ["%s COLLISION (exists, not ours): %s" % (n, r) for r in col]
        problems += aggregate_problems(pkg, names)
        if problems:
            print("gen_collections --check: DRIFT")
            for p in problems:
                print("  " + p)
            return 1
        print("gen_collections --check: clean (%d collections, %d outputs)"
              % (len(names), sum(len(v) for v in built.values())))
        return 0

    if collisions:
        for n, r in collisions:
            print("  %s: %s exists and is owned by another harness (rename the block/texture)" % (n, r))
        raise SystemExit("gen_collections: refusing to overwrite %d file(s)" % len(collisions))
    total_w = total_o = 0
    for n in names:
        outs = built[n]
        new, stale, orphan, _ = report[n]
        for rel in new + stale:
            write(RES, rel, outs[rel])
        kept = []
        for rel in orphan:
            try:
                os.remove(os.path.join(RES, rel))
            except FileNotFoundError:
                pass
            except OSError:
                kept.append(rel)
                print("  %s: could not delete orphan %s -- delete it by hand" % (n, rel))
        mpath = pkg.manifest_path(n)
        if outs or os.path.exists(mpath):
            m = {rel: sha(data) for rel, data in outs.items()}
            m.update({rel: pkg.read_manifest(n)[rel] for rel in kept})
            with open(mpath, "w", encoding="utf-8", newline="\n") as fh:
                json.dump({"schema": 1, "outputs": dict(sorted(m.items()))}, fh, indent=2)
                fh.write("\n")
        total_w += len(new) + len(stale)
        total_o += len(outs)
    print("gen_collections: %d collections, %d outputs (%d written)" % (len(names), total_o, total_w))
    pending = [b.id for n in names for b in pkg.blocks(n) if not pkg.has_data(b)]
    if pending:
        print("gen_collections: %d block(s) not registered in Java yet -- loot/recipe/mineable withheld "
              "until they are (rerun after registering): %s" % (len(pending), ", ".join(pending[:8])
                                                               + (", ..." if len(pending) > 8 else "")))
    if not (args.no_aggregates or args.multiloader):
        r = subprocess.run([sys.executable, os.path.join(HERE, "gen_resources.py")])
        if r.returncode:
            return r.returncode
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, KeyError) as e:  # rule violations from _kit / the modules: one clear line
        sys.exit("gen_collections: %s" % (e.args[0] if e.args else e))
