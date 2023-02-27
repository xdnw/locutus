package link.locutus.discord.commands.manager.v2.binding;


public class LocalValueStore<T> extends DelegateValueStore<T> {
    private final ValueStore<T> global;

    public LocalValueStore(ValueStore<T> parent) {
        super(new SimpleValueStore<>());
        this.global = parent;
    }

    @Override
    public <V extends T> Parser<V> get(Key<V> key) {
        if (key.getType() == ValueStore.class) {
            return new ProviderParser<>((Key) key, this);
        }
        ValueStore<T> local = getParent();
        Parser<V> value = local.get(key);

        if (value == null) value = global.get(key);
        return value;
    }
}
