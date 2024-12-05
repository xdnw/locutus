package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.db.entities.DBAlliance;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Map;

public record CoalitionGraph(
        String name,
        Map<DBAlliance, Integer> alliances,
        @Nullable WebGraph overall,
        Map<Integer, WebGraph> by_alliance
) {

}