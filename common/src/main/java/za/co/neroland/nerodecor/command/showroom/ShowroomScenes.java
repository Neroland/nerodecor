package za.co.neroland.nerodecor.command.showroom;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerodecor.chroma.ChromaColor;
import za.co.neroland.nerodecor.chroma.EffectParams;
import za.co.neroland.nerodecor.chroma.EffectProgram;
import za.co.neroland.nerodecor.chroma.EffectType;
import za.co.neroland.nerodecor.chroma.Scene;
import za.co.neroland.nerodecor.chroma.ZoneAssignment;
import za.co.neroland.nerodecor.chroma.server.ChromaNetworks;
import za.co.neroland.nerodecor.chroma.server.ChromaScenes;
import za.co.neroland.nerodecor.chroma.server.ChromaVerdict;

/**
 * The showroom's eight bays, as data. Each layer is a plan view of the bay, front (the corridor, north) at the top,
 * west on the left; see {@link ShowroomScene} for the format. Tweak a layout here and rerun {@code /nerodecor gallery}
 * (it clears and rebuilds in place); the {@code showroom_builds} GameTest fails on any unknown id, property, value or
 * block that cannot stand where it is put.
 */
public final class ShowroomScenes {

    private ShowroomScenes() {
    }

    // --- 1. Reactor hall ------------------------------------------------------------------------------------------
    // Starsteel hull walls ribbed with live plasma conduits, a pipe band at head height, the core in a hazard ring.
    static final ShowroomScene REACTOR_HALL = ShowroomScene.scene("reactor_hall", "REACTOR HALL", "Conduits + Luminous")
            .shell("panel_starsteel", "hull_starsteel", "hull_starsteel")
            .key('S', "hull_starsteel")
            .key('P', "plasma_conduit[axis=y]")
            .key('C', "capacitor_bank")
            .key('T', "coolant_tube")
            .key('H', "hazard_stripe[face=down,axis=x]")
            .key('h', "hazard_stripe[face=down,axis=z]")
            .key('V', "ion_vent")
            .key('L', "crystal_lattice")
            .key('p', "decor_pipe")
            .key('v', "pipe_valve[axis=x]")
            .key('g', "pipe_gauge[axis=x]")
            .key('W', "warning_placard[face=south,symbol=radiation]")
            .key('w', "warning_placard[face=west,symbol=radiation]")
            .key('E', "warning_placard[face=east,symbol=radiation]")
            .key('c', "cage_lamp[face=up]")
            .sides(0, 1, ".SPSSPSSP", ".SPSSPSSP")
            .sides(2, 2, ".SPSSPSwP", ".SPSSPSEP")
            .sides(3, 3, ".SPSSPSSP", ".SPSSPSSP")
            .sides(4, 4, ".pPppPppP", ".pPppPppP")
            .sides(5, 5, ".SPSSPSSP", ".SPSSPSSP")
            .layer(0,
                    "...........",
                    "...........",
                    "..HHHHHHH..",
                    "..h.....h..",
                    "..h.CCC.h..",
                    "..h.CPC.h..",
                    "..h.CCC.h..",
                    "..HHHHHHH..",
                    ".LV.....VL.")
            .layer(1,
                    "...........",
                    "...........",
                    "...........",
                    "...........",
                    "....T.T....",
                    ".....P.....",
                    "....T.T....",
                    "...........",
                    ".L.......L.")
            .layer(2,
                    "...........",
                    "...........",
                    "...........",
                    "...........",
                    "....T.T....",
                    ".....P.....",
                    "....T.T....",
                    "...........",
                    ".....W.....")
            .layer(3,
                    "...........",
                    "...........",
                    "...........",
                    "...........",
                    "....T.T....",
                    ".....P.....",
                    "....T.T....")
            .layer(4,
                    "...........",
                    "...........",
                    "...........",
                    "...........",
                    "....TTT....",
                    "....TPT....",
                    "....TTT....",
                    "...........",
                    ".pgppvppgp.")
            .layer(5,
                    "...........",
                    "...........",
                    "..c.....c..",
                    "...........",
                    "...........",
                    ".....P.....",
                    "...........",
                    "..c.....c..")
            .build();

