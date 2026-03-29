package link.locutus.discord.db;

import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.TransactionEndpointKey;
import link.locutus.discord.util.io.BitBuffer;
import link.locutus.discord.util.offshore.OffshoreInstance;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.Set;
import java.util.stream.Stream;

final class OffshoreBenchmarkSupport {
    static final long DEFAULT_GUILD_ID = 672217848311054346L;
    static final long START = 0L;
    static final long END = Long.MAX_VALUE;
    static final int GUILD_TYPE = TransactionEndpointKey.GUILD_TYPE;
    static final int ALLIANCE_TYPE = TransactionEndpointKey.ALLIANCE_TYPE;

    private static final int MULTI_ALLIANCE_COUNT = 4;
    private static final String OWNER_IDS_TEST_CSV = """
            7425,8706,12290,11270,11782,7432,9736,8457,8205,11023,10003,7445,8472,8728,11290,8476,11036,8222,10527,
            10529,11298,7977,8745,10795,11307,8493,10546,10035,8760,11064,11320,10809,11836,8512,8513,12355,11332,
            8773,11077,10822,8264,7498,10570,12363,7244,8782,11090,8789,8533,12373,7766,11350,10585,9821,11103,
            7521,10081,7778,11106,9315,10595,8549,7529,11372,7534,10608,9585,10865,11127,10109,7550,12414,11647,
            10881,9347,9605,10631,11400,11656,9614,10896,12432,8597,11417,11163,9632,7332,7589,9644,7597,12461,
            11182,10415,10927,10672,8628,11445,9403,8636,10429,11709,7614,10691,11204,12230,7623,11719,11464,
            13769,9674,9419,11214,10703,11729,11474,12244,7637,9941,9686,7384,7385,7644,8669,10973,9954,11237,
            7911,10728,11497,10987,12271,13809,8692,11509,11257,8443,11518
            """;
    private static final String USER_IDS_TEST_CSV = """
            14336,12290,14341,13318,12295,14344,11273,1428570979713679392,13324,14349,13326,9230,11281,
            1357889124039790845,14354,10259,1162818416835563713,1108023670498152468,12312,9241,12314,13339,13342,
            13345,1061444001242296362,1268518968205901875,13356,1362091535780417536,14382,13360,14386,
            1455255634889936970,12339,1285343873140392017,14390,1338187400953856051,11324,1366532239797653596,
            1277641816619094088,14401,1296210357437595788,11330,11331,12355,14404,12357,14405,13382,13384,13386,
            12363,11339,13390,1057740879974105119,14415,13392,13393,14417,14419,1478853253352390883,12373,14421,
            14422,13399,1424314593349730355,1263241407280185404,13423,13431,1466887885797200037,13434,11387,
            1346162589041692843,14459,13436,12414,12418,1124965572698968166,14468,12422,10375,1385144213930508520,
            10377,647252780817448972,1091564651864670248,12432,14480,12433,14481,14485,13463,1120134226965168178,
            14489,14493,1366878520248107038,13475,14501,13482,13483,1414336004344512534,12461,13489,13490,14514,
            13491,1350006914481782824,1412519200785830101,13497,13498,14522,12475,1097581008628482078,13505,
            1485069268281200692,12493,13518,1252491173348507768,1397332960591614042,12497,11474,13524,14549,
            1040139305202503710,11483,13532,1486313524392099962,12512,1452450981676711986,13537,9445,12519,11497,
            13547,1467633006830419968,13552,11509,10488,1421022990048428065,14586,8442,11518,13568,12550,12551,
            13575,1368208090578812989,14601,1077035593973895319,1287277681091805225,8463,12562,1478535724344148152,
            11543,13594,7452,12576,11553,13603,13612,959221677416411176,1424090183044825109,1438917833697263730,
            14643,1424881373201698879,14645,2358,13622,14647,10553,1166241139578376243,1176910636295389265,
            13632,12610,1468974038712979704,1200983066601082961,12611,1338238506471919616,14661,12614,12615,
            14666,13643,14670,14673,1188931831291191386,14675,14676,11607,1378137326643056811,1418607509551452272,
            11610,13658,12635,1145128931473764495,1270594160298164246,13664,14688,14689,13667,13668,13670,14696,
            9577,12649,14700,12653,1430664354638073879,11634,14706,1263223495869857954,11640,1480579107484991660,
            12666,14715,1457072473295159351,14717,14718,11647,14719,9600,13697,13699,10629,14727,11656,13704,
            1478844946600558763,11657,1103439191536320513,1096375542799941632,13709,13711,14735,1266535215967371344,
            13715,1321010334831480934,14743,1363632987962609674,14745,13723,14748,14751,1396825094306660423,
            948345873182642246,12705,14753,14754,13733,10668,10671,14769,964173142933274636,9651,1282927002671644726,
            1122996886064865363,13751,11709,11711,1111925588920377476,12738,12741,1146432357579116685,11719,
            13769,14793,12751,11729,14802,14808,12774,1401849134066958336,14826,13805,13807,12784,13809,
            1125541644159168583,1353380808551043092,13815,14839,1282072566634250373,13817,13824,14849,12802,
            13828,11782,14859,1330946810134397028,1421787014713315370,14866,1283207384688234507,14869,12823,
            14873,1211798526619684895,4638,14878,12832,1329218694139871243,10788,13861,11818,1323720756164821106,
            1182957349393137704,1283205787321237545,1482085715641897014,11833,9788,11836,13885,14909,11841,7750,
            11846,14918,1199946177144172564,1481846456729079898,13897,1061615892808601651,11853,12879,13907,13908,
            13912,12889,13913,12892,1363933376012226590,9822,11873,11874,14951,12904,1351127353429856256,11884,
            11885,11889,12917,1279742353174691924,12919,12925,13950,877164184360611841,11907,12931,11908,13958,
            1403470355581894758,7815,12941,1114326913180242001,11920,1279597584914186281,13972,11925,12951,9883,
            12958,12960,1146694365960491069,8868,1329617789606170634,11949,1353065341630287984,11954,
            1435101745750736949,12985,11962,10938,11965,14013,14014,1447616692871233690,12991,1366067465947316265,
            12994,8899,9927,11977,1197581560111706182,1235326933764411424,14026,1199014340314009690,14029,1742,
            1445311581708882044,13009,1286108479840456787,1363974558322917406,14036,13014,1296983631864004698,
            14047,12000,1297124861055074385,1324416999715242035,14056,12009,13033,12010,14058,12011,1343293383392759869,
            13045,11003,1440360427891462187,1464730711499211006,11009,9986,14082,1283235968786366494,14086,14090,
            8972,1449845846173552722,1404811017258532865,1132644199985070150,12053,11032,14108,11037,14109,14110,
            1330506368284626967,14114,14117,14122,14124,1187910561799864340,1372205302019264652,756580822739320883,
            1282441925529571480,14133,1283237899881414698,1303212182711963699,13111,11066,13115,12092,
            1337520010981015662,14141,12094,1264109379901198396,14146,14147,13125,14151,1327992354581254246,
            14160,1280642965844262953,1424290236720218174,14163,12116,1278977526617870438,846710610028396554,
            13142,14166,13143,1324783742677422193,14170,10075,14172,14173,12128,14177,1461732625403347194,
            14181,1147757659571888141,14185,14189,13168,9074,13175,678328116107542541,14199,14201,1136336457439653959,
            1155733529196494901,1477222411542724682,13183,14207,1135060034368327703,12166,13194,11149,
            672217848311054346,14225,13202,1279965157598036050,1255383950860484659,14230,14232,11162,14234,
            1404582621228306576,14237,1249402766653390850,14245,362547926511386625,14248,1271424607349379133,
            13225,1382127372656181378,1287274272615698574,11186,7094,1308155586990309516,1308104715489251349,
            13250,11202,14276,12230,845289446147620915,11209,14282,1382658088733118474,11218,12244,13271,
            1321636127467245699,13275,14305,10213,13286,14310,9192,13290,12271,14326,1086518847311462410,
            12281,13307,1362026605278662818,1424496320441356320,1277039575985819709
            """;

