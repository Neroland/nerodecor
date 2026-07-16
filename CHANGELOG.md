# Changelog

All notable changes to **NeroDecor** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0-beta.1]

First content release. NeroDecor moves from a barebones multiloader skeleton to a working
decorative block set with in-house connected textures, paint recolouring, and the ecosystem's
standard Core integration, config, and telemetry. Targets **MC 26.1.2 and 26.2** on **NeoForge,
Forge, and Fabric** (the six cells), **Java 25**, built on **Neroland Core 1.9.0** (the only hard
dependency).

### Added

**Decorative block families (42 blocks)**

- **Hull / structural** — `nero_alloy`, `starsteel`, `void_crystal`, each as cube + slab + stairs +
  wall (12 blocks).
- **Industrial panel** — the same three materials as cube + slab + stairs (9 blocks).
- **Reinforced glass** — `plasma_glass`, `cyan`, `light_blue` tints, each as cube + pane + slab
  (9 blocks), non-occluding with connected-glass rendering.
- **Neon light strips** — 12 colours (red, orange, yellow, lime, green, cyan, light_blue, blue,
  purple, magenta, pink, white), fullbright (light level 15).

**Connected textures (CTM)**

- Loader-agnostic tile-selection core and SPI (`client/ctm`): neighbourhood/quadrant solver with
  `FULL`, `GLASS`, and `STRIP` styles. Two faces connect on the same family **and** the same painted
  colour.
- Per-loader render binding on NeoForge, Forge, and Fabric via the client render seam.
- Client-local `connectedTextures` kill-switch (falls back to flat tiles for resource-pack conflicts
  or performance).

**Paint recolouring**

- Paintable `COLOR` block property backed by a `nerodecor:color` data component, carried on placement
  via the `DecorBlockItem` bridge (`NATURAL` = untinted).
- Colour applied through the 26.x `BlockTintSource` seam (`DecorColorTintSource`), registered per
  loader over every decor block; cube models carry `tintindex 0`.

**Assets**

- Black-and-blue futuristic theme with emissive glow accents and subtle looping animations
  (`.mcmeta` frame strips), produced by the deterministic `tools/gen_textures.py` pipeline.
- Regenerated mod logo / mods-list icon (`nerodecor_logo.png`).
- Committed, generated resources (blockstates, models, `items/` client-item JSON, loot, recipes,
  tags, lang) emitted by `tools/gen_resources.py`; both harnesses run via `./gradlew genAssets`.
- Crafting recipes for every block plus stonecutting routes (slab ×2, stairs, wall) from their base
  material.

**Neroland Core integration**

- Depends on **Neroland Core 1.9.0** (required, loads before NeroDecor); external interop stays
  Core-tag-mediated and dormant until third-party mods port to 26.1+.
- Signature blocks contributed to Core's shared `NEROLAND_DECOR` creative tab; blocks carry the
  `neroland:decor/*` common tags.

**Config** (`nerodecor`, via Core's config manager)

- `connectedTextures` (client-local, default on) — CTM render kill-switch.
- `emissiveRendering` (client-local, default on) — fullbright neon/glow layers.
- `telemetryEnabled` (default on, disclosed) — opt-out anonymous crash reporting.

**Telemetry**

- Opt-out Sentry crash reporting (EU ingest), matching the rest of the ecosystem: NeroDecor-only
  event filter (`za.co.neroland.nerodecor`), per-session de-duplication, a 10-event/session cap, and
  full PII scrubbing — no IP, hostname, username, UUID, world data, or chat; OS-account names stripped
  from file paths. Reports the loader/dist/runtime/MC version, the two render toggles, and the
  loaded-mod list (public ids + versions only). New `IPlatformHelper.getLoadedModIds()` seam on all
  three loaders.

**Commands**

- `/nerodecor gallery` — places a showcase of every family, shape, and paint colour; `/nerodecor
  clear` removes it.

### Notes

- NeroDecor stores no player-attributable data; POPIA/GDPR erasure is not applicable beyond the
  opt-out telemetry above.
- Every block is obtainable with or without other Nero mods — Core gating changes recipes, never
  availability.
- All six cells build cleanly (0 errors, 0 warnings; `ecjCheck` clean). On-screen verification of
  paint tint and CTM binding is pending a developer client run.
