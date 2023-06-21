package link.locutus.discord.commands.alliance;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliancePosition;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class SetRank extends Command {
    public SetRank() {
        super("rank", "setrank", "rankup", CommandCategory.GOV, CommandCategory.INTERNAL_AFFAIRS);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "setrank <user> <rank>";
    }

    @Override
    public String desc() {
        return "Set the rank of a player in the alliance. Ranks: " + StringMan.getString(Rank.values()) + "\n" +
                "Add `-d` to not set rank on discord";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        GuildDB db = Locutus.imp().getGuildDB(server);
        return db.isValidAlliance() && (Roles.INTERNAL_AFFAIRS.has(user, server) || Roles.INTERNAL_AFFAIRS_STAFF.has(user, server));
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 2) return usage();
        GuildDB db = Locutus.imp().getGuildDB(guild);
        DBNation nation = DiscordUtil.parseNation(args.get(0));
        DBAlliancePosition position = PWBindings.position(db, me, args.get(1));
        return IACommands.setRank(author, channel, db, me, nation, position, flags.contains('f'), flags.contains('d'));
    }
}
