package link.locutus.discord.db.guild;

import com.google.gson.reflect.TypeToken;
import com.politicsandwar.graphql.model.ApiKeyDetails;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.apiv3.subscription.PnwPusherShardManager;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.EnemyAlertChannelMode;
import link.locutus.discord.db.entities.MMRMatcher;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.BeigeReason;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.AutoAuditType;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.io.PagePriority;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class GuildKey {
    public static final GuildSetting<Set<Integer>> ALLIANCE_ID = new GuildSetting<Set<Integer>>(GuildSettingCategory.DEFAULT, Set.class, Integer.class) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String registerAlliance(@Me GuildDB db, @Me User user, Set<DBAlliance> alliances) {
            Set<Integer> existing = ALLIANCE_ID.getOrNull(db, false);
            existing = existing == null ? new LinkedHashSet<>() : new LinkedHashSet<>(existing);
            Set<Integer> toAdd = alliances.stream().map(DBAlliance::getId).collect(Collectors.toSet());
            for (DBAlliance alliance : alliances) {
                if (existing.contains(alliance.getId())) {
                    throw new IllegalArgumentException("Alliance " + alliance.getName() + " (id: " + alliance.getId() + ") is already registered (registered: " + StringMan.join(existing, ",") + ")");
                }
            }
            toAdd = ALLIANCE_ID.allowedAndValidate(db, user, toAdd);
            existing.addAll(toAdd);
            return ALLIANCE_ID.set(db, toAdd);
        }
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String unregisterAlliance(@Me GuildDB db, @Me User user, Set<DBAlliance> alliances) {
            Set<Integer> existing = ALLIANCE_ID.getOrNull(db, false);
            existing = existing == null ? new LinkedHashSet<>() : new LinkedHashSet<>(existing);
            for (DBAlliance alliance : alliances) {
                if (!existing.contains(alliance.getId())) {
                    throw new IllegalArgumentException("Alliance " + alliance.getId() + " is not registered (registered: " + StringMan.join(existing, ",") + ")");
                }
                existing.remove(alliance.getId());
            }
            if (existing.isEmpty()) {
                return ALLIANCE_ID.delete(db, user);
            }
            return ALLIANCE_ID.set(db, existing);
        }
        @Override
        public Set<Integer> validate(GuildDB db, Set<Integer> aaIds) {
            if (DELEGATE_SERVER.has(db, false))
                throw new IllegalArgumentException("Cannot set alliance id of delegate server (please unset DELEGATE_SERVER first)");

            if (aaIds.isEmpty()) {
                throw new IllegalArgumentException("No alliance provided");
            }

            for (int aaId : aaIds) {
                if (aaId == 0) {
                    throw new IllegalArgumentException("None alliance (id=0) cannot be registered: " + aaIds);
                }
                DBAlliance alliance = DBAlliance.getOrCreate(aaId);
                GuildDB otherDb = alliance.getGuildDB();
                Member owner = db.getGuild().getOwner();
                DBNation ownerNation = owner != null ? DiscordUtil.getNation(owner.getUser()) : null;
                if (ownerNation == null || ownerNation.getAlliance_id() != aaId || ownerNation.getPosition() < Rank.LEADER.id) {
                    Set<String> inviteCodes = new HashSet<>();
                    boolean isValid = Roles.ADMIN.hasOnRoot(owner.getUser());
                    if (!isValid) {
                        try {
                            try {
                                List<Invite> invites = RateLimitUtil.complete(db.getGuild().retrieveInvites());
                                for (Invite invite : invites) {
                                    String inviteCode = invite.getCode();
                                    inviteCodes.add(inviteCode);
                                }
                            } catch (Throwable ignore) {
                            }

                            if (!inviteCodes.isEmpty() && alliance.getDiscord_link() != null && !alliance.getDiscord_link().isEmpty()) {
                                for (String code : inviteCodes) {
                                    if (alliance.getDiscord_link().contains(code)) {
                                        isValid = true;
                                        break;
                                    }
                                }
                            }

                            if (!isValid) {
                                String url = Settings.INSTANCE.PNW_URL() + "/alliance/id=" + aaId;
                                String content = FileUtil.readStringFromURL(PagePriority.ALLIANCE_ID_AUTH_CODE.ordinal(), url);
                                String idStr = db.getGuild().getId();

                                if (!content.contains(idStr)) {
                                    for (String inviteCode : inviteCodes) {
                                        if (content.contains(inviteCode)) {
                                            isValid = true;
                                            break;
                                        }
                                    }
                                } else {
                                    isValid = true;
                                }
                            }

                            if (!isValid) {
                                String msg = "1. Go to: <" + Settings.INSTANCE.PNW_URL() + "/alliance/edit/id=" + aaId + ">\n" +
                                        "2. Scroll down to where it says Alliance Description:\n" +
                                        "3. Put your guild id `" + db.getIdLong() + "` somewhere in the text\n" +
                                        "4. Click save\n" +
                                        "5. Run the command " + getCommandObj(aaIds) + " again\n" +
                                        "(note: you can remove the id after setup)";
                                throw new IllegalArgumentException(msg);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                if (otherDb != null && otherDb != db) {
                    otherDb.deleteInfo(ALLIANCE_ID);

                    String msg = "Only 1 root server per Alliance is permitted. The ALLIANCE_ID in the other guild: " + otherDb.getGuild() + " has been removed.\n" +
                            "To have multiple servers, set the ALLIANCE_ID on your primary server, and then set " + DELEGATE_SERVER.getCommandMention() + " on your other servers\n" +
                            "The `<guild-id>` for this server is `" + db.getIdLong() + "` and the id for the other server is `" + otherDb.getIdLong() + "`.\n\n" +
                            "Run this command again to confirm and set the ALLIANCE_ID";
                    throw new IllegalArgumentException(msg);
                }
            }
            return aaIds;
        }

        @Override
        public String help() {
            return "Your alliance id";
        }

        @Override
        public String toString(Set<Integer> value) {
            return StringMan.join(value, ",");
        }
    };

    public static final GuildSetting<List<String>> API_KEY = new GuildSetting<List<String>>(GuildSettingCategory.DEFAULT, List.class, String.class) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String registerApiKey(@Me GuildDB db, @Me User user, List<String> apiKeys) {
            List<String> existing = API_KEY.getOrNull(db);
            existing = existing == null ? new ArrayList<>() : new ArrayList<>(existing);

            List<String> toAdd = API_KEY.allowedAndValidate(db, user, apiKeys);

            StringBuilder response = new StringBuilder();
            for (String key : existing) {
                // list duplicate keys
                if (toAdd.contains(key)) {
                    Integer nationId = Locutus.imp().getDiscordDB().getNationFromApiKey(key, false);
                    String startWith = key.charAt(0) + "";
                    String endWith = key.charAt(key.length() - 1) + "";
                    response.append("The existing key `" + startWith + "..." + endWith + "` for nation: " + nationId + " is already registered\n");
                    toAdd.remove(key);
                }
            }

            for (String key : existing) {
                ApiKeyDetails details = null;
                Integer nationId = null;
                try {
                    nationId = Locutus.imp().getDiscordDB().getNationFromApiKey(key, false);
                    details = new PoliticsAndWarV3(ApiKeyPool.builder().addKeyUnsafe(key).build()).getApiKeyStats();
                } catch (Throwable e) {}
                if (details == null || details.getNation() == null || details.getNation().getId() == null) {
                    String startWith = key.charAt(0) + "";
                    String endWith = key.charAt(key.length() - 1) + "";
                    response.append("The key `" + startWith + "..." + endWith + "` (nation: " + nationId + ") is invalid and has been removed\n");
                    existing.remove(key);
                }
            }
            apiKeys = API_KEY.allowedAndValidate(db, user, apiKeys);

            existing.addAll(apiKeys);
            existing.removeIf(String::isBlank);
            // List the missing keys
            Set<Integer> aaIds = new HashSet<>(db.getAllianceIds());
            for (String key : existing) {
                Integer nationId = Locutus.imp().getDiscordDB().getNationFromApiKey(key, false);
                if (nationId != null) {
                    DBNation nation = DBNation.getById(nationId);
                    if (nation != null) {
                        aaIds.remove(nation.getAlliance_id());
                    }
                }
            }
            if (!aaIds.isEmpty()) {
                response.append("The following alliance ids are missing from the api keys: " + StringMan.join(aaIds, ",") + "\n");
            }

            response.append(API_KEY.set(db, existing));
            return response.toString();
        }

        @Override
        public List<String> validate(GuildDB db, List<String> keys) {
            keys = new ArrayList<>(new LinkedHashSet<>(keys));
            Set<Integer> aaIds = db.getAllianceIds();
            if (aaIds.isEmpty()) {
                throw new IllegalArgumentException("Please first use " + GuildKey.ALLIANCE_ID.getCommandMention());
            }
            for (String key : keys) {
                try {
                    Integer nationId = Locutus.imp().getDiscordDB().getNationFromApiKey(key);
                    if (nationId == null) {
                        throw new IllegalArgumentException("Invalid API key");
                    }
                    DBNation nation = DBNation.getById(nationId);
                    if (nation == null) {
                        throw new IllegalArgumentException("Nation not found for id: " + nationId + "(out of sync?)");
                    }
                    if (!aaIds.contains(nation.getAlliance_id())) {
                        nation.update(true);
                        if (!aaIds.contains(nation.getAlliance_id())) {
                            throw new IllegalArgumentException("Nation " + nation.getName() + " is not in your alliance: " + StringMan.getString(aaIds));
                        }
                    }
                } catch (Throwable e) {
                    try {
                        ApiKeyDetails details = new PoliticsAndWarV3(ApiKeyPool.builder().addKeyUnsafe(key).build()).getApiKeyStats();
                        Integer nationId = details.getNation().getId();
                        if (nationId != null) {
                            DBNation nation = DBNation.getById(nationId);
                            if (nation != null && !aaIds.contains(nation.getAlliance_id())) {
                                continue;
                            }
                            throw new IllegalArgumentException("API key is not from a nation in the alliance (nation: " + nation + "): " + e.getMessage());
                        }
                    } catch (Throwable ignore) {
                        ignore.printStackTrace();
                    }
                    throw new IllegalArgumentException("Key was rejected: " + e.getMessage());
                }
            }
            return keys;
        }

        @Override
        public String toString(List<String> value) {
            value.removeIf(f -> f.isBlank() || f.endsWith(","));
            return StringMan.join(value, ",");
        }

        @Override
        public List<String> parse(GuildDB db, String input) {
            return new ArrayList<>(StringMan.split(input, ','));
        }

        @Override
        public String toReadableString(List<String> value) {
            List<String> redacted = new ArrayList<>();
            for (String key : value) {
                String startWith = key.charAt(0) + "";
                String endWith = key.charAt(key.length() - 1) + "";
                Integer nationId = Locutus.imp().getDiscordDB().getNationFromApiKey(key, false);
                if (nationId != null) {
                    redacted.add(PnwUtil.getName(nationId, false));
                } else {
                    redacted.add(startWith + "..." + endWith);
                }
            }
            return StringMan.join(redacted, ",");
        }

        @Override
        public String help() {
            return "API key found on: <https://politicsandwar.com/account/>\n" +
                    "Needed for alliance functions and information";
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID));

    public static GuildSetting<MessageChannel> ESPIONAGE_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.WAR_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String ESPIONAGE_ALERT_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return ESPIONAGE_ALERT_CHANNEL.setAndValidate(db, user, channel);
        }

        @Override
        public MessageChannel validate(GuildDB db, MessageChannel channel) {
            db.getOrThrow(ALLIANCE_ID);
            Set<Integer> aaIds = db.getAllianceIds(true);
            if (aaIds.isEmpty()) {
                throw new IllegalArgumentException("Guild not registered to an alliance. See: " + CM.settings.info.cmd.toSlashMention() + " with key `" + ALLIANCE_ID.name() + "`");
            }
            String msg = "Invalid api key set. See " + CM.settings.info.cmd.toSlashMention() + " with key `" + API_KEY.name() + "`";
            for (String key : db.getOrThrow(API_KEY)) {
                Integer nationId = Locutus.imp().getDiscordDB().getNationFromApiKey(key);
                if (nationId == null) throw new IllegalArgumentException(msg);
                DBNation nation = DBNation.getById(nationId);
                if (nation.getAlliancePosition() == null)
                    throw new IllegalArgumentException(msg + " (no position found for nation: " + nationId + ")");
                if (!nation.getAlliancePosition().hasPermission(AlliancePermission.SEE_SPIES))
                    throw new IllegalArgumentException(msg + " (nation: " + nationId + " does not have permission " + AlliancePermission.SEE_SPIES + ")");
                if (!aaIds.contains(nation.getAlliance_id()))
                    throw new IllegalArgumentException(msg + " (nation: " + nationId + " is not in your alliance: " + StringMan.getString(aaIds) + ")");

                PoliticsAndWarV3 api = new PoliticsAndWarV3(ApiKeyPool.create(nationId, key));
                try {
                    api.getApiKeyStats();
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(e.getMessage() + " (for nation: " + nationId + ")");
                }
            }
            PnwPusherShardManager pusher = Locutus.imp().getPusher();
            if (pusher == null) {
                throw new IllegalArgumentException("Pusher is not enabled. Please contact the bot owner.");
            }

            channel = validateChannel(db, channel);

            for (int aaId : aaIds) {
                DBAlliance aa = DBAlliance.get(aaId);
                if (aa != null) {
                    pusher.setupSpySubscriptions(db, aa);
                }
            }

            return channel;
        }

        @Override
        public String help() {
            return "The channel to get alerts when a member is spied";
        }
    }.setupRequirements(f -> f.requires(API_KEY).requires(ALLIANCE_ID).requireActiveGuild());
    public static GuildSetting<Boolean> MEMBER_CAN_SET_BRACKET = new GuildBooleanSetting(GuildSettingCategory.TAX) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String MEMBER_CAN_SET_BRACKET(@Me GuildDB db, @Me User user, boolean value) {
            return MEMBER_CAN_SET_BRACKET.setAndValidate(db, user, value);
        }

        @Override
        public String help() {
            return "Whether members can use " + CM.nation.set.taxbracket.cmd.toSlashMention();
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID).requires(API_KEY));
    public static GuildSetting<Boolean> MEMBER_CAN_OFFSHORE = new GuildBooleanSetting(GuildSettingCategory.BANK_ACCESS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String MEMBER_CAN_OFFSHORE(@Me GuildDB db, @Me User user, boolean value) {
            return MEMBER_CAN_OFFSHORE.setAndValidate(db, user, value);
        }

        @Override
        public boolean allowed(GuildDB db, boolean throwError) {
            if (super.allowed(db, throwError)) {
                if (db.getOffshoreDB() == null) {
                    if (throwError) {
                        throw new IllegalArgumentException("No offshore is set. See " + CM.offshore.add.cmd.toSlashMention());
                    }
                    return false;
                }
            }
            return true;
        }

        @Override
        public String help() {
            return "Whether members can use " + CM.offshore.send.cmd.toSlashMention() + " (true/false)";
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID).requires(API_KEY).requiresCoalition(Coalition.OFFSHORE).requiresOffshore());
    public static GuildSetting<String> RECRUIT_MESSAGE_SUBJECT = new GuildStringSetting(GuildSettingCategory.RECRUIT) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String RECRUIT_MESSAGE_SUBJECT(@Me GuildDB db, @Me User user, String value) {
            return RECRUIT_MESSAGE_SUBJECT.setAndValidate(db, user, value);
        }
        @Override
        public String validate(GuildDB db, String value) {
            if (value.length() >= 50)
                throw new IllegalArgumentException("Your subject line cannot be longer than 50 characters.");
            return value;
        }

        @Override
        public String help() {
            return "The recruit message subject\n" +
                    "Must also set " + RECRUIT_MESSAGE_CONTENT.getCommandMention();
        }
    }.setupRequirements(f -> f.requires(API_KEY).requires(ALLIANCE_ID).requireValidAlliance());
    public static GuildSetting<String> RECRUIT_MESSAGE_CONTENT = new GuildStringSetting(GuildSettingCategory.RECRUIT) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String RECRUIT_MESSAGE_CONTENT(@Me GuildDB db, @Me User user, String message) {
            return RECRUIT_MESSAGE_CONTENT.setAndValidate(db, user, message);
        }
        @Override
        public String help() {
            return "The recruit message body\n" +
                    "Must also set " + RECRUIT_MESSAGE_OUTPUT.getCommandMention();
        }
    }.setupRequirements(f -> f.requireValidAlliance().requires(RECRUIT_MESSAGE_SUBJECT).requires(ALLIANCE_ID).requires(API_KEY));
    public static GuildSetting<MessageChannel> RECRUIT_MESSAGE_OUTPUT = new GuildChannelSetting(GuildSettingCategory.RECRUIT) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String RECRUIT_MESSAGE_OUTPUT(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return RECRUIT_MESSAGE_OUTPUT.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive recruitment message output";
        }
    }.setupRequirements(f -> f.requires(RECRUIT_MESSAGE_SUBJECT).requires(RECRUIT_MESSAGE_CONTENT).requires(API_KEY).requireValidAlliance().requireActiveGuild());
    public static GuildSetting<Long> RECRUIT_MESSAGE_DELAY = new GuildLongSetting(GuildSettingCategory.RECRUIT) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String RECRUIT_MESSAGE_DELAY(@Me GuildDB db, @Me User user, @Timediff Long timediff) {
            if (timediff < 60) {
                return "The interval must be at least 1 minute";
            }
            if (timediff > TimeUnit.HOURS.toMillis(2)) {
                return "The interval must be less than 2 hours";
            }
            return RECRUIT_MESSAGE_DELAY.setAndValidate(db, user, timediff);
        }

        // fix legacy
        @Override
        public Long parse(GuildDB db, String input) {
            // if info contains letters
            if (input.matches(".*[a-zA-Z]+.*")) {
                input = "" + (TimeUtil.timeToSec(input) * 1000);
            }
            return super.parse(db, input);
        }

        @Override
        public String toReadableString(Long value) {
            return TimeUtil.secToTime(TimeUnit.MILLISECONDS, value);
        }

        @Override
        public String help() {
            return "The amount of time to delay recruitment messages by";
        }

    }.setupRequirements(f -> f.requireValidAlliance().requires(RECRUIT_MESSAGE_OUTPUT).requires(ALLIANCE_ID));
    public static GuildSetting<Map<NationFilter, TaxRate>> REQUIRED_INTERNAL_TAXRATE = new GuildSetting<Map<NationFilter, TaxRate>>(GuildSettingCategory.TAX, Map.class, NationFilter.class, TaxRate.class) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String addRequiredInternalTaxrate(@Me GuildDB db, @Me User user, NationFilter filter, TaxRate bracket) {
            Map<NationFilter, TaxRate> existing = REQUIRED_INTERNAL_TAXRATE.getOrNull(db, false);
            existing = existing == null ? new HashMap<>() : new LinkedHashMap<>(existing);
            existing.put(filter, bracket);
            return REQUIRED_INTERNAL_TAXRATE.setAndValidate(db, user, existing);
        }

        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String REQUIRED_INTERNAL_TAXRATE(@Me GuildDB db, @Me User user, Map<NationFilter, TaxRate> value) {
            return REQUIRED_INTERNAL_TAXRATE.setAndValidate(db, user, value);
        }

        @Override
        public String toString(Map<NationFilter, TaxRate> filterToTaxRate) {
            StringBuilder result = new StringBuilder();
            for (Map.Entry<NationFilter, TaxRate> entry : filterToTaxRate.entrySet()) {
                result.append(entry.getKey().getFilter() + ":" + entry.getValue() + "\n");
            }
            return result.toString().trim();
        }

        @Override
        public String help() {
            String response = "This setting maps nation filters to internal tax rate for bulk automation.\n" +
                    "To list nations current rates: " + CM.tax.listBracketAuto.cmd.toSlashMention() + "\n" +
                    "To bulk move nations: " + CM.nation.set.taxinternalAuto.cmd.toSlashMention() + "\n" +
                    "Tax rate is in the form: `money/rss`\n" +
                    "In the form: \n" +
                    "```\n" +
                    "#cities<10:100/100\n" +
                    "#cities>=10:25/25" +
                    "\n```\n" +
                    "All nation filters are supported (e.g. roles)\n" +
                    "Priority is first to last (so put defaults at the bottom)";

            return response;
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID).requires(API_KEY));
    public static GuildSetting<Map<NationFilter, Integer>> REQUIRED_TAX_BRACKET = new GuildSetting<Map<NationFilter, Integer>>(GuildSettingCategory.TAX, Map.class, NationFilter.class, Integer.class) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String addRequiredBracket(@Me GuildDB db, @Me User user, NationFilter filter, TaxBracket bracket) {
            Map<NationFilter, Integer> existing = REQUIRED_TAX_BRACKET.getOrNull(db, false);
            existing = existing == null ? new HashMap<>() : new LinkedHashMap<>(existing);
            existing.put(filter, bracket.getId());
            return REQUIRED_TAX_BRACKET.setAndValidate(db, user, existing);
        }
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String REQUIRED_TAX_BRACKET(@Me GuildDB db, @Me User user, Map<NationFilter, Integer> brackets) {
            return REQUIRED_TAX_BRACKET.setAndValidate(db, user, brackets);
        }

        @Override
        public String toString(Map<NationFilter, Integer> filterToBracket) {
            StringBuilder result = new StringBuilder();
            for (Map.Entry<NationFilter, Integer> entry : filterToBracket.entrySet()) {
                result.append(entry.getKey().getFilter() + ":" + entry.getValue() + "\n");
            }
            return result.toString().trim();
        }

        @Override
        public String help() {
            String response = "This setting maps nation filters to tax bracket for bulk automation.\n" +
                    "To list nations current rates: " + CM.tax.listBracketAuto.cmd.toSlashMention() + "\n" +
                    "To bulk move nations: " + CM.nation.set.taxbracketAuto.cmd.toSlashMention() + "\n" +
                    "In the form: \n" +
                    "```\n" +
                    "#cities<10:1234\n" +
                    "#cities>=10:2345" +
                    "\n```\n" +
                    "All nation filters are supported (e.g. roles)\n" +
                    "Priority is first to last (so put defaults at the bottom)";

            return response;
        }

        @Override
        public Map<NationFilter, Integer> validate(GuildDB db, Map<NationFilter, Integer> parsed) {

            AllianceList alliance = db.getAllianceList();
            if (alliance == null || alliance.isEmpty())
                throw new IllegalArgumentException("No valid `!KeyStore ALLIANCE_ID` set");

            Map<Integer, TaxBracket> brackets = alliance.getTaxBrackets(false);
            if (brackets.isEmpty())
                throw new IllegalArgumentException("Could not fetch tax brackets. Is `!KeyStore API_KEY` correct?");

            for (Map.Entry<NationFilter, Integer> entry : parsed.entrySet()) {
                if (!brackets.containsKey(entry.getValue())) {
                    throw new IllegalArgumentException("No tax bracket founds for id: " + entry.getValue());
                }
            }

            return parsed;
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID).requires(API_KEY));
    public static GuildSetting<Map<ResourceType, Double>> WARCHEST_PER_CITY = new GuildResourceSetting(GuildSettingCategory.AUDIT) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String WARCHEST_PER_CITY(@Me GuildDB db, @Me User user, Map<ResourceType, Double> amount) {
            return WARCHEST_PER_CITY.setAndValidate(db, user, amount);
        }

        @Override
        public String toReadableString(Map<ResourceType, Double> value) {
            return PnwUtil.resourcesToString(value);
        }

        @Override
        public String help() {
            return "Amount of warchest to recommend per city in form `{steel=1234,aluminum=5678,gasoline=69,munitions=420}`";
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID).requireValidAlliance().requires(API_KEY));
    public static GuildSetting<Category> EMBASSY_CATEGORY = new GuildCategorySetting(GuildSettingCategory.FOREIGN_AFFAIRS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String EMBASSY_CATEGORY(@Me GuildDB db, @Me User user, Category category) {
            return EMBASSY_CATEGORY.setAndValidate(db, user, category);
        }
        @Override
        public String help() {
            return "The name or id of the CATEGORY you would like embassy channels created in (for " + CM.embassy.cmd.toSlashMention() + ")";
        }
    }.setupRequirements(f -> f.requireFunction(new Consumer<GuildDB>() {
        @Override
        public void accept(GuildDB db) {
            if (ALLIANCE_ID.getOrNull(db, true) == null) {
                for (GuildDB otherDb : Locutus.imp().getGuildDatabases().values()) {
                    Guild warServer = WAR_SERVER.getOrNull(otherDb, false);
                    GuildDB faServer = FA_SERVER.getOrNull(otherDb, false);
                    if (faServer != null && faServer.getIdLong() == db.getIdLong()) {
                        return;
                    }
                    if (warServer != null && warServer.getIdLong() == db.getIdLong()) {
                        return;
                    }
                }
                throw new IllegalArgumentException("Missing required setting " + ALLIANCE_ID.name() + " " + ALLIANCE_ID.getCommandMention() + "\n" +
                        "(Or set this server as an " + FA_SERVER.name() + " from another guild)");

            }
        }
    }));
    public static GuildSetting<Map<Role, Set<Role>>> ASSIGNABLE_ROLES = new GuildSetting<Map<Role, Set<Role>>>(GuildSettingCategory.ROLE, Map.class, Role.class, TypeToken.getParameterized(Set.class, Role.class).getType()) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String addAssignableRole(@Me GuildDB db, @Me User user, Role role, Set<Role> roles) {
            Map<Role, Set<Role>> existing = ASSIGNABLE_ROLES.getOrNull(db, false);
            existing = existing == null ? new HashMap<>() : new LinkedHashMap<>(existing);
            existing.put(role, roles);
            return ASSIGNABLE_ROLES.setAndValidate(db, user, existing);
        }


        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String ASSIGNABLE_ROLES(@Me GuildDB db, @Me User user, Map<Role, Set<Role>> value) {
            return ASSIGNABLE_ROLES.setAndValidate(db, user, value);
        }

        @Override
        public String toReadableString(Map<Role, Set<Role>> map) {
            List<String> lines = new ArrayList<>();
            for (Map.Entry<Role, Set<Role>> entry : map.entrySet()) {
                String key = entry.getKey().getName();
                List<String> valueStrings = entry.getValue().stream().map(f -> f.getName()).collect(Collectors.toList());
                String value = StringMan.join(valueStrings, ",");

                lines.add(key + ":" + value);
            }
            return StringMan.join(lines, "\n");
        }

        public String toString(Map<Role, Set<Role>> map) {
            List<String> lines = new ArrayList<>();
            for (Map.Entry<Role, Set<Role>> entry : map.entrySet()) {
                String key = entry.getKey().getAsMention();
                List<String> valueStrings = entry.getValue().stream().map(f -> f.getAsMention()).collect(Collectors.toList());
                String value = StringMan.join(valueStrings, ",");

                lines.add(key + ":" + value);
            }
            return StringMan.join(lines, "\n");
        }

        @Override
        public String help() {
            return "Map roles that can be assigned (or removed). See `" + CM.self.create.cmd.toSlashMention() + "` " + CM.role.removeAssignableRole.cmd.toSlashMention() + " " + CM.role.add.cmd.toSlashMention() + " " + CM.role.remove.cmd.toSlashMention();
        }
    };
    public static GuildSetting<MessageChannel> DEFENSE_WAR_CHANNEL = new GuildChannelSetting(GuildSettingCategory.WAR_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String DEFENSE_WAR_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return DEFENSE_WAR_CHANNEL.setAndValidate(db, user, channel);
        }

        @Override
        public String help() {
            return "The #channel to receive alerts for defensive wars";
        }
    }.setupRequirements(f -> f.requiresAllies().requireActiveGuild());
    public static GuildSetting<Boolean> SHOW_ALLY_DEFENSIVE_WARS = new GuildBooleanSetting(GuildSettingCategory.WAR_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String SHOW_ALLY_DEFENSIVE_WARS(@Me GuildDB db, @Me User user, boolean enabled) {
            return SHOW_ALLY_DEFENSIVE_WARS.setAndValidate(db, user, enabled);
        }
        @Override
        public String help() {
            return "Whether to show offensive war alerts for allies (true/false)";
        }
    }.setupRequirements(f -> f.requires(DEFENSE_WAR_CHANNEL).requiresCoalition(Coalition.ALLIES));
    public static GuildSetting<NationFilter> MENTION_MILCOM_FILTER = new GuildNationFilterSetting(GuildSettingCategory.WAR_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String MENTION_MILCOM_FILTER(@Me GuildDB db, @Me User user, NationFilter value) {
            return MENTION_MILCOM_FILTER.setAndValidate(db, user, value);
        }
        @Override
        public String help() {
            return "A nation filter to apply to limit what wars milcom gets pinged for. ";
        }
    }.setupRequirements(f -> f.requires(DEFENSE_WAR_CHANNEL));
    public static GuildSetting<Boolean> WAR_ALERT_FOR_OFFSHORES = new GuildBooleanSetting(GuildSettingCategory.WAR_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String WAR_ALERT_FOR_OFFSHORES(@Me GuildDB db, @Me User user, boolean enabled) {
            return WAR_ALERT_FOR_OFFSHORES.setAndValidate(db, user, enabled);
        }
        @Override
        public String help() {
            return "Whether to do war alerts for offshore alliances";
        }
    }.setupRequirements(f -> f.requiresCoalition(Coalition.OFFSHORE).requires(DEFENSE_WAR_CHANNEL));
    public static GuildSetting<MessageChannel> OFFENSIVE_WAR_CHANNEL = new GuildChannelSetting(GuildSettingCategory.WAR_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String OFFENSIVE_WAR_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return OFFENSIVE_WAR_CHANNEL.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive alerts for offensive wars";
        }
    }.setupRequirements(f -> f.requiresAllies().requireActiveGuild());
    public static GuildSetting<Boolean> SHOW_ALLY_OFFENSIVE_WARS = new GuildBooleanSetting(GuildSettingCategory.WAR_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String SHOW_ALLY_OFFENSIVE_WARS(@Me GuildDB db, @Me User user, boolean enabled) {
            return SHOW_ALLY_OFFENSIVE_WARS.setAndValidate(db, user, enabled);
        }
        @Override
        public String help() {
            return "Whether to show offensive war alerts for allies (true/false)";
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID).requires(OFFENSIVE_WAR_CHANNEL).requiresCoalition(Coalition.ALLIES));
    public static GuildSetting<Boolean> HIDE_APPLICANT_WARS = new GuildBooleanSetting(GuildSettingCategory.WAR_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String HIDE_APPLICANT_WARS(@Me GuildDB db, @Me User user, boolean value) {
            return HIDE_APPLICANT_WARS.setAndValidate(db, user, value);
        }
        @Override
        public String help() {
            return "Whether to hide war alerts for applicants";
        }
    }.setupRequirements(f -> f.requires(OFFENSIVE_WAR_CHANNEL));
    public static GuildSetting<MessageChannel> WAR_PEACE_ALERTS = new GuildChannelSetting(GuildSettingCategory.WAR_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String WAR_PEACE_ALERTS(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return WAR_PEACE_ALERTS.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive alerts for changes to any war peace offers";
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID));
    public static GuildSetting<MessageChannel> UNBLOCKADE_REQUESTS = new GuildChannelSetting(GuildSettingCategory.WAR_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String UNBLOCKADE_REQUESTS(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return UNBLOCKADE_REQUESTS.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive alerts for unblockade requests";
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID));
    public static GuildSetting<MessageChannel> BLOCKADED_ALERTS = new GuildChannelSetting(GuildSettingCategory.WAR_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String BLOCKADED_ALERTS(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return BLOCKADED_ALERTS.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive alerts for blockades";
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID));
    public static GuildSetting<MessageChannel> UNBLOCKADED_ALERTS = new GuildChannelSetting(GuildSettingCategory.WAR_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String UNBLOCKADED_ALERTS(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return UNBLOCKADED_ALERTS.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive alerts for unblockades";
        }
    }.setupRequirements(f -> f.requires(BLOCKADED_ALERTS).requires(ALLIANCE_ID));
    public static GuildSetting<Boolean> MEMBER_CAN_WITHDRAW = new GuildBooleanSetting(GuildSettingCategory.BANK_ACCESS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String MEMBER_CAN_WITHDRAW(@Me GuildDB db, @Me User user, boolean enabled) {
            return MEMBER_CAN_WITHDRAW.setAndValidate(db, user, enabled);
        }
        @Override
        public String help() {
            return "Whether members can use " + CM.transfer.resources.cmd.toSlashMention() + " or " + Settings.commandPrefix(true) + "grant` to access their own funds (true/false)";
        }
    }.setupRequirements(f -> f.requiresCoalition(Coalition.OFFSHORE).requiresOffshore());
    public static GuildSetting<Boolean> MEMBER_CAN_WITHDRAW_WARTIME = new GuildBooleanSetting(GuildSettingCategory.BANK_ACCESS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String MEMBER_CAN_WITHDRAW_WARTIME(@Me GuildDB db, @Me User user, boolean enabled) {
            return MEMBER_CAN_WITHDRAW_WARTIME.setAndValidate(db, user, enabled);
        }
        @Override
        public String help() {
            return "Whether members can withdraw during wartime (true/false)";
        }
    }.setupRequirements(f -> f.requiresCoalition(Coalition.OFFSHORE).requiresOffshore());
    public static GuildSetting<Boolean> MEMBER_CAN_WITHDRAW_IGNORES_GRANTS = new GuildBooleanSetting(GuildSettingCategory.BANK_ACCESS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String WITHDRAW_IGNORES_GRANTS(@Me GuildDB db, @Me User user, boolean enabled) {
            return MEMBER_CAN_WITHDRAW_IGNORES_GRANTS.setAndValidate(db, user, enabled);
        }

        @Override
        public String help() {
            return "Whether members's withdraw limit ignores their grants (true/false)";
        }
    }.setupRequirements(f -> f.requiresCoalition(Coalition.OFFSHORE).requiresOffshore());
    public static GuildSetting<Boolean> DISPLAY_ITEMIZED_DEPOSITS = new GuildBooleanSetting(GuildSettingCategory.BANK_INFO) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String DISPLAY_ITEMIZED_DEPOSITS(@Me GuildDB db, @Me User user, boolean enabled) {
            return DISPLAY_ITEMIZED_DEPOSITS.setAndValidate(db, user, enabled);
        }
        @Override
        public String help() {
            return "Whether members's deposits are displayed by default with a breakdown of each category (true/false)";
        }

    }.setupRequirements(f -> f.requiresRole(Roles.MEMBER, true));
    public static GuildSetting<MessageChannel> LOST_WAR_CHANNEL = new GuildChannelSetting(GuildSettingCategory.WAR_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String LOST_WAR_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return LOST_WAR_CHANNEL.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to post wars when our side loses a war";
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID));
    public static GuildSetting<MessageChannel> WON_WAR_CHANNEL = new GuildChannelSetting(GuildSettingCategory.WAR_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String WON_WAR_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return WON_WAR_CHANNEL.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to post wars when our side wins a war (only includes actives)";
        }

    }.setupRequirements(f -> f.requires(ALLIANCE_ID));
    public static GuildSetting<MessageChannel> DEPOSIT_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.BANK_INFO) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String DEPOSIT_ALERT_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return DEPOSIT_ALERT_CHANNEL.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive alerts when a nation makes a deposit (this will no longer reliably alert)";
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID));
    public static GuildSetting<MessageChannel> WITHDRAW_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.BANK_INFO) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String WITHDRAW_ALERT_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return WITHDRAW_ALERT_CHANNEL.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive alerts when a nation requests a transfer";
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID));
    public static GuildSetting<MessageChannel> ADDBALANCE_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.BANK_INFO) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String ADDBALANCE_ALERT_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return ADDBALANCE_ALERT_CHANNEL.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive alerts when balance is added";
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID));
    public static GuildSetting<MessageChannel> BANK_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.BANK_INFO) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String BANK_ALERT_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return BANK_ALERT_CHANNEL.setAndValidate(db, user, channel);
        }

        @Override
        public String help() {
            return "The #channel to receive alerts e.g. for custom `" + Settings.commandPrefix(true) + "BankAlerts`";
        }
    }.setupRequirements(f -> f.requireValidAlliance().requires(ALLIANCE_ID).requiresWhitelisted());
    public static GuildSetting<MessageChannel> REROLL_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.ORBIS_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String REROLL_ALERT_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return REROLL_ALERT_CHANNEL.setAndValidate(db, user, channel);
        }

        @Override
        public String help() {
            return "The #channel to receive alerts for nation rerolls";
        }
    }.nonPublic().requireActiveGuild();
    public static GuildSetting<MessageChannel> DELETION_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.ORBIS_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String DELETION_ALERT_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return DELETION_ALERT_CHANNEL.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The channel to receive alerts when a nation deletes (in all of orbis)";
        }
    }.nonPublic().requireActiveGuild();
    public static GuildSetting<GuildDB.AutoNickOption> AUTONICK = new GuildEnumSetting<GuildDB.AutoNickOption>(GuildSettingCategory.ROLE, GuildDB.AutoNickOption.class) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String AUTONICK(@Me GuildDB db, @Me User user, GuildDB.AutoNickOption mode) {
            return AUTONICK.setAndValidate(db, user, mode);
        }
        @Override
        public String help() {
            return "Options: " + StringMan.getString(GuildDB.AutoNickOption.values()) + "\n" +
                    "See also: " + CM.role.clearNicks.cmd.toSlashMention();
        }
    };
    public static GuildSetting<GuildDB.AutoRoleOption> AUTOROLE_ALLIANCES = new GuildEnumSetting<GuildDB.AutoRoleOption>(GuildSettingCategory.ROLE, GuildDB.AutoRoleOption.class) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String AUTOROLE_ALLIANCES(@Me GuildDB db, @Me User user, GuildDB.AutoRoleOption mode) {
            return AUTOROLE_ALLIANCES.setAndValidate(db, user, mode);
        }

        @Override
        public String help() {
            return "Options: " + StringMan.getString(GuildDB.AutoRoleOption.values()) + "\n" +
                    "See also:\n" +
                    "- " + CM.coalition.create.cmd.create(null, Coalition.MASKEDALLIANCES.name()) + "\n" +
                    "- " + CM.role.clearAllianceRoles.cmd.toSlashMention() + "\n" +
                    "- " + AUTOROLE_ALLIANCE_RANK.getCommandMention() + "\n" +
                    "- " + AUTOROLE_TOP_X.getCommandMention();
        }
    };
    public static GuildSetting<Rank> AUTOROLE_ALLIANCE_RANK = new GuildEnumSetting<Rank>(GuildSettingCategory.ROLE, Rank.class) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String AUTOROLE_ALLIANCE_RANK(@Me GuildDB db, @Me User user, Rank allianceRank) {
            return AUTOROLE_ALLIANCE_RANK.setAndValidate(db, user, allianceRank);
        }
        @Override
        public String help() {
            return "The ingame rank required to get an alliance role. (default: member) Options: " + StringMan.getString(Rank.values());
        }
    }.setupRequirements(f -> f.requires(AUTOROLE_ALLIANCES));
    public static GuildSetting<Integer> AUTOROLE_TOP_X = new GuildIntegerSetting(GuildSettingCategory.ROLE) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String AUTOROLE_TOP_X(@Me GuildDB db, @Me User user, Integer topScoreRank) {
            return AUTOROLE_TOP_X.setAndValidate(db, user, topScoreRank);
        }
        @Override
        public String help() {
            return "The number of top alliances to provide roles for, defaults to `0`";
        }
    }.setupRequirements(f -> f.requires(AUTOROLE_ALLIANCES));
    public static GuildSetting<Integer> DO_NOT_RAID_TOP_X = new GuildIntegerSetting(GuildSettingCategory.FOREIGN_AFFAIRS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String DO_NOT_RAID_TOP_X(@Me GuildDB db, @Me User user, Integer topAllianceScore) {
            return DO_NOT_RAID_TOP_X.setAndValidate(db, user, topAllianceScore);
        }
        @Override
        public String help() {
            return "The number of top alliances to include in the Do Not Raid (DNR) list\n" +
                    "Members are not permitted to declare on members of these alliances or their direct allies\n" +
                    "Results in the DNR will be excluded from commands, and will alert Foreign Affairs if violated\n" +
                    "Defaults to `0`";
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID));
    public static GuildSetting<Boolean> AUTOROLE_ALLY_GOV = new GuildBooleanSetting(GuildSettingCategory.ROLE) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String AUTOROLE_ALLY_GOV(@Me GuildDB db, @Me User user, boolean enabled) {
            return AUTOROLE_ALLY_GOV.setAndValidate(db, user, enabled);
        }
        @Override
        public String help() {
            return "Whether to give gov/member roles to allies (this is intended for coalition servers), `true` or `false`";
        }
    }.setupRequirements(f -> f.requires(AUTOROLE_ALLIANCES).requiresCoalition(Coalition.ALLIES).requiresNot(ALLIANCE_ID));
    public static GuildSetting<Set<Roles>> AUTOROLE_ALLY_ROLES = new GuildEnumSetSetting<Roles>(GuildSettingCategory.ROLE, Roles.class) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String AUTOROLE_ALLY_ROLES(@Me GuildDB db, @Me User user, Set<Roles> roles) {
            return AUTOROLE_ALLY_ROLES.setAndValidate(db, user, roles);
        }
        @Override
        public String toString(Set<Roles> value) {
            return StringMan.join(value.stream().map(f -> f.name()).collect(Collectors.toList()), ",");
        }

        @Override
        public String help() {
            return "List of roles to autorole from ally servers\n" +
                    "(this is intended for coalition servers to give gov roles to allies)";
        }
    }.setupRequirements(f -> f.requires(AUTOROLE_ALLY_GOV).requiresCoalition(Coalition.ALLIES).requiresNot(ALLIANCE_ID));
    public static GuildSetting<Boolean> ENABLE_WAR_ROOMS = new GuildBooleanSetting(GuildSettingCategory.WAR_ROOM) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String ENABLE_WAR_ROOMS(@Me GuildDB db, @Me User user, boolean enabled) {
            return ENABLE_WAR_ROOMS.setAndValidate(db, user, enabled);
        }

        @Override
        public Boolean parse(GuildDB db, String input) {
            db.warChannelInit = false;
            return super.parse(db, input);
        }

        @Override
        public String help() {
            return "If war rooms should be enabled (i.e. auto generate a channel for wars against active nations)\n" +
                    "Note: Defensive war channels must be enabled to have auto war room creation";
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID));
    public static GuildSetting<Guild> WAR_SERVER = new GuildSetting<Guild>(GuildSettingCategory.WAR_ROOM, Guild.class) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String WAR_SERVER(@Me GuildDB db, @Me User user, Guild guild) {
            return WAR_SERVER.setAndValidate(db, user, guild);
        }


        @Override
        public String toString(Guild value) {
            return value.getId();
        }

        @Override
        public String toReadableString(Guild value) {
            return value.toString();
        }

        @Override
        public Guild validate(GuildDB db, Guild guild) {
            GuildDB otherDb = Locutus.imp().getGuildDB(guild);
            if (guild.getIdLong() == db.getGuild().getIdLong())
                throw new IllegalArgumentException("Use " + CM.settings.delete.cmd.create(GuildKey.WAR_SERVER.name()) + " to unset the war server");
            if (otherDb.getOrNull(GuildKey.WAR_SERVER) != null)
                throw new IllegalArgumentException("Circular reference. The server you have set already defers its war room");
            return guild;
        }

        @Override
        public boolean hasPermission(GuildDB db, User author, Guild guild) {
            if (!super.hasPermission(db, author, guild)) return false;
            if (guild != null && !Roles.ADMIN.has(author, guild))
                throw new IllegalArgumentException("You do not have ADMIN on " + guild);
            return true;
        }

        @Override
        public String help() {
            return "The guild to defer war rooms to";
        }
    }.setupRequirements(f -> f.requires(ENABLE_WAR_ROOMS).requires(ALLIANCE_ID));
    public static GuildSetting<Map.Entry<Integer, Long>> DELEGATE_SERVER = new GuildSetting<Map.Entry<Integer, Long>>(GuildSettingCategory.DEFAULT, Map.class, Integer.class, Long.class) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String DELEGATE_SERVER(@Me GuildDB db, @Me User user, Guild guild) {
            return DELEGATE_SERVER.setAndValidate(db, user, Map.entry(0, guild.getIdLong()));
        }

        @Override
        public Map.Entry<Integer, Long> validate(GuildDB db, Map.Entry<Integer, Long> ids) {
            if (db.getOrNull(ALLIANCE_ID) != null) {
                throw new IllegalArgumentException("You cannot delegate a server with an alliance id set");
            }
            Guild guild = Locutus.imp().getDiscordApi().getGuildById(ids.getValue());
            if (guild == null)
                throw new IllegalArgumentException("Invalid guild: `" + ids.getValue() + "` (are you sure locutus is in that server?)");
            GuildDB otherDb = Locutus.imp().getGuildDB(guild);
            if (guild.getIdLong() == db.getIdLong())
                throw new IllegalArgumentException("You cannot set the delegate as this guild");
            if (DELEGATE_SERVER.has(otherDb, false)) {
                throw new IllegalArgumentException("Circular reference. The server you have set already delegates its DELEGATE_SERVER");
            }
            return ids;
        }

        @Override
        public boolean hasPermission(GuildDB db, User author, Map.Entry<Integer, Long> entry) {
            if (!super.hasPermission(db, author, entry)) return false;
            if (entry == null) return true;
            GuildDB otherDB = Locutus.imp().getGuildDB(entry.getValue());
            if (otherDB == null) {
                throw new IllegalArgumentException("Invalid guild: `" + entry.getValue() + "` (are you sure locutus is in that server?)");
            }
            if (!Roles.ADMIN.has(author, otherDB.getGuild()))
                throw new IllegalArgumentException("You do not have ADMIN on " + otherDB.getGuild());
            return true;
        }

        @Override
        public Map.Entry<Integer, Long> parse(GuildDB db, String input) {
            String[] split2 = input.trim().split("[:|=]", 2);
            Map.Entry<Integer, Long> entry;
            if (split2.length == 2) {
                return Map.entry(Integer.parseInt(split2[0]), Long.parseLong(split2[1]));
            } else {
                return Map.entry(0, Long.parseLong(input));
            }
        }

        @Override
        public String toString(Map.Entry<Integer, Long> value) {
            Map.Entry<Integer, Long> pair = value;
            if (pair.getKey() == 0) return String.valueOf(pair.getValue());
            return pair.getKey() + ":" + pair.getValue();
        }

        @Override
        public String help() {
            return "The guild to delegate unset settings to";
        }
    };
    public static GuildSetting<GuildDB> FA_SERVER = new GuildSetting<GuildDB>(GuildSettingCategory.FOREIGN_AFFAIRS, GuildDB.class) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String FA_SERVER(@Me GuildDB db, @Me User user, Guild guild) {
            GuildDB otherDb = Locutus.imp().getGuildDB(guild);
            return FA_SERVER.setAndValidate(db, user, otherDb);
        }

        @Override
        public GuildDB validate(GuildDB db, GuildDB otherDb) {
            if (otherDb.getIdLong() == db.getGuild().getIdLong())
                throw new IllegalArgumentException("Use " + CM.settings.delete.cmd.create(FA_SERVER.name()) + " to unset the FA_SERVER");
            if (FA_SERVER.has(otherDb, false))
                throw new IllegalArgumentException("Circular reference. The server you have set already defers its FA_SERVER");
            return otherDb;
        }

        @Override
        public boolean hasPermission(GuildDB db, User author, GuildDB otherDB) {
            if (!super.hasPermission(db, author, otherDB)) return false;
            if (otherDB != null && !Roles.ADMIN.has(author, otherDB.getGuild()))
                throw new IllegalArgumentException("You do not have ADMIN on " + otherDB.getGuild());
            return true;
        }

        @Override
        public String toString(GuildDB value) {
            return value.getIdLong() + "";
        }

        @Override
        public String toReadableString(GuildDB value) {
            return value.getName();
        }

        @Override
        public String help() {
            return "The guild to defer coalitions to";
        }
    };
    public static GuildSetting<Boolean> RESOURCE_CONVERSION = new GuildBooleanSetting(GuildSettingCategory.BANK_ACCESS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String RESOURCE_CONVERSION(@Me GuildDB db, @Me User user, boolean enabled) {
            return RESOURCE_CONVERSION.setAndValidate(db, user, enabled);
        }
        @Override
        public String help() {
            return "If the alliance can convert resources to cash.\n" +
                    "This is done virtually in " + CM.deposits.check.cmd.toSlashMention() +
                    "Resources are converted using market average\n" +
                    "Use `#cash` as the note when depositing or transferring funds";
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID));
    public static GuildSetting<Boolean> DEPOSIT_INTEREST = new GuildBooleanSetting(GuildSettingCategory.BANK_INFO) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String DEPOSIT_INTEREST(@Me GuildDB db, @Me User user, boolean enabled) {
            return DEPOSIT_INTEREST.setAndValidate(db, user, enabled);
        }
        @Override
        public String help() {
            return "If members should expect to receive interest on their deposits\n" +
                    "To add interest you must manually run: " + CM.deposits.interest.cmd.toSlashMention();
        }
    }.setupRequirements(f -> f.requiresWhitelisted());
    public static GuildSetting<MessageChannel> TRADE_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.TRADE) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String TRADE_ALERT_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return TRADE_ALERT_CHANNEL.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive alerts for trades";
        }
    }.setupRequirements(f -> f.requiresWhitelisted().requireActiveGuild());
    public static GuildSetting<MessageChannel> BEIGE_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.BEIGE_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String BEIGE_ALERT_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return BEIGE_ALERT_CHANNEL.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive alerts when a raid target leaves beige.\n" + CM.role.setAlias.cmd.create(Roles.BEIGE_ALERT.name(), null, null, null) + " must also be set and have members in range";
        }
    }.setupRequirements(f -> f.requireValidAlliance().requires(ALLIANCE_ID).requiresWhitelisted().requireActiveGuild());
    public static GuildSetting<MessageChannel> ENEMY_BEIGED_ALERT = new GuildChannelSetting(GuildSettingCategory.BEIGE_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String ENEMY_BEIGED_ALERT(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return ENEMY_BEIGED_ALERT.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive alerts when an enemy gets beiged";
        }
    }.setupRequirements(f -> f.requireValidAlliance().requiresCoalition(Coalition.ENEMIES));
    public static GuildSetting<MessageChannel> ENEMY_BEIGED_ALERT_VIOLATIONS = new GuildChannelSetting(GuildSettingCategory.BEIGE_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String ENEMY_BEIGED_ALERT_VIOLATIONS(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return ENEMY_BEIGED_ALERT_VIOLATIONS.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive alerts when an enemy gets beiged (without reason)";
        }
    }.setupRequirements(f -> f.requiresCoalition(Coalition.ENEMIES).requires(ALLIANCE_ID).requireValidAlliance().requireActiveGuild());
    public static GuildSetting<Map<CityRanges, Set<BeigeReason>>> ALLOWED_BEIGE_REASONS = new GuildSetting<Map<CityRanges, Set<BeigeReason>>>(GuildSettingCategory.BEIGE_ALERTS, Map.class, CityRanges.class, TypeToken.getParameterized(Set.class, BeigeReason.class).getType()) {

        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String setBeigeReasons(@Me GuildDB db, @Me User user, Map<CityRanges, Set<BeigeReason>> reasons) {
            return ALLOWED_BEIGE_REASONS.setAndValidate(db, user, reasons);
        }

        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String addBeigeReasons(@Me GuildDB db, @Me User user, CityRanges range, Set<BeigeReason> reasons) {
            Map<CityRanges, Set<BeigeReason>> existing = ALLOWED_BEIGE_REASONS.getOrNull(db, false);
            existing = existing == null ? new HashMap<>() : new LinkedHashMap<>(existing);
            // print if there is overlap with ranges
            StringBuilder response = new StringBuilder();
            for (CityRanges existRange : existing.keySet()) {
                if (existRange.equals(range)) {
                    response.append("Replacing previous value: `" + StringMan.join(existing.get(range).stream().map(f -> f.name()).collect(Collectors.toSet()), ", ") + "`\n");
                }
                else if (existRange.overlaps(range)) {
                    response.append("City range `" + range + "` overlaps with existing `" + range + "`. You may want to remove it\n" +
                            CM.settings_beige_alerts.removeBeigeReasons.cmd.toSlashMention());
                }
            }
            allowedAndValidate(db, user, Collections.singletonMap(range, reasons));
            existing.put(range, reasons);
            return response.toString() + set(db, existing);
        }

        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String removeBeigeReasons(@Me GuildDB db, @Me User user, CityRanges range) {
            Map<CityRanges, Set<BeigeReason>> existing = ALLOWED_BEIGE_REASONS.getOrNull(db, false);
            if (existing == null || existing.isEmpty()) return "No value is set. Set with: " + getCommandMention();
            if (!existing.containsKey(range)) return "No value is set for range `" + range + "`. Set with " + getCommandMention();
            existing.remove(range);
            return set(db, existing);
        }

        @Override
        public String toString(Map<CityRanges, Set<BeigeReason>> obj) {
            StringBuilder result = new StringBuilder();
            for (Map.Entry<CityRanges, Set<BeigeReason>> entry : obj.entrySet()) {
                result.append(entry.getKey().toString() + ":" + StringMan.join(entry.getValue(), ",")).append("\n");
            }
            return result.toString().trim();
        }

        @Override
        public String help() {
            StringBuilder response = new StringBuilder("A list of city ranges to beige reasons that are permitted.\n" +
                    "In the form:\n" +
                    "```\n" +
                    "c1-9:*\n" +
                    "c10+:INACTIVE,VACATION_MODE,APPLICANT\n" +
                    "```\n").append(" Options:\n");
            for (BeigeReason value : BeigeReason.values()) {
                response.append("- " + value.name() + ": " + value.getDescription()).append("\n");
            }
            response.append("\nAlso set: " + CM.coalition.create.cmd.toSlashMention() + " with " + Coalition.ENEMIES);
            return response.toString();
        }
    }.setupRequirements(f -> f.requires(ENEMY_BEIGED_ALERT_VIOLATIONS).requireValidAlliance());

    public static GuildSetting<Map<NationFilter, MMRMatcher>> REQUIRED_MMR = new GuildSetting<Map<NationFilter, MMRMatcher>>(GuildSettingCategory.AUDIT, Map.class, NationFilter.class, MMRMatcher.class) {

        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String REQUIRED_MMR(@Me GuildDB db, @Me User user, Map<NationFilter, MMRMatcher> mmrMap) {
            return REQUIRED_MMR.setAndValidate(db, user, mmrMap);
        }

        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String addRequiredMMR(@Me GuildDB db, @Me User user, NationFilter filter, MMRMatcher mmr) {
            Map<NationFilter, MMRMatcher> existing = REQUIRED_MMR.getOrNull(db, false);
            existing = existing == null ? new HashMap<>() : new LinkedHashMap<>(existing);
            existing.put(filter, mmr);
            return REQUIRED_MMR.setAndValidate(db, user, existing);
        }


        @Override
        public String toString(Map<NationFilter, MMRMatcher> filterToMMR) {
            StringBuilder result = new StringBuilder();
            for (Map.Entry<NationFilter, MMRMatcher> entry : filterToMMR.entrySet()) {
                result.append(entry.getKey().getFilter() + ":" + entry.getValue() + "\n");
            }
            return result.toString().trim();
        }

        @Override
        public String help() {
            String response = "A list of filters to required MMR.\n" +
                    "In the form:\n" +
                    "```\n" +
                    "#cities<10:505X\n" +
                    "#cities>=10:0250\n" +
                    "```\n" +
                    "All nation filters are supported";

            return response;
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID));
    public static GuildSetting<MessageChannel> ENEMY_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.BEIGE_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String ENEMY_ALERT_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return ENEMY_ALERT_CHANNEL.setAndValidate(db, user, channel);
        }

        @Override
        public String help() {
            return "The #channel to receive alerts when an enemy nation leaves beige";
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID).requiresCoalition(Coalition.ENEMIES).requireValidAlliance().requireActiveGuild());
    public static GuildSetting<EnemyAlertChannelMode> ENEMY_ALERT_CHANNEL_MODE = new GuildEnumSetting<EnemyAlertChannelMode>(GuildSettingCategory.BEIGE_ALERTS, EnemyAlertChannelMode.class) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String ENEMY_ALERT_CHANNEL_MODE(@Me GuildDB db, @Me User user, EnemyAlertChannelMode mode) {
            return ENEMY_ALERT_CHANNEL_MODE.setAndValidate(db, user, mode);
        }
        @Override
        public String help() {
            return "The mode for the enemy alert channel to determine what alerts are posted and who is pinged\n" +
                    "Options:\n- " + StringMan.join(EnemyAlertChannelMode.values(), "\n- ");
        }
    }.setupRequirements(f -> f.requires(ENEMY_ALERT_CHANNEL));

    public static GuildSetting<NationFilter> ENEMY_ALERT_FILTER = new GuildSetting<NationFilter>(GuildSettingCategory.BEIGE_ALERTS, NationFilter.class) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String ENEMY_ALERT_FILTER(@Me GuildDB db, @Me User user, NationFilter filter) {
            return ENEMY_ALERT_FILTER.setAndValidate(db, user, filter);
        }

        @Override
        public String toString(NationFilter value) {
            return value.getFilter();
        }

        @Override
        public String help() {
            return "A filter for enemies to alert on when they leave beige\n" +
                    "Defaults to `#active_m<7200` (active in the past 5 days)";
        }
    }.setupRequirements(f -> f.requires(ENEMY_ALERT_CHANNEL));

    public static GuildSetting<MessageChannel> BOUNTY_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.BOUNTY) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String BOUNTY_ALERT_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return BOUNTY_ALERT_CHANNEL.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The channel to receive alerts when a bounty is placed";
        }
    }.setupRequirements(f -> f.requireValidAlliance().requireActiveGuild());
    public static GuildSetting<MessageChannel> TREASURE_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.BOUNTY) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String TREASURE_ALERT_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return TREASURE_ALERT_CHANNEL.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The channel to receive alerts when a bounty is placed";
        }
    }.setupRequirements(f -> f.requireValidAlliance().requireActiveGuild());
    public static GuildSetting<MessageChannel> MEMBER_REBUY_INFRA_ALERT = new GuildChannelSetting(GuildSettingCategory.AUDIT) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String MEMBER_REBUY_INFRA_ALERT(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return MEMBER_REBUY_INFRA_ALERT.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The channel to receive alerts when a member buys infra";
        }
    }.setupRequirements(f -> f.requireValidAlliance());
    public static GuildSetting<MessageChannel> MEMBER_LEAVE_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.AUDIT) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String MEMBER_LEAVE_ALERT_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return MEMBER_LEAVE_ALERT_CHANNEL.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The channel to receive alerts when a member leaves";
        }
    }.setupRequirements(f -> f.requireValidAlliance());
    //        public static GuildSetting<MessageChannel> LOW_TIER_BUY_CITY_ALERTS = new GuildChannelSetting(GuildSettingCategory.AUDIT) {
