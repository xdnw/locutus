package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FindOffshore extends Command {
    public FindOffshore() {
        super("FindOffshore", "FindOffshores", CommandCategory.ECON);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + getClass().getSimpleName() + " <alliance> [days]";
    }

    @Override
    public String desc() {
        return "Find potential offshores used by an alliance.";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.isEmpty()) {
            return usage(event);
        }
        Integer alliance = PnwUtil.parseAllianceId(args.get(0));
        if (alliance == null) {
            return "Invalid alliance: `" + args.get(0) + "`";
        }

        int days = 7;
        if (args.size() == 2) {
            days = Integer.parseInt(args.get(1));
        }

        long cutoffMs = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;

        List<Transaction2> transactions = Locutus.imp().getBankDB().getToNationTransactions(cutoffMs);
        long now = System.currentTimeMillis();
        transactions.removeIf(t -> {
            DBNation nation = DBNation.byId((int) t.getReceiver());
            return nation == null || t.getDate() > now || t.getSender() == alliance || nation.getAlliance_id() != alliance || (t.note != null && t.note.contains("defeated"));
        });
        Map<Integer, Integer> numTransactions = new HashMap<>();
        Map<Integer, Double> valueTransferred = new HashMap<>();

        for (Transaction2 t : transactions) {
            numTransactions.put((int) t.getSender(), numTransactions.getOrDefault((int) t.getSender(), 0) + 1);

            double value = PnwUtil.convertedTotal(t.resources);

            valueTransferred.put((int) t.getSender(), valueTransferred.getOrDefault((int) t.getSender(), 0d) + value);
        }

        new SummedMapRankBuilder<>(numTransactions).sort().name(e -> PnwUtil.getName(e.getKey(), true) + ": " + e.getValue() + " | $" + MathMan.format(valueTransferred.get(e.getKey()))).build(event, "Potential offshores");

        return null;
    }
}
