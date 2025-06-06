package link.locutus.discord.commands.sync;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.TaxDeposit;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

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
    public List<CommandRef> getSlashReference() {
        return List.of(CM.admin.sync2.taxes.cmd);
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
        return """
                Use `sheet` as the first argument to update taxes via the tax sheet
                Use `auto` as the first argument to auto update taxes via api
                Use `legacy` as the first argument to update taxes via login""";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        GuildDB db = Locutus.imp().getGuildDB(guild);
        if (args.size() >= 1) {
            switch (args.get(0).toLowerCase()) {
                default:
                    return usage();
                case "sheet": {
                    Set<Integer> aaIds = db.getAllianceIds();
                    if (args.size() < 2 || (args.size() != 3 && aaIds.size() != 1)) return Settings.commandPrefix(true) + "SyncTaxes sheet <sheet> [alliance]";
                    int aaId;
                    if (args.size() >= 3) {
                        aaId = Integer.parseInt(args.get(2));
                        if (!aaIds.contains(aaId)) return "Alliance AA:" + aaId + " is not registered to guild: " + StringMan.getString(aaIds);
                    } else {
                        aaId = aaIds.iterator().next();
                    }
                    return updateTaxesLegacy(db, null, aaId);
                }
                case "legacy": {
                    Set<Integer> ids = db.getAllianceIds();
                    if (ids.isEmpty()) return "No alliance registered to this guild. See " + GuildKey.ALLIANCE_ID.getCommandMention();
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

                    DBAlliance aa = DBAlliance.get(aaId);
                    if (aa == null) {
                        throw new IllegalArgumentException("Alliance AA:" + aaId + " is not registered to guild: " + StringMan.getString(ids));
                    }

                    CompletableFuture<IMessageBuilder> msgFuture = (channel.sendMessage("Syncing taxes for " + StringMan.getString(ids) + ". Please wait..."));

                    int taxesCount = aa.updateTaxesLegacy(latestDate);

                    IMessageBuilder msg = msgFuture.get();
                    if (msg != null && msg.getId() > 0) {
                        channel.delete(msg.getId());
                    }

                    return "Updated " + taxesCount + " records.\n"
                            + "<" + updateTurnGraph(db, aaId) + ">";
                }
                case "auto": {
                    Long startDate = null;
                    if (args.size() >= 2) {
                        startDate = System.currentTimeMillis() - TimeUtil.timeToSec(args.get(1)) * 1000L;
                    }
                    AllianceList aaList = db.getAllianceList();
                    if (aaList == null) {
                        return "No alliance registered to this guild. See " + GuildKey.ALLIANCE_ID.getCommandMention();
                    }
                    List<TaxDeposit> taxes = aaList.updateTaxes(startDate);
                    return "Updated " + taxes.size() + " records.";
                }
            }
        }
        SpreadSheet sheet = SpreadSheet.create(db, SheetKey.TAX_SHEET);
        if (db.getInfo(SheetKey.TAX_SHEET, true) == null) sheet.updateWrite();
        return desc() + "\nEnter tax records here: " + sheet.getURL(false, false);
    }

    public static String updateTaxesLegacy(GuildDB guildDb, SpreadSheet sheet, int aaId) throws GeneralSecurityException, IOException {
        if (sheet == null) {
            sheet = SpreadSheet.create(guildDb, SheetKey.TAX_SHEET);
        }
        List<List<Object>> rows = sheet.fetchRange(null, "A1:Q");

        ResourceType[] resources = {ResourceType.MONEY, ResourceType.FOOD, ResourceType.COAL, ResourceType.OIL, ResourceType.URANIUM, ResourceType.LEAD, ResourceType.IRON, ResourceType.BAUXITE, ResourceType.GASOLINE, ResourceType.MUNITIONS, ResourceType.STEEL, ResourceType.ALUMINUM};

        List<TaxDeposit> records = new ArrayList<>();

        for (List<Object> row : rows) {
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
            Integer allianceId = PW.parseAllianceId(allianceName);

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
            TaxDeposit taxRecord = new TaxDeposit(allianceId, date, 0, taxId, nationId, moneyTax, resourceTax, internal.money, internal.resources, deposit);
            records.add(taxRecord);
        }

        if (records.isEmpty()) {
            return "Please pin, and update this sheet: " + sheet.getURL(false, false);
        }

        Collections.sort(records, new Comparator<TaxDeposit>() {
            @Override
            public int compare(TaxDeposit o1, TaxDeposit o2) {
                if (o1.date == o2.date) {
                    return Integer.compare(o1.nationId, o2.nationId);
                }
                return Long.compare(o1.date, o2.date);
            }
        });

        Iterator<TaxDeposit> iter = records.iterator();
        TaxDeposit prev = null;
        TaxDeposit curr = null;
        while (iter.hasNext()) {
            prev = curr;
            curr = iter.next();
            if (prev == null) continue;
            if (prev.nationId == curr.nationId && prev.date == curr.date) iter.remove();
        }

        BankDB bank = Locutus.imp().getBankDB();
        bank.clearTaxDeposits(aaId);

        for (int i = 0; i < records.size(); i++) {
            TaxDeposit record = records.get(i);
            record.index = i;
            bank.addTaxDeposit(record);
        }

        return null;
    }

    public static String updateTurnGraph(GuildDB db, int aaId) throws GeneralSecurityException, IOException {
        SpreadSheet sheet = SpreadSheet.create(db, SheetKey.TAX_GRAPH_SHEET);

        List<Object> header = new ArrayList<>();
        header.add("date");
        header.add("total");
        header.add("totalScaled");
        for (ResourceType type : ResourceType.values) {
            if (type != ResourceType.CREDITS) header.add(type.name().toLowerCase());
        }

        sheet.setHeader(header);

        List<TaxDeposit> byTurn = Locutus.imp().getBankDB().getTaxesByTurn(aaId);

        for (TaxDeposit deposit : byTurn) {
            header.clear();
            double total = ResourceType.convertedTotal(deposit.resources);
            double totalScaled = 0;
            if (deposit.resources[0] != 0) {
                totalScaled += ResourceType.convertedTotal(ResourceType.MONEY, deposit.resources[0]) * 100 / deposit.moneyRate;
            }
            for (int i = 2; i < deposit.resources.length; i++) {
                double amt = deposit.resources[i];
                if (amt > 0) {
                    totalScaled += ResourceType.convertedTotal(ResourceType.values[i], amt) * 100 / deposit.resourceRate;
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

        sheet.updateWrite();

        return sheet.getURL(true, true);
    }
}
