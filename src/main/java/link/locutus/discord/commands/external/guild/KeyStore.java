package link.locutus.discord.commands.external.guild;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.Noformat;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.SettingCommands;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.*;

public class KeyStore extends Command implements Noformat {
    public KeyStore() {
        super(CommandCategory.GUILD_MANAGEMENT, "keystore", "settings");
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.hasAny(user, server, Roles.ADMIN);
    }

    @Override
    public String help() {
        return "" + Settings.commandPrefix(true) + "KeyStore <key> <value>";
    }

    @Override
    public String desc() {
        return "Use `" + Settings.commandPrefix(true) + "KeyStore <key>` for info about a setting\n" +
                "Use `" + Settings.commandPrefix(true) + "KeyStore <key> null` to remove a setting\n" +
                "Add `-a` to list all settings.";
    }


    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.settings.info.cmd);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        return onCommand(channel, guild, author, me, args, flags);
    }

    public String onCommand(IMessageIO io, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (me == null) return "Please use " + CM.register.cmd.toSlashMention() + "";

        GuildSetting setting = null;
        if (args.size() > 0) {
            setting = GuildKey.valueOf(args.get(0).toUpperCase());
        }
        String value = null;
        if (args.size() == 2) {
            value = args.get(1);
        } else if (args.size() > 2) {
            value = StringMan.join(args.subList(1, args.size()), " ");
        }
        boolean listAll = flags.contains('a');
        return SettingCommands.info(guild, author, setting, value, listAll);
    }
}