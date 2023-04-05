package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.MMRDouble;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.Grant;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.task.balance.GetCityBuilds;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class GrantCmd extends Command {
    private final TransferCommand withdrawCommand;

    public GrantCmd(TransferCommand withdrawCommand) {
        super("grant", "loan", CommandCategory.ECON);
        this.withdrawCommand = withdrawCommand;
    }

    @Override
    public String help() {
        return super.help() + " <nation> <city|infra|land|build|project|unit|warchest|mmr=5553|mmrbuy=5553> [amount]";
    }

    @Override
    public String desc() {
        return "Grant money/rss for a city, project, build, warchest, or units\n" +
                "Add `-i` to build grants to exclude infra cost\n" +
                "Add `-l` to build grants to exclude land cost\n" +
                "Add `-e` or `#expire=60d` to have a grant's debt expire\n" +
                "Add `-c` to have a grant count as cash value in " + CM.deposits.check.cmd.toSlashMention() + "\n" +
                "Add `-o` to only send what funds they are missing for a grant\n" +
                "Add `-m` to multiply the grant per city";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        String expireStr = DiscordUtil.parseArg(args, "expire");
        Long expire = expireStr == null ? null : System.currentTimeMillis() + TimeUtil.timeToSec(expireStr);
        if (flags.contains('e')) {
            expire = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(60);
        }

        DBNation nationAccount = null;
        DBAlliance allianceAccount = null;
        DBAlliance offshoreAccount = null;

        String nationAccountStr = DiscordUtil.parseArg(args, "nation");
        if (nationAccountStr != null) {
            nationAccount = PWBindings.nation(author, nationAccountStr);
        }

        String allianceAccountStr = DiscordUtil.parseArg(args, "alliance");
        if (allianceAccountStr != null) {
            allianceAccount = PWBindings.alliance(allianceAccountStr);
        }

        String offshoreAccountStr = DiscordUtil.parseArg(args, "offshore");
        if (offshoreAccountStr != null) {
            offshoreAccount = PWBindings.alliance(offshoreAccountStr);
        }

        Double factor = null;

        for (Iterator<String> iter = args.iterator(); iter.hasNext(); ) {
            String arg = iter.next().toLowerCase();
            if (arg.startsWith("-expire") || arg.startsWith("-e") || arg.startsWith("#expire")) {
                expire = System.currentTimeMillis() + TimeUtil.timeToSec(arg.split("[:=]", 2)[1]);
                iter.remove();
            }
            else if (arg.endsWith("%")) {
                arg = arg.substring(0, arg.length() - 1);
                factor = PrimitiveBindings.Double(arg);
                if (factor != null) {
                    factor /= 100d;
                    iter.remove();
                }
            }
        }
        Double num;
        if (args.size() != 3) {
            if (args.size() == 2) {
                if (args.get(1).equalsIgnoreCase("project")) {
                    return "Usage: " + Settings.commandPrefix(true) + "grant <nation> <" + StringMan.join(Projects.PROJECTS_MAP.keySet(), "|") + "> 1";
                }
                else if (args.get(1).equalsIgnoreCase("build") || (args.get(1).startsWith("{") && args.get(1).endsWith("}"))) {
                    num = Double.MAX_VALUE;
                }
                else if (args.get(1).equalsIgnoreCase("unit")) {
                    return "Usage: " + Settings.commandPrefix(true) + "grant <nation> <" + StringMan.join(MilitaryUnit.values(), "|") + "> <amount>";
                }
                else if (args.get(1).equalsIgnoreCase("warchest")) {
                    num = 1d;
                } else {
                    return usage(event);
                }
            } else {
                return usage(event);
            }
        } else {
            num = MathMan.parseDouble(args.get(2));
            if (num == null || num <= 0) return "Invalid number: `" + args.get(2) + "`";
        }
        if (num <= 0) return "Invalid positive number: " + num;

        GuildDB guildDb = Locutus.imp().getGuildDB(event);

        me = DiscordUtil.parseNation(args.get(0));
        String typeArg = args.get(1);
        if (me == null) {
            Set<DBNation> nations = DiscordUtil.parseNations(guild, args.get(0));
            Set<Integer> requiredAAs = guildDb.getAllianceIds();
            if (!flags.contains('f')) nations.removeIf(f -> !requiredAAs.contains(f.getAlliance_id()));
            if (nations.isEmpty()) return "Invalid nation: `" + args.get(0) + "`";
            if (!Roles.ECON.has(author, guild)) return "No permission (econ)";

            SpreadSheet sheet = SpreadSheet.create(guildDb, GuildDB.Key.GRANT_SHEET);
            sheet.clearAll();
            List<String> header = new ArrayList<>(Arrays.asList(
                    "nation",
                    "cities",
                    "avg_infra",
                    "response",
                    "cost_converted",
                    "cost_raw"
            ));
            sheet.setHeader(header);

            Map<ResourceType, Double> total = new HashMap<>();
            for (DBNation nation : nations) {
                ArrayList<Object> row = new ArrayList<>();
                row.add(MarkupUtil.sheetUrl(nation.getNation(), nation.getNationUrl()));
                row.add(nation.getCities());
                row.add(nation.getAvg_infra());
                try {
                    Grant grant = generateGrant(typeArg, guildDb, nation, num, flags, false);
                    row.add(grant.getInstructions());
                    row.add(PnwUtil.convertedTotal(grant.cost()));
                    row.add(PnwUtil.resourcesToString(grant.cost()));

                    total = PnwUtil.add(total, PnwUtil.resourcesToMap(grant.cost()));
                    if (factor != null) {
                        total = PnwUtil.multiply(total, factor);
                    }
                } catch (IllegalArgumentException e) {
                    row.add(e.getMessage());
                    row.add(0);
                    row.add("{}");
                }
                sheet.addRow(row);
            }

            sheet.set(0, 0);

            String totalStr = PnwUtil.resourcesToString(total) + " worth ~$" + MathMan.format(PnwUtil.convertedTotal(total));
            sheet.attach(new DiscordChannelIO(event).create().append(totalStr), null, false, 0).send();
            return null;
        }

        Grant grant = generateGrant(typeArg, guildDb, me, num, flags, true);

        Member member = null;

        if (!flags.contains('f')) {
            Role noGrants = Roles.TEMP.toRole(guild);
            PNWUser user = Locutus.imp().getDiscordDB().getUserFromNationId(me.getNation_id());
            if (user != null) {
                member = guild.getMemberById(user.getDiscordId());
                if (member == null) {
                    throw new IllegalArgumentException("Not on this discord");
                }
            }
            if (noGrants != null && member != null && member.getRoles().contains(noGrants)) {
                throw new IllegalArgumentException("The member has been marked (with discord role) as to not receive grants");
            }
        }

        UUID uuid = UUID.randomUUID();
        BankCommands.AUTHORIZED_TRANSFERS.put(uuid, grant);

        Map<ResourceType, Double> resources = PnwUtil.resourcesToMap(grant.cost());

        if (factor != null) {
            resources = PnwUtil.multiply(resources, factor);
        }

        JSONObject command = CM.transfer.resources.cmd.create(
                me.getUrl(),
                PnwUtil.resourcesToString(resources),
                grant.getType().toString(),
                (nationAccount == null ? me : nationAccount).getUrl(),
                allianceAccount != null ? allianceAccount.getUrl() : null,
                offshoreAccount != null ? offshoreAccount.getUrl() : null,
                String.valueOf(flags.contains('o')),
                expire != null ? "timestamp:" + expire : null,
                uuid.toString(),
                String.valueOf(flags.contains('c')),
                String.valueOf(flags.contains('f'))
        ).toJson();
        StringBuilder msg = new StringBuilder();
        msg.append(PnwUtil.resourcesToString(resources)).append("\n")
                .append("Current values for: " + me.getNation()).append('\n')
                .append("Cities: " + me.getCities()).append('\n')
                .append("Infra: " + me.getAvg_infra()).append('\n')
        ;

        msg.append("\n**INSTRUCTIONS:** ").append(grant.getInstructions());

        new DiscordChannelIO(event).create().confirmation(grant.title(), msg.toString(), command).cancelButton().send();

        return null;
    }

    public Grant generateGrant(String arg, GuildDB guildDb, DBNation me, double amt, Set<Character> flags, boolean single) throws IOException, ExecutionException, InterruptedException {
        Grant grant;

        boolean existing = flags.contains('o');
        boolean force = flags.contains('f');
        boolean noInfra = flags.contains('i');
        boolean noLand = flags.contains('l');

        me = new DBNation(me);

        PNWUser user = Locutus.imp().getDiscordDB().getUserFromNationId(me.getNation_id());
        if (!force) {
            if (user == null) throw new IllegalArgumentException("No user found for nation: " + me.getNation_id());
            Guild guild = guildDb.getGuild();
            Member member = guild.getMemberById(user.getDiscordId());
            if (member == null) {
                throw new IllegalArgumentException("Not on this discord");
            }
        }

        Map<ResourceType, Double> resources = new HashMap<>();

        if (arg.equalsIgnoreCase("city")) {
            if (me.getCityTurns() > 0 && me.getCities() >= 10 && !force) throw new IllegalArgumentException("You still have a city timer");
            int currentCity = me.getCities();
            int numBuy = (int) amt;
            if (numBuy >= 10) numBuy = numBuy - currentCity;

            int maxBuy = Math.max(1, 10 - currentCity);
            if (numBuy > maxBuy && !force) throw new IllegalArgumentException("Only " + maxBuy + " cities can be granted");
            if (numBuy <= 0) throw new IllegalArgumentException("Already has " + currentCity + " cities");


            grant = new Grant(me, DepositType.CITY.withAmount(currentCity + numBuy));
            grant.setAmount(amt);
            grant.addCity(me.getCities());
            grant.setInstructions(grantCity(me, numBuy, resources, force));
        } else if (arg.equalsIgnoreCase("infra")) {
            // city id
            // amt
            grant = new Grant(me, DepositType.INFRA.withValue((int) amt, -1));
            grant.setAmount(amt);
            grant.setInstructions(grantInfra(me, (int) amt, resources, force, single));
            grant.setAllCities();
        } else if (arg.equalsIgnoreCase("land")) {
            grant = new Grant(me, DepositType.LAND.withValue((int) amt, -1));
            grant.setAmount((int) amt);
            grant.setInstructions(grantLand(me, (int) amt, resources, force));
            grant.setAllCities();
        } else if (arg.contains("mmrbuy=")) {
            MMRDouble mmr = MMRDouble.fromString(arg.split("=")[1]);
            grant = new Grant(me, DepositType.WARCHEST.withValue());
            grant.setAmount(amt);
            grant.setInstructions(grantMMRBuy(me, mmr, (int) amt, resources, force));
            grant.setAllCities();
        } else if (arg.contains("mmr=")) {
            MMRDouble mmr = MMRDouble.fromString(arg.split("=")[1]);
            grant = new Grant(me, DepositType.WARCHEST.withValue());
            grant.setAmount(amt);
            grant.setInstructions(grantMMR(me, mmr, (int) amt, resources, force));
            grant.setAllCities();
        } else if (arg.startsWith("{")) {
            if (arg.contains("infra_needed")) {
                JavaCity city = new JavaCity(arg);
                city.setLand(0d);

                city.validate(me.getContinent(), me::hasProject);

                long pair = MathMan.pairInt((int) city.getInfra(), city.getLand().intValue());
                int citiesAmt;

                Map<Integer, JavaCity> from;
                if (amt == Double.MAX_VALUE) {
                    from = new GetCityBuilds(me).adapt().get(me);
                    citiesAmt = -1;
                } else if (amt == 1) {
                    from = new HashMap<>();
                    JavaCity newCity = new JavaCity();
                    if (noInfra) newCity.setInfra(city.getInfra());
                    if (noLand) newCity.setLand(city.getLand());
                    from.put(0, newCity);
                    citiesAmt = 1;
                } else {
                    citiesAmt = (int) amt;
                    JavaCity found = me.getCityMap(true).get(citiesAmt);
                    if (found == null) {
                        throw new IllegalArgumentException("Invalid city id: " + amt + " (must be a valud city id, 1, or no value)");
                    }
                    from = Collections.singletonMap(citiesAmt, found);
                }
                grant = new Grant(me, DepositType.BUILD.withValue(pair, citiesAmt));

                for (Map.Entry<Integer, JavaCity> entry : from.entrySet()) {
                    if (noInfra) entry.getValue().setInfra(city.getInfra());
                    if (noLand) entry.getValue().setLand(city.getLand());
                }
                if (!noInfra) grant.addNote("#infra=" + city.getInfra());
                if (!noLand) grant.addNote("#land=" + city.getLand());
                double[] buffer = new double[ResourceType.values.length];
                grant.setInstructions(city.instructions(from, buffer));
                resources = PnwUtil.resourcesToMap(buffer);
            } else {
                grant = new Grant(me, DepositType.GRANT.withValue());
                grant.setInstructions("transfer resources");
                resources = PnwUtil.parseResources(arg);
            }
        } else if (arg.equalsIgnoreCase("build")) {
            throw new IllegalArgumentException("Usage: " + Settings.commandPrefix(true) + "grant <nation> <json> 1");
        } else if (arg.equalsIgnoreCase("warchest")) {
            Map<ResourceType, Double> stockpile = me.getStockpile();
            if (stockpile == null) throw new IllegalArgumentException("Unable to fetch stockpile (are you sure they are a member?)");
            Map<ResourceType, Double> cityWc = guildDb.getPerCityWarchest(me);
            resources = PnwUtil.multiply(cityWc, (double) me.getCities());
            if (amt > 0 && amt != 1) {
//                Double multiplier = MathMan.parseDouble(args.get(2));
//                if (multiplier == null) return "Invalid multiplier: `" + args.get(2) + "`";
                resources = PnwUtil.multiply(resources, amt);
            }

            for (Map.Entry<ResourceType, Double> entry : stockpile.entrySet()) {
                double required = resources.getOrDefault(entry.getKey(), 0d);
                if (required > 0) {
                    required -= entry.getValue();
                    if (required <= 0) resources.remove(entry.getKey());
                    else resources.put(entry.getKey(), required);
                }
            }
            grant = new Grant(me, DepositType.WARCHEST.withValue());
            grant.setInstructions("warchest");
        } else {
            Project project = Projects.get(arg);
            if (project == null) {
                if (arg.equalsIgnoreCase("project")) {
                    if (me.getProjectTurns() > 0 && me.getCities() >= 10 && !force) throw new IllegalArgumentException("You still have a project timer");
                    throw new IllegalArgumentException("Usage: " + Settings.commandPrefix(true) + "grant <nation> <" + StringMan.join(Projects.PROJECTS_MAP.keySet(), "|") + "> 1");
                }
                if (arg.equalsIgnoreCase("unit")) {
                    throw new IllegalArgumentException("Usage: " + Settings.commandPrefix(true) + "grant <nation> <" + StringMan.join(MilitaryUnit.values(), "|") + "> <amount>");
                }

                MilitaryUnit unit = MilitaryUnit.get(arg);
                if (unit == null) usage();

                amt -= me.getUnits(unit);
                if (amt <= 0) {
                    throw new IllegalArgumentException("You already have " + amt + " " + unit.name());
                }
                int max = Integer.MAX_VALUE;
                switch (unit) {
                    case SOLDIER:
                    case TANK:
                    case AIRCRAFT:
                    case SHIP:
                        MilitaryBuilding building = unit.getBuilding();
                        max = building.cap(f -> false) * building.max() * me.getCities();
                        break;
                    case SPIES:
                        max = Projects.INTELLIGENCE_AGENCY.get(me) > 0 ? 60 : 50;
                        break;
                    case NUKE:
                    case MISSILE:
                        if (!flags.contains('f')) throw new IllegalArgumentException("We do not approve grants for missiles/nukes");
                    case MONEY:
                        break;
                }
                if (amt + me.getUnits(unit) > max) {
                    throw new IllegalArgumentException(me.getNation() + " can only have up to " + max + " " + unit.getName());
                }

                resources = PnwUtil.resourcesToMap(unit.getCost((int) amt));
                grant = new Grant(me, DepositType.WARCHEST.withValue());
                grant.setInstructions("Go to <" + Settings.INSTANCE.PNW_URL() + "/military/" + unit.getName() + "/> and purchase " + (int) amt + " " + unit.getName());
            } else {
                if (me.projectSlots() <= me.getNumProjects() && !flags.contains('f')) {
                    throw new IllegalArgumentException("Error: " + me.getNationUrl() + " has full project slots " + (me.projectSlots() + "<=" + me.getNumProjects()));
                }
                resources = project.cost();
                if (!force && PnwUtil.convertedTotal(resources) > 2000000 && me.getDomesticPolicy() != DomesticPolicy.TECHNOLOGICAL_ADVANCEMENT) {
                    throw new IllegalArgumentException("Please set your Domestic Policy to `Technological Advancement` in <" + Settings.INSTANCE.PNW_URL() + "/nation/edit/> to save 5%.");
                }
                if (me.hasProject(project)) {
                    throw new IllegalArgumentException("You already have: " + project.name());
                }
                double factor = 1;

                if (me.getDomesticPolicy() == DomesticPolicy.TECHNOLOGICAL_ADVANCEMENT) {
                    factor -= 0.05;
                    if (me.hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY)) {
                        factor -= 0.025;
                    }
                }
                if (factor != 1) {
                    resources = PnwUtil.multiply(resources, factor);
                }

                grant = new Grant(me, DepositType.PROJECT.withAmount(project.ordinal()));
                grant.setInstructions("Go to <" + Settings.INSTANCE.PNW_URL() + "/nation/projects/> and purchase " + project.name());
            }
        }

        if (flags.contains('m')) {
            resources = PnwUtil.multiply(resources, (double) me.getCities());
        }

        if (existing) {
            Map<ResourceType, Double> stockpile = me.getStockpile();
            for (Map.Entry<ResourceType, Double> entry : stockpile.entrySet()) {
                if (entry.getValue() > 0) {
                    double newAmt = Math.max(0, resources.getOrDefault(entry.getKey(), 0d) - entry.getValue());
                    if (newAmt <= 0) {
                        resources.remove(entry.getKey());
                    } else {
                        resources.put(entry.getKey(), newAmt);
                    }
                }
            }

        }

        Map<ResourceType, Double> finalResources = resources;
        grant.setCost(f -> PnwUtil.resourcesToArray(finalResources));

        return grant;
    }

    public String grantMMR(DBNation me, MMRDouble mmr, int numBuys, Map<ResourceType, Double> resources, boolean force) {
        if (numBuys > 5 && !force) {
            throw new IllegalArgumentException("You cannot grant more than 5 full buys");
        }

        StringBuilder response = new StringBuilder();
        response.append(" - mmr[unit]=" + me.getMMR() + "\n");
        response.append(" - mmr[build]=" + me.getMMRBuildingStr() + "\n");
        response.append(" - Cities: " + me.getCities() + "\n\n");
        response.append("Buy for mmr=" + mmr.toString() + " for " + numBuys + " full buys\n");

        int cities = me.getCities();
        for (MilitaryUnit unit : MilitaryUnit.values()) {
            MilitaryBuilding building = unit.getBuilding();
            if (building == null) continue;
            double numBuildings = mmr.get(unit) * cities;
            int numUnitsPerRebuy = (int) (Math.floor(building.max() * numBuildings));
            int numUnits = numUnitsPerRebuy * numBuys;
            resources = PnwUtil.addResourcesToA(resources, PnwUtil.resourcesToMap(unit.getCost(numUnits)));
            response.append(" - " + numUnits + " x " + unit);
            if (numBuys != 1) {
                response.append(" (" + numUnitsPerRebuy + " per full buy)");
            }
            response.append("\n");
        }

        return response.toString();
    }

    public String grantMMRBuy(DBNation me, MMRDouble mmr, int numBuys, Map<ResourceType, Double> resources, boolean force) {
        if (numBuys > 5 && !force) {
            throw new IllegalArgumentException("You cannot grant more than 5 days of buys");
        }

        StringBuilder response = new StringBuilder();
        response.append("**Warchest for " + me.getNation() + "**:\n");
        response.append(" - mmr[unit]=" + me.getMMR() + "\n");
        response.append(" - mmr[build]=" + me.getMMRBuildingStr() + "\n");
        response.append(" - Cities: " + me.getCities() + "\n\n");
        response.append("Buy for mmr=" + mmr.toString() + " over " + numBuys + " days\n");

        int cities = me.getCities();
        for (MilitaryUnit unit : MilitaryUnit.values()) {
            MilitaryBuilding building = unit.getBuilding();
            if (building == null) continue;
            double numBuildings = mmr.get(unit) * cities;
            int numUnitsPerDay = (int) (Math.floor(building.perDay() * numBuildings));
            int numUnits = numUnitsPerDay * numBuys;
            resources = PnwUtil.addResourcesToA(resources, PnwUtil.resourcesToMap(unit.getCost(numUnits)));
            response.append(" - " + numUnits + " x " + unit);
            if (numBuys != 1) {
                response.append(" (" + numUnitsPerDay + " per day)");
            }
            response.append("\n");
        }

        return response.toString();
    }

    public String grantLand(DBNation me, int numBuy, Map<ResourceType, Double> resources, boolean force) throws InterruptedException, ExecutionException, IOException {
        if (numBuy > 2000 && !force) {
            throw new IllegalArgumentException("Land grants >2000 are not approved as they are unprofitable.");
        }
        Map<Integer, JavaCity> myBuilds = new GetCityBuilds(me).adapt().get(me);

        double totalCost = 0;

        for (Map.Entry<Integer, JavaCity> entry : myBuilds.entrySet()) {
            double land = entry.getValue().getLand();
            if (land < numBuy) {
                totalCost += PnwUtil.calculateLand((int) land, numBuy);
            }
        }
        double factor = 1;
        boolean ala = me.hasProject(Projects.ARABLE_LAND_AGENCY);
        boolean aec = me.hasProject(Projects.ADVANCED_ENGINEERING_CORPS);
        boolean expansion = me.getDomesticPolicy() == DomesticPolicy.RAPID_EXPANSION;
        if (ala) factor -= 0.05;
        if (aec) factor -= 0.05;
        if (expansion) {
            factor -= 0.05;
            if (me.hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY)) {
                factor -= 0.025;
            }
        }
        totalCost *= factor;

        resources.put(ResourceType.MONEY, totalCost);

        StringBuilder response = new StringBuilder("Go to your cities page and enter `@" + numBuy + "` into the land field.\n" +
                Projects.ARABLE_LAND_AGENCY + ": " + ala + "\n" +
                Projects.ADVANCED_ENGINEERING_CORPS + ": " + aec + "\n" +
                DomesticPolicy.RAPID_EXPANSION + ": " + expansion + "\n" +
                Projects.GOVERNMENT_SUPPORT_AGENCY +": " + me.hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY));

