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
import link.locutus.discord.db.guild.GuildChannelSetting;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildSettingCategory;
import link.locutus.discord.db.guild.SheetKeys;
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
                GuildSetting key = GuildDB.Key.valueOf(args.get(0).toUpperCase());
                String result = "Usage: `" + key.getCommand("YOUR_VALUE_HERE") + "`\n" + key.help().trim();

                String rawValue = key.getRaw(db, false);
                if (rawValue != null) {
                    Object value = db.getOrNull(key, false);
                    if (value == null) {
                        result += "\nProvided value is no longer valid: `" + rawValue + "` (Please set a new value)";
                    } else {
                        String strValue = key.toReadableString(value);
                        result += "\nCurrent value:```" + strValue + "```";
                    }
                }

                return result;
            }

            if (!flags.contains('s') && page == null) {
                StringBuilder response = new StringBuilder();
                Map<GuildSettingCategory, Map<GuildSetting, Object>> keys = getKeys(db, flags.contains('a'));
                for (Map.Entry<GuildSettingCategory, Map<GuildSetting, Object>> entry : keys.entrySet()) {
                    GuildSettingCategory category = entry.getKey();

                    response.append("**").append(category != null ? category.name() : "UNCATEGORIZED").append(":**\n");

                    Map<GuildSetting, Object> catKeys = entry.getValue();
                    for (Map.Entry<GuildSetting, Object> keyObjectEntry : catKeys.entrySet()) {
                        GuildSetting key = keyObjectEntry.getKey();
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

            Map<SheetKeys, String> keys = getSheets(db);
            if (!keys.isEmpty() && Roles.ADMIN.has(author, guild)) {
                List<String> pages = new ArrayList<>();
                for (Map.Entry<SheetKeys, String> catKeys : keys.entrySet()) {
                    StringBuilder response = new StringBuilder();
                    SheetKeys key = catKeys.getKey();
                    response.append(key.name() + ": <https://docs.google.com/document/d/" + catKeys.getValue() + ">").append("\n");
                    pages.add(response.toString().trim());
                }
                String pageStr = ((page == null ? 0 : page) + 1) + "/" + pages.size();
                String title = "Sheets " + pageStr;
                String command = "!KeyStore";
                DiscordUtil.paginate(io, title, command, page, 1, pages, null, true);
            }

            return null;
        }
        GuildSetting key = GuildDB.Key.valueOf(args.get(0));
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
            if (!key.has(db, false)) {
                return "Key `" + key.name() + "` is not set in " + guild.getName();
            }
            db.deleteInfo(key);
            return "Deleted " + key.name() + " for " + guild.getName();
        } else {
            Object newVal = key.parse(db, args.get(1));
            newVal = key.validate(db, newVal);
            if (!key.hasPermission(db, author, newVal)) return "No permission to set that key to `" + args.get(1) + "`";

            return key.set(db, newVal) + " (in guild " + guild.getName() + ")";
        }
    }

    private Map<SheetKeys, String> getSheets(GuildDB db) {
        Map<SheetKeys, String> map = new LinkedHashMap<>();
        for (SheetKeys key : SheetKeys.values()) {
            String value = db.getInfo(key, false);
            if (value != null) {
                String baseUrl = "https://tinyurl.com/nnfajjp/";
                String fullUrl = baseUrl + value;
                String formatted = MarkupUtil.markdownUrl(key.name(), fullUrl);
                map.put(key, formatted);
            }
        }
        return map;
    }

    private Map<GuildSettingCategory, Map<GuildSetting, Object>> getKeys(GuildDB db, boolean listAll) {
        Map<GuildSettingCategory, Map<GuildSetting, Object>> map = new LinkedHashMap<>();
        for (GuildSetting key : GuildDB.Key.values()) {
            if (!key.allowed(db) && !listAll) continue;
            map.computeIfAbsent(key.getCategory(), f -> new LinkedHashMap<>()).put(key, db.getOrNull(key, false));
        }
        return map;
    }
}