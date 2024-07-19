package link.locutus.discord.commands.alliance;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.offshore.Auth;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class SendTreaty extends Command {
    public SendTreaty() {
        super(CommandCategory.GOV, CommandCategory.FOREIGN_AFFAIRS);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.FOREIGN_AFFAIRS.has(user, server);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.treaty.send.cmd);
    }
    @Override
    public String help() {
        return super.help() + " <alliance> <type> <days> <message>";
    }

    @Override
    public String desc() {
        return "Send a treaty to an alliance.";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 3) return usage(args.size(), 3, channel);
        GuildDB db = Locutus.imp().getGuildDB(guild);

        Integer aaId = PW.parseAllianceId(args.get(0));
        TreatyType type;
        try {
            type = TreatyType.parse(args.get(1));
        } catch (IllegalArgumentException e) {
            return "Invalid treaty type: `" + args.get(1) + "`. Options: " + StringMan.getString(TreatyType.values());
        }
        int days = Integer.parseInt(args.get(2));
        args.subList(0, 3).clear();
        String message = StringMan.join(args, " ");
        if (message.isEmpty() && !Roles.ADMIN.has(author, guild)) {
            return "Admin is required to send a treaty with a message.";
        }
        return FACommands.sendTreaty(author, db, db.getAllianceList(), DBAlliance.getOrCreate(aaId), type, days, message);
    }
}