//            @Override
//            public String help() {
//                return "The channel to receive alerts when a <c10 member buys a city";
//            }
//        }.setupRequirements(f -> f.requireValidAlliance());
    public static GuildSetting<MessageChannel> INTERVIEW_INFO_SPAM = new GuildChannelSetting(GuildSettingCategory.INTERVIEW) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String INTERVIEW_INFO_SPAM(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return INTERVIEW_INFO_SPAM.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The channel to receive info spam about expired interview channels";
        }
    }.setupRequirements(f -> f.requireValidAlliance());
    public static GuildSetting<MessageChannel> INTERVIEW_PENDING_ALERTS = new GuildChannelSetting(GuildSettingCategory.INTERVIEW) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String INTERVIEW_PENDING_ALERTS(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return INTERVIEW_PENDING_ALERTS.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The channel to receive alerts when a member requests an interview";
        }
    }.setupRequirements(f -> f.requireValidAlliance().requireFunction(db -> {
        Role interviewerRole = Roles.INTERVIEWER.toRole(db.getGuild());
        if (interviewerRole == null) interviewerRole = Roles.MENTOR.toRole(db.getGuild());
        if (interviewerRole == null) interviewerRole = Roles.INTERNAL_AFFAIRS_STAFF.toRole(db.getGuild());
        if (interviewerRole == null) interviewerRole = Roles.INTERNAL_AFFAIRS.toRole(db.getGuild());
        if (interviewerRole == null) {
            throw new IllegalArgumentException("Please use: " + CM.role.setAlias.cmd.toSlashMention() + " to set at least ONE of the following:\n" +
                    StringMan.join(Arrays.asList(Roles.INTERVIEWER, Roles.MENTOR, Roles.INTERNAL_AFFAIRS_STAFF, Roles.INTERNAL_AFFAIRS), ", "));
        }
    }));
    public static GuildSetting<Category> ARCHIVE_CATEGORY = new GuildCategorySetting(GuildSettingCategory.INTERVIEW) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String ARCHIVE_CATEGORY(@Me GuildDB db, @Me User user, Category category) {
            return ARCHIVE_CATEGORY.setAndValidate(db, user, category);
        }
        @Override
        public String help() {
            return "The name or id of the CATEGORY you would like " + CM.channel.close.current.cmd.toSlashMention() + " to move channels to";
        }
    }.setupRequirements(f -> f.requireValidAlliance().requires(INTERVIEW_PENDING_ALERTS));
    public static GuildSetting<Map<Long, MessageChannel>> RESOURCE_REQUEST_CHANNEL = new GuildSetting<Map<Long, MessageChannel>>(GuildSettingCategory.BANK_ACCESS, Map.class, Long.class, MessageChannel.class) {

        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String removeResourceChannel(@Me GuildDB db, @Me User user, @Me MessageChannel channel) {
            Map<Long, MessageChannel> existing = RESOURCE_REQUEST_CHANNEL.getOrNull(db, false);
            existing = existing == null ? new HashMap<>() : new LinkedHashMap<>(existing);
            if (!existing.values().contains(channel)) {
                return "This channel is not set as a resource request channel";
            }
            existing.entrySet().removeIf(f -> f.getValue().equals(channel));
            if (existing.isEmpty()) {
                return RESOURCE_REQUEST_CHANNEL.delete(db, user);
            }
            return RESOURCE_REQUEST_CHANNEL.set(db, existing);
        }
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String addResourceChannel(@Me GuildDB db, @Me User user, @Me MessageChannel channel, @Default DBAlliance alliance) {
            Map<Long, MessageChannel> existing = RESOURCE_REQUEST_CHANNEL.getOrNull(db, false);
            existing = existing == null ? new HashMap<>() : new LinkedHashMap<>(existing);
            existing.put(alliance == null ? 0L : alliance.getId(), channel);
            return RESOURCE_REQUEST_CHANNEL.setAndValidate(db, user, existing);
        }
//
//        @NoFormat
//        @Command(descMethod = "help")
//        @RolePermission(Roles.ADMIN)
//        public String RESOURCE_REQUEST_CHANNEL(@Me GuildDB db, @Me User user, Map<Long, MessageChannel> channels) {
//            return RESOURCE_REQUEST_CHANNEL.setAndValidate(db, user, channels);
//        }

        @Override
        public Map<Long, MessageChannel> validate(GuildDB db, Map<Long, MessageChannel> parsed) {
            if (!parsed.containsKey(0L))
                throw new IllegalArgumentException("You must first specify a default channel (e.g. `0:#channel`)");
            return parsed;
        }

        @Override
        public Map<Long, MessageChannel> parse(GuildDB db, String input) {
            Map<Long, MessageChannel> parsed = new HashMap<>();
            for (String line : input.split("[\n;,]")) {
                String[] split = line.split("[:=]", 2);
                long id = split.length == 1 ? 0 : Long.parseLong(split[0]);
                MessageChannel channel = DiscordUtil.getChannel(db.getGuild(), split[split.length - 1]);
                if (channel != null) {
                    parsed.put(id, channel);
                }
            }
            return parsed.isEmpty() ? null : parsed;
        }

        @Override
        public String toString(Map<Long, MessageChannel> parsed) {
            List<String> mentions = new ArrayList<>();
            for (Map.Entry<Long, MessageChannel> entry : parsed.entrySet()) {
                if (entry.getKey() == 0) mentions.add(entry.getValue().getAsMention());
                else mentions.add(entry.getKey() + ":" + entry.getValue().getAsMention());
            }
            return StringMan.join(mentions, "\n");
        }

        @Override
        public String help() {
            return "The #channel for users to request resources in.\n" +
                    "For multiple alliances, use the form:\n```\n" +
                    "#defaultChannel\n" +
                    "alliance1:#channel\n" +
                    "```\n";
        }
    }.setupRequirements(f -> f.requiresCoalition(Coalition.OFFSHORE).requiresOffshore());
    public static GuildSetting<MessageChannel> GRANT_REQUEST_CHANNEL = new GuildChannelSetting(GuildSettingCategory.BANK_INFO) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String GRANT_REQUEST_CHANNEL(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return GRANT_REQUEST_CHANNEL.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel for users to request grants in";
        }
    }.setupRequirements(f -> f.requireValidAlliance().requiresWhitelisted().requiresRole(Roles.ECON_GRANT_SELF, true).requiresOffshore());
    public static GuildSetting<MessageChannel> TREATY_ALERTS = new GuildChannelSetting(GuildSettingCategory.FOREIGN_AFFAIRS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String TREATY_ALERTS(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return TREATY_ALERTS.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive alerts for treaty changes";
        }
    }.setupRequirements(f -> f.requireActiveGuild());
    public static GuildSetting<MessageChannel> ORBIS_LEADER_CHANGE_ALERT = new GuildChannelSetting(GuildSettingCategory.ORBIS_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String ORBIS_LEADER_CHANGE_ALERT(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return ORBIS_LEADER_CHANGE_ALERT.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive alerts when a nation is promoted to leader in an alliance (top 80)";
        }
    }.setupRequirements(f -> f.requireActiveGuild());
    public static GuildSetting<MessageChannel> ORBIS_OFFICER_LEAVE_ALERTS = new GuildChannelSetting(GuildSettingCategory.ORBIS_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String ORBIS_OFFICER_LEAVE_ALERTS(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return ORBIS_OFFICER_LEAVE_ALERTS.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive alerts when officers leave an alliance  (top 50)";
        }
    }.setupRequirements(f -> f.requireActiveGuild());
    public static GuildSetting<MessageChannel> ORBIS_ALLIANCE_EXODUS_ALERTS = new GuildChannelSetting(GuildSettingCategory.ORBIS_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String ORBIS_ALLIANCE_EXODUS_ALERTS(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return ORBIS_ALLIANCE_EXODUS_ALERTS.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive alerts when multiple 5+ members leave an alliance  (top 80)";
        }
    }.setupRequirements(f -> f.requireActiveGuild());
    public static GuildSetting<MessageChannel> ORBIS_OFFICER_MMR_CHANGE_ALERTS = new GuildChannelSetting(GuildSettingCategory.ORBIS_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String ORBIS_OFFICER_MMR_CHANGE_ALERTS(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return ORBIS_OFFICER_MMR_CHANGE_ALERTS.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive alerts when gov members increase MMR (top 80)";
        }
    }.setupRequirements(f -> f.requireActiveGuild());
    public static GuildSetting<MessageChannel> ENEMY_MMR_CHANGE_ALERTS = new GuildChannelSetting(GuildSettingCategory.BEIGE_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String ENEMY_MMR_CHANGE_ALERTS(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return ENEMY_MMR_CHANGE_ALERTS.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive alerts when a member in `enemies` coalitions changes MMR";
        }
    }.setupRequirements(f -> f.requireValidAlliance().requireActiveGuild());
    public static GuildSetting<MessageChannel> ESCALATION_ALERTS = new GuildChannelSetting(GuildSettingCategory.ORBIS_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String ESCALATION_ALERTS(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return ESCALATION_ALERTS.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive alerts for war escalation alerts in orbis";
        }
    }.setupRequirements(f -> f.requireActiveGuild());
    public static GuildSetting<MessageChannel> ACTIVITY_ALERTS = new GuildChannelSetting(GuildSettingCategory.ORBIS_ALERTS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String ACTIVITY_ALERTS(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return ACTIVITY_ALERTS.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to receive alerts for activity (e.g. pre blitz)";
        }
    }.setupRequirements(f -> f.requireActiveGuild());
    public static GuildSetting<Long> BANKER_WITHDRAW_LIMIT = new GuildLongSetting(GuildSettingCategory.BANK_ACCESS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String BANKER_WITHDRAW_LIMIT(@Me GuildDB db, @Me User user, long amount) {
            return BANKER_WITHDRAW_LIMIT.setAndValidate(db, user, amount);
        }
        @Override
        public String help() {
            return "The daily withdraw limit (from the offshore) of non admins";
        }
    }.setupRequirements(f -> f.requiresOffshore());
    public static GuildSetting<Long> BANKER_WITHDRAW_LIMIT_INTERVAL = new GuildLongSetting(GuildSettingCategory.BANK_ACCESS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String BANKER_WITHDRAW_LIMIT_INTERVAL(@Me GuildDB db, @Me User user, @Timediff Long timediff) {
            if (timediff < 60) {
                return "The interval must be at least 1 minute";
            }
            if (timediff > TimeUnit.DAYS.toMillis(90)) {
                return "The interval must be less than 90 days";
            }
            return BANKER_WITHDRAW_LIMIT_INTERVAL.setAndValidate(db, user, timediff);
        }

        // fix legacy
        @Override
        public Long parse(GuildDB db, String input) {
            // if info contains letters
            if (input.matches(".*[a-zA-Z]+.*")) {
                input = "" + (TimeUtil.timeToSec(input) * 1000);
            }
            return super.parse(db, input);
        }

        @Override
        public String toReadableString(Long value) {
            return TimeUtil.secToTime(TimeUnit.MILLISECONDS, value);
        }

        @Override
        public String help() {
            return "The time period the withdraw limit applies to (defaults to 1 day)";
        }
    }.setupRequirements(f -> f.requires(BANKER_WITHDRAW_LIMIT).requiresOffshore());
    public static GuildSetting<TaxRate> TAX_BASE = new GuildSetting<TaxRate>(GuildSettingCategory.TAX, TaxRate.class) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String TAX_BASE(@Me GuildDB db, @Me User user, TaxRate taxRate) {
            return TAX_BASE.setAndValidate(db, user, taxRate);
        }

        @Override
        public String toString(TaxRate value) {
            return value.toString();
        }

        @Override
        public String help() {
            return "The internal tax amount ($/rss) in the format e.g. `25/25` to be excluded in deposits.\n" +
                    "Defaults to `100/100` (i.e. no taxes are included in depos).\n" +
                    "Setting is retroactive. See also: " + CM.nation.set.taxinternal.cmd.toSlashMention();
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID).requires(API_KEY).requireValidAlliance());
    public static GuildSetting<MessageChannel> MEMBER_AUDIT_ALERTS = new GuildChannelSetting(GuildSettingCategory.AUDIT) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String MEMBER_AUDIT_ALERTS(@Me GuildDB db, @Me User user, MessageChannel channel) {
            return MEMBER_AUDIT_ALERTS.setAndValidate(db, user, channel);
        }
        @Override
        public String help() {
            return "The #channel to ping members about audits";
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID).requireValidAlliance());
    public static GuildSetting<Set<AutoAuditType>> DISABLED_MEMBER_AUDITS = new GuildEnumSetSetting<AutoAuditType>(GuildSettingCategory.AUDIT, AutoAuditType.class) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String DISABLED_MEMBER_AUDITS(@Me GuildDB db, @Me User user, Set<AutoAuditType> audits) {
            return DISABLED_MEMBER_AUDITS.setAndValidate(db, user, audits);
        }
        @Override
        public String help() {
            return "A comma separated list of audit types to ignore: " + StringMan.getString(AutoAuditType.values());
        }
    }.setupRequirements(f -> f.requires(MEMBER_AUDIT_ALERTS).requireValidAlliance());
    public static GuildSetting<Map<ResourceType, Double>> REWARD_REFERRAL = new GuildResourceSetting(GuildSettingCategory.REWARD) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String REWARD_REFERRAL(@Me GuildDB db, @Me User user, Map<ResourceType, Double> amount) {
            return REWARD_REFERRAL.setAndValidate(db, user, amount);
        }

        @Override
        public String help() {
            return "The reward (resources) for referring a nation in the form `{food=1,money=3.2}`";
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID).requireValidAlliance().requires(INTERVIEW_PENDING_ALERTS));
    public static GuildSetting<Map<ResourceType, Double>> REWARD_MENTOR = new GuildResourceSetting(GuildSettingCategory.REWARD) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String REWARD_MENTOR(@Me GuildDB db, @Me User user, Map<ResourceType, Double> amount) {
            return REWARD_MENTOR.setAndValidate(db, user, amount);
        }
        @Override
        public String help() {
            return "The reward (resources) for mentoring a nation in the form `{food=1,money=3.2}`";
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID).requireValidAlliance().requires(INTERVIEW_PENDING_ALERTS));
    public static GuildSetting<Boolean> PUBLIC_OFFSHORING = new GuildBooleanSetting(GuildSettingCategory.BANK_ACCESS) {
        @NoFormat
        @Command(descMethod = "help")
        @RolePermission(Roles.ADMIN)
        public String PUBLIC_OFFSHORING(@Me GuildDB db, @Me User user, boolean enabled) {
            return PUBLIC_OFFSHORING.setAndValidate(db, user, enabled);
        }

        @Override
        public String help() {
            return "Public offshores allow other alliances to see and register to use this alliance as an offshore without approval";
        }
    }.setupRequirements(f -> f.requires(ALLIANCE_ID).requireValidAlliance().requiresOffshore().requireFunction(db -> {
        Map.Entry<GuildDB, Integer> offshoreDb = db.getOffshoreDB();
        if (offshoreDb == null || offshoreDb.getKey().getIdLong() != db.getIdLong()) {
            throw new IllegalArgumentException("This guild is not an offshore. See: " + CM.offshore.add.cmd.toSlashMention());
        }
    }));
    public static GuildSetting<Set<Integer>> GRANT_TEMPLATE_BLACKLIST = new GuildSetting<Set<Integer>>(GuildSettingCategory.BANK_ACCESS, Set.class, Integer.class) {

        @Command(descMethod = "help")
        @RolePermission(Roles.ECON)
        public String toggleGrants(@Me GuildDB db, DBNation nation) {

            Set<Integer> blacklist = GRANT_TEMPLATE_BLACKLIST.getOrNull(db, false);

            if(blacklist == null)
                blacklist = new HashSet<>();

            if(!blacklist.contains(nation.getId())) {
                blacklist.add(nation.getId());
                GRANT_TEMPLATE_BLACKLIST.set(db, blacklist);

                return "Member has been added to the black list";
            }
            else {
                blacklist.remove(nation.getId());
                GRANT_TEMPLATE_BLACKLIST.set(db, blacklist);

                return "Member has been removed from the black list";
            }
        }

        @Override
        public String help() {
            return "The id of the member you want to add to the blacklist";
        }

        @Override
        public Set<Integer> validate(GuildDB db, Set<Integer> nationIDs) {
            for(int id : nationIDs) {
                DBNation nation = DBNation.getById(id);

                if(nation == null)
                    throw new IllegalArgumentException("Nation does not exist");
            }

            return nationIDs;
        }

        @Override
        public String toReadableString(Set<Integer> value) {

            List<String> names = new ArrayList<>();

            for (int id : value) {
                DBNation nation = DBNation.getById(id);
                // add name to list, or add the id if nation is null

                if(nation == null)
                    names.add(nation.getId() + "");
                else
                    names.add(nation.getName());
            }

            return String.join(",", names);
        }

        @Override
        public String toString(Set<Integer> value) {
            return StringMan.join(value, ",");
        }
    };

    private static final Map<String, GuildSetting> BY_NAME = new HashMap<>();

    static {
        // add by field names
        for (Field field : GuildKey.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!GuildSetting.class.isAssignableFrom(field.getType())) continue;
            try {
                GuildSetting setting = (GuildSetting) field.get(null);
                BY_NAME.put(field.getName(), setting);
                setting.setName(field.getName());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public static GuildSetting[] values() {
        return BY_NAME.values().toArray(new GuildSetting[0]);
    }

    public static GuildSetting valueOf(String name) {
        GuildSetting result = BY_NAME.get(name);
        if (result == null) {
            throw new IllegalArgumentException("No such setting: " + name + ". Options:\n- " + StringMan.join(BY_NAME.keySet(), "\n- "));
        }
        return result;
    }
}
