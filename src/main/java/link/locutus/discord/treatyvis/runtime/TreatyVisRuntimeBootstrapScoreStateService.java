package link.locutus.discord.treatyvis.runtime;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBAlliance;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class TreatyVisRuntimeBootstrapScoreStateService {
    private final TreatyVisRuntimeRepository repository;
    private final Supplier<List<LiveAllianceScore>> liveScoreSupplier;

    public TreatyVisRuntimeBootstrapScoreStateService() {
        this(
                new TreatyVisRuntimeRepository(Locutus.imp().getNationDB()),
                TreatyVisRuntimeBootstrapScoreStateService::loadLiveAllianceScores
        );
    }

    TreatyVisRuntimeBootstrapScoreStateService(
            TreatyVisRuntimeRepository repository,
            Supplier<List<LiveAllianceScore>> liveScoreSupplier
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.liveScoreSupplier = Objects.requireNonNull(liveScoreSupplier, "liveScoreSupplier");
    }

    public void assertBootstrapTargetAvailable(boolean replaceExisting) throws IOException {
        repository.ensureTables();
        if (repository.countTopNScoreRows() == 0) {
            throw new IOException("Historical score import must complete before bootstrapping live score helper state.");
        }
        if (!replaceExisting && repository.countLastAllianceScores() > 0) {
            throw new IOException("Live score helper state already exists. Run the runtime bootstrap reset first or rerun with replace enabled.");
        }
    }

    public ScoreBootstrapResult bootstrapCurrentScores(boolean replaceExisting) throws IOException {
        assertBootstrapTargetAvailable(replaceExisting);
        if (replaceExisting) {
            repository.clearLastAllianceScores();
        }

        List<LiveAllianceScore> liveScores = new ArrayList<>(liveScoreSupplier.get());
        if (liveScores.isEmpty()) {
            throw new IOException("No live alliance scores were available for score helper-state bootstrap.");
        }

        liveScores.sort(Comparator.comparingInt(LiveAllianceScore::allianceId));
        List<TreatyVisRuntimeRepository.LastAllianceScoreRow> helperRows = new ArrayList<>(liveScores.size());
        List<TreatyVisRuntimeLegacyScoreImportService.ScoreRow> topRows = new ArrayList<>();
        for (LiveAllianceScore liveScore : liveScores) {
            if (liveScore.allianceId() <= 0) {
                continue;
            }
            helperRows.add(new TreatyVisRuntimeRepository.LastAllianceScoreRow(liveScore.allianceId(), liveScore.quantizedScore()));
            if (liveScore.quantizedScore() > 0) {
                topRows.add(new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(liveScore.allianceId(), liveScore.quantizedScore()));
            }
        }

        topRows.sort(Comparator
                .comparingInt(TreatyVisRuntimeLegacyScoreImportService.ScoreRow::quantizedScore)
                .reversed()
                .thenComparingInt(TreatyVisRuntimeLegacyScoreImportService.ScoreRow::allianceId));
        if (topRows.size() > TreatyHistoryRuntimeConfig.TOP_N_ALLIANCES) {
            topRows = new ArrayList<>(topRows.subList(0, TreatyHistoryRuntimeConfig.TOP_N_ALLIANCES));
        }

        repository.replaceLastAllianceScores(helperRows);

        int bootstrapDay = Math.toIntExact(LocalDate.now(ZoneOffset.UTC).toEpochDay());
        byte[] bootstrapPayload = TreatyVisRuntimeScoreRowCodec.encode(topRows);
        Map<Integer, byte[]> storedRows = repository.loadTopNScoreRows();
        Map.Entry<Integer, byte[]> latestStoredRow = storedRows.isEmpty() ? null : new ArrayList<>(storedRows.entrySet()).get(storedRows.size() - 1);
        boolean wroteBootstrapDay = latestStoredRow == null
                || latestStoredRow.getKey() != bootstrapDay
                || !Arrays.equals(latestStoredRow.getValue(), bootstrapPayload);
        if (wroteBootstrapDay) {
            repository.replaceTopNScoreRows(Map.of(bootstrapDay, bootstrapPayload));
        }

        return new ScoreBootstrapResult(helperRows.size(), topRows.size(), bootstrapDay, wroteBootstrapDay);
    }

    public ScoreBootstrapResetResult resetBootstrappedCurrentScores() {
        return new ScoreBootstrapResetResult(repository.clearLastAllianceScores());
    }

    private static List<LiveAllianceScore> loadLiveAllianceScores() {
        return Locutus.imp().getNationDB().getAlliances().stream()
                .filter(alliance -> alliance.getId() > 0)
                .map(alliance -> new LiveAllianceScore(
                        alliance.getId(),
                        quantizeScore(alliance)
                ))
                .toList();
    }

    private static int quantizeScore(DBAlliance alliance) {
        return Math.max(0, Math.toIntExact(Math.round(alliance.getScore() * TreatyHistoryRuntimeConfig.SCORE_QUANTIZATION)));
    }

    public record LiveAllianceScore(int allianceId, int quantizedScore) {
    }

    public record ScoreBootstrapResult(int helperAllianceCount, int topAllianceCount, int bootstrapDay, boolean wroteBootstrapDay) {
    }

    public record ScoreBootstrapResetResult(int deletedHelperRowCount) {
    }
}