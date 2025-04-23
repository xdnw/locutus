package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.CreateSheet;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.PlaceholderType;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.CustomSheet;
import link.locutus.discord.db.entities.SelectionAlias;
import link.locutus.discord.db.entities.SheetTemplate;
import link.locutus.discord.db.entities.sheet.CustomSheetManager;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.sheet.GoogleDoc;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.sheet.templates.TransferSheet;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class SheetBindings extends BindingHelper {

    @PlaceholderType
    @Binding(value = "An entity type for a placeholder\n" +
            "Used for sheets or formatted messages", examples = {"nation", "city", "alliance", "war"})
    public static Class type(String input) {
        Set<Class<?>> types = Locutus.cmd().getV2().getPlaceholders().getTypes();
        for (Class<?> type : types) {
            if (PlaceholdersMap.getClassName(type).equalsIgnoreCase(input) || type.getSimpleName().equalsIgnoreCase(input)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid type: `" + input + "`. Options: " + StringMan.getString(types.stream().map(PlaceholdersMap::getClassName)));
    }

    @Binding(value = "A list of whole numbers (comma separated)")
    public List<Integer> columns(String input) {
        // newline, space or comma
        String[] split = input.split("[\n ,]");
        List<Integer> result = new ArrayList<>();
        for (String s : split) {
            try {
                result.add(Integer.parseInt(s));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid column number: `" + s + "`");
            }
        }
        return result;
    }

    @Binding
    public CustomSheetManager manager(@Me GuildDB db) {
        return db.getSheetManager();
    }

    @Binding(value = "A sheet template name that has been created in this guild\n" +
            "Sheet templates are column formats for a sheet\n" +
            "Templates, each with a selection can be used to generate multi-tabbed spreadsheets\n" +
            "If the command supports it, you can specify a new template inline")
    public static SheetTemplate template(@Me GuildDB db, ParameterData data, CustomSheetManager manager, String name) {
        if (name.contains("{")) {
            if (data == null || data.getAnnotation(CreateSheet.class) == null) {
                templateError(name, "This function only permits existing templates.\n", manager);
            }
            List<String> columns = StringMan.split(name, " ");
            return new SheetTemplate<>(name, null, columns);
        }
        SheetTemplate template = manager.getSheetTemplate(name);
        if (template == null) {
            templateError(name, "Specify placeholders " + MarkupUtil.markdownUrl("types", "https://github.com/xdnw/locutus/wiki/custom_spreadsheets#selection-types") + " for a placeholders", manager);
        }
        return template;
    }

    private static void templateError(String name, String msg, CustomSheetManager manager) {
        Set<String> options = manager.getSheetTemplateNames(true);
        throw new IllegalArgumentException(msg + "No template found with name `" + name + "`. Options: " + StringMan.getString(options) + ". Or create a sheet template with `/sheet_template add <type>`");
    }

    public static SelectionAlias selectionAlias(boolean allowInline, CustomSheetManager manager, ValueStore store, String name) {
        SelectionAlias<Object> alias = manager.getSelectionAlias(name, false);
        if (alias != null) return alias;
        if (allowInline) {
            PlaceholdersMap phM = Locutus.cmd().getV2().getPlaceholders();
            Set<Class<?>> types = phM.getTypes();
            for (Class type : types) {
                String typeName = PlaceholdersMap.getClassName(type);
                String prefixRegex = typeName.toLowerCase(Locale.ROOT) + ":(\\w+)|" + typeName.toLowerCase(Locale.ROOT) + ":";
                if (!name.startsWith(typeName) && !name.matches(prefixRegex + ".*")) {
                    continue;
                }
                List<String> split = StringMan.split(name, ":", 2);
                if (split.size() != 2) {
                    continue;
                }
                String typeNameAndModifier = split.get(0);
                String modifier = null;
                if (typeNameAndModifier.contains("(")) {
                    int start = typeNameAndModifier.indexOf("(");
                    int end = typeNameAndModifier.lastIndexOf(")");
                    modifier = typeNameAndModifier.substring(start + 1, end);
                }
                String filter = split.get(1);
                Placeholders parser = phM.get(type);
                parser.parseSet(store, filter);
                return new SelectionAlias<>("", type, filter, modifier);
            }
        }
        Set<String> options = manager.getSelectionAliasNames();
        throw new IllegalArgumentException("No selection alias found with name `" + name + "`. Options: " + StringMan.getString(options) + ". Or create one with `/selection_alias add <type> <name> <selection>`");
    }

    @Binding(value = "A selection alias name that has been created in this guild\n" +
            "Used to reference a list of nations or other entities that can be used in commands and sheets\n" +
            "If the command supports it, you can specify a new selection alias inline e.g. `nation:*,#cities>10`")
    public static SelectionAlias selectionAlias(ParameterData data, CustomSheetManager manager, ValueStore store, String name) {
        boolean allowInline = data != null && data.getAnnotation(CreateSheet.class) != null;
        return selectionAlias(allowInline, manager, store, name);
    }

    @Binding("A comma separated list of spreadsheets")
    public Set<SpreadSheet> sheets(String input) throws GeneralSecurityException, IOException {
        List<String> split = StringMan.split(input, ",");
        Set<SpreadSheet> sheets = new ObjectLinkedOpenHashSet<>();
        for (String s : split) {
            sheets.add(sheet(s));
        }
        return sheets;
    }

    @Binding(value = "A custom sheet name that has been created in this guild\n" +
            "Custom sheets have named tabs comprised of template-selection pairs")
    public CustomSheet customSheet(@Me GuildDB db, CustomSheetManager manager, String name, ParameterData data) throws GeneralSecurityException, IOException {
        name = name.toLowerCase(Locale.ROOT);
        CreateSheet createSheet = data == null ? null : data.getAnnotation(CreateSheet.class);
        CustomSheet sheet = manager.getCustomSheet(name);
        if (sheet == null) {
            if (createSheet == null) {
                Set<String> options = manager.getCustomSheets().keySet();
                throw new IllegalArgumentException("No custom sheet found with name `" + name + "`. Options: " + StringMan.getString(options));
            }
            SpreadSheet mySheet = SpreadSheet.createTitle(name);
            manager.addCustomSheet(name, mySheet.getSpreadsheetId());
            sheet = new CustomSheet(name, mySheet.getSpreadsheetId(), new LinkedHashMap<>());
        }
        return sheet;
    }

    @Binding(value = "A google spreadsheet id or url\n" +
            "For shorthand, use a comma when specifying the sheet tab e.g. `sheet:ID,TAB_NAME`" +
            "For a url, append `#gid=1234` or `#tab=tabName` to specify the id of the tab to use", examples = {"sheet:1X2Y3Z4", "https://docs.google.com/spreadsheets/d/1X2Y3Z4/edit#gid=0"})
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

    @Binding(value = "The name of a premade sheet\n" +
            "Premade sheet commands assign a google sheet to a key, so subsequent commands use the same sheet")
    public SheetKey key(String input) {
        return emum(SheetKey.class, input);
    }
}
