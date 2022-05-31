package com.boydti.discord.web.commands;

import com.boydti.discord.commands.manager.v2.binding.annotation.Command;
import com.boydti.discord.commands.manager.v2.binding.annotation.Default;
import com.boydti.discord.commands.manager.v2.binding.annotation.Me;
import com.boydti.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.util.offshore.Grant;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.apiv1.enums.city.project.Project;
import net.dv8tion.jda.api.entities.User;
import com.boydti.discord.pnw.DBNation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


public class GrantPages {

    private Set<Grant> fetchGrants(GuildDB db, User user, DBNation nation, Grant.Type type, Map<Grant, List<String>> failedRequirements, Map<Grant, List<String>> overrideRequirements, Map<Grant, UUID> grantTokens) {
        boolean overrideSafe = Roles.ECON_LOW_GOV.has(user, db.getGuild());
        boolean overrideUnsafe = Roles.ECON.has(user, db.getGuild());

        Set<Grant> grants = new LinkedHashSet<>(db.getHandler().getEligableGrants(nation, type, overrideSafe, overrideUnsafe));

        for (Grant grant : grants) {
            grant.getNote();
            grant.getInstructions();
            PnwUtil.resourcesToString(grant.cost());

            boolean allowed = true;
            for (Grant.Requirement requirement : grant.getRequirements()) {
                long start = System.currentTimeMillis();
                Boolean result = requirement.apply(nation);
                long diff = System.currentTimeMillis() - start;
                if (diff > 10) {
                    System.out.println(requirement.getMessage() + " took " + (diff) + "ms");
                }
                if (!result) {
                    if (requirement.canOverride()) {
                        overrideRequirements.computeIfAbsent(grant, f -> new ArrayList<>()).add(requirement.getMessage());
                    } else {
                        allowed = false;
                        failedRequirements.computeIfAbsent(grant, f -> new ArrayList<>()).add(requirement.getMessage());
                    }
                }
            }
            UUID token = UUID.randomUUID();
            grantTokens.put(grant, token);
            if (allowed) {
                Grant.addGrant(db.getIdLong(), token, grant);
            }
        }
        return grants;
    }
    @Command
    @RolePermission(value = {Roles.ECON_LOW_GOV,Roles.ECON}, any=true)
    public String projectGrants(@Me GuildDB db, @Me User user, @Me DBNation me, @Default DBNation nation) {
        if (nation == null) nation = me;
        Map<Grant, List<String>> failedRequirements = new HashMap<>();
        Map<Grant, List<String>> overrideRequirements = new HashMap<>();
        Map<Grant, UUID> grantTokens = new HashMap<>();

        try {
        Set<Grant> grants = fetchGrants(db, user, nation, Grant.Type.PROJECT, failedRequirements, overrideRequirements, grantTokens);
        Set<Project> recommendedProjects = db.getHandler().getRecommendedProjects(nation);

        return views.grant.projects.template(recommendedProjects, grants, user, nation, failedRequirements, overrideRequirements, grantTokens).render().toString();
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    @Command
    @RolePermission(value = {Roles.ECON_LOW_GOV,Roles.ECON}, any=true)
    public String cityGrants(@Me GuildDB db, @Me User user, @Me DBNation me, @Default DBNation nation) {
        if (nation == null) nation = me;
        Map<Grant, List<String>> failedRequirements = new HashMap<>();
        Map<Grant, List<String>> overrideRequirements = new HashMap<>();
        Map<Grant, UUID> grantTokens = new HashMap<>();

        try {
        Set<Grant> grants = fetchGrants(db, user, nation, Grant.Type.CITY, failedRequirements, overrideRequirements, grantTokens);
        return views.grant.cities.template(grants, user, nation, failedRequirements, overrideRequirements, grantTokens).render().toString();
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    @Command
    @RolePermission(value = {Roles.ECON_LOW_GOV,Roles.ECON}, any=true)
    public String infraGrants(@Me GuildDB db, @Me User user, @Me DBNation me, @Default DBNation nation) {
        if (nation == null) nation = me;
        Map<Grant, List<String>> failedRequirements = new HashMap<>();
        Map<Grant, List<String>> overrideRequirements = new HashMap<>();
        Map<Grant, UUID> grantTokens = new HashMap<>();

        try {
            Set<Grant> grants = fetchGrants(db, user, nation, Grant.Type.INFRA, failedRequirements, overrideRequirements, grantTokens);
            return views.grant.infras.template(grants, user, nation, failedRequirements, overrideRequirements, grantTokens).render().toString();
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    @Command
    @RolePermission(value = {Roles.ECON_LOW_GOV,Roles.ECON}, any=true)
    public String landGrants(@Me GuildDB db, @Me User user, @Me DBNation me, @Default DBNation nation) {
        if (nation == null) nation = me;
        Map<Grant, List<String>> failedRequirements = new HashMap<>();
        Map<Grant, List<String>> overrideRequirements = new HashMap<>();
        Map<Grant, UUID> grantTokens = new HashMap<>();

        try {
            Set<Grant> grants = fetchGrants(db, user, nation, Grant.Type.LAND, failedRequirements, overrideRequirements, grantTokens);
            return views.grant.lands.template(grants, user, nation, failedRequirements, overrideRequirements, grantTokens).render().toString();
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }
}
