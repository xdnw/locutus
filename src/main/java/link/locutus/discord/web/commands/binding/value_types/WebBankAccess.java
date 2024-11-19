package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.apiv1.enums.AccessType;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Map;
import java.util.stream.Collectors;

public class WebBankAccess {
    public final Map<String, Integer> access;

    public WebBankAccess(Map<Long, AccessType> allowed) {
        this.access = allowed.entrySet().stream().collect(Collectors.toMap(f -> f.getKey() + "", f -> f.getValue().ordinal()));
    }
}