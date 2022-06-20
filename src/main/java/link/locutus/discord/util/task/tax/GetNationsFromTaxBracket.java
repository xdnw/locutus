package link.locutus.discord.util.task.tax;

import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.task.balance.GetPageTask;
import com.google.common.base.Charsets;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class GetNationsFromTaxBracket implements Callable<List<DBNation>> {
    private final int taxId;

    public GetNationsFromTaxBracket(String url) {
        int taxId = 0;
        String query = url.split("\\?")[1];
        List<NameValuePair> entries = URLEncodedUtils.parse(query, Charsets.UTF_8);
        for (NameValuePair pair : entries) {
            if (pair.getName().equalsIgnoreCase("tax_id")) {
                taxId = Integer.parseInt(pair.getValue());
            }
        }
        if (taxId == 0) {
            throw new IllegalArgumentException("Invalid tax url: " + url);
        }
        this.taxId = taxId;
    }

    public GetNationsFromTaxBracket(int taxId) {
        this.taxId = taxId;
    }

    @Override
    public List<DBNation> call() throws Exception {
        ArrayList<DBNation> nations = new ArrayList<>();
        String url = String.format("" + Settings.INSTANCE.PNW_URL() + "/index.php?id=15&tax_id=%s", taxId);

        List<Element> elems = new GetPageTask(null, url, 0).call();
        Map<Integer, DBNation> nationsMap = Locutus.imp().getNationDB().getNations();
        for (Element row : elems) {
            Elements columns = row.getElementsByTag("td");
            if (columns.size() < 2) continue;
            String nationUrl = columns.get(1).getElementsByTag("a").get(0).attr("href");
            Integer nationId = DiscordUtil.parseNationId(nationUrl);

            if (nationId != null) {
                DBNation nation = nationsMap.get(nationId);
                if (nation != null) {
                    nations.add(nation);
                }
            }
        }
        return nations;
    }
}
