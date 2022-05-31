package com.boydti.discord.commands.manager.v2.binding.validator;

import com.boydti.discord.commands.manager.v2.binding.ValueStore;
import com.boydti.discord.commands.manager.v2.command.CommandCallable;
import com.boydti.discord.commands.manager.v2.command.ParameterData;

public interface Validator<T> {
    T validate(CommandCallable callable, ParameterData param, ValueStore store, T value);
}
