package link.locutus.discord.util.task.balance;

import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.apiv1.enums.ResourceType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;

public class BankWithTask implements Callable<String> {
    private final Function<Map<ResourceType, Double>, String> modifier;
    private final int fromBank;
    private final DBNation nation;
    private final Integer toBank;
    private final Auth auth;

    public BankWithTask(Auth auth, int fromBank, int toBank, DBNation nation, Function<Map<ResourceType, Double>, String> modifier) {
        this.fromBank = fromBank;
        this.toBank = toBank;
        this.nation = nation;
        this.modifier = modifier;
        this.auth = auth;
    }

    public BankWithTask(Auth auth, int fromBank, DBNation nation, Function<Map<ResourceType, Double>, String> modifier) {
        this(auth, fromBank, 0, nation, modifier);
    }

    public BankWithTask(Auth auth, int fromBank, Integer alliance, Function<Map<ResourceType, Double>, String> modifier) {
        this(auth, fromBank, alliance, null, modifier);
    }

    @Override
    public String call() {
        return PnwUtil.withLogin(() -> {
            String url = "" + Settings.INSTANCE.PNW_URL() + "/alliance/id=" + fromBank + "&display=bank";
            String result = auth.readStringFromURL(url, Collections.emptyMap());
            Document dom = Jsoup.parse(result);

            try {
                Elements tableList = dom.getElementsByClass("nationtable");
                Element table = tableList.isEmpty() ? null : tableList.get(0);

                double[] resources = ResourceType.getBuffer();
                if (table == null || table.text().contains("You do not have permission")) {
                    resources = PnwUtil.resourcesToArray(new DBAlliance(auth.getAllianceId()).getStockpile());
                } else {
                    resources[ResourceType.MONEY.ordinal()] = MathMan.parseDouble(table.select("td:contains(Money)").first().nextElementSibling().text());
                    resources[ResourceType.FOOD.ordinal()] = MathMan.parseDouble(table.select("td:contains(Food)").first().nextElementSibling().text());
                    resources[ResourceType.COAL.ordinal()] = MathMan.parseDouble(table.select("td:contains(Coal)").first().nextElementSibling().text());
                    resources[ResourceType.OIL.ordinal()] = MathMan.parseDouble(table.select("td:contains(Oil)").first().nextElementSibling().text());
                    resources[ResourceType.URANIUM.ordinal()] = MathMan.parseDouble(table.select("td:contains(Uranium)").first().nextElementSibling().text());
                    resources[ResourceType.LEAD.ordinal()] = MathMan.parseDouble(table.select("td:contains(Lead)").first().nextElementSibling().text());
                    resources[ResourceType.IRON.ordinal()] = MathMan.parseDouble(table.select("td:contains(Iron)").first().nextElementSibling().text());
                    resources[ResourceType.BAUXITE.ordinal()] = MathMan.parseDouble(table.select("td:contains(Bauxite)").first().nextElementSibling().text());
                    resources[ResourceType.GASOLINE.ordinal()] = MathMan.parseDouble(table.select("td:contains(Gasoline)").first().nextElementSibling().text());
                    resources[ResourceType.MUNITIONS.ordinal()] = MathMan.parseDouble(table.select("td:contains(Munitions)").first().nextElementSibling().text());
                    resources[ResourceType.STEEL.ordinal()] = MathMan.parseDouble(table.select("td:contains(Steel)").first().nextElementSibling().text());
                    resources[ResourceType.ALUMINUM.ordinal()] = MathMan.parseDouble(table.select("td:contains(Aluminum)").first().nextElementSibling().text());
                }

                Elements tokenElem = dom.select("input[name=token]");
                String token = tokenElem.attr("value");

                Map<ResourceType, Double> rss = new LinkedHashMap<>();
                rss.put(ResourceType.MONEY, resources[ResourceType.MONEY.ordinal()]);
                rss.put(ResourceType.FOOD, resources[ResourceType.FOOD.ordinal()]);
                rss.put(ResourceType.COAL, resources[ResourceType.COAL.ordinal()]);
                rss.put(ResourceType.OIL, resources[ResourceType.OIL.ordinal()]);
                rss.put(ResourceType.URANIUM, resources[ResourceType.URANIUM.ordinal()]);
                rss.put(ResourceType.LEAD, resources[ResourceType.LEAD.ordinal()]);
                rss.put(ResourceType.IRON, resources[ResourceType.IRON.ordinal()]);
                rss.put(ResourceType.BAUXITE, resources[ResourceType.BAUXITE.ordinal()]);
                rss.put(ResourceType.GASOLINE, resources[ResourceType.GASOLINE.ordinal()]);
                rss.put(ResourceType.MUNITIONS, resources[ResourceType.MUNITIONS.ordinal()]);
                rss.put(ResourceType.STEEL, resources[ResourceType.STEEL.ordinal()]);
                rss.put(ResourceType.ALUMINUM, resources[ResourceType.ALUMINUM.ordinal()]);

                Map<ResourceType, Double> copy = new HashMap<>(rss);

                String note = modifier.apply(copy);
                if (copy.equals(rss)) {
                    return note;
                }

                Map<String, String> post = new LinkedHashMap<>();

                for (Map.Entry<ResourceType, Double> entry : rss.entrySet()) {
                    String name = "with" + entry.getKey().name().toLowerCase();

                    double amt = entry.getValue() - copy.get(entry.getKey());
                    if (amt > 0) {
                        post.put(name, String.format("%.2f", amt));
                    } else {
                        post.put(name, "0");
                    }
                }

                post.put("withnote", note == null ? "" : note);

                if (nation != null) {
                    post.put("withrecipient", nation.getNation());
                    post.put("withtype", "Nation");
                } else {
                    String name = Locutus.imp().getNationDB().getAllianceName(toBank);
                    if (name == null) {
                        return "No alliance found for: " + toBank;
                    }
                    post.put("withrecipient", "" + name);
                    post.put("withtype", "Alliance");
                }

                post.put("withsubmit", "Withdraw");

                post.put("token", token);

                StringBuilder response = new StringBuilder();

                result = auth.readStringFromURL("" + Settings.INSTANCE.PNW_URL() + "/alliance/id=" + fromBank + "&display=bank", post);
                dom = Jsoup.parse(result);
                for (Element element : dom.getElementsByClass("alert")) {
                    String text = element.text();
                    if (text.startsWith("Player Advertisement by ")) {
                        continue;
                    }
                    response.append('\n').append(text);
                }

//                response.append('\n').append("Current alliance totals: ").append("```" + StringMan.getString(copy) + "```");
                return response.toString();
            } catch (Throwable e) {
                if (result.contains("You do not have permission to see what is available in the alliance bank")) {
                    throw new IllegalArgumentException("You do not have permission to see what is available in the alliance bank, however you are able to make deposits.");
                }
                throw e;
            }
        }, auth);

    }
}
