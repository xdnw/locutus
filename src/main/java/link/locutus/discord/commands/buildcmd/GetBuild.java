package link.locutus.discord.commands.buildcmd;

import com.google.api.client.util.Lists;
import com.google.common.collect.Maps;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.task.balance.GetCityBuilds;
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

    public static String onCommand(DBNation nation, IMessageIO channel) throws Exception {
        Map<DBNation, Map<Integer, JavaCity>> builds = new GetCityBuilds(nation).adapt(i -> {
        });
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

        IMessageBuilder msg = channel.create();
        for (Map.Entry<DBNation, Map<CityBuild, List<String>>> entry : uniqueBuilds.entrySet()) {
            msg.append(nation.getNation() + " has " + entry.getValue().size() + " unique builds in " + nation.getCities() + " cities:");

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

                msg.embed(title, response.toString());
            }
        }
        msg.send();

        return null;
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "getbuild <nation>";
    }

    @Override
    public String desc() {
        return "Print the current build being used by a nation.";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        DBNation me = DiscordUtil.getNation(event);
        if (me == null) {
            return "Invalid nation, Are you sure you are registered?" + event.getAuthor().getAsMention();
        }

        Integer id = DiscordUtil.parseNationId(args.get(0));
        if (id == null) {
            return "Not found: `" + Settings.commandPrefix(true) + "pnw-who <user>`";
        }
        DBNation nation = Locutus.imp().getNationDB().getNation(id);
        if (nation == null) {
            return "Nation not found: `" + args.get(0) + "`";
        }
        return onCommand(nation, new DiscordChannelIO(event));
    }
}
