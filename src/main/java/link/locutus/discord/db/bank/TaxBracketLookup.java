package link.locutus.discord.db.bank;

import link.locutus.discord.db.AllianceLookup;
import link.locutus.discord.db.entities.DBNation;

import java.util.Set;

/**
 * Narrow lookup surface for tax bracket helper objects.
 */
public interface TaxBracketLookup extends AllianceLookup {
    Set<DBNation> getNationsByBracket(int taxId);

    default int getAllianceIdByTaxId(int taxId) {
        if (taxId == 0) {
            return 0;
        }
        Set<DBNation> nations = getNationsByBracket(taxId);
        if (nations.isEmpty()) {
            return 0;
        }
        return nations.iterator().next().getAlliance_id();
    }
}