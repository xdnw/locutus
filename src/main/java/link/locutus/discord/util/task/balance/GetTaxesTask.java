package link.locutus.discord.util.task.balance;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.apiv1.enums.ResourceType;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class GetTaxesTask implements Callable<List<BankDB.TaxDeposit>> {
    private final long cutOff;
    private final Auth auth;

    public GetTaxesTask(Auth auth, long cutOff) {
        this.auth = auth;
        this.cutOff = cutOff;
    }

    @Override
    public synchronized List<BankDB.TaxDeposit> call()  {
        int allianceId = auth.getAllianceId();
        GuildDB db = Locutus.imp().getGuildDBByAA(allianceId);
        String taxUrl = String.format("" + Settings.INSTANCE.PNW_URL() + "/alliance/id=%s&display=banktaxes", allianceId);

        ResourceType[] resources = {ResourceType.MONEY, ResourceType.FOOD, ResourceType.COAL, ResourceType.OIL, ResourceType.URANIUM, ResourceType.LEAD, ResourceType.IRON, ResourceType.BAUXITE, ResourceType.GASOLINE, ResourceType.MUNITIONS, ResourceType.STEEL, ResourceType.ALUMINUM};

        return PnwUtil.withLogin(new Callable<List<BankDB.TaxDeposit>>() {
            @Override
            public List<BankDB.TaxDeposit> call() throws Exception {
                try {
                    Map<Integer, TaxRate> internalTaxRates = new HashMap<>();

                    List<BankDB.TaxDeposit> records = new ArrayList<>();
                    GetPageTask task = new GetPageTask(auth, taxUrl, -1);
                    task.consume(element -> {
                        Elements row = element.getElementsByTag("td");
                        String indexStr = row.get(0).text().replace(")", "").trim();

                        if (!MathMan.isInteger(indexStr)) {
                            if (indexStr.equalsIgnoreCase("There are no tax records to display.")) {
                                if (element.parent().getElementsByTag("tr").size() < 3) {
                                    return true;
                                }
                            }
                            return false;
                        }

                        String[] taxStr = row.get(1).getElementsByTag("img").get(0).attr("title").split("Automated Tax ")[1].replace("%", "").split("/");
                        String dateStr = row.get(1).text().trim();
                        long date = TimeUtil.parseDate(TimeUtil.MMDDYYYY_HH_MM_A, dateStr);

                        int moneyTax = Integer.parseInt(taxStr[0].trim());
                        int resourceTax = Integer.parseInt(taxStr[1].trim());

                        String nationName = row.get(2).text();
                        DBNation nation = Locutus.imp().getNationDB().getNationByName(nationName);

                        String allianceName = row.get(3).text();
                        allianceName = allianceName.replaceAll(" Bank$", "");
                        Integer allianceId = PnwUtil.parseAllianceId(allianceName);

                        int nationId;
                        if (nation == null || allianceId == null) {
                            nationId = 0;
                        } else {
                            nationId = nation.getNation_id();
                        }

                        int taxId = Integer.parseInt(row.get(16).text());

                        double[] deposit = new double[ResourceType.values.length];
                        int offset = 4;

                        for (int j = 0; j < resources.length; j++) {
                            ResourceType type = resources[j];
                            Double amt = MathMan.parseDouble(row.get(j + offset).text().trim());
                            deposit[type.ordinal()] = amt;
                        }

                        if (date <= cutOff) {
                            return true;
                        }

                        TaxRate internal;
                        if (db == null) {
                            internal = new TaxRate(-1, -1);
                        } else {
                            internal = internalTaxRates.get(nationId);
                            if (internal == null) {
                                internal = db.getHandler().getInternalTaxrate(nationId);
                                internalTaxRates.put(nationId, internal);
                            }
                        }

                        BankDB.TaxDeposit taxRecord = new BankDB.TaxDeposit(allianceId, date, 0, taxId, nationId, moneyTax, resourceTax, internal.money, internal.resources, deposit);
                        records.add(taxRecord);

                        return false;
                    });

                    return records;
                } catch (Throwable e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        }, auth);
    }

}
