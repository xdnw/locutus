package link.locutus.discord.commands.manager.v2.binding.validator;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.ParameterData;

public interface Validator<T> {
    T validate(CommandCallable callable, ParameterData param, ValueStore store, T value);
}