    private static volatile BenchmarkFixture sharedFixture;
    private static volatile Field bankCacheField;

    private OffshoreBenchmarkSupport() {
    }

    static BenchmarkFixture fixture() {
        BenchmarkFixture current = sharedFixture;
        if (current != null) {
            return current;
        }
        synchronized (OffshoreBenchmarkSupport.class) {
            current = sharedFixture;
            if (current != null) {
                return current;
            }
            current = loadFixture();
            sharedFixture = current;
            return current;
        }
    }

    static void configureDatabaseDirectory(BenchmarkFixture fixture) {
        Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY = fixture.snapshotDirectory().toAbsolutePath().toString();
    }

    static void clearBankTransactionCache(BankDB bankDb) {
        try {
            Field field = bankCacheField;
            if (field == null) {
                synchronized (OffshoreBenchmarkSupport.class) {
                    field = bankCacheField;
                    if (field == null) {
                        field = BankDB.class.getDeclaredField("transactionCache2");
                        field.setAccessible(true);
                        bankCacheField = field;
                    }
                }
            }
            @SuppressWarnings("unchecked")
            Map<Integer, Set<Transaction2>> cache = (Map<Integer, Set<Transaction2>>) field.get(bankDb);
            synchronized (cache) {
                cache.clear();
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to clear BankDB transaction cache for benchmark setup", e);
        }
    }

    static void shutdownBenchmarkSchedulers() {
        shutdownTimeUtilScheduler();
    }

    static List<Transaction2> decodeRows(List<StoredPayloadRow> rows) {
        return decodeRows(rows, Transaction2.createNoteBuffer());
    }

    static List<Transaction2> decodeRows(List<StoredPayloadRow> rows, BitBuffer buffer) {
        List<Transaction2> decoded = new ArrayList<>(rows.size());
        for (StoredPayloadRow row : rows) {
            decoded.add(row.decode(buffer));
        }
        return decoded;
    }

    static List<Transaction2> loadGuildTransactions(BankDB bankDb, GuildDB guildDb, BenchmarkFixture fixture) {
        List<Transaction2> toProcess = new ArrayList<>();
        toProcess.addAll(bankDb.getAllianceTransactions(
                fixture.offshoreIds(),
                true,
                OffshoreInstance.getFilter(fixture.guildId(), GUILD_TYPE)));
        toProcess.addAll(guildDb.getDepositOffsetTransactionsForGuild(fixture.guildId(), START, END));
        return toProcess;
    }

    static List<Transaction2> loadAllianceTransactions(BankDB bankDb, GuildDB guildDb, BenchmarkFixture fixture,
            int allianceId) {
        List<Transaction2> toProcess = new ArrayList<>();
        toProcess.addAll(bankDb.getAllianceTransactions(
                fixture.offshoreIds(),
                true,
                OffshoreInstance.getFilter(allianceId, ALLIANCE_TYPE)));
        toProcess.addAll(guildDb.getDepositOffsetTransactionsForAlliance(allianceId, START, END));
        return toProcess;
    }

    static List<Transaction2> loadAllianceTransactions(BankDB bankDb, GuildDB guildDb, BenchmarkFixture fixture,
            List<Integer> allianceIds) {
        List<Transaction2> toProcess = new ArrayList<>();
        for (int allianceId : allianceIds) {
            toProcess.addAll(loadAllianceTransactions(bankDb, guildDb, fixture, allianceId));
        }
        return toProcess;
    }

    private static BenchmarkFixture loadFixture() {
        try {
            Path snapshotDirectory = createSnapshotDirectory();
            Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY = snapshotDirectory.toAbsolutePath().toString();

            Set<Integer> offshoreIds = parseIntegerSet(OWNER_IDS_TEST_CSV);
            Set<Long> offshoringIds = parseLongSet(USER_IDS_TEST_CSV);
            List<Integer> candidateAllianceIds = extractAllianceIds(offshoringIds);

            try (ActualBankStore bankStore = new ActualBankStore(snapshotDirectory);
                    ActualGuildStore guildStore = new ActualGuildStore(snapshotDirectory, DEFAULT_GUILD_ID)) {
                List<StoredPayloadRow> bankRows = bankStore.loadAllianceTransactionRows(offshoreIds);
                long guildInternalOffsetCount = guildStore.countTransactionsByEndpoint(DEFAULT_GUILD_ID, GUILD_TYPE);
                Map<Integer, Long> internalCounts = guildStore.countAllianceTransactions(candidateAllianceIds);
                Map<Integer, Long> bankSenderCounts = bankStore.countSenderTransactions(offshoreIds, candidateAllianceIds);
                List<Integer> trackedAllianceIds = selectTrackedAllianceIds(
                        candidateAllianceIds,
                        internalCounts,
                        bankSenderCounts,
                        MULTI_ALLIANCE_COUNT);
                Set<Long> trackedAllianceIdsLong = new LinkedHashSet<>();
                for (int allianceId : trackedAllianceIds) {
                    trackedAllianceIdsLong.add((long) allianceId);
                }

                return new BenchmarkFixture(
                        snapshotDirectory,
                        DEFAULT_GUILD_ID,
                        Collections.unmodifiableSet(new LinkedHashSet<>(offshoreIds)),
                        Collections.unmodifiableSet(new LinkedHashSet<>(offshoringIds)),
                        trackedAllianceIds.getFirst(),
                        Collections.unmodifiableList(new ArrayList<>(trackedAllianceIds)),
                        Collections.unmodifiableSet(new LinkedHashSet<>(trackedAllianceIdsLong)),
                        Collections.unmodifiableList(new ArrayList<>(bankRows)),
                        guildInternalOffsetCount,
                        Collections.unmodifiableMap(new LinkedHashMap<>(bankSenderCounts)),
                        Collections.unmodifiableMap(new LinkedHashMap<>(internalCounts)));
            }
        } catch (IOException | SQLException | ClassNotFoundException e) {
            throw new IllegalStateException("Failed to initialize the real-data offshore benchmark fixture", e);
        }
    }

    private static Path createSnapshotDirectory() throws IOException {
        Path snapshotDirectory = Files.createTempDirectory("offshore-jmh-");
        Path sourceDirectory = Path.of("database");
        copySnapshotFile(sourceDirectory.resolve("bank.db"), snapshotDirectory.resolve("bank.db"), true);
        copySnapshotFile(sourceDirectory.resolve("bank.db-shm"), snapshotDirectory.resolve("bank.db-shm"), false);
        copySnapshotFile(sourceDirectory.resolve("bank.db-wal"), snapshotDirectory.resolve("bank.db-wal"), false);

        Path guildSourceDirectory = sourceDirectory.resolve("guilds");
        Path guildSnapshotDirectory = snapshotDirectory.resolve("guilds");
        Files.createDirectories(guildSnapshotDirectory);
        String guildName = Long.toString(DEFAULT_GUILD_ID);
        copySnapshotFile(guildSourceDirectory.resolve(guildName + ".db"), guildSnapshotDirectory.resolve(guildName + ".db"), true);
        copySnapshotFile(guildSourceDirectory.resolve(guildName + ".db-shm"), guildSnapshotDirectory.resolve(guildName + ".db-shm"), false);
        copySnapshotFile(guildSourceDirectory.resolve(guildName + ".db-wal"), guildSnapshotDirectory.resolve(guildName + ".db-wal"), false);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(snapshotDirectory)));
        return snapshotDirectory;
    }

    private static void copySnapshotFile(Path source, Path target, boolean required) throws IOException {
        if (Files.notExists(source)) {
            if (required) {
                throw new IOException("Missing benchmark snapshot source file: " + source);
            }
            return;
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static void deleteRecursively(Path root) {
        if (root == null || Files.notExists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static Set<Integer> parseIntegerSet(String csv) {
        Set<Integer> values = new LinkedHashSet<>();
        for (String token : csv.split("[,\\s]+")) {
            if (!token.isBlank()) {
                values.add(Integer.parseInt(token));
            }
        }
        return values;
    }

    private static Set<Long> parseLongSet(String csv) {
        Set<Long> values = new LinkedHashSet<>();
        for (String token : csv.split("[,\\s]+")) {
            if (!token.isBlank()) {
                values.add(Long.parseLong(token));
            }
        }
        return values;
    }

    private static List<Integer> extractAllianceIds(Set<Long> ids) {
        Set<Integer> allianceIds = new LinkedHashSet<>();
        for (long id : ids) {
            if (id > 0L && id <= Integer.MAX_VALUE) {
                allianceIds.add((int) id);
            }
        }
        return new ArrayList<>(allianceIds);
    }

    private static List<Integer> selectTrackedAllianceIds(
            List<Integer> candidateAllianceIds,
            Map<Integer, Long> internalCounts,
            Map<Integer, Long> bankCounts,
            int limit
    ) {
        List<Integer> ranked = new ArrayList<>();
        for (int allianceId : candidateAllianceIds) {
            long internal = internalCounts.getOrDefault(allianceId, 0L);
            long bank = bankCounts.getOrDefault(allianceId, 0L);
            if (internal > 0L || bank > 0L) {
                ranked.add(allianceId);
            }
        }

        ranked.sort((left, right) -> {
            long leftInternal = internalCounts.getOrDefault(left, 0L);
            long rightInternal = internalCounts.getOrDefault(right, 0L);
            long leftBank = bankCounts.getOrDefault(left, 0L);
            long rightBank = bankCounts.getOrDefault(right, 0L);

            int compare = Long.compare((rightInternal + rightBank), (leftInternal + leftBank));
            if (compare != 0) {
                return compare;
            }
            compare = Long.compare(rightBank, leftBank);
            if (compare != 0) {
                return compare;
            }
            compare = Long.compare(rightInternal, leftInternal);
            if (compare != 0) {
                return compare;
            }
            return Integer.compare(left, right);
        });

        if (ranked.isEmpty()) {
            throw new IllegalStateException("No active alliance ids were found in the hardcoded offshoring set");
        }

        return new ArrayList<>(ranked.subList(0, Math.min(limit, ranked.size())));
    }

    private static List<Long> allianceKeys(Set<Integer> allianceIds) {
        List<Long> keys = new ArrayList<>(allianceIds.size());
        for (int allianceId : allianceIds) {
            keys.add(TransactionEndpointKey.encode(allianceId, ALLIANCE_TYPE));
        }
        return keys;
    }

    private static List<Long> allianceKeys(List<Integer> allianceIds) {
        List<Long> keys = new ArrayList<>(allianceIds.size());
        for (int allianceId : allianceIds) {
            keys.add(TransactionEndpointKey.encode(allianceId, ALLIANCE_TYPE));
        }
        return keys;
    }

    private static void shutdownTimeUtilScheduler() {
        try {
            Class<?> timeUtilClass = Class.forName("link.locutus.discord.util.TimeUtil");
            Field field = timeUtilClass.getDeclaredField("SCHEDULER");
            field.setAccessible(true);
            Object scheduler = field.get(null);
            if (scheduler instanceof ScheduledExecutorService service) {
                service.shutdownNow();
            }
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException ignored) {
        }
    }

    record BenchmarkFixture(
            Path snapshotDirectory,
            long guildId,
            Set<Integer> offshoreIds,
            Set<Long> offshoringIds,
            int primaryAllianceId,
            List<Integer> trackedAllianceIds,
            Set<Long> trackedAllianceIdsLong,
            List<StoredPayloadRow> bankRows,
            long guildInternalOffsetCount,
            Map<Integer, Long> bankSenderCounts,
            Map<Integer, Long> internalCounts
    ) {
    }

    record StoredPayloadRow(
            int txId,
            long txDatetime,
            long senderKey,
            long receiverKey,
            int bankerNationId,
            byte[] payload
    ) {
        static StoredPayloadRow fromResultSet(ResultSet rs) throws SQLException {
            return new StoredPayloadRow(
                    rs.getInt("tx_id"),
                    rs.getLong("tx_datetime"),
                    rs.getLong("sender_key"),
                    rs.getLong("receiver_key"),
                    rs.getInt("banker_nation_id"),
                    rs.getBytes("note"));
        }

        Transaction2 decode(BitBuffer buffer) {
            return Transaction2.fromStoredPayload(
                    txId,
                    txDatetime,
                    senderKey,
                    receiverKey,
                    bankerNationId,
                    payload,
                    buffer);
        }
    }

    static final class ActualBankStore extends DBMainV3 {
        ActualBankStore(Path databaseDirectory) throws SQLException, ClassNotFoundException {
            super(databaseDirectory.toFile(), "bank", true, false, 0, 20, 5);
            createTables();
        }

        @Override
        public void createTables() {
            // The benchmark reads from an on-disk snapshot of the live bank database.
        }

        List<StoredPayloadRow> loadAllianceTransactionRows(Set<Integer> receiverAAs) {
            if (receiverAAs == null || receiverAAs.isEmpty()) {
                return List.of();
            }
            List<Long> receiverKeys = allianceKeys(receiverAAs);
            return jdbi().withHandle(handle -> handle.createQuery("""
                    SELECT tx_id, tx_datetime, sender_key, receiver_key, banker_nation_id, note
                    FROM TRANSACTIONS_2
                    WHERE (sender_key & :typeMask) = :allianceType
                      AND (receiver_key & :typeMask) = :allianceType
                      AND receiver_key IN (<receiverKeys>)
                    ORDER BY tx_id
                    """)
                    .bind("typeMask", TransactionEndpointKey.TYPE_MASK)
                    .bind("allianceType", ALLIANCE_TYPE)
                    .bindList("receiverKeys", receiverKeys)
                    .map((rs, ctx) -> StoredPayloadRow.fromResultSet(rs))
                    .list());
        }

        Map<Integer, Long> countSenderTransactions(Set<Integer> receiverAAs, List<Integer> senderAllianceIds) {
            if (receiverAAs == null || receiverAAs.isEmpty() || senderAllianceIds.isEmpty()) {
                return Map.of();
            }
            List<Long> receiverKeys = allianceKeys(receiverAAs);
            List<Long> senderKeys = allianceKeys(senderAllianceIds);
            List<Map.Entry<Integer, Long>> rows = jdbi().withHandle(handle -> handle.createQuery("""
                    SELECT sender_key, COUNT(*) AS row_count
                    FROM TRANSACTIONS_2
                    WHERE (sender_key & :typeMask) = :allianceType
                      AND (receiver_key & :typeMask) = :allianceType
                      AND receiver_key IN (<receiverKeys>)
                      AND sender_key IN (<senderKeys>)
                    GROUP BY sender_key
                    """)
                    .bind("typeMask", TransactionEndpointKey.TYPE_MASK)
                    .bind("allianceType", ALLIANCE_TYPE)
                    .bindList("receiverKeys", receiverKeys)
                    .bindList("senderKeys", senderKeys)
                    .map((rs, ctx) -> Map.entry(
                            (int) (rs.getLong("sender_key") >> TransactionEndpointKey.TYPE_BITS),
                            rs.getLong("row_count")))
                    .list());
            Map<Integer, Long> counts = new LinkedHashMap<>();
            for (Map.Entry<Integer, Long> row : rows) {
                counts.put(row.getKey(), row.getValue());
            }
            return counts;
        }
    }

    static final class ActualGuildStore extends DBMainV3 {
        private final long guildId;

        ActualGuildStore(Path databaseDirectory, long guildId) throws SQLException, ClassNotFoundException {
            super(databaseDirectory.resolve("guilds").toFile(), Long.toString(guildId), true, false, 0, 0, 5);
            this.guildId = guildId;
            createTables();
        }

        @Override
        public void createTables() {
            // The benchmark reads from an on-disk snapshot of the live guild database.
        }

        long countTransactionsByEndpoint(long endpointId, int endpointType) {
            long endpointKey = TransactionEndpointKey.encode(endpointId, endpointType);
            String query = GuildDB.buildInternalTransactionLookupQuery("sender_key = ? OR receiver_key = ?", START, END);
            try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
                stmt.setLong(1, endpointKey);
                stmt.setLong(2, endpointKey);
                try (ResultSet rs = stmt.executeQuery()) {
                    long count = 0L;
                    while (rs.next()) {
                        count++;
                    }
                    return count;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed counting internal transactions for guild " + guildId, e);
            }
        }

        Map<Integer, Long> countAllianceTransactions(List<Integer> allianceIds) {
            if (allianceIds.isEmpty()) {
                return Map.of();
            }
            List<Long> candidateKeys = allianceKeys(allianceIds);
            Set<Long> candidateKeySet = new HashSet<>(candidateKeys);
            List<Map.Entry<Long, Long>> rows = jdbi().withHandle(handle -> handle.createQuery("""
                    SELECT sender_key, receiver_key
                    FROM INTERNAL_TRANSACTIONS2
                    WHERE sender_key IN (<candidateKeys>)
                       OR receiver_key IN (<candidateKeys>)
                    """)
                    .bindList("candidateKeys", candidateKeys)
                    .map((rs, ctx) -> Map.entry(rs.getLong("sender_key"), rs.getLong("receiver_key")))
                    .list());
            Map<Integer, Long> counts = new LinkedHashMap<>();
            for (Map.Entry<Long, Long> row : rows) {
                long matchedKey = candidateKeySet.contains(row.getKey()) ? row.getKey() : row.getValue();
                counts.merge((int) (matchedKey >> TransactionEndpointKey.TYPE_BITS), 1L, Long::sum);
            }
            return counts;
        }
    }
}
