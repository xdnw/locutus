package link.locutus.discord.gpt;

import java.util.Arrays;

public enum ProviderType {
    OPENAI,
    GOOGLE,
    LOCAL,

    ;

    public static ProviderType parse(String name) {
        for (ProviderType type :values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown provider type: " + name + ". Valid values are: " + Arrays.toString(values()));
    }
}
