package link.locutus.discord.commands.manager.v2.binding;

import link.locutus.discord.commands.manager.v2.command.ArgumentStack;

import java.util.Map;

public class ProviderParser<T> implements Parser<T> {
    private final Key<T> key;
    private final T value;

    public ProviderParser(Key<T> key, T value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public String[] getExamples() {
        return new String[0];
    }

    @Override
    public Class<?>[] getWebType() {
        return new Class[0];
    }

    public T getValue() {
        return value;
    }

    @Override
    public T apply(ArgumentStack arg) {
        return getValue();
    }

    @Override
    public T apply(ValueStore store, Object t) {
        return getValue();
    }

    @Override
    public boolean isConsumer(ValueStore store) {
        return false;
    }

    @Override
    public Key getKey() {
        return key;
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public Map<String, Object> toJson() {
        throw new UnsupportedOperationException();
    }
}
