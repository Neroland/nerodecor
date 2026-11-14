# Holo

Holograms, signs and display stands. The projector, sign, globe and table have block entities that draw
their holograms; the rest are plain blocks with animated textures.

| Block | What it is | Placement and properties | Use |
| --- | --- | --- | --- |
| Holo Projector | An emitter puck that projects a light cone | Mounts on any face and projects the other way | Use switches it on and off |
| Holo Sign | A floating panel with two lines of text | Faces you when placed | Use opens the text editor (two lines, 32 characters each) |
| Arrow Sign | A floating chevron | Faces you; points in one of eight directions | Use turns the arrow |
| World Globe | A cradle with a slowly turning globe | Shows one of seven worlds: Station, Luna, Mars, Cindara, Glacira, Greenxertz, Nyxara | Use shows the next world |
| Holo Table | A table with a terrain hologram in its well | No properties | - |
| Holo Curtain | A see-through scanline sheet | Runs along x or z; connects to neighbouring curtains; no collision | - |
| Holo Plinth | A lit display stand | No properties; holds nothing | - |
| Ticker Strip | A thin band of scrolling glyphs | Mounts on any face; joins end to end | - |

## Using them

- **Holo Sign text** is sanitised and limited to two lines of 32 characters. It is world state, like a vanilla
  sign, and is not tied to the player who wrote it. See [Privacy](Privacy.md).
- **Display surfaces.** The Holo Sign and Holo Table accept Neroland Core `link.display` pushes (text, an icon
  and a status colour) from other mods. A pushed display replaces the panel until it is cleared.
- The Ticker Strip scrolls abstract glyphs; for typed text, use a Holo Sign.
- Projector, sign, arrow, table, curtain and ticker are Chroma blocks.

## Recipes

Amethyst shards, glass panes and glowstone; the globe needs a map. Full list: [Recipes](Recipes.md#holograms).
