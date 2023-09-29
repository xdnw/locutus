package link.locutus.discord.web.commands.binding;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.ArgChoice;
import link.locutus.discord.commands.manager.v2.binding.annotation.TextArea;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timediff;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.HtmlInput;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static link.locutus.discord.web.WebUtil.createInput;

public class PrimitiveWebBindings extends WebBindingHelper {

    @Binding(types = {Set.class, Integer.class}, multiple = true)
    @HtmlInput
    public String Set(ValueStore store, ParameterData param) {
        return createInput(WebUtil.InputType.text, param, "pattern=\"[0-9]+(,[0-9]+)*\"");
    }

    @Binding(types = {List.class, String.class}, multiple = true)
    @TextArea
    @HtmlInput
    public String List(ParameterData param) {
        return TextArea(param);
    }

    @Binding(types = {String.class})
    @TextArea
    @HtmlInput
    public static String TextArea(ParameterData param) {
        return WebUtil.createInputWithClass("textarea", WebUtil.InputType.text, param, null, true);
    }

    @Binding(types = Parser.class)
    @HtmlInput
    public String Parser(ValueStore store, ParameterData param) {
        Map<Key, Parser> parsers = store.getParsers();
        List<Parser> options = parsers.values().stream().filter(parser -> parser.isConsumer(store)).toList();
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.getKey().toSimpleString());
        });
    }

    @HtmlInput
    @Binding(examples = "hello", types={String.class})
    public static String String(ParameterData param) {
        ArgChoice choice = param.getAnnotation(ArgChoice.class);
        if (choice != null) {
            String[] choices = choice.value();
            return WebUtil.generateSearchableDropdown(param, Arrays.asList(choices), (obj, names, values, subtext) -> {
                names.add(obj);
            });
        }
        if (param.getAnnotation(TextArea.class) == null) {
            return WebUtil.createInput(WebUtil.InputType.text, param);
        }
        return TextArea(param);
    }

    @HtmlInput
    @Binding(types={int.class, Integer.class}, examples = {"3"})
    public static String Integer(ParameterData param) {
        return WebUtil.createInput(WebUtil.InputType.number, param, "step='1'");
    }

    @HtmlInput
    @Binding(types={double.class, Double.class, Number.class}, examples = {"3.0"})
    public static String Double(ParameterData param) {
        return WebUtil.createInput(WebUtil.InputType.number, param);
    }

    @HtmlInput
    @Binding(types={long.class, Long.class}, examples = {"3.0"})
    public static String Long(ParameterData param) {
        return Integer(param);
    }

    @HtmlInput
    @Binding(examples = {"true", "false"}, types = {boolean.class, Boolean.class})
    public String Boolean(ParameterData param) {
        String def = param.getDefaultValueString();
        return WebUtil.createInput(WebUtil.InputType.checkbox, param, (def != null && def.equals("true") ? "checked " : ""));
    }

    @HtmlInput
    @Binding(examples = {"#420420"}, types={Color.class})
    public static String color(ParameterData param) {
        return WebUtil.createInput(WebUtil.InputType.color, param);
    }

    @HtmlInput
    @Binding(examples = {"8-4-4-4-12"}, types={UUID.class})
    public static String uuid(ParameterData param) {
        String pattern = "/^[0-9a-f]{8}-[0-9a-f]{4}-[0-5][0-9a-f]{3}-[089ab][0-9a-f]{3}-[0-9a-f]{12}$/i";
        return WebUtil.createInput(WebUtil.InputType.text, param, "pattern='" + pattern + "'");
    }

    @Timediff
    @HtmlInput
    @Binding(types={long.class, Long.class}, examples = {"5d", "10h3m25s"})
    public static String timediff(ParameterData param) {
        return WebUtil.createInputWithClass("input", WebUtil.InputType.date, param, "input-timediff", false);
    }

    @Timestamp
    @HtmlInput
    @Binding(types={long.class, Long.class}, examples = {"5d", "10h3m25s", "dd/MM/yyyy"})
    public static String timestamp(ParameterData param) {
        return WebUtil.createInput(WebUtil.InputType.date, param);
    }
}
