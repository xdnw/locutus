package link.locutus.discord.commands.manager.v2.binding.bindings;

import java.util.function.Predicate;

public enum MathOperation {
    LESSER_EQUAL("<="),
    GREATER_EQUAL(">="),
    NOT_EQUAL("!="),
    EQUAL("="),
    GREATER(">"),
    LESSER("<");

    public final String code;

    MathOperation(String s) {
        this.code = s;
    }

    public Predicate<Number> getNumberPredicate(Number compareTo) {
        double val2 = compareTo.doubleValue();
        return input -> {
            if (input == null) return MathOperation.this == MathOperation.NOT_EQUAL;
            double val1 = input.doubleValue();
            return switch (MathOperation.this) {
                case LESSER_EQUAL -> val1 <= val2;
                case GREATER_EQUAL -> val1 >= val2;
                case EQUAL -> val1 == val2;
                case GREATER -> val1 > val2;
                case LESSER -> val1 < val2;
                case NOT_EQUAL -> val1 != val2;
            };
        };
    }

    public Predicate<String> getStringPredicate(String compareTo) {
        return val1 -> {
            if (val1 == null) return MathOperation.this == MathOperation.NOT_EQUAL;
            return switch (MathOperation.this) {
                case LESSER_EQUAL -> val1.compareTo(compareTo) <= 0;
                case GREATER_EQUAL -> val1.compareTo(compareTo) >= 0;
                case EQUAL -> val1.equalsIgnoreCase(compareTo) || val1.matches(compareTo);
                case GREATER -> val1.compareTo(compareTo) > 0;
                case LESSER -> val1.compareTo(compareTo) < 0;
                case NOT_EQUAL -> !val1.equalsIgnoreCase(compareTo) && !val1.matches(compareTo);
            };
        };
    }

    public Predicate<Boolean> getBooleanPredicate(boolean compareTo) {
        return val1 -> {
            if (val1 == null) return MathOperation.this == MathOperation.NOT_EQUAL;
            return switch (MathOperation.this) {
                case LESSER_EQUAL -> !val1;
                case GREATER_EQUAL -> val1;
                case EQUAL -> val1 == compareTo;
                case GREATER -> val1 && !compareTo;
                case LESSER -> !val1 && compareTo;
                case NOT_EQUAL -> val1 != compareTo;
            };
        };
    }

    public MathOperation opposite() {
        return switch (this) {
            case LESSER_EQUAL -> GREATER;
            case GREATER_EQUAL -> LESSER;
            case EQUAL -> NOT_EQUAL;
            case GREATER -> LESSER_EQUAL;
            case LESSER -> GREATER_EQUAL;
            case NOT_EQUAL -> EQUAL;
        };
    }
}
