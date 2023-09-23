package link.locutus.discord.util.math;

import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.util.IOUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import org.apache.commons.lang3.math.NumberUtils;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    public interface MathToken<T extends MathToken<T>> {
        T create(String input);
        T add(T other);
        T power(double value);
        T power(T value);
        T modulo(T value);
        T modulo(double value);
        T multiply(T other);
        T multiply(double value);
        T divide(T other);
        T divide(double value);
        T subtract(T other);
    }

    public static class DoubleArray implements MathToken<DoubleArray> {
        private double[] array;

        public DoubleArray(double... array) {
            this.array = array;
        }

        @Override
        public DoubleArray create(String input) {
            return parse(input);
        }

        public static DoubleArray parse(String input) {
            List<String> values = StringMan.split(input.replaceAll("\\{", "").replaceAll("\\}", ""), ',');
            double[] array = new double[values.size()];
            for (int i = 0; i < values.size(); i++) {
                array[i] = PrimitiveBindings.Double(values.get(i).trim());
            }
            return new DoubleArray(array);
        }

        @Override
        public DoubleArray add(DoubleArray other) {
            double[] result = new double[array.length];
            for (int i = 0; i < array.length; i++) {
                result[i] = array[i] + other.array[i];
            }
            return new DoubleArray(result);
        }

        @Override
        public DoubleArray power(double value) {
            double[] result = new double[array.length];
            for (int i = 0; i < array.length; i++) {
                result[i] = Math.pow(array[i], value);
            }
            return new DoubleArray(result);
        }

        @Override
        public DoubleArray subtract(DoubleArray other) {
            double[] result = new double[array.length];
            for (int i = 0; i < array.length; i++) {
                result[i] = array[i] - other.array[i];
            }
            return new DoubleArray(result);
        }

        @Override
        public DoubleArray multiply(double value) {
            double[] result = new double[array.length];
            for (int i = 0; i < array.length; i++) {
                result[i] = array[i] * value;
            }
            return new DoubleArray(result);
        }

        @Override
        public DoubleArray multiply(DoubleArray value) {
            if (value.array.length == 1) {
                return multiply(value.array[0]);
            }
            if (this.array.length == 1) {
                return value.multiply(this.array[0]);
            }
            double[] result = new double[array.length];
            for (int i = 0; i < array.length; i++) {
                result[i] = array[i] * value.array[i];
            }
            return new DoubleArray(result);
        }

        @Override
        public DoubleArray divide(DoubleArray value) {
            if (value.array.length == 1) {
                return divide(value.array[0]);
            }
            if (this.array.length == 1) {
                return value.divide(this.array[0]);
            }
            double[] result = new double[array.length];
            for (int i = 0; i < array.length; i++) {
                result[i] = array[i] / value.array[i];
            }
            return new DoubleArray(result);
        }

        @Override
        public DoubleArray power(DoubleArray value) {
            if (value.array.length == 1) {
                return power(value.array[0]);
            }
            if (this.array.length == 1) {
                return value.power(this.array[0]);
            }
            double[] result = new double[array.length];
            for (int i = 0; i < array.length; i++) {
                result[i] = Math.pow(array[i], value.array[i]);
            }
            return new DoubleArray(result);
        }

        @Override
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

        @Override
        public DoubleArray modulo(DoubleArray value) {
            if (value.array.length == 1) {
                return modulo(value.array[0]);
            }
            if (this.array.length == 1) {
                return value.modulo(this.array[0]);
            }
            double[] result = new double[array.length];
            for (int i = 0; i < array.length; i++) {
                result[i] = array[i] % value.array[i];
            }
            return new DoubleArray(result);
        }

        @Override
        public DoubleArray modulo(double value) {
            if (value == 0) {
                throw new ArithmeticException("Division by zero");
            }
            double[] result = new double[array.length];
            for (int i = 0; i < array.length; i++) {
                result[i] = array[i] % value;
            }
            return new DoubleArray(result);
        }

        public double[] toArray() {
            return array;
        }
    }

    private static final String OPERATORS = "+-*/^";
    private static final String LEFT_PAREN = "(";
    private static final String RIGHT_PAREN = ")";

    public static DoubleArray calculateDouble(String input, @Nullable Function<String, DoubleArray> parseOrigin) {
        if (parseOrigin == null) {
            parseOrigin = DoubleArray::parse;
        }
        return calculate(input, parseOrigin);
    }

    public static void main(String[] args) {
        String input = "{test{blah}}*{test{blah}}";

        DoubleArray value = calculate(input, new Function<String, DoubleArray>() {
            @Override
            public DoubleArray apply(String s) {
                System.out.println("Parse " + s);
                return new DoubleArray(new double[]{5});
            }
        });
        System.out.println(value.toString());
    }

    public static class ResolvedFunction<T, V> implements Function<T, V> {
        private final V object;
        public ResolvedFunction(V object) {
            this.object = object;
        }

        @Override
        public final V apply(T t) {
            return object;
        }

        public V get() {
            return object;
        }
    }

    public static class LazyMathArray<T> implements MathToken<LazyMathArray<T>> {
        private final DoubleArray resolved;
        private final Function<T, DoubleArray> resolver;
        private final Function<String, Function<T, DoubleArray>> parser;

        public LazyMathArray(DoubleArray resolved, Function<String, Function<T, DoubleArray>> parser) {
            this.resolved = resolved;
            this.resolver = null;
            this.parser = parser;
        }

        public LazyMathArray(String input, Function<String, Function<T, DoubleArray>> parser) {
            this(parser.apply(input), parser);
        }

        public LazyMathArray(Function<T, DoubleArray> resolver, Function<String, Function<T, DoubleArray>> parser) {
            if (resolver instanceof ResolvedFunction<T, DoubleArray> f) {
                this.resolved = f.object;
                this.resolver = null;
            } else {
                this.resolved = null;
                this.resolver = resolver;
            }
            this.parser = parser;
        }

        @Override
        public LazyMathArray<T> create(String input) {
            Function<T, DoubleArray> newResolver = parser.apply(input);
            if (newResolver instanceof ResolvedFunction<T, DoubleArray> f) {
                return new LazyMathArray<>(f.object, parser);
            }
            return new LazyMathArray<T>(newResolver, parser);
        }

        public DoubleArray resolve(T input) {
            if (resolved != null) return resolved;
            return resolver.apply(input);
        }

        public DoubleArray getOrNull() {
            return resolved;
        }

        @Override
        public LazyMathArray<T> add(LazyMathArray<T> other) {
            if (this.resolved != null && other.resolved != null) {
                return new LazyMathArray<>(this.resolved.add(other.resolved), parser);
            }
            return new LazyMathArray<>(t -> this.resolve(t).add(other.resolve(t)), parser);
        }

        @Override
        public LazyMathArray<T> power(double value) {
            if (this.resolved != null) {
                return new LazyMathArray<>(this.resolved.power(value), parser);
            }
            return new LazyMathArray<>(t -> this.resolve(t).power(value), parser);
        }

        @Override
        public LazyMathArray<T> power(LazyMathArray<T> other) {
            if (this.resolved != null && other.resolved != null) {
                return new LazyMathArray<>(this.resolved.power(other.resolved), parser);
            }
            return new LazyMathArray<>(t -> this.resolve(t).power(other.resolve(t)), parser);
        }

        @Override
        public LazyMathArray<T> multiply(double value) {
            if (this.resolved != null) {
                return new LazyMathArray<>(this.resolved.multiply(value), parser);
            }
            return new LazyMathArray<>(t -> this.resolve(t).multiply(value), parser);
        }

        @Override
        public LazyMathArray<T> divide(double value) {
            if (this.resolved != null) {
                return new LazyMathArray<>(this.resolved.divide(value), parser);
            }
            return new LazyMathArray<>(t -> this.resolve(t).divide(value), parser);
        }

        @Override
        public LazyMathArray<T> subtract(LazyMathArray<T> other) {
            if (this.resolved != null && other.resolved != null) {
                return new LazyMathArray<>(this.resolved.subtract(other.resolved), parser);
            }
            return new LazyMathArray<>(t -> this.resolve(t).subtract(other.resolve(t)), parser);
        }

        @Override
        public LazyMathArray<T> multiply(LazyMathArray<T> other) {
            if (this.resolved != null && other.resolved != null) {
                return new LazyMathArray<>(this.resolved.multiply(other.resolved), parser);
            }
            return new LazyMathArray<>(t -> this.resolve(t).multiply(other.resolve(t)), parser);
        }

        @Override
        public LazyMathArray<T> divide(LazyMathArray<T> other) {
            if (this.resolved != null && other.resolved != null) {
                return new LazyMathArray<>(this.resolved.divide(other.resolved), parser);
            }
            return new LazyMathArray<>(t -> this.resolve(t).divide(other.resolve(t)), parser);
        }

        @Override
        public LazyMathArray<T> modulo(double value) {
            if (this.resolved != null) {
                return new LazyMathArray<>(this.resolved.modulo(value), parser);
            }
            return new LazyMathArray<>(t -> this.resolve(t).modulo(value), parser);
        }

        @Override
        public LazyMathArray<T> modulo(LazyMathArray<T> value) {
            if (this.resolved != null && value.resolved != null) {
                return new LazyMathArray<>(this.resolved.modulo(value.resolved), parser);
            }
            return new LazyMathArray<>(t -> this.resolve(t).modulo(value.resolve(t)), parser);
        }
    }

    public static <T extends MathToken<T>> T calculate(String input, Function<String, T> parseOrigin) {
        Queue<String> outputQueue = new ArrayDeque<>();
        Deque<String> operatorStack = new ArrayDeque<>();

        String[] tokens = splitMathExpression(input);

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

        Deque<T> stack = new ArrayDeque<>();
        for (String token : outputQueue) {
            if (isOperator(token)) {
                T right = stack.pop();
                T left = stack.pop();
                switch (token) {
                    case "+":
                        stack.push(left.add(right));
                        break;
                    case "-":
                        stack.push(left.subtract(right));
                        break;
                    case "*":
                        stack.push(left.multiply(right));
                        break;
                    case "/":
                        stack.push(left.divide(right));
                        break;
                    case "^":
                        stack.push(left.power(right));
                        break;
                    case "%":
                        stack.push(left.modulo(right));
                        break;
                }
            } else {
                T arr = parseOrigin.apply(token);
                stack.push(arr);
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
        int curlBracketLevel = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '{') {
                curlBracketLevel++;
                currentToken.append(c);
            } else if (c == '}') {
                curlBracketLevel--;
                currentToken.append(c);
                if (curlBracketLevel == 0) {
                    tokens.add(currentToken.toString());
                    currentToken = new StringBuilder();
                }
            } else if (curlBracketLevel > 0) {
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

    public static class ParseResult<T> {
        public final List<Predicate<T>> predicates;
        public final Map<T, Predicate<T>> conditionalNumbers;
        public final Set<T> resolvedNumbers;
        public final AtomicBoolean hadNonFilter;
        private final String input;

        public ParseResult(String input, List<Predicate<T>> predicates, AtomicBoolean hadNonFilter) {
            this.input = input;
            this.predicates = predicates;
            this.resolvedNumbers = new LinkedHashSet<>();
            this.conditionalNumbers = new LinkedHashMap<>();
            this.hadNonFilter = hadNonFilter;
        }

        public ParseResult<T> add(Collection<T> numbers) {
            hadNonFilter.set(true);
            this.resolvedNumbers.addAll(numbers);
            return this;
        }

        @Override
        public String toString() {
            return "Input: `" + input + "` | predicates: " + predicates.size() + " | " + resolvedNumbers.size() + " resolved | " + conditionalNumbers.size() + " conditional | " + hadNonFilter.get();
        }

        public void addResult(ParseResult<T> other) {
            this.resolvedNumbers.addAll(other.resolvedNumbers);
            for (Map.Entry<T, Predicate<T>> entry : other.conditionalNumbers.entrySet()) {
                T number = entry.getKey();
                if (resolvedNumbers.contains(number)) {
                    continue;
                }
                Predicate<T> predicate = entry.getValue();

                Predicate<T> existing = this.conditionalNumbers.get(number);
                if (existing != null) {
                    predicate = or(predicate, existing);
                }
                this.conditionalNumbers.put(number, predicate);
            }
            this.predicates.addAll(other.predicates);
        }

        public void combinePredicatesAndNumbers() {
            // Removes all conditionals that are already resolved
            conditionalNumbers.keySet().removeAll(resolvedNumbers);
            // If there are predicates:

            if (!predicates.isEmpty()) {
                //  - adds the predicate to each existing conditional
                for (Map.Entry<T, Predicate<T>> entry : conditionalNumbers.entrySet()) {
                    T number = entry.getKey();
                    Predicate<T> predicate = entry.getValue();

                    List<Predicate<T>> newPredicates = new ArrayList<>(predicates.size() + 1);
                    newPredicates.addAll(predicates);
                    newPredicates.add(predicate);

                    predicate = and(newPredicates);
                    conditionalNumbers.put(number, predicate);
                }

                //  - turns all resolved numbers to conditionals
                for (T number : resolvedNumbers) {
                    conditionalNumbers.put(number, and(predicates));
                }
                // clear resolved
                resolvedNumbers.clear();
                // clear predicates
                predicates.clear();
            }
        }

        public Set<T> resolve() {
            combinePredicatesAndNumbers();
            // resolve conditional numbers
            for (Map.Entry<T, Predicate<T>> entry : conditionalNumbers.entrySet()) {
                T number = entry.getKey();
                Predicate<T> predicate = entry.getValue();
                if (predicate.test(number)) {
                    resolvedNumbers.add(number);
                }
            }
            conditionalNumbers.clear();
            return resolvedNumbers;
        }
    }

    public static <T> Predicate<T> and(Predicate<T> a, Predicate<T> b) {
        return a.and(b);
    }

    public static <T> Predicate<T> xor(Predicate<T> a, Predicate<T> b) {
        return number -> a.test(number) ^ b.test(number);
    }

    public static <T> Predicate<T> or(Predicate<T> a, Predicate<T> b) {
        return a.or(b);
    }

    public static <T> Predicate<T> xor(List<Predicate<T>> predicates) {
        if (predicates.size() == 1) {
            return predicates.get(0);
        }
        if (predicates.size() == 2) {
            return xor(predicates.get(0), predicates.get(1));
        }
        return number -> {
            int count = 0;
            for (Predicate<T> predicate : predicates) {
                if (predicate.test(number)) {
                    count++;
                    if (count > 1) {
                        return false;
                    }
                }
            }
            return count == 1;
        };
    }

    public static <T> Predicate<T> and(List<Predicate<T>> predicates) {
        if (predicates.size() == 1) {
            return predicates.get(0);
        }
        if (predicates.size() == 2) {
            return and(predicates.get(0), predicates.get(1));
        }
        return predicates.stream().reduce(Predicate::and).orElseThrow();
    }

    public static <T> Predicate<T> or(List<Predicate<T>> predicates) {
        if (predicates.size() == 2) {
            return or(predicates.get(0), predicates.get(1));
        }
        return predicates.stream().reduce(Predicate::or).orElseThrow();
    }

    private static <T> ParseResult<T> parseTokens(String input, Function<String, Set<T>> parseSet, Function<String, Predicate<T>> parsePredicate) {
        List<String> splitAnd = StringMan.split(input, ',');

        List<ParseResult<T>> andResults = new ArrayList<>();

        for (String andGroup : splitAnd) {
            List<String> splitXor = StringMan.split(andGroup, '^');
            if (splitXor.isEmpty()) {
                throw new IllegalArgumentException("Invalid group: `" + andGroup + "`: Empty group");
            }

            List<ParseResult<T>> xorResults = new ArrayList<>();

            for (String xorGroup : splitXor) {
                List<String> splitOr = StringMan.split(xorGroup, '|');
                if (splitOr.isEmpty()) {
                    throw new IllegalArgumentException("Invalid group: `" + xorGroup + "`: Empty group");
                }

                List<ParseResult<T>> orResults = new ArrayList<>();

                for (String elem : splitOr) {
                    if (elem.isEmpty()) {
                        if (xorGroup.isEmpty()) {
                            if (andGroup.isEmpty()) {
                                throw new IllegalArgumentException("Invalid group: `" + input + "`: Empty group");
                            }
                            throw new IllegalArgumentException("Invalid group: `" + andGroup + "`: Empty group");
                        }
                        throw new IllegalArgumentException("Invalid group: `" + xorGroup + "`: Empty group");
                    }
                    char char0 = elem.charAt(0);

                    if (char0 == '#') {
                        elem = elem.substring(1);
                        Predicate<T> filter = parsePredicate.apply(elem);
                        orResults.add(new ParseResult<>(elem, List.of(filter), new AtomicBoolean()));
                    } else if (char0 == '(') {
                        if (!elem.endsWith(")")) {
                            throw new IllegalArgumentException("Invalid group: `" + elem + "`: No end bracket found");
                        }
                        elem = elem.substring(1, elem.length() - 1);
                        ParseResult<T> result = parseTokens(elem, parseSet, parsePredicate);
                        orResults.add(result);
                    } else {
                        ParseResult<T> result = new ParseResult<>(elem, List.of(), new AtomicBoolean());
                        result.add(parseSet.apply(elem));
                        orResults.add(result);
                    }
                }

                if (orResults.size() == 1) {
                    xorResults.add(orResults.get(0));
                    continue;
                }
                int hasNonFilter = orResults.stream().mapToInt(result -> result.hadNonFilter.get() ? 1 : 0).sum();
                if (hasNonFilter > 0) {
                    if (hasNonFilter != orResults.size()) {
                        throw new IllegalArgumentException("Invalid group: `" + xorGroup + "`: Cannot OR filters and entries");
                    }
                    ParseResult<T> or = new ParseResult<>(xorGroup, List.of(), new AtomicBoolean(true));
                    for (ParseResult<T> addOr : orResults) {
                        addOr.combinePredicatesAndNumbers();
                        or.addResult(addOr);
                    }
                    xorResults.add(or);
                    continue;
                }
                List<Predicate<T>> predicates = orResults.stream().map(result -> result.predicates.get(0)).toList();
                xorResults.add(new ParseResult<T>(xorGroup, List.of(or(predicates)), new AtomicBoolean()));
            }

            if (xorResults.size() > 1) {
                for (ParseResult<T> result : xorResults) {
                    if (result.hadNonFilter.get()) {
                        throw new IllegalArgumentException("Invalid group: `" + andGroup + "`: Cannot mix filters and entries");
                    }
                    if (result.predicates.size() != 1) {
                        throw new IllegalArgumentException("Invalid group: `" + andGroup + "`: Must be resolved to 1 filter");
                    }
                }
                List<Predicate<T>> predicates = xorResults.stream().map(result -> result.predicates.get(0)).toList();
                andResults.add(new ParseResult<T>(andGroup, List.of(xor(predicates)), new AtomicBoolean()));
            } else {
                andResults.addAll(xorResults);
            }
        }

        if (andResults.size() == 1) {
            return andResults.get(0);
        }
        boolean hadNonFilter = false;
        for (ParseResult<T> result : andResults) {
            if (result.hadNonFilter.get()) {
                hadNonFilter = true;
                break;
            }
        }

        List<Predicate<T>> predicates = andResults.stream().flatMap(result -> result.predicates.stream()).toList();
        if (hadNonFilter) {
            ParseResult<T> result = new ParseResult<T>(input, new ArrayList<>(), new AtomicBoolean(true));
            for (ParseResult<T> andResult : andResults) {
                result.addResult(andResult);
            }
            result.combinePredicatesAndNumbers();
            return result;
        }
        ParseResult<T> result = new ParseResult<T>(input, List.of(and(predicates)), new AtomicBoolean());
        return result;
    }

    public static <T> Set<T> resolveQuery(String input, Function<String, Set<T>> parseSet, Function<String, Predicate<T>> parsePredicate) {
        ParseResult<T> result = parseQuery(input, parseSet, parsePredicate);
        return result.resolve();
    }

    public static <T> ParseResult<T> parseQuery(String input, Function<String, Set<T>> parseSet, Function<String, Predicate<T>> parsePredicate) {
        Map<String, Predicate<T>> parserCache = new Object2ObjectOpenHashMap<>();
        ParseResult<T> result = parseTokens(input, parseSet, new Function<String, Predicate<T>>() {
            @Override
            public Predicate<T> apply(String s) {
                Predicate<T> cachedPredicate = parserCache.get(s);
                if (cachedPredicate != null) {
                    return cachedPredicate;
                }
                Predicate<T> uncached = parsePredicate.apply(s);
                if (uncached == null) return null;
                Predicate<T> cached = new Predicate<T>() {
                    final Map<T, Boolean> cacheResult = new Object2BooleanOpenHashMap<>();
                    @Override
                    public boolean test(T t) {
                        Boolean cachedResult = cacheResult.get(t);
                        if (cachedResult != null) {
                            return cachedResult;
                        }
                        boolean result = uncached.test(t);
                        cacheResult.put(t, result);
                        return result;
                    }
                };
                parserCache.put(s, cached);
                return cached;
            };
        });
        return result;
    }
}