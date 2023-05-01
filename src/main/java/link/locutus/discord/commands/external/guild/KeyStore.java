package link.locutus.discord.commands.external.guild;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.Noformat;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import java.util.*;

public class KeyStore extends Command implements Noformat {
    public KeyStore() {
        super(CommandCategory.GUILD_MANAGEMENT);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.hasAny(user, server, Roles.ADMIN, Roles.INTERNAL_AFFAIRS, Roles.ECON, Roles.MILCOM, Roles.FOREIGN_AFFAIRS);
    }

    @Override
    public String help() {
        return "" + Settings.commandPrefix(true) + "KeyStore <key> <value>";
    }

    @Override
    public String desc() {
        return "Use `" + Settings.commandPrefix(true) + "KeyStore <key>` for info about a setting\n" +
                "Use `" + Settings.commandPrefix(true) + "KeyStore <key> null` to remove a setting\n" +
                "Add `-a` to list all settings.";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        return onCommand(new DiscordChannelIO(event), guild, author, me, args, flags);
    }

    public String onCommand(IMessageIO io, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (me == null) return "Please use " + CM.register.cmd.toSlashMention() + "";

        Integer page = DiscordUtil.parseArgInt(args, "page");
        GuildDB db = Locutus.imp().getGuildDB(guild);
        if (db == null) return "Command must run in a guild.";
        if (args.size() != 2) {
            if (args.size() == 1) {
                GuildDB.Key key = GuildDB.Key.valueOf(args.get(0).toUpperCase());
                String result = "Usage: `" + Settings.commandPrefix(true) + "KeyStore " + key.name() + " <value>`\n" + key.help().trim();

                Object value = db.getOrNull(key, false);
                if (value != null) {
                    String strValue = key.toString(value);
                    result += "\nCurrent value:```" + strValue + "```";
                }

                return result;
            }

            if (!flags.contains('s') && page == null) {
                StringBuilder response = new StringBuilder();
                Map<CommandCategory, Map<GuildDB.Key, Object>> keys = getKeys(db, flags.contains('a'));
                for (Map.Entry<CommandCategory, Map<GuildDB.Key, Object>> entry : keys.entrySet()) {
                    CommandCategory category = entry.getKey();

                    response.append("**").append(category != null ? category.name() : "UNCATEGORIZED").append(":**\n");

                    Map<GuildDB.Key, Object> catKeys = entry.getValue();
                    for (Map.Entry<GuildDB.Key, Object> keyObjectEntry : catKeys.entrySet()) {
                        GuildDB.Key key = keyObjectEntry.getKey();
                        if (!key.hasPermission(db, author, null)) continue;
                        Object setValue = keyObjectEntry.getValue();
                        if (setValue != null) {
                            String setValueStr = key.toString(setValue);
                            if (setValueStr.length() > 21) {
                                setValueStr = setValueStr.substring(0, 20) + "..";
                            }
                            response.append(" - `").append(key.name()).append("`=").append(setValueStr).append("\n");
                        } else {
                            response.append(" - `").append(key.name()).append("`\n"); //  + "`: " + key.help()
                        }
                    }
                }
                response.append("\n").append(desc()).append("\n").append(help());
                io.create().embed("Settings", response.toString()).send();
            }

            Map<CommandCategory, Map<GuildDB.Key, String>> keys = getSheets(db);
            if (!keys.isEmpty() && Roles.ADMIN.has(author, guild)) {
                List<String> pages = new ArrayList<>();
                for (Map.Entry<CommandCategory, Map<GuildDB.Key, String>> entry : keys.entrySet()) {
                    StringBuilder response = new StringBuilder();
                    CommandCategory category = entry.getKey();

                    response.append("**").append(category != null ? category.name() : "UNCATEGORIZED").append(":**\n");

                    Map<GuildDB.Key, String> catKeys = entry.getValue();
                    for (Map.Entry<GuildDB.Key, String> keyObjectEntry : catKeys.entrySet()) {
                        response.append(keyObjectEntry.getValue()).append("\n");
                    }
                    pages.add(response.toString().trim());
                }
                String pageStr = ((page == null ? 0 : page) + 1) + "/" + pages.size();
                String title = "Sheets " + pageStr;
                String command = "!KeyStore";
                DiscordUtil.paginate(io, title, command, page, 1, pages, null, true);
            }

            return null;
        }
        GuildDB.Key key = GuildDB.Key.valueOf(args.get(0).toUpperCase());
        if (!key.hasPermission(db, author, null)) return "No permission for modify that key.";
        if (!key.allowed(db)) return "This guild does not have permission to set this key.";

        String value = args.get(1);
        if (key == GuildDB.Key.API_KEY) {
            if (!value.equalsIgnoreCase("null")) {
                try {
                    IMessageBuilder msg = io.getMessage();
                    if (msg != null) io.delete(msg.getId());
                } catch (InsufficientPermissionException ignore) {
                }
                value = "<redacted>";
            }
        }
        if (args.get(1).equalsIgnoreCase("null")) {
            db.deleteInfo(key);
        } else {
            String newVal = key.validate(db, args.get(1));
            Object obj = key.parse(db, newVal);
            if (!key.hasPermission(db, author, obj)) return "No permission to set that key to `" + args.get(1) + "`";

            if (key.requires != null && db.getOrThrow(key.requires) == null) {
                throw new IllegalArgumentException("Please set `" + key.requires.name() + "` first.");
            }
            db.setInfo(key, args.get(1));
        }
        return "Set " + key.name() + " to " + value + " for " + guild.getName();
    }

    private Map<CommandCategory, Map<GuildDB.Key, String>> getSheets(GuildDB db) {
        Map<CommandCategory, Map<GuildDB.Key, String>> map = new LinkedHashMap<>();
        for (GuildDB.Key key : GuildDB.Key.values()) {
            if (key.name().toLowerCase().endsWith("_sheet")) {
                String value = db.getOrNull(key, false);
                if (value != null) {
                    String baseUrl = "https://tinyurl.com/nnfajjp/";
                    String fullUrl = baseUrl + value;
                    String formatted = MarkupUtil.markdownUrl(key.name(), fullUrl);
                    map.computeIfAbsent(key.category, f -> new LinkedHashMap<>()).put(key, formatted);
                }
            }
        }
        return map;
    }

    private Map<CommandCategory, Map<GuildDB.Key, Object>> getKeys(GuildDB db, boolean listAll) {
        Map<CommandCategory, Map<GuildDB.Key, Object>> map = new LinkedHashMap<>();
        for (GuildDB.Key key : GuildDB.Key.values()) {
            if (!key.requiresSetup && !listAll) continue;
            if (key.requires != null && db.getOrNull(key.requires, false) == null) continue;
            if (!key.allowed(db)) continue;
            map.computeIfAbsent(key.category, f -> new LinkedHashMap<>()).put(key, db.getOrNull(key, false));
        }
        return map;
    }
}