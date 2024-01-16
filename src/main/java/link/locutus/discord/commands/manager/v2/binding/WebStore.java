package link.locutus.discord.commands.manager.v2.binding;

import io.javalin.http.Context;
import link.locutus.discord.web.commands.binding.AuthBindings;

import java.io.IOException;

public class WebStore {
    private final ValueStore store;
    private final Context context;
    private AuthBindings.Auth auth;

    public WebStore(ValueStore store) {
        this.store = store;
        this.context = (Context) store.getProvided(Context.class);
    }

    public Context context() {
        return context;
    }

    public AuthBindings.Auth auth() {
        return auth(false, false, false);
    }

    public AuthBindings.Auth auth(boolean allowRedirect, boolean requireNation, boolean requireUser) {
        if (this.auth != null) return this.auth;
        try {
            this.auth = AuthBindings.getAuth(context, allowRedirect, requireNation, requireUser);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public ValueStore store() {
        return store;
    }

    public <T> T getSafe(Class clazz) {
        return get(clazz, false);
    }

    public <T> T getSafe(Class clazz, Class clazz2) {
        return get(clazz, clazz2,false);
    }

    public <T> T get(Class clazz, boolean throwError) {
        return (T) store.getProvided(Key.of(clazz), throwError);
    }

    public <T> T get(Class clazz, Class clazz2, boolean throwError) {
        return (T) store.getProvided(Key.of(clazz, clazz2), throwError);
    }
}
