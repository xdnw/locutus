
package link.locutus.discord.util.offshore;

import com.google.common.base.Predicates;
import com.politicsandwar.graphql.model.Bankrec;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongDoubleImmutablePair;
import it.unimi.dsi.fastutil.longs.LongDoublePair;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.util.scheduler.ThrowingSupplier;
import link.locutus.discord.web.jooby.WebRoot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class OffshoreInstance {
    public static final Object BANK_LOCK = new Object();
    public static final ConcurrentHashMap<Integer, Object> NATION_LOCKS = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<Integer, Boolean> FROZEN_ESCROW = new ConcurrentHashMap<>();

    public static String DISABLED_MESSAGE = "Disabled temporarily for maintenance. Please try again later or contact the bot developer if you need immediate assistance.";
    public static final boolean DISABLE_TRANSFERS = false;
    private final int allianceId;
    private final AtomicInteger transfersThisSession = new AtomicInteger();

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


    public Set<Integer> getOffshoreAAIds() {
        Set<Integer> result = new IntOpenHashSet();
        result.add(allianceId);
        GuildDB db = getGuildDB();
        if (db != null) {
            result.addAll(db.getCoalition(Coalition.OFFSHORE));
        }
        return result;
    }

    private static final AtomicBoolean outOfSync = new AtomicBoolean(false);
    public static void setOutOfSync() {
        outOfSync.set(true);
    }

    public synchronized boolean sync() {
        return sync(null);
    }

    public synchronized boolean sync(Long latest) {
        return sync(latest, true);
    }

    private double[] lastFunds2 = null;

    public synchronized boolean sync(Long latest, boolean checkLast) {
        Map<ResourceType, Double> stockpileMap = getAlliance().getStockpile(true);
        double[] stockpile = stockpileMap == null ? null : ResourceType.resourcesToArray(stockpileMap);
        if (stockpile == null) {
            throw new IllegalArgumentException("No stockpile found for " + PW.getMarkdownUrl(allianceId, true));
        }
        if (lastFunds2 != null && checkLast) {
            if (Arrays.equals(lastFunds2, stockpile)) {
                outOfSync.set(false);
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
        if (!PW.API.hasRecent500Error()) {
            try {
                PoliticsAndWarV3 api = getAlliance().getApi(AlliancePermission.VIEW_BANK);
                List<Bankrec> bankRecs = api.fetchAllianceBankRecs(allianceId, f -> {
                    f.or_id(List.of(allianceId));
                    f.rtype(List.of(2));
                    f.stype(List.of(2));
                    if (finalBankMetaI > 0) f.min_id(finalBankMetaI);
                });

                if (bankRecs.isEmpty()) {
                    if (ResourceType.isZero(stockpile)) {
                        throw new IllegalArgumentException("No bank records & stockpile found for " + allianceId);
                    }
                    if (getGuildDB().getOrNull(GuildKey.PUBLIC_OFFSHORING) == Boolean.TRUE) {
                        throw new IllegalArgumentException("No bank records found for " + allianceId + " | " + alliance.getId() + " | " + ResourceType.toString(stockpile));
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

                    Locutus.imp().runEventsAsync(events -> Locutus.imp().getBankDB().saveBankRecs(bankRecs, events));

                    if (!bankRecs.isEmpty()) {
                        // delete legacy transactions for alliance id after date
                        Locutus.imp().getBankDB().deleteLegacyAllianceTransactions(allianceId, minDate - 1000);

                        // set bank update timestamp
                        alliance.setMeta(AllianceMeta.BANK_UPDATE_INDEX, minDate);
                    }
                }
            } catch (Exception e) {
                if (!PW.API.is500Error(e)) throw e;
            }
        }
        outOfSync.set(false);
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
//        return PW.resourcesToMap(resources);
//    }

    public synchronized List<Transaction2> getTransactionsGuild(long guildId, boolean force, long start, long end) {
        if (force || outOfSync.get()) sync();

        List<Transaction2> transactions = new ObjectArrayList<>();
        transactions.addAll(getOffshoreTransactions(guildId, 3, start, end));

        GuildDB db = getGuildDB();
        List<Transaction2> offset = db.getDepositOffsetTransactions(guildId, start, end);
        transactions.addAll(offset);

        return transactions;
    }

    public synchronized Map<ResourceType, Double> getDeposits(long guildId, boolean force) {
        if (!getGuildDB().getCoalitionRaw(Coalition.OFFSHORING).contains(guildId)) {
            throw new IllegalArgumentException("Guild " + guildId + " is not offshoring with " + getGuildDB().getGuild());
        }
        List<Transaction2> toProcess = getTransactionsGuild(guildId, force, 0L, Long.MAX_VALUE);

        Map<ResourceType, Double> result = ResourceType.resourcesToMap(getTotal(getOffshoreAAIds(), toProcess, guildId, 3));
        return result;
    }

    public synchronized List<Transaction2> getTransactionsAA(int allianceId, boolean force, long start, long end) {
        return getTransactionsAA(Collections.singleton(allianceId), force, start, end);
    }

    public static Predicate<Transaction2> getFilter(long id, int type) {
        return new Predicate<Transaction2>() {
            @Override
            public boolean test(Transaction2 tx) {
                if (tx.sender_type == 3) {
                    if (tx.sender_id == id && tx.sender_type == type) {
                        return true;
                    }
                    return false;
                }
                if (tx.sender_type != 2) {
                    return false;
                } else if (tx.note == null || tx.note.isEmpty()) {
                    return tx.sender_id == id && tx.sender_type == type;
                }

                Map<DepositType, Object> parsed = tx.getNoteMap();
                if (!parsed.isEmpty()) {
                    if (parsed.containsKey(DepositType.IGNORE)) {
                        return false;
                    }
                    Object aaAccount = (Object) parsed.get(DepositType.ALLIANCE);
                    Object guildAccount = (Object) parsed.get(DepositType.GUILD);
                    if (aaAccount != null || guildAccount != null) {
                        if (aaAccount != null && guildAccount != null) {
                            return false;
                        }
                        Object account = type == 2 ? aaAccount : type == 3 ? guildAccount : null;
                        if (account instanceof Number n) {
                            if (account != null && n.longValue() == id) {
                                tx.sender_id = id;
                                tx.sender_type = type;
                                return true;
                            }
                            return false;
                        }
                        return false;
                    }

                }
                if (tx.sender_id == id && tx.sender_type == type) {
                    return true;
                }
                return false;
            }
        };
    }

    private List<Transaction2> getOffshoreTransactions(long id, int type, long start, long end) {
        Set<Integer> offshoreIds = getOffshoreAAIds();
        Predicate<Transaction2> filter = getFilter(id, type);
        if (start != 0) filter = filter.and(tx -> tx.tx_datetime >= start);
        if (end != Long.MAX_VALUE) filter = filter.and(tx -> tx.tx_datetime <= end);
        return Locutus.imp().getBankDB().getAllianceTransactions(offshoreIds, true, filter);
    }

    public synchronized List<Transaction2> getTransactionsAA(Set<Integer> allianceId, boolean force, long start, long end) {
        if (force || outOfSync.get()) sync();

        GuildDB db = getGuildDB();
        List<Transaction2> toProcess = new ObjectArrayList<>();
        for (int id : allianceId) {
            toProcess.addAll(getOffshoreTransactions((long) id, 2, start, end));
            toProcess.addAll(db.getDepositOffsetTransactions(-id, start, end));
        }
        return toProcess;
    }

    public synchronized Map<ResourceType, Double> getDeposits(int allianceId, boolean force) {
        return getDepositsAA(Collections.singleton(allianceId), force);
    }
    public synchronized Map<ResourceType, Double> getDepositsAA(Set<Integer> allianceIds, boolean force) {
        allianceIds = new ObjectLinkedOpenHashSet<>(allianceIds);
        Set<Integer> allowed = getGuildDB().getCoalition(Coalition.OFFSHORING);
        allianceIds.removeIf(f -> !allowed.contains(f));
        if (allianceIds.isEmpty()) return new HashMap<>();
        List<Transaction2> toProcess = getTransactionsAA(allianceIds, force, 0, Long.MAX_VALUE);
        Set<Long> allianceIdsLong = allianceIds.stream().map(Integer::longValue).collect(Collectors.toSet());
        double[] sum = getTotal(getOffshoreAAIds(), toProcess, allianceIdsLong, 2);
        return ResourceType.resourcesToMap(sum);
    }

    public boolean hasAccount(GuildDB db) {
        return getGuildDB().getCoalitionRaw(Coalition.OFFSHORING).contains(db.getIdLong());
    }

    public boolean hasAccount(DBAlliance alliance) {
        return getGuildDB().getCoalitionRaw(Coalition.OFFSHORING).contains(alliance.getIdLong());
    }

//    public List<Transaction2> filterTransactions(int allianceId)

    public static double[] getTotal(Set<Integer> offshoreAAs, List<Transaction2> transactions, Set<Long> ids, int type) {
        if (ids.size() == 1) {
            long id = ids.iterator().next();
            return getTotal(offshoreAAs, transactions, id, type);
        }
        double[] resources = ResourceType.getBuffer();
        for (Transaction2 transfer : transactions) {
            int sign;

            // transfer.sender_id == 0 && transfer.sender_type == 0 &&
            if ((ids.contains(transfer.sender_id) && transfer.sender_type == type) &&
                    ((transfer.tx_id == -1) || (transfer.receiver_type == 2 && offshoreAAs.contains((int) transfer.receiver_id)))) {
                sign = 1;
                // transfer.receiver_id == 0 && transfer.receiver_type == 0 &&
            } else if ((ids.contains(transfer.receiver_id) && transfer.receiver_type == type) &&
                    ((transfer.tx_id == -1) || (transfer.sender_type == 2 && offshoreAAs.contains((int) transfer.sender_id)))) {
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
    public static double[] getTotal(Set<Integer> offshoreAAs, List<Transaction2> transactions, long id, int type) {
        double[] resources = ResourceType.getBuffer();
        for (Transaction2 transfer : transactions) {
            int sign;

            // transfer.sender_id == 0 && transfer.sender_type == 0 &&
            if ((transfer.sender_id == id && transfer.sender_type == type) &&
                    ((transfer.tx_id == -1) || (transfer.receiver_type == 2 && offshoreAAs.contains((int) transfer.receiver_id)))) {
                sign = 1;
                // transfer.receiver_id == 0 && transfer.receiver_type == 0 &&
            } else if ((transfer.receiver_id == id && transfer.receiver_type == type) &&
                    ((transfer.tx_id == -1) || (transfer.sender_type == 2 && offshoreAAs.contains((int) transfer.sender_id)))) {
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

    public TransferResult transfer(DBNation nation, Map<ResourceType, Double> transfer, Map<DBAlliance, Map<ResourceType, Double>> transferRoute) {
        return transfer(nation, transfer, null, transferRoute);
    }

    public TransferResult transferSafe(NationOrAlliance nation, Map<ResourceType, Double> transfer, String note, Map<DBAlliance, Map<ResourceType, Double>> transferRoute) {
        if (DISABLE_TRANSFERS) throw new IllegalArgumentException(DISABLED_MESSAGE);
        synchronized (BANK_LOCK) {
            if (nation.isNation()) return transferSafe(nation.asNation(), transfer, note, transferRoute);
            return transfer(nation.asAlliance(), transfer, note, transferRoute);
        }
    }

    public Map<ResourceType, Double> getOffshoredBalance(DBAlliance alliance) {
        GuildDB db = alliance.getGuildDB();
        if (db == null) return new HashMap<>();
        if (db.getOffshore() != this) return new HashMap<>();
        if (alliance.getAlliance_id() == allianceId) {
            double[] result = ResourceType.getBuffer();
            Arrays.fill(result, Double.MAX_VALUE);
            return ResourceType.resourcesToMap(result);
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

    public final Map<Integer, Long> disabledNations = new ConcurrentHashMap<>();

    public TransferResult transferFromNationAccountWithRoleChecks(DBNation bankerNation, User banker, DBNation nationAccount, DBAlliance allianceAccount, TaxBracket tax_account, GuildDB senderDB, Long senderChannel, NationOrAlliance receiver, double[] amount, DepositType.DepositTypeInfo depositType, Long expire, Long decay, UUID grantToken, boolean convertCash, EscrowMode escrowMode, boolean requireConfirmation, boolean bypassChecks) throws IOException {
        Supplier<Map<Long, AccessType>> allowedIdsGet = ArrayUtil.memorize(new Supplier<Map<Long, AccessType>>() {
            @Override
            public Map<Long, AccessType> get() {
               return senderDB.getAllowedBankAccountsOrThrow(bankerNation, banker, receiver, senderChannel, senderChannel == null || Roles.ECON.has(banker, senderDB.getGuild()));
            }
        });
        return transferFromNationAccountWithRoleChecks(allowedIdsGet, banker, nationAccount, allianceAccount, tax_account, senderDB, senderChannel, receiver, amount, depositType, expire, decay, grantToken, convertCash, escrowMode, requireConfirmation, bypassChecks);
    }

    private final Map<Integer, double[]> lastSuccessfulTransfer = new ConcurrentHashMap<>();
    private void setLastSuccessfulTransfer(NationOrAlliance receiver, double[] resources) {
        int id = receiver.isAlliance() ? -receiver.asAlliance().getAlliance_id() : receiver.asNation().getNation_id();
        lastSuccessfulTransfer.put(id, resources);
    }

    private double[] getLastSuccessfulTransfer(NationOrAlliance receiver) {
        int id = receiver.isAlliance() ? -receiver.asAlliance().getAlliance_id() : receiver.asNation().getNation_id();
        return lastSuccessfulTransfer.get(id);
    }

    public boolean checkLastSuccessfulTransferMatches(NationOrAlliance receiver, double[] amount) {
        double[] previous = getLastSuccessfulTransfer(receiver);
        return previous != null && ResourceType.equals(previous, amount);
    }

    public TransferResult transferFromNationAccountWithRoleChecks(Supplier<Map<Long, AccessType>> allowedIdsGet, User banker, DBNation nationAccount, DBAlliance allianceAccount, TaxBracket tax_account, GuildDB senderDB, Long senderChannel, NationOrAlliance receiver, double[] amount, DepositType.DepositTypeInfo depositType, Long expire, Long decay, UUID grantToken, boolean convertCash, EscrowMode escrowMode, boolean requireConfirmation, boolean bypassChecks) throws IOException {
        if (expire != null && decay != null) {
            return new TransferResult(TransferStatus.INVALID_NOTE, receiver, amount, depositType.toString()).addMessage("You cannot set both `expire` and `decay`, please choose one");
        }
        if (!TimeUtil.checkTurnChange()) {
//            return KeyValue.of(TransferStatus.TURN_CHANGE, "You cannot transfer close to turn change");
            return new TransferResult(TransferStatus.TURN_CHANGE, receiver, amount, depositType.toString()).addMessage("You cannot transfer close to turn change");
        }

        if (escrowMode == EscrowMode.ALWAYS && !receiver.isNation()) {
            return new TransferResult(TransferStatus.INVALID_DESTINATION, receiver, amount, depositType.toString()).addMessage("You set `escrow_mode: ALWAYS` but the receiver is not a nation: " + receiver.getMarkdownUrl());
        }

        if (nationAccount != null) nationAccount = nationAccount.copy(); // Copy to avoid external mutation

        if (receiver.isAlliance() && !receiver.asAlliance().exists()) {
            return new TransferResult(TransferStatus.INVALID_DESTINATION, receiver, amount, depositType.toString()).addMessage("Alliance: " + receiver.getMarkdownUrl() + " has no receivable nations");
        }
        if (!receiver.isNation() && !depositType.isReservedOrIgnored() && nationAccount == null) {
            return new TransferResult(TransferStatus.INVALID_NOTE, receiver, amount, depositType.toString()).addMessage("Please use `" + DepositType.IGNORE + "` as the `depositType` when transferring to alliances");
        }

        boolean escrowSetting = GuildKey.MEMBER_CAN_ESCROW.getOrNull(senderDB) == Boolean.TRUE;
        boolean allowEscrow = escrowMode == EscrowMode.ALWAYS || (escrowMode == EscrowMode.WHEN_BLOCKADED && receiver.isNation());
        boolean escrowFunds = (allowEscrow || escrowSetting) && receiver.isNation() && receiver.asNation().isBlockaded();

        if (!bypassChecks && receiver.isNation()) {
            DBNation nation = receiver.asNation();
            if (nation.getVm_turns() > 0) {
//                return KeyValue.of(TransferStatus.VACATION_MODE, TransferStatus.VACATION_MODE.msg + " (set the `force` parameter to bypass)");
                return new TransferResult(TransferStatus.VACATION_MODE, receiver, amount, depositType.toString()).addMessage(TransferStatus.VACATION_MODE.msg + " (set the `bypassChecks` parameter to bypass)");
            }
            if (nation.isGray()) {
//                return KeyValue.of(TransferStatus.GRAY, TransferStatus.GRAY.msg + " (set the `force` parameter to bypass)");
                return new TransferResult(TransferStatus.GRAY, receiver, amount, depositType.toString()).addMessage(TransferStatus.GRAY.msg + " (set the `bypassChecks` parameter to bypass)");
            }
            if (!allowEscrow && nation.getNumWars() > 0 && nation.isBlockaded()) {
//                return KeyValue.of(TransferStatus.BLOCKADE, TransferStatus.BLOCKADE.msg + " (set the `force` parameter to bypass. set `escrow_mode` to escrow)");
                return new TransferResult(TransferStatus.BLOCKADE, receiver, amount, depositType.toString()).addMessage(TransferStatus.BLOCKADE.msg + " (set the `bypassChecks` parameter to bypass. set `escrow_mode` to escrow)");
            }
            if (nation.active_m() > 11520) {
//                return KeyValue.of(TransferStatus.INACTIVE, TransferStatus.INACTIVE.msg + " (set the `force` parameter to bypass)");
                return new TransferResult(TransferStatus.INACTIVE, receiver, amount, depositType.toString()).addMessage(TransferStatus.INACTIVE.msg + " (set the `bypassChecks` parameter to bypass)");
            }
        }

        Set<Grant.Requirement> failedRequirements = new HashSet<>();
        boolean isGrant = false;
        if (grantToken != null) {
            Grant authorized = null; // TODO grant request tokens;
            if (authorized == null) {
//                return KeyValue.of(TransferStatus.INVALID_TOKEN, "Invalid grant token (try again)");
                return new TransferResult(TransferStatus.INVALID_TOKEN, receiver, amount, depositType.toString()).addMessage("Invalid grant token (try again)");
            }
            if (!receiver.isNation()) {
//                return KeyValue.of(TransferStatus.INVALID_DESTINATION, "Grants can only be used to sent to nations");
                return new TransferResult(TransferStatus.INVALID_DESTINATION, receiver, amount, depositType.toString()).addMessage("Grants can only be used to sent to nations");
            }

            for (Grant.Requirement requirement : authorized.getRequirements()) {
                if (!requirement.apply(receiver.asNation())) {
                    failedRequirements.add(requirement);
                    if (requirement.canOverride()) continue;
                    else {
//                        return KeyValue.of(TransferStatus.GRANT_REQUIREMENT, requirement.getMessage());
                        return new TransferResult(TransferStatus.GRANT_REQUIREMENT, receiver, amount, depositType.toString()).addMessage(requirement.getMessage());
                    }
                }
            }
            isGrant = true;
        }

        StringBuilder reqMsg = new StringBuilder();
        if (!failedRequirements.isEmpty()) {
            reqMsg.append("The following grant requirements were not met: ");
            for (Grant.Requirement requirement : failedRequirements) {
                reqMsg.append("- " + requirement.getMessage() + "\n");
            }
            if (!bypassChecks) {
//                return KeyValue.of(TransferStatus.GRANT_REQUIREMENT, reqMsg + "\n(set the `force` parameter to bypass)");
                return new TransferResult(TransferStatus.GRANT_REQUIREMENT, receiver, amount, depositType.toString()).addMessage(reqMsg.toString()).addMessage("(set the `bypassChecks` parameter to bypass)");
            }
        }

        List<String> otherNotes = new ArrayList<>();

        if (expire != null && expire != 0) {
            if (!receiver.isNation()) {
                return new TransferResult(TransferStatus.INVALID_NOTE, receiver, amount, depositType.toString()).addMessage("Expire can only be used with nations");
            }
            if (expire < 1000) {
                return new TransferResult(TransferStatus.INVALID_NOTE, receiver, amount, depositType.toString()).addMessage("Expire time must be at least 1 second (e.g. `3d` for three days)");
            }
            otherNotes.add("#expire=" + TimeUtil.secToTime(TimeUnit.MILLISECONDS, expire));
        }

        if (decay != null && decay != 0) {
            if (!receiver.isNation()) {
                return new TransferResult(TransferStatus.INVALID_NOTE, receiver, amount, depositType.toString()).addMessage("Decay can only be used with nations");
            }
            if (decay < 1000) {
                return new TransferResult(TransferStatus.INVALID_NOTE, receiver, amount, depositType.toString()).addMessage("Decay time must be at least 1 second (e.g. `3d` for three days)");
            }
            otherNotes.add("#decay=" + TimeUtil.secToTime(TimeUnit.MILLISECONDS, decay));
        }

        DBNation bankerNation = DiscordUtil.getNation(banker);
        if (bankerNation == null) {
            return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, depositType.toString()).addMessage("No nation found for: `" + banker.getName() + "`. See: " + CM.register.cmd.toSlashMention());
        }

        Map<Long, AccessType> allowedIds;
        try {
            allowedIds = allowedIdsGet.get();
        } catch (IllegalArgumentException e) {
            return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, depositType.toString()).addMessage(e.getMessage());
        }
        if (allowedIds.isEmpty()) {
            return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, depositType.toString()).addMessage("You do not have permission to do a transfer (receiver: " + receiver.getMarkdownUrl() + ", channel: <#" + senderChannel + ">)");
        }

        if (convertCash) {
            if (!receiver.isNation()) {
                return new TransferResult(TransferStatus.INVALID_DESTINATION, receiver, amount, depositType.toString()).addMessage("Cash conversion is only for nations");
            }
            if (senderDB.getOrNull(GuildKey.RESOURCE_CONVERSION) != Boolean.TRUE) {
                return new TransferResult(TransferStatus.INVALID_NOTE, receiver, amount, depositType.toString()).addMessage("Missing role: " + Roles.ECON.toDiscordRoleNameElseInstructions(senderDB.getGuild()),
                        "Members do not have permission to convert resources to cash. See " + CM.settings.info.cmd.toSlashMention() + " with key: `" + GuildKey.RESOURCE_CONVERSION.name() + "`");
            }
            allowedIds.entrySet().removeIf(f -> f.getValue() != AccessType.ECON);
            if (allowedIds.isEmpty()) {
//                return KeyValue.of(TransferStatus.INVALID_NOTE, "You do not have permission to convert cash to resources. Missing role: " + Roles.ECON.toDiscordRoleNameElseInstructions(senderDB.getGuild()));
                return new TransferResult(TransferStatus.INVALID_NOTE, receiver, amount, depositType.toString()).addMessage("You do not have permission to convert cash to resources.", "Missing role: " + Roles.ECON.toDiscordRoleNameElseInstructions(senderDB.getGuild()));
            }
            otherNotes.add("#cash=" + MathMan.format(ResourceType.convertedTotal(amount)));
        }

        boolean hasAnyEcon = allowedIds.containsValue(AccessType.ECON);
        boolean isInternalTransfer = false;
        if (nationAccount == null) {
            if (!hasAnyEcon) {
                nationAccount = bankerNation.copy(); // Copy to avoid external mutation
            } else {
                allowedIds.entrySet().removeIf(f -> f.getValue() != AccessType.ECON);
            }
        } else if (nationAccount.getId() != bankerNation.getId()) {
            allowedIds.entrySet().removeIf(f -> f.getValue() != AccessType.ECON);
            if (allowedIds.isEmpty()) {
//                return KeyValue.of(TransferStatus.AUTHORIZATION, "You do not have permission to do a transfer for another nation's account using this channel");
                return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, depositType.toString()).addMessage("You do not have permission to do a transfer for another nation's account using this channel");
            }
        }

        if (allianceAccount != null) {
            if (!allowedIds.containsKey((long) allianceAccount.getId())) {
//                return KeyValue.of(TransferStatus.AUTHORIZATION, "You attempted to withdraw from the alliance account: " + allianceAccount.getId() + " but are only authorized for " + StringMan.getString(allowedIds) + " (did you use the correct channel?)");
                return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, depositType.toString()).addMessage("You attempted to withdraw from the alliance account: `" + allianceAccount.getId() + "` but are only authorized for `" + StringMan.getString(allowedIds) + "` (did you use the correct channel?)");
            }
            allowedIds.entrySet().removeIf(f -> f.getKey() != (long) allianceAccount.getId());
        }

        Map<Long, AccessType> allowedIdsCopy = new HashMap<>(allowedIds);

        Set<Integer> guildAllianceIds = senderDB.getAllianceIds();

        if ((expire != null && expire != 0) || (decay != null && decay != 0)) {
            allowedIds.entrySet().removeIf(f -> f.getValue() != AccessType.ECON);
            if (allowedIds.isEmpty()) {
//                return KeyValue.of(TransferStatus.AUTHORIZATION, "You are only authorized " + DepositType.DEPOSIT + " but attempted to do " + depositType);
                return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, depositType.toString()).addMessage("You are only authorized for the note `" + DepositType.DEPOSIT + "` but attempted to use `" + depositType + "`.");
            }
        }

        boolean rssConversion = senderDB.getOrNull(GuildKey.RESOURCE_CONVERSION) == Boolean.TRUE;
        if (rssConversion) {
            Role role = Roles.RESOURCE_CONVERSION.toRole2(senderDB);
            if (role != null) {
                rssConversion = false;
                Member member = senderDB.getGuild().getMember(banker);
                if (member != null) {
                    rssConversion = member.getUnsortedRoles().contains(role);
                }
            }
        }
        boolean allowNegative = senderDB.getOrNull(GuildKey.ALLOW_NEGATIVE_RESOURCES) == Boolean.TRUE && rssConversion;
        boolean ignoreGrants = senderDB.getOrNull(GuildKey.MEMBER_CAN_WITHDRAW_IGNORES_GRANTS) == Boolean.TRUE;
        double txValue = ResourceType.convertedTotal(amount);

        Object lockObj = requireConfirmation ? new Object() : BANK_LOCK;

        synchronized (lockObj) {
            double[] myDeposits;
            if (nationAccount != null) {
                if (disabledNations.containsKey(nationAccount.getId())) {
                    // Account temporarily disabled due to error. Use CM.bank.unlockTransfers.cmd.toSlashMention() to re-enable
//                    return KeyValue.of(TransferStatus.AUTHORIZATION, "Transfers are temporarily disabled for this account due to an error. Have a server admin use " + CM.bank.unlockTransfers.cmd.toSlashMention() + " to re-enable");
                    return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, depositType.toString()).addMessage("Transfers are temporarily disabled for this account due to an error.", "Have a server admin use " + CM.bank.unlockTransfers.cmd.nationOrAllianceOrGuild(nationAccount.getId() + "") + " in " + getGuild());
                }
                if (!depositType.isDeposits() || depositType.isReservedOrIgnored()) {
                    allowedIds.entrySet().removeIf(f -> f.getValue() != AccessType.ECON);
                    if (allowedIds.isEmpty()) {
//                        return KeyValue.of(TransferStatus.AUTHORIZATION, "You are only authorized for the note `" + DepositType.DEPOSIT + "` but attempted to do `" + depositType + "`");
                        return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, depositType.toString()).addMessage("You are only authorized for the note `" + DepositType.DEPOSIT + "` but attempted to do `" + depositType + "`");
                    }
                }

                if (nationAccount.getId() != bankerNation.getId()) {
                    allowedIds.entrySet().removeIf(f -> f.getValue() != AccessType.ECON);

                    if (!allowedIds.isEmpty() && !guildAllianceIds.isEmpty() && (banker == null || !Roles.ECON.has(banker, senderDB, nationAccount.getAlliance_id()))) {
//                        return KeyValue.of(TransferStatus.AUTHORIZATION, "You attempted to access a nation's account in alliance: " + nationAccount.getMarkdownUrl() + " but are only authorized for " + StringMan.getString(allowedIdsCopy));
                        return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, depositType.toString()).addMessage("You attempted to access a nation's account in alliance: " + nationAccount.getMarkdownUrl() + " but are only authorized for `" + StringMan.getString(allowedIdsCopy) + "`");
                    }
                }

                if (!receiver.isNation() || nationAccount.getNation_id() != receiver.asNation().getNation_id()) {
                    isInternalTransfer = true;
                    if (depositType.isReservedOrIgnored()) {
                        allowedIds.entrySet().removeIf(f -> f.getValue() != AccessType.ECON);
                        if (allowedIds.isEmpty()) {
//                        return KeyValue.of(TransferStatus.INVALID_NOTE, "Please use `" + DepositType.DEPOSIT + "` as the depositType when transferring to another nation");
                            return new TransferResult(TransferStatus.INVALID_NOTE, receiver, amount, depositType.toString()).addMessage("Please use `" + DepositType.DEPOSIT + "` as the depositType when transferring to another nation");
                        }
                    }
                }

                boolean hasEcon = allowedIds.containsValue(AccessType.ECON);
                if (!depositType.isIgnored() && (!hasEcon || requireConfirmation))
                {
                    boolean allowUpdate = !requireConfirmation;
                    boolean forceUpdate = !requireConfirmation && (Settings.USE_FALLBACK || Settings.INSTANCE.TASKS.BANK_RECORDS_INTERVAL_SECONDS <= 0);
                    Map.Entry<double[], TransferResult> result = checkNationDeposits(senderDB, nationAccount, allowedIds, receiver, amount, txValue, depositType, ignoreGrants, allowNegative, reqMsg, forceUpdate, allowUpdate);
                    if (result.getValue() != null) return result.getValue();
                    myDeposits = result.getKey();
                } else {
                    myDeposits = null;
                }
            } else {
                myDeposits = null;
            }

            if (tax_account != null) {
                int taxAAId = tax_account.getAlliance_id();
                if (taxAAId == 0) {
//                    return KeyValue.of(TransferStatus.INVALID_DESTINATION, "The tax bracket you specified either does not exist, or has no alliance: " + tax_account.getQualifiedName());
                    return new TransferResult(TransferStatus.INVALID_DESTINATION, receiver, amount, depositType.toString()).addMessage("The tax bracket you specified either does not exist, or has no alliance: " + tax_account.getQualifiedId());
                }
                AccessType aaAccess = allowedIds.get((long) taxAAId);
                if (aaAccess == null) {
//                    return KeyValue.of(TransferStatus.AUTHORIZATION, "You do not have permission to withdraw from the tax bracket: " + tax_account.getQualifiedName());
                    return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, depositType.toString()).addMessage("You do not have permission to withdraw from the tax bracket: " + tax_account.getQualifiedId());
                }
                if (aaAccess != AccessType.ECON) {
//                    return KeyValue.of(TransferStatus.AUTHORIZATION, "You only have member permissions and cannot withdraw from the tax bracket: " + tax_account.getQualifiedName());
                    return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, depositType.toString()).addMessage("You only have member permissions and cannot withdraw from the tax bracket: " + tax_account.getQualifiedId());
                }
                allowedIds.entrySet().removeIf(f -> f.getKey() != taxAAId);

                boolean hasEcon = allowedIds.containsValue(AccessType.ECON);
                if (!hasEcon || requireConfirmation) {
                    Map<DepositType, double[]> bracketDeposits = senderDB.getTaxBracketDeposits(tax_account.taxId, 0L, Long.MAX_VALUE, false, false);
                    double[] taxDeposits = bracketDeposits.get(DepositType.TAX);
                    double[] taxDepositsNormalized = PW.normalize(taxDeposits);
                    double taxDepoValue = ResourceType.convertedTotal(taxDepositsNormalized);
                    double[] depoArr = (taxDepoValue < txValue ? taxDepositsNormalized : taxDeposits);
                    double[] missing = null;
                    for (ResourceType type : ResourceType.values) {
                        if (amount[type.ordinal()] > 0 && Math.round(depoArr[type.ordinal()] * 100) < Math.round(amount[type.ordinal()] * 100)) {
                            if (missing == null) {
                                missing = ResourceType.getBuffer();
                            }
                            missing[type.ordinal()] = amount[type.ordinal()] - depoArr[type.ordinal()];
                        }
                    }
                    if (missing != null) {
                        if (!allowNegative) {
                            String[] msg = {tax_account.getQualifiedId() + " is missing `" + ResourceType.toString(missing) + "`. (see " +
                                    CM.deposits.check.cmd.nationOrAllianceOrGuild(tax_account.getQualifiedId()) +
                                    " ).", "ALLOW_NEGATIVE_RESOURCES is disabled (see " +
                                    GuildKey.ALLOW_NEGATIVE_RESOURCES.getCommandObj(senderDB, true) +
                                    ")"};
                            allowedIds.entrySet().removeIf(f -> f.getValue() != AccessType.ECON);
                            if (allowedIds.isEmpty()) {
//                                return KeyValue.of(TransferStatus.INSUFFICIENT_FUNDS, msg);
                                return new TransferResult(TransferStatus.INSUFFICIENT_FUNDS, receiver, amount, depositType.toString()).addMessage(StringMan.join(msg, "\n"));
                            }
                            reqMsg.append(msg + "\n");
                        } else if (taxDepoValue < txValue) {
                            String msg = tax_account.getQualifiedId() + "'s deposits are worth $" + MathMan.format(taxDepoValue) + "(market max) but you requested to withdraw $" + MathMan.format(txValue) + " worth of resources";
                            allowedIds.entrySet().removeIf(f -> f.getValue() != AccessType.ECON);
                            if (allowedIds.isEmpty()) {
//                                return KeyValue.of(TransferStatus.INSUFFICIENT_FUNDS, msg);
                                return new TransferResult(TransferStatus.INSUFFICIENT_FUNDS, receiver, amount, depositType.toString()).addMessage(msg);
                            }
                            reqMsg.append(msg + "\n");
                        }
                    }
                }
            }

            if (requireConfirmation) {
                StringBuilder body = new StringBuilder();
                if (checkLastSuccessfulTransferMatches(receiver, amount)) {
                    reqMsg.append("You have already transferred this exact amount to " + receiver.getQualifiedId() + "\n");
                }
                if (reqMsg.length() > 0) {
                    body.append("**!! ERRORS !!:**\n");
                    body.append("- " + reqMsg.toString().trim().replace("\n", "\n- "));
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
                            body.append("**Receiver is NOT registered with this Bot**\n");
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

                    body.append("**Receiver:** " + nation.getNationUrlMarkup() + " | " + nation.getAllianceUrlMarkup() + "\n");
                    if (escrowFunds) {
                        body.append("`Funds will be escrowed due to blockade`\n");
                    } else if (allowEscrow) {
                        body.append("`Funds will be escrowed if blockaded`\n");
                    }
                } else {
                    DBAlliance alliance = receiver.asAlliance();
                    if (alliance.getNations(f -> f.active_m() < 7200 && f.getPositionEnum().id >= Rank.HEIR.id && f.getVm_turns() == 0).isEmpty()) {
                        body.append("**Alliance has no active leaders/heirs (5d)**\n");
                    }
                    body.append("**Receiver AA:**" + alliance.getMarkdownUrl() + " (" + MathMan.format(alliance.getScore()) + "ns, #" + alliance.getRank() + ")\n");
                }
                body.append("**Amount:** `" + ResourceType.toString(amount) + "` worth: ~$" + MathMan.format(ResourceType.convertedTotal(amount)) + "\n");
                body.append("**Note:** `" + (depositType + " " + StringMan.join(otherNotes, " ")).trim().toLowerCase(Locale.ROOT) + "`\n");

                body.append("**Sender:**\n");
                if (banker != null) {
                    body.append("- Banker: " + bankerNation.getNationUrlMarkup() + "\n");
                }
                if (nationAccount != null) {
                    body.append("- Nation Account: " + nationAccount.getNationUrlMarkup() + "\n");
                } else if (depositType.isIgnored()) {
                    body.append("- Will NOT deduct from a nation deposits\n");
                } else {
                    body.append("- Will deduct from receiver's deposits\n");
                }
                if (allianceAccount != null) {
                    body.append("- Alliance Account: " + allianceAccount.getMarkdownUrl() + "\n");
                }
                if (senderDB != null) {
                    body.append("- Sender Guild: " + senderDB.getGuild().getName() + "\n");
                }
                if (tax_account != null) {
                    body.append("- Tax Account: " + tax_account.toString() + "\n");
                }
//                return KeyValue.of(TransferStatus.CONFIRMATION, body.toString());
                return new TransferResult(TransferStatus.CONFIRMATION, receiver, amount, depositType.toString()).addMessage(body.toString());
            }

            if (allowedIds.isEmpty()) {
                StringBuilder response = new StringBuilder();
                if (allowedIdsCopy.isEmpty()) {
                    response.append("You do not have permission to send directly from ANY alliance account using this channel (did you instead mean to do a personal withdrawal?)\n");
                } else {
                    response.append("You have permission to withdraw from the alliance accounts: " + StringMan.getString(allowedIdsCopy) + " but are not authorized to make this withdrawal (did you use the correct channel or account?)\n");
                }
//                return KeyValue.of(TransferStatus.AUTHORIZATION, response.toString());
                return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, depositType.toString()).addMessage(response.toString());
            }

            long primaryAccountId = getAccountId(allowedIds.keySet(), nationAccount, receiver);
            String note = depositType.toString(primaryAccountId);
            if (otherNotes.size() > 0) {
                note += " " + String.join(" ", otherNotes);
            }

            String ingameNote = isInternalTransfer ? "#" + DepositType.IGNORE.name().toLowerCase(Locale.ROOT) + "=" + primaryAccountId : note;

            long timestamp = System.currentTimeMillis();
            if (isInternalTransfer && !depositType.isIgnored()) {
                senderDB.subtractBalance(timestamp, nationAccount, bankerNation.getNation_id(), note, amount);
            }

            boolean hasEcon = allowedIds.containsValue(AccessType.ECON);

            DBNation finalNationAccount = nationAccount;
            Supplier<TransferResult> checkDisabled = (ThrowingSupplier<TransferResult>) () -> {
                double[] myNewDeposits = finalNationAccount.getNetDeposits(null, senderDB, !ignoreGrants, -1L, true);
                // ensure myDeposits and myNewDeposits difference is amount
                double[] diff = ResourceType.getBuffer();
                for (int i = 0; i < amount.length; i++) {
                    diff[i] = myDeposits[i] - myNewDeposits[i];
                }
                for (int i = 0; i < amount.length; i++) {
                    if (Math.round((diff[i] - amount[i]) * 100) > 1) {
                        disabledNations.put(finalNationAccount.getId(), System.currentTimeMillis());
                        String[] message = {"Internal error: " + ResourceType.toString(diff) + " != " + ResourceType.toString(amount),
                                "Nation Account: `" + finalNationAccount.getMarkdownUrl() + "` has been temporarily disabled. Have a guild admin use: " + CM.bank.unlockTransfers.cmd.toSlashMention()};
//                        return KeyValue.of(TransferStatus.OTHER, message);
                        return new TransferResult(TransferStatus.OTHER, receiver, amount, ingameNote).addMessage(message);
                    }
                }
                return null;
            };

            if (isInternalTransfer && !depositType.isIgnored() && (!hasEcon)) {
                TransferResult result = checkDisabled.get();
                if (result != null) return result;
            }

            if (tax_account != null) {
                double[] amountNegative = ResourceType.negative(amount.clone());
                if (receiver.isNation()) {
                    senderDB.addBalanceTaxId(timestamp, tax_account.getId(), receiver.getId(), bankerNation != null ? bankerNation.getNation_id() : 0, "#tax", amountNegative);
                } else {
                    senderDB.addBalanceTaxId(timestamp, tax_account.getId(), bankerNation != null ? bankerNation.getNation_id() : 0, "#tax", amountNegative);
                }

            }

            TransferResult result = null;
            if (!escrowFunds) {
                result = transferFromAllianceDeposits(bankerNation, senderDB, f -> allowedIds.containsKey(f.longValue()), receiver, amount, ingameNote);
            }
            if (escrowFunds || (result.getStatus() == TransferStatus.BLOCKADE && allowEscrow)) {
                // add escrow funds
                Object lock = NATION_LOCKS.computeIfAbsent(receiver.getId(), k -> new Object());
                synchronized (lock) {
                    Map.Entry<double[], Long> balanceOrNull = senderDB.getEscrowed(receiver.asNation());
                    double[] balance = balanceOrNull == null ? ResourceType.getBuffer() : balanceOrNull.getKey();
                    long escrowDate = balanceOrNull == null ? 0 : balanceOrNull.getValue();
                    for (int i = 0; i < amount.length; i++) {
                        balance[i] += amount[i];
                    }
                    if (!isInternalTransfer && !depositType.isIgnored()) {
                        if (nationAccount == null) nationAccount = receiver.asNation();
                        senderDB.subtractBalance(timestamp, nationAccount, bankerNation.getNation_id(), note, amount);
                    }
                    senderDB.setEscrowed(receiver.asNation(), balance, escrowDate);
                }
//                result = KeyValue.of(TransferStatus.SUCCESS, "Escrowed `" + PW.resourcesToString(amount) + "` for " + receiver.getName() + ". use " + CM.escrow.withdraw.cmd.toSlashMention() + " to withdraw.");
                result = new TransferResult(TransferStatus.ESCROWED, receiver, amount, ingameNote)
                        .addMessage("Escrowed `" + ResourceType.toString(amount) + "` for " + receiver.getMarkdownUrl(),
                        "Use " + CM.escrow.withdraw.cmd.toSlashMention() + " to withdraw.");
            }
            switch (result.getStatus()) {
                default: {
                    if (isInternalTransfer) {
                        senderDB.addBalance(timestamp, nationAccount, bankerNation != null ? bankerNation.getNation_id() : 0, note, amount);
                    }
                    if (tax_account != null) {
                        senderDB.addBalanceTaxId(timestamp, tax_account.getId(), bankerNation != null ? bankerNation.getNation_id() : 0, "#tax", amount);
                    }
                    break;
                }
                case ESCROWED:
                case SENT_TO_ALLIANCE_BANK:
                case SUCCESS: {
                    if (tax_account != null) {
                        result.addMessage("Deducted from tax account: " + tax_account.getQualifiedId());
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
    }

    public long getAccountId(Set<Long> allowedIds, DBNation nationAccount, NationOrAlliance receiver) {
        if (nationAccount != null && allowedIds.contains((long) nationAccount.getAlliance_id())) {
            return nationAccount.getAlliance_id();
        } else if (receiver.isNation() && allowedIds.contains((long) receiver.asNation().getAlliance_id())) {
            return receiver.asNation().getAlliance_id();
        } else {
            return allowedIds.iterator().next();
        }
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

    public TransferResult transferFromAllianceDeposits(DBNation banker, GuildDB senderDB, Predicate<Integer> allowedAlliances, NationOrAlliance receiver, double[] amount, String note) {
        if (banker != null) {
            note += " #banker=" + banker.getId();
            String denyReason = Settings.INSTANCE.MODERATION.BANNED_BANKERS.get((long) banker.getId());
            if (denyReason != null) {
                return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, note).addMessage("Access-Denied[Banker-Nation]: " + denyReason);
            }
            Long bankerUser = banker.getUserId();
            if (bankerUser != null) {
                denyReason = Settings.INSTANCE.MODERATION.BANNED_USERS.get(bankerUser);
                if (denyReason != null) {
                    return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, note).addMessage("Access-Denied[Banker-User]: " + denyReason);
                }
            }
            denyReason = Settings.INSTANCE.MODERATION.BANNED_NATIONS.get(banker.getId());
            if (denyReason != null) {
                return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, note).addMessage("Access-Denied[Banker]: " + denyReason);
            }
            if (receiver.isAlliance()) {
                DBAlliance receiverAlliance = receiver.asAlliance();
                denyReason = Settings.INSTANCE.MODERATION.BANNED_ALLIANCES.get(receiverAlliance.getId());
                if (denyReason != null) {
                    return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, note).addMessage("Access-Denied[Receiver-Ban]: " + denyReason);
                }
                GuildDB receiverGuild = receiverAlliance.getGuildDB();
                if (receiverGuild != null) {
                    denyReason = Settings.INSTANCE.MODERATION.BANNED_GUILDS.get(receiverGuild.getIdLong());
                    if (denyReason != null) {
                        return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, note).addMessage("Access-Denied[Receiver-Ban]: " + denyReason);
                    }
                }
            } else if (receiver.isNation()) {
                DBNation receiverNation = receiver.asNation();
                denyReason = Settings.INSTANCE.MODERATION.BANNED_NATIONS.get(receiverNation.getId());
                if (denyReason == null) denyReason = Settings.INSTANCE.MODERATION.BANNED_BANKERS.get((long) receiverNation.getId());
                if (denyReason != null) {
                    return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, note).addMessage("Access-Denied[Receiver-Ban]: " + denyReason);
                }
                Long userId = receiverNation.getUserId();
                if (userId != null) {
                    denyReason = Settings.INSTANCE.MODERATION.BANNED_USERS.get(userId);
                    if (denyReason == null) denyReason = Settings.INSTANCE.MODERATION.BANNED_BANKERS.get(userId);
                    if (denyReason != null) {
                        return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, note).addMessage("Access-Denied[Receiver-Ban]: " + denyReason);
                    }
                }
            }
        }
        if (note != null) {
            String noteLower = note.toLowerCase(Locale.ROOT);
            if (noteLower.contains("#alliance") || noteLower.contains("#guild") || noteLower.contains("#account")) {
//            return KeyValue.of(TransferStatus.INVALID_NOTE, "You cannot send with #alliance #guild or #account as the note");
                return new TransferResult(TransferStatus.INVALID_NOTE, receiver, amount, note).addMessage("You cannot send with `#alliance`, `#guild` or `#account` as the note");
            }
        }

        if (receiver.isAlliance() && !receiver.asAlliance().exists()) {
//            return KeyValue.of(TransferStatus.INVALID_DESTINATION, "Alliance: " + receiver.getUrl() + " has no receivable nations");
            return new TransferResult(TransferStatus.INVALID_DESTINATION, receiver, amount, note).addMessage("Alliance: " + receiver.getMarkdownUrl() + " has no receivable nations");
        }
        if (receiver.isAlliance() && !note.contains("#ignore")) {
//            return KeyValue.of(TransferStatus.INVALID_NOTE, "You must include #ignore in the note to send to an alliance");
            return new TransferResult(TransferStatus.INVALID_NOTE, receiver, amount, note).addMessage("You must include `#ignore` in the note to send to an alliance");
        }

        GuildDB delegate = senderDB.getDelegateServer();
        if (delegate != null) senderDB = delegate;

        boolean hasAmount = false;
        for (double amt : amount) if (amt >= 0.01) hasAmount = true;
        for (ResourceType type : ResourceType.values) if (amount[type.ordinal()] < 0) {
//            return KeyValue.of(TransferStatus.NOTHING_WITHDRAWN, "You cannot withdraw negative " + type);
            return new TransferResult(TransferStatus.NOTHING_WITHDRAWN, receiver, amount, note).addMessage("You cannot withdraw negative " + type);
        }
        if (!hasAmount) {
//            return KeyValue.of(TransferStatus.NOTHING_WITHDRAWN, "You did not withdraw anything.");
            return new TransferResult(TransferStatus.NOTHING_WITHDRAWN, receiver, amount, note).addMessage("You did not withdraw anything.");
        }

        if (DISABLE_TRANSFERS && (banker == null || banker.getNation_id() != Locutus.loader().getNationId())) {
//            return KeyValue.of(TransferStatus.AUTHORIZATION, "Error: Maintenance. Transfers are currently disabled");
            return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, note).addMessage(DISABLED_MESSAGE);
        }

        synchronized (BANK_LOCK) {
            if (banker != null) {
                boolean isAdmin = false;
                User user = banker.getUser();
                if (user != null) {
                    isAdmin = Roles.ADMIN.has(user, senderDB.getGuild());
                }
                if (!isAdmin) {
                    TransferResult limit = checkLimit(senderDB, banker, receiver, amount, note, null);
                    if (limit != null) return limit;
                }
            }

            if (ResourceType.isZero(amount)) {
                return new TransferResult(TransferStatus.NOTHING_WITHDRAWN, receiver, amount, note).addMessage("No funds need to be sent");
            }

            if (!senderDB.isAllianceId(allianceId) && senderDB.getOffshore() != this) {
                return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, note).addMessage("Sender does not have " + PW.getMarkdownUrl(allianceId, true) + " as an offshore");
            }
            GuildDB offshoreDB = getGuildDB();
            if (offshoreDB == null) {
                return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, note).addMessage("No guild is registered with this offshore");
            }

            if (isDisabled(senderDB.getIdLong())) {
                return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, note).addMessage("There was an error transferring funds (failed to fetch bank stockpile). Please have an admin use " + CM.offshore.unlockTransfers.cmd.nationOrAllianceOrGuild(senderDB.getIdLong() + "") + " in the offshore server (" + getGuild() + ")");
            }

            boolean hasAdmin = false;
            User bankerUser = banker != null ? banker.getUser() : null;
            if (bankerUser != null) hasAdmin = Roles.ECON.has(bankerUser, offshoreDB.getGuild());

            // Ensure sufficient deposits
            Map.Entry<Map<NationOrAllianceOrGuild, double[]>, double[]> depositsEntry;
            try {
                depositsEntry = checkDeposits(senderDB, allowedAlliances, amount, false);
            } catch (IllegalArgumentException e) {
                return new TransferResult(TransferStatus.INSUFFICIENT_FUNDS, receiver, amount, note).addMessage(e.getMessage());
            }
            Map<NationOrAllianceOrGuild, double[]> depositsByAA = depositsEntry.getKey();
            double[] deposits = depositsEntry.getValue();

            if (senderDB != offshoreDB) {
                disabledGuilds.put(senderDB.getGuild().getIdLong(), true);
            }

            Map<ResourceType, Double> transfer = ResourceType.resourcesToMap(amount);

            long tx_datetime = System.currentTimeMillis();

            boolean route = GuildKey.ROUTE_ALLIANCE_BANK.getOrNull(senderDB) == Boolean.TRUE && senderDB != getGuildDB() && (!receiver.isAlliance() || !senderDB.isAllianceId(receiver.getId()));
            if (route) {
                boolean hasNonAlliance = depositsByAA.keySet().stream().anyMatch(n -> !n.isAlliance());
                if (hasNonAlliance || depositsByAA.isEmpty()) {
                    return new TransferResult(TransferStatus.AUTHORIZATION, receiver, amount, note).addMessage("`" + GuildKey.ROUTE_ALLIANCE_BANK.name() + "` is enabled, but this server is not registered to an alliance. Disable with " + CM.settings.delete.cmd.key(GuildKey.ROUTE_ALLIANCE_BANK.name()));
                }
            }

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
                }
            } catch (Throwable e) {
                e.printStackTrace();
                log(senderDB, banker, receiver, "Transfer error " + e.getMessage() + " | " + ResourceType.toString(amount) + " | " + transfer);
                throw e;
            }

            Map<DBAlliance, Map<ResourceType, Double>> transferRoute = null;
            if (route) {
                transferRoute = new LinkedHashMap<>();
                for (Map.Entry<NationOrAllianceOrGuild, double[]> entry : addBalanceResult.entrySet()) {
                    if (entry.getKey().isAlliance()) {
                        DBAlliance alliance = (DBAlliance) entry.getKey();
                        transferRoute.put(alliance, ResourceType.resourcesToMap(entry.getValue()));
                    }
                }
            }

            TransferResult result = transferSafe(receiver, transfer, note, transferRoute);

            switch (result.getStatus()) {
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
                    log(senderDB, banker, receiver, "Unknown result: " + result.toLineString() + " | <@" + Locutus.loader().getAdminUserId() + ">");
                case ESCROWED:
                case SUCCESS:
                case SENT_TO_ALLIANCE_BANK: {
                    {
                        if (addBalanceResult != null) {
                            for (Map.Entry<NationOrAllianceOrGuild, double[]> entry : addBalanceResult.entrySet()) {
                                result.addMessage("Subtracting " + ResourceType.toString(entry.getValue()) + " from " + entry.getKey().getQualifiedId());
                            }
                        }
                    }

                    boolean valid = senderDB == offshoreDB;
                    if (!valid && ((result.getStatus().isSuccess()))) {
                        double[] newDeposits = getDeposits(senderDB, false);
                        for (ResourceType type : ResourceType.values) {
                            double amt = deposits[type.ordinal()];
                            if (Math.round(amt * 100) > Math.round(newDeposits[type.ordinal()]) * 100) valid = true;
                        }
                        if (!valid) {
                            for (ResourceType type : ResourceType.values) {
                                double amt = deposits[type.ordinal()];
                                if (amt > newDeposits[type.ordinal()]) valid = true;
                            }
                        }
                        log(senderDB, banker, receiver, "New Deposits: `" +  ResourceType.toString(newDeposits) + ("`"));
                    } else {
                        valid = false;
                    }

                    if (valid) {
                        disabledGuilds.remove(senderDB.getIdLong());
                    } else {
                        if (senderDB != offshoreDB) {
                            String title = "Reimburse";
                            StringBuilder body = new StringBuilder();
                            body.append("`").append(result.getMessageJoined(true)).append("`\n");
                            for (Map.Entry<NationOrAllianceOrGuild, double[]> entry : addBalanceResult.entrySet()) {
                                NationOrAllianceOrGuild account = entry.getKey();
                                body.append("\n- `!addbalance " + account.getTypePrefix() + ":" + account.getIdLong() + " " + ResourceType.toString(entry.getValue()) + " #deposit`");
                            }
                            body.append("\n<@" + Locutus.loader().getAdminUserId() + ">");
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

    private final Map<Integer, List<LongDoublePair>> CURR_TURN_VALUE_BY_NATION = new Int2ObjectOpenHashMap<>();

    public void addInternalTransfer(int nationId, double value) {
        synchronized (CURR_TURN_VALUE_BY_NATION) {
            CURR_TURN_VALUE_BY_NATION.computeIfAbsent(nationId, k -> new ObjectArrayList<>()).add(LongDoubleImmutablePair.of(System.currentTimeMillis(), value));
        }
    }

    private double getInternalLimitUsed(int nationId, long cutoff) {
        // Remove the elements that are before the cutoff for the user, and then return the remainder
        synchronized (CURR_TURN_VALUE_BY_NATION) {
            List<LongDoublePair> list = CURR_TURN_VALUE_BY_NATION.get(nationId);
            if (list == null) return 0;
            double total = 0;
            int indexRemove = -1;
            for (int i = 0; i < list.size(); i++) {
                LongDoublePair item = list.get(i);
                if (item.keyLong() < cutoff) {
                    indexRemove = i;
                } else {
                    total += item.valueDouble();
                }
            }
            if (indexRemove != -1) {
                for (int i = indexRemove; i >= 0; i--) {
                    list.remove(i);
                }
            }
            return total;
        }
    }

    public TransferResult checkLimit(GuildDB senderDB, DBNation banker, NationOrAlliance receiver, double[] amount, String note, Double limitDefault) {
        Double withdrawLimit = senderDB.getHandler().getWithdrawLimit(banker.getNation_id());
        if (withdrawLimit == null) withdrawLimit = limitDefault;
        if (withdrawLimit != null && withdrawLimit < Long.MAX_VALUE) {
            long cutoff = System.currentTimeMillis();
            Long interval = senderDB.getOrNull(GuildKey.BANKER_WITHDRAW_LIMIT_INTERVAL);
            if (interval != null) {
                cutoff -= interval;
            } else {
                cutoff -= TimeUnit.DAYS.toMillis(1);
            }

            String append = "#banker=" + banker.getNation_id();
            // getBankTransactionsWithNote
            if (note == null || note.isEmpty()) {
                note = append;
            } else {
                note += " " + append;
            }

            List<Transaction2> transactions = Locutus.imp().getBankDB().getTransactionsByNote(append, cutoff);
            Set<Integer> offshoreAAIds2 = getOffshoreAAIds();
            Set<Integer> aaIds = senderDB.getAllianceIds();
            double initialValue = ResourceType.convertedTotal(amount);
            double total = initialValue;

            for (Transaction2 transaction : transactions) {
                if (!transaction.isTrackedForGuild(senderDB, aaIds, offshoreAAIds2)) continue;
                total += transaction.convertedTotal();
            }

            double valueUsed = getInternalLimitUsed(banker.getNation_id(), cutoff);
            total += valueUsed;

            if (total > withdrawLimit) {
                MessageChannel alertChannel = senderDB.getOrNull(GuildKey.WITHDRAW_ALERT_CHANNEL);
                if (alertChannel != null) {
                    StringBuilder body = new StringBuilder();
                    body.append(banker.getNationUrlMarkup() + " | " + banker.getAllianceUrlMarkup()).append("\n");
                    body.append("Transfer: " + ResourceType.toString(amount) + " worth `$" + MathMan.format(initialValue) + "` | " + note + " | " + (receiver != null ? "to:" + receiver.getTypePrefix() + receiver.getName() : ""));
                    body.append("Your limit is `$" + MathMan.format(withdrawLimit) + "` (worth of $/rss)\n\n");
                    body.append("You have used $" + MathMan.format(total) + " in the last " + (interval != null ? TimeUtil.secToTime(TimeUnit.MILLISECONDS, interval) : "24 hours") + "\n");
                    body.append("To set the limit for a user: " + CM.bank.limits.setTransferLimit.cmd.toSlashMention() + "\n");
                    body.append("To set the default " + GuildKey.BANKER_WITHDRAW_LIMIT.getCommandMention());

                    Role adminRole = Roles.ADMIN.toRole2(senderDB.getGuild());

                    RateLimitUtil.queueMessage(new DiscordChannelIO(alertChannel), msg -> {
                        msg.embed("Banker withdraw limit exceeded", body.toString());
                        if (adminRole != null) {
                            msg.append(("^ " + adminRole.getAsMention()));
                        }
                        return true;
                    }, true, null);
                }
//                            return KeyValue.of(TransferStatus.INSUFFICIENT_FUNDS, "You (" + banker.getNation() + ") have hit your transfer limit ($" + MathMan.format(withdrawLimit) + ")");
                return new TransferResult(TransferStatus.INSUFFICIENT_FUNDS, receiver, amount, note)
                        .addMessage("You (" + banker.getMarkdownUrl() + ") have hit your transfer limit: `$" + MathMan.format(withdrawLimit) + "`");
            }
        }
        return null;
    }

    private Map.Entry<double[], TransferResult> checkNationDeposits(GuildDB senderDB, DBNation nationAccount, Map<Long, AccessType> allowedIds, NationOrAlliance receiver, double[] amount, double txValue, DepositType.DepositTypeInfo depositType, boolean ignoreGrants, boolean allowNegative, StringBuilder reqMsg, boolean update, boolean allowUpdate) throws IOException {
        double[] myDeposits = nationAccount.getNetDeposits(null, senderDB, !ignoreGrants, update ? 0L : -1, true);
        double[] myDepositsNormalized = PW.normalize(myDeposits);
        double myDepoValue = ResourceType.convertedTotal(myDepositsNormalized, false);
        double[] depoArr = (myDepoValue < txValue ? myDepositsNormalized : myDeposits);
        double[] missing = null;
        for (ResourceType type : ResourceType.values) {
            if (amount[type.ordinal()] > 0 && Math.round(depoArr[type.ordinal()] * 100) < Math.round(amount[type.ordinal()] * 100)) {
                if (!update && allowUpdate) {
                    return checkNationDeposits(senderDB, nationAccount, allowedIds, receiver, amount, txValue, depositType, ignoreGrants, allowNegative, reqMsg, true, false);
                }
                if (missing == null) {
                    missing = ResourceType.getBuffer();
                }
                missing[type.ordinal()] = amount[type.ordinal()] - depoArr[type.ordinal()];
            }
        }
        if (missing != null) {
            if (!allowNegative) {
                String[] msg = {nationAccount.getMarkdownUrl() + " is missing `" + ResourceType.toString(missing) + "`. (see " +
                        CM.deposits.check.cmd.nationOrAllianceOrGuild(nationAccount.getUrl()) +
                        " ).", "ALLOW_NEGATIVE_RESOURCES is disabled (see " +
                        GuildKey.ALLOW_NEGATIVE_RESOURCES.getCommandObj(senderDB, true) +
                        ")"};
                allowedIds.entrySet().removeIf(f -> f.getValue() != AccessType.ECON);
                if (allowedIds.isEmpty()) {
                    return new KeyValue<>(null, new TransferResult(TransferStatus.INSUFFICIENT_FUNDS, receiver, amount, depositType.toString()).addMessage(msg));
                }
                reqMsg.append(StringMan.join(msg, "\n") + "\n");
            } else if (myDepoValue < txValue) {
                String msg = nationAccount.getNation() + "'s deposits are worth $" + MathMan.format(myDepoValue) + "(market max) but you requested to withdraw $" + MathMan.format(txValue) + " worth of resources";
                allowedIds.entrySet().removeIf(f -> f.getValue() != AccessType.ECON);
                if (allowedIds.isEmpty()) {
                    return new KeyValue<>(null, new TransferResult(TransferStatus.INSUFFICIENT_FUNDS, receiver, amount, depositType.toString()).addMessage(msg));
                }
                reqMsg.append(msg + "\n");
            }
        }
        return new KeyValue<>(myDeposits, null);
    }

    private Map.Entry<Map<NationOrAllianceOrGuild, double[]>, double[]> checkDeposits(GuildDB senderDB, Predicate<Integer> allowedAlliances, double[] amount, boolean update) {
        Map<NationOrAllianceOrGuild, double[]> depositsByAA = getDepositsByAA(senderDB, allowedAlliances, update);
        double[] deposits = ResourceType.getBuffer();
        double[] finalDeposits = deposits;
        depositsByAA.forEach((a, b) -> ResourceType.add(finalDeposits, b));

        if (senderDB != getGuildDB()) {
            deposits = PW.normalize(deposits); // normalize
            for (int i = 0; i < amount.length; i++) {
                if (Math.round(amount[i] * 100) != 0 && Math.round(deposits[i] * 100) < Math.round(amount[i] * 100)) {
                    if (!update) {
                        return checkDeposits(senderDB, allowedAlliances, amount, true);
                    }
                    throw new IllegalArgumentException("You do not have " + MathMan.format(amount[i]) + "x" + ResourceType.values[i] + ", only " + MathMan.format(deposits[i]) + " (normalized)\n" +
                            "Note: Account balance is managed on the offshore server (" + getGuildDB().getGuild() + ") and can be adjusted via " + CM.deposits.add.cmd.toSlashMention());
                }
                if (!Double.isFinite(amount[i]) || Math.round(amount[i] * 100) < 0) {
                    if (!update) {
                        return checkDeposits(senderDB, allowedAlliances, amount, true);
                    }
                    throw new IllegalArgumentException(amount[i] + " is not a valid positive amount");
                }
            }
            if (amount[ResourceType.CREDITS.ordinal()] != 0) {
                if (!update) {
                    return checkDeposits(senderDB, allowedAlliances, amount, true);
                }
                throw new IllegalArgumentException("You cannot transfer credits");
            }
        }
        return KeyValue.of(depositsByAA, deposits);
    }

    public TransferResult transferSafe(DBNation nation, Map<ResourceType, Double> transfer, String note, Map<DBAlliance, Map<ResourceType, Double>> transferRoute) {
        synchronized (BANK_LOCK) {
            TransferResult result = transfer(nation, transfer, note, transferRoute);
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
//                        result = KeyValue.of(TransferStatus.SUCCESS, "Withdrawing funds using local account");
//                        response.append("\n" + result.getValue());
//                        System.out.println("Different DB " + bank.getGuildDB().getGuild() + " | " + db.getGuild());
//                    }
//                    if (result.getKey() == TransferStatus.SUCCESS) {
//                        response.append("\nTransferring to nation...");
//                        Auth auth = null;
//                        DBAlliancePosition position = nation.getAlliancePosition();
//                        if (nation.getPositionEnum().id >= Rank.MEMBER.id || position != null && position.hasPermission(AlliancePermission.WITHDRAW_BANK)) {
//                            try {
//                                Auth nationAuth = nation.getAuth(true);
//                                if (nationAuth != null) auth = nationAuth;
//                            } catch (IllegalArgumentException ignore) {}
//                        }
//                        try {
//                            result = bank.transfer(auth, nation, transfer, note);
//                        } catch (Throwable e) {
//                            e.printStackTrace();
//                        }
//                        if (result.getKey() != TransferStatus.SUCCESS) {
//                            result = new KeyValue<>(TransferStatus.SUCCESS, result.getValue());
//                        }
//                        response.append("\n" + result.getValue());
//                    }
//                    result = KeyValue.of(result.getKey(), response.toString());
//                }
//            }
            return result;
        }
    }

    public TransferResult transfer(DBNation nation, Map<ResourceType, Double> transfer, String note, Map<DBAlliance, Map<ResourceType, Double>> transferRoute) {
        synchronized (BANK_LOCK) {
            Auth auth = getAlliance().getAuth(AlliancePermission.WITHDRAW_BANK);
            return transfer(auth, nation, transfer, note, transferRoute);
        }
    }

    public TransferResult transfer(Auth auth, DBNation nation, Map<ResourceType, Double> transfer, String note, Map<DBAlliance, Map<ResourceType, Double>> transferRoute) {
        if (!TimeUtil.checkTurnChange()) {
//            return KeyValue.of(TransferStatus.TURN_CHANGE, "You cannot transfer close to turn change");
            return new TransferResult(TransferStatus.TURN_CHANGE, nation, transfer, note).addMessage("You cannot transfer close to turn change");
        }
        if (DISABLE_TRANSFERS) throw new IllegalArgumentException(DISABLED_MESSAGE);
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
            TransferResult result = transferWithRoute(auth, nation, transfer, note, transferRoute);//categorize(task);
            String msg = "`" + ResourceType.toString(transfer) + "` -> " + nation.getUrl() + "\n**" + result.getStatus() + "**: " + result.getMessageJoined(true);

            MessageChannel logChannel = getGuildDB().getResourceChannel(0);
            if (logChannel != null) {
                RateLimitUtil.queueMessage(logChannel, msg, true);
            }
            return result;
        }
    }

    public TransferResult transfer(DBAlliance alliance, Map<ResourceType, Double> transfer, Map<DBAlliance, Map<ResourceType, Double>> transferRoute) {
        return transfer(alliance, transfer, "#deposit", transferRoute);
    }

    public TransferResult transferWithRoute(Auth auth, NationOrAlliance receiver, Map<ResourceType, Double> transfer, String note, Map<DBAlliance, Map<ResourceType, Double>> transferRoute) {
        TransferResult resultFinal;
        if (transferRoute != null) {
            if (transferRoute.isEmpty()) {
                return new TransferResult(TransferStatus.NOTHING_WITHDRAWN, receiver, transfer, note).addMessage("No transfer route specified");
            }
            String routeNote = "#" + DepositType.IGNORE.name().toLowerCase(Locale.ROOT);

            List<TransferResult> results = new ArrayList<>();
            Map<DBAlliance, PoliticsAndWarV3> apis = new LinkedHashMap<>();
            boolean isError = false;

            for (Map.Entry<DBAlliance, Map<ResourceType, Double>> entry : transferRoute.entrySet()) {
                DBAlliance alliance = entry.getKey();
                Map<ResourceType, Double> amt = entry.getValue();

                PoliticsAndWarV3 api = alliance.getApi(AlliancePermission.WITHDRAW_BANK);
                if (api == null) {
                    isError = true;
                    String msg = "No api key found with `" + AlliancePermission.WITHDRAW_BANK.name() + "` for " + alliance.getMarkdownUrl() + ". Set one with " + CM.settings_default.registerApiKey.cmd.toSlashMention();
                    results.add(new TransferResult(TransferStatus.INVALID_API_KEY, alliance, amt, note).addMessage(msg));
                    continue;
                }
                apis.put(alliance, api);
            }

            for (Map.Entry<DBAlliance, PoliticsAndWarV3> entry : apis.entrySet()) {
                DBAlliance alliance = entry.getKey();
                Map<ResourceType, Double> amt = transferRoute.get(alliance);
                PoliticsAndWarV3 api = entry.getValue();

                if (isError) {
                    results.add(new TransferResult(TransferStatus.NOTHING_WITHDRAWN, alliance, amt, note).addMessage("Skipped due to previous error"));
                    continue;
                }

                TransferResult result = transferUnsafe(auth, alliance, amt, routeNote);
                results.add(result);
                if (!result.getStatus().isSuccess()) {
                    isError = true;
                } else {
                    Auth routeAuth = getAlliance().getAuth(AlliancePermission.WITHDRAW_BANK);
                    result = createTransfer(routeAuth, api, receiver, amt, note);
                    results.add(result);
                    if (!result.getStatus().isSuccess()) {
                        isError = true;
                    }
                }
            }

            boolean hasOther = results.stream().anyMatch(r -> r.getStatus() == TransferStatus.OTHER);
            boolean hasSuccess = results.stream().anyMatch(r -> r.getStatus().isSuccess());
            String msgCombined = results.stream().map(f -> f.getMessageJoined(true)).collect(Collectors.joining("\n"));
            if (hasOther) {
                resultFinal = new TransferResult(TransferStatus.OTHER, receiver, transfer, note).addMessage(msgCombined);
            } else if (hasSuccess) {
                resultFinal = new TransferResult(TransferStatus.SENT_TO_ALLIANCE_BANK, receiver, transfer, note).addMessage(msgCombined);
            } else {
                resultFinal = new TransferResult(TransferStatus.NOTHING_WITHDRAWN, receiver, transfer, note).addMessage(msgCombined);
            }
        } else {
            resultFinal = transferUnsafe(auth, receiver, transfer, note);
        }
        if (resultFinal.getStatus().isSuccess()) {
            setLastSuccessfulTransfer(receiver, ResourceType.resourcesToArray(transfer));
        }
        return resultFinal;
    }

    private TransferResult createTransfer(Auth auth, PoliticsAndWarV3 api, NationOrAlliance receiver, Map<ResourceType, Double> transfer, String note) {
        boolean v2 = PW.API.hasRecent500Error();
        if (!v2 || auth == null) {
            TransferResult txResult;
            try {
                Bankrec result = api.transferFromBank(ResourceType.resourcesToArray(transfer), receiver, note);
                double[] amt = ResourceType.fromApiV3(result, ResourceType.getBuffer());
                String amtStr = ResourceType.toString(amt);
                if (result.getId() != null) {
                    Locutus.imp().runEventsAsync(events -> Locutus.imp().getBankDB().saveBankRecs(List.of(result), events));
                }
                txResult = new TransferResult(TransferStatus.SUCCESS, receiver, amt, note).addMessage("Success: " + amtStr);
            } catch (HttpClientErrorException.Unauthorized e) {
                txResult = new TransferResult(TransferStatus.INVALID_API_KEY, receiver, transfer, note).addMessage("Invalid API key");
            } catch (Throwable e) {
                v2 = PW.API.is500Error(e);
                e.printStackTrace();
                String msg = e.getMessage();
                txResult = categorize(receiver, transfer, note, StringMan.stripApiKey(msg));
            }
            if (!v2 || auth == null) {
                TransferStatus status = txResult.getStatus();
                if (status == TransferStatus.SUCCESS || status == TransferStatus.SENT_TO_ALLIANCE_BANK || status == TransferStatus.OTHER) {
                    transfersThisSession.incrementAndGet();
                }
                return txResult;
            }
        }
        if (auth.getAllianceId() != allianceId) {
            throw new IllegalArgumentException("Game API is down currently");
        }
        DBAlliance aa = getAlliance();

        try {
            String response = auth.withdrawResources(aa, receiver, ResourceType.resourcesToArray(transfer), note);
            TransferResult category = categorize(receiver, transfer, note, response);
            if (category.getStatus() == TransferStatus.SUCCESS || category.getStatus() == TransferStatus.SENT_TO_ALLIANCE_BANK) {
                setLastSuccessfulTransfer(receiver, ResourceType.resourcesToArray(transfer));
            }
            return category;
        } catch (Exception e) {
            return new TransferResult(TransferStatus.OTHER, receiver, transfer, note).addMessage("Timeout: " + e.getMessage());
        }
    }

    public int getTransfersThisSession() {
        return transfersThisSession.get();
    }

    public TransferResult transferUnsafe(Auth auth, NationOrAlliance receiver, Map<ResourceType, Double> transfer, String note) {
        if (!TimeUtil.checkTurnChange()) {
            return new TransferResult(TransferStatus.TURN_CHANGE, receiver, transfer, note).addMessage(TransferStatus.TURN_CHANGE.msg);
        }
        if (receiver.isAlliance()) {
            DBAlliance alliance = receiver.asAlliance();
            if (alliance.getNations(true, (int) TimeUnit.DAYS.toMinutes(30), true).isEmpty()) {
                return new TransferResult(TransferStatus.VACATION_MODE, receiver, transfer, note).addMessage("The alliance: " + receiver.getMarkdownUrl() + " has no active members (> 30 days)");
            }
        } else if (receiver.isNation()) {
            DBNation nation = receiver.asNation();
            if (nation.getVm_turns() > 0) {
                return new TransferResult(TransferStatus.VACATION_MODE, receiver, transfer, note).addMessage(TransferStatus.VACATION_MODE.msg);
            }
            if (nation.active_m() > TimeUnit.DAYS.toMinutes(30)) {
                return new TransferResult(TransferStatus.VACATION_MODE, receiver, transfer, note).addMessage("Nation " + nation.getMarkdownUrl() + " is inactive (>30 days)");
            }
        }

        {
            try {
                PoliticsAndWarV3 api = getAlliance().getApiOrThrow(AlliancePermission.WITHDRAW_BANK);
                return createTransfer(auth, api, receiver, transfer, note);
            } catch (HttpClientErrorException.Unauthorized e) {
//                return KeyValue.of(TransferStatus.INVALID_API_KEY, "Invalid API key");
                return new TransferResult(TransferStatus.INVALID_API_KEY, receiver, transfer, note).addMessage("Invalid API key");
            } catch (RuntimeException e) {
                e.printStackTrace();
                String msg = e.getMessage();
                return categorize(receiver, transfer, note, msg);
            }
        }

//
//
//        DBNation receiverNation = null;
//        int receiverAlliance = 0;
//        if (receiver.isNation()) receiverNation = receiver.asNation();
//        else receiverAlliance = receiver.asAlliance().getAlliance_id();
//        BankWithTask task = new BankWithTask(auth, allianceId, receiverAlliance, receiverNation, new Function<Map<ResourceType, Double>, String>() {
//            @Override
//            public String apply(Map<ResourceType, Double> stock) {
//                for (Map.Entry<ResourceType, Double> entry : transfer.entrySet()) {
//                    if (entry.getValue() > stock.getOrDefault(entry.getKey(), 0d)) {
//                        return "Insufficient funds.";
//                    }
//                }
//                for (Map.Entry<ResourceType, Double> entry : transfer.entrySet()) {
//                    ResourceType type = entry.getKey();
//                    double stored = stock.getOrDefault(type, 0d);
//                    double withdraw = entry.getValue();
//                    stock.put(type, stored - withdraw);
//                }
//                return note;
//            }
//        });
//        return categorize(task);
    }

    public TransferResult transfer(DBAlliance alliance, Map<ResourceType, Double> transfer, String note, Map<DBAlliance, Map<ResourceType, Double>> transferRoute) {
        if (alliance.getAlliance_id() == allianceId) return new TransferResult(TransferStatus.INVALID_DESTINATION, alliance, transfer, note).addMessage("You can't send funds to yourself");
        if (!TimeUtil.checkTurnChange()) {
            return new TransferResult(TransferStatus.TURN_CHANGE, alliance, transfer, note).addMessage("You cannot transfer close to turn change");
        }
        if (DISABLE_TRANSFERS) throw new IllegalArgumentException(DISABLED_MESSAGE);
        if (!alliance.exists()) {
//            return KeyValue.of(TransferStatus.INVALID_DESTINATION, "The alliance does not exist");
            return new TransferResult(TransferStatus.INVALID_DESTINATION, alliance, transfer, note).addMessage("The alliance <" + alliance.getUrl() + "> does not exist");
        }
        if (alliance.getNations(true, 10000, true).isEmpty()) {
//            return KeyValue.of(TransferStatus.INVALID_DESTINATION, "The alliance has no members");
            return new TransferResult(TransferStatus.INVALID_DESTINATION, alliance, transfer, note).addMessage("The alliance has no members");
        }
        synchronized (BANK_LOCK) {
            TransferResult result = transferWithRoute(null, alliance, transfer, note, transferRoute);
            String msg = "`" + ResourceType.toString(transfer) + "` -> " + alliance.getUrl() + "\n**" + result.getStatus() + "**: " + result.getMessageJoined(true);

            MessageChannel logChannel = getGuildDB().getResourceChannel(0);
            if (logChannel != null) {
                RateLimitUtil.queueMessage(logChannel, (msg), true);
            }
            return result;
        }
    }

    public double[] getDeposits(GuildDB guildDb) {
        return getDeposits(guildDb, true);
    }

    public Map<NationOrAllianceOrGuild, double[]> getDepositsByAA(GuildDB guildDb, Predicate<Integer> allowedAlliances, boolean update) {
        Map<NationOrAllianceOrGuild, double[]> result = new Object2ObjectLinkedOpenHashMap<>();

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
                double[] rss = ResourceType.resourcesToArray(getDepositsAA(Set.of(id), update));
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
                double[] rss = ResourceType.resourcesToArray(getDeposits(guildId, update));
                result.put(guildDb, rss);
            }
        }
        return result;
    }

    public double[] getDeposits(GuildDB guildDb, boolean update) {
        Map<NationOrAllianceOrGuild, double[]> byAA = getDepositsByAA(guildDb, Predicates.alwaysTrue(), update);
        double[] deposits = ResourceType.getBuffer();
        byAA.forEach((a, b) -> ResourceType.add(deposits, b));
        return deposits;
    }

    public DBAlliance getAlliance() {
        return Locutus.imp().getNationDB().getAlliance(allianceId);
    }

    public Guild getGuild() {
        return getGuildDB().getGuild();
    }

    public enum TransferStatus {
        SUCCESS("You successfully transferred funds from the alliance bank."),
        ESCROWED("You successfully escrowed funds for withdrawal at a later date"),
        BLOCKADE("You can't withdraw resources to a blockaded nation."),
        MULTI("This player has been flagged for using the same network as you."),
        TURN_CHANGE("You cannot transfer close to turn change. (DC = 10m, TC = 1m)"),
        INSUFFICIENT_FUNDS("Insufficient funds"),
        INVALID_DESTINATION("You did not enter a valid recipient name."),
        OTHER("Unspecified error. (Is it turn change? Is there a captcha?)"),
        SENT_TO_ALLIANCE_BANK("Transferring via alliance bank"),
        VACATION_MODE("You can't send funds to this nation because they are inactive or in Vacation Mode"),
        NOTHING_WITHDRAWN("You did not withdraw anything."),

        INVALID_API_KEY("The API key you provided does not allow whitelisted access."),

        ALLIANCE_ACCESS("Has disabled alliance access to resource information (account page). Or the API key set may be lacking the required scopes"),

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

        public boolean isSuccess() {
            return this == SUCCESS || this == SENT_TO_ALLIANCE_BANK || this == ESCROWED;
        }
    }

    /**
     *         boolean whitelistedError = msg.contains("The API key you provided does not allow whitelisted access.");
     *         if (whitelistedError || msg.contains("The API key you provided is not valid.")) {
     *             String[] keys = getGuildDB().getOrNull(GuildKey.API_KEY);
     *             if (keys == null) {
     *                 msg += "\nEnsure " + GuildKey.API_KEY + " is set: " + CM.settings.cmd.toSlashMention();
     *             } else {
     *                 Integer nation = Locutus.imp().getDiscordDB().getNationFromApiKey(keys[0]);
     *                 if (nation == null) {
     *                     msg += "\nEnsure " + GuildKey.API_KEY + " is set: " + CM.settings.cmd.toSlashMention() + " to a valid key in the alliance (with bank access)";
     *                 } else {
     *                     msg += "\nEnsure " + PW.getNationUrl(nation) + " is a valid nation in the alliance with bank access in " + allianceId;
     *                 }
     *             }
     *             if (whitelistedError) {
     *                 msg += "\nEnsure Whitelisted access is enabled in " + Settings.PNW_URL() + "/account";
     *             }
     *             return KeyValue.of(TransferStatus.INVALID_API_KEY, msg);
     *         }
     *         if (msg.contains("You need provide the X-Bot-Key header with the key for a verified bot to use this endpoint.")) {
     *             return KeyValue.of(TransferStatus.INVALID_API_KEY, msg);
     *         }
     *         if (msg.isEmpty()) msg = "Unknown Error (Captcha?)";
     *         return KeyValue.of(TransferStatus.OTHER, msg);
     * @return
     */

    private TransferResult categorize(NationOrAlliance receiver, Map<ResourceType, Double> amount, String note, String msg) {

        if (msg.contains("You successfully transferred funds from the alliance bank.")) {
//            return KeyValue.of(TransferStatus.SUCCESS, msg);
            return new TransferResult(TransferStatus.SUCCESS, receiver, amount, note).addMessage(msg);
        }
        if (msg.contains("This API key does not allow you to withdraw resources from an alliance bank.")) {
            return new TransferResult(TransferStatus.INVALID_API_KEY, receiver, amount, note).addMessage(msg);
        }
        if (msg.contains("You can't send funds to this nation because they are in Vacation Mode") || msg.contains("You can't withdraw resources to a nation in vacation mode")) {
//            return KeyValue.of(TransferStatus.VACATION_MODE, msg);
            return new TransferResult(TransferStatus.VACATION_MODE, receiver, amount, note).addMessage(msg);
        }
        if (msg.contains("There was an Error with your Alliance Bank Withdrawal: You can't withdraw funds to that nation because they are under a naval blockade. When the naval blockade ends they will be able to receive funds.")
        || msg.contains("You can't withdraw resources to a blockaded nation.")) {
//            return KeyValue.of(TransferStatus.BLOCKADE, msg);
            return new TransferResult(TransferStatus.BLOCKADE, receiver, amount, note).addMessage(msg);
        }
        if (msg.contains("This player has been flagged for using the same network as you.")) {
//            return KeyValue.of(TransferStatus.MULTI, msg);
            return new TransferResult(TransferStatus.MULTI, receiver, amount, note).addMessage(msg);
        }
        if (msg.contains("Insufficient funds") || msg.contains("You don't have that much") || msg.contains("You don't have enough resources.")) {
//            return KeyValue.of(TransferStatus.INSUFFICIENT_FUNDS, msg);
            return new TransferResult(TransferStatus.INSUFFICIENT_FUNDS, receiver, amount, note).addMessage(msg);
        }
        if (msg.contains("You did not enter a valid recipient name.")) {
//            return KeyValue.of(TransferStatus.INVALID_DESTINATION, msg);
            return new TransferResult(TransferStatus.INVALID_DESTINATION, receiver, amount, note).addMessage(msg);
        }
        if (msg.contains("You did not withdraw anything.") || msg.contains("You can't withdraw no resources.")) {
//            return KeyValue.of(TransferStatus.NOTHING_WITHDRAWN, msg);
            return new TransferResult(TransferStatus.NOTHING_WITHDRAWN, receiver, amount, note).addMessage(msg);
        }
        boolean whitelistedError = msg.contains("The API key you provided does not allow whitelisted access.");
        if (whitelistedError || msg.contains("The API key you provided is not valid.")) {
            List<String> keys = getGuildDB().getOrNull(GuildKey.API_KEY);
            List<String> messages = new ArrayList<>(Arrays.asList(msg));
            if (keys == null || keys.isEmpty()) {
                messages.add("Ensure " + GuildKey.API_KEY.name() + " is set: " + CM.settings.info.cmd.toSlashMention());
            } else {
                for (String key : keys) {
                    Integer nation = Locutus.imp().getDiscordDB().getNationFromApiKey(key);
                    if (nation == null) {
                        messages.add("Ensure " + GuildKey.API_KEY.name() + " is set: " + CM.settings.info.cmd.toSlashMention() + " to a valid key in the alliance (with bank access)");
                    } else {
                        messages.add("Ensure the key is set to a valid nation in the alliance with bank access in " + PW.getMarkdownUrl(allianceId, true));
                    }
                }
            }
            if (whitelistedError) {
                messages.add("Ensure Whitelisted access is enabled in " + Settings.PNW_URL() + "/account");
            }
//            return KeyValue.of(TransferStatus.INVALID_API_KEY, msg);
            return new TransferResult(TransferStatus.INVALID_API_KEY, receiver, amount, note).addMessages(messages);
        }
        if (msg.contains("You need provide the X-Bot-Key header with the key for a verified bot to use this endpoint.")) {
//            return KeyValue.of(TransferStatus.INVALID_API_KEY, msg);
            return new TransferResult(TransferStatus.INVALID_API_KEY, receiver, amount, note).addMessage(msg);
        }
        if (msg.isEmpty()) msg = "Unknown Error (Captcha?)";
//        return KeyValue.of(TransferStatus.OTHER, msg);
        return new TransferResult(TransferStatus.OTHER, receiver, amount, note).addMessage(msg);
    }
}
