package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.siegmar.fastcsv.reader.CloseableIterator;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRow;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.SheetBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.CustomSheet;
import link.locutus.discord.db.entities.SelectionAlias;
import link.locutus.discord.db.entities.SheetTemplate;
import link.locutus.discord.db.entities.sheet.CustomSheetManager;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.web.WebUtil;
import net.dv8tion.jda.api.entities.Message;
import org.jetbrains.annotations.Unmodifiable;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import static link.locutus.discord.config.Messages.TAB_TYPE;

public class CustomSheetCommands {

    @NoFormat
    @Command(desc = "List the sheet keys in use", viewable = true)
    @RolePermission(value = {Roles.ADMIN})
    public String listSheetKeys(@Me GuildDB db) {
        StringBuilder result = new StringBuilder();
        for (SheetKey key : SheetKey.values()) {
            String info = db.getInfo(key, false);
            if (info == null || info.isEmpty()) {
                result.append("- ").append(key.name()).append("\n");
                continue;
            }

            String[] split = info.split(",", 2);
            String sheetId = split[0];
            String tabId = split.length > 1 ? split[1] : null;

            String url = "<https://docs.google.com/spreadsheets/d/" + sheetId + "/edit>";
            if (tabId != null) {
                url += "#gid=" + tabId;
            }
            result.append("- " + MarkupUtil.markdownUrl(key.name(), url));
            if (tabId != null) {
                result.append(" (#gid").append(tabId).append(")");
            }
            result.append("\n");
        }
        if (result.isEmpty()) {
            return "No sheet keys found";
        }
        return result.toString();
    }

