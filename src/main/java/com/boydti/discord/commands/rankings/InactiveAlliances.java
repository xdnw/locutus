package com.boydti.discord.commands.rankings;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.commands.rankings.builder.RankBuilder;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.MathMan;
import com.google.common.collect.BiMap;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class InactiveAlliances extends Command {
    public InactiveAlliances() {
        super(CommandCategory.GAME_INFO_AND_TOOLS);
    }
    @Override
    public String help() {
        return "!InactiveAlliances <alliances|coalition> [days=7]";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.size() == 0 || args.size() > 2) {
            return usage(event);
        }
        String group;
        List<DBNation> nations;
        if (args.get(0).equalsIgnoreCase("*")) {
            group = "*";
            nations = new ArrayList<>(Locutus.imp().getNationDB().getNations().values());
        } else {
            group = args.get(0);
            Set<Integer> alliances = DiscordUtil.parseAlliances(DiscordUtil.getDefaultGuild(event), group);
            if (alliances == null || alliances.isEmpty()) {
                return "Invalid aa/coalition: " + group;
            }
            nations = Locutus.imp().getNationDB().getNations(alliances);
        }
        nations.removeIf(n -> n.getPosition() <= 1);

        Integer days = 7;
        if (args.size() == 2) {
            days = MathMan.parseInt(args.get(1));
            if (days == null) {
                return "Invalid number of days: `" + args.get(1) + "`";
            }
        }
        long minutes = TimeUnit.DAYS.toMinutes(days);

        Map<Integer, Integer> allianceSize = new RankBuilder<>(nations).group(DBNation::getAlliance_id).sumValues(i -> 1).get();

        BiMap<Integer, String> alliances = Locutus.imp().getNationDB().getAlliances();

        new RankBuilder<>(nations)
                .removeIf(nation -> nation.getActive_m() > minutes)
                .group(DBNation::getAlliance_id)
                .sumValues(nation -> 1)
                .sort(new Comparator<Map.Entry<Integer, Integer>>() {
                    @Override
                    public int compare(Map.Entry<Integer, Integer> o1, Map.Entry<Integer, Integer> o2) {
                        double size1 = allianceSize.get(o1.getKey()) / (double) (1 + o1.getValue());
                        double size2 = allianceSize.get(o2.getKey()) / (double) (1 + o2.getValue());
                        return Double.compare(size2, size1);
                    }
                })
                .name(new Function<Map.Entry<Integer, Integer>, String>() {
                    @Override
                    public String apply(Map.Entry<Integer, Integer> e) {
                        return alliances.get(e.getKey()) + ": " + e.getValue() + "/" + allianceSize.get(e.getKey());
                    }
                }).build(event, "Active in " + group + " (" + days + " days)");

        return null;
    }
}
