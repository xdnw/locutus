package link.locutus.discord.commands.manager.v2.impl.pw.binding.autocomplete;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Autocomplete;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.PlaceholderType;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.CustomSheet;
import link.locutus.discord.db.entities.SelectionAlias;
import link.locutus.discord.db.entities.SheetTemplate;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.util.StringMan;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SheetCompleter extends BindingHelper {
    @Autocomplete
    @Binding(types={SelectionAlias.class})
    public List<String> SelectionAlias(@Me GuildDB db, String input) {
        List<String> options = new ArrayList<>(db.getSheetManager().getSelectionAliasNames());
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true, false);
    }

    @Autocomplete
    @Binding(types={SheetTemplate.class})
    public List<String> SheetTemplate (@Me GuildDB db, String input) {
        List<String> options = new ArrayList<>(db.getSheetManager().getSheetTemplateNames(true));
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true, false);
    }

    @Autocomplete
    @Binding(types={CustomSheet.class})
    public List<String> CustomSheet (@Me GuildDB db, String input) {
        List<String> options = new ArrayList<>(db.getSheetManager().getCustomSheets().keySet());
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true, false);
    }

    @Autocomplete
    @Binding(types = {PlaceholderType.class, Class.class})
    public List<String> PlaceholderType(String input) {
        Set<Class<?>> optionClasses = Locutus.cmd().getV2().getPlaceholders().getTypes();
        List<String> options = optionClasses.stream().map(PlaceholdersMap::getClassName).collect(Collectors.toList());
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true, false);
    }

    @Autocomplete
    @Binding(types = {SheetKey.class})
    public List<String> SheetKey(String input) {
        List<String> options = Arrays.stream(SheetKey.values()).map(SheetKey::name).collect(Collectors.toList());
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true, false);
    }
}
