package link.locutus.discord.commands.buildcmd;

import com.google.gson.JsonSyntaxException;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.pnw.json.CityBuildRange;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.web.WebUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AddBuild extends Command {
    public AddBuild() {
        super("addbuild", "setbuild", CommandCategory.ECON, CommandCategory.GOV);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.build.add.cmd);
    }
    @Override
    public String help() {
        return Settings.commandPrefix(true) + "addbuild [category] [city-min] [city-max] [build json...]";
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
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        String content = DiscordUtil.trimContent(fullCommandRaw);
        if (args.size() < 3) {
            return usage(args.size(), 3, channel);
        }
        int jsonStart = content.indexOf('{');
        if (jsonStart != -1) {
            // add a build
            String category = args.get(0).toLowerCase(Locale.ROOT);
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
                CityBuild gson = WebUtil.GSON.fromJson(buildJson, CityBuild.class);
                int min = Integer.parseInt(minStr);
                int max = Integer.parseInt(maxStr);
                Locutus.imp().getGuildDB(guild).addBuild(category, min, max, buildJson);

                StringBuilder response = new StringBuilder("Added build: ```" + gson + "```");

                Map<String, List<CityBuildRange>> builds = Locutus.imp().getGuildDB(guild).getBuilds();
                List<CityBuildRange> list = builds.get(category);
                for (CityBuildRange range : list) {
                    if (range.getMin() == min) continue;
                    if (Math.max(min, range.getMin()) <= Math.min(max, range.getMax())) {
                        response.append('\n').append("- Overlaps with (category, min-city, max-city) ").append(category).append(" ").append(range.getMin()).append(" ").append(range.getMax()).append(". Use `").append(Settings.commandPrefix(true)).append("delbuild ").append(category).append(" ").append(range.getMin()).append("`");
                    }
                }
                return response.toString().trim();
            } catch (JsonSyntaxException e) {
                return "Invalid build json: " + e.getMessage();
            }
        }
        return usage("No build json provided.", channel);
    }
}
