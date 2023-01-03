package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public class WarCitySheet extends Command {
    public WarCitySheet() {
        super(CommandCategory.GOV, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    @Override
    public String help() {
        return super.help() + " <coalition-1> <coalition-2> ...";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MILCOM.has(user, server);
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

        GuildDB guildDb = checkNotNull(Locutus.imp().getGuildDB(event));
        SpreadSheet sheet = SpreadSheet.create(guildDb, GuildDB.Key.CITY_GRAPH_SHEET);

        List<String> header = new ArrayList<>();
        header.add("cities");
        header.addAll(args);

        sheet.setHeader(header);

        List<String>[] rows = new List[50];

        for (int column = 0; column < args.size(); column++) {
            String arg = args.get(column);
            Set<DBNation> nations = DiscordUtil.parseNations(guild, arg);
            nations.removeIf(t -> t.getVm_turns() > 0 || t.getActive_m() > minutesInactive);
            int[] num = new int[100];
            for (DBNation nation : nations) num[nation.getCities()]++;

//            BiFunction<Integer, Integer, Integer> scores = PnwUtil.getIsNationsInCityRange(nations);

            for (int i = 0; i < 50; i++) {
                List<String> row = rows[i];
                int cities = i + 1;
                if (row == null) {
                    rows[i] = row = new ArrayList<>(header);
                    row.set(0, cities + "");
                }

                row.set(column + 1, num[cities] + "");
            }
        }

        for (List<String> row : rows) {
            if (row != null) {
                sheet.addRow(row);
            }
        }

        sheet.clearAll();
        sheet.set(0, 0);
        sheet.attach(new DiscordChannelIO(event).create()).send();
        return null;
    }
}
