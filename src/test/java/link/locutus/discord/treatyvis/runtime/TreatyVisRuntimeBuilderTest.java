package link.locutus.discord.treatyvis.runtime;

import link.locutus.discord.apiv1.enums.TreatyType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TreatyVisRuntimeBuilderTest {
    private final TreatyVisRuntimeBuilder builder = new TreatyVisRuntimeBuilder();

    @Test
    void buildsCurrentStatePayloadWithStableDictionaries() {
        TreatyVisRuntimePayload payload = builder.build(new TreatyVisRuntimeInput(
                20_089,
                100,
                List.of(
                        new TreatyVisRuntimeInput.Alliance(4729, "Rose"),
                        new TreatyVisRuntimeInput.Alliance(881, "Guardian"),
                        new TreatyVisRuntimeInput.Alliance(9123, "Eclipse")
                ),
                List.of(
                        new TreatyVisRuntimeInput.TreatyEdge(4729, 881, TreatyType.MDP),
                        new TreatyVisRuntimeInput.TreatyEdge(4729, 9123, TreatyType.MDOAP)
                ),
                List.of(
                        new TreatyVisRuntimeInput.AllianceFlag(4729, 2),
                        new TreatyVisRuntimeInput.AllianceFlag(881, 1)
                ),
                List.of(
                        new TreatyVisRuntimeInput.AllianceScore(4729, 1_823_400),
                        new TreatyVisRuntimeInput.AllianceScore(881, 1_752_100)
                )
        ));

        assertEquals(TreatyVisRuntimeBuilder.PAYLOAD_VERSION, payload.version());
        assertEquals(20_089, payload.baseDay());
        assertEquals(100, payload.scoreQuantization());

        assertEquals(List.of(881, 4729, 9123), payload.alliances().ids());
        assertEquals(List.of("Guardian", "Rose", "Eclipse"), payload.alliances().names());
        assertEquals(List.of("mdp", "mdoap"), payload.treatyTypes());

        assertEquals(List.of(1, 1), payload.edges().fromAllianceIndexes());
        assertEquals(List.of(0, 2), payload.edges().toAllianceIndexes());
        assertEquals(List.of(0, 1), payload.edges().treatyTypeIndexes());
        assertEquals(List.of(0, 1), payload.initialState().activeEdgeIndexes());

        assertEquals(List.of(0, 1), payload.initialState().flagAllianceIndexes());
        assertEquals(List.of(1, 2), payload.initialState().flagIndexes());

        assertEquals(List.of(1, 0), payload.initialState().scoreAllianceIndexes());
        assertEquals(List.of(1_823_400, 1_752_100), payload.initialState().scoreQuantized());

        assertEquals(List.of(), payload.treatyChanges().days());
        assertEquals(List.of(0), payload.treatyChanges().rowOffsets());
        assertEquals(List.of(), payload.flagChanges().days());
        assertEquals(List.of(0), payload.flagChanges().rowOffsets());
        assertEquals(List.of(), payload.scoreSnapshots().days());
        assertEquals(List.of(0), payload.scoreSnapshots().rowOffsets());
    }

    @Test
        void buildsReplayLanesAndRespectsExplicitFlagAtlasIndexes() {
        TreatyVisRuntimePayload payload = builder.build(new TreatyVisRuntimeInput(
                20_089,
                100,
                List.of(
                        new TreatyVisRuntimeInput.Alliance(4729, "Rose"),
                        new TreatyVisRuntimeInput.Alliance(881, "Guardian"),
                        new TreatyVisRuntimeInput.Alliance(9123, "Eclipse")
                ),
                List.of(
                        new TreatyVisRuntimeInput.TreatyEdge(4729, 881, TreatyType.MDP)
                ),
                List.of(
                        new TreatyVisRuntimeInput.AllianceFlag(4729, 1)
                ),
                List.of(
                        new TreatyVisRuntimeInput.AllianceScore(4729, 1_823_400)
                ),
                List.of(
                        new TreatyVisRuntimeInput.TreatyChange(0, new TreatyVisRuntimeInput.TreatyEdge(4729, 9123, TreatyType.MDOAP), 1),
                        new TreatyVisRuntimeInput.TreatyChange(2, new TreatyVisRuntimeInput.TreatyEdge(4729, 881, TreatyType.MDP), 4)
                ),
                List.of(
                        new TreatyVisRuntimeInput.FlagChange(1, 881, 0),
                        new TreatyVisRuntimeInput.FlagChange(3, 9123, 2)
                ),
                List.of(
                        new TreatyVisRuntimeInput.ScoreSnapshot(1, List.of(
                                new TreatyVisRuntimeInput.AllianceScore(4729, 1_830_000),
                                new TreatyVisRuntimeInput.AllianceScore(881, 1_752_100)
                        ))
                )
        ));

        assertEquals(List.of(0, 2), payload.treatyChanges().days());
        assertEquals(List.of(0, 1, 2), payload.treatyChanges().rowOffsets());
        assertEquals(List.of(1, 0), payload.treatyChanges().edgeIndexes());
        assertEquals(List.of(1, 4), payload.treatyChanges().actions());
        assertEquals(List.of(1, 3), payload.flagChanges().days());
        assertEquals(List.of(0, 1, 2), payload.flagChanges().rowOffsets());
        assertEquals(List.of(0, 2), payload.flagChanges().allianceIndexes());
        assertEquals(List.of(0, 2), payload.flagChanges().flagIndexes());
        assertEquals(List.of(1), payload.scoreSnapshots().days());
        assertEquals(List.of(0, 2), payload.scoreSnapshots().rowOffsets());
        assertEquals(List.of(1, 0), payload.scoreSnapshots().allianceIndexes());
        assertEquals(List.of(1_830_000, 1_752_100), payload.scoreSnapshots().scoresQuantized());
    }

        @Test
        void leavesMissingAllianceNamesNull() {
                TreatyVisRuntimePayload payload = builder.build(new TreatyVisRuntimeInput(
                                20_089,
                                100,
                                List.of(
                                                new TreatyVisRuntimeInput.Alliance(4729, null)
                                ),
                                List.of(
                                                new TreatyVisRuntimeInput.TreatyEdge(4729, 881, TreatyType.MDP)
                                ),
                                List.of(),
                                List.of()
                ));

                assertEquals(List.of(881, 4729), payload.alliances().ids());
                assertNull(payload.alliances().names().get(0));
                assertNull(payload.alliances().names().get(1));
        }
}