package link.locutus.discord.commands.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;

import java.util.Collection;

public enum WarCatReason {
    WARS_NOT_AGAINST_ACTIVE("Nation has no wars against active nations", false),
    NO_WARS("Nation has no wars", false),
    NO_WARS_CHANNEL("Channel for nation has no wars and not registered to a room", false),
    NATION_NOT_FOUND("Nation not found in database (did they delete?)", false),

    VACATION_MODE("Nation is in vacation mode", false),
    INACTIVE_APPLICANT("Nation is applicant not active in past 1d, with 0 offensive wars", false),
    INACTIVE("Nation is inactive for 2 days", false),
    INACTIVE_NONE("Nation is not in an alliance and not active in past 1d, with 0 offensive wars", false),
    FILTER("Nation does not match the `WAR_ROOM_FILTER` set", false),
    ACTIVE("Nation is active with wars", true),
    ACTIVE_BUT_APPLICANT("Active war, but with an applicant", false),
    PLANNING_NO_ACTIVE_WARS("War room is marked as planning and has no active wars", true),
    ROOM_ACTIVE_NO_CHANNEL("War room is registered and active but has no channel", true),
    ROOM_ACTIVE_INVALID_CHANNEL("War room is registered and active but has an invalid channel", true),
    ROOM_ACTIVE_EXISTS("War room is registered and active and has a valid channel", true),
    NOT_CREATED("War room is not created yet, but active wars were found", true),
    ROOM_ACTIVE_NO_FREE_CATEGORY("War room is active, but no free category is found", true),

    // Not used for syncing
    EXISTING("War room was loaded from the channel name", true),
    NATION_UPDATE("Nation with existing war room received an update", true),
    CACHE("War room and channel id was loaded from cache", true),
    CHANNEL_MOVE("War room was moved to a new channel", true),
    CHANNEL_CREATE("A new text channel was created in the war room category", true),
    CHANNEL_DELETE("The text channel associated with the war room was deleted", false),

    WAR_ALERT_DNR("war alert dnr", false),
    WARCAT_COMMAND("/war room category", false),
    WARPIN_COMMAND("/war room pin", false),
    WAR_PAGE("<web>/counters", false),
    COMMAND_ARGUMENT("Command argument", false),
    SYNC_COMMAND("/war room sync", false),
    COUNTER_SHEET("Counter sheet", false),
    WARCAT_SHEET("WarCat sheet", false),
    WARROOM_COMMAND("/war room create", false),
    MESSAGE_SYNC("Message sync", false),
    PURGE_COMMAND("/war room purge", false),

    ;

    public boolean isExisting() {
        return this == EXISTING || this == CACHE;
    }

    private final boolean isActive;
    private final String reason;

    WarCatReason(String reason, boolean isActive) {
        this.reason = reason;
        this.isActive = isActive;

    }

    public static WarCatReason getActiveReason(NationFilter filter, DBNation nation) {
        if (nation == null) {
            return WarCatReason.NATION_NOT_FOUND;
        }
        if (nation.getVm_turns() > 0) {
            return WarCatReason.VACATION_MODE;
        }
        int activeM = nation.active_m();
        if (activeM >= 2880) {
            return WarCatReason.INACTIVE;
        }
        if (activeM > 1440 && nation.getOff() == 0) {
            if (nation.getPositionEnum() == Rank.APPLICANT) {
                return WarCatReason.INACTIVE_APPLICANT;
            }
            if (nation.getPositionEnum().id <= Rank.APPLICANT.id) {
                return WarCatReason.INACTIVE_NONE;
            }
        }
        if (filter != null && !filter.test(nation)) {
            return WarCatReason.FILTER;
        }
        return WarCatReason.ACTIVE;
    }

    public static WarCatReason getActiveReason(NationFilter filter, Collection<DBWar> wars, DBNation nation) {
        WarCatReason reason = getActiveReason(filter, nation);
        if (!reason.isActive()) return reason;
        for (DBWar war : wars) {
            int attackerId = war.getAttacker_id() == nation.getNation_id() ? war.getDefender_id() : war.getAttacker_id();
            DBNation attacker = Locutus.imp().getNationDB().getNationById(attackerId);
            if (attacker != null) {
                reason = getActiveReason(filter, attacker);
                if (reason.isActive()) return reason;
            }
        }
        if (wars.isEmpty()) {
            // no wars
            return WarCatReason.NO_WARS;
        }
        if (wars.size() == 1) {
            return reason;
        }
        return WarCatReason.WARS_NOT_AGAINST_ACTIVE;
    }

    public String getReason() {
        return reason;
    }

    public boolean isActive() {
        return isActive;
    }
}
