package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.util.math.ArrayUtil;

import java.util.*;

public interface ICommandGroup extends CommandCallable {
    Map<String, CommandCallable> getSubcommands();

    default Map<String, CommandCallable> getSubcommandsSorted() {
        return ArrayUtil.sortMap(getSubcommands(), Comparator.comparing(CommandCallable::getPrimaryCommandId));
    }

    default Set<String> getSubCommandIds() {
        return getSubcommands().keySet();
    }

    default Set<String> primarySubCommandIds() {
        Map<CommandCallable, String> reverse = new HashMap<>();
        getSubcommandsSorted().forEach((k, v) -> reverse.putIfAbsent(v, k));
        return new HashSet<>(reverse.values());
    }
}
