package link.locutus.discord.commands.buildcmd;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class DeleteBuild extends Command {
    public DeleteBuild() {
        super("delbuild", "removebuild", "deletebuild", CommandCategory.ECON, CommandCategory.GOV);
    }

    @Override
    public String help() {
        return Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "delbuild <category> <min-cities>";
    }

    @Override
    public String desc() {
        return "Delete a build registered in a specific category with the provided min-cities";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ECON.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        switch (args.size()) {
            default:
                return usage(event);
            case 2:
                if (!MathMan.isInteger(args.get(1))) {
                    return "Not an integer. `" + args.get(1) + "`";
                }
                Locutus.imp().getGuildDB(event).removeBuild(args.get(0), Integer.parseInt(args.get(1)));
                return "Removed build: `" + args.get(0) + "`";
        }
    }
}
