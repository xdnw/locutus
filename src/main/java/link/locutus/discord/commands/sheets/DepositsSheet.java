package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.PW;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.*;
import java.util.stream.Collectors;

import static link.locutus.discord.apiv1.enums.ResourceType.convertedTotal;
import static link.locutus.discord.apiv1.enums.ResourceType.toString;

public class DepositsSheet extends Command {
    public DepositsSheet() {
        super("DepositsSheet", "DepositSheet", CommandCategory.ECON, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.GOV);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.deposits.sheet.cmd);
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
                "Add `-p` to include all past members\n" +
                "Add `-e` to exclude the escrow sheet";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        GuildDB db = Locutus.imp().getGuildDB(guild);
        String noteFlow = DiscordUtil.parseArg(args, "flow");

        Set<Long> tracked = null;

        Set<DBNation> nations;
        if (args.isEmpty()) {
            nations = null;
        } else {
            nations = (DiscordUtil.parseNations(guild, author, me, args.get(0), false, true));
            if (args.size() == 2) {
                Set<Integer> alliances = DiscordUtil.parseAllianceIds(guild, args.get(1));
                tracked = new LinkedHashSet<>();
                for (Integer alliance : alliances) tracked.add(alliance.longValue());
                tracked = PW.expandCoalition(tracked);
            }
        }

        boolean ignoreTaxBase = flags.contains('b');
        boolean ignoreOffset = flags.contains('o');
        boolean noLoans = flags.contains('l');
        boolean noGrants = flags.contains('g');
        boolean noTaxes = flags.contains('t');
        boolean noDeposits = flags.contains('d');
        Set<Integer> includePastDepositors = flags.contains('p') ? db.getAllianceIds() : null;
        boolean noEscrowSheet = flags.contains('e');

