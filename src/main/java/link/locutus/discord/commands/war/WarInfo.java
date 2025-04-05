package link.locutus.discord.commands.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.shrink.EmbedShrink;
import link.locutus.discord.commands.manager.v2.command.shrink.IShrink;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.task.war.WarCard;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class WarInfo extends Command {
    public WarInfo() {
        super("warinfo", CommandCategory.MILCOM);
    }
    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.war.info.cmd, CM.war.card.cmd);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "WarInfo <war-link|nation>";
    }

    @Override
    public String desc() {
        StringBuilder info = new StringBuilder();
        info.append("Generate a war card, or war summary of a nation\n\n");
        info.append("my war info | enemy war info\n");
        info.append("`\u26F5` = blockaded\n");
        info.append("`\u2708` = air control\n");
        info.append("`\uD83D\uDC82` = ground control\n");
        info.append("`\uD83C\uDFF0` = fortified\n");
        info.append("`\u2764` = offering peace\n");
        info.append("X/12 = Military Action Points\n");
        info.append("Y% = Resistance\n\n");
        info.append("Add `-l` to show estimated raid loot (if showing nation war info)");
        return info.toString();
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) {
            return usage(args.size(), 1, channel);
        }
        String arg0 = args.get(0);
        if (arg0.contains("/war=")) {
            arg0 = arg0.split("war=")[1];
        }
        Integer warId = MathMan.parseInt(arg0);
        if (warId == null || Locutus.imp().getWarDb().getWar(warId) == null) {
            DBNation nation = DiscordUtil.parseNation(args.get(0));
            if (nation == null) return "Invalid warId: " + warId;
            Set<DBWar> wars = nation.getActiveWars();
            String title = wars.size() + " wars";
            IShrink body = nation.getWarInfoEmbed(flags.contains('l'));
            EmbedShrink embed = new EmbedShrink().title(title).append(body);
            channel.create().embed(embed).send();
        } else {
            new WarCard(warId).embed(channel, true, false);
        }
        return null;
    }

}