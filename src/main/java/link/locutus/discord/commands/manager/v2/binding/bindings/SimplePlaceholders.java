package link.locutus.discord.commands.manager.v2.binding.bindings;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;

import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public class SimplePlaceholders<T> extends Placeholders<T> {

    private final String help;
    private final BiFunction<ValueStore, String, Set<T>> parse;
    private final BiFunction<ValueStore, String, Predicate<T>> parsePredicate;
    private final Function<T, String> getName;

    public SimplePlaceholders(Class<T> type, ValueStore store, ValidatorStore validators, PermissionHandler permisser, String help, BiFunction<ValueStore, String, Set<T>> parse, BiFunction<ValueStore, String, Predicate<T>> parsePredicate, Function<T, String> getName) {
        super(type, store, validators, permisser);
        this.help = help;
        this.parse = parse;
        this.parsePredicate = parsePredicate;
        this.getName = getName;
    }

    @Override
    public String getName(T o) {
        return getName.apply(o);
    }

    @Override
    public String getDescription() {
        return help;
    }

    @Override
    protected Set<T> parseSingleElem(ValueStore store, String input) {
        return parse.apply(store, input);
    }

    @Override
    protected Predicate<T> parseSingleFilter(ValueStore store, String input) {
        return parsePredicate.apply(store, input);
    }
}
