package link.locutus.discord.util.task.roles;

import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.util.RateLimitedSources;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntFunction;

public final class AutoRoleRoleDirectory {
    private static final Comparator<Role> ROLE_ORDER = Comparator.comparingLong(Role::getIdLong);
    private static final Comparator<CityKey> CITY_ORDER = Comparator
            .comparingInt(CityKey::start)
            .thenComparingInt(CityKey::end);
    private static final Comparator<TaxKey> TAX_ORDER = Comparator
            .comparingInt(TaxKey::money)
            .thenComparingInt(TaxKey::resources);

    private AutoRoleRoleDirectory() {
    }

    public static Snapshot snapshot(Guild guild) {
        return scan(guild.getRoles());
    }

    static TaskState taskState(Guild guild) {
        Snapshot snapshot = snapshot(guild);
        return new TaskState(
                normalizeAllianceRoles(guild, groupAllianceRoles(snapshot.allianceRoles())),
                buildCityRoleFunc(snapshot.cityRoles()),
                buildCityRoleSet(snapshot.cityRoles()),
                buildTaxRoleMap(snapshot.taxRoles()));
    }

    static Snapshot scan(Collection<Role> roles) {
        Map<Integer, List<Role>> allianceRoles = new TreeMap<>();
        Map<CityKey, List<Role>> cityRoles = new TreeMap<>(CITY_ORDER);
        Map<TaxKey, List<Role>> taxRoles = new TreeMap<>(TAX_ORDER);

        for (Role role : roles) {
            Integer allianceId = ManagedRoleNameParser.parseAllianceId(role);
            if (allianceId != null) {
                allianceRoles.computeIfAbsent(allianceId, ignored -> new ArrayList<>()).add(role);
                continue;
            }

            Map.Entry<Integer, Integer> cityRange = ManagedRoleNameParser.parseCityRange(role);
            if (cityRange != null) {
                CityKey key = new CityKey(cityRange.getKey(), cityRange.getValue());
                cityRoles.computeIfAbsent(key, ignored -> new ArrayList<>()).add(role);
                continue;
            }

            TaxRate taxRate = ManagedRoleNameParser.parseTaxRate(role);
            if (taxRate != null) {
                TaxKey key = new TaxKey(taxRate.money, taxRate.resources);
                taxRoles.computeIfAbsent(key, ignored -> new ArrayList<>()).add(role);
            }
        }

        List<AllianceRole> allianceEntries = new ArrayList<>();
        allianceRoles.forEach((allianceId, matches) -> {
            boolean duplicateKey = matches.size() > 1;
            matches.stream().sorted(ROLE_ORDER)
                    .forEach(role -> allianceEntries.add(new AllianceRole(role, allianceId, duplicateKey)));
        });

        List<CityRole> cityEntries = new ArrayList<>();
        cityRoles.forEach((key, matches) -> {
            boolean duplicateKey = matches.size() > 1;
            matches.stream().sorted(ROLE_ORDER)
                    .forEach(role -> cityEntries.add(new CityRole(role, key.start(), key.end(), duplicateKey)));
        });

        List<TaxRole> taxEntries = new ArrayList<>();
        taxRoles.forEach((key, matches) -> {
            boolean duplicateKey = matches.size() > 1;
            matches.stream().sorted(ROLE_ORDER)
                    .forEach(role -> taxEntries.add(new TaxRole(role, key.money(), key.resources(), duplicateKey)));
        });

        return new Snapshot(allianceEntries, cityEntries, taxEntries);
    }

    public static AllianceRole addAllianceRole(GuildDB db, DBAlliance alliance) {
        if (alliance == null) {
            throw new IllegalArgumentException("Alliance is required.");
        }
        synchronized (db) {
            List<Role> matchingRoles = findAllianceRoles(snapshot(db.getGuild()), alliance.getId());
            Role role = addRole(db, ManagedRoleNameParser.expectedAllianceRoleName(alliance), firstRole(matchingRoles),
                    () -> db.addCoalition(alliance.getId(), Coalition.MASKEDALLIANCES));
            return new AllianceRole(role, alliance.getId(), matchingRoles.size() > 1);
        }
    }

