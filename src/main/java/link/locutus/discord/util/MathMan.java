package link.locutus.discord.util;

import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.util.scheduler.KeyValue;
import org.apache.commons.lang3.math.NumberUtils;

import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
public class MathMan {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,###.00");
    private static final DecimalFormat INT_FORMAT = new DecimalFormat("#,###");

    public static <T extends Number> T orElse(T value, T orElse) {
        return value == null ? orElse : value;
    }

    private static final NavigableMap<Long, String> suffixes = new TreeMap<>();
    static {
        suffixes.put(1_000L, "k");
        suffixes.put(1_000_000L, "M");
        suffixes.put(1_000_000_000L, "B");
        suffixes.put(1_000_000_000_000L, "T");
        suffixes.put(1_000_000_000_000_000L, "P");
        suffixes.put(1_000_000_000_000_000_000L, "E");
    }

    public static Format toFormat(Function<Number, String> formatFunc) {
        return new Format() {
            @Override
            public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
                return toAppendTo.append(formatFunc.apply((Number) obj));
            }

            @Override
            public Object parseObject(String source, ParsePosition pos) {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Converts a number to a string in <a href="http://en.wikipedia.org/wiki/Metric_prefix">metric prefix</a> format.
     * For example, 7800000 will be formatted as '7.8M'. Numbers under 1000 will be unchanged. Refer to the tests for further examples.
     */
    public static class RoundedMetricPrefixFormat extends Format {

        private static final String[] METRIC_PREFIXES = new String[]{"", "k", "M", "G", "T"};

        /**
         * The maximum number of characters in the output, excluding the negative sign
         */
        private static final Integer MAX_LENGTH = 12;

        private static final Pattern TRAILING_DECIMAL_POINT = Pattern.compile("[0-9]+\\.[kMBT]");

        private static final Pattern METRIC_PREFIXED_NUMBER = Pattern.compile("\\-?[0-9]+(\\.[0-9])?[kMBT]");

        @Override
        public StringBuffer format(Object obj, StringBuffer output, FieldPosition pos) {

            Double number = Double.valueOf(obj.toString());

            // if the number is negative, convert it to a positive number and add the minus sign to the output at the end
            boolean isNegative = number < 0;
            number = Math.abs(number);

            String result = new DecimalFormat("##0E0").format(number);

            Integer index = Character.getNumericValue(result.charAt(result.length() - 1)) / 3;
            result = result.replaceAll("E[0-9]", METRIC_PREFIXES[index]);

            while (result.length() > MAX_LENGTH || TRAILING_DECIMAL_POINT.matcher(result).matches()) {
                int length = result.length();
                result = result.substring(0, length - 2) + result.substring(length - 1);
            }

            return output.append(isNegative ? "-" + result : result);
        }

        /**
         * Convert a String produced by <tt>format()</tt> back to a number. This will generally not restore
         * the original number because <tt>format()</tt> is a lossy operation, e.g.
         *
         * <pre>
         * {@code
         * def formatter = new RoundedMetricPrefixFormat()
         * Long number = 5821L
         * String formattedNumber = formatter.format(number)
         * assert formattedNumber == '5.8k'
         *
         * Long parsedNumber = formatter.parseObject(formattedNumber)
         * assert parsedNumber == 5800
         * assert parsedNumber != number
         * }
         * </pre>
         *
         * @param source a number that may have a metric prefix
         * @param pos if parsing succeeds, this should be updated to the index after the last parsed character
         * @return a Number if the the string is a number without a metric prefix, or a Long if it has a metric prefix
         */
        @Override
        public Object parseObject(String source, ParsePosition pos) {

            if (NumberUtils.isNumber(source)) {

                // if the value is a number (without a prefix) don't return it as a Long or we'll lose any decimals
                pos.setIndex(source.length());
                return toNumber(source);

            } else if (METRIC_PREFIXED_NUMBER.matcher(source).matches()) {

                boolean isNegative = source.charAt(0) == '-';
                int length = source.length();

                String number = isNegative ? source.substring(1, length - 1) : source.substring(0, length - 1);
                String metricPrefix = Character.toString(source.charAt(length - 1));

                Number absoluteNumber = toNumber(number);

                int index = 0;

                for (; index < METRIC_PREFIXES.length; index++) {
                    if (METRIC_PREFIXES[index].equals(metricPrefix)) {
                        break;
                    }
                }

                Integer exponent = 3 * index;
                Double factor = Math.pow(10, exponent);
                factor *= isNegative ? -1 : 1;

                pos.setIndex(source.length());
                Float result = absoluteNumber.floatValue() * factor.longValue();
                return result.longValue();
            }

            return null;
        }

        private static Number toNumber(String number) {
            return NumberUtils.createNumber(number);
        }
    }

    public static String formatSig(double value) {
        return formatSig((long) value);
    }

    public static String formatSig(long value) {
        if (value == Long.MIN_VALUE) return format(Long.MIN_VALUE + 1);
        if (value < 0) return "-" + format(-value);
        if (value < 1000) return Long.toString(value); //deal with easy case

        Map.Entry<Long, String> e = suffixes.floorEntry(value);
        Long divideBy = e.getKey();
        String suffix = e.getValue();

        long truncated = value / (divideBy / 10); //the number part of the output times 10
        boolean hasDecimal = truncated < 100 && (truncated / 10d) != (truncated / 10);
        return hasDecimal ? (truncated / 10d) + suffix : (truncated / 10) + suffix;
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) * 0.01;
    }

    public static BigInteger binomial(final int N, final int K) {
        BigInteger ret = BigInteger.ONE;
        for (int k = 0; k < K; k++) {
            ret = ret.multiply(BigInteger.valueOf(N-k))
                    .divide(BigInteger.valueOf(k+1));
        }
        return ret;
    }

    public static String format(Number value) {
        DecimalFormat format;
        if (value instanceof Integer || value instanceof Long || value.intValue() == value.doubleValue()) {
            format = INT_FORMAT;
        } else {
            format = DECIMAL_FORMAT;
        }
        return format.format(value);
    }

    private static String[] splitArgs = {">=", "<=", ">", "<", "!=", "="};
    private static BiFunction<Double, Double, Boolean>[] splitComparators = new BiFunction[splitArgs.length];
    static {
        splitComparators[0] = (a, b) -> a >= b;
        splitComparators[1] = (a, b) -> a <= b;
        splitComparators[2] = (a, b) -> a > b;
        splitComparators[3] = (a, b) -> a < b;
        splitComparators[4] = (a, b) -> !a.equals(b);
        splitComparators[5] = Double::equals;
    }

    private static String[] splitStrArgs = {"!=", "="};
    private static BiFunction<String, String, Boolean>[] splitStrComparators = new BiFunction[splitArgs.length];
    static {
        splitStrComparators[0] = (a, b) -> {
            try {
                return !a.equalsIgnoreCase(b) && !(a.matches(b));
            } catch (PatternSyntaxException ignore) {
                return true;
            }
        };
        splitStrComparators[1] = (a, b) -> {
            try {
                return a.equalsIgnoreCase(b) || a.matches(b);
            } catch (PatternSyntaxException ignore) {
                return false;
            }
        };
    }

    public static Map.Entry<String, Function<Double, Boolean>> parseFilter(String arg) {
        for (int i = 0; i < splitArgs.length; i++) {
            String splitBy = splitArgs[i];
            if (arg.contains(splitBy)) {
                String[] split = arg.split(splitBy, 2);
                if (split.length == 2) {
                    try {
                        Double b = PrimitiveBindings.Double(split[1]);
                        if (b != null) {
                            BiFunction<Double, Double, Boolean> comparator = splitComparators[i];
                            return new KeyValue<>(split[0], a -> comparator.apply(a, b));
                        }
                    } catch (IllegalArgumentException ignore) {}
                }
                return null;
            }
        }
        return null;
    }

    public static Map.Entry<String, Function<String, Boolean>> parseStringFilter(String arg) {
        for (int i = 0; i < splitStrArgs.length; i++) {
            String splitBy = splitStrArgs[i];
            if (arg.contains(splitBy)) {
                String[] split = arg.split(splitBy, 2);
                if (split.length == 2) {
                    BiFunction<String, String, Boolean> comparator = splitStrComparators[i];
                    return new KeyValue<>(split[0], a -> comparator.apply(a, split[1]));
                }
                return null;
            }
        }
        return null;
    }

    public static Number add(Number a, Number b) {
        if (a == null) return b;
        if (b == null) return a;
        return switch (a) {
            case Double d -> a.doubleValue() + b.doubleValue();
            case Float f  -> a.floatValue() + b.floatValue();
            case Long l   -> a.longValue() + b.longValue();
            default       -> a.intValue() + b.intValue();
        };
    }

    public static Number subtract(Number a, Number b) {
        if (a == null) {
            return switch (b) {
                case null -> 0d;
                case Double v -> -b.doubleValue();
                case Float v -> -b.floatValue();
                case Long l -> -b.longValue();
                default -> -b.intValue();
            };
        }
        if (b == null) return a;
        return switch (a) {
            case Double v -> a.doubleValue() - b.doubleValue();
            case Float v -> a.floatValue() - b.floatValue();
            case Long l -> a.longValue() - b.longValue();
            default -> a.intValue() - b.intValue();
        };
    }

    public static <T extends Number> T multiply(T a, T b) {
        return switch (a) {
            case Double d -> (T) (Number) Double.valueOf(d.doubleValue() * b.doubleValue());
            case Float f  -> (T) (Number) Float.valueOf(f.floatValue() * b.floatValue());
            case Long l   -> (T) (Number) Long.valueOf(l.longValue() * b.longValue());
            default       -> (T) (Number) Integer.valueOf(a.intValue() * b.intValue());
        };
    }

    public static Integer parseIntDef0(String input) {
        Integer val = parseInt(input);
        return val == null ? 0 : val;
    }

    public static Integer parseInt(String input) {
        Double result = parseDouble(input);
        return result == null ? null : (int) Math.round(result);
    }

    public static int parseInt(String input, boolean exception) {
        Integer result = parseInt(input);
        if (result == null) throw new IllegalArgumentException("Invalid integer: " + input);
        return result;
    }

    public static Double parseDoubleDef0(String input) {
        Double val = parseDouble(input);
        return val == null ? 0 : val;
    }

    public static Double parseDouble(String input) {
        if (input == null || input.isEmpty()) return null;
        if (input.charAt(0) == '$') {
            input = input.substring(1);
        } else if (input.length() > 2 && input.charAt(0) == '-' && input.charAt(1) == '$') {
            input = input.replaceFirst("\\$", "");
        }
        if ((input.indexOf(',') != -1)) {// > 4 && input.charAt(input.length() - 4) == ',') || (input.length() > 7 && input.charAt(input.length() - 7) == ',')) {
            input = input.replace(",", "");
        }
        try {
            if (input.isEmpty()) return null;
            switch (Character.toLowerCase(input.charAt(input.length() - 1))) {
                default:
                    return Double.parseDouble(input);
                case 'k':
                    return Double.parseDouble(input.substring(0, input.length() - 1)) * 1000L;
                case 'm':
                    return Double.parseDouble(input.substring(0, input.length() - 1)) * 1000000L;
                case 'b':
                    return Double.parseDouble(input.substring(0, input.length() - 1)) * 1000000000L;
                case 't':
                    return Double.parseDouble(input.substring(0, input.length() - 1)) * 1000000000000L;
            }
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    public static long factorial(int number) {
        long result = 1;

        for (int factor = 2; factor <= number; factor++) {
            result *= factor;
        }

        return result;
    }

    public static int log2nlz(int bits) {
        return Integer.SIZE - Integer.numberOfLeadingZeros(bits);
    }

    public static int floorZero(double d0) {
        int i = (int) d0;
        return d0 < (double) i ? i - 1 : i;
    }

    public static double max(double... values) {
        double max = Double.MIN_VALUE;
        for (double d : values) {
            if (d > max) {
                max = d;
            }
        }
        return max;
    }

    public static int max(int... values) {
        int max = Integer.MIN_VALUE;
        for (int d : values) {
            if (d > max) {
                max = d;
            }
        }
        return max;
    }

    public static int min(int... values) {
        int min = Integer.MAX_VALUE;
        for (int d : values) {
            if (d < min) {
                min = d;
            }
        }
        return min;
    }

    public static double min(double... values) {
        double min = Double.MAX_VALUE;
        for (double d : values) {
            if (d < min) {
                min = d;
            }
        }
        return min;
    }

    public static int ceilZero(float floatNumber) {
        int floor = (int) floatNumber;
        return floatNumber > (float) floor ? floor + 1 : floor;
    }

    public static double sqr(double val) {
        return val * val;
    }

    public static int sqr(int val) {
        return val * val;
    }

    public static int clamp(int check, int min, int max) {
        return check > max ? max : (Math.max(check, min));
    }

    public static float clamp(float check, float min, float max) {
        return check > max ? max : (Math.max(check, min));
    }

    public static double hypot(final double... pars) {
        double sum = 0;
        for (final double d : pars) {
            sum += Math.pow(d, 2);
        }
        return Math.sqrt(sum);
    }

    public static double hypot2(final double... pars) {
        double sum = 0;
        for (final double d : pars) {
            sum += Math.pow(d, 2);
        }
        return sum;
    }

    public static int wrap(int value, int min, int max) {
        if (max < min) {
            return value;
        }
        if (min == max) {
            return min;
        }
        int diff = max - min + 1;
        if (value < min) {
            return max - ((min - value) % diff);
        } else if (value > max) {
            return min + ((value - min) % diff);
        } else {
            return value;
        }
    }

    public static long inverseRound(double val) {
        long round = Math.round(val);
        return (long) (round + Math.signum(val - round));
    }

    public static int pair(short x, short y) {
        return (x << 16) | (y & 0xFFFF);
    }

    public static short unpairX(int hash) {
        return (short) (hash >> 16);
    }

    public static short unpairY(int hash) {
        return (short) (hash & 0xFFFF);
    }

    public static int pairChars(char firstChar, char secondChar) {
        // Combine the two chars into an int
        return ((int) firstChar << 16) | (int) secondChar;
    }

    public static char getXFromInt(int combinedInt) {
        return (char) (combinedInt >> 16);
    }

    public static char getYFromInt(int combinedInt) {
        return (char) combinedInt;
    }


    public static short pairByte(int x, int y) {
        return (short) ((x << 8) | (y & 0xFF));
    }

    public static byte unpairShortX(short pair) {
        return (byte) (pair >> 8);
    }

    public static byte unpairShortY(short pair) {
        return (byte) pair;
    }

    public static long pairInt(int x, int y) {
        return (((long) x) << 32) | (y & 0xffffffffL);
    }

    public static long tripleWorldCoord(int x, int y, int z) {
        return y + (((long) x & 0x3FFFFFF) << 8) + (((long) z & 0x3FFFFFF) << 34);
    }

    public static long untripleWorldCoordX(long triple) {
        return (((triple >> 8) & 0x3FFFFFF) << 38) >> 38;
    }

    public static long untripleWorldCoordY(long triple) {
        return triple & 0xFF;
    }

    public static long untripleWorldCoordZ(long triple) {
        return (((triple >> 34) & 0x3FFFFFF) << 38) >> 38;
    }

    public static short tripleBlockCoord(int x, int y, int z) {
        return (short) ((x & 15) << 12 | (z & 15) << 8 | y);
    }

    public static char tripleBlockCoordChar(int x, int y, int z) {
        return (char) ((x & 15) << 12 | (z & 15) << 8 | y);
    }

    public static int untripleBlockCoordX(int triple) {
        return (triple >> 12) & 0xF;
    }

    public static int untripleBlockCoordY(int triple) {
        return (triple & 0xFF);
    }

    public static int untripleBlockCoordZ(int triple) {
        return (triple >> 8) & 0xF;
    }

    public static int tripleSearchCoords(int x, int y, int z) {
        byte b1 = (byte) y;
        byte b3 = (byte) (x);
        byte b4 = (byte) (z);
        int x16 = (x >> 8) & 0x7;
        int z16 = (z >> 8) & 0x7;
        byte b2 = MathMan.pair8(x16, z16);
        return ((b1 & 0xFF)
                + ((b2 & 0x7F) << 8)
                + ((b3 & 0xFF) << 15)
                + ((b4 & 0xFF) << 23))
                ;
    }

    public static int pairSearchCoords(int x, int y) {
        byte b1 = (byte) ((x & 0xF) + ((y & 0xF) << 4));
        byte b2 = (byte) ((x >> 4) & 0xFF);
        byte b3 = (byte) ((y >> 4) & 0xFF);
        int x16 = (x >> 12) & 0xF;
        int y16 = (y >> 12) & 0xF;
        byte b4 = (byte) ((x16 & 0xF) + ((y16 & 0xF) << 4));
        return ((b1 & 0xFF)
                + ((b2 & 0xFF) << 8)
                + ((b3 & 0xFF) << 16)
                + ((b4 & 0xFF) << 24));
    }

    public static int unpairSearchCoordsX(int pair) {
        int x1 = (pair >> 24) & 0x7;
        int x2 = (pair >> 8) & 0xFF;
        int x3 = (pair & 0xF);
        return x3 + (x2 << 4) + (x1 << 12);
    }

    public static int unpairSearchCoordsY(int pair) {
        int y1 = ((pair >> 24) & 0x7F) >> 3;
        int y2 = (pair >> 16) & 0xFF;
        int y3 = (pair & 0xFF) >> 4;
        return y3 + (y2 << 4) + (y1 << 12);
    }

    public static long chunkXZ2Int(int x, int z) {
        return (long) x & 4294967295L | ((long) z & 4294967295L) << 32;
    }

    public static int unpairIntX(long pair) {
        return (int) (pair >> 32);
    }

    public static int unpairIntY(long pair) {
        return (int) pair;
    }

    public static byte pair16(int x, int y) {
        return (byte) (x + (y << 4));
    }

    public static byte unpair16x(byte value) {
        return (byte) (value & 0xF);
    }

    public static byte unpair16y(byte value) {
        return (byte) ((value >> 4) & 0xF);
    }

    public static byte pair8(int x, int y) {
        return (byte) (x + (y << 3));
    }

    public static byte unpair8x(int value) {
        return (byte) (value & 0x7);
    }

    public static byte unpair8y(int value) {
        return (byte) ((value >> 3) & 0x7F);
    }

    public static int lossyFastDivide(int a, int b) {
        return (a * ((1 << 16) / b)) >> 16;
    }

    public static int gcd(int a, int b) {
        if (b == 0) {
            return a;
        }
        return gcd(b, a % b);
    }

    public static int gcd(int[] a) {
        int result = a[0];
        for (int i = 1; i < a.length; i++) {
            result = gcd(result, a[i]);
        }
        return result;
    }


    public static double getMean(int[] array) {
        double count = 0;
        for (int i : array) {
            count += i;
        }
        return count / array.length;
    }

    public static double getMean(double[] array) {
        double count = 0;
        for (double i : array) {
            count += i;
        }
        return count / array.length;
    }

    /**
     * Returns [x, y, z]
     *
     * @param yaw
     * @param pitch
     * @return
     */
    public static float[] getDirection(float yaw, float pitch) {
        double pitch_sin = Math.sin(pitch);
        return new float[]{(float) (pitch_sin * Math.cos(yaw)), (float) (pitch_sin * Math.sin(yaw)), (float) Math.cos(pitch)};
    }

    public static int roundInt(double value) {
        return (int) (value < 0 ? (value == (int) value) ? value : value - 1 : value);
    }

    public static float sqrtApprox(float f) {
        return f * Float.intBitsToFloat(0x5f375a86 - (Float.floatToIntBits(f) >> 1));
    }

    public static double sqrtApprox(double d) {
        return Double.longBitsToDouble(((Double.doubleToLongBits(d) - (1l << 52)) >> 1) + (1l << 61));
    }

    public static float invSqrt(float x) {
        float xhalf = 0.5f * x;
        int i = Float.floatToIntBits(x);
        i = 0x5f3759df - (i >> 1);
        x = Float.intBitsToFloat(i);
        x = x * (1.5f - (xhalf * x * x));
        return x;
    }

    public static boolean isInteger(CharSequence str) {
        if (str == null) {
            return false;
        }
        int length = str.length();
        if (length == 0) {
            return false;
        }
        int i = 0;
        if (str.charAt(0) == '-') {
            if (length == 1) {
                return false;
            }
            i = 1;
        }
        for (; i < length; i++) {
            char c = str.charAt(i);
            if ((c <= '/') || (c >= ':')) {
                return false;
            }
        }
        return true;
    }

    public static double getSD(double[] array, double av) {
        double sd = 0;
        for (double element : array) {
            sd += Math.pow(Math.abs(element - av), 2);
        }
        return Math.sqrt(sd / array.length);
    }

    public static double getSD(int[] array, double av) {
        double sd = 0;
        for (int element : array) {
            sd += Math.pow(Math.abs(element - av), 2);
        }
        return Math.sqrt(sd / array.length);
    }

    public static int absByte(int value) {
        return (value ^ (value >> 8)) - (value >> 8);
    }

    public static int mod(int x, int y) {
        if (isPowerOfTwo(y)) {
            return x & (y - 1);
        }
        return x % y;
    }

    public static int unsignedmod(int x, int y) {
        if (isPowerOfTwo(y)) {
            return x & (y - 1);
        }
        return x % y;
    }

    public static boolean isPowerOfTwo(int x) {
        return (x & (x - 1)) == 0;
    }
}