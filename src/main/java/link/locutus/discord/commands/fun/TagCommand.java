package link.locutus.discord.commands.fun;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.CommandManager;
import link.locutus.discord.config.Settings;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class TagCommand extends Command {
    private final CommandManager manager;

    public TagCommand(CommandManager manager) {
        super("tag", "titan", CommandCategory.FUN);
        this.manager = manager;
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "tag";
    }

    @Override
    public String desc() {
        return "Play a game of tag";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        String tagResult = manager.getTag().tag(event);
        if (tagResult != null) {
            return tagResult;
        }
        User it = event.getJDA().getUserById(manager.getTag().getIt());
        if (it != null) return it.getName() + " is it!";
        return "No-one is playing tag right now";
//        String msg = random.get(new Random().nextInt(random.size()));
//        return msg;
    }
}
