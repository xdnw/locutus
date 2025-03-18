package link.locutus.discord.db;

import com.politicsandwar.graphql.model.*;
import com.politicsandwar.graphql.model.SortOrder;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.entities.BankRecord;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.bank.TransactionEvent;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import net.dv8tion.jda.api.entities.User;
import org.example.jooq.bank.tables.records.SubscriptionsRecord;
import org.example.jooq.bank.tables.records.TaxDepositsDateRecord;
import org.example.jooq.bank.tables.records.Transactions_2Record;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.exception.InvalidResultException;
import org.jooq.impl.DSL;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import link.locutus.discord.util.scheduler.KeyValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.example.jooq.bank.Tables.LOOT_DIFF_BY_TAX_ID;
import static org.example.jooq.bank.Tables.SUBSCRIPTIONS;
import static org.example.jooq.bank.Tables.TAX_BRACKETS;
import static org.example.jooq.bank.Tables.TAX_DEPOSITS_DATE;
import static org.example.jooq.bank.Tables.TAX_ESTIMATE;
import static org.example.jooq.bank.Tables.TRANSACTIONS_2;
import static org.example.jooq.bank.Tables.TRANSACTIONS_ALLIANCE_2;
import static org.jooq.impl.DSL.lower;

public class BankDB extends DBMainV3 {
    private final Map<Long, Set<Transaction2>> transactionCache = new ConcurrentHashMap<>();

    public BankDB() throws SQLException, ClassNotFoundException {
        super(Settings.INSTANCE.DATABASE, "bank", false);
    }

    public List<Transaction2> getTransactions(Condition condition) {
        return getTransactions(condition, null, null);
    }

    public List<Transaction2> getTransactions(Condition condition, SortField<?> orderBy, Integer limit) {
        Result<Record> rs = query(TRANSACTIONS_2, condition, orderBy, limit);
        List<Transaction2> list = new ArrayList<>();
        for (Record r : rs) {
            list.add(Transaction2.fromTX2Table((Transactions_2Record) r));
        }
        return list;
    }

    public synchronized void addTaxEstimate(int taxId, int minCash, int maxCash, int minRss, int maxRss) {
        ctx().insertInto(TAX_ESTIMATE, TAX_ESTIMATE.TAX_ID, TAX_ESTIMATE.MIN_CASH, TAX_ESTIMATE.MAX_CASH, TAX_ESTIMATE.MIN_RSS, TAX_ESTIMATE.MAX_RSS)
                .values(taxId, minCash, maxCash, minRss, maxRss)
                .execute();
    }

    public Map<Integer, TaxEstimate> getTaxEstimates() {
        return getTaxEstimates(null, null, null);
    }

    public Map<Integer, TaxEstimate> getTaxEstimates(Condition condition, SortField<?> orderBy, Integer limit) {
        Result<Record> rs = query(TAX_ESTIMATE, condition, orderBy, limit);
        Map<Integer, TaxEstimate> result = new Int2ObjectOpenHashMap<>();
        for (Record r : rs) {
            TaxEstimate estimate = new TaxEstimate();
            estimate.tax_id = (r.get(TAX_ESTIMATE.TAX_ID));
            estimate.min_cash = (r.get(TAX_ESTIMATE.MIN_CASH));
            estimate.max_cash = (r.get(TAX_ESTIMATE.MAX_CASH));
            estimate.min_rss = (r.get(TAX_ESTIMATE.MIN_RSS));
            estimate.max_rss = (r.get(TAX_ESTIMATE.MAX_RSS));
            result.put(estimate.tax_id, estimate);
        }
        return result;
    }

//    public void importFromExternal(String fileName) throws SQLException, ClassNotFoundException {
//        BankDB otherDb = new BankDB(fileName, false);
//        List<Transaction2> transactions = selectTransactions(f -> {
//        });
//        int batchSize = 10000;
//        for (int i = 0; i < transactions.size(); i+= batchSize) {
//            List<Transaction2> subList = transactions.subList(i, Math.min(i + batchSize, transactions.size()));
//            addTransactions(subList, true);
//        }
//    }

    @Override
    public void createTables() {
        ctx().createTableIfNotExists(TRANSACTIONS_2).columns(TRANSACTIONS_2.fields()).primaryKey(TRANSACTIONS_2.getPrimaryKey().getFields()).execute();
        for (Index index : TRANSACTIONS_2.getIndexes()) ctx().createIndex(index);
        ctx().createTableIfNotExists(SUBSCRIPTIONS).columns(SUBSCRIPTIONS.fields()).primaryKey(SUBSCRIPTIONS.getPrimaryKey().getFields()).execute();
        for (Index index : SUBSCRIPTIONS.getIndexes()) ctx().createIndex(index);
        ctx().createTableIfNotExists(TAX_BRACKETS).columns(TAX_BRACKETS.fields()).primaryKey(TAX_BRACKETS.getPrimaryKey().getFields()).execute();
        for (Index index : TAX_BRACKETS.getIndexes()) ctx().createIndex(index);
        ctx().createTableIfNotExists(LOOT_DIFF_BY_TAX_ID).columns(LOOT_DIFF_BY_TAX_ID.fields()).primaryKey(LOOT_DIFF_BY_TAX_ID.getPrimaryKey().getFields()).execute();
        for (Index index : LOOT_DIFF_BY_TAX_ID.getIndexes()) ctx().createIndex(index);
        ctx().createTableIfNotExists(TAX_ESTIMATE).columns(TAX_ESTIMATE.fields()).primaryKey(TAX_ESTIMATE.getPrimaryKey().getFields()).execute();
        for (Index index : TAX_ESTIMATE.getIndexes()) ctx().createIndex(index);
        ctx().createTableIfNotExists(TAX_DEPOSITS_DATE).columns(TAX_DEPOSITS_DATE.fields()).primaryKey(TAX_DEPOSITS_DATE.getPrimaryKey().getFields()).execute();
        for (Index index : TAX_DEPOSITS_DATE.getIndexes()) ctx().createIndex(index);
    }

    public Transaction2 getLatestTransaction() {
        List<Transaction2> latestList = getTransactions(null, TRANSACTIONS_2.TX_ID.desc(), 1);
        return latestList.isEmpty() ? null : latestList.get(0);
    }

    public void updateBankRecs(int nationId, boolean priority, Consumer<Event> eventConsumer) {
        PoliticsAndWarV3 v3 = Locutus.imp().getV3();

        List<Transaction2> latestTx = getTransactionsByNation(nationId, 1);
        int minId = latestTx.size() == 1 ? latestTx.get(0).tx_id : 0;
        List<Bankrec> bankRecs = v3.fetchBankRecsWithInfo(priority, new Consumer<BankrecsQueryRequest>() {
            @Override
            public void accept(BankrecsQueryRequest request) {
                if (minId > 0) request.setMin_id(minId + 1);
                request.setOr_id(List.of(nationId));
//                request.setOr_type(List.of(1));
            }
        });

        saveBankRecs(bankRecs, eventConsumer);
    }

//    public void updateBankRecsv2(int nationId, boolean priority, Consumer<Event> eventConsumer) {
//        PoliticsAndWarV2 api = Locutus.imp().getPnwApiV2();
//        List<BankRecord> records = api.getBankRecords(nationId, priority);
//        saveBankRecsV2(records, eventConsumer);
//    }

