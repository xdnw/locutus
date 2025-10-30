package link.locutus.discord.util;

import com.google.common.base.Predicates;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.opencsv.CSVWriter;
import info.debatty.java.stringsimilarity.CharacterSubstitutionInterface;
import info.debatty.java.stringsimilarity.WeightedLevenshtein;
import info.debatty.java.stringsimilarity.experimental.Sift4;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.util.scheduler.KeyValue;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class StringMan {

    public static String stripApiKey(String msg) {
        return msg
                .replaceAll("(?i)[\\[\\]\"\\n^:\\s,\\.](?=.*[A-Za-z])(?=.*\\d)[0-9A-Fa-f]{14,}(?=[\\[\\]\"\\n$:\\s,\\.]|$)", "XXX")
                .replaceAll("(key=)(?i)([0-9A-Fa-f]{14,})", "$1XXX");
    }

    public static String toCsv(List<List<String>> rows) {
        StringWriter stringWriter = new StringWriter();
        CSVWriter csvWriter = new CSVWriter(stringWriter);

        for (List<String> row : rows) {
            csvWriter.writeNext(row.toArray(new String[0]));
        }
        return stringWriter.toString();
    }

    public static class ConsoleColors {
        // Reset
        public static final String RESET = "\033[0m"; // Text Reset // Regular Colors
        public static final String BLACK = "\033[0;30m"; // BLACK
        public static final String RED = "\033[0;31m"; // RED
        public static final String GREEN = "\033[0;32m"; // GREEN
        public static final String YELLOW = "\033[0;33m"; // YELLOW
        public static final String BLUE = "\033[0;34m"; // BLUE
        public static final String PURPLE = "\033[0;35m"; // PURPLE
        public static final String CYAN = "\033[0;36m"; // CYAN
        public static final String WHITE = "\033[0;37m"; // WHITE // Bold
        public static final String BLACK_BOLD = "\033[1;30m"; // BLACK
        public static final String RED_BOLD = "\033[1;31m"; // RED
        public static final String GREEN_BOLD = "\033[1;32m"; // GREEN
        public static final String YELLOW_BOLD = "\033[1;33m"; // YELLOW
        public static final String BLUE_BOLD = "\033[1;34m"; // BLUE
        public static final String PURPLE_BOLD = "\033[1;35m"; // PURPLE
        public static final String CYAN_BOLD = "\033[1;36m"; // CYAN
        public static final String WHITE_BOLD = "\033[1;37m"; // WHITE // Underline
        public static final String BLACK_UNDERLINED = "\033[4;30m"; // BLACK
        public static final String RED_UNDERLINED = "\033[4;31m"; // RED
        public static final String GREEN_UNDERLINED = "\033[4;32m"; // GREEN
        public static final String YELLOW_UNDERLINED = "\033[4;33m"; // YELLOW
        public static final String BLUE_UNDERLINED = "\033[4;34m"; // BLUE
        public static final String PURPLE_UNDERLINED = "\033[4;35m"; // PURPLE
        public static final String CYAN_UNDERLINED = "\033[4;36m"; // CYAN
        public static final String WHITE_UNDERLINED = "\033[4;37m"; // WHITE // Background
        public static final String BLACK_BACKGROUND = "\033[40m"; // BLACK
        public static final String RED_BACKGROUND = "\033[41m"; // RED
        public static final String GREEN_BACKGROUND = "\033[42m"; // GREEN
        public static final String YELLOW_BACKGROUND = "\033[43m"; // YELLOW
        public static final String BLUE_BACKGROUND = "\033[44m"; // BLUE
        public static final String PURPLE_BACKGROUND = "\033[45m"; // PURPLE
        public static final String CYAN_BACKGROUND = "\033[46m"; // CYAN
        public static final String WHITE_BACKGROUND = "\033[47m"; // WHITE // High Intensity
        public static final String BLACK_BRIGHT = "\033[0;90m"; // BLACK
        public static final String RED_BRIGHT = "\033[0;91m"; // RED
        public static final String GREEN_BRIGHT = "\033[0;92m"; // GREEN
        public static final String YELLOW_BRIGHT = "\033[0;93m"; // YELLOW
        public static final String BLUE_BRIGHT = "\033[0;94m"; // BLUE
        public static final String PURPLE_BRIGHT = "\033[0;95m"; // PURPLE
        public static final String CYAN_BRIGHT = "\033[0;96m"; // CYAN
        public static final String WHITE_BRIGHT = "\033[0;97m"; // WHITE // Bold High Intensity
        public static final String BLACK_BOLD_BRIGHT = "\033[1;90m"; // BLACK
        public static final String RED_BOLD_BRIGHT = "\033[1;91m"; // RED
        public static final String GREEN_BOLD_BRIGHT = "\033[1;92m"; // GREEN
        public static final String YELLOW_BOLD_BRIGHT = "\033[1;93m";// YELLOW
        public static final String BLUE_BOLD_BRIGHT = "\033[1;94m"; // BLUE
        public static final String PURPLE_BOLD_BRIGHT = "\033[1;95m";// PURPLE
        public static final String CYAN_BOLD_BRIGHT = "\033[1;96m"; // CYAN
        public static final String WHITE_BOLD_BRIGHT = "\033[1;97m"; // WHITE // High Intensity backgrounds
        public static final String BLACK_BACKGROUND_BRIGHT = "\033[0;100m";// BLACK
        public static final String RED_BACKGROUND_BRIGHT = "\033[0;101m";// RED
        public static final String GREEN_BACKGROUND_BRIGHT = "\033[0;102m";// GREEN
        public static final String YELLOW_BACKGROUND_BRIGHT = "\033[0;103m";// YELLOW
        public static final String BLUE_BACKGROUND_BRIGHT = "\033[0;104m";// BLUE
        public static final String PURPLE_BACKGROUND_BRIGHT = "\033[0;105m"; // PURPLE
        public static final String CYAN_BACKGROUND_BRIGHT = "\033[0;106m"; // CYAN
        public static final String WHITE_BACKGROUND_BRIGHT = "\033[0;107m"; // WHITE
    }

    public static Matcher match(String input, String regex) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(input);
        if (m.find()) {
            return m;
        }
        return null;
    }

    public static String formatJsonLikeText(String input) {
        int indentationLevel = 0;
        StringBuilder formattedText = new StringBuilder();

        for (char c : input.toCharArray()) {
            if (c == '{' || c == '[') {
                formattedText.append("\n").append(indentSpaces(indentationLevel));
                formattedText.append(c).append("\n");
                indentationLevel++;
                formattedText.append(indentSpaces(indentationLevel));
            } else if (c == '}' || c == ']') {
                indentationLevel--;
                formattedText.append("\n").append(indentSpaces(indentationLevel));
                formattedText.append(c);
            } else {
                formattedText.append(c);
            }
        }

        return formattedText.toString();
    }

    public static String normalize(String text) {
        text = Normalizer.normalize(text, Normalizer.Form.NFKD);
        return text.replaceAll("[^a-zA-Z0-9\\.\\-_ ]", "");
    }

    public static String indentSpaces(int count) {
        StringBuilder spaces = new StringBuilder();
        // Using 4 spaces for each level of indentation
        spaces.append(" ".repeat(Math.max(0, count * 4)));
        return spaces.toString();
    }

    public static byte[] getDiffBytes(String a, String b) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(out);

        DiffMatchPatch dmp = new DiffMatchPatch();
        List<DiffMatchPatch.Diff> diffs = dmp.diffMain(a, b, false);
        Integer lastEqual = null;
        for (DiffMatchPatch.Diff diff : diffs) {
            if (lastEqual != null) {
                dos.writeByte(DiffMatchPatch.Operation.EQUAL.ordinal());
                IOUtil.writeVarInt(out, lastEqual);
                lastEqual = null;
            }
            switch (diff.operation) {
                case EQUAL:
                    lastEqual = diff.text.length();
                    break;
                case DELETE:
                    dos.writeByte(diff.operation.ordinal());
                    IOUtil.writeVarInt(out, diff.text.length());
                    break;
                case INSERT:
                    dos.writeByte(diff.operation.ordinal());
                    IOUtil.writeVarInt(out, diff.text.length());
                    dos.writeChars(diff.text);
                    break;
            }
        }
        return out.toByteArray();
    }

    public static String getDiffVariant(String a, byte[] diffBytes) {
        StringBuilder out = new StringBuilder(a);

        ByteArrayInputStream in = new ByteArrayInputStream(diffBytes);
        DataInputStream din = new DataInputStream(in);
        DiffMatchPatch.Operation[] types = DiffMatchPatch.Operation.values();

        try {
            int i = 0;
            while (in.available() > 0) {
                DiffMatchPatch.Operation type = types[in.read()];
                int len = IOUtil.readVarInt(in);
                switch (type) {
                    case DELETE:
                        out = out.replace(i, len + i, "");
                        break;
                    case INSERT:
                        char[] chars = new char[len];
                        for (int j = 0; j < len; j++) chars[j] = din.readChar();
                        String text = new String(chars);
                        out = out.insert(i, text);
                        i += len;
                        break;
                    case EQUAL:
                        i+= len;
                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return out.toString();
    }

    private static List<String[]> getReplacementString(String announcement, List<String> replacements) {
        List<String[]> replacementsSplit = new ArrayList<>();

        for (int i = 0; i < replacements.size(); i++) {
            String replacement = replacements.get(i);
            String[] split = replacement.split("\\|");
            if (split.length < 2) throw new IllegalArgumentException("Term `" + split[0] + "` has no replacement options " + " | `" + replacement + "`  (" + StringMan.getString(split) + ")");
            String search = split[0];
            if (search.isEmpty()) throw new IllegalArgumentException("Search term " + i + " is empty");
            if (announcement.indexOf(search) <= 0) throw new IllegalArgumentException("No match found for `" + search + "`");

            replacementsSplit.add(split);
        }

        return replacementsSplit;
    }

    public static String getReplacementIndex(String announcement, List<String> replacements, int requiredResults, int requiredDiff, int requiredDepth, int index) {
        if (requiredDepth >= replacements.size()) {
            throw new IllegalArgumentException("Depth is too high");
        }
        List<String[]> replacementsSplit = getReplacementString(announcement, replacements);
        Set<String> results = new ObjectLinkedOpenHashSet<>();
        replace(announcement, replacementsSplit, 0, 0, 0, -2, 0, requiredDiff, requiredDepth, requiredResults, results, 0, i -> i == index);
        if (results.isEmpty()) return null;
        return results.iterator().next();
    }

    public static Set<String> enumerateReplacements(String announcement, List<String> replacements, int requiredResults, int requiredDiff, int requiredDepth) {
        if (requiredDepth >= replacements.size()) {
            throw new IllegalArgumentException("Depth is too high");
        }
        List<String[]> replacementsSplit = getReplacementString(announcement, replacements);
        Set<String> results = new ObjectLinkedOpenHashSet<>();
        replace(announcement, replacementsSplit, 0, 0, 0, -2, 0, requiredDiff, requiredDepth, requiredResults, results, 0, Predicates.alwaysTrue());
        return results;
    }

    private static void replace(String announcement, List<String[]> replacements, int lastIndexAdded, int globalIndex, int replacementsIndex, int foundIndex, int depth, int requiredDiff, int requiredDepth, int requiredResults, Set<String> results, int currentIndex, Predicate<Integer> allowIndex) {
        if (replacementsIndex >= replacements.size()) return;
        String[] currentReplacements = replacements.get(replacementsIndex);
        String search = currentReplacements[0];

        foundIndex = announcement.indexOf(search, foundIndex + 1);
        if (foundIndex < 0) {
            if (requiredDiff <= 0 && requiredDepth <= depth && allowIndex.test(currentIndex)) {
                results.add(announcement);
            }
            replace(announcement, replacements, lastIndexAdded, globalIndex, replacementsIndex + 1, -1, depth + 1, requiredDiff, requiredDepth, requiredResults, results, currentIndex + 1, allowIndex);
            return;
        }

        for (int k = 0; k < currentReplacements.length; k++) {
            String replacement = currentReplacements[k];

            String replaced = announcement;
            if (k > 0 && allowIndex.test(currentIndex)) {
                replaced = announcement.substring(0, foundIndex) + replacement + announcement.substring(foundIndex + search.length());

                if (requiredDepth >= depth && (requiredDiff <= 1 || globalIndex - lastIndexAdded >= requiredDiff)) {
                    currentIndex++;
                    lastIndexAdded = globalIndex;
                    results.add(replaced);

                    if (results.size() >= requiredResults) return;
                }
            }

            replace(replaced, replacements, lastIndexAdded, globalIndex + (k > 0 ? 1 : 0), replacementsIndex, foundIndex + replacement.length(), depth, requiredDiff, requiredDepth, requiredResults, results, currentIndex + 1, allowIndex);
        }
    }

    public static <T extends Enum<T>> List<T> parseEnumList(Class<T> clazz, String input) {
        if (input.equalsIgnoreCase("*")) {
            return new ArrayList<>(Arrays.asList(clazz.getEnumConstants()));
        }
        List<T> result = new ArrayList<>();
        for (String arg : input.split(",")) {
            T t = Enum.valueOf(clazz, arg);
            result.add(t);
        }
        return result;
    }

    public static String stacktraceToString(StackTraceElement[] elems) {
        return stacktraceToString(elems, Integer.MAX_VALUE, Predicates.alwaysTrue());
    }

    public static String stacktraceToString(StackTraceElement[] elems, int maxLines, Predicate<String> allowLine) {
        StringBuilder body = new StringBuilder();
        int len = 0;
        for (StackTraceElement elem : elems) {
            String line = elem.toString();
            if (!allowLine.test(line)) continue;
            if (len++ >= maxLines) break;
            body.append(line).append("\n");
        }
        return body.toString().trim();
    }

    public static Map.Entry<String, String> stacktraceToString(Throwable e) {
        return stacktraceToString(e, Integer.MAX_VALUE, Predicates.alwaysTrue());
    }

    public static Map.Entry<String, String> stacktraceToString(Throwable e, int maxLines, Predicate<String> allowLine) {
        String title = e.getMessage();
        String body = stacktraceToString(e.getStackTrace(), maxLines, allowLine);
        return new KeyValue<>(title, body);
    }

    public static String escapeUnicode(String input) {
        StringBuilder b = new StringBuilder(input.length());
        Formatter f = new Formatter(b);
        for (char c : input.toCharArray()) {
            if (c < 128) {
                b.append(c);
            } else {
                f.format("\\u%04x", (int) c);
            }
        }
        return b.toString();
    }

    public static boolean containsAny(CharSequence sequence, String any) {
        return IntStream.range(0, sequence.length())
                .anyMatch(i -> any.indexOf(sequence.charAt(i)) != -1);
    }

    public static boolean containsIgnoreCase(String haystack, String needle) {
        final int length = needle.length();
        if (length == 0)
            return true; // Empty string is contained

        final char firstLo = Character.toLowerCase(needle.charAt(0));
        final char firstUp = Character.toUpperCase(needle.charAt(0));

        for (int i = haystack.length() - length; i >= 0; i--) {
            // Quick check before calling the more expensive regionMatches() method:
            final char ch = haystack.charAt(i);
            if (ch != firstLo && ch != firstUp)
                continue;

            if (haystack.regionMatches(true, i, needle, 0, length))
                return true;
        }

        return false;
    }

    public static String abbreviate(String arg, char delim) {
        String[] myName = arg.split(delim + "");
        StringBuilder result = new StringBuilder();
        for (String s : myName) {
            if (s.length() != 0 && s.charAt(0) != delim) result.append(s.charAt(0));
        }
        return result.toString();
    }

    public static String humanReadableFormat(Duration duration) {
        return duration.toString()
                .substring(2)
                .replaceAll("(\\d[HMS])(?!$)", "$1 ")
                .toLowerCase();
    }

    public static String wrapHashFunctions(String str, Predicate<String> isValidFunction) {
        StringBuilder result = new StringBuilder();
        boolean inFunction = false;
        StringBuilder function = new StringBuilder();

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (inFunction) {
                if (c == '(') {
                    int matchingBracketIndex = StringMan.findMatchingBracket(str, i);
                    if (matchingBracketIndex != -1) {
                        String functionName = function.toString();
                        if (isValidFunction.test(functionName.split("\\.")[0])) {
                            String formatted = wrapHashFunctions(str.substring(i, matchingBracketIndex + 1), isValidFunction);
                            function.append(formatted);
                            i = matchingBracketIndex;
                            inFunction = false;
                            result.append('{').append(function).append('}');
                            function.setLength(0);
                        } else {
                            result.append('#').append(function).append(str.substring(i, matchingBracketIndex + 1));
                            i = matchingBracketIndex;
                            inFunction = false;
                            function.setLength(0);
                        }
                        continue;
                    }
                }
                if (!(Character.isLetterOrDigit(c) || c == '_' || c == '.')) {
                    inFunction = false;
                    if (isValidFunction.test(function.toString())) {
                        result.append('{').append(function).append('}');
                    } else {
                        result.append('#').append(function);
                    }
                    function.setLength(0);
                    if (c == '#') {
                        inFunction = true;
                        continue;
                    }
                } else {
                    function.append(c);
                    continue;
                }
            } else {
                if (c == '#' && i + 1 < str.length() && Character.isLetter(str.charAt(i + 1))) {
                    inFunction = true;
                    continue;
                }
            }
            result.append(c);
        }
        if (inFunction) {
            if (isValidFunction.test(function.toString())) {
                result.append('{').append(function).append('}');
            } else {
                result.append('#').append(function);
            }
        }
        return result.toString();
    }

    public static int findMatchingBracket(CharSequence sequence, int index) {
        char startC = sequence.charAt(index);
        char lookC = getMatchingBracket(startC);
        if (lookC == startC) return -1;
        boolean forward = isBracketForwards(startC);
        int increment = forward ? 1 : -1;
        int end = forward ? sequence.length() : -1;
        Function<Integer, Boolean> incrementTest = forward ? i -> i < end : i -> i > end;
        int count = 0;
        for (int i = index + increment; incrementTest.apply(i); i += increment) {
            char c = sequence.charAt(i);
            if (c == startC) {
                count++;
            } else if (c == lookC && count-- == 0) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isBracketForwards(char c) {
        switch (c) {
            case '[':
            case '(':
            case '{':
            case '<':
                return true;
            default:
                return false;
        }
    }

    public static boolean isBracketEnding(char c) {
        switch (c) {
            case ']':
            case ')':
            case '}':
            case '>':
                return true;
            default:
                return false;
        }
    }

    public static String prettyFormat(double d) {
        if (d == Double.MIN_VALUE) return "-∞";
        if (d == Double.MAX_VALUE) return "∞";
        if (d == (long) d) return String.format("%d", (long) d);
        else return String.format("%s", d);
    }

    public static char getMatchingBracket(char c) {
        switch (c) {
            case '[':
                return ']';
            case '(':
                return ')';
            case '{':
                return '}';
            case '<':
                return '>';
            case ']':
                return '[';
            case ')':
                return '(';
            case '}':
                return '{';
            case '>':
                return '<';
            default:
                return c;
        }
    }

    public static int parseInt(CharSequence string) {
        int val = 0;
        boolean neg = false;
        int numIndex = 1;
        int len = string.length();
        for (int i = len - 1; i >= 0; i--) {
            char c = string.charAt(i);
            if (c == '-') {
                val = -val;
            } else {
                val = val + (c - 48) * numIndex;
                numIndex *= 10;
            }
        }
        return val;
    }

    public static String removeFromSet(String string, Collection<String> replacements) {
        final StringBuilder sb = new StringBuilder(string);
        int size = string.length();
        for (String key : replacements) {
            if (size == 0) {
                break;
            }
            int start = sb.indexOf(key, 0);
            while (start > -1) {
                final int end = start + key.length();
                final int nextSearchStart = start;
                sb.delete(start, end);
                size -= end - start;
                start = sb.indexOf(key, nextSearchStart);
            }
        }
        return sb.toString();
    }

    public static int indexOf(String input, int start, char... values) {
        for (int i = start; i < input.length(); i++) {
            for (char c : values) {
                if (c == input.charAt(i)) return i;
            }
        }
        return -1;
    }

    public static String toProperCase(String s) {
        return s.substring(0, 1).toUpperCase() +
                s.substring(1);
    }

    public static boolean isQuote(char c) {
        switch (c) {
            case '\'':
            case '"':
            case '\u201C':
            case '\u201D':
                return true;
            default:
                return false;
        }
    }

    public static String joinAndQuote(List<String> input, String separator) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.size(); i++) {
            String s = input.get(i);
            if (s.contains(separator)) {
                result.append("\u201C").append(s).append("\u201D");
            } else {
                result.append(s);
            }
            if (i < input.size() - 1) {
                result.append(separator);
            }
        }
        return result.toString();
    }

    public static List<String> split(String input, char delim) {
        return split(input, Character.toString(delim));
    }

    public static List<String> split(String input, String delim) {
        return split(input, delim, Integer.MAX_VALUE);
    }

    public static List<String> split(String input, String delim, int limit) {
        if (delim.isEmpty()) {
            throw new IllegalArgumentException("The delimiter cannot be the empty string.");
        }
        if (delim.length() > input.length() || limit <= 1) {
            return Collections.singletonList(input);
        }
        return split(input, (str, fromIndex) -> str.startsWith(delim, fromIndex) ? delim.length() : null, limit);
    }

    public static List<String> split(String input, BiFunction<String, Integer, Integer> startsWith, int limit) {
        List<String> result = new ArrayList<>();
        int start = 0;
        int bracket = 0;
        List<Character> findBracket = new ArrayList<>();
        boolean inQuotes = false;
        char quoteChar = 0;
        for (int current = 0; current < input.length(); current++) {
            char currentChar = input.charAt(current);
            if (currentChar == '\u201C') currentChar = '\u201D';
            boolean atLastChar = current == input.length() - 1;
            switch (currentChar) {
                case '[':
                case '(':
                case '{':
                    bracket++;
                    break;
                case '}':
                case ')':
                case ']':
                    bracket--;
                    break;
            }
            if (!atLastChar && bracket > 0) {
                continue;
            }
            if (atLastChar) {
                String toAdd;
                if (inQuotes) {
                    toAdd = input.substring(start + 1, input.length() - 1);
                } else {
                    toAdd = input.substring(start);
                }
                if (!toAdd.isEmpty()) result.add(toAdd);
                continue;
            }
            if (isQuote(currentChar)) {
                if (quoteChar == 0 || (isQuote(quoteChar) && isQuote(currentChar) && currentChar == quoteChar)) {
                    inQuotes = !inQuotes;
                    quoteChar = inQuotes ? currentChar : 0;
                }
            }

            if (!inQuotes && startsWith != null) {
                Integer foundLen = startsWith.apply(input, current);
                if (foundLen != null && foundLen != -1) {
                    String toAdd = input.substring(start, current);
                    if (!toAdd.isEmpty()) {
                        switch (toAdd.charAt(0)) {
                            case '\'':
                            case '\"':
                            case '\u201C':
                            case '\u201D':
                                toAdd = toAdd.substring(1, toAdd.length() - 1);
                        }
                        if (!toAdd.trim().isEmpty()) result.add(toAdd);
                    }
                    start = current + foundLen;
                    current = start - 1;
                    if (--limit <= 1) {
                        startsWith = null;
                    }
                }
            }
        }
        return result;
    }

    public static int intersection(Set<String> options, String[] toCheck) {
        int count = 0;
        for (String check : toCheck) {
            if (options.contains(check)) {
                count++;
            }
        }
        return count;
    }

    public static String padRight(String s, int n) {
        return String.format("%1$-" + n + "s", s);
    }

    public static String padLeft(String s, int n) {
        return String.format("%1$" + n + "s", s);
    }

    public static boolean areEqual(Object obj1, Object obj2) {
        if (obj1 == obj2) {
            return true;
        }
        if (obj1 == null || obj2 == null) {
            return false;
        }
        return switch (obj1) {
            case int[] a when obj2 instanceof int[] b -> Arrays.equals(a, b);
            case long[] a when obj2 instanceof long[] b -> Arrays.equals(a, b);
            case short[] a when obj2 instanceof short[] b -> Arrays.equals(a, b);
            case byte[] a when obj2 instanceof byte[] b -> Arrays.equals(a, b);
            case char[] a when obj2 instanceof char[] b -> Arrays.equals(a, b);
            case float[] a when obj2 instanceof float[] b -> Arrays.equals(a, b);
            case double[] a when obj2 instanceof double[] b -> Arrays.equals(a, b);
            case boolean[] a when obj2 instanceof boolean[] b -> Arrays.equals(a, b);
            case Object[] a when obj2 instanceof Object[] b -> Arrays.deepEquals(a, b);
            default -> Objects.equals(obj1, obj2);
        };
    }

    public static JsonElement toJson(Object obj) {
        if (obj == null) {
            return JsonNull.INSTANCE;
        }
        if (obj instanceof JsonElement) {
            return (JsonElement) obj;
        }
        if (obj.getClass() == String.class) {
            return new JsonPrimitive((String) obj);
        }
        if (obj instanceof Enum<?>) {
            return new JsonPrimitive(((Enum<?>) obj).name());
        }
        if (obj.getClass().isArray()) {
            JsonArray arr = new JsonArray();
            for (int i = 0; i < Array.getLength(obj); i++) {
                arr.add(toJson(Array.get(obj, i)));
            }
            return arr;
        } else if (obj instanceof Collection<?>) {
            JsonArray arr = new JsonArray();
            for (Object element : (Collection<?>) obj) {
                arr.add(toJson(element));
            }
            return arr;
        } else if (obj instanceof Map) {
            JsonObject result = new JsonObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                result.add(entry.getKey().toString(), toJson(entry.getValue()));
            }
            return result;
        } else if (obj instanceof Class) {
            return new JsonPrimitive(((Class<?>) obj).getSimpleName());
        } else if (obj instanceof Number) {
            return new JsonPrimitive((Number) obj);
        } else if (obj instanceof Boolean) {
            return new JsonPrimitive((Boolean) obj);
        } else {
            return new JsonPrimitive(obj.toString());
        }
    }

    public static Object toSerializable(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj.getClass() == String.class) {
            return obj.toString();
        }
        if (obj instanceof Enum<?> emum) {
            return emum.name();
        }
        if (obj.getClass().isArray()) {
            List<Object> arr = new ObjectArrayList<>();
            for (int i = 0; i < Array.getLength(obj); i++) {
                arr.add(toSerializable(Array.get(obj, i)));
            }
            return arr;
        } else if (obj instanceof Collection<?>) {
            List<Object> arr = new ObjectArrayList<>();
            for (Object element : (Collection<?>) obj) {
                arr.add(toSerializable(element));
            }
            return arr;
        } else if (obj instanceof Map) {
            Map<String, Object> result = new Object2ObjectLinkedOpenHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                result.put(entry.getKey().toString(), toSerializable(entry.getValue()));
            }
            return result;
        } else if (obj instanceof Class) {
            return ((Class<?>) obj).getSimpleName();
        } else if (obj instanceof Number) {
            return (Number) obj;
        } else if (obj instanceof Boolean) {
            return (Boolean) obj;
        } else {
            return obj.toString();
        }
    }

    public static String getString(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj.getClass() == String.class) {
            return (String) obj;
        }
        if (obj instanceof Enum<?>) {
            return ((Enum<?>) obj).name();
        }
        if (obj.getClass().isArray()) {
            StringBuilder result = new StringBuilder();
            String prefix = "";

            for (int i = 0; i < Array.getLength(obj); i++) {
                result.append(prefix).append(getString(Array.get(obj, i)));
                prefix = ",";
            }
            return "{ " + result + " }";
        } else if (obj instanceof Collection<?>) {
            StringBuilder result = new StringBuilder();
            String prefix = "";
            for (Object element : (Collection<?>) obj) {
                result.append(prefix).append(getString(element));
                prefix = ",";
            }
            return "( " + result + " )";
        } else {
            return obj.toString();
        }
    }

    public static String replaceFirst(char c, String s) {
        if (s == null) {
            return "";
        }
        if (s.isEmpty()) {
            return s;
        }
        char[] chars = s.toCharArray();
        final char[] newChars = new char[chars.length];
        int used = 0;
        boolean found = false;
        for (char cc : chars) {
            if (!found && c == cc) {
                found = true;
            } else {
                newChars[used++] = cc;
            }
        }
        if (found) {
            chars = new char[newChars.length - 1];
            System.arraycopy(newChars, 0, chars, 0, chars.length);
            return String.valueOf(chars);
        }
        return s;
    }

    public static String replaceAll(String string, Object... pairs) {
        final StringBuilder sb = new StringBuilder(string);
        for (int i = 0; i < pairs.length; i += 2) {
            final String key = pairs[i] + "";
            final String value = pairs[i + 1] + "";
            int start = sb.indexOf(key, 0);
            while (start > -1) {
                final int end = start + key.length();
                final int nextSearchStart = start + value.length();
                sb.replace(start, end, value);
                start = sb.indexOf(key, nextSearchStart);
            }
        }
        return sb.toString();
    }

    public static boolean isAlphanumeric(String str) {
        for (int i = 0; i < str.length(); i++) {
            final char c = str.charAt(i);
            if (c < 0x30 || c >= 0x3a && c <= 0x40 || c > 0x5a && c <= 0x60 ||
                    c > 0x7a) {
                return false;
            }
        }
        return true;
    }

    public static boolean isAlphanumericUnd(CharSequence str) {
        for (int i = 0; i < str.length(); i++) {
            final char c = str.charAt(i);
            if (c < 0x30 || c >= 0x3a && c <= 0x40 || c > 0x5a && c <= 0x60 || c > 0x7a) {
                return false;
            }
        }
        return true;
    }

    public static boolean isAlpha(String str) {
        for (int i = 0; i < str.length(); i++) {
            final char c = str.charAt(i);
            if (c <= 0x40 || c > 0x5a && c <= 0x60 || c > 0x7a) {
                return false;
            }
        }
        return true;
    }

    public static String join(Collection<?> collection, String delimiter) {
        return join(collection.toArray(), delimiter);
    }

    public static String joinOrdered(Collection<?> collection, String delimiter) {
        final Object[] array = collection.toArray();
        Arrays.sort(array, Comparator.comparingInt(Object::hashCode));
        return join(array, delimiter);
    }

    public static String join(Collection<?> collection, char delimiter) {
        return join(collection.toArray(), delimiter + "");
    }

    public static boolean isAsciiPrintable(char c) {
        return c >= ' ' && c < '';
    }

    public static boolean isAsciiPrintable(String s) {
        for (char c : s.toCharArray()) {
            if (!isAsciiPrintable(c)) {
                return false;
            }
        }
        return true;
    }

    public static Comparator<String> blockStateComparator(String input) {
        return Comparator.comparingInt(o -> blockStateStringDistance(input, o));
    }

    public static boolean blockStateMatches(String input, String item) {
        return blockStateStringDistance(input, item) != Integer.MAX_VALUE;
    }

    public static List<String> getClosest(String input, List<String> options, int maxResults, boolean requireContains) {
        return getClosest(input, options, f -> f, maxResults, requireContains);
    }

    public static <T> List<Map.Entry<String, String>> autocompletePairs(List<T> options, Function<T, String> keyFunc, Function<T, String> valueFunc) {
        List<Map.Entry<String, String>> result = new ArrayList<>(options.size());
        for (T option : options) {
            String key = keyFunc.apply(option);
            String value = valueFunc.apply(option);
            result.add(new KeyValue<>(key, value));
        }
        return result;
    }
    public static <T extends Enum> T parseUpper(Class<T> emum, String input) {
        try {
            return (T) Enum.valueOf(emum, input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage() + ". Valid options are: " + StringMan.getString(emum.getEnumConstants()));
        }
    }
    public static <T extends Enum<?>> List<Map.Entry<String, String>> autocompleteCommaEnum(Class<T> type, String input, int maxResults) {
        Function<String, T> parse = f -> parseUpper(type, f);
        return autocompleteCommaEnum(type, input, parse, maxResults);
    }

    public static <T extends Enum>  List<Map.Entry<String, String>> autocompleteCommaEnum(Class<T> type, String input, Function<String, T> parse, int maxResults) {
        List<T> options = new ArrayList<>(Arrays.asList(type.getEnumConstants()));
        Function<T, String> keyFunc = T::name;
        Function<T, String> valueFunc = keyFunc;
        Function<String, T> parent = parse;
        return autocompleteComma(input, options, parse, keyFunc, valueFunc, maxResults);
    }

    public static List<String> autocompleteComma(String input, List<String> options, int maxResults) {
        return autocompleteComma(input, options, f -> f, f -> f, f -> f, maxResults).stream().map(Map.Entry::getKey).toList();
    }

    public static <T>  List<Map.Entry<String, String>> autocompleteComma(String input, List<T> options, Function<String, T> parse, Function<T, String> keyFunc, Function<T, String> valueFunc, int maxResults) {
        List<T> optionsOriginal = options;

        input = input.trim();
        String[] split = input.split(",");
        int last = input.lastIndexOf(',');
        String trailing = last != -1 ? input.substring(last + 1) : input;

        List<T> prefixArgs = new ArrayList<>();
        int len = input.endsWith(",") ? split.length : split.length - 1;
        for (int i = 0; i < len; i++) {
            String arg = split[i];
            T parsed = parse.apply(arg);
            if (options == optionsOriginal) options = new ArrayList<>(options);
            options.remove(parsed);
            prefixArgs.add(parsed);
        }
        String prefixEnd = prefixArgs.isEmpty() ? "" : ",";
        String prefixKey = prefixArgs.stream().map(keyFunc).collect(Collectors.joining(",")) + prefixEnd;

        options = getClosest(trailing, options, true);
        if (options.size() > maxResults) {
            options = options.subList(0, maxResults);
        }

        List<Map.Entry<String, String>> results = new ArrayList<>();
        for (T option : options) {
            String key = prefixKey + keyFunc.apply(option);
            String value = prefixKey + valueFunc.apply(option);
            results.add(new KeyValue<>(key, value));
        }
        return results;
    }

    public static <T> List<String> completeMap(List<T> keys, Function<T, String> toString, String input) {
        if (toString == null) toString = Object::toString;
        input = input.trim();
        if (input.isEmpty()) {
            return Collections.singletonList("{");
        }
        int lastComma = input.lastIndexOf(',');
        if (lastComma == -1) lastComma = input.lastIndexOf('{');
        if (lastComma == -1) return null;

        String prefix;
        List<String> options = new ArrayList<>();

        int lastEqual = input.lastIndexOf('=');
        if (lastComma == input.length() - 1) {
            prefix = input;
            Set<String> optionsIgnore = new HashSet<>();
            for (String existing : input.substring(1).split(",")) {
                existing = existing.split("=")[0].trim().toLowerCase(Locale.ROOT);
                optionsIgnore.add(existing);
            }
            for (T key : keys) {
                String keyStr = toString.apply(key);
                if (optionsIgnore.contains(keyStr.toLowerCase(Locale.ROOT))) continue;
                options.add(keyStr + "=");
            }
        } else if (lastEqual == input.length() - 1) {
            prefix = input;
            options = new ArrayList<>(Collections.singletonList("0"));
        } else if (lastComma < lastEqual) {
            prefix = input;
            options = new ArrayList<>(Collections.singletonList(","));
        } else {
            prefix = input.substring(0, lastComma + 1);
            String part = input.substring(lastComma + 1);
            Function<T, String> finalToString = toString;
            options = StringMan.getClosest(part, keys, finalToString::apply, OptionData.MAX_CHOICES, true)
                    .stream().map(f -> f + "=").collect(Collectors.toList());
        }
        if (options.isEmpty()) return null;
        if (options.size() > OptionData.MAX_CHOICES) {
            options = options.subList(0, OptionData.MAX_CHOICES);
        }
        for (int i = 0; i < options.size(); i++) {
            options.set(i, prefix + options.get(i));
        }
        return options;
    }

    public static <T extends Enum> List<String> completeEnum(String input, Class<T> emum) {
        List<T> options = Arrays.asList(emum.getEnumConstants());
        return StringMan.getClosest(input, options, true).stream()
                .map(Enum::name).collect(Collectors.toList());
    }

    public static <T> List<T> getClosest(String input, List<T> options, boolean requireContains) {
        return getClosest(input, options, Object::toString, Integer.MAX_VALUE, requireContains);
    }

    public static <T> List<T> getClosest(String input, List<T> options, Function<T, String> optionToString, boolean requireContains) {
        return getClosest(input, options, optionToString, Integer.MAX_VALUE, requireContains);
    }

    public static <T> List<T> getClosest(String input, List<T> options, Function<T, String> optionToString, int maxResults, boolean requireContains) {
        return getClosest(input, options, optionToString, maxResults, requireContains, false);
    }

    public static <T> List<T> getClosest(String input, List<T> options, Function<T, String> optionToString, int maxResults, boolean requireContains, boolean requireStartsWith) {
        if (input.isEmpty()) return options;

        if (requireStartsWith) requireContains = false;
        List<Map.Entry<T, Double>> distances = new ArrayList<>();

        input = input.toUpperCase();
        for (T option : options) {
            String optionName = optionToString.apply(option).toUpperCase();
            if (requireStartsWith) {
                if (!optionName.startsWith(input)) continue;
            }
            else if (requireContains && !optionName.contains(input)) continue;

            double distance = StringUtils.getJaroWinklerDistance(input, optionName);
            if (distance < Integer.MAX_VALUE) {
                distances.add(new KeyValue<>(option, distance));
            }
        }

        distances.sort(Comparator.comparingDouble(Map.Entry::getValue));
        Collections.reverse(distances);
        if (distances.size() > maxResults) {
            distances = distances.subList(0, maxResults);
        }
        return distances.stream().map(Map.Entry::getKey).collect(Collectors.toList());
    }

    public static int blockStateStringDistance(String input, String item) {
        int distance = 0;
        boolean sequentail = false;
        int j = 0;
        for (int i = 0; i < input.length(); i++) {
            char ai = input.charAt(i);
            outer:
            while (true) {
                if (j >= item.length()) return Integer.MAX_VALUE;

                char bj = item.charAt(j++);
                if (sequentail) {
                    switch (bj) {
                        case ':':
                        case '_':
                            sequentail = false;
                            if (bj == ai) break outer;
                            continue;
                    }
                    continue;
                }
                if (bj != ai) {
                    distance++;
                    switch (bj) {
                        case ':':
                        case '_':
                            continue;
                        default:
                            sequentail = true;
                            continue;
                    }
                }
                break;
            }
        }
        return distance;
    }

    public static int countWords(String paragraph, String match) {
        int count = 0;
        String[] words = paragraph.replaceAll("[^a-zA-Z ]", "").toLowerCase().split("\\s+");
        for (String word : words) {
            if (word.equalsIgnoreCase(match)) {
                count++;
            }
        }
        return count;
    }

    private static final String[] QWERTY_KEYBOARD = {
            "1234567890",
            "qwertyuiop",
            "asdfghjkl",
            "zxcvbnm"
    };

    private static int[] getCharPosition(char c) {
        for (int i = 0; i < QWERTY_KEYBOARD.length; i++) {
            int index = QWERTY_KEYBOARD[i].indexOf(c);
            if (index != -1) {
                return new int[]{i, index};
            }
        }
        return new int[]{-1, -1};
    }

    private static double getDistance(char c1, char c2) {
        int[] pos1 = getCharPosition(c1);
        int[] pos2 = getCharPosition(c2);

        if (pos1[0] == -1 || pos2[0] == -1) {
            return 1.0;
        }

        int dx = pos1[1] - pos2[1];
        int dy = pos1[0] - pos2[0];

        return MathMan.sqrtApprox(dx * dx + dy * dy);
    }

    public static double distanceWeightedQwertSift4(String a, String b) {
        // combine wl and sift4
        WeightedLevenshtein wl = new WeightedLevenshtein(
                new CharacterSubstitutionInterface() {
                    public double cost(char c1, char c2) {
                        return getDistance(c1, c2);
                    }
                });
        Sift4 s4 = new Sift4();
        return (wl.distance(a, b) + s4.distance(a, b)) / 2;
    }

    public static int getLevenshteinDistance(String s, String t) {
        int n = s.length();
        int m = t.length();
        if (n == 0) {
            return m;
        } else if (m == 0) {
            return n;
        }
        if (n > m) {
            final String tmp = s;
            s = t;
            t = tmp;
            n = m;
            m = t.length();
        }
        int[] p = new int[n + 1];
        int[] d = new int[n + 1];
        int[] _d;
        int i;
        int j;
        char t_j;
        int cost;
        for (i = 0; i <= n; i++) {
            p[i] = i;
        }
        for (j = 1; j <= m; j++) {
            t_j = t.charAt(j - 1);
            d[0] = j;

            for (i = 1; i <= n; i++) {
                cost = s.charAt(i - 1) == t_j ? 0 : 1;
                d[i] = Math.min(Math.min(d[i - 1] + 1, p[i] + 1), p[i - 1] + cost);
            }
            _d = p;
            p = d;
            d = _d;
        }
        return p[n];
    }

    public static <T> String join(Collection<T> arr, String delimiter, Function<T, String> funx) {
        final StringBuilder result = new StringBuilder();
        int i = 0;
        for (T obj : arr) {
            if (i > 0) {
                result.append(delimiter);
            }
            result.append(funx.apply(obj));
            i++;
        }
        return result.toString();
    }

    public static String join(Object[] array, String delimiter) {
        switch (array.length) {
            case 0:
                return "";
            case 1:
                return array[0].toString();
            default:
                final StringBuilder result = new StringBuilder();
                for (int i = 0, j = array.length; i < j; i++) {
                    if (i > 0) {
                        result.append(delimiter);
                    }
                    result.append(array[i]);
                }
                return result.toString();
        }
    }

    public static Integer toInteger(String string, int start, int end) {
        int value = 0;
        char char0 = string.charAt(0);
        boolean negative;
        if (char0 == '-') {
            negative = true;
            start++;
        } else negative = false;
        for (int i = start; i < end; i++) {
            char c = string.charAt(i);
            switch (c) {
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    value = value * 10 + c - '0';
                    break;
                default:
                    return null;
            }
        }
        return negative ? -value : value;
    }

    public static String join(int[] array, String delimiter) {
        final Integer[] wrapped = Arrays.stream(array).boxed().toArray(Integer[]::new);
        return join(wrapped, delimiter);
    }

    public static boolean isEqualToAny(String a, String... args) {
        for (String arg : args) {
            if (StringMan.isEqual(a, arg)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEqualIgnoreCaseToAny(String a, String... args) {
        for (String arg : args) {
            if (StringMan.isEqualIgnoreCase(a, arg)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEqual(String a, String b) {
        return a == b || a != null && b != null && a.length() == b.length()
                && a.hashCode() == b.hashCode()
                && a.equals(b);
    }

    public static boolean isEqualIgnoreCase(String a, String b) {
        return a == b ||
                a != null && b != null && a.length() == b.length() && a.equalsIgnoreCase(b);
    }

    public static String repeat(String s, int n) {
        return IntStream.range(0, n).mapToObj(i -> s).collect(Collectors.joining());
    }

    private static final BigInteger INIT32  = new BigInteger("811c9dc5",         16);
    private static final BigInteger INIT64  = new BigInteger("cbf29ce484222325", 16);
    private static final BigInteger PRIME32 = new BigInteger("01000193",         16);
    private static final BigInteger PRIME64 = new BigInteger("100000001b3",      16);
    private static final BigInteger MOD32   = new BigInteger("2").pow(32);
    private static final BigInteger MOD64   = new BigInteger("2").pow(64);

    public static BigInteger hash_fnv1_32(byte[] data) {
        BigInteger hash = INIT32;

        for (byte b : data) {
            hash = hash.multiply(PRIME32).mod(MOD32);
            hash = hash.xor(BigInteger.valueOf((int) b & 0xff));
        }

        return hash;
    }

    public static BigInteger hash_fnv1_64(byte[] data) {
        BigInteger hash = INIT64;

        for (byte b : data) {
            hash = hash.multiply(PRIME64).mod(MOD64);
            hash = hash.xor(BigInteger.valueOf((int) b & 0xff));
        }

        return hash;
    }

    public static BigInteger hash_fnv1a_32(byte[] data) {
        BigInteger hash = INIT32;

        for (byte b : data) {
            hash = hash.xor(BigInteger.valueOf((int) b & 0xff));
            hash = hash.multiply(PRIME32).mod(MOD32);
        }

        return hash;
    }
    private static final long FNV_OFFSET_BASIS_64 = 0xcbf29ce484222325L;
    private static final long FNV_PRIME_64 = 0x100000001b3L;

    public static long hash(String input) {
        long hash = FNV_OFFSET_BASIS_64;
        for (byte b : input.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xff);
            hash *= FNV_PRIME_64;
        }
        return hash + Long.MIN_VALUE;
    }

    public static long hash(List<String> inputs) {
        long hash = FNV_OFFSET_BASIS_64;
        for (String input : inputs) {
            for (byte b : input.getBytes(StandardCharsets.UTF_8)) {
                hash ^= (b & 0xff);
                hash *= FNV_PRIME_64;
            }
        }
        return hash + Long.MIN_VALUE;
    }


//    private static long hash2(String input) {
//        BigInteger value = StringMan.hash_fnv1a_64(input.getBytes());
//        value = value.add(BigInteger.valueOf(Long.MIN_VALUE));
//        return value.longValueExact();
//    }
//
//    public static BigInteger hash_fnv1a_64(byte[] data) {
//        BigInteger hash = INIT64;
//
//        for (byte b : data) {
//            hash = hash.xor(BigInteger.valueOf((int) b & 0xff));
//            hash = hash.multiply(PRIME64).mod(MOD64);
//        }
//
//        return hash;
//    }

    public static String classNameToSimple(String className) {
        return className.toString().replaceAll("[a-z_A-Z0-9.]+\\.([a-z_A-Z0-9]+)", "$1").replaceAll("[a-z_A-Z0-9]+\\$([a-z_A-Z0-9]+)", "$1");
    }
}
