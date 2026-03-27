package link.locutus.discord.db.entities;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.db.bank.TaxBracketLookup;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.pnw.AllianceList;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TaxBracketLookupTest {
    @Test
    void resolvesAllianceAndMembersFromExplicitLookup() {
        StubAlliance alliance = new StubAlliance(10, "Rose", Map.of());
        DBNation member = nation(77, "Alpha", 10, 123);
        TaxBracketLookup lookup = lookup(alliance, Set.of(member));

        TaxBracket bracket = new TaxBracket(123, -1, "Entry", 25, 25, 0L).withLookup(lookup);

        assertEquals(10, bracket.getAlliance_id());
        assertSame(alliance, bracket.getAlliance());
        assertEquals(Set.of(member), bracket.getNations());
    }

    @Test
    void allianceListAttachesLookupToFetchedBrackets() {
        TaxBracket detached = new TaxBracket(123, -1, "Entry", 25, 25, 0L);
        StubAlliance alliance = new StubAlliance(10, "Rose", Map.of(123, detached));
        DBNation member = nation(77, "Alpha", 10, 123);
        TaxBracketLookup lookup = lookup(alliance, Set.of(member));

        TaxBracket bracket = new AllianceList(Set.of(10)).getTaxBrackets(lookup, 0L).get(123);

        assertSame(detached, bracket);
        assertEquals(10, bracket.getAlliance_id());
        assertSame(alliance, bracket.getAlliance());
        assertEquals(Set.of(member), bracket.getNations());
    }

    private static TaxBracketLookup lookup(DBAlliance alliance, Set<DBNation> nations) {
        return new TaxBracketLookup() {
            @Override
            public Set<DBNation> getNationsByAlliance(Set<Integer> alliances) {
                return alliances.contains(alliance.getId()) ? nations : Collections.emptySet();
            }

            @Override
            public DBAlliance getAlliance(int allianceId) {
                return allianceId == alliance.getId() ? alliance : null;
            }

            @Override
            public Set<DBNation> getNationsByBracket(int taxId) {
                return taxId == 123 ? nations : Collections.emptySet();
            }
        };
    }

    private static DBNation nation(int id, String name, int allianceId, int taxId) {
        SimpleDBNation nation = new SimpleDBNation(new DBNationData());
        nation.setNation_id(id);
        nation.setNation(name);
        nation.setAlliance_id(allianceId);
        nation.setTax_id(taxId);
        return nation;
    }

    private static final class StubAlliance extends DBAlliance {
        private final Map<Integer, TaxBracket> brackets;

        private StubAlliance(int id, String name, Map<Integer, TaxBracket> brackets) {
            super(id, name, "", "", "", "", "", 0L, NationColor.GRAY,
                    (Int2ObjectOpenHashMap<byte[]>) null);
            this.brackets = new LinkedHashMap<>(brackets);
        }

        @Override
        public synchronized Map<Integer, TaxBracket> getTaxBrackets(long cacheFor) {
            return brackets;
        }
    }
}