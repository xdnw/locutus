package link.locutus.discord.config;

import link.locutus.discord.Locutus;
import link.locutus.discord.network.ProxyHandler;
import link.locutus.discord.config.yaml.Config;
import link.locutus.discord.db.GuildDB;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.io.File;
import java.util.*;


public class Settings extends Config {
    @Ignore
    @Final
    public static final Settings INSTANCE = new Settings();

    @Comment("If the bot is running on the test server (default: false)")
    public boolean TEST = false;
    @Comment({"The discord token of the bot (required)",
            "Found on the bot section of the Discord Developer Portal"})
    public String BOT_TOKEN = "";

    @Comment({"The discord client secret of the bot (required)",
            "Found on the OAuth2 section of the Discord Developer Portal"})
    public String CLIENT_SECRET = "";

    @Comment({"The guild id of the management server for this bot (you should be owner here)",
    "See: https://support.discord.com/hc/en-us/articles/206346498"})
    public long ROOT_SERVER = 0;

    @Comment({"The guild id of the root coalition server (e.g. for spheres)",
            "Defaults to the root server server"})
    public long ROOT_COALITION_SERVER = 0;

    @Comment({"The guild id to post the forum feed in",
            "Set to 0 to disable (default 0)"})
    public long FORUM_FEED_SERVER = 0;

    @Comment("Your P&W username (optional)")
    public String USERNAME = "";
    @Comment("Your P&W password (optional)")
    public String PASSWORD = "";

    @Comment("Your api key (generated if username/password is set)")
    public String API_KEY_PRIMARY = "";

    @Comment({"A list of api keys the bot can use for requests (optional)",
            "See: $validateApiKeys"})
    public List<String> API_KEY_POOL = Arrays.asList();

    @Comment({"The discord id of the bot (generated)",
            "Found in the General Information section of the Discord Developer Portal"})
    public long APPLICATION_ID = 0;

    @Comment("The discord user id of the admin user. (generated)")
    public long ADMIN_USER_ID = -1;

    @Comment("The nation id of the admin. (generated)")
    public int NATION_ID = 0; // generated

    ////////////////////////////////////////////////////////////////////////////

    @Create
    public ENABLED_COMPONENTS ENABLED_COMPONENTS;
    @Create
    public TASKS TASKS;
    @Create
    public DISCORD DISCORD;
    @Create
    public WEB WEB;
    @Create
    public MODERATION MODERATION;
    @Create
    public UPDATE_PROCESSOR UPDATE_PROCESSOR;
    @Create
    public LEGACY_SETTINGS LEGACY_SETTINGS;
    @Create
    public PROXY PROXY;

    //

    public String PNW_URL() {
        return "https://" + (TEST ? "test." : "") + "politicsandwar.com";
    }
    @Ignore
    public String PREFIX = "";
    public int ALLIANCE_ID() {
        return Locutus.imp().getNationDB().getNation(NATION_ID).getAlliance_id();
    }

    public static class ENABLED_COMPONENTS {
        @Comment({"If the discord bot is enabled at all",
                " - Other components require the discord bot to be enabled"})
        public boolean DISCORD_BOT = true;
        @Comment("If message commands e.g. `!who` or `$who` are enabled")
        public boolean MESSAGE_COMMANDS = true;
        @Comment("If slash `/` commands are enabled (WIP)")
        public boolean SLASH_COMMANDS = false;
        @Comment("If the web interface is enabled")
        public boolean WEB = false;

        @Comment({"Should databases be initialized on startup",
                "false = they are initialized as needed (I havent done much optimization here, so thats probably shortly after startup anyway, lol)"})
        public boolean CREATE_DATABASES_ON_STARTUP = true;

        @Comment({"Should any repeating tasks be enabled",
                " - See the task section to disable/adjust individual tasks"})
        public boolean REPEATING_TASKS = true;

        @Comment("If P&W events should be enabled")
        public boolean EVENTS = true;

        @Comment("Game of tag")
        public boolean TAG = false;

        public void disableTasks() {
            CREATE_DATABASES_ON_STARTUP = false;
            REPEATING_TASKS = false;
        }

        public void disableListeners() {
            Settings.INSTANCE.DISCORD.INTENTS.GUILD_MESSAGES = false;
            Settings.INSTANCE.DISCORD.INTENTS.GUILD_MESSAGE_REACTIONS = false;
            Settings.INSTANCE.DISCORD.INTENTS.DIRECT_MESSAGES = false;
            Settings.INSTANCE.DISCORD.INTENTS.EMOJI = false;
            Settings.INSTANCE.DISCORD.CACHE.MEMBER_OVERRIDES = false;
            Settings.INSTANCE.DISCORD.CACHE.ONLINE_STATUS = false;
            Settings.INSTANCE.DISCORD.CACHE.EMOTE = false;

            Settings.INSTANCE.ENABLED_COMPONENTS.MESSAGE_COMMANDS = false;
            Settings.INSTANCE.ENABLED_COMPONENTS.SLASH_COMMANDS = false;

            Settings.INSTANCE.ENABLED_COMPONENTS.WEB = false;
            Settings.INSTANCE.ENABLED_COMPONENTS.EVENTS = false;

            Settings.INSTANCE.ENABLED_COMPONENTS.REPEATING_TASKS = false;

            Settings.INSTANCE.MODERATION.BANNED_ALLIANCES.clear();
            Settings.INSTANCE.MODERATION.BANNED_GUILDS.clear();

            Settings.INSTANCE.ENABLED_COMPONENTS.CREATE_DATABASES_ON_STARTUP = false;
        }
    }

