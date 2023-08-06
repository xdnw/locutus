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
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.gpt.imps.IText2Text;
import link.locutus.discord.gpt.pwembed.GPTProvider;
import link.locutus.discord.gpt.pwembed.PWGPTHandler;
import link.locutus.discord.gpt.pwembed.ProviderType;
import link.locutus.discord.gpt.test.ExtractText;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class GPTCommands {
    private final PWGPTHandler gpt;

    public GPTCommands(PWGPTHandler gpt) {
        this.gpt = gpt;
    }

    @Command(desc = "This command allows you to convert a public Google document (of document type) into a Google spreadsheet of facts.\n" +
            "The output format will have a single column with a header row labeled \"facts.\" Each fact will be standalone and not order dependent.\n" +
            "The information is extracted using the user's configured GPT provider.\n" +
            "When the command is run, the document is added to the queue, and the user will be alerted when the conversion finishes.\n" +
            "Users have the option to check the progress of the conversion using a command.")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS, root = true)
    public synchronized String generate_factsheet(@Me GuildDB db, @Me IMessageIO io, @Me JSONObject command, String googleDocumentUrl, String document_description, @Switch("s") SpreadSheet sheet, @Switch("f") boolean confirm) throws GeneralSecurityException, IOException {
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
            "To view a specific dataset see: TODO CM ref.")
    @RolePermission(value = Roles.AI_COMMAND_ACCESS)
    public String list_documents(@Me GuildDB db, @Default boolean listRoot) {
        Set<EmbeddingSource> sources = gpt.getSources(db.getGuild(), listRoot);
        StringBuilder result = new StringBuilder();
        for (EmbeddingSource source : sources) {
            result.append("#" + source.source_id + ": " + source.source_name);
            int numVectors = gpt.getHandler().getEmbeddings().countVectors(source);
            result.append(" - " + numVectors + " vectors");
            // date to string `source.date_added`
            String dateStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - source.date_added);
            result.append(" (added " + dateStr + " ago)\n");
        }
        return result.toString();
    }

    @Command(desc = "Delete your custom datasets.\n" +
            "Default datasets cannot be deleted, and if a custom dataset is deleted, tasks will fall back to using the base datasets.")
    @RolePermission(value = Roles.ADMIN)
    public String delete_document(@Me GuildDB db, @Me IMessageIO io, @Me JSONObject command, EmbeddingSource source, @Switch("f") boolean force) {
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
    @RolePermission(value = Roles.AI_COMMAND_ACCESS)
    public String view_document(@Me IMessageIO io, @Me GuildDB db, EmbeddingSource source) {
        return "TODO";
    }

    @Command(desc = "Save Google spreadsheet contents to a named embedding dataset.\n" +
            "Requires two columns labeled \"fact\" or \"question\" and \"answer\" for vectors.\n" +
            "Search finds nearest fact, or searches questions and returns corresponding answers if two columns.")
    @RolePermission(value = Roles.ADMIN)
    public String save_embeddings(@Me IMessageIO io, @Me JSONObject command, @Me GuildDB db, SpreadSheet sheet, String document_description, @Switch("f") boolean force) {
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

        return "Registered " + embeddings.size() + " embeddings for `" + document_description + "` See: TODO CM ref";
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
    @RolePermission
    public String find_command2(ValueStore store, PWGPTHandler pwGpt, @Me GuildDB db, @Me User user, String search) {
        DBNation nation = DiscordUtil.getNation(user);
        if (nation != null) {
            GPTProvider provider = pwGpt.getDefaultProvider(db, user, nation);
            if (provider != null) {
                int cap = provider.getSizeCap();

                String prompt = """
                        You will return:
                        - Full syntax and description of the commands that the user is looking for
                        - Answers to any questions if you know the answer
                        - Instruct the user to use `/help command_usage` for more information
                          
                        The command list is sorted by text similarity to the query
                        Do not paraphrase, use exact text
                        Do not make anything up
                        
                        User query
                        ```
                        {query}
                        ```
                        
                        Command list
                        ```
                        {commands}
                        ```""";

                prompt = prompt.replace("{query}", search);
                String promptWithoutPlaceholders = prompt.replaceAll("\\{.*?\\}", "");
                int promptLength = provider.getSize(promptWithoutPlaceholders);

                int responseLength = 1572;
                int remaining = cap - responseLength;

                List<String> commandTexts = new ArrayList<>();

                List<ParametricCallable> closest = pwGpt.getClosestCommands(store, search, 100);
                int i = 0;
                boolean full = true;
                for (ParametricCallable command : closest) {
                    i++;
                    if (i > 5) full = false;
                    String fullText = "# " + command.toBasicMarkdown(store, null, "/", false, false);
                    String shortText;
                    {
                        String path = command.getFullPath();
                        String help = command.help(store).replaceFirst(path, "").trim();
                        String desc = command.simpleDesc();
                        shortText = "# " + path;
                        if (!help.isEmpty()) {
                            shortText += " " + help;
                        }
                        if (desc != null && !desc.isEmpty()) {
                            shortText += "\n" + desc;
                        }
                    }

                    int fullTextLength = provider.getSize(fullText);
                    int shortTextLength = provider.getSize(shortText);
                    if (remaining > fullTextLength && full) {
                        commandTexts.add(fullText);
                        remaining -= fullTextLength;
                    } else if (remaining > shortTextLength) {
                        full = false;
                        commandTexts.add(shortText);
                        remaining -= shortTextLength;
                    } else {
                        break;
                    }
                }
                prompt = prompt.replace("{commands}", String.join("\n\n", commandTexts));

                Map<String, String> options = pwGpt.getOptions(nation, provider);

                provider.submit(db, user, null, prompt);
            }
        }
        return "TODO";

    }



}
