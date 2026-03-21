package link.locutus.discord.commands.manager.v2.binding.bindings;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;

import java.util.Set;

/**
 * Explicitly published only from live-app placeholder bootstraps that expose
 * raw entity command owners.
 */
public interface LiveAppPlaceholderRegistry extends PlaceholderRegistry {
    Set<Class<?>> getAppOnlyEntityCommandTypes();

    static LiveAppPlaceholderRegistry resolve(ValueStore store) {
        if (store != null) {
            LiveAppPlaceholderRegistry provided = (LiveAppPlaceholderRegistry) store
                    .getProvided(Key.of(LiveAppPlaceholderRegistry.class), false);
            if (provided != null) {
                return provided;
            }
        }
        return null;
    }
}