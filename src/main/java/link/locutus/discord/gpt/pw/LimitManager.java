package link.locutus.discord.gpt.pw;

import com.openai.models.ChatModel;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.gpt.GptHandler;
import link.locutus.discord.gpt.ProviderType;
import link.locutus.discord.gpt.imps.text2text.IText2Text;
import link.locutus.discord.gpt.imps.text2text.OpenAiText2Text;
import link.locutus.discord.util.RateLimitUtil;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.*;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LimitManager {
    private final Logger logger;
    private final GptHandler handler;
    private final Map<Long, GptLimitTracker> limitTrackerMap;

    public LimitManager(GptHandler handler) {
        this.handler = handler;
        this.limitTrackerMap = new ConcurrentHashMap<>();

        ConfigurationBuilder<BuiltConfiguration> builder
                = ConfigurationBuilderFactory.newConfigurationBuilder();

        AppenderComponentBuilder rollingFile = builder.newAppender("rolling", "RollingFile");
        rollingFile.addAttribute("fileName", "rolling.log");
        rollingFile.addAttribute("filePattern", "rolling-%d{MM-dd-yy}.log.gz");

        ComponentBuilder<?> triggeringPolicies = builder.newComponent("Policies")
                .addComponent(builder.newComponent("CronTriggeringPolicy")
                        .addAttribute("schedule", "0 0 0 * * ?"))
                .addComponent(builder.newComponent("SizeBasedTriggeringPolicy")
                        .addAttribute("size", "100M"));

        rollingFile.addComponent(triggeringPolicies);

        LoggerComponentBuilder logger = builder.newLogger("gpt", Level.DEBUG);
        logger.add(builder.newAppenderRef("log"));
        logger.addAttribute("additivity", false);

        Configurator.initialize(builder.build());

        this.logger = LoggerFactory.getLogger("gpt");
    }

    public GptLimitTracker getLimitTracker(GuildDB db) {
        return limitTrackerMap.computeIfAbsent(db.getIdLong(), f -> new SimpleGPTLimitTracker(true, logger));
    }

    public GptLimitTracker getDefaultProvider(GuildDB db, User user, DBNation nation) {
        return getLimitTracker(db);
    }

    public void registerDefaults() {
//        if (Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.COPILOT.ENABLED) {
//            SimpleGPTProvider provider = new SimpleGPTProvider(
//                    ProviderType.COPILOT,
//                    handler.createCopilotText2Text("tokens", authData -> {
//                        throw new IllegalArgumentException("(For bot owner): Open URL <" + authData.Url + "> to enter the device code: `" + authData.UserCode + "`");
//                    }),
//                    handler.getModerator(),
//                    true,
//                    logger);
//            provider.setTurnLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.COPILOT.USER_TURN_LIMIT);
//            provider.setDayLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.COPILOT.USER_DAY_LIMIT);
//            provider.setGuildTurnLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.COPILOT.GUILD_TURN_LIMIT);
//            provider.setGuildDayLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.COPILOT.GUILD_DAY_LIMIT);
//
//            globalProviders.put(ProviderType.COPILOT, provider);
//        }

        if (Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.API_KEY != null) {
            SimpleGPTLimitTracker provider = new SimpleGPTLimitTracker(true, logger);

            provider.setTurnLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.LIMITS.USER_TURN_LIMIT);
            provider.setDayLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.LIMITS.USER_DAY_LIMIT);
            provider.setGuildTurnLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.LIMITS.GUILD_TURN_LIMIT);
            provider.setGuildDayLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.LIMITS.GUILD_DAY_LIMIT);

            globalProviders.put(ProviderType.OPENAI, provider);
        }

//        if (handler.getProcessText2Text() != null) {
//            SimpleGPTProvider provider = new SimpleGPTProvider(
//                    ProviderType.PROCESS,
//                    handler.getProcessText2Text(),
//                    handler.getModerator(),
//                    false,
//                    logger);
//            globalProviders.put(ProviderType.PROCESS, provider);
//        }
    }
}
