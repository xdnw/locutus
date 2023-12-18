package link.locutus.discord.util.scheduler;

import java.util.function.Consumer;
import java.util.function.Supplier;

@FunctionalInterface
public interface ThrowingSupplier<T> extends Supplier<T> {

    @Override
    default T get() {
        try {
            return getThrows();
        } catch (final Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    T getThrows() throws Exception;

}