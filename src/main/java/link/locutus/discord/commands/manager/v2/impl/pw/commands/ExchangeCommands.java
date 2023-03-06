package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.annotation.TextArea;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.stock.Exchange;
import link.locutus.discord.commands.stock.ExchangeCategory;
import link.locutus.discord.commands.stock.StockDB;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationOrExchange;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.*;
import org.json.JSONObject;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExchangeCommands {
    @Command(desc = "Create an exchange.")
    @RolePermission(guild = StockDB.ROOT_GUILD)
    public String create(@Me Guild guild, @Me User user, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command, StockDB db, @Me DBNation nation, ExchangeCategory category, String symbol, @Switch("f") boolean force) {
        if (!symbol.matches("[a-zA-Z0-9_]+")) return "`" + symbol + "` does not match (letters, numbers, underscores)";
        if (MathMan.isInteger(symbol)) return "The exchange symbol `" + symbol + "` must contain at least one letter";
        Exchange exchange = db.getExchange(symbol);
        if (exchange != null) return "An exchange by that name already exists: " + exchange.symbol;


        long owner = guild.getOwnerIdLong();
        if (owner != user.getIdLong()) {
            return "You cannot register a company for `" + guild.getName() + "` as you lack discord ownership.\n Be sure ot run the command in the correct server.";
        }

        List<Exchange> exchanges = db.getExchangesByOwner(me.getNation_id());
        if (exchanges.size() >= 4)
            return "You already have 4 corporations. Please contact us if you wish to create more.";

        exchange = new Exchange(category, symbol, "", nation.getNation_id(), guild.getIdLong());
        exchange.name = guild.getName();

        if (!force) {
            io.create().confirmation("Create: " + symbol, "Create exchange: `" + symbol + "`", command).send();
            return null;
        }

        db.addExchange(exchange);
        exchange.autoRole();
        exchange.createRoles();

        GuildMessageChannel exchangeChannel = exchange.getChannel();
        RateLimitUtil.queue(exchangeChannel.sendMessage("Company info: `" + Settings.commandPrefix(false) + "exchange description <info>`\n" +
                "Company name: `" + Settings.commandPrefix(false) + "exchange name <name>`\n" +
                "Officers: `" + Settings.commandPrefix(false) + "exchange promote <user> <MEMBER|OFFICER|HEIR|LEADER>`\n" +
                "Charter: `" + Settings.commandPrefix(false) + "exchange charter <doc-url>`\n" +
                "Website: `" + Settings.commandPrefix(false) + "exchange website <url>`\n" +
                "Color Roles: `" + Settings.commandPrefix(false) + "exchange color <rank> <color>`\n"
        ));

        String help = "Created exchange: `" + symbol.toUpperCase() + "`. To have it listed on the exchange please complete the following:\n" + " - Join " + StockDB.INVITE + "\n" +
                " - Visit your channel and read the setup documentation: " + exchangeChannel.getAsMention() + "\n";
        return help;
    }

    @Command(desc = "Autorole members for an exchange")
    @RolePermission(guild = StockDB.ROOT_GUILD)
    public String autoRole(@Me GuildDB db, @Me Guild guildl, @Me Member member, @Me DBNation me, StockDB stockDB, @Me Exchange exchange) {
        exchange.autoRole(member);
        exchange.autoRole();
        StringBuilder response = new StringBuilder();
        db.getAutoRoleTask().autoRole(member, f -> response.append(f).append("\n"));
        return response.toString().trim();
    }

    @Command(desc = "Destroy an exchange")
    @RolePermission(guild = StockDB.ROOT_GUILD)
    public String drop(@Me Guild guild, @Me IMessageIO io, @Me DBNation me, @Me JSONObject command, StockDB db, @Me Exchange exchange, @Switch("f") boolean force) {
        if (!exchange.checkPermission(me, Rank.LEADER)) return "You are not the leader of: " + exchange.name;
        if (!force) {
            io.create().confirmation("Delete: " + exchange.symbol, exchange.toString(), command).send();
            return null;
        }
        db.deleteExchange(exchange);
        TextChannel tc = exchange.getChannel();
        if (tc != null) {
            RateLimitUtil.queue(tc.delete());
        }
        return "Deleted exchange: " + exchange.symbol;
    }

    @Command(desc = "Transfer funds from this corp to the accounts of a nation or another corp.")
    @RolePermission(guild = StockDB.ROOT_GUILD)
    public String transfer(@Me IMessageIO io, @Me JSONObject command, StockDB db, @Me DBNation me, @Me Exchange exchange, NationOrExchange receiver, Exchange resource, double amount, @Switch("f") boolean force) {
        if (!force) {
            String title = "Confirm transfer: " + MathMan.format(amount) + "x" + resource.name;
            String body = "From `*" + exchange.getName() + "` to " + receiver.getUrlMarkup();
            io.create().confirmation(title, body, command).send();
            return null;
        }
        // TODO
        Map.Entry<Boolean, String> result = new NationOrExchange(exchange).give(me, receiver, resource, amount, false);
        return result.getValue();
    }

    public String withdraw(StockDB db, @Me DBNation me, @Me Exchange exchange, NationOrExchange receiver, Exchange resource, double amount, @Switch("f") boolean force) {
        return null; // TODO
    }

    @Command(desc = "Deposit your funds into an exchange.")
    @RolePermission(guild = StockDB.ROOT_GUILD)
    public String deposit(@Me IMessageIO io, @Me JSONObject command, StockDB db, @Me DBNation me, @Me Exchange exchange, Exchange resource, double amount, @Switch("f") boolean force) {
        NationOrExchange receiver = new NationOrExchange(exchange);
        if (!force) {
            String title = "Confirm transfer: " + MathMan.format(amount) + "x" + resource.name;
            String body = "From `*" + exchange.getName() + "` to " + receiver.getUrlMarkup();
            io.create().confirmation(title, body, command).send();
            return null;
        }
        Map.Entry<Boolean, String> result = new NationOrExchange(me).give(me, receiver, resource, amount, false);
        return result.getValue();
    }

    @Command(desc = "Export shares to a spreadsheet")
    @RolePermission(guild = StockDB.ROOT_GUILD)
    public String exportShares(StockDB db, @Me DBNation me, @Me Exchange exchange, SpreadSheet sheet) {
        synchronized (db) {
            return null; // TODO
        }
    }

    @Command(desc = "Import shares from a spreadsheet")
    @RolePermission(guild = StockDB.ROOT_GUILD)
    public String importShares(StockDB db, @Me DBNation me, @Me Exchange exchange, SpreadSheet sheet) {
        // publicly traded companies, only Root admin can import shares
        // private companies, heir can import shares
        synchronized (db) {
            return null; // TODO
        }
    }

    @Command(desc = "Bulk transfer shares/resources from a corp to nation/corp balances")
    @RolePermission(guild = StockDB.ROOT_GUILD)
    public String transferBulk(StockDB db, @Me DBNation me, @Me Exchange exchange, SpreadSheet sheet) {
        synchronized (db) {
            return null; // TODO
        }
    }

    @Command(desc = "Bulk withdraw resources from a corp to nations/alliances ingame")
    @RolePermission(guild = StockDB.ROOT_GUILD)
    public String withdrawBulk(StockDB db, @Me DBNation me, @Me Exchange exchange, SpreadSheet sheet) {
        synchronized (db) {
            return null; // TODO
        }
    }

    @Command(desc = "Disburse raw resources to nations")
    @RolePermission(guild = StockDB.ROOT_GUILD)
    public String disburse(StockDB db, @Me DBNation me, @Me Exchange exchange, Set<DBNation> nations, int days) {
        synchronized (db) {
            return null; // TODO
        }
    }

    @Command(desc = "Bulk transfer shares/resources from a corp to nations")
    @RolePermission(guild = StockDB.ROOT_GUILD)
    public String dividends(StockDB db, @Me DBNation me, @Me Exchange exchange, double valuePerShare, @Default("true") boolean sendInactive, @Default("true") boolean sendGray) {
        synchronized (db) {
            return null; // TODO
        }
    }

    @Command(desc = "Set exchange description")
    @RolePermission(guild = StockDB.ROOT_GUILD)
    public String description(StockDB db, @Me DBNation me, @Me Exchange exchange, @TextArea String description) {
        if (!exchange.checkPermission(me, Rank.OFFICER)) return "You are not the officer of: " + exchange.name;

        exchange.description = description;
        db.addExchangeWithId(exchange);

        return "Set company description to: ```" + description + "```";
    }

    @Command(desc = "Set exchange name")
    @RolePermission(guild = StockDB.ROOT_GUILD)
    public String name(StockDB db, @Me DBNation me, @Me Exchange exchange, String name) {
        if (!exchange.checkPermission(me, Rank.OFFICER)) return "You are not the officer of: " + exchange.name;
        exchange.name = name;
        db.addExchangeWithId(exchange);
        return "Set company name to: " + name;
    }

    @Command(desc = "Transfer exchange ownership")
    @RolePermission(guild = StockDB.ROOT_GUILD)
    public String owner(@Me IMessageIO io, @Me JSONObject command, StockDB db, @Me DBNation me, @Me Exchange exchange, DBNation newOwner, @Switch("f") boolean force) {
        if (!exchange.checkPermission(me, Rank.LEADER)) return "You are not the leader of: " + exchange.name;
        User user = newOwner.getUser();
        if (user == null) return newOwner.getNation() + " has not used " + CM.register.cmd.toSlashMention() + "";

        if (!force) {
            String title = "Transfer ownership to: " + newOwner.getNation() + " | " + newOwner.getAllianceName();
            String desc = "User: " + user.getAsMention() + "\nPress to confirm";
            io.create().confirmation(title, desc, command).send();
            return null;
        }

        exchange.owner = newOwner.getNation_id();
        db.addExchangeWithId(exchange);
        return "Transferred company ownership to: " + newOwner.getNation();
    }

    @Command(desc = "Set exchange guild/invite", aliases = {"guild", "invite"})
    @RolePermission(guild = StockDB.ROOT_GUILD)
    public String guild(@Me Guild guild, @Me User user, @Me TextChannel channel, StockDB db, @Me DBNation me, Exchange exchange) {
        if (!exchange.checkPermission(me, Rank.HEIR)) return "You are not the heir of: " + exchange.name;
        long owner = guild.getOwnerIdLong();
        if (owner != user.getIdLong()) {
            return "You cannot register a company for `" + guild.getName() + "` as you lack discord ownership.\nBe sure to ran the command in the correct server.";
        }
        {
            boolean hasInvite = false;
            List<Invite> invites = RateLimitUtil.complete(guild.retrieveInvites());
            for (Invite invite : invites) {
                if (invite.getMaxUses() == 0) {
                    hasInvite = true;
                    break;
                }
            }
            if (!hasInvite) {
                Invite invite = RateLimitUtil.complete((channel).createInvite().setUnique(false).setMaxAge(Integer.MAX_VALUE).setMaxUses(0));
                if (invite == null) {
                    return "Could not create a invite.";
                }
            }
        }

        exchange.setGuildId(guild.getIdLong());

        db.addExchangeWithId(exchange);
        return "Set company guild to: " + guild.getName();
    }

    @Command(desc = "Promote or demote", aliases = {"demote", "setrank"})
    @RolePermission(guild = StockDB.ROOT_GUILD)
    public String promote(StockDB db, @Me DBNation me, @Me Exchange exchange, DBNation user, Rank rank) {
        if (!exchange.checkPermission(me, Rank.OFFICER)) return "You are not an officer of: " + exchange.name;
        if (!exchange.checkPermission(me, rank)) return "You can not promote someone higher than you.";
        Rank userRank = exchange.getRank(user);
        if (!exchange.checkPermission(me, userRank)) return "You can not modify the rank of someone higher than you.";
        if (rank == Rank.REMOVE) {
            exchange.removeOfficer(user.getNation_id());
            return "Removed " + user.getNation() + " from " + exchange.name;
        } else {
            exchange.addOfficer(user, rank);

            String type = rank.id >= userRank.id ? "Promoted" : "Demoted";

            String note = "";
            if (exchange.isAlliance()) {
                note = "\nNote: This overrides ";
            }
            return type + " " + user.getName() + " | " + user.getAllianceName() + " to " + rank + note;
        }
    }

    @Command(desc = "Set company charter.")
    @RolePermission(guild = StockDB.ROOT_GUILD)
    public String charter(StockDB db, @Me DBNation me, @Me Exchange exchange, String charter) {
        if (!exchange.checkPermission(me, Rank.OFFICER)) return "You are not the officer of: " + exchange.name;
        if (!exchange.charter.startsWith("https://docs.google.com/"))
            return "Not a valid google docs link: `" + charter + "`";
        exchange.charter = charter;
        db.addExchangeWithId(exchange);
        return "Set charter to: " + charter;
    }

    @Command(desc = "Set company website", aliases = {"demote", "setrank"})
    @RolePermission(guild = StockDB.ROOT_GUILD)
    public String website(StockDB db, @Me DBNation me, @Me Exchange exchange, String website) {
        if (!exchange.checkPermission(me, Rank.OFFICER)) return "You are not the officer of: " + exchange.name;
        exchange.charter = website;
        db.addExchangeWithId(exchange);
        return "Set website to: " + website;
    }

    @Command(desc = "Add stock to a nation/corp")
    @RolePermission(guild = StockDB.ROOT_GUILD)
    public String addStock(@Me User user, @Me DBNation me, StockDB db, NationOrExchange nation, @Me Exchange exchange, @Range(min = 0.01) double quantity) {
        if (exchange.getRank(me).id < Rank.HEIR.id && !Roles.ECON.hasOnRoot(user))
            return "You are not the heir of: " + exchange.name;
        if (exchange.id == ResourceType.CREDITS.ordinal()) throw new IllegalArgumentException("Cannot add credits");

        long existing = db.getSharesByNation(nation.getId(), exchange.id);
        long amtLong = (long) (quantity * 100);
        if (db.addShares(nation.getId(), exchange.id, amtLong)) {
            db.updateCompanyTotalShares(exchange.id);
            long current = db.getSharesByNation(nation.getId(), exchange.id);
            return "Added " + MathMan.format(quantity) + "x " + exchange.symbol + " to " + nation.getName() + ". (" + MathMan.format(existing / 100d) + " -> " + MathMan.format(current / 100d) + ")";
        } else {
            return "Could not create " + MathMan.format(quantity) + "x " + exchange.symbol;
        }
    }

    @Command(desc = "Add a resource to a nation (admin)")
    @RolePermission(value = {Roles.ECON}, guild = StockDB.ROOT_GUILD)
    public String addResources(StockDB db, DBNation nation, Map<ResourceType, Double> resources) {
        if (resources.isEmpty()) return "No resources specified.";
        for (Map.Entry<ResourceType, Double> entry : resources.entrySet()) {
            if (entry.getKey() == ResourceType.CREDITS) throw new IllegalArgumentException("Can not add credits.");
        }
        Map<ResourceType, Double> added = new HashMap<>();
        Map<ResourceType, Double> failed = new HashMap<>();
        for (Map.Entry<ResourceType, Double> entry : resources.entrySet()) {
            ResourceType resource = entry.getKey();
            long amount = (long) (entry.getValue() * 100L);
            if (amount == 0) continue;
            if (db.addShares(nation.getNation_id(), resource.ordinal(), amount)) {
                added.put(resource, amount / 100d);
            } else {
                failed.put(resource, amount / 100d);
            }
        }
        StringBuilder response = new StringBuilder();
        if (!added.isEmpty()) {
            response.append("Added: ").append(PnwUtil.resourcesToString(added)).append("\n");
        }
        if (!failed.isEmpty()) {
            response.append("Failed: ").append(PnwUtil.resourcesToString(failed)).append("\n");
        }
        return response.toString().trim();
    }

    @Command(desc = "Add stock to a nation")
    @RolePermission(guild = StockDB.ROOT_GUILD)
    public String color(@Me User user, @Me DBNation me, StockDB db, @Me Exchange exchange, Rank rank, Color color) {
        if (exchange.getRank(me).id < Rank.HEIR.id && !Roles.ECON.hasOnRoot(user))
            return "You are not the heir of: " + exchange.name;

        Map<Rank, Role> roles = exchange.getCompanyRoles();
        Role role = roles.get(rank);
        if (role == null)
            return "No role found for: `" + rank + "`. Valid roles are: " + StringMan.getString(roles.keySet());
        role.getManager().setColor(color).complete();
        return "Set " + role.getName() + " to " + color;

    }
}