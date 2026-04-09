package link.locutus.discord.web.commands.binding.value_types;

import java.util.Map;

public class WebSettingValidationErrors {
    public final Map<Integer, String> errors;

    public WebSettingValidationErrors(Map<Integer, String> errors) {
        this.errors = errors;
    }
}
