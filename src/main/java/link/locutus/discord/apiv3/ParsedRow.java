package link.locutus.discord.apiv3;

import de.siegmar.fastcsv.reader.CsvRow;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.TimeUtil;

import java.text.ParseException;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Predicate;

public class ParsedRow {
    private final DataDumpParser parser;
    public CsvRow row;
    public Object[] data = new Object[0];
    private final Predicate<Integer> allowAll = b -> true;
    private int nationLoaded;
    private DBNation nation;
    private DBCity city;

    private static final int LOADED = 1;
    private static final int ALLOW_VM = 2;
    private static final int ALLOW_DELETED = 4;
    private long currentTimeMs;
    private long day;

    public ParsedRow(DataDumpParser parser) {
        this.parser = parser;
    }

    public void setRow(CsvRow row, long day) {
        this.row = row;
        if (data.length != row.getFieldCount()) {
            data = new Object[row.getFieldCount()];
        } else {
            Arrays.fill(data, null);
        }
        this.day = day;
        this.currentTimeMs = TimeUtil.getTimeFromDay(day);
        nation = null;
        city = null;
        nationLoaded = 0;
    }

    public <T extends Number> Number getNumber(int index, Function<String, T> parse) {
        return ((Number) get(index, parse));
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

    public DBNation getNation(DataDumpParser.NationHeader header, boolean allowVM, boolean allowDeleted) {
        if (nation != null) {
            if (!allowVM && (nationLoaded & ALLOW_VM) != 0 && nation.getVm_turns() > 0) return null;
            if (!allowDeleted && (nationLoaded & ALLOW_DELETED) != 0 && !nation.isValid()) return null;
            return nation;
        }
        if ((nationLoaded & LOADED) != 0 && (!allowVM || (nationLoaded & ALLOW_VM) == 0) && (!allowDeleted || (nationLoaded & ALLOW_DELETED) == 0)) return null;
        nationLoaded |= LOADED | (allowVM ? ALLOW_VM : 0) | (allowDeleted ? ALLOW_DELETED : 0);
        try {
            nation = parser.loadNation(header, row, allowAll, allowAll, allowVM, allowDeleted, currentTimeMs);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        return nation;
    }

    public DBCity getCity(DataDumpParser.CityHeader header, Integer nationId) {
        if (city != null) return city;
        try {
            city = parser.loadCity(header, row, nationId);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        return city;
    }
}