//        if (numBuy > 2000) {
//            JavaCity newCity = new JavaCity(myBuilds.values().iterator().next());
//            newCity.setLand((double) numBuy);
//            JavaCity optimal = newCity.optimalBuild(pnwNation, me, 10000);
//            double[] newProfit = new double[ResourceType.values.length];
//            if (optimal != null) {
//                newProfit = optimal.profit(me, pnwNation, newProfit);
//            }
//
//            double[] oldProfit = new double[ResourceType.values.length];
//            for (Map.Entry<Integer, JavaCity> entry : myBuilds.entrySet()) {
//                oldProfit = entry.getValue().profit(me, pnwNation, oldProfit);
//            }
//
//            double profit = PnwUtil.convertedTotal(newProfit) * pnwNation.getCities() - PnwUtil.convertedTotal(oldProfit);
//            response.append("\nProfit/day: $").append(MathMan.format(profit));
//            double roi =( ((profit * 120 - totalCost) / totalCost) * 7 * 100 / 120);
//            response.append("\nROI/120d: ").append(MathMan.format(roi)).append("%");
//
//        }

        return response.toString();
    }

    public String grantInfra(DBNation me, int numBuy, Map<ResourceType, Double> resources, boolean force, boolean fetchROI) throws InterruptedException, ExecutionException, IOException {
        if (me.getCities() < 9 && numBuy > 1700 && !force) {
            throw new IllegalArgumentException("Please grant up to C10 before buying infra.");
        }
        if (numBuy > 2500 && !force) {
            throw new IllegalArgumentException("Infra grants >2500 are not approved as they are unprofitable.");
        }
        Map<Integer, JavaCity> myBuilds = new GetCityBuilds(me).adapt().get(me);

        double totalCost = 0;

        for (Map.Entry<Integer, JavaCity> entry : myBuilds.entrySet()) {
            double infra = entry.getValue().getInfra();
            if (infra < numBuy) {
                totalCost += PnwUtil.calculateInfra((int) infra, numBuy);
            }
        }

        if (totalCost <= 0) return "You already have " + numBuy + " in your cities";

        boolean urbanization = me.getDomesticPolicy() == DomesticPolicy.URBANIZATION;
        boolean gsa = me.hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY);
        boolean cce = me.hasProject(Projects.CENTER_FOR_CIVIL_ENGINEERING);
        boolean aec = me.hasProject(Projects.ADVANCED_ENGINEERING_CORPS);

        if (numBuy > 1700 && !urbanization && !force) {
            throw new IllegalArgumentException("Please set Urbanization as your domestic policy");
        }

        double factor = 1;
        if (urbanization) {
            factor -= 0.05;
            if (gsa) factor -= 0.025;
        }
        if (cce) factor -= 0.05;
        if (aec) factor -= 0.05;

        totalCost = totalCost * factor;

        resources.put(ResourceType.MONEY, totalCost);

        StringBuilder response = new StringBuilder();
        response.append("Go to your cities page and enter `@" + numBuy + "` into the infrastructure field." +
                "\nUrbanization: " + urbanization +
                "\n" + Projects.CENTER_FOR_CIVIL_ENGINEERING + ": " + cce + "\n" +
                Projects.ADVANCED_ENGINEERING_CORPS + ": " + aec + "\n" +
                Projects.GOVERNMENT_SUPPORT_AGENCY + ": " + gsa);

