package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Deposits extends Command {
    public Deposits() {
        super("deposits", "depo", "holdings", CommandCategory.ECON);
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
                "Add `-l` to only include the largest positive value of each rss in the total\n" +
                "Add `-i` to include nation transfers with #ignore\n" +
                "Add `-e` to include expired nation transfers\n" +
                "Add `-t` to show taxes separately (See flag: `-b` and `!synctaxes`)\n\n" +
                "Note: Use `" + Settings.commandPrefix(true) + "synctaxes` to update tax records\n" +
                "Add `-d` to show results in dm."
                ;
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        DBNation banker = DiscordUtil.getNation(event);
        if (banker == null) {
            return "Please use " + Settings.commandPrefix(true) + "validate.";
        }
        String requiredNote = DiscordUtil.parseArg(args, "note");
        boolean includeIgnored = flags.contains('i');
        boolean includeExpired = flags.contains('e');

        banker.setMeta(NationMeta.INTERVIEW_DEPOSITS, (byte) 1);

        long cutOff = 0;
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
        }

        if (args.size() != 1 && args.size() != 2) {
            return usage(event);
        }

        Map<String, Map<DepositType, double[]>> accountDeposits = new HashMap<>();

        String arg0 = args.get(0);

        Set<String> split = new LinkedHashSet<>(Arrays.asList(arg0.split(",")));

        GuildDB guildDb = Locutus.imp().getGuildDB(event);

        StringBuilder response = new StringBuilder();
        response.append("**").append(arg0).append("**:\n");

        List<String> footers = new ArrayList<>();

        DBNation requiredUser = null;

        Set<Long> tracked = null;
        if (args.size() == 2) {
            tracked = new LinkedHashSet<>();
            Set<Integer> alliances = DiscordUtil.parseAlliances(guild, args.get(1));
            if (alliances == null || alliances.isEmpty()) {
                return "Invalid alliance: `" + args.get(1) + "`";
            }
            for (Integer alliance : alliances) tracked.add(alliance.longValue());
            tracked = PnwUtil.expandCoalition(tracked);
        }

        for (String arg : split) {
            DBNation nation = DiscordUtil.parseNation(arg);
            if (arg.contains("tax_id=")) {
                int taxId = PnwUtil.parseTaxId(arg);

                Map<DepositType, double[]> deposits = guildDb.getTaxBracketDeposits(taxId, cutOff, includeExpired, includeIgnored);
                accountDeposits.put("tax_id=" + taxId, deposits);
            } else if (arg.equalsIgnoreCase("*")) {
                OffshoreInstance offshore = guildDb.getOffshore();
                if (offshore == null) return "No offshore found";
                if (!Roles.ECON_STAFF.has(author, guild) && !Roles.ECON_STAFF.has(author, offshore.getGuildDB().getGuild()))
                    return "You do not have permission to check this guild's deposits";
                double[] deposits = offshore.getDeposits(guildDb);
                accountDeposits.put("*", Collections.singletonMap(DepositType.DEPOSIT, deposits));
            } else if (nation == null && MathMan.isInteger(arg) && Long.parseLong(arg) > Integer.MAX_VALUE) {
                long id = Long.parseLong(arg);
                GuildDB otherDb = Locutus.imp().getGuildDB(id);
                if (otherDb != null) {
                    OffshoreInstance offshore = otherDb.getOffshore();
                    if (offshore == null)
                        return "No offshore is set. In this server, use " + CM.coalition.add.cmd.create(otherDb.getIdLong() + "", Coalition.OFFSHORE.name()) + " and from the offshore server use " + CM.coalition.add.cmd.create(otherDb.getIdLong() + "", Coalition.OFFSHORING.name()) + "";
                    if (!Roles.ECON_STAFF.has(author, offshore.getGuildDB().getGuild()) && !Roles.ECON_STAFF.has(author, otherDb.getGuild()))
                        return "You do not have permission to check another guild's deposits";

                    double[] deposits = offshore.getDeposits(otherDb);
                    String name = otherDb.getGuild().getName();
                    accountDeposits.put(name, Collections.singletonMap(DepositType.DEPOSIT, deposits));
                } else if (!guildDb.isOffshore()) {
                    return "Unknown guild: : `" + arg + "`";
                } else {
                    if (!Roles.ECON.has(author, guildDb.getGuild())) {
                        return "You do not have permission to check another guild's deposits";
                    }
                    OffshoreInstance offshore = guildDb.getOffshore();
                    double[] deposits = PnwUtil.resourcesToArray(offshore.getDeposits(id, true));
                    String name = id + "";
                    accountDeposits.put(name + "(removed guild)", Collections.singletonMap(DepositType.DEPOSIT, deposits));
                }
            } else if (nation == null && PnwUtil.parseAllianceId(arg) != null) {
                Integer allianceId = PnwUtil.parseAllianceId(arg);
                DBAlliance alliance = allianceId == null ? null : DBAlliance.get(allianceId);
                if (alliance == null) return "Invalid alliance: `" + arg + "`";
                GuildDB otherDb = Locutus.imp().getGuildDBByAA(allianceId);
                OffshoreInstance offshore;
                if (otherDb != null) {
                    offshore = otherDb.getOffshore();
                    if (offshore == null) {
                        if (flags.contains('f')) {
                            offshore = alliance.getBank();
                        }
                        if (offshore == null) {
                            return "No offshore set";
                        }
                    }
                } else if (!guildDb.isOffshore()) {
                    return "Unknown guild for AA:" + allianceId;
                } else {
                    offshore = guildDb.getOffshore();
                }
                if (!Roles.ECON_STAFF.has(author, offshore.getGuildDB().getGuild()) && !Roles.ECON_STAFF.has(author, otherDb.getGuild()))
                    return "You do not have permisssion to check another alliance's deposits";

                String name = PnwUtil.getName(allianceId, true);
                double[] deposits = PnwUtil.resourcesToArray(offshore.getDeposits(allianceId, true));
                accountDeposits.put(name, Collections.singletonMap(DepositType.DEPOSIT, deposits));
            } else {
                if (nation == null && arg.contains("/nation/") && DiscordUtil.parseNationId(arg) != null) {
                    nation = new DBNation();
                    nation.setNation_id(DiscordUtil.parseNationId(arg));
                }
                if (nation == null) return "Nation not found: `" + arg + "`";
                if (split.size() == 1) requiredUser = nation;
                if (nation.getNation_id() != me.getNation_id() && !Roles.INTERNAL_AFFAIRS.has(author, guild) && !Roles.INTERNAL_AFFAIRS_STAFF.has(author, guild) && !Roles.ECON_STAFF.has(author, guild)) return "You do not have permission to check other nation's deposits";

                Map<DepositType, double[]> nationDepo = nation.getDeposits(guildDb,
                        tracked,
                        !flags.contains('b'),
                        !flags.contains('o'),
                        0L,
                        cutOff,
                        includeIgnored,
                        includeExpired,
                        f -> requiredNote == null || (f.note == null || f.note.toLowerCase().contains(requiredNote.toLowerCase())));
                accountDeposits.put(nation.getNation(), nationDepo);
            }
        }

        GuildDB db = Locutus.imp().getGuildDB(guild);

        boolean largest = flags.contains('l');

        double[] total = new double[ResourceType.values.length];
        double[] totalNoGrants = new double[ResourceType.values.length];
        double[] taxAndDeposits = new double[ResourceType.values.length];
        Map<DepositType, double[]> categorized = new HashMap<>();

        for (Map<DepositType, double[]> accountDeposit : accountDeposits.values()) {
            for (Map.Entry<DepositType, double[]> entry : accountDeposit.entrySet()) {
                DepositType type = entry.getKey();
                double[] existing = categorized.computeIfAbsent(type, f -> new double[ResourceType.values.length]);
                double[] current = entry.getValue();

                for (int i = 0; i < existing.length; i++) {
                    if (largest) {
                        existing[i] = Math.max(existing[i], current[i]);
                        total[i] = Math.max(total[i], current[i]);
                        if (type != DepositType.GRANT) {
                            totalNoGrants[i] = Math.max(totalNoGrants[i], current[i]);
                            if (type != DepositType.LOAN) {
                                taxAndDeposits[i] = Math.max(taxAndDeposits[i], current[i]);
                            }
                        }
                    }
                    else {
                        existing[i] += current[i];
                        total[i] += current[i];
                        if (type != DepositType.GRANT) {
                            totalNoGrants[i] += current[i];
                            if (type != DepositType.LOAN) {
                                taxAndDeposits[i] += current[i];
                            }
                        }
                    }
                }
            }
        }

        footers.add("value is based on current market prices");

        if (flags.contains('t') || db.getOrNull(GuildDB.Key.DISPLAY_ITEMIZED_DEPOSITS) == Boolean.TRUE) {
            if (categorized.containsKey(DepositType.DEPOSIT)) {
                response.append("#DEPOSIT: (worth $" + MathMan.format(PnwUtil.convertedTotal(categorized.get(DepositType.DEPOSIT))) + ")");
                response.append("\n```").append(PnwUtil.resourcesToString(categorized.get(DepositType.DEPOSIT))).append("``` ");
            }
            if (categorized.containsKey(DepositType.TAX)) {
                response.append("#TAX (worth $").append(MathMan.format(PnwUtil.convertedTotal(categorized.get(DepositType.TAX)))).append(")");
                response.append("\n```").append(PnwUtil.resourcesToString(categorized.get(DepositType.TAX))).append("``` ");
            }
            if (categorized.containsKey(DepositType.LOAN)) {
                response.append("#LOAN/#GRANT (worth $").append(MathMan.format(PnwUtil.convertedTotal(categorized.get(DepositType.LOAN)))).append(")");
                response.append("\n```").append(PnwUtil.resourcesToString(categorized.get(DepositType.LOAN))).append("``` ");
            }
            if (categorized.containsKey(DepositType.GRANT)) {
                response.append("#EXPIRE (worth $").append(MathMan.format(PnwUtil.convertedTotal(categorized.get(DepositType.GRANT)))).append(")");
                response.append("\n```").append(PnwUtil.resourcesToString(categorized.get(DepositType.GRANT))).append("``` ");
            }
            if (categorized.size() > 1) {
                response.append("Total: (worth: $").append(MathMan.format(PnwUtil.convertedTotal(total))).append(")");
                response.append("\n```").append(PnwUtil.resourcesToString(total)).append("``` ");
            }
        } else {
            String totalTitle = "Total (`#expire`|`#loan`|`#tax`|`#deposit`: worth $";
            String noGrantTitle = "Excluding `#expire` (worth: $";
            String safekeepTitle = "Safekeep (`#tax`|`#deposit`: worth $";
            boolean hasPriorCategory = false;
            if (categorized.containsKey(DepositType.GRANT)) {
                response.append(totalTitle).append(MathMan.format(PnwUtil.convertedTotal(total))).append(")");
                response.append("\n```").append(PnwUtil.resourcesToString(total)).append("``` ");
                footers.add("Unlike loans, debt from grants will expire if you stay (see the transaction for the timeframe)");
                hasPriorCategory = true;
            }
            if (categorized.containsKey(DepositType.LOAN)) {
                response.append(hasPriorCategory ? noGrantTitle : totalTitle).append(MathMan.format(PnwUtil.convertedTotal(totalNoGrants))).append(")");
                response.append("\n```").append(PnwUtil.resourcesToString(totalNoGrants)).append("``` ");
                hasPriorCategory = true;
            }

            response.append(hasPriorCategory ? safekeepTitle : totalTitle).append(MathMan.format(PnwUtil.convertedTotal(taxAndDeposits))).append(")");
            response.append("\n```").append(PnwUtil.resourcesToString(taxAndDeposits)).append("``` ");
        }
        if (requiredUser != null && me != null && requiredUser.getNation_id() == me.getNation_id()) {
            footers.add("Funds default to #deposit if no other note is used.");
            if (Boolean.TRUE.equals(guildDb.getOrNull(GuildDB.Key.RESOURCE_CONVERSION))) {
                footers.add("You can sell resources to the alliance by depositing with the note #cash.");
            }
            if (PnwUtil.convertedTotal(total) > 0 && Boolean.TRUE.equals(guildDb.getOrNull(GuildDB.Key.MEMBER_CAN_WITHDRAW))) {
                Role role = Roles.ECON_WITHDRAW_SELF.toRole(guild);
                if (guild.getMember(author).getRoles().contains(role)) {
                    footers.add("To withdraw, use: `" + CM.transfer.self.cmd.toSlashMention() + "` ");
                }
            }
        }

        for (int i = 0; i < footers.size(); i++) {
            String footer = footers.get(i);
            response.append("\n`note").append(i == 0 ? "" : i).append(": ").append(footer).append("`");
        }

        MessageChannel output = flags.contains('d') ? RateLimitUtil.complete(author.openPrivateChannel()) : event.getChannel();
        Message message = RateLimitUtil.complete(output.sendMessage(response.toString()));

        if (requiredUser != null && requiredUser.getPosition() > 1 && guildDb.isValidAlliance() && guildDb.getAllianceIds(true).contains(requiredUser.getAlliance_id())) {
            DBNation finalNation = requiredUser;
            Locutus.imp().getExecutor().submit(new Runnable() {
                @Override
                public void run() {
                    List<String> tips2 = new ArrayList<>();

                    {
                        Map<ResourceType, Double> stockpile = finalNation.getStockpile();
                        if (stockpile != null && !stockpile.isEmpty() && stockpile.getOrDefault(ResourceType.CREDITS, 0d) != -1) {
                            Map<ResourceType, Double> excess = finalNation.checkExcessResources(guildDb, stockpile);
                            if (!excess.isEmpty()) {
                                tips2.add("Excess can be deposited: " + PnwUtil.resourcesToString(excess));
                                if (Boolean.TRUE.equals(guildDb.getOrNull(GuildDB.Key.DEPOSIT_INTEREST))) {
                                    List<Transaction2> transactions = finalNation.getTransactions(-1);
                                    long last = 0;
                                    for (Transaction2 transaction : transactions) last = Math.max(transaction.tx_datetime, last);
                                    if (System.currentTimeMillis() - last > TimeUnit.DAYS.toMillis(5)) {
                                        tips2.add("Deposit frequently to be eligable for interest on your deposits");
                                    }
                                }
                            }
                            Map<ResourceType, Double> needed = finalNation.getResourcesNeeded(stockpile, 3, true);
                            if (!needed.isEmpty()) {
                                tips2.add("Missing resources for the next 3 days: " + PnwUtil.resourcesToString(needed));
                            }
                        }
                    }

                    if (me != null && me.getNation_id() == finalNation.getNation_id() && Boolean.TRUE.equals(guildDb.getOrNull(GuildDB.Key.MEMBER_CAN_OFFSHORE)) && guildDb.isValidAlliance()) {
                        AllianceList alliance = db.getAllianceList();
                        if (alliance != null && !alliance.isEmpty() && alliance.contains(me.getAlliance_id())) {
                            try {
                                Map<ResourceType, Double> stockpile = me.getAlliance().getStockpile();
                                if (PnwUtil.convertedTotal(stockpile) > 5000000) {
                                    tips2.add("You MUST offshore funds after depositing `" + CM.offshore.send.cmd.toSlashMention() + "` ");
                                }
                            } catch (Throwable ignore) {}
                        }
                    }

                    if (!tips2.isEmpty()) {
                        for (String tip : tips2) response.append("\n`tip: " + tip + "`");

                        RateLimitUtil.queue(output.editMessageById(message.getIdLong(), response.toString()));
                    }
                }
            });
        }

        return null;
    }

}
