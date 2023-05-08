package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.politicsandwar.graphql.model.ApiKeyDetails;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.info.optimal.OptimalBuild;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.AllowDeleted;
import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Filter;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.TextArea;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timediff;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasApi;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasOffshore;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RankPermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.rankings.builder.NumericGroupRankBuilder;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.AddBalanceBuilder;
import link.locutus.discord.db.entities.DBAlliancePosition;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.NationOrAllianceOrGuildOrTaxid;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.pnw.SimpleNationList;
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
import link.locutus.discord.util.offshore.test.IACategory;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.util.sheet.templates.TransferSheet;
import link.locutus.discord.util.task.ia.IACheckup;
import net.dv8tion.jda.api.entities.*;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UnsortedCommands {

    @Command(desc = "Generate a sheet of guild member nations that have free espionage spy operations\n" +
            "Useful for finding who can participate in a spy blitz")
    @IsAlliance
    @HasApi
    public void freeSpyOpsSheet(
            @Me GuildDB db,
            ValueStore store, NationPlaceholders placeholders, @Me IMessageIO channel,
            @Arg("Nations to list in the sheet\n" +
                    "Defaults to the guild alliance")
            @Default NationList nations,
            @Default @Arg("A space separated list of columns to add to the sheet\n" +
                    "Can include NationAttribute as placeholders in columns\n" +
                    "All NationAttribute placeholders must be surrounded by {} e.g. {nation}")
            List<String> addColumns,
            @Arg("Number of free espionage ops required") @Switch("r") Integer requireXFreeOps,
            @Arg("Number of spies required")
            @Switch("s") Integer requireSpies,
            @Switch("sheet") SpreadSheet sheet) throws GeneralSecurityException, IOException {

        List<DBNation> invalidNations = new ArrayList<>();
        AllianceList aaList = db.getAllianceList();
        Set<DBNation> aaNations = aaList.getNations(f -> f.getPositionEnum().id >= Rank.APPLICANT.id && f.getVm_turns() == 0 && nations.contains(f));
        if (aaNations.isEmpty()) {
            throw new IllegalArgumentException("No nations in alliances " + StringMan.getString(aaList.getIds()) + " matched `nations` (vacation mode or applicants are ignored)");
        }
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKeys.NATION_SHEET);
        }

        aaList = aaList.subList(aaNations);
        Map<DBNation, Integer> opsUsed = aaList.updateOffSpyOps();

        List<String> columns = new ArrayList<>(
                Arrays.asList(
                        "=HYPERLINK(\"politicsandwar.com/nation/id={nation_id}\", \"{nation}\")",
                        "=HYPERLINK(\"politicsandwar.com/alliance/id={alliance_id}\", \"{alliancename}\")",
                        "{score}",
                        "{cities}",
                        "{spies}",
                        "{free_spy_ops}"
                )
        );
        if (addColumns != null) columns.addAll(addColumns);
        List<String> header = new ArrayList<>(columns);
        for (int i = 0; i < header.size(); i++) {
            String arg = header.get(i);
            arg = arg.replace("{", "").replace("}", "").replace("=", "");
            header.set(i, arg);
        }

        sheet.setHeader(header);

        LocalValueStore locals = new LocalValueStore(store);
        for (Map.Entry<DBNation, Integer> entry : opsUsed.entrySet()) {
            DBNation nation = entry.getKey();
            if (!aaNations.contains(nation)) continue;
            int offSlots = nation.getOffSpySlots();
            int usedSlots = entry.getValue();
            int free = offSlots - usedSlots;
            if (requireXFreeOps != null && free < requireXFreeOps) continue;
            if (requireSpies != null && nation.getSpies() < requireSpies) continue;

            locals.addProvider(Key.of(DBNation.class, Me.class), nation);
            locals.addProvider(Key.of(User.class, Me.class), nation.getUser());
            for (int i = 0; i < columns.size(); i++) {
                String arg = columns.get(i);
                arg = arg.replace("{free_spy_ops}", String.valueOf(free));
                arg = arg.replace("{used_spy_ops}", String.valueOf(usedSlots));
                String formatted = placeholders.format(locals, arg);

                header.set(i, formatted);
            }

            sheet.addRow(new ArrayList<>(header));
        }

        sheet.clear("A:ZZ");
        sheet.set(0, 0);

        sheet.attach(channel.create()).send();
    }

    @Command(desc = "Get an alert on discord when a target logs in within the next 5 days\n" +
            "Useful if you want to know when they might defeat you in war or perform an attack")
    public String loginNotifier(@Me User user, @Me DBNation nation, DBNation target, @Switch("w") boolean doNotRequireWar) {
        // ensure nation is fighting target
        boolean isFighting = false;
        for (DBWar war : target.getActiveWars()) {
            if (war.attacker_id == nation.getId() || war.defender_id == nation.getId()) {
                isFighting = true;
                break;
            }
        }
        if (!isFighting && !doNotRequireWar) {
            return "You are not fighting " + target.getName() + "!";
        }
        synchronized (target) {
            Map<Long, Long> existingMap = target.getLoginNotifyMap();
            if (existingMap == null) existingMap = new LinkedHashMap<>();
            existingMap.put(user.getIdLong(), System.currentTimeMillis());
            target.setLoginNotifyMap(existingMap);
        }
        return "You will be notified when " + target.getName() + " logs in (within the next 5d).";
    }

    @Command(desc ="Generate a google sheet of tax revenue for a list of nations")
    @RolePermission(Roles.MEMBER)
    @IsAlliance
    public String taxRevenueSheet(@Me IMessageIO io, @Me Guild guild, @Me GuildDB db, @Me DBNation me, @Me User author, @Default Set<DBNation> nations, @Switch("s") SpreadSheet sheet, @Switch("f") boolean forceUpdate,
                                  @Arg("Include the potential revenue of untaxable nations\n" +
                                          "Assumes 100/100)")
                                  @Switch("u") boolean includeUntaxable) throws GeneralSecurityException, IOException {
        Set<TaxBracket> brackets = new HashSet<>();
        Set<Integer> aaIds = db.getAllianceIds(true);
        Set<Integer> alliancesUpdated = new HashSet<>();
        if (nations != null) {
            Map<Integer, Integer> taxIdToAA = new LinkedHashMap<>();
            for (DBNation nation : nations) {
                int taxId = nation.getTax_id();
                if (taxId > 0) {
                    taxIdToAA.put(taxId, nation.getAlliance_id());
                }
            }
            for (Map.Entry<Integer, Integer> entry : taxIdToAA.entrySet()) {
                int taxId = entry.getKey();
                int aaId = entry.getValue();
                DBAlliance alliance = DBAlliance.get(aaId);
                TaxBracket bracket = new TaxBracket(taxId, aaId, "", -1, -1, 0L);
                if (alliance != null) {
                    Map<Integer, TaxBracket> aaBrackets = alliance.getTaxBrackets(!forceUpdate || !alliancesUpdated.add(aaId));
                    bracket = aaBrackets.get(taxId);
                }
                brackets.add(bracket);
            }
        } else if (!aaIds.isEmpty()){
            for (int aaId : aaIds) {
                DBAlliance alliance = DBAlliance.get(aaId);
                if (alliance != null) {
                    brackets.addAll(alliance.getTaxBrackets(!forceUpdate || !alliancesUpdated.add(aaId)).values());
                }
            }
        } else {
            throw new IllegalArgumentException("No alliances are registered to this guild. Please provide a list of nations to check.");
        }
        if (brackets.isEmpty()) {
            throw new IllegalArgumentException("No tax brackets found.");
        }
        if (sheet == null) sheet = SpreadSheet.create(db, SheetKeys.TAX_BRACKET_SHEET);

        List<String> header = new ArrayList<>(Arrays.asList(
                "ID",
                "Name",
                "Money Rate",
                "Resource Rate",
                "Alliance",
                "Nations",
                "Total[TAX]",
                "Total[DEPOSITS]",
                "Value[TAX]",
                "Value[*]",
                "Revenue Value"
        ));
        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS) continue;
            header.add(type.name());
        }

        sheet.setHeader(header);

        boolean includeUnknownRate = false;
        Gson gson = new Gson();
        for (TaxBracket bracket : brackets) {
            header.clear();
            if (bracket.rssRate < 0 || bracket.moneyRate < 0) {
                includeUnknownRate = true;
            }
            header.add("" + bracket.getId());
            header.add(bracket.getName());
            header.add("" + bracket.moneyRate);
            header.add("" + bracket.rssRate);
            header.add("" + bracket.getAlliance_id());
            header.add("" + bracket.getNations().size());

            Map<DepositType, double[]> depositsByCat = db.getTaxBracketDeposits(bracket.getId(), 0L, false, false);
            double[] tax = depositsByCat.getOrDefault(DepositType.TAX, ResourceType.getBuffer());
            double[] deposits = depositsByCat.getOrDefault(DepositType.DEPOSIT, ResourceType.getBuffer());
            header.add(gson.toJson(PnwUtil.resourcesToMap(tax)));
            header.add(gson.toJson(PnwUtil.resourcesToMap(deposits)));
            header.add(String.format("%.2f", PnwUtil.convertedTotal(tax)));
            header.add(String.format("%.2f", PnwUtil.convertedTotal(ResourceType.add(depositsByCat.values()))));

            double[] revenue = ResourceType.getBuffer();
            Set<DBNation> taxable = bracket.getNations();
            if (!includeUntaxable) taxable.removeIf(f -> !f.isTaxable());
            for (DBNation nation : taxable) {
                double mRate = (bracket.moneyRate < 0 ? 100 : bracket.moneyRate) / 100d;
                double rRate = (bracket.rssRate < 0 ? 100 : bracket.rssRate) / 100d;
                double[] natRevenue = nation.getRevenue();
                revenue[0] += Math.max(0, natRevenue[0]) * mRate;
                for (int i = 1 ; i < natRevenue.length ; i++) {
                    revenue[i] += Math.max(0, natRevenue[i]) * rRate;
                }
            }
            header.add(String.format("%.2f", PnwUtil.convertedTotal(revenue)));
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                header.add("" + revenue[type.ordinal()]);
            }
            sheet.addRow(header);
        }

        List<String> messages = new ArrayList<>();
        if (!includeUntaxable) {
            messages.add("Set the `includeUntaxable` switch to include nations not currently paying taxes.");
        }
        if (!forceUpdate) {
            messages.add("Set the `forceUpdate` switch to force an update of all tax brackets.");
        }
        if (includeUnknownRate) {
            messages.add("You do not have permission to view the tax rates of some brackets. Revenue will be assumed 100/100");
        }
        messages.add("The TAX column includes tax records not set to go into a member's personal deposits, or offsets using the `#tax` note");

        sheet.clearAll();
        sheet.set(0, 0);

        IMessageBuilder msg = io.create();
        msg.append("Notes:\n - " + StringMan.join(messages, "\n - "));
        sheet.attach(msg).send();
        return null;
    }

    @Command(desc ="View the resources in a nation or alliance")
    @RolePermission(Roles.MEMBER)
    @IsAlliance
    public String stockpile(@Me IMessageIO channel, @Me Guild guild, @Me GuildDB db, @Me DBNation me, @Me User author, NationOrAlliance nationOrAlliance) throws IOException {
        Map<ResourceType, Double> totals = new HashMap<>();

        if (nationOrAlliance.isAlliance()) {
            DBAlliance alliance = nationOrAlliance.asAlliance();
            GuildDB otherDb = alliance.getGuildDB();
            if (otherDb == null) return "No guild found for " + alliance;
            if (!Roles.ECON_STAFF.has(author, otherDb.getGuild())) {
                return "You do not have " + Roles.ECON_STAFF + " in " + otherDb;
            }
            totals = alliance.getStockpile();
        } else {
            DBNation nation = nationOrAlliance.asNation();
            if (nation != me) {
                boolean noPerm = false;
                if (!Roles.ECON.has(author, guild) && !Roles.MILCOM.has(author, guild) && !Roles.INTERNAL_AFFAIRS.has(author, guild) && !Roles.INTERNAL_AFFAIRS_STAFF.has(author, guild)) {
                    noPerm = true;
                } else if (!db.isAllianceId(nation.getAlliance_id())) {
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

    @Command(desc = "List the public resource imports or exports of a nation or alliance to other nations or alliances over a period of time")
    public String inflows(@Me IMessageIO channel, Set<NationOrAlliance> nationOrAlliances,
                          @Arg("Date to start from")
                          @Timestamp long cutoffMs,
                          @Arg("Do not show inflows")
                          @Switch("i") boolean hideInflows,
                          @Arg("Do not show outflows")
                          @Switch("o") boolean hideOutflows) {
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

    @Command(desc="Set your api and bot key for the bot\n" +
            "Your API key can be found on the account page: <https://politicsandwar.com/account/>\n" +
            "See: <https://forms.gle/KbszjAfPVVz3DX9A7> and DM <@258298021266063360> to get a bot key")
    public String addApiKey(@Me JSONObject command, String apiKey, @Default String verifiedBotKey) {
        PoliticsAndWarV3 api = new PoliticsAndWarV3(ApiKeyPool.builder().addKeyUnsafe(apiKey, verifiedBotKey).build());
        ApiKeyDetails stats = api.getApiKeyStats();

        int nationId = stats.getNation().getId();
        Locutus.imp().getDiscordDB().addApiKey(nationId, apiKey);

        if (verifiedBotKey != null && !verifiedBotKey.isEmpty()) {
            api.testBotKey();
            Locutus.imp().getDiscordDB().addBotKey(nationId, verifiedBotKey);
        }

        return "Set api key for " + PnwUtil.getName(nationId, false);
    }

    @Command(desc="Login to allow the bot to run scripts through your account\n" +
            "(Avoid using this if possible)")
    @RankPermission(Rank.OFFICER)
    public static String login(@Me IMessageIO io, DiscordDB discordDB, @Me DBNation me,
                               @Arg("Your username (i.e. email) for Politics And War")
                               String username,
                               String password) {
        IMessageBuilder msg = io.getMessage();
        try {
            if (msg != null) io.delete(msg.getId());
        } catch (Throwable ignore) {};
        if (me == null || me.getPosition() < Rank.OFFICER.id) return "You are not an officer of an alliance";
        DBAlliance alliance = me.getAlliance();
        Auth existingAuth = alliance.getAuth();;
        if (existingAuth != null) {
            DBNation existingNation = existingAuth.getNation();
            if (existingNation.getPositionEnum().id >= Rank.HEIR.id) {
                return "You already have an heir logged in. Please ask them to logout first: " + existingNation.getNation();
            }
            DBAlliancePosition existingPosition = existingNation.getAlliancePosition();
            if (existingPosition != null && existingPosition.hasAllPermission(AlliancePermission.CHANGE_PERMISSIONS, AlliancePermission.REMOVE_MEMBERS, AlliancePermission.ACCEPT_APPLICANTS)) {
                return "You already have an officer logged in. Please ask them to logout first: " + existingNation.getNation();
            }
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

    @Command(desc = "Remove your login details from the bot")
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

    @Command(desc = "List all in-game alliance members")
    @IsAlliance
    public String listAllianceMembers(@Me IMessageIO channel, @Me JSONObject command, @Me GuildDB db, int page) {
        Set<DBNation> nations = db.getAllianceList().getNations();

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
        }).page(page - 1, perPage).build(channel, command, getClass().getSimpleName());
        return null;
    }

    public enum ClearRolesEnum {
        UNUSED,
        ALLIANCE,
        UNREGISTERED
    }

    @Command(desc = "Clear the bot managed roles on discord")
    @RolePermission(Roles.ADMIN)
    public String clearAllianceRoles(@Me GuildDB db, @Me Guild guild,
                                     @Arg("What role types do you want to remove")
                                     ClearRolesEnum type) throws ExecutionException, InterruptedException {
        List<Future<?>> tasks = new ArrayList<>();
        try {
            switch (type) {
                case UNUSED: {
                    Map<Integer, Role> aaRoles = DiscordUtil.getAARoles(guild.getRoles());
                    for (Map.Entry<Integer, Role> entry : aaRoles.entrySet()) {
                        if (guild.getMembersWithRoles(entry.getValue()).isEmpty()) {
                            tasks.add(RateLimitUtil.queue(entry.getValue().delete()));
                        }
                    }
                    return "Cleared unused Alliance roles!";
                }
                case ALLIANCE: {
                    Set<Integer> aaIds = db.getAllianceIds();

                    Role memberRole = Roles.MEMBER.toRole(guild);

                    StringBuilder response = new StringBuilder();
                    for (Member member : guild.getMembers()) {
                        DBNation nation = DiscordUtil.getNation(member.getIdLong());
                        List<Role> roles = member.getRoles();
                        if (roles.contains(memberRole)) {
                            if (nation == null || !aaIds.contains(nation.getAlliance_id())) {
                                response.append("\nRemove member from " + member.getEffectiveName());
                                tasks.add(RateLimitUtil.queue(db.getGuild().removeRoleFromMember(member, memberRole)));
                            }
                        }
                    }
                    response.append("\nDone!");
                    return response.toString();
                }
                case UNREGISTERED: {
                    Map<Integer, Role> aaRoles = DiscordUtil.getAARoles(guild.getRoles());
                    for (Map.Entry<Integer, Role> entry : aaRoles.entrySet()) {
                        tasks.add(RateLimitUtil.queue(entry.getValue().delete()));
                    }
                    return "Cleared all AA roles!";
                }
                default:
                    throw new IllegalArgumentException("Unknown type " + type);
            }
        } finally {
            for (Future<?> task : tasks) {
                task.get();
            }
        }
    }

    private Map<Long, String> previous = new HashMap<>();
    private long previousNicksGuild = 0;

    @Command(desc = "Clear all nicknames on discord")
    @RolePermission(Roles.ADMIN)
    public synchronized String clearNicks(@Me Guild guild,
                                          @Arg("Undo the last recent use of this command")
                                          @Default boolean undo) throws ExecutionException, InterruptedException {
        if (previousNicksGuild != guild.getIdLong()) {
            previousNicksGuild = guild.getIdLong();
            previous.clear();
        }
        int failed = 0;
        String msg = null;
        List<Future<?>> tasks = new ArrayList<>();
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
                    tasks.add(RateLimitUtil.queue(member.modifyNickname(nick)));
                } catch (Throwable e) {
                    msg = e.getMessage();
                    failed++;
                }
            }
        }
        for (Future<?> task : tasks) {
            task.get();
        }
        if (failed != 0) {
            return "Failed to clear " + failed + " nicknames for reason: " + msg;
        }
        return "Cleared all nicknames (that I have permission to clear)!";
    }

    @Command(desc = "Add or subtract from a nation, alliance, guild or tax bracket's account balance\n" +
            "note: Mutated alliance deposits are only valid if your server is a bank/offshore\n" +
            "Use `#expire=30d` to have the amount expire after X days")
    @RolePermission(Roles.ECON)
    public String addBalance(@Me GuildDB db, @Me JSONObject command, @Me IMessageIO channel, @Me Guild guild, @Me User author, @Me DBNation me,
                             @AllowDeleted Set<NationOrAllianceOrGuildOrTaxid> accounts, Map<ResourceType, Double> amount, String note, @Switch("f") boolean force) throws Exception {

        AddBalanceBuilder builder = db.addBalanceBuilder().add(accounts, amount, note);
        if (!force) {
            builder.buildWithConfirmation(channel, command);
            return null;
        }
        boolean hasEcon = Roles.ECON.has(author, guild);
        return builder.buildAndSend(me, hasEcon);
    }

    @Command(desc = "Add account balance using a google sheet of nation's and resource amounts\n" +
            "The google sheet must have a column for nation (name, id or url) and a column named for each in-game resource")
    @RolePermission(Roles.ECON)
    public String addBalanceSheet(@Me GuildDB db, @Me JSONObject command, @Me IMessageIO channel, @Me Guild guild, @Me User author, @Me DBNation me,
                                  SpreadSheet sheet, @Arg("The transaction note to use") String note, @Switch("f") boolean force,
                                  @Arg("Subtract the amounts instead of add") @Switch("n") boolean negative) throws Exception {
        List<String> errors = new ArrayList<>();
        AddBalanceBuilder builder = db.addBalanceBuilder().addSheet(sheet, negative, errors::add, !force, note);
        if (!force) {
            builder.buildWithConfirmation(channel, command);
            return null;
        }
        boolean hasEcon = Roles.ECON.has(author, guild);
        return builder.buildAndSend(me, hasEcon);
    }

    @Command(desc = "Get the revenue of nations or alliances")
    public String revenue(@Me GuildDB db, @Me Guild guild, @Me IMessageIO channel, @Me User user, @Me DBNation me,
                          NationList nations,
                          @Arg("Include the revenue of nations unable to be taxed")
                          @Switch("t") boolean includeUntaxable,
                          @Arg("Exclude the new nation bonus")
                          @Switch("b") boolean excludeNationBonus) throws Exception {
        ArrayList<DBNation> filtered = new ArrayList<>(nations.getNations());
        if (!includeUntaxable) {
            filtered.removeIf(f -> f.getAlliance_id() == 0 || f.getVm_turns() != 0);
            filtered.removeIf(f -> f.isGray() || f.isBeige() || f.getPosition() <= 1);
        }
        double[] cityProfit = new double[ResourceType.values.length];
        double[] milUp = new double[ResourceType.values.length];
        int tradeBonusTotal = 0;
        for (DBNation nation : filtered) {
            ResourceType.add(cityProfit, nation.getRevenue(12, true, false, false, !excludeNationBonus, false, false, false));
            ResourceType.add(milUp, nation.getRevenue(12, false, true, false, false, false, false, false));
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

    @Command(desc = "Get the revenue of a city or build json")
    public String cityRevenue(@Me Guild guild, @Me IMessageIO channel, @Me User user,
                              @Arg("The city url or build json")
                              CityBuild city,
                                @Arg("The nation to calculate the revenue for\n" +
                                        "i.e. Projects, radiation, continent")
                              @Default("%user%") DBNation nation,
                              @Arg("Exclude the new nation bonus")
                              @Switch("b") boolean excludeNationBonus) throws Exception {
        if (nation == null) return "Please use " + CM.register.cmd.toSlashMention();
        JavaCity jCity = new JavaCity(city);

        double[] revenue = PnwUtil.getRevenue(null, 12, nation, Collections.singleton(jCity), false, false, !excludeNationBonus, false, false);

        JavaCity.Metrics metrics = jCity.getMetrics(nation::hasProject);
        IMessageBuilder msg = channel.create()
                .append("Daily city revenue ```" + city.toString() + "```")
                .append("```").append(PnwUtil.resourcesToString(revenue)).append("```")
                .append("Converted total: $" + MathMan.format(PnwUtil.convertedTotal(revenue)));
        if (metrics.powered != null && !metrics.powered) {
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

    @Command(desc = "Get the military unit count history (dates/times) for a nation")
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

    @Command(desc = "Get a ranking of alliances or nations by their resource production")
    public String findProducer(@Me IMessageIO channel, @Me JSONObject command, @Me Guild guild, @Me User author, @Me DBNation me,
                               @Arg("The resources to rank production of")
                               List<ResourceType> resources,
                               @Arg("Nations to include in the ranking")
                               @Default NationList nationList,
                               @Switch("m") boolean ignoreMilitaryUpkeep,
                               @Arg("Exclude color trade bloc bonus")
                               @Switch("t") boolean ignoreTradeBonus,
                               @Arg("Exclude the new nation bonus")
                               @Switch("b") boolean ignoreNationBonus,
                               @Arg("Include negative resource revenue")
                               @Switch("n") boolean includeNegative,
                               @Arg("Rank by nation instead of alliances")
                               @Switch("a") boolean listByNation,
                               @Arg("Rank by average per nation instead of total per alliance")
                               @Switch("s") boolean listAverage,
                               @Switch("u") boolean uploadFile,
                               @Arg("Include inactive nations (2 days)")
                               @Switch("i") boolean includeInactive) throws Exception {
        if (nationList == null) nationList = new SimpleNationList(Locutus.imp().getNationDB().getNations().values());
        ArrayList<DBNation> nations = new ArrayList<>(nationList.getNations());
        if (!includeInactive) nations.removeIf(f -> !f.isTaxable());

        Map<DBNation, Number> profitByNation = new HashMap<>();

        Set<Integer> nationIds = nations.stream().map(DBNation::getNation_id).collect(Collectors.toSet());

        Map<Integer, Map<Integer, DBCity>> allCities = Locutus.imp().getNationDB().getCitiesV3(nationIds);
        double[] profitBuffer = ResourceType.getBuffer();

        for (DBNation nation : nations) {
            Map<Integer, DBCity> v3Cities = allCities.get(nation.getNation_id());
            if (v3Cities == null || v3Cities.isEmpty()) continue;

//            Map<Integer, JavaCity> cities = Locutus.imp().getNationDB().toJavaCity(v3Cities);

            Arrays.fill(profitBuffer, 0);
            double[] profit = nation.getRevenue(12, true, !ignoreMilitaryUpkeep, !ignoreTradeBonus, !ignoreNationBonus, false, false, false);
            double value;
            if (resources.size() == 1) {
                value = profit[resources.get(0).ordinal()];
            } else {
                value = 0;
                for (ResourceType type : resources) {
                    value += PnwUtil.convertedTotal(type, profit[type.ordinal()]);
                }
            }
            if (value > 0 || includeNegative) {
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

    @Command(desc = "Estimate a nation's rebuy time based on unit purchase history")
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

    @Command(desc = "List the alliance rank changes of a nation or alliance members")
    public String leftAA(@Me IMessageIO io, @Me Guild guild, @Me User author, @Me DBNation me,
                         NationOrAlliance nationOrAlliance,
                         @Arg("Date to start from")
                         @Default @Timestamp Long time,
                         @Arg("Only include these nations")
                         @Default NationList filter,
                         @Arg("Ignore inactive nations (7 days)")
                         @Switch("a") boolean ignoreInactives,
                         @Arg("Ignore nations in vacation mode")
                         @Switch("v") boolean ignoreVM,
                         @Arg("Ignore nations currently a member of an alliance")
                         @Switch("m") boolean ignoreMembers,
                         @Arg("Attach a list of all nation ids found")
                         @Switch("i") boolean listIds) throws Exception {
        if (time == null) time = 0L;
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

    @Command(desc = "Save or paste a stored message")
    @RolePermission(Roles.MEMBER)
    public String copyPasta(@Me IMessageIO io, @Me GuildDB db, @Me Guild guild, @Me Member member, @Me User author, @Me DBNation me,
                            @Arg("What to name the saved message")
                            @Default String key,
                            @Default @TextArea String message,
                            @Arg("Require roles to paste the message")
                            @Default Set<Role> requiredRolesAny,
                            NationPlaceholders placeholders, ValueStore store) throws Exception {
        if (key == null) {

            Map<String, String> copyPastas = db.getCopyPastas(member);
            Set<String> options = copyPastas.keySet().stream().map(f -> f.split("\\.")[0]).collect(Collectors.toSet());

            if (options.size() <= 25) {
                // buttons
                IMessageBuilder msg = io.create().append("Options:");
                for (String option : options) {
                    msg.commandButton(CommandBehavior.DELETE_MESSAGE, CM.copyPasta.cmd.create(option, null, null), option);
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

        String value = db.getCopyPasta(key, true);

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

    @Command(desc = "Generate an audit report of a list of nations")
    @RolePermission(Roles.MEMBER)
    public String checkCities(@Me GuildDB db, @Me IMessageIO channel, @Me Guild guild, @Me User author, @Me DBNation me,
                              @Arg("Nations to audit")
                              NationList nationList,
                              @Arg("Only perform these audits (default: all)")
                              @Default Set<IACheckup.AuditType> audits,
                              @Arg("Ping the user on discord with their audit")
                              @Switch("u") boolean pingUser,
                              @Arg("Mail the audit to each nation in-game")
                              @Switch("m") boolean mailResults,
                              @Arg("Post the audit in the interview channels (if exists)")
                              @Switch("c") boolean postInInterviewChannels,
                              @Arg("Skip updating nation info from the game")
                              @Switch("s") boolean skipUpdate) throws Exception {
        Collection<DBNation> nations = nationList.getNations();
        Set<Integer> aaIds = nationList.getAllianceIds();

        if (nations.size() > 1) {
            IACategory category = db.getIACategory();
            if (category != null) {
                category.load();
                category.purgeUnusedChannels(channel);
                category.alertInvalidChannels(channel);
            }
        }

        for (DBNation nation : nations) {
            if (!db.isAllianceId(nation.getAlliance_id())) {
                return "Nation `" + nation.getName() + "` is in " + nation.getAlliance().getQualifiedName() + " but this server is registered to: "
                        + StringMan.getString(db.getAllianceIds()) + "\nSee: " + CM.settings.info.cmd.toSlashMention() + " with key `" + GuildKey.ALLIANCE_ID.name() + "`";
            }
        }

        me.setMeta(NationMeta.INTERVIEW_CHECKUP, (byte) 1);

        IACheckup checkup = new IACheckup(db, db.getAllianceList().subList(aaIds), false);

        ApiKeyPool keys = mailResults ? db.getMailKey() : null;
        if (mailResults && keys == null) throw new IllegalArgumentException("No API_KEY set, please use " + CM.credentials.addApiKey.cmd.toSlashMention() + "");

        CompletableFuture<IMessageBuilder> msg = channel.send("Please wait...");

        Map<DBNation, Map<IACheckup.AuditType, Map.Entry<Object, String>>> auditResults = new HashMap<>();

        IACheckup.AuditType[] allowed = audits == null || audits.isEmpty() ? IACheckup.AuditType.values() : audits.toArray(new IACheckup.AuditType[0]);
        for (DBNation nation : nations) {
            StringBuilder output = new StringBuilder();
            int failed = 0;

            Map<IACheckup.AuditType, Map.Entry<Object, String>> auditResult = checkup.checkup(nation, allowed, nations.size() == 1, skipUpdate);
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

    @Command(desc = "Transfer the missing resource amounts per city to a list of nations")
    @RolePermission(value = {Roles.ECON, Roles.ECON_WITHDRAW_SELF}, any = true)
    @HasOffshore
    @IsAlliance
    public static String warchest(@Me GuildDB db, @Me IMessageIO io, @Me Guild guild, @Me User author, @Me DBNation me,
                           NationList nations,
                          @Arg("The resources each nation needs for each city\n" +
                                  "Only resources they are missing is sent")
                          Map<ResourceType, Double> resourcesPerCity,
                                  @Arg("The transfer note to use\n" +
                                          "Defaults to `#WARCHEST`")
                                  @Default DepositType.DepositTypeInfo note,
                           @Arg("Do not check nation stockpile\n" +
                               "Sends the full amount of resources to each nation")
                           @Switch("s") boolean skipStockpile,
                          @Arg("The nation account to deduct from") @Switch("n") DBNation depositsAccount,
                          @Arg("The alliance bank to send from\nDefaults to the offshore") @Switch("a") DBAlliance useAllianceBank,
                          @Arg("The alliance account to deduct from\nAlliance must be registered to this guild\nDefaults to all the alliances of this guild") @Switch("o") DBAlliance useOffshoreAccount,
                          @Arg("The tax account to deduct from") @Switch("t") TaxBracket taxAccount,
                          @Arg("Deduct from the receiver's tax bracket account") @Switch("ta") boolean existingTaxAccount,
                          @Arg("Have the transfer ignored from nation holdings after a timeframe") @Switch("e") @Timediff Long expire,
                          @Arg("Have the transfer valued as cash in nation holdings")@Switch("m") boolean convertToMoney,
                           @Switch("b") boolean bypassChecks,
                           @Switch("f") boolean force) throws Exception {
        if (existingTaxAccount) {
            if (taxAccount != null) throw new IllegalArgumentException("You can't specify both `tax_id` and `existingTaxAccount`");
        }
        if (note == null) note = DepositType.WARCHEST.withValue();

        Collection<DBNation> nationSet = new HashSet<>(nations.getNations());
        Map<NationOrAlliance, String> errors = new HashMap<>();

        boolean hasEcon = Roles.ECON.has(author, guild);
        if (!hasEcon && (nationSet.size() != 1 || !nationSet.iterator().next().equals(me))) return "You only have permission to send to your own nation";

        Iterator<DBNation> iter = nationSet.iterator();
        while (iter.hasNext()) {
            DBNation nation = iter.next();
            if (nation.getActive_m() > 7200) {
                iter.remove();
                errors.put(nation, "Nation is inactive: " + TimeUtil.secToTime(TimeUnit.MINUTES, nation.getActive_m()));
            } else if (nation.getPosition() <= 1) {
                iter.remove();
                errors.put(nation, "Nation is not a member");
            } else if (nation.getVm_turns() != 0) {
                iter.remove();
                errors.put(nation, "Nation is in Vacation Mode");
            }
        }

        if (nationSet.isEmpty()) {
            return "No active members in bracket";
        }

        AllianceList aaList = db.getAllianceList().subList(nationSet);

        Map<DBNation, Map<ResourceType, Double>> fundsToSendNations = new LinkedHashMap<>();
        Map<DBNation, Map<ResourceType, Double>> memberResources2 = aaList.getMemberStockpile();

        for (DBNation nation : nationSet) {
            Map<ResourceType, Double> stockpile;
            if (skipStockpile) {
                stockpile = new HashMap<>();
            } else {
                stockpile = memberResources2.get(nation);
                if (stockpile == null) {
                    if (!aaList.isInAlliance(nation)) {
                        errors.put(nation, "No stockpile information available (not in the guild's alliance)");
                    } else {
                        errors.put(nation, "No stockpile information available (are you sure a valid api key is set?)");
                    }
                    continue;
                }
                if (PnwUtil.convertedTotal(stockpile) < 0) {
                    errors.put(nation, "Alliance information access is disabled from their **account** page");
                    continue;
                }
                Map<ResourceType, Double> toSendCurrent = new HashMap<>();
                for (ResourceType type : resourcesPerCity.keySet()) {
                    double required = resourcesPerCity.getOrDefault(type, 0d) * nation.getCities();
                    double current = stockpile.getOrDefault(type, 0d);
                    if (required > current) {
                        toSendCurrent.put(type, required - current);
                    }
                }
                if (!toSendCurrent.isEmpty()) {
                    fundsToSendNations.put(nation, toSendCurrent);
                } else {
                    errors.put(nation, "No funds need to be sent");
                    continue;
                }
            }
        }

        UUID key = UUID.randomUUID();
        TransferSheet sheet = new TransferSheet(db).write(fundsToSendNations, new LinkedHashMap<>()).build();
        BankCommands.APPROVED_BULK_TRANSFER.put(key, sheet.getTransfers());

        JSONObject command = CM.transfer.bulk.cmd.create(
                sheet.getSheet().getURL(),
                note.toString(),
                depositsAccount != null ? depositsAccount.getUrl() : null,
                useAllianceBank != null ? useAllianceBank.getUrl() : null,
                useOffshoreAccount != null ? useOffshoreAccount.getUrl() : null,
                taxAccount != null ? taxAccount.getQualifiedName() : null,
                existingTaxAccount + "",
                Boolean.FALSE.toString(),
                expire == null ? null : TimeUtil.secToTime(TimeUnit.MILLISECONDS, expire),
                String.valueOf(force),
                null,
                key.toString()
        ).toJson();

        return BankCommands.transferBulkWithErrors(io, command, author, me, db, sheet, note, depositsAccount, useAllianceBank, useOffshoreAccount, taxAccount, existingTaxAccount, expire, convertToMoney, bypassChecks, force, key, errors);
    }

    @Command(desc = "Check if a nation is a reroll and print their reroll date")
    public static String reroll(@Me IMessageIO io, DBNation nation) {
        long date = nation.getRerollDate();
        if (date == Long.MAX_VALUE) {
            return "No direct reroll found. See also: " + CM.nation.list.multi.cmd.toSlashMention();
        }
        String title = "`" + nation.getNation() + "` is a reroll";
        StringBuilder body = new StringBuilder();
        body.append("Reroll date: " + DiscordUtil.timestamp(nation.getDate(), "d") + "\n");
        body.append("Original creation date: " + DiscordUtil.timestamp(date, "d"));
        io.create().embed(title, body.toString()).send();
        return null;
    }

    @Command(desc = "Generate an optimal build json for a city")
    @RolePermission(Roles.MEMBER)
    public String optimalBuild(@Me JSONObject command, @Me IMessageIO io, @Me Guild guild, @Me User author, @Me DBNation me,
                               @Arg("A city url or build json to optimize")
                               CityBuild build,
                               @Arg("Set the days the build is expected to last before replacement (or destruction)")
                               @Default Integer days,
                               @Arg("Set the MMR (military building counts) of the city to optimize")
                               @Switch("x") @Filter("[0-9]{4}") String buildMMR,
                               @Arg("Set the age of the city to optimize")
                               @Switch("a") Integer age,
                               @Arg("Set the infrastructure level of buildings in the city to optimize")
                               @Switch("i") Integer infra,
                               @Arg("Set the damaged infrastructure level of the city to optimize")
                               @Switch("b") Integer baseReducedInfra,
                               @Arg("Set the land level of the city to optimize")
                               @Switch("l") Integer land,
                               @Arg("Set the maximum disease allowed")
                               @Switch("d") Double diseaseCap,
                               @Arg("Set the maximum crime allowed")
                               @Switch("c") Double crimeCap,
                               @Arg("Set the minimum population allowed")
                               @Switch("p") Double minPopulation,
                               @Arg("Set the radiation level")
                               @Switch("r") Double radiation,
                               @Arg("Maximize untaxed revenue for a tax rate")
                               @Switch("t")TaxRate taxRate,
                               @Arg("Require the city to produce all raw resources it uses for manufacturing")
                               @Switch("u") boolean useRawsForManu,
                               @Arg("Return a result on discord in plain text")
                               @Switch("w") boolean writePlaintext,
                               @Arg("Set the projects a city has access to")
                               @Switch("n") Set<Project> nationalProjects,
                               @Arg("Require the city build to be cash positive")
                               @Switch("m") boolean moneyPositive,
                               @Arg("Set the continent the city is in")
                               @Switch("g") Continent geographicContinent
    ) throws Exception {
        List<String> cmd = new ArrayList<>();
        Set<Character> flags = new HashSet<>();
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
        if (writePlaintext) flags.add('p');
        if (nationalProjects != null) {
            for (Project project : nationalProjects) {
                cmd.add("" + project.name());
            }
        }

        if (moneyPositive) cmd.add("cash=" + moneyPositive);
        if (geographicContinent != null) cmd.add("continent=" + geographicContinent);



        return new OptimalBuild().onCommand(io, guild, author, me, cmd, flags);
    }
}