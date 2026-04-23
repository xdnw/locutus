package link.locutus.discord.sim.combat;

import java.util.SplittableRandom;

/**
 * Deterministic-by-key RNG used by {@link ResolutionMode#STOCHASTIC} resolution.
 *
 * <p>Streams are keyed so changing one caller's decisions cannot reshuffle another caller's
 * samples. The sim layer composes stream keys from {@code (turn, nationId, actionIndex)};
 * unit tests can key by any stable identifier.
 */
public interface RandomSource {
    /** Uniform {@code [0, 1)} deterministically derived from the stream key. */
    double nextDouble(long streamKey);

    /** 64-bit sample deterministically derived from the stream key. */
    long nextLong(long streamKey);

    /** In-memory implementation backed by a per-call {@link SplittableRandom}. */
    static RandomSource splittable(long seed) {
        return new RandomSource() {
            @Override
            public double nextDouble(long streamKey) {
                return new SplittableRandom(seed ^ mix(streamKey)).nextDouble();
            }

            @Override
            public long nextLong(long streamKey) {
                return new SplittableRandom(seed ^ mix(streamKey)).nextLong();
            }

            // SplittableRandom.mix64 isn't public; reproduce the constant so streams are stable.
            private long mix(long z) {
                z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b7L;
                z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
                return z ^ (z >>> 31);
            }
        };
    }
}
