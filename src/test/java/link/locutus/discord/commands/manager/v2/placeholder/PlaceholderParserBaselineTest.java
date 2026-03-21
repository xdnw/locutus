package link.locutus.discord.commands.manager.v2.placeholder;

import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.autocomplete.PredicateDslCompleter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderParserBaselineTest {
    private PlaceholderParserTestSupport.PlaceholderContractFactory<PlaceholderParserTestSupport.TestRoot> factory;

    @BeforeEach
    void setUp() {
        factory = PlaceholderParserTestSupport.currentFactory();
    }

    @Test
    void setSelectorsCommaSequenceUnionsMatches() {
        Set<String> names = names(factory.parseSet("name:Alpha,name:Beta"));
        assertEquals(Set.of("Alpha", "Beta"), names);
    }

    @Test
    void setSelectorsPipeUnionsMatches() {
        Set<String> names = names(factory.parseSet("name:Alpha|name:Gamma"));
        assertEquals(Set.of("Alpha", "Gamma"), names);
    }

    @Test
    void setSelectorThenFilterCommaNarrowsExistingSelection() {
        Set<String> names = names(factory.parseSet("group:red,{getScore}>15"));
        assertEquals(Set.of("Gamma"), names);
    }

    @Test
    void predicateSupportsSymmetricComparisons() {
        Predicate<PlaceholderParserTestSupport.TestRoot> predicate = factory.parsePredicate("10<{getScore},{getScore}>{getPriority}");
        List<String> matching = matchingNames(predicate);
        assertEquals(List.of("Alpha", "Gamma"), matching);
    }

    @Test
    void predicateGroupedArithmeticIsCurrentlyRejected() {
        assertThrows(IllegalArgumentException.class, () -> factory.parsePredicate("({getScore}+{getPriority})>15"));
    }

    @Test
    void predicateSupportsBooleanOr() {
        Predicate<PlaceholderParserTestSupport.TestRoot> predicate = factory.parsePredicate("{isEnabled}|{getScore}>20");
        List<String> matching = matchingNames(predicate);
        assertEquals(List.of("Alpha", "Gamma"), matching);
    }

    @Test
    void stringFormatterInterpolatesMultiplePlaceholders() {
        TypedFunction<PlaceholderParserTestSupport.TestRoot, String> formatter = factory.compileString("Name {getName} [{getGroup}] score={getScore}");
        assertEquals("Name Alpha [red] score=12.5", formatter.applyCached(factory.sampleData().get(0)));
    }

    @Test
    void doubleFormatterSupportsArithmetic() {
        TypedFunction<PlaceholderParserTestSupport.TestRoot, Double> formatter = factory.compileDouble("({getScore}+{getPriority})/2");
        assertEquals(7.75d, formatter.applyCached(factory.sampleData().get(0)));
        assertEquals(4d, formatter.applyCached(factory.sampleData().get(1)));
        assertEquals(13d, formatter.applyCached(factory.sampleData().get(2)));
    }

    @Test
    void numericFormatterWithNestedPredicateArgumentCurrentlyFailsAtEvaluation() {
        TypedFunction<PlaceholderParserTestSupport.TestRoot, Double> formatter = factory.compileDouble("{countChildren(filter:{getScore}>5)}+{getPriority}");
        assertThrows(ClassCastException.class, () -> formatter.applyCached(factory.sampleData().get(0)));
    }

    @Test
    void functionResolutionIsCaseInsensitiveAndPrefixAgnosticForPrefixedMethods() {
        TypedFunction<PlaceholderParserTestSupport.TestRoot, Double> formatter = factory.compileDouble("{score}+{GETPRIORITY}");
        assertEquals(15.5d, formatter.applyCached(factory.sampleData().get(0)));
    }

    @Test
    void ampersandInSetExpressionsCurrentlyYieldsNoMatches() {
        assertEquals(Set.of(), names(factory.parseSet("name:Alpha&name:Beta")));
    }

    @Test
    void currentBaselineDoesNotSupportBracketIndexSyntax() {
        assertThrows(IllegalArgumentException.class, () -> factory.compileDouble("{getChild[0].getScore}"));
    }

    @Test
    void completionSuggestsDirectOptionValues() {
        PredicateDslCompleter.CompletionResult result = factory.complete("Al", 2);
        List<String> inserts = inserts(result);
        assertTrue(inserts.contains("Alpha"));
    }

    @Test
    void completionInsideFilterContextReturnsSuggestions() {
        PredicateDslCompleter.CompletionResult result = factory.complete("*,{", 3);
        assertTrue(!result.getItems().isEmpty());
    }

    private Set<String> names(Set<PlaceholderParserTestSupport.TestRoot> roots) {
        return roots.stream().map(PlaceholderParserTestSupport.TestRoot::name).collect(Collectors.toSet());
    }

    private List<String> matchingNames(Predicate<PlaceholderParserTestSupport.TestRoot> predicate) {
        return factory.sampleData().stream()
                .filter(predicate)
                .map(PlaceholderParserTestSupport.TestRoot::name)
                .collect(Collectors.toList());
    }

    private List<String> inserts(PredicateDslCompleter.CompletionResult result) {
        return result.getItems().stream()
                .map(PredicateDslCompleter.CompletionItem::getInsertText)
                .collect(Collectors.toList());
    }
}
