"""
NeroDecor collection plug-in layer. One module per content collection; each is owned by one art
agent and declares:

  BLOCKS    list[_kit.Block]          blocks that ship (emitted into common/src/main/resources)
  TEXTURES  {texture id: painter}     painter() -> PIL Image or _kit.Art (animated)
  EXAMPLES  list[_kit.Block] (opt.)   preview-only blocks, never written to resources
  ITEMS     list[_kit.Item] (opt.)    item-only art (items with no block): model + client item +
                                      textures/item/<collection>/<tid>.png + en_us name
  ITEM_TEXTURES {texture id: painter} (opt.) painters for ITEMS layers (16x16, alpha 0/255)

gen_collections.py writes the per-block files (blockstates, models under block/<collection>/,
items, loot, recipes, textures under textures/block/<collection>/) with one manifest per
collection; gen_resources.py merges the shared files (en_us lang, mineable/pickaxe,
neroland:decor/<family> tags) from aggregate().

This package is loaded by file path under the name `nerodecor_collections` (see load()), never
as `collections`, so it cannot shadow the standard library.
"""
import importlib
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
COLLECTIONS = ["chroma_lights", "bulkhead", "holo", "command", "lab_habitat", "gantry",
               "conduits", "seven_worlds", "ancients", "greebles", "chroma_core"]


def module(name):
    if name not in COLLECTIONS:
        raise KeyError("unknown collection %r (known: %s)" % (name, ", ".join(COLLECTIONS)))
    return importlib.import_module("%s.%s" % (__name__, name))


def blocks(name, examples=False):
    m = module(name)
    return list(m.BLOCKS) + (list(getattr(m, "EXAMPLES", [])) if examples else [])


def items(name):
    """Item-only art a collection declares (ITEMS, a list of _kit.Item); [] when it has none."""
    return list(getattr(module(name), "ITEMS", []))


def manifest_path(name):
    return os.path.join(HERE, "%s.manifest.json" % name)


def read_manifest(name):
    p = manifest_path(name)
    if not os.path.exists(p):
        return {}
    with open(p, encoding="utf-8") as fh:
        return json.load(fh).get("outputs", {})


def aggregate(strict=True, log=print):
    """What gen_resources.py needs: shared lang/tag entries plus every resource path the layer
    owns (so its orphan check skips them). strict=False skips a collection that fails to import
    or declare (an art agent mid-edit) with a warning instead of raising."""
    from ._kit import block_json, block_aggregates, item_json, item_aggregates
    agg = {"lang": {}, "mineable": [], "decor": {}, "owned": set()}
    for name in COLLECTIONS:
        try:
            for b in blocks(name):
                agg["owned"].update(block_json(name, b))
                lang, fam = block_aggregates(name, b)
                agg["lang"].update(lang)
                agg["mineable"].append("nerodecor:" + b.id)
                agg["decor"].setdefault(fam, []).append("nerodecor:" + b.id)
            for it in items(name):
                agg["owned"].update(item_json(name, it))
                agg["lang"].update(item_aggregates(name, it))
            agg["owned"].update(read_manifest(name))
        except Exception as e:  # noqa: BLE001 -- a broken module must not stop other harnesses
            if strict:
                raise
            log("collections: skipping %s (%s: %s)" % (name, type(e).__name__, e))
    return agg
