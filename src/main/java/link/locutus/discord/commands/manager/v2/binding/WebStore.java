package link.locutus.discord.commands.manager.v2.binding;

import io.javalin.http.Context;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.web.commands.binding.AuthBindings;
import link.locutus.discord.web.commands.binding.DBAuthRecord;
import link.locutus.discord.web.jooby.WebRoot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

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
        if (!initAuth) {
            initAuth = true;
            this.auth = AuthBindings.getAuth(this, context);
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
        return AuthBindings.guild(context, getNation(), getUser());
    }

    public ValueStore store() {
        return store;
    }

    public PermissionHandler permisser() {
        return WebRoot.getInstance().getPageHandler().getPermisser();
    }
}
