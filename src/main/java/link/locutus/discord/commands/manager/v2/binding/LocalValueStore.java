package link.locutus.discord.commands.manager.v2.binding;


import java.util.LinkedHashMap;
import java.util.Map;

public class LocalValueStore<T> extends DelegateValueStore<T> {
    private final ValueStore<T> global;

    public LocalValueStore(ValueStore<T> parent) {
        super(new SimpleValueStore<>());
        this.global = parent;
    }

    @Override
    public <V> Parser<V> get(Key<V> key) {
        if (key.getType() == ValueStore.class) {
            return new ProviderParser<>((Key) key, this);
        }
        ValueStore<T> local = getParent();
        Parser<V> value = local.get(key);

        if (value == null) value = global.get(key);
        return value;
    }

    @Override
    public Map<Key, Parser> getParsers() {
        Map<Key, Parser> parsers = new LinkedHashMap<>();
        parsers.putAll(getParent().getParsers());
        for (Map.Entry<Key, Parser> entry : global.getParsers().entrySet()) {
            if (!parsers.containsKey(entry.getKey())) {
                parsers.put(entry.getKey(), entry.getValue());
            }
        }
        return parsers;
    }
}
