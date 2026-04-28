package link.locutus.discord.web.commands.binding.value_types;

/**
 * Identifying labels for a nation that participates in an existing war but is
 * not part of the planner's attacker/defender population. The frontend uses
 * this to render outsider opponents in war-fact rows without a second lookup.
 */
public record BlitzOutsiderNation(
        int nationId,
        String nationName,
        int allianceId,
        String allianceName
) {
}
