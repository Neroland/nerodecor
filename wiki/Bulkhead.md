# Bulkhead

Doors, hatches, frames and glazing for airlocks and hulls. Doors are animated by a block entity: they slide,
iris or roll open over a few ticks and draw their moving parts only while they move.

| Block | What it is | Placement and properties | Use |
| --- | --- | --- | --- |
| Sliding Door | A 1x2 door whose leaf slides sideways | Faces the way you walk through; two doors placed side by side pair up and open together | Use or redstone opens and closes |
| Iris Hatch | A round hatch whose blades iris open | Mounts on a floor, wall or ceiling | Use or redstone |
| Blast Door | A 3x3 door with two heavy sliding panels | Placed from the bottom centre; needs a clear 3x3 | Use or redstone on any part |
| Pressure Trapdoor | A heavy trapdoor | Top or bottom half, like a vanilla trapdoor | Use or redstone |
| Airlock Frame | A chevron frame block | Connects, so a wall of frames keeps one clean rim round any opening | - |
| Door Status Light | A small lozenge light | Mounts on any face; Chroma sets its colour | - |
| Access Pad | A keypad panel | Mounts on any face; decorative | - |
| Viewport Frame | A window block with its own glazed centre | Connects, so the frame stays on the outside of a window group | - |
| Porthole | A round window | Any axis | - |
| Canopy Glass | A 45-degree sloped pane | Faces its low edge; top or bottom half; joins along the ridge | - |
| Shutter | A roller shutter | Runs along x or z; a vertical stack rolls as one | Use or redstone |
| Smart Glass | Glass that turns frosted | Connects; Chroma tints the frosted state | Use, or a rising redstone signal, toggles clear and frosted |

## Using them

- An airlock: two pairs of sliding doors (or a sliding door and a blast door) with airlock frames around them,
  door status lights beside each door and an access pad. The showroom's airlock bay shows one.
- Doors respond to redstone like vanilla doors: a powered door opens, an unpowered one closes.
- Door Status Light and Smart Glass are Chroma blocks: link them to a hub to colour them.

## Recipes

Iron and redstone for most doors, Nero Alloy for the blast door, glass for the glazing. Full list:
[Recipes](Recipes.md#bulkheads--glazing).
