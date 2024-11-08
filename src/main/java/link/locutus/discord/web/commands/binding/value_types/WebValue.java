package link.locutus.discord.web.commands.binding.value_types;

import org.checkerframework.checker.nullness.qual.Nullable;

public class WebValue extends WebSuccess {
    public String value;
    public WebValue(String value) {
        super(true, null);
        this.value = value;
    }
}
