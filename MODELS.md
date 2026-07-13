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
