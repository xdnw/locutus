package link.locutus.discord.commands.fun;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.util.FileUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class Jokes extends Command {
    private final String[] lines;

    public Jokes() {
        super("joke", "pun", CommandCategory.FUN);
        this.lines = Objects.requireNonNull(FileUtil.readFile("/fun/jokes.txt")).split("\\r?\\n");
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        return lines[ThreadLocalRandom.current().nextInt(lines.length)];
    }
}
