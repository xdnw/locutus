package link.locutus.discord.util.sheet;

import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.ExtendedValue;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.AddBalanceBuilder;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.ClearValuesResponse;
import com.google.api.services.sheets.v4.model.GridCoordinate;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.api.services.sheets.v4.model.UpdateCellsRequest;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.opencsv.CSVWriter;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.MessageChannel;
import org.apache.commons.collections4.map.PassiveExpiringMap;

import java.io.*;
import java.lang.reflect.Field;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.gson.internal.$Gson$Preconditions.checkArgument;
import static com.google.gson.internal.$Gson$Preconditions.checkNotNull;

public class SpreadSheet {

    private static final String INSTRUCTIONS = """
1. In the Google Cloud console, go to Menu menu > APIs & Services > Credentials.
2. Go to Credentials <https://console.cloud.google.com/apis/credentials.
3. Click Create Credentials > OAuth client ID.
4. Click Application type > Desktop app (or web application).
5. Download the credentials and save it to `config/credentials-sheets.json`""";
    private static final PassiveExpiringMap<String, SpreadSheet> CACHE = new PassiveExpiringMap<String, SpreadSheet>(5, TimeUnit.MINUTES);

    public CompletableFuture<IMessageBuilder> addTransactionsList(IMessageIO channel, List<Transaction2> transactions, boolean includeHeader) throws IOException {
        List<Object> header = new ArrayList<>(Arrays.asList(
                "id",
                "type",
                "date",
                "sender_id",
                "sender_type",
                "receiver_id",
                "receiver_type",
                "banker",
                "note"
        ));
        for (ResourceType value : ResourceType.values()) {
            if (value == ResourceType.CREDITS) continue;
            header.add(value.name());
        }

        if (includeHeader) {
            this.setHeader(header);
        }

        Collections.sort(transactions, Comparator.comparingLong(o -> o.tx_datetime));
        for (Transaction2 record : transactions) {
            String type;
            if (record.tx_id == -1) {
                type = "INTERNAL";
            } else if (record.sender_type == 1 && record.receiver_id == 2 && record.note.equals("#tax")) {
                type = "TAX";
            } else {
                type = "BANK";
            }
            header.set(0, record.tx_id);
            header.set(1, type);
            header.set(2, TimeUtil.YYYY_MM_DD_HH_MM_SS.format(new Date(record.tx_datetime)));
            header.set(3, record.sender_id);
            header.set(4, record.sender_type);
            header.set(5, record.receiver_id);
            header.set(6, record.receiver_type);
            header.set(7, record.banker_nation);
            header.set(8, record.note + "");
            int i = 9;
            for (ResourceType value : ResourceType.values()) {
                if (value == ResourceType.CREDITS) continue;
                header.set(i++, record.resources[value.ordinal()]);
            }

            this.addRow(header);
        }

        this.clear("A:Z");
        try {
            this.set(0, 0);
            return attach(channel.create()).send();
        } catch (Throwable e) {
            e.printStackTrace();
            return channel.create().file("transactions.csv", this.toCsv())
                            .append(e.getMessage())
                                    .send();
        }
    }

    public <T> T loadHeader(T instance, List<Object> headerStr) throws NoSuchFieldException, IllegalAccessException {
        for (int i = 0; i < headerStr.size(); i++) {
            Object columnObj = headerStr.get(i);
            if (columnObj == null) continue;
            String columnName = columnObj.toString().toLowerCase().replaceAll("[^a-z_]", "");
            if (columnName.isEmpty()) continue;
            Field field = instance.getClass().getDeclaredField(columnName);
            field.set(instance, i);
        }
        return instance;
    }

