package link.locutus.discord.commands.buildcmd;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DeleteBuild extends Command {
    public DeleteBuild() {
        super("delbuild", "removebuild", "deletebuild", CommandCategory.ECON, CommandCategory.GOV);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.build.delete.cmd);
    }
    @Override
    public String help() {
        return Settings.commandPrefix(true) + "delbuild <category> <min-cities>";
    }

    @Override
    public String desc() {
        return "Delete a build registered in a specific category with the provided min-cities.";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ECON.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() == 2) {
            if (!MathMan.isInteger(args.get(1))) {
                return "Not an integer. `" + args.get(1) + "`";
            }
            Locutus.imp().getGuildDB(guild).removeBuild(args.get(0).toLowerCase(Locale.ROOT), Integer.parseInt(args.get(1)));
            return "Removed build: `" + args.get(0) + "`";
        }
        return usage(args.size(), 2, channel);
    }
}
