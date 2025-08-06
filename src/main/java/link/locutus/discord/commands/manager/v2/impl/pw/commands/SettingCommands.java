package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildSettingCategory;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class SettingCommands {
    private Map<SheetKey, String> getSheets(GuildDB db) {
        Map<SheetKey, String> map = new LinkedHashMap<>();
        for (SheetKey key : SheetKey.values()) {
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

    private static Map<GuildSettingCategory, Map<GuildSetting, Object>> getKeys(GuildDB db, boolean listAll) {
        Map<GuildSettingCategory, Map<GuildSetting, Object>> map = new LinkedHashMap<>();
        for (GuildSetting key : GuildKey.values()) {
            if (!key.allowed(db) && !listAll) continue;
            map.computeIfAbsent(key.getCategory(), f -> new LinkedHashMap<>()).put(key, db.getOrNull(key, false));
        }
        return map;
    }

    @Command(desc = "Delete an alliance or guild setting")
    @RolePermission(any = true, value = {Roles.ADMIN, Roles.INTERNAL_AFFAIRS, Roles.ECON, Roles.MILCOM, Roles.FOREIGN_AFFAIRS})
    public String delete( @Me GuildDB db, @Me User author, GuildSetting key) {
        if (!key.hasPermission(db, author, null)) {
            return "You do not have permission to delete the key `" + key.name() + "`";
        }
        String valueRaw = key.getRaw(db, false);
        if (valueRaw == null) {
            return "The key `" + key.name() + "` has not been set on this guild";
        }
        StringBuilder response = new StringBuilder();
        Object value = key.getOrNull(db, false);
        if (value != null) {
            response.append("Previous value:\n```\n" + key.toReadableString(db, value) + "\n```\n");
        } else {
            response.append("Previous value (invalid):\n```\n" + valueRaw + "\n```\n");
        }
        response.append(key.delete(db, author));
        return response.toString();
    }

    @Command(desc = "Configure any alliance or guild settings")
    @RolePermission(any = true, value = {Roles.ADMIN, Roles.INTERNAL_AFFAIRS, Roles.ECON, Roles.MILCOM, Roles.FOREIGN_AFFAIRS})
    @NoFormat
    public static String info(@Me Guild guild, @Me User author,
                           @Arg("The setting to change or view")
                           @Default GuildSetting key,
                           @Arg("The value to set the setting to")
                           @Default @TextArea String value,
                           @Switch("a") boolean listAll) throws Exception {
        GuildDB db = Locutus.imp().getGuildDB(guild);
        if (db == null) return "Command must run in a guild.";
        if (value == null) {
            if (key != null) {
                StringBuilder response = new StringBuilder();
                response.append("# **__").append(key.name()).append("__**\n");
                response.append("> " + key.help().replaceAll("\n", "\n> ") + "\n\n");
                response.append("**category**: `" + key.getCategory() + "`\n");
                response.append("**type**: `" + key.getType().toSimpleString() + "`\n");
                response.append("**commands**: ").append(key.getCommandMention()).append("\n");

                List<String> requirementsStr = key.getRequirementDesc();
                if (!requirementsStr.isEmpty()) {
                    response.append("**requirements**:\n- " + String.join("\n- ", requirementsStr) + "\n\n");
                }

                try {
                    key.allowed(db, true);
                } catch (IllegalArgumentException e) {
                    String[] msg = e.getMessage().split("\n");
                    response.append("**missing requirements**:\n- " + StringMan.join(msg, "\n- ") + "\n");
                }
                response.append("\n");

                String valueStr = key.getRaw(db, false);

                if (valueStr != null) {
                    Object valueObj = key.getOrNull(db, false);
                    if (valueObj == null) {
                        response.append("**current value**: `" + valueStr + "`\n\n");
                        response.append("`!! A value is set but it is invalid`\n");
                    } else {
                        response.append("**current value**: `" + key.toReadableString(db, valueObj) + "`\n\n");
                    }
                    response.append("`note: to delete, use: " + CM.settings.delete.cmd.key(key.name()).toSlashCommand(false) + "`\n");
                } else {
                    response.append("`no value is set`\n");
                }
                return response.toString();
            } else {
                StringBuilder response = new StringBuilder();
                Map<GuildSettingCategory, Map<GuildSetting, Object>> keys = getKeys(db, listAll);
                for (Map.Entry<GuildSettingCategory, Map<GuildSetting, Object>> entry : keys.entrySet()) {
                    GuildSettingCategory category = entry.getKey();

                    response.append("# **__").append(category.name()).append(":__**\n");

                    Map<GuildSetting, Object> catKeys = entry.getValue();
                    for (Map.Entry<GuildSetting, Object> keyObjectEntry : catKeys.entrySet()) {
                        GuildSetting currKey = keyObjectEntry.getKey();
                        if (!currKey.hasPermission(db, author, null)) continue;

                        String hide = "";
                        if (!currKey.allowed(db, false)) {
                            hide = "~~";
                        }
                        response.append("- ").append(hide + "`" + currKey.name() + "`" + hide);
//                        response.append(" (" + currKey.getCommandMention() + ")");

                        Object setValue = keyObjectEntry.getValue();
                        if (setValue != null) {
                            String setValueStr = currKey.toReadableString(db, setValue);
                            if (setValueStr.length() > 25) {
                                setValueStr = setValueStr.substring(0, 24) + "\u2026";
                            }
                            response.append("=").append(setValueStr);
                        }
                        response.append("\n");
                    }
                }

                response.append("\n");
                if (!listAll) {
                    response.append("To list all setting: " + CM.settings.info.cmd.listAll("true") + "\n");
                }
                response.append("For info/usage: " + CM.settings.info.cmd.key("YOUR_KEY_HERE").toSlashCommand(false) + "\n");
                response.append("To delete: " + CM.settings.delete.cmd.toSlashMention() + "\n");
                response.append("Find a setting: " + CM.help.find_setting.cmd.toSlashMention());

                return response.toString();
            }
        } else if (key == null) {
            throw new IllegalArgumentException("Please set a value for `key`");
        }
        if (!key.hasPermission(db, author, null)) return "No permission for modify that key.";
        if (!key.allowed(db, true)) return "This guild does not have permission to set this key.";

        Object valueObj;
        if (value.equalsIgnoreCase("null")) {
            valueObj = null;
        } else {
            valueObj = key.parse(db, value);
            valueObj = key.validate(db, author, valueObj);

            if (valueObj == null) {
                return "Invalid value for key `" + key.name() + "`";
            }
            if (!key.hasPermission(db, author, valueObj)) return "No permission to set `" + key.name() + "` to `" + key.toReadableString(db, valueObj) + "`";
        }
        if (valueObj == null) {
            if (!key.has(db, false)) {
                return "Key `" + key.name() + "` is already unset.";
            }
            return key.delete(db, author);
        } else {
            return key.set(db, author, valueObj);
        }
    }

    @Command(desc = "View set or delete alliance or guild google sheets")
    @RolePermission(any = true, value = {Roles.ADMIN, Roles.INTERNAL_AFFAIRS, Roles.ECON, Roles.MILCOM, Roles.FOREIGN_AFFAIRS})
    public String sheets(@Me GuildDB db) throws Exception {
        Map<SheetKey, String> sheets = getSheets(db);
        if (sheets.isEmpty()) {
            return "No sheets are configured (sheets are created when you use a sheet command)";
        }
        StringBuilder response = new StringBuilder();
        response.append("**Sheets**:\n");
        for (Map.Entry<SheetKey, String> entry : sheets.entrySet()) {
            response.append(entry.getKey() + ": <" + entry.getValue()).append(">\n");
        }
        return response.toString();
    }

    @Command
    @RolePermission(Roles.ADMIN)
    public String importTransactions(@Me GuildDB db, @Me User user, @Me JSONObject command, @Me IMessageIO io,
                                     Guild server, Set<DBNation> nations, @Switch("f") boolean force) {
        if (nations.size() > 1000) {
            throw new IllegalArgumentException("Too many nations to import. Max 1000");
        }
        if (!Roles.ADMIN.has(user, server)) {
            throw new IllegalArgumentException("No permission to import from " + server + ". Missing " + Roles.ADMIN.toDiscordRoleNameElseInstructions(db.getGuild()));
        }
        GuildDB other = Locutus.imp().getGuildDB(server);
        Set<Integer> nationIds = new IntOpenHashSet();
        for (DBNation nation : nations) {
            nationIds.add(nation.getId());
        }
        if (!force) {
            String title = "Overwrite Existing Transactions?";
            String body = "Are you sure you want to import and overwrite the transactions for " + nationIds.size() + " nations?";
            io.create().confirmation(title, body, command).send();
            return null;
        }
        StringBuilder tsv = new StringBuilder();
        tsv.append("tx_id\ttx_datetime\tsender_id\tsender_type\treceiver_id\treceiver_type\tbanker_nation_id\tnote\n");

        db.iterateTransactionsByIds(nationIds, 2, 0L, Long.MAX_VALUE, new Consumer<Transaction2>() {
            @Override
            public void accept(Transaction2 transaction) {
                tsv.append(transaction.tx_id).append("\t");
                tsv.append(transaction.tx_datetime).append("\t");
                tsv.append(transaction.sender_id).append("\t");
                tsv.append(transaction.sender_type).append("\t");
                tsv.append(transaction.receiver_id).append("\t");
                tsv.append(transaction.receiver_type).append("\t");
                tsv.append(transaction.banker_nation).append("\t");
                tsv.append(transaction.note).append("\n");
            }
        });

        io.create().append("Please wait...\nSee attached, the list of transactions being overwritten")
                .file("transactions.tsv", tsv.toString()).send();
        int numAdded = db.importNationTransactions(other, nationIds);
        return "Done! Added " + numAdded + " transactions.\n" +
                "See also " + CM.settings.info.cmd.key("") + " with:\n" +
                "- `" + GuildKey.TAX_BASE.name() + "`\n" +
                "- `" + GuildKey.ALLIANCE_ID.name() + "`\n" +
                "And also see " + CM.coalition.add.cmd.coalitionName("").alliances("") + " with:\n" +
                "- `" + Coalition.OFFSHORE + "`\n" +
                "- `" + Coalition.TRACK_DEPOSITS + "`\n" +
                "- `" + Coalition.UNTRACKED + "`";
    }
}
