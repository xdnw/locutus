package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv2.PoliticsAndWarV2;
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
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Filter;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.TextArea;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasOffshore;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RankPermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.commands.trade.FindProducer;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.pnw.Alliance;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.trade.Offer;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.project.Project;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UnsortedCommands {
    @Command(desc ="View nation or AA bank contents")
    @RolePermission(Roles.MEMBER)
    @IsAlliance
    public String stockpile(@Me MessageChannel channel, @Me Guild guild, @Me GuildDB db, @Me DBNation me, @Me User author, NationOrAlliance nationOrAlliance) throws IOException {
        Map<ResourceType, Double> totals = new HashMap<>();

        PoliticsAndWarV2 allowedApi = db.getApi();
        PoliticsAndWarV2 api;

        if (nationOrAlliance.isAlliance()) {
            Alliance alliance = nationOrAlliance.asAlliance();
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
        DiscordUtil.createEmbedCommand(channel, nationOrAlliance.getName() + " stockpile", out);
        return null;
    }

    @Command(desc = "List the inflows of a nation or alliance over a period of time")
    public String inflows(@Me MessageChannel channel, Set<NationOrAlliance> nationOrAlliances, @Timestamp long cutoffMs, @Switch('i') boolean hideInflows, @Switch('o') boolean hideOutflows) {
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

                List<Offer> trades = Locutus.imp().getTradeManager().getTradeDb().getOffers(nation.getNation_id(), cutoffMs);
                for (Offer offer : trades) {
                    int per = offer.getPpu();
                    ResourceType type = offer.getResource();
                    if (per > 1 && (per < 10000 || (type != ResourceType.FOOD && per < 100000))) {
                        continue;
                    }
                    long amount = offer.getAmount();
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

        if ((!aaInflow.isEmpty() || !nationInflow.isEmpty()) && !hideInflows) {
            channel.sendMessage("Net inflows: ").complete();
            sendIO(channel, selfName, true, aaInflow, cutoffMs, true);
            sendIO(channel, selfName, false, nationInflow, cutoffMs, true);
        }
        if ((!aaOutflow.isEmpty() || !nationOutflow.isEmpty()) && !hideOutflows) {
            channel.sendMessage("Net outflows: ").complete();
            sendIO(channel, selfName, true, aaOutflow, cutoffMs, false);
            sendIO(channel, selfName, false, nationOutflow, cutoffMs, false);
        }

        if (aaInflow.isEmpty() && nationInflow.isEmpty() && aaOutflow.isEmpty() && nationOutflow.isEmpty()) {
            return "No results.";
        } else {
            return "Done!";
        }
    }

    private void sendIO(MessageChannel channel, String selfName, boolean isAlliance, Map<Integer, List<Transaction2>> transferMap, long timestamp, boolean inflow) {
        String URL_BASE = "" + Settings.INSTANCE.PNW_URL() + "/%s/id=%s";
        long days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - timestamp);
        StringBuilder result = new StringBuilder();
        for (Map.Entry<Integer, List<Transaction2>> entry : transferMap.entrySet()) {
            int id = entry.getKey();

            String typeOther = isAlliance ? "alliance" : "nation";
            String name = PnwUtil.getName(id, isAlliance);
            String url = String.format(URL_BASE, typeOther, id);

            List<Transaction2> transfers = entry.getValue();
            String title = inflow ? name + " > " + selfName : selfName + " > " + name;
            String followCmd = "!inflows " + url + " " + timestamp;

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

            String infoCmd = "!pw-who " + url;
//            Message msg = PnwUtil.createEmbedCommand(channel, title, message.toString(), EMOJI_FOLLOW, followCmd, EMOJI_QUESTION, infoCmd);
            result.append(title + ": " + message).append("\n");
        }
        DiscordUtil.sendMessage(channel, result.toString());
    }

    @Command(desc="Login to allow locutus to run scripts through your account (Avoid using if possible)")
    @RankPermission(Rank.OFFICER)
    public String login(@Me Message message, @Me User author, @Me DBNation me, @Me Guild guild, String username, String password) {
        try {
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
                    return "An officer is already connected";
            }

            Auth auth = new Auth(me.getNation_id(), username, password);
            String key = auth.getApiKey();

            Locutus.imp().getDiscordDB().addUserPass(author.getIdLong(), username, password);
            if (existingAuth != null) existingAuth.setValid(false);
            Auth myAuth = me.getAuth(null);
            if (myAuth != null) myAuth.setValid(false);

            return "Login successful.";
        } finally {
            if (guild != null) {
                RateLimitUtil.queue(message.delete());
            }
        }
    }

    @Command(desc = "Remove your login details from locutus")
    public String logout(@Me DBNation me, @Me User author) {
        if (Locutus.imp().getDiscordDB().getUserPass(author.getIdLong()) != null) {
            Locutus.imp().getDiscordDB().logout(author.getIdLong());
            Auth cached = me.auth;
            if (cached != null) {
                cached.setValid(false);
            }
            me.auth = null;
            return "Logged out";
        }
        return "You are not logged in";
    }

    @Command
    @IsAlliance
    public String listAllianceMembers(@Me MessageChannel channel, @Me GuildDB db, int page) {
        List<DBNation> nations = Locutus.imp().getNationDB().getNations(Collections.singleton(db.getAlliance_id()));

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
                response.append(n.getNation()).append(" | ").append(n.getAlliance()).append(" | ").append(active);
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

    @Command(desc = "Modify a nation, alliance or guild's deposits")
    @RolePermission(Roles.ECON)
    public String addBalance(@Me Message message, @Me MessageChannel channel, @Me Guild guild, @Me User author, @Me DBNation nation,
                             NationOrAllianceOrGuild nationOrAllianceOrGuild, Map<ResourceType, Double> amount, String note, @Switch('f') boolean force) throws Exception {
        List<String> args = new ArrayList<>(Arrays.asList(
                nationOrAllianceOrGuild.getTypePrefix() + ":" + nationOrAllianceOrGuild.getIdLong(),
                PnwUtil.resourcesToString(amount),
                note
        ));
        Set<Character> flags = new HashSet<>();
        if (force) flags.add('f');
        return new AddBalance().onCommand(message, channel, guild, author, nation, args, flags);
    }

    @Command(desc = "Modify a nation, alliance or guild's deposits")
    @RolePermission(Roles.ECON)
    public String addBalanceSheet(@Me Message message, @Me MessageChannel channel, @Me Guild guild, @Me User author, @Me DBNation me,
                                  SpreadSheet sheet, String note, @Switch('f') boolean force) throws Exception {
        List<String> args = new ArrayList<>(Arrays.asList(
                "sheet:" + sheet.getSpreadsheetId(),
                note
        ));
        Set<Character> flags = new HashSet<>();
        if (force) flags.add('f');
        return new AddBalance().onCommand(message, channel, guild, author, me, args, flags);
    }

    @Command
    public String nationRevenue(@Me GuildDB db, @Me Guild guild, @Me MessageChannel channel, @Me User user, @Me DBNation me,
                                NationList nations, @Switch('i') boolean includeInactive, @Switch('b') boolean excludeNationBonus) throws Exception {
        if (!db.isWhitelisted() && nations.getNations().size() > 8000) return "This server is not permitted to check " + nations.getNations() + " nations (max: 8000). Consider using filters e.g. `#vm_turns=0,#position>1`";
        List<String> cmd = new ArrayList<>();
        cmd.add(nations.getFilter());
        if (includeInactive) cmd.add("-i");
        if (excludeNationBonus) cmd.add("-b");
        return new Revenue().onCommand(guild, channel, user, me, cmd);
    }

    @Command
    public String cityRevenue(@Me Guild guild, @Me MessageChannel channel, @Me User user, @Me DBNation me,
                              CityBuild city, @Switch('b') boolean excludeNationBonus) throws Exception {
        List<String> cmd = new ArrayList<>(Arrays.asList(city.toString()));
        if (excludeNationBonus) cmd.add("-b");
        return new Revenue().onCommand(guild, channel, user, me, cmd);
    }

    @Command(desc = "Generate an optimal build for a city")
    @RolePermission(Roles.MEMBER)
    @WhitelistPermission
    public String optimalBuild(@Me Message message, @Me MessageChannel channel, @Me Guild guild, @Me User author, @Me DBNation me,
            CityBuild build, @Default Integer days,
                               @Switch('x') @Filter("[0-9]{4}") String buildMMR,
                               @Switch('a') Integer age,
                               @Switch('i') Integer infra,
                               @Switch('b') Integer baseReducedInfra,
                               @Switch('l') Integer land,
                               @Switch('d') Double diseaseCap,
                               @Switch('c') Double crimeCap,
                               @Switch('p') Double minPopulation,
                               @Switch('r') Double radiation,
                               @Switch('t')TaxRate taxRate,
                               @Switch('u') boolean useRawsForManu,
                               @Switch('w') boolean writePlaintext,
                               @Switch('n') Set<Project> nationalProjects,
                               @Switch('m') boolean moneyPositive,
                               @Switch('g') Continent geographicContinent
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

    @Command(desc = "Generate an optimal build for a city")
    @RolePermission(Roles.ECON)
    @HasOffshore
    public String warchest(@Me MessageChannel channel, @Me Guild guild, @Me User author, @Me DBNation me,
                           NationList nations, Map<ResourceType, Double> resources, String note) throws Exception {
        List<String> cmd = Arrays.asList(nations.getFilter(), PnwUtil.resourcesToString(resources), note);
        return new Warchest().onCommand(guild, channel, author, me, cmd);
    }


    @Command
    @RolePermission(Roles.MEMBER)
    public String copyPasta(@Me MessageChannel channel, @Me Guild guild, @Me User author, @Me DBNation me,
            String key, @Default @TextArea String message) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(key + ""));
        if (message != null) cmd.add(message);
        return new CopyPasta().onCommand(guild, channel, author, me, cmd);
    }

    @Command
    @WhitelistPermission
    @RolePermission(Roles.MEMBER)
    public String checkCities(@Me MessageChannel channel, @Me Guild guild, @Me User author, @Me DBNation me,
                              NationList nations, @Switch('p') boolean ping, @Switch('m') boolean mailResults, @Switch('c') boolean postInInterviewChannels) throws Exception {
        List<String> cmd = new ArrayList<>(Arrays.asList( nations.getFilter()));
        if (ping) cmd.add("-p");
        if (mailResults) cmd.add("-m");
        if (postInInterviewChannels) cmd.add("-c");

        return new CheckCities().onCommand(guild, channel, author, me, cmd);
    }

    @Command
    public String reroll(@Me MessageChannel channel, @Me Guild guild, @Me User author, @Me DBNation me,
                         DBNation nation) throws Exception {
        return new Reroll().onCommand(guild, channel, author, me, nation.getNationUrl());
    }

    @Command
    public String unitHistory(@Me MessageChannel channel, @Me Guild guild, @Me User author, @Me DBNation me,
                              DBNation nation, MilitaryUnit unit, @Switch('p') Integer page) throws Exception {
        List<String> cmd = new ArrayList<>(Arrays.asList(nation.getNation_id() + "", "" + unit));
        if (page != null) cmd.add("page:" + page);
        return new UnitHistory().onCommand(guild, channel, author, me, cmd);
    }

    @Command
    public String rebuy(@Me MessageChannel channel, @Me Guild guild, @Me User author, @Me DBNation me,
                              DBNation nation) throws Exception {
        return new Rebuy().onCommand(guild, channel, author, me, nation.getNationUrl());
    }

    @Command
    public String leftAA(@Me MessageChannel channel, @Me Guild guild, @Me User author, @Me DBNation me,
            NationOrAlliance nationOrAlliance, @Default @Timestamp Long time, @Default NationList filter,
                         @Switch('a') boolean ignoreInactives,
                         @Switch('v') boolean ignoreVM,
                         @Switch('m') boolean ignoreMembers,
                         @Switch('i') boolean listIds) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(nationOrAlliance.getUrl());
        if (time != null) {
            cmd.add("timestamp:" + time);
        }
        if (filter != null) {
            cmd.add("" + filter.getFilter());
        }
        if (ignoreInactives) cmd.add("-a");
        if (ignoreVM) cmd.add("-v");
        if (ignoreMembers) cmd.add("-m");
        if (listIds) cmd.add("-i");

        return new LeftAA().onCommand(guild, channel, author, me, cmd);
    }

    @Command
    @RolePermission(Roles.MEMBER)
    public String findProducer(@Me MessageChannel channel, @Me Guild guild, @Me User author, @Me DBNation me,
                               List<ResourceType> resources, @Default NationList nations,
                               @Switch('m') boolean ignoreMilitaryUpkeep,
                               @Switch('t') boolean ignoreTradeBonus,
                               @Switch('n') boolean ignoreNationBonus,
                               @Switch('a') boolean listByNation,
                               @Switch('s') boolean listAverage) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(StringMan.join(resources, ","));
        if (nations != null) cmd.add("" + nations.getFilter());

        if (ignoreMilitaryUpkeep) cmd.add("-m");
        if (ignoreTradeBonus) cmd.add("-t");
        if (ignoreNationBonus) cmd.add("-n");
        if (listByNation) cmd.add("-a");
        if (listAverage) cmd.add("-s");

        return new FindProducer().onCommand(guild, channel, author, me, cmd);
    }

    @Command
    @RolePermission(any = true, value = {Roles.ADMIN, Roles.INTERNAL_AFFAIRS, Roles.ECON, Roles.MILCOM, Roles.FOREIGN_AFFAIRS})
    public String keyStore(@Me MessageChannel channel, @Me Guild guild, @Me User author, @Me DBNation me,
                           @Default GuildDB.Key key, @Default @TextArea String value) throws Exception {
        List<String> cmd = new ArrayList<>();
        if (key != null) cmd.add(key + "");
        if (value != null) cmd.add(value);
        return new KeyStore().onCommand(guild, channel, author, me, cmd);
    }
}
