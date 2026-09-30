package za.co.neroland.nerodecor.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;

import za.co.neroland.nerodecor.NeroDecorCommon;
import za.co.neroland.nerodecor.registry.RegistrationProvider.RegistryEntry;

/**
 * NeroDecor's sound events — the quiet ambience of the Luminous collection. The audio is
 * synthesised by {@code tools/gen_sounds.py} (no samples) and mapped in
 * {@code assets/nerodecor/sounds.json}; playback frequency and volume live in
 * {@link za.co.neroland.nerodecor.content.fx.AmbientFx}.
 */
public final class ModSounds {

    public static final RegistrationProvider<SoundEvent> SOUNDS =
            RegistrationProvider.get(Registries.SOUND_EVENT, NeroDecorCommon.MOD_ID);

    public static final RegistryEntry<SoundEvent> CIRCUIT_CHIRP = register("block.circuit.chirp");
    public static final RegistryEntry<SoundEvent> VOID_RIFT_HUM = register("block.void_rift.hum");
    public static final RegistryEntry<SoundEvent> CONDUIT_CRACKLE = register("block.plasma_conduit.crackle");
    public static final RegistryEntry<SoundEvent> CRYSTAL_CHIME = register("block.crystal_lattice.chime");
    public static final RegistryEntry<SoundEvent> LAMP_POWER_ON = register("block.fusion_lamp.power_on");
    public static final RegistryEntry<SoundEvent> LAMP_POWER_OFF = register("block.fusion_lamp.power_off");
    public static final RegistryEntry<SoundEvent> LAMP_HUM = register("block.fusion_lamp.hum");
    public static final RegistryEntry<SoundEvent> VENT_HISS = register("block.ion_vent.hiss");
    public static final RegistryEntry<SoundEvent> CAPACITOR_CHARGE = register("block.capacitor_bank.charge");

    private ModSounds() {
    }

    private static RegistryEntry<SoundEvent> register(String path) {
        return SOUNDS.register(path, key -> SoundEvent.createVariableRangeEvent(key.identifier()));
    }

    /** Loads the class (and so registers every event). Call before any block that plays them. */
    public static void init() {
    }
}
