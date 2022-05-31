package com.boydti.discord.commands.manager.v2.binding.bindings;

import com.boydti.discord.commands.manager.v2.binding.BindingHelper;
import com.boydti.discord.commands.manager.v2.binding.annotation.Binding;
import com.boydti.discord.commands.manager.v2.binding.annotation.Range;

import javax.validation.constraints.NotNull;

public class PrimitiveValidators extends BindingHelper {
    @Binding(types={Integer.class})
    @Range
    public static int intRange(int input, Range range) {
        if (input < range.min() || input > range.max()) {
            String argName = "number";
            throw new IllegalArgumentException("The " + argName + " (" + input + ") is not within the range [" + range.min() + "," + range.max() + "]");
        }
        return input;
    }

    @Binding(types={Double.class})
    @Range
    public static double doubleRange(double input, Range range) {
        if (input < range.min() || input > range.max()) {
            String argName = "number";
            throw new IllegalArgumentException("The " + argName + " (" + input + ") is not within the range [" + range.min() + "," + range.max() + "]");
        }
        return input;
    }

    @Binding(types={Long.class})
    @Range
    public static long longRange(long input, Range range) {
        if (input < range.min() || input > range.max()) {
            String argName = "number";
            throw new IllegalArgumentException("The " + argName + " (" + input + ") is not within the range [" + range.min() + "," + range.max() + "]");
        }
        return input;
    }
}