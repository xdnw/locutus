package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StockpileSheet extends Command {
    public StockpileSheet() {
        super(CommandCategory.GOV, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.ECON, CommandCategory.MILCOM);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        GuildDB db = Locutus.imp().getGuildDB(server);
        return db.isValidAlliance() && db.getOrNull(GuildDB.Key.API_KEY) != null && (Roles.ECON.has(user, server) || Roles.ECON_LOW_GOV.has(user, server));
    }

    @Override
    public String desc() {
        return "List all nations in the alliance and their current stockpile\n" +
                "Add `-n` to normalize it per city";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        GuildDB db = Locutus.imp().getGuildDB(guild);
        AllianceList alliance = db.getAllianceList();
        if (alliance == null) return "Pleas set " + CM.settings.cmd.create(GuildDB.Key.ALLIANCE_ID.name(), null).toSlashCommand() + "";

        Map<DBNation, Map<ResourceType, Double>> stockpile = alliance.getMemberStockpile();

        List<String> header = new ArrayList<>();
        header.add("nation");
        header.add("discord");
        header.add("cities");
        header.add("avg_infra");
        header.add("off|def");
        header.add("mmr");

        for (ResourceType value : ResourceType.values) {
            header.add(value.name().toLowerCase());
        }

        SpreadSheet sheet = SpreadSheet.create(db, GuildDB.Key.STOCKPILE_SHEET);
        sheet.setHeader(header);

        double[] aaTotal = ResourceType.getBuffer();

        for (Map.Entry<DBNation, Map<ResourceType, Double>> entry : stockpile.entrySet()) {
            List<Object> row = new ArrayList<>();

            DBNation nation = entry.getKey();
            if (nation == null) continue;
            row.add(MarkupUtil.sheetUrl(nation.getNation(), nation.getNationUrl()));
            row.add(nation.getUserDiscriminator());
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
        DiscordUtil.createEmbedCommand(event.getChannel(), "Nation Stockpiles", totalStr);

        sheet.attach(new DiscordChannelIO(event).create()).send();
        return null;
    }
}
