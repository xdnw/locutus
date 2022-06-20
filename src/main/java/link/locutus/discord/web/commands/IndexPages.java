package link.locutus.discord.web.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Announcement;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.task.ia.IACheckup;
import link.locutus.discord.util.task.war.WarCard;
import link.locutus.discord.web.commands.search.SearchResult;
import link.locutus.discord.web.commands.search.SearchType;
import link.locutus.discord.web.jooby.PageHandler;
import link.locutus.discord.web.jooby.WebRoot;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import io.javalin.http.Context;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import views.guild.memberindex;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class IndexPages {
    @Command
    public Object search(@Me GuildDB db, String term) {
        // TODO simplify the code
        term = URLDecoder.decode(term).trim();

        double nameEquals = 100;
        double nameContains = 50;
        double descContainsWord = 20;
        double descContainsText = 10;

        String termLow = term.toLowerCase();

        String urlBase = WebRoot.REDIRECT + "/" + db.getIdLong() + "/";
        String cmdUrl = urlBase + "command/";

        List<SearchResult> results = new ArrayList<>();

        BiConsumer<String, CommandCallable> cmdSearch = new BiConsumer<String, CommandCallable>() {
            @Override
            public void accept(String cmdPrefix, CommandCallable callable) {
                double val = 0;
                for (String alias : callable.aliases()) {
                    if (alias.equals(termLow)) {
                        val = nameEquals;
                        break;
                    }
                    if (alias.contains(termLow)) {
                        val = Math.max(val, nameContains - Math.abs(termLow.length() - alias.length()));
                    }
                }
                {
                    int count = StringMan.countWords(callable.simpleDesc() + " " + callable.simpleHelp(), termLow);
                    if (count > 0) val = Math.max(val, count + descContainsWord);
                }
                if (val == 0) {
                    int count = StringUtils.countMatches(callable.simpleDesc() + callable.simpleHelp(), termLow);
                    if (count > 0) val = Math.max(val, count + descContainsText);
                }

                if (val > 0) {
                    String url = cmdPrefix + callable.getFullPath("/");

                    StringBuilder body = new StringBuilder();
                    body.append(callable.simpleDesc());

                    results.add(new SearchResult(callable.getFullPath(" "),
                            callable.simpleDesc(),
                            url,
                            val,
                            SearchType.COMMAND));
                }
            }
        };

        recursive(Locutus.imp().getCommandManager().getV2().getCommands(), f -> cmdSearch.accept(urlBase + "command/", f));
        recursive(WebRoot.getInstance().getPageHandler().getCommands(), f -> cmdSearch.accept(urlBase + "/", f));


        for (DBNation nation : Locutus.imp().getNationDB().getNations().values()) {
            double val = 0;
            if ((nation.getNation_id() + "").equals(termLow)) {
                val = 200;
            } else if (nation.getNation().equalsIgnoreCase(termLow)) {
                val = nameEquals;
            } else if (nation.getLeader().equalsIgnoreCase(termLow)) {
                val = nameEquals - 0.5;
            } else if (nation.getVm_turns() == 0 && nation.getActive_m() < 10000 && termLow.length() > 3) {
                if (nation.getNation().toLowerCase().contains(termLow)) {
                    val = nameContains - Math.abs(termLow.length() - nation.getNation().length());
                } else if (nation.getLeader().toLowerCase().contains(termLow)) {
                    val = nameContains - Math.abs(termLow.length() - nation.getLeader().length());
                }
            }

            if (val > 0) {
                String url = nation.getNationUrl();
                String body = "leader: " + nation.getLeader() + "<br>" + MarkupUtil.markdownToHTML(nation.toMarkdown());
                results.add(new SearchResult(nation.getNation(),
                        body,
                        url,
                        val,
                        SearchType.NATION));
            }
        }

        for (DBAlliance alliance : Locutus.imp().getNationDB().getAlliances(false, false, false, 9999)) {
            double val = 0;
            String name = alliance.getName();
            if ((alliance.getId() + "").equals(termLow)) {
                val = 200;
            } else if (name.equalsIgnoreCase(termLow)) {
                val = nameEquals;
            } else if (name.replaceAll("[a-z ]", "").equalsIgnoreCase(termLow)) {
                val = nameEquals - 0.5;
            } else if (WordUtils.capitalizeFully(name).replaceAll("[a-z ]", "").equalsIgnoreCase(termLow)) {
                val = nameEquals - 0.6;
            } else if (WordUtils.capitalizeFully(name).replaceAll("The", "").replaceAll("[a-z ]", "").equalsIgnoreCase(termLow)) {
                val = nameEquals - 0.7;
            } else if (name.startsWith(termLow)) {
                val = nameEquals - 0.8;
            } else if (termLow.length() > 3 && name.toLowerCase().contains(termLow)) {
                val = nameContains - Math.abs(termLow.length() - name.length());
            }

            if (val > 0) {
                String url = alliance.getUrl();
                StringBuilder body = new StringBuilder();
                List<DBNation> nations = alliance.getNations(true, 0, true);
                DBNation total = alliance.getMembersTotal();
                body.append("Nations: " + nations.size()).append("<br>");
                body.append("Score: " + total.getScore()).append("<br>");
                body.append("Cities: " + total.getCities()).append("<br>");
                results.add(new SearchResult(name,
                        body.toString(),
                        url,
                        val,
                        SearchType.ALLIANCE));
            }
        }


        Collections.sort(results, (o1, o2) -> Double.compare(o2.match - o2.type.ordinal(), o1.match - o1.type.ordinal()));

        PageHandler pageHandler = WebRoot.getInstance().getPageHandler();
//        pageHandler.getCommands()
        return views.command.search.template(term, results).render().toString();
    }

    private void recursive(CommandCallable root, Consumer<CommandCallable> onEach) {
        onEach.accept(root);
        if (root instanceof CommandGroup){
            CommandGroup group = (CommandGroup) root;
            for (CommandCallable cmd : new HashSet<>(group.getSubcommands().values())) {
                recursive(cmd, onEach);
            }
        }
    }

    @Command()
    public Object guildindex(@Me GuildDB db, @Me User user, ArgumentStack stack) {
        CommandGroup commands = Locutus.imp().getCommandManager().getV2().getCommands();
        CommandGroup pages = WebRoot.getInstance().getPageHandler().getCommands();

        StringBuilder result = new StringBuilder();
        String cmdEndpoint = WebRoot.REDIRECT + "/" + db.getIdLong() + "/command/";
        String pageEndpoint = WebRoot.REDIRECT + "/" + db.getIdLong() + "/";

        result.append(
                commands.toHtml(stack.getStore(), stack.getPermissionHandler(), cmdEndpoint)
        );
        result.append(
                pages.toHtml(stack.getStore(), stack.getPermissionHandler(), pageEndpoint)
        );

        return views.command.guildindex.template(stack.getStore(), stack.getPermissionHandler(), cmdEndpoint, commands, pageEndpoint, pages).render().toString();
    }

    @Command()
    public Object index(@Me User user) {
        List<GuildDB> dbs = new ArrayList<>();
        for (Guild guild : user.getMutualGuilds()) {
            GuildDB db = Locutus.imp().getGuildDB(guild);
            dbs.add(db);
        }
        return views.guild.guilds.template(dbs).render().toString();
    }

    @Command()
    public Object logout(Context context) throws IOException {
        WebRoot.getInstance().logout(context);
        return "Logging out. If you are not redirected, please visit <a href=\"" + WebRoot.REDIRECT + "\">" + WebRoot.REDIRECT + "</a>";
    }

    @Command()
    @RolePermission(Roles.MEMBER)
    public Object guildMemberIndex(@Me Guild guild, @Me GuildDB db, @Me DBNation me, @Me User author, @Default DBNation nation) throws IOException {
        System.out.println("NATION " + nation);
        if (nation == null) nation = me;
        if (nation.getNation_id() != me.getNation_id() && !Roles.INTERNAL_AFFAIRS_STAFF.has(author, guild) && !Roles.MILCOM.has(author, guild)) {
            return "You do not have permission to view another nations page";
        }
        Map<DBWar, DBNation> offensiveWars = new LinkedHashMap<>();
        Map<DBWar, DBNation> defensiveWars = new LinkedHashMap<>();

        Map<DBWar, WarCard> warCards = new HashMap<>();
        Map<DBWar, AttackType> recommendedAttack = new HashMap<>();

        long start = System.currentTimeMillis();
        System.out.println(((-start) + (start = System.currentTimeMillis())) + "ms (0)");
        List<DBWar> myWars = nation.getActiveWars();
//        myWars = nation.getWars().subList(0, 5);
        System.out.println(((-start) + (start = System.currentTimeMillis())) + "ms (1)");

        Collections.sort(myWars, Comparator.comparingLong(o -> o.date));
        Collections.reverse(myWars);
        List<Integer> warIds = myWars.stream().map(f -> f.warId).collect(Collectors.toList());
        System.out.println(((-start) + (start = System.currentTimeMillis())) + "ms (2)");
        List<DBAttack> attacks = myWars.isEmpty() ? Collections.emptyList() : Locutus.imp().getWarDb().getAttacksByWarIds(warIds);
        System.out.println(((-start) + (start = System.currentTimeMillis())) + "ms (3)");
        boolean isFightingActives = false;

        Collection<JavaCity> cities = myWars.isEmpty() ? null : nation.getCityMap(false, false).values();
        System.out.println(((-start) + (start = System.currentTimeMillis())) + "ms (4)");

        for (DBWar war : myWars) {
            List<DBAttack> warAttacks = attacks.stream().filter(f -> f.war_id == war.warId).collect(Collectors.toList());
            WarCard warcard = new WarCard(war, warAttacks, false, false, false);
            warCards.put(war, warcard);

            boolean isAttacker = war.isAttacker(nation);
            DBNation other = war.getNation(!isAttacker);

            if (other.getActive_m() < 1440) {
                isFightingActives = true;
            }

            if (isAttacker) {
                offensiveWars.put(war, other);
            } else {
                defensiveWars.put(war, other);
            }
        }
        System.out.println(((-start) + (start = System.currentTimeMillis())) + "ms (5)");

        Map<IACheckup.AuditType, Map.Entry<Object, String>> checkupResult = new HashMap<>();
        if (db.isWhitelisted() && db.getOrNull(GuildDB.Key.ALLIANCE_ID) != null) {
            System.out.println(((-start) + (start = System.currentTimeMillis())) + "ms (5.1)");
            try {
                IACheckup checkup = new IACheckup(db, db.getAlliance_id(), true);
                checkupResult = checkup.checkup(nation, true, true);
                checkupResult.entrySet().removeIf(f -> f.getValue() == null || f.getValue().getValue() == null);
            } catch (InterruptedException | ExecutionException | IOException e) {
                e.printStackTrace();
            }
        }
        double[] deposits = nation.getNetDeposits(db, -1L);
        System.out.println(((-start) + (start = System.currentTimeMillis())) + "ms (7)");

        List<Announcement.PlayerAnnouncement> announcements = db.getPlayerAnnouncementsByNation(nation.getNation_id(), true);

        String result = memberindex.template(guild, db, nation, author, deposits, checkupResult, cities, isFightingActives, offensiveWars, defensiveWars, warCards, recommendedAttack, announcements).render().toString();
        System.out.println(((-start) + (start = System.currentTimeMillis())) + "ms (8)");
        return result;
    }

    @Command()
    @RolePermission(Roles.MEMBER)
    public Object allianceIndex(@Me User user, int allianceId) {
        DBAlliance alliance = new DBAlliance(allianceId);
        GuildDB db = alliance.getGuildDB();
        Guild guild = db != null ? db.getGuild() : null;

        String url = alliance.getUrl();
        List<DBNation> nations = alliance.getNations();

        // 1 view wars
        // 2 view members

        return views.alliance.allianceindex.template(db, guild, alliance, user).render().toString();
    }

    @Command()
    public Object testIndex(@Me User user) {
//        NationPlaceholders placeholders = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
//
//        List<NationMetricDouble> metricsDouble = placeholders.getMetricsDouble(store);
//        List<NationMetric> metricsString = placeholders.getMetrics(store);
//        metricsString.removeIf(f -> f.getType() != String.class);

        return "hello world";
    }

    public Object nationsEndpoint(String filters) {
        return "TODO";
    }
}