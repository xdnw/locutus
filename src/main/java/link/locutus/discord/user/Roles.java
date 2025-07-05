package link.locutus.discord.user;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.GuildSetting;
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
    REGISTERED(0, false, true, "auto role for anyone who is verified with the bot"),
    MEMBER(1, true, true, "Members can run commands"),
    ADMIN(2, false, true, "Admin has access to alliance / guild management commands"),

    MILCOM(3, true, true, "Access to milcom related commands", GuildKey.ALLIANCE_ID) {
        @Override
        public boolean has(Member member) {
            if (super.has(member)) return true;
            return MILCOM_NO_PINGS.has(member);
        }
    },
    MILCOM_NO_PINGS(4, true, true, "Access to milcom related commands- doesn't receive pings", GuildKey.ALLIANCE_ID, "MILCOM_ADVISOR"),

    ECON(5, true, true, "Has access to econ gov commands", null),
    ECON_STAFF(6, true, true, "Has access to economy information commands", GuildKey.ALLIANCE_ID, "ECON_LOW_GOV") {
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
    ECON_DEPOSIT_ALERTS(8, false, false,"Gets pinged when there is a deposit", GuildKey.DEPOSIT_ALERT_CHANNEL),
    ECON_WITHDRAW_ALERTS(9, false, false,"Gets pinged when there is a withdrawal", GuildKey.WITHDRAW_ALERT_CHANNEL, "ECON_GRANT_ALERTS"),
    ECON_WITHDRAW_SELF(10, true, true,"Can withdraw own funds", GuildKey.MEMBER_CAN_WITHDRAW),
    ECON_GRANT_SELF(11, true, true, "Role to allow member to grant themselves", GuildKey.MEMBER_CAN_WITHDRAW),

    FOREIGN_AFFAIRS(12, true, true, "Role required to see other alliance's embassy channel", GuildKey.ALLIANCE_ID),
    FOREIGN_AFFAIRS_STAFF(13, true, true, "Role for some basic FA commands", GuildKey.ALLIANCE_ID) {
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

    INTERNAL_AFFAIRS(14, true, true,"Access to IA related commands", GuildKey.ALLIANCE_ID),
    INTERNAL_AFFAIRS_STAFF(15, true, true, "Role for some basic IA commands, such as accepting applicants", GuildKey.ALLIANCE_ID) {
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

    APPLICANT(16, true, true, "Applying to join the alliance in-game", GuildKey.INTERVIEW_PENDING_ALERTS),
    INTERVIEWER(17, false, true, "Role to get pinged when a user requests an interview to join the alliance", GuildKey.INTERVIEW_PENDING_ALERTS),
    MENTOR(18, false, true, "Role for mentoring applicants who have completed their interview", GuildKey.INTERVIEW_PENDING_ALERTS),
    GRADUATED(19, true, true, "Members with this role can have their interview channels archived", GuildKey.INTERVIEW_PENDING_ALERTS) {
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
    RECRUITER(20, false, true, "Role to get pinged for recruitment messages (if enabled)", GuildKey.RECRUIT_MESSAGE_OUTPUT),

    TRADE_ALERT(21, false, false, "Gets pinged for trade alerts", GuildKey.TRADE_ALERT_CHANNEL),

    BEIGE_ALERT(22, false, false, "Gets pinged when a nation leaves beige (in their score range), and they have a slot free", GuildKey.BEIGE_ALERT_CHANNEL),
    BEIGE_ALERT_OPT_OUT(23, false, false,"Overrides the beige alert role", GuildKey.BEIGE_ALERT_CHANNEL),

    BOUNTY_ALERT(24, false, false, "Gets pings when bounties are placed in their score range"),
    BOUNTY_ALERT_OPT_OUT(38, false, false, "Opt out of received bounty alerts", GuildKey.BOUNTY_ALERT_CHANNEL),
//    MAP_FULL_ALERT("Gets pinged when you are on 12 MAPs in an offensive war", GuildKey.MEMBER_AUDIT_ALERTS),

//    WAR_ALERT("Opt out of received war target alerts", GuildKey.ENEMY_ALERT_CHANNEL),
    WAR_ALERT_OPT_OUT(25, false, false, "Opt out of received war target alerts", GuildKey.ENEMY_ALERT_CHANNEL),
    AUDIT_ALERT_OPT_OUT(26, false, false, "Opt out of received audit alerts", GuildKey.MEMBER_AUDIT_ALERTS),
    BLITZ_PARTICIPANT(27, false, false, "Opt in to blitz participation (clear this regularly)", GuildKey.ALLIANCE_ID),
    BLITZ_PARTICIPANT_OPT_OUT(28, false, false, "Opt in to blitz participation (clear this regularly)", GuildKey.ALLIANCE_ID),

    TEMP(29, false, false, "Role to signify temporary member, not elligable for grants", GuildKey.ALLIANCE_ID),

    MAIL(30, true, true, "Can use mail commands", GuildKey.API_KEY),

    BLOCKADED_ALERT(31, false, false, "Gets a ping when you are blockaded", GuildKey.BLOCKADED_ALERTS, "BLOCKADED_ALERTS"),
    UNBLOCKADED_ALERT(32, false, false, "Gets a ping when you are unblockaded", GuildKey.UNBLOCKADED_ALERTS, "UNBLOCKADED_ALERTS"),

    UNBLOCKADED_GOV_ROLE_ALERT(33, false, false, "Pings this role when any member is fully unblockaded", GuildKey.UNBLOCKADED_ALERTS, "UNBLOCKADED_GOV_ROLE_ALERTS"),
    ESCROW_GOV_ALERT(39, false, false, "Pings this role when any member is fully unblockaded and has an escrow balance", GuildKey.UNBLOCKADED_ALERTS, "UNBLOCKADED_GOV_ROLE_ALERTS"),

    TREASURE_ALERT(34, false, false, "Gets alerts in the TREASURE_ALERT_CHANNEL if a treasure is spawning in their range", GuildKey.TREASURE_ALERT_CHANNEL, "TREASURE_ALERTS"),
    TREASURE_ALERT_OPT_OUT(35, false, false, "Does not receive treasure alerts (even with the treasure alert role)", GuildKey.TREASURE_ALERT_CHANNEL, "TREASURE_ALERTS_OPT_OUT"),

    ENEMY_BEIGE_ALERT_AUDITOR(36, false, false, "Role to receive pings when an enemy gets beiged", GuildKey.ENEMY_BEIGED_ALERT_VIOLATIONS),

    GROUND_MILITARIZE_ALERT(37, false, false, "Role to receive pings when alliances militarize", GuildKey.AA_GROUND_UNIT_ALERTS, "GROUND_MILITARIZE_ROLE"),

    AI_COMMAND_ACCESS(40, false, true, "Access to AI commands on the discord server"),

    ESPIONAGE_ALERTS(41, false, false, "Role to receive pings when an alliance member gets spied", GuildKey.DEFENSE_WAR_CHANNEL),

    ENEMY_ALERT_OFFLINE(42, false, false, "Able to receive enemy alerts when offline or invisible on discord (unless opt out, or player setting overrides)", GuildKey.BEIGE_ALERT_CHANNEL),

    RESOURCE_CONVERSION(43, false, true, "Set a required role for accessing resource conversion (if enabled). If no role is set, then all members have access", GuildKey.RESOURCE_CONVERSION),

    WITHDRAW_ALERT_NO_NOTE(44, false, false, "Alert this role when a withdrawal occurs without a valid note, in the configured withdrawal alert channel", GuildKey.WITHDRAW_ALERT_CHANNEL),

    ;


    public static Roles[] values = values();
    private final String desc;
    private final GuildSetting key;

    private final int id;
    private final String legacy_name;
    private final boolean allowAlliance, allowAdminBypass;

    public static Long hasAlliance(Roles[] roles, User author, Guild guild) {
        Long min = null;
        for (Roles role : roles) {
            Long alliance = role.hasAlliance(author, guild);
            if (alliance != null) {
                if (min == null || alliance < min) {
                    min = alliance;
                    if (min == 0) return 0L;
                }
            }
        }
        return min;
    }

    public int getId() {
        return id;
    }

    private static final Map<Integer, Roles> ROLES_ID_MAP = new ConcurrentHashMap<>();

    public static String getValidRolesStringList() {
        return "\n- " + StringMan.join(Roles.values(), "\n- ");
    }
    Roles(int id, boolean allowAlliance, boolean allowAdminBypass, String desc) {
        this(id, allowAlliance, allowAdminBypass, desc, null);
    }
    Roles(int id, boolean allowAlliance, boolean allowAdminBypass, String desc, GuildSetting key) {
        this(id, allowAlliance, allowAdminBypass, desc, key, null);
    }
    Roles(int id, boolean allowAlliance, boolean allowAdminBypass, String desc, GuildSetting key, String legacy_name) {
        this.desc = desc;
        this.key = key;
        this.id = id;
        this.legacy_name = legacy_name;
        this.allowAlliance = allowAlliance;
        this.allowAdminBypass = allowAdminBypass;
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
        if (user == null || db == null) return Collections.emptySet();
        boolean hasAdmin = false;
        Member member = null;
        if (allowAdminBypass && user.getIdLong() == Settings.INSTANCE.APPLICATION_ID) hasAdmin = true;
        else if (allowAdminBypass && user.getIdLong() == Locutus.loader().getAdminUserId()) hasAdmin = true;
        else {
            member = db.getGuild().getMember(user);
            if (member != null) {
                hasAdmin = has(member);
            }
        }
        if (hasAdmin) {
            Set<Long> allowed = new LongOpenHashSet();
            allowed.add(0L);
            allowed.addAll(db.getAllianceIds().stream().map(Integer::longValue).collect(Collectors.toSet()));
            return allowed;
        } else {
            Set<Integer> aaIds = db.getAllianceIds();
            Set<Long> allowed = new LongOpenHashSet();

            if (member == null && user.isBot()) {
                if (Settings.INSTANCE.LEGACY_SETTINGS.WHITELISTED_BOT_IDS.contains(user.getIdLong())) {
                    boolean hasMember = false;
                    for (String roleName : user.getName().split("-")) {
                        if (roleName.equalsIgnoreCase(Roles.MEMBER.name()) || roleName.equalsIgnoreCase(Roles.ADMIN.name())) {
                            hasMember = true;
                            break;
                        }
                    }
                    if (hasMember) {
                        for (int aaId : aaIds) {
                            allowed.add((long) aaId);
                        }
                        allowed.add(0L);
                        allowed.add(db.getIdLong());
                    }
                }
                return allowed;
            }


            if (aaIds.isEmpty()) {
                if (has(member)) {
                    allowed.add(db.getIdLong());
                }
                return allowed;
            }
            for (Integer aaId : aaIds) {
                if (has(member, aaId)) {
                    allowed.add(aaId.longValue());
                }
            }
            return allowed;
        }
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
        if (user == null) return false;
        if (allowAdminBypass && user.getIdLong() == Locutus.loader().getAdminUserId()) return true;
        if (Locutus.imp().getServer() == null) {
            return false;
        }
        return has(user, Locutus.imp().getServer());
    }

    public boolean has(User user, GuildDB server, int alliance) {
        return has(user, server.getGuild(), alliance);
    }

    private static Set<Roles> getRoles(String webhookName) {
        Set<Roles> roles = new ObjectOpenHashSet<>();
        String[] roleNames = webhookName.split("-");
        for (String roleName : roleNames) {
            try {
                Roles role = Roles.valueOf(roleName.toUpperCase(Locale.ROOT));
                roles.add(role);
            } catch (IllegalArgumentException e) {
                // Ignore, not a valid role
            }
        }
        if (roles.contains(ADMIN)) {
            for (Roles role : Roles.values()) {
                if (role.allowAlliance || role.allowAdminBypass) {
                    roles.add(role);
                }
            }
        }
        if (roles.contains(INTERNAL_AFFAIRS)) {
            roles.add(INTERNAL_AFFAIRS_STAFF);
        }
        if (roles.contains(FOREIGN_AFFAIRS)) {
            roles.add(FOREIGN_AFFAIRS_STAFF);
        }
        if (roles.contains(MILCOM)) {
            roles.add(MILCOM_NO_PINGS);
        }
        if (roles.contains(ECON)) {
            roles.add(ECON_STAFF);
        }
        return roles;
    }

    public boolean has(User user, Guild server, int alliance) {
        if (user == null) return false;
        Member member = server.getMember(user);
        if (member == null && user.isBot()) {
            if (Settings.INSTANCE.LEGACY_SETTINGS.WHITELISTED_BOT_IDS.contains(user.getIdLong())) {
                Set<Roles> roles = getRoles(user.getName());
                return roles.contains(this);
            }
            return false;
        }
        return member != null && has(member, alliance);
    }

    public boolean has(Member member, int alliance) {
        if (member == null) return false;
        if (has(member)) return true;
        if (alliance == 0) return false;
        Role role = Locutus.imp().getGuildDB(member.getGuild()).getRole(this, (long) alliance);
        return role != null && getRoles(member).contains(role);
    }

    public boolean has(Member member) {
        if (member == null) return false;
        if (allowAdminBypass) {
            if (member.getIdLong() == Locutus.loader().getAdminUserId()) return true;
            if (member.isOwner()) return true;
            if (member.hasPermission(Permission.ADMINISTRATOR)) return true;
        }
        GuildDB db = Locutus.imp().getGuildDB(member.getGuild());

        Set<Role> roles = getRoles(member);
        if (allowAdminBypass) {
            for (Role discordRole : roles) {
                if (discordRole.hasPermission(Permission.ADMINISTRATOR)) {
                    return true;
                }
            }
        }
        Map<Long, Role> map = db.getRoleMap(this);
        Role serverRole = map.get(0L);
        if (serverRole != null && roles.contains(serverRole)) return true;
        DBNation nation = DiscordUtil.getNation(member.getIdLong());
        if (nation == null || nation.getAlliance_id() == 0) return false;
        Role aaRole = map.get((long) nation.getAlliance_id());
        return aaRole != null && roles.contains(aaRole);
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
        if (allowAdminBypass && (member.getIdLong() == Settings.INSTANCE.APPLICATION_ID
        || member.getIdLong() == Locutus.loader().getAdminUserId()
        || member.hasPermission(Permission.ADMINISTRATOR)
        || member.isOwner())) return 0L;
        GuildDB db = Locutus.imp().getGuildDB(member.getGuild());
        Set<Role> roles = getRoles(member);
        Map<Long, Role> map = db.getRoleMap(this);
        Role serverRole = map.get(0L);
        if (serverRole != null && roles.contains(serverRole)) return 0L;
        DBNation nation = DiscordUtil.getNation(member.getIdLong());
        if (nation == null || nation.getAlliance_id() == 0) return null;
        Role aaRole = map.get((long) nation.getAlliance_id());
        if (aaRole != null && roles.contains(aaRole)) return (long) nation.getAlliance_id();
        return null;
    }

    private static Set<Role> getRoles(Member member) {
        return member.getUnsortedRoles();
    }

    private Set<Role> getWebhookRoles(User user, Guild guild) {
        if (user.isBot() && Settings.INSTANCE.LEGACY_SETTINGS.WHITELISTED_BOT_IDS.contains(user.getIdLong())) {
            Set<Role> rolesCopy = new ObjectOpenHashSet<>();
            Set<Roles> lcRoles = getRoles(user.getName());
            for (Roles lcRole : lcRoles) {
                Role discRole = lcRole.toRole2(guild);
                if (discRole != null) {
                    rolesCopy.add(discRole);
                }
            }
            return rolesCopy;
        }
        return Collections.emptySet();
    }

    public boolean has(User user, Guild server) {
        if (user == null) return false;
        if (allowAdminBypass) {
            if (user.getIdLong() == Settings.INSTANCE.APPLICATION_ID) return true;
            if (user.getIdLong() == Locutus.loader().getAdminUserId()) return true;
        }
        if (server == null) return false;
        if (!server.isMember(user)) {
            if (user.isBot() && Settings.INSTANCE.LEGACY_SETTINGS.WHITELISTED_BOT_IDS.contains(user.getIdLong())) {
                Set<Roles> lcRoles = getRoles(user.getName());
                return lcRoles.contains(this);
            }
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
