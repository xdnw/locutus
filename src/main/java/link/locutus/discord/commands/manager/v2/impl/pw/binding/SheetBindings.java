package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.PlaceholderType;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.CustomSheet;
import link.locutus.discord.db.entities.SelectionAlias;
import link.locutus.discord.db.entities.SheetTemplate;
import link.locutus.discord.db.entities.sheet.CustomSheetManager;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.sheet.GoogleDoc;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.sheet.templates.TransferSheet;
import net.dv8tion.jda.api.entities.Guild;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Set;

public class SheetBindings extends BindingHelper {

    @PlaceholderType
    @Binding(value = "An entity type for a placeholder\n" +
            "Used for sheets or formatted messages", examples = {"nation", "city", "alliance", "war"})
    public Class type(String input) {
        Set<Class<?>> types = Locutus.cmd().getV2().getPlaceholders().getTypes();
        for (Class<?> type : types) {
            if (PlaceholdersMap.getClassName(type).equalsIgnoreCase(input)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid type: `" + input + "`. Options: " + StringMan.getString(types.stream().map(PlaceholdersMap::getClassName)));
    }

    @Binding
    public CustomSheetManager manager(@Me GuildDB db) {
        return db.getSheetManager();
    }
    // SheetTemplate
    // selectionAlias
    // customSheet
    @Binding(value = "A sheet template name that has been created in this guild\n" +
            "Sheet templates are column formats for a sheet\n" +
            "Templates, each with a selection can be used to generate multi-tabbed spreadsheets")
    public SheetTemplate template(@Me GuildDB db, CustomSheetManager manager, String name) {
        SheetTemplate template = manager.getSheetTemplate(name);
        if (template == null) {
            Set<String> options = manager.getSheetTemplateNames();
            throw new IllegalArgumentException("No template found with name `" + name + "`. Options: " + StringMan.getString(options));
        }
        return template;
    }

    @Binding(value = "A selection alias name that has been created in this guild\n" +
            "A selection alias is used to reference a list of nations or other entities that can be used in commands and sheets")
    public SelectionAlias selectionAlias(@Me GuildDB db, CustomSheetManager manager, String name) {
        SelectionAlias<Object> alias = manager.getSelectionAlias(name);
        if (alias == null) {
            Set<String> options = manager.getSelectionAliasNames();
            throw new IllegalArgumentException("No selection alias found with name `" + name + "`. Options: " + StringMan.getString(options));
        }
        return alias;
    }

    @Binding(value = "A custom sheet name that has been created in this guild\n" +
            "Custom sheets have named tabs comprised of template-selection pairs")
    public CustomSheet customSheet(@Me GuildDB db, CustomSheetManager manager, String name) {
        CustomSheet sheet = manager.getCustomSheet(name);
        if (sheet == null) {
            Set<String> options = manager.getCustomSheets().keySet();
            throw new IllegalArgumentException("No custom sheet found with name `" + name + "`. Options: " + StringMan.getString(options));
        }
        return sheet;
    }

    @Binding(value = "A google spreadsheet id or url", examples = {"sheet:1X2Y3Z4", "https://docs.google.com/spreadsheets/d/1X2Y3Z4/edit#gid=0"})
    public SpreadSheet sheet(String input) throws GeneralSecurityException, IOException {
        if (input.startsWith("sheet:")) {
        } else if (input.startsWith("https://docs.google.com/spreadsheets/")) {
        } else {
            throw new IllegalArgumentException("Invalid sheet: `" + input + "`");
        }
        return SpreadSheet.create(input);
    }

    @Binding(value = "A google spreadsheet id or url. Must have a `nation` or `leader` column as well as the names of each resource", examples = {"sheet:1X2Y3Z4", "https://docs.google.com/spreadsheets/d/1X2Y3Z4/edit#gid=0"})
    public TransferSheet transferSheet(String input) throws GeneralSecurityException, IOException {
        sheet(input); // validate
        TransferSheet sheet = new TransferSheet(input);

        Set<String> invalid = sheet.read();
        if (!invalid.isEmpty())
            throw new IllegalArgumentException("Invalid nations/alliances: " + StringMan.getString(invalid));

        return sheet;
    }

    @Binding(value = "A google document id or url", examples = {"document:1X2Y3Z4", "https://docs.google.com/document/d/1X2Y3Z4/edit"})
    public GoogleDoc doc(String input) throws GeneralSecurityException, IOException {
        if (input.startsWith("document:")) {
        } else if (input.startsWith("https://docs.google.com/document/")) {
        } else {
            throw new IllegalArgumentException("Invalid document: `" + input + "`");
        }
        return GoogleDoc.create(input);
    }
}
