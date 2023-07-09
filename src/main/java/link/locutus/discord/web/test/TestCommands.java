package link.locutus.discord.web.test;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.command.ICommand;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import java.util.List;
import java.util.Map;

public class TestCommands {

    @Command(desc = "Dummy command. No output")
    public String dummy() {
        return null;
    }

    @Command
    public String modal(@Me IMessageIO io, String title, ICommand command, String defaults, List<String> arguments) {
        // parse defaults to a Map<String, String>
        Map<String, String> args = PnwUtil.parseMap(defaults);
        io.modal().create(command, args, arguments).send();
        return null;
    }
}