//        if (numBuy > 1500 && fetchROI) {
//            JavaCity newCity = new JavaCity(myBuilds.values().iterator().next());
//            newCity.clear();
//            newCity.setInfra(numBuy);
//            JavaCity optimal = newCity.optimalBuild(pnwNation, me, 10000);
//            double[] newProfit = new double[ResourceType.values.length];
//            if (optimal != null) {
//                for (Map.Entry<Integer, JavaCity> entry : myBuilds.entrySet()) {
//                    JavaCity newCityX = new JavaCity(optimal);
//                    JavaCity oldCityX = entry.getValue();
//                    newCityX.setAge(oldCityX.getAge());
//                    newProfit = newCityX.profit(me, pnwNation, newProfit);
//                }
//            }
//
//            double[] oldProfit = new double[ResourceType.values.length];
//            for (Map.Entry<Integer, JavaCity> entry : myBuilds.entrySet()) {
//                oldProfit = entry.getValue().profit(me, pnwNation, oldProfit);
//            }
//
//            double profit = PnwUtil.convertedTotal(newProfit) - PnwUtil.convertedTotal(oldProfit);
//            response.append("\nProfit/day: $").append(MathMan.format(profit));
//            double roi =( ((profit * 120 - totalCost) / totalCost) * 7 * 100 / 120);
//            response.append("\nROI/120d: ").append(MathMan.format(roi)).append("%");
//        }

        return response.toString();
    }

    public String grantCity(DBNation me, int numBuy, Map<ResourceType, Double> resources, boolean force) throws IOException {
        int currentCity = me.getCities();

        boolean cp = me.hasProject(Projects.URBAN_PLANNING);
        boolean acp = me.hasProject(Projects.ADVANCED_URBAN_PLANNING);
        boolean mp = me.hasProject(Projects.METROPOLITAN_PLANNING);
        boolean manifest = me.getDomesticPolicy() == DomesticPolicy.MANIFEST_DESTINY;
        boolean gsa = me.hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY);

        double cost = 0;
        for (int i = currentCity; i < currentCity + numBuy; i++) {
            cost += PnwUtil.nextCityCost(i, manifest, cp, acp, mp, gsa);
        }

        StringBuilder result = new StringBuilder();

        if (currentCity >= Projects.URBAN_PLANNING.requiredCities() && !cp && !force) {
            result.append(Projects.URBAN_PLANNING + " has not been built\n");
        }

        if (currentCity >= Projects.ADVANCED_URBAN_PLANNING.requiredCities() && !acp && !force) {
            result.append(Projects.ADVANCED_URBAN_PLANNING + " has not been built\n");
        }

        if (currentCity >= Projects.METROPOLITAN_PLANNING.requiredCities() && !mp && !force) {
            result.append(Projects.METROPOLITAN_PLANNING.requiredCities() + " has not been built\n");
        }


        if (currentCity > 10 && !manifest && !force) {
            throw new IllegalArgumentException("Please set Manifest Destiny as your domestic policy");
        }

        resources.put(ResourceType.MONEY, cost);

        if (numBuy == 1) {
            result.append("Then go to <https://politicsandwar.com/city/create/> and create your new city.");
        } else {
            result.append("Then go to <https://politicsandwar.com/city/create/> and buy " + numBuy + " new cities.");
        }
        return result.toString();
    }
}
