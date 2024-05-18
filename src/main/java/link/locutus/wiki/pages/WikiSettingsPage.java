package link.locutus.wiki.pages;

import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildSettingCategory;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.wiki.BotWikiGen;
import link.locutus.wiki.CommandWikiPages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class WikiSettingsPage extends BotWikiGen {
    public WikiSettingsPage(CommandManager2 manager) {
        super(manager, "guild_settings");
    }

    @Override
    public String getDescription() {
        return "List and description of all guild settings.";
    }

    @Override
    public String generateMarkdown() {
        List<GuildSetting> settings = new ArrayList<>(Arrays.asList(GuildKey.values()));
        // sort by category, then by name
        settings.sort(Comparator.comparing((GuildSetting a) -> a.getCategory()).thenComparing(GuildSetting::name));
        StringBuilder categoryLinks = new StringBuilder("### Categories\n");
        for (GuildSettingCategory cat : GuildSettingCategory.values()) {
            categoryLinks.append("- ").append(MarkupUtil.markdownUrl(cat.name(), "#category-" + cat.name().toLowerCase())).append("\n");
        }
        return build(
        """
                This page lists all the settings that can be set on a guild.
                To view a setting:
            """,
                commandMarkdownSpoiler(CM.settings.info.cmd),
                "To delete a setting",
                commandMarkdownSpoiler(CM.settings.delete.cmd),
                "---",
                categoryLinks.toString(),
                "---",
                "# Settings\n"
        ) +
        CommandWikiPages.printSettings(settings);
    }
}
