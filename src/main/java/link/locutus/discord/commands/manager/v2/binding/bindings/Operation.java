package link.locutus.discord.commands.manager.v2.binding.bindings;

import java.util.function.Predicate;

public enum Operation {
    LESSER_EQUAL("<="),
    GREATER_EQUAL(">="),
    NOT_EQUAL("!="),
    EQUAL("="),
    GREATER(">"),
    LESSER("<")
    ;

    public final String code;

    Operation(String s) {
        this.code = s;
    }

    public Predicate<Number> getNumberPredicate(Number compareTo) {
        double val2 = compareTo.doubleValue();
        return input -> {
            if (input == null) return Operation.this == Operation.NOT_EQUAL;
            double val1 = input.doubleValue();
            switch (Operation.this) {
                case LESSER_EQUAL: return val1 <= val2;
                case GREATER_EQUAL: return val1 >= val2;
                case EQUAL: return val1 == val2;
                case GREATER: return val1 > val2;
                case LESSER: return val1 < val2;
                case NOT_EQUAL: return val1 != val2;
            }
            return false;
        };
    }

    public Predicate<String> getStringPredicate(String compareTo) {
        String val2 = compareTo;
        return val1 -> {
            if (val1 == null) return Operation.this == Operation.NOT_EQUAL;
            switch (Operation.this) {
                case LESSER_EQUAL: return val1.compareTo(val2) <= 0;
                case GREATER_EQUAL: return val1.compareTo(val2) >= 0;
                case EQUAL: return val1.equalsIgnoreCase(val2);
                case GREATER: return val1.compareTo(val2) > 0;
                case LESSER: return val1.compareTo(val2) < 0;
                case NOT_EQUAL: return !val1.equalsIgnoreCase(val2);
            }
            return false;
        };
    }

    public Predicate<Boolean> getBooleanPredicate(boolean compareTo) {
        boolean val2 = compareTo;
        return val1 -> {
            if (val1 == null) return Operation.this == Operation.NOT_EQUAL;
            switch (Operation.this) {
                case LESSER_EQUAL: return !val1;
                case GREATER_EQUAL: return val1;
                case EQUAL: return val1 == val2;
                case GREATER: return val1 && !val2;
                case LESSER: return !val1 && val2;
                case NOT_EQUAL: return val1 != val2;
            }
            return false;
        };
    }
}
