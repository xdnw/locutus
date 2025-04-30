package link.locutus.discord.commands.trade.subbank;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;

public class UnsubBank extends Command {
    public UnsubBank() {
        super("UnsubBank", "Unsub-Bank", "UnsubscribeBank", "Unsubscribe-Bank");
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.alerts.trade.unsubscribe.cmd);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "UnsubBank <nation|alliance>";
    }

    @Override
    public String desc() {
        return "Unsubscribe from trade alerts. Available resources: " + StringMan.getString(ResourceType.values);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        GuildKey.LARGE_TRANSFERS_CHANNEL.get(Locutus.imp().getGuildDB(guild));

        if (args.size() != 1) {
            return usage(args.size(), 1, channel);
        }
        BankDB db = Locutus.imp().getBankDB();

        if (args.get(0).equalsIgnoreCase("*")) {
            db.unsubscribe(author, 0, BankDB.BankSubType.ALL);
            db.unsubscribeAll(author.getIdLong());
        } else {
            Integer nationId = DiscordUtil.parseNationId(args.get(0), false);
            if (nationId == null || args.get(0).contains("/alliance/")) {
                Integer allianceId = PW.parseAllianceId(args.get(0));
                if (allianceId == null) {
                    return "Invalid nation or alliance: `" + args.get(0) + "`";
                }
                db.unsubscribe(author, allianceId, BankDB.BankSubType.ALLIANCE);
            } else {
                db.unsubscribe(author, nationId, BankDB.BankSubType.NATION);
            }
        }
        return "Unsubscribed from `" + args.get(0) + "`" + " alerts";
    }
}
