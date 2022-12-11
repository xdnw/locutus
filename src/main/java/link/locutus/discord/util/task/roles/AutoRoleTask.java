package link.locutus.discord.util.task.roles;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
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

import java.awt.Color;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
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

    public void setAllianceMask(GuildDB.AutoRoleOption value) {
        this.setAllianceMask = value == null ? GuildDB.AutoRoleOption.FALSE : value;
    }

    public void setNickname(GuildDB.AutoNickOption value) {
        this.setNickname = value == null ? GuildDB.AutoNickOption.FALSE : value;
    }

    public synchronized void syncDB() {
        String nickOptStr = db.getInfo(GuildDB.Key.AUTONICK);
        if (nickOptStr != null) {
            try {
                setNickname(GuildDB.AutoNickOption.valueOf(nickOptStr.toUpperCase()));
            } catch (IllegalArgumentException e) {}
        }

        GuildDB.AutoRoleOption roleOpt = db.getOrNull(GuildDB.Key.AUTOROLE);
        if (roleOpt != null) {
            try {
                setAllianceMask(roleOpt);
            } catch (IllegalArgumentException e) {}
        }
        initRegisteredRole = false;
        List<Role> roles = guild.getRoles();
        this.allianceRoles = new ConcurrentHashMap<>(DiscordUtil.getAARoles(roles));
        this.cityRoleMap = new ConcurrentHashMap<>(DiscordUtil.getCityRoles(roles));
        this.cityRoles = new HashSet<>();
        for (Set<Role> value : cityRoleMap.values()) cityRoles.addAll(value);

        if (!cityRoles.isEmpty()) {
            System.out.println("City roles: " + guild.getIdLong());
            for (Role cityRole : cityRoles) {
                System.out.println(" - " + cityRole.getName());
            }
        }

        fetchTaxRoles(true);

        this.autoRoleAllyGov = Boolean.TRUE == db.getOrNull(GuildDB.Key.AUTOROLE_ALLY_GOV);

        this.autoRoleRank = db.getOrNull(GuildDB.Key.AUTOROLE_ALLIANCE_RANK);
        if (this.autoRoleRank == null) this.autoRoleRank = Rank.MEMBER;
        allowedAAs = null;
        Integer topX = db.getOrNull(GuildDB.Key.AUTOROLE_TOP_X);
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
            Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
            if (aaId != null) allies.add(aaId);

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
    }

    public void autoRoleAllies(Consumer<String> output, Set<Member> members) {
        if (output == null) output = f -> {};
        if (!members.isEmpty()) {
            DiscordDB discordDb = Locutus.imp().getDiscordDB();

            Role memberRole = Roles.MEMBER.toRole(guild);

            Set<Roles> maskRolesSet = db.getOrNull(GuildDB.Key.AUTOROLE_ALLY_ROLES);

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
                                        guild.addRoleToMember(member, memberRole).complete();
                                    }
                                }
                                for (int i = 0; i < allyDiscordRoles.length; i++) {
                                    Role allyRole = allyDiscordRoles[i];
                                    Role thisRole = thisDiscordRoles[i];
                                    if (allyRole == null || thisRole == null) {
                                        if (thisRole != null) output.accept("Role not registered " + thisRole.getName());
                                        continue;
                                    }

                                    if (allyRoles.contains(allyRole)) {
                                        memberRoles.computeIfAbsent(member, f -> new ArrayList<>()).add(thisRole);
                                        if (!roles.contains(thisRole)) {
                                            guild.addRoleToMember(member, thisRole).complete();
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
                                    guild.addRoleToMember(member, memberRole).complete();
                                }
                            }
                        }
                    }
                    if (!isMember && roles.contains(memberRole)) {
                        guild.removeRoleFromMember(member, memberRole).complete();
                    }

                    List<Role> allowed = memberRoles.getOrDefault(member, new ArrayList<>());

                    for (Role role : thisDiscordRoles) {
                        if (roles.contains(role) && !allowed.contains(role)) {
                            guild.removeRoleFromMember(member, role).complete();
                        }
                    }
                }
            }
        }
    }

    @Override
    public synchronized void autoRoleAll(Consumer<String> output) {
        syncDB();
        if (setNickname == GuildDB.AutoNickOption.FALSE && setAllianceMask == GuildDB.AutoRoleOption.FALSE) return;

        ArrayDeque<Future> tasks = new ArrayDeque<>();

        List<Member> members = guild.getMembers();

        Map<Integer, Role> existantAllianceRoles = new HashMap<>(allianceRoles);

        for (int i = 0; i < members.size(); i++) {
            Member member = members.get(i);
            try {
                autoRole(member, true, output, f -> tasks.add(f));
            } catch (Throwable e) {
                e.printStackTrace();
                throw e;
            }
        }
        if (autoRoleAllyGov) {
            HashSet<Member> memberSet = new HashSet<>(members);
            autoRoleAllies(output, memberSet);
        }
        while (!tasks.isEmpty()) {
            try {
                tasks.poll().get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            } catch (Throwable e) {
                e.printStackTrace();
                throw e;
            }
        }

        for (Map.Entry<Integer, Role> entry : existantAllianceRoles.entrySet()) {
            Role role = entry.getValue();
            List<Member> withRole = guild.getMembersWithRoles(role);
            if (withRole.isEmpty()) {
                allianceRoles.remove(entry.getKey());
                tasks.add(role.delete().submit());
            }
        }
        while (!tasks.isEmpty()) {
            try {
                tasks.poll().get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean initRegisteredRole = false;
    private Map<Integer, Integer> nationAACache = new HashMap<>();

    @Override
    public synchronized void autoRole(Member member, Consumer<String> output) {
        autoRole(member, false, output, f -> {});
    }

    public synchronized void autoNick(boolean autoAll, Member member, Consumer<String> output, Consumer<Future> tasks, Supplier<PNWUser> pnwUserSup, Supplier<DBNation> nationSup) {
        PNWUser pnwUser = pnwUserSup.get();
        if (pnwUser == null) {
            output.accept(member.getEffectiveName() + " has the registered role, but no DB entry has been found.");
            return;
        }
        DBNation nation = nationSup.get();
        if (nation == null) {
            output.accept(member.getEffectiveName() + " is registered, but no nation entry has been found");
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
            output.accept("Set " + member.getEffectiveName() + "  to " + name);
            try {
                tasks.accept(member.modifyNickname(name).submit());
            } catch (HierarchyException ignore) {
                output.accept(member.getEffectiveName() + " " + ignore.getMessage());
            }
        } else if (!autoAll) {
            output.accept(member.getEffectiveName() + " already matches their ingame nation");
        }
    }

    public synchronized void autoRole(Member member, boolean autoAll, Consumer<String> output, Consumer<Future> tasks) {
        if (setNickname == GuildDB.AutoNickOption.FALSE && setAllianceMask == GuildDB.AutoRoleOption.FALSE) {
            return;
        }
        if (allianceRoles.isEmpty()) syncDB();
        {
            initRegisteredRole = true;
            this.registeredRole = Roles.REGISTERED.toRole(guild);
        }

        try {
        User user = member.getUser();
        List<Role> roles = member.getRoles();
        Supplier<PNWUser> pnwUserSup = ArrayUtil.memorize(() -> Locutus.imp().getDiscordDB().getUser(user));
        Supplier<DBNation> nationSup = ArrayUtil.memorize(() -> {
            PNWUser pnwUser = pnwUserSup.get();
            if (pnwUser == null) return null;
            return Locutus.imp().getNationDB().getNation(pnwUser.getNationId());
        });

        boolean isRegistered = registeredRole != null && roles.contains(registeredRole);

        if (!isRegistered && registeredRole != null) {
            if (nationSup.get() != null) {
                guild.addRoleToMember(user.getIdLong(), registeredRole).complete();
                isRegistered = true;
            }
        }
        if (!isRegistered && !autoAll) {
            if (registeredRole == null) {
                output.accept("No registered role exists. Please create one on discord, then use " + CM.role.setAlias.cmd.create(Roles.REGISTERED.name(), null) + "");
            } else {
                output.accept(member.getEffectiveName() + " is NOT registered");
            }
        }

        if (!autoAll && this.autoRoleAllyGov) {
            autoRoleAllies(output, Collections.singleton(member));
        }

        if (setAllianceMask != null && setAllianceMask != GuildDB.AutoRoleOption.FALSE) {
            autoRoleAlliance(member, isRegistered, pnwUserSup, nationSup, autoAll, output, tasks);
        }

        if (isRegistered) {
            autoRoleCities(member, nationSup, output, tasks);
        }

        if (setNickname != null && setNickname != GuildDB.AutoNickOption.FALSE && member.getNickname() == null && isRegistered) {
            autoNick(autoAll, member, output, tasks, pnwUserSup, nationSup);
            PNWUser pnwUser = pnwUserSup.get();
        } else if (!autoAll && (setNickname == null || setNickname == GuildDB.AutoNickOption.FALSE)) {
            output.accept("Auto nickname is disabled");
        }

        } catch (Throwable e) {
            e.printStackTrace();
            output.accept("Failed for " + member.getEffectiveName() + ": " + e.getClass().getSimpleName() + " | " + e.getMessage());
//            e.printStackTrace();
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

    public void updateTaxRoles(Map<DBNation, TaxBracket> brackets) {
        fetchTaxRoles(true);
        for (Member member : guild.getMembers()) {
            DBNation nation = DiscordUtil.getNation(member.getIdLong());
            TaxBracket bracket = nation != null ? brackets.get(nation) : null;
            updateTaxRole(member, bracket);
        }
    }

    public void updateTaxRole(Member member, TaxBracket bracket) {
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
                RateLimitUtil.queue(guild.removeRoleFromMember(member, taxRole));
            }
        }
        if (expectedRole != null && !roles.contains(expectedRole)) {
            RateLimitUtil.queue(guild.addRoleToMember(member, expectedRole));
        }
    }

    public void autoRoleAlliance(Member member, boolean isRegistered, Supplier<PNWUser> pnwUserSup, Supplier<DBNation> nationSup, boolean autoAll, Consumer<String> output, Consumer<Future> tasks) {
        if (isRegistered) {
            PNWUser pnwUser = pnwUserSup.get();
            if (pnwUser != null) {
                DBNation nation = nationSup.get();
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

                    Role role = allianceRoles.get(alliance_id);
                    if (role == null && alliance_id != 0) {
                        role = createRole(position, guild, alliance_id, nation.getAllianceName());
                        if (role != null) {
                            allianceRoles.put(alliance_id, role);
                        }
                    }

                    Map<Integer, Role> myRoles = DiscordUtil.getAARoles(member.getRoles());

                    if (myRoles.size() == 1 && role != null && myRoles.get(alliance_id) == role) {
                        return;
                    }
                    if (alliance_id == 0) {
                        for (Map.Entry<Integer, Role> entry : myRoles.entrySet()) {
                            tasks.accept(guild.removeRoleFromMember(member, entry.getValue()).submit());
                        }
                        return;
                    }
                    for (Map.Entry<Integer, Role> entry : myRoles.entrySet()) {
                        if (entry.getValue().getIdLong() != role.getIdLong()) {
                            if (!autoAll)
                                output.accept("Remove " + entry.getValue().getName() + " to " + member.getEffectiveName());
                            tasks.accept(guild.removeRoleFromMember(member, entry.getValue()).submit());
                        }
                    }
                    if (!myRoles.containsKey(alliance_id)) {
                        if (!autoAll)
                            output.accept("Add " + role.getName() + " to " + member.getEffectiveName());
                        tasks.accept(guild.addRoleToMember(member, role).submit());
                    }
                } else {
                    if (!autoAll) output.accept("No nation found for " + pnwUser.getNationId());
                }
            } else isRegistered = false;
        }
        if (!isRegistered && !autoAll) {
            PNWUser pnwUser = pnwUserSup.get();
            if (pnwUser == null) {
                Map<Integer, Role> memberAARoles = DiscordUtil.getAARoles(member.getRoles());
                if (!memberAARoles.isEmpty()) {
                    for (Map.Entry<Integer, Role> entry : memberAARoles.entrySet()) {
                        output.accept("Remove " + entry.getValue().getName() + " from " + member.getEffectiveName());
                        tasks.accept(guild.removeRoleFromMember(member, entry.getValue()).submit());
                    }
                }
            }
        }
    }

    public void autoRoleCities(Member member, Supplier<DBNation> nationSup, Consumer<String> output, Consumer<Future> tasks) {
        if (cityRoles.isEmpty()) return;
        Role memberRole = Roles.MEMBER.toRole(member.getGuild());
        Integer allianceId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
        if (allianceId != null || memberRole != null) {
            DBNation nation = nationSup.get();
            if (nation == null) {
                return;
            }
            Set<Role> allowed;
            if ((allianceId != null && (nation.getAlliance_id() != allianceId || nation.getPosition() <= 1)) ||
                    (allianceId == null && (memberRole == null || !member.getRoles().contains(memberRole)))) {
                allowed = new HashSet<>();
            } else {
                allowed = new HashSet<>(cityRoleMap.getOrDefault(nation.getCities(), new HashSet<>()));
            }
            List<Role> roles = new ArrayList<>(member.getRoles());
            for (Role role : roles) {
                if (allowed.contains(role)) {
                    allowed.remove(role);
                    continue;
                }
                Map.Entry<Integer, Integer> cityRole = DiscordUtil.getCityRange(role.getName());
                if (cityRole == null) continue;

                output.accept("Remove " + role.getName() + " from " + member.getEffectiveName());
                tasks.accept(guild.removeRoleFromMember(member, role).submit());
            }

            for (Role role : allowed) {
                output.accept("Add " + role.getName() + " to " + member.getEffectiveName());
                tasks.accept(guild.addRoleToMember(member, role).submit());
            }
        }
    }

    public Role createRole(int position, Guild guild, int allianceId, String allianceName) {
        Random random = new Random(allianceId);
        Color color = null;
        double maxDiff = 0;
        for (int i = 0; i < 100; i++) {
            int nextInt = random.nextInt(0xffffff + 1);
            String colorCode = String.format("#%06x", nextInt);
            Color nextColor = Color.decode(colorCode);

            if (CIEDE2000.calculateDeltaE(BG, nextColor) < 12) continue;

            double minDiff = Double.MAX_VALUE;
            for (Role role : allianceRoles.values()) {
                Color otherColor = role.getColor();
                if (otherColor != null) {
                    minDiff = Math.min(minDiff, CIEDE2000.calculateDeltaE(nextColor, otherColor));
                }
            }
            if (minDiff > maxDiff) {
                maxDiff = minDiff;
                color = nextColor;
            }
            if (minDiff > 12) break;
        }

        String roleName = "AA " + allianceId + " " + allianceName;
        Role role = guild.createRole()
                .setName(roleName)
                .setColor(color)
                .setMentionable(false)
                .setHoisted(true)
                .complete();

        if (role == null) {
            List<Role> roles = guild.getRolesByName(roleName, false);
            if (roles.size() == 1) role = roles.get(0);
            else {
                throw new IllegalStateException("Could not create role: " + roleName);
            }
        }

        if (position != -1) {
            guild.modifyRolePositions().selectPosition(role).moveTo(position).complete();
        }
        return role;
    }

    private static Color BG = Color.decode("#36393E");
}
