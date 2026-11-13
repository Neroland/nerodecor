# NeroDecor — Models, Textures & Connected Textures

How NeroDecor's two rendering foundations work and how to extend them: the **in-house
connected-texture (CTM) layer** and the **reproducible `gen_textures` pipeline**. Built in
Stage D, before any block, so every later family is modelled once against a stable layer.

Models, blockstates, loot, recipes, tags and lang are produced by **datagen** (not
hand-authored, not Blockbench); **textures** come only from `gen_textures`. Colours come
from Neroland Core's exported palette (`PaletteRegistry`, Core 1.9.0), mirrored for the
generator in [`tools/palette.json`](tools/palette.json).

## Connected textures (CTM)

A fully in-house layer — **no third-party dependency**. Lives in
`common/.../client/ctm/` so it is loader-agnostic; the per-loader render binding attaches
through `client/NeroDecorClient`.

### The algorithm (`CtmSolver`)

CTM uses the **corner method**: each block face is split into four `Quadrant`s, and each
quadrant's sub-tile is chosen only from the connectivity of the two edges and the one
diagonal that touch it:

| edges connected | diagonal | piece |
| --- | --- | --- |
| both | yes | `FILL` (interior) |
| both | no | `INNER_CORNER` (notch) |
| one | – | `EDGE_HORIZONTAL` / `EDGE_VERTICAL` |
| neither | – | `OUTER_CORNER` |

Enumerating all 256 neighbourhoods yields exactly **47 distinct whole-face appearances** —
the classic "47-tile" connected set — asserted by `CtmSolver.distinctFaceCount()`. Strips
(neon bars, trim) use the simpler one-dimensional `StripConnection` set (4 states). The
solver is pure (no Minecraft types), so it is unit-testable in isolation.

### Connection rules (`CtmSurface` / `CtmKey`)

A block opts in by implementing `CtmSurface`, returning a `CtmKey(family, colour)` per
state. **Two faces connect iff their keys are equal** — same family *and* same painted
colour (`DecorColor` ordinal, Stage E). So a red neon strip never merges into a blue one,
and hull never merges into glass. `ctmStyle()` selects `FULL`, `STRIP` or `GLASS`
(translucent). Connected **glass** renders on the cutout/translucent layer, is
waterloggable, and culls between same-tint panes.

### Config & fallback

Connected textures are gated by `NeroDecorConfig.CONNECTED_TEXTURES` (a client-local
kill-switch, on by default) and emissive glow by `EMISSIVE_RENDERING`. With CTM off — or if
a resource pack conflicts — surfaces fall back cleanly to the flat base tile. Both route
through Core's config framework (`/neroland config reload`).

### Per-loader render binding (Stage E, runtime-verified)

The concrete binding that wraps a block's baked model and swaps sub-tiles from neighbour
state is wired per loader (NeoForge/Forge model events, Fabric renderer API) **behind
`NeroDecorClient`**, so `common/` never imports loader render types. It attaches once the
first `CtmSurface` block exists and can be confirmed in a running client — the visual half
of Gate D is verified with the developer.

## `gen_resources` — the committed-JSON harness

`python tools/gen_resources.py` (or `./gradlew genAssets`).

NeroDecor follows the ecosystem convention: **resources are committed JSON in
`common/src/main/resources`, emitted by a Python harness — no runtime datagen** (MC
`runData` is deliberately avoided; its HashCache deletes every non-generated file under its
output dir). `gen_resources.py` reads a `SPEC` mirroring `registry/DecorBlocks.java` and
writes, for the registered blocks: blockstates, block + item models (referencing
`nerodecor:block/<texture>`), loot tables, crafting recipes (vanilla + Core only, no gate),
`neroland:decor/<family>` + `minecraft:mineable/pickaxe` tags, and `en_us` lang. Colour is
tint-only, so blockstates map to a single model per shape (the `COLOR` property is applied by
the render tint provider, not by the blockstate). To add blocks, extend the `SPEC` list to
match `DecorBlocks`, rerun, and validate the JSON.

