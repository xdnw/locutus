package link.locutus.discord.db.bank;

import link.locutus.discord.Locutus;

/**
 * Transitional access to the live nation-db tax bracket lookup for older callers.
 */
public final class TaxBracketLookups {
    private TaxBracketLookups() {
    }

    public static TaxBracketLookup liveNationDb() {
        return Locutus.imp().getNationDB();
    }
}