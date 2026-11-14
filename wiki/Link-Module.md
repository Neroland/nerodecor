# Companion Link Module

NeroDecor publishes two things to NeroLink companion clients (the phone app that talks to your
server):

- a **public catalogue**: the shared Neroland palette and every NeroDecor block, the same for
  everyone;
- a **Chroma remote**: the Chroma Hubs you own or are trusted on, their zones and scenes, and four
  remote controls (apply a scene, recolour a zone, start or stop the animation, set the brightness).

> **Requires:** Neroland Core (already a hard dependency) and the separate **NeroLink** bridge mod on
> the server. NeroDecor itself ships no server and no networking. It registers what it can show with
> Core, and the bridge serves it. With no bridge installed, this costs one registry entry.

This page documents **link schema 2** (NeroDecor 0.8.0). Schema 1 had only the two catalogue
sections and no actions.

## Turning it off

```properties
# config/nerodecor.properties
enableLinkModule=false
```

Server-authoritative: the server's value wins, because whether a server exposes a mod to companion
clients is the server's call. With it `false` the module is never registered and a companion client
lists NeroDecor as absent. Turning Chroma itself off (`chroma.enabled=false`) leaves the catalogue
up, reports `chroma_enabled: false`, lists no hubs and refuses every action.

## Sections

Every section starts with `schema_version` (`2`). Ids are `snake_case` because the bridge only
accepts lower-case path and topic segments.

### `palette` (public)

The colour vocabulary. Optional `q` filter (case-insensitive substring of id or name), at most 512
rows per list.

- **`finishes`**: Neroland Core's shared palette export, verbatim (id, name, packed and `#RRGGBB`
  colour, channels, emissive flag and light, material tag or `null` for an accent).
  `finishes_registered_by_mod` is `0`: NeroDecor consumes the palette and adds no finishes.
- **`colors`**: the values the `COLOR` blockstate property can take: sixteen dye-aligned accents
  plus `NATURAL` ("no paint", white tint).
- **`legacy_neon`**: each pre-0.8.0 `neon_<colour>` id with the block it became
  (`nerodecor:chroma_cube`) and its colour.

### `blocks` (public)

