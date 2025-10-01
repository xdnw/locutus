package link.locutus.discord.commands.manager.v2.binding.bindings;

import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.commands.manager.v2.binding.*;
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
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.SelectionAlias;
import link.locutus.discord.db.entities.SheetTemplate;
import link.locutus.discord.gpt.pw.PWGPTHandler;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.math.LazyMathEntity;
import link.locutus.discord.util.math.ReflectionUtil;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.HtmlInput;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class Placeholders<T, M> extends BindingHelper {
    private final ValidatorStore validators;
    private final PermissionHandler permisser;
    private final CommandGroup commands;

    private final Class<T> instanceType;
    private final Class<M> modifierType;
    private final ValueStore store;

    private ParametricCallable createModifier;
    private boolean isGeneric;

//    private final ValueStore math2Type;
//    private final ValueStore type2Math;

    public Placeholders(Class<T> type, Class<M> modifierType, ValueStore store, ValidatorStore validators, PermissionHandler permisser) {
        this.instanceType = type;
        this.modifierType = modifierType;

        this.validators = validators;
        this.permisser = permisser;

        this.commands = CommandGroup.createRoot(store, validators);
        this.store = store;
//        this.math2Type = new SimpleValueStore();
//        this.type2Math = new SimpleValueStore();
        this.getCommands().registerCommands(new DefaultPlaceholders());
        Method create = ReflectionUtil.getMethodByName(getClass(), "create");
        if (create != null) {
            this.createModifier = ParametricCallable.generateFromMethod(null, this, create, store);
        }
    }

    public boolean isGeneric() {
        return isGeneric;
    }

    protected void setGeneric() {
        this.isGeneric = true;
    }

    public ParametricCallable getCreateModifier() {
        return createModifier;
    }

    public Map.Entry<String, M> parseModifier(ValueStore store, Map<String, Object> args) {
        if (createModifier != null && args.containsKey("")) {
            Object selection = args.remove("");
            Object[] parsed = createModifier.parseArgumentMap2(args, store, validators, permisser, true);
            M modifierVal = (M) createModifier.call(this, store, parsed);
            return KeyValue.of((String) selection, modifierVal);
        }
        return null;
    }

    public M parseModifierLegacy(ValueStore store, String input) {
        return null;
    }

    public Placeholders<T, M> init() {
        this.commands.registerCommandsClass(getType());
//        new PWMath2Type().register(math2Type);
//        new PWType2Math().register(type2Math);
        return this;
    }

    public abstract Set<String> getSheetColumns();

    public abstract Set<SelectorInfo> getSelectorInfo();

    public Class<T> getType() {
        return instanceType;
    }

    public Class<M> getModifier() {
        return modifierType;
    }

    protected static <T, M> String _addSelectionAlias(Placeholders<T, M> instance, @Me JSONObject command, @Me GuildDB db, String name, Set<T> elems, String argumentName) {
        return _addSelectionAlias(instance, "", command, db, name, argumentName);
    }

    protected static <T, M> String _addSelectionAlias(Placeholders<T, M> instance, String prefix, @Me JSONObject command, @Me GuildDB db, String name, String... argumentNames) {
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
            throw new IllegalArgumentException("Selection already exists: `" + existing.toString() + "`");
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
        db.getSheetManager().addSelectionAlias(name, instance.getType(), selection, null);
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

    public SelectionAlias getOrCreateSelection(GuildDB db, String selection, String modifier, boolean save, AtomicBoolean createdFlag) {
        outer:
        for (Map.Entry<String, SelectionAlias<T>> entry : db.getSheetManager().getSelectionAliases(getType()).entrySet()) {
            SelectionAlias<T> alias = entry.getValue();
            if (alias.getSelection().equalsIgnoreCase(selection)) {
                return alias;
            }
        }
        String name = getNextSelectionName(db);
        SelectionAlias result;
        if (save) {
            result = db.getSheetManager().addSelectionAlias(name, getType(), selection, modifier);
        } else {
            result = new SelectionAlias(name, getType(), selection, modifier);
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

    protected static <T, M> String _addColumns(Placeholders<T, M> placeholders, @Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet, TypedFunction<T, String>... columns) {
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
            sheet.columns.add(column.getName());
        }
        db.getSheetManager().addSheetTemplate(sheet);
        return (created ? "Created" : "Updated") + " sheet template named: " + sheet + "\n\n" +
                "Rename via " + CM.sheet_template.rename.cmd.toSlashMention() + "\n" +
                "Add more columns via `/sheet_template add`\n" +
                "Remove columns via";
    }

    private void registerCustom(ValueStore store, Method method, Type type) {
        Binding binding = method.getAnnotation(Binding.class);
        String desc = binding.value();
        if (desc == null) desc = "";
        MethodParser parser = new MethodParser(this, method, desc, binding.examples(), type, binding.webType());
        Key key = parser.getKey();
        Parser existing = store.get(key);
        if (existing != null) {
            if (existing instanceof MethodParser<?> mp) {
                Logg.text("Duplicate placeholder name: " + mp.getMethod().getDeclaringClass().getSimpleName() + " | " + mp.getMethod().getName());
            } else {
                Logg.text("Duplicate placeholder type: " + key.toSimpleString());
            }
            return;
        }
        store.addParser(key, parser);
    }

    public void registerWeb() {

    }

    public void registerWebLegacy(ValueStore store) {
        Key<String> key = Key.of(TypeUtils.parameterize(ICommand.class, getType()), HtmlInput.class);
        store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
            ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
            List<CommandCallable> options = new ArrayList<>(getParametricCallables());
            return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
                names.add(obj.getFullPath());
                subtext.add(obj.simpleDesc().split("\n")[0]);
            });
        }));
    }

    public void registerCompleters(ValueStore store) {
        // Key<Object> key = Key.nested(Predicate.class, type);
        // Selectors
        // Predicate
        // Set
        // TypedFunction
        //;     @Autocomplete
        //    @Binding(types={TypedFunction.class, DBNation.class, Double.class}, multiple = true)
        //    public List<String> NationPlaceholder(ArgumentStack stack, String input) {
        //        NationPlaceholders placeholders = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
        //        List<String> options = placeholders.getMetricsDouble(stack.getStore())
        //                .stream().map(NationAttribute::getName).collect(Collectors.toList());
        //        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true);
        //    }
    }

    public void registerTools(ValueStore store, PWGPTHandler gpt) {
        int numFilters = 25;
        Class<T> type = getType();
        { // Predicate<T>
            Key<Object> key = Key.nested(Predicate.class, type);
            store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                String inputStr = (String) input;
                String desc = getDSL(gpt, valueStore, inputStr, numFilters, Placeholders.DSLType.PREDICATE);
                return Map.of("type", "string", "description", desc);
            }));
        }
        { // Set<T>
            Key<Object> key = Key.nested(Set.class, type);
            store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                String inputStr = (String) input;
                String desc = getDSL(gpt, valueStore, inputStr, numFilters, Placeholders.DSLType.SET);
                return Map.of("type", "string", "description", desc);
            }));
        }
        { // TypedFunction<T, String>
            Key<Object> key = Key.of(TypeToken.getParameterized(TypedFunction.class, type, String.class).getType());
            store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                String inputStr = (String) input;
                String desc = getDSL(gpt, valueStore, inputStr, numFilters, Placeholders.DSLType.FORMATTER_STRING);
                return Map.of("type", "string", "description", desc);
            }));
        }
        { // TypedFunction<T, Double>
            Key<Object> key = Key.of(TypeToken.getParameterized(TypedFunction.class, type, Double.class).getType());
            store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                String inputStr = (String) input;
                String desc = getDSL(gpt, valueStore, inputStr, numFilters, Placeholders.DSLType.FORMATTER_NUMBER);
                return Map.of("type", "string", "description", desc);
            }));
        }
        { // Set<TypedFunction<T, Double>>
            Key<Object> key = Key.of(TypeUtils.parameterize(Set.class, TypeUtils.parameterize(TypedFunction.class, type, Double.class)));
            store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                String inputStr = (String) input;
                String desc = getDSL(gpt, valueStore, inputStr, numFilters, DSLType.FORMATTER_NUMBER_ARRAY);
                return Map.of(
                        "type", "array",
                        "items", Map.of("type", "number"),
                        "description", desc
                );
            }));
        }
        { // Set<TypedFunction<T, String>>
            Key<Object> key = Key.of(TypeUtils.parameterize(Set.class, TypeUtils.parameterize(TypedFunction.class, type, String.class)));
            store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                String inputStr = (String) input;
                String desc = getDSL(gpt, valueStore, inputStr, numFilters, DSLType.FORMATTER_STRING_ARRAY);
                return Map.of(
                        "type", "array",
                        "items", Map.of("type", "string"),
                        "description", desc
                );
            }));
        }
        { // ICommand<T>
            Key<Object> key = Key.nested(link.locutus.discord.commands.manager.v2.command.ICommand.class, type);
            store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                // items is {"const": command name, "description": command description}
                PermissionHandler permisser = (PermissionHandler) valueStore.getProvided(Key.of(PermissionHandler.class));
                List<Map<String, String>> items = new ObjectArrayList<>();
                for (ParametricCallable cmd : getParametricCallables()) {
                    if (!cmd.hasPermission(valueStore, permisser)) {
                        continue;
                    }
                    items.add(Map.of(
                            "const", cmd.getPrimaryCommandId(),
                            "description", cmd.simpleDesc()
                    ));
                }
                return Map.of("type", "string", "oneOf", items);
            }));
        }
    }

    public void register(ValueStore store) {
        try {
            Method methodSet = this.getClass().getMethod("parseSet", ValueStore.class, String.class);
            Method methodPredicate = this.getClass().getMethod("parseFilter", ValueStore.class, String.class);
            Method methodFormat = this.getClass().getMethod("getFormatFunction", ValueStore.class, String.class);
            Method methodDouble = this.getClass().getMethod("getDoubleFunction", ValueStore.class, String.class);
            Method methodCommand = this.getClass().getMethod("getCommand", String.class);
            Method methodFormatSet = this.getClass().getMethod("getFormatFunctionSet", ValueStore.class, String.class);
            Method methodDoubleSet = this.getClass().getMethod("getDoubleFunctionSet", ValueStore.class, String.class);
            registerCustom(store, methodSet, TypeToken.getParameterized(Set.class, this.instanceType).getType());
            registerCustom(store, methodPredicate, TypeToken.getParameterized(Predicate.class, this.instanceType).getType());
            registerCustom(store, methodFormat, TypeToken.getParameterized(TypedFunction.class, this.instanceType, String.class).getType());
            registerCustom(store, methodDouble, TypeToken.getParameterized(TypedFunction.class, this.instanceType, Double.class).getType());
            registerCustom(store, methodFormatSet, TypeToken.getParameterized(Set.class, TypeToken.getParameterized(TypedFunction.class, this.instanceType, String.class).getType()).getType());
            registerCustom(store, methodDoubleSet, TypeToken.getParameterized(Set.class, TypeToken.getParameterized(TypedFunction.class, this.instanceType, Double.class).getType()).getType());
            Key<Object> iCommandKey = Key.of(TypeToken.getParameterized(ICommand.class, this.getType()).getType());
            registerCustom(store, methodCommand, iCommandKey.getType());
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
    @Binding(value = "A comma separated list of text containing placeholders to format")
    public Set<TypedFunction<T, String>> getFormatFunctionSet(ValueStore store, String input) {
        if (input == null || input.isEmpty()) return Collections.emptySet();
        List<String> split = StringMan.split(input, ',');
        Set<TypedFunction<T, String>> result = new ObjectLinkedOpenHashSet<>();
        for (String part : split) {
            TypedFunction<T, String> func = getFormatFunction(store, part);
            result.add(func);
        }
        return result;
    }

    @Binding(value = "A comma separated list of text containing placeholders to format to numeric values")
    public Set<TypedFunction<T, Double>> getDoubleFunctionSet(ValueStore store, String input) {
        if (input == null || input.isEmpty()) return Collections.emptySet();
        List<String> split = StringMan.split(input, ',');
        Set<TypedFunction<T, Double>> result = new ObjectLinkedOpenHashSet<>();
        for (String part : split) {
            TypedFunction<T, Double> func = getDoubleFunction(store, part);
            result.add(func);
        }
        return result;
    }

    @Binding(value = "A placeholder name")
    public ICommand<T> getCommand(String input) {
        if (input.startsWith("#")) {
            input = input.substring(1);
        }
        if (input.startsWith("{") && input.endsWith("}")) {
            input = input.substring(1, input.length() - 1);
        }
        List<String> split = StringMan.split(input, ' ');
        CommandCallable command = getCommands().getCallable(split);
        if (command == null) throw new IllegalArgumentException("No command found for `" + input + "`");
        if (command instanceof ICommandGroup group) {
            String prefix = group.getFullPath();
            if (!prefix.isEmpty()) prefix += " ";
            String optionsStr = "- `" + prefix + String.join("`\n- `" + prefix, group.primarySubCommandIds()) + "`";
            throw new IllegalArgumentException("Command `" + input + "` is a group, not an endpoint. Please specify a sub command:\n" + optionsStr);
        }
        if (!(command instanceof ICommand<?>)) throw new IllegalArgumentException("Command `" + input + "` is not a command endpoint");
        return (ICommand<T>) command;
    }

    public Placeholders<T, M> register(Object obj) {
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

    public Map<String, String> getExamples() {
        Map<String, String> examples = new Object2ObjectLinkedOpenHashMap<>();
        return examples;
    }

    public static enum DSLType {
        PREDICATE,
        SET,
        FORMATTER_NUMBER,
        FORMATTER_STRING,
        FORMATTER_NUMBER_ARRAY,
        FORMATTER_STRING_ARRAY,
    }

    public String getDSL(PWGPTHandler gpt, ValueStore store, String query, int numFilters, DSLType type) {
        String phDesc = getDescription();

        List<ParametricCallable> filterList = null;
        if (numFilters > 0) {
            filterList = gpt.getClosestPlaceholder(getType(), store, query, numFilters, true);
        }

        if (type == DSLType.FORMATTER_STRING || type == DSLType.FORMATTER_NUMBER || type == DSLType.FORMATTER_NUMBER_ARRAY || type == DSLType.FORMATTER_STRING_ARRAY) {
            String outKind = switch (type) {
                case FORMATTER_NUMBER -> "number";
                case FORMATTER_STRING -> "string";
                case FORMATTER_NUMBER_ARRAY -> "number[]";
                case FORMATTER_STRING_ARRAY -> "string[]";
                default -> null;
            };

            String outNotes = (type == DSLType.FORMATTER_NUMBER)
                    ? """
                  - The full expression must evaluate to a numeric value.
                  - String fragments are not allowed outside numeric sub‑expressions."""
                    : """
                  - Any value is converted to text via toString().
                  - Math sub‑expressions are evaluated and concatenated""";
            String argDesc =
                ((type == DSLType.FORMATTER_NUMBER_ARRAY || type == DSLType.FORMATTER_STRING_ARRAY) ? "DSL: Array of formatters " : "DSL: formatter ") +
                """
                resolving to a {outKind}.
                Syntax
                - Placeholders: wrap in { and } (e.g., {name}, {fn(2,3)} or {fn(a:2 b:3)}).
                - Operators: +, -, *, /, ^, %, ( ), and ternary cond?then:else.
                - Chaining: {a.b} and nesting {fn({otherFn()})} allowed.
                Coercions
                - Booleans: true→1, false→0 in numeric contexts.
                Output
                {outNotes}
                """
                .replace("{outKind}", outKind)
                .replace("{outNotes}", outNotes);

            StringBuilder extra = new StringBuilder();
            if (filterList != null && !filterList.isEmpty()) {
                extra.append("\nPlaceholders you can use:\n");
                for (ParametricCallable filter : filterList) {
                    Map<String, ParameterData> params = filter.getUserParameterMap();
                    String sig;
                    if (params.isEmpty()) {
                        sig = filter.getPrimaryCommandId();
                    } else {
                        sig = filter.getPrimaryCommandId() + "(" +
                        params.entrySet().stream()
                                .map(e -> e.getKey() + ":" + e.getValue().getBinding().getKey().toSimpleString())
                                .collect(Collectors.joining(", ")) + ")";
                    }
                    extra.append("- {").append(sig).append("}\n");
                }
            }

            return phDesc + "\n" + argDesc + extra.toString();
        } else {
            String argDesc = """
                DSL: single-arg predicate.
                Terms: selectors add; filters keep truthy.
                Truthiness: true or !=0; false or 0.
                Operators
                , sequence (L→R): selectors union-add; filters restrict (keep truthy). Not boolean AND/OR.
                &: selectors ∩; filters AND.
                |: selectors ∪; filters OR.
                (): group; filters apply only within the group.
                Elements
                Selectors{sel_required}:
                {sel_list}
                Filters: start with # (e.g., #flag, #fn(v1,v2), #fn(a:v1 b:v2). Inside filters: comparisons (>=, >, <=, <, !=, =), arithmetic (+, -, *, /, ^), ternary (cond?then:else). Booleans coerce to 1/0; non-zero is truthy.
                """;
            String selRequiredStr = type == DSLType.PREDICATE ? "(optional)" : "(required)";

            List<String> selList = new ObjectArrayList<>();
            for (SelectorInfo info : getSelectorInfo()) {
                selList.add(info.toString());
            }
            String selDesc = String.join("\n", selList);

            String fullDesc = phDesc + "\n" + argDesc
                    .replace("{sel_required}", selRequiredStr)
                    .replace("{sel_list}", selDesc);

            if (filterList != null && !filterList.isEmpty()) {
                StringBuilder extra = new StringBuilder();
                extra.append("\nFilters you can use:\n");
                for (ParametricCallable filter : filterList) {
                    Map<String, ParameterData> params = filter.getUserParameterMap();
                    String sig;
                    if (params.isEmpty()) {
                        sig = "#" + filter.getPrimaryCommandId();
                    } else {
                        sig = "#" + filter.getPrimaryCommandId() + "(" +
                                params.entrySet().stream()
                                        .map(e -> e.getKey() + ":" + e.getValue().getBinding().getKey().toSimpleString())
                                        .collect(Collectors.joining(", ")) + ")";
                    }
                    extra.append("- ").append(sig).append("\n");
                }
                fullDesc = fullDesc + extra;
            }
            return fullDesc;
        }
        // TODO FIXME CM REF add mention of using tool call for listing more filters and for getting more detailed filter info






    }

//    public Map<String, Object> toPredicateJson(ISourceManager embedSrc, String query) {
//        StringBuilder schemaDesc = new StringBuilder();
//        schemaDesc.append("Single-argument predicate DSL string\n");
//        // A single-argument predicate DSL string.
//        //Valid syntax:
//        //
//        //Selectors:
//        for (SelectorInfo selectors : getSelectorInfo()) {
//            // String format, String example, String desc
//            // example may be null (omit it)
//
//        }
//
//        Set<String> sheetCols = getSheetColumns();
//        if (sheetCols != null && !sheetCols.isEmpty()) {
//
//        }
//        //- A Google Sheet URL containing a column of alliance identifiers (one of: alliance, {id}, {name}, {getname}, {getid})
//        //
//        //Filters:
//        //- Placeholders or functions start with '#'
//        //- Example: '#myBoolean', '#myFunc', '#myFunc(arg1,arg2)'
//        //
//        //Comparisons:
//        //- '>=', '>', '<=', '<', '!=', '='
//        //
//        //Arithmetic:
//        //- '+', '-', '*', '/', '^'
//        //
//        //Ternary operator:
//        //- 'cond?then:else'
//        //
//        //Booleans automatically coerce to 1 or 0 in numeric expressions.
//        //Parentheses may be used for grouping and subexpressions.
//    }
//
//    public Map<String, Object> toSetJson(String query) {
//
//    }
//
//    public Map<String, Object> toTypedJson(String query) {
//
//    }

    public Set<T> deserializeSelection(ValueStore store, String input, M modifier) {
        return parseSet(store, input, modifier);
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

    protected Predicate<T> getSingleFilter(ValueStore store, String input) {
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

    protected String wrapHashLegacy(ValueStore store2, String input) {
        if (input.contains("%")) {
            if (input.contains("%guild_alliances%")) {
                Supplier<GuildDB> dbLazy = ArrayUtil.memorize(() -> (GuildDB) store2.getProvided(Key.of(GuildDB.class, Me.class), false));
                GuildDB db = dbLazy.get();
                if (db != null) {
                    String value = db.getAllianceIds(true).stream().map(f -> "AA:" + f).collect(Collectors.joining(","));
                    input = input.replace("%guild_alliances%", value);
                }
            }
        }
        return wrapHash(input);
    }

    public String wrapHash(String input) {
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
        input = wrapHashLegacy(store2, input);
        return ArrayUtil.parseFilter(input,
                f -> parseSingleFilter(store2, f),
                s -> getSingleFilter(store2, s));
    }

    public Set<T> parseSet(Guild guild, User author, DBNation nation, String input) {
        return parseSet(guild, author, nation, input, null);
    }

    public Set<T> parseSet(Guild guild, User author, DBNation nation, String input, M modifier) {
        return parseSet(createLocals(guild, author, nation), input, modifier);
    }

    public Set<T> parseSet(ValueStore store2, String input, M modifier) {
        return parseSet(store2, input);
    }

    @Binding(value = "A comma separated list of items")
    public Set<T> parseSet(ValueStore store2, String input) {
        input = wrapHashLegacy(store2, input);
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
        if (expr == null) return null;
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
                throw new IllegalArgumentException("Null parsed `" + typeFunc.getKey() + "` for Math Expression->Type");
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
        if (param != null && ((Class<?>) param.getType()).isAssignableFrom(s.getClass())) {
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
                        throw new IllegalArgumentException("Unknown math function `" + str + "`");
                    }
                    Object parsed = mathFunc.apply(locals, validators, permisser, str);
                    if (parsed == null) {
                        throw new IllegalArgumentException("Null parsed `" + mathFunc.getKey() + "` for Type->Math Expression");
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
            throw new IllegalArgumentException("Cannot parse int[] to DoubleArray for `" + param.getName() + "`");
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
        return new IllegalArgumentException("Unknown sub command `" + command + "`:\n" +
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
            throw new IllegalArgumentException("Invalid input, Empty function: `" + functionContent + "`");
        }
        for (String s : split) {
            Map<String, TypedFunction<T, ?>> actualArguments = new HashMap<>();
            String arg = s;
            int indexPar = arg.indexOf('(');
            int indexSpace = arg.indexOf(' ');
            String functionName;
            String argumentString;
            if (indexPar != -1 && (indexSpace == -1 || indexPar < indexSpace)) {
                if (!arg.endsWith(")")) {
                    if (throwError) {
                        throw new IllegalArgumentException("Invalid input, Missing closing parenthesis for: `" + arg + "`");
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
            Placeholders<T, M> placeholders = this;
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
            ParametricCallable<?> command = placeholders.get(functionName);
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
            TypedFunction<T, ?> function = format(store, command, actualArguments);
            if (previousFunc == null) {
                previousFunc = function;
            } else if (function.isResolved()) {
                previousFunc = ResolvedFunction.createConstant(function.getType(), function.applyCached(null), functionContent);
            } else if (previousFunc.isResolved()) {
                T value = (T) previousFunc.applyCached(null);
                previousFunc = ResolvedFunction.createConstant(function.getType(), function.applyCached(value), functionContent);
            } else {
                previousFunc = TypedFunction.createParent(function.getType(), previousFunc.andThen((Function) function), functionContent, previousFunc);
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
        BiFunction<T, Object[], Object> format = (object, paramVals) -> object == null ? null : command.call(object, store, paramVals);
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
            locals.addProvider(Key.nested(PlaceholderCache.class, getType()), cache);
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
//                return multipleSelect(param, options, t -> new KeyValue<>(t.getEffectiveName(), t.getAsMention()), true);
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
        arg = wrapHashLegacy(store, arg);
        return getFormatFunction(store, arg, true);
    }

    @Binding(value = "Format text containing placeholders")
    public TypedFunction<T, Double> getDoubleFunction(ValueStore store, String arg) {
        arg = wrapHashLegacy(store, arg);
        TypedFunction<T, ?> result = this.formatRecursively(store, arg, null, 0, false, true);
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
        if (cache != null) store.addProvider(Key.nested(PlaceholderCache.class, getType()), cache);
        TypedFunction<T, ?> result = this.formatRecursively(store, arg, null, 0, false, throwError);
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
//        return new KeyValue<>(cmdObj.getReturnType(), func);
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