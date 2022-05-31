package com.boydti.discord.commands.sheets;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import static com.google.common.base.Preconditions.checkNotNull;

public class WarCostByCitySheet extends Command {
    public WarCostByCitySheet() {
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
        GuildDB guildDb = checkNotNull(Locutus.imp().getGuildDB(event));
        SpreadSheet sheet = SpreadSheet.create(guildDb, GuildDB.Key.WAR_COST_BY_CITY_SHEET);

        List<String> header = new ArrayList<>();
        header.add("cities");
        header.addAll(args);

        sheet.setHeader(header);

        List<String>[] rows = new List[50];

        for (int column = 0; column < args.size(); column++) {
            String arg = args.get(column);
            Set<DBNation> nations = DiscordUtil.parseNations(guild, arg);
            nations.removeIf(t -> t.getVm_turns() > 0 || t.getActive_m() > 2440);
            BiFunction<Integer, Integer, Integer> scores = PnwUtil.getIsNationsInCityRange(nations);

            for (int i = 0; i < 50; i++) {
                List<String> row = rows[i];
                int cities = i + 1;
                if (row == null) {
                    rows[i] = row = new ArrayList<>(header);
                    row.set(0, cities + "");
                }

                row.set(column + 1, scores.apply(cities, cities) + "");
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
