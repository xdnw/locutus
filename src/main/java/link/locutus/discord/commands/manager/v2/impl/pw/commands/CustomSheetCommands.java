package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.binding.annotation.PlaceholderType;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.CustomSelection;
import link.locutus.discord.db.entities.CustomSheet;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CustomSheetCommands {
    @NoFormat
    @Command(desc = "List custom sheets")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String listCustomSheets(@Me GuildDB db, @Default @PlaceholderType Class type) {
        List<String> errors = new ArrayList<>();
        Map<String, CustomSheet> sheets = db.getCustomSheets(errors);
        if (type != null) {
            sheets.entrySet().removeIf(entry -> !entry.getValue().getType().equals(type));
        }
        if (sheets.isEmpty()) {
            return "No custom sheets found" + (type == null ? "" : " of type `" + type.getSimpleName() + "`");
        }
        List<Map.Entry<String, CustomSheet>> entries = new ArrayList<>(sheets.entrySet());
        // sort by type simple name, then name
        Collections.sort(entries, Comparator.comparing((Map.Entry<String, CustomSheet> o) -> o.getValue().getType().getSimpleName()).thenComparing(Map.Entry::getKey));

        Map<String, List<CustomSheet>> groupByTypeName = new LinkedHashMap<>();
        for (Map.Entry<String, CustomSheet> entry : entries) {
            String typeName = entry.getValue().getType().getSimpleName();
            groupByTypeName.computeIfAbsent(typeName, k -> new ArrayList<>()).add(entry.getValue());
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<CustomSheet>> entry : groupByTypeName.entrySet()) {
            sb.append("**").append(entry.getKey()).append("**\n");
            for (CustomSheet sheet : entry.getValue()) {
                sb.append("- " + sheet.getName() + ": `" + StringMan.join(sheet.getColumns(), " ") + "`\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @NoFormat
    @Command(desc = "List custom selections")
    public String listSelectionAliases(@Me GuildDB db, @Default @PlaceholderType Class type) {
        Map<Class, Map<String, String>> selections = new LinkedHashMap<>(db.getCustomSelections());
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
    @Command(desc = "View a custom sheet")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String deleteSelectionAlias(@Me GuildDB db, CustomSelection selection) {
        db.removeCustomSelection(selection.getName());
        return "Deleted selection with alias `" + selection.getName() + "`\n" +
                "- `" + selection.getSelection() + "`";
    }

    @NoFormat
    @Command(desc = "View a custom sheet")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String viewSheet(CustomSheet sheet) {
        return sheet.toString() + "\n\n" +
                "See TODO CM REF (remove, move, delete)";
    }

    @NoFormat
    @Command(desc = "Delete a custom spreadsheet")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String deleteSheet(@Me GuildDB db, CustomSheet sheet) {
        db.deleteCustomSheet(sheet.getName());
        return "Deleted sheet `" + sheet.getName() + "`";
    }

    @NoFormat
    @Command(desc = "Delete columns in a custom sheet")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String deleteColumns(@Me GuildDB db, CustomSheet sheet, List<Integer> columns) {
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

        db.addCustomSheet(sheet);
        return "Removed columns `" + columns + "` from sheet `" + sheet.getName() + "`\n" +
                "See: TODO CM REF VIEW";

    }
}
