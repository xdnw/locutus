package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import com.github.javaparser.printer.lexicalpreservation.Added;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.CreateSheet;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.binding.annotation.PlaceholderType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.CustomSheet;
import link.locutus.discord.db.entities.SelectionAlias;
import link.locutus.discord.db.entities.SheetTemplate;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CustomSheetCommands {
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
        return "Renamed `" + sheet.getName() + "` to `" + name + "`";
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
        StringBuilder response = new StringBuilder("**Custom Sheets**\n");
        for (Map.Entry<String, String> entry : sheets.entrySet()) {
            response.append("- ").append(MarkupUtil.markdownUrl(entry.getKey(), entry.getValue())).append("\n");
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
    public String addTab(@Me JSONObject command, @Me IMessageIO io, @Me GuildDB db, @CreateSheet CustomSheet sheet, String tabName, SelectionAlias alias, SheetTemplate template, @Switch("f") boolean force) {
        tabName = tabName.toLowerCase(Locale.ROOT);
        // ensure name is alphanumeric _
        if (!tabName.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Tab name must be alphanumericunderscore, not `" + tabName + "`");
        }
        // ensure alias and template type match
        if (!alias.getType().equals(template.getType())) {
            throw new IllegalArgumentException("Alias type `" + alias.getType().getSimpleName() + "` does not match template type `" + template.getType().getSimpleName() + "`");
        }
        // tabs
        Map.Entry<SelectionAlias, SheetTemplate> existingTab = sheet.getTab(tabName);
        if (!force) {
            String title = (existingTab == null ? "Add" : "Replace existing") + " tab: `" + tabName + "`";
            StringBuilder body = new StringBuilder();
            SelectionAlias previousAlias = existingTab == null ? null : existingTab.getKey();
            SheetTemplate previousTemplate = existingTab == null ? null : existingTab.getValue();
            if (previousAlias == null) {
                body.append("**Selection:** `").append(alias.getName()).append("`\n");
                body.append("- Type: `").append(alias.getType().getSimpleName()).append("`\n");
                body.append("- Selection: `").append(alias.getSelection()).append("`\n");
            } else if (alias.getName().equalsIgnoreCase(previousAlias.getName())) {
                body.append("**Selection:** `" + alias.getName() + "` (no change)\n");
            } else {
                body.append("**Selection:** `").append(previousAlias.getName()).append("` -> `").append(alias.getName()).append("`\n");
                body.append("- Type: `").append(previousAlias.getType().getSimpleName()).append("` -> `").append(alias.getType().getSimpleName()).append("`\n");
                body.append("- Selection: `").append(previousAlias.getSelection()).append("` -> `").append(alias.getSelection()).append("`\n");
            }
            if (previousTemplate == null) {
                body.append("**Template:** `").append(template.getName()).append("`\n");
                body.append("- Type: `").append(template.getType().getSimpleName()).append("`\n");
                body.append("- Columns: `").append(template.getColumns().size()).append("`\n");
            } else if (template.getName().equalsIgnoreCase(previousTemplate.getName())) {
                body.append("**Template:** `" + template.getName() + "` (no change)\n");
            } else {
                body.append("**Template:** `").append(previousTemplate.getName()).append("` -> `").append(template.getName()).append("`\n");
                body.append("- Type: `").append(previousTemplate.getType().getSimpleName()).append("` -> `").append(template.getType().getSimpleName()).append("`\n");
                body.append("- Columns: `").append(previousTemplate.getColumns().size()).append("` -> `").append(template.getColumns().size()).append("`\n");
            }

            io.create().confirmation(title, body.toString(), command).send();
            return null;
        }

        db.getSheetManager().addCustomSheetTab(sheet.getName(), tabName, alias.getName(), template.getName());

        return "**__sheet:" + sheet.getName() + "__**\n" + (existingTab == null ? " Added " : "Updated") + " tab `" + tabName + "`\n" +
                "- Selection: `" + alias.getName() + "`\n" +
                "- Template: `" + template.getName() + "`\n" +
                "- Url: <" + sheet.getUrl() + ">\n" +
                CM.sheet_custom.update.cmd.toSlashMention() + " to update the sheet\n" +
                "See: " + CM.sheet_custom.view.cmd.toSlashMention() + " | " + CM.sheet_custom.remove_tab.cmd.toSlashMention() + " | " + CM.sheet_custom.update.cmd.toSlashMention();
    }

    @Command(desc = "Update the tabs in a custom sheet and return the url\n" +
            "A sheet update may produce errors if a selection is no longer valid\n" +
            "Tabs in the google sheet which aren't registered will be ignored")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String updateSheet(@Me IMessageIO io, CustomSheet sheet, ValueStore store) throws GeneralSecurityException, IOException {
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
    public String deleteTab(@Me GuildDB db, @Me IMessageIO io, CustomSheet sheet, String tabName) {
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
}