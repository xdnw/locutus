package link.locutus.discord.util.math;

import it.unimi.dsi.fastutil.doubles.Double2DoubleFunction;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.util.MathMan;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Function;

public class LazyMathEntity<T> implements ArrayUtil.MathToken<LazyMathEntity<T>> {

    private final Object resolved2;
    private final Function<T, Object> resolver3;
    private final boolean returnError;
    private final Function<T, String> getInput;

    public LazyMathEntity(Object resolved) {
        this.resolved2 = resolved;
        this.resolver3 = null;
        this.returnError = false;
        this.getInput = f -> String.valueOf(resolved);
    }

    @Override
    public String toString() {
        return getInput.apply(null);
    }

    public LazyMathEntity(String input, Function<String, Function<T, Object>> parser, boolean returnError) {
        this((Function<T, Object>) parser.apply(input), returnError, f -> input);
    }

    private LazyMathEntity(Function<T, Object> resolver, boolean returnError, Function<T, String> getInput) {
        this.returnError = returnError;
        if (resolver instanceof TypedFunction typed && typed.isResolved()) {
            this.resolved2 = typed.get(null);
            this.resolver3 = null;
        } else {
            this.resolved2 = null;
            this.resolver3 = resolver;
        }
        this.getInput = f -> {
            if (resolver3 != null) {
                try {
                    return String.valueOf(resolver.apply(f));
                } catch (RuntimeException e) {
                }
            } else {
                return String.valueOf(resolved2);
            }
            return getInput.apply(f);
        };
    }

    public boolean isResolved() {
        return resolver3 == null;
    }

    public Object resolve(T input) {
        if (resolver3 == null) return resolved2;
        try {
            Object result = resolver3.apply(input);
            return result;
        } catch (RuntimeException e) {
            return getInput.apply(input) + (returnError ? ": " + e.getMessage() : "");
        }
    }

    public Object getOrNull() {
        return resolved2;
    }

    private Object resolveNumber(Object obj, Object other) {
        return resolveNumber(obj, other, false);
    }

