package za.co.neroland.nerodecor.link;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerolandcore.link.LinkSnapshotProvider;
import za.co.neroland.nerolandcore.palette.Finish;
import za.co.neroland.nerolandcore.palette.PaletteRegistry;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.chroma.server.ChromaTags;
import za.co.neroland.nerodecor.client.ctm.CtmKey;
import za.co.neroland.nerodecor.client.ctm.CtmSurface;
import za.co.neroland.nerodecor.config.NeroDecorConfig;
import za.co.neroland.nerodecor.content.DecorColor;
import za.co.neroland.nerodecor.content.DecorProperties;
import za.co.neroland.nerodecor.content.block.DecorCubeBlock;
import za.co.neroland.nerodecor.content.block.DecorPaneBlock;
import za.co.neroland.nerodecor.content.block.DecorSlabBlock;
import za.co.neroland.nerodecor.content.block.DecorStairBlock;
import za.co.neroland.nerodecor.content.block.DecorWallBlock;
import za.co.neroland.nerodecor.registry.DecorBlocks;
import za.co.neroland.nerodecor.registry.ChromaContent;
import za.co.neroland.nerodecor.content.block.ChromaCubeBlock;
import za.co.neroland.nerodecor.content.block.ChromaHubBlock;
import za.co.neroland.nerodecor.content.block.gantry.AxisLinkBlock;
import za.co.neroland.nerodecor.content.block.NeonShimBlock;
import za.co.neroland.nerodecor.registry.RegistrationProvider.RegistryEntry;

/**
 * NeroDecor's link sections: two public catalogue sections, plus the three requester-scoped Chroma sections, which
 * are built by {@link ChromaLinkSections} (link schema 2).
 *
 * <h2>Sections</h2>
 *
 * <ul>
 *   <li>{@code palette} — the shared Neroland palette Core exports ({@code PaletteRegistry}), each
 *       finish with its sRGB colour, emissive flag and the material tag it derives from; plus
 *       NeroDecor's own paint vocabulary, the 16 accents and {@code NATURAL} of {@code DecorColor}
 *       that the {@code COLOR} blockstate property can take;</li>
 *   <li>{@code blocks} — every visible NeroDecor block (the paintable families, the Chroma hub and cube, the
 *       Luminous blocks and the 0.8.0 collections; the twelve hidden neon shims are left out) with its shape and
 *       model kind, whether it takes paint, whether a Chroma Hub can drive it ({@code chroma}), whether it glows,
 *       and, for the paintable families, its CTM family and connected-texture style;</li>
 *   <li>{@code chroma_hubs}, {@code chroma_zones}, {@code chroma_scenes} — the requester's own and trusted hubs,
 *       built by {@link ChromaLinkSections}.</li>
 * </ul>
 *
 * <p>{@code palette} and {@code blocks} accept an optional {@code q} parameter (a case-insensitive substring
 * matched against ids and names) and cap their output; the zone and scene sections take {@code hub}. Any other
 * section name yields an empty object, as Core's contract prescribes.
 *
 * <h2>Privacy (POPIA/GDPR)</h2>
 *
 * <p>For {@code palette} and {@code blocks} the {@code playerId} argument is read for nothing but a null check:
 * every byte is public content metadata, identical for every requester. The Chroma sections use it for one thing,
 * {@link za.co.neroland.nerodecor.chroma.server.HubNetwork#roleOf}, and return only hubs the requester owns or is
 * trusted on, with no coordinates and no roster (see {@link ChromaLinkSections}).
 *
 * <p><b>Read-only and bounded.</b> Nothing here mutates anything or loads a chunk.
 *
 * <p>Server thread only (as Core's contract specifies), though nothing here is thread-sensitive.
 */
public final class NeroDecorLinkSnapshots implements LinkSnapshotProvider {

    private final Supplier<ChromaLinkSource> chroma;

    public NeroDecorLinkSnapshots(Supplier<ChromaLinkSource> chroma) {
        this.chroma = chroma;
    }

