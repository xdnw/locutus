package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import com.google.common.base.Predicates;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.TextArea;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.CommandRuntimeServices;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.stock.StockDB;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.BaseballDB;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.ForumDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.ReportManager;
import link.locutus.discord.db.TradeDB;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBBan;
import link.locutus.discord.db.entities.DBBounty;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTreasure;
import link.locutus.discord.db.entities.LoanManager;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.event.Event;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.scheduler.ThrowingFunction;
import link.locutus.discord.util.trade.TradeManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * App-only bindings that intentionally expose raw runtime services back into command parsing.
 * Composition roots opt into these explicitly; the neutral default store does not register them.
 */
public class PWAppBindings extends BindingHelper {

    @Binding
    public WarDB warDB(CommandRuntimeServices services) {
        return services.warDb();
    }

    @Binding
    public NationDB nationDB(CommandRuntimeServices services) {
        return services.nationDb();
    }

    @Binding
    public BankDB bankDB(CommandRuntimeServices services) {
        return services.bankDb();
    }

    @Binding
    public StockDB stockDB(CommandRuntimeServices services) {
        return services.stockDb();
    }

    @Binding
    public BaseballDB baseballDB(CommandRuntimeServices services) {
        if (Settings.INSTANCE.TASKS.BASEBALL_SECONDS <= 0) {
            throw new IllegalStateException("Baseball is not enabled");
        }
        return services.baseballDb();
    }

    @Binding
    public ForumDB forumDB(CommandRuntimeServices services) {
        return services.forumDb();
    }

    @Binding
    public DiscordDB discordDB(CommandRuntimeServices services) {
        return services.discordDb();
    }

    @Binding
    public ReportManager ReportManager(CommandRuntimeServices services) {
        return services.reportManager();
    }

    @Binding
    public LoanManager loanManager(CommandRuntimeServices services) {
        return services.loanManager();
    }

    @Binding(examples = "647252780817448972", value = "A discord guild id. See: <https://en.wikipedia.org/wiki/Template:Discord_server#Getting_Guild_ID>")
    public GuildDB guildDb(CommandRuntimeServices services, long guildId) {
        return PWBindings.resolveGuildDb(services, guildId);
    }

    @Binding(examples = "647252780817448972", value = "A discord guild id. See: <https://en.wikipedia.org/wiki/Template:Discord_server#Getting_Guild_ID>")
    public Guild guild(CommandRuntimeServices services, long guildId) {
        return PWBindings.resolveGuild(services, guildId);
    }

    @Binding
    public ExecutorService executor(CommandRuntimeServices services) {
        return services.executor();
    }

    @Binding
    public TradeManager tradeManager(CommandRuntimeServices services) {
        return services.tradeManager();
    }

    @Binding
    public TradeDB tradeDB(CommandRuntimeServices services) {
        return services.tradeDb();
    }

    @Binding(value = "A named game treasure")
    public DBTreasure treasure(CommandRuntimeServices services, String input) {
        return parseTreasure(services.nationDb(), input);
    }

    @Binding
    public DBBounty bounty(CommandRuntimeServices services, String input) {
        return PWBindings.parseBounty(services.warDb(), input);
    }

    @Binding
    public DBBan ban(CommandRuntimeServices services, @Me @Default Guild guild, String input) {
        return parseBan(services, guild, input);
    }

