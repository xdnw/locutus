package link.locutus.discord.web.commands.binding.value_types;

import org.checkerframework.checker.nullness.qual.Nullable;

public final class WebError {
    public String error;

    public WebError(String error) {
        this.error = error;
    }
}
