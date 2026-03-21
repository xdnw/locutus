package link.locutus.discord.commands.manager.v2.binding.bindings;

import java.util.Set;

/**
 * Optional live-app-only extension for placeholders whose app bootstrap needs
 * more than the default "register the placeholder's own entity type" rule.
 */
public interface LiveAppPlaceholderExtension {
    void registerAdditionalLiveAppEntityCommands();

    Set<Class<?>> getAdditionalLiveAppEntityCommandTypes();
}
