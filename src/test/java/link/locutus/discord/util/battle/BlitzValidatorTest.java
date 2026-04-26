package link.locutus.discord.util.battle;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlitzValidatorTest {
    private static final BlitzValidator.Rules FULL_PAIR_RULES = new BlitzValidator.Rules(0.75, 1.75, true, true, true);

    @Test
    void pairValidationKeepsLegacyBelowRangeDetail() {
        TestNation attacker = nation(1, "Att", 100d, 5, 1_000, 100);
        TestNation defender = nation(2, "Def", 70d, 5, 1_000, 100);

        List<BlitzWarning> warnings = BlitzValidator.validatePair(attacker, defender, FULL_PAIR_RULES);

        assertEquals(1, warnings.size());
        assertEquals(BlitzWarningCode.BELOW_WAR_RANGE, warnings.get(0).code());
        assertEquals(BlitzWarningCode.BELOW_WAR_RANGE.ordinal(), warnings.get(0).codeOrdinal());
        assertEquals("`Def` is 5ns below `Att`", warnings.get(0).detail());
    }

    @Test
    void pairValidationKeepsLegacySlotPrecedenceAfterRangeChecks() {
        TestNation attacker = nation(1, "Att", 100d, 5, 1_000, 100);
        TestNation defender = nation(2, "Def", 100d, 5, 1_000, 100).withDefensiveWars(3);

        List<BlitzWarning> warnings = BlitzValidator.validatePair(attacker, defender, FULL_PAIR_RULES);

        assertEquals(1, warnings.size());
        assertEquals(BlitzWarningCode.DEFENDER_SLOTTED, warnings.get(0).code());
        assertEquals("`Def` is slotted", warnings.get(0).detail());
    }

    @Test
    void attackerAndDefenderWarningsKeepSheetDetails() {
        TestNation inactiveAttacker = nation(1, "Slow", 100d, 5, 1_000, 100).withActiveMinutes(4_881);
        TestNation vmDefender = nation(2, "Gone", 100d, 5, 1_000, 100).withVmTurns(4);

        assertEquals("Attacker: `Slow` is inactive", BlitzValidator.validateAttacker(inactiveAttacker).get(0).detail());
        BlitzWarning defenderWarning = BlitzValidator.validateDefender(vmDefender, ignored -> true).get(0);

        assertEquals(BlitzWarningCode.DEFENDER_VM, defenderWarning.code());
        assertEquals("Defender: `Gone` is in VM for 4 turns", defenderWarning.detail());
    }

    @Test
    void maxOffensivesWarningIsStructured() {
        BlitzWarning warning = BlitzValidator.maxOffensivesWarning(nation(7, "Busy", 100d, 5, 1_000, 100), 4);

        assertEquals(BlitzWarningCode.ATTACKER_AT_MAX_OFFENSIVES, warning.code());
        assertEquals(7, warning.attackerNationId());
        assertEquals(0, warning.defenderNationId());
        assertEquals("`Busy` has 4 targets", warning.detail());
    }

    @Test
    void targetPredicateFailureUsesSharedCode() {
        BlitzWarning warning = BlitzValidator.validateDefender(nation(2, "Neutral", 100d, 5, 1_000, 100), ignored -> false).get(0);

        assertEquals(BlitzWarningCode.TARGET_NOT_ENEMY, warning.code());
        assertTrue(warning.detail().contains("not an enemy"));
    }

    @Test
    void parserWarningsUseSharedCodesAndLegacyDetails() {
        assertEquals("`bad nation` is an invalid nation",
                BlitzValidator.unknownNationWarning("bad nation").detail());

        BlitzWarning alliance = BlitzValidator.allianceMismatchWarning(nation(2, "Moved", 100d, 5, 1_000, 100), "Moved", "OldAA");
        assertEquals(BlitzWarningCode.ALLIANCE_MISMATCH, alliance.code());
        assertEquals("Nation: `Moved` is no longer in alliance: `OldAA`", alliance.detail());

        BlitzWarning duplicate = BlitzValidator.duplicateSheetColumnWarning("att1", List.of("C1", "D1"));
        assertEquals(BlitzWarningCode.DUPLICATE_SHEET_COLUMN, duplicate.code());
        assertEquals("Duplicate columns found for: att1 at C1, D1", duplicate.detail());
    }

    private static TestNation nation(int id, String name, double score, int cities, int aircraft, int tanks) {
        return new TestNation(id, 10, name, score, cities, tanks, aircraft, 0, 0, 0, true, false);
    }

    private record TestNation(
            int nationId,
            int allianceId,
            String nationName,
            double score,
            int cities,
            int tanks,
            int aircraft,
            int defensiveWars,
            int vmTurns,
            int activeMinutes,
            boolean espionageAvailable,
            boolean beige
    ) implements BlitzDraftNation {
        TestNation withDefensiveWars(int value) {
            return new TestNation(nationId, allianceId, nationName, score, cities, tanks, aircraft, value, vmTurns, activeMinutes, espionageAvailable, beige);
        }

        TestNation withVmTurns(int value) {
            return new TestNation(nationId, allianceId, nationName, score, cities, tanks, aircraft, defensiveWars, value, activeMinutes, espionageAvailable, beige);
        }

        TestNation withActiveMinutes(int value) {
            return new TestNation(nationId, allianceId, nationName, score, cities, tanks, aircraft, defensiveWars, vmTurns, value, espionageAvailable, beige);
        }
    }
}