    public static AllianceRole removeAllianceRole(GuildDB db, DBAlliance alliance) {
        if (alliance == null) {
            throw new IllegalArgumentException("Alliance is required.");
        }
        synchronized (db) {
            List<Role> matchingRoles = findAllianceRoles(snapshot(db.getGuild()), alliance.getId());
            String expectedName = ManagedRoleNameParser.expectedAllianceRoleName(alliance);
            Role role = removeRoles(db, matchingRoles,
                    "No alliance autorole exists for `" + expectedName + "`.",
                    () -> db.removeCoalition(alliance.getId(), Coalition.MASKEDALLIANCES));
            return new AllianceRole(role, alliance.getId(), matchingRoles.size() > 1);
        }
    }

    public static CityRole addCityRole(GuildDB db, CityRanges range) {
        Map.Entry<Integer, Integer> cityRange = requireSingleRange(range);
        synchronized (db) {
            List<Role> matchingRoles = findCityRoles(snapshot(db.getGuild()), cityRange.getKey(), cityRange.getValue());
            Role role = addRole(db, DiscordUtil.cityRangeToString(cityRange), firstRole(matchingRoles), null);
            return new CityRole(role, cityRange.getKey(), cityRange.getValue(), matchingRoles.size() > 1);
        }
    }

    public static CityRole removeCityRole(GuildDB db, CityRanges range) {
        Map.Entry<Integer, Integer> cityRange = requireSingleRange(range);
        synchronized (db) {
            List<Role> matchingRoles = findCityRoles(snapshot(db.getGuild()), cityRange.getKey(), cityRange.getValue());
            String expectedName = DiscordUtil.cityRangeToString(cityRange);
            Role role = removeRoles(db, matchingRoles,
                    "No city autorole exists for `" + expectedName + "`.",
                    null);
            return new CityRole(role, cityRange.getKey(), cityRange.getValue(), matchingRoles.size() > 1);
        }
    }

    public static TaxRole addTaxRole(GuildDB db, TaxRate rate) {
        if (rate == null) {
            throw new IllegalArgumentException("Tax rate is required.");
        }
        synchronized (db) {
            List<Role> matchingRoles = findTaxRoles(snapshot(db.getGuild()), rate.money, rate.resources);
            Role role = addRole(db, rate.toString(), firstRole(matchingRoles), null);
            return new TaxRole(role, rate.money, rate.resources, matchingRoles.size() > 1);
        }
    }

    public static TaxRole removeTaxRole(GuildDB db, TaxRate rate) {
        if (rate == null) {
            throw new IllegalArgumentException("Tax rate is required.");
        }
        synchronized (db) {
            List<Role> matchingRoles = findTaxRoles(snapshot(db.getGuild()), rate.money, rate.resources);
            Role role = removeRoles(db, matchingRoles,
                    "No tax autorole exists for `" + rate + "`.",
                    null);
            return new TaxRole(role, rate.money, rate.resources, matchingRoles.size() > 1);
        }
    }

    private static Map<Integer, List<Role>> groupAllianceRoles(List<AllianceRole> allianceRoles) {
        Map<Integer, List<Role>> groupedRoles = new LinkedHashMap<>();
        for (AllianceRole allianceRole : allianceRoles) {
            groupedRoles.computeIfAbsent(allianceRole.allianceId(), ignored -> new ArrayList<>()).add(allianceRole.role());
        }
        return groupedRoles;
    }

