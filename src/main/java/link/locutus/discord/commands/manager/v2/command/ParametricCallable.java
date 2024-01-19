package link.locutus.discord.commands.manager.v2.command;

import gg.jte.Content;
import gg.jte.generated.precompiled.command.JteparametriccallableGenerated;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.impl.SlashCommandManager;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ReflectionUtil;
import link.locutus.discord.web.commands.HtmlInput;
import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONObject;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParametricCallable implements ICommand {

    private final Object object;
    private final Method method;
    private final ParameterData[] parameters;
    private final Set<String> valueFlags;
    private final Set<String> provideFlags;
    private final ArrayList<ParameterData> userParameters;
    private final Map<String, ParameterData> paramaterMap;
    private final String help;
    private final ArrayList<String> aliases;
    private final CommandCallable parent;
    private final Annotation[] annotations;
    private final Type returnType;
    private final boolean isStatic;
    private Supplier<String> descMethod;

    public ParametricCallable(CommandCallable parent, ParametricCallable clone, List<String> aliases) {
        this.object = clone.object;
        this.method = clone.method;
        this.isStatic = clone.isStatic;
        this.parameters = clone.parameters;
        this.valueFlags = clone.valueFlags;
        this.provideFlags = clone.provideFlags;
        this.userParameters = clone.userParameters;
        this.paramaterMap = clone.paramaterMap;
        this.descMethod = clone.descMethod;
        this.help = clone.help;
        this.aliases = new ArrayList<>(aliases);
        this.parent = parent;
        this.annotations = clone.annotations;
        this.returnType = clone.returnType;
    }

    public ParametricCallable(CommandCallable parent, ValueStore store, Object object, Method method, Command definition) {
        this.parent = parent;
        this.object = object;
        this.method = method;
        this.isStatic = Modifier.isStatic(method.getModifiers());
        this.returnType = method.getGenericReturnType();
        this.annotations =  method.getAnnotations();
        method.setAccessible(true);
        this.valueFlags = new LinkedHashSet<>();
        this.provideFlags = new LinkedHashSet<>();

        Annotation[][] methodAnnotations = method.getParameterAnnotations();
        String[] names = lookupParameterNames(method);

        Type[] types = method.getGenericParameterTypes();

        this.parameters = new ParameterData[types.length];
        this.userParameters = new ArrayList<>();
        this.paramaterMap = new LinkedHashMap<>();

        // This helps keep tracks of @Nullables that appear in the middle of a list
        // of parameters
        int numOptional = 0;

        LocalValueStore locals = new LocalValueStore<>(store);

        Set<String> flagList = new LinkedHashSet<>();

        // Go through each parameter
        for (int i = 0; i < types.length; i++) {
            Type type = types[i];
            Annotation[] annotations = methodAnnotations[i];

            ParameterData parameter = new ParameterData();
            parameter.setType(type);
            parameter.setModifiers(annotations);

            // Search for annotations
            for (Annotation annotation : annotations) {
                if (annotation instanceof Switch) {
                    parameter.setFlag(((Switch) annotation).value());
                    parameter.setOptional(true);
                } else if (annotation instanceof Default) {
                    parameter.setOptional(true);
                    String[] value = ((Default) annotation).value();
                    if (value.length > 0) {
                        parameter.setDefaultValue(value);
                    }
                    // Special annotation bindings
//                } else if (parameter.getBinding() == null) {
//                    parameter.setBinding(builder.getBindings().get(annotation.annotationType()));
//                    parameter.setClassifier(annotation);
                } else if (annotation instanceof Arg) {
                    parameter.setDescription(((Arg) annotation).value());
                }
            }

            Key<Object> key = Key.of(type, annotations);
            Parser binding = store.get(key);
            if (binding == null) {
                throw new IllegalStateException("No binding found for " + key + " for command: " + method.getDeclaringClass().getSimpleName() + "#" + method.getName());
            }
            parameter.setBinding(binding);

            parameter.setName(names[i]);

            if (parameter.getFlag() != null) {
                if (flagList.contains(parameter.getFlag())) {
                    throw new IllegalStateException("Duplicate flag " + parameter.getFlag() + " for command: " + method.getDeclaringClass().getSimpleName() + "#" + method.getName());
                }
                flagList.add(parameter.getFlag());
            }

            // Track all value flags
            if (parameter.isConsumeFlag()) {
                valueFlags.add(parameter.getFlag());
            } else if (parameter.getFlag() != null) {
                provideFlags.add(parameter.getFlag());
            }
            // Do some validation of this parameter
//            parameter.validate(method, i + 1);

//             Keep track of optional parameters
            if (parameter.isOptional() && parameter.getFlag() == null) {
                numOptional++;
            }  //                if (numOptional > 0 && parameter.getFlag() == null && binding.isConsumer(store)) {
            //                }


            parameters[i] = parameter;

            locals.addProvider(ParameterData.class, parameter);
            // Make a list of "real" parameters
            if (binding.isConsumer(locals)) {
                userParameters.add(parameter);
                paramaterMap.put(parameter.getName().toLowerCase(Locale.ROOT), parameter);
            }
        }

        this.aliases = new ArrayList<>(Arrays.asList(definition.aliases()));
        if (aliases.isEmpty()) aliases.add(method.getName());

        this.help = definition.help();

        //        desc.append(ICommand.formatDescription(definition));
        this.descMethod = () -> definition.desc();
        if (!definition.descMethod().isBlank()) {
            try {
                Method method2 = object.getClass().getMethod(definition.descMethod());
                method2.setAccessible(true);
                descMethod = (() -> {
                    try {
                        return method2.invoke(object).toString();
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public List<Annotation> getAnnotations() {
        return Arrays.asList(annotations);
    }

    public String getSlashMention() {
        return SlashCommandManager.getSlashMention(this);
    }

    public String getSlashCommand(Map<String, String> arguments) {
        return SlashCommandManager.getSlashCommand(this, arguments, true);
    }

    public static List<ParametricCallable> generateFromClass(CommandCallable parent, Class clazz, Object object, ValueStore store) {
        List<ParametricCallable> cmds = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            ParametricCallable parametric = generateFromMethod(parent, object, method, store);
            if (parametric != null) cmds.add(parametric);
        }
        return cmds;
    }

    public static List<ParametricCallable> generateFromObj(CommandCallable parent, Object object, ValueStore store) {
        return generateFromClass(parent, object.getClass(), object, store);
    }

    public static ParametricCallable generateFromMethod(CommandCallable parent, Object object, Method method, ValueStore store) {
        Command cmdAnn = method.getAnnotation(Command.class);
        if (cmdAnn != null) {
            return new ParametricCallable(parent, store, object, method, cmdAnn);
        }
        return null;
    }

    private static String[] lookupParameterNames(Method method) {
        Parameter[] params = method.getParameters();
        String[] names = new String[params.length];
        for (int i = 0; i < params.length; i++) {
            names[i] = params[i].getName();
        }
        return names;
    }

    public Type getReturnType() {
        return returnType;
    }

    @Override
    public String simpleDesc() {
        String desc = descMethod.get();
        if (desc != null && desc.indexOf('{') > 0) {
            desc = desc.replace("{legacy_prefix}", Settings.commandPrefix(true) + "");
            desc = desc.replace("{prefix}", Settings.commandPrefix(false) + "");

            Pattern pattern = Pattern.compile("(\\{/[^}]+})");
            Matcher matcher = pattern.matcher(desc);
            desc = matcher.replaceAll(matchResult -> {
                String group = matchResult.group();
                group = group.substring(1, group.length() - 1);

                // validate command and arguments
                Locutus.imp().getCommandManager().getV2().validateSlashCommand(group.substring(1), true);

                return group;
            });
        }
        return desc;
    }

    @Override
    public String simpleHelp() {
        return help;
    }

    @Override
    public CommandCallable getParent() {
        return parent;
    }

    @Override
    public CommandCallable clone(CommandCallable parent, List<String> aliases) {
        return new ParametricCallable(parent, this, aliases);
    }

    @Override
    public List<String> aliases() {
        return aliases;
    }

    @Override
    public Collection<ParameterData> getParameters() {
        return Arrays.asList(parameters);
    }

    @Override
    public List<ParameterData> getUserParameters() {
        return userParameters;
    }

    @Override
    public Map<String, ParameterData> getUserParameterMap() {
        return paramaterMap;
    }

    @Override
    public String help(ValueStore store) {
        if (this.help.isEmpty()) {
            StringBuilder help = new StringBuilder(getFullPath());
            for (ParameterData parameter : parameters) {
                Parser<?> binding = parameter.getBinding();
                if (!binding.isConsumer(store)) continue;
                String argFormat = parameter.isOptional() || parameter.isFlag() ? "[%s]" : "<%s>";
                if (parameter.getBinding().isConsumer(store)) {
                    if (!parameter.isFlag()) {
                        argFormat = String.format(argFormat, parameter.getName());
                    } else {
                        argFormat = String.format(argFormat, "-" + parameter.getFlag() + " " + parameter.getName());
                    }
                    help.append(" ").append(argFormat);
                }
            }
            return help.toString();
        }
        return help;
    }

    public String getSimpleHelp() {
        return this.help;
    }

    @Override
    public String desc(ValueStore store) {
        StringBuilder expanded = new StringBuilder();
        expanded.append(simpleDesc());
        for (ParameterData parameter : parameters) {
            Parser<?> binding = parameter.getBinding();
            if (!binding.isConsumer(store)) continue;
            expanded.append("\n");
            expanded.append(parameter.getExpandedDescription());
        }
        return expanded.toString();
    }

    @Override
    public void validatePermissions(ValueStore store, PermissionHandler permisser) {
        for (Annotation annotation : annotations) {
            permisser.validate(store, annotation);
        }
    }

    @Override
    public Object call(ArgumentStack stack) {
        return call(this.object, stack);
    }

    public Object[] parseArguments(ArgumentStack stack) {
        return argumentMapToArray(parseArgumentsToMap(stack));
    }

    public Object[] argumentMapToArray(Map<ParameterData, Map.Entry<String, Object>> map) {
        Object[] paramVals = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            ParameterData parameter = parameters[i];
            Map.Entry<String, Object> entry = map.get(parameter);
            if (entry != null) paramVals[i] = entry.getValue();
        }
        return paramVals;
    }

    public Map<String, String> formatArgumentsToMap(ValueStore store, List<String> input) {
        List<String> args = new ArrayList<>(input);
        Map<String, String> flags = ArgumentStack.consumeFlags(args, provideFlags, valueFlags);

        Map<String, String> argumentMap = new LinkedHashMap<>();

        int i = 0;
        for (Map.Entry<String, ParameterData> entry : getUserParameterMap().entrySet()) {
            String name = entry.getKey();
            ParameterData parameter = entry.getValue();
            if (parameter.isFlag()) {
                String value = flags.get(parameter.getFlag());
                if (value != null) {
                    argumentMap.put(name, value);
                }
                continue;
            }
            if (i < args.size()) {
                argumentMap.put(name, args.get(i++));
                continue;
            }
            String value = parameter.getDefaultValueString();
            if (value != null) {
                argumentMap.put(name, value);
                continue;
            }
            if (!parameter.isOptional() || !parameter.getBinding().isConsumer(store)) {
                throw new IllegalArgumentException("Missing required argument: " + parameter.getName() + " for command: " + getFullPath());
            }
        }
        return argumentMap;
    }

    public Map<ParameterData, Map.Entry<String, Object>> parseArgumentsToMap(ArgumentStack stack) {
        ValueStore<?> store = stack.getStore();
        validatePermissions(store, stack.getPermissionHandler());

        ValueStore locals = store;
        locals.addProvider(Key.of(ParametricCallable.class, Me.class), this);

        Map<ParameterData, Map.Entry<String, Object>> argumentMap = new LinkedHashMap<>();
        ParameterData commandIndex = null;

        Map<String, String> flags = stack.consumeFlags(provideFlags, valueFlags);
        for (ParameterData parameter : parameters) {
            Key typeKey = parameter.getBinding().getKey();
            if (typeKey.getType() == JSONObject.class && typeKey.getAnnotation(Me.class) != null) {
                commandIndex = parameter;
                continue;
            }
            locals.addProvider(ParameterData.class, parameter);

            String unparsed = null;
            Object value;
            try {
                if (parameter.isFlag()) {
                    unparsed = flags.get(parameter.getFlag());
                    if (unparsed == null) {
                        if (parameter.getDefaultValue() != null && parameter.getDefaultValue().length != 0) {
                            unparsed = parameter.getDefaultValueString();
                            value = locals.get(parameter.getBinding().getKey()).apply(stack.getStore(), unparsed);
                        } else if (!parameter.isConsumeFlag()) {
                            value = false;
                        } else {
                            value = null;
                        }
                    } else {
                        value = locals.get(parameter.getBinding().getKey()).apply(stack.getStore(), unparsed);
                    }
                } else {
                    if (!stack.hasNext() && parameter.isOptional()) {
                        if (!parameter.getBinding().isConsumer(stack.getStore())) {
                            value = store.getProvided(parameter.getBinding().getKey(), false);
                        } else {
                            if (parameter.getDefaultValue() == null) {
                                continue;
                            } else {
                                unparsed = parameter.getDefaultValueString();
                                stack.add(parameter.getDefaultValue());
                            }
                            continue;
                        }
                    } else if (stack.hasNext() || !parameter.isOptional() || (parameter.getDefaultValue() != null && parameter.getDefaultValue().length != 0)) {
                        if (parameter.getBinding().isConsumer(stack.getStore()) && !stack.hasNext()) {
                            String name = parameter.getBinding().getKey().toSimpleString();
                            throw new CommandUsageException(this, "Expected argument: <" + parameter.getName() + "> of type: " + name);
                        }
                        int originalRemaining = stack.remaining();
                        List<String> remaining = new ArrayList<>(stack.getRemainingArgs());
                        System.out.println("Key " + parameter.getBinding().getKey() + " | " + parameter.getName() + " | " + method.getName() + " input " + originalRemaining + " | " + remaining);
                        value = locals.get(parameter.getBinding().getKey()).apply(stack);
                        int numConsumed = originalRemaining - stack.remaining();
                        unparsed = String.join(" ", remaining.subList(0, numConsumed));
                    } else {
                        value = null;
                    }
                }
            } catch (IllegalStateException e) {
                if (!parameter.isOptional() || parameter.getDefaultValue() != null) {
                    System.out.println("Throw e");
                    throw e;
                }
                System.out.println("Dont throw e");
                value = null;
            }
            try {
                value = stack.getValidators().validate(parameter, locals, value);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                String msg = "For `" + parameter.getName() + "`: " + e.getMessage();
                throw new CommandUsageException(this, msg);
            }
            argumentMap.put(parameter, new AbstractMap.SimpleEntry<>(unparsed, value));
        }
        if (commandIndex != null) {
            Map<String, String> commandArgs = new LinkedHashMap<>();
            commandArgs.put("", getFullPath());
            for (Map.Entry<ParameterData, Map.Entry<String, Object>> entry : argumentMap.entrySet()) {
                if (entry.getValue().getKey() != null) {
                    commandArgs.put(entry.getKey().getName(), entry.getValue().getKey());
                }
            }
            argumentMap.put(commandIndex, new AbstractMap.SimpleEntry<>(null, new JSONObject(commandArgs)));
        }
        return argumentMap;
    }

    public Object call(Object instance, ArgumentStack stack) {
        Object[] paramVals = parseArguments(stack);
        return call(instance, stack.getStore(), paramVals);
    }

    public Object call(Object instance, ValueStore store, Object[] paramVals) {
        try {
            Object callOn = isStatic ? null : (!method.getDeclaringClass().isInstance(instance) ? this.object : instance);
            return method.invoke(callOn, paramVals);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
            throw new CommandUsageException(this, e.getMessage());
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toBasicMarkdown(ValueStore store, PermissionHandler permisser, String prefix, boolean spoiler, boolean links, boolean useFullLinks) {
        StringBuilder result = new StringBuilder();
        Map<String, String> permissionInfo = new LinkedHashMap<>();

        Method method = getMethod();
        if (permisser != null) {
            String baseUrl = (useFullLinks ? "https://t.ly/JvbjX" : "permissions") + "#";
            for (Annotation permAnnotation : method.getDeclaredAnnotations()) {
                Key<Object> permKey = Key.of(boolean.class, permAnnotation);
                Parser parser = permisser.get(permKey);
                if (parser != null) {
                    List<String> permValues = new ArrayList<>();

                    for (Method permMeth : permAnnotation.annotationType().getDeclaredMethods()) {
                        Object def = permMeth.getDefaultValue();
                        Object current = null;
                        try {
                            current = permMeth.invoke(permAnnotation);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException(e);
                        }
                        if (!Objects.equals(def, current)) {
                            String currentStr;
                            if (current.getClass().isArray()) {
                                List<String> stringList = new ArrayList<>();
                                int length = Array.getLength(current);
                                for (int i = 0; i < length; i++) {
                                    Object element = Array.get(current, i);
                                    stringList.add(StringMan.getString(element));
                                }
                                currentStr = String.join("/", stringList);
                            } else {
                                currentStr = current.toString();
                            }
                            if (permMeth.getName().equalsIgnoreCase("value")) {
                                permValues.add(currentStr);
                            } else if (current.getClass() == Boolean.class) {
                                permValues.add(permMeth.getName());
                            } else {
                                permValues.add(permMeth.getName() + ": " + StringMan.getString(current));
                            }
                        }
                    }

                    String simpleName = permAnnotation.annotationType().getSimpleName().replaceFirst("(?i)permission", "");
                    String title = simpleName + "(" + String.join(", ", permValues) + ")";
                    permissionInfo.put(title, baseUrl + simpleName.toLowerCase(Locale.ROOT));
                }
            }
            if (permissionInfo.isEmpty()) {
                result.append("`This command is public`\n\n");
            }
        }

        if (simpleDesc().isEmpty()) {
            result.append("`No description provided`\n\n");
        } else {
            result.append(simpleDesc() + "\n\n");
        }

        Set<String> duplicateDesc = new HashSet<>();
        List<ParameterData> params = getUserParameters();
        if (params.isEmpty()) {
            result.append("`This command has no arguments`\n\n");
        } else {
            String typeUrlBase = (useFullLinks ? "https://t.ly/maKT" : "arguments") + "#";

            result.append("**Arguments:**\n\n");
            for (ParameterData parameter : params) {
                Parser<?> binding = parameter.getBinding();
                String name = parameter.getName();
                Key key = binding.getKey();
                String desc = parameter.getDescription();
                String defDesc = binding.getDescription();
                if (desc == null || desc.isEmpty()) desc = defDesc;
                else if (defDesc != null && !defDesc.isEmpty() && !Objects.equals(desc, defDesc)) {
                    desc += "\n(" + defDesc + ")";
                }
                if (!duplicateDesc.add(desc)) {
                    desc = null;
                }

                if (parameter.getDefaultValue() != null) {
                    String defStr = parameter.getDefaultValueString();
                    name = name + "=" + defStr;
                }

                String argFormat = parameter.isOptional() || parameter.isFlag() ? "[%s]" : "<%s>";
                if (parameter.isFlag()) {
                    name = "-" + parameter.getFlag() + " " + name;
                }
                argFormat = String.format(argFormat, name);

                String keyName = key.toSimpleString();
                if (spoiler) keyName = StringEscapeUtils.escapeHtml4(keyName.replace("[", "\\[").replace("]", "\\]"));
                if (links) {
                    String typeLink = MarkupUtil.markdownUrl(keyName, typeUrlBase + MarkupUtil.pathName(key.toSimpleString().toLowerCase(Locale.ROOT)));
                    result.append("`" + argFormat + "`").append(" - ").append(typeLink);
                } else {
                    result.append("`" + argFormat + "`").append(" - ").append(keyName);
                }

                result.append("\n\n");

                if (desc != null && !desc.isEmpty()) {
                    result.append("> " + desc.replaceAll("\n", "\n> ") + "\n\n");
                }
            }
        }

        if (!permissionInfo.isEmpty()) {
            result.append("**Permission:**\n\n");
            for (Map.Entry<String, String> entry : permissionInfo.entrySet()) {
                if (links) {
                    result.append("- " + MarkupUtil.markdownUrl(entry.getKey(), entry.getValue()) + "\n");
                } else {
                    result.append("- " + entry.getKey() + "\n");
                }
            }
        }
        return result.toString();
    }

    public String toBasicHtml(ValueStore store) {
        StringBuilder response = new StringBuilder();
        for (ParameterData parameter : parameters) {
            store.addProvider(ParameterData.class, parameter);

            Parser<?> binding = parameter.getBinding();
            if (!binding.isConsumer(store)) continue;

            Key htmlKey = binding.getKey().append(HtmlInput.class);

            Parser parser = store.get(htmlKey);
            if (parser == null) throw new IllegalArgumentException("No key found for " + htmlKey);

            response.append(parser.apply(store, null));
        }
        return response.toString();
    }

    @Override
    public String toHtml(WebStore ws, PermissionHandler permHandler, String endpoint, boolean sse) {
        validatePermissions(ws.store(), permHandler);

        String response = "<form id='command-form' " + (endpoint != null ? "endpoint=\"" + endpoint + "\" " : "") + "onsubmit=\"return executeCommandFromArgMap(this, " + sse + ")\" method=\"post\">" +
                "<div class=\"\">" +
                toBasicHtml(ws.store()) +
                "</div>" +
                "<button type=\"submit\" class=\"btn btn-primary\">Submit</button>" +
                "</form>";
        return WebStore.render(f -> JteparametriccallableGenerated.render(f, null, ws,  this, ws.unsafe(response)));
    }

    public String stringifyArgumentMap(Map<String, String> combined, String delim) {
        return StringMan.join(orderArgumentMap(combined, true), delim);
    }

    public List<String> orderArgumentMap(Map<String, String> combined, boolean wrap) {
        List<String> args = new ArrayList<>();
        for (ParameterData parameter : parameters) {
            String value = combined.get(parameter.getName());
            if (value == null) {
                value = combined.get(parameter.getName().toLowerCase(Locale.ROOT));
            }
            String valueWrapped = wrap ? "\u201D" + value + "\u201D" : value;

            if (value != null) {
                if (parameter.isFlag()) {
                    if (parameter.isConsumeFlag()) {
                        args.add("-" + parameter.getFlag() + " " + valueWrapped);
                    } else if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("1")) {
                        args.add("-" + parameter.getFlag());
                    }
                } else {
                    args.add(valueWrapped);
                }
            }
        }
        return args;
    }

    public Object[] parseArgumentMap(Map<String, String> combined, ArgumentStack stack2) {
        return parseArgumentMap(combined, stack2.getStore(), stack2.getValidators(), stack2.getPermissionHandler());
    }
    public Object[] parseArgumentMap(Map<String, String> combined, ValueStore store, ValidatorStore validators, PermissionHandler permisser) {
        return parseArgumentMap2((Map) combined, store, validators, permisser, false);
    }
    public Object[] parseArgumentMap2(Map<String, Object> combined, ValueStore store, ValidatorStore validators, PermissionHandler permisser, boolean partialParse) {
        validatePermissions(store, permisser);

        ValueStore locals = store;
        locals.addProvider(Key.of(ParametricCallable.class, Me.class), this);

        Map<String, ParameterData> paramsByName = new HashMap<>();
        Map<String, ParameterData> paramsByNameLower = new HashMap<>();
        for (ParameterData parameter : parameters) {
            paramsByName.put(parameter.getName(), parameter);
            paramsByNameLower.put(parameter.getName().toLowerCase(Locale.ROOT), parameter);
        }

        BiFunction<ParameterData, Object, Object> parse;
        if (partialParse) {
            parse = new BiFunction<ParameterData, Object, Object>() {
                @Override
                public Object apply(ParameterData param, Object o) {
                    if (o == null) {
                        if (!param.getBinding().isConsumer(store)) {
                            return locals.getProvided(param.getBinding().getKey(), false);
                        }
                        String def = param.getDefaultValueString();
                        if (def != null) {
                            return locals.get(param.getBinding().getKey()).apply(store, def);
                        }
                        if (param.isOptional()) {
                            return null;
                        }
                        throw new IllegalArgumentException("Missing required parameter: " + param.getName() + " for " + getFullPath());
                    }
                    Class typeClass = ReflectionUtil.getClassType(param.getType());
                    if (typeClass.isAssignableFrom(o.getClass())) return o;
                    if (typeClass.isPrimitive()) {
                        Class<?> wrapper = ReflectionUtil.getWrapperClass(typeClass);
                        if (wrapper.isAssignableFrom(o.getClass())) {
                            return o;
                        }
                    }
                    if (o.getClass() != String.class) {
                        throw new IllegalArgumentException("Cannot parse " + o.getClass() + " to " + typeClass + " for " + getFullPath() + " parameter " + param.getName());
                    }
                    return locals.get(param.getBinding().getKey()).apply(store, o.toString());
                }
            };
        } else {
            parse = new BiFunction<ParameterData, Object, Object>() {
                @Override
                public Object apply(ParameterData param, Object o) {
                    if (o == null) {
                        if (!param.getBinding().isConsumer(store)) {
                            return locals.getProvided(param.getBinding().getKey(), !param.isOptional());
                        }
                        String def = param.getDefaultValueString();
                        if (def != null) {
                            return locals.get(param.getBinding().getKey()).apply(store, def);
                        }
                        if (param.isOptional()) {
                            return null;
                        }
                        throw new IllegalArgumentException("Missing required parameter: " + param.getName() + " for " + getFullPath());
                    }
                    return locals.get(param.getBinding().getKey()).apply(store, o.toString());
                }
            };
        }

        Map<String, Object> flags = new HashMap<>();
        for (Map.Entry<String, Object> entry : combined.entrySet()) {
            ParameterData param = paramsByName.get(entry.getKey());
            if (param == null) param = paramsByNameLower.get(entry.getKey().toLowerCase(Locale.ROOT));
            if (param == null) throw new IllegalArgumentException("Could not find param: `" + entry.getKey() + "` for command " + getFullPath());
            if (param.isFlag()) {
                flags.put(param.getFlag(), entry.getValue());
            }
        }

        Object[] paramVals = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            ParameterData parameter = parameters[i];
            locals.addProvider(ParameterData.class, parameter);

            Object arg = combined.get(parameter.getName());
            if (arg == null) arg = combined.get(parameter.getName().toLowerCase(Locale.ROOT));

            Object value;
            // flags
            if (parameter.isFlag()) {
                Object toParse = flags.get(parameter.getFlag());
                if (toParse == null) {
                    if (parameter.getDefaultValue() != null && parameter.getDefaultValue().length != 0) {
                        value = locals.get(parameter.getBinding().getKey()).apply(store, parameter.getDefaultValueString());
                    } else if (!parameter.isConsumeFlag()) {
                        value = false;
                    } else {
                        value = null;
                    }
                } else {
                    value = parse.apply(parameter, toParse);
                }
            } else {
                Object arg2 = null;
//                List<String> args;
                if (arg == null && parameter.isOptional()) {
                    if (parameter.getDefaultValue() == null) {
                        paramVals[i] = null;
                        continue;
                    }
//                    args = new ArrayList<>(Arrays.asList(parameter.getDefaultValue()));
                    arg2 = parameter.getDefaultValueString();
                } else if (arg != null) {
                    arg2 = arg;
//                    args = new ArrayList<>(Collections.singletonList(arg));
                } else {
//                    args = new ArrayList<>();
                    if (parameter.getDefaultValue() != null && parameter.getDefaultValue().length > 0)
                        arg2 = parameter.getDefaultValueString();
//                        args.addAll(Arrays.asList(parameter.getDefaultValue()));
                }
                if (!parameter.isOptional() || arg2 != null || (parameter.getDefaultValue() != null && parameter.getDefaultValue().length != 0)) {
                    if (parameter.getBinding().isConsumer(store) && arg2 == null) {
                        String name = parameter.getType().getTypeName();
                        String[] split = name.split("\\.");
                        name = split[split.length - 1];
                        throw new CommandUsageException(this, "Expected argument: <" + parameter.getName() + "> of type: " + name + " for command " + getFullPath());
                    }
                    value = parse.apply(parameter, arg2);
//                    ArgumentStack stack = new ArgumentStack(args, store, validators, permisser);
//                    value = locals.get(parameter.getBinding().getKey()).apply(stack);
                } else {
                    value = null;
                }
            }
            try {
                value = validators.validate(parameter, locals, value);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                String msg = "For `" + parameter.getName() + "`: " + e.getMessage();
                throw new CommandUsageException(this, msg);
            }
            paramVals[i] = value;
        }
        return paramVals;
    }

    @Override
    public Set<ParametricCallable> getParametricCallables(Predicate<ParametricCallable> returnIf) {
        return (returnIf.test(this)) ? Collections.singleton(this) : Collections.emptySet();
    }

    public Method getMethod() {
        return method;
    }

    public Object getObject() {
        return object;
    }

    public <T extends Annotation> T getAnnotation(Class<T> annClazz) {
        return method.getAnnotation(annClazz);
    }
}