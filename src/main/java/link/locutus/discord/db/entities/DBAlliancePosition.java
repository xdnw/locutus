package link.locutus.discord.db.entities;

import com.politicsandwar.graphql.model.AlliancePosition;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.position.PositionChangeLevelEvent;
import link.locutus.discord.event.position.PositionChangeNameEvent;
import link.locutus.discord.event.position.PositionChangePermissionEvent;
import link.locutus.discord.event.position.PositionChangeRankEvent;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import static link.locutus.discord.apiv3.enums.AlliancePermission.*;

public class DBAlliancePosition {
    public static final DBAlliancePosition APPLICANT = new DBAlliancePosition(1,
            0,
            "applicant",
            0,
            0,
            Rank.APPLICANT,
            0
    );
    public static final DBAlliancePosition REMOVE = new DBAlliancePosition(0,
            0,
            "remove",
            0,
            0,
            Rank.APPLICANT,
            0
    );

    private final int id;
    private final int alliance_id;
    private String name;
    private long date_created;
    private int position_level;
    private Rank rank;
    private long permission_bits;

    public DBAlliancePosition(int alliance_id, AlliancePosition v3Position) {
        this.id = v3Position.getId();
        this.alliance_id = alliance_id;

        set(v3Position, null);
    }

    public static DBAlliancePosition parse(String input, int aaId, boolean allowApplicantAndRemove) {
        DBAlliance alliance = DBAlliance.get(aaId);
        if (alliance == null) throw new IllegalStateException("No alliance found with id: " + aaId);
        return null;
    }

    public boolean set(AlliancePosition v3Position, Consumer<Event> eventConsumer) {
        boolean dirty = false;

        Rank rank = Rank.APPLICANT;
        if (v3Position.getLeader() == Boolean.TRUE) rank = Rank.LEADER;
        if (v3Position.getHeir() == Boolean.TRUE) rank = Rank.HEIR;
        if (v3Position.getOfficer() == Boolean.TRUE) rank = Rank.OFFICER;
        if (v3Position.getMember() == Boolean.TRUE) rank = Rank.MEMBER;

        DBAlliancePosition copy = null;

        if (this.date_created != v3Position.getDate().toEpochMilli()) {
            this.date_created = v3Position.getDate().toEpochMilli(); // Should never change
            dirty = true;
        }

        if (v3Position.getName() != null && !v3Position.getName().equals(name)) {
            dirty = true;
            if (copy == null && eventConsumer != null) copy = new DBAlliancePosition(this);
            this.name = v3Position.getName();
            if (eventConsumer != null) eventConsumer.accept(new PositionChangeNameEvent(copy, this));
        }
        if (v3Position.getPosition_level() != null && v3Position.getPosition_level() != this.position_level) {
            dirty = true;
            if (copy == null && eventConsumer != null) copy = new DBAlliancePosition(this);
            this.position_level = v3Position.getPosition_level();
            if (eventConsumer != null) eventConsumer.accept(new PositionChangeLevelEvent(copy, this));
        }
        if (v3Position.getPosition_level() != null && v3Position.getPosition_level() != this.position_level) {
            dirty = true;
            if (copy == null && eventConsumer != null) copy = new DBAlliancePosition(this);
            this.position_level = v3Position.getPosition_level();
            if (eventConsumer != null) eventConsumer.accept(new PositionChangeLevelEvent(copy, this));
        }
        if ((v3Position.getLeader() != null || rank != Rank.APPLICANT) && this.rank != rank) {
            dirty = true;
            if (copy == null && eventConsumer != null) copy = new DBAlliancePosition(this);
            this.rank = rank;
            if (eventConsumer != null) eventConsumer.accept(new PositionChangeRankEvent(copy, this));
        }
        if (v3Position.getPermissions() != null && v3Position.getPermissions() != this.permission_bits) {
            dirty = true;
            if (copy == null && eventConsumer != null) copy = new DBAlliancePosition(this);
            this.permission_bits = v3Position.getPermissions();
            if (eventConsumer != null) eventConsumer.accept(new PositionChangePermissionEvent(copy, this));
        }
        return dirty;
    }

    public DBAlliancePosition(int id, int alliance_id, String name, long date_created, int position_level, Rank rank, long permission_bits) {
        this.id = id;
        this.alliance_id = alliance_id;
        this.name = name;
        this.date_created = date_created;
        this.position_level = position_level;
        this.rank = rank;
        this.permission_bits = permission_bits;
    }

    public DBAlliancePosition(DBAlliancePosition other) {
        this(other.id, other.alliance_id, other.name, other.date_created, other.position_level, other.rank, other.permission_bits);
    }

    public boolean hasPermission(AlliancePermission permission) {
        return permission.has(permission_bits);
    }

    public boolean hasAnyAdminPermission() {
        return hasAnyPermission(
                CHANGE_PERMISSIONS,
                PROMOTE_SELF_TO_LEADER
        );
    }

    public boolean hasAnyOfficerPermissions() {
        return hasAnyPermission(
                CHANGE_PERMISSIONS,
                SEE_SPIES,
                WITHDRAW_BANK,
                SEE_RESET_TIMERS,
                TAX_BRACKETS,
                POST_ANNOUNCEMENTS,
                MANAGE_ANNOUNCEMENTS,
                ACCEPT_APPLICANTS,
                REMOVE_MEMBERS,
                EDIT_ALLIANCE_INFO,
                MANAGE_TREATIES,
                MANAGE_MARKET_SHARE,
                MANAGE_EMBARGOES,
                PROMOTE_SELF_TO_LEADER
        );
    }

    public boolean hasAnyPermission(AlliancePermission... permissions) {
        for (AlliancePermission perm : permissions) {
            if (hasPermission(perm)) return true;
        }
        return false;
    }

    public boolean hasAllPermission(AlliancePermission... permissions) {
        for (AlliancePermission perm : permissions) {
            if (!hasPermission(perm)) return false;
        }
        return true;
    }

    public int getId() {
        return id;
    }

    public int getAlliance_id() {
        return alliance_id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getDate_created() {
        return date_created;
    }

    public void setDate_created(long date_created) {
        this.date_created = date_created;
    }

    public int getPosition_level() {
        return position_level;
    }

    public void setPosition_level(int position_level) {
        this.position_level = position_level;
    }

    public Rank getRank() {
        return rank;
    }

    public void setRank(Rank rank) {
        this.rank = rank;
    }

    public long getPermission_bits() {
        return permission_bits;
    }

    public void setPermission_bits(long permission_bits) {
        this.permission_bits = permission_bits;
    }

    public GuildDB getGuildDB() {
        return Locutus.imp().getGuildDBByAA(alliance_id);
    }

    public Set<AlliancePermission> getPermissions() {
        Set<AlliancePermission> result = new LinkedHashSet<>();
        for (AlliancePermission perm : AlliancePermission.values()) {
            if (hasPermission(perm)) result.add(perm);
        }
        return result;
    }

    public String getInputName() {
        if (this == APPLICANT) return "applicant";
        if (this == REMOVE) return "remove";
        return this.id + "";
    }
}
