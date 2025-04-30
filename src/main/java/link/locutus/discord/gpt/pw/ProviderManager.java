package link.locutus.discord.gpt.pw;

import com.knuddels.jtokkit.api.ModelType;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.gpt.GptHandler;
import link.locutus.discord.gpt.imps.GPTText2Text;
import link.locutus.discord.gpt.imps.IText2Text;
import link.locutus.discord.gpt.imps.ProviderType;
import link.locutus.discord.util.RateLimitUtil;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.api.LoggerComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ProviderManager {
    private final Logger logger;
    private final GptHandler handler;
    private Map<ProviderType, GPTProvider> globalProviders = new ConcurrentHashMap<>();
    private Map<Long, Map<ProviderType, GPTProvider>> guildProviders = new ConcurrentHashMap<>();

    public ProviderManager(GptHandler handler) {
        this.handler = handler;
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

    public Set<GPTProvider> getProviders(GuildDB db) {
        Set<GPTProvider> providers = new ObjectLinkedOpenHashSet<>();
        // add guild
        Map<ProviderType, GPTProvider> result = guildProviders.computeIfAbsent(db.getIdLong(), k -> new ConcurrentHashMap<>());
        synchronized (result) {
            String openAiKey = GuildKey.OPENAI_KEY.getOrNull(db);
            ModelType expectedModel = GuildKey.OPENAI_MODEL.getOrNull(db);
            if (expectedModel == null) expectedModel = ModelType.GPT_4;

            int[] limits = GuildKey.GPT_USAGE_LIMITS.getOrNull(db);

            GPTProvider existing = result.get(ProviderType.OPENAI);
            if (openAiKey == null || (existing != null && ((GPTText2Text) existing.getText2Text()).getModel() != expectedModel)) {
                GPTProvider removed = result.remove(ProviderType.OPENAI);
                if (removed != null) {
                    try {
                        removed.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else {
                ModelType finalExpectedModel = expectedModel;
                GPTProvider provider = result.computeIfAbsent(ProviderType.OPENAI, k -> {
                    IText2Text newProvider = handler.createOpenAiText2Text(openAiKey, finalExpectedModel);
                    return new SimpleGPTProvider(ProviderType.OPENAI, newProvider, handler.getModerator(), true, logger);
                });
                if (limits != null) provider.setUsageLimits(limits[0], limits[1], limits[2], limits[3]);
                providers.add(provider);
            }

            Boolean enableCopilot = GuildKey.ENABLE_GITHUB_COPILOT.getOrNull(db);
            if (enableCopilot != Boolean.TRUE) {
                GPTProvider removed = result.remove(ProviderType.COPILOT);
                if (removed != null) {
                    try {
                        removed.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else {
                GPTProvider provider = result.computeIfAbsent(ProviderType.COPILOT, k -> {
                    String path = "tokens" + File.separator + db.getIdLong();
                    IText2Text newProvider = handler.createCopilotText2Text(path, authData -> {
                        PrivateChannel channel = RateLimitUtil.complete(db.getGuild().getOwner().getUser().openPrivateChannel());
                        String message = "To use copilot for chat completions, please authenticate the application via:\n" +
                                authData.Url + "\n" +
                                "and enter the code: " + authData.UserCode + "\n\n" +
                                "This feature was enabled on a guild you own: " + db.getGuild() + "\n" +
                                "To disable, see: " + CM.settings.delete.cmd.toSlashMention() + " with key: `" + GuildKey.ENABLE_GITHUB_COPILOT.name() + "`";
                        RateLimitUtil.queue(channel.sendMessage(message));
                    });
                    return new SimpleGPTProvider(ProviderType.COPILOT, newProvider, handler.getModerator(), true, logger);
                });
                if (limits != null) provider.setUsageLimits(limits[0], limits[1], limits[2], limits[3]);
                providers.add(provider);
            }
        }

        // add global
        providers.addAll(globalProviders.values());

        return providers;
    }

    public Set<ProviderType> getProviderTypes(DBNation nation) {
        ByteBuffer existingBuf = nation.getMeta(NationMeta.GPT_PROVIDER);
        Set<ProviderType> existing = new HashSet<>();
        long mask = existingBuf == null ? 0 : existingBuf.getLong();
        if (mask == 0) mask = -1;
        for (ProviderType type : ProviderType.values()) {
            if ((mask & (1L << type.ordinal())) != 0) {
                existing.add(type);
            }
        }
        return existing;
    }

    public void setProviderTypes(DBNation nation, Set<ProviderType> types) {
        long mask = 0;
        for (ProviderType type : types) {
            mask |= (1L << type.ordinal());
        }
        nation.setMeta(NationMeta.GPT_PROVIDER, mask);
    }

    public GPTProvider getDefaultProvider(GuildDB db, User user, DBNation nation) {
        Set<ProviderType> types = getProviderTypes(nation);
        return getProvider(db, user, types);
    }

    public GPTProvider getProvider(GuildDB db, User user, Set<ProviderType> types) {
        Set<GPTProvider> providers = getProviders(db);
        // remove if not type
        if (!types.isEmpty()) {
            providers.removeIf(provider -> !types.contains(provider.getType()));
        }
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("No providers available. See: " + CM.chat.providers.list.cmd.toSlashMention() + " and " + CM.chat.providers.set.cmd.toSlashMention());
        }
        List<String> noPermsMessages = new ArrayList<>();
        for (GPTProvider provider : providers) {
            try {
                if (provider.hasPermission(db, user, true)) {
                    return provider;
                }
            } catch (IllegalArgumentException ignore) {
                noPermsMessages.add(provider.getId() + ": " + ignore.getMessage());
            }
        }
        throw new IllegalArgumentException("No providers available. Errors:\n- " + String.join("\n- ", noPermsMessages) + "\n\nSee " +  CM.chat.providers.list.cmd.toSlashMention() + " and " + CM.chat.providers.set.cmd.toSlashMention());
    }

    public void registerDefaults() {
        if (Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.COPILOT.ENABLED) {
            SimpleGPTProvider provider = new SimpleGPTProvider(
                    ProviderType.COPILOT,
                    handler.createCopilotText2Text("tokens", authData -> {
                        throw new IllegalArgumentException("(For bot owner): Open URL <" + authData.Url + "> to enter the device code: `" + authData.UserCode + "`");
                    }),
                    handler.getModerator(),
                    true,
                    logger);
            provider.setTurnLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.COPILOT.USER_TURN_LIMIT);
            provider.setDayLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.COPILOT.USER_DAY_LIMIT);
            provider.setGuildTurnLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.COPILOT.GUILD_TURN_LIMIT);
            provider.setGuildDayLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.COPILOT.GUILD_DAY_LIMIT);

            globalProviders.put(ProviderType.COPILOT, provider);
        }

        if (Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.API_KEY != null) {
            SimpleGPTProvider provider = new SimpleGPTProvider(
                    ProviderType.OPENAI,
                    handler.createOpenAiText2Text(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.API_KEY, ModelType.GPT_4),
                    handler.getModerator(),
                    true,
                    logger);

            provider.setTurnLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.USER_TURN_LIMIT);
            provider.setDayLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.USER_DAY_LIMIT);
            provider.setGuildTurnLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.GUILD_TURN_LIMIT);
            provider.setGuildDayLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.GUILD_DAY_LIMIT);

            globalProviders.put(ProviderType.OPENAI, provider);
        }

        if (handler.getProcessText2Text() != null) {
            SimpleGPTProvider provider = new SimpleGPTProvider(
                    ProviderType.PROCESS,
                    handler.getProcessText2Text(),
                    handler.getModerator(),
                    false,
                    logger);
            globalProviders.put(ProviderType.PROCESS, provider);
        }
    }
}
