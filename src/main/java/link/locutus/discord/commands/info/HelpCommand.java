package link.locutus.discord.commands.info;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.CommandManager;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.commons.lang3.text.WordUtils;

import java.util.*;

public class HelpCommand extends Command {
    private final CommandManager manager;

    public HelpCommand(CommandManager manager) {
        super("?", "help", CommandCategory.GENERAL_INFO_AND_TOOLS);
        this.manager = manager;
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "help [command]";
    }

    @Override
    public String desc() {
        return "Get a command help.";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        StringBuilder response = new StringBuilder();
        if (args.size() == 0) {
            Set<CommandCategory> categories = new HashSet<>();
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
                String categoryCase = WordUtils.capitalizeFully(category.name(), '_');
                response.append("`").append(Settings.commandPrefix(true)).append("? ").append(categoryCase).append("`").append("\n");
            }

            response.append("\n").append("**You can specify multiple categories**");
            response.append("\n").append("**For help on a specific command, use: `").append(Settings.commandPrefix(true)).append("? <command>`**");
            response.append("\n").append("**To search for a cmd, use: `").append(Settings.commandPrefix(true)).append("? <search>`**");
            String footer = "Bot created and managed by the Interwebs Sourcery division of the Borg Collective. If you would like this bot in your server use the chant `" + Settings.commandPrefix(true) + "invite` and follow the summoning ritual instructions.";
            DiscordUtil.createEmbedCommandWithFooter(event.getChannel(), "Locutus Cats", response.toString().trim(), footer);
        } else {
            Integer page = DiscordUtil.parseArgInt(args, "page");

            List<String> commandsDescShort = new ArrayList<>();
            Set<CommandCategory> requiredCategories = new HashSet<>();

            for (String arg : args) {
                try {
                    CommandCategory category = CommandCategory.valueOf(arg.toUpperCase());
                    requiredCategories.add(category);
                } catch (IllegalArgumentException ignore) {
                }
            }
            if (!requiredCategories.isEmpty()) {
                LinkedHashSet<Command> commands = new LinkedHashSet<>(manager.getCommandMap().values());
                for (Command command : commands) {
                    try {
                        if (!command.checkPermission(event.isFromGuild() ? event.getGuild() : null, event.getAuthor()))
                            continue;
                        if (command.getCategories().containsAll(requiredCategories)) {
                            if (command.desc() == null) throw new IllegalArgumentException("Command: " + command.getAliases().get(0) + " returns null for description");
                            String descShort = command.desc().split("\n")[0];
                            String helpDesc = "`" + Settings.commandPrefix(true) + "? " + command.getAliases().get(0) + "` - " + descShort;
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
                    return "Command not found for `" + args.get(0) + "`" + ". Use `" + Settings.commandPrefix(true) + "?` to get a list of commands";
                }
            }
            response.append("\n").append("`").append(cmd.help()).append("`").append(" - ").append(cmd.desc());
            DiscordUtil.createEmbedCommand(event.getChannel(), args.get(0), response.toString().trim());
        }
        return null;
    }
}
