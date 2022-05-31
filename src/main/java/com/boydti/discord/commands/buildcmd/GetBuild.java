package com.boydti.discord.commands.buildcmd;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.config.Settings;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.pnw.json.CityBuild;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.MarkupUtil;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.task.balance.GetCityBuilds;
import com.google.api.client.util.Lists;
import com.google.common.collect.Maps;
import com.boydti.discord.apiv1.enums.city.JavaCity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetBuild extends Command {
    public GetBuild() {
        super("getbuild", CommandCategory.ECON, CommandCategory.MEMBER);
    }

    @Override
    public String help() {
        return "!getbuild <nation>";
    }

    @Override
    public String desc() {
        return "Print the current build being used by a nation";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        DBNation me = DiscordUtil.getNation(event);
        if (me == null) {
            return "Invalid nation? Are you sure you are registered?";
        }

        Integer id = DiscordUtil.parseNationId(args.get(0));
        if (id == null) {
            return "Not found: `!pnw-who <user>`";
        }
        DBNation nation = Locutus.imp().getNationDB().getNation(id);
        if (nation == null) {
            return "Nation not found: `" + args.get(0) + "`";
        }
        Map<DBNation, Map<Integer, JavaCity>> builds = new GetCityBuilds(nation).adapt(i -> {});
        Map<DBNation, Map<CityBuild, List<String>>> uniqueBuilds = new HashMap<>();
        for (Map.Entry<DBNation, Map<Integer, JavaCity>> nationEntry : builds.entrySet()) {
            Map<CityBuild, List<String>> nMap = uniqueBuilds.computeIfAbsent(nationEntry.getKey(), i -> Maps.newHashMap());
            for (Map.Entry<Integer, JavaCity> entry : nationEntry.getValue().entrySet()) {
                int cityId = entry.getKey();
                String url = "" + Settings.INSTANCE.PNW_URL() + "/city/id=" + cityId;
                String link = MarkupUtil.markdownUrl(cityId + "", url);
                JavaCity build = entry.getValue();
                nMap.computeIfAbsent(build.toCityBuild(), i -> Lists.newArrayList()).add(link);
            }
        }

        for (Map.Entry<DBNation, Map<CityBuild, List<String>>> entry : uniqueBuilds.entrySet()) {
            event.getChannel().sendMessage(nation.getNation() + " has " + entry.getValue().size() + " unique builds in " + nation.getCities() + " cities:").complete();

            Map<CityBuild, List<String>> cityPair = entry.getValue();
            nation = entry.getKey();
            for (Map.Entry<CityBuild, List<String>> cityEntry : cityPair.entrySet()) {
                String title = entry.getKey().getNation();
                StringBuilder response = new StringBuilder(cityEntry.getValue().size() + " cities");
                String cityStr = StringMan.getString(cityEntry.getValue());
                response.append('\n').append(cityStr)
                        .append("```")
                        .append(cityEntry.getKey().toString())
                        .append("```");

                DiscordUtil.createEmbedCommand(event.getChannel(), title, response.toString());
            }
        }

        return null;
    }
}
