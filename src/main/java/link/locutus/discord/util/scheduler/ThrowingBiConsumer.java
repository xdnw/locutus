package link.locutus.discord.util.scheduler;

import java.util.function.BiConsumer;

@FunctionalInterface
public interface ThrowingBiConsumer<T, V> extends BiConsumer<T, V> {

    @Override
    default void accept(T t, V v) {
        try {
            acceptThrows(t, v);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    void acceptThrows(T elem, V elem2) throws Exception;

}