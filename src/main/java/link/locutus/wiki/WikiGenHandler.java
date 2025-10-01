package link.locutus.wiki;

import com.google.gson.JsonObject;
import link.locutus.wiki.pages.*;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.gpt.pw.PWGPTHandler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class WikiGenHandler {

    private final CommandManager2 manager;
    private final PWGPTHandler gpt;
    private final String pathRelative;

    public WikiGenHandler(String pathRelative, CommandManager2 manager) {
        this.manager = manager;
        this.gpt = manager.getGptHandler();
        this.pathRelative = pathRelative;
    }

    public List<BotWikiGen> getIntroPages() {
        List<BotWikiGen> pages = new ArrayList<>();
        pages.add(new WikiSetupPage(manager));
        pages.add(new WikiHostingLocutus(manager));
        return pages;
    }

    public List<BotWikiGen> getCommandPages() {
        List<BotWikiGen> pages = new ArrayList<>();
        pages.add(new WikiCommandsPage(manager));
        pages.add(new WikiSettingsPage(manager));
        pages.add(new WikiArgumentsPage(manager));
        pages.add(new WikiPermsPage(manager));
        return pages;
    }

    public List<BotWikiGen> getTopicPages() {
        List<BotWikiGen> pages = new ArrayList<>();

        pages.add(new WikiWarAlertsPage(manager));
//        //Auto masking
        pages.add(new WikiAutoMaskingPage(manager));
        // Embassies
        pages.add(new WikiEmbassyPage(manager));
        //Deposits System
        pages.add(new WikiDepositsPage(manager));
        pages.add(new WikiConversionPage(manager));
        //Deposits System
        pages.add(new WikiBankFlows(manager));
//        //Setup an offshore or bank
        pages.add(new WikiBankPage(manager));
        // Grant templates
        pages.add(new WikiGrantTemplate(manager));
//        //Interview System
        pages.add(new WikiInterviewPage(manager));
        //Recruitment System
        pages.add(new WikiRecruitmentPage(manager));
//        //DNR system
        pages.add(new WikiDNRPage(manager));
//        //Coalitions system
        pages.add(new WikiCoalitionsPage(manager));
//        //War Room System
        pages.add(new WikiWarRoomPage(manager));
//        //Countering
        pages.add(new WikiCounteringPage(manager));
        //        //Tax Bracket Automation
        pages.add(new WikiTaxBracketPage(manager));
        //Auditing System / Alliance MMR requirements
        pages.add(new WikiAuditingPage(manager));
//        //Finding Targets / War panels
        pages.add(new WikiFindingTargetsPage(manager)); // <--- 2
//        //Escrow System
        pages.add(new WikiEscrowPage(manager));
//        //Plan a blitz
        pages.add(new WikiBlitzPage(manager)); // <--- 3
//        //Send out targets
        pages.add(new WikiSendTargetsPage(manager));
//        //Spy war
        pages.add(new WikiSpyWarPage(manager)); // <--- easy 5
//        //Blockade System
        pages.add(new WikiBlockadePage(manager));
//        //Beige cycling tutorial
        pages.add(new WikiBeigeCyclingPage(manager));
//        //Loan System
        pages.add(new WikiLoanPage(manager));
//        //Report System
        pages.add(new WikiReportPage(manager));
//        //Anti Leak System
        pages.add(new WikiAntiLeakPage(manager));
        //Making custom embeds
        pages.add(new WikiCustomEmbeds(manager));
        pages.add(new WikiDelegateServers(manager)); // <--- 6
//        //Making custom spreadsheets
        pages.add(new WikiCustomSheetsPage(manager)); // <--- 7
//        //Statistics System
        pages.add(new WikiStatisticsPage(manager)); // <--- 8
//        //Trade System (use trading guide)
//        //- Link DocScripts trade tool (cause useful)
        pages.add(new WikiTradePage(manager)); // <--- 9
        pages.add(new WikiSelfRoles(manager)); // <--- 10

        pages.add(new WikiAiTools(manager)); // <--- 10
        return pages;
    }


    public List<BotWikiGen> getPlaceholderPages() {
        List<BotWikiGen> pages = new ArrayList<>();
        PlaceholdersMap placeholderMap = Locutus.cmd().getV2().getPlaceholders();
        List<Class> types = new ArrayList<>(placeholderMap.getTypes());
        Collections.sort(types, Comparator.comparing(PlaceholdersMap::getClassName));
        for (Class type : types) {
            pages.add(new WikiPlaceholderPage(manager, placeholderMap, type));
        }
        return pages;
    }

    public void writeDefaults() throws IOException {
        List<BotWikiGen> pages = new ArrayList<>();

        // register defaults
        pages.addAll(getIntroPages());
        pages.addAll(getCommandPages());
        pages.addAll(getTopicPages());
        pages.removeIf(f -> f instanceof WikiPermsPage);
        // Placeholders
        List<BotWikiGen> placeholderPages = getPlaceholderPages();
        WikiPermsPage permsPage = new WikiPermsPage(manager);
        WikiHelpPage help = new WikiHelpPage(manager, pages, placeholderPages, permsPage);
        pages.add(help);

        ArrayList<BotWikiGen> allPages = new ArrayList<>();
        allPages.addAll(placeholderPages);
        allPages.addAll(pages);
        allPages.add(permsPage);
        for (BotWikiGen page : allPages) {
            writePage(page);
        }

        // Unlisted
        writePage(new WikiConflictsPage(manager));
    }

    private void writePage(BotWikiGen page) throws IOException {
        String markdown = page.generateMarkdown().trim();
        if (markdown.isEmpty()) {
            return;
        }
        File file = new File(pathRelative + File.separator + page.getPageName().toLowerCase().replace(" ", "_") + ".md");
        Files.write(file.toPath(), markdown.getBytes());
    }

    public JsonObject generateSiteMap() {
        JsonObject wiki = new JsonObject();
        wiki.addProperty("home", "");
        JsonObject wikiIntro = new JsonObject();
        for (BotWikiGen page : getIntroPages()) {
            wikiIntro.addProperty(page.getPageName(), "");
        }
        wiki.add("intro", wikiIntro);
        JsonObject wikiTopics = new JsonObject();
        for (BotWikiGen page : getTopicPages()) {
            wikiTopics.addProperty(page.getPageName(), "");
        }
        wiki.add("topic", wikiTopics);
        JsonObject wikiCommands = new JsonObject();
        for (BotWikiGen page : getCommandPages()) {
            wikiCommands.addProperty(page.getPageName(), "");
        }
        wiki.add("command", wikiCommands);
        return wiki;
    }
}
