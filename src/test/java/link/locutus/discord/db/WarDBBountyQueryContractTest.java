package link.locutus.discord.db;

import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.db.entities.DBBounty;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WarDBBountyQueryContractTest {
    @Test
    void bountyLookupsAreKeyedByNationIdAndScopedByCandidateIds() throws Exception {
        String dbName = "war-bounty-contract-" + System.nanoTime();
        File dbFile = null;

        try (WarDB warDb = new WarDB(dbName)) {
            dbFile = warDb.getFile();
            insertBounty(warDb, 1, 1_000L, 77, WarType.RAID, 50_000L);
            insertBounty(warDb, 2, 2_000L, 88, WarType.ORD, 60_000L);
            insertBounty(warDb, 3, 3_000L, 77, WarType.ATT, 70_000L);

            Map<Integer, List<DBBounty>> grouped = warDb.getBountiesByNation();
            assertEquals(Set.of(77, 88), grouped.keySet());
            assertEquals(List.of(3, 1), grouped.get(77).stream().map(DBBounty::getId).toList());
            assertEquals(List.of(2), grouped.get(88).stream().map(DBBounty::getId).toList());

            Map<Integer, List<DBBounty>> scoped = warDb.getBountiesByNationIds(Set.of(77));
            assertEquals(Set.of(77), scoped.keySet());
            assertEquals(List.of(3, 1), scoped.get(77).stream().map(DBBounty::getId).toList());
        } finally {
            deleteSqliteFiles(dbFile);
        }
    }

    private static void insertBounty(WarDB warDb, int id, long date, int nationId, WarType type, long amount) {
        warDb.update("INSERT INTO `BOUNTIES_V3`(`id`, `date`, `nation_id`, `posted_by`, `attack_type`, `amount`) VALUES(?, ?, ?, ?, ?, ?)",
                id,
                date,
                nationId,
                0,
                type.ordinal(),
                amount);
    }

    private static void deleteSqliteFiles(File dbFile) throws Exception {
        if (dbFile == null) {
            return;
        }
        deleteIfExists(dbFile);
        deleteIfExists(new File(dbFile.getPath() + "-wal"));
        deleteIfExists(new File(dbFile.getPath() + "-shm"));
    }

    private static void deleteIfExists(File file) throws Exception {
        if (file.exists()) {
            Files.delete(file.toPath());
        }
    }
}