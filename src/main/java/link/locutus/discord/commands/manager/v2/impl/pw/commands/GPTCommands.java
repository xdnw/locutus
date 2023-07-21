package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import com.google.gson.JsonObject;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.gpt.pwembed.PWGPTHandler;
import link.locutus.discord.gpt.test.ExtractText;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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

    @Command
    @RolePermission(value = Roles.INTERNAL_AFFAIRS, root = true)
    public synchronized String generate_factsheet(@Me GuildDB db, @Me IMessageIO io, JSONObject command, String googleDocumentUrl, String document_description, @Switch("s") SpreadSheet sheet, @Switch("f") boolean confirm) throws GeneralSecurityException, IOException {
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

    @Command
    @RolePermission(value = Roles.ADMIN)
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

    @Command
    @RolePermission(value = Roles.ADMIN)
    public String delete_document(@Me GuildDB db, @Me IMessageIO io, @Me JSONObject command, String name, @Switch("f") boolean force) {
        name = name.replaceAll("[^a-z0-9_]", "").toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Name must be at least 1 character long and alphanumerical");
        }

        EmbeddingSource source = gpt.getHandler().getEmbeddings().getSource(name, db.getIdLong());
        if (source == null) {
            throw new IllegalArgumentException("No document with name `" + name + "` found");
        }

        // confirm overwrite
        if (!force) {
            String title = "Delete document?";
            StringBuilder body = new StringBuilder();
            body.append("This will delete the document with the name `" + name + "`.\n");
            int numVectors = gpt.getHandler().getEmbeddings().countVectors(source);
            body.append("Vectors: `").append(numVectors).append("`.\n");
            io.create().confirmation(title, body.toString(), command).send();
            return null;
        }

        gpt.getHandler().getEmbeddings().deleteSource(source);
        return "Deleted document `" + name + "`";
    }

    @Command
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
}
