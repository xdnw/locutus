package link.locutus.discord.web.commands.binding;

import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.ArgChoice;
import link.locutus.discord.commands.manager.v2.binding.annotation.TextArea;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timediff;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.HtmlInput;

import java.awt.Color;
import java.util.Arrays;
import java.util.UUID;

import static link.locutus.discord.web.WebUtil.createInput;

public class PrimitiveWebBindings extends BindingHelper {
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
        return WebUtil.createInputWithClass("textarea", WebUtil.InputType.text, param, null, true);
    }

    @HtmlInput
    @Binding(types={int.class, Integer.class}, examples = {"3"})
    public static String Integer(ParameterData param) {
        return WebUtil.createInput(WebUtil.InputType.number, param, "step='1'");
    }

    @HtmlInput
    @Binding(types={double.class, Double.class}, examples = {"3.0"})
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
