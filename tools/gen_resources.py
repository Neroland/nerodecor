#!/usr/bin/env python3
"""
NeroDecor resource harness — emits the committed, loader-agnostic JSON (blockstates, block +
item models, loot tables, recipes, tags, lang) for the registered decor blocks into
common/src/main/resources, matching the Core/nerospace convention (hand-committed JSON, NO
runtime datagen). Deterministic + idempotent: re-running rewrites byte-stable files.

The SPEC below mirrors registry/DecorBlocks.java — keep them in lockstep. Textures come from
tools/gen_textures.py; models here reference nerodecor:block/<texture>.

Usage: python tools/gen_resources.py [--check]   (or ./gradlew genAssets)
       --check writes nothing and exits 1 on any new / stale / orphan file.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
RES = os.path.join(REPO, "common", "src", "main", "resources")
NS = "nerodecor"

# name, kind, texture (base cube texture id path), family, recipe-material (Core ingot / vanilla)
# Finish sets — keep in LOCKSTEP with tools/gen_textures.py + registry/DecorBlocks.java.
STRUCT = ["nero_alloy", "starsteel", "void_crystal"]
GLASS_FIN = ["plasma_glass", "cyan", "light_blue"]
NEON = ["red", "orange", "yellow", "lime", "green", "cyan",
        "light_blue", "blue", "purple", "magenta", "pink", "white"]
STRUCT_MAT = {"nero_alloy": "nerolandcore:nero_alloy_ingot",
              "starsteel": "nerolandcore:starsteel_ingot",
              "void_crystal": "nerolandcore:void_crystal_shard"}
GLASS_MAT = {"plasma_glass": "nerolandcore:plasma_glass_block",
             "cyan": "minecraft:cyan_stained_glass",
             "light_blue": "minecraft:light_blue_stained_glass"}


def _build_spec():
    # (name, kind, cube-texture, family, recipe-material)
    s = []
    for m in STRUCT:
        s.append(("hull_%s" % m, "cube", "hull_%s" % m, "hull", STRUCT_MAT[m]))
        s.append(("hull_%s_slab" % m, "slab", "hull_%s" % m, "hull", None))
        s.append(("hull_%s_stairs" % m, "stairs", "hull_%s" % m, "hull", None))
        s.append(("hull_%s_wall" % m, "wall", "hull_%s" % m, "hull", None))
    for m in STRUCT:
        s.append(("panel_%s" % m, "cube", "panel_%s" % m, "panel", STRUCT_MAT[m]))
        s.append(("panel_%s_slab" % m, "slab", "panel_%s" % m, "panel", None))
        s.append(("panel_%s_stairs" % m, "stairs", "panel_%s" % m, "panel", None))
    for f in GLASS_FIN:
        s.append(("glass_%s" % f, "cube", "glass_%s" % f, "glass", GLASS_MAT[f]))
        s.append(("glass_%s_pane" % f, "pane", "glass_%s" % f, "glass", None))
        s.append(("glass_%s_slab" % f, "slab", "glass_%s" % f, "glass", None))
    for c in NEON:
        s.append(("neon_%s" % c, "cube", "neon_%s" % c, "neon", c))
    return s


SPEC = _build_spec()
CUBE_OF = {t: name for (name, kind, t, fam, mat) in SPEC if kind == "cube"}

files = {}          # relative path -> dict (written as JSON)
lang = {}

# Hand-written strings that ride along in en_us.json. LANG_HEAD sits above the generated block keys;
# QUEST_LANG (the NeroQuests "Architect" chapter whose data lives in data/nerodecor/neroquests/) is
# appended after a blank line, in authored order. English is the source; other languages via Weblate.
# Chroma (0.8.0) chat/action-bar messages follow the tab title; block and item names for the hub,
# linker and other collection items come from tools/collections via collections.aggregate().
LANG_HEAD = [
    ("itemGroup.%s" % NS, "NeroDecor"),
    ("message.nerodecor.chroma.ok",
     "Linked."),
    ("message.nerodecor.chroma.already_linked",
     "Already linked to this hub."),
    ("message.nerodecor.chroma.disabled",
     "Chroma is disabled on this server."),
    ("message.nerodecor.chroma.inactive",
     "That hub is not placed."),
    ("message.nerodecor.chroma.no_network",
     "No Chroma network here."),
    ("message.nerodecor.chroma.no_permission",
     "You are not trusted for that on this hub."),
    ("message.nerodecor.chroma.protected",
     "That block is protected."),
    ("message.nerodecor.chroma.not_capable",
     "That block cannot be controlled by a hub."),
    ("message.nerodecor.chroma.out_of_volume",
     "That block is outside the hub's volume."),
    ("message.nerodecor.chroma.volume_too_large",
     "A hub volume is at most 64 blocks on each side."),
    ("message.nerodecor.chroma.claimed_by_other_hub",
     "That block already belongs to another hub."),
    ("message.nerodecor.chroma.block_cap",
     "This hub controls as many blocks as it can."),
    ("message.nerodecor.chroma.hub_cap",
     "You own as many hubs as allowed."),
    ("message.nerodecor.chroma.zone_cap",
     "This hub has as many zones as allowed."),
    ("message.nerodecor.chroma.bad_zone",
     "Unknown zone."),
    ("message.nerodecor.chroma.bad_name",
     "Invalid name."),
    ("message.nerodecor.chroma.bad_channel",
     "Channels run from 0 to 15."),
    ("message.nerodecor.chroma.not_linked",
     "Unlinked."),
    ("message.nerodecor.chroma.selection_too_large",
     "That selection is too large."),
    ("message.nerodecor.chroma.rate_limited",
     "Slow down: too many Chroma edits."),
    ("message.nerodecor.chroma.selection",
     "Linked %s blocks (%s skipped)."),
    ("message.nerodecor.chroma.status",
     "Chroma Hub: %s blocks, %s zones."),
    ("message.nerodecor.chroma.auto_adopt.on",
     "Auto-adopt on: blocks you place in range join this hub."),
    ("message.nerodecor.chroma.auto_adopt.off",
     "Auto-adopt off."),
    ("message.nerodecor.chroma.linker.bound",
     "Linker bound to this hub."),
    ("message.nerodecor.chroma.linker.unbound",
     "Use the linker on a Chroma Hub first."),
    ("message.nerodecor.chroma.linker.corner",
     "First corner set. Use on the opposite corner."),
    ("message.nerodecor.chroma.linker.mode_box",
     "Box mode: mark two corners."),
    ("message.nerodecor.chroma.linker.mode_single",
     "Single mode: click blocks to link or unlink."),
    ("message.nerodecor.chroma.bad_op",
     "That Chroma edit was malformed and was ignored."),
    ("message.nerodecor.chroma.claimed",
     "You now own this orphaned Chroma hub network."),
    ("message.nerodecor.chroma.no_orphan",
     "You are not inside an orphaned Chroma hub network."),
    ("message.nerodecor.privacy.export.done",
     "Your NeroDecor data was written to %s in the world folder. It is deleted after %s days."),
    ("message.nerodecor.privacy.export.cooldown",
     "Please wait %s s before exporting again."),
    ("message.nerodecor.privacy.export.failed",
     "Your NeroDecor data could not be exported. Ask the server operator."),
    ("message.nerodecor.paint_gun.selected",
     "Paint: %s"),
    ("message.nerodecor.paint_gun.picked",
     "Copied paint: %s"),
    ("entity.nerodecor.seat",
     "Seat"),
    ("screen.nerodecor.holo_sign",
     "Holo Sign"),
    ("screen.nerodecor.holo_sign.line1",
     "Line 1"),
    ("screen.nerodecor.holo_sign.line2",
     "Line 2"),
    ("screen.nerodecor.chroma_hub",
     "Chroma Hub"),
    ("screen.nerodecor.chroma_hub.title",
     "CHROMA HUB"),
    ("screen.nerodecor.chroma_hub.tab.zones",
     "Zones"),
    ("screen.nerodecor.chroma_hub.tab.palette",
     "Palette"),
    ("screen.nerodecor.chroma_hub.tab.scenes",
     "Scenes"),
    ("screen.nerodecor.chroma_hub.tab.access",
     "Access"),
    ("screen.nerodecor.chroma_hub.tab.view",
     "3D View"),
    ("screen.nerodecor.chroma_hub.role.none",
     "No access"),
    ("screen.nerodecor.chroma_hub.role.viewer",
     "Viewer"),
    ("screen.nerodecor.chroma_hub.role.painter",
     "Painter"),
    ("screen.nerodecor.chroma_hub.role.admin",
     "Admin"),
    ("screen.nerodecor.chroma_hub.role.owner",
     "Owner"),
    ("screen.nerodecor.chroma_hub.chip.blocks",
     "Blocks %s/%s"),
    ("screen.nerodecor.chroma_hub.chip.zones",
     "Zones %s/%s"),
    ("screen.nerodecor.chroma_hub.chip.scene",
     "Scene: %s"),
    ("screen.nerodecor.chroma_hub.chip.online",
     "Online"),
    ("screen.nerodecor.chroma_hub.chip.offline",
     "Hub removed"),
    ("screen.nerodecor.chroma_hub.scene.static",
     "Static"),
    ("screen.nerodecor.chroma_hub.zones.header",
     "ZONES"),
    ("screen.nerodecor.chroma_hub.zones.columns",
     "Channel  Blocks"),
    ("screen.nerodecor.chroma_hub.zones.blocks",
     "%s blk"),
    ("screen.nerodecor.chroma_hub.zones.rename",
     "Rename"),
    ("screen.nerodecor.chroma_hub.zones.add",
     "Add zone"),
    ("screen.nerodecor.chroma_hub.zones.channel",
     "Channel"),
    ("screen.nerodecor.chroma_hub.zones.manage_only",
     "Admins manage zones"),
    ("screen.nerodecor.chroma_hub.palette.header",
     "PALETTE  %s/256"),
    ("screen.nerodecor.chroma_hub.palette.empty",
     "No hub colours yet: linked blocks show their own colour until you paint them."),
    ("screen.nerodecor.chroma_hub.palette.entry_hint",
     "Entry #%s, used by %s blocks"),
    ("screen.nerodecor.chroma_hub.palette.info",
     "#%s  %s  light %s  used by %s blocks"),
    ("screen.nerodecor.chroma_hub.palette.pick",
     "Pick an entry to edit it."),
    ("screen.nerodecor.chroma_hub.palette.legend",
     "A dark corner marks an entry no block uses; it is reused first."),
    ("screen.nerodecor.chroma_hub.scenes.header",
     "SCENE SLOTS"),
    ("screen.nerodecor.chroma_hub.scenes.none",
     "This hub has no scene slots."),
    ("screen.nerodecor.chroma_hub.scenes.none2",
     "Scenes arrive with the hub's scene system."),
    ("screen.nerodecor.chroma_hub.scenes.slot",
     "Slot %s"),
    ("screen.nerodecor.chroma_hub.scenes.active",
     "LIVE"),
    ("screen.nerodecor.chroma_hub.scenes.play",
     "Play"),
    ("screen.nerodecor.chroma_hub.scenes.static",
     "Static colours"),
    ("screen.nerodecor.chroma_hub.scenes.rename",
     "Rename"),
    ("screen.nerodecor.chroma_hub.scenes.copy",
     "Copy"),
    ("screen.nerodecor.chroma_hub.scenes.import",
     "Import"),
    ("screen.nerodecor.chroma_hub.scenes.store",
     "Store effect"),
    ("screen.nerodecor.chroma_hub.scenes.mode.slots",
     "Slots"),
    ("screen.nerodecor.chroma_hub.scenes.mode.compose",
     "Compose"),
    ("screen.nerodecor.chroma_hub.hint.compose",
     "Build a scene zone by zone: an effect or a keyframe timeline for each zone."),
    ("screen.nerodecor.chroma_hub.compose.header",
     "COMPOSE"),
    ("screen.nerodecor.chroma_hub.compose.zone",
     "Zone %s/%s: %s"),
    ("screen.nerodecor.chroma_hub.compose.effect",
     "Effect"),
    ("screen.nerodecor.chroma_hub.compose.timeline",
     "Timeline"),
    ("screen.nerodecor.chroma_hub.compose.all_zones",
     "Copy to all"),
    ("screen.nerodecor.chroma_hub.hint.all_zones",
     "Give every zone this zone's program. Each zone keeps its channel."),
    ("screen.nerodecor.chroma_hub.compose.type",
     "Type"),
    ("screen.nerodecor.chroma_hub.compose.period",
     "Period"),
    ("screen.nerodecor.chroma_hub.hint.period",
     "One cycle of the effect (sparkle: how long one twinkle lasts)."),
    ("screen.nerodecor.chroma_hub.compose.amplitude",
     "Amp"),
    ("screen.nerodecor.chroma_hub.hint.amplitude",
     "How far towards colour B the effect swings (rainbow: saturation)."),
    ("screen.nerodecor.chroma_hub.compose.axis",
     "Axis %s"),
    ("screen.nerodecor.chroma_hub.hint.axis",
     "The axis chase, wave and rainbow travel along."),
    ("screen.nerodecor.chroma_hub.compose.wavelength",
     "Wave"),
    ("screen.nerodecor.chroma_hub.hint.wavelength",
     "Blocks per spatial cycle for chase, wave, ripple and rainbow."),
    ("screen.nerodecor.chroma_hub.compose.density",
     "Density"),
    ("screen.nerodecor.chroma_hub.hint.density",
     "How many blocks sparkle lights."),
    ("screen.nerodecor.chroma_hub.compose.colours",
     "Colours from picker"),
    ("screen.nerodecor.chroma_hub.hint.colours",
     "Sets this zone's colours to picker A and picker B."),
    ("screen.nerodecor.chroma_hub.compose.key",
     "Key %s of %s"),
    ("screen.nerodecor.chroma_hub.compose.up",
     "Up"),
    ("screen.nerodecor.chroma_hub.compose.down",
     "Down"),
    ("screen.nerodecor.chroma_hub.hint.move_key",
     "Swaps this key's colour and easing with its neighbour. Ticks stay in order."),
    ("screen.nerodecor.chroma_hub.hint.tick",
     "When this key is reached, in ticks (20 per second)."),
    ("screen.nerodecor.chroma_hub.hint.tick10",
     "Move this key by 10 ticks (half a second)."),
    ("screen.nerodecor.chroma_hub.compose.easing",
     "Ease"),
    ("screen.nerodecor.chroma_hub.hint.easing",
     "How the colour travels from the previous key to this one."),
    ("screen.nerodecor.chroma_hub.compose.key_colour",
     "Set key colour to picker A"),
    ("screen.nerodecor.chroma_hub.hint.key_colour",
     "Click a key to load its colour into the picker, adjust it, then press this."),
    ("screen.nerodecor.chroma_hub.compose.add_key",
     "+ Key"),
    ("screen.nerodecor.chroma_hub.hint.add_key",
     "Adds a key after the selected one, in picker A's colour. Up to 32 keys."),
    ("screen.nerodecor.chroma_hub.compose.remove_key",
     "Remove"),
    ("screen.nerodecor.chroma_hub.compose.length",
     "Length"),
    ("screen.nerodecor.chroma_hub.hint.length",
     "Timeline length. A looping timeline repeats with this period."),
    ("screen.nerodecor.chroma_hub.compose.loop_on",
     "Loop: on"),
    ("screen.nerodecor.chroma_hub.compose.loop_off",
     "Loop: off (plays once)"),
    ("screen.nerodecor.chroma_hub.hint.loop",
     "Looping blends the last key back into the first."),
    ("screen.nerodecor.chroma_hub.compose.preview",
     "PREVIEW"),
    ("screen.nerodecor.chroma_hub.compose.cycle",
     "cycle %s"),
    ("screen.nerodecor.chroma_hub.hint.time_strip",
     "One cycle at the hub, left to right. The white marker is now."),
    ("screen.nerodecor.chroma_hub.hint.space_strip",
     "A row of blocks along the axis, right now."),
    ("screen.nerodecor.chroma_hub.hint.live",
     "This zone, live."),
    ("screen.nerodecor.chroma_hub.compose.slot",
     "Slot %s"),
    ("screen.nerodecor.chroma_hub.compose.load",
     "Load"),
    ("screen.nerodecor.chroma_hub.hint.load",
     "Load the selected slot's scene into the composer."),
    ("screen.nerodecor.chroma_hub.compose.store",
     "Store scene"),
    ("screen.nerodecor.chroma_hub.hint.store_scene",
     "Checks the flash limit, then stores this scene in the selected slot."),
    ("screen.nerodecor.chroma_hub.compose.default_name",
     "Custom scene"),
    ("screen.nerodecor.chroma_hub.compose.flash_banner",
     "Refused: this scene flashes more than 3 times a second over a large area. Slow it down or soften the colours."),
    ("screen.nerodecor.chroma_hub.hint.scene_name",
     "Scene name"),
    ("screen.nerodecor.chroma_hub.status.stored_scene",
     "Scene sent to slot %s."),
    ("screen.nerodecor.chroma_hub.status.loaded",
     "Slot loaded into the composer."),
    ("screen.nerodecor.chroma_hub.status.copied_all",
     "Copied to every zone."),
    ("screen.nerodecor.chroma_hub.status.key_full",
     "No room for another key there."),
    ("screen.nerodecor.chroma_hub.picker.compose_note",
     "Pick a colour here, then use a colour button in the composer."),
    ("screen.nerodecor.chroma_hub.easing.linear",
     "Linear"),
    ("screen.nerodecor.chroma_hub.easing.ease_in",
     "Ease in"),
    ("screen.nerodecor.chroma_hub.easing.ease_out",
     "Ease out"),
    ("screen.nerodecor.chroma_hub.easing.ease_in_out",
     "Ease in-out"),
    ("screen.nerodecor.chroma_hub.easing.step",
     "Step"),
    ("screen.nerodecor.chroma_hub.effect.pulse",
     "Pulse"),
    ("screen.nerodecor.chroma_hub.effect.breathe",
     "Breathe"),
    ("screen.nerodecor.chroma_hub.effect.chase",
     "Chase"),
    ("screen.nerodecor.chroma_hub.effect.axis_wave",
     "Axis wave"),
    ("screen.nerodecor.chroma_hub.effect.radial_ripple",
     "Ripple"),
    ("screen.nerodecor.chroma_hub.effect.rainbow",
     "Rainbow"),
    ("screen.nerodecor.chroma_hub.effect.sparkle",
     "Sparkle"),
    ("screen.nerodecor.chroma_hub.effect.sky_mirror",
     "Sky mirror"),
    ("screen.nerodecor.chroma_hub.access.header",
     "ACCESS"),
    ("screen.nerodecor.chroma_hub.access.trusted",
     "TRUSTED"),
    ("screen.nerodecor.chroma_hub.access.online",
     "ONLINE PLAYERS"),
    ("screen.nerodecor.chroma_hub.access.manage_only",
     "Only the owner and admins can see who is trusted."),
    ("screen.nerodecor.chroma_hub.access.nobody",
     "Nobody is trusted yet."),
    ("screen.nerodecor.chroma_hub.access.no_one_online",
     "No other players online."),
    ("screen.nerodecor.chroma_hub.access.offline",
     "Offline player"),
    ("screen.nerodecor.chroma_hub.access.offline_id",
     "Offline player %s"),
    ("screen.nerodecor.chroma_hub.access.trusted_count",
     "TRUSTED %s/%s"),
    ("screen.nerodecor.chroma_hub.access.owner_only",
     "Only the owner can change roles."),
    ("screen.nerodecor.chroma_hub.access.v",
     "V"),
    ("screen.nerodecor.chroma_hub.access.p",
     "P"),
    ("screen.nerodecor.chroma_hub.access.a",
     "A"),
    ("screen.nerodecor.chroma_hub.access.auto_on",
     "Auto-adopt: ON"),
    ("screen.nerodecor.chroma_hub.access.auto_off",
     "Auto-adopt: OFF"),
    ("screen.nerodecor.chroma_hub.tool.orbit",
     "Orbit"),
    ("screen.nerodecor.chroma_hub.tool.orbit.hint",
     "Drag to orbit. Right-drag orbits and middle-drag pans in every tool."),
    ("screen.nerodecor.chroma_hub.tool.brush",
     "Brush"),
    ("screen.nerodecor.chroma_hub.tool.brush.hint",
     "Click or drag across blocks to paint them."),
    ("screen.nerodecor.chroma_hub.tool.box",
     "Box"),
    ("screen.nerodecor.chroma_hub.tool.box.hint",
     "Click two corners; every linked block between them is painted."),
    ("screen.nerodecor.chroma_hub.tool.fill",
     "Fill"),
    ("screen.nerodecor.chroma_hub.tool.fill.hint",
     "Flood fill from a block through touching blocks that match."),
    ("screen.nerodecor.chroma_hub.tool.gradient",
     "Gradient"),
    ("screen.nerodecor.chroma_hub.tool.gradient.hint",
     "Click a start and an end block: the start block's zone fades from colour B to A."),
    ("screen.nerodecor.chroma_hub.tool.axis",
     "Axis"),
    ("screen.nerodecor.chroma_hub.tool.axis.hint",
     "Click a block: its zone fades from B to A along the chosen axis."),
    ("screen.nerodecor.chroma_hub.tool.pick",
     "Pick"),
    ("screen.nerodecor.chroma_hub.tool.pick.hint",
     "Click a block to copy its colour into the picker."),
    ("screen.nerodecor.chroma_hub.view.mode_colour",
     "Paint colour"),
    ("screen.nerodecor.chroma_hub.view.mode_zone",
     "Assign zone: %s"),
    ("screen.nerodecor.chroma_hub.view.mode.hint",
     "Switch between painting colour and assigning blocks to the selected zone."),
    ("screen.nerodecor.chroma_hub.view.match.linked",
     "Match: any"),
    ("screen.nerodecor.chroma_hub.view.match.zone",
     "Match: zone"),
    ("screen.nerodecor.chroma_hub.view.match.colour",
     "Match: colour"),
    ("screen.nerodecor.chroma_hub.view.match.zone_and_colour",
     "Match: zone + colour"),
    ("screen.nerodecor.chroma_hub.view.match.hint",
     "What the fill must match to spread."),
    ("screen.nerodecor.chroma_hub.view.axis",
     "Axis: %s"),
    ("screen.nerodecor.chroma_hub.view.frame",
     "Frame"),
    ("screen.nerodecor.chroma_hub.view.frame.hint",
     "Recentre the camera on the hub's blocks."),
    ("screen.nerodecor.chroma_hub.view.count",
     "%s blocks"),
    ("screen.nerodecor.chroma_hub.view.lod",
     "Plain cubes"),
    ("screen.nerodecor.chroma_hub.view.slice",
     "Y slice: showing blocks at or below %s"),
    ("screen.nerodecor.chroma_hub.view.second_point",
     "Click the second block. Press another tool to cancel."),
    ("screen.nerodecor.chroma_hub.view.controls",
     "Wheel zoom  Right-drag orbit  Middle-drag pan"),
    ("screen.nerodecor.chroma_hub.view.hover",
     "%s, %s, %s  %s  %s"),
    ("screen.nerodecor.chroma_hub.picker.sv",
     "Saturation and value"),
    ("screen.nerodecor.chroma_hub.picker.hue",
     "Hue"),
    ("screen.nerodecor.chroma_hub.picker.light",
     "Light level the block gives off"),
    ("screen.nerodecor.chroma_hub.picker.light_value",
     "Light %s"),
    ("screen.nerodecor.chroma_hub.picker.swap",
     "Colour B: click to swap with A"),
    ("screen.nerodecor.chroma_hub.picker.paint_zone",
     "Paint zone with A"),
    ("screen.nerodecor.chroma_hub.picker.set_entry",
     "Set entry #%s to A"),
    ("screen.nerodecor.chroma_hub.picker.scene_note",
     "Store effect puts one effect on every zone, moving between picker B and picker A."),
    ("screen.nerodecor.chroma_hub.picker.gradient_note",
     "Gradients run from B to A."),
    ("screen.nerodecor.chroma_hub.footer.apply",
     "Apply"),
    ("screen.nerodecor.chroma_hub.footer.apply_n",
     "Apply %s"),
    ("screen.nerodecor.chroma_hub.footer.revert",
     "Revert"),
    ("screen.nerodecor.chroma_hub.footer.undo",
     "Undo"),
    ("screen.nerodecor.chroma_hub.footer.redo",
     "Redo"),
    ("screen.nerodecor.chroma_hub.hint.zone_name",
     "Zone name"),
    ("screen.nerodecor.chroma_hub.hint.slot_name",
     "Slot name"),
    ("screen.nerodecor.chroma_hub.hint.share_code",
     "Share code (ND1-...)"),
    ("screen.nerodecor.chroma_hub.hint.rename",
     "Rename the selected zone."),
    ("screen.nerodecor.chroma_hub.hint.add_zone",
     "Add a zone with this name."),
    ("screen.nerodecor.chroma_hub.hint.apply",
     "Send your colour changes to the hub."),
    ("screen.nerodecor.chroma_hub.hint.auto_adopt",
     "New capable blocks placed in the volume join the hub."),
    ("screen.nerodecor.chroma_hub.hint.copy",
     "Copy the slot's share code to the clipboard."),
    ("screen.nerodecor.chroma_hub.hint.import",
     "Store the share code (or the clipboard) in the selected slot."),
    ("screen.nerodecor.chroma_hub.hint.paint_zone",
     "Give every block in the selected zone colour A."),
    ("screen.nerodecor.chroma_hub.hint.revoke",
     "Remove access"),
    ("screen.nerodecor.chroma_hub.hint.set_entry",
     "Change this palette entry; every block using it follows."),
    ("screen.nerodecor.chroma_hub.hint.static",
     "Stop the scene and show the static colours."),
    ("screen.nerodecor.chroma_hub.hint.store",
     "Store the effect, on every zone, in the selected slot."),
    ("screen.nerodecor.chroma_hub.status.changed_elsewhere",
     "The hub changed elsewhere. Apply or Revert your edits."),
    ("screen.nerodecor.chroma_hub.status.copied",
     "Share code copied."),
    ("screen.nerodecor.chroma_hub.status.imported",
     "Scene sent to the slot."),
    ("screen.nerodecor.chroma_hub.status.stored",
     "Effect sent to the slot."),
    ("screen.nerodecor.chroma_hub.status.bad_code",
     "That is not a valid share code."),
    ("screen.nerodecor.chroma_hub.status.bad_effect",
     "That effect cannot be built."),
    ("screen.nerodecor.chroma_hub.status.too_big",
     "The scene is too large."),
    ("screen.nerodecor.chroma_hub.status.palette_full",
     "The palette is full. Reuse a colour or free an entry."),
    ("screen.nerodecor.chroma_hub.status.selected",
     "%s blocks"),
    ("screen.nerodecor.chroma_hub.status.sent",
     "Sent %s changes."),
    ("screen.nerodecor.chroma_hub.status.sent_batches",
     "Sent %s changes in %s batches."),
    ("screen.nerodecor.chroma_hub.status.view_only",
     "You can view this hub but not change it."),
    # Chroma Stage 4 (scenes, Scene Chip)
    ("message.nerodecor.chroma.flash_rejected",
     "That scene flashes more than 3 times a second over a large area, which can harm photosensitive players. Slow it down."),
    ("message.nerodecor.chroma.bad_slot",
     "That scene slot is empty or does not exist."),
    ("message.nerodecor.chroma.bad_scene",
     "That scene is damaged or too large and was not used."),
    ("message.nerodecor.chroma.trust_cap",
     "This hub already trusts 64 players. Revoke someone first."),
    ("message.nerodecor.chroma.unknown_player",
     "That player is not known to this server."),
    ("message.nerodecor.chroma.too_far",
     "Stand near the hub to change it."),
    ("message.nerodecor.scene_chip.loaded",
     "Scene loaded into slot %s."),
    ("message.nerodecor.scene_chip.copied",
     "Copied the playing scene onto the chip."),
    ("message.nerodecor.scene_chip.nothing_playing",
     "This hub is not playing a scene."),
    ("message.nerodecor.scene_chip.empty",
     "This chip holds no scene."),
    ("tooltip.nerodecor.scene_chip.scene",
     "%s - %s zones"),
    ("tooltip.nerodecor.scene_chip.unnamed",
     "Unnamed scene"),
    ("tooltip.nerodecor.scene_chip.blank",
     "Blank chip"),
    # Recipe-viewer information pages (compat/ViewerInfo: JEI and EMI)
    ("info.nerodecor.chroma_hub",
     "The heart of a Chroma network. Use it with an empty hand to open the hub screen: paint linked blocks, "
     "sort them into zones, keep a palette, store and play scenes, and grant roles. Viewers look, Painters "
     "paint and play scenes, Admins manage zones, channels and auto-adopt, and the Owner grants roles. "
     "Sneak-use toggles auto-adopt (Admins)."),
    ("info.nerodecor.chroma_linker",
     "Chooses which blocks a Chroma Hub controls. Use it on a hub to bind it (Admin role). Use it on a "
     "chroma-capable block to link it, or again to unlink it. Use it in the air to switch to Box mode: mark "
     "two corners to link every capable block between them. Sneak-use on a block flood-links the capable "
     "blocks touching it."),
    ("info.nerodecor.paint_gun",
     "Paints blocks without a hub. Use it on a paintable block to paint it the gun's colour. Sneak-use on a "
     "block copies that block's colour into the gun. Use it in the air for the next colour, sneak-use in the "
     "air for the previous one."),
    ("info.nerodecor.scene_chip",
     "Carries one Chroma scene between hubs. Sneak-use on a hub to copy the scene it is playing onto the "
     "chip. Use the chip on another hub to load the scene into a free slot and play it (Painter role). In "
     "the hub screen, Copy and Import move the same scene as a share code (ND1-...)."),
] + [("color.nerodecor.%s" % c, n) for c, n in (
    ("natural", "Natural"), ("white", "White"), ("light_gray", "Light Gray"), ("gray", "Gray"),
    ("black", "Black"), ("red", "Red"), ("orange", "Orange"), ("yellow", "Yellow"), ("lime", "Lime"),
    ("green", "Green"), ("cyan", "Cyan"), ("light_blue", "Light Blue"), ("blue", "Blue"),
    ("purple", "Purple"), ("magenta", "Magenta"), ("pink", "Pink"), ("brown", "Brown"))]
QUEST_LANG = [
    ("chapter.nerodecor.architect.title",
     "Architect"),
    ("quest.nerodecor.architect.first_light.title",
     "First Light"),
    ("quest.nerodecor.architect.first_light.desc",
     "Eight Chroma Cubes, any colour. A base can be functional and still glow - this is where it starts to look like somewhere people live."),
    ("quest.nerodecor.architect.hull_plating.title",
     "Hull Plating"),
    ("quest.nerodecor.architect.hull_plating.desc",
     "Sixteen pieces of hull - blocks, slabs, stairs or walls, in any alloy. The same plating that skins a starship makes a handsome workshop."),
    ("quest.nerodecor.architect.clean_lines.title",
     "Clean Lines"),
    ("quest.nerodecor.architect.clean_lines.desc",
     "Sixteen panels in any finish. Flat, even, ordered - the quiet backdrop that lets everything else on the wall stand out."),
    ("quest.nerodecor.architect.through_the_glass.title",
     "Through the Glass"),
    ("quest.nerodecor.architect.through_the_glass.desc",
     "Eight pieces of tinted glass. Give the hull a window and the view becomes part of the design."),
    ("quest.nerodecor.architect.starsteel_frame.title",
     "Starsteel Frame"),
    ("quest.nerodecor.architect.starsteel_frame.desc",
     "Four starsteel pillars. Every grand hall needs a skeleton, and these carry the look of a station bulkhead."),
    ("quest.nerodecor.architect.circuitry.title",
     "Circuitry"),
    ("quest.nerodecor.architect.circuitry.desc",
     "Circuit plating for the walls and a holo-grid underfoot. A control room that looks like a control room."),
    ("quest.nerodecor.architect.power_aesthetic.title",
     "Power Aesthetic"),
    ("quest.nerodecor.architect.power_aesthetic.desc",
     "A fusion lamp and a pair of capacitor banks. Nothing here generates a single watt, and nobody visiting will ever believe that."),
    ("quest.nerodecor.architect.into_the_void.title",
     "Into the Void"),
    ("quest.nerodecor.architect.into_the_void.desc",
     "Void rifts and a crystal lattice. A little deep space, hung on the wall where you can admire it safely."),
    ("quest.nerodecor.architect.xeno_garden.title",
     "Xeno Garden"),
    ("quest.nerodecor.architect.xeno_garden.desc",
     "Xeno blooms under aurora glass. An alien garden is proof that a base can be a home, not just a factory."),
    ("quest.nerodecor.architect.full_spectrum.title",
     "Full Spectrum"),
    ("quest.nerodecor.architect.full_spectrum.desc",
     "Hold twelve Chroma Cubes - dye them every colour you like. A complete palette, ready for whatever you build next."),
    ("quest.nerodecor.architect.master_architect.title",
     "Master Architect"),
    ("quest.nerodecor.architect.master_architect.desc",
     "Power, void and garden: you have built with every family NeroDecor has to offer. Whatever stands on your land now, it was designed."),
]
mineable = []
decor_tags = {}     # family -> [block ids]
walls = []          # wall block ids — need #minecraft:walls to connect to each other


# --- tinted shape templates ---------------------------------------------------
# Vanilla's slab / stair / wall / pane templates carry no "tintindex", so a paintable shape that used
# them directly would never show its DecorColor. These are the vanilla element lists (identical in
# 1.21.1 and 26.3), re-emitted as nerodecor:block/tinted/<template> with "tintindex": 0 on every face.
# Each tinted template keeps the vanilla template as its parent, so display transforms and
# ambient-occlusion still come from the running version; only the elements are overridden.
VANILLA_GEOMETRY = {
    "slab": [
        {"from": [0, 0, 0], "to": [16, 8, 16], "faces": {"down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"}, "up": {"uv": [0, 0, 16, 16], "texture": "#top"}, "north": {"uv": [0, 8, 16, 16], "texture": "#side", "cullface": "north"}, "south": {"uv": [0, 8, 16, 16], "texture": "#side", "cullface": "south"}, "west": {"uv": [0, 8, 16, 16], "texture": "#side", "cullface": "west"}, "east": {"uv": [0, 8, 16, 16], "texture": "#side", "cullface": "east"}}},
    ],
    "slab_top": [
        {"from": [0, 8, 0], "to": [16, 16, 16], "faces": {"down": {"uv": [0, 0, 16, 16], "texture": "#bottom"}, "up": {"uv": [0, 0, 16, 16], "texture": "#top", "cullface": "up"}, "north": {"uv": [0, 0, 16, 8], "texture": "#side", "cullface": "north"}, "south": {"uv": [0, 0, 16, 8], "texture": "#side", "cullface": "south"}, "west": {"uv": [0, 0, 16, 8], "texture": "#side", "cullface": "west"}, "east": {"uv": [0, 0, 16, 8], "texture": "#side", "cullface": "east"}}},
    ],
    "stairs": [
        {"from": [0, 0, 0], "to": [16, 8, 16], "faces": {"down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"}, "up": {"uv": [0, 0, 16, 16], "texture": "#top"}, "north": {"uv": [0, 8, 16, 16], "texture": "#side", "cullface": "north"}, "south": {"uv": [0, 8, 16, 16], "texture": "#side", "cullface": "south"}, "west": {"uv": [0, 8, 16, 16], "texture": "#side", "cullface": "west"}, "east": {"uv": [0, 8, 16, 16], "texture": "#side", "cullface": "east"}}},
        {"from": [8, 8, 0], "to": [16, 16, 16], "faces": {"up": {"uv": [8, 0, 16, 16], "texture": "#top", "cullface": "up"}, "north": {"uv": [0, 0, 8, 8], "texture": "#side", "cullface": "north"}, "south": {"uv": [8, 0, 16, 8], "texture": "#side", "cullface": "south"}, "west": {"uv": [0, 0, 16, 8], "texture": "#side"}, "east": {"uv": [0, 0, 16, 8], "texture": "#side", "cullface": "east"}}},
    ],
    "inner_stairs": [
        {"from": [0, 0, 0], "to": [16, 8, 16], "faces": {"down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"}, "up": {"uv": [0, 0, 16, 16], "texture": "#top"}, "north": {"uv": [0, 8, 16, 16], "texture": "#side", "cullface": "north"}, "south": {"uv": [0, 8, 16, 16], "texture": "#side", "cullface": "south"}, "west": {"uv": [0, 8, 16, 16], "texture": "#side", "cullface": "west"}, "east": {"uv": [0, 8, 16, 16], "texture": "#side", "cullface": "east"}}},
        {"from": [8, 8, 0], "to": [16, 16, 16], "faces": {"up": {"uv": [8, 0, 16, 16], "texture": "#top", "cullface": "up"}, "north": {"uv": [0, 0, 8, 8], "texture": "#side", "cullface": "north"}, "south": {"uv": [8, 0, 16, 8], "texture": "#side", "cullface": "south"}, "west": {"uv": [0, 0, 16, 8], "texture": "#side"}, "east": {"uv": [0, 0, 16, 8], "texture": "#side", "cullface": "east"}}},
        {"from": [0, 8, 8], "to": [8, 16, 16], "faces": {"up": {"uv": [0, 8, 8, 16], "texture": "#top", "cullface": "up"}, "north": {"uv": [8, 0, 16, 8], "texture": "#side"}, "south": {"uv": [0, 0, 8, 8], "texture": "#side", "cullface": "south"}, "west": {"uv": [8, 0, 16, 8], "texture": "#side", "cullface": "west"}}},
    ],
    "outer_stairs": [
        {"from": [0, 0, 0], "to": [16, 8, 16], "faces": {"down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"}, "up": {"uv": [0, 0, 16, 16], "texture": "#top"}, "north": {"uv": [0, 8, 16, 16], "texture": "#side", "cullface": "north"}, "south": {"uv": [0, 8, 16, 16], "texture": "#side", "cullface": "south"}, "west": {"uv": [0, 8, 16, 16], "texture": "#side", "cullface": "west"}, "east": {"uv": [0, 8, 16, 16], "texture": "#side", "cullface": "east"}}},
        {"from": [8, 8, 8], "to": [16, 16, 16], "faces": {"up": {"uv": [8, 8, 16, 16], "texture": "#top", "cullface": "up"}, "north": {"uv": [0, 0, 8, 8], "texture": "#side"}, "south": {"uv": [8, 0, 16, 8], "texture": "#side", "cullface": "south"}, "west": {"uv": [8, 0, 16, 8], "texture": "#side"}, "east": {"uv": [0, 0, 8, 8], "texture": "#side", "cullface": "east"}}},
    ],
    "template_wall_post": [
        {"from": [4, 0, 4], "to": [12, 16, 12], "faces": {"down": {"texture": "#wall", "cullface": "down"}, "up": {"texture": "#wall", "cullface": "up"}, "north": {"texture": "#wall"}, "south": {"texture": "#wall"}, "west": {"texture": "#wall"}, "east": {"texture": "#wall"}}},
    ],
    "template_wall_side": [
        {"from": [5, 0, 0], "to": [11, 14, 8], "faces": {"down": {"texture": "#wall", "cullface": "down"}, "up": {"texture": "#wall"}, "north": {"texture": "#wall", "cullface": "north"}, "west": {"texture": "#wall"}, "east": {"texture": "#wall"}}},
    ],
    "template_wall_side_tall": [
        {"from": [5, 0, 0], "to": [11, 16, 8], "faces": {"down": {"texture": "#wall", "cullface": "down"}, "up": {"texture": "#wall", "cullface": "up"}, "north": {"texture": "#wall", "cullface": "north"}, "west": {"texture": "#wall"}, "east": {"texture": "#wall"}}},
    ],
    "wall_inventory": [
        {"from": [4, 0, 4], "to": [12, 16, 12], "faces": {"down": {"uv": [4, 4, 12, 12], "texture": "#wall", "cullface": "down"}, "up": {"uv": [4, 4, 12, 12], "texture": "#wall"}, "north": {"uv": [4, 0, 12, 16], "texture": "#wall"}, "south": {"uv": [4, 0, 12, 16], "texture": "#wall"}, "west": {"uv": [4, 0, 12, 16], "texture": "#wall"}, "east": {"uv": [4, 0, 12, 16], "texture": "#wall"}}},
        {"from": [5, 0, 0], "to": [11, 13, 16], "faces": {"down": {"uv": [5, 0, 11, 16], "texture": "#wall", "cullface": "down"}, "up": {"uv": [5, 0, 11, 16], "texture": "#wall"}, "north": {"uv": [5, 3, 11, 16], "texture": "#wall", "cullface": "north"}, "south": {"uv": [5, 3, 11, 16], "texture": "#wall", "cullface": "south"}, "west": {"uv": [0, 3, 16, 16], "texture": "#wall"}, "east": {"uv": [0, 3, 16, 16], "texture": "#wall"}}},
    ],
    "template_glass_pane_post": [
        {"from": [7, 0, 7], "to": [9, 16, 9], "faces": {"down": {"uv": [7, 7, 9, 9], "texture": "#edge"}, "up": {"uv": [7, 7, 9, 9], "texture": "#edge"}}},
    ],
    "template_glass_pane_side": [
        {"from": [7, 0, 0], "to": [9, 16, 7], "faces": {"down": {"uv": [7, 0, 9, 7], "texture": "#edge"}, "up": {"uv": [7, 0, 9, 7], "texture": "#edge"}, "north": {"uv": [7, 0, 9, 16], "texture": "#edge", "cullface": "north"}, "west": {"uv": [16, 0, 9, 16], "texture": "#pane"}, "east": {"uv": [9, 0, 16, 16], "texture": "#pane"}}},
    ],
    "template_glass_pane_side_alt": [
        {"from": [7, 0, 9], "to": [9, 16, 16], "faces": {"down": {"uv": [7, 0, 9, 7], "texture": "#edge"}, "up": {"uv": [7, 0, 9, 7], "texture": "#edge"}, "south": {"uv": [7, 0, 9, 16], "texture": "#edge", "cullface": "south"}, "west": {"uv": [7, 0, 0, 16], "texture": "#pane"}, "east": {"uv": [0, 0, 7, 16], "texture": "#pane"}}},
    ],
    "template_glass_pane_noside": [
        {"from": [7, 0, 7], "to": [9, 16, 9], "faces": {"north": {"uv": [9, 0, 7, 16], "texture": "#pane"}}},
    ],
    "template_glass_pane_noside_alt": [
        {"from": [7, 0, 7], "to": [9, 16, 9], "faces": {"east": {"uv": [7, 0, 9, 16], "texture": "#pane"}}},
    ],
}


def tinted_parent(template):
    return "%s:block/tinted/%s" % (NS, template)


def emit_tinted_templates():
    for template, elements in VANILLA_GEOMETRY.items():
        tinted = []
        for el in elements:
            el = json.loads(json.dumps(el))  # deep copy
            for face in el["faces"].values():
                face["tintindex"] = 0
            tinted.append(el)
        files["assets/%s/models/block/tinted/%s.json" % (NS, template)] = {
            "parent": "minecraft:block/%s" % template, "elements": tinted}


def tex(t):
    return "%s:block/%s" % (NS, t)


def block_id(name):
    return "%s:%s" % (NS, name)


def title(name):
    return " ".join(w[:1].upper() + w[1:] for w in name.split("_") if w)


def render_type(family):
    return "minecraft:cutout" if family in ("glass", "neon") else None


# --- per-kind emitters --------------------------------------------------------
NEON_LEGACY = "neon"


def emit_neon_shim(name, t):
    """A legacy neon_<colour> id (0.8.0 neon fold): hidden shim. Keeps its model so a not-yet-swapped block or
    stack renders; drops a chroma_cube of its colour; no recipe of its own, no decor-family tag entry."""
    colour = name[len("neon_"):]
    files["assets/%s/blockstates/%s.json" % (NS, name)] = {"variants": {"": {"model": "%s:block/%s" % (NS, name)}}}
    faces = {face: {"uv": [0, 0, 16, 16], "texture": "#all", "tintindex": 0, "cullface": face}
             for face in ("north", "east", "south", "west", "up", "down")}
    files["assets/%s/models/block/%s.json" % (NS, name)] = {
        "textures": {"particle": tex(t), "all": tex(t)},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}], "render_type": "minecraft:cutout"}
    files["assets/%s/models/item/%s.json" % (NS, name)] = {"parent": "%s:block/%s" % (NS, name)}
    files["assets/%s/items/%s.json" % (NS, name)] = {
        "model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}}
    table = {"type": "minecraft:block",
             "pools": [{"rolls": 1, "bonus_rolls": 0,
                        "entries": [{"type": "minecraft:item", "name": block_id("chroma_cube"),
                                     "functions": [{"function": "minecraft:set_components",
                                                    "components": {"%s:color" % NS: colour}}]}],
                        "conditions": [{"condition": "minecraft:survives_explosion"}]}]}
    files["data/%s/loot_table/blocks/%s.json" % (NS, name)] = table
    files[RES_26_3 + "data/%s/loot_table/blocks/%s.json" % (NS, name)] = loot_26_3(table)
    mineable.append(block_id(name))
    lang["block.%s.%s" % (NS, name)] = title(name) + " (Legacy)"
    lang["item.%s.%s" % (NS, name)] = title(name) + " (Legacy)"


def emit_chroma_cube_data():
    """The chroma cube's crafting (base recipe + one dye recolour per old neon colour, reusing the neon_<colour>
    recipe ids so recipe books carry over), the paint gun recipe, and the cube's place in the old neon family."""
    files["data/%s/recipe/chroma_cube.json" % NS] = {
        "type": "minecraft:crafting_shaped", "pattern": [" G ", "GQG", " G "],
        "key": {"G": "minecraft:glass", "Q": "minecraft:glowstone_dust"},
        "result": {"id": block_id("chroma_cube"), "count": 4}}
    for c in NEON:
        files["data/%s/recipe/neon_%s.json" % (NS, c)] = {
            "type": "minecraft:crafting_shapeless",
            "ingredients": ["minecraft:%s_dye" % c, block_id("chroma_cube")],
            "result": {"id": block_id("chroma_cube"), "count": 1, "components": {"%s:color" % NS: c}}}
    files["data/%s/recipe/paint_gun.json" % NS] = {
        "type": "minecraft:crafting_shaped", "pattern": ["IB", "S "],
        "key": {"I": "minecraft:iron_ingot", "B": "minecraft:glass_bottle", "S": "minecraft:stick"},
        "result": {"id": "%s:paint_gun" % NS, "count": 1}}
    decor_tags.setdefault(NEON_LEGACY, []).append(block_id("chroma_cube"))


