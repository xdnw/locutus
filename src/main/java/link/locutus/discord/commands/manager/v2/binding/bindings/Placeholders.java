package link.locutus.discord.commands.manager.v2.binding.bindings;

import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.MethodParser;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.SimpleValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.*;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWMath2Type;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWType2Math;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.SelectionAlias;
import link.locutus.discord.db.entities.SheetTemplate;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.math.ReflectionUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class Placeholders<T> extends BindingHelper {
    private final ValidatorStore validators;
    private final PermissionHandler permisser;
    private final CommandGroup commands;
    private final Class<T> instanceType;
    private final ValueStore store;

    private final ValueStore math2Type;
    private final ValueStore type2Math;

    public Placeholders(Class<T> type, ValueStore store, ValidatorStore validators, PermissionHandler permisser) {
        this.instanceType = type;

        this.validators = validators;
        this.permisser = permisser;

        this.commands = CommandGroup.createRoot(store, validators);
        this.store = store;
        this.commands.registerCommandsClass(type);
//        this.commands.registerCommands(this);

        this.math2Type = new SimpleValueStore();
        this.type2Math = new SimpleValueStore();

        new PWMath2Type().register(math2Type);
        new PWType2Math().register(type2Math);
    }

    public Class<T> getType() {
        return instanceType;
    }

    protected static <T> String _addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<T> elems, String argumentName) {
        return _addSelectionAlias("", command, db, name, argumentName);
    }

    protected static <T> String _addSelectionAlias(String prefix, @Me JSONObject command, @Me GuildDB db, String name, String... argumentNames) {
        if (argumentNames.length == 0) {
            throw new IllegalArgumentException("No arguments provided");
        }
        // ensure name is alphanumeric_- and not too long
        if (!name.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Invalid name: `" + name + "` (must be alphanumeric_-)");
        }
        if (name.length() > 20) {
            throw new IllegalArgumentException("Name too long: `" + name + "` (max 20 chars)");
        }
        SelectionAlias<Continent> existing = db.getSheetManager().getSelectionAlias(name, Continent.class);
        if (existing != null) {
            throw new IllegalArgumentException("Selection already exists: " + existing.toString());
        }
        String selection;
        if (argumentNames.length == 1) {
            selection = command.getString(argumentNames[0]);
        } else {
            // copy of json object of only those keys
            JSONObject obj = new JSONObject();
            for (String arg : argumentNames) {
                Object value = command.optString(arg, null);
                if (value != null) {
                    obj.put(arg, value);
                }
            }
            selection = obj.toString();
        }
        db.getSheetManager().addSelectionAlias(name, Continent.class, selection);
        return "Added selection `" + name + "`: " + selection + ". Use it with `!" + name + "`";
    }

    protected static <T> String _addColumns(Placeholders<T> placeholders, @Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet, TypedFunction<T, String>... columns) {
        boolean created = false;
        if (sheet == null) {
            created = true;
            Set<String> names = db.getSheetManager().getSheetTemplateNames();
            for (int i = 0; ; i++) {
                String name = placeholders.getType().getSimpleName() + (i == 0 ? "" : "_" + i);
                if (!names.contains(name)) {
                    sheet = new SheetTemplate(name, placeholders.getType(), "*", new ArrayList<>());
                    break;
                }
            }
        } else if (!sheet.type.equals(placeholders.getType())) {
            throw new IllegalArgumentException("Sheet type mismatch: `" + sheet.type.getSimpleName() + "` != `" + placeholders.getType() + "`");
        }

        List<TypedFunction<T, String>> columnsNonNull = new ArrayList<>();
        for (TypedFunction<T, String> column : columns) {
            if (column != null) {
                columnsNonNull.add(column);
            }
        }
        for (TypedFunction<T, String> column : columnsNonNull) {
            sheet.columns.add(column.getName());
        }
        db.getSheetManager().addSheetTemplate(sheet);
        return (created ? "Created" : "Updated") + " sheet template: " + sheet;
    }

    private void registerCustom(Method method, Type type) {
        Binding binding = method.getAnnotation(Binding.class);
        MethodParser parser = new MethodParser(this, method, this.getDescription(), binding, type);
        Key key = parser.getKey();
        Parser existing = store.get(key);
        if (existing != null) {
            if (existing instanceof MethodParser<?> mp) {
                System.out.println("Existing: " + mp.getMethod().getDeclaringClass().getSimpleName() + " | " + mp.getMethod().getName());
            } else {
                System.out.println("Existing: " + key);
            }
            return;
        }
        System.out.println("Registering: " + key);
        store.addParser(key, parser);
    }

    public void register(ValueStore store) {
        try {
            Method methodSet = this.getClass().getMethod("parseSet", ValueStore.class, String.class);
            Method methodPredicate = this.getClass().getMethod("parseFilter", ValueStore.class, String.class);
            Method methodFormat = this.getClass().getMethod("getFormatFunction", ValueStore.class, String.class);
            registerCustom(methodSet, TypeToken.getParameterized(Set.class, this.instanceType).getType());
            registerCustom(methodPredicate, TypeToken.getParameterized(Predicate.class, this.instanceType).getType());
            registerCustom(methodFormat, TypeToken.getParameterized(TypedFunction.class, this.instanceType, String.class).getType());
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public Placeholders<T> register(Object obj) {
        this.commands.registerCommands(obj);
        return this;
    }

    public ValueStore getStore() {
        return store;
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
        CommandCallable result = commands.get(cmd);
        if (result == null) {
            for (String prefix : prefixes) {
                result = commands.get(prefix + cmd);
                if (result != null) {
                    break;
                }
            }
        }
        return (ParametricCallable) result;
    }

    public String getHtml(ValueStore store, String cmd, String parentId) {
        ParametricCallable callable = get(cmd);
        StringBuilder html = new StringBuilder(callable.toBasicHtml(store));
        html.append("<select name=\"filter-operator\" for=\"").append(parentId).append("\" class=\"form-control\">");
        for (Operation value : Operation.values()) {
            String selected = value == Operation.EQUAL ? "selected=\"selected\"" : "";
            html.append("<option value=\"").append(value.code).append("\" ").append(selected).append(">").append(StringEscapeUtils.escapeHtml4(value.code)).append("</option>");
        }
        html.append("</select>");
        Type type = callable.getReturnType();
        if (type == String.class) {
            html.append("<input name=\"filter-value\" for=\"").append(parentId).append("\" required type=\"text\" class=\"form-control\"/>");
        } else if (Enum.class.isAssignableFrom((Class<?>) type)) {
            html.append("<select name=\"filter-value\" for=\"").append(parentId).append("\" required class=\"form-control\">");
            for (Object o : ((Class<?>) type).getEnumConstants()) {
                html.append("<option value=\"").append(StringEscapeUtils.escapeHtml4(o.toString())).append("\">").append(StringEscapeUtils.escapeHtml4(o.toString())).append("</option>");
            }
            html.append("</select>");
            html.append(html);
        } else if (type == boolean.class || type == Boolean.class) {
            html.append("<select name=\"filter-value\" for=\"").append(parentId).append("\" required class=\"form-control\"><option>true</option><option>false</option></select>");
        } else if (type == int.class || type == Integer.class || type == double.class || type == Double.class || type == long.class || type == Long.class) {
            html.append("<input name=\"filter-value\" for=\"").append(parentId).append("\" required type=\"number\" class=\"form-control\"/>");
        } else {
            throw new IllegalArgumentException("Only the following filter types are supported: String, Number, Boolean.");
        }
        html.append("<button id=\"addfilter.submit\" for=\"").append(parentId).append("\" class=\"btn btn-primary\" >Add Filter</button>");

        return html.toString();
    }

    public List<CommandCallable> getFilterCallables() {
        List<ParametricCallable> result = getParametricCallables();
        result.removeIf(cmd -> {
            Type type = cmd.getReturnType();
            return type != String.class && type != boolean.class && type != Boolean.class && type != int.class && type != Integer.class && type != double.class && type != Double.class && type != long.class && type != Long.class && !Enum.class.isAssignableFrom(ReflectionUtil.getClassType(type));
        });
        return new ArrayList<>(result);
    }

    public abstract String getDescription();
    protected abstract Set<T> parseSingleElem(ValueStore store, String input);
    protected abstract Predicate<T> parseSingleFilter(ValueStore store, String input);

    public Set<T> deserializeSelection(ValueStore store, String input) {
        return parseSet(store, input);
    }

    private static Triple<String, Operation, String> opSplit(String input) {
        for (Operation op : Operation.values()) {
            List<String> split = StringMan.split(input, op.code, 2);
            if (split.size() == 2) {
                return Triple.of(split.get(0), op, split.get(1));
            }
        }
        return null;
    }

    private Predicate<T> getSingleFilter(ValueStore store, String input, Map<String, Map<T, Object>> cache) {
        Triple<String, Operation, String> pairs = opSplit(input);
        boolean isDefault = false;
        if (pairs == null) {
            pairs = Triple.of(input, Operation.EQUAL, "1");
            isDefault = true;
        }
        String part1 = pairs.getLeft();
        Operation op = pairs.getMiddle();
        String part2 = pairs.getRight();

        TypedFunction<T, ?> placeholder = evaluateFunction(store, part1, 0, true);
        if (placeholder == null) {
            throw throwUnknownCommand(part1);
        }
        Function<T, ?> func = placeholder;
        Type type = placeholder.getType();
        Predicate adapter;
        boolean toString;

        if (Enum.class.isAssignableFrom((Class<?>) type)) {
            if (isDefault) {
                throw new IllegalArgumentException("Please provide an operation for the filter: `" + part1 + "` e.g. " + StringMan.getString(Operation.values()));
            }
            adapter = op.getStringPredicate(part2);
            toString = true;
        } else {
            toString = false;
            if (type == String.class) {
                if (isDefault) {
                    part2 = "";
                    op = Operation.NOT_EQUAL;
                }
                adapter = op.getStringPredicate(part2);
            } else if (type == boolean.class || type == Boolean.class) {
                boolean val2 = PrimitiveBindings.Boolean(part2);
                adapter = op.getBooleanPredicate(val2);
            } else if (type == int.class || type == Integer.class || type == double.class || type == Double.class || type == long.class || type == Long.class) {
                if (isDefault) {
                    part2 = "0";
                    op = Operation.GREATER;
                }
                double val2 = PrimitiveBindings.Number(part2).doubleValue();
                adapter = op.getNumberPredicate(val2);
            } else {
                throw new IllegalArgumentException("Only the following filter types are supported: String, Number, Boolean, not: `" + ((Class<?>) type).getSimpleName() + "`");
            }
        }

        return nation -> {
            Object value;
            if (cache != null) {
                value = cache.computeIfAbsent(part1, k -> new Object2ObjectOpenHashMap<>()).computeIfAbsent(nation, k -> func.apply(nation));
            } else {
                value = func.apply(nation);
            }
            if (toString && value != null) {
                value = value.toString();
            }
            return adapter.test(value);
        };
    }

    @Binding(value = "A comma separated list of filters")
    public Predicate<T> parseFilter(ValueStore store2, String input) {
        Map<String, Map<T, Object>> cache = new Object2ObjectOpenHashMap<>();
        return ArrayUtil.parseFilter(input,
                f -> parseSingleFilter(store2, f),
                s -> getSingleFilter(store2, s, cache));
    }

    public Set<T> parseSet(Guild guild, User author, DBNation nation, String input) {
        return parseSet(createLocals(guild, author, nation), input);
    }

    @Binding(value = "A comma separated list of items")
    public Set<T> parseSet(ValueStore store2, String input) {
        Map<String, Map<T, Object>> cache = new Object2ObjectOpenHashMap<>();
        return ArrayUtil.resolveQuery(input,
                f -> parseSingleElem(store2, f),
                s -> getSingleFilter(store2, s, cache));
    }

    public TypedFunction<T, ?> formatRecursively(ValueStore store, String input, ParameterData param, int depth, boolean throwError) {
        input = input.trim();
        int currentIndex = 0;

        List<String> sections = new ArrayList<>();
        Map<String, TypedFunction<T, ?>> functions = new LinkedHashMap<>();

        boolean hasMath = false;
        boolean hasNonMath = false;
        boolean hasCurlyBracket = false;
        boolean hasPlaceholder = false;
        boolean hasNonPlaceholder = false;

        while (currentIndex < input.length()) {
            char currentChar = input.charAt(currentIndex);
            if (currentChar != '{') {
                sections.add("" +currentChar);
                currentIndex++;
                switch (currentChar) {
                    case '+', '-', '*', '/', '^', '%' -> {
                        hasMath = true;
                    }
                    case '(', ')', ' ' -> {

                    }
                    default -> {
                        hasNonMath = true;
                    }
                };
            } else {
                hasCurlyBracket = true;
                // Find the matching closing curly brace
                int closingBraceIndex = StringMan.findMatchingBracket(input, currentIndex);
                if (closingBraceIndex != -1) {
                    String fullContent = input.substring(currentIndex, closingBraceIndex + 1);
                    String functionContent = input.substring(currentIndex + 1, closingBraceIndex);
                    sections.add(fullContent);
                    if (!functions.containsKey(fullContent)) {
                        TypedFunction<T, ?> functionResult = evaluateFunction(store, functionContent, depth, throwError);
                        if (functionResult != null) {
                            functions.put(fullContent, functionResult);
                            hasPlaceholder = true;
                        } else {
                            hasNonPlaceholder = true;
                        }
                    }
                    currentIndex = closingBraceIndex + 1;
                } else {
                    if (throwError) {
                        throw new IllegalArgumentException("Invalid input: Missing closing curly brace: `" + input + "`");
                    }
//                    return ResolvedFunction.create(String.class, input);
                }
            }
        }

        if (hasMath && !hasNonMath) {
            if ((hasPlaceholder || (hasCurlyBracket && param != null)) && !hasNonPlaceholder) {
                Function<String, Function<T, ArrayUtil.DoubleArray>> stringToParser = s -> {
                    if (s.startsWith("{") && s.endsWith("}")) {
                        TypedFunction<T, ?> function = functions.get(s);
                        if (function != null) {
                            return function.andThen(o -> parseDoubleArray(o, param, true));
                        }
                    }
                    return ResolvedFunction.create(ArrayUtil.DoubleArray.class, parseDoubleArray(s, param, true), s);
                };

                ArrayUtil.LazyMathArray<T> lazy = ArrayUtil.calculate(input, new Function<String, ArrayUtil.LazyMathArray<T>>() {
                    @Override
                    public ArrayUtil.LazyMathArray<T> apply(String s) {
                        return new ArrayUtil.LazyMathArray<>(s, stringToParser);
                    }
                });

                ArrayUtil.DoubleArray array = lazy.getOrNull();
                Type type = param == null ? Double.class : param.getType();
                if (array != null) {
                    return TypedFunction.create(type, toObject(array, param), input);
                }
                return TypedFunction.create(type, f -> toObject(lazy.resolve(f), param), input);
            } else if (hasCurlyBracket) {
                if (throwError) {
                    throw new IllegalArgumentException("Invalid input: No functions found: `" + input + "`");
                } else {
//                    return null;
                }
            } else {
                double val = PrimitiveBindings.Double(input);
                return new ResolvedFunction<>(Double.class, val, input);
            }
        }
        if (param != null) {
            // if hasPlaceholder is false, then parse in entirety
            if (!hasPlaceholder) {
                Parser<?> binding = param.getBinding();
                LocalValueStore locals = new LocalValueStore<>(store);
                locals.addProvider(param);
                Object val = binding.apply(locals, input);
                return new ResolvedFunction<>(param.getType(), val,input);
            }
        }

        // else return function
        if (sections.size() == 1) {
            String section = sections.get(0);
            // get function
            TypedFunction<T, ?> function = functions.get(section);
            if (function != null) {
                return function;
            }
            return new ResolvedFunction<>(String.class, section, section);
        }
        boolean isResolved = functions.isEmpty() || functions.values().stream().allMatch(ResolvedFunction.class::isInstance);
        Function<T, Object> result = f -> {
            StringBuilder resultStr = new StringBuilder();
            for (String section : sections) {
                TypedFunction<T, ?> function = functions.get(section);
                if (function == null) {
                    resultStr.append(section);
                } else {
                    resultStr.append(function.apply(f));
                }
            }
            return resultStr.toString();
        };
        if (isResolved) {
            return new ResolvedFunction<>(String.class, result.apply(null), input);
        }
        return TypedFunction.create(String.class, result, input);
    }

    private Object toObject(ArrayUtil.DoubleArray expr, ParameterData param) {
        double[] arr = expr.toArray();
        if (arr.length == 1) {
            return arr[0];
        }
        if (param != null) {
            LocalValueStore locals = new LocalValueStore<>(store);
            locals.addProvider(param);
            Parser typeFunc = math2Type.get(param.getBinding().getKey());
            if (typeFunc == null) {
                throw new IllegalArgumentException("Unknown type function (1): `" + param.getBinding().getKey() + "`");
            }
            Object parsed = typeFunc.apply(locals, expr);
            if (parsed == null) {
                throw new IllegalArgumentException("Null parsed " + typeFunc.getKey() + " for Math Expression->Type");
            }
            return parsed;
        }
        throw new IllegalArgumentException("Cannot parse math expression to object: len:" + arr.length + " | " + StringMan.getString(arr));
    }

    private ArrayUtil.DoubleArray parseDoubleArray(Object s, ParameterData param, boolean throwForUnknown) {
        if (s instanceof Number n) {
            return new ArrayUtil.DoubleArray(n.doubleValue());
        }
        if (s instanceof String str) {
            if (NumberUtils.isParsable(str)) {
                return new ArrayUtil.DoubleArray(NumberUtils.createDouble(str));
            }
            if (str.startsWith("{") && str.endsWith("}")) {
                if (param != null) {
                    LocalValueStore locals = new LocalValueStore<>(store);
                    locals.addProvider(param);
                    Parser mathFunc = type2Math.get(param.getBinding().getKey());
                    if (mathFunc == null) {
                        throw new IllegalArgumentException("Unknown function (2): `" + str + "`");
                    }
                    Object parsed = mathFunc.apply(locals, validators, permisser, str);
                    if (parsed == null) {
                        throw new IllegalArgumentException("Null parsed " + mathFunc.getKey() + " for Type->Math Expression");
                    }
                    if (parsed instanceof ArrayUtil.DoubleArray da) {
                        return da;
                    }
                    throw new IllegalArgumentException("Cannot parse `" + mathFunc.getKey() + "` invalid type: " + parsed.getClass() + " (expected DoubleArray)");
                }
            }
            if (throwForUnknown) {
                throw new IllegalArgumentException("Unknown numeric `" + str + "` cannot parse to DoubleArray for " + param);
            }
        }
        throw new IllegalArgumentException("Unknown numeric `" + s.toString() + "` of type " + s.getClass() + " cannot parse to DoubleArray for " + param);
    }

    private Map<String, String> explodeArguments(ValueStore store, ParametricCallable command, String argumentString, Set<String> arguments) {
        try {
            return CommandManager2.parseArguments(arguments, argumentString, false);
        } catch (IllegalArgumentException e) {
            List<String> input = StringMan.split(argumentString, ",");
            return command.formatArgumentsToMap(store, input);
        }
    }

    private IllegalArgumentException throwUnknownCommand(String command) {
        Set<String> options = commands.getSubCommandIds();
        List<String> closest = StringMan.getClosest(command, new ArrayList<>(options), false);
        if (closest.size() > 5) closest = closest.subList(0, 5);
        return new IllegalArgumentException("Unknown command (4): " + command + "\n" +
                "Did you mean:\n- " + StringMan.join(closest, "\n- ") +
                "\n\nSee also: " + getDescription());
    }

    // Helper method to evaluate a function and its arguments
    private TypedFunction<T, ?> evaluateFunction(ValueStore store, String functionContent, int depth, boolean throwError) {
        Map<String, TypedFunction<T, ?>> actualArguments = new HashMap<>();

        // Split the functionContent into function name and arguments
        int indexPar = functionContent.indexOf('(');
        int indexSpace = functionContent.indexOf(' ');
        String functionName;
        String argumentString;
        if (indexPar != -1 && (indexSpace == -1 || indexPar < indexSpace)) {
            if (!functionContent.endsWith(")")) {
                if (throwError) {
                    throw new IllegalArgumentException("Invalid input: Missing closing parenthesis for `" + functionContent + "`");
                }
                return null;
            }
            functionName = functionContent.substring(0, indexPar);
            argumentString = functionContent.substring(indexPar + 1, functionContent.length() - 1).trim();
        } else if (indexSpace != -1 && (indexPar == -1 || indexSpace < indexPar)) {
            functionName = functionContent.substring(0, indexSpace).trim();
            argumentString = functionContent.substring(indexSpace + 1).trim();
        } else {
            functionName = functionContent.trim();
            argumentString = "";
        }
        ParametricCallable command = this.get(functionName);
        if (command == null) {
            if (throwError) {
                throw throwUnknownCommand(functionName);
            }
            return null;
        }
        if (!argumentString.isEmpty()) {
            Set<String> argumentNames = command.getUserParameterMap().keySet();
            // Use explodeArguments to parse the argument string
            Map<String, String> explodedArguments = explodeArguments(store, command, argumentString, argumentNames);

            for (Map.Entry<String, ParameterData> entry : command.getUserParameterMap().entrySet()) {
                ParameterData param = entry.getValue();
                String argumentName = entry.getKey();
                if (explodedArguments.containsKey(argumentName)) {
                    TypedFunction<T, ?> argumentValue = formatRecursively(store, explodedArguments.get(argumentName), param, depth + 1, throwError);
                    actualArguments.put(argumentName, argumentValue);
                }
            }
        }

        return format(store, command, actualArguments);
    }

    private TypedFunction<T, ?> format(ValueStore store, ParametricCallable command, Map<String, TypedFunction<T, ?>> arguments) {
        Map<String, Object> resolvedArgs = new LinkedHashMap<>();
        boolean isResolved = true;
        for (Map.Entry<String, TypedFunction<T, ?>> entry : arguments.entrySet()) {
            TypedFunction<T, ?> func = entry.getValue();
            if (func instanceof ResolvedFunction f) {
                resolvedArgs.put(entry.getKey(), f.get());
                continue;
            }
            isResolved = false;
        }

        boolean finalIsResolved = isResolved;
        Function<T, Object[]> resolved = f -> {
            if (!finalIsResolved) {
                for (Map.Entry<String, TypedFunction<T, ?>> entry : arguments.entrySet()) {
                    String argName = entry.getKey();
                    if (!resolvedArgs.containsKey(argName)) {
                        resolvedArgs.put(argName, entry.getValue().apply(f));
                    }
                }
            }
            return command.parseArgumentMap2(resolvedArgs, store, validators, permisser, true);
        };
        StringBuilder full = new StringBuilder(command.getPrimaryCommandId());
        for (Map.Entry<String, TypedFunction<T, ?>> entry : arguments.entrySet()) {
            full.append(" ").append(entry.getKey()).append(" ").append(entry.getValue());
        }

        BiFunction<T, Object[], Object> format = (object, paramVals) -> command.call(object, store, paramVals);
        if (isResolved) {
            Object[] argArr = resolved.apply(null);
            return TypedFunction.create(command.getReturnType(), f -> format.apply(f, argArr), full.toString());
        }
        return TypedFunction.create(command.getReturnType(), f -> format.apply(f, resolved.apply(f)), full.toString());
    }

    public abstract String getName(T o);

    public static class PlaceholderCache<T> {
        private final List<T> list;
        private boolean cached = false;
//        private final Map<T, Map<String, Object>> cache = new Object2ObjectOpenHashMap<>();
        private final Map<String, Map<T, Object>> cache2 = new Object2ObjectOpenHashMap<>();

        public PlaceholderCache(Collection<T> set) {
            this.list = new ObjectArrayList<>(new ObjectOpenHashSet<>(set));
        }

        public List<T> getList() {
            return list;
        }

        public static <V, T> V get(PlaceholderCache<T> cache, T obj, Supplier<String> key, Function<List<T>, List<V>> getAll) {
            return get(cache, obj, key, getAll, f -> getAll.apply(Collections.singletonList(f)).get(0));
        }

        public static <V, T> V get(PlaceholderCache<T> cache, T obj, Supplier<String> key, Function<List<T>, List<V>> getAll, Function<T, V> getSingle) {
            if (cache == null) {
                return getSingle.apply(obj);
            }
            Map<T, Object> map = cache.cache2.computeIfAbsent(key.get(), o -> new Object2ObjectOpenHashMap<>());
            if (map.containsKey(obj)) {
                return (V) map.get(obj);
            }
            if (!cache.cached && cache.list != null && !cache.list.isEmpty()) {
                cache.cached = true;
                if (cache.list.size() == 1) {
                    T first = cache.list.get(0);
                    map.put(first, getSingle.apply(first));
                } else {
                    List<V> list = getAll.apply(cache.list);
                    for (int i = 0; i < cache.list.size(); i++) {
                        map.put(cache.list.get(i), list.get(i));
                    }
                }
            }
            return (V) map.computeIfAbsent(obj, getSingle);
        }

        public Object get(T object, String id) {
            Map<T, Object> map = cache2.computeIfAbsent(id, o -> new Object2ObjectOpenHashMap<>());
            return map.get(object);
        }

        public boolean has(T object, String id) {
            Map<T, Object> map = cache2.get(id);
            return map != null && map.containsKey(object);
        }

        public void put(T object, String id, Object value) {
            Map<T, Object> map = cache2.computeIfAbsent(id, o -> new Object2ObjectOpenHashMap<>());
            map.put(object, value);
        }
    }

    public LocalValueStore createLocals(Guild guild, User user, DBNation nation) {
        return createLocals(guild, user, nation, null);
    }

    public LocalValueStore createLocals(Guild guild, User user, DBNation nation, PlaceholderCache cache) {
        if (nation == null && user != null) {
            nation = DBNation.getByUser(user);
        }
        if (user == null && nation != null) {
            user = nation.getUser();
        }
        LocalValueStore locals = new LocalValueStore<>(this.getStore());
        if (nation != null) {
            locals.addProvider(Key.of(DBNation.class, Me.class), nation);
        }
        if (user != null) {
            locals.addProvider(Key.of(User.class, Me.class), user);
        }
        if (guild != null) {
            locals.addProvider(Key.of(Guild.class, Me.class), guild);
        }
        if (cache != null) {
            locals.addProvider(Key.of(PlaceholderCache.class, getType()), cache);
        }
        return locals;
    }

    public String format2(Guild callerGuild, DBNation callerNation, User callerUser, String arg, T elem, boolean throwError) {
        LocalValueStore locals = createLocals(callerGuild, callerUser, callerNation);
        return getFormatFunction(locals, arg, throwError).apply(elem);
    }

    public String format2(ValueStore store, String arg, T elem, boolean throwError) {
        return getFormatFunction(store, arg, throwError).apply(elem);
    }


    public  Map<T, String> format(Guild callerGuild, DBNation callerNation, User callerUser, String arg, Set<T> elems) {
        LocalValueStore locals = createLocals(callerGuild, callerUser, callerNation);
        return format(locals, arg, elems);
    }

    public Map<T, String> format(ValueStore store, String arg, Set<T> entities) {
        PlaceholderCache<T> cache = new PlaceholderCache<>(entities);
        Function<T, String> func = getFormatFunction(store, arg, cache, true);
        Map<T, String> formatted = new Object2ObjectOpenHashMap<>();
        for (T entity : entities) {
            formatted.put(entity, func.apply(entity));
        }
        return formatted;
    }

    public Function<T, String> getFormatFunction(Guild callerGuild, DBNation callerNation, User callerUser, String arg, Set<T> elems) {
        PlaceholderCache<T> cache = new PlaceholderCache<>(elems);
        return getFormatFunction(callerGuild, callerNation, callerUser, arg, cache, true);
    }
    public Function<T, String> getFormatFunction(Guild callerGuild, DBNation callerNation, User callerUser, String arg, PlaceholderCache cache, boolean throwError) {
        LocalValueStore locals = createLocals(callerGuild, callerUser, callerNation, cache);
        return getFormatFunction(locals, arg, throwError);
    }

    @Binding(value = "Format text containing placeholders")
    public Function<T, String> getFormatFunction(ValueStore store, String arg) {
        return getFormatFunction(store, arg, true);
    }

    public Function<T, String> getFormatFunction(ValueStore store, String arg, boolean throwError) {
        return getFormatFunction(store, arg, null, throwError);
    }

    public Function<T, String> getFormatFunction(ValueStore store, String arg, PlaceholderCache cache, boolean throwError) {
        if (cache != null) store.addProvider(cache);
        return this.formatRecursively(store, arg, null, 0, throwError).andThen(f -> f + "");
    }

    private static String[] prefixes = {"get", "is", "can", "has"};

//    public Map.Entry<Type, Function<T, Object>> getTypeFunction(ValueStore<?> store, String id, boolean ignorePerms) {
//        Map.Entry<Type, Function<T, Object>> typeFunction;
//        try {
//            typeFunction = getPlaceholderFunction(store, id);
//        } catch (CommandUsageException ignore) {
//            return null;
//        } catch (Exception ignore2) {
//            if (!ignorePerms) throw ignore2;
//            return null;
//        }
//        return typeFunction;
//    }
//    public String getCmd(String input) {
//        int argStart = input.indexOf('(');
//        return argStart != -1 ? input.substring(0, argStart) : input;
//    }
//
//    public Map.Entry<Type, Function<T, Object>> getPlaceholderFunction(ValueStore store, String input) {
//        List<String> args;
//        int argStart = input.indexOf('(');
//        int argEnd = input.lastIndexOf(')');
//        String cmd;
//        if (argStart != -1 && argEnd != -1) {
//            cmd = input.substring(0, argStart);
//            String argsStr = input.substring(argStart + 1, argEnd);
//            args = StringMan.split(argsStr, ',');
//        } else if (input.indexOf(' ') != -1) {
//            args = StringMan.split(input, ' ');
//            cmd = args.get(0);
//            args.remove(0);
//        } else {
//            args = Collections.emptyList();
//            cmd = input;
//        }
//
//        ParametricCallable cmdObj = get(cmd);
//        if (cmdObj == null) {
//            return null;
//        }
//
//        LocalValueStore locals = new LocalValueStore<>(store);
////        locals.addProvider(Key.of(instanceType, Me.class), nullInstance);
//        ArgumentStack stack = new ArgumentStack(args, locals, validators, permisser);
//
//        Object[] arguments = cmdObj.parseArguments(stack);
//
//        Function<T, Object> func;
//        ParametricCallable finalCmdObj = cmdObj;
//        func = obj -> finalCmdObj.call(obj, store, arguments);
//        return new AbstractMap.SimpleEntry<>(cmdObj.getReturnType(), func);
//    }

    public CommandGroup getCommands() {
        return commands;
    }

    public PermissionHandler getPermisser() {
        return permisser;
    }

    public ValidatorStore getValidators() {
        return validators;
    }
}