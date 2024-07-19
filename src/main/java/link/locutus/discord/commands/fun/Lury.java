package link.locutus.discord.commands.fun;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.FileUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class Lury extends Command {
    public Lury() {
        super(CommandCategory.FUN);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of();
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "lury";
    }

    @Override
    public String desc() {
        return "sunq ";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        String[] lines = Objects.requireNonNull(FileUtil.readFile("/fun/overlord.txt")).split("\\r?\\n");
        return lines[ThreadLocalRandom.current().nextInt(lines.length)];
    }
}
