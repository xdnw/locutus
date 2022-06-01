package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.rankings.builder.GroupedRankBuilder;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.pnw.Alliance;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class WarRanking extends Command {
    public WarRanking() {
        super(CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return super.help() + " <days> <attackers=*> <defenders=*>";
    }

    @Override
    public String desc() {
        return "Alliances by total wars over a specified number of days\n" +
                "Use `-o` to only include offensive wars\n" +
                "use `-d` to only include defensive wars\n" +
                "use `-n` to normalize it per active member\n" +
                "Use `-i` to filter out inactives (2d) for normalization\n" +
                "Use `-a` to not rank alliances and do it by nation";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) return usage(event);
        Integer days = MathMan.parseInt(args.get(0));
        if (days == null) return usage(event);
        Function<Integer, Boolean> allowedAttackersF;
        Function<Integer, Boolean> allowedDefendersF;

        Set<Integer> allowedAttackers = args.get(1).equals("*") ? null : DiscordUtil.parseAlliances(guild, args.get(1));
        Set<Integer> allowedDefenders = args.size() < 3 ? allowedAttackers : args.get(2).equals("*") ? null : DiscordUtil.parseAlliances(guild, args.get(2));

        allowedAttackersF = allowedAttackers == null ? f -> true : f -> allowedAttackers.contains(f);
        allowedDefendersF = allowedDefenders == null ? f -> true : f -> allowedDefenders.contains(f);

        boolean offensive = !flags.contains('d');
        boolean defensive = !flags.contains('o');

        long timeout = ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond() * 1000L - TimeUnit.DAYS.toMillis(days);
        Map<Integer, DBWar> wars = Locutus.imp().getWarDb().getWars();
        wars.entrySet().removeIf(e -> e.getValue().date < timeout);

        boolean byAA = !flags.contains('a');

        SummedMapRankBuilder<Integer, Double> ranksUnsorted = new RankBuilder<>(wars.values()).group(new BiConsumer<DBWar, GroupedRankBuilder<Integer, DBWar>>() {
            @Override
            public void accept(DBWar dbWar, GroupedRankBuilder<Integer, DBWar> builder) {
                if (!allowedDefendersF.apply(dbWar.defender_aa) || !allowedAttackersF.apply(dbWar.attacker_aa)) return;
                if (byAA) {
                    if (dbWar.attacker_aa != 0 && offensive) builder.put(dbWar.attacker_aa, dbWar);
                    if (dbWar.defender_aa != 0 && defensive) builder.put(dbWar.defender_aa, dbWar);
                } else {
                    if (offensive) builder.put(dbWar.attacker_id, dbWar);
                    if (defensive) builder.put(dbWar.defender_id, dbWar);
                }
            }
        }).sumValues(f -> 1d);
        if (flags.contains('n') && byAA) {
            ranksUnsorted = ranksUnsorted.adapt((aaId, numWars) -> {
                int num = new Alliance(aaId).getNations(true, flags.contains('i') ? 2440 : Integer.MAX_VALUE, true).size();
                if (num == 0) return 0d;
                return numWars.doubleValue() / (double) num;
            });
        }

        RankBuilder<String> ranks = ranksUnsorted.sort().nameKeys(i -> PnwUtil.getName(i, byAA));
        String offOrDef ="";
        if (offensive != defensive) {
            if (offensive) offOrDef = "offensive ";
            else offOrDef = "defensive ";
        }

        String title = "Most " + offOrDef + "wars (" + days + " days)";
        if (flags.contains('n')) title += "(per " + (flags.contains('i') ? "active " : "") + "nation)";
        ranks.build(event, title);

        if (ranks.get().size() > 25) {
            DiscordUtil.upload(event.getGuildChannel(), title, ranks.toString());
        }

        return null;
    }
}
