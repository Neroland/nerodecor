package za.co.neroland.nerodecor.chroma;

/** A zone program that runs one procedural effect. */
public record EffectProgram(EffectType type, EffectParams params) implements ZoneProgram {

    public EffectProgram {
        if (type == null || params == null) {
            throw new IllegalArgumentException("effect program needs a type and params");
        }
    }

    @Override
    public ChromaColor colourAt(long elapsedTicks, int x, int y, int z, long seed) {
        return ChromaEffects.colourAt(type, elapsedTicks, x, y, z, seed, params);
    }

    @Override
    public boolean positionIndependent() {
        return type == EffectType.PULSE || type == EffectType.BREATHE || type == EffectType.SKY_MIRROR;
    }
}
