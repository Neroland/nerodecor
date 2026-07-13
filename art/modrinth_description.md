# NeroDecor

**Build in the same visual language as the tech — hull, panels, neon, glass, holograms and sci-fi doors for the Neroland universe.**

NeroDecor is the **decoration & furniture** mod of the Neroland ecosystem — the cosmetic building set that lets you dress your world in the same sci-fi visual language as the machines that run it. Space station hull, industrial panels, reinforced glass, neon strips, hologram signs, lab furniture, server racks, control panels, sci-fi airlocks and planet-themed sets turn a functional installation into a cohesive station, lab or colony instead of vanilla rooms with a few machines bolted on.

Built on **Neroland Core**, so its colour/material palette, `c:` tags, creative-tab structure and server-side progression rules are shared with the rest of the lineup — a "Nero Steel" hull here matches the exact tone on a Nerotech machine casing. *(Planned — in design; not yet released.)*

---

## What you can build

1. **Space station blocks.** A modular kit of hull plating, floors, ceilings, trims and structural blocks — static models with rotation/axis states and **connected textures** so adjacent hull pieces line up into seamless large surfaces, in Core colour/finish variants that match Nerospace ships and stations.
2. **Industrial panels.** Riveted, vented, grated and plated wall/floor blocks — the foundational "looks like a factory" set, visually matched to Nerotech machine housings so a base reads as one continuous installation.
3. **Reinforced glass.** Clear, tinted and framed sci-fi glazing with CTM-driven framing and waterlogging, so windows render as seamless panes; tinted variants pull from Core's palette to match ship canopies and observation decks.
4. **Neon lights.** Emissive light strips, bars and panels in the full Core colour palette — no block entity, optionally connectable into continuous runs — the accent layer that ties surfaces to the glowing trim on Nerotech/Nerospace machines.
5. **Hologram signs.** A projector base whose **block entity** floats translucent, emissive text/icons above it via a billboard renderer — small payloads synced only on change, distance-culled — designed as **flavour dashboards** other mods can drive.
6. **Control panels.** Wall- and console-mounted panels of buttons, gauges and screens; a lightweight block entity holds display state (and an optional linked target) behind a textured, distance-culled emissive face.
7. **Lab furniture & server racks.** Tables, consoles, cabinets and sample storage for research rooms, plus datacentre racks with blinking emissive LEDs and optional cheap animation — the connective decoration for science and operations builds.
8. **Sci-fi doors.** Animated single- and multi-block doors and **airlocks** with a sliding/iris open-close, coordinated by a block entity that drives animation and collision, with connected-texture faces to match the surrounding hull.
9. **Planet-themed blocks.** Decorative sets keyed to Nerospace's planets — lunar regolith greys, Martian rust-reds, ice-world blues — **gated by Nerospace progression** so a planet's set unlocks as you reach it (toggleable in config).

## Built for volume

- 🧱 **Performance under hundreds of blocks** — static blocks are model-only with no block entity; only genuinely dynamic blocks (holograms, control panels, animated racks, doors) carry one, and they prefer event-driven, distance-culled, frustum-aware rendering over per-tick work.
- 🎨 **Modular by design** — a small set of block families × Core colour/material variants × rotation/waterlog states yields a large, coherent build vocabulary without exploding the registry.
- 🎛️ **Tune anything** — config toggles for emissive rendering, hologram render distance, animated-prop animations (for low-end clients), connected-texture behaviour, and disabling planet-themed gating on build-focused servers.
- 📟 **Dashboard flavour** — hologram signs and control panels expose a small content API so **NeroSecurity** and **NeroLogistics** can push status, alerts and route labels onto placed props, keeping all real logic in the source mod.

## Privacy (POPIA / GDPR)

NeroDecor stores **no personal data** — its blocks are cosmetic and carry no power, inventory, or player-keyed state. Any optional crash telemetry is **anonymous and opt-out**, carrying only version strings (mod / MC / loader / OS / Java) — never IPs, usernames, UUIDs or world data.

## Why it fits the ecosystem

- 🧩 **Built on Neroland Core** — one colour/material palette, one config framework, shared `c:` tags, and Core's server-side progression rules for planet gating. NeroDecor ships in its own creative tab.
- 🔌 **Interoperates, never hard-depends** — Nerotech, Nerospace, NeroColonies, NeroSecurity and NeroLogistics are all optional. With them absent, NeroDecor plays **fully standalone** as a decorative set, simply without the cross-mod dashboard content or planet-themed progression gating.
- 🚀 **Visual match across the lineup** — panels and neon match Nerotech casings, hull and glass match Nerospace ships and stations, and lab furniture, planet sets and airlocks dress **NeroColonies** habitats to feel native to their world.
- 🧱 **Cross-loader** — NeoForge, Forge and Fabric on Minecraft **26.1.2** and **26.2**.

## Requirements & compatibility

- **Requires [Neroland Core](https://modrinth.com/mod/nerolandcore)** — install it alongside NeroDecor (it loads first); NeroDecor draws its palette, creative tabs, config and progression rules from Core.
- Conventional `c:` tags mediate all external compatibility, so Create, AE2, Mekanism, Ad Astra and Energized Power share NeroDecor's material palette as the 26.x ecosystem fills in — no hard dependency on any of them.
- **Modpacks are allowed and encouraged** — any platform, no need to ask. Use the official files and credit *NeroDecor by Neroland* with links to the [CurseForge page](https://www.curseforge.com/minecraft/mc-mods/nerodecor) and the [GitHub repository](https://github.com/Neroland/nerodecor). Full terms: [LICENSE](https://github.com/Neroland/nerodecor/blob/main/LICENSE).

## Links

- 📖 **[Wiki](https://github.com/Neroland/nerodecor/wiki)** — every block family and system documented.
- 💬 **[Discord](https://discord.gg/ArPXvYUzJG)** — chat, help, and sneak peeks.
- 🐞 **[Issues](https://github.com/Neroland/nerodecor/issues)** — bug reports and feature requests.
- 🗒️ **[Changelog](https://github.com/Neroland/nerodecor/blob/main/CHANGELOG.md)**
- 🔥 **[Also on CurseForge](https://www.curseforge.com/minecraft/mc-mods/nerodecor)**

---

*Created by Neroland. The project logo was made with the help of AI image tools; in-game art is generated by the project's own tooling and refined by hand.*
