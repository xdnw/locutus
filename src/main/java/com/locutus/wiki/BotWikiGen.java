package com.locutus.wiki;

import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;

import java.util.ArrayList;
import java.util.List;

public abstract class BotWikiGen {
    private final String pageName;
    private final CommandManager2 manager;

    public BotWikiGen(CommandManager2 manager, String pageName) {
        this.pageName = pageName;
        this.manager = manager;
    }

    public String getPageName() {
        return pageName;
    }

    public CommandManager2 getManager() {
        return manager;
    }

    public String getDescription() {
        String markdown = generateMarkdown();
        String[] lines = markdown.split("\n");
        // Get all the top level `#` headings
        List<String> headings = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith("# ")) {
                line = line.substring(2);
                if (line.equalsIgnoreCase("see also")) continue;

                headings.add(line);
            }
        }
        return "- " + StringMan.join(headings, "\n- ");
    }

    public abstract String generateMarkdown();

    public String build(String... content) {
        return StringMan.join(content, "\n\n").replaceAll("<\\/(\\w{1,25}(\\s+\\w{1,25}){0,2}):\\d+>", "`/$1`");
    }

    public String commandMarkdownSpoiler(CommandRef ref) {
        return commandMarkdownSpoiler(ref, true);
    }

    public String commandMarkdownSpoiler(CommandRef ref, boolean prefixDescription) {
        String title = "<bold><kbd>" + ref.toSlashCommand(false) + "</kbd><br></bold>";
        ParametricCallable callable = ref.getCallable(true);
        String body = MarkupUtil.markdownToHTML(callable.toBasicMarkdown(manager.getStore(), null, "/", false, true)) + "\n\n---\n\n";
        String spoiler = MarkupUtil.spoiler(title, body);
        if (prefixDescription) {
            String desc = callable.simpleDesc().trim();
            if (!desc.isEmpty()) {
                String suffix = desc.contains("\n") ? "..." : "";
                desc = desc.split("\n")[0] + suffix;
                return "| :books:  " + desc + " |\n|-|\n\n" + spoiler;
            }
        }
        return spoiler;
    }

    public String commandMarkdown(CommandRef ref) {
        return "### " + ref.toSlashCommand(true) + "\n" + ref.getCallable(true).toBasicMarkdown(manager.getStore(), null, "/", false, true) + "\n\n---\n\n";
    }


    public String linkPage(String pageName) {
        return MarkupUtil.markdownUrl("Locutus/Wiki/" + pageName, "../wiki/" + pageName);
    }

    public String hr() {
        return "\n---\n";
    }

}
