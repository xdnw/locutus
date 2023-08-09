package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.SlashCommandManager;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.NationPlaceholder;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttribute;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.gpt.IEmbeddingDatabase;
import link.locutus.discord.gpt.imps.IText2Text;
import link.locutus.discord.gpt.pwembed.GPTProvider;
import link.locutus.discord.gpt.pwembed.PWGPTHandler;
import link.locutus.discord.gpt.pwembed.ProviderType;
import link.locutus.discord.gpt.test.ExtractText;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.DocPrinter2;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.scheduler.TriConsumer;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

public class GPTCommands {

    @Command(desc = "This command allows you to convert a public Google document (of document type) into a Google spreadsheet of facts.\n" +
            "The output format will have a single column with a header row labeled \"facts.\" Each fact will be standalone and not order dependent.\n" +
            "The information is extracted using the user's configured GPT provider.\n" +
            "When the command is run, the document is added to the queue, and the user will be alerted when the conversion finishes.\n" +
            "Users have the option to check the progress of the conversion using a command.")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS, root = true)
    public synchronized String generate_factsheet(PWGPTHandler gpt, @Me GuildDB db, @Me IMessageIO io, @Me JSONObject command, String googleDocumentUrl, String document_description, @Switch("s") SpreadSheet sheet, @Switch("f") boolean confirm) throws GeneralSecurityException, IOException {
        // to markdown
        String baseUrl = "https://docs.google.com/document/d/";
        if (!googleDocumentUrl.startsWith(baseUrl)) {
            return "Invalid Google Document URL. Expecting `https://docs.google.com/document/d/...`, received: `" + googleDocumentUrl + "`";
        }
        String docId = googleDocumentUrl.substring(baseUrl.length(), googleDocumentUrl.lastIndexOf('/'));
        String markdown = ExtractText.getDocumentMarkdown(docId);

        if (markdown.isEmpty()) {
            return "Document is empty";
        }

        // max length
        int maxLineLength = 200;
        String[] lines = markdown.split("\n");
        for (String line : lines) {
            if (line.length() > maxLineLength) {
                return "Line `" + line + "` is too long (`" + line.length() + "`). Max length is " + maxLineLength + " characters";
            }
        }
        int maxLines = 1000;
        if (lines.length > maxLines) {
            return "Document has too many lines (`" + lines.length + "`). Max lines is " + maxLines;
        }

        if (!confirm) {
            String title = "Generate factsheet?";
            StringBuilder body = new StringBuilder();
            body.append("This will generate a factsheet with the following parameters:\n");
            body.append("Document description: `").append(document_description).append("`\n");
            body.append("Input lines: `").append(lines.length).append("`\n");

            IMessageBuilder msg = io.create();
            msg.file("input.md", markdown);
            msg.confirmation(title, body.toString(), command).send();
            return null;
        }

        List<String> converted = gpt.convertDocument(markdown, document_description);

        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKeys.ANSWER_SHEET);
        }

        // set values in sheet
        List<String> header = new ArrayList<>(Arrays.asList("description"));
        sheet.addRow(header);
        for (String line : converted) {
            header.set(0, line);
            sheet.addRow(header);
        }

        sheet.clear("A:Z");
        sheet.set(0, 0);

        sheet.attach(io.create(), null, false, 0).send();
        return null;
    }

    @Command(desc = "This command provides a list of accessible embedding datasets used for prompting GPT.\n" +
            "Embedding datasets consist of vectors representing text strings, allowing for comparison between different strings.\n" +
            "See: <https://github.com/xdnw/locutus/wiki> or <https://politicsandwar.fandom.com/wiki/Politics_and_War_Wiki>\n" +
            "To view a specific dataset see: /chat embedding view")
    @RolePermission(value = Roles.AI_COMMAND_ACCESS)
    public String list_documents(PWGPTHandler gpt, @Me IMessageIO io, @Me GuildDB db, @Switch("r") boolean listRoot) {
        Set<EmbeddingSource> sources = gpt.getSources(db.getGuild(), listRoot);
        if (sources.isEmpty()) {
            if (!listRoot) {
                io.create().embed("No sources found", "Try `listRoot` to see the default sources")
                        .commandButton(CommandBehavior.DELETE_MESSAGE, CM.chat.embedding.list.cmd.create(Boolean.TRUE + ""), "List Root")
                        .send();
                return null;
            }
            return "No sources found";
        }
        StringBuilder result = new StringBuilder();
        for (EmbeddingSource source : sources) {
            result.append("#" + source.source_id + ": " + source.source_name);
            int numVectors = gpt.getHandler().getEmbeddings().countVectors(source);
            result.append(" - " + numVectors + " vectors");
            // date to string `source.date_added`
            String dateStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - source.date_added);
            result.append(" (added " + dateStr + " ago)\n");
        }
        result.append("\nSee also: " + CM.chat.embedding.view.cmd.toSlashMention());

        return result.toString();
    }

    @Command(desc = "Delete your custom datasets.\n" +
            "Default datasets cannot be deleted, and if a custom dataset is deleted, tasks will fall back to using the base datasets.")
    @RolePermission(value = Roles.ADMIN)
    public String delete_document(PWGPTHandler gpt, @Me GuildDB db, @Me IMessageIO io, @Me JSONObject command, EmbeddingSource source, @Switch("f") boolean force) {
        if (source.guild_id != db.getIdLong()) {
            throw new IllegalArgumentException("Document `" + source.source_name + "` is not owned by this guild");
        }

        // confirm overwrite
        if (!force) {
            String title = "Delete document?";
            StringBuilder body = new StringBuilder();
            body.append("This will delete the document with the name `" + source.source_name + "`.\n");
            int numVectors = gpt.getHandler().getEmbeddings().countVectors(source);
            body.append("Vectors: `").append(numVectors).append("`.\n");
            io.create().confirmation(title, body.toString(), command).send();
            return null;
        }

        gpt.getHandler().getEmbeddings().deleteSource(source);
        return "Deleted document `" + source.source_name + "`";
    }

    @Command(desc = "Save Google spreadsheet contents to a named embedding dataset.\n" +
            "Requires two columns labeled \"fact\" or \"question\" and \"answer\" for vectors.\n" +
            "Search finds nearest fact, or searches questions and returns corresponding answers if two columns.")
    @RolePermission(value = Roles.ADMIN)
    public String view_document(PWGPTHandler handler, @Me IMessageIO io, @Me GuildDB db, EmbeddingSource source, final @Switch("a") boolean getAnswers, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKeys.ANSWER_SHEET);
        }

        List<String> header;
        if (getAnswers) {
            header = new ArrayList<>(Arrays.asList(
                    "question",
                    "answer"
            ));
        } else {
            header = new ArrayList<>(Arrays.asList(
                    "fact"
            ));
        }

        sheet.addRow(header);

        IEmbeddingDatabase embeddings = handler.getHandler().getEmbeddings();
        Set<EmbeddingSource> sources = Collections.singleton(source);
        SpreadSheet finalSheet = sheet;
        embeddings.iterateVectors(sources, new TriConsumer<EmbeddingSource, Long, float[]>() {
            @Override
            public void consume(EmbeddingSource source, Long hash, float[] vector) {
                String text = embeddings.getText(hash);
                header.set(0, text);
                if (getAnswers) {
                    String expanded = embeddings.getExpandedText(source.source_id, hash);
                    header.set(1, expanded);
                }
                finalSheet.addRow(header);
            }
        });

        sheet.clear("A:Z");
        sheet.set(0, 0);

        sheet.attach(io.create(), null, false, 0).send();
        return null;
    }

    @Command(desc = "Save Google spreadsheet contents to a named embedding dataset.\n" +
            "Requires two columns labeled \"fact\" or \"question\" and \"answer\" for vectors.\n" +
            "Search finds nearest fact, or searches questions and returns corresponding answers if two columns.")
    @RolePermission(value = Roles.ADMIN)
    public String save_embeddings(PWGPTHandler gpt, @Me IMessageIO io, @Me JSONObject command, @Me GuildDB db, SpreadSheet sheet, String document_description, @Switch("f") boolean force) {
        document_description = document_description.replaceAll("[^a-z0-9_ ]", "").toLowerCase(Locale.ROOT).trim();
        if (document_description.isEmpty()) {
            throw new IllegalArgumentException("Name must be at least 1 character long and alphanumerical");
        }

        EmbeddingSource source = gpt.getHandler().getEmbeddings().getSource(document_description, db.getIdLong());
        if (source != null) {
            // confirm overwrite
            if (!force) {
                String title = "Overwrite existing document?";
                StringBuilder body = new StringBuilder();
                body.append("This will overwrite the existing document with the same name.\n");
                int numVectors = gpt.getHandler().getEmbeddings().countVectors(source);
                body.append("Vectors: `").append(numVectors).append("`.\n");
                io.create().confirmation(title, body.toString(), command).send();
                return null;
            }
        }

        List<List<Object>> rows = sheet.getAll();
        if (rows.size() < 2){
            throw new IllegalArgumentException("Must have at least 2 rows, a header (`description`, `full_text`) and 1 row of data");
        }
        List<Object> header = rows.get(0);
        String col1 = header.get(0).toString();
        String col2 = header.size() > 1 ? header.get(1).toString() : null;
        if (!col1.equalsIgnoreCase("text")) {
            throw new IllegalArgumentException("First column must be `description`");
        }
        if (col2 != null && !col2.equalsIgnoreCase("full_text")) {
            throw new IllegalArgumentException("Second column must be `full_text`");
        }
        List<String> descriptions = new ArrayList<>();
        List<String> fullTexts = new ArrayList<>();
        // ad all
        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.size() < 1) {
                continue;
            }
            String desc = row.get(0).toString();
            if (desc.isEmpty()) continue;
            String fullText = row.size() > 1 ? row.get(1).toString() : null;
            descriptions.add(desc);
            fullTexts.add(fullText);
        }

        if (source == null) {
            source = gpt.getHandler().getEmbeddings().getOrCreateSource(document_description, db.getIdLong());
        }

        List<Long> embeddings = gpt.getHandler().registerEmbeddings(source, descriptions, fullTexts, true, true);

        return "Registered " + embeddings.size() + " embeddings for `" + document_description + "` See: " + CM.chat.embedding.view.cmd.toSlashMention() + " and " + CM.chat.embedding.list.cmd.toSlashMention();
    }

    @Command(desc = "List available chat providers, and their information.\n" +
            "This includes status, rate limits, execution time, model, permissions, options.")
    @RolePermission(Roles.AI_COMMAND_ACCESS)
    public String listChatProviders(PWGPTHandler pwGpt, @Me GuildDB db, @Me User user) {
        Set<GPTProvider> providers = pwGpt.getProviders(db);

        if (providers.isEmpty()) {
            return "No providers found";
        }

        StringBuilder result = new StringBuilder();
        for (GPTProvider provider : providers) {
            result.append(provider.toString(db, user) + "\n");
        }

        result.append("\n\nSee also: " + CM.chat.providers.set.cmd.toSlashMention() + " and " + CM.chat.providers.configure.cmd.toSlashMention());

        return result.toString();
    }

    @Command(desc = "Configure chat provider types used for conversations.\n" +
            "Settings applies to all new messages.\n" +
            "Use provider list command to view types.")
    @RolePermission(Roles.AI_COMMAND_ACCESS)
    public String setChatProviders(PWGPTHandler pwGpt, @Me GuildDB db, @Me User user, @Me DBNation nation, Set<ProviderType> providerTypes) {
        Set<ProviderType> existing = pwGpt.getProviderTypes(nation);
        // if equal, return
        if (existing.equals(providerTypes)) {
            return "You are already using these providers";
        }

        StringBuilder response = new StringBuilder();
        for (ProviderType type : ProviderType.values()) {
            if (providerTypes.contains(type)) {
                if (!existing.contains(type)) {
                    response.append("Enabled " + type.name() + "\n");
                }
            } else {
                if (existing.contains(type)) {
                    response.append("Disabled " + type.name() + "\n");
                }
            }
        }
        pwGpt.setProviderTypes(nation, providerTypes);

        return response.toString();
    }

    @Command(desc = "Customize options for a chat provider.\n" +
            "Defaults apply if not set.\n" +
            "Configurations for all new messages.\n" +
            "Refer to API docs for details: <https://platform.openai.com/docs/api-reference/chat/create>")
    @RolePermission(Roles.AI_COMMAND_ACCESS)
    public String chatProviderConfigure(PWGPTHandler pwGpt, @Me GuildDB db, @Me User user, @Me DBNation nation, GPTProvider provider, Map<String, String> options) {
        Map<String, Map<String, String>> config = pwGpt.setAndValidateOptions(nation, provider, options);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(config);
        return "Set options for " + provider.getId() + ".\nCurrent configuration:\n```json\n" + json + "\n```";
    }

    @Command(desc = "Resume paused chat provider (i.e. manual/error).\n" +
            "Check provider status with list command.")
    @RolePermission(value = Roles.ADMIN)
    public String chatResume(@Me GuildDB db, @Me User user, GPTProvider provider) {
        if (!provider.checkAdminPermission(db, user, true)) {
            return "You do not have permission to resume this provider";
        }

        if (!provider.isPaused()) {
            return "Provider is not paused:\n```\n" + provider.toString(db, user) + "\n```";
        }
        Throwable e = provider.getPauseError();
        provider.resume();
        String msg = "Resumed provider: `" + provider.getId() + "`.";
        if (e != null) {
            // append previous error information
            msg += "\nPrevious error:\n```\n" + ExceptionUtils.getStackTrace(e) + "\n```";
        }
        return msg;
    }

    @Command(desc = "Pause a chat provider.\n" +
            "Other providers will not be paused.\n" +
            "Halts document conversion using this provider.\n" +
            "Providers may be resumed.")
    @RolePermission(value = Roles.ADMIN)
    public String chatPause(@Me GuildDB db, @Me User user, GPTProvider provider) {
        if (!provider.checkAdminPermission(db, user, true)) {
            return "You do not have permission to pause this provider";
        }

        if (provider.isPaused()) {
            return "Provider is already paused:\n```\n" + provider.toString(db, user) + "\n```";
        }
        provider.pause(new IllegalStateException("Paused by " + user.getAsTag()));
        return "Paused provider: `" + provider.getId() + "`.";
    }

    @Command(desc = "Locate a command you are looking for.\n" +
            "Use keywords for relevant results, or ask a question.")
    @RolePermission(Roles.AI_COMMAND_ACCESS)
    public String find_command2(@Me IMessageIO io, ValueStore store, @Me GuildDB db, @Me User user, String search, @Default String instructions, @Switch("g") boolean useGPT, @Switch("n") Integer numResults) {
        Function<Integer, List<ParametricCallable>> getClosest = integer -> {
            PWGPTHandler pwGpt = Locutus.imp().getCommandManager().getV2().getPwgptHandler();
            return pwGpt.getClosestCommands(store, search, 100);
        };

        Function<ParametricCallable, String> getMention = command -> {
            String mention = SlashCommandManager.getSlashMention(command.getFullPath());
            String path = command.getFullPath();
            if (mention == null) {
                mention = "**/" + path + "**";
            }
            return mention;
        };

        Function<List<String>, ParametricCallable> getCommand = strings -> {
            CommandCallable callable = Locutus.imp().getCommandManager().getV2().getCommands().get(strings);
            return callable instanceof ParametricCallable ? (ParametricCallable) callable : null;
        };

        String footer = "For command usage: " + CM.help.command.cmd.toSlashMention();

        return find_callable(io, store, db, user, search, instructions, useGPT, numResults,
                getClosest,
                getCommand,
                getMention,
                footer);
    }

    public String find_callable(@Me IMessageIO io, ValueStore store, @Me GuildDB db, @Me User user, String search, @Default String instructions, @Switch("g") boolean useGPT, @Switch("n") Integer numResults,
                                      Function<Integer, List<ParametricCallable>> getClosest,
                                      Function<List<String>, ParametricCallable> getCommand,
                                      Function<ParametricCallable, String> getMention,
                                String footer) {
        if (instructions != null) useGPT = true;
        PWGPTHandler pwGpt = Locutus.imp().getCommandManager().getV2().getPwgptHandler();
        if (numResults == null) numResults = 8;
        if (numResults > 25) {
            numResults = 25;
        }
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

        List<ParametricCallable> closest = null;

        DBNation nation = DiscordUtil.getNation(user);
        if (nation != null && useGPT && pwGpt != null) {
            GPTProvider provider = pwGpt.getDefaultProvider(db, user, nation);
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
                for (ParametricCallable command : closest) {
                    if (allowedFull-- <= 0) full = false;
                    String fullText;
                    if (!full) {
                        fullText = null;
                    } else {
                        fullText = "# /" + command.getFullPath() + "\n" +
                                command.toBasicMarkdown(store, null, "/", false, false);
                    }

                    int fullTextLength = 0;
                    if (full && (fullTextLength = provider.getSize(fullText)) <= remaining) {
                        commandTexts.add(fullText);
                        remaining -= fullTextLength;
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

                        if (remaining > shortTextLength) {
                            full = false;
                            commandTexts.add(shortTextStr);
                            remaining -= shortTextLength;
                        } else {
                            break;
                        }
                    }
                }
                prompt = prompt.replace("{commands}", String.join("\n\n", commandTexts));
                System.out.println(prompt);

                Map<String, String> options = pwGpt.getOptions(nation, provider);
                if (provider.getOptions().containsKey("temperature")) {
                    options.putIfAbsent("temperature", "0.5");
                }
                if (provider.getOptions().containsKey("max_tokens")) {
                    options.putIfAbsent("max_tokens", String.valueOf(20 * numResults));
                }

                Future<String> result = provider.submit(db, user, nation, options, prompt);
                String resultStr = FileUtil.get(result);
                System.out.println(resultStr);
                List<String> lines = Arrays.asList(resultStr.split("\n"));


                int error = 0;
                int success = 0;
                List<String> found = new ArrayList<>();
                int lineIndex = 1;
                for (int j = 0; j < lines.size(); j++) {
                    String line = lines.get(j);
                    int index = line.indexOf("/");
                    // skip if index not found
                    if (!(index >= 0)) continue;
                    String commandStr = line.substring(index + 1).trim();
                    List<String> commandPath = Arrays.asList(commandStr.split(" "));
                    // cap at 3
                    if (commandPath.size() > 3) commandPath = commandPath.subList(0, 3);
                    try {
                        ParametricCallable command = getCommand.apply(commandPath);
                        if (command != null) {
                            if (!(command instanceof ParametricCallable)) error++;
                            else success++;
                            String prefix = "__**" + (lineIndex++) + ".**__ ";
                            found.add(prefix + getDescription.apply(command));
                            continue;
                        }
                    } catch (IllegalArgumentException e) {
                        System.out.println(commandStr);
                        e.printStackTrace();
                    }
                    error++;
                }

                if (found.size() > 0 && success > 0) {
                    resultStr = String.join("\n", found);
                    resultStr += "\n\n" + footer;
                    if (error > 0) {
                        resultStr += "\n\n" + "These results may not be accurate. Please try another query, or set `useGPT: False`";
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
            ParametricCallable command = closest.get(i);
            msg.append("__**" + (i + 1) + ".**__ ");
            msg.append(getDescription.apply(command));
            msg.append("\n");
        }
        msg.append("\n\n" + footer);
        msg.send();
        return null;
    }

    @Command(desc = "Locate a nation placeholder you are looking for.\n" +
            "Use keywords for relevant results, or ask a question.")
    @RolePermission(Roles.AI_COMMAND_ACCESS)
    public String find_placeholder(NationPlaceholders placeholders, @Me IMessageIO io, ValueStore store, @Me GuildDB db, @Me User user, String search, @Default String instructions, @Switch("g") boolean useGPT, @Switch("n") Integer numResults) {
        Function<Integer, List<ParametricCallable>> getClosest = integer -> {
            PWGPTHandler pwGpt = Locutus.imp().getCommandManager().getV2().getPwgptHandler();
            return pwGpt.getClosestNationAttributes(store, search, 100);
        };

        Function<ParametricCallable, String> getMention = command -> {
            return "#" + command.getFullPath();
        };

        Function<List<String>, ParametricCallable> getCommand = strings -> {
            String arg = strings.get(0);
            return placeholders.get(arg);
        };

        String footer = DocPrinter2.PLACEHOLDER_HEADER.replaceAll("\n+", "\n");

        return find_callable(io, store, db, user, search, instructions, useGPT, numResults,
                getClosest,
                getCommand,
                getMention,
                footer);
    }



}
