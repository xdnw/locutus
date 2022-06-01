package link.locutus.discord.util.task;

import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.apiv1.enums.ResourceType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class AllianceRssTask implements Callable<Map<Integer, Map<ResourceType, Double>>> {
    private final int allianceId;
    private final Auth auth;

    public AllianceRssTask(DBNation account) {
        this.allianceId = account.getAlliance_id();
        this.auth = account.getAuth(null);
    }

    @Override
    public Map<Integer, Map<ResourceType, Double>> call() throws Exception {
        return PnwUtil.withLogin(new Callable<Map<Integer, Map<ResourceType, Double>>>() {
            @Override
            public Map<Integer, Map<ResourceType, Double>> call() throws Exception {
                Map<Integer, Map<ResourceType, Double>> nationResources = new LinkedHashMap<>();
                String html = auth.readStringFromURL("" + Settings.INSTANCE.PNW_URL() + "/alliance/id=" + allianceId + "&display=acp", Collections.emptyMap());
                Document dom = Jsoup.parse(html);
                Element table = dom.getElementsByClass("nationtable").get(2);

                Elements rows = table.getElementsByTag("tr");
                for (int i = 1; i < rows.size(); i++) {
                    Element row = rows.get(i);
                    Elements columns = row.getElementsByTag("td");
                    if (columns.size() != 17) {
                        continue;
                    }

                    Locutus.imp().getPnwApi().getNation(1).getCityids();

                    double money = MathMan.parseDouble(columns.get(3).text());
                    double steel = MathMan.parseDouble(columns.get(15).text());
                    double aluminum = MathMan.parseDouble(columns.get(16).text());
                    double gasoline = MathMan.parseDouble(columns.get(13).text());
                    double munitions = MathMan.parseDouble(columns.get(14).text());
                    double uranium = MathMan.parseDouble(columns.get(9).text());
                    double food = MathMan.parseDouble(columns.get(6).text());

                    double coal = MathMan.parseDouble(columns.get(7).text());
                    double oil = MathMan.parseDouble(columns.get(8).text());

                    double lead = MathMan.parseDouble(columns.get(10).text());
                    double iron = MathMan.parseDouble(columns.get(11).text());
                    double bauxite = MathMan.parseDouble(columns.get(12).text());




                }
                return null;
            }
        }, auth);
    }
}
