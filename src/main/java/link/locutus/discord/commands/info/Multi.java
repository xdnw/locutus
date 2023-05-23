package link.locutus.discord.commands.info;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.task.nation.MultiReport;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class Multi extends Command {
    public Multi() {
        super(CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + getClass().getSimpleName() + " <nation>";
    }

    @Override
    public String desc() {
        return "Check if a nation has a multi account.";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.isEmpty()) {
            return usage(event);
        }
        Integer nationId = DiscordUtil.parseNationId(args.get(0));
        if (nationId == null) {
            return "Invalid nation: `" + args.get(0) + "`";
        }
        MultiReport report = new MultiReport(nationId);
        String result = report.toString();

        String title = PnwUtil.getName(nationId, false) + " multi report";
        if (result.length() + title.length() >= 2000) {
            String condensed = report.toString(true);
            if (condensed.length() + title.length() < 2000) {
                DiscordUtil.createEmbedCommand(event.getChannel(), PnwUtil.getName(nationId, false), condensed);
            }
        }

        DiscordUtil.createEmbedCommand(event.getChannel(), title, result);

        return """
                ```Disclaimer:
                - Sharing networks does not mean they are the same person (mobile networks, schools, public wifi, vpns, dynamic ips)
                - A network not shared 'concurrently' or within a short timeframe may be a false positive
                - Having many networks, but only a few shared may be a sign of a VPN being used (there are legitimate reasons for using a VPN)
                - It is against game rules to use evidence to threaten or coerce others
                See: https://politicsandwar.com/rules/```""";
    }
}
