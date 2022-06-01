package link.locutus.discord.commands.manager.v2.binding.bindings;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import org.apache.commons.lang3.StringEscapeUtils;

import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class Placeholders<T> {
    private final ValidatorStore validators;
    private final PermissionHandler permisser;
    private final CommandGroup commands;

    private final T nullInstance;
    private final Type instanceType;

    public Placeholders(Type type, T nullInstance, ValueStore store, ValidatorStore validators, PermissionHandler permisser) {
        this.nullInstance = nullInstance;
        this.instanceType = type;

        this.validators = validators;
        this.permisser = permisser;

        this.commands = CommandGroup.createRoot(store, validators);
        this.commands.registerCommands(nullInstance);
        this.commands.registerCommands(this);
    }

    public Set<String> getKeys() {
        return commands.primarySubCommandIds();
    }

    public List<ParametricCallable> getParametricCallables() {
        List<ParametricCallable> result = new ArrayList<>();
        for (CommandCallable value : this.commands.getSubcommands().values()) {
            if (value instanceof ParametricCallable) {
                result.add((ParametricCallable) value);
            }
        }
        return result;
    }

    public ParametricCallable get(String cmd) {
        return (ParametricCallable) commands.get(cmd);
    }

    public String getHtml(ValueStore store, String cmd, String parentId) {
        ParametricCallable callable = get(cmd);
        StringBuilder html = new StringBuilder(callable.toBasicHtml(store));
        html.append("<select name=\"filter-operator\" for=\"" + parentId + "\" class=\"form-control\">");
        for (Operation value : Operation.values()) {
            String selected = value == Operation.EQUAL ? "selected=\"selected\"" : "";
            html.append("<option value=\"" + value.code + "\" " + selected + ">" + StringEscapeUtils.escapeHtml4(value.code) + "</option>");
        }
        html.append("</select>");
        Type type = callable.getReturnType();
        if (type == String.class) {
            html.append("<input name=\"filter-value\" for=\"" + parentId + "\" required type=\"text\" class=\"form-control\"/>");
        }  else if (type == boolean.class || type == Boolean.class) {
            html.append("<select name=\"filter-value\" for=\"" + parentId + "\" required class=\"form-control\" /><option>true</option><option>false</option></select>");
        } else if (type == int.class || type == Integer.class || type == double.class || type == Double.class || type == long.class || type == Long.class) {
            html.append("<input name=\"filter-value\" for=\"" + parentId + "\" required type=\"number\" class=\"form-control\"/>");
        } else {
            throw new IllegalArgumentException("Only the following filter types are supported: String, Number, Boolean");
        }
        html.append("<button id=\"addfilter.submit\" for=\"" + parentId + "\" class=\"btn btn-primary\" >Add Filter</button>");

        return html.toString();
    }

    public List<CommandCallable> getPlaceholderCallables() {
        return new ArrayList<>(getParametricCallables());
    }

    public List<CommandCallable> getFilterCallables() {
        List<ParametricCallable> result = getParametricCallables();
        result.removeIf(cmd -> {
            Type type = cmd.getReturnType();
            if (type == String.class || type == boolean.class || type == Boolean.class || type == int.class || type == Integer.class || type == double.class || type == Double.class || type == long.class || type == Long.class) {
                return false;
            }
            return true;
        });
        return new ArrayList<>(result);
    }

    public Predicate<T> getFilter(ValueStore store, String input) {
        int argEnd = input.lastIndexOf(')');

        for (Operation op : Operation.values()) {
            int index = input.lastIndexOf(op.code);
            if (index > argEnd) {
                String part1 = input.substring(0, index);
                String part2 = input.substring(index + op.code.length());

                Map.Entry<Type, Function<T, Object>> placeholder = getPlaceholderFunction(store, part1);
                Function<T, Object> func = placeholder.getValue();
                Type type = placeholder.getKey();
                Predicate adapter;

                if (type == String.class) {
                    adapter = op.getStringPredicate(part2);
                }
                else if (type == boolean.class || type == Boolean.class) {
                    boolean val2 = PrimitiveBindings.Boolean(part2);
                    adapter = op.getBooleanPredicate(val2);
                }
                else if (type == int.class || type == Integer.class || type == double.class || type == Double.class || type == long.class || type == Long.class) {
                    double val2 = MathMan.parseDouble(part2);
                    adapter = op.getNumberPredicate(val2);
                } else {
                    throw new IllegalArgumentException("Only the following filter types are supported: String, Number, Boolean");
                }

                return nation -> adapter.test(func.apply(nation));
            }
        }
        return null;
    }

    public Map.Entry<Type, Function<T, Object>> getPlaceholderFunction(ValueStore store, String input) {
        List<String> args;
        int argStart = input.indexOf('(');
        int argEnd = input.lastIndexOf(')');
        String cmd;
        if (argStart != -1 && argEnd != -1) {
            cmd = input.substring(0, argStart);
            String argsStr = input.substring(argStart + 1, argEnd);
            args = StringMan.split(argsStr, ',');
        } else if (input.indexOf(' ') != -1) {
            args = StringMan.split(input, ' ');
            cmd = args.get(0);
            args.remove(0);
        } else {
            args = Collections.emptyList();
            cmd = input;
        }

        ParametricCallable cmdObj = (ParametricCallable) commands.get(cmd);
        if (cmdObj == null) return null;

        LocalValueStore locals = new LocalValueStore<>(store);
        locals.addProvider(Key.of(instanceType, Me.class), nullInstance);
        ArgumentStack stack = new ArgumentStack(args, locals, validators, permisser);

        Object[] arguments = cmdObj.parseArguments(nullInstance, stack);
        int replacement = -1;
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] == nullInstance) replacement = i;
        }

        Function<T, Object> func;

        if (replacement != -1) {
            int valIndex = replacement;
            func = new Function<>() {
                private volatile boolean useCopy = false;

                @Override
                public Object apply(T val) {
                    // create copy if used by multiple threads
                    if (useCopy) {
                        Object[] copy = arguments.clone();
                        copy[valIndex] = val;
                        return cmdObj.call(val, store, copy);
                    } else {
                        try {
                            useCopy = true;
                            arguments[valIndex] = val;
                            return cmdObj.call(val, store, arguments);
                        } finally {
                            useCopy = false;
                        }
                    }
                }
            };
        } else {
            func = obj -> cmdObj.call(obj, store, arguments);
        }
        return new AbstractMap.SimpleEntry<>(cmdObj.getReturnType(), func);
    }
}
