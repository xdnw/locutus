package link.locutus.discord.util.scheduler;

import java.util.function.BiFunction;

@FunctionalInterface
public interface ThrowingBiFunction<I, I2, O> extends BiFunction<I, I2, O> {

    @Override
    default O apply(I i, I2 i2) {
        try {
            return applyThrows(i, i2);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    O applyThrows(I elem, I2 elem2) throws Exception;
}