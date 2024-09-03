package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.CreateSheet;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.binding.annotation.PlaceholderType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
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
import link.locutus.discord.util.sheet.SpreadSheet;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static link.locutus.discord.config.Messages.TAB_TYPE;

public class CustomSheetCommands {

    @NoFormat
    @Command(desc = "List the sheet keys in use")
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
    @Command(desc = "List sheet templates for this guild")
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
    @Command(desc = "List selection aliases for this guild")
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
    @Command(desc = "List custom sheets for this guild")
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
    @Command(desc = "View a sheet template")
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
    @Command(desc = "Add a tab to a custom sheet\n" +
            "Tabs are named and are comprised of a selection alias (rows) and a sheet template (columns)\n" +
            "You must create a selection alias and sheet template first\n" +
            "Sheets must be generated/updated with the update command")
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

    @Command(desc = "Update the tabs in a custom sheet and return the url\n" +
            "A sheet update may produce errors if a selection is no longer valid\n" +
            "Tabs in the google sheet which aren't registered will be ignored")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String updateSheet(CustomSheet sheet, ValueStore store) throws GeneralSecurityException, IOException {
        List<String> errors = sheet.update(store);

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

    @Command(desc = "Get the google sheet url and view the tabs for a custom sheet, and their respective selection alias (rows) and sheet template (columns)")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String info(CustomSheet sheet) {
        return sheet.toString();
    }

    @Command(desc = "Generate or update a spreadsheet from a url\n" +
            "Each tab must be a valid selection, prefixed by the type e.g. `nation:*`\n" +
            "The first row must have placeholders in each column, such as `{nation}` `{cities}` `{score}`")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String auto(ValueStore store, @Me GuildDB db, SpreadSheet sheet, @Switch("s") boolean saveSheet) throws GeneralSecurityException, IOException {
        CustomSheetManager manager = db.getSheetManager();
        PlaceholdersMap phMap = Locutus.cmd().getV2().getPlaceholders();
        List<String> errors = new ArrayList<>();
        CustomSheet custom = manager.getCustomSheetById(sheet.getSpreadsheetId());
        if (custom == null) {
            if (saveSheet) {
                String name = sheet.getTitle();
                CustomSheet existing = manager.getCustomSheet(name);
                if (existing != null) {
                    return "Sheet with name `" + name + "` already exists: <" + existing.getUrl() + ">. Please change the title of your spreadsheet to something unique or delete the existing sheet using ";
                }
                manager.addCustomSheet(name, sheet.getSpreadsheetId());
            } else {
                custom = new CustomSheet(sheet.getTitle(), sheet.getSpreadsheetId(), new LinkedHashMap<>());
            }
        } else {
            String msg = "A custom sheet with the name `" + custom.getName() + "` has row selections and column templates already saved.\n" +
                    "These saved tabs will be used when new selection is specified for a tab.";
            if (saveSheet) {
                msg += "\nChanges will be saved";
            }
            errors.add(msg);
        }

        Map<String, Map.Entry<SelectionAlias, SheetTemplate>> customTabs = new LinkedHashMap<>();

        Map<Integer, String> tabs = sheet.fetchTabs();
        for (Map.Entry<Integer, String> entry : tabs.entrySet()) {
            String tabName = entry.getValue();
            SelectionAlias selection;
            SheetTemplate template = null;

            try {
                selection = SheetBindings.selectionAlias(true, manager, store, tabName);
            } catch (IllegalArgumentException e) {
                int index = tabName.indexOf(":");
                if (index == -1) {
                    errors.add(TAB_TYPE.replace("{tab_name}", tabName) + " (1)");
                    continue;
                }
                String typeStr = tabName.substring(0, index);
                if (typeStr != null && !typeStr.isEmpty() && !typeStr.contains(",") && !typeStr.contains("#") && !typeStr.contains("(") && !typeStr.contains(" ")) {
                    Class type;
                    try {
                        type = SheetBindings.type(typeStr);
                    } catch (IllegalArgumentException e2) {
                        errors.add(e2.getMessage());
                        continue;
                    }

                    String selectionStr = tabName.substring(index + 1);
                    Placeholders ph = phMap.get(type);

                    AtomicBoolean createdSelection = new AtomicBoolean();
                    selection = ph.getOrCreateSelection(db, selectionStr, saveSheet, createdSelection);

                    if (createdSelection.get() && saveSheet) {
                        errors.add("Created and saved `" + selectionStr + "` as `" + selection.getName() + "` for type: `" + PlaceholdersMap.getClassName(selection.getType()) + "`. You may use this alias in commands and sheets\n" +
                                "To rename: " + CM.selection_alias.rename.cmd.toSlashMention() + "\n" +
                                "To list aliases: " + CM.selection_alias.list.cmd.toSlashMention());
                    }
                } else if (custom != null){
                    Map.Entry<SelectionAlias, SheetTemplate> tab = custom.getTab(tabName);
                    if (tab == null) {
                        errors.add(TAB_TYPE.replace("{tab_name}", tabName) + "\nNote: A saved sheet was found for this url, but no tab was registered to `" + tabName + "`." +
                                "Create a tab with " + CM.sheet_custom.add_tab.cmd.toSlashMention());
                    }
                    selection = tab.getKey();
                } else {
                    errors.add(TAB_TYPE.replace("{tab_name}", tabName));
                    continue;
                }
            }

            Placeholders ph = phMap.get(selection.getType());

            List<List<Object>> row = sheet.fetchRange(tabName, "1:1");
            List<String> header = row == null || row.isEmpty() ? null : row.get(0).stream().map(o -> o == null ? "" : o.toString()).toList();
            if (header == null || header.isEmpty()) {
                errors.add("Tab `" + tabName + "` has no header row");
            } else {
                AtomicBoolean createdTemplate = new AtomicBoolean();
                template = ph.getOrCreateTemplate(db, header, false, createdTemplate);
            }
            if (template == null) {
                continue;
            }

            customTabs.put(tabName, Map.entry(selection, template));
        }
        if (customTabs.isEmpty()) {
            errors.add("No tabs found. No tabs will be updated");
            return "**Result**:\n- " + StringMan.join(errors, "\n- ");
        }

        StringBuilder response = new StringBuilder();
        errors.addAll(custom.update(store, customTabs));
        response.append("**Result**:\n- ").append(StringMan.join(errors, "\n- ")).append("\n");
        response.append("Url: <").append(custom.getUrl()).append(">\n");
        if (saveSheet) {
            response.append("Saved sheet: `").append(custom.getName()).append("`");
        }
        return response.toString();
    }
}