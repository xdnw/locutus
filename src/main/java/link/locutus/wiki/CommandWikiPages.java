package link.locutus.wiki;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.MethodParser;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Autocomplete;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.config.yaml.Config;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildSettingCategory;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import retrofit2.http.HEAD;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

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
        return printCommands(placeholders.getCommands(), store, placeholders.getPermisser(), "\\#", header, true);
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
        return printCommands(group, store, permisser, "/", header, false);
    }

    public static String printCommands(CommandGroup group, ValueStore store, PermissionHandler permisser, String prefix, String header, boolean printReturnType) {
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
            result.append("## ").append(prefix).append(command.getFullPath());
            if (printReturnType) {
                Type returnType = command.getReturnType();
                String keyName = Key.keyNameMarkdown(returnType.getTypeName());
                String url = "arguments#" + keyName;
                result.append(" - " + MarkupUtil.markdownUrl(keyName, url));
            }
            result.append("\n");
            result.append(command.toBasicMarkdown(store, permisser, prefix, true, true, false));
            result.append("\n---\n\n");
        }

        return result.toString();
    }

    public static String printSettings(List<GuildSetting> settings) {
        StringBuilder result = new StringBuilder();
        GuildSettingCategory cat = null;
        for (GuildSetting setting : settings) {
            if (cat != setting.getCategory()) {
                cat = setting.getCategory();
                result.append("## Category ").append(cat).append("\n\n");
            }
            Set<ParametricCallable> callables = setting.getCallables();
            List<String> commandRefs = callables.stream().map(c -> c.getSlashCommand(Collections.emptyMap())).toList();
            if (commandRefs.isEmpty()) commandRefs = List.of(CM.settings.info.cmd.create(setting.name(), "<value>", null).toString());

            List<String> requirementsStr = setting.getRequirementDesc();

            result.append("### `").append(setting.name()).append("`\n");
            result.append("type: `" + setting.getType().toSimpleString() + "`\n\n");
            result.append("category: `" + setting.getCategory() + "`\n\n");
            result.append("Desc:\n```\n" + setting.help() + "\n```\n\n");
            result.append("commands:\n- " + String.join("\n- ", commandRefs) + "\n\n");
            if (!requirementsStr.isEmpty()) {
                result.append("requirements:\n- " + String.join("\n- ", requirementsStr) + "\n\n");
            }
            result.append("\n\n---\n\n");
        }
        return result.toString();
    }

    private static String printPerms(Parser parser) {
        Annotation permAnnotation = parser.getKey().getAnnotations()[0];

        String typeUrlBase = "arguments";

        List<String> permValues = new ArrayList<>();
        Method[] methods = permAnnotation.annotationType().getDeclaredMethods();
        // sort the methods array
        Arrays.sort(methods, (o1, o2) -> o1.getName().compareTo(o2.getName()));

        for (Method permMeth : methods) {
            Object def = permMeth.getDefaultValue();
            Class<?> retType = permMeth.getReturnType();

            String simpleTypeName;
            String modifier = "";
            Class componentType;
            // if array
            if (retType.isArray()) {
                componentType = retType.getComponentType();
                simpleTypeName = componentType.getSimpleName();
                modifier = "\\[\\]";
            } else {
                componentType = retType;
                simpleTypeName = retType.getSimpleName();
            }
            simpleTypeName = MarkupUtil.markdownUrl(simpleTypeName + modifier, typeUrlBase + "#" + MarkupUtil.pathName(simpleTypeName.toLowerCase(Locale.ROOT)));
            String desc;
            // if componentType is enum, list options
            if (componentType.isEnum()) {
                List<String> enumValues = new ArrayList<>();
                for (Object enumConstant : componentType.getEnumConstants()) {
                    enumValues.add(((Enum<?>) enumConstant).name());
                }
                simpleTypeName += " | Options: " + String.join(", ", enumValues);
            }
            desc = "`" + permMeth.getName() + "`: " + simpleTypeName;

            Command comment = permMeth.getAnnotation(Command.class);
            if (comment != null) {
                // in markdown quote
                desc = "- " + desc + "\n> " + StringMan.join(comment.desc().split("\n"), "\n> ");
            }
            permValues.add(desc);
        }

        String title = "## " + permAnnotation.annotationType().getSimpleName().replaceFirst("(?i)permission", "");
        String body = parser.getDescription();
        if (!permValues.isEmpty()) {
            body += "\n\n**Arguments:**\n\n" + StringMan.join(permValues, "\n\n");
        }
        return title + "\n" + body;
    }

    private static List<Map.Entry<Key, Parser>> parsersSorted(ValueStore store) {
        Map<Key, Parser> parsers = store.getParsers();
        List<Map.Entry<Key, Parser>> parsersList = new ArrayList<>(parsers.entrySet());
        // sort
        parsersList.sort((o1, o2) -> {
            // toString
            String o1Str = o1.getKey().toSimpleString();
            String o2Str = o2.getKey().toSimpleString();
            return o1Str.compareTo(o2Str);
        });
        return parsersList;
    }


    public static String printPermissions(ValueStore store) {
        StringBuilder result = new StringBuilder();
        List<Map.Entry<Key, Parser>> parsersList = parsersSorted(store);

        for (Map.Entry<Key, Parser> entry : parsersList) {
            Parser parser = entry.getValue();
            result.append(printPerms(parser));
            result.append("\n\n---\n\n");
        }
        return result.toString();
    }

    public static String printParsers(ValueStore store) {
        StringBuilder result = new StringBuilder();
        List<Map.Entry<Key, Parser>> parsersList = parsersSorted(store);

        for (Map.Entry<Key, Parser> entry : parsersList) {
            Parser parser = entry.getValue();
            if (!parser.isConsumer(store)) continue;
            if (parser.getKey().getAnnotation(Autocomplete.class) != null) continue;
            result.append("## " + parser.getNameDescriptionAndExamples(true, true,false, true));
            result.append("---\n");
        }
        return result.toString();
    }
}
