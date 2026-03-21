package link.locutus.discord.util.task.roles;

import link.locutus.discord.db.entities.DBAlliance;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AutoRoleTextFormatter {
    private AutoRoleTextFormatter() {
    }

    public static String formatPlan(AutoRoleInfo info) {
        StringBuilder result = new StringBuilder();
        appendCreateRoles(result, info.getCreateAllianceIds());
        appendRoleMap(result, "Rename Roles", info.getRenameRoleMap(), currentRoleNames(info));
        for (Member member : info.getPlannedMembers()) {
            appendMemberPlan(result, info, member);
        }
        return result.length() == 0 ? "No changes" : result.toString().trim();
    }

    public static String formatExecution(AutoRoleInfo info) {
        StringBuilder result = new StringBuilder();
        appendCreatedRoles(result, info.getCreatedRoleIds(), info.getCreatedRoleNames());
        appendRoleMap(result, "Renamed Roles", info.getRenamedRoles(), currentRoleNames(info));
        appendIssues(result, "Execution Issues", info.getGlobalExecutionIssues(), currentRoleNames(info));
        for (Member member : info.getPlannedMembers()) {
            appendMemberExecution(result, info, member);
        }
        return result.length() == 0 ? "No changes" : result.toString().trim();
    }

    public static String formatSyncState(@Nullable AutoRoleSyncState sync, Map<Long, String> roleNames) {
        if (sync == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        appendLine(result, "AUTONICK", sync.nickname_mode == null ? null : sync.nickname_mode.name());
        appendLine(result, "AUTOROLE_ALLIANCES", sync.alliance_mask_mode == null ? null : sync.alliance_mask_mode.name());
        appendLine(result, "AUTOROLE_ALLIANCE_RANK", sync.alliance_rank == null ? null : sync.alliance_rank.name());
        appendLine(result, "AUTOROLE_TOP_X", sync.top_x == null ? "All" : Integer.toString(sync.top_x));
        appendList(result, "Masked Alliances", sync.masked_alliances.stream().map(String::valueOf).toList());
        appendLine(result, "AUTOROLE_ALLY_GOV", Boolean.toString(sync.ally_gov_enabled));

        List<String> allianceRoles = new ArrayList<>();
        sync.alliance_roles.forEach((allianceId, roleId) -> allianceRoles.add(allianceId + " -> " + roleName(roleId, roleNames)));
        appendList(result, "Found Alliance Roles", allianceRoles);
        appendLine(result, "REGISTERED", sync.registered_role == null ? null : roleName(sync.registered_role, roleNames));
        appendList(result, "Found City Roles", sync.city_roles.stream().map(roleId -> roleName(roleId, roleNames)).toList());

        List<String> taxRoles = new ArrayList<>();
        for (AutoRoleSyncState.TaxRole taxRole : sync.tax_roles) {
            taxRoles.add(taxRole.money_rate + "/" + taxRole.rss_rate + " -> " + roleName(taxRole.role_id, roleNames));
        }
        appendList(result, "Found Tax Roles", taxRoles);

        appendLine(result, "Auto Role Members/Apps", Boolean.toString(sync.member_apps_enabled));
        appendList(result, "Applicant Roles", mappingLines(sync.applicant_roles, roleNames));
        appendList(result, "Member Roles", mappingLines(sync.member_roles, roleNames));

        List<String> conditionalRoles = new ArrayList<>();
        for (AutoRoleSyncState.ConditionalRole conditionalRole : sync.conditional_roles) {
            conditionalRoles.add(conditionalRole.filter + ": " + roleName(conditionalRole.role_id, roleNames));
        }
        appendList(result, "Conditional Roles", conditionalRoles);
        appendList(result, "Alliances", sync.alliance_ids.stream().map(String::valueOf).toList());
        appendList(result, "Allies", sync.ally_ids.stream().map(String::valueOf).toList());
        appendList(result, "Extensions", sync.extension_ids.stream().map(String::valueOf).toList());
        return result.toString().trim();
    }

    public static String formatIssue(AutoRoleIssue issue, Map<Long, String> roleNames) {
        StringBuilder response = new StringBuilder();
        response.append(issue.type.name());
        if (issue.role_id != null) {
            response.append(" [role=").append(roleName(issue.role_id, roleNames)).append("]");
        }
        if (issue.alliance_id != null) {
            response.append(" [alliance=").append(formatAlliance(issue.alliance_id)).append("]");
        }
        if (issue.nickname != null) {
            response.append(" [nickname=").append(issue.nickname).append("]");
        }
        if (issue.error_type != null) {
            response.append(" [error=").append(issue.error_type).append("]");
        }
        if (issue.detail != null && !issue.detail.isBlank()) {
            response.append(" ").append(issue.detail);
        }
        return response.toString();
    }

    private static void appendMemberPlan(StringBuilder result, AutoRoleInfo info, Member member) {
        List<Integer> createRoles = info.getCreateAllianceIds(member);
        List<Long> addRoles = info.getAddRoleIds(member);
        List<Long> removeRoles = info.getRemoveRoleIds(member);
        String nickname = info.getNickname(member);
        boolean clearNickname = info.isClearNickname(member);
        List<AutoRoleIssue> issues = info.getIssues(member);
        if (createRoles.isEmpty() && addRoles.isEmpty() && removeRoles.isEmpty() && nickname == null && !clearNickname && issues.isEmpty()) {
            return;
        }
        if (result.length() > 0) {
            result.append("\n");
        }
        result.append(memberLabel(member)).append("\n");
        appendIndentedList(result, "Create Roles", createRoles.stream().map(id -> formatAlliance(id)).toList());
        appendIndentedList(result, "Add Roles", addRoles.stream().map(roleId -> roleName(roleId, currentRoleNames(info))).toList());
        appendIndentedList(result, "Remove Roles", removeRoles.stream().map(roleId -> roleName(roleId, currentRoleNames(info))).toList());
        if (clearNickname) {
            appendIndentedValue(result, "Clear Nickname", "true");
        } else if (nickname != null) {
            appendIndentedValue(result, "Set Nickname", nickname);
        }
        appendIssues(result, "Issues", issues, currentRoleNames(info), true);
    }

    private static void appendMemberExecution(StringBuilder result, AutoRoleInfo info, Member member) {
        List<Long> addedRoles = info.getAddedRoleIds(member);
        List<Long> removedRoles = info.getRemovedRoleIds(member);
        String nickname = info.getAppliedNickname(member);
        boolean clearedNickname = info.wasNicknameCleared(member);
        List<AutoRoleIssue> issues = info.getExecutionIssues(member);
        if (addedRoles.isEmpty() && removedRoles.isEmpty() && nickname == null && !clearedNickname && issues.isEmpty()) {
            return;
        }
        if (result.length() > 0) {
            result.append("\n");
        }
        result.append(memberLabel(member)).append("\n");
        appendIndentedList(result, "Added Roles", addedRoles.stream().map(roleId -> roleName(roleId, currentRoleNames(info))).toList());
        appendIndentedList(result, "Removed Roles", removedRoles.stream().map(roleId -> roleName(roleId, currentRoleNames(info))).toList());
        if (clearedNickname) {
            appendIndentedValue(result, "Cleared Nickname", "true");
        } else if (nickname != null) {
            appendIndentedValue(result, "Applied Nickname", nickname);
        }
        appendIssues(result, "Execution Issues", issues, currentRoleNames(info), true);
    }

    private static void appendCreateRoles(StringBuilder result, List<Integer> allianceIds) {
        if (allianceIds.isEmpty()) {
            return;
        }
        if (result.length() > 0) {
            result.append("\n");
        }
        result.append("Create Roles:\n");
        for (Integer allianceId : allianceIds) {
            result.append("- ").append(formatAlliance(allianceId)).append("\n");
        }
    }

    private static void appendCreatedRoles(StringBuilder result, List<Long> roleIds, Map<Long, String> createdRoleNames) {
        if (roleIds.isEmpty()) {
            return;
        }
        if (result.length() > 0) {
            result.append("\n");
        }
        result.append("Created Roles:\n");
        for (Long roleId : roleIds) {
            result.append("- ").append(roleName(roleId, createdRoleNames)).append("\n");
        }
    }

    private static void appendRoleMap(StringBuilder result, String title, Map<Long, String> roles, Map<Long, String> roleNames) {
        if (roles.isEmpty()) {
            return;
        }
        if (result.length() > 0) {
            result.append("\n");
        }
        result.append(title).append(":\n");
        roles.forEach((roleId, newName) -> result.append("- ").append(roleName(roleId, roleNames)).append(" -> ").append(newName).append("\n"));
    }

    private static void appendIssues(StringBuilder result, String title, List<AutoRoleIssue> issues, Map<Long, String> roleNames) {
        appendIssues(result, title, issues, roleNames, false);
    }

    private static void appendIssues(StringBuilder result, String title, List<AutoRoleIssue> issues, Map<Long, String> roleNames, boolean indented) {
        if (issues.isEmpty()) {
            return;
        }
        if (indented) {
            result.append("  ").append(title).append(":\n");
            for (AutoRoleIssue issue : issues) {
                result.append("  - ").append(formatIssue(issue, roleNames)).append("\n");
            }
            return;
        }
        if (result.length() > 0) {
            result.append("\n");
        }
        result.append(title).append(":\n");
        for (AutoRoleIssue issue : issues) {
            result.append("- ").append(formatIssue(issue, roleNames)).append("\n");
        }
    }

    private static void appendIndentedList(StringBuilder result, String title, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        result.append("  ").append(title).append(": ").append(String.join(", ", values)).append("\n");
    }

    private static void appendIndentedValue(StringBuilder result, String title, String value) {
        result.append("  ").append(title).append(": ").append(value).append("\n");
    }

    private static void appendLine(StringBuilder result, String title, @Nullable String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        result.append(title).append(": ").append(value).append("\n");
    }

    private static void appendList(StringBuilder result, String title, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        result.append(title).append(":\n");
        for (String value : values) {
            result.append("- ").append(value).append("\n");
        }
    }

    private static List<String> mappingLines(Map<Integer, Long> mappings, Map<Long, String> roleNames) {
        if (mappings == null || mappings.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        mappings.forEach((key, value) -> values.add(key + " -> " + roleName(value, roleNames)));
        return values;
    }

    private static Map<Long, String> currentRoleNames(AutoRoleInfo info) {
        Map<Long, String> roleNames = new LinkedHashMap<>();
        for (Role role : info.getDb().getGuild().getRoles()) {
            roleNames.put(role.getIdLong(), role.getName());
        }
        return roleNames;
    }

    private static String memberLabel(Member member) {
        String displayName = member.getEffectiveName();
        return displayName == null || displayName.isBlank() ? member.getId() : displayName + " (" + member.getId() + ")";
    }

    private static String roleName(long roleId, Map<Long, String> roleNames) {
        return roleNames.getOrDefault(roleId, Long.toString(roleId));
    }

    private static String formatAlliance(int allianceId) {
        DBAlliance alliance = DBAlliance.get(allianceId);
        if (alliance == null) {
            return Integer.toString(allianceId);
        }
        return allianceId + " " + alliance.getName();
    }
}