    /** Hard cap on rows per list, so a data pack that registers a thousand finishes cannot flood a phone. */
    private static final int MAX_ROWS = 512;

    @Override
    public String moduleId() {
        return NeroDecorLinkModule.MODULE_ID;
    }

    @Override
    public int schemaVersion() {
        return NeroDecorLinkModule.SCHEMA_VERSION;
    }

    @Override
    public List<String> sections() {
        return NeroDecorLinkModule.SECTIONS;
    }

    @Override
    public JsonObject snapshot(UUID playerId, String section, Map<String, String> params) {
        if (playerId == null || section == null) {
            return new JsonObject();
        }
        if (!NeroDecorConfig.linkModuleEnabled()) {
            return new JsonObject();
        }
        try {
            String query = query(params);
            return switch (section) {
                case NeroDecorLinkModule.SECTION_PALETTE -> palette(query);
                case NeroDecorLinkModule.SECTION_BLOCKS -> blocks(query);
                // Requester-scoped: only hubs the requester owns or is trusted on, no coordinates, no roster.
                case NeroDecorLinkModule.SECTION_CHROMA_HUBS -> ChromaLinkSections.hubs(chroma.get(), playerId);
                case NeroDecorLinkModule.SECTION_CHROMA_ZONES ->
                        ChromaLinkSections.zones(chroma.get(), playerId, param(params, "hub"));
                case NeroDecorLinkModule.SECTION_CHROMA_SCENES ->
                        ChromaLinkSections.scenes(chroma.get(), playerId, param(params, "hub"));
                // Unknown section: nothing to say.
                default -> new JsonObject();
            };
        } catch (RuntimeException e) {
            // Section name only — never who asked (POPIA/GDPR).
            NeroDecorCommon.LOGGER.warn(
                    "[NeroDecor] NeroLink snapshot section '{}' failed; returning nothing for it.",
                    section, e);
            return new JsonObject();
        }
    }

    // --- section: palette -----------------------------------------------------

    /**
     * The shared palette, and NeroDecor's paint vocabulary beside it.
     *
     * <p>{@code finishes} is Core's export verbatim — NeroDecor <b>registers no finishes of its own</b>
     * (it is a consumer of the palette, not a contributor), so {@code finishes_registered_by_mod} is
     * reported as zero rather than left to be inferred. {@code colors} is the separate, mod-owned
     * vocabulary the paint property actually takes: the 16 dye-aligned accents whose RGBs mirror Core's
     * {@code neroland:accent/*} set, plus {@code NATURAL}, which means "no paint — show the material's
     * own tone" and is why its tint is white.
     */
    private static JsonObject palette(String query) {
        JsonObject root = envelope();
        root.addProperty("palette_api_version", PaletteRegistry.API_VERSION);
        root.addProperty("finishes_available", PaletteRegistry.size());
        root.addProperty("finishes_registered_by_mod", 0);

        JsonArray finishes = new JsonArray();
        for (Finish finish : PaletteRegistry.all()) {
            if (finishes.size() >= MAX_ROWS) {
                break;
            }
            Identifier id = finish.id();
            if (!matches(query, id.toString(), finish.name())) {
                continue;
            }
            JsonObject row = new JsonObject();
            row.addProperty("id", id.toString());
            row.addProperty("name", finish.name());
            row.addProperty("rgb", finish.rgb());
            row.addProperty("hex", hex(finish.rgb()));
            row.addProperty("red", finish.red());
            row.addProperty("green", finish.green());
            row.addProperty("blue", finish.blue());
            row.addProperty("emissive", finish.emissive());
            row.addProperty("emissive_light", finish.emissiveLight());
            Identifier material = finish.material();
            row.addProperty("material_tag", material == null ? null : material.toString());
            row.addProperty("accent", material == null);
            finishes.add(row);
        }
        root.add("finishes", finishes);

        JsonArray colors = new JsonArray();
        for (DecorColor color : DecorColor.values()) {
            String name = readable(color.getSerializedName());
            if (!matches(query, color.getSerializedName(), name)) {
                continue;
            }
            JsonObject row = new JsonObject();
            row.addProperty("id", color.getSerializedName());
            row.addProperty("name", name);
            row.addProperty("tint", color.tint());
            row.addProperty("hex", hex(color.tint()));
            row.addProperty("natural", color.isNatural());
            colors.add(row);
        }
        root.add("colors", colors);
        root.addProperty("colors_available", DecorColor.values().length);

        // 0.8.0 neon fold: the twelve neon blocks are now one chroma_cube with a COLOR; old ids map here.
        JsonArray legacy = new JsonArray();
        for (RegistryEntry<Block> shim : DecorBlocks.NEON_SHIMS) {
            if (shim.get() instanceof NeonShimBlock neon) {
                JsonObject row = new JsonObject();
                row.addProperty("legacy_id", shim.id().toString());
                row.addProperty("block", ChromaContent.CUBE.id().toString());
                row.addProperty("color", neon.dye().getSerializedName());
                legacy.add(row);
            }
        }
        root.add("legacy_neon", legacy);
        return root;
    }

