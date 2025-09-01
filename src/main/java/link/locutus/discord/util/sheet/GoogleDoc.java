package link.locutus.discord.util.sheet;

import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.*;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.gpt.test.ExtractText;
import org.apache.commons.collections4.map.PassiveExpiringMap;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoogleDoc {
    public static String parseId(String id) {
        if (id.startsWith("document:")) {
            id = id.split(":")[1];
        } else if (id.startsWith("https://docs.google.com/document/")){
            String regex = "([a-zA-Z0-9-_]{30,})";
            Matcher m = Pattern.compile(regex).matcher(id);
            m.find();
            id = m.group();
        }
        return id.split("/")[0];
    }

    public static boolean isDoc(String arg) {
        return arg.startsWith("https://docs.google.com/document/") || arg.startsWith("document:");
    }

    public static GoogleDoc create(GuildDB db, SheetKey key, String titleOrNull) throws GeneralSecurityException, IOException {
        if (titleOrNull == null) {
            if (key == null || db == null) {
                throw new IllegalArgumentException("A guild, key, or title must be provided to create a new document.");
            }
        }
        String documentId = key == null ? null : db.getInfo(key, true);
        String defTitle = titleOrNull != null ? titleOrNull : db.getGuild().getId() + "." + key.name();
        Docs api = null;
        try {
            api = SheetUtil.getDocsService();

            Document document = new Document()
                    .setTitle(defTitle);

            document = api.documents().create(document)
                    .setFields("documentId")
                    .execute();

            documentId = document.getDocumentId();
            if (key != null && db != null) db.setInfo(key, documentId);
            if (documentId != null) {
                try {
                    DriveFile gdFile = new DriveFile(documentId);
                    gdFile.shareWithAnyone(DriveFile.DriveRole.WRITER);
                } catch (GeneralSecurityException | IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            documentId = UUID.randomUUID().toString();
        }
        return create(documentId, api, defTitle);
    }

    public static GoogleDoc create(String id) throws GeneralSecurityException, IOException {
        Docs api = null;
        try {
            api = SheetUtil.getDocsService();
        } catch (IOException _) {}
        return create(id, api, "");
    }

    private static GoogleDoc create(String id, Docs api, String title) throws GeneralSecurityException, IOException {
        id = parseId(id);
        {
            GoogleDoc cached = CACHE.get(id);
            if (cached != null) return cached;
        }
        GoogleDoc doc = new GoogleDoc(id, api, title);
        return doc;
    }

    private static final PassiveExpiringMap<String, GoogleDoc> CACHE = new PassiveExpiringMap<>(5, TimeUnit.MINUTES);

    private Docs service;
    private StringBuilder content;
    private String documentId;
    private GoogleDoc(String id, Docs api, String title) throws GeneralSecurityException, IOException {
        if (id != null) {
            this.service = api;
            this.documentId = parseId(id);
        }
        this.content = new StringBuilder();
    }

    public void append(String text) {
        content.append(text);
    }

    public void clear() {
        this.content.setLength(0);
    }

    public void write() throws IOException {
        if (service == null) {
            return;
        }
        try {
            String html = content.toString();
            if (html.isEmpty()) {
                return;
            }
            List<Request> requests = new ArrayList<>();

//            // get the current document content
            Document document = getDocument();
            // get content length

            List<StructuralElement> elems = document.getBody().getContent();
            if (!elems.isEmpty()) {
                int endIndex = elems.get(elems.size() - 1).getEndIndex();
                if (endIndex > 2) {
                    requests.add(new Request().setDeleteContentRange(new DeleteContentRangeRequest()
                            .setRange(new Range()
                                    .setStartIndex(1)
                                    .setEndIndex(endIndex - 1))));
                }
            }
            requests.add(new Request().setInsertText(new InsertTextRequest()
                    .setText(html)
                    .setLocation(new Location()
                            .setIndex(1))
            ));
            service.documents().batchUpdate(documentId, new BatchUpdateDocumentRequest()
                    .setRequests(requests)).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String readHtml() throws GeneralSecurityException, IOException {
        if (service == null) {
            return content.toString();
        }
        return ExtractText.getDocumentHtml(documentId);
    }

    public String getUrl() {
        return "https://docs.google.com/document/d/" + documentId + "/edit";
    }

    public Document getDocument() throws IOException {
        return service == null ? null : service.documents().get(documentId).execute();
    }

    public String getTitle() throws IOException {
        Document doc = getDocument();
        return doc == null ? null : doc.getTitle();
    }
}
