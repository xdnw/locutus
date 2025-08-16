package link.locutus.discord.util.task.roles;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.commands.manager.v2.builder.RankBuilder;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.scheduler.KeyValue;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AutoRoleTask implements IAutoRoleTask {
    private final Guild guild;
    private int position;
    private Map<Integer, Role> allianceRoles;
    private Role registeredRole;
    private final GuildDB db;
    private GuildDB.AutoNickOption setNickname;
    private GuildDB.AutoRoleOption setAllianceMask;

    private boolean autoRoleAllyGov = false;

    private Function<Integer, Boolean> allowedAAs = f -> true;
    private Map<Integer, Set<Role>> cityRoleMap;
    private Map<NationFilter, Role> conditionalRoles;
    private Set<Role> cityRoles;
    private Rank autoRoleRank;
    private boolean autoRoleMembersApps;
    private Map<Long, Role> applicantRole;
    private Set<Role> applicantRoleValues;
    private Map<Long, Role> memberRole;
    private Set<Role> memberRoleValues;
    private Set<Integer> extensions;

    public AutoRoleTask(Guild guild, GuildDB db) {
        this.guild = guild;
        this.db = db;
        this.allianceRoles = new Int2ObjectOpenHashMap<>();
        this.position = -1;
        this.extensions = db.getCoalition(Coalition.EXTENSION);
        syncDB();
    }

    public boolean isMember(Member member, DBNation nation) {
        Set<Integer> aaIds = GuildKey.ALLIANCE_ID.getOrNull(db);
        if (aaIds == null || aaIds.isEmpty()) {
            return db.getAllies(false).contains(nation.getAlliance_id()) && nation.getPositionEnum().id >= Rank.MEMBER.id;
        }
        return (nation != null && (aaIds.contains(nation.getAlliance_id()) && nation.getPositionEnum().id >= Rank.MEMBER.id) || extensions.contains(nation.getAlliance_id()));
    }

    public void setAllianceMask(GuildDB.AutoRoleOption value) {
        this.setAllianceMask = value == null ? GuildDB.AutoRoleOption.FALSE : value;
    }

    public void setNickname(GuildDB.AutoNickOption value) {
        this.setNickname = value == null ? GuildDB.AutoNickOption.FALSE : value;
    }

    public Function<Integer, Boolean> getAllowedAlliances() {
        return allowedAAs;
    }

    public synchronized String syncDB() {
        Map<String, String> info = new LinkedHashMap<>();

        GuildDB.AutoNickOption nickOpt = db.getOrNull(GuildKey.AUTONICK);
        if (nickOpt != null) {
            setNickname(nickOpt);
        }

        GuildDB.AutoRoleOption roleOpt = db.getOrNull(GuildKey.AUTOROLE_ALLIANCES);
        if (roleOpt != null) {
            setAllianceMask(roleOpt);
        }

        List<Role> roles = db.getGuild().getRoles();
        this.allianceRoles = new ConcurrentHashMap<>(DiscordUtil.getAARoles(roles));
        this.cityRoleMap = new ConcurrentHashMap<>(DiscordUtil.getCityRoles(roles));
        this.cityRoles = new HashSet<>();
        for (Set<Role> value : cityRoleMap.values()) cityRoles.addAll(value);

        this.conditionalRoles = GuildKey.CONDITIONAL_ROLES.getOrNull(db);
        if (this.conditionalRoles != null) this.conditionalRoles.keySet().forEach(NationFilter::recalculate);

        fetchTaxRoles(true);

        this.autoRoleAllyGov = Boolean.TRUE == db.getOrNull(GuildKey.AUTOROLE_ALLY_GOV);

        this.autoRoleRank = db.getOrNull(GuildKey.AUTOROLE_ALLIANCE_RANK);
        if (this.autoRoleRank == null) this.autoRoleRank = Rank.MEMBER;
        allowedAAs = null;
        Integer topX = db.getOrNull(GuildKey.AUTOROLE_TOP_X);
        if (topX != null) {
            Map<Integer, Double> aas = new RankBuilder<>(Locutus.imp().getNationDB().getAllNations()).group(DBNation::getAlliance_id).sumValues(DBNation::getScore).sort().get();
            List<Integer> topAAIds = new ArrayList<>(aas.keySet());
            if (topAAIds.size() > topX) {
                topAAIds = topAAIds.subList(0, topX);
            }
            Set<Integer> topAAIdSet = new IntOpenHashSet(topAAIds);
            topAAIdSet.remove(0);
            allowedAAs = topAAIdSet::contains;
        }
        if (setAllianceMask == GuildDB.AutoRoleOption.ALLIES) {
            Set<Integer> allies = new IntOpenHashSet(db.getAllies(true));

            if (allowedAAs == null) allowedAAs = allies::contains;
            else {
                Function<Integer, Boolean> previousAllowed = allowedAAs;
                allowedAAs = f -> previousAllowed.apply(f) || allies.contains(f);
            }
        } else if (allowedAAs == null) {
            allowedAAs = f -> true;
        }
        Set<Integer> masked = db.getCoalition(Coalition.MASKEDALLIANCES);
        if (!masked.isEmpty()) {
            Function<Integer, Boolean> previousAllowed = allowedAAs;
            allowedAAs = f -> previousAllowed.apply(f) || masked.contains(f);
        }
        registeredRole = Roles.REGISTERED.toRole2(guild);

        this.autoRoleMembersApps = GuildKey.AUTOROLE_MEMBER_APPS.getOrNull(db) == Boolean.TRUE && !autoRoleAllyGov;
        this.applicantRole = Roles.APPLICANT.toRoleMap(db);
        this.applicantRoleValues = new HashSet<>(applicantRole.values());
        this.memberRole = Roles.MEMBER.toRoleMap(db);
        this.memberRoleValues = new HashSet<>(memberRole.values());
        this.extensions = db.getCoalition(Coalition.EXTENSION);

        info.put(GuildKey.AUTONICK.name(), setNickname + "");
        info.put(GuildKey.AUTOROLE_ALLIANCES.name(), setAllianceMask + "");
        info.put(GuildKey.AUTOROLE_ALLIANCE_RANK.name(), autoRoleRank == null ? "Member" : autoRoleRank.name());
        info.put(GuildKey.AUTOROLE_TOP_X.name(), allowedAAs == null ? "All" : "Top " + topX);
        if (!masked.isEmpty()) {
            info.put("Masked Alliances", masked.stream().map(f -> PW.getName(f, true)).collect(Collectors.joining("\n")));
        }
        info.put(GuildKey.AUTOROLE_ALLY_GOV.name() + " (for coalition servers)", autoRoleAllyGov + "");
        if (allianceRoles.isEmpty()) {
            info.put("Found Alliance Roles", "None (Roles are generated based on settings)");
        } else {
            // join by markdown list
            List<String> aaNamesList = allianceRoles.entrySet().stream().map(f -> f.getKey() + " -> " + f.getValue().getName()).toList();
            String listStr = "- " + String.join("\n- ", aaNamesList);
            info.put("Found Alliance Roles", listStr);
        }

        Set<Integer> aaIds = db.getAllianceIds();
        Set<Integer> allies = db.getCoalition(Coalition.ALLIES);

        info.put(Roles.REGISTERED.name(), registeredRole == null ? "None" : registeredRole.getName());
        if (cityRoles.isEmpty()) {
            info.put("Found City Roles", "None (Make one e.g. `c10-20` or `c21+`)");
        } else {
            // join by markdown list
            String key = "Found City Roles";
            if (aaIds.isEmpty() && allies.isEmpty()) {
                key += " (No Alliance Set)";
            }
            List<String> cityNamesList = cityRoles.stream().map(Role::getName).toList();
            info.put(key, String.join("\n", cityNamesList));
        }
        if (taxRoles.isEmpty()) {
            info.put("Found Tax Roles", "None (Make one e.g. `25/25`)");
        } else {
            // join by markdown list
            List<String> taxNamesList = taxRoles.entrySet().stream().map(f -> f.getKey().getKey() + "/" + f.getKey().getValue() + " -> " + f.getValue().getName()).toList();
            info.put("Found Tax Roles", String.join("\n", taxNamesList));
        }
        if (autoRoleMembersApps) {
            StringBuilder infoStr = new StringBuilder();
            infoStr.append("True\n");
            infoStr.append(applicantRole.isEmpty() ? "No Applicant Role" : "- Applicant Role: " + DiscordUtil.toRoleString(applicantRole)).append("\n");
            infoStr.append(memberRole.isEmpty() ? "No Member Role" : "- Member Role: " + DiscordUtil.toRoleString(memberRole)).append("\n");
            info.put("Auto Role Members/Apps", infoStr.toString());
        } else {
            info.put("Auto Role Members/Apps", "False (see: " + CM.settings_role.AUTOROLE_MEMBER_APPS.cmd.toSlashMention() + ")");
        }
        if (conditionalRoles != null && !conditionalRoles.isEmpty()) {
            StringBuilder infoStr = new StringBuilder();
            for (Map.Entry<NationFilter, Role> entry : conditionalRoles.entrySet()) {
                NationFilter condition = entry.getKey();
                Role role = entry.getValue();
                infoStr.append("- " + condition.getFilter()).append(": ").append(role.getAsMention()).append("\n");
            }
            info.put("Conditional Roles", infoStr.toString());
        } else {
            info.put("Conditional Roles", "None (see: " + GuildKey.CONDITIONAL_ROLES.getCommandMention() + ")");
        }

        if (!aaIds.isEmpty()) {
            info.put("Alliances", aaIds.stream().map(f -> Integer.toString(f)).collect(Collectors.joining("\n")));
            if (!extensions.isEmpty()) {
                info.put("Extensions", extensions.stream().map(f -> Integer.toString(f)).collect(Collectors.joining("\n")));
            } else {
                info.put("Extensions", "None (see: " + CM.coalition.add.cmd.toSlashMention() + " with " + Coalition.EXTENSION.name() + ")");
            }
        } else {
            if (!allies.isEmpty()) {
                info.put("Alliances (Allies)", allies.stream().map(f -> Integer.toString(f)).collect(Collectors.joining("\n")));
            } else {
                info.put("Alliances", "None (see: " + GuildKey.ALLIANCE_ID.getCommandMention() + ")");
            }
        }


        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : info.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value.contains("\n")) {
                result.append(key).append(":\n").append(value).append("\n");
            } else {
                result.append(key).append(": ").append(value).append("\n");
            }
        }
        result.append("\n");
        result.append("\nSetting Info: " + CM.settings.info.cmd.toSlashMention());
        result.append("\nRole Info: " + CM.role.setAlias.cmd.toSlashMention());
        return result.toString();
    }

    public void autoRoleAllies(AutoRoleInfo info, Set<Member> members) {
        if (!members.isEmpty()) {
            DiscordDB discordDb = Locutus.imp().getDiscordDB();

            Set<Roles> maskRolesSet = db.getOrNull(GuildKey.AUTOROLE_ALLY_ROLES);

            Roles[] maskRoles = {Roles.MILCOM, Roles.ECON, Roles.FOREIGN_AFFAIRS, Roles.INTERNAL_AFFAIRS};
            if (maskRolesSet != null) {
                maskRoles = maskRolesSet.toArray(new Roles[0]);
            }
            Role[] thisDiscordRoles = new Role[maskRoles.length];
            boolean thisHasRoles = false;
            for (int i = 0; i < maskRoles.length; i++) {
                thisDiscordRoles[i] = maskRoles[i].toRole2(guild);
                thisHasRoles |= thisDiscordRoles[i] != null;
            }

            Set<Integer> allies = db.getAllies();
            if (allies.isEmpty()) return;

            List<Future<?>> tasks = new ArrayList<>();

            if (thisHasRoles) {
                Map<Member, List<Role>> memberRoles = new HashMap<>();

                for (Integer ally : allies) {
                    GuildDB guildDb = Locutus.imp().getGuildDBByAA(ally);
                    if (guildDb == null) {
                        continue;
                    }
                    Guild allyGuild = guildDb.getGuild();
                    if (allyGuild == null) {
                        continue;
                    }
                    Map<Long, Role>[] allyDiscordRoles = new Map[maskRoles.length];
                    boolean[] hasRoles = new boolean[maskRoles.length];

                    boolean hasRole = false;
                    for (int i = 0; i < maskRoles.length; i++) {
                        Roles role = maskRoles[i];
                        Map<Long, Role> roleMap = role.toRoleMap(guildDb);
                        allyDiscordRoles[i] = roleMap;
                        hasRole |= !roleMap.isEmpty();
                        hasRoles[i] = !roleMap.isEmpty();
                    }

                    if (hasRole) {
                        Set<Long> otherMemberIds;
                        if (members.size() == 1) {
                            otherMemberIds = Collections.singleton(members.iterator().next().getIdLong());
                        } else {
                            otherMemberIds = allyGuild.getMembers().stream().map(ISnowflake::getIdLong).collect(Collectors.toSet());
                        }
                        for (Member member : members) {
                            if (!otherMemberIds.contains(member.getIdLong())) {
                                continue;
                            }
                            PNWUser user = discordDb.getUserFromDiscordId(member.getIdLong());
                            if (user == null) {
                                continue;
                            }
                            DBNation nation = Locutus.imp().getNationDB().getNationById(user.getNationId());
                            if (nation == null) {
                                continue;
                            }
                            Member allyMember = allyGuild.getMemberById(member.getIdLong());
                            if (allyMember == null) {
                                continue;
                            }
                            if (allies.contains(nation.getAlliance_id()) && nation.getPosition() > 1) {
                                Set<Role> roles = member.getUnsortedRoles();
                                Set<Role> allyRoles = allyMember.getUnsortedRoles();
//                                if (!memberRole.isEmpty()) {
//                                    Role addRole = memberRole.get((long) nation.getAlliance_id());
//                                    if (addRole == null) addRole = memberRole.get(0L);
//                                    if (addRole != null && !roles.contains(addRole)) {
//                                        memberRoles.computeIfAbsent(member, f -> new ArrayList<>()).add(addRole);
//                                        if (!roles.contains(addRole)) {
//                                            info.addRoleToMember(member, addRole);
//                                        }
//                                    }
//                                }
                                for (int i = 0; i < maskRoles.length; i++) {
                                    if (!hasRoles[i]) continue;
                                    Role thisRole = thisDiscordRoles[i];
                                    if (thisRole == null) continue;

                                    Map<Long, Role> allyRoleMap = allyDiscordRoles[i];
                                    Role role1 = allyRoleMap.get((long) 0L);
                                    Role role2 = allyRoleMap.get((long) nation.getAlliance_id());
                                    boolean has = (role1 != null && allyRoles.contains(role1)) || (role2 != null && allyRoles.contains(role2));
                                    if (has) {
                                        memberRoles.computeIfAbsent(member, f -> new ArrayList<>()).add(thisRole);
                                        if (!roles.contains(thisRole)) {
                                            info.addRoleToMember(member, thisRole);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                for (Member member : members) {
                    List<Role> roles = new ArrayList<>(member.getUnsortedRoles());
                    boolean isMember = false;
                    if (!memberRole.isEmpty()) {
                        DBNation nation = DiscordUtil.getNation(member.getIdLong());
                        if (nation != null) {
                            if (allies.contains(nation.getAlliance_id()) && nation.getPosition() > 1) {
                                isMember = true;
                                Role addRole = memberRole.get((long) nation.getAlliance_id());
                                if (addRole == null) addRole = memberRole.get(0L);
                                if (addRole != null && !roles.contains(addRole)) {
                                    memberRoles.computeIfAbsent(member, f -> new ArrayList<>()).add(addRole);
                                    if (!roles.contains(addRole)) {
                                        info.addRoleToMember(member, addRole);
                                    }
                                }
                            }
                        }
                    }
                    if (!isMember) {
                        for (Role role : memberRoleValues) {
                            if (roles.contains(role)) {
                                info.removeRoleFromMember(member, role);
                            }
                        }
                    }

                    List<Role> allowed = memberRoles.getOrDefault(member, new ArrayList<>());

                    for (Role role : thisDiscordRoles) {
                        if (roles.contains(role) && !allowed.contains(role)) {
                            info.removeRoleFromMember(member, role);
                        }
                    }
                }

            }
            for (Future<?> task : tasks) {
                try {
                    task.get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    @Override
    public synchronized AutoRoleInfo autoRoleAll() {
        String syncDbResult = syncDB();
        AutoRoleInfo info = new AutoRoleInfo(db, syncDbResult);

        List<Member> members = guild.getMembers();

        Map<Integer, Role> existantAllianceRoles = new Int2ObjectOpenHashMap<>(allianceRoles);

        Set<Integer> memberAllianceIds = new IntOpenHashSet();
        int requiredRank = autoRoleRank == null ? Rank.MEMBER.id : autoRoleRank.id;
        for (Member member : members) {
            DBNation nation = DiscordUtil.getNation(member.getIdLong());
            if (nation != null && nation.getPositionEnum().id >= requiredRank) {
                memberAllianceIds.add(nation.getAlliance_id());
            }
            try {
                autoRole(info, member, nation, true);
            } catch (Throwable e) {
                e.printStackTrace();
                throw e;
            }
        }
        if (autoRoleAllyGov) {
            HashSet<Member> memberSet = new HashSet<>(members);
            autoRoleAllies(info, memberSet);
        }

        for (Map.Entry<Integer, Role> entry : existantAllianceRoles.entrySet()) {
            Role role = entry.getValue();
            List<Member> withRole = guild.getMembersWithRoles(role);
            if (!memberAllianceIds.contains(entry.getKey()) && withRole.isEmpty()) {
                RateLimitUtil.queue(role.delete());
            } else {
                DBAlliance aa = DBAlliance.get(entry.getKey());
                if (aa != null) {
                    String expectedName = "AA " + entry.getKey() + " " + aa.getName();
                    if (!role.getName().equalsIgnoreCase(expectedName)) {
                        info.renameRole(role, expectedName);
                    }
                }

            }
        }
        return info;
    }

    @Override
    public synchronized AutoRoleInfo autoRole(Member member, DBNation nation) {
        AutoRoleInfo info = new AutoRoleInfo(db, "");
        autoRole(info, member, nation, false);
        return info;
    }

    public synchronized void autoNick(AutoRoleInfo info, boolean autoAll, Member member, DBNation nation) {
        if (nation == null) {
            return;
        }
        String leaderOrNation;
        switch (setNickname) {
            case LEADER:
                leaderOrNation = nation.getLeader();
                break;
            case NATION:
                leaderOrNation = nation.getNation();
                break;
            case DISCORD:
                leaderOrNation = member.getUser().getName();
                break;
            case NICKNAME:
                leaderOrNation = member.getUser().getEffectiveName();
                break;
            default:
                return;
        }
        boolean setName = leaderOrNation != null;

        String effective = member.getEffectiveName();
        if (autoAll && effective.contains("/")) {
            return;
        }

        String name = leaderOrNation + "/" + nation.getNation_id();
        if (name.length() > 32) {
            if (effective.equalsIgnoreCase(leaderOrNation)) {
                setName = false;
            } else {
                name = leaderOrNation;
            }
        }
        if (setName) {
            info.modifyNickname(member, name);
        }
    }

    public synchronized void autoRole(AutoRoleInfo info, Member member, DBNation nation, boolean autoAll) {
        this.registeredRole = Roles.REGISTERED.toRole2(guild);

        try {
        Set<Role> roles = member.getUnsortedRoles();

        boolean hasRegisteredRole = registeredRole != null && roles.contains(registeredRole);

        if (!hasRegisteredRole && registeredRole != null) {
            if (nation != null) {
                info.addRoleToMember(member, registeredRole);
            }
        }
        if (nation != null) {
            if (registeredRole == null) {
                info.logError(member, "No registered role exists. Please create one on discord, then use " + CM.role.setAlias.cmd.locutusRole(Roles.REGISTERED.name()).discordRole(null));
            } else {
                info.logError(member, "Not registered. See: " + CM.register.cmd.toSlashMention());
            }
        }

        if (!autoAll && this.autoRoleAllyGov) {
            autoRoleAllies(info, Collections.singleton(member));
        }

        if (setAllianceMask != null && setAllianceMask != GuildDB.AutoRoleOption.FALSE) {
            autoRoleAlliance(info, member, nation, autoAll);
        }

        autoRoleCities(info, member, nation);
        autoRoleConditions(info, member, nation);

        if (autoRoleMembersApps && !autoRoleAllyGov) {
            setAutoRoleMemberApp(info, member, nation);
        }

        if (setNickname != null && setNickname != GuildDB.AutoNickOption.FALSE && member.getNickname() == null && nation != null) {
            autoNick(info, autoAll, member, nation);
        } else if (!autoAll && (setNickname == null || setNickname == GuildDB.AutoNickOption.FALSE)) {
            info.logError(member, "Auto nickname is disabled");
        }

        } catch (Throwable e) {
            e.printStackTrace();
            info.logError(member, "Failed: " + e.getClass().getSimpleName() + " | " + e.getMessage());
        }
    }

    private Map<Map.Entry<Integer, Integer>, Role> taxRoles = null;

    private Map<Map.Entry<Integer, Integer>, Role> fetchTaxRoles(boolean update) {
        Map<Map.Entry<Integer, Integer>, Role> tmp = taxRoles;
        if (tmp == null || update) {
            taxRoles = tmp = new HashMap<>();
            for (Role role : guild.getRoles()) {
                String[] split = role.getName().split("/");
                if (split.length != 2 || !MathMan.isInteger(split[0]) || !MathMan.isInteger(split[1])) continue;

                int moneyRate = Integer.parseInt(split[0]);
                int rssRate = Integer.parseInt(split[1]);
                taxRoles.put(new KeyValue<>(moneyRate, rssRate), role);
            }
        }
        return tmp;
    }

    public void updateTaxRoles(AutoRoleInfo info, Map<DBNation, TaxBracket> brackets) {
        fetchTaxRoles(true);
        for (Member member : guild.getMembers()) {
            DBNation nation = DiscordUtil.getNation(member.getIdLong());
            TaxBracket bracket = nation != null ? brackets.get(nation) : null;
            updateTaxRole(info, member, bracket);
        }
    }

    public void updateTaxRole(AutoRoleInfo info, Member member, TaxBracket bracket) {
        Map<Map.Entry<Integer, Integer>, Role> tmpTaxRoles = fetchTaxRoles(false);
        Role expectedRole = null;
        if (bracket != null) {
            Map.Entry<Integer, Integer> key = new KeyValue<>(bracket.moneyRate, bracket.rssRate);
            expectedRole = tmpTaxRoles.get(key);
        }

        Set<Role> roles = member.getUnsortedRoles();

        for (Map.Entry<Map.Entry<Integer, Integer>, Role> entry : tmpTaxRoles.entrySet()) {
            Role taxRole = entry.getValue();
            if (!taxRole.equals(expectedRole) && roles.contains(taxRole)) {
                info.removeRoleFromMember(member, taxRole);
            }
        }
        if (expectedRole != null && !roles.contains(expectedRole)) {
            info.addRoleToMember(member, expectedRole);
        }
    }

    public void autoRoleAlliance(AutoRoleInfo info, Member member, DBNation nation, boolean autoAll) {
        if (nation != null) {
            if (!allianceRoles.isEmpty() && position == -1) {
                position = allianceRoles.values().iterator().next().getPosition();
            }

            int alliance_id = nation.getAlliance_id();
            if (nation.getPosition() < autoRoleRank.id || !allowedAAs.apply(alliance_id)) {
                alliance_id = 0;
            }

            Map<Integer, Role> myRoles = DiscordUtil.getAARoles(member.getUnsortedRoles());
            if (myRoles.size() == 1) {
                int aaRole = myRoles.keySet().iterator().next();
                if (aaRole == alliance_id) return;
            } else if (myRoles.isEmpty() && alliance_id == 0) {
                return;
            }

            if (alliance_id == 0) {
                for (Map.Entry<Integer, Role> entry : myRoles.entrySet()) {
                    info.removeRoleFromMember(member, entry.getValue());
                }
                return;
            }

            Role role = allianceRoles.get(alliance_id);

            for (Map.Entry<Integer, Role> entry : myRoles.entrySet()) {
                if (entry.getKey() != alliance_id) {
                    info.removeRoleFromMember(member, entry.getValue());
                }
            }
            if (!myRoles.containsKey(alliance_id)) {
                if (role == null) {
                    String roleName = "AA " + alliance_id + " " + nation.getAllianceName();
                    List<Role> roles = guild.getRolesByName(roleName, false);
                    if (roles.size() > 0) role = roles.get(0);
                    if (role == null) {
                        AutoRoleInfo.RoleOrCreate roleAdd = info.createRole(null, roleName, position, info.supplyColor(alliance_id, allianceRoles.values()));
                        info.addRoleToMember(member, roleAdd);
                    }
                }
                if (role != null) {
                    info.addRoleToMember(member, role);
                } else {
                    // (position, guild, alliance_id, nation.getAllianceName())
                }
            }
        }
        if (nation == null) {
            Map<Integer, Role> memberAARoles = DiscordUtil.getAARoles(member.getUnsortedRoles());
            if (!memberAARoles.isEmpty()) {
                for (Map.Entry<Integer, Role> entry : memberAARoles.entrySet()) {
                    info.removeRoleFromMember(member, entry.getValue());
                }
            }
        }
    }

    @Override
    public AutoRoleInfo autoRoleCities(Member member, DBNation nation) {
        AutoRoleInfo info = new AutoRoleInfo(db, "");
        autoRoleCities(info, member, nation);
        info.execute();
        return info;
    }


    @Override
    public AutoRoleInfo autoRoleConditions(Member member, DBNation nation) {
        AutoRoleInfo info = new AutoRoleInfo(db, "");
        autoRoleConditions(info, member, nation);
        info.execute();
        return info;
    }

    @Override
    public AutoRoleInfo autoRoleMemberApp(Member member, DBNation nation) {
        if (!autoRoleMembersApps) return null;
        AutoRoleInfo info = new AutoRoleInfo(db, "");
        setAutoRoleMemberApp(info, member, nation);
        info.execute();
        return info;
    }

    @Override
    public AutoRoleInfo updateTaxRoles(Map<DBNation, TaxBracket> brackets) {
        AutoRoleInfo info = new AutoRoleInfo(db, "");
        updateTaxRoles(info, brackets);
        info.execute();
        return info;
    }

    @Override
    public AutoRoleInfo updateTaxRole(Member member, TaxBracket bracket) {
        AutoRoleInfo info = new AutoRoleInfo(db, "");
        updateTaxRole(info, member, bracket);
        info.execute();
        return info;
    }

    private void setAutoRoleMemberApp(AutoRoleInfo info, Member member, DBNation nation) {
        if (!autoRoleMembersApps) return;
        if (memberRole.isEmpty() && applicantRole.isEmpty()) {
            return;
        }
        Set<Role> myRoles = member.getUnsortedRoles();
        if (nation != null && (db.isAllianceId(nation.getAlliance_id()) || extensions.contains(nation.getAlliance_id()))) {
            if (nation.getPositionEnum().id > Rank.APPLICANT.id) {
                if (!memberRole.isEmpty()) {
                    Role addMemberRole = memberRole.get((long) nation.getAlliance_id());
                    if (addMemberRole == null) addMemberRole = memberRole.get(0L);
                    if (addMemberRole != null && !myRoles.contains(addMemberRole)) {
                        info.addRoleToMember(member, addMemberRole);
                    }
                    for (Role role : memberRoleValues) {
                        if (role != addMemberRole && myRoles.contains(role)) {
                            info.removeRoleFromMember(member, role);
                        }
                    }
                }
                for (Role role : applicantRoleValues) {
                    if (myRoles.contains(role)) {
                        info.removeRoleFromMember(member, role);
                    }
                }
            } else {
                if (!applicantRole.isEmpty()) {
                    Role addApplicantRole = applicantRole.get((long) nation.getAlliance_id());
                    if (addApplicantRole == null) addApplicantRole = applicantRole.get(0L);
                    if (addApplicantRole != null && !myRoles.contains(addApplicantRole)) {
                        info.addRoleToMember(member, addApplicantRole);
                    }
                    for (Role role : applicantRoleValues) {
                        if (role != addApplicantRole && myRoles.contains(role)) {
                            info.removeRoleFromMember(member, role);
                        }
                    }
                }
                for (Role role : memberRoleValues) {
                    if (myRoles.contains(role)) {
                        info.removeRoleFromMember(member, role);
                    }
                }
            }
        } else {
            for (Role role : memberRoleValues) {
                if (myRoles.contains(role)) {
                    info.removeRoleFromMember(member, role);
                }
            }
            for (Role role : applicantRoleValues) {
                if (myRoles.contains(role)) {
                    info.removeRoleFromMember(member, role);
                }
            }
        }
    }

    public void autoRoleConditions(AutoRoleInfo info, Member member, DBNation nation) {
        if (conditionalRoles == null || conditionalRoles.isEmpty()) return;
        if (nation != null && isMember(member, nation)) {
            for (Map.Entry<NationFilter, Role> entry : conditionalRoles.entrySet()) {
                Predicate<DBNation> condition = entry.getKey().toCached(TimeUnit.MINUTES.toMillis(1));
                Set<Role> memberRoles = member.getUnsortedRoles();
                if (condition.test(nation)) {
                    if (!memberRoles.contains(entry.getValue())) {
                        info.addRoleToMember(member, entry.getValue());
                    }
                } else {
                    if (memberRoles.contains(entry.getValue())) {
                        info.removeRoleFromMember(member, entry.getValue());
                    }
                }
            }
        } else {
            Set<Role> memberRoles = member.getUnsortedRoles();
            for (Role role : conditionalRoles.values()) {
                if (memberRoles.contains(role)) {
                    info.removeRoleFromMember(member, role);
                }
            }
        }
    }

    public void autoRoleCities(AutoRoleInfo info, Member member, DBNation nation) {
        if (cityRoles.isEmpty()) return;
        if (nation != null && isMember(member, nation)) {
            Set<Role> allowed = new HashSet<>(cityRoleMap.getOrDefault(nation.getCities(), new HashSet<>()));
            Set<Role> memberRoles = member.getUnsortedRoles();
            for (Role role : allowed) {
                if (!memberRoles.contains(role)) {
                    info.addRoleToMember(member, role);
                }
            }
            for (Role role : cityRoles) {
                if (memberRoles.contains(role) && !allowed.contains(role)) {
                    info.removeRoleFromMember(member, role);
                }
            }
        } else {
            Set<Role> memberRoles = member.getUnsortedRoles();
            for (Role role : cityRoles) {
                if (memberRoles.contains(role)) {
                    info.removeRoleFromMember(member, role);
                }
            }
        }
    }
}