    public static SpreadSheet create(GuildDB db, GuildDB.Key key) throws GeneralSecurityException, IOException {
        String sheetId = db.getInfo(key);

        Sheets api = null;

        System.out.println("Credentials " + credentialsExists());
        if (credentialsExists()) {
            if (sheetId == null) {
                Spreadsheet spreadsheet = new Spreadsheet()
                        .setProperties(new SpreadsheetProperties()
                                .setTitle(db.getGuild().getId() + "." + key)
                        );
                api = getServiceAPI();
                spreadsheet = api.spreadsheets().create(spreadsheet)
                        .setFields("spreadsheetId")
                        .execute();

                sheetId = spreadsheet.getSpreadsheetId();
                db.setInfo(key, sheetId);
            }
            if (sheetId != null) {
                DriveFile gdFile = new DriveFile(sheetId);
                try {
                    gdFile.shareWithAnyone(DriveFile.DriveRole.WRITER);
                } catch (GoogleJsonResponseException | TokenResponseException e) {
                    e.printStackTrace();
                }
            }
        } else {
            sheetId = UUID.randomUUID().toString();
        }

        SpreadSheet sheet = create(sheetId, api);

        if (api != null) sheet.set(0, 0);
        return sheet;
    }

    public static SpreadSheet create(String id) throws GeneralSecurityException, IOException {
        return create(id, null);
    }

    private static SpreadSheet create(String id, Sheets api) throws GeneralSecurityException, IOException {
        id = parseId(id);
        // check cache
        {
            SpreadSheet cached = CACHE.get(id);
            if (cached != null) return cached;
        }
        SpreadSheet sheet = new SpreadSheet(id, api);
        // add to cache (or update the timestamp)
        CACHE.put(id, sheet);
        return sheet;
    }

    private static final String APPLICATION_NAME = "Spreadsheet";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "config" + java.io.File.separator + "tokens";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private static final String CREDENTIALS_FILE_PATH = "config" + java.io.File.separator + "credentials-sheets.json";
    private Sheets service;
    private List<List<Object>> values;
    private String spreadsheetId;

    public static String parseId(String id) {
        if (id.startsWith("sheet:")) {
            id = id.split(":")[1];
        } else if (id.startsWith("https://docs.google.com/spreadsheets/")){
            String regex = "([a-zA-Z0-9-_]{30,})";
            Matcher m = Pattern.compile(regex).matcher(id);
            m.find();
            id = m.group();
        }
        return id.split("/")[0];
    }

    private SpreadSheet(String id, Sheets api) throws GeneralSecurityException, IOException {
        if (id != null) {
            if (api == null && credentialsExists()) api = getServiceAPI();
            this.service = api;
            this.spreadsheetId = parseId(id);
        }
    }

    protected SpreadSheet(String spreadsheetId) throws GeneralSecurityException, IOException {
        this(spreadsheetId, null);
    }

    private static Sheets getServiceAPI() throws GeneralSecurityException, IOException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        return  new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public Sheets getService() {
        return service;
    }

    public SpreadSheet(GuildDB db, GuildDB.Key key) throws GeneralSecurityException, IOException {
        this.service = getServiceAPI();

        String sheetId = db.getInfo(key);
        if (sheetId == null) {
            Spreadsheet spreadsheet = new Spreadsheet()
                    .setProperties(new SpreadsheetProperties()
                            .setTitle(db.getGuild().getId() + "." + key)
                    );
            spreadsheet = service.spreadsheets().create(spreadsheet)
                    .setFields("spreadsheetId")
                    .execute();

            this.spreadsheetId = spreadsheet.getSpreadsheetId();
            db.setInfo(key, this.spreadsheetId);
            set(0, 0);

            DriveFile gdFile = new DriveFile(spreadsheetId);
            gdFile.shareWithAnyone(DriveFile.DriveRole.WRITER);

        } else {
            this.spreadsheetId = sheetId;
        }
    }