    // --- 2. Observation deck --------------------------------------------------------------------------------------
    // A canopy-glass roof over the stars, viewport ribs down both sides, light strips tracing the frame.
    static final ShowroomScene OBSERVATION_DECK = ShowroomScene.scene("observation_deck", "OBSERVATION DECK",
                    "Bulkhead viewports + Habitat")
            .shell("holo_grid_floor", "starfield_panel", "canopy_glass[facing=north,half=top]")
            .key('F', "viewport_frame")
            .key('R', "starsteel_pillar[axis=y]")
            .key('b', "panel_void_crystal")
            .key('r', "railing")
            .key('s', "operator_chair[facing=south]")
            .key('t', "lab_stool")
            .key('K', "locker[facing=east]")
            .key('k', "locker[facing=west]")
            .key('p', "planter[axis=x,plant=0]")
            .key('q', "planter[axis=x,plant=1]")
            .key('u', "planter[axis=x,plant=2]")
            .key('h', "holo_table")
            .key('l', "light_strip[face=up,axis=x]")
            .key('w', "light_strip[face=up,axis=z]")
            .sides(0, 0, ".bbbbbbbb", ".bbbbbbbb")
            .sides(1, 4, ".RFRFRFRF", ".RFRFRFRF")
            .sides(5, 5, ".wwwwwwww", ".wwwwwwww")
            .layer(0,
                    "...........",
                    "...pqupq...",
                    ".K.......k.",
                    ".K...h...k.",
                    "...........",
                    "..ts.s.st..",
                    "...........",
                    "rrrrrrrrrrr",
                    "FFFFFFFFFFF")
            .layer(1,
                    "...........",
                    "...........",
                    ".K.......k.",
                    ".K.......k.",
                    "...........",
                    "...........",
                    "...........",
                    "...........",
                    "FFFFFFFFFFF")
            .layer(2, "...........", "...........", "...........", "...........", "...........", "...........",
                    "...........", "...........", "FFFFFFFFFFF")
            .layer(3, "...........", "...........", "...........", "...........", "...........", "...........",
                    "...........", "...........", "FFFFFFFFFFF")
            .layer(4, "...........", "...........", "...........", "...........", "...........", "...........",
                    "...........", "...........", "FFFFFFFFFFF")
            .layer(5,
                    "...........",
                    "...........",
                    "...........",
                    ".lllllllll.",
                    "...........",
                    "...........",
                    ".lllllllll.")
            .build();

    // --- 3. Command bridge ----------------------------------------------------------------------------------------
    // Nero-alloy panelling, wall screens on both flanks, a ticker band, the console arc facing the main screen.
    static final ShowroomScene COMMAND_BRIDGE = ShowroomScene.scene("command_bridge", "COMMAND BRIDGE",
                    "Command + Holo")
            .shell("floor_tile", "hull_nero_alloy", "panel_nero_alloy")
            .key('N', "panel_nero_alloy")
            .key('W', "wall_screen[facing=east]")
            .key('V', "wall_screen[facing=west]")
            .key('T', "ticker_strip[face=west,axis=z]")
            .key('U', "ticker_strip[face=east,axis=z]")
            .key('S', "wall_screen[facing=north]")
            .key('B', "blinkenlight_panel")
            .key('c', "command_console[facing=north]")
            .key('s', "operator_chair[facing=south]")
            .key('j', "holo_projector[face=down]")
            .key('d', "door_status_light[face=south]")
            .key('a', "access_pad[face=east]")
            .key('k', "ceiling_coffer")
            .key('K', "keyboard_shelf[facing=north]")
            .key('e', "step_light[facing=north]")
            .sides(0, 0, ".NNNNNNNN", ".NNNNNNNN")
            .sides(1, 2, ".NWWWNNNN", ".NVVVNNNN")
            .sides(3, 3, ".NNNNNNNN", ".NNNNNNNN")
            .sides(4, 4, ".TTTTTTTT", ".UUUUUUUU")
            .sides(5, 5, ".NNNNNNNN", ".NNNNNNNN")
            .layer(0,
                    "...........",
                    "...........",
                    ".....s.....",
                    ".....j.....",
                    "...s...s...",
                    "..ccccccc..",
                    "...........",
                    "..e.....e..",
                    "BB.......BB")
            .layer(1,
                    "...........",
                    "...........",
                    "...........",
                    "...........",
                    "...........",
                    "...........",
                    "..........a",
                    "...........",
                    "BBSSSSSSSBB")
            .layer(2, "...........", "...........", "...........", "...........", "...........", "...........",
                    "...........", ".d.......d.", "BBSSSSSSSBB")
            .layer(3, "...........", "...........", "...........", "...........", "...........", "...........",
                    "...........", "...........", "..SSSSSSS..")
            .layer(5,
                    "...........",
                    "...........",
                    "...kkkkk...",
                    "...........",
                    "...........",
                    "...kkkkk...")
            .build();

