package za.co.neroland.nerodecor.link;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;

import za.co.neroland.nerolandcore.link.LinkSnapshotProvider;
import za.co.neroland.nerolandcore.palette.Finish;
import za.co.neroland.nerolandcore.palette.PaletteRegistry;

import za.co.neroland.nerodecor.NeroDecorCommon;
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
import za.co.neroland.nerodecor.registry.RegistrationProvider.RegistryEntry;

/**
 * NeroDecor's whole link surface: two read-only catalogue sections.
 *
 * <h2>Sections</h2>
 *
 * <ul>
 *   <li>{@code palette} — the shared Neroland palette Core exports ({@code PaletteRegistry}), each
 *       finish with its sRGB colour, emissive flag and the material tag it derives from; plus
 *       NeroDecor's own paint vocabulary, the 16 accents and {@code NATURAL} of {@code DecorColor}
 *       that the {@code COLOR} blockstate property can take;</li>
 *   <li>{@code blocks} — every registered decor block (the paintable families and the Luminous
 *       collection) with its shape, whether it takes paint, whether it glows, and, for the paintable
 *       families, its CTM family and connected-texture style, so an app can render a browsable set
 *       rather than a list of opaque ids.</li>
 * </ul>
 *
 * <p>Both accept an optional {@code q} parameter (a case-insensitive substring matched against ids and
 * names) so a long list can be searched client-side or server-side, and both cap their output. Any
 * other section name yields an empty object, as Core's contract prescribes.
 *
 * <h2>Privacy (POPIA/GDPR)</h2>
 *
 * <p>The {@code playerId} argument is <b>read for nothing but a null check</b>. Every byte returned
 * here is public content metadata — registry ids, hex colours, enum names — identical for every
 * requester and already visible to anyone who opens the creative tab. NeroDecor stores no
 * player-attributable data anywhere in the mod (a block's paint colour is world state and records
 * nobody), so there is no scoping to do and no filtering the caller must trust.
 *
 * <p><b>Read-only and bounded.</b> Nothing here mutates anything, touches the world, or loads a chunk:
 * the block catalogue is read from {@link DecorBlocks#ALL}, the mod's own registration-order list, and
 * the palette from Core's registry snapshot.
 *
 * <p>Server thread only (as Core's contract specifies), though nothing here is thread-sensitive.
 */
public final class NeroDecorLinkSnapshots implements LinkSnapshotProvider {

    private static final List<String> SECTIONS = List.of(
            NeroDecorLinkModule.SECTION_PALETTE,
            NeroDecorLinkModule.SECTION_BLOCKS);

    /** Hard cap on rows per list, so a data pack that registers a thousand finishes cannot flood a phone. */
    private static final int MAX_ROWS = 512;

    /** The CTM family whose members render fullbright. Mirrors {@code DecorBlocks}' neon preset. */
    private static final String NEON_FAMILY = "neon";

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
        return SECTIONS;
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
        return root;
    }

    // --- section: blocks ------------------------------------------------------

    /**
     * The registered decor block set, in registration order — the same order the creative tab and
     * datagen iterate, so an app's list matches what a player sees in game.
     *
     * <p>Family and connected-texture style come from the block's own {@link CtmSurface} contract, and
     * the shape from its class, so a block added to {@code DecorBlocks} appears here with no change to
     * this file. {@code emissive} is derived from the CTM family rather than from a light-level read:
     * the neon family is the fullbright one, and asking a blockstate for its emission would be a
     * render-side question this catalogue has no business asking.
     *
     * <p>The Luminous collection (0.4.0) registers into the same list but does not carry the
     * {@code COLOR} property and is not a {@link CtmSurface}: those rows report {@code paintable=false},
     * {@code collection="luminous"} and {@code emissive=true}, and carry no family fields.
     */
    private static JsonObject blocks(String query) {
        JsonObject root = envelope();
        JsonArray rows = new JsonArray();
        int total = 0;

        for (RegistryEntry<? extends Block> entry : DecorBlocks.ALL) {
            total++;
            if (rows.size() >= MAX_ROWS) {
                continue;
            }
            Identifier id = entry.id();
            String name = readable(id.getPath());
            if (!matches(query, id.toString(), name)) {
                continue;
            }
            Block block = entry.get();
            JsonObject row = new JsonObject();
            row.addProperty("id", id.toString());
            row.addProperty("name", name);
            // Built from the id rather than read off the block: the vanilla convention the mod's own
            // lang files already follow, and one less render-side API in a catalogue.
            row.addProperty("translation_key", "block." + id.getNamespace() + "." + id.getPath());
            row.addProperty("shape", shapeOf(block));
            // Paintable families carry the COLOR blockstate property; the Luminous collection does not.
            boolean paintable = block.defaultBlockState().hasProperty(DecorProperties.COLOR);
            row.addProperty("paintable", paintable);
            row.addProperty("collection", paintable ? "decor" : "luminous");

            String family = null;
            if (block instanceof CtmSurface surface) {
                CtmKey key = surface.ctmKey(block.defaultBlockState());
                family = key.family().getPath();
                row.addProperty("family", key.family().toString());
                row.addProperty("family_name", readable(family));
                row.addProperty("ctm_style", surface.ctmStyle().name());
            }
            // Every Luminous block glows by design; among the paintable families only neon is fullbright.
            row.addProperty("emissive", !paintable || NEON_FAMILY.equals(family));
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

    /** The block's geometry, from its class. Unknown classes report {@code "block"} rather than guessing. */
    private static String shapeOf(Block block) {
        if (block instanceof DecorCubeBlock) {
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
        if (block instanceof RotatedPillarBlock) {
            return "pillar";
        }
        return "block";
    }

    // --- helpers --------------------------------------------------------------

    /** The one field every NeroDecor snapshot root starts with. There is no player state to report. */
    private static JsonObject envelope() {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", NeroDecorLinkModule.SCHEMA_VERSION);
        return root;
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
