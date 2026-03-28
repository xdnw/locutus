package link.locutus.discord.gpt.pw;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Logg;
import link.locutus.discord.db.DBMainV3;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.GptHandler;
import link.locutus.discord.gpt.IVectorDB;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.wiki.game.PWWikiUtil;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WikiManager {
    private final Map<Integer, WikiPagePW> gameWikiPagesBySourceId = new ConcurrentHashMap<>();
    private final IVectorDB embeddings;
    private final GptHandler handler;
    private final DBMainV3 vectorSqlDb;

    public WikiManager(DBMainV3 vectorSqlDb, IVectorDB embeddings, GptHandler handler) {
        this.vectorSqlDb = vectorSqlDb;
        this.embeddings = embeddings;
        this.handler = handler;

        createTables();
    }

    public Set<WikiPagePW> getModifiedPages() throws IOException {
        // return list of pages where hash of file != hash
        Set<WikiPagePW> pages = new ObjectLinkedOpenHashSet<>();
        // iterate over each page
        for (Map.Entry<Integer, WikiPagePW> entry : gameWikiPagesBySourceId.entrySet()) {
            int sourceId = entry.getKey();
            EmbeddingSource source = embeddings.getEmbeddingSource(sourceId);

            WikiPagePW page = entry.getValue();

            // get hash of file
            long oldHash = source.source_hash;
            // get hash of page
            page.getPageData(embeddings);
            long newHash = page.getHash();
            // if they are different
            if (oldHash != newHash) {
                // add page to list
                pages.add(page);
            }
        }

        return pages;
    }

    public List<WikiPagePW> getOutdatedPages(long updateOlderThan) throws IOException {
        List<Map.Entry<WikiPagePW, Long>> pages = new ArrayList<>();
        PWWikiUtil.fetchDefaultPages();
        importFileCategories(true, false);

        // iterate pages
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, WikiPagePW> entry : gameWikiPagesBySourceId.entrySet()) {
//            int sourceId = entry.getKey();
//            EmbeddingSource source = embeddings.getEmbeddingSource(sourceId);
            WikiPagePW page = entry.getValue();

            Long lastModified = page.getLastModified();
            if (lastModified == null) continue;
            if (lastModified < updateOlderThan) {
                long ageDiff = Math.max(0, now - lastModified);
                pages.add(KeyValue.of(page, ageDiff));
            }
        }
        List<WikiPagePW> list = new ArrayList<>(pages.stream().sorted(Map.Entry.comparingByValue()).map(Map.Entry::getKey).toList());
        Collections.reverse(list);
        return list;
    }


    public Set<String> getCategories() {
        Set<String> categories = new ObjectLinkedOpenHashSet<>();
        for (WikiPagePW page : gameWikiPagesBySourceId.values()) {
            categories.addAll(page.getCategories());
        }
        return categories;
    }

    public Set<WikiPagePW> getPages(Set<String> allowedCategories) {
            Set<WikiPagePW> pages = new ObjectLinkedOpenHashSet<>();
        for (WikiPagePW page : gameWikiPagesBySourceId.values()) {
            if (page.getCategories().stream().anyMatch(allowedCategories::contains)) {
                pages.add(page);
            }
        }
        return pages;
    }

    public void createTables() {
        vectorSqlDb.jdbi().useHandle(handle ->
                handle.execute("CREATE TABLE IF NOT EXISTS wiki_pages (source_id INT NOT NULL PRIMARY KEY, page_name TEXT NOT NULL, url TEXT NOT NULL, categories TEXT NOT NULL, hash BIGINT NOT NULL)")
        );
    }

    public void deleteWikiPage(int sourceId, boolean deleteEmbedding) {
        gameWikiPagesBySourceId.remove(sourceId);
        if (deleteEmbedding) {
            EmbeddingSource source = embeddings.getEmbeddingSource(sourceId);
            if (source != null) {
                embeddings.deleteSource(source);
            }
        }
        vectorSqlDb.jdbi().useHandle(handle ->
                handle.createUpdate("DELETE FROM wiki_pages WHERE source_id = :sourceId")
                        .bind("sourceId", sourceId)
                        .execute()
        );
    }

    public void loadPages() {
        gameWikiPagesBySourceId.clear();
        vectorSqlDb.jdbi().useHandle(handle -> handle.createQuery(
                        "SELECT source_id, page_name, url, categories, hash FROM wiki_pages")
                .map((rs, ctx) -> new StoredWikiPage(
                        rs.getInt("source_id"),
                        rs.getString("page_name"),
                        rs.getString("url"),
                        rs.getString("categories"),
                        rs.getLong("hash")
                ))
                .forEach(f -> {
            int sourceId = f.sourceId();
            String pageName = f.pageName();
            String url = f.url();
            long hash = f.hash();
            String categories = f.categories();
            Set<String> categorySet = new ObjectLinkedOpenHashSet<>();
            for (String category : categories.split(",")) {
                categorySet.add(category.trim());
            }
            WikiPagePW page = new WikiPagePW(pageName, url, hash, categorySet);
            gameWikiPagesBySourceId.put(sourceId, page);
        }));
    }

    public void importFileCategories(boolean deleteMissing, boolean importSummary) throws IOException {
        Map<String, WikiPagePW> pages = readFileCategories();
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("No pages found");
        }
        if (deleteMissing) {
            Set<EmbeddingSource> wikiSources = embeddings.getSources(f -> f == 0, f -> f.source_name.startsWith("wiki/"));
            for (EmbeddingSource source : wikiSources) {
                String sourceName = source.source_name;
                if (!pages.containsKey(sourceName)) {
                    deleteWikiPage(source.source_id, true);
                }
            }
            for (Map.Entry<Integer, WikiPagePW> entry : this.gameWikiPagesBySourceId.entrySet()) {
                WikiPagePW page = entry.getValue();
                if (!pages.containsKey(page.getName())) {
                    int sourceId = entry.getKey();
                    deleteWikiPage(sourceId, true);
                }
            }
        }

        // register current sources
        for (Map.Entry<String, WikiPagePW> entry : pages.entrySet()) {
            String sourceName = entry.getKey();
            WikiPagePW page = entry.getValue();
            EmbeddingSource source = embeddings.getOrCreateSource(sourceName, 0);
            gameWikiPagesBySourceId.put(source.source_id, entry.getValue());

            if (importSummary) {
                List<String> summary = page.getSummaryData();
                if (summary != null && !summary.isEmpty()) {
                    System.out.println("Found Summary for " + page.getName() + ": " + summary);
                    handler.registerEmbeddings(source, summary.stream(), false, true);
                } else {
                    System.out.println("No summary for " + page.getSlug());
                }
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
                Logg.text("No page found for " + pageName);
                continue;
            }
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

    private record StoredWikiPage(int sourceId, String pageName, String url, String categories, long hash) {
    }
}
