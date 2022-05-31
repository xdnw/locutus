package com.boydti.discord.commands.sheets;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.pnw.NationList;
import com.boydti.discord.pnw.SimpleNationList;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.TimeUtil;
import com.boydti.discord.util.battle.BlitzGenerator;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import static com.google.common.base.Preconditions.checkNotNull;

public class StrengthCitySheet extends Command {
    public StrengthCitySheet() {
        super(CommandCategory.GOV, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    @Override
    public String help() {
        return super.help() + " <coalition-1> <coalition-2> ...";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Locutus.imp().getGuildDB(server).isValidAlliance() && Roles.MILCOM.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 1) return usage(event);
        int minutesInactive;
        if (false || args.size() == 3) {
            minutesInactive = (int) (TimeUtil.timeToSec(args.get(2)) / 60);
        } else {
            minutesInactive = 2880;
        }
        boolean total = flags.contains('t');

        GuildDB guildDb = checkNotNull(Locutus.imp().getGuildDB(event));
        SpreadSheet sheet = SpreadSheet.create(guildDb, GuildDB.Key.MILITARY_GRAPH_SHEET);

        List<String> header = new ArrayList<>();
        header.add("cities");

        for (String arg : args) {
            header.add(arg);
        }


        sheet.setHeader(header);

        List<String>[] rows = new List[50];


        for (int column = 0; column < args.size(); column++) {
            String arg = args.get(column);
            Set<DBNation> nations = DiscordUtil.parseNations(guild, arg);
            nations.removeIf(t -> t.getVm_turns() > 0 || t.getActive_m() > minutesInactive);

            HashMap<Integer, Set<DBNation>> byCity = new HashMap<Integer, Set<DBNation>>();
            for (DBNation n : nations) byCity.computeIfAbsent(n.getCities(), f -> new HashSet<>()).add(n);

            BiFunction<Integer, Integer, Integer> scores = PnwUtil.getIsNationsInCityRange(nations);

            for (int i = 0; i < 50; i++) {
                List<String> row = rows[i];
                int cities = i + 1;
                if (row == null) {
                    rows[i] = row = new ArrayList<>(header);
                    row.set(0, cities + "");
                }

                NationList atCity = new SimpleNationList(byCity.getOrDefault(cities, Collections.emptySet()));
                if (atCity.getNations().isEmpty()) {
                    row.set(column + 1, "");
                } else {
                    DBNation combined = total ? atCity.getTotal() : atCity.getAverage();
//                    row.set(column + 1, combined.getSoldiers() + "");
//                    row.set(column + 2, combined.getTanks() + "");
//                    row.set(column + 3, combined.getAircraft() + "");
//                    row.set(column + 4, combined.getShips() + "");
                    row.set(column + 1, BlitzGenerator.getAirStrength(combined, true) + "");
                }
            }
        }

        for (List<String> row : rows) {
            if (row != null) {
                sheet.addRow(row);
            }
        }

        sheet.clearAll();
        sheet.set(0, 0);
        return "<" + sheet.getURL() + ">";
    }
}
