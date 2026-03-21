package link.locutus.discord.util.task.roles;

import org.checkerframework.checker.nullness.qual.Nullable;

public class AutoRoleIssue {
    public AutoRoleIssueType type;
    @Nullable public Long role_id;
    @Nullable public Integer alliance_id;
    @Nullable public String nickname;
    @Nullable public String error_type;
    @Nullable public String detail;

    public AutoRoleIssue() {
    }

    public AutoRoleIssue(AutoRoleIssueType type) {
        this.type = type;
    }
}
