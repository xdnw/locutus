package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.locutus.wiki.game.PWWikiUtil;
import com.vdurmont.emoji.EmojiParser;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.WikiCategory;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.SlashCommandManager;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.config.Messages;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.gpt.IEmbeddingDatabase;
import link.locutus.discord.gpt.imps.ConvertingDocument;
import link.locutus.discord.gpt.imps.DocumentChunk;
import link.locutus.discord.gpt.imps.EmbeddingType;
import link.locutus.discord.gpt.pw.ArgumentEmbeddingAdapter;
import link.locutus.discord.gpt.pw.GPTProvider;
import link.locutus.discord.gpt.pw.GPTSearchUtil;
import link.locutus.discord.gpt.pw.PWGPTHandler;
import link.locutus.discord.gpt.imps.ProviderType;
import link.locutus.discord.gpt.test.ExtractText;
import link.locutus.discord.user.Roles;
import com.locutus.wiki.CommandWikiPages;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.scheduler.TriConsumer;
import link.locutus.discord.util.scheduler.TriFunction;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

public class GPTCommands {

    @Command(desc = "Pause conversion for a google document to a chat dataset\n" +
            "Conversion can be resumed later")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String pauseConversion(PWGPTHandler gpt, @Me User user, @Me IMessageIO io, @Me GuildDB db, EmbeddingSource source) {
        ConvertingDocument document = gpt.getEmbeddings().getConvertingDocument(source.source_id);
        if (document == null) {
            return "No converting document found for `" + source.getQualifiedName() + "`";
        }
        gpt.getConverter().pauseConversion(document, "Manually paused by " + user.getName());
        return "Paused conversion for `" + source.getQualifiedName() + "`\n" +
                "Resume with: " + CM.chat.conversion.resume.cmd.toSlashMention();
    }

    // resume conversion
    @Command(desc = "Resume conversion for a google document to a chat dataset")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String resumeConversion(PWGPTHandler gpt, @Me User user, @Me IMessageIO io, @Me GuildDB db, EmbeddingSource source) {
        ConvertingDocument document = gpt.getEmbeddings().getConvertingDocument(source.source_id);
        if (document == null) {
            return "No converting document found for `" + source.getQualifiedName() + "`";
        }
        String reason = gpt.getConverter().resumeConversion(db, document);
        String response = "Resumed conversion for `" + source.getQualifiedName() + "`";
        if (reason != null) {
            response += "\nPrevious Error: `" + reason + "`";
        }
        return response + "\nPause with " + CM.chat.conversion.pause.cmd.toSlashMention();
    }

    @Command(desc = "Delete a conversion task for a google document to a chat dataset")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String deleteConversion(PWGPTHandler gpt, @Me User user, @Me IMessageIO io, @Me GuildDB db, EmbeddingSource source) {
        ConvertingDocument document = gpt.getEmbeddings().getConvertingDocument(source.source_id);
        if (document == null) {
            return "No converting document found for `" + source.getQualifiedName() + "`";
        }
        gpt.getConverter().pauseConversion(document, "Deleted by " + user.getName());
        gpt.getEmbeddings().deleteDocumentAndChunks(document.source_id);
        return "Deleted conversion for `" + source.getQualifiedName() + "`\n" +
                "This does not delete the dataset. See: " + CM.chat.dataset.delete.cmd.toSlashMention();
    }


