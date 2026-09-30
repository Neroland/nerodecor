# Luminous Collection

NeroDecor's signature set (added in **0.4.0**): fourteen blocks and three slabs built in the
same black-and-blue language as the hull and panel families, but made to come alive. They glow
at night, animate, join into seamless surfaces and, now and then, make a quiet sound.

Every Luminous block is **model-only**: no block entities, no ticking. The glow is a vanilla
model light-emission layer, the effects run on the client's normal display tick, and the
connected edges are plain blockstate properties. Large builds stay cheap.

## The blocks

| Block | Light | Glows at night | Effect | Connects | Sound |
| --- | :-: | --- | --- | --- | --- |
| Circuit Plating | 4 | Teal traces, light packets racing along them | Animated | Seamless bezel | Soft data chirps |
| Holo-Grid Floor | 6 | Cyan grid that breathes, with a slow scan line | Animated | Seamless bezel | — |
| Starfield Panel | 3 | Twinkling stars over a violet-teal nebula | Star motes drift off the face | Seamless bezel | — |
| Data Stream Panel | 5 | Glyph columns cascading downwards | Animated, flows across stacked panels | Seamless bezel | Low data chirps |
| Aurora Glass | 7 | Whole pane, softly | Drifting aurora curtains | Seamless bezel, hides inner faces | — |
| Lumen Panel | 15 | Whole face, cool white | Soft light field for ceilings | Seamless bezel | — |
| Void Rift | 8 | Three-armed vortex | Portal specks drawn into it | — | Deep, slow hum |
| Void Crystal Lattice | 6 | Fracture lines | Shimmer sweep, sparkles, lilac glints | — | Crystal chimes |
| Capacitor Bank | 7 | Charge in three cells, status LEDs | Cells fill and drain out of phase | — | Charging whine |
| Xenobloom | 6 | Bioluminescent colonies | Colonies pulse; glowing spores rise | — | — |
| Ion Vent | 5 | Ion-blue glow between the louvres | Wisps and sparks rise from the top | — | Gentle hiss |
| Plasma Conduit | 10 | Plasma core | Plasma flows along the tube; sparks | Along its axis | Electric crackle |
| Starsteel Pillar | 3 | Teal inset channel, end emblem | Channel pulses | Along its axis | — |
| Fusion Lamp | 0 / 15 | Caged plasma core when powered | Core breathes; motes rise | — | Power-up, power-down, low hum |

Slabs: **Circuit Plating Slab**, **Holo-Grid Floor Slab** and **Lumen Panel Slab** (the lumen
slab is a flush ceiling light).

## How the connected textures work

The six connected surfaces (circuit, holo-grid, starfield, data stream, aurora glass, lumen) draw a
thin bevelled bezel only along edges whose neighbour is *not* the same block. Place them in a wall,
floor or ceiling and the inner seams disappear, leaving one large framed panel. This uses vanilla
multipart blockstates, so it works on every loader and needs no extra mod.

Pillars (plasma conduit, starsteel pillar) place along the axis you click, like logs. A stacked run
loses its inner collars and reads as one continuous column, capped at both ends.

## Fusion Lamp

Works like a redstone lamp: it lights instantly when powered and switches off four ticks after the
signal goes. Lighting it plays a soft rising tone and switching it off a falling one; while lit it
hums very quietly.

## Keeping it subtle

- Each ambient sound has a **shared cooldown**, so a wall of two hundred circuit plates chirps no
  more often than a single plate would. Volumes are low and the sounds were designed without harsh
  highs.
- Light levels are moderate except for the two real light sources (Fusion Lamp, Lumen Panel).
- Animations are slow loops (2 to 5 seconds) with no strobing.
- Two client-side config switches turn the ambience off (see below).

## Config

In the `nerodecor` config (Neroland Core's config manager, reload with `/neroland config reload`):

| Key | Default | Effect |
| --- | --- | --- |
| `ambientParticles` | `true` | Star motes, rift specks, sparks, glints, spores and vent wisps. Client-side. |
| `ambientSounds` | `true` | Chirps, hums, chimes, crackles, hiss. Client-side. |

## Recipes

All cheap, vanilla or Neroland Core ingredients only, and no progression gate.

| Block | Recipe | Makes |
| --- | --- | :-: |
| Circuit Plating | 8 Nero Alloy Hull around Redstone Dust | 8 |
| Holo-Grid Floor | 8 Starsteel Panel around Prismarine Crystals | 8 |
| Starfield Panel | 8 Void Crystal Hull around Glowstone Dust | 8 |
| Data Stream Panel | 8 Nero Alloy Panel around Lapis Lazuli | 8 |
| Aurora Glass | 8 Plasma Glass (NeroDecor) around an Amethyst Shard | 8 |
| Lumen Panel | 8 Glass around a Glowstone block | 8 |
| Void Rift | 2 Void Crystal Shards + Ender Pearl + Obsidian (shapeless) | 2 |
| Void Crystal Lattice | Void Crystal Shards and Amethyst Shards in a 2×2 checker | 4 |
| Capacitor Bank | Starsteel Panels, Redstone and a Plasma Glass block | 2 |
| Xenobloom | 4 Cobbled Deepslate + Glow Berries (shapeless) | 4 |
| Ion Vent | Iron Bars around a Nero Alloy Panel (X shape) | 4 |
| Plasma Conduit | Starsteel Ingot, Plasma Glass block, Starsteel Ingot (column) | 4 |
| Starsteel Pillar | 2 Starsteel Hull (column), or 1 Starsteel Hull in a stonecutter | 2 / 1 |
| Fusion Lamp | 4 Starsteel Panels around a Redstone Lamp | 1 |
| Slabs | 3 in a row, or the stonecutter | 6 / 2 |

## For builders

- **Operations room:** Data Stream Panel walls, Holo-Grid Floor, a Capacitor Bank row and Lumen
  Panel Slabs overhead.
- **Observation deck:** Starfield Panel on the ceiling, Aurora Glass windows, Starsteel Pillars.
- **Engine bay:** Plasma Conduit runs along the walls, Ion Vents in the floor, Circuit Plating.
- **Xeno-lab:** Xenobloom planters, a Void Crystal Lattice feature wall and one Void Rift as a
  centrepiece.

## See them in the gallery

In creative, `/nerodecor gallery` now adds a **Luminous row** behind the paint swatches: each
connected surface as a 3×3 wall (so the seamless bezel shows), both pillars as linked 3-high
columns, the Fusion Lamp lit on a redstone block, and the other Luminous blocks and slabs on a
plinth. `/nerodecor gallery clear` removes it with the rest of the gallery.

## For contributors

- Blocks: `registry/LuminousBlocks.java`; classes in `content/block/` (`GlowDecorBlock`,
  `ConnectedGlowBlock`, `DecorPillarBlock`, `FusionLampBlock`); ambience in `content/fx/AmbientFx`.
- Sounds: `registry/ModSounds.java`, audio synthesised by `tools/gen_sounds.py` (no samples).
- Textures: `tools/gen_luminous.py` (deterministic; `--check` for drift, `--preview` for a contact
  sheet). JSON: the `LUMINOUS` spec in `tools/gen_resources.py`. All run via `./gradlew genAssets`.
