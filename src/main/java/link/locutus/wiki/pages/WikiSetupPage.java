package link.locutus.wiki.pages;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;
import link.locutus.wiki.BotWikiGen;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static link.locutus.discord.user.Roles.*;

public class WikiSetupPage extends BotWikiGen {
    public WikiSetupPage(CommandManager2 manager) {
        super(manager, "initial_setup");
    }

    @Override
    public String generateMarkdown() {
        Set<Roles> usedRoles = new ObjectLinkedOpenHashSet<>();
        usedRoles.add(Roles.REGISTERED);
        usedRoles.add(Roles.MEMBER);

        Set<Roles> basicRoles = new ObjectLinkedOpenHashSet<>(Arrays.asList(
                MILCOM,
                ECON,
                FOREIGN_AFFAIRS,
                INTERNAL_AFFAIRS,
                MILCOM_NO_PINGS,
                ECON_STAFF,
                FOREIGN_AFFAIRS_STAFF,
                INTERNAL_AFFAIRS_STAFF
        ));

        List<Roles> otherRoles = Arrays.stream(values).filter(role -> !usedRoles.contains(role) && !basicRoles.contains(role)).collect(Collectors.toList());

        Function<Collection<Roles>, String> rolesToInfo = roles -> {
            StringBuilder response = new StringBuilder();
            for (Roles role : roles) {
                response.append("- `").append(role.name()).append("` ");
                if (role.getKey() != null) {
                    response.append(" | requires setting `").append(role.getKey().name()).append("`");
                }
                response.append(" - ").append(role.getDesc());
                response.append("\n");
            }
            return response.toString();
        };

        return build(
                """
                Locutus is a bot on the discord server which assists with war calculations and alliance affairs for the game Politics and War.
                                    
                Support Server: [<kbd> <br> Discord Support Server <br> </kbd>][Link]
                                
                [Link]: https://discord.gg/cUuskPDrB7 'Discord Support Server'
                                    
                Created by Borg (nation: <https://politicsandwar.com/nation/id=189573> discord: `xdnw` )""",
                "# Inviting the bot to your discord server",
                """
                Click this link:
                https://discord.com/api/oauth2/authorize?client_id=672237266940198960&permissions=395606879321&scope=bot
                """,
                "# Registering your nation",
                """
                Use the register command to link your in-game nation to your discord.
                This allows you to use nation related commands.""",
                CM.register.cmd.toSlashCommand(true),
                commandMarkdownSpoiler(CM.register.cmd, false),
                "Example:",
                CM.register.cmd.nation("https://politicsandwar.com/nation/id=189573").toSlashCommand(true),
                "You can verify by using:",
                CM.me.cmd.toSlashCommand(true),
                "# Registering your alliance",
                GuildKey.ALLIANCE_ID.help(),
                CM.settings_default.registerAlliance.cmd.toSlashCommand(true),
                "# Creating and registering roles",
                """
                        The bot uses discord roles to manage notifications and access to commands
                        You must register these discord roles using""",
                commandMarkdownSpoiler(CM.role.setAlias.cmd, false),
                "Let's register three discord roles to the bot (first create the discord roles if they do not exist)",
                "- " + REGISTERED.name() + " - " + REGISTERED.getDesc(),
                "- " + MEMBER.name() + " - " + MEMBER.getDesc(),
                "- " + ADMIN.name() + " - " + ADMIN.getDesc(),
                "Register them using:",
                "- " + CM.role.setAlias.cmd.locutusRole(REGISTERED.name()).discordRole("@registered").toSlashCommand(true),
                "- " + CM.role.setAlias.cmd.locutusRole(MEMBER.name()).discordRole("@member").toSlashCommand(true),
                "- " + CM.role.setAlias.cmd.locutusRole(ADMIN.name()).discordRole("@admin").toSlashCommand(true),
                "There are roles which correspond to alliance departments",
                "You can register all of them, or only the main ones you need",
                rolesToInfo.apply(basicRoles),
                "Other roles work for specific configuration options",
                "To view a config option, use:",
                CM.settings.info.cmd.key("").toSlashCommand(true),
                "Here is a list of all the roles:",
                rolesToInfo.apply(otherRoles),
                "# Remove a role alias",
                "Here is an example of unlinking the registered role",
                CM.role.setAlias.cmd.locutusRole(REGISTERED.name()).removeRole("true").toSlashCommand(true),
                "# Setting your API Key",
                GuildKey.API_KEY.help(),
                "To set your API key, use:",
                CM.settings_default.registerApiKey.cmd.toSlashCommand(true)
        );
    }
}
