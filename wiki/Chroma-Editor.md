# Chroma Hub Editor

Using a **Chroma Hub** with an empty hand opens the hub screen. You need at least the **Viewer** role
on the hub. Sneak-use still toggles auto-adopt (admins only).

The screen edits the hub's network: the blocks it links, their zones, its palette, its scene slots
and who may use it. The server re-checks every change, so the screen never lets you do more than your
role allows.

## Roles

| Role | Can |
| --- | --- |
| Viewer | Open the screen and look around |
| Painter | Paint blocks and palette entries, assign zones, play and store scenes |
| Admin | Everything above, plus rename zones, add zones, change channels and auto-adopt; sees the trusted list |
| Owner | Everything, plus grant and revoke roles |

## Layout

- **Header**: whether the hub is placed, linked blocks against the server's cap, zones against the
  cap, the active scene and your role.
- **Colour picker** (right): a saturation/value square, a hue bar, the **light level** (0-15, the
  light the block gives off), a hex box and two colours, **A** and **B**. Click B to swap them.
  Gradients and quick effects run from B to A. The scene composer's colour buttons read A (and B).
- **Footer**: hints and results, **Undo** / **Redo** (32 steps, also Ctrl+Z and Ctrl+Y),
  **Revert** and **Apply**.

Colour and zone-assignment edits stay on your screen until you press **Apply**. Apply sends them as
one batch, or as several batches of up to 512 changes each for very large edits. Renaming, channels,
roles and scene actions are sent straight away.

## Tabs

### Zones

Lists the hub's zones (up to 32): the colour most of the zone's blocks show, the zone's effect channel
(0-15) and its block count. Select a zone to:

- rename it, or add a new zone with the name in the box (admins),
- move it to another channel with **-** and **+** (admins),
- **Paint zone with A**. When the zone's blocks share one palette entry that nothing else uses, the
  entry itself changes, which costs a single change.

### Palette

Shows every palette entry (up to 256). Click one to load it into the picker, then use **Set entry**:
every block that uses the entry changes with it. Entries with a dark corner are not used by any block,
and new colours reuse them first. Entry 0 is never reused, because newly linked blocks start on it.

### Scenes

The tab has two views, switched with **Slots** and **Compose** at its top right.

**Slots** lists the hub's eight scene slots:

- The playing slot is marked **LIVE** (a green badge and a green edge on its row).
- **Play** starts any other slot that holds a scene. **Static colours** stops the scene.
- **Rename** names the selected slot.
- **Copy** puts the slot's share code (`ND1-...`) on the clipboard. **Import** stores the code in the
  box, or the clipboard when the box is empty, in the selected slot.
- **Quick effect**: pick an effect and a period, then **Store effect**. This puts that effect, from
  colour B to colour A, on every zone in the selected slot.

**Compose** builds a scene zone by zone. Pick a zone with **<** and **>**, then choose what it plays:

- **Effect**: a type (pulse, breathe, chase, axis wave, ripple, rainbow, sparkle, sky mirror), its
  **period**, **amplitude**, **axis**, **wave** length and sparkle **density**. **Colours from picker**
  sets the zone's two colours to picker A and picker B.
- **Timeline**: up to 32 keys. Each key has a time (in ticks, shown in seconds), a colour and an easing
  (linear, ease in, ease out, ease in-out or step) that sets how the colour travels from the previous key.
  Click a key to load its colour into the picker, then **Set key colour to picker A**. **+ Key** adds a key
  after the selected one in picker A's colour, **Remove** deletes it, and **Up** / **Down** swap its colour
  and easing with its neighbour (the times stay in order). Move a key's time with **-** / **+** (1 tick) or
  **<<** / **>>** (10 ticks). Set the timeline's **length** and whether it **loops**; a looping timeline blends
  its last key back into the first.

**Copy to all** gives every zone the selected zone's program (each zone keeps its channel). The **preview**
under the editor runs the same code the blocks run: the top strip is one cycle at the hub (a white marker
shows now), the bottom strip is a row of blocks along the axis right now, and the square on the right is the
zone live.

At the bottom, name the scene, pick a slot with **<** and **>**, and press **Store scene**. **Load** brings the
slot's scene back into the composer to edit it.

The screen checks each scene against the photosensitivity flash limit before sending it, and the server
checks it again. A refused scene shows a red banner over the preview (and over the slot list) until you change
it; slow it down or soften the colours.

### Access

Admins and the owner see the trusted list. The owner can grant **V**iewer, **P**ainter or **A**dmin,
or remove access with **x**. New players are picked from the players online right now. Names on this
tab come only from your own game's player list. The server stores player ids and roles, never names,
so trusted players who are offline show as "Offline player".

The auto-adopt toggle is here too.

### 3D View

A rotatable view of the hub's volume. Linked blocks are drawn as plain coloured cubes, with the
volume's floor grid and outline behind them.

| Control | Action |
| --- | --- |
| Right-drag | Orbit |
| Middle-drag | Pan |
| Mouse wheel | Zoom (over the view) or move the Y slice (elsewhere) |
| Y slice bar (right edge) | Hide blocks above a height, to reach inner layers |
| Frame | Recentre on the hub's blocks |

Tools:

- **Orbit**: drag with the left button to orbit.
- **Brush**: click or drag across blocks.
- **Box**: click two corners. Every linked block between them, at or below the slice, is changed.
- **Fill**: flood fill through touching blocks that match: any linked block, the same zone, the same
  colour, or both.
- **Gradient**: click a start block and an end block. The start block's zone fades from B to A along
  that line.
- **Axis**: click a block. Its zone fades from B to A along X, Y or Z.
- **Pick**: copies a block's colour into the picker.

**Paint colour / Assign zone** switches Brush, Box and Fill between painting colour A and moving
blocks into the zone selected on the Zones tab.

Gradients are interpolated in OKLab and snapped to 16 steps. A gradient therefore adds at most 16
palette entries.

Above 2,000 visible blocks, the view switches to **plain cubes**: one flat square per block. This keeps
the screen responsive on large hubs. Use the Y slice to get the detailed view back.

The view is drawn by the screen itself, the same way on every supported Minecraft version and loader.
It does not depend on the world renderer.

## Privacy

The screen receives only hub-relative block offsets, colours, zone names and scene slots. Owners and
admins also receive the trusted players' ids and roles. Nothing the screen does is logged. See
[Privacy](Privacy.md).