    // --- 4. Server vault ------------------------------------------------------------------------------------------
    // Racks line both walls under a band of blinkenlights; cable trays and a duct run overhead.
    static final ShowroomScene SERVER_VAULT = ShowroomScene.scene("server_vault", "SERVER VAULT", "Command racks + Conduits")
            .shell("circuit_plating", "hull_starsteel", "hull_starsteel")
            .key('x', "server_rack[facing=east]")
            .key('X', "server_rack[facing=west]")
            .key('b', "blinkenlight_panel")
            .key('s', "panel_starsteel")
            .key('R', "server_rack[facing=north]")
            .key('D', "drive_rack[facing=north]")
            .key('P', "patch_panel[facing=north]")
            .key('c', "hanging_cables")
            .key('t', "cable_tray[face=up,axis=x]")
            .key('l', "light_strip[face=up,axis=x]")
            .key('u', "duct[axis=x]")
            .key('f', "duct_fan[facing=east]")
            .key('m', "maintenance_panel[face=south,open=true]")
            .key('n', "deck_sign[face=south,number=4]")
            .key('w', "warning_placard[face=south,symbol=voltage]")
            .sides(0, 1, ".xxxxxxxs", ".XXXXXXXs")
            .sides(2, 3, ".bbbbbbbs", ".bbbbbbbs")
            .sides(4, 5, ".ssssssss", ".ssssssss")
            .layer(0,
                    "...........",
                    "...........",
                    "...........",
                    "..RRRRRRR..",
                    "...........",
                    "...........",
                    "..DDDDDDD..",
                    "...........",
                    ".m.......m.")
            .layer(1, "...........", "...........", "...........", "..RRRRRRR..", "...........", "...........",
                    "..DDDDDDD..")
            .layer(2, "...........", "...........", "...........", "..RRRRRRR..", "...........", "...........",
                    "..DDDDDDD..", "...........", ".w...n...w.")
            .layer(3, "...........", "...........", "...........", "...........", "...........", "...........",
                    "...........", "...........", "..PPPPPPP..")
            .layer(4, "...........", "...........", "...........", "...........", "..c.....c..")
            .layer(5,
                    "...........",
                    ".ttttttttt.",
                    "...........",
                    "...........",
                    "..c.....c..",
                    ".lllllllll.",
                    "...........",
                    ".uuuuuuuuf.")
            .build();

    // --- 5. Airlock sequence --------------------------------------------------------------------------------------
    // Starsteel bulkheads with hazard-striped kick plates; the frames hug the doors so the doors read first.
    static final ShowroomScene AIRLOCK = ShowroomScene.scene("airlock", "AIRLOCK SEQUENCE", "Bulkhead doors")
            .shell("hull_nero_alloy", "mars_rust_plate", "hull_nero_alloy")
            .key('N', "hull_starsteel")
            .key('A', "airlock_frame")
            .key('G', "smart_glass")
            .key('L', "!sliding_door[facing=south,hinge=left]")
            .key('R', "!sliding_door[facing=south,hinge=right]")
            .key('X', "!blast_door[facing=north,part=7]")
            .key('O', "porthole[axis=z]")
            .key('s', "door_status_light[face=north]")
            .key('p', "access_pad[face=south]")
            .key('H', "hazard_stripe[face=down,axis=x,edge=true]")
            .key('k', "hazard_stripe[face=south,axis=y]")
            .key('z', "hazard_stripe[face=west,axis=z]")
            .key('Z', "hazard_stripe[face=east,axis=z]")
            .key('r', "rivet_plate[face=west]")
            .key('q', "rivet_plate[face=east]")
            .key('i', "iris_hatch[face=up]")
            .key('t', "pressure_trapdoor[facing=north,half=bottom]")
            .key('w', "warning_placard[face=south,symbol=oxygen]")
            .key('m', "mars_dust_drift_tile[facing=north]")
            .sides(0, 0, ".zzzzzzzz", ".ZZZZZZZZ")
            .sides(1, 4, ".rrrrrrrr", ".qqqqqqqq")
            .sides(5, 5, ".zzzzzzzz", ".ZZZZZZZZ")
            .layer(0,
                    "...........",
                    "...kHHk....",
                    "NNNALRANNNN",
                    "..HHHHHHH..",
                    ".........t.",
                    "..HHHHHHH..",
                    "NNNA.X.ANNN",
                    "...........",
                    "mmmmmmmmmmm")
            .layer(1,
                    "...........",
                    "...k..k.p..",
                    "NGGA..AGGGN",
                    "...s..s....",
                    "...........",
                    "...........",
                    "NNNA...ANNN")
            .layer(2,
                    "...........",
                    "...........",
                    "NNNAAAANNNN",
                    "...........",
                    "...........",
                    ".w.......w.",
                    "NONA...ANON")
            .layer(3, "...........", "...........", "NNNNNNNNNNN", "...........", "...........", "...........",
                    "NNNAAAAANNN")
            .layer(4, "...........", "...........", "NNNNNNNNNNN", "...........", "...........", "...........",
                    "NNNNNNNNNNN")
            .layer(5, "...........", "...........", "NNNNNNNNNNN", "...........", ".....i.....", "...........",
                    "NNNNNNNNNNN")
            .hook(bay -> { // doors toggle on a redstone edge only, so opened doors stay open with no power source
                bay.openDoor(4, 0, 2);
                bay.openDoor(5, 0, 2);
            })
            .build();

