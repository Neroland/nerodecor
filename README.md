# NeroDecor

> Part of the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland Core**.

**Status:** beta — version `0.8.0-beta.1` "Chroma". Hull/panel and glass families with paint recolouring, the **Luminous collection**, and 106 new blocks in ten collections lit by the **Chroma** system: a hub that colours and animates linked lights, with an in-game editor, scenes, triggers and a paint gun. `/nerodecor gallery` builds a walkable showroom. See [`CHANGELOG.md`](CHANGELOG.md) and the [wiki](wiki/Home.md).

## Build targets

- **Minecraft:** 1.21.1, 1.21.4, 1.21.5, 1.21.8 (also 1.21.7), 1.21.10 (also 1.21.9), 1.21.11, 26.1.2, 26.2 and 26.3
- **Loaders:** NeoForge, MinecraftForge/Forge, Fabric (the "27 cells")
- **Java:** 21 for the 1.21.x builds, 25 for 26.x
- Mod id: `nerodecor` · package `za.co.neroland.nerodecor`

## Layout

The build is the repo root, with a flattened cross-loader structure driven by Stonecutter:

- `common/` — shared, loader-agnostic source spliced into every loader node
- `fabric/` — Fabric Loom
- `forge/` — ForgeGradle
- `neoforge/` — ModDevGradle
- `stonecutter.gradle` — the real root build script; `build.gradle` is intentionally inert

## Building

```sh
./gradlew :fabric:26.2:build          # one cell
./gradlew :neoforge:26.1.2:build :neoforge:26.2:build :neoforge:26.3:build \
          :forge:26.1.2:build :forge:26.2:build :forge:26.3:build \
          :fabric:26.1.2:build :fabric:26.2:build :fabric:26.3:build   # the 26.x cells
```

The 1.21.x cells are `:<loader>:1.21.1`, `:1.21.4`, `:1.21.5`, `:1.21.8`, `:1.21.10` and `:1.21.11`
(see `mc_versions` in `gradle.properties`; `.github/workflows/publish.yml` lists all 27).

See [`AGENTS.md`](AGENTS.md) / [`CLAUDE.md`](CLAUDE.md) for agent and contributor context.

## Quests

NeroDecor ships a **NeroQuests** quest pack: the chapter **Architect** (`nerodecor:architect`, 11
quests) walks a builder through the neon, panel, hull and glass families and the Luminous
collection, ending in the *Master Architect* capstone. It appears in the quest book only when
NeroQuests is installed; without it the pack is inert data in the jar. NeroQuests is **optional** —
NeroDecor has no dependency on it. See [`wiki/Architect-Quests.md`](wiki/Architect-Quests.md).

## Privacy

NeroDecor stores **no personal data**. It ships anonymous, NeroDecor-only crash reporting via
Sentry (EU servers) that is **on by default** and **opt-out**: set `telemetryEnabled=false` in
`config/nerodecor.properties` to switch it off. Reports carry a stack trace plus version strings
and the loaded-mod list — never IPs, usernames, UUIDs or world data. Full disclosure:
[`PRIVACY.md`](PRIVACY.md).

## Docs

- [`CHANGELOG.md`](CHANGELOG.md) — release history
- [`wiki/`](wiki/Home.md) — player-facing documentation
- [`wiki/Link-Module.md`](wiki/Link-Module.md) — the read-only NeroLink companion catalogue (public block and palette data only)
- [`PRIVACY.md`](PRIVACY.md) — privacy & crash-telemetry disclosure (POPIA / GDPR)
