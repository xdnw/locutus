package link.locutus.discord.util.task.roles;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.math.CIEDE2000;
import link.locutus.discord.apiv1.enums.Rank;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import retrofit2.http.HEAD;

import java.awt.Color;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
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
    private Set<Role> cityRoles;
    private Rank autoRoleRank;

    public AutoRoleTask(Guild guild, GuildDB db) {
        this.guild = guild;
        this.db = db;
        this.allianceRoles = new HashMap<>();
        this.position = -1;
        syncDB();
    }

    public boolean isMember(Member member, DBNation nation) {
        Set<Integer> aaIds = GuildKey.ALLIANCE_ID.getOrNull(db);
        if (aaIds == null || aaIds.isEmpty()) {
            return db.getAllies(false).contains(nation.getAlliance_id()) && nation.getPositionEnum().id >= Rank.MEMBER.id;
        }
        return (nation != null && aaIds.contains(nation.getAlliance_id()) && nation.getPositionEnum().id >= Rank.MEMBER.id);
    }

    public void setAllianceMask(GuildDB.AutoRoleOption value) {
        this.setAllianceMask = value == null ? GuildDB.AutoRoleOption.FALSE : value;
    }

    public void setNickname(GuildDB.AutoNickOption value) {
        this.setNickname = value == null ? GuildDB.AutoNickOption.FALSE : value;
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

        initRegisteredRole = false;
        List<Role> roles = db.getGuild().getRoles();
        this.allianceRoles = new ConcurrentHashMap<>(DiscordUtil.getAARoles(roles));
        this.cityRoleMap = new ConcurrentHashMap<>(DiscordUtil.getCityRoles(roles));
        this.cityRoles = new HashSet<>();
        for (Set<Role> value : cityRoleMap.values()) cityRoles.addAll(value);

        fetchTaxRoles(true);

        this.autoRoleAllyGov = Boolean.TRUE == db.getOrNull(GuildKey.AUTOROLE_ALLY_GOV);

        this.autoRoleRank = db.getOrNull(GuildKey.AUTOROLE_ALLIANCE_RANK);
        if (this.autoRoleRank == null) this.autoRoleRank = Rank.MEMBER;
        allowedAAs = null;
        Integer topX = db.getOrNull(GuildKey.AUTOROLE_TOP_X);
        if (topX != null) {
            Map<Integer, Double> aas = new RankBuilder<>(Locutus.imp().getNationDB().getNations().values()).group(DBNation::getAlliance_id).sumValues(DBNation::getScore).sort().get();
            List<Integer> topAAIds = new ArrayList<>(aas.keySet());
            if (topAAIds.size() > topX) {
                topAAIds = topAAIds.subList(0, topX);
            }
            Set<Integer> topAAIdSet = new HashSet<>(topAAIds);
            topAAIdSet.remove(0);
            allowedAAs = f -> topAAIdSet.contains(f);
        }
        if (setAllianceMask == GuildDB.AutoRoleOption.ALLIES) {
            Set<Integer> allies = new HashSet<>(db.getAllies(true));

            if (allowedAAs == null) allowedAAs = f -> allies.contains(f);
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
        registeredRole = Roles.REGISTERED.toRole(guild);

        info.put(GuildKey.AUTONICK.name(), setNickname + "");
        info.put(GuildKey.AUTOROLE_ALLIANCES.name(), setAllianceMask + "");
        info.put(GuildKey.AUTOROLE_ALLIANCE_RANK.name(), autoRoleRank == null ? "Member" : autoRoleRank.name());
        info.put(GuildKey.AUTOROLE_TOP_X.name(), allowedAAs == null ? "All" : "Top " + topX);
        if (!masked.isEmpty()) {
            info.put("Masked Alliances", masked.stream().map(f -> PnwUtil.getName(f, true)).collect(Collectors.joining("\n")));
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
        info.put(Roles.REGISTERED.name(), registeredRole == null ? "None" : registeredRole.getName());
        if (cityRoles.isEmpty()) {
            info.put("Found City Roles", "None (Make one e.g. `c10-20` or `c21+`)");
        } else {
            // join by markdown list
            List<String> cityNamesList = cityRoles.stream().map(Role::getName).toList();
            info.put("Found City Roles", String.join("\n", cityNamesList));
        }
        if (taxRoles.isEmpty()) {
            info.put("Found Tax Roles", "None (Make one e.g. `25/25`)");
        } else {
            // join by markdown list
            List<String> taxNamesList = taxRoles.entrySet().stream().map(f -> f.getKey().getKey() + "/" + f.getKey().getValue() + " -> " + f.getValue().getName()).toList();
            info.put("Found Tax Roles", String.join("\n", taxNamesList));
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

            Role memberRole = Roles.MEMBER.toRole(guild);

            Set<Roles> maskRolesSet = db.getOrNull(GuildKey.AUTOROLE_ALLY_ROLES);

            Roles[] maskRoles = {Roles.MILCOM, Roles.ECON, Roles.FOREIGN_AFFAIRS, Roles.INTERNAL_AFFAIRS};
            if (maskRolesSet != null) {
                maskRoles = maskRolesSet.toArray(new Roles[0]);
            }
            Role[] thisDiscordRoles = new Role[maskRoles.length];
            boolean thisHasRoles = false;
            for (int i = 0; i < maskRoles.length; i++) {
                thisDiscordRoles[i] = maskRoles[i].toRole(guild);
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
                    Role[] allyDiscordRoles = new Role[maskRoles.length];

                    boolean hasRole = false;
                    for (int i = 0; i < maskRoles.length; i++) {
                        Roles role = maskRoles[i];
                        Role discordRole = role.toRole(allyGuild);
                        allyDiscordRoles[i] = discordRole;
                        hasRole |= discordRole != null;
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
                            DBNation nation = Locutus.imp().getNationDB().getNation(user.getNationId());

                            if (nation == null) {
                                continue;
                            }

                            Member allyMember = allyGuild.getMemberById(member.getIdLong());
                            if (allyMember == null) {
                                continue;
                            }

                            if (allies.contains(nation.getAlliance_id()) && nation.getPosition() > 1) {
                                List<Role> roles = member.getRoles();
                                List<Role> allyRoles = allyMember.getRoles();

                                // set member
                                if (memberRole != null) {
                                    memberRoles.computeIfAbsent(member, f -> new ArrayList<>()).add(memberRole);
                                    if (!roles.contains(memberRole)) {
                                        info.addRoleToMember(member, memberRole);
                                    }
                                }
                                for (int i = 0; i < allyDiscordRoles.length; i++) {
                                    Role allyRole = allyDiscordRoles[i];
                                    Role thisRole = thisDiscordRoles[i];
                                    if (allyRole == null || thisRole == null) {
//                                        if (thisRole != null) output.accept("Role not registered " + thisRole.getName());
                                        continue;
                                    }

                                    if (allyRoles.contains(allyRole)) {
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
                    List<Role> roles = new ArrayList<>(member.getRoles());

                    boolean isMember = false;
                    if (memberRole != null) {
                        DBNation nation = DiscordUtil.getNation(member.getIdLong());
                        if (nation != null) {
                            if (allies.contains(nation.getAlliance_id()) && nation.getPosition() > 1) {
                                isMember = true;
                                if (!roles.contains(memberRole)) {
                                    info.addRoleToMember(member, memberRole);
                                }
                            }
                        }
                    }
                    if (!isMember && roles.contains(memberRole)) {
                        info.removeRoleFromMember(member, memberRole);
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

        Map<Integer, Role> existantAllianceRoles = new HashMap<>(allianceRoles);

        Set<Integer> memberAllianceIds = new HashSet<>();
        int requiredRank = autoRoleRank == null ? Rank.MEMBER.id : autoRoleRank.id;
        for (int i = 0; i < members.size(); i++) {
            Member member = members.get(i);
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
                allianceRoles.remove(entry.getKey());
                RateLimitUtil.queue(role.delete());
            }
        }
        return info;
    }

    private boolean initRegisteredRole = false;
    private Map<Integer, Integer> nationAACache = new HashMap<>();

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
            default:
                return;
        }
        boolean setName = true;

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
        initRegisteredRole = true;
        this.registeredRole = Roles.REGISTERED.toRole(guild);

        try {
        List<Role> roles = member.getRoles();

        boolean hasRegisteredRole = registeredRole != null && roles.contains(registeredRole);

        if (!hasRegisteredRole && registeredRole != null) {
            if (nation != null) {
                info.addRoleToMember(member, registeredRole);
            }
        }
        if (nation != null) {
            if (registeredRole == null) {
                info.logError(member, "No registered role exists. Please create one on discord, then use " + CM.role.setAlias.cmd.create(Roles.REGISTERED.name(), null, null, null) + "");
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

        if (nation != null) {
            autoRoleCities(info, member, nation);
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
                taxRoles.put(new AbstractMap.SimpleEntry<>(moneyRate, rssRate), role);
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
            Map.Entry<Integer, Integer> key = new AbstractMap.SimpleEntry<>(bracket.moneyRate, bracket.rssRate);
            expectedRole = tmpTaxRoles.get(key);
        }

        List<Role> roles = member.getRoles();

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

            Integer currentAARole = nationAACache.get(nation.getNation_id());
            if (currentAARole != null && currentAARole.equals(alliance_id) && autoAll) {
                return;
            } else {
                nationAACache.put(nation.getNation_id(), alliance_id);
            }

            Map<Integer, Role> myRoles = DiscordUtil.getAARoles(member.getRoles());

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
                    if (roles.size() == 1) role = roles.get(0);
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
            Map<Integer, Role> memberAARoles = DiscordUtil.getAARoles(member.getRoles());
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

    public void autoRoleCities(AutoRoleInfo info, Member member, DBNation nation) {
        if (nation == null) {
            return;
        }
        if (cityRoles.isEmpty()) return;
        if (isMember(member, nation)) {
            Set<Role> allowed = new HashSet<>(cityRoleMap.getOrDefault(nation.getCities(), new HashSet<>()));
            List<Role> memberRoles = member.getRoles();
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
            List<Role> memberRoles = member.getRoles();
            for (Role role : cityRoles) {
                if (memberRoles.contains(role)) {
                    info.removeRoleFromMember(member, role);
                }
            }
        }
    }
}
