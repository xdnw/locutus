package link.locutus.discord.util;

import link.locutus.discord.config.Settings;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class TimeUtil {
    public static final SimpleDateFormat MMDD_HH_MM_A = new SimpleDateFormat("MM/dd h:mm a", Locale.ENGLISH);
    public static final SimpleDateFormat MMDDYYYY_HH_MM_A = new SimpleDateFormat("MM/dd/yyyy h:mm a", Locale.ENGLISH);
    public static final SimpleDateFormat YYYY_MM_DD_HH_MM_SS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
    public static final SimpleDateFormat YYYY_MM_DDTHH_MM_SSX = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssX", Locale.ENGLISH);
    public static final SimpleDateFormat YYYY_MM_DD_HH_MM_SS_A = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.ENGLISH);

    public static final SimpleDateFormat F_YYYY_MM_DD = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

    public static final SimpleDateFormat YYYY_MM_DD_HH_MM_A = new SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.ENGLISH);

    public static final SimpleDateFormat WAR_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.ENGLISH);
    public static final SimpleDateFormat DD_MM_YY = new SimpleDateFormat("dd/MM/yy", Locale.ENGLISH);
    public static final SimpleDateFormat DD_MM_YYYY = new SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH);
    public static final SimpleDateFormat YYYY_MM_DD_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

    public static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH);
    public static final SimpleDateFormat DD_MM_YYYY_HH = new SimpleDateFormat("dd/MM/yyyy HH", Locale.ENGLISH);


    static {

        MMDD_HH_MM_A.setTimeZone(TimeZone.getTimeZone("UTC"));
        YYYY_MM_DD_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        MMDDYYYY_HH_MM_A.setTimeZone(TimeZone.getTimeZone("UTC"));
        YYYY_MM_DD_HH_MM_SS.setTimeZone(TimeZone.getTimeZone("UTC"));
        YYYY_MM_DDTHH_MM_SSX.setTimeZone(TimeZone.getTimeZone("UTC"));
        YYYY_MM_DD_HH_MM_SS_A.setTimeZone(TimeZone.getTimeZone("UTC"));
        YYYY_MM_DD_HH_MM_A.setTimeZone(TimeZone.getTimeZone("UTC"));
        F_YYYY_MM_DD.setTimeZone(TimeZone.getTimeZone("UTC"));
        WAR_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        DD_MM_YY.setTimeZone(TimeZone.getTimeZone("UTC"));
        DD_MM_YYYY.setTimeZone(TimeZone.getTimeZone("UTC"));
        DD_MM_YYYY_HH.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public static String turnsToTime(long turns) {
        return secToTime(TimeUnit.HOURS, turns * 2);
    }

    public static String minutesToTime(long turns) {
        return secToTime(TimeUnit.MINUTES, turns);
    }

    public static String secToTime(TimeUnit unit, long time) {
        time = unit.toSeconds(time);
        StringBuilder toreturn = new StringBuilder();
        if (time >= 33868800) {
            int years = (int) (time / 33868800);
            int time1 = years * 33868800;
            time -= time1;
            toreturn.append(years + "y");
        }
        if (time >= 604800) {
            int weeks = (int) (time / 604800);
            time -= weeks * 604800;
            toreturn.append(weeks + "w");
        }
        if (time >= 86400) {
            int days = (int) (time / 86400);
            time -= days * 86400;
            toreturn.append(days + "d");
        }
        if (time >= 3600) {
            int hours = (int) (time / 3600);
            time -= hours * 3600;
            toreturn.append(hours + "h");
        }
        if (time >= 60) {
            int minutes = (int) (time / 60);
            time -= minutes * 60;
            toreturn.append(minutes + "m");
        }
        if (toreturn.equals("") || time > 0) {
            toreturn.append((time) + "s");
        }
        return toreturn.toString().trim();
    }

    public static boolean checkTurnChange() {
        return checkTurnChange(System.currentTimeMillis());
    }

    public static boolean checkTurnChange(long now) {
        long minute = TimeUnit.MINUTES.toMillis(1);
        long lastTurn = TimeUtil.getTurn(now - minute);
        long nextTurn = TimeUtil.getTurn(now + minute);
        if (lastTurn != nextTurn) return false;
        double amt = (nextTurn % 12 == 0 && !Settings.INSTANCE.TEST) ? 10.2d : 1.2d;
        long turnChangeTimer = TimeUtil.getTurn((long) (now - minute * amt));
        return turnChangeTimer == nextTurn;
    }

    public static long timeToSec(String string) {
        return timeToSec(string, System.currentTimeMillis(), false);
    }

    // Fix for incorrect paring. Only applies fix after specific date to avoid retroactively altering
    public static long timeToSec_BugFix1(String string, long currentTime) {
        if (currentTime > 1652245190000L) {
            return timeToSec(string, currentTime, true);
        } else {
            return timeToSec(string, System.currentTimeMillis(), false);
        }
    }

    public static long timeToSec(String string, long currentTime, boolean forwards) {
        if (string.length() == 0) return 0;
        if (string.equals("60d")) return TimeUnit.DAYS.toSeconds(60);

        if (string.length() > 10 && ((string.charAt(0) == 't' && string.startsWith("timestamp:")) || (MathMan.isInteger(string) && string.length() > 12))) {
            long timestamp = Long.parseLong(string.split(":")[1]);
            if (forwards) {
                return (timestamp - currentTime) / 1000L;
            } else {
                return (currentTime - timestamp) / 1000L;
            }
        }
        if (MathMan.isInteger(string)) {
            return Long.parseLong(string);
        }
        string = string.toLowerCase().trim().toLowerCase();
        if (string.charAt(0) == 'f'  && string.equalsIgnoreCase("false")) {
            return 0;
        }
        string = string.replaceAll("([a-zA-Z])([0-9])", "$1 $2");
        String[] split = string.indexOf(' ') != -1 ? string.split(" ") : new String[] {string};
        long time = 0;
        for (String value : split) {
            double nums = Double.parseDouble(value.replaceAll("[^\\d.]", ""));
            String letters = value.replaceAll("[^a-z]", "");
            switch (letters) {
                case "month(s)":
                case "months":
                    time += TimeUnit.DAYS.toSeconds(30) * nums;
                    break;
                case "week":
                case "weeks":
                case "wks":
                case "week(s)":
                case "w":
                    time += 604800 * nums;
                    break;
                case "day(s)":
                case "days":
                case "day":
                case "d":
                    time += 86400 * nums;
                    break;
                case "hour(s)":
                case "hour":
                case "hr":
                case "hrs":
                case "hours":
                case "h":
                    time += 3600 * nums;
                    break;
                case "minute(s)":
                case "minutes":
                case "minute":
                case "mins":
                case "min":
                case "m":
                    time += 60 * nums;
                    break;
                case "second(s)":
                case "seconds":
                case "second":
                case "secs":
                case "sec":
                case "s":
                    time += nums;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown unit: " + letters);
            }
        }
        if (time < 1000 && MathMan.isInteger(string)) {
            time *= TimeUnit.DAYS.toSeconds(1);
        }
        return time;
    }

    public static void deleteOldTimeLocks() {
        File folder = new File((Settings.INSTANCE.TEST ? "test" + File.separator : "") + "database" + File.separator + "timelocks");
        if (!folder.exists()) return;
        long turn = TimeUtil.getTurn();
        // delete any previous turns
        // iterate files
        for (File file : folder.listFiles()) {
            String[] split = file.getName().split("\\.");
            if (MathMan.isInteger(split[0]) && Long.parseLong(split[0]) < turn) {
                file.delete();
            }
        }

    }

    public static long getTurn() {
        if (Settings.INSTANCE.TEST) {
            long value = ChronoUnit.HOURS.between(Instant.EPOCH, Instant.now());
            return value;
        }
        long now = System.currentTimeMillis();
        long daysSince0 = TimeUnit.MILLISECONDS.toDays(now);
        long hoursInCurrentDay = TimeUnit.MILLISECONDS.toHours(now % 86400000);
        int turnsPerDay = 12;
        return (hoursInCurrentDay / 2) + daysSince0 * turnsPerDay;
    }

    public static long getTurn(Long timestamp) {
        if (timestamp == null) return getTurn();
        Instant instant = Instant.ofEpochMilli(timestamp);
        return getTurn(instant.atZone(ZoneOffset.UTC));
    }

    public static long getTimeFromDay(long day) {
        if (Settings.INSTANCE.TEST) day *= 2;

        ZonedDateTime time = Instant.ofEpochMilli(0).atZone(ZoneOffset.UTC).plusDays(day);
        long millisecond = time.toEpochSecond() * 1000L;
        return millisecond;
    }

    public static long getTimeFromTurn(long turn) {
        if (Settings.INSTANCE.TEST) {
            return TimeUnit.HOURS.toMillis(turn);
        }
        long day = (turn / 12);
        long hour = turn * 2 - (day * 24);

        ZonedDateTime time = Instant.ofEpochMilli(0).atZone(ZoneOffset.UTC).plusDays(day).plusHours(hour);
        long millisecond = time.toEpochSecond() * 1000L;
        return millisecond;
    }

    public static long getTurn(ZonedDateTime utc) {
        if (Settings.INSTANCE.TEST) {
            long value = ChronoUnit.HOURS.between(Instant.EPOCH, utc);
            return value;
        }
        return (utc.getHour() / 2) + getDay(utc) * 12L;
    }

    public static long getDayTurn() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        if (Settings.INSTANCE.TEST) {
            int hours = now.getHour();
            return hours;
        }
        return now.getHour() / 2;
    }

    public static long getDay() {
        ZonedDateTime utc = ZonedDateTime.now(ZoneOffset.UTC);
        return getDay(utc);
    }

    public static long getDay(ZonedDateTime utc) {
        if (Settings.INSTANCE.TEST) {
            long value = ChronoUnit.HOURS.between(Instant.EPOCH, utc);
            return value;
        }
        return ChronoUnit.DAYS.between(Instant.EPOCH, utc);
    }

    public static long getDay(long timestamp) {
        if (Settings.INSTANCE.TEST) {
            long value = ChronoUnit.HOURS.between(Instant.EPOCH, Instant.ofEpochMilli(timestamp));
            return value / 12;
        }
        return ChronoUnit.DAYS.between(Instant.EPOCH, Instant.ofEpochMilli(timestamp));
    }

    public static long getDayFromTurn(long turn) {
        return turn / 12;
    }

    public static long getOrbisDate(long date) {
        long origin = getOrigin();
        return origin + (date - origin) * 12;
    }

    public static long getRealDate(long orbisDate) {
        long origin = getOrigin();
        return ((orbisDate - origin) / 12) + origin;
    }

    public static long getOrigin() {
        return 16482268800000L / 11L;
    }

    public static long parseDate(DateFormat format, String dateStr) {
        return parseDate(format, dateStr, false);
    }

    public static String format(DateFormat format, Date date) {
        synchronized (format) {
            return format.format(date);
        }
    }

    public static long parseDate(DateFormat format, String dateStr, boolean setYear) {
        try {
            synchronized (format) {
                Date date = format.parse(dateStr);
                if (setYear) {
//                    int CAL_YEAR = Calendar.YEAR;
                    date.setYear(LocalDateTime.now(ZoneOffset.UTC).getYear() - 1900);
                }
                return date.getTime();
            }
        } catch (ParseException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
