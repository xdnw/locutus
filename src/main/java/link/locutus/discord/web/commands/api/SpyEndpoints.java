package link.locutus.discord.web.commands.api;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.war.SpyOpsService;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.Operation;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.CacheType;
import link.locutus.discord.web.commands.binding.value_types.WebSpyTarget;
import link.locutus.discord.web.commands.binding.value_types.WebSpyTargets;
import link.locutus.discord.web.commands.page.PageHelper;

import java.util.Set;

public class SpyEndpoints extends PageHelper {
    @Command(desc = "Compute the best spy ops for a target set", viewable = true)
    @RolePermission(Roles.MEMBER)
    @ReturnType(value = WebSpyTargets.class, cache = CacheType.SessionStorage, duration = 30)
    public WebSpyTargets spyops(@Me @Default GuildDB db,
                                @Me @Default DBNation me,
                                @Default DBNation attacker,
                                @Default("*") Set<DBNation> targets,
                                @Default("*") Set<Operation> operations,
                                @Default("40") @Range(min = 0, max = 100) int requiredSuccess,
                                @Default("false") boolean prioritizeKills,
                                @Default("5") @Range(min = 1, max = 25) int numResults) {
        DBNation finalAttacker = attacker == null ? me : attacker;
        if (finalAttacker == null) {
            throw new IllegalArgumentException("Please sign in or provide an attacker nation");
        }

        SpyOpsService.SpyOpsResult result = SpyOpsService.findSpyOps(
                finalAttacker,
                db,
                targets,
                operations,
                requiredSuccess,
                prioritizeKills,
                numResults);
        return toWebSpyTargets(finalAttacker, result);
    }

    @Command(desc = "Find nations to gather intel on", viewable = true)
    @RolePermission(Roles.MEMBER)
    @ReturnType(value = WebSpyTargets.class, cache = CacheType.SessionStorage, duration = 30)
    public WebSpyTargets intel(@Me @Default GuildDB db,
                               @Me @Default DBNation me,
                               @Default DBNation attacker,
                               @Default Integer dnrTopX,
                               @Default("false") boolean ignoreDNR,
                               @Default Double score,
                               @Default("8") @Range(min = 1, max = 25) int numResults) {
        DBNation finalAttacker = attacker == null ? me : attacker;
        if (finalAttacker == null) {
            throw new IllegalArgumentException("Please sign in or provide an attacker nation");
        }

        SpyOpsService.IntelResult result = SpyOpsService.findIntelTargets(
                finalAttacker,
                db,
                dnrTopX,
                ignoreDNR,
                score,
                null,
                numResults);

        return toWebSpyTargets(finalAttacker, result);
    }

    private static WebSpyTargets toWebSpyTargets(DBNation attacker, SpyOpsService.SpyOpsResult result) {
        WebSpyTargets response = new WebSpyTargets(attacker);
        response.message = result.message();
        for (SpyOpsService.SpyOpRecommendation recommendation : result.recommendations()) {
            response.targets.add(new WebSpyTarget(recommendation));
        }
        return response;
    }

    private static WebSpyTargets toWebSpyTargets(DBNation attacker, SpyOpsService.IntelResult result) {
        WebSpyTargets response = new WebSpyTargets(attacker);
        response.message = result.message();
        for (SpyOpsService.IntelRecommendation recommendation : result.recommendations()) {
            response.targets.add(new WebSpyTarget(recommendation));
        }
        return response;
    }
}