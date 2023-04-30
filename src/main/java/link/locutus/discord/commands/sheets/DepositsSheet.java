package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static link.locutus.discord.util.PnwUtil.convertedTotal;
import static link.locutus.discord.util.PnwUtil.resourcesToString;

public class DepositsSheet extends Command {
    public DepositsSheet() {
        super("DepositsSheet", "DepositSheet", CommandCategory.ECON, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.GOV);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ECON.has(user, server);
    }

    @Override
    public String help() {
        return super.help() + " [nations] [offshores]";
    }

    @Override
    public String desc() {
        return "Get a list of nations and their deposits.\n" +
                "Add `-b` to use 0/0 as the tax base\n" +
                "Add `-o` to not include any manual deposit offsets\n" +
                "Add `-d` to not include deposits\n" +
                "Add `-t` to not include taxes\n" +
                "Add `-l` to not include loans\n" +
                "Add `-g` to not include grants`\n" +
                "Add `-f` to force an update\n" +
                "Add `-p` to include all past members";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        GuildDB db = Locutus.imp().getGuildDB(guild);

        Message message = RateLimitUtil.complete(event.getChannel().sendMessage("Please wait..."));

        SpreadSheet sheet = SpreadSheet.create(db, GuildDB.Key.DEPOSITS_SHEET);

        List<Object> header = new ArrayList<>(Arrays.asList(
            "nation",
            "cities",
            "age",
            "deposit",
            "tax",
            "loan",
            "grant",
            "total",
            "last_deposit_day"
        ));

        for (ResourceType type : ResourceType.values()) {
            if (type == ResourceType.CREDITS) continue;
            header.add(type.name());
        }

        sheet.setHeader(header);

        boolean useTaxBase = !flags.contains('b');
        boolean useOffset = !flags.contains('o');
        boolean noLoans = flags.contains('l');
        boolean noGrants = flags.contains('g');
        boolean noTaxes = flags.contains('t');
        boolean noDeposits = flags.contains('d');

        Set<Long> tracked = null;

        Set<DBNation> nations;
        if (args.isEmpty()) {
            Set<Integer> aaIds = db.getAllianceIds();
            if (!aaIds.isEmpty()) {
                nations = Locutus.imp().getNationDB().getNations(aaIds);
                nations.removeIf(n -> n.getPosition() <= 1);

                if (flags.contains('p')) {
                    Set<Integer> ids = Locutus.imp().getBankDB().getReceiverNationIdFromAllianceReceivers(aaIds);
                    for (int id : ids) {
                        DBNation nation = Locutus.imp().getNationDB().getNation(id);
                        if (nation != null) nations.add(nation);
                    }
                }
            } else {
                Role role = Roles.MEMBER.toRole(guild);
                if (role == null) throw new IllegalArgumentException("No " + CM.settings.cmd.create(GuildDB.Key.ALLIANCE_ID.name(), null, null, null).toSlashCommand() + " set, or " + CM.role.setAlias.cmd.create(Roles.MEMBER.name(), "", null, null) + " set");
                nations = new HashSet<>();
                for (Member member : guild.getMembersWithRoles(role)) {
                    DBNation nation = DiscordUtil.getNation(member.getUser());
                    if (nation != null) {
                        nations.add(nation);
                    }
                }
                if (nations.isEmpty()) return "No members found";
            }
        } else if (args.size() >= 1) {
            nations = (DiscordUtil.parseNations(guild, args.get(0)));
            if (args.size() == 2) {
                Set<Integer> alliances = DiscordUtil.parseAlliances(guild, args.get(1));
                tracked = new LinkedHashSet<>();
                for (Integer alliance : alliances) tracked.add(alliance.longValue());
                tracked = PnwUtil.expandCoalition(tracked);
            }
        } else {
            return usage(event);
        }

        double[] aaTotalPositive = ResourceType.getBuffer();
        double[] aaTotalNet = ResourceType.getBuffer();

        long last = System.currentTimeMillis();
        for (DBNation nation : nations) {
            if (System.currentTimeMillis() - last > 10000) {
                RateLimitUtil.queue(event.getChannel().editMessageById(message.getIdLong(), "calculating for: " + nation.getNation()));
                last = System.currentTimeMillis();
            }
            Map<DepositType, double[]> deposits = nation.getDeposits(db, tracked, useTaxBase, useOffset, 0L, 0L);
            double[] buffer = ResourceType.getBuffer();

            header.set(0, MarkupUtil.sheetUrl(nation.getNation(), nation.getNationUrl()));
            header.set(1, nation.getCities());
            header.set(2, nation.getAgeDays());
            header.set(3, String.format("%.2f", PnwUtil.convertedTotal(deposits.getOrDefault(DepositType.DEPOSIT, buffer))));
            header.set(4, String.format("%.2f", PnwUtil.convertedTotal(deposits.getOrDefault(DepositType.TAX, buffer))));
            header.set(5, String.format("%.2f", PnwUtil.convertedTotal(deposits.getOrDefault(DepositType.LOAN, buffer))));
            header.set(6, String.format("%.2f", PnwUtil.convertedTotal(deposits.getOrDefault(DepositType.GRANT, buffer))));
            double[] total = ResourceType.getBuffer();
            for (Map.Entry<DepositType, double[]> entry : deposits.entrySet()) {
                switch (entry.getKey()) {
                    case GRANT:
                        if (noGrants) continue;
                        break;
                    case LOAN:
                        if (noLoans) continue;
                        break;
                    case TAX:
                        if (noTaxes) continue;
                        break;
                    case DEPOSIT:
                        if (noDeposits) continue;
                        break;
                }
                double[] value = entry.getValue();
                total = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, total, value);
            }
            header.set(7, String.format("%.2f", PnwUtil.convertedTotal(total)));
            List<Transaction2> transactions = nation.getTransactions(Long.MAX_VALUE);
            long lastDeposit = 0;
            for (Transaction2 transaction : transactions) {
                if (transaction.sender_id == nation.getNation_id()) {
                    lastDeposit = Math.max(transaction.tx_datetime, lastDeposit);
                }
            }
            if (lastDeposit == 0) {
                header.set(8, "NEVER");
            } else {
                long days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastDeposit);
                header.set(8, days);
            }
            int i = 9;
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                header.set((i++), total[type.ordinal()]);
            }
            double[] normalized = PnwUtil.normalize(total);
            if (PnwUtil.convertedTotal(normalized) > 0) {
                aaTotalPositive = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, aaTotalPositive, normalized);
            }
            aaTotalNet = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, aaTotalNet, total);
            sheet.addRow(header);
        }

        sheet.clearAll();
        sheet.set(0, 0);

        StringBuilder footer = new StringBuilder();
        footer.append(PnwUtil.resourcesToFancyString(aaTotalPositive, "Nation Deposits (" + nations.size() + " nations)"));

        String type = "";
        OffshoreInstance offshore = db.getOffshore();
        double[] aaDeposits;
        if (offshore != null && offshore.getGuildDB() != db) {
            type = "offshored";
            aaDeposits = offshore.getDeposits(db);
        } else if (db.isValidAlliance()){
            type = "bank stockpile";
            aaDeposits = PnwUtil.resourcesToArray(db.getAllianceList().getStockpile());
        } else aaDeposits = null;
        if (aaDeposits != null) {
            if (PnwUtil.convertedTotal(aaDeposits) > 0) {
                for (int i = 0; i < aaDeposits.length; i++) {
                    aaTotalNet[i] = aaDeposits[i] - aaTotalNet[i];
                    aaTotalPositive[i] = aaDeposits[i] - aaTotalPositive[i];

                }
                footer.append("\n**Total " + type + " - nation deposits (negatives normalized)**:  Worth: $" + MathMan.format(PnwUtil.convertedTotal(aaTotalPositive)) + "\n`" + PnwUtil.resourcesToString(aaTotalPositive) + "`");
                footer.append("\n**Total " + type + " - nation deposits**:  Worth: $" + MathMan.format(PnwUtil.convertedTotal(aaTotalNet)) + "\n`" + PnwUtil.resourcesToString(aaTotalNet) + "`");
            } else {
                footer.append("\n**No funds are currently " + type + "**");
            }
        }

        sheet.attach(new DiscordChannelIO(event).create(), footer.toString()).send();
        return null;
    }
}
