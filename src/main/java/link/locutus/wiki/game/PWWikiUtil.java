package link.locutus.wiki.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.db.conflict.Conflict;
import link.locutus.discord.db.entities.DBTopic;
import link.locutus.discord.db.entities.conflict.ConflictCategory;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.web.WebUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
                if (hrefLower.contains("category:") || hrefLower.contains("user:") || hrefLower.contains("file:") || hrefLower.contains("template:") || hrefLower.contains("talk:")) {
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

    public static Document getDocument(String slug, String urlStub, boolean readCache) throws IOException {
        File file = new File("wiki/html/" + slug + ".html");
        if (readCache) {
            if (file.exists()) {
                return Jsoup.parse(file, "UTF-8", "");
            }
        }
        file.getParentFile().mkdirs();
        Document document = Jsoup.connect("https://politicsandwar.fandom.com/wiki/" + urlStub).get();
        Files.write(file.toPath(), document.outerHtml().getBytes());
        return document;
    }

    public static void fetchDefaultPages() throws IOException {
        Map<String, String> pagesToSave = new LinkedHashMap<>();
        pagesToSave.put("Frequently Asked Questions", "Frequently_Asked_Questions");
        pagesToSave.put("Paperless", "Paperless");

        String[] categoriesToSave = {"Wars", "Alliances", "Treaties", "Guides", "Mechanics", "API"};

        pagesToSave.putAll(getPages(categoriesToSave));

        // Save to sitemap.json
        File file = new File("wiki/sitemap.json");
        file.getParentFile().mkdirs();
        file.createNewFile();
        FileWriter writer = new FileWriter(file);
        WebUtil.GSON.toJson(pagesToSave, writer);
        writer.close();
        System.out.println("Write sitemap " + file.getAbsolutePath());
    }

    public static Map<String, String> getPages(String... categories) throws IOException {
        Map<String, String> pagesToSave = new LinkedHashMap<>();
        for (String category : categories) {
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
                if (hrefLower.contains("category:") || hrefLower.contains("user:") || hrefLower.contains("file:") || hrefLower.contains("template:") || hrefLower.contains("talk:")) {
                    continue;
                }
                pagesToSave.put(page, url);
            }
        }
        return pagesToSave;
    }

    public static Map<String, String> getSitemapCached() throws IOException {
        String filename = "wiki/sitemap.json";
        File file = new File(filename);

        // If the file does not exist, fetch and save to sitemap.json
        if (!file.exists()) {
            fetchDefaultPages();
        }

        // Load sitemap.json
        System.out.println("Loading " + file.getAbsolutePath());
        FileReader reader = new FileReader(filename);
        Map<String, String> sitemap = WebUtil.GSON.fromJson(reader, LinkedHashMap.class);
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
            return WebUtil.GSON.fromJson(reader, Map.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Map<String, Integer> getWikiAllianceIds() throws IOException {
        Map<String, Integer> ids = new Object2IntOpenHashMap<>();
        for (Map.Entry<String, String> entry : getPages("Alliances").entrySet()) {
            String text = entry.getKey();
            String href = entry.getValue();
            PWWikiPage page = new PWWikiPage(text, href, true);
            Integer id = page.getAllianceId();
            System.out.println(text + " = " + id);
            if (id != null) {
                ids.put(text, id);
            }
        }
        return ids;
    }

    public static void main(String[] args) throws IOException {
        PWWikiPage page = new PWWikiPage("Brawlywood", "Brawlywood", true);
        for (Map.Entry<String, DBTopic> entry : page.getForumLinks().entrySet()) {
            System.out.println(entry.getKey() + " = " + entry.getValue().topic_name);
        }


//        for (Map.Entry<String, String> entry : getPages("Alliances").entrySet()) {
//            String text = entry.getKey();
//            String href = entry.getValue();
//            PWWikiPage page = new PWWikiPage(text, href, true);
//            Integer id = page.getAllianceId();
//            System.out.println(text + " = " + id);
//        }

//        PWWikiPage page = new PWWikiPage("Great War 30", "Great_War_30", true);
//        page.getForumLinks();



//        String url = "https://forum.politicsandwar.com/index.php?/topic/398-what-is-pw-listening-to/";
//        Document document = Jsoup.connect(url).get();
//        Element time = document.select("time[datetime]").first();
//        if (time == null) {
//            System.out.println("No time");
//        } else {
//            String dateStr = time.attr("datetime");
//            DateTimeFormatter formatter = DateTimeFormatter.ISO_ZONED_DATE_TIME;
//            ZonedDateTime dateTime = ZonedDateTime.parse(dateStr, formatter);
//            long millis = dateTime.toInstant().toEpochMilli();
//            System.out.println(new Date(millis));
//        }
//
//        String category = "Alliance_Wars";
//        Map<String, String> pages = getPages(category);
//        System.out.println("Got pages");
//        long wikiCutoff = TimeUtil.getDay(1577836800000L);
//
//        List<String> errors = new ArrayList<>();
//
//        for (Map.Entry<String, String> entry : pages.entrySet()) {
//            String name = entry.getKey();
//            String nameNormal = StringMan.normalize(name);
//            String slug = slugify(name, false);
//            String urlStub = entry.getValue();
//
//            PWWikiPage page = new PWWikiPage(nameNormal, urlStub, true);
//            String ctowned = page.getCtownedLink();
//            if (ctowned != null) {
//                System.out.println(ctowned + " = " + nameNormal + " | " + urlStub);
//
//                continue;
//            } else {
//                continue;
//            }
////
////            Map<String, List<String>> table = page.getTableData();
////            if (table == null) continue;
////            Map.Entry<Long, Long> dates = page.getDates();
////            if (dates == null || dates.getKey() < wikiCutoff) continue;
////
////            if (dates.getValue() == null || dates.getValue() > TimeUtil.getDay()) {
////                errors.add("Ongoing war?? " + page.getUrl());
////                continue;
////            }
////
////            String status = page.getStatus();
////            List<String> cb = page.getTableData().get("casus belli");
////            if (cb == null) {
////                errors.add("No cb " + page.getTableData().keySet() + " | " + page.getUrl());
////                continue;
////            }
////            System.out.println("Has CB: " + name + " = " + cb);
////
////            if (status == null) {
////                errors.add("No status " + page.getUrl() + " | " + page.getTableData().keySet());
////                continue;
////            } else {
////                System.out.println("Status: " + status);
////            }
//        }
//        for (String error : errors) {
//            System.out.println(error);
//        }
    }

    public static Conflict loadWikiConflict(String name, String urlStub, Map<String, String> errorsByPage, boolean allowCache) throws IOException {
        String nameNormal = StringMan.normalize(name);

        PWWikiPage page = new PWWikiPage(nameNormal, urlStub, allowCache);
        Map<String, List<String>> table = page.getTableData();
        if (table == null) {
            errorsByPage.put(name, "No table found");
            return null;
        }
        Map.Entry<Long, Long> date = page.getDates();
        if (date == null) {
            List<String> dateList = table.get("date");
            if (dateList == null) {
                errorsByPage.put(name, "No date found");
            } else if (dateList.isEmpty()) {
                errorsByPage.put(name, "Empty date found");
            } else if (!dateList.get(0).contains("-")) {
                errorsByPage.put(name, "No end date specified (in the format `start date - end date`)");
            } else {
                errorsByPage.put(name, "Unparseable date range: `" + dateList.get(0) + "`");
            }
            return null;
        }
        Set<String> unknownAlliances = new ObjectLinkedOpenHashSet<>();
        Map.Entry<Set<Integer>, Set<Integer>> combatants = page.getCombatants(unknownAlliances, date.getKey());
        if (combatants == null) {
            errorsByPage.put(name, "No combatants found (unknown: " + unknownAlliances + ")");
            return null;
        }
//            if (!unknownAlliances.isEmpty()) {
//                errorsByPage.put(name, "Unknown alliances: " + unknownAlliances);
//                continue;
//            }
        if (combatants.getKey().isEmpty()) {
            errorsByPage.put(name, "No coalition 1 combatants found");
            return null;
        }
        if (combatants.getValue().isEmpty()) {
            errorsByPage.put(name, "No coalition 2 combatants found");
            return null;
        }
        Set<String> pageCategories = page.getCategories();
        boolean isGreatWar = pageCategories.contains("Great Wars");
        boolean isMicroWar = pageCategories.contains("Micro Wars");
        boolean isHistory = pageCategories.stream().anyMatch(category -> category.startsWith("Wars of "));
        ConflictCategory category;
        if (isGreatWar) {
            category = ConflictCategory.GREAT;
        } else if (isMicroWar) {
            category = ConflictCategory.MICRO;
        } else if (isHistory) {
            category = ConflictCategory.NON_MICRO;
        } else {
            category = ConflictCategory.UNVERIFIED;
        }
        String cb = page.getCasusBelli();
        String status = page.getStatus();
        long startTurn = TimeUtil.getTurn(date.getKey() * TimeUnit.DAYS.toMillis(1));
        long endTurn = date.getValue() == null ? Long.MAX_VALUE : TimeUtil.getTurn((date.getValue() + 1) * TimeUnit.DAYS.toMillis(1));
        Conflict conflict = new Conflict(0, 0, 0, category, nameNormal, "Coalition 1", "Coalition 2", urlStub, cb, status, startTurn, endTurn);
        combatants.getKey().forEach(allianceId -> conflict.addParticipant(allianceId, true, false, null, null));
        combatants.getValue().forEach(allianceId -> conflict.addParticipant(allianceId, false, false, null, null));
        for (Map.Entry<String, DBTopic> topicEntry : page.getForumLinks().entrySet()) {
            conflict.addAnnouncement(topicEntry.getKey(), topicEntry.getValue(), false);
        }
        return conflict;
    }

    /**
     * Note:
     * Conflicts lacking alliance name information will be skipped
     * Some alliance pages have dates in formats that cannot be parsed
     * @return
     */
    public static List<Conflict> loadWikiConflicts(Map<String, String> errorsByPage, boolean allowCache) throws IOException {
        List<Conflict> conflicts = new ArrayList<>();
        String wikiCategory = "Alliance_Wars";
        Map<String, String> pages = getPages(wikiCategory);
        for (Map.Entry<String, String> entry : pages.entrySet()) {
            String name = entry.getKey();
            String urlStub = entry.getValue();
            Conflict conflict = loadWikiConflict(name, urlStub, errorsByPage, allowCache);
            if (conflict != null) {
                conflicts.add(conflict);
            }
        }
        return conflicts;
    }


    public static String getWikiUrlFromCtowned(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            // #33 Closest (1/0)Step on Snek -> https://politicsandwar.fandom.com/wiki/Step_on_Snek
            case "step on snek" -> "Step_on_Snek";
            //#25 Closest (4/1)Judgement Day -> https://politicsandwar.fandom.com/wiki/Judgement_Day
            case "judgement day" -> "Judgement_Day";
            //#8 Closest (6/6)Ragnarok -> https://politicsandwar.fandom.com/wiki/Ragnar%C3%B6k
            case "ragnarok" -> "Ragnar%C3%B6k";
            //#9 Closest (2/1)Honor Thy Treaty -> https://politicsandwar.fandom.com/wiki/Training_Day
            case "honor thy treaty" -> "Honor_Thy_Treaty";
            //#24 Closest (0/1)Singularity vs HS -> https://politicsandwar.fandom.com/wiki/Breaking_The_News
            case "singularity vs hs" -> "Breaking_The_News";
            //#26 Closest (6/7)The Fault in Our Stars -> https://politicsandwar.fandom.com/wiki/The_Fault_in_Our_Stars
            case "the fault in our stars" -> "The_Fault_in_Our_Stars";
            //#16 Closest (14/6)Global War 28 -> https://politicsandwar.fandom.com/wiki/Dodge_This
            case "global war 28" -> "Dodge_This";
            //#17 Closest (3/3)Antarctic Expedition -> https://politicsandwar.fandom.com/wiki/Antarctic_Expedition
            case "antarctic expedition" -> "Antarctic_Expedition";
            //#21 Closest (2/3)Mental Midgetry -> https://politicsandwar.fandom.com/wiki/Mental_Midgetry
            case "mental midgetry" -> "Mental_Midgetry";
            //#23 Closest (5/1)Antarctic Expedition Part 2 -> https://politicsandwar.fandom.com/wiki/Aqua_War
            case "antarctic expedition part 2" -> "Aqua_War";
            //#27 Closest (6/4)The Way the Cookie Crumbles -> https://politicsandwar.fandom.com/wiki/The_Way_the_Cookie_Crumbles
            case "the way the cookie crumbles" -> "The_Way_the_Cookie_Crumbles";
            //#31 Closest (8/3)Singularly Hostile -> https://politicsandwar.fandom.com/wiki/Singular_Hostility
            case "singularly hostile" -> "Singular_Hostility";
            //#52 Closest (1/0)Size Does Matter -> https://politicsandwar.fandom.com/wiki/Size_Does_Matter
            case "size does matter" -> "Size_Does_Matter";
            //#32 Closest (14/6)New Year Nuke Me -> https://politicsandwar.fandom.com/wiki/Great_War_30
            case "new year nuke me" -> "Great_War_30";
            //#34 Closest (1/0)Broken Promises -> https://politicsandwar.fandom.com/wiki/Broken_Promises
            case "broken promises" -> "Broken_Promises";
            //#38 Closest (2/1)Paper Shortage -> https://politicsandwar.fandom.com/wiki/Paper_Shortage
            case "paper shortage" -> "Paper_Shortage";
            //#36 Closest (1/0)The Lost Debts -> https://politicsandwar.fandom.com/wiki/The_Lost_Debts
            case "the lost debts" -> "The_Lost_Debts";
            //#42 Closest (1/1)Fake News Eradication -> https://politicsandwar.fandom.com/wiki/SomaliArrgh_Hostage_Crisis
            case "fake news eradication" -> "SomaliArrgh_Hostage_Crisis";
            //#43 Closest (1/0)Arrgh Raid Ussr -> https://politicsandwar.fandom.com/wiki/Welcome_to_the_Gularrgh
            case "arrgh raid ussr" -> "Welcome_to_the_Gularrgh";
            //#44 Closest (3/1)Spanky and Piggys Little Misadventure -> https://politicsandwar.fandom.com/wiki/Super_Mario_64
            case "spanky and piggys little misadventure" -> "Super_Mario_64";
            //#45 Closest (0/0)Homicide for the Holidays -> https://politicsandwar.fandom.com/wiki/Homicide_for_the_Holidays
            case "homicide for the holidays" -> "Homicide_for_the_Holidays";
            //#40 Closest (1/1)Hong Kong Crisis -> https://politicsandwar.fandom.com/wiki/Hong_Kong_Crisis
            case "hong kong crisis" -> "Hong_Kong_Crisis";
            //#4 Closest (1/1)Under the Missile-Toe -> https://politicsandwar.fandom.com/wiki/Catatonic
            case "under the missile-toe" -> "Catatonic";
            //#12 Closest (13/5)World vs Fortuna -> https://politicsandwar.fandom.com/wiki/Darkest_Hour
            case "world vs fortuna" -> "Darkest_Hour";
            //#13 Closest (2/1)Guardian  HS vs Carthago -> https://politicsandwar.fandom.com/wiki/CTOwed
            case "guardian  hs vs carthago" -> "CTOwed";
            //#30 Closest (3/5)Singularitys Infra Shaving Exploit -> https://politicsandwar.fandom.com/wiki/Matrix_Assimilations
            case "singularitys infra shaving exploit" -> "Matrix_Assimilations";
            //#22 Closest (5/8)GW29 -> https://politicsandwar.fandom.com/wiki/That%27ll_Buff_Right_Out
            case "gw29" -> "That%27ll_Buff_Right_Out";
            //#2 Closest (1/4)Davey Jones Locker -> https://politicsandwar.fandom.com/wiki/Pirates_vs_Pacifists
            case "davey jones locker" -> "Pirates_vs_Pacifists";
            //#6 Closest (1/4)Cam vs Micros -> https://politicsandwar.fandom.com/wiki/Infinity_War
            case "cam vs micros" -> "Infinity_War";
            //#15 Closest (1/1)Arrgh vs SNB -> https://politicsandwar.fandom.com/wiki/Jolly_Roger
            case "arrgh vs snb" -> "Jolly_Roger";
            //#11 Closest (1/2)Covens Last Banzai -> https://politicsandwar.fandom.com/wiki/Alan_has_Fallen
            case "covens last banzai" -> "Alan_has_Fallen";
            //#10 Closest (6/2)World vs Aurora -> https://politicsandwar.fandom.com/wiki/Instant_Karma
            case "world vs aurora" -> "Instant_Karma";
            //#1 Closest (7/7)Clockattack -> https://politicsandwar.fandom.com/wiki/Vein_Has_A_Smoll_PP_War
            case "clockattack" -> "Vein_Has_A_Smoll_PP_War";
            //#3 Closest (6/12)Global War 26 -> https://politicsandwar.fandom.com/wiki/Bifr%C3%B6st_Blitz
            case "global war 26" -> "Bifr%C3%B6st_Blitz";
            //#5 Closest (6/4)New Year Firework -> https://politicsandwar.fandom.com/wiki/Steamed_HOGGs
            case "new year firework" -> "Steamed_HOGGs";
            // #20 Closest (0/0)Micro Brawl -> https://ctowned.net/conflicts/microbrawl
            case "micro brawl" -> "Alan_Tripped_and_Fell";
            // #14 Closest (0/0)KT vs UU -> https://politicsandwar.fandom.com/wiki/UU%27s_Intifada
            case "kt vs uu" -> "UU%27s_Intifada";
            // #7 Closest (0/1)Piracy in Atlantis -> https://politicsandwar.fandom.com/wiki/Atlantic_Vacation
            case "piracy in atlantis" -> "Atlantic_Vacation";
            // #19 Closest (0/0)CoA are the OGs -> https://politicsandwar.fandom.com/wiki/WTF_is_in_the_Media
            case "coa are the ogs" -> "WTF_is_in_the_Media";
            // #28 Closest (0/0)Cam vs Coal -> https://politicsandwar.fandom.com/wiki/Infinity_War
            case "cam vs coal" -> "Infinity_War";
            // #55 Closest (3/7)Blue Balled -> https://politicsandwar.fandom.com/wiki/Great_War_30
            case "blue balled" -> "Great_War_30";
            // #48 Closest (0/0)Counter Encounter -> https://politicsandwar.fandom.com/wiki/Wait,_do_Arrgh_counter%3F
            case "counter encounter" -> "Wait,_do_Arrgh_counter%3F";
            // #39 Closest (0/0)Maidenless Behavior -> https://politicsandwar.fandom.com/wiki/War_of_the_Roses
            case "maidenless behavior" -> "War_of_the_Roses";
            // #35 Closest (0/0)Turning Crimson -> https://politicsandwar.fandom.com/wiki/Turning_Crimson
            case "turning crimson" -> "Turning_Crimson";
            // #49 Closest (0/0)Pirates vs Scammers -> https://politicsandwar.fandom.com/wiki/Infinity_War
            case "pirates vs scammers" -> "Infinity_War";
            // #41 Closest (1/3)Rolling Micros -> https://politicsandwar.fandom.com/wiki/Welcome_to_the_Gularrgh
            case "rolling micros" -> "Welcome_to_the_Gularrgh";
            default -> null;
        };
    }

}