    private static IntFunction<Set<Role>> buildCityRoleFunc(List<CityRole> cityRoles) {
        Objects.requireNonNull(cityRoles, "cityRoles");

        if (cityRoles.isEmpty()) {
            return ignored -> Set.of();
        }

        final int inputSize = cityRoles.size();

        // Map each distinct Role -> dense index [0..roleCount)
        final Map<Role, Integer> roleToIndex = new HashMap<>(hashCapacity(inputSize));
        final ArrayList<Role> indexToRole = new ArrayList<>();

        int eventCount = 0;
        for (int i = 0; i < inputSize; i++) {
            final CityRole cr = Objects.requireNonNull(cityRoles.get(i), "cityRoles[" + i + "]");
            final Role role = Objects.requireNonNull(cr.role(), "cityRoles[" + i + "].role");

            if (cr.rangeStart() > cr.rangeEnd()) {
                throw new IllegalArgumentException("rangeStart > rangeEnd: " + cr);
            }

            // duplicateKey is intentionally ignored:
            // duplicates/overlaps are handled by reference counts during the sweep.
            Integer idx = roleToIndex.get(role);
            if (idx == null) {
                idx = indexToRole.size();
                roleToIndex.put(role, idx);
                indexToRole.add(role);
            }

            eventCount += (cr.rangeEnd() == Integer.MAX_VALUE) ? 1 : 2;
        }

        final int roleCount = indexToRole.size();
        final Role[] roles = indexToRole.toArray(new Role[0]);

        // Pack events into primitive longs:
        // high 32 bits = position
        // low bits     = roleIndex + add/remove flag
        final long[] events = new long[eventCount];
        int e = 0;

        for (int i = 0; i < inputSize; i++) {
            final CityRole cr = cityRoles.get(i);
            final int roleIndex = roleToIndex.get(cr.role());

            // inclusive start
            events[e++] = packEvent(cr.rangeStart(), roleIndex, true);

            // inclusive end => remove at end + 1
            if (cr.rangeEnd() != Integer.MAX_VALUE) {
                events[e++] = packEvent(cr.rangeEnd() + 1, roleIndex, false);
            }
        }

        Arrays.sort(events);

        final int[] counts = new int[roleCount];
        final int[] deltaAtPos = new int[roleCount];
        final int[] touched = new int[roleCount];

        // Max possible number of segments is <= eventCount
        int[] starts = new int[eventCount];
        int[] ends = new int[eventCount];
        @SuppressWarnings("unchecked")
        Set<Role>[] roleSets = (Set<Role>[]) new Set<?>[eventCount];
        int segmentCount = 0;

        boolean open = false;
        int currentStart = 0;
        Set<Role> currentRoles = Set.of();

        if (roleCount <= 64) {
            // Fast path: active role membership tracked in a single long
            long activeMask = 0L;
            final Map<Long, Set<Role>> maskCache = new HashMap<>();

            int i = 0;
            while (i < events.length) {
                final int pos = unpackPos(events[i]);
                int touchedSize = 0;

                do {
                    final long ev = events[i++];
                    final int idx = unpackRoleIndex(ev);

                    if (deltaAtPos[idx] == 0) {
                        touched[touchedSize++] = idx;
                    }
                    deltaAtPos[idx] += isAdd(ev) ? 1 : -1;
                } while (i < events.length && unpackPos(events[i]) == pos);

                boolean membershipChanged = false;

                for (int t = 0; t < touchedSize; t++) {
                    final int idx = touched[t];
                    final int before = counts[idx];
                    final int after = before + deltaAtPos[idx];

                    if (after < 0) {
                        throw new IllegalStateException("Negative active count for role " + roles[idx]);
                    }

                    if ((before == 0) != (after == 0)) {
                        membershipChanged = true;
                        activeMask ^= (1L << idx);
                    }

                    counts[idx] = after;
                    deltaAtPos[idx] = 0;
                }

                if (membershipChanged) {
                    if (open) {
                        starts[segmentCount] = currentStart;
                        ends[segmentCount] = pos - 1;
                        roleSets[segmentCount] = currentRoles;
                        segmentCount++;
                        open = false;
                    }

                    if (activeMask != 0L) {
                        currentStart = pos;

                        Set<Role> snapshot = maskCache.get(activeMask);
                        if (snapshot == null) {
                            snapshot = buildImmutableSetFromMask(activeMask, roles);
                            maskCache.put(activeMask, snapshot);
                        }

                        currentRoles = snapshot;
                        open = true;
                    }
                }
            }

        } else {
            // Generic path for many distinct roles
            final HashSet<Role> active = new HashSet<>(hashCapacity(roleCount));

            int i = 0;
            while (i < events.length) {
                final int pos = unpackPos(events[i]);
                int touchedSize = 0;

                do {
                    final long ev = events[i++];
                    final int idx = unpackRoleIndex(ev);

                    if (deltaAtPos[idx] == 0) {
                        touched[touchedSize++] = idx;
                    }
                    deltaAtPos[idx] += isAdd(ev) ? 1 : -1;
                } while (i < events.length && unpackPos(events[i]) == pos);

                boolean membershipChanged = false;

                for (int t = 0; t < touchedSize; t++) {
                    final int idx = touched[t];
                    final int before = counts[idx];
                    final int after = before + deltaAtPos[idx];

                    if (after < 0) {
                        throw new IllegalStateException("Negative active count for role " + roles[idx]);
                    }

                    if ((before == 0) != (after == 0)) {
                        membershipChanged = true;
                        if (after == 0) {
                            active.remove(roles[idx]);
                        } else {
                            active.add(roles[idx]);
                        }
                    }

                    counts[idx] = after;
                    deltaAtPos[idx] = 0;
                }

                if (membershipChanged) {
                    if (open) {
                        starts[segmentCount] = currentStart;
                        ends[segmentCount] = pos - 1;
                        roleSets[segmentCount] = currentRoles;
                        segmentCount++;
                        open = false;
                    }

                    if (!active.isEmpty()) {
                        currentStart = pos;
                        currentRoles = Set.copyOf(active);
                        open = true;
                    }
                }
            }
        }

        if (open) {
            starts[segmentCount] = currentStart;
            ends[segmentCount] = Integer.MAX_VALUE;
            roleSets[segmentCount] = currentRoles;
            segmentCount++;
        }

        if (segmentCount == 0) {
            return ignored -> Set.of();
        }

        final int[] finalStarts = Arrays.copyOf(starts, segmentCount);
        final int[] finalEnds = Arrays.copyOf(ends, segmentCount);
        final Set<Role>[] finalRoleSets = Arrays.copyOf(roleSets, segmentCount);
        final Set<Role> empty = Set.of();

        return city -> {
            int idx = Arrays.binarySearch(finalStarts, city);
            if (idx < 0) {
                idx = -idx - 2;
            }
            return (idx >= 0 && city <= finalEnds[idx]) ? finalRoleSets[idx] : empty;
        };
    }