    public void updateBankRecs(boolean priority, Consumer<Event> eventConsumer) {
        ByteBuffer info = Locutus.imp().getDiscordDB().getInfo(DiscordMeta.BANK_RECS_SEQUENTIAL, 0);
        int latestId = info == null ? -1 : info.getInt();

        PoliticsAndWarV3 v3 = Locutus.imp().getV3();

        List<Bankrec> records = new ArrayList<>();
        Runnable saveTransactions = () -> {
            if (records.isEmpty()) return;
            List<Bankrec> copy = new ArrayList<>(records);
            int maxId = copy.stream().mapToInt(Bankrec::getId).max().getAsInt();
            saveBankRecs(copy, eventConsumer);

            byte[] maxIdData = ByteBuffer.allocate(4).putInt(maxId).array();
            Locutus.imp().getDiscordDB().setInfo(DiscordMeta.BANK_RECS_SEQUENTIAL, 0, maxIdData);
            records.clear();
        };
        v3.fetchBankRecs(priority, new Consumer<BankrecsQueryRequest>() {
            @Override
            public void accept(BankrecsQueryRequest f) {
                f.setOr_type(List.of(1));
                if (latestId > 0) f.setMin_id(latestId + 1);
                f.setOrderBy(List.of(new QueryBankrecsOrderByOrderByClause(QueryBankrecsOrderByColumn.ID, SortOrder.ASC, null)));
            }
        }, v3.createBankRecProjection(), new Predicate<Bankrec>() {
            @Override
            public boolean test(Bankrec bankrec) {
                records.add(bankrec);
                if (records.size() > 1000) {
                    saveTransactions.run();
                }
                return false;
            }
        });
        saveTransactions.run();
    }

    public void saveBankRecsV2(List<BankRecord> bankrecs, Consumer<Event> eventConsumer) {
        if (bankrecs.isEmpty()) return;
        invalidateTXCache();
        List<Transaction2> transfers = new ArrayList<>();
        for (BankRecord bankrec : bankrecs) {
            Transaction2 tx = new Transaction2(bankrec);
            transfers.add(tx);
        }
        Collections.sort(transfers, Comparator.comparingLong(Transaction2::getDate));
        int[] modified = addTransactions(transfers, true);
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
        if (eventConsumer != null) {
            for (int i = 0; i < modified.length; i++) {
                if (modified[i] > 0) {
                    Transaction2 tx = transfers.get(i);
                    if (tx.tx_datetime > cutoff) {
                        eventConsumer.accept(new TransactionEvent(tx));
                    }
                }
            }
        }
    }

    public void saveBankRecs(List<Bankrec> bankrecs, Consumer<Event> eventConsumer) {
        if (bankrecs.isEmpty()) return;
        invalidateTXCache();
        List<Transaction2> transfers = new ArrayList<>();
        for (Bankrec bankrec : bankrecs) {
            Transaction2 tx = Transaction2.fromApiV3(bankrec);
            transfers.add(tx);
        }
        Collections.sort(transfers, Comparator.comparingLong(Transaction2::getDate));
        int[] modified = addTransactions(transfers, true);
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
        if (eventConsumer != null) {
            for (int i = 0; i < modified.length; i++) {
                if (modified[i] > 0) {
                    Transaction2 tx = transfers.get(i);
                    if (tx.tx_datetime > cutoff) {
                        eventConsumer.accept(new TransactionEvent(tx));
                    }
                }
            }
        }
    }

//    public void addLoan(DBLoan loan) {
//        addTask(TaskType.LOAN, pair, new UniqueStatement(pair) {
//            @Override
//            public PreparedStatement get() throws SQLException {
//                return getConnection().prepareStatement("INSERT OR IGNORE INTO `TAX_DEPOSITS_DATE` (`alliance`, `date`, `id`, `nation`, `moneyrate`, `resoucerate`, `resources`) VALUES(?, ?, ?, ?, ?, ?, ?)");
//            }
//
//            @Override
//            public void set(PreparedStatement stmt) throws SQLException {
//                stmt.setInt(1, allianceId);
//                stmt.setLong(2, date);
//                stmt.setInt(3, taxIndex);
//                stmt.setInt(4, nation);
//                stmt.setInt(5, moneyRate);
//                stmt.setInt(6, rssRate);
//                stmt.setBytes(7, depositBytes);
//            }
//        });
//    }

    public void addTaxDeposit(TaxDeposit record) {
        addTaxDeposits(List.of(record));
    }

    public List<Transaction2> getAllTransactions(NationOrAlliance sender, NationOrAlliance receiver, NationOrAlliance banker, Long startDate, Long endDate) {
        return getAllTransactions(sender == null ? null : Set.of(sender), receiver == null ? null : Set.of(receiver), banker == null ? null : Set.of(banker), startDate, endDate);
    }

