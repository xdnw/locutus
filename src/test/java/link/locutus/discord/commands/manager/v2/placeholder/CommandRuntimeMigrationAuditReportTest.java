package link.locutus.discord.commands.manager.v2.placeholder;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRuntimeMigrationAuditReportTest {
    @Test
    void summarizePlaceholderOwnerAuditMarksClassifiedDebtUnresolved() {
        CommandRuntimeMigrationAudit.PlaceholderOwnerAuditSummary summary =
                new CommandRuntimeMigrationAudit.PlaceholderOwnerAuditSummary(
                        List.of(),
                        List.of(),
                        List.of(
                                ownerFailure("link.locutus.discord.db.entities.DBNation",
                                        "src/main/java/link/locutus/discord/db/entities/DBNation.java",
                                        60, 40, 23),
                                ownerFailure("link.locutus.discord.db.entities.DBAlliance",
                                        "src/main/java/link/locutus/discord/db/entities/DBAlliance.java",
                                        20, 15, 10),
                                ownerFailure("link.locutus.discord.db.entities.DBCity",
                                        "src/main/java/link/locutus/discord/db/entities/DBCity.java",
                                        3, 2)));

        CommandRuntimeMigrationAudit.ScenarioResult result =
                CommandRuntimeMigrationAudit.summarizePlaceholderOwnerAudit(summary);

        assertEquals(CommandRuntimeMigrationAudit.ScenarioStatus.UNRESOLVED, result.status());
        assertTrue(result.detail().contains("owner-layer debt summary: 3 files, 173 direct Locutus singleton call sites"));
        assertHotspotOrder(result.detail(), "DBNation", "DBAlliance", "DBCity");
    }

    @Test
    void summarizePlaceholderOwnerAuditFailsWhenNeutralOwnersStillUseLocutus() {
        CommandRuntimeMigrationAudit.PlaceholderOwnerAuditSummary summary =
                new CommandRuntimeMigrationAudit.PlaceholderOwnerAuditSummary(
                        List.of("link.locutus.discord.SomeType -> src/main/java/link/locutus/discord/SomeType.java"),
                        List.of(ownerFailure("link.locutus.discord.db.entities.DBTreasure",
                                "src/main/java/link/locutus/discord/db/entities/DBTreasure.java",
                                4, 1)),
                        List.of(ownerFailure("link.locutus.discord.db.entities.DBNation",
                                "src/main/java/link/locutus/discord/db/entities/DBNation.java",
                                80, 43)));

        CommandRuntimeMigrationAudit.ScenarioResult result =
                CommandRuntimeMigrationAudit.summarizePlaceholderOwnerAudit(summary);

        assertEquals(CommandRuntimeMigrationAudit.ScenarioStatus.FAIL, result.status());
        assertTrue(result.detail().contains("missing source coverage:"));
        assertTrue(result.detail().contains("files with unclassified direct Locutus access:"));
        assertTrue(result.detail().contains("classified live app owner debt also still exists:"));
    }

    @Test
    void auditReportRendersUnresolvedScenariosSeparately() {
        CommandRuntimeMigrationAudit.AuditReport report = new CommandRuntimeMigrationAudit.AuditReport();
        report.add(new CommandRuntimeMigrationAudit.ScenarioResult(
                "clean seam",
                CommandRuntimeMigrationAudit.ScenarioStatus.PASS,
                "still clean"));
        report.add(new CommandRuntimeMigrationAudit.ScenarioResult(
                "owner debt",
                CommandRuntimeMigrationAudit.ScenarioStatus.UNRESOLVED,
                "still pending"));

        String rendered = report.render(true);

        assertFalse(report.hasFailures());
        assertTrue(report.hasUnresolved());
        assertTrue(rendered.contains("[UNRESOLVED] owner debt"));
        assertTrue(rendered.contains("Summary: 1 passed, 1 unresolved, 0 failed"));
        assertTrue(rendered.contains("Unresolved scenarios remain migration work and are reported without changing the exit code."));
    }

    private static CommandRuntimeMigrationAudit.PlaceholderOwnerFailure ownerFailure(String typeName,
            String relativePath,
            int... hitsPerLine) {
        List<CommandRuntimeMigrationAudit.SourceMatch> matches = new ArrayList<>();
        for (int i = 0; i < hitsPerLine.length; i++) {
            matches.add(new CommandRuntimeMigrationAudit.SourceMatch(
                    100 + i,
                    "Locutus.imp().call" + i + "()",
                    hitsPerLine[i]));
        }
        return new CommandRuntimeMigrationAudit.PlaceholderOwnerFailure(typeName, relativePath, matches);
    }

    private static void assertHotspotOrder(String detail, String first, String second, String third) {
        int firstIndex = detail.indexOf(first);
        int secondIndex = detail.indexOf(second);
        int thirdIndex = detail.indexOf(third);

        assertTrue(firstIndex >= 0, () -> "expected detail to mention " + first + " but got: " + detail);
        assertTrue(secondIndex > firstIndex, () -> "expected " + second + " after " + first + " but got: " + detail);
        assertTrue(thirdIndex > secondIndex, () -> "expected " + third + " after " + second + " but got: " + detail);
    }
}
