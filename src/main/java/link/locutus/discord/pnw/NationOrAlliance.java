package link.locutus.discord.pnw;

public interface NationOrAlliance extends NationOrAllianceOrGuild {
    int getId();

    boolean isAlliance();

    int getAlliance_id();

    String getName();

    default Alliance asAlliance() {
        return (Alliance) this;
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
}
