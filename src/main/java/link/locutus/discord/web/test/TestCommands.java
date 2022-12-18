package link.locutus.discord.web.test;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.util.RateLimitUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;

public class TestCommands {

    @Command(desc = "Dummy command. No output")
    public String dummy() {
        return null;
    }

    @Command
    public String embedTest(@Me MessageChannel channel) {
        Message msg = new MessageBuilder().setEmbeds(new EmbedBuilder().setTitle("test1").build(), new EmbedBuilder().setTitle("test2").build()).append("hello world 1").append("\n\nhello world 2").build();
        RateLimitUtil.queue(channel.sendMessage(msg));
        return "Done!";
    }
}