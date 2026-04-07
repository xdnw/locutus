package link.locutus.discord.util.sheet;

import link.locutus.discord.util.RateLimitedSources;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.AddSheetResponse;
import com.google.api.services.sheets.v4.model.BatchClearValuesRequest;
import com.google.api.services.sheets.v4.model.BatchGetValuesResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.DeleteSheetRequest;
import com.google.api.services.sheets.v4.model.ExtendedValue;
import com.google.api.services.sheets.v4.model.GridCoordinate;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.api.services.sheets.v4.model.UpdateCellsRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.opencsv.CSVWriter;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.AddBalanceBuilder;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.TransactionNote;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.pnw.NationOrAllianceOrGuildOrTaxid;
import link.locutus.discord.util.io.BitBuffer;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class SpreadSheet {

    private static final String INSTRUCTIONS = """
            1. In the Google Cloud console, go to Menu menu > APIs & Services > Credentials.
            2. Go to Credentials <https://console.cloud.google.com/apis/credentials.
            3. Click Create Credentials > OAuth client ID.
            4. Click Application type > Desktop app (or web application).
            5. Download the credentials and save it to `config/credentials-sheets.json`""";

    private static final long HANDLE_CACHE_MINUTES = 30;
    private static final long PENDING_CACHE_MINUTES = 10;
    private static final long TAB_CACHE_TTL_MS = TimeUnit.SECONDS.toMillis(30);
    private static final long SERVICE_RETRY_COOLDOWN_MS = TimeUnit.SECONDS.toMillis(30);

    private static final String FIRST_TAB_SENTINEL = "\u0000:first-tab";

    private static final PassiveExpiringMap<String, SharedState> CACHE = new PassiveExpiringMap<>(HANDLE_CACHE_MINUTES,
            TimeUnit.MINUTES);

    private static final PassiveExpiringMap<String, PendingCreate> PENDING_CREATES = new PassiveExpiringMap<>(
            PENDING_CACHE_MINUTES, TimeUnit.MINUTES);

    private static final ExecutorService SHEET_IO = Executors.newFixedThreadPool(
            Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())),
            r -> {
                Thread t = new Thread(r, "SpreadSheet-io");
                t.setDaemon(true);
                return t;
            });

    public static boolean isSheet(String arg) {
        return arg.startsWith("https://docs.google.com/spreadsheets/") || arg.startsWith("sheet:");
    }

    public static <T> Set<T> parseSheet(String sheetId, List<String> expectedColumns, boolean defaultZero,
            BiFunction<Integer, String, T> parseCell) {
        List<String> expectedLower = new ObjectArrayList<>(expectedColumns.size());
        for (String col : expectedColumns) {
            expectedLower.add(col == null ? "" : col.toLowerCase(Locale.ROOT));
        }
        Function<String, Integer> parseColumnType = f -> {
            int index = expectedLower.indexOf(f.toLowerCase(Locale.ROOT));
            return index == -1 ? null : index;
        };
        return parseSheet(sheetId, expectedColumns, defaultZero, parseColumnType, parseCell);
    }

    public static <T> Set<T> parseSheet(String sheetId, List<String> expectedColumns, boolean defaultZero,
            Function<String, Integer> parseColumnType, BiFunction<Integer, String, T> parseCell) {
        SheetId keys = SpreadSheet.parseId(sheetId);
        SpreadSheet sheet;
        try {
            sheet = SpreadSheet.create(keys);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String tab = keys.tabName;
        if (tab == null)
            tab = sheet.getDefaultTab(true);
        List<List<Object>> rows = sheet.fetchAll(tab);
        synchronized (sheet.valuesLock) {
            sheet.valuesByTab.remove(normalizeTabKey(tab));
        }
        if (rows == null || rows.isEmpty())
            return Collections.emptySet();

        Set<T> toAdd = new ObjectLinkedOpenHashSet<>();
        Map<Integer, String> columnIndexToName = new LinkedHashMap<>();
        Map<Integer, Integer> columnIndexToColumnType = new LinkedHashMap<>();
        List<Object> header = rows.get(0);
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i) == null)
                continue;
            String name = header.get(i).toString().trim();
            if (name.isEmpty())
                continue;
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
            if (row == null || row.isEmpty())
                continue;

            for (Map.Entry<Integer, Integer> entry : columnIndexToColumnType.entrySet()) {
                int index = entry.getKey();
                if (index >= row.size())
                    continue;
                Object cell = row.get(index);
                if (cell == null)
                    continue;
                String cellStr = cell.toString().trim();
                if (cellStr.isEmpty())
                    continue;

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
            throw new IllegalArgumentException(
                    "Could not parse sheet: `" + sheetId + "`. Errors:\n- " + StringMan.join(invalid.values(), "\n- "));
        }
        return toAdd;
    }

    public static List<List<Object>> generateTransactionsListCells(List<Transaction2> transactions,
            boolean includeHeader, boolean ascending) {
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
                "note"));
        for (ResourceType value : ResourceType.values()) {
            if (value == ResourceType.CREDITS)
                continue;
            header.add(value.name());
        }

        if (includeHeader) {
            cells.add(new ObjectArrayList<>(header));
        }

        if (ascending) {
            transactions.sort(Comparator.comparingLong(o -> o.tx_datetime));
        } else {
            transactions.sort((o1, o2) -> Long.compare(o2.tx_datetime, o1.tx_datetime));
        }

        BitBuffer noteBuffer = Transaction2.createNoteBuffer();
        for (Transaction2 record : transactions) {
            String type;
            if (record.tx_id == -1) {
                type = "INTERNAL";
            } else if (record.sender_type == 1 && record.receiver_id == 2 && record.hasNoteTag(DepositType.TAX)) {
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
            header.set(8, record.getStructuredNote().toBytes(noteBuffer));
            int i = 9;
            for (ResourceType value : ResourceType.values()) {
                if (value == ResourceType.CREDITS)
                    continue;
                header.set(i++, record.resources[value.ordinal()]);
            }

            cells.add(new ObjectArrayList<>(header));
        }

        return cells;
    }

    public CompletableFuture<IMessageBuilder> addTransactionsList(IMessageIO channel, List<Transaction2> transactions,
            boolean includeHeader) throws IOException {
        List<List<Object>> cells = generateTransactionsListCells(transactions, includeHeader, true);
        if (includeHeader) {
            reset();
        }
        List<List<Object>> values = this.getCachedValues(null);
        synchronized (valuesLock) {
            values.addAll(cells);
        }
        this.updateClearTab(null);
        try {
            this.updateWrite();
            return attach(channel.create(), "transactions").send(RateLimitedSources.COMMAND_RESULT);
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
            return msg.append(e.getMessage()).send(RateLimitedSources.COMMAND_RESULT);
        }
    }

    public <T> T loadHeader(T instance, List<Object> headerStr) throws NoSuchFieldException, IllegalAccessException {
        for (int i = 0; i < headerStr.size(); i++) {
            Object columnObj = headerStr.get(i);
            if (columnObj == null)
                continue;
            String columnName = columnObj.toString().toLowerCase(Locale.ROOT).replaceAll("[^a-z_]", "");
            if (columnName.isEmpty())
                continue;
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
                throw new IllegalArgumentException(
                        "This command must be run in a guild, or a sheet must be specified for: " + key.name());
            }
            titleOrNull = dbOrNull.getGuild().getId() + "." + key.name();
        }

        String existing = dbOrNull == null || key == null ? null : dbOrNull.getInfo(key, true);
        if (existing != null && !existing.isEmpty()) {
            return create(existing, null);
        }

        PendingCreate pending = getOrCreatePendingCreate(dbOrNull, key, titleOrNull);
        SpreadSheet sheet = new SpreadSheet(pending);
        sheet.prepareAsync(); // start create in background
        return sheet;
    }

    public static SpreadSheet createTitle(String title) throws GeneralSecurityException, IOException {
        PendingCreate pending = new PendingCreate(title, null, null, null);
        SpreadSheet sheet = new SpreadSheet(pending);
        sheet.prepareAsync(); // start create in background
        return sheet;
    }

    public static SpreadSheet create(GuildDB db, SheetKey key) throws GeneralSecurityException, IOException {
        return createInternal(db, key, null);
    }

    public static SpreadSheet create(String id) throws IOException {
        return create(id, null);
    }

    private static SpreadSheet create(String id, Sheets api) {
        return create(parseId(id), api);
    }

    private static SpreadSheet create(SheetId id) throws IOException {
        return create(id, null);
    }

    private static SpreadSheet create(SheetId id, Sheets api) {
        SharedState state = getOrCreateSharedState(id.id, api);
        SpreadSheet sheet = new SpreadSheet(state);
        if (id.tabId != null) {
            sheet.defaultTabId = id.tabId;
            synchronized (sheet.valuesLock) {
                sheet.tabIdsByKey.put("", id.tabId);
            }
        }
        if (id.tabName != null) {
            sheet.defaultTab = id.tabName;
            synchronized (sheet.valuesLock) {
                sheet.tabNamesByKey.put(normalizeTabKey(id.tabName), id.tabName);
            }
        } else {
            sheet.defaultTab = "";
        }
        sheet.warmRemoteAsync();
        return sheet;
    }

    private final Object valuesLock = new Object();

    private volatile SharedState shared;
    private volatile PendingCreate pendingCreate;
    private volatile String spreadsheetId;

    private final Map<String, List<List<Object>>> valuesByTab = new LinkedHashMap<>();
    private final Map<String, String> tabNamesByKey = new LinkedHashMap<>();
    private final Map<String, Integer> tabIdsByKey = new LinkedHashMap<>();
    private final Set<String> queuedWriteTabs = new LinkedHashSet<>();
    private final Set<String> queuedClearTabs = new LinkedHashSet<>();

    private String defaultTab = "";
    private Integer defaultTabId = null;

    private SpreadSheet(SharedState shared) {
        this.shared = shared;
        this.spreadsheetId = shared.spreadsheetId;
    }

    private SpreadSheet(PendingCreate pendingCreate) {
        this.pendingCreate = pendingCreate;
        this.spreadsheetId = pendingCreate.localId;
    }

    public String getTitle() {
        syncFromPendingIfReady();
        PendingCreate pending = this.pendingCreate;
        if (pending != null) {
            return pending.title;
        }
        SharedState state = this.shared;
        if (state == null) {
            return spreadsheetId;
        }
        Sheets service = state.getService();
        if (service == null) {
            return spreadsheetId;
        }
        try {
            Spreadsheet spreadsheet = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                    () -> service.spreadsheets().get(state.spreadsheetId).setFields("properties(title)").execute());
            if (spreadsheet != null && spreadsheet.getProperties() != null
                    && spreadsheet.getProperties().getTitle() != null) {
                return spreadsheet.getProperties().getTitle();
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        return spreadsheetId;
    }

    public String getQualifiedId(boolean includeTab) {
        syncFromPendingIfReady();
        StringBuilder out = new StringBuilder("sheet:").append(spreadsheetId);
        if (includeTab) {
            if (defaultTabId != null) {
                out.append("#").append(defaultTabId);
            } else if (defaultTab != null && !defaultTab.isEmpty()) {
                out.append("#").append(defaultTab);
            }
        }
        return out.toString();
    }

    public record SheetId(String id, String tabName, Integer tabId) {
    }

    public static SheetId parseId(String id) {
        String tabName = null;
        Integer tabId = null;
        if (id.startsWith("sheet:")) {
            id = id.split(":", 2)[1];
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
                if (tabId == 0)
                    tabId = null;
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
        syncFromPendingIfReady();
        SharedState state = shared;
        return state == null ? null : state.getService();
    }

    public void setDefaultTab(Integer id) {
        SharedState state;
        try {
            flush();
            state = ensureRemoteReady();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (state == null) {
            throw new IllegalArgumentException("No google api server found, cannot select tab with id `" + id + "`");
        }
        ResolvedTab resolved = state.resolveById(id, true);
        if (resolved == null) {
            throw new IllegalArgumentException(
                    "No tab with id `" + id + "` found. Options: " + StringMan.getString(fetchTabs()));
        }
        setDefaultTab(resolved.title, resolved.id, false);
    }

    public void setDefaultTab(String defaultTab, Integer id) {
        setDefaultTab(defaultTab, id, false);
    }

    public void setDefaultTab(String defaultTab, Integer id, boolean createIfAbsent) {
        this.defaultTab = defaultTab == null ? "" : defaultTab;
        this.defaultTabId = id;

        synchronized (valuesLock) {
            if (!this.defaultTab.isEmpty()) {
                String key = normalizeTabKey(this.defaultTab);
                tabNamesByKey.put(key, this.defaultTab);
                if (id != null) {
                    tabIdsByKey.put(key, id);
                }
            }
            if (id != null) {
                tabIdsByKey.put("", id);
            }
        }

        if (createIfAbsent && this.defaultTab != null && !this.defaultTab.isEmpty()) {
            final String tabToCreate = this.defaultTab;
            CompletableFuture.runAsync(() -> {
                try {
                    SharedState state = ensureRemoteReady();
                    if (state == null)
                        return;
                    state.ensureTabsExist(Collections.singleton(tabToCreate));
                    ResolvedTab resolved = state.resolveByName(tabToCreate, true);
                    if (resolved != null) {
                        postResolveTab(normalizeTabKey(tabToCreate), resolved);
                    }
                } catch (Throwable ignore) {
                }
            }, SHEET_IO);
        }
    }

    public Map<String, Boolean> parseTransfers(AddBalanceBuilder builder, boolean negative,
            TransactionNote defaultNote) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        List<List<Object>> rows = fetchAll(null);
        if (rows == null || rows.isEmpty())
            return result;

        List<Object> header = rows.get(0);

        Integer noteI = null;
        for (int i = 0; i < header.size(); i++) {
            Object col = header.get(i);
            if (col != null && col.toString().equalsIgnoreCase("note"))
                noteI = i;
        }

        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.isEmpty() || row.size() < 2)
                continue;

            Object name = row.get(0);
            if (name == null)
                continue;
            String nameStr = name + "";
            if (nameStr.isEmpty())
                continue;

            Map<ResourceType, Double> transfer = new LinkedHashMap<>();
            for (int j = 1; j < row.size(); j++) {
                Object rssName = header.size() > j ? header.get(j) : null;
                if (rssName == null || rssName.toString().isEmpty() || (noteI != null && j == noteI))
                    continue;
                Object amtStr = row.get(j);
                if (amtStr == null || amtStr.toString().isEmpty())
                    continue;
                try {
                    ResourceType type = ResourceType.parse(rssName.toString());
                    if (type == null)
                        throw new IllegalArgumentException("Invalid resource: " + rssName);
                    Double amt = MathMan.parseDouble(amtStr.toString());
                    if (amt == null)
                        continue;
                    transfer.put(type, transfer.getOrDefault(type, 0d) + amt);
                    continue;
                } catch (IllegalArgumentException ignore) {
                    ignore.printStackTrace();
                }
                if (rssName.toString().equalsIgnoreCase("cost_raw")
                        || rssName.toString().equalsIgnoreCase("deposit_raw")
                        || rssName.toString().equalsIgnoreCase("resources")) {
                    for (Map.Entry<ResourceType, Double> entry : ResourceType.parseResources(amtStr.toString())
                            .entrySet()) {
                        transfer.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                }
            }
            if (transfer.isEmpty())
                continue;
            if (negative)
                transfer = ResourceType.subResourcesToA(new LinkedHashMap<>(), transfer);

            NationOrAllianceOrGuildOrTaxid account = PWBindings.parseNationOrAllianceOrGuildOrTaxId(
                    Locutus.cmd().getV2().getCommandRuntimeServices(), nameStr, true, null, null);
            if (account == null) {
                throw new IllegalArgumentException("Invalid nation/alliance/guild: `" + nameStr + "`");
            }
            TransactionNote note = defaultNote == null ? TransactionNote.empty() : defaultNote;
            if (noteI != null && row.size() > noteI)
                note = row.get(noteI) == null ? note
                        : TransactionNote.parseLegacy(row.get(noteI).toString(),
                                System.currentTimeMillis());
            builder.add(account, transfer, note);
        }
        return result;
    }

    public String getSpreadsheetId() {
        syncFromPendingIfReady();
        return spreadsheetId;
    }

    public String getURL() {
        return getURL(true);
    }

    public String getURL(boolean includeTab) {
        try {
            SharedState state = ensureRemoteReady();
            if (state == null) {
                return "sheet:" + spreadsheetId;
            }
            if (includeTab && defaultTabId == null) {
                tryResolveDefaultTabId();
            }
            return formatRemoteUrl(state.spreadsheetId, false)
                    + (includeTab && defaultTabId != null ? "#gid=" + defaultTabId : "");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public IMessageBuilder attach(IMessageBuilder msg, String name, String append) {
        return attach(msg, name).append(append);
    }

    public IMessageBuilder attach(IMessageBuilder msg, String name) {
        return attach(msg, MarkupUtil.escapeMarkdown(name), null, false, 0);
    }

    public IMessageBuilder attach(IMessageBuilder msg, String name, StringBuilder output, boolean allowInline,
            int currentLength) {
        SharedState state;
        try {
            flush();
            state = ensureRemoteReady();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String append;
        if (state == null) {
            Map<String, String> csvs = toCsv();
            if (csvs.isEmpty()) {
                append = "(`sheet:" + spreadsheetId + "`)";
            } else {
                int length = 0;
                for (String csv : csvs.values()) {
                    length += csv.length();
                }
                boolean willInline = length + currentLength + (9 * csvs.size()) < Message.MAX_CONTENT_LENGTH
                        && allowInline;

                StringBuilder sb = new StringBuilder();
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
                        if (sb.length() > 0)
                            sb.append("\n");
                        sb.append(title).append("```csv\n").append(csv).append("```");
                    } else {
                        append = "(`sheet:" + spreadsheetId + "`)";
                        msg.file(title + ".csv", csv);
                        if (sb.length() == 0) {
                            sb.append(append);
                        }
                    }
                }
                append = sb.toString();
            }
        } else {
            append = "\n" + (name == null ? "" : name + ": " + formatRemoteUrl(state.spreadsheetId, true));
        }

        if (output != null)
            output.append(append);
        else
            msg.append(append);
        return msg;
    }

    public IMessageBuilder send(IMessageIO io, String header, String footer) {
        if (header == null)
            header = "";
        if (footer == null)
            footer = "";

        SharedState state;
        try {
            flush();
            state = ensureRemoteReady();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (state == null) {
            Map<String, String> csvs = toCsv();
            IMessageBuilder msg = io.create();
            if (csvs.isEmpty()) {
                return msg.append(header)
                        .append(header.isEmpty() ? "" : "\n")
                        .append("sheet:" + spreadsheetId)
                        .append(footer);
            }

            int length = 0;
            for (String csv : csvs.values()) {
                length += csv.length();
            }
            boolean willInline = length + footer.length() + (9 * csvs.size()) < 2000;

            if (willInline) {
                StringBuilder sb = new StringBuilder();
                sb.append(header);
                if (!header.isEmpty())
                    sb.append("\n");
                for (Map.Entry<String, String> entry : csvs.entrySet()) {
                    sb.append(entry.getKey()).append("\n```csv\n").append(entry.getValue()).append("```\n");
                }
                sb.append(footer);
                msg.append(sb.toString());
            } else {
                msg.append(header);
                if (!header.isEmpty())
                    msg.append("\n");
                msg.append(footer);
                for (Map.Entry<String, String> entry : csvs.entrySet()) {
                    msg.file(entry.getKey() + ".csv", entry.getValue());
                }
            }
            return msg;
        }

        return io.create()
                .append(header)
                .append(header.isEmpty() ? "" : "\n")
                .append(formatRemoteUrl(state.spreadsheetId, false))
                .append(footer);
    }

    public String getURL(boolean allowFallback, boolean markdown) {
        try {
            SharedState state = ensureRemoteReady();
            if (state == null) {
                if (!allowFallback) {
                    throw new IllegalArgumentException(INSTRUCTIONS);
                }
                if (markdown) {
                    Map<String, String> csvs = toCsv();
                    if (csvs.isEmpty()) {
                        return "sheet:" + spreadsheetId;
                    }
                    int length = 0;
                    for (String csv : csvs.values()) {
                        length += csv.length();
                    }
                    if (length < 2000) {
                        StringBuilder output = new StringBuilder();
                        for (Map.Entry<String, String> entry : csvs.entrySet()) {
                            output.append(entry.getKey()).append("\n```csv\n").append(entry.getValue()).append("```\n");
                        }
                        return output.toString();
                    }
                    StringBuilder output = new StringBuilder();
                    for (Map.Entry<String, String> entry : csvs.entrySet()) {
                        output.append(entry.getKey()).append("\n").append(entry.getValue()).append("\n");
                    }
                    return output.toString();
                } else {
                    return "sheet:" + spreadsheetId;
                }
            }
            return formatRemoteUrl(state.spreadsheetId, markdown);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void addRow(String tab, List<?> list) {
        String requested = tab == null ? getDefaultTab() : tab;
        if (requested == null)
            requested = "";
        String key = normalizeTabKey(requested);

        synchronized (valuesLock) {
            if (!requested.isEmpty()) {
                tabNamesByKey.putIfAbsent(key, requested);
            }
            valuesByTab.computeIfAbsent(key, k -> new ObjectArrayList<>())
                    .add(formatRow(requested, new ObjectArrayList<>(list)));
        }
    }

    public String getDefaultTab() {
        return defaultTab == null ? "" : defaultTab;
    }

    public Integer getDefaultTabId() {
        return defaultTabId;
    }

    public String getDefaultTab(boolean useFirstTabIfNone) {
        if ((defaultTab == null || defaultTab.isEmpty()) && useFirstTabIfNone) {
            try {
                SharedState state = ensureRemoteReady();
                if (state == null) {
                    defaultTab = "";
                    return defaultTab;
                }

                ResolvedTab resolved = null;
                if (defaultTabId != null) {
                    resolved = state.resolveById(defaultTabId, true);
                }
                if (resolved == null) {
                    resolved = state.getFirstTab(true);
                }
                if (resolved != null) {
                    defaultTab = resolved.title;
                    defaultTabId = resolved.id;
                    postResolveTab("", resolved);
                    if (resolved.title != null && !resolved.title.isEmpty()) {
                        migrateLocalTabKey("", resolved.title);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return defaultTab == null ? "" : defaultTab;
    }

    public List<List<Object>> getCachedValues(String tab) {
        String requested = tab == null ? getDefaultTab() : tab;
        if (requested == null)
            requested = "";
        String key = normalizeTabKey(requested);

        synchronized (valuesLock) {
            if (!requested.isEmpty()) {
                tabNamesByKey.putIfAbsent(key, requested);
            }
            return valuesByTab.computeIfAbsent(key, k -> new ObjectArrayList<>());
        }
    }

    public void addRow(List<?> row) {
        addRow((String) null, row);
    }

    public void addRow(Object... values) {
        addRow((String) null, Arrays.asList(values));
    }

    private List<Object> formatRow(String tab, List<?> row) {
        List<Object> out = new ObjectArrayList<>(row.size());
        Integer rowCount = null;

        String requested = tab == null ? getDefaultTab() : tab;
        if (requested == null)
            requested = "";
        String key = normalizeTabKey(requested);

        for (int i = 0; i < row.size(); i++) {
            Object val = row.get(i);
            if (!(val instanceof String)) {
                out.add(val);
                continue;
            }

            String s = val.toString();
            boolean hasRow = s.contains("$row");
            boolean hasCol = s.contains("$column");
            if (!hasRow && !hasCol) {
                out.add(val);
                continue;
            }

            if (hasRow && rowCount == null) {
                synchronized (valuesLock) {
                    rowCount = valuesByTab.computeIfAbsent(key, k -> new ObjectArrayList<>()).size();
                }
            }

            if (hasRow)
                s = s.replace("$row", String.valueOf(rowCount + 1));
            if (hasCol)
                s = s.replace("$column", SheetUtil.getLetter(i + 1));
            out.add(s);
        }
        return out;
    }

    public void updateWrite(String tab, List<RowData> rowData) throws IOException {
        flush();

        SharedState state = ensureRemoteReady();
        if (state == null) {
            reset();
            for (RowData row : rowData) {
                List<Object> dataSimple = new ObjectArrayList<>();
                for (CellData cell : row.getValues()) {
                    ExtendedValue value = cell.getUserEnteredValue();
                    dataSimple.add(value == null ? "" : value.toString());
                }
                addRow(tab, dataSimple);
            }
            return;
        }

        String requested = tab == null ? getDefaultTab() : tab;
        if (requested == null)
            requested = "";
        String key = normalizeTabKey(requested);
        ResolvedTab resolved = resolveSingleRemoteTab(state, key, requested, true);
        if (resolved == null) {
            throw new IllegalArgumentException("Tab not found: " + requested);
        }

        UpdateCellsRequest appendCellReq = new UpdateCellsRequest();
        appendCellReq.setRows(rowData);
        appendCellReq.setFields("userEnteredValue,note");

        GridCoordinate start = new GridCoordinate();
        start.setColumnIndex(0);
        start.setRowIndex(0);
        if (resolved.id != null) {
            start.setSheetId(resolved.id);
        }
        appendCellReq.setStart(start);

        List<Request> requests = new ObjectArrayList<>();
        requests.add(new Request().setUpdateCells(appendCellReq));
        BatchUpdateSpreadsheetRequest batchRequests = new BatchUpdateSpreadsheetRequest();
        batchRequests.setRequests(requests);

        SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                () -> state.getService().spreadsheets().batchUpdate(state.spreadsheetId, batchRequests).execute());
    }

    /**
     * Checks if the provided tabs exist in the Google Spreadsheet. If a tab does
     * not exist, it is created.
     * The method returns a map where the keys are the tab names in lower case and
     * the values are Booleans
     * indicating whether the tab was created during the method execution.
     */
    public Map<String, Boolean> updateCreateTabsIfAbsent(Set<String> tabs) throws IOException {
        flush();

        SharedState state = ensureRemoteReady();
        if (state == null) {
            throw new IllegalStateException(INSTRUCTIONS);
        }

        TabsSnapshot before = state.getTabsSnapshot(true);
        Map<String, Boolean> result = new LinkedHashMap<>();
        LinkedHashMap<String, String> missing = new LinkedHashMap<>();

        for (String tab : tabs) {
            if (tab == null || tab.isEmpty())
                continue;
            String key = normalizeTabKey(tab);
            ResolvedTab found = before.findByName(tab);
            if (found != null) {
                result.put(key, false);
                postResolveTab(key, found);
            } else {
                missing.put(key, tab);
            }
        }

        if (!missing.isEmpty()) {
            state.ensureTabsExist(missing.values());
            for (Map.Entry<String, String> entry : missing.entrySet()) {
                ResolvedTab now = state.resolveByName(entry.getValue(), true);
                if (now != null) {
                    postResolveTab(entry.getKey(), now);
                    result.put(entry.getKey(), !before.byNameLower.containsKey(entry.getKey()));
                } else {
                    result.put(entry.getKey(), false);
                }
            }
        }

        return result;
    }

    public Integer updateCreateTabIfAbsent(String tabName) throws IOException {
        flush();

        SharedState state = ensureRemoteReady();
        if (state == null) {
            return null;
        }

        ResolvedTab existing = state.resolveByName(tabName, true);
        if (existing != null) {
            postResolveTab(normalizeTabKey(tabName), existing);
            return existing.id;
        }

        Map<String, Integer> created = state.ensureTabsExist(Collections.singleton(tabName));
        Integer id = created.get(normalizeTabKey(tabName));
        if (id != null) {
            ResolvedTab resolved = state.resolveById(id, false);
            if (resolved != null) {
                postResolveTab(normalizeTabKey(tabName), resolved);
                return resolved.id;
            }
        }
        return id;
    }

    private Integer updateAddTab(String tabName) {
        try {
            flush();
            SharedState state = ensureRemoteReady();
            if (state == null)
                return null;
            Integer id = state.addTabDirect(tabName);
            if (id != null) {
                ResolvedTab resolved = state.resolveById(id, false);
                if (resolved != null) {
                    postResolveTab(normalizeTabKey(tabName), resolved);
                }
            }
            return id;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Queues writes. They are flushed automatically on attach/send/remote reads, or
     * by calling flush().
     */
    public void updateWrite() throws IOException {
        synchronized (valuesLock) {
            if (valuesByTab.isEmpty()) {
                return;
            }
            queuedWriteTabs.addAll(valuesByTab.keySet());
        }
    }

    /**
     * Queues a write for the given tab. They are flushed automatically on
     * attach/send/remote reads, or by calling flush().
     */
    public void updateWrite(String tabName) throws IOException {
        String requested = tabName == null ? getDefaultTab() : tabName;
        if (requested == null)
            requested = "";
        String key = normalizeTabKey(requested);
        synchronized (valuesLock) {
            if (!requested.isEmpty()) {
                tabNamesByKey.putIfAbsent(key, requested);
            }
            queuedWriteTabs.add(key);
        }
    }

    /**
     * Flushes any queued clear/write operations.
     * Also forces pending new-sheet creation if needed.
     */
    public synchronized void flush() throws IOException {
        SharedState state = ensureRemoteReady();
        if (state == null) {
            return;
        }

        LinkedHashSet<String> clearKeys;
        LinkedHashSet<String> writeKeys;
        LinkedHashMap<String, List<List<Object>>> valuesSnapshot;

        synchronized (valuesLock) {
            if (queuedClearTabs.isEmpty() && queuedWriteTabs.isEmpty()) {
                return;
            }

            clearKeys = new LinkedHashSet<>(queuedClearTabs);
            writeKeys = new LinkedHashSet<>(queuedWriteTabs);
            valuesSnapshot = new LinkedHashMap<>(valuesByTab.size());
            for (Map.Entry<String, List<List<Object>>> entry : valuesByTab.entrySet()) {
                valuesSnapshot.put(entry.getKey(), copyRows(entry.getValue()));
            }
        }

        LinkedHashSet<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(clearKeys);
        allKeys.addAll(writeKeys);

        Map<String, ResolvedTab> resolvedByKey = new LinkedHashMap<>();
        LinkedHashMap<String, String> missingWriteTabs = new LinkedHashMap<>();

        for (String key : allKeys) {
            String requestedName = getRequestedTabNameForKey(key);
            ResolvedTab resolved = resolveSingleRemoteTab(state, key, requestedName, false);
            if (resolved != null) {
                resolvedByKey.put(key, resolved);
            } else if (writeKeys.contains(key) && requestedName != null && !requestedName.isEmpty()) {
                missingWriteTabs.put(key, requestedName);
            }
        }

        if (!missingWriteTabs.isEmpty()) {
            state.ensureTabsExist(missingWriteTabs.values());
            for (Map.Entry<String, String> entry : missingWriteTabs.entrySet()) {
                ResolvedTab resolved = resolveSingleRemoteTab(state, entry.getKey(), entry.getValue(), false);
                if (resolved != null) {
                    resolvedByKey.put(entry.getKey(), resolved);
                }
            }
        }

        for (String key : writeKeys) {
            if (!resolvedByKey.containsKey(key)) {
                throw new IllegalArgumentException("Tab not found: `" + getRequestedTabNameForKey(key) + "`");
            }
        }

        List<String> clearRanges = new ObjectArrayList<>();
        for (String key : clearKeys) {
            ResolvedTab tab = resolvedByKey.get(key);
            if (tab != null && tab.title != null && !tab.title.isEmpty()) {
                clearRanges.add(quoteSheetName(tab.title));
            }
        }

        List<ValueRange> data = new ObjectArrayList<>();
        for (String key : writeKeys) {
            ResolvedTab tab = resolvedByKey.get(key);
            if (tab == null || tab.title == null || tab.title.isEmpty())
                continue;

            List<List<Object>> values = valuesSnapshot.get(key);
            if (values == null || values.isEmpty())
                continue;

            int width = maxWidth(values);
            if (width <= 0)
                width = 1;

            String range = withTab(tab.title, SheetUtil.getRange(0, 0, width - 1, values.size() - 1));
            data.add(new ValueRange().setRange(range).setValues(values));
        }

        if (!clearRanges.isEmpty()) {
            BatchClearValuesRequest clearBody = new BatchClearValuesRequest().setRanges(clearRanges);
            SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                    () -> state.getService().spreadsheets().values().batchClear(state.spreadsheetId, clearBody)
                            .execute());
        }

        if (!data.isEmpty()) {
            BatchUpdateValuesRequest writeBody = new BatchUpdateValuesRequest()
                    .setValueInputOption("USER_ENTERED")
                    .setData(data);

            SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                    () -> state.getService().spreadsheets().values().batchUpdate(state.spreadsheetId, writeBody)
                            .execute());
        }

        synchronized (valuesLock) {
            queuedClearTabs.removeAll(clearKeys);
            queuedWriteTabs.removeAll(writeKeys);
            valuesByTab.clear();
        }
    }

    public List<List<Object>> get(int x1, int y1, int x2, int y2) {
        return fetchRange(this.getDefaultTab(), SheetUtil.getRange(x1, y1, x2, y2));
    }

    public List<List<Object>> loadValuesCurrentTab(boolean force) {
        return loadValues(getDefaultTab(true), force);
    }

    public List<List<Object>> loadValues(String tabName, boolean force) {
        String actualTab = (tabName == null || tabName.isEmpty()) ? getDefaultTab(true) : tabName;
        String key = normalizeTabKey(actualTab);

        if (!force && !hasPendingRemoteState()) {
            synchronized (valuesLock) {
                List<List<Object>> values = valuesByTab.get(key);
                if (values != null) {
                    return values;
                }
            }
        }

        if (shared != null || pendingCreate != null) {
            return fetchAll(actualTab);
        }

        synchronized (valuesLock) {
            return valuesByTab.computeIfAbsent(key, k -> new ObjectArrayList<>());
        }
    }

    public Map<String, List<List<Object>>> loadValues(boolean force) {
        if (force || hasPendingRemoteState() || isRemoteBacked() && isLocalCacheEmpty()) {
            fetchAll();
        }
        synchronized (valuesLock) {
            if (valuesByTab.isEmpty()) {
                valuesByTab.put("", new ObjectArrayList<>());
            }
            return valuesByTab;
        }
    }

    public List<List<Object>> fetchAll(String tab) {
        try {
            flush();
            SharedState state = ensureRemoteReady();

            String actualTab = tab;
            if (actualTab == null || actualTab.isEmpty())
                actualTab = getDefaultTab(true);

            if (state == null) {
                if (shared != null) {
                    throw new IllegalArgumentException(INSTRUCTIONS);
                }
                synchronized (valuesLock) {
                    return valuesByTab.getOrDefault(normalizeTabKey(actualTab), new ObjectArrayList<>());
                }
            }

            ResolvedTab resolved = state.resolveByName(actualTab, false);
            if (resolved != null) {
                actualTab = resolved.title;
                postResolveTab(normalizeTabKey(tab == null ? actualTab : tab), resolved);
            }

            String range = quoteSheetName(actualTab);
            ValueRange response = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                    () -> state.getService().spreadsheets().values().get(state.spreadsheetId, range).execute());

            List<List<Object>> values = copyRows(response == null ? null : response.getValues());
            String key = normalizeTabKey(actualTab);
            synchronized (valuesLock) {
                valuesByTab.put(key, values);
                tabNamesByKey.put(key, actualTab);
                if (resolved != null && resolved.id != null) {
                    tabIdsByKey.put(key, resolved.id);
                }
            }
            return values;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, List<List<Object>>> fetchAll() {
        try {
            flush();
            SharedState state = ensureRemoteReady();
            if (state == null) {
                if (shared != null) {
                    throw new IllegalArgumentException(INSTRUCTIONS);
                }
                synchronized (valuesLock) {
                    return new LinkedHashMap<>(valuesByTab);
                }
            }

            TabsSnapshot tabs = state.getTabsSnapshot(false);
            if (tabs.byId.isEmpty()) {
                tabs = state.getTabsSnapshot(true);
            }
            if (tabs.byId.isEmpty()) {
                return Collections.emptyMap();
            }

            List<String> titles = new ObjectArrayList<>(tabs.byId.values());
            List<String> ranges = new ObjectArrayList<>(titles.size());
            for (String title : titles) {
                ranges.add(quoteSheetName(title));
            }

            BatchGetValuesResponse response;
            try {
                Sheets.Spreadsheets.Values.BatchGet request = state.getService().spreadsheets().values()
                        .batchGet(state.spreadsheetId)
                        .setRanges(ranges)
                        .setFields("valueRanges(values)");
                response = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS, request::execute);
            } catch (RuntimeException e) {
                state.invalidateTabs();
                tabs = state.getTabsSnapshot(true);
                titles = new ObjectArrayList<>(tabs.byId.values());
                ranges = new ObjectArrayList<>(titles.size());
                for (String title : titles) {
                    ranges.add(quoteSheetName(title));
                }
                Sheets.Spreadsheets.Values.BatchGet request = state.getService().spreadsheets().values()
                        .batchGet(state.spreadsheetId)
                        .setRanges(ranges)
                        .setFields("valueRanges(values)");
                response = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS, request::execute);
            }

            List<ValueRange> valueRanges = response == null ? null : response.getValueRanges();
            Map<String, List<List<Object>>> map = new LinkedHashMap<>();

            synchronized (valuesLock) {
                for (int i = 0; i < titles.size(); i++) {
                    String title = titles.get(i);
                    List<List<Object>> values = Collections.emptyList();
                    if (valueRanges != null && i < valueRanges.size() && valueRanges.get(i) != null) {
                        values = valueRanges.get(i).getValues();
                    }
                    List<List<Object>> copied = copyRows(values);
                    String key = normalizeTabKey(title);
                    valuesByTab.put(key, copied);
                    tabNamesByKey.put(key, title);
                    Integer tabId = tabs.byNameLower.get(key);
                    if (tabId != null) {
                        tabIdsByKey.put(key, tabId);
                    }
                    map.put(title, copied);
                }
            }
            return map;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<List<Object>> fetchRange(String range) {
        return fetchRange(getDefaultTab(), range);
    }

    public List<List<Object>> fetchRange(String tab, String range) {
        return fetchRange(tab, range, null);
    }

    public List<List<Object>> fetchRange(String tab, String range, Consumer<Sheets.Spreadsheets.Values.Get> onGet) {
        try {
            flush();
            SharedState state = ensureRemoteReady();
            if (state == null) {
                throw new IllegalStateException(INSTRUCTIONS);
            }

            String actualTab = tab;
            if (actualTab == null || actualTab.isEmpty()) {
                actualTab = getDefaultTab(true);
            }

            String finalRange = (actualTab != null && !actualTab.isEmpty()) ? withTab(actualTab, range) : range;
            Sheets.Spreadsheets.Values.Get query = state.getService().spreadsheets().values().get(state.spreadsheetId,
                    finalRange);
            if (onGet != null)
                onGet.accept(query);

            ValueRange result = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS, query::execute);
            return copyRows(result == null ? null : result.getValues());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, List<List<Object>>> fetchHeaderRows(Set<String> tabNames) {
        try {
            flush();
            SharedState state = ensureRemoteReady();
            if (state == null) {
                throw new IllegalStateException("Google Sheets service is not initialized.");
            }

            List<String> tabNamesList = new ObjectArrayList<>(tabNames);
            List<String> ranges = new ObjectArrayList<>(tabNamesList.size());
            for (String tab : tabNamesList) {
                ranges.add(withTab(tab, "1:1"));
            }

            Sheets.Spreadsheets.Values.BatchGet request = state.getService().spreadsheets().values()
                    .batchGet(state.spreadsheetId)
                    .setRanges(ranges)
                    .setValueRenderOption("FORMULA")
                    .setFields("valueRanges(values)");

            BatchGetValuesResponse response = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS, request::execute);

            Map<String, List<List<Object>>> headers = new LinkedHashMap<>();
            List<ValueRange> valueRanges = response == null ? null : response.getValueRanges();
            for (int i = 0; i < tabNamesList.size(); i++) {
                String tabName = tabNamesList.get(i);
                List<List<Object>> values = Collections.emptyList();
                if (valueRanges != null && i < valueRanges.size() && valueRanges.get(i) != null) {
                    values = valueRanges.get(i).getValues();
                }
                headers.put(tabName, copyRows(values));
            }
            return headers;
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch header rows", e);
        }
    }

    /**
     * Queues a clear for the current tab.
     */
    public void updateClearCurrentTab() throws IOException {
        updateClearTab(null);
    }

    /**
     * Queues a clear for the first tab specifically.
     */
    public void updateClearFirstTab() throws IOException {
        synchronized (valuesLock) {
            queuedClearTabs.add(FIRST_TAB_SENTINEL);
        }
    }

    /**
     * Queues a clear for the given tab.
     */
    public void updateClearTab(String tab) throws IOException {
        String requested = tab == null ? getDefaultTab() : tab;
        if (requested == null)
            requested = "";
        String key = normalizeTabKey(requested);

        synchronized (valuesLock) {
            if (!requested.isEmpty()) {
                tabNamesByKey.putIfAbsent(key, requested);
            }
            queuedClearTabs.add(key);
        }
    }

    public void updateClearAll() throws IOException {
        flush();

        SharedState state = ensureRemoteReady();
        if (state == null) {
            return;
        }

        TabsSnapshot snapshot = state.getTabsSnapshot(true);
        if (snapshot.byId.isEmpty())
            return;

        List<String> ranges = new ObjectArrayList<>(snapshot.byId.size());
        for (String title : snapshot.byId.values()) {
            ranges.add(quoteSheetName(title));
        }

        BatchClearValuesRequest requestBody = new BatchClearValuesRequest().setRanges(ranges);
        SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                () -> state.getService().spreadsheets().values().batchClear(state.spreadsheetId, requestBody)
                        .execute());
    }

    public void clearAllButFirstRow(String tabName) throws IOException {
        flush();

        SharedState state = ensureRemoteReady();
        if (state == null) {
            return;
        }

        ClearValuesRequest requestBody = new ClearValuesRequest();
        Sheets.Spreadsheets.Values.Clear request = state.getService().spreadsheets().values().clear(state.spreadsheetId,
                withTab(tabName, "2:40000"), requestBody);

        SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS, request::execute);
    }

    public Map<Integer, String> fetchTabs() {
        try {
            flush();
            SharedState state = ensureRemoteReady();
            if (state == null) {
                throw new IllegalArgumentException("Spreadsheet not found or accessible: `" + spreadsheetId
                        + "` (Are you sure the google account associated with the bot has access to it?)");
            }

            TabsSnapshot snapshot = state.getTabsSnapshot(true);
            if (snapshot.byId.isEmpty()) {
                throw new IllegalArgumentException("Spreadsheet not found or accessible: `" + spreadsheetId
                        + "` (Are you sure the google account associated with the bot has access to it?)");
            }
            return new LinkedHashMap<>(snapshot.byId);
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Spreadsheet not found or accessible: `" + spreadsheetId
                    + "` (Are you sure the google account associated with the bot has access to it?)", e);
        }
    }

    public Map<String, Integer> fetchTabsByName() {
        Map<String, Integer> tabsByName = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : fetchTabs().entrySet()) {
            tabsByName.put(entry.getValue(), entry.getKey());
        }
        return tabsByName;
    }

    public Map<String, Integer> getTabsByNameLower() {
        syncFromPendingIfReady();
        SharedState state = shared;
        if (state == null) {
            synchronized (valuesLock) {
                Map<String, Integer> out = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> entry : tabIdsByKey.entrySet()) {
                    if (!entry.getKey().isEmpty()) {
                        out.put(entry.getKey(), entry.getValue());
                    }
                }
                return out;
            }
        }
        TabsSnapshot snapshot = state.getTabsSnapshot(false);
        return new LinkedHashMap<>(snapshot.byNameLower);
    }

    public void updateDeleteTab(int tabId) {
        try {
            flush();
            SharedState state = ensureRemoteReady();
            if (state == null) {
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

            SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                    () -> state.getService().spreadsheets().batchUpdate(state.spreadsheetId, requestBody).execute());

            state.removeTabFromCache(tabId);
            if (Objects.equals(defaultTabId, tabId)) {
                defaultTab = "";
                defaultTabId = null;
            }
            synchronized (valuesLock) {
                tabIdsByKey.entrySet().removeIf(e -> Objects.equals(e.getValue(), tabId));
            }
        } catch (RuntimeException | IOException e) {
            e.printStackTrace();
        }
    }

    public void updateClearRange(String tab, String range) throws IOException {
        flush();

        SharedState state = ensureRemoteReady();
        if (state == null) {
            return;
        }

        ClearValuesRequest requestBody = new ClearValuesRequest();
        Sheets.Spreadsheets.Values.Clear request = state.getService().spreadsheets().values().clear(
                state.spreadsheetId,
                (tab == null || tab.isEmpty() ? range : withTab(tab, range)),
                requestBody);

        SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS, request::execute);
    }

    public void reset() {
        synchronized (valuesLock) {
            this.valuesByTab.clear();
            this.queuedWriteTabs.clear();
            this.queuedClearTabs.clear();
        }
    }

    public Map<String, String> toCsv() {
        Map<String, List<List<Object>>> snapshot = new LinkedHashMap<>();
        synchronized (valuesLock) {
            if (this.valuesByTab.isEmpty())
                return Collections.emptyMap();
            for (Map.Entry<String, List<List<Object>>> entry : this.valuesByTab.entrySet()) {
                snapshot.put(entry.getKey(), copyRows(entry.getValue()));
            }
        }

        Map<String, String> results = new LinkedHashMap<>();
        for (Map.Entry<String, List<List<Object>>> entry : snapshot.entrySet()) {
            String tabKey = entry.getKey();
            List<List<Object>> rows = entry.getValue();
            if (tabKey.isEmpty() && rows.isEmpty())
                continue;

            String displayName = displayTabName(tabKey);

            try (StringWriter stringWriter = new StringWriter()) {
                CSVWriter csvWriter = new CSVWriter(stringWriter);
                for (List<Object> rowObj : rows) {
                    String[] row = new String[rowObj.size()];
                    for (int i = 0; i < rowObj.size(); i++) {
                        Object value = rowObj.get(i);
                        row[i] = value != null ? "" + value : "";
                    }
                    csvWriter.writeNext(row);
                }
                csvWriter.flush();
                results.put(displayName, stringWriter.toString());
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
                if (s.contains(arg.toLowerCase(Locale.ROOT)))
                    return true;
            }
            return false;
        });
    }

    public List<Object> findColumn(int columnDefault, Predicate<String> acceptName) {
        Map<String, List<Object>> resultMap = findColumn(columnDefault, acceptName, false);
        if (resultMap == null || resultMap.isEmpty())
            return null;
        return resultMap.values().iterator().next();
    }

    public Map<String, List<Object>> findColumn(int columnDefault2, Predicate<String> acceptName,
            boolean acceptMultiple) {
        synchronized (valuesLock) {
            if (valuesByTab.isEmpty())
                throw new IllegalArgumentException("No values found. Was `loadValues` called?");
        }

        List<List<Object>> values = getCachedValues(getDefaultTab(true));
        if (values.isEmpty()) {
            return null;
        }

        Map<String, Integer> columnIds = new LinkedHashMap<>();
        Map<String, List<Object>> result = new LinkedHashMap<>();

        List<Object> header = values.get(0);
        for (int i = 0; i < header.size(); i++) {
            Object obj = header.get(i);
            if (obj == null)
                continue;
            String objStr = obj.toString().toLowerCase(Locale.ROOT);
            if (!acceptName.test(objStr))
                continue;
            columnIds.put(objStr, i);
            if (!acceptMultiple) {
                break;
            }
        }

        if (columnIds.isEmpty() && columnDefault2 >= 0 && columnDefault2 < header.size()) {
            columnIds.put(header.get(columnDefault2).toString().toLowerCase(Locale.ROOT), columnDefault2);
        }
        if (columnIds.isEmpty())
            return null;

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
        synchronized (valuesLock) {
            this.valuesByTab.clear();
        }
        this.addRow(header);
    }

    // ---------- internals ----------

    private CompletableFuture<SharedState> prepareAsync() {
        PendingCreate pending = pendingCreate;
        if (pending != null) {
            return startPendingCreateIfNeeded(pending);
        }
        SharedState state = shared;
        if (state != null) {
            warmRemoteAsync();
            return CompletableFuture.completedFuture(state);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void warmRemoteAsync() {
        SharedState state = this.shared;
        if (state == null)
            return;

        boolean warmTabs = defaultTabId != null || defaultTab == null || defaultTab.isEmpty();
        CompletableFuture.runAsync(() -> {
            Sheets api = state.getService();
            if (api != null && warmTabs) {
                try {
                    state.getTabsSnapshot(false);
                } catch (RuntimeException ignore) {
                }
            }
        }, SHEET_IO);
    }

    private SharedState ensureRemoteReady() throws IOException {
        syncFromPendingIfReady();

        SharedState state = shared;
        if (state != null) {
            return state.getService() == null ? null : state;
        }

        PendingCreate pending = pendingCreate;
        if (pending == null) {
            return null;
        }

        CompletableFuture<SharedState> future = startPendingCreateIfNeeded(pending);
        try {
            state = future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while preparing spreadsheet", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IOException("Failed to prepare spreadsheet", cause);
        }

        if (state != null) {
            attachSharedState(state);
            return state.getService() == null ? null : state;
        }
        return null;
    }

    private void attachSharedState(SharedState state) {
        this.shared = state;
        this.spreadsheetId = state.spreadsheetId;
        this.pendingCreate = null;
    }

    private void syncFromPendingIfReady() {
        PendingCreate pending = this.pendingCreate;
        if (pending == null)
            return;
        CompletableFuture<SharedState> future = pending.future;
        if (future == null || !future.isDone() || future.isCompletedExceptionally())
            return;
        try {
            SharedState ready = future.getNow(null);
            if (ready != null) {
                attachSharedState(ready);
            }
        } catch (Throwable ignore) {
        }
    }

    private CompletableFuture<SharedState> startPendingCreateIfNeeded(PendingCreate pending) {
        CompletableFuture<SharedState> future = pending.future;
        if (future != null)
            return future;

        synchronized (pending.lock) {
            future = pending.future;
            if (future != null)
                return future;

            CompletableFuture<SharedState> createdFuture = CompletableFuture
                    .supplyAsync(() -> createRemoteForPending(pending), SHEET_IO);
            pending.future = createdFuture;

            createdFuture.whenComplete((state, ex) -> {
                if (state != null) {
                    removePendingCreate(pending);
                } else {
                    synchronized (pending.lock) {
                        if (pending.future == createdFuture) {
                            pending.future = null;
                        }
                    }
                }
            });

            return createdFuture;
        }
    }

    private static SharedState createRemoteForPending(PendingCreate pending) {
        Sheets api;
        try {
            api = SheetUtil.getSheetService();
        } catch (IOException e) {
            return null;
        }

        Spreadsheet requestBody = new Spreadsheet()
                .setProperties(new SpreadsheetProperties().setTitle(pending.title));

        Spreadsheet created = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                () -> api.spreadsheets().create(requestBody)
                        .setFields("spreadsheetId,sheets(properties(sheetId,title))")
                        .execute());

        if (created == null || created.getSpreadsheetId() == null) {
            return null;
        }

        SharedState state = getOrCreateSharedState(created.getSpreadsheetId(), api);
        state.seedTabsFromSpreadsheet(created);

        if (pending.db != null && pending.key != null) {
            pending.db.setInfo(pending.key, created.getSpreadsheetId());
        }

        state.scheduleShareIfNeeded();
        return state;
    }

    private boolean hasPendingRemoteState() {
        synchronized (valuesLock) {
            if (!queuedClearTabs.isEmpty() || !queuedWriteTabs.isEmpty()) {
                return true;
            }
        }
        return pendingCreate != null && shared == null;
    }

    private boolean isRemoteBacked() {
        return shared != null || pendingCreate != null;
    }

    private boolean isLocalCacheEmpty() {
        synchronized (valuesLock) {
            return valuesByTab.isEmpty();
        }
    }

    private void tryResolveDefaultTabId() {
        SharedState state = shared;
        if (state == null)
            return;
        if (defaultTabId != null)
            return;

        try {
            if (defaultTab != null && !defaultTab.isEmpty()) {
                ResolvedTab resolved = resolveSingleRemoteTab(state, normalizeTabKey(defaultTab), defaultTab, false);
                if (resolved != null) {
                    defaultTabId = resolved.id;
                }
            } else {
                ResolvedTab first = state.getFirstTab(false);
                if (first != null) {
                    defaultTab = first.title;
                    defaultTabId = first.id;
                    postResolveTab("", first);
                }
            }
        } catch (IOException ignore) {
        }
    }

    private String getRequestedTabNameForKey(String key) {
        if (FIRST_TAB_SENTINEL.equals(key))
            return null;

        synchronized (valuesLock) {
            String name = tabNamesByKey.get(key);
            if ((name == null || name.isEmpty()) && key.isEmpty() && defaultTab != null && !defaultTab.isEmpty()) {
                name = defaultTab;
            }
            if ((name == null || name.isEmpty()) && !key.isEmpty()) {
                name = key;
            }
            return name;
        }
    }

    private ResolvedTab resolveSingleRemoteTab(SharedState state, String key, String requestedName,
            boolean createIfAbsent) throws IOException {
        if (FIRST_TAB_SENTINEL.equals(key)) {
            return state.getFirstTab(true);
        }

        Integer knownId = null;
        synchronized (valuesLock) {
            knownId = tabIdsByKey.get(key);
        }
        if (knownId == null && key.equals(normalizeTabKey(defaultTab)) && defaultTabId != null) {
            knownId = defaultTabId;
        }

        if (knownId != null) {
            ResolvedTab byId = state.resolveById(knownId, true);
            if (byId != null) {
                postResolveTab(key, byId);
                return byId;
            }
        }

        String name = requestedName;
        if ((name == null || name.isEmpty()) && key.isEmpty() && defaultTab != null && !defaultTab.isEmpty()) {
            name = defaultTab;
        }
        if ((name == null || name.isEmpty()) && !key.isEmpty()) {
            name = key;
        }

        if (name != null && !name.isEmpty()) {
            ResolvedTab byName = state.resolveByName(name, true);
            if (byName != null) {
                postResolveTab(key, byName);
                return byName;
            }

            if (createIfAbsent) {
                Map<String, Integer> ensured = state.ensureTabsExist(Collections.singleton(name));
                Integer id = ensured.get(normalizeTabKey(name));
                if (id != null) {
                    ResolvedTab created = state.resolveById(id, false);
                    if (created == null) {
                        created = new ResolvedTab(id, name);
                    }
                    postResolveTab(key, created);
                    return created;
                }
            }
            return null;
        }

        ResolvedTab first = state.getFirstTab(true);
        if (first != null) {
            postResolveTab(key, first);
        }
        return first;
    }

    private void postResolveTab(String key, ResolvedTab resolved) {
        if (resolved == null)
            return;

        if (!FIRST_TAB_SENTINEL.equals(key)) {
            synchronized (valuesLock) {
                if (resolved.title != null) {
                    tabNamesByKey.put(key, resolved.title);
                    tabNamesByKey.put(normalizeTabKey(resolved.title), resolved.title);
                }
                if (resolved.id != null) {
                    tabIdsByKey.put(key, resolved.id);
                    if (resolved.title != null) {
                        tabIdsByKey.put(normalizeTabKey(resolved.title), resolved.id);
                    }
                }
            }
        }

        if (key.isEmpty() || (!getDefaultTab().isEmpty() && key.equals(normalizeTabKey(getDefaultTab())))) {
            defaultTab = resolved.title == null ? "" : resolved.title;
            defaultTabId = resolved.id;
        }
    }

    private void migrateLocalTabKey(String fromKey, String toTitle) {
        String toKey = normalizeTabKey(toTitle);
        if (fromKey.equals(toKey))
            return;

        synchronized (valuesLock) {
            List<List<Object>> existing = valuesByTab.remove(fromKey);
            if (existing != null && !existing.isEmpty()) {
                valuesByTab.computeIfAbsent(toKey, k -> new ObjectArrayList<>()).addAll(existing);
            }
            if (queuedWriteTabs.remove(fromKey))
                queuedWriteTabs.add(toKey);
            if (queuedClearTabs.remove(fromKey))
                queuedClearTabs.add(toKey);

            String oldName = tabNamesByKey.remove(fromKey);
            if (oldName != null || !toTitle.isEmpty()) {
                tabNamesByKey.put(toKey, toTitle);
            }

            Integer oldId = tabIdsByKey.remove(fromKey);
            if (oldId != null) {
                tabIdsByKey.put(toKey, oldId);
            }
        }
    }

    private String displayTabName(String key) {
        synchronized (valuesLock) {
            String name = tabNamesByKey.get(key);
            if ((name == null || name.isEmpty()) && key.isEmpty()) {
                name = defaultTab;
            }
            if (name == null || name.isEmpty()) {
                name = key;
            }
            if (name == null || name.isEmpty()) {
                name = "sheet";
            }
            return name;
        }
    }

    private static String normalizeTabKey(String tab) {
        return tab == null ? "" : tab.toLowerCase(Locale.ROOT);
    }

    private static String formatRemoteUrl(String spreadsheetId, boolean markdown) {
        String url = "https://docs.google.com/spreadsheets/d/" + spreadsheetId + "/";
        return markdown ? "<" + url + ">" : url;
    }

    private static String quoteSheetName(String tabName) {
        if (tabName == null || tabName.isEmpty())
            return "";
        return "'" + tabName.replace("'", "''") + "'";
    }

    private static String withTab(String tabName, String range) {
        if (tabName == null || tabName.isEmpty())
            return range;
        if (range == null || range.isEmpty())
            return quoteSheetName(tabName);
        return quoteSheetName(tabName) + "!" + range;
    }

    private static int maxWidth(List<List<Object>> values) {
        int width = 0;
        for (List<Object> row : values) {
            if (row != null) {
                width = Math.max(width, row.size());
            }
        }
        return width;
    }

    private static List<List<Object>> copyRows(List<List<Object>> rows) {
        List<List<Object>> out = new ObjectArrayList<>();
        if (rows == null || rows.isEmpty())
            return out;
        for (List<Object> row : rows) {
            if (row == null) {
                out.add(new ObjectArrayList<>());
            } else {
                out.add(new ObjectArrayList<>(row));
            }
        }
        return out;
    }

    private static SharedState getOrCreateSharedState(String spreadsheetId, Sheets api) {
        synchronized (CACHE) {
            SharedState state = CACHE.get(spreadsheetId);
            if (state == null) {
                state = new SharedState(spreadsheetId, api);
                CACHE.put(spreadsheetId, state);
            } else {
                state.seedService(api);
                CACHE.put(spreadsheetId, state);
            }
            return state;
        }
    }

    private static PendingCreate getOrCreatePendingCreate(GuildDB db, SheetKey key, String title) {
        if (db == null || key == null) {
            return new PendingCreate(title, null, null, null);
        }

        String cacheKey = db.getGuild().getId() + ":" + key.name();
        synchronized (PENDING_CREATES) {
            PendingCreate pending = PENDING_CREATES.get(cacheKey);
            if (pending == null) {
                pending = new PendingCreate(title, db, key, cacheKey);
                PENDING_CREATES.put(cacheKey, pending);
            } else {
                PENDING_CREATES.put(cacheKey, pending);
            }
            return pending;
        }
    }

    private static void removePendingCreate(PendingCreate pending) {
        if (pending == null || pending.cacheKey == null)
            return;
        synchronized (PENDING_CREATES) {
            PendingCreate current = PENDING_CREATES.get(pending.cacheKey);
            if (current == pending) {
                PENDING_CREATES.remove(pending.cacheKey);
            }
        }
    }

    private static final class PendingCreate {
        private final String title;
        private final GuildDB db;
        private final SheetKey key;
        private final String cacheKey;
        private final String localId = UUID.randomUUID().toString();

        private final Object lock = new Object();
        private volatile CompletableFuture<SharedState> future;

        private PendingCreate(String title, GuildDB db, SheetKey key, String cacheKey) {
            this.title = title;
            this.db = db;
            this.key = key;
            this.cacheKey = cacheKey;
        }
    }

    private static final class ResolvedTab {
        private final Integer id;
        private final String title;

        private ResolvedTab(Integer id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    private static final class TabsSnapshot {
        private static final TabsSnapshot EMPTY = new TabsSnapshot(0L, new LinkedHashMap<>(), new LinkedHashMap<>());

        private final long loadedAt;
        private final LinkedHashMap<Integer, String> byId;
        private final LinkedHashMap<String, Integer> byNameLower;

        private TabsSnapshot(long loadedAt, LinkedHashMap<Integer, String> byId,
                LinkedHashMap<String, Integer> byNameLower) {
            this.loadedAt = loadedAt;
            this.byId = byId;
            this.byNameLower = byNameLower;
        }

        private ResolvedTab findById(Integer id) {
            if (id == null)
                return null;
            String title = byId.get(id);
            return title == null ? null : new ResolvedTab(id, title);
        }

        private ResolvedTab findByName(String name) {
            if (name == null || name.isEmpty())
                return null;
            Integer id = byNameLower.get(normalizeTabKey(name));
            return id == null ? null : new ResolvedTab(id, byId.get(id));
        }

        private ResolvedTab first() {
            if (byId.isEmpty())
                return null;
            Map.Entry<Integer, String> first = byId.entrySet().iterator().next();
            return new ResolvedTab(first.getKey(), first.getValue());
        }
    }

    private static final class SharedState {
        private final String spreadsheetId;

        private volatile Sheets service;
        private volatile long lastServiceFailureAt;
        private final Object serviceLock = new Object();

        private volatile TabsSnapshot tabsSnapshot;
        private final Object tabsLock = new Object();

        private final AtomicBoolean shareScheduled = new AtomicBoolean(false);

        private SharedState(String spreadsheetId, Sheets initialService) {
            this.spreadsheetId = spreadsheetId;
            this.service = initialService;
            this.tabsSnapshot = TabsSnapshot.EMPTY;
        }

        private void seedService(Sheets initialService) {
            if (initialService == null)
                return;
            synchronized (serviceLock) {
                if (this.service == null) {
                    this.service = initialService;
                    this.lastServiceFailureAt = 0L;
                }
            }
        }

        private Sheets getService() {
            Sheets current = service;
            if (current != null)
                return current;

            long now = System.currentTimeMillis();
            if (now - lastServiceFailureAt < SERVICE_RETRY_COOLDOWN_MS) {
                return null;
            }

            synchronized (serviceLock) {
                current = service;
                if (current != null)
                    return current;

                now = System.currentTimeMillis();
                if (now - lastServiceFailureAt < SERVICE_RETRY_COOLDOWN_MS) {
                    return null;
                }

                try {
                    service = SheetUtil.getSheetService();
                    lastServiceFailureAt = 0L;
                } catch (IOException e) {
                    lastServiceFailureAt = now;
                    return null;
                }
                return service;
            }
        }

        private void scheduleShareIfNeeded() {
            if (!shareScheduled.compareAndSet(false, true))
                return;
            CompletableFuture.runAsync(() -> {
                try {
                    DriveFile gdFile = new DriveFile(spreadsheetId);
                    gdFile.shareWithAnyone(DriveFile.DriveRole.WRITER);
                } catch (GeneralSecurityException | IOException e) {
                    e.printStackTrace();
                }
            }, SHEET_IO);
        }

        private void seedTabsFromSpreadsheet(Spreadsheet spreadsheet) {
            if (spreadsheet == null || spreadsheet.getSheets() == null)
                return;

            LinkedHashMap<Integer, String> byId = new LinkedHashMap<>();
            LinkedHashMap<String, Integer> byNameLower = new LinkedHashMap<>();

            for (Sheet sheet : spreadsheet.getSheets()) {
                if (sheet == null || sheet.getProperties() == null)
                    continue;
                Integer id = sheet.getProperties().getSheetId();
                String title = sheet.getProperties().getTitle();
                if (id == null || title == null)
                    continue;
                byId.put(id, title);
                byNameLower.put(normalizeTabKey(title), id);
            }

            synchronized (tabsLock) {
                this.tabsSnapshot = new TabsSnapshot(System.currentTimeMillis(), byId, byNameLower);
            }
        }

        private TabsSnapshot getTabsSnapshot(boolean forceRefresh) {
            Sheets api = getService();
            if (api == null)
                return TabsSnapshot.EMPTY;

            TabsSnapshot snapshot = tabsSnapshot;
            long now = System.currentTimeMillis();
            if (!forceRefresh && snapshot != null && now - snapshot.loadedAt <= TAB_CACHE_TTL_MS) {
                return snapshot;
            }

            synchronized (tabsLock) {
                snapshot = tabsSnapshot;
                now = System.currentTimeMillis();
                if (!forceRefresh && snapshot != null && now - snapshot.loadedAt <= TAB_CACHE_TTL_MS) {
                    return snapshot;
                }

                Spreadsheet spreadsheet = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                        () -> api.spreadsheets().get(spreadsheetId)
                                .setFields("sheets(properties(sheetId,title))")
                                .execute());

                LinkedHashMap<Integer, String> byId = new LinkedHashMap<>();
                LinkedHashMap<String, Integer> byNameLower = new LinkedHashMap<>();
                if (spreadsheet != null && spreadsheet.getSheets() != null) {
                    for (Sheet sheet : spreadsheet.getSheets()) {
                        if (sheet == null || sheet.getProperties() == null)
                            continue;
                        Integer id = sheet.getProperties().getSheetId();
                        String title = sheet.getProperties().getTitle();
                        if (id == null || title == null)
                            continue;
                        byId.put(id, title);
                        byNameLower.put(normalizeTabKey(title), id);
                    }
                }

                tabsSnapshot = new TabsSnapshot(System.currentTimeMillis(), byId, byNameLower);
                return tabsSnapshot;
            }
        }

        private void invalidateTabs() {
            synchronized (tabsLock) {
                tabsSnapshot = TabsSnapshot.EMPTY;
            }
        }

        private void putTabInCache(Integer id, String title) {
            if (id == null || title == null)
                return;
            synchronized (tabsLock) {
                TabsSnapshot current = tabsSnapshot == null ? TabsSnapshot.EMPTY : tabsSnapshot;
                LinkedHashMap<Integer, String> byId = new LinkedHashMap<>(current.byId);
                LinkedHashMap<String, Integer> byNameLower = new LinkedHashMap<>(current.byNameLower);
                byId.put(id, title);
                byNameLower.put(normalizeTabKey(title), id);
                tabsSnapshot = new TabsSnapshot(System.currentTimeMillis(), byId, byNameLower);
            }
        }

        private void removeTabFromCache(Integer id) {
            if (id == null)
                return;
            synchronized (tabsLock) {
                TabsSnapshot current = tabsSnapshot == null ? TabsSnapshot.EMPTY : tabsSnapshot;
                LinkedHashMap<Integer, String> byId = new LinkedHashMap<>(current.byId);
                String title = byId.remove(id);
                LinkedHashMap<String, Integer> byNameLower = new LinkedHashMap<>(current.byNameLower);
                if (title != null) {
                    byNameLower.remove(normalizeTabKey(title));
                }
                tabsSnapshot = new TabsSnapshot(System.currentTimeMillis(), byId, byNameLower);
            }
        }

        private ResolvedTab resolveById(Integer id, boolean refreshOnMiss) {
            if (id == null)
                return null;
            TabsSnapshot snapshot = getTabsSnapshot(false);
            ResolvedTab found = snapshot.findById(id);
            if (found != null || !refreshOnMiss)
                return found;
            snapshot = getTabsSnapshot(true);
            return snapshot.findById(id);
        }

        private ResolvedTab resolveByName(String name, boolean refreshOnMiss) {
            if (name == null || name.isEmpty())
                return null;
            TabsSnapshot snapshot = getTabsSnapshot(false);
            ResolvedTab found = snapshot.findByName(name);
            if (found != null || !refreshOnMiss)
                return found;
            snapshot = getTabsSnapshot(true);
            return snapshot.findByName(name);
        }

        private ResolvedTab getFirstTab(boolean refreshIfNeeded) {
            TabsSnapshot snapshot = getTabsSnapshot(false);
            ResolvedTab first = snapshot.first();
            if (first != null || !refreshIfNeeded)
                return first;
            snapshot = getTabsSnapshot(true);
            return snapshot.first();
        }

        private Map<String, Integer> ensureTabsExist(Collection<String> tabNames) {
            LinkedHashMap<String, String> requested = new LinkedHashMap<>();
            for (String tabName : tabNames) {
                if (tabName == null || tabName.isEmpty())
                    continue;
                requested.put(normalizeTabKey(tabName), tabName);
            }
            if (requested.isEmpty())
                return Collections.emptyMap();

            Map<String, Integer> result = new LinkedHashMap<>();

            TabsSnapshot snapshot = getTabsSnapshot(false);
            for (Map.Entry<String, String> entry : requested.entrySet()) {
                ResolvedTab found = snapshot.findByName(entry.getValue());
                if (found != null) {
                    result.put(entry.getKey(), found.id);
                }
            }
            if (result.size() == requested.size())
                return result;

            snapshot = getTabsSnapshot(true);
            for (Map.Entry<String, String> entry : requested.entrySet()) {
                if (result.containsKey(entry.getKey()))
                    continue;
                ResolvedTab found = snapshot.findByName(entry.getValue());
                if (found != null) {
                    result.put(entry.getKey(), found.id);
                }
            }
            if (result.size() == requested.size())
                return result;

            List<Request> requests = new ObjectArrayList<>();
            List<String> orderKeys = new ObjectArrayList<>();
            for (Map.Entry<String, String> entry : requested.entrySet()) {
                if (result.containsKey(entry.getKey()))
                    continue;
                requests.add(new Request().setAddSheet(
                        new AddSheetRequest().setProperties(new SheetProperties().setTitle(entry.getValue()))));
                orderKeys.add(entry.getKey());
            }

            if (!requests.isEmpty()) {
                try {
                    BatchUpdateSpreadsheetRequest batchReq = new BatchUpdateSpreadsheetRequest().setRequests(requests);
                    BatchUpdateSpreadsheetResponse resp = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                            () -> getService().spreadsheets().batchUpdate(spreadsheetId, batchReq).execute());

                    if (resp != null && resp.getReplies() != null) {
                        for (int i = 0; i < resp.getReplies().size() && i < orderKeys.size(); i++) {
                            AddSheetResponse addResp = resp.getReplies().get(i).getAddSheet();
                            if (addResp == null || addResp.getProperties() == null)
                                continue;
                            Integer id = addResp.getProperties().getSheetId();
                            String title = addResp.getProperties().getTitle();
                            if (id == null || title == null)
                                continue;
                            result.put(orderKeys.get(i), id);
                            putTabInCache(id, title);
                        }
                    }
                } catch (RuntimeException e) {
                    invalidateTabs();
                }
            }

            if (result.size() != requested.size()) {
                snapshot = getTabsSnapshot(true);
                for (Map.Entry<String, String> entry : requested.entrySet()) {
                    if (result.containsKey(entry.getKey()))
                        continue;
                    ResolvedTab found = snapshot.findByName(entry.getValue());
                    if (found != null) {
                        result.put(entry.getKey(), found.id);
                    }
                }
            }

            return result;
        }

        private Integer addTabDirect(String tabName) {
            if (tabName == null || tabName.isEmpty())
                return null;
            Sheets api = getService();
            if (api == null)
                return null;

            AddSheetRequest addSheetRequest = new AddSheetRequest()
                    .setProperties(new SheetProperties().setTitle(tabName));

            BatchUpdateSpreadsheetRequest batchReq = new BatchUpdateSpreadsheetRequest()
                    .setRequests(List.of(new Request().setAddSheet(addSheetRequest)));

            try {
                BatchUpdateSpreadsheetResponse resp = SheetUtil.executeRequest(SheetUtil.RequestType.SHEETS,
                        () -> api.spreadsheets().batchUpdate(spreadsheetId, batchReq).execute());

                if (resp == null || resp.getReplies() == null || resp.getReplies().isEmpty())
                    return null;

                AddSheetResponse addResp = resp.getReplies().get(0).getAddSheet();
                if (addResp == null || addResp.getProperties() == null)
                    return null;

                Integer id = addResp.getProperties().getSheetId();
                String title = addResp.getProperties().getTitle();
                putTabInCache(id, title);
                return id;
            } catch (RuntimeException e) {
                invalidateTabs();
                ResolvedTab existing = resolveByName(tabName, true);
                if (existing != null)
                    return existing.id;
                throw e;
            }
        }
    }
}
