package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.util.battle.BlitzWarning;
import link.locutus.discord.util.battle.BlitzWarningCode;

public record BlitzPlanWarning(
        int codeOrdinal,
        int attackerNationId,
        int defenderNationId,
        int warId
) {
    public BlitzPlanWarning(BlitzWarning warning) {
        this(warning.codeOrdinal(), warning.attackerNationId(), warning.defenderNationId(), warning.warId());
    }

    public BlitzPlanWarning {
        codeAt(codeOrdinal);
    }

    public BlitzWarningCode code() {
        return codeAt(codeOrdinal);
    }

    private static BlitzWarningCode codeAt(int ordinal) {
        BlitzWarningCode[] values = BlitzWarningCode.values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("codeOrdinal is out of range: " + ordinal);
        }
        return values[ordinal];
    }
}