package link.locutus.discord.pnw;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;

import java.util.Map;

public interface NationOrAlliance extends NationOrAllianceOrGuild {
    int getId();

    int getAlliance_id();

    String getMarkdownUrl();

    Map<ResourceType, Double> getStockpile();

    @Command(desc = "If this exists in game")
    boolean isValid();
}
