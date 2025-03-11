package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.shrink.IShrink;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.builder.GroupedRankBuilder;
import link.locutus.discord.commands.manager.v2.builder.RankBuilder;
import link.locutus.discord.commands.manager.v2.builder.SummedMapRankBuilder;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

public class WarRanking extends Command {
    public WarRanking() {
        super(CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.stats_war.warRanking.cmd);
    }

    @Override
    public String help() {
        return super.help() + " <days> <attackers=*> <defenders=*>";
    }

    @Override
    public String desc() {
        return """
                Alliances by total wars over a specified number of days
                Use `-o` to only include offensive wars
                use `-d` to only include defensive wars
                use `-n` to normalize it per active member
                Use `-i` to filter out inactives (2d) for normalization
                Use `-a` to not rank alliances and do it by nation""";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) return usage(args.size(), 2, channel);
        Integer days = MathMan.parseInt(args.get(0));
        if (days == null) return usage("Invalid days (number): `" + args.get(0) + "`", channel);
        Function<Integer, Boolean> allowedAttackersF;
        Function<Integer, Boolean> allowedDefendersF;

        Set<Integer> allowedAttackers = args.get(1).equals("*") ? null : checkNotNull(DiscordUtil.parseAllianceIds(guild, args.get(1)), "Invalid alliance for coalition 1");
        Set<Integer> allowedDefenders = args.size() < 3 ? allowedAttackers : args.get(2).equals("*") ? null : checkNotNull(DiscordUtil.parseAllianceIds(guild, args.get(2)), "Invalid alliance for coalition 2");

        allowedAttackersF = allowedAttackers == null ? f -> true : allowedAttackers::contains;
        allowedDefendersF = allowedDefenders == null ? f -> true : allowedDefenders::contains;

        boolean offensive = !flags.contains('d');
        boolean defensive = !flags.contains('o');

        long timeout = ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond() * 1000L - TimeUnit.DAYS.toMillis(days);
        Set<DBWar> wars = Locutus.imp().getWarDb().getWars();
        wars.removeIf(e -> e.getDate() < timeout);

        boolean byAA = !flags.contains('a');

        SummedMapRankBuilder<Integer, Double> ranksUnsorted = new RankBuilder<>(wars).group((BiConsumer<DBWar, GroupedRankBuilder<Integer, DBWar>>) (dbWar, builder) -> {
            if (!allowedDefendersF.apply(dbWar.getDefender_aa()) || !allowedAttackersF.apply(dbWar.getAttacker_aa())) return;
            if (byAA) {
                if (dbWar.getAttacker_aa() != 0 && offensive) builder.put(dbWar.getAttacker_aa(), dbWar);
                if (dbWar.getDefender_aa() != 0 && defensive) builder.put(dbWar.getDefender_aa(), dbWar);
            } else {
                if (offensive) builder.put(dbWar.getAttacker_id(), dbWar);
                if (defensive) builder.put(dbWar.getDefender_id(), dbWar);
            }
        }).sumValues(f -> 1d);
        if (flags.contains('n') && byAA) {
            ranksUnsorted = ranksUnsorted.adapt((aaId, numWars) -> {
                int num = DBAlliance.getOrCreate(aaId).getNations(true, flags.contains('i') ? 2440 : Integer.MAX_VALUE, true).size();
                if (num == 0) return 0d;
                return numWars / (double) num;
            });
        }

        RankBuilder<IShrink> ranks = ranksUnsorted.sort().nameKeys(i -> (byAA ? DBAlliance.getOrCreate(i) : DBNation.getOrCreate(i)).toShrink());
        String offOrDef = "";
        if (offensive != defensive) {
            if (offensive) offOrDef = "offensive ";
            else offOrDef = "defensive ";
        }

        String title = "Most " + offOrDef + "wars (" + days + " days)";
        if (flags.contains('n')) title += "(per " + (flags.contains('i') ? "active " : "") + "nation)";
        ranks.build(author, channel, fullCommandRaw, title, true);

        return null;
    }
}
