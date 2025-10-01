package link.locutus.discord.gpt.pw;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.gpt.imps.text2text.IText2Text;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.function.Function;
import java.util.function.IntConsumer;

public class GPTSearchUtil {

    public static String gptSearchParametricCallable(@Me IMessageIO io, ValueStore store, @Me GuildDB db, @Me User user, String search, @Default String instructions, @Switch("g") boolean useGPT, @Switch("n") Integer numResults,
                                              Function<Integer, List<ParametricCallable>> getClosest,
                                              Function<String, ParametricCallable> getCommand,
                                              Function<ParametricCallable, String> getMention,
                                              String footer) {
        Function<ParametricCallable, String> getDescription = new Function<ParametricCallable, String>() {
            @Override
            public String apply(ParametricCallable command) {
                StringBuilder msg = new StringBuilder();
                String path = command.getFullPath();
                String help = command.help(store).replaceFirst(path, "").trim();
                if (!help.isEmpty()) {
                    msg.append(" " + help);
                }
                msg.append("\n");
                msg.append(command.simpleDesc());
                return msg.toString().replace("\n", ". ");
            }
        };

        Function<ParametricCallable, String> getName = ParametricCallable::getFullPath;

        return gptSearchCommand(io, store, db, user, search, instructions, useGPT, numResults,
                getClosest,
                getCommand,
                getMention,
                getName,
                getDescription,
                footer);
    }

    public static <T> String gptSearchCommand(@Me IMessageIO io, ValueStore store, @Me GuildDB db, @Me User user, String search, @Default String instructions, @Switch("g") boolean useGPT, @Switch("n") Integer numResults,
            Function<Integer, List<T>> getClosest,
            Function<String, T> getCommand,
            Function<T, String> getMention,
            Function<T, String> getName,
            Function<T, String> getDescription,
            String footer) {


        if (instructions != null) useGPT = true;
        PWGPTHandler pwGpt = Locutus.imp().getCommandManager().getV2().getGptHandler();
        if (numResults == null) numResults = 8;
        if (numResults > 25) {
            numResults = 25;
        }

        List<T> closest = getClosest.apply(numResults);

        DBNation nation = DiscordUtil.getNation(user);
        if (useGPT && pwGpt != null) {
            IText2Text provider = pwGpt.getText2Text();
            if (provider != null) {
                IntConsumer addUsage = pwGpt.getUsageTracker(provider);
                System.out.println("Do reranking for " + search + " with " + closest.size() + " items");
                List<T> reranked = provider.rerank(closest, search, addUsage, getName, getDescription);
                if (reranked != null && !reranked.isEmpty()) {
                    closest = reranked;
                } else {
                    throw new IllegalStateException("Rerank returned no results");
                }
            }
        }

        IMessageBuilder msg = io.create();
        if (useGPT) {
            msg.append("`unable to retrieve GPT results`\n");
        }
        for (int i = 0; i < Math.min(numResults, closest.size()); i++) {
            T command = closest.get(i);
            msg.append("__**" + (i + 1) + ".**__ ");
            msg.append(getMention.apply(command) + "\n> " + getDescription.apply(command).replace("\n", "\n> "));
            msg.append("\n");
        }
        msg.append("\n\n" + footer);
        msg.send();
        return null;
    }
}
