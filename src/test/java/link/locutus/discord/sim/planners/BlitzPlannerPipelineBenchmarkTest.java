package link.locutus.discord.sim.planners;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlitzPlannerPipelineBenchmarkTest {
    @Test
    void liveAllianceFixtureRequiresBothSides() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> BlitzPlannerPipelineBenchmark.BenchmarkConfig.parse(new String[]{"--attackerAlliances=Singularity"})
        );

        assertTrue(error.getMessage().contains("must be provided together"));
    }

    @Test
    void liveAllianceFixtureDefaultsToReplaySlice() {
        BlitzPlannerPipelineBenchmark.BenchmarkConfig config = BlitzPlannerPipelineBenchmark.BenchmarkConfig.parse(
                new String[]{"--attackerAlliances=Singularity", "--defenderAlliances=The Knights Radiant"}
        );

        assertTrue(config.hasLiveAllianceFixture());
        assertEquals(1, config.sliceCliNames().size());
        assertEquals("replay", config.sliceCliNames().get(0));
    }

    @Test
    void liveAllianceFixtureHonorsExplicitSlices() {
        BlitzPlannerPipelineBenchmark.BenchmarkConfig config = BlitzPlannerPipelineBenchmark.BenchmarkConfig.parse(
                new String[]{
                        "--attackerAlliances=Singularity",
                        "--defenderAlliances=The Knights Radiant",
                        "--slices=replay,blitz"
                }
        );

        assertEquals(2, config.sliceCliNames().size());
        assertEquals("replay", config.sliceCliNames().get(0));
        assertEquals("blitz", config.sliceCliNames().get(1));
    }

    @Test
    void objectiveCanBeSelectedByNameOrOrdinal() {
        BlitzPlannerPipelineBenchmark.BenchmarkConfig named = BlitzPlannerPipelineBenchmark.BenchmarkConfig.parse(
                new String[]{"--objective=CONTROL"}
        );
        BlitzPlannerPipelineBenchmark.BenchmarkConfig ordinal = BlitzPlannerPipelineBenchmark.BenchmarkConfig.parse(
                new String[]{"--objective=4"}
        );

        assertEquals("CONTROL", named.objectiveName());
        assertEquals("BALANCED", ordinal.objectiveName());
    }
}
