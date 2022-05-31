package com.boydti.discord.commands.sheets;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.MarkupUtil;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.sheet.SpreadSheet;
import com.boydti.discord.util.task.GetMemberResources;
import com.boydti.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.boydti.discord.db.GuildDB.Key.ALLIANCE_ID;
import static com.boydti.discord.db.GuildDB.Key.STOCKPILE_SHEET;

public class StockpileSheet extends Command {
    public StockpileSheet() {
        super(CommandCategory.GOV, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.ECON, CommandCategory.MILCOM);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        GuildDB db = Locutus.imp().getGuildDB(server);
        return db.isValidAlliance() && db.getOrNull(GuildDB.Key.API_KEY) != null && Roles.ECON.has(user, server);
    }

    @Override
    public String desc() {
        return "List all nations in the alliance and their current stockpile\n" +
                "Add `-n` to normalize it per city";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        GuildDB db = Locutus.imp().getGuildDB(guild);
        int allianceId = db.getOrThrow(ALLIANCE_ID);

        Map<Integer, Map<ResourceType, Double>> stockpile = new GetMemberResources(allianceId).call();

        List<String> header = new ArrayList<>();
        header.add("nation");
        header.add("cities");
        header.add("avg_infra");
        header.add("off|def");
        header.add("mmr");

        for (ResourceType value : ResourceType.values) {
            header.add(value.name().toLowerCase());
        }

        SpreadSheet sheet = SpreadSheet.create(db, STOCKPILE_SHEET);
        sheet.setHeader(header);

        double[] aaTotal = ResourceType.getBuffer();

        for (Map.Entry<Integer, Map<ResourceType, Double>> entry : stockpile.entrySet()) {
            List<Object> row = new ArrayList<>();

            Integer nationId = entry.getKey();
            DBNation nation = Locutus.imp().getNationDB().getNation(nationId);
            if (nation == null) continue;
            row.add(MarkupUtil.sheetUrl(nation.getNation(), nation.getNationUrl()));
            row.add(nation.getCities());
            row.add(nation.getAvg_infra());
            row.add(nation.getOff() +"|" + nation.getDef());
            row.add(nation.getMMR());

            Map<ResourceType, Double> rss = entry.getValue();

            for (ResourceType type : ResourceType.values) {
                double amt = rss.getOrDefault(type, 0d);
                if (flags.contains('n')) amt /= nation.getCities();
                row.add(amt);

                if (amt > 0) aaTotal[type.ordinal()] += amt;
            }

            sheet.addRow(row);
        }

        sheet.clearAll();
        sheet.set(0, 0);

        String totalStr = PnwUtil.resourcesToFancyString(aaTotal);
        totalStr += "\n`note:total ignores nations with alliance info disabled`";
        DiscordUtil.createEmbedCommand(event.getChannel(), "AA Total", totalStr);

        return "<" + sheet.getURL() + ">";
    }
}
