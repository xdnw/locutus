package link.locutus.discord.commands.info;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class UnitHistory extends Command {
    public UnitHistory() {
        super("UnitHistory", "MilitaryHistory", "Rebuy", CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return super.help() + " <nation> <unit>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        Integer page = DiscordUtil.parseArgInt(args, "page");
        if (args.size() != 2) return usage(args.size(), 2, channel);
        DBNation nation = DiscordUtil.parseNation(args.get(0));
        if (nation == null) return "Invalid nation: `" + args.get(0) + "`";
        MilitaryUnit unit = MilitaryUnit.valueOfVerbose(args.get(1).toUpperCase().replaceAll(" ", "_"));

        List<Map.Entry<Long, Integer>> history = nation.getUnitHistory(unit);

        long day = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);

        if (unit == MilitaryUnit.NUKE || unit == MilitaryUnit.MISSILE) {
            List<AbstractCursor> attacks = Locutus.imp().getWarDb().getAttacks(nation.getNation_id(), day);
            AttackType attType = unit == MilitaryUnit.NUKE ? AttackType.NUKE : AttackType.MISSILE;
            attacks.removeIf(f -> f.getAttack_type() != attType);

            outer:
            for (AbstractCursor attack : attacks) {
                AbstractMap.SimpleEntry<Long, Integer> toAdd = new AbstractMap.SimpleEntry<>(attack.getDate(), nation.getUnits(unit));
                int i = 0;
                for (; i < history.size(); i++) {
                    Map.Entry<Long, Integer> entry = history.get(i);
                    long diff = Math.abs(entry.getKey() - attack.getDate());
                    if (diff < 5 * 60 * 1000) continue outer;

                    toAdd.setValue(entry.getValue());
                    if (entry.getKey() < toAdd.getKey()) {
                        history.add(i, toAdd);
                        continue outer;
                    }
                }
                history.add(i, toAdd);
            }
        }


        boolean purchasedToday = false;

        List<String> results = new ArrayList<>();
        Map.Entry<Long, Integer> previous = null;
        for (Map.Entry<Long, Integer> entry : history) {
            if (previous != null) {
                long timestamp = previous.getKey();
                String dateStr = TimeUtil.format(TimeUtil.MMDDYYYY_HH_MM_A, new Date(timestamp));

                int from = entry.getValue();
                int to = previous.getValue();

                results.add(dateStr + ": " + from + " -> " + to);

                if (to >= from && entry.getKey() >= day) purchasedToday = true;
            } else if (entry.getKey() >= day) purchasedToday = true;
            previous = new AbstractMap.SimpleEntry<>(entry);
        }
        if (previous != null) {
            long timestamp = previous.getKey();
            String dateStr = TimeUtil.format(TimeUtil.MMDDYYYY_HH_MM_A, new Date(timestamp));

            int to = previous.getValue();
            String from = "?";

            if ((unit == MilitaryUnit.MISSILE || unit == MilitaryUnit.NUKE) && to > 0) {
                from = "" + (to - 1);
            }

            results.add(dateStr + ": " + from + " -> " + to);
        }

        if (results.isEmpty()) return "No unit history.";

        StringBuilder footer = new StringBuilder();

        if (unit == MilitaryUnit.MISSILE || unit == MilitaryUnit.NUKE) {
            if (purchasedToday) {
                footer.append("\n**note: ").append(unit.name().toLowerCase()).append(" purchased in the past 24h.");
            }
        }

        CM.unit.history cmd = CM.unit.history.cmd.nation(nation.getNation_id() + "").unit(unit.name());

        String title = "`" + nation.getNation() + "` " + unit.name() + " history";
        int perPage = 15;
        int pages = (results.size() + perPage - 1) / perPage;
        if (page == null) page = 0;
        title += " (" + (page + 1) + "/" + pages + ")";

        channel.create().paginate(title, cmd, page, perPage, results, footer.toString(), false).send();
        return null;
    }
}