    @NoFormat
    @Command(desc = "Set the url used for a sheet key")
    @RolePermission(value = {Roles.ADMIN})
    public String setSheetKey(@Me GuildDB db, SheetKey key, String sheetId, @Default String tab) throws GeneralSecurityException, IOException {
        SpreadSheet sheet = SpreadSheet.create(sheetId);
        Map<Integer, String> tabs = sheet.fetchTabs();
        Integer tabId = null;
        if (tab != null) {
            for (Map.Entry<Integer, String> entry : tabs.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(tab)) {
                    tabId = entry.getKey();
                    break;
                }
            }
            if (tabId == null) {
                return "Tab not found: `" + tab + "`\n" +
                        "Valid options: " + StringMan.getString(tabs.values());
            }
        }
        String combined = sheet.getSpreadsheetId();
        if (tabId != null) combined += "," + tabId;
        db.setInfo(key, combined);
        return "Set `" + key.name() + "` to `" + combined + "`";
    }

    @NoFormat
    @Command(desc = "Rename a sheet template")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String renameTemplate(@Me GuildDB db, SheetTemplate sheet, String name) {
        name = name.toLowerCase(Locale.ROOT);
        // ensure name is alphanumeric _
        if (!name.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Template name must be alphanumericunderscore, not `" + name + "`");
        }
        for (String other : db.getSheetManager().getSheetTemplateNames(false)) {
            if (other.equalsIgnoreCase(name)) {
                throw new IllegalArgumentException("Template name `" + name + "` already exists");
            }
        }
        // rename it
        db.getSheetManager().renameSheetTemplate(sheet, name);
        return "Renamed sheet template `" + sheet.getName() + "` to `" + name + "`";
    }

    @NoFormat
    @Command(desc = "Rename a selection alias")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String renameSelection(@Me GuildDB db, SelectionAlias sheet, String name) {
        name = name.toLowerCase(Locale.ROOT);
        // ensure name is alphanumeric _
        if (!name.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Selection alias must be alphanumericunderscore, not `" + name + "`");
        }
        for (String other : db.getSheetManager().getSelectionAliasNames()) {
            if (other.equalsIgnoreCase(name)) {
                throw new IllegalArgumentException("Selection alias `" + name + "` already exists");
            }
        }
        // rename it
        db.getSheetManager().renameSelectionAlias(sheet, name);
        return "Renamed selection alias `" + sheet.getName() + "` to `" + name + "`";
    }

    @NoFormat
    @Command(desc = "List sheet templates for this guild", viewable = true)
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String listSheetTemplates(@Me GuildDB db, @Default @PlaceholderType Class type) {
        List<String> errors = new ArrayList<>();
        Map<String, SheetTemplate> sheets = db.getSheetManager().getSheetTemplates(errors);
        if (type != null) {
            sheets.entrySet().removeIf(entry -> !entry.getValue().getType().equals(type));
        }
        if (sheets.isEmpty()) {
            return "No custom sheets found" + (type == null ? "" : " of type `" + type.getSimpleName() + "`");
        }
        List<Map.Entry<String, SheetTemplate>> entries = new ArrayList<>(sheets.entrySet());
        // sort by type simple name, then name
        Collections.sort(entries, Comparator.comparing((Map.Entry<String, SheetTemplate> o) -> o.getValue().getType().getSimpleName()).thenComparing(Map.Entry::getKey));

        Map<String, List<SheetTemplate>> groupByTypeName = new LinkedHashMap<>();
        for (Map.Entry<String, SheetTemplate> entry : entries) {
            String typeName = entry.getValue().getType().getSimpleName();
            groupByTypeName.computeIfAbsent(typeName, k -> new ArrayList<>()).add(entry.getValue());
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<SheetTemplate>> entry : groupByTypeName.entrySet()) {
            sb.append("**").append(entry.getKey()).append("**\n");
            for (SheetTemplate sheet : entry.getValue()) {
                sb.append("- " + sheet.getName() + ": `" + StringMan.join(sheet.getColumns(), " ") + "`\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @NoFormat
    @Command(desc = "List selection aliases for this guild", viewable = true)
    public String listSelectionAliases(@Me GuildDB db, @Default @PlaceholderType Class type) {
        Map<Class, Map<String, SelectionAlias>> selections = new LinkedHashMap<>(db.getSheetManager().getSelectionAliases());
        if (type != null) {
            selections.entrySet().removeIf(entry -> !entry.getKey().equals(type));
        }
        if (selections.isEmpty()) {
            return "No selection aliases found" + (type == null ? "" : " of type `" + PlaceholdersMap.getClassName(type) + "`");
        }
        StringBuilder sb = new StringBuilder("__**" + selections.size() + " selection aliases found:**__\n");
        for (Map.Entry<Class, Map<String, SelectionAlias>> entry : selections.entrySet()) {
            sb.append("**").append(entry.getKey().getSimpleName()).append("**\n");
            for (Map.Entry<String, SelectionAlias> selection : entry.getValue().entrySet()) {
                sb.append("- `").append(selection.getKey()).append("` -> `").append(selection.getValue().getSelection()).append("`\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @NoFormat
    @Command(desc = "List custom sheets for this guild", viewable = true)
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String listCustomSheets(@Me GuildDB db) {
        Map<String, String> sheets = db.getSheetManager().getCustomSheets();
        if (sheets.isEmpty()) {
            return "No custom sheets found. Create one with " + CM.sheet_custom.add_tab.cmd.toSlashMention();
        }
        // google sheet
        String prefix = "https://docs.google.com/spreadsheets/d/";
        String suffix = "/edit?usp=sharing";
        StringBuilder response = new StringBuilder("**Custom Sheets**\n");
        for (Map.Entry<String, String> entry : sheets.entrySet()) {
            response.append("- ").append(MarkupUtil.markdownUrl(entry.getKey(), prefix + entry.getValue() + suffix)).append("\n");
        }
        return response.toString();
    }

    @NoFormat
    @Command(desc = "View a custom sheet")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String deleteSelectionAlias(@Me GuildDB db, SelectionAlias selection) {
        db.getSheetManager().removeSelectionAlias(selection.getName());
        return "Deleted selection with alias `" + selection.getName() + "`\n" +
                "- `" + selection.getSelection() + "`";
    }

    @NoFormat
    @Command(desc = "View a sheet template", viewable = true)
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String viewTemplate(SheetTemplate sheet) {
        return sheet.toString() + "\n\n" +
                "See: " + CM.sheet_custom.add_tab.cmd.toSlashMention() + " | " + CM.sheet_custom.remove_tab.cmd.toSlashMention() + " | " + CM.sheet_custom.update.cmd.toSlashMention();
    }

    @NoFormat
    @Command(desc = "Delete a sheet template")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String deleteTemplate(@Me GuildDB db, SheetTemplate sheet) {
        db.getSheetManager().deleteSheetTemplate(sheet.getName());
        return "Deleted sheet template `" + sheet.getName() + "`";
    }

    @NoFormat
    @Command(desc = "Remove columns in a sheet template")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String deleteColumns(@Me GuildDB db, SheetTemplate sheet, List<Integer> columns) {
        List<Integer> toRemove = new ArrayList<>();
        for (int column : columns) {
            if (column <= 0 || column > sheet.getColumns().size()) {
                return "Cannot remove column: `" + column + "`. Max column is `" + (sheet.getColumns().size()) + "`";
            }
            toRemove.add(column - 1);
        }
        toRemove.sort(Integer::compareTo);
        for (int i = toRemove.size() - 1; i >= 0; i--) {
            sheet.getColumns().remove((int) toRemove.get(i));
        }

        db.getSheetManager().addSheetTemplate(sheet);
        return "Removed columns `" + columns + "` from sheet `" + sheet.getName() + "`\n" +
                "See: " + CM.sheet_custom.view.cmd.toSlashMention();

    }

    //- add_tab <tab-name> <selector> <template>
    @NoFormat
    @Command(desc = """
            Add a tab to a custom sheet
            Tabs are named and are comprised of a selection alias (rows) and a sheet template (columns)
            You must create a selection alias and sheet template first
            Sheets must be generated/updated with the update command""")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String addTab(@Me JSONObject command, @Me IMessageIO io, @Me GuildDB db, @CreateSheet CustomSheet sheet, String tabName, @CreateSheet SelectionAlias select, @CreateSheet SheetTemplate columns, @Switch("f") boolean force) {
        columns = columns.resolve(select.getType());
        tabName = tabName.toLowerCase(Locale.ROOT);
        // ensure name is alphanumeric _
        if (!tabName.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Tab name must be alphanumericunderscore, not `" + tabName + "`");
        }
        // tabs
        Map.Entry<SelectionAlias, SheetTemplate> existingTab = sheet.getTab(tabName);
        if (!force) {
            String title = (existingTab == null ? "Add" : "Replace existing") + " tab: `" + tabName + "`";
            StringBuilder body = new StringBuilder();
            SelectionAlias previousAlias = existingTab == null ? null : existingTab.getKey();
            SheetTemplate previousTemplate = existingTab == null ? null : existingTab.getValue();
            if (previousAlias == null) {
                body.append("**Selection:** `").append(select.getName()).append("`\n");
                body.append("- Type: `").append(select.getType().getSimpleName()).append("`\n");
                body.append("- Selection: `").append(select.getSelection()).append("`\n");
            } else if (select.getName().equalsIgnoreCase(previousAlias.getName())) {
                body.append("**Selection:** `" + select.getName() + "` (no change)\n");
            } else {
                body.append("**Selection:** `").append(previousAlias.getName()).append("` -> `").append(select.getName()).append("`\n");
                body.append("- Type: `").append(previousAlias.getType().getSimpleName()).append("` -> `").append(select.getType().getSimpleName()).append("`\n");
                body.append("- Selection: `").append(previousAlias.getSelection()).append("` -> `").append(select.getSelection()).append("`\n");
            }
            if (previousTemplate == null) {
                body.append("**Template:** `").append(columns.getName()).append("`\n");
                body.append("- Type: `").append(columns.getType().getSimpleName()).append("`\n");
                body.append("- Columns: `").append(columns.getColumns().size()).append("`\n");
            } else if (columns.getName().equalsIgnoreCase(previousTemplate.getName())) {
                body.append("**Template:** `" + columns.getName() + "` (no change)\n");
            } else {
                body.append("**Template:** `").append(previousTemplate.getName()).append("` -> `").append(columns.getName()).append("`\n");
                body.append("- Type: `").append(previousTemplate.getType().getSimpleName()).append("` -> `").append(columns.getType().getSimpleName()).append("`\n");
                body.append("- Columns: `").append(previousTemplate.getColumns().size()).append("` -> `").append(columns.getColumns().size()).append("`\n");
            }

            io.create().confirmation(title, body.toString(), command).send();
            return null;
        }

        db.getSheetManager().addCustomSheetTab(sheet.getName(), tabName, select.getName(), columns.getName());

        return "**__sheet:" + sheet.getName() + "__**\n" + (existingTab == null ? " Added " : "Updated") + " tab `" + tabName + "`\n" +
                "- Selection: `" + select.getName() + "`\n" +
                "- Template: `" + columns.getName() + "`\n" +
                "- Url: <" + sheet.getUrl() + ">\n" +
                CM.sheet_custom.update.cmd.toSlashMention() + " to update the sheet\n" +
                "See: " + CM.sheet_custom.view.cmd.toSlashMention() + " | " + CM.sheet_custom.remove_tab.cmd.toSlashMention() + " | " + CM.sheet_custom.update.cmd.toSlashMention();
    }

    @Command(desc = """
            Update the tabs in a custom sheet and return the url
            A sheet update may produce errors if a selection is no longer valid
            Tabs in the google sheet which aren't registered will be ignored""")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String updateSheet(CustomSheet sheet, ValueStore store) throws GeneralSecurityException, IOException {
        List<String> errors = sheet.update(store, null);

        StringBuilder result = new StringBuilder();
        result.append("Updating sheet: `").append(sheet.getName()).append("` (").append(sheet.getTabs().size()).append(" tabs)\n");
        result.append("Url: <").append(sheet.getUrl()).append(">\n");
        if (errors.isEmpty()) {
            result.append("- No errors");
        } else {
            result.append("Update Info:\n");
            for (String error : errors) {
                result.append("- ").append(error).append("\n");
            }
        }
        return result.toString();
    }

    @Command(desc = "Unregister a tab from a custom sheet\n" +
            "The tab wont be deleted from the google sheet, but it will no longer be updated. You may manually delete it from the google sheet")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String deleteTab(@Me GuildDB db, CustomSheet sheet, String tabName) {
        tabName = tabName.toLowerCase(Locale.ROOT);
        Map.Entry<SelectionAlias, SheetTemplate> tab = sheet.getTab(tabName);
        if (tab == null) {
            db.getSheetManager().deleteCustomSheetTab(sheet.getName(), tabName);
            return "No tab found with name: `" + tabName + "` in `" + sheet.getName() + "`.\n" +
                    "Valid options: " + StringMan.getString(sheet.getTabs());
        }
        db.getSheetManager().deleteCustomSheetTab(sheet.getName(), tabName);
        return "Deleted tab `" + tabName + "` from `" + sheet.getName() + "`\n" +
                "See: " + CM.sheet_custom.view.cmd.toSlashMention();
    }

    @Command(desc = "Get the google sheet url and view the tabs for a custom sheet, and their respective selection alias (rows) and sheet template (columns)", viewable = true)
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String info(CustomSheet sheet) {
        return sheet.toString();
    }

    @Command(desc = """
            Update a spreadsheet's tab from a url
            The tab specified by the URL (`#guid`) must be named a valid selection, prefixed by the type e.g. `nation:*`
            The first row must have placeholders in each column, such as `{nation}` `{cities}` `{score}`""")
    public String autoTab(ValueStore store, @Me IMessageIO io, @Me GuildDB db, SpreadSheet sheet, @Switch("s") boolean saveSheet) throws GeneralSecurityException, IOException {
        String defaultTab = sheet.getDefaultTab();
        Integer defaultTabId = sheet.getDefaultTabId();
        if (defaultTab == null && defaultTabId == null) {
            throw new IllegalArgumentException("No tab specified in the sheet url you provided. Please copy a google sheet tab URL with a `#guid` provided");
        }
        return autoPredicate(store, io, db, sheet, saveSheet, (id, name) -> {
            return (defaultTabId != null && id != null && id.equals(defaultTabId)) ||
                     (defaultTab != null && name != null && name.equalsIgnoreCase(defaultTab)) ||
                     (defaultTabId == null && defaultTab == null && id != null && id.equals(0));
        });
    }

    @Command(desc = """
            Generate or update a spreadsheet from a url
            Each tab must be a valid selection, prefixed by the type e.g. `nation:*`
            The first row must have placeholders in each column, such as `{nation}` `{cities}` `{score}`""")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.INTERNAL_AFFAIRS, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String auto(ValueStore store, @Me IMessageIO io, @Me GuildDB db, SpreadSheet sheet, @Switch("s") boolean saveSheet) throws GeneralSecurityException, IOException {
        return autoPredicate(store, io, db, sheet, saveSheet, (_, _) -> true);
    }

    private String autoPredicate(ValueStore store, @Me IMessageIO io, @Me GuildDB db, SpreadSheet sheet, @Switch("s") boolean saveSheet, BiPredicate<Integer, String> allowTab) throws GeneralSecurityException, IOException {
        CustomSheetManager manager = db.getSheetManager();
        PlaceholdersMap phMap = Locutus.cmd().getV2().getPlaceholders();

        List<String> messageList = new ObjectArrayList<>();
        Map<String, List<String>> errorGroups = new Object2ObjectLinkedOpenHashMap<>();
        Supplier<List<String>> toErrorList = () -> {
            List<String> errors = new ArrayList<>(messageList);
            for (Map.Entry<String, List<String>> entry : errorGroups.entrySet()) {
                String key = entry.getKey();
                List<String> values = entry.getValue();
                if (values.isEmpty()) {
                    continue;
                }
                String error = key.replace("{value}", "`" + StringMan.join(values, "`,`") + "`");
                errors.add(error);
            }
            return errors;
        };
        Supplier<Boolean> hasErrors = () -> !messageList.isEmpty() || !errorGroups.isEmpty();

        CustomSheet custom = manager.getCustomSheetById(sheet.getSpreadsheetId());
        if (custom == null) {
            if (saveSheet) {
                String name = sheet.getTitle();
                CustomSheet existing = manager.getCustomSheet(name);
                if (existing != null) {
                    return "Sheet with name `" + name + "` already exists: <" + existing.getUrl() + ">. Please change the title of your spreadsheet to something unique or delete the existing sheet using ";
                }
                manager.addCustomSheet(name, sheet.getSpreadsheetId());
                custom = manager.getCustomSheetById(sheet.getSpreadsheetId());
            } else {
                custom = new CustomSheet(sheet.getTitle(), sheet.getSpreadsheetId(), new LinkedHashMap<>());
            }
        } else {
            String msg = "A custom sheet with the name `" + custom.getName() + "` has row selections and column templates already saved.\n" +
                    "These saved tabs will be used when new selection is specified for a tab.";
            if (saveSheet) {
                msg += "\nChanges will be saved";
            }
            messageList.add(msg);
        }

        Map<String, Map.Entry<SelectionAlias, SheetTemplate>> customTabs = new LinkedHashMap<>();

        Map<Integer, String> tabs = sheet.fetchTabs();


        Map<String, SelectionAlias> customTabsToFetch = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : tabs.entrySet()) {
            if (!allowTab.test(entry.getKey(), entry.getValue())) {
                continue;
            }
            String tabName = entry.getValue();
            SelectionAlias selection;
            try {
                selection = SheetBindings.selectionAlias(true, manager, store, tabName);
            } catch (IllegalArgumentException e) {
                int index = tabName.indexOf(":");
                int barIndex = tabName.indexOf(" | ");
                if (index == -1) {
                    errorGroups.computeIfAbsent(TAB_TYPE, k -> new ObjectArrayList<>()).add(tabName);
                    continue;
                }
                String typeStr = tabName.substring(0, index);
                if (barIndex < index && barIndex != -1) {
                    typeStr = typeStr.substring(barIndex + 3);
                }
                String typeModifier = null;
                if (typeStr.contains("(")) {
                    int end = typeStr.indexOf(")");
                    if (end == -1) {
                        errorGroups.computeIfAbsent(TAB_TYPE, k -> new ObjectArrayList<>()).add(tabName);
                        continue;
                    }
                    typeModifier = typeStr.substring(end + 1);
                    typeStr = typeStr.substring(0, end + 1);
                }
                if (typeStr != null && !typeStr.isEmpty() && !typeStr.contains(",") && !typeStr.contains("#") && !typeStr.contains("(") && !typeStr.contains(" ")) {
                    Class type;
                    try {
                        type = SheetBindings.type(typeStr);
                    } catch (IllegalArgumentException e2) {
                        messageList.add(e2.getMessage());
                        continue;
                    }

                    String selectionStr = tabName.substring(index + 1);
                    Placeholders ph = phMap.get(type);

                    AtomicBoolean createdSelection = new AtomicBoolean();
                    selection = ph.getOrCreateSelection(db, selectionStr, typeModifier, saveSheet, createdSelection);

                    if (createdSelection.get() && saveSheet) {
                        messageList.add("Created and saved `" + selectionStr + "` as `" + selection.getName() + "` for type: `" + PlaceholdersMap.getClassName(selection.getType()) + "`. You may use this alias in commands and sheets\n" +
                                "To rename: " + CM.selection_alias.rename.cmd.toSlashMention() + "\n" +
                                "To list aliases: " + CM.selection_alias.list.cmd.toSlashMention());
                    }
                } else if (custom != null){
                    Map.Entry<SelectionAlias, SheetTemplate> tab = custom.getTab(tabName);
                    if (tab == null) {
                        errorGroups.computeIfAbsent(TAB_TYPE, k -> new ObjectArrayList<>()).add(tabName);
                        errorGroups.computeIfAbsent("Note: A saved sheet was found for this url, but no tabs were registered to `{value}`. " +
                                "Create a tab with " + CM.sheet_custom.add_tab.cmd.toSlashMention(), k -> new ObjectArrayList<>()).add(tabName);
                        continue;
                    }
                    selection = tab.getKey();
                } else {
                    errorGroups.computeIfAbsent(TAB_TYPE, k -> new ObjectArrayList<>()).add(tabName);
                    continue;
                }
            }
            customTabsToFetch.put(tabName, selection);
        }

        Map<String, List<List<Object>>> headerRows = sheet.fetchHeaderRows(customTabsToFetch.keySet());
        for (Map.Entry<String, List<List<Object>>> entry : headerRows.entrySet()) {
            String tabName = entry.getKey();
            SelectionAlias selection = customTabsToFetch.get(tabName);

            Placeholders ph = phMap.get(selection.getType());

            List<List<Object>> row = entry.getValue();
            List<String> header = row == null || row.isEmpty() ? null : row.get(0).stream().map(o -> o == null ? "" : o.toString()).toList();
            SheetTemplate template = null;
            if (header == null || header.isEmpty()) {
                errorGroups.computeIfAbsent("Tabs: `{value}`; have no header row", k -> new ObjectArrayList<>()).add(tabName);
            } else {
                AtomicBoolean createdTemplate = new AtomicBoolean();
                template = ph.getOrCreateTemplate(db, header, false, createdTemplate);
            }
            if (template == null) {
                continue;
            }
            customTabs.put(tabName, KeyValue.of(selection, template));
        }


        if (customTabs.isEmpty()) {
            messageList.add("No tabs found. No tabs will be updated");
            return "**Result**:\n- " + StringMan.join(toErrorList.get(), "\n- ");
        }

        StringBuilder response = new StringBuilder();
        Map<String, List<String>> exportColumns = new Object2ObjectLinkedOpenHashMap<>();
        messageList.addAll(custom.update(custom.getSheet(), store, customTabs, exportColumns));
        response.append("**Result**:\n- ").append(StringMan.join(toErrorList.get(), "\n- ")).append("\n");
        response.append("Url: <").append(custom.getUrl()).append(">\n");
        if (saveSheet) {
            response.append("Saved sheet: `").append(custom.getName()).append("`");
        }
        IMessageBuilder msg = io.create().append(response.toString());
        if (!exportColumns.isEmpty()) {
            int numTabsWithPlaceholders = (int) exportColumns.values().stream()
                    .filter(col -> col.stream().anyMatch(s -> s.contains("{") && s.contains("}")))
                    .count();
            Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
            String exportJson = prettyGson.toJson(exportColumns);
            String fileName = "tabs_" + exportColumns.size() + "_hascolumns_" + numTabsWithPlaceholders + ".json";
            msg.file(fileName, exportJson.getBytes(StandardCharsets.UTF_8));
        }
        msg.send();
        return null;
    }

    @Command(desc = """
            Import a JSON object with columns into a spreadsheet
            The JSON must be in the format:
            {
                "tab1": ["value1", "value2"],
                "tab2": ["value3", "value4"]
            }
            The keys will be the tab names, and the values will be the column names""")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.INTERNAL_AFFAIRS, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String importSheetJsonColumns(SpreadSheet sheet, JSONObject json) throws IOException {
        Map<String, List<String>> columns = new Gson().fromJson(json.toString(), Map.class);
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("No columns found in the JSON");
        }

        sheet.reset();
        columns.forEach((tabName, header) -> {
            sheet.setDefaultTab(tabName, null);
            sheet.setHeader(header);
        });
        sheet.updateWrite();

        return "Imported columns to <" + sheet.getURL() + ">";
    }

    @Command(desc = """
            Reads a CSV file from a discord message attachment and updates the provided Google Sheet
            The sheet must be in a valid TSV or CSV format, with the header row as the first row
            Specify the index of the attachment if there are multiple attachments""")
    public String fromFile(@Me IMessageIO io, @Me JSONObject command, Message message, SpreadSheet sheet, @Switch("i") Integer index) throws ExecutionException, InterruptedException {
        @Unmodifiable List<Message.Attachment> attachments = message.getAttachments();
        if (attachments.isEmpty()) {
            throw new IllegalArgumentException("No attachments found in the message.");
        }
        Message.Attachment attachment;
        if (attachments.size() != 1) {
            if (index == null) {
                IMessageBuilder embed = io.create().embed("Multiple attachments found", "Please specify the index by clicking a number below");
                for (int i = 0; i < attachments.size(); i++) {
                    JSONObject copy = WebUtil.json(command).put("index", i + 1);
                    embed = embed.commandButton(copy, String.valueOf(i + 1));
                }
                embed.send();
                return null;
            } else {
                if (index < 1 || index > attachments.size()) {
                    throw new IllegalArgumentException("Index must be between 1 and " + attachments.size());
                }
                attachment = attachments.get(index - 1);
            }
        } else {
            attachment = attachments.get(0);
        }

        try (InputStream is = attachment.getProxy().download().get()) {
            byte[] bytes = is.readAllBytes();
            String content = new String(bytes, StandardCharsets.UTF_8);
            String[] lines = content.split("\r?\n");
            if (lines.length < 2) {
                throw new IllegalArgumentException("File must have at least 2 lines (found " + lines.length + ")");
            }
            String headerLine = lines[0];
            char separator = headerLine.contains("\t") ? '\t' : ',';
            try (CsvReader reader = CsvReader.builder().fieldSeparator(separator).quoteCharacter('"').build(new String(bytes, StandardCharsets.UTF_8))) {
                try (CloseableIterator<CsvRow> iter = reader.iterator()) {
                    CsvRow header = iter.next();
                    List<String> fields = header.getFields();
                    sheet.setHeader(fields);

                    while (iter.hasNext()) {
                        CsvRow row = iter.next();
                        List<String> rowData = row.getFields();
                        sheet.addRow(rowData);
                    }

                    sheet.updateClearCurrentTab();
                    sheet.updateWrite();
                    sheet.attach(io.create(), "file").send();
                    return null;
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}