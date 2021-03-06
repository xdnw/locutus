package link.locutus.discord.db.entities;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.config.Settings;

import java.util.*;

public class TaxBracket {
    public int taxId;
    public int allianceId;
    public int nations;
    public String name;
    public int moneyRate;
    public int rssRate;

    public TaxBracket(int allianceId, Map<String, String> data) {
        name = data.get("Bracket Name");
        String[] nameSplit = name.substring(0, name.length() - 1).split("#");
        name = name.split(" \\(#")[0];

        taxId = Integer.parseInt(nameSplit[nameSplit.length - 1]);
        this.allianceId = allianceId;
        nations = Integer.parseInt(data.get("Nations").split(" ")[0].replace(",", ""));
        moneyRate = Integer.parseInt(data.get("Money Tax Rate").replaceAll("%", ""));
        rssRate = Integer.parseInt(data.get("Resource Tax Rate").replaceAll("%", ""));
    }

    public TaxBracket(int taxId, int allianceId, int nations, String name, int moneyRate, int rssRate) {
        this.taxId = taxId;
        this.allianceId = allianceId;
        this.nations = nations;
        this.name = name;
        this.moneyRate = moneyRate;
        this.rssRate = rssRate;
    }

    public TaxBracket(com.politicsandwar.graphql.model.TaxBracket bracket) {
        this.taxId = bracket.getId();
        this.allianceId = bracket.getAlliance_id();
        this.nations = Locutus.imp().getNationDB().getNationsMatching(f -> f.getTax_id() == taxId).size();
        this.name = bracket.getBracket_name();
        this.moneyRate = bracket.getTax_rate();
        this.rssRate = bracket.getResource_tax_rate();
    }

    public Set<DBNation> getNations() throws Exception {
        if (nations == 0) return new HashSet<>();
        return Locutus.imp().getNationDB().getNationsMatching(f -> f.getTax_id() == taxId);
    }

    public String getUrl() {
        return String.format("" + Settings.INSTANCE.PNW_URL() + "/index.php?id=15&tax_id=%s", taxId);
    }

    @Override
    public String toString() {
        return name + " - " + "#" + taxId + " (" + moneyRate + "/" + rssRate + ")";
    }

    public TaxRate getTaxRate() {
        return new TaxRate(moneyRate, rssRate);
    }
}
