package link.locutus.discord.commands.manager.v2.table;

import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.TimeUtil;

import java.util.concurrent.TimeUnit;

public enum TimeFormat {
    NUMERIC(false) {
        @Override
        public String toString(Number number) {
            return number.toString();
        }
    },
    DECIMAL_ROUNDED(false) {
        @Override
        public String toString(Number number) {
            return MathMan.format(number);
        }
    },
    SI_UNIT(false) {
        @Override
        public String toString(Number number) {
            return MathMan.formatSig(number.doubleValue());
        }
    },
    TURN_TO_DATE(false) {
        @Override
        public String toString(Number number) {
            long time = TimeUtil.getTimeFromTurn(number.longValue());
            if (time < TimeUtil.getOrigin()) {
                return TimeUtil.secToTime(TimeUnit.MILLISECONDS, time);
            }
            return TimeUtil.format(TimeUtil.DD_MM_YYYY_HH, time);

        }
    },
    DAYS_TO_DATE(false) {
        @Override
        public String toString(Number number) {
            long time = TimeUtil.getTimeFromDay(number.longValue());
            if (time < TimeUtil.getOrigin()) {
                return TimeUtil.secToTime(TimeUnit.MILLISECONDS, time);
            }
            return TimeUtil.format(TimeUtil.DD_MM_YYYY_HH, time);
        }
    },
    MILLIS_TO_DATE(false) {
        @Override
        public String toString(Number number) {
            long time = number.longValue();
            if (time < TimeUtil.getOrigin()) {
                return TimeUtil.secToTime(TimeUnit.MILLISECONDS, time);
            }
            return TimeUtil.format(TimeUtil.DD_MM_YYYY_HH, time);
        }
    },
    SECONDS_TO_DATE(false) {
        @Override
        public String toString(Number number) {
            long time = number.longValue();
            if (time < TimeUtil.getOrigin()) {
                return TimeUtil.secToTime(TimeUnit.SECONDS, time);
            }
            return TimeUtil.format(TimeUtil.DD_MM_YYYY_HH, time);
        }
    },
    ;

    private final boolean isTime;

    TimeFormat(boolean isTime) {
        this.isTime = isTime;
    }

    public boolean isTime() {
        return isTime;
    }

    public abstract String toString(Number number);
}
