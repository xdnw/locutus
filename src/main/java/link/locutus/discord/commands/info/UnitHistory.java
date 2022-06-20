package link.locutus.discord.commands.info;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        Integer page = DiscordUtil.parseArgInt(args, "page");
        if (args.size() != 2) return usage(event);
        DBNation nation = DiscordUtil.parseNation(args.get(0));
        if (nation == null) return "Invalid nation: `" + args.get(0) +"`";
        MilitaryUnit unit = MilitaryUnit.valueOfVerbose(args.get(1).toUpperCase().replaceAll(" ", "_"));

        List<Map.Entry<Long, Integer>> history = nation.getUnitHistory(unit);

        long day = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);

        if (unit == MilitaryUnit.NUKE || unit == MilitaryUnit.MISSILE) {
            List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacks(nation.getNation_id(), day);
            AttackType attType = unit == MilitaryUnit.NUKE ? AttackType.NUKE : AttackType.MISSILE;
            attacks.removeIf(f -> f.attack_type != attType);

            outer:
            for (DBAttack attack : attacks) {
                AbstractMap.SimpleEntry<Long, Integer> toAdd = new AbstractMap.SimpleEntry<>(attack.epoch, nation.getUnits(unit));
                int i = 0;
                for (; i < history.size(); i++) {
                    Map.Entry<Long, Integer> entry = history.get(i);
                    long diff = Math.abs(entry.getKey() - attack.epoch);
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

        if (results.isEmpty()) return "No unit history";

        StringBuilder footer = new StringBuilder();

        if (unit == MilitaryUnit.MISSILE || unit == MilitaryUnit.NUKE) {
            if (purchasedToday) {
                footer.append("\n**note: " + unit.name().toLowerCase() + " purchased in the past 24h**");
            }
        }

            String cmd = DiscordUtil.trimContent(event.getMessage().getContentRaw());

        String title = "`" + nation.getNation() + "` " + unit.name() + " history";
        int perPage =15;
        int pages = (results.size() + perPage - 1) / perPage;
        if (page == null) page = 0;
        title += " (" + (page + 1) + "/" + pages +")";
        DiscordUtil.paginate(event.getGuildChannel(), title, cmd, page, perPage, results, footer.toString());
        return null;
    }
}
