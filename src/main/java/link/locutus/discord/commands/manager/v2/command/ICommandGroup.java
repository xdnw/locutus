package link.locutus.discord.commands.manager.v2.command;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public interface ICommandGroup extends CommandCallable {
    public Map<String, CommandCallable> getSubcommands();

    default Set<String> getSubCommandIds() {
        return getSubcommands().keySet();
    }

    default Set<String> primarySubCommandIds() {
        Set<String> aliases = new HashSet<>();
        for (CommandCallable cmd : new HashSet<>(getSubcommands().values())) {
            aliases.add(cmd.aliases().get(0));
        }
        return aliases;
    }
}
