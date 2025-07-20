package link.locutus.discord.db.guild;

import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.SlashCommandManager;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.google.gson.internal.$Gson$Preconditions.checkNotNull;

public abstract class GuildSetting<T> {
    private final Set<GuildSetting> requires = new ObjectLinkedOpenHashSet<>();
    private final Set<String> requiresCoalitionStr = new ObjectLinkedOpenHashSet<>();
    private final Set<String> requiresCoalitionRootStr = new ObjectLinkedOpenHashSet<>();

    private final Map<BiPredicate<GuildDB, Boolean>, Supplier<String>> requiresFunction = new LinkedHashMap<>();
    private final Map<Roles, Boolean> requiresRole = new LinkedHashMap<>();

    private final Key type;
    private final GuildSettingCategory category;
    private String name;

    private Queue<Consumer<GuildSetting<T>>> setupRequirements = new ConcurrentLinkedQueue<>();

    public List<String> getRequirementDesc() {
        List<String> reqListStr = new ArrayList<>();
        for (GuildSetting key : requires) {
            reqListStr.add("setting:" + key.name());
        }
        for (String coalition : requiresCoalitionStr) {
            reqListStr.add("coalition:" + coalition);
        }
        for (String coalition : requiresCoalitionRootStr) {
            reqListStr.add("coalition:" + coalition + "(root)");
        }
        for (Roles role : requiresRole.keySet()) {
            reqListStr.add("role:" + role.name());
        }
        for (Supplier<String> valueSup : requiresFunction.values()) {
            String value = valueSup.get();
            if (value.isEmpty()) continue;
            reqListStr.add("function:" + value);
        }
        return reqListStr;
    }

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


