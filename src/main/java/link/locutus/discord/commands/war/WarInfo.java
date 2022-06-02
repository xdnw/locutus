package link.locutus.discord.commands.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.pnw.DBNation;
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
    public String help() {
        return Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "WarInfo <war-link|nation>";
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
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (flags.contains('l') && !Locutus.imp().getGuildDB(guild).isWhitelisted()) {
            return "No permission for `-l`";
        }
        if (args.size() != 1) {
            return usage(event);
        }
        String arg0 = args.get(0);
        if (arg0.contains("/war=")) {
            arg0 = arg0.split("war=")[1];
        }
        Integer warId = MathMan.parseInt(arg0);
        if (warId == null || Locutus.imp().getWarDb().getWar(warId) == null) {
            DBNation nation = DiscordUtil.parseNation(args.get(0));
            if (nation == null) return "Invalid warId: " + warId;
            List<DBWar> wars = nation.getActiveWars();
            String title = wars.size() + " wars";
            String body = nation.getWarInfoEmbed(flags.contains('l'));
            DiscordUtil.createEmbedCommand(event.getChannel(), title, body);
        } else {
            new WarCard(warId).embed(event.getChannel(), true);
        }
        return null;
    }

}