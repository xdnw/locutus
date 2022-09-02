package link.locutus.discord.commands.buildcmd;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.pnw.json.CityBuildRange;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.task.balance.GetCityBuilds;
import com.google.gson.Gson;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class AssignBuild extends Command {
    public AssignBuild() {
        super("AssignBuild", "build", CommandCategory.ECON, CommandCategory.MEMBER);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "build [category]";
    }

    @Override
    public String desc() {
        return "Have the bot give you a build for war or raiding, based on your city count. Available categories are: `" + Settings.commandPrefix(true) + "build ?`";
    }


    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {

        DBNation me = DiscordUtil.getNation(event);
        if (me == null) {
            return "Invalid nation? Are you sure you are registered?";
        }

        if (args.size() != 1) {
            return usage(event);
        }
        GuildDB db = Locutus.imp().getGuildDB(event);
        String result = build(db, me, me.getCities(), args.get(0));

        return result;
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server);
    }

    public static String build(GuildDB db, DBNation me, int cities, String arg) throws InterruptedException, ExecutionException, IOException {
        JavaCity to = null;

        if (arg.contains("/city/")) {
//            cityId = Integer.parseInt(content.split("=")[1]);
//            City pnwCity = Locutus.imp().getPnwApi().getCity(cityId);
//            from = new JavaCity(pnwCity);
            throw new IllegalArgumentException("Not implemented");
        }
        else if (arg.charAt(0) == '{') {
            String buildJson = arg;
            CityBuild build = new Gson().fromJson(buildJson, CityBuild.class);
            to = new JavaCity(build);
        } else {
            String category = arg.toLowerCase();
            Map<String, List<CityBuildRange>> builds = db.getBuilds();
            if (!builds.containsKey(category)) {
                throw new IllegalArgumentException("No category for: " + category + ". Available categories are: " + StringMan.getString(db.getBuilds().keySet()));
            }

            List<CityBuildRange> list = builds.get(category);
            for (CityBuildRange range : list) {
                if (cities >= range.getMin() && cities <= range.getMax()) {
                    to = new JavaCity(range.getBuildGson());
                    break;
                }
            }
            if (to == null) {
                throw new IllegalArgumentException("Invalid build: " + arg);
            }
        }

        double[] totalArr = new double[ResourceType.values.length];
        Map<Integer, JavaCity> from = me.getCityMap(true);
        return to.instructions(from, totalArr);
    }
}
