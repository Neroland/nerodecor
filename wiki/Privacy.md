# Privacy

NeroDecor's blocks are decoration, but **Chroma hubs** (0.8.0) remember a little about players. This
page is the player and server-owner summary; the full disclosure, including the opt-out crash
reporting, is in [PRIVACY.md](https://github.com/Neroland/nerodecor/blob/main/PRIVACY.md).

## What a hub remembers

- The **owner's** player UUID.
- The UUIDs of **trusted** players and their role (viewer, painter, admin): at most 64 per hub, and
  only players the server knows.
- The **zone and scene names** players type.
- The hub's position, volume, linked blocks, palette and channels.

It is saved per dimension in `data/nerodecor/chroma_networks.dat` (Minecraft 26.x; on 1.21.x
`data/nerodecor_chroma_networks.dat`), with a last-known-good backup,
`data/nerodecor_chroma_networks_backup.dat`, next to it. Player names are never stored. Scenes,
share codes and Scene Chips carry **no author**.

## What reaches players' games

- **Trusted UUIDs and roles**: only the hub screen of the owner and admins.
- **Zone and scene names**: anyone with at least the viewer role who opens the hub screen.
- **The hub's position**, linked blocks, palette and playing scene: every player whose game tracks
  the hub's chunk, because their game needs it to draw the colours.

The owner's UUID is not sent to anyone. Hub-screen changes only work standing near the hub (its
volume plus 8 blocks, or within 16 blocks of it) and where the world's protection lets you build.

Through a NeroLink companion app you see only the hubs you own or are trusted on, with no
coordinates and no other players (members are counts per role), and painters and up can apply
scenes, recolour zones, toggle animation and dim the hub once a second while in game. See
[Companion Link Module](Link-Module.md).

## Signs and screens

Holo Sign, Wall Screen, Holo Projector and Holo Table text is stored in the block, in the world,
like vanilla sign text, and so is text other mods push to them. No author is recorded, and erasing a
player's data does **not** change it. Don't write personal information on signs.

**Scene Chips** are items in the world, so they keep their scene names after an erasure; whoever
holds one can overwrite or destroy it.

## Erasing a player

Chroma is part of Neroland Core's shared erasure (`/neroland data eraseme`, or an operator acting on
a request). For the erased player:

1. They are removed from every trusted list.
2. Each hub they owned is **orphaned**: owner cleared, trusted list emptied, zone and scene names
   reset to neutral defaults, auto-adopt off. Its blocks keep their colours.
3. Nobody can edit an orphaned hub. An operator can take it over with `/nerodecor chroma claim`
   while standing inside its volume.
4. Any export file is deleted, then every dimension's saved file **and** its backup are written to
   disk straight away (not at the next autosave). Each step runs even if another fails.

## Retention

| Config key | Default | What is deleted |
| --- | --- | --- |
| `chroma.orphanRetentionDays` | 30 days | Orphaned hubs nobody has claimed |
| `chroma.inactiveRetentionDays` | 90 days | Hubs whose hub block was broken and not re-placed |
| (fixed) | 7 days | Export files |

The two hub timers are separate; whichever applies first wins. A deleted hub's blocks go back to
their own painted colour. The sweeps run at server start and every 10 minutes, even with Chroma
switched off.

## Getting a copy of your data

Run `/nerodecor privacy export` (once a minute). The server writes
`nerodecor/exports/<your uuid>.json` in the world folder (owner-only on Linux and macOS; on Windows
the folder's normal permissions apply); ask the operator for it. It lists:

- the hubs you own, with their coordinates, zones, scenes (name and share code) and the players you
  trusted with their roles;
- the roles you hold on other players' hubs, without their coordinates or owner.

## Operator commands

| Command | Who | Does |
| --- | --- | --- |
| `/nerodecor privacy export` | Any player | Writes that player's own export file |
| `/nerodecor chroma claim` | Operators | Takes over the orphaned hub whose volume you stand in |

## Logs and crash reports

Logs never contain player UUIDs, names, coordinates, or zone, scene or sign text. Crash reports
mask UUIDs and coordinates, send no breadcrumbs, and can be switched off with
`telemetryEnabled=false` in `config/nerodecor.properties`.
