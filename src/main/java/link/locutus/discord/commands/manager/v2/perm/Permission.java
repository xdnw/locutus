package link.locutus.discord.commands.manager.v2.perm;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;

public interface Permission {
    boolean test(ValueStore store);
}
