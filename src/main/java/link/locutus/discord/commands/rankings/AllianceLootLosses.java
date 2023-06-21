package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.*;

public class AllianceLootLosses extends Command {
    public AllianceLootLosses() {
        super(CommandCategory.GAME_INFO_AND_TOOLS);
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
        List<DBNation> allNations = new ArrayList<>(DiscordUtil.parseNations(guild, args.get(1)));
        allNations.removeIf(n -> n.getVm_turns() > 0 || n.getPosition() <= 1);
        for (DBNation nation : allNations) {
            allianceScores.put(nation.getAlliance_id(), nation.getScore() + allianceScores.getOrDefault(nation.getAlliance_id(), 0d));
        }

        Map<Integer, Double> totals = new HashMap<>();
        List<DBAttack> aaLoot = Locutus.imp().getWarDb().getAttacks(cutOff, AttackType.A_LOOT);
        for (DBAttack attack : aaLoot) {
            Map<ResourceType, Double> loot = attack.getLoot();
            Integer allianceId = attack.getLooted();
            if (allianceId == null || allianceId == 0) continue;

            Double existing = totals.getOrDefault(allianceId, 0d);
            totals.put(allianceId, existing + PnwUtil.convertedTotal(loot));
        }

        totals.entrySet().removeIf(e -> !allianceScores.containsKey(e.getKey()) || e.getValue() <= 0);


        RankBuilder<String> ranks = new SummedMapRankBuilder<>(totals).sort().nameKeys(i -> PnwUtil.getName(i, true));


        String title = "Alliance bank loot losses (" + args.get(0) + ")";

        ranks.build(event, title);

        if (ranks.get().size() > 25) {
            DiscordUtil.upload(channel, title, ranks.toString());
        }

        return super.onCommand(event, guild, author, me, args, flags);
    }
}
