package link.locutus.discord.commands.manager.v2.perm;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.SimpleValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import net.dv8tion.jda.api.entities.User;

import java.lang.annotation.Annotation;

public class PermissionHandler extends SimpleValueStore {
    public void validate(ValueStore store, Annotation annotation) {
        Key<Object> key = Key.of(boolean.class, annotation);
        Parser parser = get(key);
        if (parser != null) {
            LocalValueStore locals = new LocalValueStore<>(store);
            locals.addProvider(Key.of(annotation.annotationType()), annotation);

            boolean hasPerm = (Boolean) parser.apply(locals, null);
            if (!hasPerm) {
                User userOrNull = (User) store.getProvided(Key.of(User.class, Me.class), false);
                String prefix = userOrNull != null ? "`" + userOrNull.getAsTag() + "` lacking permission: " : "Lacking permission: ";
                throw new IllegalCallerException(prefix + "`" + parser.getKey().toSimpleString() + "`: `" + parser.getDescription() + "`");
            }
        }
    }

    public boolean isPermission(Annotation annotation) {
        return get(Key.of(boolean.class, annotation)) != null;
    }
}
