package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.TaxDeposit;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.TransactionEndpointKey;
import link.locutus.discord.db.entities.WebTaxBracket;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.io.BitBuffer;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseBracket;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseBracketRow;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseBracketRows;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseNation;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseTime;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseTimeBracket;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseTimeBracketSummary;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseTimeCategory;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseTimeResources;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseTransactionRow;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenses;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONObject;

import java.lang.ref.SoftReference;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class TaxExpenseDatasets {
    enum FlowType {
        TAX(false),
        DEPOSIT(false),
        WITHDRAWAL(true),
        GRANT(true);

        final boolean isExpense;

        FlowType(boolean isExpense) {
            this.isExpense = isExpense;
        }
    }

    private static final int TOTAL_TAX_ID = -1;
    private static final long REQUEST_CACHE_TTL_MINUTES = 2L;
    private static final long DATASET_LOOKUP_TTL_HOURS = 12L;
    private static final AtomicInteger NEXT_DATASET_ID = new AtomicInteger(1);
    private static final Object SUMMARY_LOCK = new Object();
    private static final Object TIME_LOCK = new Object();
    // Keep request-key reuse fresh for "to now" queries, but retain dataset ids long enough to rebuild page state.
    private static final PassiveExpiringMap<SummaryRequestKey, SummaryDatasetSlot> SUMMARY_BY_KEY =
            new PassiveExpiringMap<>(REQUEST_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
    private static final PassiveExpiringMap<Integer, SummaryDatasetSlot> SUMMARY_BY_ID =
            new PassiveExpiringMap<>(DATASET_LOOKUP_TTL_HOURS, TimeUnit.HOURS);
    private static final PassiveExpiringMap<TimeRequestKey, TimeDatasetSlot> TIME_BY_KEY =
            new PassiveExpiringMap<>(REQUEST_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
    private static final PassiveExpiringMap<Integer, TimeDatasetSlot> TIME_BY_ID =
            new PassiveExpiringMap<>(DATASET_LOOKUP_TTL_HOURS, TimeUnit.HOURS);
    private static final FlowType[] FLOW_TYPES = FlowType.values();
    private static final Comparator<TaxExpenseBracketRow> BRACKET_ROW_ORDER =
            Comparator.<TaxExpenseBracketRow>comparingDouble(row -> Math.abs(row.netValue))
                    .reversed()
                    .thenComparing(Comparator.comparingDouble((TaxExpenseBracketRow row) -> row.netValue).reversed())
                    .thenComparingInt(row -> row.nationId);
    private static final Comparator<TaxExpenseTransactionRow> RECENT_TRANSACTION_ORDER =
            Comparator.comparingLong((TaxExpenseTransactionRow row) -> row.txDatetime)
                    .reversed()
                    .thenComparing(Comparator.comparingInt((TaxExpenseTransactionRow row) -> row.txId).reversed());
    private static final Comparator<TaxExpenseTimeBracketSummary> TIME_BRACKET_SUMMARY_ORDER =
            Comparator.<TaxExpenseTimeBracketSummary>comparingDouble(summary -> summary.incomeValue + summary.expenseValue)
                    .reversed()
                    .thenComparingInt(summary -> summary.taxId);
    private static final List<TaxExpenseTimeCategory> TIME_CATEGORIES = createTimeCategories();

    private TaxExpenseDatasets() {
    }

    static SummaryDataset getSummaryDataset(GuildDB db,
                                            @Nullable JSONObject command,
                                            long start,
                                            long end,
                                            @Nullable NationList nationList,
                                            boolean dontRequireGrant,
                                            boolean dontRequireTagged,
                                            boolean dontRequireExpiry,
                                            boolean includeDeposits) throws Exception {
        long loadEnd = resolveDatasetEnd(end);
        String filter = resolveFilter(command, "nationList", nationList);
        SummaryRequestKey key = new SummaryRequestKey(
                db.getIdLong(),
                start,
                end,
                dontRequireGrant,
                dontRequireTagged,
                dontRequireExpiry,
                includeDeposits,
                filter
        );
        NationSelection selection = createSelection(nationList);

        synchronized (SUMMARY_LOCK) {
            SummaryDatasetSlot cached = SUMMARY_BY_KEY.get(key);
            if (cached != null) {
                SummaryDataset dataset = cached.getOrBuild(db);
                rememberSummaryRequestSlot(cached);
                rememberSummaryIdSlot(cached);
                return dataset;
            }

            SummaryDatasetSlot created = new SummaryDatasetSlot(
                    key,
                    nextDatasetId(),
                    db.getIdLong(),
                    start,
                    loadEnd,
                    selection,
                    dontRequireGrant,
                    dontRequireTagged,
                    dontRequireExpiry,
                    includeDeposits
            );
            SummaryDataset dataset = created.getOrBuild(db);
            rememberSummaryRequestSlot(created);
            rememberSummaryIdSlot(created);
            return dataset;
        }
    }

    static SummaryDataset requireSummaryDataset(GuildDB db, int datasetId) throws Exception {
        synchronized (SUMMARY_LOCK) {
            SummaryDatasetSlot slot = SUMMARY_BY_ID.get(datasetId);
            if (slot == null || slot.guildId != db.getIdLong()) {
                throw new IllegalArgumentException("Tax expense dataset expired. Reload the summary page.");
            }
            SummaryDataset dataset = slot.getOrBuild(db);
            rememberSummaryIdSlot(slot);
            return dataset;
        }
    }

    static TimeDataset getTimeDataset(GuildDB db,
                                      @Nullable JSONObject command,
                                      long start,
                                      long end,
                                      @Nullable NationList nationFilter,
                                      boolean dontRequireGrant,
                                      boolean dontRequireTagged,
                                      boolean dontRequireExpiry,
                                      boolean includeDeposits) throws Exception {
        long loadEnd = resolveDatasetEnd(end);
        long turnStart = TimeUtil.getTurn(start);
        long turnEnd = resolveTurnEnd(loadEnd);
        if (turnEnd - turnStart > 365L * 12L) {
            throw new IllegalArgumentException("Timeframe is too large");
        }

        String filter = resolveFilter(command, "nationFilter", nationFilter);
        TimeRequestKey key = new TimeRequestKey(
                db.getIdLong(),
                start,
                end,
                dontRequireGrant,
                dontRequireTagged,
                dontRequireExpiry,
                includeDeposits,
                filter
        );
        NationSelection selection = createSelection(nationFilter);

        synchronized (TIME_LOCK) {
            TimeDatasetSlot cached = TIME_BY_KEY.get(key);
            if (cached != null) {
                TimeDataset dataset = cached.getOrBuild(db);
                rememberTimeRequestSlot(cached);
                rememberTimeIdSlot(cached);
                return dataset;
            }

            TimeDatasetSlot created = new TimeDatasetSlot(
                    key,
                    nextDatasetId(),
                    db.getIdLong(),
                    start,
                    loadEnd,
                    selection,
                    dontRequireGrant,
                    dontRequireTagged,
                    dontRequireExpiry,
                    includeDeposits,
                    turnStart,
                    turnEnd
            );
            TimeDataset dataset = created.getOrBuild(db);
            rememberTimeRequestSlot(created);
            rememberTimeIdSlot(created);
            return dataset;
        }
    }

    static TimeDataset requireTimeDataset(GuildDB db, int datasetId) throws Exception {
        synchronized (TIME_LOCK) {
            TimeDatasetSlot slot = TIME_BY_ID.get(datasetId);
            if (slot == null || slot.guildId != db.getIdLong()) {
                throw new IllegalArgumentException("Tax expense dataset expired. Reload the by-time page.");
            }
            TimeDataset dataset = slot.getOrBuild(db);
            rememberTimeIdSlot(slot);
            return dataset;
        }
    }

    private static void rememberSummaryRequestSlot(SummaryDatasetSlot slot) {
        SUMMARY_BY_KEY.put(slot.requestKey, slot);
    }

    private static void rememberSummaryIdSlot(SummaryDatasetSlot slot) {
        SUMMARY_BY_ID.put(slot.datasetId, slot);
    }

    private static void rememberTimeRequestSlot(TimeDatasetSlot slot) {
        TIME_BY_KEY.put(slot.requestKey, slot);
    }

    private static void rememberTimeIdSlot(TimeDatasetSlot slot) {
        TIME_BY_ID.put(slot.datasetId, slot);
    }

    private static SummaryDataset buildSummaryDataset(int datasetId,
                                                      GuildDB db,
                                                      long start,
                                                      long end,
                                                      NationSelection selection,
                                                      boolean dontRequireGrant,
                                                      boolean dontRequireTagged,
                                                      boolean dontRequireExpiry,
                                                      boolean includeDeposits) throws Exception {
        LoadedData loaded = loadData(db, start, end, selection, dontRequireGrant, dontRequireTagged, dontRequireExpiry, includeDeposits);

        Int2ObjectOpenHashMap<MutableSummarySection> sections = new Int2ObjectOpenHashMap<>();
        MutableSummarySection total = new MutableSummarySection(TOTAL_TAX_ID, null, loaded.allNationIds);
        sections.put(TOTAL_TAX_ID, total);

        for (TaxDeposit tax : loaded.taxes) {
            int currentTaxId = loaded.currentTaxIdByNation.get(tax.nationId);
            addSummaryResources(total, tax.nationId, currentTaxId, tax.resources, false, true, null);
            addSummaryResources(getOrCreateSummarySection(sections, loaded, tax.tax_id), tax.nationId, currentTaxId, tax.resources, false, true, null);
        }

        BitBuffer noteBuffer = Transaction2.createNoteBuffer();
        for (ResolvedTransfer transfer : loaded.transfers) {
            TaxExpenseTransactionRow row = new TaxExpenseTransactionRow(transfer.transaction, noteBuffer);
            int currentTaxId = loaded.currentTaxIdByNation.get(transfer.nationId);
            addSummaryResources(total, transfer.nationId, currentTaxId, transfer.transaction.resources, transfer.type.isExpense, false, row);
            addSummaryResources(
                    getOrCreateSummarySection(sections, loaded, transfer.taxId),
                    transfer.nationId,
                    currentTaxId,
                    transfer.transaction.resources,
                    transfer.type.isExpense,
                    false,
                    row
            );
        }

        Int2ObjectOpenHashMap<SummarySectionData> sectionsByTaxId = new Int2ObjectOpenHashMap<>();
        ObjectArrayList<TaxExpenseBracket> brackets = new ObjectArrayList<>();
        IntArrayList taxIds = new IntArrayList();
        for (int taxId : sections.keySet()) {
            if (taxId != TOTAL_TAX_ID) {
                taxIds.add(taxId);
            }
        }
        taxIds.sort(IntComparators.NATURAL_COMPARATOR);

        for (int i = 0; i < taxIds.size(); i++) {
            int taxId = taxIds.getInt(i);
            SummarySectionData section = freezeSummarySection(sections.get(taxId), loaded.currentTaxIdByNation);
            sectionsByTaxId.put(taxId, section);
            brackets.add(section.summary);
        }

        SummarySectionData totalSection = freezeSummarySection(total, loaded.currentTaxIdByNation);
        sectionsByTaxId.put(TOTAL_TAX_ID, totalSection);

        TaxExpenses response = new TaxExpenses(
                datasetId,
                totalSection.summary,
                brackets,
                new IntOpenHashSet(loaded.expenseAllianceIds),
                loaded.taxes.size()
        );
        return new SummaryDataset(datasetId, db.getIdLong(), response, sectionsByTaxId, loaded.currentTaxIdByNation);
    }

    private static TimeDataset buildTimeDataset(int datasetId,
                                                GuildDB db,
                                                long start,
                                                long end,
                                                NationSelection selection,
                                                boolean dontRequireGrant,
                                                boolean dontRequireTagged,
                                                boolean dontRequireExpiry,
                                                boolean includeDeposits,
                                                long turnStart,
                                                long turnEnd) throws Exception {
        LoadedData loaded = loadData(
                db,
                start,
                end,
                selection,
                dontRequireGrant,
                dontRequireTagged,
                dontRequireExpiry,
                includeDeposits
        );
        int turnCount = (int) (turnEnd - turnStart + 1L);

        double[][] totalOverallByCategory = new double[FLOW_TYPES.length][turnCount];
        double[][][] totalByResourceOrdinalByCategory = createResourceSeriesContainer(turnCount);
        double[] totalIncome = ResourceType.getBuffer();
        double[] totalExpense = ResourceType.getBuffer();
        Int2ObjectOpenHashMap<MutableTimeSection> sections = new Int2ObjectOpenHashMap<>();

        for (TaxDeposit tax : loaded.taxes) {
            int turnIndex = (int) (tax.getTurn() - turnStart);
            if (turnIndex < 0 || turnIndex >= turnCount) {
                continue;
            }

            addTimeSeriesValue(totalOverallByCategory, totalByResourceOrdinalByCategory, FlowType.TAX, turnIndex, tax.resources);
            ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, totalIncome, tax.resources);

            MutableTimeSection section = getOrCreateTimeSection(sections, loaded, tax.tax_id);
            ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, section.income, tax.resources);
            section.taxRecords.add(new TimedResources(turnIndex, tax.resources));
        }

        for (ResolvedTransfer transfer : loaded.transfers) {
            int turnIndex = (int) (TimeUtil.getTurn(transfer.transaction.tx_datetime) - turnStart);
            if (turnIndex < 0 || turnIndex >= turnCount) {
                continue;
            }

            addTimeSeriesValue(totalOverallByCategory, totalByResourceOrdinalByCategory, transfer.type, turnIndex, transfer.transaction.resources);
            MutableTimeSection section = getOrCreateTimeSection(sections, loaded, transfer.taxId);
            if (transfer.type.isExpense) {
                ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, totalExpense, transfer.transaction.resources);
                ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, section.expense, transfer.transaction.resources);
            } else {
                ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, totalIncome, transfer.transaction.resources);
                ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, section.income, transfer.transaction.resources);
            }
            section.transfers.add(new TimedTransfer(turnIndex, transfer.type, transfer.transaction.resources));
        }

        ObjectArrayList<TaxExpenseTimeBracketSummary> summaries = new ObjectArrayList<>();
        Int2ObjectOpenHashMap<TimeSectionData> sectionsByTaxId = new Int2ObjectOpenHashMap<>();
        for (Int2ObjectMap.Entry<MutableTimeSection> entry : sections.int2ObjectEntrySet()) {
            TimeSectionData section = freezeTimeSection(entry.getValue());
            sectionsByTaxId.put(entry.getIntKey(), section);
            summaries.add(section.summary);
        }
        summaries.sort(TIME_BRACKET_SUMMARY_ORDER);

        TaxExpenseTimeBracket total = new TaxExpenseTimeBracket(
                TOTAL_TAX_ID,
                null,
                loaded.allNationIds.size(),
                toConvertedValue(totalIncome),
                toConvertedValue(totalExpense),
                toConvertedValue(totalIncome) - toConvertedValue(totalExpense),
                totalOverallByCategory
        );
        TaxExpenseTime response = new TaxExpenseTime(
                datasetId,
                createTurnTimestamps(turnStart, turnEnd),
                new ObjectArrayList<>(TIME_CATEGORIES),
                total,
                summaries
        );
        TaxExpenseTimeResources resources = new TaxExpenseTimeResources(totalByResourceOrdinalByCategory);
        return new TimeDataset(datasetId, db.getIdLong(), response, resources, sectionsByTaxId, turnCount);
    }

    private static LoadedData loadData(GuildDB db,
                                       long start,
                                       long end,
                                       NationSelection selection,
                                       boolean dontRequireGrant,
                                       boolean dontRequireTagged,
                                       boolean dontRequireExpiry,
                                       boolean includeDeposits) throws Exception {
        CurrentBracketState state = loadCurrentBracketState(db, selection);
        ObjectArrayList<TaxDeposit> taxes = loadTaxes(db, state, selection, start, end);
        ObjectArrayList<ResolvedTransfer> transfers = loadTransfers(
                state,
                selection,
                start,
                end,
                dontRequireGrant,
                dontRequireTagged,
                dontRequireExpiry,
                includeDeposits
        );
        return new LoadedData(state, taxes, transfers);
    }

    private static CurrentBracketState loadCurrentBracketState(GuildDB db, NationSelection selection) {
        CurrentBracketState state = new CurrentBracketState();
        state.taxAllianceIds.addAll(db.getAllianceIds());
        state.expenseAllianceIds.addAll(state.taxAllianceIds);
        state.expenseAllianceIds.addAll(db.getCoalition(Coalition.OFFSHORE));

        for (DBNation nation : db.getAllianceList().getNations(Locutus.imp().getNationDB(), true, 0, true)) {
            int nationId = nation.getNation_id();
            if (selection.accepts(nationId)) {
                state.allNationIds.add(nationId);
            }
        }
        state.allNationIds.sort(IntComparators.NATURAL_COMPARATOR);

        for (Map.Entry<Integer, TaxBracket> entry : db.getAllianceList().getTaxBrackets(Locutus.imp().getNationDB(), TimeUnit.MINUTES.toMillis(60)).entrySet()) {
            int taxId = entry.getKey();
            TaxBracket bracket = entry.getValue();
            state.bracketsByTaxId.put(taxId, new WebTaxBracket(bracket));
            long pair = MathMan.pairInt(bracket.moneyRate, bracket.rssRate);
            if (!state.firstTaxIdByRatePair.containsKey(pair)) {
                state.firstTaxIdByRatePair.put(pair, taxId);
            }

            IntArrayList nationIds = new IntArrayList();
            for (DBNation nation : bracket.getNations()) {
                if (nation.getPosition() <= 1) {
                    continue;
                }
                int nationId = nation.getNation_id();
                if (!selection.accepts(nationId)) {
                    continue;
                }
                state.currentTaxIdByNation.put(nationId, taxId);
                nationIds.add(nationId);
            }
            nationIds.sort(IntComparators.NATURAL_COMPARATOR);
            state.currentNationIdsByTaxId.put(taxId, nationIds);
        }
        return state;
    }

    private static ObjectArrayList<TaxDeposit> loadTaxes(GuildDB db,
                                                         CurrentBracketState state,
                                                         NationSelection selection,
                                                         long start,
                                                         long end) {
        ObjectArrayList<TaxDeposit> taxes = new ObjectArrayList<>(Locutus.imp().getBankDB().getTaxesByAA(state.taxAllianceIds, start, end));
        taxes.removeIf(tax -> !selection.accepts(tax.nationId));

        Int2ObjectOpenHashMap<NationBracketTimelineBuilder> timelineBuilders = new Int2ObjectOpenHashMap<>();
        for (TaxDeposit tax : taxes) {
            tax.tax_id = resolveTaxId(state, tax);
            ensureBracket(state, tax);
            timelineBuilders.computeIfAbsent(tax.nationId, ignored -> new NationBracketTimelineBuilder())
                    .add(tax.getTurn(), tax.tax_id);
        }
        for (Int2ObjectMap.Entry<NationBracketTimelineBuilder> entry : timelineBuilders.int2ObjectEntrySet()) {
            state.timelines.put(entry.getIntKey(), entry.getValue().build());
        }

        applyTaxBase(db, taxes);
        return taxes;
    }

    private static ObjectArrayList<ResolvedTransfer> loadTransfers(CurrentBracketState state,
                                                                   NationSelection selection,
                                                                   long start,
                                                                   long end,
                                                                   boolean dontRequireGrant,
                                                                   boolean dontRequireTagged,
                                                                   boolean dontRequireExpiry,
                                                                   boolean includeDeposits) {
        ObjectArrayList<Transaction2> transfers = new ObjectArrayList<>(Locutus.imp().getBankDB().getTransactionsByAllianceSender(
                state.expenseAllianceIds,
                start,
                end,
                TransactionEndpointKey.NATION_TYPE
        ));
        if (includeDeposits) {
            transfers.addAll(Locutus.imp().getBankDB().getTransactionsByAllianceReceiver(
                    state.expenseAllianceIds,
                    start,
                    end,
                    TransactionEndpointKey.NATION_TYPE
            ));
        }

        transfers.removeIf(tx -> !passesTransferRequirements(tx, state.taxAllianceIds, dontRequireGrant, dontRequireTagged, dontRequireExpiry));
        transfers.removeIf(tx -> {
            boolean acceptedReceiver = tx.isReceiverNation() && selection.accepts((int) tx.receiver_id);
            boolean acceptedSender = tx.isSenderNation() && selection.accepts((int) tx.sender_id);
            return !acceptedReceiver && !acceptedSender;
        });
        transfers.sort(Comparator.comparingLong((Transaction2 tx) -> tx.tx_datetime).thenComparingInt(tx -> tx.tx_id));

        ObjectArrayList<ResolvedTransfer> resolved = new ObjectArrayList<>(transfers.size());
        for (Transaction2 transfer : transfers) {
            int nationId = transfer.isSenderNation() ? (int) transfer.sender_id : (int) transfer.receiver_id;
            Integer taxId = resolveTransferTaxId(state, nationId, TimeUtil.getTurn(transfer.tx_datetime));
            if (taxId == null) {
                continue;
            }
            resolved.add(new ResolvedTransfer(taxId, nationId, classifyTransfer(transfer), transfer));
        }
        return resolved;
    }

    private static void applyTaxBase(GuildDB db, ObjectArrayList<TaxDeposit> taxes) {
        TaxRate taxBase = db.getOrNull(GuildKey.TAX_BASE);
        int moneyBase = taxBase == null || taxBase.money < 0 ? 100 : taxBase.money;
        int resourceBase = taxBase == null || taxBase.resources < 0 ? 100 : taxBase.resources;
        int[] internalTaxRate = new int[2];

        for (TaxDeposit tax : taxes) {
            internalTaxRate[0] = tax.internalMoneyRate > 0 ? tax.internalMoneyRate : moneyBase;
            internalTaxRate[1] = tax.internalResourceRate > 0 ? tax.internalResourceRate : resourceBase;
            tax.multiplyBaseInverse(internalTaxRate);
        }
    }

    private static int resolveTaxId(CurrentBracketState state, TaxDeposit tax) {
        if (tax.tax_id > 0) {
            return tax.tax_id;
        }

        int currentTaxId = state.currentTaxIdByNation.get(tax.nationId);
        if (currentTaxId > 0) {
            WebTaxBracket current = state.bracketsByTaxId.get(currentTaxId);
            if (current != null && current.moneyRate == tax.moneyRate && current.rssRate == tax.resourceRate) {
                return currentTaxId;
            }
        }

        long pair = MathMan.pairInt(tax.moneyRate, tax.resourceRate);
        int pairTaxId = state.firstTaxIdByRatePair.get(pair);
        return pairTaxId > 0 ? pairTaxId : tax.tax_id;
    }

    private static void ensureBracket(CurrentBracketState state, TaxDeposit tax) {
        if (tax.tax_id <= 0 || state.bracketsByTaxId.containsKey(tax.tax_id)) {
            return;
        }

        TaxBracket bracket = new TaxBracket(tax.tax_id, tax.allianceId, "", tax.moneyRate, tax.resourceRate, System.currentTimeMillis());
        state.bracketsByTaxId.put(tax.tax_id, new WebTaxBracket(bracket));
        long pair = MathMan.pairInt(tax.moneyRate, tax.resourceRate);
        if (!state.firstTaxIdByRatePair.containsKey(pair)) {
            state.firstTaxIdByRatePair.put(pair, tax.tax_id);
        }
    }

    private static @Nullable Integer resolveTransferTaxId(CurrentBracketState state, int nationId, long turn) {
        NationBracketTimeline timeline = state.timelines.get(nationId);
        if (timeline != null) {
            Integer taxId = timeline.findTaxIdAtOrAfter(turn);
            if (taxId != null) {
                return taxId;
            }
        }

        int currentTaxId = state.currentTaxIdByNation.get(nationId);
        return currentTaxId > 0 ? currentTaxId : null;
    }

    private static boolean passesTransferRequirements(Transaction2 transfer,
                                                     IntOpenHashSet taxAllianceIds,
                                                     boolean dontRequireGrant,
                                                     boolean dontRequireTagged,
                                                     boolean dontRequireExpiry) {
        if (!dontRequireTagged && !(transfer.isSenderAA() && taxAllianceIds.contains((int) transfer.sender_id))
                && transfer.getTaggedAccountId() == 0) {
            return false;
        }
        if (!dontRequireGrant && !transfer.hasNoteTag(DepositType.GRANT)) {
            return false;
        }
        if (!dontRequireExpiry && !transfer.hasNoteTag(DepositType.EXPIRE) && !transfer.hasNoteTag(DepositType.DECAY)) {
            return false;
        }
        return true;
    }

    private static FlowType classifyTransfer(Transaction2 transfer) {
        if (transfer.isSenderNation()) {
            return FlowType.DEPOSIT;
        }
        if (transfer.hasNoteTag(DepositType.EXPIRE)) {
            return FlowType.GRANT;
        }
        return FlowType.WITHDRAWAL;
    }

    private static MutableSummarySection getOrCreateSummarySection(Int2ObjectOpenHashMap<MutableSummarySection> sections,
                                                                   LoadedData loaded,
                                                                   int taxId) {
        return sections.computeIfAbsent(taxId, ignored -> new MutableSummarySection(
                taxId,
                loaded.bracketsByTaxId.get(taxId),
                loaded.currentNationIdsByTaxId.getOrDefault(taxId, new IntArrayList())
        ));
    }

    private static void addSummaryResources(MutableSummarySection section,
                                            int nationId,
                                            int currentTaxId,
                                            double[] resources,
                                            boolean expense,
                                            boolean isTaxDeposit,
                                            @Nullable TaxExpenseTransactionRow row) {
        MutableNation nation = section.nationsById.computeIfAbsent(nationId, ignored -> new MutableNation(nationId, currentTaxId));
        if (expense) {
            ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, section.expense, resources);
            ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, nation.expense, resources);
        } else {
            ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, section.income, resources);
            ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, nation.income, resources);
        }
        if (isTaxDeposit) {
            nation.depositCount++;
        }
        if (row != null) {
            nation.transactions.add(row);
        }
    }

    private static SummarySectionData freezeSummarySection(MutableSummarySection section,
                                                           Int2IntOpenHashMap currentTaxIdByNation) {
        ObjectArrayList<TaxExpenseBracketRow> rows = new ObjectArrayList<>(section.nationIds.size());
        Int2ObjectOpenHashMap<TaxExpenseNation> nationsById = new Int2ObjectOpenHashMap<>(section.nationsById.size());

        for (Int2ObjectMap.Entry<MutableNation> entry : section.nationsById.int2ObjectEntrySet()) {
            MutableNation nation = entry.getValue();
            nation.transactions.sort(RECENT_TRANSACTION_ORDER);
            nationsById.put(entry.getIntKey(), new TaxExpenseNation(
                    section.taxId,
                    nation.nationId,
                    nullableTaxId(nation.currentTaxId),
                    nation.depositCount,
                    nation.transactions.size(),
                    toConvertedValue(nation.income),
                    toConvertedValue(nation.expense),
                    toConvertedValue(nation.income) - toConvertedValue(nation.expense),
                    nation.income,
                    nation.expense,
                    nation.transactions
            ));
        }

        for (int i = 0; i < section.nationIds.size(); i++) {
            int nationId = section.nationIds.getInt(i);
            MutableNation nation = section.nationsById.get(nationId);
            int currentTaxId = nation == null ? currentTaxIdByNation.get(nationId) : nation.currentTaxId;
            double incomeValue = nation == null ? 0D : toConvertedValue(nation.income);
            double expenseValue = nation == null ? 0D : toConvertedValue(nation.expense);
            double netValue = incomeValue - expenseValue;
            rows.add(new TaxExpenseBracketRow(nationId, nullableTaxId(currentTaxId), incomeValue, expenseValue, netValue));
        }
        rows.sort(BRACKET_ROW_ORDER);

        TaxExpenseBracket summary = new TaxExpenseBracket(
                section.taxId,
                section.bracket,
                section.nationIds.size(),
                toConvertedValue(section.income),
                toConvertedValue(section.expense),
                toConvertedValue(section.income) - toConvertedValue(section.expense),
                section.income,
                section.expense
        );
        return new SummarySectionData(summary, section.nationIdSet, nationsById, rows);
    }

    private static MutableTimeSection getOrCreateTimeSection(Int2ObjectOpenHashMap<MutableTimeSection> sections,
                                                             LoadedData loaded,
                                                             int taxId) {
        return sections.computeIfAbsent(taxId, ignored -> new MutableTimeSection(
                taxId,
                loaded.bracketsByTaxId.get(taxId),
                loaded.currentNationIdsByTaxId.getOrDefault(taxId, new IntArrayList())
        ));
    }

    private static void addTimeSeriesValue(double[][] overallByCategory,
                                           double[][][] byResourceOrdinalByCategory,
                                           FlowType type,
                                           int turnIndex,
                                           double[] resources) {
        overallByCategory[type.ordinal()][turnIndex] += toConvertedValue(resources);
        for (int i = 0; i < resources.length; i++) {
            byResourceOrdinalByCategory[i][type.ordinal()][turnIndex] += resources[i];
        }
    }

    private static TimeSectionData freezeTimeSection(MutableTimeSection section) {
        TaxExpenseTimeBracketSummary summary = new TaxExpenseTimeBracketSummary(
                section.taxId,
                section.bracket,
                section.nationIds.size(),
                toConvertedValue(section.income),
                toConvertedValue(section.expense),
                toConvertedValue(section.income) - toConvertedValue(section.expense)
        );
        return new TimeSectionData(summary, section.taxRecords, section.transfers);
    }

    private static double[][][] createResourceSeriesContainer(int turnCount) {
        return new double[ResourceType.values.length][FLOW_TYPES.length][turnCount];
    }

    private static long[] createTurnTimestamps(long turnStart, long turnEnd) {
        int len = (int) (turnEnd - turnStart + 1L);
        long[] timestamps = new long[len];
        for (int i = 0; i < len; i++) {
            timestamps[i] = TimeUtil.getTimeFromTurn(turnStart + i) / 1000L;
        }
        return timestamps;
    }

    private static List<TaxExpenseTimeCategory> createTimeCategories() {
        ObjectArrayList<TaxExpenseTimeCategory> categories = new ObjectArrayList<>(FLOW_TYPES.length);
        for (FlowType type : FLOW_TYPES) {
            categories.add(new TaxExpenseTimeCategory(type.name(), type.isExpense));
        }
        return categories;
    }

    private static String resolveFilter(@Nullable JSONObject command,
                                        String argumentName,
                                        @Nullable NationList nationList) {
        if (command != null && command.has(argumentName)) {
            return normalizeFilter(command.optString(argumentName, null));
        }
        if (nationList != null) {
            return normalizeFilter(nationList.getFilter());
        }
        return "*";
    }

    private static String normalizeFilter(@Nullable String filter) {
        return filter == null || filter.isBlank() ? "*" : filter;
    }

    private static NationSelection createSelection(@Nullable NationList nationList) {
        if (nationList == null) {
            return NationSelection.ALL;
        }
        return new NationSelection(new IntOpenHashSet(nationList.getNationIds()));
    }

    private static long resolveDatasetEnd(long end) {
        return end == Long.MAX_VALUE ? System.currentTimeMillis() : end;
    }

    private static long resolveTurnEnd(long end) {
        return Math.min(TimeUtil.getTurn(end), TimeUtil.getTurn());
    }

    private static int nextDatasetId() {
        return NEXT_DATASET_ID.getAndUpdate(current -> current == Integer.MAX_VALUE ? 1 : current + 1);
    }

    private static double toConvertedValue(double[] resources) {
        return ResourceType.convertedTotal(resources);
    }

    private static @Nullable Integer nullableTaxId(int currentTaxId) {
        return currentTaxId > 0 ? currentTaxId : null;
    }

    private static final class SummaryRequestKey {
        private final long guildId;
        private final long start;
        private final long end;
        private final boolean dontRequireGrant;
        private final boolean dontRequireTagged;
        private final boolean dontRequireExpiry;
        private final boolean includeDeposits;
        private final String filter;

        private SummaryRequestKey(long guildId,
                                  long start,
                                  long end,
                                  boolean dontRequireGrant,
                                  boolean dontRequireTagged,
                                  boolean dontRequireExpiry,
                                  boolean includeDeposits,
                                  String filter) {
            this.guildId = guildId;
            this.start = start;
            this.end = end;
            this.dontRequireGrant = dontRequireGrant;
            this.dontRequireTagged = dontRequireTagged;
            this.dontRequireExpiry = dontRequireExpiry;
            this.includeDeposits = includeDeposits;
            this.filter = filter;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SummaryRequestKey that)) {
                return false;
            }
            return guildId == that.guildId
                    && start == that.start
                    && end == that.end
                    && dontRequireGrant == that.dontRequireGrant
                    && dontRequireTagged == that.dontRequireTagged
                    && dontRequireExpiry == that.dontRequireExpiry
                    && includeDeposits == that.includeDeposits
                    && filter.equals(that.filter);
        }

        @Override
        public int hashCode() {
            return Objects.hash(guildId, start, end, dontRequireGrant, dontRequireTagged, dontRequireExpiry, includeDeposits, filter);
        }
    }

    private static final class TimeRequestKey {
        private final long guildId;
        private final long start;
        private final long end;
        private final boolean dontRequireGrant;
        private final boolean dontRequireTagged;
        private final boolean dontRequireExpiry;
        private final boolean includeDeposits;
        private final String filter;

        private TimeRequestKey(long guildId,
                               long start,
                               long end,
                               boolean dontRequireGrant,
                               boolean dontRequireTagged,
                               boolean dontRequireExpiry,
                               boolean includeDeposits,
                               String filter) {
            this.guildId = guildId;
            this.start = start;
            this.end = end;
            this.dontRequireGrant = dontRequireGrant;
            this.dontRequireTagged = dontRequireTagged;
            this.dontRequireExpiry = dontRequireExpiry;
            this.includeDeposits = includeDeposits;
            this.filter = filter;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TimeRequestKey that)) {
                return false;
            }
            return guildId == that.guildId
                    && start == that.start
                    && end == that.end
                    && dontRequireGrant == that.dontRequireGrant
                    && dontRequireTagged == that.dontRequireTagged
                    && dontRequireExpiry == that.dontRequireExpiry
                    && includeDeposits == that.includeDeposits
                    && filter.equals(that.filter);
        }

        @Override
        public int hashCode() {
            return Objects.hash(guildId, start, end, dontRequireGrant, dontRequireTagged, dontRequireExpiry, includeDeposits, filter);
        }
    }

    private static final class NationSelection {
        private static final NationSelection ALL = new NationSelection(null);

        private final @Nullable IntOpenHashSet nationIds;

        private NationSelection(@Nullable IntOpenHashSet nationIds) {
            this.nationIds = nationIds;
        }

        private boolean accepts(int nationId) {
            return nationIds == null || nationIds.contains(nationId);
        }
    }

    private static final class CurrentBracketState {
        private final IntOpenHashSet taxAllianceIds = new IntOpenHashSet();
        private final IntOpenHashSet expenseAllianceIds = new IntOpenHashSet();
        private final Int2ObjectOpenHashMap<WebTaxBracket> bracketsByTaxId = new Int2ObjectOpenHashMap<>();
        private final Long2IntOpenHashMap firstTaxIdByRatePair = new Long2IntOpenHashMap();
        private final Int2IntOpenHashMap currentTaxIdByNation = new Int2IntOpenHashMap();
        private final Int2ObjectOpenHashMap<IntArrayList> currentNationIdsByTaxId = new Int2ObjectOpenHashMap<>();
        private final Int2ObjectOpenHashMap<NationBracketTimeline> timelines = new Int2ObjectOpenHashMap<>();
        private final IntArrayList allNationIds = new IntArrayList();

        private CurrentBracketState() {
            firstTaxIdByRatePair.defaultReturnValue(0);
            currentTaxIdByNation.defaultReturnValue(0);
        }
    }

    private static final class NationBracketTimelineBuilder {
        private final LongArrayList turns = new LongArrayList();
        private final IntArrayList taxIds = new IntArrayList();

        private void add(long turn, int taxId) {
            int lastIndex = turns.size() - 1;
            if (lastIndex >= 0 && turns.getLong(lastIndex) == turn) {
                taxIds.set(lastIndex, taxId);
                return;
            }
            turns.add(turn);
            taxIds.add(taxId);
        }

        private NationBracketTimeline build() {
            return new NationBracketTimeline(turns.toLongArray(), taxIds.toIntArray());
        }
    }

    private static final class NationBracketTimeline {
        private final long[] turns;
        private final int[] taxIds;

        private NationBracketTimeline(long[] turns, int[] taxIds) {
            this.turns = turns;
            this.taxIds = taxIds;
        }

        private @Nullable Integer findTaxIdAtOrAfter(long turn) {
            int index = java.util.Arrays.binarySearch(turns, turn);
            if (index < 0) {
                index = -index - 1;
            }
            if (index >= taxIds.length) {
                return null;
            }
            return taxIds[index];
        }
    }

    private static final class ResolvedTransfer {
        private final int taxId;
        private final int nationId;
        private final FlowType type;
        private final Transaction2 transaction;

        private ResolvedTransfer(int taxId, int nationId, FlowType type, Transaction2 transaction) {
            this.taxId = taxId;
            this.nationId = nationId;
            this.type = type;
            this.transaction = transaction;
        }
    }

    private static final class LoadedData {
        private final IntOpenHashSet expenseAllianceIds;
        private final Int2ObjectOpenHashMap<WebTaxBracket> bracketsByTaxId;
        private final Int2IntOpenHashMap currentTaxIdByNation;
        private final Int2ObjectOpenHashMap<IntArrayList> currentNationIdsByTaxId;
        private final IntArrayList allNationIds;
        private final ObjectArrayList<TaxDeposit> taxes;
        private final ObjectArrayList<ResolvedTransfer> transfers;

        private LoadedData(CurrentBracketState state,
                           ObjectArrayList<TaxDeposit> taxes,
                           ObjectArrayList<ResolvedTransfer> transfers) {
            this.expenseAllianceIds = state.expenseAllianceIds;
            this.bracketsByTaxId = state.bracketsByTaxId;
            this.currentTaxIdByNation = state.currentTaxIdByNation;
            this.currentNationIdsByTaxId = state.currentNationIdsByTaxId;
            this.allNationIds = state.allNationIds;
            this.taxes = taxes;
            this.transfers = transfers;
        }
    }

    private static final class MutableNation {
        private final int nationId;
        private final int currentTaxId;
        private int depositCount;
        private final double[] income = ResourceType.getBuffer();
        private final double[] expense = ResourceType.getBuffer();
        private final ObjectArrayList<TaxExpenseTransactionRow> transactions = new ObjectArrayList<>();

        private MutableNation(int nationId, int currentTaxId) {
            this.nationId = nationId;
            this.currentTaxId = currentTaxId;
        }
    }

    private static final class MutableSummarySection {
        private final int taxId;
        private final @Nullable WebTaxBracket bracket;
        private final IntArrayList nationIds;
        private final IntOpenHashSet nationIdSet;
        private final Int2ObjectOpenHashMap<MutableNation> nationsById = new Int2ObjectOpenHashMap<>();
        private final double[] income = ResourceType.getBuffer();
        private final double[] expense = ResourceType.getBuffer();

        private MutableSummarySection(int taxId, @Nullable WebTaxBracket bracket, IntArrayList nationIds) {
            this.taxId = taxId;
            this.bracket = bracket;
            this.nationIds = new IntArrayList(nationIds);
            this.nationIds.sort(IntComparators.NATURAL_COMPARATOR);
            this.nationIdSet = new IntOpenHashSet(this.nationIds);
        }
    }

    private static final class SummarySectionData {
        private final TaxExpenseBracket summary;
        private final IntOpenHashSet nationIdSet;
        private final Int2ObjectOpenHashMap<TaxExpenseNation> nationsById;
        private final ObjectArrayList<TaxExpenseBracketRow> rows;

        private SummarySectionData(TaxExpenseBracket summary,
                                   IntOpenHashSet nationIdSet,
                                   Int2ObjectOpenHashMap<TaxExpenseNation> nationsById,
                                   ObjectArrayList<TaxExpenseBracketRow> rows) {
            this.summary = summary;
            this.nationIdSet = nationIdSet;
            this.nationsById = nationsById;
            this.rows = rows;
        }
    }

    private static final class SummaryDatasetSlot {
        private final SummaryRequestKey requestKey;
        private final int datasetId;
        private final long guildId;
        private final long start;
        private final long end;
        private final NationSelection selection;
        private final boolean dontRequireGrant;
        private final boolean dontRequireTagged;
        private final boolean dontRequireExpiry;
        private final boolean includeDeposits;
        private SoftReference<SummaryDataset> datasetRef = new SoftReference<>(null);

        private SummaryDatasetSlot(SummaryRequestKey requestKey,
                                   int datasetId,
                                   long guildId,
                                   long start,
                                   long end,
                                   NationSelection selection,
                                   boolean dontRequireGrant,
                                   boolean dontRequireTagged,
                                   boolean dontRequireExpiry,
                                   boolean includeDeposits) {
            this.requestKey = requestKey;
            this.datasetId = datasetId;
            this.guildId = guildId;
            this.start = start;
            this.end = end;
            this.selection = selection;
            this.dontRequireGrant = dontRequireGrant;
            this.dontRequireTagged = dontRequireTagged;
            this.dontRequireExpiry = dontRequireExpiry;
            this.includeDeposits = includeDeposits;
        }

        private SummaryDataset getOrBuild(GuildDB db) throws Exception {
            SummaryDataset dataset = datasetRef.get();
            if (dataset != null) {
                return dataset;
            }
            dataset = buildSummaryDataset(
                    datasetId,
                    db,
                    start,
                    end,
                    selection,
                    dontRequireGrant,
                    dontRequireTagged,
                    dontRequireExpiry,
                    includeDeposits
            );
            datasetRef = new SoftReference<>(dataset);
            return dataset;
        }
    }

    static final class SummaryDataset {
        final int datasetId;
        final long guildId;
        private final TaxExpenses response;
        private final Int2ObjectOpenHashMap<SummarySectionData> sectionsByTaxId;
        private final Int2IntOpenHashMap currentTaxIdByNation;

        private SummaryDataset(int datasetId,
                               long guildId,
                               TaxExpenses response,
                               Int2ObjectOpenHashMap<SummarySectionData> sectionsByTaxId,
                               Int2IntOpenHashMap currentTaxIdByNation) {
            this.datasetId = datasetId;
            this.guildId = guildId;
            this.response = response;
            this.sectionsByTaxId = sectionsByTaxId;
            this.currentTaxIdByNation = currentTaxIdByNation;
        }

        TaxExpenses toResponse() {
            return response;
        }

        TaxExpenseBracketRows getBracketRows(int taxId) {
            return new TaxExpenseBracketRows(taxId, requireSection(taxId).rows);
        }

        TaxExpenseNation getNation(int taxId, int nationId) {
            SummarySectionData section = requireSection(taxId);
            if (!section.nationIdSet.contains(nationId)) {
                throw new IllegalArgumentException("Nation is not part of the selected tax-expense section.");
            }
            TaxExpenseNation detail = section.nationsById.get(nationId);
            if (detail != null) {
                return detail;
            }
            return new TaxExpenseNation(
                    taxId,
                    nationId,
                    nullableTaxId(currentTaxIdByNation.get(nationId)),
                    0,
                    0,
                    0D,
                    0D,
                    0D,
                    ResourceType.getBuffer(),
                    ResourceType.getBuffer(),
                    List.of()
            );
        }

        private SummarySectionData requireSection(int taxId) {
            SummarySectionData section = sectionsByTaxId.get(taxId);
            if (section == null) {
                throw new IllegalArgumentException("Tax expense section is not available in this dataset.");
            }
            return section;
        }
    }

    private static final class TimedResources {
        private final int turnIndex;
        private final double[] resources;

        private TimedResources(int turnIndex, double[] resources) {
            this.turnIndex = turnIndex;
            this.resources = resources;
        }
    }

    private static final class TimedTransfer {
        private final int turnIndex;
        private final FlowType type;
        private final double[] resources;

        private TimedTransfer(int turnIndex, FlowType type, double[] resources) {
            this.turnIndex = turnIndex;
            this.type = type;
            this.resources = resources;
        }
    }

    private static final class MutableTimeSection {
        private final int taxId;
        private final @Nullable WebTaxBracket bracket;
        private final IntArrayList nationIds;
        private final ObjectArrayList<TimedResources> taxRecords = new ObjectArrayList<>();
        private final ObjectArrayList<TimedTransfer> transfers = new ObjectArrayList<>();
        private final double[] income = ResourceType.getBuffer();
        private final double[] expense = ResourceType.getBuffer();

        private MutableTimeSection(int taxId, @Nullable WebTaxBracket bracket, IntArrayList nationIds) {
            this.taxId = taxId;
            this.bracket = bracket;
            this.nationIds = new IntArrayList(nationIds);
            this.nationIds.sort(IntComparators.NATURAL_COMPARATOR);
        }
    }

    private static final class TimeSectionData {
        private final TaxExpenseTimeBracketSummary summary;
        private final ObjectArrayList<TimedResources> taxRecords;
        private final ObjectArrayList<TimedTransfer> transfers;
        private @Nullable TaxExpenseTimeBracket bracket;

        private TimeSectionData(TaxExpenseTimeBracketSummary summary,
                                ObjectArrayList<TimedResources> taxRecords,
                                ObjectArrayList<TimedTransfer> transfers) {
            this.summary = summary;
            this.taxRecords = taxRecords;
            this.transfers = transfers;
        }

        private TaxExpenseTimeBracket getBracket(int turnCount) {
            if (bracket == null) {
                double[][] overallByCategory = new double[FLOW_TYPES.length][turnCount];
                for (TimedResources tax : taxRecords) {
                    overallByCategory[FlowType.TAX.ordinal()][tax.turnIndex] += toConvertedValue(tax.resources);
                }
                for (TimedTransfer transfer : transfers) {
                    overallByCategory[transfer.type.ordinal()][transfer.turnIndex] += toConvertedValue(transfer.resources);
                }
                bracket = new TaxExpenseTimeBracket(
                        summary.taxId,
                        summary.bracket,
                        summary.nationCount,
                        summary.incomeValue,
                        summary.expenseValue,
                        summary.netValue,
                        overallByCategory
                );
            }
            return bracket;
        }
    }

    private static final class TimeDatasetSlot {
        private final TimeRequestKey requestKey;
        private final int datasetId;
        private final long guildId;
        private final long start;
        private final long end;
        private final NationSelection selection;
        private final boolean dontRequireGrant;
        private final boolean dontRequireTagged;
        private final boolean dontRequireExpiry;
        private final boolean includeDeposits;
        private final long turnStart;
        private final long turnEnd;
        private SoftReference<TimeDataset> datasetRef = new SoftReference<>(null);

        private TimeDatasetSlot(TimeRequestKey requestKey,
                                int datasetId,
                                long guildId,
                                long start,
                                long end,
                                NationSelection selection,
                                boolean dontRequireGrant,
                                boolean dontRequireTagged,
                                boolean dontRequireExpiry,
                                boolean includeDeposits,
                                long turnStart,
                                long turnEnd) {
            this.requestKey = requestKey;
            this.datasetId = datasetId;
            this.guildId = guildId;
            this.start = start;
            this.end = end;
            this.selection = selection;
            this.dontRequireGrant = dontRequireGrant;
            this.dontRequireTagged = dontRequireTagged;
            this.dontRequireExpiry = dontRequireExpiry;
            this.includeDeposits = includeDeposits;
            this.turnStart = turnStart;
            this.turnEnd = turnEnd;
        }

        private TimeDataset getOrBuild(GuildDB db) throws Exception {
            TimeDataset dataset = datasetRef.get();
            if (dataset != null) {
                return dataset;
            }
            dataset = buildTimeDataset(
                    datasetId,
                    db,
                    start,
                    end,
                    selection,
                    dontRequireGrant,
                    dontRequireTagged,
                    dontRequireExpiry,
                    includeDeposits,
                    turnStart,
                    turnEnd
            );
            datasetRef = new SoftReference<>(dataset);
            return dataset;
        }
    }

    static final class TimeDataset {
        final int datasetId;
        final long guildId;
        private final TaxExpenseTime response;
        private final TaxExpenseTimeResources resources;
        private final Int2ObjectOpenHashMap<TimeSectionData> sectionsByTaxId;
        private final int turnCount;

        private TimeDataset(int datasetId,
                            long guildId,
                            TaxExpenseTime response,
                            TaxExpenseTimeResources resources,
                            Int2ObjectOpenHashMap<TimeSectionData> sectionsByTaxId,
                            int turnCount) {
            this.datasetId = datasetId;
            this.guildId = guildId;
            this.response = response;
            this.resources = resources;
            this.sectionsByTaxId = sectionsByTaxId;
            this.turnCount = turnCount;
        }

        TaxExpenseTime toResponse() {
            return response;
        }

        TaxExpenseTimeResources getResources() {
            return resources;
        }

        TaxExpenseTimeBracket getBracket(int taxId) {
            TimeSectionData section = sectionsByTaxId.get(taxId);
            if (section == null) {
                throw new IllegalArgumentException("Tax expense bracket is not available in this by-time dataset.");
            }
            return section.getBracket(turnCount);
        }
    }
}
