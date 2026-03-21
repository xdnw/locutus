package link.locutus.discord.util.task.roles;

import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.db.GuildDB;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AutoRoleSyncState {
    public GuildDB.AutoNickOption nickname_mode;
    public GuildDB.AutoRoleOption alliance_mask_mode;
    public @Nullable Rank alliance_rank;
    public @Nullable Integer top_x;
    public boolean ally_gov_enabled;
    public boolean member_apps_enabled;
    public @Nullable Long registered_role;
    public List<Integer> masked_alliances = new ArrayList<>();
    public List<Integer> alliance_ids = new ArrayList<>();
    public List<Integer> ally_ids = new ArrayList<>();
    public List<Integer> extension_ids = new ArrayList<>();
    public Map<Integer, Long> alliance_roles = new LinkedHashMap<>();
    public List<Long> city_roles = new ArrayList<>();
    public List<TaxRole> tax_roles = new ArrayList<>();
    public Map<Integer, Long> applicant_roles = new LinkedHashMap<>();
    public Map<Integer, Long> member_roles = new LinkedHashMap<>();
    public List<ConditionalRole> conditional_roles = new ArrayList<>();

    public AutoRoleSyncState() {
    }

    public static class TaxRole {
        public int money_rate;
        public int rss_rate;
        public long role_id;

        public TaxRole() {
        }
    }

    public static class ConditionalRole {
        public String filter;
        public long role_id;

        public ConditionalRole() {
        }
    }
}