    public Map<String, Boolean> parseTransfers(AddBalanceBuilder builder, boolean negative, String defaultNote) {
        Map<String, Boolean> result = new LinkedHashMap<String, Boolean>();
        List<List<Object>> rows = getAll();
        List<Object> header = rows.get(0);

        Integer noteI = null;
        for (int i = 0; i < header.size(); i++) {
            Object col = header.get(i);
            if (col != null && col.toString().equalsIgnoreCase("note")) noteI = i;
        }

        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.isEmpty() || row.size() < 2) continue;

            Object name = row.get(0);
            if (name == null) continue;
            String nameStr = name + "";
            if (nameStr.isEmpty()) continue;

            Map<ResourceType, Double> transfer = new LinkedHashMap<>();
            for (int j = 1; j < row.size(); j++) {
                Object rssName = header.size() > j ? header.get(j) : null;
                if (rssName == null || rssName.toString().isEmpty() || (noteI != null && i == noteI)) continue;
                Object amtStr = row.get(j);
                if (amtStr == null || amtStr.toString().isEmpty()) continue;
                try {
                    ResourceType type = ResourceType.parse(rssName.toString());
                    if (type == null) throw new IllegalArgumentException("Invalid resource: " + rssName);
                    Double amt = MathMan.parseDouble(amtStr.toString());
                    if (amt == null) continue;
                    transfer.put(type, transfer.getOrDefault(type, 0d) + amt);
                    continue;
                } catch (IllegalArgumentException ignore) {
                    ignore.printStackTrace();
                }
                if (rssName.toString().equalsIgnoreCase("cost_raw")) {
                    for (Map.Entry<ResourceType, Double> entry : PnwUtil.parseResources(amtStr.toString()).entrySet()) {
                        transfer.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                }
            }
            if (transfer.isEmpty()) continue;
            if (negative) transfer = PnwUtil.subResourcesToA(new LinkedHashMap<>(), transfer);


            NationOrAllianceOrGuild account = PWBindings.nationOrAllianceOrGuild(nameStr);
            if (account == null) {
                throw new IllegalArgumentException("Invalid nation/alliance/guild: `" + nameStr + "`");
            }
            Object noteObj = null;
            if (noteI != null && row.size() > noteI) noteObj = row.get(noteI);
            if (noteObj == null) noteObj = defaultNote;
            builder.add(account, transfer, noteObj.toString());
        }
        return result;
    }

    public String getSpreadsheetId() {
        return spreadsheetId;
    }

    public String getURL() {
        if (service == null) {
            return "sheet:" + spreadsheetId;
        }
        return getURL(false, false);
    }

    public IMessageBuilder attach(IMessageBuilder msg, String append) {
        return attach(msg).append(append);
    }

    public IMessageBuilder attach(IMessageBuilder msg) {
        return attach(msg, null, false, 0);
    }

    public IMessageBuilder attach(IMessageBuilder msg, StringBuilder output, boolean allowInline, int currentLength) {
        String append = null;
        if (service == null) {
            String csv = toCsv();
            if (csv.length() + currentLength + 9 < 2000 && allowInline) {
                append = "```csv\n" + csv + "```";
            } else {
                append = "(`sheet:" + spreadsheetId + "`)";
                msg.file("sheet.csv", csv);
            }
        } else {
            append = ("\n" + getURL(false, true));
        }
        if (output != null) output.append(append);
        else msg.append(append);
        return msg;
    }

    public IMessageBuilder send(IMessageIO io, String header, String footer) {
        if (header == null) header = "";
        if (footer == null) footer = "";
        if (service == null) {
            String csv = toCsv();
            if (csv.length() + header.length() + footer.length() < 2000) {
                return io.create().append(header + "```" + csv + "```" + footer);
            } else {
                return io.create()
                        .append(header)
                        .append(header.isEmpty() ? "" : "\n")
                        .append(footer)
                        .file("sheet.csv", csv);
            }
        } else {
            return io.create()
                    .append(header)
                    .append(header != null ? "\n" : "")
                    .append(getURL(false, false))
                    .append(footer);
        }
    }

    public String getURL(boolean allowFallback, boolean markdown) {
        if (service == null) {
            if (!allowFallback) {
                throw new IllegalArgumentException(INSTRUCTIONS);
            }
            if (markdown) {
                String csv = toCsv();
                if (csv.length() < 2000) {
                    return "```" + csv + "```";
                }
                return csv;
            } else {
                return "sheet:" + spreadsheetId;
            }
        }
        String url = "https://docs.google.com/spreadsheets/d/%s/";
        url = String.format(url, spreadsheetId);
        if (markdown) {
            url = "<" + url + ">";
        }
        return url;
    }

    public static File getCredentialsFile() {
        return new File(CREDENTIALS_FILE_PATH);
    }

    public static boolean credentialsExists() {
        return getCredentialsFile().exists();
    }

    /**
     * Creates an authorized Credential object.
     *
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
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

    public void addRow(List<? extends Object> list) {
        this.getValues().add(processRow(new ArrayList<>(list)));
    }

    public List<List<Object>> getValues() {
        if (values == null) values = new ArrayList<>();
        return values;
    }

    public void addRow(Object... values) {
        this.getValues().add(processRow(Arrays.asList(values)));
    }

    private List<Object> processRow(List<Object> row) {
        List<Object> out = new ArrayList<>();
        for (int i = 0; i < row.size(); i++) {
            Object value = row.get(i);
            if (value == null) {
                out.add(null);
            } else {
                String valueStr = value.toString();
                if (valueStr.contains("{")) {
                    valueStr = valueStr.replaceAll("\\{row}", (getValues().size() + 1) + "");
                    valueStr = valueStr.replaceAll("\\{column}", SheetUtil.getLetter(getValues().size() + 1));
                    out.add(valueStr);
                } else {
                    out.add(value);
                }
            }
        }
        return out;
    }

    public void write(List<RowData> rowData) throws IOException {
        if (service == null) {
            reset();
            for (RowData row : rowData) {
                List<Object> dataSimple = new ArrayList<>();
                for (CellData cell : row.getValues()) {
                    ExtendedValue value = cell.getUserEnteredValue();
                    dataSimple.add(value.toString());
                }
                addRow(dataSimple);
            }
            return;
        }
        UpdateCellsRequest appendCellReq = new UpdateCellsRequest();
        appendCellReq.setRows( rowData );
        appendCellReq.setFields("userEnteredValue,note");
        GridCoordinate start = new GridCoordinate();
        start.setColumnIndex(0);
        start.setRowIndex(0);
        appendCellReq.setStart(start);

        ArrayList<Request> requests = new ArrayList<Request>();
        requests.add( new Request().setUpdateCells(appendCellReq));
        BatchUpdateSpreadsheetRequest batchRequests = new BatchUpdateSpreadsheetRequest();
        batchRequests.setRequests( requests );

        BatchUpdateSpreadsheetResponse result = service.spreadsheets().batchUpdate(spreadsheetId, batchRequests).execute();
    }

    public void set(int x, int y) throws IOException {
        if (values == null || values.isEmpty()) {
            return;
        }
        if (x == 0 && y == 0 && service == null) {
            return;
        }
        int width = getValues().isEmpty() ? 0 : getValues().get(0).size();
        int size = getValues().size();

        for (int i = 0; i < size; i += 10000) {
            int height = Math.min(i + 9999, size);
            List<List<Object>> subList = getValues().subList(i, height);
            for (List<Object> objects : subList) {
                width = Math.max(width, objects.size());
            }

            int y_off = y + i;

            String pos1 = SheetUtil.getRange(x, y_off);
            String pos2 = SheetUtil.getRange(width - 1 + x, height + y_off - 1);
            String range = pos1 + ":" + pos2;

            ValueRange body = new ValueRange()
                    .setValues(subList);
            UpdateValuesResponse result =
                    service.spreadsheets().values().update(spreadsheetId, range, body)
                            .setValueInputOption("USER_ENTERED")
                            .execute();
        }
        values = null;
    }

    public List<List<Object>> get(int x1, int y1, int x2, int y2) {
        return get(SheetUtil.getRange(x1, y1, x2, y2));
    }

    public List<List<Object>> loadValues() {
        if (values == null) {
            this.values = get("A:ZZ");
        }
        return this.values;
    }

    public List<List<Object>> getAll() {
        if (service == null) return loadValues();
        return get("A:ZZ");
    }

    public List<List<Object>> get(String range) {
        return get(range, null);
    }

    public List<List<Object>> get(String range, Consumer<Sheets.Spreadsheets.Values.Get> onGet) {
        try {
            System.out.println("Service " + service + " | " + spreadsheetId);
            Sheets.Spreadsheets.Values.Get query = service.spreadsheets().values().get(spreadsheetId, range);
            if (onGet != null) onGet.accept(query);
            ValueRange result = query.execute();
            return result.getValues();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void clearAll() throws IOException {
        if (service == null) {
            return;
        }
        UpdateCellsRequest updateCellsRequest = new UpdateCellsRequest();
        int allSheetsId = 0;
        String clearAllFieldsSpell = "*";
        GridRange gridRange = new GridRange();
        gridRange.setSheetId(allSheetsId);
        updateCellsRequest.setRange(gridRange);
        updateCellsRequest.setFields(clearAllFieldsSpell);
        BatchUpdateSpreadsheetRequest request = new BatchUpdateSpreadsheetRequest();
        Request clearAllDataRequest = new Request().setUpdateCells(updateCellsRequest);
        request.setRequests(List.of(clearAllDataRequest));

        BatchUpdateSpreadsheetResponse response = service.spreadsheets().batchUpdate(spreadsheetId, request).execute();
    }

    public void clear(String range) throws IOException {
        if (service == null) {
            return;
        }
        ClearValuesRequest requestBody = new ClearValuesRequest();
        Sheets.Spreadsheets.Values.Clear request =
                service.spreadsheets().values().clear(spreadsheetId, range, requestBody);

        ClearValuesResponse response = request.execute();
    }

    public void reset() {
        this.values = null;
    }

    public String toCsv() {
        if (this.values == null || this.values.isEmpty()) return "";
        StringWriter stringWriter = new StringWriter();
        CSVWriter csvWriter = new CSVWriter(stringWriter, ',');

        for (List<Object> rowObj : getValues()) {
            String[] row = new String[rowObj.size()];
            for (int i = 0; i < rowObj.size(); i++) {
                Object value = rowObj.get(i);
                row[i] = value != null ? "" + value : "";
            }
            csvWriter.writeNext(row);
        }
        return stringWriter.toString();
    }

    public List<Object> findColumn(String... arguments) {
        return findColumn(-1, arguments);
    }

    public List<Object> findColumn(int columnDefault, String... arguments) {
        checkNotNull(arguments);
        checkArgument(arguments.length > 0);
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("No values found. Was `loadValues` called?");
        List<Object> header = this.values.get(0);
        outer:
        for (int i = 0; i < header.size(); i++) {
            Object obj = header.get(i);
            if (obj == null) continue;
            String objStr = obj.toString().toLowerCase(Locale.ROOT);

            for (String argument : arguments) {
                if (objStr.contains(argument.toLowerCase(Locale.ROOT))) {
                    columnDefault = i;
                    break outer;
                }
            }
        }
        if (columnDefault < 0) return null;
        List<Object> column = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (row.size() <= columnDefault) {
                column.add(null);
            } else {
                column.add(row.get(columnDefault));
            }
        }
        return column;
    }

    public void setHeader(Object... header) {
        setHeader(Arrays.asList(header));
    }

    public void setHeader(List<? extends Object> header) {
        this.values = null;
        this.addRow(header);
    }
}