package link.locutus.discord.commands.manager.v2.placeholder;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.SimpleValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Autocomplete;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderEngine;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.SelectorInfo;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.autocomplete.PredicateDslCompleter;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.CommandRuntimeServices;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

final class PlaceholderParserTestSupport {
    private static CurrentEngineFactory currentFactory;

    private PlaceholderParserTestSupport() {
    }

    static synchronized CurrentEngineFactory currentFactory() {
        if (currentFactory == null) {
            currentFactory = new CurrentEngineFactory();
        }
        return currentFactory;
    }

    interface PlaceholderContractFactory<T> {
        Set<T> parseSet(String input);

        Predicate<T> parsePredicate(String input);

        TypedFunction<T, String> compileString(String input);

        TypedFunction<T, Double> compileDouble(String input);

        PredicateDslCompleter.CompletionResult complete(String input, int cursor);

        List<T> sampleData();
    }

    static final class CurrentEngineFactory implements PlaceholderContractFactory<TestRoot> {
        private final PlaceholderEngine engine;
        private final Placeholders<TestRoot, Void> rootPlaceholders;
        private final Placeholders<TestChild, Void> childPlaceholders;
        private final LocalValueStore locals;
        private final List<TestRoot> sampleData;
        private final PredicateDslCompleter<TestRoot> completer;

        CurrentEngineFactory() {
            this.engine = new PlaceholderEngine(
                    PWBindings.createDefaultStore(),
                    PWBindings.createDefaultValidators(),
                    PWBindings.createDefaultPermisser(),
                    CommandRuntimeServices.builder(modifier -> null).build());

            this.sampleData = List.of(TestRoot.alpha(), TestRoot.beta(), TestRoot.gamma());
            this.rootPlaceholders = createRootPlaceholders(engine.getStore(), engine.getValidators(), engine.getPermisser(), sampleData);
            this.childPlaceholders = createChildPlaceholders(engine.getStore(), engine.getValidators(), engine.getPermisser(), sampleData);

            registerValueBindings((SimpleValueStore) engine.getStore());
            registerAutocomplete((SimpleValueStore) engine.getStore(), sampleData);
            engine.add(childPlaceholders).add(rootPlaceholders).initCommands();

            this.locals = engine.createLocals();
            this.completer = new PredicateDslCompleter<>(engine.getStore(), TestRoot.class, engine);
        }

        @Override
        public Set<TestRoot> parseSet(String input) {
            return rootPlaceholders.parseSet(locals, input);
        }

        @Override
        public Predicate<TestRoot> parsePredicate(String input) {
            return rootPlaceholders.parseFilter(locals, input);
        }

        @Override
        public TypedFunction<TestRoot, String> compileString(String input) {
            return rootPlaceholders.getFormatFunction(locals, input);
        }

        @Override
        public TypedFunction<TestRoot, Double> compileDouble(String input) {
            return rootPlaceholders.getDoubleFunction(locals, input);
        }

        @Override
        public PredicateDslCompleter.CompletionResult complete(String input, int cursor) {
            return completer.apply(input, cursor);
        }

        @Override
        public List<TestRoot> sampleData() {
            return sampleData;
        }
    }