    // --- section: blocks ------------------------------------------------------

    /**
     * The registered decor block set, in registration order — the same order the creative tab and
     * datagen iterate, so an app's list matches what a player sees in game.
     *
     * <p>Family and connected-texture style come from the block's own {@link CtmSurface} contract, and
     * the shape and model kind from its class (or, for bespoke blocks, whether its default shape fills the block),
     * so a block added to {@code DecorBlocks.ALL} appears here with no change to this file. {@code emissive} is the
     * default state's light emission (the Chroma Cube always counts). The hidden neon shims are skipped; the
     * Chroma Hub, registered outside {@code ALL}, is listed after it.
     */
    private static JsonObject blocks(String query) {
        JsonObject root = envelope();
        JsonArray rows = new JsonArray();
        int total = 0;

        List<RegistryEntry<? extends Block>> visible = new java.util.ArrayList<>(DecorBlocks.ALL);
        if (ChromaContent.HUB != null) {
            visible.add(ChromaContent.HUB); // the hub is registered outside ALL but is a visible block
        }
        for (RegistryEntry<? extends Block> entry : visible) {
            Block block = entry.get();
            if (block instanceof NeonShimBlock) {
                continue; // hidden legacy ids; the palette section's legacy_neon maps them to chroma_cube
            }
            total++;
            if (rows.size() >= MAX_ROWS) {
                continue;
            }
            Identifier id = entry.id();
            String name = readable(id.getPath());
            if (!matches(query, id.toString(), name)) {
                continue;
            }
            BlockState state = block.defaultBlockState();
            JsonObject row = new JsonObject();
            row.addProperty("id", id.toString());
            row.addProperty("name", name);
            // Built from the id rather than read off the block: the vanilla convention the mod's own
            // lang files already follow, and one less render-side API in a catalogue.
            row.addProperty("translation_key", "block." + id.getNamespace() + "." + id.getPath());
            String model = modelOf(block, state);
            row.addProperty("shape", shapeOf(block, model));
            row.addProperty("model", model);
            // Paintable families carry the COLOR blockstate property.
            boolean paintable = state.hasProperty(DecorProperties.COLOR);
            row.addProperty("paintable", paintable);
            // Whether a Chroma Hub can drive it (the data-driven #nerodecor:chroma_capable tag).
            row.addProperty("chroma", isChroma(state));
            boolean emissive = block instanceof ChromaCubeBlock || state.getLightEmission() > 0;
            row.addProperty("collection", collectionOf(block, paintable, emissive));

            if (block instanceof CtmSurface surface) {
                CtmKey key = surface.ctmKey(state);
                String family = key.family().getPath();
                row.addProperty("family", key.family().toString());
                row.addProperty("family_name", readable(family));
                row.addProperty("ctm_style", surface.ctmStyle().name());
            }
            row.addProperty("emissive", emissive);
            rows.add(row);
        }

        root.add("blocks", rows);
        root.addProperty("blocks_registered", total);
        root.addProperty("truncated", rows.size() >= MAX_ROWS);
        // Both render kill-switches are client-local; a companion client showing the catalogue may want
        // to say "connected textures are off on this client" without guessing.
        root.addProperty("connected_textures", NeroDecorConfig.connectedTexturesEnabled());
        root.addProperty("emissive_rendering", NeroDecorConfig.emissiveRenderingEnabled());
        return root;
    }

