package link.locutus.discord.util.sheet;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import com.opencsv.CSVWriter;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.AddBalanceBuilder;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.pnw.NationOrAllianceOrGuildOrTaxid;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import net.dv8tion.jda.api.entities.Message;
import org.apache.commons.collections4.map.PassiveExpiringMap;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class SpreadSheet {

    private static final String INSTRUCTIONS = """
1. In the Google Cloud console, go to Menu menu > APIs & Services > Credentials.
2. Go to Credentials <https://console.cloud.google.com/apis/credentials.
3. Click Create Credentials > OAuth client ID.
4. Click Application type > Desktop app (or web application).
5. Download the credentials and save it to `config/credentials-sheets.json`""";
    private static final PassiveExpiringMap<String, SpreadSheet> CACHE = new PassiveExpiringMap<String, SpreadSheet>(30, TimeUnit.MINUTES);

    public static boolean isSheet(String arg) {
        return arg.startsWith("https://docs.google.com/spreadsheets/") || arg.startsWith("sheet:");
    }

    public static <T> Set<T> parseSheet(String sheetId, List<String> expectedColumns, boolean defaultZero, BiFunction<Integer, String, T> parseCell) {
        Function<String, Integer> parseColumnType = f -> {
            int index = expectedColumns.indexOf(f.toLowerCase(Locale.ROOT));
            return index == -1 ? null : index;
        };
        return parseSheet(sheetId, expectedColumns, defaultZero, parseColumnType, parseCell);
    }

    public static <T> Set<T> parseSheet(String sheetId, List<String> expectedColumns, boolean defaultZero, Function<String, Integer> parseColumnType, BiFunction<Integer, String, T> parseCell) {
        SheetId keys = SpreadSheet.parseId(sheetId);
        SpreadSheet sheet;
        try {
            sheet = SpreadSheet.create(keys);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String tab = keys.tabName;
        if (tab == null) tab = sheet.getDefaultTab(true);
        List<List<Object>> rows = sheet.fetchAll(tab);
        sheet.valuesByTab.remove(tab.toLowerCase(Locale.ROOT));
        if (rows == null || rows.isEmpty()) return Collections.emptySet();

        Set<T> toAdd = new ObjectLinkedOpenHashSet<>();
        Map<Integer, String> columnIndexToName = new LinkedHashMap<>();
        Map<Integer, Integer> columnIndexToColumnType = new LinkedHashMap<>();
        List<Object> header = rows.get(0);
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i) == null) continue;
            String name = header.get(i).toString().trim();
            if (name.isEmpty()) continue;
            Integer columnType = parseColumnType.apply(name);
            if (columnType != null) {
                columnIndexToColumnType.put(i, columnType);
                columnIndexToName.put(i, name);
            }
        }
        if (defaultZero && !header.isEmpty() && header.get(0) != null) {
            columnIndexToColumnType.put(0, 0);
            columnIndexToName.put(0, header.get(0).toString());
        }
        if (columnIndexToColumnType.isEmpty()) {
            throw new IllegalArgumentException("Could not parse sheet: `" + sheetId + "`\n" +
                    "expected one of: `" + expectedColumns + "`\n" +
                    "found invalid header: `" + StringMan.getString(header) + "`\n");
        }

        Map<String, String> invalid = new LinkedHashMap<>();

        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row == null || row.isEmpty()) continue;

            for (Map.Entry<Integer, Integer> entry : columnIndexToColumnType.entrySet()) {
                int index = entry.getKey();
                if (index >= row.size()) continue;
                Object cell = row.get(index);
                if (cell == null) continue;
                String cellStr = cell.toString().trim();
                if (cellStr.isEmpty()) continue;

                String columnName = columnIndexToName.get(index);
                try {
                    T value = parseCell.apply(entry.getValue(), cellStr);
                    if (value != null) {
                        toAdd.add(value);
                    } else {
                        invalid.put(cellStr, "Invalid (null): `" + cellStr + "` in column `" + columnName + "`");
                    }
                } catch (IllegalArgumentException e) {
                    invalid.put(cellStr, e.getMessage() + " in column `" + columnName + "`");
                }
            }
        }
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("Could not parse sheet: `" + sheetId + "`. Errors:\n- " + StringMan.join(invalid.values(), "\n- "));
        }
        return toAdd;
    }

    public static List<List<Object>> generateTransactionsListCells(List<Transaction2> transactions, boolean includeHeader, boolean ascending) {
        List<List<Object>> cells = new ObjectArrayList<>();

        List<Object> header = new ObjectArrayList<>(Arrays.asList(
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
            cells.add(new ObjectArrayList<>(header));
        }

        if (ascending) {
            Collections.sort(transactions, Comparator.comparingLong(o -> o.tx_datetime));
        } else {
            Collections.sort(transactions, (o1, o2) -> Long.compare(o2.tx_datetime, o1.tx_datetime));
        }
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
            header.set(2, TimeUtil.format(TimeUtil.YYYY_MM_DD_HH_MM_SS, record.tx_datetime));
            header.set(3, record.sender_id + "");
            header.set(4, record.sender_type);
            header.set(5, record.receiver_id + "");
            header.set(6, record.receiver_type);
            header.set(7, record.banker_nation);
            header.set(8, record.note);
            int i = 9;
            for (ResourceType value : ResourceType.values()) {
                if (value == ResourceType.CREDITS) continue;
                header.set(i++, record.resources[value.ordinal()]);
            }

            cells.add(new ObjectArrayList<>(header));
        }

        return cells;
    }

    public CompletableFuture<IMessageBuilder> addTransactionsList(IMessageIO channel, List<Transaction2> transactions, boolean includeHeader) throws IOException {
        List<List<Object>> cells = generateTransactionsListCells(transactions, includeHeader, true);
        if (includeHeader) {
            this.valuesByTab.clear();
        }
        List<List<Object>> values = this.getCachedValues(null);
        synchronized (values) {
            values.addAll(cells);
        }
        this.updateClearTab(null);
        try {
            this.updateWrite();
            return attach(channel.create(), "transactions").send();
        } catch (Throwable e) {
            e.printStackTrace();
            IMessageBuilder msg = channel.create();
            Map<String, String> csv = this.toCsv();
            if (csv.isEmpty()) {
                msg.append("`No transactions to add`\n");
            } else {
                for (Map.Entry<String, String> entry : csv.entrySet()) {
                    msg.file(entry.getKey() + ".csv", entry.getValue());
                }
            }
            return msg.append(e.getMessage()).send();
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

    private static SpreadSheet createInternal(GuildDB dbOrNull, SheetKey key, String titleOrNull) {
        if (titleOrNull == null) {
            if (dbOrNull == null || key == null) {
                if (key == null) {
                    throw new IllegalArgumentException("Either a guild or title must be provided");
                }
                throw new IllegalArgumentException("This command must be run in a guild, or a sheet must be specified for: " + key.name());
            }
            titleOrNull = dbOrNull.getGuild().getId() + "." + key.name();
        }
        Spreadsheet spreadsheet = new Spreadsheet()
                .setProperties(new SpreadsheetProperties()
                        .setTitle(titleOrNull)
                );

        Sheets api = null;
        String sheetId = dbOrNull == null ? null : dbOrNull.getInfo(key, true);
        try {
            api = SheetUtil.getSheetService();
            if (sheetId == null) {
                Sheets finalApi = api;
                Spreadsheet finalSpreadsheet = spreadsheet;
                spreadsheet = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                        () -> finalApi.spreadsheets().create(finalSpreadsheet)
                                .setFields("spreadsheetId")
                                .execute());
                sheetId = spreadsheet.getSpreadsheetId();
                if (dbOrNull != null && key != null) {
                    dbOrNull.setInfo(key, sheetId);
                }
            }
            if (sheetId != null) {
                String[] split = sheetId.split(",");
                try {
                    DriveFile gdFile = new DriveFile(split[0]);
                    gdFile.shareWithAnyone(DriveFile.DriveRole.WRITER);
                } catch (GeneralSecurityException | IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            sheetId = UUID.randomUUID().toString();
        }
        return create(sheetId, api);
    }

    public static SpreadSheet createTitle(String title) throws GeneralSecurityException, IOException {
        return createInternal(null, null, title);
    }

    public static SpreadSheet create(GuildDB db, SheetKey key) throws GeneralSecurityException, IOException {
        return createInternal(db, key, null);
    }

    private SpreadSheet(String id, Sheets api) {
        if (id != null) {
            this.service = api;
            this.spreadsheetId = id;
            if (this.service != null) {
                this.valuesByTab.clear();
            }
        }
    }

    public static SpreadSheet create(String id) throws IOException {
        Sheets api = null;
        try {
            api = SheetUtil.getSheetService();
        } catch (IOException _) {}
        return create(id, api);
    }

    private static SpreadSheet create(String id, Sheets api) {
        return create(parseId(id), api);
    }

    private static SpreadSheet create(SheetId id) throws IOException {
        Sheets api = null;
        try {
            api = SheetUtil.getSheetService();
        } catch (IOException _) {}
        return create(id, api);
    }

    private static SpreadSheet create(SheetId id, Sheets api) {
        SpreadSheet cached;
        synchronized (CACHE) {
            cached = CACHE.get(id.id);
            if (cached == null) {
                cached = new SpreadSheet(id.id, api);
                CACHE.put(id.id, cached);
                if (api != null) {
                    try {
                        DriveFile gdFile = new DriveFile(id.id);
                        gdFile.shareWithAnyone(DriveFile.DriveRole.WRITER);
                    } catch (GeneralSecurityException | IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                CACHE.put(id.id, cached); // Reset expiration
            }
        }
        synchronized (cached) {
            if (id.tabId != null) {
                cached.setDefaultTab(id.tabId);
            } else if (id.tabName != null) {
                cached.setDefaultTab(id.tabName, id.tabId);
            } else {
                cached.setDefaultTab("", null);
            }
        }
        return cached;
    }

    private Sheets service;
    private final Map<String, List<List<Object>>> valuesByTab = new LinkedHashMap<>();
    private String spreadsheetId;
    private Integer defaultTabId = null;
    private String defaultTab = "";

    public String getTitle() {
        if (service == null) {
            return spreadsheetId;
        }
        try {
            Spreadsheet spreadsheet = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                    () -> service.spreadsheets().get(spreadsheetId).setFields("properties").execute());
            return spreadsheet.getProperties().getTitle();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        return spreadsheetId;
    }

    public String getQualifiedId(boolean includeTab) {
        return "sheet:" + spreadsheetId + (includeTab && defaultTabId != null ? "#" + defaultTabId : "");
    }

    public record SheetId(String id, String tabName, Integer tabId) {
    }

    public static SheetId parseId(String id) {
        String tabName = null;
        Integer tabId = null;
        if (id.startsWith("sheet:")) {
            id = id.split(":",2)[1];
            if (id.contains(",") || id.contains(";") || id.contains(":") || id.contains("#")) {
                String[] split = id.split("[,;:#]", 2);
                id = split[0];
                String tabStr = split[1];
                if (MathMan.isInteger(tabStr)) {
                    tabId = Integer.parseInt(tabStr);
                } else {
                    tabName = tabStr;
                }
            }
        } else if (id.startsWith("https://docs.google.com/spreadsheets/")) {
            String regex = "#gid=([0-9]{1,})";
            Matcher m;
            m = Pattern.compile(regex).matcher(id);
            if (m.find()) {
                tabId = Integer.parseInt(m.group(1));
                if (tabId == 0) tabId = null;
            }
            regex = "#tab=([a-zA-Z0-9-_]{1,})";
            m = Pattern.compile(regex).matcher(id);
            if (m.find()) {
                tabName = m.group(1);
            }
            regex = "([a-zA-Z0-9-_]{30,})";
            m = Pattern.compile(regex).matcher(id);
            m.find();
            id = m.group().split("/")[0];
        }
        return new SheetId(id, tabName, tabId);
    }

    public Sheets getService() {
        return service;
    }

    public void setDefaultTab(Integer id) {
        if (service == null) {
            throw new IllegalArgumentException("No google api server found, cannot select tab with id `" + id + "`");
        }
        Map<Integer, String> tabs = fetchTabs();
        String tab = tabs.get(id);
        if (tab == null) {
            throw new IllegalArgumentException("No tab with id `" + id + "` found. Options: " + StringMan.getString(tabs));
        }
        setDefaultTab(tab, id);
    }

    public void setDefaultTab(String defaultTab, Integer id) {
        if (defaultTab != null && !defaultTab.isEmpty() && !defaultTab.equals(this.defaultTab)) {
            if (id == null && service != null) {
                Map<String, Integer> tabs = getTabsByNameLower();
                id = tabs.get(defaultTab.toLowerCase());
                if (id == null) {
                    throw new IllegalArgumentException("No tab found with name: `" + defaultTab + "`. Options: " + StringMan.getString(tabs));
                }
            }
            this.defaultTabId = id;
        }
        this.defaultTab = defaultTab;
        if (defaultTab == null || defaultTab.isEmpty()) {
            this.defaultTabId = null;
        }
    }

    public Map<String, Boolean> parseTransfers(AddBalanceBuilder builder, boolean negative, String defaultNote) {
        Map<String, Boolean> result = new LinkedHashMap<String, Boolean>();
        List<List<Object>> rows = fetchAll(null);
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
                if (rssName.toString().equalsIgnoreCase("cost_raw") || rssName.toString().equalsIgnoreCase("deposit_raw") || rssName.toString().equalsIgnoreCase("resources")) {
                    for (Map.Entry<ResourceType, Double> entry : ResourceType.parseResources(amtStr.toString()).entrySet()) {
                        transfer.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                }
            }
            if (transfer.isEmpty()) continue;
            if (negative) transfer = ResourceType.subResourcesToA(new LinkedHashMap<>(), transfer);


            NationOrAllianceOrGuildOrTaxid account = PWBindings.nationOrAllianceOrGuildOrTaxId(nameStr, true);
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
        return getURL(true);
    }

    public String getURL(boolean includeTab) {
        if (service == null) {
            return "sheet:" + spreadsheetId;
        }
        return getURL(false, false) + (includeTab && defaultTabId != null ? "#    gid=" + defaultTabId : "");
    }

    public IMessageBuilder attach(IMessageBuilder msg, String name, String append) {
        return attach(msg, name).append(append);
    }

    public IMessageBuilder attach(IMessageBuilder msg, String name) {
        return attach(msg, MarkupUtil.escapeMarkdown(name), null, false, 0);
    }

    public IMessageBuilder attach(IMessageBuilder msg, String name, StringBuilder output, boolean allowInline, int currentLength) {
        String append = null;
        if (service == null) {
            Map<String, String> csvs = toCsv();
            int length = csvs.values().stream().mapToInt(String::length).sum();
            boolean willInline = length + currentLength + (9 * csvs.size()) < Message.MAX_CONTENT_LENGTH && allowInline;
            for (Map.Entry<String, String> entry : csvs.entrySet()) {
                String title;
                if (name == null || name.isEmpty()) {
                    title = entry.getKey();
                } else if (csvs.size() > 1) {
                    title = name + "." + entry.getKey();
                } else {
                    title = name;
                }
                String csv = entry.getValue();
                if (willInline) {
                    append = title + "```csv\n" + csv + "```";
                } else {
                    append = "(`sheet:" + spreadsheetId + "`)";
                    msg.file(title + ".csv", csv);
                }
            }
        } else {
            append = ("\n" + (name == null ? "" : name + ": " + getURL(false, true)));
        }
        if (output != null) output.append(append);
        else msg.append(append);
        return msg;
    }

    public IMessageBuilder send(IMessageIO io, String header, String footer) {
        if (header == null) header = "";
        if (footer == null) footer = "";
        if (service == null) {
            Map<String, String> csvs = toCsv();
            int length = csvs.values().stream().mapToInt(String::length).sum();
            boolean willInline = length + footer.length() + (9 * csvs.size()) < 2000;
            IMessageBuilder msg = io.create();
            for (Map.Entry<String, String> entry : csvs.entrySet()) {
                String title = entry.getKey();
                String csv = entry.getValue();
                if (willInline) {
                    msg.append(header + "```" + csv + "```" + footer);
                } else {
                    msg.append(header)
                            .append(header.isEmpty() ? "" : "\n")
                            .append(footer)
                            .file(title + ".csv", csv);
                }
            }
            return msg;
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
                Map<String, String> csvs = toCsv();
                int length = csvs.values().stream().mapToInt(String::length).sum();
                if (length < 2000) {
                    StringBuilder output = new StringBuilder();
                    for (Map.Entry<String, String> entry : csvs.entrySet()) {
                        String title = entry.getKey();
                        String csv = entry.getValue();
                        output.append(title).append("\n```csv\n").append(csv).append("```\n");
                    }
                    return output.toString();
                }
                // join by newline
                StringBuilder output = new StringBuilder();
                for (Map.Entry<String, String> entry : csvs.entrySet()) {
                    String title = entry.getKey();
                    String csv = entry.getValue();
                    output.append(title).append("\n").append(csv).append("\n");
                }
                return output.toString();
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

    public void addRow(String tab, List<?> list) {
        List<List<Object>> values = this.getCachedValues(tab);
        synchronized (values) {
            values.add(formatRow(tab, new ObjectArrayList<>(list)));
        }
    }

    public String getDefaultTab() {
        return defaultTab;
    }

    public Integer getDefaultTabId() {
        return defaultTabId;
    }

    public String getDefaultTab(boolean useFirstTabIfNone) {
        if ((defaultTab == null || defaultTab.isEmpty()) && useFirstTabIfNone) {
            if (service == null) {
                defaultTab = "";
                defaultTabId = null;
            } else {
                Map.Entry<Integer, String> next = fetchTabs().entrySet().iterator().next();
                defaultTab = next.getValue();
                defaultTabId = next.getKey();
                if (!defaultTab.isEmpty()) {
                    List<List<Object>> existing = valuesByTab.remove("");
                    if (existing != null && !existing.isEmpty()) {
                        valuesByTab.computeIfAbsent(defaultTab.toLowerCase(Locale.ROOT), k -> new ObjectArrayList<>()).addAll(existing);
                    }
                }
            }
        }
        return defaultTab;
    }

    public List<List<Object>> getCachedValues(String tab) {
        if (tab == null) tab = getDefaultTab();
        synchronized (valuesByTab) {
            return valuesByTab.computeIfAbsent(tab.toLowerCase(Locale.ROOT), k -> new ObjectArrayList<>());
        }
    }

    public void addRow(List<?> row) {
        List<List<Object>> rows = this.getCachedValues(null);
        synchronized (rows) {
            rows.add(formatRow(null, row));
        }
    }

    public void addRow(Object... values) {
        List<List<Object>> rows = this.getCachedValues(null);
        synchronized (rows) {
            rows.add(formatRow(null, Arrays.asList(values)));
        }
    }

    private List<Object> formatRow(String tab, List<?> row) {
        List<Object> out = new ObjectArrayList<>(row.size());
        Integer rowCount = null;

        for (int i = 0; i < row.size(); i++) {
            Object val = row.get(i);
            if (!(val instanceof String)) { out.add(val); continue; }

            String s = val.toString();
            boolean hasRow = s.contains("$row");
            boolean hasCol = s.contains("$column");
            if (!hasRow && !hasCol) { out.add(val); continue; }

            if (hasRow && rowCount == null) {
                List<List<Object>> rows = getCachedValues(tab);
                synchronized (rows) { rowCount = rows.size(); }
                System.out.println("Format rows " + rowCount + " for value: " + s);
            }

            if (hasRow) s = s.replace("$row", String.valueOf(rowCount + 1));
            if (hasCol) s = s.replace("$column", SheetUtil.getLetter(i + 1));
            out.add(s);
        }
        return out;
    }

    public void updateWrite(String tab, List<RowData> rowData) throws IOException {
        if (tab == null) tab = getDefaultTab();
        if (service == null) {
            reset();
            for (RowData row : rowData) {
                List<Object> dataSimple = new ObjectArrayList<>();
                for (CellData cell : row.getValues()) {
                    ExtendedValue value = cell.getUserEnteredValue();
                    dataSimple.add(value.toString());
                }
                addRow(tab, dataSimple);
            }
            return;
        }
        UpdateCellsRequest appendCellReq = new UpdateCellsRequest();
        appendCellReq.setRows( rowData );
        appendCellReq.setFields("userEnteredValue,note");
        GridCoordinate start = new GridCoordinate();
        start.setColumnIndex(0);
        start.setRowIndex(0);

        if (!tab.isEmpty()) {
            Integer id = getTabsByNameLower().get(tab.toLowerCase(Locale.ROOT));
            if (id == null) {
                updateCreateTabIfAbsent(tab);
            }
            id = getTabsByNameLower().get(tab.toLowerCase(Locale.ROOT));
            if (id == null) {
                throw new IllegalArgumentException("Tab not found: " + tab);
            }
            start.setSheetId(id);
        }
        appendCellReq.setStart(start);
        List<Request> requests = new ObjectArrayList<Request>();
        requests.add( new Request().setUpdateCells(appendCellReq));
        BatchUpdateSpreadsheetRequest batchRequests = new BatchUpdateSpreadsheetRequest();
        batchRequests.setRequests( requests );

        BatchUpdateSpreadsheetResponse result = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                () -> service.spreadsheets().batchUpdate(spreadsheetId, batchRequests).execute());
    }

    public void updateCreateTabIfAbsent(String tabName) throws IOException {
        Spreadsheet sheet = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                () -> service.spreadsheets().get(spreadsheetId).execute());
        List<Sheet> sheets = sheet.getSheets();
        for (Sheet sheet1 : sheets) {
            if (sheet1.getProperties().getTitle().equals(tabName)) {
                return;
            }
        }
        updateAddTab(tabName);
    }

    /**
     * Checks if the provided tabs exist in the Google Spreadsheet. If a tab does not exist, it is created.
     *  The method returns a map where the keys are the tab names in lower case and the values are Booleans indicating whether the tab was created during the method execution.
     * @param tabs
     * @return
     * @throws IOException
     */
    public Map<String, Boolean> updateCreateTabsIfAbsent(Set<String> tabs) throws IOException {
        Map<String, Boolean> result = new LinkedHashMap<>();
        Spreadsheet sheet = service.spreadsheets().get(spreadsheetId).execute();
        List<Sheet> sheets = sheet.getSheets();
        Set<String> tabsLower = tabs.stream().map(String::toLowerCase).collect(Collectors.toCollection(ObjectLinkedOpenHashSet::new));
        for (Sheet subSheet : sheets) {
            String title = subSheet.getProperties().getTitle().toLowerCase();
            tabsLower.remove(title);
            result.put(title, false);
        }
        for (String tab : tabsLower) {
            updateAddTab(tab);
            result.put(tab.toLowerCase(Locale.ROOT), true);
        }
        return result;
    }

    private void updateAddTab(String tabName) {
        if (service == null) {
            return;
        }
        AddSheetRequest addSheetRequest = new AddSheetRequest();
        SheetProperties sheetProperties = new SheetProperties();
        sheetProperties.setTitle(tabName);
        addSheetRequest.setProperties(sheetProperties);
        List<Request> requests = new ObjectArrayList<>();
        requests.add(new Request().setAddSheet(addSheetRequest));
        BatchUpdateSpreadsheetRequest batchUpdateSpreadsheetRequest = new BatchUpdateSpreadsheetRequest();
        batchUpdateSpreadsheetRequest.setRequests(requests);
        try {
            SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                    () -> service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateSpreadsheetRequest).execute());
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    public void updateWrite() throws IOException {
        if (valuesByTab.isEmpty()) {
            return;
        }
        if (service == null) {
            System.out.println("Skipping write, no service");
            return;
        }
        for (Map.Entry<String, List<List<Object>>> entry : valuesByTab.entrySet()) {
            String tabName = entry.getKey();
            updateWrite(tabName);
        }
        reset();
    }

    public void updateWrite(String tabName) throws IOException {
        if (valuesByTab.isEmpty()) {
            return;
        }
        if (service == null) {
            System.out.println("Skipping write, no service");
            return;
        }
        List<List<Object>> values = valuesByTab.get(tabName.toLowerCase(Locale.ROOT));
        if (values == null || values.isEmpty()) {
            return;
        }
        int size = values.size();
        final int BATCH_ROWS = 10000;

        for (int i = 0; i < size; i += BATCH_ROWS) {
            int endExclusive = Math.min(i + BATCH_ROWS, size);
            List<List<Object>> batch = new ObjectArrayList<>(values.subList(i, endExclusive));

            int batchWidth = 0;
            for (List<Object> row : batch) {
                batchWidth = Math.max(batchWidth, row.size());
            }
            if (batchWidth == 0) {
                batchWidth = 1; // ensure a valid column range
            }

            String pos1 = SheetUtil.getRange(0, i);
            String pos2 = SheetUtil.getRange(batchWidth - 1, endExclusive - 1);
            String range = pos1 + ":" + pos2;

            ValueRange body = new ValueRange().setValues(batch);

            UpdateValuesResponse result = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                    () -> service.spreadsheets().values()
                            .update(spreadsheetId, (tabName.isEmpty() ? "" : tabName + "!") + range, body)
                            .setValueInputOption("USER_ENTERED")
                            .execute());
        }
    }

    public List<List<Object>> get(int x1, int y1, int x2, int y2) {
        return fetchRange(this.getDefaultTab(), SheetUtil.getRange(x1, y1, x2, y2));
    }

    public List<List<Object>> loadValuesCurrentTab(boolean force) {
        return loadValues(getDefaultTab(true), force);
    }

    public List<List<Object>> loadValues(String tabName, boolean force) {
        if (tabName == null || tabName.isEmpty()) {
            tabName = getDefaultTab(true);
        }
        if (service == null) {
            return valuesByTab.getOrDefault(tabName.toLowerCase(Locale.ROOT), new ObjectArrayList<>());
        }
        if (!force) {
            List<List<Object>> values = this.valuesByTab.get(tabName.toLowerCase(Locale.ROOT));
            if (values != null) {
                return values;
            }
        }
        List<List<Object>> result = fetchAll(tabName);
        valuesByTab.put(tabName.toLowerCase(Locale.ROOT), result);
        return result;
    }

    public Map<String, List<List<Object>>> loadValues(boolean force) {
        if (service != null && (force || this.valuesByTab.isEmpty())) {
            for (Map.Entry<String, List<List<Object>>> entry : fetchAll().entrySet()) {
                String tabNameLower = entry.getKey().toLowerCase(Locale.ROOT);
                this.valuesByTab.put(tabNameLower, entry.getValue());
            }
            if (this.valuesByTab.isEmpty()) {
                this.valuesByTab.put("", new ObjectArrayList<>());
            }
        }
        return this.valuesByTab;
    }

    public List<List<Object>> fetchAll(String tab) {
        if (tab == null || tab.isEmpty()) tab = getDefaultTab(true);
        if (service == null) return loadValues(false).get(tab);

        String range = tab; // Change this to the name of your sheet
        ValueRange response = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                () -> service.spreadsheets().values()
                        .get(spreadsheetId, range)
                        .execute());
        List<List<Object>> values = response.getValues();
        if (values == null) {
            return Collections.emptyList();
        }
        valuesByTab.put(tab.toLowerCase(Locale.ROOT), values);
        return values;
    }

    public Map<String, List<List<Object>>> fetchAll() {
        if (service == null) return loadValues(false);
        Map<String, List<List<Object>>> map = new LinkedHashMap<>();
        Spreadsheet spreadsheet = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                () -> service.spreadsheets().get(spreadsheetId).execute());
        for (Sheet sheet : spreadsheet.getSheets()) {
            String title = sheet.getProperties().getTitle();
            List<List<Object>> values = fetchAll(title);
            if (values != null) {
                map.put(title, values);
            }
        }
        return map;
    }

    public List<List<Object>> fetchRange(String range) {
        return fetchRange(getDefaultTab(), range);
    }

    public List<List<Object>> fetchRange(String tab, String range) {
        return fetchRange(tab, range, null);
    }

    public List<List<Object>> fetchRange(String tab, String range, Consumer<Sheets.Spreadsheets.Values.Get> onGet) {
        if (tab != null && !tab.isEmpty()) {
            range = tab + "!" + range;
        }
        try {
            Sheets.Spreadsheets.Values.Get query = service.spreadsheets().values().get(spreadsheetId, range);
            if (onGet != null) onGet.accept(query);
            ValueRange result = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                    () -> query.execute());
            return result.getValues();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, List<List<Object>>> fetchHeaderRows(Set<String> tabNames) {
        Map<String, List<List<Object>>> headers = new LinkedHashMap<>();
        if (service == null) {
            throw new IllegalStateException("Google Sheets service is not initialized.");
        }

        try {
            List<String> tabNamesList = new ObjectArrayList<>(tabNames);
            List<String> ranges = tabNamesList.stream()
                    .map(tab -> tab + "!1:1")
                    .collect(Collectors.toList());

            Sheets.Spreadsheets.Values.BatchGet request = service.spreadsheets().values().batchGet(spreadsheetId)
                    .setRanges(ranges)
                    .setValueRenderOption("FORMULA") // return formulas / raw cell input instead of displayed text
                    .setFields("valueRanges.values");

            BatchGetValuesResponse response = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                    () -> request.execute());

            List<ValueRange> valueRanges = response.getValueRanges();
            for (int i = 0; i < tabNamesList.size(); i++) {
                String tabName = tabNamesList.get(i);
                List<List<Object>> values = Collections.emptyList();
                if (valueRanges != null && i < valueRanges.size()) {
                    values = valueRanges.get(i).getValues();
                }
                headers.put(tabName, values != null ? values : Collections.emptyList());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch header rows", e);
        }

        return headers;
    }

    public void updateClearCurrentTab() throws IOException {
        if (this.defaultTab == null || this.defaultTab.isEmpty()) {
            updateClearFirstTab();
        } else {
            updateClearTab(null);
        }
    }

    public void updateClearFirstTab() throws IOException {
        if (service == null) {
            return;
        }
        // Wrapped execute to get spreadsheet then getSheets()
        String firstSheetId = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                () -> service.spreadsheets().get(spreadsheetId).execute()).getSheets().get(0).getProperties().getTitle();
        ClearValuesRequest requestBody = new ClearValuesRequest();
        // Wrapped execute for clear
        SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                () -> service.spreadsheets().values().clear(spreadsheetId, firstSheetId, requestBody).execute());
    }

    public void updateClearAll() throws IOException {
        if (service == null) {
            return;
        }
        Spreadsheet spreadsheet = null;
        try {
            // Wrapped execute
            spreadsheet = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                    () -> service.spreadsheets().get(spreadsheetId).execute());
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        List<Sheet> sheets = spreadsheet.getSheets();
        List<Request> requests = new ObjectArrayList<>();
        for (Sheet sheet : sheets) {
            ClearValuesRequest requestBody = new ClearValuesRequest();
            Sheets.Spreadsheets.Values.Clear request =
                    service.spreadsheets().values().clear(spreadsheetId, sheet.getProperties().getTitle(), requestBody);
            try {
                // Wrapped execute
                SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                        () -> request.execute());
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }

    public void clearAllButFirstRow(String tabName) throws IOException {
        String range = tabName + "!2:40000";
        // Create a new ClearValuesRequest
        ClearValuesRequest requestBody = new ClearValuesRequest();

        // Use the Sheets API to clear the values
        Sheets.Spreadsheets.Values.Clear request =
                service.spreadsheets().values().clear(spreadsheetId, range, requestBody);

        // Execute the request (wrapped)
        SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                () -> request.execute());
    }

    public void updateClearTab(String tab) throws IOException {
        if (service == null) {
            return;
        }
        if (tab == null) tab = getDefaultTab(true);
        ClearValuesRequest requestBody = new ClearValuesRequest();
        Sheets.Spreadsheets.Values.Clear request =
                service.spreadsheets().values().clear(spreadsheetId, tab, requestBody);

        // Wrapped execute
        SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                () -> request.execute());
    }

    public Map<Integer, String> fetchTabs() {
        Spreadsheet spreadsheet = null;
        try {
            // Wrapped execute
            spreadsheet = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                    () -> service.spreadsheets().get(spreadsheetId).execute());
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        if (spreadsheet == null) {
            throw new IllegalArgumentException("Spreadsheet not found or accessible: `" + spreadsheetId + "` (Are you sure the google account associated with the bot has access to it?)");
        }
        List<Sheet> sheets = spreadsheet.getSheets();
        Map<Integer, String> tabs = new LinkedHashMap<>();
        for (Sheet sheet : sheets) {
            SheetProperties prop = sheet.getProperties();
            tabs.put(prop.getSheetId(), prop.getTitle());
        }
        return tabs;
    }

    public Map<String, Integer> fetchTabsByName() {
        Map<String, Integer> tabsByName = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : fetchTabs().entrySet()) {
            tabsByName.put(entry.getValue(), entry.getKey());
        }
        return tabsByName;
    }

    public Map<String, Integer> getTabsByNameLower() {
        return fetchTabsByName().entrySet().stream().collect(Collectors.toMap(e -> e.getKey().toLowerCase(), Map.Entry::getValue));
    }

    public void updateDeleteTab(int tabId) {
        if (service == null) {
            return;
        }
        List<Request> requests = new ObjectArrayList<>();
        DeleteSheetRequest deleteSheetRequest = new DeleteSheetRequest();
        deleteSheetRequest.setSheetId(tabId);
        Request request = new Request();
        request.setDeleteSheet(deleteSheetRequest);
        requests.add(request);
        BatchUpdateSpreadsheetRequest requestBody = new BatchUpdateSpreadsheetRequest();
        requestBody.setRequests(requests);
        try {
            // Wrapped execute
            SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                    () -> service.spreadsheets().batchUpdate(spreadsheetId, requestBody).execute());
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    public void updateClearRange(String tab, String range) throws IOException {
        if (service == null) {
            return;
        }
        ClearValuesRequest requestBody = new ClearValuesRequest();
        Sheets.Spreadsheets.Values.Clear request =
                service.spreadsheets().values().clear(spreadsheetId, (tab == null || tab.isEmpty() ? "" : tab + "!") + range, requestBody);

        // Wrapped execute
        SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                () -> request.execute());
    }

    public void reset() {
        this.valuesByTab.clear();
    }

    public Map<String, String> toCsv() {
        if (this.valuesByTab.isEmpty()) return Collections.emptyMap();
        Map<String, String> results = new LinkedHashMap<>();
        for (Map.Entry<String, List<List<Object>>> entry : valuesByTab.entrySet()) {
            String tabName = entry.getKey();
            List<List<Object>> rows = entry.getValue();
            if (tabName.isEmpty() && rows.isEmpty()) continue;
            try (StringWriter stringWriter = new StringWriter()) {
                CSVWriter csvWriter = new CSVWriter(stringWriter);
                for (List<Object> rowObj : entry.getValue()) {
                    String[] row = new String[rowObj.size()];
                    for (int i = 0; i < rowObj.size(); i++) {
                        Object value = rowObj.get(i);
                        row[i] = value != null ? "" + value : "";
                    }
                    csvWriter.writeNext(row);
                }
                csvWriter.flush();
                results.put(tabName, stringWriter.toString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return results;
    }

    public List<Object> findColumn(String... arguments) {
        return findColumn(-1, arguments);
    }

    public List<Object> findColumn(int columnDefault, String... arguments) {
        checkNotNull(arguments);
        checkArgument(arguments.length > 0);
        return findColumn(columnDefault, s -> {
            for (String arg : arguments) {
                if (s.contains(arg.toLowerCase(Locale.ROOT))) return true;
            }
            return false;
        });
    }

    public List<Object> findColumn(int columnDefault, Predicate<String> acceptName) {
        Map<String, List<Object>> resultMap = findColumn(columnDefault, acceptName, false);
        if (resultMap == null || resultMap.isEmpty()) return null;
        return resultMap.values().iterator().next();
    }

    public Map<String, List<Object>> findColumn(int columnDefault2, Predicate<String> acceptName, boolean acceptMultiple) {
        if (valuesByTab.isEmpty()) throw new IllegalArgumentException("No values found. Was `loadValues` called?");
        List<List<Object>> values = getCachedValues(getDefaultTab(true));
        synchronized (values) {
            if (values.isEmpty()) {
                return null;
            }
        }
        Map<String, Integer> columnIds = new LinkedHashMap<>();
        Map<String, List<Object>> result = new LinkedHashMap<>();
        synchronized (values) {
            List<Object> header = values.get(0);
            outer:
            for (int i = 0; i < header.size(); i++) {
                Object obj = header.get(i);
                if (obj == null) continue;
                String objStr = obj.toString().toLowerCase(Locale.ROOT);
                if (!acceptName.test(objStr)) continue;
                columnIds.put(objStr, i);
                if (!acceptMultiple) {
                    break;
                }
            }
            if (columnIds.isEmpty() && columnDefault2 >= 0 && columnDefault2 < header.size()) {
                columnIds.put(header.get(columnDefault2).toString().toLowerCase(Locale.ROOT), columnDefault2);
            }
        }
        if (columnIds.isEmpty()) return null;

        for (Map.Entry<String, Integer> entry : columnIds.entrySet()) {
            String name = entry.getKey();
            int i = entry.getValue();
            List<Object> column = new ObjectArrayList<>(values.size());
            for (int j = 1; j < values.size(); j++) {
                List<Object> row = values.get(j);
                if (row.size() <= i) {
                    column.add(null);
                } else {
                    column.add(row.get(i));
                }
            }
            result.put(name, column);
        }
        return result;
    }

    public void setHeader(Object... header) {
        setHeader(Arrays.asList(header));
    }

    public void setHeader(List<?> header) {
        this.valuesByTab.clear();
        this.addRow(header);
    }
}

