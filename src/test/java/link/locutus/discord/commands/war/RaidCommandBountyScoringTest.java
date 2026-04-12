package link.locutus.discord.commands.war;

import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.db.entities.DBBounty;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RaidCommandBountyScoringTest {
    @Test
    void appliesTypeSpecificBountiesAgainstTheirWeightedTracks() {
        DBNation attacker = nationWithNukes(0);

        double result = RaidCommand.applyBounties(attacker, 1_000d, List.of(
                new DBBounty(1, 1_000L, 77, 0, WarType.RAID, 50L),
                new DBBounty(2, 2_000L, 77, 0, WarType.ORD, 700L)
        ));

        assertEquals(1_200d, result);
    }

    @Test
    void addsNuclearBountiesToEveryNonNuclearTrackWhenAttackerHasNukes() {
        DBNation attacker = nationWithNukes(1);

        double result = RaidCommand.applyBounties(attacker, 10_000d, List.of(
                new DBBounty(1, 1_000L, 77, 0, WarType.NUCLEAR, 1_200_000L)
        ));

        assertEquals(210_000d, result);
    }

    private static DBNation nationWithNukes(int nukes) {
        SimpleDBNation nation = new SimpleDBNation(new DBNationData());
        nation.setNukes(nukes);
        return nation;
    }
}