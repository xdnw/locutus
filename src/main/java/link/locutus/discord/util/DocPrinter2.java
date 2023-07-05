package link.locutus.discord.util;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.config.Settings;
import link.locutus.discord.config.yaml.Config;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DocPrinter2 {
    public static String printPlaceholders(Placeholders placeholders, ValueStore store) throws InvocationTargetException, IllegalAccessException {
        String header = """
## Placeholder Syntax

 - `<arg>` - A required parameter
 
 - `[arg]` - An optional parameter
 
 - `<arg1|arg2>` - Multiple parameters options
 
 - `<arg=value>` - Default or suggested value
 
 - `[-f flag]` - A optional command argument flag
 
 
## Example discord usage

For placeholders `{getLand}`

For filters `#getLand>30`

Use round brackets for arguments `myFunction(123)`

---

# Placeholders

---


""";
        return printCommands(placeholders.getCommands(), store, placeholders.getPermisser(), "\\#", header);
    }

    public static String printCommands(CommandGroup group, ValueStore store, PermissionHandler permisser) throws InvocationTargetException, IllegalAccessException {
        String header = """
## Command Syntax

 - `<arg>` - A required parameter
 
 - `[arg]` - An optional parameter
 
 - `<arg1|arg2>` - Multiple parameters options
 
 - `<arg=value>` - Default or suggested value
 
 - `[-f flag]` - A optional command argument flag
 
 
## Example discord usage

For `who <nations> [-l list]`

Slash: `/who nations:Rose list:True`

Message: `$who Rose -l`

---

# Commands

---


""";
        return printCommands(group, store, permisser, "/", header);
    }

    public static String printCommands(CommandGroup group, ValueStore store, PermissionHandler permisser, String prefix, String header) throws InvocationTargetException, IllegalAccessException {
        // Command name
        // Description
        // Arguments
        // Permissions

        StringBuilder result = new StringBuilder();

//        Map<Key, Parser> perms = permisser.getParsers();
//        for (Map.Entry<Key, Parser> keyParserEntry : perms.entrySet()) {
//            System.out.println(keyParserEntry.getKey() + " | " + keyParserEntry.getValue().getDescription());
//        }


        List<ParametricCallable> commands = new ArrayList<>(group.getParametricCallables(f -> true));
        // has any @Ignore annotation
        commands.removeIf(f -> f.getAnnotations().stream().anyMatch(a -> a.annotationType().equals(Config.Ignore.class)));
        commands.removeIf(f -> {
            // get RolePermission annotation f.getAnnotations()
            RolePermission ann = f.getAnnotations().stream().filter(a -> a.annotationType().equals(RolePermission.class)).map(a -> (RolePermission) a).findFirst().orElse(null);
            if (ann == null) return false;
            if (ann.root()) return true;
            return false;
        });
        Collections.sort(commands, (o1, o2) -> o1.getFullPath().compareTo(o2.getFullPath()));

        result.append(header);

        for (ParametricCallable command : commands) {
            result.append("## ").append(prefix).append(command.getFullPath()).append("\n");
            result.append(command.toBasicMarkdown(store, permisser, prefix, true));
            result.append("\n---\n\n");
        }

        return result.toString();
    }

    public static String keyName(Key key) {
        String keyStr = key.toSimpleString();
        return keyStr.replace("[", "\\[").replace("]", "\\]").replaceAll("([<|, ])([a-zA-Z_0-9]+)([>|, ])", "$1[$2](#$2)$3");
    }

    public static String printParsers(ValueStore store) {
        StringBuilder result = new StringBuilder();

        Map<Key, Parser> parsers = store.getParsers();
        List<Map.Entry<Key, Parser>> parsersList = new ArrayList<>(parsers.entrySet());
        // sort
        parsersList.sort((o1, o2) -> {
            // toString
            String o1Str = o1.getKey().toSimpleString();
            String o2Str = o2.getKey().toSimpleString();
            return o1Str.compareTo(o2Str);
        });

        for (Map.Entry<Key, Parser> entry : parsers.entrySet()) {
            Parser parser = entry.getValue();
            if (!parser.isConsumer(store)) continue;
            Key key = entry.getKey();
            result.append("## " + keyName(key) + "\n");
            Type type = key.getType();
            if (parser.getDescription().isEmpty()) {
                result.append("`No description provided`\n\n");
            } else {
                result.append(parser.getDescription() + "\n\n");
            }
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
                Binding binding = key.getBinding();
                if (binding.examples() != null && binding.examples().length > 0) {
                    result.append("Examples:\n");
                    for (String example : binding.examples()) {
                        result.append("- " + example + "\n");
                    }
                } else {
                    result.append("`No examples provided`\n\n");
                }
            }

            result.append("---\n");
        }

//
//        CommandGroup group = manager.getCommands();
//
//        for (ParametricCallable command : group.getParametricCallables(f -> true)) {
//            String help = command.help(manager.getStore());
//            System.out.println("\n\nCommand: " + command.getFullPath() + "\n" + help);
//            String desc = command.desc(manager.getStore());
//            System.out.println("Desc: " + desc);
//        }

        return result.toString();
    }
}
