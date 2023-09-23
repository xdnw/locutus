package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import com.google.gson.reflect.TypeToken;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.FunctionConsumerParser;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Autocomplete;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ArrayUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public class PWType2Math extends BindingHelper {
    @Binding(types = {Map.class, ResourceType.class, Double.class}, multiple = true)
    public ArrayUtil.DoubleArray toDoubleArray(Map<ResourceType, Double> input) {
        return new ArrayUtil.DoubleArray(PnwUtil.resourcesToArray(input));
    }
}
