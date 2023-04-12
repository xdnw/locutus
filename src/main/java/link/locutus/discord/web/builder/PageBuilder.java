package link.locutus.discord.web.builder;

import link.locutus.discord.commands.manager.v2.command.ParametricCallable;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PageBuilder {
    private final StringBuilder output;
    private String title = "";
    private final List<Map.Entry<String, String>> navbar;

    public PageBuilder() {
        this.output = new StringBuilder();
        this.navbar = new ArrayList<>();
    }

    public PageBuilder title(String title) {
        this.title = title;
        return this;
    }

    public PageBuilder addNavbar(String name, String url) {
        if (true) throw new UnsupportedOperationException("TODO not implemented");
        this.navbar.add(new AbstractMap.SimpleEntry<>(name, url));
        return this;
    }

    public PageBuilder spoiler(String title, String content) {
        output.append(rocker.data.spoiler.template(title + " \u25BC", content, "S" + UUID.randomUUID().getMostSignificantBits()).render().toString()).append("\n");
        return this;
    }

    public PageBuilder add(String content) {
        this.output.append(content);
        return this;
    }

    public PageBuilder danger(String content) {
        output.append(rocker.data.alert.template("danger", content).render().toString());
        return this;
    }

    public PageBuilder warning(String content) {
        output.append(rocker.data.alert.template("warning", content).render().toString());
        return this;
    }

    public PageBuilder info(String content) {
        output.append(rocker.data.alert.template("info", content).render().toString());
        return this;
    }

    public PageBuilder alertPrimary(String content) {
        output.append(rocker.data.alert.template("primary", content).render().toString());
        return this;
    }

    public PageBuilder alertSecondary(String content) {
        output.append(rocker.data.alert.template("secondary", content).render().toString());
        return this;
    }

    public String buildWithContainer() {
        return rocker.main_raw.template(title, navbar, "<div class=\"bg-white container-fluid mt-3 rounded shadow py-1\">" + output.toString() + "</div>").render().toString();
    }

    public String build() {
        return rocker.main_raw.template(title, navbar, output.toString()).render().toString();
    }
}