
package link.locutus.discord.util.offshore;

import com.politicsandwar.graphql.model.Bankrec;
import com.politicsandwar.graphql.model.GameInfo;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.AllianceMeta;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBAlliancePosition;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
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
import link.locutus.discord.web.jooby.BankRequestHandler;
import link.locutus.discord.web.jooby.WebRoot;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.User;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.SQLException;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    private static final AtomicBoolean outOfSync = new AtomicBoolean(false);
    public static void setOutOfSync() {
        outOfSync.set(true);
    }
    public synchronized boolean sync(boolean force) {
        if (force || outOfSync.get()) return sync();
        return false;
    }

    public synchronized boolean sync() {
        return sync(null);
    }

    public synchronized boolean sync(Long latest) {
        return sync(latest, true);
    }

    private double[] lastFunds2 = null;

    public synchronized boolean sync(Long latest, boolean checkLast) {
        double[] stockpile = null;
        PoliticsAndWarV3 api = null;
        if (!Settings.USE_V2) {
            try {
                api = getGuildDB().getApi(allianceId, false, AlliancePermission.VIEW_BANK);
                stockpile = api.getAllianceStockpile(allianceId);
            } catch (HttpServerErrorException.InternalServerError | HttpServerErrorException.ServiceUnavailable |
                     HttpServerErrorException.GatewayTimeout ignore) {
            }
        }
        if (stockpile == null) {
            try {
                AllianceBankContainer funds = getApi().getBank(allianceId).getAllianceBanks().get(0);
                stockpile = PnwUtil.resourcesToArray(PnwUtil.adapt(funds));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (lastFunds2 != null && checkLast) {
            if (Arrays.equals(lastFunds2, stockpile)) {
                System.out.println("Funds are the same");
                return true;
            }
        }
        lastFunds2 = stockpile;

        DBAlliance alliance = getAlliance();
        ByteBuffer bankMeta = alliance.getMeta(AllianceMeta.BANK_UPDATE_INDEX);
        int bankMetaI = bankMeta == null ? 0 : bankMeta.getInt();
        Transaction2 latestTx = Locutus.imp().getBankDB().getLatestTransaction();
        if (latestTx != null && latestTx.tx_id < bankMetaI) {
            bankMetaI = 0;
        }

        int finalBankMetaI = bankMetaI;
        if (!Settings.USE_V2) {
            List<Bankrec> bankRecs = api.fetchAllianceBankRecs(allianceId, f -> {
                f.or_id(List.of(allianceId));
                f.rtype(List.of(2));
                f.stype(List.of(2));
                if (finalBankMetaI > 0) f.min_id(finalBankMetaI);
            });

            if (bankRecs.isEmpty()) {
                if (ResourceType.isEmpty(stockpile)) {
                    throw new IllegalArgumentException("No bank records & stockpile found for " + allianceId);
                }
                throw new IllegalArgumentException("No bank records found for " + allianceId + " | " + alliance.getId() + " | " + PnwUtil.resourcesToString(stockpile));
            }

            int minId = Integer.MAX_VALUE;
            int maxId = Integer.MIN_VALUE;
            long minDate = 0;
            for (Bankrec bankRec : bankRecs) {
                Transaction2 tx = Transaction2.fromApiV3(bankRec);
                minId = Math.min(minId, tx.tx_id);
                maxId = Math.max(maxId, tx.tx_id);
                minDate = Math.min(minDate, tx.tx_datetime);
            }

            // add transactions
            System.out.println("Add " + bankRecs.size());
            Locutus.imp().runEventsAsync(events -> Locutus.imp().getBankDB().saveBankRecs(bankRecs, events));

            if (bankRecs.size() > 0) {
                // delete legacy transactions for alliance id after date
                Locutus.imp().getBankDB().deleteLegacyAllianceTransactions(allianceId, minDate - 1000);

                // set bank update timestamp
                alliance.setMeta(AllianceMeta.BANK_UPDATE_INDEX, minDate);
            }
        }

        return false;
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
        if (force || outOfSync.get()) sync();

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

    public synchronized Map<ResourceType, Double> getDeposits(long guildId, boolean force) {
        List<Transaction2> toProcess = getTransactionsGuild(guildId, force);

        return PnwUtil.resourcesToMap(addTransfers(toProcess, guildId, 3));
    }

    public synchronized List<Transaction2> getTransactionsAA(int allianceId, boolean force) {
        return getTransactionsAA(Collections.singleton(allianceId), force);
    }
    public synchronized List<Transaction2> getTransactionsAA(Set<Integer> allianceId, boolean force) {
        if (force || outOfSync.get()) sync();

        GuildDB db = getGuildDB();
        List<Transaction2> transactions = new ArrayList<>();
        List<Transaction2> offset = new ArrayList<>();
        for (int id : allianceId) {
            transactions.addAll(Locutus.imp().getBankDB().getBankTransactions(id, 2));
            offset.addAll(db.getDepositOffsetTransactions(-id));
        }

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
                            if (!allianceId.contains(transferAA) || offshoreAAs.contains(transfer.getSender()))
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
        return getDepositsAA(Collections.singleton(allianceId), force);
    }
    public synchronized Map<ResourceType, Double> getDepositsAA(Set<Integer> allianceIds, boolean force) {
        List<Transaction2> toProcess = getTransactionsAA(allianceIds, force);
        Set<Long> allianceIdsLong = allianceIds.stream().map(Integer::longValue).collect(Collectors.toSet());
        return PnwUtil.resourcesToMap(addTransfers(toProcess, allianceIdsLong, 2));
    }

//    public List<Transaction2> filterTransactions(int allianceId)

    private double[] addTransfers(List<Transaction2> transactions, Set<Long> ids, int type) {
        double[] resources = ResourceType.getBuffer();
        for (Transaction2 transfer : transactions) {
            int sign;

            // transfer.sender_id == 0 && transfer.sender_type == 0 &&
            if ((ids.contains(transfer.sender_id) && transfer.sender_type == type) &&
                    (offshoreAAs.contains(transfer.getReceiver()) || (transfer.tx_id == -1))) {
                sign = 1;

                // transfer.receiver_id == 0 && transfer.receiver_type == 0 &&
            } else if ((ids.contains(transfer.receiver_id) && transfer.receiver_type == type) &&
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

    public boolean isDisabled(long guild) {
        // dont disable self
        if (guild == this.getGuildDB().getIdLong()) return false;
        if (disabledGuilds.containsKey(guild)) {
            return true;
        }
        Set<Long> coalition = getGuildDB().getCoalitionRaw(Coalition.FROZEN_FUNDS);
        if (coalition.contains(guild)) return true;
        GuildDB db = Locutus.imp().getGuildDB(guild);
        if (db != null) {
            Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
            if (aaId != null && coalition.contains((long) aaId)) {
                return true;
            }
        }
        return false;
    }

    public Map<Long, Boolean> disabledGuilds = new ConcurrentHashMap<>();

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

                    List<Transaction2> transactions = Locutus.imp().getBankDB().getTransactionsByNote(append, cutoff);
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
                            body.append("To set the limit for a user: " + CM.bank.limits.setTransferLimit.cmd.toSlashMention() + "\n");
                            body.append("To set the default " + CM.settings.cmd.create(GuildDB.Key.BANKER_WITHDRAW_LIMIT.name(), "<amount>") + "");
                            DiscordUtil.createEmbedCommand(alertChannel, "Banker withdraw limit exceeded", body.toString());
                            Role adminRole = Roles.ADMIN.toRole(senderDB.getGuild());
                            if (adminRole != null) {
                                RateLimitUtil.queue(alertChannel.sendMessage("^ " + adminRole.getAsMention()));
                            }
                        }
                        return new AbstractMap.SimpleEntry<>(TransferStatus.INSUFFICIENT_FUNDS, "You (" + banker.getNation() + ") have hit your transfer limit ($" + MathMan.format(withdrawLimit) + ")");
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

            if (isDisabled(senderDB.getGuild().getIdLong())) {
                throw new IllegalArgumentException("There was an error transferring funds (failed to fetch bank stockpile). Please have an admin use " + CM.offshore.unlockTransfers.cmd.toSlashMention() + " in the offshore server (" + getGuildDB().getIdLong() + ")");
            }

            boolean hasAdmin = false;
            User bankerUser = banker.getUser();
            if (bankerUser != null) hasAdmin = Roles.ECON.has(bankerUser, offshoreDB.getGuild());

            // Ensure sufficient deposits
            boolean valid = senderDB == offshoreDB;
            Map<NationOrAllianceOrGuild, double[]> depositsByAA = getDepositsByAA(senderDB, true);
            double[] deposits = ResourceType.getBuffer();
            double[] finalDeposits = deposits;
            depositsByAA.forEach((a, b) -> ResourceType.add(finalDeposits, b));

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

            Integer aaId2 = senderDB.getOrNull(GuildDB.Key.ALLIANCE_ID);
            disabledGuilds.put(senderDB.getGuild().getIdLong(), true);

            Map<ResourceType, Double> transfer = PnwUtil.resourcesToMap(amount);

            // add first
            MessageChannel logChannel = getGuildDB().getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL);

            long tx_datetime = System.currentTimeMillis();
            String offshoreNote = "#deposit #receiver_id=" + receiver.getId() + " #receiver_type=" + receiver.getReceiverType();

            try {
                offshoreDB.addBalanceMulti(depositsByAA, amount, -1, banker.getNation_id(), offshoreNote);
//                offshoreDB.addTransfer(tx_datetime, 0, 0, senderDB, banker.getNation_id(), offshoreNote, amount);
            } catch (Throwable e) {
                e.printStackTrace();
                if (logChannel != null) {
                    String msg = "Transfer error " + e.getMessage() + " | " + PnwUtil.resourcesToString(amount) + " | " + transfer + " | " + senderDB.getGuild().toString() + "/" + aaId2 + " | <@" + Settings.INSTANCE.ADMIN_USER_ID + ">";
                    RateLimitUtil.queue(logChannel.sendMessage(msg));
                }
                throw e;
            }

            Map.Entry<OffshoreInstance.TransferStatus, String> result = transferSafe(receiver, transfer, note);

            switch (result.getKey()) {
                default:
                case OTHER:
                    if (logChannel != null) {
                        String msg = "Unknown result for: " + senderDB.getGuild().toString() + "/" + aaId2 + ": " + result + " | <@" + Settings.INSTANCE.ADMIN_USER_ID + ">";
                        RateLimitUtil.queue(logChannel.sendMessage(msg));
                    }
                case SUCCESS:
                case ALLIANCE_BANK: {
                    if ((result.getKey() == OffshoreInstance.TransferStatus.SUCCESS || result.getKey() == OffshoreInstance.TransferStatus.ALLIANCE_BANK)) {
                        double[] newDeposits = getDeposits(senderDB, true);
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
                        if (logChannel != null) {
                            String msg = "New Deposits for: " + senderDB.getGuild().toString() + "/" + aaId2 + ": `" + PnwUtil.resourcesToString(newDeposits) + ("`");
                            RateLimitUtil.queue(logChannel.sendMessage(msg));
                        }
                    } else {
                        valid = false;
                    }
                    if (valid) {
                        disabledGuilds.remove(senderDB.getIdLong());
                    } else {
                        String title = "Reimburse";
                        StringBuilder body = new StringBuilder();
                        body.append("`").append(result.getValue()).append("`\n");
                        body.append("ID: " + aaId2 + " | " + senderDB.getGuild().toString());
                        body.append("\nAmount: " + PnwUtil.resourcesToString(transfer));

                        String id = aaId2 == null ? "guild:" + senderDB.getGuild().getIdLong() : ("aa:" + aaId2);
                        String cmd = CM.deposits.add.cmd.create("AA:" + id, PnwUtil.resourcesToString(transfer), null, null).toSlashCommand();
                        body.append("\n" + cmd);

                        if (logChannel != null) {
                            DiscordUtil.createEmbedCommand(logChannel, title, body.toString());
                            RateLimitUtil.queue(logChannel.sendMessage("^ <@" + Settings.INSTANCE.ADMIN_USER_ID + (">")));
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
                case INVALID_API_KEY:
                    disabledGuilds.remove(senderDB.getIdLong());
                    offshoreDB.addBalanceMulti(depositsByAA, amount, 1, banker.getNation_id(), offshoreNote);
//                    double[] negative = ResourceType.negative(amount.clone());
//                    offshoreDB.addTransfer(tx_datetime, 0, 0, senderDB, banker.getNation_id(), offshoreNote, negative);
                    break;
//                default:
//                    throw new IllegalStateException("Unknown result: " + result);
            }
            return result;
        }
    }

    public Map.Entry<TransferStatus, String> transferSafe(DBNation nation, Map<ResourceType, Double> transfer, String note) {
        synchronized (BANK_LOCK) {
            Map.Entry<TransferStatus, String> result = transfer(nation, transfer, note);
            if (result.getKey() == TransferStatus.MULTI && nation.getPosition() > 1) {
                DBAlliance alliance = nation.getAlliance(false);
                GuildDB db = alliance == null ? null : alliance.getGuildDB();
                if (db != null) {
                    OffshoreInstance bank = db.getHandler().getBank();
                    StringBuilder response = new StringBuilder();
                    if (bank.getGuildDB() != getGuildDB()) {
                        result = transfer(alliance, transfer, "#ignore");

                        response.append("\nSent to AA:" + alliance.getName() + "/" + alliance.getAlliance_id());
                        response.append("\n" + result.getValue());
                    } else {
                        result = Map.entry(TransferStatus.SUCCESS, "Withdrawing funds using local account");
                        response.append("\n" + result.getValue());
                        System.out.println("Different DB " + bank.getGuildDB().getGuild() + " | " + db.getGuild());
                    }
                    if (result.getKey() == TransferStatus.SUCCESS) {
                        response.append("\nTransferring to nation...");
                        Auth auth = OffshoreInstance.this.auth;
                        DBAlliancePosition position = nation.getAlliancePosition();
                        if (nation.getPositionEnum().id >= Rank.MEMBER.id || position != null && position.hasPermission(AlliancePermission.WITHDRAW_BANK)) {
                            try {
                                Auth nationAuth = nation.getAuth(null);
                                if (nationAuth != null) auth = nationAuth;
                            } catch (IllegalArgumentException ignore) {}
                        }
                        try {
                            result = bank.transfer(auth, nation, transfer, note);
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                        if (result.getKey() != TransferStatus.SUCCESS) {
                            result = new AbstractMap.SimpleEntry<>(TransferStatus.SUCCESS, result.getValue());
                        }
                        response.append("\n" + result.getValue());
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
//            BankWithTask task = new BankWithTask(auth, allianceId, 0, nation, new Function<Map<ResourceType, Double>, String>() {
//                @Override
//                public String apply(Map<ResourceType, Double> stock) {
//                    for (Map.Entry<ResourceType, Double> entry : transfer.entrySet()) {
//                        ResourceType type = entry.getKey();
//                        Double currentAmt = stock.getOrDefault(type, 0d);
//                        stock.put(type, currentAmt - entry.getValue());
//                    }
//                    return note;
//                }
//            });
            Map.Entry<TransferStatus, String> result = transferUnsafe(auth, nation, transfer, note);//categorize(task);
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

    public Map.Entry<TransferStatus, String> transferUnsafe(Auth auth, NationOrAlliance receiver, Map<ResourceType, Double> transfer, String note) {
        if (Settings.USE_V2 && auth != null) {
            // todo test if game is still down
            WebRoot web = WebRoot.getInstance();

            BankRequestHandler handler = web.getLegacyBankHandler();
            if (auth.getNationId() != Settings.INSTANCE.NATION_ID || auth.getAllianceId() != allianceId) {
                throw new IllegalArgumentException("Game API is down currently");
            }

            UUID uuid = UUID.randomUUID();
            Future<String> request = handler.addRequest(uuid, receiver, PnwUtil.resourcesToArray(transfer), note);

            try {
                String response = request.get(20, TimeUnit.SECONDS);
                return categorize(response);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                return new AbstractMap.SimpleEntry<>(TransferStatus.OTHER, "Timeout: " + e.getMessage());
            }
        }

        if (auth == null || !auth.isValid()) {
            // get api
            try {
                PoliticsAndWarV3 api = getAlliance().getApi(false, AlliancePermission.WITHDRAW_BANK);
                Bankrec result = api.transferFromBank(PnwUtil.resourcesToArray(transfer), receiver, note);
                return new AbstractMap.SimpleEntry<>(TransferStatus.SUCCESS, result.toString());
            } catch (HttpClientErrorException.Unauthorized e) {
                return new AbstractMap.SimpleEntry<>(TransferStatus.INVALID_DESTINATION, "Invalid API key");

            } catch (RuntimeException e) {
                String msg = e.getMessage();
                return categorize(msg);
            }
        }


        DBNation receiverNation = null;
        int receiverAlliance = 0;
        if (receiver.isNation()) receiverNation = receiver.asNation();
        else receiverAlliance = receiver.asAlliance().getAlliance_id();
        BankWithTask task = new BankWithTask(auth, allianceId, receiverAlliance, receiverNation, new Function<Map<ResourceType, Double>, String>() {
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
        return categorize(task);
    }

    public Map.Entry<TransferStatus, String> transfer(DBAlliance alliance, Map<ResourceType, Double> transfer, String note) {
        if (alliance.getAlliance_id() == allianceId) return new AbstractMap.SimpleEntry<>(TransferStatus.INVALID_DESTINATION, "You can't send funds to yourself");
        if (!TimeUtil.checkTurnChange()) return new AbstractMap.SimpleEntry<>(TransferStatus.TURN_CHANGE, "You cannot transfer close to turn change");
        if (!alliance.exists()) {
            return new AbstractMap.SimpleEntry<>(TransferStatus.INVALID_DESTINATION, "The alliance does not exist");
        }
        if (alliance.getNations(true, 10000, true).isEmpty()) {
            return new AbstractMap.SimpleEntry<>(TransferStatus.INVALID_DESTINATION, "The alliance has no members");
        }
        synchronized (BANK_LOCK) {
            Map.Entry<TransferStatus, String> result = transferUnsafe(this.auth, alliance, transfer, note);
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

    public Map<NationOrAllianceOrGuild, double[]> getDepositsByAA(GuildDB guildDb, boolean update) {
        Map<NationOrAllianceOrGuild, double[]> result = new HashMap<>();

        GuildDB delegate = guildDb.getDelegateServer();
        if (delegate != null) {
            guildDb = delegate;
        }
        if (guildDb.getOffshore() != this) return result;


        Set<Integer> ids = guildDb.getAllianceids();

        if (!ids.isEmpty()) {

            for (int id : ids) {
                double[] rss = PnwUtil.resourcesToArray(getDepositsAA(ids, update));
                update = false;
                result.put(DBAlliance.getOrCreate(id), rss);
            }
        } else {
            GuildDB offshoreDb = getGuildDB();
            if (offshoreDb != null) {
                long guildId = guildDb.getGuild().getIdLong();
                double[] rss = PnwUtil.resourcesToArray(getDeposits(guildId, update));
                result.put(guildDb, rss);
            }
        }
        return result;
    }

    public double[] getDeposits(GuildDB guildDb, boolean update) {
        Map<NationOrAllianceOrGuild, double[]> byAA = getDepositsByAA(guildDb, update);
        double[] deposits = ResourceType.getBuffer();
        byAA.forEach((a, b) -> ResourceType.add(deposits, b));
        return deposits;
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

        INVALID_API_KEY,
    }

    private Map.Entry<TransferStatus, String> categorize(BankWithTask task) {
        try {
            String msg = task.call();
            return categorize(msg);
        } catch (IndexOutOfBoundsException e) {
            if (new Date().getMinutes() <= 1) {
                return new AbstractMap.SimpleEntry<>(TransferStatus.OTHER, "Resources cannot be transferred during turn change");
            }
            e.printStackTrace();
            return new AbstractMap.SimpleEntry<>(TransferStatus.OTHER, "Unspecified authentication error.");
        }
    }

    private Map.Entry<TransferStatus, String> categorize(String msg) {
        if (msg.contains("You successfully transferred funds from the alliance bank.")) {
            return new AbstractMap.SimpleEntry<>(TransferStatus.SUCCESS, msg);
        }
        if (msg.contains("You can't send funds to this nation because they are in Vacation Mode") || msg.contains("You can't withdraw resources to a nation in vacation mode")) {
            return new AbstractMap.SimpleEntry<>(TransferStatus.VACATION_MODE, msg);
        }
        if (msg.contains("There was an Error with your Alliance Bank Withdrawal: You can't withdraw funds to that nation because they are under a naval blockade. When the naval blockade ends they will be able to receive funds.")
        || msg.contains("You can't withdraw resources to a blockaded nation.")) {
            return new AbstractMap.SimpleEntry<>(TransferStatus.BLOCKADE, msg);
        }
        if (msg.contains("This player has been flagged for using the same network as you.")) {
            return new AbstractMap.SimpleEntry<>(TransferStatus.MULTI, msg);
        }
        if (msg.contains("Insufficient funds") || msg.contains("You don't have that much") || msg.contains("You don't have enough resources.")) {
            return new AbstractMap.SimpleEntry<>(TransferStatus.INSUFFICIENT_FUNDS, msg);
        }
        if (msg.contains("You did not enter a valid recipient name.")) {
            return new AbstractMap.SimpleEntry<>(TransferStatus.INVALID_DESTINATION, msg);
        }
        if (msg.contains("You did not withdraw anything.") || msg.contains("You can't withdraw no resources.")) {
            return new AbstractMap.SimpleEntry<>(TransferStatus.NOTHING_WITHDRAWN, msg);
        }
        boolean whitelistedError = msg.contains("The API key you provided does not allow whitelisted access.");
        if (whitelistedError || msg.contains("The API key you provided is not valid.")) {
            String[] keys = getGuildDB().getOrNull(GuildDB.Key.API_KEY);
            if (keys == null) {
                msg += "\nEnsure " + GuildDB.Key.API_KEY + " is set: " + CM.settings.cmd.toSlashMention();
            } else {
                Integer nation = Locutus.imp().getDiscordDB().getNationFromApiKey(keys[0]);
                if (nation == null) {
                    msg += "\nEnsure " + GuildDB.Key.API_KEY + " is set: " + CM.settings.cmd.toSlashMention() + " to a valid key in the alliance (with bank access)";
                } else {
                    msg += "\nEnsure " + PnwUtil.getNationUrl(nation) + " is a valid nation in the alliance with bank access in " + allianceId;
                }
            }
            if (whitelistedError) {
                msg += "\nEnsure Whitelisted access is enabled in " + Settings.INSTANCE.PNW_URL() + "/account";
            }
            return new AbstractMap.SimpleEntry<>(TransferStatus.INVALID_API_KEY, msg);
        }
        if (msg.contains("You need provide the X-Bot-Key header with the key for a verified bot to use this endpoint.")) {
            return new AbstractMap.SimpleEntry<>(TransferStatus.INVALID_API_KEY, msg);
        }
        if (msg.isEmpty()) msg = "Unknown Error (Captcha?)";
        return new AbstractMap.SimpleEntry<>(TransferStatus.OTHER, msg);
    }
}
