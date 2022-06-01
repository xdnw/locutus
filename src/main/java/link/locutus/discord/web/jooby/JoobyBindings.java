package link.locutus.discord.web.jooby;

import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import io.javalin.http.Context;

public class JoobyBindings extends BindingHelper {
    @Binding
    public Context context() {
        throw new IllegalStateException("No context set in command locals");
    }
}
