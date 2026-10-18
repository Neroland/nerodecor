# Companion Link Module

NeroDecor publishes a **read-only catalogue** to NeroLink companion clients — the phone app that
talks to your server. It lets you browse the shared Neroland palette and the full decor block set
without the game open: which neon colours exist, which shapes the panel family comes in, what hex
value `starsteel` actually is.

That is the whole of it. The module shows things; it does not change anything, and it holds no
information about you.

> **Requires:** Neroland Core (already a hard dependency) and the separate **NeroLink** bridge mod on
> the server. NeroDecor itself ships no server and no networking — it registers what it can show with
> Core, and the bridge serves it. With no bridge installed, this costs one registry entry.

## Turning it off

```properties
# config/nerodecor.properties
enableLinkModule=false
```

Server-authoritative: the server's value wins, because whether a server exposes a mod to companion
clients is the server's call (unlike `connectedTextures` and `emissiveRendering`, which are yours).
With it `false` the module is never registered and a companion client lists NeroDecor as absent. The
blocks themselves behave identically either way.

## What you can see

Both sections accept an optional `q` parameter — a case-insensitive substring matched against ids and
names — and both cap their output at 512 rows.

### `palette`

The colour vocabulary, from both ends of it.

- **`finishes`** — Neroland Core's shared palette export, verbatim: each finish's id, display name,
  packed sRGB value, a `#RRGGBB` string a colour picker can use directly, the individual red/green/
  blue channels, whether it reads as emissive and at what block light level, and the canonical
  material tag it derives from (`null` for a pure accent). Also `palette_api_version` and
  `finishes_available`.

  NeroDecor **registers no finishes of its own** — it is a consumer of the palette, not a
  contributor — so `finishes_registered_by_mod` is reported as `0` rather than left to be inferred
  from the namespaces.

- **`colors`** — NeroDecor's own paint vocabulary: the values the `COLOR` blockstate property can
  take. Sixteen dye-aligned accents whose RGBs mirror Core's `neroland:accent/*` set, plus
  `NATURAL`, which means "no paint — show the material's own tone" and is why its tint is white
  (white is the multiply identity, not a colour choice).

### `blocks`

Every registered decor block, in registration order — the same order the creative tab and datagen
use, so the app's list matches what you see in game. Per block:

- its registry id, a readable name and the translation key,
- its **shape**: `cube`, `slab`, `stairs`, `wall`, `pane`, `pillar`, or `block` for the rest,
- its **collection**: `decor` for the paintable families, `luminous` for the
  [Luminous collection](Luminous-Collection.md),
- whether it is **paintable** (the paintable families are; the Luminous blocks are not) and whether
  it is **emissive** (the neon family and every Luminous block),
- for the paintable families only, its **CTM family** (`nerodecor:hull`, `nerodecor:panel`,
  `nerodecor:glass`, `nerodecor:neon`) and its **connected-texture style** (`FULL`, `GLASS` or
  `STRIP`) — two faces connect only on the same family *and* the same painted colour.

The section also reports `blocks_registered` and the two client-local render toggles, so a client can
say "connected textures are off on this client" without guessing.

Adding a block to the mod adds a row here; that is not a schema change, and clients should treat the
list as one that grows.

## What you can do

**Nothing, deliberately.**

Everything NeroDecor can change is a block in the world: painting one, placing one, breaking one.
Doing any of that from a phone would let a player edit the world without being in it, and no amount
of gating turns "recolour that block over there" into a status operation. There is likewise nothing
to schedule, nothing to claim and nothing to acknowledge — so even the ecosystem's usual
`acknowledge_alert` would be an action with no alerts behind it.

So NeroDecor registers a snapshot provider and no action handler at all. Companion clients see the
module as read-only, and its action list is empty rather than padded.

## Display surfaces: not yet

Neroland Core also ships a **display surface** API — the seam through which a block can accept text,
an icon and a status colour pushed to it by another mod, so a hologram or a wall panel can show live
data. That is obviously where NeroDecor is going, and it is the reason Core's
`neroland:decor/dashboard_surface` tag exists.

It is not wired up here, because NeroDecor currently ships **no block entities at all**: every block
in the set, the Luminous collection included, is model-only, and a display surface needs somewhere to
store what was pushed to it. Holograms, signs, panels and racks arrive with those block entities in a later
build, and the surfaces come with them.

## Live events

**None.** A live delta exists to tell you something changed while you were away. NeroDecor's entire
state is "which blocks are registered", which never changes at runtime, and "what colour is that
block", which only changes because somebody was standing there with a paint gun. Neither is worth a
push notification, so nothing is published on Core's event bus.

## Privacy

There is essentially no privacy surface here, and that is worth stating rather than implying.

**NeroDecor stores no player-attributable data anywhere in the mod.** A block's paint colour is world
state — a blockstate property mirrored onto the item through the `nerodecor:color` data component —
and records nothing about who applied it. There is no owner, no history and no saved data of the
mod's own, which is also why NeroDecor is not registered with Neroland Core's shared data-erasure
hook: there would be nothing for it to erase.

Both sections are therefore **public content metadata, identical for every requester**: registry ids,
hex colours and enum names, all of it already visible to anyone who opens the creative tab. The
requesting player's id is read for nothing but a null check, which is the strongest form of the
own-data-only contract this API can have.

The mod's only outbound data of any kind is its opt-out crash telemetry — see
[PRIVACY.md](https://github.com/Neroland/nerodecor/blob/main/PRIVACY.md) for the full disclosure.

## See also

- [Home](Home.md)
- [Privacy & telemetry](https://github.com/Neroland/nerodecor/blob/main/PRIVACY.md)
