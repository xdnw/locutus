package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.rankings.builder.GroupedRankBuilder;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class NetProfitPerWar extends Command {
    public NetProfitPerWar() {
        super(CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        int days = 3;
        boolean profit = true;

        Set<Integer> AAs = null;
        String id = "AA";

        Predicate<DBWar> warFilter = f -> true;
        for (String arg : args) {
            if (MathMan.isInteger(arg)) {
                days = Integer.parseInt(arg);
            } else if (arg.equalsIgnoreCase("false")) {
                profit = false;
            } else if (arg.equalsIgnoreCase("*")) {
                id = "Nation";
                AAs = new HashSet<>();
            } else {
                id = arg;
                AAs = DiscordUtil.parseAllianceIds(guild, arg);
                Set<Integer> finalAAs1 = AAs;
                warFilter = warFilter.and(f -> (finalAAs1.contains(f.getAttacker_aa()) || finalAAs1.contains(f.getDefender_aa())));
            }
        }
        int sign = profit ? -1 : 1;
        long cutoffMs = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;

        String title = id + " Net " + (profit ? "profit" : "losses") + " per war (%s days)";
        title = String.format(title, days);

        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNations();

        Set<Integer> finalAAs = AAs;

        Predicate<DBWar> finalWarFilter = warFilter;
        List<AbstractCursor> attacks = Locutus.imp().getWarDb().getAttacks(cutoffMs, Long.MAX_VALUE, f -> f.possibleEndDate() >= cutoffMs && finalWarFilter.test(f), f -> true);

        double[] buffer = ResourceType.getBuffer();
        SummedMapRankBuilder<Integer, Number> byNation = new RankBuilder<>(attacks)
                .group((BiConsumer<AbstractCursor, GroupedRankBuilder<Integer, AbstractCursor>>) (attack, map) -> {
                    // Group attacks into attacker and defender
                    map.put(attack.getAttacker_id(), attack);
                    map.put(attack.getDefender_id(), attack);
                }).map((i, a) -> a.getWar_id(),
                        // Convert attack to profit value
                        (nationdId, attack) -> {
                            DBNation nation = nations.get(nationdId);
                            if (nation != null) {
                                Arrays.fill(buffer, 0);
                                return sign * attack.getLossesConverted(buffer, attack.getAttacker_id() == nationdId);
                            }
                            return 0;
                        })
                // Average it per war
                .average();

        RankBuilder<String> ranks;
        if (AAs == null) {
            // Group it by alliance
            ranks = byNation.<Integer>group((entry, builder) -> {
                        DBNation nation = nations.get(entry.getKey());
                        if (nation != null) {
                            builder.put(nation.getAlliance_id(), entry.getValue());
                        }
                    })
                    // Average it per alliance
                    .average()
                    // Sort descending
                    .sort()
                    // Change key to alliance name
                    .nameKeys(allianceId -> PW.getName(allianceId, true))
                    .limit(25);
        } else {
            // Sort descending
            ranks = byNation
                    .removeIfKey(nationId -> !nations.containsKey(nationId) || (!finalAAs.isEmpty() && !finalAAs.contains(nations.get(nationId).getAlliance_id())))
                    .sort()
                    // Change key to alliance name
                    .nameKeys(nationId -> nations.get(nationId).getNation())
                    .limit(25);
        }

        // Embed the rank list
        ranks.build(author, channel, fullCommandRaw, title);


        return null;
    }
}