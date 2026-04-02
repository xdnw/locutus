package link.locutus.discord.db;

import com.politicsandwar.graphql.model.Bankrec;
import com.politicsandwar.graphql.model.QueryBankrecsOrderByColumn;
import com.politicsandwar.graphql.model.QueryBankrecsOrderByOrderByClause;
import com.politicsandwar.graphql.model.SortOrder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.entities.BankRecord;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.bank.BankStore;
import link.locutus.discord.db.bank.LegacySplitPayloadTransactionRowMapper;
import link.locutus.discord.db.bank.LegacyWideTransactionRowMapper;
import link.locutus.discord.db.bank.Transaction2RowMapper;
import link.locutus.discord.db.entities.DiscordMeta;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.TaxEstimate;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.TransactionEndpointKey;
import link.locutus.discord.db.entities.TransactionNote;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.bank.TransactionEvent;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.io.BitBuffer;
import link.locutus.discord.util.math.ArrayUtil;
import net.dv8tion.jda.api.entities.User;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.jdbi.v3.core.statement.Query;

import java.io.File;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class BankDB extends DBMainV3 implements BankStore {
    private static final String TRANSACTIONS_TABLE = "TRANSACTIONS_2";
    private static final String TRANSACTIONS_LEGACY_TABLE = "TRANSACTIONS_2_LEGACY";
    private static final String TRANSACTIONS_ALLIANCE_LEGACY_TABLE = "TRANSACTIONS_ALLIANCE_2";
    private static final String TRANSACTION_FORMAT_TABLE = "TRANSACTION_PAYLOAD_FORMAT";

    private static final int MIGRATION_BATCH_SIZE = Character.MAX_VALUE;
    private static final long TURN_GROUP_WINDOW_MS = 5L * 60L * 1000L;
    private static final long TAX_DATE_FIX_START = 1656153134000L;
    private static final long TAX_DATE_FIX_END = 1657449182000L;
    private static final short TAX_BASE_NO_INTERNAL_SENTINEL = (short) 25700;

    private static final String INSERT_OR_IGNORE_TRANSACTION_SQL = """
            INSERT OR IGNORE INTO TRANSACTIONS_2
            (tx_id, tx_datetime, sender_key, receiver_key, banker_nation_id, note)
            VALUES (:txId, :txDatetime, :senderKey, :receiverKey, :bankerNationId, :note)
            """;

    private static final String UPSERT_TRANSACTION_SQL = """
            INSERT INTO TRANSACTIONS_2
            (tx_id, tx_datetime, sender_key, receiver_key, banker_nation_id, note)
            VALUES (:txId, :txDatetime, :senderKey, :receiverKey, :bankerNationId, :note)
            ON CONFLICT(tx_id) DO UPDATE SET
            tx_datetime = excluded.tx_datetime,
            sender_key = excluded.sender_key,
            receiver_key = excluded.receiver_key,
            banker_nation_id = excluded.banker_nation_id,
            note = excluded.note
            """;

    private static final String INSERT_OR_IGNORE_TAX_DEPOSIT_SQL = """
            INSERT OR IGNORE INTO TAX_DEPOSITS_DATE (
            tax_id, alliance, date, id, nation,
            moneyrate, resoucerate, resources, internal_taxrate
            ) VALUES (
            :taxId, :allianceId, :date, :id, :nationId,
            :moneyRate, :resourceRate, :resources, :internalTaxRate
            )
            """;

    private static final String UPSERT_TAX_SUMMARY_SQL = """
            INSERT INTO TAX_SUMMARY (
            alliance_id, nation_id, tax_base, date,
            no_internal_applied, no_internal_unapplied,
            internal_applied, internal_unapplied
            ) VALUES (
            :allianceId, :nationId, :taxBase, :date,
            :noInternalApplied, :noInternalUnapplied,
            :internalApplied, :internalUnapplied
            )
            ON CONFLICT(alliance_id, nation_id) DO UPDATE SET
            tax_base = excluded.tax_base,
            date = excluded.date,
            no_internal_applied = excluded.no_internal_applied,
            no_internal_unapplied = excluded.no_internal_unapplied,
            internal_applied = excluded.internal_applied,
            internal_unapplied = excluded.internal_unapplied
            """;

    private static final RowMapper<Subscription> SUBSCRIPTION_MAPPER = (rs, ctx) ->
            new Subscription(
                    rs.getLong("user"),
                    rs.getInt("allianceOrNation"),
                    BankSubType.get(rs.getInt("isNation")),
                    rs.getLong("date"),
                    rs.getInt("isReceive") == 1,
                    rs.getLong("amount")
            );

    private static final RowMapper<TaxEstimate> TAX_ESTIMATE_MAPPER = (rs, ctx) -> {
        TaxEstimate estimate = new TaxEstimate();
        estimate.tax_id = rs.getInt("tax_id");
        estimate.min_cash = rs.getInt("min_cash");
        estimate.max_cash = rs.getInt("max_cash");
        estimate.min_rss = rs.getInt("min_rss");
        estimate.max_rss = rs.getInt("max_rss");
        return estimate;
    };

    private final RowMapper<Transaction2> transactionMapper = new Transaction2RowMapper();

    private final Map<Integer, Set<Transaction2>> transactionCache2 = new HashMap<>();
    private final Map<Integer, Long> lastTaxSummaryUpdateByAlliance = new HashMap<>();
    private final Map<Integer, Short> lastTaxBaseByAlliance = new HashMap<>();
    private final Map<Integer, Long> lastTaxRecordDateByAlliance = new HashMap<>();

    private SoftReference<Map.Entry<Integer, List<Transaction2>>> txNationCache;

    public BankDB() throws SQLException, ClassNotFoundException {
        super(
                new File(Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY),
                "bank",
                true,
                false,
                Settings.INSTANCE.DATABASE.SQLITE.BANK_MMAP_SIZE_MB,
                20,
                5
        );
        createTables();
    }

    private static String txSelectSql() {
        return """
                SELECT tx_id, tx_datetime, sender_key, receiver_key, banker_nation_id, note
                FROM TRANSACTIONS_2
                """;
    }

    private static String taxDepositSelectSql() {
        return """
                SELECT tax_id, alliance, date, id, nation, moneyrate, resoucerate, resources, internal_taxrate
                FROM TAX_DEPOSITS_DATE
                """;
    }

    private static String inClause(String column, String bindName) {
        return column + " IN (<" + bindName + ">)";
    }

    private static String hasEndpointTypeSql(String column, String bindName) {
        return "(" + column + " & " + TransactionEndpointKey.TYPE_MASK + ") = :" + bindName;
    }

    private static long endpointKey(NationOrAlliance account) {
        return TransactionEndpointKey.encode(account.getIdLong(), account.getReceiverType());
    }

    private static List<Integer> sortedIntegers(Collection<Integer> values) {
        List<Integer> list = new ArrayList<>();
        if (values != null) {
            for (Integer value : values) {
                if (value != null) {
                    list.add(value);
                }
            }
        }
        list.sort(Comparator.naturalOrder());
        return list;
    }

    private static List<Long> sortedLongs(Collection<Long> values) {
        List<Long> list = new ArrayList<>();
        if (values != null) {
            for (Long value : values) {
                if (value != null) {
                    list.add(value);
                }
            }
        }
        list.sort(Comparator.naturalOrder());
        return list;
    }

    private static List<Long> bankEndpointKeys(Collection<Long> ids) {
        Set<Long> keys = new HashSet<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id == null) {
                    continue;
                }
                keys.add(TransactionEndpointKey.encode(id, TransactionEndpointKey.NATION_TYPE));
                keys.add(TransactionEndpointKey.encode(id, TransactionEndpointKey.ALLIANCE_TYPE));
            }
        }
        return sortedLongs(keys);
    }

    private static List<Long> nationKeys(Collection<Integer> nationIds) {
        List<Long> keys = new ArrayList<>();
        for (Integer nationId : sortedIntegers(nationIds)) {
            keys.add(TransactionEndpointKey.encode(nationId.longValue(), TransactionEndpointKey.NATION_TYPE));
        }
        return keys;
    }

    private static List<Long> allianceKeys(Collection<Integer> allianceIds) {
        List<Long> keys = new ArrayList<>();
        for (Integer allianceId : sortedIntegers(allianceIds)) {
            keys.add(TransactionEndpointKey.encode(allianceId.longValue(), TransactionEndpointKey.ALLIANCE_TYPE));
        }
        return keys;
    }

    private static String andJoin(List<String> clauses) {
        return String.join(" AND ", clauses);
    }

    private static int normalizeTaxBase(short pair) {
        return pair == TAX_BASE_NO_INTERNAL_SENTINEL ? -1 : pair;
    }

    private static void applyAverageRates(
            TaxDeposit deposit,
            double moneyRate,
            double rssRate,
            double internalMoneyRate,
            double internalRssRate
    ) {
        deposit.moneyRate = (int) moneyRate;
        deposit.resourceRate = (int) rssRate;
        deposit.internalMoneyRate = (int) internalMoneyRate;
        deposit.internalResourceRate = (int) internalRssRate;
    }

    private List<Transaction2> queryTransactions(
            String whereSql,
            Map<String, Object> scalarBinds,
            Map<String, ? extends Collection<?>> listBinds,
            String orderBySql,
            Integer limit
    ) {
        StringBuilder sql = new StringBuilder(txSelectSql());
        if (whereSql != null && !whereSql.isBlank()) {
            sql.append(" WHERE ").append(whereSql);
        }
        if (orderBySql != null && !orderBySql.isBlank()) {
            sql.append(" ORDER BY ").append(orderBySql);
        }
        if (limit != null) {
            sql.append(" LIMIT :limit");
        }

        return jdbi().withHandle(handle -> {
            Query query = handle.createQuery(sql.toString());

            if (scalarBinds != null) {
                scalarBinds.forEach(query::bind);
            }
            if (listBinds != null) {
                listBinds.forEach((name, values) -> {
                    if (values != null && !values.isEmpty()) {
                        query.bindList(name, values);
                    }
                });
            }
            if (limit != null) {
                query.bind("limit", limit);
            }

            return query.map(transactionMapper).list();
        });
    }

    private Transaction2 querySingleTransaction(
            String whereSql,
            Map<String, Object> scalarBinds,
            Map<String, ? extends Collection<?>> listBinds,
            String orderBySql
    ) {
        List<Transaction2> list = queryTransactions(whereSql, scalarBinds, listBinds, orderBySql, 1);
        return list.isEmpty() ? null : list.get(0);
    }

    private TaxDeposit mapTaxDeposit(ResultSet rs) throws SQLException {
        return TaxDeposit.fromStored(
                rs.getInt("tax_id"),
                rs.getInt("alliance"),
                rs.getLong("date"),
                rs.getInt("id"),
                rs.getInt("nation"),
                rs.getInt("moneyrate"),
                rs.getInt("resoucerate"),
                rs.getBytes("resources"),
                rs.getShort("internal_taxrate")
        );
    }

    private void streamTaxDeposits(
            String whereSql,
            Map<String, Object> scalarBinds,
            Map<String, ? extends Collection<?>> listBinds,
            String orderBySql,
            Integer limit,
            Consumer<TaxDeposit> consumer
    ) {
        StringBuilder sql = new StringBuilder(taxDepositSelectSql());
        if (whereSql != null && !whereSql.isBlank()) {
            sql.append(" WHERE ").append(whereSql);
        }
        if (orderBySql != null && !orderBySql.isBlank()) {
            sql.append(" ORDER BY ").append(orderBySql);
        }
        if (limit != null) {
            sql.append(" LIMIT :limit");
        }

        jdbi().useHandle(handle -> {
            Query query = handle.createQuery(sql.toString());

            if (scalarBinds != null) {
                scalarBinds.forEach(query::bind);
            }
            if (listBinds != null) {
                listBinds.forEach((name, values) -> {
                    if (values != null && !values.isEmpty()) {
                        query.bindList(name, values);
                    }
                });
            }
            if (limit != null) {
                query.bind("limit", limit);
            }

            try (Stream<TaxDeposit> stream = query.map((rs, ctx) -> mapTaxDeposit(rs)).stream()) {
                stream.forEach(consumer);
            }
        });
    }

    private List<TaxDeposit> queryTaxDeposits(
            String whereSql,
            Map<String, Object> scalarBinds,
            Map<String, ? extends Collection<?>> listBinds,
            String orderBySql,
            Integer limit
    ) {
        List<TaxDeposit> rows = new ArrayList<>();
        streamTaxDeposits(whereSql, scalarBinds, listBinds, orderBySql, limit, rows::add);
        return rows;
    }

    private TaxDeposit querySingleTaxDeposit(
            String whereSql,
            Map<String, Object> scalarBinds,
            Map<String, ? extends Collection<?>> listBinds,
            String orderBySql
    ) {
        List<TaxDeposit> list = queryTaxDeposits(whereSql, scalarBinds, listBinds, orderBySql, 1);
        return list.isEmpty() ? null : list.get(0);
    }

    private void createTransactionsTable() {
        jdbi().useHandle(handle -> {
            handle.execute("""
                    CREATE TABLE IF NOT EXISTS TRANSACTIONS_2 (
                    tx_id INTEGER PRIMARY KEY,
                    tx_datetime INTEGER NOT NULL,
                    sender_key INTEGER NOT NULL,
                    receiver_key INTEGER NOT NULL,
                    banker_nation_id INTEGER NOT NULL,
                    note BLOB
                    )
                    """);
            handle.execute("CREATE INDEX IF NOT EXISTS idx_transactions_2_datetime ON TRANSACTIONS_2(tx_datetime)");
            handle.execute("CREATE INDEX IF NOT EXISTS idx_transactions_2_sender_key ON TRANSACTIONS_2(sender_key)");
            handle.execute("CREATE INDEX IF NOT EXISTS idx_transactions_2_receiver_key ON TRANSACTIONS_2(receiver_key)");
            handle.execute("CREATE INDEX IF NOT EXISTS idx_transactions_2_banker_nation_id ON TRANSACTIONS_2(banker_nation_id)");
        });
    }

    private void createSubscriptionsTable() {
        jdbi().useHandle(handle -> {
            handle.execute("""
                    CREATE TABLE IF NOT EXISTS SUBSCRIPTIONS (
                    user INTEGER NOT NULL,
                    allianceOrNation INTEGER NOT NULL,
                    isNation INTEGER NOT NULL,
                    date INTEGER NOT NULL,
                    isReceive INTEGER NOT NULL,
                    amount INTEGER NOT NULL,
                    PRIMARY KEY (user, allianceOrNation, isNation)
                    )
                    """);
            handle.execute("""
                    CREATE INDEX IF NOT EXISTS idx_subscriptions_lookup
                    ON SUBSCRIPTIONS(allianceOrNation, isNation, isReceive, amount, date)
                    """);
        });
    }

    private void createTaxBracketsTable() {
        jdbi().useHandle(handle -> handle.execute("""
                CREATE TABLE IF NOT EXISTS TAX_BRACKETS (
                id INTEGER PRIMARY KEY,
                money INTEGER NOT NULL,
                resources INTEGER NOT NULL,
                date INTEGER NOT NULL
                )
                """));
    }

    private void createTaxEstimateTable() {
        jdbi().useHandle(handle -> handle.execute("""
                CREATE TABLE IF NOT EXISTS TAX_ESTIMATE (
                tax_id INTEGER PRIMARY KEY,
                min_cash INTEGER NOT NULL,
                max_cash INTEGER NOT NULL,
                min_rss INTEGER NOT NULL,
                max_rss INTEGER NOT NULL
                )
                """));
    }

    private void createTaxDepositsDateTable() {
        jdbi().useHandle(handle -> {
            // Assumes the existing key is the API-side deposit id, which matches how the old code queried by `id`.
            handle.execute("""
                    CREATE TABLE IF NOT EXISTS TAX_DEPOSITS_DATE (
                    id INTEGER PRIMARY KEY,
                    tax_id INTEGER NOT NULL,
                    alliance INTEGER NOT NULL,
                    date INTEGER NOT NULL,
                    nation INTEGER NOT NULL,
                    moneyrate INTEGER NOT NULL,
                    resoucerate INTEGER NOT NULL,
                    resources BLOB NOT NULL,
                    internal_taxrate INTEGER NOT NULL
                    )
                    """);
            handle.execute("""
                    CREATE INDEX IF NOT EXISTS idx_tax_deposits_alliance_date
                    ON TAX_DEPOSITS_DATE(alliance, date)
                    """);
            handle.execute("""
                    CREATE INDEX IF NOT EXISTS idx_tax_deposits_nation
                    ON TAX_DEPOSITS_DATE(nation)
                    """);
            handle.execute("""
                    CREATE INDEX IF NOT EXISTS idx_tax_deposits_tax_id
                    ON TAX_DEPOSITS_DATE(tax_id)
                    """);
            handle.execute("""
                    CREATE INDEX IF NOT EXISTS idx_tax_deposits_date
                    ON TAX_DEPOSITS_DATE(date)
                    """);
        });
    }

    private void createTaxSummaryTable() {
        jdbi().useHandle(handle -> {
            handle.execute("""
                    CREATE TABLE IF NOT EXISTS TAX_SUMMARY (
                    alliance_id INTEGER NOT NULL,
                    nation_id INTEGER NOT NULL,
                    tax_base INTEGER NOT NULL,
                    date INTEGER NOT NULL,
                    no_internal_applied BLOB NOT NULL,
                    no_internal_unapplied BLOB NOT NULL,
                    internal_applied BLOB NOT NULL,
                    internal_unapplied BLOB NOT NULL,
                    PRIMARY KEY (alliance_id, nation_id)
                    )
                    """);
            handle.execute("""
                    CREATE INDEX IF NOT EXISTS idx_tax_summary_alliance_date
                    ON TAX_SUMMARY(alliance_id, date)
                    """);
            handle.execute("""
                    CREATE INDEX IF NOT EXISTS idx_tax_summary_nation_id
                    ON TAX_SUMMARY(nation_id)
                    """);
        });
    }

    private void createLootDiffByTaxIdTable() {
        jdbi().useHandle(handle -> {
            // Minimal fallback DDL because the exact schema was not in the pasted file.
            // If you already have a richer schema in production, keep that exact schema instead.
            handle.execute("""
                    CREATE TABLE IF NOT EXISTS LOOT_DIFF_BY_TAX_ID (
                    tax_id INTEGER PRIMARY KEY,
                    diff BLOB
                    )
                    """);
        });
    }

    @Override
    public void createTables() {
        ensureParsedTransactionsTable();
        createSubscriptionsTable();
        createTaxBracketsTable();
        createTaxEstimateTable();
        createTaxDepositsDateTable();
        createTaxSummaryTable();
        createLootDiffByTaxIdTable();
    }

    private void ensureParsedTransactionsTable() {
        try {
            boolean canonicalExists = tableExists(TRANSACTIONS_TABLE);

            if (canonicalExists && isLegacyTransactionsTable(TRANSACTIONS_TABLE)) {
                String sourceTable = renameTable(TRANSACTIONS_TABLE, TRANSACTIONS_LEGACY_TABLE);
                createTransactionsTable();
                ensureTransactionPayloadFormat(true);
                migrateTransactions(sourceTable);
                migrateLegacyAllianceTransactionsIfPresent();
                return;
            }

            if (canonicalExists) {
                assertCurrentTransactionsTable(TRANSACTIONS_TABLE);
                createTransactionsTable();
                // allow initializing metadata if this DB predates TRANSACTION_PAYLOAD_FORMAT
                ensureTransactionPayloadFormat(true);
                migrateLegacyAllianceTransactionsIfPresent();
                return;
            }

            String sourceTable = tableExists(TRANSACTIONS_LEGACY_TABLE) ? TRANSACTIONS_LEGACY_TABLE : null;

            createTransactionsTable();
            ensureTransactionPayloadFormat(true);

            if (sourceTable != null) {
                migrateTransactions(sourceTable);
            }
            migrateLegacyAllianceTransactionsIfPresent();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to migrate bank transactions table", e);
        }
    }

    private void migrateLegacyAllianceTransactionsIfPresent() throws SQLException {
        if (!tableExists(TRANSACTIONS_ALLIANCE_LEGACY_TABLE)) {
            return;
        }
        migrateTransactions(TRANSACTIONS_ALLIANCE_LEGACY_TABLE);
    }

    private void migrateTransactions(String sourceTable) {
        final RowMapper<Transaction2> mapper;
        try {
            boolean splitEndpointTable = isSplitEndpointTransactionsTable(sourceTable);
            boolean currentPayloadTable = !splitEndpointTable
                    && hasEndpointKeyColumns(sourceTable)
                    && isBlobNoteTable(sourceTable);

            if (currentPayloadTable) {
                mapper = transactionMapper;
            } else if (splitEndpointTable) {
                mapper = new LegacySplitPayloadTransactionRowMapper();
            } else {
                mapper = new LegacyWideTransactionRowMapper();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to inspect bank transaction table schema: " + sourceTable, e);
        }

        String sql = """
                SELECT *
                FROM %s
                WHERE tx_id > :lastTxId
                ORDER BY tx_id ASC
                LIMIT :limit
                """.formatted(quoteIdentifier(sourceTable));

        int lastTxId = 0;
        while (true) {
            int fromId = lastTxId;
            List<Transaction2> batch = jdbi().withHandle(handle ->
                    handle.createQuery(sql)
                            .bind("lastTxId", fromId)
                            .bind("limit", MIGRATION_BATCH_SIZE)
                            .map(mapper)
                            .list()
            );

            if (batch.isEmpty()) {
                break;
            }

            addTransactions(batch, false);
            lastTxId = batch.get(batch.size() - 1).tx_id;
        }
    }

    private String renameTable(String currentName, String preferredName) throws SQLException {
        try (var connection = getConnection()) {
            return SqliteTableMoveSupport.moveTableToAvailableName(connection, currentName, preferredName);
        }
    }

    private boolean isBlobNoteTable(String tableName) throws SQLException {
        String noteType = getColumnType(tableName, "note");
        return noteType != null && noteType.toUpperCase(Locale.ROOT).contains("BLOB");
    }

    private boolean isLegacyTransactionsTable(String tableName) throws SQLException {
        return !hasEndpointKeyColumns(tableName) || isSplitEndpointTransactionsTable(tableName);
    }

    private boolean hasEndpointKeyColumns(String tableName) throws SQLException {
        return hasColumn(tableName, "sender_key") && hasColumn(tableName, "receiver_key");
    }

    private boolean isSplitEndpointTransactionsTable(String tableName) throws SQLException {
        return isBlobNoteTable(tableName)
                && hasColumn(tableName, "sender_id")
                && hasColumn(tableName, "sender_type")
                && hasColumn(tableName, "receiver_id")
                && hasColumn(tableName, "receiver_type");
    }

    private void assertCurrentTransactionsTable(String tableName) throws SQLException {
        if (!isBlobNoteTable(tableName)) {
            throw new IllegalStateException("Expected blob-backed transactions table: " + tableName);
        }
        if (!hasEndpointKeyColumns(tableName)) {
            throw new IllegalStateException("Expected endpoint-key-backed transactions table: " + tableName);
        }
        if (hasColumn(tableName, ResourceType.MONEY.name())) {
            throw new IllegalStateException(
                    "Bank transaction DB uses an obsolete split-resource blob schema. " +
                            "Recreate it from the legacy table or start from a fresh DB."
            );
        }
    }

    private void ensureTransactionPayloadFormat(boolean allowInitialize) {
        jdbi().useHandle(handle -> {
            handle.execute("""
                    CREATE TABLE IF NOT EXISTS TRANSACTION_PAYLOAD_FORMAT (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    format_magic INTEGER NOT NULL,
                    format_version INTEGER NOT NULL
                    )
                    """);

            Map<String, Object> row = handle.createQuery("""
                    SELECT format_magic, format_version
                    FROM TRANSACTION_PAYLOAD_FORMAT
                    WHERE id = 1
                    """).mapToMap().findOne().orElse(null);

            if (row == null) {
                if (!allowInitialize) {
                    throw new IllegalStateException(
                            "Bank transaction DB is missing transaction payload metadata row.");
                }

                handle.createUpdate("""
                        INSERT INTO TRANSACTION_PAYLOAD_FORMAT(id, format_magic, format_version)
                        VALUES (1, :magic, :version)
                        """)
                        .bind("magic", Transaction2.noteDbFormatMagic())
                        .bind("version", Transaction2.noteDbFormatVersion())
                        .execute();
                return;
            }

            int magic = ((Number) row.get("format_magic")).intValue();
            int version = ((Number) row.get("format_version")).intValue();

            if (magic != Transaction2.noteDbFormatMagic()
                    || version != Transaction2.noteDbFormatVersion()) {
                throw new IllegalStateException(
                        "Bank transaction DB payload format mismatch: "
                                + magic + "/" + version
                                + " != "
                                + Transaction2.noteDbFormatMagic() + "/"
                                + Transaction2.noteDbFormatVersion()
                );
            }
        });
    }

    @Override
    public synchronized void addTaxEstimate(int taxId, int minCash, int maxCash, int minRss, int maxRss) {
        jdbi().useHandle(handle ->
                handle.createUpdate("""
                        INSERT INTO TAX_ESTIMATE(tax_id, min_cash, max_cash, min_rss, max_rss)
                        VALUES (:taxId, :minCash, :maxCash, :minRss, :maxRss)
                        ON CONFLICT(tax_id) DO UPDATE SET
                        min_cash = excluded.min_cash,
                        max_cash = excluded.max_cash,
                        min_rss = excluded.min_rss,
                        max_rss = excluded.max_rss
                        """)
                        .bind("taxId", taxId)
                        .bind("minCash", minCash)
                        .bind("maxCash", maxCash)
                        .bind("minRss", minRss)
                        .bind("maxRss", maxRss)
                        .execute()
        );
    }

    @Override
    public Map<Integer, TaxEstimate> getTaxEstimates() {
        List<TaxEstimate> rows = jdbi().withHandle(handle ->
                handle.createQuery("""
                        SELECT tax_id, min_cash, max_cash, min_rss, max_rss
                        FROM TAX_ESTIMATE
                        """)
                        .map(TAX_ESTIMATE_MAPPER)
                        .list()
        );

        Map<Integer, TaxEstimate> result = new HashMap<>();
        for (TaxEstimate estimate : rows) {
            result.put(estimate.tax_id, estimate);
        }
        return result;
    }

    private void loadRecordDate(Set<Integer> allianceIds, Set<Integer> toUpdate) {
        if (allianceIds.isEmpty()) {
            return;
        }

        List<Integer> ids = sortedIntegers(allianceIds);

        List<AbstractMap.SimpleEntry<Integer, Long>> rows = jdbi().withHandle(handle ->
                handle.createQuery("""
                        SELECT alliance, MAX(date) AS max_date
                        FROM TAX_DEPOSITS_DATE
                        WHERE alliance IN (<allianceIds>)
                        GROUP BY alliance
                        """)
                        .bindList("allianceIds", ids)
                        .map((rs, ctx) -> new AbstractMap.SimpleEntry<>(
                                rs.getInt("alliance"),
                                rs.getLong("max_date")
                        ))
                        .list()
        );

        for (Map.Entry<Integer, Long> row : rows) {
            int allianceId = row.getKey();
            long date = row.getValue();
            lastTaxRecordDateByAlliance.put(allianceId, date);

            long lastTaxSummaryUpdate = lastTaxSummaryUpdateByAlliance.getOrDefault(allianceId, Long.MIN_VALUE);
            if (date > lastTaxSummaryUpdate) {
                toUpdate.add(allianceId);
            }
        }

        for (int allianceId : allianceIds) {
            lastTaxRecordDateByAlliance.putIfAbsent(allianceId, 0L);
        }
    }

    private void loadSummaryDate(Set<Integer> allianceIds, Set<Integer> toUpdate) {
        if (allianceIds.isEmpty()) {
            return;
        }

        List<Integer> ids = sortedIntegers(allianceIds);

        List<AbstractMap.SimpleEntry<Integer, Long>> rows = jdbi().withHandle(handle ->
                handle.createQuery("""
                        SELECT alliance_id, MAX(date) AS sum_date
                        FROM TAX_SUMMARY
                        WHERE alliance_id IN (<allianceIds>)
                        GROUP BY alliance_id
                        """)
                        .bindList("allianceIds", ids)
                        .map((rs, ctx) -> new AbstractMap.SimpleEntry<>(
                                rs.getInt("alliance_id"),
                                rs.getLong("sum_date")
                        ))
                        .list()
        );

        for (Map.Entry<Integer, Long> row : rows) {
            lastTaxSummaryUpdateByAlliance.put(row.getKey(), row.getValue());
        }

        for (int allianceId : allianceIds) {
            long date = lastTaxSummaryUpdateByAlliance.getOrDefault(allianceId, -1L);
            lastTaxSummaryUpdateByAlliance.putIfAbsent(allianceId, date);

            if (date < lastTaxRecordDateByAlliance.getOrDefault(allianceId, Long.MIN_VALUE)) {
                toUpdate.add(allianceId);
            }
        }
    }

    private void loadExistingTaxSummaries(
            Set<Integer> allianceIds,
            Map<Integer, Map<Integer, TaxRecordSummary>> summaryByAlliance
    ) {
        if (allianceIds.isEmpty()) {
            return;
        }

        List<Integer> ids = sortedIntegers(allianceIds);

        List<StoredTaxSummaryRow> rows = jdbi().withHandle(handle ->
                handle.createQuery("""
                        SELECT alliance_id, nation_id,
                        no_internal_applied, no_internal_unapplied,
                        internal_applied, internal_unapplied
                        FROM TAX_SUMMARY
                        WHERE alliance_id IN (<allianceIds>)
                        """)
                        .bindList("allianceIds", ids)
                        .map((rs, ctx) -> new StoredTaxSummaryRow(
                                rs.getInt("alliance_id"),
                                rs.getInt("nation_id"),
                                rs.getBytes("no_internal_applied"),
                                rs.getBytes("no_internal_unapplied"),
                                rs.getBytes("internal_applied"),
                                rs.getBytes("internal_unapplied")
                        ))
                        .list()
        );

        for (StoredTaxSummaryRow row : rows) {
            summaryByAlliance
                    .computeIfAbsent(row.allianceId, ignored -> new HashMap<>())
                    .put(
                            row.nationId,
                            new TaxRecordSummary(
                                    ArrayUtil.toDoubleArray(row.noInternalApplied),
                                    ArrayUtil.toDoubleArray(row.noInternalUnapplied),
                                    ArrayUtil.toDoubleArray(row.internalApplied),
                                    ArrayUtil.toDoubleArray(row.internalUnapplied),
                                    false
                            )
                    );
        }
    }

    @Override
    public Map<Integer, double[]> getAppliedTaxDeposits(
            Set<Integer> nationIds,
            Set<Integer> allianceIds,
            int[] taxBase,
            boolean useTaxBase
    ) {
        Map<Integer, double[]> result = new HashMap<>();
        if (nationIds == null || nationIds.isEmpty() || allianceIds == null || allianceIds.isEmpty()) {
            return result;
        }

        checkUpdateTaxSummary(allianceIds, taxBase);

        List<Integer> nationList = sortedIntegers(nationIds);
        List<Integer> allianceList = sortedIntegers(allianceIds);

        String sql = """
                SELECT nation_id, %s AS base_amount, internal_applied AS other_amount
                FROM TAX_SUMMARY
                WHERE nation_id IN (<nationIds>)
                AND alliance_id IN (<allianceIds>)
                """.formatted(useTaxBase ? "no_internal_applied" : "no_internal_unapplied");

        List<SummaryAmountsRow> rows = jdbi().withHandle(handle ->
                handle.createQuery(sql)
                        .bindList("nationIds", nationList)
                        .bindList("allianceIds", allianceList)
                        .map((rs, ctx) -> new SummaryAmountsRow(
                                rs.getInt("nation_id"),
                                rs.getBytes("base_amount"),
                                rs.getBytes("other_amount")
                        ))
                        .list()
        );

        for (SummaryAmountsRow row : rows) {
            double[] added = result.get(row.nationId);
            if (added == null) {
                added = ResourceType.getBuffer();
                result.put(row.nationId, added);
            }

            ResourceType.add(added, ArrayUtil.toDoubleArray(row.baseAmount));
            ResourceType.add(added, ArrayUtil.toDoubleArray(row.otherAmount));
        }

        return result;
    }

    @Override
    public Map<Integer, double[]> getUnappliedTaxDeposits(
            Set<Integer> nationIds,
            Set<Integer> allianceIds,
            int[] taxBase
    ) {
        Map<Integer, double[]> result = new HashMap<>();
        if (nationIds == null || nationIds.isEmpty() || allianceIds == null || allianceIds.isEmpty()) {
            return result;
        }

        checkUpdateTaxSummary(allianceIds, taxBase);

        List<Integer> nationList = sortedIntegers(nationIds);
        List<Integer> allianceList = sortedIntegers(allianceIds);

        List<SummaryAmountsRow> rows = jdbi().withHandle(handle ->
                handle.createQuery("""
                        SELECT nation_id,
                        no_internal_unapplied AS base_amount,
                        internal_unapplied AS other_amount
                        FROM TAX_SUMMARY
                        WHERE nation_id IN (<nationIds>)
                        AND alliance_id IN (<allianceIds>)
                        """)
                        .bindList("nationIds", nationList)
                        .bindList("allianceIds", allianceList)
                        .map((rs, ctx) -> new SummaryAmountsRow(
                                rs.getInt("nation_id"),
                                rs.getBytes("base_amount"),
                                rs.getBytes("other_amount")
                        ))
                        .list()
        );

        for (SummaryAmountsRow row : rows) {
            double[] added = result.get(row.nationId);
            if (added == null) {
                added = ResourceType.getBuffer();
                result.put(row.nationId, added);
            }

            ResourceType.add(added, ArrayUtil.toDoubleArray(row.baseAmount));
            ResourceType.add(added, ArrayUtil.toDoubleArray(row.otherAmount));
        }

        return result;
    }

    private void checkUpdateTaxSummary(Set<Integer> allianceIds, int[] taxBase) {
        short guildTaxPair = MathMan.pairByte(taxBase[0], taxBase[1]);

        synchronized (lastTaxSummaryUpdateByAlliance) {
            Set<Integer> loadTaxBase = new HashSet<>();
            for (int allianceId : allianceIds) {
                if (!lastTaxBaseByAlliance.containsKey(allianceId)) {
                    loadTaxBase.add(allianceId);
                }
            }

            if (!loadTaxBase.isEmpty()) {
                List<Integer> ids = sortedIntegers(loadTaxBase);
                List<AbstractMap.SimpleEntry<Integer, Short>> rows = jdbi().withHandle(handle ->
                        handle.createQuery("""
                                SELECT DISTINCT alliance_id, tax_base
                                FROM TAX_SUMMARY
                                WHERE alliance_id IN (<allianceIds>)
                                """)
                                .bindList("allianceIds", ids)
                                .map((rs, ctx) -> new AbstractMap.SimpleEntry<>(
                                        rs.getInt("alliance_id"),
                                        (short) rs.getInt("tax_base")
                                ))
                                .list()
                );

                for (Map.Entry<Integer, Short> row : rows) {
                    lastTaxBaseByAlliance.put(row.getKey(), row.getValue());
                }

                for (int allianceId : loadTaxBase) {
                    lastTaxBaseByAlliance.putIfAbsent(allianceId, guildTaxPair);
                }
            }

            Set<Integer> purgeSummary = new HashSet<>();
            Set<Integer> toLoadRecordDate = new HashSet<>();
            Set<Integer> toLoadSummary = new HashSet<>();
            Set<Integer> toUpdate = new HashSet<>();

            for (int allianceId : allianceIds) {
                short lastTaxBase = lastTaxBaseByAlliance.getOrDefault(allianceId, (short) -1);
                if (normalizeTaxBase(lastTaxBase) != normalizeTaxBase(guildTaxPair)) {
                    lastTaxSummaryUpdateByAlliance.remove(allianceId);
                    lastTaxBaseByAlliance.put(allianceId, guildTaxPair);
                    purgeSummary.add(allianceId);
                }

                long lastTaxSummaryUpdate = lastTaxSummaryUpdateByAlliance.getOrDefault(allianceId, Long.MIN_VALUE);
                long lastTaxRecordDate = lastTaxRecordDateByAlliance.getOrDefault(allianceId, Long.MIN_VALUE);

                if (lastTaxSummaryUpdate == Long.MIN_VALUE) {
                    toLoadSummary.add(allianceId);
                } else if (lastTaxSummaryUpdate < lastTaxRecordDate) {
                    toUpdate.add(allianceId);
                }

                if (lastTaxRecordDate == Long.MIN_VALUE) {
                    toLoadRecordDate.add(allianceId);
                }
            }

            if (!purgeSummary.isEmpty()) {
                jdbi().useHandle(handle ->
                        handle.createUpdate("""
                                DELETE FROM TAX_SUMMARY
                                WHERE alliance_id IN (<allianceIds>)
                                """)
                                .bindList("allianceIds", sortedIntegers(purgeSummary))
                                .execute()
                );
            }

            if (!toLoadRecordDate.isEmpty()) {
                loadRecordDate(toLoadRecordDate, toUpdate);
            }
            if (!toLoadSummary.isEmpty()) {
                loadSummaryDate(toLoadSummary, toUpdate);
            }
            if (toUpdate.isEmpty()) {
                return;
            }

            Map<Integer, Map<Integer, TaxRecordSummary>> summaryByAlliance = new HashMap<>();
            loadExistingTaxSummaries(toUpdate, summaryByAlliance);

            Map<Integer, Long> newTaxSummaryUpdateByAlliance = new HashMap<>();
            boolean[] hasUpdated = {false};

            Consumer<TaxDeposit> apply = record -> {
                int allianceId = record.allianceId;
                long date = record.date;

                if (date > TAX_DATE_FIX_START && date < TAX_DATE_FIX_END) {
                    date = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(date));
                }

                long lastSummaryDate = lastTaxSummaryUpdateByAlliance.getOrDefault(allianceId, Long.MIN_VALUE);
                if (date <= lastSummaryDate) {
                    return;
                }

                Map<Integer, TaxRecordSummary> nationMap =
                        summaryByAlliance.computeIfAbsent(allianceId, ignored -> new HashMap<>());

                TaxRecordSummary nationSummary = nationMap.computeIfAbsent(
                        record.nationId,
                        ignored -> new TaxRecordSummary(
                                ResourceType.getBuffer(),
                                ResourceType.getBuffer(),
                                ResourceType.getBuffer(),
                                ResourceType.getBuffer(),
                                true
                        )
                );

                newTaxSummaryUpdateByAlliance.merge(allianceId, date, Math::max);
                nationSummary.dirty = true;
                hasUpdated[0] = true;

                double[] deposit = record.resources;
                if (ResourceType.isZero(deposit)) {
                    return;
                }

                int internalMoneyRate = record.internalMoneyRate;
                int internalResourceRate = record.internalResourceRate;

                if (internalMoneyRate < 0 || internalMoneyRate > 100) {
                    int moneyRate = taxBase[0];
                    double pct = record.moneyRate > moneyRate
                            ? Math.max(0, (record.moneyRate - moneyRate) / (double) record.moneyRate)
                            : 0;
                    nationSummary.no_internal_applied[ResourceType.MONEY.ordinal()] +=
                            deposit[ResourceType.MONEY.ordinal()] * pct;
                    nationSummary.no_internal_unapplied[ResourceType.MONEY.ordinal()] +=
                            deposit[ResourceType.MONEY.ordinal()];
                } else {
                    double pct = record.moneyRate > internalMoneyRate
                            ? Math.max(0, (record.moneyRate - internalMoneyRate) / (double) record.moneyRate)
                            : 0;
                    nationSummary.internal_applied[ResourceType.MONEY.ordinal()] +=
                            deposit[ResourceType.MONEY.ordinal()] * pct;
                    nationSummary.internal_unapplied[ResourceType.MONEY.ordinal()] +=
                            deposit[ResourceType.MONEY.ordinal()];
                }

                if (internalResourceRate < 0 || internalResourceRate > 100) {
                    double rssRate = taxBase[1];
                    double pct = record.resourceRate > rssRate
                            ? Math.max(0, (record.resourceRate - rssRate) / (double) record.resourceRate)
                            : 0;

                    for (ResourceType type : ResourceType.values()) {
                        if (type == ResourceType.MONEY) {
                            continue;
                        }
                        nationSummary.no_internal_applied[type.ordinal()] += deposit[type.ordinal()] * pct;
                        nationSummary.no_internal_unapplied[type.ordinal()] += deposit[type.ordinal()];
                    }
                } else {
                    double pct = record.resourceRate > internalResourceRate
                            ? Math.max(0, (record.resourceRate - internalResourceRate) / (double) record.resourceRate)
                            : 0;

                    for (ResourceType type : ResourceType.values()) {
                        if (type == ResourceType.MONEY) {
                            continue;
                        }
                        nationSummary.internal_applied[type.ordinal()] += deposit[type.ordinal()] * pct;
                        nationSummary.internal_unapplied[type.ordinal()] += deposit[type.ordinal()];
                    }
                }
            };

            Set<Integer> hasDate = new HashSet<>();
            Set<Integer> hasNoDate = new HashSet<>();
            long earliestDate = Long.MAX_VALUE;

            for (int allianceId : toUpdate) {
                long date = lastTaxSummaryUpdateByAlliance.getOrDefault(allianceId, -1L);
                if (date > 0) {
                    hasDate.add(allianceId);
                    earliestDate = Math.min(earliestDate, date);
                } else {
                    hasNoDate.add(allianceId);
                }
            }

            if (!hasDate.isEmpty()) {
                streamTaxDeposits(
                        "alliance IN (<allianceIds>) AND date > :earliestDate",
                        Map.of("earliestDate", earliestDate),
                        Map.of("allianceIds", sortedIntegers(hasDate)),
                        null,
                        null,
                        apply
                );
            }

            if (!hasNoDate.isEmpty()) {
                streamTaxDeposits(
                        "alliance IN (<allianceIds>)",
                        null,
                        Map.of("allianceIds", sortedIntegers(hasNoDate)),
                        null,
                        null,
                        apply
                );
            }

            lastTaxSummaryUpdateByAlliance.putAll(newTaxSummaryUpdateByAlliance);

            if (hasUpdated[0]) {
                jdbi().useTransaction(handle -> {
                    PreparedBatch batch = handle.prepareBatch(UPSERT_TAX_SUMMARY_SQL);

                    for (Map.Entry<Integer, Map<Integer, TaxRecordSummary>> allianceEntry : summaryByAlliance.entrySet()) {
                        int allianceId = allianceEntry.getKey();
                        long date = newTaxSummaryUpdateByAlliance.getOrDefault(
                                allianceId,
                                lastTaxSummaryUpdateByAlliance.getOrDefault(allianceId, 0L)
                        );

                        lastTaxBaseByAlliance.put(allianceId, guildTaxPair);

                        for (Map.Entry<Integer, TaxRecordSummary> nationEntry : allianceEntry.getValue().entrySet()) {
                            int nationId = nationEntry.getKey();
                            TaxRecordSummary summary = nationEntry.getValue();

                            if (!summary.dirty) {
                                continue;
                            }

                            batch.bind("allianceId", allianceId)
                                    .bind("nationId", nationId)
                                    .bind("taxBase", (int) guildTaxPair)
                                    .bind("date", date)
                                    .bindBySqlType("noInternalApplied", ArrayUtil.toByteArray(summary.no_internal_applied), Types.BLOB)
                                    .bindBySqlType("noInternalUnapplied", ArrayUtil.toByteArray(summary.no_internal_unapplied), Types.BLOB)
                                    .bindBySqlType("internalApplied", ArrayUtil.toByteArray(summary.internal_applied), Types.BLOB)
                                    .bindBySqlType("internalUnapplied", ArrayUtil.toByteArray(summary.internal_unapplied), Types.BLOB)
                                    .add();
                        }
                    }

                    batch.execute();
                });
            }
        }
    }

    @Override
    public Transaction2 getLatestTransaction() {
        return querySingleTransaction(null, null, null, "tx_id DESC");
    }

    @Override
    public void updateBankRecs(int nationId, boolean priority, Consumer<Event> eventConsumer) {
        PoliticsAndWarV3 v3 = Locutus.imp().getApiPool();
        List<Transaction2> latestTx = getTransactionsByNation(nationId, 1);
        int minId = latestTx.size() == 1 ? latestTx.get(0).tx_id : 0;

        List<Bankrec> bankRecs = v3.fetchBankRecsWithInfo(priority, request -> {
            if (minId > 0) {
                request.setMin_id(minId + 1);
            }
            request.setOr_id(List.of(nationId));
        });

        saveBankRecs(bankRecs, eventConsumer);
    }

    @Override
    public void updateBankRecsAuto(Set<Integer> nations, boolean priority, Consumer<Event> eventConsumer) {
        if (Settings.INSTANCE.TASKS.BANK_RECORDS_INTERVAL_SECONDS > 0) {
            updateBankRecs(priority, eventConsumer);
        } else {
            for (int nationId : nations) {
                updateBankRecs(nationId, priority, eventConsumer);
            }
        }
    }

    @Override
    public void updateBankRecs(boolean priority, Consumer<Event> eventConsumer) {
        ByteBuffer info = Locutus.imp().getDiscordDB().getInfo(DiscordMeta.BANK_RECS_SEQUENTIAL, 0);
        int latestId = info == null ? -1 : info.getInt();

        PoliticsAndWarV3 v3 = Locutus.imp().getApiPool();
        List<Bankrec> records = new ArrayList<>();

        Runnable saveTransactions = () -> {
            if (records.isEmpty()) {
                return;
            }

            List<Bankrec> copy = new ArrayList<>(records);
            int maxId = copy.stream().mapToInt(Bankrec::getId).max().getAsInt();

            saveBankRecs(copy, eventConsumer);

            byte[] maxIdData = ByteBuffer.allocate(4).putInt(maxId).array();
            Locutus.imp().getDiscordDB().setInfo(DiscordMeta.BANK_RECS_SEQUENTIAL, 0, maxIdData);

            records.clear();
        };

        v3.fetchBankRecs(
                priority,
                f -> {
                    f.setOr_type(List.of(1));
                    if (latestId > 0) {
                        f.setMin_id(latestId + 1);
                    }
                    f.setOrderBy(List.of(
                            new QueryBankrecsOrderByOrderByClause(
                                    QueryBankrecsOrderByColumn.ID,
                                    SortOrder.ASC,
                                    null
                            )
                    ));
                },
                v3.createBankRecProjection(),
                bankrec -> {
                    records.add(bankrec);
                    if (records.size() > 1000) {
                        saveTransactions.run();
                    }
                    return false;
                }
        );

        saveTransactions.run();
    }

    @Override
    public void saveBankRecsV2(List<BankRecord> bankrecs, Consumer<Event> eventConsumer) {
        if (bankrecs == null || bankrecs.isEmpty()) {
            return;
        }

        invalidateTXCache();

        List<Transaction2> transfers = new ArrayList<>();
        for (BankRecord bankrec : bankrecs) {
            transfers.add(Transaction2.fromBankRecord(bankrec));
        }

        transfers.sort(Comparator.comparingLong(Transaction2::getDate));
        int[] modified = addTransactions(transfers, true);

        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
        if (eventConsumer != null) {
            for (int i = 0; i < modified.length; i++) {
                if (modified[i] > 0) {
                    Transaction2 tx = transfers.get(i);
                    if (tx.tx_datetime > cutoff) {
                        eventConsumer.accept(new TransactionEvent(tx));
                    }
                }
            }
        }
    }

    @Override
    public void saveBankRecs(List<Bankrec> bankrecs, Consumer<Event> eventConsumer) {
        if (bankrecs == null || bankrecs.isEmpty()) {
            return;
        }

        invalidateTXCache();

        List<Transaction2> transfers = new ObjectArrayList<>();
        for (Bankrec bankrec : bankrecs) {
            Transaction2 tx2 = Transaction2.fromApiV3(bankrec);
            if (tx2.tx_datetime < 1451607023000L) continue;
            transfers.add(tx2);
        }

        transfers.sort(Comparator.comparingLong(Transaction2::getDate));
        int[] modified = addTransactions(transfers, true);

        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
        if (eventConsumer != null) {
            for (int i = 0; i < modified.length; i++) {
                if (modified[i] > 0) {
                    Transaction2 tx = transfers.get(i);
                    if (tx.tx_datetime > cutoff) {
                        eventConsumer.accept(new TransactionEvent(tx));
                    }
                }
            }
        }
    }

    @Override
    public void addTaxDeposit(TaxDeposit record) {
        addTaxDeposits(List.of(record));
    }

    @Override
    public List<Transaction2> getAllTransactions(
            NationOrAlliance sender,
            NationOrAlliance receiver,
            NationOrAlliance banker,
            Long startDate,
            Long endDate
    ) {
        return getAllTransactions(
                sender == null ? null : Set.of(sender),
                receiver == null ? null : Set.of(receiver),
                banker == null ? null : Set.of(banker),
                startDate,
                endDate
        );
    }

    @Override
    public List<Transaction2> getAllTransactions(
            Set<NationOrAlliance> sender,
            Set<NationOrAlliance> receiver,
            Set<NationOrAlliance> banker,
            Long startDate,
            Long endDate
    ) {
        if (sender != null && sender.isEmpty()) {
            sender = null;
        }
        if (receiver != null && receiver.isEmpty()) {
            receiver = null;
        }
        if (banker != null && banker.isEmpty()) {
            banker = null;
        }

        if (sender == null && receiver == null && banker == null) {
            throw new IllegalArgumentException("Please provide at least one of sender, receiver, or banker");
        }

        List<String> clauses = new ArrayList<>();
        Map<String, Object> scalarBinds = new HashMap<>();
        Map<String, Collection<?>> listBinds = new HashMap<>();

        if (sender != null) {
            List<Long> senderKeys = new ArrayList<>(sender.size());
            for (NationOrAlliance account : sender) {
                senderKeys.add(endpointKey(account));
            }
            clauses.add(inClause("sender_key", "senderKeys"));
            listBinds.put("senderKeys", sortedLongs(senderKeys));
        }

        if (receiver != null) {
            List<Long> receiverKeys = new ArrayList<>(receiver.size());
            for (NationOrAlliance account : receiver) {
                receiverKeys.add(endpointKey(account));
            }
            clauses.add(inClause("receiver_key", "receiverKeys"));
            listBinds.put("receiverKeys", sortedLongs(receiverKeys));
        }

        if (banker != null) {
            List<Long> bankerIds = new ArrayList<>(banker.size());
            for (NationOrAlliance account : banker) {
                bankerIds.add(account.getIdLong());
            }
            clauses.add(inClause("banker_nation_id", "bankerIds"));
            listBinds.put("bankerIds", sortedLongs(bankerIds));
        }

        if (startDate != null) {
            clauses.add("tx_datetime >= :startDate");
            scalarBinds.put("startDate", startDate);
        }

        if (endDate != null) {
            clauses.add("tx_datetime <= :endDate");
            scalarBinds.put("endDate", endDate);
        }

        return queryTransactions(andJoin(clauses), scalarBinds, listBinds, null, null);
    }

    @Override
    public List<Transaction2> getTransactionsbyId(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        return queryTransactions(
                inClause("tx_id", "ids"),
                null,
                Map.of("ids", sortedIntegers(ids)),
                "tx_id DESC",
                null
        );
    }

    @Override
    public List<Transaction2> getTransactionsByBySenderOrReceiver(
            Set<Long> senders,
            Set<Long> receivers,
            long minDateMs,
            long maxDateMs
    ) {
        List<String> clauses = new ArrayList<>();
        Map<String, Object> scalarBinds = new HashMap<>();
        Map<String, Collection<?>> listBinds = new HashMap<>();

        if (minDateMs > 0) {
            clauses.add("tx_datetime >= :minDateMs");
            scalarBinds.put("minDateMs", minDateMs);
        }

        if (maxDateMs != Long.MAX_VALUE) {
            clauses.add("tx_datetime <= :maxDateMs");
            scalarBinds.put("maxDateMs", maxDateMs);
        }

        if (senders != null && !senders.isEmpty()) {
            clauses.add(inClause("sender_key", "senderKeys"));
            listBinds.put("senderKeys", bankEndpointKeys(senders));
        }

        if (receivers != null && !receivers.isEmpty()) {
            clauses.add(inClause("receiver_key", "receiverKeys"));
            listBinds.put("receiverKeys", bankEndpointKeys(receivers));
        }

        return queryTransactions(
                clauses.isEmpty() ? null : andJoin(clauses),
                scalarBinds,
                listBinds,
                "tx_id DESC",
                null
        );
    }

    @Override
    public List<Transaction2> getTransactionsByBySender(Set<Long> senders, long minDateMs) {
        if (senders == null || senders.isEmpty()) {
            return Collections.emptyList();
        }

        return queryTransactions(
                "tx_datetime >= :minDateMs AND " + inClause("sender_key", "senderKeys"),
                Map.of("minDateMs", minDateMs),
                Map.of("senderKeys", bankEndpointKeys(senders)),
                "tx_id DESC",
                null
        );
    }

    @Override
    public List<Transaction2> getTransactionsByByReceiver(Set<Long> receivers, long minDateMs, long endDate) {
        if (receivers == null || receivers.isEmpty()) {
            return Collections.emptyList();
        }

        return queryTransactions(
                "tx_datetime >= :minDateMs AND tx_datetime <= :endDate AND " + inClause("receiver_key", "receiverKeys"),
                Map.of("minDateMs", minDateMs, "endDate", endDate),
                Map.of("receiverKeys", bankEndpointKeys(receivers)),
                "tx_id DESC",
                null
        );
    }

    @Override
    public List<Transaction2> getTransactions(long minDateMs, boolean desc) {
        return queryTransactions(
                "tx_datetime >= :minDateMs",
                Map.of("minDateMs", minDateMs),
                null,
                desc ? "tx_id DESC" : "tx_id ASC",
                null
        );
    }

    @Override
    public List<Transaction2> getToNationTransactions(long minDateMs) {
        return queryTransactions(
                "tx_datetime >= :minDateMs AND "
                        + hasEndpointTypeSql("sender_key", "senderType")
                        + " AND "
                        + hasEndpointTypeSql("receiver_key", "receiverType"),
                Map.of(
                        "minDateMs", minDateMs,
                        "senderType", TransactionEndpointKey.ALLIANCE_TYPE,
                        "receiverType", TransactionEndpointKey.NATION_TYPE
                ),
                null,
                "tx_id DESC",
                null
        );
    }

    @Override
    public List<Transaction2> getNationTransfers(int nationId, long minDateMs) {
        long nationKey = TransactionEndpointKey.encode((long) nationId, TransactionEndpointKey.NATION_TYPE);

        return queryTransactions(
                "tx_datetime >= :minDateMs AND (receiver_key = :nationKey OR sender_key = :nationKey)",
                Map.of("minDateMs", minDateMs, "nationKey", nationKey),
                null,
                "tx_id DESC",
                null
        );
    }

    @Override
    public Map<Integer, List<Transaction2>> getNationTransfersByNation(long start, long end, Set<Integer> nationIds) {
        Map<Integer, List<Transaction2>> result = new HashMap<>();
        iterateNationTransfersByNation(start, end, nationIds, (nationId, transfer) ->
                result.computeIfAbsent(nationId, ignored -> new ArrayList<>()).add(transfer), true);
        return result;
    }

    @Override
    public void iterateNationTransfersByNation(
            long start,
            long end,
            Set<Integer> nationIds,
            BiConsumer<Integer, Transaction2> consumer,
            boolean ordered
    ) {
        if (nationIds == null || nationIds.isEmpty()) {
            return;
        }

        List<Long> nationKeys = nationKeys(nationIds);

        List<String> clauses = new ArrayList<>();
        Map<String, Object> scalarBinds = new HashMap<>();
        Map<String, Collection<?>> listBinds = new HashMap<>();

        if (start > 0) {
            clauses.add("tx_datetime >= :start");
            scalarBinds.put("start", start);
        }

        if (end != Long.MAX_VALUE) {
            clauses.add("tx_datetime <= :end");
            scalarBinds.put("end", end);
        }

        clauses.add("("
                + inClause("receiver_key", "receiverNationKeys")
                + " OR "
                + inClause("sender_key", "senderNationKeys")
                + ")");

        listBinds.put("receiverNationKeys", nationKeys);
        listBinds.put("senderNationKeys", nationKeys);

        List<Transaction2> transfers = queryTransactions(
                andJoin(clauses),
                scalarBinds,
                listBinds,
                ordered ? "tx_id DESC" : null,
                null
        );

        for (Transaction2 transfer : transfers) {
            int nationId;
            if (transfer.sender_type == TransactionEndpointKey.NATION_TYPE) {
                if (transfer.receiver_type == TransactionEndpointKey.NATION_TYPE) {
                    continue;
                }
                nationId = (int) transfer.sender_id;
            } else if (transfer.receiver_type == TransactionEndpointKey.NATION_TYPE) {
                nationId = (int) transfer.receiver_id;
            } else {
                continue;
            }
            consumer.accept(nationId, transfer);
        }
    }

    @Override
    public List<Transaction2> getAllianceTransfers(int allianceId, long minDateMs) {
        long allianceKey = TransactionEndpointKey.encode((long) allianceId, TransactionEndpointKey.ALLIANCE_TYPE);

        return queryTransactions(
                "tx_datetime >= :minDateMs AND (receiver_key = :allianceKey OR sender_key = :allianceKey)",
                Map.of("minDateMs", minDateMs, "allianceKey", allianceKey),
                null,
                "tx_id DESC",
                null
        );
    }

    @Override
    public int getTransactionsByNationCount(int nation) {
        long nationKey = TransactionEndpointKey.encode((long) nation, TransactionEndpointKey.NATION_TYPE);
        Long count = jdbi().withHandle(handle ->
                handle.createQuery("""
                        SELECT COUNT(*)
                        FROM TRANSACTIONS_2
                        WHERE sender_key = :nationKey OR receiver_key = :nationKey
                        """)
                        .bind("nationKey", nationKey)
                        .mapTo(Long.class)
                        .one()
        );
        return count.intValue();
    }

    @Override
    public List<Transaction2> getTransactionsByBanker(int nation) {
        return queryTransactions(
                "banker_nation_id = :nation",
                Map.of("nation", nation),
                null,
                null,
                null
        );
    }

    private void invalidateTXCache() {
        txNationCache = null;
    }

    @Override
    public List<Transaction2> getTransactionsByNation(int nation) {
        return getTransactionsByNation(nation, -1);
    }

    @Override
    public List<Transaction2> getTransactionsByNation(int nation, int limit) {
        Reference<Map.Entry<Integer, List<Transaction2>>> tmp = txNationCache;
        Map.Entry<Integer, List<Transaction2>> cached = tmp == null ? null : tmp.get();

        if (cached != null && cached.getKey() == nation) {
            List<Transaction2> value = cached.getValue();
            if (limit > 0) {
                List<Transaction2> copy = new ArrayList<>(value);
                copy.sort((o1, o2) -> Long.compare(o2.tx_id, o1.tx_id));
                return new ArrayList<>(copy.subList(0, Math.min(limit, copy.size())));
            }
            return value;
        }

        long nationKey = TransactionEndpointKey.encode((long) nation, TransactionEndpointKey.NATION_TYPE);

        List<Transaction2> list = queryTransactions(
                "sender_key = :nationKey OR receiver_key = :nationKey",
                Map.of("nationKey", nationKey),
                null,
                "tx_id DESC",
                limit > 0 ? limit : null
        );

        if (limit == -1) {
            txNationCache = new SoftReference<>(
                    new AbstractMap.SimpleEntry<>(nation, new ArrayList<>(list))
            );
        }

        return list;
    }

    @Override
    public Transaction2 getLatestDeposit(int id, int type) {
        long senderKey = TransactionEndpointKey.encode((long) id, type);
        return querySingleTransaction(
                "sender_key = :senderKey AND " + hasEndpointTypeSql("receiver_key", "receiverType"),
                Map.of(
                        "senderKey", senderKey,
                        "receiverType", TransactionEndpointKey.ALLIANCE_TYPE
                ),
                null,
                "tx_id DESC"
        );
    }

    @Override
    public Transaction2 getLatestWithdrawal(int id, int type) {
        long receiverKey = TransactionEndpointKey.encode((long) id, type);
        return querySingleTransaction(
                "receiver_key = :receiverKey AND " + hasEndpointTypeSql("sender_key", "senderType"),
                Map.of(
                        "receiverKey", receiverKey,
                        "senderType", TransactionEndpointKey.ALLIANCE_TYPE
                ),
                null,
                "tx_id DESC"
        );
    }

    @Override
    public Transaction2 getLatestSelfWithdrawal(int nationId) {
        long nationKey = TransactionEndpointKey.encode((long) nationId, TransactionEndpointKey.NATION_TYPE);

        List<Transaction2> transactions = queryTransactions(
                "receiver_key = :receiverKey AND " + hasEndpointTypeSql("sender_key", "senderType"),
                Map.of(
                        "receiverKey", nationKey,
                        "senderType", TransactionEndpointKey.ALLIANCE_TYPE
                ),
                null,
                "tx_id DESC",
                null
        );

        for (Transaction2 transaction : transactions) {
            if (transaction.isSelfWithdrawal(nationId)) {
                return transaction;
            }
        }
        return null;
    }

    @Override
    public List<Transaction2> getTransactionsByNation(int nation, long start, long end) {
        if (start < 0) {
            start = 0;
        }

        long nationKey = TransactionEndpointKey.encode((long) nation, TransactionEndpointKey.NATION_TYPE);

        List<String> clauses = new ArrayList<>();
        Map<String, Object> scalarBinds = new HashMap<>();

        clauses.add("(sender_key = :nationKey OR receiver_key = :nationKey)");
        scalarBinds.put("nationKey", nationKey);

        if (start > 0) {
            clauses.add("tx_datetime >= :start");
            scalarBinds.put("start", start);
        }

        if (end != Long.MAX_VALUE) {
            clauses.add("tx_datetime <= :end");
            scalarBinds.put("end", end);
        }

        return queryTransactions(andJoin(clauses), scalarBinds, null, "tx_id DESC", null);
    }

    @Override
    public List<Transaction2> getTransactionsWithStructuredNote(TransactionNote note, long cutoff) {
        return filterTransactionsByStructuredNote(
                "tx_datetime > :cutoff",
                Map.of("cutoff", cutoff),
                note
        );
    }

    private List<Transaction2> filterTransactionsByStructuredNote(
            String whereSql,
            Map<String, Object> scalarBinds,
            TransactionNote note
    ) {
        List<Transaction2> matches = queryTransactions(whereSql, scalarBinds, null, "tx_id DESC", null);
        if (note == null || note.isEmpty()) {
            return matches;
        }
        matches.removeIf(tx -> !tx.getStructuredNote().containsAll(note));
        return matches;
    }

    @Override
    public List<Transaction2> getTransactionsByAllianceSender(int allianceId) {
        long allianceKey = TransactionEndpointKey.encode((long) allianceId, TransactionEndpointKey.ALLIANCE_TYPE);
        return queryTransactions(
                "sender_key = :allianceKey",
                Map.of("allianceKey", allianceKey),
                null,
                null,
                null
        );
    }

    @Override
    public Set<Integer> getReceiverNationIdFromAllianceReceivers(Set<Integer> allianceIds) {
        if (allianceIds == null || allianceIds.isEmpty()) {
            throw new IllegalArgumentException("allianceIds must not be empty");
        }

        List<Long> keys = allianceKeys(allianceIds);

        List<Long> senderKeys = jdbi().withHandle(handle ->
                handle.createQuery("""
                        SELECT DISTINCT sender_key
                        FROM TRANSACTIONS_2
                        WHERE receiver_key IN (<receiverKeys>)
                        """)
                        .bindList("receiverKeys", keys)
                        .mapTo(Long.class)
                        .list()
        );

        Set<Integer> result = new HashSet<>();
        for (Long senderKey : senderKeys) {
            if (TransactionEndpointKey.typeFromKey(senderKey) == TransactionEndpointKey.NATION_TYPE) {
                result.add((int) TransactionEndpointKey.idFromKey(senderKey));
            }
        }
        return result;
    }

    @Override
    public List<Transaction2> getTransactionsByAllianceReceiver(int allianceId) {
        long allianceKey = TransactionEndpointKey.encode((long) allianceId, TransactionEndpointKey.ALLIANCE_TYPE);
        return queryTransactions(
                "receiver_key = :allianceKey",
                Map.of("allianceKey", allianceKey),
                null,
                null,
                null
        );
    }

    public List<Transaction2> getPotentialDuplicateAllianceBankTransfers(
            int allianceId,
            long routeStartDate,
            long routeEndDate,
            long matchWindowMs
    ) {
        if (routeEndDate < routeStartDate) {
            throw new IllegalArgumentException("routeEndDate must be >= routeStartDate");
        }
        if (matchWindowMs < 0) {
            throw new IllegalArgumentException("matchWindowMs must be >= 0");
        }

        long allianceKey = TransactionEndpointKey.encode((long) allianceId, TransactionEndpointKey.ALLIANCE_TYPE);
        long candidateStartDate = routeStartDate <= matchWindowMs ? 0L : routeStartDate - matchWindowMs;
        long candidateEndDate = routeEndDate >= Long.MAX_VALUE - matchWindowMs ? Long.MAX_VALUE
                : routeEndDate + matchWindowMs;

        String senderAllianceTypeSql = hasEndpointTypeSql("sender_key", "senderType");
        String receiverAllianceTypeSql = hasEndpointTypeSql("receiver_key", "receiverType");
        String whereSql = "tx_datetime >= :candidateStartDate AND tx_datetime <= :candidateEndDate AND "
                + senderAllianceTypeSql
                + " AND sender_key IN ("
                + "SELECT DISTINCT sender_key FROM TRANSACTIONS_2 WHERE receiver_key = :allianceKey "
                + "AND tx_datetime >= :routeStartDate AND tx_datetime <= :routeEndDate AND "
                + senderAllianceTypeSql + " AND " + receiverAllianceTypeSql
                + ")";

        return queryTransactions(
                whereSql,
                Map.of(
                        "allianceKey", allianceKey,
                        "candidateStartDate", candidateStartDate,
                        "candidateEndDate", candidateEndDate,
                        "routeStartDate", routeStartDate,
                        "routeEndDate", routeEndDate,
                        "senderType", TransactionEndpointKey.ALLIANCE_TYPE,
                        "receiverType", TransactionEndpointKey.ALLIANCE_TYPE
                ),
                null,
                "tx_datetime ASC, tx_id ASC",
                null
            );
    }

    @Override
    public List<Transaction2> getTransactionsByAlliance(int allianceId) {
        long allianceKey = TransactionEndpointKey.encode((long) allianceId, TransactionEndpointKey.ALLIANCE_TYPE);
        return queryTransactions(
                "sender_key = :allianceKey OR receiver_key = :allianceKey",
                Map.of("allianceKey", allianceKey),
                null,
                null,
                null
        );
    }

    @Override
    public synchronized int[] addTransactions(List<Transaction2> transactions, boolean ignoreInto) {
        if (transactions == null || transactions.isEmpty()) {
            return new int[0];
        }

        String sql = ignoreInto ? INSERT_OR_IGNORE_TRANSACTION_SQL : UPSERT_TRANSACTION_SQL;
        invalidateTXCache();

        int[] result = jdbi().inTransaction(handle -> {
            PreparedBatch batch = handle.prepareBatch(sql);
            BitBuffer noteBuffer = Transaction2.createNoteBuffer();

            for (Transaction2 tx : transactions) {
                byte[] noteBytes = tx.getNoteBytes(noteBuffer);

                batch.bind("txId", tx.tx_id)
                        .bind("txDatetime", tx.tx_datetime)
                        .bind("senderKey", tx.getSenderKey())
                        .bind("receiverKey", tx.getReceiverKey())
                        .bind("bankerNationId", tx.banker_nation)
                        // IMPORTANT: use bindBySqlType for nullable BLOBs
                        .bindBySqlType("note", noteBytes, Types.BLOB)
                        .add();
            }

            return batch.execute();
        });

        synchronized (transactionCache2) {
            if (!transactionCache2.isEmpty()) {
                for (int i = 0; i < transactions.size(); i++) {
                    if (result[i] > 0) {
                        cache(transactions.get(i));
                    }
                }
            }
        }

        return result;
    }

    private void cache(Transaction2 tx) {
        if (tx.sender_type != TransactionEndpointKey.ALLIANCE_TYPE
                || tx.receiver_type != TransactionEndpointKey.ALLIANCE_TYPE) {
            return;
        }

        Set<Transaction2> existingSet = transactionCache2.get((int) tx.receiver_id);
        if (existingSet != null) {
            existingSet.add(tx);
        }
    }

    @Override
    public int addTransaction(Transaction2 tx, boolean ignoreInto) {
        return addTransactions(Collections.singletonList(tx), ignoreInto)[0];
    }

    @Override
    public List<TaxDeposit> getTaxesPaid(int nation, int alliance) {
        return queryTaxDeposits(
                "alliance = :alliance AND nation = :nation",
                Map.of("alliance", alliance, "nation", nation),
                null,
                null,
                null
        );
    }

    @Override
    public void iterateTaxesPaid(
            Set<Integer> nationIds,
            Set<Integer> alliances,
            boolean includeNoInternal,
            boolean includeMaxInternal,
            long start,
            long end,
            Consumer<TaxDeposit> consumer
    ) {
        if (nationIds == null || nationIds.isEmpty()) {
            return;
        }

        List<String> clauses = new ArrayList<>();
        Map<String, Object> scalarBinds = new HashMap<>();
        Map<String, Collection<?>> listBinds = new HashMap<>();

        clauses.add(inClause("nation", "nationIds"));
        listBinds.put("nationIds", sortedIntegers(nationIds));

        if (!includeNoInternal) {
            clauses.add("internal_taxrate != :noInternal");
            scalarBinds.put("noInternal", -1);
        }

        if (!includeMaxInternal) {
            clauses.add("internal_taxrate != :maxInternal");
            scalarBinds.put("maxInternal", (int) MathMan.pairByte(100, 100));
        }

        if (start > 0) {
            clauses.add("date >= :start");
            scalarBinds.put("start", start);
        }

        if (end != Long.MAX_VALUE) {
            clauses.add("date <= :end");
            scalarBinds.put("end", end);
        }

        if (alliances != null && !alliances.isEmpty()) {
            clauses.add(inClause("alliance", "alliances"));
            listBinds.put("alliances", sortedIntegers(alliances));
        }

        streamTaxDeposits(andJoin(clauses), scalarBinds, listBinds, null, null, consumer);
    }

    @Override
    public List<TaxDeposit> getTaxesByIds(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        return queryTaxDeposits(
                inClause("id", "ids"),
                null,
                Map.of("ids", sortedIntegers(ids)),
                null,
                null
        );
    }

    @Override
    public List<TaxDeposit> getTaxesByBrackets(Collection<Integer> bracketIds) {
        if (bracketIds == null || bracketIds.isEmpty()) {
            return Collections.emptyList();
        }

        return queryTaxDeposits(
                inClause("tax_id", "taxIds"),
                null,
                Map.of("taxIds", sortedIntegers(bracketIds)),
                null,
                null
        );
    }

    @Override
    public List<TaxDeposit> getTaxesByNations(Collection<Integer> nationIds) {
        if (nationIds == null || nationIds.isEmpty()) {
            return Collections.emptyList();
        }

        return queryTaxDeposits(
                inClause("nation", "nationIds"),
                null,
                Map.of("nationIds", sortedIntegers(nationIds)),
                null,
                null
        );
    }

    @Override
    public List<TaxDeposit> getTaxesByBracket(int taxId) {
        return getTaxesByBracket(taxId, 0, Long.MAX_VALUE);
    }

    @Override
    public List<TaxDeposit> getTaxesByBracket(int taxId, long start, long end) {
        List<String> clauses = new ArrayList<>();
        Map<String, Object> scalarBinds = new HashMap<>();

        clauses.add("tax_id = :taxId");
        scalarBinds.put("taxId", taxId);

        if (start > 0) {
            clauses.add("date >= :start");
            scalarBinds.put("start", start);
        }

        if (end != Long.MAX_VALUE) {
            clauses.add("date <= :end");
            scalarBinds.put("end", end);
        }

        return queryTaxDeposits(andJoin(clauses), scalarBinds, null, null, null);
    }

    @Override
    public List<TaxDeposit> getTaxesPaid(int nation) {
        return queryTaxDeposits(
                "nation = :nation",
                Map.of("nation", nation),
                null,
                null,
                null
        );
    }

    @Override
    public List<TaxDeposit> getTaxesByAA(int alliance) {
        return queryTaxDeposits(
                "alliance = :alliance",
                Map.of("alliance", alliance),
                null,
                null,
                null
        );
    }

    @Override
    public List<TaxDeposit> getTaxesByAA(Set<Integer> allianceIds) {
        if (allianceIds == null || allianceIds.isEmpty()) {
            return Collections.emptyList();
        }

        return queryTaxDeposits(
                inClause("alliance", "allianceIds"),
                null,
                Map.of("allianceIds", sortedIntegers(allianceIds)),
                null,
                null
        );
    }

    @Override
    public TaxDeposit getLatestTaxDeposit(int allianceId) {
        return querySingleTaxDeposit(
                "alliance = :allianceId",
                Map.of("allianceId", allianceId),
                null,
                "date DESC"
        );
    }

    @Override
    public List<TaxDeposit> getTaxesByTurn(int alliance) {
        List<TaxDeposit> deposits = queryTaxDeposits(
                "alliance = :alliance",
                Map.of("alliance", alliance),
                null,
                "date ASC",
                null
        );

        List<TaxDeposit> list = new ArrayList<>();
        TaxDeposit turnTotal = null;

        double moneyRateDouble = 0;
        double rssRateDouble = 0;
        double intMoneyRateDouble = 0;
        double intRssRateDouble = 0;
        int i = 0;

        for (TaxDeposit nextDeposit : deposits) {
            i++;

            if (turnTotal == null) {
                i = 1;
                turnTotal = nextDeposit;
                moneyRateDouble = turnTotal.moneyRate;
                rssRateDouble = turnTotal.resourceRate;
                intMoneyRateDouble = turnTotal.internalMoneyRate;
                intRssRateDouble = turnTotal.internalResourceRate;
            } else if (Math.abs(turnTotal.date - nextDeposit.date) > TURN_GROUP_WINDOW_MS) {
                applyAverageRates(turnTotal, moneyRateDouble, rssRateDouble, intMoneyRateDouble, intRssRateDouble);
                list.add(turnTotal);

                i = 1;
                turnTotal = nextDeposit;
                moneyRateDouble = turnTotal.moneyRate;
                rssRateDouble = turnTotal.resourceRate;
                intMoneyRateDouble = turnTotal.internalMoneyRate;
                intRssRateDouble = turnTotal.internalResourceRate;
            } else {
                moneyRateDouble = ((moneyRateDouble * (i - 1d) + nextDeposit.moneyRate) / i);
                rssRateDouble = ((rssRateDouble * (i - 1d) + nextDeposit.resourceRate) / i);
                intMoneyRateDouble = ((intMoneyRateDouble * (i - 1d) + nextDeposit.internalMoneyRate) / i);
                intRssRateDouble = ((intRssRateDouble * (i - 1d) + nextDeposit.internalResourceRate) / i);
                ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, turnTotal.resources, nextDeposit.resources, false);
            }
        }

        if (turnTotal != null) {
            applyAverageRates(turnTotal, moneyRateDouble, rssRateDouble, intMoneyRateDouble, intRssRateDouble);
            list.add(turnTotal);
        }

        return list;
    }

    @Override
    public void deleteTaxDeposits(int allianceId, long date) {
        jdbi().useHandle(handle -> {
            handle.createUpdate("""
                    DELETE FROM TAX_DEPOSITS_DATE
                    WHERE alliance = :allianceId AND date >= :date
                    """)
                    .bind("allianceId", allianceId)
                    .bind("date", date)
                    .execute();

            handle.createUpdate("""
                    DELETE FROM TAX_SUMMARY
                    WHERE alliance_id = :allianceId AND date >= :date
                    """)
                    .bind("allianceId", allianceId)
                    .bind("date", date)
                    .execute();
        });

        synchronized (lastTaxSummaryUpdateByAlliance) {
            lastTaxSummaryUpdateByAlliance.remove(allianceId);
            lastTaxRecordDateByAlliance.remove(allianceId);
            lastTaxBaseByAlliance.remove(allianceId);
        }
    }

    @Override
    public synchronized void addTaxDeposits(Collection<TaxDeposit> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        jdbi().useTransaction(handle -> {
            PreparedBatch batch = handle.prepareBatch(INSERT_OR_IGNORE_TAX_DEPOSIT_SQL);

            for (TaxDeposit record : records) {
                long dateRounded = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(record.date));

                double[] deposit = record.resources;
                long[] depositCents = new long[deposit.length];
                for (int i = 0; i < deposit.length; i++) {
                    depositCents[i] = (long) (deposit[i] * 100);
                }

                int internalMoneyRate = record.internalMoneyRate >= record.moneyRate ? 100 : record.internalMoneyRate;
                int internalResourceRate = record.internalResourceRate >= record.resourceRate ? 100 : record.internalResourceRate;
                short internalPair = MathMan.pairByte(internalMoneyRate, internalResourceRate);

                batch.bind("taxId", record.tax_id)
                        .bind("allianceId", record.allianceId)
                        .bind("date", dateRounded)
                        .bind("id", record.index)
                        .bind("nationId", record.nationId)
                        .bind("moneyRate", (int) record.moneyRate)
                        .bind("resourceRate", (int) record.resourceRate)
                        .bindBySqlType("resources", ArrayUtil.toByteArray(depositCents), Types.BLOB)
                        .bind("internalTaxRate", (int) internalPair)
                        .add();

                synchronized (lastTaxSummaryUpdateByAlliance) {
                    long prev = lastTaxRecordDateByAlliance.getOrDefault(record.allianceId, Long.MIN_VALUE);
                    lastTaxRecordDateByAlliance.put(record.allianceId, Math.max(prev, dateRounded));
                }
            }

            batch.execute();
        });
    }

    @Override
    public void clearTaxDeposits(int allianceId) {
        jdbi().useHandle(handle -> {
            handle.createUpdate("""
                    DELETE FROM TAX_DEPOSITS_DATE
                    WHERE alliance = :allianceId
                    """)
                    .bind("allianceId", allianceId)
                    .execute();

            handle.createUpdate("""
                    DELETE FROM TAX_SUMMARY
                    WHERE alliance_id = :allianceId
                    """)
                    .bind("allianceId", allianceId)
                    .execute();
        });

        synchronized (lastTaxSummaryUpdateByAlliance) {
            lastTaxSummaryUpdateByAlliance.remove(allianceId);
            lastTaxRecordDateByAlliance.remove(allianceId);
            lastTaxBaseByAlliance.remove(allianceId);
        }
    }

    @Override
    public Map<Integer, TaxBracket> getTaxBracketsFromDeposits() {
        List<TaxBracket> rows = jdbi().withHandle(handle ->
                handle.createQuery("""
                        SELECT tax_id, alliance, moneyrate, resoucerate, MAX(date) AS max_date
                        FROM TAX_DEPOSITS_DATE
                        GROUP BY tax_id, alliance, moneyrate, resoucerate
                        """)
                        .map((rs, ctx) -> new TaxBracket(
                                rs.getInt("tax_id"),
                                rs.getInt("alliance"),
                                "",
                                rs.getInt("moneyrate"),
                                rs.getInt("resoucerate"),
                                rs.getLong("max_date")
                        ))
                        .list()
        );

        Map<Integer, TaxBracket> result = new HashMap<>();
        for (TaxBracket bracket : rows) {
            TaxBracket existing = result.get(bracket.taxId);
            if (existing == null || existing.dateFetched < bracket.dateFetched) {
                result.put(bracket.taxId, bracket);
            }
        }
        return result;
    }

    @Override
    public Map<Integer, TaxBracket> getTaxBracketsAndEstimates() {
        return getTaxBracketsAndEstimates(true, true, true);
    }

    @Override
    public Map<Integer, TaxBracket> getTaxBracketsAndEstimates(
            boolean allowDeposits,
            boolean allowApi,
            boolean addUnknownBrackets
    ) {
        Map<Integer, TaxBracket> rates = new HashMap<>();
        Map<Integer, Integer> taxIdByAlliances = new HashMap<>();
        List<Map.Entry<Integer, TaxBracket>> bracketEntries = new ArrayList<>();

        if (allowDeposits) {
            bracketEntries.addAll(getTaxBracketsFromDeposits().entrySet());
        }
        if (allowApi) {
            bracketEntries.addAll(getTaxBrackets(taxIdByAlliances).entrySet());
        }

        for (Map.Entry<Integer, TaxBracket> entry : bracketEntries) {
            TaxBracket bracket = entry.getValue();
            TaxBracket existing = rates.get(bracket.taxId);
            if (existing == null || existing.dateFetched < bracket.dateFetched) {
                rates.put(bracket.taxId, bracket);
            }
        }

        if (addUnknownBrackets) {
            if (taxIdByAlliances.isEmpty()) {
                taxIdByAlliances.putAll(Locutus.imp().getNationDB().getAllianceIdByTaxId());
            }

            for (Map.Entry<Integer, Integer> entry : taxIdByAlliances.entrySet()) {
                int taxId = entry.getKey();
                int allianceId = entry.getValue();
                if (!rates.containsKey(taxId)) {
                    rates.put(taxId, new TaxBracket(taxId, allianceId, "", -1, -1, 0));
                }
            }
        }

        return rates;
    }

    @Override
    public Map<Integer, TaxBracket> getTaxBrackets() {
        return getTaxBrackets(new HashMap<>());
    }

    @Override
    public Map<Integer, TaxBracket> getTaxBrackets(Map<Integer, Integer> alliancesByTaxId) {
        if (alliancesByTaxId.isEmpty()) {
            alliancesByTaxId.putAll(Locutus.imp().getNationDB().getAllianceIdByTaxId());
        }

        List<TaxBracket> rows = jdbi().withHandle(handle ->
                handle.createQuery("""
                        SELECT id, money, resources, date
                        FROM TAX_BRACKETS
                        """)
                        .map((rs, ctx) -> {
                            int id = rs.getInt("id");
                            int allianceId = alliancesByTaxId.getOrDefault(id, 0);
                            return new TaxBracket(
                                    id,
                                    allianceId,
                                    "",
                                    rs.getInt("money"),
                                    rs.getInt("resources"),
                                    rs.getLong("date")
                            );
                        })
                        .list()
        );

        Map<Integer, TaxBracket> result = new HashMap<>();
        for (TaxBracket bracket : rows) {
            result.put(bracket.taxId, bracket);
        }
        return result;
    }

    @Override
    public void addTaxBracket(TaxBracket bracket) {
        jdbi().useHandle(handle ->
                handle.createUpdate("""
                        INSERT INTO TAX_BRACKETS(id, money, resources, date)
                        VALUES (:id, :money, :resources, :date)
                        ON CONFLICT(id) DO UPDATE SET
                        money = excluded.money,
                        resources = excluded.resources,
                        date = excluded.date
                        """)
                        .bind("id", bracket.taxId)
                        .bind("money", bracket.moneyRate)
                        .bind("resources", bracket.rssRate)
                        .bind("date", bracket.dateFetched)
                        .execute()
        );
    }

    @Override
    public synchronized void purgeSubscriptions() {
        long now = System.currentTimeMillis();
        jdbi().useHandle(handle ->
                handle.createUpdate("DELETE FROM SUBSCRIPTIONS WHERE date < :now")
                        .bind("now", now)
                        .execute()
        );
    }

    @Override
    public void unsubscribeAll(long userId) {
        jdbi().useHandle(handle ->
                handle.createUpdate("DELETE FROM SUBSCRIPTIONS WHERE user = :user")
                        .bind("user", userId)
                        .execute()
        );
    }

    @Override
    public void unsubscribe(User user, int allianceOrNation, BankSubType type) {
        jdbi().useHandle(handle ->
                handle.createUpdate("""
                        DELETE FROM SUBSCRIPTIONS
                        WHERE user = :user
                        AND allianceOrNation = :allianceOrNation
                        AND (isNation & :mask) > 0
                        """)
                        .bind("user", user.getIdLong())
                        .bind("allianceOrNation", allianceOrNation)
                        .bind("mask", type.mask)
                        .execute()
        );
    }

    @Override
    public void subscribe(
            User user,
            int allianceOrNation,
            BankSubType type,
            long date,
            boolean isReceive,
            long amount
    ) {
        jdbi().useHandle(handle ->
                handle.createUpdate("""
                        INSERT INTO SUBSCRIPTIONS(user, allianceOrNation, isNation, date, isReceive, amount)
                        VALUES (:user, :allianceOrNation, :isNation, :date, :isReceive, :amount)
                        ON CONFLICT(user, allianceOrNation, isNation) DO UPDATE SET
                        date = excluded.date,
                        isReceive = excluded.isReceive,
                        amount = excluded.amount
                        """)
                        .bind("user", user.getIdLong())
                        .bind("allianceOrNation", allianceOrNation)
                        .bind("isNation", type.mask)
                        .bind("date", date)
                        .bind("isReceive", isReceive ? 1 : 0)
                        .bind("amount", amount)
                        .execute()
        );
    }

    @Override
    public Set<Subscription> getSubscriptions(
            int allianceOrNation,
            BankSubType type,
            boolean isReceive,
            long amount
    ) {
        long now = System.currentTimeMillis();

        List<Subscription> rows = jdbi().withHandle(handle ->
                handle.createQuery("""
                        SELECT user, allianceOrNation, isNation, date, isReceive, amount
                        FROM SUBSCRIPTIONS
                        WHERE allianceOrNation = :allianceOrNation
                        AND (isNation & :mask) > 0
                        AND isReceive = :isReceive
                        AND amount <= :amount
                        AND date > :now
                        """)
                        .bind("allianceOrNation", allianceOrNation)
                        .bind("mask", type.mask)
                        .bind("isReceive", isReceive ? 1 : 0)
                        .bind("amount", amount)
                        .bind("now", now)
                        .map(SUBSCRIPTION_MAPPER)
                        .list()
        );

        return new LinkedHashSet<>(rows);
    }

    @Override
    public Set<Subscription> getSubscriptions(long userId) {
        long now = System.currentTimeMillis();

        List<Subscription> rows = jdbi().withHandle(handle ->
                handle.createQuery("""
                        SELECT user, allianceOrNation, isNation, date, isReceive, amount
                        FROM SUBSCRIPTIONS
                        WHERE user = :user
                        AND date > :now
                        """)
                        .bind("user", userId)
                        .bind("now", now)
                        .map(SUBSCRIPTION_MAPPER)
                        .list()
        );

        return new LinkedHashSet<>(rows);
    }

    @Override
    public List<Transaction2> getAllianceTransactions(
            Set<Integer> receiverAAs,
            boolean includeLegacy,
            Predicate<Transaction2> filter
    ) {
        Predicate<Transaction2> actualFilter = filter == null ? tx -> true : filter;
        List<Transaction2> result = new ArrayList<>();
        if (receiverAAs == null || receiverAAs.isEmpty()) {
            return result;
        }

        List<Integer> remaining = new ArrayList<>();

        synchronized (transactionCache2) {
            for (int id : receiverAAs) {
                Set<Transaction2> existing = transactionCache2.get(id);
                if (existing != null) {
                    for (Transaction2 tx : existing) {
                        if (actualFilter.test(tx)) {
                            result.add(tx);
                        }
                    }
                } else {
                    transactionCache2.put(id, new HashSet<>());
                    remaining.add(id);
                }
            }
        }

        if (remaining.isEmpty()) {
            return result;
        }

        // includeLegacy is now effectively a no-op: legacy rows are migrated into TRANSACTIONS_2 on startup.
        List<Transaction2> modern = queryTransactions(
                hasEndpointTypeSql("sender_key", "senderType")
                        + " AND "
                        + hasEndpointTypeSql("receiver_key", "receiverType")
                        + " AND "
                        + inClause("receiver_key", "receiverKeys"),
                Map.of(
                        "senderType", TransactionEndpointKey.ALLIANCE_TYPE,
                        "receiverType", TransactionEndpointKey.ALLIANCE_TYPE
                ),
                Map.of("receiverKeys", allianceKeys(remaining)),
                null,
                null
        );

        for (Transaction2 tx : modern) {
            if (actualFilter.test(tx)) {
                result.add(tx);
            }
        }

        synchronized (transactionCache2) {
            for (Transaction2 tx : modern) {
                transactionCache2
                        .computeIfAbsent((int) tx.receiver_id, ignored -> new HashSet<>())
                        .add(tx);
            }
        }

        return result;
    }

    public enum BankSubType {
        ALL(7),
        ALLIANCE(1),
        NATION(2),
        GUILD(4);

        public final int mask;

        BankSubType(int mask) {
            this.mask = mask;
        }

        public static BankSubType of(boolean isAA) {
            return isAA ? ALLIANCE : NATION;
        }

        public static BankSubType get(int isNation) {
            for (BankSubType type : values()) {
                if (type.mask == isNation) {
                    return type;
                }
            }
            return null;
        }
    }

    public static final class Subscription {
        public final long user;
        public final int allianceOrNation;
        public final BankSubType type;
        public final long endDate;
        public final boolean isReceive;
        public final long amount;

        public Subscription(
                long user,
                int allianceOrNation,
                BankSubType type,
                long endDate,
                boolean isReceive,
                long amount
        ) {
            this.user = user;
            this.allianceOrNation = allianceOrNation;
            this.type = type;
            this.endDate = endDate;
            this.isReceive = isReceive;
            this.amount = amount;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Subscription that)) {
                return false;
            }
            return user == that.user
                    && allianceOrNation == that.allianceOrNation
                    && endDate == that.endDate
                    && isReceive == that.isReceive
                    && amount == that.amount
                    && type == that.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(user, allianceOrNation, type, endDate, isReceive, amount);
        }

        @Override
        public String toString() {
            return "Subscription{" +
                    "user=" + user +
                    ", allianceOrNation=" + allianceOrNation +
                    ", type=" + type +
                    ", endDate=" + endDate +
                    ", isReceive=" + isReceive +
                    ", amount=" + amount +
                    '}';
        }
    }

    private static final class TaxRecordSummary {
        private final double[] no_internal_applied;
        private final double[] no_internal_unapplied;
        private final double[] internal_applied;
        private final double[] internal_unapplied;
        private boolean dirty;

        private TaxRecordSummary(
                double[] noInternalApplied,
                double[] noInternalUnapplied,
                double[] internalApplied,
                double[] internalUnapplied,
                boolean dirty
        ) {
            this.no_internal_applied = noInternalApplied;
            this.no_internal_unapplied = noInternalUnapplied;
            this.internal_applied = internalApplied;
            this.internal_unapplied = internalUnapplied;
            this.dirty = dirty;
        }
    }

    private static final class StoredTaxSummaryRow {
        private final int allianceId;
        private final int nationId;
        private final byte[] noInternalApplied;
        private final byte[] noInternalUnapplied;
        private final byte[] internalApplied;
        private final byte[] internalUnapplied;

        private StoredTaxSummaryRow(
                int allianceId,
                int nationId,
                byte[] noInternalApplied,
                byte[] noInternalUnapplied,
                byte[] internalApplied,
                byte[] internalUnapplied
        ) {
            this.allianceId = allianceId;
            this.nationId = nationId;
            this.noInternalApplied = noInternalApplied;
            this.noInternalUnapplied = noInternalUnapplied;
            this.internalApplied = internalApplied;
            this.internalUnapplied = internalUnapplied;
        }
    }

    private static final class SummaryAmountsRow {
        private final int nationId;
        private final byte[] baseAmount;
        private final byte[] otherAmount;

        private SummaryAmountsRow(int nationId, byte[] baseAmount, byte[] otherAmount) {
            this.nationId = nationId;
            this.baseAmount = baseAmount;
            this.otherAmount = otherAmount;
        }
    }
}