    // --- 6. Chroma corridor ---------------------------------------------------------------------------------------
    // Dark void-crystal hull; the colour lives in the lighting (strips, pillars, coffers, neon, a framed cube panel)
    // and drifts down the corridor as a slow OKLab wave, so the room has depth instead of flat saturated fills.
    static final ShowroomScene CHROMA_CORRIDOR = ShowroomScene.scene("chroma_corridor", "CHROMA CORRIDOR",
                    "Hub + linked lights, scene 1")
            .shell("floor_tile", "hull_void_crystal", "hull_starsteel")
            .key('V', "hull_void_crystal")
            .key('H', "!chroma_hub")
            .key('C', "chroma_cube")
            .key('I', "starsteel_pillar[axis=y]")
            .key('J', "starsteel_pillar[axis=x]")
            .key('P', "light_pillar[axis=y]")
            .key('s', "light_strip[face=down,axis=z]")
            .key('l', "light_strip[face=west,axis=y]")
            .key('L', "light_strip[face=east,axis=y]")
            .key('e', "edge_trim[facing=east,half=top]")
            .key('E', "edge_trim[facing=west,half=top]")
            .key('n', "neon_tube[face=up,axis=z]")
            .key('r', "halo_ring")
            .key('c', "cage_lamp[face=up]")
            .key('k', "ceiling_coffer")
            .sides(0, 4, ".VlVVlVVl", ".VLVVLVVL")
            .sides(5, 5, ".eeeeeeee", ".EEEEEEEE")
            .layer(0,
                    "...........",
                    ".....H.....",
                    "...s...s...",
                    ".P.s...s.P.",
                    "...s...s...",
                    "...s...s...",
                    ".P.s...s.P.",
                    "...s...s...",
                    "...JJJJJ...")
            .layer(1, "...........", "...........", "...........", ".P.......P.", "...........", "...........",
                    ".P.......P.", "...........", "...ICCCI...")
            .layer(2, "...........", "...........", "...........", ".P.......P.", "...........", "...........",
                    ".P.......P.", "...........", "...ICCCI...")
            .layer(3, "...........", "...........", "...........", ".P.......P.", "...........", "...........",
                    ".P.......P.", "...........", "...ICCCI...")
            .layer(4, "...........", "...........", "...........", ".P...r...P.", "...........", "...........",
                    ".P...r...P.", "...........", "...JJJJJ...")
            .layer(5,
                    "...........",
                    "..n.kkk.n..",
                    "..n.kkk.n..",
                    "..n.kkk.n..",
                    "..nckkkcn..",
                    "..n.kkk.n..",
                    "..n.kkk.n..",
                    "..n.kkk.n..",
                    "..n.kkk.n..")
            .hook(ShowroomScenes::playChroma)
            .build();

