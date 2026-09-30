# Changelog

All notable changes to **NeroDecor** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

The **Luminous collection** — NeroDecor's signature set. See the wiki page
`wiki/Luminous-Collection.md`.

### Added

- **17 new blocks** (59 total, well inside ADR-001's 200 cap): Circuit Plating, Holo-Grid Floor,
  Starfield Panel, Data Stream Panel, Aurora Glass, Lumen Panel, Void Rift, Void Crystal Lattice,
  Capacitor Bank, Xenobloom, Ion Vent, Plasma Conduit, Starsteel Pillar and Fusion Lamp, plus slabs
  of Circuit Plating, Holo-Grid Floor and Lumen Panel. All model-only: no block entities, no ticking.
- **Glow at night** via a cutout emissive model overlay (`"light_emission": 15`) so only the lit
  details glow (traces, stars, plasma, fractures, spores), not the whole block.
- **Model-conditioned connected textures** for the six surface blocks: six connection booleans pick,
  per face, one of 16 texture variants with the bezel baked in on the unconnected edges, so walls,
  floors and ceilings read as one panel. Pillars link along their axis and lose their inner
  collars. Pure vanilla JSON on every loader, and no coplanar overlay quads (no z-fighting).
- **Fusion Lamp**: a redstone lamp with power-up / power-down tones and a quiet hum while lit.
- **Ambient effects** (`content/fx/AmbientFx`) from `animateTick`: star motes, void-rift portal
  specks, conduit sparks, crystal glints, rising spores, vent wisps, lamp motes.
- **Nine sound events** with 17 synthesised sounds (`tools/gen_sounds.py`, no samples): circuit
  chirps, void hum, plasma crackle, crystal chimes, lamp power on/off/hum, vent hiss, capacitor
  charge. Each sound has a shared cooldown so large builds stay quiet. Subtitles included.
- **Config** (client-local, default on): `ambientParticles`, `ambientSounds`.
- **Gallery**: `/nerodecor gallery` adds a Luminous row: 3×3 walls for the connected surfaces, linked
  pillar columns, a lit Fusion Lamp and plinths for the rest.
- Recipes for every new block (vanilla and Core ingredients, no gates) and stonecutter routes for
  the slabs and the Starsteel Pillar. New `neroland:decor/luminous` block tag.
- Tools: `tools/gen_luminous.py` (deterministic textures with `--check` and `--preview`) and
  `tools/gen_sounds.py` (needs ffmpeg with libvorbis; skips gracefully without it).

### Changed

- The paint tint source is now registered only for blocks with the `COLOR` property
  (`DecorBlocks.paintableBlocks()`); the Luminous blocks keep their fixed finishes.
- `./gradlew genAssets` now also runs `gen_luminous.py` and `gen_sounds.py`.

## [0.3.0-beta.1] - 2026-09-24

EMI compatibility. No gameplay, id, tag or config change.

### Added

- **EMI support.** Every NeroDecor recipe uses a vanilla recipe type, which EMI shows on its own, so
  nothing needed a plugin. The build now compiles against the community EMI Unofficial Port (Unstable),
  the only EMI build for Minecraft 26.x, and dev clients load it with `-PwithEmi` (default runs stay
  JEI-only). EMI stays optional.

## [0.2.0-beta.1] - 2026-09-20

Minecraft **26.3** support. No gameplay, id, tag or config change.

### Added

- **Minecraft 26.3** as a new Stonecutter node on every loader — NeoForge `26.3.0.7-beta`,
  Forge `26.3-66.0.2` and Fabric (fabric-api `0.161.0+26.3`, NeoForm `26.3-1`) — built alongside
  26.1.2 and 26.2, so every release now ships **nine** loader × version jars.

### Changed

- VS Code run/debug configurations (`.vscode/launch.json`, `.vscode/tasks.json`) gain the three
  26.3 cells; the "Build all" task now builds all nine.
- CI (`multiloader.yml`, `publish.yml`) builds, attaches and publishes the 26.3 jars.
- Requires **Neroland Core 1.13.0** (was `1.9.0`) — the first Core release with a 26.3
  build. The loader range still derives from the pin (`[${nerolandcore_version},2.0)`).
- JEI pins moved to the newest published builds on each Minecraft version: `29.40.0.101` (26.1.2), `30.35.0.223` (26.2) and `31.3.0.18` (26.3). Compile-time API only — JEI remains a soft dependency and the shipped jar gains no hard requirement.

### 26.3 port notes

- Build: the shared `common/` Java source is now preprocessed by Stonecutter for every non-active node (`stonecutterProcessCommon`), so common code can carry `//? if >=26.3 {` blocks, and `common/src/main/resources-<mc>` overlay folders are merged over the shared resources for matching nodes (`mergeCommonResources`). The active node still compiles the raw `common/` folder.
- Build plugins aligned with Neroland Core: ModDevGradle `2.0.147` (the older 2.0.141 cannot set up NeoForge 26.3), ForgeGradle `7.0.40`, Stonecutter `0.9.8`.
- NeoForge metadata: the deprecated `logoFile` property is replaced by `iconFile` on 26.2+ (the logo is a square 256x256 PNG) while 26.1.2, whose FML only understands the old key, still gets `logoFile` — the key is chosen per cell when the manifest is expanded. This clears NeoForge 26.2+'s dev-only "uses the deprecated `logoFile` property" warning screen. The Forge manifest is unchanged: `logoFile` is still the only key Forge supports.

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
