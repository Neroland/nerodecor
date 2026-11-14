# Showroom

`/nerodecor gallery` builds a walkable showroom: a corridor with eight staged bays that show the 0.8.0
collections the way they are meant to be built. It is for creative worlds, screenshots and modpack previews.

## Commands

| Command | What it does |
| --- | --- |
| `/nerodecor gallery` | Builds the showroom 3 blocks east and 3 blocks south of you |
| `/nerodecor gallery clear` | Removes the last showroom built in this dimension since the server started. With none remembered it refuses and changes nothing |
| `/nerodecor gallery grid` | The old flat layout: every NeroDecor block in a grid, a paint row and a Luminous row |
| `/nerodecor gallery grid clear` | Removes the grid |

All four need **operator rights** (permission level 2); creative mode alone is not enough, because the
showroom replaces a large box of blocks and removes any Chroma hub network whose hub stood in that box,
whoever owns it. `clear` reports how many blocks and hub networks it removed. To build somewhere exact, run it
positioned: `/execute positioned 100 64 200 run nerodecor gallery`.

## The footprint

- 97 blocks along x, 13 along z, and from one block below your feet to 10 above. The command clears this box
  first (terrain included, no drops), so stand where you do not mind losing the ground.
- The edge of the footprint is a ring of polished blackstone in the floor.
- Running the command again in the same place rebuilds it identically. A Chroma hub inside the box is removed
  together with its network, so rebuilding never piles up hubs.
- `clear` empties the whole box, floor included, and leaves air.

## The bays

Walk the corridor east. Each bay has a holo sign with its number and name on a plinth at its front right (its west corner).

| Bay | Shows |
| --- | --- |
| 1 Reactor Hall | Starsteel hull walls ribbed with plasma conduits and a pipe band; a plasma conduit core in a ring of capacitor banks and coolant tubes, hazard stripes, valves and gauges, ion vents, radiation placards, cage lamps |
| 2 Observation Deck | A canopy-glass roof, viewport ribs down both sides and a wall of viewport frames onto a starfield; railing, operator chairs and lab stools, planters, lockers, a holo table, light strips |
| 3 Command Bridge | Nero-alloy panelling with a wall screen on each flank and a ticker band, a 7x3 main screen, a console row, operator chairs, a holo projector, blinkenlight panels, step and door status lights, ceiling coffers |
| 4 Server Vault | Server racks lining both walls under a band of blinkenlights, rows of server and drive racks, patch panels, cable trays and hanging cables, a duct with a fan, maintenance panels, a deck sign |
| 5 Airlock Sequence | Starsteel bulkheads with riveted walls and hazard-striped kick plates; an inner pair of sliding doors (open, framed by hazard stripes), a chamber with status lights, an iris hatch and a pressure trapdoor, and a closed blast door to a Mars exterior |
| 6 Chroma Corridor | Dark void-crystal hull lit only by linked lights: wall light strips, light pillars, neon tubes, coffers, halo rings, edge trim and a framed Chroma Cube panel, all playing a slow cyan-to-violet wave down the corridor |
| 7 Seven Worlds Museum | Void-crystal gallery walls with sconces; one plinth per world: its globe on its base block, its other blocks behind, and one holo sign label each (back row on top of its column, front row on the floor in front) |
| 8 Ancient Vault | Brick walls set with glyph stones between pillars, arch lintels, a glyph cartouche and strip, and three dormant cores (the middle one lit, on a glyph pedestal that hides its power) |

The Chroma corridor's hub belongs to whoever ran the command, and counts toward their hub limit until the
showroom is cleared.

## For contributors

The bays are data: each one is a set of plan-view layers and a character legend in
`common/src/main/java/za/co/neroland/nerodecor/command/showroom/ShowroomScenes.java`. Change a layout and rerun the
command. The `showroom_builds` GameTest fails on an unknown block, property or value, or a block that cannot
stand where the layout puts it, and checks that rebuilding and clearing leave nothing behind. The Fabric client
game test screenshots each bay at night.
