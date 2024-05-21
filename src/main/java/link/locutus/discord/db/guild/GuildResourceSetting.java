package link.locutus.discord.db.guild;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.math.ArrayUtil;
import net.dv8tion.jda.api.entities.User;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public abstract class GuildResourceSetting extends GuildSetting<Map<ResourceType, Double>> {
    public GuildResourceSetting(GuildSettingCategory category) {
        super(category, Map.class, ResourceType.class, Double.class);
    }

    @Override
    public Map<ResourceType, Double> validate(GuildDB db, User user, Map<ResourceType, Double> value) {
        // ensure amounts are positive
        for (Map.Entry<ResourceType, Double> entry : value.entrySet()) {
            if (entry.getValue() < 0) {
                throw new IllegalArgumentException("Amounts must be positive (" + entry.getKey() + " x" + MathMan.format(entry.getValue()) + ")");
            }
        }
        return value;
    }

    @Override
    public String toString(Map<ResourceType, Double> value) {
        double[] arr = ResourceType.resourcesToArray(value);
        byte[] bytes = ArrayUtil.toByteArray(arr);
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    @Override
    public String toReadableString(GuildDB db, Map<ResourceType, Double> value) {
        return ResourceType.resourcesToString(value);
    }

    @Override
    public Map<ResourceType, Double> parse(GuildDB db, String input) {
        if (input.startsWith("{") && input.endsWith("}")) {
            return ResourceType.parseResources(input);
        }
        return ResourceType.resourcesToMap(ArrayUtil.toDoubleArray(input.getBytes(StandardCharsets.ISO_8859_1)));
    }
}
