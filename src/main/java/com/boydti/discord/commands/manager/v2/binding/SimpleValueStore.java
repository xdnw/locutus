package com.boydti.discord.commands.manager.v2.binding;

import com.boydti.discord.commands.manager.v2.command.ParameterData;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.math.ArrayUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class SimpleValueStore<T> implements ValueStore<T> {
    public Map<Type, Map<Key, Parser>> bindings = new LinkedHashMap<>();
    public Set<Type> allowedAnnotations = new LinkedHashSet<>();

    public SimpleValueStore() {
    }

    @Override
    public <V extends T> Parser<V> addParser(Key<V> key, Parser<V> parser) {
        allowedAnnotations.addAll(key.getAnnotationTypes());
        bindings.computeIfAbsent(key.getType(), f -> new LinkedHashMap<>()).put(key, parser);
        return parser;
    }

    @Override
    public <V extends T> Parser<V> get(Key<V> key) {
        if (!key.getAnnotationTypes().isEmpty()) {
            Set<Class<?>> types = new LinkedHashSet<>(key.getAnnotationTypes());
//            List<Annotation> set = new LinkedList<>(Arrays.asList(key.getAnnotations()));

            types.removeIf(f -> !allowedAnnotations.contains(f));
            if (types.size() != key.getAnnotationTypes().size()) {
                key = Key.of(key.getType(), types.toArray(new Class[0]));
            }
        }

        Map<Key, Parser> allowed = bindings.getOrDefault(key.getType(), Collections.emptyMap());

        Parser result = allowed.get(key);
        return result;
    }
}
