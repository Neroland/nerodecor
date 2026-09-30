package za.co.neroland.nerodecor.content.fx;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerodecor.config.NeroDecorConfig;

/**
 * The client-side ambience of a Luminous-collection block: an occasional particle mote and an
 * occasional, quiet sound. Driven entirely from {@code Block#animateTick}, so it costs nothing on
 * a server, needs no block entity and never ticks per block (ADR-001's block-entity policy).
 *
 * <p>Two guards keep it subtle at any build size. Each sound has a <b>global cooldown</b> keyed by
 * its event, so a wall of two hundred circuit plates chirps no more often than one plate would.
 * And both halves honour the client-local {@code ambientParticles} / {@code ambientSounds}
 * switches in {@link NeroDecorConfig}.
 */
public final class AmbientFx {

    /** What kind of particle mote a block releases. */
    public enum Motes {
        NONE,
        /** Starfield: a white star-mote drifting off an exposed face. */
        STAR_GLINT,
        /** Void rift: portal specks pulled inward from all around the block. */
        RIFT_PULL,
        /** Plasma conduit: a brief electric spark on an exposed face. */
        CONDUIT_SPARK,
        /** Crystal lattice: a lilac glint that hangs on a face. */
        CRYSTAL_GLINT,
        /** Ion vent: a soft wisp rising from the grille, with the odd spark. */
        VENT_PLUME,
        /** Xenobloom: a bioluminescent spore floating up. */
        SPORE_GLOW,
        /** Fusion lamp (lit): a bright mote rising from the core. */
        LAMP_MOTE
    }

    /** No particles, no sound. */
    public static final AmbientFx NONE = builder().build();

    private static final Map<SoundEvent, Long> LAST_PLAYED = new HashMap<>();
    private static final double FACE = 0.5625D;
    private static final ParticleOptions LILAC_GLINT = new DustParticleOptions(0xC8A6FF, 0.7F);

    private final Motes motes;
    private final int moteOdds;
    @Nullable
    private final Supplier<SoundEvent> sound;
    private final int soundOdds;
    private final float volume;
    private final float pitch;
    private final int cooldownTicks;

    private AmbientFx(Builder b) {
        this.motes = b.motes;
        this.moteOdds = Math.max(1, b.moteOdds);
        this.sound = b.sound;
        this.soundOdds = Math.max(1, b.soundOdds);
        this.volume = b.volume;
        this.pitch = b.pitch;
        this.cooldownTicks = b.cooldownTicks;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Call from {@code animateTick} (client only). */
    public void animate(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (motes != Motes.NONE && random.nextInt(moteOdds) == 0 && NeroDecorConfig.ambientParticlesEnabled()) {
            spawn(level, pos, random);
        }
        if (sound != null && random.nextInt(soundOdds) == 0 && NeroDecorConfig.ambientSoundsEnabled()) {
            SoundEvent event = sound.get();
            long now = level.getGameTime();
            Long last = LAST_PLAYED.get(event);
            if (last == null || now < last || now - last >= cooldownTicks) {
                LAST_PLAYED.put(event, now);
                float p = pitch * (0.94F + random.nextFloat() * 0.12F);
                level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                        event, SoundSource.BLOCKS, volume, p, false);
            }
        }
    }

