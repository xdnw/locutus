package com.boydti.discord.commands.manager.v2.binding.validator;

import com.boydti.discord.commands.manager.v2.binding.Key;
import com.boydti.discord.commands.manager.v2.binding.LocalValueStore;
import com.boydti.discord.commands.manager.v2.binding.Parser;
import com.boydti.discord.commands.manager.v2.binding.SimpleValueStore;
import com.boydti.discord.commands.manager.v2.binding.ValueStore;
import com.boydti.discord.commands.manager.v2.binding.annotation.Range;
import com.boydti.discord.commands.manager.v2.command.ParameterData;

import javax.validation.constraints.NotNull;
import java.lang.annotation.Annotation;

public class ValidatorStore extends SimpleValueStore {
    public <T> T validate(ParameterData data, ValueStore store, T object) {
        if (object == null) {
            for (Annotation annotation : data.getAnnotations()) {
                if (annotation.annotationType() == NotNull.class) throw new IllegalArgumentException("Object fgor " + data + " cannot be null");
            }
            return null;
        }
        for (Annotation annotation : data.getAnnotations()) {
            Key key = Key.of(data.getType(), annotation);
            Parser parser = get(key);
            if (parser != null) {
                LocalValueStore locals = new LocalValueStore<>(store);
                locals.addProvider(data);
                locals.addProvider(Key.of(annotation.annotationType()), annotation);
                return (T) parser.apply(locals, object);
            }
        }
        return object;
    }
}