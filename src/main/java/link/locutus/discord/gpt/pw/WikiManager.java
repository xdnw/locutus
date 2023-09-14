package link.locutus.discord.gpt.pw;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.locutus.wiki.game.PWWikiUtil;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.GptHandler;
import link.locutus.discord.gpt.IEmbeddingDatabase;
import org.jooq.DSLContext;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class WikiManager {
    private final Map<Integer, WikiPagePW> gameWikiPagesBySourceId = new ConcurrentHashMap<>();
    private final GptDatabase database;
    private final IEmbeddingDatabase embeddings;
    private final GptHandler handler;

    public WikiManager(GptDatabase database, IEmbeddingDatabase embeddings, GptHandler handler) {
        this.database = database;
        this.embeddings = embeddings;
        this.handler = handler;

        createTables();
    }

    public Set<WikiPagePW> getOutdatedPages() {
        // return list of pages where hash of file != hash
        Set<WikiPagePW> pages = new LinkedHashSet<>();
        // iterate over each page
        for (Map.Entry<Integer, WikiPagePW> entry : gameWikiPagesBySourceId.entrySet()) {
            int sourceId = entry.getKey();
            EmbeddingSource source = embeddings.getEmbeddingSource(sourceId);

            WikiPagePW page = entry.getValue();


            // get hash of file
            long hash = page.getHash();
            // get hash of page
            long fileHash = page.getFileHash();
            // if they are different
            if (hash != fileHash) {
                // add page to list
                pages.add(page);
            }
        }

        // add a fetchHash method
        // move code in method below that has existing gethash to that method



    }

    public void updateWikiPages(long updateOlderThan, int maxFetches) {
        // use file last modified date

        // TODO update the sitemap

        // Download missing pages
    }



    private DSLContext ctx() {
        return database.ctx();
    }

    public Set<String> getCategories() {
        Set<String> categories = new LinkedHashSet<>();
        for (WikiPagePW page : gameWikiPagesBySourceId.values()) {
            categories.addAll(page.getCategories());
        }
        return categories;
    }

    public Set<WikiPagePW> getPages(Set<String> allowedCategories) {
            Set<WikiPagePW> pages = new LinkedHashSet<>();
        for (WikiPagePW page : gameWikiPagesBySourceId.values()) {
            if (page.getCategories().stream().anyMatch(allowedCategories::contains)) {
                pages.add(page);
            }
        }
        return pages;
    }

    public void createTables() {
        ctx().execute("CREATE TABLE IF NOT EXISTS wiki_pages (source_id INT NOT NULL PRIMARY KEY, page_name TEXT NOT NULL, url TEXT NOT NULL, categories TEXT NOT NULL, hash BIGINT NOT NULL)");
    }

    public void deleteWikiPage(int sourceId, boolean deleteEmbedding) {
        gameWikiPagesBySourceId.remove(sourceId);
        if (deleteEmbedding) {
            EmbeddingSource source = embeddings.getEmbeddingSource(sourceId);
            if (source != null) {
                embeddings.deleteSource(source);
            }
        }
        ctx().execute("DELETE FROM wiki_pages WHERE source_id = ?", sourceId);
    }

    public void loadPages() {
        gameWikiPagesBySourceId.clear();
        ctx().selectFrom("wiki_pages").fetch().forEach(f -> {
            int sourceId = f.get("source_id", Integer.class);
            String pageName = f.get("page_name", String.class);
            String url = f.get("url", String.class);
            long hash = f.get("hash", Long.class);
            String categories = f.get("categories", String.class);
            Set<String> categorySet = new LinkedHashSet<>();
            for (String category : categories.split(",")) {
                categorySet.add(category.trim());
            }
            WikiPagePW page = new WikiPagePW(pageName, url, hash, categorySet);
            gameWikiPagesBySourceId.put(sourceId, page);
        });
    }

    public void importFileCategories() throws IOException {
        Map<String, WikiPagePW> pages = readFileCategories();
        System.out.println("Importing " + pages.size() + " pages");

        Set<EmbeddingSource> wikiSources = embeddings.getSources(f -> f == 0, f -> f.source_name.startsWith("wiki/"));
        // Delete unused sources
        for (EmbeddingSource source : wikiSources) {
            String sourceName = source.source_name;
            if (!pages.containsKey(sourceName)) {
                embeddings.deleteSource(source);
            }
        }

        // register current sources
        for (Map.Entry<String, WikiPagePW> entry : pages.entrySet()) {
            String sourceName = entry.getKey();
            WikiPagePW page = entry.getValue();
            EmbeddingSource source = embeddings.getOrCreateSource(sourceName, 0);
            gameWikiPagesBySourceId.put(source.source_id, entry.getValue());

            List<String> summary = page.getSummaryData();
            if (summary != null && !summary.isEmpty()) {
                System.out.println("Found Summary for " + page.getName() + ": " + summary);

                // entry source , null
                Stream<Map.Entry<String, String>> stream = summary.stream().map(f -> new AbstractMap.SimpleEntry<>(f, null));
                handler.registerEmbeddings(source, stream, false, true);
            } else {
                System.out.println("No summary for " + page.getSlug());
            }
        }
    }

    private Map<String, WikiPagePW> readFileCategories() throws IOException {
        Map<String, WikiPagePW> pages = new LinkedHashMap<>();
        for (Map.Entry<String, String> pageEntry : PWWikiUtil.getSitemapCached().entrySet()) {
            String pageName = pageEntry.getKey();
            WikiPagePW wikiPage = new WikiPagePW(pageName, pageEntry.getValue(), 0, Collections.emptySet());
            Map<String, Object> map = wikiPage.getPageData(embeddings);
            if (map == null) {
                System.out.println("No page found for " + pageName);
                continue;
            }
            long hash = wikiPage.getHash();
            String sourceName = "wiki/" + pageName;
            pages.put(sourceName, wikiPage);
        }
        return pages;
    }

    public WikiPagePW getWikiPageBySourceId(int sourceId) {
        EmbeddingSource source = embeddings.getEmbeddingSource(sourceId);
        if (source == null) {
            deleteWikiPage(sourceId, false);
            return null;
        }
        return gameWikiPagesBySourceId.get(sourceId);
    }
}