def emit_cube(name, t, fam):
    files["assets/%s/blockstates/%s.json" % (NS, name)] = {"variants": {"": {"model": "%s:block/%s" % (NS, name)}}}
    # A full cube whose faces carry tintindex 0, so the DecorColorTintSource can paint it
    # (vanilla cube_all has no tintindex). NATURAL colour = white = the base texture unchanged.
    faces = {face: {"uv": [0, 0, 16, 16], "texture": "#all", "tintindex": 0, "cullface": face}
             for face in ("north", "east", "south", "west", "up", "down")}
    model = {"textures": {"particle": tex(t), "all": tex(t)},
             "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]}
    rt = render_type(fam)
    if rt:
        model["render_type"] = rt
    files["assets/%s/models/block/%s.json" % (NS, name)] = model
    files["assets/%s/models/item/%s.json" % (NS, name)] = {"parent": "%s:block/%s" % (NS, name)}
    loot_self(name)
    tag_common(name, fam)


def emit_slab(name, t, fam):
    cube = CUBE_OF[t]
    files["assets/%s/blockstates/%s.json" % (NS, name)] = {"variants": {
        "type=bottom": {"model": "%s:block/%s" % (NS, name)},
        "type=top": {"model": "%s:block/%s_top" % (NS, name)},
        "type=double": {"model": "%s:block/%s" % (NS, cube)},
    }}
    textures = {"bottom": tex(t), "top": tex(t), "side": tex(t)}
    files["assets/%s/models/block/%s.json" % (NS, name)] = {"parent": tinted_parent("slab"), "textures": textures}
    files["assets/%s/models/block/%s_top.json" % (NS, name)] = {"parent": tinted_parent("slab_top"), "textures": textures}
    files["assets/%s/models/item/%s.json" % (NS, name)] = {"parent": "%s:block/%s" % (NS, name)}
    files["data/%s/loot_table/blocks/%s.json" % (NS, name)] = slab_loot(name)
    tag_common(name, fam)


def emit_stairs(name, t, fam):
    files["assets/%s/blockstates/%s.json" % (NS, name)] = stairs_blockstate(name)
    textures = {"bottom": tex(t), "top": tex(t), "side": tex(t)}
    files["assets/%s/models/block/%s.json" % (NS, name)] = {"parent": tinted_parent("stairs"), "textures": textures}
    files["assets/%s/models/block/%s_inner.json" % (NS, name)] = {"parent": tinted_parent("inner_stairs"), "textures": textures}
    files["assets/%s/models/block/%s_outer.json" % (NS, name)] = {"parent": tinted_parent("outer_stairs"), "textures": textures}
    files["assets/%s/models/item/%s.json" % (NS, name)] = {"parent": "%s:block/%s" % (NS, name)}
    loot_self(name)
    tag_common(name, fam)


def emit_wall(name, t, fam):
    files["assets/%s/blockstates/%s.json" % (NS, name)] = wall_blockstate(name)
    textures = {"wall": tex(t)}
    files["assets/%s/models/block/%s_post.json" % (NS, name)] = {"parent": tinted_parent("template_wall_post"), "textures": textures}
    files["assets/%s/models/block/%s_side.json" % (NS, name)] = {"parent": tinted_parent("template_wall_side"), "textures": textures}
    files["assets/%s/models/block/%s_side_tall.json" % (NS, name)] = {"parent": tinted_parent("template_wall_side_tall"), "textures": textures}
    files["assets/%s/models/block/%s_inventory.json" % (NS, name)] = {"parent": tinted_parent("wall_inventory"), "textures": textures}
    files["assets/%s/models/item/%s.json" % (NS, name)] = {"parent": "%s:block/%s_inventory" % (NS, name)}
    loot_self(name)
    walls.append(block_id(name))
    tag_common(name, fam)


def emit_pane(name, t, fam):
    files["assets/%s/blockstates/%s.json" % (NS, name)] = pane_blockstate(name)
    textures = {"pane": tex(t), "edge": tex(t)}
    for suf, parent in (("_post", "post"), ("_side", "side"), ("_side_alt", "side_alt"),
                        ("_noside", "noside"), ("_noside_alt", "noside_alt")):
        files["assets/%s/models/block/%s%s.json" % (NS, name, suf)] = {
            "parent": tinted_parent("template_glass_pane_%s" % parent), "textures": textures}
    files["assets/%s/models/item/%s.json" % (NS, name)] = {
        "parent": "minecraft:item/generated", "textures": {"layer0": tex(t)}}
    loot_self(name)
    tag_common(name, fam)


# --- shared helpers -----------------------------------------------------------
def loot_self(name):
    files["data/%s/loot_table/blocks/%s.json" % (NS, name)] = {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item", "name": block_id(name)}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    }


def slab_loot(name):
    return {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item",
            "functions": [{"function": "minecraft:set_count", "count": 2,
                "conditions": [{"condition": "minecraft:block_state_property", "block": block_id(name),
                    "properties": {"type": "double"}}]},
                {"function": "minecraft:explosion_decay"}],
            "name": block_id(name)}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    }


def tag_common(name, fam):
    mineable.append(block_id(name))
    decor_tags.setdefault(fam, []).append(block_id(name))
    lang["block.%s.%s" % (NS, name)] = title(name)
    lang["item.%s.%s" % (NS, name)] = title(name)


# --- standard vanilla blockstate templates ------------------------------------
def stairs_blockstate(name):
    m = "%s:block/%s" % (NS, name)
    mi = m + "_inner"
    mo = m + "_outer"
    v = {}

    def add(key, model, x=0, y=0):
        e = {"model": model}
        if x:
            e["x"] = x
        if y:
            e["y"] = y
        e["uvlock"] = True
        v[key] = e
    # bottom/top half x facing x shape — the canonical vanilla stair map
    facings = ["east", "west", "south", "north"]
    yrot = {"east": 0, "west": 180, "south": 90, "north": 270}
    for half, xrot in (("bottom", 0), ("top", 180)):
        for f in facings:
            base = yrot[f]
            add("facing=%s,half=%s,shape=straight" % (f, half), m, xrot, base % 360)
            add("facing=%s,half=%s,shape=outer_right" % (f, half), mo, xrot, base % 360)
            add("facing=%s,half=%s,shape=outer_left" % (f, half), mo, xrot, (base + 270) % 360)
            add("facing=%s,half=%s,shape=inner_right" % (f, half), mi, xrot, base % 360)
            add("facing=%s,half=%s,shape=inner_left" % (f, half), mi, xrot, (base + 270) % 360)
    return {"variants": v}


def wall_blockstate(name):
    post = "%s:block/%s_post" % (NS, name)
    side = "%s:block/%s_side" % (NS, name)
    tall = "%s:block/%s_side_tall" % (NS, name)
    parts = [{"when": {"up": "true"}, "apply": {"model": post}}]
    dirs = {"north": 0, "east": 90, "south": 180, "west": 270}
    for d, y in dirs.items():
        for prop, model in (("low", side), ("tall", tall)):
            a = {"model": model, "uvlock": True}
            if y:
                a["y"] = y
            parts.append({"when": {d: prop}, "apply": a})
    return {"multipart": parts}


def pane_blockstate(name):
    post = "%s:block/%s_post" % (NS, name)
    side = "%s:block/%s_side" % (NS, name)
    side_alt = "%s:block/%s_side_alt" % (NS, name)
    noside = "%s:block/%s_noside" % (NS, name)
    noside_alt = "%s:block/%s_noside_alt" % (NS, name)
    parts = [{"apply": {"model": post}}]
    parts.append({"when": {"north": "true"}, "apply": {"model": side}})
    parts.append({"when": {"east": "true"}, "apply": {"model": side, "y": 90}})
    parts.append({"when": {"south": "true"}, "apply": {"model": side_alt}})
    parts.append({"when": {"west": "true"}, "apply": {"model": side_alt, "y": 90}})
    parts.append({"when": {"north": "false"}, "apply": {"model": noside}})
    parts.append({"when": {"east": "false"}, "apply": {"model": noside, "y": 90}})
    parts.append({"when": {"south": "false"}, "apply": {"model": noside_alt}})
    parts.append({"when": {"west": "false"}, "apply": {"model": noside_alt, "y": 90}})
    return {"multipart": parts}


# --- recipes ------------------------------------------------------------------
STONECUT_COUNT = {"slab": 2, "stairs": 1, "wall": 1}


def emit_recipes():
    for name, kind, t, fam, mat in SPEC:
        if kind == "cube" and mat and fam != NEON_LEGACY:
            files["data/%s/recipe/%s.json" % (NS, name)] = cube_recipe(name, fam, mat)
        elif kind in ("slab", "stairs", "wall", "pane"):
            files["data/%s/recipe/%s.json" % (NS, name)] = shape_recipe(name, CUBE_OF[t], kind)
            # Also a stonecutter route from the base cube (the decor-mod convenience).
            if kind in STONECUT_COUNT:
                files["data/%s/recipe/%s_from_stonecutting.json" % (NS, name)] = {
                    "type": "minecraft:stonecutting",
                    "ingredient": block_id(CUBE_OF[t]),
                    "result": {"id": block_id(name), "count": STONECUT_COUNT[kind]}}


def cube_recipe(name, fam, mat):
    # 26.x: an ingredient is the item-id STRING (or #tag), NOT {"item": ...} (removed in 1.21.2).
    if fam == "neon":  # glowstone dust + dye -> neon
        return {"type": "minecraft:crafting_shapeless",
                "ingredients": ["minecraft:glowstone_dust", "minecraft:%s_dye" % mat],
                "result": {"id": block_id(name), "count": 2}}
    if fam == "panel":  # 3-in-a-row -> 3 panels (distinct from hull's 2x2)
        return {"type": "minecraft:crafting_shaped", "pattern": ["MMM"],
                "key": {"M": mat}, "result": {"id": block_id(name), "count": 3}}
    # hull / glass: 2x2 -> 4
    return {"type": "minecraft:crafting_shaped", "pattern": ["MM", "MM"],
            "key": {"M": mat}, "result": {"id": block_id(name), "count": 4}}


def shape_recipe(name, cube, kind):
    src = block_id(cube)  # 26.x: ingredient is the item-id string, not {"item": ...}
    if kind == "slab":
        return {"type": "minecraft:crafting_shaped", "pattern": ["###"],
                "key": {"#": src}, "result": {"id": block_id(name), "count": 6}}
    if kind == "stairs":
        return {"type": "minecraft:crafting_shaped", "pattern": ["#  ", "## ", "###"],
                "key": {"#": src}, "result": {"id": block_id(name), "count": 4}}
    if kind == "wall":
        return {"type": "minecraft:crafting_shaped", "pattern": ["###", "###"],
                "key": {"#": src}, "result": {"id": block_id(name), "count": 6}}
    # pane
    return {"type": "minecraft:crafting_shaped", "pattern": ["###", "###"],
            "key": {"#": src}, "result": {"id": block_id(name), "count": 16}}


# --- Luminous collection (0.4.0) ------------------------------------------------
# Keep in LOCKSTEP with registry/LuminousBlocks.java and tools/gen_luminous.py (textures).
# kind: connected = cube with model-conditioned connected bezel (6 booleans, multipart)
#       plain     = cube with an emissive overlay
#       pillar    = axis pillar that links along its axis (pos_link / neg_link collars)
#       lamp      = fusion lamp (lit=false/true)
#       lslab     = slab of a connected cube (base + glow, no bezel)
# glow: the block has a <name>_glow emissive overlay; emit: light_emission for the whole base
# face (light-field blocks with no separate overlay).
LUMINOUS = [
    # name,                 kind,        glow,  emit
    ("circuit_plating",      "connected", True,  0),
    ("circuit_plating_slab", "lslab",     True,  0),
    ("holo_grid_floor",      "connected", True,  0),
    ("holo_grid_floor_slab", "lslab",     True,  0),
    ("starfield_panel",      "connected", True,  0),
    ("data_stream_panel",    "connected", True,  0),
    ("aurora_glass",         "connected", False, 9),
    ("lumen_panel",          "connected", False, 15),
    ("lumen_panel_slab",     "lslab",     False, 15),
    ("void_rift",            "plain",     True,  0),
    ("crystal_lattice",      "plain",     True,  0),
    ("capacitor_bank",       "plain",     True,  0),
    ("xeno_bloom",           "plain",     True,  0),
    ("ion_vent",             "plain",     True,  0),
    ("plasma_conduit",       "pillar",    True,  0),
    ("starsteel_pillar",     "pillar",    True,  0),
    ("fusion_lamp",          "lamp",      True,  0),
]
LUMINOUS_NAMES = {
    "circuit_plating": "Circuit Plating", "circuit_plating_slab": "Circuit Plating Slab",
    "holo_grid_floor": "Holo-Grid Floor", "holo_grid_floor_slab": "Holo-Grid Floor Slab",
    "starfield_panel": "Starfield Panel", "data_stream_panel": "Data Stream Panel",
    "aurora_glass": "Aurora Glass", "lumen_panel": "Lumen Panel", "lumen_panel_slab": "Lumen Panel Slab",
    "void_rift": "Void Rift", "crystal_lattice": "Void Crystal Lattice", "capacitor_bank": "Capacitor Bank",
    "xeno_bloom": "Xenobloom", "ion_vent": "Ion Vent", "plasma_conduit": "Plasma Conduit",
    "starsteel_pillar": "Starsteel Pillar", "fusion_lamp": "Fusion Lamp",
}
# sound event -> (files, subtitle)
LUMINOUS_SOUNDS = {
    "block.circuit.chirp": (["circuit_chirp1", "circuit_chirp2", "circuit_chirp3"], "Circuitry chirps"),
    "block.void_rift.hum": (["void_hum1", "void_hum2"], "Void rift hums"),
    "block.plasma_conduit.crackle": (["conduit_crackle1", "conduit_crackle2", "conduit_crackle3"], "Plasma crackles"),
    "block.crystal_lattice.chime": (["crystal_chime1", "crystal_chime2", "crystal_chime3"], "Crystal chimes"),
    "block.fusion_lamp.power_on": (["lamp_power_on"], "Fusion lamp ignites"),
    "block.fusion_lamp.power_off": (["lamp_power_off"], "Fusion lamp powers down"),
    "block.fusion_lamp.hum": (["lamp_hum"], "Fusion lamp hums"),
    "block.ion_vent.hiss": (["vent_hiss1", "vent_hiss2"], "Ion vent hisses"),
    "block.capacitor_bank.charge": (["capacitor_charge"], "Capacitor charges"),
}
FACES = ("north", "east", "south", "west", "up", "down")
# For each face, which world direction each TEXTURE edge touches (vanilla auto-UV with
# uv [0,0,16,16]): (top, bottom, left, right).
FACE_EDGES = {
    "north": ("up", "down", "east", "west"),
    "south": ("up", "down", "west", "east"),
    "west": ("up", "down", "north", "south"),
    "east": ("up", "down", "south", "north"),
    "up": ("north", "south", "west", "east"),
    "down": ("south", "north", "west", "east"),
}


def _el(frm, to, faces, texture, emit=0, uv=None, cull=True):
    """One element; `faces` = face names (or {face: texture})."""
    fs = {}
    for f in faces:
        t = faces[f] if isinstance(faces, dict) else texture
        face = {"texture": t}
        if uv:
            face["uv"] = uv
        if cull is True:
            face["cullface"] = f
        elif isinstance(cull, dict) and cull.get(f):
            face["cullface"] = cull[f]
        fs[f] = face
    if emit:
        # Chroma (0.8.0): emissive layers take the hub colour through the tint bridge (white = unchanged).
        for face in fs.values():
            face["tintindex"] = 0
    e = {"from": frm, "to": to}
    if emit:
        e["shade"] = False
        e["light_emission"] = emit
    e["faces"] = fs
    return e


def _core_elements(glow, emit):
    els = [_el([0, 0, 0], [16, 16, 16], FACES, "#all", emit=emit, uv=[0, 0, 16, 16])]
    if glow:
        els.append(_el([0, 0, 0], [16, 16, 16], FACES, "#glow", emit=15, uv=[0, 0, 16, 16]))
    return els


def _model(textures, elements):
    return {"textures": textures, "elements": elements}


def _lum_tex(name, glow, base=None):
    base = base or name
    t = {"particle": tex(base), "all": tex(base)}
    if glow:
        t["glow"] = tex(base + "_glow")
    return t


def lum_connected(name, glow, emit):
    """Connected textures without any coplanar overlay: each face is its own multipart part, and
    the part is chosen by the face's four in-plane neighbours. The matching texture variant
    (<name>_f<mask>, from gen_luminous.py) has the bezel baked in on exactly the unconnected edges,
    and the glow variant is masked there, so nothing is drawn twice on the same plane."""
    bs = []
    for face in FACES:
        edges = FACE_EDGES[face]
        for mask in range(16):
            bits = (mask & 8, mask & 4, mask & 2, mask & 1)
            when = {d: ("false" if bit else "true") for d, bit in zip(edges, bits)}
            textures = {"particle": tex(name), "all": tex("%s_f%d" % (name, mask))}
            els = [_el([0, 0, 0], [16, 16, 16], [face], "#all", emit=emit, uv=[0, 0, 16, 16])]
            if glow:
                textures["glow"] = tex("%s_glow_f%d" % (name, mask))
                els.append(_el([0, 0, 0], [16, 16, 16], [face], "#glow", emit=15, uv=[0, 0, 16, 16]))
            model = "%s_%s_f%d" % (name, face, mask)
            files["assets/%s/models/block/%s.json" % (NS, model)] = _model(textures, els)
            bs.append({"when": when, "apply": {"model": "%s:block/%s" % (NS, model)}})
    files["assets/%s/blockstates/%s.json" % (NS, name)] = {"multipart": bs}
    # inventory / double-slab look: every edge framed
    inv_tex = {"particle": tex(name), "all": tex(name + "_f15")}
    if glow:
        inv_tex["glow"] = tex(name + "_glow_f15")
    files["assets/%s/models/block/%s_inventory.json" % (NS, name)] = _model(inv_tex, _core_elements(glow, emit))
    files["assets/%s/models/item/%s.json" % (NS, name)] = {"parent": "%s:block/%s_inventory" % (NS, name)}


def lum_plain(name, glow, emit):
    files["assets/%s/blockstates/%s.json" % (NS, name)] = {"variants": {"": {"model": "%s:block/%s" % (NS, name)}}}
    files["assets/%s/models/block/%s.json" % (NS, name)] = _model(_lum_tex(name, glow), _core_elements(glow, emit))
    files["assets/%s/models/item/%s.json" % (NS, name)] = {"parent": "%s:block/%s" % (NS, name)}


def lum_slab(name, glow, emit):
    cube = name[:-len("_slab")]
    textures = _lum_tex(cube, glow)
    for suffix, frm, to, open_face in (("", [0, 0, 0], [16, 8, 16], "up"), ("_top", [0, 8, 0], [16, 16, 16], "down")):
        cull = {f: f for f in FACES if f != open_face}
        els = [_el(frm, to, FACES, "#all", emit=emit, cull=cull)]
        if glow:
            els.append(_el(frm, to, FACES, "#glow", emit=15, cull=cull))
        files["assets/%s/models/block/%s%s.json" % (NS, name, suffix)] = _model(textures, els)
    files["assets/%s/blockstates/%s.json" % (NS, name)] = {"variants": {
        "type=bottom": {"model": "%s:block/%s" % (NS, name)},
        "type=top": {"model": "%s:block/%s_top" % (NS, name)},
        "type=double": {"model": "%s:block/%s_inventory" % (NS, cube)},
    }}
    files["assets/%s/models/item/%s.json" % (NS, name)] = {"parent": "%s:block/%s" % (NS, name)}


# Pillars: per axis, the texture for each side face given which ends carry a collar
# (cp = collar at the positive end, cn = at the negative end). Side textures come in
# side_v_c<top><bottom> and side_h_c<left><right>; vanilla auto-UV decides which texture edge
# faces the positive end, so the variant index is swapped per face where needed.
def _pillar_side(axis, face, cp, cn):
    if axis == "y":
        return "side_v", (cp, cn)            # top = +y
    if axis == "x":
        if face == "north":
            return "side_h", (cp, cn)        # u = 16 - x: left = +x
        return "side_h", (cn, cp)            # south/up/down: right = +x
    # axis z
    if face == "east":
        return "side_h", (cp, cn)            # u = 16 - z: left = +z
    if face == "west":
        return "side_h", (cn, cp)            # right = +z
    if face == "up":
        return "side_v", (cn, cp)            # v = z: bottom = +z
    return "side_v", (cp, cn)                # down: v = 16 - z, top = +z


PILLAR_AXES = {
    "y": (("north", "south", "east", "west"), ("up", "down")),
    "x": (("north", "south", "up", "down"), ("east", "west")),
    "z": (("east", "west", "up", "down"), ("north", "south")),
}


def lum_pillar(name, glow, emit):
    """Collars are baked into side-texture variants (no coplanar cap layer); the blockstate picks
    one model per axis x link combination."""
    variants = {}
    for axis, (sides, ends) in PILLAR_AXES.items():
        for pos_link in (False, True):
            for neg_link in (False, True):
                cp, cn = int(not pos_link), int(not neg_link)
                textures = {"particle": tex(name + "_end"), "end": tex(name + "_end")}
                faces, gfaces = {}, {}
                for f in sides:
                    kind, (a, b) = _pillar_side(axis, f, cp, cn)
                    var = "%s_c%d%d" % (kind, a, b)
                    textures[var] = tex("%s_%s" % (name, var))
                    faces[f] = "#" + var
                    if glow:
                        gvar = "%s_glow_c%d%d" % (kind, a, b)
                        textures[gvar] = tex("%s_%s" % (name, gvar))
                        gfaces[f] = "#" + gvar
                for f in ends:
                    faces[f] = "#end"
                    if glow:
                        textures["end_glow"] = tex(name + "_end_glow")
                        gfaces[f] = "#end_glow"
                els = [_el([0, 0, 0], [16, 16, 16], faces, None, uv=[0, 0, 16, 16])]
                if glow:
                    els.append(_el([0, 0, 0], [16, 16, 16], gfaces, None, emit=15, uv=[0, 0, 16, 16]))
                model = "%s_%s_c%d%d" % (name, axis, cp, cn)
                files["assets/%s/models/block/%s.json" % (NS, model)] = _model(textures, els)
                key = "axis=%s,neg_link=%s,pos_link=%s" % (axis, str(neg_link).lower(), str(pos_link).lower())
                variants[key] = {"model": "%s:block/%s" % (NS, model)}
    files["assets/%s/blockstates/%s.json" % (NS, name)] = {"variants": variants}
    files["assets/%s/models/item/%s.json" % (NS, name)] = {"parent": "%s:block/%s_y_c11" % (NS, name)}


def lum_lamp(name, glow, emit):
    files["assets/%s/models/block/%s.json" % (NS, name)] = _model(
        {"particle": tex(name), "all": tex(name)}, _core_elements(False, 0))
    files["assets/%s/models/block/%s_on.json" % (NS, name)] = _model(
        {"particle": tex(name + "_on"), "all": tex(name + "_on"), "glow": tex(name + "_on_glow")},
        _core_elements(True, 0))
    files["assets/%s/blockstates/%s.json" % (NS, name)] = {"variants": {
        "lit=false": {"model": "%s:block/%s" % (NS, name)},
        "lit=true": {"model": "%s:block/%s_on" % (NS, name)}}}
    # the inventory icon shows the lamp lit — what it is for
    files["assets/%s/models/item/%s.json" % (NS, name)] = {"parent": "%s:block/%s_on" % (NS, name)}


def _shaped(pattern, key, result, count):
    return {"type": "minecraft:crafting_shaped", "pattern": pattern, "key": key,
            "result": {"id": block_id(result), "count": count}}


def _shapeless(ingredients, result, count):
    return {"type": "minecraft:crafting_shapeless", "ingredients": ingredients,
            "result": {"id": block_id(result), "count": count}}


def _ring(outer, centre, result, count):
    return _shaped(["OOO", "OCO", "OOO"], {"O": outer, "C": centre}, result, count)


# Cheap on purpose (decoration is expression, not a tax): mostly 8 of an existing
# NeroDecor block around one accent ingredient. Vanilla + Core items only, no gates.
LUMINOUS_RECIPES = {
    "circuit_plating": _ring("nerodecor:hull_nero_alloy", "minecraft:redstone", "circuit_plating", 8),
    "holo_grid_floor": _ring("nerodecor:panel_starsteel", "minecraft:prismarine_crystals", "holo_grid_floor", 8),
    "starfield_panel": _ring("nerodecor:hull_void_crystal", "minecraft:glowstone_dust", "starfield_panel", 8),
    "data_stream_panel": _ring("nerodecor:panel_nero_alloy", "minecraft:lapis_lazuli", "data_stream_panel", 8),
    "aurora_glass": _ring("nerodecor:glass_plasma_glass", "minecraft:amethyst_shard", "aurora_glass", 8),
    "lumen_panel": _ring("minecraft:glass", "minecraft:glowstone", "lumen_panel", 8),
    "void_rift": _shapeless(["nerolandcore:void_crystal_shard", "nerolandcore:void_crystal_shard",
                             "minecraft:ender_pearl", "minecraft:obsidian"], "void_rift", 2),
    "crystal_lattice": _shaped(["VA", "AV"], {"V": "nerolandcore:void_crystal_shard",
                                              "A": "minecraft:amethyst_shard"}, "crystal_lattice", 4),
    "capacitor_bank": _shaped(["SRS", "SGS", "SRS"], {"S": "nerodecor:panel_starsteel", "R": "minecraft:redstone",
                                                      "G": "nerolandcore:plasma_glass_block"}, "capacitor_bank", 2),
    "xeno_bloom": _shapeless(["minecraft:cobbled_deepslate", "minecraft:cobbled_deepslate",
                              "minecraft:cobbled_deepslate", "minecraft:cobbled_deepslate",
                              "minecraft:glow_berries"], "xeno_bloom", 4),
    "ion_vent": _shaped(["B B", "BPB", "B B"], {"B": "minecraft:iron_bars", "P": "nerodecor:panel_nero_alloy"},
                        "ion_vent", 4),
    "plasma_conduit": _shaped(["S", "G", "S"], {"S": "nerolandcore:starsteel_ingot",
                                               "G": "nerolandcore:plasma_glass_block"}, "plasma_conduit", 4),
    "starsteel_pillar": _shaped(["H", "H"], {"H": "nerodecor:hull_starsteel"}, "starsteel_pillar", 2),
    "fusion_lamp": _shaped([" S ", "SLS", " S "], {"S": "nerodecor:panel_starsteel", "L": "minecraft:redstone_lamp"},
                           "fusion_lamp", 1),
}


def emit_luminous():
    emitters = {"connected": lum_connected, "plain": lum_plain, "pillar": lum_pillar,
                "lamp": lum_lamp, "lslab": lum_slab}
    for name, kind, glow, emit in LUMINOUS:
        emitters[kind](name, glow, emit)
        files["assets/%s/items/%s.json" % (NS, name)] = {
            "model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}}
        if kind == "lslab":
            cube = name[:-len("_slab")]
            files["data/%s/loot_table/blocks/%s.json" % (NS, name)] = slab_loot(name)
            files["data/%s/recipe/%s.json" % (NS, name)] = shape_recipe(name, cube, "slab")
            files["data/%s/recipe/%s_from_stonecutting.json" % (NS, name)] = {
                "type": "minecraft:stonecutting", "ingredient": block_id(cube),
                "result": {"id": block_id(name), "count": 2}}
        else:
            loot_self(name)
            files["data/%s/recipe/%s.json" % (NS, name)] = LUMINOUS_RECIPES[name]
        mineable.append(block_id(name))
        decor_tags.setdefault("luminous", []).append(block_id(name))
        lang["block.%s.%s" % (NS, name)] = LUMINOUS_NAMES[name]
        lang["item.%s.%s" % (NS, name)] = LUMINOUS_NAMES[name]
    files["data/%s/recipe/starsteel_pillar_from_stonecutting.json" % NS] = {
        "type": "minecraft:stonecutting", "ingredient": block_id("hull_starsteel"),
        "result": {"id": block_id("starsteel_pillar"), "count": 1}}
    sounds = {}
    for event, (names, subtitle) in sorted(LUMINOUS_SOUNDS.items()):
        key = "subtitles.%s.%s" % (NS, event)
        sounds[event] = {"subtitle": key, "sounds": ["%s:block/%s" % (NS, n) for n in names]}
        lang[key] = subtitle
    files["assets/%s/sounds.json" % NS] = sounds


# --- drive --------------------------------------------------------------------
def main():
    emit = {"cube": emit_cube, "slab": emit_slab, "stairs": emit_stairs, "wall": emit_wall, "pane": emit_pane}
    for name, kind, t, fam, _mat in SPEC:
        if fam == NEON_LEGACY:
            emit_neon_shim(name, t)
            continue
        emit[kind](name, t, fam)
        # 1.21.4+/26.x: every item needs a client-item file at assets/<ns>/items/<id>.json
        # pointing at its model, or the item icon renders as the missing texture. Every SPEC block is
        # paintable, so its item tints layer 0 from the stack's nerodecor:color component through the
        # nerodecor:decor_color tint source (client/DecorColorItemTint); NATURAL/absent = white.
        files["assets/%s/items/%s.json" % (NS, name)] = {
            "model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name),
                      "tints": [{"type": "%s:decor_color" % NS}]}}
    emit_tinted_templates()
    emit_recipes()
    emit_chroma_cube_data()
    emit_luminous()

    # The tools/collections layer: gen_collections.py owns those blocks' own files; their shared
    # entries (lang, mineable, neroland:decor/<family>) are merged here so these files keep one owner.
    global COLLECTIONS
    COLLECTIONS = collections_aggregate("--check" in sys.argv[1:])
    lang.update(COLLECTIONS["lang"])
    mineable.extend(COLLECTIONS["mineable"])
    for fam, ids in COLLECTIONS["decor"].items():
        decor_tags.setdefault(fam, []).extend(ids)
    # Extra block tags from the collection modules (Block.tags; chroma=True -> #nerodecor:chroma_capable).
    block_tags = {t: list(ids) for t, ids in COLLECTIONS["tags"].items()}
    # #nerodecor:chroma_capable also takes every paintable SPEC family and the Luminous collection.
    block_tags.setdefault(CHROMA_TAG, [])
    block_tags[CHROMA_TAG] = ["#neroland:decor/%s" % f for f in CHROMA_FAMILIES] + sorted(block_tags[CHROMA_TAG])

    # aggregate tags
    files["data/minecraft/tags/block/mineable/pickaxe.json"] = {"replace": False, "values": sorted(mineable)}
    # Walls only connect to neighbours in #minecraft:walls (vanilla WallBlock.connectsTo).
    if walls:
        files["data/minecraft/tags/block/walls.json"] = {"replace": False, "values": sorted(walls)}
    for fam, ids in decor_tags.items():
        files["data/neroland/tags/block/decor/%s.json" % fam] = {"replace": False,
            "values": [{"id": i, "required": False} for i in sorted(ids)]}
    for tag, ids in sorted(block_tags.items()):
        ns, path = tag.split(":", 1)
        rel = "data/%s/tags/block/%s.json" % (ns, path)
        if rel in files:
            raise SystemExit("gen_resources: collection tag %s collides with another emitted file" % tag)
        files[rel] = {"replace": False, "values": ids if tag == CHROMA_TAG else sorted(ids)}

    # Core item highlights (neroland:highlight/<category>, Core 1.5.0): every decor block is a building
    # material; the collections add their own categories (the Chroma Hub is a machine, its handheld
    # items are tools) through Block.highlight / Item.highlight.
    highlight = {"materials": [block_id(n) for n, _k, _t, fam, _m in SPEC if fam != NEON_LEGACY]
                 + [block_id(n) for n, _k, _g, _e in LUMINOUS]}
    for cat, ids in COLLECTIONS["highlight"].items():
        highlight.setdefault(cat, []).extend(ids)
    for cat, ids in sorted(highlight.items()):
        files["data/neroland/tags/item/highlight/%s.json" % cat] = {"replace": False, "values": sorted(set(ids))}

    # lang
    files[LANG_FILE] = dict(LANG_HEAD + sorted(lang.items()))

    out = {rel: render(rel, obj) for rel, obj in files.items()}
    if "--check" in sys.argv[1:]:
        sys.exit(check(out))
    n = 0
    for rel, text in sorted(out.items()):
        path = os.path.join(RES, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(text)
        n += 1
    print("gen_resources: wrote %d JSON files for %d blocks" % (n, len(SPEC) + len(LUMINOUS)))


LANG_FILE = "assets/%s/lang/en_us.json" % NS
# Outputs under this prefix land in common/src/main/resources-26.3 (the 26.3 loot format overlay).
RES_26_3 = "../resources-26.3/"


def loot_26_3(table):
    """Pre-26.3 block loot -> the 26.3 format (the collection kit's converter)."""
    import importlib
    _collections_pkg()
    return importlib.import_module("nerodecor_collections._kit").loot_26_3(table)


def _collections_pkg():
    import importlib.util
    pkg = sys.modules.get("nerodecor_collections")
    if pkg is None:
        d = os.path.join(HERE, "collections")
        spec = importlib.util.spec_from_file_location("nerodecor_collections", os.path.join(d, "__init__.py"),
                                                      submodule_search_locations=[d])
        pkg = importlib.util.module_from_spec(spec)
        sys.modules["nerodecor_collections"] = pkg
        spec.loader.exec_module(pkg)
    return pkg

COLLECTIONS = {"lang": {}, "mineable": [], "decor": {}, "tags": {}, "highlight": {}, "owned": set()}
CHROMA_TAG = "%s:chroma_capable" % NS
# Families whose every block a Chroma hub may drive (the paintable SPEC families + Luminous).
CHROMA_FAMILIES = ["hull", "panel", "glass", "neon", "luminous"]


def collections_aggregate(strict):
    """Entries from tools/collections (loaded as `nerodecor_collections`, never as the stdlib name).
    strict on --check; otherwise a collection that fails to load is skipped with a warning."""
    import importlib.util
    pkg = sys.modules.get("nerodecor_collections")
    if pkg is None:
        d = os.path.join(HERE, "collections")
        spec = importlib.util.spec_from_file_location("nerodecor_collections", os.path.join(d, "__init__.py"),
                                                      submodule_search_locations=[d])
        pkg = importlib.util.module_from_spec(spec)
        sys.modules["nerodecor_collections"] = pkg
        spec.loader.exec_module(pkg)
    return pkg.aggregate(strict=strict)


# Every file under these roots is owned by this harness: --check flags anything here it does not emit.
OWNED_DIRS = ["assets/%s/blockstates" % NS, "assets/%s/models" % NS, "assets/%s/items" % NS,
              "data/%s/loot_table" % NS, "data/%s/recipe" % NS,
              "data/minecraft/tags/block", "data/neroland/tags/block", "data/%s/tags/block" % NS,
              "data/neroland/tags/item/highlight"]
OWNED_FILES = [LANG_FILE, "assets/%s/sounds.json" % NS]


def render(rel, obj):
    text = json.dumps(obj, indent=2, sort_keys=False) + "\n"
    if rel == LANG_FILE and QUEST_LANG:
        tail = ",\n".join("  %s: %s" % (json.dumps(k), json.dumps(v)) for k, v in QUEST_LANG)
        text = text[:-len("\n}\n")] + ",\n\n" + tail + "\n}\n"
    return text


def check(out):
    """Compare every output with the committed file (CRLF folded to LF: a Windows checkout under
    text=auto has CRLF in the working tree, git stores LF). Writes nothing; 1 on any drift."""
    problems = []
    for rel, text in sorted(out.items()):
        path = os.path.join(RES, rel)
        if not os.path.exists(path):
            problems.append("new: " + rel)
            continue
        with open(path, "rb") as fh:
            disk = fh.read().replace(b"\r\n", b"\n")
        if disk != text.encode("utf-8"):
            problems.append("stale: " + rel)
    owned = set(OWNED_FILES)
    for d in OWNED_DIRS:
        for root, _dirs, names in os.walk(os.path.join(RES, d)):
            for n in names:
                owned.add(os.path.relpath(os.path.join(root, n), RES).replace(os.sep, "/"))
    problems += ["orphan (not generated): " + r for r in sorted(owned - set(out) - COLLECTIONS["owned"])
                 if os.path.exists(os.path.join(RES, r))]
    if problems:
        print("gen_resources --check: DRIFT")
        for p in problems:
            print("  " + p)
        return 1
    print("gen_resources --check: clean (%d outputs)" % len(out))
    return 0


if __name__ == "__main__":
    main()