    @Binding(value = "City build json or url", examples = { "city/id=371923", "{city-json}",
            "city/id=1{json-modifiers}" })
    public CityBuild city(CommandRuntimeServices services, NationDB nationDb, @Default @Me DBNation nation,
            @TextArea String input) {
        int index = input.indexOf('{');
        Integer cityId = null;
        CityBuild build = null;

        String json;
        if (index == -1) {
            json = null;
        } else {
            if (input.startsWith("{city")) {
                // Support modifiers relative to an existing city in the current nation.
                index = input.indexOf('}');
                if (index == -1) {
                    throw new IllegalArgumentException("No closing bracket found");
                }
                int cityIndex = input.contains(" ") ? Integer.parseInt(input.substring(6, index)) - 1 : 0;
                Set<Map.Entry<Integer, JavaCity>> cities = nation.getCityMap(true, false).entrySet();
                int i = 0;
                for (Map.Entry<Integer, JavaCity> entry : cities) {
                    if (++i == cityIndex) {
                        CityBuild other = entry.getValue().toCityBuild();
                        other.setCity_id(entry.getKey());
                        build = other;
                        break;
                    }
                }
                if (build == null) {
                    throw new IllegalArgumentException("City not found: " + index + " for natiion " + nation.getName());
                }
            }
            json = input.substring(index);
            input = input.substring(0, index);
        }
        if (build == null && input.contains("city/id=")) {
            cityId = Integer.parseInt(input.split("=")[1]);
            DBCity cityEntry = nationDb.getCitiesV3ByCityId(cityId);
            if (cityEntry == null) {
                final int finalCityId = cityId;
                cityEntry = returnEventsAsync(services.executor(), f -> nationDb.getCitiesV3ByCityId(finalCityId, true, f));
                if (cityEntry == null) {
                    throw new IllegalArgumentException(
                            "No city found in cache with id " + cityId + " (expecting city id or url)");
                }
            }
            int nationId = cityEntry.getNationId();
            DBNation nation2 = services.lookup().getNationById(nationId);
            if (nation2 != null) {
                nation = nation2;
                nationDb.markCityDirty(nationId, cityEntry.getId(), System.currentTimeMillis());
            }
            build = cityEntry.toJavaCity(nation == null ? Predicates.alwaysFalse() : nation::hasProject).toCityBuild();
            build.setCity_id(cityEntry.getId());
        }
        if (json == null) {
            json = "";
        } else {
            json = json.trim();
        }
        if (!json.isBlank() && json.startsWith("{") && json.endsWith("}")) {
            for (Building building : Buildings.values()) {
                json = json.replace(building.name(), building.nameSnakeCase());
            }
            CityBuild build2 = CityBuild.of(json, true);
            if (build != null) {
                json = build2.toString().replace("}", "") + "," + build.toString().replace("{", "");
                build = CityBuild.of(json, true);
            } else {
                build = build2;
            }
        }
        if (build != null && cityId != null) {
            build.setCity_id(cityId);
        }
        if (build == null) {
            throw new IllegalArgumentException("Invalid city build: `" + input
                    + "`. Please use a valid CITY id (not nation id), city url, or valid city json.");
        }
        return build;
    }

    @Binding
    public AllianceList allianceList(CommandRuntimeServices services, ParameterData param,
            @Default @Me User user, @Me GuildDB db) {
        AllianceList list = db.getAllianceList();
        if (list == null) {
            throw new IllegalArgumentException("This guild has no registered alliance. See "
                    + CM.settings.info.cmd.toSlashMention()
                    + " with key `" + GuildKey.ALLIANCE_ID.name() + "`");
        }
        RolePermission perms = param.getAnnotation(RolePermission.class);
        if (perms == null) {
            throw new IllegalArgumentException(
                    "TODO: disable this error once i verify it works (see console for debug info)");
        }
        if (user == null) {
            throw new IllegalArgumentException("Not registered");
        }

        Set<Integer> allowedIds = new IntLinkedOpenHashSet();
        for (int aaId : list.getIds()) {
            try {
                PermissionBinding.checkRole(db.getGuild(), perms, user, aaId, services);
                allowedIds.add(aaId);
            } catch (IllegalArgumentException ignore) {
            }
        }
        if (allowedIds.isEmpty()) {
            throw new IllegalArgumentException("You are lacking role permissions for the alliance ids: "
                    + StringMan.getString(list.getIds()));
        }
        return new AllianceList(allowedIds);
    }

    private static <T> T returnEventsAsync(ExecutorService executor,
            ThrowingFunction<Consumer<Event>, T> eventHandler) {
        Collection<Event> events = new ObjectArrayList<>(0);
        T result = eventHandler.apply(events::add);
        if (!events.isEmpty()) {
            executor.submit(() -> {
                for (Event event : events) {
                    event.post();
                }
            });
        }
        return result;
    }

    private static DBTreasure parseTreasure(NationDB nationDb, String input) {
        Map<String, DBTreasure> treasures = nationDb.getTreasuresByName();
        DBTreasure treasure = treasures.get(input.toLowerCase(Locale.ROOT));
        if (treasure == null) {
            throw new IllegalArgumentException(
                    "No treasure found with name: `" + input + "`. Options " + StringMan.getString(treasures.keySet()));
        }
        return treasure;
    }

    private static DBBan parseBan(CommandRuntimeServices services, Guild guild, String input) {
        if (MathMan.isInteger(input)) {
            DBBan ban = services.nationDb().getBanById(Integer.parseInt(input));
            if (ban == null) {
                throw new IllegalArgumentException("No ban found for id `" + input + "`");
            }
            return ban;
        }
        DBNation nation = PWBindings.parseNation(services, null, guild, input, null);
        List<DBBan> bans = services.nationDb().getBansForNation(nation.getId());
        if (bans.isEmpty()) {
            throw new IllegalArgumentException("No bans found for nation `" + nation.getName() + "`");
        }
        return bans.get(0);
    }
}
