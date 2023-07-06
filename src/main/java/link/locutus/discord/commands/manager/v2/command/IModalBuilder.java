package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.util.RateLimitUtil;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public interface IModalBuilder {
    IModalBuilder addInput(TextInput input);

    IModalBuilder setTitle(String title);

    CompletableFuture<IModalBuilder> send();

    default CompletableFuture<IModalBuilder> sendIfFree() {
        if (RateLimitUtil.getCurrentUsed() < RateLimitUtil.getLimitPerMinute()) {
            return send();
        }
        return null;
    }

}
