package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import com.google.gson.JsonObject;
import com.politicsandwar.graphql.model.ApiKeyDetails;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv2.PoliticsAndWarV2;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.commands.alliance.LeftAA;
import link.locutus.discord.commands.bank.AddBalance;
import link.locutus.discord.commands.bank.Warchest;
import link.locutus.discord.commands.compliance.CheckCities;
import link.locutus.discord.commands.external.guild.CopyPasta;
import link.locutus.discord.commands.external.guild.KeyStore;
import link.locutus.discord.commands.info.Rebuy;
import link.locutus.discord.commands.info.Reroll;
import link.locutus.discord.commands.info.Revenue;
import link.locutus.discord.commands.info.UnitHistory;
import link.locutus.discord.commands.info.optimal.OptimalBuild;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Filter;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.TextArea;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasOffshore;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RankPermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.rankings.builder.NumericGroupRankBuilder;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.commands.trade.FindProducer;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.AddBalanceBuilder;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.offshore.test.IACategory;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.util.task.ia.IACheckup;
import net.dv8tion.jda.api.entities.*;
import org.json.JSONObject;
import rocker.guild.ia.message;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UnsortedCommands {
    @Command(desc ="View nation or AA bank contents")
    @RolePermission(Roles.MEMBER)
    @IsAlliance
    public String stockpile(@Me IMessageIO channel, @Me Guild guild, @Me GuildDB db, @Me DBNation me, @Me User author, NationOrAlliance nationOrAlliance) throws IOException {
        Map<ResourceType, Double> totals = new HashMap<>();

        PoliticsAndWarV2 allowedApi = db.getApi();
        PoliticsAndWarV2 api;

        if (nationOrAlliance.isAlliance()) {
            DBAlliance alliance = nationOrAlliance.asAlliance();
            GuildDB otherDb = alliance.getGuildDB();
            if (otherDb == null) return "No guild found for " + alliance;
            if (!Roles.ECON_LOW_GOV.has(author, otherDb.getGuild())) {
                return "You do not have " + Roles.ECON_LOW_GOV + " in " + otherDb;
            }
            totals = alliance.getStockpile();
        } else {
            DBNation nation = nationOrAlliance.asNation();
            if (nation != me) {
                boolean noPerm = false;
                if (!Roles.ECON.has(author, guild) && !Roles.MILCOM.has(author, guild) && !Roles.INTERNAL_AFFAIRS.has(author, guild) && !Roles.INTERNAL_AFFAIRS_STAFF.has(author, guild)) {
                    noPerm = true;
                } else if (nation.getAlliance_id() != db.getAlliance_id()) {
                    noPerm = true;
                }
                if (noPerm) return "You do not have permission to check that account's stockpile!";
            }
            totals = nation.getStockpile();
        }

        String out = PnwUtil.resourcesToFancyString(totals);
        channel.create().embed(nationOrAlliance.getName() + " stockpile", out).send();
        return null;
    }

    private void sendIO(StringBuilder out, String selfName, boolean isAlliance, Map<Integer, List<Transaction2>> transferMap, long timestamp, boolean inflow) {
        String URL_BASE = "" + Settings.INSTANCE.PNW_URL() + "/%s/id=%s";
        long days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - timestamp);
        for (Map.Entry<Integer, List<Transaction2>> entry : transferMap.entrySet()) {
            int id = entry.getKey();

            String typeOther = isAlliance ? "alliance" : "nation";
            String name = PnwUtil.getName(id, isAlliance);
            String url = String.format(URL_BASE, typeOther, id);

            List<Transaction2> transfers = entry.getValue();
            String title = inflow ? name + " > " + selfName : selfName + " > " + name;
//            String followCmd = Settings.commandPrefix(true) + "inflows " + url + " " + timestamp;

            StringBuilder message = new StringBuilder();

            Map<ResourceType, Double> totals = new HashMap<>();
            for (Transaction2 transfer : transfers) {
//                int sign = transfer.getSender() == id ? -1 : 1;
                int sign = 1;

                double[] rss = transfer.resources.clone();
//                rss[0] = 0;
                totals = PnwUtil.add(totals, PnwUtil.resourcesToMap(rss));
//                totals.put(type, sign * transfer.getAmount() + totals.getOrDefault(type, 0d));
            }

            message.append(PnwUtil.resourcesToString(totals));

//            String infoCmd = Settings.commandPrefix(true) + "pw-who " + url;
//            Message msg = PnwUtil.createEmbedCommand(channel, title, message.toString(), EMOJI_FOLLOW, followCmd, EMOJI_QUESTION, infoCmd);
            out.append(title + ": " + message).append("\n");
        }
    }

    @Command(desc = "List the inflows of a nation or alliance over a period of time")
    public String inflows(@Me IMessageIO channel, Set<NationOrAlliance> nationOrAlliances, @Timestamp long cutoffMs, @Switch("i") boolean hideInflows, @Switch("o") boolean hideOutflows) {
        List<Transaction2> allTransfers = new ArrayList<>();

        String selfName = StringMan.join(nationOrAlliances.stream().map(f -> f.getName()).collect(Collectors.toList()), ",");
        Set<Integer> self = new HashSet<>();
        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNations();
        Function<Integer, String> nationNameFunc = i -> {
            DBNation nation = nations.get(i);
            return nation == null ? Integer.toString(i) : nation.getNation();
        };

        if (nationOrAlliances.size() > 15) return "Too many nations or alliances: " + nationOrAlliances.size() + " (try using explicit alliance notation e.g. `AA:1234`)";

        for (NationOrAlliance nationOrAlliance : nationOrAlliances) {
            self.add(nationOrAlliance.getId());
            if (nationOrAlliance.isAlliance()) {
                allTransfers.addAll(Locutus.imp().getBankDB().getAllianceTransfers(nationOrAlliance.getAlliance_id(), cutoffMs));
            } else {
                DBNation nation = nationOrAlliance.asNation();
                allTransfers.addAll(Locutus.imp().getBankDB().getNationTransfers(nation.getNation_id(), cutoffMs));

                List<DBTrade> trades = Locutus.imp().getTradeManager().getTradeDb().getTrades(nation.getNation_id(), cutoffMs);
                for (DBTrade offer : trades) {
                    int per = offer.getPpu();
                    ResourceType type = offer.getResource();
                    if (per > 1 && (per < 10000 || (type != ResourceType.FOOD && per < 100000))) {
                        continue;
                    }
                    long amount = offer.getQuantity();
                    if (per <= 1) {
                        amount = offer.getTotal();
                        type = ResourceType.MONEY;
                    }
                    Transaction2 transfer = new Transaction2(offer);
                    allTransfers.add(transfer);
                }
            }
        }

        Map<Integer, List<Transaction2>> aaInflow = new HashMap<>();
        Map<Integer, List<Transaction2>> nationInflow = new HashMap<>();

        Map<Integer, List<Transaction2>> aaOutflow = new HashMap<>();
        Map<Integer, List<Transaction2>> nationOutflow = new HashMap<>();

        for (Transaction2 transfer : allTransfers) {
            if (transfer.note != null && transfer.note.contains("'s nation and captured")) continue;
            int sender = (int) transfer.getSender();
            int receiver = (int) transfer.getReceiver();

            Map<Integer, List<Transaction2>> map;
            int other;
            if (!self.contains(receiver)) {
                other = receiver;
                map = transfer.receiver_type == 2 ? aaOutflow : nationOutflow;
            } else if (!self.contains(sender)) {
                other = sender;
                map = transfer.sender_type == 2 ? aaInflow : nationInflow;
            } else {
                // Internal transfer
                continue;
            }

            List<Transaction2> list = map.computeIfAbsent(other, k -> new ArrayList<>());
            list.add(transfer);
        }

        StringBuilder out = new StringBuilder();

        if ((!aaInflow.isEmpty() || !nationInflow.isEmpty()) && !hideInflows) {
            out.append("Net inflows:\n");
            sendIO(out, selfName, true, aaInflow, cutoffMs, true);
            sendIO(out, selfName, false, nationInflow, cutoffMs, true);
        }
        if ((!aaOutflow.isEmpty() || !nationOutflow.isEmpty()) && !hideOutflows) {
            out.append("Net outflows:\n");
            sendIO(out, selfName, true, aaOutflow, cutoffMs, false);
            sendIO(out, selfName, false, nationOutflow, cutoffMs, false);
        }

        if (out.isEmpty()) {
            return "No results.";
        } else {
            return out.toString();
        }
    }

    @Command(desc="Set your api and bot key\n" +
            "See: <https://forms.gle/KbszjAfPVVz3DX9A7> and DM <@258298021266063360> to get a bot key")
    public String addApiKey(@Me JSONObject command, String apiKey, @Default String verifiedBotKey) {
        PoliticsAndWarV3 api = new PoliticsAndWarV3(ApiKeyPool.builder().addKeyUnsafe(apiKey, verifiedBotKey).build());
        ApiKeyDetails stats = api.getApiKeyStats();

        int nationId = stats.getNation().getId();
        Locutus.imp().getDiscordDB().addApiKey(nationId, apiKey);

        if (verifiedBotKey != null && !verifiedBotKey.isEmpty()) {
            try {
                api.testBotKey();
            } catch (IllegalArgumentException e) {
                if (e.getMessage().contains("The bot key you provided is not valid.")) {
                    return e.getMessage() + "\n - Please fill out <https://forms.gle/KbszjAfPVVz3DX9A7> and DM <@258298021266063360> to receive a working bot key";
                }
                if (e.getMessage().contains("The API key you provided does not allow whitelisted access.")) {
                    return e.getMessage() + "\n - Please go to <https://politicsandwar.com/account/> and at the bottem enable `Whitelisted Access`";
                }
                if (!e.getMessage().contains("You can't deposit no resources.")) {
                    return "Error: " + e.getMessage();
                }
            }
            Locutus.imp().getDiscordDB().addBotKey(nationId, verifiedBotKey);
        }

        return "Set api key for " + PnwUtil.getName(nationId, false);
    }

    @Command(desc="Login to allow locutus to run scripts through your account (Avoid using if possible)")
    @RankPermission(Rank.OFFICER)
    public String login(DiscordDB discordDB, @Me User author, @Me DBNation me, @Me Guild guild, String username, String password) {
        if (me == null || me.getPosition() < Rank.OFFICER.id) return "You are not an officer of an alliance";
        if (guild != null) {
            return "This command must be used via private message with Locutus. DO NOT USE THIS COMMAND HERE";
        }
        GuildDB db = Locutus.imp().getGuildDBByAA(me.getAlliance_id());
        if (db == null) return "Your alliance " + me.getAlliance_id() + " is not registered with Locutus";
        Auth existingAuth = db.getAuth();;

        if (!Roles.MEMBER.has(author, Locutus.imp().getServer())) {
            OffshoreInstance offshore = db.getOffshore();
            if (offshore == null) return "You have no offshore";
            if (!Roles.MEMBER.has(author, Locutus.imp().getServer()) && existingAuth != null && existingAuth.isValid() && existingAuth.getNation().getPosition() >= Rank.OFFICER.id && existingAuth.getNationId() != me.getNation_id())
                return "An officer is already connected: `" + PnwUtil.getName(existingAuth.getNationId(), false);
        }

        Auth auth = new Auth(me.getNation_id(), username, password);
        ApiKeyPool.ApiKey key = auth.fetchApiKey();

        discordDB.addApiKey(me.getNation_id(), key.getKey());
        discordDB.addUserPass2(me.getNation_id(), username, password);
        if (existingAuth != null) existingAuth.setValid(false);
        Auth myAuth = me.getAuth(null);
        if (myAuth != null) myAuth.setValid(false);

        return "Login successful.";
    }

    @Command(desc = "Remove your login details from locutus")
    public String logout(@Me DBNation me, @Me User author) {
        if (Locutus.imp().getDiscordDB().getUserPass2(author.getIdLong()) != null || (me != null && Locutus.imp().getDiscordDB().getUserPass2(me.getNation_id()) != null)) {
            Locutus.imp().getDiscordDB().logout(author.getIdLong());
            if (me != null) {
                Locutus.imp().getDiscordDB().logout(me.getNation_id());
                Auth cached = me.auth;
                if (cached != null) {
                    cached.setValid(false);
                }
                me.auth = null;
            }
            return "Logged out";
        }
        return "You are not logged in";
    }

    @Command
    @IsAlliance
    public String listAllianceMembers(@Me IMessageIO channel, @Me GuildDB db, int page) {
        Set<DBNation> nations = Locutus.imp().getNationDB().getNations(Collections.singleton(db.getAlliance_id()));

        int perPage = 5;

        new RankBuilder<>(nations).adapt(new com.google.common.base.Function<DBNation, String>() {
            @Override
            public String apply(DBNation n) {
                PNWUser user = Locutus.imp().getDiscordDB().getUserFromNationId(n.getNation_id());
                String result = "**" + n.getNation() + "**:" +
                        "";

                String active;
                if (n.getActive_m() < TimeUnit.DAYS.toMinutes(1)) {
                    active = "daily";
                } else if (n.getActive_m() < TimeUnit.DAYS.toMinutes(7)) {
                    active = "weekly";
                } else {
                    active = "inactive";
                }
                String url = n.getNationUrl();
                String general = n.toMarkdown(false, true, false);
                String infra = n.toMarkdown(false, false, true);

                StringBuilder response = new StringBuilder();
                response.append(n.getNation()).append(" | ").append(n.getAllianceName()).append(" | ").append(active);
                if (user != null) {
                    response.append('\n').append(user.getDiscordName()).append(" | ").append("`<@!").append(user.getDiscordId()).append(">`");
                }
                response.append('\n').append(url);
                response.append('\n').append(general);
                response.append(infra);

                return response.toString();
            }
        }).page(page - 1, perPage).build(channel, null, getClass().getSimpleName());
        return null;
    }

    public enum ClearRolesEnum {
        UNUSED,
        ALLIANCE,
        UNREGISTERED
    }

    @Command
    @RolePermission(Roles.ADMIN)
    public String clearAllianceRoles(@Me GuildDB db, @Me Guild guild, ClearRolesEnum type) {
        switch (type) {
            case UNUSED: {
                Map<Integer, Role> aaRoles = DiscordUtil.getAARoles(guild.getRoles());
                for (Map.Entry<Integer, Role> entry : aaRoles.entrySet()) {
                    if (guild.getMembersWithRoles(entry.getValue()).isEmpty()) {
                        entry.getValue().delete().complete();
                    }
                }
                return "Cleared unused AA roles!";
            }
            case ALLIANCE: {
                int aaId = db.getOrThrow(GuildDB.Key.ALLIANCE_ID);

                Role memberRole = Roles.MEMBER.toRole(guild);

                StringBuilder response = new StringBuilder();

                for (Member member : guild.getMembers()) {
                    DBNation nation = DiscordUtil.getNation(member.getIdLong());
                    List<Role> roles = member.getRoles();
                    if (roles.contains(memberRole)) {
                        if (nation == null || nation.getAlliance_id() != aaId) {
                            response.append("\nRemove member from " + member.getEffectiveName());
                            RateLimitUtil.queue(db.getGuild().removeRoleFromMember(member, memberRole));
                        }
                    }
                }
                response.append("\nDone!");
                return response.toString();
            }
            case UNREGISTERED: {
                Map<Integer, Role> aaRoles = DiscordUtil.getAARoles(guild.getRoles());
                for (Map.Entry<Integer, Role> entry : aaRoles.entrySet()) {
                    entry.getValue().delete().complete();
                }
                return "Cleared all AA roles!";
            }
            default:
                throw new IllegalArgumentException("Unknown type " + type);
        }
    }

    private Map<Long, String> previous = new HashMap<>();
    private long previousNicksGuild = 0;

    @Command
    @RolePermission(Roles.ADMIN)
    public synchronized String clearNicks(@Me Guild guild, @Default boolean undo) {
        if (previousNicksGuild != guild.getIdLong()) {
            previousNicksGuild = guild.getIdLong();
            previous.clear();
        }
        int failed = 0;
        String msg = null;
        for (Member member : guild.getMembers()) {
            if (member.getNickname() != null) {
                try {
                    String nick;
                    if (!undo) {
                        previous.put(member.getIdLong(), member.getNickname());
                        nick = null;
                    } else {
                        nick = previous.get(member.getIdLong());
//                        if (args.get(0).equalsIgnoreCase("*")) {
//                            nick = previous.get(member.getIdLong());
//                        } else {
//                            previous.put(member.getIdLong(), member.getNickname());
//                            nick = DiscordUtil.trimContent(event.getMessage().getContentRaw());
//                            nick = nick.substring(nick.indexOf(' ') + 1);
//                        }
                    }
                    member.modifyNickname(nick).complete();
                } catch (Throwable e) {
                    msg = e.getMessage();
                    failed++;
                }
            }
        }
        if (failed != 0) {
            return "Failed to clear " + failed + " nicknames for reason: " + msg;
        }
        return "Cleared all nicknames (that I have permission to clear)!";
    }

    @Command(desc = "Add or subtract from a nation, alliance or guild deposits\n" +
            "note: Mutated alliance deposits are only valid if your server is a bank/offshore\n" +
            "Use `#expire=30d` to have the amount expire after X days")
    @RolePermission(Roles.ECON)
    public String addBalance(@Me GuildDB db, @Me JSONObject command, @Me IMessageIO channel, @Me Guild guild, @Me User author, @Me DBNation me,
                             Set<NationOrAllianceOrGuild> accounts, Map<ResourceType, Double> amount, String note, @Switch("f") boolean force) throws Exception {

        AddBalanceBuilder builder = db.addBalanceBuilder().add(accounts, amount, note);
        if (!force) {
            builder.buildWithConfirmation(channel, command);
            return null;
        }
        boolean hasEcon = Roles.ECON.has(author, guild);
        return builder.buildAndSend(me, hasEcon);
    }

    @Command(desc = "Modify a nation, alliance or guild's deposits")
    @RolePermission(Roles.ECON)
    public String addBalanceSheet(@Me GuildDB db, @Me JSONObject command, @Me IMessageIO channel, @Me Guild guild, @Me User author, @Me DBNation me,
                                  SpreadSheet sheet, String note, @Switch("f") boolean force, @Switch("n") boolean negative) throws Exception {
        List<String> errors = new ArrayList<>();
        AddBalanceBuilder builder = db.addBalanceBuilder().addSheet(sheet, negative, errors::add, !force, note);
        if (!force) {
            builder.buildWithConfirmation(channel, command);
            return null;
        }
        boolean hasEcon = Roles.ECON.has(author, guild);
        return builder.buildAndSend(me, hasEcon);
    }

    @Command
    public String revenue(@Me GuildDB db, @Me Guild guild, @Me IMessageIO channel, @Me User user, @Me DBNation me,
                                NationList nations, @Switch("t") boolean includeUntaxable, @Switch("b") boolean excludeNationBonus) throws Exception {
        ArrayList<DBNation> filtered = new ArrayList<>(nations.getNations());
        if (!includeUntaxable) {
            filtered.removeIf(f -> f.getAlliance_id() == 0 || f.getVm_turns() != 0);
            filtered.removeIf(f -> f.isGray() || f.isBeige() || f.getPosition() <= 1);
        }
        double[] cityProfit = new double[ResourceType.values.length];
        double[] milUp = new double[ResourceType.values.length];
        int tradeBonusTotal = 0;
        for (DBNation nation : filtered) {
            ResourceType.add(cityProfit, nation.getRevenue(12, true, false, false, !excludeNationBonus, false, false));
            ResourceType.add(milUp, nation.getRevenue(12, false, true, false, false, false, false));
            tradeBonusTotal += nation.getColor().getTurnBonus() * 12;
        }
        double[] total = ResourceType.builder().add(cityProfit).add(milUp).addMoney(tradeBonusTotal).build();

        IMessageBuilder response = channel.create();
        double equilibriumTaxrate = 100 * ResourceType.getEquilibrium(total);

        response.append("Daily city revenue:")
                .append("```").append(PnwUtil.resourcesToString(cityProfit)).append("```");

        response.append(String.format("Converted total: $" + MathMan.format(PnwUtil.convertedTotal(cityProfit))));

        response.append("\nMilitary upkeep:")
                .append("```").append(PnwUtil.resourcesToString(milUp)).append("```");

        response.append("\nTrade bonus: ```" + MathMan.format(tradeBonusTotal) + "```");

        response.append("\nCombined Total:")
                .append("```").append(PnwUtil.resourcesToString(total)).append("```")
                .append("Converted total: $" + MathMan.format(PnwUtil.convertedTotal(total)));

        if (equilibriumTaxrate >= 0) {
            response.append("\nEquilibrium taxrate: `" + MathMan.format(equilibriumTaxrate) + "%`");
        } else {
            response.append("\n`warn: Revenue is not sustainable`");
        }
        response.send();
        return null;
    }

