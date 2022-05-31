package com.boydti.discord.db.entities;

import com.boydti.discord.commands.manager.v2.impl.pw.TaxRate;
import com.boydti.discord.config.Settings;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.task.tax.GetNationsFromTaxBracket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private List<DBNation> nationsCached = null;

    public List<DBNation> getNations() throws Exception {
        return getNations(false);
    }

    public List<DBNation> getNations(boolean update) throws Exception {
        if (nations == 0) return new ArrayList<>();
        if (nationsCached != null && !update) return nationsCached;
        return nationsCached = new GetNationsFromTaxBracket(taxId).call();
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
