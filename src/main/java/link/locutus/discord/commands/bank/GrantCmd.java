package link.locutus.discord.commands.bank;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.EscrowMode;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.MMRDouble;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.Grant;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.sheet.SpreadSheet;
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
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class GrantCmd extends Command {
    private final TransferCommand withdrawCommand;

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(
                CM.grant.project.cmd,
                CM.grant.city.cmd,
                CM.grant.land.cmd,
                CM.grant.infra.cmd,
                CM.grant.build.cmd,
                CM.grant.unit.cmd,
                CM.grant.consumption.cmd,
                CM.grant.warchest.cmd
        );
    }
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
                "Add `-d` or `#decay=60d` to have a grant's debt decay linearly\n" +
                "Add `-c` to have a grant count as cash value in " + CM.deposits.check.cmd.toSlashMention() + "\n" +
                "Add `-o` to only send what funds they are missing for a grant\n" +
                "Add `-m` to multiply the grant per city\n" +
                "Use `tax_id:1234` to specify tax account\n" +
                "Use `-t` to specify receiver's tax account\n" +
                "Add `#ignore` to ignore";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        String expireStr = DiscordUtil.parseArg(args, "expire");
        if (expireStr == null) expireStr = DiscordUtil.parseArg(args, "#expire");
        Long expire = expireStr == null ? null : TimeUtil.timeToSec(expireStr) * 1000L;
        if (flags.contains('e')) {
            expire = TimeUnit.DAYS.toMillis(60);
        }
        String decayStr = DiscordUtil.parseArg(args, "decay");
        if (decayStr == null) decayStr = DiscordUtil.parseArg(args, "#decay");
        Long decay = decayStr == null ? null : TimeUtil.timeToSec(decayStr) * 1000L;
        if (flags.contains('d')) {
            decay = TimeUnit.DAYS.toMillis(60);
        }


        String escrowModeStr = DiscordUtil.parseArg(args, "escrow");
        EscrowMode escrowMode = escrowModeStr != null ? PWBindings.EscrowMode(escrowModeStr) : null;
        GuildDB guildDb = Locutus.imp().getGuildDB(guild);

        boolean ignore = false;
        DBNation nationAccount = null;
        DBAlliance allianceAccount = null;
        DBAlliance offshoreAccount = null;
        TaxBracket taxAccount = null;

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

        String taxIdStr = DiscordUtil.parseArg(args, "tax_id");
        if (taxIdStr == null) taxIdStr = DiscordUtil.parseArg(args, "bracket");
        if (taxIdStr != null) {
            taxAccount = PWBindings.bracket(guildDb, "tax_id=" + taxIdStr);
        }
        if (flags.contains('t')) {
            if (taxAccount != null) return "You can't specify both `tax_id` and `-t`";
        }


        Double factor = null;

        for (Iterator<String> iter = args.iterator(); iter.hasNext(); ) {
            String arg = iter.next().toLowerCase();
            if (arg.equalsIgnoreCase("#ignore")) {
                iter.remove();
                ignore = true;
                continue;
            }
            if (arg.startsWith("-expire") || arg.startsWith("-e") || arg.startsWith("#expire")) {
                expire = TimeUtil.timeToSec(arg.split("[:=]", 2)[1]) * 1000L;
                iter.remove();
            }
            if (arg.startsWith("-decay") || arg.startsWith("-d") || arg.startsWith("#decay")) {
                decay = TimeUtil.timeToSec(arg.split("[:=]", 2)[1]) * 1000L;
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
                    return usage(args.size(), 3, channel);
                }
            } else {
                return usage(args.size(), 3, channel);
            }
        } else {
            num = MathMan.parseDouble(args.get(2));
            if (num == null || num <= 0) return "Invalid number: `" + args.get(2) + "`";
        }
        if (num <= 0) return "Invalid positive number: " + num;

        me = DiscordUtil.parseNation(args.get(0), false);
        String typeArg = args.get(1);
        if (me == null) {
            Set<DBNation> nations = DiscordUtil.parseNations(guild, author, me, args.get(0), false, false);
            Set<Integer> requiredAAs = guildDb.getAllianceIds();
            if (!flags.contains('f')) nations.removeIf(f -> !requiredAAs.contains(f.getAlliance_id()));
            if (nations.isEmpty()) return "Invalid nation: `" + args.get(0) + "`";
            if (!Roles.ECON.has(author, guild)) return "No permission (econ)";

            SpreadSheet sheet = SpreadSheet.create(guildDb, SheetKey.GRANT_SHEET);
            sheet.updateClearCurrentTab();
            List<String> header = new ArrayList<>(Arrays.asList(
                    "nation",
                    "cities",
                    "avg_infra",
                    "response",
                    "cost_converted",
                    "cost_raw"
            ));
            sheet.setHeader(header);

            Map<ResourceType, Double> total = new Object2DoubleOpenHashMap<>();
            for (DBNation nation : nations) {
                ArrayList<Object> row = new ArrayList<>();
                row.add(MarkupUtil.sheetUrl(nation.getNation(), nation.getUrl()));
                row.add(nation.getCities());
                row.add(nation.getAvg_infra());
                try {
                    Grant grant = generateGrant(typeArg, guildDb, nation, num, flags, false, ignore);
                    row.add(grant.getInstructions());
                    row.add(ResourceType.convertedTotal(grant.cost()));
                    row.add(ResourceType.toString(grant.cost()));

                    total = ResourceType.add(total, ResourceType.resourcesToMap(grant.cost()));
                    if (factor != null) {
                        total = PW.multiply(total, factor);
                    }
                } catch (IllegalArgumentException e) {
                    row.add(e.getMessage());
                    row.add(0);
                    row.add("{}");
                }
                sheet.addRow(row);
            }

            sheet.updateWrite();

            String totalStr = ResourceType.toString(total) + " worth ~$" + MathMan.format(ResourceType.convertedTotal(total));
            sheet.attach(channel.create().append(totalStr), "grant", null, false, 0).send();
            return null;
        }

        Grant grant = generateGrant(typeArg, guildDb, me, num, flags, true, ignore);

        Member member = null;

        if (!flags.contains('f')) {
            Role noGrants = Roles.TEMP.toRole2(guild);
            PNWUser user = Locutus.imp().getDiscordDB().getUserFromNationId(me.getNation_id());
            if (user != null) {
                member = guild.getMemberById(user.getDiscordId());
//                if (member == null) {
//                    throw new IllegalArgumentException("Not on this discord");
//                }
            }
            if (noGrants != null && member != null && member.getRoles().contains(noGrants)) {
                throw new IllegalArgumentException("The member has been marked (with discord role) as to not receive grants");
            }
        }

        UUID uuid = UUID.randomUUID();
        BankCommands.AUTHORIZED_TRANSFERS.put(uuid, grant);

        Map<ResourceType, Double> resources = ResourceType.resourcesToMap(grant.cost());

        if (factor != null) {
            resources = PW.multiply(resources, factor);
        }

        JSONObject command = CM.transfer.resources.cmd.receiver(
                me.getUrl()).transfer(
                ResourceType.toString(resources)).depositType(
                grant.getType().toString()).nationAccount(
                (nationAccount == null ? me : nationAccount).getUrl()).senderAlliance(
                allianceAccount != null ? allianceAccount.getUrl() : null).allianceAccount(
                offshoreAccount != null ? offshoreAccount.getUrl() : null).taxAccount(
                taxAccount != null ? taxAccount.getQualifiedId() : null).existingTaxAccount(
                flags.contains('t') ? "true" : null).onlyMissingFunds(
                String.valueOf(flags.contains('o'))).expire(
                expire != null ? TimeUtil.secToTime(TimeUnit.MILLISECONDS, expire) : null).decay(
                decay != null ? TimeUtil.secToTime(TimeUnit.MILLISECONDS, decay) : null).token(
                uuid.toString()).convertCash(
                String.valueOf(flags.contains('c'))).escrow_mode(
                escrowMode == null ? null : escrowMode.name()).bypassChecks(
                String.valueOf(flags.contains('f'))).force(
                "false"
        ).toJson();
        StringBuilder msg = new StringBuilder();
        msg.append(ResourceType.toString(resources)).append("\n")
                .append("Current values for: " + me.getNation()).append('\n')
                .append("Cities: " + me.getCities()).append('\n')
                .append("Infra: " + me.getAvg_infra()).append('\n')
        ;


        channel.create().embed("Instruct: " + grant.title(), "**INSTRUCTIONS:** " + grant.getInstructions()).send();
        channel.create().confirmation("Confirm: " + grant.title(), msg.toString(), command).cancelButton().send();

        return null;
    }

    public Grant generateGrant(String arg, GuildDB guildDb, DBNation me, double amt, Set<Character> flags, boolean single, boolean ignore) throws IOException, ExecutionException, InterruptedException {
        Grant grant;

        boolean existing = flags.contains('o');
        boolean force = flags.contains('f');
        boolean noInfra = flags.contains('i');
        boolean noLand = flags.contains('l');

        me = me.copy();

        PNWUser user = Locutus.imp().getDiscordDB().getUserFromNationId(me.getNation_id());
        if (!force) {
            if (user == null) throw new IllegalArgumentException("No user found for nation: " + me.getNation_id());
            Guild guild = guildDb.getGuild();
            Member member = guild.getMemberById(user.getDiscordId());
            if (member == null) {
                throw new IllegalArgumentException("The user `" + DiscordUtil.getUserName(user.getDiscordId()) + "` is not in this server");
            }
        }

        Map<ResourceType, Double> resources = new Object2DoubleOpenHashMap<>();

        if (arg.equalsIgnoreCase("city")) {
            if (me.getCityTurns() > 0 && me.getCities() >= 10 && !force) throw new IllegalArgumentException("You still have a city timer");
            int currentCity = me.getCities();
            int numBuy = (int) amt;
            if (numBuy >= 10) numBuy = numBuy - currentCity;

            int maxBuy = Math.max(1, 10 - currentCity);
            if (numBuy > maxBuy && !force) throw new IllegalArgumentException("Only " + maxBuy + " cities can be granted");
            if (numBuy <= 0) throw new IllegalArgumentException("Already has " + currentCity + " cities");


            grant = new Grant(me, DepositType.CITY.withAmount(currentCity + numBuy).ignore(ignore));
            grant.setAmount(amt);
            grant.addCity(me.getCities());
            grant.setInstructions(grantCity(me, numBuy, resources, force));
        } else if (arg.equalsIgnoreCase("infra")) {
            grant = new Grant(me, DepositType.INFRA.withValue((int) amt, -1).ignore(ignore));
            grant.setAmount(amt);
            grant.setInstructions(grantInfra(me, (int) amt, resources, force, single));
            grant.setAllCities();
        } else if (arg.equalsIgnoreCase("land")) {
            grant = new Grant(me, DepositType.LAND.withValue((int) amt, -1).ignore(ignore));
            grant.setAmount((int) amt);
            grant.setInstructions(grantLand(me, (int) amt, resources, force));
            grant.setAllCities();
        } else if (arg.contains("mmrbuy=")) {
            MMRDouble mmr = MMRDouble.fromString(arg.split("=")[1]);
            grant = new Grant(me, DepositType.WARCHEST.withValue().ignore(ignore));
            grant.setAmount(amt);
            grant.setInstructions(grantMMRBuy(me, mmr, (int) amt, resources, force));
            grant.setAllCities();
        } else if (arg.contains("mmr=")) {
            MMRDouble mmr = MMRDouble.fromString(arg.split("=")[1]);
            grant = new Grant(me, DepositType.WARCHEST.withValue().ignore(ignore));
            grant.setAmount(amt);
            grant.setInstructions(grantMMR(me, mmr, (int) amt, resources, force));
            grant.setAllCities();
        } else if (arg.startsWith("{")) {
            if (arg.contains("infra_needed")) {
                JavaCity city = new JavaCity(arg);
                if (!arg.contains("land")) city.setLand(0d);

                city.validate(me.getContinent(), me::hasProject);

                long pair = MathMan.pairInt((int) city.getInfra(), (int) city.getLand());
                int citiesAmt;

                Map<Integer, JavaCity> from;
                if (amt == Double.MAX_VALUE) {
                    from = me.getCityMap(true);
                    citiesAmt = -1;
                } else if (amt == 1) {
                    from = new Int2ObjectOpenHashMap<>();
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
                grant = new Grant(me, DepositType.BUILD.withValue(pair, citiesAmt).ignore(ignore));

                for (Map.Entry<Integer, JavaCity> entry : from.entrySet()) {
                    if (noInfra) entry.getValue().setInfra(city.getInfra());
                    if (noLand) entry.getValue().setLand(city.getLand());
                }
                if (!noInfra) grant.addNote("#infra=" + city.getInfra());
                if (!noLand) grant.addNote("#land=" + city.getLand());
                double[] buffer = new double[ResourceType.values.length];
                grant.setInstructions(city.instructions(from, buffer, true, true));
                resources = ResourceType.resourcesToMap(buffer);
            } else {
                grant = new Grant(me, DepositType.GRANT.withValue().ignore(ignore));
                grant.setInstructions("transfer resources");
                resources = ResourceType.parseResources(arg);
            }
        } else if (arg.equalsIgnoreCase("build")) {
            throw new IllegalArgumentException("Usage: " + Settings.commandPrefix(true) + "grant <nation> <json> 1");
        } else if (arg.equalsIgnoreCase("warchest")) {
            if (!guildDb.isAllianceId(me.getAlliance_id())) {
                throw new IllegalArgumentException("Cannot view stockpile for " + me.getMarkdownUrl() + " as their alliance is not registered to this guild (currently: " + guildDb.getAllianceIds() + ")");
            }
            Map<ResourceType, Double> stockpile = me.getStockpile();
            if (stockpile == null) throw new IllegalArgumentException("Unable to fetch stockpile (are you sure they are a member?)");
            Map<ResourceType, Double> cityWc = guildDb.getPerCityWarchest(me);
            resources = PW.multiply(cityWc, (double) me.getCities());
            if (amt > 0 && amt != 1) {
//                Double multiplier = MathMan.parseDouble(args.get(2));
//                if (multiplier == null) return "Invalid multiplier: `" + args.get(2) + "`";
                resources = PW.multiply(resources, amt);
            }

            for (Map.Entry<ResourceType, Double> entry : stockpile.entrySet()) {
                double required = resources.getOrDefault(entry.getKey(), 0d);
                if (required > 0) {
                    required -= entry.getValue();
                    if (required <= 0) resources.remove(entry.getKey());
                    else resources.put(entry.getKey(), required);
                }
            }
            grant = new Grant(me, DepositType.WARCHEST.withValue().ignore(ignore));
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
                        max = building.cap(f -> false) * building.getUnitCap() * me.getCities();
                        break;
                    case SPIES:
                        max = Projects.INTELLIGENCE_AGENCY.get(me) > 0 ? 60 : 50;
                        break;
                    case NUKE:
                    case MISSILE:
                        if (!flags.contains('f')) throw new IllegalArgumentException("We do not approve grants for missiles/nukes");
                    case MONEY:
                    case INFRASTRUCTURE:
                        break;
                }
                if (amt + me.getUnits(unit) > max) {
                    throw new IllegalArgumentException(me.getNation() + " can only have up to " + max + " " + unit.getName());
                }

                resources = unit.getCost((int) amt, me::getResearch);
                grant = new Grant(me, DepositType.WARCHEST.withValue().ignore(ignore));
                grant.setInstructions("Go to <" + Settings.PNW_URL() + "/military/" + unit.getName() + "/> and purchase " + (int) amt + " " + unit.getName());
            } else {
                if (me.projectSlots() <= me.getNumProjects() && !flags.contains('f')) {
                    throw new IllegalArgumentException("Error: " + me.getUrl() + " has full project slots " + (me.projectSlots() + "<=" + me.getNumProjects()));
                }
                resources = project.cost();
                if (!force && ResourceType.convertedTotal(resources) > 2000000 && me.getDomesticPolicy() != DomesticPolicy.TECHNOLOGICAL_ADVANCEMENT) {
                    throw new IllegalArgumentException("Please set your Domestic Policy to `Technological Advancement` in <" + Settings.PNW_URL() + "/nation/edit/> to save 5%.");
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
                    if (me.hasProject(Projects.BUREAU_OF_DOMESTIC_AFFAIRS)) {
                        factor -= 0.0125;
                    }
                }
                if (factor != 1) {
                    resources = PW.multiply(resources, factor);
                }

                grant = new Grant(me, DepositType.PROJECT.withAmount(project.ordinal()).ignore(ignore));
                grant.setInstructions("Go to <" + Settings.PNW_URL() + "/nation/projects/> and purchase " + project.name());
            }
        }

        if (flags.contains('m')) {
            resources = PW.multiply(resources, (double) me.getCities());
        }

        if (existing) {
            if (!guildDb.isAllianceId(me.getAlliance_id())) {
                throw new IllegalArgumentException("Nation " + me.getMarkdownUrl() + " is not in an alliance registered to this guild (currently: " + guildDb.getAllianceIds() + ")");
            }
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
        grant.setCost(f -> ResourceType.resourcesToArray(finalResources));

        return grant;
    }

    public String grantMMR(DBNation me, MMRDouble mmr, int numBuys, Map<ResourceType, Double> resources, boolean force) {
        if (numBuys > 5 && !force) {
            throw new IllegalArgumentException("You cannot grant more than 5 full buys");
        }

        StringBuilder response = new StringBuilder();
        response.append("- mmr[unit]=" + me.getMMR() + "\n");
        response.append("- mmr[build]=" + me.getMMRBuildingStr() + "\n");
        response.append("- Cities: " + me.getCities() + "\n\n");
        response.append("Buy for mmr=" + mmr.toString() + " for " + numBuys + " full buys\n");

        int cities = me.getCities();
        for (MilitaryUnit unit : MilitaryUnit.values()) {
            MilitaryBuilding building = unit.getBuilding();
            if (building == null) continue;
            double numBuildings = mmr.get(unit) * cities;
            int numUnitsPerRebuy = (int) (Math.floor(building.getUnitCap() * numBuildings));
            int numUnits = numUnitsPerRebuy * numBuys;
            resources = ResourceType.addResourcesToA(resources, unit.getCost(numUnits, me::getResearch));
            response.append("- " + numUnits + " x " + unit);
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
        response.append("- mmr[unit]=" + me.getMMR() + "\n");
        response.append("- mmr[build]=" + me.getMMRBuildingStr() + "\n");
        response.append("- Cities: " + me.getCities() + "\n\n");
        response.append("Buy for mmr=" + mmr.toString() + " over " + numBuys + " days\n");

        int cities = me.getCities();
        for (MilitaryUnit unit : MilitaryUnit.values()) {
            MilitaryBuilding building = unit.getBuilding();
            if (building == null) continue;
            double numBuildings = mmr.get(unit) * cities;
            int numUnitsPerDay = (int) (Math.floor(building.getUnitDailyBuy() * numBuildings));
            int numUnits = numUnitsPerDay * numBuys;
            resources = ResourceType.addResourcesToA(resources, unit.getCost(numUnits, me::getResearch));
            response.append("- " + numUnits + " x " + unit);
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
        Map<Integer, JavaCity> myBuilds = me.getCityMap(true);

        double totalCost = 0;

        for (Map.Entry<Integer, JavaCity> entry : myBuilds.entrySet()) {
            double land = entry.getValue().getLand();
            if (land < numBuy) {
                totalCost += PW.City.Land.calculateLand((int) land, numBuy);
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
            if (me.hasProject(Projects.BUREAU_OF_DOMESTIC_AFFAIRS)) {
                factor -= 0.0125;
            }
        }
        totalCost *= factor;

        resources.put(ResourceType.MONEY, totalCost);

        StringBuilder response = new StringBuilder("Go to your cities page and enter `@" + numBuy + "` into the land field.\n" +
                Projects.ARABLE_LAND_AGENCY + ": " + ala + "\n" +
                Projects.ADVANCED_ENGINEERING_CORPS + ": " + aec + "\n" +
                DomesticPolicy.RAPID_EXPANSION + ": " + expansion + "\n" +
                Projects.GOVERNMENT_SUPPORT_AGENCY +": " + me.hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY) + "\n" +
                Projects.BUREAU_OF_DOMESTIC_AFFAIRS + ": " + me.hasProject(Projects.BUREAU_OF_DOMESTIC_AFFAIRS));

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
//            double profit = PW.convertedTotal(newProfit) * pnwNation.getCities() - PW.convertedTotal(oldProfit);
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
        Map<Integer, JavaCity> myBuilds = me.getCityMap(true);

        double totalCost = 0;

        for (Map.Entry<Integer, JavaCity> entry : myBuilds.entrySet()) {
            double infra = entry.getValue().getInfra();
            if (infra < numBuy) {
                totalCost += PW.City.Infra.calculateInfra((int) infra, numBuy);
            }
        }

        if (totalCost <= 0) return "You already have " + numBuy + " in your cities";

        boolean urbanization = me.getDomesticPolicy() == DomesticPolicy.URBANIZATION;
        boolean gsa = me.hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY);
        boolean cce = me.hasProject(Projects.CENTER_FOR_CIVIL_ENGINEERING);
        boolean aec = me.hasProject(Projects.ADVANCED_ENGINEERING_CORPS);
        boolean bda = me.hasProject(Projects.BUREAU_OF_DOMESTIC_AFFAIRS);

        if (numBuy > 1700 && !urbanization && !force) {
            throw new IllegalArgumentException("Please set Urbanization as your domestic policy");
        }

        double factor = 1;
        if (urbanization) {
            factor -= 0.05;
            if (gsa) factor -= 0.025;
            if (bda) factor -= 0.0125;
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
                Projects.GOVERNMENT_SUPPORT_AGENCY + ": " + gsa + "\n" +
                Projects.BUREAU_OF_DOMESTIC_AFFAIRS + ": " + bda);

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
//            double profit = PW.convertedTotal(newProfit) - PW.convertedTotal(oldProfit);
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
        boolean bda = me.hasProject(Projects.BUREAU_OF_DOMESTIC_AFFAIRS);

        double cost = 0;
        for (int i = currentCity; i < currentCity + numBuy; i++) {
            cost += PW.City.nextCityCost(i, manifest, cp, acp, mp, gsa, bda);
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
            result.append("Then go to <" + Settings.PNW_URL() + "/city/create/> and create your new city.");
        } else {
            result.append("Then go to <" + Settings.PNW_URL() + "/city/create/> and buy " + numBuy + " new cities.");
        }
        return result.toString();
    }
}