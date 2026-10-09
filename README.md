# NeroDecor

> Part of the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland Core**.

**Status:** beta — version `0.4.0-beta.1`. Hull/panel, glass and neon families with shape variants, connected textures and paint recolouring, plus the **Luminous collection**: 17 glowing, animated, connected blocks with subtle ambient effects and sounds. See [`CHANGELOG.md`](CHANGELOG.md).

## Build targets

- **Minecraft:** 26.1.2, 26.2 and 26.3
- **Loaders:** NeoForge, MinecraftForge/Forge, Fabric (the "9 cells")
- **Java:** 25
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
          :fabric:26.1.2:build :fabric:26.2:build :fabric:26.3:build   # all nine
```

See [`AGENTS.md`](AGENTS.md) / [`CLAUDE.md`](CLAUDE.md) for agent and contributor context.

## Privacy

NeroDecor stores **no personal data**. It ships anonymous, NeroDecor-only crash reporting via
Sentry (EU servers) that is **on by default** and **opt-out**: set `telemetryEnabled=false` in
`config/nerodecor.properties` to switch it off. Reports carry a stack trace plus version strings
and the loaded-mod list — never IPs, usernames, UUIDs or world data. Full disclosure:
[`PRIVACY.md`](PRIVACY.md).

## Docs

- [`CHANGELOG.md`](CHANGELOG.md) — release history
- [`wiki/`](wiki/Home.md) — player-facing documentation
- [`PRIVACY.md`](PRIVACY.md) — privacy & crash-telemetry disclosure (POPIA / GDPR)
