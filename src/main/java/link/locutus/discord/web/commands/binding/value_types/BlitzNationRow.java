package link.locutus.discord.web.commands.binding.value_types;

public record BlitzNationRow(
        int nationId,
        int allianceId,
        int cities,
        int[] unitsByMilitaryUnitOrdinal,
        int[] unitCapsByMilitaryUnitOrdinal,
        int[] unitsBoughtTodayByMilitaryUnitOrdinal,
        int avgInfraCents,
        int beigeTurns,
        int vmTurns,
        int inactiveMinutes,
        int activityBp,
        int freeOffensiveSlots,
        int freeDefensiveSlots,
        int maxOffensiveSlots,
        int policyOrdinal,
        long projectBits,
        int researchBits,
        int activeOrdinal,
        int resetHourUtc,
        boolean resetHourUtcFallback
) {
}
