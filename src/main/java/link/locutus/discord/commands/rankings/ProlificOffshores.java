package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarCostMode;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProlificOffshores extends Command {

    public ProlificOffshores() {
        super(CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.ECON);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.offshore.list.prolific.cmd);
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
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) return usage(args.size(), 1, channel);
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
        for (Map.Entry<Integer, Long> entry : aaCount.entrySet()) {
            List<Transaction2> transfers = Locutus.imp().getBankDB().getAllianceTransfers(entry.getKey(), cutoffMs);
            long sum = 0;
            for (Transaction2 value : transfers) {
                if (value.banker_nation == value.getReceiver()) continue;
                DBNation nation = nations.get((int) value.getReceiver());
                if (nation == null) continue;
                if (nation.getAlliance_id() == value.getSender()) continue;
                sum += (long) Math.abs(ResourceType.convertedTotal(value.resources));
            }
            entry.setValue(sum);
        }


        new SummedMapRankBuilder<>(aaCount)
                .sort()
                .nameKeys(f -> PW.getName(f, true))
                .limit(10)
                .build(author, channel, fullCommandRaw, "Prolific Offshores (" + days + " days)");

        return null;
    }
}