    // --- 7. Seven Worlds museum -----------------------------------------------------------------------------------
    /** World, globe value, then its blocks (the first is the plinth under the globe). */
    static final List<List<String>> WORLDS = List.of(
            List.of("STATION", "station", "station_plating", "station_tread_plate", "station_thermal_blanket"),
            List.of("LUNA", "luna", "luna_regolith_brick", "luna_bootprint_tile", "luna_mare_column[axis=y]",
                    "luna_bead_glass"),
            List.of("MARS", "mars", "mars_rust_plate", "mars_wind_strata_brick", "mars_spherule_stone",
                    "mars_dust_drift_tile[facing=north]"),
            List.of("CINDARA", "cindara", "cindara_ember_basalt", "cindara_slag_brick", "cindara_ash_tile"),
            List.of("GLACIRA", "glacira", "glacira_snow_brick", "glacira_bubble_ice", "glacira_cryo_fin[axis=y]",
                    "glacira_frost_plate"),
            List.of("GREENXERTZ", "greenxertz", "greenxertz_woven_chitin", "greenxertz_spore_tile",
                    "greenxertz_hive_comb"),
            List.of("NYXARA", "nyxara", "nyxara_shard_mosaic", "nyxara_faceted_crystal", "nyxara_geode_block"));

    /**
     * Plinth positions (x, z of the globe); the world's column stands one block behind. Back row of four, front row of
     * three in the gaps; see {@link #worlds} for where each label goes.
     */
    private static final int[][] WORLD_SPOTS = {{1, 6}, {4, 6}, {7, 6}, {10, 6}, {2, 3}, {5, 3}, {8, 3}};

    // Void-crystal gallery walls with sconces between the exhibits; the side alcoves stay open for the end plinths.
    static final ShowroomScene SEVEN_WORLDS = ShowroomScene.scene("seven_worlds", "SEVEN WORLDS MUSEUM",
                    "One plinth bay per world")
            .shell("panel_nero_alloy", "hull_void_crystal", "hull_nero_alloy")
            .key('v', "panel_void_crystal")
            .key('o', "sconce_bar[facing=east,vertical=true]")
            .key('O', "sconce_bar[facing=west,vertical=true]")
            .key('l', "light_strip[face=up,axis=x]")
            .sides(0, 1, ".vvvv...v", ".vvvv...v")
            .sides(2, 2, ".vovv...o", ".vOvv...O")
            .sides(3, 5, ".vvvvvvvv", ".vvvvvvvv")
            .layer(5, "...........", "...........", ".lllllllll.", "...........", "...........", ".lllllllll.")
            .hook(ShowroomScenes::worlds)
            .build();

    // --- 8. Ancient vault -----------------------------------------------------------------------------------------
    // Brick walls set with glowing glyph stones between pillars; the dormant core sits lit on a glyph pedestal that
    // hides its power source.
    static final ShowroomScene ANCIENT_VAULT = ShowroomScene.scene("ancient_vault", "ANCIENT VAULT", "Ancients")
            .shell("ancient_brick", "ancient_brick", "cracked_ancient_brick")
            .key('B', "ancient_brick")
            .key('I', "ancient_pillar[axis=y]")
            .key('a', "arch_lintel[facing=north]")
            .key('0', "glyph_block[glyph=0]").key('1', "glyph_block[glyph=1]").key('2', "glyph_block[glyph=2]")
            .key('3', "glyph_block[glyph=3]").key('4', "glyph_block[glyph=4]").key('5', "glyph_block[glyph=5]")
            .key('6', "glyph_block[glyph=6]").key('7', "glyph_block[glyph=7]")
            .key('g', "glyph_strip[face=down,axis=x]")
            .key('D', "dormant_core[powered=true]")
            .key('d', "dormant_core[powered=false]")
            .key('x', "cracked_ancient_brick")
            .key('R', "minecraft:redstone_block")
            .sides(0, 0, ".BBIBBIBB", ".BBIBBIBB")
            .sides(1, 1, ".B2IB5IB0", ".B6IB1IB4")
            .sides(2, 2, ".x7IB3Ix2", ".B3Ix7IB5")
            .sides(3, 3, ".B4IB0IB6", ".x1IB4IB7")
            .sides(4, 5, ".BxIBBIxB", ".xBIBxIBB")
            .layer(0,
                    "...........",
                    ".I.......I.",
                    "...........",
                    "..ggggggg..",
                    ".....3.....",
                    "...d4R6d...",
                    ".....5.....",
                    ".I.......I.",
                    "x.........x")
            .layer(1, "...........", ".I.......I.", "...........", "...........", "...........", ".....D.....",
                    "...........", ".I.......I.", "...01234...")
            .layer(2, "...........", ".I.......I.", "...........", "...........", "...........", "...........",
                    "...........", ".I.......I.", "...56701...")
            .layer(3, "...........", ".I.......I.", "...........", "...........", "...........", "...........",
                    "...........", ".I.......I.", "...23456...")
            .layer(4, "...........", ".I.......I.", "...........", "...........", "...........", "...........",
                    "...........", ".I.......I.", "x.......x..")
            .layer(5, "...........", ".IaaaaaaaI.", "...........", "...........", "...........", "...........",
                    "...........", ".IaaaaaaaI.")
            .build();

