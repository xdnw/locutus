package link.locutus.discord.commands.trade.subbank;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class UnsubBank extends Command {
    public UnsubBank() {
        super("UnsubBank", "Unsub-Bank", "UnsubscribeBank", "Unsubscribe-Bank");
    }

    @Override
    public String help() {
        return "!UnsubBank <nation|alliance>";
    }

    @Override
    public String desc() {
        return "Unsubscribe from trade alerts. Available resources: " + StringMan.getString(ResourceType.values);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.size() != 1) {
            return usage(event);
        }
        BankDB db = Locutus.imp().getBankDB();

        if (args.get(0).equalsIgnoreCase("*")) {
            db.unsubscribe(event.getAuthor(), 0, BankDB.BankSubType.ALL);
        } else {
            Integer nationId = DiscordUtil.parseNationId(args.get(0));
            if (nationId == null || args.get(0).contains("/alliance/")) {
                Integer allianceId = PnwUtil.parseAllianceId(args.get(0));
                if (allianceId == null) {
                    return "Invalid alliance: `" + args.get(0) + "`";
                }
                db.unsubscribe(event.getAuthor(), allianceId, BankDB.BankSubType.ALLIANCE);
            } else {
                db.unsubscribe(event.getAuthor(), nationId, BankDB.BankSubType.NATION);
            }
        }
        return "Unsubscribed from `" + args.get(0) + "`" + " alerts";
    }
}
