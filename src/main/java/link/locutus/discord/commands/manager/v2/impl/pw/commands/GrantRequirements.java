package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timediff;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.db.entities.MMRDouble;
import link.locutus.discord.db.entities.MMRMatcher;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.Grant;
import rocker.grant.projects;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GrantRequirements {
    @Command
    public Grant.Requirement domesticPolicy(DomesticPolicy policy) {
        return new Grant.Requirement("Domestic policy must be set to MANIFEST_DESTINY for city grants: <https://politicsandwar.com/nation/edit/>", false,
                f -> f.getDomesticPolicy() == policy);
    }

    @Command
    public Grant.Requirement project(Set<Project> projects) {
        return new Grant.Requirement("Requires the following projects: " + projects.stream().map(Project::name).collect(Collectors.joining(",")), false,
        f -> {
            for (Project project : projects) {
                if (!f.hasProject(project)) {
                    return false;
                }
            }
            return true;
        });
    }

    @Command
    public Grant.Requirement continent(Set<Continent> continents) {
        return new Grant.Requirement("Requires any of the following continents: " + continents.stream().map(Continent::name).collect(Collectors.joining(",")), false,
                f -> continents.contains(f.getContinent())
        );
    }

    @Command
    public Grant.Requirement minCities(int cities) {
        return new Grant.Requirement("Requires at least " + cities + " cities", false,
                f -> f.getCities() >= cities
        );
    }

    @Command
    public Grant.Requirement maxCities(int cities) {
        return new Grant.Requirement("Requires at most " + cities + " cities", false,
                f -> f.getCities() <= cities
        );
    }

    @Command
    public Grant.Requirement active(@Timediff long time) {
        return new Grant.Requirement("Requires active in past " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, time), false,
                f -> f.lastActiveMs() > System.currentTimeMillis() - time
        );
    }

    @Command
    public Grant.Requirement turnsSinceProject(@Range(min=1) long turns) {
        return new Grant.Requirement("Requires " + TimeUtil.turnsToTime(turns) + " since last project", false,
                f -> TimeUtil.getTurn() - f.getProjectAbsoluteTurn() >= turns
        );
    }

    @Command
    public Grant.Requirement turnsSinceCity(@Range(min=1) long turns) {
        return new Grant.Requirement("Requires " + TimeUtil.turnsToTime(turns) + " since last city", false,
                f -> TimeUtil.getTurn() - f.getCityTimerAbsoluteTurn() >= turns
        );
    }

    @Command
    public Grant.Requirement seniority(@Range(min=1) @Timediff long time) {
        return new Grant.Requirement("Requires " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, time) + " in the alliance", false,
                f -> f.allianceSeniorityMs() >= time
        );
    }

    @Command
    public Grant.Requirement mmr_building(MMRMatcher mmr) {
        return new Grant.Requirement("Requires mmr=" + mmr.toString() + " (building)", false,
                f -> mmr.test(f.getMMRBuildingStr())
        );
    }

    @Command
    public Grant.Requirement mmr_unit(MMRMatcher mmr) {
        return new Grant.Requirement("Requires mmr=" + mmr.toString() + " (units)", false,
                f -> mmr.test(f.getMMR())
        );
    }

    @Command
    public Grant.Requirement color(Set<NationColor> color) {
        return new Grant.Requirement("Requires nation on color bloc: " + StringMan.getString(color), false,
                f -> color.contains(f.getColor())
        );
    }

    @Command
    public Grant.Requirement login_percent(@Range(min=0, max=100) Double percent) {
        return new Grant.Requirement("Requires nation weekly login percent above: " + MathMan.format(percent), false,
                f -> f.avg_daily_login_week() >= (percent / 100d)
        );
    }

    @Command
    public Grant.Requirement login_percent(@Timestamp long validUntil) {
        return new Grant.Requirement("Requires nation weekly login percent above: " + MathMan.format(percent), false,
                f -> f.avg_daily_login_week() >= (percent / 100d)
        );
    }

    // days since last infragrant
    // valid until

    // - CheckRaidsBelowC10
    // - OnlyNewCity
    // You have already received infra of that level for that city
    // Nation is losing", overrideSafe, f -> f.getRelativeStrength() < 1
    // "Nation is on low military", overrideSafe, f -> f.getAircraftPct() < 0.7)
    // "Already received warchest since last war"
    // infra requirements
    // correctMMR
    // mmr requirement
    // mmrunit requirement
    // taxrate requirement
    // tax bracket requirement
    // off requirement
    // def requirement
    // role requirement
    // correct color requirement
    // color requirement

    //
    // unblockaded


    // ---

    // auto add uranium continent to uranium project
    // auto add required projects
    // auto add city requirements (e.g. planning)
}
