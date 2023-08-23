package link.locutus.discord.util.task.nation;

import link.locutus.discord.config.Settings;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.util.io.PagePriority;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLEncoder;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

public class FetchStats{
    Map<MilitaryUnit, Integer> military;
    Map<Key, Double> valueMap;

    public FetchStats(String name, boolean isAlliance, int days) throws IOException {
        military = new HashMap<>();
        valueMap = new HashMap<>();

        String type = isAlliance ? "alliance" : "nation";

        String dateStr = (ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).format(TimeUtil.YYYY_MM_DD));

        String statUrl = "" + Settings.INSTANCE.PNW_URL() + "/index.php?id=132&name=%s&type=%s&date=%s&submit=Go";
        statUrl = String.format(statUrl, URLEncoder.encode(name, "UTF-8"), type, dateStr);

        Document dom = Jsoup.parse(FileUtil.readStringFromURL(PagePriority.NATION_STATS_UNUSED, statUrl));
        Elements tables = dom.getElementsByClass("nationtable");
        if (tables.isEmpty()) return;

        Element table = tables.get(0);
        Elements rows = table.getElementsByTag("tr");
        Elements elems = table.select(".bold");
        for (Element elem : elems) {
            String key = elem.text().replace(":", "").toLowerCase();
            if (key.isEmpty()) continue;

            String value = elem.nextElementSibling().text();

            Key keyEnum = Key.valueOf(key.toUpperCase().replace(" ", "_").replaceAll("[\\(\\)]", ""));
            Double doubleValue = MathMan.parseDouble(value.split("\\(")[0].trim());
            valueMap.put(keyEnum, doubleValue);

            switch (key) {
                case "soldiers":
                    military.put(MilitaryUnit.SOLDIER, MathMan.parseInt(value));
                    break;
                case "aircraft":
                    military.put(MilitaryUnit.AIRCRAFT, MathMan.parseInt(value));
                    break;
                case "missiles":
                    military.put(MilitaryUnit.MISSILE, MathMan.parseInt(value));
                    break;
                case "tanks":
                    military.put(MilitaryUnit.TANK, MathMan.parseInt(value));
                    break;
                case "ships":
                    military.put(MilitaryUnit.SHIP, MathMan.parseInt(value));
                    break;
                case "nukes":
                    military.put(MilitaryUnit.NUKE, MathMan.parseInt(value));
                    break;
                default:
            }
        }
    }

    public double get(Key key) {
        return valueMap.get(key);
    }

    public Integer getUnit(MilitaryUnit unit) {
        return military.get(unit);
    }

    public enum Key {
        SCORE,
        CITIES,
        NEW_OFFENSIVE_WARS,
        WARS_WON,
        INFRASTRUCTURE_DESTROYED,
        VALUE_OF_INFRASTRUCTURE_DESTROYED,
        STEEL_USED_IN_WAR,
        MUNITIONS_USED_IN_WAR,
        MISSILES_LAUNCHED,
        MISSILES_EATEN,
        TOTAL_NATIONS,
        GRAY_NATIONS,
        TANKS,
        SHIPS,
        NUKES,
        INFRASTRUCTURE,
        PROJECTS,
        NEW_DEFENSIVE_WARS,
        WARS_LOST,
        INFRASTRUCTURE_LOST,
        VALUE_OF_INFRASTRUCTURE_LOST,
        ALUMINUM_USED_IN_WAR,
        GASOLINE_USED_IN_WAR,
        NUKES_LAUNCHED,
        NUKES_EATEN,
        BEIGE_NATIONS,
        SOLDIERS,
        AIRCRAFT,
        MISSILES,
    }
}
