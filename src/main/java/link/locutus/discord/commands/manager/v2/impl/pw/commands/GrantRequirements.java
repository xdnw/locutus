package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timediff;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.MMRMatcher;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.Grant;

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
    public Grant.Requirement mmr_correct() {
        return new Grant.Requirement("Requires the correct MMR (see " + CM.settings.info.cmd.toSlashMention() + " with key " + GuildKey.REQUIRED_MMR.name(), false,
                DBNation::correctAllianceMMR
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
    public Grant.Requirement no_losing_wars() {
        return new Grant.Requirement("Nation is losing a war", false,
                f -> f.getRelativeStrength() < 1
        );
    }

    @Command
    public Grant.Requirement avg_land(int amount) {
        return new Grant.Requirement("Nation requires an average of " + amount + " land", false,
                f -> f.getAvgLand() >= amount
        );
    }

    @Command
    public Grant.Requirement avg_infra(int amount) {
        return new Grant.Requirement("Nation requires an average of " + amount + " infrastructure", false,
                f -> f.getAvg_infra() >= amount
        );
    }

    // days since last infragrant
    // valid until

    // - CheckRaidsBelowC10
    // - OnlyNewCity
    // You have already received infra of that level for that city
    // Nation is losing", overrideSafe, f -> f.getRelativeStrength() < 1
    // "Already received warchest since last war"
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
    // valid until

    // total
    // total_per_banker
    // daily_per_banker_daily
    // check timeframe 3d


}
