# Chroma Animations

A Chroma Hub can do more than hold one colour per block. Since 0.8.0 "Chroma" each hub stores up to
**eight scenes**, plays one of them at a time, and can switch between them on its own from redstone or
the time of day. This page covers how scenes play, what switches them, the Scene Chip, and the options
that keep animations comfortable and cheap.

## Scenes and slots

A scene gives each **zone** of a hub (see the [Chroma Hub Editor](Chroma-Editor.md)) either a
procedural effect (pulse, breathe, chase, wave, ripple, rainbow, sparkle, sky mirror) or a keyframe
timeline. Blocks in a zone the scene does not mention keep their static colour.

- A hub has **8 scene slots**. One slot plays at a time, or none ("static": every block shows its
  committed colour).
- Saving or loading a scene needs the **Painter** role or higher. Changing the trigger needs **Admin**.
- Scenes hold only a name, zones and effect settings: no player names, no ids, no coordinates.

### How a scene plays

Every effect is a pure function of the time since the scene started, the block's position relative to
the hub, and a per-hub seed. The server only stores which slot plays and the game tick it started at;
each client works the colours out itself. That means:

- **No per-block or per-tick traffic.** Starting or switching a scene is one small packet.
- **Everyone sees the same thing at the same time**, because every client uses the same game time.
- **Chunks are never rebuilt for animation.** The client draws the animated faces of a hub's blocks in
  one extra glowing pass on top of the normal world; when that pass is off or skipped, the blocks simply
  show their static colour.
- **Light never follows animation.** A Chroma Cube's light level always follows its static brightness.

## Triggers

Each hub has one trigger:

| Trigger | What picks the slot |
| --- | --- |
| Manual (default) | Whoever activates a slot in the editor or loads a Scene Chip. |
| Redstone | The strongest redstone signal going into the hub (0-15). By default strength 0 shows the static colours (no scene) and strength N plays slot N, up to slot 8 (strength 8 or more plays slot 8). The mapping can be changed per hub. |
| Daylight | The time of day: dawn (23000-999), day (1000-11999), dusk (12000-12999) and night (13000-22999). By default dawn, day, dusk and night play slots 1, 2, 3 and 4. |

An empty slot always means static. Activating a slot by hand switches the hub back to the manual
trigger.

### Music

Note blocks and jukeboxes within 8 blocks of a hub make its animation react on your screen: a note
block that is powered kicks a short brightness pulse, and a jukebox with a disc adds a slow swell.
This happens only on the client (nothing is sent to the server), pulses are limited to 2.5 per second,
and it never changes light levels. Note blocks played by hand (not powered) are not detected. Turn it
off with `chroma.musicReactive`.

## Photosensitivity

The server refuses to save or play any scene that flashes more than **3 times a second over a large
area** (the same rule broadcast guidelines use). You will see "That scene flashes too fast for
photosensitive players" and the scene is not stored. Scenes restored from an old world are checked
again before they play.

## Scene Chip

The Scene Chip carries one scene. Its stripe shows the scene's main colour, and its tooltip shows the
scene name and how many zones it uses.

- **Use on a hub**: loads the chip's scene into the hub's first empty slot (or the playing slot when
  all eight are full) and plays it. Needs the Painter role; the scene is checked and must pass the
  photosensitivity rule.
- **Sneak-use on a hub**: copies the scene the hub is playing onto the chip (Viewer role is enough).
- The chip stores only the scene itself (at most 2 KB, with a checksum). A damaged or edited chip is
  refused. Chips carry no author or player information, so they are safe to trade.
- Share codes (`ND1-...`) for copying a chip's scene as text are handled in the hub editor.

## Options

Client options (each player's own choice, in `nerodecor.properties`):

| Key | Default | Effect |
| --- | --- | --- |
| `chroma.reducedMotion` | `false` | Plays effects four times slower and at half intensity, with less sparkle. |
| `chroma.disableAnimation` | `false` | Shows static colours only on this client. |
| `chroma.musicReactive` | `true` | Note blocks and jukeboxes pulse nearby hubs. |

Server options:

| Key | Default | Effect |
| --- | --- | --- |
| `chroma.animations` | `true` | Server-wide kill-switch: when off, every hub shows its static colours. Stored scenes are kept. |
| `chroma.enabled` | `true` | Turns Chroma hubs off entirely (see [Chroma Hub Editor](Chroma-Editor.md)). |

## Performance

The animation pass has fixed budgets on each client:

- up to **4,096** animated blocks per hub;
- up to **32** hubs drawn at once, nearest first;
- about **0.5 ms** per frame for all hubs together. When a frame goes over, the farthest hubs first
  update their colours every other tick, then stop animating (they show their static colours) until
  there is room again.

Only faces you could actually see are drawn, and colours are worked out once per game tick, not per
frame.

## Version notes

- Minecraft 26.x draws translucent blocks (such as Chroma glass) in a translucent pass. On 1.21.x the
  animation pass draws every animated face as cut-out, so a translucent block looks more solid while a
  scene plays.
