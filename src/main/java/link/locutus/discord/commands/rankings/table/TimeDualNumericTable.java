package link.locutus.discord.commands.rankings.table;

import de.erichseifert.gral.data.DataTable;

public abstract class TimeDualNumericTable<T> extends TimeNumericTable<T> {
    /**
     *
     * @param title
     * @param labelX Axiss label
     * @param labelY Axis label
     * @param labelA Series label
     * @param labelB Series label
     */
    public TimeDualNumericTable(String title, String labelX, String labelY, String labelA, String labelB) {
        super(title, labelX, labelY, labelA, labelB);
    }

    public void add(long day, double a, double b) {
        data.add(day, a, b);
    }

    public DataTable getData() {
        return data;
    }
}
