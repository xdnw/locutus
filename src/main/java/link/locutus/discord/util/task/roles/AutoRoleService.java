package link.locutus.discord.util.task.roles;

import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AutoRoleService {
    private AutoRoleService() {
    }

    public static AutoRoleResult autorole(GuildDB db, Member member, boolean force) {
        IAutoRoleTask task = db.getAutoRoleTask();
        AutoRoleSyncState syncState = task.syncDB();
        DBNation nation = DiscordUtil.getNation(member.getUser());

        AutoRoleResult result = new AutoRoleResult();
        result.sync = syncState;
        result.result = createMemberResult(member, nation);
        if (nation == null) {
            result.result.issues.add(issue(AutoRoleIssueType.NOT_REGISTERED));
            collectRoleNames(result.role_names, db.getGuild(), syncState, result.result, result.rename_roles, result.renamed_roles);
            return result;
        }

        AutoRoleInfo info = task.autoRole(member, nation);
        result.create_roles.addAll(info.getCreateAllianceIds());
        result.rename_roles.putAll(info.getRenameRoleMap());
        populateMemberPlan(result.result, info, member);
        collectRoleNames(result.role_names, db.getGuild(), syncState, result.result, result.rename_roles, result.renamed_roles);

        if (force) {
            info.execute();
            result.created_roles.addAll(info.getCreatedRoleIds());
            result.renamed_roles.putAll(info.getRenamedRoles());
            result.execution_issues.addAll(info.getGlobalExecutionIssues());
            populateMemberExecution(result.result, info, member);
            result.role_names.putAll(info.getCreatedRoleNames());
            result.execution_issues.forEach(issue -> registerRoleId(result.role_names, db.getGuild(), issue.role_id));
        }

        return result;
    }

    public static AutoRoleBulkResult autoroleall(GuildDB db, boolean force) {
        AutoRoleInfo info = db.getAutoRoleTask().autoRoleAll();
        AutoRoleBulkResult result = new AutoRoleBulkResult();
        result.sync = info.getSyncState();
        result.create_roles.addAll(info.getCreateAllianceIds());
        result.rename_roles.putAll(info.getRenameRoleMap());
        result.masked_non_members.addAll(getMaskedNonMembers(db));

        for (Member member : info.getPlannedMembers()) {
            DBNation nation = DiscordUtil.getNation(member.getIdLong());
            AutoRoleMemberResult memberResult = createMemberResult(member, nation);
            populateMemberPlan(memberResult, info, member);
            if (force) {
                // Execution is applied after the shared plan snapshot is collected.
            }
            if (hasContent(memberResult)) {
                result.results.add(memberResult);
            }
        }

        collectRoleNames(result.role_names, db.getGuild(), result.sync, result.results, result.rename_roles, result.renamed_roles);

        if (force) {
            info.execute();
            result.created_roles.addAll(info.getCreatedRoleIds());
            result.renamed_roles.putAll(info.getRenamedRoles());
            result.execution_issues.addAll(info.getGlobalExecutionIssues());
            for (AutoRoleMemberResult memberResult : result.results) {
                Member member = db.getGuild().getMemberById(memberResult.user_id);
                if (member != null) {
                    populateMemberExecution(memberResult, info, member);
                }
            }
            result.role_names.putAll(info.getCreatedRoleNames());
            result.execution_issues.forEach(issue -> registerRoleId(result.role_names, db.getGuild(), issue.role_id));
        }

        return result;
    }

    private static AutoRoleMemberResult createMemberResult(Member member, @Nullable DBNation nation) {
        AutoRoleMemberResult result = new AutoRoleMemberResult();
        User user = member.getUser();
        result.user_id = user.getIdLong();
        result.username = DiscordUtil.getFullUsername(user);
        String displayName = member.getEffectiveName();
        result.display_name = displayName == null || displayName.isBlank() ? result.username : displayName;
        if (nation != null) {
            result.nation_id = nation.getNation_id();
            result.alliance_id = nation.getAlliance_id();
        }
        return result;
    }

    private static void populateMemberPlan(AutoRoleMemberResult target, AutoRoleInfo info, Member member) {
        target.create_roles.addAll(info.getCreateAllianceIds(member));
        target.add_roles.addAll(info.getAddRoleIds(member));
        target.remove_roles.addAll(info.getRemoveRoleIds(member));
        target.nickname = info.getNickname(member);
        target.clear_nickname = info.isClearNickname(member);
        target.issues.addAll(info.getIssues(member));
    }

    private static void populateMemberExecution(AutoRoleMemberResult target, AutoRoleInfo info, Member member) {
        target.added_roles.addAll(info.getAddedRoleIds(member));
        target.removed_roles.addAll(info.getRemovedRoleIds(member));
        target.applied_nickname = info.getAppliedNickname(member);
        target.cleared_nickname = info.wasNicknameCleared(member);
        target.execution_issues.addAll(info.getExecutionIssues(member));
    }

    private static List<AutoRoleMaskedMember> getMaskedNonMembers(GuildDB db) {
        List<AutoRoleMaskedMember> maskedMembers = new ArrayList<>();
        for (Map.Entry<Member, UnmaskedReason> entry : db.getMaskedNonMembers().entrySet()) {
            Member member = entry.getKey();
            User user = member.getUser();

            AutoRoleMaskedMember maskedMember = new AutoRoleMaskedMember();
            maskedMember.user_id = user.getIdLong();
            maskedMember.username = DiscordUtil.getFullUsername(user);
            String displayName = member.getEffectiveName();
            maskedMember.display_name = displayName == null || displayName.isBlank() ? maskedMember.username : displayName;
            maskedMember.reason = entry.getValue();

            DBNation nation = DiscordUtil.getNation(user);
            if (nation != null) {
                maskedMember.nation_id = nation.getNation_id();
            }
            maskedMembers.add(maskedMember);
        }
        maskedMembers.sort((left, right) -> Long.compare(left.user_id, right.user_id));
        return maskedMembers;
    }

    private static void collectRoleNames(Map<Long, String> roleNames, Guild guild, @Nullable AutoRoleSyncState sync,
            AutoRoleMemberResult memberResult, Map<Long, String> renameRoles, Map<Long, String> renamedRoles) {
        collectRoleNames(roleNames, guild, sync, List.of(memberResult), renameRoles, renamedRoles);
    }

    private static void collectRoleNames(Map<Long, String> roleNames, Guild guild, @Nullable AutoRoleSyncState sync,
            List<AutoRoleMemberResult> memberResults, Map<Long, String> renameRoles, Map<Long, String> renamedRoles) {
        if (sync != null) {
            registerRoleId(roleNames, guild, sync.registered_role);
            sync.alliance_roles.values().forEach(roleId -> registerRoleId(roleNames, guild, roleId));
            sync.city_roles.forEach(roleId -> registerRoleId(roleNames, guild, roleId));
            sync.tax_roles.forEach(taxRole -> registerRoleId(roleNames, guild, taxRole.role_id));
            sync.applicant_roles.values().forEach(roleId -> registerRoleId(roleNames, guild, roleId));
            sync.member_roles.values().forEach(roleId -> registerRoleId(roleNames, guild, roleId));
            sync.conditional_roles.forEach(role -> registerRoleId(roleNames, guild, role.role_id));
        }
        renameRoles.keySet().forEach(roleId -> registerRoleId(roleNames, guild, roleId));
        renamedRoles.keySet().forEach(roleId -> registerRoleId(roleNames, guild, roleId));
        for (AutoRoleMemberResult memberResult : memberResults) {
            memberResult.add_roles.forEach(roleId -> registerRoleId(roleNames, guild, roleId));
            memberResult.remove_roles.forEach(roleId -> registerRoleId(roleNames, guild, roleId));
            memberResult.added_roles.forEach(roleId -> registerRoleId(roleNames, guild, roleId));
            memberResult.removed_roles.forEach(roleId -> registerRoleId(roleNames, guild, roleId));
            memberResult.issues.forEach(issue -> registerRoleId(roleNames, guild, issue.role_id));
            memberResult.execution_issues.forEach(issue -> registerRoleId(roleNames, guild, issue.role_id));
        }
    }

    private static void registerRoleId(Map<Long, String> roleNames, Guild guild, @Nullable Long roleId) {
        if (roleId == null || roleNames.containsKey(roleId)) {
            return;
        }
        Role role = guild.getRoleById(roleId);
        if (role != null) {
            roleNames.put(roleId, role.getName());
        }
    }

    private static boolean hasContent(AutoRoleMemberResult result) {
        return !result.create_roles.isEmpty()
                || !result.add_roles.isEmpty()
                || !result.remove_roles.isEmpty()
                || result.nickname != null
                || result.clear_nickname
                || !result.issues.isEmpty()
                || !result.added_roles.isEmpty()
                || !result.removed_roles.isEmpty()
                || result.applied_nickname != null
                || result.cleared_nickname
                || !result.execution_issues.isEmpty();
    }

    private static AutoRoleIssue issue(AutoRoleIssueType type) {
        return new AutoRoleIssue(type);
    }
}
