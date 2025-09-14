package link.locutus.wiki;

import com.google.common.base.Predicates;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Autocomplete;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.SelectorInfo;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.config.yaml.Config;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildSettingCategory;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ArrayUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class CommandWikiPages {

    public static String PLACEHOLDER_HEADER = """
This page contains a list of functions that can be used as PLACEHOLDERS and FILTERS

### About Arguments
Some functions here may accept an argument. The brackets imply argument type, you do NOT include them when using the function. 
 - `<arg>` - A required parameter
 - `[arg]` - An optional parameter
 - `<arg1|arg2>` - Multiple parameters options
 - `<arg=value>` - Default or suggested value
 - `[-f flag]` - A optional command argument flag

### About Placeholders
Placeholders in a text, such as in a spreadsheet or message, are replaced with actual values.

#### Examples:
- `{myFunction}`
- `{functionWithArgs(123)}`
- `{withNamedArgs(myArg: 123 otherArg: 456)}`
- `({conditional}?{ifTrue}:{ifFalse})`
- `9-({numericalFunction}+5)*3`

### About filters
Filters are used to modify a selection. i.e. When you are choosing which things to act upon or display, you use filters to narrow down the selection to ones that meet a certain requirement.
When the return type is a string, the filter can be compared using regex to the value.
When the return type is boolean (true/false), it will be resolved to either 1 or 0

#### Examples:
- `#myBoolean,#myOtherTrueFalseFunction`
- `(#myFunction=5||#myOtherFunction<10)`
- `#textFunction=abc123,#regexFunction=efg.*`
- `(#myFunction<(#myOtherFunction+5)`
""";

    public static String printPlaceholders(Placeholders<?, ?> placeholders, ValueStore store) {
        String operators = Arrays.stream(ArrayUtil.MathOperator.values()).map(f -> f.name() + ": `"+ f.getSymbol() + "`").reduce((a, b) -> a + "\n- " + b).orElse("");

        Set<SelectorInfo> selectors = placeholders.getSelectorInfo();
        Set<String> sheetColumns = placeholders.getSheetColumns(); // or null

        StringBuilder header = new StringBuilder(PLACEHOLDER_HEADER + "\n\n### Operators\n- " + operators + "\n\n");
        if (!selectors.isEmpty()) {
            header.append("### " + PlaceholdersMap.getClassName(placeholders.getType()) + " Selectors\n");
            for (SelectorInfo selector : selectors) {
                header.append("- `").append(selector.format()).append("`: " + selector.desc());
                if (selector.example() != null) {
                    header.append("\nExample: ");
                    if (selector.example().contains("`")) {
                        header.append(selector.example());
                    } else {
                        header.append("`").append(selector.example()).append("`");
                    }
                }
                header.append("\n");
            }
        }
        if (sheetColumns != null && !sheetColumns.isEmpty()) {
            header.append("\n\n### Sheet Columns\n");
            // join sheetColumn with ` wrapped
            header.append("A google sheet url with one of the following columns is accepted: " +
                    sheetColumns.stream().collect(Collectors.joining("`, `", "`", "`")) + "\n");
        }
        header.append("""

---

# Placeholders

---


""");
        return printCommands(placeholders.getCommands(), store, placeholders.getPermisser(), "\\#", header.toString(), true);
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


        List<ParametricCallable<?>> commands = new ArrayList<>(group.getParametricCallables(Predicates.alwaysTrue()));
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

        for (ParametricCallable<?> command : commands) {
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
            if (commandRefs.isEmpty()) commandRefs = List.of(CM.settings.info.cmd.key(setting.name()).value("<value>").toString());

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
