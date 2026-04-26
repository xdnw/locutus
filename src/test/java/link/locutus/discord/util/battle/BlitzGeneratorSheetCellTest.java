package link.locutus.discord.util.battle;

import link.locutus.discord.apiv1.enums.WarType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BlitzGeneratorSheetCellTest {

    @Test
    void parsesAttackerCellWithTypeSuffix() {
        BlitzGenerator.BlitzSheetAttackerCell cell = BlitzGenerator.parseAttackerCell(
                "=HYPERLINK(\"https://politicsandwar.com/nation/id=101\",\"Alpha\")|type:Attrition");

        assertEquals("=HYPERLINK(\"https://politicsandwar.com/nation/id=101\",\"Alpha\")", cell.nationToken());
        assertEquals(WarType.ATT, cell.warType());
    }

    @Test
    void attackerOptionCanOwnTheParseableNationToken() {
        BlitzGenerator.BlitzSheetAttackerCell cell = BlitzGenerator.parseAttackerCell(
                "type:ordinary|att:https://politicsandwar.com/nation/id=102");

        assertEquals("https://politicsandwar.com/nation/id=102", cell.nationToken());
        assertEquals(WarType.ORD, cell.warType());
    }

    @Test
    void malformedTypeDoesNotHideTheAttackerToken() {
        BlitzGenerator.BlitzSheetAttackerCell cell = BlitzGenerator.parseAttackerCell("Alpha|type:not-a-type");

        assertEquals("Alpha", cell.nationToken());
        assertNull(cell.warType());
    }
}
