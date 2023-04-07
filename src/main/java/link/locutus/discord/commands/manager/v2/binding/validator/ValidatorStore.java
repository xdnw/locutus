package link.locutus.discord.commands.manager.v2.binding.validator;

import link.locutus.discord.commands.manager.v2.binding.*;
import link.locutus.discord.commands.manager.v2.command.ParameterData;

import javax.validation.constraints.NotNull;
import java.lang.annotation.Annotation;

public class ValidatorStore extends SimpleValueStore {
    public <T> T validate(ParameterData data, ValueStore store, T object) {
        if (object == null) {
            for (Annotation annotation : data.getAnnotations()) {
                if (annotation.annotationType() == NotNull.class)
                    throw new IllegalArgumentException("Object fgor " + data + " cannot be null");
            }
            return null;
        }
        for (Annotation annotation : data.getAnnotations()) {
            Key<Object> key = Key.of(data.getType(), annotation);
            Parser parser = get(key);
            if (parser != null) {
                LocalValueStore locals = new LocalValueStore<>(store);
                locals.addProvider(ParameterData.class, data);
                locals.addProvider(Key.of(annotation.annotationType()), annotation);
                return (T) parser.apply(locals, object);
            }
        }
        return object;
    }
}