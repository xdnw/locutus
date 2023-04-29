package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import com.theokanning.openai.OpenAiService;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationPlaceholder;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.gpt.CommandEmbedding;
import link.locutus.discord.gpt.EmbeddingType;
import link.locutus.discord.gpt.PWEmbedding;
import link.locutus.discord.gpt.PWGPTHandler;
import link.locutus.discord.gpt.SettingEmbedding;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class HelpCommands {
    private final PWGPTHandler handler;

    public HelpCommands(PWGPTHandler handler) {
        this.handler = handler;
    }

    @Command
    public void use_command(@Me IMessageIO io, ValueStore store, ParametricCallable command, String query) {
        StringBuilder prompt = new StringBuilder();

        OpenAiService service = handler.getHandler().getService();


    }
//    @Command
//    public void find_placeholders(@Me IMessageIO io, ValueStore store, String query, @Range(min = 1, max = 25) @Default("3") int num_results) {
//
//    }

    @Command
    public void find_setting(@Me IMessageIO io, ValueStore store, String query, @Range(min = 1, max = 25) @Default("5") int num_results) {
        try {
            IMessageBuilder msg = io.create();
            msg.append("**See: **" + CM.settings.cmd.toSlashMention());
            msg.append("\n - More Info: " + CM.settings.cmd.create("YOUR_KEY_HERE", null, null, null).toSlashCommand() + "\n");
            msg.append(" - To set " + CM.settings.cmd.create("YOUR_KEY_HERE", "Your Value Here", null, null).toSlashCommand() + "\n\n");

            List<Map.Entry<PWEmbedding, Double>> closest = handler.getClosest(store, query, num_results, Set.of(EmbeddingType.Configuration));
            for (int i = 0; i < closest.size(); i++) {
                Map.Entry<PWEmbedding, Double> entry = closest.get(i);
                SettingEmbedding embed = (SettingEmbedding) entry.getKey();
                GuildDB.Key obj = embed.getObj();

                msg.append("__**" + (i + 1) + ".**__ ");
                msg.append("**" + obj.name() + "**: \n");

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
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Command
    public void find_command(@Me IMessageIO io, ValueStore store, String query, @Range(min = 1, max = 25) @Default("5") int num_results) {
        try {
            IMessageBuilder msg = io.create();
            List<Map.Entry<PWEmbedding, Double>> closest = handler.getClosest(store, query, num_results, Set.of(EmbeddingType.Command));
            System.out.println("Results " + num_results + " " + closest.size());
            for (int i = 0; i < closest.size(); i++) {
                Map.Entry<PWEmbedding, Double> entry = closest.get(i);
                CommandEmbedding embed = (CommandEmbedding) entry.getKey();
                ParametricCallable command = embed.getObj();

                // /command [arguments]
                String mention = Locutus.imp().getSlashCommands().getSlashMention(command.getFullPath());
                String path = command.getFullPath();
                if (mention == null) {
                    mention = "**/" + path + "**";
                }
                String help = command.help(store).replaceFirst(path, "").trim();
                msg.append("__**" + (i + 1) + ".**__ ");
                msg.append(mention);
                if (!help.isEmpty()) {
                    msg.append(" " + help);
                }
                msg.append("\n");
                msg.append("> " + command.simpleDesc().replaceAll("\n", "\n > "));
                msg.append("\n");
            }
            msg.send();
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
