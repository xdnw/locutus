package link.locutus.discord.web.commands.binding.value_types;

public record BlitzDraftEdit(
        int nationId,
        Boolean forceActive,
        Integer policyOrdinal,
        Integer avgInfraCents,
        int[] unitCountsByMilitaryUnitOrdinal,
        int[] unitsBoughtTodayByMilitaryUnitOrdinal,
        long projectBitsSet,
        long projectBitsClear,
        int researchBitsSet,
        int researchBitsClear,
        Integer resetHour
) {
}
