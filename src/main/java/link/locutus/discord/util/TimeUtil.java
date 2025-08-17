package link.locutus.discord.util;

import link.locutus.discord.config.Settings;

import java.io.File;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TimeUtil {
    public static final DateTimeFormatter MMDD_HH_MM_A      = withOptionalTime("MM/dd h:mm a");
    public static final DateTimeFormatter MMDD_HH_MM_SS_A      = withOptionalTime("MM/dd h:mm:ss a");
    public static final DateTimeFormatter MMDDYYYY_HH_MM_A  = withOptionalTime("MM/dd/yyyy h:mm a");
    public static final DateTimeFormatter YYYY_MM_DD_HH_MM_SS = withOptionalTime("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter YYYY_MM_DDTHH_MM_SSX = withOptionalTime("yyyy-MM-dd HH:mm:ssX");
    public static final DateTimeFormatter YYYY_MM_DD_HH_MM_SS_A = withOptionalTime("yyyy-MM-dd hh:mm:ss a");

    public static final DateTimeFormatter F_YYYY_MM_DD      = withOptionalTime("yyyy-MM-dd");
    public static final DateTimeFormatter YYYY_MM_DD_HH_MM_A = withOptionalTime("yyyy-MM-dd hh:mm a");

    public static final DateTimeFormatter WAR_FORMAT        = withOptionalTime("yyyy-MM-dd'T'HH:mm:ssX");
    public static final DateTimeFormatter DD_MM_YY          = withOptionalTime("dd/MM/yy");
    public static final DateTimeFormatter DD_MM_YYYY        = withOptionalTime("dd/MM/yyyy");
    public static final DateTimeFormatter YYYY_MM_DD_FORMAT = withOptionalTime("yyyy-MM-dd");

    public static final DateTimeFormatter YYYY_MM_DD        = withOptionalTime("yyyy-MM-dd");
    public static final DateTimeFormatter DD_MM_YYYY_HH     = withOptionalTime("dd/MM/yyyy HH");

//    static class Wrapper {
//        public Instant date;
//    }
//
//    public static void main(String[] args) throws JsonProcessingException {
//        ObjectMapper jacksonObjectMapper = Jackson2ObjectMapperBuilder.json().simpleDateFormat("yyyy-MM-dd")
//                .featuresToEnable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
//                .build();
//        jacksonObjectMapper.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS,true);
//        jacksonObjectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
//        SimpleModule module = new SimpleModule();
//        // Fix for snapshots returning Object instead of Array of CityInfraDamage
////        module.addDeserializer(CityInfraDamage.class, (JsonDeserializer<CityInfraDamage>) (Object) new CityInfraDamageDeserializer());
//        jacksonObjectMapper.registerModule(module);
//
//        // test deserializing something containing a date: `2114-10-25T00:00:00+00:00`
//        {
//            String json = "\"2114-10-25T00:00:00+00:00\"";
//            Instant actual = jacksonObjectMapper.readValue(json, Instant.class);
//            Instant instant = Instant.parse("2114-10-25T00:00:00Z");
//            System.out.println("Deserialized Instant: " + actual);
//            System.out.println("Expected Instant: " + instant);
//            System.out.println("Are they equal? " + actual.equals(instant));
//        }
//        {
//            String json = "{\"date\":\"2114-10-25T00:00:00+00:00\"}";
//            Wrapper wrapper = jacksonObjectMapper.readValue(json, Wrapper.class);
//            Instant actual = wrapper.date;
//            Instant instant = Instant.parse("2114-10-25T00:00:00Z");
//            System.out.println("Deserialized Wrapper Instant: " + actual);
//            System.out.println("Expected Instant: " + instant);
//            System.out.println("Are they equal? " + actual.equals(instant));
//        }
//    }

    private static DateTimeFormatter withOptionalTime(String pattern) {
        DateTimeFormatterBuilder b = new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .parseLenient()
                .appendPattern(pattern)
                .parseDefaulting(ChronoField.MINUTE_OF_HOUR,     0)
                .parseDefaulting(ChronoField.SECOND_OF_MINUTE,   0);

        if (pattern.contains("H")) {
            b.parseDefaulting(ChronoField.HOUR_OF_DAY, 0);
        } else if (pattern.contains("h") && pattern.contains("a")) {
            // use CLOCK_HOUR_OF_AMPM for 'hh' patterns
            b.parseDefaulting(ChronoField.AMPM_OF_DAY,       0)
                    .parseDefaulting(ChronoField.CLOCK_HOUR_OF_AMPM, 12);
        } else {
            b.parseDefaulting(ChronoField.HOUR_OF_DAY, 0);
        }
        return b.toFormatter(Locale.ENGLISH)
                .withResolverStyle(ResolverStyle.LENIENT);
    }

    public static long parseDate(DateTimeFormatter formatter, String dateStr) {
        if (dateStr.startsWith("0000-00-00")) {
            return 0L;  // or 0L for epoch
        }
        LocalDateTime ldt = LocalDateTime.parse(dateStr.toUpperCase(Locale.ROOT), formatter);
        return ldt.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    public static long parseDateSetYear(DateTimeFormatter formatter, String dateStr) {
        if (dateStr.startsWith("0000-00-00")) {
            return 0L;  // or 0L for epoch
        }
        LocalDateTime ldt = LocalDateTime.parse(dateStr.toUpperCase(Locale.ROOT), formatter);
        int currentYear = LocalDateTime.now(ZoneOffset.UTC).getYear();
        ldt = ldt.withYear(currentYear);
        return ldt.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private static long calcTurn() {
        if (Settings.INSTANCE.TEST) {
            long value = ChronoUnit.HOURS.between(Instant.EPOCH, Instant.now());
            return CURRENT_TURN = value;
        }
        long now = System.currentTimeMillis();
        long daysSince0 = TimeUnit.MILLISECONDS.toDays(now);
        long hoursInCurrentDay = TimeUnit.MILLISECONDS.toHours(now % 86400000);
        int turnsPerDay = 12;
        return CURRENT_TURN = (hoursInCurrentDay / 2) + daysSince0 * turnsPerDay;
    }

    private static volatile long CURRENT_TURN = calcTurn();
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();
    private static volatile boolean nearTurnChange = false;

    static {
        // schedule precise turn updates
        scheduleNextTurn();
        // maintain a flag when within 5 seconds of a turn change
        scheduleNearChange();
    }

    private static void scheduleNextTurn() {
        long current = calcTurn();
        long nextTime = getTimeFromTurn(current + 1);
        long now = System.currentTimeMillis();
        long delayToTurn = nextTime - now;
        long delayToNear = Math.max(0, delayToTurn - 5);

        // 5 ms before turn change
        SCHEDULER.schedule(() -> nearTurnChange = true,
                delayToNear, TimeUnit.MILLISECONDS);

        // at turn change
        SCHEDULER.schedule(() -> {
            CURRENT_TURN = calcTurn();
            nearTurnChange = false;
            scheduleNextTurn();
        }, delayToTurn, TimeUnit.MILLISECONDS);
    }

    private static void scheduleNearChange() {
        SCHEDULER.scheduleAtFixedRate(() -> {
            long nextTurnTime = getTimeFromTurn(CURRENT_TURN + 1);
            nearTurnChange = nextTurnTime - System.currentTimeMillis() <= TimeUnit.SECONDS.toMillis(5);
        }, 0, 1, TimeUnit.SECONDS);
    }

    public static long getTurn() {
        return nearTurnChange ? calcTurn() : CURRENT_TURN;
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
                case "year":
                case "years":
                case "yr":
                case "yrs":
                case "year(s)":
                case "y":
                    time += TimeUnit.DAYS.toSeconds(365) * nums;
                    break;
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

    public static long getTurn(Long timestamp) {
        if (timestamp == null) return getTurn();
        long hours = timestamp / 3_600_000L;
        if (Settings.INSTANCE.TEST) {
            return hours;
        }
        return (hours / 2);
    }

    public static long getTimeFromDay(long day) {
        if (Settings.INSTANCE.TEST) day *= 2;

        long millisecond = day * 86_400_000L; // 24 * 60 * 60 * 1000
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

    public static long getDayTurn() {
        long now = System.currentTimeMillis();
        long hours = now / 3_600_000L;
        if (Settings.INSTANCE.TEST) {
            return hours % 12;
        }
        return (hours / 2) % 12;
    }

    public static long getDay() {
        long daysSinceEpoch = System.currentTimeMillis() / 86_400_000L;
        return daysSinceEpoch;
    }

    public static long getDay(long timestamp) {
        if (Settings.INSTANCE.TEST) {
            long hours = timestamp / 3_600_000L; // hours since epoch
            return hours / 12;
        }
        return timestamp / 86_400_000L; // days since epoch
    }

    public static long getDayFromTurn(long turn) {
        return turn / 12;
    }

    public static long getOrbisDate(long date) {
        long origin = getOrigin();
        return origin + (date - origin) * 12;
    }

    public static Month getMonth(long timestamp) {
        ZonedDateTime dateTime = Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC);
        return Month.of(dateTime.getMonthValue());
    }

    public static long getRealDate(long orbisDate) {
        long origin = getOrigin();
        return ((orbisDate - origin) / 12) + origin;
    }

    public static long getOrigin() {
        return 16482268800000L / 11L;
    }

    public static String format(DateTimeFormatter formatter, long timestamp) {
        ZonedDateTime utcDateTime = Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC);
        return formatter.format(utcDateTime);
    }

//    public static String format(DateFormat format, Date date) {
//        synchronized (format) {
//            return format.format(date);
//        }
//    }
}