Every visible NeroDecor block in registration order (the creative tab's order), at most 512 rows,
optional `q` filter. The twelve hidden `neon_<colour>` shims are **not** listed; `chroma_cube` and
`chroma_hub` are. Per block:

| Field | Meaning |
|---|---|
| `id`, `name`, `translation_key` | Registry id, readable name, lang key |
| `model` | `cube`, `slab`, `stairs`, `wall`, `pane`, `pillar` (axis-linked columns and beams) or `custom` (a bespoke model: consoles, doors, pipes, furniture, fittings) |
| `shape` | The schema-1 field, kept for older clients: as `model`, but `block` for anything bespoke |
| `chroma` | Whether a Chroma Hub can drive it (the data-driven `#nerodecor:chroma_capable` tag) |
| `paintable` | Whether it takes paint (has the `COLOR` property) |
| `emissive` | Whether its default state gives off light (the Chroma Cube always counts) |
| `collection` | `decor` (paintable families), `chroma` (hub and cube), `luminous` (other light sources) or `collections` |
| `family`, `family_name`, `ctm_style` | Paintable families only: CTM family and connected-texture style |

Plus `blocks_registered`, `truncated`, and the two client-local render toggles
(`connected_textures`, `emissive_rendering`). A new block adds a row; that is not a schema change.

### `chroma_hubs` (yours only)

The hubs **you own or are trusted on** (viewer or higher), owned hubs first, at most 64:

| Field | Meaning |
|---|---|
| `hub` | The hub's network id, 16 hex digits. Every other Chroma section and action takes it |
| `name` | `Chroma Hub` and the id's last four digits (hubs have no player-set name yet) |
| `role` | Your role: `owner`, `admin`, `painter` or `viewer` |
| `can_control` | Painter or higher: you may use the actions below |
| `active` | The hub block is placed (a broken hub keeps its data but drives nothing) |
| `blocks`, `zones`, `scenes` | Linked blocks, zones, filled scene slots |
| `active_scene`, `active_scene_name`, `animating` | The playing slot (`-1` = static colours) and its name |
| `trigger` | `manual`, `redstone` or `daylight` |
| `brightness` | The hub's static brightness, 0–15 (its brightest colour), or `null` with no colours |
| `members` | **Counts only**: `admin`, `painter`, `viewer`. Never who |

Also `chroma_enabled`, `hubs_visible` and `truncated`.

### `chroma_zones?hub=<id>` (yours only)

The hub's zones: `zone` (index), `name`, `channel`, `colour` (`#RRGGBB`, the colour most of the
zone's blocks show, or `null` for an empty zone), `brightness` and `blocks` (count).

### `chroma_scenes?hub=<id>` (yours only)

The eight scene slots: `slot`, `name` (or `null`), `filled`, `active`; plus `active_scene`,
`animating` and `trigger`.

For the zone and scene sections, a hub that does not exist and a hub you may not see give the
**same** empty answer, so the sections cannot be used to probe for other players' hubs.

## Scoping and privacy

- **Only your hubs.** A hub appears only when you own it or are on its trusted list. Erasing a
  player orphans their hubs, and an orphaned hub is visible to nobody.
- **No coordinates.** No anchor, no volume, no block position and no dimension, anywhere. A hub is
  named by its random network id.
- **No roster.** No other player's name or UUID. Members are three counts. Your own role is the only
  role named.
- **Nothing new is stored.** The module reads what the Chroma Hub already holds and already shows the
  same players in game. In memory only, it keeps one rate-limit bucket per requester and the last
  scene each hub played (for the animation switch). Nothing is logged with a player, position or
  scene text.

See [PRIVACY.md](https://github.com/Neroland/nerodecor/blob/main/PRIVACY.md) for what a hub stores.

## Actions

All four need **painter or higher** on the hub, the player **online** (the world-protection checks
need the player), and pass through **the same code as the Chroma Hub GUI**. The remote can never do
something the GUI would refuse.

| Action | Body | What it does |
|---|---|---|
| `apply_scene` | `hub`, `slot` (0–7) | Plays a scene. The photosensitivity flash clamp applies; the hub switches to the manual trigger, as the GUI's scene buttons do |
| `toggle_animation` | `hub` | Stops the playing scene (static colours), or resumes the last scene played (else the first filled slot) |
| `set_zone_colour` | `hub`, `zone`, `colour` (`#RRGGBB`) | Sets every block of the zone to the colour, at the hub's current brightness. Every block must pass the world guard (game mode, spawn protection, world border, claim mods) **before** anything changes, or nothing does |
| `set_brightness` | `hub`, `value` (0–15) | Sets the brightness of every colour in the hub's palette |

A successful action answers with the hub's `chroma_hubs` row.

### Limits

- **One action per second per player.** A second action within the second is refused with
  `rate_limited`. The token is spent even when the action is then refused, so the limit cannot be
  probed for free.
- The shared Chroma edit limiter (`chroma.editsPerSecond`, `chroma.blockOpsPerMinute`) is charged
  exactly as for the GUI.
- Request bodies over 1 KiB are refused.

### Refusals

A refusal's message is a short token the app turns into a sentence:

| Token | Meaning |
|---|---|
| `rate_limited` | More than one action a second |
| `not_owner` | Not your hub, or no such hub (deliberately the same) |
| `role` | You are a viewer; painters and up only |
| `protected` | A block is protected (claim, spawn protection, game mode, world border) |
| `flash_rejected` | The scene flashes too fast over a large area |
| `bad_slot`, `bad_zone`, `bad_op`, `bad_params` | Empty slot, unknown zone, out-of-range value, malformed body |
| `inactive` | The hub block is not placed |
| `not_linked` | Nothing to change (an empty zone, a hub with no colours) |
| `offline` | You must be in game |
| `chroma_disabled` | Chroma is off on this server |

## Live events

Published on topic `nerodecor.chroma_hubs`, so a client subscribed to the hub list gets the section
as its first frame and these as deltas. Each payload has `type`, `hub` and `name`:

| `type` | Extra fields | When |
|---|---|---|
| `scene_changed` | `slot` (`-1` = static), `scene`, `by_trigger` | The playing scene changed (GUI, remote, Scene Chip or trigger) |
| `trigger_fired` | `trigger` (`redstone`/`daylight`), `slot` | A trigger switched the scene; at most one per hub per second |
| `hub_broken` | | The hub block was removed |

Every event is **player-scoped**: one copy to the hub's owner and one to each trusted member. Never
a broadcast, never to anyone else, and no position in any payload.

## Display surfaces: not yet

Neroland Core's **display surface** API (text, an icon and a status colour pushed to a block by
another mod) is not wired up by this module yet.

## See also

- [Home](Home.md)
- [Privacy & telemetry](https://github.com/Neroland/nerodecor/blob/main/PRIVACY.md)
