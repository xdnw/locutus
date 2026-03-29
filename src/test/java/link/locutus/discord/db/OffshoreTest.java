package link.locutus.discord.db;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.util.io.BitBuffer;
import link.locutus.discord.util.offshore.OffshoreInstance;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Xms1g", "-Xmx1g"})
public class OffshoreTest {
    @Benchmark
    public void queryRawBankPayloadRows(RawBankReadState state, Blackhole bh) {
        List<OffshoreBenchmarkSupport.StoredPayloadRow> rows = state.bankStore
                .loadAllianceTransactionRows(state.fixture.offshoreIds());
        bh.consume(rows.size());
        bh.consume(checksumStoredRows(rows));
    }

    @Benchmark
    public void decodeBankStoredPayloads(PayloadDecodeState state, Blackhole bh) {
        long checksum = 0L;
        for (OffshoreBenchmarkSupport.StoredPayloadRow row : state.rows) {
            Transaction2 tx = row.decode(state.buffer);
            checksum = mix(checksum, tx.tx_id);
            checksum = mix(checksum, tx.sender_id);
            checksum = mix(checksum, tx.receiver_id);
        }
        bh.consume(checksum);
    }

    @Benchmark
    public void filterBankTransactionsForGuild(FilterState state, Blackhole bh) {
        bh.consume(runFilter(state.workingTransactions, state.guildFilter));
    }

    @Benchmark
    public void filterBankTransactionsForAlliance(FilterState state, Blackhole bh) {
        bh.consume(runFilter(state.workingTransactions, state.primaryAllianceFilter));
    }

    @Benchmark
    public void totalGuildTransactions(TotalState state, Blackhole bh) {
        bh.consume(OffshoreInstance.getTotal(
                state.fixture.offshoreIds(),
                state.guildTransactions,
                state.fixture.guildId(),
                OffshoreBenchmarkSupport.GUILD_TYPE));
    }

    @Benchmark
    public void totalAllianceTransactionsSingle(TotalState state, Blackhole bh) {
        bh.consume(OffshoreInstance.getTotal(
                state.fixture.offshoreIds(),
                state.primaryAllianceTransactions,
                state.fixture.primaryAllianceId(),
                OffshoreBenchmarkSupport.ALLIANCE_TYPE));
    }

    @Benchmark
    public void totalAllianceTransactionsMulti(TotalState state, Blackhole bh) {
        bh.consume(OffshoreInstance.getTotal(
                state.fixture.offshoreIds(),
                state.multiAllianceTransactions,
                state.fixture.trackedAllianceIdsLong(),
                OffshoreBenchmarkSupport.ALLIANCE_TYPE));
    }

    @Benchmark
    public void loadBankTransactionsUnfilteredCold(BankColdLoadState state, Blackhole bh) {
        List<Transaction2> loaded = state.bankDb.getAllianceTransactions(state.fixture.offshoreIds(), true, null);
        bh.consume(loaded.size());
        bh.consume(checksumTransactions(loaded));
    }

    @Benchmark
    public void loadBankTransactionsUnfilteredHot(BankHotLoadState state, Blackhole bh) {
        List<Transaction2> loaded = state.bankDb.getAllianceTransactions(state.fixture.offshoreIds(), true, null);
        bh.consume(loaded.size());
        bh.consume(checksumTransactions(loaded));
    }

    @Benchmark
    public void loadBankTransactionsForGuildCold(BankColdLoadState state, Blackhole bh) {
        List<Transaction2> loaded = state.bankDb.getAllianceTransactions(
                state.fixture.offshoreIds(),
                true,
                state.guildFilter);
        bh.consume(loaded.size());
        bh.consume(checksumTransactions(loaded));
    }

    @Benchmark
    public void loadBankTransactionsForGuildHot(BankHotLoadState state, Blackhole bh) {
        List<Transaction2> loaded = state.bankDb.getAllianceTransactions(
                state.fixture.offshoreIds(),
                true,
                state.guildFilter);
        bh.consume(loaded.size());
        bh.consume(checksumTransactions(loaded));
    }

