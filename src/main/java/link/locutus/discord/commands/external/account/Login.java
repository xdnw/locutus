package link.locutus.discord.commands.external.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;

public class Login extends Command {

    public Login() {
        super(CommandCategory.USER_SETTINGS);
    }


    @Override
    public String help() {
        return super.help() + " <username> <password>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        String[] split = DiscordUtil.trimContent(fullCommandRaw).split(" ", 3);
        String username = split[1];
        String password = split[2];
        return UnsortedCommands.login(channel, Locutus.imp().getDiscordDB(), me, username, password);
    }
}