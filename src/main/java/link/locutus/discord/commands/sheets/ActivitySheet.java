package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Activity;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class ActivitySheet extends Command {
    public ActivitySheet() {
        super(CommandCategory.INTERNAL_AFFAIRS, CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.GOV);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return (super.checkPermission(server, user) && Roles.MILCOM.has(user, server));
    }

    @Override
    public String help() {
        return super.help() + " <nations>";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        String sheetUrl = DiscordUtil.parseArg(args, "sheet");
        if (args.size() != 1) return usage(event);

        Set<DBNation> nations = DiscordUtil.parseNations(guild, args.get(0));

        GuildDB db = Locutus.imp().getGuildDB(event);

        SpreadSheet sheet;
        if (sheetUrl != null) {
            sheet = SpreadSheet.create(sheetUrl);
        } else {
            sheet = SpreadSheet.create(db, GuildDB.Key.ACTIVITY_SHEET);
        }

        List<Object> header = new ArrayList<>(Arrays.asList(
                "nation",
                "alliance",
                "\uD83C\uDFD9", // cities
                "\uD83C\uDFD7", // avg_infra
                "score",
                "Mo",
                "Tu",
                "We",
                "Th",
                "Fr",
                "Sa",
                "Su"
        ));
        for (int i = 0; i < 12; i++) {
            header.add((i + 1) + "");
        }

        sheet.setHeader(header);

        for (DBNation nation : nations) {

            header.set(0, MarkupUtil.sheetUrl(nation.getNation(), PnwUtil.getUrl(nation.getNation_id(), false)));
            header.set(1, MarkupUtil.sheetUrl(nation.getAlliance(), PnwUtil.getUrl(nation.getAlliance_id(), true)));
            header.set(2, nation.getCities());
            header.set(3, nation.getAvg_infra());
            header.set(4, nation.getScore());

            Activity activity = nation.getActivity(14 * 12);
            double[] byDay = activity.getByDay();
            double[] byDayTurn = activity.getByDayTurn();

            for (int i = 0; i < byDay.length; i++) {
                header.set(5 + i, byDay[i] * 100);
            }

            for (int i = 0; i < byDayTurn.length; i++) {
                header.set(5 + byDay.length + i, byDayTurn[i] * 100);
            }

            sheet.addRow(header);
        }

        sheet.clearAll();
        sheet.set(0, 0);

        return "<" + sheet.getURL() + ">";
    }
}
