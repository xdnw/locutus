package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.enums.Rank;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TaxBracketSheet extends Command {
    public TaxBracketSheet() {
        super(CommandCategory.GOV, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.ECON);
    }
    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ECON.has(user, server);
    }

    @Override
    public String help() {
        return super.help();
    }

    @Override
    public String desc() {
        return "Generate a sheet of member tax brackets.\n" +
                "Add `-a` to include applicants\n" +
                "Add `-f` to force an update of deposits\n" +
                "`note: internal tax rate is the TAX_BASE and determines what % of their taxes is excluded from deposits`";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        GuildDB db = Locutus.imp().getGuildDB(guild);

        SpreadSheet sheet = SpreadSheet.create(db, GuildDB.Key.TAX_BRACKET_SHEET);
        List<Object> header = new ArrayList<>(Arrays.asList(
                "nation",
                "position",
                "cities",
                "age",
                "deposits",
                "tax_id",
                "tax_rate",
                "internal"
        ));

        sheet.setHeader(header);

        Auth auth = db.getAuth();
        if (auth == null) return "No authentication enabled for this guild";
        Map<Integer, TaxBracket> brackets = auth.getTaxBrackets();

        Map<DBNation, TaxBracket> nations = new HashMap<>();
        for (TaxBracket bracket : brackets.values()) {
            if (bracket.nations == 0) continue;
            for (DBNation nation : bracket.getNations()) {
                nations.put(nation, bracket);
            }
        }

        db.getAutoRoleTask().updateTaxRoles(nations);

        long threshold = flags.contains('f') ? 0 : Long.MAX_VALUE;

        for (Map.Entry<DBNation, TaxBracket> entry : nations.entrySet()) {
            TaxBracket bracket = entry.getValue();
            DBNation nation = entry.getKey();

            if (!flags.contains('a') && nation.getPosition() <= 1) continue;

            header.set(0, MarkupUtil.sheetUrl(nation.getNation(), nation.getNationUrl()));
            header.set(1, Rank.byId(nation.getPosition()).name());
            header.set(2, nation.getCities());
            header.set(3, nation.getAgeDays());
            header.set(4, String.format("%.2f", nation.getNetDepositsConverted(db, threshold)));
            header.set(5, bracket.taxId + "");
            header.set(6, bracket.moneyRate + "/" + bracket.rssRate);

            TaxRate internal = db.getHandler().getInternalTaxrate(nation.getNation_id());
            header.set(7, internal.toString());

            sheet.addRow(header);
        }

        sheet.clearAll();
        sheet.set(0, 0);

        return "<" + sheet.getURL() + ">";
    }
}
