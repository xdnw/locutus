package link.locutus.discord.web.commands.binding.value_types;

import java.util.Map;

public class WebSettingValidationCheapness {
    public final Map<Integer, Boolean> is_cheap;

    public WebSettingValidationCheapness(Map<Integer, Boolean> isCheap) {
        this.is_cheap = isCheap;
    }
}
