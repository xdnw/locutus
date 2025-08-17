package link.locutus.discord.db;

import com.google.common.base.Predicates;
import com.politicsandwar.graphql.model.*;
import com.politicsandwar.graphql.model.SortOrder;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.entities.BankRecord;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.bank.TransactionEvent;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import net.dv8tion.jda.api.entities.User;
import org.example.jooq.bank.tables.records.SubscriptionsRecord;
import org.example.jooq.bank.tables.records.TaxDepositsDateRecord;
import org.example.jooq.bank.tables.records.TaxSummaryRecord;
import org.example.jooq.bank.tables.records.Transactions_2Record;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.example.jooq.bank.Tables.*;
import static org.jooq.impl.DSL.lower;

public class BankDB extends DBMainV3 {
    private final Map<Integer, Set<Transaction2>> transactionCache2 = new Int2ObjectOpenHashMap<>();
    private final boolean legacyExists;

    public BankDB() throws SQLException, ClassNotFoundException {
        super(Settings.INSTANCE.DATABASE, "bank", false);
        try {
            this.legacyExists = tableExists("TRANSACTIONS_ALLIANCE_2");
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
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
        createTableWithIndexes(TRANSACTIONS_2);
        createTableWithIndexes(SUBSCRIPTIONS);
        createTableWithIndexes(TAX_BRACKETS);
        createTableWithIndexes(LOOT_DIFF_BY_TAX_ID);
        createTableWithIndexes(TAX_ESTIMATE);
        createTableWithIndexes(TAX_DEPOSITS_DATE);
        createTableWithIndexes(TAX_SUMMARY);
    }

    private final Map<Integer, Long> lastTaxSummaryUpdateByAlliance = new Int2LongOpenHashMap();
    private final Map<Integer, Short> lastTaxBaseByAlliance = new Int2ShortOpenHashMap();
    private final Map<Integer, Long> lastTaxRecordDateByAlliance = new Int2LongOpenHashMap();

    private void loadRecordDate(Set<Integer> allianceIds, Set<Integer> toUpdate) {
        @NotNull SelectJoinStep<Record2<Integer, Long>> select = ctx().select(
                        TAX_DEPOSITS_DATE.ALLIANCE,
                        DSL.max(TAX_DEPOSITS_DATE.DATE).as("maxDate")
                )
                .from(TAX_DEPOSITS_DATE);
        @NotNull SelectConditionStep<Record2<Integer, Long>> where;
        if (allianceIds.size() == 1) {
            where = select.where(TAX_DEPOSITS_DATE.ALLIANCE.eq(allianceIds.iterator().next()));

        } else {
            List<Integer> toLoadSorted = new IntArrayList(allianceIds);
            toLoadSorted.sort(Comparator.naturalOrder());
            where = select.where(TAX_DEPOSITS_DATE.ALLIANCE.in(toLoadSorted));
        }
        try (Stream<Record2<Integer, Long>> stream = where.groupBy(TAX_DEPOSITS_DATE.ALLIANCE).fetchStream()) {
            stream.forEach(r -> {
                int allianceId = r.get(TAX_DEPOSITS_DATE.ALLIANCE);
                long date = r.get("maxDate", Long.class);
                lastTaxRecordDateByAlliance.put(allianceId, date);
                long lastTaxSummaryUpdate = lastTaxSummaryUpdateByAlliance.getOrDefault(allianceId, Long.MIN_VALUE);
                if (date > lastTaxSummaryUpdate) {
                    toUpdate.add(allianceId);
                }
            });
        }
        allianceIds.forEach(aid -> {
                    lastTaxRecordDateByAlliance.computeIfAbsent(aid, k -> 0L);
                }
        );

    }

    private void loadSummaryDate(Set<Integer> allianceIds, Set<Integer> toUpdate) {
        {
            @NotNull SelectJoinStep<Record2<Integer, Long>> select = ctx().select(
                    TAX_SUMMARY.ALLIANCE_ID,
                    DSL.max(TAX_SUMMARY.DATE).as("sumDate")
            ).from(TAX_SUMMARY);
            @NotNull SelectConditionStep<Record2<Integer, Long>> where;
            if (allianceIds.size() == 1) {
                where = select.where(TAX_SUMMARY.ALLIANCE_ID.eq(allianceIds.iterator().next()));
            } else {
                List<Integer> toLoadSorted = new IntArrayList(allianceIds);
                toLoadSorted.sort(Comparator.naturalOrder());
                where = select.where(TAX_SUMMARY.ALLIANCE_ID.in(toLoadSorted));
            }
            try (Stream<Record2<Integer, Long>> stream = where.groupBy(TAX_SUMMARY.ALLIANCE_ID).fetchStream()) {
                stream.forEach(r ->
                        lastTaxSummaryUpdateByAlliance.put(
                                r.get(TAX_SUMMARY.ALLIANCE_ID),
                                r.get("sumDate", Long.class)
                        )
                );
            }
        }
        // any alliance with no existing summary row gets -1
        for (int aid : allianceIds) {
            long date = lastTaxSummaryUpdateByAlliance.computeIfAbsent(aid, k -> -1L);
            // if outdated (as in below lastTaxRecordDateByAlliance), add to toUpdate
            if (date < lastTaxRecordDateByAlliance.getOrDefault(aid, Long.MIN_VALUE)) {
                toUpdate.add(aid);
            }
        }
    }

    public Map<Integer, double[]> getAppliedTaxDeposits(Set<Integer> nationIds, Set<Integer> allianceIds, int[] taxBase, boolean useTaxBase) {
        Map<Integer, double[]> result = new Int2ObjectOpenHashMap<>();
        if (nationIds.isEmpty() || allianceIds.isEmpty()) return result;
        checkUpdateTaxSummary(allianceIds, taxBase);
        try (Stream<Record3<Integer, byte[], byte[]>> stream = ctx().select(TAX_SUMMARY.NATION_ID, (useTaxBase ? TAX_SUMMARY.NO_INTERNAL_APPLIED : TAX_SUMMARY.NO_INTERNAL_UNAPPLIED), TAX_SUMMARY.INTERNAL_APPLIED)
                .from(TAX_SUMMARY)
                .where(nationIds.size() == 1 ?
                        TAX_SUMMARY.NATION_ID.eq(nationIds.iterator().next()) :
                        TAX_SUMMARY.NATION_ID.in(nationIds))
                .and(allianceIds.size() == 1 ?
                        TAX_SUMMARY.ALLIANCE_ID.eq(allianceIds.iterator().next()) :
                        TAX_SUMMARY.ALLIANCE_ID.in(allianceIds))
                .fetchStream()) {
            stream.forEach(rs -> {
                int nationId = rs.get(TAX_SUMMARY.NATION_ID);
                double[] added = result.computeIfAbsent(nationId, k -> ResourceType.getBuffer());
                double[] amtBase;
                if (useTaxBase) {
                    amtBase = ArrayUtil.toDoubleArray(rs.get(TAX_SUMMARY.NO_INTERNAL_APPLIED));
                } else {
                    amtBase = ArrayUtil.toDoubleArray(rs.get(TAX_SUMMARY.NO_INTERNAL_UNAPPLIED));
                }
                ResourceType.add(added, amtBase);

                double[] amtInternal = ArrayUtil.toDoubleArray(rs.get(TAX_SUMMARY.INTERNAL_APPLIED));
                ResourceType.add(added, amtInternal);
            });
            return result;
        }
    }

    public Map<Integer, double[]> getUnappliedTaxDeposits(Set<Integer> nationIds, Set<Integer> allianceIds, int[] taxBase) {
        if (nationIds.isEmpty() || allianceIds.isEmpty()) return new Int2ObjectOpenHashMap<>();
        checkUpdateTaxSummary(allianceIds, taxBase);
        try (Stream<Record3<Integer, byte[], byte[]>> stream = ctx().select(TAX_SUMMARY.NATION_ID, TAX_SUMMARY.NO_INTERNAL_UNAPPLIED, TAX_SUMMARY.INTERNAL_UNAPPLIED)
                .from(TAX_SUMMARY)
                .where(nationIds.size() == 1 ?
                        TAX_SUMMARY.NATION_ID.eq(nationIds.iterator().next()) :
                        TAX_SUMMARY.NATION_ID.in(nationIds))
                .and(allianceIds.size() == 1 ?
                        TAX_SUMMARY.ALLIANCE_ID.eq(allianceIds.iterator().next()) :
                        TAX_SUMMARY.ALLIANCE_ID.in(allianceIds))
                .fetchStream()) {
            Map<Integer, double[]> result = new Int2ObjectOpenHashMap<>();
            stream.forEach(rs -> {
                int nationId = rs.get(TAX_SUMMARY.NATION_ID);
                double[] added = result.computeIfAbsent(nationId, k -> ResourceType.getBuffer());
                ResourceType.add(added, ArrayUtil.toDoubleArray(rs.get(TAX_SUMMARY.NO_INTERNAL_UNAPPLIED)));
                ResourceType.add(added, ArrayUtil.toDoubleArray(rs.get(TAX_SUMMARY.INTERNAL_UNAPPLIED)));
            });
            return result;
        }
    }

    private void checkUpdateTaxSummary(Set<Integer> allianceIds, int[] taxBase) {
        short guildTaxPair = MathMan.pairByte(taxBase[0], taxBase[1]);
        synchronized (lastTaxSummaryUpdateByAlliance) {
            // determine which alliances need to load record dates or summaries, or update

            Set<Integer> loadTaxBase = new IntArraySet();
            {
                for (int allianceId : allianceIds) {
                    if (!lastTaxBaseByAlliance.containsKey(allianceId)) {
                        loadTaxBase.add(allianceId);
                    }
                }
                if (!loadTaxBase.isEmpty()) {
                    try (Stream<Record2<Integer, Integer>> stream = ctx()
                            .selectDistinct(TAX_SUMMARY.ALLIANCE_ID, TAX_SUMMARY.TAX_BASE)
                            .from(TAX_SUMMARY)
                            .where(TAX_SUMMARY.ALLIANCE_ID.in(loadTaxBase))
                            .fetchStream()) {
                        stream.forEach(r ->
                                lastTaxBaseByAlliance.put(
                                        r.get(TAX_SUMMARY.ALLIANCE_ID),
                                        r.get(TAX_SUMMARY.TAX_BASE).shortValue()
                                )
                        );
                    }
                    for (int allianceId : loadTaxBase) {
                        if (!lastTaxBaseByAlliance.containsKey(allianceId)) {
                            lastTaxBaseByAlliance.put(allianceId, guildTaxPair);
                        }
                    }
                }
            }

            Set<Integer> purgeSummary = new IntArraySet();

            Set<Integer> toLoadRecordDate = new IntArraySet();
            Set<Integer> toLoadSummary = new IntArraySet();
            Set<Integer> toUpdate = new IntArraySet();

            for (int allianceId : allianceIds) {
                short lastTaxBase = lastTaxBaseByAlliance.getOrDefault(allianceId, (short) -1);
                // if tax base doesn't match, clear the lastTaxSummaryUpdateByAlliance
                if ((lastTaxBase == 25700 ? -1 : lastTaxBase) != (guildTaxPair == 25700 ? -1 : guildTaxPair)) {
                    lastTaxSummaryUpdateByAlliance.remove(allianceId);
                    purgeSummary.add(allianceId);
                }

                long lastTaxSummaryUpdate = lastTaxSummaryUpdateByAlliance.getOrDefault(allianceId, Long.MIN_VALUE);
                long lastTaxRecordDate = lastTaxRecordDateByAlliance.getOrDefault(allianceId, Long.MIN_VALUE);

                if (lastTaxSummaryUpdate == Long.MIN_VALUE) {
                    toLoadSummary.add(allianceId);
                } else if (lastTaxSummaryUpdate < lastTaxRecordDate) {
                    toUpdate.add(allianceId);
                } else {
                }
                if (lastTaxRecordDate == Long.MIN_VALUE) {
                    toLoadRecordDate.add(allianceId);
                }
            }
            if (!purgeSummary.isEmpty()) {
                ctx().deleteFrom(TAX_SUMMARY)
                        .where(purgeSummary.size() == 1 ?
                                TAX_SUMMARY.ALLIANCE_ID.eq(purgeSummary.iterator().next()) :
                                TAX_SUMMARY.ALLIANCE_ID.in(purgeSummary))
                        .execute();
            }
            if (!toLoadRecordDate.isEmpty()) {
                loadRecordDate(toLoadRecordDate, toUpdate);
            }
            if (!toLoadSummary.isEmpty()) {
                loadSummaryDate(toLoadSummary, toUpdate);
            }

            if (toUpdate.isEmpty()) {
                return;
            }

            // alliance -> nation -> TaxRecordSummary
            Map<Integer, Map<Integer, TaxRecordSummary>> summaryByAlliance = new Int2ObjectOpenHashMap<>();
            { // Load the summaries from TAX_SUMMARY
                try (Stream<TaxSummaryRecord> stream =
                             ctx().selectFrom(TAX_SUMMARY)
                                     .where(toUpdate.size() == 1 ?
                                                TAX_SUMMARY.ALLIANCE_ID.eq(toUpdate.iterator().next()) :
                                                TAX_SUMMARY.ALLIANCE_ID.in(toUpdate))
                                     .fetchStream()) {
                    stream.forEach(rs -> {
                        int aid = rs.getAllianceId();
                        int nid = rs.getNationId();
                        short taxPair = rs.getTaxBase().shortValue();
                        double[] noInternalApplied = ArrayUtil.toDoubleArray(rs.getNoInternalApplied());
                        double[] noInternalUnapplied = ArrayUtil.toDoubleArray(rs.getNoInternalUnapplied());
                        double[] internalApplied = ArrayUtil.toDoubleArray(rs.getInternalApplied());
                        double[] internalUnappl = ArrayUtil.toDoubleArray(rs.getInternalUnapplied());
                        summaryByAlliance
                                .computeIfAbsent(aid, k -> new Int2ObjectOpenHashMap<>())
                                .put(nid, new TaxRecordSummary(taxPair, noInternalApplied, noInternalUnapplied, internalApplied, internalUnappl, false));
                    });
                }
            }

            Map<Integer, Long> newTaxSummaryUpdateByAlliance = new Int2LongOpenHashMap();
            boolean[] hasUpdated = {false};

            Consumer<TaxDepositsDateRecord> apply = record -> {
                int aid = record.getAlliance();
                long   date = record.getDate();
                if (date > 1656153134000L && date < 1657449182000L) {
                    date = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(date));
                }
                long lastSum = lastTaxSummaryUpdateByAlliance.getOrDefault(aid, Long.MIN_VALUE);
                if (date <= lastSum) return;
                Map<Integer, TaxRecordSummary> natMap = summaryByAlliance.computeIfAbsent(aid, k -> new Int2ObjectOpenHashMap<>());
                TaxRecordSummary natSummary = natMap.computeIfAbsent(record.getNation(), k -> new TaxRecordSummary(
                        guildTaxPair,
                        ResourceType.getBuffer(),
                        ResourceType.getBuffer(),
                        ResourceType.getBuffer(),
                        ResourceType.getBuffer(),
                        true
                ));
                short internalPair = record.getInternalTaxrate().shortValue();
                byte internalMoneyRate = MathMan.unpairShortX(internalPair);
                byte internalResourceRate = MathMan.unpairShortY(internalPair);
                double[] deposit = ArrayUtil.toDoubleArrayCents(record.getResources());

                newTaxSummaryUpdateByAlliance.merge(aid, date, Math::max);
                natSummary.dirty = true;
                hasUpdated[0] = true;

                if (ResourceType.isZero(deposit)) {
                    // no resources, nothing to do
                    return;
                }

                // double[] no_internal, double[] internal_applied, double[] internal_unapplied, boolean dirty
                if (internalMoneyRate < 0 || internalMoneyRate > 100) {
                    int moneyRate = taxBase[0];
                    double pct = record.getMoneyrate() > moneyRate ?
                            Math.max(0, (record.getMoneyrate() - moneyRate) / (double) record.getMoneyrate()) : 0;
                    natSummary.no_internal_applied[ResourceType.MONEY.ordinal()] += deposit[ResourceType.MONEY.ordinal()] * pct;
                    natSummary.no_internal_unapplied[ResourceType.MONEY.ordinal()] += deposit[ResourceType.MONEY.ordinal()];
                } else {
                    // add money to internal_applied AND internal_unapplied
                    // unapplied is the raw amount, applied is the amount calculating the internal rate
                    double pct = record.getMoneyrate() > internalMoneyRate ? Math.max(0, (record.getMoneyrate() - internalMoneyRate) / (double) record.getMoneyrate()) : 0;
                    natSummary.internal_applied[ResourceType.MONEY.ordinal()] += deposit[ResourceType.MONEY.ordinal()] * pct;
                    natSummary.internal_unapplied[ResourceType.MONEY.ordinal()] += deposit[ResourceType.MONEY.ordinal()];
                }
                if (internalResourceRate < 0 || internalResourceRate > 100) {
                    double rssRate = taxBase[1];
                    double pct = record.getResoucerate() > rssRate ? Math.max(0, (record.getResoucerate() - rssRate) / (double) record.getResoucerate()) : 0;
                    for (ResourceType type : ResourceType.values()) {
                        if (type == ResourceType.MONEY) continue;
                        natSummary.no_internal_applied[type.ordinal()] += deposit[type.ordinal()] * pct;
                        natSummary.no_internal_unapplied[type.ordinal()] += deposit[type.ordinal()];
                    }
                } else {
                    // add resources (not money) to internal_applied AND internal_unapplied
                    // unapplied is the raw amount, applied is the amount calculating the internal rate
                    double pct = record.getResoucerate() > internalResourceRate ? Math.max(0, (record.getResoucerate() - internalResourceRate) / (double) record.getResoucerate()) : 0;
                    for (ResourceType type : ResourceType.values()) {
                        if (type == ResourceType.MONEY) continue;
                        natSummary.internal_applied[type.ordinal()] += deposit[type.ordinal()] * pct;
                        natSummary.internal_unapplied[type.ordinal()] += deposit[type.ordinal()];
                    }
                }
            };

            // The alliance ids where a value >0 for date is present in lastTaxSummaryUpdateByAlliance
            Set<Integer> hasDate = new IntOpenHashSet();
            long earliestDate = Long.MAX_VALUE;

            // the alliance ids where no date is present or its = 0
            Set<Integer> hasNoDate = new IntOpenHashSet();

            for (int aid : toUpdate) {
                long date = lastTaxSummaryUpdateByAlliance.getOrDefault(aid, -1L);
                if (date > 0) {
                    hasDate.add(aid);
                    earliestDate = Math.min(earliestDate, date);
                } else {
                    hasNoDate.add(aid);
                }
            }

            if (!hasDate.isEmpty()) {
                SelectConditionStep<TaxDepositsDateRecord> where = ctx()
                        .selectFrom(TAX_DEPOSITS_DATE)
                        .where(
                                hasDate.size() == 1
                                        ? TAX_DEPOSITS_DATE.ALLIANCE.eq(hasDate.iterator().next())
                                        : TAX_DEPOSITS_DATE.ALLIANCE.in(hasDate)
                        )
                        .and(TAX_DEPOSITS_DATE.DATE.gt(earliestDate));

                try (Stream<TaxDepositsDateRecord> stream = where.fetchStream()) {
                    stream.forEach(apply);
                }
            }

            if (!hasNoDate.isEmpty()) {
                SelectConditionStep<TaxDepositsDateRecord> where = ctx()
                        .selectFrom(TAX_DEPOSITS_DATE)
                        .where(
                                hasNoDate.size() == 1
                                        ? TAX_DEPOSITS_DATE.ALLIANCE.eq(hasNoDate.iterator().next())
                                        : TAX_DEPOSITS_DATE.ALLIANCE.in(hasNoDate)
                        );

                try (Stream<TaxDepositsDateRecord> stream = where.fetchStream()) {
                    stream.forEach(apply);
                }
            }

            lastTaxSummaryUpdateByAlliance.putAll(newTaxSummaryUpdateByAlliance);

            if (hasUpdated[0]) {
                var insert = ctx().insertInto(TAX_SUMMARY)
                        .columns(
                                TAX_SUMMARY.ALLIANCE_ID,
                                TAX_SUMMARY.NATION_ID,
                                TAX_SUMMARY.TAX_BASE,
                                TAX_SUMMARY.DATE,
                                TAX_SUMMARY.NO_INTERNAL_APPLIED,
                                TAX_SUMMARY.NO_INTERNAL_UNAPPLIED,
                                TAX_SUMMARY.INTERNAL_APPLIED,
                                TAX_SUMMARY.INTERNAL_UNAPPLIED
                        );
                // save the new summaries to the TAX_SUMMARY table
                // set the lastTaxSummaryUpdateByAlliance for each alliance to the new value
                for (var allianceEntry : summaryByAlliance.entrySet()) {
                    int allianceId = allianceEntry.getKey();
                    long   date      = newTaxSummaryUpdateByAlliance.get(allianceId);

                    for (var nationEntry : allianceEntry.getValue().entrySet()) {
                        int               nationId = nationEntry.getKey();
                        TaxRecordSummary  summary  = nationEntry.getValue();
                        if (!summary.dirty) continue;

                        insert = insert.values(
                                allianceId,
                                nationId,
                                (int) guildTaxPair,
                                date,
                                ArrayUtil.toByteArray(summary.no_internal_applied),
                                ArrayUtil.toByteArray(summary.no_internal_unapplied),
                                ArrayUtil.toByteArray(summary.internal_applied),
                                ArrayUtil.toByteArray(summary.internal_unapplied)
                        );
                    }
                }

                synchronized (this) {
                    insert.onConflict(TAX_SUMMARY.ALLIANCE_ID, TAX_SUMMARY.NATION_ID)
                            .doUpdate()
                            .set(TAX_SUMMARY.TAX_BASE, DSL.excluded(TAX_SUMMARY.TAX_BASE))
                            .set(TAX_SUMMARY.DATE, DSL.excluded(TAX_SUMMARY.DATE))
                            .set(TAX_SUMMARY.NO_INTERNAL_APPLIED, DSL.excluded(TAX_SUMMARY.NO_INTERNAL_APPLIED))
                            .set(TAX_SUMMARY.NO_INTERNAL_UNAPPLIED, DSL.excluded(TAX_SUMMARY.NO_INTERNAL_UNAPPLIED))
                            .set(TAX_SUMMARY.INTERNAL_APPLIED, DSL.excluded(TAX_SUMMARY.INTERNAL_APPLIED))
                            .set(TAX_SUMMARY.INTERNAL_UNAPPLIED, DSL.excluded(TAX_SUMMARY.INTERNAL_UNAPPLIED))
                            .execute();
                }
            }
        }
    }


