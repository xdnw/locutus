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

    @Comment({"Override use V2"})
    @Ignore
    @Final
    public static boolean USE_V2 = false;

    @Comment({"Override use V2"})
    @Ignore
    @Final
    public static Set<String> WHITELISTED_IPS = new HashSet<>(Arrays.asList("127.0.0.1"));

    @Ignore
    @Final
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36 Edg/107.0.1418.52";

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
            "Defaults to the root-server"})
    public long ROOT_COALITION_SERVER = 0;

    @Comment({"The guild id to post the forum feed in",
            "Set to 0 to disable (default 0)"})
    public long FORUM_FEED_SERVER = 0;

    @Comment("Your P&W username (optional, but recommended)")
    public String USERNAME = "";
    @Comment("Your P&W password (optional, but recommended)")
    public String PASSWORD = "";

    @Comment("Your P&W verified bot key (optional)")
    public String ACCESS_KEY = "";

    @Comment("Your api key (generated if username/password is set)")
    public String API_KEY_PRIMARY = "";

    @Comment({"A list of api keys the bot can use for requests (optional)",
            "See: `/admin validateApiKeys`"})
    public List<String> API_KEY_POOL = Arrays.asList();

    @Comment({"The discord id of the bot (generated)",
            "Found in the General Information section of the Discord Developer Portal"})
    public long APPLICATION_ID = 0;

    @Comment("The discord user id of the admin user. (generated)")
    public long ADMIN_USER_ID = -1;

    @Comment("The nation id of the admin. (generated from login or api key)")
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
    @Create
    public ARTIFICIAL_INTELLIGENCE ARTIFICIAL_INTELLIGENCE;
    @Create
    public DATABASE DATABASE;

    public static String commandPrefix(boolean legacy) {
        return legacy ? Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX : Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX;
    }

    //

    public String PNW_URL() {
        return "https://" + (TEST ? "test." : "") + "politicsandwar.com";
    }
    public int ALLIANCE_ID() {
        return Locutus.imp().getNationDB().getNation(NATION_ID).getAlliance_id();
    }

    public static class ENABLED_COMPONENTS {
        @Comment({"If the discord bot is enabled at all",
                "- Other components require the discord bot to be enabled"})
        public boolean DISCORD_BOT = true;
        @Comment("If message commands e.g. `!who` is enabled")
        public boolean MESSAGE_COMMANDS = true;
        @Comment("If slash `/` commands are enabled (WIP)")
        public boolean SLASH_COMMANDS = true;
        @Comment({"If the web interface is enabled",
                "- If enabled, also configure the web section below"
        })
        public boolean WEB = false;

        @Comment({"Should databases be initialized on startup",
                "false = they are initialized as needed (I havent done much optimization here, so thats probably shortly after startup anyway, lol)"})
        public boolean CREATE_DATABASES_ON_STARTUP = true;

        @Comment({"Should any repeating tasks be enabled",
                "- See the task section to disable/adjust individual tasks"})
        public boolean REPEATING_TASKS = true;

        @Comment({"Should any subscriptions be enabled",
                "- See the task section to disable/adjust individual subscriptions"})
        public boolean SUBSCRIPTIONS = true;

        @Comment("If P&W events should be enabled")
        public boolean EVENTS = true;

        public void disableTasks() {
            CREATE_DATABASES_ON_STARTUP = false;
            REPEATING_TASKS = false;
            SUBSCRIPTIONS = false;
        }

        public void disableListeners() {
            Settings.INSTANCE.DISCORD.INTENTS.GUILD_MESSAGES = false;
            Settings.INSTANCE.DISCORD.INTENTS.MESSAGE_CONTENT = false;
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
            Settings.INSTANCE.ENABLED_COMPONENTS.SUBSCRIPTIONS = false;

            Settings.INSTANCE.ENABLED_COMPONENTS.REPEATING_TASKS = false;

            Settings.INSTANCE.MODERATION.BANNED_ALLIANCES.clear();
            Settings.INSTANCE.MODERATION.BANNED_GUILDS.clear();

            Settings.INSTANCE.ENABLED_COMPONENTS.CREATE_DATABASES_ON_STARTUP = false;
        }
    }

    @Comment({
            "How often in seconds a task is run (set to 0 to disable)",
            "Note: Politics and war is rate limited. You may experience issues if you run tasks too frequently"
    })
    public static class TASKS {
        @Comment("If any turn related tasks are run (default: true)")
        public boolean ENABLE_TURN_TASKS = true;

        @Comment("Fetches most active wars and then attacks (default: 1 minute)")
        public int ACTIVE_WAR_SECONDS = 60;

        @Comment("Fetches all wars (default 5 minutes)")
        public int ALL_WAR_SECONDS = 60 * 5;

        @Comment({
                "Unload attacks after days (-1 = load all attacks)",
                "Must be less than or equal to `unload-wars-after-days`"
        })
        public int UNLOAD_ATTACKS_AFTER_DAYS = -1;

        @Comment("Unload wars after days (-1 = load all wars)")
        public int UNLOAD_WARS_AFTER_DAYS = -1;

        @Comment({"If escalation alerts are run every time all wars are updated",
                "Requires ALL_WAR_SECONDS to be enabled"})
        public boolean ESCALATION_ALERTS = true;

        @Comment("Fetches most active nations (default 1 minute)")
        public int ACTIVE_NATION_SECONDS = 60;

        @Comment("Fetches colored nations (default 5 minutes)")
        public int COLORED_NATIONS_SECONDS = 60 * 5;

        @Comment("Fetches non Vacation Mode nations (default 15 minutes)")
        public int ALL_NON_VM_NATIONS_SECONDS = 60 * 15;

        @Comment("Fetches outdated cities (default 5 minute)")
        public int OUTDATED_CITIES_SECONDS = 60 * 5;

        @Comment("Runs the pre update beige reminders (default: 61 seconds)")
        public int BEIGE_REMINDER_SECONDS = 61;

        @Comment({"What range of top alliances to check the MMR of (default: 80)",
        "set to 0 to disable"})
        public int OFFICER_MMR_ALERT_TOP_X = 80;

        @Comment("Fetches baseball games (default 2 hours)")
        public int BASEBALL_SECONDS = 60 * 60 * 2;

        @Comment("Fetches the bounties (default 7 minutes)")
        public int BOUNTY_UPDATE_SECONDS = 60 * 7;


        @Comment("Fetches the bounties (default 13 minutes)")
        public int TREASURE_UPDATE_SECONDS = 60 * 13;

        @Comment("Fetches the treaties (default 6 minutes)")
        public int TREATY_UPDATE_SECONDS = 60 * 6;

        @Comment("Fetches trades (default: 15 minutes)")
        public int COMPLETED_TRADES_SECONDS = 15 * 60;

        @Comment({"Fetches forum comments",
                "(default: DISABLED, as you can just check the forums with your browser)",
                "Requires FORUM_FEED_SERVER to be enabled"})
        public int FORUM_UPDATE_INTERVAL_SECONDS = 0;

        @Comment({"Fetches spies in the background via webscrape (default: disabled)",
                "If disabled, spies will be fetched when needed",
                "*Requires setting the `trackspies` coalition in the root server"})
        public int FETCH_SPIES_INTERVAL_SECONDS = 0;

        @Comment("Fetches discord ids (default: 15 minutes)")
        public int NATION_DISCORD_SECONDS = 15 * 60;

        @Comment({"Fetches all bank records at invterval (default: disabled)",
                "If disabled, bank records will be fetched when needed"})
        public int BANK_RECORDS_INTERVAL_SECONDS = 0;

        @Comment({"If network UIDs are fetched automatically (for multi checking) (disabled by default, since it is slow and uses web scraping)"})
        public boolean AUTO_FETCH_UID = false;

        @Create
        public TURN_TASKS TURN_TASKS;

        @Create
        public SUBSCRIPTIONS SUBSCRIPTIONS;

        @Create
        public ConfigBlock<MAIL> MAIL;

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

        public static class SUBSCRIPTIONS {
        }

        public static class TURN_TASKS {
            public boolean ALLIANCE_METRICS = true;
            @Comment("TODO: Not finished")
            public boolean MAP_FULL_ALERT = true;

            @Comment({"Fetches spies in the background via the api (default: false)",
                    "If disabled, spies will be fetched when needed",
                    "*Requires setting the `trackspies` coalition in the root server"})
            public boolean FETCH_SPIES_BACKGROUND_API = false;
        }
    }

    @Comment({
            "Proxy settings (Not implemented, work in progress)",
            "- Locutus commands can perform alliance administration actions",
            "- A proxy can aid multiple alliances performing actions concurrently"
    })
    public static class PROXY {
//        public String USER = "username@example.com";
//        public String PASSWORD = "12345678";
//        @Comment({"The available hosts to use",
//        "multiple hosts will be distributed amongst the clients used"})
//        public List<String> HOSTS = new ArrayList<>(Arrays.asList(
//                "region.example.com"
//        ));
//        public int PORT = 1080;
//
//        public ProxyHandler createProxy(String host) {
//            if (host == null) {
//                host = HOSTS.get(0);
//            }
//            return new ProxyHandler(host, PORT, USER, PASSWORD);
//        }
//
//        /**
//         * Find a recommended host to use for the proxy.
//         * The previous host will be used if it is valid.
//         * A host least used in the local bucket, and (secondary) globally, is preferred
//         * @param previousHost - the previous host used, or null
//         * @param tier1avoid - a list of hosts used by the local bucket
//         * @param tier2avoid - a list of hosts used globally
//         * @return host
//         */
//        public String recommendHost(String previousHost, List<String> tier1avoid, List<String> tier2avoid) {
//            if (previousHost != null && HOSTS.contains(previousHost)) return previousHost;
//            if (HOSTS.size() == 1) return HOSTS.get(0);
//            Map<String, Long> weighting = new HashMap<>();
//            for (String host : tier1avoid) weighting.put(host, weighting.getOrDefault(host, 0L) + Integer.MAX_VALUE);
//            for (String host : tier2avoid) weighting.put(host, weighting.getOrDefault(host, 0L) + 1);
//
//            long minVal = Long.MAX_VALUE;
//            String minHost = null;
//            for (String host : HOSTS) {
//                Long val = weighting.getOrDefault(host, 0L);
//                if (val < minVal) {
//                    minVal = val;
//                    minHost = host;
//                }
//            }
//            return minHost;
//        }
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

        @Create
        public COMMAND COMMAND;

        @Comment({
                "User ids of people who can `!register` other nations",
                "Only give this to trusted people, since it can be abused"
        })
        public List<Long> REGISTER_ANYONE = Arrays.asList();
        @Comment({
                "User ids of people who can `!register` other nations who are applicants in their alliance",
                "Less abusable version of the above, since applicants aren't typically important that impersonation would be too damaging"
        })
        public List<Long> REGISTER_APPLICANTS = Arrays.asList();

        public static class CHANNEL {
            @Comment("The channel id to receive admin alerts in (set to 0 to disable)")
            public long ADMIN_ALERTS = 0;
            @Comment("The channel id to receive error alerts in (set to 0 to disable)")
            public long ERRORS = 0;
        }


        public static class INTENTS {
            @Comment("Can see what members are in a guild")
            public boolean GUILD_MEMBERS = true;
            @Comment({
                    "Can see guild member online status",
                    "Used to limit alerts to online members only",
            })
            public boolean GUILD_PRESENCES = true;
            @Comment("Can see messages sent in guild channels mentioning or replying to this bot")
            public boolean GUILD_MESSAGES = true;
            @Comment({
                    "Can see reactions to messages sent by the bot",
                    "Disabled by default; bot interaction uses buttons now",
                    "The `/embed info` command will not display reaction totals if this is disabled"
            })
            public boolean GUILD_MESSAGE_REACTIONS = false;
            @Comment("Can read direct messages sent to the bot")
            public boolean DIRECT_MESSAGES = true;
            @Comment({"Can see all messages sent in guild channels",
                    "Disabled by default since message content is a whitelisted intent",
                    "Legacy commands require mentioning or replying to the bot"})
            public boolean MESSAGE_CONTENT = false;
            @Comment({
                    "To be able to use custom emojis in embeds as well as the import emoji command",
                    "Disabled by default since it increases discord api usage and is non essential"
            })
            public boolean EMOJI = false;
        }

        public static class CACHE {
            public boolean MEMBER_OVERRIDES = true;
            public boolean ONLINE_STATUS = true;
            public boolean EMOTE = false;
        }

        public static class COMMAND {
            @Comment("The prefix used for legacy commands (single character)")
            public String LEGACY_COMMAND_PREFIX = "!";

            @Comment("The prefix used for v2 commands (single character)")
            public String COMMAND_PREFIX = "$";
        }
    }


    public static class WEB {
        @Comment("The url/ip/hostname for the web interface")
        public String REDIRECT = "https://locutus.link";
        @Comment({"File location of the ssl certificate",
        "- Locutus expects a privkey.pem and a fullchain.pem in the directory",
        "- You can get a free certificate from e.g. https://zerossl.com/ or https://letsencrypt.org/",
        "- Set to empty string to not use an ssl certificate",
        })
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

        @Comment("The port google sheets uses to validate your credentials")
        public int GOOGLE_SHEET_VALIDATION_PORT = 8889;

        @Comment("The port google drive uses to validate your credentials")
        public int GOOGLE_DRIVE_VALIDATION_PORT = 8890;
    }

    @Comment({
            "How often in seconds a task is run (set to 0 to disable)",
            "Note: Politics and war is rate limited. You may experience issues if you run tasks too frequently"
    })
    public static class ARTIFICIAL_INTELLIGENCE {

        @Create
        public OCR OCR;

        public static final class OCR {
            @Comment({
                    "The directory of the tesseract files.",
                    "Tesseract is used for optical character recognition (OCR) to read text from images",
                    "To install tesseract, see:",
                    "macOS: `brew install tesseract` and `brew install tesseract-lang`",
                    "linux: `yum install tesseract` and `yum install tesseract-langpack-eng`",
                    "windows: `https://github.com/UB-Mannheim/tesseract/wiki`"
            })
            public String TESSERACT_LOCATION = "src/main/java/tessdata";

            @Comment({"Your API key for <ocr.space> (optional)"})
            public String OCR_SPACE_KEY = "";
        }

        @Create
        public COPILOT COPILOT;

        public static final class COPILOT {
            @Comment({"Allow use of github copilot as on option for chat completions"})
            public boolean ENABLED = false;

            public int USER_TURN_LIMIT = 10;
            public int USER_DAY_LIMIT = 25;
            public int GUILD_TURN_LIMIT = 10;
            public int GUILD_DAY_LIMIT = 25;
        }

        @Create
        public OPENAI OPENAI;

        public static final class OPENAI {
            @Comment({"Your API key from <https://platform.openai.com/account/api-keys> (optional)"})
            public String API_KEY = "";
            public int USER_TURN_LIMIT = 10;
            public int USER_DAY_LIMIT = 25;
            public int GUILD_TURN_LIMIT = 10;
            public int GUILD_DAY_LIMIT = 25;
        }

    }

    public static class DATABASE {
//        @Create
//        public MYSQL MYSQL;
        @Create
        public SQLITE SQLITE;

        public static final class SQLITE {
            @Comment("Should SQLite be used?")
            public boolean USE = true;
            @Comment("The directory to store the database in")
            public String DIRECTORY = "database";
        }
//
//        @Comment("TODO: MySQL support is not fully implemented. Request this to be finished if important")
//        public static final class MYSQL {
//            @Comment("Should MySQL be used?")
//            public boolean USE = false;
//            public String HOST = "localhost";
//            public int PORT = 3306;
//            public String USER = "root";
//            public String PASSWORD = "password";
//        }
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