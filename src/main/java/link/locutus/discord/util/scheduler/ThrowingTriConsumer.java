package link.locutus.discord.util.scheduler;

import java.util.function.Consumer;

@FunctionalInterface
public interface ThrowingTriConsumer<A, B, C> extends TriConsumer<A, B, C> {

    @Override
    default void accept(final A a, final B b, final C c) {
        try {
            acceptThrows(a, b, c);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    void acceptThrows(A a, B b, C c) throws Exception;

}