    private static Placeholders<TestRoot, Void> createRootPlaceholders(
            ValueStore store,
            ValidatorStore validators,
            PermissionHandler permisser,
            List<TestRoot> sampleData
    ) {
        return new link.locutus.discord.commands.manager.v2.binding.bindings.SimpleVoidPlaceholders<>(
                TestRoot.class,
                store,
                validators,
                permisser,
                "Test placeholder root",
                (instance, valueStore, input) -> parseRootSelector(sampleData, input),
                (instance, valueStore, input) -> parseRootDirectPredicate(sampleData, input),
                TestRoot::name
        ) {
            @Override
            public Set<String> getSheetColumns() {
                return Set.of("name", "group", "status", "score");
            }

            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                return new LinkedHashSet<>(List.of(
                        new SelectorInfo("*", null, "All test roots"),
                        new SelectorInfo("name:VALUE", "name:Alpha", "Select by name"),
                        new SelectorInfo("group:VALUE", "group:red", "Select by group"),
                        new SelectorInfo("status:VALUE", "status:ACTIVE", "Select by status"),
                        new SelectorInfo("VALUE", "Alpha", "Unqualified name")
                ));
            }

            @Override
            public link.locutus.discord.commands.manager.v2.command.ParametricCallable get(String cmd) {
                link.locutus.discord.commands.manager.v2.command.ParametricCallable callable = super.get(cmd);
                if (callable != null) {
                    return callable;
                }
                String normalized = cmd.toLowerCase(Locale.ROOT);
                for (String key : getKeys()) {
                    if (matchesAlias(key.toLowerCase(Locale.ROOT), normalized)) {
                        return super.get(key);
                    }
                }
                return null;
            }
        };
    }

    private static Placeholders<TestChild, Void> createChildPlaceholders(
            ValueStore store,
            ValidatorStore validators,
            PermissionHandler permisser,
            List<TestRoot> sampleData
    ) {
        return new link.locutus.discord.commands.manager.v2.binding.bindings.SimpleVoidPlaceholders<>(
                TestChild.class,
                store,
                validators,
                permisser,
                "Test child placeholder root",
                (instance, valueStore, input) -> parseChildSelector(sampleData, input),
                (instance, valueStore, input) -> parseChildDirectPredicate(sampleData, input),
                TestChild::name
        ) {
            @Override
            public Set<String> getSheetColumns() {
                return Set.of("name", "score");
            }

            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                return new LinkedHashSet<>(List.of(
                        new SelectorInfo("name:VALUE", "name:A0", "Select child by name"),
                        new SelectorInfo("VALUE", "A0", "Unqualified child name")
                ));
            }

            @Override
            public link.locutus.discord.commands.manager.v2.command.ParametricCallable get(String cmd) {
                link.locutus.discord.commands.manager.v2.command.ParametricCallable callable = super.get(cmd);
                if (callable != null) {
                    return callable;
                }
                String normalized = cmd.toLowerCase(Locale.ROOT);
                for (String key : getKeys()) {
                    if (matchesAlias(key.toLowerCase(Locale.ROOT), normalized)) {
                        return super.get(key);
                    }
                }
                return null;
            }
        };
    }

    private static boolean matchesAlias(String declared, String requested) {
        if (declared.equalsIgnoreCase(requested)) {
            return true;
        }
        for (String prefix : List.of("get", "is", "can", "has")) {
            if (declared.startsWith(prefix) && declared.substring(prefix.length()).equalsIgnoreCase(requested)) {
                return true;
            }
            if (requested.startsWith(prefix) && requested.substring(prefix.length()).equalsIgnoreCase(declared)) {
                return true;
            }
        }
        return false;
    }

    private static Set<TestRoot> parseRootSelector(List<TestRoot> sampleData, String input) {
        String trimmed = input.trim();
        if (trimmed.equals("*")) {
            return new LinkedHashSet<>(sampleData);
        }
        if (trimmed.startsWith("name:")) {
            String value = trimmed.substring("name:".length()).trim();
            return sampleData.stream()
                    .filter(root -> root.name().equalsIgnoreCase(value))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        if (trimmed.startsWith("group:")) {
            String value = trimmed.substring("group:".length()).trim();
            return sampleData.stream()
                    .filter(root -> root.group().equalsIgnoreCase(value))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        if (trimmed.startsWith("status:")) {
            String value = trimmed.substring("status:".length()).trim();
            return sampleData.stream()
                    .filter(root -> root.status().name().equalsIgnoreCase(value))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        return sampleData.stream()
                .filter(root -> root.name().equalsIgnoreCase(trimmed))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Predicate<TestRoot> parseRootDirectPredicate(List<TestRoot> sampleData, String input) {
        String trimmed = input.trim();
        if (trimmed.equalsIgnoreCase("active")) {
            return root -> root.status() == TestStatus.ACTIVE;
        }
        Set<TestRoot> selected = parseRootSelector(sampleData, trimmed);
        if (!selected.isEmpty()) {
            return selected::contains;
        }
        throw new IllegalArgumentException("Unknown selector or filter: `" + input + "`");
    }

    private static Set<TestChild> parseChildSelector(List<TestRoot> sampleData, String input) {
        String trimmed = input.trim();
        List<TestChild> allChildren = sampleData.stream().flatMap(root -> root.children().stream()).toList();
        if (trimmed.startsWith("name:")) {
            String value = trimmed.substring("name:".length()).trim();
            return allChildren.stream()
                    .filter(child -> child.name().equalsIgnoreCase(value))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        return allChildren.stream()
                .filter(child -> child.name().equalsIgnoreCase(trimmed))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Predicate<TestChild> parseChildDirectPredicate(List<TestRoot> sampleData, String input) {
        Set<TestChild> selected = parseChildSelector(sampleData, input);
        if (!selected.isEmpty()) {
            return selected::contains;
        }
        throw new IllegalArgumentException("Unknown selector or filter: `" + input + "`");
    }

    private static void registerAutocomplete(SimpleValueStore store, List<TestRoot> sampleData) {
        addAutocompleteParser(store, Key.of(TestRoot.class, Autocomplete.class), prefix -> sampleData.stream()
                .map(TestRoot::name)
                .filter(name -> containsIgnoreCase(name, prefix))
                .collect(Collectors.toList()));

        addAutocompleteParser(store, Key.of(TestStatus.class, Autocomplete.class), prefix -> filterEnum(TestStatus.values(), prefix));
        addAutocompleteParser(store, Key.of(TestMetric.class, Autocomplete.class), prefix -> filterEnum(TestMetric.values(), prefix));
        addAutocompleteParser(store, Key.of(String.class, Autocomplete.class), prefix -> sampleData.stream()
                .flatMap(root -> root.tags().stream())
                .distinct()
                .filter(tag -> containsIgnoreCase(tag, prefix))
                .collect(Collectors.toList()));
    }

    private static void registerValueBindings(SimpleValueStore store) {
        addValueParser(store, Key.of(TestMetric.class), input -> parseEnum(TestMetric.class, input));
        addValueParser(store, Key.of(TestStatus.class), input -> parseEnum(TestStatus.class, input));
    }

    private static boolean containsIgnoreCase(String value, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return true;
        }
        return value.toLowerCase(Locale.ROOT).contains(prefix.toLowerCase(Locale.ROOT));
    }

    private static List<String> filterEnum(Enum<?>[] values, String prefix) {
        List<String> result = new ArrayList<>();
        for (Enum<?> value : values) {
            if (containsIgnoreCase(value.name(), prefix)) {
                result.add(value.name());
            }
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addAutocompleteParser(SimpleValueStore store, Key key, Function<String, List<String>> values) {
        store.addParser(key, new AutocompleteParser(key, values));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addValueParser(SimpleValueStore store, Key key, Function<String, Object> parse) {
        store.addParser(key, new ValueParser(key, parse));
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String input) {
        for (T constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(input.trim())) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Unknown " + type.getSimpleName() + " value: `" + input + "`");
    }

    private static final class AutocompleteParser implements Parser<Object> {
        private final Key<?> key;
        private final Function<String, List<String>> values;

        private AutocompleteParser(Key<?> key, Function<String, List<String>> values) {
            this.key = key;
            this.values = values;
        }

        @Override
        public Object apply(ArgumentStack arg) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object apply(ValueStore store, Object input) {
            return values.apply(input == null ? "" : input.toString());
        }

        @Override
        public boolean isConsumer(ValueStore store) {
            return false;
        }

        @Override
        public Key<?> getKey() {
            return key;
        }

        @Override
        public String getDescription() {
            return "test autocomplete";
        }

        @Override
        public String[] getExamples() {
            return new String[0];
        }

        @Override
        public Class<?>[] getWebType() {
            return new Class<?>[0];
        }

        @Override
        public Map<String, Object> toJson() {
            return Map.of();
        }
    }

    private static final class ValueParser implements Parser<Object> {
        private final Key<?> key;
        private final Function<String, Object> parse;

        private ValueParser(Key<?> key, Function<String, Object> parse) {
            this.key = key;
            this.parse = parse;
        }

        @Override
        public Object apply(ArgumentStack arg) {
            return parse.apply(arg.consumeNext());
        }

        @Override
        public Object apply(ValueStore store, Object input) {
            return parse.apply(input == null ? "" : input.toString());
        }

        @Override
        public boolean isConsumer(ValueStore store) {
            return false;
        }

        @Override
        public Key<?> getKey() {
            return key;
        }

        @Override
        public String getDescription() {
            return "test value parser";
        }

        @Override
        public String[] getExamples() {
            return new String[0];
        }

        @Override
        public Class<?>[] getWebType() {
            return new Class<?>[0];
        }

        @Override
        public Map<String, Object> toJson() {
            return Map.of();
        }
    }

    enum TestStatus {
        ACTIVE,
        INACTIVE
    }

    enum TestMetric {
        PRIMARY,
        SECONDARY
    }

    record TestChild(String rawName, double rawScore, Map<TestMetric, Double> rawMetrics) {
        public String name() {
            return rawName;
        }

        @Command(desc = "Child name")
        public String getName() {
            return rawName;
        }

        @Command(desc = "Child score")
        public double getScore() {
            return rawScore;
        }

        @Command(desc = "Child metric")
        public double getMetric(TestMetric metric) {
            return rawMetrics.get(metric);
        }

        public double score() {
            return rawScore;
        }
    }

    record TestRoot(
            int rawId,
            String rawName,
            String rawGroup,
            TestStatus rawStatus,
            boolean rawEnabled,
            double rawScore,
            int rawPriority,
            String rawLabel,
            List<String> rawTags,
            List<TestChild> rawChildren,
            Map<TestMetric, Double> rawMetrics
    ) {
        public String name() {
            return rawName;
        }

        public String group() {
            return rawGroup;
        }

        public TestStatus status() {
            return rawStatus;
        }

        static TestRoot alpha() {
            return new TestRoot(
                    1,
                    "Alpha",
                    "red",
                    TestStatus.ACTIVE,
                    true,
                    12.5,
                    3,
                    "A-label",
                    List.of("ALPHA", "SHARED"),
                    List.of(
                            new TestChild("A0", 5.0, Map.of(TestMetric.PRIMARY, 5.0, TestMetric.SECONDARY, 1.0)),
                            new TestChild("A1", 9.0, Map.of(TestMetric.PRIMARY, 9.0, TestMetric.SECONDARY, 2.0))
                    ),
                    Map.of(TestMetric.PRIMARY, 100.0, TestMetric.SECONDARY, 20.0)
            );
        }

        static TestRoot beta() {
            return new TestRoot(
                    2,
                    "Beta",
                    "blue",
                    TestStatus.INACTIVE,
                    false,
                    7.0,
                    1,
                    "B-label",
                    List.of("BETA", "SHARED"),
                    List.of(
                            new TestChild("B0", 2.0, Map.of(TestMetric.PRIMARY, 2.0, TestMetric.SECONDARY, 5.0))
                    ),
                    Map.of(TestMetric.PRIMARY, 40.0, TestMetric.SECONDARY, 10.0)
            );
        }

        static TestRoot gamma() {
            return new TestRoot(
                    3,
                    "Gamma",
                    "red",
                    TestStatus.ACTIVE,
                    true,
                    21.0,
                    5,
                    "G-label",
                    List.of("GAMMA"),
                    List.of(
                            new TestChild("G0", 11.0, Map.of(TestMetric.PRIMARY, 11.0, TestMetric.SECONDARY, 6.0)),
                            new TestChild("G1", 1.0, Map.of(TestMetric.PRIMARY, 1.0, TestMetric.SECONDARY, 1.0))
                    ),
                    Map.of(TestMetric.PRIMARY, 200.0, TestMetric.SECONDARY, 30.0)
            );
        }

        @Command(desc = "Identifier")
        public int getId() {
            return rawId;
        }

        @Command(desc = "Name")
        public String getName() {
            return rawName;
        }

        @Command(desc = "Group")
        public String getGroup() {
            return rawGroup;
        }

        @Command(desc = "Status")
        public TestStatus getStatus() {
            return rawStatus;
        }

        @Command(desc = "Enabled flag")
        public boolean isEnabled() {
            return rawEnabled;
        }

        @Command(desc = "Score")
        public double getScore() {
            return rawScore;
        }

        @Command(desc = "Priority")
        public int getPriority() {
            return rawPriority;
        }

        @Command(desc = "Label")
        public String getLabel() {
            return rawLabel;
        }

        @Command(desc = "Primary child")
        public TestChild getChild() {
            return rawChildren.get(0);
        }

        @Command(desc = "Child by index")
        public TestChild getChild(int index) {
            return rawChildren.get(index);
        }

        @Command(desc = "Tag by index")
        public String getTag(int index) {
            return rawTags.get(index);
        }

        @Command(desc = "Metric by key")
        public double getMetric(TestMetric metric) {
            return rawMetrics.get(metric);
        }

        public double score() {
            return rawScore;
        }

        @Command(desc = "Count children matching nested predicate")
        public int countChildren(Predicate<TestChild> filter) {
            int count = 0;
            for (TestChild child : rawChildren) {
                if (filter.test(child)) {
                    count++;
                }
            }
            return count;
        }

        public List<String> tags() {
            return rawTags;
        }

        public List<TestChild> children() {
            return rawChildren;
        }
    }
}
