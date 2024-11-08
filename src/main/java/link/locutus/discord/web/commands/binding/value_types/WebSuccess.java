package link.locutus.discord.web.commands.binding.value_types;

import org.checkerframework.checker.nullness.qual.Nullable;

public class WebSuccess {
    public boolean success;
    public @Nullable String message;

    public WebSuccess(boolean success, @Nullable String message) {
        this.success = success;
        this.message = message;
    }
}