    public Transaction2 getLatestTransaction() {
        List<Transaction2> latestList = getTransactions(null, TRANSACTIONS_2.TX_ID.desc(), 1);
        return latestList.isEmpty() ? null : latestList.get(0);
    }

    public void updateBankRecs(int nationId, boolean priority, Consumer<Event> eventConsumer) {
        PoliticsAndWarV3 v3 = Locutus.imp().getApiPool();

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

    public void updateBankRecsAuto(Set<Integer> nations, boolean priority, Consumer<Event> eventConsumer) {
        if (Settings.INSTANCE.TASKS.BANK_RECORDS_INTERVAL_SECONDS > 0) {
            updateBankRecs(priority, eventConsumer);
        } else {
            for (int nationId : nations) {
                updateBankRecs(nationId, priority, eventConsumer);
            }
        }
    }

    public void updateBankRecs(boolean priority, Consumer<Event> eventConsumer) {
        ByteBuffer info = Locutus.imp().getDiscordDB().getInfo(DiscordMeta.BANK_RECS_SEQUENTIAL, 0);
        int latestId = info == null ? -1 : info.getInt();

        PoliticsAndWarV3 v3 = Locutus.imp().getApiPool();

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

        Predicate<Transaction2> filter = Predicates.alwaysTrue();

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

        if (checkAlliance && legacyExists) {
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
        return results;
    }

    public void deleteLegacyAllianceTransactions(int allianceId, long minDate) {
        try {
            if (legacyExists && tableExists("TRANSACTIONS_ALLIANCE_2")) {
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

    public Map<Integer, List<Transaction2>> getNationTransfersByNation(long start, long end, Set<Integer> nationIds) {
        Map<Integer, List<Transaction2>> result = new Int2ObjectOpenHashMap<>();
        iterateNationTransfersByNation(start, end, nationIds, (nationId, transfer) -> {
            result.computeIfAbsent(nationId, k -> new ArrayList<>()).add(transfer);
        }, true);
        return result;
    }

    public void iterateNationTransfersByNation(long start, long end, Set<Integer> nationIds, BiConsumer<Integer, Transaction2> consumer, boolean ordered) {
        if (nationIds.isEmpty()) {
            return; // no nations, no transfers
        }

        Consumer<SelectGroupByStep<Transactions_2Record>> handleFetch = sel -> {
            int fetchSize = nationIds.size() > 1 ? 4096 : 512;
            @NotNull ResultQuery<Transactions_2Record> preCursor = (ordered ? sel.orderBy(TRANSACTIONS_2.TX_ID.desc()) : sel).fetchSize(fetchSize).poolable(true);
            if (!ordered) {
                preCursor = preCursor.resultSetConcurrency(ResultSet.CONCUR_READ_ONLY);
            }
            try (Stream<Transactions_2Record> recordStream = preCursor.fetchStream()) {
                Stream<Transactions_2Record> workStream = ordered
                        ? recordStream
                        : recordStream.parallel();

                BiConsumer<Integer, Transaction2> consumerFinal = ordered ? consumer : (nationId, transfer) -> {
                    synchronized (consumer) {
                        consumer.accept(nationId, transfer);
                    }
                };
                workStream.forEach(record -> {
                    Transaction2 transfer = Transaction2.fromTX2Table((Transactions_2Record) record);
                    int nationId;
                    if (transfer.sender_type == 1) {
                        if (transfer.receiver_type == 1) {
                            return; // ignore because nation to nation transfer
                        }
                        nationId = (int) transfer.sender_id;
                    } else if (transfer.receiver_type == 1) {
                        nationId = (int) transfer.receiver_id;
                    } else {
                        return; // not a nation transfer
                    }
                    consumerFinal.accept(nationId, transfer);
                });
            }
        };

        Condition condition = null;
        if (start > 0) {
            condition = TRANSACTIONS_2.TX_DATETIME.ge(start);
        }
        if (end != Long.MAX_VALUE) {
            condition = and(condition, TRANSACTIONS_2.TX_DATETIME.le(end));
        }

        if (nationIds.size() == 1) {
            int nationId = nationIds.iterator().next();
            condition = and(condition, DSL.or(
                    TRANSACTIONS_2.RECEIVER_ID.eq((long) nationId).and(TRANSACTIONS_2.RECEIVER_TYPE.eq(1)),
                    TRANSACTIONS_2.SENDER_ID.eq((long) nationId).and(TRANSACTIONS_2.SENDER_TYPE.eq(1))
            ));
        } else {
            List<Integer> nationIdsSorted = new IntArrayList(nationIds);
            nationIdsSorted.sort(Comparator.naturalOrder());
            if (!ordered) {
                // do multiple fetches to avoid index issues from the OR IN combination below

                @NotNull Condition condition1 = and(condition, (TRANSACTIONS_2.RECEIVER_TYPE.eq(1)))
                        .and(TRANSACTIONS_2.RECEIVER_ID.in(nationIdsSorted));
                @NotNull Condition condition2 = and(condition, (TRANSACTIONS_2.SENDER_TYPE.eq(1)))
                        .and(TRANSACTIONS_2.SENDER_ID.in(nationIdsSorted));

                Consumer<Condition> fetchConsumer = cond -> {
                    @NotNull SelectGroupByStep<Transactions_2Record> sel = ctx().selectFrom(TRANSACTIONS_2)
                            .where(cond);
                    handleFetch.accept(sel);
                };
                fetchConsumer.accept(condition1);
                fetchConsumer.accept(condition2);

                return;
            }


            condition = and(condition, DSL.or(
                    TRANSACTIONS_2.RECEIVER_ID.in(nationIdsSorted).and(TRANSACTIONS_2.RECEIVER_TYPE.eq(1)),
                    TRANSACTIONS_2.SENDER_ID.in(nationIdsSorted).and(TRANSACTIONS_2.SENDER_TYPE.eq(1))
            ));
        }

        @NotNull SelectGroupByStep<Transactions_2Record> sel = ctx().selectFrom(TRANSACTIONS_2)
                .where(condition);
        handleFetch.accept(sel);
    }

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

    public Transaction2 getLatestDeposit(int id, int type) {
        Condition condition = TRANSACTIONS_2.SENDER_ID.eq((long) id).and(TRANSACTIONS_2.SENDER_TYPE.eq(type).and(TRANSACTIONS_2.RECEIVER_TYPE.eq(2)));
        List<Transaction2> transactions = getTransactions(condition, TRANSACTIONS_2.TX_ID.desc(), 1);
        return transactions.isEmpty() ? null : transactions.getFirst();
    }

    public Transaction2 getLatestWithdrawal(int id, int type) {
        Condition condition = TRANSACTIONS_2.RECEIVER_ID.eq((long) id).and(TRANSACTIONS_2.RECEIVER_TYPE.eq(type).and(TRANSACTIONS_2.SENDER_TYPE.eq(2)));
        List<Transaction2> transactions = getTransactions(condition, TRANSACTIONS_2.TX_ID.desc(), 1);
        return transactions.isEmpty() ? null : transactions.getFirst();
    }

    public Transaction2 getLatestSelfWithdrawal(int nationId) {
        Condition condition = TRANSACTIONS_2.RECEIVER_ID.eq((long) nationId).and(TRANSACTIONS_2.RECEIVER_TYPE.eq(1))
                .and(TRANSACTIONS_2.SENDER_TYPE.eq(2))
                .and(lower(TRANSACTIONS_2.NOTE).like("%#banker=" + nationId + "%"));
        List<Transaction2> transactions = getTransactions(condition, TRANSACTIONS_2.TX_ID.desc(), 1);
        return transactions.isEmpty() ? null : transactions.getFirst();
    }

    public List<Transaction2> getTransactionsByNation(int nation, long start, long end) {
        if (start < 0) start = 0;
        Condition condition = DSL.or(
                TRANSACTIONS_2.SENDER_ID.eq((long) nation).and(TRANSACTIONS_2.SENDER_TYPE.eq(1)),
                TRANSACTIONS_2.RECEIVER_ID.eq((long) nation).and(TRANSACTIONS_2.RECEIVER_TYPE.eq(1))
        );
        if (start > 0) {
            condition = condition.and(TRANSACTIONS_2.TX_DATETIME.ge(start));
        }
        if (end != Long.MAX_VALUE) {
            condition = condition.and(TRANSACTIONS_2.TX_DATETIME.le(end));
        }
        return getTransactions(condition, TRANSACTIONS_2.TX_ID.desc(), null);
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
        Set<Integer> set = new IntOpenHashSet();
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
        synchronized (transactionCache2) {
            if (!transactionCache2.isEmpty()) {
                for (int i = 0; i < transactions.size(); i++) {
                    if (result[i] <= 0) continue;
                    cache(transactions.get(i));
                }
            }
        }
        return result;
    }

    private void cache(Transaction2 tx) {
        if (tx.sender_type != 2 || tx.receiver_type != 2) return;
        Set<Transaction2> existingSet = transactionCache2.get((int) tx.receiver_id);
        if (existingSet != null) {
            existingSet.add(tx);
        }
    }

    public int addTransaction(Transaction2 tx, boolean ignoreInto) {
        return addTransactions(Collections.singletonList(tx), ignoreInto)[0];
    }

    public List<TaxDeposit> getTaxesPaid(int nation, int alliance) {
        List<TaxDeposit> list = new ObjectArrayList<>();
        ctx().selectFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.ALLIANCE.eq(alliance).and(TAX_DEPOSITS_DATE.NATION.eq(nation))).fetch().forEach(rs -> list.add(TaxDeposit.of(rs)));
        return list;
    }

    public void iterateTaxesPaid(Set<Integer> nationIds, Set<Integer> alliances, boolean includeNoInternal, boolean includeMaxInternal, long start, long end, Consumer<TaxDeposit> consumer) {
        if (nationIds.isEmpty()) return;

        @NotNull SelectWhereStep<TaxDepositsDateRecord> select = ctx().selectFrom(TAX_DEPOSITS_DATE);
        Condition where = nationIds.size() == 1 ?
                TAX_DEPOSITS_DATE.NATION.eq(nationIds.iterator().next()) :
                TAX_DEPOSITS_DATE.NATION.in(nationIds);
        if (!includeNoInternal) {
            where = where.and(TAX_DEPOSITS_DATE.INTERNAL_TAXRATE.ne(-1));
        }
        if (!includeMaxInternal) {
            where = where.and(TAX_DEPOSITS_DATE.INTERNAL_TAXRATE.ne((int) MathMan.pairByte(100, 100)));
        }
        if (start > 0) {
            where = where.and(TAX_DEPOSITS_DATE.DATE.ge(start));
        }
        if (end != Long.MAX_VALUE) {
            where = where.and(TAX_DEPOSITS_DATE.DATE.le(end));
        }
        if (alliances.size() == 1) {
            where = where.and(TAX_DEPOSITS_DATE.ALLIANCE.eq(alliances.iterator().next()));
        } else if (alliances.size() > 1) {
            where = where.and(TAX_DEPOSITS_DATE.ALLIANCE.in(alliances));
        }

        try (Stream<TaxDepositsDateRecord> stream = select.where(where).stream()) {
            stream.forEach(rs -> {
                consumer.accept(TaxDeposit.of(rs));
            });
        }
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
            int id = idsSorted.iterator().next();
            ctx().selectFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.TAX_ID.eq(id)).fetch().forEach(rs -> list.add(TaxDeposit.of(rs)));
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
        return getTaxesByBracket(tax_id, 0, Long.MAX_VALUE);
    }

    public List<TaxDeposit> getTaxesByBracket(int tax_id, long start, long end) {
        List<TaxDeposit> list = new ObjectArrayList<>();
        @NotNull SelectConditionStep<TaxDepositsDateRecord> condition = ctx().selectFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.TAX_ID.eq(tax_id));
        if (start > 0) {
            condition = condition.and(TAX_DEPOSITS_DATE.DATE.ge(start));
        }
        if (end != Long.MAX_VALUE) {
            condition = condition.and(TAX_DEPOSITS_DATE.DATE.le(end));
        }
        condition.fetch().forEach(rs -> list.add(TaxDeposit.of(rs)));
        return list;
    }

    public List<TaxDeposit> getTaxesPaid(int nation) {
        List<TaxDeposit> list = new ObjectArrayList<>();
        try (Stream<TaxDepositsDateRecord> stream = ctx().selectFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.NATION.eq(nation)).stream()) {
            stream.forEach(rs -> list.add(TaxDeposit.of(rs)));
        }
        return list;
    }

    public List<TaxDeposit> getTaxesByAA(int alliance) {
        List<TaxDeposit> list = new ObjectArrayList<>();
        try (Stream<TaxDepositsDateRecord> stream = ctx().selectFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.ALLIANCE.eq(alliance)).stream()) {
            stream.forEach(rs -> list.add(TaxDeposit.of(rs)));
        }
        return list;
    }

