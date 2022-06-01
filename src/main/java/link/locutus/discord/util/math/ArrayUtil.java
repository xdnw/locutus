package link.locutus.discord.util.math;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.Supplier;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class ArrayUtil {
    public static final DoubleBinaryOperator DOUBLE_ADD = (x, y) -> x + y;
    public static final DoubleBinaryOperator DOUBLE_SUBTRACT = (x, y) -> x - y;
    public static final IntBinaryOperator INT_ADD = (x, y) -> x + y;

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
}