    private void spawn(Level level, BlockPos pos, RandomSource random) {
        switch (motes) {
            case STAR_GLINT -> {
                Direction d = exposedFace(level, pos, random);
                if (d != null) {
                    faceParticle(level, pos, d, random, ParticleTypes.END_ROD, 0.012D);
                }
            }
            case RIFT_PULL -> {
                for (int i = 0; i < 2; i++) {
                    // PORTAL particles travel from (pos + offset) back to pos: the rift inhales them.
                    level.addParticle(ParticleTypes.PORTAL, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                            (random.nextDouble() - 0.5D) * 2.2D, (random.nextDouble() - 0.5D) * 2.2D,
                            (random.nextDouble() - 0.5D) * 2.2D);
                }
            }
            case CONDUIT_SPARK -> {
                Direction d = exposedFace(level, pos, random);
                if (d != null) {
                    faceParticle(level, pos, d, random, ParticleTypes.ELECTRIC_SPARK, 0.04D);
                }
            }
            case CRYSTAL_GLINT -> {
                Direction d = exposedFace(level, pos, random);
                if (d != null) {
                    faceParticle(level, pos, d, random, LILAC_GLINT, 0.0D);
                }
            }
            case VENT_PLUME -> {
                BlockPos above = pos.above();
                if (!level.getBlockState(above).isSolidRender()) {
                    double x = pos.getX() + 0.2D + random.nextDouble() * 0.6D;
                    double z = pos.getZ() + 0.2D + random.nextDouble() * 0.6D;
                    level.addParticle(ParticleTypes.WHITE_SMOKE, x, pos.getY() + 1.02D, z,
                            0.0D, 0.018D + random.nextDouble() * 0.01D, 0.0D);
                    if (random.nextInt(5) == 0) {
                        level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, pos.getY() + 1.05D, z, 0.0D, 0.05D, 0.0D);
                    }
                }
            }
            case SPORE_GLOW -> {
                Direction d = exposedFace(level, pos, random);
                if (d != null && d != Direction.DOWN) {
                    faceParticle(level, pos, d, random, ParticleTypes.GLOW, 0.01D);
                }
            }
            case LAMP_MOTE -> {
                Direction d = exposedFace(level, pos, random);
                if (d != null) {
                    faceParticle(level, pos, d, random, ParticleTypes.END_ROD, 0.02D);
                }
            }
            default -> {
            }
        }
    }

    /** A random face whose neighbour does not hide it, or {@code null}. */
    @Nullable
    private static Direction exposedFace(Level level, BlockPos pos, RandomSource random) {
        Direction d = Direction.getRandom(random);
        return level.getBlockState(pos.relative(d)).isSolidRender() ? null : d;
    }

    private static void faceParticle(Level level, BlockPos pos, Direction d, RandomSource random,
                                     ParticleOptions particle, double outward) {
        Direction.Axis axis = d.getAxis();
        double x = axis == Direction.Axis.X ? 0.5D + FACE * d.getStepX() : random.nextDouble();
        double y = axis == Direction.Axis.Y ? 0.5D + FACE * d.getStepY() : random.nextDouble();
        double z = axis == Direction.Axis.Z ? 0.5D + FACE * d.getStepZ() : random.nextDouble();
        level.addParticle(particle, pos.getX() + x, pos.getY() + y, pos.getZ() + z,
                d.getStepX() * outward, d.getStepY() * outward + outward * 0.5D, d.getStepZ() * outward);
    }

    /** Fluent builder; every block's ambience reads as one line in {@code LuminousBlocks}. */
    public static final class Builder {
        private Motes motes = Motes.NONE;
        private int moteOdds = 1;
        @Nullable
        private Supplier<SoundEvent> sound;
        private int soundOdds = 1;
        private float volume = 0.3F;
        private float pitch = 1.0F;
        private int cooldownTicks = 100;

        private Builder() {
        }

        /** Release {@code motes} on roughly one in {@code odds} animate ticks. */
        public Builder motes(Motes kind, int odds) {
            this.motes = kind;
            this.moteOdds = odds;
            return this;
        }

        /**
         * Play {@code event} on roughly one in {@code odds} animate ticks, at {@code volume}, never more
         * often than once per {@code cooldownTicks} across all blocks sharing that sound.
         */
        public Builder sound(Supplier<SoundEvent> event, int odds, float vol, int cooldown) {
            this.sound = event;
            this.soundOdds = odds;
            this.volume = vol;
            this.cooldownTicks = cooldown;
            return this;
        }

        public Builder pitch(float p) {
            this.pitch = p;
            return this;
        }

        public AmbientFx build() {
            return new AmbientFx(this);
        }
    }
}
