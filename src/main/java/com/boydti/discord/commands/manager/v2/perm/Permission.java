package com.boydti.discord.commands.manager.v2.perm;

import com.boydti.discord.commands.manager.v2.binding.ValueStore;

public interface Permission {
    boolean test(ValueStore store);
}
