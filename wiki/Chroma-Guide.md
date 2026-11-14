# Chroma Guide

Chroma is NeroDecor's lighting system. A **Chroma Hub** controls the colour of every block linked to it, can
animate them with scenes, and can switch scenes from redstone, the time of day or music. This page walks
through it from the first hub to a remote control on your phone. The details live on the linked pages.

## 1. Place a hub

Craft a **Chroma Hub** and place it. The hub starts a network that you own. It controls blocks inside its
**volume**, a box centred on the hub (48 wide, 32 high and 48 deep by default). One hub links up to 4,096
blocks, and each player can own up to 8 hubs (server settings can change both).

Breaking the hub stops it controlling its blocks: they show their own colour again. Placing the hub item
again re-links them. A network whose hub stays broken is deleted after 90 days.

## 2. Link blocks with the linker

Any block in the `nerodecor:chroma_capable` tag can be linked: the [Chroma Lights](Chroma-Lights.md), the
Chroma Cube, glass, Luminous lights and every other block marked as a Chroma block on its collection page.

With the **Chroma Linker**:

- **Sneak-use the hub** to bind the linker to it.
- **Use a block** to link or unlink it.
- **Use in the air** to switch box mode on; then use two blocks to link everything capable between them.
- **Sneak-use a block** to flood-link every capable block touching it.

A block can belong to one hub only. Linking respects build permission, spawn protection, the world border and
claim mods. Turn on **auto-adopt** (sneak-use the hub, admins only) to link Chroma blocks you place inside the
volume automatically.

## 3. Zones and colours

Use the hub with an empty hand to open the [Chroma Hub Editor](Chroma-Editor.md).

- **Zones** group blocks (up to 32). New blocks join the default zone. Assign blocks to zones from the 3D
  view, then colour or animate a whole zone at once.
- **Colours** are picked with the HSV picker, a hex box and a light level (0-15). A Chroma Cube's light
  follows the brightness of its colour; animations never change light.
- The **palette** holds up to 256 colours. Change a palette entry and every block using it changes with it.
- Edits stay on your screen until you press **Apply**.

## 4. Scenes

A hub has eight **scene slots**. A scene gives each zone an effect (pulse, breathe, chase, axis wave, radial
ripple, rainbow, sparkle, sky mirror) or a keyframe timeline of up to 32 colours, each with its own time and
easing. One slot plays at a time, or none (static colours).

Author scenes on the editor's Scenes tab: **Store effect** puts one effect on every zone, and **Compose** lets
you give each zone its own effect or timeline, with a live preview, then store it in a slot. See
[Chroma Hub Editor](Chroma-Editor.md#scenes) and [Chroma Animations](Chroma-Animations.md).

## 5. Triggers

Each hub has one trigger that picks the playing slot:

- **Manual**: whoever presses Play, loads a Scene Chip or uses the NeroLink remote.
- **Redstone**: signal 0 shows static colours, signal 1 to 15 picks a slot.
- **Daylight**: dawn, day, dusk and night each play their own slot.
- **Music**: powered note blocks and jukeboxes near a hub make it pulse, on each player's own screen only.

## 6. The editor's 3D view

The 3D View tab shows the whole network as cubes you can orbit, pan, zoom and slice. Paint with the brush,
box, fill, gradient and axis tools, pick colours with the eyedropper, and move blocks between zones. See
[Chroma Hub Editor](Chroma-Editor.md).

## 7. Scene Chips and share codes

- A **Scene Chip** stores one scene. Sneak-use a hub to copy its playing scene onto the chip; use the chip
  on another hub to load and play it.
- A **share code** (`ND1-...`) is the same scene as text. Copy and Import it on the Scenes tab, and paste it
  in chat, on a forum or in a modpack guide.
- Chips and codes carry no author or player information.

## 8. Roles

The owner can trust other players as **Viewer** (look), **Painter** (paint, zones, scenes) or **Admin**
(also names, channels and auto-adopt), up to 64 players, from the Access tab.

## 9. NeroLink remote

With the NeroLink bridge mod and its companion app, you can see your hubs (and hubs you are trusted on), play
a scene, stop or resume animation, set a zone's colour and set the brightness from your phone while you are in
the game. The remote goes through the same checks as the editor. See [Companion Link Module](Link-Module.md).

## 10. Privacy and photosensitivity

- **Photosensitivity.** The server refuses any scene that flashes more than three times a second over a large
  area, and says why. Each player can turn on **reduced motion** (slower, gentler effects) or turn animation
  off (`chroma.reducedMotion`, `chroma.disableAnimation` in `nerodecor.properties`).
- **Privacy.** A hub stores its owner's and trusted players' ids and roles, never names. Erasing a player
  through Neroland Core removes them from every trusted list and orphans their hubs, which operators can
  reclaim. `/nerodecor privacy export` gives you a copy of your own hub data. See [Privacy](Privacy.md).

## See also

- [Chroma Cube, Paint Gun and the neon fold](Chroma-Cube-And-Paint-Gun.md)
- [Showroom](Showroom.md): the Chroma corridor bay shows a linked hub playing a scene.
