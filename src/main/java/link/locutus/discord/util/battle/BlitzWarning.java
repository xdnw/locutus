package link.locutus.discord.util.battle;

public record BlitzWarning(
        BlitzWarningCode code,
        int attackerNationId,
        int defenderNationId,
        int warId,
        String detail
) {
    public int codeOrdinal() {
        return code.ordinal();
    }
}