        return BankCommands.depositSheet(
                channel,
                guild,
                db,
                nations,
                tracked == null ? null : new ArrayList<>(tracked).stream().map(f -> DBAlliance.getOrCreate(f.intValue())).collect(Collectors.toSet()),
                ignoreTaxBase,
                ignoreOffset,
                false,
                false,
                noTaxes,
                noLoans,
                noGrants,
                noDeposits,
                includePastDepositors,
                noEscrowSheet,
                noteFlow == null ? null : PWBindings.DepositType(noteFlow),
                flags.contains('f')
        );
//
//        CompletableFuture<IMessageBuilder> msgFuture = channel.sendMessage("Please wait...");
//
//        SpreadSheet sheet = SpreadSheet.create(db, SheetKeys.DEPOSITS_SHEET);
//
//        List<Object> header = new ArrayList<>(Arrays.asList(
//            "nation",
//            "cities",
//            "age",
//            "deposit",
//            "tax",
//            "loan",
//            "grant",
//            "total",
//            "last_deposit_day"
//        ));
//
//        for (ResourceType type : ResourceType.values()) {
//            if (type == ResourceType.CREDITS) continue;
//            header.add(type.name());
//        }
//
//        sheet.setHeader(header);
//

//
//        double[] aaTotalPositive = ResourceType.getBuffer();
//        double[] aaTotalNet = ResourceType.getBuffer();
//
//        long last = System.currentTimeMillis();
//        for (DBNation nation : nations) {
//            if (System.currentTimeMillis() - last > 10000) {
//                IMessageBuilder msg = msgFuture.get();
//                if (msg != null) {
//                    msg.clear();
//                    msg.append("calculating for: " + nation.getNation()).send();
//                }
//                last = System.currentTimeMillis();
//            }
//            Map<DepositType, double[]> deposits = nation.getDeposits(db, tracked, useTaxBase, useOffset, 0L, 0L);
//            double[] buffer = ResourceType.getBuffer();
//
//            header.set(0, MarkupUtil.sheetUrl(nation.getNation(), nation.getNationUrl()));
//            header.set(1, nation.getCities());
//            header.set(2, nation.getAgeDays());
//            header.set(3, String.format("%.2f", PW.convertedTotal(deposits.getOrDefault(DepositType.DEPOSIT, buffer))));
//            header.set(4, String.format("%.2f", PW.convertedTotal(deposits.getOrDefault(DepositType.TAX, buffer))));
//            header.set(5, String.format("%.2f", PW.convertedTotal(deposits.getOrDefault(DepositType.LOAN, buffer))));
//            header.set(6, String.format("%.2f", PW.convertedTotal(deposits.getOrDefault(DepositType.GRANT, buffer))));
//            double[] total = ResourceType.getBuffer();
//            for (Map.Entry<DepositType, double[]> entry : deposits.entrySet()) {
//                switch (entry.getKey()) {
//                    case GRANT:
//                        if (noGrants) continue;
//                        break;
//                    case LOAN:
//                        if (noLoans) continue;
//                        break;
//                    case TAX:
//                        if (noTaxes) continue;
//                        break;
//                    case DEPOSIT:
//                        if (noDeposits) continue;
//                        break;
//                }
//                double[] value = entry.getValue();
//                total = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, total, value);
//            }
//            header.set(7, String.format("%.2f", PW.convertedTotal(total)));
//            List<Transaction2> transactions = nation.getTransactions(Long.MAX_VALUE);
//            long lastDeposit = 0;
//            for (Transaction2 transaction : transactions) {
//                if (transaction.sender_id == nation.getNation_id()) {
//                    lastDeposit = Math.max(transaction.tx_datetime, lastDeposit);
//                }
//            }
//            if (lastDeposit == 0) {
//                header.set(8, "NEVER");
//            } else {
//                long days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastDeposit);
//                header.set(8, days);
//            }
//            int i = 9;
//            for (ResourceType type : ResourceType.values) {
//                if (type == ResourceType.CREDITS) continue;
//                header.set((i++), total[type.ordinal()]);
//            }
//            double[] normalized = PW.normalize(total);
//            if (PW.convertedTotal(normalized) > 0) {
//                aaTotalPositive = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, aaTotalPositive, normalized);
//            }
//            aaTotalNet = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, aaTotalNet, total);
//            sheet.addRow(header);
//        }
//
//        sheet.clearAll();
//        sheet.write();
//
//        StringBuilder footer = new StringBuilder();
//        footer.append(PW.resourcesToFancyString(aaTotalPositive, "Nation Deposits (" + nations.size() + " nations)"));
//
//        String type = "";
//        OffshoreInstance offshore = db.getOffshore();
//        double[] aaDeposits;
//        if (offshore != null && offshore.getGuildDB() != db) {
//            type = "offshored";
//            aaDeposits = offshore.getDeposits(db);
//        } else if (db.isValidAlliance()){
//            type = "bank stockpile";
//            aaDeposits = PW.resourcesToArray(db.getAllianceList().getStockpile());
//        } else aaDeposits = null;
//        if (aaDeposits != null) {
//            if (PW.convertedTotal(aaDeposits) > 0) {
//                for (int i = 0; i < aaDeposits.length; i++) {
//                    aaTotalNet[i] = aaDeposits[i] - aaTotalNet[i];
//                    aaTotalPositive[i] = aaDeposits[i] - aaTotalPositive[i];
//
//                }
//                footer.append("\n**Total " + type + "- nation deposits (negatives normalized)**:  Worth: $" + MathMan.format(PW.convertedTotal(aaTotalPositive)) + "\n`" + PW.resourcesToString(aaTotalPositive) + "`");
//                footer.append("\n**Total " + type + "- nation deposits**:  Worth: $" + MathMan.format(PW.convertedTotal(aaTotalNet)) + "\n`" + PW.resourcesToString(aaTotalNet) + "`");
//            } else {
//                footer.append("\n**No funds are currently " + type + "**");
//            }
//        }
//
//        sheet.attach(channel.create(), footer.toString()).send();
//        return null;
    }
}
