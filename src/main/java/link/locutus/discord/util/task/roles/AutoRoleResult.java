package link.locutus.discord.util.task.roles;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AutoRoleResult {
    @Nullable public AutoRoleSyncState sync;
    public Map<Long, String> role_names = new LinkedHashMap<>();
    public List<Integer> create_roles = new ArrayList<>();
    public Map<Long, String> rename_roles = new LinkedHashMap<>();
    public List<Long> created_roles = new ArrayList<>();
    public Map<Long, String> renamed_roles = new LinkedHashMap<>();
    public List<AutoRoleIssue> execution_issues = new ArrayList<>();
    public AutoRoleMemberResult result = new AutoRoleMemberResult();

    public AutoRoleResult() {
    }
}
