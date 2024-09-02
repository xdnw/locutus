package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.NationAttributeCallable;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.ICommand;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.gpt.ModerationResult;
import link.locutus.discord.gpt.pw.PWGPTHandler;
import link.locutus.discord.util.MarkupUtil;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HelpCommands {
    public HelpCommands() {
    }

    public PWGPTHandler getGPT() {
        return Locutus.imp().getCommandManager().getV2().getPwgptHandler();
    }

//    @Command
//    public void use_command(@Me IMessageIO io, ValueStore store, ParametricCallable command, String query) {
//        StringBuilder prompt = new StringBuilder();
//
//        OpenAiService service = getGPT().getHandler().getService();
//    }
//    @Command
//    public void find_placeholders(@Me IMessageIO io, ValueStore store, String query, @Range(min = 1, max = 25) @Default("3") int num_results) {
//
//    }

    @Command
    public String query(ValueStore store, @Me GuildDB db, @Me User user, @Me IMessageIO io, String input) throws IOException {
        return "Not implemented";
//        String result = getGPT().generateSolution(store, db, user, input);
//        return result;
    }

    @Command
    public void moderation_check(@Me IMessageIO io, String input) throws IOException {
        List<String> inputs = List.of(input);
        List<ModerationResult> results = getGPT().getHandler().getModerator().moderate(inputs);
        IMessageBuilder msg = io.create();
        for (ModerationResult result : results) {
            msg.append("Flagged: " + result.isFlagged() + "\n");
            if (result.isFlagged()) {
                msg.append("Flagged categories: " + result.getFlaggedCategories() + "\n");
            }
            if (result.isError()) {
                msg.append("Error message: " + result.getMessage() + "\n");
            }
        }
        msg.send();
    }

    @Command
    public void argument(@Me IMessageIO io, Parser argument, @Switch("s") boolean skipOptionalArgs) {
        Key key = argument.getKey();
        String title = "`" + key.toSimpleString() + "`";
        StringBuilder body = new StringBuilder(argument.getNameDescriptionAndExamples(false, true, true, true));

        CommandManager2 cmdManager = Locutus.imp().getCommandManager().getV2();
        Set<ParametricCallable> allCommands = cmdManager.getCommands().getParametricCallables(f -> true);

        List<ParametricCallable> hasArgument = new ArrayList<>();
        Set<Method> methods = new HashSet<>();
        for (ParametricCallable callable : allCommands) {
            Method method = callable.getMethod();
            // add / skip if method already checked
            if (methods.contains(method)) continue;
            methods.add(method);

            for (ParameterData userParam : callable.getUserParameters()) {
                if (skipOptionalArgs && userParam.isOptional()) continue;

                Key userKey = userParam.getBinding().getKey();
                if (userKey.equals(key)) {
                    hasArgument.add(callable);
                    break;
                }
            }
        }

        List<String> commandListStr = new ArrayList<>();
        for (ParametricCallable callable : hasArgument) {
            String commandStr = callable.getSlashMention();
            commandListStr.add(commandStr);
        }
        body.append("\n" + MarkupUtil.markdownUrl("Arguments Wiki Page", "https://github.com/xdnw/locutus/wiki/Arguments"));

        if (!commandListStr.isEmpty()) {
            body.append("\n\nCommands that use this argument:\n- " + String.join("\n- ", commandListStr));
        }

        io.create().embed(title, body.toString()).send();
    }

    @Command(desc = "Show the description, usage information and permissions for a command")
    public String command(@Me IMessageIO io, ValueStore store, PermissionHandler permisser, ICommand command) {
        String body = command.toBasicMarkdown(store, permisser, "/", false, true, true);
        String title = "/" + command.getFullPath();
        if (body.length() > 4096) {
            return "#" + title + "\n" + body;
        }

        IMessageBuilder embed = io.create().embed(title, body);

        PWGPTHandler gpt = Locutus.imp().getCommandManager().getV2().getPwgptHandler();
        if (gpt != null && command instanceof ParametricCallable pc) {
            List<ParametricCallable> closest = gpt.getClosestCommands(store, pc, 6);
            for (ParametricCallable callable : closest) {
                if (callable.getMethod().equals(pc.getMethod())) continue;
                embed = embed.commandButton(CommandBehavior.DELETE_MESSAGE,
                        CM.help.command.cmd.command(callable.getFullPath()),
                        callable.getFullPath());
            }
        }

        embed.send();
        return null;
    }

    @Command(desc = "Show the description, usage information and permissions for a nation placeholder")
    public String nation_placeholder(@Me IMessageIO io, ValueStore store, PermissionHandler permisser, @NationAttributeCallable ParametricCallable command) {
        String body = command.toBasicMarkdown(store, permisser, "/", false, true, true);
        String title = "/" + command.getFullPath();
        if (body.length() > 4096) {
            return "#" + title + "\n" + body;
        }
        IMessageBuilder embed = io.create().embed(title, body);
        PWGPTHandler gpt = Locutus.imp().getCommandManager().getV2().getPwgptHandler();
        if (gpt != null) {
            List<ParametricCallable> closest = gpt.getClosestNationAttributes(store, command, 6);
            for (ParametricCallable other : closest) {
                if (other.getMethod().equals(command.getMethod())) continue;
                embed = embed.commandButton(CommandBehavior.DELETE_MESSAGE,
                        CM.help.nation_placeholder.cmd.command(other.getFullPath()), other.getFullPath());
            }
        }

        embed.send();
        return null;
    }

    @Command(desc = "Locate a setting you are looking for.\n" +
            "Use keywords for relevant results, or ask a question.")
    public void find_setting(@Me IMessageIO io, ValueStore store, String query, @Range(min = 1, max = 25) @Default("5") int num_results) {
        try {
            IMessageBuilder msg = io.create();
            msg.append("**All settings: **" + CM.settings.info.cmd.key("true") + "\n");
            msg.append("- More Info: " + CM.settings.info.cmd.key("YOUR_KEY_HERE") + "\n");
            msg.append("- To Delete: " + CM.settings.delete.cmd.key("YOUR_KEY_HERE") + "\n\n");

            List<GuildSetting> results = getGPT().getClosestSettings(store, query, num_results);
            for (int i = 0; i < results.size(); i++) {
                GuildSetting obj = results.get(i);
                msg.append("__**" + (i + 1) + ".**__ ");
                msg.append("**" + obj.name() + "**: " + obj.getCommandMention() + "\n");

                String desc = obj.help();
                int tickIndex = desc.indexOf("```");
                int optionsIndex = desc.toLowerCase().indexOf("options");
                if (tickIndex != -1 || optionsIndex != -1) {
                    if (tickIndex == -1) tickIndex = Integer.MAX_VALUE;
                    if (optionsIndex == -1) optionsIndex = Integer.MAX_VALUE;
                    int first = Math.min(tickIndex, optionsIndex);
                    desc = desc.substring(0, first);
                }
                desc = desc.trim();

                msg.append("> " + desc.replaceAll("\n", "\n > "));
                msg.append("\n");
            }
            msg.send();
        } catch (IllegalArgumentException e) {
            io.send(e.getMessage());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

//    @Command
//    public String setting(@Default GuildDB key, @Default String query) {
//        return null;
//    }
//
//    @Command
//    public String test(String question) {
//        return "Not implemented";
//    }
//
//    @Command
//    public String nationStat(@Default NationPlaceholder placeholder, @Default String query) {
//        return null;
//    }

//
//    @Command
//    public String type(@Default @Argument Key type, @Default String query) {
//        /*
//        Help on this argument (description, examples)
//
//        List of commands that use this argument
//         */
//        return null;
//    }
//
//    @Command
//    public String permission(@Default @Permission Key type, @Default String query) {
//        return null;
//    }
//

//
//

}
