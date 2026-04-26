package link.locutus.discord.util.battle;

import com.google.common.base.Predicates;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.math.ArrayUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class BlitzValidator {
    public static final int ATTACKER_INACTIVE_MINUTES = 4880;
    public static final int DEFENDER_INACTIVE_MINUTES = (int) TimeUnit.DAYS.toMinutes(8);

    private BlitzValidator() {
    }

    public record Rules(
            double minScoreMultiplier,
            double maxScoreMultiplier,
            boolean checkUpdeclare,
            boolean checkWarSlots,
            boolean checkSpySlots
    ) {
    }

    public static List<BlitzWarning> validatePair(
            BlitzDraftNation attacker,
            BlitzDraftNation defender,
            Rules rules
    ) {
        List<BlitzWarning> warnings = new ArrayList<>(1);
        double minScore = attacker.score() * rules.minScoreMultiplier();
        double maxScore = attacker.score() * rules.maxScoreMultiplier();

        if (defender.score() < minScore) {
            double diff = ArrayUtil.toCents(minScore - defender.score()) / 100d;
            warnings.add(warning(BlitzWarningCode.BELOW_WAR_RANGE, attacker, defender,
                    "`" + defender.nationName() + "` is " + MathMan.format(diff) + "ns below " + "`" + attacker.nationName() + "`"));
        } else if (defender.score() > maxScore) {
            double diff = ArrayUtil.toCents(defender.score() - maxScore) / 100d;
            warnings.add(warning(BlitzWarningCode.ABOVE_WAR_RANGE, attacker, defender,
                    "`" + defender.nationName() + "` is " + MathMan.format(diff) + "ns above " + "`" + attacker.nationName() + "`"));
        } else if (rules.checkUpdeclare() && airStrength(defender) > airStrength(attacker) * 1.33) {
            double ratio = airStrength(defender) / airStrength(attacker);
            warnings.add(warning(BlitzWarningCode.UPDECLARE_TOO_STRONG, attacker, defender,
                    "`" + defender.nationName() + "` is " + MathMan.format(ratio) + "x stronger than " + "`" + attacker.nationName() + "`"));
        } else if (rules.checkWarSlots() && defender.defensiveWars() == 3) {
            warnings.add(warning(BlitzWarningCode.DEFENDER_SLOTTED, attacker, defender,
                    "`" + defender.nationName() + "` is slotted"));
        } else if (rules.checkSpySlots() && !defender.espionageAvailable()) {
            warnings.add(warning(BlitzWarningCode.DEFENDER_SPY_SLOTTED, attacker, defender,
                    "`" + defender.nationName() + "` is spy slotted"));
        }
        return warnings;
    }

    public static List<BlitzWarning> validateAttacker(BlitzDraftNation attacker) {
        if (attacker.activeMinutes() > ATTACKER_INACTIVE_MINUTES) {
            return List.of(warning(BlitzWarningCode.ATTACKER_INACTIVE, attacker, null,
                    "Attacker: `" + attacker.nationName() + "` is inactive"));
        }
        if (attacker.vmTurns() > 1) {
            return List.of(warning(BlitzWarningCode.ATTACKER_VM, attacker, null,
                    "Attacker: `" + attacker.nationName() + "` is in VM for " + attacker.vmTurns() + " turns"));
        }
        return List.of();
    }

    public static List<BlitzWarning> validateDefender(
            BlitzDraftNation defender,
            Function<BlitzDraftNation, Boolean> isValidTarget
    ) {
        if (!isValidTarget.apply(defender)) {
            return List.of(warning(BlitzWarningCode.TARGET_NOT_ENEMY, null, defender,
                    "Defender: `" + defender.nationName() + "` is not an enemy"));
        }
        if (defender.activeMinutes() > DEFENDER_INACTIVE_MINUTES) {
            return List.of(warning(BlitzWarningCode.DEFENDER_INACTIVE, null, defender,
                    "Defender: `" + defender.nationName() + "` is inactive"));
        }
        if (defender.vmTurns() > 1) {
            return List.of(warning(BlitzWarningCode.DEFENDER_VM, null, defender,
                    "Defender: `" + defender.nationName() + "` is in VM for " + defender.vmTurns() + " turns"));
        }
        if (defender.beige()) {
            return List.of(warning(BlitzWarningCode.BEIGE_DEFENDER, null, defender,
                    "Defender: `" + defender.nationName() + "` is beige"));
        }
        return List.of();
    }

    public static BlitzWarning maxOffensivesWarning(BlitzDraftNation attacker, int targets) {
        return warning(BlitzWarningCode.ATTACKER_AT_MAX_OFFENSIVES, attacker, null,
                "`" + attacker.nationName() + "` has " + targets + " targets");
    }

    public static BlitzWarning unknownNationWarning(String cellValue) {
        return new BlitzWarning(BlitzWarningCode.UNKNOWN_NATION, 0, 0, 0,
                "`" + cellValue + "` is an invalid nation");
    }

    public static BlitzWarning allianceMismatchWarning(BlitzDraftNation nation, String nationValue, String allianceValue) {
        return new BlitzWarning(BlitzWarningCode.ALLIANCE_MISMATCH, 0, nation.nationId(), 0,
                "Nation: `" + nationValue + "` is no longer in alliance: `" + allianceValue + "`");
    }

    public static BlitzWarning duplicateSheetColumnWarning(String columnName, List<String> cells) {
        return new BlitzWarning(BlitzWarningCode.DUPLICATE_SHEET_COLUMN, 0, 0, 0,
                "Duplicate columns found for: " + columnName + " at " + String.join(", ", cells));
    }

    private static BlitzWarning warning(
            BlitzWarningCode code,
            BlitzDraftNation attacker,
            BlitzDraftNation defender,
            String detail
    ) {
        return new BlitzWarning(
                code,
                attacker == null ? 0 : attacker.nationId(),
                defender == null ? 0 : defender.nationId(),
                0,
                detail
        );
    }

    private static double airStrength(BlitzDraftNation nation) {
        int max = Buildings.HANGAR.cap(Predicates.alwaysFalse()) * Buildings.HANGAR.getUnitCap() * nation.cities();
        return nation.aircraft() + max / 2d + nation.tanks() / 32d;
    }
}
