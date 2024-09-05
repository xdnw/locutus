package link.locutus.discord.commands.manager.v2.table;

import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.TimeUtil;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public enum TimeFormat {
    NUMERIC {
        @Override
        public String toString(Number number) {
            return number.toString();
        }
    },
    DECIMAL_ROUNDED {
        @Override
        public String toString(Number number) {
            return MathMan.format(number);
        }
    },
    SI_UNIT {
        @Override
        public String toString(Number number) {
            return MathMan.formatSig(number.doubleValue());
        }
    },
    TURN_TO_DATE {
        @Override
        public String toString(Number number) {
            long time = TimeUtil.getTimeFromTurn(number.longValue());
            if (time < TimeUtil.getOrigin()) {
                return TimeUtil.secToTime(TimeUnit.MILLISECONDS, time);
            }
            return TimeUtil.DD_MM_YYYY_HH.format(new Date(time));

        }
    },
    DAYS_TO_DATE {
        @Override
        public String toString(Number number) {
            long time = TimeUtil.getTimeFromDay(number.longValue());
            if (time < TimeUtil.getOrigin()) {
                return TimeUtil.secToTime(TimeUnit.MILLISECONDS, time);
            }
            return TimeUtil.DD_MM_YYYY_HH.format(new Date(time));
        }
    },
    MILLIS_TO_DATE {
        @Override
        public String toString(Number number) {
            long time = number.longValue();
            if (time < TimeUtil.getOrigin()) {
                return TimeUtil.secToTime(TimeUnit.MILLISECONDS, time);
            }
            return TimeUtil.DD_MM_YYYY_HH.format(new Date(time));
        }
    },
    ;

    public abstract String toString(Number number);
}
