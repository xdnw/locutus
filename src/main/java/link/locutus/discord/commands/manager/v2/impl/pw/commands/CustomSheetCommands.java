package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.binding.annotation.PlaceholderType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
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
    @Command(desc = "List custom sheets")
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
    @Command(desc = "List custom selections")
    public String listSelectionAliases(@Me GuildDB db, @Default @PlaceholderType Class type) {
        Map<Class, Map<String, String>> selections = new LinkedHashMap<>(db.getSheetManager().getSelectionAliases());
        if (type != null) {
            selections.entrySet().removeIf(entry -> !entry.getKey().equals(type));
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Class, Map<String, String>> entry : selections.entrySet()) {
            sb.append("**").append(entry.getKey().getSimpleName()).append("**\n");
            for (Map.Entry<String, String> selection : entry.getValue().entrySet()) {
                sb.append("- `").append(selection.getKey()).append("` -> `").append(selection.getValue()).append("`\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @NoFormat
    @Command(desc = "List custom sheets")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String listCustomSheets(@Me GuildDB db) {
        Map<String, String> sheets = db.getSheetManager().getCustomSheets();
        if (sheets.isEmpty()) {
            return "No custom sheets found. Create one with TODO CM REF";
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
    @Command(desc = "View a custom sheet")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String viewTemplate(SheetTemplate sheet) {
        return sheet.toString() + "\n\n" +
                "See TODO CM REF (remove, move, delete)";
    }

    @NoFormat
    @Command(desc = "Delete a custom spreadsheet")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String deleteTemplate(@Me GuildDB db, SheetTemplate sheet) {
        db.getSheetManager().deleteSheetTemplate(sheet.getName());
        return "Deleted sheet `" + sheet.getName() + "`";
    }

    @NoFormat
    @Command(desc = "Delete columns in a custom sheet")
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
                "See: TODO CM REF VIEW";

    }

    //- add_tab <tab-name> <selector> <template>
    @NoFormat
    @Command
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String addTab(@Me JSONObject command, @Me IMessageIO io, @Me GuildDB db, CustomSheet sheet, String tabName, SelectionAlias alias, SheetTemplate template, @Switch("f") boolean force) {
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
            String title = "Replace existing tab: `" + tabName + "`";
            StringBuilder body = new StringBuilder();
            SelectionAlias previousAlias = existingTab.getKey();
            SheetTemplate previousTemplate = existingTab.getValue();
            if (alias.getName().equals(previousAlias)) {
                body.append("**Selection:** `" + alias.getName() + "` (no change)\n");
            } else {
                body.append("**Selection:** `").append(previousAlias.getName()).append("` -> `").append(alias.getName()).append("`\n");
                body.append("- Type: `").append(previousAlias.getType().getSimpleName()).append("` -> `").append(alias.getType().getSimpleName()).append("`\n");
                body.append("- Selection: `").append(previousAlias.getSelection()).append("` -> `").append(alias.getSelection()).append("`\n");
            }
            if (template.getName().equals(previousTemplate)) {
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

        return (existingTab == null ? "Added" : "Updated") + " tab `" + tabName + "`\n" +
                "- Selection: `" + alias.getName() + "`\n" +
                "- Template: `" + template.getName() + "`\n" +
                "- Url: <" + sheet.getUrl() + ">" +
                "TODO CM REF to update the sheet\n" +
                "See also TODO CM REF (delete, rename, remove tab)";
    }

    @Command
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String updateSheet(@Me IMessageIO io, CustomSheet sheet, ValueStore store) throws GeneralSecurityException, IOException {
        List<String> errors = sheet.update(store);

        StringBuilder result = new StringBuilder();
        result.append("Updating sheet: `").append(sheet.getName()).append("` (").append(sheet.getTabs().size()).append(" tabs)\n");
        result.append("Url: <").append(sheet.getUrl()).append(">\n");
        if (errors.isEmpty()) {
            result.append("- No errors");
        } else {
            result.append("Errors:\n");
            for (String error : errors) {
                result.append("- ").append(error).append("\n");
            }
        }
        return result.toString();
    }

    @Command
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
                "See: TODO CM REF VIEW";
    }

    @Command
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String info(CustomSheet sheet) {
        return sheet.toString();
    }
}
