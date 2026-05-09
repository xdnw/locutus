package link.locutus.discord.treatyvis.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.util.TimeUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public final class TreatyVisRuntimeValidationService {
    private static final String CHECKPOINT_PROVENANCE = "treaty-vis data/incremental_state.json event_store.active + event_store.max_timestamp";

    private final TreatyVisRuntimeBootstrapService bootstrapService;
    private final TreatyVisRuntimeRepository repository;
    private final TreatyVisRuntimeLegacyTreatyImportService treatyImportService;
    private final TreatyVisRuntimeLegacyScoreImportService scoreImportService;
    private final TreatyVisRuntimeLegacyFlagImportService flagImportService;
    private final TreatyVisRuntimeImportedSnapshotService importedSnapshotService;
    private final Path validateReportPath;
    private final Path runtimeFlagCacheRoot;
    private final Supplier<List<TreatyVisRuntimeTreatyCheckpointRefreshService.CurrentTreatyRow>> currentTreatySupplier;
    private final Supplier<List<LiveAllianceScoreRow>> liveScoreSupplier;
    private final Supplier<List<LiveAllianceFlagRow>> liveFlagSupplier;
    private final IntFunction<String> allianceNameResolver;
    private final ObjectMapper json;

    public TreatyVisRuntimeValidationService() {
        this(
                new TreatyVisRuntimeBootstrapService(),
                new TreatyVisRuntimeRepository(Locutus.imp().getNationDB()),
                new TreatyVisRuntimeLegacyTreatyImportService(),
                new TreatyVisRuntimeLegacyScoreImportService(),
                new TreatyVisRuntimeLegacyFlagImportService(),
                new TreatyVisRuntimeImportedSnapshotService(new TreatyVisRuntimeRepository(Locutus.imp().getNationDB())),
                TreatyVisRuntimeBootstrapService.DEFAULT_VALIDATE_REPORT_PATH,
                Path.of("data", "flag_cache"),
                TreatyVisRuntimeValidationService::loadCurrentTreatyTruth,
                TreatyVisRuntimeValidationService::loadCurrentLiveScores,
                TreatyVisRuntimeValidationService::loadCurrentLiveFlags,
                Locutus.imp().getWarDb().getAllianceNameHistory()::getAllianceName,
                TreatyVisRuntimeSerializers.JSON
        );
    }

    TreatyVisRuntimeValidationService(
            TreatyVisRuntimeBootstrapService bootstrapService,
            TreatyVisRuntimeRepository repository,
            TreatyVisRuntimeLegacyTreatyImportService treatyImportService,
            TreatyVisRuntimeLegacyScoreImportService scoreImportService,
            TreatyVisRuntimeLegacyFlagImportService flagImportService,
            TreatyVisRuntimeImportedSnapshotService importedSnapshotService,
            Path validateReportPath,
            Path runtimeFlagCacheRoot,
            Supplier<List<TreatyVisRuntimeTreatyCheckpointRefreshService.CurrentTreatyRow>> currentTreatySupplier,
            Supplier<List<LiveAllianceScoreRow>> liveScoreSupplier,
            Supplier<List<LiveAllianceFlagRow>> liveFlagSupplier,
                IntFunction<String> allianceNameResolver,
            ObjectMapper json
    ) {
        this.bootstrapService = Objects.requireNonNull(bootstrapService, "bootstrapService");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.treatyImportService = Objects.requireNonNull(treatyImportService, "treatyImportService");
        this.scoreImportService = Objects.requireNonNull(scoreImportService, "scoreImportService");
        this.flagImportService = Objects.requireNonNull(flagImportService, "flagImportService");
        this.importedSnapshotService = Objects.requireNonNull(importedSnapshotService, "importedSnapshotService");
        this.validateReportPath = validateReportPath.toAbsolutePath().normalize();
        this.runtimeFlagCacheRoot = runtimeFlagCacheRoot.toAbsolutePath().normalize();
        this.currentTreatySupplier = Objects.requireNonNull(currentTreatySupplier, "currentTreatySupplier");
        this.liveScoreSupplier = Objects.requireNonNull(liveScoreSupplier, "liveScoreSupplier");
        this.liveFlagSupplier = Objects.requireNonNull(liveFlagSupplier, "liveFlagSupplier");
        this.allianceNameResolver = Objects.requireNonNull(allianceNameResolver, "allianceNameResolver");
        this.json = Objects.requireNonNull(json, "json");
    }

    public RuntimeValidationResult validateImportedRuntime() throws IOException {
        TreatyVisRuntimeBootstrapService.ValidationResult staged = bootstrapService.validateImportedLegacyArtifacts();
        List<String> issues = new ArrayList<>();
        if (!staged.valid()) {
            issues.addAll(staged.missingRequiredEntries());
        }

        TreatyVisRuntimeRepository.RuntimeBootstrapState bootstrapState = repository.loadBootstrapState();
        TreatyVisRuntimeRepository.TreatyCheckpoint checkpoint = repository.loadLatestTreatyCheckpoint();

        TreatyMetrics treatyMetrics = validateTreaties(bootstrapState, checkpoint, issues);
        ScoreMetrics scoreMetrics = validateScores(bootstrapState, issues);
        FlagMetrics flagMetrics = validateFlags(bootstrapState, issues);
        TreatyReplayMetrics treatyReplayMetrics = validateTreatyReplay(checkpoint, issues);

        boolean importedSnapshotAvailable = false;
        TreatyVisRuntimeImportedSnapshotService.ImportedSnapshot importedSnapshot = null;
        try {
            importedSnapshot = importedSnapshotService.buildImportedSnapshot(allianceNameResolver);
            importedSnapshotAvailable = importedSnapshot != null;
            if (!importedSnapshotAvailable) {
                issues.add("Imported runtime snapshot could not be materialized from imported tables.");
            }
        } catch (IOException ex) {
            issues.add("Imported runtime snapshot materialization failed: " + ex.getMessage());
        }
        PayloadReplayMetrics payloadReplayMetrics = validatePayloadReplay(importedSnapshot, issues);
        BootstrapLiveHandoffMetrics bootstrapLiveHandoffMetrics = validateBootstrapLiveHandoff(issues);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("createdAt", Instant.now().toString());
        report.put("valid", issues.isEmpty());
        report.put("issues", List.copyOf(issues));
        report.put("checkpointProvenance", CHECKPOINT_PROVENANCE);
        report.put("staged", stagedReport(staged));
        report.put("runtime", runtimeReport(bootstrapState, checkpoint, treatyMetrics, scoreMetrics, flagMetrics, treatyReplayMetrics, payloadReplayMetrics, bootstrapLiveHandoffMetrics, importedSnapshotAvailable));

        if (validateReportPath.getParent() != null) {
            Files.createDirectories(validateReportPath.getParent());
        }
        json.writerWithDefaultPrettyPrinter().writeValue(validateReportPath.toFile(), report);
        repository.setValidationComplete(issues.isEmpty());
        return new RuntimeValidationResult(issues.isEmpty(), validateReportPath, List.copyOf(issues));
    }

    private TreatyMetrics validateTreaties(
            TreatyVisRuntimeRepository.RuntimeBootstrapState bootstrapState,
            TreatyVisRuntimeRepository.TreatyCheckpoint checkpoint,
            List<String> issues
    ) {
        try {
            List<TreatyVisRuntimeRepository.UnifiedTreatyChangeRow> sourceRows = treatyImportService.loadUnifiedTreatyRows();
            int sourceCount = sourceRows.size();
            int importedCount = repository.countUnifiedTreatyChanges();
            Integer minDay = sourceRows.isEmpty() ? null : sourceRows.stream().mapToInt(row -> epochDay(row.timestamp())).min().orElseThrow();
            Integer maxDay = sourceRows.isEmpty() ? null : sourceRows.stream().mapToInt(row -> epochDay(row.timestamp())).max().orElseThrow();
            if (sourceCount != importedCount) {
                issues.add("Imported treaty row count does not match staged source row count.");
            }
            if (!Objects.equals(minDay, bootstrapState.importedTreatyMinDay()) || !Objects.equals(maxDay, bootstrapState.importedTreatyMaxDay())) {
                issues.add("Imported treaty day range does not match bootstrap state.");
            }
            if (checkpoint == null) {
                issues.add("Missing imported treaty checkpoint.");
            }
            return new TreatyMetrics(sourceCount, importedCount, minDay, maxDay, checkpoint == null ? null : checkpoint.day());
        } catch (IOException ex) {
            issues.add("Treaty validation failed: " + ex.getMessage());
            return new TreatyMetrics(null, null, null, null, checkpoint == null ? null : checkpoint.day());
        }
    }

    private ScoreMetrics validateScores(TreatyVisRuntimeRepository.RuntimeBootstrapState bootstrapState, List<String> issues) {
        try {
            Map<Integer, TreatyVisRuntimeLegacyScoreImportService.ImportedScoreDay> sourceDays = scoreImportService.loadHistoricalScoreDays();
            Map<Integer, byte[]> importedRows = repository.loadTopNScoreRows();
            int sourceDayCount = sourceDays.size();
            int importedDayCount = importedRows.size();
            Integer sourceMinDay = sourceDays.isEmpty() ? null : sourceDays.keySet().stream().min(Integer::compareTo).orElseThrow();
            Integer sourceMaxDay = sourceDays.isEmpty() ? null : sourceDays.keySet().stream().max(Integer::compareTo).orElseThrow();
            if (importedDayCount < sourceDayCount) {
                issues.add("Imported score day count is smaller than staged source day count.");
            }
            if (!Objects.equals(sourceMinDay, bootstrapState.importedScoreMinDay()) || !Objects.equals(sourceMaxDay, bootstrapState.importedScoreMaxDay())) {
                issues.add("Imported score day range does not match bootstrap state.");
            }

            List<TreatyVisRuntimeLegacyScoreImportService.ScoreRow> helperTopRows = repository.loadLastAllianceScores().entrySet().stream()
                    .map(entry -> new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(entry.getKey(), entry.getValue()))
                    .filter(row -> row.quantizedScore() > 0)
                    .sorted(java.util.Comparator.comparingInt(TreatyVisRuntimeLegacyScoreImportService.ScoreRow::quantizedScore).reversed().thenComparingInt(TreatyVisRuntimeLegacyScoreImportService.ScoreRow::allianceId))
                    .limit(TreatyHistoryRuntimeConfig.TOP_N_ALLIANCES)
                    .toList();
            byte[] expectedLatestPayload = TreatyVisRuntimeScoreRowCodec.encode(helperTopRows);
            byte[] actualLatestPayload = importedRows.isEmpty() ? null : importedRows.get(importedRows.keySet().stream().max(Integer::compareTo).orElseThrow());
            boolean helperMatchesLatest = java.util.Arrays.equals(expectedLatestPayload, actualLatestPayload);
            if (!helperMatchesLatest) {
                issues.add("LAST_ALLIANCE_SCORES does not match the latest TOP_N_SCORE_BY_DAY row.");
            }
            return new ScoreMetrics(sourceDayCount, importedDayCount, sourceMinDay, sourceMaxDay, helperMatchesLatest);
        } catch (IOException ex) {
            issues.add("Score validation failed: " + ex.getMessage());
            return new ScoreMetrics(null, null, null, null, false);
        }
    }

    private FlagMetrics validateFlags(TreatyVisRuntimeRepository.RuntimeBootstrapState bootstrapState, List<String> issues) {
        try {
            TreatyVisRuntimeLegacyFlagImportService.FlagValidationData source = flagImportService.loadHistoricalFlagValidationData();
            List<TreatyVisRuntimeRepository.FlagChangeRow> importedRows = repository.loadFlagChangeRows();
            if (importedRows.size() < source.sourceEventCount()) {
                issues.add("Imported flag change row count is smaller than staged source event count.");
            }
            if (!Objects.equals(source.minDay(), bootstrapState.importedFlagMinDay()) || !Objects.equals(source.maxDay(), bootstrapState.importedFlagMaxDay())) {
                issues.add("Imported flag day range does not match bootstrap state.");
            }

            boolean rawCacheHashesValid = true;
            for (com.google.common.hash.HashCode rawHash : source.rawHashByUrl().values()) {
                Path rawCachePath = runtimeFlagCacheRoot.resolve(rawHash.toString());
                if (!Files.isRegularFile(rawCachePath)) {
                    rawCacheHashesValid = false;
                    issues.add("Runtime raw flag cache is missing imported hash " + rawHash + ".");
                    continue;
                }
                com.google.common.hash.HashCode actualHash = TreatyVisRuntimeFlagAssetUtil.sha256(Files.readAllBytes(rawCachePath));
                if (!rawHash.equals(actualHash)) {
                    rawCacheHashesValid = false;
                    issues.add("Runtime raw flag cache hash mismatch for " + rawHash + ".");
                }
            }

            Map<Integer, byte[]> latestByAlliance = new LinkedHashMap<>();
            for (TreatyVisRuntimeRepository.FlagChangeRow row : importedRows) {
                latestByAlliance.put(row.allianceId(), row.flagHash());
            }
            for (Map.Entry<Integer, byte[]> entry : source.latestHashByAlliance().entrySet()) {
                latestByAlliance.putIfAbsent(entry.getKey(), entry.getValue());
            }
            Map<Integer, TreatyVisRuntimeRepository.LastFlagUrlRow> helperRows = repository.loadLastFlagUrlRows();
            boolean helperMatchesLatest = true;
            for (Map.Entry<Integer, TreatyVisRuntimeRepository.LastFlagUrlRow> entry : helperRows.entrySet()) {
                byte[] expectedHash = latestByAlliance.get(entry.getKey());
                byte[] actualHash = entry.getValue().flagHash();
                if (!java.util.Arrays.equals(expectedHash, actualHash)) {
                    helperMatchesLatest = false;
                    issues.add("LAST_FLAG_URLS hash does not match latest FLAG_CHANGES row for alliance " + entry.getKey() + ".");
                }
            }
            return new FlagMetrics(source.sourceEventCount(), importedRows.size(), source.minDay(), source.maxDay(), rawCacheHashesValid, helperMatchesLatest);
        } catch (IOException ex) {
            issues.add("Flag validation failed: " + ex.getMessage());
            return new FlagMetrics(null, null, null, null, false, false);
        }
    }

    private TreatyReplayMetrics validateTreatyReplay(TreatyVisRuntimeRepository.TreatyCheckpoint checkpoint, List<String> issues) {
        if (checkpoint == null) {
            issues.add("Cannot validate treaty replay without an imported checkpoint.");
            return new TreatyReplayMetrics(false, 0, 0);
        }

        TreatyVisRuntimeTreatyCheckpointRefreshService.TreatyCheckpointComparison comparison =
                new TreatyVisRuntimeTreatyCheckpointRefreshService(
                        repository,
                        currentTreatySupplier,
                        System::currentTimeMillis,
                TreatyVisRuntimeTreatyCheckpointRefreshService.DEFAULT_CHECKPOINT_STRIDE_DAYS,
                (timestamp, action, treatyType, fromAllianceId, toAllianceId, turnsRemaining) -> {
                }
                ).compareCheckpointToCurrentTruth(checkpoint);
        if (!comparison.matchesCurrentTruth()) {
            issues.add("Replayed treaty state from the imported checkpoint does not match current treaty truth.");
        }
        return new TreatyReplayMetrics(
                comparison.matchesCurrentTruth(),
                comparison.replayedActiveCount(),
                comparison.currentActiveCount()
        );
    }

    private PayloadReplayMetrics validatePayloadReplay(
            TreatyVisRuntimeImportedSnapshotService.ImportedSnapshot importedSnapshot,
            List<String> issues
    ) {
        if (importedSnapshot == null) {
            return new PayloadReplayMetrics(false, 0);
        }

        TreatyVisRuntimeInput input = importedSnapshot.input();
        TreatyVisRuntimePayload payload = new TreatyVisRuntimeBuilder().build(input);
        TreeSet<Integer> sampledDays = new TreeSet<>();
        sampledDays.add(0);
        sampledDays.addAll(payload.treatyChanges().days());
        sampledDays.addAll(payload.flagChanges().days());
        sampledDays.addAll(payload.scoreSnapshots().days());

        for (int sampleDay : sampledDays) {
            PayloadReplayState expected = replayInputState(input, sampleDay);
            PayloadReplayState actual = replayPayloadState(payload, sampleDay);
            if (!expected.equals(actual)) {
                issues.add("Unified payload replay does not match imported runtime state at day offset " + sampleDay + ".");
                return new PayloadReplayMetrics(false, sampledDays.size());
            }
        }
        return new PayloadReplayMetrics(true, sampledDays.size());
    }

    private BootstrapLiveHandoffMetrics validateBootstrapLiveHandoff(List<String> issues) {
        Map<Integer, Integer> expectedScores = new LinkedHashMap<>();
        for (LiveAllianceScoreRow score : liveScoreSupplier.get()) {
            expectedScores.put(score.allianceId(), score.quantizedScore());
        }
        Map<Integer, Integer> actualScores = repository.loadLastAllianceScores();
        boolean scoreMatches = actualScores.equals(expectedScores);

        Map<Integer, String> expectedFlags = new LinkedHashMap<>();
        for (LiveAllianceFlagRow flag : liveFlagSupplier.get()) {
            expectedFlags.put(flag.allianceId(), normalizeFlagUrl(flag.flagUrl()));
        }
        Map<Integer, TreatyVisRuntimeRepository.LastFlagUrlRow> actualFlagRows = repository.loadLastFlagUrlRows();
        boolean flagMatches = true;
        for (Map.Entry<Integer, String> entry : expectedFlags.entrySet()) {
            TreatyVisRuntimeRepository.LastFlagUrlRow actualRow = actualFlagRows.get(entry.getKey());
            String actualUrl = actualRow == null ? "" : normalizeFlagUrl(actualRow.flagUrl());
            if (!Objects.equals(entry.getValue(), actualUrl)) {
                flagMatches = false;
                break;
            }
        }
        if (!flagMatches) {
            issues.add("LAST_FLAG_URLS does not match the captured bootstrap live flag snapshot.");
        }

        return new BootstrapLiveHandoffMetrics(scoreMatches, flagMatches, expectedScores.size(), expectedFlags.size());
    }
    private static PayloadReplayState replayInputState(TreatyVisRuntimeInput input, int sampleDay) {
        Set<PayloadTreatyKey> activeTreaties = new LinkedHashSet<>();
        for (TreatyVisRuntimeInput.TreatyEdge edge : input.activeTreaties()) {
            activeTreaties.add(PayloadTreatyKey.fromEdge(edge));
        }
        List<TreatyVisRuntimeInput.TreatyChange> treatyChanges = input.treatyChanges().stream()
                .sorted(Comparator.comparingInt(TreatyVisRuntimeInput.TreatyChange::day))
                .toList();
        for (TreatyVisRuntimeInput.TreatyChange change : treatyChanges) {
            if (change.day() > sampleDay || change.edge() == null) {
                continue;
            }
            PayloadTreatyKey key = PayloadTreatyKey.fromEdge(change.edge());
            if (change.actionCode() == 1 || change.actionCode() == 2) {
                activeTreaties.add(key);
            } else if (change.actionCode() == 3 || change.actionCode() == 4) {
                activeTreaties.remove(key);
            }
        }

        Map<Integer, Integer> flagsByAlliance = new LinkedHashMap<>();
        for (TreatyVisRuntimeInput.AllianceFlag flag : input.initialFlags()) {
            if (flag.flagIndex() > 0) {
                flagsByAlliance.put(flag.allianceId(), flag.flagIndex());
            }
        }
        List<TreatyVisRuntimeInput.FlagChange> flagChanges = input.flagChanges().stream()
                .sorted(Comparator.comparingInt(TreatyVisRuntimeInput.FlagChange::day))
                .toList();
        for (TreatyVisRuntimeInput.FlagChange change : flagChanges) {
            if (change.day() > sampleDay) {
                continue;
            }
            if (change.flagIndex() <= 0) {
                flagsByAlliance.remove(change.allianceId());
            } else {
                flagsByAlliance.put(change.allianceId(), change.flagIndex());
            }
        }

        Map<Integer, Integer> scoresByAlliance = new LinkedHashMap<>();
        for (TreatyVisRuntimeInput.AllianceScore score : input.initialScores()) {
            scoresByAlliance.put(score.allianceId(), score.quantizedScore());
        }
        List<TreatyVisRuntimeInput.ScoreSnapshot> scoreSnapshots = input.scoreSnapshots().stream()
                .sorted(Comparator.comparingInt(TreatyVisRuntimeInput.ScoreSnapshot::day))
                .toList();
        for (TreatyVisRuntimeInput.ScoreSnapshot snapshot : scoreSnapshots) {
            if (snapshot.day() > sampleDay) {
                continue;
            }
            scoresByAlliance.clear();
            for (TreatyVisRuntimeInput.AllianceScore score : snapshot.scores()) {
                scoresByAlliance.put(score.allianceId(), score.quantizedScore());
            }
        }

        return new PayloadReplayState(Set.copyOf(activeTreaties), Map.copyOf(flagsByAlliance), Map.copyOf(scoresByAlliance));
    }

    private static PayloadReplayState replayPayloadState(TreatyVisRuntimePayload payload, int sampleDay) {
        Set<PayloadTreatyKey> activeTreaties = new LinkedHashSet<>();
        for (int edgeIndex : payload.initialState().activeEdgeIndexes()) {
            activeTreaties.add(payloadTreatyKey(payload, edgeIndex));
        }
        for (int dayIndex = 0; dayIndex < payload.treatyChanges().days().size(); dayIndex += 1) {
            int day = payload.treatyChanges().days().get(dayIndex);
            if (day > sampleDay) {
                break;
            }
            int rowStart = payload.treatyChanges().rowOffsets().get(dayIndex);
            int rowEnd = payload.treatyChanges().rowOffsets().get(dayIndex + 1);
            for (int rowIndex = rowStart; rowIndex < rowEnd; rowIndex += 1) {
                PayloadTreatyKey key = payloadTreatyKey(payload, payload.treatyChanges().edgeIndexes().get(rowIndex));
                int action = payload.treatyChanges().actions().get(rowIndex);
                if (action == 1 || action == 2) {
                    activeTreaties.add(key);
                } else if (action == 3 || action == 4) {
                    activeTreaties.remove(key);
                }
            }
        }

        Map<Integer, Integer> flagsByAlliance = new LinkedHashMap<>();
        for (int index = 0; index < payload.initialState().flagAllianceIndexes().size(); index += 1) {
            int allianceId = payload.alliances().ids().get(payload.initialState().flagAllianceIndexes().get(index));
            int flagIndex = payload.initialState().flagIndexes().get(index);
            if (flagIndex > 0) {
                flagsByAlliance.put(allianceId, flagIndex);
            }
        }
        for (int dayIndex = 0; dayIndex < payload.flagChanges().days().size(); dayIndex += 1) {
            int day = payload.flagChanges().days().get(dayIndex);
            if (day > sampleDay) {
                break;
            }
            int rowStart = payload.flagChanges().rowOffsets().get(dayIndex);
            int rowEnd = payload.flagChanges().rowOffsets().get(dayIndex + 1);
            for (int rowIndex = rowStart; rowIndex < rowEnd; rowIndex += 1) {
                int allianceId = payload.alliances().ids().get(payload.flagChanges().allianceIndexes().get(rowIndex));
                int flagIndex = payload.flagChanges().flagIndexes().get(rowIndex);
                if (flagIndex <= 0) {
                    flagsByAlliance.remove(allianceId);
                } else {
                    flagsByAlliance.put(allianceId, flagIndex);
                }
            }
        }

        Map<Integer, Integer> scoresByAlliance = new LinkedHashMap<>();
        for (int index = 0; index < payload.initialState().scoreAllianceIndexes().size(); index += 1) {
            int allianceId = payload.alliances().ids().get(payload.initialState().scoreAllianceIndexes().get(index));
            scoresByAlliance.put(allianceId, payload.initialState().scoreQuantized().get(index));
        }
        for (int dayIndex = 0; dayIndex < payload.scoreSnapshots().days().size(); dayIndex += 1) {
            int day = payload.scoreSnapshots().days().get(dayIndex);
            if (day > sampleDay) {
                break;
            }
            int rowStart = payload.scoreSnapshots().rowOffsets().get(dayIndex);
            int rowEnd = payload.scoreSnapshots().rowOffsets().get(dayIndex + 1);
            scoresByAlliance.clear();
            for (int rowIndex = rowStart; rowIndex < rowEnd; rowIndex += 1) {
                int allianceId = payload.alliances().ids().get(payload.scoreSnapshots().allianceIndexes().get(rowIndex));
                scoresByAlliance.put(allianceId, payload.scoreSnapshots().scoresQuantized().get(rowIndex));
            }
        }

        return new PayloadReplayState(Set.copyOf(activeTreaties), Map.copyOf(flagsByAlliance), Map.copyOf(scoresByAlliance));
    }

    private static PayloadTreatyKey payloadTreatyKey(TreatyVisRuntimePayload payload, int edgeIndex) {
        return new PayloadTreatyKey(
                payload.alliances().ids().get(payload.edges().fromAllianceIndexes().get(edgeIndex)),
                payload.alliances().ids().get(payload.edges().toAllianceIndexes().get(edgeIndex)),
                payload.treatyTypes().get(payload.edges().treatyTypeIndexes().get(edgeIndex)).trim().toLowerCase(java.util.Locale.ROOT)
        );
    }

    private static String normalizeFlagUrl(String flagUrl) {
        return flagUrl == null ? "" : flagUrl.trim();
    }

    private static List<TreatyVisRuntimeTreatyCheckpointRefreshService.CurrentTreatyRow> loadCurrentTreatyTruth() {
        Set<Treaty> treaties = Locutus.imp().getNationDB().getTreaties();
        List<TreatyVisRuntimeTreatyCheckpointRefreshService.CurrentTreatyRow> rows = new ArrayList<>(treaties.size());
        for (Treaty treaty : treaties) {
            rows.add(new TreatyVisRuntimeTreatyCheckpointRefreshService.CurrentTreatyRow(
                    treaty.getFromId(),
                    treaty.getToId(),
                    treaty.getType(),
                    treaty.isPermanent() ? -1 : treaty.getTurnsRemaining()
            ));
        }
        rows.sort(Comparator
                .comparingInt(TreatyVisRuntimeTreatyCheckpointRefreshService.CurrentTreatyRow::fromAllianceId)
                .thenComparingInt(TreatyVisRuntimeTreatyCheckpointRefreshService.CurrentTreatyRow::toAllianceId));
        return rows;
    }

    private static List<LiveAllianceScoreRow> loadCurrentLiveScores() {
        return Locutus.imp().getNationDB().getAlliances().stream()
                .filter(alliance -> alliance.getId() > 0)
                .map(alliance -> new LiveAllianceScoreRow(
                        alliance.getId(),
                Math.max(0, Math.toIntExact(Math.round(alliance.getScore() * TreatyHistoryRuntimeConfig.SCORE_QUANTIZATION)))
                ))
                .sorted(Comparator.comparingInt(LiveAllianceScoreRow::allianceId))
                .toList();
    }

    private static List<LiveAllianceFlagRow> loadCurrentLiveFlags() {
        return Locutus.imp().getNationDB().getAlliances().stream()
                .filter(alliance -> alliance.getId() > 0)
                .map(alliance -> new LiveAllianceFlagRow(alliance.getId(), normalizeFlagUrl(alliance.getFlag())))
                .sorted(Comparator.comparingInt(LiveAllianceFlagRow::allianceId))
                .toList();
    }

    private Map<String, Object> stagedReport(TreatyVisRuntimeBootstrapService.ValidationResult staged) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("valid", staged.valid());
        map.put("importRoot", staged.importRoot().toString());
        map.put("reportPath", staged.reportPath().toString());
        map.put("stagedFileCount", staged.stagedFileCount());
        map.put("stagedDirectoryCount", staged.stagedDirectoryCount());
        map.put("flagCacheFileCount", staged.flagCacheFileCount());
        map.put("requiredGroupCounts", staged.requiredGroupCounts());
        map.put("missingRequiredEntries", staged.missingRequiredEntries());
        return map;
    }

    private Map<String, Object> runtimeReport(
            TreatyVisRuntimeRepository.RuntimeBootstrapState bootstrapState,
            TreatyVisRuntimeRepository.TreatyCheckpoint checkpoint,
            TreatyMetrics treatyMetrics,
            ScoreMetrics scoreMetrics,
            FlagMetrics flagMetrics,
            TreatyReplayMetrics treatyReplayMetrics,
            PayloadReplayMetrics payloadReplayMetrics,
            BootstrapLiveHandoffMetrics bootstrapLiveHandoffMetrics,
            boolean importedSnapshotAvailable
    ) {
        Map<String, Object> bootstrap = new LinkedHashMap<>();
        bootstrap.put("treatyImportComplete", bootstrapState.treatyImportComplete());
        bootstrap.put("scoreImportComplete", bootstrapState.scoreImportComplete());
        bootstrap.put("flagImportComplete", bootstrapState.flagImportComplete());
        bootstrap.put("importedTreatyMinDay", bootstrapState.importedTreatyMinDay());
        bootstrap.put("importedTreatyMaxDay", bootstrapState.importedTreatyMaxDay());
        bootstrap.put("importedScoreMinDay", bootstrapState.importedScoreMinDay());
        bootstrap.put("importedScoreMaxDay", bootstrapState.importedScoreMaxDay());
        bootstrap.put("importedFlagMinDay", bootstrapState.importedFlagMinDay());
        bootstrap.put("importedFlagMaxDay", bootstrapState.importedFlagMaxDay());
        bootstrap.put("validationComplete", bootstrapState.validationComplete());

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bootstrapState", bootstrap);
        map.put("checkpointDay", checkpoint == null ? null : checkpoint.day());
        map.put("checkpointSourceCursorMs", checkpoint == null ? null : checkpoint.sourceCursorMs());
        map.put("treaties", treatyMetrics.asReportMap());
        map.put("treatyReplay", treatyReplayMetrics.asReportMap());
        map.put("payloadReplay", payloadReplayMetrics.asReportMap());
        map.put("bootstrapLiveHandoff", bootstrapLiveHandoffMetrics.asReportMap());
        map.put("scores", scoreMetrics.asReportMap());
        map.put("flags", flagMetrics.asReportMap());
        map.put("importedSnapshotAvailable", importedSnapshotAvailable);
        return map;
    }

    private static int epochDay(long timestampMs) {
        return Math.toIntExact(Instant.ofEpochMilli(timestampMs).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay());
    }

    public record RuntimeValidationResult(boolean valid, Path reportPath, List<String> issues) {
    }

    private record TreatyMetrics(Integer sourceCount, Integer importedCount, Integer minDay, Integer maxDay, Integer checkpointDay) {
        Map<String, Object> asReportMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sourceCount", sourceCount);
            map.put("importedCount", importedCount);
            map.put("minDay", minDay);
            map.put("maxDay", maxDay);
            map.put("checkpointDay", checkpointDay);
            return map;
        }
    }

    private record TreatyReplayMetrics(boolean matchesCurrentTruth, int replayedActiveCount, int currentTruthCount) {
        Map<String, Object> asReportMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("matchesCurrentTruth", matchesCurrentTruth);
            map.put("replayedActiveCount", replayedActiveCount);
            map.put("currentTruthCount", currentTruthCount);
            return map;
        }
    }

    private record PayloadReplayMetrics(boolean matchesImportedSource, int sampledDayCount) {
        Map<String, Object> asReportMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("matchesImportedSource", matchesImportedSource);
            map.put("sampledDayCount", sampledDayCount);
            return map;
        }
    }

    private record BootstrapLiveHandoffMetrics(boolean scoreMatchesCurrentLive, boolean flagMatchesCurrentLive, int liveScoreAllianceCount, int liveFlagAllianceCount) {
        Map<String, Object> asReportMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("scoreMatchesCurrentLive", scoreMatchesCurrentLive);
            map.put("flagMatchesCurrentLive", flagMatchesCurrentLive);
            map.put("liveScoreAllianceCount", liveScoreAllianceCount);
            map.put("liveFlagAllianceCount", liveFlagAllianceCount);
            return map;
        }
    }

    private record ScoreMetrics(Integer sourceDayCount, Integer importedDayCount, Integer minDay, Integer maxDay, boolean helperMatchesLatest) {
        Map<String, Object> asReportMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sourceDayCount", sourceDayCount);
            map.put("importedDayCount", importedDayCount);
            map.put("minDay", minDay);
            map.put("maxDay", maxDay);
            map.put("helperMatchesLatest", helperMatchesLatest);
            return map;
        }
    }

    private record FlagMetrics(Integer sourceEventCount, Integer importedEventCount, Integer minDay, Integer maxDay, boolean rawCacheHashesValid, boolean helperMatchesLatest) {
        Map<String, Object> asReportMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sourceEventCount", sourceEventCount);
            map.put("importedEventCount", importedEventCount);
            map.put("minDay", minDay);
            map.put("maxDay", maxDay);
            map.put("rawCacheHashesValid", rawCacheHashesValid);
            map.put("helperMatchesLatest", helperMatchesLatest);
            return map;
        }
    }

    record LiveAllianceScoreRow(int allianceId, int quantizedScore) {
    }

    record LiveAllianceFlagRow(int allianceId, String flagUrl) {
    }

    private record PayloadReplayState(
            Set<PayloadTreatyKey> activeTreaties,
            Map<Integer, Integer> flagsByAlliance,
            Map<Integer, Integer> scoresByAlliance
    ) {
    }

    private record PayloadTreatyKey(int fromAllianceId, int toAllianceId, String treatyType) {
        static PayloadTreatyKey fromEdge(TreatyVisRuntimeInput.TreatyEdge edge) {
            return new PayloadTreatyKey(
                    edge.fromAllianceId(),
                    edge.toAllianceId(),
                    edge.treatyType() == null ? "" : edge.treatyType().getName().trim().toLowerCase(java.util.Locale.ROOT)
            );
        }
    }

}
