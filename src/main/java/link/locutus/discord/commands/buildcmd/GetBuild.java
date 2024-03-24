package link.locutus.discord.commands.buildcmd;

import com.google.api.client.util.Lists;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
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
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GetBuild extends Command {
    public GetBuild() {
        super("getbuild", CommandCategory.ECON, CommandCategory.MEMBER);
    }

    public static String onCommand(DBNation nation, IMessageIO channel) throws Exception {
        Map<DBNation, Map<Integer, JavaCity>> builds = Collections.singletonMap(nation, nation.getCityMap(true));
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
            List<Map.Entry<String, String>> buildStr = new ObjectArrayList<>();
            for (Map.Entry<CityBuild, List<String>> cityEntry : cityPair.entrySet()) {
                String title = cityEntry.getValue().size() + " cities";
                StringBuilder response = new StringBuilder();
                String cityStr = StringMan.getString(cityEntry.getValue());
                response.append(cityStr)
                        .append("```")
                        .append(cityEntry.getKey().toString())
                        .append("```");
                buildStr.add(Map.entry(title, response.toString()));
            }
            if (buildStr.size() <= 10) {
                for (Map.Entry<String, String> build : buildStr) {
                    msg.embed(build.getKey(), build.getValue());
                }
            } else {
                for (Map.Entry<String, String> build : buildStr) {
                    msg.append("### " + build.getKey() + ":\n" + build.getValue() + "\n");
                }
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
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        
        if (me == null) {
            return "Invalid nation, Are you sure you are registered?" + author.getAsMention();
        }

        Integer id = DiscordUtil.parseNationId(args.get(0));
        if (id == null) {
            return "Not found: `" + Settings.commandPrefix(true) + "pnw-who <user>`";
        }
        DBNation nation = Locutus.imp().getNationDB().getNation(id);
        if (nation == null) {
            return "Nation not found: `" + args.get(0) + "`";
        }
        return onCommand(nation, channel);
    }
}
