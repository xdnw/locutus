package link.locutus.discord.db.entities;

public class WebTaxBracket {
    public final int taxId;
    public final long dateFetched;
    public final int allianceId;
    public final String name;
    public final int moneyRate;
    public final int rssRate;

    public WebTaxBracket(TaxBracket bracket) {
        this.taxId = bracket.taxId;
        this.dateFetched = bracket.dateFetched;
        this.allianceId = bracket.getAlliance_id();
        this.name = bracket.getName();
        this.moneyRate = bracket.moneyRate;
        this.rssRate = bracket.rssRate;
    }
}
