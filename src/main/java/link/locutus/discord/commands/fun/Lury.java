package link.locutus.discord.commands.fun;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.util.FileUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Lury extends Command {
    public Lury() {
        super(CommandCategory.FUN);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String help() {
        return "!lury";
    }

    @Override
    public String desc() {
        return "sunq ";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        String[] lines = FileUtil.readFile("/fun/overlord.txt").split("\\r?\\n");
        return lines[ThreadLocalRandom.current().nextInt(lines.length)];
    }
}
