package link.locutus.discord.commands.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.Noformat;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;

public class Sudo extends Command implements Noformat {
    public Sudo() {
        super("sudo", CommandCategory.LOCUTUS_ADMIN);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.admin.sudo.user.cmd);
    }
    @Override
    public String help() {
        return Settings.commandPrefix(true) + "sudo <user> <command>";
    }

    @Override
    public String desc() {
        return "Run a command as another user.";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) {
            return usage(args.size(), 2, channel);
        }
        String arg0 = args.get(0);
        String content = DiscordUtil.trimContent(fullCommandRaw);
        int start = content.indexOf(' ', content.indexOf(' ') + 1);

        String command = content.substring(start + 1);

        User user = DiscordUtil.getMention(arg0);

        Command cmd = Locutus.imp().getCommandManager().getCommandMap().get(args.get(1).substring(1).toLowerCase());
        if (cmd == null) {
            return "Unknown command: ``" + args.get(1) + "`" + "`";
        }

        DBNation nation;
        if (user == null) {
            Integer nationId = DiscordUtil.parseNationId(arg0);

            if (nationId == null) {
                return "Invalid nation: `" + arg0 + "`";
            }
            nation = Locutus.imp().getNationDB().getNationById(nationId);
        } else {
            nation = DiscordUtil.getNation(user);
            if (nation == null) {
                return "Invalid nation: `" + arg0 + "`";
            }
            if (!Roles.ADMIN.hasOnRoot(author) || !cmd.checkPermission(guild, user)) {
                user = null;
            }
        }

        User finalUser = user;
        DiscordUtil.withNation(nation, () -> {
            Locutus.imp().getCommandManager().run(guild, channel, finalUser, command, false, true);
            return null;
        });
        return null;
    }

}