    @Command(desc = "Show the documents currently converting to a dataset\n" +
            "Datasets are a list of information that can be used to generate chat responses")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String showConverting(PWGPTHandler gpt, @Me User user, @Me IMessageIO io, @Me GuildDB db, @Switch("r") boolean showRoot, @Switch("a") boolean showOtherGuilds) {
        if (showOtherGuilds && !Roles.ADMIN.hasOnRoot(user)) {
            return "You must be a bot admin to use the `showAll`";
        }
        List<ConvertingDocument> documents2 = gpt.getHandler().getEmbeddings().getUnconvertedDocuments();
        Map<ConvertingDocument, EmbeddingSource> sourceMap = new LinkedHashMap<>();
        for (ConvertingDocument document : documents2) {
            EmbeddingSource source = gpt.getHandler().getEmbeddings().getEmbeddingSource(document.source_id);
            if (source == null) {
                System.out.println("No source found for " + document.source_id);
                continue;
            }
            sourceMap.put(document, source);
        }
        if (!showRoot) {
            sourceMap.entrySet().removeIf(f -> f.getValue().guild_id == 0);
        }
        if (!showOtherGuilds) {
            sourceMap.entrySet().removeIf(f -> f.getValue().guild_id != 0 && f.getValue().guild_id != db.getIdLong());
        }
        if (sourceMap.isEmpty()) {
            return "No documents are currently being converted. See " + CM.chat.dataset.list.cmd.toSlashMention() + " to view documents";
        }
        StringBuilder builder = new StringBuilder(sourceMap.size() + " documents in queue:");
        for (Map.Entry<ConvertingDocument, EmbeddingSource> entry : sourceMap.entrySet()) {
            EmbeddingSource source = entry.getValue();
            ConvertingDocument document = entry.getKey();
            List<DocumentChunk> chunks = gpt.getHandler().getEmbeddings().getChunks(document.source_id);
            int converted = (int) chunks.stream().filter(f -> f.converted).count();

            builder.append("`#" + document.source_id + "` - " + source.source_name);
            if (source.guild_id > 0) {
                builder.append(" - " + DiscordUtil.getGuildName(source.guild_id));
            }
            builder.append("\n- **Added by** " + MarkupUtil.markdownUrl(DiscordUtil.getUserName(document.user), "<" + DiscordUtil.userUrl(document.user, false)) + ">");
            builder.append(" " + DiscordUtil.timestamp(document.date, null));
            if (document.error != null) {
                builder.append("- **Error: **" + document.error);
            } else if (document.converted) {
                builder.append("- **Converted: **COMPLETED");
            } else {
                builder.append("- **Converted: **" + converted + "/" + chunks.size());
            }
        }
        return builder.toString();
    }

