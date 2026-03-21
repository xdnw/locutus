package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Neutral command catalog contract exposed to command parsing and autocomplete.
 */
public interface CommandRuntimeCommandContext {
    CommandCallable getCommand(List<String> args);

    Map<String, String> validateSlashCommand(String input, boolean strict);

    Collection<ParametricCallable<?>> getParametricCommands();
}
