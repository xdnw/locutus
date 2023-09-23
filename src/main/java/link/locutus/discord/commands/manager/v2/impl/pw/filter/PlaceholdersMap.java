package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public class PlaceholdersMap {
    private final Map<Class<?>, Placeholders<?>> placeholders = new ConcurrentHashMap<>();
    private final ValueStore store;
    private final ValidatorStore validators;
    private final PermissionHandler permisser;

    public PlaceholdersMap(ValueStore store, ValidatorStore validators, PermissionHandler permisser) {
        this.store = store;
        this.validators = validators;
        this.permisser = permisser;

        this.placeholders.put(DBNation.class, new NationPlaceholders(store, validators, permisser));
        this.placeholders.put(DBAlliance.class, new AlliancePlaceholders(store, validators, permisser));
        this.placeholders.put(Continent.class, createContinents());
        this.placeholders.put(GuildDB.class, createGuildDB());
        //- Projects
        this.placeholders.put(Project.class, createProjects());
        //- Treaty
        // - *, alliances
        //- Bans
        // - *, nation, user mention
        //- Cities
        // - *, nations
        //- Tax records
        // - * (within aa)
        //- Treasure
        //- Bounties
        //- Color bloc
        // -TaxBracket
        //- resource type
        //- attack type
        //- military unit
        //- treaty type
        //- building
        //-GuildKey
        //-AllianceMeta
        // NationList
        //-Coalition (wrapper)
        //  - Make Coalition wrapper instead of Coalition for PWBindings
        //  - Coalition wrapper extends NationList
        //-GrantTemplate
        //- Discord Channel (wrapper) - only current server
        // nation
        // //-AuditType
        //- Attacks
        //- Trades
        //- Bank recs (only allow aa→ aa if guild db has access)
        // - *, with filters: sender, receiver, banker
    }

    public <T> Placeholders<T> get(Class<T> type) {
        return (Placeholders<T>) this.placeholders.get(type);
    }

    private Placeholders<Continent> createContinents() {
        return Placeholders.create(Continent.class, store, validators, permisser,
                "TODO CM REF",
                (store, input) -> PWBindings.continentTypes(input));
    }

    private Placeholders<GuildDB> createGuildDB() {
        return Placeholders.create(GuildDB.class, store, validators, permisser,
                "TODO CM Ref",
            (store, input) -> {
                User user = (User) store.getProvided(Key.of(User.class, Me.class), true);
                boolean admin = Roles.ADMIN.hasOnRoot(user);
                if (input.equalsIgnoreCase("*")) {
                    if (admin) {
                        return new HashSet<>(Locutus.imp().getGuildDatabases().values());
                    }
                    List<Guild> guilds = user.getMutualGuilds();
                    Set<GuildDB> dbs = new HashSet<>();
                    for (Guild guild : guilds) {
                        GuildDB db = Locutus.imp().getGuildDB(guild);
                        if (db != null) {
                            dbs.add(db);
                        }
                    }
                    return dbs;
                }
                if (SpreadSheet.isSheet(input)) {
                    return SpreadSheet.parseSheet(input, List.of("guild"), true,
                            (type, str) -> PWBindings.guild(PrimitiveBindings.Long(str)));
                }
                long id = PrimitiveBindings.Long(input);
                GuildDB guild = PWBindings.guild(id);
                if (!admin && guild.getGuild().getMember(user) == null) {
                    throw new IllegalArgumentException("You (" + user + ") are not in the guild with id: `" + id + "`");
                }
                return Collections.singleton(guild);
            });
    }

    private Placeholders<Project> createProjects() {
        return Placeholders.create(Project.class, store, validators, permisser,
                "TODO CM REF",
                (store, input) -> PWBindings.projects(input));
    }

}
