package link.locutus.discord.commands.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.gpt.GptHandler;
import link.locutus.discord.gpt.ModerationResult;
import link.locutus.discord.gpt.pw.PWGPTHandler;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;

public class Say extends Command {
    public Say() {
        super(CommandCategory.GENERAL_INFO_AND_TOOLS);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) {
            return null;
        }
        String msg = DiscordUtil.trimContent(fullCommandRaw);
        msg = msg.replace("@", "@\u200B");
        msg = msg.replace("&", "&\u200B");

        GPTUtil.checkThrowModeration(msg);
        NationPlaceholders formatter = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
        return formatter.format2(guild, me, author, msg.substring(5) + "\n\n- " + author.getAsMention(), me, false);
    }
}
