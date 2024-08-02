package link.locutus.discord.commands.manager.v2.binding.bindings;

import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.MethodParser;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.*;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.DefaultPlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.SelectionAlias;
import link.locutus.discord.db.entities.SheetTemplate;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.math.LazyMathEntity;
import link.locutus.discord.util.math.ReflectionUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class Placeholders<T> extends BindingHelper {
    private final ValidatorStore validators;
    private final PermissionHandler permisser;
    private final CommandGroup commands;
    private final Class<T> instanceType;
    private final ValueStore store;

//    private final ValueStore math2Type;
//    private final ValueStore type2Math;

    public Placeholders(Class<T> type, ValueStore store, ValidatorStore validators, PermissionHandler permisser) {
        this.instanceType = type;

        this.validators = validators;
        this.permisser = permisser;

        this.commands = CommandGroup.createRoot(store, validators);
        this.store = store;
//        this.math2Type = new SimpleValueStore();
//        this.type2Math = new SimpleValueStore();
        this.getCommands().registerCommands(new DefaultPlaceholders());
    }

    public Placeholders<T> init() {
        this.commands.registerCommandsClass(getType());
//        new PWMath2Type().register(math2Type);
//        new PWType2Math().register(type2Math);
        return this;
    }

    public Class<T> getType() {
        return instanceType;
    }

    protected static <T> String _addSelectionAlias(Placeholders<T> instance, @Me JSONObject command, @Me GuildDB db, String name, Set<T> elems, String argumentName) {
        return _addSelectionAlias(instance, "", command, db, name, argumentName);
    }

    protected static <T> String _addSelectionAlias(Placeholders<T> instance, String prefix, @Me JSONObject command, @Me GuildDB db, String name, String... argumentNames) {
        name = name.toLowerCase(Locale.ROOT);
        if (argumentNames.length == 0) {
            throw new IllegalArgumentException("No arguments provided");
        }
        // ensure name is alphanumeric_- and not too long
        if (!name.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Invalid name: `" + name + "` (must be alphanumeric_-)");
        }
        // cannot start with number
        if (name.matches("[0-9]+")) {
            throw new IllegalArgumentException("Invalid name: `" + name + "` (cannot be a number)");
        }
        if (name.length() > 20) {
            throw new IllegalArgumentException("Name too long: `" + name + "` (max 20 chars)");
        }
        SelectionAlias<T> existing = db.getSheetManager().getSelectionAlias(name, false);
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
        if (prefix != null && !prefix.isEmpty()) {
            selection = prefix + selection;
        }
        if (selection.toLowerCase(Locale.ROOT).contains(name)) {
            throw new IllegalArgumentException("Selection cannot reference itself: `" + selection + "`");
        }
        db.getSheetManager().addSelectionAlias(name, instance.getType(), selection);
        return "Added selection `" + name + "`: `" + selection + "`. Use it with `$" + name + "` or `select:" + name + "`\n" +
                "- Rename: " + CM.selection_alias.rename.cmd.toSlashMention() + "\n" +
                "- Remove: " + CM.selection_alias.remove.cmd.toSlashMention() + "\n" +
                "- View: " + CM.selection_alias.list.cmd.toSlashMention();
    }

    public SheetTemplate getOrCreateTemplate(GuildDB db, List<String> columns, boolean save, AtomicBoolean createdFlag) {
        outer:
        for (Map.Entry<String, SheetTemplate> entry : db.getSheetManager().getSheetTemplates(getType()).entrySet()) {
            SheetTemplate template = entry.getValue();
            List<String> existing = template.getColumns();
            // check if existing equals ignorecase columns
            if (existing.size() == columns.size()) {
                for (int i = 0; i < existing.size(); i++) {
                    if (!existing.get(i).equalsIgnoreCase(columns.get(i))) {
                        continue outer;
                    }
                }
                return template;
            }
        }
        SheetTemplate template = new SheetTemplate(getNextTemplateName(db), getType(), columns);
        if (save) {
            db.getSheetManager().addSheetTemplate(template);
        }
        createdFlag.set(true);
        return template;
    }

    public SelectionAlias getOrCreateSelection(GuildDB db, String selection, boolean save, AtomicBoolean createdFlag) {
        outer:
        for (Map.Entry<String, SelectionAlias<T>> entry : db.getSheetManager().getSelectionAliases(getType()).entrySet()) {
            SelectionAlias alias = entry.getValue();
            if (alias.getSelection().equalsIgnoreCase(selection)) {
                return alias;
            }
        }
        String name = getNextSelectionName(db);
        SelectionAlias result;
        if (save) {
            result = db.getSheetManager().addSelectionAlias(name, getType(), selection);
        } else {
            result = new SelectionAlias(name, getType(), selection);
        }
        createdFlag.set(true);
        return result;
    }

    private String getNextTemplateName(GuildDB db) {
        Set<String> names = db.getSheetManager().getSheetTemplateNames(false);
        String prefix = PlaceholdersMap.getClassName(getType());
        for (int i = 0; ; i++) {
            String name = prefix + (i == 0 ? "" : "_" + i);
            if (!names.contains(name)) {
                return name;
            }
        }
    }

    private String getNextSelectionName(GuildDB db) {
        Set<String> names = db.getSheetManager().getSelectionAliasNames();
        String prefix = PlaceholdersMap.getClassName(getType());
        for (int i = 0; ; i++) {
            String name = prefix + (i == 0 ? "" : "_" + i);
            if (!names.contains(name)) {
                return name;
            }
        }
    }

    protected static <T> String _addColumns(Placeholders<T> placeholders, @Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet, TypedFunction<T, String>... columns) {
        boolean created = false;
        if (sheet == null) {
            created = true;
            String name = placeholders.getNextTemplateName(db);
            sheet = new SheetTemplate(name, placeholders.getType(), new ArrayList<>());
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
            System.out.println("Add `" + column.getName() + "` | " + column.toString());
            sheet.columns.add(column.getName());
        }
        db.getSheetManager().addSheetTemplate(sheet);
        return (created ? "Created" : "Updated") + " sheet template named: " + sheet + "\n\n" +
                "Rename via TODO CM REF\n" +
                "Add more columns via TODO CM REF\n" +
                "Remove columns via";
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
            Method methodDouble = this.getClass().getMethod("getDoubleFunction", ValueStore.class, String.class);
            registerCustom(methodSet, TypeToken.getParameterized(Set.class, this.instanceType).getType());
            registerCustom(methodPredicate, TypeToken.getParameterized(Predicate.class, this.instanceType).getType());
            registerCustom(methodFormat, TypeToken.getParameterized(TypedFunction.class, this.instanceType, String.class).getType());
            registerCustom(methodDouble, TypeToken.getParameterized(TypedFunction.class, this.instanceType, Double.class).getType());
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
        for (MathOperation value : MathOperation.values()) {
            String selected = value == MathOperation.EQUAL ? "selected=\"selected\"" : "";
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

    private static Triple<String, MathOperation, String> opSplit(String input) {
        for (MathOperation op : MathOperation.values()) {
            List<String> split = StringMan.split(input, op.code, 2);
            if (split.size() == 2) {
                return Triple.of(split.get(0), op, split.get(1));
            }
        }
        return null;
    }

    private Predicate<T> getSingleFilter(ValueStore store, String input) {
        TypedFunction<T, ?> placeholder = formatRecursively(store, input, null, 0, false, true);
        if (placeholder == null) {
            throw throwUnknownCommand(input);
        }
        Function<T, ?> func = placeholder;
        Type type = placeholder.getType();
        Predicate<T> adapter;
        if (type == String.class) {
            throw new IllegalArgumentException("Cannot use String as filter: `" + input + "`. Please provide a condition e.g. `" + input + "=blah`");
        } else if (type == boolean.class || type == Boolean.class) {
            adapter = f -> {
                if (f == null) return false;
                return (Boolean) f;
            };
        } else if (type == byte.class || type == Byte.class || type == short.class || type == Short.class || type == int.class || type == Integer.class || type == long.class || type == Long.class || type == float.class || type == Float.class || type == double.class || type == Double.class || type == Number.class) {
            adapter = f -> {
                if (f == null) return false;
                return ((Number) f).doubleValue() > 0;
            };
        } else {
            throw new IllegalArgumentException("Only the following filter types are supported: String, Number, Boolean, not: `" + ((Class<?>) type).getSimpleName() + "`");
        }
        return f -> {
            Object value = func.apply(f);
            if (value instanceof String) return false;
            return adapter.test((T) value);
        };
    }

    private String wrapHashLegacy(String input) {
        return StringMan.wrapHashFunctions(input, new Predicate<String>() {
            @Override
            public boolean test(String s) {
                int dot = s.indexOf('.');
                String firstMethod = dot == -1 ? s : s.substring(0, dot);
                return get(firstMethod) != null || firstMethod.equalsIgnoreCase("this");
            }
        });
    }

    @Binding(value = "A comma separated list of filters")
    public Predicate<T> parseFilter(ValueStore store2, String input) {
        input = wrapHashLegacy(input);
        return ArrayUtil.parseFilter(input,
                f -> parseSingleFilter(store2, f),
                s -> getSingleFilter(store2, s));
    }

    public Set<T> parseSet(Guild guild, User author, DBNation nation, String input) {
        return parseSet(createLocals(guild, author, nation), input);
    }

    @Binding(value = "A comma separated list of items")
    public Set<T> parseSet(ValueStore store2, String input) {
        input = wrapHashLegacy(input);
        System.out.println("Parsing 1");
        new Exception().printStackTrace();
        return ArrayUtil.resolveQuery(input,
                f -> parseSingleElem(store2, f),
                s -> getSingleFilter(store2, s));
    }

    public TypedFunction<T, ?> formatRecursively(ValueStore store, String input, ParameterData param, int depth, boolean skipFormat, boolean throwError) {
        input = input.trim();
        int currentIndex = 0;

        List<String> sections = new ArrayList<>();
        Map<String, TypedFunction<T, ?>> functions = new LinkedHashMap<>();

        boolean check = !skipFormat && (param == null || param.getAnnotation(NoFormat.class) == null);
        boolean hasMath = false;
        boolean hasNonMath = false;
        boolean hasCurlyBracket = false;
        boolean hasPlaceholder = false;
        boolean hasNonPlaceholder = false;

        if (check) {
            while (currentIndex < input.length()) {
                char currentChar = input.charAt(currentIndex);
                if (currentChar != '{') {
                    sections.add("" + currentChar);
                    currentIndex++;
                    switch (currentChar) {
                        case '+', '-', '*', '/', '^', '%', '=', '>', '<', '?' -> {
                            hasMath = true;
                        }
                        case '(', ')', ' ', '!', 'e', 'E', '.', ':' -> {

                        }
                        default -> {
                            if (!Character.isDigit(currentChar)) {
                                hasNonMath = true;
                            }
                        }
                    }
                    ;
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
                        currentIndex++;
                    }
                }
            }
        }

        String errorMsg = null;
        if (hasMath) {
            try {
                if ((hasPlaceholder || (hasCurlyBracket && param != null)) && !hasNonPlaceholder) {
                    Function<String, Function<T, Object>> stringToParser = s -> {
                        if (s.startsWith("{") && s.endsWith("}")) {
                            TypedFunction<T, ?> function = functions.get(s);
                            if (function != null) {
                                return function.andThen(o -> parseMath(o, param, true));
                            }
                        }
                        Object parsed = parseMath(s, param, true);
                        return ResolvedFunction.createConstant(parsed != null ? parsed.getClass() : Object.class, parsed, s);
                    };
                    List<LazyMathEntity<T>> lazies = ArrayUtil.calculate(input, s -> new LazyMathEntity<>(s, stringToParser, false));
                    if (lazies.size() == 1) {
                        LazyMathEntity<T> lazy = lazies.get(0);
                        Object array = lazy.getOrNull();
                        Class type = param == null ? Double.class : (Class) param.getType();
                        if (array != null) {
                            return TypedFunction.createConstant(type, toObject(array, type, param), input);
                        }
                        return TypedFunction.createParents(type, f -> {
                            return toObject(lazy.resolve(f), type, param);
                        }, input, functions.values());
                    }
                } else if (!hasNonMath) {
                    if (hasCurlyBracket) {
                        if (throwError) {
                            throw new IllegalArgumentException("Invalid input: No functions found: `" + input + "`");
                        }
                    } else {
                        double val = PrimitiveBindings.Double(input);
                        return new ResolvedFunction<>(Double.class, val, input);
                    }
                }
            } catch (RuntimeException e) {
                errorMsg = e.getMessage();
            }
        }
        if (param != null) {
            // if hasPlaceholder is false, then parse in entirety
            if (!hasPlaceholder) {
                Parser<?> binding = param.getBinding();
                LocalValueStore locals = new LocalValueStore<>(store);
                locals.addProvider(param);
                Object val = binding.apply(locals, input);
                return new ResolvedFunction<>(param.getType(), val, input);
            }
        }

        // else return function
        if (sections.size() == 1) {
            String section = sections.get(0);
            // get function
            TypedFunction<T, ?> function = functions.get(section);
            if (function != null) {
                System.out.println("Return function 1:" + function.getName() + " | " + function.getType());
                return function;
            }
            System.out.println("Return non function section " + section);
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
                    resultStr.append(function.applyCached(f));
                }
            }
            return resultStr.toString();
        };
        if (isResolved) {
            return new ResolvedFunction<>(String.class, result.apply(null), input);
        }
        return TypedFunction.createParents(String.class, result, input, functions.values());
    }

    private Object toObject(Object expr, Class type, ParameterData param) {
        if (type.isAssignableFrom(expr.getClass())) {
            return expr;
        }
        if (expr instanceof Number num) {
            if (type == int.class || type == Integer.class) {
                return num.intValue();
            } else if (type == double.class || type == Double.class) {
                return num.doubleValue();
            } else if (type == long.class || type == Long.class) {
                return num.longValue();
            } else if (type == float.class || type == Float.class) {
                return num.floatValue();
            } else if (type == short.class || type == Short.class) {
                return num.shortValue();
            } else if (type == byte.class || type == Byte.class) {
                return num.byteValue();
            }
        }
        if (param != null) {
            LocalValueStore locals = new LocalValueStore<>(store);
            locals.addProvider(param);
            Parser typeFunc = store.get(param.getBinding().getKey());
            if (typeFunc == null) {
                throw new IllegalArgumentException("Unknown type function (1): `" + param.getBinding().getKey() + "`");
            }
            Object parsed = typeFunc.apply(locals, expr.toString());
            if (parsed == null) {
                throw new IllegalArgumentException("Null parsed " + typeFunc.getKey() + " for Math Expression->Type");
            }
            return parsed;
        }
        return expr;
    }

    private Object parseMath(Object s, ParameterData param, boolean throwForUnknown) {
        if (s == null) {
            return null;
        }
        if (s instanceof Number n) {
            return n;
        }
        if (param != null && ((Class) param.getType()).isAssignableFrom(s.getClass())) {
            return s;
        }
        if (s instanceof String str) {
            if (NumberUtils.isParsable(str)) {
                return NumberUtils.createDouble(str);
            }
            if (str.startsWith("{") && str.endsWith("}")) {
                if (param != null) {
                    LocalValueStore locals = new LocalValueStore<>(store);
                    locals.addProvider(param);
                    Parser mathFunc = store.get(param.getBinding().getKey());
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
                if (throwForUnknown) {
                    throw new IllegalArgumentException("Unknown numeric `" + str + "` cannot parse to DoubleArray for " + param);
                }
            }
            return str;
        } else if (s instanceof double[]) {
            return s;
        } else if (s instanceof int[]) {
            throw new IllegalArgumentException("Cannot parse int[] to DoubleArray for " + param);
        } else if (s instanceof ArrayUtil.DoubleArray) {
            return s;
        } else {
            return s;
        }
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
        TypedFunction<T, ?> previousFunc = null;
        if (functionContent.equalsIgnoreCase("this")) {
            return TypedFunction.createParents(getType(), Function.identity(), "this", null);
        }
        List<String> split = StringMan.split(functionContent, ".");
        if (split.isEmpty()) {
            throw new IllegalArgumentException("Invalid input: Empty function: `" + functionContent + "`");
        }
        for (int i = 0; i < split.size(); i++) {
            Map<String, TypedFunction<T, ?>> actualArguments = new HashMap<>();
            String arg = split.get(i);
            int indexPar = arg.indexOf('(');
            int indexSpace = arg.indexOf(' ');
            String functionName;
            String argumentString;
            if (indexPar != -1 && (indexSpace == -1 || indexPar < indexSpace)) {
                if (!arg.endsWith(")")) {
                    if (throwError) {
                        throw new IllegalArgumentException("Invalid input: Missing closing parenthesis for `" + arg + "`");
                    }
                    return null;
                }
                functionName = arg.substring(0, indexPar);
                argumentString = arg.substring(indexPar + 1, arg.length() - 1).trim();
            } else if (indexSpace != -1 && (indexPar == -1 || indexSpace < indexPar)) {
                functionName = arg.substring(0, indexSpace).trim();
                argumentString = arg.substring(indexSpace + 1).trim();
            } else {
                functionName = arg.trim();
                argumentString = "";
            }
            Placeholders<T> placeholders = this;
            if (previousFunc != null) {
                Type type = previousFunc.getType();
                if (type instanceof Class) {
                    placeholders = Locutus.cmd().getV2().getPlaceholders().get((Class) type);
                } else {
                    TypedFunction<T, ?> result = handleParameterized(store, functionName, argumentString, previousFunc, depth, throwError);
                    if (result != null) {
                        previousFunc = result;
                        continue;
                    }
                }
                if (placeholders == null) {
                    throw new IllegalArgumentException("Cannot call `" + arg + "` on function: `" + previousFunc.getName() + "` as return type is not public: `" + ((Class<?>) previousFunc.getType()).getSimpleName() + "`");
                }
            }
            ParametricCallable command = placeholders.get(functionName);
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
                        TypedFunction<T, ?> argumentValue = formatRecursively(store, explodedArguments.get(argumentName), param, depth + 1, false, throwError);
                        actualArguments.put(argumentName, argumentValue);
                    }
                }
            }
            TypedFunction function = format(store, command, actualArguments);
            if (previousFunc == null) {
                previousFunc = function;
            } else if (function.isResolved()){
                previousFunc = ResolvedFunction.createConstant(function.getType(), function.applyCached(null), functionContent);
            } else if (previousFunc.isResolved()) {
                Object value = previousFunc.applyCached(null);
                previousFunc = ResolvedFunction.createConstant(function.getType(), function.applyCached(value), functionContent);
            } else {
                previousFunc = TypedFunction.createParent(function.getType(), previousFunc.andThen(function), functionContent, previousFunc);
            }
        }
        return previousFunc;
    }

    private TypedFunction<T, ?> handleParameterized(ValueStore store, String functionName, String argumentString, TypedFunction<T, ?> previousFunc, int depth, boolean throwError) {
        if (argumentString != null && !argumentString.isEmpty()) return null;
        Type type = previousFunc.getType();
        if (!(type instanceof ParameterizedType pt)) return null;
        Type rawType = pt.getRawType();
        if (!(rawType instanceof Class rawClass)) return null;
//            if (Map.class.isAssignableFrom(rawClass)) {
        if (!rawClass.isAssignableFrom(Map.class)) return null;
        Type[] args = pt.getActualTypeArguments();
        Type keyType = args[0];
        Type valueType = args[1];
        if (!(keyType instanceof Class keyClass && keyClass.isEnum())) return null;
        // get enum options
        Enum<?>[] enumConstants = (Enum<?>[]) keyClass.getEnumConstants();
        // if any enum constant equals ignore case
        for (Enum<?> enumConstant : enumConstants) {
            if (enumConstant.name().equalsIgnoreCase(functionName)) {
                Function<T, Object> getValue = f -> {
                    Object key = enumConstant;
                    Map<T, ?> map = (Map<T, ?>) previousFunc.applyCached(f);
                    return map.get(key);
                };
                return TypedFunction.createParent(valueType, getValue, functionName, previousFunc);
            }
        }
        return null;
    }

    private TypedFunction<T, ?> format(ValueStore store, ParametricCallable command, Map<String, TypedFunction<T, ?>> arguments) {
        Map<String, Object> resolvedArgs = new Object2ObjectLinkedOpenHashMap<>();
        Map<T, Map<String, Object>> resolvedByEntity = new Object2ObjectLinkedOpenHashMap<>();
        boolean isResolved = true;
        for (Map.Entry<String, TypedFunction<T, ?>> entry : arguments.entrySet()) {
            TypedFunction<T, ?> func = entry.getValue();
            if (func.isResolved()) {
                resolvedArgs.put(entry.getKey(), func.get(null));
                continue;
            }
            isResolved = false;
        }

        boolean finalIsResolved = isResolved;
        System.out.println("Pre parse 2");
        Function<T, Object[]> resolved = f -> {
            Map<String, Object> finalArgs;
            if (!finalIsResolved) {
                finalArgs = resolvedByEntity.get(f);
                if (finalArgs == null) {
                    finalArgs = new Object2ObjectLinkedOpenHashMap<>(resolvedArgs);
                    resolvedByEntity.put(f, finalArgs);
                    for (Map.Entry<String, TypedFunction<T, ?>> entry : arguments.entrySet()) {
                        String argName = entry.getKey();
                        if (!finalArgs.containsKey(argName)) {
                            finalArgs.put(argName, entry.getValue().applyCached(f));
                        }
                    }
                }
            } else {
                finalArgs = resolvedArgs;
            }
            return command.parseArgumentMap2(finalArgs, store, validators, permisser, true);
        };
        StringBuilder full = new StringBuilder(command.getPrimaryCommandId());
        if (!arguments.isEmpty()) {
            full.append("(");
            boolean first = true;
            for (Map.Entry<String, TypedFunction<T, ?>> entry : arguments.entrySet()) {
                if (!first) {
                    full.append(" ");
                }
                first = false;
                full.append(entry.getKey()).append(": ").append(entry.getValue().getName());
            }
            full.append(")");
        }
        BiFunction<T, Object[], Object> format = (object, paramVals) -> command.call(object, store, paramVals);
        if (isResolved) {
            Object[] argArr = resolved.apply(null);
            return TypedFunction.createParents(command.getReturnType(), f -> format.apply(f, argArr), "{" + full.toString() + "}", null);
        }
        return TypedFunction.createParents(command.getReturnType(), f -> format.apply(f, resolved.apply(f)), "{" + full.toString() + "}", null);
    }

    public abstract String getName(T o);

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
        return getFormatFunction(locals, arg, throwError).applyCached(elem);
    }

    public String format2(ValueStore store, String arg, T elem, boolean throwError) {
        return getFormatFunction(store, arg, throwError).applyCached(elem);
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

//    public String registerWebBindings() {
//        Key key = Key.of(TypeToken.getParameterized(TypedFunction.class, getType(), String.class).getType(), HtmlInput.class);
//        addBinding(store -> {
//            store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
//                Guild guild = (Guild) valueStore.getProvided(Key.of(Guild.class, Me.class));
//                ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
//                List<Member> options = new ArrayList<>(guild.getMembers());
//
//                return multipleSelect(param, options, t -> new AbstractMap.SimpleEntry<>(t.getEffectiveName(), t.getAsMention()), true);
//            }));
//        });
//        List<Coalition> options = Arrays.asList(Coalition.values());
//        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
//            names.add(obj.name());
//            subtext.add(obj.getDescription());
//        });
//    }

    @Binding(value = "Format text containing placeholders")
    public TypedFunction<T, String> getFormatFunction(ValueStore store, String arg) {
        arg = wrapHashLegacy(arg);
        return getFormatFunction(store, arg, true);
    }

    @Binding(value = "Format text containing placeholders")
    public TypedFunction<T, Double> getDoubleFunction(ValueStore store, String arg) {
        arg = wrapHashLegacy(arg);
        TypedFunction<T, ?> result = this.formatRecursively(store, arg, null, 0, true);
        Class type = (Class) result.getType();
        if (type == boolean.class || type == Boolean.class) {
            return TypedFunction.createParent(Double.class, t -> {
                Object value = result.applyCached(t);
                return value == null ? 0 : ((Boolean) value) ? 1d : 0d;
            }, result.getName(), result);
        } else if (type == byte.class || type == Byte.class || type == short.class || type == Short.class || type == int.class || type == Integer.class || type == long.class || type == Long.class || type == float.class || type == Float.class || type == double.class || type == Double.class || type == Number.class) {
            return TypedFunction.createParent(Double.class, f -> {
                Object value = result.applyCached((T) f);
                return value == null ? 0 : ((Number) value).doubleValue();
            }, result.getName(), result);
        } else {
            throw new IllegalArgumentException("Only the following filter types are supported: Number, Boolean, not: `" + ((Class<?>) type).getSimpleName() + "` | input: `" + result.getName() + "`");
        }
    }

    public TypedFunction<T, String> getFormatFunction(ValueStore store, String arg, boolean throwError) {
        return getFormatFunction(store, arg, null, throwError);
    }

    public TypedFunction<T, String> getFormatFunction(ValueStore store, String arg, PlaceholderCache cache, boolean throwError) {
        boolean startsWithEquals = arg.startsWith("=");
        if (startsWithEquals) {
            arg = arg.substring(1);
        }
        if (cache != null) store.addProvider(cache);
        TypedFunction<T, ?> result = this.formatRecursively(store, arg, null, 0, throwError);
        if (result.isResolved()) {
            Object value = result.applyCached(null);
            String valueStr = value == null ? null : value.toString();
            if (startsWithEquals) valueStr = "=" + valueStr;
            return TypedFunction.createConstant(String.class, valueStr, result.getName());
        } else {
            return TypedFunction.createParent(String.class, f -> {
                Object value = result.applyCached((T) f);
                String str = value == null ? null : value.toString();
                if (startsWithEquals && str != null) {
                    str = "=" + value;
                }
                return str;
            }, result.getName(), result);
        }
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