package link.locutus.discord.commands.info;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.task.nation.MultiReport;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;

public class Multi extends Command {
    public Multi() {
        super(CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.nation.list.multi.cmd);
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
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) {
            return usage(args.size(), 1, channel);
        }
        DBNation nation = DiscordUtil.parseNation(args.get(0), true, true, guild);
        if (nation == null) {
            return "Invalid nation: `" + args.get(0) + "`";
        }
        MultiReport report = new MultiReport(nation.getId());
        String result = report.toString();

        String title = nation.getName() + " multi report";
        IMessageBuilder msg = channel.create();
        boolean attachFile = true;
        if (result.length() + title.length() > MessageEmbed.EMBED_MAX_LENGTH_BOT || result.length() > MessageEmbed.DESCRIPTION_MAX_LENGTH) {
            String condensed = report.toString(true);
            if (condensed.length() + title.length() <= MessageEmbed.EMBED_MAX_LENGTH_BOT && condensed.length() <= MessageEmbed.DESCRIPTION_MAX_LENGTH) {
                msg.embed( nation.getName(), condensed);
            }
        } else {
            msg.embed( nation.getName(), result);
            attachFile = false;
        }

        if (!attachFile) {
            msg.file(title + ".txt", result);
        }

        msg.append("""
            ```Disclaimer:
            - Sharing networks does not mean they are the same person (mobile networks, schools, public wifi, vpns, dynamic ips)
            - A network not shared 'concurrently' or within a short timeframe may be a false positive
            - Having many networks, but only a few shared may be a sign of a VPN being used (there are legitimate reasons for using a VPN)```""");

        if (Settings.INSTANCE.ENABLED_COMPONENTS.WEB) {
            msg.append("\n**See also:** " + Settings.INSTANCE.WEB.FRONTEND_DOMAIN + "/#/multi_v2/" + nation.getId());
        }

        msg.send();
        return null;
    }
}
