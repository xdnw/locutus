package link.locutus.discord.commands.manager.v2.command;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.util.RateLimitUtil;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public interface IModalBuilder {
    IModalBuilder addInput(TextInput input);
    UUID getId();

    IModalBuilder setId(UUID id);
    IModalBuilder setTitle(String title);

    // New java cache builder map 15m
    public static final LoadingCache<UUID, Map<String, String>> DEFAULT_VALUES = CacheBuilder.newBuilder()
    .expireAfterWrite(15, TimeUnit.MINUTES)
    .build(new CacheLoader<UUID, Map<String, String>>() {
        @Override
        public Map<String, String> load(UUID key) throws Exception {
            return new HashMap<>();
        }
    });

    default IModalBuilder create(ICommand command, Map<String, String> defaultValues, List<String> promptForArguments) {
        setTitle(command.getFullPath(" "));
        UUID id = getId();
        if (id == null) {
            id = UUID.randomUUID();
            setId(id);
        }

        Map<String, ParameterData> paramMap = command.getUserParameterMap();

        // validate arguments
        for (Map.Entry<String, String> entry : defaultValues.entrySet()) {
            String key = entry.getKey();
            if (!paramMap.containsKey(key)) {
                throw new IllegalArgumentException("Argument " + key + " is not a valid argument for command " + command.getFullPath(" ") + "\n" +
                        "Options: " + paramMap.keySet().toString());
            }
        }

        for (String key : promptForArguments) {
            ParameterData param = paramMap.get(key);
            if (param == null) {
                throw new IllegalArgumentException("Argument " + key + " is not a valid argument for command " + command.getFullPath(" ") + "\n" +
                        "Options: " + paramMap.keySet().toString());
            }
            String desc = param.getExpandedDescription(true, true, true);

            String name = param.getName();
            TextInput.Builder builder = TextInput.create(param.getName(), desc, TextInputStyle.PARAGRAPH);
            String[] examples = param.getBinding().getKey().getBinding().examples();
            if (examples != null && examples.length > 0) {
                builder.setPlaceholder(examples[0]);
            }
            Range range = param.getAnnotation(Range.class);
            if (range != null) {
                builder.setRequiredRange((int) Math.round(Math.max(Integer.MAX_VALUE, range.min())), (int) Math.round(Math.min(range.max(), Integer.MAX_VALUE)));
            }

            String[] def = param.getDefaultValue();
            if (def != null && def.length > 0) {
                builder.setValue(def[0]);
            }
            // parameter.isOptional() || parameter.isFlag()
            boolean isOptional = param.isOptional() || param.isFlag() || defaultValues.containsKey(name);
            if (!isOptional) {
                builder.setRequired(true);
            }

            TextInput input = builder.build();
            addInput(input);

        }

        DEFAULT_VALUES.put(id, defaultValues);

        return this;
    }

    CompletableFuture<IModalBuilder> send();

    default CompletableFuture<IModalBuilder> sendIfFree() {
        if (RateLimitUtil.getCurrentUsed() < RateLimitUtil.getLimitPerMinute()) {
            return send();
        }
        return null;
    }
}