    @Benchmark
    public void loadBankTransactionsForAllianceCold(BankColdLoadState state, Blackhole bh) {
        List<Transaction2> loaded = state.bankDb.getAllianceTransactions(
                state.fixture.offshoreIds(),
                true,
                state.primaryAllianceFilter);
        bh.consume(loaded.size());
        bh.consume(checksumTransactions(loaded));
    }

    @Benchmark
    public void loadBankTransactionsForAllianceHot(BankHotLoadState state, Blackhole bh) {
        List<Transaction2> loaded = state.bankDb.getAllianceTransactions(
                state.fixture.offshoreIds(),
                true,
                state.primaryAllianceFilter);
        bh.consume(loaded.size());
        bh.consume(checksumTransactions(loaded));
    }

    @Benchmark
    public void loadGuildOffsetTransactions(InternalLoadState state, Blackhole bh) {
        List<Transaction2> loaded = state.guildDb.getDepositOffsetTransactionsForGuild(
                state.fixture.guildId(),
                OffshoreBenchmarkSupport.START,
                OffshoreBenchmarkSupport.END);
        bh.consume(loaded.size());
        bh.consume(checksumTransactions(loaded));
    }

    @Benchmark
    public void loadAllianceOffsetTransactions(InternalLoadState state, Blackhole bh) {
        List<Transaction2> loaded = state.guildDb.getDepositOffsetTransactionsForAlliance(
                state.fixture.primaryAllianceId(),
                OffshoreBenchmarkSupport.START,
                OffshoreBenchmarkSupport.END);
        bh.consume(loaded.size());
        bh.consume(checksumTransactions(loaded));
    }

    @Benchmark
    public void pipelineGuildBalanceCold(PipelineColdState state, Blackhole bh) {
        List<Transaction2> toProcess = OffshoreBenchmarkSupport.loadGuildTransactions(
                state.bankDb,
                state.guildDb,
                state.fixture);
        bh.consume(ResourceType.resourcesToMap(OffshoreInstance.getTotal(
                state.fixture.offshoreIds(),
                toProcess,
                state.fixture.guildId(),
                OffshoreBenchmarkSupport.GUILD_TYPE)));
    }

    @Benchmark
    public void pipelineGuildBalanceHot(PipelineHotState state, Blackhole bh) {
        List<Transaction2> toProcess = OffshoreBenchmarkSupport.loadGuildTransactions(
                state.bankDb,
                state.guildDb,
                state.fixture);
        bh.consume(ResourceType.resourcesToMap(OffshoreInstance.getTotal(
                state.fixture.offshoreIds(),
                toProcess,
                state.fixture.guildId(),
                OffshoreBenchmarkSupport.GUILD_TYPE)));
    }

    @Benchmark
    public void pipelineAllianceBalanceSingleCold(PipelineColdState state, Blackhole bh) {
        int allianceId = state.fixture.primaryAllianceId();
        List<Transaction2> toProcess = OffshoreBenchmarkSupport.loadAllianceTransactions(
                state.bankDb,
                state.guildDb,
                state.fixture,
                allianceId);
        bh.consume(ResourceType.resourcesToMap(OffshoreInstance.getTotal(
                state.fixture.offshoreIds(),
                toProcess,
                allianceId,
                OffshoreBenchmarkSupport.ALLIANCE_TYPE)));
    }

    @Benchmark
    public void pipelineAllianceBalanceSingleHot(PipelineHotState state, Blackhole bh) {
        int allianceId = state.fixture.primaryAllianceId();
        List<Transaction2> toProcess = OffshoreBenchmarkSupport.loadAllianceTransactions(
                state.bankDb,
                state.guildDb,
                state.fixture,
                allianceId);
        bh.consume(ResourceType.resourcesToMap(OffshoreInstance.getTotal(
                state.fixture.offshoreIds(),
                toProcess,
                allianceId,
                OffshoreBenchmarkSupport.ALLIANCE_TYPE)));
    }

