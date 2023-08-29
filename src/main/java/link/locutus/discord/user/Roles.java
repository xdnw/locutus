package link.locutus.discord.user;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.util.StringMan;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public enum Roles {
    REGISTERED(0, "auto role for anyone who is verified with the bot"),
    MEMBER(1, "Members can run commands"),
    ADMIN(2, "Admin has access to alliance / guild management commands"),

    MILCOM(3, "Access to milcom related commands", GuildKey.ALLIANCE_ID) {
        @Override
        public boolean has(Member member) {
            if (super.has(member)) return true;
            return MILCOM_NO_PINGS.has(member);
        }
    },
    MILCOM_NO_PINGS(4, "Access to milcom related commands- doesn't receive pings", GuildKey.ALLIANCE_ID, "MILCOM_ADVISOR"),

    ECON(5, "Has access to econ gov commands", null),
    ECON_STAFF(6, "Has access to economy information commands", GuildKey.ALLIANCE_ID, "ECON_LOW_GOV") {
        @Override
        public boolean has(Member member) {
            if (super.has(member)) return true;
            return ECON.has(member);
        }

        @Override
        public Role toRole(Guild guild) {
            Role value = super.toRole(guild);
            if (value != null) return value;
            return ECON.toRole(guild);
        }
    },

//    ECON_GRANT_ALERTS(7, "Gets pinged for member grant requests", GuildKey.ALLIANCE_ID),
    ECON_DEPOSIT_ALERTS(8, "Gets pinged when there is a deposit", GuildKey.DEPOSIT_ALERT_CHANNEL),
    ECON_WITHDRAW_ALERTS(9, "Gets pinged when there is a withdrawal", GuildKey.WITHDRAW_ALERT_CHANNEL, "ECON_GRANT_ALERTS"),
    ECON_WITHDRAW_SELF(10, "Can withdraw own funds", GuildKey.MEMBER_CAN_WITHDRAW),
    ECON_GRANT_SELF(11, "Role to allow member to grant themselves", GuildKey.MEMBER_CAN_WITHDRAW),

    FOREIGN_AFFAIRS(12, "Role required to see other alliance's embassy channel", GuildKey.ALLIANCE_ID),
    FOREIGN_AFFAIRS_STAFF(13, "Role for some basic FA commands", GuildKey.ALLIANCE_ID) {
        @Override
        public boolean has(Member member) {
            if (super.has(member)) return true;
            return FOREIGN_AFFAIRS.has(member);
        }

        @Override
        public Role toRole(Guild guild) {
            Role value = super.toRole(guild);
            if (value != null) return value;
            return FOREIGN_AFFAIRS.toRole(guild);
        }
    },

    INTERNAL_AFFAIRS(14, "Access to IA related commands", GuildKey.ALLIANCE_ID),
    INTERNAL_AFFAIRS_STAFF(15, "Role for some basic IA commands", GuildKey.ALLIANCE_ID) {
        @Override
        public boolean has(Member member) {
            if (super.has(member)) return true;
            return INTERNAL_AFFAIRS.has(member);
        }

        @Override
        public Role toRole(Guild guild) {
            Role value = super.toRole(guild);
            if (value != null) return value;
            return INTERNAL_AFFAIRS.toRole(guild);
        }
    },

    APPLICANT(16, "Applying to join the alliance (this role doesn't grant any elevated permissions)", GuildKey.INTERVIEW_PENDING_ALERTS),
    INTERVIEWER(17, "Role to get pinged when a user requests an interview", GuildKey.INTERVIEW_PENDING_ALERTS),
    MENTOR(18, "Role to get pinged when a user requests mentoring (can be same as interviewer)", GuildKey.INTERVIEW_PENDING_ALERTS),
    GRADUATED(19, "Members with this role will have their interview channels archived", GuildKey.INTERVIEW_PENDING_ALERTS) {
        @Override
        public boolean has(Member member) {
            return super.has(member)
                    || Roles.MILCOM.has(member)
                    || Roles.MILCOM_NO_PINGS.has(member)
                    || Roles.ECON.has(member)
                    || Roles.ECON_STAFF.has(member)
                    || Roles.FOREIGN_AFFAIRS.has(member)
                    || Roles.FOREIGN_AFFAIRS_STAFF.has(member)
                    || Roles.INTERNAL_AFFAIRS.has(member)
                    || Roles.INTERNAL_AFFAIRS_STAFF.has(member)
                    || Roles.INTERVIEWER.has(member)
                    || Roles.MENTOR.has(member)
                    || Roles.RECRUITER.has(member)
                    ;
        }
    },
    RECRUITER(20, "Role to get pinged for recruitment messages (if enabled)", GuildKey.RECRUIT_MESSAGE_OUTPUT),

    TRADE_ALERT(21, "Gets pinged for trade alerts", GuildKey.TRADE_ALERT_CHANNEL),

    BEIGE_ALERT(22, "Gets pinged when a nation leaves beige (in their score range), and they have a slot free", GuildKey.BEIGE_ALERT_CHANNEL),
    BEIGE_ALERT_OPT_OUT(23, "Overrides the beige alert role", GuildKey.BEIGE_ALERT_CHANNEL),

    BOUNTY_ALERT(24, "Gets pings when bounties are placed in their score range"),
//    MAP_FULL_ALERT("Gets pinged when you are on 12 MAPs in an offensive war", GuildKey.MEMBER_AUDIT_ALERTS),

//    WAR_ALERT("Opt out of received war target alerts", GuildKey.ENEMY_ALERT_CHANNEL),
    WAR_ALERT_OPT_OUT(25, "Opt out of received war target alerts", GuildKey.ENEMY_ALERT_CHANNEL),
    AUDIT_ALERT_OPT_OUT(26, "Opt out of received audit alerts", GuildKey.MEMBER_AUDIT_ALERTS),
    BLITZ_PARTICIPANT(27, "Opt in to blitz participation (clear this regularly)", GuildKey.ALLIANCE_ID),
    BLITZ_PARTICIPANT_OPT_OUT(28, "Opt in to blitz participation (clear this regularly)", GuildKey.ALLIANCE_ID),

    TEMP(29, "Role to signify temporary member, not elligable for grants", GuildKey.ALLIANCE_ID),

    MAIL(30, "Can use mail commands", GuildKey.API_KEY),

    BLOCKADED_ALERT(31, "Gets a ping when you are blockaded", GuildKey.BLOCKADED_ALERTS, "BLOCKADED_ALERTS"),
    UNBLOCKADED_ALERT(32, "Gets a ping when you are unblockaded", GuildKey.UNBLOCKADED_ALERTS, "UNBLOCKADED_ALERTS"),

    UNBLOCKADED_GOV_ROLE_ALERT(33, "Pings this role when any member is fully unblockaded", GuildKey.UNBLOCKADED_ALERTS, "UNBLOCKADED_GOV_ROLE_ALERTS"),
    ESCROW_GOV_ALERT(33, "Pings this role when any member is fully unblockaded and has an escrow balance", GuildKey.UNBLOCKADED_ALERTS, "UNBLOCKADED_GOV_ROLE_ALERTS"),

    TREASURE_ALERT(34, "Gets alerts in the TREASURE_ALERT_CHANNEL if a treasure is spawning in their range", GuildKey.TREASURE_ALERT_CHANNEL, "TREASURE_ALERTS"),
    TREASURE_ALERT_OPT_OUT(35, "Does not receive treasure alerts (even with the treasure alert role)", GuildKey.TREASURE_ALERT_CHANNEL, "TREASURE_ALERTS_OPT_OUT"),

    ENEMY_BEIGE_ALERT_AUDITOR(36, "Role to receive pings when an enemy gets beiged", GuildKey.ENEMY_BEIGED_ALERT_VIOLATIONS),

    GROUND_MILITARIZE_ALERT(37, "Role to receive pings when alliances militarize", GuildKey.AA_GROUND_UNIT_ALERTS, "GROUND_MILITARIZE_ROLE"),

    AI_COMMAND_ACCESS(37, "Access to AI commands on the discord server"),


    ;


    public static Roles[] values = values();
    private final String desc;
    private final GuildSetting key;

    private final int id;
    private final String legacy_name;

    public static Roles getHighestRole(Member member) {
        for (int i = values.length - 1; i >= 0; i--) {
            if (values[i].has(member)) {
                return values[i];
            }
        }
        return null;
    }

    public int getId() {
        return id;
    }

    private static final Map<Integer, Roles> ROLES_ID_MAP = new ConcurrentHashMap<>();

    public static String getValidRolesStringList() {
        return "\n- " + StringMan.join(Roles.values(), "\n- ");
    }
    Roles(int id, String desc) {
        this(id, desc, null);
    }
    Roles(int id, String desc, GuildSetting key) {
        this(id, desc, key, null);
    }
    Roles(int id, String desc, GuildSetting key, String legacy_name) {
        this.desc = desc;
        this.key = key;
        this.id = id;
        this.legacy_name = legacy_name;
    }


    public String getLegacyName() {
        return legacy_name;
    }

    public static Roles getRoleByNameLegacy(String name) {
        for (Roles role : values) {
            if (role.name().equalsIgnoreCase(name) || (role.getLegacyName() != null && role.getLegacyName().equalsIgnoreCase(name))) {
                return role;
            }
        }
        return null;
    }

    public static Roles getRoleById(int id) {
        Roles result = ROLES_ID_MAP.get(id);
        if (ROLES_ID_MAP.isEmpty()) {
            synchronized (ROLES_ID_MAP) {
                for (Roles role : values) {
                    ROLES_ID_MAP.put(role.getId(), role);
                }
            }
        }
        if (result == null) {
            synchronized (ROLES_ID_MAP) {
                result = ROLES_ID_MAP.get(id);
            }
        }
        return result;
    }

    public String toDiscordRoleNameElseInstructions(Guild guild) {
        Role role = toRole(guild);
        if (role != null) {
            return role.getName();
        }
        return "No " + name() + " role set. Use " + CM.role.setAlias.cmd.create(name(), null, null, null);
    }

    public GuildSetting getKey() {
        return key;
    }

    public Set<Long> getAllowedAccounts(User user, Guild guild) {
        GuildDB db = Locutus.imp().getGuildDB(guild);
        if (db == null) return Collections.emptySet();
        return getAllowedAccounts(user, db);
    }

    public AllianceList getAllianceList(User user, GuildDB db) {
        AllianceList list = db.getAllianceList();
        if (list == null || list.isEmpty()) return new AllianceList();
        return list.subList(getAllowedAccounts(user, db).stream()
                .map(Long::intValue)
                .collect(Collectors.toSet()));

    }

    public Set<Long> getAllowedAccounts(User user, GuildDB db) {
        Set<Integer> aaIds = db.getAllianceIds();
        Set<Long> allowed = new HashSet<>();
        if (aaIds.isEmpty()) {
            if (has(user, db.getGuild())) {
                allowed.add(db.getIdLong());
            }
            return allowed;
        }
        for (Integer aaId : aaIds) {
            if (has(user, db.getGuild(), aaId)) {
                allowed.add(aaId.longValue());
            }
        }
        return allowed;
    }

    public String getDesc() {
        return desc;
    }

    @Override
    public String toString() {
        return name() + ": `" + desc + "`";
    }

    public Role toRole(MessageReceivedEvent event) {
        return toRole(event.isFromGuild() ? event.getGuild() : Locutus.imp().getServer());
    }

    @Deprecated
    public Role toRole(Guild guild) {
        return toRole(Locutus.imp().getGuildDB(guild));
    }
    public Role toRole(int alliance) {
        GuildDB db = Locutus.imp().getGuildDBByAA(alliance);
        if (db == null) return null;
        return db.getRole(this, (long) alliance);
    }

    @Deprecated
    public Role toRole(GuildDB db) {
        if (db == null) return null;
        return db.getRole(this, null);
    }

    public boolean hasOnRoot(User user) {
        if (user.getIdLong() == Settings.INSTANCE.ADMIN_USER_ID) return true;
        if (Locutus.imp().getServer() == null) {
            return false;
        }
        return has(user, Locutus.imp().getServer());
    }

    public boolean has(User user, GuildDB server, int alliance) {
        return has(user, server.getGuild(), alliance);
    }

    public boolean has(User user, Guild server, int alliance) {
        Member member = server.getMember(user);
        return member != null && has(member, alliance);
    }

    public boolean has(Member member, int alliance) {
        if (has(member)) return true;
        if (alliance == 0) return false;
        Role role = Locutus.imp().getGuildDB(member.getGuild()).getRole(this, (long) alliance);
        return role != null && member.getRoles().contains(role);
    }

    public boolean has(Member member) {
        if (member == null) return false;
        if (member.getIdLong() == Settings.INSTANCE.APPLICATION_ID) return true;
        if (member.getIdLong() == Settings.INSTANCE.ADMIN_USER_ID) return true;

        if (member.isOwner()) return true;
        Role role = toRole(member.getGuild());
        for (Role discordRole : member.getRoles()) {
            if (discordRole.hasPermission(Permission.ADMINISTRATOR)) {
                return true;
            }
        }
        return role != null && member.getRoles().contains(role);
    }

    public static boolean hasAny(User user, Guild guild, Roles... roles) {
        for (Roles role : roles) {
            if (role.has(user, guild)) return true;
        }
        return false;
    }

    public static Roles parse(String role) {
        try {
            return Roles.valueOf(role.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException  e) {
            return null;
        }
    }

    public boolean has(User user, Guild server) {
        if (user == null) return false;
        if (user.getIdLong() == Settings.INSTANCE.APPLICATION_ID) return true;
        if (user.getIdLong() == Settings.INSTANCE.ADMIN_USER_ID) return true;
        if (server == null) return false;
        if (!server.isMember(user)) {
            return false;
        }
        return has(server.getMember(user));

    }

    public Map<Long, Role> toRoleMap(GuildDB senderDB) {
        return senderDB.getRoleMap(this);
    }
}
