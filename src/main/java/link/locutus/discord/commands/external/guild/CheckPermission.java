package link.locutus.discord.commands.external.guild;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class CheckPermission extends Command {
    public CheckPermission() {
        super(CommandCategory.GUILD_MANAGEMENT, CommandCategory.USER_INFO);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "checkpermission <command> <user>";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 2) return usage();
        User user = DiscordUtil.getUser(args.get(1));
        if (user == null) return "Unknown user: `" + args.get(1) + "`";
        Command cmd = Locutus.imp().getCommandManager().getCommandMap().get(args.get(0));
        if (cmd == null) return "Unknown command: `" + args.get(0) + "`";
        boolean result = cmd.checkPermission(guild, user);
        return "" + result;
    }
}