    @Benchmark
    public void pipelineAllianceBalanceMultiCold(PipelineColdState state, Blackhole bh) {
        List<Transaction2> toProcess = OffshoreBenchmarkSupport.loadAllianceTransactions(
                state.bankDb,
                state.guildDb,
                state.fixture,
                state.fixture.trackedAllianceIds());
        bh.consume(ResourceType.resourcesToMap(OffshoreInstance.getTotal(
                state.fixture.offshoreIds(),
                toProcess,
                state.fixture.trackedAllianceIdsLong(),
                OffshoreBenchmarkSupport.ALLIANCE_TYPE)));
    }

    @Benchmark
    public void pipelineAllianceBalanceMultiHot(PipelineHotState state, Blackhole bh) {
        List<Transaction2> toProcess = OffshoreBenchmarkSupport.loadAllianceTransactions(
                state.bankDb,
                state.guildDb,
                state.fixture,
                state.fixture.trackedAllianceIds());
        bh.consume(ResourceType.resourcesToMap(OffshoreInstance.getTotal(
                state.fixture.offshoreIds(),
                toProcess,
                state.fixture.trackedAllianceIdsLong(),
                OffshoreBenchmarkSupport.ALLIANCE_TYPE)));
    }

    public static void main(String[] args) throws RunnerException, IOException {
        OptionsBuilder options = new OptionsBuilder();
        options.include("^" + Pattern.quote(OffshoreTest.class.getName()) + ".*");
        options.shouldFailOnError(true);

        applyIntegerOption(System.getProperty("offshoreJmhWarmupIterations"), options::warmupIterations);
        applyIntegerOption(System.getProperty("offshoreJmhMeasurementIterations"), options::measurementIterations);
        applyIntegerOption(System.getProperty("offshoreJmhForks"), options::forks);

        String resultFile = System.getProperty("offshoreJmhResultFile");
        if (resultFile != null && !resultFile.isBlank()) {
            Path output = Path.of(resultFile).toAbsolutePath();
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            options.result(output.toString());
        }

        String resultFormat = System.getProperty("offshoreJmhResultFormat");
        if (resultFormat != null && !resultFormat.isBlank()) {
            options.resultFormat(ResultFormatType.valueOf(resultFormat.toUpperCase(Locale.ROOT)));
        }

        new Runner(options.build()).run();
    }

    private static void applyIntegerOption(String value, java.util.function.IntConsumer consumer) {
        if (value == null || value.isBlank()) {
            return;
        }
        consumer.accept(Integer.parseInt(value));
    }

    private static long runFilter(List<Transaction2> transactions, Predicate<Transaction2> filter) {
        long checksum = 0L;
        int matches = 0;
        for (Transaction2 tx : transactions) {
            if (filter.test(tx)) {
                matches++;
                checksum = mix(checksum, tx.tx_id);
                checksum = mix(checksum, tx.sender_id);
                checksum = mix(checksum, tx.receiver_id);
            }
        }
        return mix(checksum, matches);
    }

    private static long checksumStoredRows(List<OffshoreBenchmarkSupport.StoredPayloadRow> rows) {
        long checksum = 0L;
        for (OffshoreBenchmarkSupport.StoredPayloadRow row : rows) {
            checksum = mix(checksum, row.txId());
            checksum = mix(checksum, row.senderKey());
            checksum = mix(checksum, row.receiverKey());
            checksum = mix(checksum, row.payload() == null ? 0L : row.payload().length);
        }
        return checksum;
    }

