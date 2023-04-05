package link.locutus.discord.pnw;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;

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
}
