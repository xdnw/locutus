package link.locutus.discord.commands.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.Noformat;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;

public class Runall extends Command implements Noformat {

    public Runall() {
        super(CommandCategory.GENERAL_INFO_AND_TOOLS);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.admin.command.multiple.cmd);
    }

    @Override
    public String help() {
        return super.help() + " <command>";
    }

    @Override
    public String desc() {
        return "Run multiple commands at a time (put each new command on a new line)";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        String msg = DiscordUtil.trimContent(fullCommandRaw);
        msg = msg.replaceAll(Settings.commandPrefix(true) + "runall " + Settings.commandPrefix(true), "");
        String[] split = msg.split("\\r?\\n" + Settings.commandPrefix(true));
        StringBuilder response = new StringBuilder();
        for (String s : split) {
            String cmd = Settings.commandPrefix(true) + s;
            if (cmd.toLowerCase().startsWith(Settings.commandPrefix(true) + "runall")) continue;
            response.append("### " + (cmd.split(" ")[0]) + ":\n>>> " + Locutus.imp().getCommandManager().run(guild, channel, cmd, me, author) + "\n");
        }
        response.append("\nDone!");
        return response.toString();
    }
}