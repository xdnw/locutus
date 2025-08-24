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
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.util.scheduler.TriFunction;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Function;

public class GPTSearchUtil {

    public static String gptSearchParametricCallable(@Me IMessageIO io, ValueStore store, @Me GuildDB db, @Me User user, String search, @Default String instructions, @Switch("g") boolean useGPT, @Switch("n") Integer numResults,
                                              Function<Integer, List<ParametricCallable>> getClosest,
                                              Function<List<String>, ParametricCallable> getCommand,
                                              Function<ParametricCallable, String> getMention,
                                              String footer) {
        TriFunction<ParametricCallable, IText2Text, Integer, Map.Entry<String, Integer>> getPromptText = new TriFunction<ParametricCallable, IText2Text, Integer, Map.Entry<String, Integer>>() {
            private boolean full = false;
            private int allowedFull = 5;

            @Override
            public Map.Entry<String, Integer> apply(ParametricCallable command, IText2Text provider, Integer remaining) {
                if (allowedFull-- <= 0) full = false;
                String fullText;
                if (!full) {
                    fullText = null;
                } else {
                    fullText = "# /" + command.getFullPath() + "\n" +
                            command.toBasicMarkdown(store, null, "/", false, false, true);
                }

                int fullTextLength = 0;
                if (full && (fullTextLength = provider.getSize(fullText)) <= remaining) {
                    return KeyValue.of(fullText, fullTextLength);
                } else {
                    StringBuilder shortText = new StringBuilder("# /");
                    {
                        String path = command.getFullPath();
                        String help = command.help(store).replaceFirst(path, "").trim();
                        String desc = command.simpleDesc();
                        shortText.append(path);
                        if (!help.isEmpty()) {
                            shortText.append("\n").append(help);
                        }
                        if (desc != null && !desc.isEmpty()) {
                            shortText.append("\n").append(desc);
                        }
                    }

                    String shortTextStr = shortText.toString();
                    int shortTextLength = provider.getSize(shortTextStr);
                    if (shortTextLength <= remaining) {
                        return KeyValue.of(shortTextStr, shortTextLength);
                    } else {
                        return null;
                    }
                }
            }
        };

        Function<ParametricCallable, String> getDescription = new Function<ParametricCallable, String>() {
            @Override
            public String apply(ParametricCallable command) {
                StringBuilder msg = new StringBuilder();
                String path = command.getFullPath();
                String help = command.help(store).replaceFirst(path, "").trim();
                msg.append(getMention.apply(command));
                if (!help.isEmpty()) {
                    msg.append(" " + help);
                }
                msg.append("\n");
                msg.append("> " + command.simpleDesc().replaceAll("\n", "\n > "));
                msg.append("\n");
                return msg.toString();
            }
        };

        return gptSearchCommand(io, store, db, user, search, instructions, useGPT, numResults, getClosest, getCommand, getPromptText, getDescription, footer);
    }

