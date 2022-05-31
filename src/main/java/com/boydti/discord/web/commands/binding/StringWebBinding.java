package com.boydti.discord.web.commands.binding;

import com.boydti.discord.commands.manager.v2.binding.BindingHelper;
import com.boydti.discord.commands.manager.v2.binding.annotation.Binding;
import com.boydti.discord.commands.manager.v2.binding.annotation.TextArea;
import com.boydti.discord.commands.manager.v2.command.ParameterData;
import com.boydti.discord.web.WebUtil;
import com.boydti.discord.web.commands.HtmlInput;

import static com.boydti.discord.web.WebUtil.createInput;
import static com.boydti.discord.web.WebUtil.createInputWithClass;

public class StringWebBinding extends BindingHelper {
    @HtmlInput
    @Binding(examples = "hello", types={String.class})
    public static String String(ParameterData param) {
        if (param.getAnnotation(TextArea.class) == null) {
            return createInput(WebUtil.InputType.text, param);
        }
        return createInputWithClass("textarea", WebUtil.InputType.text, param, null, true);
    }
}
