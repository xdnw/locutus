package link.locutus.discord.commands.info;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.CommandManager;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.commons.lang3.text.WordUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class HelpCommand extends Command {
    private final CommandManager manager;

    public HelpCommand(CommandManager manager) {
        super("?", "help", CommandCategory.GENERAL_INFO_AND_TOOLS);
        this.manager = manager;
    }

    @Override
    public String help() {
        return Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "help [command]";
    }

    @Override
    public String desc() {
        return "Get command help";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        StringBuilder response = new StringBuilder();
        if (args.size() == 0) {
            Set<CommandCategory> categories = new HashSet<CommandCategory>();
            Set<Command> cmds = new LinkedHashSet<>(manager.getCommandMap().values());
            cmds.removeIf(cmd -> {
                try {
                    return !cmd.checkPermission(event.isFromGuild() ? event.getGuild() : null, event.getAuthor());
                } catch (Throwable e) {
                    return true;
                }
            });
            for (Command cmd : cmds) categories.addAll(cmd.getCategories());

            for (CommandCategory category : categories) {
                String categoryCase = WordUtils.capitalizeFully(category.name(), new char[]{'_'});
                response.append("`" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "? " + categoryCase + "`").append("\n");
            }


//
//            for (Command cmd : cmds) {
//                response.append('\n')
//                        .append(cmd.getAliases().get(0).toUpperCase());
//                if (cmd.getAliases().size() > 1) {
//                    response.append("\n\tAliases: ").append(StringMan.getString(cmd.getAliases()));
//                }
//                response.append("\n\tUsage: ").append(cmd.help());
//                response.append("\n\tDesc: ").append(cmd.desc());
//                response.append('\n');
//            }

            response.append("\n").append("**You can specify multiple categories**");
            response.append("\n").append("**For help on a specific command, use: `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "? <command>`**");
            response.append("\n").append("**To search for a cmd, use: `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "? <search>`**");
            String footer = "Bot created and managed by the Interwebs Sourcery division of the Borg Collective. If you would like this bot in your server use the chant `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "invite` and follow the summoning ritual instructions.";
            DiscordUtil.createEmbedCommandWithFooter(event.getChannel(), "Locutus Cats", response.toString().trim(), footer);
        } else {
            Integer page = DiscordUtil.parseArgInt(args, "page");

            List<String> commandsDescShort = new ArrayList<>();
            Set<CommandCategory> requiredCategories = new HashSet<>();

            for (String arg : args) {
                CommandCategory category = CommandCategory.valueOf(arg.toUpperCase());
                requiredCategories.add(category);
            }
            if (!requiredCategories.isEmpty()) {
                LinkedHashSet<Command> commands = new LinkedHashSet<>(manager.getCommandMap().values());
                for (Command command : commands) {
                    try {
                        if (!command.checkPermission(event.isFromGuild() ? event.getGuild() : null, event.getAuthor()))
                            continue;
                        if (command.getCategories().containsAll(requiredCategories)) {
                            String descShort = command.desc().split("\n")[0];
                            String helpDesc = "`" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "? " + command.getAliases().get(0) + "` - " + descShort;
                            commandsDescShort.add(helpDesc);
                        }
                    } catch (IllegalArgumentException ignore) {

                    }
                }
                if (!commandsDescShort.isEmpty()) {
                    String cmd = DiscordUtil.trimContent(event.getMessage().getContentRaw());
                    if (page == null) page = 0;
                    int perPage = 15;
                    int pages = (commandsDescShort.size() + perPage - 1) / perPage;

                    String title = StringMan.join(requiredCategories, ",");
                    title += " (" + (page + 1) + "/" + pages + ")";
                    DiscordUtil.paginate(event.getGuildChannel(), title, cmd, page, perPage, commandsDescShort);
                    return null;
                }
            }

            String arg = args.get(0).toLowerCase();
            Command cmd = manager.getCommandMap().get(arg);
            if (cmd == null) {
                Set<String> commands = new HashSet<>();
                for (String alias : manager.getCommandMap().keySet()) {
                    if (alias.contains(arg)) commands.add(alias);
                }
                if (!commands.isEmpty()) {
                    return "Did you mean:\n - " + StringMan.join(commands, "\n - ");
                } else {
                    return "Command not found for `" + args.get(0) + "`" + ". Use `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "?` to get a list of commands";
                }
            }
            response.append("\n").append("`").append(cmd.help()).append("`").append(" - ").append(cmd.desc());
            DiscordUtil.createEmbedCommand(event.getChannel(), args.get(0), response.toString().trim());
        }
        return null;
    }
}
