package link.locutus.discord.util.scheduler;

import java.util.Map;

public class KeyValue<K, V> implements Map.Entry<K, V> {
    private final K key;
    private V value;

    public KeyValue(Map.Entry<K, V> entry) {
        this(entry.getKey(), entry.getValue());
    }

    public static final <T, V> KeyValue<T, V> of(T key, V value) {
        return new KeyValue<>(key, value);
    }

    public KeyValue(K key, V value) {
        this.key = key;
        this.value = value;
    }
    @Override
    public K getKey() {
        return key;
    }

    @Override
    public V getValue() {
        return value;
    }

    @Override
    public V setValue(V value) {
        V old = this.value;
        this.value = value;
        return old;
    }
}
