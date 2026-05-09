package link.locutus.discord.treatyvis.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import link.locutus.discord.Locutus;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public final class TreatyVisRuntimeBootstrapService {
    public static final Path DEFAULT_LEGACY_SOURCE_ROOT = Path.of("..", "treaty-vis");
    public static final Path DEFAULT_IMPORT_ROOT = Path.of("data", "treaty_vis_runtime", "legacy-import");
    public static final Path DEFAULT_VALIDATE_REPORT_PATH = Path.of("data", "treaty_vis_runtime", "runtime_validate_report.json");

    private static final List<Path> REQUIRED_FILES = List.of(
            Path.of("data", "treaties.json"),
            Path.of("data", "treaties_archive.json"),
            Path.of("data", "incremental_state.json"),
            Path.of("data", "flags_day_cache.msgpack"),
            Path.of("data", "flag_download_state.json"),
            Path.of("public", "data", "treaty_changes_reconciled_index.msgpack"),
            Path.of("public", "data", "treaty_changes_reconciled_delta.msgpack"),
            Path.of("public", "data", "treaty_changes_reconciled_summary.msgpack"),
            Path.of("public", "data", "alliance_scores_v2_index.msgpack"),
            Path.of("public", "data", "alliance_scores_v2_delta.msgpack"),
            Path.of("public", "data", "alliance_score_ranks_daily.msgpack"),
            Path.of("public", "data", "flags_index.msgpack"),
            Path.of("public", "data", "flags_delta.msgpack"),
            Path.of("public", "data", "flag_assets.msgpack")
    );

    private static final List<Path> OPTIONAL_FILES = List.of(
            Path.of("data", "legacy_flags.csv"),
            Path.of("public", "data", "manifest.json"),
            Path.of("public", "data", "treaty_changes_reconciled.msgpack"),
            Path.of("public", "data", "treaty_changes_reconciled_flags.msgpack"),
            Path.of("public", "data", "alliance_scores_v2.msgpack"),
            Path.of("public", "data", "flags.msgpack"),
            Path.of("public", "data", "flag_atlas.webp")
    );

    private static final List<PatternGroup> REQUIRED_GROUPS = List.of(
            new PatternGroup(Path.of("public", "data"), "treaty_changes_reconciled_window_*.msgpack"),
            new PatternGroup(Path.of("public", "data"), "alliance_scores_v2_window_*.msgpack"),
            new PatternGroup(Path.of("public", "data"), "flags_window_*.msgpack")
    );

    private static final List<Path> REQUIRED_DIRECTORIES = List.of(
            Path.of("data", "flag_cache")
    );

    private final Path importRoot;
    private final Path validateReportPath;
    private final ObjectMapper json;
    private final TreatyVisRuntimeRepository repository;
    private final TreatyVisRuntimeLegacyTreatyImportService treatyImportService;
    private final TreatyVisRuntimeLegacyTreatyCheckpointImportService checkpointImportService;
    private final TreatyVisRuntimeLegacyScoreImportService scoreImportService;
    private final TreatyVisRuntimeBootstrapScoreStateService scoreBootstrapService;
    private final TreatyVisRuntimeLegacyFlagImportService flagImportService;
    private final TreatyVisRuntimeBootstrapFlagStateService flagBootstrapService;
    private final TreatyVisRuntimeTreatyCheckpointRefreshService checkpointRefreshService;

    public TreatyVisRuntimeBootstrapService() {
        this(
                DEFAULT_IMPORT_ROOT,
                DEFAULT_VALIDATE_REPORT_PATH,
            TreatyVisRuntimeSerializers.JSON,
                new TreatyVisRuntimeRepository(Locutus.imp().getNationDB()),
                new TreatyVisRuntimeLegacyTreatyImportService(),
                new TreatyVisRuntimeLegacyTreatyCheckpointImportService(),
                new TreatyVisRuntimeLegacyScoreImportService(),
                new TreatyVisRuntimeBootstrapScoreStateService(),
                new TreatyVisRuntimeLegacyFlagImportService(),
                new TreatyVisRuntimeBootstrapFlagStateService(),
                new TreatyVisRuntimeTreatyCheckpointRefreshService()
        );
    }

            TreatyVisRuntimeBootstrapService(
                Path importRoot,
                Path validateReportPath,
                ObjectMapper json
            ) {
                this.importRoot = normalizePath(Objects.requireNonNull(importRoot, "importRoot"));
                this.validateReportPath = normalizePath(Objects.requireNonNull(validateReportPath, "validateReportPath"));
                this.json = Objects.requireNonNull(json, "json");
                this.repository = null;
                this.treatyImportService = null;
                this.checkpointImportService = null;
                this.scoreImportService = null;
                this.scoreBootstrapService = null;
                this.flagImportService = null;
                this.flagBootstrapService = null;
                this.checkpointRefreshService = null;
            }

    TreatyVisRuntimeBootstrapService(
            Path importRoot,
            Path validateReportPath,
            ObjectMapper json,
            TreatyVisRuntimeRepository repository,
            TreatyVisRuntimeLegacyTreatyImportService treatyImportService,
            TreatyVisRuntimeLegacyTreatyCheckpointImportService checkpointImportService,
            TreatyVisRuntimeLegacyScoreImportService scoreImportService,
            TreatyVisRuntimeBootstrapScoreStateService scoreBootstrapService,
            TreatyVisRuntimeLegacyFlagImportService flagImportService,
                TreatyVisRuntimeBootstrapFlagStateService flagBootstrapService,
                TreatyVisRuntimeTreatyCheckpointRefreshService checkpointRefreshService
    ) {
        this.importRoot = normalizePath(Objects.requireNonNull(importRoot, "importRoot"));
        this.validateReportPath = normalizePath(Objects.requireNonNull(validateReportPath, "validateReportPath"));
        this.json = Objects.requireNonNull(json, "json");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.treatyImportService = Objects.requireNonNull(treatyImportService, "treatyImportService");
        this.checkpointImportService = Objects.requireNonNull(checkpointImportService, "checkpointImportService");
        this.scoreImportService = Objects.requireNonNull(scoreImportService, "scoreImportService");
        this.scoreBootstrapService = Objects.requireNonNull(scoreBootstrapService, "scoreBootstrapService");
        this.flagImportService = Objects.requireNonNull(flagImportService, "flagImportService");
        this.flagBootstrapService = Objects.requireNonNull(flagBootstrapService, "flagBootstrapService");
        this.checkpointRefreshService = checkpointRefreshService;
    }

    TreatyVisRuntimeBootstrapService(
            Path importRoot,
            Path validateReportPath,
            ObjectMapper json,
            TreatyVisRuntimeRepository repository,
            TreatyVisRuntimeLegacyTreatyImportService treatyImportService,
            TreatyVisRuntimeLegacyTreatyCheckpointImportService checkpointImportService,
            TreatyVisRuntimeLegacyScoreImportService scoreImportService,
            TreatyVisRuntimeBootstrapScoreStateService scoreBootstrapService,
            TreatyVisRuntimeLegacyFlagImportService flagImportService,
            TreatyVisRuntimeBootstrapFlagStateService flagBootstrapService
    ) {
        this(
                importRoot,
                validateReportPath,
                json,
                repository,
                treatyImportService,
                checkpointImportService,
                scoreImportService,
                scoreBootstrapService,
                flagImportService,
                flagBootstrapService,
                null
        );
    }

    public RuntimeBootstrapImportResult importLegacyRuntime(Path sourceRoot, boolean replace) throws IOException {
        requireWorkflowDependencies();
        treatyImportService.assertImportTargetAvailable(replace);
        checkpointImportService.assertImportTargetAvailable(replace);
        scoreImportService.assertImportTargetAvailable(replace);
        flagImportService.assertImportTargetAvailable(replace);

        if (replace) {
            repository.resetBootstrapState();
        }

        ImportResult stagedImport = importLegacyArtifacts(sourceRoot, replace);
        TreatyVisRuntimeLegacyTreatyImportService.TreatyImportResult treatyImport = treatyImportService.importHistoricalTreaties(replace);
        TreatyVisRuntimeLegacyTreatyCheckpointImportService.TreatyCheckpointImportResult checkpointImport = checkpointImportService.importHistoricalTreatyCheckpoint(replace);
        TreatyVisRuntimeLegacyScoreImportService.ScoreImportResult scoreImport = scoreImportService.importHistoricalScores(replace);
        TreatyVisRuntimeBootstrapScoreStateService.ScoreBootstrapResult scoreBootstrap = scoreBootstrapService.bootstrapCurrentScores(replace);
        TreatyVisRuntimeLegacyFlagImportService.FlagImportResult flagImport = flagImportService.importHistoricalFlags(replace);
        TreatyVisRuntimeBootstrapFlagStateService.FlagBootstrapResult flagBootstrap = flagBootstrapService.bootstrapCurrentFlags();
        if (checkpointRefreshService != null) {
            checkpointRefreshService.refreshTreatyCheckpoint();
        }

        return new RuntimeBootstrapImportResult(
                stagedImport,
                treatyImport,
                checkpointImport,
                scoreImport,
                scoreBootstrap,
                flagImport,
                flagBootstrap
        );
    }

    public ImportResult importLegacyArtifacts(Path sourceRoot, boolean replace) throws IOException {
        Path resolvedSourceRoot = resolveSourceRoot(sourceRoot);
        SourceImportPlan importPlan = buildImportPlan(resolvedSourceRoot);
        if (!importPlan.missingRequiredEntries().isEmpty()) {
            throw new IOException(
                    "Missing required treaty-vis legacy artifacts: " + String.join(", ", importPlan.missingRequiredEntries())
            );
        }

        if (Files.exists(importRoot)) {
            if (!replace) {
                throw new IOException(
                        "Treaty-vis legacy import staging already exists at " + importRoot
                                + ". Run the bootstrap reset first or rerun the import with replace enabled."
                );
            }
            deleteRecursively(importRoot);
        }
        deleteIfExists(validateReportPath);
        Files.createDirectories(importRoot);

        CopySummary summary = new CopySummary();
        for (Path requiredFile : importPlan.requiredFiles()) {
            copyRelativeFile(resolvedSourceRoot, requiredFile, summary);
        }
        for (Path optionalFile : importPlan.optionalFiles()) {
            copyRelativeFile(resolvedSourceRoot, optionalFile, summary);
        }
        for (Path requiredDirectory : importPlan.requiredDirectories()) {
            copyDirectory(resolvedSourceRoot, requiredDirectory, summary);
        }
        for (List<Path> matchedFiles : importPlan.requiredGroupMatches().values()) {
            for (Path matchedFile : matchedFiles) {
                copyRelativeFile(resolvedSourceRoot, matchedFile, summary);
            }
        }

        return new ImportResult(
                resolvedSourceRoot,
                importRoot,
                summary.copiedFileCount,
                summary.copiedBytes,
                summary.flagCacheFileCount,
                toGroupCountMap(importPlan.requiredGroupMatches())
        );
    }

    public ValidationResult validateImportedLegacyArtifacts() throws IOException {
        ValidationResult result = buildValidationResult();
        if (validateReportPath.getParent() != null) {
            Files.createDirectories(validateReportPath.getParent());
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("createdAt", Instant.now().toString());
        report.put("importRoot", result.importRoot().toString());
        report.put("valid", result.valid());
        report.put("importRootExists", result.importRootExists());
        report.put("stagedFileCount", result.stagedFileCount());
        report.put("stagedDirectoryCount", result.stagedDirectoryCount());
        report.put("flagCacheFileCount", result.flagCacheFileCount());
        report.put("missingRequiredEntries", result.missingRequiredEntries());
        report.put("requiredGroupCounts", result.requiredGroupCounts());
        json.writerWithDefaultPrettyPrinter().writeValue(validateReportPath.toFile(), report);
        return result;
    }

    public ResetResult resetBootstrapArtifacts() throws IOException {
        List<Path> deletedPaths = new ArrayList<>();
        List<Path> missingPaths = new ArrayList<>();

        deleteKnownPath(importRoot, deletedPaths, missingPaths);
        deleteKnownPath(validateReportPath, deletedPaths, missingPaths);
        deleteKnownPath(TreatyHistoryRuntimeConfig.FLAG_ATLAS_CACHE_PATH, deletedPaths, missingPaths);
        deleteKnownPath(TreatyHistoryRuntimeConfig.FLAG_ATLAS_STATE_PATH, deletedPaths, missingPaths);

        return new ResetResult(List.copyOf(deletedPaths), List.copyOf(missingPaths));
    }

    public RuntimeBootstrapResetResult resetImportedRuntime() throws IOException {
        requireWorkflowDependencies();
        TreatyVisRuntimeLegacyTreatyImportService.TreatyResetResult treatyReset = treatyImportService.resetImportedHistoricalTreaties();
        TreatyVisRuntimeLegacyTreatyCheckpointImportService.TreatyCheckpointResetResult checkpointReset = checkpointImportService.resetImportedHistoricalTreatyCheckpoint();
        TreatyVisRuntimeLegacyScoreImportService.ScoreResetResult scoreReset = scoreImportService.resetImportedHistoricalScores();
        TreatyVisRuntimeBootstrapScoreStateService.ScoreBootstrapResetResult scoreBootstrapReset = scoreBootstrapService.resetBootstrappedCurrentScores();
        TreatyVisRuntimeLegacyFlagImportService.FlagResetResult flagReset = flagImportService.resetImportedHistoricalFlags();
        ResetResult artifactReset = resetBootstrapArtifacts();
        repository.resetBootstrapState();
        return new RuntimeBootstrapResetResult(
                artifactReset,
                treatyReset,
                checkpointReset,
                scoreReset,
                scoreBootstrapReset,
                flagReset
        );
    }

    private void requireWorkflowDependencies() {
        if (repository == null
                || treatyImportService == null
                || checkpointImportService == null
                || scoreImportService == null
                || scoreBootstrapService == null
                || flagImportService == null
                || flagBootstrapService == null) {
            throw new IllegalStateException("Runtime bootstrap workflow dependencies are unavailable in the file-only bootstrap service constructor.");
        }
    }

    private ValidationResult buildValidationResult() throws IOException {
        boolean importRootExists = Files.isDirectory(importRoot);
        List<String> missingRequiredEntries = new ArrayList<>();
        Map<String, Integer> requiredGroupCounts = new LinkedHashMap<>();

        for (Path requiredFile : REQUIRED_FILES) {
            if (!Files.isRegularFile(importRoot.resolve(requiredFile))) {
                missingRequiredEntries.add(display(requiredFile));
            }
        }

        int flagCacheFileCount = 0;
        for (Path requiredDirectory : REQUIRED_DIRECTORIES) {
            Path stagedDirectory = importRoot.resolve(requiredDirectory);
            if (!Files.isDirectory(stagedDirectory)) {
                missingRequiredEntries.add(display(requiredDirectory));
                continue;
            }
            flagCacheFileCount += countRegularFiles(stagedDirectory);
        }

        for (PatternGroup requiredGroup : REQUIRED_GROUPS) {
            int count = findMatchingFiles(importRoot, requiredGroup).size();
            requiredGroupCounts.put(requiredGroup.display(), count);
            if (count == 0) {
                missingRequiredEntries.add(requiredGroup.display());
            }
        }

        int stagedFileCount = countRegularFiles(importRoot);
        int stagedDirectoryCount = countDirectories(importRoot);

        return new ValidationResult(
                importRoot,
                validateReportPath,
                importRootExists && missingRequiredEntries.isEmpty(),
                importRootExists,
                stagedFileCount,
                stagedDirectoryCount,
                flagCacheFileCount,
                List.copyOf(missingRequiredEntries),
                Map.copyOf(requiredGroupCounts)
        );
    }

    private SourceImportPlan buildImportPlan(Path sourceRoot) throws IOException {
        List<String> missingRequiredEntries = new ArrayList<>();
        List<Path> optionalFiles = new ArrayList<>();
        Map<String, List<Path>> requiredGroupMatches = new LinkedHashMap<>();

        for (Path requiredFile : REQUIRED_FILES) {
            if (!Files.isRegularFile(sourceRoot.resolve(requiredFile))) {
                missingRequiredEntries.add(display(requiredFile));
            }
        }

        for (Path optionalFile : OPTIONAL_FILES) {
            if (Files.isRegularFile(sourceRoot.resolve(optionalFile))) {
                optionalFiles.add(optionalFile);
            }
        }

        for (Path requiredDirectory : REQUIRED_DIRECTORIES) {
            if (!Files.isDirectory(sourceRoot.resolve(requiredDirectory))) {
                missingRequiredEntries.add(display(requiredDirectory));
            }
        }

        for (PatternGroup requiredGroup : REQUIRED_GROUPS) {
            List<Path> matches = findMatchingFiles(sourceRoot, requiredGroup);
            requiredGroupMatches.put(requiredGroup.display(), matches);
            if (matches.isEmpty()) {
                missingRequiredEntries.add(requiredGroup.display());
            }
        }

        return new SourceImportPlan(
                List.copyOf(REQUIRED_FILES),
                List.copyOf(optionalFiles),
                List.copyOf(REQUIRED_DIRECTORIES),
                Map.copyOf(requiredGroupMatches),
                List.copyOf(missingRequiredEntries)
        );
    }

    private void copyRelativeFile(Path sourceRoot, Path relativeFile, CopySummary summary) throws IOException {
        Path sourcePath = sourceRoot.resolve(relativeFile);
        Path targetPath = importRoot.resolve(relativeFile);
        if (targetPath.getParent() != null) {
            Files.createDirectories(targetPath.getParent());
        }
        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        summary.recordCopy(relativeFile, Files.size(sourcePath));
    }

    private void copyDirectory(Path sourceRoot, Path relativeDirectory, CopySummary summary) throws IOException {
        Path sourceDirectory = sourceRoot.resolve(relativeDirectory);
        try (Stream<Path> stream = Files.walk(sourceDirectory)) {
            for (Path sourcePath : stream.sorted().toList()) {
                Path relativePath = sourceRoot.relativize(sourcePath);
                Path targetPath = importRoot.resolve(relativePath);
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                    continue;
                }
                if (targetPath.getParent() != null) {
                    Files.createDirectories(targetPath.getParent());
                }
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                summary.recordCopy(relativePath, Files.size(sourcePath));
            }
        }
    }

    private static List<Path> findMatchingFiles(Path root, PatternGroup group) throws IOException {
        Path directory = root.resolve(group.directory());
        if (!Files.isDirectory(directory)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> group.matches(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(root::relativize)
                    .toList();
        }
    }

    private static int countRegularFiles(Path root) throws IOException {
        if (!Files.exists(root)) {
            return 0;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return Math.toIntExact(stream.filter(Files::isRegularFile).count());
        }
    }

    private static int countDirectories(Path root) throws IOException {
        if (!Files.exists(root)) {
            return 0;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return Math.max(0, Math.toIntExact(stream.filter(Files::isDirectory).count()) - 1);
        }
    }

    private static void deleteKnownPath(Path path, List<Path> deletedPaths, List<Path> missingPaths) throws IOException {
        if (!Files.exists(path)) {
            missingPaths.add(path);
            return;
        }

        if (Files.isDirectory(path)) {
            deleteRecursively(path);
        } else {
            Files.deleteIfExists(path);
        }
        deletedPaths.add(path);
    }

    private static void deleteRecursively(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteIfExists(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.deleteIfExists(path);
        }
    }

    private static Path normalizePath(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static Path resolveSourceRoot(Path sourceRoot) throws IOException {
        Path resolved = normalizePath(sourceRoot == null ? DEFAULT_LEGACY_SOURCE_ROOT : sourceRoot);
        if (!Files.isDirectory(resolved)) {
            throw new IOException("Treaty-vis legacy source root does not exist: " + resolved);
        }
        return resolved;
    }

    private static String display(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static Map<String, Integer> toGroupCountMap(Map<String, List<Path>> groupMatches) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<String, List<Path>> entry : groupMatches.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().size());
        }
        return Map.copyOf(counts);
    }

    public record ImportResult(
            Path sourceRoot,
            Path importRoot,
            int copiedFileCount,
            long copiedBytes,
            int flagCacheFileCount,
            Map<String, Integer> requiredGroupCounts
    ) {
    }

    public record ValidationResult(
            Path importRoot,
            Path reportPath,
            boolean valid,
            boolean importRootExists,
            int stagedFileCount,
            int stagedDirectoryCount,
            int flagCacheFileCount,
            List<String> missingRequiredEntries,
            Map<String, Integer> requiredGroupCounts
    ) {
    }

    public record ResetResult(
            List<Path> deletedPaths,
            List<Path> missingPaths
    ) {
    }

        public record RuntimeBootstrapImportResult(
            ImportResult stagedImport,
            TreatyVisRuntimeLegacyTreatyImportService.TreatyImportResult treatyImport,
            TreatyVisRuntimeLegacyTreatyCheckpointImportService.TreatyCheckpointImportResult checkpointImport,
            TreatyVisRuntimeLegacyScoreImportService.ScoreImportResult scoreImport,
            TreatyVisRuntimeBootstrapScoreStateService.ScoreBootstrapResult scoreBootstrap,
            TreatyVisRuntimeLegacyFlagImportService.FlagImportResult flagImport,
            TreatyVisRuntimeBootstrapFlagStateService.FlagBootstrapResult flagBootstrap
        ) {
        }

        public record RuntimeBootstrapResetResult(
            ResetResult artifactReset,
            TreatyVisRuntimeLegacyTreatyImportService.TreatyResetResult treatyReset,
            TreatyVisRuntimeLegacyTreatyCheckpointImportService.TreatyCheckpointResetResult checkpointReset,
            TreatyVisRuntimeLegacyScoreImportService.ScoreResetResult scoreReset,
            TreatyVisRuntimeBootstrapScoreStateService.ScoreBootstrapResetResult scoreBootstrapReset,
            TreatyVisRuntimeLegacyFlagImportService.FlagResetResult flagReset
        ) {
        }

    private record PatternGroup(Path directory, String glob) {
        boolean matches(String fileName) {
            String regex = glob.replace(".", "\\.").replace("*", ".*").replace("?", ".");
            return fileName.matches(regex);
        }

        String display() {
            return TreatyVisRuntimeBootstrapService.display(directory) + "/" + glob;
        }
    }

    private record SourceImportPlan(
            List<Path> requiredFiles,
            List<Path> optionalFiles,
            List<Path> requiredDirectories,
            Map<String, List<Path>> requiredGroupMatches,
            List<String> missingRequiredEntries
    ) {
    }

    private static final class CopySummary {
        private int copiedFileCount;
        private long copiedBytes;
        private int flagCacheFileCount;

        private void recordCopy(Path relativePath, long bytes) {
            copiedFileCount += 1;
            copiedBytes += bytes;
            if (relativePath.startsWith(Path.of("data", "flag_cache"))) {
                flagCacheFileCount += 1;
            }
        }
    }
}
