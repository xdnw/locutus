package link.locutus.discord.web.commands.binding;

import com.google.gson.JsonArray;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.util.scheduler.QuadConsumer;
import link.locutus.discord.web.WebUtil;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public class WebBindingHelper extends BindingHelper {
    public <T> String multipleSelect(ParameterData param, Collection<T> objects, Function<T, Map.Entry<String, String>> toNameValue) {
        return multipleSelect(param, objects, toNameValue, false);
    }

    public <T> String multipleSelectEmum(Class<T> emum, ValueStore valueStore) {
        ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
        List<T> options = Arrays.asList(emum.getEnumConstants());
        return multipleSelect(param, options, t -> new AbstractMap.SimpleEntry<>(t.toString(), t.toString()), true);
    }

    public <T> String multipleSelect(ParameterData param, Collection<T> objects, Function<T, Map.Entry<String, String>> toNameValue, boolean multiple) {
        if (true) {
            return WebUtil.generateSearchableDropdown(param, objects, new QuadConsumer<T, JsonArray, JsonArray, JsonArray>() {
                @Override
                public void consume(T obj, JsonArray names, JsonArray values, JsonArray subtext) {
                    Map.Entry<String, String> pair = toNameValue.apply(obj);
                    names.add(pair.getKey());
                    values.add(pair.getValue());
                }
            }, multiple);
        }

        UUID uuid = UUID.randomUUID();

        String def = param.getDefaultValueString();
        String valueStr = def != null ? " value=\"" + def + "\"" : "";
        StringBuilder response = new StringBuilder("<select id=\"" + uuid + "\" class=\"form-control form-control-sm\" name=\"" + param.getName() + "\" " + valueStr + " " + (param.isOptional() ? "" : "required") + " " + (multiple ? " multiple" : "") + ">");

        for (T object : objects) {
            Map.Entry<String, String> pair = toNameValue.apply(object);
            response.append("<option value=\"" + pair.getValue() + "\">" + pair.getKey() + "</option>");
        }

        response.append("</select>");

        return WebUtil.wrapLabel(param, uuid, response.toString(), WebUtil.InlineMode.NONE);
    }
}
