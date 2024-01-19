package link.locutus.discord.commands.manager.v2.binding;

import java.util.Map;
import java.util.Set;

public class DelegateValueStore<T> implements ValueStore<T> {
    private final ValueStore<T> parent;

    public DelegateValueStore(ValueStore<T> parent) {
        this.parent = parent;
    }

    public ValueStore<T> getParent() {
        return parent;
    }

    @Override
    public <V extends T> Parser<V> addParser(Key<V> key, Parser<V> parser) {
        return parent.addParser(key, parser);
    }

    @Override
    public <V extends T> Parser<V> get(Key<V> key) {
        if (key.getType() == ValueStore.class) {
            return new ProviderParser<>((Key) key, this);
        }
        return parent.get(key);
    }

    @Override
    public Map<Key, Parser> getParsers() {
        return parent.getParsers();
    }
}