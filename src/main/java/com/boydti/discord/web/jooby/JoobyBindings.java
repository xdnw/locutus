package com.boydti.discord.web.jooby;

import com.boydti.discord.commands.manager.v2.binding.BindingHelper;
import com.boydti.discord.commands.manager.v2.binding.annotation.Binding;
import com.boydti.discord.commands.manager.v2.binding.annotation.Me;
import com.boydti.discord.db.GuildDB;
import io.javalin.http.Context;

public class JoobyBindings extends BindingHelper {
    @Binding
    public Context context() {
        throw new IllegalStateException("No context set in command locals");
    }
}