## `gen_textures` pipeline

`python tools/gen_textures.py [--multiloader] [--check] [--prune] [--force]`
(or `./gradlew genAssets`).

Deterministically composites every block texture from per-family layer templates + the
palette manifest, into `common/src/main/resources/assets/nerodecor/textures/`:

| Output | Purpose |
| --- | --- |
| `block/<family>_<finish>.png` | base (interior `FILL`) tile |
| `block/<family>_<finish>_ctm.png` | 2×2 CTM atlas: `[FILL, EDGE, OUTER_CORNER, INNER_CORNER]` (renderer rotates) |
| `block/<family>_<finish>_emissive.png` | separate fullbright glow mask (emissive families only) |
| `item/<family>_<finish>.png` | item icon |

### Guarantees

- **Reproducible / idempotent** — no RNG, no timestamps; detail comes from a hash of
  `(family, finish, x, y)`. Re-running produces **byte-identical** PNGs.
- **Palette-driven** — colours come only from `tools/palette.json` (kept in lockstep with
  `CoreFinishes`). Emissive masks are composited layers, never hand-painted one-offs.
- **Guarded** — a checksum manifest (`tools/gen_textures.manifest.json`) records each
  output's sha256. `--check` reports drift (new / stale / orphan) and writes nothing (use
  it in CI). Orphan outputs no longer in the spec **fail the run** unless `--prune`.

### Adding a family or finish

1. **Finish:** add it to `tools/palette.json` (match the `CoreFinishes` RGB) and to the
   relevant family's `finishes` list in the `SPEC` at the top of `gen_textures.py`.
2. **Family:** add a `SPEC` entry (`finishes`, `emissive`, `translucent`) and a branch in
   the `_plate(...)` painter (plus `emissive_mask(...)` if it glows). Give the block a
   matching `CtmKey` family id when you register it (Stage E).
3. Run `python tools/gen_textures.py --multiloader`, then `--check` to confirm clean.
4. Keep the CTM atlas layout in step with `CtmPiece`; the renderer reads
   `[FILL, EDGE, OUTER_CORNER, INNER_CORNER]` from the 2×2 atlas.

## Luminous collection (0.4.0)

The signature set uses a lighter-weight connected-texture technique than the CTM solver:
**model-conditioned blockstates with baked face variants**. `ConnectedGlowBlock` keeps six
booleans (same block on that side) up to date in `updateShape`. The multipart blockstate has one
part per face per combination of that face's four in-plane neighbours (6 x 16 parts); each part is
a single full-size face using `<name>_f<mask>.png`, where the mask bits (8 top, 4 bottom, 2 left,
1 right, in texture space) say which edges carry the bezel. `FACE_EDGES` in `gen_resources.py`
maps texture edges to world directions for each face under vanilla auto-UV.

Pillars work the same way along one axis: `pos_link` / `neg_link` select a model whose side faces
use `<name>_side_v_c<t><b>` or `_side_h_c<l><r>` with the end collar baked in.

**Never draw the frame or collar as a separate partial quad over the face.** A 2px strip quad has
different vertices from the face under it, so its interpolated depth does not match exactly and
the two z-fight (smeared, flickering edges in game). Every quad on a given plane here is full size
with identical vertices.

Glow is a second, full-size, coplanar cutout element with `"light_emission": 15` and
`"shade": false` sampling a `_glow` texture whose alpha is only 0 or 255. Glow variants are cleared
under the baked bezel/collar so they never paint over it. Light-field blocks (lumen panel, aurora
glass) put `light_emission` on the base element instead.

Textures come from `tools/gen_luminous.py` (own manifest, `--check`, `--preview sheet.png`);
sounds from `tools/gen_sounds.py`; JSON from the `LUMINOUS` spec in `tools/gen_resources.py`.

