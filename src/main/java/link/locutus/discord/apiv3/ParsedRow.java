package link.locutus.discord.apiv3;

import de.siegmar.fastcsv.reader.CsvRow;

import java.util.Arrays;
import java.util.function.Function;

public class ParsedRow {
    public CsvRow row;
    public Object[] data = new Object[0];

    public void setRow(CsvRow row) {
        this.row = row;
        if (data.length != row.getFieldCount()) {
            data = new Object[row.getFieldCount()];
        } else {
            Arrays.fill(data, null);
        }
    }

    public <T> T get(int index, Function<String, T> parse) {
        Object o = data[index];
        if (o == null) {
            String str = row.getField(index);
            if (str.isEmpty()) return null;
            o = parse.apply(str);
            data[index] = o;
        }
        return (T) o;
    }
}