    private Object resolveNumber(Object obj, Object other, boolean enumAsNumber) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Number) {
            return (Number) obj;
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj ? 1 : 0;
        }
        if (enumAsNumber && obj instanceof Enum) {
            return ((Enum<?>) obj).ordinal();
        }
        try {
            Double value = MathMan.parseDouble(String.valueOf(obj));
            if (value != null) return value;
        } catch (NullPointerException | IllegalArgumentException e) {}
        return obj;
    }



    private Object add(Object a, Object b) {
        a = resolveNumber(a, b);
        b = resolveNumber(b, a);
        // String
        if (a instanceof String || b instanceof String) {
            return String.valueOf(a) + String.valueOf(b);
        }
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).doubleValue() + ((Number) b).doubleValue();
        }
        // Enum
        // Map
        if (a instanceof Map) {
            return mathMap((Map<?, ?>) a, b, Double::sum, f -> f, f -> f);
        } else if (b instanceof Map) {
            return mathMap((Map<?, ?>) b, a, Double::sum, f -> f, f -> f);
        }
        if (a instanceof double[]) {
            return mathDoubleArray((double[]) a, b, Double::sum);
        } else if (b instanceof double[]) {
            return mathDoubleArray((double[]) b, a, Double::sum);
        }
        // List
        // Set
        throw new IllegalArgumentException("Cannot add " + a.getClass() + " to " + b.getClass());
    }

    private Object power(Object a, Object b) {
        a = resolveNumber(a, b);
        b = resolveNumber(b, a);
        // Number
        if (a instanceof Number && b instanceof Number) {
            return Math.pow(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        // String
        // Enum
        // Map
        if (a instanceof Map) {
            return mathMap((Map<?, ?>) a, b, Math::pow, f -> 1, null);
        } else if (b instanceof Map) {
            return mathMap((Map<?, ?>) b, a, (y, x) -> Math.pow(x, y), null, f -> 1);
        }
        // double[]
        if (a instanceof double[]) {
            return mathDoubleArray((double[]) a, b, Math::pow);
        } else if (b instanceof double[]) {
            return mathDoubleArray((double[]) b, a, Math::pow);
        }
        // List
        // Set
        throw new IllegalArgumentException("Cannot raise " + a.getClass() + " to the power of " + b.getClass());
    }

    private Object multiply(Object a, Object b) {
        a = resolveNumber(a, b);
        b = resolveNumber(b, a);
        // Number
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).doubleValue() * ((Number) b).doubleValue();
        }
        // String
        // Enum
        // Map
        if (a instanceof Map) {
            return mathMap((Map) a, b, (x, y) -> x * y, null, null);
        } else if (b instanceof Map) {
            return mathMap((Map) b, a, (y, x) -> x * y, null, null);
        }
        // double[]
        if (a instanceof double[]) {
            return mathDoubleArray((double[]) a, b, (x, y) -> x * y);
        } else if (b instanceof double[]) {
            return mathDoubleArray((double[]) b, a, (x, y) -> x * y);
        }
        // List
        // Set
        throw new IllegalArgumentException("Cannot multiply " + a.getClass() + " by " + b.getClass());
    }

    private Object divide(Object a, Object b) {
        a = resolveNumber(a, b);
        b = resolveNumber(b, a);
        // Number
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).doubleValue() / ((Number) b).doubleValue();
        }
        // String
        // Enum
        // Map
        if (a instanceof Map) {
            return mathMap((Map) a, b, (x, y) -> x / y, null, null);
        } else if (b instanceof Map) {
            return mathMap((Map) b, a, (y, x) -> x / y, null, null);
        }
        // double[]
        if (a instanceof double[]) {
            return mathDoubleArray((double[]) a, b, (x, y) -> x / y);
        } else if (b instanceof double[]) {
            return mathDoubleArray((double[]) b, a, (x, y) -> x / y);
        }
        // List
        // Set
        return null;
    }

    private Number cast(double value, Number type) {
        if (type instanceof Integer) {
            return (int) value;
        } else if (type instanceof Long) {
            return (long) value;
        } else if (type instanceof Float) {
            return (float) value;
        } else if (type instanceof Short) {
            return (short) value;
        } else if (type instanceof Byte) {
            return (byte) value;
        } else {
            return value;
        }
    }

    private Map<?, ?> mathMap(Map a, Object b, DoubleBinaryOperator operator, Double2DoubleFunction includeA, Double2DoubleFunction includeB) {
        if (b instanceof Number) {
            Map<?, Number> copy = new LinkedHashMap<>(a);
            for (Map.Entry<?, Number> entry : copy.entrySet()) {
                Number aValue = entry.getValue();
                Number bValue = (Number) b;
                entry.setValue(cast(operator.applyAsDouble(aValue.doubleValue(), bValue.doubleValue()), aValue));
            }
            return copy;
        } else if (b instanceof Map<?, ?> bMap) {
            Map<Object, Number> result = new HashMap<>();
            for (Object key : a.keySet()) {
                if (bMap.containsKey(key)) {
                    Number aValue = (Number) a.get(key);
                    Number bValue = (Number) bMap.get(key);
                    result.put(key, cast(operator.applyAsDouble(aValue.doubleValue(), bValue.doubleValue()), bValue));
                } else if (includeA != null) {
                    Number aValue = (Number) a.get(key);
                    result.put(key, cast(includeA.applyAsDouble(aValue.doubleValue()), aValue));
                }
            }
            if (includeB != null) {
                for (Object key : bMap.keySet()) {
                    if (!a.containsKey(key)) {
                        Number bValue = (Number) bMap.get(key);
                        result.put(key, cast(includeB.applyAsDouble(bValue.doubleValue()), bValue));
                    }
                }
            }
            return result;
        } else {
            throw new IllegalArgumentException("Cannot combine " + b.getClass() + " with " + a.getClass());
        }
    }

    private double[] mathDoubleArray(double[] a, Object b, DoubleBinaryOperator operator) {
        if (b instanceof Number) {
            double[] result = new double[a.length];
            double bValue = ((Number) b).doubleValue();
            for (int i = 0; i < a.length; i++) {
                result[i] = operator.applyAsDouble(a[i], bValue);
            }
            return result;
        } else if (b instanceof double[]) {
            double[] bArray = (double[]) b;
            if (a.length != bArray.length) {
                throw new IllegalArgumentException("Cannot combine arrays of different lengths");
            }
            double[] result = new double[a.length];
            for (int i = 0; i < a.length; i++) {
                result[i] = operator.applyAsDouble(a[i], bArray[i]);
            }
            return result;
        } else {
            throw new IllegalArgumentException("Cannot combine " + b.getClass() + " with a double array");
        }
    }

    private Object subtract(Object a, Object b) {
        a = resolveNumber(a, b);
        b = resolveNumber(b, a);
        // Number
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).doubleValue() - ((Number) b).doubleValue();
        }
        // String
        // Enum
        // Map
        if (a instanceof Map) {
            return mathMap((Map) a, b, (x, y) -> x - y, f -> f, f -> -f);
        } else if (b instanceof Map) {
            return mathMap((Map) b, a, (y, x) -> x - y, f -> -f, f -> f);
        }
        // double[]
        if (a instanceof double[]) {
            return mathDoubleArray((double[]) a, b, (x, y) -> x - y);
        } else if (b instanceof double[]) {
            return mathDoubleArray((double[]) b, a, (x, y) -> x - y);
        }
        // List
        // Set
        throw new IllegalArgumentException("Cannot subtract " + b.getClass() + " with " + a.getClass());
    }

    private Object modulo(Object a, Object b) {
        a = resolveNumber(a, b);
        b = resolveNumber(b, a);
        // Number
        // String
        // Enum
        // Map
        if (a instanceof Map) {
            return mathMap((Map) a, b, (x, y) -> x % y, null, null);
        } else if (b instanceof Map) {
            return mathMap((Map) b, a, (y, x) -> x % y, null, null);
        }
        // double[]
        if (a instanceof double[]) {
            return mathDoubleArray((double[]) a, b, (x, y) -> x % y);
        } else if (b instanceof double[]) {
            return mathDoubleArray((double[]) b, a, (x, y) -> x % y);
        }
        // List
        // Set
        throw new IllegalArgumentException("Cannot modulo " + b.getClass() + " with " + a.getClass());
    }

    private Object greaterEqual(Object a, Object b) {
        a = resolveNumber(a, b, true);
        b = resolveNumber(b, a, true);
        // Number
        if (a instanceof Number && b instanceof Number) {
            if (((Number) a).doubleValue() >= ((Number) b).doubleValue()) {
                return 1;
            }
            return 0;
        }
        // String
        // Enum - handled above
        // Map
        // List
        // Set
        throw new IllegalArgumentException("Cannot >= " + b.getClass() + " with " + a.getClass());
    }

    private Object greater(Object a, Object b) {
        a = resolveNumber(a, b, true);
        b = resolveNumber(b, a, true);
        // Number
        if (a instanceof Number && b instanceof Number) {
            if (((Number) a).doubleValue() > ((Number) b).doubleValue()) {
                return 1;
            }
            return 0;
        }
        // String
        // Enum
        // Map
        // List
        // Set
        throw new IllegalArgumentException("Cannot > " + b.getClass() + " with " + a.getClass());
    }

    private Object lessEqual(Object a, Object b) {
        a = resolveNumber(a, b, true);
        b = resolveNumber(b, a, true);
        // Number
        if (a instanceof Number && b instanceof Number) {
            if (((Number) a).doubleValue() <= ((Number) b).doubleValue()) {
                return 1;
            }
            return 0;
        }
        // String
        // Enum
        // Map
        // List
        // Set
        throw new IllegalArgumentException("Cannot <= " + b.getClass() + " with " + a.getClass());
    }

    private Object less(Object a, Object b) {
        a = resolveNumber(a, b, true);
        b = resolveNumber(b, a, true);
        // Number
        if (a instanceof Number && b instanceof Number) {
            if (((Number) a).doubleValue() < ((Number) b).doubleValue()) {
                return 1;
            }
            return 0;
        }
        // String
        // Enum
        // Map
        // List
        // Set
        throw new IllegalArgumentException("Cannot < " + b.getClass() + " with " + a.getClass());
    }

    private boolean emumEqual(Enum a, Object b) {
        // check is alphanumeric underscore
        if (b instanceof String bStr && bStr.matches("[a-zA-Z0-9_]+")) {
            b = BindingHelper.emum((Class) a.getClass(), (String) b);
        }
        if (b instanceof Enum) {
            return (a.ordinal() == ((Enum<?>) b).ordinal());
        } else if (b instanceof String) {
            // matches regex
            return (a.name().matches((String) b));
        } else if (b instanceof Number) {
            return a.ordinal() == ((Number) b).intValue();
        }
        throw new IllegalArgumentException("Cannot compare " + a.getClass() + " with " + b.getClass());
    }

    private boolean stringEqual(String a, Object b) {
        if (b instanceof String) {
            return a.matches((String) b);
        } else if (b instanceof Enum) {
            return emumEqual((Enum) b, a);
        } else {
            return a.equals(String.valueOf(b));
        }
    }

    private Object notEqual(Object a, Object b) {
        a = resolveNumber(a, b);
        b = resolveNumber(b, a);
        if (a instanceof Number && b instanceof Number) {
            if (Math.round(((Number) a).doubleValue() * 100) == Math.round(((Number) b).doubleValue() * 100)) {
                return 0;
            }
            return 1;
        }
        // String
        if (a instanceof String aStr) {
            return !stringEqual(aStr, b) ? 1 : 0;
        } else if (b instanceof String bStr) {
            return !stringEqual(bStr, a) ? 1 : 0;
        }
        // Enum
        if (a instanceof Enum) {
            return !emumEqual((Enum) a, b) ? 1 : 0;
        } else if (b instanceof Enum) {
            return !emumEqual((Enum) b, a) ? 1 : 0;
        }
        // Map
        if (a instanceof Map) {
            return !a.equals(b) ? 1 : 0;
        } else if (b instanceof Map) {
            return !b.equals(a) ? 1 : 0;
        }
        // double[]
        if (a instanceof double[] aArr && b instanceof double[] bArr) {
            return arrEquals(aArr, bArr) ? 1 : 0;
        }
        // List
        // Set
        return null;
    }
    private boolean arrEquals(double[] a, double[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            double aVal = a[i];
            double bVal = b[i];
            if (Math.round(aVal * 100) != Math.round(bVal * 100)) {
                return false;
            }
        }
        return true;
    }

    private Object equal(Object a, Object b) {
        a = resolveNumber(a, b);
        b = resolveNumber(b, a);
        // Number
        if (a instanceof Number && b instanceof Number) {
            if (Math.round(((Number) a).doubleValue() * 100) == Math.round(((Number) b).doubleValue() * 100)) {
                return 1;
            }
            return 0;
        }
        // String
        if (a instanceof String aStr) {
            return stringEqual(aStr, b) ? 1 : 0;
        } else if (b instanceof String bStr) {
            return stringEqual(bStr, a) ? 1 : 0;
        }
        // Enum
        if (a instanceof Enum) {
            return emumEqual((Enum) a, b) ? 1 : 0;
        } else if (b instanceof Enum) {
            return emumEqual((Enum) b, a) ? 1 : 0;
        }
        // Map
        if (a instanceof Map) {
            return a.equals(b) ? 1 : 0;
        } else if (b instanceof Map) {
            return b.equals(a) ? 1 : 0;
        }
        // double[]
        if (a instanceof double[] aArr && b instanceof double[] bArr) {
            return arrEquals(aArr, bArr) ? 1 : 0;
        }
        // List
        // Set
        return null;
    }

    private Object ternary(Object condition, Object a, Object b) {
        condition = resolveNumber(condition, condition);
        a = resolveNumber(a, condition);
        b = resolveNumber(b, condition);
        // Number
        if (condition instanceof Number) {
            if (((Number) condition).doubleValue() > 0) {
                return a;
            }
            return b;
        }
        throw new IllegalArgumentException("Cannot use ternary with " + condition.getClass());
        // String
        // Enum
        // Map
        // List
        // Set
    }

    private Function<T, String> combine(LazyMathEntity<T> other, String operator) {
        return f -> getInput.apply(f) + operator + other.getInput.apply(f);
    }

    private Function<T, String> combine(double value, String operator) {
        return f -> getInput.apply(f) + operator + value;
    }

    @Override
    public LazyMathEntity<T> add(LazyMathEntity<T> other) {
        try{
            if (this.resolved2 != null && other.resolved2 != null) {
                return new LazyMathEntity<>(add(this.resolved2, other.resolved2));
            }
        } catch (RuntimeException e) {}
        Function<T, String> fallback = combine(other, "+");
        return new LazyMathEntity<>(fallback(t -> add(this.resolve(t), other.resolve(t)), fallback), returnError, fallback);
    }

    @Override
    public LazyMathEntity<T> power(double value) {
        try {
        if (this.resolved2 != null) {
            return new LazyMathEntity<>(power(this.resolved2, value));
        }
        } catch (RuntimeException e) {}
        Function<T, String> fallback = combine(value, "^");
        return new LazyMathEntity<>(fallback(t -> power(this.resolve(t), value), fallback), returnError, fallback);
    }

    @Override
    public LazyMathEntity<T> power(LazyMathEntity<T> other) {
        try{
        if (this.resolved2 != null && other.resolved2 != null) {
            return new LazyMathEntity<>(power(this.resolved2, other.resolved2));
        }
        } catch (RuntimeException e) {}
        Function<T, String> fallback = combine(other, "^");
        return new LazyMathEntity<>(fallback(t -> power(this.resolve(t), other.resolve(t)), fallback), returnError, fallback);
    }

    @Override
    public LazyMathEntity<T> multiply(double value) {
        try {
        if (this.resolved2 != null) {
            return new LazyMathEntity<>(multiply(this.resolved2, value));
        }
        } catch (RuntimeException e) {}
        Function<T, String> fallback = combine(value, "*");
        return new LazyMathEntity<>(fallback(t -> multiply(this.resolve(t), value), fallback), returnError, fallback);
    }

    @Override
    public LazyMathEntity<T> divide(double value) {
        try {
        if (this.resolved2 != null) {
            return new LazyMathEntity<>(divide(this.resolved2, value));
        }
        } catch (RuntimeException e) {}
        Function<T, String> fallback = combine(value, "/");
        return new LazyMathEntity<>(fallback(t -> divide(this.resolve(t), value), fallback), returnError, fallback);
    }

    @Override
    public LazyMathEntity<T> subtract(LazyMathEntity<T> other) {
        try{
        if (this.resolved2 != null && other.resolved2 != null) {
            return new LazyMathEntity<>(subtract(this.resolved2, other.resolved2));
        }
        } catch (RuntimeException e) {}
        Function<T, String> fallback = combine(other, "-");
        return new LazyMathEntity<>(fallback(t -> subtract(this.resolve(t), other.resolve(t)), fallback), returnError, fallback);
    }

    @Override
    public LazyMathEntity<T> multiply(LazyMathEntity<T> other) {
        try{
        if (this.resolved2 != null && other.resolved2 != null) {
            return new LazyMathEntity<>(multiply(this.resolved2, other.resolved2));
        }
        } catch (RuntimeException e) {}
        Function<T, String> fallback = combine(other, "*");
        return new LazyMathEntity<>(fallback(t -> multiply(this.resolve(t), other.resolve(t)), fallback), returnError, fallback);
    }

    @Override
    public LazyMathEntity<T> divide(LazyMathEntity<T> other) {
        try{
        if (this.resolved2 != null && other.resolved2 != null) {
            return new LazyMathEntity<>(divide(this.resolved2, other.resolved2));
        }
        } catch (RuntimeException e) {}
        Function<T, String> fallback = combine(other, "/");
        return new LazyMathEntity<>(fallback(t -> divide(this.resolve(t), other.resolve(t)), fallback), returnError, fallback);
    }

    @Override
    public LazyMathEntity<T> modulo(double value) {
        try {
        if (this.resolved2 != null) {
            return new LazyMathEntity<>(modulo(this.resolved2, value));
        }
        } catch (RuntimeException e) {}
        Function<T, String> fallback = combine(value, "%");
        return new LazyMathEntity<>(fallback(t -> modulo(this.resolve(t), value), fallback), returnError, fallback);
    }

    @Override
    public LazyMathEntity<T> modulo(LazyMathEntity<T> value) {
        try {
        if (this.resolved2 != null && value.resolved2 != null) {
            return new LazyMathEntity<>(modulo(this.resolved2, value.resolved2));
        }
        } catch (RuntimeException e) {}
        Function<T, String> fallback = combine(value, "%");
        return new LazyMathEntity<>(fallback(t -> modulo(this.resolve(t), value.resolve(t)), fallback), returnError, fallback);
    }

    @Override
    public LazyMathEntity<T> greaterEqual(LazyMathEntity<T> other) {
        try{
        if (this.resolved2 != null && other.resolved2 != null) {
            return new LazyMathEntity<>(greaterEqual(this.resolved2, other.resolved2));
        }
        } catch (RuntimeException e) {}
        Function<T, String> fallback = combine(other, ">=");
        return new LazyMathEntity<>(fallback(t -> greaterEqual(this.resolve(t), other.resolve(t)), fallback), returnError, fallback);
    }

    @Override
    public LazyMathEntity<T> greater(LazyMathEntity<T> other) {
        try{
        if (this.resolved2 != null && other.resolved2 != null) {
            return new LazyMathEntity<>(greater(this.resolved2, other.resolved2));
        }
        } catch (RuntimeException e) {}
        Function<T, String> fallback = combine(other, ">");
        return new LazyMathEntity<>(fallback(t -> greater(this.resolve(t), other.resolve(t)), fallback), returnError, fallback);
    }

    @Override
    public LazyMathEntity<T> lessEqual(LazyMathEntity<T> other) {
        try{
        if (this.resolved2 != null && other.resolved2 != null) {
            return new LazyMathEntity<>(lessEqual(this.resolved2, other.resolved2));
        }
        } catch (RuntimeException e) {}
        Function<T, String> fallback = combine(other, "<=");
        return new LazyMathEntity<>(fallback(t -> lessEqual(this.resolve(t), other.resolve(t)), fallback), returnError, fallback);
    }

    @Override
    public LazyMathEntity<T> less(LazyMathEntity<T> other) {
        try{
        if (this.resolved2 != null && other.resolved2 != null) {
            return new LazyMathEntity<>(less(this.resolved2, other.resolved2));
        }
        } catch (RuntimeException e) {}
        Function<T, String> fallback = combine(other, "<");
        return new LazyMathEntity<>(fallback(t -> less(this.resolve(t), other.resolve(t)), fallback), returnError, fallback);
    }

    @Override
    public LazyMathEntity<T> notEqual(LazyMathEntity<T> other) {
        try{
        if (this.resolved2 != null && other.resolved2 != null) {
            return new LazyMathEntity<>(notEqual(this.resolved2, other.resolved2));
        }
        } catch (RuntimeException e) {}
        Function<T, String> fallback = combine(other, "!=");
        return new LazyMathEntity<>(fallback(t -> notEqual(this.resolve(t), other.resolve(t)), fallback), returnError, fallback);
    }

    @Override
    public LazyMathEntity<T> equal(LazyMathEntity<T> other) {
        try{
        if (this.resolved2 != null && other.resolved2 != null) {
            return new LazyMathEntity<>(equal(this.resolved2, other.resolved2));
        }
        } catch (RuntimeException e) {}
        Function<T, String> fallback = combine(other, "=");
        return new LazyMathEntity<>(fallback(t -> equal(this.resolve(t), other.resolve(t)), fallback), returnError, fallback);
    }

    @Override
    public LazyMathEntity<T> ternary(LazyMathEntity<T> a, LazyMathEntity<T> b) {
        try {
            if (this.resolved2 != null && a.resolved2 != null && b.resolved2 != null) {
                return new LazyMathEntity<>(ternary(this.resolved2, a.resolved2, b.resolved2));
            }
        } catch (RuntimeException e) {}
        Function<T, String> fallback = f -> getInput.apply(f) + "?" + a.getInput.apply(f) + ":" + b.getInput.apply(f);
        return new LazyMathEntity<>(fallback(t -> ternary(this.resolve(t), a.resolve(t), b.resolve(t)), fallback), returnError, fallback);
    }

    public Function<T, Object> fallback(Function<T, Object> child, Function<T, String> fallbackFunc) {
        return t -> {
            try {
                return child.apply(t);
            } catch (RuntimeException e) {
                return fallbackFunc.apply(t);
            }
        };
    }
}