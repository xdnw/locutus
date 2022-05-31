package com.boydti.discord.commands.manager.v2.perm;

import com.boydti.discord.commands.manager.v2.binding.Key;
import com.boydti.discord.commands.manager.v2.binding.LocalValueStore;
import com.boydti.discord.commands.manager.v2.binding.MethodParser;
import com.boydti.discord.commands.manager.v2.binding.Parser;
import com.boydti.discord.commands.manager.v2.binding.SimpleValueStore;
import com.boydti.discord.commands.manager.v2.binding.ValueStore;
import com.boydti.discord.commands.manager.v2.command.ParameterData;

import java.lang.annotation.Annotation;

public class PermissionHandler extends SimpleValueStore {
    public void validate(ValueStore store, Annotation annotation) {
        Key key = Key.of(boolean.class, annotation);
        Parser parser = get(key);
        if (parser != null) {
            LocalValueStore locals = new LocalValueStore<>(store);
            locals.addProvider(Key.of(annotation.annotationType()), annotation);

            boolean hasPerm = (Boolean) parser.apply(locals, null);
            if (!hasPerm) {
                throw new IllegalCallerException("No permission");
            }
        }
    }
}
