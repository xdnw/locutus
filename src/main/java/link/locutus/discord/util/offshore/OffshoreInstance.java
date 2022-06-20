package link.locutus.discord.util.offshore;

import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.task.balance.BankWithTask;
import link.locutus.discord.util.task.balance.GetDepositTask;
import link.locutus.discord.apiv2.PoliticsAndWarV2;
import link.locutus.discord.apiv1.domains.subdomains.AllianceBankContainer;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class OffshoreInstance {
    public static final Object BANK_LOCK = new Object();
    public static final boolean DISABLE_TRANSFERS = false;
    private final int allianceId;
    private final Set<Long> offshoreAAs;

    private final Auth auth;

    public OffshoreInstance(Auth auth, GuildDB db, int allianceId) {
        this.auth = auth;
        this.offshoreAAs = new LinkedHashSet<>(db.getCoalitionRaw(Coalition.OFFSHORE));
        offshoreAAs.add((long) allianceId);
        this.allianceId = allianceId;
    }

    public int getAllianceId() {
        return allianceId;
    }

    public PoliticsAndWarV2 getApi() {
        return getGuildDB().getApi(false);
    }

    public Auth getAuth() {
        return auth;
    }

    public Set<Long> getOffshoreAAs() {
        return Collections.unmodifiableSet(offshoreAAs);
    }

    private final AtomicBoolean outOfSync = new AtomicBoolean(false);
    private Map<ResourceType, Double> lastFunds = null;

    public synchronized boolean sync(boolean force) {
        if (force || !outOfSync.get()) return sync();
        return false;
    }

    public synchronized boolean sync() {
        return sync(null);
    }

    public synchronized boolean sync(Long latest) {
        return sync(latest, true);
    }

    public synchronized boolean sync(Long latest, boolean checkLast) {
        try {
            AllianceBankContainer funds = getApi().getBank(allianceId).getAllianceBanks().get(0);
            Map<ResourceType, Double> totals = PnwUtil.adapt(funds);
            if (lastFunds != null && checkLast) {
                if (lastFunds.equals(totals)) return true;
            }
            lastFunds = totals;

            List<Transaction2> existing = Locutus.imp().getBankDB().getBankTransactions(allianceId, 2);
            if (latest == null) {
                latest = 0L;
                for (Transaction2 transfer : existing) {
                    latest = Math.max(transfer.getDate(), latest);
                }
            }

            long now = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(60);

            if (latest > System.currentTimeMillis() + 1000L) {
                if (true) throw new IllegalArgumentException("Transaction date is > now: " + latest + " | " + allianceId);
                List<Transaction2> transfers = new GetDepositTask(auth, allianceId, 0).call();
                for (Transaction2 transaction : transfers) {
                    if (transaction.tx_datetime > now) throw new IllegalArgumentException("Transaction date is > now: " + transaction.tx_datetime);
                }
//                transfers.removeIf(transfer -> !transfer.isSenderAA() || !transfer.isReceiverAA());
                if (!transfers.isEmpty()) {
                    Locutus.imp().getBankDB().removeAllianceTransactions(allianceId);
                    Locutus.imp().getBankDB().addAllianceTransactions(transfers);
                }
            } else {
                long fetchTo = latest - 60000;
                List<Transaction2> transfers = new GetDepositTask(auth, allianceId, fetchTo).call();
                for (Transaction2 transaction : transfers) {
                    if (transaction.tx_datetime > now) throw new IllegalArgumentException("Transaction date is > now: " + transaction.tx_datetime);
                }
                if (!transfers.isEmpty()) {
                    Locutus.imp().getBankDB().removeAllianceTransactions(allianceId, auth.getAllianceId(), fetchTo);
//                    Locutus.imp().getBankDB().removeAllianceTransactions(allianceId, fetchTo);
                    Locutus.imp().getBankDB().addAllianceTransactions(transfers);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return  false;
    }

    public synchronized Map<ResourceType, Double> getDeposits(int allianceId) {
        return getDeposits(allianceId, true);
    }

//    public synchronized List<Transaction2> getTransactions(String requiredNote, boolean force) {
//        if (force || !outOfSync.get()) sync();
//        List<Transaction2> result = new ArrayList<>();
//
//        List<Transfer> transactions = Locutus.imp().getBankDB().getBankTransactions();
//        for (Transfer transfer : transactions) {
//            String note = transfer.getNote();
//            if (note == null || !note.toLowerCase().startsWith(requiredNote.toLowerCase())) {
//                continue;
//            }
//            int sign;
//            if (offshoreAAs.contains((long) transfer.getSender()) && transfer.isSenderAA()) {
//                sign = -1;
//            } else if (offshoreAAs.contains((long) transfer.getReceiver()) && transfer.isReceiverAA()) {
//                sign = 1;
//            } else {
//                continue;
//            }
//            Transaction2 tx = new Transaction2(transfer);
//            result.add(tx);
//        }
//        return result;
//    }
//
//    public synchronized Map<ResourceType, Double> getDeposits(String requiredNote, boolean force) {
//        if (force || !outOfSync.get()) sync();
//
//        double[] resources = new double[ResourceType.values.length];
//        List<Transfer> transactions = Locutus.imp().getBankDB().getBankTransactions();
//        for (Transfer transfer : transactions) {
//            String note = transfer.getNote();
//            if (note == null || !note.toLowerCase().startsWith(requiredNote.toLowerCase())) {
//                continue;
//            }
//            int sign;
//            if (offshoreAAs.contains((long) transfer.getSender()) && transfer.isSenderAA()) {
//                sign = -1;
//            } else if (offshoreAAs.contains((long) transfer.getReceiver()) && transfer.isReceiverAA()) {
//                sign = 1;
//            } else {
//                continue;
//            }
//            resources[transfer.getRss().ordinal()] += sign * transfer.getAmount();
//        }
//        return PnwUtil.resourcesToMap(resources);
//    }

    public synchronized List<Transaction2> getTransactionsGuild(long guildId, boolean force) {
        if (force || !outOfSync.get()) sync();

        List<Transaction2> transactions = Locutus.imp().getBankDB().getBankTransactions(guildId, 3);

        GuildDB db = getGuildDB();
        List<Transaction2> offset = db.getDepositOffsetTransactions(guildId);
        transactions.addAll(offset);

        List<Transaction2> toProcess = new ArrayList<>();

        outer:
        for (Transaction2 transfer : transactions) {
            String note = transfer.note;
            if (note != null) {
                String[] split = note.split("(?=#)");
                for (String filter : split) {
                    String[] tagSplit = filter.split("[=| ]", 2);
                    String tag = tagSplit[0].toLowerCase();
                    String value = tagSplit.length == 2 && !tagSplit[1].trim().isEmpty() ? tagSplit[1].split(" ")[0].trim() : null;

                    switch (tag) {
                        case "#guild":
                            if (!MathMan.isInteger(value)) continue outer;
                            long transferGuild = Long.parseLong(value);
                            if (transferGuild != guildId || offshoreAAs.contains(transfer.getSender())) continue outer;
                            transfer.sender_id = transferGuild;
                            transfer.sender_type = 3;
                            continue;
                        case "#alliance":
                        case "#account":
                        case "#nation":
                        case "#ignore":
                            continue outer;
                    }
                }
            }
            toProcess.add(transfer);
        }
        return toProcess;
    }

    private synchronized Map<ResourceType, Double> getDeposits(long guildId, boolean force) {
        List<Transaction2> toProcess = getTransactionsGuild(guildId, force);

        return PnwUtil.resourcesToMap(addTransfers(toProcess, guildId, 3));
    }

    public synchronized List<Transaction2> getTransactionsAA(int allianceId, boolean force) {
        if (force || !outOfSync.get()) sync();

        List<Transaction2> transactions = Locutus.imp().getBankDB().getBankTransactions(allianceId, 2);

        GuildDB db = getGuildDB();
        List<Transaction2> offset = db.getDepositOffsetTransactions(-allianceId);
        transactions.addAll(offset);

        List<Transaction2> toProcess = new ArrayList<>();

        outer:
        for (Transaction2 transfer : transactions) {
            String note = transfer.note;
            if (note != null) {
                String[] split = note.split("(?=#)");
                for (String filter : split) {
                    String[] tagSplit = filter.split("[=| ]", 2);
                    String tag = tagSplit[0].toLowerCase();
                    String value = tagSplit.length == 2 && !tagSplit[1].trim().isEmpty() ? tagSplit[1].split(" ")[0].trim() : null;

                    switch (tag) {
                        case "#alliance":
                            if (!MathMan.isInteger(value)) continue outer;
                            int transferAA = Integer.parseInt(value);
                            if (transferAA != allianceId || offshoreAAs.contains(transfer.getSender()))
                                continue outer;
                            transfer.sender_id = transferAA;
                            transfer.sender_type = 2;
                            continue;
                        case "#guild":
                        case "#account":
                        case "#nation":
                        case "#ignore":
                            continue outer;
                    }
                }
            }
            toProcess.add(transfer);
        }
        return toProcess;
    }

    public synchronized Map<ResourceType, Double> getDeposits(int allianceId, boolean force) {
        List<Transaction2> toProcess = getTransactionsAA(allianceId, force);

        return PnwUtil.resourcesToMap(addTransfers(toProcess, allianceId, 2));
    }

    private double[] addTransfers(List<Transaction2> transactions, long id, int type) {
        double[] resources = ResourceType.getBuffer();
        for (Transaction2 transfer : transactions) {
            int sign;

            // transfer.sender_id == 0 && transfer.sender_type == 0 &&
            if ((transfer.sender_id == id && transfer.sender_type == type) &&
                    (offshoreAAs.contains(transfer.getReceiver()) || (transfer.tx_id == -1))) {
                sign = 1;

                // transfer.receiver_id == 0 && transfer.receiver_type == 0 &&
            } else if ((transfer.receiver_id == id && transfer.receiver_type == type) &&
                    (offshoreAAs.contains(transfer.getSender()) || (transfer.tx_id == -1))) {
                sign = -1;
            } else {
                continue;
            }
            for (int i = 0; i < transfer.resources.length; i++) {
                resources[i] += sign * transfer.resources[i];
            }
        }

        return resources;
    }

    public Map.Entry<TransferStatus, String> transfer(DBNation nation, Map<ResourceType, Double> transfer) {
        return transfer(nation, transfer, null);
    }

    public Map.Entry<TransferStatus, String> transferSafe(NationOrAlliance nation, Map<ResourceType, Double> transfer, String note) {
        synchronized (BANK_LOCK) {
            if (nation.isNation()) return transferSafe(nation.asNation(), transfer, note);
            return transfer(nation.asAlliance(), transfer, note);
        }
    }

//    public boolean isAuthorized(User banker, Alliance alliance) {
//        boolean isAdmin = Roles.ECON.hasOnRoot(banker);
//        if (isAdmin) return true;
//
//        int aaId = alliance.getId();
//        if (disabledAAs.contains(aaId)) return false;
//
//        GuildDB guildDb = Locutus.imp().getGuildDBByAA(aaId);
//        if (guildDb == null) throw new IllegalArgumentException("No guild set for: " + alliance.getMarkdownUrl());
//
//        if (allianceId == Locutus.imp().getRootBank().getAllianceId()) {
//            if (!Settings.INSTANCE.ALLIANCES_OFFSHORING.contains(aaId)) {
//                throw new IllegalArgumentException("Alliance is not authorized to offshore");
//            }
//
//            // nation has econ on alliance server
//            if (!Roles.ECON.has(banker, guildDb.getGuild())) return false;
//
//
//            return true;
//        } else {
//            throw new IllegalArgumentException("WIP (self hosted offshore)");
//            // todo econ role on offshore server
//        }
//    }

    public Map<ResourceType, Double> getOffshoredBalance(DBAlliance alliance) {
        GuildDB db = alliance.getGuildDB();
        if (db == null) return new HashMap<>();
        if (db.getOffshore() != this) return new HashMap<>();
        if (alliance.getAlliance_id() == allianceId) {
            double[] result = ResourceType.getBuffer();
            Arrays.fill(result, Double.MAX_VALUE);
            return PnwUtil.resourcesToMap(result);
        }
        return getDeposits(alliance.getAlliance_id(), true);
    }

//    private Set<Integer> disabledAccounts = new HashSet<>();
//    public synchronized boolean transferFromBalance(User banker, Alliance account, NationOrAlliance receiver, Map<ResourceType, Double> transfer, String note) {
//        if (account.isNation()) {
//            throw new IllegalArgumentException("WIP (sending nation -> nation)");
//        }
//        Alliance alliance = account.asAlliance();
//
//        // check alliance has balance
//        Map<ResourceType, Double> deposits = getOffshoredBalance(alliance);
//        for (Map.Entry<ResourceType, Double> entry : transfer.entrySet()) {
//            ResourceType rss = entry.getKey();
//            Double amt = entry.getValue();
//            if (amt > 0 && deposits.getOrDefault(rss, -1d) + 0.01 < amt) {
//                throw new IllegalArgumentException("You do not have " + MathMan.format(amt) + " x " + rss.name());
//            }
//        }
//        // check balance is sufficient
//
//        // get guild db for addbalance
//
//        // have a disabled set
//
////        Map<ResourceType, Double> deposits = getDeposits();
//        return false;//
//    }

    public Set<Long> disabledGuilds = new HashSet<>();

    public Map.Entry<TransferStatus, String> transferFromDeposits(DBNation banker, GuildDB senderDB, NationOrAlliance receiver, double[] amount, String note) {
        GuildDB delegate = senderDB.getDelegateServer();
        if (delegate != null) senderDB = delegate;

        boolean hasAmount = false;
        for (double amt : amount) if (amt >= 0.01) hasAmount = true;
        if (!hasAmount) return new AbstractMap.SimpleEntry<>(TransferStatus.NOTHING_WITHDRAWN, "You did not withdraw anything.");


        if (banker != null) {
            boolean isAdmin = false;
            User user = banker.getUser();
            if (user != null) {
                isAdmin = Roles.ADMIN.has(user, senderDB.getGuild());
            }
            String append = "#banker=" + banker.getNation_id();
            // getBankTransactionsWithNote
            if (note == null || note.isEmpty()) {
                note = append;
            } else {
                note += " " + append;
            }

            if (!isAdmin) {
                Double withdrawLimit = senderDB.getHandler().getWithdrawLimit(banker.getNation_id());
                if (withdrawLimit != null) {
                    long cutoff = System.currentTimeMillis();
                    Long interval = senderDB.getOrNull(GuildDB.Key.BANKER_WITHDRAW_LIMIT_INTERVAL);
                    if (interval != null) {
                        cutoff -= interval;
                    } else {
                        cutoff -= TimeUnit.DAYS.toMillis(1);
                    }

                    List<Transaction2> transactions = Locutus.imp().getBankDB().getBankTransactionsWithNote(append, cutoff);
                    double total = 0;
                    for (Transaction2 transaction : transactions) {
                        total += transaction.convertedTotal();
                    }
                    if (total > withdrawLimit) {
                        GuildMessageChannel alertChannel = senderDB.getOrNull(GuildDB.Key.WITHDRAW_ALERT_CHANNEL);
                        if (alertChannel != null) {
                            StringBuilder body = new StringBuilder();
                            body.append(banker.getNationUrlMarkup(true) + " | " + banker.getAllianceUrlMarkup(true)).append("\n");
                            body.append("Transfer: " + PnwUtil.resourcesToString(amount) + " | " + note + " | to:" + receiver.getTypePrefix() + receiver.getName());
                            body.append("Limit set to $" + MathMan.format(withdrawLimit) + " (worth of $/rss)\n\n");
                            body.append("To set the limit for a user: `" + Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX + "setTransferLimit <nation> <value>`\n");
                            body.append("To set the default `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "KeyStore BANKER_WITHDRAW_LIMIT <amount>`");
                            DiscordUtil.createEmbedCommand(alertChannel, "Banker withdraw limit exceeded", body.toString());
                            Role adminRole = Roles.ADMIN.toRole(senderDB.getGuild());
                            if (adminRole != null) {
                                RateLimitUtil.queue(alertChannel.sendMessage("^ " + adminRole.getAsMention()));
                            }
                        }
                        return new AbstractMap.SimpleEntry<>(TransferStatus.INSUFFICIENT_FUNDS, "You (" + banker.getNation() + ") have hit your transfer limit");
                    }
                }
            }
        }
        if (DISABLE_TRANSFERS && banker.getNation_id() != Settings.INSTANCE.NATION_ID) throw new IllegalArgumentException("Error: Maintenance");

        synchronized (BANK_LOCK) {
            boolean isZero = true;
            for (double i : amount) if (i != 0) isZero = false;
            if (isZero) throw new IllegalArgumentException("No funds need to be sent");

            OffshoreInstance senderOffshore = senderDB.getOffshore();
            if (senderOffshore != this)
                throw new IllegalArgumentException("Sender does not have " + allianceId + " as an offshore");
            GuildDB offshoreDB = getGuildDB();
            if (offshoreDB == null) throw new IllegalArgumentException("No guild is registered with this offshore");

            if (disabledGuilds.contains(senderDB.getGuild().getIdLong())) {
                throw new IllegalArgumentException("There was an error transferring funds (failed to fetch bank stockpile). Please have an admin use `" + Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX + "unlocktransfers <alliance>` in the offshore server");
            }

            boolean hasAdmin = false;
            User bankerUser = banker.getUser();
            if (bankerUser != null) hasAdmin = Roles.ECON.has(bankerUser, offshoreDB.getGuild());

            // Ensure sufficient deposits
            boolean valid = senderDB == offshoreDB;
            double[] deposits = getDeposits(senderDB);

            if (!valid) {
                deposits = PnwUtil.normalize(deposits); // normalize
                for (int i = 0; i < amount.length; i++) {
                    if (amount[i] != 0 && deposits[i] + 0.01 < amount[i] && !hasAdmin)
                        throw new IllegalArgumentException("You do not have " + MathMan.format(amount[i]) + "x" + ResourceType.values[i] + ", only " + MathMan.format(deposits[i]) + " (normalized)");
                    if (Double.isNaN(amount[i]) || amount[i] < 0)
                        throw new IllegalArgumentException(amount[i] + " is not a valid positive amount");
                }
                if (deposits[ResourceType.CREDITS.ordinal()] != 0)
                    throw new IllegalArgumentException("You cannot transfer credits");
            }

            Integer aaId = senderDB.getOrNull(GuildDB.Key.ALLIANCE_ID);
            disabledGuilds.add(senderDB.getGuild().getIdLong());

            Map<ResourceType, Double> transfer = PnwUtil.resourcesToMap(amount);
            Map.Entry<OffshoreInstance.TransferStatus, String> result = transferSafe(receiver, transfer, note);

            switch (result.getKey()) {
                default:
                case OTHER:
                    MessageChannel logChannel = getGuildDB().getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL);
                    if (logChannel != null) {
                        String msg = "Unknown result for: " + senderDB.getGuild().toString() + "/" + aaId + ": " + result + " | <@" + Settings.INSTANCE.ADMIN_USER_ID + ">";
                        RateLimitUtil.queue(logChannel.sendMessage(msg));
                    }
                case SUCCESS:
                case ALLIANCE_BANK: {
                    Map<ResourceType, Double> negative = PnwUtil.subResourcesToA(new HashMap<>(), transfer);
                    negative.entrySet().removeIf(f -> f.getValue() >= 0);
                    String noteLower = note.toLowerCase();

                    {
                        long tx_datetime = System.currentTimeMillis();
                        String offshoreNote = "#deposit #receiver_id=" + receiver.getId() + " #receiver_type=" + receiver.getReceiverType();
                        offshoreDB.addTransfer(tx_datetime, 0, 0, senderDB, banker.getNation_id(), offshoreNote, amount);
                    }

                    if ((result.getKey() == OffshoreInstance.TransferStatus.SUCCESS || result.getKey() == OffshoreInstance.TransferStatus.ALLIANCE_BANK)) {
                        double[] newDeposits = getDeposits(senderDB, false);
                        for (ResourceType type : ResourceType.values) {
                            double amt = deposits[type.ordinal()];
                            if (amt > newDeposits[type.ordinal()]) valid = true;
                        }
                        if (!valid) {
                            sync(null, false);
                            for (ResourceType type : ResourceType.values) {
                                double amt = deposits[type.ordinal()];
                                if (amt > newDeposits[type.ordinal()]) valid = true;
                            }
                        }
                        logChannel = getGuildDB().getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL);
                        if (logChannel != null) {
                            String msg = "New Deposits for: " + senderDB.getGuild().toString() + "/" + aaId + ": `" + PnwUtil.resourcesToString(newDeposits) + ("`");
                            RateLimitUtil.queue(logChannel.sendMessage(msg));
                        }
                    } else {
                        valid = true;
                    }
                    if (valid) {
                        disabledGuilds.remove(senderDB.getGuild().getIdLong());
                    } else {
                        String title = "Reimburse";
                        StringBuilder body = new StringBuilder();
                        body.append("ID: " + aaId + " | " + senderDB.getGuild().toString());
                        body.append("\nAmount: " + PnwUtil.resourcesToString(transfer));

                        String id = aaId == null ? "guild:" + senderDB.getGuild().getIdLong() : ("aa:" + aaId);
                        String cmd = Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "addbalance " + id + " " + PnwUtil.resourcesToString(transfer) + " #deposit";
                        body.append("\n" + cmd);

                        GuildMessageChannel txChannel = getGuildDB().getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL);
                        if (txChannel != null) {
                            DiscordUtil.createEmbedCommand(txChannel, title, body.toString());
                            RateLimitUtil.queue(txChannel.sendMessage("^ <@" + Settings.INSTANCE.ADMIN_USER_ID + (">")));
                        }
                    }
                    break;
                }
                case TURN_CHANGE:
                case BLOCKADE:
                case VACATION_MODE:
                case MULTI:
                case INSUFFICIENT_FUNDS:
                case INVALID_DESTINATION:
                case NOTHING_WITHDRAWN:
                    disabledGuilds.remove(senderDB.getGuild().getIdLong());
                    break;
            }
            return result;
        }
    }

    public Map.Entry<TransferStatus, String> transferSafe(DBNation nation, Map<ResourceType, Double> transfer, String note) {
        synchronized (BANK_LOCK) {
            Map.Entry<TransferStatus, String> result = transfer(nation, transfer, note);
            if (result.getKey() == TransferStatus.MULTI && nation.getPosition() > 1) {
                DBAlliance alliance = nation.getAlliance();
                GuildDB db = alliance == null ? null : alliance.getGuildDB();
                if (db != null) {
                    OffshoreInstance bank = db.getHandler().getBank();

                    result = transfer(alliance, transfer, "#ignore");

                    StringBuilder response = new StringBuilder();
                    response.append("\nSent to AA:" + alliance.getName() + "/" + alliance.getAlliance_id());
                    response.append("\n" + result.getValue());
                    if (result.getKey() == TransferStatus.SUCCESS) {
                        if (bank != null) {
                            response.append("\nTransferring to nation...");
                            Auth auth = OffshoreInstance.this.auth;
                            if (nation.getPosition() > Rank.MEMBER.id) {
                                Auth nationAuth = nation.getAuth(null);
                                if (nationAuth != null) auth = nationAuth;
                            }
                            try {
                                result = bank.transfer(auth, nation, transfer, note);
                            } catch (Throwable e) {

                            }
                            if (result.getKey() != TransferStatus.SUCCESS) {
                                result = new AbstractMap.SimpleEntry<>(TransferStatus.SUCCESS, result.getValue());
                            }
                            response.append("\n" + result.getValue());
                        }
                    }
                    result.setValue(response.toString());
                }
            }
            return result;
        }
    }

    public Map.Entry<TransferStatus, String> transfer(DBNation nation, Map<ResourceType, Double> transfer, String note) {
        synchronized (BANK_LOCK) {
            return transfer(auth, nation, transfer, note);
        }
    }

    public Map.Entry<TransferStatus, String> transfer(Auth auth, DBNation nation, Map<ResourceType, Double> transfer, String note) {
        if (!TimeUtil.checkTurnChange()) return new AbstractMap.SimpleEntry<>(TransferStatus.TURN_CHANGE, "You cannot transfer close to turn change");
        synchronized (BANK_LOCK) {
            BankWithTask task = new BankWithTask(auth, allianceId, 0, nation, new Function<Map<ResourceType, Double>, String>() {
                @Override
                public String apply(Map<ResourceType, Double> stock) {
                    for (Map.Entry<ResourceType, Double> entry : transfer.entrySet()) {
                        ResourceType type = entry.getKey();
                        Double currentAmt = stock.getOrDefault(type, 0d);
                        stock.put(type, currentAmt - entry.getValue());
                    }
                    return note;
                }
            });
            Map.Entry<TransferStatus, String> result = categorize(task);
            String msg = "`" + PnwUtil.resourcesToString(transfer) + "` -> " + nation.getUrl() + "\n**" + result.getKey() + "**: " + result.getValue();

            GuildMessageChannel logChannel = getGuildDB().getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL);
            if (logChannel != null) {
                RateLimitUtil.queue(logChannel.sendMessage(msg));
            }
            return result;
        }
    }

    public Map.Entry<TransferStatus, String> transfer(DBAlliance alliance, Map<ResourceType, Double> transfer) {
        return transfer(alliance, transfer, "#deposit");
    }

    public Map.Entry<TransferStatus, String> transfer(DBAlliance alliance, Map<ResourceType, Double> transfer, String note) {
        if (!TimeUtil.checkTurnChange()) return new AbstractMap.SimpleEntry<>(TransferStatus.TURN_CHANGE, "You cannot transfer close to turn change");
        if (!alliance.exists()) {
            return new AbstractMap.SimpleEntry<>(TransferStatus.INVALID_DESTINATION, "The alliance does not exist");
        }
        if (alliance.getNations(true, 10000, true).isEmpty()) {
            return new AbstractMap.SimpleEntry<>(TransferStatus.INVALID_DESTINATION, "The alliance has no members");
        }
        synchronized (BANK_LOCK) {
            BankWithTask task = new BankWithTask(auth, allianceId, alliance.getAlliance_id(), null, new Function<Map<ResourceType, Double>, String>() {
                @Override
                public String apply(Map<ResourceType, Double> stock) {
                    for (Map.Entry<ResourceType, Double> entry : transfer.entrySet()) {
                        if (entry.getValue() > stock.getOrDefault(entry.getKey(), 0d)) {
                            return "Insufficient funds.";
                        }
                    }
                    for (Map.Entry<ResourceType, Double> entry : transfer.entrySet()) {
                        ResourceType type = entry.getKey();
                        double stored = stock.getOrDefault(type, 0d);
                        double withdraw = entry.getValue();
                        stock.put(type, stored - withdraw);
                    }
                    return note;
                }
            });
            Map.Entry<TransferStatus, String> result = categorize(task);
            String msg = "`" + PnwUtil.resourcesToString(transfer) + "` -> " + alliance.getUrl() + "\n**" + result.getKey() + "**: " + result.getValue();

            GuildMessageChannel logChannel = getGuildDB().getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL);
            if (logChannel != null) {
                RateLimitUtil.queue(logChannel.sendMessage(msg));
            }
            return result;
        }
    }

    public double[] getDeposits(GuildDB guildDb) {
        return getDeposits(guildDb, true);
    }

    public double[] getDeposits(GuildDB guildDb, boolean update) {
        GuildDB delegate = guildDb.getDelegateServer();
        if (delegate != null) {
            guildDb = delegate;
        }
        if (guildDb.getOffshore() != this) return ResourceType.getBuffer();

        Integer aaId = guildDb.getOrNull(GuildDB.Key.ALLIANCE_ID);
        if (aaId != null) {
            return PnwUtil.resourcesToArray(getDeposits(aaId, update));
        }
        GuildDB offshoreDb = getGuildDB();
        if (offshoreDb == null) return ResourceType.getBuffer();
        long guildId = guildDb.getGuild().getIdLong();
        return PnwUtil.resourcesToArray(getDeposits(guildId, update));
    }

    public GuildDB getGuildDB() {
        return Locutus.imp().getGuildDBByAA(allianceId);
    }

    public DBAlliance getAlliance() {
        return Locutus.imp().getNationDB().getAlliance(allianceId);
    }

    public enum TransferStatus {
        SUCCESS,
        BLOCKADE,
        MULTI,
        TURN_CHANGE,
        INSUFFICIENT_FUNDS,
        INVALID_DESTINATION,
        OTHER,
        ALLIANCE_BANK,
        VACATION_MODE,
        NOTHING_WITHDRAWN,
    }

    private Map.Entry<TransferStatus, String> categorize(BankWithTask task) {
        String msg;
        try {
            msg = task.call();
        } catch (IndexOutOfBoundsException e) {
            if (new Date().getMinutes() <= 1) {
                return new AbstractMap.SimpleEntry<>(TransferStatus.OTHER, "Resources cannot be transferred during turn change");
            }
            e.printStackTrace();
            return new AbstractMap.SimpleEntry<>(TransferStatus.OTHER, "Unspecified authentication error.");
        }
        if (msg.contains("You successfully transferred funds from the alliance bank.")) {
            return new AbstractMap.SimpleEntry<>(TransferStatus.SUCCESS, msg);
        }
        if (msg.contains("You can't send funds to this nation because they are in Vacation Mode")) {
            return new AbstractMap.SimpleEntry<>(TransferStatus.VACATION_MODE, msg);
        }
        if (msg.contains("There was an Error with your Alliance Bank Withdrawal: You can't withdraw funds to that nation because they are under a naval blockade. When the naval blockade ends they will be able to receive funds.")) {
            return new AbstractMap.SimpleEntry<>(TransferStatus.BLOCKADE, msg);
        }
        if (msg.contains("This player has been flagged for using the same network as you.")) {
            return new AbstractMap.SimpleEntry<>(TransferStatus.MULTI, msg);
        }
        if (msg.contains("Insufficient funds") || msg.contains("You don't have that much")) {
            return new AbstractMap.SimpleEntry<>(TransferStatus.INSUFFICIENT_FUNDS, msg);
        }
        if (msg.contains("You did not enter a valid recipient name.")) {
            return new AbstractMap.SimpleEntry<>(TransferStatus.INVALID_DESTINATION, msg);
        }
        if (msg.contains("You did not withdraw anything.")) {
            return new AbstractMap.SimpleEntry<>(TransferStatus.NOTHING_WITHDRAWN, msg);
        }
        return new AbstractMap.SimpleEntry<>(TransferStatus.OTHER, msg);
        //
    }
}
