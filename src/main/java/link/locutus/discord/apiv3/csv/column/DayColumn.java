package link.locutus.discord.apiv3.csv.column;

import link.locutus.discord.apiv3.csv.header.DataHeader;
import link.locutus.discord.util.TimeUtil;

import java.io.DataOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.function.BiConsumer;

public class DayColumn<P> extends NumberColumn<P, Long> {
    public DayColumn(DataHeader<P> header, BiConsumer<P, Long> setter) {
        super(header, setter);
    }

    @Override
    public Long read(byte[] buffer, int offset) throws IOException {
        int day = (((buffer[offset] & 255) << 8) | ((buffer[offset + 1] & 255)));
        return day == 0 ? 0 : TimeUtil.getTimeFromDay(day + TimeUtil.getDay(TimeUtil.getOrigin()));
    }

    @Override
    public Long getDefault() {
        return 0L;
    }

    @Override
    public int getBytes() {
        return 2;
    }

    @Override
    public void write(DataOutputStream dos, Long value) throws IOException {
        char day = value == 0 ? 0 : (char) (TimeUtil.getDay(value) - TimeUtil.getDay(TimeUtil.getOrigin()));
        dos.writeChar(day);
    }

    @Override
    public Long read(String string) {
        return TimeUtil.parseDate(TimeUtil.YYYY_MM_DD_FORMAT, string);
    }
}
