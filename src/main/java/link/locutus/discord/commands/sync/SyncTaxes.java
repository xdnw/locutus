package link.locutus.discord.commands.sync;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class SyncTaxes extends Command {
    public SyncTaxes() {
        super(CommandCategory.ECON, CommandCategory.GOV);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ECON.has(user, server) || Roles.INTERNAL_AFFAIRS.has(user, server);
    }

    @Override
    public String help() {
        return super.help() + " <auto|sheet> [time]";
    }

    @Override
    public String desc() {
        return "Use `sheet` as the first argument to update taxes via the tax sheet\n" +
                "Use `auto` as the first argument to auto update taxes (requires login)";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        GuildDB db = Locutus.imp().getGuildDB(guild);
        if (args.size() >= 1) {
            switch (args.get(0).toLowerCase()) {
                default:
                    SpreadSheet sheet = SpreadSheet.create(args.get(0));
                    if (!args.get(0).startsWith("sheet:") && !args.get(0).startsWith("https://docs.google.com/spreadsheets/")) return Settings.commandPrefix(true) + "synctaxes <sheet-url>";
                    return updateTaxesLegacy(db, sheet);
                case "sheet": {
                    if (args.size() != 1) return usage();
                    if (db.hasAuth() && !Roles.ADMIN.has(author, guild)) {
                        return "No permission. Authentication is enabled for this guild. Please use `" + Settings.commandPrefix(true) + "SyncTaxes auto` or have an admin run this command";
                    }
                    return updateTaxesLegacy(db, null);
                }
                case "legacy": {
                    Set<Integer> ids = db.getAllianceIds();
                    if (ids.isEmpty()) return "No alliance registered to this guild. See " + CM.settings.cmd.create(GuildDB.Key.ALLIANCE_ID.name(), null);
                    int aaId;
                    int offset = 0;
                    if (ids.size() > 1) {
                        if (args.size() != 2) return "!synctaxes legacy <alliance> [time]";
                        aaId = Integer.parseInt(args.get(1));
                        offset = 1;
                    } else {
                        aaId = ids.iterator().next();
                    }
                    Long latestDate = null;
                    if (args.size() >= 2 + offset)
                        latestDate = System.currentTimeMillis() - TimeUtil.timeToSec(args.get(1 + offset)) * 1000L;
                    if (args.size() > 2 + offset) return usage();

                    CompletableFuture<Message> msgFuture = event.getChannel().sendMessage("Syncing taxes for " + db.getAlliance_id() + ". Please wait...").submit();

                    int taxesCount = db.getHandler().updateTaxesLegacy(aaId, latestDate);

                    Message msg = msgFuture.get();
                    RateLimitUtil.queue(event.getChannel().deleteMessageById(msg.getIdLong()));

                    return "Updated " + taxesCount + " records.\n"
                            + "<" + updateTurnGraph(db) + ">";
                }
                case "auto": {
                    List<BankDB.TaxDeposit> taxes = db.getAlliance().updateTaxes();
                    return "Updated " + taxes.size() + " records.";

                }
            }
        }
        SpreadSheet sheet = SpreadSheet.create(db, GuildDB.Key.TAX_SHEET);
        if (db.getOrNull(GuildDB.Key.TAX_SHEET) == null) sheet.set(0, 0);
        return desc() + "\nEnter tax records here: " + sheet.getURL(false, false);
    }

    public String updateTaxesLegacy(GuildDB guildDb, SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (sheet == null) {
            sheet = SpreadSheet.create(guildDb, GuildDB.Key.TAX_SHEET);
        }
        List<List<Object>> rows = sheet.get("A1:Q");

        ResourceType[] resources = {ResourceType.MONEY, ResourceType.FOOD, ResourceType.COAL, ResourceType.OIL, ResourceType.URANIUM, ResourceType.LEAD, ResourceType.IRON, ResourceType.BAUXITE, ResourceType.GASOLINE, ResourceType.MUNITIONS, ResourceType.STEEL, ResourceType.ALUMINUM};

        List<BankDB.TaxDeposit> records = new ArrayList<>();

        for (Iterator<List<Object>> iter = rows.iterator(); iter.hasNext();) {
            List<Object> row = iter.next();

            if (row.size() != 17) continue;
            String indexStr = row.get(0).toString().replace(")", "").trim();
            if (!MathMan.isInteger(indexStr)) continue;

            String[] dateTax = row.get(1).toString().split(" Automated Tax ");
            String dateStr = dateTax[0].toString();
            long date = TimeUtil.parseDate(TimeUtil.MMDDYYYY_HH_MM_A, dateStr);

            String[] taxStr = dateTax[1].replace("%", "").split("/");
            int moneyTax = Integer.parseInt(taxStr[0].trim());
            int resourceTax = Integer.parseInt(taxStr[1].trim());

            String nationName = row.get(2).toString();
            DBNation nation = Locutus.imp().getNationDB().getNationByName(nationName);

            String allianceName = row.get(3).toString();
            allianceName = allianceName.replaceAll(" Bank$", "");
            Integer allianceId = PnwUtil.parseAllianceId(allianceName);

            int nationId;
            if (nation == null || allianceId == null) {
                nationId = 0;
            } else {
                nationId = nation.getNation_id();
            }

            int taxId = Integer.parseInt(row.get(16).toString());

            double[] deposit = new double[ResourceType.values.length];
            int offset = 4;

            for (int j = 0; j < resources.length; j++) {
                ResourceType type = resources[j];
                Double amt = MathMan.parseDouble(row.get(j + offset).toString().trim());
                deposit[type.ordinal()] = amt;
            }

            TaxRate internal = guildDb.getHandler().getInternalTaxrate(nationId);
            BankDB.TaxDeposit taxRecord = new BankDB.TaxDeposit(allianceId, date, 0, taxId, nationId, moneyTax, resourceTax, internal.money, internal.resources, deposit);
            records.add(taxRecord);
        }

        if (records.isEmpty()) {
            return "Please pin, and update this sheet: " + sheet.getURL(false, false);
        }

        Collections.sort(records, new Comparator<BankDB.TaxDeposit>() {
            @Override
            public int compare(BankDB.TaxDeposit o1, BankDB.TaxDeposit o2) {
                if (o1.date == o2.date) {
                    return Integer.compare(o1.nationId, o2.nationId);
                }
                return Long.compare(o1.date, o2.date);
            }
        });

        Iterator<BankDB.TaxDeposit> iter = records.iterator();
        BankDB.TaxDeposit prev = null;
        BankDB.TaxDeposit curr = null;
        while (iter.hasNext()) {
            prev = curr;
            curr = iter.next();
            if (prev == null) continue;
            if (prev.nationId == curr.nationId && prev.date == curr.date) iter.remove();
        }

        BankDB bank = Locutus.imp().getBankDB();
        bank.clearTaxDeposits(Integer.parseInt(guildDb.getOrThrow(GuildDB.Key.ALLIANCE_ID)));

        for (int i = 0; i < records.size(); i++) {
            BankDB.TaxDeposit record = records.get(i);
            record.index = i;
            bank.addTaxDeposit(record);
        }

        return null;
    }

    public String updateTurnGraph(GuildDB db) throws GeneralSecurityException, IOException {
        SpreadSheet sheet = SpreadSheet.create(db, GuildDB.Key.TAX_GRAPH_SHEET);

        List<Object> header = new ArrayList<>();
        header.add("date");
        header.add("total");
        header.add("totalScaled");
        for (ResourceType type : ResourceType.values) {
            if (type != ResourceType.CREDITS) header.add(type.name().toLowerCase());
        }

        sheet.setHeader(header);

        List<BankDB.TaxDeposit> byTurn = Locutus.imp().getBankDB().getTaxesByTurn(db.getOrThrow(GuildDB.Key.ALLIANCE_ID));

        for (BankDB.TaxDeposit deposit : byTurn) {
            header.clear();
            double total = PnwUtil.convertedTotal(deposit.resources);
            double totalScaled = 0;
            if (deposit.resources[0] != 0) {
                totalScaled += PnwUtil.convertedTotal(ResourceType.MONEY, deposit.resources[0]) * 100 / deposit.moneyRate;
            }
            for (int i = 2; i < deposit.resources.length; i++) {
                double amt = deposit.resources[i];
                if (amt > 0) {
                    totalScaled += PnwUtil.convertedTotal(ResourceType.values[i], amt) * 100 / deposit.resourceRate;
                }
            }

            header.add(TimeUtil.format(TimeUtil.MMDDYYYY_HH_MM_A, new Date(deposit.date)));
            header.add(total);
            header.add(totalScaled);

            for (int i = 0; i < deposit.resources.length; i++) {
                ResourceType type = ResourceType.values[i];
                if (type == ResourceType.CREDITS) continue;
                header.add(deposit.resources[i]);
            }

            sheet.addRow(header);
        }

        sheet.set(0, 0);

        return sheet.getURL(true, true);
    }
}
