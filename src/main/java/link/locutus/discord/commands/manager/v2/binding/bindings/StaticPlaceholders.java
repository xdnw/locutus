package link.locutus.discord.commands.manager.v2.binding.bindings;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;

import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public class StaticPlaceholders<T> extends SimplePlaceholders<T> {
    public StaticPlaceholders(Class<T> type, ValueStore store, ValidatorStore validators, PermissionHandler permisser, String help, BiFunction<ValueStore, String, Set<T>> parse) {
        super(type, store, validators, permisser, help, parse, new BiFunction<ValueStore, String, Predicate<T>>() {
            @Override
            public Predicate<T> apply(ValueStore valueStore, String s) {
                Set<T> parsed = parse.apply(valueStore, s);
                return parsed::contains;
            }
        });
    }
}
