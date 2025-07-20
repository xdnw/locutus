package link.locutus.discord.commands.manager.v2.command;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.util.RateLimitUtil;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public static Map<String, String> getPlaceholders(Collection<String> allowedPlaceholderNames, String text) {
        Map<String, String> placeholders = new HashMap<>();
        // Build a regex pattern based on the allowed placeholder names
        String regexPattern = "\\{(" + String.join("|", allowedPlaceholderNames) + ")(?:=(.*?))?\\}";
        Pattern pattern = Pattern.compile(regexPattern);
        Matcher matcher = pattern.matcher(text);
        // Find all matches and populate the placeholders map
        while (matcher.find()) {
            String placeholderName = matcher.group(1);
            String placeholderValue = matcher.group(2); // May be null
            placeholders.put(placeholderName, placeholderValue);
        }
        return placeholders;
    }

    default IModalBuilder create(ICommand command, Map<String, String> defaultValues, List<String> promptForArguments) {
        setTitle(command.getFullPath(" "));
        UUID id = getId();
        if (id == null) {
            id = UUID.randomUUID();
            setId(id);
        }

        Map<String, ParameterData> paramMap = command.getUserParameterMap();
        Map<String, String> aliases = command.getArgumentAliases(true);
        promptForArguments = promptForArguments.stream().map(s -> paramMap.containsKey(s) ? s : aliases.getOrDefault(s, s)).toList();
        // validate arguments, remap the key to the getAlias, keep value the same
        // remap the promptForArguments using aliases as well
        // do not remap if  it exists in paramMap already
        defaultValues = defaultValues.entrySet().stream().collect(HashMap::new, (m, e) -> {
            String key = paramMap.containsKey(e.getKey()) ? e.getKey() : aliases.getOrDefault(e.getKey(), e.getKey());
            m.put(key, e.getValue());
        }, HashMap::putAll);

        for (String key : defaultValues.keySet()) {
            if (!paramMap.containsKey(key)) {
                throw new IllegalArgumentException("Argument `" + key + "` is not a valid argument for command " + command.getFullPath(" ") + "\n" +
                        "Options: " + paramMap.keySet());
            }
        }

        outer:
        for (String key : promptForArguments) {
            ParameterData param = paramMap.get(key);
            if (param != null) {
                String desc = param.getName() + ": " + param.getExpandedDescription(false, false, true);
                if (desc.length() > 45) {
                    // 3 dots unicode char
                    desc = desc.substring(0, 44) + "\u2026";
                }

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
                continue;
            }

            // Compile the regex pattern
            for (Map.Entry<String, String> defEntry : defaultValues.entrySet()) {
                String stringWithPh = defEntry.getValue();
                Map<String, String> placeholders = getPlaceholders(List.of(key), stringWithPh);
                String defValue = placeholders.get(key);
                if (!placeholders.containsKey(key)) {
                    continue;
                }
                String label = "Placeholder " + key + " in " + defEntry.getKey();
                TextInput.Builder builder = TextInput.create(key, label, TextInputStyle.PARAGRAPH);

                if (defValue != null) {
                    builder = builder.setPlaceholder(defValue);
                    builder = builder.setValue(defValue);
                }
                builder.setRequired(true);
                addInput(builder.build());
                continue outer;
            }
            throw new IllegalArgumentException("Argument " + key + " is not a valid argument for command " + command.getFullPath(" ") + "\n" +
                    "Options: " + paramMap.keySet().toString());

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

    String getTitle();
}