    private static long checksumTransactions(List<Transaction2> transactions) {
        long checksum = 0L;
        for (Transaction2 tx : transactions) {
            checksum = mix(checksum, tx.tx_id);
            checksum = mix(checksum, tx.sender_id);
            checksum = mix(checksum, tx.receiver_id);
            checksum = mix(checksum, Double.doubleToLongBits(tx.resources[ResourceType.MONEY.ordinal()]));
        }
        return checksum;
    }

    private static long mix(long current, long value) {
        long mixed = current ^ value;
        return mixed * 0x9E3779B97F4A7C15L;
    }

    @State(Scope.Thread)
    public static class RawBankReadState {
        private OffshoreBenchmarkSupport.BenchmarkFixture fixture;
        private OffshoreBenchmarkSupport.ActualBankStore bankStore;

        @Setup(Level.Trial)
        public void setUp() throws Exception {
            fixture = OffshoreBenchmarkSupport.fixture();
            bankStore = new OffshoreBenchmarkSupport.ActualBankStore(fixture.snapshotDirectory());
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            bankStore.close();
            OffshoreBenchmarkSupport.shutdownBenchmarkSchedulers();
        }
    }

    @State(Scope.Benchmark)
    public static class PayloadDecodeState {
        private OffshoreBenchmarkSupport.BenchmarkFixture fixture;
        private List<OffshoreBenchmarkSupport.StoredPayloadRow> rows;
        private BitBuffer buffer;

        @Setup(Level.Trial)
        public void setUp() {
            fixture = OffshoreBenchmarkSupport.fixture();
            rows = fixture.bankRows();
            buffer = Transaction2.createNoteBuffer();
        }
    }

    @State(Scope.Thread)
    public static class FilterState {
        private OffshoreBenchmarkSupport.BenchmarkFixture fixture;
        private Predicate<Transaction2> guildFilter;
        private Predicate<Transaction2> primaryAllianceFilter;
        private List<Transaction2> workingTransactions;

        @Setup(Level.Trial)
        public void setUpFilters() {
            fixture = OffshoreBenchmarkSupport.fixture();
            guildFilter = OffshoreInstance.getFilter(fixture.guildId(), OffshoreBenchmarkSupport.GUILD_TYPE);
            primaryAllianceFilter = OffshoreInstance.getFilter(
                    fixture.primaryAllianceId(),
                    OffshoreBenchmarkSupport.ALLIANCE_TYPE);
        }

        @Setup(Level.Invocation)
        public void setUpInvocation() {
            workingTransactions = OffshoreBenchmarkSupport.decodeRows(fixture.bankRows());
        }
    }

    @State(Scope.Benchmark)
    public static class TotalState {
        private OffshoreBenchmarkSupport.BenchmarkFixture fixture;
        private List<Transaction2> guildTransactions;
        private List<Transaction2> primaryAllianceTransactions;
        private List<Transaction2> multiAllianceTransactions;

        @Setup(Level.Trial)
        public void setUpTotals() throws Exception {
            fixture = OffshoreBenchmarkSupport.fixture();
            OffshoreBenchmarkSupport.configureDatabaseDirectory(fixture);

            try (BankDB bankDb = new BankDB(); GuildDB guildDb = new GuildDB(null, fixture.guildId())) {
                guildTransactions = OffshoreBenchmarkSupport.loadGuildTransactions(bankDb, guildDb, fixture);
            }
            try (BankDB bankDb = new BankDB(); GuildDB guildDb = new GuildDB(null, fixture.guildId())) {
                primaryAllianceTransactions = OffshoreBenchmarkSupport.loadAllianceTransactions(
                        bankDb,
                        guildDb,
                        fixture,
                        fixture.primaryAllianceId());
            }
            try (BankDB bankDb = new BankDB(); GuildDB guildDb = new GuildDB(null, fixture.guildId())) {
                multiAllianceTransactions = OffshoreBenchmarkSupport.loadAllianceTransactions(
                        bankDb,
                        guildDb,
                        fixture,
                        fixture.trackedAllianceIds());
            }
        }

