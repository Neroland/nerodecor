# Chroma Cube, Paint Gun and the neon fold (0.8.0)

## Chroma Cube

A connected light-guide cube that replaces the twelve neon blocks. It takes any paint colour and emits light 15
by default. Linked to a Chroma Hub, the hub's static colour overrides its look, and its light follows the
brightness of that colour (changes apply at most once a second per block; animations never change light).

- Recipe: 4 glass around 1 glowstone dust gives 4 Chroma Cubes.
- Recolour: 1 dye + 1 Chroma Cube (shapeless) gives a cube of that colour (the 12 old neon colours).
- Breaking a cube drops it with its colour.

## Paint Gun

- Use on a paintable block: paint it the gun's colour.
- Sneak-use on a paintable block: copy that block's colour into the gun.
- Use in the air: next colour; sneak-use in the air: previous colour.
- Painting respects the same protection as every Chroma edit (build permission, spawn protection, world border and
  claim mods).
- Recipe: iron ingot and glass bottle over a stick.

## The neon fold (upgrading from 0.7.x)

Old `neon_<colour>` blocks and items still load. They are hidden (no creative tab, no recipe) and turn into
Chroma Cubes of the same colour, with the same light:

- placed blocks, when their chunk loads (on the next server tick);
- stacks in chests and other containers in that chunk;
- stacks in a player's inventory, on their next tick;
- dropped stacks, on their next tick.

The old `neon_<colour>` recipe ids now hold the dye recolour recipes, so recipe books carry over.

## Hub colour sync

Players near a hub (tracking the hub's chunk) receive its colour layout; changes are batched to at most two updates a
second per hub, and only the affected chunk sections re-render. Nothing about players is stored or logged.
