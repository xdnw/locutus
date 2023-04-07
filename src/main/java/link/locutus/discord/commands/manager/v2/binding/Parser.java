package link.locutus.discord.commands.manager.v2.binding;

import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;

import java.util.ArrayList;
import java.util.Arrays;

public interface Parser<T> {
    T apply(ArgumentStack arg);

    T apply(ValueStore store, Object t);

    boolean isConsumer(ValueStore store);

    Key getKey();

    String getDescription();

    default T apply(ValueStore store, ValidatorStore validators, PermissionHandler permisser, String... args) {
        ArgumentStack stack = new ArgumentStack(new ArrayList<>(Arrays.asList(args)), store, validators, permisser);
        return apply(stack);
    }
}
