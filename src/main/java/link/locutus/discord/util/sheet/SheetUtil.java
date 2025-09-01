package link.locutus.discord.util.sheet;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.DocsScopes;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.ExtendedValue;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.Activity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.TimeUtil;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class SheetUtil {

    private static final File SHEET_CREDENTIALS_PATH = new File("config" + java.io.File.separator + "credentials-sheets.json");
    private static final File DRIVE_CREDENTIALS_PATH = new File("config" + java.io.File.separator + "credentials-drive.json");
    private static final File DOCS_CREDENTIALS_PATH = new File("config" + java.io.File.separator + "credentials-drive.json");

    private static final File SHEET_TOKENS_PATH = new File("config" + java.io.File.separator + "tokens");
    private static final File DRIVE_TOKENS_PATH = new File("config" + java.io.File.separator + "tokens2");
    private static final File DOCS_TOKENS_PATH = new File("config" + java.io.File.separator + "tokens2");

    private static final List<String> SHEET_SCOPES = List.of(SheetsScopes.SPREADSHEETS);
    private static final List<String> DRIVE_SCOPES = List.of(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_APPDATA, DriveScopes.DRIVE_METADATA, DriveScopes.DRIVE);
    private static final List<String> DOCS_SCOPES = List.of(DocsScopes.DOCUMENTS);

    public static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private static volatile boolean initSheetAdv = false;
    private static GoogleCredentials sheetAdvCredentials = null;

    private static volatile NetHttpTransport HTTP_TRANSPORT = null;

    public static NetHttpTransport getHttpTransport() {
        if (HTTP_TRANSPORT == null) {
            synchronized (SheetUtil.class) {
                if (HTTP_TRANSPORT == null) {
                    try {
                        HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
                    } catch (GeneralSecurityException | IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return HTTP_TRANSPORT;
    }

    public enum RequestType {
        SHEETS,
        DRIVE,
        DOCS
    }

    public static <T> T executeRequest(RequestType type, Callable<T> supplier) {
        try {
            return supplier.call();
        } catch (GoogleJsonResponseException gje) {
            // choose scopes and credential file based on request type
            List<String> scopes = SHEET_SCOPES;
            File credFile = SHEET_CREDENTIALS_PATH;
            switch (type) {
                case DRIVE:
                    scopes = DRIVE_SCOPES;
                    credFile = DRIVE_CREDENTIALS_PATH;
                    break;
                case DOCS:
                    scopes = DOCS_SCOPES;
                    credFile = DOCS_CREDENTIALS_PATH;
                    break;
                case SHEETS:
                default:
                    scopes = SHEET_SCOPES;
                    credFile = SHEET_CREDENTIALS_PATH;
                    break;
            }

            // build a copy of scopes for the gcloud hint; gcloud may require additional scopes (e.g. cloud-platform for Drive)
            List<String> scopesForLogin = new ArrayList<>(scopes);
            {
                final String CLOUD_PLATFORM = "https://www.googleapis.com/auth/cloud-platform";
                if (!scopesForLogin.contains(CLOUD_PLATFORM)) {
                    scopesForLogin.add(CLOUD_PLATFORM);
                }
            }

            String scopesCsv = String.join(",", scopesForLogin);
            String tutorialUrl = "https://cloud.google.com/sdk/gcloud/reference/auth/application-default/login";
            String hint = "\n\nFix: run `gcloud auth application-default login --scopes=" + scopesCsv + "` " +
                    "and ensure the application default credentials are available (or place the JSON in `" + credFile.getPath() + "`). " +
                    "See " + tutorialUrl + " for details.";

            String details = gje.getDetails() != null ? gje.getDetails().toString() : gje.getMessage();
            throw new RuntimeException("Google API error: " + gje.getStatusMessage() + " (" + gje.getStatusCode() + "). " + details + hint, gje);
        } catch (Exception e) {
            // preserve existing behavior converting checked exceptions to runtime
            throw new RuntimeException(e);
        }
    }

    public static Sheets getSheetService() throws IOException {
        final int TIMEOUT_MS = 240_000;

        GoogleCredentials adcCredential = getSheetAdcCredentials();
        if (adcCredential != null) {
            final HttpRequestInitializer adcAdapter = new HttpCredentialsAdapter(adcCredential);
            HttpRequestInitializer requestInitializer = new HttpRequestInitializer() {
                @Override
                public void initialize(final HttpRequest httpRequest) throws IOException {
                    adcAdapter.initialize(httpRequest);
                    httpRequest.setConnectTimeout(TIMEOUT_MS);
                    httpRequest.setReadTimeout(TIMEOUT_MS);
                }
            };
            return new Sheets.Builder(getHttpTransport(), JSON_FACTORY, requestInitializer)
                    .setApplicationName("Spreadsheet")
                    .build();
        }

        Credential jsonCredential = getSheetJsonCredentials();
        if (jsonCredential == null) {
            throw new IOException("No Google credentials available for Sheets");
        }
        HttpRequestInitializer requestInitializer = new HttpRequestInitializer() {
            @Override
            public void initialize(final HttpRequest httpRequest) throws IOException {
                jsonCredential.initialize(httpRequest);
                httpRequest.setConnectTimeout(TIMEOUT_MS);
                httpRequest.setReadTimeout(TIMEOUT_MS);
            }
        };
        return new Sheets.Builder(getHttpTransport(), JSON_FACTORY, requestInitializer)
                .setApplicationName("Spreadsheet")
                .build();
    }

    public static Drive getDriveService() throws IOException {
        final int TIMEOUT_MS = 240_000;

        GoogleCredentials adcCredential = getDriveAdcCredentials();
        if (adcCredential != null) {
            final HttpRequestInitializer adcAdapter = new HttpCredentialsAdapter(adcCredential);
            HttpRequestInitializer requestInitializer = new HttpRequestInitializer() {
                @Override
                public void initialize(final HttpRequest httpRequest) throws IOException {
                    adcAdapter.initialize(httpRequest);
                    httpRequest.setConnectTimeout(TIMEOUT_MS);
                    httpRequest.setReadTimeout(TIMEOUT_MS);
                }
            };
            return new Drive.Builder(getHttpTransport(), JSON_FACTORY, requestInitializer)
                    .setApplicationName("DriveFile")
                    .build();
        }

        Credential jsonCredential = getDriveJsonCredentials();
        if (jsonCredential == null) {
            throw new IOException("No Google credentials available for Drive");
        }
        HttpRequestInitializer requestInitializer = new HttpRequestInitializer() {
            @Override
            public void initialize(final HttpRequest httpRequest) throws IOException {
                jsonCredential.initialize(httpRequest);
                httpRequest.setConnectTimeout(TIMEOUT_MS);
                httpRequest.setReadTimeout(TIMEOUT_MS);
            }
        };
        return new Drive.Builder(getHttpTransport(), JSON_FACTORY, requestInitializer)
                .setApplicationName("DriveFile")
                .build();
    }

    public static Credential getDriveJsonCredentials() throws IOException {
        if (DRIVE_CREDENTIALS_PATH.exists()) {
            return getCredentials(getHttpTransport(), DRIVE_CREDENTIALS_PATH, DRIVE_TOKENS_PATH, DRIVE_SCOPES);
        }
        return null;
    }

    public static Docs getDocsService() throws IOException {
        final int TIMEOUT_MS = 240_000;

        GoogleCredentials adcCredential = getDocsAdcCredentials();
        if (adcCredential != null) {
            final HttpRequestInitializer adcAdapter = new HttpCredentialsAdapter(adcCredential);
            HttpRequestInitializer requestInitializer = new HttpRequestInitializer() {
                @Override
                public void initialize(final HttpRequest httpRequest) throws IOException {
                    adcAdapter.initialize(httpRequest);
                    httpRequest.setConnectTimeout(TIMEOUT_MS);
                    httpRequest.setReadTimeout(TIMEOUT_MS);
                }
            };
            return new Docs.Builder(getHttpTransport(), JSON_FACTORY, requestInitializer)
                    .setApplicationName("DriveFile")
                    .build();
        }

        Credential jsonCredential = getDocsJsonCredentials();
        if (jsonCredential == null) {
            throw new IOException("No Google credentials available for Docs");
        }
        HttpRequestInitializer requestInitializer = new HttpRequestInitializer() {
            @Override
            public void initialize(final HttpRequest httpRequest) throws IOException {
                jsonCredential.initialize(httpRequest);
                httpRequest.setConnectTimeout(TIMEOUT_MS);
                httpRequest.setReadTimeout(TIMEOUT_MS);
            }
        };
        return new Docs.Builder(getHttpTransport(), JSON_FACTORY, requestInitializer)
                .setApplicationName("Docs")
                .build();
    }

    public static GoogleCredentials getSheetAdcCredentials() {
        if (sheetAdvCredentials != null) {
            return sheetAdvCredentials;
        }
        if (!initSheetAdv) {
            synchronized (SHEET_TOKENS_PATH) {
                if (!initSheetAdv) {
                    try {
                        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
                        return sheetAdvCredentials = credentials.createScoped(SHEET_SCOPES);
                    } catch (IOException e) {
                        return null;
                    }
                }
            }
        }
        return sheetAdvCredentials;
    }

    public static Credential getSheetJsonCredentials() throws IOException {
        if (SHEET_CREDENTIALS_PATH.exists()) {
            return getCredentials(getHttpTransport(), SHEET_CREDENTIALS_PATH, SHEET_TOKENS_PATH, SHEET_SCOPES);
        }
        return null;
    }

    private static volatile boolean initDocsAdc = false;
    private static GoogleCredentials docsAdcCredentials = null;

    public static GoogleCredentials getDocsAdcCredentials() {
        if (docsAdcCredentials != null) {
            return docsAdcCredentials;
        }
        if (!initDocsAdc) {
            synchronized (DOCS_TOKENS_PATH) {
                if (!initDocsAdc) {
                    try {
                        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
                        return docsAdcCredentials = credentials.createScoped(DOCS_SCOPES);
                    } catch (IOException e) {
                        return null;
                    }
                }
            }
        }
        return docsAdcCredentials;
    }

    public static Credential getDocsJsonCredentials() throws IOException {
        if (DOCS_CREDENTIALS_PATH.exists()) {
            return getCredentials(getHttpTransport(), DOCS_CREDENTIALS_PATH, DOCS_TOKENS_PATH, DOCS_SCOPES);
        }
        return null;
    }

    private static volatile boolean initDriveAdc = false;
    private static GoogleCredentials driveAdcCredentials = null;

    public static GoogleCredentials getDriveAdcCredentials() {
        if (driveAdcCredentials != null) {
            return driveAdcCredentials;
        }
        if (!initDriveAdc) {
            synchronized (DRIVE_TOKENS_PATH) {
                if (!initDriveAdc) {
                    try {
                        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
                        return driveAdcCredentials = credentials.createScoped(DRIVE_SCOPES);
                    } catch (IOException e) {
                        return null;
                    }
                }
            }
        }

        return driveAdcCredentials;
    }

    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT, File keyFile, File tokenDirectory, List<String> scopes) throws IOException {
        // Load client secrets.
        if (!keyFile.exists()) {
            throw new FileNotFoundException("Resource not found: " + keyFile);
        }
        try (InputStream in = new FileInputStream(keyFile)) {
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

            // Build flow and trigger user authorization request.
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, scopes)
                    .setDataStoreFactory(new FileDataStoreFactory(tokenDirectory))
                    .setAccessType("offline")
                    .build();
            LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(Settings.INSTANCE.WEB.GOOGLE_SHEET_VALIDATION_PORT).build();
            return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        }
    }

    public static String getLetter(int x) {
        x++;
        String letter = "";
        while (x > 0) {
            int r = (x - 1) % 26;
            int n = (x - 1) / 26;
            letter = ((char) ('A' + r)) + letter;
            x = n;
        }
        return letter;
    }

    public static int getIndex(String column) {
        column = column.toUpperCase();
        int out = 0, len = column.length();
        for (int pos = 0; pos < len; pos++) {
            out += (column.charAt(pos) - 64) * Math.pow(26, len - pos - 1);
        }
        return out;
    }

    public static String getRange(int x, int y) {
        return getLetter(x) + (y + 1);
    }

    public static String getRange(int x1, int y1, int x2, int y2) {
        return getRange(x1, y1) + ":" + getRange(x2, y2);
    }

    public static RowData toRowData(List myList) {
        RowData row = new RowData();
        ArrayList<CellData> cellData = new ArrayList<CellData>();
        for (Object obj : myList) {
            if (obj == null) cellData.add(null);

            CellData cell = new CellData();
            String str = obj.toString();
            if (str.startsWith("=")) {
                cell.setUserEnteredValue(new ExtendedValue().setFormulaValue(str));
            } else {
                cell.setUserEnteredValue(new ExtendedValue().setStringValue(str));
            }
            cellData.add(cell);

        }
        row.setValues(cellData);
        return row;
    }

    public static void writeTargets(SpreadSheet sheet, Map<DBNation, List<DBNation>> targets, int turn) throws IOException {
        List<RowData> rowData = new ArrayList<RowData>();

        List<Object> header = new ArrayList<>(Arrays.asList(
                "alliance",
                "nation",
                "cities",
                "infra",
                "soldiers",
                "tanks",
                "planes",
                "ships",
                "spies",
                "score",
                "beige",
                "inactive",
                "login_chance",
                "weekly_activity",
                "att1",
                "att2",
                "att3"
        ));

        rowData.add(SheetUtil.toRowData(header));

        for (Map.Entry<DBNation, List<DBNation>> entry : targets.entrySet()) {
            DBNation defender = entry.getKey();
            List<DBNation> attackers = entry.getValue();
            ArrayList<Object> row = new ArrayList<>();
            row.add(MarkupUtil.sheetUrl(defender.getAllianceName(), defender.getAllianceUrl()));
            row.add(MarkupUtil.sheetUrl(defender.getNation(), defender.getUrl()));

            row.add(defender.getCities());
            row.add(defender.getAvg_infra());
            row.add(defender.getSoldiers() + "");
            row.add(defender.getTanks() + "");
            row.add(defender.getAircraft() + "");
            row.add(defender.getShips() + "");
            row.add(defender.getSpies() + "");

            row.add(defender.getScore() + "");
            row.add(defender.getBeigeTurns() + "");
            row.add(TimeUtil.secToTime(TimeUnit.MINUTES, defender.active_m()));

            Activity activity = defender.getActivity(12 * 7 * 2);
            double loginChance = activity.loginChance(turn == -1 ? 11 : turn, 48, false);
            row.add(loginChance);
            row.add(activity.getAverageByDay());

            List<DBNation> myCounters = targets.getOrDefault(defender, Collections.emptyList());

            for (DBNation counter : myCounters) {
                String counterUrl = MarkupUtil.sheetUrl(counter.getNation(), counter.getUrl());
                row.add(counterUrl);
            }
            RowData myRow = SheetUtil.toRowData(row);
            List<CellData> myRowData = myRow.getValues();
            int attOffset = myRowData.size() - myCounters.size();
            for (int i = 0; i < myCounters.size(); i++) {
                DBNation counter = myCounters.get(i);
                myRowData.get(attOffset + i).setNote(getAttackerNote(counter));
            }
            myRow.setValues(myRowData);

            rowData.add(myRow);
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite(null, rowData);
    }

    private static String getAttackerNote(DBNation nation) {
        StringBuilder note = new StringBuilder();

        double score = nation.getScore();
        double minScore = Math.ceil(nation.getScore() * 0.75);
        double maxScore = Math.floor(nation.getScore() * PW.WAR_RANGE_MAX_MODIFIER);
        note.append("War Range: " + MathMan.format(minScore) + "-" + MathMan.format(maxScore) + " (" + score + ")").append("\n");
        note.append("ID: " + nation.getNation_id()).append("\n");
        note.append("Alliance: " + nation.getAllianceName()).append("\n");
        note.append("Cities: " + nation.getCities()).append("\n");
        note.append("avg_infra: " + nation.getAvg_infra()).append("\n");
        note.append("soldiers: " + nation.getSoldiers()).append("\n");
        note.append("tanks: " + nation.getTanks()).append("\n");
        note.append("aircraft: " + nation.getAircraft()).append("\n");
        note.append("ships: " + nation.getShips()).append("\n");
        return note.toString();
    }
}
