package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandUsageException;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttribute;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.lang.reflect.Type;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class NationPlaceholders extends Placeholders<DBNation> {
    private final Map<String, NationAttribute> customMetrics = new HashMap<>();

    public NationPlaceholders(ValueStore store, ValidatorStore validators, PermissionHandler permisser) {
        super(DBNation.class, new DBNation(), store, validators, permisser);
    }

    @Override
    public String getCommandMention() {
        return CM.help.find_nation_placeholder.cmd.toSlashMention();
    }

    public List<NationAttribute> getMetrics(ValueStore store) {
        List<NationAttribute> result = new ArrayList<>();
        for (CommandCallable cmd : getFilterCallables()) {
            String id = cmd.aliases().get(0);
            try {
                Map.Entry<Type, Function<DBNation, Object>> typeFunction = getPlaceholderFunction(store, id);
                if (typeFunction == null) continue;

                NationAttribute metric = new NationAttribute(cmd.getPrimaryCommandId(), cmd.simpleDesc(), typeFunction.getKey(), typeFunction.getValue());
                result.add(metric);
            } catch (IllegalStateException | CommandUsageException ignore) {
                continue;
            }
        }
        return result;
    }

    public NationAttributeDouble getMetricDouble(ValueStore<?> store, String id) {
        return getMetricDouble(store, id, false);
    }

    public NationAttribute getMetric(ValueStore<?> store, String id, boolean ignorePerms) {
        Map.Entry<Type, Function<DBNation, Object>> typeFunction = getTypeFunction(store, id, ignorePerms);
        if (typeFunction == null) return null;
        return new NationAttribute<>(id, "", typeFunction.getKey(), typeFunction.getValue());
    }

    public NationAttributeDouble getMetricDouble(ValueStore store, String id, boolean ignorePerms) {
        ParametricCallable cmd = get(getCmd(id));
        if (cmd == null) return null;
        Map.Entry<Type, Function<DBNation, Object>> typeFunction = getTypeFunction(store, id, ignorePerms);
        if (typeFunction == null) return null;

        Function<DBNation, Object> genericFunc = typeFunction.getValue();
        Function<DBNation, Double> func;
        Type type = typeFunction.getKey();
        if (type == int.class || type == Integer.class) {
            func = nation -> ((Integer) genericFunc.apply(nation)).doubleValue();
        } else if (type == double.class || type == Double.class) {
            func = nation -> (Double) genericFunc.apply(nation);
        } else if (type == short.class || type == Short.class) {
            func = nation -> ((Short) genericFunc.apply(nation)).doubleValue();
        } else if (type == byte.class || type == Byte.class) {
            func = nation -> ((Byte) genericFunc.apply(nation)).doubleValue();
        } else if (type == long.class || type == Long.class) {
            func = nation -> ((Long) genericFunc.apply(nation)).doubleValue();
        } else if (type == boolean.class || type == Boolean.class) {
            func = nation -> ((Boolean) genericFunc.apply(nation)) ? 1d : 0d;
        } else {
            return null;
        }
        return new NationAttributeDouble(cmd.getPrimaryCommandId(), cmd.simpleDesc(), func);
    }

    public List<NationAttributeDouble> getMetricsDouble(ValueStore store) {
        List<NationAttributeDouble> result = new ArrayList<>();
        for (CommandCallable cmd : getFilterCallables()) {
            String id = cmd.aliases().get(0);
            NationAttributeDouble metric = getMetricDouble(store, id, true);
            if (metric != null) {
                result.add(metric);
            }
        }
        for (Map.Entry<String, NationAttribute> entry : customMetrics.entrySet()) {
            String id = entry.getKey();
            NationAttributeDouble metric = getMetricDouble(store, id, true);
            if (metric != null) {
                result.add(metric);
            }
        }
        return result;
    }

    public String format(Guild guild, DBNation nation, User user, String arg) {
        if (nation == null && user != null) {
            nation = DBNation.getByUser(user);
        }
        if (user == null && nation != null) {
            user = nation.getUser();
        }
        LocalValueStore locals = new LocalValueStore<>(this.getStore());
        if (nation != null) {
            locals.addProvider(Key.of(DBNation.class, Me.class), nation);
        }
        if (user != null) {
            locals.addProvider(Key.of(User.class, Me.class), user);
        }
        if (guild != null) {
            locals.addProvider(Key.of(Guild.class, Me.class), guild);
        }
        return format(locals, arg);
    }

    public String format(ValueStore<?> store, String arg) {
        DBNation me = store.getProvided(Key.of(DBNation.class, Me.class));
        User author = null;
        try {
            author = store.getProvided(Key.of(User.class, Me.class));
        } catch (Exception ignore) {
        }

        if (author != null && arg.contains("%user%")) {
            arg = arg.replace("%user%", author.getAsMention());
        }

        return format(arg, 0, new Function<String, String>() {
            @Override
            public String apply(String placeholder) {
                NationAttribute result = NationPlaceholders.this.getMetric(store, placeholder, false);
                if (result == null && !placeholder.startsWith("get")) {
                    result = NationPlaceholders.this.getMetric(store, "get" + placeholder, false);
                }
                if (result != null) {
                    Object obj = result.apply(me);
                    if (obj != null) {
                        return obj.toString();
                    }
                }
                return null;
            }
        });
    }

    @Override
    public Set<DBNation> parse(ValueStore store, String name) {
        String nameLower = name.toLowerCase(Locale.ROOT);
        Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
        if (name.equals("*")) {
            return new ObjectArraySet<>(Locutus.imp().getNationDB().getNations().values());
        } else if (name.contains("tax_id=")) {
            int taxId = PnwUtil.parseTaxId(name);
            return Locutus.imp().getNationDB().getNationsByBracket(taxId);
        } else if (name.startsWith("https://docs.google.com/spreadsheets/") || name.startsWith("sheet:")) {
            String key = SpreadSheet.parseId(name);
            SpreadSheet sheet = null;
            try {
                sheet = SpreadSheet.create(key);
            } catch (GeneralSecurityException | IOException e) {
                throw new RuntimeException(e);
            }

            List<List<Object>> rows = sheet.getAll();
            if (rows == null || rows.isEmpty()) return Collections.emptySet();

            Set<DBNation> toAdd = new LinkedHashSet<>();
            Integer nationI = 0;
            boolean isLeader = false;
            List<Object> header = rows.get(0);
            for (int i = 0; i < header.size(); i++) {
                if (header.get(i) == null) continue;
                if (header.get(i).toString().equalsIgnoreCase("nation")) {
                    nationI = i;
                    break;
                }
                if (header.get(i).toString().toLowerCase(Locale.ROOT).contains("leader")) {
                    nationI = i;
                    isLeader = true;
                    break;
                }

            }
            for (int i = 1; i < rows.size(); i++) {
                List<Object> row = rows.get(i);
                if (row.size() <= nationI) continue;

                Object cell = row.get(nationI);
                if (cell == null) continue;
                String nationName = (cell + "").trim();
                if (nationName.isEmpty()) continue;

                DBNation nation = null;
                if (isLeader) {
                    nation = Locutus.imp().getNationDB().getNationByLeader(nationName);
                }
                if (nation == null) {
                    nation = DiscordUtil.parseNation(nationName);
                }
                if (nation != null) {
                    toAdd.add(nation);
                } else {
                    throw new IllegalArgumentException("Unknown nation: " + nationName + " in " + name);
                }
            }
            return toAdd;
        }  else if (nameLower.startsWith("aa:")) {
            Set<Integer> alliances = DiscordUtil.parseAllianceIds(guild, name.split(":", 2)[1].trim());
            if (alliances == null) throw new IllegalArgumentException("Invalid alliance: `" + name + "`");
            Set<DBNation> allianceMembers = Locutus.imp().getNationDB().getNations(alliances);
            return allianceMembers;
        }

        Set<DBNation> nations = new LinkedHashSet<>();
        boolean containsAA = nameLower.contains("/alliance/");
        DBNation nation = containsAA ? null : DiscordUtil.parseNation(name, true);
        if (nation == null || containsAA) {
            Set<Integer> alliances = DiscordUtil.parseAllianceIds(guild, name);
            if (alliances == null) {
                Role role = guild != null ? DiscordUtil.getRole(guild, name) : null;
                if (role != null) {
                    List<Member> members = guild.getMembersWithRoles(role);
                    for (Member member : members) {
                        PNWUser user = Locutus.imp().getDiscordDB().getUserFromDiscordId(member.getIdLong());
                        if (user == null) continue;
                        nation = Locutus.imp().getNationDB().getNation(user.getNationId());
                        if (nation != null) nations.add(nation);
                    }

                } else if (name.contains("#")) {
                    String[] split = name.split("#");
                    PNWUser user = Locutus.imp().getDiscordDB().getUser(null, split[0], name);
                    if (user != null) {
                        nation = Locutus.imp().getNationDB().getNation(user.getNationId());
                    }
                    if (nation == null) {
                        throw new IllegalArgumentException("Invalid nation/aa: `" + name + "`");
                    }
                } else {
                    throw new IllegalArgumentException("Invalid nation/aa: `" + name + "`");
                }
            } else {
                Set<DBNation> allianceMembers = Locutus.imp().getNationDB().getNations(alliances);
                nations.addAll(allianceMembers);
            }
        } else {
            nations.add(nation);
        }
        return nations;
    }
}