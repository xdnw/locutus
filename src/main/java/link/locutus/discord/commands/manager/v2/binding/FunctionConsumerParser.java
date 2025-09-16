package link.locutus.discord.commands.manager.v2.binding;

import link.locutus.discord.commands.manager.v2.command.ArgumentStack;

import java.util.Map;
import java.util.function.BiFunction;

public class FunctionConsumerParser<R> implements Parser<R> {
    private final Key<R> key;
    private final BiFunction<ValueStore, String, R> consumer;

    public FunctionConsumerParser(Key<R> returnKey, BiFunction<ValueStore, String, R> consumer) {
        this.key = returnKey;
        this.consumer = consumer;
    }

    @Override
    public R apply(ArgumentStack arg) {
        return consumer.apply(arg.getStore(), arg.consumeNext());
    }

    @Override
    public R apply(ValueStore store, Object t) {
        return consumer.apply(store, t.toString());
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
        return new String[0];
    }

    @Override
    public Class<?>[] getWebType() {
        return new Class[0];
    }

    @Override
    public Map<String, Object> toJson() {
        throw new UnsupportedOperationException();
    }
}
