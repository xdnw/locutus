package link.locutus.discord.db.guild;

import com.google.gson.reflect.TypeToken;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public abstract class GuildSetting<T> {
    private final Set<GuildSetting> requires = new LinkedHashSet<>();
    private final Set<Coalition> requiresCoalition = new LinkedHashSet<>();

    private final Set<BiPredicate<GuildDB, Boolean>> requiresFunction = new LinkedHashSet<>();

    private final Map<Roles, Boolean> requiresRole = new LinkedHashMap<>();

    private final Key type;
    private final GuildSettingCategory category;
    private String name;

    public GuildSetting(GuildSettingCategory category, Type a, Type... subArgs) {
        this(category, TypeToken.getParameterized(a, subArgs).getType());
    }

    public GuildSetting(GuildSettingCategory category, Type t) {
        this(category, Key.of(t));
    }
    public GuildSetting(GuildSettingCategory category, Class t) {
        this(category, Key.of(t));
    }
    public GuildSetting(GuildSettingCategory category, Key type) {
        this.category = category;
        this.type = type;
        if (type == null) {
            try {
                if (getClass().getMethod("myMethod", GuildDB.class, String.class).getDeclaringClass() != getClass()) {
                    throw new IllegalStateException("Type is null for " + getClass().getSimpleName() + " and no parse method found");
                }
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Key getType() {
        return type;
    }

    public ValueStore getStore() {
        return Locutus.imp().getCommandManager().getV2().getStore();
    }

    public abstract String help();

    public abstract String toString(T value);

    public GuildSetting<T> setName(String name) {
        this.name = name;
        return this;
    }

    public GuildSetting<T> requires(GuildSetting other) {
        requires.add(other);
        return this;
    }

    public GuildSetting<T> requiresCoalition(Coalition coalition) {
        requiresCoalition.add(coalition);
        return this;
    }

    public T validate(GuildDB db, T value) {
        return value;
    }

    public String toReadableString(T value) {
        return toString(value);
    }

    public String getCommandObj(T value) {
        return getCommand(toString(value));
    }

    public String getCommand(String value) {
        return CM.settings.cmd.create(name(), value, null, null).toSlashCommand();
    }

    public String set(GuildDB db, T value) {
        String readableStr = toReadableString(value);
        db.setInfo(this, value);
        return "Set `" + name() + "` to `" + readableStr + "`";
    }

    public T parse(GuildDB db, String input) {
        if (type == null) throw new IllegalStateException("Type is null for " + getClass().getSimpleName());
        LocalValueStore locals = new LocalValueStore<>(getStore());
        locals.addProvider(Key.of(GuildDB.class, Me.class), db);
        return (T) locals.get(type).apply(locals, input);
    }

    public String toString() {
        return name() + "\n> " + help() + "\n";
    }

    public String name() {
        if (name == null) {
            throw new IllegalStateException("Name is null for " + getClass().getSimpleName());
        }
        return name;
    }

    public boolean hasPermission(GuildDB db, User author, T value) {
        return Roles.ADMIN.has(author, db.getGuild());
    }


    public T getOrNull(GuildDB db) {
        return getOrNull(db, true);
    }

    public T getOrNull(GuildDB db, boolean allowDelegate) {
        return db.getOrNull(this, allowDelegate);
    }

    public T get(GuildDB db) {
        return get(db, true);
    }

    public T get(GuildDB db, boolean allowDelegate) {
        return db.getOrThrow(this, allowDelegate);
    }

    public boolean allowed(GuildDB db) {
        return allowed(db, false);
    }

    public boolean allowed(GuildDB db, boolean throwException) {
        for (GuildSetting require : requires) {
            if (require.getOrNull(db) == null) {
                if (throwException) {
                    throw new IllegalStateException("Missing required setting " + require.name() + " (see: " + require.getCommandObj((String) null) + ")");
                } else {
                    return false;
                }
            }
        }
        for (Coalition coalition : requiresCoalition) {
            if (db.getCoalition(coalition).isEmpty()) {
                if (throwException) {
                    throw new IllegalStateException("Missing required coalition " + coalition.name() + " (see: " + CM.coalition.create.cmd.create(null, coalition.name()).toSlashCommand() + ")");
                } else {
                    return false;
                }
            }
        }

        for (Map.Entry<Roles, Boolean> entry : requiresRole.entrySet()) {
            Roles role = entry.getKey();
            boolean allowAARole = entry.getValue();
            Map<Long, Role> roleMap = db.getRoleMap(role);
            if (roleMap.isEmpty() || (!allowAARole && !roleMap.containsKey(0L))) {
                if (throwException) {
                    throw new IllegalStateException("Missing required role " + role.name() + " (see: " + CM.role.setAlias.cmd.create(role.name(), null, null, null).toSlashCommand() + ")");
                } else {
                    return false;
                }
            }
        }

        for (BiPredicate<GuildDB, Boolean> predicate : requiresFunction) {
            try {
                if (!predicate.test(db, throwException)) {
                    return false;
                }
            } catch (IllegalArgumentException e) {
                if (throwException) throw e;
                return false;
            }
        }

        return true;
    }

    public GuildSetting<T> requiresOffshore() {
        this.requiresFunction.add((db, throwError) -> {
            if (db.getOffshoreDB() == null) {
                throw new IllegalArgumentException("No bank is setup (see: " + CM.offshore.add.cmd.toSlashCommand() + ")");
            }
            return true;
        });
        return this;
    }

    public GuildSetting<T> requiresRole(Roles role, boolean allowAllianceRole) {
        this.requiresRole.put(role, allowAllianceRole);
        return this;
    }

    public GuildSetting<T> requiresWhitelisted() {
        this.requiresFunction.add(new BiPredicate<GuildDB, Boolean>() {
            @Override
            public boolean test(GuildDB db, Boolean throwError) {
                if (!db.isWhitelisted()) {
                    throw new IllegalArgumentException("This guild is not whitelisted by the bot developer (this feature may not be ready for public use yet)");
                }
                return true;
            }
        });
        return this;
    }

    public GuildSetting<T> nonPublic() {
        this.requiresFunction.add(new BiPredicate<GuildDB, Boolean>() {
            @Override
            public boolean test(GuildDB db, Boolean throwError) {
                throw new IllegalArgumentException("Please use the public channels for this (this is to reduce unnecessary discord calls)");
            }
        });
        return this;
    }

    public static MessageChannel validateChannel(GuildDB db, MessageChannel channel) {
        MessageChannel original = channel;
        channel = DiscordUtil.getChannel(db.getGuild(), channel.getId());
        if (channel == null) {
            throw new IllegalArgumentException("Channel " + original + " not found (are you sure it is in this server?)");
        }
        if (channel.getType() != ChannelType.TEXT) {
            throw new IllegalArgumentException("Channel " + channel.getAsMention() + " is not a text channel");
        }
        if (!channel.canTalk()) {
            throw new IllegalArgumentException("Bot does not have permission to talk in " + channel.getAsMention());
        }
        return channel;
    }

    public static Category validateCategory(GuildDB db, Category category) {
        Category original = category;
        category = DiscordUtil.getCategory(db.getGuild(), category.getId());
        if (category == null) {
            throw new IllegalArgumentException("Category " + original + " not found (are you sure it is in this server?)");
        }
        if (category.getType() != ChannelType.CATEGORY) {
            throw new IllegalArgumentException("Channel " + category.getAsMention() + " is not a category");
        }
        return category;
    }

    public GuildSetting<T> requireValidAlliance() {
        requiresFunction.add((db, throwError) -> {
            if (!db.isValidAlliance()) {
                throw new IllegalArgumentException("No valid alliance is setup (see: " + CM.settings.cmd.create(GuildSettings.Key.ALLIANCE_ID.name(), null, null, null) + ")");
            }
            return true;
        });
        return this;
    }

    public GuildSetting<T> requiresNot(GuildSetting setting) {
        requiresFunction.add((db, throwError) -> {
            if (setting.getOrNull(db) != null) {
                throw new IllegalArgumentException("Cannot be used with " + setting.name() + " set. Unset via " + CM.settings.cmd.toSlashMention());
            }
            return true;
        });
        return this;
    }

    public GuildSetting<T> requireFunction(Consumer<GuildDB> predicate) {
        this.requiresFunction.add((guildDB, throwError) -> {
            try {
                predicate.accept(guildDB);
            } catch (IllegalArgumentException e) {
                if (throwError) throw e;
                return false;
            }
            return true;
        });
        return this;
    }

    public GuildSetting<T> requireRegisteredOwner() {
        requireFunction(db -> {
            Member owner = db.getGuild().getOwner();
            if (owner != null) {
                DBNation ownerNation = DiscordUtil.getNation(owner.getUser());
                if (ownerNation == null) {
                    throw new IllegalArgumentException("The owner of this server (" + owner.getEffectiveName() + ") is not registered with the bot (see: " + CM.register.cmd.toSlashMention() + ")");
                }
                if (ownerNation.active_m() > 7200) {
                    throw new IllegalArgumentException("The owner of this server (user: " + owner.getEffectiveName() + " | nation: " + ownerNation.getNation() + ") has not been active in the last 5 days");
                }
            }
        });
        return this;
    }

    public boolean has(GuildDB db, boolean allowDelegate) {
        return db.getInfoRaw(this, allowDelegate) != null;
    }

    public String getRaw(GuildDB db, boolean allowDelegate) {
        return db.getInfoRaw(this, allowDelegate);
    }

    public GuildSettingCategory getCategory() {
        return category;
    }

    public String setAndValidate(GuildDB db, User user, T value) {
        if (!allowed(db, true)) {
            throw new IllegalArgumentException("This setting is not allowed in this server (you may be missing some prerequisite settings)");
        }
        if (!hasPermission(db, user, value)) {
            throw new IllegalArgumentException("You do not have permission to set " + name() + " to `" + toReadableString(value) + "`");
        }
        value = validate(db, value);
        return set(db, value);
    }
}
