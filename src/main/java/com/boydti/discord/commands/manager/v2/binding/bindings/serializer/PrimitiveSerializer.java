package com.boydti.discord.commands.manager.v2.binding.bindings.serializer;

import com.boydti.discord.commands.manager.v2.binding.BindingHelper;
import com.boydti.discord.commands.manager.v2.binding.annotation.Binding;
import com.boydti.discord.commands.manager.v2.binding.annotation.Serializer;

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
