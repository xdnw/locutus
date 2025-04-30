package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarCostMode;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.shrink.IShrink;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.builder.RankBuilder;
import link.locutus.discord.commands.manager.v2.builder.SummedMapRankBuilder;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.*;

public class AllianceLootLosses extends Command {
    public AllianceLootLosses() {
        super(CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.stats_war.warCostRanking.cmd.type(WarCostMode.ATTACKER_LOSSES.name()).allowedAttacks(AttackType.A_LOOT.name()).groupByAlliance("true"));
    }

    @Override
    public String help() {
        return super.help() + " <time> [alliances]";
    }

    @Override
    public String desc() {
        return "Calculate the total losses from alliance loot over a specified timeframe";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty() || args.size() > 2) return usage(args.size(), 1, 2, channel);
        if (args.size() == 1) args.add("*");

        long millis = TimeUtil.timeToSec(args.get(0)) * 1000L;
        long cutOff = System.currentTimeMillis() - millis;

        Map<Integer, Double> allianceScores = new HashMap<>();
        List<DBNation> allNations = new ArrayList<>(DiscordUtil.parseNations(guild, author, me, args.get(1), false, true));
        allNations.removeIf(n -> n.getVm_turns() > 0 || n.getPosition() <= 1);
        for (DBNation nation : allNations) {
            allianceScores.put(nation.getAlliance_id(), nation.getScore() + allianceScores.getOrDefault(nation.getAlliance_id(), 0d));
        }

        Map<Integer, Double> totals = new HashMap<>();
        Locutus.imp().getWarDb().iterateAttacks(cutOff, AttackType.A_LOOT, (war, attack) -> {
            double[] loot = attack.getLoot();
            if (loot == null) return;
            int allianceId = attack.getAllianceIdLooted();
            if (allianceId == 0) return;

            Double existing = totals.getOrDefault(allianceId, 0d);
            totals.put(allianceId, existing + ResourceType.convertedTotal(loot));
        });

        totals.entrySet().removeIf(e -> !allianceScores.containsKey(e.getKey()) || e.getValue() <= 0);


        RankBuilder<IShrink> ranks = new SummedMapRankBuilder<>(totals).sort().nameKeys(i -> DBAlliance.getOrCreate(i).toShrink());


        String title = "Alliance bank loot losses (" + args.get(0) + ")";

        ranks.build(author, channel, fullCommandRaw, title, true);
        return null;
    }
}