    private static Set<Role> buildImmutableSetFromMask(long mask, Role[] roles) {
        final int size = Long.bitCount(mask);

        switch (size) {
            case 0:
                return Set.of();
            case 1: {
                final int i0 = Long.numberOfTrailingZeros(mask);
                return Set.of(roles[i0]);
            }
            case 2: {
                final int i0 = Long.numberOfTrailingZeros(mask);
                mask &= (mask - 1);
                final int i1 = Long.numberOfTrailingZeros(mask);
                return Set.of(roles[i0], roles[i1]);
            }
            default: {
                final HashSet<Role> set = new HashSet<>(hashCapacity(size));
                while (mask != 0L) {
                    final int idx = Long.numberOfTrailingZeros(mask);
                    set.add(roles[idx]);
                    mask &= (mask - 1);
                }
                return Set.copyOf(set);
            }
        }
    }

    private static int hashCapacity(int expectedSize) {
        return Math.max(16, (int) (expectedSize / 0.75f) + 1);
    }

    private static long packEvent(int pos, int roleIndex, boolean add) {
        return (((long) pos) << 32)
                | (((long) roleIndex) << 1)
                | (add ? 1L : 0L);
    }

    private static int unpackPos(long event) {
        return (int) (event >> 32);
    }

    private static int unpackRoleIndex(long event) {
        return (int) ((event >>> 1) & 0x7FFF_FFFFL);
    }

    private static boolean isAdd(long event) {
        return (event & 1L) != 0;
    }

    private static Set<Role> buildCityRoleSet(List<CityRole> cityRoles) {
        LinkedHashSet<Role> roles = new LinkedHashSet<>();
        cityRoles.stream().map(CityRole::role).forEach(roles::add);
        return roles;
    }