    @Comment("How often in seconds a task is run (set to 0 to disable)")
    public static class TASKS {
        @Create
        public TURN_TASKS TURN_TASKS;
        @Create
        public TRADE_TASKS TRADE_TASKS;
        @Create
        public ConfigBlock<MAIL> MAIL;

        @Comment("If any turn related tasks are run (default: true)")
        public boolean ENABLE_TURN_TASKS = true;

        @Comment("Runs the post update raid alerts (default: 1 second)")
        public int RAID_UPDATE_PROCESSOR_SECONDS = 1;

        @Comment("Runs the pre update beige reminders (default: 61 seconds)")
        public int BEIGE_REMINDER_SECONDS = 61;

        @Comment("Alerts for MMR changes (default 127 minutes)")
        public int OFFICER_MMR_ALERT_SECONDS = 127 * 60;
        @Comment("What range of top alliances to check the MMR of (default: 80)")
        public int OFFICER_MMR_ALERT_TOP_X = 80;

        @Comment("Fetches general nation information (default: 60)")
        public int ACTIVE_NATION_UPDATER_SECONDS = 60;

        @Comment("Fetches wars and then attacks (default 3 minutes)")
        public int WAR_ATTACK_SECONDS = 60 * 3;
        public boolean WAR_ATTACKS_ESCALATION_ALERTS = true;

        @Comment({"Fetch ingame mail of an authenticated nation and post it to a channel",
                "Set the values to 0 to disable",
                "Copy the default block for multiple users",
                "The tasks will fail if a user is not authenticated (user/pass)"})
        @BlockName("default")
        public static class MAIL extends ConfigBlock {
            public int NATION_ID = 189573;
            public int FETCH_INTERVAL_SECONDS = 62;
            public long CHANNEL_ID = 674505503400919040L;
        }

        public static class TRADE_TASKS {
            @Comment("Fetches trades (default: 15 minutes)")
            public int COMPLETED_TRADES_SECONDS = 15 * 60;
        }

        public static class TURN_TASKS {
            public boolean VM_NATION_UPDATER = true;

            public boolean GUILD_ALLIANCE_TASKS = true;
            public boolean GUILD_NATION_TASKS = true;

            public boolean ALLIANCE_METRICS = true;

            @Comment("TODO: Not finished")
            public boolean MAP_FULL_ALERT = true;
            @Comment("Update spy slots on turn change")
            public boolean SPY_SLOTS = true;
            @Comment("Update nation policy on turn change")
            public boolean POLICY = true;
            @Comment("Update nation projects on turn change")
            public boolean PROJECT = true;
            @Comment({"If bank records should be fetched each turn (default: false)",
                    " - If disabled, records will be fetched on demand"})
            public boolean BANK_RECORDS = false;
            @Comment({"Update nation cities on turn change (default: true)",
            " - If disabled, records will be fetched on demand"})
            public boolean CITIES = true;
        }
    }

    @Comment({
            "Proxy settings (Not implemented, work in progress)",
            " - Locutus commands can perform alliance administration actions",
            " - A proxy can aid multiple alliances performing actions concurrently"
    })
    public static class PROXY {
        public String USER = "username@example.com";
        public String PASSWORD = "12345678";
        @Comment({"The available hosts to use",
        "multiple hosts will be distributed amongst the clients used"})
        public List<String> HOSTS = new ArrayList<>(Arrays.asList(
                "region.example.com"
        ));
        public int PORT = 1080;

        public ProxyHandler createProxy(String host) {
            if (host == null) {
                host = HOSTS.get(0);
            }
            return new ProxyHandler(host, PORT, USER, PASSWORD);
        }

        /**
         * Find a recommended host to use for the proxy.
         * The previous host will be used if it is valid.
         * A host least used in the local bucket, and (secondary) globally, is preferred
         * @param previousHost - the previous host used, or null
         * @param tier1avoid - a list of hosts used by the local bucket
         * @param tier2avoid - a list of hosts used globally
         * @return host
         */
        public String recommendHost(String previousHost, List<String> tier1avoid, List<String> tier2avoid) {
            if (previousHost != null && HOSTS.contains(previousHost)) return previousHost;
            if (HOSTS.size() == 1) return HOSTS.get(0);
            Map<String, Long> weighting = new HashMap<>();
            for (String host : tier1avoid) weighting.put(host, weighting.getOrDefault(host, 0L) + Integer.MAX_VALUE);
            for (String host : tier2avoid) weighting.put(host, weighting.getOrDefault(host, 0L) + 1);

            long minVal = Long.MAX_VALUE;
            String minHost = null;
            for (String host : HOSTS) {
                Long val = weighting.getOrDefault(host, 0L);
                if (val < minVal) {
                    minVal = val;
                    minHost = host;
                }
            }
            return minHost;
        }
    }