    /** Bay order along the corridor, west to east. */
    public static final List<ShowroomScene> ALL = List.of(REACTOR_HALL, OBSERVATION_DECK, COMMAND_BRIDGE,
            SERVER_VAULT, AIRLOCK, CHROMA_CORRIDOR, SEVEN_WORLDS, ANCIENT_VAULT);

    // --- hooks ----------------------------------------------------------------------------------------------------

    /** Links the whole Chroma corridor bay (every capable light and cube) to its hub and plays a slow wave. */
    private static void playChroma(Showroom.Bay bay) {
        ServerPlayer owner = bay.owner();
        long id = bay.hubNetwork(5, 0, 1);
        if (owner == null || id == 0L) {
            bay.note("the hub has no network (no player, Chroma disabled, or the hub cap is reached); lights are static");
            return;
        }
        ChromaNetworks.Selection selection = ChromaNetworks.linkBox(bay.level(), owner, id, bay.at(0, -1, 0),
                bay.at(Showroom.BAY_W - 1, Showroom.BAY_H, Showroom.BAY_D));
        if (!selection.verdict().isOk() || selection.linked() == 0) {
            bay.error("linking the corridor failed: " + selection.verdict());
            return;
        }
        // A slow OKLab wave down the corridor (z), cyan to violet: smooth, never a flash.
        Scene scene = new Scene("Showroom", List.of(new ZoneAssignment(0, 0, new EffectProgram(EffectType.AXIS_WAVE,
                EffectParams.builder().primary(ChromaColor.of(0x22, 0xE8, 0xFF)).secondary(ChromaColor.of(0x8A, 0x5C,
                        0xFF)).axis(EffectParams.AXIS_Z).wavelength(18).periodTicks(200).build()))));
        ChromaVerdict verdict = ChromaScenes.save(ChromaNetworks.store(bay.level()), id, owner.getUUID(), 0, scene,
                true, bay.level().getGameTime());
        if (!verdict.isOk()) {
            bay.error("starting the scene failed: " + verdict);
        }
    }

    /** One plinth per world: its first block under a globe, the rest stacked behind, one holo sign each. */
    private static void worlds(Showroom.Bay bay) {
        for (int i = 0; i < WORLDS.size(); i++) {
            List<String> world = WORLDS.get(i);
            int x = WORLD_SPOTS[i][0];
            int z = WORLD_SPOTS[i][1];
            List<String> blocks = world.subList(2, world.size());
            boolean back = z >= 5;
            bay.set(x, 0, z, blocks.get(0));
            bay.set(x, 1, z, "world_globe[world=" + world.get(1) + "]");
            // Back row: a three-high column. Front row: two high, so it never hides the back row's labels, with any
            // remaining block inlaid in the floor beside the plinth.
            int height = back ? 3 : 2;
            for (int y = 0; y < height; y++) {
                bay.set(x, y, z + 1, blocks.get(1 + y % (blocks.size() - 1)));
            }
            for (int k = height + 1, side = -1; k < blocks.size(); k++, side += 2) {
                bay.set(x + side, -1, z, blocks.get(k));
            }
            // One label per plinth, in two height bands: back row on top of its column, front row on the floor in
            // front of its plinth. Within a row the plinths stand three apart, so no two labels overlap.
            int ly = back ? 3 : 0;
            int lz = back ? z + 1 : z - 1;
            bay.set(x, ly, lz, "holo_sign[facing=north]");
            bay.sign(x, ly, lz, world.get(0), blocks.size() + " blocks");
        }
    }
}
