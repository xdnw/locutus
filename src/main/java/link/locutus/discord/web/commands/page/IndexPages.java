package link.locutus.discord.web.commands.page;

import com.google.gson.Gson;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
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
import link.locutus.discord.web.commands.binding.AuthBindings;
import link.locutus.discord.web.commands.search.SearchResult;
import link.locutus.discord.web.commands.search.SearchType;
import link.locutus.discord.web.jooby.PageHandler;
import link.locutus.discord.web.jooby.WebRoot;
import link.locutus.discord.config.Settings;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import io.javalin.http.Context;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import rocker.guild.memberindex;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class IndexPages extends PageHelper {

    @Command
    public Object argTypes() {
        CommandManager2 manager = Locutus.imp().getCommandManager().getV2();
        ValueStore<Object> store = manager.getStore();
        return "Arg types stub";
    }

    @Command
    public Object index(Context context) throws IOException {
        AuthBindings.Auth auth = AuthBindings.getAuth(context, false, false, false);
        if (auth == null) {
            return "Not logged in";
        }
        StringBuilder response = new StringBuilder();
        response.append("Index page:\n");
        response.append("User: ").append(auth.userId()).append("\n");
        response.append("nation: ").append(auth.nationId()).append("\n");
        response.append("Valid: ").append(auth.isValid()).append("\n");
        Guild guild = AuthBindings.guild(context, auth.getNation(), auth.getUser(), false);
        response.append("Guild: ").append(guild).append("\n");
        return response.toString();
    }

    @Command
    public Object search(@Me GuildDB db, String term) {
        // TODO simplify the code
        term = URLDecoder.decode(term).trim();

        double nameEquals = 100;
        double nameContains = 50;
        double descContainsWord = 20;
        double descContainsText = 10;

        String termLow = term.toLowerCase();

        String urlBase = Settings.INSTANCE.WEB.REDIRECT + "/" + db.getIdLong() + "/";
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
                Set<DBNation> nations = alliance.getNations(true, 0, true);
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
        return rocker.command.search.template(term, results).render().toString();
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
        String cmdEndpoint = Settings.INSTANCE.WEB.REDIRECT + "/" + db.getIdLong() + "/command/";
        String pageEndpoint = Settings.INSTANCE.WEB.REDIRECT + "/" + db.getIdLong() + "/";

        result.append(
                commands.toHtml(stack.getStore(), stack.getPermissionHandler(), cmdEndpoint)
        );
        result.append(
                pages.toHtml(stack.getStore(), stack.getPermissionHandler(), pageEndpoint)
        );

        return rocker.command.guildindex.template(stack.getStore(), stack.getPermissionHandler(), cmdEndpoint, commands, pageEndpoint, pages).render().toString();
    }

    @Command()
    public Object register(Context context, @Default @Me GuildDB current, @Default @Me User user, @Default @Me DBNation nation) throws IOException {
        if (user == null) {
            return PageHelper.redirect(context, AuthBindings.getDiscordAuthUrl());
        }
        AuthBindings.Auth auth = AuthBindings.getAuth(context, true, true, true);
        return "You are already registered";
    }

    @Command()
    public Object login(Context context, @Default @Me GuildDB current, @Default @Me User user, @Default @Me DBNation nation) throws IOException {
        Map<String, List<String>> queries = context.queryParamMap();
        boolean requireNation = queries.containsKey("nation");
        boolean requireUser = queries.containsKey("discord");
        AuthBindings.Auth auth = AuthBindings.getAuth(context, true, requireNation, requireUser);
        if (auth != null) {
            // return and redirect
            String url = AuthBindings.getRedirect(context, false);
            if (url != null) {
                return PageHelper.redirect(context, url);
            }
            Map<String, Object> result = new HashMap<>();
            if (nation != null) {
                result.put("nation", nation.getId());
            }
            if (user != null) {
                result.put("discord", user.getId());
            }
            if (current != null) {
                result.put("guild", current.getId());
            }
            return new Gson().toJson(result);
        } else {
            Map<String, String> result = new HashMap<>();
            result.put("error", "You are not logged in, add `nation` or `discord` to the query string to require login");
            return new Gson().toJson(result);
        }
    }

    @Command()
    public Object setguild(Context context, Guild guild) {
        AuthBindings.setGuild(context, guild);
        return PageHelper.redirect(context, AuthBindings.getRedirect(context));
    }

    @Command()
    public Object guildselect(Context context, ValueStore store, @Default @Me GuildDB current, @Default @Me User user, @Default @Me DBNation nation) {
        if (user == null && nation == null) {
            new Exception().printStackTrace();
            // need to login
            // return WM login page
            // throw error
            User user2 = (User) store.getProvided(Key.of(User.class, Me.class), false);
            DBNation nation2 = (DBNation) store.getProvided(Key.of(DBNation.class, Me.class), false);
            String user2Str = user2 == null ? "null" : user2.getName();
            String nation2Str = nation2 == null ? "null" : nation2.getName();
            return "You are not logged in | " + user2Str + " | " + nation2Str;
        }
        System.out.println("Current " + (current == null ? null : current.getName()));
        JDA jda = Locutus.imp().getDiscordApi().getApis().iterator().next();
        String registerLink = (user == null || nation == null) ? CM.register.cmd.toCommandUrl() : null;
        String locutusInvite = null;
        String joinLink = nation != null && nation.getAlliance_id() == 0 ? Settings.INSTANCE.PNW_URL() + "/alliances/" : null;

        Set<GuildDB> dbs = new LinkedHashSet<>();

        if (current != null) {
            dbs.add(current);
        }

        GuildDB allianceDb = null;

        if (nation != null) {
            DBAlliance alliance = nation.getAlliance();
            if (alliance != null) {
                allianceDb = alliance.getGuildDB();
                if (allianceDb != null) {
                    dbs.add(allianceDb);
                    locutusInvite = jda.getInviteUrl(Permission.ADMINISTRATOR);
                }
            }
        }
        if (user != null) {
            for (Guild guild : user.getMutualGuilds()) {
                GuildDB db = Locutus.imp().getGuildDB(guild);
                dbs.add(db);
            }
        }
        return rocker.guild.guilds.template(dbs, current, allianceDb, registerLink, locutusInvite, joinLink).render().toString();
    }

    @Command()
    public Object logout(Context context) throws IOException {
        WebRoot.getInstance().getPageHandler().logout(context);
        return "Logging out. If you are not redirected, please visit <a href=\"" + Settings.INSTANCE.WEB.REDIRECT + "\">" + Settings.INSTANCE.WEB.REDIRECT + "</a>";
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
        System.out.println(((-start) + (start = System.currentTimeMillis())) + "ms (2)");
        List<AbstractCursor> attacks = myWars.isEmpty() ? Collections.emptyList() : Locutus.imp().getWarDb().getAttacksByWars(myWars);
        System.out.println(((-start) + (start = System.currentTimeMillis())) + "ms (3)");
        boolean isFightingActives = false;

        Collection<JavaCity> cities = myWars.isEmpty() ? null : nation.getCityMap(false, false).values();
        System.out.println(((-start) + (start = System.currentTimeMillis())) + "ms (4)");

        for (DBWar war : myWars) {
            List<AbstractCursor> warAttacks = attacks.stream().filter(f -> f.getWar_id() == war.warId).collect(Collectors.toList());
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
        if (db.isWhitelisted() && db.hasAlliance()) {
            System.out.println(((-start) + (start = System.currentTimeMillis())) + "ms (5.1)");
            try {
                IACheckup checkup = new IACheckup(db, db.getAllianceList(), true);
                checkupResult = checkup.checkup(nation, true, true);
                checkupResult.entrySet().removeIf(f -> f.getValue() == null || f.getValue().getValue() == null);
            } catch (InterruptedException | ExecutionException | IOException e) {
                e.printStackTrace();
            }
        }
        double[] deposits = nation.getNetDeposits(db, -1L, true);
        System.out.println(((-start) + (start = System.currentTimeMillis())) + "ms (7)");

        List<Announcement.PlayerAnnouncement> announcements = db.getPlayerAnnouncementsByNation(nation.getNation_id(), true);

        String result = memberindex.template(guild, db, nation, author, deposits, checkupResult, cities, isFightingActives, offensiveWars, defensiveWars, warCards, recommendedAttack, announcements).render().toString();
        System.out.println(((-start) + (start = System.currentTimeMillis())) + "ms (8)");
        return result;
    }

    @Command()
    @RolePermission(Roles.MEMBER)
    public Object allianceIndex(@Me User user, int allianceId) {
        DBAlliance alliance = DBAlliance.getOrCreate(allianceId);
        GuildDB db = alliance.getGuildDB();
        Guild guild = db != null ? db.getGuild() : null;

        String url = alliance.getUrl();
        Set<DBNation> nations = alliance.getNations();

        // 1 view wars
        // 2 view members

        return rocker.alliance.allianceindex.template(db, guild, alliance, user).render().toString();
    }
}