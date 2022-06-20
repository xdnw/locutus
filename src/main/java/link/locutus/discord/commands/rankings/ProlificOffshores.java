package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import com.google.common.collect.BiMap;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProlificOffshores extends Command {

    public ProlificOffshores() {
        super(CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.ECON);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String help() {
        return super.help() + " [days]";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.isEmpty()) return usage(event);
        Integer days = MathMan.parseInt(args.get(0));
        if (days == null) {
            return "Invalid number of days: `" + args.get(0) + "`";
        }
        long cutoffMs = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;

        Map<Integer, Long> aaCount = new HashMap<>();
        Map<Integer, Long> aaCount1City = new HashMap<>();
        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNations();
        for (Map.Entry<Integer, DBNation> entry : nations.entrySet()) {
            int aaId = entry.getValue().getAlliance_id();
            aaCount.put(aaId, 1 + aaCount.getOrDefault(aaId, 0L));
            if (entry.getValue().getCities() == 1) {
                aaCount1City.put(aaId, 1 + aaCount1City.getOrDefault(aaId, 0L));
            }
        }
        aaCount.entrySet().removeIf(e -> e.getValue() > 2);
//        aaCount.entrySet().removeIf(new Predicate<Map.Entry<Integer, Long>>() {
//            @Override
//            public boolean test(Map.Entry<Integer, Long> e) {
//                return aaCount1City.getOrDefault(e.getKey(), 0L).equals(e.getValue());
//            }
//        });

        for (Map.Entry<Integer, Long> entry : aaCount.entrySet()) {
            List<Transaction2> transfers = Locutus.imp().getBankDB().getAllianceTransfers(entry.getKey(), cutoffMs);
            long sum = 0;
            for (Transaction2 value : transfers) {
                if (value.banker_nation == value.getReceiver()) continue;
                DBNation nation = nations.get((int) value.getReceiver());
                if (nation == null) continue;
                if (nation.getAlliance_id() == value.getSender()) continue;
                sum += (long) Math.abs(PnwUtil.convertedTotal(value.resources));
            }
            entry.setValue(sum);
        }

        BiMap<Integer, String> alliances = Locutus.imp().getNationDB().getAlliances();

        new SummedMapRankBuilder<>(aaCount)
        .sort()
        .nameKeys(alliances::get)
        .limit(10)
        .build(event, "Prolific Offshores (" + days + " days)");

        return null;
    }
}
