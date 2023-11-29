package link.locutus.discord.util.scheduler;

import java.util.function.BiFunction;

@FunctionalInterface
public interface ThrowingTriFunction<I, I2, I3, O> extends TriFunction<I, I2, I3, O> {

    @Override
    default O apply(I i, I2 i2, I3 i3) {
        try {
            return applyThrows(i, i2, i3);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }


    O applyThrows(I elem, I2 elem2, I3 elem3) throws Exception;
}