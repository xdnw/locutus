package link.locutus.discord.commands.manager.v2.binding;

import link.locutus.discord.commands.manager.v2.command.ArgumentStack;

import java.util.Map;
import java.util.function.Function;

public class FunctionProviderParser<T> implements Parser<T> {
    private final Key<T> key;
    private final Function<ValueStore, T> provider;
    private String[] examples;
    private Class<?>[] webType;

    public FunctionProviderParser(Key<T> key, Function<ValueStore, T> provider) {
        this.key = key;
        this.provider = provider;
    }

    @Override
    public T apply(ArgumentStack arg) {
        return provider.apply(arg.getStore());
    }

    @Override
    public T apply(ValueStore store, Object t) {
        return provider.apply(store);
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
    public String[] getExamples() {
        return examples == null ? new String[0] : examples;
    }

    @Override
    public Class<?>[] getWebType() {
        return webType == null ? new Class<?>[0] : webType;
    }

    public FunctionProviderParser<T> setWebType(Class<?>... webType) {
        this.webType = webType;
        return this;
    }

    public FunctionProviderParser<T> setExamples(String... examples) {
        this.examples = examples;
        return this;
    }

    @Override
    public Map<String, Object> toJson() {
        throw new UnsupportedOperationException();
    }
}