    public List<Transaction2> getAllTransactions(Set<NationOrAlliance> sender, Set<NationOrAlliance> receiver, Set<NationOrAlliance> banker, Long startDate, Long endDate) {
        if (sender != null && sender.isEmpty()) sender = null;
        if (receiver != null && receiver.isEmpty()) receiver = null;
        if (banker != null && banker.isEmpty()) banker = null;
        if (sender == null && receiver == null && banker == null) throw new IllegalArgumentException("Please provide at least one of sender, receiver, or banker");

        Predicate<Transaction2> filter = f -> true;

        Condition condition = null;
        if (sender != null) {
            if (sender.size() == 1) {
                NationOrAlliance sender1 = sender.iterator().next();
                condition = and(condition, TRANSACTIONS_2.SENDER_ID.eq(sender1.getIdLong()).and(TRANSACTIONS_2.SENDER_TYPE.eq(sender1.getReceiverType())));
            } else {
                condition = and(condition, TRANSACTIONS_2.SENDER_ID.in(sender.stream().map(NationOrAlliance::getIdLong).collect(Collectors.toList())));
                Set<Integer> nationIds = sender.stream().filter(NationOrAlliance::isNation).map(NationOrAlliance::getId).collect(Collectors.toSet());
                Set<Integer> aaIds = sender.stream().filter(NationOrAlliance::isAlliance).map(NationOrAlliance::getId).collect(Collectors.toSet());
                filter = filter.and(f -> {
                    if (f.isSenderNation()) {
                        return nationIds.contains((int) f.sender_id);
                    } else {
                        return aaIds.contains((int) f.sender_id);
                    }
                });
            }
        }
        if (receiver != null) {
            if (receiver.size() == 1) {
                NationOrAlliance receiver1 = receiver.iterator().next();
                condition = and(condition, TRANSACTIONS_2.RECEIVER_ID.eq(receiver1.getIdLong()).and(TRANSACTIONS_2.RECEIVER_TYPE.eq(receiver1.getReceiverType())));
            } else {
                condition = and(condition, TRANSACTIONS_2.RECEIVER_ID.in(receiver.stream().map(NationOrAlliance::getIdLong).collect(Collectors.toList())));
                Set<Integer> nationIds = receiver.stream().filter(NationOrAlliance::isNation).map(NationOrAlliance::getId).collect(Collectors.toSet());
                Set<Integer> aaIds = receiver.stream().filter(NationOrAlliance::isAlliance).map(NationOrAlliance::getId).collect(Collectors.toSet());
                filter = filter.and(f -> {
                    if (f.isReceiverNation()) {
                        return nationIds.contains((int) f.receiver_id);
                    } else {
                        return aaIds.contains((int) f.receiver_id);
                    }
                });
            }
        }
        if (banker != null) {
            if (banker.size() == 1) {
                NationOrAlliance banker1 = banker.iterator().next();
                condition = and(condition, TRANSACTIONS_2.BANKER_NATION_ID.eq(banker1.getId()));
            } else {
                condition = and(condition, TRANSACTIONS_2.BANKER_NATION_ID.in(banker.stream().map(NationOrAlliance::getIdLong).collect(Collectors.toList())));
            }
        }
        if (startDate != null) {
            condition = and(condition, TRANSACTIONS_2.TX_DATETIME.ge(startDate));
        }
        if (endDate != null) {
            condition = and(condition, TRANSACTIONS_2.TX_DATETIME.le(endDate));
        }
        List<Transaction2> results = getTransactions(condition);
        try {
            boolean checkAlliance = false;
            if (receiver != null) {
                for (NationOrAlliance nationOrAlliance : receiver) {
                    if (nationOrAlliance.isAlliance()) {
                        checkAlliance = true;
                        break;
                    }
                }
            }
            if (sender != null) {
                for (NationOrAlliance nationOrAlliance : sender) {
                    if (nationOrAlliance.isAlliance()) {
                        checkAlliance = true;
                        break;
                    }
                }
            }
//            boolean checkNation = (sender != null && sender.isNation()) || (receiver != null && receiver.isNation()) || (sender == null && receiver == null);
//            boolean checkAlliance = !checkNation || (sender == null && receiver == null);

            if (checkAlliance && tableExists("TRANSACTIONS_ALLIANCE_2")) {
                String query = "SELECT * FROM %table% WHERE tx_datetime > ? AND tx_datetime < ? ";
                if (sender != null) {
                    if (sender.size() == 1) {
                        NationOrAlliance sender1 = sender.iterator().next();
                        query += " AND sender_id = " + sender1.getIdLong();
                        query += " AND sender_type = " + sender1.getReceiverType();
                    } else {
                        query += " AND sender_id IN (" + sender.stream().map(NationOrAlliance::getIdLong).map(String::valueOf).collect(Collectors.joining(",")) + ")";
                    }
                }
                if (receiver != null) {
                    if (receiver.size() == 1) {
                        NationOrAlliance receiver1 = receiver.iterator().next();
                        query += " AND receiver_id = " + receiver1.getIdLong();
                        query += " AND receiver_type = " + receiver1.getReceiverType();
                    } else {
                        query += " AND receiver_id IN (" + receiver.stream().map(NationOrAlliance::getIdLong).map(String::valueOf).collect(Collectors.joining(",")) + ")";
                    }
                }
                if (banker != null) {
                    if (banker.size() == 1)
                        query += " AND banker_nation_id = " + banker.iterator().next().getId();
                    else
                        query += " AND banker_nation_id IN (" + banker.stream().map(NationOrAlliance::getIdLong).map(String::valueOf).collect(Collectors.joining(",")) + ")";
                }
                String queryAA = query.replaceFirst("%table%", "TRANSACTIONS_ALLIANCE_2");
                Predicate<Transaction2> finalFilter = filter;
                queryLegacy(queryAA,
                        (ThrowingConsumer<PreparedStatement>) elem -> {
                            elem.setLong(1, startDate == null ? 0 : startDate);
                            elem.setLong(2, endDate == null ? 0 : endDate);
                        },
                        (ThrowingConsumer<ResultSet>) elem -> {
                            while (elem.next()) {
                                Transaction2 tx = new Transaction2(elem);
                                if (finalFilter.test(tx)) {
                                    results.add(tx);
                                }
                            }
                        }
                );
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return results;
    }

    public void deleteLegacyAllianceTransactions(int allianceId, long minDate) {
        try {
            if (tableExists("TRANSACTIONS_ALLIANCE_2")) {
                ctx().deleteFrom(TRANSACTIONS_ALLIANCE_2)
                        .where(TRANSACTIONS_ALLIANCE_2.TX_DATETIME.gt(minDate).and(
                                        DSL.or(TRANSACTIONS_ALLIANCE_2.SENDER_ID.eq((long) allianceId).and(TRANSACTIONS_ALLIANCE_2.SENDER_TYPE.eq(2)),
                                                (TRANSACTIONS_ALLIANCE_2.RECEIVER_ID.eq((long) allianceId).and(TRANSACTIONS_ALLIANCE_2.RECEIVER_TYPE.eq(2)))
                                        )
                                )
                        )
                        .execute();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Transaction2> getTransactionsbyId(Collection<Integer> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        if (ids.size() == 1) {
            return getTransactions(TRANSACTIONS_2.TX_ID.eq(ids.iterator().next()), null, null);
        }
        List<Integer> idsSorted = new IntArrayList(ids);
        idsSorted.sort(Comparator.naturalOrder());
        return getTransactions(TRANSACTIONS_2.TX_ID.in(idsSorted), TRANSACTIONS_2.TX_ID.desc(), null);
    }

    public List<Transaction2> getTransactionsByBySenderOrReceiver(Set<Long> senders, Set<Long> receivers, long minDateMs, long maxDateMs) {
        List<Condition> addConditions = new ArrayList<>();
        if (minDateMs > 0) {
            addConditions.add(TRANSACTIONS_2.TX_DATETIME.ge(minDateMs));
        }
        if (maxDateMs != Long.MAX_VALUE) {
            addConditions.add(TRANSACTIONS_2.TX_DATETIME.le(maxDateMs));
        }
        if (senders.size() > 0) {
            if (senders.size() == 1) {
                addConditions.add(TRANSACTIONS_2.SENDER_ID.eq(senders.iterator().next()));
            } else {
                addConditions.add(TRANSACTIONS_2.SENDER_ID.in(senders));
            }
        }
        if (receivers.size() > 0) {
            if (receivers.size() == 1) {
                addConditions.add(TRANSACTIONS_2.RECEIVER_ID.eq(receivers.iterator().next()));
            } else {
                addConditions.add(TRANSACTIONS_2.RECEIVER_ID.in(receivers));
            }
        }
        return getTransactions(DSL.and(addConditions), TRANSACTIONS_2.TX_ID.desc(), null);
    }

    public List<Transaction2> getTransactionsByBySender(Set<Long> senders, long minDateMs) {
        return getTransactions(TRANSACTIONS_2.TX_DATETIME.ge(minDateMs).and(TRANSACTIONS_2.SENDER_ID.in(senders)), TRANSACTIONS_2.TX_ID.desc(), null);
    }

    public List<Transaction2> getTransactionsByByReceiver(Set<Long> receivers, long minDateMs, long endDate) {
        return getTransactions(TRANSACTIONS_2.TX_DATETIME.ge(minDateMs).and(TRANSACTIONS_2.TX_DATETIME.le(endDate)).and(TRANSACTIONS_2.RECEIVER_ID.in(receivers)), TRANSACTIONS_2.TX_ID.desc(), null);
    }

    public List<Transaction2> getTransactions(long minDateMs, boolean desc) {
        return getTransactions(TRANSACTIONS_2.TX_DATETIME.ge(minDateMs), desc ? TRANSACTIONS_2.TX_ID.desc() : TRANSACTIONS_2.TX_ID.asc(), null);
    }

    public List<Transaction2> getToNationTransactions(long minDateMs) {
        return getTransactions(TRANSACTIONS_2.TX_DATETIME.ge(minDateMs).and(TRANSACTIONS_2.SENDER_TYPE.eq(2)).and(TRANSACTIONS_2.RECEIVER_TYPE.eq(1)), TRANSACTIONS_2.TX_ID.desc(), null);
    }
    public List<Transaction2> getNationTransfers(int nationId, long minDateMs) {
        return getTransactions(TRANSACTIONS_2.TX_DATETIME.ge(minDateMs).and(
                (TRANSACTIONS_2.RECEIVER_ID.eq((long) nationId).and(TRANSACTIONS_2.RECEIVER_TYPE.eq(1))).or
                        (TRANSACTIONS_2.SENDER_ID.eq((long) nationId).and(TRANSACTIONS_2.SENDER_TYPE.eq(1)))
        ), TRANSACTIONS_2.TX_ID.desc(), null);
    }

    public Map<Integer, List<Transaction2>> getNationTransfersByNation(long minDateMs, Set<Integer> nationIds) {
        Map<Integer, List<Transaction2>> result = new HashMap<>();

        List<Transaction2> transactions = getTransactions(TRANSACTIONS_2.TX_DATETIME.ge(minDateMs).and(
                (TRANSACTIONS_2.RECEIVER_ID.in(nationIds).and(TRANSACTIONS_2.RECEIVER_TYPE.eq(1))).or
                        (TRANSACTIONS_2.SENDER_ID.in(nationIds).and(TRANSACTIONS_2.SENDER_TYPE.eq(1)))
        ), TRANSACTIONS_2.TX_ID.desc(), null);


        for (Transaction2 transfer : transactions) {
            // add to result map
            int nationId = (int) (transfer.sender_type == 1 ? transfer.sender_id : transfer.receiver_id);
            List<Transaction2> list = result.get(nationId);
            if (list == null) {
                result.put(nationId, list = new ArrayList<>());
            }
            list.add(transfer);
        }
        return result;
    }
    //
    public List<Transaction2> getAllianceTransfers(int allianceId, long minDateMs) {
        return getTransactions(TRANSACTIONS_2.TX_DATETIME.ge(minDateMs).and(
                (TRANSACTIONS_2.RECEIVER_ID.eq((long) allianceId).and(TRANSACTIONS_2.RECEIVER_TYPE.eq(2))).or
                        (TRANSACTIONS_2.SENDER_ID.eq((long) allianceId).and(TRANSACTIONS_2.SENDER_TYPE.eq(2)))), TRANSACTIONS_2.TX_ID.desc(), null);
    }

    public int getTransactionsByNationCount(int nation) {
        return ctx().fetchCount(TRANSACTIONS_2,
                DSL.or(TRANSACTIONS_2.SENDER_ID.eq((long) nation).and(TRANSACTIONS_2.SENDER_TYPE.eq(1))),
                TRANSACTIONS_2.RECEIVER_ID.eq((long) nation).and(TRANSACTIONS_2.RECEIVER_TYPE.eq(1))
        );
    }

    public List<Transaction2> getTransactionsByBanker(int nation) {
        return getTransactions(TRANSACTIONS_2.BANKER_NATION_ID.eq(nation));
    }

    private SoftReference<Map.Entry<Integer, List<Transaction2>>> txNationCache = null;
    private void invalidateTXCache() {
        txNationCache = null;
    }

    public List<Transaction2> getTransactionsByNation(int nation) {
        return getTransactionsByNation(nation, -1);
    }

    public List<Transaction2> getTransactionsByNation(int nation, int limit) {
        Reference<Map.Entry<Integer, List<Transaction2>>> tmp = txNationCache;
        Map.Entry<Integer, List<Transaction2>> cached = tmp == null ? null : tmp.get();
        if (cached != null && cached.getKey() == nation) {
            List<Transaction2> value = cached.getValue();
            if (limit > 0) {
                // sort by tx_id desc
                value.sort((o1, o2) -> Long.compare(o2.tx_id, o1.tx_id));
                // return top limit
                return value.subList(0, Math.min(limit, value.size()));
            }
            return value;
        }
        List<Transaction2> list = getTransactions(DSL.or(TRANSACTIONS_2.SENDER_ID.eq((long) nation).and(TRANSACTIONS_2.SENDER_TYPE.eq(1)),
                TRANSACTIONS_2.RECEIVER_ID.eq((long) nation).and(TRANSACTIONS_2.RECEIVER_TYPE.eq(1))), TRANSACTIONS_2.TX_ID.desc(), limit > 0 ? limit : null);
        if (limit == -1) txNationCache = new SoftReference<>(new KeyValue<>(nation, new ArrayList<>(list)));
        return list;
    }

    public List<Transaction2> getTransactionsByNote(String note) {
        note = note.toLowerCase(Locale.ROOT);
        return getTransactions(lower(TRANSACTIONS_2.NOTE).like("%" + note + "%"));
    }

    public List<Transaction2> getTransactionsByNote(String note, long cutoff) {
        note = note.toLowerCase(Locale.ROOT);
        return getTransactions(TRANSACTIONS_2.TX_DATETIME.gt(cutoff).and(lower(TRANSACTIONS_2.NOTE).like("%" + note + "%")));
    }

    public List<Transaction2> getTransactionsByAllianceSender(int alliance_id) {
        return getTransactions(TRANSACTIONS_2.SENDER_ID.eq((long) alliance_id).and(TRANSACTIONS_2.SENDER_TYPE.eq(2)));
    }

    public Set<Integer> getReceiverNationIdFromAllianceReceivers(Set<Integer> allianceIds) {
        if (allianceIds.size() == 0) throw new IllegalArgumentException("allianceIds must not be empty");
        Condition condition;
        if (allianceIds.size() == 1) {
            int allianceId = allianceIds.iterator().next();
            condition = TRANSACTIONS_2.RECEIVER_ID.eq((long) allianceId).and(TRANSACTIONS_2.RECEIVER_TYPE.eq(2));
        } else {
            condition = TRANSACTIONS_2.RECEIVER_ID.in(allianceIds).and(TRANSACTIONS_2.RECEIVER_TYPE.eq(2));
        }
        GroupField groupBy = TRANSACTIONS_2.SENDER_ID;
        Result<Record> rs = query(TRANSACTIONS_2, condition, null, null, groupBy);
        Set<Integer> set = new HashSet<>();
        for (Record r : rs) {
            Transaction2 tx = Transaction2.fromTX2Table((Transactions_2Record) r);
            if (tx.sender_type == 1) {
                // ignore notes where it is bank loot
                set.add((int) tx.sender_id);
            }
        }
        return set;
    }
    public List<Transaction2> getTransactionsByAllianceReceiver(int alliance_id) {
        return getTransactions(TRANSACTIONS_2.RECEIVER_ID.eq((long) alliance_id).and(TRANSACTIONS_2.RECEIVER_TYPE.eq(2)));
    }

    public List<Transaction2> getTransactionsByAlliance(int alliance_id) {
        return getTransactions(DSL.or(TRANSACTIONS_2.SENDER_ID.eq((long) alliance_id).and(TRANSACTIONS_2.SENDER_TYPE.eq(2)),
                TRANSACTIONS_2.RECEIVER_ID.eq((long) alliance_id).and(TRANSACTIONS_2.RECEIVER_TYPE.eq(2))));
    }

    public synchronized int[] addTransactions(List<Transaction2> transactions, boolean ignoreInto) {
        if (transactions.isEmpty()) return new int[0];
        List<Query> queries = new ArrayList<>(transactions.size());
        for (Transaction2 transaction : transactions) {
            Transactions_2Record record = ctx().newRecord(TRANSACTIONS_2);
            transaction.set(record);

            @NotNull InsertSetMoreStep<Transactions_2Record> insert = ctx().insertInto(TRANSACTIONS_2).set(record);
            Query query;
            if (ignoreInto) {
                query = insert.onDuplicateKeyIgnore();
            } else {
                query = insert.onDuplicateKeyUpdate().set(record);
            }
            queries.add(query);
        }
        invalidateTXCache();
        int[] result;
        if (queries.size() == 1) {
            result = new int[]{queries.get(0).execute()};
        } else {
            result = ctx().batch(queries).execute();
        }
        synchronized (transactionCache) {
            if (!transactionCache.isEmpty()) {
                for (int i = 0; i < transactions.size(); i++) {
                    if (result[i] <= 0) continue;
                    cache(transactions.get(i));
                }
            }
        }
        return result;
    }

    public int addTransaction(Transaction2 tx, boolean ignoreInto) {
        return addTransactions(Collections.singletonList(tx), ignoreInto)[0];
    }

    public List<TaxDeposit> getTaxesPaid(int nation, int alliance) {
        List<TaxDeposit> list = new ObjectArrayList<>();
        ctx().selectFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.ALLIANCE.eq(alliance).and(TAX_DEPOSITS_DATE.NATION.eq(nation))).fetch().forEach(rs -> list.add(TaxDeposit.of(rs)));
        return list;
    }

    public List<TaxDeposit> getTaxesByIds(Collection<Integer> ids) {
        List<Integer> idsSorted = new IntArrayList(ids);
        idsSorted.sort(Comparator.naturalOrder());
        List<TaxDeposit> list = new ObjectArrayList<>();
        if (ids.size() == 1) {
            ctx().selectFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.ID.eq(ids.iterator().next())).fetch().forEach(rs -> list.add(TaxDeposit.of(rs)));
        } else {
            ctx().selectFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.ID.in(idsSorted)).fetch().forEach(rs -> list.add(TaxDeposit.of(rs)));
        }
        return list;
    }

    public List<TaxDeposit> getTaxesByBrackets(Collection<Integer> bracketIds) {
        List<Integer> idsSorted = new IntArrayList(bracketIds);
        idsSorted.sort(Comparator.naturalOrder());
        List<TaxDeposit> list = new ObjectArrayList<>();
        if (idsSorted.size() == 1) {
            ctx().selectFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.TAX_ID.eq(idsSorted.iterator().next())).fetch().forEach(rs -> list.add(TaxDeposit.of(rs)));
        } else {
            ctx().selectFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.TAX_ID.in(idsSorted)).fetch().forEach(rs -> list.add(TaxDeposit.of(rs)));
        }
        return list;
    }

