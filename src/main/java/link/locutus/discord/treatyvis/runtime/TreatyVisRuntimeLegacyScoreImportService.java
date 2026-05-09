package link.locutus.discord.treatyvis.runtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import link.locutus.discord.Locutus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class TreatyVisRuntimeLegacyScoreImportService {
    private static final String SCORE_WINDOW_PREFIX = "alliance_scores_v2_window_";
    private static final String SCORE_WINDOW_SUFFIX = ".msgpack";

    private final Path stagedImportRoot;
    private final TreatyVisRuntimeRepository repository;
    private final ObjectMapper msgpack;

    public TreatyVisRuntimeLegacyScoreImportService() {
        this(
                TreatyVisRuntimeBootstrapService.DEFAULT_IMPORT_ROOT,
                new TreatyVisRuntimeRepository(Locutus.imp().getNationDB()),
            TreatyVisRuntimeSerializers.MSGPACK
        );
    }

    TreatyVisRuntimeLegacyScoreImportService(Path stagedImportRoot, TreatyVisRuntimeRepository repository, ObjectMapper msgpack) {
        this.stagedImportRoot = stagedImportRoot.toAbsolutePath().normalize();
        this.repository = Objects.requireNonNull(repository, "repository");
        this.msgpack = Objects.requireNonNull(msgpack, "msgpack");
    }

    public void assertImportTargetAvailable(boolean replaceExisting) throws IOException {
        repository.ensureTables();
        TreatyVisRuntimeRepository.RuntimeBootstrapState bootstrapState = repository.loadBootstrapState();
        if (!replaceExisting && (bootstrapState.scoreImportComplete() || repository.countTopNScoreRows() > 0)) {
            throw new IOException("Historical score import already exists. Run the runtime bootstrap reset first or rerun with replace enabled.");
        }
    }

    public ScoreImportResult importHistoricalScores(boolean replaceExisting) throws IOException {
        assertImportTargetAvailable(replaceExisting);

        if (replaceExisting) {
            repository.clearImportedScoreHistory();
        }

        Map<Integer, ImportedScoreDay> scoreDays = loadHistoricalScoreDays();
        if (scoreDays.isEmpty()) {
            throw new IOException("No staged treaty-vis historical score windows were found under " + stagedImportRoot);
        }

        Map<Integer, byte[]> payloadByDay = new LinkedHashMap<>();
        int importedRowCount = 0;
        int minDay = Integer.MAX_VALUE;
        int maxDay = Integer.MIN_VALUE;
        for (ImportedScoreDay scoreDay : scoreDays.values()) {
            payloadByDay.put(scoreDay.day(), scoreDay.payload());
            importedRowCount += scoreDay.rowCount();
            minDay = Math.min(minDay, scoreDay.day());
            maxDay = Math.max(maxDay, scoreDay.day());
        }

        repository.replaceTopNScoreRows(payloadByDay);
        repository.markHistoricalScoreImportComplete(minDay, maxDay);

        return new ScoreImportResult(payloadByDay.size(), importedRowCount, minDay, maxDay);
    }

    public ScoreResetResult resetImportedHistoricalScores() {
        int deletedDayCount = repository.clearImportedScoreHistory();
        return new ScoreResetResult(deletedDayCount);
    }

    Map<Integer, ImportedScoreDay> loadHistoricalScoreDays() throws IOException {
        Path publicDataRoot = stagedImportRoot.resolve("public").resolve("data");
        if (!Files.isDirectory(publicDataRoot)) {
            throw new IOException("Staged treaty-vis public data directory is missing: " + publicDataRoot);
        }

        List<Path> windowFiles;
        try (Stream<Path> stream = Files.list(publicDataRoot)) {
            windowFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        return fileName.startsWith(SCORE_WINDOW_PREFIX) && fileName.endsWith(SCORE_WINDOW_SUFFIX);
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }

        Map<Integer, ImportedScoreDay> scoreDays = new TreeMap<>();
        for (Path windowFile : windowFiles) {
            LegacyScoreWindow window = msgpack.readValue(windowFile.toFile(), LegacyScoreWindow.class);
            validateWindow(window, windowFile);
            for (int index = 0; index < window.dayKeys().size(); index += 1) {
                String dayKey = window.dayKeys().get(index);
                List<List<Integer>> rawRows = window.days().get(index);
                ImportedScoreDay importedScoreDay = buildImportedScoreDay(dayKey, rawRows, window.quantizationScale(), windowFile);
                ImportedScoreDay previous = scoreDays.put(importedScoreDay.day(), importedScoreDay);
                if (previous != null) {
                    throw new IOException("Duplicate historical score day " + dayKey + " while importing " + windowFile);
                }
            }
        }

        return scoreDays;
    }

    private static void validateWindow(LegacyScoreWindow window, Path windowFile) throws IOException {
        if (window == null) {
            throw new IOException("Unable to decode staged historical score window: " + windowFile);
        }
        if (window.schemaVersion() != 2) {
            throw new IOException("Unsupported historical score schema version " + window.schemaVersion() + " in " + windowFile);
        }
        if (window.quantizationScale() <= 0) {
            throw new IOException("Historical score quantization must be positive in " + windowFile);
        }
        if (window.dayKeys() == null || window.days() == null || window.dayKeys().size() != window.days().size()) {
            throw new IOException("Historical score window has mismatched day_keys and days in " + windowFile);
        }
    }

    private static ImportedScoreDay buildImportedScoreDay(String dayKey, List<List<Integer>> rawRows, int sourceQuantizationScale, Path windowFile) throws IOException {
        int epochDay;
        try {
            epochDay = Math.toIntExact(LocalDate.parse(dayKey).toEpochDay());
        } catch (RuntimeException ex) {
            throw new IOException("Invalid historical score day key " + dayKey + " in " + windowFile, ex);
        }

        List<ScoreRow> scoreRows = new ArrayList<>();
        Set<Integer> allianceIds = new LinkedHashSet<>();
        List<List<Integer>> safeRows = rawRows == null ? List.of() : rawRows;
        for (List<Integer> rawRow : safeRows) {
            if (rawRow == null || rawRow.size() < 2) {
                throw new IOException("Invalid historical score row in " + windowFile + " for day " + dayKey);
            }
            int allianceId = rawRow.get(0);
            int quantizedScore = requantizeScore(rawRow.get(1), sourceQuantizationScale, TreatyHistoryRuntimeConfig.SCORE_QUANTIZATION);
            if (allianceId <= 0 || quantizedScore <= 0) {
                continue;
            }
            if (!allianceIds.add(allianceId)) {
                throw new IOException("Duplicate alliance score entry for alliance " + allianceId + " on " + dayKey + " in " + windowFile);
            }
            scoreRows.add(new ScoreRow(allianceId, quantizedScore));
        }

        scoreRows.sort(Comparator.comparingInt(ScoreRow::quantizedScore).reversed().thenComparingInt(ScoreRow::allianceId));
        if (scoreRows.size() > TreatyHistoryRuntimeConfig.TOP_N_ALLIANCES) {
            scoreRows = new ArrayList<>(scoreRows.subList(0, TreatyHistoryRuntimeConfig.TOP_N_ALLIANCES));
        }

        return new ImportedScoreDay(epochDay, scoreRows.size(), encodeRows(scoreRows));
    }

    static byte[] encodeRows(List<ScoreRow> scoreRows) {
        return TreatyVisRuntimeScoreRowCodec.encode(scoreRows);
    }

    static int requantizeScore(int sourceQuantizedScore, int sourceScale, int targetScale) {
        if (sourceScale == targetScale) {
            return sourceQuantizedScore;
        }
        long numerator = (long) sourceQuantizedScore * targetScale;
        return Math.toIntExact((numerator + (sourceScale / 2L)) / sourceScale);
    }

    public record ScoreImportResult(int importedDayCount, int importedRowCount, int minDay, int maxDay) {
    }

    public record ScoreResetResult(int deletedDayCount) {
    }

    record ImportedScoreDay(int day, int rowCount, byte[] payload) {
    }

    record ScoreRow(int allianceId, int quantizedScore) {
    }

    private record LegacyScoreWindow(
            @JsonProperty("schema_version") int schemaVersion,
            @JsonProperty("quantization_scale") int quantizationScale,
            @JsonProperty("day_keys") List<String> dayKeys,
            List<List<List<Integer>>> days
    ) {
    }
}