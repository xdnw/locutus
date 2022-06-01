package link.locutus.discord.commands.manager.v2.binding.bindings.serializer;

import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Serializer;

public class PrimitiveSerializer extends BindingHelper {

    @Binding(types = {
            int.class,
            Integer.class,
    })
    @Serializer
    public String serializeInt(Object input) {
        return input.toString();
    }
}