        @TearDown(Level.Trial)
        public void tearDownTotals() {
            OffshoreBenchmarkSupport.shutdownBenchmarkSchedulers();
        }
    }

    public abstract static class AbstractBankLoadState {
        protected OffshoreBenchmarkSupport.BenchmarkFixture fixture;
        protected BankDB bankDb;
        protected Predicate<Transaction2> guildFilter;
        protected Predicate<Transaction2> primaryAllianceFilter;

        @Setup(Level.Trial)
        public void setUpBank() throws Exception {
            fixture = OffshoreBenchmarkSupport.fixture();
            OffshoreBenchmarkSupport.configureDatabaseDirectory(fixture);
            bankDb = new BankDB();
            guildFilter = OffshoreInstance.getFilter(fixture.guildId(), OffshoreBenchmarkSupport.GUILD_TYPE);
            primaryAllianceFilter = OffshoreInstance.getFilter(
                    fixture.primaryAllianceId(),
                    OffshoreBenchmarkSupport.ALLIANCE_TYPE);
        }

        @TearDown(Level.Trial)
        public void tearDownBank() {
            bankDb.close();
            OffshoreBenchmarkSupport.shutdownBenchmarkSchedulers();
        }
    }

    @State(Scope.Thread)
    public static class BankColdLoadState extends AbstractBankLoadState {
        @Setup(Level.Invocation)
        public void prepareInvocation() {
            OffshoreBenchmarkSupport.clearBankTransactionCache(bankDb);
        }
    }

    @State(Scope.Thread)
    public static class BankHotLoadState extends AbstractBankLoadState {
        @Setup(Level.Invocation)
        public void prepareInvocation() {
            OffshoreBenchmarkSupport.clearBankTransactionCache(bankDb);
            bankDb.getAllianceTransactions(fixture.offshoreIds(), true, null);
        }
    }

    @State(Scope.Thread)
    public static class InternalLoadState {
        private OffshoreBenchmarkSupport.BenchmarkFixture fixture;
        private GuildDB guildDb;

        @Setup(Level.Trial)
        public void setUpGuild() throws Exception {
            fixture = OffshoreBenchmarkSupport.fixture();
            OffshoreBenchmarkSupport.configureDatabaseDirectory(fixture);
            guildDb = new GuildDB(null, fixture.guildId());
        }

        @TearDown(Level.Trial)
        public void tearDownGuild() {
            guildDb.close();
            OffshoreBenchmarkSupport.shutdownBenchmarkSchedulers();
        }
    }

    public abstract static class AbstractPipelineState {
        protected OffshoreBenchmarkSupport.BenchmarkFixture fixture;
        protected BankDB bankDb;
        protected GuildDB guildDb;

        @Setup(Level.Trial)
        public void setUpPipeline() throws Exception {
            fixture = OffshoreBenchmarkSupport.fixture();
            OffshoreBenchmarkSupport.configureDatabaseDirectory(fixture);
            bankDb = new BankDB();
            guildDb = new GuildDB(null, fixture.guildId());
        }

        @TearDown(Level.Trial)
        public void tearDownPipeline() {
            bankDb.close();
            guildDb.close();
            OffshoreBenchmarkSupport.shutdownBenchmarkSchedulers();
        }
    }

    @State(Scope.Thread)
    public static class PipelineColdState extends AbstractPipelineState {
        @Setup(Level.Invocation)
        public void prepareInvocation() {
            OffshoreBenchmarkSupport.clearBankTransactionCache(bankDb);
        }
    }

    @State(Scope.Thread)
    public static class PipelineHotState extends AbstractPipelineState {
        @Setup(Level.Invocation)
        public void prepareInvocation() {
            OffshoreBenchmarkSupport.clearBankTransactionCache(bankDb);
            bankDb.getAllianceTransactions(fixture.offshoreIds(), true, null);
        }
    }
}
