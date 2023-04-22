
package link.locutus.discord.util.offshore;

import com.politicsandwar.graphql.model.Bankrec;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.AccessType;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.AllianceMeta;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.task.balance.BankWithTask;
import link.locutus.discord.apiv1.domains.subdomains.AllianceBankContainer;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.web.jooby.BankRequestHandler;
import link.locutus.discord.web.jooby.WebRoot;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.User;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class OffshoreInstance {
    public static final Object BANK_LOCK = new Object();
    public static final boolean DISABLE_TRANSFERS = false;
    private final int allianceId;

    private GuildDB guildDBCached;

    public OffshoreInstance(int allianceId) {
        this.allianceId = allianceId;
    }

    public int getAllianceId() {
        return allianceId;
    }

    public GuildDB getGuildDB() {
        if (guildDBCached == null || !guildDBCached.isAllianceId(allianceId)) {
            guildDBCached = Locutus.imp().getGuildDBByAA(allianceId);
        }
        return guildDBCached;
    }


    public boolean isOffshoreAA(long allianceId) {
        if (allianceId == this.allianceId) return true;
        GuildDB db = getGuildDB();
        if (db != null) {
            return db.getCoalitionRaw(Coalition.OFFSHORE).contains((long) allianceId);
        }
        return false;
    }

    public Set<Long> getOffshoreAAs() {
        Set<Long> result = new LinkedHashSet<>();
        result.add((long) allianceId);
        GuildDB db = getGuildDB();
        if (db != null) {
            result.addAll(db.getCoalitionRaw(Coalition.OFFSHORE));
        }
        return result;
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
                api = getAlliance().getApi(AlliancePermission.VIEW_BANK);
                stockpile = api.getAllianceStockpile(allianceId);
            } catch (HttpServerErrorException.InternalServerError | HttpServerErrorException.ServiceUnavailable |
                     HttpServerErrorException.GatewayTimeout ignore) {
            }
        }
        if (stockpile == null) {
            try {
                AllianceBankContainer funds = getAlliance().getApiV2().getBank(allianceId).getAllianceBanks().get(0);
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
                if (getGuildDB().getOrNull(GuildDB.Key.PUBLIC_OFFSHORING) == Boolean.TRUE) {
                    throw new IllegalArgumentException("No bank records found for " + allianceId + " | " + alliance.getId() + " | " + PnwUtil.resourcesToString(stockpile));
                }
            } else {
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
//            if (isOffshoreAA((long) transfer.getSender()) && transfer.isSenderAA()) {
//                sign = -1;
//            } else if (isOffshoreAA((long) transfer.getReceiver()) && transfer.isReceiverAA()) {
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
//            if (isOffshoreAA((long) transfer.getSender()) && transfer.isSenderAA()) {
//                sign = -1;
//            } else if (isOffshoreAA((long) transfer.getReceiver()) && transfer.isReceiverAA()) {
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
                Map<String, String> parsed = PnwUtil.parseTransferHashNotes(note);
                for (Map.Entry<String, String> entry : parsed.entrySet()) {
                    String value = entry.getValue();
                    switch (entry.getKey()) {
                        case "#guild":
                            if (!MathMan.isInteger(value)) continue outer;
                            long transferGuild = Long.parseLong(value);
                            if (transferGuild != guildId || isOffshoreAA(transfer.getSender())) continue outer;
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
        if (!guildDBCached.getCoalitionRaw(Coalition.OFFSHORING).contains(guildId)) {
            throw new IllegalArgumentException("Guild " + guildId + " is not offshoring with " + getGuildDB().getGuild());
        }
        List<Transaction2> toProcess = getTransactionsGuild(guildId, force);

        Map<ResourceType, Double> result = PnwUtil.resourcesToMap(addTransfers(toProcess, guildId, 3));
        return result;
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
                Map<String, String> parsed = PnwUtil.parseTransferHashNotes(note);
                for (Map.Entry<String, String> entry : parsed.entrySet()) {
                    String value = entry.getValue();
                    switch (entry.getKey()) {
                        case "#alliance":
                            if (!MathMan.isInteger(value)) continue outer;
                            int transferAA = Integer.parseInt(value);
                            if (!allianceId.contains(transferAA) || isOffshoreAA(transfer.getSender()))
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
        allianceIds = new LinkedHashSet<>(allianceIds);
        Set<Integer> allowed = getGuildDB().getCoalition(Coalition.OFFSHORING);
        allianceIds.removeIf(f -> !allowed.contains(f));
        if (allianceIds.isEmpty()) return new HashMap<>();
        List<Transaction2> toProcess = getTransactionsAA(allianceIds, force);
        Set<Long> allianceIdsLong = allianceIds.stream().map(Integer::longValue).collect(Collectors.toSet());
        double[] sum = addTransfers(toProcess, allianceIdsLong, 2);
        return PnwUtil.resourcesToMap(sum);
    }

//    public List<Transaction2> filterTransactions(int allianceId)

    private double[] addTransfers(List<Transaction2> transactions, Set<Long> ids, int type) {
        double[] resources = ResourceType.getBuffer();
        for (Transaction2 transfer : transactions) {
            int sign;

            // transfer.sender_id == 0 && transfer.sender_type == 0 &&
            if ((ids.contains(transfer.sender_id) && transfer.sender_type == type) &&
                    (isOffshoreAA(transfer.getReceiver()) || (transfer.tx_id == -1))) {
                sign = 1;

                // transfer.receiver_id == 0 && transfer.receiver_type == 0 &&
            } else if ((ids.contains(transfer.receiver_id) && transfer.receiver_type == type) &&
                    (isOffshoreAA(transfer.getSender()) || (transfer.tx_id == -1))) {
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
                    (isOffshoreAA(transfer.getReceiver()) || (transfer.tx_id == -1))) {
                sign = 1;

                // transfer.receiver_id == 0 && transfer.receiver_type == 0 &&
            } else if ((transfer.receiver_id == id && transfer.receiver_type == type) &&
                    (isOffshoreAA(transfer.getSender()) || (transfer.tx_id == -1))) {
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
        if (DISABLE_TRANSFERS) throw new IllegalArgumentException("Error: Maintenance");
        synchronized (BANK_LOCK) {
            if (nation.isNation()) return transferSafe(nation.asNation(), transfer, note);
            return transfer(nation.asAlliance(), transfer, note);
        }
    }

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
            for (Integer aaId : db.getAllianceIds()) {
                if (coalition.contains(aaId.longValue())) return true;
            }
        }
        return false;
    }

    public Map.Entry<TransferStatus, String> transferFromNationAccountWithRoleChecks(User banker, DBNation nationAccount, DBAlliance allianceAccount, TaxBracket taxAccount, GuildDB senderDB, Long senderChannel, NationOrAlliance receiver, double[] amount, DepositType.DepositTypeInfo depositType, Long expire, UUID grantToken, boolean convertCash, boolean requireConfirmation, boolean bypassChecks) throws IOException {
        if (!TimeUtil.checkTurnChange()) return Map.entry(TransferStatus.TURN_CHANGE, "You cannot transfer close to turn change");

        if (nationAccount != null) nationAccount = new DBNation(nationAccount); // Copy to avoid external mutation

        if (receiver.isAlliance() && !receiver.asAlliance().exists()) {
            return Map.entry(TransferStatus.INVALID_DESTINATION, "Alliance: " + receiver.getUrl() + " has no receivable nations");
        }
        if (!receiver.isNation() && depositType.getType() != DepositType.IGNORE) {
            return Map.entry(TransferStatus.INVALID_NOTE, "Please use `" + DepositType.IGNORE + "` as the depositType when transferring to alliances");
        }

        if (!bypassChecks && receiver.isNation()) {
            DBNation nation = receiver.asNation();
            if (nation.getVm_turns() > 0) return Map.entry(TransferStatus.VACATION_MODE, TransferStatus.VACATION_MODE.msg + " (set the `force` parameter to bypass)");
            if (nation.isGray()) return Map.entry(TransferStatus.GRAY, TransferStatus.GRAY.msg + " (set the `force` parameter to bypass)");
            if (nation.getNumWars() > 0 && nation.isBlockaded()) return Map.entry(TransferStatus.BLOCKADE, TransferStatus.BLOCKADE.msg + " (set the `force` parameter to bypass)");
            if (nation.getActive_m() > 11520) return Map.entry(TransferStatus.INACTIVE, TransferStatus.INACTIVE.msg + " (set the `force` parameter to bypass)");
        }

        Set<Grant.Requirement> failedRequirements = new HashSet<>();
        boolean isGrant = false;
        if (grantToken != null) {
            Grant authorized = BankCommands.AUTHORIZED_TRANSFERS.get(grantToken);
            if (authorized == null) {
                return Map.entry(TransferStatus.INVALID_TOKEN, "Invalid grant token (try again)");
            }
            if (!receiver.isNation()) {
                return Map.entry(TransferStatus.INVALID_DESTINATION, "Grants can only be used to sent to nations");
            }

            for (Grant.Requirement requirement : authorized.getRequirements()) {
                if (!requirement.apply(receiver.asNation())) {
                    failedRequirements.add(requirement);
                    if (requirement.canOverride()) continue;
                    else {
                        return Map.entry(TransferStatus.GRANT_REQUIREMENT, requirement.getMessage());
                    }
                }
            }
            isGrant = true;
        }

        StringBuilder reqMsg = new StringBuilder();
        if (!failedRequirements.isEmpty()) {
            reqMsg.append("The following grant requirements were not met: ");
            for (Grant.Requirement requirement : failedRequirements) {
                reqMsg.append(" - " + requirement.getMessage() + "\n");
            }
            if (!bypassChecks) {
                return Map.entry(TransferStatus.GRANT_REQUIREMENT, reqMsg + "\n(set the `force` parameter to bypass)");
            }
        }

        List<String> otherNotes = new ArrayList<>();

        if (expire != null && expire != 0) {
            if (!receiver.isNation()) {
                return Map.entry(TransferStatus.INVALID_NOTE, "Expire can only be used with nations");
            }
            // Requires econ role for the alliance to send expire

            if (expire < 1000) {
                return Map.entry(TransferStatus.INVALID_NOTE, "Expire time must be at least 1 second (e.g. `3d` for three days)");
            }
            otherNotes.add("#expire=" + TimeUtil.secToTime(TimeUnit.MILLISECONDS, expire));
        }

        DBNation bankerNation = DiscordUtil.getNation(banker);

        Map<Long, AccessType> allowedIds;
        try {
            allowedIds = senderDB.getAllowedBankAccountsOrThrow(banker, receiver, senderChannel);
        } catch (IllegalArgumentException e) {
            return Map.entry(TransferStatus.AUTHORIZATION, e.getMessage());
        }
        if (allowedIds.isEmpty()) {
            return Map.entry(TransferStatus.AUTHORIZATION, "You do not have permission to do a transfer (receiver: " + receiver.getQualifiedName() + ", channel: <#" + senderChannel + ">)");
        }

        if (convertCash) {
            if (!receiver.isNation()) {
                return Map.entry(TransferStatus.INVALID_DESTINATION, "Cash conversion is only to alliances");
            }
            if (senderDB.getOrNull(GuildDB.Key.RESOURCE_CONVERSION) != Boolean.TRUE) {
                return Map.entry(TransferStatus.INVALID_NOTE, "Missing role: " + Roles.ECON.toDiscordRoleNameElseInstructions(senderDB.getGuild()) +
                        "\nMembers do not have permission to convert resources to cash. See " + CM.settings.cmd.toSlashMention() + " with key: " + GuildDB.Key.RESOURCE_CONVERSION);
            }
            allowedIds.entrySet().removeIf(f -> f.getValue() != AccessType.ECON);
            if (allowedIds.isEmpty()) {
                return Map.entry(TransferStatus.INVALID_NOTE, "You do not have permission to convert cash to resources. Missing role: " + Roles.ECON.toDiscordRoleNameElseInstructions(senderDB.getGuild()));
            }
            otherNotes.add("#cash=" + MathMan.format(PnwUtil.convertedTotal(amount)));
        }

        boolean hasAnyEcon = allowedIds.containsValue(AccessType.ECON);
        boolean isInternalTransfer = false;
        if (nationAccount == null) {
            if (!hasAnyEcon) {
                nationAccount = new DBNation(bankerNation); // Copy to avoid external mutation
            } else {
                allowedIds.entrySet().removeIf(f -> f.getValue() != AccessType.ECON);
            }
        } else if (nationAccount.getId() != bankerNation.getId()) {
            allowedIds.entrySet().removeIf(f -> f.getValue() != AccessType.ECON);
            if (allowedIds.isEmpty()) {
                return Map.entry(TransferStatus.AUTHORIZATION, "You do not have permission to do a transfer for another nation's account using this channel");
            }
        }

        if (allianceAccount != null) {
            if (!allowedIds.containsKey((long) allianceAccount.getId())) {
                return Map.entry(TransferStatus.AUTHORIZATION, "You attempted to withdraw from the alliance account: " + allianceAccount.getId() + " but are only authorized for " + StringMan.getString(allowedIds) + " (did you use the correct channel?)");
            }
            allowedIds.entrySet().removeIf(f -> f.getKey() != (long) allianceAccount.getId());
        }

        Map<Long, AccessType> allowedIdsCopy = new HashMap<>(allowedIds);

        Set<Integer> guildAllianceIds = senderDB.getAllianceIds();

        if (expire != null && expire != 0) {
            allowedIds.entrySet().removeIf(f -> f.getValue() != AccessType.ECON);
            if (allowedIds.isEmpty()) {
                return Map.entry(TransferStatus.AUTHORIZATION, "You are only authorized " + DepositType.DEPOSIT + " but attempted to do " + depositType);
            }
        }

        boolean rssConversion = senderDB.getOrNull(GuildDB.Key.RESOURCE_CONVERSION) == Boolean.TRUE;
        boolean ignoreGrants = senderDB.getOrNull(GuildDB.Key.MEMBER_CAN_WITHDRAW_IGNORES_GRANTS) == Boolean.TRUE;
        double txValue = PnwUtil.convertedTotal(amount);

        double[] myDeposits = null;
        if (nationAccount != null) {
            if (depositType.getType() != DepositType.DEPOSIT) {
                allowedIds.entrySet().removeIf(f -> f.getValue() != AccessType.ECON);
                if (allowedIds.isEmpty()) {
                    return Map.entry(TransferStatus.AUTHORIZATION, "You are only authorized " + DepositType.DEPOSIT + " but attempted to do " + depositType);
                }
            }

            if (nationAccount.getId() != bankerNation.getId()) {
                allowedIds.entrySet().removeIf(f -> f.getValue() != AccessType.ECON);

                if (!allowedIds.isEmpty() && !guildAllianceIds.isEmpty() && !Roles.ECON.has(banker, senderDB, nationAccount.getAlliance_id())) {
                    return Map.entry(TransferStatus.AUTHORIZATION, "You attempted to access a nation's account in alliance: " + nationAccount.getAlliance_id() + " but are only authorized for " + StringMan.getString(allowedIdsCopy));
                }
            }

            if (!receiver.isNation() || nationAccount.getNation_id() != receiver.asNation().getNation_id()) {
                isInternalTransfer = true;
                if (depositType.getType() == DepositType.IGNORE) {
                    return Map.entry(TransferStatus.INVALID_NOTE, "Please use `" + DepositType.DEPOSIT + "` as the depositType when transferring to another nation");
                }
            }

//            if (!requireConfirmation)
            {
                myDeposits = nationAccount.getNetDeposits(senderDB, !ignoreGrants, requireConfirmation ? -1 : 0L);
                double[] myDepositsNormalized = PnwUtil.normalize(myDeposits);
                double myDepoValue = PnwUtil.convertedTotal(myDepositsNormalized, false);

                double[] missing = null;

                for (ResourceType type : ResourceType.values) {
                    if (Math.round(myDepositsNormalized[type.ordinal()] * 100) < Math.round(amount[type.ordinal()] * 100)) {
                        if (missing == null) {
                            missing = ResourceType.getBuffer();
                        }
                        missing[type.ordinal()] = amount[type.ordinal()] - myDepositsNormalized[type.ordinal()];
                    }
                }
                if (missing != null) {
                    if (!rssConversion) {
                        String msg = nationAccount.getNation() + " is missing `" + PnwUtil.resourcesToString(missing) + "`. (see " +
                                CM.deposits.check.cmd.create(nationAccount.getNation(), null, null, null, null, null, null, null, null) +
                                " ). RESOURCE_CONVERSION is disabled (see " +
                                CM.settings.cmd.create(GuildDB.Key.RESOURCE_CONVERSION.name(), "true", null, null) +
                                ")";
                        allowedIds.entrySet().removeIf(f -> f.getValue() != AccessType.ECON);
                        if (allowedIds.isEmpty()) {
                            return Map.entry(TransferStatus.INSUFFICIENT_FUNDS, msg);
                        }
                        reqMsg.append(msg + "\n");
                    } else if(myDepoValue < txValue) {
                        String msg = nationAccount.getNation() + "'s deposits are worth $" + MathMan.format(myDepoValue) + "(market max) but you requested to withdraw $" + MathMan.format(txValue) + " worth of resources";
                        allowedIds.entrySet().removeIf(f -> f.getValue() != AccessType.ECON);
                        if (allowedIds.isEmpty()) {
                            return Map.entry(TransferStatus.INSUFFICIENT_FUNDS, msg);
                        }
                        reqMsg.append(msg + "\n");
                    }
                }
            }
        }

        if (taxAccount != null) {
            int taxAAId = taxAccount.getAlliance_id();
            if (taxAAId == 0) {
                return Map.entry(TransferStatus.INVALID_DESTINATION, "The tax bracket you specified either does not exist, or has no alliance: " + taxAccount.getQualifiedName());
            }
            AccessType aaAccess = allowedIds.get((long) taxAAId);
            if (aaAccess == null) {
                return Map.entry(TransferStatus.AUTHORIZATION, "You do not have permission to withdraw from the tax bracket: " + taxAccount.getQualifiedName());
            }
            if (aaAccess != AccessType.ECON) {
                return Map.entry(TransferStatus.AUTHORIZATION, "You only have member permissions and cannot withdraw from the tax bracket: " + taxAccount.getQualifiedName());
            }
            allowedIds.entrySet().removeIf(f -> f.getKey() != taxAAId);

            Map<DepositType, double[]> bracketDeposits = senderDB.getTaxBracketDeposits(taxAccount.taxId, 0L, false, false);
            double[] taxDeposits = bracketDeposits.get(DepositType.TAX);
            double[] taxDepositsNormalized = PnwUtil.normalize(taxDeposits);
            double taxDepoValue = PnwUtil.convertedTotal(taxDepositsNormalized);
            double[] missing = null;
            for (ResourceType type : ResourceType.values) {
                if (Math.round(taxDepositsNormalized[type.ordinal()] * 100) < Math.round(amount[type.ordinal()] * 100)) {
                    if (missing == null) {
                        missing = ResourceType.getBuffer();
                    }
                    missing[type.ordinal()] = amount[type.ordinal()] - taxDepositsNormalized[type.ordinal()];
                }
            }
            if (missing != null) {
                if (!rssConversion) {
                    String msg = taxAccount.getQualifiedName() + " is missing `" + PnwUtil.resourcesToString(missing) + "`. (see " +
                            CM.deposits.check.cmd.create(taxAccount.getQualifiedName(), null, null, null, null, null, null, null, null) +
                            " ). RESOURCE_CONVERSION is disabled (see " +
                            CM.settings.cmd.create(GuildDB.Key.RESOURCE_CONVERSION.name(), "true", null, null) +
                            ")";
                    allowedIds.entrySet().removeIf(f -> f.getValue() != AccessType.ECON);
                    if (allowedIds.isEmpty()) {
                        return Map.entry(TransferStatus.INSUFFICIENT_FUNDS, msg);
                    }
                    reqMsg.append(msg + "\n");
                } else if(taxDepoValue < txValue) {
                    String msg = taxAccount.getQualifiedName() + "'s deposits are worth $" + MathMan.format(taxDepoValue) + "(market max) but you requested to withdraw $" + MathMan.format(txValue) + " worth of resources";
                    allowedIds.entrySet().removeIf(f -> f.getValue() != AccessType.ECON);
                    if (allowedIds.isEmpty()) {
                        return Map.entry(TransferStatus.INSUFFICIENT_FUNDS, msg);
                    }
                    reqMsg.append(msg + "\n");
                }
            }

        }

        if (requireConfirmation) {
            StringBuilder body = new StringBuilder();
            if (reqMsg.length() > 0) {
                body.append("**Errors:**\n");
                body.append(" - " + reqMsg.toString().trim().replace("\n", "\n - "));
                body.append("\n\n");
            }
            // type / otherNotes
            if (receiver.isNation()) {
                DBNation nation = receiver.asNation();
                if (nation.active_m() > 2440) {
                    // receiver is X days inactive
                    body.append("**Receiver is inactive:" + TimeUtil.secToTime(TimeUnit.MINUTES, nation.active_m()) + "**\n");

                    if (!guildAllianceIds.isEmpty()) {
                        if (!guildAllianceIds.contains(nation.getAlliance_id())) {
                            body.append("**Receiver is NOT in your alliance**\n");
                        }
                    }
                    if (body.isEmpty() && nation.getPositionEnum().id <= Rank.APPLICANT.id) {
                        body.append("**Receiver is NOT a member ingame**\n");
                    }

                    User receiverUser = nation.getUser();
                    if (receiverUser == null) {
                        body.append("**Receiver is NOT registered with Locutus**\n");
                    } else {
                        Member member = senderDB.getGuild().getMember(receiverUser);
                        if (member == null) {
                            body.append("**Receiver is NOT in your guild**\n");
                        } else {
                            if (Roles.MEMBER.getAllowedAccounts(receiverUser, senderDB).isEmpty()) {
                                body.append("**Receiver lacks MEMBER role** (see: " + CM.role.setAlias.cmd.toSlashMention() + ")\n");
                            }
                        }
                    }
                }

                body.append("**Receiver:** " + nation.getNationUrlMarkup(true) + " | " + nation.getAllianceUrlMarkup(true) + "\n");
            } else {
                DBAlliance alliance = receiver.asAlliance();
                if (alliance.getNations(f -> f.active_m() < 7200 && f.getPositionEnum().id >= Rank.HEIR.id && f.getVm_turns() == 0).isEmpty()) {
                    body.append("**Alliance has no active leaders/heirs (5d)**\n");
                }
                body.append("**Receiver AA:**" + alliance.getMarkdownUrl() + " (" + MathMan.format(alliance.getScore()) + "ns, #" + alliance.getRank() + ")\n");
            }
            body.append("**Amount:** `" + PnwUtil.resourcesToString(amount) + "` worth: ~$" + MathMan.format(PnwUtil.convertedTotal(amount)) + "\n");
            body.append("**Note:** `" + (depositType + " " + StringMan.join(otherNotes, " ")).trim().toLowerCase(Locale.ROOT) + "`\n");

            body.append("**Sender:**\n");
            if (banker != null) {
                body.append(" - Banker: " + banker.getAsMention() + "\n");
            }
            if (nationAccount != null) {
                body.append(" - Nation Account: " + nationAccount.getNationUrlMarkup(true) + "\n");
            } else if (depositType.getType() == DepositType.IGNORE) {
                body.append(" - Will NOT deduct from a nation deposits\n");
            } else {
                body.append(" - Will deduct from receiver's deposits\n");
            }
            if (allianceAccount != null) {
                body.append(" - Alliance Account: " + allianceAccount.getMarkdownUrl() + "\n");
            }
            if (senderDB != null) {
                body.append(" - Sender Guild: " + senderDB.getGuild().getName() + "\n");
            }
            if (taxAccount != null) {
                body.append(" - Tax Account: " + taxAccount.toString() + "\n");
            }
            return Map.entry(TransferStatus.CONFIRMATION, body.toString());
        }

        if (allowedIds.isEmpty()) {
            StringBuilder response = new StringBuilder();
            if (allowedIdsCopy.isEmpty()) {
                response.append("You do not have permission to send directly from ANY alliance account using this channel (did you instead mean to do a personal withdrawal?)\n");
            } else {
                response.append("You have permission to withdraw from the alliance accounts: " + StringMan.getString(allowedIdsCopy) + " but are not authorized to make this withdrawal (did you use the correct channel or account?)\n");
            }
            return Map.entry(TransferStatus.AUTHORIZATION, response.toString());
        }

        long primaryAccountId;
        if (nationAccount != null && allowedIds.containsKey((long) nationAccount.getAlliance_id())) {
            primaryAccountId = nationAccount.getAlliance_id();
        } else if (receiver.isNation() && allowedIds.containsKey((long) receiver.asNation().getAlliance_id())) {
            primaryAccountId = receiver.asNation().getAlliance_id();
        } else {
            primaryAccountId = allowedIds.keySet().iterator().next();
        }
        String note = depositType.toString(primaryAccountId);
        if (otherNotes.size() > 0) {
            note += " " + String.join(" ", otherNotes);
        }

        String ingameNote = isInternalTransfer ? "#" + DepositType.IGNORE.name().toLowerCase(Locale.ROOT) : note;

        long timestamp = System.currentTimeMillis();
        if (isInternalTransfer) {
            senderDB.subtractBalance(timestamp, nationAccount, bankerNation != null ? bankerNation.getNation_id() : 0, note, amount);
            if (depositType.getType() == DepositType.DEPOSIT) {
                double[] myNewDeposits = nationAccount.getNetDeposits(senderDB, !ignoreGrants, -1L);
                // ensure myDeposits and myNewDeposits difference is amount
                double[] diff = ResourceType.getBuffer();
                for (int i = 0; i < amount.length; i++) {
                    diff[i] = myDeposits[i] - myNewDeposits[i];
                }
                for (int i = 0; i < amount.length; i++) {
                    if (Math.round((diff[i] - amount[i]) * 100) > 1) {
                        return Map.entry(TransferStatus.OTHER, "Internal error: " + PnwUtil.resourcesToString(diff) + " != " + PnwUtil.resourcesToString(amount));
                    }
                }
            }
        }
        if (taxAccount != null) {
            double[] amountNegative = ResourceType.negative(amount.clone());
            if (receiver.isNation()) {
                senderDB.addBalanceTaxId(timestamp, taxAccount.getId(), receiver.getId(), bankerNation != null ? bankerNation.getNation_id() : 0, "#tax", amountNegative);
            } else {
                senderDB.addBalanceTaxId(timestamp, taxAccount.getId(), bankerNation != null ? bankerNation.getNation_id() : 0, "#tax", amountNegative);
            }

        }

        Map.Entry<TransferStatus, String> result = transferFromAllianceDeposits(bankerNation, senderDB, f -> allowedIds.containsKey(f.longValue()), receiver, amount, ingameNote);
        switch (result.getKey()) {
            default: {
                if (isInternalTransfer) {
                    senderDB.addBalance(timestamp, nationAccount, bankerNation != null ? bankerNation.getNation_id() : 0, note, amount);
                }
                if (taxAccount != null) {
                    senderDB.addBalanceTaxId(timestamp, taxAccount.getId(), bankerNation != null ? bankerNation.getNation_id() : 0, "#tax", amount);
                }
                break;
            }
            case ALLIANCE_BANK:
            case SUCCESS: {
                if (taxAccount != null) {
                    result = Map.entry(result.getKey(), result.getValue() + "\n" +
                            " - Deducted from tax account: " + taxAccount.getQualifiedName());
                }
                break;
            }
            case OTHER:
            case TURN_CHANGE: {
                break;
            }
        }
        return result;
    }

    public boolean log(GuildDB sender, DBNation banker, NationOrAlliance receiver, String msg) {
        GuildDB db = getGuildDB();
        if (sender.getIdLong() == db.getIdLong()) return true;
        MessageChannel channel = db.getResourceChannel(0);
        if (channel == null) return true;
        String name = sender.getGuild() + "";
        Set<Integer> ids = sender.getAllianceIds();
        if (!ids.isEmpty()) {
            name += "/" + StringMan.join(ids, ",");
        }
        msg = "**" + name + "**: " + "Banker:" + banker.getName() + " -> " + receiver.getUrl() + ":\n" + msg;
        RateLimitUtil.queueMessage(channel, msg, true);
        return true;
    }

    public Map<Long, Boolean> disabledGuilds = new ConcurrentHashMap<>();

    public Map.Entry<TransferStatus, String> transferFromAllianceDeposits(DBNation banker, GuildDB senderDB, Predicate<Integer> allowedAlliances, NationOrAlliance receiver, double[] amount, String note) {
        Map<String, String> notes = PnwUtil.parseTransferHashNotes(note);
        if (notes.containsKey("#alliance") || notes.containsKey("#guild") || notes.containsKey("#account")) {
            return Map.entry(TransferStatus.INVALID_NOTE, "You cannot send with #alliance #guild or #account as the note");
        }

        if (receiver.isAlliance() && !receiver.asAlliance().exists()) {
            return Map.entry(TransferStatus.INVALID_DESTINATION, "Alliance: " + receiver.getUrl() + " has no receivable nations");
        }

        GuildDB delegate = senderDB.getDelegateServer();
        if (delegate != null) senderDB = delegate;

        boolean hasAmount = false;
        for (double amt : amount) if (amt >= 0.01) hasAmount = true;
        for (ResourceType type : ResourceType.values) if (amount[type.ordinal()] < 0) return Map.entry(TransferStatus.NOTHING_WITHDRAWN, "You cannot withdraw negative " + type);
        if (!hasAmount) return Map.entry(TransferStatus.NOTHING_WITHDRAWN, "You did not withdraw anything.");

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
                            body.append("To set the default " + CM.settings.cmd.create(GuildDB.Key.BANKER_WITHDRAW_LIMIT.name(), "<amount>", null, null) + "");
                            DiscordUtil.createEmbedCommand(alertChannel, "Banker withdraw limit exceeded", body.toString());
                            Role adminRole = Roles.ADMIN.toRole(senderDB.getGuild());
                            if (adminRole != null) {
                                RateLimitUtil.queue(alertChannel.sendMessage("^ " + adminRole.getAsMention()));
                            }
                        }
                        return Map.entry(TransferStatus.INSUFFICIENT_FUNDS, "You (" + banker.getNation() + ") have hit your transfer limit ($" + MathMan.format(withdrawLimit) + ")");
                    }
                }
            }
        }
        if (DISABLE_TRANSFERS && (banker == null || banker.getNation_id() != Settings.INSTANCE.NATION_ID)) {
            return Map.entry(TransferStatus.AUTHORIZATION, "Error: Maintenance. Transfers are currently disabled");
        }

        long start = System.currentTimeMillis();

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
            User bankerUser = banker != null ? banker.getUser() : null;
            if (bankerUser != null) hasAdmin = Roles.ECON.has(bankerUser, offshoreDB.getGuild());

            // Ensure sufficient deposits
            Map<NationOrAllianceOrGuild, double[]> depositsByAA = getDepositsByAA(senderDB, allowedAlliances, true);
            double[] deposits = ResourceType.getBuffer();
            double[] finalDeposits = deposits;
            depositsByAA.forEach((a, b) -> ResourceType.add(finalDeposits, b));

            if (senderDB != offshoreDB) {
                deposits = PnwUtil.normalize(deposits); // normalize
                for (int i = 0; i < amount.length; i++) {
                    if (Math.round(amount[i] * 100) != 0 && Math.round(deposits[i] * 100) < Math.round(amount[i] * 100))
                        throw new IllegalArgumentException("You do not have " + MathMan.format(amount[i]) + "x" + ResourceType.values[i] + ", only " + MathMan.format(deposits[i]) + " (normalized)\n" +
                                "Note: Account balance is managed on the offshore server (" + getGuildDB().getGuild() + ") and can be adjusted via " + CM.deposits.add.cmd.toSlashMention());
                    if (!Double.isFinite(amount[i]) || Math.round(amount[i] * 100) < 0)
                        throw new IllegalArgumentException(amount[i] + " is not a valid positive amount");
                }
                if (deposits[ResourceType.CREDITS.ordinal()] != 0)
                    throw new IllegalArgumentException("You cannot transfer credits");

                disabledGuilds.put(senderDB.getGuild().getIdLong(), true);
            }

            Map<ResourceType, Double> transfer = PnwUtil.resourcesToMap(amount);

            long tx_datetime = System.currentTimeMillis();

            Map<NationOrAllianceOrGuild, double[]> addBalanceResult = null;
            String offshoreNote = "#deposit #receiver_id=" + receiver.getId() + " #receiver_type=" + receiver.getReceiverType();
            try {
                if (senderDB != offshoreDB) {
                    addBalanceResult = offshoreDB.subBalanceMulti(depositsByAA, tx_datetime, amount, banker != null ? banker.getNation_id() : 0, offshoreNote);

                    Map<NationOrAllianceOrGuild, double[]> newDeposits = getDepositsByAA(senderDB, allowedAlliances, false);
                    // Emsure addBalanceResult total matches amount (rounded to 2 decimal places)
                    double[] totalAddBalance = ResourceType.getBuffer();
                    addBalanceResult.forEach((a, b) -> ResourceType.add(totalAddBalance, b));
                    for (int i = 0; i < amount.length; i++) {
                        if (Math.round((totalAddBalance[i] - amount[i]) * 100) > 1)
                            throw new IllegalArgumentException("Error: Addbalance does not match (1) " + MathMan.format(totalAddBalance[i]) + " != " + MathMan.format(amount[i]));
                    }
                    // ensure the difference between depositsByAA and newDeposits match the addBalanceResult
                    for (int i = 0; i < amount.length; i++) {
                        if (Math.round(amount[i] * 100) == 0) continue; // skip if amount is 0 (no need to check)
                        double diff = 0;
                        for (Map.Entry<NationOrAllianceOrGuild, double[]> entry : depositsByAA.entrySet()) {
                            diff += entry.getValue()[i];
                        }
                        for (Map.Entry<NationOrAllianceOrGuild, double[]> entry : newDeposits.entrySet()) {
                            diff -= entry.getValue()[i];
                        }
                        if (Math.round((diff - totalAddBalance[i]) * 100) > 1)
                            throw new IllegalArgumentException("Error: Addbalance does not match (2) " + MathMan.format(diff) + " != " + MathMan.format(totalAddBalance[i]));
                    }



//                offshoreDB.addTransfer(tx_datetime, 0, 0, senderDB, banker.getNation_id(), offshoreNote, amount);
                }
            } catch (Throwable e) {
                e.printStackTrace();
                log(senderDB, banker, receiver, "Transfer error " + e.getMessage() + " | " + PnwUtil.resourcesToString(amount) + " | " + transfer);
                throw e;
            }

            Map.Entry<OffshoreInstance.TransferStatus, String> result = transferSafe(receiver, transfer, note);

            switch (result.getKey()) {
                case ALLIANCE_ACCESS:
                case APPLICANT:
                case INACTIVE:
                case BEIGE:
                case GRAY:
                case NOT_MEMBER:
                case INVALID_TOKEN:
                case GRANT_REQUIREMENT:
                case AUTHORIZATION:
                case CONFIRMATION:
                default:
                case OTHER:
                    log(senderDB, banker, receiver, "Unknown result: " + result + " | <@" + Settings.INSTANCE.ADMIN_USER_ID + ">");
                case SUCCESS:
                case ALLIANCE_BANK: {
                    {
                        StringBuilder resultMsg = new StringBuilder(result.getValue());
                        if (addBalanceResult != null) {
                            for (Map.Entry<NationOrAllianceOrGuild, double[]> entry : addBalanceResult.entrySet()) {
                                resultMsg.append("\nSubtracting " + PnwUtil.resourcesToString(entry.getValue()) + " from " + entry.getKey().getQualifiedName());
                            }
                        }
                        result = Map.entry(result.getKey(), resultMsg.toString());
                    }

                    boolean valid = senderDB == offshoreDB;
                    if (!valid && ((result.getKey() == OffshoreInstance.TransferStatus.SUCCESS || result.getKey() == OffshoreInstance.TransferStatus.ALLIANCE_BANK))) {
                        double[] newDeposits = getDeposits(senderDB, true);
                        for (ResourceType type : ResourceType.values) {
                            double amt = deposits[type.ordinal()];
                            if (Math.round(amt * 100) > Math.round(newDeposits[type.ordinal()]) * 100) valid = true;
                        }
                        if (!valid) {
                            sync(null, false);
                            for (ResourceType type : ResourceType.values) {
                                double amt = deposits[type.ordinal()];
                                if (amt > newDeposits[type.ordinal()]) valid = true;
                            }
                        }
                        log(senderDB, banker, receiver, "New Deposits: `" +  PnwUtil.resourcesToString(newDeposits) + ("`"));
                    } else {
                        valid = false;
                    }

                    if (valid) {
                        disabledGuilds.remove(senderDB.getIdLong());
                    } else {
                        if (senderDB != offshoreDB) {
                            String title = "Reimburse";
                            StringBuilder body = new StringBuilder();
                            body.append("`").append(result.getValue()).append("`\n");
                            for (Map.Entry<NationOrAllianceOrGuild, double[]> entry : addBalanceResult.entrySet()) {
                                NationOrAllianceOrGuild account = entry.getKey();
                                body.append("\n - `!addbalance " + account.getTypePrefix() + ":" + account.getId() + " " + PnwUtil.resourcesToString(entry.getValue()) + " #deposit");
                            }
                            body.append("\n<@" + Settings.INSTANCE.ADMIN_USER_ID + ">");
                            log(senderDB, banker, receiver, title + ": " + body.toString());
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
                case INVALID_NOTE:
                case NOTHING_WITHDRAWN:
                case INVALID_API_KEY:
                    disabledGuilds.remove(senderDB.getIdLong());
                    if (senderDB != offshoreDB) {
                        if (addBalanceResult != null) {
                            offshoreDB.subBalanceMulti(addBalanceResult, tx_datetime, banker != null ? banker.getNation_id() : 0, offshoreNote);
                        }
                    }
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
//            if (result.getKey() == TransferStatus.MULTI && nation.getPosition() > 1) {
//                DBAlliance alliance = nation.getAlliance(false);
//                GuildDB db = alliance == null ? null : alliance.getGuildDB();
//                if (db != null) {
//                    OffshoreInstance bank = alliance.getBank();
//                    StringBuilder response = new StringBuilder();
//                    if (bank.getGuildDB() != getGuildDB()) {
//                        result = transfer(alliance, transfer, "#ignore");
//
//                        response.append("\nSent to AA:" + alliance.getName() + "/" + alliance.getAlliance_id());
//                        response.append("\n" + result.getValue());
//                    } else {
//                        result = Map.entry(TransferStatus.SUCCESS, "Withdrawing funds using local account");
//                        response.append("\n" + result.getValue());
//                        System.out.println("Different DB " + bank.getGuildDB().getGuild() + " | " + db.getGuild());
//                    }
//                    if (result.getKey() == TransferStatus.SUCCESS) {
//                        response.append("\nTransferring to nation...");
//                        Auth auth = null;
//                        DBAlliancePosition position = nation.getAlliancePosition();
//                        if (nation.getPositionEnum().id >= Rank.MEMBER.id || position != null && position.hasPermission(AlliancePermission.WITHDRAW_BANK)) {
//                            try {
//                                Auth nationAuth = nation.getAuth(null);
//                                if (nationAuth != null) auth = nationAuth;
//                            } catch (IllegalArgumentException ignore) {}
//                        }
//                        try {
//                            result = bank.transfer(auth, nation, transfer, note);
//                        } catch (Throwable e) {
//                            e.printStackTrace();
//                        }
//                        if (result.getKey() != TransferStatus.SUCCESS) {
//                            result = new AbstractMap.SimpleEntry<>(TransferStatus.SUCCESS, result.getValue());
//                        }
//                        response.append("\n" + result.getValue());
//                    }
//                    result = Map.entry(result.getKey(), response.toString());
//                }
//            }
            return result;
        }
    }

    public Map.Entry<TransferStatus, String> transfer(DBNation nation, Map<ResourceType, Double> transfer, String note) {
        synchronized (BANK_LOCK) {
            return transfer(null, nation, transfer, note);
        }
    }

    public Map.Entry<TransferStatus, String> transfer(Auth auth, DBNation nation, Map<ResourceType, Double> transfer, String note) {
        if (!TimeUtil.checkTurnChange()) return Map.entry(TransferStatus.TURN_CHANGE, "You cannot transfer close to turn change");
        if (DISABLE_TRANSFERS) throw new IllegalArgumentException("Error: Maintenance");
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

            MessageChannel logChannel = getGuildDB().getResourceChannel(0);
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
        if (!TimeUtil.checkTurnChange()) return Map.entry(TransferStatus.TURN_CHANGE, TransferStatus.TURN_CHANGE.msg);
        if (receiver.isAlliance()) {
            DBAlliance alliance = receiver.asAlliance();
            if (alliance.getNations(true, (int) TimeUnit.DAYS.toMinutes(30), true).isEmpty()) {
                return Map.entry(TransferStatus.VACATION_MODE, "The alliance: " + receiver.getQualifiedName() + " has no active members (> 30 days)");
            }
        } else if (receiver.isNation()) {
            DBNation nation = receiver.asNation();
            if (nation.getVm_turns() > 0) {
                return Map.entry(TransferStatus.VACATION_MODE, TransferStatus.VACATION_MODE.msg);
            }
            if (nation.active_m() > TimeUnit.DAYS.toMinutes(30)) {
                return Map.entry(TransferStatus.VACATION_MODE, "Nation is inactive (>30 days)");
            }
        }

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
                return Map.entry(TransferStatus.OTHER, "Timeout: " + e.getMessage());
            }
        }

        if (auth == null || !auth.isValid() || true) {
            // get api
            try {
                PoliticsAndWarV3 api = getAlliance().getApiOrThrow(AlliancePermission.WITHDRAW_BANK);
                Bankrec result = api.transferFromBank(PnwUtil.resourcesToArray(transfer), receiver, note);
                System.out.println("Success " + result.toString());
                String msg = PnwUtil.resourcesToString(transfer) + " | " + note + " -> " + receiver.getTypePrefix() + ":" + receiver.getName();
                return Map.entry(TransferStatus.SUCCESS, msg);
            } catch (HttpClientErrorException.Unauthorized e) {
                return Map.entry(TransferStatus.INVALID_DESTINATION, "Invalid API key");

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
        if (alliance.getAlliance_id() == allianceId) return Map.entry(TransferStatus.INVALID_DESTINATION, "You can't send funds to yourself");
        if (!TimeUtil.checkTurnChange()) return Map.entry(TransferStatus.TURN_CHANGE, "You cannot transfer close to turn change");
        if (DISABLE_TRANSFERS) throw new IllegalArgumentException("Error: Maintenance");
        if (!alliance.exists()) {
            return Map.entry(TransferStatus.INVALID_DESTINATION, "The alliance does not exist");
        }
        if (alliance.getNations(true, 10000, true).isEmpty()) {
            return Map.entry(TransferStatus.INVALID_DESTINATION, "The alliance has no members");
        }
        synchronized (BANK_LOCK) {
            Map.Entry<TransferStatus, String> result = transferUnsafe(null, alliance, transfer, note);
            String msg = "`" + PnwUtil.resourcesToString(transfer) + "` -> " + alliance.getUrl() + "\n**" + result.getKey() + "**: " + result.getValue();

            MessageChannel logChannel = getGuildDB().getResourceChannel(0);
            if (logChannel != null) {
                RateLimitUtil.queue(logChannel.sendMessage(msg));
            }
            return result;
        }
    }

    public double[] getDeposits(GuildDB guildDb) {
        return getDeposits(guildDb, true);
    }

    public Map<NationOrAllianceOrGuild, double[]> getDepositsByAA(GuildDB guildDb, Predicate<Integer> allowedAlliances, boolean update) {
        Map<NationOrAllianceOrGuild, double[]> result = new LinkedHashMap<>();

        GuildDB delegate = guildDb.getDelegateServer();
        if (delegate != null) {
            guildDb = delegate;
        }
        if (guildDb.getOffshore() != this) return result;


        Set<Integer> ids = guildDb.getAllianceIds();

        if (!ids.isEmpty()) {
            boolean allowedAny = false;
            for (int id : ids) {
                if (!allowedAlliances.test(id)) {
                    continue;
                }
                allowedAny = true;
                double[] rss = PnwUtil.resourcesToArray(getDepositsAA(ids, update));
                update = false;
                result.put(DBAlliance.getOrCreate(id), rss);
            }
            if (!allowedAny) {
                throw new IllegalArgumentException("Not allowed to withdraw funds from any alliance: " + StringMan.getString(ids));
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
        Map<NationOrAllianceOrGuild, double[]> byAA = getDepositsByAA(guildDb, f -> true, update);
        double[] deposits = ResourceType.getBuffer();
        byAA.forEach((a, b) -> ResourceType.add(deposits, b));
        return deposits;
    }

    public DBAlliance getAlliance() {
        return Locutus.imp().getNationDB().getAlliance(allianceId);
    }

    public enum TransferStatus {
        SUCCESS("You successfully transferred funds from the alliance bank."),
        BLOCKADE("You can't withdraw resources to a blockaded nation."),
        MULTI("This player has been flagged for using the same network as you."),
        TURN_CHANGE("You cannot transfer close to turn change. (DC = 10m, TC = 1m)"),
        INSUFFICIENT_FUNDS("Insufficient funds"),
        INVALID_DESTINATION("You did not enter a valid recipient name."),
        OTHER("Unspecified error. (Is it turn change? Is there a captcha?)"),
        ALLIANCE_BANK("Tranferring via alliance bank"),
        VACATION_MODE("You can't send funds to this nation because they are inactive or in Vacation Mode"),
        NOTHING_WITHDRAWN("You did not withdraw anything."),

        INVALID_API_KEY("The API key you provided does not allow whitelisted access."),

        ALLIANCE_ACCESS("Has disabled alliance access to resource information (account page)"),

        APPLICANT("is an applicant"),

        INACTIVE("Is inactive"),

        BEIGE("Is beige"),

        GRAY("Is gray"),

        NOT_MEMBER("is not a member of the alliance"),

        INVALID_NOTE("You did not enter a valid note."),

        INVALID_TOKEN("Invalid token"),

        GRANT_REQUIREMENT("Failed grant requirement"),

        AUTHORIZATION("You are not authorized to make that request"),

        CONFIRMATION("You must confirm the transfer"),
        ;

        private final String msg;

        TransferStatus(String msg) {
            this.msg = msg;
        }

        public String getMessage() {
            return msg;
        }
    }

    /**
     *         boolean whitelistedError = msg.contains("The API key you provided does not allow whitelisted access.");
     *         if (whitelistedError || msg.contains("The API key you provided is not valid.")) {
     *             String[] keys = getGuildDB().getOrNull(GuildDB.Key.API_KEY);
     *             if (keys == null) {
     *                 msg += "\nEnsure " + GuildDB.Key.API_KEY + " is set: " + CM.settings.cmd.toSlashMention();
     *             } else {
     *                 Integer nation = Locutus.imp().getDiscordDB().getNationFromApiKey(keys[0]);
     *                 if (nation == null) {
     *                     msg += "\nEnsure " + GuildDB.Key.API_KEY + " is set: " + CM.settings.cmd.toSlashMention() + " to a valid key in the alliance (with bank access)";
     *                 } else {
     *                     msg += "\nEnsure " + PnwUtil.getNationUrl(nation) + " is a valid nation in the alliance with bank access in " + allianceId;
     *                 }
     *             }
     *             if (whitelistedError) {
     *                 msg += "\nEnsure Whitelisted access is enabled in " + Settings.INSTANCE.PNW_URL() + "/account";
     *             }
     *             return Map.entry(TransferStatus.INVALID_API_KEY, msg);
     *         }
     *         if (msg.contains("You need provide the X-Bot-Key header with the key for a verified bot to use this endpoint.")) {
     *             return Map.entry(TransferStatus.INVALID_API_KEY, msg);
     *         }
     *         if (msg.isEmpty()) msg = "Unknown Error (Captcha?)";
     *         return Map.entry(TransferStatus.OTHER, msg);
     * @param task
     * @return
     */

    private Map.Entry<TransferStatus, String> categorize(BankWithTask task) {
        try {
            String msg = task.call();
            return categorize(msg);
        } catch (IndexOutOfBoundsException e) {
            if (new Date().getMinutes() <= 1) {
                return Map.entry(TransferStatus.OTHER, "Resources cannot be transferred during turn change");
            }
            e.printStackTrace();
            return Map.entry(TransferStatus.OTHER, "Unspecified authentication error.");
        }
    }

    private Map.Entry<TransferStatus, String> categorize(String msg) {
        if (msg.contains("You successfully transferred funds from the alliance bank.")) {
            return Map.entry(TransferStatus.SUCCESS, msg);
        }
        if (msg.contains("You can't send funds to this nation because they are in Vacation Mode") || msg.contains("You can't withdraw resources to a nation in vacation mode")) {
            return Map.entry(TransferStatus.VACATION_MODE, msg);
        }
        if (msg.contains("There was an Error with your Alliance Bank Withdrawal: You can't withdraw funds to that nation because they are under a naval blockade. When the naval blockade ends they will be able to receive funds.")
        || msg.contains("You can't withdraw resources to a blockaded nation.")) {
            return Map.entry(TransferStatus.BLOCKADE, msg);
        }
        if (msg.contains("This player has been flagged for using the same network as you.")) {
            return Map.entry(TransferStatus.MULTI, msg);
        }
        if (msg.contains("Insufficient funds") || msg.contains("You don't have that much") || msg.contains("You don't have enough resources.")) {
            return Map.entry(TransferStatus.INSUFFICIENT_FUNDS, msg);
        }
        if (msg.contains("You did not enter a valid recipient name.")) {
            return Map.entry(TransferStatus.INVALID_DESTINATION, msg);
        }
        if (msg.contains("You did not withdraw anything.") || msg.contains("You can't withdraw no resources.")) {
            return Map.entry(TransferStatus.NOTHING_WITHDRAWN, msg);
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
            return Map.entry(TransferStatus.INVALID_API_KEY, msg);
        }
        if (msg.contains("You need provide the X-Bot-Key header with the key for a verified bot to use this endpoint.")) {
            return Map.entry(TransferStatus.INVALID_API_KEY, msg);
        }
        if (msg.isEmpty()) msg = "Unknown Error (Captcha?)";
        return Map.entry(TransferStatus.OTHER, msg);
    }
}
