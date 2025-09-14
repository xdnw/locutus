package link.locutus.discord.db.entities;

import link.locutus.discord.db.guild.GuildKey;

public enum ClearRolesEnum {
    UNUSED("Alliance name roles which have no members"),
    ALLIANCE("All alliance name roles"),
    DELETED_ALLIANCES("Alliance name roles with no valid in-game alliance"),
    INACTIVE_ALLIANCES("Alliance name roles with no active members"),
    NOT_ALLOW_LISTED("Alliance name roles not in the allow list (defined by settings:`" + GuildKey.AUTOROLE_ALLIANCES.name() + "," + GuildKey.AUTOROLE_TOP_X.name() + "` and coalition:`" + Coalition.MASKEDALLIANCES.name() + "`"),

    NON_MEMBERS("Users who are not in the alliance in-game"),
    NON_ALLIES("Users who are not in the alliance, or the `allies` / `offshore` coalition in-game");

    private final String desc;

    ClearRolesEnum(String s) {
        this.desc = s;
    }

    @Override
    public String toString() {
        return name() + ": `" + desc + "`";
    }
}
