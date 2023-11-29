package link.locutus.discord.commands.manager.v2.binding.bindings;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.util.scheduler.TriFunction;

import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public class StaticPlaceholders<T> extends SimplePlaceholders<T> {
    public StaticPlaceholders(Class<T> type, ValueStore store, ValidatorStore validators, PermissionHandler permisser, String help, TriFunction<Placeholders<T>, ValueStore, String, Set<T>> parse) {
        super(type, store, validators, permisser, help, parse, (inst, valueStore, s) -> {
            Set<T> parsed = parse.apply(inst, valueStore, s);
            return parsed::contains;
        }, t -> {
            if (t instanceof Enum) {
                return ((Enum<?>) t).name();
            }
            return t.toString();
        });
    }
}