    public List<TaxDeposit> getTaxesByAA(Set<Integer> allianceIds) {
        if (allianceIds.isEmpty()) return new ObjectArrayList<>();
        if (allianceIds.size() == 1) {
            return getTaxesByAA(allianceIds.iterator().next());
        }
        List<TaxDeposit> list = new ObjectArrayList<>();
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
        ctx().deleteFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.ALLIANCE.eq(allianceId).and(TAX_DEPOSITS_DATE.DATE.ge(date))).execute();
        ctx().deleteFrom(TAX_SUMMARY).where(TAX_SUMMARY.ALLIANCE_ID.eq(allianceId).and(TAX_SUMMARY.DATE.ge(date))).execute();
        synchronized (lastTaxSummaryUpdateByAlliance) {
            lastTaxSummaryUpdateByAlliance.remove(allianceId);
        }
    }

    public synchronized void addTaxDeposits(Collection<TaxDeposit> records) {
        if (records.isEmpty()) return;

        List<TaxDepositsDateRecord> dbRecords = new ObjectArrayList<>(records.size());
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
            for (int i = 0; i < deposit.length; i++) {
                depositCents[i] = (long) (deposit[i] * 100);
            }
            byte[] depositBytes = ArrayUtil.toByteArray(depositCents);
            dbRecord.setResources(depositBytes);

            int internalMoneyRate = record.internalMoneyRate >= record.moneyRate ? 100 : record.internalMoneyRate;
            int internalResourceRate = record.internalResourceRate >= record.resourceRate ? 100 : record.internalResourceRate;
            short internalPair = MathMan.pairByte(internalMoneyRate, internalResourceRate);
            dbRecord.setInternalTaxrate((int) internalPair);
            dbRecord.setTaxId(record.tax_id);

            dbRecords.add(dbRecord);
            synchronized (lastTaxSummaryUpdateByAlliance) {
                long prev = lastTaxRecordDateByAlliance.getOrDefault(record.allianceId, Long.MIN_VALUE);
                long updated = Math.max(prev, record.date);
                lastTaxRecordDateByAlliance.put(record.allianceId, updated);
            }
        }
        if (dbRecords.size() != 1) {
            try {
                ctx().loadInto(TAX_DEPOSITS_DATE)
                        .onDuplicateKeyIgnore()
                        .loadRecords(dbRecords)
                        .fields(
                                TAX_DEPOSITS_DATE.TAX_ID,
                                TAX_DEPOSITS_DATE.ALLIANCE,
                                TAX_DEPOSITS_DATE.DATE,
                                TAX_DEPOSITS_DATE.ID,
                                TAX_DEPOSITS_DATE.NATION,
                                TAX_DEPOSITS_DATE.MONEYRATE,
                                TAX_DEPOSITS_DATE.RESOUCERATE,
                                TAX_DEPOSITS_DATE.RESOURCES,
                                TAX_DEPOSITS_DATE.INTERNAL_TAXRATE
                        )
                        .execute();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            TaxDepositsDateRecord record = dbRecords.get(0);
            ctx().insertInto(TAX_DEPOSITS_DATE)
                    .set(record)
                    .onDuplicateKeyIgnore()
                    .execute();
        }
    }

    public void clearTaxDeposits(int allianceId) {
        ctx().deleteFrom(TAX_DEPOSITS_DATE).where(TAX_DEPOSITS_DATE.ALLIANCE.eq(allianceId)).execute();
        ctx().deleteFrom(TAX_SUMMARY).where(TAX_SUMMARY.ALLIANCE_ID.eq(allianceId)).execute();
        synchronized (lastTaxSummaryUpdateByAlliance) {
            lastTaxSummaryUpdateByAlliance.remove(allianceId);
        }
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
        public static BankSubType of(boolean isAA) {
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
        Set<Subscription> list = new ObjectLinkedOpenHashSet<>();

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
        Set<Subscription> list = new ObjectLinkedOpenHashSet<>();

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

    public List<Transaction2> getAllianceTransactions(Set<Integer> receiverAAs, boolean includeLegacy, Predicate<Transaction2> filter) {
        List<Transaction2> result2 = new ObjectArrayList<>();
        if (receiverAAs.isEmpty()) return result2;
        List<Integer> remaining = new IntArrayList();
        synchronized (transactionCache2) {
            for (int id : receiverAAs) {
                Set<Transaction2> existing = transactionCache2.get(id);
                if (existing != null) {
                    for (Transaction2 tx : existing) {
                        if (filter.test(tx)) {
                            result2.add(tx);
                        }
                    }
                } else {
                    transactionCache2.computeIfAbsent(id, k -> new ObjectOpenHashSet<>());
                    remaining.add(id);
                }
            }
        }
        if (remaining.isEmpty()) {
            return result2;
        }

        remaining.sort(Comparator.naturalOrder());
        // condition = sender type = 2, receiver type = 2, receiver id in receiverAAs
        Condition condition = TRANSACTIONS_2.SENDER_TYPE.eq(2).and(TRANSACTIONS_2.RECEIVER_TYPE.eq(2));
        if (receiverAAs.size() == 1) {
            condition = condition.and(TRANSACTIONS_2.RECEIVER_ID.eq((long) receiverAAs.iterator().next()));
        } else {
            condition = condition.and(TRANSACTIONS_2.RECEIVER_ID.in(remaining));
        }

        Consumer<Transaction2> addToCache = tx -> {
            transactionCache2.computeIfAbsent((int) tx.receiver_id, k -> new ObjectOpenHashSet<>()).add(tx);
        };

        List<Transaction2> modern = getTransactions(condition);
        for (Transaction2 tx : modern) {
            if (filter.test(tx)) {
                result2.add(tx);
            }
        }
        synchronized (transactionCache2) {
            for (Transaction2 tx : modern) {
                addToCache.accept(tx);
            }
        }

        if (includeLegacy && legacyExists) {
            List<Transaction2> legacy = new ObjectArrayList<>();
            String inOrEqual = remaining.size() == 1 ? " = " + remaining.get(0) : " IN " + StringMan.getString(remaining);
            String query = "select * FROM TRANSACTIONS_ALLIANCE_2 WHERE ((sender_type = ? AND receiver_type = ? AND receiver_id " + inOrEqual + "))";
            queryLegacy(query, new ThrowingConsumer<PreparedStatement>() {
                @Override
                public void acceptThrows(PreparedStatement stmt) throws Exception {
                    stmt.setLong(1, 2);
                    stmt.setLong(2, 2);
                }
            }, (ThrowingConsumer<ResultSet>) rs -> {
                while (rs.next()) {
                    legacy.add(new Transaction2(rs));
                }
            });
            for (Transaction2 tx : legacy) {
                if (filter.test(tx)) {
                    result2.add(tx);
                }
            }
            synchronized (transactionCache2) {
                for (Transaction2 tx : legacy) {
                    addToCache.accept(tx);
                }
            }
        }
        return result2;
    }

//    private void cache(Transaction2 tx) {
//        if (shouldCache(tx)) {
//            synchronized (transactionCache2) {
//                Set<Transaction2> existing = transactionCache2.get(tx.receiver_id);
//                if (existing != null) {
//                    existing.add(tx);
//                }
//            }
//        }
//        if (shouldCache(tx)) {
//            synchronized (transactionCache) {
//                Set<Transaction2> existing = transactionCache.get(tx.receiver_id);
//                if (existing != null) {
//                    existing.add(tx);
//                }
//            }
//        }
//        if (tx.note != null) {
//            if (tx.note.contains("#")) {
//                try {
//                    if (StringMan.containsIgnoreCase(tx.note, "#guild")) {
//                        String idStr = PW.parseTransferHashNotes(tx.note).get("#guild");
//                        if (MathMan.isInteger(idStr)) {
//                            long id = Long.parseLong(idStr);
//                            if (id > Integer.MAX_VALUE) {
//                                synchronized (transactionCache) {
//                                    Set<Transaction2> existing = transactionCache.get(id);
//                                    if (existing != null) {
//                                        existing.add(tx);
//                                    }
//                                }
//                            }
//                        }
//                    }
//                    if (StringMan.containsIgnoreCase(tx.note, "#alliance")) {
//                        String idStr = PW.parseTransferHashNotes(tx.note).get("#alliance");
//                        if (MathMan.isInteger(idStr)) {
//                            long id = Long.parseLong(idStr);
//                            if (id < Integer.MAX_VALUE) {
//                                synchronized (transactionCache) {
//                                    Set<Transaction2> existing = transactionCache.get(id);
//                                    if (existing != null) {
//                                        existing.add(tx);
//                                    }
//                                }
//                            }
//                        }
//                    }
//                } catch (Throwable e) {
//                    e.printStackTrace();
//                    AlertUtil.error("Error parsing transaction note: " + tx.note, e);
//                }
//            }
//        }
//    }
//    public List<Transaction2> getBankTransactions(long senderOrReceiverId, int type) {
//        boolean cache = shouldCache(senderOrReceiverId, type);
//        if (cache) {
//            Set<Transaction2> cached = transactionCache.get(senderOrReceiverId);
//            if (cached != null) {
//                return new ArrayList<>(cached);
//            }
//        }
//        List<Transaction2> result = getBankTransactions(senderOrReceiverId, type, true, true);
//        if (cache) {
//            synchronized (transactionCache) {
//                if (transactionCache.containsKey(senderOrReceiverId)) {
//                    transactionCache.get(senderOrReceiverId).addAll(result);
//                } else {
//                    transactionCache.put(senderOrReceiverId, new ObjectLinkedOpenHashSet<>(result));
//                }
//            }
//        }
//        return new ArrayList<>(result);
//    }
//
//    private List<Transaction2> getBankTransactions(long senderOrReceiverId, int type, boolean includeLegacy, boolean includeModern) {
//        if (type == 1) return getTransactionsByNation((int) senderOrReceiverId);
//        if (type != 2 && type != 3) throw new IllegalArgumentException("Invalid type: " + type);
//
//        List<Transaction2> list = new ArrayList<>();
//
//        try {
//            if (includeLegacy && tableExists("TRANSACTIONS_ALLIANCE_2")) {
//
//                String query = "select * FROM TRANSACTIONS_ALLIANCE_2 WHERE ((sender_id = ? AND sender_TYPE = ?) OR (receiver_id = ? AND receiver_type = ?) OR (lower(note) like ?))";
//
//                queryLegacy(query, new ThrowingConsumer<PreparedStatement>() {
//                    @Override
//                    public void acceptThrows(PreparedStatement stmt) throws Exception {
//                        stmt.setLong(1, senderOrReceiverId);
//                        stmt.setInt(2, type);
//                        stmt.setLong(3, senderOrReceiverId);
//                        stmt.setInt(4, type);
//
//                        if (type == 3) stmt.setString(5, "%#guild=" + senderOrReceiverId + "%");
//                        else if (type == 2) stmt.setString(5, "%#alliance=" + senderOrReceiverId + "%");
//
//                    }
//                }, (ThrowingConsumer<ResultSet>) rs -> {
//                    while (rs.next()) {
//                        list.add(new Transaction2(rs));
//                    }
//                });
//            }
//
//            // ((sender_id = ? AND sender_TYPE = ?) OR (receiver_id = ? AND receiver_type = ?) OR (lower(note) like ?))";
//
//            if (includeModern) {
//                Condition noteCondition;
//                if (type == 3) {
//                    noteCondition = TRANSACTIONS_2.NOTE.likeIgnoreCase("%#guild=" + senderOrReceiverId + "%");
//                } else if (type == 2) {
//                    noteCondition = TRANSACTIONS_2.NOTE.likeIgnoreCase("%#alliance=" + senderOrReceiverId + "%");
//                } else throw new InvalidResultException("Invalid type " + type);
//                list.addAll(getTransactions(DSL.or(TRANSACTIONS_2.SENDER_ID.eq(senderOrReceiverId).and(TRANSACTIONS_2.SENDER_TYPE.eq(type)),
//                        TRANSACTIONS_2.RECEIVER_ID.eq(senderOrReceiverId).and(TRANSACTIONS_2.RECEIVER_TYPE.eq(type)),
//                        noteCondition)));
//            }
//        } catch (SQLException e) {
//            e.printStackTrace();
//            throw new RuntimeException(e);
//        }
//
//        return list;
//    }

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