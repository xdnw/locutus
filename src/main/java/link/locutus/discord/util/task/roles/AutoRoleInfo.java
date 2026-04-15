package link.locutus.discord.util.task.roles;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.util.RateLimitedSources;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.CIEDE2000;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.requests.restaction.RoleAction;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public class AutoRoleInfo {

    private final Map<Integer, RoleOrCreate> createMap;
    private final Map<Member, Set<RoleAdd>> addRoles;
    private final Map<Member, Set<Role>> removeRoles;
    private final Map<Role, String> renameRoles;
    private final Map<Member, NicknameChange> nicknameChanges;
    private final GuildDB db;
    @Nullable private final AutoRoleSyncState syncState;
    private final Map<Member, List<AutoRoleIssue>> issues;
    private final Map<Member, List<AutoRoleIssue>> executionIssues;
    private final Map<Member, MemberExecution> memberExecutions;
    private final Map<Long, String> createdRoles;
    private final Map<Long, String> renamedRoles;

    public AutoRoleInfo(GuildDB db) {
        this(db, null);
    }

    public AutoRoleInfo(GuildDB db, @Nullable AutoRoleSyncState syncState) {
        this.db = db;
        this.syncState = syncState;
        this.createMap = new LinkedHashMap<>();
        this.addRoles = new LinkedHashMap<>();
        this.removeRoles = new LinkedHashMap<>();
        this.renameRoles = new LinkedHashMap<>();
        this.nicknameChanges = new LinkedHashMap<>();
        this.issues = new LinkedHashMap<>();
        this.executionIssues = new LinkedHashMap<>();
        this.memberExecutions = new LinkedHashMap<>();
        this.createdRoles = new LinkedHashMap<>();
        this.renamedRoles = new LinkedHashMap<>();
    }

    public GuildDB getDb() {
        return db;
    }

    @Nullable
    public AutoRoleSyncState getSyncState() {
        return syncState;
    }

    public RoleOrCreate createAllianceRole(int allianceId, String roleName, int position, Supplier<Color> color) {
        return createMap.computeIfAbsent(allianceId, key -> new RoleOrCreate(null, allianceId, roleName, position, color));
    }

    public void renameRole(Role role, String newName) {
        if (role == null || newName == null || newName.isEmpty()) {
            return;
        }
        renameRoles.put(role, newName);
    }

    public void addIssue(Member member, AutoRoleIssueType type) {
        addIssue(member, issue(type, null, null, null, null, null));
    }

    public void addIssue(Member member, AutoRoleIssueType type, @Nullable Long roleId, @Nullable Integer allianceId,
            @Nullable String nickname, @Nullable String errorType, @Nullable String detail) {
        addIssue(member, issue(type, roleId, allianceId, nickname, errorType, detail));
    }

    public void addIssue(Member member, AutoRoleIssue issue) {
        issues.computeIfAbsent(member, key -> new ArrayList<>()).add(issue);
    }

    public List<Integer> getCreateAllianceIds() {
        return createMap.keySet().stream().sorted().toList();
    }

    public List<Integer> getCreateAllianceIds(Member member) {
        Set<RoleAdd> plannedRoles = addRoles.get(member);
        if (plannedRoles == null || plannedRoles.isEmpty()) {
            return Collections.emptyList();
        }
        return plannedRoles.stream()
                .map(RoleAdd::getAllianceId)
                .filter(id -> id != null)
                .map(Integer.class::cast)
                .distinct()
                .sorted()
                .toList();
    }

    public List<Long> getAddRoleIds(Member member) {
        Set<RoleAdd> plannedRoles = addRoles.get(member);
        if (plannedRoles == null || plannedRoles.isEmpty()) {
            return Collections.emptyList();
        }
        return plannedRoles.stream()
                .map(RoleAdd::getRoleIdOrNull)
                .filter(id -> id != null)
                .map(Long.class::cast)
                .distinct()
                .sorted()
                .toList();
    }

    public List<Long> getRemoveRoleIds(Member member) {
        Set<Role> plannedRoles = removeRoles.get(member);
        if (plannedRoles == null || plannedRoles.isEmpty()) {
            return Collections.emptyList();
        }
        return plannedRoles.stream()
                .map(Role::getIdLong)
                .distinct()
                .sorted()
                .toList();
    }

    public Map<Long, String> getRenameRoleMap() {
        Map<Long, String> result = new LinkedHashMap<>();
        renameRoles.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((left, right) -> Long.compare(left.getIdLong(), right.getIdLong())))
                .forEach(entry -> result.put(entry.getKey().getIdLong(), entry.getValue()));
        return result;
    }

    @Nullable
    public String getNickname(Member member) {
        NicknameChange change = nicknameChanges.get(member);
        return change == null ? null : change.nickname;
    }

    public boolean isClearNickname(Member member) {
        NicknameChange change = nicknameChanges.get(member);
        return change != null && change.clear;
    }

    public List<AutoRoleIssue> getIssues(Member member) {
        return copyIssues(issues.get(member));
    }

    public List<AutoRoleIssue> getExecutionIssues(Member member) {
        return copyIssues(executionIssues.get(member));
    }

    public List<AutoRoleIssue> getGlobalExecutionIssues() {
        return getExecutionIssues(null);
    }

    public List<Member> getPlannedMembers() {
        LinkedHashSet<Member> members = new LinkedHashSet<>();
        members.addAll(addRoles.keySet());
        members.addAll(removeRoles.keySet());
        members.addAll(nicknameChanges.keySet());
        for (Member member : issues.keySet()) {
            if (member != null) {
                members.add(member);
            }
        }
        return new ArrayList<>(members);
    }

    public List<Long> getCreatedRoleIds() {
        return createdRoles.keySet().stream().sorted().toList();
    }

    public Map<Long, String> getCreatedRoleNames() {
        return new LinkedHashMap<>(createdRoles);
    }

    public Map<Long, String> getRenamedRoles() {
        return new LinkedHashMap<>(renamedRoles);
    }

    public List<Long> getAddedRoleIds(Member member) {
        MemberExecution execution = memberExecutions.get(member);
        if (execution == null || execution.addedRoleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return execution.addedRoleIds.stream().sorted().toList();
    }

    public List<Long> getRemovedRoleIds(Member member) {
        MemberExecution execution = memberExecutions.get(member);
        if (execution == null || execution.removedRoleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return execution.removedRoleIds.stream().sorted().toList();
    }

    @Nullable
    public String getAppliedNickname(Member member) {
        MemberExecution execution = memberExecutions.get(member);
        return execution == null ? null : execution.appliedNickname;
    }

    public boolean wasNicknameCleared(Member member) {
        MemberExecution execution = memberExecutions.get(member);
        return execution != null && execution.clearedNickname;
    }

    public void execute() {
        executionIssues.clear();
        memberExecutions.clear();
        createdRoles.clear();
        renamedRoles.clear();

        List<CompletableFuture<?>> tasks = new ObjectArrayList<>();
        for (Map.Entry<Member, Set<RoleAdd>> entry : addRoles.entrySet()) {
            Member member = entry.getKey();
            for (RoleAdd roleAdd : entry.getValue()) {
                tasks.add(roleAdd.submit(db.getGuild()).thenAccept(success -> {
                    if (!success) {
                        addExecutionIssue(member, roleAdd.getFailureIssue());
                        return;
                    }
                    Role resolvedRole = roleAdd.getResolvedRole();
                    if (resolvedRole != null) {
                        if (roleAdd.wasCreatedRole()) {
                            recordCreatedRole(resolvedRole);
                        }
                        recordAddedRole(member, resolvedRole);
                    }
                }));
            }
        }

        for (Map.Entry<Member, Set<Role>> entry : removeRoles.entrySet()) {
            Member member = entry.getKey();
            for (Role role : entry.getValue()) {
                if (!member.getUnsortedRoles().contains(role)) {
                    addExecutionIssue(member, issue(AutoRoleIssueType.ROLE_NOT_PRESENT, role.getIdLong(), null, null, null, null));
                    continue;
                }
                try {
                    Role roleToRemove = java.util.Objects.requireNonNull(role);
                    tasks.add(RateLimitUtil.queue(db.getGuild().removeRoleFromMember(member, roleToRemove), RateLimitedSources.DB_NATION_ROLE_ASSIGN).thenAccept(v -> recordRemovedRole(member, roleToRemove)));
                } catch (PermissionException e) {
                    addExecutionIssue(member, issue(AutoRoleIssueType.REMOVE_ROLE_FAILED, role.getIdLong(), null, null,
                            e.getClass().getSimpleName(), e.getMessage()));
                }
            }
        }

        for (Map.Entry<Role, String> entry : renameRoles.entrySet()) {
            Role role = entry.getKey();
            String newName = entry.getValue();
            if (role.getName().equalsIgnoreCase(newName)) {
                addExecutionIssue(null, issue(AutoRoleIssueType.ROLE_ALREADY_NAMED, role.getIdLong(), null, null, null, null));
                continue;
            }
            try {
                Role roleToRename = role;
                String targetName = java.util.Objects.requireNonNull(newName);
                tasks.add(RateLimitUtil.queue(roleToRename.getManager().setName(targetName), RateLimitedSources.DB_NATION_ROLE_ASSIGN).thenAccept(v -> recordRenamedRole(roleToRename, targetName)));
            } catch (PermissionException e) {
                addExecutionIssue(null, issue(AutoRoleIssueType.RENAME_ROLE_FAILED, role.getIdLong(), null, null,
                        e.getClass().getSimpleName(), e.getMessage()));
            }
        }

        for (Map.Entry<Member, NicknameChange> entry : nicknameChanges.entrySet()) {
            Member member = entry.getKey();
            NicknameChange nickname = entry.getValue();
            if (nickname.clear) {
                if (member.getNickname() == null) {
                    addExecutionIssue(member, issue(AutoRoleIssueType.NICKNAME_NOT_PRESENT, null, null, null, null, null));
                    continue;
                }
                try {
                    tasks.add(RateLimitUtil.queue(db.getGuild().modifyNickname(member, null), RateLimitedSources.DB_NATION_ROLE_ASSIGN).thenAccept(v -> recordClearedNickname(member)));
                } catch (PermissionException e) {
                    addExecutionIssue(member, issue(AutoRoleIssueType.CLEAR_NICKNAME_FAILED, null, null, null,
                            e.getClass().getSimpleName(), e.getMessage()));
                }
            } else {
                String currentNickname = member.getNickname();
                if (currentNickname != null && currentNickname.equals(nickname.nickname)) {
                    addExecutionIssue(member, issue(AutoRoleIssueType.NICKNAME_ALREADY_SET, null, null, nickname.nickname, null, null));
                    continue;
                }
                try {
                    tasks.add(RateLimitUtil.queue(db.getGuild().modifyNickname(member, nickname.nickname), RateLimitedSources.DB_NATION_ROLE_ASSIGN)
                            .thenAccept(v -> recordAppliedNickname(member, nickname.nickname)));
                } catch (PermissionException e) {
                    addExecutionIssue(member, issue(AutoRoleIssueType.SET_NICKNAME_FAILED, null, null, nickname.nickname,
                            e.getClass().getSimpleName(), e.getMessage()));
                }
            }
        }

        for (CompletableFuture<?> task : tasks) {
            try {
                task.get();
            } catch (InterruptedException | ExecutionException e) {
                addExecutionIssue(null, issue(AutoRoleIssueType.PLANNING_FAILED, null, null, null,
                        e.getClass().getSimpleName(), e.getMessage()));
            }
        }

        createMap.clear();
        addRoles.clear();
        removeRoles.clear();
        nicknameChanges.clear();
    }

    public void addRoleToMember(Member member, RoleOrCreate create) {
        Role cachedRole = create.getCachedOrNull();
        if (cachedRole != null && member.getUnsortedRoles().contains(cachedRole)) {
            return;
        }
        removeConflictingRemoval(member, create);
        Set<RoleAdd> plannedAdds = addRoles.computeIfAbsent(member, key -> new LinkedHashSet<>());
        for (RoleAdd plannedAdd : plannedAdds) {
            if (plannedAdd.matches(create)) {
                return;
            }
        }
        plannedAdds.add(new RoleAdd(member, create));
    }

    public void addRoleToMember(Member member, Role role) {
        addRoleToMember(member, new RoleOrCreate(role, null, role.getName(), -1, () -> role.getColors().getPrimary()));
    }

    public void removeRoleFromMember(Member member, Role role) {
        removeConflictingAdd(member, role);
        removeRoles.computeIfAbsent(member, key -> new LinkedHashSet<>()).add(role);
    }

    public void modifyNickname(Member member, @Nullable String name) {
        nicknameChanges.put(member, new NicknameChange(name));
    }

    private static AutoRoleIssue issue(AutoRoleIssueType type, @Nullable Long roleId, @Nullable Integer allianceId,
            @Nullable String nickname, @Nullable String errorType, @Nullable String detail) {
        AutoRoleIssue issue = new AutoRoleIssue(type);
        issue.role_id = roleId;
        issue.alliance_id = allianceId;
        issue.nickname = nickname;
        issue.error_type = StringMan.stripApiKey(errorType);
        issue.detail = detail;
        return issue;
    }

    private static List<AutoRoleIssue> copyIssues(@Nullable List<AutoRoleIssue> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(values);
    }

    private synchronized void addExecutionIssue(@Nullable Member member, @Nullable AutoRoleIssue issue) {
        if (issue == null) {
            return;
        }
        executionIssues.computeIfAbsent(member, key -> new ArrayList<>()).add(issue);
    }

    private synchronized MemberExecution execution(Member member) {
        return memberExecutions.computeIfAbsent(member, key -> new MemberExecution());
    }

    private synchronized void recordCreatedRole(Role role) {
        createdRoles.putIfAbsent(role.getIdLong(), role.getName());
    }

    private synchronized void recordRenamedRole(Role role, String newName) {
        renamedRoles.put(role.getIdLong(), newName);
    }

    private synchronized void recordAddedRole(Member member, Role role) {
        execution(member).addedRoleIds.add(role.getIdLong());
    }

    private synchronized void recordRemovedRole(Member member, Role role) {
        execution(member).removedRoleIds.add(role.getIdLong());
    }

    private synchronized void recordAppliedNickname(Member member, String nickname) {
        execution(member).appliedNickname = nickname;
    }

    private synchronized void recordClearedNickname(Member member) {
        execution(member).clearedNickname = true;
    }

    private void removeConflictingRemoval(Member member, RoleOrCreate create) {
        Set<Role> plannedRemovals = removeRoles.get(member);
        if (plannedRemovals == null || plannedRemovals.isEmpty()) {
            return;
        }
        plannedRemovals.removeIf(role -> matches(create, role));
        if (plannedRemovals.isEmpty()) {
            removeRoles.remove(member);
        }
    }

    private void removeConflictingAdd(Member member, Role role) {
        Set<RoleAdd> plannedAdds = addRoles.get(member);
        if (plannedAdds == null || plannedAdds.isEmpty()) {
            return;
        }
        plannedAdds.removeIf(add -> add.matches(role));
        if (plannedAdds.isEmpty()) {
            addRoles.remove(member);
        }
    }

    private static boolean matches(RoleOrCreate create, Role role) {
        Long createRoleId = create.getRoleIdOrNull();
        return createRoleId != null && createRoleId == role.getIdLong();
    }

    public static class RoleAdd {
        private final Member member;
        private final RoleOrCreate role;

        private boolean success;
        @Nullable private AutoRoleIssue failureIssue;
        @Nullable private Role resolvedRole;
        private boolean createdRole;
        @Nullable private CompletableFuture<Boolean> future;

        public RoleAdd(Member member, RoleOrCreate role) {
            this.member = member;
            this.role = role;
        }

        public CompletableFuture<Boolean> submit(Guild guild) {
            if (future != null) {
                return future;
            }
            this.future = this.role.submit(guild).thenApply(role -> {
                resolvedRole = role;
                if (role == null) {
                    success = false;
                    failureIssue = issue(AutoRoleIssueType.CREATE_ROLE_FAILED, null, this.role.getAllianceId(), null,
                            this.role.getFailedCreateErrorType(), this.role.getFailedCreateDetail());
                    return false;
                }
                if (member.getUnsortedRoles().contains(role)) {
                    success = false;
                    failureIssue = issue(AutoRoleIssueType.ROLE_ALREADY_PRESENT, role.getIdLong(), null, null, null, null);
                    return false;
                }
                createdRole = this.role.wasCreatedNewRole();
                RateLimitUtil.queue(guild.addRoleToMember(member.getUser(), role), RateLimitedSources.DB_NATION_ROLE_ASSIGN).thenAccept(v -> success = true).exceptionally(throwable -> {
                    Throwable error = unwrap(throwable);
                    success = false;
                    failureIssue = issue(AutoRoleIssueType.ADD_ROLE_FAILED, role.getIdLong(), null, null,
                            error.getClass().getSimpleName(), error.getMessage());
                    return null;
                }).join();
                return success;
            });
            return future;
        }

        @Nullable
        public Long getRoleIdOrNull() {
            return role.getRoleIdOrNull();
        }

        @Nullable
        public Integer getAllianceId() {
            return role.getAllianceId();
        }

        @Nullable
        public AutoRoleIssue getFailureIssue() {
            return failureIssue;
        }

        @Nullable
        public Role getResolvedRole() {
            return resolvedRole;
        }

        public boolean wasCreatedRole() {
            return createdRole;
        }

        public boolean matches(Role role) {
            return AutoRoleInfo.matches(this.role, role);
        }

        public boolean matches(RoleOrCreate other) {
            Long roleId = role.getRoleIdOrNull();
            Long otherRoleId = other.getRoleIdOrNull();
            if (roleId != null && otherRoleId != null) {
                return roleId.equals(otherRoleId);
            }

            Integer allianceId = role.getAllianceId();
            Integer otherAllianceId = other.getAllianceId();
            if (allianceId != null && otherAllianceId != null) {
                return allianceId.equals(otherAllianceId);
            }

            return this.role == other;
        }
    }

    public static class RoleOrCreate {
        @Nullable private Role role;
        @Nullable private final Integer allianceId;
        private final String name;
        private final Supplier<Color> hasColor;
        private final int position;
        private boolean fetched;
        private boolean createdNewRole;
        @Nullable private CompletableFuture<Role> future;
        @Nullable private String failedCreateErrorType;
        @Nullable private String failedCreateDetail;

        public RoleOrCreate(@Nullable Role roleOrNull, @Nullable Integer allianceId, String name, int position, Supplier<Color> hasColor) {
            this.role = roleOrNull;
            this.allianceId = allianceId;
            this.name = name;
            this.hasColor = hasColor;
            this.position = position;
        }

        public CompletableFuture<Role> submit(Guild guild) {
            if (future != null) {
                return future;
            }
            if (role == null && !fetched) {
                Color color = hasColor.get();
                RoleAction create = guild.createRole().setName(name).setMentionable(false).setHoisted(true);
                if (color != null) {
                    create = create.setColor(color);
                }
                fetched = true;
                try {
                    future = RateLimitUtil.queue(create, RateLimitedSources.DB_NATION_ROLE_ASSIGN).thenApply(created -> {
                        role = created;
                        createdNewRole = true;
                        if (created != null && position >= 0) {
                            RateLimitUtil.queue(guild.modifyRolePositions().selectPosition(created).moveTo(position), RateLimitedSources.DB_NATION_ROLE_ASSIGN);
                        }
                        return created;
                    }).exceptionally(throwable -> {
                        Throwable error = unwrap(throwable);
                        failedCreateErrorType = error.getClass().getSimpleName();
                        failedCreateDetail = error.getMessage();
                        return null;
                    });
                } catch (PermissionException e) {
                    failedCreateErrorType = e.getClass().getSimpleName();
                    failedCreateDetail = e.getMessage();
                    future = CompletableFuture.completedFuture(null);
                }
            }
            if (future != null) {
                return future;
            }
            return CompletableFuture.completedFuture(role);
        }

        @Nullable
        public Role getCachedOrNull() {
            return role;
        }

        @Nullable
        public Long getRoleIdOrNull() {
            return role == null ? null : role.getIdLong();
        }

        @Nullable
        public Integer getAllianceId() {
            return allianceId;
        }

        public boolean wasCreatedNewRole() {
            return createdNewRole;
        }

        @Nullable
        public String getFailedCreateErrorType() {
            return failedCreateErrorType;
        }

        @Nullable
        public String getFailedCreateDetail() {
            return failedCreateDetail;
        }
    }

    private static class NicknameChange {
        @Nullable private final String nickname;
        private final boolean clear;

        private NicknameChange(@Nullable String nickname) {
            this.nickname = nickname;
            this.clear = nickname == null;
        }
    }

    private static class MemberExecution {
        private final Set<Long> addedRoleIds = new LinkedHashSet<>();
        private final Set<Long> removedRoleIds = new LinkedHashSet<>();
        @Nullable private String appliedNickname;
        private boolean clearedNickname;
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static final Color BG = Color.decode("#36393E");

    private Set<Color> existingColors = new HashSet<>();

    public Supplier<Color> supplyColor(int allianceId, Collection<Role> allianceRoles) {
        return new Supplier<>() {
            private Color color;

            @Override
            public Color get() {
                if (color != null) {
                    return color;
                }

                if (existingColors == null) {
                    existingColors = new HashSet<>();
                    allianceRoles.forEach(role -> {
                        Color color = role.getColors().getPrimary();
                        if (color != null) {
                            existingColors.add(color);
                        }
                    });
                }

                Random random = new Random(allianceId);
                double maxDiff = 0;
                for (int i = 0; i < 100; i++) {
                    int nextInt = random.nextInt(0xffffff + 1);
                    String colorCode = String.format("#%06x", nextInt);
                    Color nextColor = Color.decode(colorCode);

                    if (CIEDE2000.calculateDeltaE(BG, nextColor) < 12) {
                        continue;
                    }

                    double minDiff = Double.MAX_VALUE;
                    for (Color otherColor : existingColors) {
                        if (otherColor != null) {
                            minDiff = Math.min(minDiff, CIEDE2000.calculateDeltaE(nextColor, otherColor));
                        }
                    }
                    if (minDiff > maxDiff) {
                        maxDiff = minDiff;
                        color = nextColor;
                    }
                    if (minDiff > 12) {
                        break;
                    }
                }
                if (color != null) {
                    existingColors.add(color);
                }
                return color;
            }
        };
    }
}