    @Command(desc = "Convert a public Google document (of document type) into a Google spreadsheet of facts.\n" +
            "The output format will have a single column with a header row labeled \"facts.\" Each fact will be standalone and not order dependent.\n" +
            "The information is extracted using the user's configured GPT provider.\n" +
            "When the command is run, the document is added to the queue, and the user will be alerted when the conversion finishes.\n" +
            "Users have the option to check the progress of the conversion using a command.")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS, root = true)
    public synchronized String generate_factsheet(PWGPTHandler gpt, @Me GuildDB db, @Me IMessageIO io, @Me User user, @Me JSONObject command, String googleDocumentUrl, String document_name, @Switch("f") boolean force) throws GeneralSecurityException, IOException {
        // if document name is not alphanumerical space under dash
        document_name = document_name.toLowerCase(Locale.ROOT);
        if (!document_name.matches("[a-zA-Z0-9\\s_-]+")) {
            throw new IllegalArgumentException("The `document_name` must be alphanumerical, not `" + document_name + "`");
        }

        // ensure no document exists already
        EmbeddingSource existing = gpt.getEmbeddings().getSource(document_name, db.getIdLong());
        if (existing != null) {
            throw new IllegalArgumentException("A document with the name `" + document_name + "` already exists. Delete it with: " + CM.chat.dataset.delete.cmd.toSlashMention());
        }

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
        int maxLineLength = 1000;
        List<String> lines = new ArrayList<>();
//        String[] lines = markdown.split("\n");
        // split by newline or period space
        for (String line : markdown.split("\n")) {
            if (line.length() > maxLineLength) {
                for (String line2 : line.split("\\. ")) {
                    if (line2.length() > maxLineLength) {
                        return "Line `" + line2 + "` is too long (`" + line2.length() + "`). Max length is " + maxLineLength + " characters";
                    }
                    lines.add(line2);
                }
            } else {
                lines.add(line);
            }
        }
        int maxLines = 1000;
        if (lines.size() > maxLines) {
            return "Document has too many lines (`" + lines.size() + "`). Max lines is " + maxLines;
        }

        if (!force) {
            String title = "Generate factsheet?";
            StringBuilder body = new StringBuilder();
            body.append("This will generate a factsheet with the following parameters:\n");
            body.append("Document name: `").append(document_name).append("`\n");
            body.append("Input lines: `").append(lines.size()).append("`\n");

            IMessageBuilder msg = io.create();
            msg.file("input.md", markdown);
            msg.confirmation(title, body.toString(), command).send();
            return null;
        }

        // Check no document conversion already occuring
        List<ConvertingDocument> documents = gpt.getConverter().getDocumentConversions(db.getGuild());
        if (!documents.isEmpty()) {
            return "You must wait for the current document conversion to finish before starting another one: " + CM.chat.conversion.list.cmd.toSlashMention();
        }

        // User user, Guild guild, ProviderType provider, String documentName, String markdown, String prompt
        ConvertingDocument document = gpt.getConverter().createDocumentAndChunks(user,
                db,
                document_name,
                markdown,
                null,
                ProviderType.OPENAI);

        return "Added document `#" + document.source_id + "` | `" + document_name + "` to the queue. Use " +
                CM.chat.conversion.list.cmd.toSlashMention() + " to view conversion progress, and " +
                CM.chat.dataset.list.cmd.toSlashMention() + " to view completed datasets.";
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
                        .commandButton(CommandBehavior.DELETE_MESSAGE, CM.chat.dataset.list.cmd.create(Boolean.TRUE + ""), "List Root")
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
        result.append("\nSee also: " + CM.chat.dataset.view.cmd.toSlashMention());

        return result.toString();
    }

    @Command(desc = "Delete your custom datasets.\n" +
            "Default datasets cannot be deleted, and if a custom dataset is deleted, tasks will fall back to using the base datasets.")
    @RolePermission(value = Roles.ADMIN)
    public String delete_document(PWGPTHandler gpt, @Me User user, @Me GuildDB db, @Me IMessageIO io, @Me JSONObject command, EmbeddingSource source, @Switch("f") boolean force) {
        if (source.guild_id != db.getIdLong()) {
            throw new IllegalArgumentException("Document `" + source.source_name + "` is owned another guild: `" + DiscordUtil.getGuildName(source.guild_id) + "`");
        }

        if (!force) {
            String title = "Delete document?";
            StringBuilder body = new StringBuilder();
            body.append("This will delete the document with the name `" + source.source_name + "`.\n");
            int numVectors = gpt.getHandler().getEmbeddings().countVectors(source);
            body.append("Vectors: `").append(numVectors).append("`.\n");
            io.create().confirmation(title, body.toString(), command).send();
            return null;
        }

        ConvertingDocument document = gpt.getHandler().getEmbeddings().getConvertingDocument(source.source_id);
        if (document != null) {
            gpt.getConverter().pauseConversion(document, "Deleted by " + user.getName());
            gpt.getEmbeddings().deleteDocumentAndChunks(document.source_id);
        }

        gpt.getHandler().getEmbeddings().deleteSource(source);
        return "Deleted document `" + source.source_name + "`";
    }

    @Command(desc = "Save Google spreadsheet contents to a named embedding dataset.\n" +
            "Requires two columns labeled \"fact\" or \"question\" and \"answer\" for vectors.\n" +
            "Search finds nearest fact, or searches questions and returns corresponding answers if two columns.")
    @RolePermission(value = Roles.ADMIN)
    public String view_document(PWGPTHandler handler, @Me IMessageIO io, @Me GuildDB db, EmbeddingSource source, final @Switch("a") boolean getAnswers, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (source == handler.getSource(EmbeddingType.User_Input)) {
            return "Cannot view " + EmbeddingType.User_Input.name() + " source";
        }
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

        sheet.clearCurrentTab();
        sheet.write();

        sheet.attach(io.create(), "embeddings", null, false, 0).send();
        return null;
    }

    @Command(desc = "Save Google spreadsheet contents to a named embedding dataset.\n" +
            "Requires two columns labeled \"fact\" or \"question\" and \"answer\" for vectors.\n" +
            "Search finds nearest fact, or searches questions and returns corresponding answers if two columns.")
    @RolePermission(value = Roles.ADMIN)
    public String save_embeddings(PWGPTHandler gpt, @Me IMessageIO io, @Me JSONObject command, @Me GuildDB db, SpreadSheet sheet, String document_name, @Switch("f") boolean force) {
        String originalName = document_name;
        document_name = document_name.replaceAll("[^a-z0-9_ ]", "").toLowerCase(Locale.ROOT).trim();
        if (!document_name.equalsIgnoreCase(originalName) || document_name.length() < 1) {
            throw new IllegalArgumentException("Name must be at least 1 character long and alphanumerical");
        }

        EmbeddingSource source = gpt.getHandler().getEmbeddings().getSource(document_name, db.getIdLong());
        if (source == null) {
            source = gpt.getHandler().getEmbeddings().getSource(document_name, 0);
            throw new IllegalArgumentException("Document `" + document_name + "` already exists and is a default source. It cannot be overwritten.");
        }
        if (source != null) {
            if (source.guild_id != db.getIdLong()) {
                throw new IllegalArgumentException("Document `" + source.source_name + "` already exists and is owned by another guild: " + DiscordUtil.getGuildName(source.guild_id) + " (this guild: " + db.getGuild() + ")");
            }
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
            source = gpt.getHandler().getEmbeddings().getOrCreateSource(document_name, db.getIdLong());
        }

        List<Long> embeddings = gpt.getHandler().registerEmbeddings(source, descriptions, fullTexts, true, true);

        return "Registered " + embeddings.size() + " embeddings for `" + document_name + "` See: " + CM.chat.dataset.view.cmd.toSlashMention() + " and " + CM.chat.dataset.list.cmd.toSlashMention();
    }

    @Command(desc = "List available chat providers, and their information.\n" +
            "This includes status, rate limits, execution time, model, permissions, options.")
    @RolePermission(Roles.AI_COMMAND_ACCESS)
    public String listChatProviders(PWGPTHandler pwGpt, @Me GuildDB db, @Me User user) {
        Set<GPTProvider> providers = pwGpt.getProviderManager().getProviders(db);

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
        Set<ProviderType> existing = pwGpt.getProviderManager().getProviderTypes(nation);
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
        pwGpt.getProviderManager().setProviderTypes(nation, providerTypes);

        return response.toString();
    }

    @Command(desc = "Customize options for a chat provider.\n" +
            "Defaults apply if not set.\n" +
            "Configurations for all new messages.\n" +
            "Refer to API docs for details: <https://platform.openai.com/docs/api-reference/chat/create>")
    @RolePermission(Roles.AI_COMMAND_ACCESS)
    public String chatProviderConfigure(PWGPTHandler pwGpt, @Me GuildDB db, @Me User user, @Me DBNation nation, GPTProvider provider, Map<String, String> options) {
        Map<String, Map<String, String>> config = pwGpt.getPlayerGPTConfig().setAndValidateOptions(nation, provider, options);
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

        return GPTSearchUtil.gptSearchParametricCallable(io, store, db, user, search, instructions, useGPT, numResults,
                getClosest,
                getCommand,
                getMention,
                footer);
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

        String footer = CommandWikiPages.PLACEHOLDER_HEADER.replaceAll("\n+", "\n");
        footer += "\nFor detailed info for a specific placeholder: " + CM.help.nation_placeholder.cmd.toSlashMention();
        footer += "\nFor a complete list: <https://github.com/xdnw/locutus/wiki/nation_placeholders>";

        return GPTSearchUtil.gptSearchParametricCallable(io, store, db, user, search, instructions, useGPT, numResults,
                getClosest,
                getCommand,
                getMention,
                footer);
    }

    @Command(desc = "Locate a nation placeholder you are looking for.\n" +
            "Use keywords for relevant results, or ask a question.")
    @RolePermission(Roles.AI_COMMAND_ACCESS)
    public String find_argument(@Me IMessageIO io, ValueStore store, @Me GuildDB db, @Me User user, String search, @Default String instructions, @Switch("g") boolean useGPT, @Switch("n") Integer numResults) {


        Function<Integer, List<Parser>> getClosest = integer -> {
            PWGPTHandler pwGpt = Locutus.imp().getCommandManager().getV2().getPwgptHandler();
            return pwGpt.getClosestArguments(store, search, 100);
        };

        Function<List<String>, Parser> getCommand = strings -> {
            String arg = StringMan.join(strings, " ");
            arg = arg.replaceFirst("[1-8]\\.[ ]", "").trim();
            arg = arg.replace("`", "");
            System.out.println(arg);
            PWGPTHandler pwGpt = Locutus.imp().getCommandManager().getV2().getPwgptHandler();
            ArgumentEmbeddingAdapter adapter = (ArgumentEmbeddingAdapter) pwGpt.getAdapter(EmbeddingType.Argument);
            Parser parser = adapter.getParser(arg);
            if (parser == null) {
                System.out.println("No parser found for " + arg);
                System.out.println("Valid parsers are: ");
                for (Parser value : adapter.getObjectsByHash().values()) {
                    System.out.println("- `" + value.getKey().toSimpleString() + "`");
                }

            }
            return parser;
        };

        TriFunction<Parser, GPTProvider, Integer, Map.Entry<String, Integer>> getPromptText = new TriFunction<Parser, GPTProvider, Integer, Map.Entry<String, Integer>>() {
            @Override
            public Map.Entry<String, Integer> apply(Parser parser, GPTProvider provider, Integer remaining) {
                String text = "# " + parser.getNameDescriptionAndExamples(true, false,true, false);
                int length = provider.getSize(text);
                if (remaining < length) return null;
                return Map.entry(text, length);
            }
        };

        Function<Parser, String> getDescription = new Function<Parser, String>() {
            @Override
            public String apply(Parser parser) {
                return parser.getNameDescriptionAndExamples(true, false, true, false);
            }
        };

        String footer = "For detailed info for a specific argument: " + CM.help.argument.cmd.toSlashMention();
        footer += "\nFor a complete list: <https://github.com/xdnw/locutus/wiki/Arguments>";

        return GPTSearchUtil.gptSearchCommand(
                io, store, db, user, search, instructions, useGPT, numResults,
                getClosest,
                getCommand,
                getPromptText,
                getDescription,
                footer
        );
    }

    @Command(desc = "Remove a nation from the /chat commands deny list\n" +
            "i.e. If they were automatically added for submitting inappropriate content")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String unban(@Me IMessageIO io, @Me JSONObject command, DBNation nation, @Switch("f") boolean force) {
        ByteBuffer modReasonBuf = nation.getMeta(NationMeta.GPT_MODERATED);
        if (modReasonBuf == null) {
            return "Nation is not banned from chat tools";
        }
        if (!force) {
            String modReason = new String(modReasonBuf.array(), StandardCharsets.ISO_8859_1);
            String title = "Confirm unban: " + nation.getName();
            StringBuilder body = new StringBuilder();
            User user = nation.getUser();
            if (user != null) {
                // add mention
                body.append(user.getAsMention() + " | ");
            }
            body.append(nation.getNationUrlMarkup(true) + " | " + nation.getAllianceUrlMarkup(true)).append("\n");
            body.append("Reason: ").append(modReason).append("\n");
            io.create().confirmation(title, body.toString(), command).send();
            return null;
        }
        nation.deleteMeta(NationMeta.GPT_MODERATED);
        return "Unbanned " + nation.getName() + " from chat tools";
    }

    @Command(desc = "Set the data sources you want to use to generate natural language responses for chat queries")
    public String embeddingSelect(@Me IMessageIO io, @Me DBNation nation, @Me Guild guild, @Me GuildDB db, @Me User author,
                                  PWGPTHandler pwGpt,
                                  Set<EmbeddingType> excludeTypes,
                                  @Switch("w") @WikiCategory Set<String> includeWikiCategories,
                                  @Switch("n") @WikiCategory Set<String> excludeWikiCategories,
                                  @Switch("e") Set<EmbeddingSource> excludeSources,
                                  @Switch("a") Set<EmbeddingSource> addSources) {

        Set<EmbeddingSource> selected = pwGpt.setSources(nation, guild, excludeTypes, includeWikiCategories, excludeWikiCategories, excludeSources, addSources);

        StringBuilder body = new StringBuilder();
        body.append("Selected `" + selected.size() + "` sources:\n");
        for (EmbeddingSource source : selected) {
            body.append("- " + source.getQualifiedName() + ": (id=" + source.source_id + ")\n");
        }

        body.append("\nFor a list of all sources: " + CM.chat.dataset.list.cmd.toSlashMention());

        return body.toString();
    }

    @Command(desc = "Bulk rename channels using a google sheet or AI generated emojis\n" +
            "The sheet expects columns `id`, `name` and optionally `description`\n" +
            "If you do not provide a sheet, emojis and descriptions will be generated from the channel names")
    @RolePermission(Roles.ADMIN)
    public void emojifyChannels(@Me JSONObject command, @Me GuildDB db, @Me Guild guild, @Me IMessageIO io, @Me User user, @Me DBNation me, @Default SpreadSheet sheet, @Switch("e") Set<Category> excludeCategories, @Switch("c") Set<Category> includeCategories, @Switch("f") boolean force, @Switch("j") boolean popCultureQuotes) throws GeneralSecurityException, IOException {
        if (sheet != null && (excludeCategories != null || includeCategories != null)) {
            throw new IllegalArgumentException("Cannot specify both a sheet and categories");
        }
        if (excludeCategories != null && includeCategories != null) {
            throw new IllegalArgumentException("Cannot specify both exclude and include categories (pick one)");
        }
        if (popCultureQuotes && sheet != null) {
            throw new IllegalArgumentException("Cannot specify both a `sheet` and `popCultureQuotes` mode. `popCultureQuotes` is only for generating channel topic messages for a sheet.");
        }

        List<String> errors = new ArrayList<>();

        if (sheet == null) {
            List<TextChannel> channels = new ArrayList<>();
            if (excludeCategories != null) {
                for (Category category : guild.getCategories()) {
                    if (excludeCategories.contains(category)) continue;
                    channels.addAll(category.getTextChannels());
                }
            } else if (includeCategories != null) {
                for (Category category : guild.getCategories()) {
                    if (!includeCategories.contains(category)) continue;
                    channels.addAll(category.getTextChannels());
                }
            } else {
                channels.addAll(guild.getTextChannels());
            }
            // skip vc
            channels.removeIf(channel -> channel.getType() == ChannelType.VOICE);
            // skip warcat (channel category lower contains)
            channels.removeIf(channel -> {
                Category cat = channel.getParentCategory();
                if (cat == null) return false;
                String name = cat.getName().toLowerCase(Locale.ROOT);
                return name.contains("warcat") || name.contains("interview") || name.contains("archive");
            });
            // skip ones with emojis
            channels.removeIf(channel -> !EmojiParser.extractEmojis(channel.getName()).isEmpty());

            if (channels.isEmpty()) {
                throw new IllegalArgumentException("""
                        No channels to emojify. The following are excluded:
                        - Channels in `excludeCategories` (if specified)
                        - Channels not in `includeCategories` (if specified)
                        - Voice channels
                        - Channels in the `warcat`, `interview` or `archive` categories
                        - Channels that already have emojis""");
            }
            if (channels.size() > 100) {
                throw new IllegalArgumentException("Too many channels to emojify (" + channels.size() + " > 100). Please specify fewer categories with `includeCategories` or `excludeCategories`");
            }

            PWGPTHandler gpt = Locutus.imp().getCommandManager().getV2().getPwgptHandler();
            if (gpt == null) {
                throw new IllegalStateException("No GPT instance found. Please have the bot owner enable it in the `config.yaml` or specify a `sheet` instead");
            }

            GPTProvider provider = gpt.getProviderManager().getDefaultProvider(db, user, me);

            StringBuilder channelsBuilder = new StringBuilder();
            Category lastCategory = null;
            for (TextChannel channel : channels) {
                Category category = channel.getParentCategory();
                if (category != lastCategory) {
                    channelsBuilder.append(category.getName() + "\n");
                    lastCategory = category;
                }
                channelsBuilder.append("- " + channel.getName()).append("\n");
            }

            String prompt = popCultureQuotes ? Messages.PROMPT_EMOJIFY_QUOTE : Messages.PROMPT_EMOJIFY;
            prompt = prompt.replace("{channels}", channelsBuilder.toString());

            Map<String, String> options = gpt.getPlayerGPTConfig().getOptions(me, provider);
            String result = FileUtil.get(provider.submit(db, user, me, options, prompt));

            Map<String, Set<TextChannel>> channelsBySlug = new LinkedHashMap<>();
            for (TextChannel channel : channels) {
                channelsBySlug.computeIfAbsent(PWWikiUtil.slugify(channel.getName(), false), f -> new LinkedHashSet<>()).add(channel);
            }
            Map<TextChannel, Map.Entry<String, String>> emojis = new LinkedHashMap<>();

            System.out.println("Result: ```\n" + result + "\n```\n");

            for (String line : result.split("\n")) {
                line = line.trim();
                if (line.startsWith("-")) line = line.substring(1).trim();
                if (line.isEmpty()) continue;
                if (!line.contains(":") || !line.contains("|")) {
                    errors.add("Invalid line: `" + line + "`");
                    continue;
                }
                System.out.println("Line " + line);
                String channelName = line.substring(0, line.indexOf(":")).trim();
                String emoji = line.substring(line.indexOf(":") + 1, line.indexOf("|")).trim();
                String desc = line.substring(line.indexOf("|") + 1).trim();

                String slug = PWWikiUtil.slugify(channelName, false);
                if (slug.isEmpty()) {
                    errors.add("Non ascii channel names are not supported: `" + channelName + "`");
                    continue;
                }
                Set<TextChannel> toRename = channelsBySlug.get(slug);
                if (toRename == null) {
                    errors.add("No channel found for `" + slug + "`");
                    continue;
                }

                for (TextChannel channel : toRename) {
                    emojis.put(channel, new AbstractMap.SimpleEntry<>(emoji, desc));
                }
            }

            for (TextChannel channel : channels) {
                if (!emojis.containsKey(channel)) {
                    errors.add("Skipped generating emoji for `" + channel.getName() + "`. Please update the current channels and run again.");
                }
            }

            if (emojis.isEmpty()) {
                io.create().append("No emojis generated. See the attached `errors.txt`")
                        .file("errors.txt", String.join("\n", errors))
                        .send();
            }

            sheet = SpreadSheet.create(db, SheetKeys.RENAME_CHANNELS);
            List<String> header = new ArrayList<>(Arrays.asList(
                    "id",
                    "name",
                    "description"
            ));
            sheet.setHeader(header);
            for (Map.Entry<TextChannel, Map.Entry<String, String>> entry : emojis.entrySet()) {
                TextChannel channel = entry.getKey();
                String emoji = entry.getValue().getKey();
                String desc = channel.getTopic() != null && !channel.getTopic().isEmpty() ? channel.getTopic() : entry.getValue().getValue();

                String newName = emoji + "|" + channel.getName();

                List<String> row = new ArrayList<>(Arrays.asList(
                        channel.getId(),
                        newName,
                        desc
                ));
                sheet.addRow(row);
            }
            sheet.clearFirstTab();
            sheet.write();

        }

        Map<Long, String> rename = new LinkedHashMap<>();
        Map<Long, String> setDesc = new LinkedHashMap<>();

        // read the sheet
        List<List<Object>> rows = sheet.getAll();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("No rows found in sheet");
        }
        List<Object> header = rows.get(0);
        Integer channelIdIndex = null;
        Integer nameIndex = null;
        Integer descIndex = null;
        for (int i = 0; i < header.size(); i++) {
            String cell = String.valueOf(header.get(i));
            if (cell.equalsIgnoreCase("id")) {
                channelIdIndex = i;
            } else if (cell.equalsIgnoreCase("name")) {
                nameIndex = i;
            } else if (cell.equalsIgnoreCase("description")) {
                descIndex = i;
            }
        }
        if (channelIdIndex == null) {
            throw new IllegalArgumentException("No `id` column found in sheet");
        }
        if (nameIndex == null) {
            throw new IllegalArgumentException("No `name` column found in sheet");
        }

        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            String channelId = String.valueOf(row.get(channelIdIndex));
            String name = String.valueOf(row.get(nameIndex)).replace("|", "\u2502");
            String desc = descIndex == null || row.size() <= descIndex || row.get(descIndex) == null ? null : String.valueOf(row.get(descIndex));
            if (desc == null || desc.isEmpty()) desc = null;

            TextChannel channel = guild.getTextChannelById(channelId);
            if (channel == null) {
                errors.add("No channel found for `" + channelId + "`");
                continue;
            }

            rename.put(Long.parseLong(channelId), name);
            if (desc != null) {
                setDesc.put(Long.parseLong(channelId), desc);
            }
        }

        if (rename.isEmpty()) {
            IMessageBuilder msg = io.create().append("No channels to rename. See the attached `errors.txt`");
            if (!errors.isEmpty()) {
                msg = msg.file("errors.txt", String.join("\n", errors));
            }
            msg.send();
        }

        if (!force) {
            String title = "Rename " + rename.size() + " channels?";
            StringBuilder body = new StringBuilder();
            for (Map.Entry<Long, String> entry : rename.entrySet()) {
                // <#id>: name
                body.append("<#").append(entry.getKey()).append(">: ").append(entry.getValue()).append("\n");
            }

            body.append("\n\nReview and edit: " + MarkupUtil.markdownUrl("sheet:RENAME_CHANNELS", sheet.getURL()));

            IMessageBuilder msg = io.create().confirmation(title, body.toString(), CM.channel.rename.bulk.cmd.create("sheet:" + sheet.getSpreadsheetId(), null, null, "true", null));
            if (!errors.isEmpty()) {
                msg = msg.file("errors.txt", String.join("\n", errors));
            }
            msg.send();
            return;
        }

        List<String> changes = new ArrayList<>();
        for (Map.Entry<Long, String> entry : rename.entrySet()) {
            TextChannel channel = guild.getTextChannelById(entry.getKey());
            if (channel == null) {
                errors.add("No channel found for `" + entry.getKey() + "`");
                continue;
            }
            String newName = entry.getValue();
            if (newName.length() > 100) {
                errors.add("Channel name is too long: `" + newName + "`");
                continue;
            }
            if (newName.isEmpty()) {
                errors.add("Channel name is empty");
                continue;
            }
            if (newName.equals(channel.getName())) {
                errors.add("Channel name is the same: `" + newName + "`");
                continue;
            }
            changes.add(channel.getName() + " -> " + newName);
            RateLimitUtil.queue(channel.getManager().setName(newName));

            String desc = setDesc.get(entry.getKey());
            if (desc != null) {
                RateLimitUtil.queue(channel.getManager().setTopic(desc));
            }
        }

        IMessageBuilder msg = io.create();
        if (!changes.isEmpty()) {
            msg.append("Submitted " + changes.size() + " channel renames (updates pending):\n");
            msg.append(String.join("\n", changes));
        }
        if (!errors.isEmpty()) {
            // attach errors.txt
            msg.file("errors.txt", String.join("\n", errors));
        }
        msg.send();
    }

}
