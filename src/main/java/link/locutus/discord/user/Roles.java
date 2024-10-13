package link.locutus.discord.user;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public enum Roles {
    REGISTERED(0, false, "auto role for anyone who is verified with the bot"),
    MEMBER(1, true, "Members can run commands"),
    ADMIN(2, false, "Admin has access to alliance / guild management commands"),

    MILCOM(3, true, "Access to milcom related commands", GuildKey.ALLIANCE_ID) {
        @Override
        public boolean has(Member member) {
            if (super.has(member)) return true;
            return MILCOM_NO_PINGS.has(member);
        }
    },
    MILCOM_NO_PINGS(4, true, "Access to milcom related commands- doesn't receive pings", GuildKey.ALLIANCE_ID, "MILCOM_ADVISOR"),

    ECON(5, true, "Has access to econ gov commands", null),
    ECON_STAFF(6, true, "Has access to economy information commands", GuildKey.ALLIANCE_ID, "ECON_LOW_GOV") {
        @Override
        public boolean has(Member member) {
            if (super.has(member)) return true;
            return ECON.has(member);
        }

        @Override
        public Role toRole2(Guild guild) {
            Role value = super.toRole2(guild);
            if (value != null) return value;
            return ECON.toRole2(guild);
        }
    },

//    ECON_GRANT_ALERTS(7, "Gets pinged for member grant requests", GuildKey.ALLIANCE_ID),
    ECON_DEPOSIT_ALERTS(8, false,"Gets pinged when there is a deposit", GuildKey.DEPOSIT_ALERT_CHANNEL),
    ECON_WITHDRAW_ALERTS(9, false,"Gets pinged when there is a withdrawal", GuildKey.WITHDRAW_ALERT_CHANNEL, "ECON_GRANT_ALERTS"),
    ECON_WITHDRAW_SELF(10, true,"Can withdraw own funds", GuildKey.MEMBER_CAN_WITHDRAW),
    ECON_GRANT_SELF(11, true, "Role to allow member to grant themselves", GuildKey.MEMBER_CAN_WITHDRAW),

    FOREIGN_AFFAIRS(12, true, "Role required to see other alliance's embassy channel", GuildKey.ALLIANCE_ID),
    FOREIGN_AFFAIRS_STAFF(13, true, "Role for some basic FA commands", GuildKey.ALLIANCE_ID) {
        @Override
        public boolean has(Member member) {
            if (super.has(member)) return true;
            return FOREIGN_AFFAIRS.has(member);
        }

        @Override
        public Role toRole2(Guild guild) {
            Role value = super.toRole2(guild);
            if (value != null) return value;
            return FOREIGN_AFFAIRS.toRole2(guild);
        }
    },

    INTERNAL_AFFAIRS(14, true,"Access to IA related commands", GuildKey.ALLIANCE_ID),
    INTERNAL_AFFAIRS_STAFF(15, true, "Role for some basic IA commands, such as accepting applicants", GuildKey.ALLIANCE_ID) {
        @Override
        public boolean has(Member member) {
            if (super.has(member)) return true;
            return INTERNAL_AFFAIRS.has(member);
        }

        @Override
        public Role toRole2(Guild guild) {
            Role value = super.toRole2(guild);
            if (value != null) return value;
            return INTERNAL_AFFAIRS.toRole2(guild);
        }
    },

    APPLICANT(16, true, "Applying to join the alliance in-game", GuildKey.INTERVIEW_PENDING_ALERTS),
    INTERVIEWER(17, false, "Role to get pinged when a user requests an interview to join the alliance", GuildKey.INTERVIEW_PENDING_ALERTS),
    MENTOR(18, false, "Role for mentoring applicants who have completed their interview", GuildKey.INTERVIEW_PENDING_ALERTS),
    GRADUATED(19, true, "Members with this role can have their interview channels archived", GuildKey.INTERVIEW_PENDING_ALERTS) {
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
    RECRUITER(20, false, "Role to get pinged for recruitment messages (if enabled)", GuildKey.RECRUIT_MESSAGE_OUTPUT),

    TRADE_ALERT(21, false, "Gets pinged for trade alerts", GuildKey.TRADE_ALERT_CHANNEL),

    BEIGE_ALERT(22, false, "Gets pinged when a nation leaves beige (in their score range), and they have a slot free", GuildKey.BEIGE_ALERT_CHANNEL),
    BEIGE_ALERT_OPT_OUT(23, false,"Overrides the beige alert role", GuildKey.BEIGE_ALERT_CHANNEL),

    BOUNTY_ALERT(24, false, "Gets pings when bounties are placed in their score range"),
    BOUNTY_ALERT_OPT_OUT(38, false, "Opt out of received bounty alerts", GuildKey.BOUNTY_ALERT_CHANNEL),
//    MAP_FULL_ALERT("Gets pinged when you are on 12 MAPs in an offensive war", GuildKey.MEMBER_AUDIT_ALERTS),

//    WAR_ALERT("Opt out of received war target alerts", GuildKey.ENEMY_ALERT_CHANNEL),
    WAR_ALERT_OPT_OUT(25, false, "Opt out of received war target alerts", GuildKey.ENEMY_ALERT_CHANNEL),
    AUDIT_ALERT_OPT_OUT(26, false, "Opt out of received audit alerts", GuildKey.MEMBER_AUDIT_ALERTS),
    BLITZ_PARTICIPANT(27, false, "Opt in to blitz participation (clear this regularly)", GuildKey.ALLIANCE_ID),
    BLITZ_PARTICIPANT_OPT_OUT(28, false, "Opt in to blitz participation (clear this regularly)", GuildKey.ALLIANCE_ID),

    TEMP(29, false, "Role to signify temporary member, not elligable for grants", GuildKey.ALLIANCE_ID),

    MAIL(30, true, "Can use mail commands", GuildKey.API_KEY),

    BLOCKADED_ALERT(31, false, "Gets a ping when you are blockaded", GuildKey.BLOCKADED_ALERTS, "BLOCKADED_ALERTS"),
    UNBLOCKADED_ALERT(32, false, "Gets a ping when you are unblockaded", GuildKey.UNBLOCKADED_ALERTS, "UNBLOCKADED_ALERTS"),

    UNBLOCKADED_GOV_ROLE_ALERT(33, false, "Pings this role when any member is fully unblockaded", GuildKey.UNBLOCKADED_ALERTS, "UNBLOCKADED_GOV_ROLE_ALERTS"),
    ESCROW_GOV_ALERT(39, false, "Pings this role when any member is fully unblockaded and has an escrow balance", GuildKey.UNBLOCKADED_ALERTS, "UNBLOCKADED_GOV_ROLE_ALERTS"),

    TREASURE_ALERT(34, false, "Gets alerts in the TREASURE_ALERT_CHANNEL if a treasure is spawning in their range", GuildKey.TREASURE_ALERT_CHANNEL, "TREASURE_ALERTS"),
    TREASURE_ALERT_OPT_OUT(35, false, "Does not receive treasure alerts (even with the treasure alert role)", GuildKey.TREASURE_ALERT_CHANNEL, "TREASURE_ALERTS_OPT_OUT"),

    ENEMY_BEIGE_ALERT_AUDITOR(36, false, "Role to receive pings when an enemy gets beiged", GuildKey.ENEMY_BEIGED_ALERT_VIOLATIONS),

    GROUND_MILITARIZE_ALERT(37, false, "Role to receive pings when alliances militarize", GuildKey.AA_GROUND_UNIT_ALERTS, "GROUND_MILITARIZE_ROLE"),

    AI_COMMAND_ACCESS(40, false, "Access to AI commands on the discord server"),

    ESPIONAGE_ALERTS(41, false, "Role to receive pings when an alliance member gets spied", GuildKey.DEFENSE_WAR_CHANNEL),

    ENEMY_ALERT_OFFLINE(42, false, "Able to receive enemy alerts when offline or invisible on discord (unless opt out, or player setting overrides)", GuildKey.BEIGE_ALERT_CHANNEL),


    ;


    public static Roles[] values = values();
    private final String desc;
    private final GuildSetting key;

    private final int id;
    private final String legacy_name;
    private final boolean allowAlliance;

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
    Roles(int id, boolean allowAlliance, String desc) {
        this(id, allowAlliance, desc, null);
    }
    Roles(int id, boolean allowAlliance, String desc, GuildSetting key) {
        this(id, allowAlliance, desc, key, null);
    }
    Roles(int id, boolean allowAlliance, String desc, GuildSetting key, String legacy_name) {
        this.desc = desc;
        this.key = key;
        this.id = id;
        this.legacy_name = legacy_name;
        this.allowAlliance = allowAlliance;
    }

    public boolean allowAlliance() {
        return allowAlliance;
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
                    if (ROLES_ID_MAP.put(role.getId(), role) != null) {
                        throw new IllegalStateException("Duplicate role id: " + role.getId());
                    }
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
        GuildDB db = Locutus.imp().getGuildDB(guild);
        Map<Long, Role> map = db.getRoleMap(this);
        Role defRole = map.get(0L);
        if (defRole != null) {
            return defRole.getName();
        }
        if (map.isEmpty()) {
            return "No " + name() + " role set. Use " + CM.role.setAlias.cmd.locutusRole(name()).discordRole(null);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("No default `" + name() + "` role set, see ").append(CM.role.setAlias.cmd.locutusRole(name()).discordRole(null)).append(" or use an alliance role (nation must be in the alliance)\n");
            for (Map.Entry<Long, Role> entry : map.entrySet()) {
                sb.append("- ").append(entry.getKey()).append(": @").append(entry.getValue().getName()).append("\n");
            }
            return sb.toString();
        }
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

    public Predicate<Integer> isAccountAllowed(User user, Guild guild) {
        return isAccountAllowed(user, Locutus.imp().getGuildDB(guild));
    }

    public Predicate<Integer> isAccountAllowed(User user, GuildDB db) {
        Set<Long> allowedAccounts = getAllowedAccounts(user, db);
        return f -> allowedAccounts.contains((long) f) || allowedAccounts.contains(0L);
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

    public Role toRole2(MessageReceivedEvent event) {
        return toRole2(event.isFromGuild() ? event.getGuild() : Locutus.imp().getServer());
    }

    @Deprecated
    public Role toRole2(Guild guild) {
        return toRole2(Locutus.imp().getGuildDB(guild));
    }

    public Role toRole2(int alliance) {
        GuildDB db = Locutus.imp().getGuildDBByAA(alliance);
        if (db == null) return null;
        return db.getRole(this, (long) alliance);
    }

    @Deprecated
    public Role toRole2(GuildDB db) {
        if (db == null) return null;
        return db.getRole(this, null);
    }

    public boolean hasOnRoot(User user) {
        if (user.getIdLong() == Locutus.loader().getAdminUserId()) return true;
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
        if (member.getIdLong() == Locutus.loader().getAdminUserId()) return true;

        if (member.isOwner()) return true;
        GuildDB db = Locutus.imp().getGuildDB(member.getGuild());
        List<Role> roles = member.getRoles();
        int myAA = -1;
        for (Role discordRole : roles) {
            if (discordRole.hasPermission(Permission.ADMINISTRATOR)) {
                return true;
            }
            Set<Integer> allianceIds = db.getRoleAllianceIds(this, discordRole);
            if (allianceIds.isEmpty()) continue;
            if (allianceIds.contains(0)) return true;
            if (myAA == -1) {
                DBNation nation = DiscordUtil.getNation(member.getIdLong());
                if (nation != null) {
                    myAA = nation.getAlliance_id();
                } else {
                    myAA = 0;
                }
            }
            if (allianceIds.contains(myAA)) return true;
        }
        return false;
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

    public Long hasAlliance(User user, Guild guild) {
        return hasAlliance(guild.getMember(user));
    }

    public Long hasAlliance(Member member) {
        if (member == null) return null;
        if (member.getIdLong() == Settings.INSTANCE.APPLICATION_ID
        || member.getIdLong() == Locutus.loader().getAdminUserId()
        || member.isOwner()) return 0L;
        GuildDB db = Locutus.imp().getGuildDB(member.getGuild());
        List<Role> roles = member.getRoles();
        Map<Long, Role> map = db.getRoleMap(this);
        Role serverRole = map.get(0L);
        if (serverRole != null && roles.contains(serverRole)) return 0L;
        DBNation nation = DiscordUtil.getNation(member.getIdLong());
        if (nation == null) return null;
        Role aaRole = map.get((long) nation.getAlliance_id());
        if (aaRole != null && roles.contains(aaRole)) return (long) nation.getAlliance_id();
        return null;
    }

    public boolean has(User user, Guild server) {
        if (user == null) return false;
        if (user.getIdLong() == Settings.INSTANCE.APPLICATION_ID) return true;
        if (user.getIdLong() == Locutus.loader().getAdminUserId()) return true;
        if (server == null) return false;
        if (!server.isMember(user)) {
            return false;
        }
        return has(server.getMember(user));

    }

    public Map<Long, Role> toRoleMap(GuildDB senderDB) {
        return senderDB.getRoleMap(this);
    }
    public Set<Role> toRoles(GuildDB senderDB) {
        return new HashSet<>(toRoleMap(senderDB).values());
    }


    /**
     * Get all members with this locutus role
     * Must have either the default role, or the alliance specific role whilst being in the alliance in-game
     * @param db
     * @return Set of members
     */
    public Set<Member> getAll(GuildDB db) {
        Set<Member> members = new HashSet<>();
        Map<Long, Role> roleMap = toRoleMap(db);
        if (roleMap.isEmpty()) return members;
        Role defRole = roleMap.get(0L);
        if (defRole != null) {
            for (Member member : defRole.getGuild().getMembersWithRoles(defRole)) {
                if (member.getUser().isBot()) continue;
                members.add(member);
            }
        }
        if (allowAlliance) {
            for (Map.Entry<Long, Role> entry : roleMap.entrySet()) {
                if (entry.getKey() == 0) continue;
                for (Member member : entry.getValue().getGuild().getMembersWithRoles(entry.getValue())) {
                    if (member.getUser().isBot()) continue;
                    DBNation nation = DiscordUtil.getNation(member.getIdLong());
                    if (nation == null || nation.getAlliance_id() != entry.getKey().intValue()) continue;
                    members.add(member);
                }
            }
        }
        return members;
    }

    /*
    Get the root or alliance specific role for an author
     */
    public Role toRole(User author, GuildDB db) {
        if (allowAlliance()) {
            DBNation nation = DiscordUtil.getNation(author);
            if (nation != null && nation.getAlliance_id() != 0) {
                Role role = db.getRole(this, (long) nation.getAlliance_id());
                if (role != null) return role;
            }
        }
        return toRole2(db);
    }

    public Role toRole(int allianceId, GuildDB db) {
        if (allianceId != 0 && allowAlliance) {
            Role role = db.getRole(this, (long) allianceId);
            if (role != null) return role;
        }
        return toRole2(db);
    }
}
