package com.locutus.wiki;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.config.yaml.Config;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CommandWikiPages {

    public static String PLACEHOLDER_HEADER = """
## Placeholder Syntax

 - `<arg>` - A required parameter
 
 - `[arg]` - An optional parameter
 
 - `<arg1|arg2>` - Multiple parameters options
 
 - `<arg=value>` - Default or suggested value
 
 - `[-f flag]` - A optional command argument flag
 
 
## Example discord usage

For placeholders `{getLand}`

For filters `#getLand>30`

Use round brackets for arguments `#myFunction(123)`
""";

    public static String printPlaceholders(Placeholders placeholders, ValueStore store) {
        String header = PLACEHOLDER_HEADER + """

---

# Placeholders

---


""";
        return printCommands(placeholders.getCommands(), store, placeholders.getPermisser(), "\\#", header);
    }

    public static String printCommands(CommandGroup group, ValueStore store, PermissionHandler permisser) {
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

    public static String printCommands(CommandGroup group, ValueStore store, PermissionHandler permisser, String prefix, String header) {
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
            result.append(command.toBasicMarkdown(store, permisser, prefix, true, true));
            result.append("\n---\n\n");
        }

        return result.toString();
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

        for (Map.Entry<Key, Parser> entry : parsersList) {
            Parser parser = entry.getValue();
            if (!parser.isConsumer(store)) continue;
            result.append("## " + parser.getNameDescriptionAndExamples(true, true,true, true));
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
