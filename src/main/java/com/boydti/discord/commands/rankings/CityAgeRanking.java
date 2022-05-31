package com.boydti.discord.commands.rankings;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.rankings.builder.GroupedRankBuilder;
import com.boydti.discord.commands.rankings.builder.NumericMappedRankBuilder;
import com.boydti.discord.commands.rankings.builder.RankBuilder;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.MathMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CityAgeRanking extends Command {

    @Override
    public String help() {
        return super.help() + " <min> <max> [members]";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) return usage(event);
        int min = Integer.parseInt(args.get(0));
        int max = Integer.parseInt(args.get(1));

        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNations();
        nations.entrySet().removeIf(e -> {
            int cities = e.getValue().getCities();
            return cities < min || cities > max;
        });

        GroupedRankBuilder<Integer, DBNation> nationByAAGroup = new RankBuilder<>(nations.values()).group(DBNation::getAlliance_id);
        Map<Integer, List<DBNation>> nationsByAlliance = nationByAAGroup.get();

        NumericMappedRankBuilder<Integer, Integer, Number> avg = nationByAAGroup.map(new BiFunction<Integer, DBNation, Integer>() {
            @Override
            public Integer apply(Integer integer, DBNation nation) {
                return null;
            }
        }, new BiFunction<Integer, DBNation, Number>() {
            @Override
            public Number apply(Integer integer, DBNation nation) {
                return nation.getAgeDays();
            }
        });

//        new RankBuilder<>(nations)
//                .removeIf(nation -> nation.getAircraft() == null)
//                .group(DBNation::getAlliance_id)
//                .sumValues(DBNation::getAircraft)
//                .sort()
//                .nameKeys(alliances::get).build(event, "Total planes in " + group);

        return null;

    }
}
