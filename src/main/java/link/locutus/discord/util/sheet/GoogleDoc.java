package link.locutus.discord.util.sheet;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.DocsScopes;
import com.google.api.services.docs.v1.model.BatchUpdateDocumentRequest;
import com.google.api.services.docs.v1.model.DeleteContentRangeRequest;
import com.google.api.services.docs.v1.model.DeleteNamedRangeRequest;
import com.google.api.services.docs.v1.model.Document;
import com.google.api.services.docs.v1.model.DocumentStyle;
import com.google.api.services.docs.v1.model.InsertTextRequest;
import com.google.api.services.docs.v1.model.Location;
import com.google.api.services.docs.v1.model.NamedRanges;
import com.google.api.services.docs.v1.model.Range;
import com.google.api.services.docs.v1.model.ReplaceAllTextRequest;
import com.google.api.services.docs.v1.model.Request;
import com.google.api.services.docs.v1.model.StructuralElement;
import com.google.api.services.docs.v1.model.SubstringMatchCriteria;
import com.google.api.services.docs.v1.model.UpdateDocumentStyleRequest;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.gpt.test.ExtractText;
import org.apache.commons.collections4.map.PassiveExpiringMap;

import javax.print.Doc;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoogleDoc {
    private static final String APPLICATION_NAME = "DriveFile";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    protected static final String TOKENS_DIRECTORY_PATH = "config" + java.io.File.separator + "tokens2";
    private static final String CREDENTIALS_FILE_PATH = "config" + java.io.File.separator + "credentials-drive.json";

    private static final List<String> SCOPES = Collections.singletonList(DocsScopes.DOCUMENTS);

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

    private static Docs getServiceAPI() throws GeneralSecurityException, IOException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        return  new Docs.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public static boolean isDoc(String arg) {
        return arg.startsWith("https://docs.google.com/document/") || arg.startsWith("document:");
    }

    public static File getCredentialsFile() {
        return new File(CREDENTIALS_FILE_PATH);
    }

    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        File file = getCredentialsFile();
        if (!file.exists()) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        try (InputStream in = new FileInputStream(file)) {
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

            // Build flow and trigger user authorization request.
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                    .setAccessType("offline")
                    .build();
            LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(Settings.INSTANCE.WEB.GOOGLE_SHEET_VALIDATION_PORT).build();
            return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        }
    }

    public static boolean credentialsExists() {
        return getCredentialsFile().exists();
    }

    public static GoogleDoc create(GuildDB db, SheetKeys key, String titleOrNull) throws GeneralSecurityException, IOException {
        String documentId = key == null ? null : db.getInfo(key, true);
        String defTitle = titleOrNull != null ? titleOrNull : db.getGuild().getId() + "." + key.name();

        Docs api = null;

        System.out.println("Credentials " + credentialsExists());
        if (credentialsExists()) {
            if (documentId == null) {
                // Set the title of the document
                Document document = new Document()
                        .setTitle(defTitle);

                api = getServiceAPI();
                document = api.documents().create(document)
                        .setFields("documentId")
                        .execute();

                documentId = document.getDocumentId();
                if (key != null) db.setInfo(key, documentId);
            }
            if (documentId != null) {
                DriveFile gdFile = new DriveFile(documentId);
                try {
                    gdFile.shareWithAnyone(DriveFile.DriveRole.WRITER);
                } catch (GoogleJsonResponseException | TokenResponseException e) {
                    e.printStackTrace();
                }
            }
        } else {
            documentId = UUID.randomUUID().toString();
        }

        return create(documentId, api, defTitle);
    }

    public static GoogleDoc create(String id) throws GeneralSecurityException, IOException {
        return create(id, credentialsExists() ? getServiceAPI() : null, "");
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

    private static final PassiveExpiringMap<String, GoogleDoc> CACHE = new PassiveExpiringMap<String, GoogleDoc>(5, TimeUnit.MINUTES);

    private Docs service;
    private StringBuilder content;
    private String documentId;
    private GoogleDoc(String id, Docs api, String title) throws GeneralSecurityException, IOException {
        if (id != null) {
            if (api == null && credentialsExists()) api = getServiceAPI();
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
