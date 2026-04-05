package link.locutus.discord.web.commands.api;

import com.google.common.base.Predicates;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.TaxDeposit;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.TaxRecordCategorizer2;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.WebTaxBracket;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseBracket;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseBracketMembers;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseNationDetail;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseTime;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseTimeCategory;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseTimeBracket;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseTimeResources;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseTransactionRow;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseTransactions;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenses;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TaxEndpoints {
    private static final int TOTAL_TAX_ID = -1;
    private static final TaxRecordCategorizer2.TransactionType[] TIME_CATEGORY_TYPES = TaxRecordCategorizer2.TransactionType.values();

    private Predicate<Integer> createNationListFilter(@Nullable NationList nationList) {
        if (nationList == null) {
            return Predicates.alwaysTrue();
        }
        return id -> {
            DBNation nation = DBNation.getById(id);
            return nation != null && nationList.getNations().contains(nation);
        };
    }

    private Predicate<Integer> createNationFilter(@Nullable Set<DBNation> nationFilter) {
        if (nationFilter == null) {
            return Predicates.alwaysTrue();
        }
        Set<Integer> ids = nationFilter.stream().map(DBNation::getNation_id).collect(Collectors.toSet());
        return ids::contains;
    }

    private TaxRecordCategorizer2 createSummaryCategorizer(GuildDB db,
                                                           long start,
                                                           long end,
                                                           @Nullable NationList nationList,
                                                           boolean dontRequireGrant,
                                                           boolean dontRequireTagged,
                                                           boolean dontRequireExpiry,
                                                           boolean includeDeposits) throws Exception {
        return new TaxRecordCategorizer2(db, start, end, dontRequireGrant, dontRequireTagged, dontRequireExpiry,
                includeDeposits, createNationListFilter(nationList), ignored -> {
                });
    }

    private TaxRecordCategorizer2 createTimeCategorizer(GuildDB db,
                                                        long start,
                                                        long end,
                                                        @Nullable Set<DBNation> nationFilter,
                                                        boolean dontRequireTagged) throws Exception {
        return new TaxRecordCategorizer2(db, start, end, true, dontRequireTagged, true, true,
                createNationFilter(nationFilter), ignored -> {
                });
    }

    private TaxExpenseBracket createSummarySection(int taxId,
                                                   @Nullable TaxBracket bracket,
                                                   int nationCount,
                                                   double[] income,
                                                   double[] expense) {
        WebTaxBracket webBracket = bracket == null ? null : new WebTaxBracket(bracket);
        return new TaxExpenseBracket(taxId, webBracket, nationCount, income, expense);
    }

    private List<DBNation> getSectionNations(TaxRecordCategorizer2 categorized, int taxId) {
        if (taxId == TOTAL_TAX_ID) {
            return categorized.getAllNations();
        }
        return categorized.getNationsByBracket().getOrDefault(taxId, List.of());
    }

    private Map<Integer, Integer> getSectionDepositCountByNation(TaxRecordCategorizer2 categorized, int taxId) {
        if (taxId == TOTAL_TAX_ID) {
            return categorized.getAllNationDepositCount();
        }
        return categorized.getBracketToNationDepositCount().getOrDefault(taxId, Map.of());
    }

    private Map<Integer, double[]> getSectionIncomeByNation(TaxRecordCategorizer2 categorized, int taxId) {
        if (taxId == TOTAL_TAX_ID) {
            return categorized.getIncomeByNation();
        }
        return categorized.getIncomeByNationByBracket().getOrDefault(taxId, Map.of());
    }

    private Map<Integer, double[]> getSectionExpensesByNation(TaxRecordCategorizer2 categorized, int taxId) {
        if (taxId == TOTAL_TAX_ID) {
            return categorized.getExpensesByNation();
        }
        return categorized.getExpensesByNationByBracket().getOrDefault(taxId, Map.of());
    }

    private Map<Integer, List<Transaction2>> getSectionTransactionsByNation(TaxRecordCategorizer2 categorized, int taxId) {
        if (taxId == TOTAL_TAX_ID) {
            return categorized.getTransactionsByNation();
        }
        return categorized.getTransactionsByNationByBracket().getOrDefault(taxId, Map.of());
    }

    private static Map<TaxRecordCategorizer2.TransactionType, double[][]> createEmptyTransfers(long turnStart, long turnEnd) {
        int len = (int) (turnEnd - turnStart + 1);
        Map<TaxRecordCategorizer2.TransactionType, double[][]> result = new EnumMap<>(TaxRecordCategorizer2.TransactionType.class);
        for (TaxRecordCategorizer2.TransactionType type : TaxRecordCategorizer2.TransactionType.values()) {
            result.put(type, new double[len][ResourceType.values.length]);
        }
        return result;
    }

    private static void addTransfers(Map<TaxRecordCategorizer2.TransactionType, double[][]> total,
                                     Map<TaxRecordCategorizer2.TransactionType, double[][]> current) {
        for (Map.Entry<TaxRecordCategorizer2.TransactionType, double[][]> entry : current.entrySet()) {
            double[][] currentByTurn = entry.getValue();
            double[][] totalByTurn = total.computeIfAbsent(entry.getKey(), ignored -> new double[currentByTurn.length][ResourceType.values.length]);
            for (int i = 0; i < totalByTurn.length; i++) {
                ResourceType.add(totalByTurn[i], currentByTurn[i]);
            }
        }
    }

    private Map<Integer, Map<TaxRecordCategorizer2.TransactionType, double[][]>> createTransfersByTaxId(TaxRecordCategorizer2 categorized,
                                                                                                         long turnStart,
                                                                                                         long turnEnd) {
        Map<Integer, List<TaxDeposit>> taxRecordsByBracket = new LinkedHashMap<>();
        for (TaxDeposit tax : categorized.getTaxes()) {
            taxRecordsByBracket.computeIfAbsent(tax.tax_id, ignored -> new ArrayList<>()).add(tax);
        }

        Map<Integer, List<Map.Entry<Transaction2, TaxRecordCategorizer2.TransactionType>>> txsByType = categorized.getTransactionsByBracketByType();
        Map<Integer, Map<TaxRecordCategorizer2.TransactionType, double[][]>> result = new LinkedHashMap<>();

        for (Map.Entry<Integer, TaxBracket> entry : categorized.getBrackets().entrySet()) {
            int taxId = entry.getKey();
            List<TaxDeposit> taxRecords = taxRecordsByBracket.get(taxId);
            if (taxRecords == null || taxRecords.isEmpty()) {
                continue;
            }

            List<Map.Entry<Transaction2, TaxRecordCategorizer2.TransactionType>> transfers = txsByType.getOrDefault(taxId, List.of());
            result.put(taxId, categorized.sumTransfersByCategoryByTurn(turnStart, turnEnd, taxRecords, transfers));
        }
        return result;
    }

    private static long[] createTurnTimestamps(long turnStart, long turnEnd) {
        int len = (int) (turnEnd - turnStart + 1);
        long[] timestamps = new long[len];
        for (int i = 0; i < len; i++) {
            timestamps[i] = TimeUtil.getTimeFromTurn(turnStart + i) / 1000L;
        }
        return timestamps;
    }

    private static double[][] createCategorySeries(Map<TaxRecordCategorizer2.TransactionType, double[][]> transfers,
                                                   @Nullable ResourceType resource) {
        int len = transfers.values().iterator().next().length;
        double[][] result = new double[TIME_CATEGORY_TYPES.length][len];
        for (int categoryIndex = 0; categoryIndex < TIME_CATEGORY_TYPES.length; categoryIndex++) {
            TaxRecordCategorizer2.TransactionType type = TIME_CATEGORY_TYPES[categoryIndex];
            double[][] byTurn = transfers.get(type);
            double[] output = result[categoryIndex];
            for (int turnIndex = 0; turnIndex < len; turnIndex++) {
                double[] values = byTurn[turnIndex];
                output[turnIndex] = resource == null ? ResourceType.convertedTotal(values) : values[resource.ordinal()];
            }
        }
        return result;
    }

    private static Map<String, double[][]> createResourceSeries(Map<TaxRecordCategorizer2.TransactionType, double[][]> transfers) {
        Map<String, double[][]> result = new LinkedHashMap<>();
        for (ResourceType type : ResourceType.values) {
            if (type != ResourceType.CREDITS) {
                result.put(type.name(), createCategorySeries(transfers, type));
            }
        }
        return result;
    }

    private static List<TaxExpenseTimeCategory> createTimeCategories() {
        return Arrays.stream(TIME_CATEGORY_TYPES)
                .map(type -> new TaxExpenseTimeCategory(type.name(), type.isExpense))
                .toList();
    }

    private TaxExpenseTimeBracket createTimeSection(int taxId,
                                                    @Nullable TaxBracket bracket,
                                                    int nationCount,
                                                    Map<TaxRecordCategorizer2.TransactionType, double[][]> transfers) {
        WebTaxBracket webBracket = bracket == null ? null : new WebTaxBracket(bracket);
        return new TaxExpenseTimeBracket(taxId, webBracket, nationCount, createCategorySeries(transfers, null));
    }

    @Command(desc = "Show cumulative tax expenses over a period by nation/bracket", viewable = true)
    @IsAlliance
    @RolePermission(Roles.ECON_STAFF)
    @ReturnType(TaxExpenses.class)
    public TaxExpenses tax_expense(@Me GuildDB db,
                                   @Timestamp long start,
                                   @Default @Timestamp Long end,
                                   @Switch("n") NationList nationList,
                                   @Switch("g") boolean dontRequireGrant,
                                   @Switch("t") boolean dontRequireTagged,
                                   @Switch("e") boolean dontRequireExpiry,
                                   @Switch("d") boolean includeDeposits) throws Exception {
        if (end == null) end = Long.MAX_VALUE;
        TaxRecordCategorizer2 categorized = createSummaryCategorizer(db, start, end, nationList, dontRequireGrant,
                dontRequireTagged, dontRequireExpiry, includeDeposits);

        Map<Integer, double[]> expensesByBracket = categorized.getExpensesByBracket();
        Map<Integer, double[]> incomeByBracket = categorized.getIncomeByBracket();
        List<TaxExpenseBracket> brackets = new ObjectArrayList<>();

        for (Map.Entry<Integer, TaxBracket> entry : categorized.getBrackets().entrySet()) {
            int taxId = entry.getKey();
            if (expensesByBracket.get(taxId) == null && incomeByBracket.get(taxId) == null) {
                continue;
            }

            int nationCount = categorized.getNationsByBracket().getOrDefault(taxId, List.of()).size();
            brackets.add(createSummarySection(
                    taxId,
                    entry.getValue(),
                    nationCount,
                    incomeByBracket.getOrDefault(taxId, ResourceType.getBuffer()),
                    expensesByBracket.getOrDefault(taxId, ResourceType.getBuffer())
            ));
        }

        TaxExpenseBracket total = createSummarySection(TOTAL_TAX_ID, null, categorized.getAllNations().size(),
                categorized.getIncomeTotal(), categorized.getExpenseTotal());
        return new TaxExpenses(total, brackets, categorized.getAlliances(), categorized.getTaxes().size());
    }

    @Command(desc = "List nation ids for a tax expense section", viewable = true)
    @IsAlliance
    @RolePermission(Roles.ECON_STAFF)
    @ReturnType(TaxExpenseBracketMembers.class)
    public TaxExpenseBracketMembers tax_expense_bracket_members(@Me GuildDB db,
                                                                @Timestamp long start,
                                                                @Timestamp long end,
                                                                int taxId,
                                                                @Switch("n") NationList nationList,
                                                                @Switch("g") boolean dontRequireGrant,
                                                                @Switch("t") boolean dontRequireTagged,
                                                                @Switch("e") boolean dontRequireExpiry,
                                                                @Switch("d") boolean includeDeposits) throws Exception {
        TaxRecordCategorizer2 categorized = createSummaryCategorizer(db, start, end, nationList, dontRequireGrant,
                dontRequireTagged, dontRequireExpiry, includeDeposits);
        return new TaxExpenseBracketMembers(taxId, getSectionNations(categorized, taxId).stream().map(DBNation::getNation_id).toList());
    }

    @Command(desc = "Get detail data for a nation row within a tax expense section", viewable = true)
    @IsAlliance
    @RolePermission(Roles.ECON_STAFF)
    @ReturnType(TaxExpenseNationDetail.class)
    public TaxExpenseNationDetail tax_expense_nation_detail(@Me GuildDB db,
                                                            @Timestamp long start,
                                                            @Timestamp long end,
                                                            int taxId,
                                                            DBNation nation,
                                                            @Switch("n") NationList nationList,
                                                            @Switch("g") boolean dontRequireGrant,
                                                            @Switch("t") boolean dontRequireTagged,
                                                            @Switch("e") boolean dontRequireExpiry,
                                                            @Switch("d") boolean includeDeposits) throws Exception {
        TaxRecordCategorizer2 categorized = createSummaryCategorizer(db, start, end, nationList, dontRequireGrant,
                dontRequireTagged, dontRequireExpiry, includeDeposits);

        int nationId = nation.getNation_id();
        Map<Integer, Integer> depositCountByNation = getSectionDepositCountByNation(categorized, taxId);
        Map<Integer, double[]> incomeByNation = getSectionIncomeByNation(categorized, taxId);
        Map<Integer, double[]> expensesByNation = getSectionExpensesByNation(categorized, taxId);
        Map<Integer, List<Transaction2>> transactionsByNation = getSectionTransactionsByNation(categorized, taxId);
        TaxBracket currentBracket = categorized.getBracketsByNation().get(nationId);

        return new TaxExpenseNationDetail(
                taxId,
                nationId,
                currentBracket == null ? null : currentBracket.taxId,
                depositCountByNation.getOrDefault(nationId, 0),
                transactionsByNation.getOrDefault(nationId, List.of()).size(),
                incomeByNation.getOrDefault(nationId, ResourceType.getBuffer()),
                expensesByNation.getOrDefault(nationId, ResourceType.getBuffer())
        );
    }

    @Command(desc = "Get transaction rows for a nation within a tax expense section", viewable = true)
    @IsAlliance
    @RolePermission(Roles.ECON_STAFF)
    @ReturnType(TaxExpenseTransactions.class)
    public TaxExpenseTransactions tax_expense_nation_transactions(@Me GuildDB db,
                                                                  @Timestamp long start,
                                                                  @Timestamp long end,
                                                                  int taxId,
                                                                  DBNation nation,
                                                                  @Switch("n") NationList nationList,
                                                                  @Switch("g") boolean dontRequireGrant,
                                                                  @Switch("t") boolean dontRequireTagged,
                                                                  @Switch("e") boolean dontRequireExpiry,
                                                                  @Switch("d") boolean includeDeposits) throws Exception {
        TaxRecordCategorizer2 categorized = createSummaryCategorizer(db, start, end, nationList, dontRequireGrant,
                dontRequireTagged, dontRequireExpiry, includeDeposits);

        int nationId = nation.getNation_id();
        List<TaxExpenseTransactionRow> rows = getSectionTransactionsByNation(categorized, taxId)
                .getOrDefault(nationId, List.of())
                .stream()
                .map(TaxExpenseTransactionRow::new)
                .toList();
        return new TaxExpenseTransactions(taxId, nationId, rows);
    }

    @Command(desc = "Show running tax expenses by turn by bracket", viewable = true)
    @IsAlliance
    @RolePermission(Roles.ECON_STAFF)
    @ReturnType(TaxExpenseTime.class)
    public TaxExpenseTime tax_expense_by_time(@Me GuildDB db,
                                              @Timestamp long start,
                                              @Timestamp long end,
                                              @Default Set<DBNation> nationFilter,
                                              @Switch("t") boolean dontRequireTagged) throws Exception {
        long turnStart = TimeUtil.getTurn(start);
        long turnEnd = TimeUtil.getTurn(end);
        if (turnEnd - turnStart > 365L * 12L) {
            throw new IllegalArgumentException("Timeframe is too large");
        }

        TaxRecordCategorizer2 categorized = createTimeCategorizer(db, start, end, nationFilter, dontRequireTagged);
        Map<Integer, Map<TaxRecordCategorizer2.TransactionType, double[][]>> transfersByTaxId = createTransfersByTaxId(categorized, turnStart, turnEnd);
        Map<TaxRecordCategorizer2.TransactionType, double[][]> totalTransfers = createEmptyTransfers(turnStart, turnEnd);
        List<TaxExpenseTimeBracket> brackets = new ObjectArrayList<>();

        for (Map.Entry<Integer, Map<TaxRecordCategorizer2.TransactionType, double[][]>> entry : transfersByTaxId.entrySet()) {
            int taxId = entry.getKey();
            addTransfers(totalTransfers, entry.getValue());
            int nationCount = categorized.getNationsByBracket().getOrDefault(taxId, List.of()).size();
            brackets.add(createTimeSection(taxId, categorized.getBrackets().get(taxId), nationCount, entry.getValue()));
        }

        TaxExpenseTimeBracket total = createTimeSection(TOTAL_TAX_ID, null, categorized.getAllNations().size(), totalTransfers);
        return new TaxExpenseTime(createTurnTimestamps(turnStart, turnEnd), createTimeCategories(), total, brackets);
    }

    @Command(desc = "Get total tax expense series by resource over time", viewable = true)
    @IsAlliance
    @RolePermission(Roles.ECON_STAFF)
    @ReturnType(TaxExpenseTimeResources.class)
    public TaxExpenseTimeResources tax_expense_by_time_resources(@Me GuildDB db,
                                                                 @Timestamp long start,
                                                                 @Timestamp long end,
                                                                 @Default Set<DBNation> nationFilter,
                                                                 @Switch("t") boolean dontRequireTagged) throws Exception {
        long turnStart = TimeUtil.getTurn(start);
        long turnEnd = TimeUtil.getTurn(end);
        if (turnEnd - turnStart > 365L * 12L) {
            throw new IllegalArgumentException("Timeframe is too large");
        }

        TaxRecordCategorizer2 categorized = createTimeCategorizer(db, start, end, nationFilter, dontRequireTagged);
        Map<Integer, Map<TaxRecordCategorizer2.TransactionType, double[][]>> transfersByTaxId = createTransfersByTaxId(categorized, turnStart, turnEnd);
        Map<TaxRecordCategorizer2.TransactionType, double[][]> totalTransfers = createEmptyTransfers(turnStart, turnEnd);
        for (Map<TaxRecordCategorizer2.TransactionType, double[][]> transfers : transfersByTaxId.values()) {
            addTransfers(totalTransfers, transfers);
        }
        return new TaxExpenseTimeResources(createResourceSeries(totalTransfers));
    }
}
