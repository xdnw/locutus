package com.boydti.discord.commands.buildcmd;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.pnw.json.CityBuild;
import com.boydti.discord.pnw.json.CityBuildRange;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.discord.DiscordUtil;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Map;

public class AddBuild extends Command {
    public AddBuild() {
        super("addbuild", "setbuild", CommandCategory.ECON, CommandCategory.GOV);
    }

    @Override
    public String help() {
        return "!addbuild [category] [city-min] [city-max] [build json...]";
    }

    @Override
    public String desc() {
        return "Add a build to a category. (ranges are inclusive)";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ECON.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        String content = DiscordUtil.trimContent(event.getMessage().getContentRaw());
        if (args.size() < 3) {
            return usage(event);
        }
        int jsonStart = content.indexOf('{');
        if (jsonStart != -1) {
            // add a build
            String category = args.get(0).toLowerCase();
            String minStr = args.get(1);
            String maxStr = args.get(2).split("\\r?\\n")[0];
            if (!MathMan.isInteger(minStr)) {
                return "Invalid city min: " + minStr;
            }

            if (!MathMan.isInteger(maxStr)) {
                return "Invalid city max: " + maxStr + " | `" + args.get(2) + "`";
            }

            int jsonEnd = content.lastIndexOf('}');
            String buildJson = content.substring(jsonStart, jsonEnd + 1);

            try {
                CityBuild gson = new Gson().fromJson(buildJson, CityBuild.class);
                int min = Integer.parseInt(minStr);
                int max = Integer.parseInt(maxStr);
                Locutus.imp().getGuildDB(event).addBuild(category, min, max, buildJson);

                StringBuilder response = new StringBuilder("Added build: ```" + gson + "```");

                Map<String, List<CityBuildRange>> builds = Locutus.imp().getGuildDB(event).getBuilds();
                List<CityBuildRange> list = builds.get(category);
                for (CityBuildRange range : list) {
                    if (range.getMin() == min) continue;
                    if (Math.max(min, range.getMin()) <= Math.min(max, range.getMax())) {
                        response.append('\n').append(" - Overlaps with (category, min-city, max-city) " + category + " " + range.getMin() + " " + range.getMax() + ". Use `!delbuild " + category + " " + range.getMin() + "`");
                    }
                }
                return response.toString().trim();
            } catch (JsonSyntaxException e) {
                return "Invalid build json: " + e.getMessage();
            }
        }
        return usage(event, "No build json provided");
    }
}