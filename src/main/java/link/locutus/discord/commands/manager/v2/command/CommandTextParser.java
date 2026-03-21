package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.util.StringMan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static link.locutus.discord.util.StringMan.isQuote;

/**
 * Shared parsing helpers for command-text validation and named-argument parsing.
 */
public final class CommandTextParser {
    private CommandTextParser() {
    }

    public static Map<String, String> parseArguments(Set<String> params, String input, boolean checkUnbound) {
        Map<String, String> lowerCase = new LinkedHashMap<>();
        for (String param : params) {
            lowerCase.put(param.toLowerCase(), param);
        }

        Map<String, String> result = new LinkedHashMap<>();
        Pattern pattern = Pattern.compile("(?i)(^| |,)(" + String.join("|", lowerCase.keySet()) + "):[ ]{0,1}[^ ]");
        Matcher matcher = pattern.matcher(input);

        Pattern fuzzyArg = !checkUnbound ? null : Pattern.compile("(?i)[ ,]([a-zA-Z]+):[ ]{0,1}[^ ]");

        Map<String, Integer> argStart = new LinkedHashMap<>();
        Map<String, Integer> argEnd = new LinkedHashMap<>();
        String lastArg = null;
        while (matcher.find()) {
            String argName = matcher.group(2).toLowerCase();
            int index = matcher.end(2) + 1;
            Integer existing = argStart.put(argName, index);
            if (existing != null) {
                throw new IllegalArgumentException("Duplicate argument `" + argName + "` in `" + input + "`");
            }

            if (lastArg != null) {
                argEnd.put(lastArg, matcher.start(2) - 1);
            }
            lastArg = argName;
        }
        if (lastArg != null) {
            argEnd.put(lastArg, input.length());
        }

        for (Map.Entry<String, Integer> entry : argStart.entrySet()) {
            String id = entry.getKey();
            int start = entry.getValue();
            int end = argEnd.get(id);
            String value = input.substring(start, end).trim();
            boolean hasQuote = false;
            if (value.length() > 1 && isQuote(value.charAt(0)) && isQuote(value.charAt(value.length() - 1))) {
                value = value.substring(1, value.length() - 1);
                hasQuote = true;
            }

            if (fuzzyArg != null && !hasQuote) {
                Matcher valueMatcher = fuzzyArg.matcher(value);
                if (valueMatcher.find()) {
                    String fuzzyArgName = valueMatcher.group(1);
                    throw new IllegalArgumentException(
                            "Invalid argument: `" + fuzzyArgName + "` for `" + input + "` options: " + params + "\n"
                                    + "Please use quotes if you did not intend to specify an argument: `" + value + "`");
                }
            }
            result.put(lowerCase.get(id), value);
        }

        if (argStart.isEmpty()) {
            throw new IllegalArgumentException("No arguments found` for `" + input + "` options: " + params);
        }

        return result;
    }

    public static Map<String, String> validateSlashCommand(CommandCallable rootCallable, String input, boolean strict) {
        CommandCallable rootNode = rootCallable.getRoot();
        if (!(rootNode instanceof CommandGroup root)) {
            throw new IllegalArgumentException("Cannot validate slash command without a command-group root");
        }

        String original = input;
        while (true) {
            int index = input.indexOf(' ');
            if (index < 0) {
                throw new IllegalArgumentException(
                        "No parametric command found for " + original + " only found root: " + root.getFullPath());
            }
            String arg0 = input.substring(0, index);
            input = input.substring(index + 1).trim();

            CommandCallable next = root.get(arg0);
            if (next instanceof ParametricCallable<?> parametric) {
                if (!input.isEmpty()) {
                    return parseArguments(parametric.getUserParameterMap().keySet(), input, strict);
                }
                return new LinkedHashMap<>();
            }
            if (next instanceof CommandGroup group) {
                root = group;
                continue;
            }
            if (next == null) {
                throw new IllegalArgumentException("No parametric command found for " + original + " (" + arg0 + ")");
            }
            throw new UnsupportedOperationException("Invalid command class " + next.getClass());
        }
    }

    public static List<String> splitPath(String path) {
        return new ArrayList<>(StringMan.split(path, ' '));
    }
}