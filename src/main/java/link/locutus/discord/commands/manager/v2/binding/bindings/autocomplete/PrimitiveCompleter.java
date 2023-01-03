package link.locutus.discord.commands.manager.v2.binding.bindings.autocomplete;

import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.ArgChoice;
import link.locutus.discord.commands.manager.v2.binding.annotation.Filter;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.commands.manager.v2.binding.annotation.Autocomplete;
import link.locutus.discord.commands.manager.v2.binding.annotation.Autoparse;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
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
    public static List<String> String(ParameterData param, String input) {
        ArgChoice choice = param.getAnnotation(ArgChoice.class);
        if (choice != null) {
            String[] choices = choice.value();
            return StringMan.getClosest(input, Arrays.asList(choices), OptionData.MAX_CHOICES, true);
        }
        Filter filter = param.getAnnotation(Filter.class);
        if (filter != null) {
            if (!input.matches(filter.value())) {
                throw new IllegalArgumentException("Invalid match for " + filter.value());
            }
        }
        return null;
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

        List<String> colors = new ArrayList<>();

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
