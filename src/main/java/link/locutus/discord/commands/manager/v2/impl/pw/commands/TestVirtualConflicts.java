package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.config.Settings;
import link.locutus.discord.db.conflict.Conflict;
import link.locutus.discord.db.conflict.ConflictUtil;
import link.locutus.discord.db.conflict.VirtualConflictInspector;
import link.locutus.discord.db.conflict.VirtualConflictStorageManager;
import link.locutus.discord.web.jooby.S3CompatibleStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TestVirtualConflicts {
    private static final String SAMPLE_VIRTUAL_CONFLICT_PATH = "n/189573/069e6027-e3fa-4dea-94b1-6d7f9e25cbf2";
    private static final boolean INSPECT_MODE = false;
    private static final boolean PURGE_MODE = false;
    private static final boolean PURGE_DRY_RUN = true;
    private static final Integer NATION_FILTER = null;
    private static final Set<String> INSPECT_IDS = Set.of(SAMPLE_VIRTUAL_CONFLICT_PATH);
        private static final Set<String> PURGE_EXPLICIT_IDS = Set.of(
            "n/189573/069e6027-e3fa-4dea-94b1-6d7f9e25cbf2",
            "n/189573/3794a193-7231-4bfa-a906-11da37a2533e",
            "n/189573/66be224a-e3c7-48d7-bc5b-724ba5a0b541",
            "n/189573/7a0fb565-afeb-443e-9bfc-6de0fcc581f2",
            "n/189573/915c15f7-ca83-4177-ad3d-b55bb7483318",
            "n/189573/ae465743-e97a-4b5b-b1e5-4eac70821b50",
            "n/189573/bc2359bd-c1ed-4ac9-85b7-493b93a2ce72",
            "n/189573/ced6ec26-c199-4270-a697-712453bcfcf3",
            "n/189573/d1c86f27-e270-4bb0-af15-61374a83a43a",
            "n/189573/d5e68246-932b-4067-beee-27336e6fd20b",
            "n/189573/f2789be1-7231-4614-b363-0206520d5470",
            "n/355939/213c0229-206e-4729-b641-5411dd3a03b7",
            "n/355939/4e833a07-caac-4e71-ba41-997b31f09444",
            "n/355939/96864878-411e-42de-a4a8-b6be8f326c8f",
            "n/355939/b1a7a0c4-4418-4819-ba2e-3d38e98d1560",
            "n/355939/eaa2c5ed-ac1d-4439-8984-dd6aed090ce8",
            "n/549844/2fc09ce1-66ae-4c15-89f6-6ffa1c0d7f8d",
            "n/549844/5e4120ed-6eb1-4422-b402-598224238e77",
            "n/549844/ea7e6832-8fe7-4316-8840-8e2442e1cdd7",
            "n/558646/3fe1c614-29b0-4953-a2e8-5d779a81c33f",
            "n/83628/19e26261-98d6-4f20-a894-beb524eb3d27",
            "n/83628/3e2e28f5-ea26-418d-83bc-56d595c7e089",
            "n/83628/51f24eb3-c2ac-44be-811a-5db3ba884b20",
            "n/83628/6b1d7047-f269-4a84-8624-b8a880b5a688",
            "n/83628/7589f870-bafb-4e91-92bb-febb4326565f",
            "n/83628/773f32de-ea8e-45ba-9d3d-525d1627e434",
            "n/83628/b6d72a82-6e37-472b-9498-65ac4e4080ee",
            "n/83628/d6064902-c4a9-4dad-849c-057f7d828090"
        );
    private static final boolean SAVE_INSPECTION_REPORT = true;
    private static final String INSPECTION_REPORT_DIR = "repro-out/virtual-conflict-inspector";

    public static void main(String[] args) {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());

        S3CompatibleStorage aws = S3CompatibleStorage.forAwsS3(
                Settings.INSTANCE.WEB.S3.ACCESS_KEY,
                Settings.INSTANCE.WEB.S3.SECRET_ACCESS_KEY,
                Settings.INSTANCE.WEB.S3.BUCKET,
                Settings.INSTANCE.WEB.S3.REGION,
                Settings.INSTANCE.WEB.S3.BASE_URL
        );

        try {
            Integer nationFilter = NATION_FILTER;
            String loadId = SAMPLE_VIRTUAL_CONFLICT_PATH;
            boolean inspectMode = INSPECT_MODE;
            boolean purgeMode = PURGE_MODE;
            boolean purgeDryRun = PURGE_DRY_RUN;
            Set<ConflictUtil.VirtualConflictId> explicitInspectIds = new LinkedHashSet<>();
            Set<ConflictUtil.VirtualConflictId> explicitPurgeIds = new LinkedHashSet<>();
            VirtualConflictStorageManager virtualConflictManager = new VirtualConflictStorageManager(aws);

            for (String inspectId : INSPECT_IDS) {
                if (inspectId != null && !inspectId.isBlank()) {
                    explicitInspectIds.add(ConflictUtil.parseVirtualConflictWebId(inspectId.trim()));
                }
            }

            for (String purgeId : PURGE_EXPLICIT_IDS) {
                if (purgeId != null && !purgeId.isBlank()) {
                    explicitPurgeIds.add(ConflictUtil.parseVirtualConflictWebId(purgeId.trim()));
                }
            }

            if (purgeMode) {
                runInvalidConflictPurge(aws, virtualConflictManager, nationFilter, explicitPurgeIds, purgeDryRun);
                return;
            }

            if (inspectMode) {
                List<ConflictUtil.VirtualConflictId> targets = new ArrayList<>(explicitInspectIds);

                if (nationFilter != null) {
                    targets.addAll(virtualConflictManager.listIds(nationFilter));
                } else if (targets.isEmpty() && loadId != null && !loadId.isBlank()) {
                    targets.add(ConflictUtil.parseVirtualConflictWebId(loadId));
                }

                if (targets.isEmpty()) {
                    System.out.println("No virtual conflict ids selected for inspection.");
                    return;
                }

                VirtualConflictInspector inspector = new VirtualConflictInspector();
                List<VirtualConflictInspector.InspectionResult> results = inspector.inspect(aws, targets);

                System.out.println("Inspected " + results.size() + " virtual conflict object(s):");
                for (VirtualConflictInspector.InspectionResult result : results) {
                    System.out.println("\n- id: " + result.id().toWebId());
                    System.out.println("  key: " + result.objectKey());
                    System.out.println("  classification: " + result.classification());
                    System.out.println("  compressedSize: " + result.compressedSize());
                    System.out.println("  decompressedSize: " + result.decompressedSize());
                    System.out.println("  firstByteHex: " + result.firstByteHex());
                    System.out.println("  first16BytesHex: " + result.first16BytesHex());
                    System.out.println("  rootType: " + result.rootType());
                    System.out.println("  rootPreview: " + result.rootPreview());
                    System.out.println("  structurePreview: " + result.structurePreview());
                    System.out.println("  presentTopLevelKeys: " + result.presentTopLevelKeys());
                    System.out.println("  missingTopLevelKeys: " + result.missingTopLevelKeys());
                    System.out.println("  extraTopLevelKeys: " + result.extraTopLevelKeys());
                    System.out.println("  lastModified: " + result.lastModifiedIsoOrUnknown());
                    if (result.error() != null) {
                        System.out.println("  error: " + result.error());
                    }
                }

                if (SAVE_INSPECTION_REPORT) {
                    saveInspectionReport(results);
                }
                return;
            }

            // List<ConflictUtil.VirtualConflictId> virtualConflictIds = virtualConflictManager.listIds(nationFilter);
            // System.out.println("Found " + virtualConflictIds.size() + " temporary conflicts"
            //         + (nationFilter == null ? "" : " for nation " + nationFilter) + ":");
            // for (ConflictUtil.VirtualConflictId id : virtualConflictIds) {
            //     System.out.println("- " + id + " -> " + Settings.INSTANCE.WEB.CONFLICTS.SITE + "/conflict?id=" + id);
            // }

            if (loadId != null) {
                String idToLoad = loadId;
                try {
                    ConflictUtil.VirtualConflictId typedId = ConflictUtil.parseVirtualConflictWebId(idToLoad);
                    Conflict loaded = virtualConflictManager.loadConflict(typedId);
                    System.out.println("\nLoaded temp conflict: " + loaded.getName());
                    System.out.println("- id: " + typedId.toWebId());
                    System.out.println("- startTurn: " + loaded.getStartTurn());
                    System.out.println("- endTurn: " + loaded.getEndTurn());
                    System.out.println("- coalition1: " + loaded.getCoalitionName(true) + " (" + loaded.getCoalition1().size() + " alliances)");
                    System.out.println("- coalition2: " + loaded.getCoalitionName(false) + " (" + loaded.getCoalition2().size() + " alliances)");
                } catch (Exception e) {
                    System.err.println("Failed to load conflict `" + idToLoad + "`: " + e.getMessage());
                }
            } else {
                System.out.println("No specific conflict id requested; listing only.");
            }
        } finally {
            aws.close();
        }
    }

    private static void runInvalidConflictPurge(S3CompatibleStorage aws,
                                                VirtualConflictStorageManager virtualConflictManager,
                                                Integer nationFilter,
                                                Set<ConflictUtil.VirtualConflictId> explicitPurgeIds,
                                                boolean dryRun) {
        List<ConflictUtil.VirtualConflictId> allIds = new ArrayList<>(virtualConflictManager.listIds(nationFilter));
        allIds.sort(Comparator.comparing(ConflictUtil.VirtualConflictId::toWebId));

        if (allIds.isEmpty()) {
            System.out.println("No virtual conflicts found" + (nationFilter == null ? "." : " for nation " + nationFilter + "."));
            return;
        }

        VirtualConflictInspector inspector = new VirtualConflictInspector();
        List<VirtualConflictInspector.InspectionResult> inspection = inspector.inspect(aws, allIds);
        Set<ConflictUtil.VirtualConflictId> invalidSet = new HashSet<>();
        for (VirtualConflictInspector.InspectionResult result : inspection) {
            if (result.isConfirmedUnrecoverable() || result.classification() == VirtualConflictInspector.Classification.MISSING) {
                invalidSet.add(result.id());
            }
        }

        List<ConflictUtil.VirtualConflictId> invalidIds = new ArrayList<>();
        List<ConflictUtil.VirtualConflictId> validIds = new ArrayList<>();
        for (ConflictUtil.VirtualConflictId id : allIds) {
            if (invalidSet.contains(id)) {
                invalidIds.add(id);
            } else {
                validIds.add(id);
            }
        }

        List<ConflictUtil.VirtualConflictId> toDelete = new ArrayList<>();
        List<ConflictUtil.VirtualConflictId> notDeletedInvalid = new ArrayList<>();
        for (ConflictUtil.VirtualConflictId invalidId : invalidIds) {
            if (explicitPurgeIds.contains(invalidId)) {
                toDelete.add(invalidId);
            } else {
                notDeletedInvalid.add(invalidId);
            }
        }

        List<ConflictUtil.VirtualConflictId> explicitButNotInvalid = new ArrayList<>();
        for (ConflictUtil.VirtualConflictId explicitId : explicitPurgeIds) {
            if (!invalidIds.contains(explicitId)) {
                explicitButNotInvalid.add(explicitId);
            }
        }
        explicitButNotInvalid.sort(Comparator.comparing(ConflictUtil.VirtualConflictId::toWebId));

        System.out.println("Purge mode summary:");
        System.out.println("- nationFilter: " + nationFilter);
        System.out.println("- dryRun: " + dryRun);
        System.out.println("- totalFound: " + allIds.size());
        System.out.println("- validCount: " + validIds.size());
        System.out.println("- invalidCount: " + invalidIds.size());
        System.out.println("- explicitPurgeCount: " + explicitPurgeIds.size());

        printIdList("Invalid ids selected for deletion", toDelete);
        printIdList("Invalid ids NOT being deleted", notDeletedInvalid);
        printIdList("Valid ids NOT being deleted", validIds);
        if (!explicitButNotInvalid.isEmpty()) {
            printIdList("Explicit ids ignored (not invalid in current scan)", explicitButNotInvalid);
        }

        if (dryRun) {
            System.out.println("\nDry-run enabled. No deletions performed.");
            System.out.println("Set PURGE_DRY_RUN=false to execute, keeping PURGE_EXPLICIT_IDS as the explicit allowlist.");
            return;
        }

        if (explicitPurgeIds.isEmpty()) {
            System.out.println("\nRefusing to execute purge: PURGE_EXPLICIT_IDS is empty.");
            System.out.println("Provide an explicit id deletion list and re-run.");
            return;
        }

        if (toDelete.isEmpty()) {
            System.out.println("\nNo invalid ids matched PURGE_EXPLICIT_IDS. Nothing deleted.");
            return;
        }

        int deleted = 0;
        List<String> failed = new ArrayList<>();
        for (ConflictUtil.VirtualConflictId id : toDelete) {
            try {
                virtualConflictManager.deleteConflict(id);
                deleted++;
            } catch (Exception e) {
                failed.add(id.toWebId() + " => " + e.getMessage());
            }
        }

        System.out.println("\nPurge execution finished:");
        System.out.println("- requestedDeletes: " + toDelete.size());
        System.out.println("- deleted: " + deleted);
        System.out.println("- failed: " + failed.size());
        if (!failed.isEmpty()) {
            System.out.println("Failed deletions:");
            for (String failure : failed) {
                System.out.println("- " + failure);
            }
        }
    }

    private static void printIdList(String title, List<ConflictUtil.VirtualConflictId> ids) {
        System.out.println("\n" + title + " (" + ids.size() + "):");
        if (ids.isEmpty()) {
            System.out.println("- none");
            return;
        }
        for (ConflictUtil.VirtualConflictId id : ids) {
            System.out.println("- " + id.toWebId());
        }
    }

    private static void saveInspectionReport(List<VirtualConflictInspector.InspectionResult> results) {
        StringBuilder out = new StringBuilder();
        out.append("GeneratedAt: ").append(Instant.now()).append('\n');
        out.append("Count: ").append(results.size()).append("\n\n");

        for (VirtualConflictInspector.InspectionResult result : results) {
            out.append("id: ").append(result.id().toWebId()).append('\n');
            out.append("key: ").append(result.objectKey()).append('\n');
            out.append("classification: ").append(result.classification()).append('\n');
            out.append("compressedSize: ").append(result.compressedSize()).append('\n');
            out.append("decompressedSize: ").append(result.decompressedSize()).append('\n');
            out.append("firstByteHex: ").append(result.firstByteHex()).append('\n');
            out.append("first16BytesHex: ").append(result.first16BytesHex()).append('\n');
            out.append("rootType: ").append(result.rootType()).append('\n');
            out.append("rootPreview: ").append(result.rootPreview()).append('\n');
            out.append("structurePreview: ").append(result.structurePreview()).append('\n');
            out.append("presentTopLevelKeys: ").append(result.presentTopLevelKeys()).append('\n');
            out.append("missingTopLevelKeys: ").append(result.missingTopLevelKeys()).append('\n');
            out.append("extraTopLevelKeys: ").append(result.extraTopLevelKeys()).append('\n');
            out.append("lastModified: ").append(result.lastModifiedIsoOrUnknown()).append('\n');
            out.append("error: ").append(result.error()).append("\n\n");
        }

        try {
            Path outputDir = Path.of(INSPECTION_REPORT_DIR);
            Files.createDirectories(outputDir);
            Path outputPath = outputDir.resolve("inspection-" + Instant.now().toEpochMilli() + ".txt");
            Files.writeString(outputPath, out.toString());
            System.out.println("\nSaved inspection report: " + outputPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save inspection report: " + e.getMessage());
        }
    }
}