    public static <T> String gptSearchCommand(@Me IMessageIO io, ValueStore store, @Me GuildDB db, @Me User user, String search, @Default String instructions, @Switch("g") boolean useGPT, @Switch("n") Integer numResults,
            Function<Integer, List<T>> getClosest,
            Function<List<String>, T> getCommand,
            TriFunction<T, IText2Text, Integer, Map.Entry<String, Integer>> getPromptText,
            Function<T, String> getDescription,
            String footer) {


        if (instructions != null) useGPT = true;
        PWGPTHandler pwGpt = Locutus.imp().getCommandManager().getV2().getPwgptHandler();
        if (numResults == null) numResults = 8;
        if (numResults > 25) {
            numResults = 25;
        }

        List<T> closest = null;

        DBNation nation = DiscordUtil.getNation(user);
        if (nation != null && useGPT && pwGpt != null) {
            GptLimitTracker tracker = pwGpt.getProviderManager().getDefaultProvider(db, user, nation);
            IText2Text provider = pwGpt.getHandler().getText2Text();
            if (provider != null) {
                closest = getClosest.apply(100);
                int cap = provider.getSizeCap();

                String prompt = """
                        The user is looking for a command.
                        You will return the {num_results} commands that most satisfy their query.
                        Do not return any command syntax or description, just the command names.
                        There is a list below of all available commands.
                        
                        Respond with a format like this:
                        1. /register
                        2. /war find raid
                        ...
                        {num_results}. /chat provider set
                        
                        {instructions}
                        User query
                        ```
                        {query}
                        ```
                        
                        Command list
                        ```
                        {commands}
                        ```
                        
                        Response:""";

                prompt = prompt.replace("{query}", search);
                prompt = prompt.replace("{num_results}", numResults.toString());
                String instructionsStr = instructions == null ? "" : "My Instructions: " + instructions + "\n";
                prompt = prompt.replace("{instructions}", instructionsStr);

                String promptWithoutPlaceholders = prompt.replaceAll("\\{.*?\\}", "");
                int promptLength = provider.getSize(promptWithoutPlaceholders);

                int responseLength = 1572;
                int remaining = cap - responseLength - promptLength;

                List<String> commandTexts = new ArrayList<>();

                int allowedFull = 5;
                boolean full = true;
                for (T command : closest) {
                    Map.Entry<String, Integer> textPair = getPromptText.apply(command, provider, remaining);

                    if (textPair == null) {
                        break;
                    }
                    commandTexts.add(textPair.getKey());
                    remaining -= textPair.getValue();
                }
                prompt = prompt.replace("{commands}", String.join("\n\n", commandTexts));
                System.out.println(prompt);

                Future<String> result = tracker.submit(db, user, nation, prompt);
                String resultStr = FileUtil.get(result);
                System.out.println(resultStr);
                List<String> lines = Arrays.asList(resultStr.split("\n"));


                int error = 0;
                int success = 0;
                List<String> found = new ArrayList<>();
                int lineIndex = 1;
                for (String line : lines) {
                    if (line.length() < 4) continue;
                    int dotIndex = line.indexOf('.');
                    if ((dotIndex != 1 && dotIndex != 2) || !Character.isDigit(line.charAt(0))) {
                        continue;
                    }
                    line = line.substring(dotIndex + 1).trim();
                    line = line.replace("`", "");
                    if (line.startsWith("/")) {
                        line = line.substring(1).trim();
                    }
                    List<String> commandPath = Arrays.asList(line.split(" "));
                    // cap at 3
                    if (commandPath.size() > 3) commandPath = commandPath.subList(0, 3);
                    try {
                        T command = getCommand.apply(commandPath);
                        if (command != null) {
                            success++;
                            String prefix = "__**" + (lineIndex++) + ".**__ ";
                            found.add(prefix + getDescription.apply(command));
                            continue;
                        }
                    } catch (IllegalArgumentException e) {
                        System.out.println(line);
                        e.printStackTrace();
                    }
                    error++;
                }

                if (found.size() > 0 && success > 0) {
                    resultStr = String.join("\n", found);
                    resultStr += "\n\n" + footer;
                    if (error > 0) {
                        resultStr += """
                                
                                
                                These results may not be accurate. Please try another query, or set `useGPT: False`""";
                    }
                    return resultStr;
                } else {
                    System.out.println(String.join("\n", lines));
                }
            }
        }

        if (closest == null) {
            closest = getClosest.apply(numResults);
        }
        IMessageBuilder msg = io.create();
        if (useGPT) {
            msg.append("`unable to retrieve GPT results`\n");
        }
        for (int i = 0; i < Math.min(numResults, closest.size()); i++) {
            T command = closest.get(i);
            msg.append("__**" + (i + 1) + ".**__ ");
            msg.append(getDescription.apply(command));
            msg.append("\n");
        }
        msg.append("\n\n" + footer);
        msg.send();
        return null;
    }
}
