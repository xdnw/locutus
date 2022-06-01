package link.locutus.discord.web.commands.binding;

import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.TextArea;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.HtmlInput;

import static link.locutus.discord.web.WebUtil.createInput;

public class StringWebBinding extends BindingHelper {
    @HtmlInput
    @Binding(examples = "hello", types={String.class})
    public static String String(ParameterData param) {
        if (param.getAnnotation(TextArea.class) == null) {
            return WebUtil.createInput(WebUtil.InputType.text, param);
        }
        return WebUtil.createInputWithClass("textarea", WebUtil.InputType.text, param, null, true);
    }
}
