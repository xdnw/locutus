package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.builder.SummedMapRankBuilder;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AllianceAttackTypeRanking extends Command {
    public AllianceAttackTypeRanking() {
        super(CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.stats_war.attack_ranking.cmd);
    }
    @Override
    public String help() {
        return super.help() + " <days> <type> <alliances> <topX>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String desc() {
        return "Rank the alliances by the % (or total) attacks by type.\n" +
                "Types: " + StringMan.getString(AttackType.values()) + "\n" +
                "Add `-t` for total\n" +
                "Add `munitions>X` to filter attacks by munition usage\n" +
                "Add `gasoline<X` to filter attacks by gasoline usage";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 4) return usage();

        String arg = args.get(0);
        if (MathMan.isInteger(arg)) arg = arg + "d";
        long cutoffMs = System.currentTimeMillis() - TimeUtil.timeToSec(arg) * 1000L;
        AttackType type = AttackType.get(args.get(1).toUpperCase());

        Set<Integer> allowedNations = Locutus.imp().getNationDB().getNationsMatching(f -> f.getAlliance_id() > 0 && f.getPosition() > 1).stream().map(DBNation::getId).collect(Collectors.toSet());
//            Map<Integer, DBWar> wars = Locutus.imp().getWarDb().getWars();
        Map<Integer, Integer> totalAttacks = new HashMap<>();
        Map<Integer, Integer> attackOfType = new HashMap<>();

        Locutus.imp().getWarDb().queryAttacks().withActiveWars(allowedNations::contains, f -> true).afterDate(cutoffMs).withTypes(type)
        .iterateAttacks((war, attack) -> {
            DBNation nat = Locutus.imp().getNationDB().getNationById(attack.getAttacker_id());
            if (nat == null || nat.getAlliance_id() == 0 || nat.getPosition() <= 1) return;
            totalAttacks.put(nat.getAlliance_id(), totalAttacks.getOrDefault(nat.getAlliance_id(), 0) + 1);

            if (attack.getAttack_type() == type) {
                attackOfType.put(nat.getAlliance_id(), attackOfType.getOrDefault(nat.getAlliance_id(), 0) + 1);
            }
        });

        Set<DBAlliance> topAlliances = Locutus.imp().getNationDB().getAlliances(true, true, true, Integer.parseInt(args.get(3)));
        Set<Integer> alliances = DiscordUtil.parseAllianceIds(guild, args.get(2));
        if (alliances == null) return "Invalid alliances: `" + args.get(2) + "`";

        SummedMapRankBuilder<DBAlliance, Double> builder = new SummedMapRankBuilder<>();

        for (Map.Entry<Integer, Integer> entry : attackOfType.entrySet()) {
            DBAlliance aa = DBAlliance.getOrCreate(entry.getKey());

            if (!alliances.contains(aa.getAlliance_id()) || !topAlliances.contains(aa)) continue;

            int num = entry.getValue();
            int total = totalAttacks.get(entry.getKey());

            double value;
            if (flags.contains('t')) {
                value = num;
            } else {
                value = 100d * num / total;
            }

            builder.put(aa, value);
        }

        String title = " attacks of type: " + type.getName() + " (" + args.get(0) + ")";
        title = (flags.contains('t') ? "total" : "percent") + title;
        builder.sort().name(DBAlliance::toShrink, MathMan::format).build(author, channel, fullCommandRaw, title);
        return null;
    }
}
