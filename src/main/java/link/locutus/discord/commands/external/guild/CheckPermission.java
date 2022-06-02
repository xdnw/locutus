package link.locutus.discord.commands.external.guild;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class CheckPermission extends Command {
    public CheckPermission() {
        super(CommandCategory.GUILD_MANAGEMENT, CommandCategory.USER_INFO);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String help() {
        return Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "checkpermission <command> <user>";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.size() != 2) return usage();
        List<User> mentions = event.getMessage().getMentionedUsers();
        boolean result = Locutus.imp().getCommandManager().getCommandMap().get(args.get(0)).checkPermission(DiscordUtil.getDefaultGuild(event), mentions.get(0));
        return "" + result;
    }
}
