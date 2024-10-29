package link.locutus.discord.gpt.pw;

import com.google.gson.Gson;
import link.locutus.discord.web.WebUtil;
import link.locutus.wiki.game.PWWikiUtil;
import link.locutus.discord.gpt.IEmbeddingDatabase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WikiPagePW {
    private final String name;
    private final String url;
    private Set<String> categories;
    private long hash;

    private long dateModified;

    public WikiPagePW(String name, String url, long hash, Set<String> categories) {
        this.name = name;
        this.url = url;
        this.categories = categories;
        this.hash = hash;
    }

    public long getHash() {
        return hash;
    }

    public String getSlug() {
        return PWWikiUtil.slugify(name, false);
    }

    public String getUrl() {
        return url;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getPageData(IEmbeddingDatabase embeddings) throws IOException {
        Map<String, Object> map = PWWikiUtil.getPageJson(name);
        if (map == null) return null;
        this.categories = new LinkedHashSet<>((List<String>) map.get("categories"));
        String json = WebUtil.GSON.toJson(map);
        this.hash = embeddings.getHash(json);
        return map;
    }

    public File getFile() throws IOException {
        return PWWikiUtil.getPageFile(name);
    }

    public Long getLastModified() throws IOException {
        File file = getFile();
        if (file == null) return null;
        return file.lastModified();
    }

    public List<String> getSummaryData() throws IOException {
        String filePath = "wiki/json/summary/" + getSlug() + ".txt";
        File file = new File(filePath);
        if (!file.exists()) return null;
        String text = Files.readString(file.toPath());
        if (text.isEmpty()) return null;
        if (text.charAt(0) == '"') {
            // unescape text for quotes and newlines
            text = text.substring(1, text.length() - 1);
            text = text.replace("\\n", "\n");
            text = text.replace("\\\"", "\"");
        }
        List<String> lines = new ArrayList<>();
        String regex = text.contains("\n- ") ? "\n-[ ]" : "\n[ ]*- ";
        for (String line : text.split(regex)) {
            line = line.trim();
            if (line.startsWith("-")) {
                line = line.substring(1).trim();
            }
            lines.add(line);
        }
        return lines;
    }

    public Set<String> getCategories() {
        return categories;
    }
}
