# Architect Quests

NeroDecor ships an optional quest pack for **NeroQuests**: the chapter **Architect**
(`nerodecor:architect`). It appears as a tab in the quest book when NeroQuests is installed. Without
NeroQuests the pack is inert data inside the NeroDecor jar — NeroDecor has **no dependency** on it,
and nothing changes for players or servers that do not use quests.

Every quest is player-scope (each player completes it for themselves) and every objective is a craft
or a collection, because NeroDecor is a catalogue of blocks: nothing ticks, nothing is gated. Rewards
are experience, credits and ordinary vanilla items. The pack reads nothing from a player beyond the
quest progress NeroQuests already keeps.

## The chapter

Dependency lines in the book follow each quest's prerequisites. Crafting counts are the number of
matching items crafted in a crafting grid; machine outputs do not count.

| Quest | Needs | Objective | Reward |
| --- | --- | --- | --- |
| First Light | — | Craft 8 neon blocks (`neroland:decor/neon`, any colour) | 20 XP, 8 glowstone dust |
| Hull Plating | First Light | Craft 16 hull pieces (`neroland:decor/hull`: blocks, slabs, stairs, walls) | 40 XP, 30 credits |
| Clean Lines | First Light | Craft 16 panels (`neroland:decor/panel`) | 40 XP, 30 credits |
| Full Spectrum | First Light | Hold one neon block of each of the 12 colours | 100 XP, 100 credits |
| Through the Glass | Hull Plating | Craft 8 tinted glass (`neroland:decor/glass`) | 50 XP, 16 glass |
| Starsteel Frame | Hull Plating | Craft 4 Starsteel Pillars | 50 XP, 40 credits |
| Circuitry | Clean Lines | Craft 8 Circuit Plating and 8 Holo-Grid Floor | 60 XP, 8 redstone |
| Into the Void | Through the Glass | Craft 2 Void Rifts and 1 Void Crystal Lattice | 80 XP, 8 amethyst shards |
| Power Aesthetic | Starsteel Frame, Circuitry | Craft 1 Fusion Lamp and 2 Capacitor Banks | 80 XP, 75 credits |
| Xeno Garden | Circuitry | Craft 4 Xenobloom and 4 Aurora Glass | 80 XP, 16 bone meal |
| Master Architect | Power Aesthetic, Into the Void, Xeno Garden | Complete those three quests | 150 XP, 200 credits, 8 sea lanterns |

Credits are paid through Neroland Core's currency contract and need an economy mod (NeroEconomy) to
land; on a server without one the credit line of a reward simply grants nothing, while the XP and
items still pay out.

## Where the pack lives

```text
common/src/main/resources/data/nerodecor/neroquests/chapters/architect.json
common/src/main/resources/data/nerodecor/neroquests/quests/architect/<name>.json
common/src/main/resources/assets/nerodecor/lang/en_us.json   (chapter.* / quest.* keys)
```

The four family quests craft over the `neroland:decor/{neon,panel,hull,glass}` **item tags** (added in
0.4.1), so blocks added to a family later count automatically. A server datapack can override any
quest by shipping a file at the same path.

## See also

- [Luminous Collection](Luminous-Collection.md) — the blocks the later quests ask for.
- [Home](Home.md)
