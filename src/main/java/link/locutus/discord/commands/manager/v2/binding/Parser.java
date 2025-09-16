package link.locutus.discord.commands.manager.v2.binding;

import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.util.math.ReflectionUtil;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public interface Parser<T> {
    T apply(ArgumentStack arg);

    T apply(ValueStore store, Object t);

    boolean isConsumer(ValueStore store);

    Key<?> getKey();

    String getDescription();

    String[] getExamples();

    Class<?>[] getWebType();

    default Key<?> getWebTypeOrNull() {
        Key<?> key = getKey();
        Class<?>[] webType = getWebType();
        if (webType != null && webType.length > 0) {
            if (webType.length > 1) {
                Type type = ReflectionUtil.buildNestedType(webType);
                return Key.of(type);
            } else {
                return Key.of((Type) webType[0]);
            }
        }
        return null;
    }

    default T apply(LocalValueStore store, ValidatorStore validators, PermissionHandler permisser, String... args) {
        List<String> argsList;
        if (args == null) {
            argsList = new ArrayList<>();
        } else {
            argsList = new ArrayList<>(Arrays.asList(args));
        }
        ArgumentStack stack = new ArgumentStack(argsList, store, validators, permisser);
        return apply(stack);
    }

    default String getNameDescriptionAndExamples(boolean keyName, boolean markdownEscape, boolean backTicks, boolean printErrors) {
        StringBuilder result = new StringBuilder();
        Key key = getKey();

        if (keyName) {
            String name = markdownEscape ? key.keyNameMarkdown() : key.toSimpleString();
            if (backTicks) {
                result.append("`" + name + "`\n");
            } else {
                result.append(name + "\n");
            }
        }
        if (getDescription().isEmpty()) {
            if (printErrors) {
                result.append("`No description provided`\n\n");
            }
        } else {
            result.append(getDescription() + "\n\n");
        }

        Type type = key.getType();
        boolean printExamples = true;

        if (type instanceof Class typeClass) {
            if (typeClass.isEnum()) {
                Object[] options = typeClass.getEnumConstants();
                result.append("Options:\n");
                for (Object option : options) {
                    String optionStr = option.toString();
                    if (optionStr.contains("\n")) {
                        optionStr = "**" + optionStr.replaceFirst("\n", "**\n");
                    } else {
                        optionStr = "**" + optionStr + "**";
                    }
                    result.append("- " + optionStr + "\n");
                }
                printExamples = false;
            }
        }
        if (printExamples) {
            String[] examples = getExamples();
            if (examples != null && examples.length > 0) {
                result.append("Examples:\n");
                for (String example : examples) {
                    result.append("- " + example + "\n");
                }
            } else if (printErrors) {
                result.append("`No examples provided`\n\n");
            }
        }
        return result.toString();
    }

    Map<String, Object> toJson();
}
