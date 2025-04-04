package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarCostMode;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.builder.RankBuilder;
import link.locutus.discord.commands.manager.v2.builder.SummedMapRankBuilder;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.shrink.IShrink;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

import static link.locutus.discord.util.MathMan.format;

public class AllianceLootRanking extends Command {

    public AllianceLootRanking() {
        super(CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.stats_war.warCostRanking.cmd.type(WarCostMode.PROFIT.name()).groupByAlliance("true"));
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String usage() {
        return super.usage() + " <days>";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) return usage(args.size(), 1, channel);
        Integer days = MathMan.parseInt(args.get(0));
        if (days == null) {
            return "Invalid number of days: `" + args.get(0) + "`";
        }
        long cutoffMs = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;

        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNationsById();
        Map<Integer, Map<ResourceType, Double>> byAlliance = new HashMap<>();

        Locutus.imp().getWarDb().queryAttacks()
            .withWars(f -> f.possibleEndDate() >= cutoffMs && (f.getAttacker_aa() > 0 || f.getDefender_aa() > 0)).afterDate(cutoffMs).withTypes(AttackType.A_LOOT, AttackType.VICTORY, AttackType.GROUND)
            .iterateAttacks((war, attack) -> {
            double[] loot = attack.getLoot();
            if (loot == null) return;
            int looter = attack.getAttacker_id();
            {
                DBNation nation = nations.get(looter);
                int allianceId = nation == null ? 0 : nation.getAlliance_id();
                Map<ResourceType, Double> map = byAlliance.get(allianceId);
                if (map == null) {
                    map = new HashMap<>();
                }
                Map<ResourceType, Double> add = ResourceType.add(map, ResourceType.resourcesToMap(loot));
                byAlliance.put(allianceId, add);
            }
        });

        List<Map.Entry<Integer, Map<ResourceType, Double>>> sorted = new ArrayList<>(byAlliance.entrySet())
                .stream()
                .sorted(Comparator.comparingDouble(
//                        o -> -PW.convertedTotal(o.getValue())))
                        o -> -o.getValue().getOrDefault(ResourceType.MONEY, 0d)))
                .toList();

        String title = "Loot (" + days + " days):";
        Map<Integer, Double> totals = sorted.stream().collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue().getOrDefault(ResourceType.MONEY, 0d)), HashMap::putAll);
        RankBuilder<IShrink> ranks = new SummedMapRankBuilder<>(totals).sort().nameKeys(i -> DBAlliance.getOrCreate(i).toShrink());
        ranks.build(author, channel, fullCommandRaw, title, true);
        return null;
    }
}