    public List<TaxDeposit> getTaxesByNations(Collection<Integer> nationIds) {
        List<Integer> idsSorted = new IntArrayList(nationIds);
        idsSorted.sort(Comparator.naturalOrder());
        List<TaxDeposit> list = new ObjectArrayList<>();
        if (idsSorted.size() == 1) {
            ctx().selectFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.NATION.eq(idsSorted.iterator().next())).fetch().forEach(rs -> list.add(TaxDeposit.of(rs)));
        } else {
            ctx().selectFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.NATION.in(idsSorted)).fetch().forEach(rs -> list.add(TaxDeposit.of(rs)));
        }
        return list;
    }

    public List<TaxDeposit> getTaxesByBracket(int tax_id) {
        List<TaxDeposit> list = new ArrayList<>();
        ctx().selectFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.TAX_ID.eq(tax_id)).fetch().forEach(rs -> list.add(TaxDeposit.of(rs)));
        return list;
    }

    public List<TaxDeposit> getTaxesByBracket(int tax_id, long afterDate) {
        List<TaxDeposit> list = new ArrayList<>();
        ctx().selectFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.TAX_ID.eq(tax_id).and(TAX_DEPOSITS_DATE.DATE.ge(afterDate))).fetch().forEach(rs -> list.add(TaxDeposit.of(rs)));
        return list;
    }

    public List<TaxDeposit> getTaxesPaid(int nation) {
        List<TaxDeposit> list = new ArrayList<>();
        ctx().selectFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.NATION.eq(nation)).fetch().forEach(rs -> list.add(TaxDeposit.of(rs)));
        return list;
    }

    public List<TaxDeposit> getTaxesByAA(int alliance) {
        List<TaxDeposit> list = new ArrayList<>();
        ctx().selectFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.ALLIANCE.eq(alliance)).fetch().forEach(rs -> list.add(TaxDeposit.of(rs)));
        return list;
    }

    public List<TaxDeposit> getTaxesByAA(Set<Integer> allianceIds) {
        if (allianceIds.isEmpty()) return new ArrayList<>();
        if (allianceIds.size() == 1) {
            return getTaxesByAA(allianceIds.iterator().next());
        }
        List<TaxDeposit> list = new ArrayList<>();
        ctx().selectFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.ALLIANCE.in(allianceIds)).fetch().forEach(rs -> list.add(TaxDeposit.of(rs)));
        return list;
    }

    public TaxDeposit getLatestTaxDeposit(int allianceId) {
        @Nullable TaxDepositsDateRecord record = ctx().selectFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.ALLIANCE.eq(allianceId)).orderBy(TAX_DEPOSITS_DATE.DATE.asc()).limit(1).fetchOne();
        return record == null ? null : TaxDeposit.of(record);
    }

    public List<TaxDeposit> getTaxesByTurn(int alliance) {

        List<TaxDeposit> list = new ArrayList<>();
        TaxDeposit turnTotal = null;

        double moneyRateDouble = 0;
        double rssRateDouble = 0;
        double intMoneyRateDouble = 0;
        double intRssRateDouble = 0;

        int i = 0;

        @NotNull Result<TaxDepositsDateRecord> results = ctx().selectFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.ALLIANCE.eq(alliance)).fetch();
        for (TaxDepositsDateRecord result : results) {
            i++;
            TaxDeposit nextDeposit = TaxDeposit.of(result);

            if (turnTotal == null) {
                i = 1;
                turnTotal = nextDeposit;
                moneyRateDouble = turnTotal.moneyRate;
                rssRateDouble = turnTotal.resourceRate;
                intMoneyRateDouble = turnTotal.internalMoneyRate;
                intRssRateDouble = turnTotal.internalResourceRate;
            } else if (Math.abs(turnTotal.date - nextDeposit.date) > 5 * 60 * 1000) {
                i = 1;
                turnTotal.moneyRate = (int) moneyRateDouble;
                turnTotal.resourceRate = (int) rssRateDouble;
                turnTotal.internalMoneyRate = (int) intMoneyRateDouble;
                turnTotal.internalResourceRate = (int) intRssRateDouble;
                list.add(turnTotal);
                turnTotal = nextDeposit;
            } else {
                moneyRateDouble = ((moneyRateDouble * (i - 1d) + nextDeposit.moneyRate) / i);
                rssRateDouble = ((rssRateDouble * (i - 1d) + nextDeposit.resourceRate) / i);
                intMoneyRateDouble = ((intMoneyRateDouble * (i - 1d) + nextDeposit.internalMoneyRate) / i);
                intRssRateDouble = ((intRssRateDouble * (i - 1d) + nextDeposit.internalResourceRate) / i);

                ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, turnTotal.resources, nextDeposit.resources, false);
            }
        }
        if (turnTotal != null) list.add(turnTotal);
        return list;
    }

    public void deleteTaxDeposits(int allianceId, long date) {
        ctx().deleteFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.ALLIANCE.eq(allianceId).and(TAX_DEPOSITS_DATE.DATE.eq(date))).execute();
    }

    public synchronized int[] addTaxDeposits(Collection<TaxDeposit> records) {
        List<Query> queries = new ArrayList<>();
        for (TaxDeposit record : records) {
            @NotNull TaxDepositsDateRecord dbRecord = ctx().newRecord(TAX_DEPOSITS_DATE);
            dbRecord.setAlliance(record.allianceId);
            long dateRounded = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(record.date));
            dbRecord.setDate(dateRounded);

            dbRecord.setId(record.index);
            dbRecord.setNation(record.nationId);
            dbRecord.setMoneyrate((int) record.moneyRate);
            dbRecord.setResoucerate((int) record.resourceRate);

            double[] deposit = record.resources;
            long[] depositCents = new long[deposit.length];
            for (int i = 0; i < deposit.length; i++) depositCents[i] = (long) (deposit[i] * 100);
            byte[] depositBytes = ArrayUtil.toByteArray(depositCents);

            dbRecord.setResources(depositBytes);

            short internalPair = MathMan.pairByte(record.internalMoneyRate, record.internalResourceRate);
            dbRecord.setInternalTaxrate((int) internalPair);
            dbRecord.setTaxId(record.tax_id);

            Query query = ctx().insertInto(TAX_DEPOSITS_DATE).set(dbRecord).onDuplicateKeyIgnore();
            queries.add(query);
        }
        if (queries.size() == 1) {
            return new int[]{queries.get(0).execute()};
        } else {
            return ctx().batch(queries).execute();
        }
    }

    public void clearTaxDeposits(int allianceId) {
        ctx().deleteFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.ALLIANCE.eq(allianceId)).execute();
    }

    public Map<Integer, TaxBracket> getTaxBracketsFromDeposits() {
        Map<Integer, TaxBracket> rates = new HashMap<>();
        ctx().select(TAX_DEPOSITS_DATE.TAX_ID, DSL.max(TAX_DEPOSITS_DATE.DATE), TAX_DEPOSITS_DATE.MONEYRATE, TAX_DEPOSITS_DATE.RESOUCERATE, TAX_DEPOSITS_DATE.ALLIANCE)
                .from(TAX_DEPOSITS_DATE).groupBy(TAX_DEPOSITS_DATE.TAX_ID, TAX_DEPOSITS_DATE.MONEYRATE, TAX_DEPOSITS_DATE.RESOUCERATE).fetch().forEach(f -> {
                    int id = f.component1();
                    long date = f.component2();
                    int money = f.component3();
                    int rss = f.component4();
                    int alliance = f.component5();

                    TaxBracket existing = rates.get(id);
                    if (existing == null || existing.dateFetched < date) {
                        existing = new TaxBracket(id, alliance, "", money, rss, date);
                        rates.put(id, existing);
                    }
                });

        return rates;
    }

    public Map<Integer, TaxBracket> getTaxBracketsAndEstimates() {
        return getTaxBracketsAndEstimates(true, true, true);
    }
    public Map<Integer, TaxBracket> getTaxBracketsAndEstimates(boolean allowDeposits, boolean allowApi, boolean addUnknownBrackets) {
        Map<Integer, TaxBracket> rates = new HashMap<>();

        Map<Integer, Integer> taxIdByAlliances = new HashMap<>();
        // add date to tax record
        List<Map.Entry<Integer, TaxBracket>> bracketEntries = new ArrayList<>();

        if (allowDeposits) bracketEntries.addAll(getTaxBracketsFromDeposits().entrySet());
        if (allowApi) bracketEntries.addAll(getTaxBrackets(taxIdByAlliances).entrySet());
//        if (allowSuggestions) bracketEntries.addAll(getTaxBracketSuggestions(taxIdByAlliances).entrySet());

        for (Map.Entry<Integer, TaxBracket> entry : bracketEntries) {
            TaxBracket bracket = entry.getValue();
            TaxBracket existing = rates.get(bracket.taxId);
            if (existing == null || existing.dateFetched < bracket.dateFetched) {
                rates.put(bracket.taxId, bracket);
            }
        }

        if (addUnknownBrackets) {
            if (taxIdByAlliances.isEmpty()) {
                taxIdByAlliances.putAll(Locutus.imp().getNationDB().getAllianceIdByTaxId());
            }
            for (Map.Entry<Integer, Integer> entry : taxIdByAlliances.entrySet()) {
                int taxId = entry.getKey();
                int allianceId = entry.getValue();
                if (!rates.containsKey(taxId)) {
                    rates.put(taxId, new TaxBracket(taxId, allianceId, "", -1, -1, 0));
                }
            }

        }

        return rates;
    }

    public Map<Integer, TaxBracket> getTaxBrackets() {
        return getTaxBrackets(new HashMap<>());
    }

    public Map<Integer, TaxBracket> getTaxBrackets(Map<Integer, Integer> alliancesByTaxId) {
        if (alliancesByTaxId.isEmpty()) alliancesByTaxId.putAll(Locutus.imp().getNationDB().getAllianceIdByTaxId());

        Map<Integer, TaxBracket> result = new HashMap<>();
        for (org.example.jooq.bank.tables.records.TaxBracketsRecord rs : ctx().selectFrom(TAX_BRACKETS).fetch()) {
            int id = rs.getId();
            int money = rs.getMoney();
            int rss = rs.getResources();
            long date = rs.getDate();
            int allianceId = alliancesByTaxId.getOrDefault(id, 0);
            result.put(id, new TaxBracket(id, allianceId, "", money, rss, date));
        }
        return result;
    }

    public void addTaxBracket(TaxBracket bracket) {
        updateLegacy("INSERT OR REPLACE INTO `TAX_BRACKETS`(`id`, `money`, `resources`, `date`) VALUES(?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, bracket.taxId);
            stmt.setInt(2, bracket.moneyRate);
            stmt.setInt(3, bracket.rssRate);
            stmt.setLong(4, bracket.dateFetched);
        });
    }

    public synchronized void purgeSubscriptions() {
        long now = System.currentTimeMillis();
        updateLegacy("DELETE FROM `SUBSCRIPTIONS` WHERE date < ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, now);
        });
    }

    public void unsubscribeAll(long userId) {
        updateLegacy("DELETE FROM `SUBSCRIPTIONS` WHERE user = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, userId);
        });
    }

    public void unsubscribe(User user, int allianceOrNation, BankSubType type) {
        updateLegacy("DELETE FROM `SUBSCRIPTIONS` WHERE user = ? AND allianceOrNation = ? AND isNation & ? > 0", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, user.getIdLong());
            stmt.setInt(2, allianceOrNation);
            stmt.setInt(3, type.mask);
        });
    }

    public void subscribe(User user, int allianceOrNation, BankSubType type, long date, boolean isReceive, long amount) {
        long pair = user.getIdLong();
        updateLegacy("INSERT OR REPLACE INTO `SUBSCRIPTIONS`(`user`, `allianceOrNation`, `isNation`, `date`, `isReceive`, `amount`) VALUES(?, ?, ?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, user.getIdLong());
            stmt.setInt(2, allianceOrNation);
            stmt.setInt(3, type.mask);
            stmt.setLong(4, date);
            stmt.setBoolean(5, isReceive);
            stmt.setLong(6, amount);
        });
    }

    public enum BankSubType {
        ALL(7),
        ALLIANCE(1),
        NATION(2),
        GUILD(4);

        public static final BankSubType[] values = values();
        public static final BankSubType of(boolean isAA) {
            return isAA ? ALLIANCE : NATION;
        }

        public final int mask;

        BankSubType(int i) {
            this.mask = i;
        }

        public static BankSubType get(int isNation) {
            for (BankSubType type : values) {
                if (type.mask == isNation) return type;
            }
            return null;
        }
    }

    public Set<Subscription> getSubscriptions(int allianceOrNation, BankSubType type, boolean isReceive, long amount) {
        long date = System.currentTimeMillis();
        Set<Subscription> list = new LinkedHashSet<>();

        ctx().selectFrom(SUBSCRIPTIONS)
                .where(SUBSCRIPTIONS.ALLIANCEORNATION.eq(allianceOrNation))
                .and(SUBSCRIPTIONS.ISNATION.bitAnd(type.mask).gt(0))
                .and(SUBSCRIPTIONS.ISRECEIVE.eq(isReceive ? 1 : 0))
                .and(SUBSCRIPTIONS.AMOUNT.le(amount))
                .and(SUBSCRIPTIONS.DATE.gt(date))
                .fetch().forEach(rs -> {
                    try {
                        list.add(createSub(rs));
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
        return list;
    }

    public static class Subscription {
        public final long user;
        public final int allianceOrNation;
        public final BankSubType type;
        public final long endDate;
        public final boolean isReceive;
        public final long amount;

        public Subscription(long user, int allianceOrNation, BankSubType type, long endDate, boolean isReceive, long amount) {
            this.user = user;
            this.allianceOrNation = allianceOrNation;
            this.type = type;
            this.endDate = endDate;
            this.isReceive = isReceive;
            this.amount = amount;
        }
    }

    public Subscription createSub(org.example.jooq.bank.tables.records.SubscriptionsRecord rs) throws SQLException {
        long user = rs.getUser();
        int allianceOrNation = rs.getAllianceornation();
        BankSubType type = BankSubType.get(rs.getIsnation());
        long date = rs.getDate();
        boolean isReceive = rs.getIsreceive() == 1;
        long amount = rs.getAmount();
        return new Subscription(user, allianceOrNation, type, date, isReceive, amount);
    }

    public Set<Subscription> getSubscriptions(long userId) {
        long date = System.currentTimeMillis();
        Set<Subscription> list = new LinkedHashSet<>();

        @NotNull Result<SubscriptionsRecord> records = ctx().selectFrom(SUBSCRIPTIONS).where(SUBSCRIPTIONS.USER.eq(userId)).and(SUBSCRIPTIONS.DATE.gt(date)).fetch();
        for (SubscriptionsRecord rs : records) {
            try {
                list.add(createSub(rs));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        return list;
    }

    //    public void addAllianceTransactionsLegacy3(List<Transaction2> transactions) {
//        if (transactions.isEmpty()) return;
//        long now = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(60);
//        for (Transaction2 transaction : transactions) {
//            if (transaction.tx_datetime > now) throw new IllegalArgumentException("Transaction date is > now: " + transaction.tx_datetime);
//        }
//
//        String query = transactions.get(0).createInsert("TRANSACTIONS_ALLIANCE_2", false, false);
//        executeBatch(transactions, query, (ThrowingBiConsumer<Transaction2, PreparedStatement>) Transaction2::setNoID);
//    }
//
//    public void importTransactionsLegacy() {
//        BankDB bankDb = Locutus.imp().getBankDB();
//
//        Set<Long> offshores = Locutus.imp().getRootBank().getGuildDB().getCoalition2(Coalition.OFFSHORE);
//        List<Transfer> transfersLegacy = bankDb.getBankTransactionsLegacy();
//        transfersLegacy.removeIf(f -> f.getReceiver() > 10000);
//        transfersLegacy.removeIf(f -> !offshores.contains((long) f.getSender()) && !offshores.contains((long) f.getReceiver()) || f.getAmount() == 0);
//        Map<Long, List<Transfer>> byAA = new HashMap<>();
//        for (Transfer transfer : transfersLegacy) {
//            long pair = transfer.getSender() ^ ((long) transfer.getReceiver() << 17L) ^ ((long) transfer.getBanker() << 34L);
//            byAA.computeIfAbsent(pair, f -> new ArrayList<>()).add(transfer);
//        }
//
//        List<Transaction2> transaction2s = new ArrayList<>();
//
//        int notOffshore = 0;
//        Map<Integer, double[]> duplicateByAA = new HashMap<>();
//
//        for (Map.Entry<Long, List<Transfer>> entry : byAA.entrySet()) {
//            List<Transfer> transferList = entry.getValue();
//            Collections.sort(transferList, new Comparator<Transfer>() {
//                @Override
//                public int compare(Transfer o1, Transfer o2) {
//                    return Long.compare(o1.getDate(), o2.getDate());
//                }
//            });
//            Transaction2 transaction = null;
//            Transfer last = null;
//
//            List<Integer> toRemove = new ArrayList<>();
//            for (int i = 0; i < transferList.size(); i++) {
//                for (int j = i + 1; j < transferList.size(); j++) {
//                    Transfer tx1 = transferList.get(i);
//                    Transfer tx2 = transferList.get(j);
//                    if (tx2.getDate() > tx1.getDate() + 60000) break;
//                    if (!tx1.isReceiverAA() || !tx2.isReceiverAA() || !tx1.isSenderAA() || !tx2.isSenderAA()) continue;
//                    if (tx1.getReceiver() > 8762 || tx1.getSender() > 8762) continue;
//                    if (!offshores.contains((long) tx1.getSender()) && !offshores.contains((long) tx1.getReceiver())) {
//                        notOffshore++;
//                        continue;
//                    }
//                    if (tx1.getSender() == tx2.getSender() &&
//                            tx1.getReceiver() == tx2.getReceiver() &&
//                            tx1.getBanker() == tx2.getBanker() &&
//                            tx1.getDate() / 60000 == tx2.getDate() / 60000 &&
//                            tx1.getRss() == tx2.getRss() &&
//                            tx1.getAmount() == tx2.getAmount() &&
//                            Objects.equals(tx1.getNote(), tx2.getNote())
//                    ) {
//                        toRemove.add(j);
//                        if (tx1.isSenderAA()) {
//                            duplicateByAA.computeIfAbsent(tx1.getSender(), f -> ResourceType.getBuffer())[tx1.getRss().ordinal()] += (tx1.getAmount() * 1);
//                        }
//                        if (tx1.isReceiverAA()) {
//                            duplicateByAA.computeIfAbsent(tx1.getReceiver(), f -> ResourceType.getBuffer())[tx1.getRss().ordinal()] += (tx1.getAmount() * -1);
//                        }
//                    }
//                }
//            }
//            if (!toRemove.isEmpty()) {
//                toRemove = new ArrayList<>(new HashSet<>(toRemove));
//                Collections.sort(toRemove);
//                for (int i = toRemove.size() - 1; i >= 0; i--) {
//                    transferList.remove((int) toRemove.get(i));
//                }
//            }
//
//            for (Transfer transfer : transferList) {
//                // if equal to previous and rss id > previous rss id
//                if (last == null) {
//                    last = transfer;
//                    transaction = new Transaction2(last);
//                    continue;
//                }
//                if (transfer.isReceiverAA() != last.isReceiverAA() ||
//                        transfer.isSenderAA() != last.isSenderAA() ||
//                        (transfer.getDate() / 60000) != (last.getDate() / 60000) ||
//                        transfer.getSender() != last.getSender() ||
//                        transfer.getReceiver() != last.getReceiver() ||
//                        transfer.getBanker() != last.getBanker() ||
////                        transfer.getRss().ordinal() <= last.getRss().ordinal() ||
//                        transaction.resources[transfer.getRss().ordinal()] != 0 ||
//                        !Objects.equals(transfer.getNote(), last.getNote())
//                ) {
//                    transaction2s.add(transaction);
//                    last = transfer;
//                    transaction = new Transaction2(last);
//                    continue;
//                } else {
//                    transaction.resources[transfer.getRss().ordinal()] += transfer.getAmount();
//                }
//            }
//            if (transaction != null) {
//                transaction2s.add(transaction);
//            }
//        }
//
//        double[] total = ResourceType.getBuffer();
//        for (Long offshore : offshores) {
//            total = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, total, duplicateByAA.getOrDefault(offshore.intValue(), ResourceType.getBuffer()));
//        }
//        for (Map.Entry<Integer, double[]> entry : duplicateByAA.entrySet()) {
//            if (offshores.contains((long) entry.getKey())) continue;
//        }
//
//        Collections.sort(transaction2s, new Comparator<Transaction2>() {
//            @Override
//            public int compare(Transaction2 o1, Transaction2 o2) {
//                return Long.compare(o1.getDate(), o2.getDate());
//            }
//        });
//
//        addAllianceTransactionsLegacy2(transaction2s);
//    }
//
//    public void removeAllianceTransactions(int allianceId, long timestamp) {
//        updateLegacy("DELETE FROM `TRANSACTIONS_ALLIANCE_2` WHERE (sender_id = ? AND sender_type = ?) or (receiver_id = ? AND receiver_type = ?) and tx_datetime >= ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
//            stmt.setInt(1, allianceId);
//            stmt.setInt(2, 2);
//            stmt.setInt(3, allianceId);
//            stmt.setInt(4, 2);
//            stmt.setLong(5, timestamp);
//        });
//    }
//
//    public void removeAllianceTransactions(int aaId1, int aaId2, long timestamp) {
//        if (aaId1 == aaId2) {
//            removeAllianceTransactions(aaId1, timestamp);
//            return;
//        }
//        updateLegacy("DELETE FROM `TRANSACTIONS_ALLIANCE_2` WHERE ((sender_type = 2 and receiver_type = 2) and ((sender_id = ? and receiver_id = ?) or (sender_id = ? and receiver_id = ?))) and tx_datetime >= ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
//            stmt.setInt(1, aaId1);
//            stmt.setInt(2, aaId2);
//            stmt.setInt(3, aaId2);
//            stmt.setInt(4, aaId1);
//            stmt.setLong(5, timestamp);
//        });
//    }
//
//    public void removeAllianceTransactions(int allianceId) {
//        updateLegacy("DELETE FROM `TRANSACTIONS_ALLIANCE_2` WHERE (sender_id = ? AND sender_type = ?) or (receiver_id = ? AND receiver_type = ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
//            stmt.setInt(1, allianceId);
//            stmt.setInt(2, 2);
//            stmt.setInt(3, allianceId);
//            stmt.setInt(4, 2);
//        });
//    }
//
//    public List<Transaction2> getBankTransactionsWithNote(String note, long cutoff) {
//        note = "%" + note.toLowerCase() + "%";
//        String query = "select * FROM TRANSACTIONS_ALLIANCE_2 WHERE tx_datetime >= ? AND lower(note) like ?";
//
//        List<Transaction2> list = new ArrayList<>();
//
//        String finalNote = note;
//        query(query, new ThrowingConsumer<PreparedStatement>() {
//            @Override
//            public void acceptThrows(PreparedStatement stmt) throws Exception {
//                stmt.setLong(1, cutoff);
//                stmt.setString(2, finalNote);
//            }
//        }, (ThrowingConsumer<ResultSet>) rs -> {
//            while (rs.next()) {
//                list.add(new Transaction2(rs));
//            }
//        });
//        return list;
//    }
//
    private boolean shouldCache(long senderOrReceiverId, int type) {
        return Math.abs(senderOrReceiverId) < Integer.MAX_VALUE && type == 2 || Math.abs(senderOrReceiverId) > Integer.MAX_VALUE && type == 3;
    }

    private boolean shouldCache(Transaction2 tx) {
        if (shouldCache(tx.sender_id, tx.sender_type) || shouldCache(tx.receiver_id, tx.receiver_type)) {
            return true;
        }
        return false;
    }

    private void cache(Transaction2 tx) {
        if (shouldCache(tx.sender_id, tx.sender_type)) {
            synchronized (transactionCache) {
                Set<Transaction2> existing = transactionCache.get(tx.sender_id);
                if (existing != null) {
                    existing.add(tx);
                }
            }
        }
        if (shouldCache(tx.receiver_id, tx.receiver_type)) {
            synchronized (transactionCache) {
                Set<Transaction2> existing = transactionCache.get(tx.receiver_id);
                if (existing != null) {
                    existing.add(tx);
                }
            }
        }
        if (tx.note != null) {
            if (tx.note.contains("#")) {
                try {
                    if (StringMan.containsIgnoreCase(tx.note, "#guild")) {
                        String idStr = PW.parseTransferHashNotes(tx.note).get("#guild");
                        if (MathMan.isInteger(idStr)) {
                            long id = Long.parseLong(idStr);
                            if (id > Integer.MAX_VALUE) {
                                synchronized (transactionCache) {
                                    Set<Transaction2> existing = transactionCache.get(id);
                                    if (existing != null) {
                                        existing.add(tx);
                                    }
                                }
                            }
                        }
                    }
                    if (StringMan.containsIgnoreCase(tx.note, "#alliance")) {
                        String idStr = PW.parseTransferHashNotes(tx.note).get("#alliance");
                        if (MathMan.isInteger(idStr)) {
                            long id = Long.parseLong(idStr);
                            if (id < Integer.MAX_VALUE) {
                                synchronized (transactionCache) {
                                    Set<Transaction2> existing = transactionCache.get(id);
                                    if (existing != null) {
                                        existing.add(tx);
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    AlertUtil.error("Error parsing transaction note: " + tx.note, e);
                }
            }
        }
    }

    public List<Transaction2> getBankTransactions(long senderOrReceiverId, int type) {
        boolean cache = shouldCache(senderOrReceiverId, type);
        if (cache) {
            Set<Transaction2> cached = transactionCache.get(senderOrReceiverId);
            if (cached != null) {
                return new ArrayList<>(cached);
            }
        }
        List<Transaction2> result = getBankTransactions(senderOrReceiverId, type, true, true);
        if (cache) {
            synchronized (transactionCache) {
                if (transactionCache.containsKey(senderOrReceiverId)) {
                    transactionCache.get(senderOrReceiverId).addAll(result);
                } else {
                    transactionCache.put(senderOrReceiverId, new LinkedHashSet<>(result));
                }
            }
        }
        return new ArrayList<>(result);
    }

    private List<Transaction2> getBankTransactions(long senderOrReceiverId, int type, boolean includeLegacy, boolean includeModern) {
        if (type == 1) return getTransactionsByNation((int) senderOrReceiverId);
        if (type != 2 && type != 3) throw new IllegalArgumentException("Invalid type: " + type);

        List<Transaction2> list = new ArrayList<>();

        try {
            if (includeLegacy && tableExists("TRANSACTIONS_ALLIANCE_2")) {

                String query = "select * FROM TRANSACTIONS_ALLIANCE_2 WHERE ((sender_id = ? AND sender_TYPE = ?) OR (receiver_id = ? AND receiver_type = ?) OR (lower(note) like ?))";

                queryLegacy(query, new ThrowingConsumer<PreparedStatement>() {
                    @Override
                    public void acceptThrows(PreparedStatement stmt) throws Exception {
                        stmt.setLong(1, senderOrReceiverId);
                        stmt.setInt(2, type);
                        stmt.setLong(3, senderOrReceiverId);
                        stmt.setInt(4, type);

                        if (type == 3) stmt.setString(5, "%#guild=" + senderOrReceiverId + "%");
                        else if (type == 2) stmt.setString(5, "%#alliance=" + senderOrReceiverId + "%");

                    }
                }, (ThrowingConsumer<ResultSet>) rs -> {
                    while (rs.next()) {
                        list.add(new Transaction2(rs));
                    }
                });
            }

            // ((sender_id = ? AND sender_TYPE = ?) OR (receiver_id = ? AND receiver_type = ?) OR (lower(note) like ?))";

            if (includeModern) {
                Condition noteCondition;
                if (type == 3) {
                    noteCondition = TRANSACTIONS_2.NOTE.likeIgnoreCase("%#guild=" + senderOrReceiverId + "%");
                } else if (type == 2) {
                    noteCondition = TRANSACTIONS_2.NOTE.likeIgnoreCase("%#alliance=" + senderOrReceiverId + "%");
                } else throw new InvalidResultException("Invalid type " + type);
                list.addAll(getTransactions(DSL.or(TRANSACTIONS_2.SENDER_ID.eq(senderOrReceiverId).and(TRANSACTIONS_2.SENDER_TYPE.eq(type)),
                        TRANSACTIONS_2.RECEIVER_ID.eq(senderOrReceiverId).and(TRANSACTIONS_2.RECEIVER_TYPE.eq(type)),
                        noteCondition)));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        return list;
    }

//    private List<Transfer> getBankTransactionsLegacy() {
//        ArrayList<Transfer> list = new ArrayList<>();
//        try (PreparedStatement stmt = prepareQuery("select * FROM TRANSACTIONS_BANK")) {
//            try (ResultSet rs = stmt.executeQuery()) {
//                while (rs.next()) {
//                    list.add(Transfer.of(rs));
//                }
//            }
//            return list;
//        } catch (SQLException e) {
//            e.printStackTrace();
//            return null;
//        }
//    }
}