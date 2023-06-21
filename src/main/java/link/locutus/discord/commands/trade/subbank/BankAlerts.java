package link.locutus.discord.commands.trade.subbank;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.TimeUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class BankAlerts extends Command {
    public BankAlerts() {
        super("BankAlert", "Bank-Alert", "Alert-Bank", "AlertBank");
    }
    @Override
    public String help() {
        return Settings.commandPrefix(true) + "bank-alert <alliance|nation|*> <send|receive> <amount> <duration>";
    }

    @Override
    public String desc() {
        return "Subscribe (for a duration) to get alerts about bank transfers e.g. `" + Settings.commandPrefix(true) + "bank-alert \"The Blitzers\" receive 100M`";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 4) {
            return usage(args.size(), 4, channel);
        }
        boolean isReceive;
        if (args.get(1).equalsIgnoreCase("receive")) {
            isReceive = true;
        } else if (args.get(1).equalsIgnoreCase("send")) {
            isReceive = false;
        }else {
            return "Invalid arg: `" + args.get(1) + "`" + ". Must be either [send|receive]";
        }
        Integer amount = MathMan.parseInt(args.get(2));
        if (amount == null || amount <= 0) {
            return "Invalid $ threshold: `" + args.get(2) + "`";
        }
        if (amount < Settings.INSTANCE.UPDATE_PROCESSOR.THRESHOLD_BANK_SUB_ALERT) {
            return MathMan.format(amount) + " is < $" + MathMan.format(Settings.INSTANCE.UPDATE_PROCESSOR.THRESHOLD_BANK_SUB_ALERT);
        }

        BankDB.BankSubType isNation;
        Set<Integer> ids;
        Integer nationId = DiscordUtil.parseNationId(args.get(0));
        if (args.get(0).equalsIgnoreCase("*")) {
            isNation = BankDB.BankSubType.ALL;
            ids = Collections.singleton(0);
        } else if (nationId != null && !args.get(0).contains("/alliance/")) {
            isNation = BankDB.BankSubType.NATION;
            ids = Collections.singleton(nationId);
        } else {
            isNation = BankDB.BankSubType.ALLIANCE;
            ids = DiscordUtil.parseAlliances(guild, args.get(0));
        }
        if (ids == null || ids.isEmpty()) {
            return "Invalid alliance or nation: `" + args.get(0) + "`";
        }

        long now = System.currentTimeMillis();
        long msOffset = TimeUtil.timeToSec(args.get(3)) * 1000;
        long date = now + msOffset;

        User user = author;
        for (int id : ids) {
            Locutus.imp().getBankDB().subscribe(user, id, isNation, date, isReceive, amount);
        }
        return "Subscribed to `" + DiscordUtil.trimContent(fullCommandRaw).toUpperCase() + "`" +
                "\nCheck your subscriptions with: `" + Settings.commandPrefix(true) + "bank-alerts`";
    }
}