//    double[] profitBuffer, int turns, long date, DBNation nation, Collection<JavaCity> cities, boolean militaryUpkeep, boolean tradeBonus, boolean bonus, boolean checkRpc, boolean noFood, double rads, boolean atWar) {

    @Command
    public String cityRevenue(@Me Guild guild, @Me IMessageIO channel, @Me User user,
                              CityBuild city, @Default("%user%") DBNation nation, @Switch("b") boolean excludeNationBonus) throws Exception {
        if (nation == null) return "Please use " + CM.register.cmd.toSlashMention();
        JavaCity jCity = new JavaCity(city);

        double[] revenue = PnwUtil.getRevenue(null, 12, nation, Collections.singleton(jCity), false, false, !excludeNationBonus, false, false);

        JavaCity.Metrics metrics = jCity.getMetrics(nation::hasProject);
        IMessageBuilder msg = channel.create()
                .append("Daily city revenue ```" + city.toString() + "```")
                .append("```").append(PnwUtil.resourcesToString(revenue)).append("```")
                .append("Converted total: $" + MathMan.format(PnwUtil.convertedTotal(revenue)));
        if (!metrics.powered) {
            msg.append("\n**UNPOWERED**");
        }
        msg.append("\nAge: " + jCity.getAge());
        if (jCity.getInfra() != city.getInfraNeeded()) {
            msg.append("\nInfra (damaged): " + jCity.getInfra());
        }
        msg.append("\nLand: " + jCity.getLand());

        msg.append("\nCommerce: " + metrics.population);

        if (metrics.commerce > 0) msg.append("\nCommerce: " + MathMan.format(metrics.commerce));
        if (metrics.crime > 0) msg.append("\ncrime: " + MathMan.format(metrics.crime));
        if (metrics.disease > 0) msg.append("\ndisease: " + MathMan.format(metrics.disease));
        if (metrics.pollution > 0) msg.append("\npollution: " + MathMan.format(metrics.pollution));

        long nukeCutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(11);
        if (jCity.getNukeDate() > nukeCutoff) {
            msg.append("\nNuked: " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - jCity.getNukeDate()) + " ago");
        }

        msg.send();
        return null;
    }

    @Command
    public String unitHistory(@Me IMessageIO channel, @Me Guild guild, @Me User author, @Me DBNation me,
                              DBNation nation, MilitaryUnit unit, @Switch("p") Integer page) throws Exception {
        List<Map.Entry<Long, Integer>> history = nation.getUnitHistory(unit);

        long day = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);

        if (unit == MilitaryUnit.NUKE || unit == MilitaryUnit.MISSILE) {
            List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacks(nation.getNation_id(), day);
            AttackType attType = unit == MilitaryUnit.NUKE ? AttackType.NUKE : AttackType.MISSILE;
            attacks.removeIf(f -> f.attack_type != attType);

            outer:
            for (DBAttack attack : attacks) {
                AbstractMap.SimpleEntry<Long, Integer> toAdd = new AbstractMap.SimpleEntry<>(attack.epoch, nation.getUnits(unit));
                int i = 0;
                for (; i < history.size(); i++) {
                    Map.Entry<Long, Integer> entry = history.get(i);
                    long diff = Math.abs(entry.getKey() - attack.epoch);
                    if (diff < 5 * 60 * 1000) continue outer;

                    toAdd.setValue(entry.getValue());
                    if (entry.getKey() < toAdd.getKey()) {
                        history.add(i, toAdd);
                        continue outer;
                    }
                }
                history.add(i, toAdd);
            }
        }


        boolean purchasedToday = false;

        List<String> results = new ArrayList<>();
        Map.Entry<Long, Integer> previous = null;
        for (Map.Entry<Long, Integer> entry : history) {
            if (previous != null) {
                long timestamp = previous.getKey();
                String dateStr = TimeUtil.format(TimeUtil.MMDDYYYY_HH_MM_A, new Date(timestamp));

                int from = entry.getValue();
                int to = previous.getValue();

                results.add(dateStr + ": " + from + " -> " + to);

                if (to >= from && entry.getKey() >= day) purchasedToday = true;
            } else if (entry.getKey() >= day) purchasedToday = true;
            previous = new AbstractMap.SimpleEntry<>(entry);
        }
        if (previous != null) {
            long timestamp = previous.getKey();
            String dateStr = TimeUtil.format(TimeUtil.MMDDYYYY_HH_MM_A, new Date(timestamp));

            int to = previous.getValue();
            String from = "?";

            if ((unit == MilitaryUnit.MISSILE || unit == MilitaryUnit.NUKE) && to > 0) {
                from = "" + (to - 1);
            }

            results.add(dateStr + ": " + from + " -> " + to);
        }

        if (results.isEmpty()) return "No unit history";

        StringBuilder footer = new StringBuilder();

        if (unit == MilitaryUnit.MISSILE || unit == MilitaryUnit.NUKE) {
            if (purchasedToday) {
                footer.append("\n**note: " + unit.name().toLowerCase() + " purchased in the past 24h**");
            }
        }

        CM.unit.history cmd = CM.unit.history.cmd.create(nation.getNation_id() + "", unit.name(), null);

        String title = "`" + nation.getNation() + "` " + unit.name() + " history";
        int perPage =15;
        int pages = (results.size() + perPage - 1) / perPage;
        if (page == null) page = 0;
        title += " (" + (page + 1) + "/" + pages +")";

        channel.create().paginate(title, cmd, page, perPage, results, footer.toString(), false).send();
        return null;
    }

    @Command
    @RolePermission(Roles.MEMBER)
    public String findProducer(@Me IMessageIO channel, @Me JSONObject command, @Me Guild guild, @Me User author, @Me DBNation me,
                               List<ResourceType> resources, @Default NationList nationList,
                               @Switch("m") boolean ignoreMilitaryUpkeep,
                               @Switch("t") boolean ignoreTradeBonus,
                               @Switch("n") boolean ignoreNationBonus,
                               @Switch("a") boolean listByNation,
                               @Switch("s") boolean listAverage,
                               @Switch("u") boolean uploadFile) throws Exception {
        ArrayList<DBNation> nations = new ArrayList<>(nationList.getNations());
        nations.removeIf(n -> !n.isTaxable());

        Map<DBNation, Number> profitByNation = new HashMap<>();

        Set<Integer> nationIds = nations.stream().map(DBNation::getNation_id).collect(Collectors.toSet());

        Map<Integer, Map<Integer, DBCity>> allCities = Locutus.imp().getNationDB().getCitiesV3(nationIds);
        double[] profitBuffer = ResourceType.getBuffer();
        for (DBNation nation : nations) {
            Map<Integer, DBCity> v3Cities = allCities.get(nation.getNation_id());
            if (v3Cities == null || v3Cities.isEmpty()) continue;

            Map<Integer, JavaCity> cities = Locutus.imp().getNationDB().toJavaCity(v3Cities);

            Arrays.fill(profitBuffer, 0);
            double[] profit = nation.getRevenue();
            double value;
            if (resources.size() == 1) {
                value = profit[resources.get(0).ordinal()];
            } else {
                value = 0;
                for (ResourceType type : resources) {
                    value += PnwUtil.convertedTotal(type, profit[type.ordinal()]);
                }
            }
            if (value > 0) {
                profitByNation.put(nation, value);
            }
        }

        SummedMapRankBuilder<Integer, Number> byNation = new SummedMapRankBuilder<>(profitByNation).adaptKeys((n, v) -> n.getNation_id());
        RankBuilder<String> ranks;
        if (!listByNation) {
            NumericGroupRankBuilder<Integer, Number> byAAMap = byNation.group((entry, builder) -> {
                DBNation nation = Locutus.imp().getNationDB().getNation(entry.getKey());
                if (nation != null) {
                    builder.put(nation.getAlliance_id(), entry.getValue());
                }
            });
            SummedMapRankBuilder<Integer, Number> byAA = listAverage ? byAAMap.average() : byAAMap.sum();

            // Sort descending
            ranks = byAA.sort()
                    // Change key to alliance name
                    .nameKeys(id -> PnwUtil.getName(id, true));
        } else {
            ranks = byNation.sort()
                    // Change key to alliance name
                    .nameKeys(allianceId -> PnwUtil.getName(allianceId, false));
        }

        String rssNames = resources.size() >= ResourceType.values.length - 1 ? "market" : StringMan.join(resources, ",");
        String title = "Daily " + rssNames + " production";
        if (!listByNation && listAverage) title += " per member";
        if (resources.size() > 1) title += " (market value)";
        ranks.build(channel, command, title, uploadFile);
        return null;
    }

    @Command
    public String rebuy(@Me IMessageIO channel, @Me Guild guild, @Me User author, @Me DBNation me,
                        DBNation nation) throws Exception {
        Map<Integer, Long> dcProb = nation.findDayChange();
        if (dcProb.isEmpty() || dcProb.size() == 12) return "Unknown day change. Try " + CM.unit.history.cmd.toSlashMention() + "";

        if (dcProb.size() == 1) {
            Map.Entry<Integer, Long> entry = dcProb.entrySet().iterator().next();
            Integer offset = (entry.getKey() * 2 + 2) % 24;
            if (offset > 12) offset -= 24;
            return "Day change at UTC" + (offset >= 0 ? "+" : "") + offset + " (turn " + entry.getKey() + ")";
        }

        String title = "Possible DC times:";

        StringBuilder body = new StringBuilder("*date calculated | daychange time*\n\n");
        for (Map.Entry<Integer, Long> entry : dcProb.entrySet()) {
            Integer offset = (entry.getKey() * 2 + 2) % 24;
            if (offset > 12) offset -= 24;
            String dcStr = "UTC" + (offset >= 0 ? "+" : "") + offset + " (turn " + entry.getKey() + ")";
            Long turn = entry.getValue();
            long timestamp = TimeUtil.getTimeFromTurn(turn);
            String dateStr = TimeUtil.format(TimeUtil.MMDDYYYY_HH_MM_A, new Date(timestamp));
            body.append(dateStr + " | " + dcStr + "\n");
        }
        channel.create().embed(title, body.toString()).send();
        return null;
    }

    @Command
    public String leftAA(@Me IMessageIO io, @Me Guild guild, @Me User author, @Me DBNation me,
                         NationOrAlliance nationOrAlliance, @Default @Timestamp Long time, @Default NationList filter,
                         @Switch("a") boolean ignoreInactives,
                         @Switch("v") boolean ignoreVM,
                         @Switch("m") boolean ignoreMembers,
                         @Switch("i") boolean listIds) throws Exception {
        StringBuilder response = new StringBuilder();
        Map<Integer, Map.Entry<Long, Rank>> removes;
        List<Map.Entry<Map.Entry<DBNation, DBAlliance>, Map.Entry<Long, Rank>>> toPrint = new ArrayList<>();

        boolean showCurrentAA = false;
        if (nationOrAlliance.isNation()) {
            DBNation nation = nationOrAlliance.asNation();
            removes = nation.getAllianceHistory();
            for (Map.Entry<Integer, Map.Entry<Long, Rank>> entry : removes.entrySet()) {
                DBAlliance aa = DBAlliance.getOrCreate(entry.getKey());
                DBNation tmp = nation;
                if (tmp == null) {
                    tmp = new DBNation();
                    tmp.setNation_id(nation.getNation_id());
                    tmp.setAlliance_id(aa.getAlliance_id());
                    tmp.setNation(nation.getNation_id() + "");
                }
                AbstractMap.SimpleEntry<DBNation, DBAlliance> key = new AbstractMap.SimpleEntry<>(tmp, aa);
                Map.Entry<Long, Rank> value = entry.getValue();
                toPrint.add(new AbstractMap.SimpleEntry<>(key, value));
            }

        } else {
            showCurrentAA = true;
            DBAlliance alliance = nationOrAlliance.asAlliance();
            removes = alliance.getRemoves();


            if (removes.isEmpty()) return "No history found";

            for (Map.Entry<Integer, Map.Entry<Long, Rank>> entry : removes.entrySet()) {
                if (entry.getValue().getKey() < time) continue;

                DBNation nation = Locutus.imp().getNationDB().getNation(entry.getKey());
                if (nation != null && (filter == null || filter.getNations().contains(nation))) {

                    if (ignoreInactives && nation.getActive_m() > 10000) continue;
                    if (ignoreVM && nation.getVm_turns() != 0) continue;
                    if (ignoreMembers && nation.getPosition() > 1) continue;

                    AbstractMap.SimpleEntry<DBNation, DBAlliance> key = new AbstractMap.SimpleEntry<>(nation, alliance);
                    toPrint.add(new AbstractMap.SimpleEntry<>(key, entry.getValue()));
                }
            }
        }

        Set<Integer> ids = new LinkedHashSet<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<Map.Entry<DBNation, DBAlliance>, Map.Entry<Long, Rank>> entry : toPrint) {
            long diff = now - entry.getValue().getKey();
            Rank rank = entry.getValue().getValue();
            String timeStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff);

            Map.Entry<DBNation, DBAlliance> nationAA = entry.getKey();
            DBNation nation = nationAA.getKey();
            ids.add(nation.getNation_id());

            response.append(timeStr + " ago: " + nationAA.getKey().getNation() + " left " + nationAA.getValue().getName() + " | " + rank.name());
            if (showCurrentAA && nation.getAlliance_id() != 0) {
                response.append(" and joined " + nation.getAllianceName());
            }
            response.append("\n");
        }

        if (response.length() == 0) return "No history found in the specified timeframe";
        IMessageBuilder msg = io.create();
        if (listIds) {
            msg.file("ids.txt",  StringMan.join(ids, ","));
        }
        msg.append(response.toString()).send();
        return null;
    }

    @Command
    @RolePermission(Roles.MEMBER)
    public String copyPasta(@Me IMessageIO io, @Me GuildDB db, @Me Guild guild, @Me Member member, @Me User author, @Me DBNation me,
                            @Default String key, @Default @TextArea String message, @Default Set<Role> requiredRolesAny, NationPlaceholders placeholders, ValueStore store) throws Exception {
        if (key == null) {

            Map<String, String> copyPastas = db.getCopyPastas(member);
            Set<String> options = copyPastas.keySet().stream().map(f -> f.split("\\.")[0]).collect(Collectors.toSet());

            if (options.size() <= 25) {
                // buttons
                IMessageBuilder msg = io.create().append("Options:");
                for (String option : options) {
                    msg.commandButton(CommandBehavior.DELETE_MESSAGE, CM.copyPasta.cmd.create(option, null), option);
                }
                msg.send();
                return null;
            }

            // link modals
            return "Options:\n - " + StringMan.join(options, "\n - ");

            // return options
        }
        if (requiredRolesAny != null && !requiredRolesAny.isEmpty()) {
            if (message == null) {
                throw new IllegalArgumentException("requiredRoles can only be used with a message");
            }
            key = requiredRolesAny.stream().map(Role::getId).collect(Collectors.joining(".")) + key;
        }

        if (message == null) {
            Set<String> noRoles = db.getMissingCopypastaPerms(key, member);
            if (!noRoles.isEmpty()) {
                throw new IllegalArgumentException("You do not have the required roles to use this command: `" + StringMan.join(noRoles, ",") + "`");
            }
        } else if (!Roles.INTERNAL_AFFAIRS.has(author, guild)) {
            return "Missing role: " + Roles.INTERNAL_AFFAIRS;
        }

        String value = db.getInfo("copypasta." + key);

        Set<String> missingRoles = null;
        if (value == null) {
            Map<String, String> map = db.getCopyPastas(member);
            for (Map.Entry<String, String> entry : map.entrySet()) {
                String otherKey = entry.getKey();
                String[] split = otherKey.split("\\.");
                if (!split[split.length - 1].equalsIgnoreCase(key)) continue;

                Set<String> localMissing = db.getMissingCopypastaPerms(otherKey, guild.getMember(author));

                if (!localMissing.isEmpty()) {
                    missingRoles = localMissing;
                    continue;
                }

                value = entry.getValue();
                missingRoles = null;
            }
        } else {
            missingRoles = db.getMissingCopypastaPerms(key, guild.getMember(author));
        }
        if (missingRoles != null && !missingRoles.isEmpty()) {
            throw new IllegalArgumentException("You do not have the required roles to use this command: `" + StringMan.join(missingRoles, ",") + "`");
        }
        if (value == null) return "No message set for `" + key + "`. Plase use " + CM.copyPasta.cmd.toSlashMention() + "";

        value = placeholders.format(store, value);

        return value;
    }

    @Command
    @RolePermission(Roles.MEMBER)
    public String checkCities(@Me GuildDB db, @Me IMessageIO channel, @Me Guild guild, @Me User author, @Me DBNation me,
                              NationList nationList, @Default Set<IACheckup.AuditType> audits, @Switch("u") boolean pingUser, @Switch("m") boolean mailResults, @Switch("c") boolean postInInterviewChannels, @Switch("s") boolean skipUpdate) throws Exception {
        Collection<DBNation> nations = nationList.getNations();
        Set<Integer> aaIds = nationList.getAllianceIds();
        if (aaIds.size() > 1) {
            return "Nations are not in the same alliance";
        }

        if (nations.size() > 1) {
            IACategory category = db.getIACategory();
            if (category != null) {
                category.load();
                category.purgeUnusedChannels(channel);
                category.alertInvalidChannels(channel);
            }
        }

        me.setMeta(NationMeta.INTERVIEW_CHECKUP, (byte) 1);

        IACheckup checkup = new IACheckup(aaIds.iterator().next());

        ApiKeyPool keys = mailResults ? db.getMailKey() : null;
        if (mailResults && keys == null) throw new IllegalArgumentException("No API_KEY set, please use " + CM.credentials.addApiKey.cmd.toSlashMention() + "");

        CompletableFuture<IMessageBuilder> msg = channel.send("Please wait...");

        Map<DBNation, Map<IACheckup.AuditType, Map.Entry<Object, String>>> auditResults = new HashMap<>();

        for (DBNation nation : nations) {
            StringBuilder output = new StringBuilder();
            int failed = 0;

            Map<IACheckup.AuditType, Map.Entry<Object, String>> auditResult = checkup.checkup(nation, nations.size() == 1, skipUpdate);
            auditResults.put(nation, auditResult);

            if (auditResult != null) {
                auditResult = IACheckup.simplify(auditResult);
            }

            if (!auditResult.isEmpty()) {
                for (Map.Entry<IACheckup.AuditType, Map.Entry<Object, String>> entry : auditResult.entrySet()) {
                    IACheckup.AuditType type = entry.getKey();
                    Map.Entry<Object, String> info = entry.getValue();
                    if (info == null || info.getValue() == null) continue;
                    failed++;

                    output.append("**").append(type.toString()).append(":** ");
                    output.append(info.getValue()).append("\n\n");
                }
            }
            IMessageBuilder resultMsg = channel.create();
            if (failed > 0) {
                resultMsg.append("**").append(nation.getName()).append("** failed ").append(failed + "").append(" checks:");
                if (pingUser) {
                    User user = nation.getUser();
                    if (user != null) resultMsg.append(user.getAsMention());
                }
                resultMsg.append("\n");
                resultMsg.append(output.toString());
                if (mailResults) {
                    String title = nation.getAllianceName() + " automatic checkup";

                    String input = output.toString().replace("_", " ").replace(" * ", " STARPLACEHOLDER ");
                    String markdown = MarkupUtil.markdownToHTML(input);
                    markdown = MarkupUtil.transformURLIntoLinks(markdown);
                    markdown = MarkupUtil.htmlUrl(nation.getName(), nation.getNationUrl()) + "\n" + markdown;
                    markdown += ("\n\nPlease get in contact with us via discord for assistance");
                    markdown = markdown.replace("\n", "<br>").replace(" STARPLACEHOLDER ", " * ");

                    JsonObject response = nation.sendMail(keys, title, markdown);
                    String userStr = nation.getNation() + "/" + nation.getNation_id();
                    resultMsg.append("\n" + userStr + ": " + response);
                }
            } else {
                resultMsg.append("All checks passed for " + nation.getNation());
            }
            resultMsg.send();
        }

        if (postInInterviewChannels) {
            if (db.getGuild().getCategoriesByName("interview", true).isEmpty()) {
                return "No `interview` category";
            }

            IACategory category = db.getIACategory();
            if (category.isValid()) {
                category.update(auditResults);
            }
        }

        return null;
    }

    @Command(desc = "Generate an optimal build for a city")
    @RolePermission(Roles.ECON)
    @HasOffshore
    public String warchest(@Me TextChannel channel, @Me Guild guild, @Me User author, @Me DBNation me,
                           NationList nations, Map<ResourceType, Double> resources, String note) throws Exception {
        List<String> cmd = Arrays.asList(nations.getFilter(), PnwUtil.resourcesToString(resources), note);
        return new Warchest().onCommand(guild, channel, author, me, cmd);
    }

    @Command
    public String reroll(@Me TextChannel channel, @Me Guild guild, @Me User author,
                         DBNation nation) throws Exception {

        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNations();
        for (Map.Entry<Integer, DBNation> entry : nations.entrySet()) {
            int otherId = entry.getKey();
            DBNation otherNation = entry.getValue();

            if (otherId > nation.getId() && otherNation.getAgeDays() > nation.getAgeDays() && Math.abs(otherNation.getDate()  - nation.getDate()) > TimeUnit.DAYS.toMillis(3)) {
                return nation.getNation() + "/" + nation.getNation_id() + " is a reroll.";
            }
        }

//        Map<Long, BigInteger> uuids = Locutus.imp().getDiscordDB().getUuids(me.getNation_id());
        Set<String> multiNations = new HashSet<>();;
        Set<Integer> deletedMulti = new HashSet<>();
//        for (BigInteger uuid : uuids.values()) {
//            Set<Integer> multis = Locutus.imp().getDiscordDB().getMultis(uuid);
//            for (int nationId : multis) {
//                if (nationId >= me.getNation_id()) continue;
//                DBNation other = Locutus.imp().getNationDB().getNation(nationId);
//                if (other == null) {
//                    deletedMulti.add(nationId);
//                } else if (other.getActive_m() > 10000 || other.getVm_turns() != 0) {
//                    multiNations.add(other.getNation());
//                }
//            }
//        }

        if (!deletedMulti.isEmpty()) {
            return nation.getNation() + "/" + nation.getNation_id() + " is a possible reroll of the following nation ids: " + StringMan.getString(deletedMulti);
        }
        if (!multiNations.isEmpty()) {
            return nation.getNation() + "/" + nation.getNation_id() + " is a possible reroll of the following nations: " + StringMan.getString(multiNations);
        }

        return nation.getNation() + "/" + nation.getNation_id() + " is not a reroll.";
    }

    @Command
    @RolePermission(any = true, value = {Roles.ADMIN, Roles.INTERNAL_AFFAIRS, Roles.ECON, Roles.MILCOM, Roles.FOREIGN_AFFAIRS})
    public String keyStore(@Me TextChannel channel, @Me Guild guild, @Me User author, @Me DBNation me,
                           @Default GuildDB.Key key, @Default @TextArea String value) throws Exception {
        List<String> cmd = new ArrayList<>();
        if (key != null) cmd.add(key + "");
        if (value != null) cmd.add(value);
        return new KeyStore().onCommand(guild, channel, author, me, cmd);
    }

    @Command(desc = "Generate an optimal build for a city")
    @RolePermission(Roles.MEMBER)
    public String optimalBuild(@Me JSONObject command, @Me TextChannel channel, @Me Guild guild, @Me User author, @Me DBNation me,
                               CityBuild build, @Default Integer days,
                               @Switch("x") @Filter("[0-9]{4}") String buildMMR,
                               @Switch("a") Integer age,
                               @Switch("i") Integer infra,
                               @Switch("b") Integer baseReducedInfra,
                               @Switch("l") Integer land,
                               @Switch("d") Double diseaseCap,
                               @Switch("c") Double crimeCap,
                               @Switch("p") Double minPopulation,
                               @Switch("r") Double radiation,
                               @Switch("t")TaxRate taxRate,
                               @Switch("u") boolean useRawsForManu,
                               @Switch("w") boolean writePlaintext,
                               @Switch("n") Set<Project> nationalProjects,
                               @Switch("m") boolean moneyPositive,
                               @Switch("g") Continent geographicContinent
    ) throws Exception {
        List<String> cmd = new ArrayList<>();
        if (days != null) cmd.add(days + " ");
        cmd.add(build.toString());
        if (buildMMR != null) cmd.add("mmr=" + buildMMR);

        if (buildMMR != null) cmd.add("mmr=" + buildMMR);
        if (age != null) cmd.add("age=" + age);
        if (infra != null) cmd.add("infra=" + infra);
        if (baseReducedInfra != null) cmd.add("infralow=" + baseReducedInfra);
        if (land != null) cmd.add("land=" + land);
        if (diseaseCap != null) cmd.add("disease<" + diseaseCap);
        if (crimeCap != null) cmd.add("crime<" + crimeCap);
        if (minPopulation != null) cmd.add("population>" + minPopulation);
        if (radiation != null) cmd.add("radiation=" + radiation);
        if (taxRate != null) cmd.add("" + taxRate.toString());
        if (useRawsForManu) cmd.add("manu=" + useRawsForManu);
        if (writePlaintext) cmd.add("-p");
        if (nationalProjects != null) {
            for (Project project : nationalProjects) {
                cmd.add("" + project.name());
            }
        }

        if (moneyPositive) cmd.add("cash=" + moneyPositive);
        if (geographicContinent != null) cmd.add("continent=" + geographicContinent);
        return new OptimalBuild().onCommand(guild, channel, author, me, cmd + "");
    }
}
