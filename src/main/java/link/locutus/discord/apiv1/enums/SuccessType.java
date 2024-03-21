package link.locutus.discord.apiv1.enums;

import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;

public enum SuccessType {
    UTTER_FAILURE,
    PYRRHIC_VICTORY,
    MODERATE_SUCCESS,
    IMMENSE_TRIUMPH

    ;

    public static SuccessType[] values = values();

    public static SuccessType parse(String s) {
        if (MathMan.isInteger(s)) {
            int i = Integer.parseInt(s);
            if (i >= 0 && i < values.length) {
                return values[i];
            }
            throw new IllegalArgumentException("Invalid success type: " + s + ". Options: " + StringMan.getString(values));
        }
        return valueOf(s);
    }
}
