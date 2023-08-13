package link.locutus.discord.util.math;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.util.IOUtil;
import link.locutus.discord.util.MathMan;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class ArrayUtil {
    public static final DoubleBinaryOperator DOUBLE_ADD = (x, y) -> x + y;
    public static final DoubleBinaryOperator DOUBLE_SUBTRACT = (x, y) -> x - y;
    public static final IntBinaryOperator INT_ADD = (x, y) -> x + y;

    public static double cosineSimilarity(double[] vectorA, double[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public static double cosineSimilarity(float[] vectorA, float[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static <T> void getSubsets(List<T> superSet, int k, int idx, Set<T> current, List<Set<T>> solution) {
        //successful stop clause
        if (current.size() == k) {
            solution.add(new HashSet<>(current));
            return;
        }
        //unseccessful stop clause
        if (idx == superSet.size()) return;
        T x = superSet.get(idx);
        current.add(x);
        //"guess" x is in the subset
        getSubsets(superSet, k, idx+1, current, solution);
        current.remove(x);
        //"guess" x is not in the subset
        getSubsets(superSet, k, idx+1, current, solution);
    }

    public static <T extends Enum<T>> byte[] writeEnumMap(Map<T, Integer> data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (Map.Entry<T, Integer> entry : data.entrySet()) {
            IOUtil.writeVarInt(baos, entry.getKey().ordinal());
            IOUtil.writeVarInt(baos, entry.getValue());
        }
        return baos.toByteArray();
    }


    public static byte[] writeIntSet(Set<Integer> numbers) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int i : numbers) {
            IOUtil.writeVarInt(baos, i);
        }
        return baos.toByteArray();
    }

    public static Set<Integer> readIntSet(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        Set<Integer> res = new HashSet<>();
        while (bais.available() > 0) {
            res.add(IOUtil.readVarInt(bais));
        }
        return res;
    }
    public static <T extends Enum<T>> long writeEnumSet(Set<T> enums) {
        if (enums.size() > 64) {
            throw new IllegalArgumentException("Enum set too large");
        }
        long res = 0;
        for (T e : enums) {
            res |= 1L << e.ordinal();
        }
        return res;
    }

    public static <T extends Enum<T>> Set<T> readEnumSet(long data, Class<T> clazz) {
        Set<T> res = new HashSet<>();
        for (T e : clazz.getEnumConstants()) {
            if ((data & (1L << e.ordinal())) != 0) {
                res.add(e);
            }
        }
        return res;
    }

    public static <T extends Enum<T>> Map<T, Integer> readEnumMap(byte[] data, Class<T> clazz) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        Map<T, Integer> map = new EnumMap<>(clazz);
        while (bais.available() > 0) {
            int ordinal = IOUtil.readVarInt(bais);
            int value = IOUtil.readVarInt(bais);
            map.put(clazz.getEnumConstants()[ordinal], value);
        }
        return map;
    }

    public static <T> List<Set<T>> getSubsets(List<T> superSet, int k) {
        List<Set<T>> res = new ArrayList<>();
        getSubsets(superSet, k, 0, new HashSet<T>(), res);
        return res;
    }

    public static <T> List<Set<T>> getSubsets(List<T> superSet) {
        List<Set<T>> res = new ArrayList<>();
        for (int i = 1; i < superSet.size(); i++) {
            getSubsets(superSet, i, 0, new HashSet<T>(), res);
        }
        res.add(new HashSet<>(superSet));
        return res;
    }

    public static Map.Entry<Integer, Integer> findMinAvgSubarray(Function<Integer, Number> arr, int n, int k)
    {
        // k must be smaller than or equal to n
        if (n < k)
            return null;

        // Initialize beginning index of result
        int res_index = 0;

        // Compute sum of first subarray of size k
        double curr_sum = 0;
        for (int i = 0; i < k; i++)
            curr_sum += arr.apply(i).doubleValue();

        // Initialize minimum sum as current sum
        double min_sum = curr_sum;

        // Traverse from (k+1)'th element to n'th element
        for (int i = k; i < n; i++)
        {
            // Add current item and remove first
            // item of previous subarray
            curr_sum += arr.apply(i).doubleValue() - arr.apply(i - k).doubleValue();

            // Update result if needed
            if (curr_sum < min_sum) {
                min_sum = curr_sum;
                res_index = (i - k + 1);
            }
        }

        return new AbstractMap.SimpleEntry<>(res_index, res_index + k - 1);
    }

    public static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(x);
        return buffer.array();
    }

    public static long bytesToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.put(bytes);
        buffer.flip();//need flip
        return buffer.getLong();
    }

    public static double bytesToDouble(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES);
        buffer.put(bytes);
        buffer.flip();//need flip
        return buffer.getDouble();
    }

    public static int getMedian(long[] arr) {
        long total = 0;
        for (long i : arr) total += i;
        int mid = (int) ((total + 1) / 2);
        for (int i = 0; i < arr.length; i++) {
            mid -= arr[i];
            if (mid <= 0) return i;
        }
        return 0;
    }

    public static Map.Entry<Integer, Integer> findMinAvgSubarray(double[] arr, int k) {
        return findMinAvgSubarray(n -> arr[n], arr.length, k);
    }

    public static Map.Entry<Integer, Integer> findMinAvgSubarray(int[] arr, int k) {
        return findMinAvgSubarray(n -> arr[n], arr.length, k);
    }

    public static double addProbability(double[] probabilities) {
        double total = 0;
        for (int i = 0; i < probabilities.length; i++) {
            double prob = probabilities[i];
            total = total + prob - (total * prob);
        }
        return total;
    }

    public static double[] apply(DoubleBinaryOperator operator, boolean newArr, double[]... arrays) {
        if (arrays.length == 0) return null;
        if (arrays.length == 1) return arrays[0];
        double[] buffer = arrays[0];
        if (newArr) buffer = buffer.clone();
        for (int i = 1; i < arrays.length; i++) {
            buffer = apply(operator, buffer, arrays[i], newArr);
        }
        return buffer;
    }

    public static int[] apply(IntBinaryOperator operator, int[] a, int b[]) {
        return apply(operator, a, b, false);
    }

    public static int[] apply(IntBinaryOperator operator, int[] a, int b[], boolean newArr) {
        int[] result = newArr ? new int[a.length] : a;
        for (int i = 0; i < a.length; i++) {
            result[i] = operator.applyAsInt(a[i], b[i]);
        }
        return result;
    }

    public static long[] dollarToCents(double[] deposit) {
        long[] depositCents = new long[deposit.length];
        for (int i = 0; i < deposit.length; i++) depositCents[i] = (long) (deposit[i] * 100);
        return depositCents;
    }

    public static double[] centsToDollars(long[] cents) {
        double[] dollars = new double[cents.length];
        for (int i = 0; i < cents.length; i++) dollars[i] = cents[i] / 100d;
        return dollars;
    }

    public static double[] apply(DoubleBinaryOperator operator, double[] a, double b[]) {
        return apply(operator, a, b, false);
    }

    public static double[] apply(DoubleBinaryOperator operator, double[] a, double b[], boolean newArr) {
        double[] result = newArr ? new double[a.length] : a;
        for (int i = 0; i < a.length; i++) {
            result[i] = operator.applyAsDouble(a[i], b[i]);
        }
        return result;
    }

    public static byte[] toByteArray(long[] array) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(out);
        for (long l : array) {
            try {
                dout.writeLong(l);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return out.toByteArray();
    }

    public static byte[] toByteArray(double[] array) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(out);
        for (double l : array) {
            try {
                dout.writeDouble(l);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return out.toByteArray();
    }

    public static byte[] toByteArray(float[] array) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(out);
        for (float f : array) {
            try {
                dout.writeFloat(f);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return out.toByteArray();
    }

    public static long[] toLongArray(byte[] array) {
        DataInputStream is = new DataInputStream(new ByteArrayInputStream(array));
        int len = array.length / 8;
        long[] result = new long[len];
        for (int i = 0; i < len; i++) {
            try {
                result[i] = is.readLong();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public static double[] toDoubleArray(byte[] array) {
        DataInputStream is = new DataInputStream(new ByteArrayInputStream(array));
        int len = array.length / 8;
        double[] result = new double[len];
        for (int i = 0; i < len; i++) {
            try {
                result[i] = is.readDouble();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public static float[] toFloatArray(byte[] array) {
        DataInputStream is = new DataInputStream(new ByteArrayInputStream(array));
        int len = array.length / 4;
        float[] result = new float[len];
        for (int i = 0; i < len; i++) {
            try {
                result[i] = is.readFloat();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public static byte[] compress(byte[] data) throws IOException {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        deflater.setInput(data);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
        deflater.finish();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer); // returns the generated code... index
            outputStream.write(buffer, 0, count);
        }
        outputStream.close();
        byte[] output = outputStream.toByteArray();
        return output;
    }

    public static byte[] decompress(byte[] data) throws IOException, DataFormatException {
        Inflater inflater = new Inflater(true);
        inflater.setInput(data);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
        byte[] buffer = new byte[1024];
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            outputStream.write(buffer, 0, count);
        }
        outputStream.close();
        byte[] output = outputStream.toByteArray();
        return output;
    }

    public static <T> int binarySearchGreater(List<T> list, Predicate<T> isValid) {
        return binarySearchGreater(list, isValid, 0, list.size() - 1);
    }

    public static <T> int binarySearchFirst(List<T> list, Object2IntFunction<T> isValid) {
        return binarySearchFirst(list, isValid, 0, list.size() - 1);
    }

    public static <T> int binarySearchFirst(List<T> list, Object2IntFunction<T> isValid, int start, int end) {
        if (start > end) return -1;
        int mid = (start + end) / 2;
        int value = isValid.applyAsInt(list.get(mid));
        if (value == 1) return binarySearchFirst(list, isValid, mid + 1, end);
        if (value == -1) return binarySearchFirst(list, isValid, start, mid - 1);
        return mid;
    }

    public static <T> int binarySearchLess(List<T> list, Predicate<T> isValid) {
        return binarySearchLess(list, isValid, 0, list.size() - 1);
    }

    /**
     * Returns index of first element greater than isValid
     */
    public static <T> int binarySearchGreater(List<T> list, Predicate<T> isValid, int start, int end) {
        if (start > end) return -1;
        int mid = (start + end) / 2;
        if (isValid.test(list.get(mid))) {
            int result = binarySearchGreater(list, isValid, start, mid - 1);
            if (result == -1) return mid;
            return result;
        } else {
            return binarySearchGreater(list, isValid, mid + 1, end);
        }
    }

    /**
     * Returns index of first element less than isValid
     */
    public static <T> int binarySearchLess(List<T> list, Predicate<T> isValid, int start, int end) {
        if (start > end) return -1;
        int mid = (start + end) / 2;
        if (isValid.test(list.get(mid))) {
            int result = binarySearchLess(list, isValid, mid + 1, end);
            if (result == -1) return mid;
            return result;
        } else {
            return binarySearchLess(list, isValid, start, mid - 1);
        }
    }

    public static int binarySearch(int min, int max, Function<Integer, Integer> valueFunc, int goal) {
        if (min >= max) {
            return max;
        }
        int index = (int) Math.ceil((max + min) / 2d);

        int midVal = valueFunc.apply(index);
        if (midVal > goal) {
            return binarySearch(min, midVal, valueFunc, goal);
        } else if (midVal == goal) {
            return midVal;
        } else {
            return binarySearch(midVal, max, valueFunc, goal);
        }
    }

    /**
     * Cached supplier
     * @param delegate
     * @param <T>
     * @return
     */
    public static <T> Supplier<T> memorize(Supplier<T> delegate) {
        AtomicBoolean valueSet = new AtomicBoolean();
        AtomicReference<T> value = new AtomicReference<>();
        return () -> {
            T val = value.get();
            if (val == null && !valueSet.get()) {
                synchronized(value) {
                    val = value.get();
                    if (val == null && !valueSet.get()) {
                        val = delegate.get();
                        value.set(val);
                        valueSet.set(true);
                    }
                }
            }
            return val;
        };
    }

    public static class DoubleArray {
        private double[] array;

        public DoubleArray(double[] array) {
            this.array = array;
        }

        public static DoubleArray parse(String input) {
            String[] values = input.replaceAll("\\{", "").replaceAll("\\}", "").split(",");
            double[] array = new double[values.length];
            for (int i = 0; i < values.length; i++) {
                array[i] = Double.parseDouble(values[i].trim());
            }
            return new DoubleArray(array);
        }

        public DoubleArray add(DoubleArray other) {
            double[] result = new double[array.length];
            for (int i = 0; i < array.length; i++) {
                result[i] = array[i] + other.array[i];
            }
            return new DoubleArray(result);
        }

        public DoubleArray power(double value) {
            double[] result = new double[array.length];
            for (int i = 0; i < array.length; i++) {
                result[i] = Math.pow(array[i], value);
            }
            return new DoubleArray(result);
        }

        public DoubleArray subtract(DoubleArray other) {
            double[] result = new double[array.length];
            for (int i = 0; i < array.length; i++) {
                result[i] = array[i] - other.array[i];
            }
            return new DoubleArray(result);
        }

        public DoubleArray multiply(double value) {
            double[] result = new double[array.length];
            for (int i = 0; i < array.length; i++) {
                result[i] = array[i] * value;
            }
            return new DoubleArray(result);
        }

        public DoubleArray divide(double value) {
            if (value == 0) {
                throw new ArithmeticException("Division by zero");
            }
            double[] result = new double[array.length];
            for (int i = 0; i < array.length; i++) {
                result[i] = array[i] / value;
            }
            return new DoubleArray(result);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < array.length; i++) {
                sb.append(array[i]);
                if (i < array.length - 1) {
                    sb.append(",");
                }
            }
            sb.append("}");
            return sb.toString();
        }

        public double[] toArray() {
            return array;
        }
    }

    private static final String OPERATORS = "+-*/^";
    private static final String LEFT_PAREN = "(";
    private static final String RIGHT_PAREN = ")";

    public static DoubleArray calculate(String input, Function<String, DoubleArray> parseOrigin) {
        if (parseOrigin == null) {
            parseOrigin = DoubleArray::parse;
        }
        Queue<String> outputQueue = new ArrayDeque<>();
        Deque<String> operatorStack = new ArrayDeque<>();

        String[] tokens = splitMathExpression(input);
        for (String token : tokens) {
            System.out.println(token);
        }

        for (String token : tokens) {
            if (isOperator(token)) {
                while (!operatorStack.isEmpty() && isOperator(operatorStack.peek())) {
                    if ((isLeftAssociative(token) && comparePrecedence(token, operatorStack.peek()) <= 0)
                            || (comparePrecedence(token, operatorStack.peek()) < 0)) {
                        outputQueue.offer(operatorStack.pop());
                        continue;
                    }
                    break;
                }
                operatorStack.push(token);
            } else if (token.equals(LEFT_PAREN)) {
                operatorStack.push(token);
            } else if (token.equals(RIGHT_PAREN)) {
                while (!operatorStack.isEmpty() && !operatorStack.peek().equals(LEFT_PAREN)) {
                    outputQueue.offer(operatorStack.pop());
                }
                operatorStack.pop();
            } else {
                outputQueue.offer(token);
            }
        }

        while (!operatorStack.isEmpty()) {
            outputQueue.offer(operatorStack.pop());
        }

        Deque<DoubleArray> stack = new ArrayDeque<>();
        for (String token : outputQueue) {
            if (isOperator(token)) {
                DoubleArray right = stack.pop();
                DoubleArray left = stack.pop();
                switch (token) {
                    case "+":
                        stack.push(left.add(right));
                        break;
                    case "-":
                        stack.push(left.subtract(right));
                        break;
                    case "*":
                        stack.push(left.multiply(right.array[0]));
                        break;
                    case "/":
                        stack.push(left.divide(right.array[0]));
                        break;
                    case "^":
                        stack.push(left.power(right.array[0]));
                        break;
                }
            } else {
                if (NumberUtils.isNumber(token)) {
                    stack.push(new DoubleArray(new double[]{MathMan.parseDouble(token)}));
                    continue;
                }
                stack.push(parseOrigin.apply(token));
            }
        }

        return stack.pop();
    }

    private static boolean isOperator(String token) {
        return OPERATORS.contains(token);
    }

    private static boolean isLeftAssociative(String token) {
        return !token.equals("^");
    }

    private static int comparePrecedence(String token1, String token2) {
        return getPrecedence(token1) - getPrecedence(token2);
    }

    public static String[] splitMathExpression(String input) {
        Queue<String> tokens = new ArrayDeque<>();

        StringBuilder currentToken = new StringBuilder();
        boolean insideCurlyBraces = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '{') {
                insideCurlyBraces = true;
                currentToken.append(c);
            } else if (c == '}') {
                insideCurlyBraces = false;
                currentToken.append(c);
                tokens.add(currentToken.toString());
                currentToken = new StringBuilder();
            } else if (insideCurlyBraces) {
                currentToken.append(c);
            } else if (c == '(' || isNonNegOperator(c)) {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken = new StringBuilder();
                }
                currentToken.append(c);
                tokens.add(currentToken.toString());
                currentToken = new StringBuilder();

                // Check if next character is a negative sign
                if (i + 1 < input.length() && input.charAt(i + 1) == '-') {
                    currentToken.append('-');
                    i++; // Skip the negative sign
                }
            } else if (c == ')' || c == '-') {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken = new StringBuilder();
                }
                tokens.add(String.valueOf(c));
            } else if (!Character.isWhitespace(c)) {
                currentToken.append(c);
            }
        }

        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }

        return tokens.toArray(new String[0]);
    }

    private static boolean isNonNegOperator(char c) {
        return c == '+' || c == '*' || c == '/' || c == '^';
    }

    private static int getPrecedence(String token) {
        return switch (token) {
            case "+", "-" -> 1;
            case "*", "/" -> 2;
            case "^" -> 3;
            default -> 0;
        };
    }
}