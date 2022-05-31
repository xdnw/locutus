package com.boydti.discord.commands.manager.v2.binding.bindings;

import com.boydti.discord.commands.manager.v2.binding.annotation.Binding;
import com.boydti.discord.commands.manager.v2.binding.BindingHelper;
import com.boydti.discord.commands.manager.v2.binding.annotation.Filter;
import com.boydti.discord.commands.manager.v2.binding.annotation.Timediff;
import com.boydti.discord.commands.manager.v2.binding.annotation.Timestamp;
import com.boydti.discord.commands.manager.v2.command.ArgumentStack;
import com.boydti.discord.commands.manager.v2.command.ParameterData;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.ScriptUtil;
import com.boydti.discord.util.TimeUtil;

import java.awt.Color;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PrimitiveBindings extends BindingHelper {

//    @Binding(examples = {"true", "false"}, types = {boolean.class})
//    public List List(ParameterData param, String argument) {
//        Type t = ((ParameterizedType) param.getType()).getActualTypeArguments()[0];
//        String[] split = argument.split(",");
//        List list = new ArrayList<>();
//        return null;
//    }
    @Binding
    public ArgumentStack stack() {
        throw new IllegalStateException("No ArgumentStack set in command locals");
    }

    @Binding
    public List<String> all(String string, ArgumentStack stack) {
        List<String> result = new ArrayList<>();
        result.add(string);
        while (stack.hasNext()) {
            result.add(stack.consumeNext());
        }
        return result;
    }

    /**
     * Gets a type from a {@link Binding}.
     *
     * @param argument the context
     * @return the requested type
     * @throws IllegalArgumentException on error
     */
    @Binding(examples = {"true", "false"}, types = {boolean.class})
    public static Boolean Boolean(String argument) {
        switch (argument.toLowerCase(Locale.ROOT)) {
            case "":
                return null;
            case "true":
            case "yes":
            case "on":
            case "y":
            case "1":
            case "t":
                return true;
            case "false":
            case "no":
            case "off":
            case "f":
            case "n":
            case "0":
                return false;
            default:
                throw new IllegalArgumentException("Invalid boolean " + argument);
        }
    }

    @Binding(examples = "hello")
    public static String String(ParameterData param, String input) {
        Filter annotation = param.getAnnotation(Filter.class);
        if (annotation != null && !input.matches(annotation.value())) {
            throw new IllegalArgumentException("Input: `" + input + "` does not match: `" + annotation.value() + "`");
        }

        return input;
    }

    /**
     * Try to parse numeric input as either a number or a mathematical expression.
     *
     * @param input input
     * @return a number
     * @throws IllegalArgumentException thrown on parse error
     */
    @Binding(types={double.class}, examples = {"3.0"})
    public static Double Double(String input) {
        return Number(input).doubleValue();
    }

    @Binding(types={int.class}, examples = {"3"})
    public static Integer Integer(String input) {
        return Number(input).intValue();
    }

    @Binding(examples = {"#420420"})
    public static Color color(String input) {
        if (input.charAt(0) == '#') return Color.decode(input);
        try {
            return (Color)Color.class.getField(input.toUpperCase()).get(null);
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
            throw new IllegalArgumentException("Invalid color: `" + input + "`");
        }
    }

    @Binding(types={long.class}, examples = {"3k"})
    public static Long Long(String input) {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException ignore) {}
        return Number(input).longValue();
    }

    @Binding(examples = {"3.0"})
    public static Number Number(String input) {
        try {
            Double parsed = MathMan.parseDouble(input);
            if (parsed != null) return parsed;
        } catch (NumberFormatException e1) {}
        try {
            return (Number) ScriptUtil.getEngine().eval(input);
        } catch (Throwable e) {
            throw new IllegalArgumentException(String.format(
                    "Expected '%s' to be a number or valid math expression (error: %s)", input, e.getMessage()));
        }
    }

    @Binding(examples = {"8-4-4-4-12"})
    public UUID uuid(String argument) {
        return UUID.fromString(argument);
    }

    @Timediff
    @Binding(types={long.class}, examples = {"5d", "10h3m25s"})
    public Long timediff(String argument) {
        return TimeUtil.timeToSec(argument) * 1000;
    }

    @Timestamp
    @Binding(types={long.class}, examples = {"5d", "10h3m25s", "dd/MM/yyyy"})
    public Long timestamp(String argument) throws ParseException {
        if (argument.equalsIgnoreCase("%epoch%")) {
            return 0L;
        }
        if (argument.contains("/")) {
            long time = 0;
            long date = 0;
            String[] split = argument.split("/");
            if (split.length == 3) {
                if (split[2].length() == 2) {
                    return TimeUtil.parseDate(TimeUtil.DD_MM_YY, argument, false);
                } else {
                    return TimeUtil.parseDate(TimeUtil.DD_MM_YYYY, argument, false);
                }
            } else {
                throw new IllegalArgumentException("Invalid time format: " + argument);
            }
        }
        if (argument.length() == 10 && argument.charAt(4) == '-' && argument.charAt(7) == '-') {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            Date parsed = format.parse(argument);
            return parsed.getTime();
        }
        return System.currentTimeMillis() - TimeUtil.timeToSec(argument) * 1000;
    }
}
