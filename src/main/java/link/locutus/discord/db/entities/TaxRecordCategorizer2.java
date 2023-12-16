package link.locutus.discord.db.entities;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.commands.rankings.table.TimeNumericTable;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.apiv1.enums.ResourceType;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class TaxRecordCategorizer2 {
    private final GuildDB db;
    private final Set<Integer> aaIds;
    private final Set<Integer> alliances;
    private final boolean dontRequireGrant;
    private final boolean dontRequireTagged;
    private final boolean dontRequireExpiry;
    private final boolean includeDeposits;
    private final Predicate<Integer> acceptsNation;

    private final Map<Integer, TaxBracket> brackets;
    private final List<BankDB.TaxDeposit> taxes;
    private final Map<Integer, TaxBracket> bracketsByNation;
    private final Map<Integer, List<DBNation>> nationsByBracket;
    private final List<DBNation> allNations;
    private final Map<Integer, Map<Integer, Integer>> bracketToNationDepositCount;
    private final Map<Integer, Integer> allNationDepositCount;
    private final Map<Integer, double[]> incomeByBracket;
    private final Map<Integer, double[]> incomeByNation;
    private final Map<Integer, Map<Integer, double[]>> incomeByNationByBracket;
    private final Map<Integer, List<Transaction2>> transactionsByNation;

    private final Map<Integer, List<Transaction2>> transactionsByBracket;
    private final Map<Integer, List<Map.Entry<Transaction2, TransactionType>>> transactionsByBracketByType;

    private final Map<Integer, Map<Integer, List<Transaction2>>> transactionsByNationByBracket;
    private final List<Transaction2> expenseTransfers;
    private final Map<Integer, double[]> expensesByBracket;
    private final Map<Integer, double[]> expensesByNation;
    private final Map<Integer, Map<Integer, double[]>> expensesByNationByBracket;
    private final double[] incomeTotal;
    private final double[] expenseTotal;


    public enum TransactionType {
        TAX(false),
        DEPOSIT(false),
        WITHDRAWAL(true),
        GRANT(true)

        ;

        public final boolean isExpense;

        TransactionType(boolean isExpense) {
            this.isExpense = isExpense;
        }
    }

    public double[][] cumulative(double[][] byTurn) {
        double[] previous = byTurn[0];
        for (int i = 1; i < byTurn.length; i++) {
            double[] current = byTurn[i];
            for (int j = 0; j < current.length; j++) {
                current[j] += previous[j];
            }
            previous = current;
        }
        return byTurn;
    }

    public double[][] movingAverage(double[][] byTurn, int window) {
        if (window <= 1) throw new IllegalArgumentException("Windows must be >1");
        double[][] copy = new double[byTurn.length][ResourceType.values.length];

        double[] total = ResourceType.getBuffer();
        for (int i = 0; i < byTurn.length; i++) {
            int j = i - window + 1;

            double[] current = byTurn[i];
            total = PnwUtil.add(total, current);
            if (j >= 0) {
                double[] previous = byTurn[j];
                total = ArrayUtil.apply((x, y) -> x - y, total, previous);
            }

            double[] output = copy[i];
            int amt = i - Math.max(j, 0) + 1;
            for (int k = 0; k < total.length; k++) {
                output[k] = total[k] / amt;
            }
        }
        return copy;
    }

    public TimeNumericTable createTable(String title, Map<TaxRecordCategorizer2.TransactionType, double[][]> transfers, ResourceType valueType) {
        if (transfers.isEmpty()) throw new IllegalArgumentException("No transfers found");

        String[] labels = new String[transfers.size() + 2];
        labels[0] = "Expense Total";
        labels[1] = "Income Total";

        String labelY = valueType == null ? "Market Value ($)" : valueType.name();

        Function<double[], Double> getValue = input -> valueType != null ? input[valueType.ordinal()] : PnwUtil.convertedTotal(input);

        double[] buffer = new double[labels.length];

        TransactionType[] txTypes = transfers.keySet().toArray(new TransactionType[0]);
        for (int i = 0; i < txTypes.length; i++) {
            TransactionType txType = txTypes[i];
            labels[2 + i] = txType.name();
        }

        TimeNumericTable<Void> table = new TimeNumericTable<>(title, "group by", labelY, labels) {
            @Override
            public void add(long key, Void ignore) {
                buffer[0] = 0;
                buffer[1] = 0;
                for (int i = 0; i < txTypes.length; i++) {
                    TransactionType txType = txTypes[i];
                    double[] typeIncome = transfers.get(txType)[(int) key];
                    double value = getValue.apply(typeIncome);

                    buffer[i + 2] = value;
                    if (txType.isExpense) buffer[0] += value;
                    else buffer[1] += value;
                }
                add(key, buffer);
            }
        };

        double[][] arr0 = transfers.values().iterator().next();
        for (int i = 0; i < arr0.length; i++) {
            table.add(i, (Void) null);
        }
        return table;
    }

    public Map<TaxRecordCategorizer2.TransactionType, double[][]> sumTransfersByCategoryByTurn(long turnStart, long turnEnd, List<BankDB.TaxDeposit> taxRecords, List<Map.Entry<Transaction2, TaxRecordCategorizer2.TransactionType>> transfers) {
        int len = (int) (turnEnd - turnStart + 1);

        double[][] totalGrantsByTurn = new double[len][ResourceType.values.length];
        double[][] totalWithdrawalsByTurn = new double[len][ResourceType.values.length];

        double[][] totalTaxByTurn = new double[len][ResourceType.values.length];
        double[][] totalDepositsByTurn = new double[len][ResourceType.values.length];

        for (BankDB.TaxDeposit record : taxRecords) {
            long turn = record.getTurn();
            int turnRel = (int) (turn - turnStart);
            if (turnRel >= totalTaxByTurn.length || turnRel < 0) continue;
            if (!acceptsNation.test(record.nationId)) continue;

            PnwUtil.add(totalTaxByTurn[turnRel], record.resources);
        }

        for (Map.Entry<Transaction2, TaxRecordCategorizer2.TransactionType> transfer : transfers) {
            Transaction2 record = transfer.getKey();
            if ((!acceptsNation.test((int) record.receiver_id) || !record.isReceiverNation()) && (!acceptsNation.test((int) record.sender_id) || !record.isSenderNation())) continue;
            double[][] type;
            switch (transfer.getValue()) {
                case TAX:
                    type = totalTaxByTurn;
                    break;
                case DEPOSIT:
                    type = totalDepositsByTurn;
                    break;
                case WITHDRAWAL:
                    type = totalWithdrawalsByTurn;
                    break;
                case GRANT:
                    type = totalGrantsByTurn;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown type " + transfer.getValue());
            }
            long turn = TimeUtil.getTurn(record.tx_datetime);
            int turnRel = (int) (turn - turnStart);
            if (turnRel >= type.length || turnRel < 0) continue;

            PnwUtil.add(type[turnRel], record.resources);
        }

        Map<TransactionType, double[][]> result = new EnumMap<>(TransactionType.class);
        result.put(TransactionType.GRANT, totalGrantsByTurn);
        result.put(TransactionType.WITHDRAWAL, totalWithdrawalsByTurn);
        result.put(TransactionType.DEPOSIT, totalDepositsByTurn);
        result.put(TransactionType.TAX, totalTaxByTurn);
        return result;
    }


    public Map<Integer, List<Map.Entry<Transaction2, TransactionType>>> getTransactionsByBracketByType() {
        return transactionsByBracketByType;
    }

    public TaxRecordCategorizer2(GuildDB db, long start, long end, boolean dontRequireGrant, boolean dontRequireTagged, boolean dontRequireExpiry, boolean includeDeposits, Predicate<Integer> acceptsNation, Consumer<String> errors) throws Exception {
        this.db = db;
        this.aaIds = db.getAllianceIds();
        this.dontRequireGrant = dontRequireGrant;
        this.dontRequireTagged = dontRequireTagged;
        this.dontRequireExpiry = dontRequireExpiry;
        this.includeDeposits = includeDeposits;
        this.acceptsNation = acceptsNation;

        Set<Predicate<Transaction2>> expenseRequirements = new HashSet<>();
        if (!dontRequireTagged) {
            expenseRequirements.add(tx -> {
                if (aaIds.contains((int) tx.sender_id) && tx.isSenderAA()) return true;
                Map<String, String> notes = PnwUtil.parseTransferHashNotes(tx.note);
                for (String value : notes.values()) {
                    if (MathMan.isInteger(value)) {
                        if (aaIds.contains((int) Long.parseLong(value))) return true;
                    }
                }
                return false;
            });
        }
        if (!dontRequireGrant) {
            expenseRequirements.add(tx -> tx.note != null && tx.note.contains("#grant"));
        }
        if (!dontRequireExpiry) {
            expenseRequirements.add(tx -> tx.note != null && (tx.note.contains("#expire") || tx.note.contains("#decay")));
        }

        this.alliances = new HashSet<>();
        alliances.addAll(aaIds);
        alliances.addAll(db.getCoalition(Coalition.OFFSHORE));

        this.taxes = new ArrayList<>();
        for (int aaId : aaIds) {
            taxes.addAll(Locutus.imp().getBankDB().getTaxesByAA(aaId));
        }
        this.taxes.removeIf(f -> !acceptsNation.test(f.nationId));
        getTaxes().removeIf(f -> f.date < start || f.date > end);

        this.brackets = new HashMap<>(db.getAllianceList().getTaxBrackets(true));
        for (int i = getTaxes().size() - 1; i >= 0; i--) {
            BankDB.TaxDeposit tax = getTaxes().get(i);
            if (tax.tax_id > 0 && !brackets.containsKey(tax.tax_id)) {
                TaxBracket bracket = new TaxBracket(tax.tax_id, tax.allianceId, "", tax.moneyRate, tax.resourceRate, System.currentTimeMillis());
                brackets.put(tax.tax_id, bracket);
            }
        }

        this.allNations = new ArrayList<>(db.getAllianceList().getNations(true, 0, true));
        this.allNations.removeIf(f -> !acceptsNation.test(f.getNation_id()));
        this.nationsByBracket = new Int2ObjectOpenHashMap<>();
        this.bracketsByNation = new Int2ObjectOpenHashMap<>();
        Map<Long, List<TaxBracket>> bracketsByTaxRatePair = new Long2ObjectOpenHashMap<>();

        for (Map.Entry<Integer, TaxBracket> entry : getBrackets().entrySet()) {
            TaxBracket bracket = entry.getValue();
            long pair = MathMan.pairInt(bracket.moneyRate, bracket.rssRate);
            bracketsByTaxRatePair.computeIfAbsent(pair, f -> new ArrayList<>()).add(bracket);

            Set<DBNation> nations = bracket.getNations();
            nations.removeIf(f -> f.getPosition() <= 1);
            if (!nations.isEmpty()) {
                getNationsByBracket().put(bracket.taxId, new ArrayList<>(nations));
                for (DBNation nation : nations) {
                    if (acceptsNation.test(nation.getNation_id())) {
                        getBracketsByNation().put(nation.getNation_id(), bracket);
                    }
                }
            }
        }

        Map<Integer, List<BankDB.TaxDeposit>> depositsByBracket = new Int2ObjectOpenHashMap<>();

        Map<Integer, Map<Long, Integer>> nationToBracketByTimeMap = new Int2ObjectOpenHashMap<>();
        this.allNationDepositCount = new Int2ObjectOpenHashMap<>();
        this.bracketToNationDepositCount = new Int2ObjectOpenHashMap<>();

        for (BankDB.TaxDeposit tax : getTaxes()) {
            if (tax.tax_id <= 0) {
                TaxBracket currentBracket = getBracketsByNation().get(tax.nationId);
                if (currentBracket != null && currentBracket.moneyRate == tax.moneyRate && currentBracket.rssRate == tax.resourceRate) {
                    tax.tax_id = currentBracket.taxId;
                } else {
                    long pair = MathMan.pairInt(tax.moneyRate, tax.resourceRate);
                    List<TaxBracket> possible = bracketsByTaxRatePair.get(pair);
                    if (possible != null) {
                        tax.tax_id = possible.get(0).taxId;
                    }
                }
            }

            long turn = TimeUtil.getTurn(tax.date);
            Map<Long, Integer> nationBracketByTime = nationToBracketByTimeMap.computeIfAbsent(tax.nationId, f -> new Long2IntOpenHashMap());
            nationBracketByTime.put(turn, tax.tax_id);

            depositsByBracket.computeIfAbsent(tax.tax_id, f -> new ArrayList<>()).add(tax);

            Map<Integer, Integer> nationDepositCount = getBracketToNationDepositCount().computeIfAbsent(tax.tax_id, f -> new Int2IntOpenHashMap());
            nationDepositCount.put(tax.nationId, nationDepositCount.getOrDefault(tax.nationId, 0) + 1);
            getAllNationDepositCount().put(tax.nationId, getAllNationDepositCount().getOrDefault(tax.nationId, 0) + 1);
        }

        // transactions from alliance bank & any registered offshores
        this.expenseTransfers = new ArrayList<>();
        for (Integer senderAAId : getAlliances()) {
            getExpenseTransfers().addAll(Locutus.imp().getBankDB().getTransactionsByAllianceSender(senderAAId));
        }

        getExpenseTransfers().removeIf(f -> !f.isReceiverNation());
        getExpenseTransfers().removeIf(f -> f.tx_datetime < start || f.tx_datetime > end);
        for (Predicate<Transaction2> requirement : expenseRequirements) {
            getExpenseTransfers().removeIf(f -> !requirement.test(f));
        }

        if (includeDeposits) {
            List<Transaction2> depositTransfers = new ArrayList<>();
            for (Integer senderAAId : getAlliances()) {
                depositTransfers.addAll(Locutus.imp().getBankDB().getTransactionsByAllianceReceiver(senderAAId));
                depositTransfers.removeIf(f -> !f.isSenderNation());
                depositTransfers.removeIf(f -> f.tx_datetime < start || f.tx_datetime > end);
            }
            getExpenseTransfers().addAll(depositTransfers);
        }
        getExpenseTransfers().removeIf(f -> ((!acceptsNation.test((int) f.receiver_id) || !f.isReceiverNation()) && (!acceptsNation.test((int) f.sender_id) || !f.isSenderNation())));

        Collections.sort(getExpenseTransfers(), new Comparator<Transaction2>() {
            @Override
            public int compare(Transaction2 o1, Transaction2 o2) {
                return Long.compare(o1.tx_datetime, o2.tx_datetime);
            }
        });

        long minTurn = TimeUtil.getTurn(start);
        long maxTurn = TimeUtil.getTurn(end);

        this.transactionsByNation = new Int2ObjectOpenHashMap<>();
        this.transactionsByBracket = new Int2ObjectOpenHashMap<>();
        this.transactionsByBracketByType = new Int2ObjectOpenHashMap<>();
        this.transactionsByNationByBracket = new Int2ObjectOpenHashMap<>();
        for (Transaction2 transfer : getExpenseTransfers()) {
            int nationId = (int) (transfer.isSenderNation() ? transfer.sender_id : transfer.receiver_id);
            long turnStart = TimeUtil.getTurn(transfer.tx_datetime);
            Integer taxId = null;
            Map<Long, Integer> bracketByTime = nationToBracketByTimeMap.get(nationId);
            if (bracketByTime != null) {
                for (long turn = turnStart; turn <= maxTurn && taxId == null; turn++) {
                    taxId = bracketByTime.get(turn);
                }
            }
            if (taxId == null) {
                TaxBracket bracket = getBracketsByNation().get(nationId);
                if (bracket != null) taxId = bracket.taxId;
            }
            if (taxId == null) {
                try {
                    errors.accept("Unknown tax bracket for:- " + transfer.toSimpleString());
                } catch (Throwable e) {
                    e.printStackTrace();
                    throw e;
                }
                continue;
            }
            getTransactionsByBracket().computeIfAbsent(taxId, f -> new ArrayList<>()).add(transfer);

            TransactionType type;
            if (transfer.isSenderNation()) {
                type = TransactionType.DEPOSIT;
            } else if (transfer.note != null && transfer.note.toLowerCase().contains("#expire")) {
                type = TransactionType.GRANT;
            } else {
                type = TransactionType.WITHDRAWAL;
            }
            transactionsByBracketByType.computeIfAbsent(taxId, f -> new ArrayList<>()).add(new AbstractMap.SimpleEntry<>(transfer, type));
            getTransactionsByNation().computeIfAbsent(nationId, f -> new ArrayList<>()).add(transfer);
            getTransactionsByNationByBracket().computeIfAbsent(taxId, f -> new Int2ObjectOpenHashMap<>()).computeIfAbsent(nationId, f -> new ArrayList<>()).add(transfer);
        }

        this.incomeByNationByBracket = new Int2ObjectOpenHashMap<>();

        /// expenses ///

        this.expenseTotal = ResourceType.getBuffer();
        this.expensesByNation = new Int2ObjectOpenHashMap<>();
        this.expensesByBracket = new Int2ObjectOpenHashMap<>();
        this.expensesByNationByBracket = new Int2ObjectOpenHashMap<>();

        for (Map.Entry<Integer, List<Transaction2>> entry : getTransactionsByBracket().entrySet()) {
            int id = entry.getKey();
            List<Transaction2> transfers = entry.getValue();
            for (Transaction2 transfer : transfers) {

                int nationId = (int) (transfer.isSenderNation() ? transfer.sender_id : transfer.receiver_id);

                Map<Integer, Map<Integer, double[]>> myMap;
                if (transfer.isSenderNation()) {
                    myMap = getIncomeByNationByBracket();
                } else {
                    myMap = getExpensesByNationByBracket();
                }

                double[] totalByNation = myMap.computeIfAbsent(id, f -> new Int2ObjectOpenHashMap<>()).computeIfAbsent(nationId, f -> ResourceType.getBuffer());
                ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, totalByNation, transfer.resources);
            }
        }

        for (Map.Entry<Integer, Map<Integer, double[]>> entry : getExpensesByNationByBracket().entrySet()) {
            int taxId = entry.getKey();
            Map<Integer, double[]> byNation = entry.getValue();
            for (Map.Entry<Integer, double[]> entry2 : byNation.entrySet()) {
                int nationId = entry2.getKey();
                double[] resources = entry2.getValue();
                double[] currentTotal = getExpensesByBracket().computeIfAbsent(taxId, f -> ResourceType.getBuffer());
                ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, currentTotal, resources);

                currentTotal = getExpensesByNation().computeIfAbsent(nationId, f -> ResourceType.getBuffer());
                ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, currentTotal, resources);
            }
        }

        for (Map.Entry<Integer, double[]> entry : getExpensesByBracket().entrySet()) {
            ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, getExpenseTotal(), entry.getValue());
        }

        ////  income  ////

        this.incomeTotal = ResourceType.getBuffer();
        this.incomeByBracket = new Int2ObjectOpenHashMap<>();
        this.incomeByNation = new Int2ObjectOpenHashMap<>();

        TaxRate taxBase = db.getOrNull(GuildKey.TAX_BASE);
        if (taxBase == null) taxBase = new TaxRate(100, 100);
        else {
            taxBase = new TaxRate(taxBase.money, taxBase.resources);
        }
        if (taxBase.money < 0) taxBase.money = 100;
        if (taxBase.resources < 0) taxBase.resources = 100;


        int[] internalTaxRate = new int[2];
        for (Map.Entry<Integer, List<BankDB.TaxDeposit>> entry : depositsByBracket.entrySet()) {
            int taxId = entry.getKey();
            List<BankDB.TaxDeposit> records = entry.getValue();
            for (BankDB.TaxDeposit tax : records) {
                if (tax.internalMoneyRate > 0) internalTaxRate[0] = tax.internalMoneyRate;
                else internalTaxRate[0] = taxBase.money;
                if (tax.internalResourceRate > 0) internalTaxRate[1] = tax.internalResourceRate;
                else internalTaxRate[1] = taxBase.resources;

                tax.multiplyBaseInverse(internalTaxRate);

                double[] currentTotal = getIncomeByNationByBracket().computeIfAbsent(taxId, f -> new Int2ObjectOpenHashMap<>()).computeIfAbsent(tax.nationId, f -> ResourceType.getBuffer());
                ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, currentTotal, tax.resources);
            }
        }

        for (Map.Entry<Integer, Map<Integer, double[]>> entry : getIncomeByNationByBracket().entrySet()) {
            int taxId = entry.getKey();
            Map<Integer, double[]> byNation = entry.getValue();
            for (Map.Entry<Integer, double[]> entry2 : byNation.entrySet()) {
                int nationId = entry2.getKey();
                double[] resources = entry2.getValue();
                double[] currentTotal = getIncomeByBracket().computeIfAbsent(taxId, f -> ResourceType.getBuffer());
                ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, currentTotal, resources);

                currentTotal = getIncomeByNation().computeIfAbsent(nationId, f -> ResourceType.getBuffer());
                ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, currentTotal, resources);
            }
        }

        for (Map.Entry<Integer, double[]> entry : getIncomeByBracket().entrySet()) {
            ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, getIncomeTotal(), entry.getValue());
        }
    }

    public GuildDB getDb() {
        return db;
    }

    public Set<Integer> getAllianceIds() {
        return aaIds;
    }

    public Set<Integer> getAlliances() {
        return alliances;
    }

    public boolean isDontRequireGrant() {
        return dontRequireGrant;
    }

    public boolean isDontRequireTagged() {
        return dontRequireTagged;
    }

    public boolean isDontRequireExpiry() {
        return dontRequireExpiry;
    }

    public boolean isIncludeDeposits() {
        return includeDeposits;
    }

    public Map<Integer, TaxBracket> getBrackets() {
        return brackets;
    }

    public List<BankDB.TaxDeposit> getTaxes() {
        return taxes;
    }

    public Map<Integer, TaxBracket> getBracketsByNation() {
        return bracketsByNation;
    }

    public Map<Integer, List<DBNation>> getNationsByBracket() {
        return nationsByBracket;
    }

    public List<DBNation> getAllNations() {
        return allNations;
    }

    public Predicate<Integer> getAcceptsNation() {
        return acceptsNation;
    }

    public Map<Integer, Map<Integer, Integer>> getBracketToNationDepositCount() {
        return bracketToNationDepositCount;
    }

    public Map<Integer, Integer> getAllNationDepositCount() {
        return allNationDepositCount;
    }

    public Map<Integer, double[]> getIncomeByBracket() {
        return incomeByBracket;
    }

    public Map<Integer, double[]> getIncomeByNation() {
        return incomeByNation;
    }

    public Map<Integer, Map<Integer, double[]>> getIncomeByNationByBracket() {
        return incomeByNationByBracket;
    }

    public Map<Integer, List<Transaction2>> getTransactionsByNation() {
        return transactionsByNation;
    }

    public Map<Integer, List<Transaction2>> getTransactionsByBracket() {
        return transactionsByBracket;
    }

    public Map<Integer, Map<Integer, List<Transaction2>>> getTransactionsByNationByBracket() {
        return transactionsByNationByBracket;
    }

    public List<Transaction2> getExpenseTransfers() {
        return expenseTransfers;
    }

    public Map<Integer, double[]> getExpensesByBracket() {
        return expensesByBracket;
    }

    public Map<Integer, double[]> getExpensesByNation() {
        return expensesByNation;
    }

    public Map<Integer, Map<Integer, double[]>> getExpensesByNationByBracket() {
        return expensesByNationByBracket;
    }

    public double[] getIncomeTotal() {
        return incomeTotal;
    }

    public double[] getExpenseTotal() {
        return expenseTotal;
    }
}
