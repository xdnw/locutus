package link.locutus.discord.util.task.war;

import link.locutus.discord.config.Settings;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.FileUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class GetActiveWars implements Callable<Collection<Map.Entry<Integer, Integer>>> {
    private final int nationId;

    public GetActiveWars(int nationId) {
        this.nationId = nationId;
    }
    @Override
    public Collection<Map.Entry<Integer, Integer>> call() throws Exception {
        String url = "" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + nationId + "&display=war";
        Document dom = Jsoup.parse(FileUtil.readStringFromURL(url));

        Element table = dom.getElementsByClass("nationtable").get(0);
        Elements rows = table.getElementsByTag("tr");

        List<Map.Entry<Integer, Integer>> wars = new ArrayList<>();

        for (int i = 1; i < rows.size(); i++) {
            Element row = rows.get(i);
            Elements columns = row.getElementsByTag("td");

            if (columns.get(3).text().contains("Active War")) {
                String attackerStr = columns.get(1).getElementsByTag("a").get(0).attr("href");
                String defenderStr = columns.get(2).getElementsByTag("a").get(0).attr("href");
                Integer attacker = DiscordUtil.parseNationId(attackerStr);
                Integer defender = DiscordUtil.parseNationId(defenderStr);
                wars.add(new AbstractMap.SimpleEntry<>(attacker, defender));
            }
        }
        return wars;
    }
}