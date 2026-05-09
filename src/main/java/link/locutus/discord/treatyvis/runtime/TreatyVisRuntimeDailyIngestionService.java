package link.locutus.discord.treatyvis.runtime;

import link.locutus.discord.Locutus;
import link.locutus.discord.web.jooby.CloudStorage;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class TreatyVisRuntimeDailyIngestionService {
    public static final long DEFAULT_RUN_INTERVAL_MINUTES = 60L;
    private static final long TREATY_RUNTIME_MAX_AGE_SECONDS = TimeUnit.MINUTES.toSeconds(5);

    private final TreatyVisRuntimeRepository repository;
    private final IoCallable<TreatyVisRuntimeBootstrapScoreStateService.ScoreBootstrapResult> scoreIngestor;
    private final IoCallable<TreatyVisRuntimeBootstrapFlagStateService.FlagBootstrapResult> flagIngestor;
    private final IoCallable<TreatyVisRuntimeTreatyCheckpointRefreshService.TreatyCheckpointRefreshResult> treatyCheckpointRefresher;
    private final IoCallable<TreatyHistoryService.BuiltTreatyArtifacts> artifactBuilder;
    private final Supplier<CloudStorage> cloudStorageSupplier;

    public TreatyVisRuntimeDailyIngestionService() {
        TreatyVisRuntimeBootstrapScoreStateService scoreService = new TreatyVisRuntimeBootstrapScoreStateService();
        TreatyVisRuntimeBootstrapFlagStateService flagService = new TreatyVisRuntimeBootstrapFlagStateService();
        TreatyVisRuntimeTreatyCheckpointRefreshService treatyCheckpointService = new TreatyVisRuntimeTreatyCheckpointRefreshService();
        TreatyHistoryService historyService = new TreatyHistoryService();
        this.repository = new TreatyVisRuntimeRepository(Locutus.imp().getNationDB());
        this.scoreIngestor = () -> scoreService.bootstrapCurrentScores(true);
        this.flagIngestor = flagService::bootstrapCurrentFlags;
        this.treatyCheckpointRefresher = treatyCheckpointService::refreshTreatyCheckpoint;
        this.artifactBuilder = historyService::buildRuntimeArtifacts;
        this.cloudStorageSupplier = Locutus.imp()::getCloudStorage;
    }

    TreatyVisRuntimeDailyIngestionService(
            TreatyVisRuntimeRepository repository,
            IoCallable<TreatyVisRuntimeBootstrapScoreStateService.ScoreBootstrapResult> scoreIngestor,
            IoCallable<TreatyVisRuntimeBootstrapFlagStateService.FlagBootstrapResult> flagIngestor,
            IoCallable<TreatyVisRuntimeTreatyCheckpointRefreshService.TreatyCheckpointRefreshResult> treatyCheckpointRefresher,
            IoCallable<TreatyHistoryService.BuiltTreatyArtifacts> artifactBuilder,
            Supplier<CloudStorage> cloudStorageSupplier
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scoreIngestor = Objects.requireNonNull(scoreIngestor, "scoreIngestor");
        this.flagIngestor = Objects.requireNonNull(flagIngestor, "flagIngestor");
        this.treatyCheckpointRefresher = Objects.requireNonNull(treatyCheckpointRefresher, "treatyCheckpointRefresher");
        this.artifactBuilder = Objects.requireNonNull(artifactBuilder, "artifactBuilder");
        this.cloudStorageSupplier = Objects.requireNonNull(cloudStorageSupplier, "cloudStorageSupplier");
    }

    public DailyIngestionResult runDailyIngestion() throws IOException {
        TreatyVisRuntimeRepository.RuntimeBootstrapState bootstrapState = repository.loadBootstrapState();
        List<String> gatingIssues = gatingIssues(bootstrapState);
        if (!gatingIssues.isEmpty()) {
            return DailyIngestionResult.skipped(gatingIssues);
        }

        TreatyVisRuntimeBootstrapScoreStateService.ScoreBootstrapResult scoreResult = scoreIngestor.call();
        TreatyVisRuntimeBootstrapFlagStateService.FlagBootstrapResult flagResult = flagIngestor.call();
        TreatyVisRuntimeTreatyCheckpointRefreshService.TreatyCheckpointRefreshResult treatyResult = treatyCheckpointRefresher.call();
        TreatyHistoryService.BuiltTreatyArtifacts builtArtifacts = artifactBuilder.call();
        PublishedTreatyArtifacts publishedArtifacts = publishArtifacts(builtArtifacts);
        repository.setLastPayloadSha256(builtArtifacts.historySha256());
        return DailyIngestionResult.completed(
                treatyResult,
                scoreResult,
                flagResult,
                publishedArtifacts
        );
    }

        private PublishedTreatyArtifacts publishArtifacts(TreatyHistoryService.BuiltTreatyArtifacts builtArtifacts) {
        CloudStorage cloudStorage = cloudStorageSupplier.get();
        String historyUrl = null;
        String atlasUrl = null;
        if (cloudStorage != null) {
            cloudStorage.putObject(
                TreatyHistoryService.TREATY_HISTORY_OBJECT_KEY,
                builtArtifacts.historyBytes(),
                TREATY_RUNTIME_MAX_AGE_SECONDS
            );
            cloudStorage.putObject(
                TreatyHistoryService.TREATY_ATLAS_OBJECT_KEY,
                builtArtifacts.atlasBytes(),
                TREATY_RUNTIME_MAX_AGE_SECONDS
            );
            historyUrl = cloudStorage.getLink(TreatyHistoryService.TREATY_HISTORY_OBJECT_KEY);
            atlasUrl = cloudStorage.getLink(TreatyHistoryService.TREATY_ATLAS_OBJECT_KEY);
        }
        return new PublishedTreatyArtifacts(
            TreatyHistoryService.TREATY_HISTORY_OBJECT_KEY,
            historyUrl,
            TreatyHistoryService.TREATY_ATLAS_OBJECT_KEY,
            atlasUrl,
            builtArtifacts.historyBytes().length,
            builtArtifacts.atlasBytes().length
        );
        }

    private List<String> gatingIssues(TreatyVisRuntimeRepository.RuntimeBootstrapState bootstrapState) {
        java.util.ArrayList<String> issues = new java.util.ArrayList<>();
        if (!bootstrapState.treatyImportComplete() || !bootstrapState.scoreImportComplete() || !bootstrapState.flagImportComplete()) {
            issues.add("Bootstrap import is incomplete.");
        }
        if (!bootstrapState.validationComplete()) {
            issues.add("Bootstrap validation is incomplete.");
        }
        if (repository.loadLatestTreatyCheckpoint() == null) {
            issues.add("No treaty checkpoint is available for runtime ingestion.");
        }
        return List.copyOf(issues);
    }

    @FunctionalInterface
    interface IoCallable<T> {
        T call() throws IOException;
    }

    public record DailyIngestionResult(
            boolean executed,
            List<String> gatingIssues,
            TreatyVisRuntimeTreatyCheckpointRefreshService.TreatyCheckpointRefreshResult treatyCheckpointResult,
            TreatyVisRuntimeBootstrapScoreStateService.ScoreBootstrapResult scoreResult,
            TreatyVisRuntimeBootstrapFlagStateService.FlagBootstrapResult flagResult,
            PublishedTreatyArtifacts publishedArtifacts
    ) {
        static DailyIngestionResult skipped(List<String> gatingIssues) {
            return new DailyIngestionResult(false, List.copyOf(gatingIssues), null, null, null, null);
        }

        static DailyIngestionResult completed(
                TreatyVisRuntimeTreatyCheckpointRefreshService.TreatyCheckpointRefreshResult treatyCheckpointResult,
                TreatyVisRuntimeBootstrapScoreStateService.ScoreBootstrapResult scoreResult,
                TreatyVisRuntimeBootstrapFlagStateService.FlagBootstrapResult flagResult,
                PublishedTreatyArtifacts publishedArtifacts
        ) {
            return new DailyIngestionResult(true, List.of(), treatyCheckpointResult, scoreResult, flagResult, publishedArtifacts);
        }
    }

    public record PublishedTreatyArtifacts(
            String historyKey,
            String historyUrl,
            String atlasKey,
            String atlasUrl,
            int historyBytes,
            int atlasBytes
    ) {
    }
}
