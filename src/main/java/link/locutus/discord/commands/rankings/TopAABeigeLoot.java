package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import com.google.common.collect.BiMap;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TopAABeigeLoot extends Command {
    public TopAABeigeLoot() {
        super("TopAABeigeLoot", "bankbyloot", CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return super.help() + " <time>";
    }

    @Override
    public String desc() {
        return "Get the largest alliance bank loot per score\n" +
                "Add -t to just list total bank worth";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(event);
        String title = "AA loot/score";

        boolean total = flags.contains('t');
        if (total) title = "AA bank total";

        long millis = TimeUtil.timeToSec(args.get(0)) * 1000L;
        long cutOff = System.currentTimeMillis() - millis;

        Map<Integer, Double> allianceScores = new HashMap<>();
        LinkedList<DBNation> allNations = new LinkedList<>(Locutus.imp().getNationDB().getNations().values());
        allNations.removeIf(n -> n.getVm_turns() > 0 || n.getPosition() <= 1);
        for (DBNation nation : allNations) {
            allianceScores.put(nation.getAlliance_id(), nation.getScore() + allianceScores.getOrDefault(nation.getAlliance_id(), 0d));
        }

        Map<Integer, double[]> loot = Locutus.imp().getWarDb().getAllianceBankEstimate(cutOff, true);
        Map<Integer, Double> lootPerScore = new HashMap<>();

        for (Map.Entry<Integer, double[]> entry : loot.entrySet()) {
            Integer alliance = entry.getKey();

            if (total) {
                lootPerScore.put(alliance, PnwUtil.convertedTotal(entry.getValue()));
            } else {
                double aaScore = allianceScores.getOrDefault(entry.getKey(), 0d);
                if (aaScore == 0) continue;
                double ratio = (1d / aaScore) / (5);
                double percent = Math.min(ratio, 0.33);
                double convertedTotal = PnwUtil.convertedTotal(entry.getValue()) * percent;
                lootPerScore.put(alliance, convertedTotal);
            }
        }


        BiMap<Integer, String> aas = Locutus.imp().getNationDB().getAlliances();

        SummedMapRankBuilder<Integer, ? extends Number> sorted = new SummedMapRankBuilder<>(lootPerScore).sort();
        sorted.nameKeys(i -> aas.getOrDefault(i, Integer.toString(i))).build(event, title);

        for (Integer integer : sorted.get().keySet()) {
            System.out.println(PnwUtil.getBBUrl(integer, true));
        }


        return null;
    }
}