    private static Map<TaxRoleKey, Role> buildTaxRoleMap(List<TaxRole> taxRoles) {
        Map<TaxRoleKey, Role> taxRoleMap = new LinkedHashMap<>();
        for (TaxRole taxRole : taxRoles) {
            taxRoleMap.putIfAbsent(new TaxRoleKey(taxRole.moneyRate(), taxRole.rssRate()), taxRole.role());
        }
        return taxRoleMap;
    }

    private static Map<Integer, Role> normalizeAllianceRoles(Guild guild, Map<Integer, List<Role>> allianceRoleGroups) {
        Map<Integer, Role> normalizedAllianceRoles = new LinkedHashMap<>();
        if (allianceRoleGroups.isEmpty()) {
            return normalizedAllianceRoles;
        }

        Set<Role> duplicateRoles = new HashSet<>();
        for (List<Role> roles : allianceRoleGroups.values()) {
            if (roles.size() > 1) {
                duplicateRoles.addAll(roles);
            }
        }

        Map<Role, Integer> memberCounts = new HashMap<>();
        Map<Role, List<Member>> membersByRole = new HashMap<>();
        if (!duplicateRoles.isEmpty()) {
            for (Member member : guild.getMembers()) {
                for (Role role : member.getUnsortedRoles()) {
                    if (!duplicateRoles.contains(role)) {
                        continue;
                    }
                    memberCounts.put(role, memberCounts.getOrDefault(role, 0) + 1);
                    membersByRole.computeIfAbsent(role, ignored -> new ArrayList<>()).add(member);
                }
            }
        }

        Map<Member, Set<Role>> rolesToAdd = new LinkedHashMap<>();
        Map<Member, Set<Role>> rolesToRemove = new LinkedHashMap<>();
        List<Role> duplicateRolesToDelete = new ArrayList<>();

        for (Map.Entry<Integer, List<Role>> entry : allianceRoleGroups.entrySet()) {
            int allianceId = entry.getKey();
            List<Role> roles = entry.getValue();
            String expectedName = ManagedRoleNameParser.expectedAllianceRoleName(allianceId);
            Role canonicalRole = selectPreferredAllianceRole(roles, expectedName, memberCounts);
            if (canonicalRole == null) {
                continue;
            }

            normalizedAllianceRoles.put(allianceId, canonicalRole);
            renameAllianceRoleIfNeeded(guild, canonicalRole, expectedName);

            if (roles.size() == 1) {
                continue;
            }

            for (Role duplicateRole : roles) {
                if (duplicateRole.getIdLong() == canonicalRole.getIdLong()) {
                    continue;
                }
                if (!canManageAllianceRole(guild, canonicalRole) || !canManageAllianceRole(guild, duplicateRole)) {
                    continue;
                }
                List<Member> duplicateMembers = membersByRole.getOrDefault(duplicateRole, Collections.emptyList());
                for (Member duplicateMember : duplicateMembers) {
                    if (!duplicateMember.getUnsortedRoles().contains(canonicalRole)) {
                        rolesToAdd.computeIfAbsent(duplicateMember, ignored -> new LinkedHashSet<>()).add(canonicalRole);
                    }
                    rolesToRemove.computeIfAbsent(duplicateMember, ignored -> new LinkedHashSet<>()).add(duplicateRole);
                }
                duplicateRolesToDelete.add(duplicateRole);
            }
        }

        applyAllianceRoleMemberUpdates(guild, rolesToAdd, rolesToRemove);
        deleteDuplicateAllianceRoles(duplicateRolesToDelete);
        return normalizedAllianceRoles;
    }

    static Role selectPreferredAllianceRole(Collection<Role> roles, String expectedName, Map<Role, Integer> memberCounts) {
        Role preferred = null;
        for (Role candidate : roles) {
            if (preferred == null || isPreferredAllianceRole(candidate, preferred, expectedName, memberCounts)) {
                preferred = candidate;
            }
        }
        return preferred;
    }

