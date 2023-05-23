package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.commands.buildcmd.AssignBuild;
import link.locutus.discord.commands.buildcmd.GetBuild;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.pnw.json.CityBuildRange;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class BuildCommands {
    // delete
    // get
    // assign

    @Command(desc = "List the currently set build categories")
    @RolePermission(Roles.MEMBER)
    public String listall(@Me GuildDB db) {
        Map<String, List<CityBuildRange>> builds = db.getBuilds();
        StringBuilder sb = new StringBuilder();
        sb.append("**Build Categories**:\n");
        for (Map.Entry<String, List<CityBuildRange>> entry : builds.entrySet()) {
            sb.append("**").append(entry.getKey()).append("**:\n");
            for (CityBuildRange range : entry.getValue()) {
                sb.append("- ").append(range.getMin()).append("-").append(range.getMin()).append("\n");
            }
        }
        return sb.toString();
    }

    @Command(desc = "Add a build to a category with the specified city ranges")
    @RolePermission(Roles.ECON)
    public String add(@Me GuildDB db, String category, CityRanges ranges, CityBuild build) {
        List<CityBuildRange> existingRanges = db.getBuilds().getOrDefault(category, new ArrayList<>());
        for (Map.Entry<Integer, Integer> range : ranges.getRanges()) {
            db.addBuild(category, range.getKey(), range.getValue(), build.toCompressedString());
        }
        StringBuilder response = new StringBuilder("Added build: ```" + build + "```");

        for (CityBuildRange existing : existingRanges) {
            for (Map.Entry<Integer, Integer> range : ranges.getRanges()) {
                if (Math.max(range.getKey(), existing.getMin()) <= Math.min(range.getValue(), existing.getMax())) {
                    response.append('\n').append("- Overlaps with (category, min-city, max-city) ").append(category).append(" ").append(existing.getMin()).append(" ").append(existing.getMax()).append(". Use ").append(CM.build.delete.cmd.create(category, existing.getMin() + "").toSlashCommand()).append(" to delete it.");
                }
            }
        }
        return response.toString();
    }

    @Command(desc = "Delete a build registered in a specific category with the provided min-cities")
    @RolePermission(Roles.ECON)
    public String delete(@Me GuildDB db, String category, int minCities) {
        Map<String, List<CityBuildRange>> builds = db.getBuilds();
        if (!builds.containsKey(category)) {
            return "No builds found in category: `" + category + "`. Options: " + StringMan.getString(builds.keySet());
        }
        List<CityBuildRange> existingRanges = builds.getOrDefault(category, Collections.emptyList());
        List<Integer> minCitiesList = existingRanges.stream().map(CityBuildRange::getMin).collect(Collectors.toList());
        if (!minCitiesList.contains(minCities))
            return "No build found with min-cities: `" + minCities + "`. Options: " + StringMan.getString(minCitiesList);
        db.removeBuild(category, minCities);
        return "Deleted builds with min-cities: `" + minCities + "`";
    }

    @Command(desc = "Have the bot provide a pre set build based on city count")
    @RolePermission(Roles.MEMBER)
    public String assign(@Me IMessageIO io, @Me GuildDB db, String category, @Default("%user%") DBNation nation, @Default Integer cities) throws IOException, ExecutionException, InterruptedException {
        return AssignBuild.build(io, db, nation, cities == null ? nation.getCities() : cities, category);
    }

    @Command(desc = "Print the current city builds being used by a nation")
    @RolePermission(Roles.MEMBER)
    public String get(DBNation nation, @Me IMessageIO channel) throws Exception {
        return GetBuild.onCommand(nation, channel);
    }
}
