package link.locutus.discord.pnw;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MarkupUtil;

import java.util.Map;

public interface NationOrAlliance extends NationOrAllianceOrGuild {
    int getId();

    boolean isAlliance();

    int getAlliance_id();

    String getName();

    default DBAlliance asAlliance() {
        return (DBAlliance) this;
    }

    default boolean isNation() {
        return !isAlliance() && !isGuild();
    }

    default DBNation asNation() {
        return (DBNation) this;
    }

    default String getUrl() {
        if (isAlliance()) return asAlliance().getUrl();
        return asNation().getNationUrl();
    }

    String getMarkdownUrl();

    Map<ResourceType, Double> getStockpile();

    @Command(desc = "Get the sheet url for this nation/alliance")
    default String getSheetUrl() {
        return MarkupUtil.sheetUrl(getName(), getUrl());
    }

    @Command(desc = "If this exists in game")
    boolean isValid();
}
