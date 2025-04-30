package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.util.math.ArrayUtil;

import java.util.Map;

public class PWType2Math extends BindingHelper {
    @Binding(types = {Map.class, ResourceType.class, Double.class}, multiple = true)
    public ArrayUtil.DoubleArray toDoubleArray(Map<ResourceType, Double> input) {
        return new ArrayUtil.DoubleArray(ResourceType.resourcesToArray(input));
    }
}
