package link.locutus.discord.web.builder;

import gg.jte.Content;
import gg.jte.generated.precompiled.JtemainGenerated;
import gg.jte.generated.precompiled.data.JtealertGenerated;
import gg.jte.generated.precompiled.data.JtespoilerGenerated;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;

import link.locutus.discord.util.scheduler.KeyValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PageBuilder {
    private final StringBuilder output;
    private final WebStore ws;
    private String title = "";
    private final List<Map.Entry<String, String>> navbar;

    public PageBuilder(WebStore ws) {
        this.ws = ws;
        this.output = new StringBuilder();
        this.navbar = new ArrayList<>();
    }

    public PageBuilder title(String title) {
        this.title = title;
        return this;
    }

    public PageBuilder addNavbar(String name, String url) {
        if (true) throw new UnsupportedOperationException("TODO not implemented");
        this.navbar.add(new KeyValue<>(name, url));
        return this;
    }

    public PageBuilder spoiler(String title, String content) {
        output.append(WebStore.render(f -> JtespoilerGenerated.render(f, null, ws, title + " \u25BC", ws.unsafe(content), "S" + UUID.randomUUID().getMostSignificantBits()))).append("\n");
        return this;
    }

    public PageBuilder add(String content) {
        this.output.append(content);
        return this;
    }

    public PageBuilder danger(String content) {
        output.append(WebStore.render(f -> JtealertGenerated.render(f, null, ws, "danger", ws.unsafe(content))));
        return this;
    }

    public PageBuilder warning(String content) {
        output.append(WebStore.render(f -> JtealertGenerated.render(f, null, ws, "warning", ws.unsafe(content))));
        return this;
    }

    public PageBuilder info(String content) {
        output.append(WebStore.render(f -> JtealertGenerated.render(f, null, ws, "info", ws.unsafe(content))));
        return this;
    }

    public PageBuilder alertPrimary(String content) {
        output.append(WebStore.render(f -> JtealertGenerated.render(f, null, ws, "primary", ws.unsafe(content))));
        return this;
    }

    public PageBuilder alertSecondary(String content) {
        output.append(WebStore.render(f -> JtealertGenerated.render(f, null, ws, "secondary", ws.unsafe(content))));
        return this;
    }

    public String buildWithContainer() {
        return WebStore.render(f -> JtemainGenerated.render(f, null, ws, ws.unsafe("<div class=\"bg-lightcontainer-fluid mt-3 rounded shadow py-1\">" + output.toString() + "</div>"), title, navbar));
    }

    public String build() {
        return WebStore.render(f -> JtemainGenerated.render(f, null, ws, ws.unsafe(output.toString()), title, navbar));
    }

    public boolean isEmpty() {
        return output.length() == 0;
    }
}