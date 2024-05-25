package link.locutus.discord.db.entities;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAllianceOrGuildOrTaxid;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.util.PW;

import java.util.*;
import java.util.stream.Collectors;

public class TaxBracket implements NationOrAllianceOrGuildOrTaxid {
    public final int taxId;
    public long dateFetched;
    private int allianceId;
    private String name;

    public int moneyRate;
    public int rssRate;

    public TaxBracket(int allianceId, Map<String, String> data) {
        name = data.get("Bracket Name");
        String[] nameSplit = name.substring(0, name.length() - 1).split("#");
        name = name.split(" \\(#")[0];

        taxId = Integer.parseInt(nameSplit[nameSplit.length - 1]);
        this.allianceId = allianceId;
        moneyRate = Integer.parseInt(data.get("Money Tax Rate").replaceAll("%", ""));
        rssRate = Integer.parseInt(data.get("Resource Tax Rate").replaceAll("%", ""));
        int nations = Integer.parseInt(data.get("Nations").split(" ")[0].replace(",", ""));

        this.dateFetched = System.currentTimeMillis();
    }

    public void setName(String name) {
        this.name = name;
    }

    public TaxBracket(int taxId, int allianceId, String name, int moneyRate, int rssRate, long date) {
        this.taxId = taxId;
        this.allianceId = allianceId;
        this.name = name;
        this.moneyRate = moneyRate;
        this.rssRate = rssRate;
        this.dateFetched = date;
    }

    public TaxBracket(com.politicsandwar.graphql.model.TaxBracket bracket) {
        this.taxId = bracket.getId();
        this.allianceId = bracket.getAlliance_id();
        this.name = bracket.getBracket_name();
        this.moneyRate = bracket.getTax_rate();
        this.rssRate = bracket.getResource_tax_rate();
        this.dateFetched = System.currentTimeMillis();
    }

    @Command(desc = "Id of the tax bracket")
    @Override
    public int getId() {
        return taxId;
    }

    @Command(desc = "Count the number of nations in this tax bracket")
    public int countNations(@Default NationFilter filter) {
        return getNationList(filter).getNations().size();
    }

    @Override
    public String getTypePrefix() {
        return "tax_id";
    }

    @Override
    public boolean isNation() {
        return false;
    }

    @Override
    public boolean isTaxid() {
        return true;
    }

    @Override
    public boolean isAlliance() {
        return false;
    }

    @Command(desc = "The alliance id this bracket belongs to, else 0")
    public int getAlliance_id() {
        if (this.allianceId < 0) {
            Set<DBNation> nations = Locutus.imp().getNationDB().getNationsMatching(f -> f.getAlliance_id() == taxId);
            if (!nations.isEmpty()) {
                allianceId = nations.iterator().next().getAlliance_id();
            } else {
                allianceId = 0;
            }
        }
        return allianceId;
    }

    public Set<DBNation> getNations() {
        if (taxId == 0) return Collections.emptySet();
        if (getAlliance_id() != 0) {
            DBAlliance alliance = DBAlliance.get(allianceId);
            if (alliance != null) return alliance.getNations(f -> f.getTax_id() == taxId);
            return Collections.emptySet();
        }
        return Locutus.imp().getNationDB().getNationsByBracket(taxId);
    }

    @Command(desc = "Url of this tax bracket in-game")
    public String getUrl() {
        return PW.getTaxUrl(taxId);
    }

    @Override
    public String toString() {
        return (allianceId > 0 ? DBAlliance.getOrCreate(allianceId).getQualifiedId() + "- " : "") + ((name != null && !name.isEmpty()) ? (name + "- ") : "") + "#" + taxId + " (" + moneyRate + "/" + rssRate + ")";
    }

    public String getSubText() {
        return (allianceId > 0 ? DBAlliance.getOrCreate(allianceId).getQualifiedId() + "- " : "") + "#" + taxId + " (" + moneyRate + "/" + rssRate + ")";
    }


    @Command(desc = "Tax rate object")
    public TaxRate getTaxRate() {
        return new TaxRate(moneyRate, rssRate);
    }

    @Command(desc = "The name of this tax bracket")
    public String getName() {
        return name;
    }

    public int getAlliance_id(boolean fetchIfUnknown) {
        return fetchIfUnknown ? getAlliance_id() : allianceId;
    }

    @Command(desc = "The alliance object for this bracket")
    public DBAlliance getAlliance() {
        return DBAlliance.getOrCreate(allianceId);
    }

    @Command(desc = "The list of nations currently in this bracket")
    public NationList getNationList(@Default NationFilter filter) {
        Set<DBNation> nations = getNations();
        if (filter != null) nations = nations.stream().filter(filter).collect(Collectors.toSet());
        return new SimpleNationList(nations);
    }

    @Command(desc = "Money tax rate")
    public int getMoneyRate() {
        return moneyRate;
    }

    @Command(desc = "Resource tax rate")
    public int getRssRate() {
        return rssRate;
    }
}
