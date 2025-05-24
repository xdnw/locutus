package link.locutus.discord.util.sheet.templates;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.FlowType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.math.ArrayUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


public class DepositSheetTask implements Callable<NationBalanceRow> {

    private final DBNation nation;
    private final GuildDB db;
    private final Set<Long> tracked;
    private final boolean useTaxBase;
    private final boolean useOffset;
    private final boolean updateBulk;
    private final boolean force;
    private final DepositType useFlowNote;
    private final boolean includeExpired;
    private final boolean includeIgnored;
    private final int rowSize; // Assuming header structure is fixed
    private final boolean noGrants;
    private final boolean noLoans;
    private final boolean noTaxes;
    private final boolean noDeposits;
    private final AtomicInteger processedCount; // For progress update
    private final int totalNations;
    private final IMessageIO channel;
    private final CompletableFuture<IMessageBuilder> msgFuture;
    private final AtomicLong lastUpdateTimestamp;


    public DepositSheetTask(DBNation nation, GuildDB db, Set<Long> tracked, boolean useTaxBase, boolean useOffset,
                            boolean updateBulk, boolean force, DepositType useFlowNote, boolean includeExpired,
                            boolean includeIgnored, int rowSize, boolean noGrants, boolean noLoans,
                            boolean noTaxes, boolean noDeposits, AtomicInteger processedCount, int totalNations,
                            IMessageIO channel, CompletableFuture<IMessageBuilder> msgFuture, AtomicLong lastUpdateTimestamp) {
        this.nation = nation;
        this.db = db;
        this.tracked = tracked;
        this.useTaxBase = useTaxBase;
        this.useOffset = useOffset;
        this.updateBulk = updateBulk;
        this.force = force;
        this.useFlowNote = useFlowNote;
        this.rowSize = rowSize;
        this.includeExpired = includeExpired;
        this.includeIgnored = includeIgnored;
        this.noGrants = noGrants;
        this.noLoans = noLoans;
        this.noTaxes = noTaxes;
        this.noDeposits = noDeposits;
        this.processedCount = processedCount;
        this.totalNations = totalNations;
        this.channel = channel;
        this.msgFuture = msgFuture;
        this.lastUpdateTimestamp = lastUpdateTimestamp;
    }

    @Override
    public NationBalanceRow call() {
        // --- Logic from the original loop body ---
        List<Object> row = new ObjectArrayList<>(rowSize);

        List<Map.Entry<Integer, Transaction2>> transactions = nation.getTransactions(db, tracked, useTaxBase, useOffset, (!force || updateBulk) ? -1 : 0L, 0L, false);
        List<Map.Entry<Integer, Transaction2>> flowTransfers = transactions;
        if (useFlowNote != null) {
            flowTransfers = flowTransfers.stream().filter(f -> f.getValue().getNoteMap().containsKey(useFlowNote)).toList();
        }
        double[] internal = FlowType.INTERNAL.getTotal(flowTransfers, nation.getId());
        double[] withdrawal = FlowType.WITHDRAWAL.getTotal(flowTransfers, nation.getId());
        double[] deposit = FlowType.DEPOSIT.getTotal(flowTransfers, nation.getId());

        Map<DepositType, double[]> deposits = PW.sumNationTransactions(nation, db, tracked, transactions, includeExpired, includeIgnored, f -> true);
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
        long lastDeposit = 0;
        long lastSelfWithdrawal = 0;
        for (Map.Entry<Integer, Transaction2> entry : transactions) {
            Transaction2 transaction = entry.getValue();
            if (transaction.sender_id == nation.getNation_id()) {
                lastDeposit = Math.max(transaction.tx_datetime, lastDeposit);
            }
            if (transaction.isSelfWithdrawal(nation)) {
                lastSelfWithdrawal = Math.max(transaction.tx_datetime, lastSelfWithdrawal);
            }
        }
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
        row.add(ResourceType.toString(internal));
        row.add(ResourceType.toString(withdrawal));
        row.add(ResourceType.toString(deposit));

        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS) continue;
            row.add(MathMan.format(total[type.ordinal()]));
        }
        double[] normalized = PW.normalize(total);
        // --- End of logic from original loop body ---

        // Update progress (atomically)
        int count = processedCount.incrementAndGet();
        long now = System.currentTimeMillis();
        // Update message roughly every 5 seconds, avoiding contention on lastUpdateTimestamp
        if (now - lastUpdateTimestamp.get() > 5000) {
            if (lastUpdateTimestamp.compareAndSet(lastUpdateTimestamp.get(), now)) { // Only one thread updates
                channel.updateOptionally(msgFuture, String.format("Calculating... (%d/%d nations processed)", count, totalNations));
            }
        }


        return new NationBalanceRow(row, total, normalized);
    }
}