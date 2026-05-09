package link.locutus.discord.treatyvis.runtime;

import link.locutus.discord.db.DBMainV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class TreatyVisRuntimeRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void resetBootstrapStateClearsImportedMarkersAndRanges() throws Exception {
        try (DBMainV2 db = new DBMainV2(tempDir.resolve("runtime.db").toFile())) {
            TreatyVisRuntimeRepository repository = new TreatyVisRuntimeRepository(db);
            repository.markHistoricalTreatyImportComplete(10, 20);
            repository.markHistoricalScoreImportComplete(30, 40);
            repository.markHistoricalFlagImportComplete(50, 60);
            repository.setValidationComplete(true);

            repository.resetBootstrapState();

            TreatyVisRuntimeRepository.RuntimeBootstrapState state = repository.loadBootstrapState();
            assertFalse(state.treatyImportComplete());
            assertNull(state.importedTreatyMinDay());
            assertNull(state.importedTreatyMaxDay());
            assertFalse(state.scoreImportComplete());
            assertNull(state.importedScoreMinDay());
            assertNull(state.importedScoreMaxDay());
            assertFalse(state.flagImportComplete());
            assertNull(state.importedFlagMinDay());
            assertNull(state.importedFlagMaxDay());
            assertFalse(state.validationComplete());
        }
    }
}
