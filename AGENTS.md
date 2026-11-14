# Project context for AI coding agents — nerodecor

> NeroDecor is published on CurseForge and GitHub only. Do not reference or link any other mod host.

> This and `CLAUDE.md` are kept identical; update both together.

## The mod

- **NeroDecor** — part of the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland Core**.
  A sci-fi decoration mod: paintable hull, panels and glass, the Luminous collection, and since 0.8.0
  "Chroma" ten more collections (165 registered blocks of a 200 cap, 12 of them hidden neon shims dropped
  in 0.9.0) lit by the Chroma system (hubs that colour and animate linked blocks), 11 block entity types
  (doors, holograms, wall screens, the hub) and a seat entity. Shared content lives under `common/` and is
  wired through each loader entry point.
- Mod id: **`nerodecor`** (matches the registry namespace + every loader manifest). Package root:
  `za.co.neroland.nerodecor`. Author: **Neroland**.
- Version: **0.8.0-beta.1**.
- Targets **MC 1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1.2, 26.2 AND 26.3** on **NeoForge,
  MinecraftForge/Forge, and Fabric** → **27 cells**. Source is written for 26.x; older lines use `//?` blocks and
  the `legacyRenames` list in `stonecutter.gradle`. **Java 25** for 26.x, **Java 21** for 1.21.x. Mappings =
  official Mojang names (26.x ships de-obfuscated; 1.21.x Fabric uses Loom's Mojang mappings; no Parchment).

## Working rules

- **Keep responses concise and direct** — minimal verbosity, minimal formatting.
- **POPIA & GDPR**: keep all logging/telemetry/scripts compliant — only public version strings, never
  personal data; minimise data, set retention limits, support export/erasure and opt-out.
- **NEVER commit or push automatically.** Leave changes **staged**; the developer reviews and commits
  with native git (the source of truth).
- **Use relative paths only** — never hard-code machine-specific absolute paths in committed files.
- **Never run commands against production databases.** Treat any DB command as illustrative.

## Repo layout — flattened cross-loader build

- **The build IS the repo root.** `common/` (shared source spliced into every node), `neoforge/`
  (ModDevGradle), `forge/` (ForgeGradle), `fabric/` (Fabric Loom). Root build files: `settings.gradle`,
  `stonecutter.gradle` (the REAL root build script — Stonecutter repoints `buildFileName` here; the root
  `build.gradle` is inert), `gradle.properties`, `gradlew`, `gradle/`.
- **Version/loader axis = Stonecutter.** Each loader×MC is a real node `:<loader>:<mc>` — 27 of them:
  `fabric`, `neoforge`, `forge` × `1.21.1 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11 26.1.2 26.2 26.3`. `common` is
  NOT a node — its source is spliced via `rootProject.ext.commonJava` / `commonResources`. Dependency pins
  live in `gradle.properties` as `*_version_<mc>` keys; `mc_versions=1.21.1,1.21.4,1.21.5,1.21.8,1.21.10,1.21.11,26.1.2,26.2,26.3`.
- **Version-specific code in `common/`.** Non-active nodes run `common/` through Stonecutter (`stonecutterProcessCommon`), so shared code uses the same `//? if >=26.3 {` blocks as the loader `src/` trees. Keep the files in the vcsVersion state, and never put a `*/` inside a disabled block. Datapack files whose format differs by version go in `common/src/main/resources-<mc>/`, which is merged over `common/src/main/resources` for every node at or above `<mc>` (`mergeCommonResources`).

## Code map (0.8.0)

- `registry/` — `DecorBlocks` (hull/panel/glass/neon), `LuminousBlocks`, `ChromaContent` (hub, cube, linker,
  scene chip, paint gun), `registry/collections/*Blocks` (the ten 0.8.0 collections through
  `CollectionRegistries.block("id", …)`, which feeds `DecorBlocks.ALL` → items, creative tab, tint, link
  catalogue), plus block entity and entity types (`nerodecor:seat`).
- `content/block/<collection>/` — the collection blocks; `bulkhead/BhcBlock` + `BhcShapes` are the shared
  base for doors/holo/command, `lights/CollectionBlock` for the rest (empty-hand `cycled` looks).
- `chroma/` — pure Java (java.* only, unit-tested): colours, palette, OKLab gradients, effects, timelines,
  scenes, codecs, share codes, flash clamp, reduced motion. `chroma/server/` — networks, store/SavedData,
  validator, limits/config, rate limiter, scenes + triggers, guard/reach, hub admin; `chroma/server/privacy/`
  — erasure, export, retention, `/nerodecor privacy|chroma` commands. `chroma/net/` — snapshot/delta/op
  payloads and the hub screen packets. `chroma/client/` — tint bridge sync, the per-hub animation layer
  (`ChromaHubLayerRenderer`, `ChromaModelQuads` version shims, `ChromaLayerBudget`). `chroma/editor/` —
  hub editor model (pure).
- `client/` — `ClientRenderRegistry` (render layers, BE and entity renderers, iterated by each loader),
  `client/render/bhc/BhcRenderer` (one BE-renderer shim across 1.21.1-26.3), `client/screen/chroma/` (hub
  editor screen + 3D viewport), `client/ctm/` (legacy CTM solver, unit-tested, not bound).
- `command/` — `/nerodecor gallery` (showroom), `gallery grid` (collection catalogue), `capture goto`;
  `command/showroom/` holds the showroom builder (`Showroom`), the eight bays as plan-view data (`ShowroomScenes`),
  the registry-driven block grid (`BlockGrid`) and the camera presets (`CameraPresets`). `client/capture/` is the
  client-only `/nerodecor capture` command (screenshots from the presets), registered per loader.
- `compat/` — `ViewerInfo` + JEI/EMI plugins (omitted on cells without the APIs, all 1.21.x).
- `link/` — the NeroLink module (schema 2).

## Build & verify

- Build per cell with the Gradle wrapper, e.g. `./gradlew :fabric:26.3:build`; a release gate builds all
  27 (`:<loader>:<mc>:build` for every loader and MC version above). Never a plain `build`.
- **Tests.** JUnit 5 tests live in `common/src/test/java` (pure Java, no game bootstrap; Stonecutter
  preprocesses them like main) and run on the nine NeoForge nodes as part of `build`. Server GameTests
  live in `neoforge/src/gametest` (dev-only mod `nerodecor_gametest`, never packaged) on the 26.3 cell:
  `./gradlew :neoforge:26.3:runGameTestServer` — placement, paint, registry cap, the 0.7.1 fixture, Chroma
  links/sync/scenes/privacy/link module, recipes + unlocks, and `showroom_builds`. Each test is a
  `test_function` registered in `NeroDecorGameTests` plus a `test_instance` JSON. `tools/gen_gametest_fixture.py`
  writes the frozen 0.7.1 fixture structure those tests load.
- **Showcase screenshots.** A Fabric client game test (`fabric/src/gametest`, dev-only mod `nerodecor_showcase`,
  never packaged; 26.3 Fabric cell only) builds a showcase world at midnight — a Chroma room, 2x2 and 3x2 wall
  screens, the seven globes — runs `/nerodecor gallery grid` and `/nerodecor gallery`, shoots both from the
  `CameraPresets` (`grid_*.png`, `showroom_*.png`, the same names `/nerodecor capture` uses), runs a short
  `capture grid 2`, then every Chroma Hub tab: `./gradlew :fabric:26.3:runClientGameTest` (opens a game
  window). Shots land in `fabric/versions/26.3/build/run/clientGameTest/screenshots` (with `showcase-report.txt`)
  and are copied to `tools/collections/.preview/screens/` (gitignored).
- **Showroom.** `/nerodecor gallery` (creative or op) builds eight bays in a fixed 97 x 13 footprint 3 blocks
  east/south of the command position, clearing it first; `gallery clear` removes it. Bay layouts are data in
  `command/showroom/ShowroomScenes.java`; the `showroom_builds` GameTest rejects any unknown id/property/value.
  `/nerodecor gallery grid` builds every visible block, one row per collection (`BlockGrid`); a new block needs a
  collection there or the `block_grid_builds` GameTest fails.
- Static analysis: `./gradlew :fabric:26.2:ecjCheck` (the VS Code Problems panel, via `tools/ecj.prefs`).
  The task only FAILS on errors.
- A Cowork agent sandbox cannot decompile Minecraft — run builds natively (or via the local gradle MCP)
  on the developer's machine.
- **Verify the cells build before marking a task done.** Never sign off on an uncompiled change.

## Conventions (cross-loader)

- **Resources are committed JSON in `common/src/main/resources`, generated by Python harnesses (no
  runtime datagen)** — matching Core/nerospace. `tools/gen_resources.py` emits the loader-agnostic
  blockstates/models/loot/recipes/tags/lang JSON for the registered blocks; `tools/gen_textures.py` emits
  the textures (see [`MODELS.md`](MODELS.md)). Both are deterministic/idempotent and run via
  `./gradlew genAssets` (or directly). A few genuinely bespoke resources may be hand-authored. Validate
  JSON after any edit or regeneration. **Do NOT wire MC datagen** (`runData`) — its HashCache deletes every
  non-generated file under its output dir, and the ecosystem deliberately avoids it in the multiloader.
- **Platform seams via ServiceLoader (no Architectury).** Put loader-agnostic code in `common/`; ship one
  impl per loader plus a `META-INF/services` entry. Keep `common/` free of `net.neoforged.*` /
  `net.fabricmc.*` / `net.minecraftforge.*` imports.
- Loader entry points: `NeroDecorFabric` (+ `NeroDecorFabricClient`), `NeroDecorForge`,
  `NeroDecorNeoForge` — each calls `NeroDecorCommon.init()` during construction.
- NeoForge/Forge debug tasks use `-PnerodecorDebug`; Fabric Loom honours Gradle `--debug-jvm`.

## IDE (VS Code) run & debug

- Workspace: **`nerodecor.code-workspace`** (single-root `"."`). Import the Stonecutter nodes as **static
  Eclipse projects**: `./gradlew eclipse` (live Buildship/Loom import is disabled —
  `java.import.gradle.enabled=false`). Re-run `./gradlew eclipse` after dependency changes, then reload
  VS Code. Per-node Eclipse project names are `nerodecor-<loader>-<mc>`.
- **Run/Debug** a cell from `tasks.json` / `launch.json`.

## Wiki — keep `wiki/` updated

- This mod has its own **dedicated wiki** in `wiki/` at the repo root: the player- and
  contributor-facing docs for NeroDecor (features, blocks/items, machines, progression, recipes, FAQ).
- **Whenever you add, change, or remove a feature, update `wiki/` in the same change** — treat the
  wiki as part of "done"; code without a matching wiki update is incomplete.
- One page per topic; keep `wiki/Home.md` as the index that links every page, with relative links
  between pages. Validate Markdown via the gradle MCP `markdown_check` (honours `.markdownlint.json`).
- The wiki is **per-mod** — document only NeroDecor here; cross-mod / ecosystem concepts belong in the
  relevant other mod's own wiki.

## DO NOT

- Commit or push automatically — leave changes staged for the developer.
- Hard-code absolute machine paths in committed files.
- Add loader-specific code to `common/` — use the platform seams.

## Translation workflow

English is the source language for new and changed text. Community translations are intended to be contributed through Weblate; other-language translations are not required when implementing a feature. Preserve existing translations. This supersedes older instructions that require translating every change.