    private static boolean isPreferredAllianceRole(Role candidate, Role current, String expectedName,
            Map<Role, Integer> memberCounts) {
        int candidateCount = memberCounts.getOrDefault(candidate, 0);
        int currentCount = memberCounts.getOrDefault(current, 0);
        if (candidateCount != currentCount) {
            return candidateCount > currentCount;
        }

        boolean candidateMatches = roleMatchesExpectedName(candidate, expectedName);
        boolean currentMatches = roleMatchesExpectedName(current, expectedName);
        if (candidateMatches != currentMatches) {
            return candidateMatches;
        }

        return candidate.getIdLong() < current.getIdLong();
    }

    private static void renameAllianceRoleIfNeeded(Guild guild, Role role, String expectedName) {
        if (role == null || expectedName == null || roleMatchesExpectedName(role, expectedName)
                || !canManageAllianceRole(guild, role)) {
            return;
        }
        try {
            RateLimitUtil.complete(role.getManager().setName(expectedName), RateLimitedSources.DB_NATION_ROLE_ASSIGN);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    private static void applyAllianceRoleMemberUpdates(Guild guild, Map<Member, Set<Role>> rolesToAdd,
            Map<Member, Set<Role>> rolesToRemove) {
        if (rolesToAdd.isEmpty() && rolesToRemove.isEmpty()) {
            return;
        }

        Set<Member> touchedMembers = new LinkedHashSet<>();
        touchedMembers.addAll(rolesToAdd.keySet());
        touchedMembers.addAll(rolesToRemove.keySet());
        for (Member member : touchedMembers) {
            if (member == null) {
                continue;
            }
            List<Role> add = new ArrayList<>(rolesToAdd.getOrDefault(member, Collections.emptySet()));
            List<Role> remove = new ArrayList<>(rolesToRemove.getOrDefault(member, Collections.emptySet()));
            if (add.isEmpty() && remove.isEmpty()) {
                continue;
            }
            try {
                RateLimitUtil.complete(guild.modifyMemberRoles(member, add, remove), RateLimitedSources.DB_NATION_ROLE_ASSIGN);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
    }

    private static void deleteDuplicateAllianceRoles(List<Role> duplicateRolesToDelete) {
        for (Role duplicateRole : duplicateRolesToDelete) {
            try {
                RateLimitUtil.complete(duplicateRole.delete(), RateLimitedSources.DB_NATION_ROLE_ASSIGN);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
    }

    private static boolean canManageAllianceRole(Guild guild, Role role) {
        return role != null && !role.isManaged() && guild.getSelfMember().canInteract(role);
    }

    private static boolean roleMatchesExpectedName(Role role, String expectedName) {
        return expectedName != null && role.getName().equalsIgnoreCase(expectedName);
    }

    private static Role addRole(GuildDB db, String roleName, Role existingRole, Runnable afterMutation) {
        Role role = existingRole == null ? createRole(db.getGuild(), roleName) : existingRole;
        if (afterMutation != null) {
            afterMutation.run();
        }
        refreshAutoRole(db);
        return role;
    }

    private static Role removeRoles(GuildDB db, List<Role> matchingRoles, String missingMessage, Runnable afterMutation) {
        if (matchingRoles.isEmpty()) {
            throw new IllegalArgumentException(missingMessage);
        }

        Guild guild = db.getGuild();
        List<Role> sortedRoles = matchingRoles.stream().sorted(ROLE_ORDER).toList();
        IllegalArgumentException blockedDelete = null;

        for (Role role : sortedRoles) {
            try {
                validateRoleDeletion(guild, role);
            } catch (IllegalArgumentException e) {
                if (blockedDelete == null) {
                    blockedDelete = e;
                }
                continue;
            }

            try {
                RateLimitUtil.complete(role.delete(), RateLimitedSources.DB_NATION_ROLE_ASSIGN);
            } catch (Throwable throwable) {
                throw actionError("delete", role.getName(), throwable);
            }

            if (afterMutation != null && sortedRoles.size() == 1) {
                afterMutation.run();
            }
            refreshAutoRole(db);
            return role;
        }

        throw blockedDelete == null ? new IllegalArgumentException(missingMessage) : blockedDelete;
    }

    private static Role createRole(Guild guild, String roleName) {
        try {
            return RateLimitUtil.complete(guild.createRole()
                    .setName(roleName)
                    .setMentionable(false)
                    .setHoisted(true), RateLimitedSources.DB_NATION_ROLE_ASSIGN);
        } catch (Throwable throwable) {
            throw actionError("create", roleName, throwable);
        }
    }

    private static void validateRoleDeletion(Guild guild, Role role) {
        if (role.getIdLong() == guild.getPublicRole().getIdLong()) {
            throw new IllegalArgumentException("The public role cannot be managed by this endpoint.");
        }
        if (role.isManaged()) {
            throw new IllegalArgumentException("Managed integration roles cannot be deleted here.");
        }
        if (!guild.getSelfMember().canInteract(role)) {
            throw new IllegalArgumentException("The bot cannot interact with role `" + role.getName()
                    + "`. Move the bot role above it first.");
        }
        List<Member> members = guild.getMembersWithRoles(role);
        if (!members.isEmpty()) {
            throw new IllegalArgumentException("Role `" + role.getName() + "` still has " + members.size()
                    + " assigned members. Remove those assignments first.");
        }
    }

    private static List<Role> findAllianceRoles(Snapshot snapshot, int allianceId) {
        return snapshot.allianceRoles().stream()
                .filter(entry -> entry.allianceId() == allianceId)
                .map(AllianceRole::role)
                .toList();
    }

    private static List<Role> findCityRoles(Snapshot snapshot, int start, int end) {
        return snapshot.cityRoles().stream()
                .filter(entry -> entry.rangeStart() == start && entry.rangeEnd() == end)
                .map(CityRole::role)
                .toList();
    }

    private static List<Role> findTaxRoles(Snapshot snapshot, int money, int resources) {
        return snapshot.taxRoles().stream()
                .filter(entry -> entry.moneyRate() == money && entry.rssRate() == resources)
                .map(TaxRole::role)
                .toList();
    }

    private static Role firstRole(List<Role> roles) {
        return roles.isEmpty() ? null : roles.get(0);
    }

    private static Map.Entry<Integer, Integer> requireSingleRange(CityRanges range) {
        if (range == null || range.getRanges().isEmpty()) {
            throw new IllegalArgumentException("City range is required.");
        }
        if (range.getRanges().size() != 1) {
            throw new IllegalArgumentException("City roles support exactly one range per Discord role.");
        }
        return range.getRanges().get(0);
    }

    private static void refreshAutoRole(GuildDB db) {
        db.getAutoRoleTask().syncDB();
    }

    private static IllegalArgumentException actionError(String action, String target, Throwable throwable) {
        Throwable root = unwrap(throwable);
        String detail = root.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = root.getClass().getSimpleName();
        }
        return new IllegalArgumentException("Failed to " + action + " role `" + target + "`: " + detail, root);
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    public record Snapshot(List<AllianceRole> allianceRoles, List<CityRole> cityRoles, List<TaxRole> taxRoles) {
        public Snapshot {
            allianceRoles = List.copyOf(allianceRoles);
            cityRoles = List.copyOf(cityRoles);
            taxRoles = List.copyOf(taxRoles);
        }
    }

    record TaskState(Map<Integer, Role> allianceRoles, IntFunction<Set<Role>> cityRoleMap, Set<Role> cityRoles,
                     Map<TaxRoleKey, Role> taxRoles) {
        public TaskState {
            allianceRoles = Collections.unmodifiableMap(new LinkedHashMap<>(allianceRoles));
            cityRoles = Collections.unmodifiableSet(new LinkedHashSet<>(cityRoles));
            taxRoles = Collections.unmodifiableMap(new LinkedHashMap<>(taxRoles));
        }
    }

    public record AllianceRole(Role role, int allianceId, boolean duplicateKey) {
    }

    public record CityRole(Role role, int rangeStart, int rangeEnd, boolean duplicateKey) {
    }

    public record TaxRole(Role role, int moneyRate, int rssRate, boolean duplicateKey) {
    }

    public record TaxRoleKey(int moneyRate, int rssRate) {
    }

    private record CityKey(int start, int end) {
    }

    private record TaxKey(int money, int resources) {
    }
}
