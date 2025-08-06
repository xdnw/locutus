package link.locutus.discord.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.FlowType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.TaxDeposit;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.sheet.templates.NationBalanceRow;

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FetchDeposit {
    private final GuildDB db;
    private final Collection<DBNation> nations;
    private Set<Long> tracked;
    private boolean includeTaxes;
    private boolean useTaxBase;
    private boolean offset;
    private long start;
    private long end;
    private boolean forceIncludeExpired;
    private boolean forceIncludeIgnored;
    private Predicate<Transaction2> filter;
    private boolean update;
    private boolean priority;

    private BiConsumer<Transaction2, Long> getExpiring;

    private  boolean fetchLastDeposit;
    private Map<Integer, Long> lastDepositMap;

    private boolean fetchLastSelfWithdrawal;
    private Map<Integer, Long> lastSelfWithdrawalMap;

    private DepositType flowNote;
    private Map<Integer, Map<FlowType, double[]>> flowDepositsMap;

    private Map<DBNation, Map<DepositType, double[]>> result;

    public FetchDeposit(GuildDB db, Collection<DBNation> nations) {
        this.db = db;
        this.nations = nations;
        this.start = 0;
        this.end = Long.MAX_VALUE;
        this.includeTaxes = true;
        this.useTaxBase = true;
        this.offset = true;
        forceIncludeExpired = false;
        forceIncludeIgnored = false;
    }

    public FetchDeposit setFetchLastDeposit(boolean fetchLastDeposit) {
        this.fetchLastDeposit = fetchLastDeposit;
        return this;
    }

    public FetchDeposit setFetchLastSelfWithdrawal(boolean fetchLastSelfWithdrawal) {
        this.fetchLastSelfWithdrawal = fetchLastSelfWithdrawal;
        return this;
    }

    public FetchDeposit setFlowNote(DepositType flowNote) {
        this.flowNote = flowNote;
        return this;
    }

    public Set<Long> getTracked() {
        if (tracked == null) {
            tracked = db.getTrackedBanks();
        }
        return tracked;
    }

    public FetchDeposit setUpdate(boolean update, boolean priority) {
        this.update = update;
        this.priority = priority;
        return this;
    }

    public FetchDeposit setFilter(Predicate<Transaction2> filter) {
        this.filter = filter;
        return this;
    }

    public FetchDeposit setIncludeExpired(boolean forceIncludeExpired) {
        this.forceIncludeExpired = forceIncludeExpired;
        return this;
    }

    public FetchDeposit setIncludeIgnored(boolean forceIncludeIgnored) {
        this.forceIncludeIgnored = forceIncludeIgnored;
        return this;
    }

    public FetchDeposit setGetExpiring(BiConsumer<Transaction2, Long> getExpiring) {
        this.getExpiring = getExpiring;
        return this;
    }

    public FetchDeposit setStart(long start) {
        this.start = start;
        return this;
    }

    public FetchDeposit setEnd(long end) {
        this.end = end;
        return this;
    }

    public FetchDeposit setOffset(boolean offset) {
        this.offset = offset;
        return this;
    }

    public FetchDeposit setUseTaxBase(boolean useTaxBase) {
        this.includeTaxes |= useTaxBase;
        this.useTaxBase = useTaxBase;
        return this;
    }

    public FetchDeposit setIncludeTaxes(boolean includeTaxes) {
        this.includeTaxes = includeTaxes;
        return this;
    }

    public FetchDeposit setTracked(Set<Long> tracked) {
        this.tracked = tracked;
        return this;
    }

    public synchronized FetchDeposit execute() {
        if (this.result != null) return this;
        getTracked(); // Init tracked if not already set

        // sorted linked hash set fastutil
        List<Integer> nationIdsSorted = new IntArrayList(nations.size());
        for (DBNation nation : nations) {
            nationIdsSorted.add(nation.getNation_id());
        }
        nationIdsSorted.sort(Comparator.naturalOrder());
        Set<Integer> nationIds = new IntLinkedOpenHashSet(nationIdsSorted);

        Future<?> updateFuture = null;
        if (update) {
            updateFuture = Locutus.imp().runEventsAsync(events -> Locutus.imp().getBankDB().updateBankRecsAuto(nationIds, priority, events));
        }

        Map<Integer, Map<DepositType, double[]>> results = new Int2ObjectOpenHashMap<>();
        Map<Integer, BiConsumer<Integer, Transaction2>> adderByNation = new Int2ObjectOpenHashMap<>();
        Set<Long> finalTracked = tracked;

        Function<Integer, BiConsumer<Integer, Transaction2>> getAdder = (nationId) -> {
            BiConsumer<Integer, Transaction2> adder = adderByNation.get(nationId);
            if (adder != null) return adder;
            Map<DepositType, double[]> result = results.computeIfAbsent(nationId, k -> new EnumMap<>(DepositType.class));
            DBNation nation = DBNation.getOrCreate(nationId);
            BiConsumer<Integer, Transaction2> parent = PW.createSumNationTransactions(nation, db, finalTracked, forceIncludeExpired, forceIncludeIgnored, filter, result);
            adderByNation.put(nationId, parent);
            return parent;
        };

        Consumer<Transaction2> applyAdder = (tx) -> {
            int nationId;
            if (tx.sender_type == 1) {
                if (tx.receiver_type == 1) {
                    return; // Ignore transactions between nations
                }
                nationId = Math.toIntExact(tx.sender_id);
            } else if (tx.receiver_type == 1) {
                nationId = Math.toIntExact(tx.receiver_id);
            } else {
                return; // Ignore transactions that does not involve a nation
            }
            BiConsumer<Integer, Transaction2> adder = getAdder.apply(nationId);
            Integer sign = PW.getSign(tx, nationId, finalTracked);
            if (sign == null) return; // Ignore transactions that are not in a tracked alliance/guild
            adder.accept(sign, tx);
        };
        if (getExpiring != null) {
            applyAdder = applyAdder.andThen(record -> {
                if (record.note == null || record.note.isEmpty()) return;
                boolean isOffshoreSender = (record.sender_type == 2 || record.sender_type == 3) && record.receiver_type == 1;
                if (!isOffshoreSender && !record.isInternal()) return;
                Map<DepositType, Object> noteMap = record.getNoteMap();
                Object expireVal = noteMap.get(DepositType.EXPIRE);
                Object decayVal = noteMap.get(DepositType.DECAY);
                Long dateVal;
                if (decayVal instanceof Number n) {
                    dateVal = n.longValue();
                } else if (expireVal instanceof Number n) {
                    dateVal = n.longValue();
                } else {
                    return;
                }
                getExpiring.accept(record, dateVal);
            });
        }
        Consumer<Transaction2> applyAdderFinal = applyAdder;

        if (offset) {
            Consumer<Transaction2> finalApplyAdder = applyAdder;
            db.iterateTransactionsByIds(nationIds, 1, start, end, tx -> {
                tx.tx_id = -1;
                finalApplyAdder.accept(tx);
            });
        }

        if (includeTaxes) { // Taxes
            int[] guildTaxBase = new int[]{100, 100};
            TaxRate defTaxBaseRate = db.getOrNull(GuildKey.TAX_BASE);
            if (defTaxBaseRate != null) {
                guildTaxBase[0] = defTaxBaseRate.money;
                guildTaxBase[1] = defTaxBaseRate.resources;
            }

            Set<Integer> trackForTaxes = tracked.stream().filter(f -> f <= Integer.MAX_VALUE && db.isAllianceId(f.intValue())).map(Long::intValue).collect(Collectors.toSet());
            DepositType note = DepositType.TAX;

            if (start == 0 && end == Long.MAX_VALUE) {
                Map<Integer, double[]> appliedDeposits = Locutus.imp().getBankDB().getAppliedTaxDeposits(nationIds, trackForTaxes, guildTaxBase, useTaxBase);
                for (Map.Entry<Integer, double[]> entry : appliedDeposits.entrySet()) {
                    int nationId = entry.getKey();
                    double[] taxAmt = entry.getValue();
                    if (ResourceType.isZero(taxAmt)) continue;

                    double[] depos = results.computeIfAbsent(nationId, k -> new EnumMap<>(DepositType.class)).computeIfAbsent(note, f -> ResourceType.getBuffer());
                    ResourceType.add(depos, taxAmt);
                }
            } else {
                int[] defTaxBase = guildTaxBase.clone();
                if (!useTaxBase) {
                    defTaxBase[0] = 0;
                    defTaxBase[1] = 0;
                }

                boolean includeNoInternal = defTaxBase[0] != 100 || defTaxBase[1] != 100;
                boolean includeMaxInternal = false;
                Locutus.imp().getBankDB().iterateTaxesPaid(nationIds, trackForTaxes, includeNoInternal, includeMaxInternal, start, end, new Consumer<TaxDeposit>() {
                    @Override
                    public void accept(TaxDeposit deposit) {
                        int internalMoneyRate = useTaxBase ? deposit.internalMoneyRate : 0;
                        int internalResourceRate = useTaxBase ? deposit.internalResourceRate : 0;
                        if (internalMoneyRate < 0 || internalMoneyRate > 100) internalMoneyRate = defTaxBase[0];
                        if (internalResourceRate < 0 || internalResourceRate > 100) internalResourceRate = defTaxBase[1];

                        double pctMoney = (deposit.moneyRate > internalMoneyRate ?
                                Math.max(0, (deposit.moneyRate - internalMoneyRate) / (double) deposit.moneyRate)
                                : 0);
                        double pctRss = (deposit.resourceRate > internalResourceRate ?
                                Math.max(0, (deposit.resourceRate - internalResourceRate) / (double) deposit.resourceRate)
                                : 0);

                        if (pctMoney == 0 && pctRss == 0) {
                            return;
                        }

                        deposit.resources[0] *= pctMoney;
                        for (int i = 1; i < deposit.resources.length; i++) {
                            deposit.resources[i] *= pctRss;
                        }
                        Transaction2 transaction = new Transaction2(deposit);
                        applyAdderFinal.accept(transaction);
                    }
                });
            }
        }


        { // Transactions
            if (updateFuture != null) {
                FileUtil.get(updateFuture);
            }
            Consumer<Transaction2> applyNationTransfers = applyAdder;

            if (this.fetchLastDeposit) {
                lastDepositMap = new Int2ObjectOpenHashMap<>();
                applyNationTransfers = applyNationTransfers.andThen(tx -> {
                    if (tx.sender_type == 1) {
                        int nationId = Math.toIntExact(tx.sender_id);
                        long current = lastDepositMap.getOrDefault(nationId, Long.MIN_VALUE);
                        if (tx.tx_datetime > current) {
                            lastDepositMap.put(nationId, tx.tx_datetime);
                        }
                    }
                });
            } else {
                lastDepositMap = null;
            }

            if (this.fetchLastSelfWithdrawal) {
                lastSelfWithdrawalMap = new Int2ObjectOpenHashMap<>();
                applyNationTransfers = applyNationTransfers.andThen(tx -> {
                    if (tx.receiver_type == 1 && tx.isSelfWithdrawal((int) tx.receiver_id)) {
                        int nationId = Math.toIntExact(tx.receiver_id);
                        long current = lastSelfWithdrawalMap.getOrDefault(nationId, Long.MIN_VALUE);
                        if (tx.tx_datetime > current) {
                            lastSelfWithdrawalMap.put(nationId, tx.tx_datetime);
                        }
                    }
                });
            } else {
                lastSelfWithdrawalMap = null;
            }

            if (flowNote != null) {
                flowDepositsMap = new Int2ObjectOpenHashMap<>();

                applyNationTransfers = applyNationTransfers.andThen(tx -> {
                    int nationId = tx.receiver_type == 1 ? Math.toIntExact(tx.receiver_id)  : Math.toIntExact(tx.sender_id);
                    Integer sign = PW.getSign(tx, nationId, finalTracked);
                    if (sign == null) return; // Ignore transactions that are not in a tracked alliance

                    Map<FlowType, double[]> natFlow = flowDepositsMap.computeIfAbsent(nationId, k -> new EnumMap<>(FlowType.class));
                    for (FlowType flowType : FlowType.VALUES) {
                        double[] total = natFlow.computeIfAbsent(flowType, k -> ResourceType.getBuffer());
                        double[] added = flowType.addTotal(total, sign, tx, nationId);
                        ResourceType.add(total, added);
                    }
                });
            } else {
                flowDepositsMap = null;
            }

            Consumer<Transaction2> applyNationTransfersFinal = applyNationTransfers;
            Locutus.imp().getBankDB().iterateNationTransfersByNation(start, end, nationIds, (id, tx) -> {
                applyNationTransfersFinal.accept(tx);
            }, false);
        }

        Map<DBNation, Map<DepositType, double[]>> resultInstance = new Object2ObjectOpenHashMap<>();
        for (Map.Entry<Integer, Map<DepositType, double[]>> entry : results.entrySet()) {
            int nationId = entry.getKey();
            Map<DepositType, double[]> deposits = entry.getValue();
            DBNation nation = DBNation.getOrCreate(nationId);
            resultInstance.put(nation, deposits);
        }
        this.result = resultInstance;
        return this;
    }

    public Map<DBNation, Map<DepositType, double[]>> getResult() {
        execute();
        return result;
    }

    public NationBalanceRow createRow(DBNation nation, boolean noGrants, boolean noLoans, boolean noTaxes, boolean noDeposits) {
        List<Object> row = new ObjectArrayList<>();

        Map<DepositType, double[]> deposits = getResult().getOrDefault(nation, Collections.emptyMap());
        double[] buffer = ResourceType.getBuffer();

        row.add(MarkupUtil.sheetUrl(nation.getNation(), nation.getUrl()));
        row.add(nation.getCities());
        row.add(nation.getAgeDays());
        row.add(String.format("%.2f", ResourceType.convertedTotal(deposits.getOrDefault(DepositType.DEPOSIT, buffer))));
        row.add(String.format("%.2f", ResourceType.convertedTotal(deposits.getOrDefault(DepositType.TAX, buffer))));
        row.add(String.format("%.2f", ResourceType.convertedTotal(deposits.getOrDefault(DepositType.LOAN, buffer))));
        row.add(String.format("%.2f", ResourceType.convertedTotal(deposits.getOrDefault(DepositType.GRANT, buffer))));
        double[] total = ResourceType.getBuffer();
        for (Map.Entry<DepositType, double[]> entry : deposits.entrySet()) {
            switch (entry.getKey()) {
                case GRANT:
                    if (noGrants) continue;
                    break;
                case LOAN:
                    if (noLoans) continue;
                    break;
                case TAX:
                    if (noTaxes) continue;
                    break;
                case DEPOSIT:
                    if (noDeposits) continue;
                    break;
            }
            double[] value = entry.getValue();
            total = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, total, value);
        }
        row.add(String.format("%.2f", ResourceType.convertedTotal(total)));

        long lastDeposit = lastDepositMap != null ? lastDepositMap.getOrDefault(nation.getId(), 0L) : 0L;
        long lastSelfWithdrawal = lastSelfWithdrawalMap != null ? lastSelfWithdrawalMap.getOrDefault(nation.getId(), 0L) : 0L;

        if (lastDeposit == 0) {
            row.add("NEVER");
        } else {
            long days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastDeposit);
            row.add(days);
        }
        if (lastSelfWithdrawal == 0) {
            row.add("NEVER");
        } else {
            long days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastSelfWithdrawal);
            row.add(days);
        }

        Map<FlowType, double[]> flowDepositsNation = flowDepositsMap == null ? Collections.emptyMap() : flowDepositsMap.getOrDefault(nation.getId(), Collections.emptyMap());
        double[] internal = flowDepositsNation.getOrDefault(FlowType.INTERNAL, ResourceType.getBuffer());
        double[] withdrawal = flowDepositsNation.getOrDefault(FlowType.WITHDRAWAL, ResourceType.getBuffer());
        double[] deposit = flowDepositsNation.getOrDefault(FlowType.DEPOSIT, ResourceType.getBuffer());

        row.add(ResourceType.toString(internal));
        row.add(ResourceType.toString(withdrawal));
        row.add(ResourceType.toString(deposit));

        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS) continue;
            row.add(MathMan.format(total[type.ordinal()]));
        }
        double[] normalized = PW.normalize(total);

        return new NationBalanceRow(row, total, normalized);
    }
}
