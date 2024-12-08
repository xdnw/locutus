package link.locutus.discord.commands.manager.v2.table.imp;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.db.entities.TaxRecordCategorizer2;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

import java.util.Map;
import java.util.function.Function;

public class TaxCategoryGraph extends SimpleTable<Void> {
    private final double[] buffer;
    private final String[] labels;
    private final Function<double[], Double> getValue;
    private final TaxRecordCategorizer2.TransactionType[] txTypes;
    private final Map<TaxRecordCategorizer2.TransactionType, double[][]> transfers;

    public TaxCategoryGraph(String title, Map<TaxRecordCategorizer2.TransactionType, double[][]> transfers, ResourceType valueType) {
        if (transfers.isEmpty()) throw new IllegalArgumentException("No transfers found");
        this.transfers = transfers;

        this.labels = new String[transfers.size() + 2];
        labels[0] = "Expense Total";
        labels[1] = "Income Total";

        String labelY = valueType == null ? "Market Value ($)" : valueType.name();
        this.getValue = input -> valueType != null ? input[valueType.ordinal()] : ResourceType.convertedTotal(input);

        this.buffer = new double[labels.length];

        this.txTypes = transfers.keySet().toArray(new TaxRecordCategorizer2.TransactionType[0]);
        for (int i = 0; i < txTypes.length; i++) {
            TaxRecordCategorizer2.TransactionType txType = txTypes[i];
            labels[2 + i] = txType.name();
        }

        setTitle(title);
        setLabelX("group by");
        setLabelY(labelY);
        setLabels(labels);

        writeData();
    }

    @Override
    public long getOrigin() {
        return 0;
    }

    @Override
    public TableNumberFormat getNumberFormat() {
        return TableNumberFormat.SI_UNIT;
    }

    @Override
    public TimeFormat getTimeFormat() {
        return TimeFormat.MILLIS_TO_DATE;
    }

    @Override
    public GraphType getGraphType() {
        return GraphType.LINE;
    }

    @Override
    public void add(long key, Void ignore) {
        buffer[0] = 0;
        buffer[1] = 0;
        for (int i = 0; i < txTypes.length; i++) {
            TaxRecordCategorizer2.TransactionType txType = txTypes[i];
            double[] typeIncome = transfers.get(txType)[(int) key];
            double value = getValue.apply(typeIncome);

            buffer[i + 2] = value;
            if (txType.isExpense) buffer[0] += value;
            else buffer[1] += value;
        }
        add(key, buffer);
    }

    @Override
    protected SimpleTable<Void> writeData() {
        double[][] arr0 = transfers.values().iterator().next();
        for (int i = 0; i < arr0.length; i++) {
            add(i, (Void) null);
        }
        return this;
    }
}
