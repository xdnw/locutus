package com.boydti.discord.commands.manager.v2.binding.bindings.autocomplete;

import com.boydti.discord.commands.manager.v2.binding.BindingHelper;
import com.boydti.discord.commands.manager.v2.binding.annotation.Binding;
import com.boydti.discord.commands.manager.v2.binding.annotation.Filter;
import com.boydti.discord.commands.manager.v2.command.ParameterData;
import com.boydti.discord.commands.manager.v2.impl.pw.TaxRate;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.commands.manager.v2.binding.annotation.Autocomplete;
import com.boydti.discord.commands.manager.v2.binding.annotation.Autoparse;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PrimitiveCompleter extends BindingHelper {

    @Autoparse
    @Binding(types={
            UUID.class,
            TaxRate.class
    })
    public static void uuid(String input) {
    }

    @Autoparse
    @Binding(types=String.class)
    public static void String(ParameterData param, String input) {
        Filter filter = param.getAnnotation(Filter.class);
        if (filter == null) return;
        if (!input.matches(filter.value())) {
            throw new IllegalArgumentException("Invalid match for " + filter.value());
        }
    }

//    @Autocomplete
//    @Binding(types= TaxRate.class)
//    public static void taxRate(String input) {
//        if (!input.matches(filter.value())) {
//            throw new IllegalArgumentException("Invalid match for " + filter.value());
//        }
//    }

//
//    @Autocomplete
//    @Binding(types={double.class, Double.class})
//    public static Map.Entry<String, String> Double(String input) {
//        if (input.isEmpty()) return null;
//        try {
//            Double.parseDouble(input);
//            return null;
//        } catch (NumberFormatException ignore) {}
//        double value = PrimitiveBindings.Number(input).doubleValue();
//        return new AbstractMap.SimpleEntry(input, value + "");
//    }
//
//    @Autocomplete
//    @Binding(types={int.class, Integer.class})
//    public static Map.Entry<String, String> Integer(String input) {
//        if (input.isEmpty()) return null;
//        try {
//            Integer.parseInt(input);
//            return null;
//        } catch (NumberFormatException ignore) {}
//        int value = PrimitiveBindings.Number(input).intValue();
//        return new AbstractMap.SimpleEntry(input, value + "");
//    }
//
//    @Autocomplete
//    @Binding(types={long.class, Long.class})
//    public static Map.Entry<String, String> Long(String input) {
//        if (input.isEmpty()) return null;
//        try {
//            Long.parseLong(input);
//            return null;
//        } catch (NumberFormatException ignore) {}
//        long value = PrimitiveBindings.Number(input).longValue();
//        return new AbstractMap.SimpleEntry(input, value + "");
//    }
//
//    @Autocomplete
//    @Binding(types={Number.class})
//    public static Map.Entry<String, String> Number(String input) {
//        if (input.isEmpty()) return null;
//        try {
//            Double.parseDouble(input);
//            return null;
//        } catch (NumberFormatException ignore) {}
//        double value = PrimitiveBindings.Number(input).doubleValue();
//        return new AbstractMap.SimpleEntry(input, value + "");
//    }

    @Autocomplete
    @Binding(types={Color.class})
    public static List<String> color(String input) {
        if (input.isEmpty()) return null;

        List<String> colors = new LinkedList<>();

        List<Map.Entry<String, Double>> distances = new ArrayList<>();

        for (Field field : Color.class.getDeclaredFields()) {
            if (field.getType() == Color.class) {
                String name = field.getName();
                if (!Character.isUpperCase(name.charAt(0))) continue;
                colors.add(name);
            }
        }

        return StringMan.getClosest(input, colors, OptionData.MAX_CHOICES, true);
    }
}
