package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.web.commands.HtmlInput;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class ParametricCallable implements ICommand {

    private final Object object;
    private final Method method;
    private final ParameterData[] parameters;
    private final Set<String> valueFlags;
    private final Set<String> provideFlags;
    private final ArrayList<ParameterData> userParameters;
    private final Map<String, ParameterData> paramaterMap;
    private final String desc;
    private final String help;
    private final ArrayList<String> aliases;
    private final CommandCallable parent;
    private final Annotation[] annotations;
    private final Type returnType;

    public static List<ParametricCallable> generateFromClass(CommandCallable parent, Object object, ValueStore store) {
        List<ParametricCallable> cmds = new ArrayList<>();
        for (Method method : object.getClass().getDeclaredMethods()) {
            Command cmdAnn = method.getAnnotation(Command.class);
            if (cmdAnn != null) {
                ParametricCallable callable = new ParametricCallable(parent, store, object, method, cmdAnn);
                cmds.add(callable);
            }
        }
        return cmds;
    }

    private static String[] lookupParameterNames(Method method) {
        Parameter[] params = method.getParameters();
        String[] names=  new String[params.length];
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

    public ParametricCallable(CommandCallable parent, ParametricCallable clone, List<String> aliases) {
        this.object = clone.object;
        this.method = clone.method;
        this.parameters = clone.parameters;
        this.valueFlags = clone.valueFlags;
        this.provideFlags = clone.provideFlags;
        this.userParameters = clone.userParameters;
        this.paramaterMap = clone.paramaterMap;
        this.desc = clone.desc;
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
        this.returnType = method.getGenericReturnType();
        this.annotations = method.getAnnotations();
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

            Key key = Key.of(type, annotations);
            Parser binding = store.get(key);
            if (binding == null) {
                throw new IllegalStateException("No binding found for " + key + " for command: " + method.getDeclaringClass().getSimpleName() + "#" + method.getName());
            }
            parameter.setBinding(binding);

            parameter.setName(names[i]);

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
            } else {
//                if (numOptional > 0 && parameter.getFlag() == null && binding.isConsumer(store)) {
//                }
            }

            parameters[i] = parameter;

            // Make a list of "real" parameters
            if (binding.isConsumer(store)) {
                userParameters.add(parameter);
                paramaterMap.put(parameter.getName().toLowerCase(Locale.ROOT), parameter);
            }
        }

        this.aliases = new ArrayList<String>(Arrays.asList(definition.aliases()));
        if (aliases.isEmpty()) aliases.add(method.getName());

        StringBuilder help = new StringBuilder();
        help.append(definition.help());
        this.help = help.toString();

        StringBuilder desc = new StringBuilder();
        desc.append(ICommand.formatDescription(definition));
        this.desc = desc.toString();
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
            StringBuilder help = new StringBuilder(getPrimaryCommandId());
            for (ParameterData parameter : parameters) {
                Parser binding = parameter.getBinding();
                if (!binding.isConsumer(store)) continue;
                String argFormat = parameter.isOptional() || parameter.isFlag() ? "[%s]" : "<%s>";
                if (!parameter.isFlag() && parameter.getBinding().isConsumer(store)) {
                    argFormat = String.format(argFormat, parameter.getName());
                    help.append(" ").append(argFormat);
                }
            }
            return help.toString();
        }
        return help;
    }

    public String getSimpleDesc() {
        return this.desc;
    }

    public String getSimpleHelp() {
        return this.help;
    }

    @Override
    public String desc(ValueStore store) {
        StringBuilder expanded = new StringBuilder();
        expanded.append(desc);
        for (ParameterData parameter : parameters) {
            Parser binding = parameter.getBinding();
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

    public Object[] parseArguments(Object instance, ArgumentStack stack) {
        ValueStore store = stack.getStore();
        validatePermissions(store, stack.getPermissionHandler());

        ValueStore locals = store;
        locals.addProvider(ParametricCallable.class, this);

        Map<String, String> flags = stack.consumeFlags(provideFlags, valueFlags);
        Object[] paramVals = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            ParameterData parameter = parameters[i];
            locals.addProvider(ParameterData.class, parameter);

            Object value;
            // flags
            if (parameter.isFlag()) {
                String toParse = flags.get(parameter.getFlag());
                if (toParse == null) {
                    if (parameter.getDefaultValue() != null && parameter.getDefaultValue().length != 0) {
                        value = locals.get(parameter.getBinding().getKey()).apply(stack.getStore(), parameter.getDefaultValueString());
                    } else if (!parameter.isConsumeFlag()) {
                        value = false;
                    } else {
                        value = null;
                    }
                } else {
                    value = locals.get(parameter.getBinding().getKey()).apply(stack.getStore(), toParse);
                }
            } else {
                if (!stack.hasNext() && parameter.isOptional()) {
                    if (parameter.getDefaultValue() == null) {
                        paramVals[i] = null;
                        continue;
                    } else {
                        stack.add(parameter.getDefaultValue());
                    }
                }
                if (stack.hasNext() || !parameter.isOptional() || (parameter.getDefaultValue() != null && parameter.getDefaultValue().length != 0)) {
                    if (parameter.getBinding().isConsumer(stack.getStore()) && !stack.hasNext()) {
                        String name = parameter.getType().getTypeName();
                        String[] split = name.split("\\.");
                        name = split[split.length - 1];
                        throw new CommandUsageException(this, "Expected argument: <" + parameter.getName() + "> of type: " + name, help(locals), desc(locals));
                    }
                    value = locals.get(parameter.getBinding().getKey()).apply(stack);
                } else {
                    value = null;
                }
            }
            try {
                value = stack.getValidators().validate(parameter, locals, value);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                String msg = "For `" + parameter.getName() + "`: " + e.getMessage();
                throw new CommandUsageException(this, msg, help(locals), desc(locals));
            }
            paramVals[i] = value;
        }
        return paramVals;
    }

    public Object call(Object instance, ArgumentStack stack) {
        Object[] paramVals = parseArguments(instance, stack);
        return call(instance, stack.getStore(), paramVals);
    }

    public Object call(Object instance, ValueStore store, Object[] paramVals) {
        try {
            return method.invoke(instance == null ? this.object : instance, paramVals);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            throw new CommandUsageException(this, e.getMessage(), help(store), desc(store));
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public String toBasicHtml(ValueStore store) {
        StringBuilder response = new StringBuilder();
        for (ParameterData parameter : parameters) {
            store.addProvider(ParameterData.class, parameter);

            Parser binding = parameter.getBinding();
            if (!binding.isConsumer(store)) continue;

            Key htmlKey = binding.getKey().append(HtmlInput.class);

            Parser parser = store.get(htmlKey);
            if (parser == null) throw new IllegalArgumentException("No key found for " + htmlKey);

            response.append(parser.apply(store, null));
        }
        return response.toString();
    }

    @Override
    public String toHtml(ValueStore store, PermissionHandler permHandler, String endpoint) {
        validatePermissions(store, permHandler);

        StringBuilder response = new StringBuilder();
        response.append("<form id='command-form' " + (endpoint != null ? "endpoint=\"" + endpoint + "\" " : "") + "onsubmit=\"return executeCommandFromArgMap(this)\" method=\"post\">");
        response.append("<div class=\"\">");
        response.append(toBasicHtml(store));
        response.append("</div>");
        response.append("<button type=\"submit\" class=\"btn btn-primary\">Submit</button>");

        response.append("</form>");

        return rocker.command.parametriccallable.template(store, this, response.toString()).render().toString();
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
        ValueStore store = stack2.getStore();
        validatePermissions(store, stack2.getPermissionHandler());

        ValueStore locals = store;
        locals.addProvider(ParametricCallable.class, this);

        Map<String, ParameterData> paramsByName = new HashMap<>();
        Map<String, ParameterData> paramsByNameLower = new HashMap<>();
        for (ParameterData parameter : parameters) {
            paramsByName.put(parameter.getName(), parameter);
            paramsByNameLower.put(parameter.getName().toLowerCase(Locale.ROOT), parameter);
        }

        Map<String, String> flags = new HashMap<>();
        for (Map.Entry<String, String> entry : combined.entrySet()) {
            ParameterData param = paramsByName.get(entry.getKey());
            if (param == null) param = paramsByNameLower.get(entry.getKey().toLowerCase(Locale.ROOT));
            if (param.isFlag()) {
                flags.put(param.getFlag(), entry.getValue());
            }
        }

        Object[] paramVals = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            ParameterData parameter = parameters[i];
            locals.addProvider(ParameterData.class, parameter);

            String arg = combined.get(parameter.getName());
            if (arg == null) arg = combined.get(parameter.getName().toLowerCase(Locale.ROOT));

            Object value;
            // flags
            if (parameter.isFlag()) {
                String toParse = flags.get(parameter.getFlag());
                if (toParse == null) {
                    if (!parameter.isConsumeFlag()) {
                        value = false;
                    } else {
                        value = null;
                    }
                } else {
                    value = locals.get(parameter.getBinding().getKey()).apply(stack2.getStore(), toParse);
                }
            } else {
                List<String> args;
                if (arg == null && parameter.isOptional()) {
                    if (parameter.getDefaultValue() == null) {
                        paramVals[i] = null;
                        continue;
                    }
                    args = new ArrayList<>(Arrays.asList(parameter.getDefaultValue()));
                } else if (arg != null) {
                    args = new ArrayList<>(Collections.singletonList(arg));
                } else {
                    args = new ArrayList<>();
                }
                if (!parameter.isOptional() || (parameter.getDefaultValue() != null && parameter.getDefaultValue().length != 0)) {
                    if (parameter.getBinding().isConsumer(stack2.getStore()) && args.isEmpty()) {
                        String name = parameter.getType().getTypeName();
                        String[] split = name.split("\\.");
                        name = split[split.length - 1];
                        throw new CommandUsageException(this, "Expected argument: <" + parameter.getName() + "> of type: " + name, help(locals), desc(locals));
                    }
                    ArgumentStack stack = new ArgumentStack(args, stack2.getStore(), stack2.getValidators(), stack2.getPermissionHandler());
                    value = locals.get(parameter.getBinding().getKey()).apply(stack);
                } else {
                    value = null;
                }
            }
            try {
                value = stack2.getValidators().validate(parameter, locals, value);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                String msg = "For `" + parameter.getName() + "`: " + e.getMessage();
                throw new CommandUsageException(this, msg, help(locals), desc(locals));
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
}