    /**
     * The block's model kind: the vanilla-shaped families by class ({@code cube}, {@code slab}, {@code stairs},
     * {@code wall}, {@code pane}, {@code pillar}); otherwise {@code cube} when the default state fills the block and
     * {@code custom} for every bespoke model (consoles, doors, pipes, furniture, fittings).
     */
    static String modelOf(Block block, BlockState state) {
        if (block instanceof DecorCubeBlock || block instanceof ChromaCubeBlock) {
            return "cube";
        }
        if (block instanceof DecorSlabBlock || block instanceof SlabBlock) {
            return "slab";
        }
        if (block instanceof DecorStairBlock) {
            return "stairs";
        }
        if (block instanceof DecorWallBlock) {
            return "wall";
        }
        if (block instanceof DecorPaneBlock) {
            return "pane";
        }
        if (block instanceof RotatedPillarBlock || block instanceof AxisLinkBlock) {
            return "pillar";
        }
        if (block instanceof ChromaHubBlock) {
            return "custom";
        }
        try {
            return Block.isShapeFullBlock(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) ? "cube" : "custom";
        } catch (RuntimeException e) {
            return "custom"; // a shape that needs a real world is bespoke by definition
        }
    }

    /** The schema-1 {@code shape} field, kept for older clients: {@code block} for anything bespoke. */
    private static String shapeOf(Block block, String model) {
        return "custom".equals(model) || ("cube".equals(model) && !(block instanceof DecorCubeBlock
                || block instanceof ChromaCubeBlock)) ? "block" : model;
    }

    private static boolean isChroma(BlockState state) {
        try {
            return ChromaTags.isCapable(state);
        } catch (RuntimeException e) {
            return false; // tags not bound yet
        }
    }

    /** {@code decor} (paintable families), {@code chroma} (hub and cube), {@code luminous} (other light sources), {@code collections}. */
    private static String collectionOf(Block block, boolean paintable, boolean emissive) {
        if (block instanceof ChromaHubBlock || block instanceof ChromaCubeBlock) {
            return "chroma";
        }
        if (paintable) {
            return "decor";
        }
        return emissive ? "luminous" : "collections";
    }

    // --- helpers --------------------------------------------------------------

    /** The one field every NeroDecor snapshot root starts with. There is no player state to report. */
    private static JsonObject envelope() {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", NeroDecorLinkModule.SCHEMA_VERSION);
        return root;
    }

    private static String param(Map<String, String> params, String key) {
        return params == null ? null : params.get(key);
    }

    /** The lower-cased {@code q} parameter, or {@code null} when absent/blank. */
    private static String query(Map<String, String> params) {
        String raw = params == null ? null : params.get("q");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matches(String query, String id, String name) {
        if (query == null) {
            return true;
        }
        return id.toLowerCase(Locale.ROOT).contains(query) || name.toLowerCase(Locale.ROOT).contains(query);
    }

    /** {@code 0x2E8B9E} as {@code "#2E8B9E"} — what a client actually wants to hand a colour picker. */
    private static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** A display label from a registry path: {@code "hull_nero_alloy"} becomes {@code "Hull nero alloy"}. */
    private static String readable(String path) {
        String cleaned = path.replace('_', ' ').trim();
        if (cleaned.isEmpty()) {
            return path;
        }
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }
}