    public static class LEGACY_SETTINGS {
        @Final
        @Ignore
        @Comment("Open browser window when these ppl do attacks")
        public List<Integer> ATTACKER_DESKTOP_ALERTS = new ArrayList<>();

        @Final
        @Ignore
        @Comment("Timestamp for when marked deposits were introduced")
        public long MARKED_DEPOSITS_DATE = 1622661922L * 1000L;

        @Final
        @Ignore
        public boolean OPEN_DESKTOP_FOR_CAPTCHA = false;

        @Final
        @Ignore
        public boolean OPEN_DESKTOP_FOR_MISTRADES = false;

        @Final
        @Ignore
        public boolean OPEN_DESKTOP_FOR_RAIDS = false;

        @Final
        @Ignore
        @Deprecated
        @Comment("Access key for P&W (deprecated)")
        public String ACCESS_KEY = "";
        @Final
        @Ignore // disabled (not super accurate and not fair)
        @Deprecated
        public boolean DEANONYMIZE_SPYOPS = false;
        @Final
        @Ignore // disabled (not super accurate and not fair)
        @Deprecated
        public boolean DEANONYMIZE_BOUNTIES = false;

        @Deprecated
        @Comment("Can bypass the normal transfer cap")
        public List<Long> WHITELISTED_BANK_USERS = Arrays.asList();
    }

    public int getAlliance(MessageReceivedEvent event) {
        if (event.isFromGuild()) {
            return getAlliance((Guild) event.getGuild());
        } else {
            return getAlliance((Guild) null);
        }
    }

    public int getAlliance(Guild guild) {
        return Locutus.imp().getGuildDB(guild).getOrThrow(GuildDB.Key.ALLIANCE_ID);
    }


    @Comment({
            "Prevent users, nations, alliances from using the bot"
    })
    public static class MODERATION {
        /*
        DO NOT ADD HERE FOR PERSONAL REASONS. ONLY IF THEY ARE ABUSING THE BOT
        */
        public List<Integer> BANNED_NATIONS = List.of();
        public List<Long> BANNED_USERS = List.of();
        public List<Long> BANNED_GUILDS = new ArrayList<>();
        public List<Integer> BANNED_ALLIANCES = new ArrayList<>();
    }

    public static class UPDATE_PROCESSOR {
        public long THRESHOLD_BANK_SUB_ALERT = 10000000;
        public long THRESHOLD_ALL_BANK_ALERT = 100000000;
    }

    public static class DISCORD {
        @Create
        public CHANNEL CHANNEL;
        @Create
        public INTENTS INTENTS;
        @Create
        public CACHE CACHE;

        @Comment({
                "User ids of people who can `!register` other nations",
                "Only give this to trusted people, since it can be abused"
        })
        public List<Long> REGISTER_ANYONE = Arrays.asList();
        @Comment({
                "User ids of people who can `!register` other nations who are applicants in their alliance",
                "Less abusable version of the above, since applicants aren't typically important that impersonation would be too damagingaaaaaaaaaaa"
        })
        public List<Long> REGISTER_APPLICANTS = Arrays.asList();

        public static class CHANNEL {
            @Comment("The channel id to receive admin alerts in (set to 0 to disable)")
            public long ADMIN_ALERTS = 0;
            @Comment("The channel id to receive error alerts in (set to 0 to disable)")
            public long ERRORS = 0;
        }


        public static class INTENTS {
            public boolean GUILD_MEMBERS = true;
            public boolean GUILD_PRESENCES = true;
            public boolean GUILD_MESSAGES = true;
            public boolean GUILD_MESSAGE_REACTIONS = true;
            public boolean DIRECT_MESSAGES = true;
            public boolean EMOJI = false;
        }

        public static class CACHE {
            public boolean MEMBER_OVERRIDES = true;
            public boolean ONLINE_STATUS = true;
            public boolean EMOTE = false;
        }
    }


    public static class WEB {
        @Comment("The url/op/hostname for the web interface")
        public String REDIRECT = "https://locutus.link";
        @Comment({"File location of the ssl certificate",
        " - You can get a free certificate from e.g. https://zerossl.com/ or https://letsencrypt.org/",
        " - Set to empty string to not use an ssl certificate"})
        public String CERT_PATH = "C:/Certbot/live/locutus.link/";
        @Comment({"The password or passphrase for the certificate",
        "Leave blank if there is none"})
        public String CERT_PASSWORD = "";
        @Comment("Port used for HTTP. Set to 0 to disable")
        public int PORT_HTTP = 80;
        @Comment("Port used for secure HTTPS. Set to 0 to disable")
        public int PORT_HTTPS = 443;
        @Comment("If set to true, web content is not compressed/minified")
        public boolean DEVELOPMENT = true;
    }

    private File defaultFile = new File("config" + File.separator + "config.yaml");

    public File getDefaultFile() {
        return defaultFile;
    }

    public void reload(File file) {
        this.defaultFile = file;
        load(file);
        save(file);
    }

}