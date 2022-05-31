package com.boydti.discord.commands.manager.v2.binding;

import com.boydti.discord.commands.manager.v2.command.CommandCallable;
import com.boydti.discord.commands.manager.v2.command.ParameterData;

public class LocalValueStore<T> extends DelegateValueStore<T> {
    private final ValueStore<T> global;

    public LocalValueStore(ValueStore<T> parent) {
        super(new SimpleValueStore<T>());
        this.global = parent;
    }

    @Override
    public <V extends T> Parser<V> get(Key<V> key) {
        ValueStore<T> local = getParent();
        Parser<V> value = local.get(key);

        if (value == null) value = global.get(key);
        return value;
    }
}
