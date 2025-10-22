package link.locutus.discord.commands.manager.v2.binding.bindings.autocomplete;

import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.util.StringMan;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class PrimitiveCompleter extends BindingHelper {

    @Autoparse
    @Binding(types = String.class)
    public static List<String> String(ParameterData param, String input) {
        ArgChoice choice = param.getAnnotation(ArgChoice.class);
        if (choice != null) {
            String[] choices = choice.value();
            return StringMan.getClosest(input, Arrays.asList(choices), OptionData.MAX_CHOICES, true);
        }
        Filter filter = param.getAnnotation(Filter.class);
        if (filter != null) {
            if (!input.matches(filter.value())) {
                String errMsg = filter.desc();
                if (errMsg.isEmpty()) {
                    errMsg = "Invalid match for " + filter.value();
                }
                throw new IllegalArgumentException(errMsg);
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
    @Autocomplete
    @Binding(types = {Color.class})
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
