package link.locutus.discord.util.task.roles;

import org.checkerframework.checker.nullness.qual.Nullable;

public class AutoRoleMaskedMember {
    public long user_id;
    public String username;
    public String display_name;
    @Nullable public Integer nation_id;
    public UnmaskedReason reason;

    public AutoRoleMaskedMember() {
    }
}
