package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.config.Settings;
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
import java.util.LinkedHashMap;
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

        boolean failedFetch = true;
        Map<Integer, TaxBracket> brackets;
        try {
            brackets = db.getAlliance().getTaxBrackets(false);
            failedFetch = false;
        } catch (IllegalArgumentException e) {
            brackets = new LinkedHashMap<>();
            Set<Integer> allianceIds = db.getAllianceIds(true);
            Map<Integer, TaxBracket> allAllianceBrackets = Locutus.imp().getBankDB().getTaxBracketsAndEstimates();
            for (Map.Entry<Integer, TaxBracket> entry : allAllianceBrackets.entrySet()) {
                TaxBracket bracket = entry.getValue();
                if (allianceIds.contains(bracket.getAllianceId(false))) {
                    brackets.put(entry.getKey(), bracket);
                }
            }
        }

        Map<DBNation, TaxBracket> nations = new HashMap<>();
        for (TaxBracket bracket : brackets.values()) {
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

        StringBuilder response = new StringBuilder(sheet.getURL(true, true));
        if (failedFetch) response.append("\nnote: Please set an api key with " + CM.credentials.addApiKey.cmd.toSlashMention() + " to view updated tax brackets");
        return response.toString();
    }
}
