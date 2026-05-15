package link.locutus.discord.commands.manager.v2.binding;

import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.RedirectResponse;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.web.commands.binding.AuthBindings;
import link.locutus.discord.web.commands.binding.DBAuthRecord;
import link.locutus.discord.web.jooby.WebRoot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static link.locutus.discord.web.commands.binding.AuthBindings.getDiscordAuthUrl;

public class WebStore {
    private final ValueStore store;
    private final Context context;
    private DBAuthRecord auth;
    private boolean initAuth;

    public WebStore(ValueStore store) {
        this.store = store;
        this.context = (Context) store.getProvided(Context.class);
    }

    public WebStore(ValueStore store, Context context) {
        this.store = store;
        this.context = context;
    }

    public DBAuthRecord getCachedAuth() {
        return auth;
    }

    public Context context() {
        return context;
    }

    public DBAuthRecord auth() {
        return auth(false, false, false);
    }

    public DBAuthRecord auth(boolean allowRedirect, boolean requireNation, boolean requireUser) {
        try {
            if (!initAuth) {
                initAuth = true;
                this.auth = AuthBindings.getAuth(this, context, allowRedirect, requireNation, requireUser);
            }
            if (requireUser && (auth == null || auth.getUser(true) == null)) {
                throw new RedirectResponse(HttpStatus.SEE_OTHER, getDiscordAuthUrl());
            }
            if (requireNation && (auth == null || auth.getNation(true) == null)) {
                return AuthBindings.getAuth(this, context, allowRedirect, requireNation, requireUser);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return auth;
    }

    public DBNation getNation() {
        DBAuthRecord tmp = auth();
        if (tmp == null) return null;
        return tmp.getNation(true);
    }

    public User getUser() {
        DBAuthRecord tmp = auth();
        if (tmp == null) return null;
        return tmp.getUser(true);
    }

    public Guild getGuild() {
        return AuthBindings.guild(context, getNation(), getUser(), false);
    }

    public ValueStore store() {
        return store;
    }

    public PermissionHandler permisser() {
        return WebRoot.getInstance().getPageHandler().getPermisser();
    }
}
