package link.locutus.discord.commands.manager.v2.binding.bindings;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.scheduler.TriFunction;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class StaticPlaceholders<T> extends SimpleVoidPlaceholders<T> {
    private final Supplier<T[]> enumValues;

    public StaticPlaceholders(Class<T> type, Supplier<T[]> values, ValueStore store, ValidatorStore validators, PermissionHandler permisser, String help, TriFunction<Placeholders<T, Void>, ValueStore, String, Set<T>> parse) {
        super(type, store, validators, permisser, help, parse, (inst, valueStore, s) -> {
            Set<T> parsed = parse.apply(inst, valueStore, s);
            return parsed::contains;
        }, t -> {
            if (t instanceof Enum) {
                return ((Enum<?>) t).name();
            }
            return t.toString();
        });
        this.enumValues = values;
    }

    @Override
    public final Set<SelectorInfo> getSelectorInfo() {
        T[] values = enumValues.get();
        String joined = Arrays.stream(values).map(StringMan::getString).collect(Collectors.joining("`, `", "`", "`"));
        return Set.of(
                new SelectorInfo(PlaceholdersMap.getClassName(getType().getSimpleName()).toUpperCase(), joined, "One of the entity values")
        );
    }
}