## Collection layer (`tools/collections/`, 0.8.0)

New content ships as **collections**: one Python module per collection under `tools/collections/`
(`chroma_lights`, `bulkhead`, `holo`, `command`, `lab_habitat`, `gantry`, `conduits`, `seven_worlds`,
`ancients`, `greebles`, `chroma_core`), each owned by one author. `tools/gen_collections.py` turns
them into committed resources; the painting toolkit is `tools/collections/_kit.py`.

### Adding a block

1. Register the block in Java (same id), then in the collection module add a painter per texture to
   `TEXTURES` (`id -> fn()` returning a PIL image or a `_kit.Art` animation) and a `_kit.Block` to
   `BLOCKS`: `id`, `shape` (`cube` / `strip` / `custom`), `connects` (`none` / `mask16` / `strip`),
   `chroma`, `paintable`, `emissive`, `light`, `block_entity`, `textures` (role -> texture id; the
   built-in shapes use `all`), and for `custom` a `model(ctx)` returning `{model_name: model_json}`
   (plus an optional `blockstate(ctx)`; `_kit.facing_variants` covers rotations).
2. `mask16` needs `<tex>_frame` (use `_kit.bezel_layer`), `strip` needs `<tex>_cap`
   (`_kit.cap_layer`); emissive blocks need `<tex>_glow`. The kit bakes the per-face variants.
3. `chroma_lights.py` holds a fully worked `EXAMPLES` block (`light_strip`: face-mounted bar, caps
   via `pos_link`/`neg_link`, properties `face` + `axis`). Copy it.
4. Run `python tools/gen_collections.py --only <collection>`, then `--check`. Shared lang and tag
   entries (`en_us`, `mineable/pickaxe`, `neroland:decor/<family>`) are merged by
   `gen_resources.py`, which the runner calls for you.

Outputs: `blockstates/<id>.json`, `items/<id>.json` (with the `nerodecor:decor_color` tint only when
`paintable`), `models/item/<id>.json`, loot table, optional recipe, models under
`models/block/<collection>/` and textures under `textures/block/<collection>/`. Each collection has
its own manifest (`tools/collections/<collection>.manifest.json`); `--check` reports new, stale,
orphan, collision (a path another harness owns) and missing aggregate entries. The mount cannot
delete files, so an orphan that cannot be removed is reported until you delete it by hand.

### Texture bar (enforced where it can be)

- 16x16; 32x32 only for screens, glyphs and signs. Animation frames all the same size.
- Colours from 5-step hue-shifted ramps (`_kit.ramp(name)` reads `tools/palette.json`: house ramps
  plus the seven Nerospace world ramps; `_kit.ramp5(rgb)` derives one). No pure grey or black pixels.
- Key light top-left: `_kit.bevel` gives a 1px highlight and 1px occlusion.
- Wear through `_kit.wear` only (at most 8% of pixels, seeded by `hash(id, x, y)`); `_kit.dither` only
  for large gradients (64 px or more). Nothing random, nothing time-based.
- Glow layers: alpha 0/255, one accent hue, at most 12% emissive pixels unless `light > 0`. Emissive
  is always a separate full-size coplanar `_glow` element with `light_emission` and `shade: false`.
- Animations via `_kit.animate(tid, kind, paint)`: `breathing` 8 frames, `data`/`flow` 16, `flicker`
  4, with a per-texture frame offset. Glow strips never interpolate.
- Models: at most 40 elements; every quad full size for its element, and no two same-facing quads on
  one plane may overlap unless identical (the z-fight rule above). The runner rejects violations.

### Preview

`python tools/gen_collections.py --preview <collection> out.png` renders a contact sheet: every
texture at 4x with its id, size and animation, and an isometric render of each block's item model
(BLOCKS and EXAMPLES, tinted light blue where a face is tinted). `--only <collection> --examples
--out <dir>` does a full dry run into another folder.
