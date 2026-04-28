package link.locutus.discord.web.jooby.adapter;

import link.locutus.discord.web.jooby.PageHandler;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsEndpointGeneratorTest {
    @Test
    void defaultEndpointGenerationWritesToReactAndSvelteTargets() throws Exception {
        PageHandler handler = TsEndpointGenerator.createStandalonePageHandler();
        Path tempDir = Files.createTempDirectory("ts-endpoint-generator-");

        try {
            TsEndpointGenerator.writeFiles(handler, null, null, true, false, tempDir.toFile());

            Path reactEndpoint = defaultTarget(tempDir, "../lc_cmd_react/src/lib/endpoints.ts");
            Path svelteEndpoint = defaultTarget(tempDir, "../lc_stats_svelte/src/lib/endpoints.ts");

            assertTrue(Files.exists(reactEndpoint));
            assertTrue(Files.exists(svelteEndpoint));
            assertTrue(Files.readString(reactEndpoint).contains("export const ENDPOINTS = ["));
            assertEquals(Files.readString(reactEndpoint), Files.readString(svelteEndpoint));
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    void defaultCommandsTargetRemainsReactOnly() {
        File outputDir = TsEndpointGenerator.resolveCommandOutputDir(null, new File("sandbox"));

        assertEquals(Path.of("sandbox", "../lc_cmd_react/src").normalize(), outputDir.toPath().normalize());
    }

    @Test
    void explicitOutputDirDisablesDefaultEndpointFanOut() {
        File explicitOutputDir = new File("custom-output");

        assertEquals(List.of(explicitOutputDir), TsEndpointGenerator.resolveEndpointOutputDirs(explicitOutputDir, new File("sandbox")));
    }

    private static Path defaultTarget(Path tempDir, String relativePath) {
        return tempDir.resolve(relativePath).normalize();
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}