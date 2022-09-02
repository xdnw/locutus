package link.locutus.discord.commands.manager.v2.command;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public interface ICommandGroup extends CommandCallable {
    public Map<String, CommandCallable> getSubcommands();

    default Set<String> getSubCommandIds() {
        return getSubcommands().keySet();
    }

    default Set<String> primarySubCommandIds() {
        Map<CommandCallable, String> reverse = new HashMap<>();
        getSubcommands().forEach((k, v) -> reverse.putIfAbsent(v, k));
        return new HashSet<>(reverse.values());
    }
}
