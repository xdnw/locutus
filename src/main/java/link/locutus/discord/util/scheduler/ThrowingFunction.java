package link.locutus.discord.util.scheduler;

import java.util.function.Function;

@FunctionalInterface
public interface ThrowingFunction<I, O> extends Function<I, O> {

    @Override
    default O apply(I i) {
        try {
            return applyThrows(i);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    O applyThrows(I elem) throws Exception;
}