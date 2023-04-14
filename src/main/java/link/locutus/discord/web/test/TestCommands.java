package link.locutus.discord.web.test;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
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
    public String embed(@Me IMessageIO io, int myInput) {
        CM.who cmd = CM.who.cmd.create("Borg", null, null, null, null, null, null, null, null);
        String label = "mylabel";
        io.create().embed("Title", "body: " + myInput).commandButton(cmd, label).send();
        return "Done!";
    }
}