package link.locutus.discord.commands.manager.v2.binding;

import java.util.function.Supplier;

public class SupplierParser<T> extends ProviderParser<T> {
    public SupplierParser(Key<T> key, Supplier<T> value) {
        super(key, (T) value);
    }

    @Override
    public T getValue() {
        return ((Supplier<T>) super.getValue()).get();
    }
}
