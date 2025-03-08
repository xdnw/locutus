package link.locutus.discord.commands.manager.v2.binding.bindings;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.util.scheduler.TriFunction;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class SimplePlaceholders<T, M> extends Placeholders<T, M> {

    private final String help;
    private final TriFunction<Placeholders<T, M>, ValueStore, String, Set<T>> parse;
    private final TriFunction<Placeholders<T, M>, ValueStore, String, Predicate<T>> parsePredicate;
    private final Function<T, String> getName;

    public SimplePlaceholders(Class<T> type, Class<M> modifier, ValueStore store, ValidatorStore validators, PermissionHandler permisser, String help, TriFunction<Placeholders<T, M>, ValueStore, String, Set<T>> parse, TriFunction<Placeholders<T, M>, ValueStore, String, Predicate<T>> parsePredicate, Function<T, String> getName) {
        super(type, modifier, store, validators, permisser);
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
        return parse.apply(this, store, input);
    }

    @Override
    protected Predicate<T> parseSingleFilter(ValueStore store, String input) {
        return parsePredicate.apply(this, store, input);
    }
}
