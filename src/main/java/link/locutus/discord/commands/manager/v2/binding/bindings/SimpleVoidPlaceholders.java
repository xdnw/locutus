package link.locutus.discord.commands.manager.v2.binding.bindings;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.util.scheduler.TriFunction;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class SimpleVoidPlaceholders<T> extends SimplePlaceholders<T, Void> {

    public SimpleVoidPlaceholders(Class<T> type, ValueStore store, ValidatorStore validators, PermissionHandler permisser, String help, TriFunction<Placeholders<T, Void>, ValueStore, String, Set<T>> parse, TriFunction<Placeholders<T, Void>, ValueStore, String, Predicate<T>> parsePredicate, Function<T, String> getName) {
        super(type, Void.class, store, validators, permisser, help, parse, parsePredicate, getName);
    }
}
