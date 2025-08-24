package link.locutus.discord.gpt.pw;

import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.gpt.GptHandler;
import net.dv8tion.jda.api.entities.User;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.*;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
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
        return limitTrackerMap.computeIfAbsent(db.getIdLong(), f -> {
            SimpleGPTLimitTracker provider = new SimpleGPTLimitTracker(handler, true, logger);
            provider.setTurnLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.LIMITS.USER_TURN_LIMIT);
            provider.setDayLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.LIMITS.USER_DAY_LIMIT);
            provider.setGuildTurnLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.LIMITS.GUILD_TURN_LIMIT);
            provider.setGuildDayLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.LIMITS.GUILD_DAY_LIMIT);

            int[] localLimits = GuildKey.GPT_USAGE_LIMITS.getOrNull(db);
            if (localLimits != null) {
                // min between both
                provider.setTurnLimit(Math.min(provider.getTurnLimit(), localLimits[0]));
                provider.setDayLimit(Math.min(provider.getDayLimit(), localLimits[1]));
                provider.setGuildTurnLimit(Math.min(provider.getGuildTurnLimit(), localLimits[2]));
                provider.setGuildDayLimit(Math.min(provider.getGuildDayLimit(), localLimits[3]));
            }
            return provider;
        });
    }

    public GptLimitTracker getDefaultProvider(GuildDB db, User user, DBNation nation) {
        return getLimitTracker(db);
    }
}
