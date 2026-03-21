package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.task.roles.AutoRoleRoleDirectory;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.WebAllianceAutoRole;
import link.locutus.discord.web.commands.binding.value_types.WebAutoRoleRoles;
import link.locutus.discord.web.commands.binding.value_types.WebCityAutoRole;
import link.locutus.discord.web.commands.binding.value_types.WebRoleAliases;
import link.locutus.discord.web.commands.binding.value_types.WebTaxAutoRole;
import link.locutus.discord.web.commands.page.PageHelper;
import net.dv8tion.jda.api.entities.Role;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class RoleEndpoints {
    private static final Comparator<Map.Entry<Long, Long>> ALLIANCE_ORDER = Comparator
            .comparingLong(entry -> entry.getKey() == 0L ? Long.MIN_VALUE : entry.getKey());

    @Command(desc = "List the bot role aliases", viewable = true)
    @RolePermission(Roles.MEMBER)
    @ReturnType(WebRoleAliases.class)
    public WebRoleAliases list_role_aliases(@Me GuildDB db, @Default Set<Roles> roles_filter) {
        Set<Roles> explicitFilter = roles_filter == null ? Collections.emptySet() : roles_filter;

        IntArrayList allows_alliance = new IntArrayList();
        Int2IntOpenHashMap requiresSettings = new Int2IntOpenHashMap();

        Map<Long, String> discordRoleNames = getDiscordRoleNames(db);
        Map<Roles, Map<Long, Long>> rawMappings = db.getMappingRaw();
        boolean hasExplicitFilter = !explicitFilter.isEmpty();

        Map<Integer, Map<Long, Long>> mappings = new Int2ObjectLinkedOpenHashMap<>();
        IntArrayList invalidRoleOrdinals = new IntArrayList();

        for (Roles role : Roles.values) {
            if (hasExplicitFilter && !explicitFilter.contains(role)) {
                continue;
            }

            if (role.allowAlliance()) {
                allows_alliance.add(role.ordinal());
            }

            GuildSetting<?> key = role.getKey();
            if (key != null) {
                int ord = key.getOrdinal();
                if (ord >= 0) {
                    requiresSettings.put(role.ordinal(), ord);
                }
            }

            Map<Long, Long> rawRoleMap = rawMappings.getOrDefault(role, Collections.emptyMap());
            if (!hasExplicitFilter && rawRoleMap.isEmpty() && !isRoleEnabledForGuild(db, role)) {
                continue;
            }
            Map<Long, Long> roleMappings = new LinkedHashMap<>();
            boolean hasInvalidMapping = false;
            for (Map.Entry<Long, Long> entry : rawRoleMap.entrySet().stream().sorted(ALLIANCE_ORDER).toList()) {
                roleMappings.put(entry.getKey(), entry.getValue());
                if (!discordRoleNames.containsKey(entry.getValue())) {
                    hasInvalidMapping = true;
                }
            }

            mappings.put(role.ordinal(), roleMappings);
            if (hasInvalidMapping) {
                invalidRoleOrdinals.add(role.ordinal());
            }
        }

        return new WebRoleAliases(mappings, invalidRoleOrdinals, allows_alliance, requiresSettings, discordRoleNames);
    }

    @Command(desc = "List autorole-managed alliance, city, and tax roles", viewable = true)
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.INTERNAL_AFFAIRS_STAFF}, alliance = true, any = true)
    @ReturnType(WebAutoRoleRoles.class)
    public WebAutoRoleRoles list_autorole_roles(@Me GuildDB db) {
        AutoRoleRoleDirectory.Snapshot snapshot = AutoRoleRoleDirectory.snapshot(db.getGuild());
        return new WebAutoRoleRoles(
                snapshot.allianceRoles().stream().map(RoleEndpoints::toAllianceAutoRole).toList(),
                snapshot.cityRoles().stream().map(RoleEndpoints::toCityAutoRole).toList(),
                snapshot.taxRoles().stream().map(RoleEndpoints::toTaxAutoRole).toList());
    }

    @Command(desc = "Create or return the alliance autorole role")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.INTERNAL_AFFAIRS_STAFF}, alliance = true, any = true)
    @ReturnType(WebAllianceAutoRole.class)
    public Object add_alliance_role(@Me GuildDB db, DBAlliance alliance) {
        return managedRoleMutation(() -> toAllianceAutoRole(AutoRoleRoleDirectory.addAllianceRole(db, alliance)));
    }

    @Command(desc = "Delete the alliance autorole role")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.INTERNAL_AFFAIRS_STAFF}, alliance = true, any = true)
    @ReturnType(WebAllianceAutoRole.class)
    public Object remove_alliance_role(@Me GuildDB db, DBAlliance alliance) {
        return managedRoleMutation(() -> toAllianceAutoRole(AutoRoleRoleDirectory.removeAllianceRole(db, alliance)));
    }

    @Command(desc = "Create or return the city autorole role")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.INTERNAL_AFFAIRS_STAFF}, alliance = true, any = true)
    @ReturnType(WebCityAutoRole.class)
    public Object add_city_role(@Me GuildDB db, CityRanges range) {
        return managedRoleMutation(() -> toCityAutoRole(AutoRoleRoleDirectory.addCityRole(db, range)));
    }

    @Command(desc = "Delete the city autorole role")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.INTERNAL_AFFAIRS_STAFF}, alliance = true, any = true)
    @ReturnType(WebCityAutoRole.class)
    public Object remove_city_role(@Me GuildDB db, CityRanges range) {
        return managedRoleMutation(() -> toCityAutoRole(AutoRoleRoleDirectory.removeCityRole(db, range)));
    }

    @Command(desc = "Create or return the tax autorole role")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.INTERNAL_AFFAIRS_STAFF}, alliance = true, any = true)
    @ReturnType(WebTaxAutoRole.class)
    public Object add_tax_role(@Me GuildDB db, TaxRate rate) {
        return managedRoleMutation(() -> toTaxAutoRole(AutoRoleRoleDirectory.addTaxRole(db, rate)));
    }

    @Command(desc = "Delete the tax autorole role")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.INTERNAL_AFFAIRS_STAFF}, alliance = true, any = true)
    @ReturnType(WebTaxAutoRole.class)
    public Object remove_tax_role(@Me GuildDB db, TaxRate rate) {
        return managedRoleMutation(() -> toTaxAutoRole(AutoRoleRoleDirectory.removeTaxRole(db, rate)));
    }

    private static Map<Long, String> getDiscordRoleNames(GuildDB db) {
        Map<Long, String> roleNames = new LinkedHashMap<>();
        Role publicRole = db.getGuild().getPublicRole();
        roleNames.put(publicRole.getIdLong(), publicRole.getName());
        db.getGuild().getRoles().stream()
                .sorted(Comparator.comparingLong(Role::getIdLong))
                .forEach(role -> roleNames.put(role.getIdLong(), role.getName()));
        return roleNames;
    }

    @SuppressWarnings("unchecked")
    private static boolean isRoleEnabledForGuild(GuildDB db, Roles role) {
        if (role.getKey() == null) {
            return true;
        }
        return db.getOrNull(role.getKey()) != null;
    }

    private static WebAllianceAutoRole toAllianceAutoRole(AutoRoleRoleDirectory.AllianceRole entry) {
        return new WebAllianceAutoRole(entry.role(), entry.allianceId(), entry.duplicateKey());
    }

    private static WebCityAutoRole toCityAutoRole(AutoRoleRoleDirectory.CityRole entry) {
        return new WebCityAutoRole(entry.role(), entry.rangeStart(), entry.rangeEnd(), entry.duplicateKey());
    }

    private static WebTaxAutoRole toTaxAutoRole(AutoRoleRoleDirectory.TaxRole entry) {
        return new WebTaxAutoRole(entry.role(), entry.moneyRate(), entry.rssRate(), entry.duplicateKey());
    }

    private static <T> Object managedRoleMutation(Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            return PageHelper.error(e.getMessage());
        }
    }
}
