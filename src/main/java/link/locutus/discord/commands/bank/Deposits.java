package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.TimeUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class Deposits extends Command {
    public Deposits() {
        super("deposits", "depo", "holdings", CommandCategory.ECON);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.deposits.check.cmd);
    }
    @Override
    public String help() {
        return "`" + Settings.commandPrefix(true) + "deposits <nation|alliance|*>` or `" + Settings.commandPrefix(true) + "deposits <nation|alliance|*> [offshores]` e.g. `" + Settings.commandPrefix(true) + "deposits @user`";
    }

    @Override
    public String desc() {
        return "Calculate a nations deposits/loans/taxes\n" +
                "Add `-b` to include base taxes\n" +
                "Add `-o` to not include any manual offset\n" +
                "Add e.g. `\"date>05/01/2019 11:21 pm\"` to filter by date\n" +
//                "Add `-l` to only include the largest positive value of each rss in the total\n" +
                "Add `-i` to include nation transfers with #ignore\n" +
                "Add `-e` to include expired nation transfers\n" +
                "Add `-t` to show taxes separately (See flag: `-b` and `!synctaxes`)\n\n" +
                "Note: Use `" + Settings.commandPrefix(true) + "synctaxes` to update tax records\n" +
                "Add `-d` to show results in dm\n" +
                "Add `-h` to hide escrow balance\n" +
                "Add `-r` to show expiring records";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        GuildDB guildDb = Locutus.imp().getGuildDB(guild);
        guild = guildDb.getGuild();

        DBNation banker = me;
        if (banker == null) {
            return "Please use " + CM.register.cmd.toSlashMention();
        }
//        String requiredNote = DiscordUtil.parseArg(args, "note");
        boolean includeIgnored = flags.contains('i');
        boolean includeExpired = flags.contains('e');

        banker.setMeta(NationMeta.INTERVIEW_DEPOSITS, (byte) 1);

        long cutOff = 0;
        long endTime = Long.MAX_VALUE;
        Iterator<String> iterator = args.iterator();
        while (iterator.hasNext()) {
            String arg = iterator.next();
            if (arg.startsWith("date>")) {
                String dateStr = arg.split(">")[1];
                boolean equal = dateStr.startsWith("=");
                if (equal) dateStr = dateStr.substring(1);

                cutOff = TimeUtil.parseDate(TimeUtil.MMDDYYYY_HH_MM_A, dateStr);
                if (equal) cutOff -= 1;
                iterator.remove();
            }
            if (arg.startsWith("date<")) {
                String dateStr = arg.split("<")[1];
                boolean equal = dateStr.startsWith("=");
                if (equal) dateStr = dateStr.substring(1);

                endTime = TimeUtil.parseDate(TimeUtil.MMDDYYYY_HH_MM_A, dateStr);
                if (equal) endTime += 1;
                iterator.remove();
            }
        }

        if (args.size() != 1 && args.size() != 2) {
            return usage(args.size(), 1, 2, channel);
        }

        return BankCommands.deposits(
                guild,
                guildDb,
                channel,
                banker,
                author,
                (args.get(0).equalsIgnoreCase("*")) ? guildDb : PWBindings.nationOrAllianceOrGuildOrTaxId(args.get(0)),
                args.size() == 2 ? PWBindings.alliances(guild, args.get(1), author, me) : null,
                cutOff != 0 ? cutOff : null,
                endTime != Long.MAX_VALUE ? endTime : null,
                flags.contains('b'),
                flags.contains('o'),
                flags.contains('t'),
                flags.contains('d'),
                includeExpired,
                includeIgnored,
                false,
                flags.contains('h'),
                flags.contains('r')
        );
//
//        Map<String, Map<DepositType, double[]>> accountDeposits = new HashMap<>();
//
//        String arg0 = args.get(0);
//
//        Set<String> split = new ObjectLinkedOpenHashSet<>(Arrays.asList(arg0.split(",")));
//
//        StringBuilder response = new StringBuilder();
//        response.append("**").append(arg0).append("**:\n");
//
//        List<String> footers = new ArrayList<>();
//
//        DBNation requiredUser = null;
//
//        Set<Long> tracked = null;
//        if (args.size() == 2) {
//            tracked = new ObjectLinkedOpenHashSet<>();
//            Set<Integer> alliances = DiscordUtil.parseAlliances(guild, args.get(1));
//            if (alliances == null || alliances.isEmpty()) {
//                return "Invalid alliance: `" + args.get(1) + "`";
//            }
//            for (Integer alliance : alliances) tracked.add(alliance.longValue());
//            tracked = PW.expandCoalition(tracked);
//        }
//
//        for (String arg : split) {
//            DBNation nation = DiscordUtil.parseNation(arg);
//            if (arg.matches(".*tax_id[=:].*")) {
//                int taxId = PW.parseTaxId(arg);
//
//                Map<DepositType, double[]> deposits = guildDb.getTaxBracketDeposits(taxId, cutOff, includeExpired, includeIgnored);
//                accountDeposits.put("tax_id=" + taxId, deposits);
//            } else if (arg.equalsIgnoreCase("*")) {
//                OffshoreInstance offshore = guildDb.getOffshore();
//                if (offshore == null) return "No offshore found";
//                if (!Roles.ECON_STAFF.has(author, guild) && !Roles.ECON_STAFF.has(author, offshore.getGuildDB().getGuild()))
//                    return "You do not have permission to check this guild's deposits";
//                double[] deposits = offshore.getDeposits(guildDb);
//                accountDeposits.put("*", Collections.singletonMap(DepositType.DEPOSIT, deposits));
//            } else if (nation == null && MathMan.isInteger(arg) && Long.parseLong(arg) > Integer.MAX_VALUE) {
//                long id = Long.parseLong(arg);
//                GuildDB otherDb = Locutus.imp().getGuildDB(id);
//                if (otherDb != null) {
//                    OffshoreInstance offshore = otherDb.getOffshore();
//                    if (offshore == null)
//                        return "No offshore is set. In this server, use " + CM.coalition.add.cmd.create(otherDb.getIdLong() + "", Coalition.OFFSHORE.name()) + " and from the offshore server use " + CM.coalition.add.cmd.create(otherDb.getIdLong() + "", Coalition.OFFSHORING.name()) + "";
//                    if (!Roles.ECON_STAFF.has(author, offshore.getGuildDB().getGuild()) && !Roles.ECON_STAFF.has(author, otherDb.getGuild()))
//                        return "You do not have permission to check another guild's deposits";
//
//                    double[] deposits = offshore.getDeposits(otherDb);
//                    String name = otherDb.getGuild().getName();
//                    accountDeposits.put(name, Collections.singletonMap(DepositType.DEPOSIT, deposits));
//                } else if (!guildDb.isOffshore()) {
//                    return "Unknown guild: : `" + arg + "`";
//                } else {
//                    if (!Roles.ECON.has(author, guildDb.getGuild())) {
//                        return "You do not have permission to check another guild's deposits";
//                    }
//                    OffshoreInstance offshore = guildDb.getOffshore();
//                    double[] deposits = PW.resourcesToArray(offshore.getDeposits(id, true));
//                    String name = id + "";
//                    accountDeposits.put(name + "(removed guild)", Collections.singletonMap(DepositType.DEPOSIT, deposits));
//                }
//            } else if (nation == null && PW.parseAllianceId(arg) != null) {
//                Integer allianceId = PW.parseAllianceId(arg);
//                DBAlliance alliance = allianceId == null ? null : DBAlliance.get(allianceId);
//                if (alliance == null) return "Invalid alliance: `" + arg + "`";
//                GuildDB otherDb = Locutus.imp().getGuildDBByAA(allianceId);
//                OffshoreInstance offshore;
//                if (otherDb != null) {
//                    offshore = otherDb.getOffshore();
//                    if (offshore == null) {
//                        if (flags.contains('f')) {
//                            offshore = alliance.getBank();
//                        }
//                        if (offshore == null) {
//                            return "No offshore set";
//                        }
//                    }
//                } else if (!guildDb.isOffshore()) {
//                    return "Unknown guild for AA:" + allianceId;
//                } else {
//                    offshore = guildDb.getOffshore();
//                }
//                if (!Roles.ECON_STAFF.has(author, offshore.getGuildDB().getGuild()) && !Roles.ECON_STAFF.has(author, otherDb.getGuild()))
//                    return "You do not have permisssion to check another alliance's deposits";
//
//                String name = PW.getName(allianceId, true);
//                double[] deposits = PW.resourcesToArray(offshore.getDeposits(allianceId, true));
//                accountDeposits.put(name, Collections.singletonMap(DepositType.DEPOSIT, deposits));
//            } else {
//                if (nation == null && arg.contains("/nation/") && DiscordUtil.parseNationId(arg) != null) {
//                    nation = new DBNation();
//                    nation.setNation_id(DiscordUtil.parseNationId(arg));
//                }
//                if (nation == null) return "Nation not found: `" + arg + "`";
//                if (split.size() == 1) requiredUser = nation;
//                if (nation.getNation_id() != me.getNation_id() && !Roles.INTERNAL_AFFAIRS.has(author, guild) && !Roles.INTERNAL_AFFAIRS_STAFF.has(author, guild) && !Roles.ECON_STAFF.has(author, guild)) return "You do not have permission to check other nation's deposits";
//
//                Map<DepositType, double[]> nationDepo = nation.getDeposits(guildDb,
//                        tracked,
//                        !flags.contains('b'),
//                        !flags.contains('o'),
//                        0L,
//                        cutOff,
//                        includeIgnored,
//                        includeExpired,
//                        f -> requiredNote == null || (f.note == null || f.note.toLowerCase().contains(requiredNote.toLowerCase())));
//                accountDeposits.put(nation.getNation(), nationDepo);
//            }
//        }
//
//        GuildDB db = Locutus.imp().getGuildDB(guild);
//
//        boolean largest = flags.contains('l');
//
//        double[] total = new double[ResourceType.values.length];
//        double[] totalNoGrants = new double[ResourceType.values.length];
//        double[] taxAndDeposits = new double[ResourceType.values.length];
//        Map<DepositType, double[]> categorized = new HashMap<>();
//
//        for (Map<DepositType, double[]> accountDeposit : accountDeposits.values()) {
//            for (Map.Entry<DepositType, double[]> entry : accountDeposit.entrySet()) {
//                DepositType type = entry.getKey();
//                double[] existing = categorized.computeIfAbsent(type, f -> new double[ResourceType.values.length]);
//                double[] current = entry.getValue();
//
//                for (int i = 0; i < existing.length; i++) {
//                    if (largest) {
//                        existing[i] = Math.max(existing[i], current[i]);
//                        total[i] = Math.max(total[i], current[i]);
//                        if (type != DepositType.GRANT) {
//                            totalNoGrants[i] = Math.max(totalNoGrants[i], current[i]);
//                            if (type != DepositType.LOAN) {
//                                taxAndDeposits[i] = Math.max(taxAndDeposits[i], current[i]);
//                            }
//                        }
//                    }
//                    else {
//                        existing[i] += current[i];
//                        total[i] += current[i];
//                        if (type != DepositType.GRANT) {
//                            totalNoGrants[i] += current[i];
//                            if (type != DepositType.LOAN) {
//                                taxAndDeposits[i] += current[i];
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        footers.add("value is based on current market prices");
//
//        if (flags.contains('t') || db.getOrNull(GuildKey.DISPLAY_ITEMIZED_DEPOSITS) == Boolean.TRUE) {
//            if (categorized.containsKey(DepositType.DEPOSIT)) {
//                response.append("#DEPOSIT: (worth $" + MathMan.format(PW.convertedTotal(categorized.get(DepositType.DEPOSIT))) + ")");
//                response.append("\n```").append(PW.resourcesToString(categorized.get(DepositType.DEPOSIT))).append("``` ");
//            }
//            if (categorized.containsKey(DepositType.TAX)) {
//                response.append("#TAX (worth $").append(MathMan.format(PW.convertedTotal(categorized.get(DepositType.TAX)))).append(")");
//                response.append("\n```").append(PW.resourcesToString(categorized.get(DepositType.TAX))).append("``` ");
//            }
//            if (categorized.containsKey(DepositType.LOAN)) {
//                response.append("#LOAN/#GRANT (worth $").append(MathMan.format(PW.convertedTotal(categorized.get(DepositType.LOAN)))).append(")");
//                response.append("\n```").append(PW.resourcesToString(categorized.get(DepositType.LOAN))).append("``` ");
//            }
//            if (categorized.containsKey(DepositType.GRANT)) {
//                response.append("#EXPIRE (worth $").append(MathMan.format(PW.convertedTotal(categorized.get(DepositType.GRANT)))).append(")");
//                response.append("\n```").append(PW.resourcesToString(categorized.get(DepositType.GRANT))).append("``` ");
//            }
//            if (categorized.size() > 1) {
//                response.append("Total: (worth: $").append(MathMan.format(PW.convertedTotal(total))).append(")");
//                response.append("\n```").append(PW.resourcesToString(total)).append("``` ");
//            }
//        } else {
//            String totalTitle = "Total (`#expire`|`#loan`|`#tax`|`#deposit`: worth $";
//            String noGrantTitle = "Excluding `#expire` (worth: $";
//            String safekeepTitle = "Safekeep (`#tax`|`#deposit`: worth $";
//            boolean hasPriorCategory = false;
//            if (categorized.containsKey(DepositType.GRANT)) {
//                response.append(totalTitle).append(MathMan.format(PW.convertedTotal(total))).append(")");
//                response.append("\n```").append(PW.resourcesToString(total)).append("``` ");
//                footers.add("Unlike loans, debt from grants will expire if you stay (see the transaction for the timeframe)");
//                hasPriorCategory = true;
//            }
//            if (categorized.containsKey(DepositType.LOAN)) {
//                response.append(hasPriorCategory ? noGrantTitle : totalTitle).append(MathMan.format(PW.convertedTotal(totalNoGrants))).append(")");
//                response.append("\n```").append(PW.resourcesToString(totalNoGrants)).append("``` ");
//                hasPriorCategory = true;
//            }
//
//            response.append(hasPriorCategory ? safekeepTitle : totalTitle).append(MathMan.format(PW.convertedTotal(taxAndDeposits))).append(")");
//            response.append("\n```").append(PW.resourcesToString(taxAndDeposits)).append("``` ");
//        }
//        if (requiredUser != null && me != null && requiredUser.getNation_id() == me.getNation_id()) {
//            footers.add("Funds default to #deposit if no other note is used.");
//            if (Boolean.TRUE.equals(guildDb.getOrNull(GuildKey.RESOURCE_CONVERSION))) {
//                footers.add("You can sell resources to the alliance by depositing with the note #cash.");
//            }
//            if (PW.convertedTotal(total) > 0 && Boolean.TRUE.equals(guildDb.getOrNull(GuildKey.MEMBER_CAN_WITHDRAW))) {
//                Role role = Roles.ECON_WITHDRAW_SELF.toRole(guild);
//                if (guild.getMember(author).getRoles().contains(role)) {
//                    footers.add("To withdraw, use: `" + CM.transfer.self.cmd.toSlashMention() + "` ");
//                }
//            }
//        }
//
//        for (int i = 0; i < footers.size(); i++) {
//            String footer = footers.get(i);
//            response.append("\n`note").append(i == 0 ? "" : i).append(": ").append(footer).append("`");
//        }
//
//        IMessageIO output = flags.contains('d') ? new DiscordChannelIO(RateLimitUtil.complete(author.openPrivateChannel())) : channel;
//        CompletableFuture<IMessageBuilder> msgFuture = output.send(response.toString());
//
//        if (requiredUser != null && requiredUser.getPositionEnum() != null && requiredUser.getPosition() > 1 && guildDb.isValidAlliance() && guildDb.getAllianceIds(true).contains(requiredUser.getAlliance_id())) {
//            DBNation finalNation = requiredUser;
//            Locutus.imp().getExecutor().submit(new Runnable() {
//                @Override
//                public void run() {
//                    List<String> tips2 = new ArrayList<>();
//                    StringBuilder append = new StringBuilder();
//
//                    {
//                        Map<ResourceType, Double> stockpile = finalNation.getStockpile();
//                        if (stockpile != null && !stockpile.isEmpty() && stockpile.getOrDefault(ResourceType.CREDITS, 0d) != -1) {
//                            Map<ResourceType, Double> excess = finalNation.checkExcessResources(guildDb, stockpile);
//                            if (!excess.isEmpty()) {
//                                tips2.add("Excess can be deposited: " + PW.resourcesToString(excess));
//                                if (Boolean.TRUE.equals(guildDb.getOrNull(GuildKey.DEPOSIT_INTEREST))) {
//                                    List<Transaction2> transactions = finalNation.getTransactions(-1);
//                                    long last = 0;
//                                    for (Transaction2 transaction : transactions) last = Math.max(transaction.tx_datetime, last);
//                                    if (System.currentTimeMillis() - last > TimeUnit.DAYS.toMillis(5)) {
//                                        tips2.add("Deposit frequently to be eligable for interest on your deposits");
//                                    }
//                                }
//                            }
//                            Map<ResourceType, Double> needed = finalNation.getResourcesNeeded(stockpile, 3, true);
//                            if (!needed.isEmpty()) {
//                                tips2.add("Missing resources for the next 3 days: " + PW.resourcesToString(needed));
//                            }
//                        }
//                    }
//
//                    if (me != null && me.getNation_id() == finalNation.getNation_id() && Boolean.TRUE.equals(guildDb.getOrNull(GuildKey.MEMBER_CAN_OFFSHORE)) && guildDb.isValidAlliance()) {
//                        AllianceList alliance = db.getAllianceList();
//                        if (alliance != null && !alliance.isEmpty() && alliance.contains(me.getAlliance_id())) {
//                            try {
//                                Map<ResourceType, Double> stockpile = me.getAlliance().getStockpile();
//                                if (PW.convertedTotal(stockpile) > 5000000) {
//                                    tips2.add("You MUST offshore funds after depositing `" + CM.offshore.send.cmd.toSlashMention() + "` ");
//                                }
//                            } catch (Throwable ignore) {}
//                        }
//                    }
//
//                    if (!tips2.isEmpty()) {
//                        for (String tip : tips2) append.append("\n`tip: " + tip + "`");
//
//                        try {
//                            msgFuture.get().append(append.toString()).send();
//                        } catch (InterruptedException | ExecutionException e) {
//                            throw new RuntimeException(e);
//                        }
//                    }
//                }
//            });
//        }
//
//        return null;
    }
}
