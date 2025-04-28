package link.locutus.discord.commands.manager.v2.binding;

import gg.jte.Content;
import gg.jte.TemplateOutput;
import gg.jte.html.HtmlContent;
import gg.jte.html.HtmlTemplateOutput;
import gg.jte.html.OwaspHtmlTemplateOutput;
import gg.jte.output.StringOutput;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.RedirectResponse;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.web.commands.binding.AuthBindings;
import link.locutus.discord.web.commands.binding.DBAuthRecord;
import link.locutus.discord.web.jooby.WebRoot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.util.ArrayList;
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

    public static String render(Consumer<OwaspHtmlTemplateOutput> task) {
        TemplateOutput output = new StringOutput();
        OwaspHtmlTemplateOutput htmlOutput = new OwaspHtmlTemplateOutput(output);
        task.accept(htmlOutput);
        return output.toString();
    }

    public Map<String, String> getPathLinks() {
        String path = context.path();
        String[] splitPath = path.split("/");
        StringBuilder currentPath = new StringBuilder();
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("Home", "");
        for (String s : splitPath) {
            if (s.isEmpty()) continue;
            currentPath.append("/").append(s);
            paths.put(s, currentPath.toString());
        }
        return paths;
    }

    public Content unsafe(String content) {
        return (HtmlContent) out -> out.writeUnsafeContent(content);
    }

    public Content safe(String content) {
        return (HtmlContent) out -> out.writeUserContent(content);
    }

    public Content content(String content, boolean safe) {
        return safe ? safe(content) : unsafe(content);
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

    public List<Content> list(String... elements) {
        return Stream.of(elements).map(this::safe).toList();
    }

    public List<Content> list(Collection<String> myList) {
        return myList.stream().map(this::safe).toList();
    }

    public List<Content> listUnsafe(String... elements) {
        return Stream.of(elements).map(this::unsafe).toList();
    }

    public List<Content> listUnsafe(Collection<String> myList) {
        return myList.stream().map(this::unsafe).toList();
    }

    public List<List<Content>> table(List<List<String>> table) {
        return table.stream().map(this::list).toList();
    }

    public List<List<Content>> tableUnsafe(List<List<String>> table) {
        return table.stream().map(this::listUnsafe).toList();
    }

    public PermissionHandler permisser() {
        return WebRoot.getInstance().getPageHandler().getPermisser();
    }
}
