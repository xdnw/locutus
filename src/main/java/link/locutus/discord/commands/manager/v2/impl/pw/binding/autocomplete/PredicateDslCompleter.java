package link.locutus.discord.commands.manager.v2.impl.pw.binding.autocomplete;


import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Autocomplete;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.ICommand;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;

import java.lang.reflect.Type;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public final class PredicateDslCompleter<T> implements BiFunction<String, Integer, PredicateDslCompleter.CompletionResult> {
    @FunctionalInterface
    public interface DebugSink { void log(String s); }
    private DebugSink debug = s -> {
        System.out.println(s);
    }; // default no-op
    public PredicateDslCompleter<T> withDebug(DebugSink sink) { this.debug = sink != null ? sink : (s -> {}); return this; }
    private void dbg(String m) { debug.log(m); }

    private static final Set<String> COMMON_PREFIXES = new LinkedHashSet<>(Arrays.asList("get", "is", "can", "has"));

    private final Class<T> rootType;
    private final ValueStore<?> completerStore;

    public PredicateDslCompleter(ValueStore<?> completerStore, Class<T> rootType) {
        this.rootType = rootType;
        this.completerStore = completerStore;
    }

    private static String dropCommonPrefix(String name) {
        if (name == null) return null;
        for (String p : COMMON_PREFIXES) {
            if (name.length() > p.length() && name.regionMatches(true, 0, p, 0, p.length())) {
                return name.substring(p.length());
            }
        }
        return name;
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) return false;
        final int hLen = haystack.length();
        final int nLen = needle.length();
        if (nLen == 0) return true;
        for (int i = 0; i + nLen <= hLen; i++) {
            if (haystack.regionMatches(true, i, needle, 0, nLen)) return true;
        }
        return false;
    }

    private static boolean startsWithMatch(String candidate, String prefix) {
        if (prefix == null || prefix.isEmpty()) return true;
        if (candidate == null) return false;
        if (startsWithIgnoreCase(candidate, prefix)) return true;
        String stripped = dropCommonPrefix(candidate);
        return stripped != null && startsWithIgnoreCase(stripped, prefix);
    }

    private static boolean containsMatch(String candidate, String needle) {
        if (needle == null || needle.isEmpty()) return true;
        if (candidate == null) return false;
        if (containsIgnoreCase(candidate, needle)) return true;
        String stripped = dropCommonPrefix(candidate);
        return stripped != null && containsIgnoreCase(stripped, needle);
    }

    private static boolean isBoolean(Class<?> c) {
        return c == boolean.class || c == Boolean.class;
    }

    private static boolean isCoercibleToNumeric(Class<?> c) {
        return isNumeric(c) || isBoolean(c);
    }

    private static boolean isEnum(Class<?> c) {
        return c != null && c.isEnum();
    }

    private static boolean hasUnclosedParenSinceCurrentFilter(List<Token> tokens, int cursor) {
        int braceDepth = 0;
        int parenDepth = 0;
        for (Token t : tokens) {
            if (t.start >= cursor) break;
            if (t.type == TokenType.LBRACE) {
                if (braceDepth == 0) parenDepth = 0; // reset when entering a new top filter
                braceDepth++;
            } else if (t.type == TokenType.RBRACE) {
                braceDepth = Math.max(0, braceDepth - 1);
                if (braceDepth == 0) parenDepth = 0;
            } else if (braceDepth > 0) {
                if (t.type == TokenType.LPAREN) parenDepth++;
                else if (t.type == TokenType.RPAREN) parenDepth = Math.max(0, parenDepth - 1);
            }
        }
        return parenDepth > 0;
    }

    private static boolean hasRemainingParams(ICommand<?> cmd, Set<String> provided) {
        List<ParameterData> ps = cmd.getUserParameters();
        if (ps == null || ps.isEmpty()) return false;
        for (ParameterData p : ps) {
            if (!provided.contains(p.getName())) return true;
        }
        return false;
    }

    private static boolean isComparatorToken(Token t) {
        if (t == null) return false;
        return t.type == TokenType.EQ || t.type == TokenType.NEQ ||
                t.type == TokenType.GT || t.type == TokenType.GE ||
                t.type == TokenType.LT || t.type == TokenType.LE;
    }

    private static Token nextNonWsToken(List<Token> tokens, int fromExclusive) {
        for (int i = fromExclusive + 1; i < tokens.size(); i++) {
            if (tokens.get(i).type != TokenType.WS) return tokens.get(i);
        }
        return null;
    }

    private static Token prevNonWsToken(List<Token> tokens, int fromExclusive) {
        for (int i = fromExclusive - 1; i >= 0; i--) {
            if (tokens.get(i).type != TokenType.WS) return tokens.get(i);
        }
        return null;
    }

    private static final class RhsState {
        final Class<?> lhsType;
        final Span replace;
        final String prefix;
        RhsState(Class<?> lhsType, Span replace, String prefix) {
            this.lhsType = lhsType;
            this.replace = replace;
            this.prefix = prefix;
        }
    }

    private RhsState detectRhsState(String input, List<Token> tokens, int cursor) {
        // Find the nearest comparator at or before cursor
        int compIdx = -1;
        for (int i = tokens.size() - 1; i >= 0; i--) {
            Token t = tokens.get(i);
            if (t.end > cursor) continue;
            if (isComparatorToken(t)) { compIdx = i; break; }
        }
        if (compIdx < 0) return null;

        // Ensure a '}' appears immediately before comparator (ignoring WS)
        Token left = prevNonWsToken(tokens, compIdx);
        if (left == null || left.type != TokenType.RBRACE) return null;

        // If a separator (comma / set op) already appears after the RHS value, we are no longer in RHS mode.
        // This prevents suggesting additional RHS values after `{...}=VALUE,`
        for (int i = compIdx + 1; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.start >= cursor) break;
            if (t.type == TokenType.WS) continue;
            // Hitting a separator means RHS is complete; exit RHS mode.
            if (t.type == TokenType.COMMA || t.type == TokenType.PIPE || t.type == TokenType.AMP) {
                return null;
            }
            // If we see a second comparator or another brace before cursor, also abort (malformed or new context).
            if (isComparatorToken(t) || t.type == TokenType.RBRACE || t.type == TokenType.LBRACE) {
                return null;
            }
        }


        // Determine LHS type by reusing chain analyzer with cursor at the '}' start
        ChainTypeAtCursor chain = ChainTypeAtCursor.compute(input, tokens, left.start, rootType);
        Class<?> lhsType = (chain != null && chain.currentType != null) ? chain.currentType : rootType;

        // RHS replace span starts after comparator (skip spaces)
        Token comp = tokens.get(compIdx);
        int startRhs = comp.end;
        while (startRhs < input.length() && Character.isWhitespace(input.charAt(startRhs))) startRhs++;
        Span word = wordSpanAt(input, cursor, CharClasses::isIdentChar);
        if (word.start < startRhs) word = new Span(startRhs, cursor);
        String prefix = input.substring(word.start, cursor);

        dbg("RHS detected: lhsType=" + (lhsType == null ? "null" : lhsType.getName()) +
                ", comp=" + comp.text + ", prefix='" + prefix + "'");

        return new RhsState(lhsType, word, prefix);
    }

    private boolean suggestRhsValues(CompletionBuilder out, Class<?> lhsType, String prefix, Span replace) {
        boolean any = false;

        // 1) Provider-based
        Parser<?> provider = completerStore.get(Key.of(lhsType, Autocomplete.class));
        if (provider != null) {
            List<Object> vals = (List<Object>) provider.apply(completerStore, prefix);
            if (vals != null && !vals.isEmpty()) {
                for (Object raw : vals) {
                    if (raw == null) continue;
                    String label;
                    String detail = null;
                    if (raw instanceof Map.Entry<?, ?> e) {
                        label = String.valueOf(e.getKey());
                        if (e.getValue() != null) detail = String.valueOf(e.getValue());
                    } else {
                        label = String.valueOf(raw);
                    }
                    out.add(label, replace.start, replace.end).detail(detail);
                    any = true;
                }
                dbg("RHS provider hit for " + lhsType.getSimpleName() + " size=" + vals.size());
                return true;
            } else {
                dbg("RHS provider empty for " + lhsType.getSimpleName());
            }
        } else {
            dbg("No RHS provider for " + (lhsType == null ? "null" : lhsType.getSimpleName()));
        }

        // 2) Fallbacks
        if (lhsType != null && isEnum(lhsType)) {
            Object[] consts = lhsType.getEnumConstants();
            for (Object c : consts) {
                String name = ((Enum<?>) c).name();
                if (prefix == null || prefix.isEmpty() || startsWithIgnoreCase(name, prefix)) {
                    out.add(name, replace.start, replace.end).detail("enum " + lhsType.getSimpleName());
                    any = true;
                }
            }
        } else if (lhsType != null && isBoolean(lhsType)) {
            if (prefix == null || "true".startsWith(prefix.toLowerCase())) {
                out.add("true", replace.start, replace.end);
                any = true;
            }
            if (prefix == null || "false".startsWith(prefix.toLowerCase())) {
                out.add("false", replace.start, replace.end);
                any = true;
            }
        } else if (lhsType != null && isNumeric(lhsType)) {
            // basic numeric hints if nothing typed
            if (prefix == null || prefix.isEmpty()) {
                out.add("0", replace.start, replace.end);
                out.add("1", replace.start, replace.end);
                out.add("10", replace.start, replace.end);
                any = true;
            }
        }
        return any;
    }

    // Entry point
    @Override
    public CompletionResult apply(String input, Integer cursor) {
        if (input == null) input = "";
        if (cursor == null) cursor = input.length();
        cursor = Math.max(0, Math.min(cursor, input.length()));

        // Tokenize once; tokens carry start/end offsets
        List<Token> tokens = new Tokenizer(input).tokenize();

        // Determine cursor context
        CursorContext ctx = CursorContext.analyze(input, tokens, cursor);

        // Dispatch based on context
        if (!ctx.insideFilter) {
            return completeTopLevel(input, tokens, cursor, ctx);
        } else {
            return completeInsideFilter(input, tokens, cursor, ctx);
        }
    }

    // Top-level completion: selectors, wildcard, start filter, grouping, set ops
    private CompletionResult completeTopLevel(String input, List<Token> tokens, int cursor, CursorContext ctx) {
        CompletionBuilder result = new CompletionBuilder(input);

        // A) RHS mode: if after a comparator, only suggest RHS values; never return empty
        RhsState rhs = detectRhsState(input, tokens, cursor);
        if (rhs != null) {
            boolean got = suggestRhsValues(result, rhs.lhsType, rhs.prefix, rhs.replace);
            if (!got) {
                // still give the user something to proceed
                dbg("RHS fallback produced no items");
            }
            return result.build();
        }

        Span replace = wordSpanAt(input, cursor, CharClasses::isSelectorWordChar);
        String prefix = input.substring(replace.start, cursor).trim();

        Token prev = TokenUtil.tokenBefore(tokens, cursor);

        // B) Right after '}' -> suggest comparators from the filter's result type
        boolean afterClosingFilter = prev != null && prev.type == TokenType.RBRACE;
        if (afterClosingFilter) {
            Class<?> filterType = rootType;
            ChainTypeAtCursor chain = ChainTypeAtCursor.compute(input, tokens, prev.start, rootType);
            if (chain != null && chain.currentType != null) {
                filterType = chain.currentType;
            }
            dbg("After } type=" + (filterType == null ? "null" : filterType.getName()));
            suggestComparators(result, filterType, cursor);

            // If comparator is mandatory (non-numeric and non-boolean), stop here
            if (!isCoercibleToNumeric(filterType)) {
                return result.build();
            }
            // numeric/bool: comparators are optional; continue with normal suggestions
        }

        // C) Normal top-level: start filter only when allowed
        boolean canStartFilter = prev == null
                || prev.type == TokenType.COMMA
                || prev.type == TokenType.PIPE
                || prev.type == TokenType.AMP
                || prev.type == TokenType.LPAREN;

        if (canStartFilter && ("{".startsWith(prefix) || prefix.isEmpty())) {
            result.add("{", replace.start, replace.end).detail("Start filter");
        }

        // D) Wildcard
        if ("*".startsWith(prefix) || prefix.isEmpty() || prefix.equals("*")) {
            result.add("*", replace.start, replace.end).detail("All " + rootType.getSimpleName() + "s");
        }

        // E) Named selectors (unchanged)
        Parser<Object> baseCompleter = this.completerStore.get(Key.of(rootType, Autocomplete.class));
        if (baseCompleter != null) {
            List<Object> resultList = (List<Object>) baseCompleter.apply(completerStore, prefix);
            if (resultList != null && !resultList.isEmpty()) {
                for (Object raw : resultList) {
                    if (raw == null) continue;
                    String label;
                    String detail = "Selector";
                    if (raw instanceof Map.Entry<?, ?> e) {
                        Object k = e.getKey();
                        Object v = e.getValue();
                        label = String.valueOf(k);
                        if (v != null) detail = String.valueOf(v);
                    } else {
                        label = String.valueOf(raw);
                    }
                    result.add(label, replace.start, replace.end).detail(detail);
                }
            }
        }

        // F) Punctuation
        if (hasUnclosedLeftParen(tokens, cursor)) {
            result.add(")", cursor, cursor).detail("Close group");
        }
        result.add(",", cursor, cursor).detail("Sequence separator");
        result.add("&", cursor, cursor).detail("Intersect (selectors), AND (filters)");
        result.add("|", cursor, cursor).detail("Union (selectors), OR (filters)");
        result.add("(", cursor, cursor).detail("Start group");

        return result.build();
    }

    // Inside-filter completion: commands, comparators, args, RHS values
    private boolean isAtStartOfFilter(FilterSurface surface,
                                      List<Token> tokens,
                                      Token currentToken,
                                      int cursor) {
        if (surface == null || currentToken == null) return false;
        if (currentToken.type != TokenType.IDENT) return false;

        // Opening '{' token
        Token open = tokens.get(surface.openIndex);

        // Find first non-WS token after '{' up to (and possibly including) current token
        for (int i = surface.openIndex + 1; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.start >= cursor) break;
            if (t.type == TokenType.WS) continue;
            // First significant token
            return t == currentToken; // true only if current IDENT is the first token after '{'
        }
        // No other tokens yet (IDENT might be in-progress and not tokenized, so fallback false)
        return false;
    }

    // Replace existing completeInsideFilter method with this version
    private CompletionResult completeInsideFilter(String input, List<Token> tokens, int cursor, CursorContext ctx) {
        CompletionBuilder result = new CompletionBuilder(input);

        FilterSurface surface = FilterSurface.from(tokens, cursor);
        if (surface == null) {
            Span replace = wordSpanAt(input, cursor, CharClasses::isIdentChar);
            int added = completeCommandNamesForType(result, rootType, "", replace, false, null);
            if (!hasUnclosedParenSinceCurrentFilter(tokens, cursor)) {
                result.add("}", cursor, cursor).detail("Close filter");
            }
            return result.build();
        }

        ArgumentContext argCtx = ArgumentContext.find(input, tokens, cursor, rootType);
        if (argCtx != null) {
            boolean canCloseCall = allRequiredParamsProvided(argCtx.function, argCtx.alreadyNamedParams);

            if (argCtx.expectingName) {
                completeParameterNames(result, argCtx);
                if (canCloseCall) {
                    result.add(")", cursor, cursor).detail("Close call");
                }
            } else if (argCtx.expectingValue) {
                completeParameterValueValuesOnly(result, argCtx);
                if (canCloseCall) {
                    result.add(")", cursor, cursor).detail("Close call");
                }
                if (hasRemainingParams(argCtx.function, argCtx.alreadyNamedParams)) {
                    result.add(",", cursor, cursor).detail("Next argument");
                }
            } else if (canCloseCall) {
                result.add(")", cursor, cursor).detail("Close call");
            }

            if (!hasUnclosedParenSinceCurrentFilter(tokens, cursor)) {
                result.add("}", cursor, cursor).detail("Close filter");
            }
            return result.build();
        }

        ChainTypeAtCursor chain = ChainTypeAtCursor.compute(input, tokens, cursor, rootType);
        Token prev = TokenUtil.tokenBefore(tokens, cursor);
        boolean prevIsDot = prev != null && prev.type == TokenType.DOT && prev.end <= cursor;
        Token currentToken = TokenUtil.tokenAtOrBefore(tokens, cursor);

        Span replace = wordSpanAt(input, cursor, CharClasses::isIdentChar);
        String identPrefix = input.substring(replace.start, cursor);

        // Improved start-of-filter detection:
        boolean naiveStart = prev != null && prev.type == TokenType.LBRACE;
        boolean structuralStart = isAtStartOfFilter(surface, tokens, currentToken, cursor);
        boolean atStartOfFilter = naiveStart || structuralStart;

        Integer filterOpenPos = null;
        if (atStartOfFilter) {
            Token open = tokens.get(surface.openIndex);
            filterOpenPos = open.start;
        }

        int added = 0;
        if (atStartOfFilter || prevIsDot ||
                (currentToken != null && currentToken.type == TokenType.IDENT && currentToken.start < cursor)) {
            added = completeCommandNamesForType(result, chain.currentType, identPrefix, replace, atStartOfFilter, filterOpenPos);
        } else if (identPrefix.isEmpty()) {
            added = completeCommandNamesForType(result, chain.currentType, "", replace, atStartOfFilter, filterOpenPos);
        }

        if ((!atStartOfFilter || added == 0) && !hasUnclosedParenSinceCurrentFilter(tokens, cursor)) {
            result.add("}", cursor, cursor).detail("Close filter");
        }

        // Suppress premature '}' while initial command still being typed
        if (!atStartOfFilter && !hasUnclosedParenSinceCurrentFilter(tokens, cursor)) {
            result.add("}", cursor, cursor).detail("Close filter");
        }
        return result.build();
    }

    private void completeParameterValueValuesOnly(CompletionBuilder out, ArgumentContext argCtx) {
        if (argCtx.paramType == null) {
            completeParameterNames(out, argCtx);
            return;
        }
        System.out.println("Complete RHS " + argCtx.paramType + " | " + argCtx.valuePrefix);
        completeRhsValue(out, argCtx.paramType, argCtx.valuePrefix, argCtx.valueReplaceSpan);
    }

    // Build suggestions for command names for a given type
    private int completeCommandNamesForType(CompletionBuilder out,
                                            Class<?> onType,
                                            String prefix,
                                            Span replace,
                                            boolean atStartOfFilter,
                                            Integer filterOpenBracePos) {
        int startCount = out.size();
        Placeholders<?, Object> pm = PlaceholdersMap.get().get(onType);
        List<ICommand<?>> cmds;
        if (pm == null) {
            cmds = Collections.emptyList();
        } else {
            cmds = new ObjectArrayList<>();
            for (CommandCallable value : pm.getCommands().getSubcommands().values()) {
                if (value instanceof ParametricCallable<?> cmd) {
                    cmds.add(cmd);
                }
            }
        }

        List<ICommand<?>> matches = new ArrayList<>();
        for (ICommand<?> cmd : cmds) {
            String name = cmd.getFullPath();
            if (startsWithMatch(name, prefix)) matches.add(cmd);
        }
        if (matches.isEmpty() && prefix != null && !prefix.isEmpty()) {
            for (ICommand<?> cmd : cmds) {
                String name = cmd.getFullPath();
                if (containsMatch(name, prefix)) matches.add(cmd);
            }
        }

        for (ICommand<?> cmd : matches) {
            String rawName = cmd.getFullPath();
            String displayName = rawName;
            if (atStartOfFilter) {
                String dropped = dropCommonPrefix(rawName);
                if (dropped != null && !dropped.isEmpty() && !dropped.equalsIgnoreCase(rawName)) {
                    displayName = Character.toLowerCase(dropped.charAt(0)) + dropped.substring(1);
                }
            }
            List<ParameterData> ps = cmd.getUserParameters();
            int paramCount = (ps == null) ? 0 : ps.size();
            boolean zeroArg = paramCount == 0;

            if (zeroArg) {
                if (atStartOfFilter && filterOpenBracePos != null && filterOpenBracePos >= 0) {
                    out.add("{" + displayName + "}", filterOpenBracePos, replace.end)
                            .detail(signatureOf(cmd));
                } else {
                    out.add(displayName, replace.start, replace.end).detail(signatureOf(cmd));
                    out.add(displayName + "}", replace.start, replace.end)
                            .detail(signatureOf(cmd) + " — complete filter");
                }
            } else {
                boolean anyRequired = requiresAnyNonOptional(cmd);
                if (!anyRequired) {
                    out.add(displayName, replace.start, replace.end).detail(signatureOf(cmd));
                }
                out.add(displayName + "(", replace.start, replace.end)
                        .detail(signatureOf(cmd) + " — start call");
            }
        }
        return out.size() - startCount;
    }

    private static boolean requiresAnyNonOptional(ICommand<?> cmd) {
        List<ParameterData> ps = cmd.getUserParameters();
        if (ps == null || ps.isEmpty()) return false;
        for (ParameterData p : ps) {
            if (!p.isOptional()) return true;
        }
        return false;
    }

    private static String signatureOf(ICommand<?> cmd) {
        StringBuilder sb = new StringBuilder();
        sb.append(cmd.getFullPath()).append("(");
        List<ParameterData> ps = cmd.getUserParameters();
        if (ps != null) {
            boolean first = true;
            for (ParameterData p : ps) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(p.getName()).append(": ").append(simpleName(p.getType()));
                if (p.isOptional()) sb.append(" = ").append(defaultValueText(p.getDefaultValue()));
            }
        }
        sb.append(") : ").append(simpleName(cmd.getType()));
        return sb.toString();
    }

    private static String defaultValueText(Object v) {
        if (v == null) return "null";
        return String.valueOf(v);
    }

    private static String simpleName(Type t) {
        if (t instanceof Class) return ((Class<?>) t).getSimpleName();
        return String.valueOf(t);
    }

    // Comparators
    private void suggestComparators(CompletionBuilder out, Class<?> lhsType, int at) {
        // Minimal: we allow =, != for everything;
        out.add("=", at, at).detail("Equals");
        out.add("!=", at, at).detail("Not equals");

        // If number-like or comparable, add range comparators
        if (isNumeric(lhsType) || isComparable(lhsType)) {
            out.add(">", at, at).detail("Greater than");
            out.add(">=", at, at).detail("Greater than or equal");
            out.add("<", at, at).detail("Less than");
            out.add("<=", at, at).detail("Less than or equal");
        }
    }

    private static boolean isNumeric(Class<?> c) {
        if (c == null) return false;
        return Number.class.isAssignableFrom(c)
                || c == int.class || c == long.class || c == double.class || c == float.class
                || c == short.class || c == byte.class;
    }

    private static boolean isComparable(Class<?> c) {
        if (c == null) return false;
        return Comparable.class.isAssignableFrom(c);
    }

    // RHS value completion using CompleterUtil.get(type)
    private void completeRhsValue(CompletionBuilder out, Type rhsType, String rhsPrefix, Span rhsReplace) {
        Parser<?> vc = completerStore.get(Key.of(rhsType, Autocomplete.class));
        if (vc != null) {
            List<Object> resultList = (List<Object>) vc.apply(completerStore, rhsPrefix);
            if (resultList != null && resultList.size() > 0) {
                for (Object raw : resultList) {
                    if (raw == null) continue;
                    String label;
                    String detail = null;
                    if (raw instanceof Map.Entry<?, ?> e) {
                        Object k = e.getKey();
                        Object v = e.getValue();
                        label = String.valueOf(k);
                        detail = v == null ? null : String.valueOf(v);
                    } else {
                        label = String.valueOf(raw);
                    }
                    out.add(label, rhsReplace.start, rhsReplace.end)
                            .detail(detail);
                }
            }
        } else {
            // As a fallback for numbers/strings
            if (rhsType instanceof Class clazz && isNumeric(clazz)) {
                if (rhsPrefix.isEmpty()) {
                    out.add("0", rhsReplace.start, rhsReplace.end);
                    out.add("10", rhsReplace.start, rhsReplace.end);
                    out.add("100", rhsReplace.start, rhsReplace.end);
                }
            }
        }
    }

    // Parameter names
    private void completeParameterNames(CompletionBuilder out, ArgumentContext argCtx) {
        ICommand<?> cmd = argCtx.function;
        Span replace = argCtx.nameReplaceSpan != null ? argCtx.nameReplaceSpan : new Span(argCtx.cursor, argCtx.cursor);
        String prefix = argCtx.namePrefix != null ? argCtx.namePrefix : "";
        Set<String> already = argCtx.alreadyNamedParams;

        for (ParameterData p : cmd.getUserParameters()) {
            if (already.contains(p.getName())) continue; // don’t suggest duplicates
            if (prefix.isEmpty() || startsWithIgnoreCase(p.getName(), prefix)) {
                // Suggest "name: "
                String insert = p.getName() + ": ";
                out.add(insert, replace.start, replace.end)
                        .detail(simpleName(p.getType()) + (p.isOptional() ? " (optional)" : ""));
            }
        }
        // Also allow closing if the user already provided all required params
        if (allRequiredParamsProvided(cmd, already)) {
            out.add(")", argCtx.cursor, argCtx.cursor).detail("Close call");
        }
    }

    private static boolean allRequiredParamsProvided(ICommand<?> cmd, Set<String> provided) {
        for (ParameterData p : cmd.getUserParameters()) {
            if (!p.isOptional() && !provided.contains(p.getName())) return false;
        }
        return true;
    }

    // Utilities

    private static boolean startsWithIgnoreCase(String a, String prefix) {
        if (prefix == null || prefix.isEmpty()) return true;
        if (a == null) return false;
        if (prefix.length() > a.length()) return false;
        return a.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static boolean hasUnclosedLeftParen(List<Token> tokens, int cursor) {
        int depth = 0;
        for (Token t : tokens) {
            if (t.end > cursor) break;
            if (t.type == TokenType.LPAREN) depth++;
            if (t.type == TokenType.RPAREN) depth = Math.max(0, depth - 1);
        }
        return depth > 0;
    }

    private static Span wordSpanAt(String input, int cursor, Predicate<Character> isWordChar) {
        int start = cursor;
        while (start > 0 && isWordChar.test(input.charAt(start - 1))) start--;
        int end = cursor;
        while (end < input.length() && isWordChar.test(input.charAt(end))) end++;
        return new Span(start, end);
    }

    // Tokenizer and token types

    enum TokenType {
        IDENT, NUMBER, STRING,
        LBRACE, RBRACE, LPAREN, RPAREN,
        DOT, COMMA, PIPE, AMP, STAR,
        PLUS, MINUS, SLASH, PERCENT, CARET,
        QUESTION, COLON,
        GT, LT, EQ, NEQ, GE, LE,
        WS, UNKNOWN
    }

    static final class Token {
        final TokenType type;
        final String text;
        final int start;
        final int end;

        Token(TokenType type, String text, int start, int end) {
            this.type = type;
            this.text = text;
            this.start = start;
            this.end = end;
        }

        public String toString() {
            return type + "('" + text + "')@" + start + "-" + end;
        }
    }

    static final class Tokenizer {
        private final String s;
        private int i;

        Tokenizer(String s) { this.s = s != null ? s : ""; }

        List<Token> tokenize() {
            List<Token> out = new ArrayList<>();
            i = 0;
            while (i < s.length()) {
                char c = s.charAt(i);
                int start = i;
                if (Character.isWhitespace(c)) {
                    while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
                    out.add(new Token(TokenType.WS, s.substring(start, i), start, i));
                } else if (CharClasses.isIdentStart(c)) {
                    i++;
                    while (i < s.length() && CharClasses.isIdentChar(s.charAt(i))) i++;
                    out.add(new Token(TokenType.IDENT, s.substring(start, i), start, i));
                } else if (Character.isDigit(c)) {
                    i++;
                    while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
                    out.add(new Token(TokenType.NUMBER, s.substring(start, i), start, i));
                } else if (c == '\'' || c == '"') {
                    char quote = c;
                    i++;
                    while (i < s.length() && s.charAt(i) != quote) i++;
                    if (i < s.length()) i++; // include closing quote if present
                    out.add(new Token(TokenType.STRING, s.substring(start, i), start, i));
                } else {
                    switch (c) {
                        case '{': i++; out.add(new Token(TokenType.LBRACE, "{", start, i)); break;
                        case '}': i++; out.add(new Token(TokenType.RBRACE, "}", start, i)); break;
                        case '(': i++; out.add(new Token(TokenType.LPAREN, "(", start, i)); break;
                        case ')': i++; out.add(new Token(TokenType.RPAREN, ")", start, i)); break;
                        case '.': i++; out.add(new Token(TokenType.DOT, ".", start, i)); break;
                        case ',': i++; out.add(new Token(TokenType.COMMA, ",", start, i)); break;
                        case '|': i++; out.add(new Token(TokenType.PIPE, "|", start, i)); break;
                        case '&': i++; out.add(new Token(TokenType.AMP, "&", start, i)); break;
                        case '*': i++; out.add(new Token(TokenType.STAR, "*", start, i)); break;
                        case '+': i++; out.add(new Token(TokenType.PLUS, "+", start, i)); break;
                        case '-': i++; out.add(new Token(TokenType.MINUS, "-", start, i)); break;
                        case '/': i++; out.add(new Token(TokenType.SLASH, "/", start, i)); break;
                        case '%': i++; out.add(new Token(TokenType.PERCENT, "%", start, i)); break;
                        case '^': i++; out.add(new Token(TokenType.CARET, "^", start, i)); break;
                        case '?': i++; out.add(new Token(TokenType.QUESTION, "?", start, i)); break;
                        case ':': i++; out.add(new Token(TokenType.COLON, ":", start, i)); break;
                        case '=': i++; out.add(new Token(TokenType.EQ, "=", start, i)); break;
                        case '!':
                            if (i + 1 < s.length() && s.charAt(i + 1) == '=') {
                                i += 2; out.add(new Token(TokenType.NEQ, "!=", start, i));
                            } else { i++; out.add(new Token(TokenType.UNKNOWN, "!", start, i)); }
                            break;
                        case '>':
                            if (i + 1 < s.length() && s.charAt(i + 1) == '=') {
                                i += 2; out.add(new Token(TokenType.GE, ">=", start, i));
                            } else { i++; out.add(new Token(TokenType.GT, ">", start, i)); }
                            break;
                        case '<':
                            if (i + 1 < s.length() && s.charAt(i + 1) == '=') {
                                i += 2; out.add(new Token(TokenType.LE, "<=", start, i));
                            } else { i++; out.add(new Token(TokenType.LT, "<", start, i)); }
                            break;
                        default:
                            i++;
                            out.add(new Token(TokenType.UNKNOWN, String.valueOf(c), start, i));
                    }
                }
            }
            return out;
        }
    }

    static final class CharClasses {
        static boolean isIdentStart(char c) {
            return Character.isLetter(c) || c == '_' || c == '$';
        }
        static boolean isIdentChar(char c) {
            return Character.isLetterOrDigit(c) || c == '_' || c == '$';
        }
        static boolean isSelectorWordChar(int cp) {
            char c = (char) cp;
            // allow letters/digits/_/-/* for selectors
            return isIdentChar(c) || c == '-' || c == '*';
        }
    }

    // Span and token utilities

    static final class Span {
        final int start;
        final int end;
        Span(int start, int end) { this.start = start; this.end = end; }
    }

    static final class TokenUtil {
        static Token tokenBefore(List<Token> tokens, int cursor) {
            Token prev = null;
            for (Token t : tokens) {
                if (t.end > cursor) break;
                if (t.type != TokenType.WS) prev = t;
            }
            return prev;
        }
        static Token tokenAtOrBefore(List<Token> tokens, int cursor) {
            for (int k = 0; k < tokens.size(); k++) {
                Token t = tokens.get(k);
                if (t.start <= cursor && cursor <= t.end) return t;
                if (t.end < cursor) continue;
                if (t.start > cursor) {
                    // return previous non-ws
                    for (int j = k - 1; j >= 0; j--) {
                        if (tokens.get(j).type != TokenType.WS) return tokens.get(j);
                    }
                    return null;
                }
            }
            // cursor past last token end
            for (int j = tokens.size() - 1; j >= 0; j--) {
                if (tokens.get(j).type != TokenType.WS) return tokens.get(j);
            }
            return null;
        }
    }

    // Cursor context: inside filter or not
    static final class CursorContext {
        final boolean insideFilter;
        CursorContext(boolean insideFilter) { this.insideFilter = insideFilter; }

        static CursorContext analyze(String input, List<Token> tokens, int cursor) {
            int depth = 0;
            for (Token t : tokens) {
                if (t.start >= cursor) break;
                if (t.type == TokenType.LBRACE) depth++;
                if (t.type == TokenType.RBRACE) depth = Math.max(0, depth - 1);
            }
            return new CursorContext(depth > 0);
        }
    }

    // Surface of the current filter block (matching nearest '{' on left)
    static final class FilterSurface {
        final int openIndex;
        FilterSurface(int openIndex) { this.openIndex = openIndex; }

        static FilterSurface from(List<Token> tokens, int cursor) {
            int depth = 0;
            for (int i = 0; i < tokens.size(); i++) {
                Token t = tokens.get(i);
                if (t.start >= cursor) break;
                if (t.type == TokenType.LBRACE) depth++;
                if (t.type == TokenType.RBRACE) depth = Math.max(0, depth - 1);
            }
            if (depth == 0) return null;
            // Find nearest '{' before cursor unmatched
            int d = 0;
            for (int i = 0; i < tokens.size(); i++) {
                Token t = tokens.get(i);
                if (t.start >= cursor) break;
                if (t.type == TokenType.LBRACE) d++;
                if (t.type == TokenType.RBRACE) d--;
            }
            // d is same as depth. Now walk back to find the opening token for this depth
            int need = d;
            for (int i = cursorTokenIndex(tokens, cursor); i >= 0; i--) {
                Token t = tokens.get(i);
                if (t.type == TokenType.RBRACE) need++;
                if (t.type == TokenType.LBRACE) {
                    need--;
                    if (need == 0) {
                        return new FilterSurface(i);
                    }
                }
            }
            return null;
        }

        private static int cursorTokenIndex(List<Token> tokens, int cursor) {
            int idx = -1;
            for (int i = 0; i < tokens.size(); i++) {
                if (tokens.get(i).end <= cursor) idx = i;
                else break;
            }
            return idx;
        }
    }

    // Determine chain type and comparator/RHS state
    static final class ChainTypeAtCursor {
        final Class<?> currentType;
        final boolean lhsReadyForComparator;
        final Class<?> lhsType;
        final boolean afterComparator;
        final Span rhsReplaceSpan;

        ChainTypeAtCursor(Class<?> currentType, boolean lhsReadyForComparator, Class<?> lhsType, boolean afterComparator, Span rhsReplaceSpan) {
            this.currentType = currentType;
            this.lhsReadyForComparator = lhsReadyForComparator;
            this.lhsType = lhsType;
            this.afterComparator = afterComparator;
            this.rhsReplaceSpan = rhsReplaceSpan;
        }

        static ChainTypeAtCursor compute(String input, List<Token> tokens, int cursor, Class<?> rootType) {
            // Find the last unmatched '{' before cursor
            int openIdx = -1;
            int depth = 0;
            for (int i = 0; i < tokens.size(); i++) {
                Token t = tokens.get(i);
                if (t.start >= cursor) break;
                if (t.type == TokenType.LBRACE) { depth++; openIdx = i; }
                else if (t.type == TokenType.RBRACE) { depth--; if (depth < 0) depth = 0; }
            }
            if (openIdx < 0) {
                return new ChainTypeAtCursor(rootType, false, null, false, null);
            }

            // Walk tokens from openIdx+1 to cursor at brace-depth 1, track paren-depth to ignore inner calls
            int braceDepth = 1;
            int parenDepth = 0;
            Class<?> currentType = rootType;
            boolean lhsReadyForComparator = false;
            Class<?> lhsType = null;
            boolean afterComparator = false;
            Span rhsSpan = null;

            // Pre-load commands per class on demand
            int i = openIdx + 1;
            while (i < tokens.size()) {
                Token t = tokens.get(i);
                if (t.start >= cursor) break;
                if (t.type == TokenType.LBRACE) { braceDepth++; i++; continue; }
                if (t.type == TokenType.RBRACE) { braceDepth = Math.max(0, braceDepth - 1); i++; continue; }

                if (braceDepth != 1) { i++; continue; }

                if (t.type == TokenType.LPAREN) { parenDepth++; i++; continue; }
                if (t.type == TokenType.RPAREN) { parenDepth = Math.max(0, parenDepth - 1); i++; continue; }

                if (parenDepth > 0) { i++; continue; }

                // At top-level within the filter: try to recognize a chain segment: IDENT [ "(" ... ")" ] or bare IDENT
                if (t.type == TokenType.IDENT) {
                    // Look ahead for '(' to decide if it's a call or bare property
                    Token next = (i + 1 < tokens.size()) ? tokens.get(i + 1) : null;

                    if (next != null && next.type == TokenType.LPAREN && next.start < cursor) {
                        // function call; resolve command and its return type
                        ICommand<?> cmd = resolveCommand(currentType, t.text);
                        // If found, skip ahead to its matching ')' and set currentType
                        int callEnd = findMatchingParen(tokens, i + 1, cursor);
                        if (cmd != null) {
                            // If the call closes before cursor, we can update currentType to its return type.
                            if (callEnd >= 0 && tokens.get(callEnd).end <= cursor) {
                                currentType = cmd.getType();
                                i = callEnd + 1;
                                continue;
                            } else {
                                // We're inside this call; currentType remains the pre-call type for chain, comparator detection is not ready
                                lhsReadyForComparator = false;
                                // break minimal: leave after allowing arg handling by ArgumentContext
                                break;
                            }
                        } else {
                            // Unknown command name; cannot advance type; break parse
                            break;
                        }
                    } else {
                        // Bare identifier => zero-arg function?
                        ICommand<?> cmd = resolveZeroArgCommand(currentType, t.text);
                        if (cmd != null) {
                            currentType = cmd.getType();
                            i++;
                            continue;
                        } else {
                            // Unknown ident; break
                            break;
                        }
                    }
                } else if (isComparator(t)) {
                    lhsReadyForComparator = true;
                    lhsType = currentType;
                    afterComparator = true;
                    // Determine RHS span starting at t.end
                    int startRhs = t.end;
                    // Skip whitespace
                    while (startRhs < input.length() && Character.isWhitespace(input.charAt(startRhs))) startRhs++;
                    int endRhs = cursor;
                    rhsSpan = new Span(startRhs, endRhs);
                    i++;
                    continue;
                } else if (t.type == TokenType.DOT) {
                    // chain continues, nothing to do
                    i++;
                    continue;
                } else {
                    // some operator or unknown at top-level; we exit type inference
                    i++;
                }
            }

            // Even without comparator, after reading a chain expression we can suggest comparators for currentType
            if (!afterComparator) {
                lhsReadyForComparator = true;
                lhsType = currentType;
            }

            return new ChainTypeAtCursor(currentType, lhsReadyForComparator, lhsType, afterComparator, rhsSpan);
        }

        private static boolean isComparator(Token t) {
            return t.type == TokenType.EQ || t.type == TokenType.NEQ ||
                    t.type == TokenType.GT || t.type == TokenType.GE ||
                    t.type == TokenType.LT || t.type == TokenType.LE;
        }

        private static int findMatchingParen(List<Token> tokens, int lpIndex, int cursor) {
            int depth = 0;
            for (int i = lpIndex; i < tokens.size(); i++) {
                Token t = tokens.get(i);
                if (t.type == TokenType.LPAREN) depth++;
                else if (t.type == TokenType.RPAREN) {
                    depth--;
                    if (depth == 0) return i;
                }
                if (t.start >= cursor) break; // unmatched before cursor
            }
            return -1;
        }
    }

    private static ICommand<?> resolveCommand(Class<?> onType, String name) {
        return resolveCommandFlexible(onType, name);
    }

    private static ICommand<?> resolveZeroArgCommand(Class<?> onType, String name) {
        ICommand<?> c = resolveCommandFlexible(onType, name);
        if (c == null) return null;
        List<ParameterData> ps = c.getUserParameters();
        if (ps == null || ps.isEmpty()) return c;
        boolean allOptional = true;
        for (ParameterData p : ps) { if (!p.isOptional()) { allOptional = false; break; } }
        return allOptional ? c : null;
    }

    // Flexible resolver that allows optional prefixes
    private static ICommand<?> resolveCommandFlexible(Class<?> onType, String typed) {
        Placeholders<?, Object> ph = PlaceholdersMap.get().get(onType);
        if (ph == null) return null;

        // 1) exact map lookup
        ICommand<?> exact = (ICommand<?>) ph.getCommands().get(typed);
        if (exact != null) return exact;

        // 2) match by fullPath and by dropping common prefixes
        List<ICommand<?>> candidates = new ArrayList<>();
        for (CommandCallable value : ph.getCommands().getSubcommands().values()) {
            if (value instanceof ParametricCallable<?> cmd) {
                String name = cmd.getFullPath();
                if (name.equalsIgnoreCase(typed) || dropCommonPrefix(name).equalsIgnoreCase(typed)) {
                    candidates.add(cmd);
                }
            }
        }
        if (candidates.size() == 1) return candidates.get(0);

        // Ambiguous or not found
        return null;
    }

    // Argument context finder: figure out if the cursor is inside a function arg list and what it expects
    static final class ArgumentContext {
        final int cursor;
        final Class<?> onType;
        final ICommand<?> function;
        final boolean expectingName;
        final String namePrefix;
        final Span nameReplaceSpan;
        final boolean expectingValue;
        final String valuePrefix;
        final Span valueReplaceSpan;
        final Set<String> alreadyNamedParams;
        final Type paramType;

        ArgumentContext(int cursor,
                        Class<?> onType,
                        ICommand<?> function,
                        boolean expectingName,
                        String namePrefix,
                        Span nameReplaceSpan,
                        boolean expectingValue,
                        String valuePrefix,
                        Span valueReplaceSpan,
                        Set<String> alreadyNamedParams,
                        Type paramType) {
            this.cursor = cursor;
            this.onType = onType;
            this.function = function;
            this.expectingName = expectingName;
            this.namePrefix = namePrefix;
            this.nameReplaceSpan = nameReplaceSpan;
            this.expectingValue = expectingValue;
            this.valuePrefix = valuePrefix;
            this.valueReplaceSpan = valueReplaceSpan;
            this.alreadyNamedParams = alreadyNamedParams;
            this.paramType = paramType;
        }

        static ArgumentContext find(String input, List<Token> tokens, int cursor, Class<?> rootType) {
            // Walk back to find the nearest unmatched '(' with a preceding IDENT inside the current filter block
            int braceDepth = 0;
            for (Token t : tokens) {
                if (t.start >= cursor) break;
                if (t.type == TokenType.LBRACE) braceDepth++;
                else if (t.type == TokenType.RBRACE) braceDepth = Math.max(0, braceDepth - 1);
            }
            if (braceDepth == 0) return null;

            // Find LPAREN whose matching RPAREN is after cursor (or missing) inside the current brace scope
            int depthBrace = 0;
            Deque<Integer> parenStack = new ArrayDeque<>();
            int funcIdentIndex = -1;
            Integer lparenIndex = -1;

            // Also track type across chain before the function
            Class<?> currentType = rootType;

            for (int i = 0; i < tokens.size(); i++) {
                Token t = tokens.get(i);
                if (t.start >= cursor) break;
                if (t.type == TokenType.LBRACE) { depthBrace++; }
                else if (t.type == TokenType.RBRACE) { depthBrace--; }

                if (depthBrace <= 0) continue;

                if (t.type == TokenType.IDENT) {
                    // remember potential function name if next token is LPAREN
                    if (i + 1 < tokens.size() && tokens.get(i + 1).type == TokenType.LPAREN) {
                        // To resolve command, we need the currentType at this point. Try to compute it quickly:
                        // We assume well-formed chain; this is a best-effort.
                        funcIdentIndex = i;
                    }
                }
                if (t.type == TokenType.LPAREN) {
                    parenStack.push(i);
                } else if (t.type == TokenType.RPAREN) {
                    if (!parenStack.isEmpty()) parenStack.pop();
                }
            }

            if (parenStack.isEmpty()) return null;
            lparenIndex = parenStack.peek();
            if (lparenIndex == null) return null;

            // Find the IDENT immediately preceding this '('
            int identIdx = -1;
            for (int i = lparenIndex - 1; i >= 0; i--) {
                Token t = tokens.get(i);
                if (t.type == TokenType.WS) continue;
                if (t.type == TokenType.IDENT) { identIdx = i; break; }
                else break;
            }
            if (identIdx < 0) return null;

            // Compute the onType before this ident by scanning from the start of the filter
            Class<?> onType = computeOnTypeBeforeIdent(tokens, identIdx, rootType);

            // Resolve function by name on onType
            String funcName = tokens.get(identIdx).text;
            ICommand<?> cmd = resolveCommand(onType, funcName);
            if (cmd == null) return null;

            // Now decide if we are typing a parameter name or value
            // Extract substring inside parentheses from after '(' to cursor
            int argsStart = tokens.get(lparenIndex).end;
            int argsEnd = cursor;
            if (argsStart > argsEnd) return null; // cursor before '('
            String argRegion = input.substring(argsStart, argsEnd);
            // parse the last segment after the last comma that is not inside nested parens/braces
            int lastComma = findLastTopLevelComma(argRegion);

            int segmentStart = (lastComma < 0) ? 0 : lastComma + 1;
            String seg = argRegion.substring(segmentStart).trim();

            // Collect already named params in earlier segments:
            Set<String> already = new HashSet<>();
            if (lastComma >= 0) {
                String beforeSeg = argRegion.substring(0, segmentStart);
                collectNamedParams(beforeSeg, already);
            }

            // Detect "name: value" or just "name" or direct value for positional
            int colonIdx = seg.indexOf(':');
            if (colonIdx < 0) {
                // Expecting a name (preferred) or positional value
                Span nameReplace = consumeWordSpan(input, argsStart + segmentStart + leadingWs(argRegion.substring(segmentStart)));
                String namePrefix = seg; // whole segment is considered as name prefix
                return new ArgumentContext(cursor, onType, cmd,
                        true, namePrefix, nameReplace, false, null, null, already, null);
            } else {
                String name = seg.substring(0, colonIdx).trim();
                Span nameReplace = locateSpan(input, argsStart + segmentStart, name);
                Type paramType = typeOfParam(cmd, name);
                int valueAbsStart = argsStart + segmentStart + seg.indexOf(':') + 1;
                // Skip spaces before value
                int ws = 0;
                while (valueAbsStart + ws < cursor && Character.isWhitespace(input.charAt(valueAbsStart + ws))) ws++;
                int valueStart = valueAbsStart + ws;
                Span valueReplace = new Span(valueStart, cursor);
                String valuePrefix = input.substring(valueReplace.start, cursor);
                return new ArgumentContext(cursor, onType, cmd,
                        false, null, null, true, valuePrefix, valueReplace, already, paramType);
            }
        }

        private static Class<?> computeOnTypeBeforeIdent(List<Token> tokens, int identIdx, Class<?> rootType) {
            // Simple forward scan from the nearest '{' up to identIdx to compute chain type
            int openIdx = -1;
            int depth = 0;
            for (int i = 0; i <= identIdx; i++) {
                Token t = tokens.get(i);
                if (t.type == TokenType.LBRACE) { depth++; openIdx = i; }
                else if (t.type == TokenType.RBRACE) { depth--; }
            }
            if (openIdx < 0) return rootType;
            Class<?> currentType = rootType;
            int braceDepth = 1;
            int parenDepth = 0;
            for (int i = openIdx + 1; i < identIdx; i++) {
                Token t = tokens.get(i);
                if (t.type == TokenType.LBRACE) { braceDepth++; continue; }
                if (t.type == TokenType.RBRACE) { braceDepth--; continue; }
                if (braceDepth != 1) continue;

                if (t.type == TokenType.LPAREN) { parenDepth++; continue; }
                if (t.type == TokenType.RPAREN) { parenDepth--; continue; }
                if (parenDepth > 0) continue;

                if (t.type == TokenType.IDENT) {
                    Token next = (i + 1 < tokens.size()) ? tokens.get(i + 1) : null;
                    if (next != null && next.type == TokenType.LPAREN) {
                        ICommand<?> cmd = resolveCommand(currentType, t.text);
                        if (cmd != null) {
                            int endParen = findMatchingParen(tokens, i + 1);
                            if (endParen >= 0 && endParen < identIdx) {
                                currentType = cmd.getType();
                                i = endParen;
                                continue;
                            } else {
                                break;
                            }
                        } else break;
                    } else {
                        ICommand<?> cmd = resolveZeroArgCommand(currentType, t.text);
                        if (cmd != null) {
                            currentType = cmd.getType();
                        } else {
                            // unknown identifier; stop
                            break;
                        }
                    }
                }
            }
            return currentType;
        }

        private static Type typeOfParam(ICommand<?> cmd, String paramName) {
            for (ParameterData p : cmd.getUserParameters()) {
                if (p.getName().equalsIgnoreCase(paramName)) return p.getType();
            }
            return null;
        }

        private static int leadingWs(String s) {
            int k = 0; while (k < s.length() && Character.isWhitespace(s.charAt(k))) k++; return k;
        }

        private static int findLastTopLevelComma(String s) {
            int brace = 0, paren = 0;
            int last = -1;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '{') brace++;
                else if (c == '}') brace = Math.max(0, brace - 1);
                else if (c == '(') paren++;
                else if (c == ')') paren = Math.max(0, paren - 1);
                else if (c == ',' && brace == 0 && paren == 0) last = i;
            }
            return last;
        }

        private static void collectNamedParams(String beforeSeg, Set<String> into) {
            // best-effort: find occurrences of <ident> : <value> at top-level commas
            String[] parts = beforeSeg.split(",");
            for (String part : parts) {
                String p = part.trim();
                int idx = p.indexOf(':');
                if (idx > 0) {
                    String name = p.substring(0, idx).trim();
                    if (!name.isEmpty()) into.add(name);
                }
            }
        }

        private static Span consumeWordSpan(String input, int at) {
            int start = at;
            while (start < input.length() && Character.isWhitespace(input.charAt(start))) start++;
            int end = start;
            while (end < input.length() && CharClasses.isIdentChar(input.charAt(end))) end++;
            return new Span(start, end);
        }

        private static int findWordStart(String input, int pos) {
            if (input == null) return 0;
            pos = Math.max(0, Math.min(pos, input.length()));
            while (pos > 0) {
                char c = input.charAt(pos - 1);
                if (!CharClasses.isIdentChar(c)) break;
                pos--;
            }
            return pos;
        }

        private static Span locateSpan(String input, int absStart, String piece) {
            int start = absStart;
            int end = absStart;
            start = findWordStart(input, absStart + skipWs(input, absStart));
            end = start + piece.length();
            end = Math.min(end, input.length());
            return new Span(start, end);
        }

        private static int skipWs(String s, int i) {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
            return i;
        }

        private static int findMatchingParen(List<Token> tokens, int lpIndex) {
            int depth = 0;
            for (int i = lpIndex; i < tokens.size(); i++) {
                Token t = tokens.get(i);
                if (t.type == TokenType.LPAREN) depth++;
                else if (t.type == TokenType.RPAREN) {
                    depth--;
                    if (depth == 0) return i;
                }
            }
            return -1;
        }
    }

    // Completion builder and result classes

    static final class CompletionBuilder {
        private final String input;
        private final List<CompletionItem> items = new ArrayList<>();
        CompletionBuilder(String input) { this.input = input; }
        int size() { return items.size(); }

        CompletionItem add(String insertText, int replaceFrom, int replaceTo) {
            CompletionItem it = new CompletionItem(insertText, replaceFrom, replaceTo, null, 0);
            items.add(it);
            return it;
        }

        CompletionResult build() {
            // De-duplicate by label+range
            Map<String, CompletionItem> uniq = new LinkedHashMap<>();
            for (CompletionItem it : items) {
                String key = it.getInsertText() + "@" + it.getReplaceFrom() + ":" + it.getReplaceTo();
                uniq.putIfAbsent(key, it);
            }
            return new CompletionResult(new ArrayList<>(uniq.values()));
        }
    }

    public static final class CompletionResult {
        private final List<CompletionItem> items;
        public CompletionResult(List<CompletionItem> items) {
            this.items = items != null ? items : Collections.emptyList();
        }
        public List<CompletionItem> getItems() { return items; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("CompletionResult[");
            for (CompletionItem it : items) {
                sb.append("\n  ").append(it);
            }
            sb.append("\n]");
            return sb.toString();
        }
    }

    public static final class CompletionItem {
        private final String insertText;
        private final int replaceFrom;
        private final int replaceTo;
        private String detail;
        private final int priority;

        public CompletionItem(String insertText, int replaceFrom, int replaceTo, String detail, int priority) {
            this.insertText = insertText;
            this.replaceFrom = replaceFrom;
            this.replaceTo = replaceTo;
            this.detail = detail;
            this.priority = priority;
        }
        public String getInsertText() { return insertText; }
        public int getReplaceFrom() { return replaceFrom; }
        public int getReplaceTo() { return replaceTo; }
        public String getDetail() { return detail; }
        public int getPriority() { return priority; }

        public CompletionItem detail(String d) { this.detail = d; return this; }

        @Override
        public String toString() {
            return "Item(insert=\"" + insertText + "\", [" + replaceFrom + "," + replaceTo + "], detail=" + detail + ")";
        }
    }
}