    @Command(desc = "The setting usage instructions")
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
        requiresCoalitionStr.add(coalition.getNameLower());
        return this;
    }

    public GuildSetting<T> requiresCoalition(String coalition) {
        requiresCoalitionStr.add(coalition.toLowerCase());
        return this;
    }

    public GuildSetting<T> requiresCoalitionRoot(String coalition) {
        requiresCoalitionRootStr.add(coalition.toLowerCase());
        return this;
    }

    public T validate(GuildDB db, User user, T value) {
        return value;
    }

    public String toReadableString(GuildDB db, T value) {
        return toString(value);
    }

    public String getCommandFromArg(GuildDB db, Object value) {
        return getCommandObjRaw(db, value);
    }

    public Set<ParametricCallable> getCallables() {
        Set<ParametricCallable> callables = Locutus.imp().getCommandManager().getV2().getCommands().getParametricCallables(f -> f.getObject() == this);
        callables.removeIf(f -> {
            String id = f.getPrimaryCommandId().toLowerCase();
            return id.startsWith("remove") || id.startsWith("delete") || id.startsWith("unregister");
        });
        if (callables.isEmpty()) {
            AlertUtil.error("No configuration command found", name());
        }
        return callables;
    }

    public String getCommandObj(GuildDB db, T value) {
        return getCommandObjRaw(db, value);
    }

    private String getCommandObjRaw(GuildDB db, Object value) {
        Set<ParametricCallable> callables = getCallables();
        if (value != null) {
            for (ParametricCallable callable : callables) {
                for (ParameterData parameter : callable.getUserParameters()) {
                    Type type = parameter.getBinding().getKey().getType();
                    if ((type instanceof Class clazz && clazz.isAssignableFrom(value.getClass())) || callables.size() == 1) {
                        String valueStr = null;
                        if (value instanceof String) {
                            valueStr = (String) value;
                        } else {
                            try {
                                valueStr = toReadableString(db, (T) value);
                            } catch (Throwable e) {
                                valueStr = value + "";
                            }
                        }
                        Map<String, String> args = Collections.singletonMap(parameter.getName(), valueStr);
                        return callable.getSlashCommand(args);
                    }
                }
            }
        }
        return getCommandMention();
    }

    @Command(desc = "The setting command mention")
    public String getCommandMention() {
        if (Locutus.imp() == null || Locutus.imp().getSlashCommands() == null) {
            return CM.settings.info.cmd.key(name).toSlashCommand();
        }
        return getCommandMention(getCallables());
    }

    private String getCommandMention(Set<ParametricCallable> callables) {
        List<String> result = new ArrayList<>();
        for (ParametricCallable callable : callables) {
            result.add(SlashCommandManager.getSlashMention(callable));
        }
        return StringMan.join(result, ", ");
    }

    public String set(GuildDB db, User user, T value) {
        String readableStr = toReadableString(db, value);
        db.setInfo(this, user, value);
        return "Set `" + name() + "` to `" + readableStr + "`\n" +
                "Delete with " + CM.settings.delete.cmd.key(name);
    }

    public T parse(GuildDB db, String input) {
        if (type == null) throw new IllegalStateException("Type is null for " + getClass().getSimpleName());
        LocalValueStore locals = new LocalValueStore<>(getStore());
        locals.addProvider(Key.of(GuildDB.class, Me.class), db);
        locals.addProvider(Key.of(Guild.class, Me.class), db.getGuild());
        Parser parser = locals.get(type);
        if (parser == null) {
            throw new IllegalArgumentException("No parser found for " + type);
        }
        try {
            return (T) parser.apply(locals, input);
        } catch (Throwable e) {
            Logg.text(db.getGuild().toString() + ": Failed to parse " + input + " for " + name() + " with " + parser.getKey().toSimpleString() + " | " + StringMan.stripApiKey(e.getMessage()));
            return null;
        }
    }

    @Command(desc = "The setting name and help instructions")
    public String toString() {
        return name() + "\n> " + help() + "\n";
    }

    @Command(desc = "The setting name")
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
        while (!setupRequirements.isEmpty()) {
            Consumer<GuildSetting<T>> poll = setupRequirements.poll();
            if (poll != null) poll.accept(this);
        }
        List<String> errors = new ArrayList<>();
        for (GuildSetting require : requires) {
            if (require.getOrNull(db, false) == null) {
                if (throwException) {
                    errors.add("You must first enable `" + require.name() + "` (see: " + require.getCommandObj(db, (String) null) + ")");
                } else {
                    return false;
                }
            }
        }
        for (String coalition : requiresCoalitionStr) {
            if (db.getCoalition(coalition).isEmpty()) {
                if (throwException) {
                    errors.add("You must first set the coalition `" + coalition + "` (see: " + CM.coalition.add.cmd.coalitionName(coalition).toSlashCommand() + ")");
                } else {
                    return false;
                }
            }
        }
        for (String coalition : requiresCoalitionRootStr) {
            if (!db.hasCoalitionPermsOnRoot(coalition)) {
                if (throwException) {
                    errors.add("You must first set the coalition `" + coalition + "` on the root server");
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
                    errors.add("Missing required role " + role.name() + " (see: " + CM.role.setAlias.cmd.locutusRole(role.name()).discordRole(null).toSlashCommand() + ")");
                } else {
                    return false;
                }
            }
        }

        for (BiPredicate<GuildDB, Boolean> predicate : requiresFunction.keySet()) {
            try {
                if (!predicate.test(db, throwException)) {
                    return false;
                }
            } catch (IllegalArgumentException e) {
                if (throwException) {
                    errors.add(StringMan.stripApiKey(e.getMessage()));
                } else {
                    return false;
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", errors));
        }

        return true;
    }

    public GuildSetting<T> requiresOffshore() {
        Supplier<String> msg = () -> "No bank is setup (see: " + CM.offshore.add.cmd.toSlashCommand() + ")";
        this.requiresFunction.put((db, throwError) -> {
            if (db.getOffshoreDB() == null) {
                throw new IllegalArgumentException(msg.get());
            }
            return true;
        }, msg);
        return this;
    }

    public GuildSetting<T> setupRequirements(Consumer<GuildSetting<T>> consumer) {
        setupRequirements.add(consumer);
        return this;
    }

    public GuildSetting<T> requiresRole(Roles role, boolean allowAllianceRole) {
        checkNotNull(role);
        this.requiresRole.put(role, allowAllianceRole);
        return this;
    }

    public GuildSetting<T> requiresWhitelisted() {
        String msg = "This guild is not whitelisted by the bot developer (this feature may not be ready for public use yet)";
        this.requiresFunction.put(new BiPredicate<GuildDB, Boolean>() {
            @Override
            public boolean test(GuildDB db, Boolean throwError) {
                if (!db.isWhitelisted()) {
                    throw new IllegalArgumentException(msg);
                }
                return true;
            }
        }, () -> msg);
        return this;
    }

    public GuildSetting<T> nonPublic() {
        String msg = "Please follow the public channels for this <https://discord.gg/cUuskPDrB7> (this is to reduce unnecessary discord calls)";
        this.requiresFunction.put(new BiPredicate<GuildDB, Boolean>() {
            @Override
            public boolean test(GuildDB db, Boolean throwError) {
                if (db.getGuild().getIdLong() == Settings.INSTANCE.ROOT_COALITION_SERVER) return true;
                if (db.hasCoalitionPermsOnRoot(Coalition.WHITELISTED)) return true;
                throw new IllegalArgumentException(msg);
            }
        }, () -> msg);
        return this;
    }

    public static MessageChannel validateChannel(GuildDB db, MessageChannel channel) {
        MessageChannel original = channel;
        channel = DiscordUtil.getChannel(db.getGuild(), channel.getId());
        if (channel == null) {
            throw new IllegalArgumentException("Channel " + original + " not found (are you sure it is in this server?)");
        }
        if (channel.getType() != ChannelType.TEXT) {
            throw new IllegalArgumentException("Channel " + channel.getAsMention() + " is not a text channel (" + channel.getType() + ")");
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
        Supplier<String> msg = () -> "No valid alliance is setup (see: " + GuildKey.ALLIANCE_ID.getCommandMention() + ")";
        requiresFunction.put((db, throwError) -> {
            if (!db.isValidAlliance()) {
                throw new IllegalArgumentException(msg.get());
            }
            return true;
        }, msg);
        return this;
    }

    public GuildSetting<T> requiresNot(GuildSetting setting) {
        return requiresNot(setting, true);
    }

    public GuildSetting<T> requiresNot(GuildSetting setting, boolean checkDelegate) {
        Supplier<String> msg = () -> "Cannot be used with " + setting.name() + " set. Unset via " + CM.settings.info.cmd.toSlashMention();
        requiresFunction.put((db, throwError) -> {
            if (setting.getOrNull(db, checkDelegate) != null) {
                throw new IllegalArgumentException(msg.get());
            }
            return true;
        }, msg);
        return this;
    }

    public GuildSetting<T> requireFunction(Consumer<GuildDB> predicate, String msg) {
        this.requiresFunction.put((guildDB, throwError) -> {
            try {
                predicate.accept(guildDB);
            } catch (IllegalArgumentException | UnsupportedOperationException e) {
                if (throwError) throw e;
                return false;
            }
            return true;
        }, () -> msg);
        return this;
    }

    private void checkRegisteredOwnerOrActiveAlliance(GuildDB db) {
        if (db.isValidAlliance()) {
            if (!db.getAllianceList().getNations(f -> f.getPositionEnum().id >= Rank.HEIR.id && f.active_m() < 43200).isEmpty()) {
                return;
            }
        }
        GuildDB delegate = db.getDelegateServer();
        if (delegate != null) {
            if (delegate.isValidAlliance()) {
                if (!delegate.getAllianceList().getNations(f -> f.getPositionEnum().id >= Rank.HEIR.id && f.active_m() < 43200).isEmpty()) {
                    return;
                }
            }
        }
        GuildDB faServer = GuildKey.FA_SERVER.getOrNull(db, false);
        if (faServer != null) {
            if (faServer.isValidAlliance()) {
                if (!faServer.getAllianceList().getNations(f -> f.getPositionEnum().id >= Rank.HEIR.id && f.active_m() < 43200).isEmpty()) {
                    return;
                }
            }
        }
        if (!GuildKey.ALLIANCE_ID.has(db, false)) {
            for (GuildDB otherDb : Locutus.imp().getGuildDatabases().values()) {
                if (otherDb == db) continue;
                Guild warServer = GuildKey.WAR_SERVER.getOrNull(otherDb, false);
                if (warServer == null || warServer.getIdLong() != db.getIdLong()) continue;
                if (otherDb.isValidAlliance()) {
                    if (!otherDb.getAllianceList().getNations(f -> f.getPositionEnum().id >= Rank.HEIR.id && f.active_m() < 43200).isEmpty()) {
                        return;
                    }
                }
            }
        }

        Member owner = db.getGuild().getOwner();
        if (owner != null && (!Settings.INSTANCE.DISCORD.BOT_OWNER_IS_LOCUTUS_ADMIN || owner.getIdLong() != Locutus.loader().getAdminUserId())) {
            DBNation ownerNation = DiscordUtil.getNation(owner.getUser());
            if (ownerNation == null) {
                throw new IllegalArgumentException("The owner of this server (" + owner.getEffectiveName() + ") is not registered with the bot (see: " + CM.register.cmd.toSlashMention() + ")");
            }
            if (ownerNation.active_m() > 43200) {
                throw new IllegalArgumentException("The owner of this server (user: " + owner.getEffectiveName() + " | nation: " + ownerNation.getNation() + ") has not been active in the last 30 days");
            }
        }
    }


    public GuildSetting<T> requireActiveGuild() {
        requireFunction(this::checkRegisteredOwnerOrActiveAlliance, "Guild owner must be registered to an active nation, or registered to an alliance with an active nation in a leader/heir position");
        return this;
    }

    public boolean has(GuildDB db, boolean allowDelegate) {
        return db.getInfoRaw(this, allowDelegate) != null;
    }

    public String getRaw(GuildDB db, boolean allowDelegate) {
        return db.getInfoRaw(this, allowDelegate);
    }

    @Command(desc = "The setting category")
    public GuildSettingCategory getCategory() {
        return category;
    }

    public String delete(GuildDB db, User user) {
        db.deleteInfo(this);
        return "Deleted `" + name() + "`";
    }

    public T allowedAndValidate(GuildDB db, User user, T value) {
        DBNation nation = DiscordUtil.getNation(user);
        if (nation == null && (!Settings.INSTANCE.DISCORD.BOT_OWNER_IS_LOCUTUS_ADMIN || user.getIdLong() != Locutus.loader().getAdminUserId())) {
            throw new IllegalArgumentException("You are not registered with the bot (see: " + CM.register.cmd.toSlashMention() + ")");
        }
        if (!allowed(db, true)) {
            throw new IllegalArgumentException("This setting is not allowed in this server (you may be missing some prerequisite settings)");
        }
        if (!hasPermission(db, user, value)) {
            throw new IllegalArgumentException("You do not have permission to set " + name() + " to `" + toReadableString(db, value) + "`");
        }
        return validate(db, user, value);
    }

    public String setAndValidate(GuildDB db, User user, T value) {
        value = allowedAndValidate(db, user, value);
        return set(db, user, value);
    }

    public GuildSetting<T> requiresAllies() {
        String msg = "No valid alliance or `" + Coalition.ALLIES + "` coalition exists. See: " + GuildKey.ALLIANCE_ID.getCommandMention() + " or " + CM.coalition.create.cmd.toSlashMention();
        this.requireFunction(db -> {
            if (!db.isValidAlliance()) {
                boolean hasValidAllies = false;
                for (Integer aaId : db.getCoalition(Coalition.ALLIES)) {
                    if (DBAlliance.get(aaId) != null) {
                        hasValidAllies = true;
                        break;
                    }
                }
                if (!hasValidAllies) {
                    if (db.getCoalition(Coalition.ALLIES).isEmpty()) {
                        throw new IllegalArgumentException(msg);
                    }
                }
            }
        }, msg);
        return this;
    }

    @Command(desc = "Is this a channel setting")
    public boolean isChannelType() {
        Type clazzType = getType().getType();
        return clazzType instanceof Class clazz && Channel.class.isAssignableFrom(clazz) && !Category.class.isAssignableFrom(clazz);
    }

    @Command(desc = "The simple name of the setting class type")
    public String getTypeName() {
        return getType().getType().getTypeName();
    }

    @Command(desc = "The name of the setting type key")
    public String getKeyName() {
        return getType().toSimpleString();
    }

    @Command(desc = "The name of the setting")
    public String getName() {
        return name;
    }

    @Command(desc = "The human readable representation of the value")
    @RolePermission(Roles.ADMIN)
    public String getValueString(@Me GuildDB db) {
        T value = getOrNull(db);
        if (value == null) return null;
        return toReadableString(db, value);
    }

    @Command(desc = "Does the setting have a value (even if invalid)")
    @RolePermission(Roles.ADMIN)
    public boolean hasValue(@Me GuildDB db, @Switch("d") boolean checkDelegate) {
        return getOrNull(db, checkDelegate) != null;
    }

//    @Command(desc = "The setting value")
    @RolePermission(Roles.ADMIN)
    public T getValueString(@Me GuildDB db, @Switch("d") boolean checkDelegate) {
        return getOrNull(db, checkDelegate);
    }

    @Command(desc = "If the value is invalid")
    @RolePermission(Roles.ADMIN)
    public boolean hasInvalidValue(@Me GuildDB db, @Switch("d") boolean checkDelegate) {
        String raw = getRaw(db, checkDelegate);
        if (raw == null) return false;
        return getOrNull(db, checkDelegate) == null;
    }

}
