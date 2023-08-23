package link.locutus.discord.util.task.balance;

import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.offshore.Auth;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;

public class GetPageTask implements Callable<List<Element>> {
    private final String url;
    private final int tableIndex;
    private final Auth auth;
    private final PagePriority priority;

    public GetPageTask(PagePriority priority, Auth auth, String url, int tableIndex) {
        this.auth = auth;
        this.url = url;
        this.priority = priority;
        this.tableIndex = tableIndex;
    }

    @Override
    public List<Element> call() throws IOException {
        List<Element> results = new ArrayList<>();
        consume(f -> {
            results.add(f);
            return false;
        });
        return results;
    }

    public void consume(Function<Element, Boolean> shouldBreak) throws IOException {
        outer:
        for (int i = 0; ; i += 50) {
            Map<String, String> form = new HashMap<>();
            form.put("minimum", Integer.toString(i));
            form.put("maximum", Integer.toString(i + 50));
            form.put("search", "Go");

            String tmp = url + String.format("&maximum=%s&minimum=%s&search=Go", i + 50, i);

            String html = auth == null ? FileUtil.get(FileUtil.readStringFromURL(priority, tmp, form)) : auth.readStringFromURL(priority, tmp, form);
            Document dom = Jsoup.parse(html);
            Elements tables = dom.getElementsByClass("nationtable");
            int finalTableIndex = tableIndex == -1 ? tables.size() - 1 : tableIndex;
            if (finalTableIndex < 0 || finalTableIndex >= tables.size()) {
                throw new IllegalArgumentException("Unable to fetch table: " + url + "\n" + html);
            }
            Element table = tables.get(finalTableIndex);
            Elements rows = table.getElementsByTag("tr");

            List<Element> subList = rows.subList(1, rows.size());

            for (Element elem : subList) {
                if (shouldBreak.apply(elem)) {
                    break outer;
                }
            }

            if (rows.size() < 51) {
                break;
            }
        }
    }
}
