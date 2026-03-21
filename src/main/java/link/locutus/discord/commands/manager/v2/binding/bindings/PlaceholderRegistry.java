package link.locutus.discord.commands.manager.v2.binding.bindings;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;

import java.util.Set;

public interface PlaceholderRegistry {
    Set<Class<?>> getTypes();

    <T, M> Placeholders<T, M> get(Class<T> type);

    static PlaceholderRegistry resolve(ValueStore store) {
        if (store != null) {
            PlaceholderRegistry provided = (PlaceholderRegistry) store.getProvided(Key.of(PlaceholderRegistry.class), false);
            if (provided != null) {
                return provided;
            }
        }
        return null;
    }
}