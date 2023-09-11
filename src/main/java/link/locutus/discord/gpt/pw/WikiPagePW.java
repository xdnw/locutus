package link.locutus.discord.gpt.pw;

import com.locutus.wiki.game.PWWikiUtil;
import link.locutus.discord.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class WikiPagePW {
    private final String name;
    private final String url;
    private final Set<String> categories;
    private final long hash;

    public WikiPagePW(String name, String url, long hash, Set<String> categories) {
        this.name = name;
        this.url = url;
        this.hash = hash;
        this.categories = categories;
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
}
