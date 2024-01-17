package link.locutus.discord.commands.manager.v2.binding;

import gg.jte.Content;
import gg.jte.TemplateOutput;
import gg.jte.html.HtmlContent;
import gg.jte.html.HtmlTemplateOutput;
import gg.jte.html.OwaspHtmlTemplateOutput;
import gg.jte.output.StringOutput;
import io.javalin.http.Context;
import link.locutus.discord.web.commands.binding.AuthBindings;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class WebStore {
    private final ValueStore store;
    private final Context context;
    private AuthBindings.Auth auth;

    public WebStore(ValueStore store) {
        this.store = store;
        this.context = (Context) store.getProvided(Context.class);
    }

    public static String render(Consumer<OwaspHtmlTemplateOutput> task) {
        TemplateOutput output = new StringOutput();
        OwaspHtmlTemplateOutput htmlOutput = new OwaspHtmlTemplateOutput(output);
        task.accept(htmlOutput);
        return output.toString();
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

    public AuthBindings.Auth auth() {
        return auth(false, false, false);
    }

    public AuthBindings.Auth auth(boolean allowRedirect, boolean requireNation, boolean requireUser) {
        if (this.auth != null) return this.auth;
        try {
            this.auth = AuthBindings.getAuth(this, context, allowRedirect, requireNation, requireUser);
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

    public List<Content> list(String... elements) {
        return Stream.of(elements).map(this::safe).toList();
    }

    public List<Content> list(Collection<String> myList) {
        return myList.stream().map(this::safe).toList();
    }

    public List<Content> listUnsafe(String... elements) {
        return Stream.of(elements).map(this::unsafe).toList();
    }

    public List<List<Content>> table(List<List<String>> table) {
        return table.stream().map(this::list).toList();
    }
}
