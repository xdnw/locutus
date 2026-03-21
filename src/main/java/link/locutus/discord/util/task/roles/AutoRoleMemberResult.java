package link.locutus.discord.util.task.roles;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AutoRoleMemberResult {
    public long user_id;
    public String username;
    public String display_name;
    @Nullable public Integer nation_id;
    @Nullable public Integer alliance_id;
    public List<Integer> create_roles = new ArrayList<>();
    public List<Long> add_roles = new ArrayList<>();
    public List<Long> remove_roles = new ArrayList<>();
    @Nullable public String nickname;
    public boolean clear_nickname;
    public List<AutoRoleIssue> issues = new ArrayList<>();
    public List<Long> added_roles = new ArrayList<>();
    public List<Long> removed_roles = new ArrayList<>();
    @Nullable public String applied_nickname;
    public boolean cleared_nickname;
    public List<AutoRoleIssue> execution_issues = new ArrayList<>();

    public AutoRoleMemberResult() {
    }
}
