package link.locutus.discord.commands.external.guild;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class Permission extends Command {
    public Permission() {
        super(CommandCategory.LOCUTUS_ADMIN);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "permission <command> <value> [guild]";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) {
            return usage(args.size(), 2, channel);
        }
        if (!MathMan.isInteger(args.get(1))) {
            return "Invalid number: `" + args.get(1) + "`";
        }

        Map<String, Command> cmds = Locutus.imp().getCommandManager().getCommandMap();
        Command cmd = cmds.get(args.get(0).toLowerCase());
        if (cmd == null) {
            for (Command current : cmds.values()) {
                if (current.getClass().getSimpleName().equalsIgnoreCase(args.get(0))) {
                    cmd = current;
                    break;
                }
            }
        }
        if (cmd == null) {
            return "Invalid cmd: `" + args.get(0) + "`" + ". For a list of commands use `" + Settings.commandPrefix(true) + "?`";
        }
        GuildDB db;
        if (args.size() >= 3) {
            long id = Long.parseLong(args.get(2));
            if (id < Integer.MAX_VALUE) {
                db = Locutus.imp().getGuildDBByAA((int) id);
            } else {
                db = Locutus.imp().getGuildDB(id);
            }
        } else {
            db = Locutus.imp().getGuildDB(guild);
        }
        int value = Integer.parseInt(args.get(1));
        assert db != null;
        db.setPermission(cmd.getClass(), value);
        return "Set " + cmd.getAliases().get(0) + " to " + value + " for " + db.getGuild().getName();
    }
}
