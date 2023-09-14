package com.locutus.wiki.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PWWikiUtil {

    private static Set<String> SKIP_PAGES = new HashSet<>();
    static {
        SKIP_PAGES.add("the_union_of_eos");//   uses n word
        SKIP_PAGES.add("doc");// meta sub page
        SKIP_PAGES.add("python");// meta sub page
        SKIP_PAGES.add("vietnamese_civil_war_1949-55");// no content
        SKIP_PAGES.add("pw-imperator");// flagged as inappropriate by openai
    }
    public static String slugify(String value, boolean allowUnicode) {
        value = String.valueOf(value);

        if (allowUnicode) {
            value = Normalizer.normalize(value, Normalizer.Form.NFKC);
        } else {
            value = Normalizer.normalize(value, Normalizer.Form.NFKD)
                    .replaceAll("[^\\x00-\\x7F]", "")
                    .replaceAll("[^\\w\\s-]", "");
        }

        value = value.toLowerCase()
                .replaceAll("[-\\s]+", "-")
                .replaceAll("[-_]+$", "")
                .replaceAll("[-_]+", "-");

        return value;
    }

    public static Map<String, String> getAllPages() throws IOException {
        String baseUrl = "https://politicsandwar.fandom.com";
        String url = baseUrl + "/wiki/Special:AllPages";

        Map<String, String> pages = new HashMap<>();

        while (url != null) {
            Document document = Jsoup.connect(url).get();

            // Find all links within the ul with class "mw-allpages-chunk"
            Elements links = document.select("ul.mw-allpages-chunk a");

            for (Element link : links) {
                String href = link.attr("href");
                String text = link.text();

                // Skip if href is empty or if text does not contain any letters
                if (href.isEmpty() || !text.matches(".*[a-zA-Z].*")) {
                    continue;
                }

                // Add to the pages map
                pages.put(text, baseUrl + href);
            }

            // Find the link to the next page
            Element navDiv = document.select("div.mw-allpages-nav").first();
            Element nextLink = null;

            if (navDiv != null) {
                Elements anchorTags = navDiv.select("a");
                for (Element anchorTag : anchorTags) {
                    if (anchorTag.text().startsWith("Next page")) {
                        nextLink = anchorTag;
                        break;
                    }
                }
            }

            if (nextLink != null) {
                url = baseUrl + nextLink.attr("href");
            } else {
                url = null;
            }
        }

        return pages;
    }

    public static Map<String, String> getCategoryPages(String category) throws IOException {
        String baseUrl = "https://politicsandwar.fandom.com/wiki/Category:";
        String url = baseUrl + category;

        Map<String, String> pages = new HashMap<>();

        while (url != null) {
            Document document = Jsoup.connect(url).get();

            // Find all links within the div with class "category-page__members"
            Elements links = document.select("div.category-page__members a");

            for (Element link : links) {
                String href = link.attr("href");
                String text = link.text();

                // Skip if href is empty or if text does not contain any letters
                if (href.isEmpty() || !text.matches(".*[a-zA-Z].*")) {
                    continue;
                }

                // Skip if title contains "Category:"
                String hrefLower = href.toLowerCase(Locale.ROOT);
                if (hrefLower.contains("category:") || hrefLower.contains("user:") || hrefLower.contains("file:") || hrefLower.contains("template:")) {
                    continue;
                }

                // Add to the pages map
                pages.put(text, href);
            }

            // Find the link to the next page
            Element nextLink = document.select("div.category-page__pagination a.category-page__pagination-next").first();

            if (nextLink != null) {
                url = nextLink.attr("href");
                if (!url.startsWith("http")) {
                    url = baseUrl + url;
                }
            } else {
                url = null;
            }
        }

        return pages;
    }

    public static void getTable(Map<String, List<String>> blocks, Element pageElement, String title) {
        // Find all the rows in the table
        Elements rows = pageElement.select("tr");

        // Page title from URL '/'
        String key = java.net.URLDecoder.decode(title, java.nio.charset.StandardCharsets.UTF_8) + ".InfoBox";
        boolean resetKey = false;

        // Loop through each row and extract the key/value pairs
        for (Element row : rows) {
            // Get the cells in the row
            Elements cells = row.select("td, th");

            // If there are cells in the row, extract the key/value pairs
            if (!cells.isEmpty()) {
                // Skip if cells length is less than 2
                if (cells.size() == 1) {
                    if (resetKey) {
                        resetKey = false;
                        key = cells.get(0).text().trim();
                        key = key.replaceAll("[^\\x00-\\x7F]", " ");
                    }
                    // Only if the key is empty
                    continue;
                }

                resetKey = true;

                // The first cell is the key
                String left = cells.get(0).text().trim();

                // The subsequent cells are the values
                List<String> right = cells.subList(1, cells.size())
                        .stream()
                        .map(cell -> cell.text().trim())
                        .toList();

                String combined = "(" + left + ", " + right.toString() + ")";

                // Create a list if it doesn't exist and append combined to blocks[key]
                blocks.computeIfAbsent(key, k -> {
                    List<String> list = blocks.getOrDefault(k, null);
                    if (list == null) {
                        list = List.of(combined);
                    } else {
                        list.add(combined);
                    }
                    return list;
                });
            }
        }
    }
    public static Map<String, List<String>> extractSections(String url) throws IOException {
        Map<String, List<String>> blocks = new HashMap<>();

        // Fetch the webpage using Jsoup
        Document doc = Jsoup.connect(url).get();
        String page_title = url.substring(url.lastIndexOf("/") + 1);
        System.out.println("Extracting: " + url);

        // Remove elements with class "nowraplinks collapsible autocollapse navbox-subgroup"
        Elements divElements = doc.select("div.nowraplinks.collapsible.autocollapse.navbox-subgroup");
        divElements.remove();

        // Get content of "page-header__categories" class
        Element categoriesElement = doc.selectFirst("div.page-header__categories");
        if (categoriesElement != null) {
            Elements categoryLinks = categoriesElement.select("a");
            List<String> categoryList = new ArrayList<>();
            for (Element categoryLink : categoryLinks) {
                categoryList.add(categoryLink.text().trim());
            }
            // remove category if matches number more e.g. `4 more`
            categoryList.removeIf(category -> category.matches("\\d+ more"));
            blocks.put("categories", categoryList);
        }

        // Get "mw-parser-output" div
        Element divElement = doc.selectFirst("div.mw-parser-output");
        if (divElement != null && divElement.children().size() > 2) {
            Element foundChild = divElement.child(2);

            // Find the first table with more than 1 row
            for (Element child : divElement.children()) {
                if ("table".equals(child.tagName())) {
                    foundChild = child;
                    Elements rows = child.select("tr");
                    if (rows.size() > 1) {
                        break;
                    }
                }
            }

            if ("table".equals(foundChild.tagName())) {
                getTable(blocks, foundChild, page_title);
            } else {
                // Find the first "table" element with class "infobox"
                Element infobox = divElement.selectFirst("table.infobox");
                if (infobox != null) {
                    getTable(blocks, infobox, page_title);
                }
            }
        }

        // Get content before the first "h2" or "h3" inside "mw-parser-output"
        StringBuilder content = new StringBuilder();
        for (Element element : divElement.children()) {
            if ("table".equals(element.tagName())) {
                continue;
            }
            if ("h2".equals(element.tagName()) || "h3".equals(element.tagName()) || "div".equals(element.tagName())) {
                break;
            }
            content.append(element.text().trim());
        }
        blocks.put("main", List.of(content.toString().trim()));

        // Iterate through "h2" and "h3" elements for related links
        Elements headings = doc.select("h2, h3");
        String h2_heading = null;
        for (Element heading : headings) {
            if ("Related links".equals(heading.text())) {
                break;
            }
            List<String> values = new ArrayList<>();
            for (Element sibling : heading.nextElementSiblings()) {
                if ("h2".equals(sibling.tagName()) || "h3".equals(sibling.tagName())) {
                    break;
                }
                String text = sibling.text().trim();
                if (!text.isEmpty()) {
                    values.add(text);
                }
            }
            if ("h2".equals(heading.tagName())) {
                h2_heading = heading.text();
                if (!values.isEmpty()) {
                    String key = slugify(heading.text(), false);
                    blocks.put(key, values);
                }
            } else if ("h3".equals(heading.tagName())) {
                if (h2_heading != null) {
                    String h2_text = slugify(h2_heading, false);
                    String h3_text = slugify(heading.text(), false);
                    if (!values.isEmpty()) {
                        String key = h2_text + "." + h3_text;
                        key = key.replaceAll("[^\\x00-\\x7F]", " ");
                        blocks.put(key, values);
                    }
                }
            }
        }

        return blocks;
    }

    public static void saveToJson(String pageName, String url) throws IOException {
        // Assume you have implemented these methods:
        Map<String, List<String>> blocks = extractSections(url);
        String slug = slugify(pageName, false);

        // Create a Gson instance with pretty printing
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        // Create a FileWriter to save the JSON data
        File file = new File("wiki/json/" + slug + ".json");
        // create dir and file if not exist
        file.getParentFile().mkdirs();
        file.createNewFile();
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(blocks, writer);
        }
    }

    public static void fetchDefaultPages() throws IOException {
        Map<String, String> pagesToSave = new LinkedHashMap<>();
        pagesToSave.put("Frequently Asked Questions", "Frequently_Asked_Questions");
        pagesToSave.put("Paperless", "Paperless");

        String[] categoriesToSave = {"Wars", "Alliances", "Treaties", "Guides", "Mechanics", "API"};

        // Iterate through categories
        for (String category : categoriesToSave) {
            // Get the pages from each category
            System.out.println("Get " + category);
            Map<String, String> pages = getCategoryPages(category);

            for (Map.Entry<String, String> entry : pages.entrySet()) {
                String page = entry.getKey();
                String url = entry.getValue();
                // only last text after bracket, strip query string
                url = url.replace("https://politicsandwar.fandom.com/wiki/", "");
                url = url.replace("/wiki/", "");
                if (url.contains("?")) {
                    url = url.substring(0, url.indexOf("?"));
                }
                String hrefLower = url.toLowerCase();
                if (hrefLower.contains("category:") || hrefLower.contains("user:") || hrefLower.contains("file:") || hrefLower.contains("template:")) {
                    continue;
                }
                pagesToSave.put(page, url);
            }
        }

        // Save to sitemap.json
        File file = new File("wiki/sitemap.json");
        file.getParentFile().mkdirs();
        file.createNewFile();
        FileWriter writer = new FileWriter(file);
        new Gson().toJson(pagesToSave, writer);
        writer.close();
        System.out.println("Write sitemap " + file.getAbsolutePath());
    }

    public static Map<String, String> getSitemapCached() throws IOException {
        String filename = "wiki/sitemap.json";
        File file = new File(filename);

        // If the file does not exist, fetch and save to sitemap.json
        if (!file.exists()) {
            System.out.println("Fetching default pages");
            fetchDefaultPages();
        }

        // Load sitemap.json
        System.out.println("Loading " + file.getAbsolutePath());
        Gson gson = new Gson();
        FileReader reader = new FileReader(filename);
        Map<String, String> sitemap = gson.fromJson(reader, LinkedHashMap.class);
        reader.close();
        System.out.println(sitemap);
        return sitemap;
    }

    public static void saveDefaultPages() throws IOException {
        Map<String, String> pagesToSave = getSitemapCached();

        // Iterate through each page
        for (Map.Entry<String, String> entry : pagesToSave.entrySet()) {
            String name = entry.getKey();
            String urlSub = entry.getValue();
            String url = "https://politicsandwar.fandom.com/wiki/" + urlSub;
            // Strip non-filename characters
            String slug = slugify(name, false);

            if (SKIP_PAGES.contains(slug)) {
                continue;
            }

            // Check if the file exists
            File jsonFile = new File("wiki/json/" + slug + ".json");
            if (jsonFile.exists()) {
                System.out.println("Skipping " + slug + ".json");
                continue;
            }

            // Save to JSON
            System.out.println("Saving " + slug + ".json");
            saveToJson(name, url);
        }
    }

    public static File getPageFile(String pageName) throws IOException {
        String fileName = slugify(pageName, false);

        if (SKIP_PAGES.contains(fileName)) {
            return null;
        }

        // If the file doesn't exist, fetch and save it
        File jsonFile = new File("wiki/json/" + fileName + ".json");
        if (!jsonFile.exists()) {
            String url = "https://politicsandwar.fandom.com/wiki/" + java.net.URLEncoder.encode(pageName, "UTF-8");
            saveToJson(pageName, url);
        }
        return jsonFile;
    }

    public static Map<String, Object> getPageJson(String pageName) throws IOException {
        File file = getPageFile(pageName);
        try (FileReader reader = new FileReader(file)) {
            return new Gson().fromJson(reader, Map.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
