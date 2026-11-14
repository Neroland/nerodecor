# Showroom

`/nerodecor gallery` builds a walkable showroom: a corridor with eight staged bays that show the 0.8.0
collections the way they are meant to be built. `/nerodecor gallery grid` builds a catalogue of every block, one
row per collection. `/nerodecor capture` takes screenshots of either from fixed camera angles. All of this is for
creative worlds, screenshots and modpack previews.

## Commands

| Command | What it does |
| --- | --- |
| `/nerodecor gallery` | Builds the showroom 3 blocks east and 3 blocks south of you |
| `/nerodecor gallery clear` | Removes the last showroom built in this dimension since the server started. With none remembered it refuses and changes nothing |
| `/nerodecor gallery grid` | Builds the block grid 3 blocks east and 3 blocks south of you |
| `/nerodecor gallery grid clear` | Removes the last grid built in this dimension since the server started. With none remembered it refuses and changes nothing |

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

## The block grid

`/nerodecor gallery grid` lays out every NeroDecor block you can obtain (the twelve hidden legacy neon blocks are
left out; the Chroma Hub and Chroma Cube are in), one row per collection, north to south:

Original hull, panels and glass; Luminous; Chroma Lights; Bulkhead; Holo; Command; Lab and Habitat; Gantry;
Conduits; Seven Worlds; Ancients; Greebles; Chroma core. Then a **paint swatch** row (a Nero-alloy hull block in
each of the 17 paint colours) and a **Chroma demo** strip: a hub linked to the cubes, a light pillar, a wall light
strip, neon tubes and a cube panel beside it, playing a slow cyan-to-violet wave.

- Each row has a dark deepslate backing wall on its north side, the exhibits in front of it, and a four-block
  walkway to the south. Stand in a walkway and look north. A holo sign at the west end names the collection; a
  collection too long for one row continues on a second ("ORIGINAL HULL PANEL GLASS 2"). The rows are split into a
  west and an east half with an aisle between them; the paint row ends the west half and the Chroma demo the
  east half.
- Exhibits stand two blocks apart, each in a sensible default state: wall-mounted pieces hang on the backing wall,
  doors are closed, the blast door is placed whole, connecting blocks (panes, walls, pillars, strips, pipes,
  consoles, racks) are shown as a run of three so the connection reads, and connected surfaces as a 3 x 2 panel.
- The floor is deepslate tiles with a polished blackstone line under the exhibits and a ring at the edge; hidden
  light blocks over the walkways light the non-glowing blocks without washing out the glows.
- The list comes from the block registry when the command runs, so a new block shows up on its own. A block that
  belongs to no collection is still shown, in an extra **UNSORTED** row, and the command reports it as an error.
- The footprint depends on the number of blocks (about 128 x 55 in 0.8.0) and runs from one block below your feet to
  8 above. Like the showroom it is cleared first (terrain included, no drops), rebuilding in place gives the same
  result, and `gallery grid clear` empties it. The Chroma demo's hub belongs to whoever ran the command.

## Screenshots: /nerodecor capture

`/nerodecor capture [showroom|grid|all|here] [count]` is a client command that saves screenshots from fixed
camera angles:

| Target | Views |
| --- | --- |
| `showroom` | A shot down the corridor, then for each bay a low, wide hero shot from the corridor and a detail shot inside (17) |
| `grid` | Three high overviews (from the south and from each side), a low pass along the first row, the paint swatches and the Chroma demo (6) |
| `all` (default) | Both of the above |
| `here` | Your current view; `count` frames a second apart |

`count` limits the number of views (for `here`, the number of frames). For each view the command hides the HUD,
sets the view's field of view, moves you there, waits a few seconds for chunks and animations to settle, and saves
`screenshots/nerodecor/<yyyyMMdd-HHmmss>_<view>.png` in the game folder. A line in chat reports each view. At the
end your HUD, field of view, game mode, position and facing, and the time of day are put back.

- `showroom`, `grid` and `all` need **operator rights**: you are moved with `/nerodecor capture goto <view>`, which
  only finds the showroom or grid built in the current dimension since the server started. Build it first; if none
  is remembered, those views are skipped with a message.
- As an operator the capture switches you to spectator mode (no falling, no hand in shot) and sets the time to
  night so glows read, then restores both. Without operator rights `here` captures the world as it is.
- The screenshot names hold only the time and the view, never a player name. Nothing is uploaded.
- It works on every supported version and loader (Fabric, NeoForge and Forge, 1.21.1 to 26.3). There is no
  high-resolution option: use a larger game window for bigger shots.

## For contributors

The bays are data: each one is a set of plan-view layers and a character legend in
`common/src/main/java/za/co/neroland/nerodecor/command/showroom/ShowroomScenes.java`. Change a layout and rerun the
command. The `showroom_builds` GameTest fails on an unknown block, property or value, or a block that cannot
stand where the layout puts it, and checks that rebuilding and clearing leave nothing behind.

The grid lives in `command/showroom/BlockGrid.java`: a block-to-collection map and a staging table (single, run of
three, 3 x 2 panel, column, wall-mounted, multiblock). The `block_grid_builds` GameTest fails when any visible
registered block has no collection or is missing from the built grid, when a NeroDecor block it does not expect
appears, or when rebuilding or clearing leaves anything behind. Give a new block a collection there (and a staging
line if the default single block does not suit it).

The camera angles are data in `command/showroom/CameraPresets.java` (offset from the showroom or grid origin, yaw,
pitch, field of view). The Fabric client game test shoots the grid and the showroom from the same presets at night,
with the same names, and runs a short `capture grid 2`.
