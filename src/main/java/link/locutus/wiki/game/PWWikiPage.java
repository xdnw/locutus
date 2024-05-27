package link.locutus.wiki.game;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.conflict.ConflictManager;
import link.locutus.discord.db.ForumDB;
import link.locutus.discord.db.entities.DBTopic;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class PWWikiPage {
    private final String urlStub;
    private final String url;
    private final Document document;
    private final String name;
    private final String slug;
    private Map<String, List<String>> tableDate;
    private Map<String, DBTopic> forumLinks;

    public PWWikiPage(String name, String urlStub, boolean allowCache) throws IOException {
        String url = "https://politicsandwar.fandom.com/wiki/" + urlStub;
        this.urlStub = urlStub;
        this.slug = PWWikiUtil.slugify(name, false);
        this.url = url;
        this.name = name;
        this.document = PWWikiUtil.getDocument(slug, urlStub, allowCache);
    }

    public String getUrlStub() {
        return urlStub;
    }

    public String getSlug() {
        return slug;
    }

    public Document getDocument() {
        return document;
    }

    public String getUrl() {
        return url;
    }

    private LocalDate parseDate(String input) {
        input = input.replace(",2", " 2");
        input = input
                .replaceAll("([0-9])rd", "$1")
                .replaceAll("([0-9])th", "$1")
                .replaceAll("([0-9])st", "$1")
                .replaceAll("([0-9])nd", "$1");
        input = input.replace(",", "").toLowerCase(Locale.ROOT).trim();
        int index = input.indexOf("(");
        if (index == -1) index = input.indexOf("duration");
        if (index != -1) input = input.substring(0, index).trim();
        // `Sept ` to `September `
        if (input.contains("sept ")) input = input.replace("sept ", "september ");
        input = input.replace("Sept ", "September ");
        if (input.endsWith(".") || input.endsWith("*")) input = input.substring(0, input.length() - 1);

        String[] options = {"d MMMM yyyy", "MMMM d yyyy","d MMM yyyy", "MMM d yyyy","dd MMMM yyyy", "MMMM dd yyyy","dd MMM yyyy", "MMM dd yyyy", "MM/dd/yyyy"};
        for (String option : options) {
            try {
                DateTimeFormatter formatter = new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(option).toFormatter(Locale.ENGLISH);
                LocalDate parsed = LocalDate.parse(input, formatter);
                return parsed;
            } catch (DateTimeException e) {
                // ignore
            }
        }
        System.out.println("Error parsing date2: `" + input + "` for " + slug + " | https://politicsandwar.fandom.com/wiki/" + urlStub);
        return null;
    }

    public static void main(String[] args) throws IOException {
        String stub = "Great_War_30";
        PWWikiPage page = new PWWikiPage("Great War 30", stub, true);
        Set<String> categories = page.getCategories();
        System.out.println(categories);

        // Iterate wars on war page
        // add get war
    }

    public Set<String> getCategories() {
        // Get content of "page-header__categories" class
        Element categoriesElement = document.selectFirst("div.page-header__categories");
        if (categoriesElement != null) {
            Elements categoryLinks = categoriesElement.select("a");
            List<String> categoryList = new ArrayList<>();
            for (Element categoryLink : categoryLinks) {
                categoryList.add(categoryLink.text().trim());
            }
            // remove category if matches number more e.g. `4 more`
            categoryList.removeIf(category -> category.matches("\\d+ more"));
            return new HashSet<>(categoryList);
        }
        return new HashSet<>();
    }

//    private static Set<String> validYears = Set.of("2020", "2021", "2022", "2023", "2024", "2025");

    public Map.Entry<Long, Long> getDates() {
        List<String> dateList = getTableData().get("date");
        if (dateList == null || dateList.isEmpty()) {
            System.out.println("No date for: " + slug + " | https://politicsandwar.fandom.com/wiki/" + urlStub);
            System.out.println(getTableData());
            return null;
        }
        String dateStr = dateList.get(0);
        if (slug.equalsIgnoreCase("Roqpocalypse")) {
            dateStr = "02/05/2020 - 02/12/2020";
        }
//        if (!validYears.stream().anyMatch(dateStr::contains)) return null;
        String[] dates = dateStr.split("[ ]*-[ ]*");
        if (dates.length != 2) {
            System.out.println("Error parsing date (no - ): " + dateStr + " for " + slug + " | https://politicsandwar.fandom.com/wiki/" + urlStub);
            return null;
        }
        try {
            LocalDate startDate = parseDate(dates[0]);
            LocalDate endDate = dates[1].contains(" 20") || dates[1].contains("/20") || dates[1].contains(",20") ? parseDate(dates[1]) : null;
            if (startDate != null) {
                if (endDate == null && !dates[1].toLowerCase().contains("present") && !dates[1].toLowerCase().contains("ongoing")) {
                    System.out.println("No end date for: " + dates[1] + " | https://politicsandwar.fandom.com/wiki/" + urlStub);
                }
                return new AbstractMap.SimpleEntry<>(startDate.toEpochDay(), endDate == null ? null : endDate.toEpochDay());
            }
        } catch (DateTimeException e) {
            e.printStackTrace();
        }
        System.out.println("Error parsing date: " + dateStr + " for " + slug + " | https://politicsandwar.fandom.com/wiki/" + urlStub);
        return null;
    }

    public Map.Entry<Set<Integer>, Set<Integer>> getCombatants(Set<String> unknownCombatants, long dateStart) {
        List<String> combatants = getTableData().get("combatants");
        if (combatants == null || combatants.size() != 2) {
            System.out.println("Unknown combatants for: " + slug + " | https://politicsandwar.fandom.com/wiki/" + urlStub);
            return null;
        }
        // split by comma
        List<String> col1Names = StringMan.split(combatants.get(0), ",");
        List<String> col2Names = StringMan.split(combatants.get(1), ",");

        ConflictManager manager = Locutus.imp().getWarDb().getConflicts();
        Set<Integer> col1Ids = col1Names.stream().map(f -> {
            f = Normalizer.normalize(f, Normalizer.Form.NFKD);
            Integer id = manager.getAllianceId(f, dateStart);
            if (id == null) {
                Map.Entry<String, Double> similar = manager.getMostSimilar(f);
                System.out.println("Unknown combatant: `" + f + "` " + (similar == null ? "" : " | Similar: " + similar.getKey() + "=" + similar.getValue()));
//                System.out.println(" -  for " + slug + " | https://politicsandwar.fandom.com/wiki/" + urlStub);
                unknownCombatants.add(f);
            }
            return id;
        }).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Integer> col2Ids = col2Names.stream().map(f -> {
            f = Normalizer.normalize(f, Normalizer.Form.NFKD);
            Integer id = manager.getAllianceId(f, dateStart);
            if (id == null) {
                Map.Entry<String, Double> similar = manager.getMostSimilar(f);
                System.out.println("Unknown combatant: `" + f + "` " + (similar == null ? "" : " | Similar: " + similar.getKey() + "=" + similar.getValue()));
//                System.out.println(" -  for " + slug + " | https://politicsandwar.fandom.com/wiki/" + urlStub);
                unknownCombatants.add(f);
            }
            return id;
        }).filter(Objects::nonNull).collect(Collectors.toSet());
        return Map.entry(col1Ids, col2Ids);
    }

    private List<String> getCombatants(Element elem) {
        Elements links = elem.select("a");
        Elements paragraph = elem.select("p");
        List<String> result = new ArrayList<>();
        for (Element link : links) {
            String href = link.attr("href");
            if (!href.startsWith("/wiki/") && !href.startsWith("https://politicsandwar.fandom.com/wiki/")) continue;
            if (href.contains("File:") || href.contains("White_Peace")) continue;
            String text = link.text().trim();
            if (text.isEmpty()) {
                System.out.println("Empty combatant for: " + slug + " | " + href);
                continue;
            }
            if (!paragraph.isEmpty() && link.parent().parent().tag().normalName().equalsIgnoreCase("center") && (Locutus.imp() != null && Locutus.imp().getWarDb().getConflicts().getAllianceId(text, Long.MAX_VALUE) == null)) {
                System.out.println("Skipping bloc name: " + link.text() + " | " + slug + " | " + link.attr("href"));
                continue;
            }
            result.add(text);
        }
        return result;
    }

    public Map<String, List<String>> getTableData() {
        if (tableDate != null) return tableDate;
        // get tables
        Elements tables = document.select("table");
        // get first that isn't empty
        Element table = tables.stream().filter(e -> e.select("tr").size() > 1).findFirst().orElse(null);
        if (table == null) {
            System.out.println("No table for: " + slug + " | https://politicsandwar.fandom.com/wiki/" + urlStub);
            tableDate = new LinkedHashMap<>();
            return tableDate;
        }
        // iterate rows
        Elements rows = table.select("tr");
        Map<String, List<String>> data = new LinkedHashMap<>();
        for (Element row : rows) {
            // get TH
            Elements ths = row.select("th");
            // get td
            Elements tds = row.select("td");
            if (ths.size() != 1 || tds.size() != 1) {
                if (tds.size() == 2 || tds.size() == 3 && ths.isEmpty()) {
                    if (row.previousElementSibling().text().equalsIgnoreCase("combatants")) {
                        List<String> left = getCombatants(tds.get(0));
                        List<String> right = getCombatants(tds.get(1));
                        if (left.isEmpty()) {
                            System.out.println("No combatants (left) for: " + slug + " | https://politicsandwar.fandom.com/wiki/" + urlStub);
                            continue;
                        }
                        if (right.isEmpty()) {
                            System.out.println("No combatants (right) for: " + slug + " | https://politicsandwar.fandom.com/wiki/" + urlStub);
                            continue;
                        }
                        String col1 = StringMan.join(left, ",");
                        String col2 = StringMan.join(right, ",");
                        data.put("combatants", List.of(col1, col2));
                    }
                }
                continue;
            }
            Element th = ths.get(0);
            Element td = tds.get(0);
            // print text
            List<String> values = new ArrayList<>();
            String own = td.ownText().trim();
            if (!own.isEmpty()) {
                values.add(own);
            }
            for (Element child : td.children()) {
                if (child.tagName().equalsIgnoreCase("ol") || child.tagName().equalsIgnoreCase("ul")) {
                    child.select("li").forEach(e -> values.add(e.text()));
                } else {
                    values.add(child.text());
                }
            }
            data.put(th.text().toLowerCase(Locale.ROOT).trim(), values);
        }
        return tableDate = data;
    }

    public Map<String, DBTopic> getForumLinks() {
        if (this.forumLinks != null) return this.forumLinks;
        Map<String, String> result = new LinkedHashMap<>();
        Element output = document.select(".mw-parser-output").get(0);
        Elements links = output.select("a");
        for (Element link : links) {
            String desc = null;
            Elements parents = link.parents();
            for (Element parent : parents) {
                if (parent.tagName().equalsIgnoreCase("li")) {
                    desc = parent.text();
                    break;
                }
            }
            if (desc == null) {
                desc = link.text();
            }
            String href = link.attr("href");
            String startsWith = "https://forum.politicsandwar.com/index.php?/topic/";
            if (href != null && href.startsWith(startsWith)) {
                href = href.substring(startsWith.length()).split("[?/]")[0];
                result.put(href, desc);
            }
        }
        // iterate h2
//        Elements h2s = output.select("h2");
//        for (Element h2 : h2s) {
//            String header = h2.text().replaceAll("[\\[\\]]", "").toLowerCase(Locale.ROOT).trim();
//            switch (header) {
//                case "announcement","announcements","related links", "announcements and events", "links & announcements", "events", "result", "forum announcements", "timeline", "links", "time-line", "announcements and links", "announcement and related links", "addendum" -> {
//                    // iterate next siblings
//                    Element next = h2.nextElementSibling();
//                    while (next != null && !next.tagName().equalsIgnoreCase("h2")) {
//                        // get href links
//                        Elements links = next.select("a");
//                        next = next.nextElementSibling();
//                    }
//                }
//            }
//        }
        Map<String, DBTopic> postName = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : result.entrySet()) {
            String[] split = entry.getKey().split("-", 2);
            String idStr = split[0];
            int id = Integer.parseInt(idStr);
            ForumDB forumDb = Locutus.imp().getForumDb();
            if (forumDb == null) {
                throw new IllegalArgumentException("No `forum-feed-server` setup in `config.yml`");
            }
            DBTopic topic = forumDb.getTopic(id);
            if (topic == null) {
                try {
                    topic = forumDb.loadTopic(id, split[1]);
                } catch (SQLException | IOException e) {
                    System.out.println("Skipping forum topic: " + entry.getKey() + " | " + entry.getValue() + " | " + slug + " | https://politicsandwar.fandom.com/wiki/" + urlStub);
                    e.printStackTrace();
                }
            }
            if (topic != null) {
                System.out.println("Add post " + entry.getValue());
                postName.put(entry.getValue(), topic);
            }
        }

        forumLinks = postName;
        return forumLinks;
    }

    public String getStatus() {
        Map<String, List<String>> table = getTableData();
        if (table == null) return null;
        List<String> status = table.get("status");
        if (status == null) {
            // result
            status = table.get("result");
        }
        if (status != null) {
            return StringMan.join(status, "\n");
        }
        return null;
    }

    public String getCtownedLink() {
        Element element = document.select("a[href^='https://ctowned.net/']").first();
        return element != null ? element.attr("href") : null;
    }

    public String getCasusBelli() {
        Map<String, List<String>> table = getTableData();
        List<String> cb = table == null ? null : table.get("casus belli");
        return cb == null ? null : StringMan.join(cb, "\n");
    }

    public Integer getAllianceId() {
        Elements infobox = document.select(".infobox");
        if (infobox == null) return null;
        Element aaPage = infobox.select("img[alt='Alliancepage']").first();
        if (aaPage != null) {
            Element link = aaPage.parent();
            if (link.tagName().equalsIgnoreCase("a")) {
                String href = link.attr("href");
                int index = href.indexOf("/id=");
                if (index != -1) {
                    String idStr = href.substring(index + 4).split("\\?")[0].split("&")[0].replace("/", "");
                    if (MathMan.isInteger(idStr)) {
                        return Integer.parseInt(idStr);
                    } else {
                        System.out.println("Invalid alliance id: " + idStr + " for " + slug + " | https://politicsandwar.fandom.com/wiki/" + urlStub);
                    }
                }
            }
        }
        return null;
    }
}
