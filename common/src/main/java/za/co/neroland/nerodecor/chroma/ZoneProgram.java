package za.co.neroland.nerodecor.chroma;

/** What a zone plays: a {@link Timeline} or an {@link EffectProgram}. */
public sealed interface ZoneProgram permits Timeline, EffectProgram {

    /**
     * Colour of a block at {@code (x, y, z)} relative to the hub, {@code elapsedTicks}
     * ({@code gameTime - startTick}) after the program started. Deterministic.
     */
    ChromaColor colourAt(long elapsedTicks, int x, int y, int z, long seed);

    /** Whether every position shows the same colour at a given tick. */
    boolean positionIndependent();
}
