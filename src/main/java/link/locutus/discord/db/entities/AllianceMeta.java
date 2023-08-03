package link.locutus.discord.db.entities;

import link.locutus.discord.util.MathMan;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

public enum AllianceMeta {
    LAST_BLITZ_PCT,
    IS_WARRING,
    LAST_AT_WAR_TURN,

    OFFSHORE_PARENT,

    BANK_UPDATE_INDEX,

    GROUND_MILITARIZATION,
    GROUND_MILITARIZATION_DATE,


    OFFSHORE_PARENT_DATE,
    OFFSHORE_PARENT_ITERATION,

    ;

    public static AllianceMeta[] values = values();

    private final Function<ByteBuffer, Object> parse;

    AllianceMeta(Function<ByteBuffer, Object> parse) {
        this.parse = parse;
    }

    AllianceMeta() {
        this(null);
    }

    public String toString(ByteBuffer buf) {
        if (buf == null) return "";
        if (parse != null) return "" + parse.apply(buf);

        byte[] arr = new byte[buf.remaining()];
        buf.get(arr);
        buf = ByteBuffer.wrap(arr);
        switch (arr.length) {
            case 0:
                return "" + (buf.get() & 0xFF);
            case 4:
                return "" + (buf.getInt());
            case 8:
                ByteBuffer buf2 = ByteBuffer.wrap(arr);
                return buf.getLong() + "/" + MathMan.format(buf2.getDouble());
            default:
                return new String(arr, StandardCharsets.ISO_8859_1);
        }
    }
}
